package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizCollapsingLogBridgeObjectInstance {

    @Test
    void bothBridgeSubtypesRejectTheExactTopSolidBoundary() {
        AizCollapsingLogBridgeObjectInstance normal = bridge(0x08);
        AizCollapsingLogBridgeObjectInstance fire = bridge(0x88);

        assertTrue(normal.rejectsZeroDistanceTopSolidLanding(null));
        assertTrue(fire.rejectsZeroDistanceTopSolidLanding(null));
    }

    private static AizCollapsingLogBridgeObjectInstance bridge(int subtype) {
        return new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0x1AE5, 0x04F8, 0x2C, subtype, 0, false, 0));
    }
}
