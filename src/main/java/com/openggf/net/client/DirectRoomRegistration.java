package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Internal engine adapter for advertising direct-room certificate pins. */
public final class DirectRoomRegistration {
    private DirectRoomRegistration() {
    }

    public static CompletableFuture<ControlMessage.RoomCreated> createRoom(
            MasterClient master, ControlMessage.RoomDescriptor descriptor,
            String routing, int directPort, String determinismFingerprint,
            List<String> voteTrackKeys, String certificateSha256) {
        return master.createRoom(descriptor, routing, directPort,
                determinismFingerprint, voteTrackKeys, certificateSha256);
    }
}
