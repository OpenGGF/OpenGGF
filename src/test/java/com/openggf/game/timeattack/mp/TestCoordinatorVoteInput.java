package com.openggf.game.timeattack.mp;

import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCoordinatorVoteInput {
    @Test
    void castVoteSendsOfferedKeyAndIgnoresInvalidOrClosedVote() {
        List<ControlMessage> sent = new ArrayList<>();
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        MultiplayerRaceCoordinator coordinator = new MultiplayerRaceCoordinator(
                fakeTransport(sent), session);
        session.onControl(new ControlMessage.TrackVoteOffer(
                List.of("s3k:0:1", "s3k:1:0"), 10_000));
        coordinator.castVote(1);
        assertEquals(new ControlMessage.TrackVote("s3k:1:0"), sent.getLast());
        int before = sent.size();
        coordinator.castVote(2);
        coordinator.castVote(-1);
        session.onControl(new ControlMessage.TrackVoteResult("s3k:1:0"));
        coordinator.castVote(0);
        assertEquals(before, sent.size());
        assertEquals(List.of("s3k:1:0"),
                List.of(coordinator.hudState().voteResultTrackKey()));
    }

    private static RaceTransport fakeTransport(List<ControlMessage> sent) {
        return new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() { return List.of(); }
            @Override public void sendControl(ControlMessage message) { sent.add(message); }
            @Override public void sendBinary(byte[] data) { }
            @Override public int playerSlot() { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
    }
}
