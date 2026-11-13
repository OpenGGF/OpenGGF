package com.openggf.level.spawn;

import com.openggf.game.GameServices;

/**
 * Null-safe reader of the configured viewport pixel width for placement
 * windowing. Falls back to the native 320 if engine services / config are not
 * available (e.g. headless unit tests), so the per-frame width supplier never throws.
 */
public final class PlacementViewportWidth {

    public static final int NATIVE = 320;

    private PlacementViewportWidth() {
    }

    public static int current() {
        try {
            // Simulation width: NATIVE widescreen keeps the ROM's 320-pixel windows.
            return com.openggf.configuration.SimulationViewport.width(GameServices.configuration());
        } catch (RuntimeException ex) {
            return NATIVE;
        }
    }
}
