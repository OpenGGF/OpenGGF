package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Derived population index, discarded on membership/slot changes and rewind adoption. */
final class NativeObjectIndex {
    private final Map<Integer, List<ObjectInstance>> slots = new HashMap<>();
    // One requested type at a time: no retained creator Class keys or unbounded type cache.
    private final List<ObjectInstance> typed = new ArrayList<>();
    private Collection<ObjectInstance> population;
    private ObjectCallbackRouter callbacks;
    private Class<?> type;
    private boolean dirty = true;

    void invalidate() { dirty = true; }

    void prepare(Collection<ObjectInstance> population, ObjectCallbackRouter callbacks) {
        if (!dirty) return;
        slots.clear();
        typed.clear();
        type = null;
        this.population = population;
        this.callbacks = callbacks;
        for (ObjectInstance instance : population) {
            if (instance instanceof AbstractObjectInstance object) {
                int slot = callbacks.call(instance, object::getSlotIndex);
                if (slot >= 0) slots.computeIfAbsent(slot, key -> new ArrayList<>(1)).add(instance);
            }
        }
        dirty = false;
    }

    <T extends ObjectInstance> T liveAtSlot(int slot, Class<T> type) {
        var occupants = slots.get(slot);
        if (occupants == null) return null;
        for (ObjectInstance instance : occupants) {
            if (type.isInstance(instance) && !callbacks.call(instance, instance::isDestroyed))
                return type.cast(instance);
        }
        return null;
    }

    <T extends ObjectInstance> T firstLive(Class<T> requestedType) {
        if (type != requestedType) {
            typed.clear();
            for (ObjectInstance instance : population) if (requestedType.isInstance(instance)) typed.add(instance);
            type = requestedType;
        }
        T first = null;
        int firstSlot = Integer.MAX_VALUE;
        for (ObjectInstance instance : typed) {
            if (callbacks.call(instance, instance::isDestroyed)) continue;
            int slot = instance instanceof AbstractObjectInstance object
                    ? callbacks.call(instance, object::getSlotIndex) : Integer.MAX_VALUE;
            if (first == null || slot < firstSlot) {
                first = requestedType.cast(instance);
                firstSlot = slot;
            }
        }
        return first;
    }
}
