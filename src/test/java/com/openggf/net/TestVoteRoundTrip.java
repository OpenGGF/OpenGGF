package com.openggf.net;

import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase-4 in-memory acceptance gate for a complete round and vote cycle. */
class TestVoteRoundTrip {

    @Test
    void fullCycleRoundPodiumVoteNextRound() {
        long[] now = {0};
        ClientRaceSession clientA = new ClientRaceSession(() -> now[0]);
        ClientRaceSession clientB = new ClientRaceSession(() -> now[0]);
        HostRoundEngine engine = new HostRoundEngine(() -> now[0], message -> {
            clientA.onControl(message);
            clientB.onControl(message);
        });
        engine.setVoteTrackPool(List.of(
                "s3k:0:0", "s3k:0:1", "s3k:1:0", "s3k:1:1"));

        ControlMessage.RoundConfig config = new ControlMessage.RoundConfig(
                "s3k", 0, 0, 60, "OPEN", null);
        assertTrue(engine.startRound(config));
        now[0] += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        engine.onAttemptFinish(1, "ana", "sonic",
                new ControlMessage.AttemptFinish(
                        1, 1885, 10, 1895, "aa", "bb", null), false);
        now[0] += 60_001;
        engine.onTick();
        assertEquals("ana", clientA.podiumTop(3).getFirst().displayName());

        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(ClientRaceSession.Phase.VOTE, clientA.phase());
        List<String> options = clientA.voteOptions();
        engine.onTrackVote(1, options.getFirst());
        engine.onTrackVote(2, options.getFirst());
        now[0] += HostRoundEngine.VOTE_WINDOW_MILLIS;
        engine.onTick();
        assertEquals(options.getFirst(), clientA.lastVoteResultTrackKey());

        ControlMessage.RoundConfig next = engine.votedNextConfig();
        assertNotNull(next);
        assertTrue(engine.startRound(next));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, engine.phase());
    }
}
