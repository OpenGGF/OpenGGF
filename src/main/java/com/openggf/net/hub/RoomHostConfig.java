package com.openggf.net.hub;

import com.openggf.net.protocol.Protocol;

import java.util.List;

/** Immutable parameters for a player-hosted direct room. */
public record RoomHostConfig(String roomName, String gameId, int zone, int act,
                             String characterPolicy, String lockedCharacter,
                             int maxPlayers, String requiredDeterminismFingerprint,
                             List<String> voteTrackKeys, boolean verified) {
    public RoomHostConfig {
        maxPlayers = Math.min(Math.max(maxPlayers, 1), Protocol.MAX_PLAYERS_RELAY);
        voteTrackKeys = List.copyOf(voteTrackKeys == null ? List.of() : voteTrackKeys);
    }

    public RoomHostConfig(String roomName, String gameId, int zone, int act,
                          String characterPolicy, String lockedCharacter,
                          int maxPlayers, String requiredDeterminismFingerprint) {
        this(roomName, gameId, zone, act, characterPolicy, lockedCharacter,
                maxPlayers, requiredDeterminismFingerprint, List.of(), false);
    }

    public RoomHostConfig(String roomName, String gameId, int zone, int act,
                          String characterPolicy, String lockedCharacter,
                          int maxPlayers, String requiredDeterminismFingerprint,
                          List<String> voteTrackKeys) {
        this(roomName, gameId, zone, act, characterPolicy, lockedCharacter,
                maxPlayers, requiredDeterminismFingerprint, voteTrackKeys, false);
    }
}
