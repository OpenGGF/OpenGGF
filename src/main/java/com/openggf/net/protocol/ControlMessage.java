package com.openggf.net.protocol;

import java.util.List;

/** Versioned JSON control-channel messages. */
@com.openggf.game.ModApi
public sealed interface ControlMessage {
    @com.openggf.game.ModApi
    record RoomDescriptor(String name, String gameId, int zone, int act, String characterPolicy,
                          String lockedCharacter, int maxPlayers, boolean verified) {
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

    @com.openggf.game.ModApi
    record Hello(int protocolVersion, String pubKeyBase64, String displayName,
                 String determinismFingerprint) implements ControlMessage {
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

    @com.openggf.game.ModApi
    record SelectCharacter(String character) implements ControlMessage {
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
                      String determinismFingerprint, List<String> voteTrackKeys)
            implements ControlMessage {
        public RoomCreate {
            voteTrackKeys = validatedTrackKeys(voteTrackKeys, 32);
        }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint) {
            this(room, routing, directPort, determinismFingerprint, List.of());
        }
    }

    @com.openggf.game.ModApi
    record RoomCreated(String roomId) implements ControlMessage {
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
                          String determinismFingerprint) implements ControlMessage {
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
