package openggf.timeattack.mp;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.List;
import java.util.Map;

/**
 * Immutable in-round multiplayer HUD snapshot. {@code nameColours} maps a slot to the
 * {@code 0xAARRGGBB} name colour of a player with a chosen look; stock players are absent.
 */
public record MultiplayerHudState(
        boolean active,
        String phase,
        long remainingWindowMillis,
        long remainingCountdownMillis,
        List<ControlMessage.StandingsRow> standings,
        List<String> chatLines,
        boolean connectionLost,
        String kickReason,
        int totalPlayers,
        List<RemoteGhostRegistry.FarPlayer> farPlayers,
        String characterPolicy,
        List<String> voteOptions,
        List<ControlMessage.VoteCount> voteCounts,
        long voteRemainingMillis,
        String voteResultTrackKey,
        List<ControlMessage.StandingsRow> podiumRows,
        int localRank,
        Map<Integer, Integer> nameColours) {

    /** A snapshot in which every name uses its row's colour. */
    public MultiplayerHudState(boolean active, String phase, long remainingWindowMillis,
                               long remainingCountdownMillis, List<ControlMessage.StandingsRow> standings,
                               List<String> chatLines, boolean connectionLost, String kickReason,
                               int totalPlayers, List<RemoteGhostRegistry.FarPlayer> farPlayers,
                               String characterPolicy, List<String> voteOptions,
                               List<ControlMessage.VoteCount> voteCounts, long voteRemainingMillis,
                               String voteResultTrackKey, List<ControlMessage.StandingsRow> podiumRows,
                               int localRank) {
        this(active, phase, remainingWindowMillis, remainingCountdownMillis, standings, chatLines,
                connectionLost, kickReason, totalPlayers, farPlayers, characterPolicy, voteOptions, voteCounts,
                voteRemainingMillis, voteResultTrackKey, podiumRows, localRank, Map.of());
    }

    /** The name colour slot {@code slot} chose, or {@code fallback} for a stock look. */
    public int nameColour(int slot, int fallback) {
        return nameColours.getOrDefault(slot, fallback);
    }

    public MultiplayerHudState {
        nameColours = Map.copyOf(nameColours);
        standings = List.copyOf(standings);
        chatLines = List.copyOf(chatLines);
        farPlayers = List.copyOf(farPlayers);
        voteOptions = List.copyOf(voteOptions);
        voteCounts = List.copyOf(voteCounts);
        podiumRows = List.copyOf(podiumRows);
    }
}
