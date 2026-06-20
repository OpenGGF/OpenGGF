package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.objects.badniks.GrounderBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

final class Sonic2GrounderChildRewindLinks {
    private Sonic2GrounderChildRewindLinks() {
    }

    static GrounderBadnikInstance nearestGrounder(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, GrounderBadnikInstance.class);
    }

    private static <T extends ObjectInstance> T nearestLiveParent(
            RewindRecreateContext ctx,
            Class<T> parentType) {
        ObjectServices services = ctx.objectServices();
        ObjectManager objectManager = services != null ? services.objectManager() : null;
        if (objectManager == null) {
            return null;
        }
        ObjectSpawn spawn = ctx.spawn();
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (!parentType.isInstance(object) || object.isDestroyed()) {
                continue;
            }
            T parent = parentType.cast(object);
            if (spawn == null) {
                return parent;
            }
            long dx = parent.getX() - spawn.x();
            long dy = parent.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = parent;
            }
        }
        return best;
    }
}
