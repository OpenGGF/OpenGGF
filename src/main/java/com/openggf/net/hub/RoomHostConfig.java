package com.openggf.net.hub;

import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;

import java.util.List;

/** Immutable parameters for a player-hosted direct room. */
@com.openggf.game.ModApi
public record RoomHostConfig(String roomName, String gameId, int zone, int act,
                             String characterPolicy, String lockedCharacter,
                             int maxPlayers, String requiredDeterminismFingerprint,
                             List<String> voteTrackKeys, boolean verified,
                             RoomAccessPolicy accessPolicy, RoomPassword password,
                             String instanceId, String chatMode) {
    public RoomHostConfig {
        java.util.Objects.requireNonNull(accessPolicy, "access policy");
        if (accessPolicy.passwordRequired() != (password != null)) {
            throw new IllegalArgumentException("room credentials do not match access mode");
        }
        instanceId = instanceId == null ? java.util.UUID.randomUUID().toString() : instanceId;
        if (instanceId.isBlank() || instanceId.length() > 128) {
            throw new IllegalArgumentException("invalid room instance");
        }
        if (ChatMode.parse(chatMode) == null) {
            throw new IllegalArgumentException("invalid chat mode");
        }
        maxPlayers = Math.min(Math.max(maxPlayers, 1), Protocol.MAX_PLAYERS_RELAY);
        voteTrackKeys = List.copyOf(voteTrackKeys == null ? List.of() : voteTrackKeys);
    }

    public RoomHostConfig(String roomName, String gameId, int zone, int act,
                          String characterPolicy, String lockedCharacter,
                          int maxPlayers, String requiredDeterminismFingerprint,
                          List<String> voteTrackKeys, boolean verified,
                          RoomAccessPolicy accessPolicy, RoomPassword password, String instanceId) {
        this(roomName, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                requiredDeterminismFingerprint, voteTrackKeys, verified, accessPolicy,
                password, instanceId, ChatMode.FULL_CHAT.name());
    }

    public RoomHostConfig(String roomName, String gameId, int zone, int act,
                          String characterPolicy, String lockedCharacter,
                          int maxPlayers, String requiredDeterminismFingerprint,
                          List<String> voteTrackKeys, boolean verified, String chatMode) {
        this(roomName, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                requiredDeterminismFingerprint, voteTrackKeys, verified,
                RoomAccessPolicy.PUBLIC, null, null, chatMode);
    }

    public RoomHostConfig(String roomName, String gameId, int zone, int act,
                          String characterPolicy, String lockedCharacter,
                          int maxPlayers, String requiredDeterminismFingerprint,
                          List<String> voteTrackKeys, boolean verified) {
        this(roomName, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                requiredDeterminismFingerprint, voteTrackKeys, verified,
                RoomAccessPolicy.PUBLIC, null, null, ChatMode.FULL_CHAT.name());
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
