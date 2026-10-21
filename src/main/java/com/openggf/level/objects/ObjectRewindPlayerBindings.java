package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Resolves captured player roles for dynamic recreation and retains entries that
 * must wait for the player's post-restore power-up refresh. Consuming an entry
 * does not retire its admission identity: generic recreation may retry the same
 * work later in the same restore, and must not queue it twice.
 */
final class ObjectRewindPlayerBindings {
    private final Map<Class<?>, ArrayDeque<DynamicObjectEntry>> pendingEntries = new HashMap<>();
    private final Set<DynamicObjectEntry> admittedEntries =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ObjectRefId, DynamicObjectEntry> admittedIds = new HashMap<>();

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
        if (admittedEntries.contains(entry)) return;
        if (entry.objectId() != null) {
            DynamicObjectEntry previous = admittedIds.get(entry.objectId());
            if (previous != null && previous != entry)
                throw new IllegalStateException("Distinct pending entries share captured object identity: " + entry.objectId());
            admittedIds.put(entry.objectId(), entry);
        }
        admittedEntries.add(entry);
        pendingEntries.computeIfAbsent(baseType, k -> new ArrayDeque<>()).add(entry);
    }

    DynamicObjectEntry consume(Class<?> baseType, Predicate<DynamicObjectEntry> matcher) {
        ArrayDeque<DynamicObjectEntry> queue = pendingEntries.get(baseType);
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
