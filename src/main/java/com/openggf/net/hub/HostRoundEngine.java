package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.TrackKey;

import java.util.ArrayList;
import java.util.Comparator;
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
    public enum RoundMode { TIME_ATTACK, COMPETITION }

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
                                int attemptId, String outcome) {
        public FinishOutcome(int slot, int rank, boolean outsideBroadcastCap, int attemptId) {
            this(slot, rank, outsideBroadcastCap, attemptId, "FINISHED");
        }
    }

    record Result(int slot, String participantId, String fingerprint,
                  String displayName, String character, int timeFrames,
                  ControlMessage.AttemptFinish finish, String verifyState) { }

    private record Best(int slot, String participantId, String fingerprint,
                        String displayName, String character, int timeFrames,
                        long achievedOrder, ControlMessage.AttemptFinish finish,
                        String verifyState) {
    }

    /** Standings order: faster time first, then the earlier accepted improvement. */
    private static final Comparator<Best> STANDING_ORDER = Comparator
            .comparingInt(Best::timeFrames).thenComparingLong(Best::achievedOrder);

    private final LongSupplier hubClockMillis;
    private final Consumer<ControlMessage> broadcaster;
    // Shown best per participant. In a verified room it is always the fastest of
    // the participant's verified fallback and its unresolved pending candidates.
    private final Map<String, Best> verifiedLapBests = new LinkedHashMap<>();
    private final Map<String, Best> lapBests = new LinkedHashMap<>();
    private final Map<String, Best> bests = new LinkedHashMap<>();
    private final Map<String, Best> verifiedBests = new LinkedHashMap<>();
    // Every accepted improvement still awaiting a verdict, in acceptance order. A
    // superseded claim keeps its candidate: its job still runs, and its PASS can
    // become the fallback when a later, faster claim fails.
    private final Map<String, List<Best>> pendingCandidates = new LinkedHashMap<>();
    private final Map<String, Integer> lastFinishedAttemptByParticipant = new LinkedHashMap<>();
    private final List<String> voteTrackPool = new ArrayList<>();
    private final Map<Integer, String> votesBySlot = new LinkedHashMap<>();

    private record Entrant(int slot, String id, String name, String character) { }
    private final Map<String, Entrant> entrants = new LinkedHashMap<>();
    private final Map<String, String> entrantIdentities = new LinkedHashMap<>();
    private final Map<String, Integer> started = new LinkedHashMap<>();
    private final Map<String, String> out = new LinkedHashMap<>();
    private TrackValidationProfile profile;
    private long cutoffAt = Long.MAX_VALUE;
    private int winnerFrames = -1;

    boolean competition() { return config != null && "COMPETITION".equals(config.mode()); }
    void profile(TrackValidationProfile value) { profile = value; }
    void register(int slot, String id, String name, String character) {
        register(slot, id, name, character, id);
    }
    boolean register(int slot, String id, String name, String character, String fingerprint) {
        String existing = entrantIdentities.putIfAbsent(fingerprint, id);
        if (existing != null && !existing.equals(id)) return false;
        entrants.putIfAbsent(id, new Entrant(slot, id, name, character));
        return true;
    }
    boolean startAttempt(String id, int attempt) {
        long now = hubClockMillis.getAsLong();
        advanceCountdown(now);
        if (!competition()) return mayStartAttempt();
        if (phase != Phase.COUNTDOWN && phase != Phase.RUNNING) return false;
        if (!entrants.containsKey(id) || started.containsKey(id) || out.containsKey(id)) return false;
        if (now > countdownEndsAt + 2000) { out.put(id, "DNS"); return false; }
        // Arena AttemptStart is sent at release, before the native countdown's GO.
        if (now < countdownEndsAt - ("ARENA".equals(config.trackKind()) ? 4000 : 0)) return false;
        started.put(id, attempt);
        return true;
    }
    void forfeit(String id, String reason) {
        if (competition() && entrants.containsKey(id) && !bests.containsKey(id))
            out.putIfAbsent(id, "DNS".equals(reason) ? "DNS" : "DNF");
    }
    private boolean validTiming(ControlMessage.AttemptFinish f) {
        int laps = profile == null ? ("ARENA".equals(config.trackKind()) ? 5 : 0) : profile.lapCount();
        if (f.lapFrames().size() != laps) return false;
        long sum = 0;
        for (int lap : f.lapFrames()) { if (lap < 1) return false; sum += lap; }
        if (laps > 0 && sum != f.timeFrames()) return false;
        int previous = f.clockStartFrame() - 1;
        for (int split : f.splitFrames()) {
            if (split <= previous || split < f.clockStartFrame() || split > f.finishFrame()) return false;
            previous = split;
        }
        return true;
    }

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
    private long planRevision;
    private long roundId;
    private boolean verifiedRoom;
    private boolean publishUnverifiedStandings = true;
    private long pendingHoldMillis = 10_000;
    private BiConsumer<Integer, Integer> pendingExpiryListener = (slot, attempt) -> { };
    private Consumer<Result> pendingResultExpiryListener = result -> { };

    public HostRoundEngine(LongSupplier hubClockMillis,
                           Consumer<ControlMessage> broadcaster) {
        this.hubClockMillis = hubClockMillis;
        this.broadcaster = broadcaster;
    }

    public Phase phase() {
        return phase;
    }

    boolean mayStartAttempt() {
        long now = hubClockMillis.getAsLong();
        advanceCountdown(now);
        return phase == Phase.RUNNING && now <= deadline;
    }

    public boolean startRound(ControlMessage.RoundConfig newConfig) {
        if (phase != Phase.LOBBY
                && (phase != Phase.ROUND_END || !voteTrackPool.isEmpty())) {
            return false;
        }
        // A round started before the previous linger finished still holds
        // unresolved claims; void their verification routes before forgetting them.
        expirePendingCandidates();
        long now = hubClockMillis.getAsLong();
        config = newConfig;
        if (votedNextConfig == null) planRevision++;
        roundId++;
        countdownEndsAt = now + (competition() ? newConfig.startInSeconds() * 1000L : COUNTDOWN_MILLIS);
        entrants.clear(); entrantIdentities.clear(); started.clear(); out.clear(); cutoffAt = Long.MAX_VALUE; winnerFrames = -1;
        // Shipped acts and arenas cap the race clock at ten minutes. Competition
        // uses that hard limit plus reload/finish transit allowance, not the TA retry window.
        deadline = countdownEndsAt + (competition() ? 630 : newConfig.windowSeconds()) * 1000L;
        bests.clear(); lapBests.clear(); verifiedLapBests.clear();
        verifiedBests.clear();
        lastFinishedAttemptByParticipant.clear();
        achievedCounter = 0;
        votedNextConfig = null;
        phase = Phase.COUNTDOWN;
        broadcaster.accept(new ControlMessage.RoundStart(config, countdownEndsAt, deadline));
        return true;
    }

    public void onTick() {
        long now = hubClockMillis.getAsLong();
        advanceCountdown(now);
        if (competition() && now > countdownEndsAt + 2000) {
            for (String id : entrants.keySet()) if (!started.containsKey(id)) out.putIfAbsent(id, "DNS");
        }
        boolean everyoneOut = competition() && !entrants.isEmpty()
                && entrants.keySet().stream().allMatch(id -> bests.containsKey(id) || out.containsKey(id));
        // Keep accepted attempts alive for finishes in transit. The protocol has no
        // trusted completion timestamp, so the host cannot prove a finish happened
        // before the deadline; RoomHost separately stops new attempts at deadline.
        if (phase == Phase.RUNNING && (everyoneOut || now > Math.min(deadline, cutoffAt) + FINISH_GRACE_MILLIS)) {
            if (competition()) for (String id : entrants.keySet()) forfeit(id, "DNF");
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
            if (expirePendingCandidates()) {
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

    private void advanceCountdown(long now) {
        // Socket messages and scheduled ticks share the host clock but can arrive
        // in either order. Admit the announced start boundary even when its tick
        // callback has not run yet; deadline/grace checks remain with each owner.
        if (phase == Phase.COUNTDOWN && now >= countdownEndsAt) {
            phase = Phase.RUNNING;
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

    public long planRevision() {
        return planRevision;
    }

    public long roundId() {
        return roundId;
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

    void setPendingResultExpiryListener(Consumer<Result> listener) {
        pendingResultExpiryListener = listener == null ? result -> { } : listener;
    }

    /** Participants whose shown best still awaits a verdict; holds the round end. */
    public int pendingVerdictCount() {
        return (int) java.util.stream.Stream.concat(bests.entrySet().stream(), lapBests.entrySet().stream())
                .filter(e -> "PENDING".equals(e.getValue().verifyState()))
                .map(Map.Entry::getKey).distinct().count();
    }

    public ControlMessage.AttemptFinish bestFinish(int slot) {
        Best best = bests.values().stream().filter(value -> value.slot() == slot)
                .findFirst().orElse(null);
        return best == null ? null : best.finish();
    }

    boolean hasBestForParticipant(String participantId) {
        return bests.containsKey(participantId);
    }

    List<Result> results() {
        return sortedBests().stream().map(HostRoundEngine::result).toList();
    }

    private static Result result(Best best) {
        return new Result(best.slot(), best.participantId(), best.fingerprint(),
                best.displayName(), best.character(), best.timeFrames(), best.finish(),
                best.verifyState());
    }

    public void onVerdict(int slot, int attemptId, boolean pass) {
        Best candidate = pendingCandidates.values().stream().flatMap(List::stream)
                .filter(value -> value.slot() == slot
                        && value.finish().attemptId() == attemptId)
                .findFirst().orElse(null);
        if (candidate != null) {
            onVerdictForParticipant(candidate.participantId(), attemptId,
                    candidate.finish().inputRecordingHashHex(), pass);
        }
    }

    void onVerdictForParticipant(String participantId, int attemptId,
                                 String recordingHash, boolean pass) {
        List<Best> candidates = pendingCandidates.get(participantId);
        Best candidate = candidates == null ? null : candidates.stream()
                .filter(value -> value.finish().attemptId() == attemptId
                        && value.finish().inputRecordingHashHex().equals(recordingHash))
                .findFirst().orElse(null);
        if (candidate == null) {
            return;
        }
        candidates.remove(candidate);
        if (candidates.isEmpty()) {
            pendingCandidates.remove(participantId);
        }
        if (pass) {
            Best verified = new Best(candidate.slot(), candidate.participantId(),
                    candidate.fingerprint(), candidate.displayName(), candidate.character(),
                    candidate.timeFrames(), candidate.achievedOrder(), candidate.finish(),
                    "VERIFIED");
            Best lapFallback = verifiedLapBests.get(participantId);
            if (lapTime(verified) > 0 && (lapFallback == null || lapTime(verified) < lapTime(lapFallback)))
                verifiedLapBests.put(participantId, verified);
            Best fallback = verifiedBests.get(participantId);
            // A superseded claim that passes replaces a slower fallback; a stale
            // pass slower than the current fallback changes nothing.
            if (fallback == null || STANDING_ORDER.compare(verified, fallback) < 0) {
                verifiedBests.put(participantId, verified);
            }
        }
        // A verdict on the shown claim always changes its row (state or time),
        // even outside the broadcast cap; one on a hidden claim changes nothing.
        boolean changed = showBest(participantId);
        if (competition() && !pass && !bests.containsKey(participantId))
            changed |= out.putIfAbsent(participantId, "DNF") == null;
        if (changed) broadcaster.accept(new ControlMessage.StandingsDelta(broadcastStandings()));
    }

    /** Recomputes a verified room's shown best; returns whether it changed. */
    private boolean showBest(String participantId) {
        Best shown = verifiedBests.get(participantId);
        for (Best candidate : pendingCandidates.getOrDefault(participantId, List.of())) {
            if (shown == null || STANDING_ORDER.compare(candidate, shown) < 0) {
                shown = candidate;
            }
        }
        Best lapBest = verifiedLapBests.get(participantId);
        for (Best candidate : pendingCandidates.getOrDefault(participantId, List.of())) {
            if (lapTime(candidate) > 0 && (lapBest == null || lapTime(candidate) < lapTime(lapBest))) lapBest = candidate;
        }
        Best previousLap = lapBest == null ? lapBests.remove(participantId) : lapBests.put(participantId, lapBest);
        Best previous = shown == null ? bests.remove(participantId)
                : bests.put(participantId, shown);
        return !Objects.equals(previous, shown) || !Objects.equals(previousLap, lapBest);
    }

    /**
     * Expires every unresolved claim and shows each participant's verified
     * fallback. Both listeners fire once per claim, so the relay voids every
     * verification route. Returns whether any shown best changed.
     */
    private boolean expirePendingCandidates() {
        if (pendingCandidates.isEmpty()) {
            return false;
        }
        Map<String, List<Best>> expired = new LinkedHashMap<>(pendingCandidates);
        pendingCandidates.clear();
        boolean changed = false;
        for (String participantId : expired.keySet()) {
            changed |= showBest(participantId);
            if (competition() && !bests.containsKey(participantId))
                changed |= out.putIfAbsent(participantId, "DNF") == null;
        }
        for (List<Best> candidates : expired.values()) {
            for (Best candidate : candidates) {
                pendingExpiryListener.accept(candidate.slot(),
                        candidate.finish().attemptId());
                pendingResultExpiryListener.accept(result(candidate));
            }
        }
        return changed;
    }

    void sendVoteState(Consumer<ControlMessage> recipient) {
        if (phase == Phase.VOTE) {
            recipient.accept(new ControlMessage.TrackVoteOffer(voteOptions, voteEndsAt));
            recipient.accept(new ControlMessage.TrackVoteTally(currentTally()));
        } else if (phase == Phase.LOBBY && votedNextConfig != null) {
            recipient.accept(new ControlMessage.TrackVoteResult(
                    new TrackKey(votedNextConfig.gameId(), votedNextConfig.trackKind(), votedNextConfig.zone(), votedNextConfig.act()).format(),
                    votedNextConfig, planRevision));
        }
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
        return onAttemptFinish(slot, "legacy-slot:" + slot, null,
                displayName, character, finish, attemptFlagged);
    }

    FinishOutcome onAttemptFinish(int slot, String participantId,
                                  String fingerprint, String displayName,
                                  String character, ControlMessage.AttemptFinish finish,
                                  boolean attemptFlagged) {
        long now = hubClockMillis.getAsLong();
        advanceCountdown(now);
        if (phase != Phase.RUNNING || now > deadline + FINISH_GRACE_MILLIS
                || attemptFlagged || finish.timeFrames() <= 0
                || !validTiming(finish)
                || competition() && (out.containsKey(participantId)
                    || !Objects.equals(started.get(participantId), finish.attemptId())
                    || bests.containsKey(participantId)
                    || cutoffAt != Long.MAX_VALUE && now > cutoffAt + FINISH_GRACE_MILLIS
                    || winnerFrames >= 0 && (long) finish.timeFrames() > winnerFrames + config.cutoffSeconds() * 60L)
                || finish.attemptId() <= lastFinishedAttemptByParticipant
                .getOrDefault(participantId, Integer.MIN_VALUE)
                || finish.finishFrame() > 37800
                || finish.clockStartFrame() < 0
                || finish.finishFrame() < finish.clockStartFrame()
                || finish.finishFrame() - finish.clockStartFrame()
                != finish.timeFrames()
                || !isSha256Hex(finish.inputRecordingHashHex())) {
            return null;
        }
        lastFinishedAttemptByParticipant.put(participantId, finish.attemptId());
        // Compare with the shown best: each accepted improvement is strictly faster
        // than everything the participant has on display, so one job per improvement.
        int lap = finish.lapFrames().stream().mapToInt(Integer::intValue).min().orElse(0);
        Best oldLap = lapBests.get(participantId);
        boolean betterLap = lap > 0 && (oldLap == null || lap < lapTime(oldLap));
        Best existing = bests.get(participantId);
        if (existing != null && existing.timeFrames() <= finish.timeFrames() && !betterLap) return null;
        Best improved = new Best(slot, participantId, fingerprint,
                displayName, character, finish.timeFrames(),
                achievedCounter++, finish, verifiedRoom ? "PENDING" : "UNVERIFIED");
        if (verifiedRoom) {
            pendingCandidates.computeIfAbsent(participantId, ignored -> new ArrayList<>())
                    .add(improved);
        }
        if (betterLap) lapBests.put(participantId, improved);
        if (existing == null || existing.timeFrames() > improved.timeFrames()) bests.put(participantId, improved);
        if (competition() && winnerFrames < 0) {
            winnerFrames = finish.timeFrames();
            cutoffAt = now + config.cutoffSeconds() * 1000L;
            broadcaster.accept(new ControlMessage.RaceCutoff(roundId, cutoffAt));
        }
        List<Best> sorted = sortedBests();
        // Keep every finish notification: clients outside the cap also use it
        // to refresh their around-you page. Reuse this ordering for private rank.
        broadcaster.accept(new ControlMessage.StandingsDelta(standingsArePublishable()
                ? broadcastStandings() : List.of()));
        int rank = standings(sorted).get(sorted.indexOf(bests.get(participantId))).rank();
        return new FinishOutcome(slot, rank, sorted.indexOf(bests.get(participantId)) >= STANDINGS_BROADCAST_CAP,
                finish.attemptId());
    }

    public void onPlayerLeft(int slot) {
        if (competition()) entrants.values().stream().filter(e -> e.slot() == slot)
                .forEach(e -> forfeit(e.id(), "DISCONNECT"));
        // Best remains visible for the rest of the round.
        if (votesBySlot.remove(slot) != null && phase == Phase.VOTE) {
            broadcaster.accept(new ControlMessage.TrackVoteTally(currentTally()));
        }
    }

    public List<ControlMessage.StandingsRow> standings() {
        if (!standingsArePublishable()) {
            return List.of();
        }
        List<ControlMessage.StandingsRow> rows = new ArrayList<>(standings(sortedBests()));
        if (competition()) for (String outcome : List.of("DNF", "DNS")) {
            for (Entrant e : entrants.values()) if (outcome.equals(out.get(e.id())) && !bests.containsKey(e.id()))
                rows.add(new ControlMessage.StandingsRow(e.slot(), e.name(), e.character(), 0,
                        rows.size() + 1, verifiedRoom ? "VERIFIED" : "UNVERIFIED", outcome, 0, List.of(), taintedSwap(e.slot())));
        }
        return List.copyOf(rows);
    }

    void setPublishUnverifiedStandings(boolean publish) {
        publishUnverifiedStandings = publish;
    }

    private boolean standingsArePublishable() {
        return verifiedRoom || competition() || publishUnverifiedStandings;
    }

    private java.util.function.IntPredicate swapTaint = slot -> false;
    void setSwapTaint(java.util.function.IntPredicate source) { swapTaint = source; }
    private boolean taintedSwap(int slot) { return swapTaint.test(slot); }

    private List<ControlMessage.StandingsRow> standings(List<Best> sorted) {
        List<ControlMessage.StandingsRow> rows = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            Best best = sorted.get(index);
            rows.add(new ControlMessage.StandingsRow(best.slot(), best.displayName(),
                    best.character(), best.timeFrames(),
                    index > 0 && sorted.get(index - 1).timeFrames() == best.timeFrames()
                        ? rows.get(index - 1).rank() : index + 1, best.verifyState(), "FINISHED",
                    best.finish().lapFrames().stream().mapToInt(Integer::intValue).min().orElse(0), best.finish().lapFrames(), taintedSwap(best.slot())));
        }
        return List.copyOf(rows);
    }

    private List<Best> sortedBests() {
        List<Best> sorted = new ArrayList<>(bests.values());
        sorted.sort(STANDING_ORDER);
        return sorted;
    }

    List<ControlMessage.StandingsRow> page(int page, int pageSize, String board) {
        if (!"BEST_LAP".equals(board)) return page(page, pageSize);
        if (!standingsArePublishable() || page < 0 || pageSize < 1) return List.of();
        List<Best> sorted = new ArrayList<>(lapBests.values());
        sorted.sort(Comparator.comparingInt(HostRoundEngine::lapTime).thenComparingLong(Best::achievedOrder));
        List<ControlMessage.StandingsRow> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Best b = sorted.get(i);
            int rank = i > 0 && lapTime(sorted.get(i-1)) == lapTime(b) ? rows.get(i-1).rank() : i+1;
            rows.add(new ControlMessage.StandingsRow(b.slot(), b.displayName(), b.character(), b.timeFrames(), rank,
                    b.verifyState(), "FINISHED", lapTime(b), b.finish().lapFrames(), taintedSwap(b.slot())));
        }
        return rows.stream().skip((long) page * pageSize).limit(pageSize).toList();
    }

    ControlMessage.RankUpdate rankUpdateForParticipant(String participantId, String board) {
        if (!standingsArePublishable()) return null;
        boolean lap = "BEST_LAP".equals(board);
        List<Best> sorted = new ArrayList<>((lap ? lapBests : bests).values());
        java.util.function.ToIntFunction<Best> metric = lap ? HostRoundEngine::lapTime : Best::timeFrames;
        sorted.sort(Comparator.comparingInt(metric).thenComparingLong(Best::achievedOrder));
        for (Best best : sorted) if (best.participantId().equals(participantId)) {
            int time = metric.applyAsInt(best);
            int rank = 1 + (int) sorted.stream().filter(b -> metric.applyAsInt(b) < time).count();
            return new ControlMessage.RankUpdate(rank, time, board);
        }
        return null;
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

    int totalPages(int pageSize, String board) {
        if (!"BEST_LAP".equals(board)) return totalPages(pageSize);
        int size = standingsArePublishable() ? lapBests.size() : 0;
        return (size + pageSize - 1) / pageSize;
    }

    public int totalPages(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        int size = standings().size();
        return size == 0 ? 0 : (size + pageSize - 1) / pageSize;
    }

    public ControlMessage.RoundSnapshot snapshot() {
        return new ControlMessage.RoundSnapshot(
                phase.name(), config, countdownEndsAt, deadline, broadcastStandings());
    }

    private List<ControlMessage.StandingsRow> broadcastStandings() {
        if (!standingsArePublishable()) {
            return List.of();
        }
        return standings().stream().limit(STANDINGS_BROADCAST_CAP).toList();
    }

    private List<ControlMessage.StandingsRow> broadcastStandings(
            List<Best> sorted) {
        return standings(sorted.subList(0,
                Math.min(STANDINGS_BROADCAST_CAP, sorted.size())));
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
        try {
            TrackKey track = TrackKey.parse(key);
            if (!track.gameId().equals(config.gameId()) || !TrackRules.legal(track, config.mode())) return false;
        } catch (IllegalArgumentException invalid) { return false; }
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
            TrackKey track = TrackKey.parse(winner);
            votedNextConfig = new ControlMessage.RoundConfig(track.gameId(), track.zone(), track.act(),
                    config.windowSeconds(), config.characterPolicy(), config.lockedCharacter(), track.kind(),
                    config.mode(), config.monitors(), config.cutoffSeconds(), config.startInSeconds());
            if (!TrackRules.monitorsLegal(votedNextConfig))
                votedNextConfig = new ControlMessage.RoundConfig(track.gameId(), track.zone(), track.act(),
                        config.windowSeconds(), config.characterPolicy(), config.lockedCharacter(), track.kind(),
                        config.mode(), "ARENA".equals(track.kind()) ? "ITEMS" : "NORMAL", config.cutoffSeconds(), config.startInSeconds());
        } else votedNextConfig = config;
        planRevision++;
        broadcaster.accept(new ControlMessage.TrackVoteResult(
                winner == null ? currentTrackKey() : winner,
                votedNextConfig, planRevision));
        voteOptions = List.of();
        votesBySlot.clear();
        phase = Phase.LOBBY;
    }

    private String currentTrackKey() {
        return config == null ? "" : new TrackKey(config.gameId(), config.trackKind(), config.zone(), config.act()).format();
    }

    private static int lapTime(Best best) { return best.finish().lapFrames().stream().mapToInt(Integer::intValue).min().orElse(0); }

    private static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (!((digit >= '0' && digit <= '9')
                    || (digit >= 'a' && digit <= 'f')
                    || (digit >= 'A' && digit <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
