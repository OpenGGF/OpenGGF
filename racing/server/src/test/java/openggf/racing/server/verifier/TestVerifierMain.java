package openggf.racing.server.verifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void tokenFileIsReadAndLiteralTokensRemainSupported(@TempDir Path dir) throws Exception {
        Path token = dir.resolve("registration.secret");
        Files.writeString(token, "worker-secret\n");
        VerifierMain.Arguments file = VerifierMain.Arguments.parse(new String[] {
                "--master", "https://race.example/", "--registration-token-file", token.toString(),
                "--rom", "game.gen", "--shutdown-grace-seconds", "60"});
        assertEquals("worker-secret", file.registrationToken());
        assertEquals("https://race.example", file.master());
        assertEquals(60, file.shutdownGraceSeconds());
        assertEquals("literal", VerifierMain.Arguments.parse(new String[] {
                "--master", "https://race.example", "--registration-token", "literal",
                "--rom", "game.gen"}).registrationToken());
    }

    @Test
    void ambiguousOrMissingSecretsAndUnboundedShutdownAreRejected(@TempDir Path dir)
            throws Exception {
        String[] base = {"--master", "https://race.example", "--rom", "game.gen"};
        assertThrows(IllegalArgumentException.class, () -> VerifierMain.Arguments.parse(base));
        assertThrows(IllegalArgumentException.class, () -> VerifierMain.Arguments.parse(
                append(base, "--registration-token", "literal", "--registration-token-file", "absent")));
        assertThrows(IOException.class, () -> VerifierMain.Arguments.parse(
                append(base, "--registration-token-file", dir.resolve("absent").toString())));
        for (String grace : List.of("0", "301")) {
            assertThrows(IllegalArgumentException.class, () -> VerifierMain.Arguments.parse(
                    append(base, "--registration-token", "literal", "--shutdown-grace-seconds", grace)));
        }
    }

    @Test
    void stoppingDuringRegistrationPreventsLeasingAJob() {
        boolean[] stopping = {false};
        VerifierLoop.Cycle cycle = VerifierMain.registeringCycle(() -> stopping[0] = true,
                () -> { fail("shutdown must not acquire a new lease"); return false; },
                () -> stopping[0]);
        org.junit.jupiter.api.Assertions.assertFalse(cycle.pollOnce());
    }

    private static String[] append(String[] base, String... options) {
        List<String> args = new ArrayList<>(List.of(base));
        args.addAll(List.of(options));
        return args.toArray(String[]::new);
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
