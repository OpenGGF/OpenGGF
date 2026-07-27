package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.recording.UserRecordingStopReason;
import com.openggf.debug.playback.PlaybackDebugManager;

import java.util.function.BooleanSupplier;
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
            Runnable startPendingTitleCard) {
        if (mode == GameMode.TITLE_CARD) {
            return updateTitleCard.getAsBoolean()
                    ? titleReleaseResult.get()
                    : LevelFrameResult.SETUP_ONLY;
        }
        if (mode != GameMode.LEVEL) {
            return LevelFrameResult.GAMEPLAY_FRAME;
        }
        SeamlessLevelTransitionRequest request =
                levelManager.consumeSeamlessTransitionRequest();
        if (request != null) {
            recordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
            levelManager.applySeamlessTransition(request);
            startPendingTitleCard.run();
            seamlessBoundaryCompletionPending = true;
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

    static void driveTraceRunSession(GameMode mode, int cursorFrame) {
        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) {
            session.runAdvanceTickIfActive(mode, cursorFrame);
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
