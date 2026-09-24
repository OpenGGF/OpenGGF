package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kStartNewLevelObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ROM contract checks for LRZ2's {@code Obj_StartNewLevel} placement. */
class TestS3kLrzStartNewLevelObject {

    @Test
    void subtype2dTransformsToHiddenPalace1601InsideTheRomRange() {
        RecordingServices services = new RecordingServices();
        S3kStartNewLevelObjectInstance object = objectAt(0x3FE0, 0x00E0, services);
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x3FD0, (short) 0x0060);

        object.update(0, player);

        assertEquals(Sonic3kZoneIds.ZONE_HPZ, services.zone);
        assertEquals(1, services.act);
        assertTrue(services.deactivate);
    }

    @Test
    void checkInMyRangeKeepsRightAndBottomEdgesExclusive() {
        RecordingServices services = new RecordingServices();
        S3kStartNewLevelObjectInstance object = objectAt(0x1000, 0x1000, services);

        object.update(0, new TestablePlayableSprite("sonic", (short) 0x1020, (short) 0x1000));
        object.update(1, new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x1100));

        assertFalse(services.deactivate);
    }

    private static S3kStartNewLevelObjectInstance objectAt(
            int x, int y, RecordingServices services) {
        var object = new S3kStartNewLevelObjectInstance(
                new ObjectSpawn(x, y, 0xB3, 0x2D, 0, false, 0));
        object.setServices(services);
        return object;
    }

    private static final class RecordingServices extends StubObjectServices {
        private int zone = -1;
        private int act = -1;
        private boolean deactivate;

        @Override
        public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
            this.zone = zone;
            this.act = act;
            this.deactivate = deactivateLevelNow;
        }
    }
}
