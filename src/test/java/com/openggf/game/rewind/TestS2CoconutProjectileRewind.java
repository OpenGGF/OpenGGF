package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.objects.badniks.Sonic2CoconutProjectileInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Obj98 child motion must survive destructive restoration, not just its display tuple. */
class TestS2CoconutProjectileRewind {
    @Test
    void destructiveRestoreReplaysSubpixelMotionGravityAndNativeDisplay() {
        var graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(0x100, 0x100, 0x240, 0x1E0, 0);
        try {
            var manager = new ObjectManager(List.of(), null, 0, null, null, graphics,
                    mock(Camera.class), new StubObjectServices());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            var spawn = new ObjectSpawn(0x180, 0x128, 0x98, 0, 0, false, 0);
            var original = manager.createDynamicObject(() -> new Sonic2CoconutProjectileInstance(
                    spawn, 0x180, 0x128, -0x183, -0x279, true, false));
            for (int i = 0; i < 3; i++) original.update(i, null);
            assertFalse(original.isDestroyed());
            var display = original.nativeDisplaySlot();
            assertNotNull(display);
            var capturedSpawn = original.getSpawn();
            var registry = new RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            var snapshot = registry.capture();
            var identity = manager.captureIdentityContext().requireIdentityTable().idFor(original);
            int slot = original.getSlotIndex();
            manager.removeDynamicObject(original);
            registry.restore(snapshot);
            var restored = (Sonic2CoconutProjectileInstance) manager.getActiveObjects().iterator().next();
            assertNotSame(original, restored);
            assertEquals(identity, manager.captureIdentityContext().requireIdentityTable().idFor(restored));
            assertEquals(slot, restored.getSlotIndex());
            assertEquals(capturedSpawn, restored.getSpawn(), "projectile spawn reports its current position");
            assertEquals(display, restored.nativeDisplaySlot());
            for (int i = 3; i < 15; i++) {
                original.update(i, null);
                restored.update(i, null);
                assertEquals(original.getX(), restored.getX(), "subpixel X at " + i);
                assertEquals(original.getY(), restored.getY(), "gravity Y at " + i);
                assertEquals(original.nativeDisplaySlot(), restored.nativeDisplaySlot());
                assertFalse(original.isDestroyed());
                assertFalse(restored.isDestroyed());
            }
        } finally {
            graphics.resetState();
            com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }
}
