package com.openggf.mods.mutators;

/** Bounded modal gameplay hold. Its lease expires on held presentations while ordinary native clocks remain frozen. */
@com.openggf.game.ModApi
public record GameplayHoldPolicy(int maximumPresentations) implements MutatorPolicy {
    public GameplayHoldPolicy {
        if(maximumPresentations<1||maximumPresentations>1200) throw new IllegalArgumentException("Modal hold must be 1..1200 presentations");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.GAMEPLAY_HOLD; }
}
