package com.openggf.control;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Objects;

public record InputBindings(
        int p1Up,
        int p1Down,
        int p1Left,
        int p1Right,
        int p1A,
        int p1B,
        int p1C,
        int p1Start,
        int p2Up,
        int p2Down,
        int p2Left,
        int p2Right,
        int p2A,
        int p2B,
        int p2C,
        int p2Start,
        boolean controllerEnabled,
        double controllerDeadzone,
        String controllerPlayer1,
        String controllerPlayer2) {

    public InputBindings {
        controllerDeadzone = clampDeadzone(controllerDeadzone);
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
                config.getString(SonicConfiguration.CONTROLLER_PLAYER2));
    }

    public static double clampDeadzone(double value) {
        if (Double.isNaN(value)) {
            return 0.35;
        }
        return Math.max(0.0, Math.min(0.95, value));
    }
}
