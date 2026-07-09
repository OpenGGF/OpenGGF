package com.openggf.net.hub;

import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/** Single-threaded ingest and 20 Hz aggregation core for cosmetic ghost streams. */
public final class GhostHub {
    @FunctionalInterface
    public interface HubViolationRecorder {
        void record(int slot, String fingerprint, String kind, String detail);
    }

    public static final int MAX_PENDING_FRAMES = 600;

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
    private final Map<Integer, Player> players = new TreeMap<>();
    private TrackValidationProfile currentProfile;
    private int tickCount;

    public GhostHub(LongSupplier wallClockMillis, TrackValidationProfileSource profiles,
                    HubViolationRecorder recorder) {
        this.wallClockMillis = wallClockMillis;
        this.profiles = profiles;
        this.recorder = recorder;
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
            case ACCEPT, ACCEPT_FLAGGED -> buffer(player, batch);
            case DROP -> closeIfThresholdReached(player);
            case KICK -> player.connection.close("ghost stream violations");
        }
    }

    public void tick() {
        tickCount++;
        Map<Integer, List<GhostPackets.AggregateEntry>> drained = new TreeMap<>();
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            List<GhostPackets.AggregateEntry> entries = drain(entry.getKey(), entry.getValue());
            if (!entries.isEmpty()) {
                drained.put(entry.getKey(), entries);
            }
        }
        for (Map.Entry<Integer, Player> recipient : players.entrySet()) {
            List<GhostPackets.AggregateEntry> aggregate = new ArrayList<>();
            for (Map.Entry<Integer, List<GhostPackets.AggregateEntry>> sender
                    : drained.entrySet()) {
                if (!sender.getKey().equals(recipient.getKey())) {
                    aggregate.addAll(sender.getValue());
                }
            }
            if (!aggregate.isEmpty()) {
                recipient.getValue().connection.sendBinary(
                        GhostPackets.encodeAggregate(tickCount, aggregate));
            }
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
}
