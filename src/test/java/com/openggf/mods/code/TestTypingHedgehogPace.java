package com.openggf.mods.code;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Controls for the gate's displacement and native rolling-speed measurements. */
class TestTypingHedgehogPace {
    @Test void rockingBallIsStationaryAndSlowDespiteNonzeroSpeed() {
        var p = new ForwardRails.Pace();
        for (int r = 0; r < 160; r++) p.row(640 + r % 2, 100, true, r % 2 == 0 ? 8 : -8, r, false);
        assertEquals(152, p.maxStill);
        assertEquals(160, p.maxRoll);
        assertTrue(p.maxStill > TestTypingHedgehogRails.STILL_LIMIT);
        assertTrue(p.maxRoll > TestTypingHedgehogRails.SLOW_ROLL_LIMIT);
    }

    @Test void runningOrALiftRideMakesProgressAndFastRollingIsNotSlow() {
        for (boolean lift : new boolean[] {false, true}) {
            var p = new ForwardRails.Pace();
            for (int r = 0; r < 160; r++) p.row(lift ? 10 : 10 + r * 3, lift ? 100 + r * 3 : 100,
                    !lift, 0x300, r, false);
            assertEquals(0, p.maxStill);
            assertEquals(0, p.maxRoll);
        }
    }

    @Test void cleanupAndActualChargeBreakStretchesAndCopiesDoNotShareHistory() {
        var p = new ForwardRails.Pace();
        for (int r = 0; r < 20; r++) p.row(10, 100, true, 0, r, false);
        var branch = p.copy();
        p.row(10, 100, true, 0, 20, true);
        for (int r = 21; r < 41; r++) p.row(10, 100, true, 0, r, false);
        for (int r = 20; r < 41; r++) branch.row(10, 100, true, 0, r, false);
        assertEquals(20, p.maxRoll);
        assertEquals(41, branch.maxRoll);
    }
}
