package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record RelocationPolicy(Kind kind) implements MutatorPolicy {
    public RelocationPolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.RELOCATION; }
    @com.openggf.game.ModApi public enum Kind { RANDOM_CHECKPOINT, LAST_CHECKPOINT, BLINK, TEAM_SWAP, DELAYED_RETURN, STEPS }
}
