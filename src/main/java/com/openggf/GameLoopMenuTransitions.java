package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.MasterTitleExitCoordinator;
import com.openggf.graphics.FadeManager;

import java.util.logging.Logger;

/** Menu fades and the deferred master-title launch, extracted from {@link GameLoop}. */
final class GameLoopMenuTransitions {
    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());

    private GameLoopMenuTransitions() {
    }

    static void fadeOutTo(FadeManager fadeManager, AudioManager audioManager, Runnable next) {
        fadeOutTo(fadeManager, audioManager, () -> {}, next);
    }

    static void fadeOutTo(FadeManager fadeManager, AudioManager audioManager, Runnable prepareExit, Runnable next) {
        if (fadeManager.isActive()) {
            return;
        }
        prepareExit.run();
        audioManager.fadeOutMusic();
        fadeManager.startFadeToBlack(next);
    }

    static void exitMasterTitleScreen(MasterTitleScreen masterScreen, FadeManager fadeManager,
            MasterTitleExitCoordinator exitCoordinator) {
        if (fadeManager.isActive()) {
            return;
        }

        MasterTitleEntry.Launch launch = masterScreen.getSelectedLaunch();
        String selectedGameId = masterScreen.getSelectedGameId();
        boolean programmaticSelection = masterScreen.isProgrammaticSelection();

        fadeManager.startFadeToBlack(() -> {
            if (launch != null && launch.entry() instanceof MasterTitleEntry.Standalone) {
                exitCoordinator.exitStandalone(launch);
            } else {
                exitCoordinator.exitStock(selectedGameId, programmaticSelection);
            }
        });

        LOGGER.info("Starting fade-to-black for master title screen exit (game: " + selectedGameId + ")");
    }
}
