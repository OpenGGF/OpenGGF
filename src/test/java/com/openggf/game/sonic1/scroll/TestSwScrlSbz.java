package com.openggf.game.sonic1.scroll;

import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Fixed REV01 Deform_SBZ vectors, including the BgScroll_SBZ entry offset. */
class TestSwScrlSbz {
    @AfterEach
    void resetConfiguredGameFixture() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @CsvSource({"320,NATIVE_4_3,4096", "352,WIDE_16_10,4096", "400,WIDE_16_9,8192",
            "528,ULTRA_21_9,8192", "800,SUPER_32_9,8192"})
    void cacheWindowContainsEveryVisibleBandAtTheConfiguredWidth(
            int width, String aspect, int expectedPeriod) throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        GameServices.configuration().setConfigValue(SonicConfiguration.DISPLAY_ASPECT, aspect);
        GameServices.configuration().resolveDisplayAspect();
        assertEquals(width, GameServices.configuration().getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        ZoneScrollHandler handler = sbz();
        int[] scroll = new int[224];
        handler.update(scroll, 0x1234, 0, 0, 0);
        int origin = Math.floorDiv(handler.getBgCameraX(), 16) * 16;
        int period = handler.getBgPeriodWidth();
        assertEquals(944, origin, "the leftmost cloud source is pixel947");
        assertEquals(expectedPeriod, period);
        for (int packed : scroll) {
            int source = -(short) packed;
            assertTrue(source >= origin && source + width <= origin + period,
                    "the cache must include this band's complete viewport");
        }
    }

    @Test
    void levelInitializationDiscardsEarlierActFractionsAndRestorePublishesItsWindow() throws Exception {
        ZoneScrollHandler handler = sbz();
        int[] actual = new int[224];
        handler.update(actual, 100, 15, 0, 0);
        Object start = handler.captureRewindState();
        handler.update(actual, 400, 800, 1, 1);
        handler.restoreRewindState(start);
        assertEquals(2, handler.getVscrollFactorBG());
        // X100: DIVS gives24, so the SWAP/ADD cloud words are -25,-24,-22,-21.
        assertEquals(21, handler.getBgCameraX());
        handler.init(0, 200, 80);
        handler.update(actual, 200, 80, 2, 0);
        assertEquals(11, handler.getVscrollFactorBG());
        assertArrayEquals(scanlines(200, 11,
                nativeWords(-50, -47, -44, -41, -200, -200, -200)), actual);
    }

    @Test
    void actOnePublishesTheNativeCloudAndBuildingWordsOnEveryScanline() throws Exception {
        // Deform_SBZ at camera X $1234: the four SWAP/ADD/SWAP cloud words.
        short[] words = nativeWords(-1165, -1093, -1020, -947, -4660, -4660, -4660);
        for (int cameraY : new int[]{0, 128, 896, 1536, 1960}) {
            ZoneScrollHandler handler = sbz();
            int[] actual = new int[224];
            handler.update(actual, 0x1234, cameraY, 0, 0);
            int bgY = ((cameraY & 0x7F8) >> 3) + 1;
            assertEquals(bgY, handler.getVscrollFactorBG());
            assertArrayEquals(scanlines(0x1234, bgY, words), actual,
                    "BgScroll_SBZ must select the correct sixteen-line bands at Y=" + cameraY);
        }
    }

    @Test
    void fractionalMovementRetainsThreeIndependentBuildingCamerasAndRewinds() throws Exception {
        ZoneScrollHandler handler = sbz();
        int[] actual = new int[224];
        handler.update(actual, 0x1234, 1536, 0, 0);
        Object start = handler.captureRewindState();
        handler.update(actual, 0x1237, 1543, 1, 0);
        assertEquals(193, handler.getVscrollFactorBG());
        assertArrayEquals(scanlines(0x1237, 193,
                nativeWords(-1166, -1094, -1021, -948, -4660, -4661, -4661)), actual);
        handler.update(actual, 0x1238, 1544, 2, 0);
        assertEquals(194, handler.getVscrollFactorBG());
        assertArrayEquals(scanlines(0x1238, 194,
                nativeWords(-1166, -1094, -1021, -948, -4661, -4661, -4662)), actual);
        assertEquals(4661, handler.getBgCameraX(), "the cache must start at the leftmost visible band");
        handler.restoreRewindState(start);
        handler.update(actual, 0x1233, 1528, 3, 0);
        assertEquals(192, handler.getVscrollFactorBG());
        assertArrayEquals(scanlines(0x1233, 192,
                nativeWords(-1165, -1093, -1020, -947, -4659, -4659, -4659)), actual);
    }

    @Test
    void actTwoKeepsUniformScrollingAndFinalZoneOwnsSeparateState() throws Exception {
        var provider = new Sonic1ScrollHandlerProvider();
        provider.load(null);
        var sbz = provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ);
        var fz = provider.getHandler(Sonic1ZoneConstants.ZONE_FZ);
        assertNotSame(sbz, fz);
        int[] actual = new int[224];
        sbz.update(actual, 100, 15, 0, 1);
        assertEquals(2, sbz.getVscrollFactorBG());
        sbz.update(actual, 104, 23, 1, 1);
        assertArrayEquals(uniform(104, 101), actual);
        assertEquals(3, sbz.getVscrollFactorBG());
        fz.update(actual, 500, 80, 0, 2);
        assertArrayEquals(uniform(500, 500), actual);
        assertEquals(500, fz.getBgCameraX());
        assertEquals(11, fz.getVscrollFactorBG(), "FZ also enters through REV01 BgScroll_SBZ");
        sbz.update(actual, 108, 31, 2, 1);
        assertEquals(102, sbz.getBgCameraX());
        assertEquals(500, fz.getBgCameraX());
    }

    private ZoneScrollHandler sbz() throws Exception {
        var provider = new Sonic1ScrollHandlerProvider();
        provider.load(null);
        return provider.getHandler(Sonic1ZoneConstants.ZONE_SBZ);
    }

    private static short[] nativeWords(int c0, int c1, int c2, int c3,
                                     int distant, int upper, int lower) {
        short[] words = new short[32];
        words[0] = (short) c0;
        words[1] = (short) c1;
        words[2] = (short) c2;
        words[3] = (short) c3;
        Arrays.fill(words, 4, 14, (short) distant);
        Arrays.fill(words, 14, 21, (short) upper);
        Arrays.fill(words, 21, 32, (short) lower);
        return words;
    }

    private static int[] scanlines(int cameraX, int bgY, short[] words) {
        int[] expected = new int[224];
        for (int line = 0; line < expected.length; line++) {
            int index = ((bgY & 0x1F0) + (bgY & 15) + line) >> 4;
            expected[line] = ((-cameraX & 0xFFFF) << 16) | (words[index] & 0xFFFF);
        }
        return expected;
    }

    private static int[] uniform(int cameraX, int bgX) {
        int[] expected = new int[224];
        Arrays.fill(expected, ((-cameraX & 0xFFFF) << 16) | (-bgX & 0xFFFF));
        return expected;
    }
}
