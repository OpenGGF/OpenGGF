package com.openggf.configuration;

import java.util.Locale;

/**
 * How a display wider than the native 320 pixels relates to the simulation.
 * See docs/architecture/designs/2026-11-13-widescreen-native-draw-mode.md.
 */
public enum WidescreenDrawMode {
    /** The camera, object windows and on-screen tests use the full display width. */
    EDGE,
    /** The simulation stays 320 wide; the extra display width is presentation-only margin. */
    NATIVE;

    /** Parses a value case-insensitively; unknown or blank values select {@link #EDGE}. */
    public static WidescreenDrawMode parse(String value) {
        if (value == null || value.isBlank()) return EDGE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return EDGE;
        }
    }
}
