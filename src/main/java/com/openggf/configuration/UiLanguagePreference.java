package com.openggf.configuration;

import java.util.IllformedLocaleException;
import java.util.Locale;

/** Stable language preference syntax, independent of catalog discovery and presentation state. */
public final class UiLanguagePreference {
    private UiLanguagePreference() { }

    public static String normalize(String value) {
        String tag = value == null ? "" : value.trim();
        if (tag.equalsIgnoreCase("auto")) return "auto";
        try {
            if (tag.isEmpty()) throw new IllformedLocaleException("Empty language tag");
            return new Locale.Builder().setLanguageTag(tag).build().toLanguageTag();
        } catch (IllformedLocaleException e) {
            throw new IllegalArgumentException("Use auto or a BCP 47 language tag", e);
        }
    }
}
