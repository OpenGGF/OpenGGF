package com.openggf.game.rewind;

import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
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
    private boolean rewinding;

    public LiveRewindManager(SonicConfigurationService config) {
        this.config = Objects.requireNonNull(config, "config");
        this.hudOverlay = new LiveRewindHudOverlay(this::statusLabel);
    }

    public boolean handleRealtimeRewindInput(GameMode mode, InputHandler input) {
        if (mode != GameMode.LEVEL || input == null || !enabled()) {
            if (!enabled()) {
                clear();
            }
            return false;
        }
        if (!ensureInstalled()) {
            return false;
        }
        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        if (input.isKeyDown(rewindKey)) {
            rewinding = true;
            rewindController.stepBackward();
            return true;
        }
        rewinding = false;
        return false;
    }

    public void recordExternalFrame(GameMode mode, InputHandler input) {
        if (mode != GameMode.LEVEL || input == null || rewinding || !enabled()) {
            if (!enabled()) {
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
        if (!enabled()) {
            return null;
        }
        if (rewinding && rewindController != null) {
            return "REWIND " + rewindController.currentFrame();
        }
        return "Hold " + GlfwKeyNameResolver.nameOf(config.getInt(SonicConfiguration.LIVE_REWIND_KEY))
                + " Rewind";
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
        rewinding = false;
        return rewindController != null;
    }

    private boolean enabled() {
        return config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
    }

    private void clear() {
        installedRuntime = null;
        inputSource = null;
        rewindController = null;
        rewinding = false;
    }
}
