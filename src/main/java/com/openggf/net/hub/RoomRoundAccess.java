package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.function.Consumer;

/** Internal relay bridge for result ownership without expanding the mod-facing room API. */
public final class RoomRoundAccess {
    public record Result(int slot, String participantId, String fingerprint,
                         String character, ControlMessage.AttemptFinish finish) { }

    private RoomRoundAccess() { }

    public static List<Result> results(RoomHost room) {
        return room.round().results().stream()
                .map(result -> new Result(result.slot(), result.participantId(),
                        result.fingerprint(), result.character(), result.finish()))
                .toList();
    }

    public static void onVerdictEvidence(RoomHost room, String fingerprint,
                                         int attemptId, String recordingHash,
                                         boolean pass) {
        room.round().onVerdictEvidence(fingerprint, attemptId, recordingHash, pass);
    }

    public static void onPendingResultExpiry(RoomHost room,
                                              Consumer<Result> listener) {
        room.round().setPendingResultExpiryListener(result -> listener.accept(new Result(
                result.slot(), result.participantId(), result.fingerprint(),
                result.character(), result.finish())));
    }

    public static void sendToIdentityInSlot(RoomHost room, int slot,
                                            String fingerprint, ControlMessage message) {
        room.sendToIdentityInSlot(slot, fingerprint, message);
    }
}
