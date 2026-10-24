package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLbz2RobotnikShipRewind {
    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0x4300, 0x0500, 0x4600, 0x0800, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void liveFlameRestoreReplaysRetirementBeforeBossAllocation() {
        Harness h = new Harness();
        Lbz2RobotnikShipInstance lowerSlot = h.ship(0x4400);
        Lbz2RobotnikShipInstance ship = h.startRide();
        var flame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        var cameraChild = h.only(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class);
        ObjectRefId shipId = h.id(ship);
        ObjectRefId flameId = h.id(flame);
        ObjectRefId cameraId = h.id(cameraChild);
        int flameSlot = flame.getSlotIndex();
        int bossSlot = lowerSlot.getSlotIndex();
        h.manager.removeDynamicObject(lowerSlot);
        CompositeSnapshot snapshot = h.registry.capture();

        Outcome uninterrupted = h.release(ship, flame, cameraChild, bossSlot, flameSlot);
        h.registry.restore(snapshot);

        var restoredShip = h.only(Lbz2RobotnikShipInstance.class);
        var restoredFlame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        var restoredCamera = h.only(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class);
        assertNotSame(ship, restoredShip);
        assertNotSame(flame, restoredFlame);
        assertNotSame(cameraChild, restoredCamera);
        assertEquals(shipId, h.id(restoredShip));
        assertEquals(flameId, h.id(restoredFlame));
        assertEquals(cameraId, h.id(restoredCamera));
        assertEquals(List.of(restoredCamera, restoredFlame), restoredShip.spawnedChildrenForTest());

        Outcome replay = h.release(restoredShip, restoredFlame, restoredCamera, bossSlot, flameSlot);
        assertEquals(uninterrupted, replay);
        assertFalse(restoredShip.spawnedChildrenForTest().contains(flame));
        assertFalse(restoredShip.spawnedChildrenForTest().contains(cameraChild));
    }

    @Test
    void retiredCameraIsNotCapturedAsHistoricalChildIdentity() {
        Harness h = new Harness();
        var lowerSlot = h.ship(0x4400);
        var ship = h.startRide();
        var flame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        var cameraChild = h.only(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class);
        int bossSlot = lowerSlot.getSlotIndex();
        int flameSlot = flame.getSlotIndex();
        h.manager.removeDynamicObject(lowerSlot);
        h.manager.removeDynamicObject(cameraChild);
        assertEquals(List.of(flame), ship.spawnedChildrenForTest());
        CompositeSnapshot snapshot = h.registry.capture();

        Outcome uninterrupted = h.release(ship, flame, null, bossSlot, flameSlot);
        h.registry.restore(snapshot);
        var restoredShip = h.only(Lbz2RobotnikShipInstance.class);
        var restoredFlame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        assertEquals(List.of(restoredFlame), restoredShip.spawnedChildrenForTest());
        assertTrue(h.manager.activeObjectsOfType(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class).isEmpty());
        assertEquals(uninterrupted, h.release(restoredShip, restoredFlame, null, bossSlot, flameSlot));
    }

    @Test
    void captureAfterFlameRetirementKeepsOnlyRegisteredBossIdentity() {
        Harness h = new Harness();
        var ship = h.startRide();
        var flame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        var cameraChild = h.only(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class);
        ObjectRefId shipId = h.id(ship);
        ship.forceOffscreenForTest();
        ship.update(2, null);
        cameraChild.update(3, null);
        h.manager.removeDynamicObject(cameraChild);
        var boss = h.only(LbzFinalBoss1Instance.class);
        ObjectRefId bossId = h.id(boss);
        assertEquals(List.of(boss), ship.spawnedChildrenForTest());
        assertFalse(h.manager.getActiveObjects().contains(flame));
        List<ChildState> expected = h.states();
        CompositeSnapshot snapshot = h.registry.capture();

        h.registry.restore(snapshot);

        var restoredShip = (Lbz2RobotnikShipInstance) h.byId(shipId);
        var restoredBoss = (LbzFinalBoss1Instance) h.byId(bossId);
        assertNotSame(ship, restoredShip);
        assertNotSame(boss, restoredBoss);
        assertTrue(restoredShip.isDestroyed());
        assertEquals(List.of(restoredBoss), restoredShip.spawnedChildrenForTest());
        assertEquals(expected, h.states());
        assertDoesNotThrow(h.registry::capture);
        h.manager.lifecycle.clear();
        restoredShip.update(4, null);
        assertTrue(h.manager.lifecycle.isEmpty(), "retired flame must not be removed or allocated again");
        h.manager.removeDynamicObject(restoredShip);
        assertDoesNotThrow(h.registry::capture);
    }

    @Test
    void missingShipStillDropsRecreatedChildren() {
        Harness h = new Harness();
        var ship = h.startRide();
        var flame = h.only(Lbz2RobotnikShipInstance.ExhaustFlameChild.class);
        var cameraChild = h.only(Lbz2RobotnikShipInstance.GradualCameraMaxXChild.class);
        var context = h.manager.captureIdentityContext();
        var flameContext = new RewindRecreateContext(flame.getSpawn(),
                flame.captureRewindState(context), h.services, h.manager, null);
        var cameraContext = new RewindRecreateContext(cameraChild.getSpawn(),
                cameraChild.captureRewindState(context), h.services, h.manager, null);
        h.manager.removeDynamicObject(ship);

        assertNull(flame.recreateForRewind(flameContext));
        assertNull(cameraChild.recreateForRewind(cameraContext));
    }

    @Test
    void overlappingShipsRestoreExactInventoriesAndChildOwners() {
        Harness h = new Harness();
        var first = h.startRide();
        var second = h.startRide();
        ObjectRefId firstId = h.id(first);
        ObjectRefId secondId = h.id(second);
        List<ObjectRefId> firstChildren = first.spawnedChildrenForTest().stream().map(h::id).toList();
        List<ObjectRefId> secondChildren = second.spawnedChildrenForTest().stream().map(h::id).toList();
        CompositeSnapshot snapshot = h.registry.capture();

        h.registry.restore(snapshot);

        var restoredFirst = (Lbz2RobotnikShipInstance) h.byId(firstId);
        var restoredSecond = (Lbz2RobotnikShipInstance) h.byId(secondId);
        assertEquals(firstChildren, restoredFirst.spawnedChildrenForTest().stream().map(h::id).toList());
        assertEquals(secondChildren, restoredSecond.spawnedChildrenForTest().stream().map(h::id).toList());
        for (int index = 0; index < 2; index++) {
            assertNotSame(first.spawnedChildrenForTest().get(index),
                    restoredFirst.spawnedChildrenForTest().get(index));
            assertNotSame(second.spawnedChildrenForTest().get(index),
                    restoredSecond.spawnedChildrenForTest().get(index));
        }
        var secondFlame = (Lbz2RobotnikShipInstance.ExhaustFlameChild) h.byId(secondChildren.get(1));
        restoredSecond.setRideRightForTest(0x200);
        restoredSecond.update(2, null);
        secondFlame.update(2, null);
        assertEquals(restoredSecond.getCentreX() - 0x1E, secondFlame.getCentreX());
        assertEquals(restoredSecond.getCentreY(), secondFlame.getCentreY());
        restoredFirst.forceOffscreenForTest();
        restoredFirst.update(3, null);
        var firstCamera = (Lbz2RobotnikShipInstance.GradualCameraMaxXChild) h.byId(firstChildren.get(0));
        var secondCamera = (Lbz2RobotnikShipInstance.GradualCameraMaxXChild) h.byId(secondChildren.get(0));
        firstCamera.update(4, null);
        secondCamera.update(4, null);
        assertTrue(firstCamera.isDestroyed());
        assertFalse(secondCamera.isDestroyed());
        assertEquals(2, restoredSecond.spawnedChildrenForTest().size());
    }

    private record Lifecycle(String action, ObjectInstance object, int slot) { }
    private record ChildState(String type, ObjectRefId id, int slot) { }
    private record Outcome(List<ChildState> allocated, List<String> lifecycle, int cameraMaxX) { }

    private static final class RecordingManager extends ObjectManager {
        final List<Lifecycle> lifecycle = new ArrayList<>();

        RecordingManager(Camera camera, ObjectServices services) {
            super(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
        }

        @Override
        public void removeDynamicObject(ObjectInstance object) {
            lifecycle.add(new Lifecycle("remove", object, slot(object)));
            super.removeDynamicObject(object);
        }

        @Override
        public void addDynamicObject(ObjectInstance object) {
            lifecycle.add(new Lifecycle("add", object, slot(object)));
            super.addDynamicObject(object);
        }

        @Override
        public void addDynamicObjectNextFrame(ObjectInstance object) {
            lifecycle.add(new Lifecycle("add-next", object, slot(object)));
            super.addDynamicObjectNextFrame(object);
        }
    }

    private static final class Harness {
        final Camera camera = new Camera();
        final RecordingManager manager;
        final RewindRegistry registry = new RewindRegistry();
        final ObjectServices services;

        Harness() {
            services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return manager; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            };
            manager = new RecordingManager(camera, services);
            manager.reset(0);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            camera.setMaxX((short) 0x3AB8);
            registry.register(manager.rewindSnapshottable());
            registry.register(camera);
        }

        Lbz2RobotnikShipInstance ship(int x) {
            return manager.createDynamicObject(() -> new Lbz2RobotnikShipInstance(
                    new ObjectSpawn(x, 0x640, 0xC6, 0, 0, false, 0)));
        }

        Lbz2RobotnikShipInstance startRide() {
            var ship = ship(0x443F);
            var player = new TestablePlayableSprite("sonic", (short) 0x443F, (short) 0x640);
            ship.onTouchResponse(player,
                    new TouchResponseResult(0x0A, 0x10, 8, TouchCategory.SPECIAL), 0);
            ship.update(0, player);
            ship.setRideRightForTest(0x100);
            ship.update(1, player);
            assertFalse(player.isObjectControlled());
            return ship;
        }

        Outcome release(Lbz2RobotnikShipInstance ship,
                        Lbz2RobotnikShipInstance.ExhaustFlameChild flame,
                        Lbz2RobotnikShipInstance.GradualCameraMaxXChild cameraChild,
                        int bossSlot, int flameSlot) {
            manager.lifecycle.clear();
            ship.forceOffscreenForTest();
            ship.update(2, null);
            assertTrue(ship.isDestroyed());
            assertEquals("remove", manager.lifecycle.getFirst().action(),
                    "the flame must retire before AllocateObject sees the free slots");
            assertSame(flame, manager.lifecycle.getFirst().object());
            assertEquals(flameSlot, manager.lifecycle.getFirst().slot());
            assertEquals(1, manager.lifecycle.stream()
                    .filter(event -> event.action().equals("remove") && event.object() == flame).count());
            assertFalse(manager.getActiveObjects().contains(flame));
            var boss = only(LbzFinalBoss1Instance.class);
            assertEquals(bossSlot, boss.getSlotIndex());
            var fade = only(SongFadeTransitionInstance.class);
            assertEquals(cameraChild == null ? flameSlot - 1 : flameSlot, fade.getSlotIndex(),
                    "the music fade must reuse the flame slot while the ship slot remains occupied");
            assertEquals(cameraChild == null ? flameSlot : flameSlot + 1,
                    manager.activeObjectsOfType(LbzFinalBoss1Instance.RobotnikHeadChild.class)
                            .getFirst().getSlotIndex());
            if (cameraChild != null) {
                cameraChild.update(3, null);
                assertTrue(cameraChild.isDestroyed());
                manager.removeDynamicObject(cameraChild);
            }
            assertEquals(List.of(boss), ship.spawnedChildrenForTest());
            manager.removeDynamicObject(ship);
            List<ChildState> allocated = states();
            List<String> calls = manager.lifecycle.stream()
                    .map(event -> event.action() + " " + event.object().getClass().getName() + " " + event.slot())
                    .toList();
            return new Outcome(allocated, calls, camera.getMaxX() & 0xFFFF);
        }

        List<ChildState> states() {
            return manager.getActiveObjects().stream()
                    .sorted(Comparator.comparingInt(TestLbz2RobotnikShipRewind::slot))
                    .map(object -> new ChildState(object.getClass().getName(), id(object), slot(object)))
                    .toList();
        }

        ObjectInstance byId(ObjectRefId id) {
            return manager.getActiveObjects().stream().filter(object -> id(object).equals(id))
                    .findFirst().orElseThrow();
        }

        ObjectRefId id(ObjectInstance object) {
            ObjectRefId id = manager.captureIdentityContext().requireIdentityTable().idFor(object);
            assertNotNull(id);
            return id;
        }

        <T extends ObjectInstance> T only(Class<T> type) {
            List<T> objects = manager.activeObjectsOfType(type);
            assertEquals(1, objects.size(), type.getSimpleName());
            return objects.getFirst();
        }
    }

    private static int slot(ObjectInstance object) {
        return ((AbstractObjectInstance) object).getSlotIndex();
    }
}
