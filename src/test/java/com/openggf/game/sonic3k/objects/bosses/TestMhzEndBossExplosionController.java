package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMhzEndBossExplosionController {
    @Test void freshRecreationKeepsTheFollowingParentAndPendingRetirement() {
        com.openggf.graphics.GraphicsManager.getInstance().initHeadless();
        try {
            var services = new StubObjectServices().withIsolatedObjectManager();
            var manager = services.objectManager();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            var parent = manager.createDynamicObject(() -> new MhzEndBossInstance(
                    new ObjectSpawn(0x4200, 0x2E0, 0x93, 0, 0, false, 0)));
            var controller = manager.createDynamicObject(() -> MhzEndBossExplosionController.following(parent));
            controller.update(0, null);
            parent.getState().y += 5;
            parent.getState().yFixed += 5 << 16;
            controller.update(1, null);
            var registry = new com.openggf.game.rewind.RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            var saved = registry.capture();
            parent.setCustomFlag(0x38, 0x20);
            controller.update(1, null);
            controller.update(2, null);
            assertTrue(controller.isDestroyed());
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(saved);
                var restoredParent = manager.getActiveObjects().stream().filter(MhzEndBossInstance.class::isInstance)
                        .map(MhzEndBossInstance.class::cast).findFirst().orElseThrow();
                var restored = manager.getActiveObjects().stream().filter(MhzEndBossExplosionController.class::isInstance)
                        .map(MhzEndBossExplosionController.class::cast).findFirst().orElseThrow();
                assertNotSame(controller, restored);
                var restoredSnapshot = registry.capture();
                for (String key : saved.entries().keySet()) {
                    assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                            key, saved.get(key), restoredSnapshot.get(key)), key);
                }
                restoredParent.setCustomFlag(0x38, 0x20);
                restored.update(1, null);
                restored.update(2, null);
                assertTrue(restored.isDestroyed(), "bit5 must be read from the recreated parent");
                var actual = registry.capture();
                assertEquals(expected.entries().keySet(), actual.entries().keySet());
                for (String key : expected.entries().keySet()) {
                    assertEquals(java.util.List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                            key, expected.get(key), actual.get(key)), key);
                }
            }
        } finally {
            com.openggf.graphics.GraphicsManager.getInstance().resetState();
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
