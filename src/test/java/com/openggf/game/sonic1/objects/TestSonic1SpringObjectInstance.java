package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1SpringObjectInstance {
    @Test
    void exposesFullSolidRoutineProfileForVerticalAndHorizontalSprings() {
        Sonic1SpringObjectInstance vertical = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x00, 0, false, 0));
        Sonic1SpringObjectInstance horizontal = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x10, 0, false, 0));

        SolidRoutineProfile verticalProfile = vertical.getSolidRoutineProfile();
        SolidRoutineProfile horizontalProfile = horizontal.getSolidRoutineProfile();

        assertEquals(SolidRoutineKind.FULL_SOLID, verticalProfile.kind());
        assertFalse(verticalProfile.inclusiveRightEdge());
        assertFalse(verticalProfile.bypassesOffscreenSolidGate());
        assertTrue(verticalProfile.stickyContactBuffer());
        assertEquals(SolidRoutineKind.FULL_SOLID, horizontalProfile.kind());
        assertFalse(horizontalProfile.inclusiveRightEdge());
        assertFalse(horizontalProfile.bypassesOffscreenSolidGate());
        assertTrue(horizontalProfile.stickyContactBuffer());
    }
}
