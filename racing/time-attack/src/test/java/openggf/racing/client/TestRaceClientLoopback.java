package openggf.racing.client;

import openggf.racing.server.host.RaceHostImpl;

import openggf.racing.ghost.GhostFrame;
import openggf.racing.ghost.GhostFrameCodec;
import openggf.racing.host.ControlledRaceHost;
import openggf.racing.host.RaceRoomHost;
import openggf.racing.hub.HostRoundEngine;
import openggf.racing.hub.RoomHostConfig;
import openggf.racing.hub.TrackValidationProfileSource;
import openggf.racing.identity.PlayerIdentity;
import openggf.racing.protocol.ControlMessage;
import openggf.racing.protocol.ControlCodec;
import openggf.racing.protocol.GhostPackets;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class TestRaceClientLoopback {
    private static final String FP = "0.6:cafe1234";
    private RaceRoomHost server;
    private ControlledRaceHost controlled;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private RaceRoomHost startServer(RaceHostImpl impl, Path dir, String policy)
            throws Exception {
        controlled = new ControlledRaceHost(impl);
        return controlled.start(0,
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

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void connectsChatsAndStreamsGhostFrames(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = startServer(impl, dir, "OPEN");
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

        controlled.startRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 10, "OPEN", null));
        await(a, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.RoundStart);
        controlled.advance(HostRoundEngine.COUNTDOWN_MILLIS);

        byte[] frame = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1,
                false, false, false, 2, false), frame, 0);
        a.sendControl(new ControlMessage.AttemptStart(1));
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

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void fingerprintMismatchSurfacesJoinRejected(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = startServer(impl, dir, "OPEN");
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

    @Test
    void malformedFinalJoinClosesDirectConnection(@TempDir Path dir) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        AtomicReference<Channel> peer = new AtomicReference<>();
        Channel listening = null;
        try {
            String invalidJoin = ControlCodec.encode(null,
                    new ControlMessage.JoinAccepted("room-token", -1, null, null));
            listening = new ServerBootstrap().group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(8192));
                            channel.pipeline().addLast(
                                    new WebSocketServerProtocolHandler("/race"));
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override public void userEventTriggered(
                                        ChannelHandlerContext context, Object event)
                                        throws Exception {
                                    if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                                        peer.set(context.channel());
                                        context.writeAndFlush(new TextWebSocketFrame(invalidJoin));
                                    }
                                    super.userEventTriggered(context, event);
                                }
                            });
                        }
                    }).bind(0).sync().channel();
            int port = ((java.net.InetSocketAddress) listening.localAddress()).getPort();
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> RaceClient.connect(URI.create("ws://127.0.0.1:" + port + "/race"),
                                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", FP)
                            .get(5, TimeUnit.SECONDS));
            assertInstanceOf(openggf.racing.protocol.ProtocolViolationException.class,
                    rejected.getCause());
            Channel connection = peer.get();
            assertNotNull(connection);
            assertTrue(connection.closeFuture().await(5, TimeUnit.SECONDS),
                    "malformed room admission must close the direct socket");
        } finally {
            if (listening != null) listening.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
