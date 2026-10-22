package com.openggf.game.modzone;

import com.openggf.data.Rom;
import com.openggf.game.ModApi;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Host-owned catalogue and fresh runtime construction for authored level settings. */
@ModApi
public interface ModLevelSettingsProvider {
    ModLevelSettingsProvider UNSUPPORTED = new ModLevelSettingsProvider() { };

    default List<StockScrollOption> stockScrollSources() { return List.of(); }
    default boolean supportsWater() { return false; }

    /** Team availability is checked against the resolved launch registry by the launch policy owner. */
    default void validate(ModLevelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (settings.background() != null && stockScrollSources().stream()
                .noneMatch(option -> option.source().equals(settings.background())))
            throw new IllegalArgumentException("Unsupported authored background scroll source: " + settings.background());
        if (settings.waterHeight() != null && !supportsWater())
            throw new IllegalArgumentException("This host does not support authored water");
    }

    /** A ROM is required only for settings whose selected host resources actually read it. */
    default ModZoneRuntimeServices create(ModLevelSettings settings, Rom rom) throws IOException {
        validate(settings);
        return ModZoneRuntimeServices.empty();
    }

    @ModApi
    record StockScrollOption(ModLevelSettings.StockScrollSource source, String label) {
        public StockScrollOption {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(label, "label");
            if (label.isBlank() || label.length() > 256)
                throw new IllegalArgumentException("Stock scroll label must contain 1..256 characters");
        }
    }
}
