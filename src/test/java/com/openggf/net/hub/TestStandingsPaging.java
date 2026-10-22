package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStandingsPaging {
    private long now = 1_000_000;
    private final List<ControlMessage> broadcast = new ArrayList<>();
    private HostRoundEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HostRoundEngine(() -> now, broadcast::add);
        engine.startRound(new ControlMessage.RoundConfig("s3k", 0, 0, 600, "OPEN", null));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
    }

    private static ControlMessage.AttemptFinish finish(int frames) {
        return finish(1, frames);
    }

    private static ControlMessage.AttemptFinish finish(int attempt, int frames) {
        return new ControlMessage.AttemptFinish(attempt, frames, 5, 5 + frames,
                "ab".repeat(32), "cd".repeat(32), null);
    }

    @Test
    void everyImprovementPreservesBroadcastSequenceAndParticipantRanks() {
        List<ExpectedBest> expected = new ArrayList<>();
        for (int slot = 0; slot < 256; slot++) {
            assertImprovement(expected, "participant:" + slot, slot, 1,
                    1000 + (slot * 17) % 31);
        }
        for (int slot = 255; slot >= 0; slot--) {
            assertImprovement(expected, "participant:" + slot, slot, 2,
                    700 + (slot * 13) % 31);
        }
        // A reused slot can have two retained participants with different ranks.
        assertImprovement(expected, "replacement", 0, 1, 700);

        int before = broadcast.size();
        assertNull(engine.onAttemptFinish(0, "replacement", null,
                "replacement", "tails", finish(2, 701), false));
        assertEquals(before, broadcast.size(), "a slower finish emits no delta");

        now = engine.snapshot().deadlineHubMillis() + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        engine.onTick();
        assertEquals(new ControlMessage.RoundEnd(expectedRows(expected).subList(0, 10)),
                broadcast.getLast(), "round end retains the final capped ordering");
    }

    @Test
    void unchangedTopTenStillNotifiesOutsideCapFinishersAndResetsNextRound() {
        List<ExpectedBest> expected = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            assertImprovement(expected, "participant:" + slot, slot, 1, 1000 + slot);
        }
        ControlMessage previous = broadcast.getLast();
        assertImprovement(expected, "slow", 20, 1, 2000);
        assertEquals(previous, broadcast.getLast(),
                "an unchanged top ten still triggers other clients' around-you refresh");
        assertImprovement(expected, "slow", 20, 2, 1500);
        assertEquals(previous, broadcast.getLast());

        now = engine.snapshot().deadlineHubMillis() + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        engine.onTick();
        assertTrue(engine.startRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 600, "OPEN", null)));
        assertEquals(List.of(), engine.snapshot().standings());
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        assertImprovement(new ArrayList<>(), "slow", 20, 1, 2000);
    }

    private record ExpectedBest(String participantId, int slot, String name, int frames) { }

    private void assertImprovement(List<ExpectedBest> expected, String participantId,
                                   int slot, int attempt, int frames) {
        expected.removeIf(best -> best.participantId().equals(participantId));
        expected.add(new ExpectedBest(participantId, slot, participantId, frames));
        // Stable sorting of newly achieved bests independently models the tie rule.
        expected.sort(Comparator.comparingInt(ExpectedBest::frames));
        List<ControlMessage.StandingsRow> rows = expectedRows(expected);
        int rank = 1;
        while (!expected.get(rank - 1).participantId().equals(participantId)) rank++;
        int before = broadcast.size();

        assertEquals(new HostRoundEngine.FinishOutcome(slot, rank, rank > 10, attempt),
                engine.onAttemptFinish(slot, participantId, null, participantId, "tails",
                        finish(attempt, frames), false));
        assertEquals(before + 1, broadcast.size(), "each improvement emits exactly one delta");
        assertEquals(new ControlMessage.StandingsDelta(rows.subList(0, Math.min(10, rows.size()))),
                broadcast.getLast());
        assertEquals(rows, engine.standings());
        assertEquals(expected.stream().map(ExpectedBest::participantId).toList(),
                engine.results().stream().map(HostRoundEngine.Result::participantId).toList());
        assertEquals(rows, engine.page(0, 1000), "all ranks remain available to page requests");
        assertEquals(rows.subList(0, Math.min(10, rows.size())), engine.snapshot().standings());
    }

    private static List<ControlMessage.StandingsRow> expectedRows(List<ExpectedBest> expected) {
        List<ControlMessage.StandingsRow> rows = new ArrayList<>();
        for (ExpectedBest best : expected) {
            rows.add(new ControlMessage.StandingsRow(best.slot(), best.name(), "tails",
                    best.frames(), rows.size() + 1, "NONE"));
        }
        return rows;
    }

    @Test
    void broadcastsOnlyTopCapButPagesFullList() {
        for (int slot = 0; slot < 25; slot++) {
            engine.onAttemptFinish(slot, "P" + slot, "sonic", finish(1000 + slot), false);
        }
        ControlMessage.StandingsDelta lastDelta = broadcast.stream()
                .filter(ControlMessage.StandingsDelta.class::isInstance)
                .map(ControlMessage.StandingsDelta.class::cast)
                .reduce((a, b) -> b).orElseThrow();
        assertEquals(HostRoundEngine.STANDINGS_BROADCAST_CAP, lastDelta.rows().size());
        assertEquals("P0", lastDelta.rows().getFirst().displayName());
        assertEquals(3, engine.totalPages(10));
        List<ControlMessage.StandingsRow> page2 = engine.page(2, 10);
        assertEquals(5, page2.size());
        assertEquals(21, page2.getFirst().rank());
    }

    @Test
    void slowFinisherOutsideCapGetsRankUpdate() {
        for (int slot = 0; slot < 15; slot++) {
            engine.onAttemptFinish(slot, "P" + slot, "sonic", finish(1000 + slot), false);
        }
        HostRoundEngine.FinishOutcome outcome =
                engine.onAttemptFinish(20, "LATE", "sonic", finish(9999), false);
        assertNotNull(outcome);
        assertEquals(16, outcome.rank());
        assertTrue(outcome.outsideBroadcastCap());
    }
}
