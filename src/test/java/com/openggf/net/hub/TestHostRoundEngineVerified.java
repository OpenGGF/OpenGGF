package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHostRoundEngineVerified {
    @Test void expiredImprovementReportsItsOwnIdentityAndRestoresVerifiedBest() {
        long[] now = {0};
        HostRoundEngine engine = running(now);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(0);
        List<String> expired = new ArrayList<>();
        List<HostRoundEngine.Result> results = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.setPendingResultExpiryListener(results::add);
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onVerdict(1, 1, true);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(List.of("1:2"), expired);
        assertEquals(2, results.getFirst().finish().attemptId());
        assertEquals("b".repeat(64), results.getFirst().finish().inputRecordingHashHex());
        assertEquals(200, engine.standings().getFirst().bestTimeFrames());
    }


    @Test void failedImprovementPreservesVerifiedBest() {
        long[] now = {0}; var engine = running(now); engine.setVerifiedRoom(true);
        engine.onAttemptFinish(1,"A","sonic",finish(1,200,"a"),false);
        engine.onVerdict(1,1,true);
        engine.onAttemptFinish(1,"A","sonic",finish(2,100,"b"),false);
        engine.onVerdict(1,2,false);
        assertEquals(200,engine.standings().getFirst().bestTimeFrames());
        assertEquals("VERIFIED",engine.standings().getFirst().verifyState());
    }

    private static final ControlMessage.RoundConfig CONFIG =
            new ControlMessage.RoundConfig("s3k", 0, 0, 1, "OPEN", null);

    @Test
    void finishVerdictFallbackAndExpiryPreserveCompleteControlSequence() {
        long[] now = {0};
        List<ControlMessage> actual = new ArrayList<>();
        List<ControlMessage> expected = new ArrayList<>();
        HostRoundEngine engine = new HostRoundEngine(() -> now[0], actual::add);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(0);
        assertTrue(engine.startRound(CONFIG));
        expected.add(new ControlMessage.RoundStart(CONFIG, 3000, 4000));
        now[0] = 3000;
        engine.onTick();
        List<ControlMessage.StandingsRow> top = new ArrayList<>();
        for (int slot = 1; slot <= 12; slot++) {
            assertEquals(new HostRoundEngine.FinishOutcome(slot, slot, slot > 10, 1),
                    engine.onAttemptFinish(slot, "P" + slot, "sonic",
                            finish(1, 100 + slot, "a"), false));
            if (slot <= 10) top.add(verifiedRow(slot, 100 + slot, slot, "PENDING"));
            expected.add(new ControlMessage.StandingsDelta(List.copyOf(top)));
            assertEquals(expected, actual, "including unchanged deltas outside the cap");
        }

        engine.onVerdict(12, 1, true);
        expected.add(new ControlMessage.StandingsDelta(List.copyOf(top)));
        engine.onVerdict(1, 1, true);
        top.set(0, verifiedRow(1, 101, 1, "VERIFIED"));
        expected.add(new ControlMessage.StandingsDelta(List.copyOf(top)));
        engine.onAttemptFinish(12, "P12", "sonic", finish(2, 50, "b"), false);
        List<ControlMessage.StandingsRow> improvedTop = new ArrayList<>();
        improvedTop.add(verifiedRow(12, 50, 1, "PENDING"));
        for (int slot = 1; slot <= 9; slot++) {
            improvedTop.add(verifiedRow(slot, 100 + slot, slot + 1,
                    slot == 1 ? "VERIFIED" : "PENDING"));
        }
        expected.add(new ControlMessage.StandingsDelta(List.copyOf(improvedTop)));
        engine.onVerdict(12, 2, false);
        expected.add(new ControlMessage.StandingsDelta(List.copyOf(top)));
        engine.onVerdict(11, 1, false);
        expected.add(new ControlMessage.StandingsDelta(List.copyOf(top)));
        assertEquals(expected, actual, "verdicts notify even when top-ten rows are unchanged");

        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        expected.add(new ControlMessage.RoundEnd(List.copyOf(top)));
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        List<ControlMessage.StandingsRow> verified = List.of(
                verifiedRow(1, 101, 1, "VERIFIED"),
                verifiedRow(12, 112, 2, "VERIFIED"));
        expected.add(new ControlMessage.StandingsDelta(verified));
        assertEquals(expected, actual, "round-end and pending-expiry ordering is unchanged");
        assertEquals(verified, engine.standings());
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
    }

    private static ControlMessage.StandingsRow verifiedRow(
            int slot, int frames, int rank, String state) {
        return new ControlMessage.StandingsRow(slot, "P" + slot, "sonic", frames, rank, state);
    }

    @Test
    void pendingFinishPassesAndFailRecomputesRanks() {
        long[] now = {0};
        HostRoundEngine engine = running(now);
        engine.setVerifiedRoom(true);
        engine.onAttemptFinish(1, "a", "sonic", finish(1, 100, "a"), false);
        engine.onAttemptFinish(2, "b", "tails", finish(2, 200, "b"), false);
        engine.onAttemptFinish(3, "c", "sonic", finish(3, 300, "c"), false);
        assertEquals("PENDING", engine.standings().getFirst().verifyState());
        engine.onVerdict(1, 1, true);
        assertEquals("VERIFIED", engine.standings().getFirst().verifyState());
        engine.onVerdict(2, 2, false);
        assertEquals(List.of(1, 2), engine.standings().stream()
                .map(ControlMessage.StandingsRow::rank).toList());
        assertEquals(List.of(1, 3), engine.standings().stream()
                .map(ControlMessage.StandingsRow::slot).toList());
    }

    @Test
    void roundEndHoldsPendingUntilVerdictOrHoldExpiry() {
        long[] now = {0};
        List<String> expired = new ArrayList<>();
        HostRoundEngine engine = running(now);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(100);
        engine.setPendingExpiryListener((slot, attempt) ->
                expired.add(slot + ":" + attempt));
        engine.onAttemptFinish(1, "a", "sonic", finish(7, 100, "a"), false);
        now[0] = 4_001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, engine.phase());
        now[0] += 101;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
        assertTrue(engine.standings().isEmpty());
        assertEquals(List.of("1:7"), expired);
    }

    @Test
    void casualRowsStayNoneAndDoNotHold() {
        long[] now = {0};
        HostRoundEngine engine = running(now);
        engine.onAttemptFinish(1, "a", "sonic", finish(1, 100, "a"), false);
        assertEquals("NONE", engine.standings().getFirst().verifyState());
        now[0] = 4_001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
    }

    @Test
    void bestFinishRetainsOnlyAcceptedImprovementAndClears() {
        long[] now = {0};
        HostRoundEngine engine = running(now);
        ControlMessage.AttemptFinish original = finish(1, 200, "a");
        engine.onAttemptFinish(1, "a", "sonic", original, false);
        engine.onAttemptFinish(1, "a", "sonic", finish(2, 300, "b"), false);
        assertEquals(original, engine.bestFinish(1));
        ControlMessage.AttemptFinish faster = finish(3, 100, "c");
        engine.onAttemptFinish(1, "a", "sonic", faster, false);
        assertEquals(faster, engine.bestFinish(1));
        assertNull(engine.bestFinish(99));
        now[0] = 4_001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertTrue(engine.startRound(CONFIG));
        assertNull(engine.bestFinish(1));
    }

    @Test
    void duplicateAttemptCannotReplacePendingClaimBeforeVerdict() {
        long[] now = {0};
        HostRoundEngine engine = running(now);
        engine.setVerifiedRoom(true);
        ControlMessage.AttemptFinish original = finish(1, 200, "a");
        engine.onAttemptFinish(1, "a", "sonic", original, false);

        assertNull(engine.onAttemptFinish(
                1, "a", "sonic", finish(1, 100, "b"), false));
        assertEquals(original, engine.bestFinish(1));
        engine.onVerdict(1, 1, true);
        assertEquals("VERIFIED", engine.standings().getFirst().verifyState());
        assertEquals(200, engine.standings().getFirst().bestTimeFrames());
    }

    @Test
    void supersededClaimThatPassesSurvivesFailureOfTheNewerClaim() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        assertEquals(List.of(row(1, "A", 100, 1, "PENDING")), engine.standings());

        int before = sent.size();
        engine.onVerdict(1, 1, true);
        assertEquals(before, sent.size(), "a hidden claim's verdict changes nothing visible");
        assertEquals(List.of(row(1, "A", 100, 1, "PENDING")), engine.standings());
        assertEquals(1, engine.pendingVerdictCount());

        engine.onVerdict(1, 2, false);
        assertEquals(List.of(row(1, "A", 200, 1, "VERIFIED")), engine.standings());
        assertEquals(new ControlMessage.StandingsDelta(List.of(row(1, "A", 200, 1, "VERIFIED"))),
                sent.getLast());
        assertEquals(before + 1, sent.size());
        assertEquals(0, engine.pendingVerdictCount());
    }

    @Test
    void failedNewerClaimShowsTheOlderClaimStillPending() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);

        engine.onVerdict(1, 2, false);
        assertEquals(List.of(row(1, "A", 200, 1, "PENDING")), engine.standings());
        assertEquals(new ControlMessage.StandingsDelta(List.of(row(1, "A", 200, 1, "PENDING"))),
                sent.getLast());
        assertEquals(1, engine.pendingVerdictCount(), "the older claim still holds round end");
        assertNull(engine.onAttemptFinish(1, "A", "sonic", finish(3, 200, "c"), false),
                "improvement checks compare against the shown older claim");

        engine.onVerdict(1, 1, true);
        assertEquals(List.of(row(1, "A", 200, 1, "VERIFIED")), engine.standings());
        assertEquals(0, engine.pendingVerdictCount());
    }

    @Test
    void stalePassSlowerThanVerifiedFallbackChangesNothing() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(0);
        List<String> expired = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        engine.onVerdict(1, 2, true);
        assertEquals(List.of(row(1, "A", 100, 1, "VERIFIED")), engine.standings());

        int before = sent.size();
        engine.onVerdict(1, 1, true);
        assertEquals(before, sent.size(), "stale pass broadcasts nothing");
        assertEquals(List.of(row(1, "A", 100, 1, "VERIFIED")), engine.standings());
        assertEquals(new ControlMessage.AttemptFinish(2, 100, 1, 101, "b".repeat(64),
                "ghost", null), engine.bestFinish(1));

        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(List.of(), expired, "the resolved claim has no route left to void");
    }

    @Test
    void roundEndExpiresEveryRemainingCandidateOnceAndBroadcastsOnce() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(0);
        List<String> expired = new ArrayList<>();
        List<String> expiredHashes = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.setPendingResultExpiryListener(result -> expiredHashes.add(
                result.participantId() + ":" + result.finish().inputRecordingHashHex()
                        .substring(0, 1) + ":" + result.verifyState()));
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 300, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 200, "b"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(3, 100, "c"), false);
        engine.onAttemptFinish(2, "B", "tails", finish(1, 250, "d"), false);
        engine.onVerdict(1, 2, true);
        assertEquals(List.of(row(1, "A", 100, 1, "PENDING"), row(2, "B", 250, 2, "PENDING")),
                engine.standings());

        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        int beforeExpiry = sent.size();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(List.of("1:1", "1:3", "2:1"), expired,
                "every unresolved claim, hidden or shown, is expired exactly once");
        assertEquals(List.of("legacy-slot:1:a:PENDING", "legacy-slot:1:c:PENDING",
                "legacy-slot:2:d:PENDING"), expiredHashes);
        List<ControlMessage.StandingsRow> fallback = List.of(row(1, "A", 200, 1, "VERIFIED"));
        assertEquals(List.of(new ControlMessage.StandingsDelta(fallback)),
                sent.subList(beforeExpiry, sent.size()));
        assertEquals(fallback, engine.standings());
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());

        int afterExpiry = sent.size();
        engine.onVerdict(1, 3, true);
        assertEquals(afterExpiry, sent.size(), "an expired claim's late verdict is ignored");
        assertEquals(fallback, engine.standings());
    }

    @Test
    void onlyHiddenCandidatesDoNotHoldRoundEndButStillExpire() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(60_000);
        List<String> expired = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        engine.onVerdict(1, 2, true);
        assertEquals(0, engine.pendingVerdictCount());

        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        int beforeLinger = sent.size();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
        assertEquals(List.of("1:1"), expired);
        assertEquals(beforeLinger, sent.size(), "nothing visible changed at expiry");
    }

    @Test
    void startRoundFromRoundEndExpiresLeftoverCandidatesBeforeClearing() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        engine.setVerifiedRoom(true);
        engine.setPendingHoldMillis(60_000);
        List<String> expired = new ArrayList<>();
        List<HostRoundEngine.Result> results = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.setPendingResultExpiryListener(results::add);
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, engine.phase());
        assertEquals(List.of(), expired);

        int beforeStart = sent.size();
        assertTrue(engine.startRound(CONFIG));
        assertEquals(List.of("1:1", "1:2"), expired);
        assertEquals(List.of("a".repeat(64), "b".repeat(64)), results.stream()
                .map(result -> result.finish().inputRecordingHashHex()).toList());
        assertEquals(1, sent.size() - beforeStart);
        assertInstanceOf(ControlMessage.RoundStart.class, sent.getLast());
        assertTrue(engine.standings().isEmpty());
        assertEquals(0, engine.pendingVerdictCount());

        engine.onVerdict(1, 2, true);
        assertEquals(beforeStart + 1, sent.size(), "previous round's verdict is ignored");
        assertTrue(engine.standings().isEmpty());
    }

    @Test
    void unverifiedRoomKeepsNoCandidates() {
        long[] now = {0};
        List<ControlMessage> sent = new ArrayList<>();
        HostRoundEngine engine = running(now, sent);
        List<String> expired = new ArrayList<>();
        engine.setPendingExpiryListener((slot, attempt) -> expired.add(slot + ":" + attempt));
        engine.onAttemptFinish(1, "A", "sonic", finish(1, 200, "a"), false);
        engine.onAttemptFinish(1, "A", "sonic", finish(2, 100, "b"), false);
        int before = sent.size();
        engine.onVerdict(1, 1, true);
        engine.onVerdict(1, 2, false);
        assertEquals(before, sent.size());
        assertEquals(List.of(row(1, "A", 100, 1, "NONE")), engine.standings());
        now[0] = 4001 + HostRoundEngine.FINISH_GRACE_MILLIS;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertTrue(engine.startRound(CONFIG));
        assertEquals(List.of(), expired);
    }

    private static ControlMessage.StandingsRow row(int slot, String name, int frames,
                                                   int rank, String state) {
        return new ControlMessage.StandingsRow(slot, name, "A".equals(name) ? "sonic" : "tails",
                frames, rank, state);
    }

    @Test
    void malformedInputRecordingHashesAreRejectedBeforeStandings() {
        assertNull(running(new long[]{0}).onAttemptFinish(1, "a", "sonic",
                finishWithInputHash(1, 100, null), false));
        assertNull(running(new long[]{0}).onAttemptFinish(1, "a", "sonic",
                finishWithInputHash(1, 100, "a".repeat(63)), false));
        assertNull(running(new long[]{0}).onAttemptFinish(1, "a", "sonic",
                finishWithInputHash(1, 100, "z".repeat(64)), false));
    }

    private static HostRoundEngine running(long[] now) {
        return running(now, new ArrayList<>());
    }

    private static HostRoundEngine running(long[] now, List<ControlMessage> sent) {
        HostRoundEngine engine = new HostRoundEngine(() -> now[0], sent::add);
        assertTrue(engine.startRound(CONFIG));
        now[0] = HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        return engine;
    }

    private static ControlMessage.AttemptFinish finish(int id, int time, String hash) {
        return finishWithInputHash(id, time, hash.repeat(64));
    }

    private static ControlMessage.AttemptFinish finishWithInputHash(
            int id, int time, String inputHash) {
        return new ControlMessage.AttemptFinish(id, time, 1, time + 1,
                inputHash, "ghost", null);
    }
}
