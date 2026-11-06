package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.protocol.ControlMessage;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TestRoomHostBrokerAdmission {
    private static final String FP = "0.6:cafe";

    @Test
    void brokerCompletionRetainsAdmissionAndCanArriveWhenTheProcessIsFull(@TempDir Path dir)
            throws Exception {
        int before = WorkBudget.PROCESS.retainedItems();
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        RoomHost room = new RoomHost(new RoomHostConfig("Listed", "s2", 0, 0,
                "OPEN", null, 8, FP, List.of()), host, () -> 1_000L,
                TrackValidationProfileSource.none());
        CompletableFuture<ControlMessage.RoomGrantDecision> decision = new CompletableFuture<>();
        room.brokerPending();
        room.brokerRequired("listed-room", (reference, generation, fingerprint) -> decision);
        FakeHubConnection connection = new FakeHubConnection();
        ClientHandshake handshake = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_DIRECT,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT, "grant", 1);
        List<WorkBudget.Reservation> filler = new ArrayList<>();
        try {
            room.onConnected(connection);
            room.onText(connection, ControlCodec.encode(null, handshake.hello()));
            ControlMessage.Welcome welcome = assertInstanceOf(ControlMessage.Welcome.class,
                    ControlCodec.decode(connection.text.getLast()).message());
            room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
            int retained = WorkBudget.PROCESS.retainedItems();
            long bytes = WorkBudget.PROCESS.retainedBytes();
            var source = WorkBudget.PROCESS.account(8192, 1);
            for (;;) {
                var reservation = WorkBudget.PROCESS.tryReserve(source, source,
                        new WorkBudget.Cost(1, 0));
                if (reservation.isEmpty()) break;
                filler.add(reservation.get());
            }
            assertTrue(decision.complete(new ControlMessage.RoomGrantDecision("listed-room",
                    room.instanceId(), "grant", 1, guest.fingerprint(), true)));
            room.tick();
            assertAll(() -> assertEquals(before + 1, retained), () -> assertTrue(bytes > 0),
                    () -> assertEquals(1, room.playerCount()));
        } finally {
            filler.forEach(WorkBudget.Reservation::close);
            decision.completeExceptionally(new IllegalStateException("done"));
            room.close("done");
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void pendingBrokerRedemptionSurvivesOwnerTicksUntilItsDeadline(@TempDir Path dir)
            throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        long[] now = {1_000L};
        RoomHost room = new RoomHost(new RoomHostConfig("Listed", "s2", 0, 0,
                "OPEN", null, 8, FP, List.of()), host, () -> now[0],
                TrackValidationProfileSource.none());
        CompletableFuture<ControlMessage.RoomGrantDecision> decision = new CompletableFuture<>();
        room.brokerPending();
        room.brokerRequired("listed-room", (reference, generation, fingerprint) -> decision);
        FakeHubConnection connection = new FakeHubConnection();
        ClientHandshake handshake = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_DIRECT,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                "grant", 1);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = assertInstanceOf(ControlMessage.Welcome.class,
                ControlCodec.decode(connection.text.getLast()).message());
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        now[0] += 100;
        room.tick();
        assertNull(connection.closedReason, "broker reply had not reached its deadline");
        decision.complete(new ControlMessage.RoomGrantDecision("listed-room", room.instanceId(),
                "grant", 1, guest.fingerprint(), true));
        room.tick();
        assertNull(connection.closedReason);
        assertEquals(1, room.playerCount());
    }

    @Test
    void listedListenerRequiresBrokerRedemptionAndClosesItsGateOnLoss(@TempDir Path dir)
            throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        RoomHost room = new RoomHost(new RoomHostConfig("Listed", "s2", 0, 0,
                "OPEN", null, 8, FP, List.of()), host, () -> 1_000L,
                TrackValidationProfileSource.none());
        room.brokerPending();
        FakeHubConnection pending = new FakeHubConnection();
        room.onConnected(pending);
        assertNotNull(pending.closedReason);

        room.brokerRequired("listed-room", (reference, generation, identity) ->
                CompletableFuture.completedFuture(new ControlMessage.RoomGrantDecision(
                        "listed-room", room.instanceId(), reference, generation, identity, false)));
        FakeHubConnection ungranted = new FakeHubConnection();
        room.onConnected(ungranted);
        ClientHandshake manual = new ClientHandshake(guest, "Guest", FP);
        room.onText(ungranted, ControlCodec.encode(null, manual.hello()));
        assertNotNull(ungranted.closedReason);
        assertEquals(0, room.playerCount());

        FakeHubConnection denied = new FakeHubConnection();
        ClientHandshake listed = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_DIRECT,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                "opaque-grant", 1);
        room.onConnected(denied);
        room.onText(denied, ControlCodec.encode(null, listed.hello()));
        ControlMessage.Welcome welcome = denied.text.stream()
                .map(ControlCodec::decode).map(ControlCodec.DecodedControl::message)
                .filter(ControlMessage.Welcome.class::isInstance)
                .map(ControlMessage.Welcome.class::cast).findFirst().orElseThrow();
        room.onText(denied, ControlCodec.encode(null, listed.onWelcome(welcome)));
        room.tick();
        assertEquals(0, room.playerCount());
        assertNotNull(denied.closedReason);

        room.brokerUnavailable();
        FakeHubConnection unavailable = new FakeHubConnection();
        room.onConnected(unavailable);
        assertNotNull(unavailable.closedReason);
    }

    @Test
    void listedTunnelBindsBrokerSubjectToSignedRoomProof(@TempDir Path dir) throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        RoomHost room = new RoomHost(new RoomHostConfig("Listed", "s2", 0, 0,
                "OPEN", null, 8, FP, List.of()), host, () -> 1_000L,
                TrackValidationProfileSource.none());
        room.brokerPending();
        room.brokerRequired("listed-room", (reference, generation, identity) ->
                CompletableFuture.completedFuture(new ControlMessage.RoomGrantDecision(
                        "listed-room", room.instanceId(), reference, generation, identity, false)));
        FakeHubConnection output = new FakeHubConnection();
        HubConnection tunnel = com.openggf.net.host.HostLinkAuthentication.boundTunnel(output,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(tunnel, new ControlMessage.RelayGuestOpen(1, "listed-room",
                room.instanceId(), guest.fingerprint(), "fresh-route", 2, 1));
        ClientHandshake signed = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                "fresh-route", 2);
        room.onText(tunnel, ControlCodec.encode(null, signed.hello()));
        ControlMessage.Welcome welcome = output.text.stream().map(ControlCodec::decode)
                .map(ControlCodec.DecodedControl::message)
                .filter(ControlMessage.Welcome.class::isInstance)
                .map(ControlMessage.Welcome.class::cast).findFirst().orElseThrow();
        room.onText(tunnel, ControlCodec.encode(null, signed.onWelcome(welcome)));
        assertTrue(output.text.stream().map(ControlCodec::decode)
                .map(ControlCodec.DecodedControl::message)
                .anyMatch(ControlMessage.RoomAccessChallenge.class::isInstance));

        FakeHubConnection imposterOutput = new FakeHubConnection();
        HubConnection imposterTunnel = com.openggf.net.host.HostLinkAuthentication.boundTunnel(
                imposterOutput, () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(imposterTunnel, new ControlMessage.RelayGuestOpen(2, "listed-room",
                room.instanceId(), host.fingerprint(), "route-3", 3, 1));
        ClientHandshake imposter = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                "route-3", 3);
        room.onText(imposterTunnel, ControlCodec.encode(null, imposter.hello()));
        ControlMessage.Welcome imposterWelcome = imposterOutput.text.stream()
                .map(ControlCodec::decode).map(ControlCodec.DecodedControl::message)
                .filter(ControlMessage.Welcome.class::isInstance)
                .map(ControlMessage.Welcome.class::cast).findFirst().orElseThrow();
        room.onText(imposterTunnel, ControlCodec.encode(null, imposter.onWelcome(imposterWelcome)));
        assertNotNull(imposterOutput.closedReason);
        assertEquals(1, room.playerCount());
    }

    @Test
    void hostedRelayPublishesConnectionWithItsBrokerIdentity(@TempDir Path dir) throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        RoomHost room = new RoomHost(new RoomHostConfig("Relay", "s2", 0, 0,
                "OPEN", null, 8, FP, List.of()), host, () -> 1_000L,
                TrackValidationProfileSource.none());
        FakeHubConnection output = new FakeHubConnection();
        HubConnection transport = com.openggf.net.host.HostLinkAuthentication.boundTunnel(output,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnectedRelay(transport, host.fingerprint(), "relay-grant", 4);
        ClientHandshake signed = new ClientHandshake(guest, "Guest", "", FP,
                host.fingerprint(), com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                "relay-grant", 4);
        room.onText(transport, ControlCodec.encode(null, signed.hello()));
        ControlMessage.Welcome welcome = output.text.stream().map(ControlCodec::decode)
                .map(ControlCodec.DecodedControl::message)
                .filter(ControlMessage.Welcome.class::isInstance)
                .map(ControlMessage.Welcome.class::cast).findFirst().orElseThrow();
        room.onText(transport, ControlCodec.encode(null, signed.onWelcome(welcome)));
        assertNotNull(output.closedReason);
        assertTrue(output.text.stream().map(ControlCodec::decode)
                .map(ControlCodec.DecodedControl::message)
                .anyMatch(message -> message instanceof ControlMessage.JoinRejected rejected
                        && rejected.reason().contains("identity mismatch")));
        assertEquals(0, room.playerCount());
    }
}
