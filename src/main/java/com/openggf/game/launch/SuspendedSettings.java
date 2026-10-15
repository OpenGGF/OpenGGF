package com.openggf.game.launch;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The settings a game suspended to the master title was running with. The hub runs on the
 * player's saved preferences, as it does after a normal return. On resume the settings in
 * {@link LiveSettings} take their new values, exactly as a fresh launch of the game would set
 * them (saved preferences plus the game's own Game Setup profile); every other value the paused
 * game read is pinned, so those changes wait for the next launch. The editable launch.*
 * profiles are menu preferences, not running-game state, and are never pinned.
 *
 * <p>Built from the public configuration surface only, so the Mod API surface of
 * {@link SonicConfigurationService} is unchanged.
 */
public final class SuspendedSettings {
    /**
     * Window size and the derived picture size. {@link SonicConfigurationService#resolveDisplayAspect}
     * recomputes the pixel sizes from the pinned aspect, and a window the player resized in the hub
     * stays as they left it.
     */
    private static final Set<SonicConfiguration> NOT_PINNED = Set.of(
            SonicConfiguration.SCREEN_WIDTH, SonicConfiguration.SCREEN_HEIGHT,
            SonicConfiguration.SCREEN_WIDTH_PIXELS, SonicConfiguration.SCREEN_HEIGHT_PIXELS);

    /** Live runtime overrides supplied by Game Setup; Default releases their previous values. */
    private static final Set<SonicConfiguration> LIVE_PROFILE_OVERRIDES = Set.of(
            SonicConfiguration.DISPLAY_ASPECT, SonicConfiguration.LIVE_REWIND_ENABLED,
            SonicConfiguration.DEBUG_VIEW_ENABLED);

    private final Map<SonicConfiguration, Object> overrides;
    private final Map<SonicConfiguration, Object> effective;

    private SuspendedSettings(Map<SonicConfiguration, Object> overrides, Map<SonicConfiguration, Object> effective) {
        this.overrides = Collections.unmodifiableMap(overrides);
        this.effective = Collections.unmodifiableMap(effective);
    }

    /** Records the running game's settings, then returns the service to the saved preferences. */
    public static SuspendedSettings suspend(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        Map<SonicConfiguration, Object> overrides = new EnumMap<>(SonicConfiguration.class);
        Map<SonicConfiguration, Object> effective = new EnumMap<>(SonicConfiguration.class);
        for (SonicConfiguration key : SonicConfiguration.values()) {
            Object value = config.getConfigValue(key);
            // Keep the recipe editable independently of the effective settings it produced.
            // Restoring launch.* would hide a profile saved in Game Setup until the next suspend.
            if (value == null || isLaunchPreference(key)) continue;
            if (config.hasSessionOverride(key)) overrides.put(key, value);
            if (!NOT_PINNED.contains(key)) effective.put(key, value);
        }
        config.clearSessionOverrides();
        config.resolveDisplayAspect();
        return new SuspendedSettings(overrides, effective);
    }

    private static boolean isLaunchPreference(SonicConfiguration key) {
        String section = ConfigCatalog.meta(key).section();
        return section != null && section.startsWith("launch.");
    }

    /**
     * Restores the paused game's settings for resuming {@code game}. Live settings take the values
     * a fresh launch would set now; the rest are pinned to what the game was running with. The
     * next launch clears these overrides. {@code inLevel} admits a new aspect (see
     * {@link LiveSettings}).
     */
    public void resume(SonicConfigurationService config, MasterTitleScreen.GameEntry game, boolean inLevel) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(game, "game");
        Map<SonicConfiguration, Object> fresh = freshLaunchValues(config, game, inLevel);
        config.clearSessionOverrides();
        overrides.forEach((key, value) -> {
            // Default releases an old live Game Setup override even if its value now matches
            // the saved preference. Preserve overrides owned elsewhere (e.g. capture output).
            if (!LIVE_PROFILE_OVERRIDES.contains(key) || !LiveSettings.appliesOnResume(key, inLevel)) {
                config.setSessionOverride(key, value);
            }
        });
        effective.forEach((key, value) -> {
            if (fresh.containsKey(key)) return;
            if (!Objects.equals(config.getConfigValue(key), value)) config.setSessionOverride(key, value);
        });
        fresh.forEach((key, value) -> {
            if (!Objects.equals(config.getConfigValue(key), value)) config.setSessionOverride(key, value);
        });
        config.resolveDisplayAspect();
    }

    /** The live settings as MasterTitleLaunchCoordinator.prepareExit would leave them for this game. */
    private static Map<SonicConfiguration, Object> freshLaunchValues(SonicConfigurationService config,
                                                                     MasterTitleScreen.GameEntry game, boolean inLevel) {
        config.clearSessionOverrides();
        LaunchProfileStore store = new LaunchProfileStore(config);
        new LaunchProfileApplier(config).apply(store.load(game), game, store);
        Map<SonicConfiguration, Object> fresh = new EnumMap<>(SonicConfiguration.class);
        for (SonicConfiguration key : SonicConfiguration.values()) {
            Object value = config.getConfigValue(key);
            if (value != null && !NOT_PINNED.contains(key) && LiveSettings.appliesOnResume(key, inLevel)) {
                fresh.put(key, value);
            }
        }
        return fresh;
    }

    Map<SonicConfiguration, Object> overridesForTest() {
        return overrides;
    }
}
