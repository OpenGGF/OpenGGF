package openggf.racing.server.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class TestVerifierLoop {
    private static final Logger LOOP_LOG = Logger.getLogger(VerifierLoop.class.getName());

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean parentHandlers;

    @BeforeEach
    void captureLog() {
        parentHandlers = LOOP_LOG.getUseParentHandlers();
        // Expected cycle failures would otherwise print stack traces into the test log.
        LOOP_LOG.setUseParentHandlers(false);
        LOOP_LOG.addHandler(capture);
    }

    @AfterEach
    void restore() {
        LOOP_LOG.removeHandler(capture);
        LOOP_LOG.setUseParentHandlers(parentHandlers);
        // The loop exits by interruption; never leak that status into the next test.
        Thread.interrupted();
    }

    @Test
    void failedCyclesAreLoggedAndRetriedWithBoundedBackoffThatSuccessResets() {
        ScriptedCycle cycle = new ScriptedCycle(
                fail(), fail(), fail(), fail(), fail(), fail(),
                true, false, fail());
        List<Long> sleeps = new ArrayList<>();
        new VerifierLoop(cycle, sleeps::add).run();

        assertEquals(List.of(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L,
                        // A processed job polls again at once; an empty queue idles.
                        VerifierLoop.IDLE_POLL_MILLIS,
                        // The failure after a successful cycle starts from the first step.
                        VerifierLoop.INITIAL_BACKOFF_MILLIS),
                sleeps);
        assertEquals(10, cycle.calls, "the loop must keep polling after failed cycles");
        assertTrue(Thread.currentThread().isInterrupted());
        List<LogRecord> warnings = records.stream()
                .filter(record -> record.getLevel() == Level.WARNING).toList();
        assertEquals(7, warnings.size());
        warnings.forEach(warning -> assertNotNull(warning.getThrown(),
                "each failed cycle is logged with its cause"));
    }

    @Test
    void interruptedSleepEndsTheLoopWithInterruptStatusSet() {
        ScriptedCycle cycle = new ScriptedCycle(false, false);
        new VerifierLoop(cycle, millis -> {
            throw new InterruptedException("shutdown");
        }).run();

        assertEquals(1, cycle.calls);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void cycleFailingBecauseOfInterruptionExitsWithoutBackoff() {
        List<Long> sleeps = new ArrayList<>();
        int[] calls = {0};
        new VerifierLoop(() -> {
            calls[0]++;
            // HttpMasterApi restores the interrupt status and rethrows unchecked.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("verifier HTTP request failed");
        }, sleeps::add).run();

        assertEquals(1, calls[0]);
        assertTrue(sleeps.isEmpty());
        assertTrue(records.isEmpty(), "shutdown is not a failed cycle");
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void jvmFaultOutsideTheCycleContractEndsTheLoop() {
        List<Long> sleeps = new ArrayList<>();
        OutOfMemoryError exhausted = new OutOfMemoryError("Java heap space");
        ScriptedCycle cycle = new ScriptedCycle(exhausted, true);

        assertSame(exhausted, assertThrows(OutOfMemoryError.class,
                () -> new VerifierLoop(cycle, sleeps::add).run()));
        assertEquals(1, cycle.calls);
        assertTrue(sleeps.isEmpty());
        assertTrue(records.isEmpty(), "a fatal fault is not retried as a failed cycle");
    }

    @Test
    void alreadyInterruptedThreadRunsNoCycle() {
        ScriptedCycle cycle = new ScriptedCycle(true);
        Thread.currentThread().interrupt();
        new VerifierLoop(cycle, millis -> { }).run();

        assertEquals(0, cycle.calls);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    private static RuntimeException fail() {
        return new IllegalStateException("unexpected verifier API status 503, expected 200");
    }

    /** Replays scripted outcomes, then interrupts the loop's thread. */
    private static final class ScriptedCycle implements VerifierLoop.Cycle {
        private final Deque<Object> outcomes = new ArrayDeque<>();
        private int calls;

        private ScriptedCycle(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public boolean pollOnce() {
            calls++;
            Object next = outcomes.poll();
            if (next == null) {
                Thread.currentThread().interrupt();
                return true;
            }
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            if (next instanceof Error fault) {
                throw fault;
            }
            return (Boolean) next;
        }
    }
}
