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
 * {@link #capturedParent(Class)} expose its captured owner. The lifecycle supplies
 * its restore-table result, including authoritative absence, without relying on
 * live membership. A parent-dependent hook must not substitute a nearby object.
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
        ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry,
        ObjectInstance resolvedParent,
        boolean parentResolutionProvided) {

    public RewindRecreateContext {
        if (!parentResolutionProvided && resolvedParent != null) {
            throw new IllegalArgumentException("A resolved parent requires an authoritative resolution");
        }
        var family = dynamicEntry == null ? null : dynamicEntry.constructionFamily();
        if (resolvedParent != null && (family == null || !family.known() || family.parentId() == null)) {
            throw new IllegalArgumentException("A resolved parent requires a known captured parent identity");
        }
    }

    /** Preserve the former canonical constructor and its lazy exact-ID lookup. */
    public RewindRecreateContext(
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state,
            ObjectServices objectServices,
            ObjectManager objectManager,
            ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry) {
        this(spawn, state, objectServices, objectManager, dynamicEntry, null, false);
    }

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

    /** The restore-table answer is authoritative even when {@code parent} is null. */
    public static RewindRecreateContext withResolvedParent(
            ObjectSpawn spawn,
            PerObjectRewindSnapshot state,
            ObjectServices objectServices,
            ObjectManager objectManager,
            ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry,
            ObjectInstance parent) {
        return new RewindRecreateContext(spawn, state, objectServices, objectManager, dynamicEntry, parent, true);
    }

    /** Unknown and legacy families retain the concrete hook's compatibility path. */
    public boolean hasKnownConstructionFamily() {
        return dynamicEntry != null && dynamicEntry.constructionFamily() != null
                && dynamicEntry.constructionFamily().known();
    }

    /**
     * Returns the exact captured parent of the requested type, or null for known
     * absence, missing identity or a different type. Never selects a nearby owner
     * or filters a destroyed instance whose fields have not yet been restored.
     *
     * <p>Old constructor callers have no restore-table answer. Only those callers
     * retain the lazy manager/services exact-ID lookup; supplied null never retries it.
     */
    public <T extends ObjectInstance> T capturedParent(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!hasKnownConstructionFamily() || dynamicEntry.constructionFamily().parentId() == null) {
            return null;
        }
        ObjectInstance parent = resolvedParent;
        if (!parentResolutionProvided) {
            ObjectManager manager = objectManager;
            if (manager == null && objectServices != null) {
                manager = objectServices.objectManager();
            }
            if (manager == null) {
                return null;
            }
            parent = manager.objectQuery().resolve(dynamicEntry.constructionFamily().parentId()).orElse(null);
        }
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
