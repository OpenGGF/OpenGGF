package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Owns non-rewindable presentation objects derived from a creator's current visual model.
 * Call {@link #synchronize} at a presentation boundary, including while gameplay is held.
 * Reconciliation detects checkpoint removal and level-manager replacement without requiring
 * the creator to maintain restore generations. It never restores or changes the visual model.
 * Close at session teardown. This collection is session state, not a rewind adapter.
 * Checkpoint restore can drop an auxiliary without an unload callback; keep external
 * resources on the session owner rather than on these disposable presentation objects.
 */
@ModApi
public final class AuxiliaryObjectCollection<T> implements AutoCloseable {
    private record Owned(ObjectManager manager, ObjectInstance object) { }
    private final Map<T, Owned> objects = new IdentityHashMap<>();

    public AuxiliaryObjectCollection() { }

    /**
     * Retains one auxiliary object per desired model identity. The factory only constructs;
     * this collection supplies construction services and registers the returned object.
     * Desired values must be non-null and identity-distinct. Objects must be presentation-only:
     * they consume no native slots and are deliberately excluded from rewind snapshots.
     */
    public void synchronize(ObjectServices services, Collection<? extends T> desired,
                            BiFunction<ObjectServices, T, ? extends ObjectInstance> factory) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(factory, "factory");
        Set<T> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T value : desired) {
            if (!wanted.add(Objects.requireNonNull(value, "visual")))
                throw new IllegalArgumentException("Duplicate visual identity");
        }
        if (ObjectConstructionContext.isProbeConstruction()) return;
        ObjectManager manager = services.objectManager();
        // A single population scan, rather than a full scan for each visual.
        Set<ObjectInstance> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(manager.getActiveObjects());
        var iterator = objects.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Owned owned = entry.getValue();
            if (wanted.contains(entry.getKey()) && owned.manager() == manager
                    && live.contains(owned.object()) && !owned.object().isDestroyed()) continue;
            owned.manager().removeDynamicObject(owned.object());
            iterator.remove();
        }
        for (T value : desired) {
            if (objects.containsKey(value)) continue;
            ObjectInstance object = ObjectConstructionContext.construct(services,
                    () -> Objects.requireNonNull(factory.apply(services, value), "auxiliary object"));
            if (!ObjectConstructionContext.isProbeConstruction()) {
                manager.addAuxiliaryDynamicObject(object);
                objects.put(value, new Owned(manager, object));
            }
        }
    }

    /** Retires only objects installed by this collection, through their original managers. */
    @Override public void close() {
        for (Owned owned : objects.values()) owned.manager().removeDynamicObject(owned.object());
        objects.clear();
    }
}
