package com.openggf.net.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/** Versioned JSON control-channel messages. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ControlMessage.Hello.class, name = "Hello"),
        @JsonSubTypes.Type(value = ControlMessage.Welcome.class, name = "Welcome"),
        @JsonSubTypes.Type(value = ControlMessage.AuthProof.class, name = "AuthProof"),
        @JsonSubTypes.Type(value = ControlMessage.JoinAccepted.class, name = "JoinAccepted"),
        @JsonSubTypes.Type(value = ControlMessage.JoinRejected.class, name = "JoinRejected"),
        @JsonSubTypes.Type(value = ControlMessage.Kick.class, name = "Kick"),
        @JsonSubTypes.Type(value = ControlMessage.RoomState.class, name = "RoomState"),
        @JsonSubTypes.Type(value = ControlMessage.SelectCharacter.class, name = "SelectCharacter"),
        @JsonSubTypes.Type(value = ControlMessage.Chat.class, name = "Chat"),
        @JsonSubTypes.Type(value = ControlMessage.ChatBroadcast.class, name = "ChatBroadcast"),
        @JsonSubTypes.Type(value = ControlMessage.Ping.class, name = "Ping"),
        @JsonSubTypes.Type(value = ControlMessage.Pong.class, name = "Pong"),
        @JsonSubTypes.Type(value = ControlMessage.RoundConfigure.class, name = "RoundConfigure"),
        @JsonSubTypes.Type(value = ControlMessage.RoundStart.class, name = "RoundStart"),
        @JsonSubTypes.Type(value = ControlMessage.RoundEnd.class, name = "RoundEnd"),
        @JsonSubTypes.Type(value = ControlMessage.StandingsDelta.class, name = "StandingsDelta"),
        @JsonSubTypes.Type(value = ControlMessage.AttemptStart.class, name = "AttemptStart"),
        @JsonSubTypes.Type(value = ControlMessage.AttemptFinish.class, name = "AttemptFinish"),
        @JsonSubTypes.Type(value = ControlMessage.AttemptReset.class, name = "AttemptReset"),
        @JsonSubTypes.Type(value = ControlMessage.TrackVote.class, name = "TrackVote"),
        @JsonSubTypes.Type(value = ControlMessage.RecordingRequest.class, name = "RecordingRequest"),
        @JsonSubTypes.Type(value = ControlMessage.RoomCreate.class, name = "RoomCreate"),
        @JsonSubTypes.Type(value = ControlMessage.RoomCreated.class, name = "RoomCreated"),
        @JsonSubTypes.Type(value = ControlMessage.RoomCreateRejected.class, name = "RoomCreateRejected"),
        @JsonSubTypes.Type(value = ControlMessage.RoomListRequest.class, name = "RoomListRequest"),
        @JsonSubTypes.Type(value = ControlMessage.RoomListResult.class, name = "RoomListResult"),
        @JsonSubTypes.Type(value = ControlMessage.RoomJoinRequest.class, name = "RoomJoinRequest"),
        @JsonSubTypes.Type(value = ControlMessage.RoomJoinResult.class, name = "RoomJoinResult"),
        @JsonSubTypes.Type(value = ControlMessage.RoomJoinRejected.class, name = "RoomJoinRejected"),
        @JsonSubTypes.Type(value = ControlMessage.RoomLeave.class, name = "RoomLeave"),
        @JsonSubTypes.Type(value = ControlMessage.Heartbeat.class, name = "Heartbeat"),
        @JsonSubTypes.Type(value = ControlMessage.PowChallenge.class, name = "PowChallenge"),
        @JsonSubTypes.Type(value = ControlMessage.PowSolution.class, name = "PowSolution"),
        @JsonSubTypes.Type(value = ControlMessage.RelayAttach.class, name = "RelayAttach"),
        @JsonSubTypes.Type(value = ControlMessage.RelayGuestOpen.class, name = "RelayGuestOpen"),
        @JsonSubTypes.Type(value = ControlMessage.RelayGuestClose.class, name = "RelayGuestClose"),
        @JsonSubTypes.Type(value = ControlMessage.RelayGuestText.class, name = "RelayGuestText"),
        @JsonSubTypes.Type(value = ControlMessage.StandingsPageRequest.class, name = "StandingsPageRequest"),
        @JsonSubTypes.Type(value = ControlMessage.StandingsPage.class, name = "StandingsPage"),
        @JsonSubTypes.Type(value = ControlMessage.RankUpdate.class, name = "RankUpdate")
})
public sealed interface ControlMessage {
    record RoomDescriptor(String name, String gameId, int zone, int act, String characterPolicy,
                          String lockedCharacter, int maxPlayers, boolean verified) {
    }

    record PlayerInfo(int slot, String fingerprint, String displayName, String character) {
    }

    record RoundConfig(String gameId, int zone, int act, int windowSeconds,
                       String characterPolicy, String lockedCharacter) {
    }

    record RoundSnapshot(String phase, RoundConfig config, long countdownEndsAtHubMillis,
                         long deadlineHubMillis, List<StandingsRow> standings) {
    }

    record StandingsRow(int slot, String displayName, String character,
                        int bestTimeFrames, int rank) {
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

    record RecordingRequest(int attemptId, String expectedHashHex, String uploadUrl)
            implements ControlMessage {
    }

    record RoomCreate(RoomDescriptor room, String routing, int directPort,
                      String determinismFingerprint) implements ControlMessage {
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
                          String determinismFingerprint) implements ControlMessage {
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
}
