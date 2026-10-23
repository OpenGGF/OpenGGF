package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Resolves captured player roles for dynamic recreation and retains entries that
 * must wait for the player's post-restore power-up refresh. Consuming an entry
 * does not retire its admission identity: generic recreation may retry the same
 * work later in the same restore, and must not queue it twice.
 */
final class ObjectRewindPlayerBindings {
    private final Map<Class<?>, List<DynamicObjectEntry>> pendingEntries = new HashMap<>();
    private final Map<DynamicObjectEntry, Admission> admittedEntries = new IdentityHashMap<>();
    private final Map<ObjectRefId, DynamicObjectEntry> admittedIds = new HashMap<>();

    private record Admission(Class<?> baseType, int order) { }

    /** Immutable claim tied to this admission, even if a later restore reuses the same entry. */
    static final class PendingReturn {
        private final ObjectRewindPlayerBindings bindings;
        private final Admission admission;
        private final DynamicObjectEntry entry;
        private final PlayableEntity expectedOwner;

        private PendingReturn(ObjectRewindPlayerBindings bindings, Admission admission,
                              DynamicObjectEntry entry, PlayableEntity expectedOwner) {
            this.bindings = bindings;
            this.admission = admission;
            this.entry = entry;
            this.expectedOwner = expectedOwner;
        }

        void returnPending() {
            bindings.returnConsumed(admission, entry, expectedOwner);
        }
    }

    /** Prepares one identity per original entry before either recreation phase runs. */
    Map<DynamicObjectEntry, DynamicObjectEntry> prepareEntries(
            List<DynamicObjectEntry> entries, RewindIdentityTable restoreTable) {
        Map<DynamicObjectEntry, DynamicObjectEntry> preparedEntries = new IdentityHashMap<>();
        for (DynamicObjectEntry entry : entries) {
            preparedEntries.computeIfAbsent(entry, captured -> {
                if (captured.playerOwnerId() == null) return captured;
                if (captured.playerOwnerId().isNull())
                    throw new IllegalArgumentException("Captured player-bound owner role is null");
                var owner = restoreTable.resolvePlayer(captured.playerOwnerId(), true);
                return new DynamicObjectEntry(
                        captured.className(), captured.spawn(), captured.slotIndex(), captured.state(), owner,
                        captured.objectId(), captured.ownerModId(), captured.rewindableAuxiliary(),
                        captured.playerOwnerId());
            });
        }
        return preparedEntries;
    }

    void enqueue(Class<?> baseType, DynamicObjectEntry entry) {
        if (entry == null || entry.slotIndex() < 0) return;
        if (admittedEntries.containsKey(entry)) return;
        if (entry.objectId() != null) {
            DynamicObjectEntry previous = admittedIds.get(entry.objectId());
            if (previous != null && previous != entry)
                throw new IllegalStateException("Distinct pending entries share captured object identity: " + entry.objectId());
            admittedIds.put(entry.objectId(), entry);
        }
        admittedEntries.put(entry, new Admission(baseType, admittedEntries.size()));
        pendingEntries.computeIfAbsent(baseType, k -> new ArrayList<>()).add(entry);
    }

    PendingReturn claimReturn(Class<?> baseType, DynamicObjectEntry entry, PlayableEntity expectedOwner) {
        Admission admission = admittedEntries.get(entry);
        validateReturn(admission, baseType, entry, expectedOwner);
        return new PendingReturn(this, admission, entry, expectedOwner);
    }

    private void validateReturn(Admission admission, Class<?> baseType,
                                DynamicObjectEntry entry, PlayableEntity expectedOwner) {
        if (admission == null || admittedEntries.get(entry) != admission
                || admission.baseType() != baseType || expectedOwner == null
                || entry.playerOwner() != expectedOwner || entry.objectId() == null
                || admittedIds.get(entry.objectId()) != entry) {
            throw new IllegalStateException("Cannot return a mismatched or retired player-bound entry");
        }
    }

    /** Native handoff/retry returns the prepared entry without reopening generic admission. */
    private void returnConsumed(Admission admission, DynamicObjectEntry entry, PlayableEntity expectedOwner) {
        validateReturn(admission, admission.baseType(), entry, expectedOwner);
        var queue = pendingEntries.computeIfAbsent(admission.baseType(), k -> new ArrayList<>());
        for (int index = 0; index < queue.size(); index++) {
            DynamicObjectEntry pending = queue.get(index);
            if (pending == entry) return; // A factory can fail before consuming the handoff.
            if (admittedEntries.get(pending).order() > admission.order()) {
                queue.add(index, entry);
                return;
            }
        }
        queue.add(entry);
    }

    DynamicObjectEntry consume(Class<?> baseType, Predicate<DynamicObjectEntry> matcher) {
        List<DynamicObjectEntry> queue = pendingEntries.get(baseType);
        if (queue == null || queue.isEmpty()) return null;
        Iterator<DynamicObjectEntry> iterator = queue.iterator();
        while (iterator.hasNext()) {
            DynamicObjectEntry entry = iterator.next();
            if (matcher.test(entry)) {
                iterator.remove();
                if (queue.isEmpty()) {
                    pendingEntries.remove(baseType);
                }
                return entry;
            }
        }
        return null;
    }

    void clear() {
        pendingEntries.clear();
        admittedEntries.clear();
        admittedIds.clear();
    }
}
