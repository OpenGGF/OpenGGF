package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezRetractingSpringObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $5D}, {@code Obj_DEZRetractingSpring} (sonic3k.asm:94098-94185) and the shared
 * launch {@code sub_22F98} (:47719-47749).
 *
 * <p>Every expected number is a literal from the ROM listing or from the decoded
 * {@code DEZ2_Sprites} layout, never a call back into the object's own arithmetic.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezRetractingSpringHeadless {

    /** {@code DEZ2_Sprites} record 8: x {@code $04B0}, Y word {@code $24C0}, subtype {@code $02}. */
    private static final int OBJECT_X = 0x04B0;
    private static final int OBJECT_Y = 0x04C0;
    private static final int RECORD_8_FLAGS = 1;
    private static final int SUBTYPE = 0x02;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code word_4808A} (:94093-94095) is {@code dc.w -$1000, -$A00}, indexed by
     * {@code subtype & 2} (:94107-94109). The native act 2 row that named this object shows
     * {@code y_vel} going to {@code $F600}, which is {@code -$A00}.
     */
    @Test
    void theLaunchVelocityIsTheSpringPowersWordChosenBySubtypeBitOne() {
        HeadlessTestFixture fixture = fixture();
        try {
            assertEquals(-0x0A00, place(RECORD_8_FLAGS, 0x02).launchVelocity(),
                    "subtype $02 indexes word_4808A+2");
            assertEquals(-0x1000, place(RECORD_8_FLAGS, 0x00).launchVelocity(),
                    "subtype $00 indexes word_4808A+0");
            assertEquals(-0x0A00, place(RECORD_8_FLAGS, 0x83).launchVelocity(),
                    "only bit 1 selects; bits 0 and 7 do not");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code moveq #0,d1 / move.b width_pixels(a0),d1} with {@code move.b #$10,width_pixels}
     * (:94103, :94159-94160) and {@code moveq #9,d3} (:94161). {@code SolidObjectTop_1P} is
     * given no second height, so the air and ground boxes are the same nine pixels.
     */
    @Test
    void theSolidBoxIsTheRomsArgumentsToSolidObjectTop() {
        HeadlessTestFixture fixture = fixture();
        try {
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            assertEquals(0x10, spring.getSolidParams().halfWidth(), "d1 = width_pixels = $10");
            assertEquals(9, spring.getSolidParams().airHalfHeight(), "d3 = 9");
            assertEquals(9, spring.getSolidParams().groundHalfHeight(), "d3 = 9");
            assertTrue(spring.isTopSolidOnly(), "jsr (SolidObjectTop_1P).l (:94166)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_480D4} :94116-94127. The player must be a full {@code $20} below before the
     * piston moves, and it moves eight pixels an update up to {@code $32(a0) = $20}.
     */
    @Test
    void theSpringExtendsEightPixelsAnUpdateOnceThePlayerIsTwentyBelow() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x20);
            int[] expected = { 8, 0x10, 0x18, 0x20, 0x20, 0x20 };
            for (int update = 0; update < expected.length; update++) {
                spring.update(update, sprite);
                assertEquals(expected[update], spring.extensionForTest(),
                        "update " + update + ": addq.w #8 capped at $32(a0)");
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code cmpi.w #$20,d0 / blt.s loc_48124} (:94116-94117): {@code $1F} below is inside the
     * dead band and {@code $20} below is not.
     */
    @Test
    void theSpringHoldsStillWhileThePlayerIsInsideTheDeadBand() {
        assertEquals(0, extensionAfterOneUpdateAt(0x1F), "$1F below is blt");
        assertEquals(8, extensionAfterOneUpdateAt(0x20), "$20 below is not");
        assertEquals(0, extensionAfterOneUpdateAt(0), "level with the spring does nothing");
        assertEquals(0, extensionAfterOneUpdateAt(-0x20),
                "cmpi.w #-$20,d0 / bge (:94132-94133): exactly $20 above does nothing");
    }

    /**
     * {@code loc_48102} :94132-94143. The retract needs more than {@code $20} above, so the
     * band is asymmetric: {@code $20} below extends but {@code $20} above does not retract.
     */
    @Test
    void theSpringRetractsOnlyWhenThePlayerIsMoreThanTwentyAbove() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x20);
            for (int update = 0; update < 4; update++) {
                spring.update(update, sprite);
            }
            assertEquals(0x20, spring.extensionForTest(), "precondition: fully extended");

            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x20);
            spring.update(4, sprite);
            assertEquals(0x20, spring.extensionForTest(), "exactly $20 above is bge, so it holds");

            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x21);
            int[] expected = { 0x18, 0x10, 8, 0, 0, 0 };
            for (int update = 0; update < expected.length; update++) {
                spring.update(5 + update, sprite);
                assertEquals(expected[update], spring.extensionForTest(),
                        "retract update " + update + ": subq.w #8 floored by tst.w $34(a0)");
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_48124} :94146-94157. {@code btst #0,status(a0)} skips the first negate and
     * {@code btst #1,status(a0)} adds a second, so the sign is the exclusive or of the flips.
     * Decoded from {@code Levels/DEZ/Object Pos/2.bin}: records 4 and 373 carry flags 0,
     * records 8, 198, 239, 327, 397 and 398 carry 1 or 2, and records 206, 332, 355, 356 and
     * 414 carry 3.
     */
    @Test
    void theExtensionDirectionIsTheExclusiveOrOfTheTwoFlipBits() {
        assertEquals(OBJECT_X - 8, xAfterOneExtendingUpdate(0), "no flip extends towards -X");
        assertEquals(OBJECT_X + 8, xAfterOneExtendingUpdate(1), "x flip alone extends towards +X");
        assertEquals(OBJECT_X + 8, xAfterOneExtendingUpdate(2), "y flip alone extends towards +X");
        assertEquals(OBJECT_X - 8, xAfterOneExtendingUpdate(3), "both flips extend towards -X");
    }

    /** The Y never moves: the object writes {@code x_pos} only (:94157-94158). */
    @Test
    void theSpringNeverMovesInY() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x40);
            for (int update = 0; update < 6; update++) {
                spring.update(update, sprite);
                assertEquals(OBJECT_Y, spring.getY(), "update " + update);
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code tst.w $34(a0) / bne.s loc_480FC} (:94121-94122) and
     * {@code cmp.w $34(a0),d1 / bne.s loc_48120} (:94137-94138): {@code sfx_SpringLatch}
     * ({@code $9A}) plays only on the update that leaves rest and the update that leaves full
     * extension, not on the three in between.
     */
    @Test
    void theLatchSoundPlaysOnlyWhenLeavingRestOrFullExtension() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);

            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x20);
            for (int update = 0; update < 6; update++) {
                spring.update(update, sprite);
            }
            assertEquals(List.of(Sonic3kSfx.SPRING_LATCH.id), requested,
                    "four extending updates and two idle ones latch once");

            requested.clear();
            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x40);
            for (int update = 0; update < 6; update++) {
                spring.update(6 + update, sprite);
            }
            assertEquals(List.of(Sonic3kSfx.SPRING_LATCH.id), requested,
                    "and the retract latches once as it leaves $20");
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_22F98} :47721-47732. {@code addq.w #8,y_pos(a1)}, the stored velocity,
     * {@code Status_InAir} set, {@code Status_OnObj} cleared, {@code jumping} cleared and
     * animation {@code $10}.
     */
    @Test
    void standingLaunchesThePlayerWithTheEightPixelNudge() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x18);
            sprite.setYSpeed((short) 0x0100);
            sprite.setAir(false);
            sprite.setOnObject(true);
            int before = sprite.getCentreY();

            spring.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);

            assertEquals(before + 8, sprite.getCentreY(), "addq.w #8,y_pos(a1) (:47721)");
            assertEquals(-0x0A00, sprite.getYSpeed(), "move.w $30(a0),y_vel(a1) (:47726)");
            assertTrue(sprite.getAir(), "bset #1,status(a1) (:47727)");
            assertFalse(sprite.isOnObject(), "bclr #3,status(a1) (:47728)");
            assertEquals(Sonic3kAnimationIds.SPRING.id(), sprite.getAnimationId(),
                    "move.b #$10,anim(a1) (:47731)");
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void nativeTopLandingWindowKeepsItsExactEdgesInBothGravityDirections() {
        for (boolean reverse : new boolean[] {false, true}) {
            for (boolean rolling : new boolean[] {false, true}) {
                for (int overlap : new int[] {-1, 0, 1, 15, 16, 17}) {
                    for (int dx : new int[] {-17, -16, 0, 15, 16}) {
                        HeadlessTestFixture fixture = fixture();
                        try {
                            int x = 672, y = 928;
                            GameServices.gameState().setReverseGravityActive(reverse);
                            var spring = new S3kDezRetractingSpringObjectInstance(
                                    new ObjectSpawn(x, y, 0x5D, SUBTYPE, 2, false, y));
                            var objects = GameServices.level().getObjectManager();
                            objects.addDynamicObject(spring);
                            var sprite = fixture.sprite();
                            sprite.setRolling(rolling);
                            int reach = 9 + sprite.getYRadius() + 4 - overlap;
                            moveTo(sprite, x + dx, y + (reverse ? reach : -reach));
                            sprite.setAir(true);
                            sprite.setOnObject(false);
                            sprite.setYSpeed((short) 0x100);
                            sprite.setXSpeed((short) 0);
                            fixture.camera().updatePosition(true);
                            spring.snapshotPreUpdatePosition();
                            objects.processImmediateInlineSolidCheckpoint(spring, sprite, List.of());
                            boolean accepted = overlap > 0 && overlap <= 16 && dx >= -16 && dx < 16;
                            assertEquals(accepted ? -0xA00 : 0x100, sprite.getYSpeed(),
                                    "reverse=" + reverse + " rolling=" + rolling + " overlap=" + overlap + " dx=" + dx);
                        } finally {
                            GameServices.gameState().setReverseGravityActive(false);
                            SessionManager.clear();
                        }
                    }
                }
            }
        }
    }

    @Test
    void realSolidContactLaunchesFromTheCorrectFaceWithStandingAndRollingRadii() {
        for (boolean reverse : new boolean[] {false, true}) {
            for (boolean rolling : new boolean[] {false, true}) {
                HeadlessTestFixture fixture = fixture();
                try {
                    // A clear column in the ROM-backed DEZ2 layout, isolated from terrain.
                    int x = 672, y = 928;
                    assertFalse(com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, 19).hasCollision());
                    assertFalse(com.openggf.physics.ObjectTerrainUtils.checkCeilingDist(x, y, 19).hasCollision());
                    GameServices.gameState().setReverseGravityActive(reverse);
                    var spring = new S3kDezRetractingSpringObjectInstance(
                            new ObjectSpawn(x, y, 0x5D, SUBTYPE, 2, false, y));
                    GameServices.level().getObjectManager().addDynamicObject(spring);
                    var sprite = fixture.sprite();
                    sprite.setRolling(rolling);
                    int reach = 9 + sprite.getYRadius() + 2;
                    moveTo(sprite, x, y + (reverse ? reach : -reach));
                    sprite.setAir(true);
                    sprite.setOnObject(false);
                    sprite.setXSpeed((short) 0);
                    sprite.setGSpeed((short) 0);
                    sprite.setYSpeed((short) 0x100);
                    fixture.camera().updatePosition(true);
                    fixture.stepIdleFrames(1);
                    // loc_1E45A: y_obj-d3-radius-1; loc_1E4D6: y_obj+d3+radius.
                    // Player_TouchFloor restores standing radii; sub_22F98 adds +/-8.
                    int expectedY = reverse ? y + 9 + 19 - 8 : y - 9 - 19 - 1 + 8;
                    assertEquals(-0xA00, sprite.getYSpeed(), "real contact must launch");
                    assertEquals(expectedY, sprite.getCentreY(),
                            "reverse=" + reverse + ", rolling=" + rolling);
                } finally {
                    GameServices.gameState().setReverseGravityActive(false);
                    SessionManager.clear();
                }
            }
        }
    }

    /**
     * {@code tst.b (Reverse_gravity_flag).w / subi.w #2*8,y_pos(a1)} (:47722-47724): the
     * {@code +8} becomes a net {@code -8}. The velocity word is <em>not</em> mirrored.
     */
    @Test
    void theEightPixelNudgeMirrorsUnderReverseGravityButTheVelocityDoesNot() {
        HeadlessTestFixture fixture = fixture();
        try {
            GameServices.gameState().setReverseGravityActive(true);
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x18);
            int before = sprite.getCentreY();

            spring.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);

            assertEquals(before - 8, sprite.getCentreY(), "+8 then -16 (:47721-47724)");
            assertEquals(-0x0A00, sprite.getYSpeed(), "the launch word is read unchanged");
        } finally {
            GameServices.gameState().setReverseGravityActive(false);
            SessionManager.clear();
        }
    }

    /**
     * {@code move.b subtype(a0),d0 / bpl.s loc_22FE0 / move.w #0,x_vel(a1)} (:47733-47735). No
     * {@code $5D} placement sets bit 7, so this is the subroutine's contract, not a placement's.
     */
    @Test
    void onlyASubtypeWithBitSevenClearsHorizontalSpeed() {
        assertEquals(0x0400, xSpeedAfterLaunch(0x02), "subtype $02 leaves x_vel alone");
        assertEquals(0, xSpeedAfterLaunch(0x82), "subtype $82 is bmi, so x_vel is zeroed");
    }

    /**
     * {@code byte_481A9} (Levels/DEZ/Misc Object Data/Anim - Retracting Spring.asm):
     * {@code 0, 1,0,0,2,2,2,2,2,2, $FD,0}. The leading zero is the duration, so each of the
     * nine frames lasts one update, and {@code $FD} returns to script 0 whose own
     * {@code $F} duration holds frame 0 for sixteen.
     */
    @Test
    void theBounceAnimationRunsTheRomScriptAndThenReturnsToIdle() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x18);
            spring.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);
            assertEquals(1, spring.animIdForTest(), "move.w #1<<8,anim(a0) (:47720)");

            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x08);
            int[] expected = { 1, 0, 0, 2, 2, 2, 2, 2, 2 };
            for (int update = 0; update < expected.length; update++) {
                spring.update(update, sprite);
                assertEquals(expected[update], spring.mappingFrameForTest(),
                        "byte_481A9 entry " + update);
            }
            spring.update(expected.length, sprite);
            assertEquals(0, spring.animIdForTest(), "$FD,0 switches back to byte_481A6");
        } finally {
            SessionManager.clear();
        }
    }

    /** The extension and the animation are object state a rewind has to carry. */
    @Test
    void theExtensionAndAnimationSurviveARewind() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x20);
            spring.update(0, sprite);
            spring.update(1, sprite);
            assertEquals(0x10, spring.extensionForTest(), "precondition: two extending updates");
            CompositeSnapshot halfway =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            spring.update(2, sprite);
            spring.update(3, sprite);
            assertEquals(0x20, spring.extensionForTest(), "precondition: the run moved on");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(halfway);
            assertEquals(0x10, restored(spring).extensionForTest(), "the extension comes back");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    private int extensionAfterOneUpdateAt(int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + dy);
            spring.update(0, sprite);
            return spring.extensionForTest();
        } finally {
            SessionManager.clear();
        }
    }

    private int xAfterOneExtendingUpdate(int renderFlags) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(renderFlags, SUBTYPE);
            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x20);
            spring.update(0, sprite);
            return spring.getX();
        } finally {
            SessionManager.clear();
        }
    }

    private int xSpeedAfterLaunch(int subtype) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezRetractingSpringObjectInstance spring = place(RECORD_8_FLAGS, subtype);
            moveTo(sprite, OBJECT_X, OBJECT_Y - 0x18);
            sprite.setXSpeed((short) 0x0400);
            spring.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);
            return sprite.getXSpeed();
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezRetractingSpringObjectInstance place(int renderFlags, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x5D, subtype, renderFlags,
                false, OBJECT_Y | (renderFlags << 13), -1);
        S3kDezRetractingSpringObjectInstance spring =
                new S3kDezRetractingSpringObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(spring);
        return spring;
    }

    private S3kDezRetractingSpringObjectInstance restored(
            S3kDezRetractingSpringObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezRetractingSpringObjectInstance spring) {
                return spring;
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
