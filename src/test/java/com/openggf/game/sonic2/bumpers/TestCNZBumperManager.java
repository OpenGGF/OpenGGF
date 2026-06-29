package com.openggf.game.sonic2.bumpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestCNZBumperManager {
    @Test
    void angleBounceForcesSurfaceWhenWordDeltaExceedsThreshold() {
        assertEquals(0x08, CNZBumperManager.resolveAngleBounceOutAngle(0xFD, 0x08));
    }

    @Test
    void angleBounceReflectsWhenWordDeltaIsWithinThreshold() {
        assertEquals(0x00, CNZBumperManager.resolveAngleBounceOutAngle(0x10, 0x08));
        assertEquals(0x0B, CNZBumperManager.resolveAngleBounceOutAngle(0x05, 0x08));
    }
}
