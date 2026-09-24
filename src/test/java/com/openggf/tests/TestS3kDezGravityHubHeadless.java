package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravityHubObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $5C}, {@code Obj_DEZGravityHub} (sonic3k.asm:95545-95690): the junction the act 2
 * gravity tubes feed into. Three act 2 placements, each beside a {@code $5A}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityHubHeadless {

    /** {@code DEZ2_Sprites}: the hub at the top of the act 2 gravity room. */
    private static final int OBJECT_X = 0x0C40;
    private static final int OBJECT_Y = 0x05C0;
    /** All four exits allowed. */
    private static final int SUBTYPE_ALL_EXITS = 0x0F;
    private static final int BUTTON_UP = 0x01;
    private static final int BUTTON_DOWN = 0x02;
    private static final int BUTTON_LEFT = 0x04;
    private static final int BUTTON_RIGHT = 0x08;
    private static final int EXIT_SPEED = 0xC00;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code addi.w #$20,d0 / cmpi.w #$40,d0 / bhs} on both axes (:95560-95567): a
     * {@code $40} px square centred on the hub, top-inclusive and bottom-exclusive on the
     * biased compare.
     */
    @Test
    void theCaptureWindowIsTheFortyPixelSquareAroundTheHub() {
        assertTrue(captures(-0x20, 0), "dx = -$20 is the inclusive left edge");
        assertTrue(captures(0x1F, 0), "dx = $1F is the last column inside");
        assertFalse(captures(-0x21, 0), "one past the left edge");
        assertFalse(captures(0x20, 0), "dx = $20 is the exclusive right edge");
        assertTrue(captures(0, -0x20), "dy = -$20 is the inclusive top edge");
        assertFalse(captures(0, 0x20), "dy = $20 is the exclusive bottom edge");
    }

    /**
     * {@code btst #Status_OnObj,status(a1) / bne} (:95571). The hub sits beside a
     * {@code $5A}, so without this it would steal the tube's rider mid-ride.
     */
    @Test
    void aPlayerStandingOnSomethingIsLeftAlone() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setOnObject(true);
            hub.update(0, sprite);
            assertEquals(0, hub.stateForTest(true), "Status_OnObj blocks the capture");

            sprite.setOnObject(false);
            hub.update(1, sprite);
            assertEquals(1, hub.stateForTest(true), "and without it the same frame captures");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The capture (:95576-95581) zeroes all three speeds, sets {@code Status_InAir} and takes
     * object control. {@code move.w #0,angle(a1)} is a word write over {@code angle} and
     * {@code flip_angle}, so both must land.
     */
    @Test
    void theCaptureStopsThePlayerAndTakesControl() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            sprite.setXSpeed((short) 0x400);
            sprite.setYSpeed((short) -0x300);
            sprite.setGSpeed((short) 0x500);
            sprite.setAngle((byte) 0x20);
            sprite.setFlipAngle(0x40);

            hub.update(0, sprite);

            assertTrue(sprite.getAir(), "bset #Status_InAir (:95576)");
            assertEquals(0, sprite.getXSpeed(), "move.w #0,x_vel (:95577)");
            assertEquals(0, sprite.getYSpeed(), "move.w #0,y_vel (:95578)");
            assertEquals(0, sprite.getGSpeed(), "move.w #0,ground_vel (:95579)");
            assertEquals(0, sprite.getAngle() & 0xFF, "move.w #0,angle writes angle");
            assertEquals(0, sprite.getFlipAngle() & 0xFF, "and flip_angle with it");
            assertTrue(sprite.isObjectControlled(), "move.b #$83,object_control (:95584)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_49348} :95594-95630. Eight pixels per axis per frame until the player is
     * within eight, then a snap onto the hub's own coordinate — and one state bit per axis,
     * so the byte walks 1 → 3 or 5 → 7 rather than counting.
     */
    @Test
    void theHubPullsThePlayerEightPixelsPerAxisPerFrameAndSetsOneBitPerAxis() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X + 0x18, OBJECT_Y - 0x10);
            hub.update(0, sprite);
            assertEquals(1, hub.stateForTest(true), "captured, neither axis centred");

            hub.update(1, sprite);
            assertEquals(OBJECT_X + 0x10, sprite.getCentreX() & 0xFFFF, "x pulled 8 left");
            assertEquals(OBJECT_Y - 0x08, sprite.getCentreY() & 0xFFFF, "y pulled 8 down");
            assertEquals(1, hub.stateForTest(true), "still off centre on both axes");

            hub.update(2, sprite);
            assertEquals(OBJECT_X + 0x08, sprite.getCentreX() & 0xFFFF, "x pulled 8 more");
            // dy is exactly 8 here, and cmpi.w #8,d0 / bhs (:95601) takes the step branch:
            // the snap needs to be strictly inside eight, so it lands a frame later.
            assertEquals(OBJECT_Y, sprite.getCentreY() & 0xFFFF, "y stepped the last 8");
            assertEquals(1, hub.stateForTest(true), "and no bit yet, because it stepped");

            hub.update(3, sprite);
            assertEquals(OBJECT_X, sprite.getCentreX() & 0xFFFF, "x stepped its last 8 too");
            assertEquals(1 | 4, hub.stateForTest(true), "bset #2 for the centred Y axis");

            hub.update(4, sprite);
            assertEquals(1 | 2 | 4, hub.stateForTest(true), "bset #1 completes the set");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code and.b subtype(a0),d1} (:95664) with {@code word_49420} (:95672). The table is
     * ordered up, down, left, right and {@code loc_49408} takes the first set bit.
     */
    @Test
    void eachAllowedDirectionLaunchesAlongItsOwnTableRow() {
        assertLaunch(BUTTON_UP, 0, -EXIT_SPEED);
        assertLaunch(BUTTON_DOWN, 0, EXIT_SPEED);
        assertLaunch(BUTTON_LEFT, -EXIT_SPEED, 0);
        assertLaunch(BUTTON_RIGHT, EXIT_SPEED, 0);
    }

    /**
     * The mask is the placement's, not a constant: a hub whose subtype allows only the
     * vertical exits ignores a left press entirely.
     */
    @Test
    void aDirectionTheSubtypeForbidsDoesNothing() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(BUTTON_UP | BUTTON_DOWN);
            centreRider(hub, sprite);

            pressDirections(sprite, BUTTON_LEFT);
            hub.update(9, sprite);
            assertEquals(1 | 2 | 4, hub.stateForTest(true), "left is not in the subtype mask");
            assertEquals(0, sprite.getXSpeed(), "and no velocity is written");

            pressDirections(sprite, BUTTON_DOWN);
            hub.update(10, sprite);
            assertEquals(8, hub.stateForTest(true), "down is, so the hub launches");
            assertEquals(EXIT_SPEED, sprite.getYSpeed(), "along word_49420's second row");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code d1} is the whole {@code Ctrl_N_logical} word and the {@code and.b} masks its low
     * byte, which is the press half (:95664). A direction already held when the hub catches
     * the player is not a press and must not launch them straight back out.
     */
    @Test
    void aHeldDirectionIsNotAPress() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            pressDirections(sprite, BUTTON_RIGHT);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            for (int frame = 0; frame < 4; frame++) {
                hub.update(frame, sprite);
            }
            assertEquals(1 | 2 | 4, hub.stateForTest(true),
                    "right was held all along, so it never became a press");

            pressDirections(sprite, 0);
            hub.update(4, sprite);
            pressDirections(sprite, BUTTON_RIGHT);
            hub.update(5, sprite);
            assertEquals(8, hub.stateForTest(true), "released and pressed again, it launches");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_49430} (:95677-95690): state {@code 8} holds until the player has left the
     * same {@code $40} px window, and {@code move.w #0,(a2)} then clears both bytes.
     */
    @Test
    void theLaunchedStateOnlyResetsOnceThePlayerHasLeftTheWindow() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            centreRider(hub, sprite);
            pressDirections(sprite, BUTTON_RIGHT);
            hub.update(9, sprite);
            assertEquals(8, hub.stateForTest(true), "launched");

            moveTo(sprite, OBJECT_X + 0x1F, OBJECT_Y);
            hub.update(10, sprite);
            assertEquals(8, hub.stateForTest(true), "still inside the window");

            moveTo(sprite, OBJECT_X + 0x20, OBJECT_Y);
            hub.update(11, sprite);
            assertEquals(0, hub.stateForTest(true), "one column out and the block clears");
            assertEquals(0, hub.poseCounterForTest(true), "move.w #0,(a2) clears both bytes");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_493AE} :95626-95636: the pose counter increments every frame and wraps at
     * {@code $60}, and {@code RawAni_493DA} is indexed by it shifted right two, so each of
     * the twenty-four poses holds for four frames.
     */
    @Test
    void theHubPoseHoldsEachFrameForFourFramesAndWrapsAtSixty() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            hub.update(0, sprite);

            hub.update(1, sprite);
            assertEquals(0x34, sprite.getMappingFrame(), "RawAni_493DA entry 0");
            for (int frame = 2; frame <= 4; frame++) {
                hub.update(frame, sprite);
                assertEquals(0x34, sprite.getMappingFrame(),
                        "entry 0 holds for four frames, frame " + frame);
            }
            hub.update(5, sprite);
            assertEquals(0x35, sprite.getMappingFrame(), "then entry 1");

            for (int frame = 6; frame < 6 + 0x60; frame++) {
                hub.update(frame, sprite);
            }
            assertEquals(0x35, sprite.getMappingFrame(),
                    "and $60 frames later the counter has wrapped to the same pose");
        } finally {
            SessionManager.clear();
        }
    }

    /** Capture and restore of the per-player block, on a partly centred rider. */
    @Test
    void rewindRestoresTheHubsPerPlayerBlock() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X + 0x18, OBJECT_Y);
            hub.update(0, sprite);
            hub.update(1, sprite);
            int stateAtCapture = hub.stateForTest(true);
            int poseAtCapture = hub.poseCounterForTest(true);
            CompositeSnapshot midPull =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            for (int frame = 2; frame < 10; frame++) {
                hub.update(frame, sprite);
            }
            assertNotEquals(poseAtCapture, hub.poseCounterForTest(true),
                    "precondition: the forward run moved the pose counter");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(midPull);
            S3kDezGravityHubObjectInstance restored = restored(hub);
            assertEquals(stateAtCapture, restored.stateForTest(true), "the state byte comes back");
            assertEquals(poseAtCapture, restored.poseCounterForTest(true),
                    "and so does the pose counter");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    private void assertLaunch(int direction, int expectedXSpeed, int expectedYSpeed) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            centreRider(hub, sprite);
            pressDirections(sprite, direction);
            hub.update(9, sprite);
            assertEquals(8, hub.stateForTest(true), "launched on direction " + direction);
            assertEquals(expectedXSpeed, sprite.getXSpeed(), "x_vel for direction " + direction);
            assertEquals(expectedYSpeed, sprite.getYSpeed(), "y_vel for direction " + direction);
            assertFalse(sprite.isObjectControlled(),
                    "move.b #0,object_control (:95669) hands control back");
        } finally {
            SessionManager.clear();
        }
    }

    /** Capture the player and run the centring out until the state byte reads 7. */
    private void centreRider(S3kDezGravityHubObjectInstance hub, AbstractPlayableSprite sprite) {
        moveTo(sprite, OBJECT_X, OBJECT_Y);
        for (int frame = 0; frame < 3; frame++) {
            hub.update(frame, sprite);
        }
        assertEquals(1 | 2 | 4, hub.stateForTest(true), "precondition: centred on both axes");
    }

    private void pressDirections(AbstractPlayableSprite sprite, int mask) {
        sprite.setLogicalInputState((mask & BUTTON_UP) != 0, (mask & BUTTON_DOWN) != 0,
                (mask & BUTTON_LEFT) != 0, (mask & BUTTON_RIGHT) != 0, false);
    }

    private boolean captures(int dx, int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityHubObjectInstance hub = place(SUBTYPE_ALL_EXITS);
            moveTo(sprite, OBJECT_X + dx, OBJECT_Y + dy);
            hub.update(0, sprite);
            return hub.stateForTest(true) != 0;
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezGravityHubObjectInstance place(int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x5C, subtype, 0,
                false, OBJECT_Y, -1);
        S3kDezGravityHubObjectInstance hub = new S3kDezGravityHubObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(hub);
        return hub;
    }

    private S3kDezGravityHubObjectInstance restored(S3kDezGravityHubObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravityHubObjectInstance hub) {
                return hub;
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
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }
}
