package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The settings a game suspended to the master title was running with. The hub runs on the
 * player's saved preferences, as it does after a normal return; Settings, Game Setup and Mods
 * saved there apply to the next launch. Resuming pins every value the paused game read so it
 * carries on unchanged.
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
            if (value == null) continue;
            if (config.hasSessionOverride(key)) overrides.put(key, value);
            if (!NOT_PINNED.contains(key)) effective.put(key, value);
        }
        config.clearSessionOverrides();
        config.resolveDisplayAspect();
        return new SuspendedSettings(overrides, effective);
    }

    /**
     * Restores the paused game's overrides and pins any value saved in the hub since, so the
     * game reads exactly what it read before. The next launch clears these overrides.
     */
    public void resume(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        config.clearSessionOverrides();
        overrides.forEach(config::setSessionOverride);
        effective.forEach((key, value) -> {
            if (!Objects.equals(config.getConfigValue(key), value)) config.setSessionOverride(key, value);
        });
        config.resolveDisplayAspect();
    }

    Map<SonicConfiguration, Object> overridesForTest() {
        return overrides;
    }
}
