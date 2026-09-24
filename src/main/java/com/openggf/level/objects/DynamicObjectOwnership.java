package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Tracks engine-owned dynamic objects that intentionally sit outside the ROM SST pool. */
final class DynamicObjectOwnership {
    // Restore-only ordering, derived from the snapshot. Late player-bound
    // recreation must retain which dynamic alias last owns a fixed ROM SST.
    private final Map<ObjectRefId, Integer> restoredOrder =
            new HashMap<>();

    void restoreIdentity(ObjectInstance object, ObjectRefId capturedId,
            Map<ObjectInstance, ObjectRefId> identities) {
        if (capturedId == null) {
            throw new IllegalArgumentException("restored dynamic object identity is required");
        }
        identities.put(object, capturedId);
    }

    void rememberCapturedOrder(List<DynamicObjectEntry> entries) {
        restoredOrder.clear();
        for (int index = 0; index < entries.size(); index++) {
            var id = entries.get(index).objectId();
            if (id != null) restoredOrder.put(id, index);
        }
    }

    void restoreCapturedOrder(List<ObjectInstance> objects,
            Function<ObjectInstance, ObjectRefId> identity) {
        if (!restoredOrder.isEmpty()) {
            objects.sort(Comparator.comparingInt(object ->
                    restoredOrder.getOrDefault(identity.apply(object), Integer.MAX_VALUE)));
        }
    }

    private final Set<ObjectInstance> nonRewindable =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ObjectInstance> rewindable =
            Collections.newSetFromMap(new IdentityHashMap<>());

    boolean addNonRewindable(
            ObjectInstance object, ObjectServices services, List<ObjectInstance> dynamicObjects) {
        if (!prepare(object, services)) {
            return false;
        }
        dynamicObjects.add(object);
        nonRewindable.add(object);
        return true;
    }

    boolean addRewindable(
            ObjectInstance object,
            ObjectServices services,
            List<ObjectInstance> dynamicObjects,
            Set<ObjectInstance> deferredThisFrame,
            boolean updating,
            Consumer<ObjectInstance> assignRewindId) {
        if (!prepare(object, services)) {
            return false;
        }
        assignRewindId.accept(object);
        dynamicObjects.add(object);
        rewindable.add(object);
        if (updating && object instanceof AbstractObjectInstance instance
                && instance.skipsSameFrameUpdateAfterSpawn()) {
            deferredThisFrame.add(object);
        }
        return true;
    }

    private boolean prepare(ObjectInstance object, ObjectServices services) {
        if (object == null) {
            return false;
        }
        if (object instanceof AbstractObjectInstance instance) {
            instance.setServices(services);
            ObjectLifetimeOps.clearPreviousManagerSlot(instance);
        }
        return true;
    }

    void markRestoredRewindable(ObjectInstance object) {
        rewindable.add(object);
    }

    void remove(ObjectInstance object) {
        nonRewindable.remove(object);
        rewindable.remove(object);
    }

    void clear() {
        restoredOrder.clear();
        nonRewindable.clear();
        rewindable.clear();
    }

    boolean excludesFromRewind(ObjectInstance object) {
        return nonRewindable.contains(object);
    }

    boolean isRewindableAuxiliary(ObjectInstance object) {
        return rewindable.contains(object);
    }

    int nativeCount(int totalDynamicObjects) {
        return totalDynamicObjects - nonRewindable.size() - rewindable.size();
    }

    Set<ObjectInstance> nonRewindableObjects() {
        return nonRewindable;
    }
}
