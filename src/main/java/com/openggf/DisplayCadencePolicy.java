package com.openggf;

import com.openggf.configuration.DisplayPacing;

/**
 * Pure swap-timestamp policy: nominate, probe, lock, demote. No GL, no clock reads.
 *
 * <p>Swap returns are measured in vblanks of the nominated display, {@code n x regionHz}.
 * A swap at interval {@code n} spans {@code n} vblanks, so a locked service lasts
 * {@code 1 / regionHz} within the lock band. Thresholds are host policy, not gameplay.
 */
final class DisplayCadencePolicy {
    private static final long SECOND = 1_000_000_000L;
    // GLFW reports whole hertz; a 59.94 Hz panel reads as 59 or 60.
    static final int REFRESH_TOLERANCE_HZ = 1;
    // Locked play runs at display speed; 0.2% admits 59.94 and 60.02 Hz panels and bounds the speed change.
    static final double LOCK_BAND = 0.002;
    // Long enough to average compositor jitter, short enough to demote within about a second.
    static final long WINDOW_NANOS = SECOND;
    // Two consecutive good windows reject a single lucky second while the window settles.
    static final int ACCEPT_WINDOWS = 2;
    // A rare missed vblank costs one slow interval; more per second means Exact catch-up serves better.
    static final int MAX_MISSES_PER_SECOND = 2;
    // A gap past the live debt bound is a host stall (load, drag, debugger), not a display measurement.
    static final long STALL_NANOS = LiveFrameRuntime.MAX_DEBT_NANOS;
    // Locked rate checks span eight windows, so return jitter cannot read as a period change.
    static final int LOCK_HORIZON_WINDOWS = 8;
    // A swap may return this far either side of its vblank; a locked span tolerates that endpoint error.
    static final long RETURN_JITTER_NANOS = 2_000_000L;

    enum Phase { EXACT, PROBING, LOCKED }

    enum Reason {
        NONE, PACING_EXACT, SPEAKER_UNREGULATED, CAP_BELOW_REGION, REFRESH_UNKNOWN,
        REFRESH_NOT_MULTIPLE, NOT_BLOCKING, OUT_OF_BAND, MISSES
    }

    /** What one swap return grants: nothing new, a lock, one locked service, or a return to Exact. */
    enum Verdict { MEASURED, LOCK, SERVICE, EXACT }

    /** Everything nomination reads; a refusal holds until one of these changes. */
    record Request(DisplayPacing pacing, boolean speakerRegulated, int pictureCap, int regionHz,
                   DisplayOutput output) { }

    private Phase phase = Phase.EXACT;
    private Reason reason = Reason.NONE;
    private Request probed, refused;
    private Reason refusal = Reason.NONE;
    private boolean boundarySinceRefusal;
    private int interval;
    private double vblankNanos;
    private boolean measuring;
    private long lastSwap, windowStart, windowStartVblanks, vblanks;
    private int windowSwaps, windowMisses, goodWindows;
    private final long[] markNanos = new long[LOCK_HORIZON_WINDOWS + 1];
    private final long[] markVblanks = new long[LOCK_HORIZON_WINDOWS + 1];
    private int marks;
    private double measuredHz;

    /** Swap interval to probe, or zero with {@link #reason()} explaining why Exact stays. */
    int nominate(Request request) {
        int n = multiple(request);
        if (n == 0) return 0;
        if (refused != null) {
            boolean retry = refusal == Reason.MISSES && boundarySinceRefusal;
            if (request.equals(refused) && !retry) {
                reason = refusal;
                return 0;
            }
            refused = null;
            refusal = Reason.NONE;
        }
        reason = Reason.NONE;
        return n;
    }

    private int multiple(Request request) {
        int hz = request.regionHz(), refresh = request.output().advertisedRefreshHz();
        if (request.pacing() == DisplayPacing.EXACT) return refuseStatically(Reason.PACING_EXACT);
        if (!request.speakerRegulated()) return refuseStatically(Reason.SPEAKER_UNREGULATED);
        if (request.pictureCap() < hz) return refuseStatically(Reason.CAP_BELOW_REGION);
        if (refresh <= 0) return refuseStatically(Reason.REFRESH_UNKNOWN);
        int n = Math.round((float) refresh / hz);
        if (n < 1 || Math.abs(refresh - n * hz) > REFRESH_TOLERANCE_HZ) {
            return refuseStatically(Reason.REFRESH_NOT_MULTIPLE);
        }
        return n;
    }

    private int refuseStatically(Reason why) {
        reason = why;
        return 0;
    }

    /** Services stay on the exact clock while every swap at {@code interval} is measured. */
    void probe(Request request, int interval) {
        phase = Phase.PROBING;
        probed = request;
        this.interval = interval;
        vblankNanos = (double) SECOND / (interval * request.regionHz());
        reason = Reason.NONE;
        restart();
    }

    /** Runtime-owned return to Exact (focus, configuration); no refusal is recorded. */
    void release() {
        phase = Phase.EXACT;
        restart();
    }

    /** A boundary change restarts the probe or locked window and permits a re-probe after misses. */
    void boundaryChanged() {
        boundarySinceRefusal = true;
        if (phase != Phase.EXACT) restart();
    }

    Verdict observeSwap(long returnedAtNanos) {
        if (!measuring) {
            measuring = true;
            lastSwap = windowStart = returnedAtNanos;
            windowStartVblanks = vblanks = 0;
            mark(returnedAtNanos);
            return granted();
        }
        long delta = returnedAtNanos - lastSwap;
        lastSwap = returnedAtNanos;
        if (delta > STALL_NANOS || delta < 0) {
            // Stalls reset the window without demoting; no catch-up is owed while locked.
            restartAt(returnedAtNanos);
            return granted();
        }
        windowSwaps++;
        if (phase == Phase.EXACT) {
            if (returnedAtNanos - windowStart >= WINDOW_NANOS) closeWindow(returnedAtNanos);
            return Verdict.MEASURED;
        }
        if (delta < (interval - 0.5) * vblankNanos) return refuse(Reason.NOT_BLOCKING);
        long spanned = Math.round(delta / vblankNanos);
        vblanks += spanned;
        if (spanned > interval && ++windowMisses > MAX_MISSES_PER_SECOND) return refuse(Reason.MISSES);
        if (returnedAtNanos - windowStart < WINDOW_NANOS) return granted();
        long windowNanos = returnedAtNanos - windowStart, windowVblanks = vblanks - windowStartVblanks;
        closeWindow(returnedAtNanos);
        if (phase == Phase.PROBING) {
            if (!inBand(windowNanos, windowVblanks, 0)) return refuse(Reason.OUT_OF_BAND);
            if (++goodWindows < ACCEPT_WINDOWS) return Verdict.MEASURED;
            phase = Phase.LOCKED;
            return Verdict.LOCK;
        }
        long horizonNanos = returnedAtNanos - markNanos[0], horizonVblanks = vblanks - markVblanks[0];
        // Demote only when the period is outside the band by more than return jitter explains.
        if (!inBand(horizonNanos, horizonVblanks, 2.0 * RETURN_JITTER_NANOS / horizonNanos)) {
            return refuse(Reason.OUT_OF_BAND);
        }
        return Verdict.SERVICE;
    }

    private Verdict granted() {
        return phase == Phase.LOCKED ? Verdict.SERVICE : Verdict.MEASURED;
    }

    private boolean inBand(long nanos, long spannedVblanks, double allowance) {
        if (spannedVblanks <= 0) return false;
        double period = (double) nanos / spannedVblanks;
        return Math.abs(period / vblankNanos - 1) <= LOCK_BAND + allowance;
    }

    private void closeWindow(long now) {
        measuredHz = windowSwaps * (double) SECOND / (now - windowStart);
        windowStart = now;
        windowStartVblanks = vblanks;
        windowSwaps = windowMisses = 0;
        mark(now);
    }

    private void mark(long now) {
        if (marks == markNanos.length) {
            System.arraycopy(markNanos, 1, markNanos, 0, marks - 1);
            System.arraycopy(markVblanks, 1, markVblanks, 0, marks - 1);
            marks--;
        }
        markNanos[marks] = now;
        markVblanks[marks] = vblanks;
        marks++;
    }

    private Verdict refuse(Reason why) {
        refused = probed;
        refusal = reason = why;
        boundarySinceRefusal = false;
        phase = Phase.EXACT;
        restart();
        return Verdict.EXACT;
    }

    private void restart() {
        measuring = false;
        marks = 0;
        windowSwaps = windowMisses = goodWindows = 0;
    }

    private void restartAt(long now) {
        restart();
        measuring = true;
        lastSwap = windowStart = now;
        windowStartVblanks = vblanks = 0;
        mark(now);
    }

    Phase phase() { return phase; }
    Reason reason() { return reason; }
    int interval() { return phase == Phase.EXACT ? 1 : interval; }
    double measuredHz() { return measuredHz; }
}
