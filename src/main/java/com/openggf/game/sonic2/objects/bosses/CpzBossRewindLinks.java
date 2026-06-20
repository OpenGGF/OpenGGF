package com.openggf.game.sonic2.objects.bosses;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

final class CpzBossRewindLinks {
    private CpzBossRewindLinks() {
    }

    static Sonic2CPZBossInstance nearestBoss(RewindRecreateContext ctx) {
        return nearest(ctx, Sonic2CPZBossInstance.class);
    }

    static CPZBossContainer nearestContainer(RewindRecreateContext ctx) {
        return nearest(ctx, CPZBossContainer.class);
    }

    static CPZBossPipe nearestPipe(RewindRecreateContext ctx) {
        return nearest(ctx, CPZBossPipe.class);
    }

    private static <T extends ObjectInstance> T nearest(
            RewindRecreateContext ctx,
            Class<T> type) {
        if (ctx.objectServices() == null || ctx.objectServices().objectManager() == null) {
            return null;
        }
        ObjectManager objectManager = ctx.objectServices().objectManager();
        ObjectSpawn spawn = ctx.spawn();
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (!type.isInstance(object) || object.isDestroyed()) {
                continue;
            }
            long dx = object.getX() - spawn.x();
            long dy = object.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = type.cast(object);
            }
        }
        return best;
    }
}
