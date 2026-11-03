package com.openggf.game.rewind;

import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLiveRewindBudget {
    private final Object owner = new Object();

    @Test
    void explicitNewRunResetsAllowanceEvenWhenTheWorldObjectIsReused() {
        var budget = new LiveRewindBudget();
        budget.configure("checkpoint", "10");
        budget.observeMain(owner, "s2", 0, 0, 3);
        budget.spend(600);
        budget.startNewRun();
        budget.observeMain(owner, "s2", 0, 0, 3);
        assertEquals(600, budget.remainingFrames());
        budget.spend(60);
        budget.observeMain(owner, "s2", 0, 0, 3);
        assertEquals(540, budget.remainingFrames());
    }

    @Test
    void palBudgetUsesRegionSecondsRatherThanPresentationRate() {
        var budget = new LiveRewindBudget();
        budget.configure("act", "10", 50);
        budget.observeMain(owner, "s1", 0, 0, -1);
        assertEquals(500, budget.remainingFrames());
        budget.spend(50);
        assertEquals(450, budget.remainingFrames());
    }

    @Test
    void defaultAllowanceIsTenSecondsAndOnlyActualBackwardTicksSpendIt() {
        var budget = new LiveRewindBudget();
        budget.observeMain(owner, "s1", 0, 0, -1);
        assertEquals(600, budget.remainingFrames());
        budget.spend(180);
        assertEquals(420, budget.remainingFrames());
        budget.observeMain(owner, "s1", 0, 0, -1);
        assertEquals(420, budget.remainingFrames(), "ordinary forward play grants no refund");
        assertEquals(420, budget.permittedSteps(900));
        budget.spend(420);
        assertTrue(budget.empty());
        assertEquals(0, budget.permittedSteps(4));
    }

    @Test
    void checkpointRewindAndReplayNeverReawardTheSameRefill() {
        var budget = new LiveRewindBudget();
        budget.configure("checkpoint", "10");
        budget.observeMain(owner, "s2", 0, 0, -1);
        budget.spend(300);
        budget.observeMain(owner, "s2", 0, 0, 1);
        assertEquals(600, budget.remainingFrames());
        budget.spend(120);
        budget.observeMain(owner, "s2", 0, 0, -1);
        budget.observeMain(owner, "s2", 0, 0, 1);
        assertEquals(480, budget.remainingFrames());
        budget.observeMain(owner, "s2", 0, 0, 2);
        assertEquals(600, budget.remainingFrames());
    }

    @Test
    void clearingASavedCheckpointOnDeathDoesNotRefillWhenLifeRefillIsOff() {
        var budget = new LiveRewindBudget();
        budget.configure("checkpoint", "10");
        budget.observeMain(owner, "s2", 0, 0, 3);
        budget.spend(300);
        budget.lifeLost(false);
        budget.observeMain(owner, "s2", 0, 0, -1);
        assertEquals(300, budget.remainingFrames(), "act-start sentinel is not a new checkpoint");
        budget.observeMain(owner, "s2", 0, 0, 3);
        assertEquals(300, budget.remainingFrames(), "restoring the saved checkpoint is not a new arrival");
        budget.observeMain(owner, "s2", 0, 0, 4);
        assertEquals(600, budget.remainingFrames());
    }

    @Test
    void actAndZoneScopesHaveDistinctRefillBoundaries() {
        var budget = new LiveRewindBudget();
        budget.configure("zone", "10");
        budget.observeMain(owner, "s3k", 0, 0, -1);
        budget.spend(240);
        budget.observeMain(owner, "s3k", 0, 0, 1);
        budget.observeMain(owner, "s3k", 0, 1, -1);
        assertEquals(360, budget.remainingFrames());
        budget.observeMain(owner, "s3k", 1, 0, -1);
        assertEquals(600, budget.remainingFrames());
        budget.configure("act", "10");
        budget.observeMain(owner, "s3k", 1, 0, -1);
        budget.spend(240);
        budget.observeMain(owner, "s3k", 1, 0, 1);
        assertEquals(360, budget.remainingFrames());
        budget.observeMain(owner, "s3k", 1, 1, -1);
        assertEquals(600, budget.remainingFrames());
        budget.spend(60);
        budget.observeMain(owner, "s3k", 1, 0, -1);
        budget.observeMain(owner, "s3k", 1, 1, -1);
        assertEquals(540, budget.remainingFrames());
    }

    @Test
    void eachStageVisitHasItsOwnAllowanceAndPreservesTheMainStage() {
        var budget = new LiveRewindBudget();
        budget.observeMain(owner, "s3k", 0, 0, 1);
        budget.spend(420);
        Object provider = new Object();
        budget.enterMode(GameMode.BONUS_STAGE, provider);
        assertEquals(600, budget.remainingFrames());
        budget.spend(60);
        budget.enterMode(GameMode.BONUS_STAGE, provider);
        assertEquals(540, budget.remainingFrames(), "repeated bindings are one visit");
        budget.enterMode(GameMode.LEVEL, null);
        assertEquals(180, budget.remainingFrames());
        budget.enterMode(GameMode.SPECIAL_STAGE, provider);
        assertEquals(600, budget.remainingFrames());
        budget.spend(500);
        budget.enterMode(GameMode.SPECIAL_STAGE_RESULTS, null);
        budget.enterMode(GameMode.LEVEL, null);
        assertEquals(180, budget.remainingFrames());
        budget.enterMode(GameMode.BONUS_STAGE, provider);
        assertEquals(600, budget.remainingFrames(), "the same provider can host a new visit");
    }

    @Test
    void lifeLossRefillIsOptionalAndDoesNotAffectTheSuspendedMainPool() {
        var budget = new LiveRewindBudget();
        budget.observeMain(owner, "s3k", 0, 0, -1);
        budget.spend(600);
        budget.lifeLost(false);
        assertTrue(budget.empty());
        budget.lifeLost(true);
        assertEquals(600, budget.remainingFrames());
        budget.spend(300);
        budget.enterMode(GameMode.BONUS_STAGE, new Object());
        budget.spend(400);
        budget.lifeLost(true);
        assertEquals(600, budget.remainingFrames());
        budget.enterMode(GameMode.LEVEL, null);
        assertEquals(300, budget.remainingFrames());
    }

    @Test
    void unlimitedIsIndependentOfRetainedHistoryAndFiniteSettingsRejectZero() {
        var budget = new LiveRewindBudget();
        budget.configure("act", "unlimited");
        assertTrue(budget.unlimited());
        assertFalse(budget.empty());
        assertEquals(1200, budget.permittedSteps(1200));
        budget.spend(1200);
        assertTrue(budget.unlimited());
        for (String bad : new String[]{"0", "-5", "garbage", "99999999999999999999999999"}) {
            assertEquals(600, LiveRewindBudget.framesForSeconds(bad));
        }
        assertTrue(LiveRewindBudget.framesForSeconds(Long.toString(Long.MAX_VALUE)) > 0);
    }

    @Test
    void newGameplayOwnerStartsAFreshAllowance() {
        var budget = new LiveRewindBudget();
        budget.observeMain(owner, "s1", 0, 0, -1);
        budget.spend(600);
        budget.observeMain(new Object(), "s1", 0, 0, -1);
        assertEquals(600, budget.remainingFrames());
    }

    @Test
    void changingTheMaximumPreservesSpendingRatherThanRefundingIt() {
        var budget = new LiveRewindBudget();
        budget.spend(300);
        budget.configure("act", "20");
        assertEquals(900, budget.remainingFrames());
        budget.configure("act", "1");
        assertTrue(budget.empty());
    }
}
