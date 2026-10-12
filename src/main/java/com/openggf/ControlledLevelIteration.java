package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.session.GameplayModeContext;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Host-row orchestration for creator-controlled gameplay, shared owners remain injected. */
final class ControlledLevelIteration {
    private ControlledLevelIteration() { }

    /** Host pause remains independent of a creator's own Start-button menu. */
    record HostPause(int pauseKey, int frameStepKey, BooleanSupplier paused,
            BooleanSupplier consumePlaybackPause, Runnable toggleUserPause) {
        boolean frameStep(InputHandler input, boolean overlayOwnsPause) {
            if (!consumePlaybackPause.getAsBoolean() && !overlayOwnsPause && input.isKeyPressed(pauseKey)) {
                toggleUserPause.run();
            }
            return paused.getAsBoolean() && input.isKeyPressed(frameStepKey);
        }
    }

    record ConfigurationCommands(BooleanSupplier levelActive, BooleanSupplier holdsTransition,
            Predicate<LevelInputOverlay> handle) { }

    record SceneActAdmission(BooleanSupplier consume, Runnable audio) { }

    static void step(GameplayModeContext context, InputHandler input,
            EscapeToMasterTitleController escape, UserRecordingRuntimeControls recording,
            LiveRewindManager rewind, PlaybackDebugManager playback,
            HostPause hostPause, ConfigurationCommands configuration, SceneActAdmission sceneAct,
            Runnable syncPlayback, Runnable beginAudio,
            Runnable finishAudio, Runnable showTitle) {
        input.refreshLogicalSnapshot();
        boolean fadeWasActive = context.getFadeManager().isActive();
        boolean fadeServiced = false;
        if (GameLoopPauseInput.configurationPaused(GameMode.LEVEL) || configuration.holdsTransition().getAsBoolean()) {
            servicePresentationFade(context);
            fadeServiced = true;
        }
        // A completion may replace this visit or return to a scene/title. The
        // finishing presentation row cannot enter the former level's controller.
        if (!configuration.levelActive().getAsBoolean()) {
            input.update();
            return;
        }
        if (configuration.holdsTransition().getAsBoolean()) {
            input.update();
            return;
        }
        boolean overlayOwnsPause = GameLoopPauseInput.handleOverlay(GameMode.LEVEL, input);
        var overlay = GameLoopPauseInput.overlay(GameMode.LEVEL);
        if (overlayOwnsPause) escape.reset();
        if (overlay != null && configuration.handle().test(overlay)) {
            input.update();
            return;
        }
        if (!overlayOwnsPause) escape.update(GameMode.LEVEL, input);
        // Returning to the host is presentation work: HOLD and window/user
        // pause must not prevent its fade callback from completing.
        if (escape.transitionStarted()) {
            if (!fadeServiced) servicePresentationFade(context);
            input.update();
            return;
        }
        if (!overlayOwnsPause) recording.updateLevelControlInput(input);
        boolean frameStep = !GameLoopPauseInput.configurationPaused(GameMode.LEVEL)
                && hostPause.frameStep(input, overlayOwnsPause);
        if (hostPause.paused().getAsBoolean() && !frameStep) {
            if (!fadeServiced) servicePresentationFade(context);
            input.update();
            return;
        }
        // The native scene/act bridge owns both fades and the exit-admission row.
        // Its consume call advances input; service only a fade already active at
        // row entry, preserving the native outer iteration's fade timing.
        if (sceneAct.consume().getAsBoolean()) {
            if (fadeWasActive && !fadeServiced) servicePresentationFade(context);
            sceneAct.audio().run();
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

    /** Modal holds retain the native palette fade owner without entering a controller/world row. */
    private static void servicePresentationFade(GameplayModeContext context) {
        if (!context.getFadeManager().isActive()) return;
        context.plcFrameLifecycle().runLogicalIteration(frame -> {
            if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE))
                LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(context), frame);
        }, context.getFadeManager()::update, frame -> null);
    }
}
