package com.openggf.game.rewind;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.BonusStageProvider;
import com.openggf.audio.AudioManager;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runtime glue for optional held-key rewind during ordinary live level play.
 */
public final class LiveRewindManager {

    private static final int KEYFRAME_INTERVAL = 60;
    // Bound a cold backward step to nine replay ticks without multiplying costly
    // audio snapshots. Gameplay snapshots remain bounded by historySeconds.
    private static final int GAMEPLAY_CHECKPOINT_INTERVAL = 10;

    private final SonicConfigurationService config;
    private final Supplier<GameMode> modeSupplier;
    private final AudioManager audio;
    private GameplayModeContext boundOwner;
    private GameMode boundMode = GameMode.LEVEL;
    private boolean parked;
    private SpecialStageProvider installedSpecialProvider;
    private FadeManager installedFade;
    private Object installationIdentity = new Object();
    private boolean retireAfterRelease;
    private final java.util.ArrayDeque<PendingBoundary> deferredBoundaries = new java.util.ArrayDeque<>();

    private record PendingBoundary(Object installation, RewindBoundary boundary) { }
    private final LiveRewindHudOverlay hudOverlay;

    private GameplayModeContext installedGameplayMode;
    private StepperKind installedStepperKind;
    private LiveRewindInputSource inputSource;
    private RewindController rewindController;
    private RewindSpeedController speedController = RewindSpeedController.disabled();
    private InputHandler activeInputHandler;
    private boolean rewinding;
    // The current rewind is a module's ScriptedRewind rather than the held key.
    private boolean scriptedRewinding;
    private boolean releasePending;
    private AudioPresentationPolicy pendingReleasePolicy;
    private RewindBoundary pendingReleaseBoundary;
    private final RewindEffectEnvelope effectEnvelope = new RewindEffectEnvelope();

    public LiveRewindManager(SonicConfigurationService config, AudioManager audio) {
        this(config, audio, null);
    }

    public LiveRewindManager(SonicConfigurationService config, AudioManager audio,
                             Supplier<GameMode> modeSupplier) {
        this.config = Objects.requireNonNull(config, "config");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.modeSupplier = modeSupplier;
        this.hudOverlay = new LiveRewindHudOverlay(this::statusLabel);
    }

    /** Lifecycle facts from the host binding boundary; parking preserves the installed history. */
    public void bindOwner(GameplayModeContext owner, boolean parked, GameMode mode) {
        this.parked = parked;
        this.boundOwner = parked && owner == null ? installedGameplayMode : owner;
        this.boundMode = Objects.requireNonNull(mode, "mode");
        if (rewindController != null && rewindController.isApplyingHistory()) return;
        reconcileBinding();
        drainBoundaries();
        if (!parked && !releasePending && ready(boundOwner)) {
            RewindContext context = rewindContextFromSupplier();
            if (context.supported() && enabled(scriptedRewind(context))) {
                // Binding precedes the loop's entry admission. A stopped source
                // world cannot supply a floor; explicit boundaries and completed
                // ticks retain their own admission, as do registered special stages.
                if (context.stepperKind() == StepperKind.LEVEL_FRAME
                        && boundOwner.getLevelManager().isLevelInactiveForTransition()) return;
                ensureInstalled(context);
            }
        }
    }

    /** A reporter belongs to one installation, including reinstallations of the same owner. */
    public RewindBoundaryReporter boundaryReporterFor(GameplayModeContext owner) {
        Object identity = installationIdentity;
        return boundary -> {
            if (owner != installedGameplayMode || identity != installationIdentity
                    || !ready(owner) || owner != boundOwner || parked
                    || owner.getRewindController() != rewindController
                    || (boundary != RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE
                        && installedStepperKind == StepperKind.SPECIAL_STAGE
                        && installedSpecialProvider != owner.getRegisteredSpecialStageProvider())) return;
            if (rewindController != null && rewindController.isApplyingHistory()) {
                deferredBoundaries.addLast(new PendingBoundary(identity, boundary));
            } else {
                markBoundary(boundary);
            }
        };
    }

    private static boolean ready(GameplayModeContext owner) {
        return owner != null && owner.isGameplayRuntimeReady();
    }

    private void reconcileBinding() {
        if (rewindController != null && rewindController.isApplyingHistory()) return;
        if (installedGameplayMode == null) return;
        boolean replaced = installedGameplayMode != boundOwner || !ready(installedGameplayMode)
                || installedGameplayMode.getRewindController() != rewindController
                || (installedStepperKind == StepperKind.SPECIAL_STAGE
                    && installedSpecialProvider != installedGameplayMode.getRegisteredSpecialStageProvider());
        if (replaced) {
            retireAfterRelease = true;
            clear();
        } else if (parked) {
            rewindController.setRewindHistoryArmed(false);
            com.openggf.game.session.WorldSessionPolicyAccess.setHistoryRecording(
                    installedGameplayMode.getWorldSession(), false);
            if (rewinding && !releasePending) {
                cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX);
                if (!releasePending) rewinding = false;
            }
        }
    }

    private void drainBoundaries() {
        if (rewindController != null && rewindController.isApplyingHistory()) return;
        while (!deferredBoundaries.isEmpty() && !releasePending) {
            PendingBoundary pending = deferredBoundaries.removeFirst();
            if (pending.installation() == installationIdentity && installedGameplayMode == boundOwner
                    && ready(boundOwner) && !parked) markBoundary(pending.boundary());
        }
    }

    private boolean replayAdmitted(Object identity) {
        return identity == installationIdentity && !parked && !releasePending && !retireAfterRelease
                && ready(installedGameplayMode) && installedGameplayMode == boundOwner
                && installedGameplayMode.getRewindController() == rewindController
                && !rewindController.isApplyingHistory()
                && activeInputHandler != null && deferredBoundaries.isEmpty()
                && rewindContextFromSupplier().stepperKind() == installedStepperKind
                && rewindContextFromSupplier().supported()
                && (installedStepperKind != StepperKind.SPECIAL_STAGE
                    || installedSpecialProvider == installedGameplayMode.getRegisteredSpecialStageProvider());
    }

    private enum StepperKind {
        LEVEL_FRAME,
        SPECIAL_STAGE
    }

    private record RewindContext(boolean supported, StepperKind stepperKind) {
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
        reconcileBinding();
        drainBoundaries();
        if (releasePending) return retryPendingRelease();
        if (parked) return false;
        RewindContext context = rewindContextForPublicEntry(mode);
        ScriptedRewind script = scriptedRewind(context);
        if (!context.supported() || rewindBlocked || input == null || !enabled(script)) {
            return !clear();
        }
        if (!ensureInstalled(context)) {
            effectEnvelope.frameInactive();
            return releasePending;
        }
        if (releasePending) {
            return retryPendingRelease();
        }
        activeInputHandler = input;
        // A module's scripted rewind runs the held-key presentation at its own pace.
        boolean scripted = script != null && script.requested();
        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        if (scripted || (keyEnabled() && input.isKeyDown(rewindKey))) {
            if (!rewinding) {
                audio.beginReverseAudioPresentation();
                beginReverseFadePresentation();
            }
            rewinding = true;
            scriptedRewinding = scripted;
            int steps;
            if (scripted) {
                speedController.reset();
                steps = Math.max(0, script.stepsThisFrame());
            } else {
                speedController.setHeldSpeedMultiplier(resolveHeldSpeedMultiplier(input));
                steps = speedController.stepsWhileHeld();
            }
            double speed = scripted ? Math.max(1, steps) : speedController.currentSpeed();
            audio.setReversePlaybackRate(speed);
            if (scripted) {
                return stepScripted(script, steps, speed);
            }
            stepBackward(steps);
            effectEnvelope.frameActive(speed);
            return true;
        }
        int coastSteps = speedController.stepsAfterRelease();
        if (rewinding && coastSteps > 0) {
            if (stepBackward(coastSteps) > 0) {
                audio.setReversePlaybackRate(speedController.currentSpeed());
                effectEnvelope.frameActive(speedController.currentSpeed());
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
            if (!cleanupPresentationAfterRealtimeRewind(
                    AudioPresentationPolicy.STOP_TRANSIENT_SFX)) {
                return true;
            }
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
        recordExternalFrame(mode, nonRewindableTransitionPending, input, false);
    }

    /**
     * Records a completed host frame and optionally makes it the first frame of
     * a new seamless-transition rewind segment.
     *
     * <p>The boundary is applied after recording so an act reload performed
     * inside {@code LevelFrameStep} is captured only after the rest of that
     * logical frame has completed. Capturing directly from the reload call
     * would expose a mid-frame snapshot to rewind replay.
     */
    public void recordExternalFrame(
            GameMode mode,
            boolean nonRewindableTransitionPending,
            InputHandler input,
            boolean seamlessTransitionCompleted) {
        recordExternalFrame(boundOwner, mode,
                mode == GameMode.SPECIAL_STAGE && boundOwner != null
                        ? boundOwner.getRegisteredSpecialStageProvider() : null,
                nonRewindableTransitionPending, input, seamlessTransitionCompleted);
    }

    /** Records only into the history of the owner/mode/provider that actually executed the tick. */
    public void recordExternalFrame(GameplayModeContext executedOwner, GameMode mode,
            SpecialStageProvider executedProvider, boolean nonRewindableTransitionPending,
            InputHandler input, boolean seamlessTransitionCompleted) {
        recordExternalFrame(executedOwner, mode, executedProvider,
                executedOwner == null ? null : executedOwner.getActiveBonusStageProvider(),
                nonRewindableTransitionPending, input, seamlessTransitionCompleted);
    }

    public void recordExternalFrame(GameplayModeContext executedOwner, GameMode mode,
            SpecialStageProvider executedProvider, BonusStageProvider executedBonusProvider,
            boolean nonRewindableTransitionPending, InputHandler input, boolean seamlessTransitionCompleted) {
        reconcileBinding();
        drainBoundaries();
        if (releasePending || parked || executedOwner != boundOwner || !ready(executedOwner)
                || mode != currentMode()
                || (mode == GameMode.BONUS_STAGE
                    && executedBonusProvider != executedOwner.getActiveBonusStageProvider())
                || (mode == GameMode.SPECIAL_STAGE
                    && executedProvider != executedOwner.getRegisteredSpecialStageProvider())) return;
        RewindContext context = rewindContextForPublicEntry(mode);
        if (!context.supported() || nonRewindableTransitionPending || input == null
                || !enabled(scriptedRewind(context))) {
            clear();
            return;
        }
        if (rewinding) {
            return;
        }
        if (!ensureInstalled(context)) {
            return;
        }
        activeInputHandler = input;
        if (!replayAdmitted(installationIdentity)) return;
        // The host still owns its input edges and pacing here. PLC publication
        // completes later, after InputHandler.update() has consumed those edges.
        var capturedInput = LiveRewindInputSource.captureFrame(input, config);
        var pacing = LiveRewindPacingFrame.capture(mode, installedGameplayMode,
                mode == GameMode.SPECIAL_STAGE ? installedSpecialProvider : null, input);
        Object identity = installationIdentity;
        RewindController controller = rewindController;
        LiveRewindInputSource recordedInputs = inputSource;
        var level = executedOwner.getLevelManager().getCurrentLevel();
        executedOwner.plcFrameLifecycle().afterConsumedIteration(() -> {
            if (!replayAdmitted(identity) || rewindController != controller || inputSource != recordedInputs
                    || executedOwner.getLevelManager().getCurrentLevel() != level || mode != currentMode()
                    || (mode == GameMode.BONUS_STAGE
                        && executedBonusProvider != executedOwner.getActiveBonusStageProvider())
                    || (mode == GameMode.SPECIAL_STAGE
                        && executedProvider != executedOwner.getRegisteredSpecialStageProvider())) return;
            recordedInputs.discardAfter(controller.currentFrame());
            recordedInputs.appendFrame(capturedInput, pacing);
            if (controller.recordExternalStep()) pruneOldHistory();
            drainBoundaries();
            if (seamlessTransitionCompleted && rewindController == controller
                    && identity == installationIdentity) {
                handleSeamlessLevelTransitionBoundary(context);
            }
        });
    }

    public void markBoundary(RewindBoundary boundary) {
        if (boundary == null) return;
        if (rewindController != null && rewindController.isApplyingHistory()) {
            deferredBoundaries.addLast(new PendingBoundary(installationIdentity, boundary));
            return;
        }
        if (releasePending) {
            if (!retireAfterRelease && boundary == pendingReleaseBoundary) retryPendingRelease();
            return;
        }
        switch (boundary) {
            case LEVEL_LOAD -> handleLevelLoadBoundary(rewindContextFromSupplier());
            case SEAMLESS_LEVEL_TRANSITION -> handleSeamlessLevelTransitionBoundary(rewindContextFromSupplier());
            case MODE_EXIT_TO_NON_REWINDABLE -> clear();
            case MODE_ENTER_REWINDABLE -> handleModeEnterRewindableBoundary();
        }
    }

    public void resetBufferAtCurrentFrame(GameMode mode) {
        RewindContext context = rewindContextForPublicEntry(mode);
        if (!context.supported()) {
            return;
        }
        handleSeamlessLevelTransitionBoundary(context);
    }

    public void renderHud(GameMode mode, PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth) {
        RewindContext context = rewindContextForPublicEntry(mode);
        if (!context.supported() || text == null || !enabled()) {
            return;
        }
        hudOverlay.render(text, graphics, viewportWidth);
    }

    public void renderHud(GameMode mode, PixelFontTextRenderer text) {
        renderHud(mode, text, null, 320);
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

    /** Host pacing must not multiply rewind or release-reconciliation steps. */
    public boolean isRewindingOrReleasing() { return rewinding || releasePending; }

    /** Current VHS rewind presentation intensity, 0..1. */
    public float effectIntensity() {
        return effectEnvelope.intensity();
    }

    /** Latched tape speed for the VHS rewind presentation, 0.25..4.0. */
    public float effectSpeed() {
        return effectEnvelope.speed();
    }

    private String statusLabel() {
        // A scripted rewind is the game's own presentation, without the debug frame counter.
        if (!enabled() || !rewinding || scriptedRewinding || rewindController == null) {
            return null;
        }
        return "REWIND " + rewindController.currentFrame();
    }

    private boolean ensureInstalled(RewindContext context) {
        GameplayModeContext gameplayMode = boundOwner;
        if (parked || releasePending || !ready(gameplayMode)) return false;
        StepperKind requiredKind = context.stepperKind();
        if (gameplayMode == installedGameplayMode
                && installedStepperKind == requiredKind
                && rewindController != null
                && gameplayMode.getRewindController() == rewindController
                && (requiredKind != StepperKind.SPECIAL_STAGE
                    || installedSpecialProvider == gameplayMode.getRegisteredSpecialStageProvider())
                && inputSource != null) {
            // A caller already confirmed this rewind context and the feature
            // is enabled this frame, so live rewind could be used — arm PCM
            // recording (via the controller's own AudioManager collaborator,
            // not GameServices) so held rewind has history to play back.
            rewindController.setRewindHistoryArmed(true);
            com.openggf.game.session.WorldSessionPolicyAccess.setHistoryRecording(gameplayMode.getWorldSession(),true);
            return true;
        }
        if (installedGameplayMode != null && !clear()) return false;
        inputSource = new LiveRewindInputSource();
        EngineStepper stepper = requiredKind == StepperKind.SPECIAL_STAGE
                ? new SpecialStageStepper(
                        inputSource, () -> activeInputHandler,
                        gameplayMode.getRegisteredSpecialStageProvider(), gameplayMode)
                : new LiveRewindStepper(
                        inputSource,
                        () -> activeInputHandler,
                        gameplayMode);
        gameplayMode.installPlaybackController(
                inputSource,
                stepper,
                KEYFRAME_INTERVAL);
        rewindController = gameplayMode.getRewindController();
        if (rewindController != null) {
            rewindController.setGameplayCheckpointInterval(GAMEPLAY_CHECKPOINT_INTERVAL);
        }
        if (rewindController != null
                && config.getBoolean(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT)) {
            rewindController.setDeterminismAuditor(new RewindDeterminismAuditor());
        }
        installedGameplayMode = gameplayMode;
        installedStepperKind = requiredKind;
        installedSpecialProvider = requiredKind == StepperKind.SPECIAL_STAGE
                ? gameplayMode.getRegisteredSpecialStageProvider() : null;
        installedFade = gameplayMode.getFadeManager();
        installationIdentity = new Object();
        Object identity = installationIdentity;
        rewindController.setReplayAdmission(() -> replayAdmitted(identity));
        gameplayMode.setRewindBoundaryReporter(boundaryReporterFor(gameplayMode));
        speedController = RewindSpeedController.fromConfig(config);
        rewinding = false;
        if (rewindController != null) {
            rewindController.setRewindHistoryArmed(true);
            com.openggf.game.session.WorldSessionPolicyAccess.setHistoryRecording(gameplayMode.getWorldSession(),true);
        }
        return rewindController != null;
    }

    private void handleLevelLoadBoundary(RewindContext context) {
        if (!enabled() || !context.supported()) {
            clear();
            return;
        }
        if (!ensureInstalled(context)) {
            return;
        }
        boolean wasRewinding = rewinding;
        if (wasRewinding && !releaseForBoundary(
                RewindBoundary.LEVEL_LOAD,
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC)) {
            return;
        }
        GameplayModeContext owner = installedGameplayMode;
        Object identity = installationIdentity;
        RewindController controller = rewindController;
        var level = owner.getLevelManager().getCurrentLevel();
        var special = installedSpecialProvider;
        var bonus = owner.getActiveBonusStageProvider();
        owner.plcFrameLifecycle().afterConsumedIteration(() -> {
            if (identity != installationIdentity || owner != installedGameplayMode || owner != boundOwner
                    || parked || releasePending || retireAfterRelease || !ready(owner)
                    || controller != rewindController || controller != owner.getRewindController()
                    || owner.getLevelManager().getCurrentLevel() != level
                    || (installedStepperKind == StepperKind.SPECIAL_STAGE
                        && special != owner.getRegisteredSpecialStageProvider())
                    || bonus != owner.getActiveBonusStageProvider()) return;
            inputSource.resetToFrameZero();
            controller.resetToFrameZero();
            // A fresh logical floor also retires PCM from the outgoing level;
            // held rewind must not drain audio older than that floor.
            controller.clearPcmHistory();
            rewinding = false;
            effectEnvelope.reset();
        });
    }

    private void handleSeamlessLevelTransitionBoundary(RewindContext context) {
        if (!enabled() || !context.supported()) {
            clear();
            return;
        }
        if (!ensureInstalled(context)) {
            return;
        }
        boolean wasRewinding = rewinding;
        if (wasRewinding && !releaseForBoundary(
                RewindBoundary.SEAMLESS_LEVEL_TRANSITION,
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC)) {
            return;
        }
        int frame = rewindController.currentFrame();
        inputSource.retainOnlyFrame(frame);
        rewindController.resetBufferAtCurrentFrame();
        rewinding = false;
        effectEnvelope.reset();
    }

    private void handleModeEnterRewindableBoundary() {
        RewindContext context = rewindContextFromSupplier();
        if (!enabled() || !context.supported()) {
            clear();
            return;
        }
        ensureInstalled(context);
    }

    private RewindContext rewindContextForPublicEntry(GameMode mode) {
        return rewindContext(modeSupplier == null ? mode : currentMode());
    }

    private RewindContext rewindContextFromSupplier() {
        return rewindContext(currentMode());
    }

    private GameMode currentMode() {
        return modeSupplier == null ? boundMode : modeSupplier.get();
    }

    private RewindContext rewindContext(GameMode mode) {
        if (mode != GameMode.SPECIAL_STAGE) {
            return new RewindContext(isRewindableMode(mode), StepperKind.LEVEL_FRAME);
        }
        SpecialStageProvider provider = boundOwner == null ? null : boundOwner.getRegisteredSpecialStageProvider();
        if (provider != null && provider.supportsRewind()) {
            return new RewindContext(true, StepperKind.SPECIAL_STAGE);
        }
        return new RewindContext(false, StepperKind.LEVEL_FRAME);
    }

    private int stepBackward(int steps) {
        int completed = 0;
        for (int i = 0; i < steps; i++) {
            if (!rewindController.stepBackward()) {
                break;
            }
            completed++;
            if (!deferredBoundaries.isEmpty()) break;
        }
        drainBoundaries();
        return completed;
    }

    /**
     * Takes one presentation frame's backward steps of a module's scripted rewind,
     * stopping at the first restored state that reaches its target or at the history
     * floor; then tells the script and runs the ordinary release.
     */
    private boolean stepScripted(ScriptedRewind script, int steps, double speed) {
        boolean reached = false;
        boolean floor = false;
        for (int i = 0; i < steps && !reached; i++) {
            if (!rewindController.stepBackward()) {
                floor = true;
                break;
            }
            reached = script.reachedTarget();
            if (!deferredBoundaries.isEmpty()) break;
        }
        if (!deferredBoundaries.isEmpty()) {
            drainBoundaries();
            return true;
        }
        if (!reached && !floor) {
            effectEnvelope.frameActive(speed);
            return true;
        }
        script.ended(reached);
        scriptedRewinding = false;
        if (!cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_TRANSIENT_SFX)) {
            return true;
        }
        rewinding = false;
        effectEnvelope.frameInactive();
        return true;
    }

    /** The module's scripted rewind in level play; never in bonus or special stages. */
    private ScriptedRewind scriptedRewind(RewindContext context) {
        if (!context.supported() || context.stepperKind() != StepperKind.LEVEL_FRAME
                || currentMode() != GameMode.LEVEL || boundOwner == null) {
            return null;
        }
        var module = boundOwner.getWorldSession().getGameModule();
        return module == null ? null : module.scriptedRewind();
    }

    private boolean enabled() {
        return keyEnabled() || scriptedRewind(rewindContextFromSupplier()) != null;
    }

    private boolean enabled(ScriptedRewind script) {
        return keyEnabled() || script != null;
    }

    /** The player's live rewind setting, which alone arms the held key. */
    private boolean keyEnabled() {
        return config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
    }

    private void pruneOldHistory() {
        int historySeconds = Math.max(1, config.getInt(SonicConfiguration.REWIND_HISTORY_SECONDS));
        int retainedFrames = historySeconds * 60;
        int earliestFrame = rewindController.pruneHistoryToRetainFrames(retainedFrames);
        inputSource.discardBefore(earliestFrame);
    }

    private boolean cleanupAudioAfterRealtimeRewind(
            AudioPresentationPolicy policy) {
        if (rewindController == null) {
            return audio.endReverseAudioPresentation();
        }
        // Land the single deferred logical restore at the committed frame
        // before the presentation cleanup acts on backend logical state.
        rewindController.commitDeferredAudioRestore();
        return rewindController.afterAudioRestore(policy);
    }

    private void beginReverseFadePresentation() {
        FadeManager fadeManager = installedFade;
        if (fadeManager != null) {
            fadeManager.beginReversePresentation();
        }
    }

    private boolean cleanupPresentationAfterRealtimeRewind(
            AudioPresentationPolicy policy) {
        if (!cleanupAudioAfterRealtimeRewind(policy)) {
            releasePending = true;
            pendingReleasePolicy = policy;
            return false;
        }
        FadeManager fadeManager = installedFade;
        if (fadeManager != null) {
            fadeManager.endReversePresentation();
        }
        releasePending = false;
        pendingReleasePolicy = null;
        return true;
    }

    /**
     * Retries a release retained by a mode/boundary path. Called from the
     * all-mode GameLoop owner before mode-specific rewind dispatch.
     *
     * @return true while the frame must remain consumed
     */
    public boolean retryPendingRelease() {
        if (!releasePending) {
            return false;
        }
        RewindBoundary boundary = pendingReleaseBoundary;
        if ((boundary == RewindBoundary.LEVEL_LOAD
                || boundary
                == RewindBoundary.SEAMLESS_LEVEL_TRANSITION)
                && rewindController != null
                && !rewindController.preparePostBoundaryAudioRelease()) {
            return true;
        }
        if (!cleanupPresentationAfterRealtimeRewind(pendingReleasePolicy)) {
            return true;
        }
        pendingReleaseBoundary = null;
        rewinding = false;
        if (retireAfterRelease || !ready(installedGameplayMode)
                || installedGameplayMode != boundOwner) {
            clear();
        } else if (boundary == RewindBoundary.LEVEL_LOAD) {
            handleLevelLoadBoundary(rewindContextFromSupplier());
        } else if (boundary == RewindBoundary.SEAMLESS_LEVEL_TRANSITION) {
            handleSeamlessLevelTransitionBoundary(rewindContextFromSupplier());
        } else if (boundary == RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE) {
            clear();
        } else {
            rewinding = false;
            speedController.reset();
            effectEnvelope.frameInactive();
        }
        return false;
    }

    private boolean releaseForBoundary(
            RewindBoundary boundary, AudioPresentationPolicy policy) {
        if (rewindController != null
                && !rewindController.preparePostBoundaryAudioRelease()) {
            releasePending = true;
            pendingReleasePolicy = policy;
            pendingReleaseBoundary = boundary;
            return false;
        }
        if (cleanupPresentationAfterRealtimeRewind(policy)) {
            return true;
        }
        pendingReleaseBoundary = boundary;
        return false;
    }

    private boolean clear() {
        if (releasePending) {
            retireAfterRelease = true;
            return false;
        }
        if(installedGameplayMode!=null)
            com.openggf.game.session.WorldSessionPolicyAccess.setHistoryRecording(installedGameplayMode.getWorldSession(),false);
        if (rewinding && rewindController != null) {
            if (!cleanupPresentationAfterRealtimeRewind(
                    AudioPresentationPolicy.STOP_ALL_PRESENTATION)) {
                pendingReleaseBoundary =
                        RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE;
                return false;
            }
        }
        if (rewindController != null) {
            // Disarm through the outgoing controller's own AudioManager
            // collaborator (still valid even though its owning session is
            // gone) rather than GameServices, matching setRewindHistoryArmed
            // above.
            rewindController.setRewindHistoryArmed(false);
        }
        if (installedGameplayMode != null) installedGameplayMode.uninstallPlaybackController(rewindController);
        installationIdentity = new Object();
        installedGameplayMode = null;
        installedStepperKind = null;
        installedSpecialProvider = null;
        installedFade = null;
        retireAfterRelease = false;
        deferredBoundaries.clear();
        inputSource = null;
        rewindController = null;
        activeInputHandler = null;
        speedController.reset();
        speedController = RewindSpeedController.disabled();
        rewinding = false;
        scriptedRewinding = false;
        releasePending = false;
        pendingReleasePolicy = null;
        pendingReleaseBoundary = null;
        effectEnvelope.reset();
        return true;
    }
}
