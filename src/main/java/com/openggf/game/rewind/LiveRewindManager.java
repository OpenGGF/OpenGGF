package com.openggf.game.rewind;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.Objects;

/**
 * Runtime glue for optional held-key rewind during ordinary live level play.
 */
public final class LiveRewindManager {

    private static final int KEYFRAME_INTERVAL = 60;

    private final SonicConfigurationService config;
    private final LiveRewindHudOverlay hudOverlay;

    private GameRuntime installedRuntime;
    private LiveRewindInputSource inputSource;
    private RewindController rewindController;
    private RewindSpeedController speedController = RewindSpeedController.disabled();
    private boolean rewinding;

    public LiveRewindManager(SonicConfigurationService config) {
        this.config = Objects.requireNonNull(config, "config");
        this.hudOverlay = new LiveRewindHudOverlay(this::statusLabel);
    }

    public boolean handleRealtimeRewindInput(GameMode mode, InputHandler input) {
        if (mode != GameMode.LEVEL || input == null || !enabled()) {
            clear();
            return false;
        }
        if (!ensureInstalled()) {
            return false;
        }
        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        if (input.isKeyDown(rewindKey)) {
            if (!rewinding) {
                GameServices.audio().beginReverseAudioPresentation();
                beginReverseFadePresentation();
            }
            rewinding = true;
            stepBackward(speedController.stepsWhileHeld());
            GameServices.audio().update();
            return true;
        }
        int coastSteps = speedController.stepsAfterRelease();
        if (rewinding && coastSteps > 0) {
            if (stepBackward(coastSteps) > 0) {
                GameServices.audio().update();
                return true;
            }
            speedController.reset();
        }
        if (rewinding) {
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);
        }
        rewinding = false;
        return false;
    }

    public void recordExternalFrame(GameMode mode, InputHandler input) {
        if (mode != GameMode.LEVEL || input == null || rewinding || !enabled()) {
            if (mode != GameMode.LEVEL || input == null || !enabled()) {
                clear();
            }
            return;
        }
        if (!ensureInstalled()) {
            return;
        }
        inputSource.discardAfter(rewindController.currentFrame());
        inputSource.appendFrame(input, config);
        rewindController.recordExternalStep();
    }

    public void resetBufferAtCurrentFrame(GameMode mode) {
        if (mode != GameMode.LEVEL || !enabled() || !ensureInstalled()) {
            return;
        }
        inputSource.discardAfter(rewindController.currentFrame());
        rewindController.resetBufferAtCurrentFrame();
    }

    public void renderHud(GameMode mode, PixelFontTextRenderer text) {
        if (mode != GameMode.LEVEL || text == null || !enabled()) {
            return;
        }
        hudOverlay.render(text);
    }

    private String statusLabel() {
        if (!enabled() || !rewinding || rewindController == null) {
            return null;
        }
        return "REWIND " + rewindController.currentFrame();
    }

    private boolean ensureInstalled() {
        GameRuntime runtime = RuntimeManager.getCurrent();
        if (runtime == null || runtime.getGameplayModeContext() == null) {
            clear();
            return false;
        }
        if (runtime == installedRuntime && rewindController != null && inputSource != null) {
            return true;
        }
        inputSource = new LiveRewindInputSource();
        runtime.getGameplayModeContext().installPlaybackController(
                inputSource,
                new LiveRewindStepper(inputSource, config),
                KEYFRAME_INTERVAL);
        rewindController = runtime.getGameplayModeContext().getRewindController();
        installedRuntime = runtime;
        speedController = RewindSpeedController.fromConfig(config);
        rewinding = false;
        return rewindController != null;
    }

    private int stepBackward(int steps) {
        int completed = 0;
        for (int i = 0; i < steps; i++) {
            if (!rewindController.stepBackward()) {
                break;
            }
            completed++;
        }
        return completed;
    }

    private boolean enabled() {
        return config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
    }

    private void cleanupAudioAfterRealtimeRewind(AudioPresentationPolicy policy) {
        if (rewindController == null) {
            return;
        }
        GameServices.audio().afterRewindRestore(rewindController.currentFrame(), policy);
    }

    private void beginReverseFadePresentation() {
        FadeManager fadeManager = GameServices.fadeOrNull();
        if (fadeManager != null) {
            fadeManager.beginReversePresentation();
        }
    }

    private void cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy policy) {
        cleanupAudioAfterRealtimeRewind(policy);
        FadeManager fadeManager = GameServices.fadeOrNull();
        if (fadeManager != null) {
            fadeManager.endReversePresentation();
        }
    }

    private void clear() {
        if (rewinding && rewindController != null) {
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_ALL_PRESENTATION);
        }
        installedRuntime = null;
        inputSource = null;
        rewindController = null;
        speedController.reset();
        speedController = RewindSpeedController.disabled();
        rewinding = false;
    }
}
