package com.openggf.graphics.color;

import java.util.Locale;

/** Independent output range applied after palette fades and the selected colour profile. */
@com.openggf.game.ModApi
public enum DisplayColorExpansion {
    FULL("Full"),
    LIMITED("Limited");

    private final String label;

    DisplayColorExpansion(String label) { this.label = label; }

    public String label() { return label; }

    public static DisplayColorExpansion parse(String value) {
        if (value != null) {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        return FULL;
    }
}
