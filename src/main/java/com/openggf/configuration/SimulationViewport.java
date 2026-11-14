package com.openggf.configuration;

/**
 * Resolves the viewport width the game simulation sees, as opposed to the display
 * width ({@link SonicConfiguration#SCREEN_WIDTH_PIXELS}) the renderer draws.
 *
 * <p>In {@link WidescreenDrawMode#EDGE} the two are equal. In
 * {@link WidescreenDrawMode#NATIVE} the simulation is the native 320-pixel window and
 * the renderer draws an equal presentation-only inset on each side of it.
 */
public final class SimulationViewport {
    public static final int NATIVE_WIDTH = 320;

    private SimulationViewport() { }

    /** The width camera, placement windows and on-screen tests must use. */
    public static int width(SonicConfigurationService config) {
        int display = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        return width(display, WidescreenDrawMode.parse(config.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE)));
    }

    public static int width(int displayWidth, WidescreenDrawMode mode) {
        if (displayWidth <= 0) return NATIVE_WIDTH;
        // The simulation is never wider than the display: NATIVE caps it at 320.
        return mode == WidescreenDrawMode.NATIVE ? Math.min(displayWidth, NATIVE_WIDTH) : displayWidth;
    }

    /** Pixels of presentation-only margin drawn on each side of the simulation window. */
    public static int presentationInset(int displayWidth, int simulationWidth) {
        return Math.max(0, displayWidth - simulationWidth) / 2;
    }
}
