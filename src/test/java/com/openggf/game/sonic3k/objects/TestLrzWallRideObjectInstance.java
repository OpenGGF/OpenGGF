package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZWallRide} (sonic3k.asm:87693-87795, ROM {@code $4254A}).
 *
 * <p>The offsets asserted here were computed from the ROM's own {@code SineTable}
 * ({@code Levels/Misc/sine.bin}) through the routine's arithmetic, not read back from the class:
 * {@code (sin(param >> 1) * $A00) << 4} taken as a high word for X, and
 * {@code ((($100 - cos(param >> 1)) & $FFFF) >> 1) * amplitude >> 8} for Y.
 */
class TestLrzWallRideObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_FLAME_THROWER;
    /** The act 1 placement, {@code ($1E90,$5E8)} with render flags 0. */
    private static final int BASE_X = 0x1E90;
    private static final int BASE_Y = 0x05E8;

    /**
     * {@code sub}, {@code addi.w #$10}, {@code cmpi.w #$20}, {@code bhs} on both axes
     * (sonic3k.asm:87730-87736). Unsigned, so the band is {@code [-$10, $10)} on each axis and a
     * rider one pixel outside either edge is not caught.
     */
    @Test
    void captureBoxIsAHalfOpenThirtyTwoPixelSquare() {
        int[][] cases = {
                {0, 0, 1}, {-0x10, 0, 1}, {0x0F, 0, 1}, {0, -0x10, 1}, {0, 0x0F, 1},
                {-0x11, 0, 0}, {0x10, 0, 0}, {0, -0x11, 0}, {0, 0x10, 0},
        };
        for (int[] testCase : cases) {
            LrzWallRideObjectInstance ride = ride(false);
            TestablePlayableSprite player = runner(BASE_X + testCase[0], BASE_Y + testCase[1]);
            ride.setServices(services());
            ride.update(1, player);
            assertEquals(testCase[2] == 1, ride.isRidingFor(true),
                    "offset (" + testCase[0] + "," + testCase[1] + ")");
        }
    }

    /** {@code tst.b object_control(a1)}, {@code btst #Status_InAir}, {@code tst.w ground_vel}. */
    @Test
    void captureRejectsAnAirborneOrLeftwardRunner() {
        LrzWallRideObjectInstance ride = ride(false);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        ride.setServices(services());

        player.setAirForTest(true);
        ride.update(1, player);
        assertFalse(ride.isRidingFor(true), "an airborne runner is not caught");

        player.setAirForTest(false);
        player.setGSpeed((short) -0x0600);
        ride.update(2, player);
        assertFalse(ride.isRidingFor(true), "a rightward ride does not catch a leftward runner");
    }

    /** {@code cmpi.w #$400,ground_vel(a1)} / {@code move.w #$400} (sonic3k.asm:87751-87754). */
    @Test
    void captureFloorsTheSpeedAtFourHundredHex() {
        LrzWallRideObjectInstance ride = ride(false);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        player.setGSpeed((short) 0x0100);
        ride.setServices(services());
        ride.update(1, player);
        assertTrue(ride.isRidingFor(true));
        assertEquals(0x0400, player.getGSpeed(), "a slow runner is sped up to $400");
        assertTrue(player.getAir(), "sub_42636 sets Status_InAir, unlike the corkscrew's copy");
        assertEquals(0, ride.accumulatorFor(true), "move.l #0,(a2)");
    }

    /**
     * {@code loc_425EA} (sonic3k.asm:87766-87780): the leftward placement wants a leftward runner,
     * floors it at {@code -$400} and then negates it, so the accumulator runs forwards either way.
     */
    @Test
    void theLeftwardPlacementCatchesALeftwardRunnerAndReversesIt() {
        LrzWallRideObjectInstance ride = ride(true);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        player.setGSpeed((short) -0x0100);
        ride.setServices(services());
        ride.update(1, player);
        assertTrue(ride.isRidingFor(true));
        assertEquals(0x0400, player.getGSpeed(), "floored at -$400, then neg.w");
        assertEquals(Direction.LEFT, player.getDirection(), "bset #Status_Facing");
    }

    /**
     * {@code lsl.l #7,d0 / add.l d0,(a2)} (sonic3k.asm:87839-87840): one bit less than the
     * corkscrew's {@code lsl.l #8}, and {@code ground_vel} climbs by {@code $10} a ridden frame.
     */
    @Test
    void theAccumulatorStepsBySpeedShiftedSevenAndTheSpeedClimbsBySixteen() {
        LrzWallRideObjectInstance ride = ride(false);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        player.setGSpeed((short) 0x0400);
        ride.setServices(services());
        ride.update(1, player);
        assertEquals(0, ride.accumulatorFor(true), "the capture frame zeroes it");

        ride.update(2, player);
        assertEquals(0x0400 << 7, ride.accumulatorFor(true), "one step of ground_vel << 7");
        assertEquals(0x0410, player.getGSpeed(), "addi.w #$10,ground_vel(a1)");
    }

    /** {@code cmpi.w #$100,(a2)} on the accumulator's HIGH word (sonic3k.asm:87843-87844). */
    @Test
    void theRideEndsWhenTheHighWordReachesOneHundredHex() {
        LrzWallRideObjectInstance ride = ride(false);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        player.setGSpeed((short) 0x1000);
        ride.setServices(services());
        ride.update(1, player);

        int frame = 2;
        while (ride.isRidingFor(true) && frame < 4000) {
            ride.update(frame++, player);
        }
        assertFalse(ride.isRidingFor(true), "the ride ends");
        assertTrue(ride.rideParameter(true) >= 0x100, "and only once the high word reached $100");
        assertFalse(player.getAir(), "the rightward release puts the rider back on the ground");
        assertEquals(Direction.LEFT, player.getDirection(), "facing the way it is now travelling");
        assertTrue(player.getGSpeed() < 0, "neg.w ground_vel(a1) sends it back the way it came");
        assertEquals(player.getGSpeed(), player.getXSpeed(), "move.w ground_vel(a1),x_vel(a1)");
        assertEquals(0, player.getYSpeed(), "move.w #0,y_vel(a1)");
    }

    /** X offsets straight from the ROM sine table through {@code muls.w #$A00 / asl.l #4 / swap}. */
    @Test
    void rideXOffsetIsTheRomSineTimesAThousandHexTakenHigh() {
        int[][] cases = {{0x00, 0}, {0x20, 60}, {0x40, 113}, {0x80, 160}, {0xC0, 113}, {0xFE, 3}};
        for (int[] testCase : cases) {
            assertEquals(testCase[1], LrzWallRideObjectInstance.rideXOffset(testCase[0], false),
                    "param $" + Integer.toHexString(testCase[0]));
            assertEquals(-testCase[1], LrzWallRideObjectInstance.rideXOffset(testCase[0], true),
                    "the leftward placement negates it");
        }
    }

    /** Y offsets from {@code neg.w / addi.w #$100 / lsr.w #1 / mulu.w d2 / lsr.l #8}. */
    @Test
    void rideYOffsetIsTheUnsignedCosineRiseScaledByTheAmplitude() {
        int[][] cases = {{0x00, 0}, {0x20, 13}, {0x40, 51}, {0x80, 178}, {0xC0, 304}, {0xFE, 355}};
        for (int[] testCase : cases) {
            assertEquals(testCase[1], LrzWallRideObjectInstance.rideYOffset(testCase[0], false),
                    "param $" + Integer.toHexString(testCase[0]));
        }
        // $142 instead of $165 makes the same curve shallower.
        // cos($40) = 0, so the rise is $80; $80 * $142 >> 8 = 161.
        assertEquals(161, LrzWallRideObjectInstance.rideYOffset(0x80, true), "leftward amplitude");
    }

    /** {@code RawAni_42792} through {@code divu.w #$16} (sonic3k.asm:87884-87888). */
    @Test
    void rideMappingFrameIndexesTheTwelveRawFrames() {
        assertEquals(0xEF, LrzWallRideObjectInstance.rideMappingFrame(0x00));
        assertEquals(0xDF, LrzWallRideObjectInstance.rideMappingFrame(0x40));
        assertEquals(0xE0, LrzWallRideObjectInstance.rideMappingFrame(0x80));
        assertEquals(0xE3, LrzWallRideObjectInstance.rideMappingFrame(0xFE));
    }

    /**
     * The live {@code FixBugs = 0} defect: {@code d2} holds the amplitude, not the saved previous
     * X, when {@code sub.w d2,d0} makes the X velocity (sonic3k.asm:87852, :87859, :87868). The
     * first ridden frame therefore reports {@code ((BASE_X + 0) - $165) << 8} as a word, which has
     * nothing to do with the frame's displacement -- and the Y velocity, computed from a properly
     * saved previous Y, is a real delta on the same frame.
     */
    @Test
    void theXVelocityIsTheAmplitudeBugAndTheYVelocityIsNot() {
        LrzWallRideObjectInstance ride = ride(false);
        TestablePlayableSprite player = runner(BASE_X, BASE_Y);
        player.setGSpeed((short) 0x0400);
        ride.setServices(services());
        ride.update(1, player);
        ride.update(2, player);

        int parameter = ride.rideParameter(true);
        int expectedX = (BASE_X + LrzWallRideObjectInstance.rideXOffset(parameter, false)) & 0xFFFF;
        int expectedY = (BASE_Y + LrzWallRideObjectInstance.rideYOffset(parameter, false)) & 0xFFFF;
        assertEquals(expectedX, player.getCentreX(), "x_pos");
        assertEquals(expectedY, player.getCentreY(), "y_pos");
        assertEquals((short) (((expectedX - 0x165) & 0xFFFF) << 8), player.getXSpeed(),
                "x_vel subtracts the amplitude, not the previous x_pos");
        assertEquals((short) (((expectedY - BASE_Y) & 0xFFFF) << 8), player.getYSpeed(),
                "y_vel is the real displacement");
    }

    private static LrzWallRideObjectInstance ride(boolean leftward) {
        return new LrzWallRideObjectInstance(new ObjectSpawn(
                BASE_X, BASE_Y, OBJECT_ID, 0, leftward ? 1 : 0, false, 0));
    }

    private static TestablePlayableSprite runner(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setAirForTest(false);
        player.setGSpeed((short) 0x0600);
        player.setDirection(Direction.RIGHT);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
