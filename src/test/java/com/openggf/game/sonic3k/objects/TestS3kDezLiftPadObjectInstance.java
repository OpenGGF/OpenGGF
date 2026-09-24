package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kDezLiftPadObjectInstance {
    @Test
    void lowSubtypeNibbleSetsTheRomMultiSpritePointCount() {
        assertEquals(7, pad(0x07, 0).pointCountForTest());
        assertEquals(7, pad(0x27, 0).pointCountForTest());
    }

    @Test
    void subtypeOrientationAndXFlipTransformTheEightStepEndpoint() {
        S3kDezLiftPadObjectInstance horizontal = pad(0x07, 0);
        S3kDezLiftPadObjectInstance vertical = pad(0x27, 0);
        S3kDezLiftPadObjectInstance flipped = pad(0x07, 1);

        assertEquals(0x060, horizontal.getX());
        assertEquals(0x100, horizontal.getY());
        assertEquals(0x0E0, vertical.getX());
        assertEquals(0x080, vertical.getY());
        assertEquals(0x1A0, flipped.getX());
        assertEquals(0x100, flipped.getY());
    }

    @Test
    void firstStandingContactAcceleratesInWordFixedPoint() {
        S3kDezLiftPadObjectInstance pad = pad(0x07, 0);

        pad.updateOscillator(true);
        assertEquals(8, pad.angularVelocityForTest());
        assertEquals(8, pad.motionAngleForTest());

        pad.updateOscillator(false);
        pad.updateOscillator(false);
        assertEquals(24, pad.angularVelocityForTest());
        assertEquals(48, pad.motionAngleForTest());

        pad.updateOscillator(false);
        assertEquals(32, pad.angularVelocityForTest());
        assertEquals(80, pad.motionAngleForTest());
    }

    @Test
    void wordAngleUsesItsBigEndianHighByteAsTheVisiblePhase() {
        S3kDezLiftPadObjectInstance pad = pad(0x27, 0);

        pad.updateOscillator(true);
        pad.updatePositionsForTest();
        assertEquals(0x0E0, pad.getX());
        assertEquals(0x080, pad.getY(), "angle word $0008 still exposes byte angle zero");

        for (int i = 0; i < 7; i++) {
            pad.updateOscillator(true);
        }
        pad.updatePositionsForTest();
        assertTrue(pad.getX() != 0x0E0 || pad.getY() != 0x080,
                "the endpoint moves once the word's high byte advances");
    }

    @Test
    void zeroVelocityStartsThirtyPassReleaseDelayWhichStandingFreezes() {
        S3kDezLiftPadObjectInstance pad = pad(0x07, 0);
        for (int i = 0; i < 90; i++) {
            pad.updateOscillator(i == 0);
        }
        assertEquals(0, pad.angularVelocityForTest());
        assertEquals(30, pad.releaseDelayForTest());

        pad.updateOscillator(true);
        assertEquals(30, pad.releaseDelayForTest());
        pad.updateOscillator(false);
        assertEquals(29, pad.releaseDelayForTest());
    }

    @Test
    void liftPadUsesTopOnlyRomSolidDimensions() {
        S3kDezLiftPadObjectInstance pad = pad(0x07, 0);
        assertTrue(pad.isTopSolidOnly());
        assertTrue(pad.rejectsZeroDistanceTopSolidLanding());
        assertEquals(0x18, pad.getSolidParams().halfWidth());
    }

    private static S3kDezLiftPadObjectInstance pad(int subtype, int flags) {
        return new S3kDezLiftPadObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x4E, subtype, flags, false, 0));
    }
}
