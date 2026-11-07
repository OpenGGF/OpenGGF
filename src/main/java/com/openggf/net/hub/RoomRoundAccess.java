package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.function.Consumer;

/** Internal relay bridge for result ownership without expanding the mod-facing room API. */
public final class RoomRoundAccess {
    public record Result(int slot, String participantId, String fingerprint,
                         String character, ControlMessage.AttemptFinish finish) { }

    private RoomRoundAccess() { }

    public static void configureIdentity(RoomHost room, ControlMessage.RoomDescriptor descriptor) {
        room.configureIdentity(descriptor);
    }
    public static void publishUnverifiedTimeAttack(RoomHost room, boolean publish) {
        room.round().setPublishUnverifiedStandings(publish);
    }
    public static List<Result> results(RoomHost room) {
        return room.round().results().stream()
                .map(result -> new Result(result.slot(), result.participantId(),
                        result.fingerprint(), result.character(), result.finish()))
                .toList();
    }

    public static void onVerdictForParticipant(RoomHost room, String participantId,
                                                int attemptId, String recordingHash,
                                                boolean pass) {
        if (!pass) room.round().results().stream().filter(r -> r.participantId().equals(participantId)
                && r.finish().attemptId() == attemptId && r.finish().inputRecordingHashHex().equals(recordingHash))
                .findFirst().ifPresent(r -> room.taintSwaps(r.slot()));
        room.round().onVerdictForParticipant(participantId, attemptId,
                recordingHash, pass);
    }

    public static void onPendingResultExpiry(RoomHost room,
                                              Consumer<Result> listener) {
        room.round().setPendingResultExpiryListener(result -> listener.accept(new Result(
                result.slot(), result.participantId(), result.fingerprint(),
                result.character(), result.finish())));
    }

    public static String participantIdForSlot(RoomHost room, int slot,
                                               String fingerprint) {
        return room.participantIdForSlot(slot, fingerprint);
    }

    public static void sendToParticipantInSlot(RoomHost room, int slot,
                                                String participantId,
                                                ControlMessage message) {
        room.sendToParticipantInSlot(slot, participantId, message);
    }
}
