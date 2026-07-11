package com.openggf.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLevelTransitionCoordinator {

    @Test
    void inLevelTitleCardCarriesDisplayResetIntentAndDispatchOffset() {
        LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();

        transitions.requestInLevelTitleCard(1, 1, true, 7, true, 5);

        assertTrue(transitions.consumeInLevelTitleCardRequest());
        assertTrue(transitions.consumeInLevelTitleCardLevelGamestateResetRequest());
        assertEquals(7, transitions.consumeInLevelTitleCardResetAdditionalDispatches());
        assertTrue(transitions.consumeInLevelTitleCardPlayerControlLockRequest());
        assertEquals(5, transitions.consumeInLevelTitleCardExitAdditionalDispatches());
        assertFalse(transitions.consumeInLevelTitleCardRequest());
        assertFalse(transitions.consumeInLevelTitleCardLevelGamestateResetRequest());
        assertEquals(0, transitions.consumeInLevelTitleCardResetAdditionalDispatches());
        assertFalse(transitions.consumeInLevelTitleCardPlayerControlLockRequest());
        assertEquals(0, transitions.consumeInLevelTitleCardExitAdditionalDispatches());
    }
}
