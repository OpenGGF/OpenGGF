package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kDezConveyorPadObjectInstance {
    @Test
    void nonzeroSubtypeWaitsForStandingThenMovesForSubtypeTimesEightPasses() {
        S3kDezConveyorPadObjectInstance pad = pad(0x01, 0);
        pad.update(0, null);
        assertEquals(0x100, pad.getY());
        assertEquals(8, pad.travelLeftForTest());

        pad.setStandingForTest(true);
        pad.update(1, null);
        assertEquals(0x101, pad.getY());
        assertEquals(7, pad.travelLeftForTest());
    }

    @Test
    void negativeSubtypeMovesUpAndUsesTheWideSolid() {
        S3kDezConveyorPadObjectInstance pad = pad(0x81, 0);
        pad.setStandingForTest(true);
        pad.update(0, null);

        assertEquals(0x0FF, pad.getY());
        assertEquals(0x8B, pad.getSolidParams().halfWidth());
        assertEquals(0x80, pad.getOnScreenHalfWidth());
    }

    @Test
    void initialPlacementFlipSelectsTheReverseConveyorAnimation() {
        S3kDezConveyorPadObjectInstance pad = pad(0x28, 1);
        pad.setStandingForTest(true);
        pad.update(0, null);
        assertEquals(1, pad.animationForTest());
    }

    @Test
    void zeroSubtypeUsesTheNarrowFullSolidDimensions() {
        S3kDezConveyorPadObjectInstance pad = pad(0x00, 0);
        assertEquals(0x4B, pad.getSolidParams().halfWidth());
        assertEquals(0x10, pad.getSolidParams().airHalfHeight());
        assertEquals(0x11, pad.getSolidParams().groundHalfHeight());
    }

    private static S3kDezConveyorPadObjectInstance pad(int subtype, int flags) {
        return new S3kDezConveyorPadObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x53, subtype, flags, false, 0));
    }
}
