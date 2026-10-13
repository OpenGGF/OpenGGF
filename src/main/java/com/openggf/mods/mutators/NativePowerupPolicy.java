package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record NativePowerupPolicy(Kind kind, int durationTicks) implements MutatorPolicy {
    public NativePowerupPolicy {
        java.util.Objects.requireNonNull(kind);
        if (durationTicks < 8 || durationTicks > 3600) throw new IllegalArgumentException("Native power-up duration must be 8..3600 ticks");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.NATIVE_POWERUP; }
    @com.openggf.game.ModApi public enum Kind { SHIELD, SPEED_SHOES, INVINCIBILITY, AIR }
}
