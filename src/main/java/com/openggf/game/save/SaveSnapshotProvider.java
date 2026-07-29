package com.openggf.game.save;

import com.openggf.game.GameStateManager;

import java.util.Map;

@FunctionalInterface
@com.openggf.game.ModApi
public interface SaveSnapshotProvider {
    Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context);

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
