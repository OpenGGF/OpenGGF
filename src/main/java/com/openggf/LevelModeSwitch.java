package com.openggf;

import com.openggf.level.LevelManager;
import com.openggf.level.NativeLevelLoadAccess;
import java.util.function.BooleanSupplier;

/** Production mode-switch requests that return to the dispatcher at the current level-loop tail. */
final class LevelModeSwitch {
    private LevelModeSwitch() { }
    @FunctionalInterface interface StartLevel { void start(int zone, int act, int music); }

    static void afterGameplay(LevelManager level, Runnable beforeExit, StartLevel startLevel,
            BooleanSupplier returnToHost, Runnable completeHostedAct, Runnable startEnding) {
        NativeLevelLoadAccess.dispatchLoopTailModeSwitch(level, () -> {
            if (level.consumeZoneActRequest()) {
                beforeExit.run();
                startLevel.start(level.getRequestedZone(), level.getRequestedAct(), level.getRequestedMusicId());
            }
        });
        NativeLevelLoadAccess.dispatchLoopTailCredits(level, () -> {
            if (level.consumeCreditsRequest()) {
                beforeExit.run();
                if (returnToHost.getAsBoolean()) completeHostedAct.run();
                else startEnding.run();
            }
        });
    }
}
