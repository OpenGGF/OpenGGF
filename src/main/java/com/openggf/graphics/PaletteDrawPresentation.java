package com.openggf.graphics;

/** Draw-only palette projection over normal, underwater and appearance texture rows. */
public final class PaletteDrawPresentation {
    private PaletteDrawPresentation() { }

    /** Runs and flushes a draw with four projected rows repeated across all effective banks. */
    public static void withRows(GraphicsManager graphics, PaletteView[] rows, Runnable draw) {
        if (!graphics.isGlInitialized()) { draw.run(); return; }
        graphics.paletteTextures().withDrawPalette(rows, draw);
    }
}
