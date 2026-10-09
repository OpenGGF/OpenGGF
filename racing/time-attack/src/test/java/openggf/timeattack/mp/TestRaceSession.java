package openggf.timeattack.mp;

import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunLevelStart;
import com.openggf.game.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import openggf.racing.client.MasterClient;
import openggf.racing.client.RaceClient;
import openggf.racing.client.RaceConnection;
import openggf.racing.host.jdk.JdkRaceHostServer;
import openggf.racing.hub.GhostHub;
import openggf.racing.hub.TrackValidationProfile;
import openggf.racing.identity.PlayerIdentity;
import openggf.racing.protocol.ControlMessage;
import openggf.racing.server.master.MasterServer;
import openggf.racing.server.master.TestMasterServer;
import openggf.timeattack.GhostStore;
import openggf.timeattack.MemoryModStorage;
import openggf.timeattack.TimeAttackRuntime;
import openggf.timeattack.TimeAttackSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The mod-side multiplayer session over real loopback sockets: the in-process JDK room host,
 * LAN invites, the master server, round launch through the scene's run launcher, and cleanup.
 */
@Timeout(120)
class TestRaceSession {
    private static final String FP = "0.8:cafe1234";
    private static final RaceSession.RoundSetup SETUP =
            new RaceSession.RoundSetup("s2", 0, 0, "sonic", "OPEN", null, 60);

    @TempDir
    Path dir;

    private final List<RaceSession> sessions = new ArrayList<>();
    private MasterServer masterServer;

    @AfterEach
    void closeEverything() {
        sessions.forEach(RaceSession::close);
        if (masterServer != null) {
            masterServer.close();
        }
    }

    /** Records launches like the scene's {@code ctx.gameplay().launch}. */
    static final class Launches implements RaceSession.RunLauncher {
        final List<RunSpec> specs = new ArrayList<>();
        final List<RunHost> hosts = new ArrayList<>();
        final AtomicInteger leaves = new AtomicInteger();
        final AtomicBoolean active = new AtomicBoolean();
        RuntimeException failure;

        @Override
        public RunHandle launch(RunSpec spec, RunHost host) {
            if (failure != null) {
                throw failure;
            }
            specs.add(spec);
            hosts.add(host);
            active.set(true);
            return new RunHandle() {
                @Override public void retry() { }
                @Override public void leave() { leaves.incrementAndGet(); active.set(false); }
                @Override public void spectate(boolean on, int dx, int dy) { }
                @Override public boolean isActive() { return active.get(); }
            };
        }
    }

    private RaceSession session(String name, TimeAttackSettings settings, MemoryModStorage storage,
                                Launches launches) {
        return session(name, settings, storage, launches, System::currentTimeMillis);
    }

    private RaceSession session(String name, TimeAttackSettings settings, MemoryModStorage storage,
                                Launches launches, java.util.function.LongSupplier clock) {
        Path identity = dir.resolve(name + "-identity");
        GhostStore ghosts = new GhostStore(dir.resolve(name + "-ghosts"));
        RaceSession session = new RaceSession(identity, ghosts, settings, storage, launches,
                () -> new TimeAttackRuntime(ghosts, identity, () -> false), clock);
        sessions.add(session);
        return session;
    }

    private static TimeAttackSettings settings(int port) {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setHostPort(port);
        return settings;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean portIsFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void pollUntil(String what, BooleanSupplier condition, RaceSession... polled)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            for (RaceSession session : polled) {
                session.poll();
            }
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("timed out waiting for " + what);
    }

    private static boolean roomThreadAlive(int port) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("race-room-host-" + port) && thread.isAlive());
    }

    @Test
    void hostAndJoinOverLanLaunchTheRoundAndLeaveClosesEverything() throws Exception {
        int port = freePort();
        MemoryModStorage hostStorage = new MemoryModStorage();
        Launches hostLaunches = new Launches();
        RaceSession host = session("host", settings(port), hostStorage, hostLaunches);
        host.hostLan(SETUP, FP);
        assertEquals(RaceSession.State.CONNECTING, host.state());
        pollUntil("the host lobby", () -> host.state() == RaceSession.State.LOBBY, host);
        assertTrue(host.isHosting());
        String invite = host.lanInvite();
        assertTrue(invite.startsWith("HOST_IP:" + port + "#"), invite);
        assertEquals(invite + "\n", hostStorage.read(RaceSession.INVITE_FILE).orElseThrow());
        assertNotNull(host.hostServer());

        TimeAttackSettings guestSettings = settings(port);
        MemoryModStorage guestStorage = new MemoryModStorage();
        Launches guestLaunches = new Launches();
        RaceSession guest = session("guest", guestSettings, guestStorage, guestLaunches);
        String guestInvite = invite.replace("HOST_IP", "127.0.0.1");
        guest.joinLan(guestInvite, "tails", FP);
        pollUntil("both players in the lobby", () -> guest.state() == RaceSession.State.LOBBY
                && host.coordinator().session().players().size() == 2, host, guest);
        assertFalse(guest.isHosting());
        assertNull(guest.lanInvite());
        assertEquals(guestInvite, guestSettings.lastJoinAddress());
        assertTrue(guestStorage.read(TimeAttackSettings.FILE_NAME).orElseThrow()
                .contains("lastJoinAddress=" + guestInvite));

        host.coordinator().sendRoundConfigure(host.roundConfig());
        pollUntil("both players racing", () -> host.state() == RaceSession.State.RACING
                && guest.state() == RaceSession.State.RACING, host, guest);
        assertEquals(1, hostLaunches.specs.size());
        assertEquals(1, guestLaunches.specs.size());
        RunSpec hostSpec = hostLaunches.specs.get(0);
        assertEquals(new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct()), hostSpec);
        assertEquals("tails", guestLaunches.specs.get(0).character());
        TimeAttackRuntime hostRun = assertInstanceOf(TimeAttackRuntime.class, hostLaunches.hosts.get(0));
        assertSame(hostRun, host.activeRuntime());
        assertTrue(host.coordinator().isRuntimeAttached());

        // The host room validates against the act the host actually loaded.
        hostRun.onLevelReady(new RunLevelStart(hostSpec, FP, false, 4096, 1024));
        JdkRaceHostServer server = host.hostServer();
        CompletableFuture<Object> applied = new CompletableFuture<>();
        server.execute(() -> applied.complete(currentProfile(server.room().hub())));
        assertEquals(new TrackValidationProfile(4096, 1024,
                TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME, TrackValidationProfile.FRAME_RATE_CAP),
                applied.get(5, TimeUnit.SECONDS));

        host.runEnded();
        guest.runEnded();
        assertEquals(RaceSession.State.LOBBY, host.state());
        assertFalse(host.coordinator().isRuntimeAttached());
        for (int i = 0; i < 20; i++) {
            host.poll();
            guest.poll();
            Thread.sleep(5);
        }
        assertEquals(1, hostLaunches.specs.size(), "a round launches once");

        guest.leave();
        assertEquals(RaceSession.State.IDLE, guest.state());
        assertNull(guest.coordinator());
        pollUntil("the host to see the guest leave",
                () -> host.coordinator().session().players().size() == 1, host);

        host.leave();
        assertEquals(RaceSession.State.IDLE, host.state());
        assertNull(host.hostServer());
        assertNull(host.coordinator());
        assertTrue(portIsFree(port), "the room port is released");
        pollUntil("the room thread to stop", () -> !roomThreadAlive(port));
    }

    @Test
    void wrongDeterminismFingerprintIsRejectedWithItsReason() throws Exception {
        int port = freePort();
        RaceSession host = session("host", settings(port), new MemoryModStorage(), new Launches());
        host.hostLan(SETUP, FP);
        pollUntil("the host lobby", () -> host.state() == RaceSession.State.LOBBY, host);
        RaceSession guest = session("guest", settings(port), new MemoryModStorage(), new Launches());
        guest.joinLan(host.lanInvite().replace("HOST_IP", "127.0.0.1"), "sonic", "0.8:deadbeef");
        pollUntil("the join to fail", () -> guest.state() == RaceSession.State.IDLE, guest);
        String notice = guest.consumeNotice();
        assertTrue(notice.contains("determinism fingerprint mismatch"), notice);
        assertNull(guest.coordinator());
        assertNull(guest.consumeNotice(), "a notice is shown once");
    }

    @Test
    void leavingWhileConnectingClosesTheHalfBuiltRoom() throws Exception {
        int port = freePort();
        RaceSession host = session("host", settings(port), new MemoryModStorage(), new Launches());
        host.hostLan(SETUP, FP);
        host.leave();
        assertEquals(RaceSession.State.IDLE, host.state());
        pollUntil("the abandoned room to release its port", () -> host.connectWorkersRunning() == 0
                && portIsFree(port) && !roomThreadAlive(port), host);
        for (int i = 0; i < 50; i++) {
            host.poll();
            Thread.sleep(10);
        }
        assertEquals(RaceSession.State.IDLE, host.state());
        assertNull(host.coordinator());
        assertNull(host.hostServer());
        assertTrue(portIsFree(port));
    }

    @Test
    void cancellingAConnectReturnsToIdle() throws Exception {
        int port = freePort();
        RaceSession host = session("host", settings(port), new MemoryModStorage(), new Launches());
        host.hostLan(SETUP, FP);
        host.cancel();
        assertEquals(RaceSession.State.IDLE, host.state());
        pollUntil("the cancelled room to release its port", () -> host.connectWorkersRunning() == 0
                && portIsFree(port) && !roomThreadAlive(port), host);
        assertEquals(RaceSession.State.IDLE, host.state());
        assertNull(host.hostServer());
    }

    @Test
    void invalidInviteAndMissingMasterUrlFailWithoutConnecting() {
        RaceSession session = session("solo", settings(27888), new MemoryModStorage(), new Launches());
        session.joinLan("192.168.1.5:27888", "sonic", FP);
        assertEquals(RaceSession.State.IDLE, session.state());
        assertTrue(session.consumeNotice().startsWith("Invalid LAN invite"));
        session.browse(SETUP, FP);
        assertEquals(RaceSession.State.IDLE, session.state());
        assertTrue(session.consumeNotice().contains("master server URL"));
    }

    @Test
    void occupiedHostPortFailsWithTheReason() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            RaceSession host = session("host", settings(occupied.getLocalPort()), new MemoryModStorage(),
                    new Launches());
            host.hostLan(SETUP, FP);
            pollUntil("hosting to fail", () -> host.state() == RaceSession.State.IDLE, host);
            assertTrue(host.consumeNotice().startsWith("Failed:"));
            assertNull(host.hostServer());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "RELAY"})
    void masterBrowseCreateAndJoinThenLeave(String routing) throws Exception {
        masterServer = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
        String url = "ws://127.0.0.1:" + masterServer.port() + "/master";
        String hostFingerprint = PlayerIdentity.loadOrCreate(dir.resolve("host-identity")).fingerprint();
        CountDownLatch established = new CountDownLatch(1);
        masterServer.execute(() -> {
            masterServer.establishForTest(hostFingerprint);
            established.countDown();
        });
        assertTrue(established.await(5, TimeUnit.SECONDS));

        TimeAttackSettings hostSettings = settings(freePort());
        hostSettings.setMasterUrl(url);
        RaceSession host = session("host", hostSettings, new MemoryModStorage(), new Launches());
        host.browse(SETUP, FP);
        pollUntil("the host browser", () -> host.state() == RaceSession.State.BROWSING, host);
        assertEquals("s2", host.browseGameId());
        assertNotNull(host.roomDirectory());
        host.createMasterRoom(routing);
        assertTrue(host.connectingFromBrowser());
        pollUntil("the host lobby", () -> host.state() == RaceSession.State.LOBBY, host);
        assertTrue(host.isHosting());
        assertEquals("DIRECT".equals(routing), host.hostServer() != null);
        assertTrue(host.masterLinkRunning());

        TimeAttackSettings guestSettings = settings(freePort());
        guestSettings.setMasterUrl(url);
        RaceSession guest = session("guest", guestSettings, new MemoryModStorage(), new Launches());
        guest.browse(SETUP, FP);
        pollUntil("the guest browser", () -> guest.state() == RaceSession.State.BROWSING, guest);
        List<ControlMessage.RoomSummary> rooms = guest.roomDirectory().listRooms("s2", 0)
                .get(10, TimeUnit.SECONDS).rooms();
        assertEquals(1, rooms.size());
        assertEquals(routing, rooms.get(0).routing());
        guest.joinMasterRoom(rooms.get(0));
        pollUntil("both players in the lobby", () -> guest.state() == RaceSession.State.LOBBY
                && host.coordinator().session().players().size() == 2, host, guest);
        assertTrue(guest.masterLinkRunning());

        guest.leave();
        assertNull(guest.masterClient());
        host.leave();
        assertNull(host.masterClient());
        assertNull(host.hostServer());
        assertFalse(host.masterLinkRunning());
        MasterClient observer = MasterClient.connect(URI.create(url),
                PlayerIdentity.loadOrCreate(dir.resolve("observer")), "OBS", FP, null).get(15, TimeUnit.SECONDS);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!observer.listRooms("s2", 0).get(10, TimeUnit.SECONDS).rooms().isEmpty()) {
                assertTrue(System.nanoTime() < deadline, "the master still lists the left room");
                Thread.sleep(100);
            }
        } finally {
            observer.close();
        }
    }

    @Test
    void failedBrowserJoinReturnsToTheBrowserWithTheReason() throws Exception {
        masterServer = MasterServer.start(TestMasterServer.testConfig(), dir.resolve("master"));
        TimeAttackSettings settings = settings(freePort());
        settings.setMasterUrl("ws://127.0.0.1:" + masterServer.port() + "/master");
        RaceSession guest = session("guest", settings, new MemoryModStorage(), new Launches());
        guest.browse(SETUP, FP);
        pollUntil("the browser", () -> guest.state() == RaceSession.State.BROWSING, guest);
        guest.joinMasterRoom(new ControlMessage.RoomSummary("missing", "Missing", "s2", 0, 0, "OPEN", 1, 8,
                "RELAY", false));
        pollUntil("the join to fail", () -> guest.state() == RaceSession.State.BROWSING, guest);
        assertTrue(guest.consumeNotice().startsWith("Failed:"));
        assertNotNull(guest.masterClient());
        assertTrue(guest.masterClient().isOpen());
    }

    // ── Fake-transport lobby behaviour ───────────────────────────────────

    /** A room connection whose inbound events the test feeds. */
    static class FakeConnection implements RaceConnection {
        final ConcurrentLinkedQueue<RaceClient.InboundEvent> inbound = new ConcurrentLinkedQueue<>();
        final List<ControlMessage> sent = new ArrayList<>();
        boolean open = true;

        void control(ControlMessage message) {
            inbound.add(new RaceClient.Control(message));
        }

        @Override public List<RaceClient.InboundEvent> drainInbound() {
            List<RaceClient.InboundEvent> events = new ArrayList<>();
            RaceClient.InboundEvent event;
            while ((event = inbound.poll()) != null) events.add(event);
            return events;
        }
        @Override public void sendControl(ControlMessage message) { sent.add(message); }
        @Override public void sendBinary(byte[] data) { }
        @Override public int playerSlot() { return 1; }
        @Override public String sessionToken() { return "token"; }
        @Override public ControlMessage.JoinAccepted joinAccepted() {
            return new ControlMessage.JoinAccepted("token", 1, new ControlMessage.RoomDescriptor("Room", "s2", 0, 0,
                    "OPEN", null, 8, false), null);
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }

    private static final ControlMessage.RoundConfig ROUND =
            new ControlMessage.RoundConfig("s2", 0, 0, 60, "OPEN", null);

    @Test
    void roundsLaunchOncePerRoundStartAndUseTheLockedCharacter() {
        AtomicLong clock = new AtomicLong();
        Launches launches = new Launches();
        RaceSession session = session("guest", settings(27888), new MemoryModStorage(), launches, clock::get);
        FakeConnection connection = new FakeConnection();
        session.enterLobbyForTest(connection, false, ROUND, "tails");
        assertTrue(connection.sent.contains(new ControlMessage.SelectCharacter("tails")));
        session.poll();
        assertTrue(launches.specs.isEmpty());

        connection.control(new ControlMessage.RoundStart(ROUND, 1_000, 100_000));
        session.poll();
        assertEquals(RaceSession.State.RACING, session.state());
        assertEquals(List.of("tails"), launches.specs.stream().map(RunSpec::character).toList());

        session.runEnded();
        clock.set(2_000); // RUNNING
        session.poll();
        assertEquals(RaceSession.State.LOBBY, session.state());
        assertEquals(1, launches.specs.size(), "returning early does not relaunch the same round");

        connection.control(new ControlMessage.RoundEnd(List.of()));
        session.poll();
        connection.control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s2", 1, 0, 60, "LOCKED", "sonic"), 3_000, 100_000));
        session.poll();
        assertEquals(2, launches.specs.size());
        assertEquals(new RunSpec("s2", 1, 0, "sonic", GameplayRunPolicy.isolatedAct()), launches.specs.get(1));
    }

    @Test
    void aRoundAlreadyRunningOnJoinLaunchesOnce() {
        Launches launches = new Launches();
        RaceSession session = session("guest", settings(27888), new MemoryModStorage(), launches, () -> 5_000);
        FakeConnection connection = new FakeConnection() {
            @Override public ControlMessage.JoinAccepted joinAccepted() {
                return new ControlMessage.JoinAccepted("token", 1, new ControlMessage.RoomDescriptor("Room", "s2",
                        0, 0, "OPEN", null, 8, false),
                        new ControlMessage.RoundSnapshot("RUNNING", ROUND, 1_000, 100_000, List.of()));
            }
        };
        session.enterLobbyForTest(connection, false, ROUND, "sonic");
        session.poll();
        session.runEnded();
        session.poll();
        assertEquals(1, launches.specs.size());
    }

    @Test
    void aFailedLaunchStaysInTheLobbyWithTheReason() {
        Launches launches = new Launches();
        launches.failure = new IllegalStateException("a run is already active");
        RaceSession session = session("guest", settings(27888), new MemoryModStorage(), launches, () -> 0);
        FakeConnection connection = new FakeConnection();
        session.enterLobbyForTest(connection, false, ROUND, "sonic");
        connection.control(new ControlMessage.RoundStart(ROUND, 1_000, 100_000));
        session.poll();
        assertEquals(RaceSession.State.LOBBY, session.state());
        assertFalse(session.coordinator().isRuntimeAttached());
        assertEquals("Unable to start the round: a run is already active", session.consumeNotice());
    }

    @Test
    void lostOrKickedConnectionsLeaveWithANoticeAndCloseTheTransport() {
        RaceSession session = session("guest", settings(27888), new MemoryModStorage(), new Launches(), () -> 0);
        FakeConnection lost = new FakeConnection();
        session.enterLobbyForTest(lost, false, ROUND, "sonic");
        lost.inbound.add(new RaceClient.Disconnected("reset"));
        session.poll();
        assertEquals(RaceSession.State.IDLE, session.state());
        assertFalse(lost.open);
        assertEquals("Connection to the room was lost", session.consumeNotice());

        FakeConnection kicked = new FakeConnection();
        session.enterLobbyForTest(kicked, false, ROUND, "sonic");
        kicked.control(new ControlMessage.Kick("spam"));
        session.poll();
        assertEquals(RaceSession.State.IDLE, session.state());
        assertFalse(kicked.open);
        assertEquals("Kicked from the room: spam", session.consumeNotice());
    }

    @Test
    void closingWhileRacingEndsTheRunAndRefusesNewWork() {
        Launches launches = new Launches();
        RaceSession session = session("guest", settings(27888), new MemoryModStorage(), launches, () -> 0);
        FakeConnection connection = new FakeConnection();
        session.enterLobbyForTest(connection, false, ROUND, "sonic");
        connection.control(new ControlMessage.RoundStart(ROUND, 1_000, 100_000));
        session.poll();
        assertEquals(RaceSession.State.RACING, session.state());
        session.close();
        assertEquals(1, launches.leaves.get());
        assertFalse(connection.open);
        assertEquals(RaceSession.State.IDLE, session.state());
        session.hostLan(SETUP, FP);
        assertEquals(RaceSession.State.IDLE, session.state(), "a closed session starts nothing");
    }

    private static Object currentProfile(GhostHub hub) {
        try {
            Field field = GhostHub.class.getDeclaredField("currentProfile");
            field.setAccessible(true);
            return field.get(hub);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
