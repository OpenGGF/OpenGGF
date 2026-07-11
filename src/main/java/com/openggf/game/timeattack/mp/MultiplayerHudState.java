package com.openggf.game.timeattack.mp;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.List;

/** Immutable in-round multiplayer HUD snapshot. */
@com.openggf.game.ModApi
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
        int localRank) {

    public MultiplayerHudState {
        standings = List.copyOf(standings);
        chatLines = List.copyOf(chatLines);
        farPlayers = List.copyOf(farPlayers);
        voteOptions = List.copyOf(voteOptions);
        voteCounts = List.copyOf(voteCounts);
        podiumRows = List.copyOf(podiumRows);
    }
}
