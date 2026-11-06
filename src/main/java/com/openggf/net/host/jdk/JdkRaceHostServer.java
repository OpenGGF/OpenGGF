package com.openggf.net.host.jdk;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
@com.openggf.game.ModApi
public final class JdkRaceHostServer implements RaceRoomHost {
    private enum Lifecycle { OPEN, CLOSING, RETIRING_ROOM, CLOSED }

    private static final class PeerAdmissionUnavailable extends IllegalStateException {
        PeerAdmissionUnavailable(String message) { super(message); }
    }
    private record RoomWork(WebSocketConnection source, Runnable action,
                            WorkBudget.Reservation reservation) { }
    private record ControlWork(Runnable action, WorkBudget.Reservation reservation) { }

    /** A snapshot of owner retirement, I/O release and deadline state. */
    @com.openggf.game.ModApi
    public record RetirementReceipt(boolean authorityRetired, boolean ioReleased,
                                    boolean timedOut) { }

    private static final int MAX_ROOM_WORK = 512;
    private static final long MAX_ROOM_BYTES = 8L * 1024 * 1024;
    private static final int DATA_SLICE = 32;

    private final RoomHost room;
    private final JdkHostLimits limits;
    private final SSLContext tls;
    private final String tlsCertificateSha256;
    private final ServerSocket serverSocket;
    private final ScheduledThreadPoolExecutor roomExecutor;
    private final ScheduledThreadPoolExecutor watchdog;
    private final WorkBudget.Account roomWork = WorkBudget.PROCESS.account(MAX_ROOM_WORK,
            MAX_ROOM_BYTES);
    private final WorkBudget.Account administrativeWork = WorkBudget.PROCESS.account(
            MAX_ROOM_WORK, MAX_ROOM_BYTES);
    private final WorkBudget.Account administrativeControlWork = WorkBudget.PROCESS.account(
            MAX_ROOM_WORK, 1);
    private final WorkBudget.Account linkRetirementWork = WorkBudget.PROCESS.account(1, 1);
    private final WorkBudget.Account profileWork = WorkBudget.PROCESS.account(1, 1);
    private final WorkBudget.Account controlWork;
    private WorkBudget.Reservation linkRetirementReservation;
    private final WorkBudget.Reservation profileReservation;
    private TrackValidationProfile pendingProfile;
    private boolean profileQueued;
    private final Object mailboxLock = new Object();
    private final ArrayDeque<RoomWork> dataMailbox = new ArrayDeque<>();
    private final ArrayDeque<WorkBudget.Task> deferredAdministrative = new ArrayDeque<>();
    private final ArrayDeque<ControlWork> controlMailbox = new ArrayDeque<>();
    private boolean drainScheduled;
    private final CompletableFuture<Void> ownerRetirement = new CompletableFuture<>();
    private volatile boolean retirementTimedOut;
    private final ConnectionHygiene.ConnectionCounter perAddress;
    private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();
    private final Set<Thread> ioThreads = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.OPEN);
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
        controlWork = WorkBudget.PROCESS.account((int) Math.min(8192L,
                MAX_ROOM_WORK + 2L * limits.maxConnections() + 1), MAX_ROOM_BYTES);
        linkRetirementReservation = WorkBudget.PROCESS.tryReserve(linkRetirementWork,
                controlWork, new WorkBudget.Cost(1, 0)).orElseThrow(
                        () -> new IllegalStateException("race host cleanup capacity unavailable"));
        WorkBudget.Reservation profileCell = WorkBudget.PROCESS.tryReserve(profileWork,
                controlWork, new WorkBudget.Cost(1, 0)).orElse(null);
        if (profileCell == null) {
            linkRetirementReservation.close();
            throw new IllegalStateException("race host profile capacity unavailable");
        }
        profileReservation = profileCell;
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
        this.watchdog = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "race-host-watchdog-" + boundPort);
            thread.setDaemon(true);
            return thread;
        });
        watchdog.setRemoveOnCancelPolicy(true);
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

    public static JdkRaceHostServer startTlsBrokerPending(int port, RoomHostConfig config,
                                                          PlayerIdentity hostIdentity,
                                                          TrackValidationProfileSource profiles) {
        return start(port, config, hostIdentity, profiles, System::currentTimeMillis, true,
                JdkHostLimits.defaults(), true);
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
        return start(port, config, hostIdentity, profiles, clockMillis, tls, limits, false);
    }

    private static JdkRaceHostServer start(int port, RoomHostConfig config,
                                           PlayerIdentity hostIdentity,
                                           TrackValidationProfileSource profiles,
                                           LongSupplier clockMillis, boolean tls,
                                           JdkHostLimits limits, boolean brokerPending) {
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
        if (brokerPending) room.brokerPending();
        try { room.ready().join(); }
        catch (RuntimeException unavailable) {
            room.close("room initialization failed");
            throw new IllegalStateException("room initialization failed");
        }
        ServerSocket listening = null;
        JdkRaceHostServer host = null;
        try {
            listening = new ServerSocket();
            listening.setReuseAddress(true);
            listening.bind(new InetSocketAddress(port));
            host = new JdkRaceHostServer(room, limits, context, pin, listening);
            JdkRaceHostServer started = host;
            host.roomExecutor.scheduleAtFixedRate(() -> {
                        if (started.lifecycle.get() == Lifecycle.OPEN) {
                            started.guarded("room tick", room::tick);
                        }
                    },
                    limits.tickMillis(), limits.tickMillis(), TimeUnit.MILLISECONDS);
            host.acceptor = host.startIoThread("race-host-accept-" + listening.getLocalPort(),
                    host::acceptLoop);
            return host;
        } catch (IOException e) {
            closeQuietly(listening);
            room.close("host startup failed");
            throw new UncheckedIOException("cannot bind race host port " + port, e);
        } catch (RuntimeException | Error e) {
            if (host != null) {
                host.close();
            } else {
                closeQuietly(listening);
                room.close("host startup failed");
            }
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
        if (runDuringRetirement(task)) return;
        if (!queueAdministrative(task)) {
            if (task instanceof WorkBudget.Task owned) owned.close();
            throw new IllegalStateException("race host room mailbox unavailable");
        }
    }

    @Override
    public void executeControl(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (runDuringRetirement(task)) return;
        if (!queueControl(task)) {
            if (task instanceof WorkBudget.Task owned) owned.close();
            throw new IllegalStateException("race host is closed");
        }
    }

    /** Submits the latest loaded-track bounds through a reserved room control slot. */
    public void applyTrackValidationProfile(TrackValidationProfile profile) {
        Objects.requireNonNull(profile, "profile");
        synchronized (mailboxLock) {
            if (lifecycle.get() != Lifecycle.OPEN)
                throw new IllegalStateException("race host is closed");
            pendingProfile = profile;
            if (profileQueued) return;
            profileQueued = true;
            ControlWork work = new ControlWork(() -> {
                TrackValidationProfile selected;
                synchronized (mailboxLock) {
                    selected = pendingProfile;
                    pendingProfile = null;
                    profileQueued = false;
                }
                if (lifecycle.get() == Lifecycle.OPEN)
                    room.applyTrackValidationProfile(selected);
            }, null);
            controlMailbox.addLast(work);
            if (!scheduleDrainLocked()) {
                controlMailbox.removeLastOccurrence(work);
                pendingProfile = null;
                profileQueued = false;
                throw new IllegalStateException("race host owner is unavailable");
            }
        }
    }

    @Override
    public void executeLinkRetirement(Runnable task) {
        Objects.requireNonNull(task, "task");
        boolean accepted = false;
        boolean alreadyRequested = false;
        synchronized (mailboxLock) {
            if (lifecycle.get() == Lifecycle.OPEN) {
                if (linkRetirementReservation == null) {
                    alreadyRequested = true;
                } else {
                    WorkBudget.Reservation reserved = linkRetirementReservation;
                    linkRetirementReservation = null;
                    accepted = enqueueControlLocked(task, reserved, true);
                }
            }
        }
        if (!accepted && task instanceof WorkBudget.Task owned) owned.close();
        if (alreadyRequested) throw new IllegalStateException("master-link retirement already requested");
    }

    private boolean runDuringRetirement(Runnable task) {
        if (lifecycle.get() != Lifecycle.RETIRING_ROOM || Thread.currentThread() != roomThread)
            return false;
        task.run();
        return true;
    }

    @Override
    public RoomHost room() {
        return room;
    }

    @Override
    public void close() {
        if (!lifecycle.compareAndSet(Lifecycle.OPEN, Lifecycle.CLOSING)) {
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
            connectionFinished(connection);
        }
        watchdog.shutdownNow();
        List<WorkBudget.Task> discarded = new java.util.ArrayList<>();
        synchronized (mailboxLock) {
            if (linkRetirementReservation != null) {
                linkRetirementReservation.close();
                linkRetirementReservation = null;
            }
            discarded.addAll(discardDataLocked());
            discarded.addAll(discardControlLocked());
            controlMailbox.addFirst(new ControlWork(this::retireRoom, null));
            scheduleDrainLocked();
        }
        discarded.forEach(task -> guarded("discard room work", task::close));
        if (Thread.currentThread() == roomThread) {
            retireRoom();
        }
        while (!ioThreads.isEmpty() && System.nanoTime() < deadline) {
            for (Thread thread : ioThreads) {
                interrupted |= joinUntil(thread, deadline);
            }
        }
        if (Thread.currentThread() != roomThread) {
            try {
                long remaining = Math.max(0L, deadline - System.nanoTime());
                if (!roomExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    retirementTimedOut = true;
                    Thread currentRoomThread = roomThread;
                    if (currentRoomThread != null) currentRoomThread.interrupt();
                }
            } catch (InterruptedException e) {
                interrupted = true;
                retirementTimedOut = true;
                Thread currentRoomThread = roomThread;
                if (currentRoomThread != null) currentRoomThread.interrupt();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void retireRoom() {
        if (!lifecycle.compareAndSet(Lifecycle.CLOSING, Lifecycle.RETIRING_ROOM)) return;
        try {
            room.close("host closed");
            ownerRetirement.complete(null);
        } catch (Throwable failure) {
            ownerRetirement.completeExceptionally(failure);
            throw failure;
        } finally {
            lifecycle.set(Lifecycle.CLOSED);
            List<WorkBudget.Task> discarded = new java.util.ArrayList<>();
            synchronized (mailboxLock) {
                discarded.addAll(discardDataLocked());
                discarded.addAll(discardControlLocked());
                pendingProfile = null;
                profileQueued = false;
                profileReservation.close();
            }
            discarded.forEach(task -> guarded("discard room work", task::close));
            roomExecutor.shutdown();
        }
    }

    public RetirementReceipt retirementReceipt() {
        return new RetirementReceipt(ownerRetirement.isDone()
                && !ownerRetirement.isCompletedExceptionally(), released(), retirementTimedOut);
    }

    // ---- package-private seams used by WebSocketConnection and tests ----

    SSLContext tlsContext() {
        return tls;
    }

    WorkBudget.Reservation reserveRoom(WebSocketConnection connection, long bytes) {
        WorkBudget.Cost cost = new WorkBudget.Cost(1, bytes);
        while (!connection.aborted()) {
            WorkBudget.Admission<WorkBudget.Reservation> admission;
            synchronized (mailboxLock) {
                if (lifecycle.get() != Lifecycle.OPEN) return null;
                admission = WorkBudget.PROCESS.tryReserveDetailed(connection.roomWork(),
                        roomWork, cost);
            }
            if (admission.accepted()) return admission.value();
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN) return null;
            try {
                WorkBudget.PROCESS.awaitChange(50);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    boolean submitRoom(WebSocketConnection connection, Consumer<RoomHost> action,
                       WorkBudget.Reservation reservation) {
        synchronized (mailboxLock) {
            if (lifecycle.get() != Lifecycle.OPEN) {
                reservation.close();
                return false;
            }
            RoomWork work = new RoomWork(connection,
                    () -> runPeerAction(connection, action), reservation);
            dataMailbox.addLast(work);
            if (!scheduleDrainLocked()) {
                dataMailbox.removeLastOccurrence(work);
                reservation.close();
                return false;
            }
            return true;
        }
    }

    /** A connection's connect/disconnect cells are outside the congestible data mailbox. */
    boolean submitConnectionControl(WebSocketConnection connection,
                                    Consumer<RoomHost> action, boolean disconnect) {
        boolean accepted;
        synchronized (mailboxLock) {
            if (disconnect) discardConnectionDataLocked(connection);
            WorkBudget.Reservation reserved = connection.takeControlReservation(disconnect);
            accepted = reserved != null && enqueueControlLocked(() -> {
                try {
                    runPeerAction(connection, action);
                } finally {
                    if (disconnect) connectionFinished(connection);
                }
            }, reserved, false);
        }
        if (!accepted && disconnect) connectionFinished(connection);
        return accepted;
    }

    List<WorkBudget.Reservation> reserveConnectionControls(WorkBudget.Account origin) {
        List<WorkBudget.Cost> cells = List.of(new WorkBudget.Cost(1, 0),
                new WorkBudget.Cost(1, 0));
        while (true) {
            WorkBudget.Admission<List<WorkBudget.Reservation>> admission;
            synchronized (mailboxLock) {
                if (lifecycle.get() != Lifecycle.OPEN)
                    throw new PeerAdmissionUnavailable("race host is closed");
                admission = WorkBudget.PROCESS.tryReserveBatchDetailed(origin, controlWork, cells);
            }
            if (admission.accepted()) return admission.value();
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN)
                throw new PeerAdmissionUnavailable("race peer cleanup capacity unavailable");
            try {
                WorkBudget.PROCESS.awaitChange(50);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new PeerAdmissionUnavailable("race peer admission interrupted");
            }
        }
    }

    private void runPeerAction(WebSocketConnection connection, Consumer<RoomHost> action) {
        try {
            action.accept(room);
        } catch (Throwable failure) {
            logger.log(System.Logger.Level.WARNING,
                    "room callback failed for " + connection.remoteHost(), failure);
            connection.abort();
        }
    }

    private boolean queueAdministrative(Runnable action) {
        synchronized (mailboxLock) {
            if (lifecycle.get() != Lifecycle.OPEN) return false;
            java.util.Optional<WorkBudget.Reservation> reservation;
            if (action instanceof WorkBudget.Task owned && owned.reservation() != null) {
                if (!deferredAdministrative.isEmpty() || !owned.transferTo(roomWork)) {
                    deferredAdministrative.addLast(owned);
                    if (!scheduleDrainLocked()) {
                        deferredAdministrative.removeLastOccurrence(owned);
                        return false;
                    }
                    return true;
                }
                reservation = java.util.Optional.of(owned.reservation());
            } else {
                reservation = WorkBudget.PROCESS.tryReserve(administrativeWork, roomWork,
                        new WorkBudget.Cost(1, 0));
            }
            if (reservation.isEmpty()) return false;
            RoomWork work = new RoomWork(null, action, reservation.get());
            dataMailbox.addLast(work);
            if (!scheduleDrainLocked()) {
                dataMailbox.removeLastOccurrence(work);
                reservation.get().close();
                return false;
            }
            return true;
        }
    }

    private boolean queueControl(Runnable task) {
        synchronized (mailboxLock) {
            return queueControlLocked(task, administrativeControlWork);
        }
    }

    private boolean queueControlLocked(Runnable task, WorkBudget.Account origin) {
        if (lifecycle.get() != Lifecycle.OPEN) return false;
        var reservation = task instanceof WorkBudget.Task owned && owned.reservation() != null
                ? owned.transferTo(controlWork) ? java.util.Optional.of(owned.reservation())
                : java.util.Optional.<WorkBudget.Reservation>empty()
                : WorkBudget.PROCESS.tryReserve(origin, controlWork, new WorkBudget.Cost(1, 0));
        if (reservation.isEmpty()) return false;
        return enqueueControlLocked(task, reservation.get(), false);
    }

    private boolean enqueueControlLocked(Runnable task, WorkBudget.Reservation reserved, boolean urgent) {
        if (lifecycle.get() != Lifecycle.OPEN) {
            reserved.close();
            return false;
        }
        ControlWork work = new ControlWork(task, reserved);
        if (urgent) controlMailbox.addFirst(work);
        else controlMailbox.addLast(work);
        if (!scheduleDrainLocked()) {
            controlMailbox.remove(work);
            reserved.close();
            return false;
        }
        return true;
    }

    private boolean scheduleDrainLocked() {
        if (drainScheduled) return true;
        drainScheduled = true;
        try {
            roomExecutor.execute(this::drainMailbox);
            return true;
        } catch (RejectedExecutionException e) {
            drainScheduled = false;
            return false;
        }
    }

    private void drainMailbox() {
        int dataHandled = 0;
        while (true) {
            ControlWork control;
            RoomWork data;
            synchronized (mailboxLock) {
                promoteDeferredAdministrativeLocked();
                control = controlMailbox.pollFirst();
                data = control == null && dataHandled < DATA_SLICE
                        ? dataMailbox.pollFirst() : null;
                if (control == null && data == null) {
                    if (dataMailbox.isEmpty() || dataHandled == 0) {
                        drainScheduled = false;
                    } else {
                        try { roomExecutor.execute(this::drainMailbox); }
                        catch (RejectedExecutionException ignored) { drainScheduled = false; }
                    }
                    return;
                }
            }
            if (control != null) {
                try {
                    guarded("room control", control.action());
                } finally {
                    if (control.reservation() != null) control.reservation().close();
                }
            } else {
                try {
                    if (lifecycle.get() == Lifecycle.OPEN) guarded("room task", data.action());
                    else if (data.action() instanceof WorkBudget.Task owned) owned.close();
                } finally {
                    data.reservation().close();
                }
                dataHandled++;
            }
        }
    }

    private List<WorkBudget.Task> discardDataLocked() {
        List<WorkBudget.Task> discarded = new java.util.ArrayList<>();
        WorkBudget.Task deferred;
        while ((deferred = deferredAdministrative.pollFirst()) != null) discarded.add(deferred);
        RoomWork work;
        while ((work = dataMailbox.pollFirst()) != null) {
            if (work.action() instanceof WorkBudget.Task owned) discarded.add(owned);
            else work.reservation().close();
        }
        return discarded;
    }

    private void promoteDeferredAdministrativeLocked() {
        while (!deferredAdministrative.isEmpty()) {
            WorkBudget.Task waiting = deferredAdministrative.peekFirst();
            if (!waiting.transferTo(roomWork)) return;
            deferredAdministrative.removeFirst();
            dataMailbox.addLast(new RoomWork(null, waiting, waiting.reservation()));
        }
    }

    private List<WorkBudget.Task> discardControlLocked() {
        List<WorkBudget.Task> discarded = new java.util.ArrayList<>();
        ControlWork work;
        while ((work = controlMailbox.pollFirst()) != null) {
            if (work.action() instanceof WorkBudget.Task owned) {
                discarded.add(owned);
                if (work.reservation() != owned.reservation() && work.reservation() != null)
                    work.reservation().close();
            } else if (work.reservation() != null) work.reservation().close();
        }
        return discarded;
    }

    private void discardConnectionDataLocked(WebSocketConnection source) {
        for (Iterator<RoomWork> pending = dataMailbox.iterator(); pending.hasNext();) {
            RoomWork work = pending.next();
            if (work.source() == source) {
                pending.remove();
                work.reservation().close();
            }
        }
    }

    /** Aborts {@code connection} after {@code delayMillis} unless the returned handle is cancelled. */
    Future<?> scheduleAbort(WebSocketConnection connection, long delayMillis) {
        try {
            return watchdog.schedule(connection::abort, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            connection.abort();
            return CompletableFuture.completedFuture(null);
        }
    }

    boolean scheduleOutboundRetry(Runnable retry) {
        try {
            watchdog.schedule(retry, 50, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException stopped) {
            return false;
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
        connection.releaseControlReservations();
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
        while (lifecycle.get() == Lifecycle.OPEN) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (lifecycle.get() != Lifecycle.OPEN || serverSocket.isClosed()) {
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
        WebSocketConnection connection;
        try {
            connection = new WebSocketConnection(this, socket, remote,
                    nextConnectionId.incrementAndGet(), limits);
        } catch (PeerAdmissionUnavailable unavailable) {
            perAddress.release(remote);
            closeQuietly(socket);
            return;
        } catch (RuntimeException | Error failure) {
            perAddress.release(remote);
            closeQuietly(socket);
            throw failure;
        }
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
