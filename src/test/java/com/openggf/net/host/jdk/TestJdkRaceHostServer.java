package com.openggf.net.host.jdk;

import com.openggf.net.host.RawWebSocketClient;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour specific to the JDK room host: HTTP upgrade rejection, handshake and idle
 * deadlines, close codes, graceful room-initiated close, resource caps and release on
 * {@code close()}. Shared transport behaviour is covered against both hosts by
 * {@code TestRaceHostServer}.
 */
@Timeout(30)
class TestJdkRaceHostServer {
    private static final String FP = "0.6:cafe1234";
    private JdkRaceHostServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private JdkRaceHostServer start(Path dir, boolean tls, JdkHostLimits limits) throws Exception {
        server = JdkRaceHostServer.start(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none(), System::currentTimeMillis, tls, limits);
        return server;
    }

    private JdkRaceHostServer start(Path dir) throws Exception {
        return start(dir, false, JdkHostLimits.defaults());
    }

    // ---- HTTP upgrade ----

    @Test
    void upgradeRequestsOutsideTheContractGetAnHttpErrorAndAreClosed(@TempDir Path dir)
            throws Exception {
        start(dir);
        String valid = "Host: x\r\nUpgrade: websocket\r\nConnection: keep-alive, Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n";
        assertRejected(404, "GET /other HTTP/1.1\r\n" + valid + "\r\n");
        assertRejected(404, "GET /race?x=1 HTTP/1.1\r\n" + valid + "\r\n");
        assertRejected(405, "POST /race HTTP/1.1\r\n" + valid + "\r\n");
        assertRejected(400, "GET /race HTTP/1.0\r\n" + valid + "\r\n");
        assertRejected(400, "GET /race HTTP/1.1\r\n" + valid + "Content-Length: 4\r\n\r\nbody");
        assertRejected(400, "GET /race HTTP/1.1\r\n" + valid.replace("dGhlIHNhbXBsZSBub25jZQ==",
                "c2hvcnQ=") + "\r\n");
        assertRejected(400, "GET /race HTTP/1.1\r\n" + valid.replace("Upgrade: websocket\r\n", "")
                + "\r\n");
        assertRejected(414, "GET /" + "r".repeat(5000) + " HTTP/1.1\r\n" + valid + "\r\n");
        assertRejected(431, "GET /race HTTP/1.1\r\n" + valid
                + "X-Padding: " + "p".repeat(9000) + "\r\n\r\n");
        // Positive control: the same headers on the right request line upgrade.
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertEquals(101, client.sendRequest("GET /race HTTP/1.1\r\n" + valid + "\r\n"));
        }
    }

    private void assertRejected(int status, String request) throws IOException {
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertEquals(status, client.sendRequest(request), request.lines().findFirst().orElse(""));
            assertEquals(-1, client.awaitServerClose());
        }
    }

    @Test
    void framesSentBeforeReadingTheUpgradeResponseAreNotLost(@TempDir Path dir)
            throws Exception {
        start(dir);
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            // Pipeline a ping behind the request: the handshake must not over-read it.
            byte[] pingFrame = {(byte) 0x89, (byte) 0x81, 1, 2, 3, 4, (byte) (9 ^ 1)};
            byte[] request = ("GET /race HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            byte[] both = new byte[request.length + pingFrame.length];
            System.arraycopy(request, 0, both, 0, request.length);
            System.arraycopy(pingFrame, 0, both, request.length, pingFrame.length);
            client.sendRaw(both);
            assertEquals(101, client.readResponseStatus());
            RawWebSocketClient.Frame pong = client.readFrame();
            assertEquals(RawWebSocketClient.OP_PONG, pong.opcode());
            assertArrayEquals(new byte[] {9}, pong.payload());
        }
    }

    // ---- deadlines ----

    @Test
    void silentSocketIsDroppedAtTheHandshakeDeadline(@TempDir Path dir) throws Exception {
        start(dir, false, JdkHostLimits.defaults().withHandshakeTimeoutMillis(300));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            long started = System.nanoTime();
            assertEquals(-1, client.awaitServerClose());
            assertTrue(elapsedMillis(started) < 5_000);
        }
        awaitTrue(() -> server.openConnections() == 0, "slot released after the deadline");
    }

    @Test
    void tricklingUpgradeCannotOutliveTheHandshakeDeadline(@TempDir Path dir) throws Exception {
        start(dir, false, JdkHostLimits.defaults().withHandshakeTimeoutMillis(500));
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream output = socket.getOutputStream();
            long started = System.nanoTime();
            byte[] request = "GET /race HTTP/1.1\r\nX-Slow: ".getBytes(StandardCharsets.US_ASCII);
            boolean dropped = false;
            for (int i = 0; i < 100 && !dropped; i++) {
                try {
                    output.write(request[Math.min(i, request.length - 1)]);
                    output.flush();
                    Thread.sleep(100);
                } catch (IOException closed) {
                    dropped = true;
                }
                dropped |= server.openConnections() == 0;
            }
            assertTrue(dropped, "a byte every 100 ms must not keep the upgrade alive");
            assertTrue(elapsedMillis(started) < 5_000);
        }
    }

    @Test
    void stalledTlsHandshakeIsDroppedAtTheDeadline(@TempDir Path dir) throws Exception {
        start(dir, true, JdkHostLimits.defaults().withHandshakeTimeoutMillis(300));
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5_000);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void idleAdmittedPeerIsDroppedAndLeavesTheRoom(@TempDir Path dir) throws Exception {
        start(dir, false, JdkHostLimits.defaults().withIdleTimeoutMillis(400));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertInstanceOf(ControlMessage.JoinAccepted.class, client.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Idle", FP));
            assertEquals(1, onRoomThread(() -> server.room().playerCount()));
            client.awaitServerClose();
        }
        awaitTrue(() -> onRoomThreadQuietly(() -> server.room().playerCount()) == 0,
                "idle peer removed from the room");
    }

    // ---- framing ----

    @Test
    void violationsCloseWithTheMatchingStatusCode(@TempDir Path dir) throws Exception {
        start(dir);
        assertCloseCode(1002, client -> client.sendFrame(0xC0 | RawWebSocketClient.OP_TEXT, true,
                "{}".getBytes(StandardCharsets.UTF_8), -1)); // RSV1 without an extension
        assertCloseCode(1002, client -> client.sendFrame(0x80 | RawWebSocketClient.OP_TEXT, false,
                "{}".getBytes(StandardCharsets.UTF_8), -1));
        assertCloseCode(1007, client -> client.sendFrame(true, RawWebSocketClient.OP_TEXT,
                new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80})); // UTF-16 surrogate
        assertCloseCode(1009, client -> client.sendOversizedHeader(
                0x80 | RawWebSocketClient.OP_TEXT, 1L << 20));
        assertCloseCode(1009, client -> client.sendFrame(true, RawWebSocketClient.OP_BINARY,
                new byte[4097]));
    }

    private interface Sender {
        void send(RawWebSocketClient client) throws IOException;
    }

    private void assertCloseCode(int expected, Sender sender) throws IOException {
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertEquals(101, client.upgrade());
            sender.send(client);
            assertEquals(expected, client.awaitServerClose());
        }
    }

    @Test
    void pingIsAnsweredAndAPeerCloseIsEchoed(@TempDir Path dir) throws Exception {
        start(dir);
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertEquals(101, client.upgrade());
            byte[] ping = "are you there".getBytes(StandardCharsets.UTF_8);
            client.sendFrame(true, RawWebSocketClient.OP_PING, ping);
            RawWebSocketClient.Frame pong = client.readFrame();
            assertEquals(RawWebSocketClient.OP_PONG, pong.opcode());
            assertArrayEquals(ping, pong.payload());
            client.sendFrame(true, RawWebSocketClient.OP_CLOSE,
                    new byte[] {0x03, (byte) 0xE9, 'b', 'y', 'e'}); // 1001 "bye"
            RawWebSocketClient.Frame echo = client.readFrame();
            assertEquals(RawWebSocketClient.OP_CLOSE, echo.opcode());
            assertEquals(1001, echo.closeCode());
            assertTrue(client.readFrame().endOfStream());
        }
    }

    @Test
    void roomRejectionFlushesTheReasonBeforeTheCloseFrame(@TempDir Path dir) throws Exception {
        start(dir);
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            ControlMessage result = client.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Wrong", "0.6:deadbeef");
            ControlMessage.JoinRejected rejected =
                    assertInstanceOf(ControlMessage.JoinRejected.class, result);
            RawWebSocketClient.Frame close = client.readFrame();
            assertEquals(RawWebSocketClient.OP_CLOSE, close.opcode());
            assertEquals(1000, close.closeCode());
            assertTrue(close.text().contains(rejected.reason()), close.text());
            // Answer the close like a conforming client; the server then ends the stream.
            client.sendFrame(true, RawWebSocketClient.OP_CLOSE, new byte[] {0x03, (byte) 0xE8});
            assertTrue(client.readFrame().endOfStream());
        }
        awaitTrue(() -> server.openConnections() == 0, "rejected peer released");
    }

    // ---- resource caps ----

    @Test
    void totalSocketCapRefusesExtraConnections(@TempDir Path dir) throws Exception {
        start(dir, false, JdkHostLimits.defaults().withMaxConnections(10, 2));
        try (RawWebSocketClient first = RawWebSocketClient.connect(server.port());
             RawWebSocketClient second = RawWebSocketClient.connect(server.port())) {
            assertEquals(101, first.upgrade());
            assertEquals(101, second.upgrade());
            try (RawWebSocketClient third = RawWebSocketClient.connect(server.port())) {
                assertEquals(-1, third.awaitServerClose());
            }
        }
    }

    @Test
    void hubBackpressureLadderSeesTheJdkOutboundQueue(@TempDir Path dir) throws Exception {
        // Transport cap far above anything sent: only GhostHub's 1 MiB slow-consumer
        // ladder, reading queuedBytes(), can drop the stalled peer here.
        start(dir, false, JdkHostLimits.defaults().withMaxQueuedOutboundBytes(512 * 1024 * 1024));
        String bulk = "k".repeat(60_000);
        try (RawWebSocketClient reading = RawWebSocketClient.connect(server.port());
             RawWebSocketClient stalled = RawWebSocketClient.connect(server.port())) {
            ControlMessage.JoinAccepted readingSlot = assertInstanceOf(
                    ControlMessage.JoinAccepted.class, reading.join(
                            PlayerIdentity.loadOrCreate(dir.resolve("reader")), "Reader", FP));
            ControlMessage.JoinAccepted stalledSlot = assertInstanceOf(
                    ControlMessage.JoinAccepted.class, stalled.join(
                            PlayerIdentity.loadOrCreate(dir.resolve("stalled")), "Stalled", FP));
            AtomicInteger textFrames = new AtomicInteger();
            Thread drain = Thread.ofVirtual().start(() -> {
                try {
                    RawWebSocketClient.Frame frame;
                    while (!(frame = reading.readFrame()).endOfStream()) {
                        if (frame.opcode() == RawWebSocketClient.OP_TEXT) {
                            textFrames.incrementAndGet();
                        }
                    }
                } catch (IOException ignored) {
                    // Test teardown.
                }
            });
            // Up to 64 MiB to each peer, paced by the healthy reader so only the peer that
            // never reads builds a queue: far beyond the kernel buffers in front of it.
            for (int i = 1; i <= 1_100 && server.openConnections() == 2; i++) {
                server.execute(() -> {
                    server.room().sendToSlot(readingSlot.playerSlot(), new ControlMessage.Kick(bulk));
                    server.room().sendToSlot(stalledSlot.playerSlot(), new ControlMessage.Kick(bulk));
                });
                int sent = i;
                awaitTrue(() -> textFrames.get() >= sent - 1, "the reading peer keeps up");
            }
            awaitTrue(() -> onRoomThreadQuietly(() -> server.room().playerCount()) == 1,
                    "the stalled peer is dropped");
            assertEquals(1, server.openConnections(), "the reading peer stays connected");
            drain.interrupt();
        }
    }

    @Test
    void outboundQueueCapAbortsAPeerThatStopsReading(@TempDir Path dir) throws Exception {
        JdkHostLimits limits = JdkHostLimits.defaults().withMaxQueuedOutboundBytes(100_000);
        start(dir, false, limits);
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            // Never started: no writer drains, so every frame stays queued.
            WebSocketConnection connection = new WebSocketConnection(server, accepted,
                    "127.0.0.1", 1_000, limits);
            String chunk = "x".repeat(30_000);
            for (int i = 0; i < 3; i++) {
                connection.sendText(chunk);
            }
            assertFalse(accepted.isClosed(), "90 KB is within the 100 KB cap");
            assertTrue(connection.queuedBytes() >= 90_000);
            connection.sendText(chunk);
            assertTrue(accepted.isClosed(), "the frame that crosses the cap aborts the peer");
            assertTrue(connection.queuedBytes() <= limits.maxQueuedOutboundBytes());
            connection.sendText(chunk);
            assertTrue(connection.queuedBytes() <= limits.maxQueuedOutboundBytes(),
                    "an aborted peer queues nothing more");
        }
    }

    // ---- lifecycle ----

    @Test
    void closeReleasesThePortEveryThreadAndEveryPeer(@TempDir Path dir) throws Exception {
        start(dir, true, JdkHostLimits.defaults());
        int port = server.port();
        List<AutoCloseable> peers = new ArrayList<>();
        try {
            RawWebSocketClient joined = RawWebSocketClient.connectTls(port);
            peers.add(joined);
            assertInstanceOf(ControlMessage.JoinAccepted.class, joined.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", FP));
            RawWebSocketClient upgradedOnly = RawWebSocketClient.connectTls(port);
            peers.add(upgradedOnly);
            assertEquals(101, upgradedOnly.upgrade());
            Socket midTlsHandshake = new Socket("127.0.0.1", port);
            peers.add(midTlsHandshake);
            awaitTrue(() -> server.openConnections() == 3, "three peers accepted");
            assertTrue(server.liveIoThreads() >= 6, "acceptor plus reader/writer threads");

            server.close();

            assertTrue(server.released(), "port, I/O threads and room thread released");
            assertEquals(0, server.liveIoThreads());
            assertEquals(0, server.openConnections());
            assertEquals(0, server.room().playerCount(),
                    "registered peers saw onDisconnected before the room thread stopped");
            assertEquals(-1, joined.awaitServerClose());
            assertThrows(IllegalStateException.class, () -> server.execute(() -> { }));
            try (ServerSocket rebound = new ServerSocket(port)) {
                assertEquals(port, rebound.getLocalPort());
            }
        } finally {
            for (AutoCloseable peer : peers) {
                peer.close();
            }
        }
    }

    @Test
    void closeFromTheRoomThreadDoesNotDeadlock(@TempDir Path dir) throws Exception {
        start(dir);
        CompletableFuture<Void> closed = new CompletableFuture<>();
        server.execute(() -> {
            server.close();
            closed.complete(null);
        });
        closed.get(10, TimeUnit.SECONDS);
        awaitTrue(server::released, "room thread terminates after the closing task returns");
    }

    @Test
    void bindFailureIsReportedAsUncheckedIo(@TempDir Path dir) throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            assertThrows(UncheckedIOException.class, () -> JdkRaceHostServer.start(
                    occupied.getLocalPort(),
                    new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                    PlayerIdentity.loadOrCreate(dir.resolve("host")),
                    TrackValidationProfileSource.none()));
        }
    }

    // ---- helpers ----

    private <T> T onRoomThread(Supplier<T> query) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> result.complete(query.get()));
        return result.get(5, TimeUnit.SECONDS);
    }

    private <T> T onRoomThreadQuietly(Supplier<T> query) {
        try {
            return onRoomThread(query);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void awaitTrue(BooleanSupplier condition, String what)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting: " + what);
            }
            Thread.sleep(20);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
