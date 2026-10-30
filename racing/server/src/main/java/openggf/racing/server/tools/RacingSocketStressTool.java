package openggf.racing.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.MasterClient;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import openggf.racing.server.host.RaceHostServer;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.MasterServer;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Real local socket capacity/soak harness, originating in the 2026-10 racing stress task.
 * Inputs are synthetic cosmetic poses; no ROM, engine simulation or verified input recording runs.
 * Production clients, authentication, room actors and transports own all protocol behavior.
 */
public final class RacingSocketStressTool {
    /** Optional diagnostic boundary; disabled unless an external JFR recording enables it. */
    @jdk.jfr.Name("openggf.RacingStressWindow")
    @jdk.jfr.Label("Measured socket traffic through accepted finishes and hidden casual standings")
    @jdk.jfr.StackTrace(false)
    public static final class TrafficWindow extends jdk.jfr.Event {
        public String transport;
        public int players;
    }
    public enum Transport { RELAY, JDK, NETTY }
    public enum Mix { NORMAL, MIXED }

    public record Config(int players, int durationSeconds, int cycles, long seed,
                         Mix mix, Transport transport, int timeoutSeconds) {
        public Config {
            if (mix == null || transport == null || players < 2
                    || players > (transport == Transport.RELAY
                    ? Protocol.MAX_PLAYERS_RELAY : Protocol.MAX_PLAYERS_DIRECT)
                    || durationSeconds < 1 || durationSeconds > 600
                    || cycles < 1 || cycles > 100
                    || timeoutSeconds < 5 || timeoutSeconds > 120) {
                throw new IllegalArgumentException("players 2..transport cap, duration 1..600, "
                        + "cycles 1..100 and timeout 5..120 are required");
            }
        }

        public static Config smoke() {
            return new Config(4, 1, 1, 0x475746L, Mix.MIXED, Transport.RELAY, 15);
        }
    }

    /** All byte counts are payload/TCP observations, never server queue estimates. */
    public record Report(Config config, boolean success, int completedCycles,
                         int requestedPlayers, int admittedPlayers, int peakAdmittedConcurrency,
                         int expectedFinishes, int finishedPlayers, int expectedRejections,
                         int expectedSanctions, int unexpectedFailures, List<String> failures,
                         long sentControlMessages, long receivedControlMessages,
                         long sentBinaryMessages, long sentGhostFrames,
                         long receivedBinaryMessages, long receivedGhostEntries,
                         long receivedGhostFrames, long receivedRosters,
                         long measuredTcpUpstreamBytes, long measuredTcpDownstreamBytes,
                         long pingSamples, double pingP50Millis, double pingP95Millis,
                         double pingP99Millis, double measuredElapsedSeconds,
                         double wholeProcessCpuSeconds, long wholeProcessGcCollections,
                         long wholeProcessGcMillis, long wholeProcessPeakUsedHeapBytes,
                         int reconnects, int roundResets, int removedRooms, int verifiedShutdowns,
                         double maxGeneratorLatenessMillis) {
        public Report { failures = List.copyOf(failures); }
    }

    private static final String FP = "socket-stress:synthetic-v1";
    private static final int FPS = 60;
    private static final int MAX_PING_SAMPLES = 8192;

    private RacingSocketStressTool() { }

    /** Runs only owned loopback endpoints and returns contract failures as report data. */
    public static Report run(Config config, Path dataDir) throws InterruptedException {
        if (config == null || dataDir == null) throw new IllegalArgumentException("config/data directory required");
        Totals totals = new Totals(config);
        Path runDir = null;
        try {
            Files.createDirectories(dataDir);
            runDir = Files.createTempDirectory(dataDir, "socket-stress-");
            for (int cycle = 0; cycle < config.cycles(); cycle++) {
                try (Cycle workload = new Cycle(config, totals, runDir.resolve("cycle-" + cycle))) {
                    try {
                        workload.run();
                        totals.completedCycles++;
                    } catch (InterruptedException interrupted) {
                        throw interrupted;
                    } catch (Exception failure) {
                        throw new IllegalStateException("cycle=" + cycle + ", stage=" + workload.stage
                                + ", seed=" + config.seed() + ": " + failure.getMessage(), failure);
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception | AssertionError failure) {
            totals.fail(failure);
        } finally {
            if (runDir != null) {
                try (var paths = Files.walk(runDir)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException failure) {
                    totals.fail(new IOException("owned run directory cleanup failed", failure));
                }
            }
        }
        return totals.report();
    }

    private record MemberIdentity(String fingerprint, String displayName) { }

    private static final class Peer {
        final int index;
        final PlayerIdentity identity;
        final MasterClient master;
        final RaceConnection connection;
        final String name;
        final GhostStreamPublisher publisher;
        final Map<Long, Long> pendingPings = new HashMap<>();
        Set<String> roomMembers = Set.of();
        Map<Integer, MemberIdentity> roomRoster = Map.of();
        boolean intentionallyClosed;
        boolean disconnected;
        String disconnectReason;
        boolean roundStarted;
        boolean roundEnded;
        boolean started;
        boolean finishSent;
        boolean finishObserved;
        int generatedFrames;
        int goalFrames;
        int expectedTime;
        int observedGhostFrames;
        long startAt;
        long lastPingAt;
        long countdownEndsAt;

        Peer(int index, PlayerIdentity identity, MasterClient master,
             RaceConnection connection, Totals totals) {
            this.index = index;
            this.identity = identity;
            this.master = master;
            this.connection = connection;
            name = "P" + index;
            publisher = new GhostStreamPublisher(packet -> {
                check(connection.isOpen(), "closed peer sent ghost data: " + name);
                if (totals.measuring) {
                    totals.sentBinary++;
                    totals.sentGhostFrames += GhostPackets.decodeFrames(packet).frameCount();
                }
                connection.sendBinary(packet);
            });
        }

        void control(ControlMessage message, Totals totals) {
            check(connection.isOpen(), "closed peer sent control: " + name);
            if (totals.measuring) totals.sentControl++;
            connection.sendControl(message);
        }
    }

    private static final class Cycle implements AutoCloseable {
        final Config config;
        final Totals totals;
        final Path dir;
        final List<Peer> peers = new ArrayList<>();
        final List<MasterClient> masters = new ArrayList<>();
        final List<RaceConnection> connections = new ArrayList<>();
        final Map<Integer, Integer> ownerBySlot = new HashMap<>();
        MasterServer masterServer;
        RaceRoomHost directServer;
        LoopbackSourceProxy proxy;
        URI endpoint;
        String roomId;
        int serverPort;
        boolean closed;
        String stage = "start";

        Cycle(Config config, Totals totals, Path dir) {
            this.config = config;
            this.totals = totals;
            this.dir = dir;
        }

        void run() throws Exception {
            Files.createDirectories(dir);
            PlayerIdentity hostIdentity = identity(0);
            if (config.transport() == Transport.RELAY) {
                masterServer = MasterServer.start(masterConfig(), dir.resolve("master"));
                serverPort = masterServer.port();
            } else {
                RoomHostConfig roomConfig = new RoomHostConfig("Socket stress", "s3k", 0, 0,
                        "OPEN", null, config.players(), FP);
                directServer = config.transport() == Transport.JDK
                        ? JdkRaceHostServer.start(0, roomConfig, hostIdentity, TrackValidationProfileSource.none())
                        : RaceHostServer.start(0, roomConfig, hostIdentity, TrackValidationProfileSource.none());
                serverPort = directServer.port();
            }
            proxy = new LoopbackSourceProxy(serverPort, config.players() + 8);
            endpoint = URI.create("ws://127.0.0.1:" + proxy.port()
                    + (masterServer != null ? "/master" : "/race"));
            stage = "admission";
            for (int index = 0; index < config.players(); index++) {
                Peer peer = connectPeer(index, index == 0 ? hostIdentity : identity(index));
                totals.admitted++;
                totals.peakConcurrency = Math.max(totals.peakConcurrency, peers.size() + 1);
                register(peer);
            }
            await(this::membershipMatches, "every peer full room state");
            assertMembership();
            stage = "capacity plus one";
            rejectOverflow();
            if (config.mix() == Mix.MIXED) {
                stage = "hostile protocol isolation and capacity recovery";
                sanctionAndRecover();
            }
            stage = "control warmup";
            warmup();
            stage = "countdown";
            // Load peers never ready up; nobody waits under AUTO.
            peers.getFirst().control(new ControlMessage.SetReadyRule("AUTO"), totals);
            peers.getFirst().control(new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                    "s3k", 0, 0, config.durationSeconds() + 2, "OPEN", null)), totals);
            await(() -> peers.stream().allMatch(peer -> peer.roundStarted), "round start for every peer");
            long countdown = peers.getFirst().countdownEndsAt;
            checkRoundStartAgreement();
            await(() -> System.currentTimeMillis() >= countdown, "wall-clock countdown");
            stage = "measured traffic";
            traffic();
            stage = "round end";
            await(() -> peers.stream().allMatch(peer -> peer.roundEnded), "round end for every peer");
            // Each peer's accepted rank/standing is required; top-ten broadcasts alone are insufficient.
            check(peers.stream().allMatch(peer -> peer.finishObserved), "missing accepted finish");
            stage = "recovery";
            reconnectAfterRound();
            stage = "shutdown";
            verifyShutdown();
        }

        private PlayerIdentity identity(int index) throws Exception {
            return PlayerIdentity.loadOrCreate(dir.resolve("identity-" + index));
        }

        private MasterClient master(PlayerIdentity identity, String name) throws Exception {
            MasterClient client = future(MasterClient.connect(endpoint, identity, name, FP, null));
            masters.add(client);
            // All capacity identities are established: NEW identities have a deliberate high-fill gate.
            CompletableFuture<Void> promoted = new CompletableFuture<>();
            masterServer.execute(() -> {
                try {
                    masterServer.establishForTest(identity.fingerprint());
                    promoted.complete(null);
                } catch (Throwable failure) { promoted.completeExceptionally(failure); }
            });
            future(promoted);
            return client;
        }

        private Peer connectPeer(int index, PlayerIdentity identity) throws Exception {
            MasterClient master = null;
            RaceConnection connection;
            if (masterServer != null) {
                master = master(identity, "P" + index);
                if (roomId == null) {
                    roomId = future(master.createRoom(new ControlMessage.RoomDescriptor(
                            "Socket stress", "s3k", 0, 0, "OPEN", null, config.players(), false),
                            "RELAY", 0, FP)).roomId();
                    ControlMessage.RoomListResult listing = future(master.listRooms("s3k", 0));
                    check(listing.rooms().stream().anyMatch(room -> room.roomId().equals(roomId)),
                            "created relay room absent from browser");
                }
                connection = future(master.joinRoom(roomId, identity, "P" + index, FP));
            } else {
                connection = future(RaceClient.connect(endpoint, identity, "P" + index, FP));
            }
            connections.add(connection);
            return new Peer(index, identity, master, connection, totals);
        }

        private void register(Peer peer) {
            check(peer.connection.playerSlot() >= 0, "unassigned player slot");
            check(peers.stream().noneMatch(existing -> existing.connection.playerSlot() == peer.connection.playerSlot()),
                    "duplicate active player slot");
            check(peers.stream().noneMatch(existing -> existing.connection.sessionToken()
                    .equals(peer.connection.sessionToken())), "duplicate active room session token");
            ownerBySlot.put(peer.connection.playerSlot(), peer.index);
            peers.add(peer);
        }

        private void rejectOverflow() throws Exception {
            PlayerIdentity overflowIdentity = identity(config.players());
            try {
                if (masterServer != null) {
                    MasterClient overflow = master(overflowIdentity, "OVERFLOW");
                    RaceConnection unexpected = future(overflow.joinRoom(roomId, overflowIdentity, "OVERFLOW", FP));
                    connections.add(unexpected);
                } else {
                    connections.add(future(RaceClient.connect(endpoint, overflowIdentity, "OVERFLOW", FP)));
                }
                totals.admitted++;
                totals.peakConcurrency = Math.max(totals.peakConcurrency, peers.size() + 1);
                throw new IllegalStateException("capacity plus one was admitted");
            } catch (RaceClient.JoinRejectedException rejection) {
                check(rejection.getMessage().toLowerCase(Locale.ROOT).contains("full"),
                        "unexpected overflow rejection: " + rejection.getMessage());
                totals.expectedRejections++;
            }
        }

        private boolean membershipMatches() {
            Map<Integer, MemberIdentity> expected = new HashMap<>();
            peers.forEach(peer -> expected.put(peer.connection.playerSlot(),
                    new MemberIdentity(peer.identity.fingerprint(), peer.name)));
            return peers.stream().allMatch(peer -> peer.roomRoster.equals(expected));
        }

        private void assertMembership() {
            check(membershipMatches(), "room slot/identity/name mismatch on at least one peer");
        }

        private void warmup() throws Exception {
            long token = Long.MIN_VALUE;
            for (Peer peer : peers) {
                peer.pendingPings.put(token, System.nanoTime());
                peer.control(new ControlMessage.Ping(token), totals);
            }
            await(() -> peers.stream().allMatch(peer -> peer.pendingPings.isEmpty()), "warmup pongs");
            long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);
            await(() -> System.nanoTime() >= until, "warmup drain");
        }

        private void traffic() throws Exception {
            long started = System.nanoTime();
            long previousTick = started;
            long duration = TimeUnit.SECONDS.toNanos(config.durationSeconds());
            for (Peer peer : peers) {
                boolean idle = config.mix() == Mix.MIXED && profile(peer.index) == 1;
                peer.startAt = started + (idle ? duration / 2 : 0);
                peer.goalFrames = Math.max(30, config.durationSeconds() * (idle ? FPS / 2 : FPS));
            }
            Measurement measurement = new Measurement(proxy);
            TrafficWindow recordingWindow = new TrafficWindow();
            recordingWindow.transport = config.transport().name();
            recordingWindow.players = config.players();
            recordingWindow.begin();
            totals.measuring = true;
            try {
                long deadline = started + duration + TimeUnit.SECONDS.toNanos(config.timeoutSeconds());
                while (peers.stream().anyMatch(peer -> !peer.finishSent)) {
                    long now = System.nanoTime();
                    totals.maxGeneratorLatenessNanos = Math.max(totals.maxGeneratorLatenessNanos,
                            Math.max(0, now - previousTick - TimeUnit.MILLISECONDS.toNanos(3)));
                    previousTick = now;
                    tickTraffic(now);
                    drain();
                    if (System.nanoTime() > deadline) throw timeout("paced attempts");
                    Thread.sleep(3);
                }
                await(() -> peers.stream().allMatch(peer -> peer.finishObserved && peer.pendingPings.isEmpty()),
                        "accepted finishes and measured pongs");
                for (Peer peer : peers) {
                    check(peer.observedGhostFrames > 0, "peer received no other-player ghosts: " + peer.name);
                }
                // Unverified load rooms retain accepted claims internally but must
                // expose neither ranks nor a standings page to clients.
                peers.getFirst().control(new ControlMessage.StandingsPageRequest(0), totals);
                await(() -> standingsPages.containsKey(0), "empty casual standings page");
            } finally {
                totals.measuring = false;
                measurement.finish(proxy, totals);
                recordingWindow.end();
                recordingWindow.commit();
            }
        }

        private final Map<Integer, List<ControlMessage.StandingsRow>> standingsPages = new HashMap<>();

        private void tickTraffic(long now) {
            for (Peer peer : peers) {
                if (!peer.started && now >= peer.startAt) {
                    peer.control(new ControlMessage.AttemptStart(1), totals);
                    peer.publisher.beginAttempt(1);
                    peer.started = true;
                }
                if (!peer.pendingPings.isEmpty()) {
                    long oldest = peer.pendingPings.values().iterator().next();
                    check(now - oldest < TimeUnit.SECONDS.toNanos(config.timeoutSeconds()), "measured pong timeout");
                }
                // Idle workload members still own live sockets, including long soak windows.
                if (keepaliveDue(now, peer.lastPingAt, !peer.pendingPings.isEmpty())) {
                    long token = now;
                    peer.pendingPings.put(token, now);
                    peer.control(new ControlMessage.Ping(token), totals);
                    peer.lastPingAt = now;
                }
                if (!peer.started || peer.finishSent) continue;
                long elapsed = Math.max(0, now - peer.startAt);
                int due = (int) Math.min(peer.goalFrames, elapsed * FPS / 1_000_000_000L);
                if (config.mix() == Mix.MIXED && profile(peer.index) == 2) due = due / 9 * 9;
                if (elapsed >= peer.goalFrames * 1_000_000_000L / FPS) due = peer.goalFrames;
                while (peer.generatedFrames < due) {
                    peer.publisher.onFrame(pose(peer.index, peer.generatedFrames++));
                }
                if (peer.generatedFrames == peer.goalFrames) {
                    peer.publisher.finishAttempt();
                    peer.expectedTime = peer.goalFrames - 2;
                    peer.control(new ControlMessage.AttemptFinish(1, peer.expectedTime, 1,
                            peer.goalFrames - 1, "ab".repeat(32),
                            HexFormat.of().formatHex(peer.publisher.streamHashSha256()), null), totals);
                    peer.finishSent = true;
                }
            }
            totals.peakHeap = Math.max(totals.peakHeap, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }

        private int profile(int index) {
            return (int) Math.floorMod(config.seed() + index, 4);
        }

        private void sanctionAndRecover() throws Exception {
            Peer sacrificial = peers.getLast();
            for (int violation = 0; violation < 9; violation++) {
                sacrificial.control(new ControlMessage.AttemptReset(999), totals);
            }
            // This ordered response proves nine violations were processed without disconnect.
            sacrificial.pendingPings.put(Long.MIN_VALUE + 2, System.nanoTime());
            sacrificial.control(new ControlMessage.Ping(Long.MIN_VALUE + 2), totals);
            await(() -> sacrificial.pendingPings.isEmpty(), "nine-violation live barrier");
            sacrificial.intentionallyClosed = true;
            sacrificial.control(new ControlMessage.AttemptReset(999), totals);
            await(() -> sacrificial.disconnected, "tenth invalid-attempt sanction");
            if (config.transport() == Transport.JDK) {
                check(sacrificial.disconnectReason.contains("attempt protocol violations"),
                        "incorrect malicious attempt disconnect: " + sacrificial.disconnectReason);
            }
            totals.expectedSanctions++;
            peers.removeLast();
            await(() -> !peers.getFirst().roomMembers.contains(sacrificial.identity.fingerprint()),
                    "sanctioned participant removed");
            // A wire ping from every remaining player proves isolation before replacement.
            for (Peer healthy : peers) {
                healthy.pendingPings.put(Long.MIN_VALUE + 1, System.nanoTime());
                healthy.control(new ControlMessage.Ping(Long.MIN_VALUE + 1), totals);
            }
            await(() -> peers.stream().allMatch(peer -> peer.pendingPings.isEmpty()), "healthy peers after sanction");
            Peer replacement = connectPeer(sacrificial.index, sacrificial.identity);
            check(!sacrificial.connection.sessionToken().equals(replacement.connection.sessionToken()),
                    "sanction replacement reused previous session");
            register(replacement);
            await(this::membershipMatches, "every peer capacity after sanction");
            assertMembership();
            totals.reconnects++;
        }

        private void reconnectAfterRound() throws Exception {
            // Results reserve wire slots through round end, including all 256 slots at capacity.
            // A real next-round control transition clears that authority before any replacement.
            peers.forEach(peer -> peer.roundStarted = false);
            // Load peers never ready up; nobody waits under AUTO.
            peers.getFirst().control(new ControlMessage.SetReadyRule("AUTO"), totals);
            peers.getFirst().control(new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                    "s3k", 0, 0, config.durationSeconds() + 2, "OPEN", null)), totals);
            await(() -> peers.stream().allMatch(peer -> peer.roundStarted), "next-round reset");
            checkRoundStartAgreement();
            totals.roundResets++;
            Peer departing = peers.getLast();
            String oldToken = departing.connection.sessionToken();
            departing.intentionallyClosed = true;
            departing.connection.close();
            peers.removeLast();
            await(() -> !peers.getFirst().roomMembers.contains(departing.identity.fingerprint()), "disconnect membership removal");
            Peer replacement = connectPeer(departing.index, departing.identity);
            check(!oldToken.equals(replacement.connection.sessionToken()), "reconnect reused previous room session");
            register(replacement);
            await(this::membershipMatches, "every peer recovered capacity");
            assertMembership();
            long token = Long.MAX_VALUE;
            replacement.pendingPings.put(token, System.nanoTime());
            replacement.control(new ControlMessage.Ping(token), totals);
            await(() -> replacement.pendingPings.isEmpty(), "reconnected peer pong");
            totals.reconnects++;
        }

        private void verifyShutdown() throws Exception {
            if (masterServer != null) {
                Peer host = peers.getFirst();
                host.intentionallyClosed = true;
                host.connection.close();
                for (Peer peer : peers) peer.intentionallyClosed = true;
                await(() -> peers.stream().skip(1).allMatch(peer -> peer.disconnected || !peer.connection.isOpen()),
                        "host departure disconnects relay peers");
                MasterClient observer = master(identity(config.players() + 1), "OBSERVER");
                check(future(observer.listRooms("s3k", 0)).rooms().isEmpty(), "removed relay room remains browsable");
                totals.removedRooms++;
                masterServer.close();
                await(() -> !observer.isOpen(), "master shutdown closes observer");
                boolean refused = false;
                try { masterServer.execute(() -> { }); }
                catch (IllegalStateException expected) { refused = true; }
                check(refused, "closed master accepted actor work");
            } else {
                for (Peer peer : peers) peer.intentionallyClosed = true;
                directServer.close();
                await(() -> peers.stream().allMatch(peer -> peer.disconnected || !peer.connection.isOpen()),
                        "direct shutdown closes room peers");
                boolean refused = false;
                try { directServer.execute(() -> { }); }
                catch (IllegalStateException expected) { refused = true; }
                check(refused, "closed room accepted actor work");
            }
            try (Socket socket = new Socket()) {
                try {
                    socket.connect(new InetSocketAddress("127.0.0.1", serverPort), 500);
                    throw new IllegalStateException("shutdown server still accepts sockets");
                } catch (ConnectException expected) { }
            }
            totals.shutdowns++;
        }

        private void drain() throws Exception {
            if (proxy != null && proxy.failure() != null) throw proxy.failure();
            for (Peer peer : peers) {
                for (RaceClient.InboundEvent event : peer.connection.drainInbound()) {
                    switch (event) {
                        case RaceClient.Control control -> {
                            if (totals.measuring) totals.receivedControl++;
                            onControl(peer, control.message());
                        }
                        case RaceClient.GhostData ghost -> {
                            if (totals.measuring) {
                                totals.receivedBinary++;
                                totals.receivedGhostEntries += ghost.aggregate().entries().size();
                            }
                            for (GhostPackets.AggregateEntry entry : ghost.aggregate().entries()) {
                                check(entry.playerSlot() != peer.connection.playerSlot(), "self ghost leaked to peer");
                                Integer owner = ownerBySlot.get(entry.playerSlot());
                                check(owner != null, "ghost from unknown player slot");
                                check(entry.attemptId() == 1, "ghost attempt/session mismatch");
                                byte[] data = entry.frameData();
                                for (int frame = 0; frame < entry.frameCount(); frame++) {
                                    GhostFrame observed = GhostFrameCodec.decode(data, frame * GhostFrameCodec.BYTES);
                                    check(observed.equals(pose(owner, entry.startFrameIndex() + frame)),
                                            "cross-player or corrupt ghost pose");
                                }
                                peer.observedGhostFrames += entry.frameCount();
                                if (totals.measuring) totals.receivedGhostFrames += entry.frameCount();
                            }
                        }
                        case RaceClient.LobbyGhostData ignored -> { }
                        case RaceClient.Roster roster -> {
                            if (totals.measuring) {
                                totals.receivedBinary++;
                                totals.receivedRosters++;
                            }
                            check(roster.entries().stream().map(GhostPackets.RosterEntry::playerSlot).distinct().count()
                                    == roster.entries().size(), "duplicate roster player slots");
                            for (GhostPackets.RosterEntry entry : roster.entries()) {
                                if (entry.status() != GhostPackets.ROSTER_STATUS_FINISHED) continue;
                                Integer owner = ownerBySlot.get(entry.playerSlot());
                                check(owner != null, "finished roster entry has unknown player slot");
                                Peer finished = peers.get(owner);
                                finished.finishObserved = true;
                                totals.recordFinish(finished.connection.sessionToken());
                            }
                        }
                        case RaceClient.Disconnected disconnected -> {
                            peer.disconnected = true;
                            peer.disconnectReason = disconnected.reason();
                            check(peer.intentionallyClosed, "unexpected disconnect " + peer.name + ": " + disconnected.reason());
                        }
                    }
                }
                check(peer.intentionallyClosed || peer.connection.isOpen(), "unexpected closed peer " + peer.name);
            }
        }

        private void onControl(Peer peer, ControlMessage message) {
            switch (message) {
                case ControlMessage.RoomState state -> {
                    Set<Integer> slots = new HashSet<>();
                    Set<String> fingerprints = new HashSet<>();
                    Map<Integer, MemberIdentity> roster = new HashMap<>();
                    for (ControlMessage.PlayerInfo player : state.players()) {
                        check(slots.add(player.slot()) && fingerprints.add(player.fingerprint()), "duplicate room slot/identity");
                        int index = Integer.parseInt(player.displayName().substring(1));
                        check(index >= 0 && index < config.players(), "unexpected room participant");
                        ownerBySlot.put(player.slot(), index);
                        roster.put(player.slot(), new MemberIdentity(player.fingerprint(), player.displayName()));
                    }
                    peer.roomMembers = Set.copyOf(fingerprints);
                    peer.roomRoster = Map.copyOf(roster);
                }
                case ControlMessage.PlayerUpsert upsert -> {
                    ControlMessage.PlayerInfo player = upsert.player();
                    int index = Integer.parseInt(player.displayName().substring(1));
                    check(index >= 0 && index < config.players(), "unexpected room participant");
                    ownerBySlot.put(player.slot(), index);
                    Map<Integer, MemberIdentity> roster = new HashMap<>(peer.roomRoster);
                    roster.put(player.slot(), new MemberIdentity(player.fingerprint(), player.displayName()));
                    Set<String> fingerprints = new HashSet<>();
                    roster.values().forEach(info -> check(fingerprints.add(info.fingerprint()), "duplicate room identity"));
                    peer.roomMembers = Set.copyOf(fingerprints);
                    peer.roomRoster = Map.copyOf(roster);
                }
                case ControlMessage.PlayerLeft left -> {
                    Map<Integer, MemberIdentity> roster = new HashMap<>(peer.roomRoster);
                    check(roster.remove(left.slot()) != null, "departure for unknown room slot");
                    peer.roomRoster = Map.copyOf(roster);
                    peer.roomMembers = roster.values().stream().map(MemberIdentity::fingerprint)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                }
                case ControlMessage.RoundStart start -> {
                    ControlMessage.RoundConfig expected = new ControlMessage.RoundConfig(
                            "s3k", 0, 0, config.durationSeconds() + 2, "OPEN", null);
                    check(expected.equals(start.config()), "incorrect round configuration");
                    check(start.deadlineHubMillis() - start.countdownEndsAtHubMillis()
                                    == expected.windowSeconds() * 1000L,
                            "round deadline does not match configured window");
                    peer.roundStarted = true;
                    peer.countdownEndsAt = start.countdownEndsAtHubMillis();
                }
                case ControlMessage.Pong pong -> {
                    Long sent = peer.pendingPings.remove(pong.t0ClientMillis());
                    check(sent != null, "unmatched/cross-session pong");
                    if (totals.measuring) totals.samplePing(System.nanoTime() - sent);
                }
                case ControlMessage.StandingsDelta delta ->
                        check(delta.rows().isEmpty(), "unverified standings were published");
                case ControlMessage.RankUpdate ignored ->
                        throw new IllegalStateException("unverified private rank was published");
                case ControlMessage.StandingsPage page -> {
                    check(page.totalPages() == 0 && page.page() == 0 && page.rows().isEmpty(),
                            "unverified standings page was published");
                    check(standingsPages.putIfAbsent(page.page(), page.rows()) == null, "duplicate standings page response");
                }
                case ControlMessage.RoundEnd end -> {
                    check(end.finalStandings().isEmpty(),
                            "unverified round-end standings were published");
                    peer.roundEnded = true;
                }
                case ControlMessage.Kick kick -> throw new IllegalStateException("unexpected kick: " + kick.reason());
                default -> { }
            }
        }

        private void checkRoundStartAgreement() {
            long countdown = peers.getFirst().countdownEndsAt;
            check(peers.stream().allMatch(peer -> peer.countdownEndsAt == countdown),
                    "round countdown differs between participants");
        }

        private <T> T future(CompletableFuture<T> future) throws Exception {
            try {
                await(future::isDone, "network future");
                return future.get();
            } catch (ExecutionException wrapped) {
                Throwable cause = wrapped.getCause();
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw wrapped;
            }
        }

        private void await(BooleanSupplier condition, String label) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds());
            while (true) {
                drain();
                if (condition.getAsBoolean()) return;
                if (System.nanoTime() >= deadline) throw timeout(label);
                Thread.sleep(5);
            }
        }

        private IllegalStateException timeout(String label) {
            return new IllegalStateException("stage=" + stage + ", seed=" + config.seed() + ": timed out waiting for " + label);
        }

        @Override
        public void close() throws Exception {
            if (closed) return;
            closed = true;
            Exception first = null;
            List<AutoCloseable> resources = new ArrayList<>();
            connections.forEach(connection -> resources.add(connection::close));
            resources.addAll(masters);
            if (masterServer != null) resources.add(masterServer);
            if (directServer != null) resources.add(directServer);
            if (proxy != null) resources.add(proxy);
            for (AutoCloseable resource : resources) {
                try { resource.close(); }
                catch (Exception failure) {
                    if (first == null) first = failure;
                    else first.addSuppressed(failure);
                }
            }
            if (first != null) throw first;
            if (proxy != null) check(proxy.openSockets() == 0, "proxy sockets remain after close");
        }
    }

    /** Phase-independent keepalive schedule; idle members must not hit the transport idle timeout. */
    static boolean keepaliveDue(long now, long lastPingAt, boolean pingPending) {
        return !pingPending && (lastPingAt == 0 || now - lastPingAt >= TimeUnit.SECONDS.toNanos(1));
    }

    private static GhostFrame pose(int index, int frame) {
        int phase = frame % 30;
        int x = 100 + index * 4 + (phase <= 15 ? phase : 30 - phase);
        return new GhostFrame(x, 100 + index * 2, 1, false, false, false, 2, false);
    }

    private static MasterConfig masterConfig() {
        // Fast identity proof is harness configuration; address/room/transport caps are unchanged.
        return new MasterConfig(0, null, null, true, "identities.db", 0,
                java.util.UUID.randomUUID().toString(),
                48, 10, 14, 50, 8, 8, false, 2, 4, 30, 20, 30, 10_000, 60);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static long processCpuNanos() {
        return ProcessHandle.current().info().totalCpuDuration().orElse(Duration.ZERO).toNanos();
    }

    private record Gc(long collections, long millis) {
        static Gc read() {
            long collections = 0;
            long millis = 0;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                collections += Math.max(0, collector.getCollectionCount());
                millis += Math.max(0, collector.getCollectionTime());
            }
            return new Gc(collections, millis);
        }
    }

    private static final class Measurement {
        final long started = System.nanoTime();
        final long cpu = processCpuNanos();
        final Gc gc = Gc.read();
        final long upstream;
        final long downstream;
        Measurement(LoopbackSourceProxy proxy) {
            upstream = proxy.upstreamBytes();
            downstream = proxy.downstreamBytes();
        }
        void finish(LoopbackSourceProxy proxy, Totals totals) {
            totals.elapsedNanos += System.nanoTime() - started;
            totals.cpuNanos += Math.max(0, processCpuNanos() - cpu);
            Gc after = Gc.read();
            totals.gcCollections += Math.max(0, after.collections() - gc.collections());
            totals.gcMillis += Math.max(0, after.millis() - gc.millis());
            totals.upstream += proxy.upstreamBytes() - upstream;
            totals.downstream += proxy.downstreamBytes() - downstream;
        }
    }

    static final class Totals {
        final Config config;
        final List<String> failures = new ArrayList<>();
        final long[] pingReservoir = new long[MAX_PING_SAMPLES];
        final SplittableRandom random;
        boolean measuring;
        int completedCycles;
        int admitted;
        int peakConcurrency;
        final Set<String> finishedSessions = new HashSet<>();
        int expectedRejections;
        int reconnects;
        int expectedSanctions;
        int roundResets;
        int removedRooms;
        int shutdowns;
        long sentControl;
        long receivedControl;
        long sentBinary;
        long sentGhostFrames;
        long receivedBinary;
        long receivedGhostEntries;
        long receivedGhostFrames;
        long receivedRosters;
        long upstream;
        long downstream;
        long pings;
        long elapsedNanos;
        long cpuNanos;
        long gcCollections;
        long gcMillis;
        long peakHeap;
        long maxGeneratorLatenessNanos;
        Totals(Config config) { this.config = config; random = new SplittableRandom(config.seed()); }
        void recordFinish(String roomSessionToken) {
            check(roomSessionToken != null && !roomSessionToken.isBlank(), "accepted finish has no room session");
            finishedSessions.add(roomSessionToken);
        }
        void fail(Throwable failure) {
            if (failures.size() < 20) failures.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            for (Throwable suppressed : failure.getSuppressed()) {
                if (failures.size() < 20) failures.add("cleanup: " + suppressed.getMessage());
            }
        }
        void samplePing(long nanos) {
            long ordinal = ++pings;
            if (ordinal <= pingReservoir.length) pingReservoir[(int) ordinal - 1] = nanos;
            else {
                long slot = random.nextLong(ordinal);
                if (slot < pingReservoir.length) pingReservoir[(int) slot] = nanos;
            }
        }
        Report report() {
            int expected = config.players() * config.cycles();
            int finished = finishedSessions.size();
            boolean success = failures.isEmpty() && completedCycles == config.cycles()
                    && admitted == expected && finished == expected
                    && expectedRejections == config.cycles()
                    && expectedSanctions == (config.mix() == Mix.MIXED ? config.cycles() : 0)
                    && reconnects == config.cycles() * (config.mix() == Mix.MIXED ? 2 : 1)
                    && roundResets == config.cycles() && shutdowns == config.cycles()
                    && removedRooms == (config.transport() == Transport.RELAY ? config.cycles() : 0)
                    && (config.transport() != Transport.RELAY || config.players() <= 8 || receivedRosters > 0)
                    && sentGhostFrames > 0 && receivedGhostFrames > 0 && pings > 0;
            if (!success && failures.isEmpty()) failures.add("incomplete observed workload/lifecycle contract");
            long[] sorted = Arrays.copyOf(pingReservoir, (int) Math.min(pings, pingReservoir.length));
            Arrays.sort(sorted);
            return new Report(config, success, completedCycles, expected, admitted, peakConcurrency,
                    expected, finished, expectedRejections, expectedSanctions, failures.size(), failures,
                    sentControl, receivedControl, sentBinary, sentGhostFrames,
                    receivedBinary, receivedGhostEntries, receivedGhostFrames, receivedRosters,
                    upstream, downstream, pings, percentile(sorted, .50), percentile(sorted, .95),
                    percentile(sorted, .99), elapsedNanos / 1e9, cpuNanos / 1e9,
                    gcCollections, gcMillis, peakHeap, reconnects, roundResets, removedRooms, shutdowns,
                    maxGeneratorLatenessNanos / 1e6);
        }
    }

    private static double percentile(long[] sorted, double percentile) {
        return sorted.length == 0 ? 0 : sorted[Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1)] / 1e6;
    }

    /** Strict option parsing keeps an accidental maximum-capacity/long run opt-in. */
    public static Config parseArgs(String[] args) {
        Map<String, String> options = options(args);
        return new Config(Integer.parseInt(options.getOrDefault("--players", "4")),
                Integer.parseInt(options.getOrDefault("--duration", "1")),
                Integer.parseInt(options.getOrDefault("--cycles", "1")),
                Long.parseLong(options.getOrDefault("--seed", "4675398")),
                Mix.valueOf(options.getOrDefault("--mix", "mixed").toUpperCase(Locale.ROOT)),
                Transport.valueOf(options.getOrDefault("--transport", "relay").toUpperCase(Locale.ROOT)),
                Integer.parseInt(options.getOrDefault("--timeout", "15")));
    }

    private static Map<String, String> options(String[] args) {
        Set<String> known = Set.of("--players", "--duration", "--cycles", "--seed", "--mix",
                "--transport", "--timeout", "--data", "--report");
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (!known.contains(args[index]) || index + 1 == args.length
                    || options.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("unknown, duplicate or missing option: " + args[index]);
            }
        }
        return options;
    }

    public static void main(String[] args) throws Exception {
        Config config = parseArgs(args);
        Map<String, String> options = options(args);
        System.out.println("Real loopback protocol load; synthetic ghost poses, no engine/ROM replay. "
                + "CPU, GC and heap cover the whole colocated server, clients and source proxy. "
                + "Control-only warmup; measured paths include first ghost/round work, not steady-state server speed. "
                + "Ping percentiles use a bounded 8192-sample reservoir; no timing pass threshold.");
        Report report = run(config, Path.of(options.getOrDefault("--data", "target/racing-socket-stress")));
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
        if (options.containsKey("--report")) {
            Path path = Path.of(options.get("--report"));
            if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, json + System.lineSeparator());
        }
        System.out.println(json);
        if (!report.success()) throw new IllegalStateException("socket stress contract failed: " + report.failures());
    }
}
