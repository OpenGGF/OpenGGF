package openggf.racing.server.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Cycle dispatch of the verifier CLI: {@code --once} fails loudly, service mode survives. */
@Timeout(10)
class TestVerifierMain {
    private static final Logger LOOP_LOG = Logger.getLogger(VerifierLoop.class.getName());

    private boolean parentHandlers;

    @BeforeEach
    void quietLoop() {
        parentHandlers = LOOP_LOG.getUseParentHandlers();
        // The expected failed cycle would otherwise print its stack trace into the test log.
        LOOP_LOG.setUseParentHandlers(false);
    }

    @AfterEach
    void restore() {
        LOOP_LOG.setUseParentHandlers(parentHandlers);
        Thread.interrupted();
    }

    @Test
    void onceRunsOneCycleAndLetsItsFailureEndTheRun() {
        IllegalStateException failure = new IllegalStateException(
                "unexpected verifier API status 503, expected 200");
        int[] calls = {0};

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> VerifierMain.serve(true, () -> {
                    calls[0]++;
                    throw failure;
                }, millis -> fail("--once never waits for another cycle"))));
        assertEquals(1, calls[0]);
    }

    @Test
    void onceWithAnEmptyQueueRunsExactlyOneCycle() {
        int[] calls = {0};
        VerifierMain.serve(true, () -> {
            calls[0]++;
            return false;
        }, millis -> fail("--once never waits for another cycle"));

        assertEquals(1, calls[0]);
    }

    @Test
    void serviceModeSurvivesAFailedCycleUntilInterrupted() {
        int[] calls = {0};
        List<Long> sleeps = new ArrayList<>();
        VerifierMain.serve(false, () -> {
            if (calls[0]++ == 0) {
                throw new IllegalStateException("verifier HTTP request failed");
            }
            return false;
        }, millis -> {
            sleeps.add(millis);
            if (sleeps.size() == 2) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals(2, calls[0]);
        assertEquals(List.of(VerifierLoop.INITIAL_BACKOFF_MILLIS,
                VerifierLoop.IDLE_POLL_MILLIS), sleeps);
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
