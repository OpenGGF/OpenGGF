package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.internal.EndingEntryPresentation;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.save.SaveReason;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelEndingUpdateAccess;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.logging.Logger;

/** Owns the ending provider, entry gate, phase execution and ending-only cleanup. */
final class GameLoopEndingSession {
    interface Host {
        LevelManager level();
        SpriteManager sprites();
        Camera camera();
        WaterSystem water();
        FadeManager fade();
        GameModule module();
        InputHandler input();
        String mainCharacterCode();
        GameMode currentMode();
        GameplayModeContext boundGameplayContext();
        GameplayModeContext resolvedGameplayContext();
        void setGameMode(GameMode mode);
        void requestSessionSave(SaveReason reason);
        TitleScreenProvider titleScreen();
        void returnToTitleScreen();
    }

    private static final Logger LOGGER = Logger.getLogger(GameLoopEndingSession.class.getName());
    private final Host host;
    private final AudioManager audioManager;
    private EndingProvider endingProvider;
    // Also owned by standalone completion; both routes use the same gameplay/rewind gate.
    private boolean endingTransitionPending;

    GameLoopEndingSession(Host host, AudioManager audioManager) {
        this.host = host;
        this.audioManager = audioManager;
    }

    EndingProvider provider() { return endingProvider; }
    boolean isTransitionPending() { return endingTransitionPending; }
    void setTransitionPending(boolean pending) { endingTransitionPending = pending; }
    void retireForSessionReset() {
        endingProvider = null;
        endingTransitionPending = false;
    }

    void serviceNativeEndingVBlank(PlcLifecycleFrame frame) {
        if (!(endingProvider instanceof com.openggf.game.internal.NativeEndingVBlank nativeEnding)) return;
        switch (host.currentMode()) {
            case CREDITS_TEXT, CREDITS_DEMO, TRY_AGAIN_END, ENDING_CUTSCENE ->
                frame.serviceAlternateNativeVBlankClock(() -> nativeEnding.serviceEndingVBlank(frame.ownerPhase()));
            default -> { }
        }
    }

    void runFrame(PlcLifecycleFrame frame) {
        if (endingProvider instanceof com.openggf.game.internal.NativeEndingVBlank
                && frame.isOwnedBy(com.openggf.game.resources.PlcLifecyclePhase.PALETTE_FADE)) {
            // The palette routine owns this entire counted interrupt, including
            // its final wait. Its caller can only arm the next loop's wait.
            serviceNativeEndingVBlank(frame);
            // Returning from the last palette wait may prepare the next load
            // on this loop tail, without executing its next gameplay body.
            if (endingProvider.hasDemoLoadRequest()) {
                endingProvider.consumeDemoLoadRequest();
                loadEndingDemoZone();
            }
            return;
        }
        GameLoopPlcLifecycle.runPhase(frame, GameLoopPlcLifecycle.endingPhase(endingProvider), () -> {
            if (endingProvider instanceof com.openggf.game.internal.NativeEndingVBlank) {
                LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(host.resolvedGameplayContext()), frame);
                serviceNativeEndingVBlank(frame);
            }
            updateEnding();
        });
    }

    /**
     * Starts the fade-to-white transition to enter the ending sequence.
     * Called when the level requests credits (e.g., after final boss defeat).
     */
    void startEndingFade() {
        LOGGER.info("Starting fade-to-white for ending sequence");
        EndingProvider provider = host.module().getEndingProvider();
        if (provider != null) {
            provider.saveReasonOnEndingStart().ifPresent(host::requestSessionSave);
        }
        endingTransitionPending = true;
        if (provider instanceof EndingEntryPresentation presentation && presentation.ownsEntryPresentation()) {
            doEnterEnding();
            return;
        }
        audioManager.fadeOutMusic();
        GameLoopPlcLifecycle.startToWhite(host.resolvedGameplayContext(), host.fade(), this::doEnterEnding);
    }

    /**
     * Actually enters the ending sequence after fade-to-white completes.
     * Initializes the EndingProvider from the current GameModule.
     */
    private void doEnterEnding() {
        endingProvider = host.module().getEndingProvider();
        if (endingProvider == null) {
            endingTransitionPending = false;
            LOGGER.warning("No EndingProvider available, returning to title screen");
            host.setGameMode(GameMode.TITLE_SCREEN);
            TitleScreenProvider titleScreen = host.titleScreen();
            if (titleScreen != null) {
                titleScreen.initialize();
            }
            return;
        }

        if (endingProvider instanceof com.openggf.game.resources.NativeFadeLifecycleAware aware) {
            aware.bindNativeFadeLifecycle(host.boundGameplayContext().plcFrameLifecycle());
        }
        endingProvider.initialize();
        endingTransitionPending = false;
        host.setGameMode(gameModeForPhase(endingProvider.getCurrentPhase()));

        // Reveal the ending scene. Only start our own fade if the provider didn't
        // already start one during initialize() (e.g., S1 credits starts its own
        // fade-from-black with a state-advancing callback that must not be overwritten).
        FadeManager localFade = host.fade();
        if (!(endingProvider instanceof EndingEntryPresentation presentation && presentation.ownsEntryPresentation())
                && (!localFade.isActive() || localFade.getState() == FadeManager.FadeState.HOLD_WHITE)) {
            // Screen is currently white from startEndingFade's fade-to-white.
            // ROM: Normal_palette starts all $0EEE (white), display enabled.
            GameLoopPlcLifecycle.startFromWhite(host.resolvedGameplayContext(), localFade, null);
        }

        advanceBackgroundForCurrentScene();
        LOGGER.info("Entered ending sequence, phase=" + endingProvider.getCurrentPhase());
    }

    /**
     * Unified update method for all ending-related game modes.
     * Dispatches to phase-specific logic based on the current EndingPhase.
     */
    private void updateEnding() {
        if (endingProvider == null) {
            return;
        }

        EndingPhase phase = endingProvider.getCurrentPhase();

        switch (phase) {
            case CREDITS_TEXT -> updateEndingCreditsText();
            case CREDITS_DEMO -> updateEndingCreditsDemo();
            case POST_CREDITS -> updateEndingPostCredits();
            case CUTSCENE -> updateEndingCutscene();
            case FINISHED -> exitEndingToTitleScreen();
        }

        // Sync GameMode to the current phase (in case provider changed phase internally)
        if (endingProvider != null && !endingProvider.isComplete()) {
            EndingPhase newPhase = endingProvider.getCurrentPhase();
            GameMode targetMode = gameModeForPhase(newPhase);
            if (targetMode != host.currentMode()) {
                host.setGameMode(targetMode);
            }
        }
        advanceBackgroundForCurrentScene();
    }

    /** Publish logical scroll once, after the provider's final scene decision, even without a draw. */
    private void advanceBackgroundForCurrentScene() {
        if (endingProvider != null && host.currentMode() == GameMode.ENDING_CUTSCENE
                && endingProvider.needsLevelBackground()) {
            LevelEndingUpdateAccess.advanceBackground(host.level(), endingProvider.getBackgroundVscroll());
        }
    }

    /**
     * CREDITS_TEXT phase: update provider, check for demo load requests and completion.
     */
    private void updateEndingCreditsText() {
        endingProvider.update();

        // Check if the provider wants to load a demo zone
        if (endingProvider.hasDemoLoadRequest()) {
            endingProvider.consumeDemoLoadRequest();
            loadEndingDemoZone();
        }

        // Check if ending is complete (e.g., after "PRESENTED BY SEGA")
        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * CREDITS_DEMO phase: update provider, run level physics with demo input.
     */
    private void updateEndingCreditsDemo() {
        boolean scrollFrozenAtEntry = endingProvider.isScrollFrozen()
                || endingProvider.plcLifecyclePhaseOverride().orElse(null)
                    == com.openggf.game.resources.PlcLifecyclePhase.CREDITS_DEMO_FADE;
        boolean shouldAdvanceFrozenScene = endingProvider.shouldAdvanceFrozenDemoScene();
        endingProvider.update();
        shouldAdvanceFrozenScene = shouldAdvanceFrozenScene || endingProvider.shouldAdvanceFrozenDemoScene();

        // Apply demo input to player
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            if (!endingProvider.shouldRunDemoGameplay()) {
                player.setForcedInputMask(0);
            } else {
            // ROM does NOT set obj_control during demos — it writes demo input
            // directly to jpadhold1/jpadpress1 (MoveSonicInDemo.asm).
            // Do NOT use controlLocked here: PlayableSpriteMovement re-reads it
            // and would zero out left/right/jump, overriding the forced input.
                player.setForcedInputMask(endingProvider.getDemoInputMask());
            }
        }

        if (!endingProvider.shouldRunDemoGameplay()) {
            if (shouldAdvanceFrozenScene) {
                host.level().updateObjectPositionsWithoutTouches();
                host.level().updateEndingDemoScene();
            }
            // Level_Delay / PalFadeIn_Alt do not dispatch Sonic_Animate.

            if (endingProvider.hasTextReturnRequest()) {
                endingProvider.consumeTextReturnRequest();
                returnFromEndingDemo();
            }
            if (endingProvider.isComplete()) {
                exitEndingToTitleScreen();
            }
            return;
        }

        com.openggf.sprites.managers.SpriteManagerInternalAccess.runCreditsDemoGameplay(
                host.sprites(), host.module(), sprite, () -> runEndingCreditsGameplay(scrollFrozenAtEntry));
    }

    private void runEndingCreditsGameplay(boolean scrollFrozen) {
        if (!scrollFrozen) host.level().advanceLevelFrameCounter();
        // Run level physics — follows LevelFrameStep canonical order (steps 1-4),
        // but steps 5-6 are conditional on scroll-freeze state during ending fadeout.
        host.sprites().publishHeldInputForLevelEvents(host.input());
        // Level_FDLoop (sonic.asm:3073-3090) retains ExecuteObjects and
        // ObjPosLoad, but omits LZWaterFeatures, deformation/events and the
        // level-loop oscillator. Preserve the last water-slide controls.
        if (!scrollFrozen) {
            host.level().updateZoneFeaturesPrePhysics();
            if (host.level().advanceWaterLevelBeforePlayerPhysics())
                host.level().advanceDynamicWaterLevel();
        }
        host.sprites().update(host.input());
        host.level().updateObjectPositionsPostPhysicsWithoutTouches(null, !scrollFrozen);
        // Camera/scroll in ROM order (DeformLayers (REV01).asm:16-18): the camera
        // move/clamp (ScrollVertical) runs BEFORE the zone event handler, and the
        // boundary easing (DynamicLevelEvents tail) runs AFTER it. Mirrors
        // LevelFrameStep steps 4a/4b/4c. Camera/scroll only if not frozen (during
        // fadeout, scroll freezes).
        if (!scrollFrozen) {
            host.camera().updatePosition();
            host.camera().captureRenderCopy();
        }
        LevelEventProvider levelEvents = host.module().getLevelEventProvider();
        if (!scrollFrozen && levelEvents != null) {
            levelEvents.update();
        }
        if (!scrollFrozen) {
            host.camera().updateBoundaryEasing();
            if (levelEvents != null) {
                levelEvents.updateAfterCameraBoundaryEasing();
            }
            host.level().postCameraObjectPlacementSync();
            host.level().update();
            host.level().refreshObjectPostCameraRenderState();
        }

        // Check if returning to text phase
        if (endingProvider.hasTextReturnRequest()) {
            endingProvider.consumeTextReturnRequest();
            returnFromEndingDemo();
        }

        // Check if ending is complete
        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * POST_CREDITS phase: update the post-credits screen (e.g., TRY AGAIN / END).
     * Delegates to the provider's {@code updatePostCredits} / {@code consumePostCreditsExitRequest}
     * methods so each game handles its own input-driven exit logic.
     */
    private void updateEndingPostCredits() {
        endingProvider.updatePostCredits(host.input());
        if (endingProvider.consumePostCreditsExitRequest()) {
            exitEndingToTitleScreen();
            return;
        }
        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * CUTSCENE phase: update provider (e.g., S2 Tornado flyby).
     */
    private void updateEndingCutscene() {
        endingProvider.update();

        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * Loads a demo zone for ending credits and transitions to CREDITS_DEMO mode.
     * Reads zone/act/position from the EndingProvider.
     */
    private void loadEndingDemoZone() {
        int zone = endingProvider.getDemoZone();
        int act = endingProvider.getDemoAct();
        int startX = endingProvider.getDemoStartX();
        int startY = endingProvider.getDemoStartY();

        if (endingProvider instanceof com.openggf.game.internal.NativeEndingDemoAssembly nativeDemo)
            nativeDemo.beginDemoLoad();
        var profile = host.module().getLevelInitProfile();
        try (var scope = profile instanceof com.openggf.game.internal.NativeEndingDemoLoadProfile nativeDemo
                ? nativeDemo.beginEndingDemoLoad(endingProvider)
                : com.openggf.game.internal.NativeLevelLoadCallerScope.NONE) {
            // Suppress zone music — credits music should continue playing
            host.level().setSuppressNextMusicChange(true);
            host.level().loadZoneAndAct(zone, act);
            // Consume the title card request since we don't want a title card
            host.level().consumeTitleCardRequest();
        } catch (IOException e) {
            com.openggf.level.DeferredLevelLoadException.rethrowCallbackAbort(e);
            LOGGER.severe("Failed to load ending demo zone " + zone + " act " + act + ": " + e.getMessage());
            return;
        }

        if (com.openggf.level.NativeLevelLoadAccess.isPending(host.level())) {
            com.openggf.level.NativeLevelLoadAccess.afterCompletion(host.level(),
                    () -> finishEndingDemoZone(zone, act, startX, startY));
            return;
        }
        finishEndingDemoZone(zone, act, startX, startY);
    }

    private void finishEndingDemoZone(int zone, int act, int startX, int startY) {
        host.level().consumeTitleCardRequest();
        // Suppress HUD during credits demos (ROM: HUD is never drawn during credits)
        host.level().setForceHudSuppressed(true);

        EndingDemoLevelAssembly.prepareIfUnowned(host, endingProvider, startX, startY);

        // Suppress player keyboard input — demo input comes from forcedInputMask only
        host.sprites().setInputSuppressed(true);

        // Switch to CREDITS_DEMO mode
        host.setGameMode(GameMode.CREDITS_DEMO);

        // Notify provider that zone is loaded
        endingProvider.onDemoZoneLoaded();

        LOGGER.info("Loaded ending demo zone " + zone + " act " + act +
                " at (" + startX + ", " + startY + ")");
    }

    /**
     * Returns from CREDITS_DEMO to CREDITS_TEXT for the next credit.
     */
    private void returnFromEndingDemo() {
        // Restore player keyboard input and clear HUD suppression
        host.sprites().setInputSuppressed(false);
        host.level().setForceHudSuppressed(false);
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
        }

        host.setGameMode(GameMode.CREDITS_TEXT);
        endingProvider.onReturnToText();

        LOGGER.info("Returned from ending demo to credits text");
    }

    /**
     * Exits the ending sequence and returns to the title screen.
     */
    private void exitEndingToTitleScreen() {
        LOGGER.info("Ending sequence complete, returning to title screen");
        startEndingReturn(host.sprites(), host.level(), host.mainCharacterCode(),
                audioManager, host.fade(), host.resolvedGameplayContext(), this::doExitEndingToTitleScreen);
    }

    /**
     * Actually transitions to the title screen after fade completes.
     */
    private void doExitEndingToTitleScreen() {
        endingProvider = null;
        host.returnToTitleScreen();
        LOGGER.info("Ending -> Title Screen");
    }

    /**
     * Maps an {@link EndingPhase} to the corresponding {@link GameMode}.
     */
    private GameMode gameModeForPhase(EndingPhase phase) {
        return switch (phase) {
            case CUTSCENE -> GameMode.ENDING_CUTSCENE;
            case CREDITS_TEXT -> GameMode.CREDITS_TEXT;
            case CREDITS_DEMO -> GameMode.CREDITS_DEMO;
            case POST_CREDITS -> GameMode.TRY_AGAIN_END;
            case FINISHED -> GameMode.TITLE_SCREEN;
        };
    }

    /** Retains the captured-argument boundary used by the ending return owner. */
    private static void startEndingReturn(SpriteManager spriteManager, LevelManager levelManager, String mainCode,
            AudioManager audioManager, FadeManager fadeManager, GameplayModeContext context, Runnable onBlack) {
        EndingReturnTransition.start(spriteManager, levelManager, mainCode, audioManager, fadeManager, context, onBlack);
    }

}
