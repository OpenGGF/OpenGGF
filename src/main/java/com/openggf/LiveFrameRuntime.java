package com.openggf;

import com.openggf.configuration.DisplayPacing;
import com.openggf.level.PictureSelection;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Window-thread coordinator. Region opportunities admit the existing semantic operation;
 * they are not native gameplay frame identities. Pictures and swaps never admit that operation.
 * Deliberate mutator/movie/trace pacing remains inside the tick owner.
 *
 * <p>The runtime owns its monotonic wall anchor and one service cadence. {@code Exact}
 * admits services on host time and shows each completed service, thinned by the picture
 * cap. {@code Probing} keeps exact services while it measures every swap at the nominated
 * interval. {@code Locked} runs one service per paced swap. Capture and forced pictures
 * remain explicit per-service exceptions in every cadence.
 */
final class LiveFrameRuntime {
    // Four services bound the interval before the host polls again. A quarter-second
    // debt budget absorbs ordinary stalls; prolonged overload slows admitted time.
    static final int MAX_TICKS_PER_BATCH = 4;
    static final long MAX_DEBT_NANOS = 250_000_000L;
    // An unfocused window only watches for focus; about 30 polls a second is enough.
    static final long UNFOCUSED_WAIT_NANOS = 33_000_000L;
    private static final long SECOND = 1_000_000_000L;
    private static final long NO_REANCHOR = -1;

    /** One host observation; sampled before the batch and again after every tick and swap. */
    record HostState(int regionHz, int pictureCap, boolean focused, boolean hostPaused,
                     Object boundary, boolean captureActive, boolean forcedPicture,
                     DisplayPacing pacing, DisplayOutput output, boolean speakerRegulated,
                     boolean interpolation, boolean screenshotPending, boolean automaticPictureCap) { }

    interface Owner {
        HostState hostState();
        /** Called once per admitted service. The iteration's decision tells the owner whether
         * to retain interpolation history after advancing; false drops old history. */
        boolean tick(boolean interpolationActive);
        boolean draw(LivePictureRequest picture);
        void swap();
        /** Sole live writer of the window swap interval. */
        void swapInterval(int interval);
        void serviceDevice();
    }

    private sealed interface Cadence permits Exact, Probing, Locked { }
    private record Exact() implements Cadence { }
    private record Probing(int interval, DisplayCadencePolicy.Request request) implements Cadence { }
    private record Locked(int interval, DisplayCadencePolicy.Request request) implements Cadence { }
    private static final Exact EXACT = new Exact();

    private final Owner owner;
    private final LongSupplier clock;
    private final DisplayCadencePolicy policy = new DisplayCadencePolicy();
    private Cadence cadence = EXACT;
    private FixedSimulationScheduler scheduler;
    private Object boundary;
    private boolean suspended;
    private boolean unpresented;
    private boolean latestBarrier;
    private int regionHz, pictureCap, swapInterval;
    private long anchor;
    private long services, pictures, swaps, discardedWallNanos;
    private InterpolationStatus interpolationStatus = InterpolationStatus.OFF;
    private long blendedPictures;
    private double lastAlpha = Double.NaN;
    private long rateStart, servicesAtRateStart, picturesAtRateStart, blendedAtRateStart;
    private double servicesPerSecond, picturesPerSecond, blendedPerSecond;
    private long pendingTicks;
    private SwapPacingPolicy swapPacing;
    private HostState measurementHost;
    private PendingPresent pendingPresent;

    private record PendingPresent(long deadline, long nextTickDeadline) { }

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
        if (step.reanchorDelay() == NO_REANCHOR) {
            return new Iteration(step.services(), step.presented(), step.wakeAtNanos());
        }
        // Transition work is excluded from the new boundary; ordinary work and swap time
        // remain part of the following interval.
        anchor = clock.getAsLong();
        return new Iteration(step.services(), step.presented(), anchor + step.reanchorDelay());
    }

    private Step run() {
        long now = clock.getAsLong();
        HostState host = owner.hostState();
        boolean first = scheduler == null;
        boolean changed = configure(host);
        long elapsed = first || changed ? 0 : now - anchor;
        boolean measurementChanged = !host.equals(measurementHost) || changed || suspended
                || boundary != null && !boundary.equals(host.boundary()) || elapsed < 0;
        if (measurementChanged) {
            swapPacing = new SwapPacingPolicy(host.regionHz(), host.output().advertisedRefreshHz());
            pendingPresent = null;
            restartRates(now);
            measurementHost = host;
        }
        if (host.focused()) sampleRates(now);
        if (pendingPresent != null) {
            var pending = pendingPresent;
            if (now < pending.deadline()) {
                return new Step(0, false, pending.deadline(), NO_REANCHOR);
            }
            pendingPresent = null;
            if (now < pending.nextTickDeadline()) {
                long returned = swap();
                policy.observeSwap(returned);
                swapPacing.observeSwap(returned);
                return new Step(0, true, pending.nextTickDeadline(), NO_REANCHOR);
            }
            // The host woke late. Service due input/audio before replacing the stale picture.
        }
        anchor = now;
        if (swapInterval == 0) applySwapInterval(1);
        if (!host.focused()) {
            interpolationStatus = InterpolationStatus.UNFOCUSED;
            toExact();
            scheduler.reset();
            suspended = true;
            pendingTicks = 0;
            return new Step(0, false, now + UNFOCUSED_WAIT_NANOS, NO_REANCHOR);
        }
        boolean usable = usable(host);
        if (usable && (cadence instanceof Locked locked && locked.interval() != 1
                || cadence instanceof Probing probing && probing.interval() != 1)) toExact();
        var request = new DisplayCadencePolicy.Request(host.pacing(), host.speakerRegulated(),
                host.pictureCap(), host.regionHz(), host.output());
        switch (cadence) {
            case Probing probing when changed || !probing.request().equals(request) -> toExact();
            case Locked locked when changed || !locked.request().equals(request) -> toExact();
            default -> { }
        }
        if (cadence instanceof Exact) {
            int interval = policy.nominate(request);
            if (interval > 0 && (!usable || interval == 1)) {
                policy.probe(request, interval);
                applySwapInterval(interval);
                cadence = new Probing(interval, request);
            }
        }
        if (cadence instanceof Locked) {
            interpolationStatus = Objects.requireNonNullElse(inactiveStatus(host), InterpolationStatus.DISPLAY_LOCKED);
            return runLocked(host);
        }
        return runExact(host, elapsed, changed);
    }

    private Step runExact(HostState host, long elapsed, boolean changed) {
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
        boolean active = usable(host) && !changed && cadence instanceof Exact;
        int completed = 0;
        boolean drawn = false;
        boolean forcedPicture = false;
        long reanchorDelay = NO_REANCHOR;
        for (int i = 0; i < allowances; i++) {
            if (!owner.tick(active)) return aborted(completed);
            services++;
            completed++;
            unpresented = true;
            latestBarrier = false;
            drawn = false;
            host = owner.hostState();
            // Capture records each admitted output-time interval. It requires a current
            // post-FINAL picture even when no window swap is due, never another tick.
            forcedPicture |= host.forcedPicture();
            if (host.captureActive() || host.forcedPicture()) {
                latestBarrier = true;
                if (!owner.draw(exactPicture(host.captureActive()))) return aborted(completed);
                pictures++;
                drawn = true;
            }
            if (host.forcedPicture() || !boundary.equals(host.boundary()) || host.regionHz() != regionHz
                    || host.pictureCap() != pictureCap || !host.focused()) {
                scheduler.reset();
                changed = true;
                reanchorDelay = ceilingPeriod(regionHz);
                break;
            }
        }
        if (changed) {
            policy.boundaryChanged();
            swapPacing.reset();
            restartRates(clock.getAsLong());
        }
        boolean probing = cadence instanceof Probing;
        // Exact pictures thin services; interpolation also uses render-only deadlines.
        // A probe measures every swap, so it presents on each paced return.
        boolean present = probing || changed || forcedPicture || host.screenshotPending()
                || batch.render() && (unpresented || active);
        pendingTicks = changed || paused ? 0 : batch.pendingTicks();
        long wake = anchor + (changed ? ceilingPeriod(regionHz) : batch.nanosUntilTick());
        if (active) wake = Math.min(wake, anchor + batch.nanosUntilRender());
        if (!present) return new Step(completed, false, wake, reanchorDelay);
        LivePictureRequest picture = exactPicture(false);
        if (host.screenshotPending() || forcedPicture || changed) latestBarrier = true;
        InterpolationStatus inactive = inactiveStatus(host);
        if (!active || inactive != null) {
            interpolationStatus = inactive != null ? inactive
                    : changed ? InterpolationStatus.BOUNDARY : InterpolationStatus.DISPLAY_LOCKED;
        } else if (drawn || latestBarrier) {
            interpolationStatus = InterpolationStatus.EXACT_PICTURE;
        } else {
            var phase = scheduler.observePhase(clock.getAsLong() - anchor);
            if (phase instanceof FixedSimulationScheduler.Available available) {
                picture = new LivePictureRequest.Window(PictureSelection.between(available.alpha()));
                interpolationStatus = InterpolationStatus.BLENDING;
                lastAlpha = available.alpha();
                blendedPictures++;
            } else {
                interpolationStatus = InterpolationStatus.BEHIND;
                wake = clock.getAsLong();
            }
        }
        if (!drawn && !owner.draw(picture)) return aborted(completed);
        if (!drawn) pictures++;
        boolean measure = !probing && !active && !changed && completed == 1 && pendingTicks == 0
                && !host.hostPaused() && !host.captureActive() && !host.screenshotPending()
                && !forcedPicture && pictureCap >= regionHz;
        if (measure) {
            long ready = clock.getAsLong();
            swapPacing.observeWork(ready - anchor);
            long nextTick = anchor + batch.nanosUntilTick();
            long tickDeadline = nextTick - ceilingPeriod(regionHz);
            long deadline = swapPacing.presentDeadline(tickDeadline, nextTick, ready);
            if (deadline > ready) {
                pendingPresent = new PendingPresent(deadline, nextTick);
                return new Step(completed, false, deadline, NO_REANCHOR);
            }
        } else swapPacing.reset();
        long returned = swap();
        if (measure) swapPacing.observeSwap(returned);
        // The picture consumed any forced request; compare later samples with this state.
        if (changed) boundary = owner.hostState().boundary();
        switch (policy.observeSwap(returned)) {
            case LOCK -> {
                // Mode switches never spend old debt.
                scheduler.reset();
                pendingTicks = 0;
                cadence = new Locked(((Probing) cadence).interval(), ((Probing) cadence).request());
                return new Step(completed, true, returned, NO_REANCHOR);
            }
            case EXACT -> {
                return rebaseExact(completed, returned);
            }
            default -> { }
        }
        if (!probing) return new Step(completed, true, wake, reanchorDelay);
        // A probe wakes on the paced return; a transition still starts the new boundary there.
        if (reanchorDelay != NO_REANCHOR) anchor = returned;
        return new Step(completed, true, returned, NO_REANCHOR);
    }

    /** One service per paced swap; a missed vblank is one slow interval, never a catch-up burst. */
    private Step runLocked(HostState host) {
        if (suspended || boundary != null && !boundary.equals(host.boundary())) policy.boundaryChanged();
        suspended = false;
        boundary = host.boundary();
        if (!owner.tick(false)) return aborted(0);
        services++;
        swapPacing.reset();
        host = owner.hostState();
        if (host.forcedPicture() || !boundary.equals(host.boundary()) || !host.focused()) {
            policy.boundaryChanged();
            restartRates(clock.getAsLong());
        }
        if (!owner.draw(exactPicture(host.captureActive()))) return aborted(1);
        pictures++;
        long returned = swap();
        boundary = owner.hostState().boundary();
        pendingTicks = 0;
        if (policy.observeSwap(returned) == DisplayCadencePolicy.Verdict.EXACT) return rebaseExact(1, returned);
        return new Step(1, true, returned, NO_REANCHOR);
    }

    private static boolean usable(HostState host) {
        return inactiveStatus(host) == null;
    }

    /** The first host reason the window cannot blend, or null when it may. */
    private static InterpolationStatus inactiveStatus(HostState host) {
        if (!host.interpolation()) return InterpolationStatus.OFF;
        if (!host.automaticPictureCap() && host.pictureCap() <= host.regionHz()) return InterpolationStatus.FPS_LIMIT;
        if (host.output().advertisedRefreshHz() > 0 && host.output().advertisedRefreshHz() <= host.regionHz())
            return InterpolationStatus.PANEL_RATE;
        if (host.pictureCap() <= host.regionHz()) return InterpolationStatus.FPS_LIMIT;
        if (host.hostPaused()) return InterpolationStatus.PAUSED;
        if (host.captureActive()) return InterpolationStatus.RECORDING;
        if (host.forcedPicture()) return InterpolationStatus.STEPPING;
        return null;
    }

    /** Diagnostic rates over windows of at least one second of host time. */
    private void sampleRates(long now) {
        long span = now - rateStart;
        if (span < SECOND) return;
        servicesPerSecond = (services - servicesAtRateStart) * (double) SECOND / span;
        picturesPerSecond = (pictures - picturesAtRateStart) * (double) SECOND / span;
        blendedPerSecond = (blendedPictures - blendedAtRateStart) * (double) SECOND / span;
        rateStart = now;
        servicesAtRateStart = services;
        picturesAtRateStart = pictures;
        blendedAtRateStart = blendedPictures;
    }

    private void restartRates(long now) {
        rateStart = now;
        servicesAtRateStart = services;
        picturesAtRateStart = pictures;
        blendedAtRateStart = blendedPictures;
        servicesPerSecond = picturesPerSecond = blendedPerSecond = 0;
    }

    private static LivePictureRequest exactPicture(boolean capture) {
        return capture ? new LivePictureRequest.CaptureLatest()
                : new LivePictureRequest.Window(PictureSelection.LATEST);
    }

    private long swap() {
        owner.swap();
        swaps++;
        unpresented = false;
        return clock.getAsLong();
    }

    /** A demoting swap starts the exact clock; the swap that refused grants no service. */
    private Step rebaseExact(int completed, long returned) {
        cadence = EXACT;
        applySwapInterval(1);
        scheduler.reset();
        pendingTicks = 0;
        anchor = returned;
        return new Step(completed, true, returned + ceilingPeriod(regionHz), NO_REANCHOR);
    }

    private void toExact() {
        if (cadence instanceof Exact) return;
        cadence = EXACT;
        policy.release();
        applySwapInterval(1);
        scheduler.reset();
        pendingTicks = 0;
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
        if (interval == swapInterval) return;
        owner.swapInterval(interval);
        swapInterval = interval;
    }

    private Step aborted(int completed) {
        toExact();
        swapPacing.reset();
        pendingPresent = null;
        restartRates(clock.getAsLong());
        scheduler.reset();
        scheduler.invalidatePhase();
        latestBarrier = true;
        boundary = null;
        suspended = true;
        pendingTicks = 0;
        return new Step(completed, false, 0, ceilingPeriod(regionHz));
    }

    Statistics statistics() {
        String name = switch (cadence) {
            case Exact exact -> "EXACT";
            case Probing probing -> "PROBING";
            case Locked locked -> "LOCKED";
        };
        return new Statistics(regionHz, pictureCap, name, policy.reason().name(), policy.measuredHz(),
                swapInterval, services, pictures, swaps, pendingTicks, discardedWallNanos,
                interpolationStatus, servicesPerSecond, picturesPerSecond, blendedPerSecond, lastAlpha,
                swapPacing == null ? new SwapPacingPolicy.Measurement(Double.NaN, Double.NaN,
                        SwapPacingPolicy.VrrEngagement.UNKNOWN) : swapPacing.measurement());
    }

    private static long ceilingPeriod(int hz) { return (SECOND + hz - 1) / hz; }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }

    /** Either an absolute wake, or a delay from the completion clock after a transition. */
    private record Step(int services, boolean presented, long wakeAtNanos, long reanchorDelay) { }

    record Iteration(int services, boolean presented, long wakeAtNanos) { }
    record Statistics(int regionHz, int pictureCap, String cadence, String reason, double measuredPresentHz,
                      int swapInterval, long services, long pictures, long swaps,
                      long pendingTicks, long discardedWallNanos, InterpolationStatus interpolation,
                      double servicesPerSecond, double picturesPerSecond, double blendedPerSecond,
                      double lastAlpha, SwapPacingPolicy.Measurement swapPacing) {
        String swapIntervalLine() {
            return Double.isNaN(swapPacing.meanNanos()) ? "swap unknown"
                    : String.format(java.util.Locale.ROOT,
                            swapPacing.meanNanos() < 100e6 && swapPacing.spreadNanos() < 100e6
                                    ? "swap %.1f+/-%.1fms" : "swap %.0f+/-%.0fms",
                            swapPacing.meanNanos() / 1e6, swapPacing.spreadNanos() / 1e6);
        }
        String vrrLine() { return "vrr " + swapPacing.vrr(); }
    }

    /** Why window pictures are or are not blended; diagnostics only, never a scheduling input. */
    enum InterpolationStatus {
        OFF, FPS_LIMIT, PANEL_RATE, UNFOCUSED, PAUSED, RECORDING, STEPPING, DISPLAY_LOCKED, BOUNDARY,
        EXACT_PICTURE, BEHIND, BLENDING
    }
}
