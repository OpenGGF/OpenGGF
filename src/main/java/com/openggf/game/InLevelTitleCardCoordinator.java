package com.openggf.game;

import com.openggf.level.LevelManager;

import java.util.Objects;
import java.util.function.Consumer;

/** Applies a pending transparent title-card request identically in live and recorded frame drivers. */
public final class InLevelTitleCardCoordinator {
    private InLevelTitleCardCoordinator() {
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
