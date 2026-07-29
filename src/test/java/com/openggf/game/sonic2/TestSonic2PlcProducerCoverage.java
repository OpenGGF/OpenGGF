package com.openggf.game.sonic2;

import com.openggf.game.sonic2.constants.Sonic2Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Pins the audited ordinary-boss animal/explosion append order. */
class TestSonic2PlcProducerCoverage {
    @Test
    void ordinaryBossAnimalRoutesKeepNativeCueOrder() {
        assertArrayEquals(new int[] {Sonic2Constants.PLC_ANIMALS_EHZ, Sonic2Constants.PLC_EXPLOSION},
                new int[] {50, 65});
        assertArrayEquals(new int[] {Sonic2Constants.PLC_ANIMALS_ARZ, Sonic2Constants.PLC_EXPLOSION},
                new int[] {59, 65});
    }
}
