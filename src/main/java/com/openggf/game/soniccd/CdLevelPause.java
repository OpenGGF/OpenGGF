package com.openggf.game.soniccd;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.internal.NativeLevelInputOwner;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.program.CdStageExit;

/** The pad branch of {@code Level_MainLoop} (Level/Main.asm:333-395). */
final class CdLevelPause implements LevelInputOverlay, NativeLevelInputOwner {
    @Override
    public boolean handleInput(InputHandler input) {
        var levels = GameServices.levelOrNull();
        if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) {
            return false;
        }
        var pad = input.logical().player1();
        boolean start = pad.startPressed();
        if (levels.isLevelInactiveForTransition() || GameServices.fade().isActive()) {
            return start;
        }
        var services = levels.getObjectManager().getObjectServices();
        var game = services.gameState();
        var player = services.camera().getFocusedSprite();
        if (start && !level.flags().ctrlLocked && player != null && !player.getDead()) {
            game.applyPauseToggle(true);
        }
        if (!game.isGamePaused() || pad.actionPressedMask() == 0) {
            return start;
        }

        if (CdSceneCoordinator.isTimeAttack(services.gameModule())) {
            NativeGameStateOps.restoreLives(game, 0);
        } else {
            game.loseLife();
        }
        game.setGamePaused(false);
        services.checkpointState().clear();
        if (game.getLives() == 0) {
            // LevelStart (Level/Main.asm:85-115) returns to the caller when lives is zero.
            CdStageExit.outOfLives(services);
        } else {
            levels.setLevelInactiveForTransition(true);
            levels.requestRespawn();
        }
        return true;
    }
}
