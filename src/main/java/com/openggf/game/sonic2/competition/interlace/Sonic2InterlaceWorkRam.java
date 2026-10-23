package com.openggf.game.sonic2.competition.interlace;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable native WRAM image. Unlike VRAM ports, CPU RAM writes are bounded;
 * SAT overflow writes through the documented spill buffer into following RAM.
 */
public final class Sonic2InterlaceWorkRam {
    public static final int SIZE = 65536;
    public static final int SAT_P1 = 0xF800, SAT_P2 = 0xDD00, HSCROLL = 0xE000;
    private final byte[] bytes;

    public Sonic2InterlaceWorkRam(byte[] actualWorkRamBytes) {
        if (actualWorkRamBytes.length != SIZE) throw new IllegalArgumentException("Exact native 64K WRAM required");
        bytes = actualWorkRamBytes.clone();
    }
    public byte[] bytes() { return bytes.clone(); }
    public int readByte(int address) { return bytes[Objects.checkIndex(address, SIZE)] & 255; }
    public int readWord(int address) {
        Objects.checkFromIndexSize(address, 2, SIZE);
        return readByte(address) << 8 | readByte(address + 1);
    }
    public byte[] range(int address, int length) {
        Objects.checkFromIndexSize(address, length, SIZE);
        return Arrays.copyOfRange(bytes, address, address + length);
    }
    @Override public boolean equals(Object other) {
        return other instanceof Sonic2InterlaceWorkRam ram && Arrays.equals(bytes, ram.bytes);
    }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }

    static void writeWord(byte[] ram, int address, int word) {
        Objects.checkFromIndexSize(address, 2, SIZE);
        ram[address] = (byte) (word >>> 8); ram[address + 1] = (byte) word;
    }
}
