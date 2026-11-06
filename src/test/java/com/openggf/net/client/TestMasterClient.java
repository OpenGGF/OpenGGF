package com.openggf.net.client;

import openggf.racing.server.host.RaceHostImpl;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.server.master.MasterServer;
import openggf.racing.server.master.TestMasterServer;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
class TestMasterClient {
    private static final String FP = "0.6:cafe";
    private MasterServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private MasterClient connect(Path identityDir, String name) throws Exception {
        return MasterClient.connect("ws://127.0.0.1:" + server.port() + "/master",
                PlayerIdentity.loadOrCreate(identityDir), name, FP, null)
                .get(15, TimeUnit.SECONDS);
    }

    private void establish(Path identityDir) throws Exception {
        String fingerprint = PlayerIdentity.loadOrCreate(identityDir).fingerprint();
        CountDownLatch done = new CountDownLatch(1);
        server.execute(() -> {
            server.establishForTest(fingerprint);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    private static void requireBroker(RaceRoomHost listener, MasterClient host,
                                      String roomId) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        listener.executeControl(() -> {
            listener.room().brokerPending();
            com.openggf.net.hub.RoomAdmissionBridge.install(listener.room(), roomId,
                    host.inviteService(roomId), listener.room().inviteEpoch(), request ->
                            host.redeemDirectGrant(request.roomId(), request.instanceId(),
                                    request.grantReference(), request.routeGeneration(), request.fingerprint()));
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        ControlMessage.RoomActivated activated = host.activateRoom(roomId,
                listener.room().instanceId()).get(5, TimeUnit.SECONDS);
        assertTrue(activated.active());
    }

    @Test
    void nullCleanupAndStaleBindingCannotDetachReplacement(@TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        RoomHost room = new RoomHost(new RoomHostConfig("Binding", "s3k", 0, 0,
                "OPEN", null, 8, FP), PlayerIdentity.loadOrCreate(dir.resolve("listener")),
                System::currentTimeMillis, TrackValidationProfileSource.none());
        RaceRoomHost listener = new RaceRoomHost() {
            @Override public int port() { return 0; }
            @Override public String tlsCertificateSha256() { return null; }
            @Override public void execute(Runnable task) { task.run(); }
            @Override public RoomHost room() { return room; }
            @Override public void close() { room.close("test closed"); }
        };
        HostMasterLink.MessageSink sink = new HostMasterLink.MessageSink() {
            @Override public void sendControl(ControlMessage message) { }
            @Override public void sendBinary(byte[] data) { }
        };
        HostMasterLink first = HostMasterLink.forServer(listener, sink);
        HostMasterLink replacement = HostMasterLink.forServer(listener, sink);
        host.bindHostLink(first);
        host.bindHostLink(null);
        host.bindHostLink(first);
        host.bindHostLink(replacement);
        host.unbindHostLink(first);
        assertTrue(host.hostLinkIs(replacement));
        host.close();
        listener.close();
    }

    @Test
    void createListJoinRelayRoundTrip(@TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        establish(dir.resolve("host"));

        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Big", "s3k", 0, 0, "OPEN", null, 64, false);
        String roomId = host.createRoom(descriptor, "RELAY", 0, FP)
                .get(10, TimeUnit.SECONDS).roomId();

        MasterClient guest = connect(dir.resolve("guest"), "GUEST");
        ControlMessage.RoomListResult list = guest.listRooms("s3k", 0)
                .get(10, TimeUnit.SECONDS);
        assertEquals(1, list.rooms().size());
        assertEquals("RELAY", list.rooms().getFirst().routing());

        RaceConnection room = guest.joinRoom(roomId,
                        PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                .get(15, TimeUnit.SECONDS);
        assertTrue(room.isOpen());
        assertTrue(room.playerSlot() >= 0);
        room.close();
        host.close();
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void directJoinFallsBackToRelayWhenHostUnreachable(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        establish(dir.resolve("host"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Lan", "s3k", 0, 0, "OPEN", null, 8, false);
        try (RaceRoomHost hostServer = impl.start(0,
                new RoomHostConfig("Lan", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none())) {
            String roomId = host.createRoom(descriptor, "DIRECT", 1, FP,
                    List.of(), "ab".repeat(32), hostServer.room().instanceId(),
                    hostServer.room().accessEpoch(), null)
                    .get(10, TimeUnit.SECONDS).roomId();
            host.bindHostLink(HostMasterLink.forServer(hostServer,
                    new HostMasterLink.MessageSink() {
                        @Override public void sendControl(ControlMessage message) {
                            host.sendControl(message);
                        }
                        @Override public void sendBinary(byte[] data) { host.sendBinary(data); }
                    }));
            requireBroker(hostServer, host, roomId);

            MasterClient guest = connect(dir.resolve("guest"), "GUEST");
            RaceConnection room = guest.joinRoom(roomId,
                            PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                    .get(30, TimeUnit.SECONDS);
            assertTrue(room.isOpen());
            assertEquals(0, room.playerSlot());
            room.close();
            host.close();
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void directTunnelFallbackRejectsRelayedWelcomeFromDifferentHost(RaceHostImpl impl,
                                                                   @TempDir Path dir)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient advertisedHost = connect(dir.resolve("advertised"), "HOST");
        establish(dir.resolve("advertised"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Lan", "s3k", 0, 0, "OPEN", null, 8, false);
        try (RaceRoomHost otherHostServer = impl.start(0,
                new RoomHostConfig("Lan", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("differentHost")),
                TrackValidationProfileSource.none())) {
            String roomId = advertisedHost.createRoom(descriptor, "DIRECT", 1, FP,
                    List.of(), "ab".repeat(32), otherHostServer.room().instanceId(),
                    otherHostServer.room().accessEpoch(), null)
                    .get(10, TimeUnit.SECONDS).roomId();
            advertisedHost.bindHostLink(HostMasterLink.forServer(otherHostServer,
                    new HostMasterLink.MessageSink() {
                        @Override public void sendControl(ControlMessage message) {
                            advertisedHost.sendControl(message);
                        }
                        @Override public void sendBinary(byte[] data) {
                            advertisedHost.sendBinary(data);
                        }
                    }));
            requireBroker(otherHostServer, advertisedHost, roomId);
            MasterClient guest = connect(dir.resolve("guest"), "GUEST");
            Exception failure = assertThrows(Exception.class, () -> guest.joinRoom(roomId,
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                    .get(30, TimeUnit.SECONDS));
            assertTrue(failure.toString().contains("host identity mismatch"), failure.toString());
            guest.close();
            advertisedHost.close();
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void successfulDirectLeaveCanRejoinOnTheSameMasterSession(RaceHostImpl impl,
                                                             @TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guestIdentity = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        try (RaceRoomHost listener = impl.startTls(0,
                     new RoomHostConfig("Rejoin", "s3k", 0, 0, "OPEN", null, 8, FP),
                     hostIdentity, TrackValidationProfileSource.none());
             MasterClient host = connect(dir.resolve("host"), "HOST");
             MasterClient guest = connect(dir.resolve("guest"), "GUEST")) {
            establish(dir.resolve("host"));
            String roomId = host.createRoom(listener.room().descriptor(), "DIRECT", listener.port(),
                    FP, List.of(), listener.tlsCertificateSha256(), listener.room().instanceId(),
                    listener.room().accessEpoch(), null).get(10, TimeUnit.SECONDS).roomId();
            host.bindHostLink(HostMasterLink.forServer(listener, new HostMasterLink.MessageSink() {
                @Override public void sendControl(ControlMessage message) { host.sendControl(message); }
                @Override public void sendBinary(byte[] data) { host.sendBinary(data); }
            }));
            requireBroker(listener, host, roomId);
            RaceConnection first = guest.joinRoom(roomId, guestIdentity, "GUEST", FP)
                    .get(15, TimeUnit.SECONDS);
            assertTrue(first instanceof RaceClient);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            listener.execute(() -> {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CompletableFuture<RaceConnection> rejoining;
            try {
                first.close();
                CompletableFuture<ControlMessage.RoomJoinResult> canceledObserver = guest.requestJoin(roomId);
                assertTrue(canceledObserver.cancel(false));
                rejoining = guest.joinRoom(roomId, guestIdentity, "GUEST", FP);
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> rejoining.get(100, TimeUnit.MILLISECONDS));
                assertTrue(guest.isOpen());
            } finally { release.countDown(); }
            RaceConnection second = rejoining.get(15, TimeUnit.SECONDS);
            try {
                assertTrue(second.isOpen());
                CompletableFuture<Integer> players = new CompletableFuture<>();
                listener.execute(() -> players.complete(listener.room().playerCount()));
                assertEquals(1, players.get(5, TimeUnit.SECONDS));
            } finally {
                second.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void brokerPinnedDirectRoomJoinsOverTls(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        establish(dir.resolve("host"));
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Secure", "s3k", 0, 0, "OPEN", null, 8, false);
        try (RaceRoomHost hostServer = impl.startTls(0,
                new RoomHostConfig("Secure", "s3k", 0, 0, "OPEN", null, 8, FP),
                hostIdentity, TrackValidationProfileSource.none())) {
            String roomId = host.createRoom(descriptor, "DIRECT", hostServer.port(), FP,
                    List.of(), hostServer.tlsCertificateSha256(),
                    hostServer.room().instanceId(), hostServer.room().accessEpoch(), null)
                    .get(10, TimeUnit.SECONDS).roomId();
            requireBroker(hostServer, host, roomId);
            MasterClient guest = connect(dir.resolve("guest"), "GUEST");
            RaceConnection room = guest.joinRoom(roomId,
                            PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                    .get(15, TimeUnit.SECONDS);
            assertTrue(room instanceof RaceClient);
            assertTrue(room.isOpen());
            room.close();
            guest.close();
            host.close();
        }
    }

    @Test
    void brokerPinnedIpv6DirectRoomJoinsOverTls(@TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        try (MasterClient host = MasterClient.connect(
                "ws://[::1]:" + server.port() + "/master",
                hostIdentity, "HOST", FP, null).get(15, TimeUnit.SECONDS)) {
            establish(dir.resolve("host"));
            ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                    "IPv6", "s3k", 0, 0, "OPEN", null, 8, false);
            try (RaceRoomHost hostServer = com.openggf.net.host.jdk.JdkRaceHostServer.startTls(
                    0, new RoomHostConfig("IPv6", "s3k", 0, 0, "OPEN", null, 8, FP),
                    hostIdentity, TrackValidationProfileSource.none());
                 MasterClient guest = connect(dir.resolve("guest"), "GUEST")) {
                String roomId = host.createRoom(descriptor, "DIRECT", hostServer.port(), FP,
                        List.of(), hostServer.tlsCertificateSha256(),
                        hostServer.room().instanceId(), hostServer.room().accessEpoch(), null)
                        .get(10, TimeUnit.SECONDS).roomId();
                requireBroker(hostServer, host, roomId);
                RaceConnection room = guest.joinRoom(roomId,
                                PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                        .get(15, TimeUnit.SECONDS);
                try {
                    assertTrue(room instanceof RaceClient,
                            "a reachable authenticated IPv6 room must use the direct connection");
                    assertTrue(room.isOpen());
                    assertEquals(0, room.playerSlot());
                } finally {
                    room.close();
                }
            }
        }
    }

    @Test
    void relayAttachRejectionFailsHandshakeWithBrokerReason(@TempDir Path dir)
            throws Exception {
        ConcurrentLinkedQueue<RaceClient.InboundEvent> inbound = new ConcurrentLinkedQueue<>();
        inbound.add(new RaceClient.Control(
                new ControlMessage.RoomJoinRejected("room not found")));
        RaceConnection attached = new RaceConnection() {
            @Override public List<RaceClient.InboundEvent> drainInbound() {
                List<RaceClient.InboundEvent> events = List.copyOf(inbound);
                inbound.clear();
                return events;
            }
            @Override public void sendControl(ControlMessage message) { }
            @Override public void sendBinary(byte[] data) { }
            @Override public int playerSlot() { return -1; }
            @Override public String sessionToken() { return null; }
            @Override public ControlMessage.JoinAccepted joinAccepted() { return null; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };

        long started = System.nanoTime();
        java.util.concurrent.ExecutionException failure = assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> MasterClient.completeRoomHandshake(attached,
                                PlayerIdentity.loadOrCreate(dir), "GUEST", FP)
                        .get(10, TimeUnit.SECONDS));

        assertEquals("room not found", failure.getCause().getMessage());
        assertTrue(System.nanoTime() - started
                        < TimeUnit.MILLISECONDS.toNanos(RaceClient.JOIN_TIMEOUT_MILLIS),
                "attach rejection must not wait for the handshake timeout");
    }

    @Test
    void rateLimitedListFailsWithoutStealingLaterListReply(@TempDir Path dir)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient guest = connect(dir.resolve("guest"), "GUEST");
        try {
            assertEquals(0, guest.listRooms(null, 0)
                    .get(5, TimeUnit.SECONDS).page());
            CompletableFuture<ControlMessage.RoomListResult> limited =
                    guest.listRooms(null, 1);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> limited.get(1, TimeUnit.SECONDS));

            Thread.sleep(2_100);
            assertEquals(2, guest.listRooms(null, 2)
                    .get(5, TimeUnit.SECONDS).page());
        } finally {
            guest.close();
        }
    }
}
