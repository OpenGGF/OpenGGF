package com.openggf.stageplay.runtime;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/** Bounded process-protocol lines; limits apply while reading, before allocating a complete record. */
public final class StageplayProcessInput implements AutoCloseable {
    private final Reader reader;
    private final char[] buffer = new char[8192];
    private int position, limit;
    private boolean skipLf;

    public StageplayProcessInput(Reader reader) { this.reader = Objects.requireNonNull(reader); }

    /** Accepts LF, CRLF and CR exactly like BufferedReader, including an unterminated final line. */
    public String readLine(int maximumCharacters) throws IOException {
        if (maximumCharacters < 0) throw new IllegalArgumentException("Negative process record limit");
        var line = new StringBuilder(Math.min(8192, maximumCharacters));
        while (true) {
            if (position == limit) {
                limit = reader.read(buffer);
                position = 0;
            }
            if (limit < 0) return line.isEmpty() ? null : line.toString();
            if (skipLf) {
                skipLf = false;
                if (buffer[position] == '\n') { position++; continue; }
            }
            int start = position;
            while (position < limit) {
                char value = buffer[position++];
                if (value == '\n' || value == '\r') {
                    line.append(buffer, start, position - start - 1);
                    skipLf = value == '\r';
                    return line.toString();
                }
                if (line.length() + position - start > maximumCharacters)
                    throw new IOException("Stageplay process record exceeds its character bound");
            }
            line.append(buffer, start, position - start);
        }
    }

    @Override public void close() throws IOException { reader.close(); }
}
