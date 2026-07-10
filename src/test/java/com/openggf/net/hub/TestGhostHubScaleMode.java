package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGhostHubScaleMode {
    private long now = 50_000;
    private GhostHub hub;
    private final FakeHubConnection near = new FakeHubConnection();
    private final FakeHubConnection me = new FakeHubConnection();
    private final FakeHubConnection far = new FakeHubConnection();
    private final FakeHubConnection idle = new FakeHubConnection();

    @BeforeEach
    void setUp() {
        hub = new GhostHub(() -> now, TrackValidationProfileSource.none(),
                (slot, fingerprint, kind, detail) -> { }, true);
        hub.setTrack("s3k", 0, 0);
        hub.addPlayer(0, "fp-me", me);
        hub.addPlayer(1, "fp-near", near);
        hub.addPlayer(2, "fp-far", far);
        hub.addPlayer(3, "fp-idle", idle);
        for (int slot = 0; slot < 4; slot++) hub.onAttemptStart(slot, 1);
    }

    private static byte[] frames(int x, int count, boolean finishedLast) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(x + i, 256, 1, false, false,
                    finishedLast && i == count - 1, 2, false), data,
                    i * GhostFrameCodec.BYTES);
        }
        return data;
    }

    private void stream(int slot, int attemptId, int startIndex, int x) {
        hub.onBinary(slot, GhostPackets.encodeFrames(attemptId, startIndex,
                frames(x, 3, false)));
    }

    @Test
    void aggregatesIncludeOnlyNearSenders() {
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1200);
        stream(2, 1, 0, 9000);
        hub.tick();

        GhostPackets.Aggregate forMe = GhostPackets.decodeAggregate(me.binary.getFirst());
        assertEquals(1, forMe.entries().size());
        assertEquals(1, forMe.entries().getFirst().playerSlot());
        assertTrue(far.binary.isEmpty());
    }

    @Test
    void rosterArrivesAtOneHertzForEveryone() {
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1200);
        stream(2, 1, 0, 9000);
        for (int tick = 0; tick < GhostHub.ROSTER_INTERVAL_TICKS; tick++) {
            hub.tick();
        }

        List<GhostPackets.RosterEntry> roster = lastRoster(far);
        assertEquals(4, roster.size());
        GhostPackets.RosterEntry farEntry = roster.stream()
                .filter(entry -> entry.playerSlot() == 2).findFirst().orElseThrow();
        assertEquals(9002 >>> 6, farEntry.cellX());
        assertEquals(GhostPackets.ROSTER_STATUS_RUNNING, farEntry.status());
        GhostPackets.RosterEntry idleEntry = roster.stream()
                .filter(entry -> entry.playerSlot() == 3).findFirst().orElseThrow();
        assertEquals(GhostPackets.ROSTER_STATUS_IDLE, idleEntry.status());
        assertEquals(0, idleEntry.cellX());
    }

    @Test
    void finishedBitFlipsRosterStatus() {
        hub.onBinary(1, GhostPackets.encodeFrames(1, 0, frames(1200, 3, true)));
        for (int tick = 0; tick < GhostHub.ROSTER_INTERVAL_TICKS; tick++) {
            hub.tick();
        }
        assertEquals(GhostPackets.ROSTER_STATUS_FINISHED, lastRoster(me).stream()
                .filter(entry -> entry.playerSlot() == 1).findFirst().orElseThrow().status());
    }

    @Test
    void smallRoomModeIsUnchangedNoFilteringNoRoster() {
        GhostHub smallRoom = new GhostHub(() -> now, TrackValidationProfileSource.none(),
                (slot, fingerprint, kind, detail) -> { });
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        smallRoom.setTrack("s3k", 0, 0);
        smallRoom.addPlayer(0, "a", a);
        smallRoom.addPlayer(1, "b", b);
        smallRoom.onAttemptStart(0, 1);
        smallRoom.onAttemptStart(1, 1);
        smallRoom.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(1000, 3, false)));
        smallRoom.onBinary(1, GhostPackets.encodeFrames(1, 0, frames(60_000, 3, false)));
        for (int tick = 0; tick < GhostHub.ROSTER_INTERVAL_TICKS + 1; tick++) {
            smallRoom.tick();
        }
        assertTrue(a.binary.stream().anyMatch(packet ->
                (packet[0] & 0xFF) == GhostPackets.TYPE_GHOST_AGGREGATE));
        assertTrue(a.binary.stream().noneMatch(packet ->
                (packet[0] & 0xFF) == GhostPackets.TYPE_ROSTER));
        assertTrue(b.binary.stream().noneMatch(packet ->
                (packet[0] & 0xFF) == GhostPackets.TYPE_ROSTER));
    }

    private static List<GhostPackets.RosterEntry> lastRoster(FakeHubConnection connection) {
        List<GhostPackets.RosterEntry> roster = new ArrayList<>();
        for (byte[] packet : connection.binary) {
            if ((packet[0] & 0xFF) == GhostPackets.TYPE_ROSTER) {
                roster = GhostPackets.decodeRoster(packet);
            }
        }
        return roster;
    }
}
