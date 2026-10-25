package com.openggf.net.client;

import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Client-side room/round mirror with an NTP-lite hub-clock estimate. */
@com.openggf.game.ModApi
public final class ClientRaceSession {
    @com.openggf.game.ModApi
    public enum Phase { LOBBY, COUNTDOWN, RUNNING, ROUND_END, VOTE }

    public static final int CLOCK_SAMPLES_TARGET = 5;
    public static final int CHAT_LINES_KEPT = 8;

    private final LongSupplier clientClockMillis;
    private final Deque<Long> offsetSamples = new ArrayDeque<>();
    private final Deque<String> chat = new ArrayDeque<>();

    private ControlMessage.RoomDescriptor room;
    private String instanceId;
    private com.openggf.net.protocol.RoomAccessPolicy accessPolicy = com.openggf.net.protocol.RoomAccessPolicy.PUBLIC;
    private long accessEpoch;
    private long inviteEpoch;
    private long nextDisclosureId;
    private long pendingDisclosureId;
    private ControlMessage.RoomInvite invite;
    private int localSlot = -1;
    private Phase basePhase = Phase.LOBBY;
    private ControlMessage.RoundConfig roundConfig;
    private long countdownEndsAtHub;
    private long deadlineHub;
    private List<ControlMessage.StandingsRow> standings = List.of();
    private List<ControlMessage.PlayerInfo> players = List.of();
    private final Map<Integer, PlayerAppearance> looks = new HashMap<>();
    private final java.util.Set<Integer> readySlots = new java.util.HashSet<>();
    private String readyRule = com.openggf.net.protocol.ReadyRules.DEFAULT_BY_SIZE.name();
    private boolean readyRequired = true;
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
        invalidateInvite();
        instanceId = null;
        accessEpoch = 0;
        inviteEpoch = 0;
        room = accepted.room();
        accessPolicy = room == null ? com.openggf.net.protocol.RoomAccessPolicy.PUBLIC : room.accessPolicy();
        localSlot = accepted.playerSlot();
        players = List.of();
        looks.clear();
        readySlots.clear();
        readyRule = com.openggf.net.protocol.ReadyRules.DEFAULT_BY_SIZE.name();
        readyRequired = true;
        ControlMessage.RoundSnapshot snapshot = accepted.round();
        if (snapshot != null) {
            basePhase = Phase.valueOf(snapshot.phase());
            roundConfig = snapshot.config();
            countdownEndsAtHub = snapshot.countdownEndsAtHubMillis();
            deadlineHub = snapshot.deadlineHubMillis();
            standings = List.copyOf(snapshot.standings());
            finalStandings = basePhase == Phase.ROUND_END ? standings : List.of();
        }
    }

    /** Whether the player in {@code slot} is ready. */
    public boolean isReady(int slot) {
        return readySlots.contains(slot);
    }

    /** Whether this player is ready. */
    public boolean isLocalReady() {
        return readySlots.contains(localSlot);
    }

    /** The room's ready rule by name ({@link com.openggf.net.protocol.ReadyRules}). */
    public String readyRule() {
        return readyRule;
    }

    /** True while the owner's round start waits for every present player to be ready. */
    public boolean readyRequired() {
        return readyRequired;
    }

    /** Present players who are not ready yet. */
    public List<ControlMessage.PlayerInfo> playersNotReady() {
        return players.stream().filter(player -> !readySlots.contains(player.slot())).toList();
    }

    public void onControl(ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomAccessState state -> {
                if (room == null || state.instanceId() == null || state.accessPolicy() == null
                        || instanceId != null && !instanceId.equals(state.instanceId())
                        || state.accessEpoch() < accessEpoch || state.inviteEpoch() < inviteEpoch) break;
                if (state.accessEpoch() != accessEpoch || state.inviteEpoch() != inviteEpoch
                        || !accessPolicy.equals(state.accessPolicy())) invite = null;
                instanceId = state.instanceId();
                accessEpoch = state.accessEpoch();
                inviteEpoch = state.inviteEpoch();
                accessPolicy = state.accessPolicy();
            }
            case ControlMessage.RoomInvite response -> {
                if (pendingDisclosureId != 0 && response.disclosureId() == pendingDisclosureId
                        && java.util.Objects.equals(instanceId, response.instanceId())
                        && response.accessEpoch() == accessEpoch && response.inviteEpoch() == inviteEpoch
                        && response.expiresAtMillis() > hubNowEstimateMillis()) {
                    try {
                        com.openggf.net.protocol.RoomInviteCode.parse(response.code());
                        invite = response;
                    } catch (IllegalArgumentException invalid) { invite = null; }
                    pendingDisclosureId = 0;
                }
            }
            case ControlMessage.RoomInviteRejected ignored -> invalidateInvite();
            case ControlMessage.RoomState state -> players = sortedPlayers(state.players());
            case ControlMessage.PlayerUpsert upsert -> {
                List<ControlMessage.PlayerInfo> updated = new ArrayList<>(players);
                updated.removeIf(player -> player.slot() == upsert.player().slot()
                        || player.fingerprint().equals(upsert.player().fingerprint()));
                updated.add(upsert.player());
                players = sortedPlayers(updated);
            }
            case ControlMessage.PlayerLeft left -> {
                if (left.slot() == localSlot) invalidateInvite();
                players = players.stream().filter(player -> player.slot() != left.slot()).toList();
                looks.remove(left.slot());
                readySlots.remove(left.slot());
            }
            case ControlMessage.PlayerLook look -> {
                PlayerAppearance appearance;
                try {
                    appearance = PlayerAppearance.decode(look.appearance());
                } catch (IllegalArgumentException malformed) {
                    // The host validates looks; an unreadable one is shown as the stock look.
                    appearance = PlayerAppearance.STOCK;
                }
                if (appearance.isStock()) looks.remove(look.slot());
                else looks.put(look.slot(), appearance);
            }
            case ControlMessage.PlayerReady ready -> {
                if (ready.ready()) readySlots.add(ready.slot());
                else readySlots.remove(ready.slot());
            }
            case ControlMessage.ReadyReset reset -> {
                readySlots.clear();
                if (reset.ready()) players.forEach(player -> readySlots.add(player.slot()));
            }
            case ControlMessage.ReadyRule rule -> {
                readyRule = rule.rule();
                readyRequired = rule.required();
            }
            case ControlMessage.RoundStart start -> {
                // The host clears every ready state at a round start (and re-readies everyone
                // with ReadyReset when its rule needs nobody to wait).
                readySlots.clear();
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
            case ControlMessage.StandingsDelta delta -> {
                standings = List.copyOf(delta.rows());
                if (basePhase == Phase.ROUND_END) finalStandings = standings;
            }
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
            case ControlMessage.Kick kick -> {
                kickReason = kick.reason();
                invalidateInvite();
            }
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

    public ControlMessage.RoomInviteRequest beginInviteRequest() {
        invalidateInvite();
        if (room == null || localSlot < 0 || kickReason != null || nextDisclosureId == Long.MAX_VALUE) {
            throw new IllegalStateException("room membership unavailable");
        }
        pendingDisclosureId = ++nextDisclosureId;
        return new ControlMessage.RoomInviteRequest(pendingDisclosureId);
    }

    public void invalidateInvite() {
        invite = null;
        pendingDisclosureId = 0;
    }

    public ControlMessage.RoomInvite invite() {
        if (invite != null && invite.expiresAtMillis() <= hubNowEstimateMillis()) invalidateInvite();
        return invite;
    }

    public String instanceId() { return instanceId; }
    public com.openggf.net.protocol.RoomAccessPolicy accessPolicy() { return accessPolicy; }
    public long accessEpoch() { return accessEpoch; }
    public long inviteEpoch() { return inviteEpoch; }

    private static List<ControlMessage.PlayerInfo> sortedPlayers(List<ControlMessage.PlayerInfo> values) {
        return values.stream().sorted(java.util.Comparator.comparingInt(
                ControlMessage.PlayerInfo::slot)).toList();
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

    /** The look last announced for {@code slot}; {@link PlayerAppearance#STOCK} when none. */
    public PlayerAppearance appearance(int slot) {
        return looks.getOrDefault(slot, PlayerAppearance.STOCK);
    }
}
