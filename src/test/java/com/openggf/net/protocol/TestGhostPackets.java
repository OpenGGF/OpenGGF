package com.openggf.net.protocol;

import com.openggf.game.ghost.GhostFrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostPackets {
    private static byte[] frames(int n) {
        byte[] data = new byte[n * GhostFrameCodec.BYTES];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    @Test
    void roundTripsFramesBatch() {
        byte[] packet = GhostPackets.encodeFrames(7, 300, frames(3));
        assertEquals(0x01, packet[0]);
        GhostPackets.FramesBatch back = GhostPackets.decodeFrames(packet);
        assertEquals(7, back.attemptId());
        assertEquals(300, back.startFrameIndex());
        assertEquals(3, back.frameCount());
        assertArrayEquals(frames(3), back.frameData());
    }

    @Test
    void roundTripsAggregate() {
        List<GhostPackets.AggregateEntry> entries = List.of(
                new GhostPackets.AggregateEntry(0, 7, 300, 3, frames(3)),
                new GhostPackets.AggregateEntry(5, 2, 0, 1, frames(1)));
        byte[] packet = GhostPackets.encodeAggregate(1234, entries);
        assertEquals(0x02, packet[0]);
        GhostPackets.Aggregate back = GhostPackets.decodeAggregate(packet);
        assertEquals(1234, back.hubTick());
        assertEquals(2, back.entries().size());
        assertEquals(5, back.entries().get(1).playerSlot());
        assertArrayEquals(frames(1), back.entries().get(1).frameData());
    }

    @Test
    void emptyAggregateIsLegal() {
        GhostPackets.Aggregate back = GhostPackets.decodeAggregate(
                GhostPackets.encodeAggregate(1, List.of()));
        assertTrue(back.entries().isEmpty());
    }

    @Test
    void encodeFramesRejectsBadSizes() {
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.encodeFrames(1, 0, new byte[0]));
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.encodeFrames(1, 0, new byte[8]));
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.encodeFrames(1, 0, frames(4)));
    }

    @Test
    void encodeAggregateRejectsMismatchedCountAndOutOfRangeSlot() {
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.encodeAggregate(1, List.of(
                        new GhostPackets.AggregateEntry(0, 1, 0, 2, frames(1)))));
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.encodeAggregate(1, List.of(
                        new GhostPackets.AggregateEntry(256, 1, 0, 1, frames(1)))));
    }

    @Test
    void decodeRejectsHostileInput() {
        byte[] good = GhostPackets.encodeFrames(7, 300, frames(3));
        byte[] wrongType = good.clone();
        wrongType[0] = 0x02;
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeFrames(wrongType));

        byte[] hostileCount = good.clone();
        hostileCount[9] = (byte) 200;
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeFrames(hostileCount));

        byte[] truncated = java.util.Arrays.copyOf(good, good.length - 3);
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeFrames(truncated));

        byte[] trailing = java.util.Arrays.copyOf(good, good.length + 2);
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeFrames(trailing));

        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeAggregate(new byte[Protocol.MAX_BINARY_BYTES + 1]));
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeAggregate(new byte[0]));
    }
}
