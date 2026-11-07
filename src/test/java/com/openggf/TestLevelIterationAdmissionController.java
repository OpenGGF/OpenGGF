package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestLevelIterationAdmissionController {
    private TraceSessionLauncher previousTraceSession;

    @BeforeEach
    void clearActiveTraceSession() throws Exception {
        previousTraceSession = TraceSessionLauncher.active();
        setActiveTraceSession(null);
    }

    @AfterEach
    void restoreActiveTraceSession() throws Exception {
        setActiveTraceSession(previousTraceSession);
    }

    @Test
    void admittedSeamlessTransitionCompletesThroughLiveBoundaryReporter() {
        var controller = new LevelIterationAdmissionController();
        var level = mock(LevelManager.class);
        var recording = mock(UserRecordingRuntimeControls.class);
        Runnable startPendingTitle = mock(Runnable.class);
        Runnable deactivateHardwareTiming = mock(Runnable.class);
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY)
                .build();
        when(level.consumeSeamlessTransitionRequest()).thenReturn(request);
        GameplayModeContext context = new GameplayModeContext(
                new WorldSession(new Sonic2GameModule()));
        List<RewindBoundary> boundaries = new ArrayList<>();
        context.setRewindBoundaryReporter(boundaries::add);

        LevelFrameResult result = controller.admit(
                GameMode.LEVEL,
                () -> false,
                () -> LevelFrameResult.SETUP_ONLY,
                level,
                context,
                false,
                recording,
                startPendingTitle,
                () -> { },
                deactivateHardwareTiming);

        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result);
        var admissionOrder = inOrder(level, startPendingTitle);
        admissionOrder.verify(level).applySeamlessTransition(request);
        admissionOrder.verify(startPendingTitle).run();
        verify(deactivateHardwareTiming).run();

        @SuppressWarnings("unchecked")
        Consumer<Boolean> updateAudio = mock(Consumer.class);
        Runnable finishPlayback = mock(Runnable.class);
        assertTrue(controller.completePendingBoundary(
                true, updateAudio, finishPlayback, () -> context));

        verify(updateAudio).accept(true);
        verify(finishPlayback).run();
        assertEquals(
                List.of(RewindBoundary.SEAMLESS_LEVEL_TRANSITION),
                boundaries);
        assertFalse(controller.completePendingBoundary(
                true, updateAudio, finishPlayback, () -> context),
                "a completed seamless boundary must not be reported twice");
    }

    @Test
    void visualTraceOwnsEscapeDuringTitleCardPresentation()
            throws Exception {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null,
                List.<com.openggf.trace.replay.runs.TraceRunSegmentDescriptor>of(),
                null, null);
        session.beginTitleCardPresentation(
                new TraceSessionLauncher.TitleCardPresentation() {
                    @Override
                    public void prepareLevel() {
                    }

                    @Override
                    public void enterTitleCard() {
                    }
                });

        assertTrue(LevelIterationAdmissionController.shouldVisualTraceOwnEscape(
                GameMode.TITLE_CARD, session, true));
        assertFalse(LevelIterationAdmissionController.shouldVisualTraceOwnEscape(
                GameMode.TITLE_CARD, session, false));
    }

    @Test
    void visualTraceTitleCardReleaseBecomesAOneShotSetupBarrier()
            throws Exception {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null,
                List.<com.openggf.trace.replay.runs.TraceRunSegmentDescriptor>of(),
                null, null);
        session.beginTitleCardPresentation(
                new TraceSessionLauncher.TitleCardPresentation() {
                    @Override
                    public void prepareLevel() {
                    }

                    @Override
                    public void enterTitleCard() {
                    }
                });
        var controller = new LevelIterationAdmissionController();

        LevelFrameResult release = controller.admit(
                GameMode.TITLE_CARD,
                () -> true,
                () -> LevelFrameResult.GAMEPLAY_FRAME,
                mock(LevelManager.class),
                new GameplayModeContext(
                        new WorldSession(new Sonic2GameModule())),
                false,
                mock(UserRecordingRuntimeControls.class),
                () -> { }, () -> { }, () -> { });

        assertEquals(LevelFrameResult.SETUP_ONLY, release,
                "S2 must not fall through into an uncontrolled level frame");
        assertTrue(session.beginReplayBootstrapAfterTitleCardIfReady(
                GameMode.LEVEL));
        assertFalse(session.beginReplayBootstrapAfterTitleCardIfReady(
                GameMode.LEVEL));
    }

    @Test
    void visualTraceSetupOnlyTitleCardReleaseStillArmsReplayBootstrap()
            throws Exception {
        TraceSessionLauncher session = new TraceSessionLauncher(
                null, null,
                List.<com.openggf.trace.replay.runs.TraceRunSegmentDescriptor>of(),
                null, null);
        session.beginTitleCardPresentation(
                new TraceSessionLauncher.TitleCardPresentation() {
                    @Override
                    public void prepareLevel() {
                    }

                    @Override
                    public void enterTitleCard() {
                    }
                });
        var controller = new LevelIterationAdmissionController();

        LevelFrameResult release = controller.admit(
                GameMode.TITLE_CARD,
                () -> true,
                () -> LevelFrameResult.SETUP_ONLY,
                mock(LevelManager.class),
                new GameplayModeContext(
                        new WorldSession(new Sonic2GameModule())),
                false,
                mock(UserRecordingRuntimeControls.class),
                () -> { }, () -> { }, () -> { });

        assertEquals(LevelFrameResult.SETUP_ONLY, release);
        assertTrue(session.beginReplayBootstrapAfterTitleCardIfReady(
                GameMode.LEVEL),
                "S1/S3K setup-only release must still arm prepared replay");
    }

    @Test
    void startCannotPauseFreshFadeOrPretitleButCanPauseFirstOrdinaryHold() {
        var controller = new LevelIterationAdmissionController();
        var level = mock(LevelManager.class);
        when(level.hasPendingFreshLevelTransitionBoundary()).thenReturn(true);
        var context = new GameplayModeContext(new WorldSession(new Sonic2GameModule()));
        context.attachGameplayManagers(new com.openggf.camera.Camera(),
                new com.openggf.timer.TimerManager(), new com.openggf.game.GameStateManager(),
                new com.openggf.graphics.FadeManager(),
                new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2),
                new com.openggf.game.solid.DefaultSolidExecutionRegistry());
        context.getGameStateManager().startNewGameFromTitle();
        var recording = mock(UserRecordingRuntimeControls.class);
        java.util.function.BiFunction<GameMode, Boolean, LevelFrameResult> admit = (mode, start) ->
                controller.admit(mode, () -> true, () -> LevelFrameResult.SETUP_ONLY,
                        level, context, start, recording, () -> { }, () -> { }, () -> { });
        controller.loadedFreshBoundary(true);
        assertEquals(LevelFrameResult.SETUP_ONLY, admit.apply(GameMode.LEVEL, true));
        assertFalse(context.getGameStateManager().isGamePaused());
        controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, admit.apply(GameMode.LEVEL, true));
        assertFalse(context.getGameStateManager().isGamePaused());
        assertEquals(LevelFrameResult.SETUP_ONLY, admit.apply(GameMode.TITLE_CARD, false));
        assertEquals(LevelFrameResult.PAUSED, admit.apply(GameMode.LEVEL, true));
        org.mockito.Mockito.verify(level, org.mockito.Mockito.never())
                .publishFreshLevelTransitionInitialBoundary();
        assertEquals(LevelFrameResult.SETUP_ONLY, admit.apply(GameMode.LEVEL, true));
        assertFalse(context.getGameStateManager().isGamePaused());
        verify(level).publishFreshLevelTransitionInitialBoundary();
        assertEquals(LevelFrameResult.PAUSED, admit.apply(GameMode.LEVEL, true));
        assertEquals(LevelFrameResult.PAUSED, admit.apply(GameMode.LEVEL, false));
        verify(level, never()).completeFreshLevelTransitionBoundary();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, admit.apply(GameMode.LEVEL, true));
        assertFalse(context.getGameStateManager().isGamePaused(), "assembly admission must not toggle Start twice");
        verify(level).completeFreshLevelTransitionBoundary();
        controller.reset();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, admit.apply(GameMode.LEVEL, true));
        assertFalse(context.getGameStateManager().isGamePaused(), "reset removes completed-title authority");
    }

    @ParameterizedTest
    @EnumSource(value = LevelFrameResult.class, names = {"SETUP_ONLY", "GAMEPLAY_FRAME"})
    void freshTitleReleaseRetainsOneOrdinaryHoldBeforeAssembly(LevelFrameResult releaseResult) {
        var fresh = new FreshAdmission();
        fresh.controller.loadedFreshBoundary(true);
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, never()).consumePendingInitialProcessSpritesPass();

        fresh.titleComplete = false;
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.TITLE_CARD));
        verifyNoInteractions(fresh.release, fresh.activateTiming, fresh.assembly);
        verify(fresh.deactivateTiming).run();
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL),
                "an incomplete title cannot authorize publication or pause admission");

        fresh.titleComplete = true;
        when(fresh.release.get()).thenReturn(releaseResult);
        assertEquals(releaseResult, fresh.admit(GameMode.TITLE_CARD));
        verify(fresh.activateTiming, times(releaseResult == LevelFrameResult.GAMEPLAY_FRAME ? 1 : 0)).run();
        verify(fresh.level, never()).publishFreshLevelTransitionInitialBoundary();
        verifyNoInteractions(fresh.assembly);

        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.TITLE_SCREEN));
        verify(fresh.level, never()).publishFreshLevelTransitionInitialBoundary();
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, never()).completeFreshLevelTransitionBoundary();
        verify(fresh.level, never()).consumePendingInitialProcessSpritesPass();
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        var order = inOrder(fresh.level, fresh.assembly, fresh.activateTiming);
        order.verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
        order.verify(fresh.level).completeFreshLevelTransitionBoundary();
        order.verify(fresh.assembly).accept(fresh.context);
        order.verify(fresh.level).consumeSeamlessTransitionRequest();
        order.verify(fresh.activateTiming).run();
        order.verify(fresh.level).consumePendingInitialProcessSpritesPass();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void titleCompletionDoesNotClearTheSameIterationLoadHold() {
        var fresh = new FreshAdmission();
        fresh.controller.loadedFreshBoundary(true);
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.TITLE_CARD));
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, never()).publishFreshLevelTransitionInitialBoundary();
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "pending-load", "titleless-load"})
    void resetOrAnotherLoadDiscardsPriorTitleAndPublication(String restart) {
        var fresh = new FreshAdmission();
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.TITLE_CARD));
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        switch (restart) {
            case "reset" -> fresh.controller.reset();
            case "pending-load" -> fresh.controller.loadedFreshBoundary(true);
            case "titleless-load" -> fresh.controller.loadedFreshBoundary(false);
            default -> throw new AssertionError(restart);
        }
        assertEquals(restart.equals("pending-load") ? LevelFrameResult.SETUP_ONLY : LevelFrameResult.GAMEPLAY_FRAME,
                fresh.admit(GameMode.LEVEL));
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, never()).completeFreshLevelTransitionBoundary();
        verifyNoInteractions(fresh.assembly);

        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.TITLE_CARD));
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, times(2)).publishFreshLevelTransitionInitialBoundary();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void repeatedTitleCompletionRequiresAnotherOrdinaryHold() {
        var fresh = new FreshAdmission();
        fresh.admit(GameMode.TITLE_CARD);
        fresh.admit(GameMode.LEVEL);
        fresh.admit(GameMode.TITLE_CARD);
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level, times(2)).publishFreshLevelTransitionInitialBoundary();
        verify(fresh.level, never()).completeFreshLevelTransitionBoundary();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void traceReleaseBarrierRetainsCompletedTitleWithoutActivatingGameplayTiming() throws Exception {
        var session = new TraceSessionLauncher(null, null,
                List.<com.openggf.trace.replay.runs.TraceRunSegmentDescriptor>of(), null, null);
        session.beginTitleCardPresentation(new TraceSessionLauncher.TitleCardPresentation() {
            @Override public void prepareLevel() { }
            @Override public void enterTitleCard() { }
        });
        var fresh = new FreshAdmission();
        when(fresh.release.get()).thenReturn(LevelFrameResult.GAMEPLAY_FRAME);
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.TITLE_CARD));
        verify(fresh.release).get();
        verifyNoInteractions(fresh.activateTiming, fresh.assembly);
        assertTrue(session.beginReplayBootstrapAfterTitleCardIfReady(GameMode.LEVEL));
        fresh.controller.beginIteration();
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void titleReleaseFailureStillLeavesTheCompletedTitleAwaitingPublication() {
        var fresh = new FreshAdmission();
        var failure = new IllegalStateException("release failed");
        when(fresh.release.get()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fresh.admit(GameMode.TITLE_CARD)));
        verifyNoInteractions(fresh.activateTiming, fresh.assembly);
        assertEquals(LevelFrameResult.SETUP_ONLY, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
    }

    @Test
    void publicationFailurePropagatesAfterTheOrdinaryBoundaryHasBeenObserved() {
        var fresh = new FreshAdmission();
        fresh.admit(GameMode.TITLE_CARD);
        var failure = new IllegalStateException("publication failed");
        doThrow(failure).when(fresh.level).publishFreshLevelTransitionInitialBoundary();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fresh.admit(GameMode.LEVEL)));
        verifyNoInteractions(fresh.assembly, fresh.activateTiming);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void assemblyFailurePropagatesWithoutRepeatingPublicationOrCallingTheObserver() {
        var fresh = new FreshAdmission();
        fresh.admit(GameMode.TITLE_CARD);
        fresh.admit(GameMode.LEVEL);
        var failure = new IllegalStateException("assembly failed");
        doThrow(failure).doAnswer(invocation -> {
            fresh.pending = false;
            return null;
        }).when(fresh.level).completeFreshLevelTransitionBoundary();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fresh.admit(GameMode.LEVEL)));
        verifyNoInteractions(fresh.assembly, fresh.activateTiming);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).publishFreshLevelTransitionInitialBoundary();
        verify(fresh.level, times(2)).completeFreshLevelTransitionBoundary();
        verify(fresh.assembly).accept(fresh.context);
    }

    @Test
    void observerFailureAfterAssemblyDoesNotReopenTheCompletedLevelBoundary() {
        var fresh = new FreshAdmission();
        fresh.admit(GameMode.TITLE_CARD);
        fresh.admit(GameMode.LEVEL);
        var failure = new IllegalStateException("observer failed");
        doThrow(failure).when(fresh.assembly).accept(fresh.context);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fresh.admit(GameMode.LEVEL)));
        verifyNoInteractions(fresh.activateTiming);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        verify(fresh.level).completeFreshLevelTransitionBoundary();
        verify(fresh.assembly).accept(fresh.context);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshLoadPreservesSeamlessCompletionWhileResetDiscardsIt(boolean reset) {
        var fresh = new FreshAdmission();
        fresh.pending = false;
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY).build();
        when(fresh.level.consumeSeamlessTransitionRequest()).thenReturn(request, (SeamlessLevelTransitionRequest) null);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, fresh.admit(GameMode.LEVEL));
        fresh.pending = true;
        fresh.controller.loadedFreshBoundary(true);
        if (reset) fresh.controller.reset();

        List<String> completion = new ArrayList<>();
        fresh.context.setRewindBoundaryReporter(boundary -> completion.add(boundary.name()));
        assertEquals(!reset, fresh.controller.completePendingBoundary(false,
                advance -> completion.add("audio:" + advance),
                () -> completion.add("playback"), () -> fresh.context));
        assertEquals(reset ? List.of() : List.of("audio:false", "playback", "SEAMLESS_LEVEL_TRANSITION"), completion);
        assertFalse(fresh.controller.completePendingBoundary(false,
                advance -> completion.add("duplicate"), () -> completion.add("duplicate"), () -> fresh.context));
        assertEquals(reset ? LevelFrameResult.GAMEPLAY_FRAME : LevelFrameResult.SETUP_ONLY,
                fresh.admit(GameMode.LEVEL));
        verify(fresh.level, never()).publishFreshLevelTransitionInitialBoundary();
    }

    @Test
    void freshLifecycleResetDoesNotResetTheAppliedPlaybackCursor() {
        var fresh = new FreshAdmission();
        var playback = mock(com.openggf.debug.playback.PlaybackDebugManager.class);
        fresh.controller.setLastAppliedPlaybackFrame(17);
        fresh.controller.loadedFreshBoundary(true);
        fresh.controller.reset();
        fresh.controller.finishPlaybackBoundary(false, playback, fresh.recording);
        verify(fresh.recording).afterPlaybackFrame(17, true, false);
        verify(playback, never()).onLevelFrameAdvanced();
    }

    private static final class FreshAdmission {
        private final LevelManager level = mock(LevelManager.class);
        private final GameplayModeContext context = new GameplayModeContext(new WorldSession(new Sonic2GameModule()));
        private final UserRecordingRuntimeControls recording = mock(UserRecordingRuntimeControls.class);
        @SuppressWarnings("unchecked")
        private final Consumer<GameplayModeContext> assembly = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        private final Supplier<LevelFrameResult> release = mock(Supplier.class);
        private final Runnable activateTiming = mock(Runnable.class);
        private final Runnable deactivateTiming = mock(Runnable.class);
        private final LevelIterationAdmissionController controller = new LevelIterationAdmissionController(assembly);
        private boolean pending = true;
        private boolean titleComplete = true;

        private FreshAdmission() {
            when(level.hasPendingFreshLevelTransitionBoundary()).thenAnswer(invocation -> pending);
            when(release.get()).thenReturn(LevelFrameResult.SETUP_ONLY);
            doAnswer(invocation -> {
                pending = false;
                return null;
            }).when(level).completeFreshLevelTransitionBoundary();
        }

        private LevelFrameResult admit(GameMode mode) {
            return controller.admit(mode, () -> titleComplete, release, level, context, false,
                    recording, () -> { }, activateTiming, deactivateTiming);
        }
    }

    private static void setActiveTraceSession(TraceSessionLauncher session)
            throws Exception {
        Field field = TraceSessionLauncher.class.getDeclaredField("activeSession");
        field.setAccessible(true);
        field.set(null, session);
    }
}
