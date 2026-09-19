package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZSolidMovingPlatforms} (sonic3k.asm:51012-51110) and {@code sub_25974}
 * (:51149-51188).
 *
 * <p>Every expectation is the ROM's own arithmetic: the two table reads of the subtype, the
 * {@code Oscillating_table} centres, and the 8.8 accumulator whose <em>high byte</em> the limit is
 * compared against. The last is the one a transcription gets wrong: read {@code $36} as a plain
 * word and the platform reaches its limit in two frames instead of about ninety.
 */
class TestLrzSolidMovingPlatform {

    private static final int X = 0x1000;
    private static final int Y = 0x0800;

    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        services = new TestObjectServices().withIsolatedObjectManager();
    }

    private LrzSolidMovingPlatformObjectInstance platform(int subtype, boolean mirrored) {
        LrzSolidMovingPlatformObjectInstance object = new LrzSolidMovingPlatformObjectInstance(
                new ObjectSpawn(X, Y, 0x2D, subtype, mirrored ? 1 : 0, false, 0));
        object.setServices(services);
        return object;
    }

    /** {@code lsr.w #2,d0 / andi.w #$1C,d0} against {@code byte_25826}'s two entries. */
    @Test
    void theSkinComesFromBitFourAndNothingElse() {
        assertEquals(0, platform(0x00, false).mappingFrame(), "subtype $00 is the first entry");
        assertEquals(0, platform(0x06, false).mappingFrame(), "the mover nibble does not reach it");
        assertEquals(1, platform(0x10, false).mappingFrame(), "subtype $10 is the second entry");
        assertEquals(1, platform(0x15, false).mappingFrame(), "and so is $15");
    }

    /** {@code locret_258CE}: {@code off_258BC}'s first entry is an {@code rts}. */
    @Test
    void moverZeroNeverMoves() {
        LrzSolidMovingPlatformObjectInstance platform = platform(0x00, false);
        for (int frame = 0; frame < 300; frame++) {
            platform.update(frame, null);
            assertEquals(X, platform.getCentreX(), "frame " + frame);
            assertEquals(Y, platform.getCentreY(), "frame " + frame);
        }
    }

    /**
     * {@code loc_258D0} and {@code loc_258FA} (sonic3k.asm:51056-51060, :51081-51085): the same
     * {@code Oscillating_table+$0A} byte less {@code $20}, on x for mover 1 and on y for mover 4,
     * and the other coordinate is never written.
     */
    @Test
    void theOscillatingMoversTakeTheTableByteLessItsCentre() {
        LrzSolidMovingPlatformObjectInstance horizontal = platform(0x01, false);
        LrzSolidMovingPlatformObjectInstance vertical = platform(0x04, false);

        horizontal.update(0, null);
        vertical.update(0, null);

        int expected = (OscillationManager.getByte(0x0A) & 0xFF) - 0x20;
        assertEquals((X + expected) & 0xFFFF, horizontal.getCentreX(),
                "add.w $30(a0),d0 / move.w d0,x_pos(a0)");
        assertEquals(Y, horizontal.getCentreY(), "the vertical coordinate is left at the anchor");
        assertEquals((Y + expected) & 0xFFFF, vertical.getCentreY(),
                "add.w $34(a0),d0 / move.w d0,y_pos(a0)");
        assertEquals(X, vertical.getCentreX(), "the horizontal coordinate is left at the anchor");
    }

    /** {@code btst #0,status(a0) / neg.w d0} (sonic3k.asm:51061-51064). */
    @Test
    void theMirroredPlacementTakesTheOppositeSide() {
        LrzSolidMovingPlatformObjectInstance plain = platform(0x02, false);
        LrzSolidMovingPlatformObjectInstance mirrored = platform(0x02, true);

        plain.update(0, null);
        mirrored.update(0, null);

        int displacement = (OscillationManager.getByte(0x1E) & 0xFF) - 0x40;
        assertEquals((X + displacement) & 0xFFFF, plain.getCentreX());
        assertEquals((X - displacement) & 0xFFFF, mirrored.getCentreX());
    }

    /**
     * {@code sub_25974}: {@code $40} steps by {@code 4} as an 8.8 velocity and {@code $36}
     * accumulates it, so the first frame moves the platform by nothing at all -- four 256ths --
     * and the displacement the routine returns is {@code $36}'s high byte.
     */
    @Test
    void theRampAcceleratesInEightEightAndTurnsAtItsLimit() {
        LrzSolidMovingPlatformObjectInstance platform = platform(0x03, false);

        platform.update(0, null);
        assertEquals(4, platform.rampVelocity(), "addq.w #4,d1 / move.w d1,$40(a0)");
        assertEquals(4, platform.rampPosition(), "add.w d1,$36(a0)");
        assertEquals((X - 0x60) & 0xFFFF, platform.getCentreX(),
                "the high byte is still zero, so the platform sits at anchor - $60");

        // The high byte only reaches 1 once $36 passes $100, which takes the accumulator a while.
        int framesToFirstPixel = 1;
        while (((platform.rampPosition() >> 8) & 0xFF) == 0 && framesToFirstPixel < 100) {
            platform.update(framesToFirstPixel++, null);
        }
        assertTrue(framesToFirstPixel > 5,
                "an 8.8 accumulator stepping by 4 cannot cross $100 in five frames; it took "
                        + framesToFirstPixel);

        boolean turned = false;
        for (int frame = 0; frame < 400 && !turned; frame++) {
            platform.update(frame, null);
            turned = platform.isRampReturning();
        }
        assertTrue(turned, "cmp.b $36(a0),d2 / bhi: $3C flips once the high byte reaches $5F");
        assertNotEquals(X, platform.getCentreX(), "and the platform has travelled by then");
    }

    /** {@code move.w #$7F,d2} against {@code #$5F} (sonic3k.asm:51124, :51152). */
    @Test
    void theLongRampTravelsFurtherThanTheShortOne() {
        LrzSolidMovingPlatformObjectInstance shortRamp = platform(0x03, false);
        LrzSolidMovingPlatformObjectInstance longRamp = platform(0x07, false);

        int shortFrames = 0;
        while (!shortRamp.isRampReturning() && shortFrames < 400) {
            shortRamp.update(shortFrames++, null);
        }
        int longFrames = 0;
        while (!longRamp.isRampReturning() && longFrames < 400) {
            longRamp.update(longFrames++, null);
        }

        assertTrue(shortRamp.isRampReturning() && longRamp.isRampReturning(),
                "both ramps must turn inside the budget");
        assertTrue(longFrames > shortFrames,
                "$7F is further than $5F on the same acceleration: " + longFrames
                        + " against " + shortFrames);
    }

    /** {@code addi.w #$B,d1} on {@code width_pixels}, {@code d3 = d2 + 1} (:51041-51046). */
    @Test
    void theSolidBoxIsTheWidthBytePlusElevenAndOnePixelTallerOnTheGround() {
        LrzSolidMovingPlatformObjectInstance platform = platform(0x00, false);
        assertEquals(0x20 + 0x0B, platform.getSolidParams().halfWidth());
        assertEquals(0x20, platform.getSolidParams().airHalfHeight());
        assertEquals(0x21, platform.getSolidParams().groundHalfHeight());
    }
}
