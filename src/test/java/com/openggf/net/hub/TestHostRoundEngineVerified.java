package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHostRoundEngineVerified {
    private static final ControlMessage.RoundConfig CONFIG =
            new ControlMessage.RoundConfig("s3k", 0, 0, 1, "OPEN", null);

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
        now[0] = 4_001;
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
        now[0] = 4_001;
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
        now[0] = 4_001;
        engine.onTick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertTrue(engine.startRound(CONFIG));
        assertNull(engine.bestFinish(1));
    }

    private static HostRoundEngine running(long[] now) {
        HostRoundEngine engine = new HostRoundEngine(() -> now[0], ignored -> { });
        assertTrue(engine.startRound(CONFIG));
        now[0] = HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        return engine;
    }

    private static ControlMessage.AttemptFinish finish(int id, int time, String hash) {
        return new ControlMessage.AttemptFinish(id, time, 1, time + 1,
                hash, "ghost", null);
    }
}
