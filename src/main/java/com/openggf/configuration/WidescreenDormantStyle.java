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

    /** Parses a value case-insensitively; missing, blank or unknown values select {@link #NORMAL}. */
    public static WidescreenDormantStyle parse(String value) {
        if (value == null || value.isBlank()) return NORMAL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return NORMAL;
        }
    }
}
