package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.TrigLookupTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZOrbitingSpikeBallHorizontal} / {@code Obj_LRZOrbitingSpikeBallVertical}
 * (sonic3k.asm:89077-89222), the two act 2 orbiting spike balls.
 *
 * <p>Every claim below is the ROM's arithmetic, not a fitted value: the angle is
 * {@code (Level_frame_counter+1) * 2} as a byte, negated for {@code status} bit 0, plus the
 * subtype with bit 0 cleared; the ball is harmful only while that byte has bit 7 set; and the
 * displacement is a fixed fraction of {@code cos} on one axis with the other never moving.
 */
class TestLrzOrbitingSpikeBall {

    private static final int ANCHOR_X = 0x2000;
    private static final int ANCHOR_Y = 0x0400;

    private static LrzOrbitingSpikeBallObjectInstance build(
            LrzOrbitingSpikeBallObjectInstance.Axis axis, int subtype, boolean xFlip) {
        ObjectSpawn spawn = new ObjectSpawn(ANCHOR_X, ANCHOR_Y,
                axis == LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL ? 0x2B : 0x2C,
                subtype, xFlip ? 1 : 0, false, ANCHOR_Y);
        LrzOrbitingSpikeBallObjectInstance ball =
                new LrzOrbitingSpikeBallObjectInstance(spawn, axis);
        // No LevelManager here, so levelFrameCounterLowByte falls back to the vIntRunCount each
        // update is given; the expectations below are computed from the ROM's own SineTable
        // through the routine's arithmetic, not read back from the class.
        ball.setServices(new TestObjectServices().withIsolatedObjectManager());
        return ball;
    }

    @Test
    void subtypeBitZeroSelectsTheLargeBallAndIsClearedFromThePhase() {
        // bclr #0,subtype(a0) (sonic3k.asm:89083): the bit selects the ball AND is written back.
        LrzOrbitingSpikeBallObjectInstance small =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x80, false);
        LrzOrbitingSpikeBallObjectInstance large =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x81, false);
        assertFalse(small.isLarge(), "subtype $80 has bit 0 clear: the 16x16 ball");
        assertTrue(large.isLarge(), "subtype $81 has bit 0 set: the 32x32 ball");
        assertEquals(0x80, small.phase(), "the phase is the subtype with bit 0 cleared");
        assertEquals(0x80, large.phase(), "bclr writes the cleared bit back before the add");
    }

    @Test
    void theBallIsHarmfulOnlyWhileTheByteAngleHasBitSevenSet() {
        // move.b #0,collision_flags then the restore only under bpl (sonic3k.asm:89097-89109).
        LrzOrbitingSpikeBallObjectInstance ball =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x00, false);
        StringBuilder log = new StringBuilder();
        int harmful = 0;
        for (int frame = 0; frame < 128; frame++) {
            ball.update(frame, null);
            int angle = (frame * 2) & 0xFF;
            boolean expectHarmful = (angle & 0x80) != 0;
            assertEquals(expectHarmful ? 0x9A : 0, ball.getCollisionFlags(),
                    "frame " + frame + " angle $" + Integer.toHexString(angle));
            assertEquals(expectHarmful, ball.isHighPriority(),
                    "the priority bit and collision_flags are restored together");
            if (expectHarmful) {
                harmful++;
            }
            log.append(frame).append(':').append(ball.getCollisionFlags()).append(' ');
        }
        assertEquals(64, harmful, "half the 128-frame orbit is harmful" + log);
    }

    @Test
    void theHorizontalBallMovesOnXOnlyAndTheVerticalOnYOnly() {
        LrzOrbitingSpikeBallObjectInstance horizontal =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x00, false);
        LrzOrbitingSpikeBallObjectInstance vertical =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.VERTICAL, 0x00, false);
        for (int frame = 0; frame < 64; frame++) {
            horizontal.update(frame, null);
            vertical.update(frame, null);
            int cos = TrigLookupTable.cosHex((frame * 2) & 0xFF);
            assertEquals((ANCHOR_X + (cos >> 3)) & 0xFFFF, horizontal.getCentreX(),
                    "loc_43B96: x = $44(a0) + (cos asr 3), frame " + frame);
            assertEquals(ANCHOR_Y, horizontal.getCentreY(),
                    "the horizontal ball never writes y_pos");
            assertEquals((ANCHOR_Y + ((cos + (cos >> 2)) >> 3)) & 0xFFFF, vertical.getCentreY(),
                    "loc_43C88: y = $46(a0) + ((cos + cos asr 2) asr 3), frame " + frame);
            assertEquals(ANCHOR_X, vertical.getCentreX(),
                    "the vertical ball never writes x_pos");
        }
    }

    @Test
    void statusBitZeroReversesTheOrbit() {
        // neg.b d0 after the doubling (sonic3k.asm:89102-89103).
        LrzOrbitingSpikeBallObjectInstance forward =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x00, false);
        LrzOrbitingSpikeBallObjectInstance reversed =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x00, true);
        for (int frame = 1; frame < 64; frame++) {
            forward.update(frame, null);
            reversed.update(frame, null);
            int cos = TrigLookupTable.cosHex((-(frame * 2)) & 0xFF);
            assertEquals((ANCHOR_X + (cos >> 3)) & 0xFFFF, reversed.getCentreX(),
                    "the reversed ball reads cos(-2f), frame " + frame);
        }
    }

    @Test
    void theLargeBallTakesTheWiderOrbitAndTheOtherCollisionFlags() {
        LrzOrbitingSpikeBallObjectInstance large =
                build(LrzOrbitingSpikeBallObjectInstance.Axis.HORIZONTAL, 0x81, false);
        for (int frame = 0; frame < 64; frame++) {
            large.update(frame, null);
            int angle = ((frame * 2) + 0x80) & 0xFF;
            int cos = TrigLookupTable.cosHex(angle);
            assertEquals((ANCHOR_X + ((cos + (cos >> 1)) >> 3)) & 0xFFFF, large.getCentreX(),
                    "loc_43BDE: x = $44(a0) + ((cos + cos asr 1) asr 3), frame " + frame);
            assertEquals((angle & 0x80) != 0 ? 0x8F : 0, large.getCollisionFlags(),
                    "the large ball's collision_flags is $8F, frame " + frame);
        }
        assertEquals(0x20, large.getOnScreenHalfWidth(), "width_pixels $20 (sonic3k.asm:89089)");
    }
}
