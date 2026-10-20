package paradise.net;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import static paradise.net.GolfPacket.*;
import static paradise.net.ProtocolProbe.*;

/** Two production rooms using actual sockets; gameplay decisions remain in this caller fixture. */
public final class RoomProbe {
    private static void primeHostMailboxWithOrderedControls(Rooms rooms, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            rooms.host.tick(); rooms.host.resume();
            boolean[] delivered = {false};
            TransportProbe.await(() -> {
                rooms.guest.tick(); delivered[0] |= rooms.guest.drainEvents().stream().anyMatch(event ->
                        event instanceof GolfRoom.Received r && r.packet() instanceof Resume);
                return delivered[0];
            }, 3000, "priming control delivered");
        }
        check(mailbox(rooms.host).size() == count, "room retains the ordered priming controls");
    }

    public static void sendFailureCountOverflowCannotPublishAfterTerminal() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "send-failure room ready");
            var turn = new ShotId(host.state().match(), 1, 1, 1, 0); var score = new Score(0, 0, false);
            host.publishTurn(new TurnOpened(turn, 100, 200, 0, 5, score, score));
            pump(host, guest, () -> guest.state().remoteTurnOpened() != null, "send-failure turn");
            host.drainEvents(); guest.drainEvents();
            primeHostMailboxWithOrderedControls(rooms, 256);
            connection(host).close(); // Publication sees a failed send before tick observes disconnect.
            host.acceptShot(request(turn));
            var state = host.state(); var terminal = host.drainEvents();
            check(state.ended() && !state.ready() && !state.connected() && state.held()
                            && state.message().equals("room event queue overflow")
                            && terminal.size() == 1 && terminal.getFirst() instanceof GolfRoom.Disconnected d
                            && d.reason().equals("room event queue overflow"),
                    "an in-flight publication cannot append after count-overflow end: " + terminal);
            check(retainedBytes(host) == 0, "terminal drain releases in-flight failure accounting");
        }
    }

    public static void reconnectCandidateCountOverflowKeepsTerminalStateAndOrderingFinal() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "authenticated first connection");
            UUID match = guest.state().match(), token = guest.state().roomToken();
            host.drainEvents(); guest.drainEvents();
            // Fill through normal host publication, draining only the remote consumer.
            // This makes the reconnect batch reach the room count bound before its
            // outbound control queue could become a competing overflow mechanism.
            primeHostMailboxWithOrderedControls(rooms, 250);
            connection(guest).close(); // Do not tick the guest again: reconnect is the controlled raw peer below.
            TransportProbe.await(() -> { host.tick(); return !host.state().connected(); }, 3000, "host registers disconnect");
            TransportProbe.await(() -> host.activeWorkers() == 1, 3000, "original socket retires before reconnect");
            try (var raw = new java.net.Socket("127.0.0.1", host.boundPort())) {
                var bytes = new java.io.ByteArrayOutputStream();
                GolfCodec.write(bytes, new Hello(prints(), "tails"));
                GolfCodec.write(bytes, new Reconnect(match, 1, token, 0, 0));
                for (int i = 0; i < 130; i++) GolfCodec.write(bytes, new Ready(match, 1, token, prints(), "tails"));
                raw.getOutputStream().write(bytes.toByteArray());
                // Observe admission only: the actual tick must drain this entire
                // decoded socket batch and exercise the candidate-to-peer handoff.
                Field listenerField = GolfRoom.class.getDeclaredField("listener"); listenerField.setAccessible(true);
                GolfListener listener = (GolfListener)listenerField.get(host);
                Field acceptedField = GolfListener.class.getDeclaredField("accepted"); acceptedField.setAccessible(true);
                Field peersField = GolfListener.class.getDeclaredField("peers"); peersField.setAccessible(true);
                @SuppressWarnings("unchecked") var pending = (ArrayDeque<GolfConnection>)acceptedField.get(listener);
                Object peersLock = peersField.get(listener);
                var candidate = new GolfConnection[1];
                TransportProbe.await(() -> {
                    synchronized (peersLock) { candidate[0] = pending.peekFirst(); }
                    return candidate[0] != null && candidate[0].stats().incomingCommands() == 132;
                }, 3000, "complete reconnect candidate batch buffered below the transport count cap");
                host.tick();
                var state = host.state(); var terminal = host.drainEvents();
                check(state.ended() && !state.ready() && !state.connected() && state.held()
                                && state.message().equals("room event queue overflow")
                                && terminal.size() == 1 && terminal.getFirst() instanceof GolfRoom.Disconnected d
                                && d.reason().equals("room event queue overflow"),
                        "count-overflow terminal state/order must remain final: " + state + ", queued=" + terminal.size());
                check(candidate[0].isClosed() && retainedBytes(host) == 0, "terminal transport and accounting stay closed/empty");
                host.tick();
                check(host.state().equals(state) && host.drainEvents().isEmpty(), "later ticks cannot revive terminal state or delivery");
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static ArrayDeque<GolfRoom.Event> mailbox(GolfRoom room) throws Exception {
        Field field = GolfRoom.class.getDeclaredField("events"); field.setAccessible(true);
        return (ArrayDeque<GolfRoom.Event>)field.get(room);
    }
    private static int retainedBytes(GolfRoom room) throws Exception {
        Field field = GolfRoom.class.getDeclaredField("eventBytes"); field.setAccessible(true);
        return field.getInt(room);
    }
    private static void receiveView(Rooms rooms, ShotId id, long revision) throws Exception {
        var queue = mailbox(rooms.guest);
        check(connection(rooms.host).send(new ViewFrame(id, revision, revision, new byte[GolfCodec.MAX_VIEW_BYTES])), "send bounded view");
        pump(rooms.host, rooms.guest, () -> rooms.guest.state().ended() || queue.stream().anyMatch(event ->
                event instanceof GolfRoom.Received r && r.packet() instanceof ViewFrame view && view.revision() == revision), "view admitted or room overflow");
    }
    public static void roomByteOverflowClosesAndReleasesScenesWithTerminalDelivery() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "byte-bound room ready");
            var turn = new ShotId(host.state().match(), 1, 1, 1, 1); var score = new Score(0, 0, false);
            host.publishTurn(new TurnOpened(turn, 100, 200, 0, 5, score, score));
            pump(host, guest, () -> guest.state().remoteTurnOpened() != null, "byte-bound turn");
            host.drainEvents(); guest.drainEvents();
            var queue = mailbox(guest);
            int viewBytes = GolfCodec.encode(new ViewFrame(turn, 0, 0, new byte[GolfCodec.MAX_VIEW_BYTES])).length;
            for (int revision = 1; revision <= 3; revision++) {
                receiveView(rooms, turn, revision);
                check(!guest.state().ended() && retainedBytes(guest) == viewBytes,
                        "adjacent max-size replacements release old charges exactly");
            }
            check(queue.size() == 1, "one adjacent same-shot view retained");
            guest.drainEvents(); check(retainedBytes(guest) == 0, "ordinary scene drain releases charge");
            for (int i = 1; i <= 2; i++) {
                receiveView(rooms, turn, i + 3);
                var sound = new SoundCue(turn, i, 0, "charge"); host.cue(sound);
                pump(host, guest, () -> queue.stream().anyMatch(event -> event instanceof GolfRoom.Received r
                        && r.packet().equals(sound)), "ordered barrier follows scene");
            }
            receiveView(rooms, turn, 6);
            check(guest.state().ended(), "third near-max scene across barriers must close the room at its byte cap");
            check(connection(guest).isClosed(), "room overflow closes transport instead of dropping ordered data silently");
            check(retainedBytes(guest) <= 4 * 1024 * 1024 && queue.size() <= 256, "downstream mailbox remains bounded");
            var terminal = guest.drainEvents();
            check(terminal.stream().noneMatch(event -> event instanceof GolfRoom.Received r && r.packet() instanceof ViewFrame),
                    "overflow releases every scene payload");
            check(terminal.stream().filter(event -> event instanceof GolfRoom.Disconnected d
                    && d.reason().equals("room event queue overflow")).count() == 1, "explicit overflow terminal delivered once");
            check(retainedBytes(guest) == 0 && guest.drainEvents().isEmpty(), "drain releases terminal charge exactly once");
        }
    }
    public static void roomEndReleasesScenesAndPreservesOrderedSoundAndConcession() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "terminal-bound room ready");
            var turn = new ShotId(host.state().match(), 1, 1, 1, 1); var score = new Score(0, 0, false);
            host.publishTurn(new TurnOpened(turn, 100, 200, 0, 5, score, score));
            pump(host, guest, () -> guest.state().remoteTurnOpened() != null, "terminal-bound turn");
            host.drainEvents(); guest.drainEvents(); receiveView(rooms, turn, 1);
            var sound = new SoundCue(turn, 1, 0, "charge"); host.cue(sound);
            var queue = mailbox(guest);
            pump(host, guest, () -> queue.stream().anyMatch(event -> event instanceof GolfRoom.Received r
                    && r.packet().equals(sound)), "sound queued behind scene");
            host.concede(); pump(host, guest, () -> guest.state().ended(), "host concession delivered");
            check(queue.stream().noneMatch(event -> event instanceof GolfRoom.Received r && r.packet() instanceof ViewFrame),
                    "end releases retained scene payloads");
            var terminal = guest.drainEvents();
            check(terminal.size() == 3 && terminal.get(0) instanceof GolfRoom.Received r && r.packet().equals(sound)
                    && terminal.get(1) instanceof GolfRoom.Received leave && leave.packet() instanceof Leave l
                    && l.owner() == 0 && l.reason().equals("conceded") && terminal.get(2) instanceof GolfRoom.Disconnected,
                    "ordered sound and explicit concession survive terminal cleanup");
            check(retainedBytes(guest) == 0, "terminal drain releases all accounting");
        }
    }
    public static void roomCoalescesOnlyAdjacentSameShotViewsAcrossWireDelivery() throws Exception {
        GolfRoom guest = null;
        try (var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            guest = GolfRoom.join("127.0.0.1", server.getLocalPort(), prints(), "tails");
            GolfRoom consumer = guest;
            try (var raw = server.accept()) {
                raw.setSoTimeout(3000);
                check(GolfCodec.read(raw.getInputStream()) instanceof Hello, "real guest hello");
                GolfCodec.write(raw.getOutputStream(), new Ready(MATCH, 1, UUID.randomUUID(), prints(), "sonic"));
                TransportProbe.await(() -> { consumer.tick(); return consumer.state().roomToken() != null; }, 3000, "guest ready response");
                check(GolfCodec.read(raw.getInputStream()) instanceof Resume, "guest pause intent before ready");
                check(GolfCodec.read(raw.getInputStream()) instanceof Ready, "guest ready confirmation");
                var a = id(1, 1); var b = id(2, 0); var score = new Score(0, 0, false);
                GolfCodec.write(raw.getOutputStream(), new Resume(MATCH, 0));
                GolfCodec.write(raw.getOutputStream(), new TurnOpened(a, 100, 200, 0, 5, score, score));
                TransportProbe.await(() -> { consumer.tick(); return consumer.state().remoteTurnOpened() != null; }, 3000, "first turn");
                consumer.drainEvents();

                var a1 = new ViewFrame(a, 1, 1, new byte[0]); var a2 = new ViewFrame(a, 2, 2, new byte[0]);
                var sound = new SoundCue(a, 1, 0, "charge"); var a3 = new ViewFrame(a, 3, 3, new byte[0]);
                var pause = new Pause(MATCH, "hold"); var a4 = new ViewFrame(a, 4, 4, new byte[0]);
                var resume = new Resume(MATCH, 5); var a5 = new ViewFrame(a, 5, 5, new byte[0]);
                var result = new TurnCommitted(a, Outcome.SETTLED, 110, 200, score, score, 0);
                var a6 = new ViewFrame(a, 6, 6, new byte[0]);
                var turn = new TurnOpened(b, 120, 200, 0, 5, score, score);
                var b1 = new ViewFrame(b, 1, 7, new byte[0]); var b2 = new ViewFrame(b, 2, 8, new byte[0]);
                var accepted = new ShotAccepted(b, 9, 500); var b3 = new ViewFrame(b, 3, 9, new byte[0]);
                var status = new ShotStatus(b, ShotPhase.AIM, 3, 1); var b4 = new ViewFrame(b, 4, 10, new byte[0]);
                var sentinel = new Rejected(b, "barrier sentinel");
                var bytes = new java.io.ByteArrayOutputStream();
                for (GolfPacket packet : List.of(a1, a2, sound, a3, pause, a4, resume, a5, result, a6,
                        turn, b1, b2, accepted, b3, status, b4, sentinel)) GolfCodec.write(bytes, packet);
                raw.getOutputStream().write(bytes.toByteArray());
                // Hold the consumer's event drain across all transport batches, including every barrier.
                TransportProbe.await(() -> { consumer.tick(); return consumer.state().message().equals("barrier sentinel"); },
                        3000, "entire wire sequence consumed by production room");
                var expected = List.<GolfPacket>of(a2, sound, a3, pause, a4, resume, a5, result, a6, turn,
                        b2, accepted, b3, status, b4, sentinel);
                check(retainedBytes(consumer) == expected.stream().mapToInt(p -> GolfCodec.encode(p).length).sum(),
                        "room charges actual encoded sizes after adjacent drops and ordered barriers");
                var actual = packets(consumer);
                check(expected.equals(actual), "room must retain views on both sides of each ordered barrier: " + actual);
                check(retainedBytes(consumer) == 0, "ordered event drain releases exact charge");
            }
        } finally {
            if (guest != null) guest.close();
            GolfRoom owned = guest;
            TransportProbe.await(() -> owned == null || owned.activeWorkers() == 0, 3000, "wire room workers retire");
        }
    }
    public static void lanInvitationPrecedesSeatAndSurvivesReconnect() throws Exception {
        var host = GolfRoom.hostLan(0, prints(), "sonic");
        GolfRoom guest = null;
        try (host; var unanswered = new java.net.Socket("127.0.0.1", host.boundPort());
             var stranger = GolfConnection.connect("127.0.0.1#00000000000000000000000000000000", host.boundPort())) {
            unanswered.setSoTimeout(3000);
            check(unanswered.getInputStream().readNBytes(37).length == 37, "unanswered peer holds a concurrent fresh challenge");
            stranger.send(new Hello(prints(), "sonic"));
            TransportProbe.await(stranger::isClosed, 3000, "uninvited compatible peer is refused");
            TransportProbe.await(() -> host.activeWorkers() == 2, 1000, "wrong proof retires while unanswered peer remains pending");
            host.tick();
            check(host.state().roomToken() == null && !host.state().connected(), "failed proof cannot reserve first guest seat");
            guest = GolfRoom.join("127.0.0.1#" + host.inviteCode(), host.boundPort(), prints(), "tails");
            GolfRoom invited = guest;
            pump(host, invited, () -> host.state().ready() && invited.state().ready(), "invited room handshake");
            check(host.state().guestCharacter().equals("tails"), "intended guest owns seat");
            UUID token = host.state().roomToken();
            connection(invited).close();
            pump(host, invited, () -> !host.state().connected(), "invited disconnect");
            pump(host, invited, () -> host.state().ready() && invited.state().ready(), "reconnect proves invitation again");
            check(token.equals(host.state().roomToken()), "reconnect retains original room identity");
        } finally {
            host.close(); if (guest != null) guest.close();
            GolfRoom ownedGuest = guest;
            TransportProbe.await(() -> host.activeWorkers() == 0 && (ownedGuest == null || ownedGuest.activeWorkers() == 0),
                    3000, "LAN room workers retire");
        }
    }
    static void pump(GolfRoom host, GolfRoom guest, BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        boolean satisfied = condition.getAsBoolean();
        while (!satisfied && System.nanoTime() < deadline) {
            host.tick(); guest.tick(); Thread.sleep(2);
            satisfied = condition.getAsBoolean();
        }
        check(satisfied, message);
    }
    static List<GolfPacket> packets(GolfRoom room) {
        return room.drainEvents().stream().filter(e -> e instanceof GolfRoom.Received)
                .map(e -> ((GolfRoom.Received)e).packet()).toList();
    }
    static GolfConnection connection(GolfRoom room) throws Exception {
        Field field = GolfRoom.class.getDeclaredField("peer"); field.setAccessible(true);
        return (GolfConnection)field.get(room);
    }
    static final class Rooms implements AutoCloseable {
        final GolfRoom host = GolfRoom.host(0, prints(), "sonic");
        final GolfRoom guest = GolfRoom.join("localhost", host.boundPort(), prints(), "tails");
        Rooms() throws Exception { }
        @Override public void close() throws Exception {
            host.close(); guest.close();
            TransportProbe.await(() -> host.activeWorkers() == 0 && guest.activeWorkers() == 0, 3000, "fixture closes workers before its class loader");
        }
    }
    public static void guestResumeRetainsHostPause() throws Exception { overlappingPauses(true); }
    public static void rewindControlsAndBudgetsSurviveReconnect() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "rewind room ready");
            var id = new ShotId(host.state().match(), 1, 1, 1, 1); var score = new Score(0, 0, false);
            host.publishTurn(new TurnOpened(id, 100, 200, 0, 5, score, score));
            pump(host, guest, () -> guest.state().remoteTurnOpened() != null, "turn for rewind");
            var request = request(id); guest.submitShot(request);
            var requests = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { requests.addAll(packets(host)); return requests.contains(request); }, "shot accepted before control");
            check(host.acceptShot(request).newlyAccepted(), "one accepted shot");
            host.publishStatus(new ShotStatus(id, ShotPhase.WATCH, 3, 1));
            guest.drainEvents(); host.drainEvents();
            var control = new ShotControl(id, ShotAction.REWIND);
            check(guest.submitControl(control) && guest.submitControl(control), "duplicate control retained once");
            var controls = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { controls.addAll(packets(host)); return controls.contains(control); }, "rewind intent reaches host");
            check(controls.stream().filter(control::equals).count() == 1, "duplicate cannot create two control intents");
            host.publishStatus(new ShotStatus(id, ShotPhase.REWINDING, 2, 0));
            connection(guest).close();
            pump(host, guest, () -> host.state().held() && !host.state().connected(), "rewind disconnect holds world");
            var replay = new ArrayList<GolfPacket>();
            pump(host, guest, () -> {
                replay.addAll(packets(guest)); return host.state().ready() && guest.state().ready()
                        && replay.contains(new ShotStatus(id, ShotPhase.REWINDING, 2, 0));
            }, "reconnect retains playback phase and spent budgets");
            host.publishCommitted(new TurnCommitted(id, Outcome.REWOUND, 100, 200, score, score, 1));
            var retry = new ShotId(id.match(), 1, 1, 2, 1);
            host.publishTurn(new TurnOpened(retry, 100, 200, 0, 5, score, score));
            host.publishStatus(new ShotStatus(retry, ShotPhase.AIM, 2, 0));
            pump(host, guest, () -> retry.equals(guest.state().remoteTurnOpened().id()), "fresh retry identity on same turn");
            check(guest.state().player1().strokes() == 0 && guest.state().owner() == 1, "refund and ownership remain authoritative");
            check(!guest.submitControl(control), "stale control cannot alter retry");
        }
    }
    public static void hostResumeRetainsGuestPause() throws Exception { overlappingPauses(false); }
    static void overlappingPauses(boolean guestResumesFirst) throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "overlapping pause handshake");
            host.pause("host menu"); guest.pause("guest menu");
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 1; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "guest pause queued");
            host.tick(); guest.tick(); host.drainEvents(); guest.drainEvents();
            if (guestResumesFirst) {
                guest.resume();
                TransportProbe.await(() -> {
                    try { return connection(host).stats().incomingCommands() >= 1; }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }, 3000, "guest resume queued");
                host.tick();
            } else host.resume();
            check(host.state().held(), "one owner's resume cannot clear the other owner's pause");
            String remaining = guestResumesFirst ? "host menu" : "guest menu";
            var guestPackets = new ArrayList<GolfPacket>();
            pump(host, guest, () -> {
                guestPackets.addAll(packets(guest));
                return guestPackets.stream().anyMatch(p -> p instanceof Pause pause && pause.reason().equals(remaining));
            }, "server publishes the remaining hold instead of resume");
            check(guest.state().held(), "guest retains aggregate hold");
            if (guestResumesFirst) host.resume(); else guest.resume();
            pump(host, guest, () -> !host.state().held() && !guest.state().held(), "both owners cleared their pauses");
        }
    }
    public static void pausedPendingRequestIsReofferedOnce() throws Exception {
        var score = new Score(0, 0, false);
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "pending pause handshake");
            var opened = new TurnOpened(new ShotId(host.state().match(), 1, 1, 1, 1), 100, 200, 0, 5, score, score);
            host.publishTurn(opened);
            pump(host, guest, () -> opened.equals(guest.state().remoteTurnOpened()), "pending pause turn");
            host.drainEvents(); guest.drainEvents();
            var shot = request(opened.id()); check(guest.submitShot(shot), "request submitted before pause");
            guest.pause("menu");
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 2; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "request and pause share a drained batch");
            host.tick();
            check(host.state().held() && packets(host).stream().filter(shot::equals).count() == 1, "one request offered before held");
            check(!host.acceptShot(shot, 123).newlyAccepted(), "held caller cannot accept yet");
            guest.tick(); guest.resume();
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 1; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "pending resume queued");
            host.tick();
            check(packets(host).stream().filter(shot::equals).count() == 1, "unheld transition reoffers pending request once");
            connection(guest).send(new Resume(host.state().match(), 999));
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 1; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "duplicate resume queued");
            host.tick(); check(packets(host).stream().noneMatch(shot::equals), "already unheld resume does not reoffer");
            var accepted = host.acceptShot(shot, 123); check(accepted.newlyAccepted(), "reoffered shot accepted once");
            check(host.acceptShot(shot, 124).status() == ShotReceipts.Status.DUPLICATE, "same acceptance remains cached");
            var guestPackets = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { guestPackets.addAll(packets(guest)); return guestPackets.contains(accepted.receipt().accepted()); }, "cached acceptance reaches guest");
            check(packets(host).stream().noneMatch(shot::equals), "guest retry cannot create another acceptance intent");
        }
    }
    public static void pauseIntentResynchronizesAfterReconnect() throws Exception {
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "pause reconnect handshake");
            guest.pause("guest menu");
            pump(host, guest, () -> host.state().held() && guest.state().held(), "guest paused before disconnect");
            connection(guest).close();
            pump(host, guest, () -> !host.state().connected() && !guest.state().connected(), "paused peer disconnected");
            guest.resume(); // The local menu can close while its socket is reconnecting.
            pump(host, guest, () -> host.state().ready() && guest.state().ready()
                    && !host.state().held() && !guest.state().held(), "reconnect resynchronizes the current local pause intent");
            host.pause("host menu");
            pump(host, guest, () -> host.state().held() && guest.state().held(), "host paused before disconnect");
            connection(guest).close();
            pump(host, guest, () -> !host.state().connected() && !guest.state().connected(), "host-paused peer disconnected");
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "host-paused reconnect ready");
            check(host.state().held() && guest.state().held(), "guest reconnect cannot clear the host's pause");
            host.resume();
            pump(host, guest, () -> !host.state().held() && !guest.state().held(), "host alone clears its retained pause");
        }
    }
    public static void concessionsPreserveOwnerAndScores() throws Exception {
        for (int concedingOwner = 0; concedingOwner <= 1; concedingOwner++) {
            try (var rooms = new Rooms()) {
                var host = rooms.host; var guest = rooms.guest;
                pump(host, guest, () -> host.state().ready() && guest.state().ready(), "concession handshake");
                var one = new Score(5, 2, false); var two = new Score(7, 1, false);
                var opened = new TurnOpened(new ShotId(host.state().match(), 1, 1, 1, 0), 100, 200, 0, 5, one, two);
                host.publishTurn(opened);
                pump(host, guest, () -> opened.equals(guest.state().remoteTurnOpened()), "concession scores arrive");
                host.drainEvents(); guest.drainEvents();
                GolfRoom local = concedingOwner == 0 ? host : guest, remote = concedingOwner == 0 ? guest : host;
                GolfRoom.class.getMethod("concede").invoke(local);
                pump(host, guest, () -> remote.state().ended(), "concession terminates remote room");
                int owner = concedingOwner;
                var leaves = packets(remote).stream().filter(p -> p instanceof Leave).map(p -> (Leave)p).toList();
                check(leaves.stream().anyMatch(leave -> leave.owner() == owner && leave.reason().equals("conceded")), "concession owner survives terminal flush");
                check(packets(local).stream().anyMatch(p -> p instanceof Leave leave && leave.owner() == owner && leave.reason().equals("conceded")), "local caller retains explicit concession event");
                check(host.state().player0().equals(one) && guest.state().player1().equals(two), "concession does not fabricate scores");
                TransportProbe.await(() -> host.activeWorkers() == 0 && guest.activeWorkers() == 0, 3000, "concession releases workers");
            }
        }
    }
    public static void handshakePauseIntentPrecedesReady() throws Exception {
        var host = GolfRoom.host(0, prints(), "sonic");
        var client = GolfConnection.connect("localhost", host.boundPort());
        try {
            client.send(new Hello(prints(), "tails"));
            var readyPackets = new ArrayList<Ready>();
            TransportProbe.await(() -> {
                host.tick();
                for (var event : client.drain()) if (event instanceof GolfConnection.Received received && received.packet() instanceof Ready value)
                    readyPackets.add(value);
                return !readyPackets.isEmpty();
            }, 3000, "assigned socket receives ready challenge");
            var ready = readyPackets.getFirst();
            client.send(new Pause(ready.match(), "menu opened while connecting"));
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 1; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "pause intent precedes ready response");
            host.tick();
            check(host.state().connected() && !host.state().ready() && host.state().held(), "assigned pause intent keeps handshake held without revoking its socket");
            client.send(new Ready(ready.match(), 1, ready.roomToken(), prints(), "tails"));
            TransportProbe.await(() -> { host.tick(); return host.state().ready(); }, 3000, "ready response completes held handshake");
            check(host.state().held(), "paused handshake never publishes an unheld world");
        } finally {
            host.close(); client.close();
            TransportProbe.await(() -> host.activeWorkers() == 0 && client.activeWorkers() == 0, 3000, "raw handshake fixture releases workers");
        }
    }
    /** Ready handoff: owner-bound, turn-scoped, held-safe and idempotent at the room boundary. */
    public static void readyHandoffIsOwnerBoundAndTurnScoped() throws Exception {
        var score = new Score(0, 0, false);
        try (var rooms = new Rooms()) {
            var host = rooms.host; var guest = rooms.guest;
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "ready-handoff handshake");
            var hostTurn = new ShotId(host.state().match(), 1, 1, 1, 0);
            host.publishTurn(new TurnOpened(hostTurn, 100, 200, 0, 5, score, score, true));
            host.publishStatus(new ShotStatus(hostTurn, ShotPhase.HANDOFF, 3, 1));
            pump(host, guest, () -> guest.state().remoteTurnOpened() != null && guest.state().remoteTurnOpened().readyRequired(),
                    "guest sees the host's held turn");
            check(!guest.submitControl(new ShotControl(hostTurn, ShotAction.READY)), "a spectator cannot confirm another owner's turn");
            var hostPackets = new ArrayList<GolfPacket>();
            for (int i = 0; i < 20; i++) { host.tick(); guest.tick(); hostPackets.addAll(packets(host)); Thread.sleep(2); }
            check(hostPackets.stream().noneMatch(p -> p instanceof ShotControl), "no spectator intent reaches the host");

            var turn = new ShotId(host.state().match(), 1, 2, 2, 1);
            host.publishTurn(new TurnOpened(turn, 300, 200, 0, 5, score, score, true));
            host.publishStatus(new ShotStatus(turn, ShotPhase.HANDOFF, 3, 1));
            pump(host, guest, () -> turn.equals(guest.state().remoteTurnOpened().id()), "guest-owned held turn");
            host.drainEvents(); guest.drainEvents();
            var ready = new ShotControl(turn, ShotAction.READY);
            check(guest.submitControl(ready) && guest.submitControl(ready), "a repeated ready is retained once");
            var controls = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { controls.addAll(packets(host)); return controls.contains(ready); }, "owner ready reaches host");
            for (int i = 0; i < 10; i++) { host.tick(); guest.tick(); controls.addAll(packets(host)); Thread.sleep(2); }
            check(controls.stream().filter(ready::equals).count() == 1, "one ready intent per press");

            // Stale ready for the previous turn: answered with status, never offered to gameplay.
            var replies = new ArrayList<GolfPacket>();
            connection(guest).send(new ShotControl(new ShotId(turn.match(), 1, 1, 1, 1), ShotAction.READY));
            pump(host, guest, () -> { replies.addAll(packets(guest)); return replies.stream().anyMatch(p -> p instanceof ShotStatus s && s.phase() == ShotPhase.HANDOFF); },
                    "stale ready is answered with the current status");
            check(packets(host).stream().noneMatch(p -> p instanceof ShotControl), "stale ready offers no intent");

            // While held, a ready waits for the next press instead of passing through.
            guest.pause("menu");
            pump(host, guest, () -> host.state().held() && guest.state().held(), "held handoff");
            connection(guest).send(ready);
            for (int i = 0; i < 20; i++) { host.tick(); guest.tick(); Thread.sleep(2); }
            check(packets(host).stream().noneMatch(p -> p instanceof ShotControl), "held room offers no ready");
            guest.resume();
            pump(host, guest, () -> !host.state().held() && !guest.state().held(), "handoff resumes");

            // A shot sent before readiness is refused by the host and can be retried later.
            host.drainEvents(); guest.drainEvents();
            var early = request(turn);
            check(guest.submitShot(early), "room forwards the early request for the host to judge");
            var early2 = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { early2.addAll(packets(host)); return early2.contains(early); }, "early request reaches host");
            host.rejectRemote(turn, "not ready");
            var refused = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { refused.addAll(packets(guest)); return refused.stream().anyMatch(p -> p instanceof Rejected r && r.reason().equals("not ready")); },
                    "guest is told the turn is not ready");
            host.publishStatus(new ShotStatus(turn, ShotPhase.AIM, 3, 1));
            pump(host, guest, () -> packets(guest).stream().anyMatch(p -> p instanceof ShotStatus s && s.phase() == ShotPhase.AIM),
                    "authoritative ready transition reaches guest");
            check(guest.submitShot(early), "a refused request can be submitted after readiness");
            var accepted = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { accepted.addAll(packets(host)); return accepted.contains(early); }, "post-ready request offered");
            check(host.acceptShot(early).newlyAccepted(), "exactly one accepted shot after readiness");

            // A forged ready for the host's own owner slot is a protocol violation.
            connection(guest).send(new ShotControl(new ShotId(turn.match(), 1, 2, 2, 0), ShotAction.READY));
            pump(host, guest, () -> !host.state().connected() || host.state().held(), "forged owner ready revokes the socket");
            check(packets(host).stream().noneMatch(p -> p instanceof ShotControl), "forged ready never reaches gameplay");
        }
    }

    /** Different protocol generations fail with a readable reason on both sides. */
    public static void incompatibleVersionsAreNamed() throws Exception {
        try (var host = GolfRoom.host(0, prints(), "sonic");
             var raw = new java.net.Socket("localhost", host.boundPort())) {
            byte[] older = GolfCodec.encode(new Hello(prints(), "tails")); older[4] = (byte) (GolfCodec.SCHEMA - 1);
            raw.getOutputStream().write(older); raw.getOutputStream().flush();
            // Force the fast-failure race: all accepted workers finish before the
            // presentation thread polls its listener for the first time.
            raw.setSoTimeout(3000);
            check(raw.getInputStream().read() == -1, "mismatched peer is closed");
            TransportProbe.await(() -> host.activeWorkers() == 1, 3000, "mismatched socket workers retire");
            TransportProbe.await(() -> { host.tick(); return host.state().message().startsWith("incompatible"); }, 3000,
                    "host names an older peer");
            check(!host.state().ready() && host.state().held(), "an incompatible peer cannot release the course");
        }
        try (var old = new java.net.ServerSocket(0)) {
            Thread.ofVirtual().start(() -> { try (var accepted = old.accept()) { accepted.getInputStream().read(); } catch (Exception ignored) { } });
            try (var guest = GolfRoom.join("localhost", old.getLocalPort(), prints(), "tails")) {
                TransportProbe.await(() -> { guest.tick(); return guest.state().ended(); }, 3000, "older host closes the handshake");
                check(guest.state().message().contains("matching versions"), "guest is told to match versions: " + guest.state().message());
            }
        }
    }

    public static void run() throws Exception {
        var score = new Score(0, 0, false);
        int port;
        try (var host = GolfRoom.host(0, prints(), "sonic");
             var guest = GolfRoom.join("localhost", host.boundPort(), prints(), "tails")) {
            port = host.boundPort();
            check(host.state().held() && guest.state().held(), "world held during first handshake");
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "both room peers ready");
            check(!host.state().held() && !guest.state().held(), "matched handshake releases host hold");
            check(host.state().guestCharacter().equals("tails") && guest.state().hostCharacter().equals("sonic"), "independent character choices");
            UUID match = host.state().match();
            check(match.equals(guest.state().match()) && guest.state().roomToken() != null, "assigned match and room token");
            var turn = new ShotId(match, 1, 0, 0, 1);
            var opened = new TurnOpened(turn, 100, 200, 0, 5, score, score);
            host.publishTurn(opened);
            pump(host, guest, () -> opened.equals(guest.state().remoteTurnOpened()), "authoritative turn arrives");
            host.drainEvents(); guest.drainEvents();
            var shot = request(turn); guest.submitShot(shot);
            var hostPackets = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { hostPackets.addAll(packets(host)); return hostPackets.stream().anyMatch(p -> p instanceof ShotRequest); }, "remote request delivered");
            var accepted = host.acceptShot(shot, 123);
            check(accepted.newlyAccepted(), "model caller accepts shot once");
            host.publishTurn(opened); // Identical publication must preserve its accepted receipt.
            pump(host, guest, () -> packets(guest).contains(accepted.receipt().accepted()), "acceptance delivered");
            host.drainEvents(); guest.drainEvents();
            connection(guest).close();
            pump(host, guest, () -> host.state().held() && !host.state().connected(), "disconnect holds an accepted shot");
            check(host.state().ready() == false, "disconnected room cannot simulate feedback/watch");
            var scene = new ViewFrame(turn, 5, 124, new byte[]{4, 5, 6}); host.publishView(scene);
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "automatic token reconnect");
            var replay = new ArrayList<GolfPacket>();
            pump(host, guest, () -> { replay.addAll(packets(guest)); return replay.contains(scene)
                    && replay.contains(accepted.receipt().accepted()); }, "current scene and cached receipt replay");
            check(packets(host).stream().noneMatch(p -> p instanceof ShotRequest), "reconnect cannot submit a second acceptance intent");
            var result = new TurnCommitted(turn, Outcome.SETTLED, 300, 400, score, new Score(1, 0, false), 0);
            host.publishCommitted(result);
            pump(host, guest, () -> result.player1().equals(guest.state().player1()), "host score commit arrives");
            guest.pause("menu");
            pump(host, guest, () -> host.state().held() && guest.state().held(), "guest menu pause holds host");
            guest.resume();
            pump(host, guest, () -> !host.state().held() && !guest.state().held(), "authorized menu resume");
            // A guest cannot inject authoritative results through its raw peer socket.
            connection(guest).send(result);
            connection(guest).send(new Ready(match, 1, guest.state().roomToken(), prints(), "tails"));
            TransportProbe.await(() -> {
                try { return connection(host).stats().incomingCommands() >= 2; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, 3000, "invalid result and trailing ready share one drained batch");
            host.tick();
            check(host.state().held() && !host.state().ready(), "discarded connection cannot restore ready in same batch");
            pump(host, guest, () -> host.state().held(), "forged guest commit rejected and held");
            check(packets(host).stream().noneMatch(p -> p instanceof TurnCommitted), "guest commit never reaches gameplay owner");
            pump(host, guest, () -> host.state().ready() && guest.state().ready(), "room can reconnect after protocol rejection");
            host.close();
            pump(host, guest, () -> guest.state().ended(), "host exit ends guest room without migration");
            guest.close();
            TransportProbe.await(() -> host.activeWorkers() == 0 && guest.activeWorkers() == 0, 3000, "room workers released");
        }
        try (var reused = GolfRoom.host(port, prints(), "tails")) { check(reused.boundPort() == port, "room port reusable"); }
        var other = new Fingerprints(GolfCodec.SCHEMA, "0.7.0", "different-engine", "paradise", "rules", Fingerprints.SONIC_2_SHA1, "competition", 800, 224);
        try (var host = GolfRoom.host(0, prints(), "sonic");
             var guest = GolfRoom.join("localhost", host.boundPort(), other, "sonic")) {
            pump(host, guest, () -> guest.state().ended(), "mismatched fingerprints rejected before play");
            check(host.state().held() && !host.state().ready(), "mismatch cannot release course");
            host.close(); guest.close();
            TransportProbe.await(() -> host.activeWorkers() == 0 && guest.activeWorkers() == 0, 3000, "mismatch fixture workers released");
        }
    }
}
