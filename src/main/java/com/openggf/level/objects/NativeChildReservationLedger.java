package com.openggf.level.objects;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Mutates the existing placed-owner folded-slot ledger without owning allocator or snapshot state.
 * All collection references are manager-owned final collections, cleared/refilled on reset/restore.
 * Allocation and ascending-execution retirement retain their existing manager authorities.
 */
final class NativeChildReservationLedger {
    private final ObjectCallbackRouter callbacks;
    private final ObjectSlotLayout slotLayout;
    private final Map<ObjectSpawn, int[]> reservedChildSlots;
    private final Map<ObjectInstance, ObjectSpawn> instanceToSpawn;
    private final Map<ObjectSpawn, ObjectInstance> activeObjects;
    private final List<ObjectInstance> dynamicObjects;
    private final Predicate<ObjectInstance> hasRewindIdentity;
    private final Function<ObjectInstance, ObjectSpawn> managedSpawn;
    private final IntUnaryOperator allocateAfter;
    private final IntConsumer retireAtOwnPosition;

    NativeChildReservationLedger(ObjectCallbackRouter callbacks, ObjectSlotLayout slotLayout,
            Map<ObjectSpawn, int[]> reservedChildSlots, Map<ObjectInstance, ObjectSpawn> instanceToSpawn,
            Map<ObjectSpawn, ObjectInstance> activeObjects, List<ObjectInstance> dynamicObjects,
            Predicate<ObjectInstance> hasRewindIdentity, Function<ObjectInstance, ObjectSpawn> managedSpawn,
            IntUnaryOperator allocateAfter, IntConsumer retireAtOwnPosition) {
        this.callbacks = callbacks;
        this.slotLayout = slotLayout;
        this.reservedChildSlots = reservedChildSlots;
        this.instanceToSpawn = instanceToSpawn;
        this.activeObjects = activeObjects;
        this.dynamicObjects = dynamicObjects;
        this.hasRewindIdentity = hasRewindIdentity;
        this.managedSpawn = managedSpawn;
        this.allocateAfter = allocateAfter;
        this.retireAtOwnPosition = retireAtOwnPosition;
    }

    /** Existing FindNext structural-prefix allocation, including its partial-failure semantics. */
    int[] reserveFixedAfter(ObjectSpawn spawn, int childCount, int parentSlot) {
        int[] childSlots = new int[childCount];
        int lastSlot = parentSlot;
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateAfter.applyAsInt(lastSlot);
            if (childSlots[c] >= 0) {
                lastSlot = childSlots[c]; // Next child starts after this one
            }
        }
        reservedChildSlots.put(spawn, childSlots);
        return childSlots;
    }

    int reserveAfter(ObjectInstance parent, int predecessorSlot, int protectedPrefix) {
        ObjectSpawn parentSpawn = placedReservationOwner(parent);
        int[] slots = parentSpawn == null ? null : reservedChildSlots.get(parentSpawn);
        if (protectedPrefix < 0 || slots == null || slots.length < protectedPrefix) {
            throw new IllegalArgumentException("A current unambiguous placed parent and existing fixed prefix are required");
        }
        if (predecessorSlot < 0) return -1;
        int slot = allocateAfter.applyAsInt(predecessorSlot);
        if (slot < 0) return -1;
        for (int index = protectedPrefix; index < slots.length; index++) {
            if (slots[index] < 0) {
                slots[index] = slot;
                return slot;
            }
        }
        int[] extended = Arrays.copyOf(slots, slots.length + 1);
        extended[slots.length] = slot;
        reservedChildSlots.put(parentSpawn, extended);
        return slot;
    }

    /**
     * Releases a folded tail reservation only for its current placed parent. The native caller
     * must clear its saved slot after release; a numeric slot is not a same-owner generation token.
     */
    boolean release(ObjectInstance parent, int reservedSlot, int protectedPrefix) {
        ObjectSpawn parentSpawn = placedReservationOwner(parent);
        int[] slots = parentSpawn == null ? null : reservedChildSlots.get(parentSpawn);
        if (protectedPrefix < 0 || slots == null || slots.length < protectedPrefix
                || !slotLayout.isDynamicSlot(reservedSlot)) return false;
        for (int index = protectedPrefix; index < slots.length; index++) {
            if (slots[index] == reservedSlot) {
                slots[index] = -1;
                // A folded child retires when the ascending native walk reaches its SST,
                // not early at its parent's slot. Already-visited/outside-pass slots free now.
                retireAtOwnPosition.accept(reservedSlot);
                return true;
            }
        }
        return false;
    }

    private ObjectSpawn placedReservationOwner(ObjectInstance parent) {
        ObjectSpawn parentSpawn = instanceToSpawn.get(parent);
        if (parentSpawn == null || activeObjects.get(parentSpawn) != parent
                || !hasRewindIdentity.test(parent)
                || !(parent instanceof AbstractObjectInstance object)
                || !slotLayout.isDynamicSlot(callbacks.call(parent, object::getSlotIndex))
                || callbacks.call(parent, parent::isDestroyed)) return null;
        // Identity, not record equality: this is the exact registered native placement.
        // Reject aliases before allocation/release so a second live actor cannot share its ledger.
        for (ObjectInstance candidate : dynamicObjects) {
            if (candidate != parent && candidate instanceof AbstractObjectInstance other
                    && (other.spawn == parentSpawn || managedSpawn.apply(candidate) == parentSpawn)) return null;
        }
        for (var entry : instanceToSpawn.entrySet()) {
            if (entry.getKey() != parent && entry.getValue() == parentSpawn) return null;
        }
        return parentSpawn;
    }

}
