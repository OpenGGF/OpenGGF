package com.openggf.game.sonic2.bumpers;

import java.util.Objects;

/**
 * Unmodified six-byte ROM records, including the preceding boundary bytes and
 * the final $FFFF X marker. Native cursors use adjacency, even where CNZ2's
 * records are out of X order. This is deliberately separate from sorted spawns.
 */
public final class CNZNativeBumperTable {
    private static final int RECORD_BYTES = 6;
    private final int baseAddress;
    private final byte[] bytes;

    CNZNativeBumperTable(int baseAddress, byte[] precedingAndTableBytes) {
        Objects.requireNonNull(precedingAndTableBytes, "precedingAndTableBytes");
        if (precedingAndTableBytes.length < RECORD_BYTES * 2
                || precedingAndTableBytes.length % RECORD_BYTES != 0) {
            throw new IllegalArgumentException("Expected boundary bytes and complete bumper records");
        }
        this.baseAddress = baseAddress;
        this.bytes = precedingAndTableBytes.clone();
        if (entry(entryCount() - 1).x() != 0xFFFF) {
            throw new IllegalArgumentException("Missing native bumper end marker");
        }
    }

    /** Includes the terminal record, but excludes the preceding boundary bytes. */
    public int entryCount() { return bytes.length / RECORD_BYTES - 1; }

    /** Index -1 exposes the actual preceding six ROM bytes used by prev_bumper_x. */
    public Entry entry(int index) {
        if (index < -1 || index >= entryCount()) throw new IndexOutOfBoundsException(index);
        int offset = (index + 1) * RECORD_BYTES;
        return new Entry(baseAddress + index * RECORD_BYTES,
                word(offset), word(offset + 2), word(offset + 4));
    }

    private int word(int offset) {
        return (bytes[offset] & 0xFF) << 8 | (bytes[offset + 1] & 0xFF);
    }

    public record Entry(int address, int id, int x, int y) { }
}
