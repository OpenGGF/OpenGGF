package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.internal.NativeTitlelessLevelLoad;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeModeSwitchLoopTail {
    @Test void modeSwitchRunsAfterTheCurrentRomTailWithoutAnotherVBlank() {
        var events = new ArrayList<String>();
        var service = mock(PlcLifecycleService.class);
        when(service.hasPreparationBoundary(PlcLifecyclePhase.ORDINARY_LEVEL)).thenReturn(true);
        doAnswer(call -> { events.add("vblank"); return null; }).when(service).serviceVBlank(any());
        doAnswer(call -> { events.add("plc"); return null; }).when(service).prepareAfterLoop(any());
        var lifecycle = new PlcFrameLifecycleCoordinator(service);
        var level = selectedDestination(true);
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::plcFrameLifecycleOrNull).thenReturn(lifecycle);
            lifecycle.runLogicalIteration(() -> { }, frame -> {
                frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                NativeLevelLoadAccess.dispatchLoopTailModeSwitch(level, () -> {
                    assertTrue(level.consumeZoneActRequest());
                    events.add("mode-switch");
                });
                events.add("objects-completed");
                frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
                assertEquals(List.of("vblank", "objects-completed", "plc"), events);
                return null;
            });
        }
        assertEquals(List.of("vblank", "objects-completed", "plc", "mode-switch"), events);
        verify(service, times(1)).serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertFalse(level.getTransitions().hasZoneActRequest());
    }

    @Test void deferredLoadOpensItsCallerScopeOnlyAtExecutionAndClosesOnFailure() {
        var level = selectedDestination(true);
        var owner = (NativeTitlelessLevelLoad) level.activeGameModule().getLevelInitProfile();
        var events = new ArrayList<String>();
        when(owner.beginModeSwitchLoad()).thenAnswer(call -> {
            events.add("begin");
            return (com.openggf.game.internal.NativeLevelLoadCallerScope) () -> events.add("close");
        });
        var deferred = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        NativeLevelLoadAccess.dispatchLoopTailModeSwitch(level, () -> deferred.set(() -> {
            try (var caller = NativeLevelLoadAccess.beginModeSwitchLoad(level, 37, 2)) {
                assertEquals(List.of("begin"), events);
                throw new IllegalStateException("actual load failure");
            }
        }));
        assertEquals(List.of(), events, "scheduling the fade callback retains no caller scope");
        assertThrows(IllegalStateException.class, () -> deferred.get().run());
        assertEquals(List.of("begin", "close"), events);
    }

    @Test void ordinaryDestinationRetainsItsPendingRequestForTheHost() {
        var level = selectedDestination(false);
        NativeLevelLoadAccess.dispatchLoopTailModeSwitch(level, () -> fail("ordinary destination has its existing host boundary"));
        assertTrue(level.getTransitions().hasZoneActRequest());
    }

    private static LevelManager selectedDestination(boolean loopTail) {
        var level = mock(LevelManager.class);
        var module = mock(GameModule.class);
        var profile = mock(LevelInitProfile.class, withSettings().extraInterfaces(NativeTitlelessLevelLoad.class));
        var transitions = new LevelTransitionCoordinator();
        transitions.requestZoneAndAct(37, 2, true);
        when(level.getTransitions()).thenReturn(transitions);
        when(level.activeGameModule()).thenReturn(module);
        when(module.getLevelInitProfile()).thenReturn(profile);
        when(level.getRequestedZone()).thenReturn(37); when(level.getRequestedAct()).thenReturn(2);
        when(((NativeTitlelessLevelLoad) profile).switchesAtLoopTail(37, 2)).thenReturn(loopTail);
        when(level.consumeZoneActRequest()).thenAnswer(call -> transitions.consumeZoneActRequest());
        return level;
    }
}
