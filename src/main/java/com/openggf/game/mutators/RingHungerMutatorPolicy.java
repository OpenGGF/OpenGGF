package com.openggf.game.mutators;

/** Admitted ring hunger in native 60 Hz ticks; {@link #STOCK} is inactive. */
public record RingHungerMutatorPolicy(boolean active, int graceTicks, int ringsLost, int periodTicks,
                                      int threshold, int starveTicks) {
    public static final RingHungerMutatorPolicy STOCK = new RingHungerMutatorPolicy(false, 0, 1, 60, 0, 60);
    public static final int TICKS_PER_SECOND = 60;

    public RingHungerMutatorPolicy {
        if (graceTicks < 0 || graceTicks > 600 * TICKS_PER_SECOND || ringsLost < 1 || ringsLost > 100
                || periodTicks < TICKS_PER_SECOND || periodTicks > 60 * TICKS_PER_SECOND
                || threshold < 0 || threshold > 999
                || starveTicks < TICKS_PER_SECOND || starveTicks > 60 * TICKS_PER_SECOND)
            throw new IllegalArgumentException("Ring hunger exceeds native host bounds");
    }

    public static RingHungerMutatorPolicy ofSeconds(int grace, int ringsLost, int period, int threshold, int starve) {
        return new RingHungerMutatorPolicy(true, grace * TICKS_PER_SECOND, ringsLost, period * TICKS_PER_SECOND,
                threshold, starve * TICKS_PER_SECOND);
    }
}
