package com.openggf;

import com.openggf.configuration.DisplayPacing;

/**
 * Pure swap-timestamp policy: nominate, probe, lock, demote. No GL, no clock reads.
 *
 * <p>Swap returns are measured in vblanks of the nominated display, {@code n x regionHz}.
 * A swap at interval {@code n} spans {@code n} vblanks, so a locked service lasts
 * {@code 1 / regionHz} within the lock band. Each window fits the period by least squares
 * over every swap return, and decisions use the mean of recent window fits, so return jitter
 * averages out instead of landing on two endpoints.
 * Thresholds are host policy, not gameplay.
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
    // Locked play averages the last eight window fits, so return jitter cannot read as a period change.
    static final int LOCK_HORIZON_WINDOWS = 8;
    // A gap past the live debt bound is a host stall (load, drag, debugger), not a display measurement.
    static final long STALL_NANOS = LiveFrameRuntime.MAX_DEBT_NANOS;

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
    private int fitPoints;
    private double fitX, fitY, fitXX, fitXY;
    private final double[] windowPeriods = new double[LOCK_HORIZON_WINDOWS];
    private int periods;
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
        int hz = request.regionHz(), refresh = request.output().windowRefreshHz();
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
            fit(returnedAtNanos);
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
        fit(returnedAtNanos);
        if (returnedAtNanos - windowStart < WINDOW_NANOS) return granted();
        recordPeriod(fittedVblankNanos());
        closeWindow(returnedAtNanos);
        if (!inBand(meanPeriod())) return refuse(Reason.OUT_OF_BAND);
        if (phase == Phase.LOCKED) return Verdict.SERVICE;
        if (++goodWindows < ACCEPT_WINDOWS) return Verdict.MEASURED;
        phase = Phase.LOCKED;
        return Verdict.LOCK;
    }

    private Verdict granted() {
        return phase == Phase.LOCKED ? Verdict.SERVICE : Verdict.MEASURED;
    }

    private boolean inBand(double fittedVblankNanos) {
        return Math.abs(fittedVblankNanos / vblankNanos - 1) <= LOCK_BAND;
    }

    private void recordPeriod(double period) {
        if (periods == windowPeriods.length) {
            System.arraycopy(windowPeriods, 1, windowPeriods, 0, periods - 1);
            periods--;
        }
        windowPeriods[periods++] = period;
    }

    private double meanPeriod() {
        double sum = 0;
        for (int i = 0; i < periods; i++) sum += windowPeriods[i];
        return sum / periods;
    }

    /** Adds one swap return as (vblanks since window start, nanos since window start). */
    private void fit(long now) {
        double x = vblanks - windowStartVblanks, y = now - windowStart;
        fitPoints++;
        fitX += x;
        fitY += y;
        fitXX += x * x;
        fitXY += x * y;
    }

    /** Least-squares slope of return time against vblank count; NaN without two distinct counts. */
    private double fittedVblankNanos() {
        double spread = fitPoints * fitXX - fitX * fitX;
        return spread <= 0 ? Double.NaN : (fitPoints * fitXY - fitX * fitY) / spread;
    }

    private void closeWindow(long now) {
        measuredHz = windowSwaps * (double) SECOND / (now - windowStart);
        windowStart = now;
        windowStartVblanks = vblanks;
        windowSwaps = windowMisses = 0;
        clearFit();
        fit(now);
    }

    private void clearFit() {
        fitPoints = 0;
        fitX = fitY = fitXX = fitXY = 0;
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
        periods = 0;
        clearFit();
        windowSwaps = windowMisses = goodWindows = 0;
    }

    private void restartAt(long now) {
        restart();
        measuring = true;
        lastSwap = windowStart = now;
        windowStartVblanks = vblanks = 0;
        fit(now);
    }

    Phase phase() { return phase; }
    Reason reason() { return reason; }
    int interval() { return phase == Phase.EXACT ? 1 : interval; }
    double measuredHz() { return measuredHz; }
}
