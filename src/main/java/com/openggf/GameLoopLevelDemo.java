package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameOverExit;
import com.openggf.game.internal.LevelDemoPlayback;
import com.openggf.level.LevelManager;

import java.util.function.Supplier;

final class GameLoopLevelDemo {
    private GameLoopLevelDemo() {
    }

    static LevelDemoPlayback find(GameMode mode, LevelManager level) {
        if (mode != GameMode.LEVEL || level == null || level.getGameModule() == null) {
            return null;
        }
        return level.getGameModule().getGameService(LevelDemoPlayback.class);
    }

    static LevelFrameResult runFrame(LevelDemoPlayback demo, InputHandler input, Supplier<LevelFrameResult> frame) {
        if (demo == null) {
            return frame.get();
        }
        var nativeInput = LogicalInputSnapshot.ofPlayers(demo.inputForNextIteration(), PlayerInputState.neutral());
        return InputHandlerInternalAccess.withNativeInput(input, nativeInput, frame);
    }

    static void requestExitIfFinishedOrInterrupted(LevelDemoPlayback demo, InputHandler input, LevelManager level) {
        if (demo != null && (demo.hasFinished()
                || demo.playerCanInterrupt() && input.logical().player1().startPressed())) {
            level.requestGameOverExit(GameOverExit.TITLE_SCREEN);
        }
    }
}
