package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record TransportPolicy(Kind kind) implements MutatorPolicy {
    public TransportPolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.TRANSPORT; }
    @com.openggf.game.ModApi public enum Kind { CARRY, COMPANION, RIDE }
}
