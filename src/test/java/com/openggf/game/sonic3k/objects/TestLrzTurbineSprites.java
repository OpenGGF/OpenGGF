package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Expectations from sub_44338, loc_44448 and sub_4450A, including logical press-byte input. */
class TestLrzTurbineSprites {
    private static final int X = 0xB80;
    private static final int Y = 0x600;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @Test
    void captureBandsUseUnsignedHalfOpenBoundsAndMiddleRequiresHighPriority() {
        int[][] cases = {
                {-16, -80, 0, 1}, {15, -57, 0, 1}, {-17, -80, 0, 0}, {16, -80, 0, 0},
                {0, -81, 0, 0}, {0, -56, 0, 0}, {0, -16, 0, 0}, {0, -16, 1, 1},
                {0, 7, 1, 1}, {0, 8, 1, 0}, {0, 48, 0, 1}, {0, 71, 0, 1}, {0, 72, 0, 0}
        };
        for (int[] c : cases) {
            var turbine = turbine(0);
            var player = player(c[0], c[1]);
            player.setHighPriority(c[2] != 0);
            turbine.update(4, player);
            assertEquals(c[3] != 0, turbine.isCaptured(0), java.util.Arrays.toString(c));
        }
    }

    @Test
    void captureUsesLevelClockThenMovesOnTheSamePassAndLoopsAfter64Steps() {
        var turbine = turbine(0);
        var player = player(5, -80);
        player.setXSpeed((short) 0x600);
        turbine.update(4, player); // angle $70, byte_44572[14] = -$3A.
        assertTrue(turbine.isCaptured(0));
        assertEquals(X, player.getCentreX());
        assertEquals(Y - 0x3A, player.getCentreY());
        assertEquals(0x66, player.getMappingFrame());
        assertEquals(0x74, turbine.rideAngle(0));
        assertEquals(0, player.getXSpeed());
        assertTrue(player.isObjectControlled());
        for (int frame = 5; frame <= 8; frame++) turbine.update(frame, player);
        assertEquals(Y - 0x43, player.getCentreY()); // angle $80.
        assertFalse(player.isHighPriority());
        assertEquals(5, player.getPriorityBucket());
        for (int frame = 9; frame <= 68; frame++) turbine.update(frame, player);
        assertEquals(Y - 0x3A, player.getCentreY());
        assertEquals(0x74, turbine.rideAngle(0));
        assertTrue(player.isHighPriority());
        assertEquals(2, player.getPriorityBucket());
    }

    @Test
    void heldJumpDoesNotReleaseButLogicalPressDoes() {
        var turbine = turbine(0);
        var player = player(0, -80);
        player.setJumpInputPressed(true);
        player.setLogicalInputState(false, false, false, false, true, false);
        turbine.update(4, player);
        turbine.update(5, player);
        assertTrue(turbine.isCaptured(0), "Ctrl logical low byte is press, not hold");
        player.setLogicalInputState(false, false, false, false, true, true);
        turbine.update(6, player);
        assertFalse(turbine.isCaptured(0));
        assertEquals(20, turbine.cooldown(0));
        assertFalse(player.isObjectControlled());
        assertTrue(player.getAir());
        assertFalse(player.isJumping());
        // angle $78 -> byte_443B4[7] | 8 = $68; ROM sine $68 = $8E.
        assertEquals(-0x8E * 12, player.getYSpeed());
    }

    @Test
    void cooldownExpiresWithoutRecapturingOnItsLastPass() {
        var turbine = turbine(0);
        var player = player(0, -80);
        turbine.update(4, player);
        player.setLogicalInputState(false, false, false, false, true, true);
        turbine.update(5, player);
        player.setLogicalInputState(false, false, false, false, false, false);
        player.setCentreY((short) (Y - 80));
        for (int frame = 6; frame <= 25; frame++) turbine.update(frame, player);
        assertEquals(0, turbine.cooldown(0));
        assertFalse(turbine.isCaptured(0));
        turbine.update(26, player);
        assertTrue(turbine.isCaptured(0));
    }

    @Test
    void theNativePlayersHaveIndependentCaptureAndReleaseState() {
        var turbine = turbine(0);
        var leader = player(0, -80);
        var follower = player(0, 48);
        turbine.setServices(new TestObjectServices().withSidekicks(java.util.List.of(follower)));
        turbine.update(4, leader);
        assertTrue(turbine.isCaptured(0));
        assertTrue(turbine.isCaptured(1));
        assertEquals(0x74, turbine.rideAngle(0));
        assertEquals(0xF4, turbine.rideAngle(1));
        follower.setLogicalInputState(false, false, false, false, false, true);
        turbine.update(5, leader);
        assertTrue(turbine.isCaptured(0));
        assertFalse(turbine.isCaptured(1));
        assertEquals(0, turbine.cooldown(0));
        assertEquals(20, turbine.cooldown(1));
        assertTrue(follower.getYSpeed() > 0, "bottom-half release points down");
    }

    @Test
    void thinVariantAnimatesAndHurtsWithoutCapturing() {
        var turbine = turbine(1);
        var player = player(0, -80);
        for (int frame = 0; frame < 16; frame++) {
            turbine.update(frame, player);
            assertEquals((frame >> 1) & 3, turbine.mappingFrame());
            assertFalse(turbine.isCaptured(0));
        }
        assertEquals(0xA0, turbine.getCollisionFlags());
        assertEquals(4, turbine.getOnScreenHalfWidth());
        assertEquals(4, turbine.getPriorityBucket());
        assertTrue(turbine.isHighPriority());
    }

    private static LrzTurbineSpritesObjectInstance turbine(int subtype) {
        var object = new LrzTurbineSpritesObjectInstance(new ObjectSpawn(X, Y, 0x32, subtype, 0, false, 0));
        object.setServices(new TestObjectServices());
        return object;
    }

    private static TestablePlayableSprite player(int dx, int dy) {
        var player = new TestablePlayableSprite("sonic", (short) X, (short) Y);
        player.setCentreX((short) (X + dx));
        player.setCentreY((short) (Y + dy));
        return player;
    }
}
