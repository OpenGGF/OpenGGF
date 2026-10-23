package com.openggf.net.hub;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Between-round lobby presence: validates each member's lobby pose stream
 * ({@link GhostPackets#TYPE_LOBBY_FRAMES}) and relays the poses near each recipient
 * ({@link GhostPackets#TYPE_LOBBY_AGGREGATE}) once per host tick. Poses are cosmetic and never
 * scored, so the relay keeps only a short tail per sender, skips congested recipients and is
 * cleared when a round starts. With relevance filtering (relay rooms of up to 256 players) a
 * recipient hears only its {@link RelevanceClassifier#NEAR_CAP} nearest senders.
 *
 * <p>A stream is one lobby-level visit: {@code attemptId} names it and must not go backwards; a
 * new stream starts at frame 0 and its frame indices then only move forward (a gap is frames that
 * were dropped), at no more than the sustained frame rate and with no jump above the global speed
 * ceiling between consecutive frames. A violation drops the batch without desynchronising the
 * stream; {@link GhostStreamValidator#KICK_THRESHOLD} of them asks the caller to close the member.
 * Single-threaded: the room thread owns it.
 */
final class LobbyPresenceRelay {
    static final int RATE_BURST_FRAMES = 90;
    static final int RATE_REFILL_PER_SECOND = GhostStreamValidator.RATE_REFILL_PER_SECOND;
    static final int MAX_PENDING_FRAMES = 60;

    private static final class Sender {
        final HubConnection connection;
        final ArrayDeque<GhostPackets.FramesBatch> pending = new ArrayDeque<>();
        int pendingFrames;
        int streamId = -1;
        int nextFrameIndex;
        GhostFrame previous;
        double tokens = RATE_BURST_FRAMES;
        long lastRefillMillis = Long.MIN_VALUE;
        int violations;

        Sender(HubConnection connection) {
            this.connection = connection;
        }
    }

    /** What happened to one received batch. */
    enum Verdict { ACCEPT, DROP, KICK }

    private final LongSupplier wallClockMillis;
    private final boolean relevanceFiltering;
    private final RelevanceClassifier classifier = new RelevanceClassifier();
    private final Map<Integer, Sender> senders = new TreeMap<>();
    private final Violations violations;
    private int tickCount;

    LobbyPresenceRelay(LongSupplier wallClockMillis, boolean relevanceFiltering, Violations violations) {
        this.wallClockMillis = wallClockMillis;
        this.relevanceFiltering = relevanceFiltering;
        this.violations = violations;
    }

    void addMember(int slot, HubConnection connection) {
        senders.put(slot, new Sender(connection));
        classifier.remove(slot);
    }

    void removeMember(int slot) {
        senders.remove(slot);
        classifier.remove(slot);
    }

    /** A round started: every lobby stream ends, and nothing buffered is relayed later. */
    void clear() {
        for (Map.Entry<Integer, Sender> entry : senders.entrySet()) {
            Sender sender = entry.getValue();
            sender.pending.clear();
            sender.pendingFrames = 0;
            sender.previous = null;
            classifier.remove(entry.getKey());
        }
    }

    Verdict onFrames(int slot, byte[] packet) {
        Sender sender = senders.get(slot);
        if (sender == null) {
            return Verdict.DROP;
        }
        GhostPackets.FramesBatch batch;
        try {
            batch = GhostPackets.decodeLobbyFrames(packet);
        } catch (ProtocolViolationException malformed) {
            return violate(slot, sender, "undecodable lobby frames: " + malformed.getMessage());
        }
        int stream = batch.attemptId();
        if (stream < 0 || stream < sender.streamId) {
            return violate(slot, sender, "lobby stream " + stream + " after " + sender.streamId);
        }
        if (stream > sender.streamId) {
            if (batch.startFrameIndex() != 0) {
                return violate(slot, sender, "lobby stream " + stream + " starts at "
                        + batch.startFrameIndex());
            }
            sender.streamId = stream;
            sender.nextFrameIndex = 0;
            sender.previous = null;
        } else if (batch.startFrameIndex() < sender.nextFrameIndex) {
            return violate(slot, sender, "lobby frame " + batch.startFrameIndex() + " repeats before "
                    + sender.nextFrameIndex);
        } else if (batch.startFrameIndex() > sender.nextFrameIndex) {
            // Frames the relay refused (or the sender skipped) leave a gap: the pose may have
            // moved further than one frame allows across it.
            sender.previous = null;
        }
        long now = wallClockMillis.getAsLong();
        if (sender.lastRefillMillis != Long.MIN_VALUE && now > sender.lastRefillMillis) {
            sender.tokens = Math.min(RATE_BURST_FRAMES,
                    sender.tokens + (now - sender.lastRefillMillis) * RATE_REFILL_PER_SECOND / 1000.0);
        }
        if (sender.lastRefillMillis == Long.MIN_VALUE || now > sender.lastRefillMillis) {
            sender.lastRefillMillis = now;
        }
        if (sender.tokens < batch.frameCount()) {
            // Refused frames still count as sent, so the stream continues after the gap.
            sender.nextFrameIndex = batch.startFrameIndex() + batch.frameCount();
            sender.previous = null;
            return violate(slot, sender, "lobby frame rate above " + RATE_REFILL_PER_SECOND + "/s");
        }
        byte[] data = batch.frameData();
        GhostFrame previous = sender.previous;
        GhostFrame last = null;
        for (int i = 0; i < batch.frameCount(); i++) {
            GhostFrame frame = GhostFrameCodec.decode(data, i * GhostFrameCodec.BYTES);
            if (previous != null && (Math.abs(frame.x() - previous.x())
                    > TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME
                    || Math.abs(frame.y() - previous.y())
                    > TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME)) {
                return violate(slot, sender, "lobby pose jump at frame " + (batch.startFrameIndex() + i));
            }
            previous = frame;
            last = frame;
        }
        sender.tokens -= batch.frameCount();
        sender.previous = previous;
        sender.nextFrameIndex = batch.startFrameIndex() + batch.frameCount();
        sender.pending.addLast(batch);
        sender.pendingFrames += batch.frameCount();
        while (sender.pendingFrames > MAX_PENDING_FRAMES) {
            sender.pendingFrames -= sender.pending.removeFirst().frameCount();
        }
        classifier.updatePosition(slot, last.x(), last.y());
        return Verdict.ACCEPT;
    }

    /** Sends each recipient the poses received since the last tick from the senders near it. */
    void tick() {
        tickCount++;
        if (relevanceFiltering) {
            classifier.rebucket();
        }
        Map<Integer, List<GhostPackets.AggregateEntry>> drained = new TreeMap<>();
        for (Map.Entry<Integer, Sender> entry : senders.entrySet()) {
            List<GhostPackets.AggregateEntry> entries = drain(entry.getKey(), entry.getValue());
            if (!entries.isEmpty()) {
                drained.put(entry.getKey(), entries);
            }
        }
        if (drained.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Sender> recipient : senders.entrySet()) {
            HubConnection connection = recipient.getValue().connection;
            // Presence is cosmetic: a congested recipient misses it rather than queueing more.
            if (connection.queuedBytes() > GhostHub.BP_DEGRADE_BYTES) {
                continue;
            }
            List<GhostPackets.AggregateEntry> aggregate = new ArrayList<>();
            if (relevanceFiltering) {
                int[] near = classifier.nearSlotsFor(recipient.getKey()).clone();
                Arrays.sort(near);
                for (int slot : near) {
                    List<GhostPackets.AggregateEntry> entries = drained.get(slot);
                    if (entries != null) aggregate.addAll(entries);
                }
            } else {
                for (Map.Entry<Integer, List<GhostPackets.AggregateEntry>> sender : drained.entrySet()) {
                    if (!sender.getKey().equals(recipient.getKey())) aggregate.addAll(sender.getValue());
                }
            }
            if (!aggregate.isEmpty()) {
                send(connection, tickCount, aggregate);
            }
        }
    }

    int violations(int slot) {
        Sender sender = senders.get(slot);
        return sender == null ? 0 : sender.violations;
    }

    private Verdict violate(int slot, Sender sender, String detail) {
        sender.violations++;
        violations.record(slot, detail);
        return sender.violations >= GhostStreamValidator.KICK_THRESHOLD ? Verdict.KICK : Verdict.DROP;
    }

    /** Contiguous batches of one stream merge into entries of at most the aggregate frame cap. */
    private static List<GhostPackets.AggregateEntry> drain(int slot, Sender sender) {
        List<GhostPackets.AggregateEntry> entries = new ArrayList<>();
        while (!sender.pending.isEmpty()) {
            GhostPackets.FramesBatch head = sender.pending.removeFirst();
            sender.pendingFrames -= head.frameCount();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.writeBytes(head.frameData());
            int frames = head.frameCount();
            while (!sender.pending.isEmpty()) {
                GhostPackets.FramesBatch next = sender.pending.peekFirst();
                if (next.attemptId() != head.attemptId()
                        || next.startFrameIndex() != head.startFrameIndex() + frames
                        || frames + next.frameCount() > GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY) {
                    break;
                }
                sender.pending.removeFirst();
                sender.pendingFrames -= next.frameCount();
                bytes.writeBytes(next.frameData());
                frames += next.frameCount();
            }
            entries.add(new GhostPackets.AggregateEntry(slot, head.attemptId(), head.startFrameIndex(),
                    frames, bytes.toByteArray()));
        }
        return entries;
    }

    /** One packet when it fits the relay payload cap (three bytes left for the tunnel envelope). */
    private static void send(HubConnection connection, int tick, List<GhostPackets.AggregateEntry> entries) {
        byte[] packet = null;
        if (entries.size() <= 255) {
            try {
                packet = GhostPackets.encodeLobbyAggregate(tick, entries);
            } catch (ProtocolViolationException tooLarge) {
                if (entries.size() < 2) throw tooLarge;
            }
        }
        if (packet == null || packet.length > Protocol.MAX_BINARY_BYTES - 3) {
            if (entries.size() < 2) {
                throw new ProtocolViolationException("lobby aggregate exceeds relay payload cap");
            }
            int middle = entries.size() / 2;
            send(connection, tick, entries.subList(0, middle));
            send(connection, tick, entries.subList(middle, entries.size()));
            return;
        }
        connection.sendBinary(packet);
    }

    /** Where a dropped batch is reported (the room logs it). */
    @FunctionalInterface
    interface Violations {
        void record(int slot, String detail);
    }
}
