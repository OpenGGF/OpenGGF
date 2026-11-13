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

/**
 * Dynamic classification and captured-order metadata. Membership and identity
 * mutations belong to {@link ObjectPopulation}; auxiliary classifications mark
 * engine objects that intentionally sit outside the ROM SST pool.
 */
final class DynamicObjectOwnership {
    // Restore-only ordering, derived from the snapshot. Late player-bound
    // recreation must retain which dynamic alias last owns a fixed ROM SST.
    private final Map<ObjectRefId, Integer> restoredOrder =
            new HashMap<>();

    void rememberCapturedOrder(List<DynamicObjectEntry> entries) {
        restoredOrder.clear();
        for (int index = 0; index < entries.size(); index++) {
            var id = entries.get(index).objectId();
            if (id != null) restoredOrder.put(id, index);
        }
    }

    Comparator<ObjectInstance> capturedOrder(Function<ObjectInstance, ObjectRefId> identity) {
        return restoredOrder.isEmpty() ? null : Comparator.comparingInt(object ->
                restoredOrder.getOrDefault(identity.apply(object), Integer.MAX_VALUE));
    }

    private final Set<ObjectInstance> nonRewindable =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ObjectInstance> rewindable =
            Collections.newSetFromMap(new IdentityHashMap<>());

    void markNonRewindable(ObjectInstance object) {
        nonRewindable.add(object);
    }

    void markRewindable(ObjectInstance object) {
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
