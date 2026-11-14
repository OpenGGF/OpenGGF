package com.openggf;

import com.openggf.game.crash.CrashScreen;
import com.openggf.game.crash.CrashScreenAccess;
import com.openggf.game.crash.FaultKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Engine faults end the session on the crash page; declined faults and failed teardowns still rethrow. */
class TestEngineCrashBoundary {
    @TempDir Path reports;

    private final List<String> events = new ArrayList<>();

    private EngineCrashBoundary boundary(Map<String, Supplier<String>> facts) {
        return new EngineCrashBoundary(() -> facts, text -> { events.add("copy"); return true; },
                command -> events.add("browser"), reports);
    }

    @Test
    void anEngineFaultIsReportedBeforeTeardownAndOpensThePageAfterIt() throws Exception {
        var facts = new LinkedHashMap<String, Supplier<String>>();
        facts.put("Mode", () -> { events.add("facts read"); return "LEVEL"; });
        var crash = boundary(facts);
        var failure = new IllegalStateException("no keyframe at frame 120");

        boolean completed = Engine.runFrameWithFaultBoundary(() -> { throw failure; },
                () -> events.add("discard"), () -> events.add("title"), crash::capture);

        assertFalse(completed);
        assertEquals(List.of("facts read", "discard", "title"), events);
        assertTrue(crash.isOpen());
        CrashScreen screen = crash.screen();
        assertSame(failure, screen.report().failure());
        assertEquals("LEVEL", screen.report().sessionFacts().getFirst().value());
        Path saved = screen.file().orElseThrow();
        assertTrue(Files.readString(saved).contains("no keyframe at frame 120"));
    }

    @Test
    void aFactThatCannotBeReadIsReportedInsteadOfLosingTheReport() {
        var facts = new LinkedHashMap<String, Supplier<String>>();
        facts.put("Zone index", () -> { throw new NullPointerException("no level"); });
        var crash = boundary(facts);
        Engine.runFrameWithFaultBoundary(() -> { throw new IllegalStateException("x"); }, () -> { }, () -> { },
                crash::capture);
        assertEquals("unreadable (NullPointerException)", crash.screen().report().sessionFacts().getFirst().value());
    }

    @Test
    void aSecondFaultWhileThePageIsOpenEndsTheProcess() {
        var crash = boundary(Map.of());
        Engine.runFrameWithFaultBoundary(() -> { throw new IllegalStateException("first"); }, () -> { }, () -> { },
                crash::capture);
        var second = new IllegalStateException("second");
        assertSame(second, assertThrows(IllegalStateException.class, () -> Engine.runFrameWithFaultBoundary(
                () -> { throw second; }, () -> { }, () -> { }, crash::capture)));
    }

    @Test
    void theRethrowPropertyKeepsTheOldBehaviour() {
        var crash = boundary(Map.of());
        String previous = System.getProperty(EngineCrashBoundary.RETHROW_PROPERTY);
        System.setProperty(EngineCrashBoundary.RETHROW_PROPERTY, "true");
        try {
            var failure = new IllegalStateException("rethrown");
            assertSame(failure, assertThrows(IllegalStateException.class, () -> Engine.runFrameWithFaultBoundary(
                    () -> { throw failure; }, () -> events.add("discard"), () -> events.add("title"), crash::capture)));
            assertEquals(List.of(), events);
            assertFalse(crash.isOpen());
        } finally {
            if (previous == null) System.clearProperty(EngineCrashBoundary.RETHROW_PROPERTY);
            else System.setProperty(EngineCrashBoundary.RETHROW_PROPERTY, previous);
        }
    }

    @Test
    void aFailedTeardownRethrowsTheFaultAndOpensNoPage() {
        var crash = boundary(Map.of());
        var failure = new IllegalStateException("fault");
        var teardown = new IllegalArgumentException("teardown");
        var thrown = assertThrows(IllegalStateException.class, () -> Engine.runFrameWithFaultBoundary(
                () -> { throw failure; }, () -> { }, () -> { throw teardown; }, crash::capture));
        assertSame(failure, thrown);
        assertSame(teardown, thrown.getSuppressed()[0]);
        assertFalse(crash.isOpen());
    }

    @Test
    void aFaultArrivingWithRollbackFailuresStillReachesThePage() {
        var crash = boundary(Map.of());
        var failure = new IllegalStateException("seek failed");
        failure.addSuppressed(new IllegalStateException("rollback failed"));
        assertFalse(Engine.runFrameWithFaultBoundary(() -> { throw failure; }, () -> { }, () -> { }, crash::capture));
        assertTrue(crash.isOpen());
    }

    @Test
    void aReporterFailureRethrowsTheOriginalFault() {
        var failure = new IllegalStateException("fault");
        var reporter = new UnsupportedOperationException("reporter");
        var thrown = assertThrows(IllegalStateException.class, () -> Engine.runFrameWithFaultBoundary(
                () -> { throw failure; }, () -> { }, () -> { }, ignored -> { throw reporter; }));
        assertSame(failure, thrown);
        assertSame(reporter, thrown.getSuppressed()[0]);
    }

    @Test
    void theModOnlyBoundaryStillRethrowsEngineFaults() {
        var failure = new IllegalStateException("engine");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Engine.runFrameWithModAbort(() -> { throw failure; }, () -> { }, () -> { })));
    }

    @Test
    void returnToGamesClosesThePage() {
        var crash = boundary(Map.of());
        Engine.runFrameWithFaultBoundary(() -> { throw new IllegalStateException("x"); }, () -> { }, () -> { },
                crash::capture);
        assertEquals(FaultKind.ENGINE, crash.screen().report().kind());
        CrashScreenAccess.returnToGames(crash.screen());
        assertFalse(crash.isOpen());
    }
}
