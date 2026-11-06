package com.openggf.game;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.UiLanguagePreference;
import com.openggf.i18n.UiMessages;

import java.io.IOException;
import java.util.logging.Logger;

/** Host preference handoff. Persist first, then activate wording and font language together. */
public final class UiLanguageSettings {
    private static final Logger LOGGER = Logger.getLogger(UiLanguageSettings.class.getName());

    private UiLanguageSettings() { }

    /** Runs before any startup screen or ROM loading, without changing the JVM default locale. */
    public static void activateSaved(SonicConfigurationService config) {
        String preference = config.getString(SonicConfiguration.UI_LANGUAGE);
        try {
            preference = UiLanguagePreference.normalize(preference);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid ui.language preference; using the system language");
            preference = "auto";
        }
        UiMessages.selectLanguage(preference);
    }

    /** The legal screen saves its single preference without requiring a ROM or entering Settings. */
    public static void save(SonicConfigurationService config, String tag) throws IOException {
        var draft = new EngineSettingsDraft(config);
        draft.set(SonicConfiguration.UI_LANGUAGE, tag);
        apply(draft);
    }

    /** A failed publication leaves both the active language and the caller's draft intact. */
    public static void apply(EngineSettingsDraft draft) throws IOException {
        boolean languageChanged = draft.changed(SonicConfiguration.UI_LANGUAGE);
        String preference = draft.text(SonicConfiguration.UI_LANGUAGE);
        draft.apply();
        if (languageChanged) UiMessages.selectLanguage(preference);
    }
}
