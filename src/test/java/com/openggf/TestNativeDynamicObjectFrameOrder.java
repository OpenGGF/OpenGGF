package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.internal.NativeFixedLevelObjectPass;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.level.resources.KosinskiModuleQueue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native placement precedes reserved dispatch; live dynamics precede the actual camera cut. */
class TestNativeDynamicObjectFrameOrder {
    @Test void nativeOwnerRunsBothPhasesAroundReservedSlotsBeforeTheActualCamera() {
        var h = new Harness();
        h.run();
        assertEquals(List.of("vblank", "counter", "spawn", "player", "reserved", "dynamic",
                "camera", "build", "named-pre-camera-hook"), h.log);
        verify(h.level, never()).updateObjectPositionsPostPhysicsWithoutTouches(any(Runnable.class), anyBoolean());
        verify(h.level, never()).updateObjectPositionsWithoutTouches(anyBoolean());
        verify(h.module, times(1)).getGameService(NativeDynamicObjectPass.class);
    }

    @Test void unboundInlinePathKeepsItsGenericExecutionAndCallback() {
        var h = new Harness();
        when(h.module.getGameService(NativeDynamicObjectPass.class)).thenReturn(null);
        doAnswer(i -> { h.log.add("generic"); ((Runnable) i.getArgument(0)).run(); return null; })
                .when(h.level).updateObjectPositionsPostPhysicsWithoutTouches(any(Runnable.class), eq(false));
        doAnswer(i -> { h.log.add("after-generic"); return null; })
                .when(h.level).updateZoneFeaturesAfterObjectExecution();
        h.run();
        assertEquals(List.of("vblank", "counter", "player", "reserved", "generic", "after-generic",
                "camera", "build", "named-pre-camera-hook"), h.log);
    }

    @Test void competingGenericObjectManagerRejectsBeforeServiceOrClockAdvance() {
        var h = new Harness();
        when(h.level.getObjectManager()).thenReturn(mock(ObjectManager.class));
        assertThrows(IllegalStateException.class, h::run);
        assertTrue(h.log.isEmpty());
        verify(h.level, never()).advanceLevelFrameCounter();
    }

    @Test void unboundLegacyPathStillRunsGenericObjectsBeforeThePlayer() {
        var h = new Harness();
        when(h.module.getGameService(NativeDynamicObjectPass.class)).thenReturn(null);
        when(h.level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        doAnswer(i -> { h.log.add("generic"); return null; })
                .when(h.level).updateObjectPositionsWithoutTouches(false);
        h.run();
        assertEquals(List.of("vblank", "counter", "reserved", "generic", "player",
                "camera", "build", "named-pre-camera-hook"), h.log);
    }

    @Test void titleCardCycleCannotAcquireTheOrdinaryDynamicOwner() {
        var h = new Harness();
        var context = h.context(null);
        var frame = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(h.module).latchBeforeFadeUpdate();
        assertThrows(IllegalStateException.class, () -> LevelFrameStep.execute(context, frame,
                PlcLifecyclePhase.LEVEL_TITLE_CARD, h.level, h.camera, () -> h.log.add("player"),
                LevelFrameStep.DIRECT_WRAPPER));
        assertTrue(h.log.isEmpty());
    }

    @Test void competingGenericRingManagerRejectsBeforeServiceOrClockAdvance() {
        var h = new Harness();
        when(h.level.getRingManager()).thenReturn(mock(RingManager.class));
        assertThrows(IllegalStateException.class, h::run);
        assertTrue(h.log.isEmpty());
    }

    @Test void incompatibleLegacyOrderingRejectsBeforeService() {
        var h = new Harness();
        when(h.level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        assertThrows(IllegalStateException.class, h::run);
        assertTrue(h.log.isEmpty());
    }

    @Test void playerFailureCannotAdvanceDynamicPassOrCamera() {
        var h = new Harness();
        var failure = new IllegalStateException("native player body rejected");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> LevelFrameTestStep.execute(h.context(null), h.level, h.camera,
                        () -> { h.log.add("player-failed"); throw failure; })));
        assertEquals(List.of("vblank", "counter", "spawn", "player-failed"), h.log);
    }

    @Test void pausedAndLagRowsServiceVblankWithoutEitherObjectPhase() {
        var h = new Harness();
        var state = mock(GameStateManager.class);
        when(state.applyPauseToggle(true)).thenReturn(true);
        assertEquals(LevelFrameResult.PAUSED, LevelFrameTestStep.executeWithPause(
                h.context(state), h.level, h.camera, () -> h.log.add("player"), true,
                LevelFrameStep.DIRECT_WRAPPER));
        assertEquals(List.of("vblank"), h.log);
        h.log.clear();
        LevelFrameTestStep.serviceVBlankOnly(h.context(null));
        assertEquals(List.of("vblank"), h.log);
    }

    @Test void setupOnlyPassDoesNotStartNativeObjectCycle() {
        var h = new Harness();
        when(h.level.consumePendingInitialProcessSpritesPass()).thenReturn(true);
        assertEquals(LevelFrameResult.SETUP_ONLY, h.run());
        assertTrue(h.log.isEmpty());
    }

    @Test void preparedAdmissionStillRejectsAChangedManagerShapeBeforeClaim() {
        var h = new Harness();
        var admission = NativeDynamicObjectPass.admit(h.module, h.level, PlcLifecyclePhase.ORDINARY_LEVEL);
        when(h.level.getRingManager()).thenReturn(mock(RingManager.class));
        assertPreparedRejection(h, h.context(null), PlcLifecyclePhase.ORDINARY_LEVEL, admission);
    }

    @Test void ordinaryAdmissionCannotAuthorizeATitleCardPhase() {
        var h = new Harness();
        var admission = NativeDynamicObjectPass.admit(h.module, h.level, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertPreparedRejection(h, h.context(null), PlcLifecyclePhase.LEVEL_TITLE_CARD, admission);
    }

    @Test void boundAdmissionCannotTransferToADifferentModule() {
        var h = new Harness();
        var admission = NativeDynamicObjectPass.admit(h.module, h.level, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertPreparedRejection(h, new Harness().context(null), PlcLifecyclePhase.ORDINARY_LEVEL, admission);
    }

    @Test void boundAdmissionCannotCrossALoadInTheSameLevelManager() {
        var h = new Harness();
        when(h.level.getCompletedProductionLoadGeneration()).thenReturn(7L);
        var admission = NativeDynamicObjectPass.admit(h.module, h.level, PlcLifecyclePhase.ORDINARY_LEVEL);
        when(h.level.getCompletedProductionLoadGeneration()).thenReturn(8L);
        assertPreparedRejection(h, h.context(null), PlcLifecyclePhase.ORDINARY_LEVEL, admission);
    }

    private static void assertPreparedRejection(Harness h, LevelFrameContext context,
                                                PlcLifecyclePhase phase,
                                                NativeDynamicObjectPass.Admission admission) {
        var frame = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(h.module).latchBeforeFadeUpdate();
        try {
            assertThrows(IllegalStateException.class, () -> LevelFrameStep.execute(context, frame,
                    phase, h.level, h.camera, () -> h.log.add("player"),
                    LevelFrameStep.DIRECT_WRAPPER, admission));
            assertTrue(h.log.isEmpty(), "admission must reject before claim, clock, phases or camera");
            verify(h.module, times(1)).getGameService(NativeDynamicObjectPass.class);
        } finally {
            frame.finish();
        }
    }

    private static final class Harness {
        final List<String> log = new ArrayList<>();
        final GameModule module = mock(GameModule.class);
        final LevelManager level = mock(LevelManager.class);
        final Camera camera = mock(Camera.class);
        final LevelEventProvider events = mock(LevelEventProvider.class);
        final PlcLifecycleService plc = mock(PlcLifecycleService.class);

        Harness() {
            when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
            when(module.getGameService(NativeDynamicObjectPass.class)).thenReturn(new Owner(log));
            when(module.getGameService(NativeFixedLevelObjectPass.class)).thenReturn(() -> log.add("reserved"));
            when(module.getGameService(PlcLifecycleService.class)).thenReturn(plc);
            doAnswer(i -> { log.add("vblank"); return null; }).when(plc).serviceVBlank(any(PlcLifecyclePhase.class));
            doAnswer(i -> { log.add("counter"); return null; }).when(level).advanceLevelFrameCounter();
            when(level.advanceCameraDrivenScrollForFrame()).thenAnswer(i -> {
                log.add("camera"); log.add("build"); return true;
            });
            doAnswer(i -> { log.add("named-pre-camera-hook"); return null; })
                    .when(events).updateAfterObjectsBeforeCamera();
        }
        LevelFrameContext context(GameStateManager state) {
            return new LevelFrameContext(module, null, events, null, null, state, null, null,
                    new KosinskiModuleQueue(), new HardwareTimingService(), null);
        }
        LevelFrameResult run() {
            return LevelFrameTestStep.execute(context(null), level, camera, () -> log.add("player"));
        }
    }

    private record Owner(List<String> log) implements NativeDynamicObjectPass {
        @Override public void spawnBeforeReservedSlots() { log.add("spawn"); }
        @Override public void runDynamicSlotsAfterReservedSlots() { log.add("dynamic"); }
    }
}
