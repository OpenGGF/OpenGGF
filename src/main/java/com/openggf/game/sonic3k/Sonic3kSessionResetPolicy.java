package com.openggf.game.sonic3k;

import com.openggf.game.LevelGamestate;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.NativeSessionResetPolicy;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleState;

/** Selective CrossResetRAM retention followed by the native title-entry clears. */
public enum Sonic3kSessionResetPolicy implements NativeSessionResetPolicy {
    INSTANCE;

    @Override
    public RetainedState capture(GameplayModeContext outgoing, Kind kind) {
        var cheats = outgoing.getWorldSession().getGameModule().getGameService(Sonic3kCheatFlags.class);
        if (kind == Kind.COLD_POWER) {
            return replacement -> {
                if (cheats != null) cheats.reset();
                if (replacement.getWorldSession().getGameModule().getTitleScreenProvider()
                        instanceof Sonic3kTitleScreenManager title) {
                    title.nativeTitleState().apply(new Sonic3kTitleState.Snapshot(0));
                }
                // Test_Checksum clears the bank; locked-on title sets lives on Start.
                replacement.getGameStateManager().restoreSaveProgress(0, 0, java.util.List.of(), java.util.List.of());
                replacement.getLevelManager().resetLevelGamestate(new LevelGamestate());
            };
        }
        var state = outgoing.getGameStateManager().capture();
        var level = outgoing.getLevelManager();
        var levelState = level.getLevelGamestate();
        int rings = levelState == null ? 0 : levelState.getRings();
        int ringLives = levelState == null ? 0 : levelState.getRingExtraLifeFlags();
        long timer = levelState == null ? 0 : levelState.getTimerFrames();
        int levelFrames = level.getFrameCounter();
        return replacement -> {
            // Test_LockOn clears below CrossResetRAM (sonic3k.asm:270-289).
            // Title_Screen clears active mode/checkpoint/resource state
            // (sonic3k.asm:5424-5464), but not this progress bank
            // (CrossResetRAM, sonic3k.constants.asm:800-1018).
            GameStateSnapshot fresh = replacement.getGameStateManager().capture();
            replacement.getGameStateManager().restore(new GameStateSnapshot(
                    state.score(), state.lives(), state.continues(), state.currentSpecialStageIndex(),
                    state.emeraldCount(), state.gotEmeralds(), state.gotSuperEmeralds(), state.emeraldsConverted(),
                    fresh.currentBossId(), fresh.bossDefeatedFlag(), fresh.screenShakeActive(),
                    fresh.backgroundCollisionFlag(), fresh.bigRingCollected(), fresh.wfzFireToggle(),
                    state.itemBonus(), fresh.reverseGravityActive(), state.collectedSpecialRings(),
                    fresh.endOfLevelActive(), fresh.endOfLevelFlag(), fresh.screenLocked()));
            var retainedLevelState = new LevelGamestate();
            retainedLevelState.setRings(rings);
            retainedLevelState.setRingExtraLifeFlags(ringLives);
            retainedLevelState.setTimerFrames(timer);
            // Update_HUD_timer is in the retained bank; active level ownership is gone.
            retainedLevelState.pauseTimer();
            replacement.getLevelManager().resetLevelGamestate(retainedLevelState);
            replacement.getLevelManager().setFrameCounter(levelFrames);
        };
    }
}
