package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Derived population index, invalidated on membership/slot changes and rewind adoption. */
final class NativeObjectIndex {
    // Primitive slot/index pairs retain duplicate occupants in stable population order,
    // without rebuilding a map node, list and backing array for every occupied SST.
    private long[] slots = new long[0];
    private int slotCount;
    private boolean slotsDirty = true;
    private final List<ObjectInstance> population = new ArrayList<>();
    // One requested type at a time: no retained creator Class keys or unbounded type cache.
    private final List<ObjectInstance> typed = new ArrayList<>();
    private ObjectCallbackRouter callbacks;
    private Class<?> type;
    private boolean dirty = true;

    void invalidate() { dirty = true; slotsDirty = true; }

    /** Reset/resync must release owners and creator classes even without a later query. */
    void clear() {
        population.clear();
        typed.clear();
        type = null;
        callbacks = null;
        slotCount = 0;
        invalidate();
    }

    void prepare(Collection<ObjectInstance> placed, Collection<ObjectInstance> dynamic,
                 ObjectCallbackRouter callbacks) {
        if (!dirty) return;
        population.clear();
        // Copy before invoking routed callbacks: they may retire a population member.
        for (ObjectInstance instance : placed) population.add(instance);
        for (ObjectInstance instance : dynamic) population.add(instance);
        typed.clear();
        type = null;
        this.callbacks = callbacks;
        slotsDirty = true;
        dirty = false;
    }

    private void prepareSlots() {
        if (!slotsDirty) return;
        if (slots.length < population.size()) {
            slots = new long[Math.max(population.size(), slots.length * 2)];
        }
        slotCount = 0;
        slotsDirty = false;
        for (int i = 0; i < population.size(); i++) {
            ObjectInstance instance = population.get(i);
            if (instance instanceof AbstractObjectInstance object) {
                int slot = callbacks.call(instance, object::getSlotIndex);
                if (slot >= 0) slots[slotCount++] = ((long) slot << 32) | (i & 0xFFFFFFFFL);
            }
        }
        Arrays.sort(slots, 0, slotCount);
    }

    private int firstSlotEntry(int slot) {
        prepareSlots();
        int low = 0, high = slotCount;
        long key = (long) slot << 32;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (slots[mid] < key) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    <T extends ObjectInstance> T atSlot(int slot, Class<T> type) {
        return atSlot(slot, type, 0);
    }

    <T extends ObjectInstance> T atSlot(int slot, Class<T> type, int occurrence) {
        for (int i = firstSlotEntry(slot); i < slotCount && (int) (slots[i] >>> 32) == slot; i++) {
            ObjectInstance instance = population.get((int) slots[i]);
            if (type.isInstance(instance) && occurrence-- == 0) return type.cast(instance);
        }
        return null;
    }

    <T extends ObjectInstance> T liveAtSlot(int slot, Class<T> type) {
        for (int i = firstSlotEntry(slot); i < slotCount && (int) (slots[i] >>> 32) == slot; i++) {
            ObjectInstance instance = population.get((int) slots[i]);
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
        for (int i = 0; i < typed.size(); i++) {
            ObjectInstance instance = typed.get(i);
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
