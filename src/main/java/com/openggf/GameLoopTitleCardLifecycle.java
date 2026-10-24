package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.InLevelTitleCardCoordinator;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.titlecard.TitleCardLoopTail;
import com.openggf.game.OscillationManager;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.function.Consumer;
import java.util.logging.Logger;

/** Loop-lifetime title presentation, retained provider binding and shared release ownership. */
final class GameLoopTitleCardLifecycle {
    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final Host host;
    private TitleCardProvider titleCardProvider;
    private PostTitleCardDestination postTitleCardDestination = PostTitleCardDestination.LEVEL;
    private LevelFrameResult titleReleaseResult = LevelFrameResult.GAMEPLAY_FRAME;

    /** Live slots and existing virtual seams, never a root/context facade. */
    interface Host {
        LevelManager level(); SpriteManager sprites(); Camera camera(); InputHandler input();
        GameModule module(); GameMode currentMode(); boolean endOfLevelActive();
        String mainCharacterCode(); AbstractPlayableSprite mainPlayable();
        void lockTitleControls(boolean locked); void restorePendingShield(AbstractPlayableSprite playable);
        GameMode changeMode(GameMode mode); GameMode changeModeWithoutRewindBoundary(GameMode mode);
        void markBonusEntryBoundary(); boolean returningFromSpecialStage(); void completeTitleReturn();
        void applyDeferredBonusStageSetup(); void syncPlaybackInput(); void admitDestination();
        void recordLevelRelease(); void notifyModeChanged(GameMode oldMode);
    }

    GameLoopTitleCardLifecycle(Host host) { this.host = host; }

    TitleCardProvider provider() {
        if (titleCardProvider == null) {
            titleCardProvider = host.module().getTitleCardProvider();
        }
        return titleCardProvider;
    }

    TitleCardProvider cachedProvider() { return titleCardProvider; }
    void clearProviderCache() { titleCardProvider = null; }
    PostTitleCardDestination destination() { return postTitleCardDestination; }
    void destination(PostTitleCardDestination destination) { postTitleCardDestination = destination; }
    LevelFrameResult releaseResult() { return titleReleaseResult; }

    boolean update(
            boolean doFrameStep,
            TitleCardProvider titleCard,
            PlcLifecycleFrame frame,
            GameplayModeContext gameplayMode,
            LevelManager levelManager,
            SpriteManager spriteManager,
            Camera camera,
            InputHandler input,
            PostTitleCardDestination destination,
            Runnable beginAudioFrame,
            Runnable advanceAudioFrame,
            Consumer<PlcLifecyclePhase> preparePhase,
            LevelFrameStep.StepWrapper wrapper) {
        frame.claim(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        LevelFrameContext frameContext = LevelFrameContext.from(gameplayMode);
        // The title-card loop owns a real VBlank even when it does not run the
        // ordinary player/object body, and several of its branches never reach
        // a LevelFrameStep edge. Dispatch the game-owned hook here so every
        // branch services it exactly once; the token's guard makes the later
        // LevelFrameStep dispatches on the physics branch no-ops. This adds no
        // hardware service boundary -- VINT_SERVICE stays owned by
        // LevelFrameStep alone.
        LevelFrameStep.dispatchGameVBlank(frameContext, frame);
        boolean hardwareTimedProviderScan = titleCard != null
                && titleCard.shouldAdvanceVblankClockDuringLockedPhase();
        boolean preparedByFrameStep = hardwareTimedProviderScan;
        if (hardwareTimedProviderScan) {
            LevelFrameStep.executeHardwareTimedObjectScan(
                    frameContext, frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, titleCard::update);
        } else if (titleCard != null) {
            titleCard.update();
        }

        // Publish while the provider still owns its fresh mode; completing
        // the title handoff below clears that mode and its terrain-ready gate.
        if (levelManager.hasPendingFreshLevelTransitionBoundary() && titleCard != null
                && (titleCard instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                        ? boundary.shouldPublishFreshLevelTransitionInitialBoundary()
                        : titleCard.shouldCompleteFreshLevelTransitionBoundary())) {
            levelManager.publishFreshLevelTransitionInitialBoundary();
        }

        if (titleCard == null || titleCard.shouldCompleteFreshLevelTransitionBoundary()) {
            int preludePasses = 0;
            boolean ranPlayerPrelude = false;
            if (titleCard != null) {
                // Level/loc_64DC hands the surviving title owner to LevelLoop
                // with a rewritten wait timer after the title and terrain queues drain.
                titleCard.completeFreshLevelRuntimeArtHandoff();
                preludePasses = titleCard.levelObjectPreludePassesAtRelease();
                for (int i = 0; i < preludePasses; i++) {
                    OscillationManager.suppressNextFrames(1);
                    if (titleCard.shouldRunPlayerPreludeAtRelease()) {
                        runInputNeutralTitleCardPlayerPrelude();
                        ranPlayerPrelude = true;
                    }
                    levelManager.updateObjectPositionsWithoutTouches();
                }
            }
            if (ranPlayerPrelude) {
                LevelInitProfile initProfile = frameContext.gameModule().getLevelInitProfile();
                DynamicArtLifecycleService artLifecycle = gameplayMode.dynamicArtLifecycle();
                int tailRows = initProfile != null
                        ? initProfile.preLevelMainLoopDelayFrames() : 0;
                if (tailRows > 0 && artLifecycle != null && artLifecycle.isRunActive()) {
                    // S1 Level_LoadObj stages Sonic's DPLC only after the title
                    // wait drains. Its next V-int belongs to Level_Delay / the
                    // palette fade, before Level_MainLoop (sonic.asm:2895-2966).
                    // Hold the real release preparation for that counted tail;
                    // ordinary zero-tail owners keep their next VBlank service.
                    artLifecycle.holdPendingPlayerPreparationForPreMainLoopTail(tailRows);
                }
            }
            if (!preparedByFrameStep) {
                preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            }
            // GM_Level clears Level_frame_counter on every non-demo level entry
            // (docs/s2disasm/s2.asm:4771-4773), and none of the pre-Level_MainLoop
            // object passes advance it -- the only increment is Level_MainLoop's
            // own addq at s2.asm:5092. The counter lives in CrossResetRAM, so
            // Level_ClrRam does not clear it; this store is the sole reset and it
            // runs for a special-stage return exactly as for a fresh entry.
            spriteManager.setFrameCounter(0);
            destination.completeRelease(
                    levelManager, this::exitTitleCard, preludePasses > 0);
            titleReleaseResult = LevelFrameResult.SETUP_ONLY;
            return true;
        }

        beginAudioFrame.run();
        // ROM: the title-card wait loops (docs/s2disasm/s2.asm:4914-4924 and
        // 5060-5066; docs/s1disasm/sonic.asm Level_TtlCardLoop 2811-2839) run
        // RunObjects but NOT OscillateNumDo -- the global oscillator only
        // advances inside Level_MainLoop (s2.asm:5108). Suppress the oscillator
        // advance for this locked title-card object pass so it holds at its
        // OscillateNumInit baseline until gameplay unlocks.
        if (titleCard.shouldRunPlayerPhysics()) {
            OscillationManager.suppressNextFrames(1);
            spriteManager.publishHeldInputForLevelEvents(input);
            LevelFrameStep.execute(LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, levelManager, camera,
                    () -> spriteManager.update(input), wrapper);
            preparedByFrameStep = true;
        } else {
            if (titleCard.shouldRunLevelObjectsDuringLockedPhase()) {
                OscillationManager.suppressNextFrames(1);
                levelManager.updateObjectPositions();
                camera.updatePosition(true);
            } else if (titleCard.shouldAdvanceVblankClockDuringLockedPhase()) {
                levelManager.advanceTitleCardVblankOnly();
            } else {
                camera.updatePosition(true);
            }
        }
        advanceAudioFrame.run();
        if (!preparedByFrameStep) {
            preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        }
        if (titleCard instanceof TitleCardLoopTail loopTail) {
            loopTail.completeLockedIteration();
            if (titleCard.shouldReleaseControl()) {
                spriteManager.setFrameCounter(0);
                destination.completeRelease(levelManager, this::exitTitleCard, true);
                titleReleaseResult = LevelFrameResult.SETUP_ONLY;
                return true;
            }
        }
        return false;
    }

    void enterTitleCard(int zoneIndex, int actIndex) {
        if (host.currentMode() != GameMode.LEVEL) {
            return;
        }

        boolean freshBoundary = host.level().hasPendingFreshLevelTransitionBoundary();
        GameMode oldMode = host.changeMode(GameMode.TITLE_CARD);

        // Freeze the player during title card - full state reset
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (!freshBoundary && sprite instanceof AbstractPlayableSprite playable) {
            // Freeze all movement
            playable.setXSpeed((short) 0);
            playable.setYSpeed((short) 0);
            playable.setGSpeed((short) 0);
            playable.setAir(false);
            // Clear death/hurt state to prevent dying during title card
            playable.setDead(false);
            playable.setHurt(false);
            playable.setDeathCountdown(0);
            playable.setInvulnerableFrames(0);
            playable.setRolling(false);
        }
        host.lockTitleControls(true);

        // Initialize the title card manager
        if (provider() != null) {
            if (freshBoundary) {
                provider().initializeFreshLevelTransition(zoneIndex, actIndex);
            } else {
                provider().initialize(zoneIndex, actIndex);
            }
        }

        // ScreenInit can already own the camera through Scroll_lock (SSZ1_ScreenInit
        // sets $F49 before Obj_57C1E positions the player). A forced player snap would
        // override that scripted origin and clamp it against the arrival's $BC0 bound.
        if (!freshBoundary && !host.camera().getFrozen()) {
            host.camera().updatePosition(true);
        }

        // Notify listener of mode change
        host.notifyModeChanged(oldMode);

        LOGGER.info("Entered Title Card for zone " + zoneIndex + " act " + actIndex);
    }

    void startPendingInLevelTitleCard() {
        InLevelTitleCardCoordinator.startIfRequested(
                host.level(), provider(),
                host.endOfLevelActive(), host::lockTitleControls);
    }

    void applyTitleCardControlLock(boolean locked) {
        if (host.sprites() == null) {
            return;
        }
        for (var sprite : host.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.setControlLocked(locked);
            }
        }
    }

    void exitTitleCard() {
        if (host.currentMode() != GameMode.TITLE_CARD) {
            return;
        }

        GameMode oldMode = host.currentMode();
        host.lockTitleControls(false);

        if (postTitleCardDestination == PostTitleCardDestination.BONUS_STAGE) {
            // Transitioning to bonus stage after "BONUS STAGE" title card
            postTitleCardDestination = PostTitleCardDestination.LEVEL;
            host.changeMode(GameMode.BONUS_STAGE);

            // Apply deferred bonus stage setup
            host.applyDeferredBonusStageSetup();

            // Re-arm the playback forced-input bridge now that the mode is
            // BONUS_STAGE. This exit runs mid-step and falls through to
            // BONUS_STAGE gameplay processing in the SAME loop.step(): that
            // fall-through frame IS the interior's first gameplay tick. The
            // step-top syncPlaybackInputBridge already ran while the mode was
            // still TITLE_CARD, which PlaybackDebugManager.isDriving() does not
            // drive (only LEVEL/BONUS_STAGE), so the player's first interior tick
            // would otherwise run with a stale/neutral forced mask -- dropping the
            // recorded frame-0 controller input the ROM's first bonus frame reads
            // (e.g. the gumball's frame-0 left press that produces the single
            // grounded ground-move before the player falls into the machine).
            // Re-syncing here, after control is released and the mode is
            // BONUS_STAGE, applies the current forced-input mask so the
            // fall-through tick reads the recorded input. Bonus-entry only; LEVEL
            // title-card exits are unaffected.
            host.syncPlaybackInput();

            LOGGER.info("Exited bonus title card, entering BONUS_STAGE mode");
        } else if (host.returningFromSpecialStage()) {
            host.completeTitleReturn();
        } else {
            host.changeMode(GameMode.LEVEL);
            host.restorePendingShield(host.mainPlayable());

            // Re-apply zone-specific player state (airborne intros like HCZ1, MGZ1)
            LevelEventProvider levelEvents = host.module().getLevelEventProvider();
            if (!host.level().hasPendingFreshLevelTransitionBoundary() && levelEvents != null) {
                levelEvents.applyZonePlayerStateAfterTitleCard();
            }
            LOGGER.info("Exited Title Card, starting level");
        }

        // A title-card release falls through into the destination gameplay in
        // this same loop.step(). The step-top bridge sync ran while the mode was
        // still TITLE_CARD (which is intentionally not driven), so refresh the
        // forced input now for LEVEL as well as for BONUS_STAGE above. Otherwise
        // a death/restart consumes one stale pre-reload input on the first native
        // Level_MainLoop frame before a trace comparator can observe it.
        if (host.currentMode() == GameMode.LEVEL) {
            host.admitDestination();
            host.syncPlaybackInput();
        }

        if (host.currentMode() == GameMode.LEVEL) {
            host.recordLevelRelease();
        }

        host.notifyModeChanged(oldMode);
    }

    void runInputNeutralTitleCardPlayerPrelude() {
        java.util.List<AbstractPlayableSprite> playables = host.sprites().getAllSprites().stream()
                .filter(AbstractPlayableSprite.class::isInstance)
                .map(AbstractPlayableSprite.class::cast)
                .toList();
        int[] forcedInputMasks = new int[playables.size()];
        for (int i = 0; i < playables.size(); i++) {
            AbstractPlayableSprite playable = playables.get(i);
            forcedInputMasks[i] = playable.getForcedInputMask();
            playable.clearForcedInputMask();
        }
        try {
            host.sprites().update(host.input());
        } finally {
            for (int i = 0; i < playables.size(); i++) {
                playables.get(i).setForcedInputMask(forcedInputMasks[i]);
            }
        }
    }

    GameMode enterBonusTitleCardAfterLevelLoadBoundary() {
        host.markBonusEntryBoundary();
        postTitleCardDestination = PostTitleCardDestination.BONUS_STAGE;
        return host.changeModeWithoutRewindBoundary(GameMode.TITLE_CARD);
    }
}
