package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The setup guide's play styles and exactly which all-games settings each one writes. Anything a style
 * does not list keeps its current value; per-game Game Setup profiles ({@code launch.*}) are never written.
 * Keep and Multiplayer write nothing: Multiplayer is a shortcut to Play together, not a settings preset.
 */
public enum SetupGuidePreset {
    /** Upgrades and re-entry only: change nothing. */
    KEEP(Map.of()),
    /** The original 4:3 presentation and timing choices, closest to the original games. */
    STOCK(ordered(
            SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3",
            SonicConfiguration.REWIND_BUDGET_SCOPE, "off",
            SonicConfiguration.DISPLAY_INTERPOLATION, Boolean.FALSE,
            SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, Boolean.TRUE,
            SonicConfiguration.AUDIO_FM_CORE, "accurate",
            SonicConfiguration.LOAD_TIME_SIMULATION, "PROFILED")),
    /** A 16:9 picture whose gameplay stays on the native 320-pixel screen (Native widescreen). */
    BALANCED(ordered(
            SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9",
            SonicConfiguration.WIDESCREEN_DRAW_MODE, "NATIVE")),
    /** Balanced's framing plus render interpolation and live rewind. */
    ENHANCED(ordered(
            SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9",
            SonicConfiguration.WIDESCREEN_DRAW_MODE, "NATIVE",
            SonicConfiguration.DISPLAY_INTERPOLATION, Boolean.TRUE,
            SonicConfiguration.REWIND_BUDGET_SCOPE, "on")),
    /** Opens Play together; writes no settings. */
    MULTIPLAYER(Map.of());

    /**
     * Rewind is one switch to players. Gameplay reads it through {@code rewind.budgetScope} (writing the
     * scope mirrors {@code rewind.liveEnabled}): off is the {@code off} scope; on keeps a player's chosen
     * scope and restores the default {@code act} scope only when rewind was off.
     */
    public static final String REWIND_OFF = "off", REWIND_ON = "on";

    /** Guide states stored in {@link SonicConfiguration#SETUP_GUIDE}. Blank means the guide has not finished. */
    public static final String DONE = "done", SKIPPED = "skipped";

    private final Map<SonicConfiguration, Object> writes;

    SetupGuidePreset(Map<SonicConfiguration, Object> writes) { this.writes = writes; }

    /** The settings this style writes, in display order. */
    public Map<SonicConfiguration, Object> writes() { return writes; }

    /** One setting this style would change: its key, the current text and the style's text. */
    public record Change(SonicConfiguration key, String before, String after) { }

    /** Only the values that differ from the current configuration, in display order. */
    public List<Change> changes(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        List<Change> changes = new ArrayList<>();
        for (var entry : writes.entrySet()) {
            String before = current(config, entry.getKey());
            String after = target(config, entry.getKey(), entry.getValue());
            boolean rewindDiffers = entry.getKey() == SonicConfiguration.REWIND_BUDGET_SCOPE
                    && rewindOn(config) != REWIND_ON.equals(entry.getValue());
            if (!before.equalsIgnoreCase(after) || rewindDiffers) changes.add(new Change(entry.getKey(), before, after));
        }
        return changes;
    }

    /**
     * Finishes the guide: this style's differing values and {@code hub.setupGuide: done} in one atomic
     * configuration write. A failed write leaves the file and live configuration unchanged.
     */
    public void finish(SonicConfigurationService config) throws IOException {
        Map<SonicConfiguration, Object> edit = new LinkedHashMap<>();
        for (Change change : changes(config)) edit.put(change.key(), typed(writes.get(change.key()), change.after()));
        edit.put(SonicConfiguration.SETUP_GUIDE, DONE);
        ConfigurationPersistence.apply(config, edit);
    }

    /** Skipping writes only the guide state; games added earlier were saved when they were added. */
    public static void skip(SonicConfigurationService config) throws IOException {
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.SETUP_GUIDE, SKIPPED));
    }

    /** True once the guide was finished or skipped; it then opens only from Extras. */
    public static boolean settled(SonicConfigurationService config) {
        String state = config.getString(SonicConfiguration.SETUP_GUIDE);
        return state != null && !state.isBlank();
    }

    /** Whether live rewind is on, as gameplay reads it. */
    public static boolean rewindOn(SonicConfigurationService config) {
        return config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
    }

    private static String target(SonicConfigurationService config, SonicConfiguration key, Object value) {
        if (key != SonicConfiguration.REWIND_BUDGET_SCOPE) return String.valueOf(value);
        if (REWIND_OFF.equals(value)) return REWIND_OFF;
        String scope = current(config, key);
        return scope.isBlank() || REWIND_OFF.equalsIgnoreCase(scope)
                ? String.valueOf(config.getDefaultValue(SonicConfiguration.REWIND_BUDGET_SCOPE)) : scope;
    }

    private static Object typed(Object declared, String text) {
        return declared instanceof Boolean ? declared : text;
    }

    private static String current(SonicConfigurationService config, SonicConfiguration key) {
        Object value = config.getConfigValue(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<SonicConfiguration, Object> ordered(Object... pairs) {
        Map<SonicConfiguration, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((SonicConfiguration) pairs[i], pairs[i + 1]);
        return java.util.Collections.unmodifiableMap(map);
    }
}
