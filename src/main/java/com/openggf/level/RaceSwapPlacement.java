package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.Sprite;
import java.util.function.Consumer;

/** Rebuilds camera-relative placement after the race owner reconstructs the actor. */
final class RaceSwapPlacement {
    private RaceSwapPlacement() { }
    static void reinitialize(Camera camera, ObjectManager objectManager, RingManager ringManager,
            SpriteManager spriteManager, Consumer<Sprite> reregister) {
        var player = camera.getFocusedSprite();
        var objects = objectManager == null ? null : objectManager.capturePersistentRespawn();
        var rings = ringManager == null ? null : ringManager.captureRingStatusTable();
        camera.setFrozen(false);
        camera.setFocusedSprite(player);
        camera.updatePosition(true);
        if (objectManager != null) objectManager.reset(camera.getX(),objects);
        if (ringManager != null) {
            ringManager.reset(camera.getX());
            ringManager.restoreRingStatusTable(rings);
            com.openggf.level.rings.RingDrawInternalAccess.bindNativeSlots(ringManager);
        }
        reregister.accept(player);
        for (var sidekick : spriteManager.getSidekicks()) reregister.accept(sidekick);
    }
}
