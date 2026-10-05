package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.session.GameplayModeContext;

/** Host-row orchestration for creator-controlled gameplay, shared owners remain injected. */
final class ControlledLevelIteration {
    private ControlledLevelIteration() { }

    static void step(GameplayModeContext context, InputHandler input,
            EscapeToMasterTitleController escape, UserRecordingRuntimeControls recording,
            LiveRewindManager rewind, PlaybackDebugManager playback,
            Runnable syncPlayback, Runnable beginAudio, Runnable finishAudio, Runnable showTitle) {
        if (ControlledFrameRuntime.prepareSetup(context)) return;
        input.refreshLogicalSnapshot();
        var controller = ControlledFrameRuntime.controller(context);
        escape.update(GameMode.LEVEL, input);
        recording.updateLevelControlInput(input);
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
            rewind.recordExternalFrame(GameMode.LEVEL, false, input);
        }
        finishAudio.run();
        if (controller.consumeTitleRequest()) {
            controller.close();
            showTitle.run();
        }
        input.update();
    }
}
