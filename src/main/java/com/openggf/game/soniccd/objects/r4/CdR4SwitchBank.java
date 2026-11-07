package com.openggf.game.soniccd.objects.r4;

import java.util.Objects;

/** Immutable, partially known switchFlags[0x20]. Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Definitions.inc:336; historical R4 Legacy -> R3 -> R6 -> R5 aliases.
 * Bits 7=current press, 6=latched press, 5=toggle. Other bits survive every writer.
 * A value is data, never live publication or restore authority. */
public final class CdR4SwitchBank {
    public static final int SIZE = 0x20;
    public static final int BASE_WORD = 0xF7E0, BASE_LONG = 0xFFFFF7E0;
    private final byte[] bytes;
    private final int known;
    public CdR4SwitchBank(byte[] bytes, int knownMask) {
        if (bytes.length != SIZE) throw new IllegalArgumentException("switchFlags requires 32 bytes");
        this.bytes = bytes.clone(); known = knownMask;
    }
    public int read(int index) {
        Objects.checkIndex(index, SIZE);
        if ((known & (1 << index)) == 0) throw new IllegalStateException("Unreached/unknown switch byte " + index);
        return bytes[index] & 255;
    }
    public CdR4SwitchBank write(int index, int value) {
        Objects.checkIndex(index, SIZE);
        byte[] next = bytes.clone(); next[index] = (byte)value;
        return new CdR4SwitchBank(next, known | (1 << index));
    }
    public int knownMask() { return known; }
    public byte[] bytes() { return bytes.clone(); }
}
