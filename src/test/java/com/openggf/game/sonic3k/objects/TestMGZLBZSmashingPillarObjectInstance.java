package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMGZLBZSmashingPillarObjectInstance {

    @Test
    void groundedSquashEdgeEscapeSetsNativePushBit() {
        MGZLBZSmashingPillarObjectInstance pillar =
                new MGZLBZSmashingPillarObjectInstance(
                        new ObjectSpawn(0x04C4, 0x0700, 0x20, 0x0C, 0, false, 0));

        assertTrue(pillar.groundedSquashEdgeSideContactSetsPush(),
                "SolidObjectFull rejoins its push-setting side path after a grounded edge escape");
    }
}
