package com.openggf.sprites.playable.route;

import org.junit.jupiter.api.Test;

import static com.openggf.sprites.playable.route.NativeRouteSteering.*;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeRouteSteering {
    private static final int ACCEL = 12;
    private static final int DECEL = 128;
    private static final int FRICTION = 12;
    private static final int MAX_SPEED = 0x600;

    @Test void walkingReservesAccelerationHeadroomForEveryEnding() {
        for (EndingAction ending : EndingAction.values()) {
            var settings = new Settings(TravelSpeed.WALK, ending);
            assertEquals(1, steer(360, 360, 12, false, 1000, 1, settings), ending.toString());
            assertEquals(0, steer(360, 360, 13, false, 1000, 1, settings), ending.toString());
            assertEquals(1, steer(360, 360, 1000, true, 1000, 1, settings), ending.toString());
            assertEquals(0, steer(361, 361, 0, true, 1000, 1, settings), ending.toString());
            assertEquals(0, steer(0x174, STROLL_CAP, -8, false, 1000, 1, settings), ending.toString());
        }
    }

    @Test void walkingBrakesSlopeAndHorizontalOverspeedWithoutChangingNativeSpeed() {
        var walk = new Settings(TravelSpeed.WALK, EndingAction.HARD_STOP);
        assertEquals(-1, steer(STROLL_CAP + 1, STROLL_CAP + 1, 0, false, 1000, 1, walk));
        assertEquals(1, steer(-STROLL_CAP - 1, -STROLL_CAP - 1, 0, false, -1000, 1, walk));
        assertEquals(-1, steer(380, 380, 8, false, 1000, 1, walk));
        assertEquals(-1, steer(STROLL_CAP + 1, 300, 0, false, 1000, 1, walk));
        assertEquals(1, steer(0, 0, 1000, false, 1000, 1, walk),
                "Native slope resistance skips zero inertia");
        assertEquals(1, steer(-372, -372, 0, false, 1000, 1, walk),
                "Opposing input remains available even when acceleration headroom is exhausted");
    }

    @Test void walkingCapUsesTheLowerNativeMaximumAndScalesWithUrgency() {
        var walk = new Settings(TravelSpeed.WALK, EndingAction.HARD_STOP);
        assertEquals(1, direction(100, 116, 116, ACCEL, DECEL, FRICTION, 256,
                0, false, 1000, .5, walk, false, 1));
        assertEquals(0, direction(100, 117, 117, ACCEL, DECEL, FRICTION, 256,
                0, false, 1000, .5, walk, false, 1));
        assertEquals(-1, direction(100, 129, 129, ACCEL, DECEL, FRICTION, 256,
                0, false, 1000, .5, walk, false, 1));
        assertEquals(1, steer(0x200, 0x200, 0, false, 1000, 1,
                new Settings(TravelSpeed.RUN, EndingAction.HARD_STOP)), "Run has no stroll cap");
    }

    @Test void gentleArrivalCoastsWhileHardArrivalCountersteers() {
        var gentle = new Settings(TravelSpeed.RUN, EndingAction.GENTLE_STOP);
        var hard = new Settings(TravelSpeed.RUN, EndingAction.HARD_STOP);
        assertEquals(0, steer(0x200, 0x200, 0, false, 110, 1, gentle));
        assertEquals(-1, steer(0x200, 0x200, 0, false, 110, 1, hard));
        assertEquals(0, steer(-0x200, -0x200, 0, false, 110, 1, gentle));
        assertEquals(1, steer(-0x200, -0x200, 0, false, 110, 1, hard));
        assertEquals(0, steer(0, 0, 0, false, 103, 1, gentle));
        assertEquals(1, steer(0, 0, 0, false, 104, 1, gentle),
                "A stationary gentle arrival can make a small correction");
        assertEquals(0, steer(0, 0, 0, false, 104, 1, hard));
    }

    @Test void overshootUsesApproachSideThenLatchesIntoGentleReturn() {
        var overshoot = new Settings(TravelSpeed.RUN, EndingAction.OVERSHOOT);
        assertEquals(1, direction(100, 0, 0, ACCEL, DECEL, FRICTION, MAX_SPEED,
                0, false, 100, 1, overshoot, false, 1));
        assertEquals(-1, direction(100, 0, 0, ACCEL, DECEL, FRICTION, MAX_SPEED,
                0, false, 100, 1, overshoot, false, -1));
        assertEquals(0, direction(100, 256, 256, ACCEL, DECEL, FRICTION, MAX_SPEED,
                0, false, 100, 1, overshoot, true, 1));
        assertFalse(returning(117.999, 100, 1, false));
        assertTrue(returning(118, 100, 1, false));
        assertFalse(returning(82.001, 100, -1, false));
        assertTrue(returning(82, 100, -1, false));
        assertTrue(returning(-1000, 100, 1, true), "Return remains latched after crossing back");
    }

    @Test void zeroUrgencyBrakesUntilTheNativeStopThreshold() {
        assertEquals(-1, movementDirection(100, 0x200, ACCEL, DECEL, MAX_SPEED, false, 1000, 0));
        assertEquals(1, movementDirection(100, -0x200, ACCEL, DECEL, MAX_SPEED, false, -1000, 0));
        assertEquals(0, movementDirection(100, 128, ACCEL, DECEL, MAX_SPEED, false, 1000, 0));
        assertEquals(-1, movementDirection(100, 129, ACCEL, DECEL, MAX_SPEED, false, 1000, 0));
        assertEquals(0, movementDirection(100, 64, ACCEL, DECEL, MAX_SPEED, true, 1000, 0));
        assertEquals(-1, movementDirection(100, 65, ACCEL, DECEL, MAX_SPEED, true, 1000, 0));
    }

    @Test void airborneBrakingUsesDoubleAccelerationInsteadOfGroundDeceleration() {
        assertEquals(1, movementDirection(100, 0x400, ACCEL, DECEL, MAX_SPEED, false, 140, 1));
        assertEquals(-1, movementDirection(100, 0x400, ACCEL, DECEL, MAX_SPEED, true, 140, 1));
        assertEquals(-1, movementDirection(0, 256, 16, DECEL, MAX_SPEED, true, 9, 1));
        assertEquals(1, movementDirection(0, 256, 16, DECEL, MAX_SPEED, true, 10, 1));
    }

    @Test void movementIntentHonoursUrgencyCapAndCanReverseAboveIt() {
        assertEquals(1, movementDirection(100, 767, ACCEL, DECEL, MAX_SPEED, false, 1000, .5));
        assertEquals(0, movementDirection(100, 768, ACCEL, DECEL, MAX_SPEED, false, 1000, .5));
        assertEquals(1, movementDirection(100, -MAX_SPEED, ACCEL, DECEL, MAX_SPEED, false, 1000, .5));
        assertEquals(0, movementDirection(100, 0, ACCEL, DECEL, MAX_SPEED, false, 104, 1));
    }

    @Test void spindashFacesThenWaitsForNativeDuckBeforeAFreshJumpEdge() {
        assertEquals(new Buttons(-1, false, false, false, false),
                charge(100, 90, 0, 0, false, false, false, FRICTION, 0));
        assertEquals(new Buttons(1, false, false, false, false),
                charge(100, 100, 0, 0, false, false, false, FRICTION, 0));
        assertEquals(new Buttons(0, true, false, false, false),
                charge(100, 200, 1, 0, false, false, false, FRICTION, 0));
        assertEquals(new Buttons(0, true, true, false, false),
                charge(100, 200, 2, 0, false, true, false, FRICTION, 0));
        assertEquals(new Buttons(0, true, false, false, false),
                charge(100, 200, 3, 0, false, true, true, FRICTION, 0));
        assertEquals(new Buttons(0, true, false, false, false),
                charge(100, 200, 2, 0, true, true, false, FRICTION, 0));
    }

    @Test void spindashBrakesOnlyWhenDownhillPullDefeatsFrictionBeforeCharging() {
        assertEquals(new Buttons(-1, true, false, false, false),
                charge(100, 200, 2, 12, false, true, false, FRICTION, 12));
        assertEquals(new Buttons(1, true, false, false, false),
                charge(100, 200, 2, -12, false, true, false, FRICTION, -12));
        assertEquals(new Buttons(0, true, true, false, false),
                charge(100, 200, 2, 12, false, true, false, FRICTION, 11));
        assertEquals(new Buttons(0, true, true, false, false),
                charge(100, 200, 2, 12, false, true, false, FRICTION, -12));
        assertEquals(new Buttons(0, true, false, false, false),
                charge(100, 200, 2, 12, false, true, true, FRICTION, 12));
    }

    @Test void spindashKeepsTheExistingTwentyFourTickWindowAndReleasesAfterIt() {
        assertEquals(24, CHARGE_FRAMES);
        assertEquals(new Buttons(0, true, true, false, false),
                charge(100, 200, 24, 0, false, true, false, FRICTION, 0));
        assertEquals(new Buttons(0, false, false, false, true),
                charge(100, 200, 25, 12, false, true, false, FRICTION, 12));
    }

    private static int steer(int xSpeed, int groundSpeed, int slopeImpulse, boolean airborne,
            double targetX, double urgency, Settings settings) {
        return direction(100, xSpeed, groundSpeed, ACCEL, DECEL, FRICTION, MAX_SPEED,
                slopeImpulse, airborne, targetX, urgency, settings, false, targetX < 100 ? -1 : 1);
    }
}
