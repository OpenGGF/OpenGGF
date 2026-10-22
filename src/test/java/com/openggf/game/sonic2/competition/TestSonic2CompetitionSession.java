package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionSession {
    private static LogicalInputSnapshot press(int slot, int directions, boolean start) {
        var input = PlayerInputState.of(directions, directions, 0, 0, start, start);
        return LogicalInputSnapshot.ofPlayers(slot == 0 ? input : PlayerInputState.neutral(),
                slot == 1 ? input : PlayerInputState.neutral());
    }

    @Test void eitherControllerTogglesGridBitsAndOnlyStartLaunches() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(press(1, 2, false), true);
        assertEquals(2, session.selection());
        session.updateMenu(press(0, 8, false), true);
        assertEquals(3, session.selection());
        session.updateMenu(press(1, 1, false), true);
        assertEquals(1, session.selection());
        session.updateMenu(press(0, 4, false), true);
        assertEquals(0, session.selection());
        session.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        assertEquals(0, session.romZone());
        assertEquals(0, session.act());
    }

    @Test void unsupportedHostCannotLaunchAnySelection() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(press(1, 0, true), false);
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
    }

    @Test void goalWindowUsesUnsignedCentreAndStopsOnlyThatPlayersTimer() {
        var session = started();
        tick(session);
        assertFalse(session.observeGoal(0, 0xFFF, 0x1000));
        assertFalse(session.observeGoal(0, 0x1020, 0x1000));
        assertTrue(session.observeGoal(1, 0x1000, 0x1000));
        assertEquals(1, session.firstFinisher());
        assertEquals(4, session.signpostFace());
        assertFalse(session.levelState(0).isTimerPaused());
        assertTrue(session.levelState(1).isTimerPaused());
        assertEquals(0x3C3C, session.loserTimeLeft());
        tick(session);
        assertEquals(2, session.levelState(0).getTimerFrames());
        assertEquals(1, session.levelState(1).getTimerFrames());
        assertTrue(session.observeGoal(0, 0x101F, 0x1000));
        assertEquals(1, session.firstFinisher());
        session.completeSignpostSpin();
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
    }

    @Test void bothFinishOrdersAndSpinGateAreIndependentOfCategoryWinners() {
        for (int first = 0; first < 2; first++) {
            var session = started();
            session.playerState(1).addRings(10);
            session.observeGoal(first, 100, 100);
            session.completeSignpostSpin();
            assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
            session.observeGoal(1 - first, 100, 100);
            assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
            session.completeSignpostSpin();
            assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
            assertEquals(new Sonic2CompetitionResults.Wins(0, 2), session.actResult(0, 0));
        }
    }

    @Test void fiveCategoriesTieAwardsNeitherAndTimeRewardsLowerValue() {
        var p1 = new Sonic2CompetitionResults.Metrics(100, 120, 8, 20, 3);
        var p2 = new Sonic2CompetitionResults.Metrics(100, 180, 9, 20, 1);
        assertEquals(new Sonic2CompetitionResults.Wins(2, 1),
                Sonic2CompetitionResults.compare(p1, p2));
        assertEquals(new Sonic2CompetitionResults.Wins(0, 0),
                Sonic2CompetitionResults.compare(p1, p1));
    }

    @Test void actTwoPreservesLivesAndLedgerButResetsScoreStarsAndThresholds() {
        var session = started();
        session.gameState(0).addScore(50000);
        session.playerState(0).saveCheckpoint(3, 900, 600, 200, 400);
        int lives = session.gameState(0).getLives();
        finish(session);
        var result = session.actResult(0, 0);
        session.updateMenu(press(1, 0, true), false);
        assertEquals(0, session.act()); // Start must wait for native PLC completion.
        session.updateMenu(press(1, 0, true), true);
        assertEquals(1, session.act());
        assertEquals(lives, session.gameState(0).getLives());
        assertEquals(0, session.gameState(0).getScore());
        assertEquals(5000, session.playerState(0).nextExtraLifeScore());
        assertEquals(0, session.playerState(0).checkpoint().index());
        assertEquals(result, session.actResult(0, 0));
    }

    @Test void zoneCountsActWinsRatherThanRawCategoryTotalsAndUsesNativeTiebreaker() {
        var session = started();
        session.gameState(0).addScore(10);
        finish(session);
        session.updateMenu(press(0, 0, true), true);
        session.playerState(1).addRings(20);
        session.playerState(1).brokenMonitor();
        finish(session);
        session.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE, session.phase());
        assertEquals(1, session.specialStageIndex());
        session.recordSpecialSector(10, 5);
        session.recordSpecialSector(10, 10);
        session.recordSpecialSector(0, 20);
        assertEquals(new Sonic2CompetitionResults.Wins(1, 1), session.actResult(0, 2));
        session.updateMenu(press(0, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, session.phase());
        session.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
        assertTrue(session.completed(0));
        session.updateMenu(press(0, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
    }

    @Test void standaloneSpecialAlwaysPlaysFourAndFiveThenSixOnlyForTiedStageWins() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(press(0, 10, false), true);
        session.updateMenu(press(1, 0, true), true);
        assertEquals(4, session.specialStageIndex());
        special(session, 1, 0);
        session.updateMenu(press(0, 0, true), true);
        assertEquals(5, session.specialStageIndex());
        special(session, 0, 9);
        session.updateMenu(press(1, 0, true), true);
        assertEquals(6, session.specialStageIndex());
        special(session, 10, 0);
        session.updateMenu(press(0, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ZONE_RESULTS, session.phase());
        session.updateMenu(press(0, 0, true), true);
        assertTrue(session.completed(3));
    }

    @Test void specialSectorsNeverUseOrdinarySummedGateAndResetBothRingBanks() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(press(0, 10, false), true);
        session.updateMenu(press(0, 0, true), true);
        session.playerState(0).addRings(30);
        session.playerState(1).addRings(1);
        session.finishSpecialSector();
        assertEquals(0, session.levelState(0).getRings());
        assertEquals(0, session.levelState(1).getRings());
        assertEquals(30, session.specialRings().get(0));
        assertEquals(1, session.specialRings().get(1));
        session.recordSpecialSector(0, 20);
        session.recordSpecialSector(0, 20);
        assertEquals(new Sonic2CompetitionResults.Wins(1, 2), session.actResult(3, 0));
    }

    @Test void snapshotRestoresProgressLedgerModesAndCanReplayForward() {
        var session = started();
        session.playerState(0).addRings(99);
        session.playerState(1).addRings(2);
        session.playerState(1).saveCheckpoint(4, 123, 456, 12, 34);
        var snapshot = session.capture();
        session.playerState(0).addRings(1);
        session.gameState(1).addScore(1000);
        finish(session);
        var expected = session.actResult(0, 0);
        session.restore(snapshot);
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        assertEquals(4, session.playerState(1).checkpoint().index());
        assertEquals(99, session.levelState(0).getRings());
        assertEquals(3, session.gameState(0).getLives());
        session.playerState(0).addRings(1);
        session.gameState(1).addScore(1000);
        finish(session);
        assertEquals(expected, session.actResult(0, 0));
        session.reset();
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
        assertEquals(3, session.gameState(0).getLives());
        assertFalse(session.completed(0));
    }

    @Test void monitorContentsUseInitializationClockNamedLivesAndFinishTeleportReplacement() {
        var session = started();
        assertEquals(1, session.monitorContents(0, false));
        assertEquals(8, session.monitorContents(7, false));
        assertEquals(8, session.monitorContents(0, true));
        session.awardMonitor(1, 1);
        assertEquals(4, session.gameState(0).getLives());
        assertEquals(3, session.gameState(1).getLives());
        assertEquals(1, session.playerState(0).monitorsBroken());
        assertEquals(0, session.playerState(1).monitorsBroken());
        session.observeGoal(1, 10, 10);
        assertEquals(7, session.monitorContents(7, false));
        assertEquals(7, session.monitorContents(0, true));
    }

    @Test void timeoutRetainsNativeSixtyBySixtyCountdownAndWaitsForDeathCard() {
        var session = started();
        session.observeGoal(0, 100, 100);
        session.completeSignpostSpin();
        for (int i = 0; i < 3659; i++) tick(session);
        assertFalse(session.timeOver(1));
        tick(session);
        assertTrue(session.timeOver(1));
        assertTrue(session.levelState(1).isTimeOver());
        assertEquals(2, session.consumeTimeOverRequests());
        assertEquals(0, session.consumeTimeOverRequests());
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        session.deathCardDismissed(1, true);
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
    }

    @Test void gameOverUsesNativeNamedLedgerSentinelAndResetsOnSelectionReturn() {
        var session = started();
        session.deathCardDismissed(0, false);
        assertEquals(new Sonic2CompetitionResults.Wins(254, 255), session.actResult(0, 0));
        assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, session.phase());
        session.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SELECT, session.phase());
        assertNull(session.actResult(0, 0));
        assertEquals(3, session.gameState(0).getLives());
    }

    @Test void ringCapsThresholdBanksAndMonitorExtraLifeCounterMatchRev01() {
        var session = started();
        session.playerState(1).addRings(95);
        session.awardMonitor(1, 4);
        assertEquals(105, session.levelState(1).getRings());
        assertEquals(4, session.gameState(1).getLives());
        assertEquals(2, session.playerState(1).monitorsBroken());
        session.playerState(1).addRings(1000);
        assertEquals(999, session.levelState(1).getRings());
        assertEquals(999, session.playerState(1).totalRings());
        assertEquals(5, session.gameState(1).getLives());
        session.levelState(1).resetRingsForLoss();
        session.playerState(1).addRings(100);
        assertEquals(6, session.gameState(1).getLives());
        assertEquals(999, session.playerState(1).totalRings());
    }

    @Test void scoreIngressUsesDisplayedPointsWithRawRomThresholdBank() {
        var session = started();
        session.gameState(1).addScore(49999);
        assertEquals(3, session.gameState(1).getLives());
        session.gameState(1).addScore(1);
        assertEquals(4, session.gameState(1).getLives());
        assertEquals(10000, session.playerState(1).nextExtraLifeScore());
        session.gameState(1).addScore(Integer.MAX_VALUE);
        assertEquals(9999990, session.gameState(1).getScore());
        assertEquals(5, session.gameState(1).getLives());
    }

    @Test void allFourZonesProduceGameResultByEightPlayedRowWinsAndFinalStartReturnsTitle() {
        var s = started();
        for (int zone = 0; zone < 3; zone++) {
            if (zone != 0) {
                int delta = s.selection() ^ zone;
                s.updateMenu(press(1, ((delta & 2) != 0 ? 2 : 0) | ((delta & 1) != 0 ? 8 : 0), true), true);
            }
            for (int act = 0; act < 2; act++) {
                s.gameState(0).addScore(100); finish(s);
                s.updateMenu(press(act & 1, 0, true), true);
            }
            assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, s.phase());
            assertEquals(new Sonic2CompetitionResults.Wins(2, 0), s.zoneWins(zone));
            s.updateMenu(press(0, 0, true), true);
        }
        s.updateMenu(press(1, 8, true), true);
        assertEquals(4, s.specialStageIndex());
        special(s, 5, 0); s.updateMenu(press(0, 0, true), true);
        assertEquals(5, s.specialStageIndex());
        special(s, 9, 0); s.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ZONE_RESULTS, s.phase());
        assertNull(s.actResult(3, 2));
        s.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.GAME_RESULTS, s.phase());
        assertEquals(new Sonic2CompetitionResults.Wins(8, 0), s.gameWins());
        s.updateMenu(press(1, 0, true), true);
        assertEquals(Sonic2CompetitionSession.Phase.FINISHED, s.phase());
    }

    @Test void canonicalTimerAndPolicyFlowDoNotDoubleTickAndNaturalP1TimeoutKillsActiveP2() {
        var s = started();
        s.levelState(0).update(); s.levelState(1).update();
        s.advanceActFlow();
        assertEquals(1, s.levelState(0).getTimerFrames());
        assertEquals(1, s.levelState(1).getTimerFrames());
        s.levelState(0).setTimerFrames(35999); tick(s);
        assertTrue(s.timeOver(0)); assertTrue(s.timeOver(1));
        assertEquals(3, s.consumeTimeOverRequests());
        assertTrue(s.levelState(0).isTimerPaused()); assertTrue(s.levelState(1).isTimerPaused());
    }

    @Test void nativeSignpostLoadArmsCountdownBeforeEitherPlayerHasFinished() {
        var s = started();
        s.initializeSignpost();
        tick(s);
        assertEquals(0x3C3B, s.loserTimeLeft());
        s.observeGoal(1, 100, 100);
        assertEquals(0x3C3C, s.loserTimeLeft());
    }

    @Test void independentRespawnClearsRingsAndFlagsPreservesRunningTimeAndOtherPlayer() {
        var s = started();
        s.levelState(1).setTimerFrames(120);
        s.playerState(1).saveCheckpoint(1, 100, 200, 10, 20);
        s.playerState(1).addRings(100);
        s.gameState(1).addScore(1000);
        tick(s);
        s.playerState(1).respawnFromCheckpoint();
        assertEquals(121, s.levelState(1).getTimerFrames());
        assertEquals(0, s.levelState(1).getRings());
        assertEquals(0, s.levelState(1).getRingExtraLifeFlags());
        assertEquals(1000, s.gameState(1).getScore());
        assertEquals(4, s.gameState(1).getLives());
        assertEquals(1, s.levelState(0).getTimerFrames());
    }

    @Test void actResetPreservesTheShippedOptionsEmeraldRetentionBug() {
        var s = new Sonic2CompetitionSession();
        s.gameState(0).restoreSaveProgress(5, 2, java.util.List.of(0, 1, 2, 3, 4, 5, 6), java.util.List.of());
        s.updateMenu(press(0, 0, true), true);
        assertEquals(7, s.gameState(0).getEmeraldCount());
        s.gameState(0).addScore(100);
        finish(s); s.updateMenu(press(1, 0, true), true);
        assertEquals(7, s.gameState(0).getEmeraldCount());
        assertEquals(5, s.gameState(0).getLives());
        assertEquals(2, s.gameState(0).getContinues());
        assertEquals(0, s.gameState(0).getScore());
    }

    private static Sonic2CompetitionSession started() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(press(0, 0, true), true);
        return session;
    }
    private static void tick(Sonic2CompetitionSession session) {
        session.levelState(0).update();
        session.levelState(1).update();
        session.advanceActFlow();
    }
    private static void finish(Sonic2CompetitionSession session) {
        session.observeGoal(0, 100, 100);
        session.observeGoal(1, 100, 100);
        session.completeSignpostSpin();
    }
    private static void special(Sonic2CompetitionSession session, int p1, int p2) {
        for (int i = 0; i < 3; i++) session.recordSpecialSector(p1, p2);
    }
}
