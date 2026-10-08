package com.openggf.game.save;

import com.openggf.game.GameStateManager;

import java.util.Map;

@FunctionalInterface
@com.openggf.game.ModApi
public interface SaveSnapshotProvider {
    Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context);

    /**
     * Captures optional game-owned input at the save boundary, inside the provider's
     * normal owner callback boundary. Values must be JSON scalars, lists or string-keyed
     * maps; the host deeply freezes them before invoking {@link #capture}.
     * The current zone state may be null when the host has no installed zone runtime.
     */
    default Map<String,Object> captureRuntimeFields(com.openggf.game.zone.ZoneRuntimeState zoneState) {
        return Map.of();
    }

    /**
     * Restores game-owned progress fields not represented by the legacy common
     * save lists.
     *
     * @return true when this provider accepted and restored the payload
     */
    default boolean restoreProgress(
            GameStateManager gameState, int lives, int continues, Map<String, Object> payload) {
        return false;
    }
}
