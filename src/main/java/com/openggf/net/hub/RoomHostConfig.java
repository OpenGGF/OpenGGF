package com.openggf.net.hub;

import com.openggf.net.protocol.Protocol;

/** Immutable parameters for a player-hosted direct room. */
public record RoomHostConfig(String roomName, String gameId, int zone, int act,
                             String characterPolicy, String lockedCharacter,
                             int maxPlayers, String requiredDeterminismFingerprint) {
    public RoomHostConfig {
        maxPlayers = Math.min(Math.max(maxPlayers, 1), Protocol.MAX_PLAYERS_RELAY);
    }
}
