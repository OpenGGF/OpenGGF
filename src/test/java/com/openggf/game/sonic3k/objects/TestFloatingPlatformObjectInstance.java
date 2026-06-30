package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFloatingPlatformObjectInstance {

    @Test
    void squarePlatformOutOfRangeUsesSavedAnchorInsteadOfLivePosition() {
        FloatingPlatformObjectInstance platform = ObjectConstructionContext.construct(
                new StubObjectServices(),
                () -> new FloatingPlatformObjectInstance(
                        new ObjectSpawn(0x0888, 0x0B00, 0x51, 0x08, 0, false, 0)));

        assertEquals(0x0888, platform.getOutOfRangeReferenceX(),
                "ROM loc_25628 tests Obj_FloatingPlatform $44(a0), which is saved from x_pos at init "
                        + "before square-path movement (sonic3k.asm:50810-50835)");
        assertFalse(platform.usesCustomOutOfRangeCheck(),
                "Subtype 8 keeps ROM's normal $280 deletion range and only needs the saved anchor");
    }

    @Test
    void horizontalSweepUsesRomWidenedDeleteRange() {
        FloatingPlatformObjectInstance platform = ObjectConstructionContext.construct(
                new StubObjectServices(),
                () -> new FloatingPlatformObjectInstance(
                        new ObjectSpawn(0x0888, 0x0B00, 0x51, 0x0C, 0, false, 0)));

        assertEquals(0x0988, platform.getOutOfRangeReferenceX(),
                "ROM type >= 12 adds $100 to $44(a0) for the loc_25628 delete check "
                        + "(sonic3k.asm:50810-50835)");
        assertTrue(platform.usesCustomOutOfRangeCheck());
        assertFalse(platform.isCustomOutOfRange(0x0800),
                "The widened $380 range keeps the sweep platform alive where the shared $280 check would delete it");
        assertTrue(platform.isCustomOutOfRange(0x0500));
    }
}
