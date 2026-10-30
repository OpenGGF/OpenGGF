package com.openggf;

import java.util.Objects;

/**
 * Window-thread coordinator. Region opportunities admit the existing semantic operation;
 * they are not native gameplay frame identities. Pictures and swaps never admit that operation.
 * Deliberate mutator/movie/trace pacing remains inside the tick owner.
 */
final class LiveFrameRuntime {
    // Four services bound the interval before the host polls again. A quarter-second
    // debt budget absorbs ordinary stalls; prolonged overload slows admitted time.
    static final int MAX_TICKS_PER_BATCH = 4;
    static final long MAX_DEBT_NANOS = 250_000_000L;
    private static final long SECOND = 1_000_000_000L;

    interface Owner {
        int simulationHz();
        int presentationHz();
        boolean focused();
        boolean hostPaused();
        Object boundary();
        boolean tick();
        boolean captureActive();
        boolean requiresPicture();
        boolean draw(boolean capture);
        void swap();
        void serviceDevice();
    }

    private final Owner owner;
    private FixedSimulationScheduler scheduler;
    private Object boundary;
    private boolean suspended;
    private int simulationHz, presentationHz;
    private long services, pictures, swaps, discardedWallNanos;
    private long pendingTicks;

    LiveFrameRuntime(Owner owner) { this.owner = Objects.requireNonNull(owner); }

    /** Elapsed includes preceding work/swap. Caller polls first and sleeps against this observation. */
    Iteration iterate(long elapsedNanos) {
        try {
            return run(elapsedNanos);
        } finally {
            owner.serviceDevice();
        }
    }

    private Iteration run(long elapsedNanos) {
        boolean configured = scheduler != null;
        boolean changed = configure();
        if (configured && changed) elapsedNanos = 0;
        if (!owner.focused()) {
            scheduler.reset();
            suspended = true;
            pendingTicks = 0;
            return new Iteration(0, false, 33_000_000L, false);
        }
        Object currentBoundary = owner.boundary();
        if (suspended || elapsedNanos < 0 || boundary != null && !boundary.equals(currentBoundary)) {
            scheduler.reset();
            elapsedNanos = 0;
            changed = true;
        }
        suspended = false;
        boundary = currentBoundary;
        var batch = scheduler.advance(elapsedNanos);
        discardedWallNanos = saturatedAdd(discardedWallNanos, batch.discardedWallNanos());
        boolean paused = owner.hostPaused();
        int allowances = paused ? Math.min(1, batch.ticks()) : batch.ticks();
        if (paused) {
            scheduler.discardWholeTickDebt();
            discardedWallNanos = saturatedAdd(discardedWallNanos,
                    (batch.ticks() - allowances + batch.pendingTicks()) * SECOND / simulationHz);
        }
        int completed = 0;
        boolean drawn = false;
        boolean forcedPicture = false;
        boolean reanchor = false;
        for (int i = 0; i < allowances; i++) {
            if (!owner.tick()) return aborted(completed);
            services++;
            completed++;
            drawn = false;
            // Capture records each admitted output-time interval. It requires a current
            // post-FINAL picture even when no window swap is due, never another tick.
            boolean requiresPicture = owner.requiresPicture();
            forcedPicture |= requiresPicture;
            if (owner.captureActive() || requiresPicture) {
                if (!owner.draw(owner.captureActive())) return aborted(completed);
                pictures++;
                drawn = true;
            }
            var after = owner.boundary();
            if (requiresPicture || !boundary.equals(after) || owner.simulationHz() != simulationHz
                    || owner.presentationHz() != presentationHz || !owner.focused()) {
                scheduler.reset();
                boundary = after;
                changed = true;
                reanchor = true;
                break;
            }
        }
        boolean present = batch.render() || changed || forcedPicture;
        if (present && !drawn) {
            if (!owner.draw(false)) return aborted(completed);
            pictures++;
            drawn = true;
        }
        if (present && drawn) {
            owner.swap();
            swaps++;
        }
        pendingTicks = changed || paused ? 0 : batch.pendingTicks();
        long next = changed ? Math.min(ceilingPeriod(simulationHz), ceilingPeriod(presentationHz))
                : batch.nanosUntilNextIteration();
        return new Iteration(completed, present && drawn, next, reanchor);
    }

    private boolean configure() {
        int hz = owner.simulationHz(), fps = owner.presentationHz();
        if (scheduler != null && hz == simulationHz && fps == presentationHz) return false;
        simulationHz = hz;
        presentationHz = fps;
        scheduler = new FixedSimulationScheduler(hz, fps, MAX_TICKS_PER_BATCH, MAX_DEBT_NANOS);
        return true;
    }

    private Iteration aborted(int completed) {
        scheduler.reset();
        boundary = null;
        suspended = true;
        pendingTicks = 0;
        return new Iteration(completed, false, ceilingPeriod(simulationHz), true);
    }

    Statistics statistics() {
        return new Statistics(simulationHz, presentationHz, services, pictures, swaps,
                pendingTicks, discardedWallNanos);
    }

    private static long ceilingPeriod(int hz) { return (SECOND + hz - 1) / hz; }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }

    record Iteration(int services, boolean presented, long nanosUntilNextIteration, boolean reanchor) {
        long wallAnchor(long observed, long completed) { return reanchor ? completed : observed; }
    }
    record Statistics(int simulationHz, int presentationHz, long services, long pictures, long swaps,
                      long pendingTicks, long discardedWallNanos) { }
}
