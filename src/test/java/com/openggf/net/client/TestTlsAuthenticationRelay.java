package com.openggf.net.client;

import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.ControlJsonCodec;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.concurrent.*;
import javax.net.ssl.SSLContext;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class TestTlsAuthenticationRelay {
    @Test
    void validatedConnectionBindingFailsClosedWhenAnotherEngineAppears(@TempDir Path dir) throws Exception {
        var host = PlayerIdentity.loadOrCreate(dir);
        try (var target = JdkRaceHostServer.startTls(0,
                new RoomHostConfig("Target", "s3k", 0, 0, "OPEN", null, 8, "build"), host,
                TrackValidationProfileSource.none())) {
            var pinContext = RaceClient.class.getDeclaredMethod("pinnedSslContext", String.class);
            pinContext.setAccessible(true);
            var observed = new ObservedTlsContext((SSLContext) pinContext.invoke(null, target.tlsCertificateSha256()));
            var socket = HttpClient.newBuilder().sslContext(observed).build().newWebSocketBuilder().buildAsync(
                    URI.create("wss://127.0.0.1:" + target.port() + "/race"), new WebSocket.Listener() {}).get(5, TimeUnit.SECONDS);
            try {
                assertEquals("tls-leaf-sha256:" + target.tlsCertificateSha256(), observed.binding());
                observed.createSSLEngine("127.0.0.1", target.port());
                assertThrows(IllegalStateException.class, observed::binding);
            } finally { socket.abort(); }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void tlsMasterSupportsMasterOwnedAndHostTunneledRelay(boolean hostTunnel, @TempDir Path dir) throws Exception {
        var certificate = new SelfSignedCertificate("localhost");
        try {
            var config = new openggf.racing.server.master.MasterConfig(0,
                    certificate.certificate().getAbsolutePath(), certificate.privateKey().getAbsolutePath(),
                    false, "identities.db", 0, "admin", 1, 1, 1, 1, 8, 8, false, 2, 4, 30, 20, 30, 100, 60);
            var store = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            store.load(null, null); store.setCertificateEntry("master", certificate.cert());
            var trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            var ssl = SSLContext.getInstance("TLS"); ssl.init(null, trust.getTrustManagers(), null);
            var hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
            var guestIdentity = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
            try (var master = openggf.racing.server.master.MasterServer.start(config, dir);
                 var hostRoom = JdkRaceHostServer.startTlsBrokerPending(0,
                         new RoomHostConfig("Room", "s3k", 0, 0, "OPEN", null, 8, "build"), hostIdentity,
                         TrackValidationProfileSource.none())) {
                URI uri = URI.create("wss://localhost:" + master.port() + "/master");
                try (var host = MasterClient.connect(uri.toString(), hostIdentity, "Host", "build", ssl).get(5, TimeUnit.SECONDS);
                     var guest = MasterClient.connect(uri.toString(), guestIdentity, "Guest", "build", ssl).get(5, TimeUnit.SECONDS)) {
                    var established = new CountDownLatch(1);
                    master.execute(() -> { master.establishForTest(hostIdentity.fingerprint()); established.countDown(); });
                    assertTrue(established.await(2, TimeUnit.SECONDS));
                    var descriptor = new ControlMessage.RoomDescriptor("Room", "s3k", 0, 0, "OPEN", null,
                            hostTunnel ? 8 : 64, false);
                    try (var registration = hostTunnel ? DirectRoomRegistration.register(host, hostRoom, 1,
                            "build", java.util.List.of()).get(5, TimeUnit.SECONDS) : null) {
                        var created = registration == null
                                ? host.createRelayRoom(descriptor, "build", java.util.List.of(), null)
                                        .get(5, TimeUnit.SECONDS)
                                : registration.created();
                        var room = guest.joinRoom(created.roomId(), guestIdentity, "Guest", "build")
                                .get(10, TimeUnit.SECONDS);
                        try {
                            assertTrue(room.isOpen()); assertEquals(0, room.playerSlot());
                        } finally { room.close(); }
                    }
                }
            }
        } finally { certificate.delete(); }
    }

    @Test
    void attackerSelectedInvitePinCannotRelayProofToAnotherTlsHost(@TempDir Path dir) throws Exception {
        var host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        var identity = PlayerIdentity.loadOrCreate(dir.resolve("victim"));
        var certificate = new SelfSignedCertificate("localhost");
        var group = new NioEventLoopGroup(1);
        Channel listener = null;
        WebSocket upstream = null;
        try (var target = JdkRaceHostServer.startTls(0,
                new RoomHostConfig("Target", "s3k", 0, 0, "OPEN", null, 8, "build"), host,
                TrackValidationProfileSource.none())) {
            var pinContext = RaceClient.class.getDeclaredMethod("pinnedSslContext", String.class);
            pinContext.setAccessible(true);
            var downstream = new CompletableFuture<Channel>();
            var rejection = new CompletableFuture<ControlMessage.JoinRejected>();
            upstream = HttpClient.newBuilder().sslContext((SSLContext) pinContext.invoke(null,
                    target.tlsCertificateSha256())).build().newWebSocketBuilder().buildAsync(
                    URI.create("wss://127.0.0.1:" + target.port() + "/race"), new WebSocket.Listener() {
                        private final StringBuilder text = new StringBuilder();
                        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence part, boolean last) {
                            text.append(part);
                            if (last) {
                                String message = text.toString(); text.setLength(0);
                                if (new ControlJsonCodec().decodeRoom(message).message() instanceof ControlMessage.JoinRejected denied)
                                    rejection.complete(denied);
                                downstream.thenAccept(channel -> channel.writeAndFlush(new TextWebSocketFrame(message)));
                            }
                            ws.request(1); return null;
                        }
                    }).get(5, TimeUnit.SECONDS);
            WebSocket relay = upstream;
            var tls = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
            listener = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(tls.newHandler(channel.alloc()), new HttpServerCodec(),
                                    new HttpObjectAggregator(8192), new WebSocketServerProtocolHandler("/race"),
                                    new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                                        @Override protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                                            downstream.complete(ctx.channel());
                                            relay.sendText(frame.text(), true);
                                        }
                                    });
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            String attackPin = com.openggf.net.protocol.AuthenticationTranscript.certificateBinding(certificate.cert())
                    .substring("tls-leaf-sha256:".length());
            int port = ((InetSocketAddress) listener.localAddress()).getPort();
            var attempt = RaceClient.connect(URI.create("wss://127.0.0.1:" + port + "/race"), identity,
                    "Alice", "build", attackPin, host.fingerprint());
            assertThrows(ExecutionException.class, () -> attempt.get(8, TimeUnit.SECONDS));
            assertEquals("invalid signature", rejection.get(2, TimeUnit.SECONDS).reason(),
                    "the real target must reject the proof despite attacker-controlled invite pin and forwarded server ID");
        } finally {
            if (upstream != null) upstream.abort();
            if (listener != null) listener.close().sync();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
            certificate.delete();
        }
    }
}
