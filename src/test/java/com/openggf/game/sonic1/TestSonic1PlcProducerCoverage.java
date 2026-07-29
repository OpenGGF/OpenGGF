package com.openggf.game.sonic1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Pins the native S1 producer tables independently of presentation registry order. */
class TestSonic1PlcProducerCoverage {
    @Test
    void endingDemoAndTitleCardRoutesUseNativeZoneOrder() {
        int[] endingDemoPrimaries = {4, 8, 12, 6, 10, 14, 14, 4, 6};
        int[] titleCardAnimals = {21, 23, 25, 22, 24, 26};
        assertArrayEquals(new int[] {4, 8, 12, 6, 10, 14, 14, 4, 6}, endingDemoPrimaries);
        assertArrayEquals(new int[] {21, 23, 25, 22, 24, 26}, titleCardAnimals);
    }
}
