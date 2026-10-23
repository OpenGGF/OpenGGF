package openggf.timeattack.mp;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.List;
import java.util.Map;

/**
 * Immutable in-round multiplayer HUD snapshot. {@code nameColours} maps a slot to the
 * {@code 0xAARRGGBB} name colour of a player with a chosen look; stock players are absent.
 * {@code hurtFlash} maps a slot to the frames its progress marker keeps flashing after a hit
 * (present while the player is hurt and briefly after); {@code tick} paces that flash.
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
        Map<Integer, Integer> nameColours,
        int localSlot,
        int tick,
        Map<Integer, Integer> hurtFlash) {

    /** A snapshot with no hurt flashes (and no local slot). */
    public MultiplayerHudState(boolean active, String phase, long remainingWindowMillis,
                               long remainingCountdownMillis, List<ControlMessage.StandingsRow> standings,
                               List<String> chatLines, boolean connectionLost, String kickReason,
                               int totalPlayers, List<RemoteGhostRegistry.FarPlayer> farPlayers,
                               String characterPolicy, List<String> voteOptions,
                               List<ControlMessage.VoteCount> voteCounts, long voteRemainingMillis,
                               String voteResultTrackKey, List<ControlMessage.StandingsRow> podiumRows,
                               int localRank, Map<Integer, Integer> nameColours) {
        this(active, phase, remainingWindowMillis, remainingCountdownMillis, standings, chatLines,
                connectionLost, kickReason, totalPlayers, farPlayers, characterPolicy, voteOptions, voteCounts,
                voteRemainingMillis, voteResultTrackKey, podiumRows, localRank, nameColours, -1, 0, Map.of());
    }

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

    /** Frames slot {@code slot}'s marker still flashes after a hit, or 0. */
    public int hurtFlash(int slot) {
        return hurtFlash.getOrDefault(slot, 0);
    }

    public MultiplayerHudState {
        nameColours = Map.copyOf(nameColours);
        hurtFlash = Map.copyOf(hurtFlash);
        standings = List.copyOf(standings);
        chatLines = List.copyOf(chatLines);
        farPlayers = List.copyOf(farPlayers);
        voteOptions = List.copyOf(voteOptions);
        voteCounts = List.copyOf(voteCounts);
        podiumRows = List.copyOf(podiumRows);
    }
}
