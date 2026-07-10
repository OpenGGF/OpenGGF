package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHostRoundEngineVote {
    private long now = 1_000_000;
    private final List<ControlMessage> sent = new ArrayList<>();
    private final ControlMessage.RoundConfig config =
            new ControlMessage.RoundConfig("s3k", 0, 0, 120, "OPEN", null);
    private HostRoundEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HostRoundEngine(() -> now, sent::add);
        engine.setVoteTrackPool(List.of("s3k:0:0", "s3k:0:1", "s3k:1:0", "s3k:1:1"));
    }

    private void reachVote() {
        assertTrue(engine.startRound(config));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        engine.onTick();
        now += config.windowSeconds() * 1000L + 1;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, engine.phase());
        now += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.VOTE, engine.phase());
    }

    @Test
    void offersThreeEligibleOptionsAndRejectsRoundStartDuringVote() {
        reachVote();
        assertEquals(3, engine.voteOptions().size());
        assertFalse(engine.voteOptions().contains("s3k:0:0"));
        assertFalse(engine.startRound(config));
        ControlMessage.TrackVoteOffer offer = sent.stream()
                .filter(ControlMessage.TrackVoteOffer.class::isInstance)
                .map(ControlMessage.TrackVoteOffer.class::cast).reduce((a, b) -> b).orElseThrow();
        assertEquals(engine.voteOptions(), offer.trackKeys());
    }

    @Test
    void lastVoteWinsPerSlotMajorityProducesNextConfig() {
        reachVote();
        List<String> options = engine.voteOptions();
        engine.onTrackVote(1, options.getFirst());
        engine.onTrackVote(1, options.get(1));
        engine.onTrackVote(2, options.get(1));
        ControlMessage.TrackVoteTally tally = sent.stream()
                .filter(ControlMessage.TrackVoteTally.class::isInstance)
                .map(ControlMessage.TrackVoteTally.class::cast).reduce((a, b) -> b).orElseThrow();
        assertEquals(2, tally.counts().stream().mapToInt(ControlMessage.VoteCount::votes).sum());
        now += HostRoundEngine.VOTE_WINDOW_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
        assertNotNull(engine.votedNextConfig());
        assertEquals(options.get(1), engine.votedNextConfig().gameId() + ":"
                + engine.votedNextConfig().zone() + ":" + engine.votedNextConfig().act());
    }

    @Test
    void zeroVotesBroadcastsRetainedTrackAndKeepsConfigNull() {
        reachVote();
        now += HostRoundEngine.VOTE_WINDOW_MILLIS;
        engine.onTick();
        assertNull(engine.votedNextConfig());
        ControlMessage.TrackVoteResult result = sent.stream()
                .filter(ControlMessage.TrackVoteResult.class::isInstance)
                .map(ControlMessage.TrackVoteResult.class::cast).reduce((a, b) -> b).orElseThrow();
        assertEquals("s3k:0:0", result.trackKey());
    }

    @Test
    void malformedCrossGameAndUnknownTracksAreNeverOffered() {
        engine.setVoteTrackPool(List.of("bad", "s2:0:0", "s3k:99:99",
                        "s3k:0:1", "s3k:1:0"),
                Set.of("s3k:0:0", "s3k:0:1", "s3k:1:0")::contains);
        reachVote();
        assertEquals(List.of("s3k:0:1", "s3k:1:0"), engine.voteOptions());
    }

    @Test
    void emptyPoolPreservesOriginalRoundEndLifecycle() {
        engine.setVoteTrackPool(List.of());
        assertTrue(engine.startRound(config));
        now += HostRoundEngine.COUNTDOWN_MILLIS + config.windowSeconds() * 1000L + 1;
        engine.onTick();
        now += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        engine.onTick();
        assertEquals(HostRoundEngine.Phase.LOBBY, engine.phase());
    }
}
