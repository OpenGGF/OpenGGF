package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.physics.ReverseGravity;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 2a-1 of the S3K Death Egg reverse-gravity slice: airborne position integration.
 *
 * <p>{@code MoveSprite_TestGravity} (sonic3k.asm:36068-36083) with
 * {@code Reverse_gravity_flag} ($FFFFF7C6) set still does
 * {@code addi.w #$38,y_vel(a0)} — the velocity is never inverted — but loads the
 * <em>old</em> {@code y_vel} into {@code d0}, runs {@code neg.w d0}, and adds that to
 * {@code y_pos}. Positive {@code y_vel} therefore still means "falling", and falling is
 * now upward on screen. {@code MoveSprite_TestGravity2} (:36088-36101) does the same
 * without the gravity step.
 *
 * <p>The ROM never checks the zone here, so the branch is exercised from a normal S3K
 * level with the flag forced through {@code GameStateManager}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityIntegration {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** {@code neg.w d0} on the integration register alone: the helper the call sites share. */
    @Test
    void theIntegrationRegisterIsNegatedOnlyWhileTheFlagIsSet() {
        assertEquals((short) 0x0400, ReverseGravity.integrationYSpeed(false, (short) 0x0400));
        assertEquals((short) -0x0400, ReverseGravity.integrationYSpeed(true, (short) 0x0400));
        assertEquals((short) 0x0400, ReverseGravity.integrationYSpeed(true, (short) -0x0400));
        // neg.w of $8000 is $8000 on the 68000 and in Java's short arithmetic.
        assertEquals((short) 0x8000, ReverseGravity.integrationYSpeed(true, (short) 0x8000));
    }

    /**
     * A falling player with the flag set rises 4 px on the first frame for
     * {@code y_vel = $400} while {@code y_vel} still grows by {@code $38}.
     *
     * <p>What is mirrored is the 32-bit {@code y_pos} word pair, not the pixel word on
     * its own. {@code add.l d0,y_pos(a0)} carries through the 16 subpixel bits, so a
     * {@code $438} step that reads as a {@code +4} pixel delta with a {@code $38}
     * fraction reads as {@code -5} with a {@code $C8} fraction going the other way.
     * The engine's first attempt at this test asserted symmetric pixel deltas, which is
     * a Java intuition rather than anything the ROM does.
     */
    @Test
    void airborneIntegrationMirrorsTheFallWithoutInvertingTheVelocity() {
        Sample upright = sampleTwoAirborneFrames(false);
        Sample inverted = sampleTwoAirborneFrames(true);

        // y_vel is untouched by the flag: +$38 per frame either way.
        assertEquals((short) (0x0400 + 0x38), upright.yVelAfterFrame1,
                "upright: addi.w #$38,y_vel(a0)");
        assertEquals(upright.yVelAfterFrame1, inverted.yVelAfterFrame1,
                "MoveSprite_TestGravity applies the same +$38 with the flag set");
        assertEquals(upright.yVelAfterFrame2, inverted.yVelAfterFrame2,
                "gravity keeps accumulating in the same direction");

        // Position integrates the OLD y_vel: +$400 is 4 px down upright, 4 px up inverted.
        assertEquals(4, upright.yPixelDeltaFrame1, "upright: y_pos += old y_vel ($400 = 4 px)");
        assertEquals(-4, inverted.yPixelDeltaFrame1, "inverted: y_pos += -old y_vel");

        // The whole 32-bit position step is the exact negation, on both frames.
        assertEquals(0x0400 << 8, upright.yPos32DeltaFrame1, "upright frame 1 step is $400 << 8");
        assertEquals(-upright.yPos32DeltaFrame1, inverted.yPos32DeltaFrame1);
        assertEquals((0x0400 + 0x38) << 8, upright.yPos32DeltaFrame2,
                "upright frame 2 integrates the post-gravity $438");
        assertEquals(-upright.yPos32DeltaFrame2, inverted.yPos32DeltaFrame2,
                "the second frame mirrors too, so the whole arc is mirrored");

        assertTrue(upright.stayedAirborne && inverted.stayedAirborne,
                "the probe must stay in free air: a landing would make the deltas meaningless");
    }

    private record Sample(short yVelAfterFrame1, short yVelAfterFrame2,
                          int yPixelDeltaFrame1,
                          int yPos32DeltaFrame1, int yPos32DeltaFrame2,
                          boolean stayedAirborne) { }

    private Sample sampleTwoAirborneFrames(boolean reverseGravity) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        AbstractPlayableSprite sprite = fixture.sprite();
        GameServices.gameState().setReverseGravityActive(reverseGravity);

        sprite.setAir(true);
        sprite.setCentreY((short) (sprite.getCentreY() - 0x40));
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0x0400);

        short y0 = sprite.getCentreY();
        int p0 = yPos32(sprite);
        fixture.stepIdleFrames(1);
        short y1 = sprite.getCentreY();
        int p1 = yPos32(sprite);
        short v1 = sprite.getYSpeed();
        boolean air1 = sprite.getAir();
        fixture.stepIdleFrames(1);
        int p2 = yPos32(sprite);
        short v2 = sprite.getYSpeed();
        boolean air2 = sprite.getAir();

        SessionManager.clear();
        return new Sample(v1, v2, y1 - y0, p1 - p0, p2 - p1, air1 && air2);
    }

    /** The ROM's 32-bit {@code y_pos} long: pixel word in the high half, subpixel in the low. */
    private static int yPos32(AbstractPlayableSprite sprite) {
        return (sprite.getY() << 16) | sprite.getYSubpixelRaw();
    }
}
