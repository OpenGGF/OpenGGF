package com.openggf.game.modzone;

import com.openggf.game.ModApi;

import java.util.Objects;

/** Runtime metadata exposed by a decorated zone registry without leaking mod internals. */
@ModApi
public record ModZoneRuntimeContribution(
        String ownerModId,
        String localKey,
        ModZoneLevelData levelData,
        ModZoneRuntimeProfile runtimeProfile) {
    public ModZoneRuntimeContribution {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(localKey, "localKey");
        Objects.requireNonNull(levelData, "levelData");
        Objects.requireNonNull(runtimeProfile, "runtimeProfile");
    }
}
