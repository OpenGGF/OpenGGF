package openggf.racing.server.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class TestVerifierShutdown {
    @Test
    void activeCycleFinishesBeforeDrainReturnsAndNoSecondJobIsLeased() throws Exception {
        VerifierShutdown shutdown = new VerifierShutdown();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        AtomicInteger jobs = new AtomicInteger();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                new VerifierLoop(() -> {
                    jobs.incrementAndGet();
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return true;
                }, shutdown::pause, shutdown::isStopping).run();
            } finally {
                shutdown.finished();
            }
        });
        Thread hook = null;
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            shutdown.requestStop();
            hook = Thread.ofPlatform().start(() -> {
                shutdown.drain(worker, Duration.ofSeconds(5));
                drained.countDown();
            });
            assertFalse(drained.await(100, TimeUnit.MILLISECONDS),
                    "the shutdown hook must wait for the active cycle");
            release.countDown();
            worker.join(2_000);
            hook.join(2_000);
            assertFalse(worker.isAlive());
            assertFalse(hook.isAlive());
            assertEquals(1, jobs.get());
            assertFalse(interrupted.get(), "successful drain must not interrupt replay");
        } finally {
            shutdown.requestStop();
            release.countDown();
            worker.interrupt();
            worker.join(2_000);
            if (hook != null) hook.join(2_000);
        }
    }

    @Test
    void expiredGraceInterruptsTheActiveCycle() throws Exception {
        VerifierShutdown shutdown = new VerifierShutdown();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                entered.countDown();
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
            } finally {
                shutdown.finished();
            }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            shutdown.drain(worker, Duration.ofMillis(20));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally {
            worker.interrupt();
            worker.join(2_000);
        }
    }

    @Test
    void stopWakesAnIdleWorkerWithoutInterruptingIt() throws Exception {
        VerifierShutdown shutdown = new VerifierShutdown();
        CountDownLatch pausing = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofPlatform().start(() -> {
            pausing.countDown();
            try {
                shutdown.pause(60_000);
            } catch (InterruptedException failure) {
                interrupted.set(true);
            } finally {
                shutdown.finished();
            }
        });
        try {
            assertTrue(pausing.await(2, TimeUnit.SECONDS));
            shutdown.drain(worker, Duration.ofSeconds(2));
            worker.join(2_000);
            assertFalse(worker.isAlive());
            assertFalse(interrupted.get());
        } finally {
            worker.interrupt();
            worker.join(2_000);
        }
    }
}
