package paradise.net;

import java.io.IOException;
import java.util.*;
import static paradise.net.GolfPacket.*;

/**
 * Direct-connect room orchestration, confined to its caller's presentation thread.
 * tick only drains bounded socket mailboxes. No room method calls gameplay or writes a socket.
 * The host publishes turn/score/scene values and explicitly accepts validated shot intents.
 */
public final class GolfRoom implements AutoCloseable {
    public static final int MAX_EVENTS = 256;
    public static final int RECONNECT_SECONDS = 30;
    public static final String CONCEDED_REASON = "conceded";
    private static final long RETRY_NANOS = 250_000_000L;
    public sealed interface Event { }
    public record Received(GolfPacket packet) implements Event { }
    public record Disconnected(String reason) implements Event { }
    public record State(UUID match, UUID roomToken, boolean host, boolean connected, boolean ready,
                        boolean held, boolean ended, String message, String hostCharacter,
                        String guestCharacter, int localOwner, int owner, TurnOpened remoteTurnOpened,
                        Score player0, Score player1) { }
    private static final class Candidate {
        final GolfConnection connection;
        final long startedSeconds;
        String character;
        Candidate(GolfConnection connection, long startedSeconds) { this.connection = connection; this.startedSeconds = startedSeconds; }
    }
    private final boolean host;
    private final Fingerprints fingerprints;
    private final String address;
    private final int port;
    private final GolfListener listener;
    private final ShotReceipts receipts = new ShotReceipts();
    private final ReconnectSessions sessions = new ReconnectSessions();
    private final ViewRevisions revisions = new ViewRevisions();
    private final SoundCues sounds = new SoundCues();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final ArrayList<Candidate> candidates = new ArrayList<>(2);
    private final ArrayList<GolfConnection> retiring = new ArrayList<>(2);
    private final long startedNanos = System.nanoTime();
    private GolfConnection peer;
    private UUID match;
    private UUID roomToken;
    private String hostCharacter;
    private String guestCharacter;
    private boolean connected;
    private boolean ready;
    private boolean held = true;
    private boolean ended;
    private boolean localPaused;
    private boolean remotePaused;
    private String localPauseReason = "paused";
    private String remotePauseReason = "paused";
    private String message = "waiting for peer";
    private long disconnectSeconds = -1;
    private long retryAtNanos;
    private long tickOrdinal;
    private TurnOpened turn;
    private ViewFrame latestView;
    private ShotStatus latestStatus;
    private ShotControl pendingControl;
    private ShotRequest pendingRemote;
    private ShotRequest pendingGuest;
    private Score player0 = new Score(0, 0, false);
    private Score player1 = new Score(0, 0, false);
    private int owner;

    private GolfRoom(boolean host, GolfListener listener, String address, int port, Fingerprints fingerprints, String character) {
        new Hello(fingerprints, character); // Validate the same wire contract before allocating a room.
        if (fingerprints.protocol() != GolfCodec.SCHEMA) throw new IllegalArgumentException("unsupported room protocol");
        this.host = host; this.listener = listener; this.address = address; this.port = port; this.fingerprints = fingerprints;
        if (host) { match = UUID.randomUUID(); hostCharacter = character; }
        else { guestCharacter = character; connectGuest(); }
    }
    public static GolfRoom host(int port, Fingerprints fingerprints, String hostCharacter) throws IOException {
        new Hello(fingerprints, hostCharacter);
        if (fingerprints.protocol() != GolfCodec.SCHEMA) throw new IllegalArgumentException("unsupported room protocol");
        return new GolfRoom(true, GolfListener.listen(port), null, port, fingerprints, hostCharacter);
    }
    public static GolfRoom join(String address, int port, Fingerprints fingerprints, String guestCharacter) {
        return new GolfRoom(false, null, address, port, fingerprints, guestCharacter);
    }
    public int boundPort() { return host ? listener.boundPort() : port; }
    public State state() { return new State(match, roomToken, host, connected, ready, held, ended, message,
            hostCharacter, guestCharacter, host ? 0 : 1, owner, turn, player0, player1); }
    public List<Event> drainEvents() { var result = List.copyOf(events); events.clear(); return result; }
    public int activeWorkers() {
        // Host listener retains all accepted connections through worker retirement.
        // Counting room peer/candidates again would double-count the same workers.
        if (listener != null) return listener.activeWorkers();
        int count = peer == null ? 0 : peer.activeWorkers();
        for (Candidate candidate : candidates) count += candidate.connection.activeWorkers();
        for (GolfConnection connection : retiring) count += connection.activeWorkers();
        return count;
    }
    private long seconds() { return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000_000L); }

    /** No network waits, DNS or gameplay callbacks: all connection work is already on socket workers. */
    public void tick() {
        if (ended) return;
        tickOrdinal++;
        retiring.removeIf(connection -> connection.activeWorkers() == 0);
        if (peer != null) {
            GolfConnection current = peer;
            for (GolfConnection.Event event : current.drain()) {
                if (ended) break;
                if (event instanceof GolfConnection.Received received) {
                    if (host) fromGuest(received.packet()); else fromHost(received.packet());
                } else if (event instanceof GolfConnection.Disconnected disconnected && peer == current) lost(disconnected.reason());
                if (peer != current) break; // A fault revoked this socket; remaining values from its drained batch have no authority.
            }
        }
        if (ended) return;
        if (host) {
            for (GolfConnection connection : listener.drain()) {
                if (candidates.size() >= 2) connection.close(); else candidates.add(new Candidate(connection, seconds()));
            }
            for (Candidate candidate : List.copyOf(candidates)) {
                for (GolfConnection.Event event : candidate.connection.drain()) {
                    if (event instanceof GolfConnection.Received received) {
                        if (candidate.connection == peer) fromGuest(received.packet()); else handshake(candidate, received.packet());
                    } else {
                        // Name an install mismatch instead of silently waiting for a peer that can never join.
                        if (!ready && event instanceof GolfConnection.Disconnected lost && lost.reason().startsWith("incompatible"))
                            message = lost.reason();
                        candidates.remove(candidate); retire(candidate.connection);
                    }
                    if (!candidates.contains(candidate) && candidate.connection != peer) break;
                }
                if (candidates.contains(candidate) && seconds() - candidate.startedSeconds >= 5) {
                    candidate.connection.close(); candidates.remove(candidate); retire(candidate.connection);
                }
            }
        } else if (peer == null && roomToken != null && System.nanoTime() - retryAtNanos >= 0) connectGuest();
        if (disconnectSeconds >= 0 && seconds() - disconnectSeconds >= RECONNECT_SECONDS) end("reconnect window expired", false);
    }
    private void connectGuest() {
        peer = GolfConnection.connect(address, port); retryAtNanos = System.nanoTime() + RETRY_NANOS;
        peer.send(new Hello(fingerprints, guestCharacter));
        if (roomToken != null) peer.send(new Reconnect(match, 1, roomToken, Math.max(0, revisions.lastRevision()), Math.max(0, sounds.lastCue())));
        message = roomToken == null ? "connecting" : "reconnecting";
    }
    private void handshake(Candidate candidate, GolfPacket packet) {
        if (packet instanceof Hello hello) {
            if (!fingerprints.compatibleWith(hello.fingerprints())) { reject(candidate, "fingerprints mismatch"); return; }
            if (peer != null && !peer.isClosed()) { reject(candidate, "room occupied"); return; }
            candidate.character = hello.character();
            if (roomToken == null) {
                guestCharacter = hello.character(); roomToken = sessions.register(match, 1); assign(candidate);
            } else if (!Objects.equals(guestCharacter, hello.character())) reject(candidate, "reconnect character mismatch");
        } else if (packet instanceof Reconnect reconnect && candidate.character != null
                && reconnect.owner() == 1 && match.equals(reconnect.match())
                && sessions.reconnect(reconnect, seconds())) assign(candidate);
        else reject(candidate, "invalid room handshake");
    }
    private void reject(Candidate candidate, String reason) {
        candidate.connection.finish(new Leave(match, 0, reason)); candidates.remove(candidate); retire(candidate.connection);
        if (!ready) message = reason;
    }
    private void assign(Candidate candidate) {
        peer = candidate.connection; candidates.remove(candidate); connected = true; ready = false; held = true;
        peer.send(new Ready(match, 1, roomToken, fingerprints, hostCharacter)); message = "waiting for ready";
    }
    private void fromGuest(GolfPacket packet) {
        if (packet instanceof Ready response) {
            if (response.owner() != 1 || !match.equals(response.match()) || !roomToken.equals(response.roomToken())
                    || !fingerprints.compatibleWith(response.fingerprints()) || !guestCharacter.equals(response.character())) {
                violation("invalid ready"); return;
            }
            ready = true; connected = true; disconnectSeconds = -1;
            emit(packet); publishHold(); replay();
        } else if (packet instanceof ShotRequest request && ready && match.equals(request.id().match()) && request.id().owner() == 1) {
            var inspection = receipts.inspect(request, 1);
            if (inspection.status() == ShotReceipts.Status.DUPLICATE) replayReceipt(inspection.receipt());
            else if (inspection.status() != ShotReceipts.Status.ACCEPTED) send(new Rejected(request.id(), inspection.status().name()));
            else if (held) send(new Rejected(request.id(), "room held"));
            else if (pendingRemote == null) { pendingRemote = request; emit(request); }
            else if (!pendingRemote.equals(request)) send(new Rejected(request.id(), "conflicting pending shot"));
        } else if (packet instanceof ShotControl control && ready && match.equals(control.id().match()) && control.id().owner() == 1) {
            if (current(control.id()) && !held) emit(control);
            else if (latestStatus != null) send(latestStatus);
        } else if (packet instanceof Pause pause && match.equals(pause.match())) {
            // Only assigned fingerprint/token-validated peers reach this handler. Pause intent precedes Ready.
            remotePaused = true; remotePauseReason = pause.reason(); publishHold();
        } else if (packet instanceof Resume resume && match.equals(resume.match())) {
            remotePaused = false; publishHold();
        } else if (packet instanceof Leave leave && leave.owner() == 1 && match.equals(leave.match())) {
            emit(packet);
            if (CONCEDED_REASON.equals(leave.reason())) end(CONCEDED_REASON, true, leave);
            else end("guest left", true);
        } else violation("unauthorized guest packet");
    }
    private void fromHost(GolfPacket packet) {
        if (packet instanceof Leave leave && (leave.owner() == 0 || CONCEDED_REASON.equals(leave.reason()))
                && (match == null || match.equals(leave.match()))) {
            emit(packet); end(leave.reason(), false); return;
        }
        if (packet instanceof Ready response) {
            if (response.owner() != 1 || !fingerprints.compatibleWith(response.fingerprints())
                    || match != null && !match.equals(response.match()) || roomToken != null && !roomToken.equals(response.roomToken())) {
                end("invalid host ready", false); return;
            }
            match = response.match(); roomToken = response.roomToken(); hostCharacter = response.character();
            connected = true; ready = false; held = true;
            // Menu intent can change while disconnected. Publish it before Ready so the host cannot briefly unhold.
            send(localPaused ? new Pause(match, localPauseReason) : new Resume(match, tickOrdinal));
            send(new Ready(match, 1, roomToken, fingerprints, guestCharacter));
            emit(packet); return;
        }
        if (match == null) { violation("missing host handshake"); return; }
        if (packet instanceof Resume resume && match.equals(resume.match())) {
            connected = true; ready = true; remotePaused = false; held = localPaused;
            disconnectSeconds = -1; message = held ? localPauseReason : "ready"; emit(packet);
            if (!held && pendingGuest != null) send(pendingGuest);
            if (!held && pendingControl != null) send(pendingControl);
        } else if (packet instanceof Pause pause && match.equals(pause.match())) {
            connected = true; ready = true; remotePaused = true; held = true;
            disconnectSeconds = -1; message = pause.reason(); emit(packet);
        } else if (packet instanceof TurnOpened opened && match.equals(opened.id().match())) {
            if (turn != null && (opened.id().hole() < turn.id().hole()
                    || opened.id().hole() == turn.id().hole() && (opened.id().turn() < turn.id().turn()
                    || opened.id().turn() == turn.id().turn() && opened.id().shot() < turn.id().shot()))) return;
            boolean same = turn != null && turn.id().equals(opened.id());
            turn = opened; player0 = opened.player0(); player1 = opened.player1(); owner = opened.id().owner();
            revisions.begin(opened.id(), same ? revisions.lastRevision() : -1);
            sounds.begin(opened.id(), same ? sounds.lastCue() : -1);
            if (pendingGuest != null && !pendingGuest.id().equals(opened.id())) pendingGuest = null;
            if (!same) { latestStatus = null; pendingControl = null; }
            emit(packet);
        } else if (packet instanceof ShotAccepted accepted && current(accepted.id())) emit(packet);
        else if (packet instanceof ShotStatus status && current(status.id())) {
            latestStatus = status; pendingControl = null; emit(packet);
        }
        else if (packet instanceof TurnCommitted committed && current(committed.id())) {
            player0 = committed.player0(); player1 = committed.player1(); owner = committed.nextOwner();
            if (pendingGuest != null && pendingGuest.id().equals(committed.id())) pendingGuest = null;
            pendingControl = null;
            emit(packet);
        } else if (packet instanceof ViewFrame frame && revisions.accept(frame)) emit(packet);
        else if (packet instanceof SoundCue cue && sounds.accept(cue)) emit(packet);
        else if (packet instanceof Rejected rejected && current(rejected.id())) {
            pendingGuest = null; message = rejected.reason(); emit(packet);
        } else if (!(packet instanceof ShotAccepted || packet instanceof TurnCommitted || packet instanceof ViewFrame
                || packet instanceof SoundCue || packet instanceof Rejected || packet instanceof ShotStatus)) violation("unauthorized host packet");
    }
    private boolean current(ShotId id) { return turn != null && turn.id().equals(id); }
    private void send(GolfPacket packet) { if (peer != null && !peer.send(packet)) lost("outbound queue overflow"); }
    private void replay() {
        receipts.last().ifPresent(this::replayReceipt);
        if (turn != null) send(turn);
        receipts.current().ifPresent(this::replayReceipt);
        if (latestStatus != null) send(latestStatus);
        if (latestView != null) send(latestView);
        // Old charge cues are not replayed. Guest watermarks and held course state retain sound ownership.
    }
    private void replayReceipt(ShotReceipts.Receipt receipt) {
        send(receipt.accepted()); if (receipt.committed() != null) send(receipt.committed());
    }
    private void requireHost() { if (!host || ended) throw new IllegalStateException("active host room required"); }
    public void publishTurn(TurnOpened opened) {
        requireHost(); Objects.requireNonNull(opened);
        if (!match.equals(opened.id().match())) throw new IllegalArgumentException("room match");
        if (turn != null && turn.id().equals(opened.id())) {
            if (!turn.equals(opened)) throw new IllegalArgumentException("contradictory turn publication");
            return;
        }
        receipts.openTurn(opened.id()); turn = opened; latestView = null; latestStatus = null; pendingRemote = null;
        player0 = opened.player0(); player1 = opened.player1(); owner = opened.id().owner();
        if (ready) send(opened);
    }
    /** A guest retains exactly one locked request until the authoritative committed result arrives. */
    public boolean submitShot(ShotRequest request) {
        if (host || ended || !ready || held || !current(request.id()) || request.id().owner() != 1) return false;
        if (pendingGuest != null && !pendingGuest.equals(request)) throw new IllegalStateException("shot already submitted");
        pendingGuest = request; send(request); return true;
    }
    public ShotReceipts.Result acceptShot(ShotRequest request) { return acceptShot(request, tickOrdinal); }
    public boolean submitControl(ShotControl control) {
        if (host || ended || !ready || held || !current(control.id()) || control.id().owner() != 1) return false;
        if (pendingControl != null) return pendingControl.equals(control);
        pendingControl = control; send(control); return true;
    }
    public void publishStatus(ShotStatus status) {
        requireHost(); if (!current(status.id())) throw new IllegalArgumentException("status turn");
        latestStatus = status; if (ready) send(status);
    }
    public ShotReceipts.Result acceptShot(ShotRequest request, long acceptedTick) {
        requireHost(); Objects.requireNonNull(request);
        var inspection = receipts.inspect(request, request.id().owner());
        if (inspection.status() == ShotReceipts.Status.ACCEPTED && (!ready || held
                || request.id().owner() == 1 && !request.equals(pendingRemote))) return new ShotReceipts.Result(ShotReceipts.Status.WRONG_TURN, null);
        var result = receipts.accept(request, request.id().owner(), acceptedTick);
        if (result.newlyAccepted()) { pendingRemote = null; send(result.receipt().accepted()); emit(result.receipt().accepted()); }
        else if (result.status() == ShotReceipts.Status.DUPLICATE && ready) replayReceipt(result.receipt());
        return result;
    }
    /** Host refusal of a guest shot its rules cannot accept now (for example before readiness). */
    public void rejectRemote(ShotId id, String reason) {
        requireHost(); Objects.requireNonNull(id); GolfPacket.text(reason);
        if (pendingRemote != null && pendingRemote.id().equals(id)) pendingRemote = null;
        if (ready) send(new Rejected(id, reason));
    }
    public void publishCommitted(TurnCommitted committed) {
        requireHost(); receipts.commit(committed); player0 = committed.player0(); player1 = committed.player1(); owner = committed.nextOwner();
        if (ready) send(committed);
    }
    public void publishView(ViewFrame frame) {
        requireHost(); if (!current(frame.id())) throw new IllegalArgumentException("view turn");
        if (latestView != null && frame.revision() <= latestView.revision()) return;
        latestView = frame; if (ready) send(frame);
    }
    public void cue(SoundCue cue) {
        requireHost(); if (!current(cue.id())) throw new IllegalArgumentException("sound turn");
        if (ready) send(cue);
    }
    public void pause(String reason) {
        if (ended) return;
        GolfPacket.text(reason); localPaused = true; localPauseReason = reason;
        if (host) publishHold();
        else { held = true; message = reason; if (ready) send(new Pause(match, reason)); }
    }
    public void resume() {
        if (ended) return;
        localPaused = false;
        if (host) publishHold();
        else if (ready) send(new Resume(match, tickOrdinal)); // Remain held until the authoritative aggregate response.
    }
    /** Host publishes the aggregate pause state, retaining each owner's independent contribution. */
    private void publishHold() {
        boolean wasHeld = held;
        held = !ready || localPaused || remotePaused;
        if (!ready) return;
        message = held ? localPaused ? localPauseReason : remotePauseReason : "ready";
        GolfPacket control = held ? new Pause(match, message) : new Resume(match, tickOrdinal);
        send(control); emit(control);
        // A request offered before a later Pause in the same drained batch was never accepted.
        if (wasHeld && !held && pendingRemote != null) emit(pendingRemote);
    }
    /** Explicit local DNF. The bound owner survives terminal delivery; retained scores stay unchanged. */
    public void concede() {
        if (ended) return;
        Leave leave = match == null ? null : new Leave(match, host ? 0 : 1, CONCEDED_REASON);
        if (leave != null) emit(leave);
        end(CONCEDED_REASON, true, leave);
    }
    private void emit(GolfPacket packet) {
        if (packet instanceof ViewFrame) events.removeIf(event -> event instanceof Received received && received.packet() instanceof ViewFrame);
        add(new Received(packet));
    }
    private void add(Event event) {
        if (events.size() >= MAX_EVENTS) {
            events.clear(); held = true; ready = false; connected = false; message = "room event queue overflow";
            if (peer != null) peer.close(); events.addLast(new Disconnected(message));
        } else events.addLast(event);
    }
    private void retire(GolfConnection connection) {
        retiring.removeIf(old -> old.activeWorkers() == 0);
        if (!retiring.contains(connection) && retiring.size() < 2) retiring.add(connection);
    }
    private void violation(String reason) { if (peer != null) peer.close(); lost(reason); }
    private void lost(String reason) {
        if (ended) return;
        if (peer != null) { peer.close(); retire(peer); peer = null; }
        connected = false; ready = false; held = true; message = reason; pendingRemote = null;
        if (disconnectSeconds < 0) disconnectSeconds = seconds();
        if (host && roomToken != null) sessions.disconnected(1, seconds());
        if (!host && roomToken == null) {
            // An older or newer host cannot parse this build's Hello and simply closes.
            end(reason.equals("remote closed") || reason.equals("read failed") || reason.equals("write failed")
                    ? "host closed the connection - check matching versions" : reason, false);
            return;
        }
        retryAtNanos = System.nanoTime() + RETRY_NANOS; add(new Disconnected(reason));
    }
    private void end(String reason, boolean notify) {
        end(reason, notify, match == null ? null : new Leave(match, host ? 0 : 1, reason));
    }
    private void end(String reason, boolean notify, Leave leave) {
        if (ended) return;
        ended = true; held = true; connected = false; ready = false; message = reason;
        if (listener != null) { if (notify && leave != null) listener.finish(leave); else listener.close(); }
        if (peer != null) { if (notify && leave != null) peer.finish(leave); else peer.close(); }
        for (Candidate candidate : candidates) if (!notify) candidate.connection.close();
        pendingRemote = null; pendingGuest = null; latestView = null; receipts.clear(); sessions.clear();
        add(new Disconnected(reason));
    }
    @Override public void close() { end(host ? "host left" : "guest left", true); }
}
