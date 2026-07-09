package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.host.RaceHostServer;
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
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        String roomId = host.createRoom(descriptor, "DIRECT", 1, FP)
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
}
