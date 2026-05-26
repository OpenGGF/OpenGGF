package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInvisibleBlockObjectInstance {

    @Test
    void subtypeDecodesSolidObjectAlwaysDimensions() {
        InvisibleBlockObjectInstance block = new InvisibleBlockObjectInstance(
                new ObjectSpawn(0x1560, 0x02A0, Sonic2ObjectIds.INVISIBLE_BLOCK, 0x33, 0, false, 0),
                "InvisibleBlock");

        SolidObjectParams params = block.getSolidParams();

        assertEquals(0x20 + 0x0B, params.halfWidth());
        assertEquals(0x20, params.airHalfHeight());
        assertEquals(0x21, params.groundHalfHeight());
    }

    @Test
    void solidObjectAlwaysBypassesOffscreenGate() {
        InvisibleBlockObjectInstance block = new InvisibleBlockObjectInstance(
                new ObjectSpawn(0x1560, 0x02A0, Sonic2ObjectIds.INVISIBLE_BLOCK, 0x33, 0, false, 0),
                "InvisibleBlock");

        assertTrue(block.bypassesOffscreenSolidGate(),
                "Obj74 calls SolidObject_Always, which resolves even when offscreen");
    }
}
