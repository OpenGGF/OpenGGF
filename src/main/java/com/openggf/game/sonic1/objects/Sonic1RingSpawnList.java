package com.openggf.game.sonic1.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.level.rings.RingSpawn;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An S1 ring's ordered placement recipe, including its capture-time canonical
 * indices. The initial list remains caller-owned; only snapshots and restored
 * recipes are detached. A singleton also carries the current ring's placement.
 */
final class Sonic1RingSpawnList extends AbstractList<RingSpawn>
        implements RewindStateful<Sonic1RingSpawnList.Snapshot> {
    private List<RingSpawn> spawns;
    private final Supplier<RingManager> ringManager;

    Sonic1RingSpawnList(List<RingSpawn> spawns, Supplier<RingManager> ringManager) {
        this.spawns = spawns;
        this.ringManager = ringManager;
    }

    @Override
    public RingSpawn get(int index) {
        return spawns.get(index);
    }

    @Override
    public int size() {
        return spawns.size();
    }

    @Override
    public Snapshot captureRewindStateValue() {
        RingManager manager = ringManager.get();
        RingSpawn[] values = spawns.toArray(RingSpawn[]::new);
        int[] indices = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            indices[i] = manager == null ? -1
                    : RingManagerInternalAccess.spawnIndexForRewind(manager, values[i]);
        }
        return new Snapshot(values, indices);
    }

    @Override
    public void restoreRewindStateValue(Snapshot snapshot) {
        RingManager manager = ringManager.get();
        RingSpawn[] values = snapshot.values();
        int[] indices = snapshot.indices();
        List<RingSpawn> restored = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            restored.add(manager == null ? values[i]
                    : RingManagerInternalAccess.resolveSpawnForRewind(manager, values[i], indices[i]));
        }
        spawns = List.copyOf(restored);
    }

    // Record values are deep-copied by the default codec. Indices, not their Java
    // identity or just coordinates, recover distinct canonical legacy placements.
    record Snapshot(RingSpawn[] values, int[] indices) {
        Snapshot {
            values = values.clone();
            indices = indices.clone();
        }

        @Override public RingSpawn[] values() { return values.clone(); }
        @Override public int[] indices() { return indices.clone(); }
    }
}
