package com.openggf.game;

import com.openggf.level.LevelManager;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates in-level and results-return title-card preparation. */
public final class InLevelTitleCardCoordinator {
    private InLevelTitleCardCoordinator() {
    }

    /** Locks control and runs the game-specific fresh-player prelude for a results-return title card. */
    public static void prepareResultsTransition(Sprite sprite,
                                                Consumer<Boolean> controlLock,
                                                Supplier<GameModule> moduleSupplier,
                                                SpriteManager spriteManager,
                                                LevelManager levelManager) {
        controlLock.accept(true);
        if (sprite instanceof AbstractPlayableSprite playable) {
            int freshPlayerPreludeFrames = moduleSupplier.get()
                    .getLevelInitProfile()
                    .freshMainPlayablePreludeFrames();
            spriteManager.warmUpFreshMainPlayableOnly(
                    freshPlayerPreludeFrames, levelManager, playable, false);
        }
    }

    public static boolean startIfRequested(LevelManager levelManager,
                                           TitleCardProvider provider,
                                           boolean endOfLevelActive,
                                           Consumer<Boolean> controlLock) {
        Objects.requireNonNull(levelManager, "levelManager");
        Objects.requireNonNull(controlLock, "controlLock");
        if (endOfLevelActive || !levelManager.consumeInLevelTitleCardRequest()) {
            return false;
        }
        if (provider == null) {
            return false;
        }
        provider.initializeInLevel(
                levelManager.getInLevelTitleCardZone(),
                levelManager.getInLevelTitleCardAct());
        if (levelManager.consumeInLevelTitleCardLevelGamestateResetRequest()) {
            provider.requestLevelGamestateResetAtInLevelDisplay(
                    levelManager.consumeInLevelTitleCardResetAdditionalDispatches(),
                    levelManager.consumeInLevelTitleCardResetPhaseOneDispatchOverlap());
        }
        if (levelManager.consumeInLevelTitleCardPlayerControlLockRequest()) {
            provider.requestInLevelPlayerControlLock();
            controlLock.accept(true);
        }
        provider.requestInLevelExitAdditionalDispatches(
                levelManager.consumeInLevelTitleCardExitAdditionalDispatches(),
                levelManager.consumeInLevelTitleCardExitPhaseOneDispatchOverlap());
        return true;
    }
}
