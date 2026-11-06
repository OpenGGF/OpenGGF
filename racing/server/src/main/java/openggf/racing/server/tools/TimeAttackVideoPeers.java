package openggf.racing.server.tools;

import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.MasterClient;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import openggf.racing.server.master.MasterConfig;
import openggf.racing.server.master.MasterServer;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Owned loopback peers for the October 2026 full-capacity Time Attack video.
 * One real engine player joins through the ordinary browser. The remaining peers
 * are authenticated protocol clients, retiming a recorded engine route into a
 * varied pack, not independent ROM simulations or verified results.
 */
public final class TimeAttackVideoPeers implements AutoCloseable {
    private final MasterServer server;
    private final LoopbackSourceProxy proxy;
    private final Path root;
    private final String fingerprint;
    private final int players;
    private final List<Peer> peers = new ArrayList<>();
    private final List<MasterClient> masters = new ArrayList<>();
    private final TimeAttackRouteTool.Route route;
    private final Map<Integer, ControlMessage.StandingsRow> standings = new HashMap<>();
    private String room;
    private long countdown;
    private long lastHeartbeat;
    private boolean liveFinished;
    private int elapsed = -1;
    private int peak;
    private int roundEnds;
    private int expectedPages;
    private final Set<Integer> pages = new HashSet<>();
    private int totalMembers;
    private long ghostEntries;
    private boolean closed;

    private static final class Peer {
        final RaceConnection connection;
        final GhostStreamPublisher publisher;
        final int index;
        boolean started;
        boolean sentFinish;
        int generated;
        int sourceFrame;
        int firstInput = -1;
        int members;
        final Set<Integer> memberSlots = new HashSet<>();
        Peer(RaceConnection connection, int index) {
            this.connection = connection;
            publisher = new GhostStreamPublisher(connection::sendBinary);
            this.index = index;
        }
    }

    public TimeAttackVideoPeers(Path root, Path liveIdentity, String fingerprint, int players,
                               TimeAttackRouteTool.Route route) throws Exception {
        if (players < 2 || players > 256) throw new IllegalArgumentException("players 2..256");
        this.root = root;
        this.fingerprint = fingerprint;
        this.players = players;
        this.route = Objects.requireNonNull(route);
        MasterConfig config = new MasterConfig(0, null, null, true, "identities.db", 0,
                UUID.randomUUID().toString(), 48, 10, 14, 50, 8, 8, false,
                2, 4, 30, 20, 30, 10000, 60);
        server = MasterServer.start(config, root.resolve("master"));
        try {
            proxy = new LoopbackSourceProxy(server.port(), players + 16);
        } catch (Exception | Error failure) {
            closeAfterFailure(failure, server);
            throw failure;
        }
        try {
            // Seed established admission identities for this demonstration.
            // Production address limits and maximum occupancy remain enabled.
            PlayerIdentity live = PlayerIdentity.loadOrCreate(liveIdentity);
            MasterClient admission = master(live, "Capture Player");
            admission.close();
        } catch (Exception | Error failure) {
            closeAfterFailure(failure, this);
            throw failure;
        }
    }

    public URI endpoint() { return URI.create("ws://127.0.0.1:" + proxy.port() + "/master"); }

    private MasterClient master(PlayerIdentity identity, String name) throws Exception {
        MasterClient client = awaitOwned(
                MasterClient.connect(endpoint().toString(), identity, name, fingerprint, null), MasterClient::close);
        masters.add(client);
        CompletableFuture<Void> promoted = new CompletableFuture<>();
        server.execute(() -> {
            try { server.establishForTest(identity.fingerprint()); promoted.complete(null); }
            catch (Throwable failure) { promoted.completeExceptionally(failure); }
        });
        promoted.get(10, TimeUnit.SECONDS);
        return client;
    }

    /** Fill every slot except the visible player's, pumping while identities join. */
    public void fill() throws Exception {
        for (int index = 0; index < players - 1; index++) {
            String name = index == 0 ? "Capture Host" : String.format(Locale.ROOT, "Bot %03d", index);
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(root.resolve("bot-" + index));
            MasterClient master = master(identity, name);
            if (room == null) {
                room = master.createRoom(new ControlMessage.RoomDescriptor("Full Capacity Time Attack",
                        "s1", 0, 0, "LOCKED", "sonic", players, false), "RELAY", 0, fingerprint)
                        .get(20, TimeUnit.SECONDS).roomId();
            }
            RaceConnection connection = awaitOwned(
                    master.joinRoom(room, identity, name, fingerprint), RaceConnection::close);
            peers.add(new Peer(connection, index));
            connection.sendControl(new ControlMessage.SelectCharacter("sonic"));
            pump();
        }
    }

    public void startRound(int seconds) {
        if (connectedCount() != players) throw new IllegalStateException("room is not full: " + connectedCount());
        peers.getFirst().connection.sendControl(new ControlMessage.Chat(players + " connected. Good luck!"));
        // Bots never ready up; nobody waits under AUTO.
        peers.getFirst().connection.sendControl(new ControlMessage.SetReadyRule("AUTO"));
        peers.getFirst().connection.sendControl(new ControlMessage.RoundConfigure(
                new ControlMessage.RoundConfig("s1", 0, 0, seconds, "LOCKED", "sonic")));
    }

    public void onFrame(GhostFrame pose) throws Exception {
        elapsed++;
        publish();
        pump();
    }

    private void publish() {
        if (countdown == 0 || System.currentTimeMillis() < countdown) return;
        for (Peer peer : peers) {
            if (!peer.started) {
                peer.connection.sendControl(new ControlMessage.AttemptStart(1));
                peer.publisher.beginAttempt(1);
                peer.started = true;
            }
            if (peer.sentFinish) continue;
            int source = GhostPackSchedule.admittedSourceFrame(peer.index, elapsed, route.frames(), peer.sourceFrame);
            peer.sourceFrame = source;
            if (peer.firstInput < 0 && source >= route.firstInput()) peer.firstInput = peer.generated;
            peer.publisher.onFrame(route.frames().get(source));
            peer.generated++;
            if (source == route.frames().size() - 1) {
                peer.publisher.finishAttempt();
                int first = peer.firstInput;
                int finish = peer.generated - 1;
                peer.connection.sendControl(new ControlMessage.AttemptFinish(1, finish - first,
                        first, finish, "ab".repeat(32),
                        HexFormat.of().formatHex(peer.publisher.streamHashSha256()), null));
                peer.sentFinish = true;
            }
        }
    }

    public void finishBots() { liveFinished = true; }

    /** Continue delayed tails, heartbeats and inbound processing during results. */
    public void tick() throws Exception {
        if (liveFinished) { elapsed++; publish(); }
        pump();
    }

    public void requestAllStandings() {
        expectedPages = (players + 9) / 10;
        for (int page = 0; page < expectedPages; page++) {
            peers.get(page % peers.size()).connection.sendControl(new ControlMessage.StandingsPageRequest(page));
        }
    }

    public void pump() throws Exception {
        if (proxy.failure() != null) throw proxy.failure();
        long now = System.currentTimeMillis();
        if (now - lastHeartbeat >= 3000 && !peers.isEmpty()) {
            // RelayAttach transfers this socket to the room protocol. The
            // master-owned relay maintains its own registry heartbeat.
            for (Peer peer : peers) peer.connection.sendControl(new ControlMessage.Ping(now));
            lastHeartbeat = now;
        }
        for (Peer peer : peers) {
            for (RaceClient.InboundEvent event : peer.connection.drainInbound()) {
                switch (event) {
                    case RaceClient.Control control -> {
                        switch (control.message()) {
                            case ControlMessage.RoomState state -> {
                                peer.memberSlots.clear();
                                state.players().forEach(player -> peer.memberSlots.add(player.slot()));
                                peer.members = peer.memberSlots.size();
                                totalMembers = Math.max(totalMembers, peer.members);
                                peak = Math.max(peak, peer.members);
                            }
                            case ControlMessage.PlayerUpsert upsert -> {
                                peer.memberSlots.add(upsert.player().slot());
                                peer.members = peer.memberSlots.size();
                                totalMembers = Math.max(totalMembers, peer.members);
                                peak = Math.max(peak, peer.members);
                            }
                            case ControlMessage.PlayerLeft left -> {
                                peer.memberSlots.remove(left.slot());
                                peer.members = peer.memberSlots.size();
                            }
                            case ControlMessage.RoundStart start -> countdown = start.countdownEndsAtHubMillis();
                            case ControlMessage.StandingsDelta delta -> observe(delta.rows());
                            case ControlMessage.StandingsPage page -> { observe(page.rows()); pages.add(page.page()); }
                            case ControlMessage.RoundEnd end -> { observe(end.finalStandings()); roundEnds++; }
                            case ControlMessage.Kick kick -> throw new IllegalStateException("bot kicked: " + kick.reason());
                            default -> { }
                        }
                    }
                    case RaceClient.GhostData data -> ghostEntries += data.aggregate().entries().size();
                    case RaceClient.Disconnected gone -> throw new IllegalStateException("bot disconnected: " + gone.reason());
                    default -> { }
                }
            }
            if (!peer.connection.isOpen()) throw new IllegalStateException("bot socket closed");
        }
    }

    private void observe(List<ControlMessage.StandingsRow> rows) {
        rows.forEach(row -> standings.put(row.slot(), row));
    }

    public int connectedCount() { return peers.isEmpty() ? 0 : peers.getFirst().members; }
    public int botCount() { return peers.size(); }
    public int finishedCount() { return standings.size(); }
    public int sentFinishes() { return (int) peers.stream().filter(peer -> peer.sentFinish).count(); }
    public boolean livePlayerFinished() { return standings.values().stream().anyMatch(row -> row.displayName().equals("Capture Player")); }
    public int liveRank() { return standings.values().stream().filter(row -> row.displayName().equals("Capture Player"))
            .mapToInt(ControlMessage.StandingsRow::rank).findFirst().orElse(0); }
    public int peakCount() { return peak; }
    public boolean allStandingsReceived() { return expectedPages > 0 && pages.size() == expectedPages && standings.size() == players; }
    public boolean roundEnded() { return roundEnds > 0; }
    public long receivedGhostEntries() { return ghostEntries; }
    public long tcpUpstreamBytes() { return proxy.upstreamBytes(); }
    public long tcpDownstreamBytes() { return proxy.downstreamBytes(); }

    private static <T> T awaitOwned(CompletableFuture<T> future,
                                    java.util.function.Consumer<T> release) throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            // A timed-out connection may still arrive after its owner has unwound.
            future.thenAccept(release);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private static void closeAfterFailure(Throwable failure, AutoCloseable resource) {
        try { resource.close(); }
        catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
    }

    private static Throwable closeResource(Throwable failure, AutoCloseable resource) {
        try { resource.close(); }
        catch (Throwable cleanup) {
            if (failure == null) return cleanup;
            failure.addSuppressed(cleanup);
        }
        return failure;
    }

    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        for (Peer peer : peers) failure = closeResource(failure, peer.connection::close);
        for (MasterClient master : masters) failure = closeResource(failure, master::close);
        failure = closeResource(failure, server);
        failure = closeResource(failure, proxy);
        if (!proxy.isTerminated()) failure = closeResource(failure, () -> {
            throw new IllegalStateException("proxy did not stop");
        });
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
