package com.openggf.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLevelTransitionCoordinator {

    @Test
    void inLevelTitleCardCarriesDisplayResetIntentAndDispatchOffset() {
        LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();

        transitions.requestInLevelTitleCard(1, 1, true, 7);

        assertTrue(transitions.consumeInLevelTitleCardRequest());
        assertTrue(transitions.consumeInLevelTitleCardLevelGamestateResetRequest());
        assertEquals(7, transitions.consumeInLevelTitleCardResetAdditionalDispatches());
        assertFalse(transitions.consumeInLevelTitleCardRequest());
        assertFalse(transitions.consumeInLevelTitleCardLevelGamestateResetRequest());
        assertEquals(0, transitions.consumeInLevelTitleCardResetAdditionalDispatches());
    }
}
