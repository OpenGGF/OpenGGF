package com.openggf.graphics.text;

import java.util.Locale;
import java.util.Objects;

/**
 * Language context for shaping, line breaking, case mapping and font preference.
 * Callers pass it explicitly; host text never reads {@link Locale#getDefault()}.
 */
public record TextLanguage(Locale locale, TextDirection direction) {
    public static final TextLanguage UNDETERMINED = new TextLanguage(Locale.ROOT, TextDirection.AUTO);

    public TextLanguage {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(direction, "direction");
    }

    /** First-strong paragraph direction, as the message catalog expects. */
    public static TextLanguage of(Locale locale) {
        return new TextLanguage(locale, TextDirection.AUTO);
    }

    public static TextLanguage of(String bcp47Tag) {
        return of(Locale.forLanguageTag(Objects.requireNonNull(bcp47Tag, "bcp47Tag")));
    }

    public String tag() {
        return locale.toLanguageTag();
    }

    /**
     * How specifically {@code pattern} (a BCP 47 tag such as {@code zh-Hant} or {@code zh-TW})
     * describes this language: zero when it does not match, otherwise one plus its matched
     * script and region subtags.
     */
    int match(String pattern) {
        Locale wanted = Locale.forLanguageTag(pattern);
        if (wanted.getLanguage().isEmpty() || !wanted.getLanguage().equals(locale.getLanguage())) return 0;
        int score = 1;
        if (!wanted.getScript().isEmpty()) {
            if (!wanted.getScript().equals(locale.getScript())) return 0;
            score++;
        }
        if (!wanted.getCountry().isEmpty()) {
            if (!wanted.getCountry().equals(locale.getCountry())) return 0;
            score++;
        }
        return score;
    }
}
