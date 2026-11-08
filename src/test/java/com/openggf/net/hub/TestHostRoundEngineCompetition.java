package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestHostRoundEngineCompetition {
    long now;
    List<ControlMessage> messages = new ArrayList<>();
    HostRoundEngine engine = new HostRoundEngine(() -> now, messages::add);
    @Test void confirmedWinnerReclassifiesRetainedOutOfWindowClaims() {
        start(); engine.setVerifiedRoom(true);
        engine.startAttempt("p0",1); engine.startAttempt("p1",1);
        assertNotNull(finish(0,100)); assertNotNull(finish(1,2000));
        engine.onVerdict(0,1,true); engine.onVerdict(1,1,true);
        assertEquals("DNF",engine.standings().stream().filter(r -> r.slot()==1).findFirst().orElseThrow().outcome());
    }
    @Test void fasterConfirmedWinnerRecomputesFrameWindow() {
        start(); engine.setVerifiedRoom(true);
        for(int i=0;i<3;i++) engine.startAttempt("p"+i,1);
        assertNotNull(finish(0,100)); assertNotNull(finish(1,1900));
        engine.onVerdict(0,1,true); engine.onVerdict(1,1,true);
        assertNotNull(finish(2,90)); engine.onVerdict(2,1,true);
        assertEquals(2,engine.standings().getFirst().slot());
        assertEquals("DNF",engine.standings().stream().filter(r -> r.slot()==1).findFirst().orElseThrow().outcome());
    }
    @Test void verifierLatencyDoesNotMoveCutoffFromFinishArrival() {
        start(); engine.setVerifiedRoom(true); engine.startAttempt("p0",1); assertNotNull(finish(0,100));
        now=15000; engine.onVerdict(0,1,true);
        assertTrue(messages.stream().anyMatch(m -> m instanceof ControlMessage.RaceCutoff c && c.endsAtHubMillis()==40000));
    }
    @Test void forgedClockPrefixIsRejectedEvenInsideLatencyAllowance() {
        start(); engine.startAttempt("p0",1);
        var f = new ControlMessage.AttemptFinish(1,5,335,340,List.of(1,1,1,1,1),List.of(),"ab".repeat(32),"cd".repeat(32),null);
        assertNull(engine.onAttemptFinish(0,"p0",null,"P","sonic",f,false));
        assertTrue(messages.stream().noneMatch(ControlMessage.RaceCutoff.class::isInstance));
        assertEquals(openggf.timeattack.MassStartRelease.ARENA_LEAD_FRAMES,
                com.openggf.game.internal.CompetitionTrackRules.clockStartFrame(new com.openggf.net.protocol.TrackKey("s3k","ARENA",0,0)));
    }
    @Test void forgedFiveFrameFinishNeverArmsCutoff() {
        start(); engine.startAttempt("p0",1); now = 30_000;
        var f = new ControlMessage.AttemptFinish(1,5,1235,1240,List.of(1,1,1,1,1),List.of(),"ab".repeat(32),"cd".repeat(32),null);
        assertNull(engine.onAttemptFinish(0,"p0",null,"P","sonic",f,false));
        assertTrue(messages.stream().noneMatch(ControlMessage.RaceCutoff.class::isInstance));
    }
    @Test void failedPendingWinnerDoesNotExcludeHonestFinish() {
        start(); engine.setVerifiedRoom(true);
        engine.startAttempt("p0",1); engine.startAttempt("p1",1);
        assertNotNull(finish(0,100)); assertNotNull(finish(1,2000));
        assertTrue(messages.stream().noneMatch(ControlMessage.RaceCutoff.class::isInstance));
        engine.onVerdict(0,1,false); engine.onVerdict(1,1,true);
        assertEquals("FINISHED",engine.standings().getFirst().outcome());
        assertEquals(2000,engine.standings().getFirst().bestTimeFrames());
    }
    @Test void configuredStartDelayIsUsedByHost() {
        now = 123;
        engine.startRound(new ControlMessage.RoundConfig("s3k",0,0,60,"OPEN",null,"ARENA","COMPETITION","ITEMS",60,15));
        assertEquals(15123, engine.snapshot().countdownEndsAtHubMillis());
        now = 15122; engine.onTick(); assertEquals(HostRoundEngine.Phase.COUNTDOWN, engine.phase());
        now++; engine.onTick(); assertEquals(HostRoundEngine.Phase.RUNNING, engine.phase());
    }
    @Test void taintIsProjectedIntoLiveAndBestLapStandings() {
        start(); engine.setSwapTaint(slot -> slot == 0);
        assertTrue(engine.startAttempt("p0",1)); assertNotNull(finish(0,100));
        assertTrue(engine.standings().getFirst().taintedSwap());
        assertTrue(engine.page(0,10,"BEST_LAP").getFirst().taintedSwap());
    }
    void start() {
        assertTrue(engine.startRound(new ControlMessage.RoundConfig("s3k", 0, 0, 600, "OPEN", null, "ARENA", "COMPETITION", "ITEMS", 30)));
        for (int i=0;i<4;i++) engine.register(i,"p"+i,"Player"+i,"sonic");
        now=10_000; engine.onTick();
    }
    ControlMessage.AttemptFinish finish(int total) {
        return new ControlMessage.AttemptFinish(1,total,240,240+total,List.of(1,2,3,4,total-10),List.of(241,245),"ab".repeat(32),"cd".repeat(32),null);
    }
    HostRoundEngine.FinishOutcome finish(int slot, int total) {
        return engine.onAttemptFinish(slot,"p"+slot,null,"Player"+slot,"sonic",finish(total),false);
    }
    @Test void competitionHardWindowDoesNotUseTimeAttackRetryWindow() {
        engine.startRound(new ControlMessage.RoundConfig("s2",0,0,30,"OPEN",null,"ACT","COMPETITION","NORMAL",60));
        assertEquals(640_000,engine.snapshot().deadlineHubMillis());
    }
    @Test void reconnectCannotCreateAnotherCompetitionAttempt() {
        start();
        assertTrue(engine.register(4,"original","Original","sonic","fingerprint"));
        assertTrue(engine.startAttempt("original",1));
        engine.onPlayerLeft(4);
        assertFalse(engine.register(4,"replacement","Replacement","sonic","fingerprint"));
        assertFalse(engine.startAttempt("replacement",1));
    }
    @Test void arenaVotePreservesOffAndCompetitionMode() {
        engine.setVoteTrackPool(List.of("s3k:arena:1","s3k:arena:2","s3k:arena:3"));
        engine.startRound(new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null,"ARENA","COMPETITION","OFF",30));
        now = engine.snapshot().deadlineHubMillis() + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        engine.onTick(); now += HostRoundEngine.ROUND_END_LINGER_MILLIS; engine.onTick();
        assertEquals(HostRoundEngine.Phase.VOTE,engine.phase());
        engine.onTrackVote(0,"s3k:arena:1"); now += HostRoundEngine.VOTE_WINDOW_MILLIS; engine.onTick();
        assertEquals("ARENA",engine.votedNextConfig().trackKind());
        assertEquals("COMPETITION",engine.votedNextConfig().mode());
        assertEquals("OFF",engine.votedNextConfig().monitors());
    }
    @Test void failedCompetitionVerificationBecomesDnfAndAllowsAllOutEnd() {
        start(); engine.setVerifiedRoom(true); assertTrue(engine.startAttempt("p0",1));
        assertNotNull(finish(0,100)); engine.onVerdict(0,1,false);
        assertEquals("DNF",engine.standings().getFirst().outcome());
        now=12_001; engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END,engine.phase());
        assertEquals(4,engine.standings().size());
    }
    @Test void expiredCompetitionClaimRetainsDnfRow() {
        start(); engine.setVerifiedRoom(true); engine.startAttempt("p0",1); finish(0,100);
        for (int i=1;i<4;i++) engine.forfeit("p"+i,"LEFT");
        engine.onTick(); now += HostRoundEngine.ROUND_END_LINGER_MILLIS + 10_001; engine.onTick();
        assertEquals(4,engine.standings().size());
        assertTrue(engine.standings().stream().allMatch(r -> "DNF".equals(r.outcome())));
    }
    @Test void scheduledStartSingleAttemptDnsAndForfeitOrdering() {
        start(); assertEquals(10_000,engine.snapshot().countdownEndsAtHubMillis());
        assertTrue(engine.startAttempt("p0",1)); assertFalse(engine.startAttempt("p0",2));
        assertTrue(engine.startAttempt("p1",1)); engine.forfeit("p1","GAME_OVER");
        now=12_001; assertFalse(engine.startAttempt("p2",1)); engine.onTick();
        assertNotNull(finish(0,100)); engine.onTick();
        assertEquals(List.of("FINISHED","DNF","DNS","DNS"),engine.standings().stream().map(ControlMessage.StandingsRow::outcome).toList());
        assertEquals(HostRoundEngine.Phase.ROUND_END,engine.phase());
        assertTrue(engine.standings().stream().allMatch(r -> r.verifyState().equals("UNVERIFIED")));
    }
    @Test void cutoffFramesAndWallGrace() {
        start(); for(int i=0;i<4;i++) assertTrue(engine.startAttempt("p"+i,1));
        assertNotNull(finish(0,100));
        assertTrue(messages.stream().anyMatch(m -> m instanceof ControlMessage.RaceCutoff c && c.endsAtHubMillis()==40_000));
        assertNull(finish(1,1901)); assertNotNull(finish(1,1900));
        now=42_001; assertNull(finish(2,100)); engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END,engine.phase());
    }
    @Test void independentLapImprovementAndUnverifiedSwitch() {
        engine.startRound(new ControlMessage.RoundConfig("s2",0,0,600,"OPEN",null));
        engine.profile(new TrackValidationProfile(4096,1024,32,60,0,0,2,0));
        now = 3000; engine.onTick();
        var first = new ControlMessage.AttemptFinish(1,100,0,100,List.of(50,50),List.of(40),"ab".repeat(32),"cd".repeat(32),null);
        var second = new ControlMessage.AttemptFinish(2,110,0,110,List.of(10,100),List.of(40),"ab".repeat(32),"cd".repeat(32),null);
        assertNotNull(engine.onAttemptFinish(0,"p",null,"P","sonic",first,false));
        assertNotNull(engine.onAttemptFinish(0,"p",null,"P","sonic",second,false));
        assertEquals(100,engine.standings().getFirst().bestTimeFrames());
        assertEquals(10,engine.page(0,10,"BEST_LAP").getFirst().bestLapFrames());
        assertEquals(new ControlMessage.RankUpdate(1,100,"TOTAL"),engine.rankUpdateForParticipant("p","TOTAL"));
        assertEquals(new ControlMessage.RankUpdate(1,10,"BEST_LAP"),engine.rankUpdateForParticipant("p","BEST_LAP"));
        engine.setPublishUnverifiedStandings(false);
        assertNull(engine.rankUpdateForParticipant("p","TOTAL"));
        assertTrue(engine.standings().isEmpty()); assertTrue(engine.page(0,10,"BEST_LAP").isEmpty());
    }
    @Test void splitsMustStrictlyIncreaseInsideClockInterval() {
        start(); engine.startAttempt("p0",1);
        for (List<Integer> splits : List.of(List.of(241,241), List.of(239), List.of(341))) {
            var f = new ControlMessage.AttemptFinish(1,100,240,340,List.of(20,20,20,20,20),splits,"ab".repeat(32),"cd".repeat(32),null);
            assertNull(engine.onAttemptFinish(0,"p0",null,"P","sonic",f,false));
        }
    }
    @Test void lapsSplitsTiesAndBestLapBoard() {
        start(); assertTrue(engine.startAttempt("p0",1)); assertTrue(engine.startAttempt("p1",1));
        var bad = new ControlMessage.AttemptFinish(1,100,0,100,List.of(1,2,3,4,91),List.of(),"ab".repeat(32),"cd".repeat(32),null);
        assertNull(engine.onAttemptFinish(0,"p0",null,"P","sonic",bad,false));
        assertNotNull(finish(0,100)); assertNotNull(finish(1,100));
        assertEquals(1,engine.standings().get(1).rank());
        assertEquals(1,engine.page(0,10,"BEST_LAP").getFirst().bestLapFrames());
        engine.setPublishUnverifiedStandings(false); assertEquals(2,engine.standings().size());
    }
}
