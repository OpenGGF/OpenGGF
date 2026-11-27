package com.openggf.mods.mutators;

import com.openggf.game.ModApi;

/**
 * A grace countdown of {@code graceSeconds}, then {@code ringsLost} rings leave the native
 * ring counter every {@code periodSeconds}. Holding {@code threshold} rings or fewer for
 * {@code starveSeconds} continuous seconds kills the player through the native kill routine.
 * Clocks advance only during active main-level play.
 */
@ModApi
public record RingHungerPolicy(int graceSeconds, int ringsLost, int periodSeconds,
                               int threshold, int starveSeconds) implements MutatorPolicy {
    public RingHungerPolicy {
        if (graceSeconds < 0 || graceSeconds > 600 || ringsLost < 1 || ringsLost > 100
                || periodSeconds < 1 || periodSeconds > 60 || threshold < 0 || threshold > 999
                || starveSeconds < 1 || starveSeconds > 60)
            throw new IllegalArgumentException("Ring hunger exceeds host bounds");
    }
    @Override public MutatorCapability capability() { return MutatorCapability.RING_HUNGER; }
}
