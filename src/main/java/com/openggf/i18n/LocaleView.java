package com.openggf.i18n;

import java.util.Locale;
import java.util.Map;

/** Immutable wording and grammar for one complete presentation transaction. */
public final class LocaleView {
    private final Locale locale;
    private final long revision;
    private final Map<String, CatalogPattern> messages;

    LocaleView(Locale locale, long revision, Map<String, CatalogPattern> messages) {
        this.locale = locale;
        this.revision = revision;
        this.messages = Map.copyOf(messages);
    }
    public String text(String id) {
        return text(id, Map.of());
    }

    public String text(String id, Map<String, ?> arguments) {
        CatalogPattern pattern = messages.get(id);
        if (pattern == null) throw new IllegalArgumentException("Unknown UI message: " + id);
        return pattern.format(arguments);
    }

    public Locale locale() {
        return locale;
    }

    public long revision() {
        return revision;
    }
}
