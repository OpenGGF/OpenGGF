package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

final class Sonic2BadnikChildRewindLinks {
    private Sonic2BadnikChildRewindLinks() {
    }

    static BalkiryBadnikInstance nearestBalkiry(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, BalkiryBadnikInstance.class);
    }

    static RexonBadnikInstance nearestRexon(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, RexonBadnikInstance.class);
    }

    static ShellcrackerBadnikInstance nearestShellcracker(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, ShellcrackerBadnikInstance.class);
    }

    static SlicerBadnikInstance nearestSlicer(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, SlicerBadnikInstance.class);
    }

    static SolBadnikInstance nearestSol(RewindRecreateContext ctx) {
        return nearestLiveParent(ctx, SolBadnikInstance.class);
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
