package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravityTubeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $5A}, {@code Obj_DEZGravityTube} (sonic3k.asm:95215-95447): the last
 * {@code Reverse_gravity_flag} <em>reader</em> in the Death Egg object set, and the neighbour
 * of every {@code $5B} gravity-swap trigger.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityTubeHeadless {

    /** The act 2 tube beside the unflipped {@code $5B} at {@code $1A40,$08C0}, subtype {@code $08}. */
    private static final int OBJECT_X = 0x1A40;
    private static final int OBJECT_Y = 0x08C0;
    /** {@code (subtype & $3F) << 3} for subtype {@code $08}. */
    private static final int HALF_SPAN = 0x08 << 3;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code move.w $30(a0),d4 / move.w d4,d5 / add.w d5,d5} then
     * {@code add.w d4,d0 / cmp.w d5,d0 / bhs} (:95238-95270): the X window is
     * {@code ±(subtype & $3F) << 3}, top-inclusive and bottom-exclusive on the biased compare.
     */
    @Test
    void theHorizontalTubeMountsOnlyInsideItsSubtypeDerivedSpan() {
        assertTrue(mounts(0x08, -HALF_SPAN, 0), "dx = -$40 is the inclusive left edge");
        assertTrue(mounts(0x08, HALF_SPAN - 1, 0), "dx = $3F is the last column inside");
        assertFalse(mounts(0x08, -HALF_SPAN - 1, 0), "one past the left edge");
        assertFalse(mounts(0x08, HALF_SPAN, 0), "dx = $40 is the exclusive right edge");
    }

    /**
     * The span is the subtype's, not a constant: subtype {@code $04} is half the reach of
     * subtype {@code $08} at the same placement.
     */
    @Test
    void theSpanScalesWithTheSubtypesLowSixBits() {
        assertTrue(mounts(0x04, 0x1F, 0), "subtype $04 reaches $1F");
        assertFalse(mounts(0x04, 0x20, 0), "and no further");
        assertTrue(mounts(0x08, 0x20, 0), "where subtype $08 still holds");
    }

    /**
     * {@code byte_48F90} (:95299) and {@code byte_48F98} (:95301), indexed by the biased dy
     * shifted right by 3 — and by 4 for the bit 6 tube. The mount angle is what the whole
     * cosine lift is phased from, so a wrong table is a wrong ride from the first frame.
     */
    @Test
    void theMountAngleComesFromTheRomTable() {
        assertEquals(0x40, mountAngle(0x08, 0), "narrow, dy 0: index 4 of byte_48F90");
        assertEquals(0x80, mountAngle(0x08, -0x20), "narrow, dy -$20: index 0");
        assertEquals(0x00, mountAngle(0x08, 0x1F), "narrow, dy $1F: index 7");
        assertEquals(0x40, mountAngle(0x48, 0), "wide, dy 0: index 6 of byte_48F98");
        assertEquals(0x80, mountAngle(0x48, -0x60), "wide, dy -$60: index 0");
    }

    /**
     * {@code move.w #$20,d0} unless {@code btst #6,subtype(a0)}, which makes it {@code $60}
     * (:95229-95234). Both tubes have the same {@code $40} px span in X and different bands.
     */
    @Test
    void subtypeBitSixPicksTheTallerBand() {
        assertFalse(mounts(0x08, 0, 0x20), "the narrow tube's band ends at $20");
        assertTrue(mounts(0x48, 0, 0x20), "the bit 6 tube still holds at $20");
        assertTrue(mounts(0x48, 0, 0x5F), "and up to $5F");
        assertFalse(mounts(0x48, 0, 0x60), "but not at $60");
    }

    /**
     * {@code loc_48FFE} :95342-95363. The rider's Y is the object's plus
     * {@code cos(angle) * $1000 >> 16}, and the angle advances by 8 each frame.
     */
    @Test
    void theHorizontalTubeLiftsTheRiderOnACosineThatAdvancesByEight() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(0x08);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            tube.update(0, sprite);
            assertTrue(tube.isRidingForTest(true), "precondition: mounted");

            int angle = tube.angleForTest(true);
            tube.update(1, sprite);
            assertEquals((OBJECT_Y + ((TrigLookupTable.cosHex(angle) * 0x1000) >> 16)) & 0xFFFF,
                    sprite.getCentreY() & 0xFFFF,
                    "y_pos(a1) = y_pos(a0) + the scaled cosine of the ride angle");
            assertEquals((angle + 8) & 0xFF, tube.angleForTest(true),
                    "addq.b #8,(a2) for a tube without bit 6 (:95300, :95318)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code RideObject_SetRide} (sonic3k.asm:42067-42087) clears the old owner's standing
     * bit before the next object's slot runs. Recreate both owners before replaying that edge.
     */
    @Test
    void aTubeTransferClearsTheFormerTubesRideBeforeItsLaterSlotRuns() {
        var fixture = fixture();
        var sprite = fixture.sprite();
        var manager = GameServices.level().getObjectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var former = placeAt(0x0F80, 0x07C0, 0x10);
        var replacement = placeAt(0x0EC0, 0x0780, 0x48);
        moveTo(sprite, 0x0F01, 0x07CB);
        sprite.setAir(false);
        former.update(0, sprite);
        assertTrue(former.isRidingForTest(true), "precondition: the former tube owns the ride");
        assertSame(former, sprite.getLatchedSolidObjectInstance());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var beforeTransfer = registry.capture();

        transfer(sprite, former, replacement);
        assertTransferred(sprite, former, replacement);
        int replacementAngle = replacement.angleForTest(true);
        replacement.update(2, sprite);
        int replayY = sprite.getCentreY();
        int replayAngle = replacement.angleForTest(true);

        manager.removeDynamicObject(former);
        manager.removeDynamicObject(replacement);
        registry.restore(beforeTransfer);
        var restoredFormer = restored(former);
        var restoredReplacement = restored(replacement);
        assertNotSame(former, restoredFormer, "destructive restore must recreate the former owner");
        assertNotSame(replacement, restoredReplacement, "and the replacement owner");
        assertSame(restoredFormer, sprite.getLatchedSolidObjectInstance(), "restore the actual ride link");
        assertTrue(restoredFormer.isRidingForTest(true));
        assertFalse(restoredReplacement.isRidingForTest(true));
        transfer(sprite, restoredFormer, restoredReplacement);
        assertTransferred(sprite, restoredFormer, restoredReplacement);
        assertEquals(replacementAngle, restoredReplacement.angleForTest(true));
        restoredReplacement.update(2, sprite);
        assertEquals(replayY, sprite.getCentreY(), "the new owner's lift replays after recreation");
        assertEquals(replayAngle, restoredReplacement.angleForTest(true));
    }

    /** {@code Player_TouchFloor} (sonic3k.asm:24375-24416) changes Sonic's native centre by -5. */
    @Test
    void anAirborneRollingMountAppliesTheNativeCentreRadiusDelta() {
        assertAirborneRollingMount(false);
        SessionManager.clear();
        assertAirborneRollingMount(true);
    }

    private void assertAirborneRollingMount(boolean reverseGravity) {
        var fixture = fixture();
        var sprite = fixture.sprite();
        GameServices.gameState().setReverseGravityActive(reverseGravity);
        var manager = GameServices.level().getObjectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var tube = place(0x48);
        sprite.setRolling(true);
        moveTo(sprite, OBJECT_X, OBJECT_Y + 0x14);
        sprite.setSubpixelRaw(0x2345, 0x6789);
        sprite.setAir(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setPushing(true);
        sprite.setJumping(true);
        int rollingCentreY = sprite.getCentreY();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var beforeMount = registry.capture();

        tube.update(0, sprite);
        assertStandingMount(sprite, tube, rollingCentreY, reverseGravity);
        manager.removeDynamicObject(tube);
        registry.restore(beforeMount);
        var restoredTube = restored(tube);
        assertNotSame(tube, restoredTube, "destructive restore must recreate the tube");
        assertTrue(sprite.getRolling(), "restore must return the pre-mount rolling state");
        assertTrue(sprite.getAir());
        assertEquals(rollingCentreY, sprite.getCentreY());
        assertFalse(restoredTube.isRidingForTest(true));
        restoredTube.update(0, sprite);
        assertStandingMount(sprite, restoredTube, rollingCentreY, reverseGravity);
    }

    /**
     * Final-selection addition after the old-source red: horizontal RideObject_SetRide
     * (sonic3k.asm:42067-42089) and vertical sub_33C34 (:70207-70227) are different helpers.
     */
    @Test
    void theTwoBodiesKeepTheirNativeVelocityAngleAndGroundedRollOrderThroughRecreation() {
        for (boolean vertical : new boolean[]{false, true}) {
            for (boolean airborne : new boolean[]{false, true}) {
                SessionManager.clear();
                var fixture = fixture();
                var sprite = fixture.sprite();
                var manager = GameServices.level().getObjectManager();
                manager.setRewindInPlaceRestoreEnabledForTest(false);
                var tube = place(vertical ? 0x98 : 0x48);
                sprite.setRolling(true);
                moveTo(sprite, OBJECT_X, OBJECT_Y + 0x14);
                sprite.setSubpixelRaw(0x3456, 0x789A);
                sprite.setAir(airborne);
                sprite.setAngle((byte) 0x60);
                sprite.setXSpeed((short) 0x300);
                sprite.setYSpeed((short) 0x180);
                sprite.setGSpeed((short) 0x555);
                sprite.setPushing(true);
                sprite.setJumping(true);
                var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
                var beforeMount = registry.capture();

                tube.update(0, sprite);
                assertNativeMountOrder(sprite, tube, vertical, airborne);
                var afterMount = mountState(sprite);
                manager.removeDynamicObject(tube);
                registry.restore(beforeMount);
                var restoredTube = restored(tube);
                assertNotSame(tube, restoredTube);
                assertEquals(0x60, sprite.getAngle() & 0xFF, "restore the original angle");
                assertEquals(0x555, sprite.getGSpeed(), "restore the unrelated original ground velocity");
                assertTrue(sprite.getRolling());
                assertEquals(airborne, sprite.getAir());
                restoredTube.update(0, sprite);
                assertNativeMountOrder(sprite, restoredTube, vertical, airborne);
                assertEquals(afterMount, mountState(sprite), "replay the same evaluated native mount");
            }
        }
    }

    private void assertNativeMountOrder(AbstractPlayableSprite sprite,
                                       S3kDezGravityTubeObjectInstance tube,
                                       boolean vertical, boolean airborne) {
        assertSame(tube, sprite.getLatchedSolidObjectInstance());
        assertTrue(sprite.isOnObject());
        assertFalse(sprite.getAir());
        assertEquals(vertical && airborne ? 0 : 0x300, sprite.getXSpeed(),
                "only airborne sub_33C34 clears x_vel");
        assertEquals(vertical ? 0x180 : 0, sprite.getYSpeed(),
                "only horizontal RideObject_SetRide clears y_vel");
        assertEquals(vertical ? -0x180 : 0x300, sprite.getGSpeed(),
                "vertical derives -y_vel; horizontal copies the preserved x_vel");
        assertEquals(vertical ? 0xC0 : 0, sprite.getAngle() & 0xFF);
        assertEquals(!airborne, sprite.getRolling(), "already grounded riders skip TouchFloor");
        assertEquals(!airborne, sprite.getPushing());
        assertEquals(!airborne, sprite.isJumping());
        // Horizontal clears angle before TouchFloor. Vertical's incoming $60 angle
        // reverses the -5 radius delta, before its caller installs angle $C0.
        assertEquals(OBJECT_Y + 0x14 + (airborne ? (vertical ? 5 : -5) : 0),
                sprite.getCentreY(), "native centre uses the helper's evaluated incoming angle");
        assertEquals(0x3456, sprite.getXSubpixelRaw());
        assertEquals(0x789A, sprite.getYSubpixelRaw());
    }

    private List<Object> mountState(AbstractPlayableSprite sprite) {
        return List.of(sprite.getCentreX(), sprite.getCentreY(),
                sprite.getXSubpixelRaw(), sprite.getYSubpixelRaw(), sprite.getXSpeed(),
                sprite.getYSpeed(), sprite.getGSpeed(), sprite.getAngle(), sprite.getRolling(),
                sprite.getYRadius(), sprite.getAnimationId(), sprite.getFlipAngle(),
                sprite.getFlipType(), sprite.getMappingFrame(), sprite.isObjectControlled(),
                sprite.isOnObject(), sprite.getAir(), sprite.getPushing(), sprite.isJumping());
    }

    private void transfer(AbstractPlayableSprite sprite, S3kDezGravityTubeObjectInstance former,
                          S3kDezGravityTubeObjectInstance replacement) {
        moveTo(sprite, 0x0EFD, 0x07CB);
        replacement.update(1, sprite);
        assertTrue(replacement.isRidingForTest(true), "the earlier slot takes ownership first");
        former.update(1, sprite);
    }

    private void assertTransferred(AbstractPlayableSprite sprite, S3kDezGravityTubeObjectInstance former,
                                   S3kDezGravityTubeObjectInstance replacement) {
        assertFalse(former.isRidingForTest(true), "the former SST standing bit was cleared");
        assertTrue(replacement.isRidingForTest(true));
        assertTrue(sprite.isOnObject(), "the later former owner must not eject the rider");
        assertFalse(sprite.getAir());
        assertSame(replacement, sprite.getLatchedSolidObjectInstance());
    }

    private void assertStandingMount(AbstractPlayableSprite sprite, S3kDezGravityTubeObjectInstance tube,
                                     int rollingCentreY, boolean reverseGravity) {
        assertTrue(tube.isRidingForTest(true));
        assertSame(tube, sprite.getLatchedSolidObjectInstance());
        assertTrue(sprite.isOnObject());
        assertFalse(sprite.getAir());
        assertFalse(sprite.getRolling());
        assertFalse(sprite.getPushing());
        assertFalse(sprite.isJumping());
        assertEquals(0x13, sprite.getYRadius(), "restore Sonic's standing radius");
        assertEquals(rollingCentreY + (reverseGravity ? 5 : -5), sprite.getCentreY(),
                "Sonic's y_radius $0E -> $13 applies the gravity-signed native centre delta");
        assertEquals(0x2345, sprite.getXSubpixelRaw());
        assertEquals(0x6789, sprite.getYSubpixelRaw(), "the ROM writes only the position word");
        assertEquals(1, sprite.getAnimationId(), "the tube applies its pose after TouchFloor");
        assertEquals(0x80, sprite.getFlipType());
    }

    /** {@code moveq #4,d3} and {@code move.w #$5000,d0} for the bit 6 tube (:95349-95352). */
    @Test
    void theBitSixTubeStepsByFourAndSwingsFurther() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(0x48);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            tube.update(0, sprite);
            int angle = tube.angleForTest(true);
            tube.update(1, sprite);
            assertEquals((OBJECT_Y + ((TrigLookupTable.cosHex(angle) * 0x5000) >> 16)) & 0xFFFF,
                    sprite.getCentreY() & 0xFFFF, "the $5000 amplitude");
            assertEquals((angle + 4) & 0xFF, tube.angleForTest(true), "the 4 px angle step");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_48FBA} :95324-95330, the first of the object's two reverse-gravity rows. It
     * is a <em>reflection</em>, {@code -(flip_angle + $40) - $40}, not a negation: a rider
     * leaving at {@code flip_angle} 0 leaves at {@code $80}, and one at {@code $20} at
     * {@code $60}.
     */
    @Test
    void leavingTheTubeInvertedReflectsTheFlipAngle() {
        assertEquals(0x80, flipAngleAfterExit(true, 0),
                "0 reflects to $80 (-(0 + $40) - $40)");
        assertEquals(0x60, flipAngleAfterExit(true, 0x20),
                "$20 reflects to $60");
    }

    /** {@code tst.b (Reverse_gravity_flag).w / beq.s locret_48FF4} (:95324-95325). */
    @Test
    void leavingTheTubeUprightLeavesTheFlipAngleAlone() {
        assertEquals(0x20, flipAngleAfterExit(false, 0x20),
                "upright, the exit skips the whole mirror");
    }

    /**
     * {@code Obj_DEZGravityTube}'s init branches on {@code subtype} bit 7 (:95216-95217), and
     * the vertical body ({@code sub_49090}) has no direct reverse-gravity branch — its
     * exit at :95466-95474 has no mirror. An airborne rolling mount's shared floor-reset
     * radius adjustment is separate from that exit behavior.
     */
    @Test
    void theVerticalTubeSwingsTheRiderAndItsExitDoesNotMirrorTheFlag() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(true);
            S3kDezGravityTubeObjectInstance tube = place(0x98);
            assertTrue(tube.isVerticalForTest(), "subtype $98 has bit 7 set");
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            sprite.setFlipAngle(0x20);
            tube.update(0, sprite);
            assertTrue(tube.isRidingForTest(true), "precondition: mounted");

            int angle = tube.angleForTest(true);
            tube.update(1, sprite);
            assertEquals((OBJECT_X + ((TrigLookupTable.cosHex(angle) * 0x1000) >> 16)) & 0xFFFF,
                    sprite.getCentreX() & 0xFFFF,
                    "x_pos(a1) = x_pos(a0) + the scaled cosine (:95443-95448)");

            // Ride out of the band; the vertical exit writes flip_angle = 1 flat.
            moveTo(sprite, OBJECT_X, OBJECT_Y + (0x18 << 3) + 0x10);
            tube.update(2, sprite);
            assertFalse(tube.isRidingForTest(true), "precondition: left the band");
            assertEquals(1, sprite.getFlipAngle(),
                    "move.b #1,flip_angle(a1) (:95424), with no reverse-gravity branch");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code move.b (a2),d2 / divu.w #$B,d2 / move.b RawAni_491DA(pc,d2.w),mapping_frame(a1)}
     * (:95496-95499). The divisor is what makes the twenty-six-entry table cover a whole turn
     * of the eight-step angle, so it is pinned separately from the swing itself: with the
     * divisor wrong the rider still swings correctly and only the pose is nonsense.
     */
    @Test
    void theVerticalRiderPoseComesFromTheDividedAngle() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(0x98);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            tube.update(0, sprite);
            assertEquals(0x6D, sprite.getMappingFrame(), "angle 0 / $B = index 0");
            tube.update(1, sprite);
            assertEquals(0x6D, sprite.getMappingFrame(), "angle 8 / $B is still index 0");
            tube.update(2, sprite);
            tube.update(3, sprite);
            assertEquals(0x6E, sprite.getMappingFrame(), "angle $18 / $B = index 2");
        } finally {
            SessionManager.clear();
        }
    }

    /** The rewind spot: the ride angle must come back, or the lift resumes out of phase. */
    @Test
    void theRideAngleSurvivesACaptureAndRestore() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(0x08);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            tube.update(0, sprite);
            for (int frame = 1; frame < 6; frame++) {
                tube.update(frame, sprite);
            }
            int angleAtCapture = tube.angleForTest(true);
            CompositeSnapshot midRide =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            for (int frame = 6; frame < 14; frame++) {
                tube.update(frame, sprite);
            }
            assertNotEquals(angleAtCapture, tube.angleForTest(true),
                    "precondition: the forward run moved the angle");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(midRide);
            S3kDezGravityTubeObjectInstance restored = restored(tube);
            assertTrue(restored.isRidingForTest(true), "the ride itself comes back");
            assertEquals(angleAtCapture, restored.angleForTest(true),
                    "and so does the angle the lift is computed from");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    private int flipAngleAfterExit(boolean reverseGravity, int flipAngleOnExit) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(reverseGravity);
            S3kDezGravityTubeObjectInstance tube = place(0x08);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            tube.update(0, sprite);
            assertTrue(tube.isRidingForTest(true), "precondition: mounted");
            sprite.setFlipAngle(flipAngleOnExit);
            moveTo(sprite, OBJECT_X + HALF_SPAN + 0x10, OBJECT_Y);
            tube.update(1, sprite);
            assertFalse(tube.isRidingForTest(true), "precondition: left the span");
            return sprite.getFlipAngle() & 0xFF;
        } finally {
            SessionManager.clear();
        }
    }

    private int mountAngle(int subtype, int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(subtype);
            moveTo(sprite, OBJECT_X, OBJECT_Y + dy);
            sprite.setAir(false);
            tube.update(0, sprite);
            assertTrue(tube.isRidingForTest(true), "precondition: mounted at dy " + dy);
            return tube.angleForTest(true);
        } finally {
            SessionManager.clear();
        }
    }

    private boolean mounts(int subtype, int dx, int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityTubeObjectInstance tube = place(subtype);
            moveTo(sprite, OBJECT_X + dx, OBJECT_Y + dy);
            sprite.setAir(false);
            tube.update(0, sprite);
            return tube.isRidingForTest(true);
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezGravityTubeObjectInstance place(int subtype) {
        return placeAt(OBJECT_X, OBJECT_Y, subtype);
    }

    private S3kDezGravityTubeObjectInstance placeAt(int x, int y, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0x5A, subtype, 0,
                false, y, -1);
        S3kDezGravityTubeObjectInstance tube = new S3kDezGravityTubeObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(tube);
        return tube;
    }

    private S3kDezGravityTubeObjectInstance restored(S3kDezGravityTubeObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravityTubeObjectInstance tube
                    && !tube.isDestroyed() && tube.getSpawn().equals(original.getSpawn())) {
                return tube;
            }
        }
        throw new AssertionError("Missing managed restored tube at " + original.getSpawn());
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
