package com.openggf.sprites.managers;

/**
 * Pure computation of the player's right level-boundary clamp.
 * At native viewport width (320) this reproduces the ROM values exactly:
 * strict -&gt; maxX + 0x128, normal -&gt; maxX + 0x128 + 0x40. The 0x128 is a
 * native-width coincidence (320 - 24 = 0x128), not an inherent property of the
 * formula. At wider viewport widths the boundary widens with the configured
 * width (declared divergence, see docs/KNOWN_DISCREPANCIES.md).
 */
public final class RightBoundary {

    private RightBoundary() {
    }

    public static int compute(int maxX, int viewportWidth, int spriteWidth,
            int rightExtra, boolean strict) {
        int boundary = maxX + viewportWidth - spriteWidth;
        if (!strict) {
            boundary += rightExtra;
        }
        return boundary;
    }
}
