package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.host.RaceHostServer;
import com.openggf.net.host.DirectRoomTls;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.master.MasterServer;
import com.openggf.net.master.TestMasterServer;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.lang.reflect.Method;
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
        return MasterClient.connect(URI.create("ws://127.0.0.1:" + server.port() + "/master"),
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

    @Test
    void directJoinFallsBackToRelayWhenHostUnreachable(@TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        establish(dir.resolve("host"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Lan", "s3k", 0, 0, "OPEN", null, 8, false);
        String roomId = host.createRoom(descriptor, "DIRECT", 1, FP,
                List.of(), "ab".repeat(32))
                .get(10, TimeUnit.SECONDS).roomId();

        try (RaceHostServer hostServer = RaceHostServer.start(0,
                new RoomHostConfig("Lan", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none())) {
            host.bindHostLink(HostMasterLink.forServer(hostServer,
                    new HostMasterLink.MessageSink() {
                        @Override public void sendControl(ControlMessage message) {
                            host.sendControl(message);
                        }
                        @Override public void sendBinary(byte[] data) { host.sendBinary(data); }
                    }));

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

    @Test
    void directTunnelFallbackRejectsRelayedWelcomeFromDifferentHost(@TempDir Path dir)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient advertisedHost = connect(dir.resolve("advertised"), "HOST");
        establish(dir.resolve("advertised"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Lan", "s3k", 0, 0, "OPEN", null, 8, false);
        String roomId = advertisedHost.createRoom(descriptor, "DIRECT", 1, FP,
                List.of(), "ab".repeat(32)).get(10, TimeUnit.SECONDS).roomId();

        try (RaceHostServer otherHostServer = RaceHostServer.start(0,
                new RoomHostConfig("Lan", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("differentHost")),
                TrackValidationProfileSource.none())) {
            advertisedHost.bindHostLink(HostMasterLink.forServer(otherHostServer,
                    new HostMasterLink.MessageSink() {
                        @Override public void sendControl(ControlMessage message) {
                            advertisedHost.sendControl(message);
                        }
                        @Override public void sendBinary(byte[] data) {
                            advertisedHost.sendBinary(data);
                        }
                    }));
            MasterClient guest = connect(dir.resolve("guest"), "GUEST");
            Exception failure = assertThrows(Exception.class, () -> guest.joinRoom(roomId,
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", FP)
                    .get(30, TimeUnit.SECONDS));
            assertTrue(failure.toString().contains("host identity mismatch"), failure.toString());
            guest.close();
            advertisedHost.close();
        }
    }

    @Test
    void brokerPinnedDirectRoomJoinsOverTls(@TempDir Path dir) throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), dir);
        MasterClient host = connect(dir.resolve("host"), "HOST");
        establish(dir.resolve("host"));
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Secure", "s3k", 0, 0, "OPEN", null, 8, false);
        try (RaceHostServer hostServer = DirectRoomTls.start(0,
                new RoomHostConfig("Secure", "s3k", 0, 0, "OPEN", null, 8, FP),
                hostIdentity, TrackValidationProfileSource.none())) {
            String roomId = host.createRoom(descriptor, "DIRECT", hostServer.port(), FP,
                    List.of(), DirectRoomTls.certificateSha256(hostServer))
                    .get(10, TimeUnit.SECONDS).roomId();
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
    void lateJoinReplyCannotCompleteLaterRequest() throws Exception {
        ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomJoinResult>> pending =
                new ConcurrentLinkedQueue<>();
        CompletableFuture<ControlMessage.RoomJoinResult> joinA = new CompletableFuture<>();
        CompletableFuture<ControlMessage.RoomJoinResult> joinB = new CompletableFuture<>();
        pending.add(joinA);
        pending.add(joinB);
        joinA.completeExceptionally(new java.util.concurrent.TimeoutException());

        Method completeNext = MasterClient.class.getDeclaredMethod("completeNext",
                ConcurrentLinkedQueue.class, Object.class, Throwable.class);
        completeNext.setAccessible(true);
        ControlMessage.RoomJoinResult replyA = new ControlMessage.RoomJoinResult(
                "room-a", "DIRECT", "a.example", 1234, "host-a", FP, "ab".repeat(32));
        ControlMessage.RoomJoinResult replyB = new ControlMessage.RoomJoinResult(
                "room-b", "DIRECT", "b.example", 5678, "host-b", FP, "cd".repeat(32));

        completeNext.invoke(null, pending, replyA, null);
        assertTrue(!joinB.isDone(), "A's late result must not bind join B");
        completeNext.invoke(null, pending, replyB, null);
        assertEquals("room-b", joinB.get(1, TimeUnit.SECONDS).roomId());
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
