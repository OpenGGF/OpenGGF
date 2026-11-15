package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;

/**
 * WARP-local pure colour passes, not LevelStart's22-step fade or a scene clock.
 * Native pin1ecc5f3a, Time Warp Cutscene/Main.asm PaletteCycle:203-244,
 * FadeToWhite:775-834 and FadeFromWhite:859-927. Caller owns the24 selected pulses,
 * component/intensity progression, display request and publication. Dirty WORD masks and signed
 * doubled-index aliases are preserved; unknown code bytes are never invented for an out-of-view read.
 * Like the existing pure colour helper, a thrown selected-source fault publishes no staged value.
 */
public final class CdWarpPalette {
    private CdWarpPalette() { }

    public record Cycle(CdAssetBytes palette, int timerWord, int indexWord, boolean updated) {
        public Cycle {
            requirePalette(palette); requireWord(timerWord); requireWord(indexWord);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Cycle value && timerWord == value.timerWord && indexWord == value.indexWord
                    && updated == value.updated && Arrays.equals(palette.copy(), value.palette.copy());
        }
        @Override public int hashCode() {
            return 31 * (31 * (31 * Arrays.hashCode(palette.copy()) + timerWord) + indexWord) + (updated ? 1231 : 1237);
        }
    }

    /** Component0/1/2 is red/green/blue; intensity is one of the native E,C,A,8,6,4,2,0 WORDs. */
    public static CdAssetBytes fadeFromWhite(CdAssetBytes current, CdAssetBytes target, int component, int intensity) {
        requirePalette(current); requirePalette(target); requireComponent(component);
        if (intensity < 0 || intensity > 14 || (intensity & 1) != 0)
            throw new IllegalArgumentException("Native WARP white-fade intensity must be even0..14");
        int shift = component * 4; byte[] result = current.copy();
        for (int at = 0; at < 128; at += 2) {
            int kept = rotateRight(current.unsignedWord(at), shift) & 0xEEE0;
            int selected = rotateRight(target.unsignedWord(at), shift) & 14;
            int combined = rotateLeft(kept | Math.max(selected, intensity), shift);
            result[at] = (byte) (combined >>> 8); result[at + 1] = (byte) combined;
        }
        return CdAssetBytes.copyOf(result);
    }

    public static CdAssetBytes fadeToWhite(CdAssetBytes current, int component) {
        requirePalette(current); requireComponent(component); int shift = component * 4;
        byte[] result = current.copy();
        for (int at = 0; at < 128; at += 2) {
            int rotated = rotateRight(current.unsignedWord(at), shift);
            int combined = rotateLeft((rotated & 0xEEE0) | Math.min(14, (rotated & 14) + 2), shift);
            result[at] = (byte) (combined >>> 8); result[at + 1] = (byte) combined;
        }
        return CdAssetBytes.copyOf(result);
    }

    /**
     * Increment the source WORD timer once. An odd result samples neither index-derived source bytes
     * nor the colour view. On an even result the OLD index chooses the signed doubled-WORD offset;
     * the stored incremented index resets at unsigned14 before the seven LONG colour reads.
     */
    public static Cycle cycle(CdAssetBytes palette, int timerWord, int indexWord, CdAssetBytes cycleColours) {
        requirePalette(palette); requireWord(timerWord); requireWord(indexWord);
        int timer = (timerWord + 1) & 0xFFFF;
        if ((timer & 1) != 0) return new Cycle(palette, timer, indexWord, false);
        int index = (indexWord + 1) & 0xFFFF;
        if (index >= 14) index = 0;
        int offset = (short) ((indexWord + indexWord) & 0xFFFF);
        Objects.requireNonNull(cycleColours, "reached WARP palette-cycle data");
        byte[] output = palette.copy();
        for (int read = 0; read < 28; read++) output[0x11 * 2 + read] = sourceByte(offset + read, timer, index, cycleColours);
        return new Cycle(CdAssetBytes.copyOf(output), timer, index, true);
    }

    // The source data's immediately preceding timer/index are already explicit owned scalar inputs,
    // so the native signed-PC -4/-2 aliases can read those UPDATED words without a new memory owner.
    private static byte sourceByte(int offset, int timer, int index, CdAssetBytes colours) {
        return switch (offset) {
            case -4 -> (byte) (timer >>> 8);
            case -3 -> (byte) timer;
            case -2 -> (byte) (index >>> 8);
            case -1 -> (byte) index;
            default -> {
                if (offset < 0 || offset >= colours.length())
                    throw new IndexOutOfBoundsException("WARP cycle read outside finite owned source: " + offset);
                yield colours.signedByte(offset);
            }
        };
    }

    private static void requirePalette(CdAssetBytes palette) {
        Objects.requireNonNull(palette, "explicit complete WARP palette");
        if (palette.length() != 128) throw new IllegalArgumentException("WARP palette must contain all64 WORDs");
    }
    private static void requireComponent(int component) {
        if (component < 0 || component > 2) throw new IllegalArgumentException("WARP colour component must be0,1,2");
    }
    private static void requireWord(int word) {
        if (word < 0 || word > 0xFFFF) throw new IllegalArgumentException("Native counter must be a raw unsigned WORD");
    }
    private static int rotateRight(int word, int bits) {
        return bits == 0 ? word : ((word >>> bits) | (word << (16 - bits))) & 0xFFFF;
    }
    private static int rotateLeft(int word, int bits) {
        return bits == 0 ? word : ((word << bits) | (word >>> (16 - bits))) & 0xFFFF;
    }
}
