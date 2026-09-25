package com.openggf.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelRingDisplay {
    @Test void silentWriteRetainsDigitsUntilAnOrdinaryMutationRequestsRedraw() {
        var state = new LevelGamestate();
        LevelRingDisplay.publish(state);
        LevelRingDisplay.writeWithoutRefresh(state, 50);
        LevelRingDisplay.publish(state);
        assertEquals(50, state.getRings());
        assertEquals(0, LevelRingDisplay.value(state));
        state.addRings(-1);
        assertEquals(0, LevelRingDisplay.value(state), "digits wait for publication");
        LevelRingDisplay.publish(state);
        assertEquals(49, LevelRingDisplay.value(state));
    }

    @Test void silentWritePreservesAnAlreadyPendingRedrawAndLossRequestsZero() {
        var state = new LevelGamestate();
        LevelRingDisplay.publish(state);
        state.setRings(5);
        LevelRingDisplay.writeWithoutRefresh(state, 55);
        LevelRingDisplay.publish(state);
        assertEquals(55, LevelRingDisplay.value(state), "native dirty flag reads the current count");
        state.resetRingsForLoss();
        assertEquals(55, LevelRingDisplay.value(state));
        LevelRingDisplay.publish(state);
        assertEquals(0, LevelRingDisplay.value(state));
    }

    @Test void nonRetainedHudKeepsExistingLiveCounterBehavior() {
        var state = new LevelGamestate();
        state.setRings(35);
        assertEquals(35, LevelRingDisplay.value(state));
        state.addRings(-1);
        assertEquals(34, LevelRingDisplay.value(state));
    }

    @Test void restoredPendingRedrawKeepsItsDisplayedAndLiveCountsDistinct() {
        var state = new LevelGamestate();
        LevelRingDisplay.publish(state);
        LevelRingDisplay.writeWithoutRefresh(state, 50);
        var saved = state.captureRingDisplay();
        state.addRings(-1);
        LevelRingDisplay.publish(state);
        state.setRings(50); // level adapter restores gameplay before the display adapter
        state.restoreRingDisplay(saved);
        LevelRingDisplay.publish(state);
        assertEquals(0, LevelRingDisplay.value(state));
        state.addRings(-1);
        LevelRingDisplay.publish(state);
        assertEquals(49, LevelRingDisplay.value(state));
    }
}
