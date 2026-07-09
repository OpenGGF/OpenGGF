package com.openggf.net.protocol;

import com.openggf.game.ghost.GhostFrameCodec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Binary ghost packets using the canonical seven-byte ghost-frame encoding. */
public final class GhostPackets {
    public static final int TYPE_GHOST_FRAMES = 0x01;
    public static final int TYPE_GHOST_AGGREGATE = 0x02;
    public static final int TYPE_ROSTER_RESERVED = 0x03;

    public static final int MAX_UPSTREAM_FRAMES_PER_PACKET = 3;
    public static final int MAX_AGGREGATE_FRAMES_PER_ENTRY = 30;

    private static final int FRAME_BYTES = GhostFrameCodec.BYTES;
    private static final int FRAMES_HEADER = 1 + 4 + 4 + 1;
    private static final int ENTRY_HEADER = 1 + 4 + 4 + 1;
    private static final int AGGREGATE_HEADER = 1 + 4 + 1;

    public record FramesBatch(int attemptId, int startFrameIndex, int frameCount,
                              byte[] frameData) {
        public FramesBatch {
            frameData = frameData.clone();
        }

        @Override
        public byte[] frameData() {
            return frameData.clone();
        }
    }

    public record AggregateEntry(int playerSlot, int attemptId, int startFrameIndex,
                                 int frameCount, byte[] frameData) {
        public AggregateEntry {
            frameData = frameData.clone();
        }

        @Override
        public byte[] frameData() {
            return frameData.clone();
        }
    }

    public record Aggregate(int hubTick, List<AggregateEntry> entries) {
        public Aggregate {
            entries = List.copyOf(entries);
        }
    }

    private GhostPackets() {
    }

    public static byte[] encodeFrames(int attemptId, int startFrameIndex, byte[] frameData) {
        int count = validFrameCount(frameData, MAX_UPSTREAM_FRAMES_PER_PACKET);
        ByteBuffer out = ByteBuffer.allocate(FRAMES_HEADER + frameData.length);
        out.put((byte) TYPE_GHOST_FRAMES)
                .putInt(attemptId)
                .putInt(startFrameIndex)
                .put((byte) count)
                .put(frameData);
        return out.array();
    }

    public static FramesBatch decodeFrames(byte[] packet) {
        ByteBuffer in = checked(packet, TYPE_GHOST_FRAMES, FRAMES_HEADER);
        int attemptId = in.getInt();
        int startFrameIndex = in.getInt();
        int count = in.get() & 0xFF;
        if (count < 1 || count > MAX_UPSTREAM_FRAMES_PER_PACKET) {
            throw new ProtocolViolationException("frames batch count " + count);
        }
        byte[] frameData = takeExactly(in, count);
        requireExhausted(in, "frames packet");
        return new FramesBatch(attemptId, startFrameIndex, count, frameData);
    }

    public static byte[] encodeAggregate(int hubTick, List<AggregateEntry> entries) {
        if (entries == null || entries.size() > 255) {
            throw new ProtocolViolationException("aggregate entry count");
        }
        int size = AGGREGATE_HEADER;
        for (AggregateEntry entry : entries) {
            int count = validFrameCount(
                    entry.frameData(), MAX_AGGREGATE_FRAMES_PER_ENTRY);
            if (entry.frameCount() != count) {
                throw new ProtocolViolationException("aggregate frame count mismatch");
            }
            if (entry.playerSlot() < 0 || entry.playerSlot() > 255) {
                throw new ProtocolViolationException("aggregate player slot " + entry.playerSlot());
            }
            size += ENTRY_HEADER + entry.frameData().length;
            if (size > Protocol.MAX_BINARY_BYTES) {
                throw new ProtocolViolationException("aggregate exceeds binary cap");
            }
        }
        ByteBuffer out = ByteBuffer.allocate(size);
        out.put((byte) TYPE_GHOST_AGGREGATE).putInt(hubTick).put((byte) entries.size());
        for (AggregateEntry entry : entries) {
            out.put((byte) entry.playerSlot())
                    .putInt(entry.attemptId())
                    .putInt(entry.startFrameIndex())
                    .put((byte) entry.frameCount())
                    .put(entry.frameData());
        }
        return out.array();
    }

    public static Aggregate decodeAggregate(byte[] packet) {
        ByteBuffer in = checked(packet, TYPE_GHOST_AGGREGATE, AGGREGATE_HEADER);
        int hubTick = in.getInt();
        int entryCount = in.get() & 0xFF;
        List<AggregateEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            if (in.remaining() < ENTRY_HEADER) {
                throw new ProtocolViolationException("aggregate truncated at entry " + i);
            }
            int slot = in.get() & 0xFF;
            int attemptId = in.getInt();
            int startFrameIndex = in.getInt();
            int count = in.get() & 0xFF;
            if (count < 1 || count > MAX_AGGREGATE_FRAMES_PER_ENTRY) {
                throw new ProtocolViolationException("aggregate entry count " + count);
            }
            entries.add(new AggregateEntry(
                    slot, attemptId, startFrameIndex, count, takeExactly(in, count)));
        }
        requireExhausted(in, "aggregate");
        return new Aggregate(hubTick, entries);
    }

    private static int validFrameCount(byte[] frameData, int max) {
        if (frameData == null || frameData.length < FRAME_BYTES
                || frameData.length % FRAME_BYTES != 0
                || frameData.length / FRAME_BYTES > max) {
            throw new ProtocolViolationException(
                    "frame data length " + (frameData == null ? -1 : frameData.length));
        }
        return frameData.length / FRAME_BYTES;
    }

    private static ByteBuffer checked(byte[] packet, int expectedType, int minLength) {
        if (packet == null || packet.length < minLength
                || packet.length > Protocol.MAX_BINARY_BYTES) {
            throw new ProtocolViolationException(
                    "binary packet length " + (packet == null ? -1 : packet.length));
        }
        if ((packet[0] & 0xFF) != expectedType) {
            throw new ProtocolViolationException(
                    "unexpected packet type " + (packet[0] & 0xFF));
        }
        ByteBuffer in = ByteBuffer.wrap(packet);
        in.get();
        return in;
    }

    private static byte[] takeExactly(ByteBuffer in, int frameCount) {
        int byteCount = Math.multiplyExact(frameCount, FRAME_BYTES);
        if (in.remaining() < byteCount) {
            throw new ProtocolViolationException(
                    "packet truncated: need " + byteCount + " frame bytes");
        }
        byte[] frameData = new byte[byteCount];
        in.get(frameData);
        return frameData;
    }

    private static void requireExhausted(ByteBuffer in, String kind) {
        if (in.hasRemaining()) {
            throw new ProtocolViolationException(kind + " has trailing bytes");
        }
    }
}
