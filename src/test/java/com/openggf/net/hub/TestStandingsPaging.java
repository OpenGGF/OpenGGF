package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        return new ControlMessage.AttemptFinish(1, frames, 5, 5 + frames,
                "ab".repeat(32), "cd".repeat(32), null);
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
