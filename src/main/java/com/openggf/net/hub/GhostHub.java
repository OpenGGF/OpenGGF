package com.openggf.net.hub;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/** Single-threaded ingest and 20 Hz aggregation core for cosmetic ghost streams. */
public final class GhostHub {
    @FunctionalInterface
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
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            resetPlayerStream(entry.getKey(), entry.getValue());
        }
    }

    public void applyProfile(TrackValidationProfile profileOrNull) {
        currentProfile = profileOrNull;
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
                GhostFrame lastFrame = GhostFrameCodec.decode(batch.frameData(),
                        (batch.frameCount() - 1) * GhostFrameCodec.BYTES);
                classifier.updatePosition(slot, lastFrame.x(), lastFrame.y());
                lastStatus.put(slot, lastFrame.finished()
                        ? GhostPackets.ROSTER_STATUS_FINISHED
                        : GhostPackets.ROSTER_STATUS_RUNNING);
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
        }
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
            Set<Integer> nearSet = relevanceFiltering
                    ? classifier.nearSetFor(recipient.getKey()) : Set.of();
            if (relevanceFiltering && queued > BP_DEGRADE_BYTES
                    && nearSet.size() > BP_DEGRADED_NEAR_CAP) {
                nearSet = nearSet.stream().limit(BP_DEGRADED_NEAR_CAP)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            int senderCap = queued > BP_DEGRADE_BYTES
                    ? BP_DEGRADED_NEAR_CAP : Integer.MAX_VALUE;
            int includedSenders = 0;
            for (Map.Entry<Integer, List<GhostPackets.AggregateEntry>> sender
                    : drained.entrySet()) {
                boolean relevant = !relevanceFiltering || nearSet.contains(sender.getKey());
                if (!sender.getKey().equals(recipient.getKey()) && relevant
                        && includedSenders < senderCap) {
                    aggregate.addAll(sender.getValue());
                    includedSenders++;
                }
            }
            if (!aggregate.isEmpty()) {
                recipient.getValue().connection.sendBinary(
                        GhostPackets.encodeAggregate(tickCount, aggregate));
            }
        }
        if (relevanceFiltering && tickCount % ROSTER_INTERVAL_TICKS == 0) {
            sendRoster();
        }
    }

    public boolean isAttemptFlagged(int slot) {
        Player player = players.get(slot);
        return player != null && player.validator.isAttemptFlagged();
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
        int frameBytes = com.openggf.game.ghost.GhostFrameCodec.BYTES;
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
