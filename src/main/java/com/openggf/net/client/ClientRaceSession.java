package com.openggf.net.client;

import com.openggf.net.chat.EnglishQuickChat;
import com.openggf.net.chat.ProfanityFilter;
import com.openggf.net.chat.QuickChatContent;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Client-side room/round mirror with an NTP-lite hub-clock estimate. */
@com.openggf.game.ModApi
public final class ClientRaceSession {
    @com.openggf.game.ModApi
    public enum Phase { LOBBY, COUNTDOWN, RUNNING, ROUND_END, VOTE }

    public static final int CLOCK_SAMPLES_TARGET = 5;
    public static final int CHAT_LINES_KEPT = 8;

    private sealed interface ChatEntry permits TextEntry, QuickEntry {
        int slot();
        String displayName();
    }

    private record TextEntry(int slot, String displayName, String text) implements ChatEntry { }

    private record QuickEntry(int slot, String displayName, QuickChatContent content) implements ChatEntry { }

    private final LongSupplier clientClockMillis;
    private final Deque<Long> offsetSamples = new ArrayDeque<>();
    // Quick entries retain their sent track context so later rounds cannot rewrite history.
    private final Deque<ChatEntry> chat = new ArrayDeque<>();
    private List<String> renderedChat = List.of();
    private boolean censorProfanity = true;
    private boolean disableChat;
    private Function<QuickChatContent, String> quickChatFormatter =
            line -> EnglishQuickChat.text(line.phrase(), null);

    private ControlMessage.RoomDescriptor room;
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
        room = accepted.room();
        clearChat();
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
            case ControlMessage.RoomState state -> players = sortedPlayers(state.players());
            case ControlMessage.PlayerUpsert upsert -> {
                List<ControlMessage.PlayerInfo> updated = new ArrayList<>(players);
                updated.removeIf(player -> player.slot() == upsert.player().slot()
                        || player.fingerprint().equals(upsert.player().fingerprint()));
                updated.add(upsert.player());
                players = sortedPlayers(updated);
            }
            case ControlMessage.PlayerLeft left -> {
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
            case ControlMessage.ChatModeState state -> applyChatMode(state.mode());
            case ControlMessage.ChatBroadcast line -> {
                if (!disableChat && chatMode().allowsText()
                        && line.displayName() != null && line.text() != null) {
                    appendChat(new TextEntry(line.slot(), line.displayName(), line.text()));
                }
            }
            case ControlMessage.QuickChatBroadcast line -> {
                if (!disableChat && chatMode().allowsQuickChat() && line.displayName() != null) {
                    QuickChatPhrase phrase = QuickChatPhrase.parse(line.phraseId());
                    if (phrase == null) break;
                    QuickChatContent content;
                    try {
                        content = new QuickChatContent(phrase, line.gameId(), line.zone(), line.act());
                    } catch (IllegalArgumentException malformed) {
                        // Normally rejected by the codec; direct callers obey the same boundary.
                        break;
                    }
                    appendChat(new QuickEntry(line.slot(), line.displayName(), content));
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

    /** Immutable rendered snapshot; reads do no formatting or lexicon work. */
    public List<String> chatLines() {
        return renderedChat;
    }

    /** Effective capabilities announced by the room host. */
    public ChatMode chatMode() {
        return room == null ? ChatMode.OFF : ChatMode.parse(room.chatMode());
    }

    /** Personal receive/display preference; room restrictions are exposed by {@link #chatMode()}. */
    public boolean chatDisabled() {
        return disableChat;
    }

    /** Applies a settings snapshot without consulting engine configuration from networking code. */
    public void setChatPreferences(boolean censorProfanity, boolean disableChat) {
        if (this.censorProfanity == censorProfanity && this.disableChat == disableChat) {
            return;
        }
        this.censorProfanity = censorProfanity;
        this.disableChat = disableChat;
        if (disableChat) {
            clearChat();
        } else {
            renderChat();
        }
    }

    /** Supplies local catalogue/zone rendering; retained quick messages keep their sent context. */
    public void setQuickChatFormatter(Function<QuickChatContent, String> formatter) {
        java.util.Objects.requireNonNull(formatter, "formatter");
        if (quickChatFormatter != formatter) {
            quickChatFormatter = formatter;
            renderChat();
        }
    }

    private void applyChatMode(String mode) {
        ChatMode parsed = ChatMode.parse(mode);
        if (room == null || parsed == null || mode.equals(room.chatMode())) return;
        room = new ControlMessage.RoomDescriptor(room.name(), room.gameId(), room.zone(), room.act(),
                room.characterPolicy(), room.lockedCharacter(), room.maxPlayers(), room.verified(), mode);
        if (parsed == ChatMode.OFF) clearChat();
    }

    private void appendChat(ChatEntry entry) {
        chat.addLast(entry);
        while (chat.size() > CHAT_LINES_KEPT) chat.removeFirst();
        renderChat();
    }

    private void clearChat() {
        chat.clear();
        renderedChat = List.of();
    }

    private void renderChat() {
        List<String> rendered = new ArrayList<>(chat.size());
        for (ChatEntry entry : chat) {
            String label = entry.displayName();
            String body = switch (entry) {
                case TextEntry text -> text.text();
                case QuickEntry quick -> quickChatFormatter.apply(quick.content());
            };
            if (body == null) continue;
            if (censorProfanity) {
                label = ProfanityFilter.censor(label);
                body = ProfanityFilter.censor(body);
            }
            rendered.add(label + ": " + body);
        }
        renderedChat = List.copyOf(rendered);
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
