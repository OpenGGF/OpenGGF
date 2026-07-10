package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBackpressureLadder {
    static final class SlowConnection extends FakeHubConnection {
        int queued;

        @Override
        public int queuedBytes() {
            return queued;
        }
    }

    private long now = 50_000;
    private GhostHub hub;
    private final SlowConnection slow = new SlowConnection();
    private final FakeHubConnection healthy = new FakeHubConnection();
    private final FakeHubConnection sender = new FakeHubConnection();

    @BeforeEach
    void setUp() {
        hub = new GhostHub(() -> now, TrackValidationProfileSource.none(),
                (slot, fingerprint, kind, detail) -> { }, true);
        hub.setTrack("s3k", 0, 0);
        hub.addPlayer(0, "sender", sender);
        hub.addPlayer(1, "slow", slow);
        hub.addPlayer(2, "healthy", healthy);
    }

    private void streamNearEveryone(int startIndex) {
        byte[] data = new byte[3 * GhostFrameCodec.BYTES];
        for (int i = 0; i < 3; i++) {
            GhostFrameCodec.encode(new GhostFrame(1000 + startIndex + i, 256,
                    1, false, false, false, 2, false), data,
                    i * GhostFrameCodec.BYTES);
        }
        hub.onBinary(0, GhostPackets.encodeFrames(1, startIndex, data));
        if (startIndex == 0) {
            byte[] near = new byte[GhostFrameCodec.BYTES];
            GhostFrameCodec.encode(new GhostFrame(1010, 256, 1, false, false,
                    false, 2, false), near, 0);
            hub.onBinary(1, GhostPackets.encodeFrames(1, 0, near.clone()));
            hub.onBinary(2, GhostPackets.encodeFrames(1, 0, near.clone()));
        }
    }

    @Test
    void rosterOnlyStageSuppressesAggregates() {
        streamNearEveryone(0);
        slow.queued = GhostHub.BP_ROSTER_ONLY_BYTES + 1;
        hub.tick();
        assertTrue(slow.binary.stream().noneMatch(packet ->
                (packet[0] & 0xFF) == GhostPackets.TYPE_GHOST_AGGREGATE));
        assertTrue(healthy.binary.stream().anyMatch(packet ->
                (packet[0] & 0xFF) == GhostPackets.TYPE_GHOST_AGGREGATE));
    }

    @Test
    void disconnectStageClosesImmediatelyOnHugeQueue() {
        slow.queued = GhostHub.BP_DISCONNECT_BYTES + 1;
        hub.tick();
        assertNotNull(slow.closedReason);
    }

    @Test
    void sustainedDegradeDisconnects() {
        slow.queued = GhostHub.BP_DEGRADE_BYTES + 1;
        for (int i = 0; i <= GhostHub.BP_SUSTAINED_MILLIS / 50; i++) {
            hub.tick();
            now += 50;
        }
        assertNotNull(slow.closedReason);
    }

    @Test
    void recoveryClearsTheSustainedClock() {
        slow.queued = GhostHub.BP_DEGRADE_BYTES + 1;
        for (int i = 0; i < 100; i++) {
            hub.tick();
            now += 50;
        }
        slow.queued = 0;
        for (int i = 0; i < 700; i++) {
            hub.tick();
            now += 50;
        }
        assertNull(slow.closedReason);
    }
}
