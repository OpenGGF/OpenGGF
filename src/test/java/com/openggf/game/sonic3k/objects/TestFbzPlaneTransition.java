package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.PlayableEntity;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.events.FbzObjectEventBridge;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzPlaneTransition {
    @Test
    void p1ThresholdStartsExactWordMotionAndYEndpointOwnsCollisionClear() {
        FbzEndBossEventControlInstance.NativeState state = FbzEndBossEventControlInstance.NativeState.initial();

        state = FbzEndBossEventControlInstance.stepNative(state, 0x2E7F, 0x300, 0, 0, 0);
        assertEquals(0, state.offsetX16_16());
        assertEquals(0, state.offsetY16_16());
        assertFalse(state.backgroundCollision());

        state = FbzEndBossEventControlInstance.stepNative(state, 0x2E80, 0x300, 0, 0, 0);
        assertEquals(0x00007800, state.offsetX16_16());
        assertEquals(0x0000A000, state.offsetY16_16());
        assertTrue(state.backgroundCollision());

        for (int i = 1; i < 2381; i++) {
            state = FbzEndBossEventControlInstance.stepNative(state, 0x2E80, 0x300, 0, 0, 0);
        }
        assertEquals(0x045C1800, state.offsetX16_16());
        assertEquals(0x05D02000, state.offsetY16_16());
        assertFalse(state.backgroundCollision(), "Y endpoint alone clears Background_collision_flag");
        assertEquals(FbzEndBossEventControlInstance.Phase.WAIT_ARENA_LOCK, state.phase(),
                "the endpoint falls straight through the satisfied camera-Y check");
    }

    @Test
    void extraSidekickCoordinatesCannotPerturbP1TriggerOrStageGates() {
        var state = FbzEndBossEventControlInstance.NativeState.initial();
        state = FbzEndBossEventControlInstance.stepNativeWithExtraSidekicks(
                state, 0x2E7F, 0x100, java.util.List.of(0x3FFF), 0, 0, 0);
        assertEquals(FbzEndBossEventControlInstance.Phase.WAIT_P1_TRIGGER, state.phase());
        assertEquals(0, state.offsetY16_16());
    }

    @Test
    void bossAllocationFailureStillClearsShakeAndPublishesOneShotRebase() {
        var state = new FbzEndBossEventControlInstance.NativeState(
                FbzEndBossEventControlInstance.Phase.WAIT_BOSS_STAGE,
                0x045C1800, 0x05D02000, false, true, false, false);

        state = FbzEndBossEventControlInstance.stepNative(state, 0x3000, 0x300, 0, 0, 0x0C);

        assertTrue(state.bossSpawnRequested());
        assertTrue(state.rebasePending());
        assertFalse(state.screenShakeActive());
        assertEquals(FbzEndBossEventControlInstance.Phase.COMPLETE, state.phase());
        state = FbzEndBossEventControlInstance.normalizeBossLoadHighWords(state);
        assertEquals(0x1800, state.offsetX16_16());
        assertEquals(0x2000, state.offsetY16_16());
    }

    @Test
    void controllerInitialBoundsAndSolidContractMatchNative() {
        assertEquals(0x3C, FbzEndBossEventControlInstance.nativeCameraMinY(PlayerCharacter.SONIC_ALONE));
        assertEquals(0x40, FbzEndBossEventControlInstance.nativeCameraMinY(PlayerCharacter.TAILS_ALONE));
        FbzEndBossEventControlInstance controller = new FbzEndBossEventControlInstance();
        assertEquals(new SolidObjectParams(0x4C0, 0x11, 0x11), controller.getSolidParams());
        assertTrue(controller.isTopSolidOnly());
        assertTrue(controller.isPersistent(),
                "the native routine remains live through COMPLETE and has no unload tail");
        assertTrue(controller.seedsNewRideCarryFromPreUpdateX(),
                "shared SolidObjectTop must consume the pre-update d4 carry reference");
    }

    @Test
    void nativeSubbossActiveSetupWindowRetainsBossApproachObjectsAcrossRewindAndLaterTrigger() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        Camera camera = new Camera();
        camera.setX((short) 0x2B36);
        camera.setY((short) 0x03C);
        RecordingControllerBridge bridge = new RecordingControllerBridge();
        // FBZ2SE_Normal advances to stage 4 at this camera crossing even when
        // Obj_FBZ2Subboss is still in its native release/cull sequence.
        bridge.foregroundStage = 4;
        AtomicInteger playerX = new AtomicInteger(0x2BD6);
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenAnswer(ignored -> (short) playerX.get());
        when(player.getCentreY()).thenReturn((short) 0x300);

        ObjectRegistry registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "FBZ boss approach"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        services.withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));
        services.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events));
        holder[0] = new ObjectManager(List.of(), registry, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        ObjectManager manager = holder[0];
        manager.addDynamicObject(new FbzEndBossEventControlInstance());
        manager.addDynamicObject(new FbzBossPillarInstance());

        manager.update(camera.getX() & 0xFFFF, player, List.of(), 0, false);

        assertEquals(1, manager.activeObjectsOfType(FbzEndBossEventControlInstance.class).size(),
                "Obj_FBZEndBossEventControl has no native out_of_range tail");
        assertEquals(1, manager.activeObjectsOfType(FbzBossPillarInstance.class).size(),
                "Obj_FBZBossPillar has no native out_of_range tail");

        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot beforeTrigger = rewind.capture();
        playerX.set(0x2E80);
        manager.update(camera.getX() & 0xFFFF, player, List.of(), 1, false);
        assertTrue(bridge.collisionActive, "released P1 must start the native boss-approach motion");

        rewind.restore(beforeTrigger);
        bridge.collisionActive = false;
        playerX.set(0x2BD6);
        manager.update(camera.getX() & 0xFFFF, player, List.of(), 2, false);
        assertFalse(bridge.collisionActive, "rewind must restore the pre-trigger controller phase");
        assertEquals(1, manager.activeObjectsOfType(FbzEndBossEventControlInstance.class).size());
        assertEquals(1, manager.activeObjectsOfType(FbzBossPillarInstance.class).size());

        playerX.set(0x2E80);
        manager.update(camera.getX() & 0xFFFF, player, List.of(), 3, false);
        assertTrue(bridge.collisionActive, "the rewound controller must still progress after release");
    }

    @Test
    void liveControllerPublishesMotionThenLocksCameraThroughTheProductionUpdatePath() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        Camera camera = new Camera();
        camera.setX((short) 0x32B8);
        camera.setY((short) 0x3C);
        RecordingControllerBridge bridge = new RecordingControllerBridge();
        StubObjectServices services = new StubObjectServices() {
            @Override public Camera camera() { return camera; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        services.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x2E80);
        when(player.getCentreY()).thenReturn((short) 0x100);
        FbzEndBossEventControlInstance controller = new FbzEndBossEventControlInstance();
        controller.setServices(services);

        controller.update(0, player);
        assertEquals(0x32B8, camera.getMaxX() & 0xFFFF);
        assertEquals(0x3C, camera.getMinY() & 0xFFFF);
        assertTrue(bridge.collisionActive);
        assertEquals(0, bridge.offsetY, "the event bridge publishes the 16-bit high word, not the fraction");
        for (int frame = 1; frame < 2381; frame++) controller.update(frame, player);
        assertFalse(bridge.collisionActive);
        assertEquals(camera.getMinY(), camera.getMaxY());
        assertEquals(8, bridge.foregroundStage);
        assertEquals(camera.getX(), camera.getMinX());
    }

    @Test
    void liveControllerRunsCameraMinXTailThroughoutMotionAndCameraYWait() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        Camera camera = new Camera();
        camera.setX((short) 0x2F00);
        camera.setY((short) 0x0100);
        camera.setMinX((short) 0x2E00);
        RecordingControllerBridge bridge = new RecordingControllerBridge();
        StubObjectServices services = new StubObjectServices() {
            @Override public Camera camera() { return camera; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        services.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x2E80);
        when(player.getCentreY()).thenReturn((short) 0x0100);
        FbzEndBossEventControlInstance controller = new FbzEndBossEventControlInstance();
        controller.setServices(services);

        controller.update(0, player);
        assertEquals(0x2F00, camera.getMinX() & 0xFFFF,
                "loc_5333A runs on the first MOVING call after the trigger falls through");

        camera.setX((short) 0x2F20);
        controller.update(1, player);
        assertEquals(0x2F20, camera.getMinX() & 0xFFFF,
                "loc_532F0 branches to loc_5333A on every MOVING call");

        camera.setX((short) 0x3200);
        for (int frame = 2; frame < 2381; frame++) controller.update(frame, player);
        assertFalse(bridge.collisionActive, "test precondition: motion reached its Y endpoint");
        assertNotEquals(camera.getMinY(), camera.getY(),
                "test precondition: loc_53322 remains in the camera-Y wait");
        assertEquals(0x3200, camera.getMinX() & 0xFFFF,
                "the endpoint call still executes the shared loc_5333A tail once");

        camera.setX((short) 0x3210);
        controller.update(2381, player);
        assertEquals(0x3210, camera.getMinX() & 0xFFFF,
                "loc_53322 branches to loc_5333A on every WAIT_CAMERA_Y_LOCK call");
        assertEquals(0, bridge.foregroundStage,
                "the unmatched camera-Y word must not complete the arena lock");
    }

    @Test
    void liveControllerSkipsCameraMinXTailWriteAtNativePlayerYBoundary() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        Camera camera = new Camera();
        camera.setX((short) 0x2F00);
        camera.setY((short) 0x0100);
        camera.setMinX((short) 0x2E00);
        RecordingControllerBridge bridge = new RecordingControllerBridge();
        StubObjectServices services = new StubObjectServices() {
            @Override public Camera camera() { return camera; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        services.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x2E80);
        when(player.getCentreY()).thenReturn((short) 0x0280);
        FbzEndBossEventControlInstance controller = new FbzEndBossEventControlInstance();
        controller.setServices(services);

        controller.update(0, player);

        assertEquals(0x2E00, camera.getMinX() & 0xFFFF,
                "loc_5333A uses bhs: Player_1+y_pos >= $280 must not ratchet Camera_min_X_pos");
        assertTrue(bridge.collisionActive, "the Y boundary must not suppress the MOVING phase");
    }

    @Test
    void collisionDifferencesAreDerivedFromPublishedCloudCameraWords() {
        var active = Sonic3kFBZEvents.bossApproachCollisionState(0x120, 0x230, true);
        assertTrue(active.active());
        assertEquals(0x2720, active.cameraDiffX());
        assertEquals(0xD0, active.cameraDiffY());
        assertFalse(Sonic3kFBZEvents.bossApproachCollisionState(0x45C, 0x5D0, false).active());
    }

    @Test
    void pillarUsesNativeHysteresisRiseFallAndFullSolidDimensions() {
        int displacement = 0;
        for (int i = 0; i < 12; i++) displacement = FbzBossPillarInstance.nextDisplacement(displacement, true);
        assertEquals(0x40, displacement);
        for (int i = 0; i < 8; i++) displacement = FbzBossPillarInstance.nextDisplacement(displacement, false);
        assertEquals(0, displacement);
        assertEquals(0, FbzBossPillarInstance.nativeRightBoundOffset(false));
        assertEquals(0x28, FbzBossPillarInstance.nativeRightBoundOffset(true));
        FbzBossPillarInstance pillar = new FbzBossPillarInstance();
        assertFalse(pillar.isTopSolidOnly());
        assertTrue(pillar.isPersistent(), "the native pillar routine has no unload tail");
        assertEquals(new SolidObjectParams(0x2B, 0x100, 0x100), pillar.getSolidParams());
    }

    @Test
    void stageFourIsNotCloudBossStageButStageSixteenIs() {
        assertFalse(FbzEndBossEventControlInstance.isCloudBossBackgroundStage(4));
        assertTrue(FbzEndBossEventControlInstance.isCloudBossBackgroundStage(16));
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED,
                FbzEndBossEventControlInstance.planeModeForBackgroundStage(16));
    }

    @Test
    void foregroundStageEightRefreshesTwoRowsPerCallAndReachesStageCOnEighthCall() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.setAct2ForegroundStage(8);
        for (int call = 1; call <= 8; call++) {
            events.updateAct2ScreenEvent(0, 0, false, 0);
            assertEquals(call * 2, events.getBossPlaneRefreshRows());
            assertEquals(call < 8 ? 8 : 0x0C, events.getAct2ForegroundStage());
        }
    }

    private static final class RecordingControllerBridge implements FbzObjectEventBridge, LevelEventProvider {
        int offsetX, offsetY, foregroundStage;
        boolean collisionActive, shakeActive = true;
        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
        @Override public void setMagneticState(Sonic3kFBZEvents.MagneticPolarity polarity, int timerPhase) { }
        @Override public void setCloudRewindId(int index, ObjectRefId id) { }
        @Override public void setCloudCleanupTerminal(boolean value) { }
        @Override public void setBossLoadPositionAdjustmentPending(boolean value) { }
        @Override public int getAct2ForegroundStage() { return foregroundStage; }
        @Override public void setAct2ForegroundStage(int stage) { foregroundStage = stage; }
        @Override public void setBossBackgroundOffsets(int x, int y) { offsetX = x; offsetY = y; }
        @Override public void setBossApproachMotionState(int x, int y, boolean active) {
            offsetX = x; offsetY = y; collisionActive = active;
        }
        @Override public void setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode plane) { }
        @Override public void setCollisionMode(Sonic3kFBZEvents.CollisionMode collision, int x, int y) { }
        @Override public void setScreenShakeState(boolean active, int offset, int phase) { shakeActive = active; }
        @Override public void setScreenShakeActive(boolean active) { shakeActive = active; }
        @Override public boolean isScreenShakeActive() { return shakeActive; }
    }
}
