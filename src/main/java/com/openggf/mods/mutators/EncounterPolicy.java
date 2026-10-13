package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record EncounterPolicy(Kind kind, Side side) implements MutatorPolicy {
    public EncounterPolicy { java.util.Objects.requireNonNull(kind); java.util.Objects.requireNonNull(side); }
    @Override public MutatorCapability capability() { return MutatorCapability.ENCOUNTER; }
    @com.openggf.game.ModApi public enum Kind { BUMPER, GHOST, GUST, MONITOR_CAGE, VINE, FAKE_MONITOR, RESCUE_PLATFORM, BLOCK_STACK, DEBRIS, SPEED_TRAP }
    @com.openggf.game.ModApi public enum Side { AHEAD, BEHIND }
}
