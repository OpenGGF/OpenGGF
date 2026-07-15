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

    private S3kHudPaletteUseContract() {
    }

    static boolean isReserved(int paletteLine, int color) {
        if (paletteLine < 0 || paletteLine >= RESERVED_COLOR_MASKS.length
                || color <= 0 || color >= 16) {
            return false;
        }
        return (RESERVED_COLOR_MASKS[paletteLine] & (1 << color)) != 0;
    }
}
