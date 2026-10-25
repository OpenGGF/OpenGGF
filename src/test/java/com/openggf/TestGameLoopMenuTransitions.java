package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.MasterTitleLaunchCoordinator;
import com.openggf.graphics.FadeManager;
import com.openggf.mods.scene.SceneLaunch;
import com.openggf.mods.scene.host.SceneRomLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestGameLoopMenuTransitions {
    @Test
    void programmaticSelectionCarriesItsTeamThroughExitAndRunsItsCallbackOnce() {
        var config = mock(SonicConfigurationService.class);
        var coordinator = new MasterTitleLaunchCoordinator(config);
        var screen = mock(MasterTitleScreen.class);
        var fade = mock(FadeManager.class);
        var callbacks = new AtomicInteger();
        var overrides = Map.<SonicConfiguration, Object>of(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        doAnswer(call -> {
            verify(config, never()).clearSessionOverrides();
            assertEquals(0, callbacks.get());
            return null;
        }).when(screen).selectEntry(MasterTitleScreen.GameEntry.SONIC_2);

        GameLoopMenuTransitions.launchGameByEntry(screen, () -> fade, coordinator,
                MasterTitleScreen.GameEntry.SONIC_2, callbacks::incrementAndGet, overrides,
                selected -> {
                    assertSame(screen, selected);
                    verify(screen).selectEntry(MasterTitleScreen.GameEntry.SONIC_2);
                    coordinator.prepareExit("s2", true);
                    var order = inOrder(config);
                    order.verify(config).clearSessionOverrides();
                    order.verify(config).setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
                    order.verify(config).resolveDisplayAspect();
                    coordinator.stagePendingLaunchCallback();
                });
        assertEquals(0, callbacks.get(), "The caller's post-step boundary owns callback delivery");
        coordinator.runAfterStepLaunchCallbackIfPresent();
        coordinator.runAfterStepLaunchCallbackIfPresent();
        assertEquals(1, callbacks.get());
    }

    @Test
    void missingTitleRejectsBeforeFadeAcquisitionOrLaunchStaging() {
        var coordinator = mock(MasterTitleLaunchCoordinator.class);
        var failure = assertThrows(IllegalStateException.class, () ->
                GameLoopMenuTransitions.launchGameByEntry(null,
                        () -> { fail("Missing title must not acquire a fade manager"); return null; },
                        coordinator, MasterTitleScreen.GameEntry.SONIC_2, null, Map.of(),
                        ignored -> fail("Missing title cannot exit")));
        assertEquals("No master title screen available", failure.getMessage());
        verifyNoInteractions(coordinator);
    }

    @Test
    void activeFadeRejectsWithoutSelectingOrStagingALaunch() {
        var coordinator = mock(MasterTitleLaunchCoordinator.class);
        var screen = mock(MasterTitleScreen.class);
        var fade = mock(FadeManager.class);
        when(fade.isActive()).thenReturn(true);
        assertThrows(IllegalStateException.class, () ->
                GameLoopMenuTransitions.launchGameByEntry(screen, () -> fade, coordinator,
                        MasterTitleScreen.GameEntry.SONIC_2, null, Map.of(),
                        ignored -> fail("An active fade cannot exit again")));
        verifyNoInteractions(coordinator, screen);
    }

    @ParameterizedTest @ValueSource(booleans={false, true})
    void failedSelectionOrExitClearsTheCallbackAndSessionOverrides(boolean duringExit) {
        var config = mock(SonicConfigurationService.class);
        var coordinator = new MasterTitleLaunchCoordinator(config);
        var screen = mock(MasterTitleScreen.class);
        var fade = mock(FadeManager.class);
        var failure = new IllegalStateException("refused");
        var callbacks = new AtomicInteger();
        if (!duringExit) doThrow(failure).when(screen).selectEntry(MasterTitleScreen.GameEntry.SONIC_2);
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                GameLoopMenuTransitions.launchGameByEntry(screen, () -> fade, coordinator,
                        MasterTitleScreen.GameEntry.SONIC_2, callbacks::incrementAndGet,
                        Map.of(SonicConfiguration.MAIN_CHARACTER_CODE, "tails"), ignored -> { throw failure; })));
        coordinator.stagePendingLaunchCallback();
        coordinator.runAfterStepLaunchCallbackIfPresent();
        coordinator.prepareExit("s2", true);
        assertEquals(0, callbacks.get());
        verify(config, never()).setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
    }

    @Test
    void pendingModApplyFinishesBeforeSceneRelaunchAdmission() {
        var loop = mock(GameLoop.class);
        var fade = mock(FadeManager.class);
        when(loop.resolveFadeManager()).thenReturn(fade);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
                .when(loop).fadeOutTo(any(Runnable.class));
        var library = mock(SceneRomLibrary.class);
        when(library.availableGames()).thenReturn(List.of("s2"));
        var navigation = new ModSceneNavigation(loop);
        navigation.forVisit(library).prepareRelaunch(new SceneLaunch("s2", "tails", List.of())).run();
        var screen = mock(MasterTitleScreen.class);
        doAnswer(call -> { when(loop.canLaunchGameNow()).thenReturn(true); return null; })
                .when(screen).finishPendingModApply();
        clearInvocations(loop);

        navigation.onMasterTitleFrame(screen);

        var order = inOrder(screen, loop);
        order.verify(screen).finishPendingModApply();
        order.verify(loop).canLaunchGameNow();
        order.verify(loop).launchGameByEntry(MasterTitleScreen.GameEntry.SONIC_2, null,
                Map.of(SonicConfiguration.MAIN_CHARACTER_CODE, "tails",
                        SonicConfiguration.SIDEKICK_CHARACTER_CODE, ""));
        navigation.onMasterTitleFrame(screen);
        verify(loop, times(1)).launchGameByEntry(any(), any(), anyMap());
    }
}
