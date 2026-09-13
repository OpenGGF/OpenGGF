package com.openggf.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One user-supplied ROM file as the catalogue sees it: path, size and the
 * header titles at {@code 0} and at the lock-on slot {@code 0x200000}.
 *
 * <p>Probing reads only the header fields. The full bytes and every
 * fingerprint are loaded lazily and memoised, so a directory full of
 * unrelated images costs a few small reads per file at startup and nothing
 * more until one of them is actually served.
 */
public final class PhysicalImage {

    /** Offset of the second cartridge header in a lock-on dump. */
    public static final int LOCK_ON_HEADER_OFFSET = 0x200000;

    private final Path path;
    private final long size;
    private final RomHeaderName headerAt0;
    private final RomHeaderName headerAtLockOn;
    private final Object lock = new Object();
    private RomByteReader reader;
    private final Map<Long, RomFingerprint> fingerprints = new HashMap<>();

    private PhysicalImage(Path path, long size, RomHeaderName headerAt0, RomHeaderName headerAtLockOn,
            RomByteReader preloaded) {
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.headerAt0 = Objects.requireNonNull(headerAt0, "headerAt0");
        this.headerAtLockOn = Objects.requireNonNull(headerAtLockOn, "headerAtLockOn");
        this.reader = preloaded;
    }

    /** Probes a file on disk, reading only its size and header titles. */
    public static PhysicalImage probe(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        try (FileChannel channel = FileChannel.open(normalized, StandardOpenOption.READ)) {
            long size = channel.size();
            return new PhysicalImage(normalized, size,
                    headerAt(channel, size, 0),
                    headerAt(channel, size, LOCK_ON_HEADER_OFFSET),
                    null);
        }
    }

    /**
     * Describes an in-memory image, for classifier tests and callers that
     * already hold the bytes. {@code label} stands in for the path.
     */
    public static PhysicalImage ofBytes(Path label, byte[] data) {
        RomByteReader reader = RomByteReader.fromBytes(data);
        return new PhysicalImage(label, data.length,
                headerAt(reader, 0),
                headerAt(reader, LOCK_ON_HEADER_OFFSET),
                reader);
    }

    public Path path() {
        return path;
    }

    public long size() {
        return size;
    }

    public RomHeaderName headerAt0() {
        return headerAt0;
    }

    public RomHeaderName headerAtLockOn() {
        return headerAtLockOn;
    }

    /** Loads (once) and returns the whole image as a reader. */
    public RomByteReader reader() throws IOException {
        synchronized (lock) {
            if (reader == null) {
                if (size > Integer.MAX_VALUE) {
                    throw new IOException("ROM image too large to buffer: " + path);
                }
                reader = RomByteReader.fromBytes(Files.readAllBytes(path));
            }
            return reader;
        }
    }

    /** Fingerprint of the whole image, computed once on first use. */
    public RomFingerprint fingerprint() throws IOException {
        return fingerprint(0, (int) size);
    }

    /** Fingerprint of a window of the image, computed once on first use. */
    public RomFingerprint fingerprint(int offset, int length) throws IOException {
        long key = ((long) offset << 32) | (length & 0xFFFFFFFFL);
        synchronized (lock) {
            RomFingerprint cached = fingerprints.get(key);
            if (cached == null) {
                byte[] bytes = reader().slice(offset, length);
                cached = RomFingerprint.of(bytes);
                fingerprints.put(key, cached);
            }
            return cached;
        }
    }

    /** A cache key that changes when the file is replaced on disk. */
    public String changeToken() {
        try {
            return path + ":" + size + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return path + ":" + size;
        }
    }

    @Override
    public String toString() {
        return path.getFileName() + " (" + size + " bytes, header " + headerAt0
                + (headerAtLockOn != RomHeaderName.UNKNOWN ? ", lock-on " + headerAtLockOn : "") + ")";
    }

    private static RomHeaderName headerAt(FileChannel channel, long size, long base) throws IOException {
        long end = base + RomHeaderName.INTERNATIONAL_NAME_OFFSET + RomHeaderName.NAME_LENGTH;
        if (end > size) {
            return RomHeaderName.UNKNOWN;
        }
        return RomHeaderName.of(
                readString(channel, base + RomHeaderName.DOMESTIC_NAME_OFFSET),
                readString(channel, base + RomHeaderName.INTERNATIONAL_NAME_OFFSET));
    }

    private static RomHeaderName headerAt(RomByteReader reader, int base) {
        long end = (long) base + RomHeaderName.INTERNATIONAL_NAME_OFFSET + RomHeaderName.NAME_LENGTH;
        if (end > reader.size()) {
            return RomHeaderName.UNKNOWN;
        }
        return RomHeaderName.of(
                new String(reader.slice(base + RomHeaderName.DOMESTIC_NAME_OFFSET, RomHeaderName.NAME_LENGTH)),
                new String(reader.slice(base + RomHeaderName.INTERNATIONAL_NAME_OFFSET, RomHeaderName.NAME_LENGTH)));
    }

    private static String readString(FileChannel channel, long offset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(RomHeaderName.NAME_LENGTH);
        long position = offset;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                break;
            }
            position += read;
        }
        return new String(buffer.array(), 0, buffer.position());
    }
}
