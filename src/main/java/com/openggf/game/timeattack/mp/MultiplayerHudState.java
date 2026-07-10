package com.openggf.game.timeattack.mp;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.client.RemoteGhostRegistry;

import java.util.List;

/** Immutable in-round multiplayer HUD snapshot. */
public record MultiplayerHudState(
        boolean active,
        String phase,
        long remainingWindowMillis,
        long remainingCountdownMillis,
        List<ControlMessage.StandingsRow> standings,
        List<String> chatLines,
        int totalPlayers,
        List<RemoteGhostRegistry.FarPlayer> farPlayers,
        boolean connectionLost,
        String kickReason) {

    public MultiplayerHudState {
        standings = List.copyOf(standings);
        chatLines = List.copyOf(chatLines);
        farPlayers = List.copyOf(farPlayers);
    }
}
