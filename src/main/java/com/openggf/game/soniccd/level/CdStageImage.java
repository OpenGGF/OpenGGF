package com.openggf.game.soniccd.level;

import com.openggf.data.RomByteReader;

import java.util.Arrays;
import java.util.Objects;

/**
 * One stage file as the main CPU sees it: the MMD is loaded at the start of 2M Word RAM
 * ({@code WORDRAM2M}, MMD header in Level/Initialization.asm:11-13), so a pointer inside the
 * stage resolves to {@code (pointer & $FFFFFF) - $200000}. Reads are bounded by the file.
 */
public final class CdStageImage {
    /** Start of 2M Word RAM on the main CPU, where SPX loads stage MMD files. */
    public static final int WORD_RAM = 0x200000;

    private final byte[] bytes;

    public CdStageImage(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    public int size() { return bytes.length; }

    /** File offset of a 24-bit main-CPU pointer into this stage. */
    public int offsetOf(int pointer) {
        int offset = (pointer & 0xFFFFFF) - WORD_RAM;
        if (offset < 0 || offset >= bytes.length) {
            throw new IllegalArgumentException(String.format("Pointer $%06X is outside the stage file", pointer & 0xFFFFFF));
        }
        return offset;
    }

    public int u8(int offset) { return bytes[check(offset, 1)] & 0xFF; }

    public int u16(int offset) {
        check(offset, 2);
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    public int u32(int offset) {
        check(offset, 4);
        return (u16(offset) << 16) | u16(offset + 2);
    }

    public byte[] slice(int offset, int length) {
        check(offset, length);
        return Arrays.copyOfRange(bytes, offset, offset + length);
    }

    /** Bytes from {@code offset} to the end of the file. */
    public byte[] tail(int offset) {
        return slice(offset, bytes.length - check(offset, 0));
    }

    /** Reader whose addresses are file offsets, for the shared sprite-data loaders. */
    public RomByteReader reader() {
        return RomByteReader.fromBytes(bytes);
    }

    private int check(int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IllegalArgumentException("Read outside the stage file: " + offset + "+" + length);
        }
        return offset;
    }
}
