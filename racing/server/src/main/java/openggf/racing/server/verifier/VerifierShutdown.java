package openggf.racing.server.verifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Coordinates a JVM shutdown hook with an in-flight verifier cycle. */
final class VerifierShutdown {
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean stopping;

    boolean isStopping() {
        return stopping;
    }

    synchronized void requestStop() {
        stopping = true;
        notifyAll();
    }

    /** Idle/backoff waits wake at once on shutdown without interrupting a replay. */
    synchronized void pause(long millis) throws InterruptedException {
        if (!stopping) {
            wait(millis);
        }
    }

    void finished() {
        finished.countDown();
    }

    /** The JVM may exit after this bounded hook returns, even if replay ignores interruption. */
    void drain(Thread worker, Duration grace) {
        requestStop();
        try {
            if (!finished.await(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                worker.interrupt();
            }
        } catch (InterruptedException interrupted) {
            worker.interrupt();
            Thread.currentThread().interrupt();
        }
    }
}
