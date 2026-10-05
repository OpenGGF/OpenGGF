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
        }
    }
}
