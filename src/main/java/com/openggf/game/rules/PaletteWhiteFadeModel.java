package com.openggf.game.rules;

/** Game-wide white-fade presentation; source palettes and clocks remain engine-owned. */
@com.openggf.game.ModApi
public enum PaletteWhiteFadeModel {
    /** Existing screen overlay, retained by the legacy rule constructors and S1/S2. */
    SCREEN_OVERLAY(1),
    /**
     * S3K Pal_FillWhite/Pal_FromWhite (044fa46:5148-5258): parallel component
     * steps every third V-int. Pal_ToWhite/Pal_AddColor2 (:5281-5340) instead
     * changes one component R/G/B per colour on every V-int.
     */
    ROM_PALETTE(3);

    private final int fromWhiteVintsPerStep;

    PaletteWhiteFadeModel(int fromWhiteVintsPerStep) {
        this.fromWhiteVintsPerStep = fromWhiteVintsPerStep;
    }

    /** V-int periods between FromWhite colour passes; the first pass follows its first wait. */
    public int fromWhiteVintsPerStep() {
        return fromWhiteVintsPerStep;
    }
}
