package com.openggf.level;

import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;

final class LevelRewindBoundaryCoordinator {
    private LevelRewindBoundaryCoordinator() {
    }

    static void markLevelLoadBoundary() {
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        }
    }
}
