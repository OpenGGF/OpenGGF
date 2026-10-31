package com.openggf;

import com.openggf.configuration.DisplayPacing;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Window-thread coordinator. Region opportunities admit the existing semantic operation;
 * they are not native gameplay frame identities. Pictures and swaps never admit that operation.
 * Deliberate mutator/movie/trace pacing remains inside the tick owner.
 *
 * <p>The runtime owns its monotonic wall anchor. A window picture follows a completed
 * service; the picture cap only thins those pictures. Capture and forced pictures remain
 * explicit per-service exceptions.
 */
final class LiveFrameRuntime {
    // Four services bound the interval before the host polls again. A quarter-second
    // debt budget absorbs ordinary stalls; prolonged overload slows admitted time.
    static final int MAX_TICKS_PER_BATCH = 4;
    static final long MAX_DEBT_NANOS = 250_000_000L;
    // An unfocused window only watches for focus; about 30 polls a second is enough.
    static final long UNFOCUSED_WAIT_NANOS = 33_000_000L;
    private static final long SECOND = 1_000_000_000L;

    /** One host observation; sampled before the batch and again after every tick and swap. */
    record HostState(int regionHz, int pictureCap, boolean focused, boolean hostPaused,
                     Object boundary, boolean captureActive, boolean forcedPicture,
                     DisplayPacing pacing, DisplayOutput output, boolean speakerRegulated) { }

    interface Owner {
        HostState hostState();
        boolean tick();
        boolean draw(boolean capture);
        void swap();
        /** Sole live writer of the window swap interval. */
        void swapInterval(int interval);
        void serviceDevice();
    }

    private final Owner owner;
    private final LongSupplier clock;
    private FixedSimulationScheduler scheduler;
    private Object boundary;
    private boolean suspended;
    private boolean unpresented;
    private int regionHz, pictureCap, swapInterval;
    private long anchor;
    private long services, pictures, swaps, discardedWallNanos;
    private long pendingTicks;

    LiveFrameRuntime(Owner owner, LongSupplier monotonicNanos) {
        this.owner = Objects.requireNonNull(owner);
        this.clock = Objects.requireNonNull(monotonicNanos);
    }

    /** Caller polls first, then waits until the returned absolute deadline. */
    Iteration iterate() {
        Step step;
        try {
            step = run();
        } finally {
            owner.serviceDevice();
        }
        // Transition work is excluded from the new boundary; ordinary work and swap time
        // remain part of the following interval.
        if (step.reanchor()) anchor = clock.getAsLong();
        return new Iteration(step.services(), step.presented(), anchor + step.delayNanos());
    }

    private Step run() {
        long now = clock.getAsLong();
        HostState host = owner.hostState();
        boolean first = scheduler == null;
        boolean changed = configure(host);
        long elapsed = first || changed ? 0 : now - anchor;
        anchor = now;
        if (swapInterval == 0) applySwapInterval(1);
        if (!host.focused()) {
            scheduler.reset();
            suspended = true;
            pendingTicks = 0;
            return new Step(0, false, UNFOCUSED_WAIT_NANOS, false);
        }
        if (suspended || elapsed < 0 || boundary != null && !boundary.equals(host.boundary())) {
            scheduler.reset();
            elapsed = 0;
            changed = true;
        }
        suspended = false;
        boundary = host.boundary();
        var batch = scheduler.advance(elapsed);
        discardedWallNanos = saturatedAdd(discardedWallNanos, batch.discardedWallNanos());
        boolean paused = host.hostPaused();
        int allowances = paused ? Math.min(1, batch.ticks()) : batch.ticks();
        if (paused) {
            scheduler.discardWholeTickDebt();
            discardedWallNanos = saturatedAdd(discardedWallNanos,
                    (batch.ticks() - allowances + batch.pendingTicks()) * SECOND / regionHz);
        }
        int completed = 0;
        boolean drawn = false;
        boolean forcedPicture = false;
        boolean reanchor = false;
        for (int i = 0; i < allowances; i++) {
            if (!owner.tick()) return aborted(completed);
            services++;
            completed++;
            unpresented = true;
            drawn = false;
            host = owner.hostState();
            // Capture records each admitted output-time interval. It requires a current
            // post-FINAL picture even when no window swap is due, never another tick.
            forcedPicture |= host.forcedPicture();
            if (host.captureActive() || host.forcedPicture()) {
                if (!owner.draw(host.captureActive())) return aborted(completed);
                pictures++;
                drawn = true;
            }
            if (host.forcedPicture() || !boundary.equals(host.boundary()) || host.regionHz() != regionHz
                    || host.pictureCap() != pictureCap || !host.focused()) {
                scheduler.reset();
                changed = true;
                reanchor = true;
                break;
            }
        }
        // The cap is a thinning gate over completed services, never a reason to redraw.
        boolean present = changed || forcedPicture || batch.render() && unpresented;
        if (present) {
            if (!drawn && !owner.draw(false)) return aborted(completed);
            if (!drawn) pictures++;
            owner.swap();
            swaps++;
            unpresented = false;
            host = owner.hostState();
            // The picture consumed any forced request; compare later samples with this state.
            if (changed) boundary = host.boundary();
        }
        pendingTicks = changed || paused ? 0 : batch.pendingTicks();
        long delay = changed ? ceilingPeriod(regionHz) : batch.nanosUntilTick();
        return new Step(completed, present, delay, reanchor);
    }

    private boolean configure(HostState host) {
        int hz = host.regionHz(), cap = host.pictureCap();
        if (scheduler != null && hz == regionHz && cap == pictureCap) return false;
        regionHz = hz;
        pictureCap = cap;
        scheduler = new FixedSimulationScheduler(hz, cap, MAX_TICKS_PER_BATCH, MAX_DEBT_NANOS);
        return true;
    }

    private void applySwapInterval(int interval) {
        owner.swapInterval(interval);
        swapInterval = interval;
    }

    private Step aborted(int completed) {
        scheduler.reset();
        boundary = null;
        suspended = true;
        pendingTicks = 0;
        return new Step(completed, false, ceilingPeriod(regionHz), true);
    }

    Statistics statistics() {
        return new Statistics(regionHz, pictureCap, services, pictures, swaps,
                pendingTicks, discardedWallNanos);
    }

    private static long ceilingPeriod(int hz) { return (SECOND + hz - 1) / hz; }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }

    private record Step(int services, boolean presented, long delayNanos, boolean reanchor) { }

    record Iteration(int services, boolean presented, long wakeAtNanos) { }
    record Statistics(int regionHz, int pictureCap, long services, long pictures, long swaps,
                      long pendingTicks, long discardedWallNanos) { }
}
