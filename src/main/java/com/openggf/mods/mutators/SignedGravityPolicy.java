package com.openggf.mods.mutators;

import java.util.List;

/** A captured sequence of reversal contributions, composed with the current native flag. */
@com.openggf.game.ModApi
public record SignedGravityPolicy(List<Boolean> reversals, int pulsePresentations) implements MutatorPolicy {
    public SignedGravityPolicy {
        reversals = List.copyOf(reversals);
        if (reversals.isEmpty() || reversals.size() > 8 || pulsePresentations < 15
                || pulsePresentations > 180 || (long) reversals.size() * pulsePresentations > 300)
            throw new IllegalArgumentException("Gravity sequence exceeds the five-second bound");
    }
    public SignedGravityPolicy() { this(List.of(true), 120); }
    public boolean reversesAt(long agePresentations) {
        return agePresentations >= 0 && agePresentations / pulsePresentations < reversals.size()
                && reversals.get((int) (agePresentations / pulsePresentations));
    }
    @Override public MutatorCapability capability() { return MutatorCapability.SIGNED_GRAVITY; }
}
