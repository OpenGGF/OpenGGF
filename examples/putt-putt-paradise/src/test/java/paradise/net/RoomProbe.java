package paradise.net;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import static paradise.net.GolfPacket.*;
import static paradise.net.ProtocolProbe.*;

/** Two production rooms using actual sockets; gameplay decisions remain in this caller fixture. */
public final class RoomProbe {
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
            var opened = new TurnOpened(new ShotId(host.state().match(), 1, 1, 1, 1), 100, 200, score, score);
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
                var opened = new TurnOpened(new ShotId(host.state().match(), 1, 1, 1, 0), 100, 200, one, two);
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
            var opened = new TurnOpened(turn, 100, 200, score, score);
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
        var other = new Fingerprints(1, "0.7.0", "different-engine", "paradise", "rules", Fingerprints.SONIC_2_SHA1, "competition", 800, 224);
        try (var host = GolfRoom.host(0, prints(), "sonic");
             var guest = GolfRoom.join("localhost", host.boundPort(), other, "sonic")) {
            pump(host, guest, () -> guest.state().ended(), "mismatched fingerprints rejected before play");
            check(host.state().held() && !host.state().ready(), "mismatch cannot release course");
            host.close(); guest.close();
            TransportProbe.await(() -> host.activeWorkers() == 0 && guest.activeWorkers() == 0, 3000, "mismatch fixture workers released");
        }
    }
}
