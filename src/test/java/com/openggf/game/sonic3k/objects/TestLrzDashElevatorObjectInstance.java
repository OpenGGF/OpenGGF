package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZDashElevator} (sonic3k.asm:88381-88495).
 *
 * <p>Expectations are the routine's own arithmetic, written out per case: the travel range is
 * {@code (subtype & $7F) * 8}, bit 7 starts the platform {@code $20} in, and a flipped placement
 * starts it at the far end with its base Y a whole range higher (:88390-88403). A rider only
 * latches while {@code anim} is {@code 9}, contributes {@code 8 + spin_dash_counter} negated when
 * it faces right, and the sum drives a 16.16 position at one eighth of that per frame
 * (:88415-88416, :88483-88491).
 */
class TestLrzDashElevatorObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_SPIN_LAUNCHER;
    private static final int BASE_Y = 0x0600;

    /** The six act-1 placements, plus the two bit-7 and flip combinations they cover. */
    @Test
    void initDecodesRangeStartOffsetAndBaseY() {
        // subtype, flipped, expected range, expected start offset, expected base Y delta
        int[][] cases = {
                {0x1A, 0, 0x1A * 8, 0, 0},
                {0x1D, 0, 0x1D * 8, 0, 0},
                {0x20, 0, 0x20 * 8, 0, 0},
                {0x46, 0, 0x46 * 8, 0, 0},
                // Bit 7 set: moveq #$20,d1 seeds $30 before the range is computed.
                {0xB6, 0, 0x36 * 8, 0x20, 0},
                {0xB9, 0, 0x39 * 8, 0x20, 0},
                // status bit 0 set: $30 = range - d1 and $46 -= range.
                {0x1A, 1, 0x1A * 8, 0x1A * 8, -(0x1A * 8)},
                {0xB6, 1, 0x36 * 8, 0x36 * 8 - 0x20, -(0x36 * 8)},
        };

        for (int[] testCase : cases) {
            LrzDashElevatorObjectInstance elevator = elevator(testCase[0], testCase[1] != 0);
            String label = "subtype $" + Integer.toHexString(testCase[0])
                    + (testCase[1] != 0 ? " flipped" : "");
            assertEquals(testCase[2] << 16, elevator.maxPosition(), label + " range");
            assertEquals(testCase[3] << 16, elevator.position(), label + " start offset");
            assertEquals((BASE_Y + testCase[4]) & 0xFFFF, elevator.baseY(), label + " base Y");
        }
    }

    /**
     * {@code addi.w}/{@code subi.w #$40} plus {@code bcc} (sonic3k.asm:88472-88482): the carry out
     * of the 16-bit operation clamps at zero, so a speed already inside {@code $40} lands exactly
     * on zero rather than crossing it.
     */
    @Test
    void groundVelocityDrainClampsAtZero() {
        assertEquals(0x05C0, LrzDashElevatorObjectInstance.drainGroundVelocity(0x0600));
        assertEquals(0x0001, LrzDashElevatorObjectInstance.drainGroundVelocity(0x0041));
        assertEquals(0, LrzDashElevatorObjectInstance.drainGroundVelocity(0x0040));
        assertEquals(0, LrzDashElevatorObjectInstance.drainGroundVelocity(0x003F));
        assertEquals(-0x05C0, LrzDashElevatorObjectInstance.drainGroundVelocity(-0x0600));
        assertEquals(-0x0001, LrzDashElevatorObjectInstance.drainGroundVelocity(-0x0041));
        assertEquals(0, LrzDashElevatorObjectInstance.drainGroundVelocity(-0x0040));
        assertEquals(0, LrzDashElevatorObjectInstance.drainGroundVelocity(-0x003F));
    }

    /**
     * {@code cmpi.b #9,anim(a1)} is the only way into a ride (sonic3k.asm:88473-88474), so a
     * character that never produces the spindash animation - the S1 donor - stands on the platform
     * without ever moving it.
     */
    @Test
    void onlyASpindashAnimationStartsARide() {
        LrzDashElevatorObjectInstance elevator = elevator(0x20, false);
        TestablePlayableSprite player = standingPlayer(elevator);
        elevator.setServices(services(player));

        for (int animation : new int[] {0, 1, Sonic3kAnimationIds.ROLL.id(), 3}) {
            player.setAnimationId(animation);
            elevator.onSolidContact(player, standingContact(), 0);
            elevator.update(1, player);
            assertFalse(elevator.isRiding(true), "anim " + animation + " must not latch");
            assertEquals(0, elevator.position(), "anim " + animation + " must not move the platform");
        }

        player.setAnimationId(Sonic3kAnimationIds.SPINDASH.id());
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
        assertTrue(elevator.isRiding(true), "anim 9 latches the rider");
        assertEquals(0, elevator.position(),
                "the latch frame only writes x_pos and the flag; the push starts next frame");
    }

    /**
     * {@code moveq #8,d0 / add.b spin_dash_counter(a1),d0}, negated when {@code Status_Facing} is
     * clear, then {@code swap / asr.l #3}: eight units of push is one pixel a frame.
     */
    @Test
    void aLatchedRiderDrivesThePlatformOnePixelPerEightUnitsOfPush() {
        LrzDashElevatorObjectInstance elevator = elevator(0x20, false);
        TestablePlayableSprite player = standingPlayer(elevator);
        elevator.setServices(services(player));
        latch(elevator, player);

        // Facing left keeps the push positive, which drives the platform down.
        player.setDirection(Direction.LEFT);
        player.setSpindashCounter((short) 0);
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
        assertEquals(1 << 16, elevator.position(), "8 / 8 = one pixel down");

        // A charged spindash counter adds to the same byte before the shift.
        player.setSpindashCounter((short) 8);
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
        assertEquals((1 << 16) + ((16 << 16) >> 3), elevator.position(), "(8 + 8) / 8 = two pixels");

        // Facing right negates the push and the platform climbs back, clamping at zero.
        player.setDirection(Direction.RIGHT);
        player.setSpindashCounter((short) 0);
        for (int frame = 0; frame < 8; frame++) {
            elevator.onSolidContact(player, standingContact(), 0);
            elevator.update(1, player);
        }
        assertEquals(0, elevator.position(), "bpl loc_42FA4 clamps the position at zero");
    }

    /** {@code cmp.l d1,d0 / blo} clamps at {@code $34(a0)} (sonic3k.asm:88424-88428). */
    @Test
    void thePlatformStopsAtItsTravelRange() {
        // A one-pixel range: eight frames of a plain push would overshoot it.
        LrzDashElevatorObjectInstance elevator = elevator(0x01, false);
        TestablePlayableSprite player = standingPlayer(elevator);
        elevator.setServices(services(player));
        latch(elevator, player);
        player.setDirection(Direction.LEFT);

        for (int frame = 0; frame < 16; frame++) {
            elevator.onSolidContact(player, standingContact(), 0);
            elevator.update(1, player);
        }
        assertEquals(8 << 16, elevator.position(), "clamped at (subtype & $7F) * 8 pixels");
    }

    /**
     * {@code loc_4303A} (sonic3k.asm:88489-88492): leaving the ground, or any animation but roll
     * and spindash, clears {@code x_vel} and drops the ride flag.
     */
    @Test
    void leavingTheGroundOrTheRollAnimationReleasesTheRider() {
        LrzDashElevatorObjectInstance elevator = elevator(0x20, false);
        TestablePlayableSprite player = standingPlayer(elevator);
        elevator.setServices(services(player));
        latch(elevator, player);

        player.setXSpeed((short) 0x0400);
        player.setAirForTest(true);
        elevator.update(1, player);
        assertFalse(elevator.isRiding(true), "an airborne rider is released");
        assertEquals(0, player.getXSpeed(), "release clears x_vel");

        // Re-latch, then leave through the animation instead.
        player.setAirForTest(false);
        latch(elevator, player);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
        assertTrue(elevator.isRiding(true), "anim 2 keeps the ride");

        player.setAnimationId(0);
        player.setXSpeed((short) 0x0400);
        elevator.update(1, player);
        assertFalse(elevator.isRiding(true), "any other animation releases the rider");
        assertEquals(0, player.getXSpeed());
    }

    /** {@code SolidObjectFull} arguments {@code $2B}, 8, 9 (sonic3k.asm:88462-88465). */
    @Test
    void solidParamsAreTheRoutineArguments() {
        LrzDashElevatorObjectInstance elevator = elevator(0x20, false);
        assertEquals(0x2B, elevator.getSolidParams().halfWidth());
        assertEquals(8, elevator.getSolidParams().airHalfHeight());
        assertEquals(9, elevator.getSolidParams().groundHalfHeight());
    }

    private static void latch(LrzDashElevatorObjectInstance elevator, TestablePlayableSprite player) {
        player.setAirForTest(false);
        player.setAnimationId(Sonic3kAnimationIds.SPINDASH.id());
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
    }

    private static LrzDashElevatorObjectInstance elevator(int subtype, boolean flipped) {
        return new LrzDashElevatorObjectInstance(new ObjectSpawn(
                0x1000, BASE_Y, OBJECT_ID, subtype, flipped ? 1 : 0, false, 0));
    }

    private static TestablePlayableSprite standingPlayer(LrzDashElevatorObjectInstance elevator) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) elevator.getCentreX(), (short) BASE_Y);
        player.setCentreX((short) elevator.getCentreX());
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        player.setDirection(Direction.LEFT);
        return player;
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static TestObjectServices services(TestablePlayableSprite player) {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
