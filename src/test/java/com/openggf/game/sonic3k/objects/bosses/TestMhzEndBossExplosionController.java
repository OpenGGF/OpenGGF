package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMhzEndBossExplosionController {
    @Test void freshRecreationRebindsExactParentAndReplaysBothBurstKindsTwice() throws Exception {
        com.openggf.graphics.GraphicsManager.getInstance().initHeadless();
        try {
            var services = new StubObjectServices().withIsolatedObjectManager();
            var manager = services.objectManager();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            var parent = manager.createDynamicObject(() -> new MhzEndBossInstance(
                    new ObjectSpawn(0x4200, 0x2E0, 0x93, 0, 0, false, 0)));
            var decoy = manager.createDynamicObject(() -> new MhzEndBossInstance(
                    new ObjectSpawn(0x4201, 0x2E0, 0x93, 0, 0, false, 0)));
            var following = manager.createDynamicObject(() -> MhzEndBossExplosionController.following(parent));
            var stationary = manager.createDynamicObject(() -> MhzEndBossExplosionController.stationary(0x3DA1, 0x2AF));
            following.update(0, null);
            stationary.update(0, null);
            int parentSlot = parent.getSlotIndex();
            int followingSlot = following.getSlotIndex();
            int stationarySlot = stationary.getSlotIndex();
            // Seed the adversarial geometry explicitly, independently of boss spawn offsets.
            int capturedX = following.getX();
            int capturedY = following.getY();
            positionBoss(decoy, capturedX, capturedY);
            positionBoss(parent, (capturedX + 0x1000) & 0xFFFF, capturedY);
            assertEquals(capturedX, following.getX(), "the follower copies position only on its own entry");
            assertEquals(capturedY, following.getY());
            assertEquals(0, Math.abs(decoy.getX() - following.getX()));
            assertTrue(Math.abs(decoy.getX() - following.getX()) < Math.abs(parent.getX() - following.getX()),
                    "the decoy must be closer than the exact captured parent");
            var registry = new com.openggf.game.rewind.RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            var saved = registry.capture();
            advanceBursts(parent, following, stationary);
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(saved);
                var restoredParent = (MhzEndBossInstance) atSlot(manager, parentSlot);
                var restoredFollowing = (MhzEndBossExplosionController) atSlot(manager, followingSlot);
                var restoredStationary = (MhzEndBossExplosionController) atSlot(manager, stationarySlot);
                assertNotSame(parent, restoredParent);
                assertNotSame(following, restoredFollowing);
                assertNotSame(stationary, restoredStationary);
                assertSame(restoredParent, parentOf(restoredFollowing));
                assertNull(parentOf(restoredStationary));
                assertSnapshotsEqual(saved, registry.capture());
                advanceBursts(restoredParent, restoredFollowing, restoredStationary);
                assertSnapshotsEqual(expected, registry.capture());
            }
        } finally {
            com.openggf.graphics.GraphicsManager.getInstance().resetState();
        }
    }

    @Test void pendingStationaryRetirementRecreatesWithoutAnExtraExplosionOrRngRead() {
        com.openggf.graphics.GraphicsManager.getInstance().initHeadless();
        try {
            var services = new StubObjectServices().withIsolatedObjectManager();
            var manager = services.objectManager();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            var controller = manager.createDynamicObject(() -> MhzEndBossExplosionController.stationary(0x3DA1, 0x2AF));
            int slot = controller.getSlotIndex();
            for (int frame = 0; frame <= 93; frame++) controller.update(frame, null);
            assertFalse(controller.isDestroyed(), "native deletion is selected for the next own entry");
            var registry = new com.openggf.game.rewind.RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            var pending = registry.capture();
            controller.update(94, null);
            assertTrue(controller.isDestroyed());
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(pending);
                var restored = (MhzEndBossExplosionController) atSlot(manager, slot);
                assertNotSame(controller, restored);
                assertSnapshotsEqual(pending, registry.capture());
                restored.update(94, null);
                assertTrue(restored.isDestroyed());
                assertSnapshotsEqual(expected, registry.capture());
            }
        } finally {
            com.openggf.graphics.GraphicsManager.getInstance().resetState();
        }
    }

    private static void advanceBursts(MhzEndBossInstance parent, MhzEndBossExplosionController following,
                                      MhzEndBossExplosionController stationary) throws ReflectiveOperationException {
        for (int frame = 1; frame <= 7; frame++) {
            positionBoss(parent, (parent.getX() + 4) & 0xFFFF, parent.getY());
            if (frame == 5) parent.setCustomFlag(0x38, 0x20);
            following.update(frame, null);
            stationary.update(frame, null);
            if (frame < 5) assertEquals(parent.getX(), following.getX(), "live follower copies its exact owner's published position");
        }
        assertTrue(following.isDestroyed(), "only the exact owning boss's bit5 retires the follower");
        assertFalse(stationary.isDestroyed());
    }

    private static void positionBoss(MhzEndBossInstance boss, int x, int y) throws ReflectiveOperationException {
        boss.getState().x = x;
        boss.getState().y = y;
        boss.getState().xFixed = x << 16;
        boss.getState().yFixed = y << 16;
        // Controllers belong to an admitted encounter. Before admission MHZ getX()
        // deliberately exposes placement X rather than the active boss context.
        var admitted = MhzEndBossInstance.class.getDeclaredField("cameraRangePassed");
        admitted.setAccessible(true);
        admitted.setBoolean(boss, true);
        // Publish getSpawn() too, without running unrelated encounter logic.
        var publish = com.openggf.level.objects.boss.AbstractBossInstance.class
                .getDeclaredMethod("updateDynamicSpawn");
        publish.setAccessible(true);
        publish.invoke(boss);
        assertEquals(x, boss.getX());
        assertEquals(y, boss.getY());
    }

    private static ObjectInstance atSlot(ObjectManager manager, int slot) {
        return manager.getActiveObjects().stream()
                .filter(object -> object instanceof AbstractObjectInstance instance && instance.getSlotIndex() == slot)
                .findFirst().orElseThrow();
    }

    private static Object parentOf(MhzEndBossExplosionController controller) throws Exception {
        var field = MhzEndBossExplosionController.class.getDeclaredField("parent");
        field.setAccessible(true);
        return field.get(controller);
    }

    private static void assertSnapshotsEqual(com.openggf.game.rewind.CompositeSnapshot expected,
                                              com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key)), key);
        }
    }

    @Test void stationaryBurstEmits31AttemptsEveryThreeFramesAndConsumesFailedAllocation() {
        var manager = mock(ObjectManager.class);
        var rng = mock(GameRng.class);
        var controller = MhzEndBossExplosionController.stationary(0x3DA1, 0x2AF);
        controller.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public GameRng rng() { return rng; }
        });
        controller.setSlotIndex(7);
        when(manager.allocateSlotAfter(7)).thenReturn(-1, 8);
        when(rng.nextRaw()).thenReturn(0x003F0010);
        controller.update(0, null);
        controller.update(1, null);
        controller.update(2, null);
        verify(manager).allocateSlotAfter(7);
        verifyNoInteractions(rng);
        controller.update(3, null);
        var child = ArgumentCaptor.forClass(S3kBossExplosionChild.class);
        verify(manager).addDynamicObjectAtSlot(child.capture(), eq(8));
        assertEquals(0x3D91, child.getValue().getX());
        assertEquals(0x2CE, child.getValue().getY(), "SWAP uses the high random word");
        assertTrue(child.getValue().nativeInitSfxForTest());

        for (int n = 4; n <= 94; n++) controller.update(n, null);
        verify(manager, times(31)).allocateSlotAfter(7);
        verify(rng, times(30)).nextRaw();
        assertTrue(controller.isDestroyed());
    }

    @Test void followingBurstUsesCurrentParentPositionAndStopsAtBitFiveOnAWaitFrame() {
        var manager = mock(ObjectManager.class);
        var rng = mock(GameRng.class);
        var parent = new MhzEndBossInstance(new ObjectSpawn(0x4200, 0x2E0, 0x93, 0, 0, false, 0));
        var controller = MhzEndBossExplosionController.following(parent);
        controller.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public GameRng rng() { return rng; }
        });
        controller.setSlotIndex(7);
        when(manager.allocateSlotAfter(7)).thenReturn(8);
        when(rng.nextRaw()).thenReturn(0x00200020);
        controller.update(0, null);
        var child = ArgumentCaptor.forClass(S3kBossExplosionChild.class);
        verify(manager).addDynamicObjectAtSlot(child.capture(), eq(8));
        assertEquals(parent.getX(), child.getValue().getX());

        parent.setCustomFlag(0x38, 0x20);
        controller.update(1, null);
        controller.update(2, null);
        assertTrue(controller.isDestroyed());
        verify(manager).allocateSlotAfter(7);
    }
}
