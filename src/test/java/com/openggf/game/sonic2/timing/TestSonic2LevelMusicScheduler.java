package com.openggf.game.sonic2.timing;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2LevelMusicScheduler {
    @Test
    void releasesOnlyOnTheDerivedTerminalVblank() {
        Sonic2LevelMusicScheduler scheduler = new Sonic2LevelMusicScheduler();
        scheduler.arm(0x81, 11);

        for (int i = 0; i < 10; i++) {
            assertTrue(scheduler.serviceVBlank().isEmpty());
        }
        assertEquals(OptionalInt.of(0x81), scheduler.serviceVBlank());
        assertTrue(scheduler.serviceVBlank().isEmpty());
    }

    @Test
    void rewindAndResetPreserveThePendingOwnerWithoutRepublishing() {
        Sonic2LevelMusicScheduler scheduler = new Sonic2LevelMusicScheduler();
        scheduler.arm(0x81, 11);
        scheduler.serviceVBlank();
        Sonic2LevelMusicScheduler.Snapshot snapshot = scheduler.capture();

        scheduler.cancel();
        assertFalse(scheduler.pending());
        scheduler.restore(snapshot);
        assertTrue(scheduler.pending());
        for (int i = 0; i < 9; i++) {
            assertTrue(scheduler.serviceVBlank().isEmpty());
        }
        assertEquals(OptionalInt.of(0x81), scheduler.serviceVBlank());

        scheduler.arm(0x82, 11);
        scheduler.resetForMissingSnapshot();
        assertFalse(scheduler.pending());
    }
}
