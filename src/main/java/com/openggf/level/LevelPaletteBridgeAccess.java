package com.openggf.level;

import com.openggf.game.palette.PaletteOwnershipRegistry;

/** Engine-only access from the shared frame sequencer into level palette wiring. */
public final class LevelPaletteBridgeAccess {
    private LevelPaletteBridgeAccess() {
    }

    public static void submitCustomZonePaletteClaims(LevelManager levelManager,
                                                     PaletteOwnershipRegistry registry) {
        levelManager.submitCustomZonePaletteClaimsForEngine(registry);
    }
}
