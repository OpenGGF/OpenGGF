package com.openggf.game.rewind.snapshot;

import java.util.List;

/**
 * Snapshot of a game's PLC art loading epoch and runtime-published level-art consumers.
 *
 * <p>The epoch identifies the zone-load generation. The ordered key list records
 * consumers published dynamically after gameplay-time PLC/KosM work completes,
 * allowing a restore to remove stale consumers or deterministically rebuild them.
 * Providers whose art is wholly zone-load-time use the compatibility constructor
 * and therefore capture an empty key list.
 */
public record PlcProgressSnapshot(int loadEpoch, List<String> publishedLevelArtKeys) {
    public PlcProgressSnapshot {
        publishedLevelArtKeys = List.copyOf(publishedLevelArtKeys);
    }

    /** Compatibility constructor for providers without dynamic publication. */
    public PlcProgressSnapshot(int loadEpoch) {
        this(loadEpoch, List.of());
    }
}
