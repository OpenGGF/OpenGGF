package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMGZMovingSpikePlatformObjectInstance {

    @BeforeEach
    void resetOscillation() {
        OscillationManager.reset();
    }

    @Test
    void updateReadsGlobalOscillationWithoutAdvancingIt() {
        OscillationManager.update(12);
        byte[] beforeObjectSlot = OscillationManager.snapshotRomFormatBytes();
        MGZMovingSpikePlatformObjectInstance platform =
                new MGZMovingSpikePlatformObjectInstance(
                        new ObjectSpawn(0x2000, 0x0600, 0x56, 0, 0, false, 0));

        platform.update(99, null);

        assertArrayEquals(beforeObjectSlot, OscillationManager.snapshotRomFormatBytes(),
                "Obj_MGZMovingSpikePlatform only reads Oscillating_table; "
                        + "OscillateNumDo runs once at the LevelLoop tail");
        assertEquals(0x0600 + OscillationManager.getByte(0x10), platform.getY(),
                "The platform still applies the current Oscillating_table+$12 byte");
    }
}
