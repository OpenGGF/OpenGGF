package com.openggf;

/** Pure rolling swap-return evidence and work budget. No clock, GL or panel-capability claims. */
final class SwapPacingPolicy {
    private static final long SECOND = 1_000_000_000L;

    enum VrrEngagement { ACTIVE, NOT_DETECTED, UNKNOWN }

    record Measurement(double meanNanos, double spreadNanos, VrrEngagement vrr) { }

    private final int serviceHz, refreshHz;
    // Two seconds retain several quantization cycles; one full second is required to decide.
    private final long[] intervals, work;
    private int count, cursor, workCount, workCursor;
    private long lastSwap;
    private boolean started;

    SwapPacingPolicy(int serviceHz, int refreshHz) {
        this.serviceHz = serviceHz;
        this.refreshHz = refreshHz;
        intervals = new long[2 * serviceHz];
        work = new long[serviceHz];
    }

    void reset() {
        count = cursor = workCount = workCursor = 0;
        started = false;
    }

    void observeSwap(long returnedAtNanos) {
        if (started) {
            long delta = returnedAtNanos - lastSwap;
            if (delta <= 0 || delta > LiveFrameRuntime.MAX_DEBT_NANOS) reset();
            else {
                intervals[cursor] = delta;
                cursor = (cursor + 1) % intervals.length;
                count = Math.min(count + 1, intervals.length);
            }
        }
        started = true;
        lastSwap = returnedAtNanos;
    }

    void observeWork(long nanos) {
        work[workCursor] = Math.max(0, nanos);
        workCursor = (workCursor + 1) % work.length;
        workCount = Math.min(workCount + 1, work.length);
    }

    /** The recent empirical 100th percentile leaves room for every observed tick+draw cost. */
    long workMarginNanos() {
        long largest = 0;
        for (int i = 0; i < workCount; i++) largest = Math.max(largest, work[i]);
        return largest;
    }

    long presentDeadline(long tickDeadline, long nextTickDeadline, long now) {
        if (measurement().vrr() != VrrEngagement.ACTIVE) return now;
        long deadline = tickDeadline + workMarginNanos();
        // Never hold a service that is already late or spend the next tick's input/audio budget.
        return now < deadline && deadline < nextTickDeadline ? deadline : now;
    }

    Measurement measurement() {
        if (count == 0) return new Measurement(Double.NaN, Double.NaN, VrrEngagement.UNKNOWN);
        double sum = 0;
        for (int i = 0; i < count; i++) sum += intervals[i];
        double mean = sum / count, variance = 0, latticeError = 0;
        double vblank = refreshHz > 0 ? (double) SECOND / refreshHz : Double.NaN;
        for (int i = 0; i < count; i++) {
            double residual = intervals[i] - mean;
            variance += residual * residual;
            double fixedResidual = intervals[i] - Math.rint(intervals[i] / vblank) * vblank;
            latticeError += fixedResidual * fixedResidual;
        }
        variance /= count;
        latticeError /= count;
        var verdict = VrrEngagement.UNKNOWN;
        if (count >= serviceHz && refreshHz > serviceHz && refreshHz % serviceHz != 0) {
            double period = (double) SECOND / serviceHz;
            double fraction = (double) (refreshHz % serviceHz) / serviceHz;
            // Fixed refresh has intervals q*T and (q+1)*T with weights 1-f and f.
            // Its mean is P and variance Q=f*(1-f)*T*T. VRR predicts P with zero
            // variance. Least squared distance to these variance models meets at Q/2.
            // Require the mean and fixed-lattice residual to fit within the same
            // half-separation budget; stalls/ambiguous noisy returns stay UNKNOWN.
            double halfSeparation = fraction * (1 - fraction) * vblank * vblank / 2;
            double meanError = mean - period;
            if (meanError * meanError < halfSeparation) {
                if (variance < halfSeparation) verdict = VrrEngagement.ACTIVE;
                // Variable work can cross extra vblanks, increasing variance above Q.
                // Those returns still fit the lattice; the mean guard rejects sustained overload.
                else if (latticeError < halfSeparation) {
                    verdict = VrrEngagement.NOT_DETECTED;
                }
            }
        }
        return new Measurement(mean, Math.sqrt(variance), verdict);
    }
}
