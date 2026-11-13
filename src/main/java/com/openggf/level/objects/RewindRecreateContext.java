package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

import java.util.Objects;

/**
 * Context passed to {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} during
 * a rewind restore. Exposes the captured spawn, the compact field-state blob, the
 * restore-time object services, the restoring object manager, and the captured dynamic
 * entry when the object was restored from the dynamic-object surface.
 * This is engine reconstruction plumbing; creator implementations use
 * {@link ModRewindRecreatable} and {@link ObjectReconstructionContext}.
 *
 * <p>For an owned child, {@link #hasKnownConstructionFamily()} and
 * {@link #capturedParent(Class)} identify its captured parent in the admitted
 * population. A parent missing from that population is unavailable; a parent-dependent
 * hook must not substitute a nearby object.
 * Legacy or explicitly unknown families retain their class's compatibility path.
 * Family ownership is separate from a sibling or predecessor relationship.
 *
 * <p>Object-reference fields from the compact blob are <em>not</em> available here;
 * they are resolved after recreate returns. Implementations may inspect the
 * restore-time {@link #objectManager()} (or the manager in {@link #objectServices()}) for
 * structural relinks that are constructor-required, but must not attempt to decode
 * compact object-reference fields from this context.
 */
public record RewindRecreateContext(
        ObjectSpawn spawn,
        PerObjectRewindSnapshot state,
        ObjectServices objectServices,
        ObjectManager objectManager,
        ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry) {

    public RewindRecreateContext(
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state,
            ObjectServices objectServices) {
        this(spawn, state, objectServices, null, null);
    }

    public RewindRecreateContext(
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state,
            ObjectServices objectServices,
            ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry) {
        this(spawn, state, objectServices, null, dynamicEntry);
    }

    /** Unknown and legacy families retain the concrete hook's compatibility path. */
    public boolean hasKnownConstructionFamily() {
        return dynamicEntry != null && dynamicEntry.constructionFamily() != null
                && dynamicEntry.constructionFamily().known();
    }

    /**
     * Resolves only the captured identity in the admitted population. Returns null
     * for known absence, missing membership or a different type; never borrows a
     * nearby owner. Do not filter a destroyed flag before captured fields restore.
     */
    public <T extends ObjectInstance> T capturedParent(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!hasKnownConstructionFamily() || dynamicEntry.constructionFamily().parentId() == null) {
            return null;
        }
        ObjectManager manager = objectManager;
        if (manager == null && objectServices != null) {
            manager = objectServices.objectManager();
        }
        if (manager == null) {
            return null;
        }
        ObjectInstance parent = manager.objectQuery()
                .resolve(dynamicEntry.constructionFamily().parentId()).orElse(null);
        return type.isInstance(parent) ? type.cast(parent) : null;
    }

    /**
     * Queues the captured dynamic entry for the post-restore player-bound
     * power-up refresh path. This preserves the old deferred restore behavior:
     * {@code recreateForRewind} returns {@code null}, then the live player
     * refresh asks the power-up spawner to recreate and relink the concrete
     * object using the captured slot and field state.
     */
    public void enqueuePendingPlayerBoundEntry(Class<?> baseType) {
        if (dynamicEntry == null || baseType == null) {
            return;
        }
        ObjectManager manager = objectManager;
        if (manager == null && objectServices != null) {
            manager = objectServices.objectManager();
        }
        if (manager != null) {
            manager.enqueuePendingPlayerBoundEntry(baseType, dynamicEntry);
        }
    }
}
