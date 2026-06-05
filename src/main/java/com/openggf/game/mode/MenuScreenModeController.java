package com.openggf.game.mode;

import com.openggf.control.InputHandler;
import com.openggf.game.DataSelectProvider;
import com.openggf.game.GameMode;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.TitleScreenProvider;

/**
 * Owns per-frame update sequencing for menu-like game screens that run outside
 * normal gameplay but after ROM/game systems are available.
 */
public final class MenuScreenModeController {

    public boolean handles(GameMode mode) {
        return mode == GameMode.TITLE_SCREEN
                || mode == GameMode.LEVEL_SELECT
                || mode == GameMode.DATA_SELECT;
    }

    public void updateTitleScreen(TitleScreenProvider titleScreen,
                                  InputHandler inputHandler,
                                  Runnable onExit) {
        if (titleScreen != null) {
            titleScreen.update(inputHandler);
            if (titleScreen.isExiting() && onExit != null) {
                onExit.run();
            }
        }
        inputHandler.update();
    }

    public void updateLevelSelect(LevelSelectProvider levelSelect,
                                  InputHandler inputHandler,
                                  Runnable onExit) {
        if (levelSelect != null) {
            levelSelect.update(inputHandler);
            if (levelSelect.isExiting() && onExit != null) {
                onExit.run();
            }
        }
        inputHandler.update();
    }

    public void updateDataSelect(DataSelectProvider dataSelect,
                                 InputHandler inputHandler,
                                 Runnable onExit) {
        if (dataSelect != null) {
            dataSelect.update(inputHandler);
            if (dataSelect.isExiting() && onExit != null) {
                onExit.run();
            }
        }
        inputHandler.update();
    }
}
