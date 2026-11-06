package com.openggf;

/**
 * Rational region service clock, used by the live Engine coordinator.
 *
 * <p>The caller supplies monotonic elapsed nanoseconds, polls input between bounded
 * batches, and invokes the existing semantic tick operation for each allowance.
 * This class neither assigns gameplay tick identities nor admits internal ROM loops.
 * It owns no clock, sleeping, input, audio, graphics, recording or rewind state.
 */
final class FixedSimulationScheduler {
    private static final long SECOND = 1_000_000_000L;

    private final int simulationHz;
    private final int renderHz;
    private final int maxTicksPerBatch;
    private final long maxDebtUnits;
    private long tickUnits;
    private long renderUnits;
    private boolean phaseUnavailable;

    /** No defaults: runtime batch/debt limits require separate responsiveness evidence. */
    FixedSimulationScheduler(int simulationHz, int renderHz, int maxTicksPerBatch, long maxDebtNanos) {
        if (simulationHz != 50 && simulationHz != 60) {
            throw new IllegalArgumentException("simulationHz must be PAL50 or NTSC60");
        }
        if (renderHz <= 0 || renderHz > SECOND) {
            throw new IllegalArgumentException("renderHz must be between 1 and 1000000000");
        }
        if (maxTicksPerBatch <= 0) {
            throw new IllegalArgumentException("maxTicksPerBatch must be positive");
        }
        if (maxDebtNanos < ceilingDivide(SECOND, simulationHz)
                || maxDebtNanos > Long.MAX_VALUE / simulationHz) {
            throw new IllegalArgumentException("maxDebtNanos must fit at least one tick and not overflow");
        }
        this.simulationHz = simulationHz;
        this.renderHz = renderHz;
        this.maxTicksPerBatch = maxTicksPerBatch;
        this.maxDebtUnits = maxDebtNanos * simulationHz;
    }

    /**
     * Reserves one bounded batch and coalesces missed presentation deadlines.
     * Zero elapsed time drains tick debt without requesting another picture.
     *
     * <p>Elapsed time must include work and swap time since the previous observation.
     * Tick allowances are consumed here, not acknowledged as completed gameplay.
     * If a tick aborts or changes the session, the caller must stop the batch and
     * reset this scheduler plus its wall anchor before entering the new boundary.
     * Negative elapsed time is rejected without changing either phase.
     */
    Batch advance(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("elapsedNanos must be nonnegative; reset after a clock discontinuity");
        }

        // Clip before multiplying: even an arbitrarily long host stall cannot overflow.
        // Whole-nanosecond admission preserves every existing fractional tick unit.
        long admittedNanos = Math.min(elapsedNanos, (maxDebtUnits - tickUnits) / simulationHz);
        long discardedNanos = elapsedNanos - admittedNanos;
        phaseUnavailable = discardedNanos > 0;
        tickUnits += admittedNanos * simulationHz;
        int ticks = (int) Math.min(tickUnits / SECOND, maxTicksPerBatch);
        tickUnits -= ticks * SECOND;

        boolean render = elapsedNanos >= ceilingDivide(SECOND - renderUnits, renderHz);
        // Complete elapsed seconds cannot change fractional presentation phase.
        // Modulo before multiplication also bounds this arithmetic for huge stalls.
        renderUnits = (renderUnits + (elapsedNanos % SECOND) * renderHz) % SECOND;

        long pendingTicks = tickUnits / SECOND;
        long remainder = tickUnits % SECOND;
        long untilTick = pendingTicks > 0 ? 0 : ceilingDivide(SECOND - remainder, simulationHz);
        long untilRender = ceilingDivide(SECOND - renderUnits, renderHz);
        return new Batch(ticks, render, pendingTicks, remainder, discardedNanos, untilTick, untilRender);
    }

    /**
     * Discards scheduling debt/phases only. The caller excludes paused time and
     * resets its elapsed-time anchor and presentation history on pause/resume,
     * restore, session change or clock discontinuity. Region/rate changes recreate
     * the scheduler. A paused single-step and its forced picture stay caller-owned.
     */
    void reset() {
        tickUnits = 0;
        renderUnits = 0;
        phaseUnavailable = false;
    }

    /** Paused host service keeps fractional phase, but cannot accumulate whole-tick debt. */
    void discardWholeTickDebt() {
        tickUnits %= SECOND;
    }

    /** Pure observation after service work. It never reserves or consumes allowances. */
    Phase observePhase(long unaccountedElapsedNanos) {
        if (unaccountedElapsedNanos < 0) throw new IllegalArgumentException("elapsed time must be nonnegative");
        if (phaseUnavailable || tickUnits >= SECOND
                || unaccountedElapsedNanos >= ceilingDivide(SECOND - tickUnits, simulationHz)) {
            return new Unavailable();
        }
        return new Available((tickUnits + unaccountedElapsedNanos * simulationHz) / (double) SECOND);
    }

    void invalidatePhase() { phaseUnavailable = true; }

    sealed interface Phase { }
    record Available(double alpha) implements Phase { }
    record Unavailable() implements Phase { }

    private static long ceilingDivide(long numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    /**
     * {@code ticks} are allowances, not completed or numbered gameplay ticks.
     * {@code tickRemainderUnits / 1e9} is fractional tick phase, excluding whole debt;
     * it is not a valid interpolation fraction while {@code pendingTicks > 0}.
     * Discarded wall nanoseconds are per call, not a lifetime total.
     * Both relative deadlines start at the supplied elapsed-time observation, before
     * executing this batch; account for subsequent work before sleeping. The live caller
     * wakes on ticks and treats {@code render} as a thinning gate; the render deadline and
     * tick remainder stay available for presentation-time interpolation.
     */
    record Batch(int ticks, boolean render, long pendingTicks, long tickRemainderUnits,
                 long discardedWallNanos, long nanosUntilTick, long nanosUntilRender) { }
}
