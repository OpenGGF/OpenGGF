package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.NoOpSpecialStageProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFontTextRenderer;

import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runtime glue for optional held-key rewind during ordinary live level play.
 */
public final class LiveRewindManager {

    private static final int KEYFRAME_INTERVAL = 60;

    private final SonicConfigurationService config;
    private final Supplier<GameMode> modeSupplier;
    private final Supplier<SpecialStageProvider> specialStageProviderSupplier;
    private final LiveRewindHudOverlay hudOverlay;

    private GameplayModeContext installedGameplayMode;
    private StepperKind installedStepperKind;
    private LiveRewindInputSource inputSource;
    private RewindController rewindController;
    private RewindSpeedController speedController = RewindSpeedController.disabled();
    private InputHandler activeInputHandler;
    private boolean rewinding;
    private final RewindEffectEnvelope effectEnvelope = new RewindEffectEnvelope();

    public LiveRewindManager(SonicConfigurationService config) {
        this(config, () -> GameMode.LEVEL, () -> NoOpSpecialStageProvider.INSTANCE);
    }

    public LiveRewindManager(SonicConfigurationService config,
                             Supplier<GameMode> modeSupplier,
                             Supplier<SpecialStageProvider> specialStageProviderSupplier) {
        this.config = Objects.requireNonNull(config, "config");
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "modeSupplier");
        this.specialStageProviderSupplier = Objects.requireNonNull(
                specialStageProviderSupplier, "specialStageProviderSupplier");
        this.hudOverlay = new LiveRewindHudOverlay(this::statusLabel);
    }

    private enum StepperKind {
        LEVEL_FRAME,
        SPECIAL_STAGE
    }

    /**
     * @param rewindBlocked true while rewind engagement must be rejected: either
     *     the level is mid a special/bonus-stage/ending transition or a pending
     *     zone/act transition, OR a fade is in flight with a completion
     *     callback that has not yet run (see {@code GameLoop.isRewindBlocked()}).
     *     {@code currentGameMode} stays {@code GameMode.LEVEL} throughout both
     *     kinds of window -- the fade only flips the mode (or otherwise acts on
     *     its result) once its completion callback runs -- so this widens the
     *     same "not applicable this frame" gate {@code mode != GameMode.LEVEL}
     *     already uses, reusing its {@link #clear()} teardown rather than
     *     needing a separate mid-hold cancel path. Note this is a STRICT
     *     SUPERSET of {@code GameLoop.isNonRewindableTransitionPending()} (the
     *     predicate that also freezes gameplay) -- the fade term must never be
     *     folded into that narrower predicate, since ROM gameplay keeps
     *     ticking during an ordinary callback-bearing fade even though rewind
     *     must still be rejected. See ssentry-rewind-report.md.
     */
    public boolean handleRealtimeRewindInput(GameMode mode, boolean rewindBlocked, InputHandler input) {
        if (!supportsCurrentRewindContext(mode) || rewindBlocked || input == null || !enabled()) {
            activeInputHandler = null;
            clear();
            return false;
        }
        activeInputHandler = input;
        if (!ensureInstalled()) {
            effectEnvelope.frameInactive();
            return false;
        }
        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        if (input.isKeyDown(rewindKey)) {
            if (!rewinding) {
                GameServices.audio().beginReverseAudioPresentation();
                beginReverseFadePresentation();
            }
            rewinding = true;
            speedController.setHeldSpeedMultiplier(resolveHeldSpeedMultiplier(input));
            int steps = speedController.stepsWhileHeld();
            GameServices.audio().setReversePlaybackRate(speedController.currentSpeed());
            stepBackward(steps);
            effectEnvelope.frameActive(speedController.currentSpeed());
            GameServices.audio().update();
            return true;
        }
        int coastSteps = speedController.stepsAfterRelease();
        if (rewinding && coastSteps > 0) {
            if (stepBackward(coastSteps) > 0) {
                GameServices.audio().setReversePlaybackRate(speedController.currentSpeed());
                effectEnvelope.frameActive(speedController.currentSpeed());
                GameServices.audio().update();
                return true;
            }
            speedController.reset();
        }
        if (rewinding) {
            // Release lands a committed logical restore via
            // cleanupAudioAfterRealtimeRewind's commitDeferredAudioRestore()
            // before this cleanup runs, so the RESYNC_MUSIC variant's extra
            // music-stack pop is not needed here and would incorrectly end an
            // override (e.g. invincibility) the just-restored state says
            // should still be active. See markBoundary handlers below for the
            // boundary cases that DO need the pop (their deferred restore was
            // dropped, not committed).
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX);
        }
        rewinding = false;
        effectEnvelope.frameInactive();
        return false;
    }

    private double resolveHeldSpeedMultiplier(InputHandler input) {
        boolean halfHeld = isModifierDown(input, config.getInt(SonicConfiguration.LIVE_REWIND_HALF_SPEED_KEY));
        boolean doubleHeld = isModifierDown(input, config.getInt(SonicConfiguration.LIVE_REWIND_DOUBLE_SPEED_KEY));
        return speedMultiplier(halfHeld, doubleHeld);
    }

    /** Half and double stack multiplicatively, so holding both cancels back to normal speed. */
    static double speedMultiplier(boolean halfHeld, boolean doubleHeld) {
        double multiplier = 1.0;
        if (halfHeld) {
            multiplier *= 0.5;
        }
        if (doubleHeld) {
            multiplier *= 2.0;
        }
        return multiplier;
    }

    private static boolean isModifierDown(InputHandler input, int keyCode) {
        if (input.isKeyDown(keyCode)) {
            return true;
        }
        int mirrored = mirroredModifier(keyCode);
        return mirrored != GLFW.GLFW_KEY_UNKNOWN && input.isKeyDown(mirrored);
    }

    /** Left/right variants of a configured modifier key are interchangeable. */
    static int mirroredModifier(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_CONTROL -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case GLFW.GLFW_KEY_LEFT_ALT -> GLFW.GLFW_KEY_RIGHT_ALT;
            case GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_KEY_LEFT_ALT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    /**
     * @param nonRewindableTransitionPending true while the level is mid a
     *     special/bonus-stage/ending transition or a pending zone/act
     *     transition (see {@code GameLoop.isNonRewindableTransitionPending()}).
     *     Unlike {@link #handleRealtimeRewindInput(GameMode, boolean, InputHandler)}'s
     *     {@code rewindBlocked} parameter, this one intentionally does NOT
     *     also cover a pending fade completion: gameplay is deliberately NOT
     *     frozen during a completion-bearing fade, so this call site DOES run
     *     during such fades (with this flag false) and must keep recording
     *     rewind history through them. Only rewind ENGAGEMENT is blocked
     *     during those windows, via {@code GameLoop.isRewindBlocked()}.
     */
    public void recordExternalFrame(GameMode mode, boolean nonRewindableTransitionPending, InputHandler input) {
        if (!supportsCurrentRewindContext(mode) || nonRewindableTransitionPending || input == null || !enabled()) {
            activeInputHandler = null;
            clear();
            return;
        }
        activeInputHandler = input;
        if (rewinding) {
            return;
        }
        if (!ensureInstalled()) {
            return;
        }
        inputSource.discardAfter(rewindController.currentFrame());
        inputSource.appendFrame(input, config);
        if (rewindController.recordExternalStep()) {
            pruneOldHistory();
        }
    }

    public void markBoundary(RewindBoundary boundary) {
        if (boundary == null) {
            return;
        }
        switch (boundary) {
            case LEVEL_LOAD -> handleLevelLoadBoundary();
            case SEAMLESS_LEVEL_TRANSITION -> handleSeamlessLevelTransitionBoundary();
            case MODE_EXIT_TO_NON_REWINDABLE -> clear();
            case MODE_ENTER_REWINDABLE -> handleModeEnterRewindableBoundary();
        }
    }

    public void resetBufferAtCurrentFrame(GameMode mode) {
        if (!supportsCurrentRewindContext(mode)) {
            return;
        }
        markBoundary(RewindBoundary.SEAMLESS_LEVEL_TRANSITION);
    }

    public void renderHud(GameMode mode, PixelFontTextRenderer text) {
        if (!supportsCurrentRewindContext(mode) || text == null || !enabled()) {
            return;
        }
        hudOverlay.render(text);
    }

    /**
     * Modes in which held live rewind may structurally run. LEVEL is normal
     * gameplay; BONUS_STAGE covers the rewind-supported bonus stages
     * (Gumball/Pachinko), which run the same LevelFrameStep pipeline the
     * re-simulation stepper replays. SPECIAL_STAGE uses a dedicated stepper and
     * is additionally filtered by provider capability before activation.
     */
    private static boolean isRewindableMode(GameMode mode) {
        return mode == GameMode.LEVEL
                || mode == GameMode.BONUS_STAGE
                || mode == GameMode.SPECIAL_STAGE;
    }

    /** Current VHS rewind presentation intensity, 0..1. */
    public float effectIntensity() {
        return effectEnvelope.intensity();
    }

    /** Latched tape speed for the VHS rewind presentation, 0.25..4.0. */
    public float effectSpeed() {
        return effectEnvelope.speed();
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
        StepperKind requiredKind = requiredStepperKind();
        if (gameplayMode == installedGameplayMode
                && installedStepperKind == requiredKind
                && rewindController != null
                && inputSource != null) {
            // A caller already confirmed this rewind context and the feature
            // is enabled this frame, so live rewind could be used — arm PCM
            // recording (via the controller's own AudioManager collaborator,
            // not GameServices) so held rewind has history to play back.
            rewindController.setRewindHistoryArmed(true);
            return true;
        }
        inputSource = new LiveRewindInputSource();
        EngineStepper stepper = requiredKind == StepperKind.SPECIAL_STAGE
                ? new SpecialStageStepper(inputSource, () -> activeInputHandler, specialStageProviderSupplier)
                : new LiveRewindStepper(
                        inputSource,
                        () -> activeInputHandler,
                        () -> LevelFrameContext.from(gameplayMode));
        gameplayMode.installPlaybackController(
                inputSource,
                stepper,
                KEYFRAME_INTERVAL);
        rewindController = gameplayMode.getRewindController();
        if (rewindController != null
                && config.getBoolean(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT)) {
            rewindController.setDeterminismAuditor(new RewindDeterminismAuditor());
        }
        installedGameplayMode = gameplayMode;
        installedStepperKind = requiredKind;
        speedController = RewindSpeedController.fromConfig(config);
        rewinding = false;
        if (rewindController != null) {
            rewindController.setRewindHistoryArmed(true);
        }
        return rewindController != null;
    }

    private void handleLevelLoadBoundary() {
        if (!enabled()) {
            clear();
            return;
        }
        if (!ensureInstalled()) {
            return;
        }
        boolean wasRewinding = rewinding;
        inputSource.resetToFrameZero();
        rewindController.resetToFrameZero();
        if (wasRewinding) {
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);
        }
        // The raw PCM rewind-history ring is a fixed-duration buffer that is
        // not gated by the logical rewind reset above: without this, a held
        // rewind that reaches the new level's frame-zero floor can keep
        // draining the ring backward into audio recorded before this level
        // loaded. ensureInstalled() above returned true, so rewindController
        // is non-null here.
        rewindController.clearPcmHistory();
        rewinding = false;
        effectEnvelope.reset();
    }

    private void handleSeamlessLevelTransitionBoundary() {
        if (!enabled()) {
            clear();
            return;
        }
        if (!ensureInstalled()) {
            return;
        }
        boolean wasRewinding = rewinding;
        int frame = rewindController.currentFrame();
        inputSource.retainOnlyFrame(frame);
        rewindController.resetBufferAtCurrentFrame();
        if (wasRewinding) {
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);
        }
        rewinding = false;
        effectEnvelope.reset();
    }

    private void handleModeEnterRewindableBoundary() {
        if (!enabled() || !supportsCurrentRewindContext(modeSupplier.get())) {
            clear();
            return;
        }
        ensureInstalled();
    }

    private boolean supportsCurrentRewindContext(GameMode mode) {
        if (mode == GameMode.SPECIAL_STAGE) {
            SpecialStageProvider provider = specialStageProviderSupplier.get();
            return provider != null && provider.supportsRewind();
        }
        return isRewindableMode(mode);
    }

    private StepperKind requiredStepperKind() {
        GameMode mode = modeSupplier.get();
        if (mode == GameMode.SPECIAL_STAGE) {
            SpecialStageProvider provider = specialStageProviderSupplier.get();
            if (provider != null && provider.supportsRewind()) {
                return StepperKind.SPECIAL_STAGE;
            }
        }
        return StepperKind.LEVEL_FRAME;
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

    private void pruneOldHistory() {
        int historySeconds = Math.max(1, config.getInt(SonicConfiguration.REWIND_HISTORY_SECONDS));
        int retainedFrames = historySeconds * 60;
        int earliestFrame = rewindController.pruneHistoryToRetainFrames(retainedFrames);
        inputSource.discardBefore(earliestFrame);
    }

    private void cleanupAudioAfterRealtimeRewind(AudioPresentationPolicy policy) {
        if (rewindController == null) {
            return;
        }
        // Land the single deferred logical restore at the committed frame
        // before the presentation cleanup acts on backend logical state.
        rewindController.commitDeferredAudioRestore();
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
        if (rewindController != null) {
            // Disarm through the outgoing controller's own AudioManager
            // collaborator (still valid even though its owning session is
            // gone) rather than GameServices, matching setRewindHistoryArmed
            // above.
            rewindController.setRewindHistoryArmed(false);
        }
        installedGameplayMode = null;
        installedStepperKind = null;
        inputSource = null;
        rewindController = null;
        activeInputHandler = null;
        speedController.reset();
        speedController = RewindSpeedController.disabled();
        rewinding = false;
        effectEnvelope.reset();
    }
}
