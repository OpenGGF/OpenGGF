package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestElevatorObjectInstance {

    @Test
    void cnzElevatorUsesRomPlatformD3AsRideSurfaceOffset() {
        ElevatorObjectInstance elevator = new ElevatorObjectInstance(
                new ObjectSpawn(0x0D30, 0x0548, Sonic2ObjectIds.CNZ_ELEVATOR, 0, 0, false, 0),
                "Elevator");

        SolidObjectParams params = elevator.getSolidParams();

        assertEquals(0x10, params.halfWidth());
        assertEquals(9, params.airHalfHeight());
        assertEquals(9, params.groundHalfHeight());
    }
}
