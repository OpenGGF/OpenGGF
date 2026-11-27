package com.openggf.game.soniccd;

import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.game.GameServices;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelState;
import com.openggf.game.soniccd.program.CdScorePolicy;

/** Owns the level ring awards for {@code CollectRing} (Level/_Objects/Ring/Main.asm:290-312). */
public final class CdLevelGamestate extends LevelGamestate {
    @Override
    public void addRings(int amount) {
        if (amount == 0) return;
        setRings(getRings() + amount);
        if (amount > 0 && claimRingExtraLife(this)) {
            CdScorePolicy.awardLevelLife(GameServices.gameState());
            AudioManagerInternalAccess.playRingExtraLife(GameServices.audio());
        }
    }

    /**
     * Claims one threshold, in native order, for a ring or a ten-ring monitor.
     * {@code CollectRing} (Level/_Objects/Ring/Main.asm:294-305);
     * {@code ObjMonitorItem_Main} (Monitor and Time Post/Main.asm:502-513).
     */
    public static boolean claimRingExtraLife(LevelState state) {
        int rings = state.getRings();
        int flags = state.getRingExtraLifeFlags();
        if (rings >= 100 && (flags & 2) == 0) {
            state.setRingExtraLifeFlags(flags | 2);
            return true;
        }
        if (rings >= 200 && (flags & 4) == 0) {
            state.setRingExtraLifeFlags(flags | 4);
            return true;
        }
        return false;
    }
}
