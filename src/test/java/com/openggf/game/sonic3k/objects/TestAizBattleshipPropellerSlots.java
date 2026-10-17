package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.events.AizObjectEventBridge;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obj_AIZBattleship's init allocates two Obj_BattleshipPropeller SSTs after the
 * ship (docs/skdisasm/sonic3k.asm:105303-105318); each propeller animates with
 * Ani_AIZShipPropeller, translates from the ship's secondary camera and deletes
 * once Events_routine_fg reaches $C (:105385-105407).
 */
class TestAizBattleshipPropellerSlots {
    private static final int SHIP_SLOT = 4;
    private static final int CAMERA_X = 0x4100;
    private static final int CAMERA_Y = 0x0200;
    private static final int BASE_SECONDARY_Y = 0x08F0;

    private final RecordingAizBridge bridge = new RecordingAizBridge();
    private ObjectManager manager;
    private int pass;

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        manager = installObjectManager(bridge);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void firstShipPassAllocatesBothPropellersAfterTheShipAndRunsThemThatPass() {
        AizBattleshipInstance ship = addShip();

        step();
        assertTrue(propellers().isEmpty(),
                "the ship's creation pass is not executed, so its init has not allocated yet");

        step();
        List<AizBattleshipPropellerInstance> propellers = propellers();
        assertEquals(2, propellers.size(), "Obj_AIZBattleship init allocates exactly two propellers");
        AizBattleshipPropellerInstance front = propellers.get(0);
        AizBattleshipPropellerInstance back = propellers.get(1);
        assertEquals(SHIP_SLOT + 1, front.getSlotIndex(),
                "the first AllocateObjectAfterCurrent takes the next free SST after the ship");
        assertEquals(SHIP_SLOT + 2, back.getSlotIndex(),
                "the second AllocateObjectAfterCurrent takes the following free SST");
        assertEquals(AizBattleshipPropellerInstance.FRONT_SECONDARY_X, front.secondaryXForTest());
        assertEquals(AizBattleshipPropellerInstance.BACK_SECONDARY_X, back.secondaryXForTest());

        // Both run after the ship in the same pass: Translate_Camera2ObjPosition
        // uses the ship's already-advanced _unkEE98 ($4020.0000 - $8800).
        int shipX = ship.getSecondaryCameraX();
        assertEquals(0x401F, shipX);
        assertEquals(CAMERA_X + 0x3FBC - shipX, front.getX());
        assertEquals(CAMERA_X + 0x3DCC - shipX, back.getX());
        assertEquals(CAMERA_Y + 0x0A71 - ship.getSecondaryCameraY(), front.getY());
        assertEquals(0, front.mappingFrameForTest(), "first Animate_Sprite loads frame 0");
    }

    @Test
    void propellersHoldEachMappingFrameForThreePasses() {
        addShip();
        step();

        List<Integer> frames = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            step();
            frames.add(propellers().getFirst().mappingFrameForTest());
        }

        assertEquals(List.of(0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 0), frames,
                "Ani_AIZShipPropeller 2,0,1,2,3,$FF holds every frame for delay+1 passes");
    }

    @Test
    void propellersDeleteOnTheirFirstPassAfterEventsRoutineReachesEndRefresh() {
        AizBattleshipInstance ship = addShip();
        step();
        step();
        assertEquals(2, propellers().size());

        bridge.endRefreshActive = true;
        step();

        assertTrue(propellers().isEmpty(), "Events_routine_fg == $C deletes both propellers");
        assertTrue(manager.getActiveObjects().contains(ship), "the ship itself is unaffected");
        assertFalse(occupiedSlots().contains(SHIP_SLOT + 1), "deleted propellers free their SSTs");
        assertFalse(occupiedSlots().contains(SHIP_SLOT + 2), "deleted propellers free their SSTs");
    }

    @Test
    void propellerRestoresThroughGenericRecreateAndRelinksToTheLiveShip() {
        addShip();
        for (int i = 0; i < 6; i++) {
            step();
        }
        AizBattleshipPropellerInstance capturedBack = propellers().get(1);
        int capturedFrame = capturedBack.mappingFrameForTest();
        assertEquals(1, capturedFrame, "precondition: animation has advanced past its spawn frame");

        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        step();
        step();
        registry.restore(snapshot);

        AizBattleshipInstance restoredShip = manager.getActiveObjects().stream()
                .filter(AizBattleshipInstance.class::isInstance)
                .map(AizBattleshipInstance.class::cast)
                .findFirst().orElseThrow();
        List<AizBattleshipPropellerInstance> restored = propellers();
        assertEquals(2, restored.size());
        AizBattleshipPropellerInstance restoredBack = restored.get(1);
        assertNotSame(capturedBack, restoredBack, "restore recreates the captured propeller");
        assertEquals(SHIP_SLOT + 2, restoredBack.getSlotIndex());
        assertEquals(AizBattleshipPropellerInstance.BACK_SECONDARY_X, restoredBack.secondaryXForTest());
        assertEquals(capturedFrame, restoredBack.mappingFrameForTest());
        assertSame(restoredShip, restoredBack.sourceShipForTest(),
                "restored propeller must translate from the restored ship");

        // Forward replay from the restored state matches the uninterrupted run.
        step();
        step();
        step();
        assertEquals(2, restoredBack.mappingFrameForTest());
    }

    private AizBattleshipInstance addShip() {
        ObjectSpawn spawn = new ObjectSpawn(CAMERA_X, BASE_SECONDARY_Y, 0, 0, 0, false, 0);
        AizBattleshipInstance ship = new AizBattleshipInstance(spawn, BASE_SECONDARY_Y);
        manager.addDynamicObjectAtSlot(ship, SHIP_SLOT);
        return ship;
    }

    private void step() {
        pass++;
        manager.update(CAMERA_X, null, List.of(), pass, false);
    }

    private List<AizBattleshipPropellerInstance> propellers() {
        return manager.getActiveObjects().stream()
                .filter(o -> o instanceof AizBattleshipPropellerInstance && !o.isDestroyed())
                .map(AizBattleshipPropellerInstance.class::cast)
                .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                .toList();
    }

    private List<Integer> occupiedSlots() {
        List<Integer> slots = new ArrayList<>();
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof com.openggf.level.objects.AbstractObjectInstance instance && !object.isDestroyed()) {
                slots.add(instance.getSlotIndex());
            }
        }
        return slots;
    }

    private static ObjectManager installObjectManager(RecordingAizBridge bridge) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = fixedCamera();
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
        };
        ObjectManager objectManager = new ObjectManager(
                List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = objectManager;
        objectManager.reset(0);
        return objectManager;
    }

    private static Camera fixedCamera() {
        return new Camera() {
            @Override public short getX() { return (short) CAMERA_X; }
            @Override public short getY() { return (short) CAMERA_Y; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static final class RecordingAizBridge implements LevelEventProvider, AizObjectEventBridge {
        boolean endRefreshActive;

        @Override public void initLevel(int zone, int act) {}
        @Override public void update() {}
        @Override public void setBossFlag(boolean value) {}
        @Override public void setEventsFg5(boolean value) {}
        @Override public void triggerScreenShake(int frames) {}
        @Override public void onBattleshipComplete() {}
        @Override public void onBossSmallComplete() {}
        @Override public boolean isFireTransitionActive() { return false; }
        @Override public boolean isAct2TransitionRequested() { return false; }
        @Override public boolean isAiz2ShipEndRefreshActive() { return endRefreshActive; }
    }
}
