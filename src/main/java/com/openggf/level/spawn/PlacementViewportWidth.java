package com.openggf.level.spawn;

import com.openggf.configuration.SonicConfiguration;
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
            return GameServices.configuration().getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        } catch (RuntimeException ex) {
            return NATIVE;
        }
    }

    /**
     * Simulation width for placement windows: the gameplay camera's width when a gameplay
     * runtime exists (as the object window already uses), else {@link #current()} with
     * NATIVE widescreen draw mode keeping the ROM's 320-pixel windows on a wider display.
     */
    public static int simulation() {
        if (GameServices.hasRuntime()) return GameServices.camera().getWidth();
        int display = current();
        if (display <= NATIVE) return display;
        return com.openggf.configuration.SimulationViewport.width(display,
                com.openggf.configuration.WidescreenDrawMode.parse(
                        GameServices.configuration().getString(SonicConfiguration.WIDESCREEN_DRAW_MODE)));
    }
}
