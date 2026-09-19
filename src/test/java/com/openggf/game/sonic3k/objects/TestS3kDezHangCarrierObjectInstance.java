package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDezHangCarrierObjectInstance {
    @Test
    void subtypeSetsHorizontalTravelDurationInFourPassUnits() {
        assertEquals(0x94, carrier(0x25).travelFramesForTest());
        assertEquals(0x110, carrier(0x44).travelFramesForTest());
    }

    @Test
    void eligiblePlayerInsideHandleWindowIsCapturedAndSnapped() {
        S3kDezHangCarrierObjectInstance carrier = carrier(0x25);
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x100);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x128);

        carrier.process(player, false);

        assertTrue(carrier.capturedForTest(false));
        assertTrue(player.isObjectControlled());
        assertFalse(player.getAir());
        assertEquals(0x100, player.getCentreX());
        assertEquals(0x128, player.getCentreY());
        assertEquals(0x14, player.getAnimationId());
    }

    @Test
    void playerOutsideRomWindowIsNotCaptured() {
        S3kDezHangCarrierObjectInstance carrier = carrier(0x25);
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x121);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x128);
        carrier.process(player, false);
        assertFalse(carrier.capturedForTest(false));
    }

    private static S3kDezHangCarrierObjectInstance carrier(int subtype) {
        return new S3kDezHangCarrierObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x4C, subtype, 0, false, 0));
    }
}
