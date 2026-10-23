package com.openggf.data.cd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, positional access to a finite BINARY CUE/BIN image.
 * Raw MODE1 layout follows ECMA-130, second edition, sections 14 to 14.2.
 * CUE gaps follow GNU libcdio's documented INDEX 00 versus PREGAP distinction.
 * Content identity is a separate concern. Size, modification time and file-key checks detect ordinary media changes.
 *
 * @see <a href="https://ecma-international.org/wp-content/uploads/ECMA-130_2nd_edition_june_1996.pdf">ECMA-130</a>
 * @see <a href="https://libcdio.github.io/libcdio.html">GNU libcdio, Track pre-gaps</a>
 */
public final class CueDiscImage implements AutoCloseable {
    public static final int MAX_READ_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CUE_BYTES = 1024 * 1024;
    private static final Pattern FILE = Pattern.compile("FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+BINARY");
    private static final Pattern TRACK = Pattern.compile("TRACK\\s+(\\d{1,2})\\s+(AUDIO|MODE1/2352|MODE1/2048)");
    private static final Pattern INDEX = Pattern.compile("INDEX\\s+(00|01)\\s+(\\d{1,6}):(\\d{2}):(\\d{2})");
    private static final Pattern PREGAP = Pattern.compile("PREGAP\\s+(\\d{1,6}):(\\d{2}):(\\d{2})");

    private record Stamp(long size, FileTime modified, Object key) {
        static Stamp read(Path path) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new IOException("Disc input is not a regular file: " + path.getFileName());
            }
            return new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
        }
    }

    private record Source(Path path, String name, Stamp stamp, FileChannel channel) {
        void check() throws IOException {
            if (!stamp.equals(Stamp.read(path)) || channel.size() != stamp.size()) {
                throw new IOException("Disc input changed since open: " + name);
            }
        }
    }

    private static final class TrackSpec {
        final int number;
        final DiscTrack.Mode mode;
        final int source;
        long index00 = -1;
        long index01 = -1;
        long pregap = -1;

        TrackSpec(int number, DiscTrack.Mode mode, int source) {
            this.number = number;
            this.mode = mode;
            this.source = source;
        }
    }

    private final Path cuePath;
    private final Stamp cueStamp;
    private final List<Source> sources;
    private final List<DiscTrack> tracks;
    private final int[] sourceByTrack;
    private final long[] index01ByteByTrack;
    private boolean closed;

    private CueDiscImage(Path cuePath, Stamp cueStamp, List<Source> sources,
                         List<DiscTrack> tracks, int[] sourceByTrack, long[] index01ByteByTrack) {
        this.cuePath = cuePath;
        this.cueStamp = cueStamp;
        this.sources = List.copyOf(sources);
        this.tracks = List.copyOf(tracks);
        this.sourceByTrack = sourceByTrack;
        this.index01ByteByTrack = index01ByteByTrack;
    }

    /** Supports BINARY files, INDEX 00/01, PREGAP, CATALOG and REM. Other directives fail. */
    public static CueDiscImage open(Path cue) throws IOException {
        Objects.requireNonNull(cue);
        Path cuePath = cue.toRealPath();
        Stamp cueStamp = Stamp.read(cuePath);
        if (cueStamp.size() > MAX_CUE_BYTES) {
            throw new IOException("CUE exceeds size limit");
        }
        String text;
        try (InputStream input = Files.newInputStream(cuePath)) {
            byte[] bytes = input.readNBytes(MAX_CUE_BYTES + 1);
            if (bytes.length > MAX_CUE_BYTES) {
                throw new IOException("CUE exceeds size limit");
            }
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
        if (!cueStamp.equals(Stamp.read(cuePath))) {
            throw new IOException("CUE changed while opening");
        }
        List<Source> sources = new ArrayList<>();
        try {
            List<TrackSpec> specs = parse(cuePath.getParent(), text, sources);
            List<DiscTrack> tracks = new ArrayList<>();
            int[] sourceByTrack = new int[specs.size()];
            long[] index01ByteByTrack = new long[specs.size()];
            for (TrackSpec spec : specs) {
                if (spec.index01 < 0 || (spec.index00 >= 0 && spec.index00 > spec.index01)) {
                    throw new IOException("Missing or reversed INDEX for track " + spec.number);
                }
            }
            long nextDiscFrame = 0;
            long sourceByte = 0;
            for (int i = 0; i < specs.size(); i++) {
                TrackSpec spec = specs.get(i);
                Source source = sources.get(spec.source);
                boolean firstInFile = i == 0 || specs.get(i - 1).source != spec.source;
                long start = spec.index00 >= 0 ? spec.index00 : spec.index01;
                if (firstInFile) {
                    sourceByte = 0;
                    if (start != 0) {
                        throw new IOException("Unassigned bytes before first track in BINARY file");
                    }
                }
                long filePregap = spec.index01 - start;
                long index01Byte = Math.addExact(sourceByte,
                        Math.multiplyExact(filePregap, spec.mode.frameBytes()));
                long end;
                if (i + 1 < specs.size() && specs.get(i + 1).source == spec.source) {
                    TrackSpec following = specs.get(i + 1);
                    end = following.index00 >= 0 ? following.index00 : following.index01;
                } else {
                    long remaining = source.stamp.size() - sourceByte;
                    if (remaining < 0 || remaining % spec.mode.frameBytes() != 0) {
                        throw new IOException("Truncated BINARY frame in " + source.name);
                    }
                    end = Math.addExact(start, remaining / spec.mode.frameBytes());
                }
                if (end <= spec.index01) {
                    throw new IOException("Empty or overlapping track " + spec.number);
                }
                sourceByte = Math.addExact(sourceByte,
                        Math.multiplyExact(end - start, spec.mode.frameBytes()));
                if (sourceByte > source.stamp.size()) {
                    throw new IOException("Out-of-file track " + spec.number);
                }
                long synthetic = Math.max(0, spec.pregap);
                long discStart = i == 0 ? 0 : Math.addExact(nextDiscFrame, Math.addExact(synthetic, filePregap));
                long programFrames = end - spec.index01;
                tracks.add(new DiscTrack(spec.number, spec.mode, source.name,
                        spec.index00 >= 0 ? OptionalLong.of(spec.index00) : OptionalLong.empty(),
                        spec.index01, synthetic, programFrames, discStart));
                sourceByTrack[i] = spec.source;
                index01ByteByTrack[i] = index01Byte;
                nextDiscFrame = Math.addExact(discStart, programFrames);
            }
            for (Source source : sources) {
                source.check();
            }
            if (!cueStamp.equals(Stamp.read(cuePath))) {
                throw new IOException("CUE changed while opening");
            }
            return new CueDiscImage(cuePath, cueStamp, sources, tracks, sourceByTrack, index01ByteByTrack);
        } catch (IOException | RuntimeException failure) {
            for (Source source : sources) {
                try {
                    source.channel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure instanceof ArithmeticException) {
                throw new IOException("Disc geometry overflow", failure);
            }
            throw failure;
        }
    }

    private static List<TrackSpec> parse(Path directory, String text, List<Source> sources) throws IOException {
        List<TrackSpec> tracks = new ArrayList<>();
        Set<Path> seenFiles = new HashSet<>();
        TrackSpec current = null;
        int source = -1;
        boolean fileHasTrack = true;
        boolean catalogue = false;
        int lineNumber = 0;
        for (String line : text.split("\\R", -1)) {
            lineNumber++;
            line = line.strip();
            if (lineNumber == 1 && line.startsWith("\ufeff")) {
                line = line.substring(1).strip();
            }
            if (line.length() > 4096) {
                throw new IOException("CUE line exceeds limit");
            }
            if (line.isEmpty() || line.equals("REM") || line.startsWith("REM ") || line.startsWith("REM\t")) {
                continue;
            }
            Matcher matcher;
            if ((matcher = FILE.matcher(line)).matches()) {
                if (!fileHasTrack || sources.size() >= 99) {
                    throw new IOException("Empty or excessive FILE declarations");
                }
                String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                Path path = resolveSource(directory, name);
                if (!seenFiles.add(path)) {
                    throw new IOException("Repeated BINARY file is unsupported");
                }
                Stamp stamp = Stamp.read(path);
                FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
                sources.add(new Source(path, name, stamp, channel));
                source = sources.size() - 1;
                fileHasTrack = false;
                current = null;
            } else if ((matcher = TRACK.matcher(line)).matches()) {
                int number = Integer.parseInt(matcher.group(1));
                if (source < 0 || number < 1 || number > 99 || tracks.size() >= 99
                        || (!tracks.isEmpty() && number != tracks.getLast().number + 1)) {
                    throw new IOException("Invalid TRACK ordering");
                }
                DiscTrack.Mode mode = switch (matcher.group(2)) {
                    case "AUDIO" -> DiscTrack.Mode.AUDIO;
                    case "MODE1/2352" -> DiscTrack.Mode.MODE1_2352;
                    default -> DiscTrack.Mode.MODE1_2048;
                };
                current = new TrackSpec(number, mode, source);
                tracks.add(current);
                fileHasTrack = true;
            } else if ((matcher = INDEX.matcher(line)).matches()) {
                if (current == null) {
                    throw new IOException("INDEX without TRACK");
                }
                long frame = time(matcher, 2);
                if (matcher.group(1).equals("00")) {
                    if (current.index00 >= 0 || current.index01 >= 0) {
                        throw new IOException("Duplicate or misplaced INDEX 00");
                    }
                    current.index00 = frame;
                } else {
                    if (current.index01 >= 0) {
                        throw new IOException("Duplicate INDEX 01");
                    }
                    current.index01 = frame;
                }
            } else if ((matcher = PREGAP.matcher(line)).matches()) {
                if (current == null || current.pregap >= 0 || current.index00 >= 0 || current.index01 >= 0) {
                    throw new IOException("Duplicate or misplaced PREGAP");
                }
                current.pregap = time(matcher, 1);
            } else if (line.matches("CATALOG\\s+\\d{13}") && !catalogue && source < 0) {
                catalogue = true;
            } else {
                throw new IOException("Unsupported or malformed CUE directive at line " + lineNumber);
            }
        }
        if (tracks.isEmpty() || !fileHasTrack) {
            throw new IOException("CUE has no tracks or an empty FILE declaration");
        }
        return tracks;
    }

    private static Path resolveSource(Path directory, String name) throws IOException {
        String portable = name.replace('\\', '/');
        if (portable.indexOf('\0') >= 0 || portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) {
            throw new IOException("BINARY filename must be relative");
        }
        for (String part : portable.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Invalid BINARY path component");
            }
        }
        Path path = directory.resolve(portable).toRealPath();
        if (!path.startsWith(directory)) {
            throw new IOException("BINARY path escapes the CUE directory");
        }
        return path;
    }

    private static long time(Matcher matcher, int group) throws IOException {
        long minutes = Long.parseLong(matcher.group(group));
        int seconds = Integer.parseInt(matcher.group(group + 1));
        int frames = Integer.parseInt(matcher.group(group + 2));
        if (seconds >= 60 || frames >= 75) {
            throw new IOException("Invalid CUE timestamp");
        }
        return Math.addExact(Math.multiplyExact(minutes, 4500L), seconds * 75L + frames);
    }

    public List<DiscTrack> tracks() {
        return tracks;
    }

    public DiscTrack track(int number) {
        return tracks.get(trackIndex(number));
    }

    private int trackIndex(int number) {
        int index = number - tracks.getFirst().number();
        if (index < 0 || index >= tracks.size()) {
            throw new IllegalArgumentException("No track " + number);
        }
        return index;
    }

    /** Reads INDEX01-relative program bytes, stripping raw MODE1 sync/header/ECC bytes. */
    public synchronized byte[] readMode1Bytes(int number, long byteOffset, int length) throws IOException {
        DiscTrack track = track(number);
        if (!track.mode().isMode1()) {
            throw new IllegalArgumentException("Track is not MODE1");
        }
        checkRange(byteOffset, length, track.userDataBytes());
        Source source = checkedSource(number);
        byte[] result = new byte[length];
        int copied = 0;
        while (copied < length) {
            long userPosition = byteOffset + copied;
            long position = index01ByteByTrack[trackIndex(number)]
                    + Math.multiplyExact(userPosition / 2048, track.mode().frameBytes());
            int inSector = (int) (userPosition % 2048);
            byte[] sector = readStored(source, position, track.mode().frameBytes());
            if (track.mode() == DiscTrack.Mode.MODE1_2352) {
                validateMode1(sector);
            }
            int count = Math.min(length - copied, 2048 - inSector);
            System.arraycopy(sector, track.mode().userDataOffset() + inSector, result, copied, count);
            copied += count;
        }
        source.check();
        return result;
    }

    /** Returns CDDA bytes unchanged, in stored order. Negative frames address the pregap. */
    public synchronized byte[] readAudioFrames(int number, long firstFrame, int frameCount) throws IOException {
        if (track(number).mode() != DiscTrack.Mode.AUDIO) {
            throw new IllegalArgumentException("Track is not AUDIO");
        }
        return readRawFrames(number, firstFrame, frameCount);
    }

    /** Returns stored frames. Synthetic AUDIO pregaps are silence; raw data gaps are not invented. */
    public synchronized byte[] readRawFrames(int number, long firstFrame, int frameCount) throws IOException {
        DiscTrack track = track(number);
        if (frameCount < 0 || frameCount > MAX_READ_BYTES / track.mode().frameBytes()
                || firstFrame < -track.pregapFrames() || firstFrame > track.programFrames()
                || frameCount > track.programFrames() - firstFrame) {
            throw new IllegalArgumentException("Frame range outside track or read limit");
        }
        Source source = checkedSource(number);
        byte[] result = new byte[frameCount * track.mode().frameBytes()];
        long storedStart = -track.filePregapFrames();
        int syntheticFrames = firstFrame < storedStart ? (int) Math.min(frameCount, storedStart - firstFrame) : 0;
        if (syntheticFrames > 0 && track.mode() != DiscTrack.Mode.AUDIO) {
            throw new IOException("Synthetic MODE1 raw frames have no stored representation");
        }
        int remaining = frameCount - syntheticFrames;
        if (remaining > 0) {
            long position = Math.addExact(index01ByteByTrack[trackIndex(number)],
                    Math.multiplyExact(firstFrame + syntheticFrames, track.mode().frameBytes()));
            byte[] bytes = readStored(source, position,
                    remaining * track.mode().frameBytes());
            System.arraycopy(bytes, 0, result, syntheticFrames * track.mode().frameBytes(), bytes.length);
        }
        source.check();
        return result;
    }

    static void checkRange(long offset, int length, long size) {
        if (offset < 0 || length < 0 || length > MAX_READ_BYTES || offset > size || length > size - offset) {
            throw new IllegalArgumentException("Byte range outside extent or read limit");
        }
    }

    synchronized void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Disc image is closed");
        }
        if (!cueStamp.equals(Stamp.read(cuePath))) {
            throw new IOException("CUE changed since open");
        }
    }

    synchronized void checkTrack(int number) throws IOException {
        checkedSource(number);
    }

    private Source checkedSource(int number) throws IOException {
        checkOpen();
        Source source = sources.get(sourceByTrack[trackIndex(number)]);
        source.check();
        return source;
    }

    private static byte[] readStored(Source source, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int count = source.channel.read(buffer, position + buffer.position());
            if (count <= 0) {
                throw new IOException("Truncated or unreadable BINARY file: " + source.name);
            }
        }
        return buffer.array();
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
        IOException failure = null;
        for (Source source : sources) {
            try {
                source.channel.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
