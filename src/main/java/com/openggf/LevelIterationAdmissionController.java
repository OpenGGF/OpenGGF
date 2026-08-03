package com.openggf;

import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.recording.UserRecordingStopReason;
import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Owns level/title admission and deferred seamless-boundary completion. */
final class LevelIterationAdmissionController {
    private boolean seamlessBoundaryCompletionPending;
    private int lastAppliedPlaybackFrame = -1;

    LevelFrameResult admit(
            GameMode mode,
            BooleanSupplier updateTitleCard,
            Supplier<LevelFrameResult> titleReleaseResult,
            LevelManager levelManager,
            GameplayModeContext gameplayMode,
            boolean startEdge,
            UserRecordingRuntimeControls recordingControls,
            Runnable startPendingTitleCard,
            Runnable activateRepresentedHardwareTiming,
            Runnable deactivateHardwareTimingGap) {
        if (mode == GameMode.TITLE_CARD) {
            deactivateHardwareTimingGap.run();
            if (!updateTitleCard.getAsBoolean()) {
                return LevelFrameResult.SETUP_ONLY;
            }
            LevelFrameResult releaseResult = titleReleaseResult.get();
            if (releaseResult == LevelFrameResult.GAMEPLAY_FRAME) {
                activateRepresentedHardwareTiming.run();
            }
            return releaseResult;
        }
        if (mode != GameMode.LEVEL) {
            activateRepresentedHardwareTiming.run();
            return LevelFrameResult.GAMEPLAY_FRAME;
        }
        SeamlessLevelTransitionRequest request =
                levelManager.consumeSeamlessTransitionRequest();
        if (request != null) {
            deactivateHardwareTimingGap.run();
            recordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
            TraceSessionLauncher.markNextRunLevelLoadCause(
                    com.openggf.trace.replay.runs.RunLevelLoadCause.LEVEL_ADVANCE);
            levelManager.applySeamlessTransition(request);
            startPendingTitleCard.run();
            seamlessBoundaryCompletionPending = true;
        } else {
            activateRepresentedHardwareTiming.run();
        }
        return LevelFrameStep.admit(
                LevelFrameContext.from(gameplayMode), levelManager, startEdge).result();
    }

    boolean completePendingBoundary(
            boolean doFrameStep,
            java.util.function.Consumer<Boolean> updateAudio,
            Runnable finishPlayback,
            Supplier<GameplayModeContext> gameplayContext) {
        if (!seamlessBoundaryCompletionPending) {
            return false;
        }
        seamlessBoundaryCompletionPending = false;
        updateAudio.accept(doFrameStep);
        finishPlayback.run();
        TraceSessionLauncher traceSession = TraceSessionLauncher.active();
        if (traceSession != null) {
            traceSession.recordExternalRewindFrameAtBoundary();
        } else {
            GameplayModeContext context = gameplayContext.get();
            if (context != null) {
                context.markRewindBoundary(RewindBoundary.SEAMLESS_LEVEL_TRANSITION);
            }
        }
        return true;
    }

    void reset() {
        seamlessBoundaryCompletionPending = false;
    }

    void finishPlaybackBoundary(
            boolean advance,
            PlaybackDebugManager playback,
            UserRecordingRuntimeControls recordingControls) {
        int appliedFrame;
        if (advance) {
            appliedFrame = playback.getCursorFrame();
            lastAppliedPlaybackFrame = appliedFrame;
            playback.onLevelFrameAdvanced();
        } else {
            appliedFrame = lastAppliedPlaybackFrame;
            if (appliedFrame < 0) {
                return;
            }
        }
        recordingControls.afterPlaybackFrame(
                appliedFrame,
                true,
                isPlaybackMovieEnd(
                        appliedFrame,
                        playback.getMovieFrameCount(),
                        playback.isSessionPlaying()));
    }

    void setLastAppliedPlaybackFrame(int frame) {
        lastAppliedPlaybackFrame = frame;
    }

    void resetLastAppliedPlaybackFrame() {
        lastAppliedPlaybackFrame = -1;
    }

    UserRecordingMenu.PlaybackStarter withAppliedPlaybackFrameReset(
            UserRecordingMenu.PlaybackStarter starter) {
        Objects.requireNonNull(starter, "starter");
        return (entry, options) -> {
            resetLastAppliedPlaybackFrame();
            starter.start(entry, options);
        };
    }

    static void driveTraceRunSession(GameMode mode, int cursorFrame) {
        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) {
            session.runAdvanceTickIfActive(mode, cursorFrame);
        }
    }

    /** Runs one host step and guarantees all-mode run observation afterward. */
    static void runTraceObservedStep(
            Runnable step, Supplier<GameMode> mode, IntSupplier cursorFrame) {
        try {
            step.run();
        } finally {
            driveTraceRunSession(mode.get(), cursorFrame.getAsInt());
        }
    }

    static boolean shouldVisualTraceOwnEscape(
            GameMode mode, TraceSessionLauncher session, boolean escapePressed) {
        return session != null && escapePressed
                && (mode == GameMode.LEVEL || session.isRunSession()
                        || session.isPresentingTitleCard());
    }

    static void prepareTraceHardwareTimingForAdmission(GameMode mode) {
        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) {
            session.prepareHardwareTimingForAdmission(mode);
        }
    }

    static void refreshTraceInputSnapshot(com.openggf.control.InputHandler input) {
        TraceSessionLauncher.applyRunTerminalTailInputIfActive(input);
        input.refreshLogicalSnapshot();
    }

    private static void admitTraceRunDestination(GameMode mode) {
        TraceSessionLauncher.admitRunDestinationBeforeProductionIfActive(mode);
    }

    static void prepareTraceRunAdmissionAndHardwareTiming(
            GameMode mode, Runnable syncPlaybackInput) {
        admitTraceRunDestination(mode);
        syncPlaybackInput.run();
        prepareTraceHardwareTimingForAdmission(mode);
    }

    static void deactivateTraceHardwareTimingForAdmission() {
        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) {
            session.deactivateHardwareTimingForAdmission();
        }
    }

    static boolean isPlaybackMovieEnd(
            int appliedFrame, int movieFrameCount, boolean sessionPlaying) {
        return movieFrameCount > 0
                && !sessionPlaying
                && appliedFrame >= movieFrameCount - 1;
    }

    static SpecialStageStartupPolicy specialStageStartupPolicy(boolean playbackActive) {
        return playbackActive
                ? SpecialStageStartupPolicy.TRACE_ACCURATE
                : SpecialStageStartupPolicy.FAST;
    }
}
