package openggf.timeattack.mp;

import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.ModStorage;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.DirectJoinAddress;
import com.openggf.net.client.DirectRoomRegistration;
import com.openggf.net.client.MasterClient;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.client.RoomAccessRequiredException;
import com.openggf.net.client.RoomJoinOptions;
import com.openggf.net.client.RoomInviteRejectedException;
import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.DisplayNames;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomInviteCode;
import com.openggf.net.protocol.RoomPassword;
import openggf.timeattack.GhostStore;
import openggf.timeattack.TimeAttackLaunchRequest;
import openggf.timeattack.TimeAttackRuntime;
import openggf.timeattack.TimeAttackSettings;
import openggf.timeattack.TimeAttackTrackCatalog;

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
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    /** Historical export filename. Existing user files are left untouched. */
    public static final String INVITE_FILE = "lan-invite.txt";
    private static final int MAX_ROOM_PLAYERS = 8;
    // RoomDescriptor names are bounded by the master and room hosts (RoomBroker boundedField).
    private static final int MAX_ROOM_NAME_BYTES = 64;
    private static final int DEFAULT_JOIN_WINDOW_SECONDS = 300;
    private static final long MASTER_LINK_TICK_MILLIS = 1000;
    private static final int HEARTBEAT_TICKS = 5;
    private static final long AWAIT_SLICE_MILLIS = 50;

    /** Where the session is. */
    public enum State { IDLE, CONNECTING, PASSWORD_REQUIRED, BROWSING, LOBBY, RACING }

    public record PasswordPrompt(String roomName, String hostIdentity) { }

    private sealed interface PendingJoin permits ManualJoin, CodeJoin {
        State fallback();
        PasswordPrompt prompt();
    }

    private record ManualJoin(DirectJoinAddress address, String character, String fingerprint,
                              String instanceId, long accessEpoch) implements PendingJoin {
        @Override public State fallback() { return State.IDLE; }
        @Override public PasswordPrompt prompt() {
            return new PasswordPrompt("Private LAN room", address.hostFingerprint());
        }
        @Override public String toString() { return "ManualJoin[redacted]"; }
    }

    private record CodeJoin(String code, ControlMessage.RoomJoinResult resolved) implements PendingJoin {
        @Override public State fallback() { return State.BROWSING; }
        @Override public PasswordPrompt prompt() {
            return new PasswordPrompt("Private room", resolved.hostServerId());
        }
        @Override public String toString() { return "CodeJoin[redacted]"; }
    }

    /**
     * The route and rules the menu chose for a hosted room or a master browse, with the round-end
     * vote pool a room created from it carries (validated by {@link VoteTrackPools#rejection}
     * before any room is created).
     */
    public record RoundSetup(String gameId, int zone, int act, String character,
                             String characterPolicy, String lockedCharacter, int windowSeconds,
                             List<String> voteTrackKeys) {
        public RoundSetup {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(character, "character");
            Objects.requireNonNull(characterPolicy, "characterPolicy");
            voteTrackKeys = List.copyOf(voteTrackKeys == null ? VoteTrackPools.forGame(gameId) : voteTrackKeys);
        }

        /** A setup whose rooms vote from the game's default pool. */
        public RoundSetup(String gameId, int zone, int act, String character,
                          String characterPolicy, String lockedCharacter, int windowSeconds) {
            this(gameId, zone, act, character, characterPolicy, lockedCharacter, windowSeconds, null);
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
    private java.util.function.BiFunction<String, String, PlayerAppearance> lookSource =
            (gameId, character) -> PlayerAppearance.STOCK;

    private State state = State.IDLE;
    private String status = "";
    private String notice;
    private boolean closed;
    private Attempt attempt;
    private PendingJoin pendingJoin;

    private JdkRaceHostServer host;
    private DirectRoomRegistration directRegistration;
    private MasterClient master;
    private String masterRoomId;
    private MasterLink masterLink;
    private RoundSetup browseSetup;
    private String browseFingerprint;
    private MultiplayerRaceCoordinator coordinator;
    private java.util.function.BooleanSupplier censorChat = () -> true;
    private java.util.function.BooleanSupplier disableChat = () -> false;
    private boolean hosting;
    private ControlMessage.RoundConfig roundConfig;
    private String character;
    private String lanInvite;
    private TimeAttackRuntime runtime;
    private RunHandle handle;
    private int launchedRound = -1;
    private RunLauncher lobbyLauncher;
    private LobbyRun lobbyRun;
    /** False once this room's lobby level failed; the lobby menu then shows on its own. */
    private boolean lobbyLevelEnabled = true;

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
        hostLan(setup, fingerprint, RoomAccessPolicy.PUBLIC, null);
    }

    public void hostLan(RoundSetup setup, String fingerprint, RoomAccessPolicy policy, RoomPassword password) {
        Objects.requireNonNull(setup, "setup");
        requireCredentials(policy, password);
        leave();
        Optional<String> rejected = VoteTrackPools.rejection(setup.gameId(), setup.voteTrackKeys(),
                setup.lockedCharacter());
        if (rejected.isPresent()) {
            fail("Cannot host: " + rejected.get());
            return;
        }
        int port = settings.hostPort();
        String configuredName = settings.displayName();
        String look = lookSource.apply(setup.gameId(), setup.character()).encode();
        String configuredMaster = settings.masterUrl();
        boolean localTrust = settings.masterTrustInsecure();
        String chatMode = settings.chatMode().name();
        start("Starting LAN room...", State.IDLE, password, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            boolean seekListing = !configuredMaster.isBlank();
            JdkRaceHostServer server = op.own((seekListing
                    ? JdkRaceHostServer.startTlsBrokerPending(port,
                    roomConfig(name, setup, fingerprint, policy, op.password, chatMode), me,
                    TrackValidationProfileSource.none())
                    : JdkRaceHostServer.startTls(port,
                    roomConfig(name, setup, fingerprint, policy, op.password, chatMode), me,
                    TrackValidationProfileSource.none())), JdkRaceHostServer::close);
            op.await(server.room().ready(), RaceClient.JOIN_TIMEOUT_MILLIS, ignored -> { });
            MasterClient registered = null;
            DirectRoomRegistration registration = null;
            if (!configuredMaster.isBlank()) {
                MasterClient candidate = null;
                try {
                    URI uri = masterEndpoint(configuredMaster, localTrust);
                    candidate = op.own(op.await(MasterClient.connect(uri, me, name, fingerprint,
                            localTrust ? insecureMasterSslContext() : null),
                            MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 2000, MasterClient::close), MasterClient::close);
                    registration = registerDirect(op, candidate, server, setup, fingerprint);
                    registered = candidate;
                } catch (Exception unavailable) {
                    if (candidate != null) candidate.close();
                    if (op.cancelled) throw new CancellationException("left while connecting");
                    server.close();
                    server = op.own(JdkRaceHostServer.startTls(port,
                            roomConfig(name, setup, fingerprint, policy, op.password, chatMode), me,
                            TrackValidationProfileSource.none()), JdkRaceHostServer::close);
                    op.await(server.room().ready(), RaceClient.JOIN_TIMEOUT_MILLIS, ignored -> { });
                }
            }
            String pin = server.tlsCertificateSha256();
            String invite = "HOST_IP:" + server.port() + "#" + DirectJoinAddress.shareCode(pin, me.fingerprint());
            RaceConnection client;
            if (registered != null && registration != null) {
                ControlMessage.RoomJoinResult prepared = op.await(
                        registered.requestJoin(registration.created().roomId()),
                        MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 1000, ignored -> { });
                try (RoomJoinOptions options = new RoomJoinOptions(op.password, server.room().instanceId(),
                        server.room().accessEpoch())) {
                    client = op.own(op.await(registration.joinOwner(prepared, me, name, look,
                            fingerprint, options), MasterClient.MASTER_REPLY_TIMEOUT_MILLIS
                            + RaceClient.JOIN_TIMEOUT_MILLIS + 2000, RaceConnection::close),
                            RaceConnection::close);
                }
            } else {
                try (RoomJoinOptions options = new RoomJoinOptions(op.password, server.room().instanceId(),
                        server.room().accessEpoch())) {
                    client = op.own(op.await(RaceClient.connect(
                            URI.create("wss://127.0.0.1:" + server.port() + "/race"), me, name, look, fingerprint,
                            pin, me.fingerprint(), options), RaceClient.JOIN_TIMEOUT_MILLIS + 2000, RaceClient::close),
                            RaceConnection::close);
                }
            }
            MasterClient ownerLink = registered;
            DirectRoomRegistration ownedRegistration = registration;
            ControlMessage.RoomCreated created = registration == null ? null : registration.created();
            JdkRaceHostServer activeServer = server;
            return () -> {
                host = activeServer;
                directRegistration = ownedRegistration;
                master = ownerLink;
                masterRoomId = created == null ? null : created.roomId();
                browseSetup = setup;
                browseFingerprint = fingerprint;
                startMasterLink(activeServer);
                enterLobby(client, true, roundConfigFor(setup), setup.character(), invite, name, look);
                if (ownerLink == null) notice = "No short code: share the LAN invite.";
            };
        });
    }

    public void joinLan(String invite, String character, String fingerprint) {
        leave();
        DirectJoinAddress address;
        try {
            address = DirectJoinAddress.parse(invite, settings.hostPort());
        } catch (IllegalArgumentException invalid) {
            fail("Invalid LAN invite");
            return;
        }
        connectManual(address, character, fingerprint, null, null, -1);
    }

    private void connectManual(DirectJoinAddress address, String chosenCharacter, String fingerprint,
                               RoomPassword password, String instanceId, long accessEpoch) {
        String configuredName = settings.displayName();
        start("Joining LAN room...", State.IDLE, password, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            try (RoomJoinOptions options = new RoomJoinOptions(op.password, instanceId, accessEpoch)) {
                RaceClient client;
                try {
                    client = op.own(op.await(RaceClient.connect(address.uri(), me, name, "", fingerprint,
                            address.certificateSha256(), address.hostFingerprint(), options),
                            RaceClient.JOIN_TIMEOUT_MILLIS + 2000, RaceClient::close), RaceClient::close);
                } catch (RoomAccessRequiredException required) {
                    if (op.password != null) throw required;
                    return () -> promptForPassword(new ManualJoin(address, chosenCharacter, fingerprint,
                            required.instanceId(), required.accessEpoch()));
                }
                return joined(client, false, chosenCharacter, DEFAULT_JOIN_WINDOW_SECONDS, null, name, "");
            }
        });
    }

    private void promptForPassword(PendingJoin join) {
        pendingJoin = join;
        state = State.PASSWORD_REQUIRED;
        status = "";
    }

    public PasswordPrompt passwordPrompt() {
        return pendingJoin == null ? null : pendingJoin.prompt();
    }

    public void submitPassword(RoomPassword password) {
        Objects.requireNonNull(password, "password");
        if (state != State.PASSWORD_REQUIRED || pendingJoin == null) return;
        PendingJoin joining = pendingJoin;
        pendingJoin = null;
        switch (joining) {
            case ManualJoin manual -> connectManual(manual.address(), manual.character(), manual.fingerprint(),
                    password, manual.instanceId(), manual.accessEpoch());
            case CodeJoin code -> connectCode(code, password);
        }
    }

    public void joinCode(String input) {
        if (state != State.BROWSING || master == null) return;
        String code;
        try { code = RoomInviteCode.parse(input).canonical(); }
        catch (IllegalArgumentException invalid) {
            notice = "Enter a valid six-character invite code";
            return;
        }
        MasterClient client = master;
        RoundSetup setup = browseSetup;
        String fingerprint = browseFingerprint;
        String configuredName = settings.displayName();
        String look = lookSource.apply(setup.gameId(), setup.character()).encode();
        start("Finding room...", State.BROWSING, op -> {
            ControlMessage.RoomJoinResult result = op.await(client.resolveInvite(code),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 1000, ignored -> { });
            if (result.accessPolicy().passwordRequired()) {
                return () -> promptForPassword(new CodeJoin(code, result));
            }
            return connectResolved(op, client, result, setup, fingerprint, configuredName, look);
        });
    }

    private void connectCode(CodeJoin code, RoomPassword password) {
        MasterClient client = master;
        RoundSetup setup = browseSetup;
        String fingerprint = browseFingerprint;
        String configuredName = settings.displayName();
        String look = lookSource.apply(setup.gameId(), setup.character()).encode();
        start("Joining room...", State.BROWSING, password, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            RaceConnection connection = op.own(op.await(client.joinInvite(code.code(), code.resolved(), me,
                    name, look, fingerprint, op.password),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS + 2000,
                    RaceConnection::close), RaceConnection::close);
            Runnable lobby = joined(connection, false, setup.character(), setup.windowSeconds(), null, name, look);
            return () -> {
                masterRoomId = code.resolved().roomId();
                startMasterLink(null);
                lobby.run();
            };
        });
    }

    private Runnable connectResolved(Attempt op, MasterClient client, ControlMessage.RoomJoinResult result,
                                     RoundSetup setup, String fingerprint, String configuredName, String look) throws Exception {
        PlayerIdentity me = identity();
        String name = displayName(me, configuredName);
        RaceConnection connection;
        try (RoomJoinOptions options = new RoomJoinOptions(null, result.instanceId(),
                result.instanceId() == null ? -1 : result.accessEpoch())) {
            connection = op.own(op.await(client.joinResolved(result, me, name, look, fingerprint, options),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS + 2000,
                    RaceConnection::close), RaceConnection::close);
        }
        Runnable lobby = joined(connection, false, setup.character(), setup.windowSeconds(), null, name, look);
        return () -> {
            masterRoomId = result.roomId();
            startMasterLink(null);
            lobby.run();
        };
    }

    private Runnable joined(RaceConnection connection, boolean owner, String chosenCharacter, int windowSeconds,
                             String invite, String name, String look) {
        ControlMessage.RoomDescriptor room = connection.joinAccepted().room();
        ControlMessage.RoundConfig round = new ControlMessage.RoundConfig(room.gameId(), room.zone(), room.act(),
                windowSeconds, room.characterPolicy(), room.lockedCharacter());
        String chosen = room.lockedCharacter() != null ? room.lockedCharacter() : chosenCharacter;
        return () -> enterLobby(connection, owner, round, chosen, invite, name, look);
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
            uri = masterEndpoint(configured, settings.masterTrustInsecure());
        } catch (IllegalArgumentException e) {
            fail("Invalid master server URL");
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
        String look = lookSource.apply(setup.gameId(), setup.character()).encode();
        start("Joining " + room.name() + "...", State.BROWSING, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            RaceConnection connection = op.own(op.await(client.joinRoom(room.roomId(), me, name, look, fingerprint),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS,
                    RaceConnection::close), RaceConnection::close);
            ControlMessage.RoomDescriptor joined = connection.joinAccepted().room();
            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig(joined.gameId(), joined.zone(),
                    joined.act(), setup.windowSeconds(), joined.characterPolicy(), joined.lockedCharacter());
            String chosen = joined.lockedCharacter() != null ? joined.lockedCharacter() : setup.character();
            return () -> {
                masterRoomId = room.roomId();
                startMasterLink(null);
                enterLobby(connection, false, round, chosen, null, name, look);
            };
        });
    }

    /**
     * Creates a master-listed room and joins it: {@code RELAY} rooms live on the master,
     * {@code DIRECT} rooms on the in-process host advertised with its certificate pin.
     */
    public void createMasterRoom(String routing) {
        createMasterRoom(routing, RoomAccessPolicy.PUBLIC, null);
    }

    public void createMasterRoom(String routing, RoomAccessPolicy policy, RoomPassword password) {
        requireCredentials(policy, password);
        if (state != State.BROWSING || master == null) return;
        MasterClient client = master;
        RoundSetup setup = browseSetup;
        String fingerprint = browseFingerprint;
        Optional<String> rejected = VoteTrackPools.rejection(setup.gameId(), setup.voteTrackKeys(), setup.lockedCharacter());
        if (rejected.isPresent()) {
            notice = "Cannot create the room: " + rejected.get();
            return;
        }
        boolean direct = "DIRECT".equals(routing);
        int port = settings.hostPort();
        String configuredName = settings.displayName();
        String look = lookSource.apply(setup.gameId(), setup.character()).encode();
        String chatMode = settings.chatMode().name();
        start("Creating room...", State.BROWSING, password, op -> {
            PlayerIdentity me = identity();
            String name = displayName(me, configuredName);
            JdkRaceHostServer server = !direct ? null : op.own(JdkRaceHostServer.startTlsBrokerPending(port,
                    roomConfig(name, setup, fingerprint, policy, op.password, chatMode), me,
                    TrackValidationProfileSource.none()),
                    JdkRaceHostServer::close);
            ControlMessage.RoomCreated created;
            DirectRoomRegistration registration;
            if (direct) {
                op.await(server.room().ready(), RaceClient.JOIN_TIMEOUT_MILLIS, ignored -> { });
                registration = registerDirect(op, client, server, setup, fingerprint);
                created = registration.created();
            } else {
                registration = null;
                created = op.await(client.createRelayRoom(descriptor(name, setup, policy, chatMode), fingerprint,
                        setup.voteTrackKeys(), op.password),
                        MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 1000, late -> client.leaveRoom(late.roomId()));
                op.own(created.roomId(), client::leaveRoom);
            }
            ControlMessage.RoomJoinResult resolved = op.await(client.requestJoin(created.roomId()),
                    MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + 1000, ignored -> { });
            RaceConnection connection;
            try (RoomJoinOptions options = new RoomJoinOptions(op.password, created.instanceId(), created.accessEpoch())) {
                CompletableFuture<RaceConnection> joining = direct
                        ? registration.joinOwner(resolved, me, name, look, fingerprint, options)
                        : client.joinResolved(resolved, me, name, look, fingerprint, options);
                connection = op.own(op.await(joining,
                        MasterClient.MASTER_REPLY_TIMEOUT_MILLIS + RaceClient.JOIN_TIMEOUT_MILLIS + 2000,
                        RaceConnection::close), RaceConnection::close);
            }
            String manual = server == null ? null : "HOST_IP:" + server.port() + "#"
                    + DirectJoinAddress.shareCode(server.tlsCertificateSha256(), me.fingerprint());
            return () -> {
                host = server;
                directRegistration = registration;
                masterRoomId = created.roomId();
                startMasterLink(server);
                enterLobby(connection, true, roundConfigFor(setup), setup.character(), manual, name, look);
            };
        });
    }

    private DirectRoomRegistration registerDirect(Attempt op, MasterClient client, JdkRaceHostServer server,
                                                   RoundSetup setup, String fingerprint) throws Exception {
        return op.own(op.await(DirectRoomRegistration.register(client, server, fingerprint, setup.voteTrackKeys()),
                MasterClient.MASTER_REPLY_TIMEOUT_MILLIS * 2 + 2000, DirectRoomRegistration::close),
                DirectRoomRegistration::close);
    }

    /**
     * Stops a connecting operation. From the browser this returns to the browser; otherwise the
     * session leaves.
     */
    public void cancel() {
        if (state == State.PASSWORD_REQUIRED && pendingJoin != null) {
            State fallback = pendingJoin.fallback();
            pendingJoin = null;
            if (fallback == State.BROWSING && master != null && master.isOpen()) state = State.BROWSING;
            else leave();
            return;
        }
        if (state != State.CONNECTING || attempt == null) return;
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
        pendingJoin = null;
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
        if (lobbyRun != null) {
            lobbyRun.leave();
        }
        boolean registeredDirect = directRegistration != null;
        if (registeredDirect) {
            directRegistration.close();
            directRegistration = null;
        }
        if (master != null) {
            MasterClient leaving = master;
            master = null;
            if (!registeredDirect && masterRoomId != null && leaving.isOpen()) {
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
        lobbyLevelEnabled = true;
        state = State.IDLE;
        status = "";
    }

    /** Scene exit, mod fault or engine shutdown: leaves and refuses new operations. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        leave();
        completions.clear();
        flushPendingPublications();
    }

    /** Flushes accepted settings on resume or owner exit; failures remain pending. */
    public void flushPendingPublications() {
        if (!settings.flushPending(storage)) notice = "Settings active - save pending";
    }

    // ── Engine-thread pump ───────────────────────────────────────────────

    /**
     * Runs once per scene tick before the views: adopts finished connecting work, pumps the
     * room between rounds, leaves on a lost or kicked connection, and launches each new round.
     */
    public void poll() {
        if (closed) return;
        if (settings.retryPending(storage)) notice = "Settings saved";
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
        if (lobbyRun != null) {
            // A round starting ends the lobby level; the round launches once the scene is back.
            if (roundPending()) {
                lobbyRun.leave();
            } else if (!coordinator.session().isLocalReady() && !lobbyRun.isMenuShown()) {
                lobbyRun.setMenuShown(true); // only a ready player may play behind a hidden menu
            }
            return;
        }
        maybeLaunchRound();
        if (state == State.LOBBY && !roundPending()) {
            maybeLaunchLobbyRun();
        }
    }

    /**
     * Supplies the launcher for the lobby level ({@code ctx.gameplay().launchBehind}); without
     * one the lobby menu shows on its own, as before lobby levels.
     */
    public void setLobbyLauncher(RunLauncher launcher) {
        lobbyLauncher = launcher;
    }

    /** True while the room's lobby level plays behind the lobby menu. */
    public boolean lobbyRunActive() {
        return lobbyRun != null;
    }

    /** Whether the lobby menu is shown over the lobby level (true without a lobby level). */
    public boolean lobbyMenuShown() {
        return lobbyRun == null || lobbyRun.isMenuShown();
    }

    /**
     * Shows or hides the lobby menu over the lobby level. Only a ready player may hide it: the
     * character then takes the controller and the menu gets no input until it is shown again.
     */
    public void setLobbyMenuShown(boolean shown) {
        if (lobbyRun == null || (!shown && !coordinator.session().isLocalReady())) {
            return;
        }
        lobbyRun.setMenuShown(shown);
    }

    /**
     * The scene is back from a run. A lobby level that ended hands over to the pending round,
     * relaunches (after a round, or an act restart that ended it) or, after a load failure or a
     * host fault, leaves the lobby menu on its own for this room. Returns false when the ended run
     * was not the lobby level.
     */
    public boolean lobbyRunEnded(com.openggf.mods.run.RunEndReason reason) {
        if (lobbyRun == null) {
            return false;
        }
        lobbyRun = null;
        if (reason == com.openggf.mods.run.RunEndReason.LOAD_FAILED
                || reason == com.openggf.mods.run.RunEndReason.ABORTED) {
            lobbyLevelEnabled = false;
            notice = reason == com.openggf.mods.run.RunEndReason.LOAD_FAILED
                    ? "The lobby level could not be loaded" : "The lobby level stopped";
        }
        return true;
    }

    /** A round has started that this session has not launched yet. */
    private boolean roundPending() {
        if (coordinator == null) {
            return false;
        }
        ClientRaceSession.Phase phase = coordinator.session().phase();
        return (phase == ClientRaceSession.Phase.COUNTDOWN || phase == ClientRaceSession.Phase.RUNNING)
                && coordinator.roundsStarted() != launchedRound;
    }

    private void maybeLaunchLobbyRun() {
        if (lobbyLauncher == null || !lobbyLevelEnabled || closed || roundConfig == null) {
            return;
        }
        LobbyAreas.LobbyArea area = LobbyAreas.forGame(roundConfig.gameId());
        if (area == null) {
            return;
        }
        String chosen = LobbyAreas.characterFor(area.gameId(),
                roundConfig.lockedCharacter() != null ? roundConfig.lockedCharacter() : character);
        LobbyRun run = new LobbyRun(coordinator, lookSource.apply(area.gameId(), chosen));
        try {
            run.attachHandle(lobbyLauncher.launch(LobbyAreas.runSpec(area, chosen), run));
            lobbyRun = run;
        } catch (RuntimeException e) {
            lobbyLevelEnabled = false;
            LOGGER.log(Level.WARNING, "Unable to launch the lobby level", e);
            notice = "The lobby level could not be started: " + rootMessage(e);
        }
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

    /** Live engine preferences, also read while a race rather than the scene owns updates. */
    public void setChatPreferenceSource(java.util.function.BooleanSupplier censor,
                                        java.util.function.BooleanSupplier disabled) {
        censorChat = Objects.requireNonNull(censor, "censor");
        disableChat = Objects.requireNonNull(disabled, "disabled");
        if (coordinator != null) coordinator.setChatPreferenceSource(censorChat, disableChat);
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
        ControlMessage.RaceLaunch launch = coordinator.session().launch();
        if (launch == null) {
            return;
        }
        ControlMessage.RoundConfig round = launch.config();
        launchedRound = coordinator.roundsStarted();
        String chosen = launch.effectiveCharacter();
        List<String> permitted = TimeAttackTrackCatalog.eligible(round.gameId(), round.zone(), round.act())
                .map(TimeAttackTrackCatalog.Track::characters).orElse(List.of());
        if (!permitted.isEmpty() && !permitted.contains(chosen)) {
            notice = chosen.toUpperCase() + " cannot race this act";
            return;
        }
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
                            String localCharacter, String invite, String joinedName, String joinedLook) {
        ClientRaceSession clientSession = new ClientRaceSession(clockMillis);
        try {
            clientSession.applyJoin(connection.joinAccepted());
        } catch (IllegalArgumentException invalid) {
            connection.close();
            leave();
            notice = "Invalid room launch";
            return;
        }
        if (localCharacter != null) {
            connection.sendControl(new ControlMessage.SelectCharacter(localCharacter));
        }
        // The Hello carried the look for the character known before joining; the room's
        // game and locked character can differ, so correct it once the lobby is known.
        String look = lookSource.apply(round.gameId(),
                localCharacter != null ? localCharacter : "sonic").encode();
        if (!look.equals(joinedLook)) {
            connection.sendControl(new ControlMessage.UpdateProfile(joinedName, look));
        }
        if (hostingRoom && settings.readyRule() != com.openggf.net.protocol.ReadyRules.DEFAULT_BY_SIZE) {
            // The owner's last choice; the host ignores it from anyone else.
            connection.sendControl(new ControlMessage.SetReadyRule(settings.readyRule().name()));
        }
        coordinator = new MultiplayerRaceCoordinator(RaceTransport.from(connection), clientSession, clockMillis,
                settings.masterUrl(), settings.masterTrustInsecure(), ghostStore);
        coordinator.setShowMinimap(settings.minimap());
        coordinator.setChatPreferenceSource(censorChat, disableChat);
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
    }

    /** The host room validates ghosts against the act the host actually loaded (engine thread). */
    private void applyTrackProfile(RunLevelStart start) {
        TrackValidationProfile profile = LiveLevelProfileFactory.fromLevelStart(start);
        JdkRaceHostServer server = host;
        if (profile == null || server == null) {
            return;
        }
        try {
            server.applyTrackValidationProfile(profile);
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
        return state == State.CONNECTING && attempt != null && attempt.fallback == State.BROWSING
                || state == State.PASSWORD_REQUIRED && pendingJoin != null && pendingJoin.fallback() == State.BROWSING;
    }

    public MultiplayerRaceCoordinator coordinator() {
        return coordinator;
    }

    public boolean isHosting() {
        return hosting;
    }

    /** The committed next round after a vote, or the lobby's current configuration. */
    public ControlMessage.RoundConfig roundConfig() {
        ControlMessage.RoundConfig committed = coordinator == null ? null
                : coordinator.session().nextRoundConfig();
        return committed == null ? roundConfig : committed;
    }

    public String character() {
        return character;
    }

    /** The LAN invite template for a LAN host ({@code HOST_IP:port#code}), or null. */
    public String lanInvite() {
        return lanInvite;
    }

    public String manualInviteFallback() {
        return directRegistration == null && (master == null || !master.isOpen())
                ? lanInvite : null;
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
        enterLobby(connection, hostingRoom, round, localCharacter, null, "Tester", "");
    }

    // ── Connecting operations ────────────────────────────────────────────

    private void start(String connectingStatus, State fallback, Operation operation) {
        start(connectingStatus, fallback, null, operation);
    }

    private void start(String connectingStatus, State fallback, RoomPassword password, Operation operation) {
        if (closed) {
            return;
        }
        cancelAttempt();
        Attempt current = new Attempt(fallback, password);
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
        String message = failureNotice(failure);
        LOGGER.warning("Time Attack multiplayer operation failed");
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
        private final RoomPassword password;
        private final List<Owned<?>> owned = new ArrayList<>();
        private volatile boolean cancelled;

        Attempt(State fallback, RoomPassword password) {
            this.fallback = fallback;
            this.password = copyPassword(password);
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
                    future.cancel(true);
                    throw new CancellationException("left while connecting");
                }
                try {
                    return future.get(AWAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException slice) {
                    if (System.nanoTime() >= deadline) {
                        future.thenAccept(discardLate);
                        future.cancel(true);
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
            if (password != null) password.close();
            return true;
        }

        void cancel() {
            List<Owned<?>> closing;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                if (password != null) password.close();
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

    /** The configured name in canonical form (captured on the engine thread), or the identity prefix. */
    private static String displayName(PlayerIdentity me, String configured) {
        String name = DisplayNames.sanitize(configured);
        return name.isEmpty() ? me.fingerprint().substring(0, 8) : name;
    }

    /** "NAME's room", cut at a code-point boundary to the descriptor's 64-byte limit. */
    static String roomName(String name) {
        String suffix = "'s room";
        String cut = name;
        while (cut.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + suffix.length() > MAX_ROOM_NAME_BYTES) {
            cut = cut.substring(0, cut.offsetByCodePoints(cut.length(), -1)).strip();
        }
        return cut + suffix;
    }

    /**
     * Supplies the look to announce for a game and character; the scene installs the player's
     * saved choices. Called on the engine thread.
     */
    public void setLookSource(java.util.function.BiFunction<String, String, PlayerAppearance> source) {
        lookSource = Objects.requireNonNull(source, "source");
    }

    private static RoomHostConfig roomConfig(String name, RoundSetup setup, String fingerprint,
                                             RoomAccessPolicy policy, RoomPassword password, String chatMode) {
        return new RoomHostConfig(roomName(name), setup.gameId(), setup.zone(), setup.act(),
                setup.characterPolicy(), setup.lockedCharacter(), Protocol.MAX_PLAYERS_DIRECT, fingerprint,
                setup.voteTrackKeys(), false, policy, password, null, chatMode);
    }

    private static ControlMessage.RoomDescriptor descriptor(String name, RoundSetup setup, RoomAccessPolicy policy,
                                                             String chatMode) {
        return new ControlMessage.RoomDescriptor(roomName(name), setup.gameId(), setup.zone(), setup.act(),
                setup.characterPolicy(), setup.lockedCharacter(), MAX_ROOM_PLAYERS, false, policy, chatMode);
    }

    private static void requireCredentials(RoomAccessPolicy policy, RoomPassword password) {
        Objects.requireNonNull(policy, "policy");
        if (policy.passwordRequired() != (password != null)) throw new IllegalArgumentException("invalid room credentials");
    }

    private static RoomPassword copyPassword(RoomPassword password) {
        if (password == null) return null;
        char[] value = password.copyCharacters();
        try { return new RoomPassword(value); }
        finally { java.util.Arrays.fill(value, '\0'); }
    }

    private static URI masterEndpoint(String configured, boolean localTrust) {
        URI uri = NetworkEndpoints.webSocket(URI.create(configured.trim()));
        if (localTrust && !NetworkEndpoints.literalLoopback(uri.getHost()))
            throw new IllegalArgumentException("insecure TLS trust is local-development only");
        return uri;
    }

    private static String failureNotice(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof RoomInviteRejectedException limited && limited.retryAfterMillis() > 0) {
            return "Invite unavailable. Try again in " + Math.max(1, (limited.retryAfterMillis() + 999) / 1000) + " seconds.";
        }
        if (cause instanceof TimeoutException) return "Connection timed out";
        if (cause instanceof RaceClient.JoinRejectedException || cause instanceof RoomAccessRequiredException)
            return "Room access unavailable. Check the password or request a fresh invitation.";
        return "Connection unavailable. Check the invite and connection settings.";
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
        private final AtomicBoolean heartbeatPending = new AtomicBoolean();
        private volatile boolean stopped;
        private int advertisedZone;
        private int advertisedAct;
        private String advertisedChatMode;

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
                            LOGGER.warning("Master connection closed");
                        }
                    }
                    if (server != null && ++ticks % HEARTBEAT_TICKS == 0) {
                        queueHeartbeat();
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        private void queueHeartbeat() {
            if (!heartbeatPending.compareAndSet(false, true)) return;
            try {
                server.executeControl(() -> {
                    try { heartbeatOnRoomThread(); }
                    finally { heartbeatPending.set(false); }
                });
            } catch (IllegalStateException unavailable) {
                heartbeatPending.set(false);
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
            if (!Objects.equals(current.chatMode(), advertisedChatMode)) {
                client.sendControl(new ControlMessage.RoomChatModeUpdate(roomId, current.chatMode()));
                advertisedChatMode = current.chatMode();
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
