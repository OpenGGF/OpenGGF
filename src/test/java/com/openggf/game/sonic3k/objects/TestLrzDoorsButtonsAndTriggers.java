package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lava Reef's trigger family: {@code Obj_LRZDoor} ({@code $19}, sonic3k.asm:88015-88063),
 * {@code Obj_LRZBigDoor} ({@code $1A}, :88070-88145), {@code Obj_LRZButtonHorizontal}
 * ({@code $1C}, :88221-88277) and {@code Obj_LRZShootingTrigger} ({@code $1D}, :88279-88361).
 *
 * <p>Every expectation below is the routine's own arithmetic, taken from the cited label rather
 * than from the Java under test: the doors' travel is {@code GetSineCosine($2E)} shifted right by
 * two (door) or one (big door) against the ROM's {@code SineTable}
 * (docs/skdisasm/Levels/Misc/sine.bin, {@code sin($40) = $100}); the button's subtype decode is
 * {@code andi.w #$F} / {@code btst #6} / {@code btst #4}; the trigger's shot period is
 * {@code (subtype & $F0) >> 2}.
 */
class TestLrzDoorsButtonsAndTriggers {

    private static final int DOOR_ID = Sonic3kObjectIds.LBZ_CUP_ELEVATOR_POLE;
    private static final int BIG_DOOR_ID = Sonic3kObjectIds.LRZ_BIG_DOOR;
    private static final int BUTTON_ID = Sonic3kObjectIds.LRZ_BUTTON_HORIZONTAL;
    private static final int TRIGGER_ID = Sonic3kObjectIds.LRZ_SHOOTING_TRIGGER;

    private static final int BASE_X = 0x1000;
    private static final int BASE_Y = 0x0600;

    @BeforeEach
    void clearTriggerArray() {
        Sonic3kLevelTriggerManager.reset();
    }

    // ----- Obj_LRZDoor -------------------------------------------------------------------------

    /** {@code move.b subtype(a0),d0 / andi.w #$F,d0} (sonic3k.asm:88033-88034). */
    @Test
    void doorTakesItsTriggerIndexFromTheLowNibble() {
        for (int subtype = 0x00; subtype <= 0x0F; subtype++) {
            assertEquals(subtype, door(subtype).triggerIndex(), "subtype $" + Integer.toHexString(subtype));
        }
    }

    /** {@code tst.b (a3,d0.w) / beq.s loc_429BC}: a zero byte leaves the door where it was placed. */
    @Test
    void doorStaysShutWhileItsTriggerByteIsZero() {
        LrzDoorObjectInstance door = door(0x05);
        for (int frame = 0; frame < 120; frame++) {
            door.update(frame, null);
        }
        assertEquals(0, door.openTimer());
        assertEquals(BASE_Y, door.getCentreY());
        assertFalse(door.isOpening());
    }

    /**
     * {@code loc_42974} falls straight into {@code loc_42994}, so the first frame the byte reads
     * non-zero already advances {@code $2E} to 1 and moves the door.
     */
    @Test
    void doorStartsMovingOnTheSameFrameItsTriggerIsWritten() {
        LrzDoorObjectInstance door = door(0x05);
        Sonic3kLevelTriggerManager.setBit(0x05, 0);
        door.update(1, null);
        assertEquals(1, door.openTimer());
        assertEquals(BASE_Y - (TrigLookupTable.sinHex(1) >> 2), door.getCentreY());
    }

    /**
     * {@code GetSineCosine($2E) asr #2 / neg / add $46(a0)} (sonic3k.asm:88050-88056) over the
     * whole quarter turn, and the {@code cmpi.b #$40} that freezes it at 64 pixels.
     */
    @Test
    void doorRisesOneSineStepAFrameAndStopsExactlySixtyFourPixelsUp() {
        LrzDoorObjectInstance door = door(0x00);
        Sonic3kLevelTriggerManager.setBit(0x00, 0);
        for (int frame = 1; frame <= 0x40; frame++) {
            door.update(frame, null);
            assertEquals(frame, door.openTimer(), "frame " + frame);
            assertEquals(BASE_Y - (TrigLookupTable.sinHex(frame) >> 2), door.getCentreY(),
                    "y at $2E = " + frame);
        }
        assertTrue(door.isFullyOpen());
        assertEquals(BASE_Y - 0x40, door.getCentreY());

        // loc_429BC only re-runs the solid box: further frames neither move nor count.
        door.update(0x41, null);
        assertEquals(0x40, door.openTimer());
        assertEquals(BASE_Y - 0x40, door.getCentreY());
    }

    /** The routine pointer is replaced, not re-tested, so clearing the trigger cannot shut it. */
    @Test
    void doorNeverClosesOnceItHasStartedOpening() {
        LrzDoorObjectInstance door = door(0x03);
        Sonic3kLevelTriggerManager.setBit(0x03, 0);
        door.update(1, null);
        Sonic3kLevelTriggerManager.clearAll(0x03);
        for (int frame = 2; frame <= 0x40; frame++) {
            door.update(frame, null);
        }
        assertTrue(door.isFullyOpen());
        assertEquals(BASE_Y - 0x40, door.getCentreY());
    }

    /**
     * {@code move.w #$1B,d1 / moveq #0,d2 / move.b height_pixels(a0),d2 / move.w d2,d3 /
     * addq.w #1,d3} (sonic3k.asm:88058-88062). Act 1's {@code height_pixels} is {@code $28}.
     */
    @Test
    void doorSolidBoxIsTheRoutinesOwnArguments() {
        assertEquals(SolidObjectParams.of(0x1B, 0x28, 0x29), door(0x00).getSolidParams());
    }

    // ----- Obj_LRZButtonHorizontal -------------------------------------------------------------

    /** {@code andi.w #$F,d0}, {@code btst #6} -> bit 7, {@code btst #4} -> latching. */
    @Test
    void horizontalButtonDecodesIndexBitAndLatchFromItsSubtype() {
        assertEquals(0x0B, button(0x0B).triggerIndex());
        assertEquals(0, button(0x0B).triggerBit());
        assertFalse(button(0x0B).isLatching());

        assertEquals(0x01, button(0x41).triggerIndex());
        assertEquals(7, button(0x41).triggerBit(), "btst #6 selects bit 7");

        assertTrue(button(0x14).isLatching(), "btst #4 latches the press");
        assertEquals(0x04, button(0x14).triggerIndex());
    }

    /**
     * {@code swap d6 / andi.w #3,d6} (sonic3k.asm:88253-88254) is the side-touch pair
     * {@code SolidObject_cont} writes at {@code bit standing_bit + $D} (:41501-41503,
     * :41510-41512). Standing on this button is not a press.
     */
    @Test
    void horizontalButtonIsPressedBySideContactOnly() {
        LrzButtonHorizontalObjectInstance pressed = button(0x07);
        pressed.onSolidContact(player(), sideContact(), 0);
        pressed.update(1, null);
        assertTrue(Sonic3kLevelTriggerManager.testBit(0x07, 0), "side contact sets the bit");
        assertEquals(1, pressed.mappingFrame());

        Sonic3kLevelTriggerManager.reset();
        LrzButtonHorizontalObjectInstance stoodOn = button(0x07);
        stoodOn.onSolidContact(player(), standingContact(), 0);
        stoodOn.update(1, null);
        assertFalse(Sonic3kLevelTriggerManager.testBit(0x07, 0), "standing on it is not a press");
        assertEquals(0, stoodOn.mappingFrame());
    }

    /** {@code bclr d3,(a3)} on the untouched path, skipped only when subtype bit 4 is set. */
    @Test
    void horizontalButtonReleasesUnlessItsSubtypeLatches() {
        LrzButtonHorizontalObjectInstance plain = button(0x09);
        plain.onSolidContact(player(), sideContact(), 0);
        plain.update(1, null);
        plain.update(2, null);
        assertFalse(Sonic3kLevelTriggerManager.testBit(0x09, 0), "the bit clears when nobody touches");
        assertEquals(0, plain.mappingFrame());

        LrzButtonHorizontalObjectInstance latching = button(0x19);
        latching.onSolidContact(player(), sideContact(), 0);
        latching.update(1, null);
        latching.update(2, null);
        assertTrue(Sonic3kLevelTriggerManager.testBit(0x09, 0), "subtype bit 4 keeps the bit set");
    }

    /**
     * {@code move.w #$10,d1 / #$F,d2 / #$10,d3} (sonic3k.asm:88236-88238): note {@code d2} is one
     * less than {@code d3}, unlike the door.
     */
    @Test
    void horizontalButtonSolidBoxIsTheRoutinesOwnArguments() {
        assertEquals(SolidObjectParams.of(0x10, 0x0F, 0x10), button(0x01).getSolidParams());
    }

    // ----- Obj_LRZShootingTrigger --------------------------------------------------------------

    /** {@code andi.w #$F0,d0 / lsr.w #2,d0} for the two placed subtypes (sonic3k.asm:88292-88293). */
    @Test
    void shootingTriggerSplitsItsSubtypeIntoTriggerIndexAndShotPeriod() {
        assertEquals(0x0, trigger(0xA0).triggerIndex());
        assertEquals(0xA0 >> 2, trigger(0xA0).shotPeriod(), "$A0 -> 40 frames");
        assertEquals(0x2, trigger(0xC2).triggerIndex());
        assertEquals(0xC0 >> 2, trigger(0xC2).shotPeriod(), "$C2 -> 48 frames");
    }

    /**
     * {@code subq.w #1,$2E(a0) / bpl.s loc_42E84} (sonic3k.asm:88297-88298): the reload lands on
     * the frame the word first goes negative, so a fresh trigger fires on its very first update
     * and then every {@code shotPeriod + 1} frames.
     */
    @Test
    void shootingTriggerReloadsWhenItsWordCounterGoesNegative() {
        LrzShootingTriggerObjectInstance trigger = trigger(0xA0);
        trigger.update(1, null);
        assertEquals(0xA0 >> 2, trigger.reloadTimer(), "the counter reloads from $30");
        // 40 more frames walk the counter down to exactly 0, which is still positive, so the
        // period between reloads is $30 + 1 frames, not $30.
        for (int frame = 2; frame <= (0xA0 >> 2) + 1; frame++) {
            trigger.update(frame, null);
        }
        assertEquals(0, trigger.reloadTimer(), "zero is not yet negative");
        trigger.update((0xA0 >> 2) + 2, null);
        assertEquals(0xA0 >> 2, trigger.reloadTimer(), "and it reloads one frame later");
    }

    /** {@code cmpi.b #2,anim(a1) / bne.s locret_42EE6}: walking into it does nothing at all. */
    @Test
    void shootingTriggerIgnoresAPlayerWhoIsNotRolling() {
        LrzShootingTriggerObjectInstance trigger = trigger(0xA0);
        TestablePlayableSprite player = player();
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        player.setXSpeed((short) 0x0400);
        trigger.onTouchResponse(player, specialTouch(), 0);
        trigger.update(1, player);

        assertFalse(Sonic3kLevelTriggerManager.testBit(0, 0), "no trigger write");
        assertFalse(trigger.hasTriggered());
        assertEquals(0x0400, player.getXSpeed(), "the player keeps its velocity");
    }

    /**
     * {@code sub_42EC0} (sonic3k.asm:88349-88359): negate both velocities, {@code bset d3,(a3)}
     * with {@code d3} still zero, and rewrite the slot as {@code Obj_Explosion}.
     */
    @Test
    void shootingTriggerFiresItsBitAndSelfDestructsForARollingPlayer() {
        LrzShootingTriggerObjectInstance trigger = trigger(0xC2);
        TestablePlayableSprite player = player();
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        player.setXSpeed((short) 0x0400);
        player.setYSpeed((short) -0x0200);
        trigger.onTouchResponse(player, specialTouch(), 0);
        trigger.update(1, player);

        assertTrue(Sonic3kLevelTriggerManager.testBit(0x02, 0), "bit 0 of trigger index 2");
        assertTrue(trigger.hasTriggered());
        assertTrue(trigger.isDestroyed());
        assertEquals(0, trigger.getCollisionFlags(), "clr.b collision_flags(a0)");
        assertEquals(-0x0400, player.getXSpeed());
        assertEquals(0x0200, player.getYSpeed());
    }

    /**
     * {@code move.w #$200,x_vel(a1) / y_vel(a1)} and the {@code btst #0,status(a0) / neg.w}
     * (sonic3k.asm:88325-88330). {@code MoveSprite2} has no gravity term, so both stay constant.
     */
    @Test
    void shootingTriggerShotCarriesTheRoutinesVelocities() {
        LrzShootingTriggerProjectileInstance shot =
                new LrzShootingTriggerProjectileInstance(spawn(TRIGGER_ID, 0xA0, 0), false);
        assertEquals(0x200, shot.xVelocity());
        assertEquals(0x200, shot.yVelocity());

        LrzShootingTriggerProjectileInstance flipped =
                new LrzShootingTriggerProjectileInstance(spawn(TRIGGER_ID, 0xA0, 1), true);
        assertEquals(-0x200, flipped.xVelocity(), "only x_vel is negated");
        assertEquals(0x200, flipped.yVelocity());
    }

    // ----- Obj_LRZBigDoor ----------------------------------------------------------------------

    /**
     * {@code loc_42A68} (sonic3k.asm:88089-88097). The Y test is unsigned after
     * {@code addi.w #-$40}, so the band is {@code [y+$40, y+$C0)}; the X test is signed, so the
     * player must be at least {@code $50} to the right.
     */
    @Test
    void bigDoorOpensOnlyInsideItsOwnProximityBox() {
        int[][] cases = {
                // dx, dy, expected
                {0x50, 0x40, 1},
                {0x50, 0xBF, 1},
                {0x7F, 0x80, 1},
                {0x4F, 0x80, 0},
                {-0x80, 0x80, 0},
                {0x50, 0x3F, 0},
                {0x50, 0xC0, 0},
                {0x50, -0x10, 0},
        };
        for (int[] testCase : cases) {
            LrzBigDoorObjectInstance bigDoor = bigDoor();
            TestablePlayableSprite player = player();
            player.setCentreX((short) (BASE_X + testCase[0]));
            player.setCentreY((short) (BASE_Y + testCase[1]));
            String label = "dx " + testCase[0] + " dy " + testCase[1];
            assertEquals(testCase[2] != 0, bigDoor.playerInTriggerRegion(player), label);
        }
    }

    /**
     * {@code GetSineCosine($2E) asr #1} added to {@code $46(a0)} (sonic3k.asm:88119-88122): the
     * big door sinks, and its total travel is the {@code $80} the already-open branch applies.
     */
    @Test
    void bigDoorSinksEightyPixelsOverItsQuarterTurn() {
        LrzBigDoorObjectInstance bigDoor = bigDoor();
        TestablePlayableSprite player = player();
        player.setCentreX((short) (BASE_X + 0x60));
        player.setCentreY((short) (BASE_Y + 0x60));
        for (int frame = 1; frame <= 0x40; frame++) {
            bigDoor.update(frame, player);
            assertEquals((BASE_Y + (TrigLookupTable.sinHex(frame) >> 1)) & 0xFFFF,
                    bigDoor.getCentreY(), "y at $2E = " + frame);
        }
        assertTrue(bigDoor.isFullyOpen());
        assertEquals(BASE_Y + 0x80, bigDoor.getCentreY());
    }

    /** {@code move.w #$3B,d1 / #$40,d2 / #$41,d3} (sonic3k.asm:88130-88132). */
    @Test
    void bigDoorSolidBoxIsTheRoutinesOwnArguments() {
        assertEquals(SolidObjectParams.of(0x3B, 0x40, 0x41), bigDoor().getSolidParams());
    }

    // ----- helpers -----------------------------------------------------------------------------

    private static ObjectSpawn spawn(int id, int subtype, int renderFlags) {
        return new ObjectSpawn(BASE_X, BASE_Y, id, subtype, renderFlags, false, 0);
    }

    private static LrzDoorObjectInstance door(int subtype) {
        return new LrzDoorObjectInstance(spawn(DOOR_ID, subtype, 0));
    }

    private static LrzBigDoorObjectInstance bigDoor() {
        return new LrzBigDoorObjectInstance(spawn(BIG_DOOR_ID, 0, 0));
    }

    private static LrzButtonHorizontalObjectInstance button(int subtype) {
        return new LrzButtonHorizontalObjectInstance(spawn(BUTTON_ID, subtype, 0));
    }

    private static LrzShootingTriggerObjectInstance trigger(int subtype) {
        return new LrzShootingTriggerObjectInstance(spawn(TRIGGER_ID, subtype, 0));
    }

    private static TestablePlayableSprite player() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) BASE_X, (short) BASE_Y);
        player.setCentreX((short) BASE_X);
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        return player;
    }

    private static SolidContact sideContact() {
        return new SolidContact(false, true, false, false, true);
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    /** {@code collision_flags $C6}: category {@code $C0}, size index 6. */
    private static TouchResponseResult specialTouch() {
        return new TouchResponseResult(6, 0x10, 0x10, TouchCategory.SPECIAL);
    }
}
