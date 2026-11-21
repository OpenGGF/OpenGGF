package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.internal.LevelLifePolicy;
import com.openggf.game.soniccd.level.CdLevel;

final class CdLevelLifePolicy implements LevelLifePolicy {
    static final CdLevelLifePolicy INSTANCE = new CdLevelLifePolicy();

    private CdLevelLifePolicy() { }

    @Override
    public int livesAfterDeath(int lives) {
        // ObjSonic_DeadChkGone (Level/_Objects/Sonic/Main.asm:2420-2423) tests the sign of the decremented byte.
        return Math.max(0, (byte) (lives - 1));
    }

    @Override
    public boolean canTogglePause() {
        // Level_MainLoop (Level/Main.asm:333-345) tests the USA player's routine and the level's control lock.
        var levels = GameServices.levelOrNull();
        var sprites = GameServices.spritesOrNull();
        var player = sprites == null ? null : sprites.getMainPlayable();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel level
                && !level.flags().ctrlLocked && player != null && !player.getDead();
    }
}
