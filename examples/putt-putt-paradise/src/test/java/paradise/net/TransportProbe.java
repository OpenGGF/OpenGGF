package paradise.net;

import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import static paradise.net.GolfPacket.*;
import static paradise.net.ProtocolProbe.*;

/** Real localhost sockets and deterministic bounded-queue probes. */
public final class TransportProbe {
    static void await(BooleanSupplier condition, long millis, String message) throws Exception {
        long end = System.nanoTime() + Duration.ofMillis(millis).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        check(condition.getAsBoolean(), message);
    }
    static List<GolfConnection.Event> events(GolfConnection peer, int count) throws Exception {
        var all = new ArrayList<GolfConnection.Event>();
        await(() -> { all.addAll(peer.drain()); return all.size() >= count; }, 3000, "messages received");
        return all;
    }
    public static void undrainedAcceptedWorkersRemainOwned() throws Exception {
        int port;
        try (var listener = GolfListener.listen(0); var raw = new Socket("localhost", listener.boundPort())) {
            port = listener.boundPort();
            // No drain: all three accepted-socket workers still belong to the listener.
            await(() -> listener.activeWorkers() == 4, 3000, "undrained socket workers must be visible alongside accept worker");
            long started = System.nanoTime(); listener.finish(new Leave(MATCH, 0, "host left"));
            check(System.nanoTime() - started < Duration.ofSeconds(1).toNanos(), "early listener finish remains nonblocking");
            raw.setSoTimeout(3000);
            check(GolfCodec.read(raw.getInputStream()) instanceof Leave, "undrained peer receives terminal host leave");
            await(() -> listener.activeWorkers() == 0, 3000, "undrained socket workers retire before zero is reported");
        }
        try (var reused = GolfListener.listen(port)) { check(reused.boundPort() == port, "early exit releases listening port"); }
    }
    public static void terminalFlushRetainsWorkers() throws Exception {
        try (var listener = GolfListener.listen(0); var raw = new Socket()) {
            raw.setReceiveBufferSize(1024); raw.connect(new InetSocketAddress("localhost", listener.boundPort()));
            var accepted = new ArrayList<GolfConnection>();
            await(() -> { accepted.addAll(listener.drain()); return !accepted.isEmpty(); }, 3000, "terminal flush accepts slow reader");
            var connection = accepted.getFirst();
            try {
                connection.send(new ViewFrame(id(0, 0), 0, 0, new byte[GolfCodec.MAX_VIEW_BYTES]));
                await(() -> connection.stats().inFlightFrames() == 1, 3000, "terminal flush starts blocked full scene write");
                long started = System.nanoTime(); listener.finish(new Leave(MATCH, 0, "host left"));
                check(System.nanoTime() - started < Duration.ofSeconds(1).toNanos(), "slow terminal flush cannot block caller");
                await(() -> listener.activeWorkers() >= connection.activeWorkers() && connection.activeWorkers() >= 2,
                        1000, "listener retains every finishing socket worker");
                await(() -> listener.activeWorkers() == 0 && connection.activeWorkers() == 0, 8000, "terminal writer deadline retires tracked workers");
                check(connection.isClosed(), "deadline closes owned socket");
            } finally { connection.close(); }
        }
    }
    public static void creatorLoaderCanCloseImmediately() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((worker, failure) -> {
            if (worker.getName().startsWith("paradise-tcp-")) failures.add(failure);
            else if (previous != null) previous.uncaughtException(worker, failure);
        });
        try (var listener = GolfListener.listen(0); var raw = new Socket()) {
            raw.setReceiveBufferSize(1024); raw.connect(new InetSocketAddress("localhost", listener.boundPort()));
            var accepted = new ArrayList<GolfConnection>();
            await(() -> { accepted.addAll(listener.drain()); return !accepted.isEmpty(); }, 3000, "loader close accepts slow reader");
            var connection = accepted.getFirst();
            byte[] lateIncoming = GolfCodec.encode(new Resume(MATCH, 1));
            try {
                connection.send(new ViewFrame(id(0, 0), 0, 0, new byte[GolfCodec.MAX_VIEW_BYTES]));
                await(() -> connection.stats().inFlightFrames() == 1, 3000, "loader close begins blocked write");
                listener.finish(new Leave(MATCH, 0, "host left"));
                // This is the creator loader used by the engine-free harness, not the engine loader.
                ((URLClassLoader)TransportProbe.class.getClassLoader()).close();
                raw.getOutputStream().write(lateIncoming); // First Received helper is needed after the loader has closed.
                await(() -> !failures.isEmpty() || connection.isClosed() && connection.activeWorkers() == 0,
                        8000, "closed creator loader permits worker retirement");
                check(failures.isEmpty(), "creator loader close caused worker failure: " + failures);
                check(listener.activeWorkers() == 0, "closed loader leaves no listener workers");
            } finally { raw.close(); connection.close(); }
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous); }
    }
    public static void bufferedPacketsRemainReadableAfterLoaderClose() throws Exception {
        // Deliberately use raw wire bytes: neither creator packet constructors nor Codec's
        // pattern-switch encoder may warm record/enum classes before this fresh loader closes.
        byte[] buffered = rawPackets();
        try (var listener = GolfListener.listen(0); var raw = new Socket("localhost", listener.boundPort())) {
            await(() -> listener.activeWorkers() == 4, 3000, "fresh loader launches socket workers");
            listener.close();
            ((URLClassLoader)TransportProbe.class.getClassLoader()).close();
            var input = new ByteArrayInputStream(buffered);
            for (int type = 1; type <= 13; type++) check(GolfCodec.read(input) != null, "buffered packet remains decodable: " + type);
            check(GolfCodec.read(input) == null, "buffered packet boundary EOF after loader close");
            try {
                GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 0, 0, 2, 99, 1}));
                throw new AssertionError("invalid schema accepted after loader close");
            } catch (IOException expected) { }
            await(() -> listener.activeWorkers() == 0, 3000, "fresh loader close releases socket workers");
        }
    }
    private static byte[] rawPackets() throws IOException {
        var frames = new ByteArrayOutputStream(); var framed = new DataOutputStream(frames);
        for (int type = 1; type <= 13; type++) {
            var payload = new ByteArrayOutputStream(); var out = new DataOutputStream(payload);
            out.writeByte(1); out.writeByte(type);
            switch (type) {
                case 1 -> { rawFingerprints(out); rawText(out, "tails"); }
                case 2 -> { rawUuid(out); out.writeByte(1); rawUuid(out); rawFingerprints(out); rawText(out, "sonic"); }
                case 3 -> { rawId(out); out.writeInt(100); out.writeInt(200); rawScore(out); rawScore(out); }
                case 4 -> { rawId(out); out.writeByte(1); out.writeByte(25); out.writeShort(200); out.writeShort(300); }
                case 5 -> { rawId(out); out.writeLong(10); out.writeShort(500); }
                case 6 -> { rawId(out); out.writeLong(1); out.writeLong(10); out.writeInt(0); }
                case 7 -> { rawId(out); out.writeByte(0); out.writeInt(100); out.writeInt(200); rawScore(out); rawScore(out); out.writeByte(0); }
                case 8 -> { rawUuid(out); rawText(out, "paused"); }
                case 9 -> { rawUuid(out); out.writeLong(10); }
                case 10 -> { rawUuid(out); out.writeByte(0); rawText(out, "left"); }
                case 11 -> { rawUuid(out); out.writeByte(1); rawUuid(out); out.writeLong(0); out.writeLong(0); }
                case 12 -> { rawId(out); out.writeLong(1); out.writeLong(0); rawText(out, "charge"); }
                case 13 -> { rawId(out); rawText(out, "wrong turn"); }
            }
            framed.writeInt(payload.size()); payload.writeTo(framed);
        }
        return frames.toByteArray();
    }
    private static void rawUuid(DataOutputStream out) throws IOException { out.writeLong(1); out.writeLong(2); }
    private static void rawId(DataOutputStream out) throws IOException { rawUuid(out); out.writeByte(1); out.writeLong(1); out.writeLong(1); out.writeByte(1); }
    private static void rawScore(DataOutputStream out) throws IOException { out.writeInt(0); out.writeInt(0); out.writeByte(0); }
    private static void rawText(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII); out.writeByte(bytes.length); out.write(bytes);
    }
    private static void rawFingerprints(DataOutputStream out) throws IOException {
        out.writeByte(1);
        for (String text : new String[]{"0.7.0", "engine", "mod", "rules", "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9", "competition"}) rawText(out, text);
        out.writeShort(320); out.writeShort(224);
    }
    public static void run() throws Exception {
        var connections = new ArrayList<GolfConnection>();
        var listeners = new ArrayList<GolfListener>();
        int port;
        try (var host = GolfListener.listen(0)) {
            listeners.add(host);
            port = host.boundPort();
            try { GolfListener.listen(port); throw new AssertionError("open listener port was reusable"); }
            catch (BindException expected) { }
            try (var client = GolfConnection.connect("localhost", port)) {
                connections.add(client);
                var accepted = new ArrayList<GolfConnection>();
                await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "host accept");
                try (var server = accepted.getFirst()) {
                    connections.add(server);
                    var a = new Pause(MATCH, "one"); var b = new Resume(MATCH, 2);
                    check(client.send(a) && client.send(b), "nonblocking send accepted");
                    var incoming = events(server, 2);
                    check(incoming.get(0) instanceof GolfConnection.Received r && r.packet().equals(a), "ordered first command");
                    check(incoming.get(1) instanceof GolfConnection.Received r && r.packet().equals(b), "ordered second command");
                    check(server.send(new SoundCue(id(0, 0), 1, 0, "charge")), "reliable sound publish");
                    byte[] scene = new byte[100_000];
                    for (int i = 1; i <= 300; i++) check(server.send(new ViewFrame(id(0, 0), i, i, scene)), "frame publish");
                    var received = new ArrayList<GolfConnection.Event>();
                    await(() -> {
                        received.addAll(client.drain());
                        return received.stream().anyMatch(e -> e instanceof GolfConnection.Received r
                                && r.packet() instanceof ViewFrame f && f.revision() == 300);
                    }, 5000, "newest coalesced scene arrives");
                    check(received.stream().anyMatch(e -> e instanceof GolfConnection.Received r && r.packet() instanceof SoundCue),
                            "coalescing cannot discard sound cues");
                    check(server.stats().coalescedFrames() > 0, "real writer coalesced pending frames");
                }
            }
        }
        await(() -> connections.stream().allMatch(c -> c.activeWorkers() == 0)
                && listeners.stream().allMatch(l -> l.activeWorkers() == 0), 3000, "initial workers exited before port rebind");
        // Rebind using the same SO_REUSEADDR policy: accepted sockets can remain in TCP TIME_WAIT.
        try (var reuse = GolfListener.listen(port)) { listeners.add(reuse); check(reuse.boundPort() == port, "port released"); }

        var queue = new OutboundPackets();
        for (int i = 0; i < 64; i++) check(queue.offer(new Resume(MATCH, i)), "reliable message within count bound");
        check(!queue.offer(new Resume(MATCH, 64)), "reliable count overflow");
        check(queue.stats().reliableMessages() == 64, "reliable bound exact");
        queue.clear();
        for (int i = 0; i < 1000; i++) check(queue.offer(new ViewFrame(id(0, 0), i, i, new byte[0])), "coalesced queue bounded");
        check(queue.stats().pendingFrames() == 1 && queue.stats().coalescedFrames() == 999, "one pending full frame");
        check(((ViewFrame)GolfCodec.read(new ByteArrayInputStream(queue.take()))).revision() == 999, "pending frame is newest");
        queue.close();

        // A reconnect retries the same accepted request over a new socket, without a second acceptance.
        try (var host = GolfListener.listen(0)) {
            listeners.add(host);
            var receipts = new ShotReceipts(); var shot = request(id(0, 1)); receipts.openTurn(shot.id());
            var sessions = new ReconnectSessions(); UUID token = sessions.register(MATCH, 1);
            int acceptances = 0;
            GolfPacket.ShotAccepted originalAck;
            GolfConnection firstServer;
            try (var firstClient = GolfConnection.connect("localhost", host.boundPort())) {
                connections.add(firstClient);
                var accepted = new ArrayList<GolfConnection>();
                await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "first room socket");
                firstServer = accepted.getFirst(); connections.add(firstServer);
                firstClient.send(shot);
                var command = (ShotRequest)((GolfConnection.Received)events(firstServer, 1).getFirst()).packet();
                var result = receipts.accept(command, 1, 50);
                if (result.newlyAccepted()) acceptances++;
                originalAck = result.receipt().accepted(); firstServer.send(originalAck);
                check(((GolfConnection.Received)events(firstClient, 1).getFirst()).packet().equals(originalAck), "first acknowledgment delivered");
            }
            await(firstServer::isClosed, 3000, "disconnect holds room after acceptance");
            check(firstServer.drain().stream().anyMatch(e -> e instanceof GolfConnection.Disconnected), "host disconnect notification");
            sessions.disconnected(1, 100);
            try (var nextClient = GolfConnection.connect("localhost", host.boundPort())) {
                connections.add(nextClient);
                var accepted = new ArrayList<GolfConnection>();
                await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "reconnected room socket");
                try (var nextServer = accepted.getFirst()) {
                    connections.add(nextServer);
                    nextClient.send(new Reconnect(MATCH, 1, token, 0, 0)); nextClient.send(shot);
                    var commands = events(nextServer, 2);
                    check(sessions.reconnect((Reconnect)((GolfConnection.Received)commands.get(0)).packet(), 129), "room token accepted");
                    var result = receipts.accept((ShotRequest)((GolfConnection.Received)commands.get(1)).packet(), 1, 51);
                    if (result.newlyAccepted()) acceptances++;
                    check(result.status() == ShotReceipts.Status.DUPLICATE, "retried command uses cached receipt");
                    check(result.receipt().accepted().equals(originalAck), "original acceptance tick retained");
                    nextServer.send(result.receipt().accepted());
                    check(((GolfConnection.Received)events(nextClient, 1).getFirst()).packet().equals(originalAck), "cached receipt delivered on new socket");
                }
            }
            check(acceptances == 1, "one accepted shot across reconnect");
        }

        try (var host = GolfListener.listen(0); var raw = new Socket()) {
            listeners.add(host); raw.setReceiveBufferSize(1024);
            raw.connect(new InetSocketAddress("localhost", host.boundPort()));
            var accepted = new ArrayList<GolfConnection>();
            await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "outbound overflow accept");
            try (var peer = accepted.getFirst()) {
                connections.add(peer);
                for (int i = 0; i < 1_000_000 && peer.send(new Resume(MATCH, i)); i++) { }
                check(peer.isClosed(), "reliable overflow closes without waiting for slow reader");
                check(peer.drain().stream().anyMatch(e -> e instanceof GolfConnection.Disconnected d
                        && d.reason().equals("outbound queue overflow")), "reliable overflow reported for room HOLD");
                check(peer.stats().reliableMessages() == 0 && peer.stats().pendingFrames() == 0, "fault discards queued payloads");
            }
        }

        try (var host = GolfListener.listen(0); var raw = new Socket("localhost", host.boundPort())) {
            listeners.add(host);
            var accepted = new ArrayList<GolfConnection>();
            await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "overflow accept");
            try (var peer = accepted.getFirst()) {
                connections.add(peer);
                for (int i = 0; i < 257; i++) GolfCodec.write(raw.getOutputStream(), new Resume(MATCH, i));
                await(peer::isClosed, 3000, "incoming overflow disconnects");
                check(peer.drain().stream().anyMatch(e -> e instanceof GolfConnection.Disconnected d
                        && d.reason().equals("incoming queue overflow")), "room gets overflow hold event");
                check(peer.stats().incomingCommands() <= 256, "incoming queue bounded");
            }
        }
        // The remote endpoint never reads; a blocked write must close within its own deadline.
        try (var host = GolfListener.listen(0); var raw = new Socket()) {
            listeners.add(host);
            raw.setReceiveBufferSize(1024); raw.connect(new InetSocketAddress("localhost", host.boundPort()));
            var accepted = new ArrayList<GolfConnection>();
            await(() -> { accepted.addAll(host.drain()); return !accepted.isEmpty(); }, 3000, "slow-reader accept");
            try (var peer = accepted.getFirst()) {
                connections.add(peer);
                byte[] large = new byte[GolfCodec.MAX_VIEW_BYTES];
                long end = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                for (long revision = 0; !peer.isClosed() && System.nanoTime() < end; revision++) {
                    peer.send(new ViewFrame(id(0, 0), revision, revision, large));
                    check(peer.stats().pendingFrames() <= 1, "slow reader pending bound");
                    check(peer.stats().reliableBytes() <= 4 * 1024 * 1024, "slow reader byte bound");
                    Thread.sleep(10);
                }
                check(peer.isClosed(), "slow-reader write deadline reached");
                check(peer.drain().stream().anyMatch(e -> e instanceof GolfConnection.Disconnected d
                        && d.reason().equals("write deadline exceeded")), "deadline reported to host");
            }
        }
        await(() -> connections.stream().allMatch(c -> c.activeWorkers() == 0)
                && listeners.stream().allMatch(l -> l.activeWorkers() == 0), 3000, "owned virtual socket workers released");
    }
}
