package com.openggf.game.sonic1.scroll;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestUniformQuarterSpeedScroll {
    @Test
    void fractionalAndNegativeDeltasFillEveryPackedLineAndRestore() {
        var scroll = new UniformQuarterSpeedScroll();
        int[] actual = new int[224];
        scroll.update(actual, 100, 15, 0, 0);
        assertScroll(actual, 100, 100);
        assertEquals(2, scroll.getVscrollFactorBG());
        Object start = scroll.captureRewindState();
        scroll.update(actual, 103, 22, 1, 0);
        assertScroll(actual, 103, 100);
        assertEquals(2, scroll.getVscrollFactorBG());
        scroll.update(actual, 104, 23, 2, 0);
        assertScroll(actual, 104, 101);
        assertEquals(3, scroll.getVscrollFactorBG());
        scroll.update(actual, 99, 14, 3, 0);
        assertScroll(actual, 99, 99);
        assertEquals(1, scroll.getVscrollFactorBG());
        scroll.restoreRewindState(start);
        scroll.update(actual, 100, 15, 4, 0);
        assertScroll(actual, 100, 100);
        assertEquals(2, scroll.getVscrollFactorBG());
    }

    @Test
    void finalZoneOwnsItsUniformStateAndInitResetsFractions() throws Exception {
        var provider = new Sonic1ScrollHandlerProvider();
        provider.load(null);
        var uniform = new UniformQuarterSpeedScroll();
        var fz = provider.getHandler(Sonic1ZoneConstants.ZONE_FZ);
        assertNotSame(uniform, fz);
        assertEquals(uniform.getClass(), fz.getClass());
        int[] actual = new int[224];
        uniform.update(actual, 100, 15, 0, 0);
        fz.update(actual, 500, 80, 0, 2);
        uniform.update(actual, 104, 23, 1, 0);
        assertScroll(actual, 104, 101);
        assertEquals(500, fz.getBgCameraX());
        assertEquals(11, fz.getVscrollFactorBG());
        uniform.init(2, -1, -1);
        uniform.update(actual, -1, -1, 2, 0);
        assertScroll(actual, -1, -1);
        assertEquals(256, uniform.getVscrollFactorBG());
    }

    private void assertScroll(int[] actual, int foregroundX, int backgroundX) {
        int[] expected = new int[224];
        Arrays.fill(expected, ((-foregroundX & 0xFFFF) << 16) | (-backgroundX & 0xFFFF));
        assertArrayEquals(expected, actual);
    }
}
