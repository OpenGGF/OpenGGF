package com.openggf.level;

import com.openggf.game.BonusStageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLevelTransitionCoordinatorPeeks {

    @Test
    void bonusPeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertNull(c.peekBonusStageRequest());
        c.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest());
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest()); // still pending
        assertEquals(BonusStageType.GUMBALL, c.consumeBonusStageRequest()); // consumer still works
        assertNull(c.peekBonusStageRequest()); // cleared by consume, not by peek
    }

    @Test
    void specialStagePeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertFalse(c.isSpecialStageRequested());
        c.requestSpecialStageFromCheckpoint();
        assertTrue(c.isSpecialStageRequested());
        assertTrue(c.isSpecialStageRequested()); // still pending
        assertTrue(c.consumeSpecialStageRequest());
        assertFalse(c.isSpecialStageRequested());
    }
}
