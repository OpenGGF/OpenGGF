package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.InLevelTitleCardCoordinator;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
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
        return NativeTitleCardIteration.run(titleCard, frame, gameplayMode, levelManager,
                spriteManager, camera, beginAudioFrame, advanceAudioFrame, preparePhase, wrapper,
                () -> spriteManager.publishHeldInputForLevelEvents(input),
                () -> spriteManager.update(input), this::runInputNeutralTitleCardPlayerPrelude,
                preludePasses -> {
                    destination.completeRelease(levelManager, this::exitTitleCard, preludePasses > 0);
                    titleReleaseResult = LevelFrameResult.SETUP_ONLY;
                });
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
