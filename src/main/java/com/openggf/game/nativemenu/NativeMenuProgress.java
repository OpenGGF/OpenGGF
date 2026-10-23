package com.openggf.game.nativemenu;

import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;

/** Copies only persistent special-stage progress across a native options launch. */
public final class NativeMenuProgress {
    private NativeMenuProgress() { }

    public static void retainSpecialStageProgress(GameStateManager target, GameStateSnapshot previous) {
        GameStateSnapshot fresh = target.capture();
        target.restore(new GameStateSnapshot(
                fresh.score(), fresh.lives(), fresh.continues(),
                previous.currentSpecialStageIndex(), previous.emeraldCount(),
                previous.gotEmeralds(), previous.gotSuperEmeralds(), previous.emeraldsConverted(),
                fresh.currentBossId(), fresh.bossDefeatedFlag(), fresh.screenShakeActive(),
                fresh.backgroundCollisionFlag(), fresh.bigRingCollected(), fresh.wfzFireToggle(),
                fresh.itemBonus(), fresh.reverseGravityActive(), fresh.collectedSpecialRings(),
                fresh.endOfLevelActive(), fresh.endOfLevelFlag(), fresh.screenLocked()));
    }
}
