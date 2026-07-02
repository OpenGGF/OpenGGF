package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputBindings;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Supplier;

public final class InputBindingFactory {
    private InputBindingFactory() {
    }

    public static Supplier<InputBindings> supplier(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        return () -> fromConfig(config);
    }

    public static Supplier<InputBindings> standaloneSupplier() {
        SonicConfigurationService config = createSyntheticConfig();
        return supplier(config);
    }

    public static InputBindings fromConfig(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        return new InputBindings(
                config.getInt(SonicConfiguration.UP),
                config.getInt(SonicConfiguration.DOWN),
                config.getInt(SonicConfiguration.LEFT),
                config.getInt(SonicConfiguration.RIGHT),
                config.getInt(SonicConfiguration.P1_A),
                config.getInt(SonicConfiguration.P1_B),
                config.getInt(SonicConfiguration.P1_C),
                config.getInt(SonicConfiguration.START),
                config.getInt(SonicConfiguration.P2_UP),
                config.getInt(SonicConfiguration.P2_DOWN),
                config.getInt(SonicConfiguration.P2_LEFT),
                config.getInt(SonicConfiguration.P2_RIGHT),
                config.getInt(SonicConfiguration.P2_A),
                config.getInt(SonicConfiguration.P2_B),
                config.getInt(SonicConfiguration.P2_C),
                config.getInt(SonicConfiguration.P2_START),
                config.getBoolean(SonicConfiguration.CONTROLLER_ENABLED),
                config.getDouble(SonicConfiguration.CONTROLLER_DEADZONE),
                config.getString(SonicConfiguration.CONTROLLER_PLAYER1),
                config.getString(SonicConfiguration.CONTROLLER_PLAYER2),
                config.getInt(SonicConfiguration.DEBUG_MODE_KEY));
    }

    private static SonicConfigurationService createSyntheticConfig() {
        try {
            return SonicConfigurationService.createStandalone(Files.createTempDirectory("openggf-input-handler"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create standalone input configuration", e);
        }
    }
}
