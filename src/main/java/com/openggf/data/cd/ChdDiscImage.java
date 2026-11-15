package com.openggf.data.cd;

import com.openggf.data.cd.chd.ChdFormatException;
import com.openggf.data.cd.chd.ChdHunkDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Read-only, positional access to a CD-ROM CHD (MAME "Compressed Hunks of Data", version 5), presented with
 * the same geometry as the BINARY CUE set it was made from. The container is untrusted input: every size and
 * offset is bounded before allocation or reading, and nothing is read past the end of the file.
 *
 * <p>Format, from MAME's {@code chd.cpp}/{@code cdrom.cpp}/{@code chdcodec.cpp} and libchdr (BSD-3-Clause):
 * a 124-byte big-endian header; a hunk map (uncompressed four-byte entries, or the v5 Huffman-coded map
 * whose decoded twelve-byte entries are checked by CRC-16); a metadata chain whose {@code CHT2}/{@code CHTR}
 * entries describe each track. Each CD frame occupies a 2448-byte unit: 2352 sector bytes then 96 subcode
 * bytes. Audio samples are stored big-endian and are returned here byte-swapped to the Red Book
 * little-endian order a BINARY file holds. Each track's frames start on a four-frame boundary
 * (MAME's {@code CD_TRACK_PADDING}); the padding frames belong to no track. A pregap whose {@code PGTYPE}
 * carries the {@code V} prefix is stored ahead of the track's INDEX 01, like a CUE {@code INDEX 00} gap;
 * one without it has no stored frames, like a CUE {@code PREGAP}.
 *
 * <p>Supported compressors: {@code cdzl} (deflate), {@code cdlz} (LZMA), {@code cdfl} (FLAC) and
 * {@code cdzs} (Zstandard), plus uncompressed CHDs. Delta CHDs that need a parent, versions other than 5,
 * non-CD CHDs, MODE2 and GD-ROM tracks, and other codecs are refused by name. Hunk CRC-16s are verified on
 * every decode; the header's overall SHA-1 is checked against the raw-data SHA-1 and the metadata at open,
 * and {@link #verifyDataSha1()} checks the raw-data SHA-1 against every decoded hunk. chdman writes neither
 * digest nor hunk CRCs into an uncompressed CHD, so such a file is checked only by its content identity.
 *
 * @see <a href="https://github.com/mamedev/mame/blob/master/src/lib/util/chd.cpp">MAME chd.cpp</a>
 * @see <a href="https://github.com/rtissera/libchdr">libchdr</a>
 */
public final class ChdDiscImage implements DiscImage {
    static final byte[] TAG = "MComprHD".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_BYTES = 124;
    private static final int FRAME_BYTES = ChdHunkDecoder.FRAME_BYTES;
    private static final int TRACK_PADDING = 4;
    private static final int MAX_FRAMES = 450_000;
    private static final int MAX_HUNK_FRAMES = 256;
    private static final int MAX_METADATA_ENTRIES = 1024;
    private static final int MAX_METADATA_BYTES = 4096;
    private static final int CACHED_HUNKS = 8;
    private static final int TAG_CHT2 = tag("CHT2");
    private static final int TAG_CHTR = tag("CHTR");
    private static final int TAG_CHCD = tag("CHCD");
    private static final int TAG_CHGT = tag("CHGT");
    private static final int TAG_CHGD = tag("CHGD");
    private static final int TAG_DVD = tag("DVD ");
    private static final int MDFLAGS_CHECKSUM = 0x01;
    private static final Pattern CHT2 = Pattern.compile("TRACK:(\\d{1,2}) TYPE:(\\S+) SUBTYPE:(\\S+) FRAMES:(\\d{1,7})"
            + " PREGAP:(\\d{1,7}) PGTYPE:(\\S+) PGSUB:(\\S+) POSTGAP:(\\d{1,7})");
    private static final Pattern CHTR = Pattern.compile("TRACK:(\\d{1,2}) TYPE:(\\S+) SUBTYPE:(\\S+) FRAMES:(\\d{1,7})");

    private record Stamp(long size, FileTime modified, Object key) {
        static Stamp read(Path path) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new IOException("Disc input is not a regular file: " + path.getFileName());
            }
            return new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
        }
    }

    private record TrackSpec(int number, DiscTrack.Mode mode, long frames, long pregap, boolean pregapStored) { }

    private final Path path;
    private final String name;
    private final Stamp stamp;
    private final FileChannel channel;
    private final ChdHunkDecoder decoder;
    private final byte[] rawSha1;
    private final byte[] descriptorBytes;
    private final List<DiscTrack> tracks;
    private final long[] storedStartByTrack;
    private final List<SourceFile> sourceFiles;
    private final Map<Integer, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > CACHED_HUNKS;
        }
    };
    private boolean closed;

    private ChdDiscImage(Path path, Stamp stamp, FileChannel channel, ChdHunkDecoder decoder, byte[] rawSha1,
                         byte[] descriptorBytes, List<DiscTrack> tracks, long[] storedStartByTrack) {
        this.path = path;
        this.name = path.getFileName().toString();
        this.stamp = stamp;
        this.channel = channel;
        this.decoder = decoder;
        this.rawSha1 = rawSha1;
        this.descriptorBytes = descriptorBytes;
        this.tracks = List.copyOf(tracks);
        this.storedStartByTrack = storedStartByTrack;
        this.sourceFiles = List.of(new SourceFile());
    }

    /** Opens and validates a CD-ROM CHD; refuses every form this reader does not support, by name. */
    public static ChdDiscImage open(Path chd) throws IOException {
        Objects.requireNonNull(chd);
        Path path = chd.toRealPath();
        Stamp stamp = Stamp.read(path);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            ChdDiscImage image = parse(path, stamp, channel);
            if (!stamp.equals(Stamp.read(path)) || channel.size() != stamp.size()) {
                throw new IOException("CHD changed while opening");
            }
            return image;
        } catch (IOException | RuntimeException failure) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            if (failure instanceof ArithmeticException) {
                throw new ChdFormatException("CHD geometry overflow", failure);
            }
            throw failure;
        }
    }

    private static ChdDiscImage parse(Path path, Stamp stamp, FileChannel channel) throws IOException {
        long fileSize = stamp.size();
        if (fileSize < 16) {
            throw new ChdFormatException("CHD header is truncated");
        }
        ByteBuffer prefix = ByteBuffer.wrap(read(channel, fileSize, 0, 16));
        byte[] tag = new byte[8];
        prefix.get(tag);
        if (!Arrays.equals(tag, TAG)) {
            throw new ChdFormatException("Not a CHD file");
        }
        long headerLength = Integer.toUnsignedLong(prefix.getInt());
        long version = Integer.toUnsignedLong(prefix.getInt());
        if (version != 5) {
            throw new ChdFormatException("CHD version " + version + " is not supported; convert it to version 5 with "
                    + "chdman (chdman copy)");
        }
        if (headerLength != HEADER_BYTES || fileSize < HEADER_BYTES) {
            throw new ChdFormatException("CHD v5 header is truncated or has the wrong length");
        }
        ByteBuffer header = ByteBuffer.wrap(read(channel, fileSize, 0, HEADER_BYTES));
        header.position(16);
        int[] compressors = new int[4];
        for (int i = 0; i < 4; i++) {
            compressors[i] = header.getInt();
        }
        long logicalBytes = header.getLong();
        long mapOffset = header.getLong();
        long metaOffset = header.getLong();
        long hunkBytes = Integer.toUnsignedLong(header.getInt());
        long unitBytes = Integer.toUnsignedLong(header.getInt());
        byte[] rawSha1 = new byte[20];
        byte[] sha1 = new byte[20];
        byte[] parentSha1 = new byte[20];
        header.get(rawSha1);
        header.get(sha1);
        header.get(parentSha1);
        if (!allZero(parentSha1)) {
            throw new ChdFormatException("This CHD is a delta of a parent CHD, which is not supported; "
                    + "make a standalone CHD with chdman");
        }
        if (unitBytes != FRAME_BYTES || hunkBytes == 0 || hunkBytes % FRAME_BYTES != 0
                || hunkBytes > (long) FRAME_BYTES * MAX_HUNK_FRAMES) {
            throw new ChdFormatException("Not a CD-ROM CHD (unit " + unitBytes + " bytes, hunk " + hunkBytes + " bytes)");
        }
        if (logicalBytes <= 0 || logicalBytes % FRAME_BYTES != 0
                || logicalBytes > (long) FRAME_BYTES * (MAX_FRAMES + 99L * TRACK_PADDING)) {
            throw new ChdFormatException("CHD logical size is not a CD's");
        }
        long hunkCount = (logicalBytes + hunkBytes - 1) / hunkBytes;
        Metadata metadata = readMetadata(channel, fileSize, metaOffset);
        // chdman writes no digests into an uncompressed CHD (both all zero); there is nothing to check.
        boolean digests = !allZero(rawSha1) || !allZero(sha1);
        if (digests && !Arrays.equals(sha1, metadata.overallSha1(rawSha1))) {
            throw new ChdFormatException("CHD header SHA-1 does not match its raw-data SHA-1 and metadata");
        }
        List<TrackSpec> specs = metadata.tracks();
        long[] storedStart = new long[specs.size()];
        List<DiscTrack> tracks = new ArrayList<>();
        long chdFrame = 0;
        long nextDiscFrame = 0;
        for (int i = 0; i < specs.size(); i++) {
            TrackSpec spec = specs.get(i);
            long stored = spec.pregapStored() ? spec.pregap() : 0;
            long synthetic = spec.pregapStored() ? 0 : spec.pregap();
            long programFrames = spec.frames() - stored;
            if (programFrames <= 0) {
                throw new ChdFormatException("CHD track " + spec.number() + " has no program frames");
            }
            storedStart[i] = chdFrame;
            long discStart = i == 0 ? 0 : Math.addExact(nextDiscFrame, Math.addExact(synthetic, stored));
            tracks.add(new DiscTrack(spec.number(), spec.mode(), path.getFileName().toString(),
                    stored > 0 ? OptionalLong.of(0) : OptionalLong.empty(), stored, synthetic, programFrames, discStart));
            nextDiscFrame = Math.addExact(discStart, programFrames);
            long padded = Math.multiplyExact((spec.frames() + TRACK_PADDING - 1) / TRACK_PADDING, (long) TRACK_PADDING);
            chdFrame = Math.addExact(chdFrame, padded);
        }
        if (Math.multiplyExact(chdFrame, (long) FRAME_BYTES) > logicalBytes) {
            throw new ChdFormatException("CHD tracks extend past its logical size");
        }
        ChdHunkDecoder decoder = ChdHunkDecoder.open(channel, fileSize, compressors, (int) hunkBytes,
                (int) hunkCount, logicalBytes, mapOffset);
        return new ChdDiscImage(path, stamp, channel, decoder, rawSha1, metadata.descriptor(), tracks, storedStart);
    }

    private record Metadata(List<TrackSpec> tracks, byte[] descriptor, List<byte[]> checksummed) {
        byte[] overallSha1(byte[] rawSha1) {
            MessageDigest digest = sha1();
            digest.update(rawSha1);
            List<byte[]> sorted = new ArrayList<>(checksummed);
            sorted.sort(Arrays::compareUnsigned);
            for (byte[] entry : sorted) {
                digest.update(entry);
            }
            return digest.digest();
        }
    }

    private static Metadata readMetadata(FileChannel channel, long fileSize, long offset) throws IOException {
        List<TrackSpec> specs = new ArrayList<>();
        List<byte[]> checksummed = new ArrayList<>();
        java.io.ByteArrayOutputStream descriptor = new java.io.ByteArrayOutputStream();
        boolean legacy = false;
        boolean gd = false;
        int entries = 0;
        while (offset != 0) {
            if (++entries > MAX_METADATA_ENTRIES) {
                throw new ChdFormatException("CHD metadata chain is too long");
            }
            if (offset < HEADER_BYTES || offset > fileSize - 16) {
                throw new ChdFormatException("CHD metadata entry lies outside the file");
            }
            ByteBuffer header = ByteBuffer.wrap(read(channel, fileSize, offset, 16));
            int tag = header.getInt();
            int flagsAndLength = header.getInt();
            int flags = flagsAndLength >>> 24;
            int length = flagsAndLength & 0xFFFFFF;
            long next = header.getLong();
            if (length > MAX_METADATA_BYTES && (tag == TAG_CHT2 || tag == TAG_CHTR)) {
                throw new ChdFormatException("CHD track metadata entry is too large");
            }
            if (length > fileSize - offset - 16) {
                throw new ChdFormatException("CHD metadata entry runs past the end of the file");
            }
            boolean track = tag == TAG_CHT2 || tag == TAG_CHTR;
            byte[] data = null;
            if (track || (flags & MDFLAGS_CHECKSUM) != 0) {
                if (length > 16 * 1024 * 1024) {
                    throw new ChdFormatException("CHD metadata entry is too large");
                }
                data = read(channel, fileSize, offset + 16, length);
            }
            if ((flags & MDFLAGS_CHECKSUM) != 0) {
                byte[] entry = new byte[24];
                ByteBuffer.wrap(entry).putInt(tag).put(sha1().digest(data));
                checksummed.add(entry);
            }
            if (track) {
                specs.add(trackSpec(tag, data));
                descriptor.write(ByteBuffer.allocate(4).putInt(tag).array());
                descriptor.write(data);
            }
            legacy |= tag == TAG_CHCD;
            gd |= tag == TAG_CHGT || tag == TAG_CHGD;
            if (next != 0 && next <= offset) {
                throw new ChdFormatException("CHD metadata chain does not move forward");
            }
            offset = next;
        }
        if (gd) {
            throw new ChdFormatException("GD-ROM CHDs are not supported; Sonic CD is a Sega CD disc");
        }
        if (specs.isEmpty()) {
            throw new ChdFormatException(legacy ? "CHD uses the legacy binary CD metadata; convert it with chdman"
                    : "Not a CD-ROM CHD (no CD track metadata)");
        }
        specs.sort(Comparator.comparingInt(TrackSpec::number));
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).number() != i + 1) {
                throw new ChdFormatException("CHD track metadata is not numbered 1 to " + specs.size());
            }
        }
        if (specs.size() > 99) {
            throw new ChdFormatException("CHD has more than 99 tracks");
        }
        long total = 0;
        for (TrackSpec spec : specs) {
            total = Math.addExact(total, spec.frames());
        }
        if (total > MAX_FRAMES) {
            throw new ChdFormatException("CHD tracks exceed a CD's length");
        }
        return new Metadata(specs, descriptor.toByteArray(), checksummed);
    }

    private static TrackSpec trackSpec(int tag, byte[] data) throws ChdFormatException {
        int length = data.length;
        while (length > 0 && data[length - 1] == 0) {
            length--;
        }
        String text = new String(data, 0, length, StandardCharsets.US_ASCII);
        Matcher matcher = (tag == TAG_CHT2 ? CHT2 : CHTR).matcher(text);
        if (!matcher.matches()) {
            throw new ChdFormatException("Malformed CHD track metadata: " + text.substring(0, Math.min(80, text.length())));
        }
        int number = Integer.parseInt(matcher.group(1));
        DiscTrack.Mode mode = mode(matcher.group(2), number);
        String subtype = matcher.group(3);
        if (!subtype.equals("NONE") && !subtype.equals("RW") && !subtype.equals("RW_RAW")) {
            throw new ChdFormatException("CHD track " + number + " has unsupported subcode type " + subtype);
        }
        long frames = Long.parseLong(matcher.group(4));
        long pregap = 0;
        boolean stored = false;
        if (tag == TAG_CHT2) {
            pregap = Long.parseLong(matcher.group(5));
            String pregapType = matcher.group(6);
            stored = pregapType.startsWith("V");
            if (Long.parseLong(matcher.group(8)) != 0) {
                throw new ChdFormatException("CHD track " + number + " has a postgap, which is not supported");
            }
            if (stored && pregap > 0 && mode(pregapType.substring(1), number) != mode) {
                throw new ChdFormatException("CHD track " + number + " stores a pregap of another mode");
            }
            if (pregap > frames && stored) {
                throw new ChdFormatException("CHD track " + number + " pregap exceeds its frames");
            }
        }
        if (number < 1 || frames <= 0 || frames > MAX_FRAMES || pregap > MAX_FRAMES) {
            throw new ChdFormatException("CHD track " + number + " has invalid geometry");
        }
        return new TrackSpec(number, mode, frames, pregap, stored);
    }

    private static DiscTrack.Mode mode(String type, int number) throws ChdFormatException {
        return switch (type) {
            case "AUDIO" -> DiscTrack.Mode.AUDIO;
            case "MODE1_RAW", "MODE1/2352" -> DiscTrack.Mode.MODE1_2352;
            case "MODE1", "MODE1/2048" -> DiscTrack.Mode.MODE1_2048;
            default -> throw new ChdFormatException("CHD track " + number + " type " + type + " is not supported");
        };
    }

    private static int tag(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII)).getInt();
    }

    private static boolean allZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    static byte[] read(FileChannel channel, long fileSize, long position, int length) throws IOException {
        if (position < 0 || length < 0 || position > fileSize || length > fileSize - position) {
            throw new ChdFormatException("CHD read lies outside the file");
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position + buffer.position());
            if (count <= 0) {
                throw new IOException("Truncated or unreadable CHD file");
            }
        }
        return buffer.array();
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM has no SHA-1", exception);
        }
    }

    @Override
    public List<DiscTrack> tracks() {
        return tracks;
    }

    @Override
    public DiscTrack track(int number) {
        return tracks.get(trackIndex(number));
    }

    private int trackIndex(int number) {
        int index = number - 1;
        if (index < 0 || index >= tracks.size()) {
            throw new IllegalArgumentException("No track " + number);
        }
        return index;
    }

    @Override
    public synchronized Path descriptorPath() throws IOException {
        checkOpen();
        return path;
    }

    @Override
    public synchronized byte[] readDescriptorBytes() throws IOException {
        checkOpen();
        return descriptorBytes.clone();
    }

    @Override
    public synchronized void requireDescriptor(Path descriptor) throws IOException {
        checkOpen();
        if (!path.equals(Objects.requireNonNull(descriptor).toRealPath())) {
            throw new IOException("CHD does not match the open disc owner");
        }
    }

    @Override
    public synchronized List<SourceFile> sourceFiles() throws IOException {
        checkOpen();
        return sourceFiles;
    }

    /** The CHD file itself, read through the retained handle. */
    public final class SourceFile implements DiscImage.SourceFile {
        private SourceFile() { }

        @Override public Path path() { return path; }
        @Override public long bytes() { return stamp.size(); }

        @Override
        public byte[] read(long offset, int length) throws IOException {
            synchronized (ChdDiscImage.this) {
                CueDiscImage.checkRange(offset, length, bytes());
                checkOpen();
                byte[] result = ChdDiscImage.read(channel, stamp.size(), offset, length);
                checkOpen();
                return result;
            }
        }
    }

    @Override
    public synchronized byte[] readMode1Bytes(int number, long byteOffset, int length) throws IOException {
        DiscTrack track = track(number);
        if (!track.mode().isMode1()) {
            throw new IllegalArgumentException("Track is not MODE1");
        }
        CueDiscImage.checkRange(byteOffset, length, track.userDataBytes());
        checkOpen();
        byte[] result = new byte[length];
        int copied = 0;
        while (copied < length) {
            long userPosition = byteOffset + copied;
            long frame = userPosition / 2048;
            int inSector = (int) (userPosition % 2048);
            byte[] sector = frames(number, track, frame, 1);
            if (track.mode() == DiscTrack.Mode.MODE1_2352) {
                validateMode1(sector);
            }
            int count = Math.min(length - copied, 2048 - inSector);
            System.arraycopy(sector, track.mode().userDataOffset() + inSector, result, copied, count);
            copied += count;
        }
        checkOpen();
        return result;
    }

    @Override
    public synchronized byte[] readAudioFrames(int number, long firstFrame, int frameCount) throws IOException {
        if (track(number).mode() != DiscTrack.Mode.AUDIO) {
            throw new IllegalArgumentException("Track is not AUDIO");
        }
        return readRawFrames(number, firstFrame, frameCount);
    }

    @Override
    public synchronized byte[] readRawFrames(int number, long firstFrame, int frameCount) throws IOException {
        DiscTrack track = track(number);
        int frameBytes = track.mode().frameBytes();
        if (frameCount < 0 || frameCount > MAX_READ_BYTES / frameBytes
                || firstFrame < -track.pregapFrames() || firstFrame > track.programFrames()
                || frameCount > track.programFrames() - firstFrame) {
            throw new IllegalArgumentException("Frame range outside track or read limit");
        }
        checkOpen();
        byte[] result = new byte[frameCount * frameBytes];
        long storedStart = -track.filePregapFrames();
        int syntheticFrames = firstFrame < storedStart ? (int) Math.min(frameCount, storedStart - firstFrame) : 0;
        if (syntheticFrames > 0 && track.mode() != DiscTrack.Mode.AUDIO) {
            throw new IOException("Synthetic MODE1 raw frames have no stored representation");
        }
        int remaining = frameCount - syntheticFrames;
        if (remaining > 0) {
            byte[] bytes = frames(number, track, firstFrame + syntheticFrames, remaining);
            System.arraycopy(bytes, 0, result, syntheticFrames * frameBytes, bytes.length);
        }
        checkOpen();
        return result;
    }

    /** Stored frames from INDEX01-relative {@code first}, data bytes only, audio swapped to little-endian. */
    private byte[] frames(int number, DiscTrack track, long first, int count) throws IOException {
        int frameBytes = track.mode().frameBytes();
        boolean audio = track.mode() == DiscTrack.Mode.AUDIO;
        long chdFrame = Math.addExact(storedStartByTrack[trackIndex(number)],
                Math.addExact(track.filePregapFrames(), first));
        byte[] result = new byte[count * frameBytes];
        if (count == 0) {
            return result;
        }
        int hunkFrames = decoder.hunkBytes() / FRAME_BYTES;
        int firstHunk = Math.toIntExact(chdFrame / hunkFrames);
        byte[][] hunks = hunks(firstHunk, Math.toIntExact((chdFrame + count - 1) / hunkFrames) - firstHunk + 1);
        for (int i = 0; i < count; i++) {
            long frame = chdFrame + i;
            int within = (int) (frame % hunkFrames) * FRAME_BYTES;
            byte[] data = hunks[Math.toIntExact(frame / hunkFrames) - firstHunk];
            int out = i * frameBytes;
            if (audio) {
                for (int b = 0; b < frameBytes; b += 2) {
                    result[out + b] = data[within + b + 1];
                    result[out + b + 1] = data[within + b];
                }
            } else {
                System.arraycopy(data, within, result, out, frameBytes);
            }
        }
        return result;
    }

    /** Consecutive decoded hunks: cached ones reused, the rest decoded together (in parallel). */
    private byte[][] hunks(int first, int count) throws IOException {
        byte[][] result = new byte[count][];
        int[] missing = new int[count];
        int missingCount = 0;
        for (int i = 0; i < count; i++) {
            result[i] = cache.get(first + i);
            if (result[i] == null) {
                missing[missingCount++] = first + i;
            }
        }
        if (missingCount > 0) {
            byte[][] decoded = decoder.readMany(Arrays.copyOf(missing, missingCount));
            for (int i = 0; i < missingCount; i++) {
                result[missing[i] - first] = decoded[i];
            }
            for (int i = Math.max(0, missingCount - CACHED_HUNKS); i < missingCount; i++) {
                cache.put(missing[i], decoded[i]);
            }
        }
        return result;
    }

    /**
     * Decodes every hunk and compares the SHA-1 of the logical data with the header's raw-data SHA-1.
     * Returns false, checking nothing, for a CHD that carries no digests (chdman's uncompressed form).
     */
    public synchronized boolean verifyDataSha1() throws IOException {
        checkOpen();
        if (allZero(rawSha1)) {
            return false;
        }
        MessageDigest digest = sha1();
        long logical = decoder.logicalBytes();
        int batch = 256;
        for (int first = 0; first < decoder.hunkCount(); first += batch) {
            int[] indices = IntStream.range(first, Math.min(decoder.hunkCount(), first + batch)).toArray();
            byte[][] decoded = decoder.readMany(indices);
            for (int i = 0; i < indices.length; i++) {
                long remaining = logical - (long) indices[i] * decoder.hunkBytes();
                digest.update(decoded[i], 0, (int) Math.min(decoded[i].length, remaining));
            }
        }
        if (!Arrays.equals(digest.digest(), rawSha1)) {
            throw new ChdFormatException("CHD raw-data SHA-1 mismatch");
        }
        checkOpen();
        return true;
    }

    /** The compressor tags this CHD declares, e.g. {@code [cdlz, cdzl, cdfl]}; empty when uncompressed. */
    public List<String> compressors() {
        return decoder.compressorNames();
    }

    @Override
    public synchronized void checkTrack(int number) throws IOException {
        trackIndex(number);
        checkOpen();
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Disc image is closed");
        }
        if (!stamp.equals(Stamp.read(path)) || channel.size() != stamp.size()) {
            throw new IOException("Disc input changed since open: " + name);
        }
    }

    private static void validateMode1(byte[] sector) throws IOException {
        if (sector[0] != 0 || sector[11] != 0 || sector[15] != 1) {
            throw new IOException("Invalid MODE1/2352 sector header");
        }
        for (int i = 1; i <= 10; i++) {
            if (sector[i] != (byte) 0xff) {
                throw new IOException("Invalid MODE1/2352 sync pattern");
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        cache.clear();
        decoder.close();
        channel.close();
    }
}
