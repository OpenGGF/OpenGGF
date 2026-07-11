package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.SolidRoutineKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZ2WallObjectInstance {

    @Test
    void movingWallDeclaresNativeSolidObjectFull2VisibilityContract() {
        HCZ2WallObjectInstance wall = new HCZ2WallObjectInstance();

        assertEquals(SolidRoutineKind.FULL_SOLID, wall.getSolidRoutineProfile().kind());
        assertTrue(wall.getSolidRoutineProfile().bypassesOffscreenSolidGate(),
                "Obj_HCZ2Wall calls SolidObjectFull2, so its collision box remains live outside render bounds");
    }
}
