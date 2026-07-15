package com.openggf.game.sonic3k;

/**
 * ROM-independent palette-cell contract for the fixed S3K lives HUD art.
 * Line 0 covers the icon's first piece and lives digits; line 1 covers the
 * icon's second piece.
 */
final class S3kHudPaletteUseContract {
    private static final int[] RESERVED_COLOR_MASKS = {
            0xBC1E, // line 0: 1, 2, 3, 4, 10, 11, 12, 13, 15
            0xC022, // line 1: 1, 5, 14, 15
            0,
            0
    };
    /**
     * Canonical line-1 words from {@code Pal_AIZ} (AIZ Main.bin), used by the
     * {@code $210E} second lives-icon piece in the stock {@code Map_HUD}.
     */
    private static final int[] LINE_ONE_SEGA_WORDS = {
            -1, 0x0EEE, -1, -1, -1, 0x00EE, -1, -1,
            -1, -1, -1, -1, -1, -1, 0x0ECC, 0x0044
    };

    private S3kHudPaletteUseContract() {
    }

    static boolean isReserved(int paletteLine, int color) {
        if (paletteLine < 0 || paletteLine >= RESERVED_COLOR_MASKS.length
                || color <= 0 || color >= 16) {
            return false;
        }
        return (RESERVED_COLOR_MASKS[paletteLine] & (1 << color)) != 0;
    }

    static int hostSegaWord(int paletteLine, int color) {
        if (paletteLine != 1 || !isReserved(paletteLine, color)) {
            throw new IllegalArgumentException(
                    "No fixed S3K HUD host color for line " + paletteLine + ", color " + color);
        }
        return LINE_ONE_SEGA_WORDS[color];
    }
}
