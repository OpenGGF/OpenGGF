package com.openggf.trace.replay.runs;

import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRunLevelLoadTracker {

    @Test
    void emitsProductionCauseAndGenerationOnlyForAChangedLoadedLevel() {
        LevelManager levels = mock(LevelManager.class);
        Level first = mock(Level.class);
        Level second = mock(Level.class);
        when(levels.getCurrentLevel()).thenReturn(first, first, second, second);
        when(levels.getCurrentZone()).thenReturn(3);
        when(levels.getRomZoneId()).thenReturn(5);
        when(levels.getCurrentAct()).thenReturn(1);
        RunLevelLoadTracker tracker = new RunLevelLoadTracker();

        tracker.prime(levels.getCurrentLevel());
        tracker.markNext(RunLevelLoadCause.DEATH_RESTART);
        assertTrue(tracker.observeLoaded(levels).isEmpty());
        RunLevelLoadTracker.Receipt receipt =
                tracker.observeLoaded(levels).orElseThrow();

        assertEquals(RunLevelLoadCause.DEATH_RESTART, receipt.cause());
        assertEquals(1, receipt.identity().loadGeneration());
        assertEquals(3, receipt.identity().progressionZone());
        assertEquals(5, receipt.identity().romZone());
        assertEquals(1, receipt.identity().act());
        assertTrue(tracker.observeLoaded(levels).isEmpty());
    }
}
