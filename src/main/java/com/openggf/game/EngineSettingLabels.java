package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.SonicConfiguration;
import java.util.Locale;

/** Localized presentation only; persisted keys and values remain owned by ConfigCatalog. */
final class EngineSettingLabels {
    private EngineSettingLabels() { }

    /** Dynamic IDs are exhaustive against ConfigCatalog in the catalog contract checks. */
    static String label(SonicConfiguration key) {
        return UiMessages.text("settings.key." + key.name().toLowerCase(Locale.ROOT) + ".label");
    }

    static String description(SonicConfiguration key) {
        return UiMessages.text("settings.key." + key.name().toLowerCase(Locale.ROOT) + ".help");
    }

    static String value(SonicConfiguration key, String raw) {
        if (key == SonicConfiguration.UI_LANGUAGE) return LanguagePickerScreen.displayName(raw);
        if (raw.isEmpty()) return UiMessages.text(ConfigCatalog.meta(key).type() == ConfigType.KEY
                ? "settings.value.unbound" : "settings.value.empty");
        if (ConfigCatalog.meta(key).type() == ConfigType.BOOL)
            return UiMessages.text(Boolean.parseBoolean(raw) ? "settings.value.on" : "settings.value.off");
        if (ConfigCatalog.meta(key).type() != ConfigType.ENUM) return raw;
        if (key == SonicConfiguration.DISPLAY_HUD_ANCHOR) {
            return switch (raw) {
                case "DEFAULT" -> UiMessages.text("settings.value.hud_default");
                case "LEFT_EDGE" -> UiMessages.text("settings.value.hud_left_edge");
                default -> raw;
            };
        }
        // Controller GUIDs are open-ended machine values, not catalog identifiers.
        if (!ConfigCatalog.meta(key).allowedValues().contains(raw)) return raw;
        return UiMessages.text("settings.value." + raw);
    }
}
