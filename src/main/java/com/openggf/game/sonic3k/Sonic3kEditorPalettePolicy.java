package com.openggf.game.sonic3k;

/** Creator palette cells accepted by the Sonic 3&K additive-level host. */
public final class Sonic3kEditorPalettePolicy {
    private Sonic3kEditorPalettePolicy() { }

    public static boolean isEditable(int line, int color) {
        return line > 0 && line < 4 && color >= 0 && color < 16
                && !S3kHudPaletteUseContract.isReserved(line, color);
    }
}
