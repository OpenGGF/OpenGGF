package com.openggf.game.launch;

import com.openggf.game.MasterTitleEntry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestMasterTitleExitCoordinator {

    @Test
    void standaloneExitUsesTypedHandlerAndStagesSuccessfulLaunch() {
        MasterTitleLaunchCoordinator launchCoordinator = mock(MasterTitleLaunchCoordinator.class);
        AtomicBoolean atMasterTitle = new AtomicBoolean(true);
        AtomicBoolean runtimeReady = new AtomicBoolean(false);
        AtomicBoolean fadedFromBlack = new AtomicBoolean(false);
        AtomicReference<MasterTitleEntry.Launch> handled = new AtomicReference<>();
        MasterTitleExitCoordinator coordinator = coordinator(launchCoordinator, atMasterTitle,
                runtimeReady, fadedFromBlack, new AtomicReference<>());
        coordinator.setStandaloneExitHandler(launch -> {
            handled.set(launch);
            runtimeReady.set(true);
        });
        MasterTitleEntry.Launch launch = new MasterTitleEntry.Launch(
                new MasterTitleEntry.Standalone("sample", "Sample", true),
                MasterTitleEntry.Action.CONTINUE);

        coordinator.exitStandalone(launch);

        assertSame(launch, handled.get());
        assertFalse(atMasterTitle.get(), "successful launch enters gameplay when handler leaves mode unchanged");
        assertTrue(fadedFromBlack.get());
        verify(launchCoordinator).prepareStandaloneExit();
        verify(launchCoordinator).stagePendingLaunchCallback();
    }

    @Test
    void stockExitPreservesStringHandlerAndSharedSuccessChoreography() {
        MasterTitleLaunchCoordinator launchCoordinator = mock(MasterTitleLaunchCoordinator.class);
        AtomicBoolean atMasterTitle = new AtomicBoolean(true);
        AtomicBoolean runtimeReady = new AtomicBoolean(false);
        AtomicReference<String> handled = new AtomicReference<>();
        MasterTitleExitCoordinator coordinator = coordinator(launchCoordinator, atMasterTitle,
                runtimeReady, new AtomicBoolean(), new AtomicReference<>());
        coordinator.setStockExitHandler(gameId -> {
            handled.set(gameId);
            runtimeReady.set(true);
        });

        coordinator.exitStock("s2", false);

        assertSame("s2", handled.get());
        assertFalse(atMasterTitle.get());
        verify(launchCoordinator).prepareExit("s2", false);
        verify(launchCoordinator).stagePendingLaunchCallback();
    }

    @Test
    void standaloneCompletionSavesAndClearsPendingWhenFadeCompletes() {
        MasterTitleLaunchCoordinator launchCoordinator = mock(MasterTitleLaunchCoordinator.class);
        AtomicBoolean saved = new AtomicBoolean(false);
        AtomicBoolean pending = new AtomicBoolean(false);
        AtomicBoolean musicFaded = new AtomicBoolean(false);
        AtomicReference<Runnable> fadeCompletion = new AtomicReference<>();
        MasterTitleExitCoordinator coordinator = new MasterTitleExitCoordinator(
                () -> launchCoordinator, () -> false, () -> true, () -> {}, () -> {},
                () -> saved.set(true), pending::set, () -> musicFaded.set(true), fadeCompletion::set);

        coordinator.startStandaloneCompletion();

        assertTrue(saved.get());
        assertTrue(pending.get());
        assertTrue(musicFaded.get());
        fadeCompletion.get().run();
        assertFalse(pending.get());
        verify(launchCoordinator).returnToMasterTitle();
    }

    private static MasterTitleExitCoordinator coordinator(
            MasterTitleLaunchCoordinator launchCoordinator,
            AtomicBoolean atMasterTitle,
            AtomicBoolean runtimeReady,
            AtomicBoolean fadedFromBlack,
            AtomicReference<Runnable> fadeCompletion) {
        return new MasterTitleExitCoordinator(
                () -> launchCoordinator,
                atMasterTitle::get,
                runtimeReady::get,
                () -> atMasterTitle.set(false),
                () -> fadedFromBlack.set(true),
                () -> {},
                ignored -> {},
                () -> {},
                fadeCompletion::set);
    }
}
