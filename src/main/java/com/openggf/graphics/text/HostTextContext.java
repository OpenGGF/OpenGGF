package com.openggf.graphics.text;

import java.util.Locale;
import java.util.Objects;

/**
 * The active host-interface language for String-based menu chrome. The locale owner
 * activates it on the UI thread at a frame boundary; menu measurement and drawing then
 * read the same value, so a frame never measures in one language and draws in another.
 * Screens that cache wrapping, widths or hitboxes key them on {@link #revision()}.
 * This is presentation state only: it never changes {@link Locale#getDefault()}.
 */
public final class HostTextContext {
    private static volatile TextLanguage current = TextLanguage.UNDETERMINED;
    private static volatile long revision;

    private HostTextContext() { }

    public static TextLanguage current() {
        return current;
    }

    public static long revision() {
        return revision;
    }

    public static void activate(Locale locale) {
        activate(TextLanguage.of(locale));
    }

    public static synchronized void activate(TextLanguage language) {
        Objects.requireNonNull(language, "language");
        if (language.equals(current)) return;
        current = language;
        revision++;
    }
}
