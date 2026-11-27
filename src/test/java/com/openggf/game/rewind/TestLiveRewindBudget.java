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

    /**
     * Scope Never: one allowance per run. Every refill trigger the other scopes use (a new
     * checkpoint, act and zone, and a life loss with refill on) is driven with time spent before
     * each; wiring any of them to refill the main pool breaks a remaining-frames assertion.
     */
    @Test
    void neverScopeSpendsOneAllowanceThatNoBoundaryOrLifeLossRefills() {
        var budget = new LiveRewindBudget();
        budget.configure("never", "10");
        assertFalse(budget.off(), "Never keeps rewind enabled");
        budget.observeMain(owner, "s3k", 0, 0, -1);
        assertEquals(600, budget.remainingFrames(), "the allowance starts full");
        budget.spend(100);
        budget.observeMain(owner, "s3k", 0, 0, 1);
        assertEquals(500, budget.remainingFrames(), "a new checkpoint does not refill");
        budget.spend(100);
        budget.observeMain(owner, "s3k", 0, 0, 2);
        budget.observeMain(owner, "s3k", 0, 1, -1);
        assertEquals(400, budget.remainingFrames(), "a new act does not refill");
        budget.spend(100);
        budget.observeMain(owner, "s3k", 1, 0, -1);
        assertEquals(300, budget.remainingFrames(), "a new zone does not refill");
        budget.spend(100);
        assertFalse(budget.lifeLost(true), "a life loss with refill on is ignored");
        budget.observeMain(owner, "s3k", 1, 0, -1);
        assertEquals(200, budget.remainingFrames(), "a life loss does not refill");
        assertEquals(-1, budget.takeRefillFrom(), "nothing was refilled to show");
        budget.spend(200);
        budget.observeMain(owner, "s3k", 2, 0, 3);
        budget.observeMain(owner, "s3k", 2, 1, -1);
        budget.lifeLost(true);
        assertTrue(budget.empty(), "a spent allowance stays spent");
        assertEquals(0, budget.permittedSteps(60));

        budget.startNewRun();
        budget.observeMain(owner, "s3k", 0, 0, -1);
        assertEquals(600, budget.remainingFrames(), "a new game starts a full allowance");
        budget.spend(60);
        budget.observeMain(new Object(), "s3k", 0, 0, -1);
        assertEquals(600, budget.remainingFrames(), "a new session (save load) starts a full allowance");
        assertEquals(-1, budget.takeRefillFrom(), "starting play is not a refill");
    }

    @Test
    void neverScopeKeepsFreshStageVisitPoolsWithoutRestoringTheMainAllowance() {
        var budget = new LiveRewindBudget();
        budget.configure("never", "10");
        budget.observeMain(owner, "s3k", 0, 0, -1);
        budget.spend(420);
        budget.enterMode(GameMode.SPECIAL_STAGE, "stage");
        assertEquals(600, budget.remainingFrames(), "a stage visit has its own pool");
        budget.spend(300);
        assertFalse(budget.lifeLost(true));
        assertEquals(300, budget.remainingFrames(), "a life lost in a stage does not refill its pool either");
        budget.enterMode(GameMode.LEVEL, null);
        budget.observeMain(owner, "s3k", 0, 1, -1);
        assertEquals(180, budget.remainingFrames(), "the main allowance is unchanged by the visit");
    }

    @Test
    void neverScopeWithUnlimitedIsUnlimited() {
        var budget = new LiveRewindBudget();
        budget.configure("never", "unlimited");
        budget.observeMain(owner, "s1", 0, 0, -1);
        budget.spend(5000);
        assertTrue(budget.unlimited());
        assertFalse(budget.empty());
        assertEquals(5000, budget.permittedSteps(5000));
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

    @Test
    void boundaryAndLifeLossRefillsReportWhatWasLeftOnlyWhenTimeWasSpent() {
        var budget = new LiveRewindBudget();
        budget.configure("checkpoint", "10");
        budget.observeMain(owner, "s2", 0, 0, -1);
        assertEquals(-1, budget.takeRefillFrom(), "binding a session is not a refill");
        budget.observeMain(owner, "s2", 0, 0, 1);
        assertEquals(-1, budget.takeRefillFrom(), "a full pool restores nothing");
        budget.spend(150);
        budget.observeMain(owner, "s2", 0, 0, 2);
        assertEquals(450, budget.takeRefillFrom(), "a new checkpoint restores spent time");
        assertEquals(-1, budget.takeRefillFrom(), "reading consumes the event");
        budget.spend(30);
        budget.observeMain(owner, "s2", 0, 0, 2);
        assertEquals(-1, budget.takeRefillFrom(), "a reached checkpoint is not refilled again");
        budget.lifeLost(false);
        assertEquals(-1, budget.takeRefillFrom());
        budget.lifeLost(true);
        assertEquals(570, budget.takeRefillFrom());
    }

    @Test
    void actAndZoneBoundariesReportTheirRefills() {
        var act = new LiveRewindBudget();
        act.configure("act", "10");
        act.observeMain(owner, "s2", 0, 0, -1);
        act.spend(600);
        act.observeMain(owner, "s2", 0, 1, -1);
        assertEquals(0, act.takeRefillFrom(), "a drained pool fills from zero");
        var zone = new LiveRewindBudget();
        zone.configure("zone", "10");
        zone.observeMain(owner, "s2", 0, 0, -1);
        zone.spend(60);
        zone.observeMain(owner, "s2", 0, 1, -1);
        assertEquals(-1, zone.takeRefillFrom(), "a new act inside the zone is no boundary");
        zone.observeMain(owner, "s2", 1, 0, -1);
        assertEquals(540, zone.takeRefillFrom());
    }

    @Test
    void newRunsSessionsVisitsUnlimitedAndOffReportNoRefill() {
        var budget = new LiveRewindBudget();
        budget.configure("act", "10");
        budget.observeMain(owner, "s2", 0, 0, -1);
        budget.spend(60);
        budget.startNewRun();
        budget.observeMain(owner, "s2", 0, 0, -1);
        assertEquals(-1, budget.takeRefillFrom(), "a new run is a fresh allowance, not a refill");
        budget.spend(60);
        budget.observeMain(new Object(), "s2", 0, 0, -1);
        assertEquals(-1, budget.takeRefillFrom(), "a new session owner starts fresh");
        budget.enterMode(GameMode.SPECIAL_STAGE, "stage");
        assertEquals(-1, budget.takeRefillFrom(), "a visit's fresh pool is not a refill");
        budget.spend(30);
        budget.lifeLost(true);
        assertEquals(570, budget.takeRefillFrom(), "a life lost in a stage refills the visit pool");

        var unlimited = new LiveRewindBudget();
        unlimited.configure("act", "unlimited");
        unlimited.observeMain(owner, "s2", 0, 0, -1);
        unlimited.spend(60);
        unlimited.lifeLost(true);
        unlimited.observeMain(owner, "s2", 0, 1, -1);
        assertEquals(-1, unlimited.takeRefillFrom());

        var off = new LiveRewindBudget();
        off.configure("off", "10");
        off.spend(60);
        off.lifeLost(true);
        assertEquals(-1, off.takeRefillFrom());
    }
}
