package com.openggf.level.objects;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** Owns deferred SST release and borrowed-execution-slot retirement bookkeeping. */
final class ObjectSlotRetirementController {
    private final ObjectInstance[] ownersByOwnSlot;
    private final BitSet pendingChildReleases = new BitSet();

    ObjectSlotRetirementController(int processSlotCount) {
        ownersByOwnSlot = new ObjectInstance[processSlotCount];
    }

    void index(AbstractObjectInstance object, int ownSlot, int executionSlot, boolean managedOwnSlot) {
        if (ownSlot != executionSlot && managedOwnSlot) ownersByOwnSlot[ownSlot] = object;
    }

    ObjectInstance takeOwner(int slot) {
        ObjectInstance owner = ownersByOwnSlot[slot];
        ownersByOwnSlot[slot] = null;
        return owner;
    }

    boolean deferChildRelease(int slot, boolean updating, int currentSlot, int processSlots) {
        if (!updating || slot <= currentSlot || slot >= processSlots) return false;
        pendingChildReleases.set(slot);
        return true;
    }

    boolean consumeChildRelease(int slot) {
        boolean pending = pendingChildReleases.get(slot);
        pendingChildReleases.clear(slot);
        return pending;
    }

    void drainChildReleases(IntConsumer release) {
        for (int slot = pendingChildReleases.nextSetBit(0); slot >= 0;
             slot = pendingChildReleases.nextSetBit(slot + 1)) release.accept(slot);
        pendingChildReleases.clear();
    }

    void clear() {
        Arrays.fill(ownersByOwnSlot, null);
        pendingChildReleases.clear();
    }

    void clearOwners() {
        Arrays.fill(ownersByOwnSlot, null);
    }

    boolean isAlreadyExecuted(ObjectInstance object, boolean updating, int currentSlot,
                              IntPredicate managedSlot) {
        if (!updating || currentSlot < 0 || !(object instanceof AbstractObjectInstance abstractObject)) {
            return false;
        }
        int slot = abstractObject.getSlotIndex();
        return managedSlot.test(slot) && slot <= currentSlot;
    }
}
