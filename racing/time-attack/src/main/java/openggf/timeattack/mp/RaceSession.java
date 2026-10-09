package openggf.timeattack.mp;

import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.ModStorage;
import openggf.racing.client.ClientRaceSession;
import openggf.racing.client.DirectJoinAddress;
import openggf.racing.client.DirectRoomRegistration;
import openggf.racing.client.MasterClient;
import openggf.racing.client.RaceClient;
import openggf.racing.client.RaceConnection;
import openggf.racing.host.HostMasterLink;
import openggf.racing.host.jdk.JdkRaceHostServer;
import openggf.racing.hub.RoomHostConfig;
import openggf.racing.hub.TrackValidationProfile;
import openggf.racing.hub.TrackValidationProfileSource;
import openggf.racing.identity.PlayerIdentity;
import openggf.racing.protocol.ControlMessage;
import openggf.racing.protocol.Protocol;
import openggf.timeattack.GhostStore;
import openggf.timeattack.TimeAttackLaunchRequest;
import openggf.timeattack.TimeAttackRuntime;
import openggf.timeattack.TimeAttackSettings;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One player's multiplayer racing session, owned by the Time Attack scene: hosting a LAN room
 * on the in-process JDK host, joining one by invite, browsing the master server and creating or
 * joining its rooms, the master heartbeat, launching each round as a gameplay run, and leaving.
 *
 * <p><b>Threads.</b> Every public method runs on the engine thread (scene callbacks and the
 * run host callbacks of a launched round). Connecting work runs on one virtual thread per
 * operation and hands its result back through a queue that {@link #poll()} drains, so network
 * threads never touch the scene, the run or the coordinator. The master link thread only drains
 * the master socket and sends heartbeats; the room's own reads happen on the host's room thread.
 *
 * <p><b>Ownership.</b> The session owns every socket and thread it starts: the room host, the
 * master connection, the room connection (through the coordinator) and the master link thread.
 * {@link #leave()} closes them all and is called on leave, on a lost or kicked connection, when
 * the scene exits (which the engine also does on a mod fault and at shutdown) and before every
 * new operation. A connecting operation owns what it has created until the engine thread adopts
 * it, so cancelling or leaving mid-connect closes half-built rooms and late connections too.
 */
public final class RaceSession implements AutoCloseable {
    private final Logger LOGGER = Logger.getLogger(RaceSession.class.getName());
    /** Storage file holding the last LAN invite this player hosted. */
    public static final String INVITE_FILE = "lan-invite.txt";
    private static final int MAX_ROOM_PLAYERS = 8;
    private static final int DEFAULT_JOIN_WINDOW_SECONDS = 300;
    private static final long MASTER_LINK_TICK_MILLIS = 1000;
    private static final int HEARTBEAT_TICKS = 5;
    private static final long AWAIT_SLICE_MILLIS = 50;

    /** Where the session is. */
    public enum State { IDLE, CONNECTING, BROWSING, LOBBY, RACING }

    /** The route and rules the menu chose for a hosted room or a master browse. */
    public record RoundSetup(String gameId, int zone, int act, String character,
                             String characterPolicy, String lockedCharacter, int windowSeconds) {
        public RoundSetup {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(character, "character");
            Objects.requireNonNull(characterPolicy, "characterPolicy");
        }
    }

    /** Starts a gameplay run from the owning scene ({@code ctx.gameplay().launch}). */
    @FunctionalInterface
    public interface RunLauncher {
        RunHandle launch(RunSpec spec, RunHost host);
    }

    /** Work done off the engine thread; returns the step that adopts its result on it. */
    @FunctionalInterface
    private interface Operation {
        Runnable run(Attempt attempt) throws Exception;
    }

    private final Path identityDir;
    private final GhostStore ghostStore;
    private final TimeAttackSettings settings;
    private final ModStorage storage;
    private final RunLauncher launcher;
    private final Supplier<TimeAttackRuntime> runtimeFactory;
    private final LongSupplier clockMillis;
    private final ConcurrentLinkedQueue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final Object identityLock = new Object();
    private final AtomicInteger connectWorkers = new AtomicInteger();
    private PlayerIdentity identity;

    private State state = State.IDLE;
    private String status = "";
    private String notice;
    private boolean closed;
    private Attempt attempt;

    private JdkRaceHostServer host;
    private MasterClient master;
    private String masterRoomId;
    private MasterLink masterLink;
    private RoundSetup browseSetup;
    private String browseFingerprint;
    private MultiplayerRaceCoordinator coordinator;
    private boolean hosting;
    private ControlMessage.RoundConfig roundConfig;
    private String character;
    private String lanInvite;
    private TimeAttackRuntime runtime;
    private RunHandle handle;
    private int launchedRound = -1;

    public RaceSession(Path identityDir, GhostStore ghostStore, TimeAttackSettings settings,
                       ModStorage storage, RunLauncher launcher,
                       Supplier<TimeAttackRuntime> runtimeFactory, LongSupplier clockMillis) {
        this.identityDir = Objects.requireNonNull(identityDir, "identityDir");
        this.ghostStore = Objects.requireNonNull(ghostStore, "ghostStore");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.storage = storage;
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    // ── Operations (engine thread) ───────────────────────────────────────

    /**
     * Hosts a LAN room on the configured port with a fresh pinned TLS certificate and joins it.
     *
     * @param fingerprint the determinism fingerprint of {@code setup.gameId()} on this build
     */
    public void hostLan(RoundSetup setup, String fingerprint) {
        Objects.requireNonNull(setup, "setup");
        leave();
        int port = settings.hostPort();
        String configuredName = settings.displayName();
        start("Starting LAN room...", State.IDLE, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            JdkRaceHostServer server = op.own(JdkRaceHostServer.startTls(port,
                    roomConfig(name, setup, fingerprint), me, TrackValidationProfileSource.none()),
                    JdkRaceHostServer::close);
            String pin = server.tlsCertificateSha256();
            String invite = "HOST_IP:" + server.port() + "#"
                    + DirectJoinAddress.shareCode(pin, me.fingerprint());
            RaceClient client = op.own(op.await(RaceClient.connect(
                    URI.create("wss://127.0.0.1:" + server.port() + "/race"), me, name, fingerprint,
                    pin, me.fingerprint()), RaceClient.JOIN_TIMEOUT_MILLIS + 2000, RaceClient::close),
                    RaceClient::close);
            ControlMessage.RoundConfig round = roundConfigFor(setup);
            return () -> {
                host = server;
                enterLobby(client, true, round, setup.character(), invite);
            };
        });
    }

    /**
     * Joins a LAN room from a complete invite ({@code address#share-code}).
     *
     * @param fingerprint the determinism fingerprint of the game the room races
     */
    public void joinLan(String invite, String character, String fingerprint) {
        leave();
        DirectJoinAddress address;
        try {
            address = DirectJoinAddress.parse(invite, settings.hostPort());
        } catch (IllegalArgumentException e) {
            fail("Invalid LAN invite: " + e.getMessage());
            return;
        }
        String trimmed = invite.trim();
        String configuredName = settings.displayName();
        start("Joining LAN room...", State.IDLE, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            RaceClient client = op.own(op.await(RaceClient.connect(address.uri(), me, name, fingerprint,
                    address.certificateSha256(), address.hostFingerprint()),
                    RaceClient.JOIN_TIMEOUT_MILLIS + 2000, RaceClient::close), RaceClient::close);
            ControlMessage.RoomDescriptor room = client.joinAccepted().room();
            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig(room.gameId(), room.zone(),
                    room.act(), DEFAULT_JOIN_WINDOW_SECONDS, room.characterPolicy(), room.lockedCharacter());
            String chosen = room.lockedCharacter() != null ? room.lockedCharacter() : character;
            return () -> {
                settings.setLastJoinAddress(trimmed);
                settings.save(storage);
                enterLobby(client, false, round, chosen, null);
            };
        });
    }

    /** Connects to the configured master server and opens the room browser for the setup's game. */
    public void browse(RoundSetup setup, String fingerprint) {
        Objects.requireNonNull(setup, "setup");
        leave();
        String configured = settings.masterUrl();
        if (configured.isBlank()) {
            fail("Set a master server URL in Settings to browse rooms");
            return;
        }
        URI uri;
        try {
            uri = URI.create(configured.trim());
        } catch (IllegalArgumentException e) {
            fail("Invalid master server URL: " + e.getMessage());
            return;
        }
        boolean trustInsecure = settings.masterTrustInsecure();
        String configuredName = settings.displayName();
        start("Connecting to the master server...", State.IDLE, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            SSLContext ssl = trustInsecure ? insecureMasterSslContext() : null;
            MasterClient client = op.own(op.await(MasterClient.connect(uri, me, name, fingerprint, ssl),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 2000, MasterClient::close), MasterClient::close);
            return () -> {
                master = client;
                browseSetup = setup;
                browseFingerprint = fingerprint;
                state = State.BROWSING;
            };
        });
    }

    /** Joins a room listed by the browser (direct rooms fall back to the master relay). */
    public void joinMasterRoom(ControlMessage.RoomSummary room) {
        Objects.requireNonNull(room, "room");
        if (state != State.BROWSING || master == null) {
            return;
        }
        MasterClient client = master;
        RoundSetup setup = browseSetup;
        String fingerprint = browseFingerprint;
        String configuredName = settings.displayName();
        start("Joining " + room.name() + "...", State.BROWSING, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            RaceConnection connection = op.own(op.await(client.joinRoom(room.roomId(), me, name, fingerprint),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS,
                    RaceConnection::close), RaceConnection::close);
            ControlMessage.RoomDescriptor joined = connection.joinAccepted().room();
            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig(joined.gameId(), joined.zone(),
                    joined.act(), setup.windowSeconds(), joined.characterPolicy(), joined.lockedCharacter());
            String chosen = joined.lockedCharacter() != null ? joined.lockedCharacter() : setup.character();
            return () -> {
                masterRoomId = room.roomId();
                startMasterLink(null);
                enterLobby(connection, false, round, chosen, null);
            };
        });
    }

    /**
     * Creates a master-listed room and joins it: {@code RELAY} rooms live on the master,
     * {@code DIRECT} rooms on the in-process host advertised with its certificate pin.
     */
    public void createMasterRoom(String routing) {
        if (state != State.BROWSING || master == null) {
            return;
        }
        MasterClient client = master;
        RoundSetup setup = browseSetup;
        String fingerprint = browseFingerprint;
        boolean direct = "DIRECT".equals(routing);
        int port = settings.hostPort();
        String configuredName = settings.displayName();
        start("Creating room...", State.BROWSING, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            List<String> votePool = VoteTrackPools.forGame(setup.gameId());
            JdkRaceHostServer server = !direct ? null : op.own(JdkRaceHostServer.startTls(port,
                    roomConfig(name, setup, fingerprint), me, TrackValidationProfileSource.none()),
                    JdkRaceHostServer::close);
            ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(name + "'s room",
                    setup.gameId(), setup.zone(), setup.act(), setup.characterPolicy(),
                    setup.lockedCharacter(), MAX_ROOM_PLAYERS, false);
            String roomId = op.await(DirectRoomRegistration.createRoom(client, descriptor, direct ? "DIRECT" : "RELAY",
                    direct ? server.port() : 0, fingerprint, votePool,
                    direct ? server.tlsCertificateSha256() : null),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 1000,
                    late -> client.leaveRoom(late.roomId())).roomId();
            op.own(roomId, client::leaveRoom);
            if (direct) {
                client.bindHostLink(HostMasterLink.forServer(server, new HostMasterLink.MessageSink() {
                    @Override public void sendControl(ControlMessage message) { client.sendControl(message); }
                    @Override public void sendBinary(byte[] data) { client.sendBinary(data); }
                }));
                // A failed join must not leave the browser's master socket feeding a closed host.
                op.own(client, linked -> linked.bindHostLink(null));
            }
            RaceConnection connection = op.own(op.await(client.joinRoom(roomId, me, name, fingerprint),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS,
                    RaceConnection::close), RaceConnection::close);
            ControlMessage.RoundConfig round = roundConfigFor(setup);
            return () -> {
                host = server;
                masterRoomId = roomId;
                startMasterLink(server);
                enterLobby(connection, true, round, setup.character(), null);
            };
        });
    }

    /**
     * Stops a connecting operation. From the browser this returns to the browser; otherwise the
     * session leaves.
     */
    public void cancel() {
        if (state != State.CONNECTING || attempt == null) {
            return;
        }
        State fallback = attempt.fallback;
        cancelAttempt();
        if (fallback == State.BROWSING && master != null) {
            state = State.BROWSING;
            status = "";
        } else {
            leave();
        }
    }

    /** Leaves the room or browser and closes every socket and thread the session owns. */
    public void leave() {
        cancelAttempt();
        if (masterLink != null) {
            masterLink.close();
            masterLink = null;
        }
        if (coordinator != null) {
            MultiplayerRaceCoordinator leaving = coordinator;
            coordinator = null;
            leaving.shutdown();
        }
        RunHandle activeRun = handle;
        handle = null;
        runtime = null;
        if (activeRun != null && activeRun.isActive()) {
            activeRun.leave();
        }
        if (master != null) {
            MasterClient leaving = master;
            master = null;
            if (masterRoomId != null && leaving.isOpen()) {
                leaving.leaveRoom(masterRoomId);
            }
            leaving.close();
        }
        masterRoomId = null;
        if (host != null) {
            JdkRaceHostServer leaving = host;
            host = null;
            leaving.close();
        }
        browseSetup = null;
        browseFingerprint = null;
        hosting = false;
        roundConfig = null;
        character = null;
        lanInvite = null;
        launchedRound = -1;
        state = State.IDLE;
        status = "";
    }

    /** Scene exit, mod fault or engine shutdown: leaves and refuses new operations. */
    @Override
    public void close() {
        closed = true;
        leave();
        completions.clear();
    }

    // ── Engine-thread pump ───────────────────────────────────────────────

    /**
     * Runs once per scene tick before the views: adopts finished connecting work, pumps the
     * room between rounds, leaves on a lost or kicked connection, and launches each new round.
     */
    public void poll() {
        Runnable completion;
        while ((completion = completions.poll()) != null) {
            completion.run();
        }
        if (state == State.BROWSING && master != null) {
            // Nothing else reads the browser's master socket; a full queue would disconnect it.
            master.drainInbound();
        }
        if (state != State.LOBBY || coordinator == null) {
            return;
        }
        coordinator.pump();
        MultiplayerHudState hud = coordinator.hudState();
        if (hud.connectionLost() || hud.kickReason() != null) {
            String reason = hud.kickReason() != null ? "Kicked from the room: " + hud.kickReason()
                    : "Connection to the room was lost";
            leave();
            notice = reason;
            return;
        }
        maybeLaunchRound();
    }

    /** The launched round's run has ended; the scene is back and the lobby resumes. */
    public void runEnded() {
        if (state != State.RACING) {
            return;
        }
        runtime = null;
        handle = null;
        if (coordinator != null) {
            coordinator.detachRuntime();
            state = State.LOBBY;
        } else {
            state = State.IDLE;
        }
    }

    /** Applies a settings change that affects the live room (the minimap). */
    public void settingsChanged() {
        if (coordinator != null) {
            coordinator.setShowMinimap(settings.minimap());
        }
    }

    private void maybeLaunchRound() {
        ClientRaceSession.Phase phase = coordinator.session().phase();
        if ((phase != ClientRaceSession.Phase.COUNTDOWN && phase != ClientRaceSession.Phase.RUNNING)
                || coordinator.roundsStarted() == launchedRound) {
            return;
        }
        ControlMessage.RoundConfig round = coordinator.session().roundConfig();
        if (round == null) {
            return;
        }
        launchedRound = coordinator.roundsStarted();
        String chosen = round.lockedCharacter() != null ? round.lockedCharacter() : character;
        TimeAttackRuntime run = runtimeFactory.get();
        run.armForLaunch(new TimeAttackLaunchRequest(round.gameId(), round.zone(), round.act(), chosen, List.of()));
        if (!run.isActive()) {
            notice = "This round could not be started here";
            return;
        }
        coordinator.attachRuntime(run);
        try {
            RunHandle launched = launcher.launch(run.runSpec(), run);
            run.attachHandle(launched);
            runtime = run;
            handle = launched;
            state = State.RACING;
        } catch (RuntimeException e) {
            coordinator.detachRuntime();
            LOGGER.log(Level.WARNING, "Unable to launch the race round", e);
            notice = "Unable to start the round: " + rootMessage(e);
        }
    }

    private void enterLobby(RaceConnection connection, boolean hostingRoom, ControlMessage.RoundConfig round,
                            String localCharacter, String invite) {
        if (localCharacter != null) {
            connection.sendControl(new ControlMessage.SelectCharacter(localCharacter));
        }
        ClientRaceSession clientSession = new ClientRaceSession(clockMillis);
        clientSession.applyJoin(connection.joinAccepted());
        coordinator = new MultiplayerRaceCoordinator(RaceTransport.from(connection), clientSession, clockMillis,
                settings.masterUrl(), settings.masterTrustInsecure(), ghostStore);
        coordinator.setShowMinimap(settings.minimap());
        if (hostingRoom && host != null) {
            coordinator.setLevelReadyHook(this::applyTrackProfile);
        }
        hosting = hostingRoom;
        roundConfig = round;
        character = localCharacter;
        lanInvite = invite;
        launchedRound = -1;
        state = State.LOBBY;
        status = "";
        if (invite != null) {
            LOGGER.info("LAN invite (replace HOST_IP with this computer's LAN address): " + invite);
            if (storage != null && !storage.write(INVITE_FILE, invite + "\n")) {
                LOGGER.warning("Unable to save the LAN invite to the mod storage");
            }
        }
    }

    /** The host room validates ghosts against the act the host actually loaded (engine thread). */
    private void applyTrackProfile(RunLevelStart start) {
        TrackValidationProfile profile = LiveLevelProfileFactory.fromLevelStart(start);
        JdkRaceHostServer server = host;
        if (profile == null || server == null) {
            return;
        }
        try {
            server.execute(() -> server.room().applyTrackValidationProfile(profile));
        } catch (IllegalStateException closedHost) {
            LOGGER.fine("Room host closed before the track profile was applied");
        }
    }

    private void startMasterLink(JdkRaceHostServer server) {
        if (masterLink != null) {
            masterLink.close();
        }
        if (master != null && masterRoomId != null) {
            masterLink = new MasterLink(master, masterRoomId, server, browseSetup);
        }
    }

    // ── State for views ──────────────────────────────────────────────────

    public State state() {
        return state;
    }

    /** What a connecting operation is doing ("Joining LAN room..."), or empty. */
    public String status() {
        return status;
    }

    /** A one-time message for the menu or browser (a failure or the reason the room closed). */
    public String consumeNotice() {
        String result = notice;
        notice = null;
        return result;
    }

    /** True while connecting work started from the browser is in flight. */
    public boolean connectingFromBrowser() {
        return state == State.CONNECTING && attempt != null && attempt.fallback == State.BROWSING;
    }

    public MultiplayerRaceCoordinator coordinator() {
        return coordinator;
    }

    public boolean isHosting() {
        return hosting;
    }

    /** The round a host starts from the lobby (the room's configured track and rules). */
    public ControlMessage.RoundConfig roundConfig() {
        return roundConfig;
    }

    public String character() {
        return character;
    }

    /** The LAN invite template for a LAN host ({@code HOST_IP:port#code}), or null. */
    public String lanInvite() {
        return lanInvite;
    }

    /** The master room listing while browsing, or null. */
    public RoomDirectory roomDirectory() {
        return master == null ? null : RoomDirectory.of(master);
    }

    /** The game the browser lists, or null. */
    public String browseGameId() {
        return browseSetup == null ? null : browseSetup.gameId();
    }

    /** The room host while hosting in-process (tests and diagnostics). */
    JdkRaceHostServer hostServer() {
        return host;
    }

    MasterClient masterClient() {
        return master;
    }

    TimeAttackRuntime activeRuntime() {
        return runtime;
    }

    /** Connect workers still running, including cancelled ones finishing in the background (tests). */
    int connectWorkersRunning() {
        return connectWorkers.get();
    }

    boolean masterLinkRunning() {
        return masterLink != null && masterLink.thread.isAlive();
    }

    /** Test seam: enters the lobby over an existing room connection. */
    void enterLobbyForTest(RaceConnection connection, boolean hostingRoom, ControlMessage.RoundConfig round,
                           String localCharacter) {
        leave();
        enterLobby(connection, hostingRoom, round, localCharacter, null);
    }

    // ── Connecting operations ────────────────────────────────────────────

    private void start(String connectingStatus, State fallback, Operation operation) {
        if (closed) {
            return;
        }
        cancelAttempt();
        Attempt current = new Attempt(fallback);
        attempt = current;
        state = State.CONNECTING;
        status = connectingStatus;
        connectWorkers.incrementAndGet();
        Thread.ofVirtual().name("time-attack-race-connect").start(() -> {
            Runnable adopt;
            try {
                Runnable onEngineThread = operation.run(current);
                adopt = () -> {
                    if (attempt == current && current.release()) {
                        attempt = null;
                        onEngineThread.run();
                    } else {
                        current.cancel();
                    }
                };
            } catch (Throwable failure) {
                current.cancel();
                adopt = () -> {
                    if (attempt == current) {
                        attempt = null;
                        operationFailed(current.fallback, connectingStatus, failure);
                    }
                };
            }
            completions.add(adopt);
            connectWorkers.decrementAndGet();
        });
    }

    private void operationFailed(State fallback, String what, Throwable failure) {
        String reason = rootMessage(failure);
        LOGGER.log(Level.WARNING, "Time Attack multiplayer: " + what + " failed: " + reason);
        String message = "Failed: " + reason;
        if (fallback == State.BROWSING && master != null && master.isOpen()) {
            state = State.BROWSING;
            status = "";
            notice = message;
        } else {
            leave();
            notice = message;
        }
    }

    private void fail(String message) {
        state = State.IDLE;
        status = "";
        notice = message;
    }

    private void cancelAttempt() {
        Attempt current = attempt;
        attempt = null;
        if (current != null) {
            current.cancel();
        }
    }

    /** Resources one connecting operation has created and not yet handed to the session. */
    private static final class Attempt {
        private record Owned<T>(T resource, Consumer<T> closer) {
            void close() {
                try {
                    closer.accept(resource);
                } catch (RuntimeException ignored) {
                    // best-effort cleanup of an abandoned connection
                }
            }
        }

        private final State fallback;
        private final List<Owned<?>> owned = new ArrayList<>();
        private volatile boolean cancelled;

        Attempt(State fallback) {
            this.fallback = fallback;
        }

        /** Tracks {@code resource}; if the attempt was cancelled it is closed and the work stops. */
        synchronized <T> T own(T resource, Consumer<T> closer) {
            Owned<T> entry = new Owned<>(resource, closer);
            if (cancelled) {
                entry.close();
                throw new CancellationException("left while connecting");
            }
            owned.add(entry);
            return resource;
        }

        /**
         * Waits for {@code future} without interrupting the worker (identity creation must never
         * be cut short), giving up when the attempt is cancelled or the timeout passes; a result
         * that arrives later is discarded.
         */
        <T> T await(CompletableFuture<T> future, long timeoutMillis, Consumer<T> discardLate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                if (cancelled) {
                    future.thenAccept(discardLate);
                    throw new CancellationException("left while connecting");
                }
                try {
                    return future.get(AWAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException slice) {
                    if (System.nanoTime() >= deadline) {
                        future.thenAccept(discardLate);
                        throw new TimeoutException("timed out");
                    }
                } catch (ExecutionException e) {
                    throw e.getCause() instanceof Exception cause ? cause : e;
                }
            }
        }

        /** Hands every tracked resource to the session; false when the attempt was cancelled. */
        synchronized boolean release() {
            if (cancelled) {
                return false;
            }
            owned.clear();
            return true;
        }

        void cancel() {
            List<Owned<?>> closing;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                closing = new ArrayList<>(owned);
                owned.clear();
            }
            for (int i = closing.size() - 1; i >= 0; i--) {
                closing.get(i).close();
            }
        }
    }

    /** Loads the player identity once; serialized so an abandoned and a new connect never both create it. */
    private PlayerIdentity identity() throws Exception {
        synchronized (identityLock) {
            if (identity == null) {
                identity = PlayerIdentity.loadOrCreate(identityDir);
            }
            return identity;
        }
    }

    /** The configured name (captured on the engine thread), or the identity prefix when blank. */
    private static String displayName(PlayerIdentity me, String configured) {
        return configured.isBlank() ? me.fingerprint().substring(0, 8) : configured.trim();
    }

    private static RoomHostConfig roomConfig(String name, RoundSetup setup, String fingerprint) {
        return new RoomHostConfig(name + "'s room", setup.gameId(), setup.zone(), setup.act(),
                setup.characterPolicy(), setup.lockedCharacter(), Protocol.MAX_PLAYERS_DIRECT, fingerprint,
                VoteTrackPools.forGame(setup.gameId()));
    }

    private static ControlMessage.RoundConfig roundConfigFor(RoundSetup setup) {
        return new ControlMessage.RoundConfig(setup.gameId(), setup.zone(), setup.act(), setup.windowSeconds(),
                setup.characterPolicy(), setup.lockedCharacter());
    }

    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current instanceof TimeoutException) {
            return "timed out";
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private SSLContext insecureMasterSslContext() throws Exception {
        LOGGER.warning("TIME ATTACK MASTER TLS CERTIFICATE VERIFICATION IS DISABLED");
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }

    /**
     * The master socket's keeper while in a master room: drains its inbound queue (nothing
     * reads it once a room is joined, and a full queue disconnects) and, for a direct host,
     * sends the heartbeat and track updates the master needs to keep listing the room. The
     * room's player count and track are read on the room host's own thread.
     */
    private final class MasterLink {
        private final MasterClient client;
        private final String roomId;
        private final JdkRaceHostServer server;
        private final Thread thread;
        private volatile boolean stopped;
        private int advertisedZone;
        private int advertisedAct;

        MasterLink(MasterClient client, String roomId, JdkRaceHostServer server, RoundSetup setup) {
            this.client = client;
            this.roomId = roomId;
            this.server = server;
            this.advertisedZone = setup == null ? -1 : setup.zone();
            this.advertisedAct = setup == null ? -1 : setup.act();
            this.thread = Thread.ofVirtual().name("time-attack-master-link").start(this::run);
        }

        private void run() {
            int ticks = 0;
            try {
                while (!stopped && client.isOpen()) {
                    Thread.sleep(MASTER_LINK_TICK_MILLIS);
                    for (RaceClient.InboundEvent event : client.drainInbound()) {
                        if (event instanceof RaceClient.Disconnected disconnected) {
                            LOGGER.warning("Master connection closed: " + disconnected.reason());
                        }
                    }
                    if (server != null && ++ticks % HEARTBEAT_TICKS == 0) {
                        server.execute(this::heartbeatOnRoomThread);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IllegalStateException hostClosed) {
                // the room host closed; leave() is tearing the session down
            }
        }

        private void heartbeatOnRoomThread() {
            if (stopped) {
                return;
            }
            ControlMessage.RoomDescriptor current = server.room().descriptor();
            if (current.zone() != advertisedZone || current.act() != advertisedAct) {
                client.sendControl(new ControlMessage.RoomTrackUpdate(roomId, current.zone(), current.act()));
                advertisedZone = current.zone();
                advertisedAct = current.act();
            }
            client.heartbeat(roomId, server.room().playerCount());
        }

        void close() {
            stopped = true;
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
