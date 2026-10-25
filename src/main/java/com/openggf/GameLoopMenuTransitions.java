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

    /** Nothing is fading, or a completed fade-to-black is holding the screen black. */
    static boolean idleOrHeldBlack(FadeManager fadeManager) {
        return !fadeManager.isActive() || fadeManager.getState() == FadeManager.FadeState.HOLD_BLACK;
    }

    /**
     * Fades out to {@code next}; a screen already held black (a run that ended straight into the
     * next launch) continues at once, as a completed fade-to-black runs its callback.
     */
    static void fadeOutOrContinue(FadeManager fadeManager, AudioManager audioManager, Runnable next) {
        if (fadeManager.getState() == FadeManager.FadeState.HOLD_BLACK) {
            audioManager.fadeOutMusic();
            next.run();
        } else {
            fadeOutTo(fadeManager, audioManager, next);
        }
    }

    static void fadeOutTo(FadeManager fadeManager, AudioManager audioManager, Runnable prepareExit, Runnable next) {
        if (fadeManager.isActive()) {
            return;
        }
        prepareExit.run();
        audioManager.fadeOutMusic();
        fadeManager.startFadeToBlack(next);
    }

    /** Selects a programmatic launch through the same deferred master-title exit. */
    static void launchGameByEntry(MasterTitleScreen masterScreen,
            java.util.function.Supplier<FadeManager> fadeManager,
            com.openggf.game.launch.MasterTitleLaunchCoordinator launchCoordinator,
            MasterTitleScreen.GameEntry entry, Runnable afterGameLoaded,
            java.util.Map<com.openggf.configuration.SonicConfiguration, Object> sessionOverrides,
            java.util.function.Consumer<MasterTitleScreen> exit) {
        if (masterScreen == null) {
            throw new IllegalStateException("No master title screen available");
        }
        if (fadeManager.get().isActive()) {
            throw new IllegalStateException(
                    "Cannot launch game: a master-title fade is already in flight");
        }
        launchCoordinator.setPendingLaunchCallback(afterGameLoaded);
        launchCoordinator.setPendingSessionOverrides(sessionOverrides);
        try {
            masterScreen.selectEntry(entry);
            exit.accept(masterScreen);
        } catch (RuntimeException e) {
            // The fade callback will never fire, so drop the staged launch callback.
            launchCoordinator.clearPendingLaunchCallback();
            throw e;
        }
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
