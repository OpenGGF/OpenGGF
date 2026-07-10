package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestClientRaceSessionVote {
    @Test
    void voteOfferTallyAndResultFlow() {
        long[] now = {50_000};
        ClientRaceSession session = new ClientRaceSession(() -> now[0]);
        session.onControl(new ControlMessage.TrackVoteOffer(
                List.of("s3k:0:1", "s3k:1:0"), 65_000));
        assertEquals(ClientRaceSession.Phase.VOTE, session.phase());
        assertTrue(session.voteRemainingMillis() > 0);
        session.onControl(new ControlMessage.TrackVoteTally(
                List.of(new ControlMessage.VoteCount("s3k:0:1", 3))));
        assertEquals(3, session.voteCounts().getFirst().votes());
        session.onControl(new ControlMessage.TrackVoteResult("s3k:0:1"));
        assertEquals(ClientRaceSession.Phase.LOBBY, session.phase());
        assertEquals("s3k:0:1", session.lastVoteResultTrackKey());
        assertTrue(session.voteOptions().isEmpty());
        assertEquals(-1, session.voteRemainingMillis());
    }

    @Test
    void podiumAndLocalRowComeFromRoundEnd() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.applyJoin(new ControlMessage.JoinAccepted("tok", 2, null, null));
        session.onControl(new ControlMessage.RoundEnd(List.of(
                new ControlMessage.StandingsRow(0, "ana", "sonic", 3000, 1),
                new ControlMessage.StandingsRow(1, "bob", "tails", 3100, 2),
                new ControlMessage.StandingsRow(2, "you", "knuckles", 3200, 3),
                new ControlMessage.StandingsRow(3, "dan", "sonic", 3300, 4))));
        assertEquals(3, session.podiumTop(3).size());
        assertEquals(3, session.localStandingsRow().rank());
    }
}
