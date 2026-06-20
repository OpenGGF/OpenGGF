package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

final class AizMinibossRewindLinks {
    private static final int[][] BARREL_OFFSETS = {
            {0, -0x20}, {9, -0x1C}, {0x12, -0x18}
    };

    private AizMinibossRewindLinks() {
    }

    static AizMinibossInstance nearestBoss(RewindRecreateContext ctx) {
        return nearest(ctx, AizMinibossInstance.class);
    }

    static AizMinibossFlameBarrelChild nearestBarrel(RewindRecreateContext ctx) {
        return nearest(ctx, AizMinibossFlameBarrelChild.class);
    }

    static AizMinibossBarrelShotChild nearestBarrelShot(RewindRecreateContext ctx) {
        return nearest(ctx, AizMinibossBarrelShotChild.class);
    }

    static int nearestBarrelIndex(RewindRecreateContext ctx, AizMinibossInstance boss) {
        ObjectSpawn spawn = ctx.spawn();
        if (spawn == null || boss == null) {
            return 0;
        }
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < BARREL_OFFSETS.length; index++) {
            int xOffset = BARREL_OFFSETS[index][0];
            int yOffset = BARREL_OFFSETS[index][1];
            long normalDistance = distanceSquared(spawn.x(), spawn.y(),
                    boss.getX() + xOffset, boss.getY() + yOffset);
            long flippedDistance = distanceSquared(spawn.x(), spawn.y(),
                    boss.getX() - xOffset, boss.getY() + yOffset);
            long distance = Math.min(normalDistance, flippedDistance);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static long distanceSquared(int x1, int y1, int x2, int y2) {
        long dx = x1 - x2;
        long dy = y1 - y2;
        return dx * dx + dy * dy;
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
            if (spawn == null) {
                return type.cast(object);
            }
            long distance = distanceSquared(object.getX(), object.getY(), spawn.x(), spawn.y());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = type.cast(object);
            }
        }
        return best;
    }
}
