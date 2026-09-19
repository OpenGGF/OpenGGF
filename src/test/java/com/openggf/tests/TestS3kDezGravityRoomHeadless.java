package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravityRoomObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $5F}, {@code Obj_DEZGravityRoom} (sonic3k.asm:95814-95952): Death Egg act 1's
 * turbine corridor, one placement.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityRoomHeadless {

    /** {@code DEZ1_Sprites} record 299: the act 1 placement, subtype {@code $00}. */
    private static final int OBJECT_X = 0x2480;
    private static final int OBJECT_Y = 0x0840;
    private static final int CORRIDOR_LENGTH = 0x500;
    private static final int HALF_HEIGHT = 0x140;
    private static final int BLOW_ACCELERATION = 0x38;
    private static final int STEER_STEP = 0x18;
    private static final int STEER_LIMIT = 0x600;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code sub.w x_pos(a0),d0 / cmpi.w #$500,d0 / bhs} (:95847-95849) is an <em>unsigned</em>
     * compare on the raw difference, so the corridor reaches {@code $500} px to its right and
     * nothing at all to its left.
     */
    @Test
    void theCorridorReachesRightwardsOnly() {
        assertTrue(captures(0, 0), "the object's own column is inside");
        assertTrue(captures(CORRIDOR_LENGTH - 1, 0), "and so is the last column");
        assertFalse(captures(CORRIDOR_LENGTH, 0), "one past the far end");
        assertFalse(captures(-1, 0), "and one column left of it wraps unsigned, so it is out");
        assertTrue(captures(0x100, -HALF_HEIGHT), "dy = -$140 is the inclusive top edge");
        assertFalse(captures(0x100, HALF_HEIGHT), "dy = $140 is the exclusive bottom edge");
    }

    /**
     * The capture (:95858-95866) leaves the player tumbling: {@code flip_angle} 1,
     * {@code flips_remaining} -1 and {@code flip_speed} 4, airborne and under object control.
     */
    @Test
    void theCaptureStartsTheEndlessTumbleAndTakesControl() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            sprite.setAir(false);
            sprite.setJumping(true);

            room.update(0, sprite);

            assertTrue(room.isCapturedForTest(true), "state byte set");
            assertEquals(1, sprite.getFlipAngle() & 0xFF, "move.b #1,flip_angle (:95858)");
            // move.b #-1,flips_remaining is the byte $FF, which the engine exposes unsigned.
            assertEquals(0xFF, sprite.getFlipsRemaining() & 0xFF,
                    "move.b #-1,flips_remaining (:95860)");
            assertEquals(4, sprite.getFlipSpeed(), "move.b #4,flip_speed (:95861)");
            assertFalse(sprite.isJumping(), "clr.b jumping (:95863)");
            assertTrue(sprite.getAir(), "bset #Status_InAir (:95864)");
            assertTrue(sprite.isObjectControlled(), "move.b #1,object_control (:95866)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code addi.w #$38,x_vel(a1)} (:95872) every frame, with no ceiling of its own: the
     * corridor's length is the only limit on how fast the player leaves it.
     */
    @Test
    void theBlowAccumulatesFiftySixPerFrameWithNoCap() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            sprite.setXSpeed((short) 0);
            room.update(0, sprite);

            for (int frame = 1; frame <= 8; frame++) {
                int before = sprite.getXSpeed();
                room.update(frame, sprite);
                assertEquals(before + BLOW_ACCELERATION, sprite.getXSpeed(),
                        "frame " + frame + " adds $38 with no clamp");
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_496C8} :95874-95908, driven through the object rather than through its
     * arithmetic: up and down move {@code y_vel} by {@code $18} toward {@code ∓$600} and stop
     * there. The first version of this test called the steering helper with the test's own
     * copies of the step and the limit, so changing either constant in the object left it
     * green — the {@code $5A} session's lesson, met again.
     */
    @Test
    void steeringMovesTwentyFourPerFrameAndStopsAtTheLimit() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            room.update(0, sprite);
            sprite.setYSpeed((short) 0);

            hold(sprite, true, false);
            room.update(1, sprite);
            // $18 up, then the drag: -$18 asr 5 is -1, and -$18 - -1 is -$17.
            assertEquals(-0x17, sprite.getYSpeed(), "one frame of up is $18 less the drag");

            sprite.setYSpeed((short) -0x600);
            room.update(2, sprite);
            assertEquals(-0x600 + 0x30, sprite.getYSpeed(),
                    "at the limit the step is refused and only the drag applies");

            sprite.setYSpeed((short) 0);
            hold(sprite, false, true);
            room.update(3, sprite);
            // Not the mirror of it: asr rounds toward minus infinity, so -$18 drags by -1
            // while +$18 drags by nothing at all. The asymmetry is the shift's, not a bug.
            assertEquals(0x18, sprite.getYSpeed(), "down takes the full step with no drag");

            sprite.setYSpeed((short) 0x600);
            room.update(4, sprite);
            assertEquals(0x600 - 0x30, sprite.getYSpeed(), "with the same refusal at $600");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_49706} :95910-95932: {@code y_vel} loses {@code y_vel asr 5} a frame, and the
     * 68000 borrow flattens the result to zero on the frame it would cross.
     */
    @Test
    void theDragIsAFifthOfAThirtySecondAndFlattensAtTheCrossing() {
        assertEquals(0x200 - 0x10, S3kDezGravityRoomObjectInstance.dragForTest(0x200),
                "$200 loses $10");
        assertEquals(-0x200 + 0x10, S3kDezGravityRoomObjectInstance.dragForTest(-0x200),
                "and -$200 gains it back");
        assertEquals(0x1F, S3kDezGravityRoomObjectInstance.dragForTest(0x1F),
                "below $20 the shift is zero and nothing is taken");
        assertEquals(0, S3kDezGravityRoomObjectInstance.dragForTest(-1),
                "-1 shifts to -1 and the subtraction lands exactly on zero");
    }

    /**
     * {@code bset #Status_InAir,status(a1)} (:95737) runs <em>unconditionally</em> after the
     * object's own {@code SonicKnux_DoLevelCollision}, so a landing inside the corridor never
     * sticks. Asserted by handing the object a grounded player, which is what a landing leaves
     * behind: an earlier version watched a player who never landed, and stayed green with the
     * write removed.
     */
    @Test
    void aLandingInsideTheCorridorIsUndoneOnTheSameFrame() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            room.update(0, sprite);
            for (int frame = 1; frame <= 6; frame++) {
                sprite.setAir(false);
                room.update(frame, sprite);
                assertTrue(sprite.getAir(),
                        "the corridor puts the player back in the air at frame " + frame);
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_496A8} :95869-95876: the same unsigned {@code $500} compare releases the
     * player, and {@code move.b #0,object_control(a1)} hands control back.
     */
    @Test
    void leavingTheCorridorHandsControlBack() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            room.update(0, sprite);
            assertTrue(room.isCapturedForTest(true), "precondition: captured");

            moveTo(sprite, OBJECT_X + CORRIDOR_LENGTH, OBJECT_Y);
            room.update(1, sprite);
            assertFalse(room.isCapturedForTest(true), "past the far end, the state byte clears");
            assertFalse(sprite.isObjectControlled(), "and object_control with it");
        } finally {
            SessionManager.clear();
        }
    }

    /** Capture and restore of the per-player state byte. */
    @Test
    void rewindRestoresTheCapturedState() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            room.update(0, sprite);
            assertTrue(room.isCapturedForTest(true), "precondition: captured");
            CompositeSnapshot inside =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            moveTo(sprite, OBJECT_X + CORRIDOR_LENGTH, OBJECT_Y);
            room.update(1, sprite);
            assertFalse(room.isCapturedForTest(true), "precondition: the forward run released");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(inside);
            assertTrue(restored(room).isCapturedForTest(true), "the capture comes back");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    private void hold(AbstractPlayableSprite sprite, boolean up, boolean down) {
        sprite.setDirectionalInputPressed(up, down, false, false);
    }

    private boolean captures(int dx, int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityRoomObjectInstance room = place();
            moveTo(sprite, OBJECT_X + dx, OBJECT_Y + dy);
            room.update(0, sprite);
            return room.isCapturedForTest(true);
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezGravityRoomObjectInstance place() {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x5F, 0, 0,
                false, OBJECT_Y, -1);
        S3kDezGravityRoomObjectInstance room = new S3kDezGravityRoomObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(room);
        return room;
    }

    private S3kDezGravityRoomObjectInstance restored(S3kDezGravityRoomObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravityRoomObjectInstance room) {
                return room;
            }
        }
        return original;
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
    }
}
