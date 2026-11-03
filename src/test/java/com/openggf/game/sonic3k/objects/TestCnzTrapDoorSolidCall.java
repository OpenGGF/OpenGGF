package com.openggf.game.sonic3k.objects;

import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code loc_31CCA} calls {@code SolidObjectTop} once per frame after the trigger
 * checks, whatever the door's animation state (sonic3k.asm:67262-67273).
 */
class TestCnzTrapDoorSolidCall {

    @Test
    void doorResolvesItsTopEveryFrameWhileOpening() {
        CountingTrapDoor door = new CountingTrapDoor(
                new ObjectSpawn(0x2600, 0x0780, Sonic3kObjectIds.CNZ_TRAP_DOOR, 0, 0, false, 0));
        door.setServices(new TestObjectServices());
        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT, door.solidExecutionMode());

        Sonic below = new Sonic("sonic", (short) 0x2600, (short) 0x0788);
        below.setCentreX((short) 0x2600);
        below.setCentreY((short) 0x0788);
        for (int frame = 0; frame < 20; frame++) {
            door.update(frame, below);
        }

        assertTrue(door.isOpenForTest(), "the player under the hinge opened the door");
        assertEquals(20, door.calls, "SolidObjectTop runs on every frame, open or closed");
    }

    private static final class CountingTrapDoor extends CnzTrapDoorInstance {
        int calls;

        CountingTrapDoor(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            calls++;
            return new SolidCheckpointBatch(this, new IdentityHashMap<>());
        }
    }
}
