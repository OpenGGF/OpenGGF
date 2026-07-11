package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Authoritative lobby, countdown, race-window, round-end, and standings state. */
@com.openggf.game.ModApi
public final class HostRoundEngine {
    @com.openggf.game.ModApi
    public enum Phase { LOBBY, COUNTDOWN, RUNNING, ROUND_END, VOTE }

    public static final long COUNTDOWN_MILLIS = 3000;
    public static final long FINISH_GRACE_MILLIS = 2000;
    public static final long ROUND_END_LINGER_MILLIS = 10_000;
    public static final int STANDINGS_BROADCAST_CAP = 10;
    public static final long VOTE_WINDOW_MILLIS = 15_000;
    public static final int VOTE_OPTION_COUNT = 3;

    @com.openggf.game.ModApi
    public record FinishOutcome(int slot, int rank, boolean outsideBroadcastCap,
                                int attemptId) {
    }

    private record Best(String displayName, String character, int timeFrames,
                        long achievedOrder, ControlMessage.AttemptFinish finish,
                        String verifyState) {
    }

    private final LongSupplier hubClockMillis;
    private final Consumer<ControlMessage> broadcaster;
    private final Map<Integer, Best> bests = new LinkedHashMap<>();
    private final List<String> voteTrackPool = new ArrayList<>();
    private final Map<Integer, String> votesBySlot = new LinkedHashMap<>();

    private Phase phase = Phase.LOBBY;
    private ControlMessage.RoundConfig config;
    private long countdownEndsAt;
    private long deadline;
    private long roundEndAt;
    private long achievedCounter;
    private Predicate<String> knownTrack;
    private int voteRotation;
    private List<String> voteOptions = List.of();
    private long voteEndsAt;
    private ControlMessage.RoundConfig votedNextConfig;
    private boolean verifiedRoom;
    private long pendingHoldMillis = 10_000;
    private BiConsumer<Integer, Integer> pendingExpiryListener = (slot, attempt) -> { };

    public HostRoundEngine(LongSupplier hubClockMillis,
                           Consumer<ControlMessage> broadcaster) {
        this.hubClockMillis = hubClockMillis;
        this.broadcaster = broadcaster;
    }

    public Phase phase() {
        return phase;
    }

    public boolean startRound(ControlMessage.RoundConfig newConfig) {
        if (phase != Phase.LOBBY
                && (phase != Phase.ROUND_END || !voteTrackPool.isEmpty())) {
            return false;
        }
        long now = hubClockMillis.getAsLong();
        config = newConfig;
        countdownEndsAt = now + COUNTDOWN_MILLIS;
        deadline = countdownEndsAt + newConfig.windowSeconds() * 1000L;
        bests.clear();
        achievedCounter = 0;
        votedNextConfig = null;
        phase = Phase.COUNTDOWN;
        broadcaster.accept(new ControlMessage.RoundStart(config, countdownEndsAt, deadline));
        return true;
    }

    public void onTick() {
        long now = hubClockMillis.getAsLong();
        if (phase == Phase.COUNTDOWN && now >= countdownEndsAt) {
            phase = Phase.RUNNING;
        }
        if (phase == Phase.RUNNING && now > deadline) {
            phase = Phase.ROUND_END;
            roundEndAt = now;
            broadcaster.accept(new ControlMessage.RoundEnd(broadcastStandings()));
        }
        if (phase == Phase.ROUND_END
                && now >= roundEndAt + ROUND_END_LINGER_MILLIS) {
            if (pendingVerdictCount() > 0
                    && now < roundEndAt + ROUND_END_LINGER_MILLIS
                    + pendingHoldMillis) {
                return;
            }
            if (pendingVerdictCount() > 0) {
                List<Map.Entry<Integer, Best>> expired = bests.entrySet().stream()
                        .filter(entry -> "PENDING".equals(entry.getValue().verifyState()))
                        .toList();
                for (Map.Entry<Integer, Best> entry : expired) {
                    bests.remove(entry.getKey());
                    pendingExpiryListener.accept(entry.getKey(),
                            entry.getValue().finish().attemptId());
                }
                broadcaster.accept(new ControlMessage.StandingsDelta(
                        broadcastStandings()));
            }
            if (voteTrackPool.isEmpty()) {
                phase = Phase.LOBBY;
            } else {
                beginVote();
            }
        }
        if (phase == Phase.VOTE && now >= voteEndsAt) {
            closeVote();
        }
    }

    public void setVoteTrackPool(List<String> trackKeys) {
        setVoteTrackPool(trackKeys, null);
    }

    public void setVoteTrackPool(List<String> trackKeys, Predicate<String> knownTrack) {
        this.knownTrack = knownTrack;
        voteTrackPool.clear();
        if (trackKeys != null) {
            trackKeys.stream().filter(Objects::nonNull).distinct()
                    .forEach(voteTrackPool::add);
        }
    }

    public List<String> voteOptions() {
        return List.copyOf(voteOptions);
    }

    public ControlMessage.RoundConfig votedNextConfig() {
        return votedNextConfig;
    }

    public void setVerifiedRoom(boolean verified) {
        verifiedRoom = verified;
    }

    public void setPendingHoldMillis(long millis) {
        pendingHoldMillis = Math.max(0, millis);
    }

    public void setPendingExpiryListener(BiConsumer<Integer, Integer> listener) {
        pendingExpiryListener = listener == null ? (slot, attempt) -> { } : listener;
    }

    public int pendingVerdictCount() {
        return (int) bests.values().stream()
                .filter(best -> "PENDING".equals(best.verifyState())).count();
    }

    public ControlMessage.AttemptFinish bestFinish(int slot) {
        Best best = bests.get(slot);
        return best == null ? null : best.finish();
    }

    public void onVerdict(int slot, int attemptId, boolean pass) {
        Best best = bests.get(slot);
        if (best == null || !"PENDING".equals(best.verifyState())
                || best.finish().attemptId() != attemptId) {
            return;
        }
        if (pass) {
            bests.put(slot, new Best(best.displayName(), best.character(),
                    best.timeFrames(), best.achievedOrder(), best.finish(), "VERIFIED"));
        } else {
            bests.remove(slot);
        }
        broadcaster.accept(new ControlMessage.StandingsDelta(broadcastStandings()));
    }

    public void onTrackVote(int slot, String trackKey) {
        if (phase != Phase.VOTE || trackKey == null || !voteOptions.contains(trackKey)) {
            return;
        }
        votesBySlot.put(slot, trackKey);
        broadcaster.accept(new ControlMessage.TrackVoteTally(currentTally()));
    }

    public FinishOutcome onAttemptFinish(int slot, String displayName, String character,
                                         ControlMessage.AttemptFinish finish,
                                         boolean attemptFlagged) {
        long now = hubClockMillis.getAsLong();
        if (phase != Phase.RUNNING || now > deadline + FINISH_GRACE_MILLIS
                || attemptFlagged || finish.timeFrames() <= 0
                || finish.firstInputFrame() < 0
                || finish.finishFrame() < finish.firstInputFrame()
                || finish.finishFrame() - finish.firstInputFrame()
                != finish.timeFrames()) {
            return null;
        }
        Best existing = bests.get(slot);
        if (existing != null && existing.timeFrames() <= finish.timeFrames()) {
            return null;
        }
        bests.put(slot, new Best(displayName, character, finish.timeFrames(),
                achievedCounter++, finish, verifiedRoom ? "PENDING" : "NONE"));
        List<ControlMessage.StandingsRow> rows = standings();
        broadcaster.accept(new ControlMessage.StandingsDelta(broadcastStandings(rows)));
        int rank = rows.stream()
                .filter(row -> row.slot() == slot)
                .mapToInt(ControlMessage.StandingsRow::rank)
                .findFirst()
                .orElseThrow();
        return new FinishOutcome(slot, rank, rank > STANDINGS_BROADCAST_CAP,
                finish.attemptId());
    }

    public void onPlayerLeft(int slot) {
        // Best remains visible for the rest of the round.
    }

    public List<ControlMessage.StandingsRow> standings() {
        List<Map.Entry<Integer, Best>> sorted = new ArrayList<>(bests.entrySet());
        sorted.sort((left, right) -> {
            int byTime = Integer.compare(
                    left.getValue().timeFrames(), right.getValue().timeFrames());
            return byTime != 0 ? byTime : Long.compare(
                    left.getValue().achievedOrder(), right.getValue().achievedOrder());
        });
        List<ControlMessage.StandingsRow> rows = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            Map.Entry<Integer, Best> entry = sorted.get(index);
            Best best = entry.getValue();
            rows.add(new ControlMessage.StandingsRow(entry.getKey(), best.displayName(),
                    best.character(), best.timeFrames(), index + 1, best.verifyState()));
        }
        return List.copyOf(rows);
    }

    public List<ControlMessage.StandingsRow> page(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            return List.of();
        }
        List<ControlMessage.StandingsRow> rows = standings();
        long requestedStart = (long) page * pageSize;
        if (requestedStart >= rows.size()) {
            return List.of();
        }
        int start = (int) requestedStart;
        return List.copyOf(rows.subList(start, Math.min(rows.size(), start + pageSize)));
    }

    public int totalPages(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        int size = bests.size();
        return size == 0 ? 0 : (size + pageSize - 1) / pageSize;
    }

    public ControlMessage.RoundSnapshot snapshot() {
        return new ControlMessage.RoundSnapshot(
                phase.name(), config, countdownEndsAt, deadline, broadcastStandings());
    }

    private List<ControlMessage.StandingsRow> broadcastStandings() {
        return broadcastStandings(standings());
    }

    private static List<ControlMessage.StandingsRow> broadcastStandings(
            List<ControlMessage.StandingsRow> rows) {
        return List.copyOf(rows.subList(0,
                Math.min(STANDINGS_BROADCAST_CAP, rows.size())));
    }

    private void beginVote() {
        voteOptions = pickVoteOptions();
        if (voteOptions.size() < 2) {
            voteOptions = List.of();
            phase = Phase.LOBBY;
            return;
        }
        votesBySlot.clear();
        voteEndsAt = hubClockMillis.getAsLong() + VOTE_WINDOW_MILLIS;
        phase = Phase.VOTE;
        broadcaster.accept(new ControlMessage.TrackVoteOffer(voteOptions, voteEndsAt));
    }

    private List<String> pickVoteOptions() {
        List<String> candidates = voteTrackPool.stream()
                .filter(this::isEligibleVoteKey).toList();
        List<String> picked = new ArrayList<>();
        for (int index = 0; index < candidates.size()
                && picked.size() < VOTE_OPTION_COUNT; index++) {
            picked.add(candidates.get((voteRotation + index) % candidates.size()));
        }
        voteRotation = candidates.isEmpty() ? 0
                : (voteRotation + VOTE_OPTION_COUNT) % candidates.size();
        return List.copyOf(picked);
    }

    private boolean isEligibleVoteKey(String key) {
        if (key == null || key.equals(currentTrackKey()) || config == null) {
            return false;
        }
        String[] parts = key.split(":", -1);
        if (parts.length != 3 || !parts[0].equals(config.gameId())) {
            return false;
        }
        try {
            if (Integer.parseInt(parts[1]) < 0 || Integer.parseInt(parts[2]) < 0) {
                return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return knownTrack == null || knownTrack.test(key);
    }

    private List<ControlMessage.VoteCount> currentTally() {
        List<ControlMessage.VoteCount> counts = new ArrayList<>(voteOptions.size());
        for (String option : voteOptions) {
            int count = (int) votesBySlot.values().stream().filter(option::equals).count();
            counts.add(new ControlMessage.VoteCount(option, count));
        }
        return List.copyOf(counts);
    }

    private void closeVote() {
        String winner = null;
        int bestVotes = 0;
        for (String option : voteOptions) {
            int count = (int) votesBySlot.values().stream().filter(option::equals).count();
            if (count > bestVotes) {
                bestVotes = count;
                winner = option;
            }
        }
        if (winner != null) {
            String[] parts = winner.split(":");
            votedNextConfig = new ControlMessage.RoundConfig(parts[0],
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    config.windowSeconds(), config.characterPolicy(), config.lockedCharacter());
        }
        broadcaster.accept(new ControlMessage.TrackVoteResult(
                winner == null ? currentTrackKey() : winner));
        voteOptions = List.of();
        votesBySlot.clear();
        phase = Phase.LOBBY;
    }

    private String currentTrackKey() {
        return config == null ? "" : config.gameId() + ":" + config.zone() + ":" + config.act();
    }
}
