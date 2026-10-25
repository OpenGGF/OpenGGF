package com.openggf.net.protocol;

import java.util.List;

/** Versioned JSON control-channel messages. */
@com.openggf.game.ModApi
public sealed interface ControlMessage {
    @com.openggf.game.ModApi
    record RoomDescriptor(String name, String gameId, int zone, int act, String characterPolicy,
                          String lockedCharacter, int maxPlayers, boolean verified,
                          RoomAccessPolicy accessPolicy) {
        public RoomDescriptor {
            java.util.Objects.requireNonNull(accessPolicy, "access policy");
        }

        public RoomDescriptor(String name, String gameId, int zone, int act,
                              String characterPolicy, String lockedCharacter,
                              int maxPlayers, boolean verified) {
            this(name, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                    verified, RoomAccessPolicy.PUBLIC);
        }
    }

    @com.openggf.game.ModApi
    record PlayerInfo(int slot, String fingerprint, String displayName, String character,
                      boolean newPlayer) {
        public PlayerInfo(int slot, String fingerprint, String displayName, String character) {
            this(slot, fingerprint, displayName, character, false);
        }
    }

    @com.openggf.game.ModApi
    record RoundConfig(String gameId, int zone, int act, int windowSeconds,
                       String characterPolicy, String lockedCharacter) {
    }

    @com.openggf.game.ModApi
    record RoundSnapshot(String phase, RoundConfig config, long countdownEndsAtHubMillis,
                         long deadlineHubMillis, List<StandingsRow> standings) {
    }

    @com.openggf.game.ModApi
    record StandingsRow(int slot, String displayName, String character,
                        int bestTimeFrames, int rank, String verifyState) {
    }

    @com.openggf.game.ModApi
    record RoomSummary(String roomId, String name, String gameId, int zone, int act,
                       String characterPolicy, int playerCount, int maxPlayers,
                       String routing, boolean verified) {
    }

    /**
     * Join request. {@code appearance} is the canonical {@link
     * com.openggf.net.identity.PlayerAppearance} wire form for the player's current character
     * ({@code ""} for the stock look); it is part of the signed transcript.
     */
    @com.openggf.game.ModApi
    record Hello(int protocolVersion, String pubKeyBase64, String displayName,
                 String determinismFingerprint, String appearance) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Welcome(int protocolVersion, String nonceBase64, String serverId)
            implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record AuthProof(String signatureBase64) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record JoinAccepted(String sessionToken, int playerSlot, RoomDescriptor room,
                        RoundSnapshot round) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record JoinRejected(String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Kick(String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomState(List<PlayerInfo> players) implements ControlMessage {
    }

    /** Replaces metadata for one occupied slot after the initial RoomState. */
    @com.openggf.game.ModApi
    record PlayerUpsert(PlayerInfo player) implements ControlMessage {
    }

    /** Removes one occupied slot after the initial RoomState. */
    @com.openggf.game.ModApi
    record PlayerLeft(int slot) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record SelectCharacter(String character) implements ControlMessage {
    }

    /**
     * Client to host, lobby only: replaces the sender's display name and look without a new
     * identity. Both fields are canonical ({@link com.openggf.net.identity.DisplayNames},
     * {@link com.openggf.net.identity.PlayerAppearance}); the host answers with
     * {@link PlayerUpsert} and {@link PlayerLook}.
     */
    @com.openggf.game.ModApi
    record UpdateProfile(String displayName, String appearance) implements ControlMessage {
    }

    /**
     * Host to clients: one player's look. Sent per player rather than inside {@link RoomState},
     * which must hold a full 256-player roster in one frame; a stock look is {@code ""}.
     */
    @com.openggf.game.ModApi
    record PlayerLook(int slot, String appearance) implements ControlMessage {
    }

    /**
     * Client to host between rounds: the sender is ready (or no longer ready). Ready is a
     * visible signal; under a {@link ReadyRule} that requires it, the room owner's round start
     * waits for every present player. The host answers with {@link PlayerReady}.
     */
    @com.openggf.game.ModApi
    record SetReady(boolean ready) implements ControlMessage {
    }

    /** Host to clients: one player's ready state. {@link RoundStart} clears everyone's. */
    @com.openggf.game.ModApi
    record PlayerReady(int slot, boolean ready) implements ControlMessage {
    }

    /** Host to clients: every present player's ready state becomes {@code ready} at once. */
    @com.openggf.game.ModApi
    record ReadyReset(boolean ready) implements ControlMessage {
    }

    /**
     * Room owner to host between rounds: the room's ready rule, one of
     * {@link com.openggf.net.protocol.ReadyRules} by name. Anything else is ignored.
     */
    @com.openggf.game.ModApi
    record SetReadyRule(String rule) implements ControlMessage {
    }

    /**
     * Host to clients: the room's ready rule and whether, with the players present now, the
     * owner's round start waits for every player to be ready.
     */
    @com.openggf.game.ModApi
    record ReadyRule(String rule, boolean required) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Chat(String text) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record ChatBroadcast(int slot, String displayName, String text) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Ping(long t0ClientMillis) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Pong(long t0ClientMillis, long hubMillis) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoundConfigure(RoundConfig config) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoundStart(RoundConfig config, long countdownEndsAtHubMillis,
                      long deadlineHubMillis) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoundEnd(List<StandingsRow> finalStandings) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record StandingsDelta(List<StandingsRow> rows) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record AttemptStart(int attemptId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record AttemptFinish(int attemptId, int timeFrames, int firstInputFrame, int finishFrame,
                         String inputRecordingHashHex, String ghostStreamHashHex,
                         String inputRecordingRef) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record AttemptReset(int attemptId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record TrackVote(String trackKey) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record TrackVoteOffer(List<String> trackKeys, long voteEndsAtHubMillis)
            implements ControlMessage {
        public TrackVoteOffer {
            trackKeys = validatedTrackKeys(trackKeys, 8);
        }
    }

    @com.openggf.game.ModApi
    record VoteCount(String trackKey, int votes) {
        public VoteCount {
            validatedTrackKey(trackKey);
            if (votes < 0) {
                throw new IllegalArgumentException("negative vote count");
            }
        }
    }

    @com.openggf.game.ModApi
    record TrackVoteTally(List<VoteCount> counts) implements ControlMessage {
        public TrackVoteTally {
            counts = List.copyOf(counts == null ? List.of() : counts);
            if (counts.size() > 8) {
                throw new IllegalArgumentException("too many vote counts");
            }
        }
    }

    @com.openggf.game.ModApi
    record TrackVoteResult(String trackKey) implements ControlMessage {
        public TrackVoteResult {
            validatedTrackKey(trackKey);
        }
    }

    @com.openggf.game.ModApi
    record RecordingRequest(int attemptId, String expectedHashHex, String uploadUrl)
            implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomCreate(RoomDescriptor room, String routing, int directPort,
                      String determinismFingerprint, List<String> voteTrackKeys,
                      String certificateSha256, String instanceId, long accessEpoch,
                      RoomPassword password)
            implements ControlMessage {
        public RoomCreate {
            voteTrackKeys = validatedTrackKeys(voteTrackKeys, 32);
        }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint, List<String> voteTrackKeys,
                          String certificateSha256) {
            this(room, routing, directPort, determinismFingerprint, voteTrackKeys,
                    certificateSha256, null, 0, null);
        }

        @Override
        public String toString() { return "RoomCreate[redacted]"; }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint) {
            this(room, routing, directPort, determinismFingerprint, List.of(), null);
        }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint, List<String> voteTrackKeys) {
            this(room, routing, directPort, determinismFingerprint, voteTrackKeys, null);
        }
    }

    @com.openggf.game.ModApi
    record RoomCreated(String roomId, String instanceId, long accessEpoch, long inviteEpoch)
            implements ControlMessage {
        public RoomCreated(String roomId) { this(roomId, null, 0, 0); }
    }

    @com.openggf.game.ModApi
    record RoomCreateRejected(String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomListRequest(String gameFilter, int page) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomListResult(List<RoomSummary> rooms, int page, int totalPages)
            implements ControlMessage {
        public RoomListResult {
            rooms = List.copyOf(rooms);
        }
    }

    @com.openggf.game.ModApi
    record RoomJoinRequest(String roomId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomJoinResult(String roomId, String routing, String directHost,
                          int directPort, String hostServerId,
                          String determinismFingerprint, String certificateSha256,
                          String instanceId, RoomAccessPolicy accessPolicy, long accessEpoch,
                          long inviteEpoch, long inviteExpiresAtMillis)
            implements ControlMessage {
        public RoomJoinResult {
            java.util.Objects.requireNonNull(accessPolicy, "access policy");
            if (accessEpoch < 0 || inviteEpoch < 0 || inviteExpiresAtMillis < 0) {
                throw new IllegalArgumentException("invalid room access metadata");
            }
        }

        public RoomJoinResult(String roomId, String routing, String directHost,
                              int directPort, String hostServerId,
                              String determinismFingerprint, String certificateSha256) {
            this(roomId, routing, directHost, directPort, hostServerId,
                    determinismFingerprint, certificateSha256, null,
                    RoomAccessPolicy.PUBLIC, 0, 0, 0);
        }

        public RoomJoinResult(String roomId, String routing, String directHost,
                              int directPort, String hostServerId,
                              String determinismFingerprint) {
            this(roomId, routing, directHost, directPort, hostServerId,
                    determinismFingerprint, null);
        }
    }

    @com.openggf.game.ModApi
    record RoomJoinRejected(String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomLeave(String roomId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record Heartbeat(String roomId, int playerCount) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record PowChallenge(String kind, String prefixBase64, int difficultyBits)
            implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record PowSolution(String kind, long nonce) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RelayAttach(String roomId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RelayGuestOpen(int guestId) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RelayGuestClose(int guestId, String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RelayGuestText(int guestId, String text) implements ControlMessage {
        @Override
        public String toString() { return "RelayGuestText[redacted]"; }
    }

    @com.openggf.game.ModApi
    record StandingsPageRequest(int page) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record StandingsPage(List<StandingsRow> rows, int page, int totalPages)
            implements ControlMessage {
        public StandingsPage {
            rows = List.copyOf(rows);
        }
    }

    @com.openggf.game.ModApi
    record RankUpdate(int rank, int bestTimeFrames) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomTrackUpdate(String roomId, int zone, int act) implements ControlMessage {
        public RoomTrackUpdate {
            if (roomId == null || roomId.isBlank() || zone < 0 || zone > 999
                    || act < 0 || act > 999) {
                throw new IllegalArgumentException("invalid room track update");
            }
        }
    }

    @com.openggf.game.ModApi
    record RoomAccessChallenge(String challengeId, String instanceId, long accessEpoch,
                               RoomAccessPolicy accessPolicy) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomAccessSubmit(String challengeId, RoomPassword password) implements ControlMessage {
        @Override
        public String toString() { return "RoomAccessSubmit[redacted]"; }
    }

    @com.openggf.game.ModApi
    record RoomAccessState(String instanceId, RoomAccessPolicy accessPolicy,
                           long accessEpoch, long inviteEpoch) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomPasswordChange(RoomPassword password) implements ControlMessage {
        @Override
        public String toString() { return "RoomPasswordChange[redacted]"; }
    }

    @com.openggf.game.ModApi
    record RoomPasswordChanged(long accessEpoch) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteRequest() implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteRotate() implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteSharing(boolean membersCanInvite) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInvite(String code, String instanceId, long accessEpoch, long inviteEpoch,
                      long expiresAtMillis) implements ControlMessage {
        @Override
        public String toString() { return "RoomInvite[redacted]"; }
    }

    @com.openggf.game.ModApi
    record RoomInviteRejected(String reason, long retryAfterMillis) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteResolve(String code) implements ControlMessage {
        @Override
        public String toString() { return "RoomInviteResolve[redacted]"; }
    }

    @com.openggf.game.ModApi
    enum InviteOperation { FETCH, ROTATE, SET_SHARING, ACCESS_CHANGED }

    @com.openggf.game.ModApi
    record RoomInviteControl(String roomId, String instanceId, long accessEpoch,
                             long inviteEpoch, InviteOperation operation,
                             boolean membersCanInvite) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteControlResult(RoomAccessPolicy accessPolicy, RoomInvite invite)
            implements ControlMessage { }

    private static List<String> validatedTrackKeys(List<String> keys, int maxCount) {
        List<String> result = List.copyOf(keys == null ? List.of() : keys);
        if (result.size() > maxCount) {
            throw new IllegalArgumentException("too many track keys");
        }
        result.forEach(ControlMessage::validatedTrackKey);
        return result;
    }

    private static void validatedTrackKey(String key) {
        if (key == null || key.length() > 32
                || !key.matches("[a-z0-9]{1,8}:[0-9]{1,3}:[0-9]{1,3}")) {
            throw new IllegalArgumentException("invalid track key");
        }
    }
}
