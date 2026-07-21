package com.openggf.game.rewind.snapshot;

import java.util.Objects;

/**
 * Immutable capture of GameStateManager state for rewind snapshots.
 * Captures all gameplay-meaningful counters and flags including score, lives,
 * continues, emeralds, and zone-specific state flags.
 */
@com.openggf.game.ModApi
public record GameStateSnapshot(
        int score,
        int lives,
        int continues,
        int currentSpecialStageIndex,
        int emeraldCount,
        boolean[] gotEmeralds,
        boolean[] gotSuperEmeralds,
        boolean emeraldsConverted,
        int currentBossId,
        boolean bossDefeatedFlag,
        boolean screenShakeActive,
        boolean backgroundCollisionFlag,
        boolean bigRingCollected,
        boolean wfzFireToggle,
        int itemBonus,
        boolean reverseGravityActive,
        int collectedSpecialRings,
        boolean endOfLevelActive,
        boolean endOfLevelFlag,
        boolean screenLocked) {

    public GameStateSnapshot {
        Objects.requireNonNull(gotEmeralds, "gotEmeralds");
        Objects.requireNonNull(gotSuperEmeralds, "gotSuperEmeralds");
        // Defensive copy so the record is truly immutable
        gotEmeralds = gotEmeralds.clone();
        gotSuperEmeralds = gotSuperEmeralds.clone();
    }

    /** Binary-compatible constructor for the Mod API 2.4 snapshot shape. */
    public GameStateSnapshot(int score, int lives, int continues, int currentSpecialStageIndex,
            int emeraldCount, boolean[] gotEmeralds, boolean[] gotSuperEmeralds,
            int currentBossId, boolean bossDefeatedFlag, boolean screenShakeActive,
            boolean backgroundCollisionFlag, boolean bigRingCollected, boolean wfzFireToggle,
            int itemBonus, boolean reverseGravityActive, int collectedSpecialRings,
            boolean endOfLevelActive, boolean endOfLevelFlag, boolean screenLocked) {
        this(score, lives, continues, currentSpecialStageIndex, emeraldCount,
                gotEmeralds, gotSuperEmeralds, false, currentBossId, bossDefeatedFlag,
                screenShakeActive, backgroundCollisionFlag, bigRingCollected, wfzFireToggle,
                itemBonus, reverseGravityActive, collectedSpecialRings, endOfLevelActive,
                endOfLevelFlag, screenLocked);
    }

    @Override
    public boolean[] gotEmeralds() {
        return gotEmeralds.clone();
    }

    @Override
    public boolean[] gotSuperEmeralds() {
        return gotSuperEmeralds.clone();
    }
}
