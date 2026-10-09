package openggf.racing.server.host;

import openggf.racing.host.RaceRoomHost;
import openggf.racing.host.ConnectionHygiene;
import openggf.racing.host.ControlledRaceHost;
import openggf.racing.host.RawWebSocketClient;

import openggf.racing.client.ClientHandshake;
import openggf.racing.client.DirectJoinAddress;
import openggf.racing.client.RaceClient;
import openggf.racing.hub.RoomHostConfig;
import openggf.racing.hub.TrackValidationProfileSource;
import openggf.racing.identity.PlayerIdentity;
import openggf.racing.protocol.ControlCodec;
import openggf.racing.protocol.ControlMessage;
import openggf.racing.protocol.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Host transport contract, run against both the Netty and the JDK implementation. */
@Timeout(30)
class TestRaceHostServer {
    private static final String FP = "0.6:cafe1234";
    private RaceRoomHost server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private static final class Probe implements WebSocket.Listener {
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        final StringBuilder partial = new StringBuilder();
        volatile boolean closed;

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                texts.add(partial.toString());
                partial.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed = true;
        }
    }

    private WebSocket connect(Probe probe) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/race"), probe)
                .get(10, TimeUnit.SECONDS);
    }

    private static ControlMessage awaitMessage(Probe probe, Class<?> type) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String text = probe.texts.poll(250, TimeUnit.MILLISECONDS);
            if (text != null) {
                ControlMessage message = ControlCodec.decode(text).message();
                if (type.isInstance(message)) {
                    return message;
                }
            }
        }
        throw new AssertionError("timed out waiting for " + type.getSimpleName());
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void fullHandshakeOverRealSocketAdmits(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        server = start(impl, host);
        assertTrue(server.port() > 0);
        assertNull(server.tlsCertificateSha256(), "plaintext rooms advertise no pin");
        PlayerIdentity client = PlayerIdentity.loadOrCreate(dir.resolve("client"));
        ClientHandshake handshake = new ClientHandshake(client, "Probe", FP);
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome)
                awaitMessage(probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        ControlMessage.JoinAccepted accepted = (ControlMessage.JoinAccepted)
                awaitMessage(probe, ControlMessage.JoinAccepted.class);
        assertEquals(0, accepted.playerSlot());
        assertFalse(accepted.room().verified());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void brokerPinnedTlsDirectRoomAdmitsOnlyItsOwnCertificate(RaceHostImpl impl,
                                                              @TempDir Path dir)
            throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        server = impl.startTls(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                host, TrackValidationProfileSource.none());
        assertTrue(server.tlsCertificateSha256().matches("[0-9a-f]{64}"));
        String invite = "127.0.0.1:" + server.port() + "#"
                + DirectJoinAddress.shareCode(server.tlsCertificateSha256(), host.fingerprint());
        DirectJoinAddress parsed = DirectJoinAddress.parse(invite, 27888);

        RaceClient joined = RaceClient.connect(parsed.uri(), guest, "Guest", FP,
                parsed.certificateSha256(), parsed.hostFingerprint())
                .get(10, TimeUnit.SECONDS);
        assertEquals(0, joined.playerSlot());
        joined.close();

        assertThrows(Exception.class, () -> RaceClient.connect(parsed.uri(), guest, "Guest", FP,
                "00".repeat(32), host.fingerprint()).get(10, TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void liveTcpRelayCannotReadJoinedSessionToken(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        server = impl.startTls(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                host, TrackValidationProfileSource.none());
        ByteArrayOutputStream serverToClient = new ByteArrayOutputStream();
        try (ServerSocket proxy = new ServerSocket(0)) {
            Thread relay = Thread.ofVirtual().start(() -> {
                try (Socket victim = proxy.accept();
                     Socket realHost = new Socket("127.0.0.1", server.port())) {
                    Thread.ofVirtual().start(() -> copy(victim, realHost, null));
                    copy(realHost, victim, serverToClient);
                } catch (Exception ignored) {
                    // The client closes the joined socket after the assertion.
                }
            });
            URI uri = URI.create("wss://127.0.0.1:" + proxy.getLocalPort() + "/race");
            RaceClient joined = RaceClient.connect(uri, guest, "Guest", FP,
                    server.tlsCertificateSha256(), host.fingerprint())
                    .get(10, TimeUnit.SECONDS);
            synchronized (serverToClient) {
                assertTrue(serverToClient.size() > 0);
                assertFalse(serverToClient.toString(StandardCharsets.ISO_8859_1)
                        .contains(joined.sessionToken()));
            }
            joined.close();
            relay.join(1000);
        }
    }

    private static void copy(Socket source, Socket destination, ByteArrayOutputStream capture) {
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.getInputStream().read(buffer)) >= 0) {
                if (capture != null) {
                    synchronized (capture) {
                        capture.write(buffer, 0, count);
                    }
                }
                destination.getOutputStream().write(buffer, 0, count);
                destination.getOutputStream().flush();
            }
        } catch (Exception ignored) {
            // Socket closure ends each relay direction.
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void garbageTextClosesConnection(RaceHostImpl impl, @TempDir Path dir) throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText("not json", true).join();
        awaitClosed(probe);
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void oversizedFragmentedTextClosesConnection(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        String fragment = "x".repeat(6000);
        socket.sendText(fragment, false).join();
        socket.sendText(fragment, true).join();
        awaitClosed(probe);
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void hostCanStartRoundViaExecute(RaceHostImpl impl, @TempDir Path dir) throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        PlayerIdentity client = PlayerIdentity.loadOrCreate(dir.resolve("client"));
        ClientHandshake handshake = new ClientHandshake(client, "Probe", FP);
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome)
                awaitMessage(probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        awaitMessage(probe, ControlMessage.JoinAccepted.class);
        server.execute(() -> server.room().requestStartRound(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null)));
        ControlMessage.RoundStart start = (ControlMessage.RoundStart)
                awaitMessage(probe, ControlMessage.RoundStart.class);
        assertTrue(start.deadlineHubMillis() > start.countdownEndsAtHubMillis());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    // ---- transport parity: malformed and abusive traffic on an admitted connection ----

    /** Frames RFC 6455 or the room budgets forbid; both transports must end the connection. */
    enum Malformed {
        UNMASKED_TEXT(client -> client.sendFrame(0x80 | RawWebSocketClient.OP_TEXT, false,
                "{}".getBytes(StandardCharsets.UTF_8), -1)),
        RESERVED_DATA_OPCODE(client -> client.sendFrame(true, 0x3, new byte[1])),
        RESERVED_CONTROL_OPCODE(client -> client.sendFrame(true, 0xB, new byte[0])),
        OVERSIZED_PING(client -> client.sendFrame(true, RawWebSocketClient.OP_PING,
                new byte[126])),
        FRAGMENTED_PING(client -> client.sendFrame(false, RawWebSocketClient.OP_PING,
                new byte[1])),
        CONTINUATION_WITHOUT_MESSAGE(client -> client.sendFrame(true,
                RawWebSocketClient.OP_CONTINUATION, new byte[3])),
        NEW_MESSAGE_INSIDE_FRAGMENTED_MESSAGE(client -> {
            client.sendFrame(false, RawWebSocketClient.OP_TEXT, "{".getBytes(StandardCharsets.UTF_8));
            client.sendFrame(true, RawWebSocketClient.OP_TEXT, "{}".getBytes(StandardCharsets.UTF_8));
        }),
        INVALID_UTF8_TEXT(client -> client.sendFrame(true, RawWebSocketClient.OP_TEXT,
                new byte[] {(byte) 0xC3, 0x28})),
        NON_MINIMAL_LENGTH(client -> client.sendFrame(0x80 | RawWebSocketClient.OP_BINARY, true,
                new byte[5], 2)),
        LENGTH_HIGH_BIT_SET(client -> client.sendOversizedHeader(
                0x80 | RawWebSocketClient.OP_BINARY, Long.MIN_VALUE + 16)),
        FRAME_OVER_64_KIB(client -> client.sendOversizedHeader(
                0x80 | RawWebSocketClient.OP_TEXT, Protocol.MAX_CONTROL_BYTES + 1L)),
        MESSAGE_OVER_64_KIB(client -> {
            byte[] fragment = new byte[40_000];
            Arrays.fill(fragment, (byte) 'x');
            client.sendFrame(false, RawWebSocketClient.OP_TEXT, fragment);
            client.sendFrame(true, RawWebSocketClient.OP_CONTINUATION, fragment);
        }),
        BINARY_OVER_4_KIB(client -> client.sendFrame(true, RawWebSocketClient.OP_BINARY,
                new byte[Protocol.MAX_BINARY_BYTES + 1])),
        ONE_BYTE_CLOSE(client -> client.sendFrame(true, RawWebSocketClient.OP_CLOSE,
                new byte[] {3})),
        INVALID_CLOSE_STATUS(client -> client.sendFrame(true, RawWebSocketClient.OP_CLOSE,
                new byte[] {0x03, (byte) 0xED})); // 1005 must never appear on the wire

        private final FrameSender sender;

        Malformed(FrameSender sender) {
            this.sender = sender;
        }

        void send(RawWebSocketClient client) throws Exception {
            sender.send(client);
        }
    }

    @FunctionalInterface
    interface FrameSender {
        void send(RawWebSocketClient client) throws Exception;
    }

    static Stream<Arguments> malformedCases() {
        List<Arguments> cases = new ArrayList<>();
        for (RaceHostImpl impl : RaceHostImpl.values()) {
            for (Malformed malformed : Malformed.values()) {
                cases.add(Arguments.of(impl, malformed));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("malformedCases")
    void malformedFrameOnAdmittedConnectionClosesIt(RaceHostImpl impl, Malformed malformed,
                                                   @TempDir Path dir) throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertInstanceOf(ControlMessage.JoinAccepted.class, client.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Raw", FP));
            // Positive control: the admitted connection is healthy and answers a ping.
            client.sendFrame(true, RawWebSocketClient.OP_PING, new byte[] {7});
            RawWebSocketClient.Frame pong = client.readFrame();
            while (pong.opcode() == RawWebSocketClient.OP_TEXT) {
                pong = client.readFrame(); // RoomState broadcasts may precede the pong
            }
            assertEquals(RawWebSocketClient.OP_PONG, pong.opcode());
            assertArrayEquals(new byte[] {7}, pong.payload());

            malformed.send(client);
            client.awaitServerClose();
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void upgradeWithoutKeyIsRejected(RaceHostImpl impl, @TempDir Path dir) throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            int status = client.sendRequest("GET /race HTTP/1.1\r\nHost: x\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n");
            assertEquals(400, status);
            client.awaitServerClose();
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void unsupportedWebSocketVersionIsRejected(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            int status = client.sendRequest("GET /race HTTP/1.1\r\nHost: x\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 99\r\n\r\n");
            assertEquals(426, status);
            if (impl == RaceHostImpl.JDK) {
                // Netty answers 426 but leaves the socket to its 60 s idle timeout.
                client.awaitServerClose();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void fifthSocketFromOneAddressIsRefused(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        List<RawWebSocketClient> admitted = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                RawWebSocketClient client = RawWebSocketClient.connect(server.port());
                admitted.add(client);
                assertEquals(101, client.upgrade(), "socket " + i + " is within the cap");
            }
            try (RawWebSocketClient fifth = RawWebSocketClient.connect(server.port())) {
                assertEquals(-1, fifth.awaitServerClose(), "fifth socket must be dropped");
            }
            admitted.removeFirst().close();
            // Releasing one slot admits a new socket again.
            long deadline = System.currentTimeMillis() + 5_000;
            boolean readmitted = false;
            while (!readmitted && System.currentTimeMillis() < deadline) {
                RawWebSocketClient again = RawWebSocketClient.connect(server.port());
                admitted.add(again);
                readmitted = again.upgrade() == 101;
                if (!readmitted) {
                    admitted.removeLast().close();
                    Thread.sleep(50);
                }
            }
            assertTrue(readmitted, "a released slot must admit a new socket");
        } finally {
            for (RawWebSocketClient client : admitted) {
                client.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void messageFloodClosesAdmittedConnection(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        try (RawWebSocketClient client = RawWebSocketClient.connect(server.port())) {
            assertInstanceOf(ControlMessage.JoinAccepted.class, client.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Raw", FP));
            for (int i = 0; i < ConnectionHygiene.MESSAGE_RATE_BURST * 3; i++) {
                client.sendText(ControlCodec.encode(client.sessionToken(),
                        new ControlMessage.Ping(i)));
            }
            client.awaitServerClose();
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void abruptDisconnectRemovesThePlayer(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        RaceClient watcher = RaceClient.connect(
                URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                PlayerIdentity.loadOrCreate(dir.resolve("watcher")), "Watcher", FP)
                .get(10, TimeUnit.SECONDS);
        RawWebSocketClient doomed = RawWebSocketClient.connect(server.port());
        assertInstanceOf(ControlMessage.JoinAccepted.class, doomed.join(
                PlayerIdentity.loadOrCreate(dir.resolve("doomed")), "Doomed", FP));
        awaitEvent(watcher, event -> event instanceof RaceClient.Control control
                && control.message() instanceof ControlMessage.RoomState state
                && state.players().size() == 2);

        doomed.abortWithReset();

        awaitEvent(watcher, event -> event instanceof RaceClient.Control control
                && control.message() instanceof ControlMessage.RoomState state
                && state.players().size() == 1);
        assertEquals(1, onRoomThread(() -> server.room().playerCount()));
        watcher.close();
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void closeReleasesPortDropsPeersAndRejectsTasks(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        int port = server.port();
        try (RawWebSocketClient client = RawWebSocketClient.connect(port)) {
            assertInstanceOf(ControlMessage.JoinAccepted.class, client.join(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Raw", FP));
            server.close();
            client.awaitServerClose();
        }
        assertThrows(IllegalStateException.class, () -> server.execute(() -> { }));
        server.close(); // idempotent
        assertThrows(ConnectException.class, () -> new Socket("127.0.0.1", port).close());
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void failingTaskDoesNotStopTheRoomThread(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = start(impl, PlayerIdentity.loadOrCreate(dir.resolve("host")));
        server.execute(() -> {
            throw new IllegalStateException("deliberate task failure");
        });
        assertEquals(0, onRoomThread(() -> server.room().playerCount()));
    }

    private <T> T onRoomThread(java.util.function.Supplier<T> query) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> result.complete(query.get()));
        return result.get(5, TimeUnit.SECONDS);
    }

    private static void awaitEvent(RaceClient client, Predicate<RaceClient.InboundEvent> match)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (RaceClient.InboundEvent event : client.drainInbound()) {
                if (match.test(event)) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out waiting for inbound event");
    }

    private static RaceRoomHost start(RaceHostImpl impl, PlayerIdentity host) {
        return impl.start(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                host, TrackValidationProfileSource.none());
    }

    private static void awaitClosed(Probe probe) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!probe.closed && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(probe.closed);
    }
}
