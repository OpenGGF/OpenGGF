package com.openggf.net.client;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.net.host.RaceHostServer;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class TestRaceClientLoopback {
    private static final String FP = "0.6:cafe1234";
    private RaceHostServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private RaceHostServer startServer(Path dir, String policy) throws Exception {
        return RaceHostServer.start(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, policy, null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none());
    }

    private RaceClient connect(Path idDir, String name) throws Exception {
        return RaceClient.connect(URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                PlayerIdentity.loadOrCreate(idDir), name, FP).get(10, TimeUnit.SECONDS);
    }

    private static RaceClient.InboundEvent await(
            RaceClient client, Predicate<RaceClient.InboundEvent> match) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (RaceClient.InboundEvent event : client.drainInbound()) {
                if (match.test(event)) {
                    return event;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out");
    }

    @Test
    void connectsChatsAndStreamsGhostFrames(@TempDir Path dir) throws Exception {
        server = startServer(dir, "OPEN");
        RaceClient a = connect(dir.resolve("a"), "A");
        RaceClient b = connect(dir.resolve("b"), "B");
        assertEquals(0, a.playerSlot());
        assertEquals(1, b.playerSlot());
        assertTrue(a.isOpen());

        a.sendControl(new ControlMessage.Chat("hello lan"));
        RaceClient.InboundEvent chat = await(b, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.ChatBroadcast);
        assertEquals("hello lan",
                ((ControlMessage.ChatBroadcast) ((RaceClient.Control) chat).message()).text());

        byte[] frame = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1,
                false, false, false, 2, false), frame, 0);
        a.sendBinary(GhostPackets.encodeFrames(1, 0, frame));
        RaceClient.InboundEvent aggregate = await(b, e -> e instanceof RaceClient.GhostData);
        GhostPackets.Aggregate decoded = ((RaceClient.GhostData) aggregate).aggregate();
        assertEquals(0, decoded.entries().get(0).playerSlot());

        a.close();
        await(b, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.RoomState state
                && state.players().size() == 1);
        b.close();
    }

    @Test
    void fingerprintMismatchSurfacesJoinRejected(@TempDir Path dir) throws Exception {
        server = startServer(dir, "OPEN");
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> RaceClient.connect(
                                URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                                PlayerIdentity.loadOrCreate(dir.resolve("c")),
                                "C", "0.6:deadbeef")
                        .get(10, TimeUnit.SECONDS));
        assertInstanceOf(RaceClient.JoinRejectedException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("fingerprint"));
    }

    @Test
    void connectToDeadPortFailsCleanlyWithinTimeout(@TempDir Path dir) throws Exception {
        long start = System.currentTimeMillis();
        assertThrows(ExecutionException.class,
                () -> RaceClient.connect(URI.create("ws://127.0.0.1:1/race"),
                                PlayerIdentity.loadOrCreate(dir.resolve("d")), "D", FP)
                        .get(15, TimeUnit.SECONDS));
        assertTrue(System.currentTimeMillis() - start < 15_000);
    }

    @Test
    void serverThatAcceptsButNeverAnswersFailsWithinJoinTimeout(@TempDir Path dir) throws Exception {
        try (java.net.ServerSocket silent = new java.net.ServerSocket(0)) {
            long start = System.currentTimeMillis();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> RaceClient.connect(
                                    URI.create("ws://127.0.0.1:" + silent.getLocalPort() + "/race"),
                                    PlayerIdentity.loadOrCreate(dir.resolve("e")), "E", FP)
                            .get(15, TimeUnit.SECONDS));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 10_000, "failed in " + elapsed + "ms — join timeout did not fire");
            assertNotNull(failure.getCause());
        }
    }
}
