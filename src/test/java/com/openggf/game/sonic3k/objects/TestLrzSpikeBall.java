package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZSpikeBall} (sonic3k.asm:88838-89070, ROM {@code $436E8}) and the chip
 * {@code sub_439EC} throws (:88998-89050).
 *
 * <p>The four X positions asserted below are ROM sine bytes, not engine values:
 * {@code GetSineCosine} returns {@code cos(a) = sin(a + $40)} and {@code sine.bin} holds
 * {@code $0000} at angle 0 and {@code $0100} at angle {@code $40}, so {@code asr.w #2} gives
 * {@code +$40}, {@code 0}, {@code -$40}, {@code 0} at angles {@code 0}, {@code $C0}, {@code $80},
 * {@code $40}. {@code angle} counts DOWN by two a frame and the position is taken BEFORE the
 * decrement, so update {@code n} uses angle {@code -2(n-1)}.
 */
class TestLrzSpikeBall {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_ALARM;
    private static final int BASE_X = 0x1A40;
    private static final int BASE_Y = 0x0700;

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    /** {@code tst.b subtype(a0) / beq} then {@code ori.w #high_priority} and {@code $8F} (:88849-88853). */
    @Test
    void subtypeZeroIsHarmfulAndHighPriorityFromTheFirstFrame() {
        LrzSpikeBallObjectInstance ball = ball(0x00);
        assertEquals(0x8F, ball.getCollisionFlags(), "move.b #$8F,collision_flags(a0)");
        assertTrue(ball.isHighPriority(), "ori.w #high_priority,art_tile(a0)");
        assertEquals(0x20, ball.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(BASE_X, ball.baseX(), "$44(a0)");
        assertEquals(BASE_Y, ball.baseY(), "$46(a0)");
        assertFalse(ball.rolling());
    }

    /**
     * {@code loc_4397E} (:88979-88983) with {@code subq.b #2,angle(a0)} (:89016): the boulder is
     * not static -- it grinds a full 128-pixel sweep on a 128-frame cycle.
     */
    @Test
    void subtypeZeroGrindsSixtyFourPixelsEitherSideOfItsPlacedX() {
        LrzSpikeBallObjectInstance ball = ball(0x00);
        ball.setServices(services());

        int[][] cases = {{1, BASE_X + 0x40}, {33, BASE_X}, {65, BASE_X - 0x40}, {97, BASE_X}};
        int caseIndex = 0;
        for (int frame = 1; frame <= 97; frame++) {
            ball.update(frame, null);
            if (caseIndex < cases.length && frame == cases[caseIndex][0]) {
                assertEquals(cases[caseIndex][1], ball.getCentreX(),
                        "x_pos on update " + frame + " (angle $"
                                + Integer.toHexString((-2 * (frame - 1)) & 0xFF) + ")");
                caseIndex++;
            }
        }
        assertEquals(cases.length, caseIndex, "every asserted frame was reached");
        assertEquals((-2 * 97) & 0xFF, ball.angle(), "angle after 97 decrements");
    }

    /**
     * {@code loc_437FE} (:88863-88870): {@code collision_flags} and the priority bit are cleared
     * every frame and restored only while {@code tst.b angle(a0)} is negative.
     */
    @Test
    void theSwingingBoulderIsHarmfulOnlyOnTheNegativeHalfOfItsSweep() {
        LrzSpikeBallObjectInstance ball = ball(0xC0);
        ball.setServices(services());

        ball.update(1, null);
        assertEquals(0, ball.getCollisionFlags(), "angle 0 is non-negative: harmless");
        assertFalse(ball.isHighPriority());

        ball.update(2, null);
        assertEquals(0x8F, ball.getCollisionFlags(), "angle $FE has bit 7 set: harmful");
        assertTrue(ball.isHighPriority());

        // angle walks $FE .. $80 (harmful) then $7E .. $02 (harmless).
        for (int frame = 3; frame <= 65; frame++) {
            ball.update(frame, null);
        }
        assertEquals(0x80, (-2 * 64) & 0xFF, "precondition: update 65 read angle $80");
        assertEquals(0x8F, ball.getCollisionFlags(), "$80 is still negative");
        ball.update(66, null);
        assertEquals(0, ball.getCollisionFlags(), "$7E is not");
    }

    /**
     * {@code loc_43830} (:88872-88884). The window is {@code (y - $46) + $40 < $80} unsigned and
     * the {@code bcc} after {@code sub.w $44(a0),d0} arms only for a player strictly LEFT of the
     * anchor.
     */
    @Test
    void onlyAPlayerLeftOfTheAnchorAndLevelWithItArmsTheBoulder() {
        LrzSpikeBallObjectInstance right = ball(0xC0);
        right.setServices(services());
        right.update(1, player(BASE_X + 1, BASE_Y));
        assertFalse(right.armed(), "a player at or right of $44(a0) never arms it");

        LrzSpikeBallObjectInstance high = ball(0xC0);
        high.setServices(services());
        high.update(1, player(BASE_X - 0x80, BASE_Y - 0x41));
        assertFalse(high.armed(), "$41 above the anchor is outside the $80 window");

        LrzSpikeBallObjectInstance low = ball(0xC0);
        low.setServices(services());
        low.update(1, player(BASE_X - 0x80, BASE_Y + 0x40));
        assertFalse(low.armed(), "the window is exclusive at the bottom: $40 + $40 = $80");

        LrzSpikeBallObjectInstance armed = ball(0xC0);
        armed.setServices(services());
        armed.update(1, player(BASE_X - 0x80, BASE_Y + 0x3F));
        assertTrue(armed.armed(), "$3F below and left arms it");
    }

    /**
     * {@code loc_4385C} (:88886-88891): the break-loose compare is against the subtype and uses
     * the angle BEFORE the frame's decrement, so a {@code $C0} boulder armed from the start breaks
     * loose on update 33 and not before.
     */
    @Test
    void anArmedBoulderBreaksLooseOnTheFrameTheAngleReachesItsSubtype() {
        LrzSpikeBallObjectInstance ball = ball(0xC0);
        ball.setServices(services());
        TestablePlayableSprite player = player(BASE_X - 0x80, BASE_Y);

        for (int frame = 1; frame <= 32; frame++) {
            ball.update(frame, player);
            assertFalse(ball.rolling(), "still swinging on update " + frame);
        }
        assertEquals(0xC0, ball.angle(), "precondition: update 33 reads angle $C0");

        ball.update(33, player);
        assertTrue(ball.rolling(), "loc_4389E installed");
        assertEquals(-0x400, ball.xVel(), "move.w #-$400,x_vel(a0)");
        assertEquals(0x8F, ball.getCollisionFlags(), "$C0 is negative, so it broke loose harmful");
    }

    /** An unarmed boulder passes its subtype angle without breaking loose (:88886-88887). */
    @Test
    void anUnarmedBoulderSweepsPastItsSubtypeAngle() {
        LrzSpikeBallObjectInstance ball = ball(0xC0);
        ball.setServices(services());
        for (int frame = 1; frame <= 40; frame++) {
            ball.update(frame, null);
        }
        assertFalse(ball.rolling(), "tst.b $30(a0) / beq gates the whole transition");
    }

    /** {@code subq.b #1,anim_frame_timer / move.b #3 / addq.b #1,mapping_frame} (:88948-88956). */
    @Test
    void theAnimationAdvancesEveryFourthFrameAndWrapsAtThree() {
        LrzSpikeBallObjectInstance ball = ball(0x00);
        ball.setServices(services());
        int[] expected = {1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 1};
        for (int frame = 1; frame <= expected.length; frame++) {
            ball.update(frame, null);
            assertEquals(expected[frame - 1], ball.mappingFrame(), "mapping_frame on update " + frame);
        }
    }

    /**
     * {@code loc_1B666} (sonic3k.asm:37372-37377) reads the value the routine hands it, which is
     * {@code $44(a0)} (:89017), not the swung {@code x_pos}.
     */
    @Test
    void theUnloadTestUsesThePlacedXAndNotTheSwungOne() {
        LrzSpikeBallObjectInstance ball = ball(0x00);
        ball.setServices(services());
        ball.update(1, null);
        assertEquals(BASE_X + 0x40, ball.getCentreX(), "precondition: swung 64 pixels right");
        assertEquals(BASE_X, ball.getOutOfRangeReferenceX());
        assertFalse(ball.isCustomOutOfRange((BASE_X & 0xFF80) - 0x280), "$280 is inclusive: bhi");
        assertTrue(ball.isCustomOutOfRange((BASE_X & 0xFF80) - 0x281));
    }

    /**
     * {@code loc_43A6C} (:89059-89070): a chip animates four frames under {@code MoveSprite}'s
     * {@code $38} gravity and deletes itself the first frame it was not drawn.
     */
    @Test
    void theChipFallsUnderGravityAndDeletesWhenItStopsBeingDrawn() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x4000, 0);
        LrzRockDebrisInstance chip = new LrzRockDebrisInstance(BASE_X, BASE_Y, -0x80, -0x300);
        chip.setServices(services());

        chip.update(1, null);
        assertEquals(1, chip.mappingFrame(), "addq.b #1,mapping_frame / andi.b #3");
        assertEquals(BASE_Y - 3, chip.getCentreY(), "the OLD y_vel moves it: -$300 >> 8 = -3");
        assertEquals(-0x300 + 0x38, chip.yVel(), "addi.w #$38,y_vel(a0)");
        assertFalse(chip.isDestroyed());

        AbstractObjectInstance.updateCameraBounds(0x3000, 0x3000, 0x4000, 0x4000, 0);
        chip.update(2, null);
        assertFalse(chip.isDestroyed(), "it still had bit 7 from the previous frame");
        chip.update(3, null);
        assertTrue(chip.isDestroyed(), "tst.b render_flags(a0) / bpl Delete_Current_Sprite");
    }

    private static LrzSpikeBallObjectInstance ball(int subtype) {
        return new LrzSpikeBallObjectInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, subtype, 0, false, 0));
    }

    private static TestablePlayableSprite player(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}
