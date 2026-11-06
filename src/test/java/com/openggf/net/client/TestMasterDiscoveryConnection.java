package com.openggf.net.client;

import com.openggf.net.client.MasterAddressResolver.Srv;
import com.openggf.net.identity.PlayerIdentity;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.MasterServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link MasterClient#connect(String, PlayerIdentity, String, String, SSLContext)} discovery over
 * real loopback TLS: a fake SRV lookup supplies ports for the multi-label test name
 * {@value #NAME}, and the dial route sends each candidate to {@code localhost} on the same port.
 */
@Timeout(60)
class TestMasterDiscoveryConnection {
    private static final String NAME = "master.openggf.test";
    private static final String OWNER = "_openggf._tcp." + NAME;

    @TempDir
    Path dir;

    private SelfSignedCertificate certificate;
    private SelfSignedCertificate stranger;
    private SSLContext trust;
    private MasterServer master;
    private NioEventLoopGroup group;
    private final List<Channel> listeners = new ArrayList<>();
    private final List<URI> dialled = new CopyOnWriteArrayList<>();
    private PlayerIdentity identity;

    @BeforeEach
    void start() throws Exception {
        certificate = new SelfSignedCertificate("localhost");
        stranger = new SelfSignedCertificate("localhost");
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("master", certificate.cert());
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        trust = SSLContext.getInstance("TLS");
        trust.init(null, factory.getTrustManagers(), null);
        master = MasterServer.start(new MasterConfig(0, certificate.certificate().getAbsolutePath(),
                certificate.privateKey().getAbsolutePath(), false, "identities.db", 0, "admin",
                1, 1, 1, 1, 8, 8, false, 2, 4, 30, 20, 30, 100, 60), dir.resolve("master"));
        group = new NioEventLoopGroup(1);
        identity = PlayerIdentity.loadOrCreate(dir.resolve("player"));
    }

    @AfterEach
    void stop() throws Exception {
        for (Channel listener : listeners) listener.close().sync();
        if (group != null) group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        if (master != null) master.close();
        if (certificate != null) certificate.delete();
        if (stranger != null) stranger.delete();
    }

    @Test
    void aRefusedCandidateMovesOnAndTheAdmittedEndpointCarriesTheDiscoveredPort() throws Exception {
        int refused = closedPort();
        AtomicInteger lookups = new AtomicInteger();
        MasterAddressResolver.SrvLookup lookup = (owner, budget) -> {
            assertEquals(OWNER, owner);
            assertTrue(budget.compareTo(Duration.ofMillis(MasterClient.DNS_STAGE_MILLIS)) <= 0);
            lookups.incrementAndGet();
            return List.of(new Srv(0, 0, refused, NAME + "."), new Srv(10, 0, master.port(), NAME + "."));
        };
        try (MasterClient client = connect(NAME, lookup).get(30, TimeUnit.SECONDS)) {
            assertTrue(client.isOpen());
            assertEquals(master.port(), client.endpoint().getPort(), "admitted the second candidate");
            assertEquals("/master", client.endpoint().getPath());
            assertEquals(List.of(refused, master.port()), dialled.stream().map(URI::getPort).toList());
        }
        // Each connect looks the name up again: no application cache.
        try (MasterClient again = connect(NAME, lookup).get(30, TimeUnit.SECONDS)) {
            assertEquals(master.port(), again.endpoint().getPort());
        }
        assertEquals(2, lookups.get());
    }

    @Test
    void aCertificateFailureDoesNotFallThroughToALaterCandidate() throws Exception {
        int untrusted = tlsListener(SslContextBuilder.forServer(stranger.certificate(), stranger.privateKey()).build(),
                false);
        ExecutionException failed = assertThrows(ExecutionException.class, () -> connect(NAME, (owner, budget) ->
                List.of(new Srv(0, 0, untrusted, NAME), new Srv(10, 0, master.port(), NAME)))
                .get(30, TimeUnit.SECONDS));
        assertTrue(hasCause(failed, SSLException.class), "certificate failure surfaces: " + failed.getCause());
        assertEquals(List.of(untrusted), dialled.stream().map(URI::getPort).toList(),
                "the trusted master after it is never dialled");
    }

    @Test
    void anAdmissionFailureAfterTheUpgradeDoesNotFallThrough() throws Exception {
        int closing = tlsListener(SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
                .build(), true);
        ExecutionException failed = assertThrows(ExecutionException.class, () -> connect(NAME, (owner, budget) ->
                List.of(new Srv(0, 0, closing, NAME), new Srv(10, 0, master.port(), NAME)))
                .get(30, TimeUnit.SECONDS));
        // The close frame or the dropped socket ends admission; either way after the upgrade.
        assertTrue(failed.getCause() instanceof RaceClient.JoinRejectedException
                || failed.getCause() instanceof IOException, String.valueOf(failed.getCause()));
        assertEquals(List.of(closing), dialled.stream().map(URI::getPort).toList(),
                "the good master after it is never dialled");
    }

    @Test
    void discoveryFailuresAreDistinctAndNeverDial() {
        assertEquals(MasterDiscoveryException.Reason.NOT_PUBLISHED,
                discoveryFailure((owner, budget) -> List.of()));
        assertEquals(MasterDiscoveryException.Reason.LOOKUP_FAILED,
                discoveryFailure((owner, budget) -> { throw new java.net.SocketTimeoutException("silent"); }));
        assertEquals(MasterDiscoveryException.Reason.FOREIGN_TARGET,
                discoveryFailure((owner, budget) -> List.of(new Srv(0, 0, master.port(), "localhost."))));
        assertEquals(List.of(), dialled, "no failed discovery reaches the network");
        ExecutionException invalid = assertThrows(ExecutionException.class,
                () -> connect("multi-dev.openggf.com:27910", (owner, budget) -> fail("no lookup for invalid text"))
                        .get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, invalid.getCause());
    }

    @Test
    void anExplicitUrlBypassesDiscovery() throws Exception {
        String url = "wss://localhost:" + master.port() + "/master";
        try (MasterClient client = MasterClient.connect(url, identity, "Player", "build", trust,
                (owner, budget) -> fail("explicit URLs never query DNS"), new SplittableRandom(1),
                UnaryOperator.identity()).get(30, TimeUnit.SECONDS)) {
            assertEquals(URI.create(url), client.endpoint());
        }
    }

    private MasterDiscoveryException.Reason discoveryFailure(MasterAddressResolver.SrvLookup lookup) {
        ExecutionException failed = assertThrows(ExecutionException.class,
                () -> connect(NAME, lookup).get(10, TimeUnit.SECONDS));
        return assertInstanceOf(MasterDiscoveryException.class, failed.getCause()).reason();
    }

    private java.util.concurrent.CompletableFuture<MasterClient> connect(String address,
                                                                       MasterAddressResolver.SrvLookup lookup) {
        return MasterClient.connect(address, identity, "Player", "build", trust, lookup, new SplittableRandom(3),
                candidate -> {
                    assertEquals(NAME, candidate.getHost(), "candidates keep the typed name");
                    dialled.add(candidate);
                    try {
                        return new URI("wss", null, "localhost", candidate.getPort(), candidate.getPath(), null, null);
                    } catch (URISyntaxException impossible) {
                        throw new IllegalStateException(impossible);
                    }
                });
    }

    /** A TLS WebSocket listener that either fails the TLS handshake or closes right after the upgrade. */
    private int tlsListener(SslContext tls, boolean closeAfterUpgrade) throws InterruptedException {
        Channel listener = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(tls.newHandler(channel.alloc()), new HttpServerCodec(),
                                new HttpObjectAggregator(8192), new WebSocketServerProtocolHandler("/master"),
                                new ChannelInboundHandlerAdapter() {
                                    @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
                                        if (closeAfterUpgrade && event instanceof
                                                WebSocketServerProtocolHandler.HandshakeComplete) {
                                            ctx.writeAndFlush(new CloseWebSocketFrame(1008, "not admitted"))
                                                    .addListener(ignored -> ctx.close());
                                        }
                                        ctx.fireUserEventTriggered(event);
                                    }
                                });
                    }
                }).bind(0).sync().channel();
        listeners.add(listener);
        return ((InetSocketAddress) listener.localAddress()).getPort();
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }
}
