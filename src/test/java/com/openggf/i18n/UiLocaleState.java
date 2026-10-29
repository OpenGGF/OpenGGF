package com.openggf.i18n;

import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextLanguage;
import java.lang.reflect.Field;

/** Test lifecycle state, without loading all catalogs for tests which never use UI text. */
public record UiLocaleState(Object view, TextLanguage textLanguage) {
    private static final Field ACTIVE;
    static {
        try {
            ACTIVE = UiMessages.class.getDeclaredField("active");
            ACTIVE.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    public static UiLocaleState capture() {
        try { return new UiLocaleState(ACTIVE.get(null), HostTextContext.current()); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    public void restore() {
        try { ACTIVE.set(null, view); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
        HostTextContext.activate(textLanguage);
    }
}
