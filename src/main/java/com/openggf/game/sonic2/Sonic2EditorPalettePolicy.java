package com.openggf.game.sonic2;

/** Authored palette cells retained by the Sonic 2 additive-level host. */
public final class Sonic2EditorPalettePolicy {
    private Sonic2EditorPalettePolicy() { }

    public static boolean isEditable(int line, int color) {
        // Sonic2ModZoneAdapter replaces line zero with the active character palette.
        return line > 0 && line < 4 && color >= 0 && color < 16;
    }
}
