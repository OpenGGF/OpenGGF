package com.openggf.net.protocol;

import com.openggf.game.ghost.GhostFrameCodec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Binary ghost packets using the canonical seven-byte ghost-frame encoding. */
public final class GhostPackets {
    public static final int TYPE_GHOST_FRAMES = 0x01;
    public static final int TYPE_GHOST_AGGREGATE = 0x02;
    public static final int TYPE_ROSTER = 0x03;
    public static final int TYPE_RELAY_GUEST_BINARY = 0x04;

    public static final int ROSTER_STATUS_IDLE = 0;
    public static final int ROSTER_STATUS_RUNNING = 1;
    public static final int ROSTER_STATUS_FINISHED = 2;

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

    public record RosterEntry(int playerSlot, int cellX, int cellY, int status) {
    }

    public record RelayGuestBinary(int guestId, byte[] payload) {
        public RelayGuestBinary {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
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

    public static byte[] encodeRoster(List<RosterEntry> entries) {
        if (entries == null || entries.size() > Protocol.MAX_PLAYERS_RELAY) {
            throw new ProtocolViolationException("roster entry count");
        }
        ByteBuffer out = ByteBuffer.allocate(3 + entries.size() * 5);
        out.put((byte) TYPE_ROSTER).putShort((short) entries.size());
        for (RosterEntry entry : entries) {
            requireRange(entry.playerSlot(), 0xFF, "roster slot");
            requireRange(entry.cellX(), 0xFFFF, "roster cellX");
            requireRange(entry.cellY(), 0xFF, "roster cellY");
            requireRange(entry.status(), ROSTER_STATUS_FINISHED, "roster status");
            out.put((byte) entry.playerSlot()).putShort((short) entry.cellX())
                    .put((byte) entry.cellY()).put((byte) entry.status());
        }
        return out.array();
    }

    public static List<RosterEntry> decodeRoster(byte[] packet) {
        ByteBuffer in = checked(packet, TYPE_ROSTER, 3);
        int count = in.getShort() & 0xFFFF;
        if (count > Protocol.MAX_PLAYERS_RELAY || in.remaining() != count * 5) {
            throw new ProtocolViolationException(
                    "roster length mismatch for " + count + " entries");
        }
        List<RosterEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slot = in.get() & 0xFF;
            int cellX = in.getShort() & 0xFFFF;
            int cellY = in.get() & 0xFF;
            int status = in.get() & 0xFF;
            if (status > ROSTER_STATUS_FINISHED) {
                throw new ProtocolViolationException(
                        "roster status out of range: " + status);
            }
            entries.add(new RosterEntry(slot, cellX, cellY, status));
        }
        return List.copyOf(entries);
    }

    public static byte[] encodeRelayGuestBinary(int guestId, byte[] payload) {
        requireRange(guestId, 0xFFFF, "relay guest id");
        if (payload == null || payload.length == 0
                || payload.length > Protocol.MAX_BINARY_BYTES - 3) {
            throw new ProtocolViolationException(
                    "relay payload length " + (payload == null ? -1 : payload.length));
        }
        ByteBuffer out = ByteBuffer.allocate(3 + payload.length);
        out.put((byte) TYPE_RELAY_GUEST_BINARY).putShort((short) guestId).put(payload);
        return out.array();
    }

    public static RelayGuestBinary decodeRelayGuestBinary(byte[] packet) {
        ByteBuffer in = checked(packet, TYPE_RELAY_GUEST_BINARY, 3);
        int guestId = in.getShort() & 0xFFFF;
        if (!in.hasRemaining()) {
            throw new ProtocolViolationException("relay wrapper has empty payload");
        }
        byte[] payload = new byte[in.remaining()];
        in.get(payload);
        return new RelayGuestBinary(guestId, payload);
    }

    private static void requireRange(int value, int maxInclusive, String field) {
        if (value < 0 || value > maxInclusive) {
            throw new ProtocolViolationException(field + " out of range: " + value);
        }
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
