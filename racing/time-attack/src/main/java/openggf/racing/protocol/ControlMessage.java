package openggf.racing.protocol;

import java.util.List;

/** Versioned JSON control-channel messages. */
public sealed interface ControlMessage {
    record RoomDescriptor(String name, String gameId, int zone, int act, String characterPolicy,
                          String lockedCharacter, int maxPlayers, boolean verified) {
    }

    record PlayerInfo(int slot, String fingerprint, String displayName, String character,
                      boolean newPlayer) {
        public PlayerInfo(int slot, String fingerprint, String displayName, String character) {
            this(slot, fingerprint, displayName, character, false);
        }
    }

    record RoundConfig(String gameId, int zone, int act, int windowSeconds,
                       String characterPolicy, String lockedCharacter) {
    }

    record RoundSnapshot(String phase, RoundConfig config, long countdownEndsAtHubMillis,
                         long deadlineHubMillis, List<StandingsRow> standings) {
    }

    record StandingsRow(int slot, String displayName, String character,
                        int bestTimeFrames, int rank, String verifyState) {
    }

    record RoomSummary(String roomId, String name, String gameId, int zone, int act,
                       String characterPolicy, int playerCount, int maxPlayers,
                       String routing, boolean verified) {
    }

    record Hello(int protocolVersion, String pubKeyBase64, String displayName,
                 String determinismFingerprint) implements ControlMessage {
    }

    record Welcome(int protocolVersion, String nonceBase64, String serverId)
            implements ControlMessage {
    }

    record AuthProof(String signatureBase64) implements ControlMessage {
    }

    record JoinAccepted(String sessionToken, int playerSlot, RoomDescriptor room,
                        RoundSnapshot round) implements ControlMessage {
    }

    record JoinRejected(String reason) implements ControlMessage {
    }

    record Kick(String reason) implements ControlMessage {
    }

    record RoomState(List<PlayerInfo> players) implements ControlMessage {
    }

    record SelectCharacter(String character) implements ControlMessage {
    }

    record Chat(String text) implements ControlMessage {
    }

    record ChatBroadcast(int slot, String displayName, String text) implements ControlMessage {
    }

    record Ping(long t0ClientMillis) implements ControlMessage {
    }

    record Pong(long t0ClientMillis, long hubMillis) implements ControlMessage {
    }

    record RoundConfigure(RoundConfig config) implements ControlMessage {
    }

    record RoundStart(RoundConfig config, long countdownEndsAtHubMillis,
                      long deadlineHubMillis) implements ControlMessage {
    }

    record RoundEnd(List<StandingsRow> finalStandings) implements ControlMessage {
    }

    record StandingsDelta(List<StandingsRow> rows) implements ControlMessage {
    }

    record AttemptStart(int attemptId) implements ControlMessage {
    }

    record AttemptFinish(int attemptId, int timeFrames, int firstInputFrame, int finishFrame,
                         String inputRecordingHashHex, String ghostStreamHashHex,
                         String inputRecordingRef) implements ControlMessage {
    }

    record AttemptReset(int attemptId) implements ControlMessage {
    }

    record TrackVote(String trackKey) implements ControlMessage {
    }

    record TrackVoteOffer(List<String> trackKeys, long voteEndsAtHubMillis)
            implements ControlMessage {
        public TrackVoteOffer {
            trackKeys = validatedTrackKeys(trackKeys, 8);
        }
    }

    record VoteCount(String trackKey, int votes) {
        public VoteCount {
            validatedTrackKey(trackKey);
            if (votes < 0) {
                throw new IllegalArgumentException("negative vote count");
            }
        }
    }

    record TrackVoteTally(List<VoteCount> counts) implements ControlMessage {
        public TrackVoteTally {
            counts = List.copyOf(counts == null ? List.of() : counts);
            if (counts.size() > 8) {
                throw new IllegalArgumentException("too many vote counts");
            }
        }
    }

    record TrackVoteResult(String trackKey) implements ControlMessage {
        public TrackVoteResult {
            validatedTrackKey(trackKey);
        }
    }

    record RecordingRequest(int attemptId, String expectedHashHex, String uploadUrl)
            implements ControlMessage {
    }

    record RoomCreate(RoomDescriptor room, String routing, int directPort,
                      String determinismFingerprint, List<String> voteTrackKeys,
                      String certificateSha256)
            implements ControlMessage {
        public RoomCreate {
            voteTrackKeys = validatedTrackKeys(voteTrackKeys, 32);
        }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint) {
            this(room, routing, directPort, determinismFingerprint, List.of(), null);
        }

        public RoomCreate(RoomDescriptor room, String routing, int directPort,
                          String determinismFingerprint, List<String> voteTrackKeys) {
            this(room, routing, directPort, determinismFingerprint, voteTrackKeys, null);
        }
    }

    record RoomCreated(String roomId) implements ControlMessage {
    }

    record RoomCreateRejected(String reason) implements ControlMessage {
    }

    record RoomListRequest(String gameFilter, int page) implements ControlMessage {
    }

    record RoomListResult(List<RoomSummary> rooms, int page, int totalPages)
            implements ControlMessage {
        public RoomListResult {
            rooms = List.copyOf(rooms);
        }
    }

    record RoomJoinRequest(String roomId) implements ControlMessage {
    }

    record RoomJoinResult(String roomId, String routing, String directHost,
                          int directPort, String hostServerId,
                          String determinismFingerprint, String certificateSha256)
            implements ControlMessage {
        public RoomJoinResult(String roomId, String routing, String directHost,
                              int directPort, String hostServerId,
                              String determinismFingerprint) {
            this(roomId, routing, directHost, directPort, hostServerId,
                    determinismFingerprint, null);
        }
    }

    record RoomJoinRejected(String reason) implements ControlMessage {
    }

    record RoomLeave(String roomId) implements ControlMessage {
    }

    record Heartbeat(String roomId, int playerCount) implements ControlMessage {
    }

    record PowChallenge(String kind, String prefixBase64, int difficultyBits)
            implements ControlMessage {
    }

    record PowSolution(String kind, long nonce) implements ControlMessage {
    }

    record RelayAttach(String roomId) implements ControlMessage {
    }

    record RelayGuestOpen(int guestId) implements ControlMessage {
    }

    record RelayGuestClose(int guestId, String reason) implements ControlMessage {
    }

    record RelayGuestText(int guestId, String text) implements ControlMessage {
    }

    record StandingsPageRequest(int page) implements ControlMessage {
    }

    record StandingsPage(List<StandingsRow> rows, int page, int totalPages)
            implements ControlMessage {
        public StandingsPage {
            rows = List.copyOf(rows);
        }
    }

    record RankUpdate(int rank, int bestTimeFrames) implements ControlMessage {
    }

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
