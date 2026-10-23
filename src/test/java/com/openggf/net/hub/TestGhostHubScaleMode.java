package com.openggf.net.hub;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
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

    @Test
    void nearestMembershipUsesDistanceButAggregateSendersStayAscending() {
        for (int slot = 4; slot <= 12; slot++) {
            hub.addPlayer(slot, "fp-" + slot, new FakeHubConnection());
            hub.onAttemptStart(slot, 1);
        }
        stream(0, 1, 0, 1000);
        // Higher slots are closer, so iteration order differs from wire order.
        for (int slot = 1; slot <= 12; slot++) stream(slot, 1, 0, 1250 - slot * 10);
        hub.tick();
        assertEquals(List.of(5, 6, 7, 8, 9, 10, 11, 12), aggregateSlots(me));
        me.binary.clear();
        hub.tick();
        assertTrue(me.binary.isEmpty(), "all drained frames were consumed on the prior tick");
    }

    @Test
    void degradedCapSelectsNearestFourBeforePendingSendersAndSortsTheirSlots() {
        FakeHubConnection degraded = new FakeHubConnection() {
            @Override public int queuedBytes() { return GhostHub.BP_DEGRADE_BYTES + 1; }
        };
        hub.addPlayer(0, "degraded", degraded);
        hub.onAttemptStart(0, 1);
        for (int slot = 4; slot <= 10; slot++) {
            hub.addPlayer(slot, "fp-" + slot, new FakeHubConnection());
            hub.onAttemptStart(slot, 1);
        }
        stream(0, 1, 0, 1000);
        for (int slot = 1; slot <= 10; slot++) stream(slot, 1, 0, 1250 - slot * 10);
        hub.tick();
        assertEquals(List.of(7, 8, 9, 10), aggregateSlots(degraded));
        degraded.binary.clear();
        now += 50;
        // Nearest slot 10 has no pending data; a fifth-nearest sender must not fill its place.
        for (int slot = 1; slot <= 9; slot++) stream(slot, 1, 3, 1250 - slot * 10);
        hub.tick();
        assertEquals(List.of(7, 8, 9), aggregateSlots(degraded));
    }

    @Test
    void disconnectingBucketedSenderDoesNotBreakLaterRecipientsOrRoster() {
        FakeHubConnection disconnecting = new FakeHubConnection() {
            @Override public int queuedBytes() { return GhostHub.BP_DISCONNECT_BYTES + 1; }
        };
        hub.addPlayer(0, "disconnecting", disconnecting);
        hub.onAttemptStart(0, 1);
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1020);
        stream(2, 1, 0, 1040);
        hub.tick();
        assertEquals("slow consumer", disconnecting.closedReason);
        assertEquals(List.of(2), aggregateSlots(near));
        assertEquals(List.of(1), aggregateSlots(far));
        for (int tick = 1; tick < GhostHub.ROSTER_INTERVAL_TICKS; tick++) hub.tick();
        assertEquals(List.of(1, 2, 3), lastRoster(near).stream()
                .map(GhostPackets.RosterEntry::playerSlot).toList());
    }

    @Test
    void finishedSenderRelaysBufferedTailThenYieldsItsNearPlaceToNextNearest() {
        for (int slot = 4; slot <= 9; slot++) {
            hub.addPlayer(slot, "fp-" + slot, new FakeHubConnection());
            hub.onAttemptStart(slot, 1);
        }
        // Slot 1 is nearest and buffers more than one aggregate entry before finishing.
        for (int batch = 0; batch < 15; batch++) {
            stream(1, 1, batch * 3, 1010);
            now += 50;
        }
        // RoomHost order: clearing the finished attempt reports a reset first.
        hub.onAttemptReset(1, 1);
        hub.onAttemptFinished(1, 1);

        streamAllExceptSlotOne(0);
        hub.tick();
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), aggregateSlots(me));
        assertEquals(GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY, entryFor(me, 1).frameCount());

        me.binary.clear();
        now += 50;
        streamAllExceptSlotOne(3);
        hub.tick();
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), aggregateSlots(me),
                "a silent sender stays relevant while its buffered tail drains");
        assertEquals(GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY,
                entryFor(me, 1).startFrameIndex());
        assertEquals(45 - GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY,
                entryFor(me, 1).frameCount());

        me.binary.clear();
        now += 50;
        streamAllExceptSlotOne(6);
        hub.tick();
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), aggregateSlots(me),
                "once drained, the next-nearest sender takes the parked finisher's place");
    }

    @Test
    void silentSenderLeavesHysteresisAndReentersOnlyWithinEnterDistance() {
        // Batch positions end two pixels on; steps stay under the validator speed ceiling.
        int inside = 1000 + RelevanceClassifier.NEAR_ENTER_PX - 10;
        int between = 1000 + RelevanceClassifier.NEAR_ENTER_PX + 20;
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, inside);
        hub.tick();
        assertEquals(List.of(1), aggregateSlots(me));
        me.binary.clear();
        now += 50;
        stream(1, 1, 3, between);
        hub.tick();
        assertEquals(List.of(1), aggregateSlots(me), "hysteresis keeps a running peer");

        hub.onAttemptReset(1, 1);
        hub.tick();
        me.binary.clear();
        now += 50;
        hub.onAttemptStart(1, 2);
        stream(1, 2, 0, between);
        hub.tick();
        assertTrue(aggregateSlots(me).isEmpty(),
                "a retry outside the enter distance must not reuse stale hysteresis");
        now += 50;
        stream(1, 2, 3, inside);
        hub.tick();
        assertEquals(List.of(1), aggregateSlots(me));
    }

    @Test
    void silentRecipientKeepsItsPositionAndStillReceivesNearSenders() {
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1200);
        hub.tick();
        hub.onAttemptReset(0, 1);
        hub.onAttemptFinished(0, 1);
        me.binary.clear();
        now += 50;
        stream(1, 1, 3, 1200);
        hub.tick();
        assertEquals(List.of(1), aggregateSlots(me), "a finished player may spectate nearby runners");
    }

    @Test
    void degradedCapGivesAParkedFinishersPlaceToTheFifthNearestSender() {
        FakeHubConnection degraded = new FakeHubConnection() {
            @Override public int queuedBytes() { return GhostHub.BP_DEGRADE_BYTES + 1; }
        };
        hub.addPlayer(0, "degraded", degraded);
        hub.onAttemptStart(0, 1);
        for (int slot = 4; slot <= 10; slot++) {
            hub.addPlayer(slot, "fp-" + slot, new FakeHubConnection());
            hub.onAttemptStart(slot, 1);
        }
        stream(0, 1, 0, 1000);
        for (int slot = 1; slot <= 10; slot++) stream(slot, 1, 0, 1250 - slot * 10);
        // Nearest slot 10 finishes after its last batch, which still drains first.
        hub.onAttemptReset(10, 1);
        hub.onAttemptFinished(10, 1);
        hub.tick();
        assertEquals(List.of(7, 8, 9, 10), aggregateSlots(degraded));
        degraded.binary.clear();
        now += 50;
        for (int slot = 1; slot <= 9; slot++) stream(slot, 1, 3, 1250 - slot * 10);
        hub.tick();
        assertEquals(List.of(6, 7, 8, 9), aggregateSlots(degraded),
                "unlike a running sender between batches, a drained finisher frees its place");
    }

    @Test
    void senderSilencedLastRoundRejoinsNearSetsAfterTrackChange() {
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1200);
        hub.tick();
        hub.onAttemptReset(1, 1);
        hub.onAttemptFinished(1, 1);
        me.binary.clear();
        now += 50;
        stream(0, 1, 3, 1000);
        stream(2, 1, 0, 1300);
        hub.tick();
        assertEquals(List.of(2), aggregateSlots(me), "the drained finisher holds no near place");

        hub.setTrack("s3k", 0, 0);
        for (int slot = 0; slot < 4; slot++) hub.onAttemptStart(slot, 1);
        me.binary.clear();
        now += 50;
        stream(0, 1, 0, 1000);
        stream(1, 1, 0, 1200);
        hub.tick();
        assertEquals(List.of(1), aggregateSlots(me), "a new round starts every sender running");
    }

    @Test
    void attemptLifecycleDrivesRosterStatusUntilNextAcceptedBatch() {
        stream(1, 1, 0, 1200);
        assertEquals(GhostPackets.ROSTER_STATUS_RUNNING, rosterEntryAfterInterval(1).status());

        hub.onAttemptReset(1, 1);
        GhostPackets.RosterEntry reset = rosterEntryAfterInterval(1);
        assertEquals(GhostPackets.ROSTER_STATUS_IDLE, reset.status());
        assertEquals(1202 >>> 6, reset.cellX(), "a silent sender keeps its last coarse position");
        hub.onAttemptStart(1, 2);
        assertEquals(GhostPackets.ROSTER_STATUS_IDLE, rosterEntryAfterInterval(1).status(),
                "only an accepted batch shows the sender running again");
        stream(1, 2, 0, 1300);
        assertEquals(GhostPackets.ROSTER_STATUS_RUNNING, rosterEntryAfterInterval(1).status());

        hub.onAttemptReset(1, 2);
        hub.onAttemptFinished(1, 2);
        assertEquals(GhostPackets.ROSTER_STATUS_FINISHED, rosterEntryAfterInterval(1).status());
        hub.onAttemptStart(1, 3);
        stream(1, 3, 0, 1000);
        assertEquals(GhostPackets.ROSTER_STATUS_RUNNING, rosterEntryAfterInterval(1).status());

        hub.onAttemptReset(1, 3);
        hub.onAttemptFinished(1, 3);
        hub.setTrack("s3k", 0, 0);
        GhostPackets.RosterEntry nextRound = rosterEntryAfterInterval(1);
        assertEquals(GhostPackets.ROSTER_STATUS_IDLE, nextRound.status());
        assertEquals(0, nextRound.cellX());
    }

    private void streamAllExceptSlotOne(int startIndex) {
        stream(0, 1, startIndex, 1000);
        // Slot s sits s * 10 px from slot 0, so slot 9 is the ninth-nearest sender.
        for (int slot = 2; slot <= 9; slot++) stream(slot, 1, startIndex, 1000 + slot * 10);
    }

    private GhostPackets.RosterEntry rosterEntryAfterInterval(int slot) {
        for (int tick = 0; tick < GhostHub.ROSTER_INTERVAL_TICKS; tick++) {
            hub.tick();
        }
        return lastRoster(me).stream().filter(entry -> entry.playerSlot() == slot)
                .findFirst().orElseThrow();
    }

    private static GhostPackets.AggregateEntry entryFor(FakeHubConnection connection, int slot) {
        return connection.binary.stream()
                .filter(packet -> (packet[0] & 0xFF) == GhostPackets.TYPE_GHOST_AGGREGATE)
                .flatMap(packet -> GhostPackets.decodeAggregate(packet).entries().stream())
                .filter(entry -> entry.playerSlot() == slot).findFirst().orElseThrow();
    }

    private static List<Integer> aggregateSlots(FakeHubConnection connection) {
        return connection.binary.stream()
                .filter(packet -> (packet[0] & 0xFF) == GhostPackets.TYPE_GHOST_AGGREGATE)
                .flatMap(packet -> GhostPackets.decodeAggregate(packet).entries().stream())
                .map(GhostPackets.AggregateEntry::playerSlot).toList();
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
