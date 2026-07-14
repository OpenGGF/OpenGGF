package com.openggf.level.objects;

import com.openggf.level.TransitionSstOccupant;
import com.openggf.level.objects.boss.BossChildComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds immutable transition views without coupling them to manager execution. */
final class ObjectTransitionSstSnapshot {
    private ObjectTransitionSstSnapshot() {
    }

    static List<TransitionSstOccupant> persistentDynamic(List<ObjectInstance> dynamicObjects) {
        List<TransitionSstOccupant> snapshot = new ArrayList<>();
        for (ObjectInstance instance : dynamicObjects) {
            if (instance == null || instance.isDestroyed() || !instance.isPersistent()) {
                continue;
            }
            // Boss children are persistent only to survive arena culling. ROM
            // Load_Level clears their Dynamic_object_RAM slots with the parent.
            if (!(instance instanceof BossChildComponent)) {
                snapshot.add(transitionOccupant(instance));
            }
        }
        return snapshot;
    }

    static List<TransitionSstOccupant> allLive(
            Iterable<ObjectInstance> placedObjects,
            List<ObjectInstance> dynamicObjects) {
        List<TransitionSstOccupant> snapshot = new ArrayList<>();
        for (ObjectInstance instance : placedObjects) {
            addLiveSlotBacked(snapshot, instance);
        }
        for (ObjectInstance instance : dynamicObjects) {
            addLiveSlotBacked(snapshot, instance);
        }
        snapshot.sort(Comparator.comparingInt(TransitionSstOccupant::originalSlot));
        return snapshot;
    }

    private static void addLiveSlotBacked(
            List<TransitionSstOccupant> snapshot,
            ObjectInstance instance) {
        if (instance == null || instance.isDestroyed()
                || snapshot.stream().anyMatch(carried -> carried.identity() == instance)) {
            return;
        }
        if (instance instanceof AbstractObjectInstance object && object.getSlotIndex() >= 0) {
            snapshot.add(new TransitionSstOccupant(instance, object.getSlotIndex()));
        }
    }

    private static TransitionSstOccupant transitionOccupant(ObjectInstance instance) {
        int originalSlot = instance instanceof AbstractObjectInstance object
                ? object.getSlotIndex() : -1;
        return new TransitionSstOccupant(instance, originalSlot);
    }
}
