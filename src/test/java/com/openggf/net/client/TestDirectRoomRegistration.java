package com.openggf.net.client;

import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import openggf.racing.server.master.MasterServer;
import openggf.racing.server.master.TestMasterServer;
import openggf.racing.server.host.RaceHostImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class TestDirectRoomRegistration {
    private static final String BUILD = "registration:test";

    @Test
    void directRegistrationIsOneOwnedOperationRatherThanPublicAuthorityStages() {
        assertTrue(AutoCloseable.class.isAssignableFrom(DirectRoomRegistration.class),
                "the advertised room must have one complete registration lifetime");
        assertFalse(com.openggf.net.hub.RoomAdmissionBridge.class.isAnnotationPresent(
                com.openggf.game.ModApi.class));
        Set<String> internalStages = Set.of("activateRoom", "redeemDirectGrant",
                "bindHostLink", "unbindHostLink");
        for (var method : MasterClient.class.getDeclaredMethods()) {
            if (internalStages.contains(method.getName())
                    || method.getName().equals("createRoom") && method.getParameterCount() >= 6) {
                assertFalse(Modifier.isPublic(method.getModifiers()), method.toString());
            }
        }
        for (var method : RoomHost.class.getDeclaredMethods()) {
            if (method.getName().equals("brokerRequired")
                    || method.getName().equals("onConnected") && method.getParameterCount() > 1
                    || method.getName().equals("onConnectedRelay")) {
                assertFalse(Modifier.isPublic(method.getModifiers()), method.toString());
            }
        }
    }

    @Test
    void completedRegistrationIsJoinableAndRepeatedCloseRetiresOnlyItsListing(@TempDir Path dir)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             JdkRaceHostServer listener = listener(identity);
             MasterClient host = connect(broker, identity, "HOST")) {
            establish(broker, identity);
            DirectRoomRegistration registration = DirectRoomRegistration.register(host, listener, BUILD, List.of())
                    .get(10, TimeUnit.SECONDS);
            assertEquals(RoomHost.ListenerAdmission.BROKER_REQUIRED, admission(listener));
            assertEquals(registration.created().roomId(), host.listRooms(null, 0)
                    .get(10, TimeUnit.SECONDS).rooms().getFirst().roomId());
            registration.close();
            registration.close();
            assertEquals(RoomHost.ListenerAdmission.BROKER_UNAVAILABLE, admission(listener));
            assertThrows(ExecutionException.class, () -> host.requestJoin(registration.created().roomId())
                    .get(10, TimeUnit.SECONDS));
            assertTrue(host.isOpen());
        }
    }

    @Test
    void ownerJoinUsesTheLocalListenerWithTheBrokerGrantWhenTheAdvertisedRouteIsRemote(
            @TempDir Path dir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             JdkRaceHostServer listener = listener(identity);
             MasterClient host = connect(broker, identity, "HOST")) {
            establish(broker, identity);
            try (DirectRoomRegistration registration = DirectRoomRegistration.register(
                    host, listener, BUILD, List.of()).get(10, TimeUnit.SECONDS)) {
                ControlMessage.RoomJoinResult prepared = host.requestJoin(registration.created().roomId())
                        .get(10, TimeUnit.SECONDS);
                ControlMessage.RoomJoinResult remote = new ControlMessage.RoomJoinResult(
                        prepared.roomId(), prepared.routing(), "203.0.113.71", 1,
                        prepared.hostServerId(), prepared.determinismFingerprint(),
                        prepared.certificateSha256(), prepared.instanceId(), prepared.accessPolicy(),
                        prepared.accessEpoch(), prepared.inviteEpoch(), prepared.inviteExpiresAtMillis(),
                        prepared.grantReference(), prepared.routeGeneration());
                RaceConnection joined;
                try (RoomJoinOptions options = new RoomJoinOptions(null, remote.instanceId(),
                        remote.accessEpoch())) {
                    joined = registration.joinOwner(remote, identity, "HOST", "", BUILD, options)
                            .get(10, TimeUnit.SECONDS);
                }
                try {
                    assertInstanceOf(RaceClient.class, joined);
                    assertTrue(joined.isOpen());
                    assertEquals(0, joined.playerSlot());
                    assertTrue(host.isOpen(), "owner admission must leave the master link active");
                } finally { joined.close(); }
            }
        }
    }

    @Test
    void staleRegistrationCloseLeavesReplacementBridgeAndFallbackRouteUsable(@TempDir Path dir)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guestIdentity = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        try (MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             JdkRaceHostServer firstListener = listener(identity);
             JdkRaceHostServer replacementListener = listener(identity);
             MasterClient host = connect(broker, identity, "HOST");
             MasterClient guest = connect(broker, guestIdentity, "GUEST")) {
            establish(broker, identity);
            DirectRoomRegistration first = DirectRoomRegistration.register(host, firstListener, BUILD, List.of())
                    .get(10, TimeUnit.SECONDS);
            try (DirectRoomRegistration replacement = DirectRoomRegistration.register(host,
                    replacementListener, 1, BUILD, List.of()).get(10, TimeUnit.SECONDS)) {
                first.close();
                first.close();
                assertEquals(List.of(replacement.created().roomId()), host.listRooms(null, 0)
                        .get(10, TimeUnit.SECONDS).rooms().stream().map(room -> room.roomId()).toList());
                assertEquals(RoomHost.ListenerAdmission.BROKER_UNAVAILABLE, admission(firstListener));
                assertEquals(RoomHost.ListenerAdmission.BROKER_REQUIRED, admission(replacementListener));
                RaceConnection joined = guest.joinRoom(replacement.created().roomId(), guestIdentity,
                        "GUEST", BUILD).get(30, TimeUnit.SECONDS);
                try {
                    assertTrue(joined.isOpen());
                    assertFalse(joined instanceof RaceClient);
                } finally { joined.close(); }
            } finally { first.close(); }
        }
    }

    @Test
    void manualListenerCannotBecomeListedThroughRegistration(@TempDir Path dir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             JdkRaceHostServer listener = JdkRaceHostServer.startTls(0, config(), identity,
                     TrackValidationProfileSource.none());
             MasterClient host = connect(broker, identity, "HOST")) {
            establish(broker, identity);
            assertThrows(ExecutionException.class, () -> DirectRoomRegistration.register(host, listener, BUILD,
                    List.of()).get(10, TimeUnit.SECONDS));
            assertTrue(host.listRooms(null, 0).get(10, TimeUnit.SECONDS).rooms().isEmpty());
            assertEquals(RoomHost.ListenerAdmission.MANUAL, admission(listener));
        }
    }

    @Test
    void closedListenerDoesNotPreventRegistrationCleanup(@TempDir Path dir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             RaceRoomHost listener = RaceHostImpl.NETTY.startTls(0, config(), identity,
                     TrackValidationProfileSource.none());
             MasterClient host = connect(broker, identity, "HOST")) {
            establish(broker, identity);
            CompletableFuture<Void> pending = new CompletableFuture<>();
            listener.execute(() -> {
                listener.room().brokerPending();
                pending.complete(null);
            });
            pending.get(5, TimeUnit.SECONDS);
            DirectRoomRegistration registration = DirectRoomRegistration.register(host, listener, BUILD, List.of())
                    .get(10, TimeUnit.SECONDS);
            listener.close();
            assertDoesNotThrow(registration::close);
            assertDoesNotThrow(registration::close);
            assertThrows(ExecutionException.class, () -> host.requestJoin(registration.created().roomId())
                    .get(10, TimeUnit.SECONDS));
            assertTrue(host.isOpen());
        }
    }

    @Test
    void successfulDirectLeaveRetiresWithoutRenewingThePreviousAccessPolicy(@TempDir Path dir)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (RoomPassword original = RoomPassword.of("an original direct room password");
             MasterServer broker = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
             JdkRaceHostServer listener = JdkRaceHostServer.startTlsBrokerPending(0,
                     new RoomHostConfig("Owned", "s3k", 0, 0, "OPEN", null, 8, BUILD, List.of(), false,
                             new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true), original, null),
                     identity, TrackValidationProfileSource.none());
             MasterClient host = connect(broker, identity, "HOST")) {
            establish(broker, identity);
            try (DirectRoomRegistration registration = DirectRoomRegistration.register(host, listener, BUILD,
                    List.of()).get(10, TimeUnit.SECONDS)) {
                ControlMessage.RoomJoinResult prepared = host.requestJoin(registration.created().roomId())
                        .get(10, TimeUnit.SECONDS);
                RaceConnection joined;
                try (RoomJoinOptions options = new RoomJoinOptions(original, prepared.instanceId(),
                        prepared.accessEpoch())) {
                    joined = host.joinResolved(prepared, identity, "HOST", "", BUILD, options)
                            .get(10, TimeUnit.SECONDS);
                }
                try {
                    try (RoomPassword password = RoomPassword.of("a replacement direct room password")) {
                        joined.sendControl(new ControlMessage.RoomPasswordChange(password));
                    }
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    boolean changed = false;
                    while (!changed && System.nanoTime() < deadline) {
                        changed = joined.drainInbound().stream().anyMatch(event ->
                                event instanceof RaceClient.Control control
                                && control.message() instanceof ControlMessage.RoomPasswordChanged);
                        if (!changed) Thread.sleep(10);
                    }
                    assertTrue(changed, "the room and broker must commit the replacement access policy");
                } finally { joined.close(); }
                ControlMessage.RoomJoinResult fresh = host.requestJoin(registration.created().roomId())
                        .get(10, TimeUnit.SECONDS);
                assertTrue(fresh.accessPolicy().passwordRequired());
                assertEquals(registration.created().accessEpoch() + 1, fresh.accessEpoch());
            }
        }
    }

    private static JdkRaceHostServer listener(PlayerIdentity identity) throws Exception {
        return JdkRaceHostServer.startTlsBrokerPending(0, config(), identity, TrackValidationProfileSource.none());
    }

    private static RoomHostConfig config() {
        return new RoomHostConfig("Owned", "s3k", 0, 0, "OPEN", null, 8, BUILD);
    }

    private static MasterClient connect(MasterServer broker, PlayerIdentity identity, String name) throws Exception {
        return MasterClient.connect(URI.create("ws://127.0.0.1:" + broker.port() + "/master"),
                identity, name, BUILD, null).get(15, TimeUnit.SECONDS);
    }

    private static void establish(MasterServer broker, PlayerIdentity identity) throws Exception {
        CompletableFuture<Void> established = new CompletableFuture<>();
        broker.execute(() -> {
            broker.establishForTest(identity.fingerprint());
            established.complete(null);
        });
        established.get(5, TimeUnit.SECONDS);
    }

    private static RoomHost.ListenerAdmission admission(RaceRoomHost listener) throws Exception {
        CompletableFuture<RoomHost.ListenerAdmission> admission = new CompletableFuture<>();
        listener.execute(() -> admission.complete(listener.room().listenerAdmission()));
        return admission.get(5, TimeUnit.SECONDS);
    }
}
