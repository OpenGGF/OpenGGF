package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record ChallengePolicy(Kind kind) implements MutatorPolicy {
    public ChallengePolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.CHALLENGE; }
    @com.openggf.game.ModApi public enum Kind { TASK, KEEP_MOVING, AMNESTY, LANDMARK, SKILL_CHAIN }
}
