package com.openggf.net.protocol;

import com.openggf.net.ghost.GhostFrameCodec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
    void aggregateEncodingPreservesWireBytesAndAllPublicOwnershipBoundaries() {
        byte[] source = frames(3);
        byte[] original = source.clone();
        var entry = new GhostPackets.AggregateEntry(255, 17, 300, 3, source);
        source[0] ^= 0x7f;
        byte[] exposed = entry.frameData();
        exposed[1] ^= 0x7f;

        byte[] expected = java.nio.ByteBuffer.allocate(6 + 10 + original.length)
                .put((byte) 2).putInt(1234).put((byte) 1)
                .put((byte) 255).putInt(17).putInt(300).put((byte) 3)
                .put(original).array();
        byte[] encoded = GhostPackets.encodeAggregate(1234, List.of(entry));
        assertArrayEquals(expected, encoded);
        encoded[encoded.length - 1] ^= 0x7f;
        assertArrayEquals(original, entry.frameData());
        assertArrayEquals(expected, GhostPackets.encodeAggregate(1234, List.of(entry)));

        var decoded = GhostPackets.decodeAggregate(expected).entries().getFirst();
        expected[expected.length - 1] ^= 0x7f;
        byte[] decodedData = decoded.frameData();
        decodedData[0] ^= 0x7f;
        assertArrayEquals(original, decoded.frameData());
    }

    @Test
    void packetLocalCompressionSavesBytesForStationaryAndMovingFrames() {
        byte[] stationary = motion(3, 0, 0, false);
        byte[] moving = motion(3, 4, -2, false);
        byte[] animated = motion(3, 4, -2, true);
        assertEquals(19, GhostPackets.encodeFrames(1, 0, stationary).length);
        assertEquals(23, GhostPackets.encodeFrames(1, 0, moving).length);
        assertEquals(25, GhostPackets.encodeFrames(1, 0, animated).length);
        assertEquals(0x83, GhostPackets.encodeFrames(1, 0, moving)[9] & 0xFF);

        assertEquals(52, aggregatePacket(motion(30, 0, 0, false)).length);
        assertEquals(110, aggregatePacket(motion(30, 4, -2, false)).length);
        assertEquals(139, aggregatePacket(motion(30, 4, -2, true)).length);
        assertArrayEquals(moving, GhostPackets.decodeFrames(
                GhostPackets.encodeFrames(1, 0, moving)).frameData());
    }

    @Test
    void usesRawFallbackWhenCompressionDoesNotSaveBytes() {
        byte[] raw = frames(3);
        byte[] packet = GhostPackets.encodeFrames(1, 0, raw);
        assertEquals(31, packet.length);
        assertEquals(3, packet[9]);
        assertArrayEquals(raw, Arrays.copyOfRange(packet, 10, packet.length));
        assertEquals(1, GhostPackets.encodeFrames(1, 0, frames(1))[9]);
    }

    @Test
    void reconstructsAbsoluteChangesSignedDeltasWrapsAndOpaqueFrameBytes() {
        byte[] data = ByteBuffer.allocate(21)
                .putShort((short) 0).putShort((short) 65535)
                .put((byte) 1).put((byte) 0xFF).put((byte) 0xFE)
                .putShort((short) 65535).putShort((short) 65407)
                .put((byte) 2).put((byte) 0x80).put((byte) 0x90)
                .putShort((short) 4000).putShort((short) 65407)
                .put((byte) 2).put((byte) 0x80).put((byte) 0x90).array();
        // First transition has signed -1/-128 deltas, including coordinate wrap.
        // Second transition requires an absolute x coordinate and unchanged y.
        byte[] packet = GhostPackets.encodeFrames(1, 0, data);
        assertEquals(0x83, packet[9] & 0xFF);
        assertEquals(0x75, packet[17] & 0xFF);
        assertEquals(2, packet[23]);
        assertArrayEquals(data, GhostPackets.decodeFrames(packet).frameData());
    }

    @Test
    void randomFrameStreamsRoundTripWithoutIncreasingWireSize() {
        Random random = new Random(0x434f4d5052455353L);
        for (int round = 0; round < 500; round++) {
            int count = 1 + random.nextInt(30);
            byte[] data = motion(count, random.nextInt(257) - 128,
                    random.nextInt(257) - 128, random.nextBoolean());
            for (int frame = 1; frame < count; frame++) {
                if (random.nextInt(4) == 0) {
                    random.nextBytes(data); // Exercise incompressible/opaque data too.
                    break;
                }
            }
            byte[] packet = aggregatePacket(data);
            assertTrue(packet.length <= 16 + data.length);
            var decoded = GhostPackets.decodeAggregate(packet).entries().getFirst();
            assertEquals(count, decoded.frameCount());
            assertArrayEquals(data, decoded.frameData());
            if (count <= 3) {
                byte[] upstream = GhostPackets.encodeFrames(19, 123, data);
                assertTrue(upstream.length <= 10 + data.length);
                assertArrayEquals(data, GhostPackets.decodeFrames(upstream).frameData());
            }
        }
    }

    @Test
    void compressedPacketsRejectReservedFlagsModesTruncationAndTrailingBytes() {
        byte[] good = GhostPackets.encodeFrames(1, 0, motion(3, 4, 2, true));
        for (int length = 0; length < good.length; length++) {
            byte[] truncated = Arrays.copyOf(good, length);
            assertThrows(ProtocolViolationException.class,
                    () -> GhostPackets.decodeFrames(truncated), "length " + length);
        }
        for (int countByte : new int[]{0x80, 0x84, 0xA3, 0xC3, 0xE3}) {
            byte[] bad = good.clone();
            bad[9] = (byte) countByte;
            assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeFrames(bad));
        }
        for (int mask : new int[]{0x80, 0x03, 0x0C, 0xFF}) {
            byte[] bad = good.clone();
            bad[17] = (byte) mask;
            assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeFrames(bad));
        }
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeFrames(trailing));
        byte[] aggregate = aggregatePacket(motion(30, 0, 0, false));
        byte[] truncated = Arrays.copyOf(aggregate, aggregate.length - 1);
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.decodeAggregate(truncated));
        byte[] aggregateTrailing = Arrays.copyOf(aggregate, aggregate.length + 1);
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeAggregate(aggregateTrailing));
    }

    @Test
    void compressionRetainsCanonicalAggregateResourceLimit() {
        byte[] data = motion(30, 0, 0, false);
        List<GhostPackets.AggregateEntry> entries = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            entries.add(new GhostPackets.AggregateEntry(i, 1, 0, 30, data));
        }
        assertEquals(18, GhostPackets.decodeAggregate(
                GhostPackets.encodeAggregate(1, entries)).entries().size());
        entries.add(new GhostPackets.AggregateEntry(18, 1, 0, 30, data));
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.encodeAggregate(1, entries));

        byte[] encodedEntry = Arrays.copyOfRange(aggregatePacket(data), 6, 52);
        ByteBuffer hostile = ByteBuffer.allocate(6 + 19 * encodedEntry.length)
                .put((byte) 2).putInt(1).put((byte) 19);
        for (int i = 0; i < 19; i++) {
            hostile.put(encodedEntry);
        }
        assertTrue(hostile.array().length < Protocol.MAX_BINARY_BYTES);
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeAggregate(hostile.array()));
    }

    private static byte[] aggregatePacket(byte[] data) {
        return GhostPackets.encodeAggregate(1, List.of(
                new GhostPackets.AggregateEntry(0, 1, 0, data.length / 7, data)));
    }

    private static byte[] motion(int count, int dx, int dy, boolean animate) {
        ByteBuffer data = ByteBuffer.allocate(count * GhostFrameCodec.BYTES);
        for (int frame = 0; frame < count; frame++) {
            data.putShort((short) (1000 + frame * dx)).putShort((short) (2000 + frame * dy))
                    .put((byte) (animate ? frame : 7)).put((byte) 0).put((byte) 0);
        }
        return data.array();
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
