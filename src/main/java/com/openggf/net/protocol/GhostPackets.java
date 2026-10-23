package com.openggf.net.protocol;

import com.openggf.net.ghost.GhostFrameCodec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Binary ghost packets with packet-local compression of canonical seven-byte frames.
 * Every entry starts from an absolute frame; decoding never depends on another packet.
 */
@com.openggf.game.ModApi
public final class GhostPackets {
    public static final int TYPE_GHOST_FRAMES = 0x01;
    public static final int TYPE_GHOST_AGGREGATE = 0x02;
    public static final int TYPE_ROSTER = 0x03;
    public static final int TYPE_RELAY_GUEST_BINARY = 0x04;
    /**
     * Client to host between rounds: the sender's lobby-level pose, in the frames layout. Its
     * {@code attemptId} names a lobby stream (a new one per lobby level load or teleport).
     */
    public static final int TYPE_LOBBY_FRAMES = 0x05;
    /** Host to clients between rounds: nearby players' lobby poses, in the aggregate layout. */
    public static final int TYPE_LOBBY_AGGREGATE = 0x06;

    public static final int ROSTER_STATUS_IDLE = 0;
    public static final int ROSTER_STATUS_RUNNING = 1;
    public static final int ROSTER_STATUS_FINISHED = 2;

    public static final int MAX_UPSTREAM_FRAMES_PER_PACKET = 3;
    public static final int MAX_AGGREGATE_FRAMES_PER_ENTRY = 30;

    private static final int FRAME_BYTES = GhostFrameCodec.BYTES;
    // Low five bits retain the count (1..30). Bits 5 and 6 are reserved.
    private static final int COMPRESSED = 0x80;
    private static final int COUNT_MASK = 0x1F;
    private static final int RESERVED_COUNT_BITS = 0x60;
    // Each subsequent frame has x/y modes in bits 0..3, changed bytes in 4..6.
    // Coordinate modes: 0 = same, 1 = signed-byte delta, 2 = absolute unsigned short.
    private static final int SAME = 0;
    private static final int DELTA = 1;
    private static final int ABSOLUTE = 2;
    private static final int FRAMES_HEADER = 1 + 4 + 4 + 1;
    private static final int ENTRY_HEADER = 1 + 4 + 4 + 1;
    private static final int AGGREGATE_HEADER = 1 + 4 + 1;

    @com.openggf.game.ModApi
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

    @com.openggf.game.ModApi
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

    @com.openggf.game.ModApi
    public record Aggregate(int hubTick, List<AggregateEntry> entries) {
        public Aggregate {
            entries = List.copyOf(entries);
        }
    }

    @com.openggf.game.ModApi
    public record RosterEntry(int playerSlot, int cellX, int cellY, int status) {
    }

    @com.openggf.game.ModApi
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
        return encodeFrames(TYPE_GHOST_FRAMES, attemptId, startFrameIndex, frameData);
    }

    public static FramesBatch decodeFrames(byte[] packet) {
        return decodeFrames(TYPE_GHOST_FRAMES, packet);
    }

    /** A lobby pose batch: the frames layout under {@link #TYPE_LOBBY_FRAMES}. */
    public static byte[] encodeLobbyFrames(int streamId, int startFrameIndex, byte[] frameData) {
        return encodeFrames(TYPE_LOBBY_FRAMES, streamId, startFrameIndex, frameData);
    }

    public static FramesBatch decodeLobbyFrames(byte[] packet) {
        return decodeFrames(TYPE_LOBBY_FRAMES, packet);
    }

    /** Nearby lobby poses: the aggregate layout under {@link #TYPE_LOBBY_AGGREGATE}. */
    public static byte[] encodeLobbyAggregate(int hubTick, List<AggregateEntry> entries) {
        return encodeAggregate(TYPE_LOBBY_AGGREGATE, hubTick, entries);
    }

    public static Aggregate decodeLobbyAggregate(byte[] packet) {
        return decodeAggregate(TYPE_LOBBY_AGGREGATE, packet);
    }

    private static byte[] encodeFrames(int type, int attemptId, int startFrameIndex, byte[] frameData) {
        int count = validFrameCount(frameData, MAX_UPSTREAM_FRAMES_PER_PACKET);
        int encodedBytes = encodedFrameBytes(frameData);
        ByteBuffer out = ByteBuffer.allocate(FRAMES_HEADER + encodedBytes);
        out.put((byte) type).putInt(attemptId).putInt(startFrameIndex);
        putFrameData(out, count, frameData, encodedBytes);
        return out.array();
    }

    private static FramesBatch decodeFrames(int type, byte[] packet) {
        ByteBuffer in = checked(packet, type, FRAMES_HEADER);
        int attemptId = in.getInt();
        int startFrameIndex = in.getInt();
        int countByte = in.get() & 0xFF;
        int count = decodeFrameCount(countByte, MAX_UPSTREAM_FRAMES_PER_PACKET);
        byte[] frameData = takeFrameData(in, count, countByte);
        requireExhausted(in, "frames packet");
        return new FramesBatch(attemptId, startFrameIndex, count, frameData);
    }

    public static byte[] encodeAggregate(int hubTick, List<AggregateEntry> entries) {
        return encodeAggregate(TYPE_GHOST_AGGREGATE, hubTick, entries);
    }

    public static Aggregate decodeAggregate(byte[] packet) {
        return decodeAggregate(TYPE_GHOST_AGGREGATE, packet);
    }

    private static byte[] encodeAggregate(int type, int hubTick, List<AggregateEntry> entries) {
        if (entries == null || entries.size() > 255) {
            throw new ProtocolViolationException("aggregate entry count");
        }
        int size = AGGREGATE_HEADER;
        int canonicalSize = AGGREGATE_HEADER;
        for (AggregateEntry entry : entries) {
            // The enclosing codec can read the record's frozen internal array.
            // Its public constructor and accessor still copy at caller boundaries.
            int count = validFrameCount(
                    entry.frameData, MAX_AGGREGATE_FRAMES_PER_ENTRY);
            if (entry.frameCount() != count) {
                throw new ProtocolViolationException("aggregate frame count mismatch");
            }
            if (entry.playerSlot() < 0 || entry.playerSlot() > 255) {
                throw new ProtocolViolationException("aggregate player slot " + entry.playerSlot());
            }
            size += ENTRY_HEADER + encodedFrameBytes(entry.frameData);
            canonicalSize += ENTRY_HEADER + entry.frameData.length;
            if (canonicalSize > Protocol.MAX_BINARY_BYTES) {
                throw new ProtocolViolationException("aggregate exceeds binary cap");
            }
        }
        ByteBuffer out = ByteBuffer.allocate(size);
        out.put((byte) type).putInt(hubTick).put((byte) entries.size());
        for (AggregateEntry entry : entries) {
            out.put((byte) entry.playerSlot())
                    .putInt(entry.attemptId())
                    .putInt(entry.startFrameIndex());
            putFrameData(out, entry.frameCount(), entry.frameData,
                    encodedFrameBytes(entry.frameData));
        }
        return out.array();
    }

    private static Aggregate decodeAggregate(int type, byte[] packet) {
        ByteBuffer in = checked(packet, type, AGGREGATE_HEADER);
        int hubTick = in.getInt();
        int entryCount = in.get() & 0xFF;
        List<AggregateEntry> entries = new ArrayList<>(entryCount);
        int canonicalSize = AGGREGATE_HEADER;
        for (int i = 0; i < entryCount; i++) {
            if (in.remaining() < ENTRY_HEADER) {
                throw new ProtocolViolationException("aggregate truncated at entry " + i);
            }
            int slot = in.get() & 0xFF;
            int attemptId = in.getInt();
            int startFrameIndex = in.getInt();
            int countByte = in.get() & 0xFF;
            int count = decodeFrameCount(countByte, MAX_AGGREGATE_FRAMES_PER_ENTRY);
            canonicalSize += ENTRY_HEADER + count * FRAME_BYTES;
            if (canonicalSize > Protocol.MAX_BINARY_BYTES) {
                throw new ProtocolViolationException("aggregate exceeds canonical binary cap");
            }
            entries.add(new AggregateEntry(slot, attemptId, startFrameIndex, count,
                    takeFrameData(in, count, countByte)));
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

    private static int decodeFrameCount(int countByte, int maximum) {
        int count = countByte & COUNT_MASK;
        if ((countByte & RESERVED_COUNT_BITS) != 0 || count < 1 || count > maximum) {
            throw new ProtocolViolationException("frame count/flags " + countByte);
        }
        return count;
    }

    private static int coordinate(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int coordinateMode(byte[] data, int offset) {
        int difference = (short) (coordinate(data, offset) - coordinate(data, offset - FRAME_BYTES));
        return difference == 0 ? SAME : difference >= -128 && difference <= 127 ? DELTA : ABSOLUTE;
    }

    private static int control(byte[] data, int offset) {
        int mask = coordinateMode(data, offset) | (coordinateMode(data, offset + 2) << 2);
        for (int field = 4; field < FRAME_BYTES; field++) {
            if (data[offset + field] != data[offset + field - FRAME_BYTES]) {
                mask |= 1 << field;
            }
        }
        return mask;
    }

    private static int encodedFrameBytes(byte[] data) {
        int size = FRAME_BYTES;
        for (int offset = FRAME_BYTES; offset < data.length; offset += FRAME_BYTES) {
            int mask = control(data, offset);
            size += 1 + (mask & 3) + ((mask >>> 2) & 3) + Integer.bitCount(mask & 0x70);
        }
        return Math.min(size, data.length);
    }

    private static void putFrameData(ByteBuffer out, int count, byte[] data, int encodedBytes) {
        boolean compressed = encodedBytes < data.length;
        out.put((byte) (count | (compressed ? COMPRESSED : 0)));
        if (!compressed) {
            out.put(data);
            return;
        }
        out.put(data, 0, FRAME_BYTES);
        for (int offset = FRAME_BYTES; offset < data.length; offset += FRAME_BYTES) {
            int mask = control(data, offset);
            out.put((byte) mask);
            for (int field = 0; field <= 2; field += 2) {
                int mode = (mask >>> field) & 3;
                if (mode == DELTA) {
                    out.put((byte) (coordinate(data, offset + field)
                            - coordinate(data, offset + field - FRAME_BYTES)));
                } else if (mode == ABSOLUTE) {
                    out.put(data, offset + field, 2);
                }
            }
            for (int field = 4; field < FRAME_BYTES; field++) {
                if ((mask & (1 << field)) != 0) {
                    out.put(data[offset + field]);
                }
            }
        }
    }

    private static byte[] takeFrameData(ByteBuffer in, int count, int countByte) {
        if ((countByte & COMPRESSED) == 0) {
            return takeExactly(in, count);
        }
        requireRemaining(in, FRAME_BYTES);
        byte[] data = new byte[count * FRAME_BYTES];
        in.get(data, 0, FRAME_BYTES);
        for (int offset = FRAME_BYTES; offset < data.length; offset += FRAME_BYTES) {
            requireRemaining(in, 1);
            int mask = in.get() & 0xFF;
            int xMode = mask & 3;
            int yMode = (mask >>> 2) & 3;
            if ((mask & 0x80) != 0 || xMode == 3 || yMode == 3) {
                throw new ProtocolViolationException("reserved frame compression mode " + mask);
            }
            requireRemaining(in, xMode + yMode + Integer.bitCount(mask & 0x70));
            System.arraycopy(data, offset - FRAME_BYTES, data, offset, FRAME_BYTES);
            for (int field = 0; field <= 2; field += 2) {
                int mode = (mask >>> field) & 3;
                if (mode == DELTA) {
                    int value = coordinate(data, offset + field) + in.get();
                    data[offset + field] = (byte) (value >>> 8);
                    data[offset + field + 1] = (byte) value;
                } else if (mode == ABSOLUTE) {
                    in.get(data, offset + field, 2);
                }
            }
            for (int field = 4; field < FRAME_BYTES; field++) {
                if ((mask & (1 << field)) != 0) {
                    data[offset + field] = in.get();
                }
            }
        }
        return data;
    }

    private static void requireRemaining(ByteBuffer in, int bytes) {
        if (in.remaining() < bytes) {
            throw new ProtocolViolationException("truncated compressed frame data");
        }
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
