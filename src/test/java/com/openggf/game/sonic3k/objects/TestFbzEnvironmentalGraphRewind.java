package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEnvironmentalGraphRewind {
    @BeforeEach
    void init() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }

    @AfterEach
    void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void pendulumForcedReconstructionRestoresThreeExactSlotsAndRelinksWithoutDuplicates() {
        assertPendulumRestore(false);
    }

    @Test
    void pendulumInPlaceRestoreRelinksWithoutDuplicates() {
        assertPendulumRestore(true);
    }

    @Test
    void platformChainRestoresAndRelinksInBothRestoreModesWithoutDuplicates() {
        assertPlatformChainRestore(false);
        assertPlatformChainRestore(true);
    }

    @Test
    void spiderCompanionRestoresAndRelinksInBothRestoreModesWithoutDuplicates() throws Exception {
        assertSpiderCompanionRestore(false);
        assertSpiderCompanionRestore(true);
    }

    private void assertPlatformChainRestore(boolean inPlace) {
        Harness harness = harness(inPlace);
        try (var terrain = org.mockito.Mockito.mockStatic(
                com.openggf.physics.ObjectTerrainUtils.class)) {
        terrain.when(() -> com.openggf.physics.ObjectTerrainUtils.checkFloorDist(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(com.openggf.physics.TerrainCheckResult.noCollision());
        FbzMagneticPlatformObjectInstance platform = harness.manager().createDynamicObject(
                () -> new FbzMagneticPlatformObjectInstance(new ObjectSpawn(
                        0x1000, 0x700, Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM,
                        0x0F, 0, false, 0, 138)));
        platform.update(1, null);
        FbzMagneticPlatformChainObjectInstance chain =
                live(harness.manager(), FbzMagneticPlatformChainObjectInstance.class).getFirst();
        int platformSlot = platform.getSlotIndex();
        int chainSlot = chain.getSlotIndex();

        RewindRegistry registry = registry(harness.manager());
        CompositeSnapshot snapshot = registry.capture();
        harness.manager().removeDynamicObject(chain);
        registry.restore(snapshot);

        FbzMagneticPlatformObjectInstance restoredPlatform =
                live(harness.manager(), FbzMagneticPlatformObjectInstance.class).getFirst();
        FbzMagneticPlatformChainObjectInstance restoredChain =
                live(harness.manager(), FbzMagneticPlatformChainObjectInstance.class).getFirst();
        assertEquals(platformSlot, restoredPlatform.getSlotIndex());
        assertEquals(chainSlot, restoredChain.getSlotIndex());
        assertSame(restoredPlatform, restoredChain.parentMember());
        restoredPlatform.update(2, null);
        assertEquals(2, liveAll(harness.manager()).size());
        }
    }

    private void assertSpiderCompanionRestore(boolean inPlace) throws Exception {
        Harness harness = harness(inPlace);
        FbzSpiderCraneObjectInstance crane = harness.manager().createDynamicObject(
                () -> new FbzSpiderCraneObjectInstance(new ObjectSpawn(
                        0x1000, 0x700, Sonic3kObjectIds.FBZ_SPIDER_CRANE,
                        0x2C, 0, false, 0, 139)));
        var stateField = FbzSpiderCraneObjectInstance.class.getDeclaredField("state");
        stateField.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object captureState = Enum.valueOf((Class) stateField.getType(), "CAPTURE");
        stateField.set(crane, captureState);
        crane.update(1, null);
        FbzSpiderCraneCompanionObjectInstance companion =
                live(harness.manager(), FbzSpiderCraneCompanionObjectInstance.class).getFirst();
        int craneSlot = crane.getSlotIndex();
        int companionSlot = companion.getSlotIndex();

        RewindRegistry registry = registry(harness.manager());
        CompositeSnapshot snapshot = registry.capture();
        harness.manager().removeDynamicObject(companion);
        registry.restore(snapshot);

        FbzSpiderCraneObjectInstance restoredCrane =
                live(harness.manager(), FbzSpiderCraneObjectInstance.class).getFirst();
        FbzSpiderCraneCompanionObjectInstance restoredCompanion =
                live(harness.manager(), FbzSpiderCraneCompanionObjectInstance.class).getFirst();
        assertEquals(craneSlot, restoredCrane.getSlotIndex());
        assertEquals(companionSlot, restoredCompanion.getSlotIndex());
        assertSame(restoredCrane, restoredCompanion.ownerMember());
        assertSame(restoredCompanion, restoredCrane.companionMember());
        restoredCrane.update(2, null);
        assertEquals(2, liveAll(harness.manager()).size());
    }

    private void assertPendulumRestore(boolean inPlace) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = camera();
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public com.openggf.game.zone.ZoneRuntimeState zoneRuntimeState() { return runtime; }
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(() -> null, List::of);
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        if (!inPlace) manager.setRewindInPlaceRestoreEnabledForTest(false);

        ObjectSpawn spawn = new ObjectSpawn(0x1000, 0x700,
                Sonic3kObjectIds.FBZ_MAGNETIC_PENDULUM, 0, 0, false, 0, 137);
        FbzMagneticPendulumObjectInstance pivot = manager.createDynamicObject(
                () -> new FbzMagneticPendulumObjectInstance(spawn));
        pivot.update(1, null);
        FbzMagneticPendulumEndpointObjectInstance endpoint =
                live(manager, FbzMagneticPendulumEndpointObjectInstance.class).getFirst();
        endpoint.update(1, null);
        FbzMagneticPendulumChainObjectInstance chain =
                live(manager, FbzMagneticPendulumChainObjectInstance.class).getFirst();
        int pivotSlot = pivot.getSlotIndex();
        int endpointSlot = endpoint.getSlotIndex();
        int chainSlot = chain.getSlotIndex();

        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();
        for (ObjectInstance object : new ArrayList<>(manager.getActiveObjects())) {
            if (object != pivot) manager.removeDynamicObject(object);
        }
        registry.restore(snapshot);

        FbzMagneticPendulumObjectInstance restoredPivot =
                live(manager, FbzMagneticPendulumObjectInstance.class).getFirst();
        FbzMagneticPendulumEndpointObjectInstance restoredEndpoint =
                live(manager, FbzMagneticPendulumEndpointObjectInstance.class).getFirst();
        FbzMagneticPendulumChainObjectInstance restoredChain =
                live(manager, FbzMagneticPendulumChainObjectInstance.class).getFirst();
        assertEquals(pivotSlot, restoredPivot.getSlotIndex());
        assertEquals(endpointSlot, restoredEndpoint.getSlotIndex());
        assertEquals(chainSlot, restoredChain.getSlotIndex());
        assertSame(restoredPivot, restoredEndpoint.parentMember());
        assertSame(restoredPivot, restoredChain.parentMember());
        assertSame(restoredEndpoint, restoredChain.endpointMember());
        assertSame(restoredChain, restoredEndpoint.chainMember());

        restoredPivot.update(2, null);
        restoredEndpoint.update(2, null);
        assertEquals(3, liveAll(manager).size());
        restoredPivot.cascadeDelete();
        restoredEndpoint.update(3, null);
        restoredChain.update(3, null);
        assertTrue(restoredPivot.isDestroyed());
        assertTrue(restoredEndpoint.isDestroyed());
        assertTrue(restoredChain.isDestroyed());
    }

    private static Camera camera() {
        return new Camera() {
            @Override public short getX() { return 0x0E00; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x4000; }
            @Override public short getHeight() { return 0x1000; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static RewindRegistry registry(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        return registry;
    }

    private static Harness harness(boolean inPlace) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = camera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(() -> null, List::of);
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        if (!inPlace) manager.setRewindInPlaceRestoreEnabledForTest(false);
        return new Harness(manager);
    }

    private record Harness(ObjectManager manager) { }

    private static List<ObjectInstance> liveAll(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(object -> !object.isDestroyed()).toList();
    }

    private static <T extends ObjectInstance> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream()
                .filter(object -> type.isInstance(object) && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }
}
