package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLrzChainedPlatformObject {

    @Test
    void subtypeOneTakesWord4a896sFirstCounterClockwiseLeg() {
        var platform = platform(0x01, 0);

        platform.update(0, null);

        assertEquals(0, platform.pathForTest());
        assertEquals(1, platform.waypointForTest());
        assertEquals(-0x100, platform.xVelocityForTest());
        assertEquals(0x74, platform.yVelocityForTest());
        assertEquals(0x0FFF, platform.getX());
        assertEquals(0x1000, platform.getY());
    }

    @Test
    void xFlipStartsOneWaypointEarlierAndReversesThePath() {
        var platform = platform(0x01, 1);

        platform.update(0, null);

        assertEquals(9, platform.waypointForTest());
        assertEquals(0x100, platform.xVelocityForTest());
        assertEquals(0x74, platform.yVelocityForTest());
        assertEquals(0x1001, platform.getX());
        assertEquals(0x1000, platform.getY());
    }

    @Test
    void subtypeTwentyThreeSelectsTheLongPathAndFourthWaypoint() {
        var platform = platform(0x23, 0);

        platform.update(0, null);

        assertEquals(2, platform.pathForTest());
        assertEquals(3, platform.waypointForTest());
        assertEquals(-0x12, platform.xVelocityForTest());
        assertEquals(0x100, platform.yVelocityForTest());
    }

    private static LrzChainedPlatformObjectInstance platform(int subtype, int flags) {
        return new LrzChainedPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x25, subtype, flags, false, 0));
    }
}
