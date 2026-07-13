package com.openggf.game.launch;

import com.openggf.game.MasterTitleEntry;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Coordinates master-title launch exits and standalone completion returns. */
public final class MasterTitleExitCoordinator {
    private static final Logger LOGGER = Logger.getLogger(MasterTitleExitCoordinator.class.getName());

    private final Supplier<MasterTitleLaunchCoordinator> launchCoordinator;
    private final BooleanSupplier atMasterTitle;
    private final BooleanSupplier gameplayRuntimeReady;
    private final Runnable enterLevel;
    private final Runnable fadeFromBlack;
    private final Runnable progressionSave;
    private final Consumer<Boolean> endingTransitionPending;
    private final Runnable fadeOutMusic;
    private final Consumer<Runnable> fadeToBlack;
    private Consumer<String> stockExitHandler;
    private Consumer<MasterTitleEntry.Launch> standaloneExitHandler;

    public MasterTitleExitCoordinator(
            Supplier<MasterTitleLaunchCoordinator> launchCoordinator,
            BooleanSupplier atMasterTitle,
            BooleanSupplier gameplayRuntimeReady,
            Runnable enterLevel,
            Runnable fadeFromBlack,
            Runnable progressionSave,
            Consumer<Boolean> endingTransitionPending,
            Runnable fadeOutMusic,
            Consumer<Runnable> fadeToBlack) {
        this.launchCoordinator = Objects.requireNonNull(launchCoordinator, "launchCoordinator");
        this.atMasterTitle = Objects.requireNonNull(atMasterTitle, "atMasterTitle");
        this.gameplayRuntimeReady = Objects.requireNonNull(gameplayRuntimeReady, "gameplayRuntimeReady");
        this.enterLevel = Objects.requireNonNull(enterLevel, "enterLevel");
        this.fadeFromBlack = Objects.requireNonNull(fadeFromBlack, "fadeFromBlack");
        this.progressionSave = Objects.requireNonNull(progressionSave, "progressionSave");
        this.endingTransitionPending = Objects.requireNonNull(
                endingTransitionPending, "endingTransitionPending");
        this.fadeOutMusic = Objects.requireNonNull(fadeOutMusic, "fadeOutMusic");
        this.fadeToBlack = Objects.requireNonNull(fadeToBlack, "fadeToBlack");
    }

    public void setStockExitHandler(Consumer<String> handler) {
        stockExitHandler = handler;
    }

    public void setStandaloneExitHandler(Consumer<MasterTitleEntry.Launch> handler) {
        standaloneExitHandler = Objects.requireNonNull(handler, "handler");
    }

    public void exitStock(String selectedGameId, boolean programmaticSelection) {
        launchCoordinator().prepareExit(selectedGameId, programmaticSelection);
        if (stockExitHandler != null) stockExitHandler.accept(selectedGameId);
        finishExit(selectedGameId);
    }

    public void exitStandalone(MasterTitleEntry.Launch launch) {
        if (!(Objects.requireNonNull(launch, "launch").entry()
                instanceof MasterTitleEntry.Standalone standalone)) {
            throw new IllegalArgumentException("Standalone exit requires a standalone entry");
        }
        launchCoordinator().prepareStandaloneExit();
        if (standaloneExitHandler != null) standaloneExitHandler.accept(launch);
        finishExit(standalone.owner());
    }

    public void startStandaloneCompletion() {
        LOGGER.info("Starting standalone completion save and master-title return");
        progressionSave.run();
        endingTransitionPending.accept(true);
        fadeOutMusic.run();
        fadeToBlack.accept(() -> {
            endingTransitionPending.accept(false);
            launchCoordinator().returnToMasterTitle();
        });
    }

    private void finishExit(String selectedGameId) {
        if (atMasterTitle.getAsBoolean() && !gameplayRuntimeReady.getAsBoolean()) {
            launchCoordinator().restoreAfterFailedExit(selectedGameId, fadeFromBlack);
            return;
        }
        launchCoordinator().stagePendingLaunchCallback();
        if (atMasterTitle.getAsBoolean()) enterLevel.run();
        fadeFromBlack.run();
        LOGGER.info("Exited master title entry: " + selectedGameId);
    }

    private MasterTitleLaunchCoordinator launchCoordinator() {
        return Objects.requireNonNull(launchCoordinator.get(), "launchCoordinator result");
    }
}
