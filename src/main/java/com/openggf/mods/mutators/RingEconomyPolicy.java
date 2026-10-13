package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record RingEconomyPolicy(Kind kind) implements MutatorPolicy {
    public RingEconomyPolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.RING_ECONOMY; }
    @com.openggf.game.ModApi public enum Kind { RAIN, BOUNTY, LEAK, BALANCE }
}
