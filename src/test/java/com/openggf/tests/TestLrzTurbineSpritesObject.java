package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.LrzTurbineSpritesObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLrzTurbineSpritesObject {
    private static LrzTurbineSpritesObjectInstance turbine(int subtype) {
        return new LrzTurbineSpritesObjectInstance(new ObjectSpawn(
                0x2000, 0x0400, Sonic3kObjectIds.AIZ_DRAW_BRIDGE, subtype, 0, false, 0));
    }

    @Test
    void subtypeZeroBuildsTheWideCaptureTurbine() {
        var object = turbine(0);
        assertFalse(object.isThinVariant());
        assertEquals(0, object.getCollisionFlags());
        assertEquals(0x10, object.getOnScreenHalfWidth());
        assertEquals(0x30, object.getOnScreenHalfHeight());
        assertEquals(0x0004, object.romObjectCodePointerHighWord());
    }

    @Test
    void nonzeroSubtypeBuildsTheThinTouchVariant() {
        var object = turbine(1);
        assertTrue(object.isThinVariant());
        assertEquals(0xA0, object.getCollisionFlags());
        assertEquals(4, object.getOnScreenHalfWidth());
        object.update(7, null);
        assertEquals(3, object.mappingFrame());
    }
}
