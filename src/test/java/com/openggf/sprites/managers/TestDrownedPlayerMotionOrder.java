package com.openggf.sprites.managers;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every drowned player moves by its current velocity first and only then gains
 * $10 of y_vel, independent of object_control: S1 Drown_Countdown's .sinking
 * (SpeedToPos, "0A LZ Drowning Countdown.asm":280-285), S2 Obj0A
 * (ObjectMove, s2.asm:42186-42190) and S3K routine $C (MoveSprite_TestGravity2,
 * sonic3k.asm:24702-24704, 29322-29324).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestDrownedPlayerMotionOrder {

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drownedPlayerMovesBeforeGainingSinkVelocity(boolean heldByObject) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0)
                .build();
        AbstractPlayableSprite player = fixture.sprite();
        assertTrue(player.applyDrownDeath());
        player.setSubpixelRaw(player.getXSubpixelRaw(), 0xFE00);
        if (heldByObject) {
            // A carrying object (for example the HCZ water wall) holds full control.
            ObjectControlState.nativeBit7FullControl().applyTo(player);
        }
        int startY = player.getCentreY();

        fixture.stepIdleFrames(1);
        assertEquals(startY, player.getCentreY(), "the first drowned pass moves by y_vel 0");
        assertEquals(0xFE00, player.getYSubpixelRaw());
        assertEquals(0x10, player.getYSpeed());

        fixture.stepIdleFrames(1);
        assertEquals(startY + 1, player.getCentreY(), "$10 of y_vel carries $FE00 into the next pixel");
        assertEquals(0x0E00, player.getYSubpixelRaw());
        assertEquals(0x20, player.getYSpeed());
    }
}
