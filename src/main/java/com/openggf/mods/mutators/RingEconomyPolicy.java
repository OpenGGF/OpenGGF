package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record RingEconomyPolicy(Kind kind,int amount) implements MutatorPolicy {
    public RingEconomyPolicy {
        java.util.Objects.requireNonNull(kind);
        if(kind==Kind.COMPLETION_REWARD ? amount<1 || amount>10 : amount!=0)
            throw new IllegalArgumentException("Only completion rewards carry a bounded 1..10 amount");
    }
    public RingEconomyPolicy(Kind kind) { this(kind,0); }
    @Override public MutatorCapability capability() { return MutatorCapability.RING_ECONOMY; }
    @com.openggf.game.ModApi public enum Kind { RAIN, BOUNTY, LEAK, BALANCE, COMPLETION_REWARD }
}
