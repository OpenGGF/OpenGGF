package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestHostRoundEngine {
    private long now = 1_000_000;
    private final List<ControlMessage> broadcast = new ArrayList<>();
    private HostRoundEngine engine;
    private final ControlMessage.RoundConfig config =
            new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null);

    @BeforeEach
    void setUp() {
        engine = new HostRoundEngine(() -> now, broadcast::add);
    }

    private static ControlMessage.AttemptFinish finish(int attemptId, int frames) {
        return new ControlMessage.AttemptFinish(attemptId, frames, 10, 10 + frames,
                "ab".repeat(32), "cd".repeat(32), null);
    }

    @Test
    void fullRoundLifecycle() {
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
        assertTrue(engine.startRound(config));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, engine.phase());
        ControlMessage.RoundStart start = (ControlMessage.RoundStart) broadcast.get(0);
        assertEquals(now + HostRoundEngine.COUNTDOWN_MILLIS,
                start.countdownEndsAtHubMillis());
        assertEquals(start.countdownEndsAtHubMillis() + 300_000,
                start.deadlineHubMillis());

        now = start.countdownEndsAtHubMillis();
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.RUNNING, engine.phase());
        now = start.deadlineHubMillis() + 1;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, engine.phase());
        assertInstanceOf(ControlMessage.RoundEnd.class, broadcast.get(broadcast.size() - 1));
        now += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
    }

    @Test
    void cannotStartRoundMidRound() {
        engine.startRound(config);
        assertFalse(engine.startRound(config));
        assertEquals(1,
                broadcast.stream().filter(message -> message instanceof ControlMessage.RoundStart)
                        .count());
    }

    @Test
    void standingsRankImprovementsAndIgnoreSlower() {
        engine.startRound(config);
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        engine.onAttemptFinish(0, "A", "sonic", finish(1, 4000), false);
        engine.onAttemptFinish(1, "B", "tails", finish(1, 3500), false);
        engine.onAttemptFinish(0, "A", "sonic", finish(2, 5000), false);

        List<ControlMessage.StandingsRow> rows = engine.standings();
        assertEquals(2, rows.size());
        assertEquals("B", rows.get(0).displayName());
        assertEquals(1, rows.get(0).rank());
        assertEquals(4000, rows.get(1).bestTimeFrames());

        engine.onAttemptFinish(0, "A", "sonic", finish(3, 3000), false);
        assertEquals("A", engine.standings().get(0).displayName());
        assertEquals(3,
                broadcast.stream()
                        .filter(message -> message instanceof ControlMessage.StandingsDelta)
                        .count());
    }

    @Test
    void equalTimesOrderByWhenThatTimeWasAchieved() {
        engine.startRound(config);
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        engine.onAttemptFinish(0, "A", "sonic", finish(1, 4000), false);
        engine.onAttemptFinish(1, "B", "tails", finish(1, 3000), false);
        engine.onAttemptFinish(0, "A", "sonic", finish(2, 3000), false);
        assertEquals(List.of("B", "A"), engine.standings().stream()
                .map(ControlMessage.StandingsRow::displayName).toList());
    }

    @Test
    void rejectsFinishOutsideWindowFlaggedOrInLobby() {
        engine.onAttemptFinish(0, "A", "sonic", finish(1, 4000), false);
        assertTrue(engine.standings().isEmpty());
        engine.startRound(config);
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        engine.onAttemptFinish(1, "B", "tails", finish(1, 3500), true);
        assertTrue(engine.standings().isEmpty());
        now += 300_000 + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        engine.onAttemptFinish(2, "C", "sonic", finish(1, 3000), false);
        assertTrue(engine.standings().isEmpty());
    }

    @Test
    void playerLeavingKeepsBestForTheRound() {
        engine.startRound(config);
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        engine.onAttemptFinish(0, "A", "sonic", finish(1, 4000), false);
        engine.onPlayerLeft(0);
        assertEquals(1, engine.standings().size());
    }

    @Test
    void snapshotCarriesPhaseConfigAndStandings() {
        engine.startRound(config);
        ControlMessage.RoundSnapshot snapshot = engine.snapshot();
        assertEquals("COUNTDOWN", snapshot.phase());
        assertEquals(config, snapshot.config());
        assertEquals(now + HostRoundEngine.COUNTDOWN_MILLIS,
                snapshot.countdownEndsAtHubMillis());
        assertTrue(snapshot.standings().isEmpty());
    }
}
