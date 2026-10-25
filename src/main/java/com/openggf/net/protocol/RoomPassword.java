package com.openggf.net.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** A bounded attempt credential. Callers close their owned value after the attempt. */
@com.openggf.game.ModApi
public final class RoomPassword implements AutoCloseable {
    public static final int MIN_CODE_POINTS = 15;
    public static final int MAX_CODE_POINTS = 128;
    public static final int MAX_UTF8_BYTES = 512;
    private char[] value;

    public RoomPassword(char[] characters) {
        if (characters == null || characters.length > MAX_CODE_POINTS * 2) {
            throw new IllegalArgumentException("invalid room password");
        }
        String text = new String(characters);
        int points = text.codePointCount(0, text.length());
        if (points < MIN_CODE_POINTS || points > MAX_CODE_POINTS
                || text.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES
                || text.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)) {
            throw new IllegalArgumentException("invalid room password");
        }
        value = characters.clone();
    }

    public static RoomPassword of(String text) {
        if (text == null || text.length() > MAX_CODE_POINTS * 2) {
            throw new IllegalArgumentException("invalid room password");
        }
        char[] characters = text.toCharArray();
        try {
            return new RoomPassword(characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    public synchronized char[] copyCharacters() {
        if (value == null) throw new IllegalStateException("room password is closed");
        return value.clone();
    }

    synchronized String wireValue() {
        if (value == null) throw new IllegalStateException("room password is closed");
        return new String(value);
    }

    @Override
    public synchronized void close() {
        if (value != null) Arrays.fill(value, '\0');
        value = null;
    }

    @Override
    public String toString() {
        return "RoomPassword[redacted]";
    }
}
