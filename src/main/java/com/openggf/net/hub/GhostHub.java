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

/** Single-threaded ingest and 20 Hz aggregation core for cosmetic ghost streams. */
@com.openggf.game.ModApi
public final class GhostHub {
    @FunctionalInterface
    @com.openggf.game.ModApi
    public interface HubViolationRecorder {
        void record(int slot, String fingerprint, String kind, String detail);
    }

    public static final int MAX_PENDING_FRAMES = 600;
    public static final int ROSTER_INTERVAL_TICKS = 20;
    public static final int BP_DEGRADE_BYTES = 64 * 1024;
    public static final int BP_ROSTER_ONLY_BYTES = 256 * 1024;
    public static final int BP_DISCONNECT_BYTES = 1024 * 1024;
    public static final long BP_SUSTAINED_MILLIS = 30_000;
    public static final int BP_DEGRADED_NEAR_CAP = 4;

    private static final class Player {
        final String fingerprint;
        final HubConnection connection;
        final ArrayDeque<GhostPackets.FramesBatch> pending = new ArrayDeque<>();
        GhostStreamValidator validator;
        int pendingFrames;
        int totalViolations;
        // Finished or reset with no newer accepted batch; see tick().
        boolean silent;
        GhostFrame last, previous;
        int frameIndex, shoeOnset;
        long seenAt;

        Player(String fingerprint, HubConnection connection) {
            this.fingerprint = fingerprint;
            this.connection = connection;
        }
    }

    private final LongSupplier wallClockMillis;
    private final TrackValidationProfileSource profiles;
    private final HubViolationRecorder recorder;
    private final boolean relevanceFiltering;
    private final RelevanceClassifier classifier = new RelevanceClassifier();
    private final Map<Integer, Player> players = new TreeMap<>();
    private final Map<Integer, Integer> lastStatus = new TreeMap<>();
    private final Map<Integer, Long> degradedSinceMillis = new TreeMap<>();
    private TrackValidationProfile currentProfile;
    private int tickCount;

    public GhostHub(LongSupplier wallClockMillis, TrackValidationProfileSource profiles,
                    HubViolationRecorder recorder) {
        this(wallClockMillis, profiles, recorder, false);
    }

    public GhostHub(LongSupplier wallClockMillis, TrackValidationProfileSource profiles,
                    HubViolationRecorder recorder, boolean relevanceFiltering) {
        this.wallClockMillis = wallClockMillis;
        this.profiles = profiles;
        this.recorder = recorder;
        this.relevanceFiltering = relevanceFiltering;
    }

    public void setTrack(String gameId, int zone, int act) {
        currentProfile = profiles.profileFor(gameId, zone, act).orElse(null);
        classifier.geometry(currentProfile);
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            resetPlayerStream(entry.getKey(), entry.getValue());
        }
    }

    void setTrack(com.openggf.net.protocol.TrackKey key) {
        setTrack(key.gameId(), key.zone(), key.act());
        if (profiles instanceof KeyedTrackValidationProfileSource keyed) applyProfile(keyed.profileFor(key).orElse(null));
    }

    public void applyProfile(TrackValidationProfile profileOrNull) {
        currentProfile = profileOrNull;
        classifier.geometry(currentProfile);
        for (Player player : players.values()) {
            player.validator.updateProfile(profileOrNull);
        }
    }

    public void addPlayer(int slot, String fingerprint, HubConnection connection) {
        Player player = new Player(fingerprint, connection);
        players.put(slot, player);
        resetPlayerStream(slot, player);
    }

    public void removePlayer(int slot) {
        players.remove(slot);
        classifier.remove(slot);
        lastStatus.remove(slot);
        degradedSinceMillis.remove(slot);
    }

    public void onBinary(int slot, byte[] packet) {
        Player player = players.get(slot);
        if (player == null) {
            return;
        }
        final GhostPackets.FramesBatch batch;
        try {
            batch = GhostPackets.decodeFrames(packet);
        } catch (ProtocolViolationException e) {
            recordViolation(slot, player, "undecodable", e.getMessage());
            closeIfThresholdReached(player);
            return;
        }
        GhostStreamValidator.Verdict verdict = player.validator.onBatch(batch);
        switch (verdict) {
            case ACCEPT, ACCEPT_FLAGGED -> {
                buffer(player, batch);
                player.silent = false;
                GhostFrame lastFrame = GhostFrameCodec.decode(batch.frameData(),
                        (batch.frameCount() - 1) * GhostFrameCodec.BYTES);
                byte[] data = batch.frameData();
                for (int i = 0; i < batch.frameCount(); i++) {
                    GhostFrame f = GhostFrameCodec.decode(data, i * GhostFrameCodec.BYTES);
                    if (player.last == null || player.last.shoeKind() != f.shoeKind()) player.shoeOnset = batch.startFrameIndex() + i;
                    player.previous = player.last; player.last = f;
                }
                player.frameIndex = batch.startFrameIndex() + batch.frameCount() - 1;
                player.seenAt = wallClockMillis.getAsLong();
                classifier.updatePosition(slot, lastFrame.x(), lastFrame.y());
                lastStatus.put(slot, (lastFrame.progress() << 4) | (lastFrame.finished()
                        ? GhostPackets.ROSTER_STATUS_FINISHED
                        : GhostPackets.ROSTER_STATUS_RUNNING));
            }
            case DROP -> closeIfThresholdReached(player);
            case KICK -> player.connection.close("ghost stream violations");
        }
    }

    public void tick() {
        tickCount++;
        if (relevanceFiltering) {
            classifier.rebucket();
        }
        Map<Integer, List<GhostPackets.AggregateEntry>> drained = new TreeMap<>();
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            List<GhostPackets.AggregateEntry> entries = drain(entry.getKey(), entry.getValue());
            if (!entries.isEmpty()) {
                drained.put(entry.getKey(), entries);
            }
            if (relevanceFiltering) {
                // A finished or reset sender still relays its buffered tail; once
                // nothing is left it stops holding other recipients' near slots.
                classifier.setSenderExcluded(entry.getKey(),
                        entry.getValue().silent && entries.isEmpty());
            }
        }
        int[] relevantSenders = relevanceFiltering ? new int[RelevanceClassifier.NEAR_CAP] : null;
        for (Map.Entry<Integer, Player> recipient : List.copyOf(players.entrySet())) {
            int queued = recipient.getValue().connection.queuedBytes();
            long nowMillis = wallClockMillis.getAsLong();
            if (queued > BP_DISCONNECT_BYTES) {
                dropSlowConsumer(recipient.getKey(), recipient.getValue());
                continue;
            }
            if (queued > BP_DEGRADE_BYTES) {
                long since = degradedSinceMillis.computeIfAbsent(
                        recipient.getKey(), ignored -> nowMillis);
                if (nowMillis - since >= BP_SUSTAINED_MILLIS) {
                    dropSlowConsumer(recipient.getKey(), recipient.getValue());
                    continue;
                }
            } else {
                degradedSinceMillis.remove(recipient.getKey());
            }
            if (queued > BP_ROSTER_ONLY_BYTES) {
                continue;
            }
            List<GhostPackets.AggregateEntry> aggregate = new ArrayList<>();
            int senderCap = queued > BP_DEGRADE_BYTES
                    ? BP_DEGRADED_NEAR_CAP : Integer.MAX_VALUE;
            if (relevanceFiltering) {
                int[] nearest = classifier.nearSlotsFor(recipient.getKey());
                int count = Math.min(nearest.length, senderCap);
                // Membership/capping uses nearest-first order; wire entries retain
                // ascending sender slots, as with the unfiltered drained TreeMap.
                System.arraycopy(nearest, 0, relevantSenders, 0, count);
                Arrays.sort(relevantSenders, 0, count);
                for (int index = 0; index < count; index++) {
                    List<GhostPackets.AggregateEntry> entries = drained.get(relevantSenders[index]);
                    if (entries != null) {
                        aggregate.addAll(entries);
                    }
                }
            } else {
                // Only drained senders are visited, so a silent sender with no
                // buffered tail never occupies one of the capped sender places.
                int includedSenders = 0;
                for (Map.Entry<Integer, List<GhostPackets.AggregateEntry>> sender
                        : drained.entrySet()) {
                    if (!sender.getKey().equals(recipient.getKey())) {
                        aggregate.addAll(sender.getValue());
                        if (++includedSenders >= senderCap) {
                            break;
                        }
                    }
                }
            }
            if (!aggregate.isEmpty()) {
                sendAggregate(recipient.getValue().connection, tickCount, aggregate);
            }
        }
        if (relevanceFiltering && tickCount % ROSTER_INTERVAL_TICKS == 0) {
            sendRoster();
        }
    }

    private static void sendAggregate(HubConnection connection, int hubTick,
                                      List<GhostPackets.AggregateEntry> entries) {
        byte[] packet;
        try {
            packet = GhostPackets.encodeAggregate(hubTick, entries);
        } catch (ProtocolViolationException failure) {
            // Trusted entries can exceed collection caps after several short
            // attempts accumulate. Intrinsic entry errors must still propagate.
            if (entries.size() < 2
                    || (!"aggregate entry count".equals(failure.getMessage())
                    && !"aggregate exceeds binary cap".equals(failure.getMessage()))) {
                throw failure;
            }
            packet = null;
        }
        if (packet == null || packet.length > Protocol.MAX_BINARY_BYTES) {
            if (entries.size() < 2) {
                throw new ProtocolViolationException("aggregate exceeds relay payload cap");
            }
            int middle = entries.size() / 2;
            sendAggregate(connection, hubTick, entries.subList(0, middle));
            sendAggregate(connection, hubTick, entries.subList(middle, entries.size()));
            return;
        }
        // Transport failures are not encoding-capacity failures and must never
        // trigger a retry/split after a packet may already have been submitted.
        connection.sendBinary(packet);
    }

    public boolean isAttemptFlagged(int slot) {
        Player player = players.get(slot);
        return player != null && player.validator.isAttemptFlagged();
    }

    public void onAttemptStart(int slot, int attemptId) {
        Player player = players.get(slot);
        if (player != null) {
            player.last = null; player.previous = null;
            player.validator.onAttemptStart(attemptId);
        }
    }

    /**
     * The host reports only the member's active attempt. The sender stays
     * silent, shown {@code IDLE} in the roster, until its next accepted batch.
     */
    public void onAttemptReset(int slot, int attemptId) {
        Player player = players.get(slot);
        if (player != null) {
            player.validator.onAttemptReset(attemptId);
            markSilent(slot, player, GhostPackets.ROSTER_STATUS_IDLE);
        }
    }

    /**
     * Called after the host has cleared the finished attempt (which already
     * reported a reset), so a finished sender ends {@code FINISHED}, not
     * {@code IDLE}, until its next accepted batch.
     */
    public void onAttemptFinished(int slot, int attemptId) {
        Player player = players.get(slot);
        if (player != null) {
            markSilent(slot, player, GhostPackets.ROSTER_STATUS_FINISHED);
            // Small rooms do not use periodic relevance rosters. Publish this
            // authoritative coarse lifecycle transition immediately so clients
            // can observe completion without relying on unverified standings.
            sendRoster();
        }
    }

    private void markSilent(int slot, Player player, int rosterStatus) {
        player.silent = true;
        lastStatus.put(slot, (lastStatus.getOrDefault(slot, 0) & 0xF0) | rosterStatus);
    }

    public boolean hasFinishEvidence(int slot, int attemptId, int finishFrame,
                                     String claimedStreamHashHex) {
        Player player = players.get(slot);
        if (player == null) {
            return false;
        }
        boolean accepted = player.validator.hasFinishEvidence(
                attemptId, finishFrame, claimedStreamHashHex);
        closeIfThresholdReached(player);
        return accepted;
    }

    TrackValidationProfile profile() { return currentProfile; }
    HostTeleportSwap.Observation observation(int slot) {
        Player p = players.get(slot);
        if (p == null || p.last == null || p.silent || p.validator.isAttemptFlagged()) return null;
        GhostFrame f = p.last, prev = p.previous == null ? f : p.previous;
        int dx = TrackRules.delta(f.x() - prev.x(), currentProfile == null ? 0 : currentProfile.wrapWidthPx());
        int dy = TrackRules.delta(f.y() - prev.y(), currentProfile == null ? 0 : currentProfile.wrapHeightPx());
        var target = new com.openggf.net.protocol.ControlMessage.SwapTarget(f.x(), f.y(), Math.clamp(dx * 256, -4096, 4096),
                Math.clamp(dy * 256, -4096, 4096), f.hFlip(), f.secondaryPath(), f.priorityBucket(),
                f.highPriority(), f.shoeKind(), f.shoeKind() == 0 ? 0 : Math.max(0, 75 - (p.frameIndex - p.shoeOnset) / 8), f.progress());
        double progress = f.progress() + (currentProfile != null && currentProfile.wrapWidthPx() > 0
                ? Math.floorMod(f.x(), currentProfile.wrapWidthPx()) / (double) currentProfile.wrapWidthPx()
                : f.x() / (double) Math.max(1, currentProfile == null ? 65536 : currentProfile.levelWidthPx()));
        return new HostTeleportSwap.Observation(slot, p.frameIndex, p.seenAt, f.hurt() || f.finished(), progress, target);
    }
    void admitSwap(int slot, com.openggf.net.protocol.ControlMessage.SwapTarget target, int first) {
        Player p = players.get(slot);
        if (p != null) p.validator.admitSwap(target, first);
    }

    public int tickCount() {
        return tickCount;
    }

    private void resetPlayerStream(int slot, Player player) {
        player.totalViolations = 0;
        player.validator = new GhostStreamValidator(currentProfile, wallClockMillis,
                (kind, detail) -> recordViolation(slot, player, kind, detail));
        player.pending.clear();
        player.pendingFrames = 0;
        player.silent = false;
        classifier.remove(slot);
        lastStatus.remove(slot);
    }

    private void recordViolation(int slot, Player player, String kind, String detail) {
        player.totalViolations++;
        recorder.record(slot, player.fingerprint, kind, detail);
    }

    private void closeIfThresholdReached(Player player) {
        if (player.totalViolations >= GhostStreamValidator.KICK_THRESHOLD) {
            player.connection.close("ghost stream violations");
        }
    }

    private static void buffer(Player player, GhostPackets.FramesBatch batch) {
        player.pending.addLast(batch);
        player.pendingFrames += batch.frameCount();
        while (player.pendingFrames > MAX_PENDING_FRAMES) {
            player.pendingFrames -= player.pending.removeFirst().frameCount();
        }
    }

    private static List<GhostPackets.AggregateEntry> drain(int slot, Player player) {
        List<GhostPackets.AggregateEntry> entries = new ArrayList<>();
        while (!player.pending.isEmpty()) {
            GhostPackets.FramesBatch head = player.pending.peekFirst();
            int attemptId = head.attemptId();
            int startIndex = head.startFrameIndex();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int frames = 0;
            int nextIndex = startIndex;
            while (!player.pending.isEmpty()) {
                GhostPackets.FramesBatch next = player.pending.peekFirst();
                if (next.attemptId() != attemptId || next.startFrameIndex() != nextIndex
                        || frames + next.frameCount()
                        > GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY) {
                    break;
                }
                player.pending.removeFirst();
                player.pendingFrames -= next.frameCount();
                bytes.writeBytes(next.frameData());
                frames += next.frameCount();
                nextIndex += next.frameCount();
            }
            if (frames == 0) {
                splitOversizedHead(slot, player, entries);
                break;
            }
            entries.add(new GhostPackets.AggregateEntry(
                    slot, attemptId, startIndex, frames, bytes.toByteArray()));
            if (frames >= GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY) {
                break;
            }
        }
        return entries;
    }

    private static void splitOversizedHead(int slot, Player player,
                                           List<GhostPackets.AggregateEntry> entries) {
        GhostPackets.FramesBatch big = player.pending.removeFirst();
        player.pendingFrames -= big.frameCount();
        int take = GhostPackets.MAX_AGGREGATE_FRAMES_PER_ENTRY;
        int frameBytes = GhostFrameCodec.BYTES;
        byte[] data = big.frameData();
        byte[] first = java.util.Arrays.copyOfRange(data, 0, take * frameBytes);
        byte[] rest = java.util.Arrays.copyOfRange(data, take * frameBytes, data.length);
        entries.add(new GhostPackets.AggregateEntry(
                slot, big.attemptId(), big.startFrameIndex(), take, first));
        player.pending.addFirst(new GhostPackets.FramesBatch(
                big.attemptId(), big.startFrameIndex() + take,
                big.frameCount() - take, rest));
        player.pendingFrames += big.frameCount() - take;
    }

    private void sendRoster() {
        List<GhostPackets.RosterEntry> roster = new ArrayList<>();
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            RelevanceClassifier.Pos position = classifier.positionOf(entry.getKey());
            roster.add(new GhostPackets.RosterEntry(entry.getKey(),
                    position == null ? 0 : clamp(position.x() >> 6, 0, 0xFFFF),
                    position == null ? 0 : clamp(position.y() >> 6, 0, 0xFF),
                    lastStatus.getOrDefault(entry.getKey(),
                            GhostPackets.ROSTER_STATUS_IDLE)));
        }
        if (roster.isEmpty()) {
            return;
        }
        byte[] packet = GhostPackets.encodeRoster(roster);
        for (Player player : players.values()) {
            boolean degraded = player.connection.queuedBytes() > BP_DEGRADE_BYTES;
            if (!degraded || tickCount % (2 * ROSTER_INTERVAL_TICKS) == 0) {
                player.connection.sendBinary(packet);
            }
        }
    }

    private void dropSlowConsumer(int slot, Player player) {
        player.connection.close("slow consumer");
        removePlayer(slot);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
