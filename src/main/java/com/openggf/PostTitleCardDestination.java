package com.openggf;

import com.openggf.level.LevelManager;

/**
 * Where to transition after a title card completes.
 * Normally LEVEL, but bonus stage entry routes through title card first.
 */
enum PostTitleCardDestination {
    /** Normal: title card -> LEVEL mode (default) */
    LEVEL,
    /** Bonus stage entry: title card -> BONUS_STAGE mode */
    BONUS_STAGE;

    LevelFrameResult completeRelease(LevelManager levelManager, Runnable exitTitleCard) {
        boolean setupOnly = false;
        if (this == LEVEL) {
            levelManager.completeInitialTitleCardPresentation();
            setupOnly = levelManager.consumePendingInitialProcessSpritesPass();
        }
        exitTitleCard.run();
        return setupOnly ? LevelFrameResult.SETUP_ONLY : LevelFrameResult.GAMEPLAY_FRAME;
    }
}
