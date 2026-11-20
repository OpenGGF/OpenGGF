package com.openggf.game.soniccd.zones;

import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.Level;

/** Holds palette cycles while the loaded stage's time-stop word is set. */
public final class CdPaletteCycleGate {
    private CdPaletteCycleGate() { }

    public static boolean isStopped(Level level) {
        // Level_MainLoop (Level/Main.asm:420-427).
        // StageMainLoopDraw (r4/main.asm:401-406).
        // StageMainLoopDraw (common/main.asm:443-447).
        if (!(level instanceof CdLevel stage) || !stage.flags().timeStopped()) return false;

        // loc_2018F2 (r5/main.asm:375-387) calls CyclePalette unconditionally in USA Quartz Quadrant acts 1 and 2.
        CdStageKey key = stage.payload().stage();
        return !(key.zone() == CdZone.QUARTZ_QUADRANT && key instanceof CdStageKey.Traversal);
    }
}
