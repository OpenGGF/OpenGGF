package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRosterConsumption {
    @Test
    void farPlayersComeFromRosterMinusNearPlaybacks() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic", false),
                new ControlMessage.PlayerInfo(1, "fp1", "NEAR", "tails", false),
                new ControlMessage.PlayerInfo(2, "fp2", "FAR", "knuckles", false)));

        byte[] frames = new byte[3 * com.openggf.ghost.GhostFrameCodec.BYTES];
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(1, 1, i, 3, frames.clone()))));
        }
        registry.onRoster(List.of(
                new GhostPackets.RosterEntry(1, 10, 5,
                        GhostPackets.ROSTER_STATUS_RUNNING),
                new GhostPackets.RosterEntry(2, 140, 8,
                        GhostPackets.ROSTER_STATUS_FINISHED)));

        List<RemoteGhostRegistry.FarPlayer> far = registry.farPlayers(0);
        assertEquals(1, far.size());
        assertEquals(2, far.getFirst().slot());
        assertEquals("FAR", far.getFirst().displayName());
        assertEquals(140, far.getFirst().cellX());
        assertEquals(GhostPackets.ROSTER_STATUS_FINISHED, far.getFirst().status());
    }

    @Test
    void resetClearsRosterToo() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(new ControlMessage.PlayerInfo(
                2, "fp2", "FAR", "knuckles", false)));
        registry.onRoster(List.of(new GhostPackets.RosterEntry(2, 1, 1, 0)));
        assertEquals(1, registry.farPlayers(0).size());
        registry.reset();
        assertTrue(registry.farPlayers(0).isEmpty());
    }
}
