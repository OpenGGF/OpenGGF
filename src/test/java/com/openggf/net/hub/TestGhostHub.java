package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostHub {
    static final class FakeConnection implements HubConnection {
        final List<byte[]> binary = new ArrayList<>();
        final List<String> text = new ArrayList<>();
        String closedReason;

        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] value) { binary.add(value); }
        @Override public void close(String reason) { closedReason = reason; }
        @Override public String remoteHost() { return "127.0.0.1"; }
    }

    private long now = 50_000;
    private final List<String> recorded = new ArrayList<>();
    private GhostHub hub;
    private final FakeConnection a = new FakeConnection();
    private final FakeConnection b = new FakeConnection();
    private final FakeConnection c = new FakeConnection();

    @BeforeEach
    void setUp() {
        hub = new GhostHub(() -> now, TrackValidationProfileSource.none(),
                (slot, fp, kind, detail) -> recorded.add(slot + ":" + kind));
        hub.setTrack("s3k", 0, 0);
        hub.addPlayer(0, "fp-a", a);
        hub.addPlayer(1, "fp-b", b);
        hub.addPlayer(2, "fp-c", c);
        hub.onAttemptStart(0, 1);
        hub.onAttemptStart(1, 1);
        hub.onAttemptStart(2, 1);
    }

    private static byte[] frames(int startX, int count) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(startX + i * 2, 256, 1,
                    false, false, false, 2, false), data, i * GhostFrameCodec.BYTES);
        }
        return data;
    }

    @Test
    void tickSendsEachRecipientEveryoneElsesFrames() {
        hub.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(100, 3)));
        hub.onBinary(1, GhostPackets.encodeFrames(1, 0, frames(500, 3)));
        hub.tick();

        GhostPackets.Aggregate forA = GhostPackets.decodeAggregate(a.binary.get(0));
        assertEquals(1, forA.entries().size());
        assertEquals(1, forA.entries().get(0).playerSlot());
        GhostPackets.Aggregate forC = GhostPackets.decodeAggregate(c.binary.get(0));
        assertEquals(2, forC.entries().size());
    }

    @Test
    void idleRecipientsGetNoPacketAndPendingClearsAfterTick() {
        hub.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(100, 3)));
        hub.tick();
        assertTrue(a.binary.isEmpty());
        assertEquals(1, b.binary.size());
        hub.tick();
        assertEquals(1, b.binary.size());
    }

    @Test
    void contiguousBatchesMergeIntoOneEntry() {
        hub.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(100, 3)));
        hub.onBinary(0, GhostPackets.encodeFrames(1, 3, frames(106, 3)));
        hub.tick();
        GhostPackets.Aggregate forB = GhostPackets.decodeAggregate(b.binary.get(0));
        assertEquals(1, forB.entries().size());
        assertEquals(6, forB.entries().get(0).frameCount());
        assertEquals(0, forB.entries().get(0).startFrameIndex());
    }

    @Test
    void undecodableBinaryIsRecordedAndDropped() {
        hub.onBinary(0, new byte[] {0x7F, 1, 2});
        hub.tick();
        assertTrue(b.binary.isEmpty());
        assertEquals(List.of("0:undecodable"), recorded);
    }

    @Test
    void repeatedUndecodableBinaryKicks() {
        for (int i = 0; i < GhostStreamValidator.KICK_THRESHOLD; i++) {
            hub.onBinary(0, new byte[] {0x7F, 1, 2});
        }
        assertEquals("ghost stream violations", a.closedReason);
    }

    @Test
    void kickClosesConnection() {
        hub.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(100, 3)));
        for (int i = 0; i < GhostStreamValidator.KICK_THRESHOLD; i++) {
            hub.onBinary(0, GhostPackets.encodeFrames(1, 999, frames(100, 3)));
        }
        assertNotNull(a.closedReason);
        assertTrue(recorded.stream().allMatch(value -> value.startsWith("0:")));
    }

    @Test
    void removedPlayerNeitherSendsNorReceives() {
        hub.removePlayer(1);
        hub.onBinary(0, GhostPackets.encodeFrames(1, 0, frames(100, 3)));
        hub.tick();
        assertTrue(b.binary.isEmpty());
        GhostPackets.Aggregate forC = GhostPackets.decodeAggregate(c.binary.get(0));
        assertEquals(1, forC.entries().size());
    }

    @Test
    void perEntryFrameCapLeavesRemainderPending() {
        for (int i = 0; i < 15; i++) {
            hub.onBinary(0,
                    GhostPackets.encodeFrames(1, i * 3, frames(100 + i * 6, 3)));
            now += 50;
        }
        hub.tick();
        GhostPackets.Aggregate first = GhostPackets.decodeAggregate(b.binary.get(0));
        assertEquals(GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY,
                first.entries().get(0).frameCount());
        hub.tick();
        GhostPackets.Aggregate second = GhostPackets.decodeAggregate(b.binary.get(1));
        assertEquals(45 - GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY,
                second.entries().get(0).frameCount());
        assertEquals(GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY,
                second.entries().get(0).startFrameIndex());
    }
}
