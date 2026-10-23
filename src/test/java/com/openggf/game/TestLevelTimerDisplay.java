package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The HUD's TIME text, in whole seconds as the games show it or in hundredths as Sonic CD does. */
class TestLevelTimerDisplay {
    @Test
    void wholeSecondsMatchTheGamesOwnDisplay() {
        assertEquals("0:00", LevelTimer.displayTime(0, false));
        assertEquals("0:03", LevelTimer.displayTime(3 * 60 + 59, false));
        assertEquals("1:05", LevelTimer.displayTime(65 * 60, false));
    }

    @Test
    void hundredthsSplitEachSecondsSixtyFrames() {
        assertEquals("0:00.00", LevelTimer.displayTime(0, true));
        assertEquals("0:00.01", LevelTimer.displayTime(1, true), "frame 1 is 1.67 hundredths");
        assertEquals("0:00.98", LevelTimer.displayTime(59, true), "the last frame before the second turns");
        assertEquals("0:03.28", LevelTimer.displayTime(3 * 60 + 17, true));
        assertEquals("0:53.66", LevelTimer.displayTime(53 * 60 + 40, true));
    }

    @Test
    void theDisplayHoldsAtNineFiftyNineAsTheHudDoes() {
        long tenMinutes = 10 * 3600;
        assertEquals("9:59", LevelTimer.displayTime(tenMinutes, false));
        assertEquals("9:59.99", LevelTimer.displayTime(tenMinutes + 1234, true));
        assertEquals("9:59.98", LevelTimer.displayTime(tenMinutes - 1, true));
        assertEquals("0:00.00", LevelTimer.displayTime(-7, true), "a negative clock shows zero");
    }
}
