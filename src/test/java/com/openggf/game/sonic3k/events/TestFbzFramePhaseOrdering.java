package com.openggf.game.sonic3k.events;

import com.openggf.LevelFrameTestStep;
import com.openggf.LevelFrameStep;
import com.openggf.LevelFrameContext;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzFramePhaseOrdering {
    @Test
    void sharedFramePipelineExecutesFbzPhasesInRomOrder() {
        List<String> log = new ArrayList<>();
        var level = mock(com.openggf.level.LevelManager.class);
        var camera = mock(com.openggf.camera.Camera.class);
        LevelEventProvider events = mock(LevelEventProvider.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        doAnswer(i -> { log.add("anpal-fixed-pre"); return null; })
                .when(events).updateFixedInLevelObjectsBeforeDynamicObjects();
        doAnswer(i -> { log.add("objects"); return null; }).when(level).updateObjectPositionsWithoutTouches();
        doAnswer(i -> { log.add("camera"); return null; }).when(camera).updatePosition();
        doAnswer(i -> { log.add("event"); return null; }).when(events).update();
        doAnswer(i -> { log.add("flush"); return null; }).when(level).flushQueuedLayoutMutations();
        doAnswer(i -> { log.add("boundary"); return null; }).when(camera).updateBoundaryEasing();
        doAnswer(i -> { log.add("placement"); return null; }).when(level).postCameraObjectPlacementSync();
        doAnswer(i -> { log.add("level"); return null; }).when(level).update();

        LevelFrameTestStep.execute(context(events), level, camera, () -> { });

        assertEquals(List.of("anpal-fixed-pre", "objects", "camera", "event", "flush", "boundary", "placement", "level"), log);
    }

    @Test
    void magneticEdgeWrittenInFixedPreludeIsVisibleToSameFrameObjects() {
        Sonic3kFBZEvents workspace = new Sonic3kFBZEvents();
        workspace.init(0);
        var level = mock(com.openggf.level.LevelManager.class);
        var camera = mock(com.openggf.camera.Camera.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        LevelEventProvider provider = mock(LevelEventProvider.class);
        doAnswer(i -> { workspace.advanceMagneticPhase(0x100); return null; })
                .when(provider).updateFixedInLevelObjectsBeforeDynamicObjects();
        List<Sonic3kFBZEvents.MagneticPolarity> observed = new ArrayList<>();
        doAnswer(i -> { observed.add(workspace.getMagneticPolarity()); return null; })
                .when(level).updateObjectPositionsWithoutTouches();

        LevelFrameTestStep.execute(context(provider), level, camera, () -> { });

        assertEquals(List.of(Sonic3kFBZEvents.MagneticPolarity.ACTIVE), observed);
    }

    @Test
    void collisionModeWrittenByEventIsConsumedOnNextPlayerCollisionPass() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        var level = mock(com.openggf.level.LevelManager.class);
        var camera = mock(com.openggf.camera.Camera.class);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        LevelEventProvider provider = mock(LevelEventProvider.class);
        doAnswer(i -> {
            events.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 11, 12);
            return null;
        }).when(provider).update();
        List<Boolean> collisionPasses = new ArrayList<>();

        LevelFrameTestStep.execute(context(provider), level, camera,
                () -> collisionPasses.add(state.backgroundPlaneCollisionStateOrNull().active()));
        LevelFrameTestStep.execute(context(provider), level, camera,
                () -> collisionPasses.add(state.backgroundPlaneCollisionStateOrNull().active()));

        assertEquals(List.of(false, true), collisionPasses);
    }

    private static LevelFrameContext context(LevelEventProvider events) {
        return new LevelFrameContext(mock(GameModule.class), null, events, null,
                null, null, null, null, new com.openggf.level.resources.KosinskiModuleQueue(),
                new com.openggf.game.timing.HardwareTimingService(),
                com.openggf.game.timing.HardwareTimingBoundaryObserver.NO_OP);
    }
}
