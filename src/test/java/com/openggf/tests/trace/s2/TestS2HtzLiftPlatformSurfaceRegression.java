package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.objects.HTZLiftObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS2HtzLiftPlatformSurfaceRegression {

    @Test
    void htzLiftPlatformSurfaceMatchesObj16PlatformObjectHeight() {
        int objectY = 0x03BE;
        HTZLiftObjectInstance lift = new HTZLiftObjectInstance(
                new ObjectSpawn(0x01A0, objectY, 0x16, 0x14, 0, false, 0),
                "HTZLift");

        SolidObjectParams params = lift.getSolidParams();

        // S2 Obj16 passes d3 = -$28 before JmpTo3_PlatformObject
        // (docs/s2disasm/s2.asm:47384-47388). Platform riding and landing
        // therefore use surfaceY = y_pos - d3 = y_pos + $28.
        assertEquals(objectY + 0x28, objectY + params.offsetY() - params.groundHalfHeight());
    }
}
