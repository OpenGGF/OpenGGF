package com.openggf.data.cd;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-owning primary-volume ISO 9660 view of a MODE1 track.
 * Uses ECMA-119, second edition, sections 6.8, 7.2.3, 7.3.3, 7.5, 8.4 and 9.1.
 * Supports 2048-byte blocks, one volume and contiguous, single-extent files.
 * Extended attributes, associated/record files, interleaving and multi-extents fail explicitly.
 * Supplementary namespaces are ignored. Paths retain file versions; unversioned lookup selects the highest.
 *
 * @see <a href="https://ecma-international.org/wp-content/uploads/ECMA-119_2nd_edition_december_1987.pdf">ECMA-119</a>
 */
public final class Iso9660FileSystem {
    public static final int BLOCK_BYTES = 2048;
    private static final int MAX_DESCRIPTORS = 256;
    private static final int MAX_ENTRIES = 65536;
    private static final long MAX_DIRECTORY_BYTES = 16L * 1024 * 1024;
    private static final Pattern FILE_IDENTIFIER = Pattern.compile("([A-Z0-9_]*\\.[A-Z0-9_]*);([0-9]{1,5})");
    private static final Pattern DIRECTORY_IDENTIFIER = Pattern.compile("[A-Z0-9_]{1,31}");

    /** Paths are absolute. Directory versions are zero. Sizes exclude sector padding. */
    public record Entry(String path, boolean directory, long extentBlock, long size, int version) {
        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private record DirectoryRecord(String identifier, boolean directory, long extent, long size, int version) {
        Entry at(String path) {
            return new Entry(path, directory, extent, size, version);
        }
    }

    private final CueDiscImage disc;
    private final int dataTrack;
    private final long volumeBlocks;
    private final String volumeIdentifier;
    private final Entry root;
    private final Map<String, Entry> entries;
    private final Map<String, List<Entry>> directories;
    private final Map<String, Entry> latestVersions;
    private final List<Entry> files;

    private Iso9660FileSystem(CueDiscImage disc, int dataTrack, long volumeBlocks, String volumeIdentifier,
                             Entry root, Map<String, Entry> entries, Map<String, List<Entry>> directories) {
        this.disc = disc;
        this.dataTrack = dataTrack;
        this.volumeBlocks = volumeBlocks;
        this.volumeIdentifier = volumeIdentifier;
        this.root = root;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.directories = Map.copyOf(directories);
        Map<String, Entry> latest = new LinkedHashMap<>();
        List<Entry> allFiles = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.directory()) {
                allFiles.add(entry);
                String unversioned = entry.path().substring(0, entry.path().lastIndexOf(';'));
                Entry previous = latest.get(unversioned);
                if (previous == null || previous.version() < entry.version()) {
                    latest.put(unversioned, entry);
                }
            }
        }
        latestVersions = Map.copyOf(latest);
        files = List.copyOf(allFiles);
    }

    /** Validates the complete bounded directory tree without reading file payloads. */
    public static Iso9660FileSystem open(CueDiscImage disc, int dataTrack) throws IOException {
        Objects.requireNonNull(disc);
        DiscTrack track = disc.track(dataTrack);
        if (!track.mode().isMode1()) {
            throw new IllegalArgumentException("ISO requires a MODE1 track");
        }
        disc.checkTrack(dataTrack);
        byte[] primary = null;
        long terminator = -1;
        for (long block = 16; block < Math.min(track.programFrames(), 16L + MAX_DESCRIPTORS); block++) {
            byte[] descriptor = disc.readMode1Bytes(dataTrack, block * BLOCK_BYTES, BLOCK_BYTES);
            if (!new String(descriptor, 1, 5, StandardCharsets.US_ASCII).equals("CD001") || descriptor[6] != 1) {
                throw new IOException("Invalid ISO volume descriptor at block " + block);
            }
            int type = u8(descriptor, 0);
            if (type == 255) {
                terminator = block;
                break;
            }
            if (type == 1) {
                if (primary != null) {
                    throw new IOException("Multiple primary volume descriptors are unsupported");
                }
                primary = descriptor;
            } else if (type != 0 && type != 2) {
                throw new IOException("Unsupported ISO volume descriptor type " + type);
            }
        }
        if (primary == null || terminator < 0) {
            throw new IOException("Missing ISO primary descriptor or descriptor terminator");
        }
        long blocks = both32(primary, 80);
        if (blocks <= terminator || blocks > track.programFrames()
                || both16(primary, 128) != BLOCK_BYTES || both16(primary, 120) != 1
                || both16(primary, 124) != 1 || primary[881] != 1) {
            throw new IOException("Unsupported or out-of-track ISO volume geometry");
        }
        long pathBytes = both32(primary, 132);
        if (pathBytes == 0) {
            throw new IOException("Empty ISO path table");
        }
        checkExtent(unsigned(primary, 140, 4, false), pathBytes, blocks);
        checkExtent(unsigned(primary, 148, 4, true), pathBytes, blocks);
        for (int offset : new int[] {144, 152}) {
            long optional = unsigned(primary, offset, 4, offset == 152);
            if (optional != 0) {
                checkExtent(optional, pathBytes, blocks);
            }
        }
        DirectoryRecord rootRecord = parseRecord(primary, 156, 190, blocks);
        if (!rootRecord.directory() || !rootRecord.identifier().equals("\0") || u8(primary, 156) != 34) {
            throw new IOException("Invalid ISO root directory record");
        }
        Entry root = rootRecord.at("/");
        DirectoryIndex index = new DirectoryIndex(disc, dataTrack, blocks);
        index.entries.put("/", root);
        index.readDirectory(root, root, 1);
        disc.checkTrack(dataTrack);
        return new Iso9660FileSystem(disc, dataTrack, blocks,
                new String(primary, 40, 32, StandardCharsets.US_ASCII).stripTrailing(),
                root, index.entries, index.directories);
    }

    public long volumeBlocks() {
        return volumeBlocks;
    }

    public String volumeIdentifier() {
        return volumeIdentifier;
    }

    public Entry root() {
        return root;
    }

    /** Case-insensitive paths accept an optional leading slash and no dot or empty components. */
    public Entry entry(String path) throws IOException {
        disc.checkTrack(dataTrack);
        String canonical = canonicalPath(path);
        Entry entry = entries.get(canonical);
        if (entry == null) {
            entry = latestVersions.get(canonical);
        }
        if (entry == null) {
            throw new FileNotFoundException("No ISO entry " + path);
        }
        return entry;
    }

    /** Returns immediate children in recorded order, excluding self and parent records. */
    public List<Entry> listDirectory(String path) throws IOException {
        Entry entry = entry(path);
        if (!entry.directory()) {
            throw new IOException("ISO entry is not a directory: " + path);
        }
        return directories.get(entry.path());
    }

    /** Returns all files recursively, retaining each recorded version. */
    public List<Entry> files() throws IOException {
        disc.checkTrack(dataTrack);
        return files;
    }

    /** Whole-file reads are limited to 16 MiB. Use openFile for larger assets. */
    public byte[] readFile(String path) throws IOException {
        Entry file = file(path);
        if (file.size() > CueDiscImage.MAX_READ_BYTES) {
            throw new IOException("ISO file exceeds bounded read limit; use openFile: " + path);
        }
        return read(file, 0, (int) file.size());
    }

    public byte[] readFile(String path, long byteOffset, int length) throws IOException {
        return read(file(path), byteOffset, length);
    }

    /** Closing this forward-only stream leaves the borrowed disc and other streams open. */
    public InputStream openFile(String path) throws IOException {
        return new FileStream(file(path));
    }

    private Entry file(String path) throws IOException {
        Entry entry = entry(path);
        if (entry.directory()) {
            throw new IOException("ISO entry is a directory: " + path);
        }
        return entry;
    }

    private byte[] read(Entry file, long offset, int length) throws IOException {
        CueDiscImage.checkRange(offset, length, file.size());
        return disc.readMode1Bytes(dataTrack, file.extentBlock() * BLOCK_BYTES + offset, length);
    }

    private static String canonicalPath(String path) {
        Objects.requireNonNull(path);
        if (path.equals("/") || path.isEmpty()) {
            return "/";
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        for (String component : relative.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..") || component.indexOf('\\') >= 0
                    || component.chars().anyMatch(c -> c < 32 || c > 126)) {
                throw new IllegalArgumentException("Invalid ISO path");
            }
        }
        if (relative.length() > 255) {
            throw new IllegalArgumentException("ISO path exceeds limit");
        }
        String canonical = "/" + relative.toUpperCase(Locale.ROOT);
        int lastSlash = canonical.lastIndexOf('/');
        Matcher identifier = FILE_IDENTIFIER.matcher(canonical.substring(lastSlash + 1));
        if (identifier.matches()) {
            canonical = canonical.substring(0, lastSlash + 1) + identifier.group(1)
                    + ";" + Integer.parseInt(identifier.group(2));
        }
        return canonical;
    }

    private static final class DirectoryIndex {
        private final CueDiscImage disc;
        private final int track;
        private final long blocks;
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Map<String, List<Entry>> directories = new LinkedHashMap<>();
        private final TreeMap<Long, Long> directoryExtents = new TreeMap<>();
        private long directoryBytes;

        DirectoryIndex(CueDiscImage disc, int track, long blocks) {
            this.disc = disc;
            this.track = track;
            this.blocks = blocks;
        }

        void readDirectory(Entry directory, Entry parent, int depth) throws IOException {
            if (depth > 8 || directory.size() > MAX_DIRECTORY_BYTES - directoryBytes) {
                throw new IOException("ISO directory depth or metadata size exceeds limit");
            }
            directoryBytes += directory.size();
            long end = directory.extentBlock() + directory.size() / BLOCK_BYTES;
            Map.Entry<Long, Long> preceding = directoryExtents.floorEntry(directory.extentBlock());
            Map.Entry<Long, Long> following = directoryExtents.ceilingEntry(directory.extentBlock());
            if ((preceding != null && preceding.getValue() > directory.extentBlock())
                    || (following != null && following.getKey() < end)) {
                throw new IOException("Cyclic, aliased or overlapping ISO directories");
            }
            directoryExtents.put(directory.extentBlock(), end);
            List<Entry> children = new ArrayList<>();
            int recordNumber = 0;
            for (long sector = 0; sector < directory.size() / BLOCK_BYTES; sector++) {
                byte[] bytes = disc.readMode1Bytes(track,
                        (directory.extentBlock() + sector) * BLOCK_BYTES, BLOCK_BYTES);
                int offset = 0;
                while (offset < bytes.length) {
                    if (bytes[offset] == 0) {
                        if (recordNumber < 2) {
                            throw new IOException("Missing initial ISO self or parent record");
                        }
                        for (int i = offset; i < bytes.length; i++) {
                            if (bytes[i] != 0) {
                                throw new IOException("Nonzero ISO directory sector padding");
                            }
                        }
                        break;
                    }
                    DirectoryRecord record = parseRecord(bytes, offset, bytes.length, blocks);
                    offset += u8(bytes, offset);
                    if (recordNumber < 2) {
                        Entry expected = recordNumber == 0 ? directory : parent;
                        String identifier = recordNumber == 0 ? "\0" : "\1";
                        if (!record.directory() || !record.identifier().equals(identifier)
                                || record.extent() != expected.extentBlock() || record.size() != expected.size()) {
                            throw new IOException("Invalid ISO self or parent directory record");
                        }
                    } else {
                        if (record.identifier().equals("\0") || record.identifier().equals("\1")) {
                            throw new IOException("Misplaced ISO self or parent record");
                        }
                        String path = (directory.path().equals("/") ? "/" : directory.path() + "/")
                                + record.identifier();
                        if (path.length() > 256 || entries.size() >= MAX_ENTRIES) {
                            throw new IOException("ISO path or entry count exceeds limit");
                        }
                        Entry entry = record.at(path);
                        if (entries.putIfAbsent(path, entry) != null) {
                            throw new IOException("Duplicate ISO identifier " + path);
                        }
                        children.add(entry);
                    }
                    recordNumber++;
                }
            }
            if (recordNumber < 2) {
                throw new IOException("Missing ISO self or parent directory record");
            }
            directories.put(directory.path(), List.copyOf(children));
            for (Entry child : children) {
                if (child.directory()) {
                    readDirectory(child, directory, depth + 1);
                }
            }
        }
    }

    private static DirectoryRecord parseRecord(byte[] bytes, int offset, int limit, long blocks) throws IOException {
        int length = u8(bytes, offset);
        if (length < 34 || length > limit - offset) {
            throw new IOException("Invalid or sector-crossing ISO directory record");
        }
        int identifierLength = u8(bytes, offset + 32);
        int minimumLength = 33 + identifierLength + (identifierLength % 2 == 0 ? 1 : 0);
        if (identifierLength == 0 || minimumLength > length
                || (identifierLength % 2 == 0 && bytes[offset + 33 + identifierLength] != 0)) {
            throw new IOException("Invalid ISO identifier length or padding");
        }
        int flags = u8(bytes, offset + 25);
        if (bytes[offset + 1] != 0 || (flags & ~3) != 0 || bytes[offset + 26] != 0
                || bytes[offset + 27] != 0 || both16(bytes, offset + 28) != 1) {
            throw new IOException("Unsupported ISO attributes, flags, interleave or volume sequence");
        }
        boolean directory = (flags & 2) != 0;
        long extent = both32(bytes, offset + 2);
        long size = both32(bytes, offset + 10);
        checkExtent(extent, size, blocks);
        if (directory && (size == 0 || size % BLOCK_BYTES != 0)) {
            throw new IOException("Invalid ISO directory size");
        }
        String identifier = new String(bytes, offset + 33, identifierLength, StandardCharsets.US_ASCII);
        int version = 0;
        if (directory) {
            if (!identifier.equals("\0") && !identifier.equals("\1")
                    && !DIRECTORY_IDENTIFIER.matcher(identifier).matches()) {
                throw new IOException("Invalid ISO directory identifier");
            }
        } else {
            Matcher matcher = FILE_IDENTIFIER.matcher(identifier);
            if (!matcher.matches() || matcher.group(1).length() < 2 || matcher.group(1).length() > 31) {
                throw new IOException("Invalid ISO file identifier");
            }
            version = Integer.parseInt(matcher.group(2));
            if (version < 1 || version > 32767) {
                throw new IOException("Invalid ISO file version");
            }
            identifier = matcher.group(1) + ";" + version;
        }
        return new DirectoryRecord(identifier, directory, extent, size, version);
    }

    private static void checkExtent(long block, long size, long volumeBlocks) throws IOException {
        long volumeBytes = volumeBlocks * BLOCK_BYTES;
        long start = block * BLOCK_BYTES;
        if ((size != 0 && block < 16) || start > volumeBytes || size > volumeBytes - start) {
            throw new IOException("ISO extent outside volume");
        }
    }

    private static int u8(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]);
    }

    private static long unsigned(byte[] bytes, int offset, int length, boolean bigEndian) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            int index = bigEndian ? offset + i : offset + length - i - 1;
            value = (value << 8) | u8(bytes, index);
        }
        return value;
    }

    private static int both16(byte[] bytes, int offset) throws IOException {
        long little = unsigned(bytes, offset, 2, false);
        if (little != unsigned(bytes, offset + 2, 2, true)) {
            throw new IOException("Mismatched ISO both-endian 16-bit value");
        }
        return (int) little;
    }

    private static long both32(byte[] bytes, int offset) throws IOException {
        long little = unsigned(bytes, offset, 4, false);
        if (little != unsigned(bytes, offset + 4, 4, true)) {
            throw new IOException("Mismatched ISO both-endian 32-bit value");
        }
        return little;
    }

    private final class FileStream extends InputStream {
        private final Entry file;
        private byte[] buffer = new byte[0];
        private int buffered;
        private long position;
        private boolean closed;

        FileStream(Entry file) {
            this.file = file;
        }

        private void checkOpen() throws IOException {
            if (closed) {
                throw new IOException("ISO stream is closed");
            }
            disc.checkTrack(dataTrack);
        }

        @Override
        public int read() throws IOException {
            checkOpen();
            if (!fill()) {
                return -1;
            }
            position++;
            return Byte.toUnsignedInt(buffer[buffered++]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            checkOpen();
            if (length == 0) {
                return 0;
            }
            if (!fill()) {
                return -1;
            }
            int count = Math.min(length, buffer.length - buffered);
            System.arraycopy(buffer, buffered, bytes, offset, count);
            buffered += count;
            position += count;
            return count;
        }

        private boolean fill() throws IOException {
            if (position == file.size()) {
                return false;
            }
            if (buffered == buffer.length) {
                buffer = Iso9660FileSystem.this.read(file, position, (int) Math.min(65536, file.size() - position));
                buffered = 0;
            }
            return true;
        }

        @Override
        public long skip(long count) throws IOException {
            checkOpen();
            long skipped = Math.min(Math.max(0, count), file.size() - position);
            position += skipped;
            buffer = new byte[0];
            buffered = 0;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            checkOpen();
            return (int) Math.min(Integer.MAX_VALUE, file.size() - position);
        }

        @Override
        public void close() {
            closed = true;
            buffer = new byte[0];
        }
    }
}
