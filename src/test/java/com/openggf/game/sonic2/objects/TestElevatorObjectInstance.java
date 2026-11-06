package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void downTripFollowsTheRomCrossingTestAndKeepsTheAccumulatedFraction() throws Exception {
        // Subtype 7: start $1C above spawn. Under loc_2BAB6 (s2.asm:59058-59079)
        // the trip lands exactly on spawn once (bhs keeps +8 there), ends after
        // 86 passes, and its $8000-based y_sub fraction is $48 when the snap
        // rewrites only the y_pos word.
        ElevatorObjectInstance elevator = new ElevatorObjectInstance(
                new ObjectSpawn(0x0D30, 0x0588, Sonic2ObjectIds.CNZ_ELEVATOR, 0x07, 0, false, 0),
                "Elevator");
        assertEquals(0x0588 - 0x1C, elevator.getY());
        // Rider latched on the previous pass: routine 0 -> 2.
        elevator.onSolidContact(null, new com.openggf.level.objects.SolidContact(
                true, false, false, false, false), 0);
        elevator.update(0, null);
        int passes = 0;
        while (state(elevator) != 4 && passes < 200) {
            elevator.update(++passes, null);
        }
        assertEquals(4, state(elevator), "the down trip ends in routine 4");
        assertEquals(86, passes);
        assertEquals(0x0588 + 0x1C, elevator.getY(), "y_pos becomes spawn + offset");
        assertEquals(0x48, field(elevator, "ySub") & 0xFF, "y_sub survives the snap");
    }

    private static int state(ElevatorObjectInstance elevator) throws Exception {
        return field(elevator, "state");
    }

    private static int field(ElevatorObjectInstance elevator, String name) throws Exception {
        java.lang.reflect.Field f = ElevatorObjectInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(elevator);
    }

    @Test
    void cnzElevatorSkipsNewContactWhenPlayerAlreadyRidesAnotherObject() {
        ElevatorObjectInstance elevator = new ElevatorObjectInstance(
                new ObjectSpawn(0x20B0, 0x0398, Sonic2ObjectIds.CNZ_ELEVATOR, 0, 0, false, 0),
                "Elevator");

        assertTrue(elevator.skipsNewContactWhilePlayerAlreadyOnObject(null));
    }

}
