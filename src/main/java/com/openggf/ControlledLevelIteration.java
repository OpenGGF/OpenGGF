package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.session.GameplayModeContext;
import java.util.function.BooleanSupplier;

/** Host-row orchestration for creator-controlled gameplay, shared owners remain injected. */
final class ControlledLevelIteration {
    private ControlledLevelIteration() { }

    /** Host pause remains independent of a creator's own Start-button menu. */
    record HostPause(int pauseKey, int frameStepKey, BooleanSupplier paused,
            BooleanSupplier consumePlaybackPause, Runnable toggleUserPause) {
        boolean frameStep(InputHandler input, boolean overlayOwnsPause) {
            if (!overlayOwnsPause && !consumePlaybackPause.getAsBoolean() && input.isKeyPressed(pauseKey)) {
                toggleUserPause.run();
            }
            return paused.getAsBoolean() && input.isKeyPressed(frameStepKey);
        }
    }

    static void step(GameplayModeContext context, InputHandler input,
            EscapeToMasterTitleController escape, UserRecordingRuntimeControls recording,
            LiveRewindManager rewind, PlaybackDebugManager playback,
            HostPause hostPause, Runnable syncPlayback, Runnable beginAudio,
            Runnable finishAudio, Runnable showTitle) {
        input.refreshLogicalSnapshot();
        escape.update(GameMode.LEVEL, input);
        // Returning to the host is presentation work: HOLD and window/user
        // pause must not prevent its fade callback from completing.
        if (escape.transitionStarted()) {
            context.plcFrameLifecycle().runLogicalIteration(context.getFadeManager()::update, frame -> null);
            input.update();
            return;
        }
        recording.updateLevelControlInput(input);
        boolean overlayOwnsPause = GameLoopPauseInput.handleOverlay(GameMode.LEVEL, input);
        boolean frameStep = hostPause.frameStep(input, overlayOwnsPause);
        if (hostPause.paused().getAsBoolean() && !frameStep) {
            input.update();
            return;
        }
        if (ControlledFrameRuntime.prepareSetup(context)) return;
        var controller = ControlledFrameRuntime.controller(context);
        if (controller.allowsDebugRewind()
                && rewind.handleRealtimeRewindInput(GameMode.LEVEL, false, input)) {
            input.update();
            return;
        }
        syncPlayback.run();
        recording.beforeLevelFrame(input);
        beginAudio.run();
        LevelFrameResult result = ControlledFrameRuntime.step(context, input, input.logical(), true);
        // Setup is not a movie row or rewind frame. Held mode ticks still carry
        // deterministic aim/meter/menu input even when world physics is frozen.
        if (result != LevelFrameResult.SETUP_ONLY) {
            recording.afterLevelFrame();
            playback.onCurrentGameplayTickExecuted();
            playback.onLevelFrameAdvanced();
            rewind.recordExternalFrame(GameMode.LEVEL, frameStep, input);
        }
        finishAudio.run();
        if (controller.consumeTitleRequest()) {
            controller.close();
            showTitle.run();
        }
        input.update();
    }
}
