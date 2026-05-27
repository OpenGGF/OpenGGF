package com.openggf.game.rewind;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
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

    private GameplayModeContext installedGameplayMode;
    private LiveRewindInputSource inputSource;
    private RewindController rewindController;
    private RewindSpeedController speedController = RewindSpeedController.disabled();
    private boolean rewinding;
    /** Cached AudioManager reference, resolved once at install time so
     *  later reset paths reuse it instead of re-querying GameServices
     *  (which would tick the ArchUnit baseline counter). */
    private com.openggf.audio.AudioManager audio;

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
            if (rewindController.currentFrame() <= rewindController.earliestAvailableFrame()) {
                if (rewinding) {
                    cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_ALL_PRESENTATION);
                }
                rewinding = false;
                speedController.reset();
                return true;
            }
            if (!rewinding) {
                GameServices.audio().beginReverseAudioPresentation();
                beginReverseFadePresentation();
            }
            rewinding = true;
            int completed = stepBackward(speedController.stepsWhileHeld());
            if (completed == 0) {
                cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_ALL_PRESENTATION);
                rewinding = false;
                speedController.reset();
                return true;
            }
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
        // Rewind the input source to just the synthetic frame-0 row;
        // the previous act's input history is irrelevant to the new
        // act, and keeping it would leave rewindController.currentFrame
        // out of sync with the freshly-reset audio frame domain.
        inputSource.discardAfter(0);
        // Re-anchor the audio domain at frame 0 before the controller
        // rebuilds its keyframes. Without this, seamless level/act
        // transitions leave gameplayAudioFrame and
        // rewindController.currentFrame in different domains — audio
        // command entries land on the post-transition gameplayAudioFrame
        // while audio keyframes are indexed by the rewound
        // rewindController.currentFrame, and replayToLogicalState's
        // `entry.frame() <= targetFrame` check skips across domains.
        // Resetting here mirrors what ensureInstalled does on first
        // install: every level/act boundary becomes a clean frame-0
        // origin for both counters and the rewind HUD counter starts
        // from 0 too. Uses the cached `audio` field resolved at
        // ensureInstalled to avoid a fresh GameServices.audio() lookup.
        audio.resetForLevelRewindSegment();
        rewindController.resetToFrameZero();
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
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null) {
            clear();
            return false;
        }
        if (gameplayMode == installedGameplayMode && rewindController != null && inputSource != null) {
            return true;
        }
        inputSource = new LiveRewindInputSource();
        gameplayMode.installPlaybackController(
                inputSource,
                new LiveRewindStepper(inputSource, config),
                KEYFRAME_INTERVAL);
        rewindController = gameplayMode.getRewindController();
        if (rewindController != null) {
            // Share a frame-0 origin between the audio command
            // timeline and the rewind controller. Without this the
            // two counters live in different domains: gameplayAudioFrame
            // (audio side) starts at whatever value it had after
            // pre-level activity, while rewindController.currentFrame
            // (rewind side) starts at 0. Audio command entries get
            // recorded with the gameplayAudioFrame value, but keyframe
            // lookups and stepBackward target use the rewindController
            // value — so replayToLogicalState's
            // `entry.frame() <= targetFrame` check compares across
            // domains and skips entries that should fire. The reset
            // here re-anchors all audio state at 0, the rewind
            // controller's currentFrame is already 0, and the
            // resetBufferAtCurrentFrame below re-captures the
            // frame-0 keyframe under the clean origin.
            audio = GameServices.audio();
            audio.resetForLevelRewindSegment();
            audio.setLiveRewindAudioKeyframes(rewindController.audioKeyframes());
            rewindController.resetBufferAtCurrentFrame();
        }
        installedGameplayMode = gameplayMode;
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
        installedGameplayMode = null;
        inputSource = null;
        rewindController = null;
        speedController.reset();
        speedController = RewindSpeedController.disabled();
        rewinding = false;
        audio = null;
        GameServices.audio().setLiveRewindAudioKeyframes(null);
    }
}
