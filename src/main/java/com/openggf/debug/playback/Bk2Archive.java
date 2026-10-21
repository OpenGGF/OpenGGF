package com.openggf.debug.playback;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded reads count inflated bytes, independently of untrusted ZIP size declarations. */
public final class Bk2Archive {
    private Bk2Archive() { }

    public static ZipEntry findUniqueEntry(ZipFile zip, String expectedName) throws IOException {
        ZipEntry found = null;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equalsIgnoreCase(expectedName)) {
                if (found != null) throw new IOException("BK2 has duplicate entry: " + expectedName);
                if (entry.isDirectory()) throw new IOException("BK2 entry is a directory: " + expectedName);
                found = entry;
            }
        }
        return found;
    }

    public static InputStream openEntry(ZipFile zip, ZipEntry entry, long maxBytes) throws IOException {
        if (entry.getSize() > maxBytes) throw new IOException("BK2 entry exceeds inflated byte limit");
        return new LimitedInput(zip.getInputStream(entry), maxBytes, entry.getSize());
    }

    public static String readText(ZipFile zip, ZipEntry entry, int maxBytes) throws IOException {
        try (InputStream in = openEntry(zip, entry, maxBytes)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A line is bounded in UTF-8 bytes before constructing its String. CR/LF count toward total bytes. */
    public static final class LineReader implements AutoCloseable {
        private final InputStream in;
        private final int maxLineBytes;
        private final ByteArrayOutputStream line;
        private boolean skipLf;

        public LineReader(InputStream in, long maxBytes, int maxLineBytes) {
            if (maxBytes < 0 || maxLineBytes < 1) throw new IllegalArgumentException("Invalid text limits");
            this.in = new BufferedInputStream(new LimitedInput(in, maxBytes, -1),
                    (int) Math.min(8_192L, (long) maxLineBytes + 1));
            this.maxLineBytes = maxLineBytes;
            this.line = new ByteArrayOutputStream(Math.min(128, maxLineBytes));
        }

        public String readLine() throws IOException {
            line.reset();
            int value;
            while ((value = in.read()) != -1) {
                if (skipLf) {
                    skipLf = false;
                    if (value == '\n') continue;
                }
                if (value == '\r' || value == '\n') {
                    skipLf = value == '\r';
                    return line.toString(StandardCharsets.UTF_8);
                }
                if (line.size() == maxLineBytes) throw new IOException("BK2 line exceeds byte limit");
                line.write(value);
            }
            return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
        }

        @Override public void close() throws IOException { in.close(); }
    }

    private static final class LimitedInput extends FilterInputStream {
        private final long maxBytes;
        private final long declaredBytes;
        private long count;

        LimitedInput(InputStream in, long maxBytes, long declaredBytes) {
            super(in);
            this.maxBytes = maxBytes;
            this.declaredBytes = declaredBytes;
        }

        @Override public int read() throws IOException {
            int value = in.read();
            check(value == -1 ? -1 : 1);
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            long remaining = Math.min(maxBytes, declaredBytes < 0 ? maxBytes : declaredBytes) - count;
            int boundedLength = (int) Math.min(length, remaining == Long.MAX_VALUE ? remaining : remaining + 1);
            int read = in.read(bytes, offset, boundedLength);
            check(read);
            return read;
        }

        private void check(int read) throws IOException {
            if (read < 0) {
                if (declaredBytes >= 0 && count != declaredBytes) throw new IOException("BK2 entry size mismatch");
                return;
            }
            count += read;
            if (count > maxBytes) throw new IOException("BK2 entry exceeds inflated byte limit");
            if (declaredBytes >= 0 && count > declaredBytes) throw new IOException("BK2 entry size mismatch");
        }
    }
}
