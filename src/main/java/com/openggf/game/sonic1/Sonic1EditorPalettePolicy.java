package com.openggf.game.sonic1;

/** Authored palette cells retained by the Sonic 1 additive-level host. */
public final class Sonic1EditorPalettePolicy {
    private Sonic1EditorPalettePolicy() { }

    public static boolean isEditable(int line, int color) {
        // Sonic1ModZoneAdapter replaces line zero with the active character palette.
        return line > 0 && line < 4 && color >= 0 && color < 16;
    }
}
