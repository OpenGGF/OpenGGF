package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Pure selected HScroll image operations; no source selection or publication authority.
 * Native VInt_Level (Level/Main.asm:825-827, N1ecc5f3a) copies only $380 bytes. */
final class CdSelectedHscroll {
    private static final int IMAGE_BYTES = 1024;
    private static final int NATIVE_PREFIX_BYTES = 0x380;

    private CdSelectedHscroll() { }

    static CdAssetBytes copyNativePrefix(CdAssetBytes previous1024, CdAssetBytes live1024) {
        Objects.requireNonNull(previous1024, "previous selected HScroll");
        Objects.requireNonNull(live1024, "live HScroll");
        if (previous1024.length() != IMAGE_BYTES || live1024.length() != IMAGE_BYTES) {
            throw new IllegalArgumentException("Complete 1024-byte HScroll images required");
        }
        byte[] selected = previous1024.copy();
        System.arraycopy(live1024.copy(), 0, selected, 0, NATIVE_PREFIX_BYTES);
        return CdAssetBytes.copyOf(selected);
    }

    static CdAssetBytes fromCarriedWords(int[] words256) {
        Objects.requireNonNull(words256, "carried HScroll words");
        if (words256.length != IMAGE_BYTES / Integer.BYTES) {
            throw new IllegalArgumentException("Complete 256-word carried HScroll image required");
        }
        var bytes = ByteBuffer.allocate(IMAGE_BYTES).order(ByteOrder.BIG_ENDIAN);
        for (int word : words256) bytes.putInt(word);
        return CdAssetBytes.copyOf(bytes.array());
    }
}
