package com.openggf.tools.challenge;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** A worker is owned from spawn, including failure before host/hook enlistment. */
@RequiresRom(SonicGame.SONIC_1)
class TestChallengeProcessAcquisition {
    @Test
    void failedEndpointHookRegistrationReapsExactWorkerAndOwnedDirectory() throws Exception {
        Set<Path> before = directories();
        AtomicLong pid = new AtomicLong();
        IllegalStateException failure = new IllegalStateException("simulated shutdown during acquisition");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new ProcessGameEndpoint("s1", RomTestUtils.ensureSonic1RomAvailable().toPath(), 1,
                        failingHook(pid, failure))));
        assertStopped(pid);
        assertEquals(before, directories());
    }

    @Test
    void failedDiagnosticHookRegistrationReapsExactWorkerAndOwnedDirectory(@TempDir Path output) throws Exception {
        Set<Path> before = directories();
        AtomicLong pid = new AtomicLong();
        IllegalStateException failure = new IllegalStateException("simulated shutdown during diagnostic acquisition");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new ChallengeDiagnosticProcess(RomTestUtils.ensureSonic1RomAvailable().toPath(),
                        output.resolve("checkpoint"), failingHook(pid, failure))));
        assertStopped(pid);
        assertEquals(before, directories());
    }

    private static Consumer<Thread> failingHook(AtomicLong pid, RuntimeException failure) {
        return hook -> {
            String name = hook.getName();
            pid.set(Long.parseLong(name.substring(name.lastIndexOf('-') + 1)));
            assertTrue(ProcessHandle.of(pid.get()).orElseThrow().isAlive(), "the real process was acquired");
            throw failure;
        };
    }
    private static void assertStopped(AtomicLong pid) {
        assertTrue(pid.get() > 0);
        assertFalse(ProcessHandle.of(pid.get()).map(ProcessHandle::isAlive).orElse(false),
                "unregistered process must be reaped before failure escapes");
    }
    private static Set<Path> directories() throws Exception {
        Path parent = Path.of("target", "challenge-workers").toAbsolutePath();
        if (!Files.exists(parent)) return Set.of();
        try (var paths = Files.list(parent)) { return paths.collect(Collectors.toSet()); }
    }
}
