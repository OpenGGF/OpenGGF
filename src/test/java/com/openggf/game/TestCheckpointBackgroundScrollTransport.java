package com.openggf.game;

import com.openggf.game.internal.CheckpointBackgroundScroll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointBackgroundScrollTransport {
    @Test
    void sixWordsSurviveLoadSnapshotAndClearWithAnInactiveCheckpoint() {
        CheckpointState state = new CheckpointState();
        state.restoreFromSaved(100, 200, 3, 4, 1);
        CheckpointBackgroundScroll bank = new CheckpointBackgroundScroll(0x123, 0x234, 0x345, 0x456, 0x567, 0x678);
        CheckpointBackgroundScrollAccess.save(state, bank);
        LevelLoadContext load = new LevelLoadContext();
        load.snapshotCheckpoint(state);
        state.clear();
        assertNull(CheckpointBackgroundScrollAccess.saved(state));
        assertEquals(bank, CheckpointBackgroundScrollAccess.saved(load));
        state.restoreFromSaved(100, 200, 3, 4, 1);
        CheckpointBackgroundScrollAccess.restore(load, state);
        assertEquals(bank, CheckpointBackgroundScrollAccess.saved(state));
        state.clear();
        load.snapshotCheckpoint(state);
        assertNull(CheckpointBackgroundScrollAccess.saved(load));
    }
}
