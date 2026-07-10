package com.openggf.net.master;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.hub.BundledProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.Protocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutor;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone TLS master/browser server with master-owned relay rooms. */
public final class MasterServer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(MasterServer.class.getName());
    private static final int MAX_CONNECTIONS_PER_IP = 4;

    private final MasterConfig config;
    private final Path dataDir;
    private final NioEventLoopGroup brokerGroup;
    private final NioEventLoopGroup relayGroup;
    private final Channel serverChannel;
    private final SqliteIdentityStore store;
    private final TrustLadder ladder;
    private final SessionRegistry registry;
    private final GuestTunnelRouter tunnels;
    private final RelayRoomManager relays;
    private final RoomBroker broker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private AdminEndpoint admin;

    private MasterServer(MasterConfig config, Path dataDir,
                         NioEventLoopGroup brokerGroup, NioEventLoopGroup relayGroup,
                         Channel serverChannel, SqliteIdentityStore store,
                         TrustLadder ladder, SessionRegistry registry,
                         GuestTunnelRouter tunnels, RelayRoomManager relays,
                         RoomBroker broker) {
        this.config = config;
        this.dataDir = dataDir;
        this.brokerGroup = brokerGroup;
        this.relayGroup = relayGroup;
        this.serverChannel = serverChannel;
        this.store = store;
        this.ladder = ladder;
        this.registry = registry;
        this.tunnels = tunnels;
        this.relays = relays;
        this.broker = broker;
    }

    public static MasterServer start(MasterConfig config, Path dataDir) throws Exception {
        Files.createDirectories(dataDir);
        NioEventLoopGroup brokerGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup relayGroup = new NioEventLoopGroup(
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        SqliteIdentityStore store = null;
        Channel channel = null;
        try {
            var clock = (java.util.function.LongSupplier) System::currentTimeMillis;
            store = new SqliteIdentityStore(dataDir.resolve(config.dbPath()));
            NewIdentityCache cache = new NewIdentityCache(config.newIdentityCacheSize(),
                    config.newIdentityCacheTtlMinutes() * 60_000L, clock);
            TrustLadder ladder = new TrustLadder(store, cache, config.thresholds(), clock);
            SessionRegistry registry = new SessionRegistry(clock, config);
            GuestTunnelRouter tunnels = new GuestTunnelRouter();
            List<Executor> roomLoops = new ArrayList<>();
            for (EventExecutor eventExecutor : relayGroup) {
                roomLoops.add(eventExecutor);
            }
            Executor brokerLoop = brokerGroup.next();
            PlayerIdentity masterIdentity = PlayerIdentity.loadOrCreate(
                    dataDir.resolve("master-identity"));
            RelayRoomManager relays = new RelayRoomManager(masterIdentity, ladder,
                    new BundledProfileSource(), roomLoops, brokerLoop, clock,
                    registry::heartbeat);
            RoomBroker broker = new RoomBroker(masterIdentity, config, registry, store,
                    ladder, cache, clock, relays, tunnels);
            ConnectionHygiene.ConnectionCounter counter =
                    new ConnectionHygiene.ConnectionCounter(MAX_CONNECTIONS_PER_IP);
            SslContext ssl = sslContext(config);
            channel = new ServerBootstrap().group(brokerGroup, relayGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socket) {
                            ChannelPipeline pipeline = socket.pipeline();
                            if (ssl != null) {
                                pipeline.addLast(ssl.newHandler(socket.alloc()));
                            }
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(8192));
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    "/master", null, true,
                                    Protocol.MAX_MASTER_FRAME_BYTES));
                            pipeline.addLast(new WebSocketFrameAggregator(
                                    Protocol.MAX_MASTER_FRAME_BYTES));
                            pipeline.addLast(new IdleStateHandler(60, 0, 0));
                            pipeline.addLast(new MasterChannelHandler(broker, relays,
                                    tunnels, brokerLoop, counter));
                        }
                    }).bind(config.port()).syncUninterruptibly().channel();
            brokerGroup.next().scheduleAtFixedRate(broker::tick,
                    1, 1, TimeUnit.SECONDS);
            brokerGroup.next().scheduleAtFixedRate(relays::tickAll,
                    50, 50, TimeUnit.MILLISECONDS);
            MasterServer server = new MasterServer(config, dataDir, brokerGroup,
                    relayGroup, channel, store, ladder, registry, tunnels, relays, broker);
            server.admin = AdminEndpoint.start(config, server, dataDir);
            return server;
        } catch (Throwable failure) {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
            if (store != null) {
                store.close();
            }
            relayGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            brokerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            throw failure;
        }
    }

    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int adminPort() {
        return admin.port();
    }

    public void execute(Runnable task) {
        if (closed.get()) {
            throw new IllegalStateException("master server is closed");
        }
        brokerGroup.next().execute(task);
    }

    public RoomBroker broker() { return broker; }
    public RelayRoomManager relays() { return relays; }

    public void sanction(IdentityStore.SanctionRecord sanction) {
        ladder.sanction(sanction);
    }

    /** Test-only deterministic promotion, invoked on the broker loop. */
    public void establishForTest(String fingerprint) {
        long now = System.currentTimeMillis();
        long firstSeen = now - config.thresholds().establishedAgeMillis() - 1;
        store.establishForTest(fingerprint, firstSeen, now,
                config.thresholds().establishedCleanRounds());
        ladder.tierOf(fingerprint);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (admin != null) {
            admin.close();
        }
        serverChannel.close().syncUninterruptibly();
        relayGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        brokerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        store.close();
    }

    private static SslContext sslContext(MasterConfig config) throws Exception {
        if (config.plaintextForTest()) {
            LOG.log(System.Logger.Level.WARNING,
                    "MASTER RUNNING WITHOUT TLS - test mode only");
            return null;
        }
        if (config.tlsCertPath() == null || config.tlsKeyPath() == null) {
            throw new IllegalArgumentException("TLS certificate and key are required");
        }
        return SslContextBuilder.forServer(new File(config.tlsCertPath()),
                new File(config.tlsKeyPath())).build();
    }
}
