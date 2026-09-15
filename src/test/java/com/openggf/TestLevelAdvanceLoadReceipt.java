package com.openggf;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestLevelAdvanceLoadReceipt {
    @Test
    void resultsStyleDirectAdvancePublishesItsCauseAndConsumesItOnce() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var level = GameServices.level();
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        tracker.prime(level);
        long generation = level.getCompletedProductionLoadGeneration();

        // DefaultObjectServices calls this owner directly from the results fade.
        level.advanceToNextLevel();

        var receipt = tracker.latest().orElseThrow();
        assertEquals(RunLevelLoadCause.LEVEL_ADVANCE, receipt.cause());
        assertEquals(generation + 1, receipt.identity().loadGeneration());
        assertEquals(0, receipt.identity().progressionZone());
        assertEquals(1, receipt.identity().act());

        assertTrue(level.consumeTitleCardRequest(),
                "Results re-enters Level's locked title-card loop even headless");
        assertFalse(level.consumeTitleCardRequest(), "The title owner is handed off once");

        level.loadCurrentLevel();
        assertFalse(level.isTitleCardRequested(),
                "An unrelated standalone headless load still omits presentation");
        var ordinary = tracker.latest().orElseThrow();
        assertEquals(RunLevelLoadCause.ORDINARY, ordinary.cause());
        assertEquals(generation + 2, ordinary.identity().loadGeneration());
    }

    @Test
    void timeAttackMenuReturnDoesNotClassifyAnUnrelatedLaterLoad() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var level = GameServices.level();
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        tracker.prime(level);
        GameServices.gameState().setTimeAttackActive(true);
        try {
            level.advanceToNextLevel();
            assertTrue(level.consumeTimeAttackMenuReturnRequest());
            assertTrue(tracker.latest().isEmpty());
        } finally {
            GameServices.gameState().setTimeAttackActive(false);
        }
        level.loadCurrentLevel();
        assertEquals(RunLevelLoadCause.ORDINARY, tracker.latest().orElseThrow().cause());
        assertEquals(0, level.getCurrentAct());
    }
}
