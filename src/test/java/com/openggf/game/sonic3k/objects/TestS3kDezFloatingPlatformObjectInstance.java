package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestS3kDezFloatingPlatformObjectInstance {
    @Test
    void stationarySubtypeKeepsSpawnPositionAndAlternatesMappings() {
        S3kDezFloatingPlatformObjectInstance platform = platform(0);
        platform.update(0, null);
        assertEquals(0x100, platform.getX());
        assertEquals(0x180, platform.getY());
        assertEquals(1, platform.mappingFrameForTest());
        platform.update(1, null);
        assertEquals(0, platform.mappingFrameForTest());
    }

    @Test
    void platformUsesTheRomFullSolidAndDeletionAnchor() {
        S3kDezFloatingPlatformObjectInstance platform = platform(4);
        assertFalse(platform.isTopSolidOnly());
        assertEquals(0x2B, platform.getSolidParams().halfWidth());
        assertEquals(0x10, platform.getSolidParams().airHalfHeight());
        assertEquals(0x11, platform.getSolidParams().groundHalfHeight());
        assertEquals(0x100, platform.getOutOfRangeReferenceX());
        assertEquals(2, platform.romObjectCodePointerHighWord());
    }

    private static S3kDezFloatingPlatformObjectInstance platform(int subtype) {
        return new S3kDezFloatingPlatformObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x4A, subtype, 0, false, 0));
    }
}
