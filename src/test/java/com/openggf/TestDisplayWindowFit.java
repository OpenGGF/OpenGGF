package com.openggf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** A launch that changes the display aspect refits the window instead of letterboxing in the old one. */
class TestDisplayWindowFit {
    @Test
    void keepsTheHeightScaleAndTakesTheAspectsWidth() {
        // The 4:3 default window (640x448) for a mod that asks for 16:9 (400x224): 800x448, no borders.
        assertArrayEquals(new int[] {800, 448}, DisplayWindowFit.fit(640, 448, 400, 224, 1920, 1080));
        // And back to 4:3 at the master title.
        assertArrayEquals(new int[] {640, 448}, DisplayWindowFit.fit(800, 448, 320, 224, 1920, 1080));
        // A 3x window stays 3x.
        assertArrayEquals(new int[] {1200, 672}, DisplayWindowFit.fit(960, 672, 400, 224, 2560, 1440));
        // A height between scales rounds to the nearest.
        assertArrayEquals(new int[] {800, 448}, DisplayWindowFit.fit(700, 500, 400, 224, 1920, 1080));
    }

    @Test
    void staysOnTheMonitorAndNeverBelowOneTimes() {
        // 32:9 at 3x would be 2400 wide: a 1920-wide monitor holds 2x.
        assertArrayEquals(new int[] {1600, 448}, DisplayWindowFit.fit(960, 672, 800, 224, 1920, 1080));
        assertArrayEquals(new int[] {400, 224}, DisplayWindowFit.fit(100, 50, 400, 224, 1920, 1080));
        assertArrayEquals(new int[] {400, 224}, DisplayWindowFit.fit(640, 448, 400, 224, 300, 200));
        // Unknown monitor: no limit.
        assertArrayEquals(new int[] {1200, 672}, DisplayWindowFit.fit(960, 672, 400, 224, 0, 0));
    }
}
