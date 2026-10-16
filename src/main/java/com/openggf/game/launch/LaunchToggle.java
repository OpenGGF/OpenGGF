package com.openggf.game.launch;

import java.util.Locale;

/**
 * A per-game launch switch that follows the all-games setting until the player
 * overrides it for that game.
 */
public enum LaunchToggle {
    /** Follow the all-games configuration value; the launch applies no override. */
    DEFAULT("global"),
    ON("on"),
    OFF("off");

    private final String persisted;

    LaunchToggle(String persisted) {
        this.persisted = persisted;
    }

    /** The YAML value stored under {@code launch.<game>.*}. */
    public String persisted() {
        return persisted;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    /** The effective switch when {@code allGames} is the all-games configuration value. */
    public boolean resolve(boolean allGames) {
        return switch (this) {
            case DEFAULT -> allGames;
            case ON -> true;
            case OFF -> false;
        };
    }

    /** Default, then On, then Off, wrapping; Left walks the same cycle backwards. */
    public LaunchToggle step(boolean forward) {
        LaunchToggle[] values = values();
        return values[Math.floorMod(ordinal() + (forward ? 1 : -1), values.length)];
    }

    /**
     * Parses a stored value. Legacy boolean profiles predate {@link #DEFAULT}: their
     * {@code false} was the stock value and now follows the all-games setting.
     */
    public static LaunchToggle parse(Object value) {
        if (value instanceof Boolean flag) return flag ? ON : DEFAULT;
        if (value == null) return DEFAULT;
        return switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "on", "true" -> ON;
            case "off" -> OFF;
            default -> DEFAULT;
        };
    }

    /** Legacy boolean constructor values: {@code true} overrides on, {@code false} follows the default. */
    public static LaunchToggle fromLegacy(boolean enabled) {
        return enabled ? ON : DEFAULT;
    }
}
