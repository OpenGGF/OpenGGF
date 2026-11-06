package com.openggf.net.protocol;

import java.util.List;

/** Versioned JSON control-channel messages. */
@com.openggf.game.ModApi
public sealed interface ControlMessage {
    @com.openggf.game.ModApi
    record RoomDescriptor(String name, String gameId, int zone, int act, String characterPolicy,
                          String lockedCharacter, int maxPlayers, boolean verified,
                          RoomAccessPolicy accessPolicy, String chatMode) {
        public RoomDescriptor {
            java.util.Objects.requireNonNull(accessPolicy, "access policy");
            validatedChatMode(chatMode);
        }

        public RoomDescriptor(String name, String gameId, int zone, int act,
                              String characterPolicy, String lockedCharacter,
                              int maxPlayers, boolean verified, RoomAccessPolicy accessPolicy) {
            this(name, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                    verified, accessPolicy, ChatMode.FULL_CHAT.name());
        }

        public RoomDescriptor(String name, String gameId, int zone, int act,
                              String characterPolicy, String lockedCharacter,
                              int maxPlayers, boolean verified, String chatMode) {
            this(name, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                    verified, RoomAccessPolicy.PUBLIC, chatMode);
        }

        public RoomDescriptor(String name, String gameId, int zone, int act,
                              String characterPolicy, String lockedCharacter,
                              int maxPlayers, boolean verified) {
            this(name, gameId, zone, act, characterPolicy, lockedCharacter, maxPlayers,
                    verified, RoomAccessPolicy.PUBLIC, ChatMode.FULL_CHAT.name());
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
    record RaceLaunch(long roundId, long planRevision, RoundConfig config,
                      String participantId, int slot, String fingerprint,
                      String effectiveCharacter, long countdownEndsAtHubMillis,
                      long deadlineHubMillis) {
    }

    @com.openggf.game.ModApi
    record RoundSnapshot(String phase, RoundConfig config, long countdownEndsAtHubMillis,
                         long deadlineHubMillis, List<StandingsRow> standings,
                         RaceLaunch launch) {
        public RoundSnapshot(String phase, RoundConfig config, long countdownEndsAtHubMillis,
                             long deadlineHubMillis, List<StandingsRow> standings) {
            this(phase, config, countdownEndsAtHubMillis, deadlineHubMillis, standings, null);
        }
    }

    @com.openggf.game.ModApi
    record StandingsRow(int slot, String displayName, String character,
                        int bestTimeFrames, int rank, String verifyState) {
    }

    @com.openggf.game.ModApi
    record RoomSummary(String roomId, String name, String gameId, int zone, int act,
                       String characterPolicy, int playerCount, int maxPlayers,
                       String routing, boolean verified, String chatMode) {
        public RoomSummary {
            validatedChatMode(chatMode);
        }

        public RoomSummary(String roomId, String name, String gameId, int zone, int act,
                           String characterPolicy, int playerCount, int maxPlayers,
                           String routing, boolean verified) {
            this(roomId, name, gameId, zone, act, characterPolicy, playerCount, maxPlayers,
                    routing, verified, ChatMode.FULL_CHAT.name());
        }
    }

    /**
     * Join request. {@code appearance} is the canonical {@link
     * com.openggf.net.identity.PlayerAppearance} wire form for the player's current character
     * ({@code ""} for the stock look); it is part of the signed transcript.
     */
    @com.openggf.game.ModApi
    record Hello(int protocolVersion, String pubKeyBase64, String displayName,
                 String determinismFingerprint, String appearance,
                 String grantReference, long routeGeneration) implements ControlMessage { }

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
        @Override
        public String toString() { return "JoinAccepted[redacted]"; }
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

    /** Owner to host between rounds: requested capabilities, subject to the host's ceiling. */
    @com.openggf.game.ModApi
    record SetChatMode(String mode) implements ControlMessage {
    }

    /** Host to clients on admission and whenever the effective room mode changes. */
    @com.openggf.game.ModApi
    record ChatModeState(String mode) implements ControlMessage {
        public ChatModeState {
            validatedChatMode(mode);
        }
    }

    /** Client to host: a catalogue ID without sender or template context. */
    @com.openggf.game.ModApi
    record QuickChat(String phraseId) implements ControlMessage {
    }

    /** Host to clients: authoritative sender identity and current track context. */
    @com.openggf.game.ModApi
    record QuickChatBroadcast(int slot, String displayName, String phraseId,
                              String gameId, int zone, int act) implements ControlMessage {
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
    record RoundConfigure(RoundConfig config, long planRevision) implements ControlMessage {
        public RoundConfigure(RoundConfig config) {
            this(config, 0);
        }
    }

    @com.openggf.game.ModApi
    record RoundStart(RoundConfig config, long countdownEndsAtHubMillis,
                      long deadlineHubMillis, RaceLaunch launch) implements ControlMessage {
        public RoundStart(RoundConfig config, long countdownEndsAtHubMillis,
                          long deadlineHubMillis) {
            this(config, countdownEndsAtHubMillis, deadlineHubMillis, null);
        }
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
    record TrackVoteResult(String trackKey, RoundConfig nextConfig,
                           long planRevision) implements ControlMessage {
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
            java.util.Objects.requireNonNull(room, "room");
            if (accessEpoch < 0 || password != null && !room.accessPolicy().passwordRequired()
                    || "DIRECT".equals(routing) && password != null
                    || room.accessPolicy().passwordRequired() && ("DIRECT".equals(routing)
                    ? instanceId == null || instanceId.isBlank() : password == null)) {
                throw new IllegalArgumentException("invalid room creation access");
            }
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

    /** Publishes a direct room after its local admission bridge is installed. */
    @com.openggf.game.ModApi
    record RoomActivate(String roomId, String instanceId) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomActivated(String roomId, String instanceId, boolean active)
            implements ControlMessage { }

    @com.openggf.game.ModApi
    record MasterRequestCancel(long requestId) implements ControlMessage {
        public MasterRequestCancel {
            if (requestId <= 0) throw new IllegalArgumentException("invalid cancellation request ID");
        }
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
                          long inviteEpoch, long inviteExpiresAtMillis,
                          String grantReference, long routeGeneration)
            implements ControlMessage {
        public RoomJoinResult {
            java.util.Objects.requireNonNull(accessPolicy, "access policy");
            if (accessEpoch < 0 || inviteEpoch < 0 || inviteExpiresAtMillis < 0
                    || accessPolicy.passwordRequired() && (instanceId == null || instanceId.isBlank())
                    || (grantReference == null) != (routeGeneration == 0)
                    || grantReference != null && (grantReference.isBlank()
                    || grantReference.length() > 128 || routeGeneration < 1)) {
                throw new IllegalArgumentException("invalid room access metadata");
            }
        }

    }

    @com.openggf.game.ModApi
    record RoomJoinRejected(String reason) implements ControlMessage {
    }

    @com.openggf.game.ModApi
    record RoomGrantRedeem(String roomId, String instanceId, String grantReference,
                           long routeGeneration, String fingerprint) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomGrantDecision(String roomId, String instanceId, String grantReference,
                             long routeGeneration, String fingerprint,
                             boolean allowed) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomRouteRetire(String roomId, String instanceId, String grantReference,
                           long routeGeneration, boolean renew) implements ControlMessage {
        public RoomRouteRetire(String roomId, String instanceId, String grantReference,
                               long routeGeneration) {
            this(roomId, instanceId, grantReference, routeGeneration, true);
        }
    }

    @com.openggf.game.ModApi
    record RoomRouteRetireNotice(String roomId, String instanceId, String grantReference,
                                 long routeGeneration, long retirementId)
            implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomRouteRetireAck(String roomId, String instanceId, String grantReference,
                              long routeGeneration, long retirementId)
            implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomRouteRenewed(RoomJoinResult join) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomRouteRetired(String roomId, String instanceId, String grantReference,
                            long routeGeneration) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomRouteUnavailable(String reason) implements ControlMessage { }

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
    record RelayAttach(String roomId, String grantReference,
                       long routeGeneration) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RelayGuestOpen(int guestId, String roomId, String instanceId,
                          String fingerprint, String grantReference,
                          long routeGeneration, long registrationGeneration)
            implements ControlMessage { }

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
    record RoomInviteRequest(long disclosureId) implements ControlMessage {
        public RoomInviteRequest {
            if (disclosureId < 0) throw new IllegalArgumentException("invalid disclosure ID");
        }
        public RoomInviteRequest() { this(0); }
    }

    @com.openggf.game.ModApi
    record RoomInviteInvalidated(String instanceId) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteRotate() implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInviteSharing(boolean membersCanInvite) implements ControlMessage { }

    @com.openggf.game.ModApi
    record RoomInvite(String code, String instanceId, long accessEpoch, long inviteEpoch,
                      long expiresAtMillis, long disclosureId) implements ControlMessage {
        public RoomInvite {
            if (accessEpoch < 0 || inviteEpoch < 0 || expiresAtMillis < 0 || disclosureId < 0) {
                throw new IllegalArgumentException("invalid room invite metadata");
            }
        }
        public RoomInvite(String code, String instanceId, long accessEpoch, long inviteEpoch,
                          long expiresAtMillis) {
            this(code, instanceId, accessEpoch, inviteEpoch, expiresAtMillis, 0);
        }
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

    /** Direct host to master: the room's current effective communication capabilities. */
    @com.openggf.game.ModApi
    record RoomChatModeUpdate(String roomId, String mode) implements ControlMessage {
        public RoomChatModeUpdate {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("invalid room ID");
            }
            validatedChatMode(mode);
        }
    }

    private static void validatedChatMode(String mode) {
        if (ChatMode.parse(mode) == null) {
            throw new IllegalArgumentException("invalid chat mode");
        }
    }

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
