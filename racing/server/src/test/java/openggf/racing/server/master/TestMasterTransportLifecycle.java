package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static openggf.racing.server.master.TestRoomBrokerAdversarial.FP;

/** Cross-loop close races are deterministic; a few raw sockets verify the actual pipeline. */
@Timeout(30)
class TestMasterTransportLifecycle {
    static final class HeldExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) {
            assertTrue(tasks.size() < 128, "test executor budget exceeded");
            tasks.addLast(task);
        }
        void drain() {
            for (int i = 0; !tasks.isEmpty(); i++) {
                assertTrue(i < 256, "executor did not quiesce");
                tasks.removeFirst().run();
            }
        }
    }

    static final class LoopbackChannel extends EmbeddedChannel {
        LoopbackChannel(ChannelHandler... handlers) { super(handlers); }
        @Override protected SocketAddress remoteAddress0() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }
        void peerDisconnectBeforePendingTasks() throws Exception {
            // Both close() and the Unsafe wrapper drain EmbeddedChannel tasks. A real
            // peer can become inactive before queued cross-loop callbacks execute.
            super.doClose();
            pipeline().fireChannelInactive();
        }
    }

    static final class HeldOutbound extends ChannelOutboundHandlerAdapter {
        final ArrayDeque<TextWebSocketFrame> frames = new ArrayDeque<>();
        @Override public void write(ChannelHandlerContext context, Object message,
                                    ChannelPromise promise) {
            assertTrue(frames.size() < 16, "held response budget exceeded");
            frames.addLast((TextWebSocketFrame) message);
            promise.setSuccess();
        }
        @Override public void flush(ChannelHandlerContext context) {
            // Reaching EmbeddedChannel's Unsafe flush auto-drains queued IO work.
        }
        ControlMessage read() {
            TextWebSocketFrame frame = frames.pollFirst();
            assertNotNull(frame, "missing control response");
            try { return ControlCodec.decode(frame.text()).message(); }
            finally { frame.release(); }
        }
        void releaseAll() {
            TextWebSocketFrame frame;
            while ((frame = frames.pollFirst()) != null) frame.release();
        }
    }

    private static void upgraded(EmbeddedChannel channel) {
        channel.pipeline().fireUserEventTriggered(
                new WebSocketServerProtocolHandler.HandshakeComplete(
                        "/master", new DefaultHttpHeaders(), null));
    }

    @Test
    void closeBeforeBrokerConnectTaskRunsDoesNotRegisterNullPeer(@TempDir Path dir)
            throws Exception {
        HeldExecutor brokerLoop = new HeldExecutor();
        try (var f = new TestRoomBrokerAdversarial.Fixture(dir)) {
            var counter = new ConnectionHygiene.ConnectionCounter(4);
            EmbeddedChannel channel = new LoopbackChannel(new MasterChannelHandler(
                    f.broker, f.relays, f.tunnels, brokerLoop, counter));
            try {
                upgraded(channel);
                channel.close();
                assertDoesNotThrow(brokerLoop::drain,
                        "queued connect must retain its peer until disconnect cleanup runs");
                f.now[0] += 5_001;
                assertDoesNotThrow(f.broker::tick);
                assertTrue(counter.tryAcquire("127.0.0.1"));
                counter.release("127.0.0.1");
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "RELAY"})
    void closeAfterAttachBeforeIoCallbackReleasesOwnershipWithoutTimeout(String routing,
                                                                       @TempDir Path dir)
            throws Exception {
        HeldExecutor brokerLoop = new HeldExecutor();
        try (var f = new TestRoomBrokerAdversarial.Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            var witness = new TestRoomBrokerAdversarial.Connection();
            String witnessToken = f.admit(witness, owner);
            String roomId = f.create(witness, witnessToken, routing);
            PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
            f.establish(guest);
            HeldOutbound output = new HeldOutbound();
            LoopbackChannel channel = new LoopbackChannel(output, new MasterChannelHandler(
                    f.broker, f.relays, f.tunnels, brokerLoop,
                    new ConnectionHygiene.ConnectionCounter(4)));
            try {
                upgraded(channel);
                brokerLoop.drain();
                ClientHandshake handshake = f.handshake(guest);
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(null,
                        handshake.hello())));
                brokerLoop.drain();
                channel.runPendingTasks();
                ControlMessage.Welcome welcome = assertInstanceOf(ControlMessage.Welcome.class,
                        output.read());
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(null,
                        handshake.onWelcome(welcome))));
                brokerLoop.drain();
                channel.runPendingTasks();
                String token = assertInstanceOf(ControlMessage.JoinAccepted.class,
                        output.read()).sessionToken();
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(token,
                        new ControlMessage.RoomJoinRequest(roomId))));
                brokerLoop.drain();
                channel.runPendingTasks();
                assertInstanceOf(ControlMessage.RoomJoinResult.class, output.read());
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(token,
                        new ControlMessage.RelayAttach(roomId))));
                brokerLoop.drain(); // Attachment exists, IO mode transition still pending.
                if (routing.equals("DIRECT")) {
                    assertEquals(1, f.tunnels.activeTunnels());
                } else {
                    // Exercise the room owner's independent queue before the IO mode
                    // callback. The room already owns this connection after attach.
                    var access = f.relays.find(roomId).orElseThrow();
                    var connection = f.relayAttachments.getLast();
                    ClientHandshake roomHandshake = new ClientHandshake(guest, "Guest", FP,
                            null, AuthenticationTranscript.Role.ROOM_RELAY,
                            () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
                    access.room().onText(connection, ControlCodec.encode(null, roomHandshake.hello()));
                    ControlMessage.Welcome roomWelcome = assertInstanceOf(
                            ControlMessage.Welcome.class, output.read());
                    access.room().onText(connection, ControlCodec.encode(null,
                            roomHandshake.onWelcome(roomWelcome)));
                    assertEquals(1, access.room().playerCount());
                    output.releaseAll();
                }
                channel.peerDisconnectBeforePendingTasks();
                brokerLoop.drain();
                channel.runPendingTasks();
                brokerLoop.drain();
                assertFalse(f.broker.isSessionTokenValid(token));
                assertEquals(0, f.tunnels.activeTunnels(), "closed attach retained its tunnel");
                f.relays.find(roomId).ifPresent(access -> assertEquals(0,
                        access.room().playerCount(), "closed attach retained its room player"));
                assertEquals(1, f.registry.list(null).size(), "guest close must preserve witness room");
                assertNull(witness.closed);
            } finally {
                channel.finishAndReleaseAll();
                output.releaseAll();
            }
        }
    }

    @Test
    void pipelinedRelayAttachAndHelloKeepOrderAndShutdownClosesAcceptedSockets(@TempDir Path dir)
            throws Exception {
        MasterServer server = MasterServer.start(TestMasterServer.testConfig(), dir);
        int port = server.port();
        try (Wire owner = Wire.connect(port); Socket silent = new Socket("127.0.0.1", port)) {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            String token = owner.admit(identity, AuthenticationTranscript.Role.MASTER);
            onBroker(server, () -> server.establishForTest(identity.fingerprint()));
            owner.send(token, new ControlMessage.RoomCreate(new ControlMessage.RoomDescriptor(
                    "Pipeline", "s3k", 0, 0, "OPEN", null, 8, false),
                    "RELAY", 0, FP));
            String roomId = owner.await(ControlMessage.RoomCreated.class).roomId();
            ClientHandshake roomHandshake = new ClientHandshake(identity, "Owner", FP, null,
                    AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
            owner.sendTogether(ControlCodec.encode(token, new ControlMessage.RelayAttach(roomId)),
                    ControlCodec.encode(null, roomHandshake.hello()));
            ControlMessage.Welcome welcome = owner.await(ControlMessage.Welcome.class);
            owner.send(null, roomHandshake.onWelcome(welcome));
            ControlMessage.JoinAccepted room = owner.await(ControlMessage.JoinAccepted.class);
            assertEquals(0, room.playerSlot());
            assertNotNull(room.room());
            server.close();
            owner.awaitClosed();
            silent.setSoTimeout(3_000);
            assertEquals(-1, silent.getInputStream().read());
            assertThrows(IllegalStateException.class, () -> server.execute(() -> { }));
        } finally {
            server.close();
        }
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    @Test
    void fifthAddressConnectionIsRejectedAndCapacityReturnsAfterAbruptClose(@TempDir Path dir)
            throws Exception {
        try (MasterServer server = MasterServer.start(TestMasterServer.testConfig(), dir)) {
            List<Wire> peers = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) peers.add(Wire.connect(server.port()));
                assertThrows(IOException.class, () -> Wire.connect(server.port()));
                peers.removeFirst().close();
                Wire replacement = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (replacement == null && System.nanoTime() < deadline) {
                    try { replacement = Wire.connect(server.port()); }
                    catch (IOException notReleasedYet) { Thread.sleep(10); }
                }
                assertNotNull(replacement, "per-IP capacity did not recover");
                peers.add(replacement);
                String token = replacement.admit(PlayerIdentity.loadOrCreate(dir.resolve("replacement")),
                        AuthenticationTranscript.Role.MASTER);
                replacement.send(token, new ControlMessage.RoomListRequest(null, 0));
                assertTrue(replacement.await(ControlMessage.RoomListResult.class).rooms().isEmpty());
            } finally {
                for (Wire peer : peers) peer.close();
            }
        }
    }

    static void onBroker(MasterServer server, Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.execute(() -> {
            try { task.run(); done.complete(null); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        done.get(5, TimeUnit.SECONDS);
    }

    /** Master-only wire helper; malformed/concatenated messages bypass client-side admission. */
    static final class Wire implements AutoCloseable {
        final Socket socket;
        final DataInputStream input;
        private Wire(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(3_000);
            input = new DataInputStream(socket.getInputStream());
        }
        static Wire connect(int port) throws IOException {
            Wire peer = new Wire(new Socket("127.0.0.1", port));
            try {
                peer.socket.getOutputStream().write(("GET /master HTTP/1.1\r\nHost: localhost\r\n"
                        + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                        + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "
                        + Base64.getEncoder().encodeToString(new byte[16]) + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                String status = peer.line();
                if (!status.contains(" 101 ")) throw new IOException("upgrade rejected: " + status);
                while (!peer.line().isEmpty()) { }
                return peer;
            } catch (IOException | RuntimeException failure) {
                peer.close();
                throw failure;
            }
        }
        String line() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int i = 0; i < 8_192; i++) {
                int next = input.read();
                if (next < 0) throw new EOFException();
                if (next == '\n') return bytes.toString(StandardCharsets.US_ASCII).stripTrailing();
                bytes.write(next);
            }
            throw new IOException("HTTP line exceeded test budget");
        }
        void send(String token, ControlMessage message) throws IOException {
            sendTogether(ControlCodec.encode(token, message));
        }
        void sendTogether(String... messages) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (String message : messages) {
                byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                assertTrue(payload.length <= 65_535);
                bytes.write(0x81);
                if (payload.length < 126) bytes.write(0x80 | payload.length);
                else { bytes.write(0xFE); bytes.write(payload.length >>> 8); bytes.write(payload.length); }
                bytes.write(new byte[4]); // A zero mask remains a valid masked client frame.
                bytes.write(payload);
            }
            socket.getOutputStream().write(bytes.toByteArray());
            socket.getOutputStream().flush();
        }
        <T extends ControlMessage> T await(Class<T> type) throws IOException {
            for (int count = 0; count < 32; count++) {
                int first = input.read();
                if (first < 0) throw new EOFException("awaiting " + type.getSimpleName());
                int length = input.readUnsignedByte();
                assertEquals(0, length & 0x80, "server frame must be unmasked");
                if (length == 126) length = input.readUnsignedShort();
                else if (length == 127) {
                    long wide = input.readLong();
                    assertTrue(wide >= 0 && wide <= 132_096, "server frame exceeds test budget");
                    length = (int) wide;
                }
                byte[] payload = input.readNBytes(length);
                assertEquals(length, payload.length);
                if ((first & 15) == 8) throw new EOFException("closed awaiting " + type.getSimpleName());
                assertEquals(0x81, first, "expected complete text response");
                ControlMessage message = ControlCodec.decode(new String(payload,
                        StandardCharsets.UTF_8)).message();
                if (type.isInstance(message)) return type.cast(message);
            }
            throw new AssertionError("response budget exhausted awaiting " + type.getSimpleName());
        }
        String admit(PlayerIdentity identity, AuthenticationTranscript.Role role) throws Exception {
            ClientHandshake handshake = new ClientHandshake(identity, "Peer", FP, null, role,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
            send(null, handshake.hello());
            send(null, handshake.onWelcome(await(ControlMessage.Welcome.class)));
            ControlMessage.PowChallenge challenge = await(ControlMessage.PowChallenge.class);
            send(null, new ControlMessage.PowSolution("IDENTITY",
                    identity.creationPowNonce(challenge.difficultyBits())));
            return await(ControlMessage.JoinAccepted.class).sessionToken();
        }
        void awaitClosed() throws IOException {
            for (int budget = 0; budget < 65_536; budget++) {
                if (input.read() < 0) return;
            }
            fail("closed server emitted excessive data");
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
