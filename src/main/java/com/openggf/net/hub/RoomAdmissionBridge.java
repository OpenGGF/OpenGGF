package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Internal bridge from authenticated registration and transport owners to room admission. */
public final class RoomAdmissionBridge {
    private RoomAdmissionBridge() { }

    public static void install(RoomHost room, String roomId, RoomInviteService invites,
                               long inviteEpoch,
                               Function<ControlMessage.RoomGrantRedeem,
                                       CompletableFuture<ControlMessage.RoomGrantDecision>> redeem) {
        room.brokerRequired(roomId, (reference, generation, fingerprint) -> redeem.apply(
                new ControlMessage.RoomGrantRedeem(roomId, room.instanceId(), reference,
                        generation, fingerprint)));
        room.setInviteService(invites, inviteEpoch);
    }

    public static void connectTunnel(RoomHost room, HubConnection connection,
                                     ControlMessage.RelayGuestOpen trustedTunnel) {
        room.onConnected(connection, trustedTunnel);
    }

    public static void connectRelay(RoomHost room, HubConnection connection, String fingerprint,
                                    String grantReference, long routeGeneration) {
        room.onConnectedRelay(connection, fingerprint, grantReference, routeGeneration);
    }
}
