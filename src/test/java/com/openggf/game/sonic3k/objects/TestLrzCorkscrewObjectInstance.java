package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZCorkscrew} (sonic3k.asm:87494-87663).
 *
 * <p>Expectations come from the routine's own arithmetic and from the three ROM tables it indexes,
 * which were read out of the user-supplied ROM image at {@code $4247E}, {@code $4248A} and
 * {@code $4250A} and found byte-identical to the disassembly's {@code dc.b} listings. The three
 * table-driven helpers are checked against values recomputed here from the ROM's own operations
 * rather than from the class under test.
 */
class TestLrzCorkscrewObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_PLAYER_LAUNCHER;
    private static final int BASE_X = 0x0800;
    private static final int BASE_Y = 0x0600;

    /**
     * {@code sub_42278} head (sonic3k.asm:87527-87537). The horizontal test is an unsigned borrow
     * plus a signed {@code bge #$20}, so it is half-open; the vertical test is a signed
     * {@code bgt #$20}, so it includes its far edge. That asymmetry is the ROM's, not a typo.
     */
    @Test
    void captureBoxIsHalfOpenHorizontallyAndInclusiveVertically() {
        // dx measured as the ROM does: (player.x + $10) - object.x must be in [0, $20).
        assertTrue(captures(BASE_X - 0x10, BASE_Y), "left edge: dx = 0");
        assertTrue(captures(BASE_X + 0x0F, BASE_Y), "dx = $1F");
        assertFalse(captures(BASE_X - 0x11, BASE_Y), "one past the left edge borrows");
        assertFalse(captures(BASE_X + 0x10, BASE_Y), "dx = $20 is excluded");

        // dy measured as the ROM does: (player.y - object.y) + $10 must be <= $20.
        assertTrue(captures(BASE_X, BASE_Y + 0x10), "dy = $20 is included");
        assertFalse(captures(BASE_X, BASE_Y + 0x11), "dy = $21 is excluded");
        assertTrue(captures(BASE_X, BASE_Y - 0x40), "no lower bound on the vertical test");
    }

    /** {@code tst.b object_control} / {@code btst #Status_InAir} / {@code tst.w ground_vel}. */
    @Test
    void captureRefusesAirborneAndLeftwardRiders() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite airborne = riderAt(BASE_X, BASE_Y);
        airborne.setAirForTest(true);
        corkscrew.update(1, airborne);
        assertFalse(corkscrew.isRidingFor(true), "an airborne player is not caught");

        LrzCorkscrewObjectInstance second = corkscrew();
        TestablePlayableSprite leftward = riderAt(BASE_X, BASE_Y);
        leftward.setGSpeed((short) -0x0400);
        second.update(1, leftward);
        assertFalse(second.isRidingFor(true), "a player moving left is not caught");
    }

    /** {@code cmpi.w #$600,ground_vel(a1)} / {@code move.w #$600,ground_vel(a1)} (:87540-87542). */
    @Test
    void captureFloorsGroundVelocityAtSixHundred() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite slow = riderAt(BASE_X, BASE_Y);
        slow.setGSpeed((short) 0x0100);
        corkscrew.update(1, slow);
        assertTrue(corkscrew.isRidingFor(true));
        // The capture frame floors the speed; the ride's own $10 step is applied from the next
        // frame, so the value visible here is exactly the floor.
        assertEquals(0x0600, slow.getGSpeed(), "a slow rider is sped up to $600");

        LrzCorkscrewObjectInstance fast = corkscrew();
        TestablePlayableSprite quick = riderAt(BASE_X, BASE_Y);
        quick.setGSpeed((short) 0x0C00);
        fast.update(1, quick);
        assertEquals(0x0C00, quick.getGSpeed(), "a faster rider keeps its speed");
    }

    /** {@code move.b #$43,object_control(a1)} plus the state resets at {@code loc_422E6}. */
    @Test
    void captureTakesControlAndResetsTheRidersState() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite rider = riderAt(BASE_X, BASE_Y);
        rider.setGSpeed((short) 0x0800);
        rider.setXSpeed((short) 0x0700);
        rider.setYSpeed((short) -0x0300);
        rider.setDirection(Direction.LEFT);

        corkscrew.update(1, rider);

        assertTrue(corkscrew.isRidingFor(true), "the object latches its own standing bit");
        assertTrue(rider.isObjectControlled(), "object_control $43 has bit 0 set");
        assertEquals(0, rider.getXSpeed(), "x_vel is cleared");
        assertEquals(0, rider.getYSpeed(), "y_vel is cleared");
        assertEquals(Direction.RIGHT, rider.getDirection(), "bclr #Status_Facing faces right");
        assertEquals(0, corkscrew.accumulatorFor(true), "move.l #0,(a2)");
    }

    /**
     * {@code loc_423D0} (:87613-87615): {@code ext.l} then {@code lsl.l #8} means the accumulator
     * climbs by {@code ground_vel << 8}, and every {@code (a2)} word read is its HIGH word. At the
     * floor speed that is six parameter units a frame.
     */
    @Test
    void rideParameterIsTheAccumulatorsHighWord() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite rider = riderAt(BASE_X, BASE_Y);
        rider.setGSpeed((short) 0x0600);
        corkscrew.update(1, rider);
        assertEquals(0, corkscrew.rideParameter(true), "the capture frame does not advance it");

        corkscrew.update(2, rider);
        assertEquals(0x0600 << 8, corkscrew.accumulatorFor(true), "one frame at $600");
        assertEquals(0x0006, corkscrew.rideParameter(true), "$60000 >>> 16 is 6");
    }

    /** {@code cmpi.w #$1000,ground_vel(a1)} / {@code addi.w #$10} (:87617-87620). */
    @Test
    void rideAcceleratesSixteenAFrameToACapOfFourThousandNinetySix() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite rider = riderAt(BASE_X, BASE_Y);
        rider.setGSpeed((short) 0x0600);
        corkscrew.update(1, rider);

        corkscrew.update(2, rider);
        assertEquals(0x0610, rider.getGSpeed(), "one $10 step");
        corkscrew.update(3, rider);
        assertEquals(0x0620, rider.getGSpeed(), "and another");

        rider.setGSpeed((short) 0x1000);
        corkscrew.update(4, rider);
        assertEquals(0x1000, rider.getGSpeed(), "the cap holds at $1000");
    }

    /**
     * {@code muls.w #$4800,d0 / swap d0} (:87627-87628): the high word of the signed product of the
     * ROM sine and {@code $4800}. Checked against values recomputed from the ROM sine table.
     */
    @Test
    void rideXOffsetIsTheSineTimesFortyEightHundredHighWord() {
        for (int parameter : new int[] {0, 0x20, 0x80, 0x100, 0x2FF, 0x400, 0x6FF}) {
            int expected = (TrigLookupTable.sinHex((parameter >> 1) & 0xFF) * 0x4800) >> 16;
            assertEquals(expected, LrzCorkscrewObjectInstance.rideXOffset(parameter),
                    "x offset at parameter $" + Integer.toHexString(parameter));
        }
        // sin($40) = $100, so the quarter turn is the full amplitude: ($100 * $4800) >> 16 = $48.
        assertEquals(0x48, LrzCorkscrewObjectInstance.rideXOffset(0x80));
        assertEquals(0, LrzCorkscrewObjectInstance.rideXOffset(0));
    }

    /**
     * {@code andi.w #$FF80,d0 / add.b (a3,d1.w),d0} (:87640-87643). The add is a BYTE add into a
     * word whose low seven bits were just cleared, so the table value occupies the low byte and the
     * {@code $80} step survives in bit 7 of that same byte - it does not carry into the high byte.
     */
    @Test
    void rideYOffsetKeepsTheTableValueInsideTheLowByte() {
        // Parameter 0: base 0, table[0] = 0.
        assertEquals(0, LrzCorkscrewObjectInstance.rideYOffset(0x000));
        // (param >> 2) = $20, base = 0, index $20, byte_4248A[$20] = $18.
        assertEquals(0x18, LrzCorkscrewObjectInstance.rideYOffset(0x080));
        // (param >> 2) = $80, base = $80, index 0, table[0] = 0: the step alone.
        assertEquals(0x80, LrzCorkscrewObjectInstance.rideYOffset(0x200));
        // (param >> 2) = $A0, base = $80, index $20 -> $18: $80 + $18 inside the byte.
        assertEquals(0x98, LrzCorkscrewObjectInstance.rideYOffset(0x280));
        // Past $600 the flatter byte_4250A is used: (param >> 2) = $180, base = $180, index 0.
        assertEquals(0x180, LrzCorkscrewObjectInstance.rideYOffset(0x600));
        // (param >> 2) = $1A0, base = $180, byte_4250A[$20] = $18.
        assertEquals(0x198, LrzCorkscrewObjectInstance.rideYOffset(0x680));
    }

    /** {@code divu.w #$16,d0} into {@code RawAni_4247E} (:87650-87654). */
    @Test
    void rideMappingFrameWalksTheTwelveRawDplcFrames() {
        assertEquals(0xEF, LrzCorkscrewObjectInstance.rideMappingFrame(0x000), "quotient 0");
        assertEquals(0xFA, LrzCorkscrewObjectInstance.rideMappingFrame(0x2C), "$16 -> quotient 1");
        assertEquals(0xF9, LrzCorkscrewObjectInstance.rideMappingFrame(0x58), "$2C -> quotient 2");
        // ($FF / $16) = 11, the last entry.
        assertEquals(0xF0, LrzCorkscrewObjectInstance.rideMappingFrame(0x1FE), "quotient 11");
    }

    /**
     * {@code cmpi.w #$700,(a2) / bhs.s loc_42396} then {@code neg.w ground_vel(a1)} (:87616,
     * :87607). Both exits reverse the rider, which is the ROM's behaviour and not a slip.
     */
    @Test
    void reachingTheEndEjectsTheRiderBackwards() {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        TestablePlayableSprite rider = riderAt(BASE_X, BASE_Y);
        rider.setGSpeed((short) 0x1000);
        corkscrew.update(1, rider);
        assertTrue(corkscrew.isRidingFor(true));

        int frame = 2;
        while (corkscrew.isRidingFor(true) && frame < 400) {
            corkscrew.update(frame++, rider);
        }
        assertFalse(corkscrew.isRidingFor(true), "the ride ends once the high word reaches $700");
        assertTrue(rider.getGSpeed() < 0, "neg.w ground_vel turns the rider around");
        assertEquals(rider.getGSpeed(), rider.getXSpeed(), "x_vel takes the negated ground_vel");
        assertEquals(0, rider.getYSpeed(), "move.w #0,y_vel");
        assertFalse(rider.isObjectControlled(), "object_control is cleared");
        assertEquals(Direction.LEFT, rider.getDirection(), "bset #Status_Facing faces left");
    }

    // ----- helpers -----------------------------------------------------------------------------

    private static LrzCorkscrewObjectInstance corkscrew() {
        return new LrzCorkscrewObjectInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, 0, false, 71));
    }

    private static TestablePlayableSprite riderAt(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setAirForTest(false);
        player.setGSpeed((short) 0x0600);
        player.setDirection(Direction.RIGHT);
        return player;
    }

    private static boolean captures(int playerX, int playerY) {
        LrzCorkscrewObjectInstance corkscrew = corkscrew();
        corkscrew.update(1, riderAt(playerX, playerY));
        return corkscrew.isRidingFor(true);
    }
}
