package com.openggf.net.host.jdk;

import com.openggf.net.protocol.Protocol;

/**
 * Transport budgets for {@link JdkRaceHostServer}.
 *
 * <p>{@link #defaults()} mirrors the Netty pipeline in {@code RaceHostServer}: 4 sockets
 * per remote address ({@code MAX_CONNECTIONS_PER_IP}), a 10 s TLS-plus-upgrade deadline
 * (Netty's SslHandler and WebSocket handshake timeouts), 60 s read idle
 * ({@code IdleStateHandler(60, 0, 0)}), a 50 ms room tick, 4 KiB request line and
 * 8 KiB header block ({@code HttpServerCodec} defaults), 64 KiB frame payload and
 * reassembled message ({@code WebSocketServerProtocolHandler} and
 * {@code WebSocketFrameAggregator} at {@link Protocol#MAX_CONTROL_BYTES}) and 4 KiB binary
 * packets ({@link Protocol#MAX_BINARY_BYTES}).
 *
 * <p>Three budgets have no Netty counterpart and only bound resources the blocking design
 * would otherwise leave open: a total socket cap, an outbound queue cap above the hub's
 * 1 MiB backpressure disconnect, and the close grace a peer gets to acknowledge a close
 * frame before its socket is dropped.
 */
public record JdkHostLimits(int maxConnectionsPerIp,
                            int maxConnections,
                            long handshakeTimeoutMillis,
                            long idleTimeoutMillis,
                            long tickMillis,
                            long closeGraceMillis,
                            long shutdownTimeoutMillis,
                            int maxRequestLineBytes,
                            int maxRequestHeaderBytes,
                            int maxFramePayloadBytes,
                            int maxMessageBytes,
                            int maxBinaryMessageBytes,
                            int maxQueuedOutboundBytes) {
    public static final int DEFAULT_MAX_CONNECTIONS_PER_IP = 4;
    public static final int DEFAULT_MAX_CONNECTIONS = 64;
    public static final long DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 10_000;
    public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = 60_000;
    public static final long DEFAULT_TICK_MILLIS = 50;
    public static final long DEFAULT_CLOSE_GRACE_MILLIS = 2_000;
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 5_000;
    public static final int DEFAULT_MAX_REQUEST_LINE_BYTES = 4096;
    public static final int DEFAULT_MAX_REQUEST_HEADER_BYTES = 8192;
    public static final int DEFAULT_MAX_QUEUED_OUTBOUND_BYTES = 4 * 1024 * 1024;

    public JdkHostLimits {
        requirePositive(maxConnectionsPerIp, "maxConnectionsPerIp");
        requirePositive(maxConnections, "maxConnections");
        requirePositive(handshakeTimeoutMillis, "handshakeTimeoutMillis");
        requirePositive(idleTimeoutMillis, "idleTimeoutMillis");
        requirePositive(tickMillis, "tickMillis");
        requirePositive(closeGraceMillis, "closeGraceMillis");
        requirePositive(shutdownTimeoutMillis, "shutdownTimeoutMillis");
        requirePositive(maxRequestLineBytes, "maxRequestLineBytes");
        requirePositive(maxRequestHeaderBytes, "maxRequestHeaderBytes");
        requirePositive(maxFramePayloadBytes, "maxFramePayloadBytes");
        requirePositive(maxMessageBytes, "maxMessageBytes");
        requirePositive(maxBinaryMessageBytes, "maxBinaryMessageBytes");
        requirePositive(maxQueuedOutboundBytes, "maxQueuedOutboundBytes");
        if (handshakeTimeoutMillis > Integer.MAX_VALUE || idleTimeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("socket timeouts must fit in an int");
        }
    }

    /** Budgets matching the Netty room pipeline, plus the three JDK-only resource caps. */
    public static JdkHostLimits defaults() {
        return new JdkHostLimits(DEFAULT_MAX_CONNECTIONS_PER_IP, DEFAULT_MAX_CONNECTIONS,
                DEFAULT_HANDSHAKE_TIMEOUT_MILLIS, DEFAULT_IDLE_TIMEOUT_MILLIS,
                DEFAULT_TICK_MILLIS, DEFAULT_CLOSE_GRACE_MILLIS,
                DEFAULT_SHUTDOWN_TIMEOUT_MILLIS, DEFAULT_MAX_REQUEST_LINE_BYTES,
                DEFAULT_MAX_REQUEST_HEADER_BYTES, Protocol.MAX_CONTROL_BYTES,
                Protocol.MAX_CONTROL_BYTES, Protocol.MAX_BINARY_BYTES,
                DEFAULT_MAX_QUEUED_OUTBOUND_BYTES);
    }

    public JdkHostLimits withHandshakeTimeoutMillis(long millis) {
        return new JdkHostLimits(maxConnectionsPerIp, maxConnections, millis,
                idleTimeoutMillis, tickMillis, closeGraceMillis, shutdownTimeoutMillis,
                maxRequestLineBytes, maxRequestHeaderBytes, maxFramePayloadBytes,
                maxMessageBytes, maxBinaryMessageBytes, maxQueuedOutboundBytes);
    }

    public JdkHostLimits withIdleTimeoutMillis(long millis) {
        return new JdkHostLimits(maxConnectionsPerIp, maxConnections, handshakeTimeoutMillis,
                millis, tickMillis, closeGraceMillis, shutdownTimeoutMillis,
                maxRequestLineBytes, maxRequestHeaderBytes, maxFramePayloadBytes,
                maxMessageBytes, maxBinaryMessageBytes, maxQueuedOutboundBytes);
    }

    public JdkHostLimits withMaxConnections(int perIp, int total) {
        return new JdkHostLimits(perIp, total, handshakeTimeoutMillis, idleTimeoutMillis,
                tickMillis, closeGraceMillis, shutdownTimeoutMillis, maxRequestLineBytes,
                maxRequestHeaderBytes, maxFramePayloadBytes, maxMessageBytes,
                maxBinaryMessageBytes, maxQueuedOutboundBytes);
    }

    public JdkHostLimits withMaxQueuedOutboundBytes(int bytes) {
        return new JdkHostLimits(maxConnectionsPerIp, maxConnections, handshakeTimeoutMillis,
                idleTimeoutMillis, tickMillis, closeGraceMillis, shutdownTimeoutMillis,
                maxRequestLineBytes, maxRequestHeaderBytes, maxFramePayloadBytes,
                maxMessageBytes, maxBinaryMessageBytes, bytes);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
