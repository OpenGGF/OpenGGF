package com.openggf.level.rings;

import com.openggf.level.spawn.SpawnPoint;

/**
 * Immutable ring placement record expanded to individual ring positions.
 */
public record RingSpawn(int x, int y, int placementId) implements SpawnPoint {

    public RingSpawn {
        x = x & 0xFFFF;
        y = y & 0xFFFF;
    }

    /** Compatibility constructor for ROM/runtime callers without persisted identity. */
    public RingSpawn(int x, int y) {
        this(x, y, -1);
    }
}
