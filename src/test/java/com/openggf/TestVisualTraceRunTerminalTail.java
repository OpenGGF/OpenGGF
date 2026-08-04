package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.replay.runs.TraceRunFrameDriver;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapJournal;
import com.openggf.trace.replay.runs.TraceRunExternalDiagnostics;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceHudModel;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestVisualTraceRunTerminalTail {

    @AfterEach
    void clearActiveSession() {
        setStaticField("activeSession", null);
        Engine.clearGlobalInstance();
        GameServices.playbackDebug().endSession();
        SessionManager.clear();
    }

    @Test
    void beginningTailRetainsTheContinuousPlaybackTimeline() {
        Bk2Movie movie = new Bk2Movie(
                Path.of("tail.bk2"), "", Map.of(),
                List.of(frame(0, 0), frame(1, 1), frame(2, 2)), 1);
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, movie, List.of(), null);
        GameServices.playbackDebug().startSession(movie, 1);

        invokeBeginTail(session,
                new TraceRunReplayWalker.TerminalMovieTailPlan(
                        1, 2, GameMode.TITLE_SCREEN));

        assertTrue(GameServices.playbackDebug().isSessionPlaying());
        assertEquals(1, GameServices.playbackDebug().getCursorFrame());
    }

    @Test
    void emptyTailAcceptsTheContinuousTimelineStoppedOnItsLastRow() {
        Bk2Movie movie = new Bk2Movie(
                Path.of("tail.bk2"), "", Map.of(),
                List.of(frame(0, 0), frame(1, 1), frame(2, 2)), 1);
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, movie, List.of(), null);
        TraceRunPlaybackCoordinator coordinator =
                mock(TraceRunPlaybackCoordinator.class);
        when(coordinator.finishTerminalTail(GameMode.TITLE_SCREEN))
                .thenReturn(List.of());
        setField(session, "runCoordinator", coordinator);
        TraceRunDynamicArtGapJournal gapJournal =
                mock(TraceRunDynamicArtGapJournal.class);
        FrameComparison terminalComparison =
                new FrameComparison(3, Map.of());
        when(gapJournal.terminalTailClosed(3))
                .thenReturn(terminalComparison);
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        setField(session, "runDynamicArtGapJournal", gapJournal);
        setField(session, "comparator", comparator);
        GameLoop loop = mock(GameLoop.class);
        when(loop.getCurrentGameMode()).thenReturn(GameMode.TITLE_SCREEN);
        installCurrentLoop(loop);
        GameServices.playbackDebug().startSession(movie, 2);
        GameServices.playbackDebug().onLevelFrameAdvanced();

        invokeBeginTail(session,
                new TraceRunReplayWalker.TerminalMovieTailPlan(
                        3, 0, GameMode.TITLE_SCREEN));

        assertFalse(GameServices.playbackDebug().isSessionPlaying());
        assertEquals(2, GameServices.playbackDebug().getCursorFrame());
        verify(gapJournal).terminalTailClosed(3);
        verify(comparator).ingestExternalComparison(terminalComparison);
        verify(coordinator).finishTerminalTail(GameMode.TITLE_SCREEN);
    }

    @Test
    void sharedDriverAppliesAdvancesAndClearsEachTailRowOnce() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayModeContext gameplay = SessionManager.getCurrentGameplayMode();
        Bk2Movie movie = new Bk2Movie(
                Path.of("tail.bk2"), "", Map.of(),
                List.of(frame(0, 0), frame(1, 1), frame(2, 2)), 1);
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, movie, List.of(), null);
        TraceRunPlaybackCoordinator coordinator =
                mock(TraceRunPlaybackCoordinator.class);
        when(coordinator.phase()).thenReturn(
                TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL);
        when(coordinator.currentSegmentIndex()).thenReturn(-1);
        when(coordinator.finishTerminalTail(GameMode.TITLE_SCREEN))
                .thenReturn(List.of());
        setField(session, "runCoordinator", coordinator);
        TraceRunFrameDriver driver = new TraceRunFrameDriver();
        setField(session, "runFrameDriver", driver);
        gameplay.installTraceRunFrameDriver(driver);
        GameServices.playbackDebug().startSession(movie, 1);
        invokeBeginTail(session,
                new TraceRunReplayWalker.TerminalMovieTailPlan(
                        1, 2, GameMode.TITLE_SCREEN));
        setStaticField("activeSession", session);
        InputHandler input = new InputHandler();
        GameLoop loop = mock(GameLoop.class);
        when(loop.getInputHandler()).thenReturn(input);
        when(loop.getCurrentGameMode()).thenReturn(GameMode.TITLE_SCREEN);
        installCurrentLoop(loop);

        TraceSessionLauncher.runProductionIterationIfActive(() -> {
            assertTrue(input.hasLogicalOverride());
            assertEquals(1, input.logical().player1().heldMask());
        }, GameServices.playbackDebug()::onLevelFrameAdvanced);

        session.runAdvanceTickIfActive(GameMode.TITLE_SCREEN, 0);
        assertFalse(input.hasLogicalOverride());
        assertEquals(2, GameServices.playbackDebug().getCursorFrame());

        TraceSessionLauncher.runProductionIterationIfActive(() -> {
            assertTrue(input.hasLogicalOverride());
            assertEquals(2, input.logical().player1().heldMask());
        }, GameServices.playbackDebug()::onLevelFrameAdvanced);
        session.runAdvanceTickIfActive(GameMode.TITLE_SCREEN, 0);
        assertFalse(input.hasLogicalOverride());
        assertEquals(2, GameServices.playbackDebug().getCursorFrame(),
                "the final timeline cursor remains on its last valid row");
        verify(coordinator).finishTerminalTail(GameMode.TITLE_SCREEN);
    }

    @Test
    void runHudShowsThePhysicalClockAndCompletesOnlyWithTheWholeRun() {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null, List.of(), null);
        TraceRunPlaybackCoordinator coordinator =
                mock(TraceRunPlaybackCoordinator.class);
        when(coordinator.phase()).thenReturn(
                TraceRunPlaybackCoordinator.Phase.TRANSITION_GAP,
                TraceRunPlaybackCoordinator.Phase.COMPLETE);
        setField(session, "runCoordinator", coordinator);
        setField(session, "runLastPhysicalInput", frame(7, 2));
        TraceRunExternalDiagnostics external =
                new TraceRunExternalDiagnostics(null);
        external.accept(new FrameComparison(7, Map.of(
                "run_gap.edge_count", new FieldComparison(
                        "run_gap.edge_count", "0", "1",
                        Severity.ERROR, 1))));
        setField(session, "runExternalDiagnostics", external);
        TraceHudModel segment = mock(TraceHudModel.class);
        when(segment.isComplete()).thenReturn(true);

        TraceHudModel runHud = invokeRunHudModel(session, segment);

        assertEquals(2, runHud.recentInputMask());
        assertEquals(1, runHud.errorCount());
        assertEquals("run_gap.edge_count",
                runHud.recentMismatches().getLast().field());
        assertTrue(runHud.hasRecordingDesync());
        assertFalse(runHud.isComplete(),
                "a completed segment must not freeze the whole-run HUD");
        assertTrue(runHud.isComplete());
    }

    @Test
    void runHudMismatchRingPreservesComparisonOwnerSequence() {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null, List.of(), null);
        TraceRunExternalDiagnostics diagnostics =
                new TraceRunExternalDiagnostics(null);
        diagnostics.acceptDisplayed(errorComparison(1, "source"));
        diagnostics.accept(errorComparison(900, "gap"));
        for (int i = 0; i < 4; i++) {
            diagnostics.acceptDisplayed(errorComparison(i, "destination-" + i));
        }
        setField(session, "runExternalDiagnostics", diagnostics);

        TraceHudModel runHud = invokeRunHudModel(
                session, mock(TraceHudModel.class));

        assertEquals(List.of(
                        "destination-3", "destination-2", "destination-1",
                        "destination-0", "gap"),
                runHud.recentMismatches().stream()
                        .map(com.openggf.trace.live.MismatchEntry::field)
                        .toList());
    }

    @Test
    void completeWithoutExpectedEndModeStillClosesTheTerminalGap() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        Bk2Movie movie = new Bk2Movie(
                Path.of("tail.bk2"), "", Map.of(),
                List.of(frame(0, 0), frame(1, 1), frame(2, 2)), 1);
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, movie, List.of(), null);
        TraceRunDynamicArtGapJournal gapJournal =
                mock(TraceRunDynamicArtGapJournal.class);
        FrameComparison terminalComparison =
                new FrameComparison(3, Map.of());
        when(gapJournal.terminalTailClosed(3))
                .thenReturn(terminalComparison);
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        setField(session, "runDynamicArtGapJournal", gapJournal);
        setField(session, "comparator", comparator);

        invokeCoordinatorActions(session, List.of(
                new TraceRunPlaybackCoordinator.CompleteRun(0)));

        verify(gapJournal).terminalTailClosed(3);
        verify(comparator).ingestExternalComparison(terminalComparison);
    }

    private static Bk2FrameInput frame(int index, int mask) {
        return new Bk2FrameInput(index, mask, 0, false, "");
    }

    private static FrameComparison errorComparison(int frame, String field) {
        return new FrameComparison(frame, Map.of(field, new FieldComparison(
                field, "expected", "actual", Severity.ERROR, 1)));
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setStaticField(String name, Object value) {
        try {
            Field field = TraceSessionLauncher.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void invokeBeginTail(
            TraceSessionLauncher session,
            TraceRunReplayWalker.TerminalMovieTailPlan plan) {
        try {
            var method = TraceSessionLauncher.class.getDeclaredMethod(
                    "beginRunTerminalTail",
                    TraceRunReplayWalker.TerminalMovieTailPlan.class);
            method.setAccessible(true);
            method.invoke(session, plan);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static TraceHudModel invokeRunHudModel(
            TraceSessionLauncher session, TraceHudModel delegate) {
        try {
            var method = TraceSessionLauncher.class.getDeclaredMethod(
                    "createRunHudModel", TraceHudModel.class);
            method.setAccessible(true);
            return (TraceHudModel) method.invoke(session, delegate);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void invokeCoordinatorActions(
            TraceSessionLauncher session,
            List<TraceRunPlaybackCoordinator.Action> actions) {
        try {
            var method = TraceSessionLauncher.class.getDeclaredMethod(
                    "applyRunCoordinatorActions", List.class);
            method.setAccessible(true);
            method.invoke(session, actions);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void installCurrentLoop(GameLoop loop) {
        try {
            Engine engine = mock(Engine.class);
            Field loopField = Engine.class.getDeclaredField("gameLoop");
            loopField.setAccessible(true);
            loopField.set(engine, loop);
            setStaticField(Engine.class, "instance", engine);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setStaticField(
            Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
