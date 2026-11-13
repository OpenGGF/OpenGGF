package com.openggf.game.sonic2.competition.view;

/** Stored 8x16-cell attributes. Bit 10 is ignored by the native cell index. */
public record Sonic2CellWord(int value) {
    public Sonic2CellWord { value &= 0xFFFF; }
    public int patternIndex() { return value & 0x3FF; }
    public int palette() { return value >>> 13 & 3; }
    public boolean priority() { return (value & 0x8000) != 0; }
    public boolean hFlip() { return (value & 0x0800) != 0; }
    public boolean vFlip() { return (value & 0x1000) != 0; }
}
