package com.openggf.game.rewind.snapshot;

import java.util.List;

/**
 * Snapshot of a game's PLC/object-art progress and runtime-published level-art consumers.
 *
 * <p>The epoch identifies the zone-load generation. The ordered key list records
 * consumers published dynamically after gameplay-time PLC/KosM work completes,
 * allowing a restore to remove stale consumers or deterministically rebuild them.
 * The optional runtime state records provider-owned queued art work, such as
 * S3K's direct {@code Queue_Kos_Module} requests. Providers whose art is wholly
 * zone-load-time use the compatibility constructor.
 */
public record PlcProgressSnapshot(int loadEpoch, int runtimeState, List<String> publishedLevelArtKeys) {
    public PlcProgressSnapshot {
        publishedLevelArtKeys = List.copyOf(publishedLevelArtKeys);
    }

    /** Compatibility constructor for providers without dynamic publication. */
    public PlcProgressSnapshot(int loadEpoch) {
        this(loadEpoch, 0, List.of());
    }

    public PlcProgressSnapshot(int loadEpoch, int runtimeState) {
        this(loadEpoch, runtimeState, List.of());
    }

    public PlcProgressSnapshot(int loadEpoch, List<String> publishedLevelArtKeys) {
        this(loadEpoch, 0, publishedLevelArtKeys);
    }
}
