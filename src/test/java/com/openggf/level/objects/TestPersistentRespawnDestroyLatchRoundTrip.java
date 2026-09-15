package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3K's permanent-destroy latch must survive a special-stage round trip, and
 * must be back in place before the return load's first window scan.
 *
 * <p>ROM: the giant-ring entry sets {@code Respawn_table_keep = 1}
 * (docs/skdisasm/sonic3k.asm:128409-128412), so the reload skips the
 * {@code Object_respawn_table} wipe at :37429-37438 and the table is already
 * populated when {@code Load_Sprites} scans the entry window (:37741-37766).
 * An object deleted through {@code Delete_Current_Sprite} rather than
 * {@code Go_Delete_SpriteSlotted} (:179056-179061) keeps its bit 7 set, so it
 * must not be re-created on return.
 */
class TestPersistentRespawnDestroyLatchRoundTrip {

    @Test
    void destroyLatchSurvivesRoundTripAndSuppressesEntryWindowRespawn() {
        ObjectSpawn destroyed = spawn(0x0100, 0);
        ObjectSpawn intact = spawn(0x0110, 1);

        ObjectPlacementController entry = controller(destroyed, intact);
        entry.reset(0x0000);
        entry.removeFromActive(destroyed);
        PersistentRespawnState carried = entry.capturePersistentRespawn();

        ObjectPlacementController returned = controller(destroyed, intact);
        List<Integer> createdOnEntryWindow = new ArrayList<>();
        returned.reset(0x0000, carried);
        assertTrue(returned.getActiveSpawns().stream().noneMatch(s -> s.x() == 0x0100),
                "the entry-window scan inside reset() runs after the carried table is "
                        + "restored, so a latched spawn must never enter the active set");
        returned.updateAndLoad(0x0000, (spawn, counter) -> {
            createdOnEntryWindow.add(spawn.x());
            return true;
        });

        assertTrue(returned.isDestroyedInWindow(returned.getSpawnIndex(destroyed)),
                "the carried Object_respawn_table bit 7 must be restored");
        assertEquals(List.of(), createdOnEntryWindow.stream().filter(x -> x == 0x0100).toList(),
                "a spawn still latched in the ROM's kept respawn table must not be "
                        + "re-created by the return load's entry-window scan");
    }

    @Test
    void lowerLayoutBitsSurviveReturnBeyondCounterByteRangeAndRingCapture() {
        List<ObjectSpawn> layout = new ArrayList<>();
        for (int i = 0; i < 300; i++) layout.add(spawn(i * 16, i));
        var entry = new ObjectPlacementController(layout, () -> 320);
        entry.setTwoAxisCursorPlacement(true);
        entry.reset(0);
        entry.setCounterStateBit(layout.get(280), 0);
        entry.setCounterStateBit(layout.get(280), 6);
        entry.setCounterStateBit(layout.get(24), 2);
        var carried = entry.capturePersistentRespawn().withRingStatusBits(new long[]{3});
        byte[] detached = carried.objectStateBits();
        detached[280] = 0;
        assertEquals(0x41, carried.objectStateBits()[280]);

        var returned = new ObjectPlacementController(layout, () -> 320);
        returned.setTwoAxisCursorPlacement(true);
        returned.reset(280 * 16 - 160, carried);
        assertTrue(returned.isCounterStateBitSet(layout.get(280), 0));
        assertTrue(returned.isCounterStateBitSet(layout.get(280), 6));
        assertTrue(returned.isCounterStateBitSet(layout.get(24), 2));
        assertEquals(3, carried.ringStatusBits()[0]);
        // A normal load still clears the table; only Respawn_table_keep carries it.
        returned.reset(0);
        org.junit.jupiter.api.Assertions.assertFalse(
                returned.isCounterStateBitSet(layout.get(280), 0));
    }

    private static ObjectPlacementController controller(ObjectSpawn... spawns) {
        ObjectPlacementController placement =
                new ObjectPlacementController(List.of(spawns), () -> 320);
        placement.enablePermanentDestroyLatch();
        return placement;
    }

    private static ObjectSpawn spawn(int x, int layoutIndex) {
        return new ObjectSpawn(x, 0x0100, 0x05, 0, 0, false, layoutIndex);
    }
}
