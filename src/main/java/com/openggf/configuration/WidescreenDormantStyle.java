package com.openggf.configuration;

import java.util.Locale;

/** How {@link WidescreenDrawMode#NATIVE} draws not-yet-loaded layout objects in the margins. */
public enum WidescreenDormantStyle {
    /** Opaque, desaturated: visibly not yet part of the running game. */
    GREYSCALE,
    /** Ordinary palette colours. */
    NORMAL,
    /** Terrain only; dormant objects are not drawn. */
    HIDDEN;

    /** Parses a value case-insensitively; unknown or blank values select {@link #GREYSCALE}. */
    public static WidescreenDormantStyle parse(String value) {
        if (value == null || value.isBlank()) return GREYSCALE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return GREYSCALE;
        }
    }
}
