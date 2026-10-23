package openggf.racing.server.verifier;

import java.util.function.BooleanSupplier;

/**
 * Long-running verifier poll loop. A failed cycle (an unreachable or overloaded master,
 * a transient HTTP status, a replay fault) is logged and retried after a bounded
 * exponential back-off instead of ending the process. A stop request drains the current
 * cycle; interruption exits without another cycle.
 *
 * <p>A cycle reports a survivable failure as a {@link RuntimeException}.
 * {@link VerifierWorker#pollOnce()} converts the job-scoped Errors a poisoned replay can
 * raise ({@link StackOverflowError}, {@link LinkageError}) into one. Every other Error,
 * such as {@link OutOfMemoryError}, deliberately propagates and ends the process: the JVM
 * state can no longer be trusted to sign verdicts.
 */
final class VerifierLoop {
    static final long IDLE_POLL_MILLIS = 2_000;
    static final long INITIAL_BACKOFF_MILLIS = 2_000;
    static final long MAX_BACKOFF_MILLIS = 30_000;
    private static final System.Logger LOG = System.getLogger(VerifierLoop.class.getName());

    /** One verification cycle: {@code true} when a job was processed. */
    @FunctionalInterface
    interface Cycle {
        boolean pollOnce();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Cycle cycle;
    private final Sleeper sleeper;
    private final BooleanSupplier stopping;

    VerifierLoop(Cycle cycle, Sleeper sleeper) {
        this(cycle, sleeper, () -> false);
    }

    VerifierLoop(Cycle cycle, Sleeper sleeper, BooleanSupplier stopping) {
        this.cycle = cycle;
        this.sleeper = sleeper;
        this.stopping = stopping;
    }

    /** Runs cycles until the calling thread is interrupted; the interrupt status stays set. */
    void run() {
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        try {
            while (!stopping.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                long pauseMillis;
                try {
                    pauseMillis = cycle.pollOnce() ? 0 : IDLE_POLL_MILLIS;
                    backoffMillis = INITIAL_BACKOFF_MILLIS;
                } catch (RuntimeException failure) {
                    if (stopping.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                        // An interrupted HTTP request surfaces as a failed cycle.
                        return;
                    }
                    LOG.log(System.Logger.Level.WARNING, "verification cycle failed; retrying in "
                            + backoffMillis + " ms", failure);
                    pauseMillis = backoffMillis;
                    backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
                }
                if (pauseMillis > 0 && !stopping.getAsBoolean()) {
                    sleeper.sleep(pauseMillis);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
