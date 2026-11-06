package com.openggf.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.openggf.graphics.text.HostTextContext;

/** Host-only presentation language. Does not change the JVM's default locale. */
public final class UiMessages {
    private static final Locale SYSTEM_LANGUAGE = Locale.getDefault(Locale.Category.DISPLAY);
    private static final class Holder {
        static final CatalogRepository CATALOGS = CatalogRepository.bundled();
    }
    private static volatile LocaleView active;
    private UiMessages() { }

    public static LocaleView current() {
        LocaleView view = active;
        if (view == null) {
            synchronized (UiMessages.class) {
                if (active == null) active = Holder.CATALOGS.view("en", 0);
                view = active;
            }
        }
        return view;
    }

    public static String text(String id) {
        return current().text(id);
    }

    public static String text(String id, Map<String, ?> arguments) {
        return current().text(id, arguments);
    }

    public static long revision() { return current().revision(); }
    public static Locale locale() { return current().locale(); }

    public static synchronized void selectLanguage(String preference) {
        String requested = preference == null || preference.equals("auto") ? SYSTEM_LANGUAGE.toLanguageTag() : preference;
        LocaleView before = current();
        if (before.locale().toLanguageTag().equalsIgnoreCase(requested)) {
            HostTextContext.activate(before.locale());
            return;
        }
        LocaleView next = Holder.CATALOGS.view(requested, before.revision() + 1);
        HostTextContext.activate(next.locale());
        if (next.locale().equals(before.locale())) return;
        active = next;
    }

    public static List<UiLanguage> languages() {
        return Holder.CATALOGS.languages();
    }

    /** A preview view which does not change the application language. */
    public static LocaleView forLanguage(String preference) {
        return Holder.CATALOGS.view(preference, 0);
    }
}
