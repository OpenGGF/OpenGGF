package com.openggf.game.sonic1.scroll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic1Rev00Scroll {
    @Test
    void originalGreenHillHasOneCloudAndMountainBandWithNoIdleDrift() {
        var handler = new Sonic1Rev00Scroll(Sonic1ZoneConstants.ZONE_GHZ);
        handler.init(0, 100, 0);
        int[] actual = new int[224];
        handler.update(actual, 100, 0, 1, 0);
        assertEquals(0x26, handler.getVscrollFactorBG());
        for (int line = 0; line < 74; line++) assertEquals((short) -100, (short) actual[line]);
        int[] initial = actual.clone();
        handler.update(actual, 100, 0, 120, 0);
        assertArrayEquals(initial, actual, "REV00 clouds do not autonomously scroll");
        handler.update(actual, 108, 0, 121, 0);
        assertEquals((short) -103, (short) actual[0], "clouds/mountains move at 3/8 camera speed");
        assertEquals((short) -104, (short) actual[90], "hills move at 1/2 camera speed");
    }

    @Test
    void greenHillResidencyCoversNativeWaterSpreadAndTheConfiguredViewport() {
        var handler = new Sonic1Rev00Scroll(Sonic1ZoneConstants.ZONE_GHZ);
        handler.init(0, 100, 0);
        handler.update(new int[224], 100, 0, 1, 0);
        assertEquals(1024, Sonic1Rev00Scroll.requiredBgPeriodWidth(
                handler.getMinScrollOffset(), handler.getMaxScrollOffset(), 320));
        assertEquals(2048, Sonic1Rev00Scroll.requiredBgPeriodWidth(
                handler.getMinScrollOffset(), handler.getMaxScrollOffset(), 640));
        assertEquals(8192, Sonic1Rev00Scroll.requiredBgPeriodWidth(0, 20000, 960));
    }

    @Test
    void originalLabyrinthAndSbz3AreUniformAndHaveNoUnderwaterRipple() {
        var handler = new Sonic1Rev00Scroll(Sonic1ZoneConstants.ZONE_LZ);
        handler.init(0, 100, 200);
        int[] actual = new int[224];
        handler.update(actual, 108, 208, 55, 0);
        for (int word : actual) assertEquals((-108 & 0xFFFF) << 16 | (-104 & 0xFFFF), word);
        assertEquals(104, handler.getVscrollFactorBG());
    }

    @Test
    void originalMarbleUsesOneDungeonBandAndOriginalVerticalFormula() {
        var handler = new Sonic1Rev00Scroll(Sonic1ZoneConstants.ZONE_MZ);
        handler.init(0, 100, 450);
        int[] actual = new int[224];
        handler.update(actual, 108, 460, 1, 0);
        assertEquals(515, handler.getVscrollFactorBG());
        for (int word : actual) assertEquals((short) -106, (short) word);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5, 6, 7})
    void originalScrollRetainsFractionsAcrossRestoreAndReplay(int zone) {
        var handler = new Sonic1Rev00Scroll(zone);
        handler.init(0, 0x50, 0x160);
        int[] actual = new int[224];
        handler.update(actual, 0x53, 0x163, 21, 0);
        Object saved = handler.captureRewindState();
        handler.update(actual, 0x58, 0x166, 22, 0);
        int[] expected = actual.clone();
        short expectedY = handler.getVscrollFactorBG();
        var expectedBank = handler.captureBackgroundScroll();
        handler.init(0, 0x900, 0x600);
        handler.update(actual, 0x910, 0x610, 99, 0);
        handler.restoreRewindState(saved);
        handler.update(actual, 0x58, 0x166, 22, 0);
        assertArrayEquals(expected, actual);
        assertEquals(expectedY, handler.getVscrollFactorBG());
        assertEquals(expectedBank, handler.captureBackgroundScroll());
    }
}
