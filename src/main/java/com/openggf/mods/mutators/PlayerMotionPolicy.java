package com.openggf.mods.mutators;

import java.util.Objects;

/** Bounded intent for an ordinary movement owner; values never contain world references. */
@com.openggf.game.ModApi
public record PlayerMotionPolicy(Mode mode, int amount, int speedCap) implements MutatorPolicy {
    public PlayerMotionPolicy {
        Objects.requireNonNull(mode, "mode");
        boolean valid = switch (mode) {
            case JUMP_SCALE -> amount >= 100 && amount <= 150 && speedCap == 0;
            case TRACTION -> amount >= 25 && amount <= 200 && speedCap == 0;
            case HORIZONTAL_FORCE -> amount != 0 && Math.abs((long) amount) <= 0x10
                    && speedCap >= 0x100 && speedCap <= 0x800;
            case CRUISE -> Math.abs((long) amount) >= 0x200 && Math.abs((long) amount) <= 0x600
                    && speedCap == 0;
            case MOMENTUM_PULSE, ROLLING, GROUND_STOP -> amount == 0 && speedCap == 0;
        };
        if (!valid) throw new IllegalArgumentException("Motion values exceed the bounds for " + mode);
    }

    public PlayerMotionPolicy(Mode mode, int amount) {
        this(mode, amount, mode == Mode.HORIZONTAL_FORCE ? 0x800 : 0);
    }

    @Override public MutatorCapability capability() { return MutatorCapability.PLAYER_MOTION; }

    @com.openggf.game.ModApi
    public enum Mode { JUMP_SCALE, TRACTION, HORIZONTAL_FORCE, MOMENTUM_PULSE, CRUISE, ROLLING, GROUND_STOP }
}
