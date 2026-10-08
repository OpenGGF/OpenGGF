package com.openggf.net.host.jdk;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLContext;

/**
 * JDK-only implementation of {@link RaceRoomHost}: the in-process {@code /race} room host
 * that can run inside a validated code mod (no Netty, no Bouncy Castle, no static state).
 *
 * <p>Threads: one virtual acceptor, one virtual reader and one virtual writer per peer, and
 * one daemon platform room thread that runs the 50 ms {@link RoomHost#tick()}, every room
 * callback and every {@link #execute(Runnable)} task in submission order. The room is
 * sized for direct rooms of up to {@code Protocol.MAX_PLAYERS_DIRECT} players.
 *
 * <p>Contract shared with the Netty {@code RaceHostServer}: exact {@code /race} path; RFC 6455
 * version 13 with masked client frames; 64 KiB frames and reassembled messages; 4 KiB binary
 * packets; strict UTF-8 text; at most four sockets per remote address; the shared
 * {@link ConnectionHygiene.RateBucket} per peer; 10 s to finish TLS plus the upgrade; 60 s read
 * idle; optional TLS with a fresh self-signed certificate pinned by SHA-256. Deliberate
 * differences, all stricter or more graceful: RSV bits are rejected because no extension is
 * negotiated; ping and pong frames draw from the rate bucket; non-{@code /race}, non-GET and
 * malformed upgrade requests get an HTTP error and are closed instead of idling; room-initiated
 * closes flush queued frames and send a close frame with the reason; total sockets and the
 * outbound queue are capped (see {@link JdkHostLimits}).
 */
public final class JdkRaceHostServer implements RaceRoomHost {
    private final RoomHost room;
    private final JdkHostLimits limits;
    private final SSLContext tls;
    private final String tlsCertificateSha256;
    private final ServerSocket serverSocket;
    private final ScheduledThreadPoolExecutor roomExecutor;
    private final ConnectionHygiene.ConnectionCounter perAddress;
    private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();
    private final Set<Thread> ioThreads = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger nextConnectionId = new AtomicInteger();
    private final System.Logger logger = System.getLogger(JdkRaceHostServer.class.getName());
    private volatile Thread roomThread;
    private Thread acceptor;

    private JdkRaceHostServer(RoomHost room, JdkHostLimits limits, SSLContext tls,
                              String tlsCertificateSha256, ServerSocket serverSocket) {
        this.room = room;
        this.limits = limits;
        this.tls = tls;
        this.tlsCertificateSha256 = tlsCertificateSha256;
        this.serverSocket = serverSocket;
        this.perAddress = new ConnectionHygiene.ConnectionCounter(limits.maxConnectionsPerIp());
        int boundPort = serverSocket.getLocalPort();
        this.roomExecutor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "race-room-host-" + boundPort);
            thread.setDaemon(true);
            roomThread = thread;
            return thread;
        });
        roomExecutor.setRemoveOnCancelPolicy(true);
        // Close deadlines and handshake watchdogs are moot once every socket is aborted.
        roomExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        roomExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    /** Plaintext room on {@code port} (0 = ephemeral), timed by the wall clock. */
    public static JdkRaceHostServer start(int port, RoomHostConfig config,
                                          PlayerIdentity hostIdentity,
                                          TrackValidationProfileSource profiles) {
        return start(port, config, hostIdentity, profiles, System::currentTimeMillis, false,
                JdkHostLimits.defaults());
    }

    /**
     * Broker-pinnable TLS room: a fresh self-signed certificate whose
     * {@link #tlsCertificateSha256()} is advertised in invites and room listings.
     */
    public static JdkRaceHostServer startTls(int port, RoomHostConfig config,
                                             PlayerIdentity hostIdentity,
                                             TrackValidationProfileSource profiles) {
        return start(port, config, hostIdentity, profiles, System::currentTimeMillis, true,
                JdkHostLimits.defaults());
    }

    /**
     * Fully parameterized start. {@code clockMillis} only drives room time (tests and
     * deterministic tools); certificate validity always uses the wall clock.
     */
    public static JdkRaceHostServer start(int port, RoomHostConfig config,
                                          PlayerIdentity hostIdentity,
                                          TrackValidationProfileSource profiles,
                                          LongSupplier clockMillis, boolean tls,
                                          JdkHostLimits limits) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(hostIdentity, "hostIdentity");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(clockMillis, "clockMillis");
        Objects.requireNonNull(limits, "limits");
        SSLContext context = null;
        String pin = null;
        if (tls) {
            try {
                SelfSignedRoomCertificate certificate =
                        SelfSignedRoomCertificate.generate(System.currentTimeMillis());
                context = certificate.serverContext();
                pin = certificate.sha256Hex();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("cannot start authenticated direct room", e);
            }
        }
        RoomHost room = new RoomHost(config, hostIdentity, clockMillis, profiles);
        ServerSocket listening = null;
        try {
            listening = new ServerSocket();
            listening.setReuseAddress(true);
            listening.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            closeQuietly(listening);
            throw new UncheckedIOException("cannot bind race host port " + port, e);
        }
        JdkRaceHostServer host = new JdkRaceHostServer(room, limits, context, pin, listening);
        try {
            host.roomExecutor.scheduleAtFixedRate(() -> host.guarded("room tick", room::tick),
                    limits.tickMillis(), limits.tickMillis(), TimeUnit.MILLISECONDS);
            host.acceptor = host.startIoThread("race-host-accept-" + listening.getLocalPort(),
                    host::acceptLoop);
            return host;
        } catch (RuntimeException | Error e) {
            host.close();
            throw e;
        }
    }

    @Override
    public int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public String tlsCertificateSha256() {
        return tlsCertificateSha256;
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            throw new IllegalStateException("race host is closed");
        }
        try {
            roomExecutor.execute(() -> guarded("room task", task));
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("race host is closed", e);
        }
    }

    @Override
    public RoomHost room() {
        return room;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(limits.shutdownTimeoutMillis());
        boolean interrupted = false;
        closeQuietly(serverSocket);
        Thread acceptingThread = acceptor;
        if (acceptingThread != null) {
            interrupted |= joinUntil(acceptingThread, deadline);
        }
        for (WebSocketConnection connection : connections) {
            connection.abort();
        }
        // Readers queue their onDisconnected before exiting; wait for them before shutdown.
        while (!ioThreads.isEmpty() && System.nanoTime() < deadline) {
            for (Thread thread : ioThreads) {
                interrupted |= joinUntil(thread, deadline);
            }
        }
        roomExecutor.shutdown();
        if (Thread.currentThread() != roomThread) {
            try {
                long remaining = Math.max(0L, deadline - System.nanoTime());
                if (!roomExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    roomExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                interrupted = true;
                roomExecutor.shutdownNow();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- package-private seams used by WebSocketConnection and tests ----

    SSLContext tlsContext() {
        return tls;
    }

    /** Runs {@code action} on the room thread; a callback failure drops that peer, as Netty's handler did. */
    boolean submitRoom(WebSocketConnection connection, Consumer<RoomHost> action) {
        try {
            roomExecutor.execute(() -> {
                try {
                    action.accept(room);
                } catch (Throwable failure) {
                    logger.log(System.Logger.Level.WARNING,
                            "room callback failed for " + connection.remoteHost(), failure);
                    connection.abort();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** Aborts {@code connection} after {@code delayMillis} unless the returned handle is cancelled. */
    Future<?> scheduleAbort(WebSocketConnection connection, long delayMillis) {
        try {
            return roomExecutor.schedule(connection::abort, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            connection.abort();
            return CompletableFuture.completedFuture(null);
        }
    }

    Thread startIoThread(String name, Runnable body) {
        Thread thread = Thread.ofVirtual().name(name).unstarted(() -> {
            try {
                body.run();
            } catch (Throwable failure) {
                logger.log(System.Logger.Level.WARNING, name + " failed", failure);
            } finally {
                ioThreads.remove(Thread.currentThread());
            }
        });
        ioThreads.add(thread);
        thread.start();
        return thread;
    }

    void connectionFinished(WebSocketConnection connection) {
        if (connections.remove(connection)) {
            perAddress.release(connection.remoteHost());
        }
    }

    /** True once the port, every I/O thread and the room thread have been released. */
    boolean released() {
        return serverSocket.isClosed() && ioThreads.isEmpty() && roomExecutor.isTerminated();
    }

    int liveIoThreads() {
        return ioThreads.size();
    }

    int openConnections() {
        return connections.size();
    }

    // ---- internals ----

    private void acceptLoop() {
        while (!closed.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (closed.get() || serverSocket.isClosed()) {
                    return;
                }
                logger.log(System.Logger.Level.WARNING, "race host accept failed", e);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    return;
                }
                continue;
            }
            admit(socket);
        }
    }

    private void admit(Socket socket) {
        String remote = socket.getInetAddress().getHostAddress();
        if (connections.size() >= limits.maxConnections() || !perAddress.tryAcquire(remote)) {
            closeQuietly(socket);
            return;
        }
        WebSocketConnection connection = new WebSocketConnection(this, socket, remote,
                nextConnectionId.incrementAndGet(), limits);
        connections.add(connection);
        startIoThread("race-host-conn-" + connection.id(), connection::run);
    }

    private void guarded(String what, Runnable task) {
        try {
            task.run();
        } catch (Throwable failure) {
            logger.log(System.Logger.Level.WARNING, what + " failed", failure);
        }
    }

    /** Joins without giving up on interruption; returns whether an interrupt was swallowed. */
    private static boolean joinUntil(Thread thread, long deadlineNanos) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                thread.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort release.
        }
    }
}
