package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/** Client-side room/round mirror with an NTP-lite hub-clock estimate. */
public final class ClientRaceSession {
    public enum Phase { LOBBY, COUNTDOWN, RUNNING, ROUND_END, VOTE }

    public static final int CLOCK_SAMPLES_TARGET = 5;
    public static final int CHAT_LINES_KEPT = 8;

    private final LongSupplier clientClockMillis;
    private final Deque<Long> offsetSamples = new ArrayDeque<>();
    private final Deque<String> chat = new ArrayDeque<>();

    private ControlMessage.RoomDescriptor room;
    private int localSlot = -1;
    private Phase basePhase = Phase.LOBBY;
    private ControlMessage.RoundConfig roundConfig;
    private long countdownEndsAtHub;
    private long deadlineHub;
    private List<ControlMessage.StandingsRow> standings = List.of();
    private List<ControlMessage.PlayerInfo> players = List.of();
    private String kickReason;
    private List<String> voteOptions = List.of();
    private List<ControlMessage.VoteCount> voteCounts = List.of();
    private long voteEndsAtHubMillis = -1;
    private String lastVoteResult;
    private List<ControlMessage.StandingsRow> finalStandings = List.of();

    public ClientRaceSession(LongSupplier clientClockMillis) {
        this.clientClockMillis = clientClockMillis;
    }

    public void applyJoin(ControlMessage.JoinAccepted accepted) {
        room = accepted.room();
        localSlot = accepted.playerSlot();
        ControlMessage.RoundSnapshot snapshot = accepted.round();
        if (snapshot != null) {
            basePhase = Phase.valueOf(snapshot.phase());
            roundConfig = snapshot.config();
            countdownEndsAtHub = snapshot.countdownEndsAtHubMillis();
            deadlineHub = snapshot.deadlineHubMillis();
            standings = List.copyOf(snapshot.standings());
        }
    }

    public void onControl(ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomState state -> players = List.copyOf(state.players());
            case ControlMessage.RoundStart start -> {
                basePhase = Phase.COUNTDOWN;
                roundConfig = start.config();
                countdownEndsAtHub = start.countdownEndsAtHubMillis();
                deadlineHub = start.deadlineHubMillis();
                standings = List.of();
                finalStandings = List.of();
                lastVoteResult = null;
            }
            case ControlMessage.RoundEnd end -> {
                basePhase = Phase.ROUND_END;
                standings = List.copyOf(end.finalStandings());
                finalStandings = standings;
            }
            case ControlMessage.StandingsDelta delta ->
                    standings = List.copyOf(delta.rows());
            case ControlMessage.ChatBroadcast line -> {
                chat.addLast(line.displayName() + ": " + line.text());
                while (chat.size() > CHAT_LINES_KEPT) {
                    chat.removeFirst();
                }
            }
            case ControlMessage.Pong pong -> {
                long now = clientClockMillis.getAsLong();
                offsetSamples.addLast(pong.hubMillis()
                        - midpoint(pong.t0ClientMillis(), now));
                while (offsetSamples.size() > CLOCK_SAMPLES_TARGET) {
                    offsetSamples.removeFirst();
                }
            }
            case ControlMessage.Kick kick -> kickReason = kick.reason();
            case ControlMessage.TrackVoteOffer offer -> {
                basePhase = Phase.VOTE;
                voteOptions = List.copyOf(offer.trackKeys());
                voteCounts = List.of();
                voteEndsAtHubMillis = offer.voteEndsAtHubMillis();
            }
            case ControlMessage.TrackVoteTally tally ->
                    voteCounts = List.copyOf(tally.counts());
            case ControlMessage.TrackVoteResult result -> {
                basePhase = Phase.LOBBY;
                lastVoteResult = result.trackKey();
                voteOptions = List.of();
                voteCounts = List.of();
                voteEndsAtHubMillis = -1;
            }
            default -> { }
        }
    }

    public long clockOffsetMillis() {
        if (offsetSamples.isEmpty()) {
            return 0;
        }
        long[] sorted = offsetSamples.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public boolean needsMoreClockSamples() {
        return offsetSamples.size() < CLOCK_SAMPLES_TARGET;
    }

    public long hubNowEstimateMillis() {
        return clientClockMillis.getAsLong() + clockOffsetMillis();
    }

    public Phase phase() {
        long hubNow = hubNowEstimateMillis();
        if (basePhase == Phase.COUNTDOWN && hubNow >= countdownEndsAtHub) {
            return hubNow > deadlineHub ? Phase.ROUND_END : Phase.RUNNING;
        }
        if (basePhase == Phase.RUNNING && hubNow > deadlineHub) {
            return Phase.ROUND_END;
        }
        return basePhase;
    }

    public long remainingCountdownMillis() {
        return phase() == Phase.COUNTDOWN
                ? Math.max(0, countdownEndsAtHub - hubNowEstimateMillis()) : 0;
    }

    public long remainingWindowMillis() {
        Phase current = phase();
        if (current != Phase.COUNTDOWN && current != Phase.RUNNING) {
            return -1;
        }
        return Math.max(0, deadlineHub - hubNowEstimateMillis());
    }

    public boolean isWindowOpen() {
        return phase() == Phase.RUNNING;
    }

    public List<ControlMessage.StandingsRow> standings() {
        return standings;
    }

    public List<ControlMessage.PlayerInfo> players() {
        return players;
    }

    public List<String> chatLines() {
        return new ArrayList<>(chat);
    }

    public ControlMessage.RoundConfig roundConfig() {
        return roundConfig;
    }

    public ControlMessage.RoomDescriptor room() {
        return room;
    }

    public int localSlot() {
        return localSlot;
    }

    public String kickReason() {
        return kickReason;
    }

    public List<String> voteOptions() { return voteOptions; }

    public List<ControlMessage.VoteCount> voteCounts() { return voteCounts; }

    public long voteRemainingMillis() {
        return voteEndsAtHubMillis < 0 ? -1
                : Math.max(0, voteEndsAtHubMillis - hubNowEstimateMillis());
    }

    public String lastVoteResultTrackKey() { return lastVoteResult; }

    public List<ControlMessage.StandingsRow> podiumTop(int count) {
        if (count <= 0) {
            return List.of();
        }
        return List.copyOf(finalStandings.subList(
                0, Math.min(count, finalStandings.size())));
    }

    public ControlMessage.StandingsRow localStandingsRow() {
        return standings.stream().filter(row -> row.slot() == localSlot)
                .findFirst().orElse(null);
    }

    private static long midpoint(long left, long right) {
        return left + (right - left) / 2;
    }
}
