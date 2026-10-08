package com.openggf.sprites.playable;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectCallbackDispatch;
import com.openggf.level.objects.ObjectInstance;

/** Stateless contact bookkeeping; the player owns all captured contact state. */
final class LatchedSolidContactSupport {
    private LatchedSolidContactSupport() { }

    static boolean isReleased(AbstractPlayableSprite player, boolean capturedRelease) {
        if (capturedRelease) {
            return true;
        }
        ObjectInstance instance = player.latchedSolidObjectInstance;
        if (instance == null) {
            return false;
        }
        var level = player.currentLevelManagerIfAvailable();
        var objects = level == null ? null : level.getObjectManager();
        if (objects == null) {
            return instance.isDestroyed();
        }
        return ObjectCallbackDispatch.call(objects, instance, instance::isDestroyed)
                || !objects.getActiveObjects().contains(instance);
    }

    static void setId(AbstractPlayableSprite player, int objectId) {
        player.latchedSolidObjectId = objectId & 0xFF;
        if (player.latchedSolidObjectId == 0) {
            player.latchedSolidObjectInstance = null;
            player.setLatchedSolidObjectBinding(false);
            player.clearLatchedSolidObjectRelease();
        }
    }

    static void setInstance(AbstractPlayableSprite player, ObjectInstance instance) {
        player.latchedSolidObjectInstance = instance;
        player.setLatchedSolidObjectBinding(instance != null);
        if (instance != null) {
            player.clearLatchedSolidObjectRelease();
        }
    }

    static void bind(AbstractPlayableSprite player, int objectId, ObjectInstance instance) {
        player.latchedSolidObjectId = objectId & 0xFF;
        player.latchedSolidObjectInstance = instance;
        player.setLatchedSolidObjectBinding(instance != null);
        player.clearLatchedSolidObjectRelease();
        // ROM RideObject_SetRide writes interact(a1) = slot index of the
        // ridden object (s2.asm:36005-36006). Null/unassigned owners do not
        // replace the persistent slot used by the sidekick despawn comparator.
        if (instance instanceof AbstractObjectInstance object) {
            int slot = object.getSlotIndex();
            if (slot >= 0) {
                player.interactSlotIndex = slot;
            }
        }
    }
}
