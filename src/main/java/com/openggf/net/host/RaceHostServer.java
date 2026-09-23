package com.openggf.net.host;

import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.Protocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct-connect WebSocket transport for one player-hosted race room. */
@com.openggf.game.ModApi
public final class RaceHostServer implements AutoCloseable {
    private static final long TICK_MILLIS = 50;
    private static final int MAX_CONNECTIONS_PER_IP = 4;

    private final EventLoopGroup group;
    private final Channel serverChannel;
    private final RoomHost room;
    private final SelfSignedCertificate tlsCertificate;
    private final String tlsCertificateSha256;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RaceHostServer(EventLoopGroup group, Channel serverChannel, RoomHost room,
                           SelfSignedCertificate tlsCertificate, String tlsCertificateSha256) {
        this.group = group;
        this.serverChannel = serverChannel;
        this.room = room;
        this.tlsCertificate = tlsCertificate;
        this.tlsCertificateSha256 = tlsCertificateSha256;
    }

    public static RaceHostServer start(int port, RoomHostConfig config,
                                       PlayerIdentity hostIdentity,
                                       TrackValidationProfileSource profiles) {
        return start(port, config, hostIdentity, profiles, System::currentTimeMillis);
    }

    /** Starts a direct room with a fresh TLS key whose certificate digest is advertised by the broker. */
    static RaceHostServer startAuthenticated(int port, RoomHostConfig config,
                                                    PlayerIdentity hostIdentity,
                                                    TrackValidationProfileSource profiles) {
        return start(port, config, hostIdentity, profiles, System::currentTimeMillis, true);
    }

    // Package-private clock seam: public transport behavior keeps wall time.
    static RaceHostServer start(int port, RoomHostConfig config,
                                PlayerIdentity hostIdentity,
                                TrackValidationProfileSource profiles,
                                LongSupplier clockMillis) {
        return start(port, config, hostIdentity, profiles, clockMillis, false);
    }

    private static RaceHostServer start(int port, RoomHostConfig config,
                                PlayerIdentity hostIdentity,
                                TrackValidationProfileSource profiles,
                                LongSupplier clockMillis, boolean authenticated) {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        SelfSignedCertificate certificate = null;
        SslContext ssl = null;
        String certificateSha256 = null;
        if (authenticated) {
            try {
                certificate = new SelfSignedCertificate("openggf-direct-room");
                ssl = SslContextBuilder.forServer(certificate.certificate(),
                        certificate.privateKey()).build();
                certificateSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(certificate.cert().getEncoded()));
            } catch (Exception e) {
                if (certificate != null) {
                    certificate.delete();
                }
                group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
                throw new IllegalStateException("cannot start authenticated direct room", e);
            }
        }
        final SslContext tls = ssl;
        RoomHost room = new RoomHost(config, hostIdentity, clockMillis, profiles);
        ConnectionHygiene.ConnectionCounter counter =
                new ConnectionHygiene.ConnectionCounter(MAX_CONNECTIONS_PER_IP);
        try {
            Channel serverChannel = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            if (tls != null) {
                                pipeline.addLast(tls.newHandler(channel.alloc()));
                            }
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(Protocol.MAX_CONTROL_BYTES));
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    "/race", null, true, Protocol.MAX_CONTROL_BYTES));
                            pipeline.addLast(new WebSocketFrameAggregator(Protocol.MAX_CONTROL_BYTES));
                            pipeline.addLast(new IdleStateHandler(60, 0, 0));
                            pipeline.addLast(new RaceHostChannelHandler(room, counter));
                        }
                    })
                    .bind(port)
                    .syncUninterruptibly()
                    .channel();
            group.next().scheduleAtFixedRate(room::tick,
                    TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
            return new RaceHostServer(group, serverChannel, room, certificate,
                    certificateSha256);
        } catch (RuntimeException | Error e) {
            if (certificate != null) {
                certificate.delete();
            }
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            throw e;
        }
    }

    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    String tlsCertificateSha256() {
        return tlsCertificateSha256;
    }

    public void execute(Runnable task) {
        if (closed.get()) {
            throw new IllegalStateException("race host is closed");
        }
        group.next().execute(task);
    }

    public RoomHost room() {
        return room;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        serverChannel.close().syncUninterruptibly();
        group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        if (tlsCertificate != null) {
            tlsCertificate.delete();
        }
    }
}
