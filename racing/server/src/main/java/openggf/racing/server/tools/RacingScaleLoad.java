package openggf.racing.server.tools;

import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.MasterClient;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** One owned room's clients/proxy, deliberately outside the measured server JVM. */
final class RacingScaleLoad implements AutoCloseable {
    public record Report(int roomIndex, String roomId, boolean success, int admittedPlayers,
                         int finishedPlayers, boolean overflowRejected, boolean reconnected,
                         boolean removedFromBrowser, boolean proxyTerminated,
                         long initialAdmissionMillis, RacingScaleMetrics.Latency initialJoinLatency,
                         long admissionUpstreamBytes, long admissionDownstreamBytes,
                         long measuredUpstreamBytes, long measuredDownstreamBytes,
                         long sentGhostFrames, long receivedGhostFrames,
                         long browserRequests, long admissionConnections,
                         RacingScaleMetrics.Latency browserLatency,
                         RacingScaleMetrics.Latency admissionLatency,
                         RacingScaleMetrics.Latency pingLatency,
                         RacingScaleMetrics.Latency generatorLateness,
                         String sourceRange, int distinctSourceAddresses,
                         RacingScaleMetrics.ProcessReport process,
                         List<String> jvmArguments) { }

    private static final int FPS = 60;
    private final RacingScaleTool.Config config;
    private final Path run;
    private final int roomIndex;
    private final LoopbackSourceProxy proxy;
    private final URI endpoint;
    private final List<Peer> peers = new ArrayList<>();
    private final List<MasterClient> masters = new ArrayList<>();
    private final Map<Integer, Peer> bySlot = new HashMap<>();
    private final Map<String, Peer> byName = new HashMap<>();
    private final Set<Integer> finished = new HashSet<>();
    private final Map<Integer, List<ControlMessage.StandingsRow>> pages = new HashMap<>();
    private final RacingScaleMetrics.Latencies initialJoins = new RacingScaleMetrics.Latencies();
    private final RacingScaleMetrics.Latencies browsers = new RacingScaleMetrics.Latencies();
    private final RacingScaleMetrics.Latencies admissions = new RacingScaleMetrics.Latencies();
    private final RacingScaleMetrics.Latencies pings = new RacingScaleMetrics.Latencies();
    private final RacingScaleMetrics.Latencies lateness = new RacingScaleMetrics.Latencies();
    private final AtomicLong browserRequests = new AtomicLong();
    private final AtomicLong admissionConnections = new AtomicLong();
    private final AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
    private RacingScaleTool.Window window;
    private Thread browserThread;
    private Thread admissionThread;
    private volatile boolean stopBackground;
    private String roomId;
    private long sentGhostFrames;
    private long receivedGhostFrames;
    private boolean allowDisconnect;
    private boolean closed;

    private static final class Peer {
        final int index;
        final PlayerIdentity identity;
        final String name;
        final MasterClient master;
        final RaceConnection connection;
        final GhostStreamPublisher publisher;
        final Map<Long, Long> pendingPings = new HashMap<>();
        Map<Integer, String> roster = Map.of();
        int frames;
        long countdown;
        long nextPing;
        long observedGhostFrames;
        boolean roundStarted;
        boolean roundEnded;
        boolean started;
        boolean finishSent;
        boolean intentionallyClosed;

        Peer(int index, PlayerIdentity identity, String name, MasterClient master, RaceConnection connection,
             RacingScaleLoad workload) {
            this.index = index;
            this.identity = identity;
            this.name = name;
            this.master = master;
            this.connection = connection;
            publisher = new GhostStreamPublisher(packet -> {
                workload.check(connection.isOpen(), "closed peer sent ghost frames");
                if (workload.measuring()) workload.sentGhostFrames += GhostPackets.decodeFrames(packet).frameCount();
                connection.sendBinary(packet);
            });
        }
    }

    private RacingScaleLoad(Path run, RacingScaleTool.Config config, int roomIndex) throws Exception {
        this.run = run;
        this.config = config;
        this.roomIndex = roomIndex;
        int serverPort = RacingScaleTool.JSON.readTree(run.resolve("server.ready").toFile()).get("port").asInt();
        int budget = config.players() + 16
                + (config.durationSeconds() + config.warmupSeconds() + config.timeoutSeconds() * 4)
                / config.admissionIntervalSeconds();
        proxy = new LoopbackSourceProxy(serverPort, budget, 72 + roomIndex);
        endpoint = URI.create("ws://127.0.0.1:" + proxy.port() + "/master");
    }

    static void run(Path run, RacingScaleTool.Config config, int roomIndex) throws Exception {
        Report report;
        try (RacingScaleLoad load = new RacingScaleLoad(run, config, roomIndex)) {
            report = load.run();
            load.close();
            RacingScaleTool.check(load.proxy.isTerminated(), "load proxy threads/sockets remain");
            report = new Report(report.roomIndex(), report.roomId(), report.success(), report.admittedPlayers(),
                    report.finishedPlayers(), report.overflowRejected(), report.reconnected(),
                    report.removedFromBrowser(), true, report.initialAdmissionMillis(), report.initialJoinLatency(),
                    report.admissionUpstreamBytes(), report.admissionDownstreamBytes(),
                    report.measuredUpstreamBytes(), report.measuredDownstreamBytes(), report.sentGhostFrames(),
                    report.receivedGhostFrames(), report.browserRequests(), report.admissionConnections(),
                    report.browserLatency(), report.admissionLatency(), report.pingLatency(), report.generatorLateness(),
                    report.sourceRange(), load.proxy.acceptedConnections(), report.process(), report.jvmArguments());
        }
        RacingScaleTool.write(run.resolve("room-" + roomIndex + ".json"), report);
    }

    private Report run() throws Exception {
        long admissionStart = System.nanoTime();
        for (int index = 0; index < config.players(); index++) {
            long before = System.nanoTime();
            Peer peer = connect(index);
            register(peer);
            initialJoins.add(System.nanoTime() - before);
        }
        await(this::membershipMatches, "complete room identity/slot membership");
        long admissionMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - admissionStart);
        long admissionUpstream = proxy.upstreamBytes(), admissionDownstream = proxy.downstreamBytes();
        rejectOverflow();
        RacingScaleTool.write(run.resolve("room-" + roomIndex + ".ready"), Map.of("roomId", roomId));
        long windowDeadline = RacingScaleTool.deadline(Math.max(60, config.players() * 2) + config.timeoutSeconds());
        while (!java.nio.file.Files.exists(run.resolve("window.json"))) {
            drain();
            check(System.nanoTime() < windowDeadline, "timeout waiting for shared traffic window");
            Thread.sleep(10);
        }
        window = RacingScaleTool.JSON.readValue(run.resolve("window.json").toFile(), RacingScaleTool.Window.class);
        peers.getFirst().connection.sendControl(new ControlMessage.RoundConfigure(roundConfig()));
        await(() -> peers.stream().allMatch(peer -> peer.roundStarted), "all round starts");
        long countdown = peers.getFirst().countdown;
        check(peers.stream().allMatch(peer -> peer.countdown == countdown), "round countdown disagreement");
        check(countdown <= window.trafficStartEpochMillis(), "shared start precedes room countdown");
        startBackground();
        int goalFrames = (config.warmupSeconds() + config.durationSeconds()) * FPS;
        RacingScaleMetrics.ProcessWindow measurement = null;
        RacingScaleMetrics.ProcessReport process = null;
        long upstream = 0, downstream = 0, measuredUpstream = 0, measuredDownstream = 0;
        long tick = System.nanoTime();
        long until = RacingScaleTool.deadline(config.warmupSeconds() + config.durationSeconds() + config.timeoutSeconds());
        try {
            while (System.currentTimeMillis() < window.measureEndEpochMillis()) {
                long nowMillis = System.currentTimeMillis();
                long nowNanos = System.nanoTime();
                if (nowMillis >= window.measureStartEpochMillis() && measurement == null) {
                    measurement = new RacingScaleMetrics.ProcessWindow();
                    upstream = proxy.upstreamBytes();
                    downstream = proxy.downstreamBytes();
                }
                if (measuring()) lateness.add(Math.max(0, nowNanos - tick - TimeUnit.MILLISECONDS.toNanos(3)));
                tick = nowNanos;
                if (nowMillis >= window.trafficStartEpochMillis()) {
                    int due = (int) Math.min(goalFrames, (nowMillis - window.trafficStartEpochMillis()) * FPS / 1000);
                    for (Peer peer : peers) {
                        if (!peer.started) {
                            peer.connection.sendControl(new ControlMessage.AttemptStart(1));
                            peer.publisher.beginAttempt(1);
                            peer.started = true;
                        }
                        while (peer.frames < due) peer.publisher.onFrame(pose(peer.index, peer.frames++));
                        if (nowNanos >= peer.nextPing && peer.pendingPings.isEmpty()) {
                            peer.pendingPings.put(nowNanos, nowNanos);
                            peer.connection.sendControl(new ControlMessage.Ping(nowNanos));
                            peer.nextPing = nowNanos + TimeUnit.SECONDS.toNanos(1);
                        }
                    }
                }
                drain();
                check(System.nanoTime() < until, "traffic generator deadline exceeded");
                Thread.sleep(3);
            }
        } finally {
            if (measurement != null) {
                measuredUpstream = proxy.upstreamBytes() - upstream;
                measuredDownstream = proxy.downstreamBytes() - downstream;
                measurement.close();
                process = measurement.report();
            }
        }
        check(process != null, "shared measurement window was missed");
        stopBackground();
        check(sentGhostFrames > 0 && receivedGhostFrames > 0 && measuredUpstream > 0 && measuredDownstream > 0,
                "measured traffic was not observed");
        check(browserRequests.get() > 0 && admissionConnections.get() > 0 && pings.report().samples() > 0,
                "measured browser/admission/ping traffic was not observed");
        for (Peer peer : peers) {
            while (peer.frames < goalFrames) peer.publisher.onFrame(pose(peer.index, peer.frames++));
            peer.publisher.finishAttempt();
            peer.finishSent = true;
            peer.connection.sendControl(new ControlMessage.AttemptFinish(1, goalFrames - 2, 1,
                    goalFrames - 1, "ab".repeat(32), HexFormat.of().formatHex(peer.publisher.streamHashSha256()), null));
        }
        await(() -> finished.size() == config.players(), "accepted finish for every player");
        for (Peer peer : peers) check(peer.observedGhostFrames > 0, "peer received no remote ghosts");
        int pageCount = (config.players() + 9) / 10;
        for (int page = 0; page < pageCount; page++) {
            peers.get(page).connection.sendControl(new ControlMessage.StandingsPageRequest(page));
        }
        await(() -> pages.size() == pageCount, "all standings pages");
        Set<String> names = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (var rows : pages.values()) for (var row : rows) {
            check(names.add(row.displayName()) && ranks.add(row.rank()), "duplicate standings participant/rank");
        }
        check(names.size() == config.players(), "standings omit players");
        await(() -> peers.stream().allMatch(peer -> peer.roundEnded), "all round ends");
        reconnect();
        removeRoom();
        return new Report(roomIndex, roomId, true, config.players(), finished.size(), true, true, true,
                false, admissionMillis, initialJoins.report(), admissionUpstream, admissionDownstream,
                measuredUpstream, measuredDownstream, sentGhostFrames, receivedGhostFrames,
                browserRequests.get(), admissionConnections.get(), browsers.report(), admissions.report(),
                pings.report(), lateness.report(), "127." + (72 + roomIndex) + ".0.1..127."
                + (72 + roomIndex) + ".255.255; one source per accepted proxy connection",
                proxy.acceptedConnections(), process,
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    private ControlMessage.RoundConfig roundConfig() {
        return new ControlMessage.RoundConfig("s3k", 0, 0,
                config.warmupSeconds() + config.durationSeconds() + 5, "OPEN", null);
    }

    private Peer connect(int index) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(RacingScaleTool.identityPath(run, roomIndex, index));
        String name = "R" + roomIndex + "P" + index;
        MasterClient master = future(MasterClient.connect(endpoint, identity, name, RacingScaleTool.FP, null));
        masters.add(master);
        if (roomId == null) {
            roomId = future(master.createRoom(new ControlMessage.RoomDescriptor("Scale " + roomIndex,
                    "s3k", 0, 0, "OPEN", null, config.players(), false), "RELAY", 0, RacingScaleTool.FP)).roomId();
        }
        RaceConnection connection = future(master.joinRoom(roomId, identity, name, RacingScaleTool.FP));
        return new Peer(index, identity, name, master, connection, this);
    }

    private void register(Peer peer) {
        check(peer.connection.playerSlot() >= 0, "missing player slot");
        check(peers.stream().noneMatch(other -> other.connection.playerSlot() == peer.connection.playerSlot()),
                "duplicate active player slot");
        check(peers.stream().noneMatch(other -> other.connection.sessionToken().equals(peer.connection.sessionToken())),
                "duplicate active session token");
        peers.add(peer);
        bySlot.put(peer.connection.playerSlot(), peer);
        byName.put(peer.name, peer);
    }

    private void rejectOverflow() throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(RacingScaleTool.identityPath(run, roomIndex, config.players()));
        try (MasterClient overflow = future(MasterClient.connect(endpoint, identity, "OVERFLOW", RacingScaleTool.FP, null))) {
            try {
                RaceConnection connection = future(overflow.joinRoom(roomId, identity, "OVERFLOW", RacingScaleTool.FP));
                connection.close();
                throw new IllegalStateException("capacity plus one admitted");
            } catch (RaceClient.JoinRejectedException expected) {
                check(expected.getMessage().toLowerCase(Locale.ROOT).contains("full"), "wrong overflow rejection");
            }
        }
    }

    private boolean membershipMatches() {
        Map<Integer, String> expected = new HashMap<>();
        for (Peer peer : peers) expected.put(peer.connection.playerSlot(), peer.identity.fingerprint());
        return peers.stream().allMatch(peer -> peer.roster.equals(expected));
    }

    private void startBackground() {
        browserThread = Thread.ofVirtual().name("scale-browser").start(() -> {
            try {
                PlayerIdentity identity = PlayerIdentity.loadOrCreate(RacingScaleTool.identityPath(run, roomIndex, config.players() + 1));
                try (MasterClient browser = backgroundConnect(identity, "BROWSER")) {
                    while (!stopBackground && System.currentTimeMillis() < window.measureEndEpochMillis()) {
                        if (System.currentTimeMillis() < window.measureStartEpochMillis()) { Thread.sleep(10); continue; }
                        long start = System.nanoTime();
                        var result = browser.listRooms("s3k", 0).get(config.timeoutSeconds(), TimeUnit.SECONDS);
                        check(result.rooms().stream().anyMatch(room -> room.roomId().equals(roomId)),
                                "active room missing from browser");
                        browsers.add(System.nanoTime() - start);
                        browserRequests.incrementAndGet();
                        Thread.sleep(config.browserIntervalMillis());
                    }
                }
            } catch (Throwable failure) { if (!stopBackground) backgroundFailure.compareAndSet(null, failure); }
        });
        admissionThread = Thread.ofVirtual().name("scale-admission").start(() -> {
            try {
                // One sequential churn identity per room: never concurrent duplicate logins.
                PlayerIdentity identity = PlayerIdentity.loadOrCreate(RacingScaleTool.identityPath(run, roomIndex, config.players()));
                while (!stopBackground && System.currentTimeMillis() < window.measureEndEpochMillis()) {
                    if (System.currentTimeMillis() < window.measureStartEpochMillis()) { Thread.sleep(10); continue; }
                    long start = System.nanoTime();
                    try (MasterClient admission = backgroundConnect(identity, "ADMISSION")) {
                        admissions.add(System.nanoTime() - start);
                        admissionConnections.incrementAndGet();
                    }
                    Thread.sleep(config.admissionIntervalSeconds() * 1000L);
                }
            } catch (Throwable failure) { if (!stopBackground) backgroundFailure.compareAndSet(null, failure); }
        });
    }

    private MasterClient backgroundConnect(PlayerIdentity identity, String name) throws Exception {
        CompletableFuture<MasterClient> pending = MasterClient.connect(endpoint, identity, name, RacingScaleTool.FP, null);
        try {
            return pending.get(config.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception failure) {
            // Cancel admission authority as well as the waiter when the shared window closes.
            pending.cancel(true);
            throw failure;
        }
    }

    private void stopBackground() throws Exception {
        stopBackground = true;
        for (Thread thread : new Thread[]{browserThread, admissionThread}) {
            if (thread == null) continue;
            thread.interrupt();
            thread.join(TimeUnit.SECONDS.toMillis(config.timeoutSeconds()));
            check(!thread.isAlive(), "background client did not terminate");
        }
        Throwable failure = backgroundFailure.get();
        if (failure != null) throw new IllegalStateException("browser/admission traffic failed", failure);
    }

    private void reconnect() throws Exception {
        peers.forEach(peer -> peer.roundStarted = false);
        peers.getFirst().connection.sendControl(new ControlMessage.RoundConfigure(roundConfig()));
        await(() -> peers.stream().allMatch(peer -> peer.roundStarted), "next round reset");
        Peer old = peers.removeLast();
        old.intentionallyClosed = true;
        old.connection.close();
        await(() -> peers.stream().allMatch(peer -> !peer.roster.containsValue(old.identity.fingerprint())),
                "departed player's membership removal");
        Peer replacement = connect(old.index);
        check(!replacement.connection.sessionToken().equals(old.connection.sessionToken()), "reconnect reused session token");
        register(replacement);
        await(this::membershipMatches, "recovered full room membership");
        long token = System.nanoTime();
        replacement.pendingPings.put(token, token);
        replacement.connection.sendControl(new ControlMessage.Ping(token));
        await(replacement.pendingPings::isEmpty, "replacement pong");
    }

    private void removeRoom() throws Exception {
        allowDisconnect = true;
        peers.getFirst().connection.close();
        await(() -> peers.stream().allMatch(peer -> !peer.connection.isOpen()), "host departure disconnects guests");
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(RacingScaleTool.identityPath(run, roomIndex, config.players() + 1));
        try (MasterClient observer = future(MasterClient.connect(endpoint, identity, "OBSERVER", RacingScaleTool.FP, null))) {
            await(() -> {
                try {
                    return future(observer.listRooms("s3k", 0)).rooms().stream()
                            .noneMatch(room -> room.roomId().equals(roomId));
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }, "removed room absent from browser");
        }
    }

    private void drain() {
        if (proxy.failure() != null) throw new IllegalStateException("source proxy failed", proxy.failure());
        Throwable background = backgroundFailure.get();
        if (background != null) throw new IllegalStateException("background traffic failed", background);
        for (Peer peer : peers) {
            for (RaceClient.InboundEvent event : peer.connection.drainInbound()) {
                switch (event) {
                    case RaceClient.Control control -> onControl(peer, control.message());
                    case RaceClient.GhostData data -> {
                        for (var entry : data.aggregate().entries()) {
                            Peer owner = bySlot.get(entry.playerSlot());
                            check(owner != null && owner != peer && entry.attemptId() == 1, "wrong ghost participant/attempt");
                            byte[] frameData = entry.frameData();
                            for (int frame = 0; frame < entry.frameCount(); frame++) {
                                GhostFrame observed = GhostFrameCodec.decode(frameData, frame * GhostFrameCodec.BYTES);
                                check(observed.equals(pose(owner.index, entry.startFrameIndex() + frame)),
                                        "cross-room/corrupt ghost pose");
                            }
                            peer.observedGhostFrames += entry.frameCount();
                            if (measuring()) receivedGhostFrames += entry.frameCount();
                        }
                    }
                    case RaceClient.Roster roster -> check(roster.entries().stream().map(GhostPackets.RosterEntry::playerSlot)
                            .distinct().count() == roster.entries().size(), "duplicate binary roster slots");
                    case RaceClient.Disconnected disconnected -> check(allowDisconnect || peer.intentionallyClosed,
                            "unexpected disconnect: " + peer.name + ": " + disconnected.reason());
                }
            }
            check(allowDisconnect || peer.intentionallyClosed || peer.connection.isOpen(), "peer socket closed unexpectedly");
            long now = System.nanoTime();
            if (!allowDisconnect && !peer.intentionallyClosed && (window == null
                    || System.currentTimeMillis() < window.trafficStartEpochMillis())
                    && now >= peer.nextPing && peer.pendingPings.isEmpty()) {
                peer.pendingPings.put(now, now);
                peer.connection.sendControl(new ControlMessage.Ping(now));
                peer.nextPing = now + TimeUnit.SECONDS.toNanos(1);
            }
            for (long sent : peer.pendingPings.values()) {
                check(System.nanoTime() - sent < TimeUnit.SECONDS.toNanos(config.timeoutSeconds()), "pong timeout");
            }
        }
    }

    private void onControl(Peer peer, ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomState state -> {
                Map<Integer, String> roster = new HashMap<>();
                Set<String> fingerprints = new HashSet<>();
                for (var player : state.players()) {
                    check(roster.put(player.slot(), player.fingerprint()) == null && fingerprints.add(player.fingerprint()),
                            "duplicate room identity/slot");
                    check(player.displayName().startsWith("R" + roomIndex + "P"), "cross-room roster name");
                }
                peer.roster = Map.copyOf(roster);
            }
            case ControlMessage.PlayerUpsert upsert -> {
                var player = upsert.player();
                check(player.displayName().startsWith("R" + roomIndex + "P"), "cross-room roster name");
                Map<Integer, String> roster = new HashMap<>(peer.roster);
                roster.put(player.slot(), player.fingerprint());
                check(new HashSet<>(roster.values()).size() == roster.size(), "duplicate room identity");
                peer.roster = Map.copyOf(roster);
            }
            case ControlMessage.PlayerLeft left -> {
                Map<Integer, String> roster = new HashMap<>(peer.roster);
                check(roster.remove(left.slot()) != null, "departure for unknown room slot");
                peer.roster = Map.copyOf(roster);
            }
            case ControlMessage.RoundStart start -> {
                check(start.config().equals(roundConfig()), "wrong round configuration");
                peer.roundStarted = true;
                peer.countdown = start.countdownEndsAtHubMillis();
            }
            case ControlMessage.Pong pong -> {
                Long sent = peer.pendingPings.remove(pong.t0ClientMillis());
                check(sent != null, "unmatched/cross-session pong");
                if (measuring()) pings.add(System.nanoTime() - sent);
            }
            case ControlMessage.StandingsDelta delta -> delta.rows().forEach(this::observeStanding);
            case ControlMessage.RankUpdate rank -> {
                check(peer.finishSent && rank.bestTimeFrames() == goalFrames() - 2
                        && rank.rank() > HostRoundEngine.STANDINGS_BROADCAST_CAP && rank.rank() <= config.players(),
                        "incorrect private standing");
                finished.add(peer.index);
            }
            case ControlMessage.StandingsPage page -> {
                int count = (config.players() + 9) / 10;
                check(page.totalPages() == count && page.page() >= 0 && page.page() < count, "wrong standings pagination");
                check(page.rows().size() == Math.min(10, config.players() - page.page() * 10), "wrong standings page length");
                for (int row = 0; row < page.rows().size(); row++) {
                    check(page.rows().get(row).rank() == page.page() * 10 + row + 1, "wrong standing page rank");
                    observeStanding(page.rows().get(row));
                }
                check(pages.putIfAbsent(page.page(), page.rows()) == null, "duplicate standings page");
            }
            case ControlMessage.RoundEnd end -> {
                check(end.finalStandings().size() == Math.min(config.players(), HostRoundEngine.STANDINGS_BROADCAST_CAP),
                        "wrong final broadcast size");
                end.finalStandings().forEach(this::observeStanding);
                peer.roundEnded = true;
            }
            case ControlMessage.Kick kick -> throw new IllegalStateException("unexpected kick: " + kick.reason());
            default -> { }
        }
    }

    private int goalFrames() { return (config.warmupSeconds() + config.durationSeconds()) * FPS; }

    private void observeStanding(ControlMessage.StandingsRow row) {
        Peer owner = byName.get(row.displayName());
        check(owner != null && owner.finishSent && row.slot() == owner.connection.playerSlot()
                && row.bestTimeFrames() == goalFrames() - 2 && row.rank() >= 1 && row.rank() <= config.players()
                && "NONE".equals(row.verifyState()), "standing belongs to wrong participant/attempt");
        finished.add(owner.index);
    }

    private boolean measuring() {
        long now = System.currentTimeMillis();
        return window != null && now >= window.measureStartEpochMillis() && now < window.measureEndEpochMillis();
    }

    private static GhostFrame pose(int index, int frame) {
        int phase = frame % 30;
        return new GhostFrame(100 + index * 4 + (phase <= 15 ? phase : 30 - phase),
                100 + index * 2, 1, false, false, false, 2, false);
    }

    private <T> T future(CompletableFuture<T> pending) throws Exception {
        await(pending::isDone, "network reply");
        try {
            return pending.get();
        } catch (ExecutionException wrapped) {
            Throwable cause = wrapped.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw wrapped;
        }
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long until = RacingScaleTool.deadline(config.timeoutSeconds());
        while (true) {
            drain();
            if (condition.getAsBoolean()) return;
            check(System.nanoTime() < until, "timeout waiting for " + label);
            Thread.sleep(3);
        }
    }

    private void check(boolean condition, String message) {
        RacingScaleTool.check(condition, "room=" + roomIndex + ": " + message);
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        closed = true;
        allowDisconnect = true;
        Exception first = null;
        try { stopBackground(); } catch (Exception failure) { first = failure; }
        for (MasterClient master : masters) {
            try { master.close(); } catch (Exception failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        try { proxy.close(); } catch (Exception failure) {
            if (first == null) first = failure;
            else first.addSuppressed(failure);
        }
        if (first != null) throw first;
    }
}
