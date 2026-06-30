package com.openggf.level.objects;

/**
 * Shared structural relink helpers for {@link RewindRecreatable} objects whose
 * constructors need an already-restored parent or sibling object.
 */
public final class RewindRecreateObjectLinks {
    private RewindRecreateObjectLinks() {
    }

    public static <T extends ObjectInstance> T nearestLiveObject(
            RewindRecreateContext ctx,
            Class<T> type) {
        if (ctx == null || type == null) {
            return null;
        }
        ObjectServices services = ctx.objectServices();
        ObjectManager objectManager = services != null ? services.objectManager() : null;
        if (objectManager == null) {
            return null;
        }
        ObjectSpawn spawn = ctx.spawn();
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (!type.isInstance(object) || object.isDestroyed()) {
                continue;
            }
            T candidate = type.cast(object);
            if (spawn == null) {
                return candidate;
            }
            long dx = candidate.getX() - spawn.x();
            long dy = candidate.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
