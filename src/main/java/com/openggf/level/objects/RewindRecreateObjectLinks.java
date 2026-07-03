package com.openggf.level.objects;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Shared structural relink helpers for {@link RewindRecreatable} objects whose
 * constructors need an already-restored parent or sibling object.
 *
 * <p><strong>Absence is legitimate.</strong> During a rewind restore the recreate
 * loop rebuilds captured objects in capture order; the {@code destroyed} flag is a
 * scalar restored in a later pass, so a captured-but-destroyed link target still
 * presents as {@code isDestroyed() == false} while recreate runs. A relink lookup
 * therefore returns empty only when the target was genuinely swept before capture
 * (player-destroyed and self-expired, off-screen-culled) or was not captured at all.
 * When the player breaks a linked object and then rewinds, the child's true link
 * target can be that destroyed object, whose captured state (e.g. a cascade flag)
 * is load-bearing — so prefer {@code acceptDestroyed = true} to relink to the
 * restored (possibly destroyed) instance rather than dropping the link.
 *
 * <p>The {@link Optional}-returning {@link #nearestObject} methods force call sites
 * to handle absence at the type level (the recreate should return {@code null} to
 * drop the child, mirroring how the object's live {@code update()} self-expires when
 * its parent is gone). The legacy nullable {@link #nearestLiveObject} is retained,
 * delegating to {@link #nearestObject}, for existing tolerant call sites.
 */
public final class RewindRecreateObjectLinks {
    private RewindRecreateObjectLinks() {
    }

    /**
     * Finds the restored link target of {@code type} nearest to the recreating
     * object's captured spawn.
     *
     * @param acceptDestroyed when {@code true}, a restored target already flagged
     *                        destroyed is still eligible (relink to the destroyed
     *                        instance, preserving its load-bearing captured state);
     *                        when {@code false}, destroyed targets are skipped
     * @return the nearest matching restored instance, or {@link Optional#empty()}
     *         if none is present (a legitimate absent-link state)
     */
    public static <T extends ObjectInstance> Optional<T> nearestObject(
            RewindRecreateContext ctx,
            Class<T> type,
            boolean acceptDestroyed) {
        return nearestObject(ctx, type, acceptDestroyed, candidate -> true);
    }

    /**
     * Variant of {@link #nearestObject(RewindRecreateContext, Class, boolean)} that
     * additionally constrains candidates with {@code filter} (e.g. a parent whose
     * mode/subtype must match the child).
     */
    public static <T extends ObjectInstance> Optional<T> nearestObject(
            RewindRecreateContext ctx,
            Class<T> type,
            boolean acceptDestroyed,
            Predicate<? super T> filter) {
        if (ctx == null || type == null || filter == null) {
            return Optional.empty();
        }
        ObjectManager objectManager = resolveManager(ctx);
        if (objectManager == null) {
            return Optional.empty();
        }
        ObjectSpawn spawn = ctx.spawn();
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (!type.isInstance(object)) {
                continue;
            }
            if (!acceptDestroyed && object.isDestroyed()) {
                continue;
            }
            T candidate = type.cast(object);
            if (!filter.test(candidate)) {
                continue;
            }
            if (spawn == null) {
                return Optional.of(candidate);
            }
            long dx = candidate.getX() - spawn.x();
            long dy = candidate.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Legacy nullable relink lookup, restricted to live (non-destroyed) targets.
     *
     * @deprecated Prefer {@link #nearestObject(RewindRecreateContext, Class, boolean)}
     *     so absence is handled explicitly at the call site. Retained for existing
     *     tolerant call sites that already null-check the result.
     */
    @Deprecated
    public static <T extends ObjectInstance> T nearestLiveObject(
            RewindRecreateContext ctx,
            Class<T> type) {
        return nearestObject(ctx, type, false).orElse(null);
    }

    private static ObjectManager resolveManager(RewindRecreateContext ctx) {
        ObjectManager objectManager = ctx.objectManager();
        if (objectManager == null && ctx.objectServices() != null) {
            objectManager = ctx.objectServices().objectManager();
        }
        return objectManager;
    }
}
