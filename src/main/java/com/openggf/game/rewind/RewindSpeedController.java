package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

public final class RewindSpeedController {
    private final boolean coastEnabled;
    private final double accelerationPerTick;
    private final double decelerationPerTick;
    private final double maxStepsPerTick;
    private double speed;

    public static RewindSpeedController disabled() {
        return new RewindSpeedController(false, 0.0, 0.0, 1.0);
    }

    public static RewindSpeedController fromConfig(SonicConfigurationService config) {
        if (!config.getBoolean(SonicConfiguration.REWIND_TAPE_COAST_ENABLED)) {
            return disabled();
        }
        return new RewindSpeedController(
                true,
                config.getDouble(SonicConfiguration.REWIND_TAPE_COAST_ACCELERATION),
                config.getDouble(SonicConfiguration.REWIND_TAPE_COAST_DECELERATION),
                config.getDouble(SonicConfiguration.REWIND_TAPE_COAST_MAX_STEPS));
    }

    RewindSpeedController(
            boolean coastEnabled,
            double accelerationPerTick,
            double decelerationPerTick,
            double maxStepsPerTick) {
        this.coastEnabled = coastEnabled;
        this.accelerationPerTick = Math.max(0.0, accelerationPerTick);
        this.decelerationPerTick = Math.max(0.0, decelerationPerTick);
        this.maxStepsPerTick = Math.max(1.0, maxStepsPerTick);
    }

    public int stepsWhileHeld() {
        if (!coastEnabled) {
            speed = 1.0;
            return 1;
        }
        double currentSpeed = Math.max(1.0, speed);
        int steps = Math.max(1, (int) Math.floor(currentSpeed));
        speed = Math.min(maxStepsPerTick, currentSpeed + accelerationPerTick);
        return steps;
    }

    public int stepsAfterRelease() {
        if (!coastEnabled) {
            reset();
            return 0;
        }
        if (decelerationPerTick <= 0.0) {
            reset();
            return 0;
        }
        speed = Math.max(0.0, speed - decelerationPerTick);
        if (speed < 1.0) {
            reset();
            return 0;
        }
        return (int) Math.floor(speed);
    }

    public double presentationRate() {
        if (!coastEnabled || speed <= 0.0) {
            return 1.0;
        }
        return Math.max(0.05, Math.min(1.0, speed / maxStepsPerTick));
    }

    public void reset() {
        speed = 0.0;
    }
}
