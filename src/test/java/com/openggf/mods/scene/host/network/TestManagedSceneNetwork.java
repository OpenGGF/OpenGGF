package com.openggf.mods.scene.host.network;

import com.openggf.mods.scene.ScenePeer;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/** Real loopback framing, bounded queue behavior and cancellable selector resources. */
@Timeout(15)
class TestManagedSceneNetwork {
    private static int port() throws IOException {
        try (ServerSocket reservation = new ServerSocket(0)) {
            return reservation.getLocalPort();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean(), "Condition did not become true within five seconds");
    }

    private static Socket connectRaw(int port) throws Exception {
        // LISTENING describes the asynchronous phase, including bind admission.
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                socket.setSoTimeout(1000);
                return socket;
            } catch (ConnectException notBoundYet) {
                socket.close();
                if (System.nanoTime() >= deadline) throw notBoundYet;
                Thread.sleep(2);
            }
        }
    }

    private static ScenePeer connect(ManagedSceneNetwork network, int port) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true) {
            ScenePeer peer = network.connect("127.0.0.1", port);
            await(() -> peer.state() == ScenePeer.State.CONNECTED || peer.state() == ScenePeer.State.FAILED);
            if (peer.state() == ScenePeer.State.CONNECTED) return peer;
            if (System.nanoTime() >= deadline) fail(peer.error());
            Thread.sleep(2);
        }
    }

    private static void frame(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    private static int pending(ManagedSceneNetwork network, ScenePeer peer) {
        // Observe admission under the production monitor without racing another send
        // against the last incoming frame or draining the queue we are checking.
        try {
            var lock = ManagedSceneNetwork.class.getDeclaredField("lock");
            lock.setAccessible(true);
            var pending = peer.getClass().getDeclaredField("pending");
            pending.setAccessible(true);
            synchronized (lock.get(network)) { return pending.getInt(peer); }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void realPeersExchangeOrderedUnicodeWithLocalMonotonicReceiveTimes() throws Exception {
        try (ManagedSceneNetwork host = new ManagedSceneNetwork(); ManagedSceneNetwork client = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer receiver = host.host(port);
            ScenePeer sender = connect(client, port);
            await(() -> receiver.state() == ScenePeer.State.CONNECTED);
            long before = System.nanoTime();
            List<String> expected = List.of("", "one", "sitār 🎸\nnext", "界".repeat(4096));
            for (String text : expected) assertTrue(sender.send(text));
            List<ScenePeer.Message> messages = new ArrayList<>();
            await(() -> { messages.addAll(receiver.poll()); return messages.size() == expected.size(); });
            assertEquals(expected, messages.stream().map(ScenePeer.Message::text).toList());
            long last = before;
            for (ScenePeer.Message message : messages) {
                assertTrue(message.receivedNanos() >= last);
                assertTrue(message.receivedNanos() <= System.nanoTime());
                last = message.receivedNanos();
            }
            assertThrows(UnsupportedOperationException.class,
                    () -> receiver.poll().add(new ScenePeer.Message("x", 0)));
            assertTrue(receiver.send("reply"));
            List<ScenePeer.Message> replies = new ArrayList<>();
            await(() -> { replies.addAll(sender.poll()); return !replies.isEmpty(); });
            assertEquals("reply", replies.getFirst().text());
            assertNull(sender.error());
            assertNull(receiver.error());
        }
    }

    @Test
    void invalidArgumentsOpenNoEndpointAndOnlyOneEndpointMayBeActive() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            for (int invalid : new int[] {-1, 0, 65536}) {
                assertThrows(IllegalArgumentException.class, () -> network.host(invalid));
                assertThrows(IllegalArgumentException.class, () -> network.connect("localhost", invalid));
            }
            for (String invalid : new String[] {"", "example.com", "127.1", "256.0.0.1", "[::1]", "::1%lo", "http://127.0.0.1", "a:b"})
                assertThrows(IllegalArgumentException.class, () -> network.connect(invalid, 12345), invalid);
            assertThrows(IllegalArgumentException.class, () -> network.connect(null, 12345));
            ScenePeer first = network.host(port());
            assertFalse(first.send("before connected"));
            assertThrows(IllegalStateException.class, () -> network.host(12345));
            assertThrows(IllegalStateException.class, () -> network.connect("localhost", 12345));
            first.close();
            ScenePeer replacement = network.host(port());
            assertEquals(ScenePeer.State.LISTENING, replacement.state());
            network.close();
            assertEquals(ScenePeer.State.CLOSED, replacement.state());
            assertThrows(IllegalStateException.class, () -> network.host(12345));
        }
    }

    @Test
    void rejectsOversizedNullAndMalformedOutgoingTextWithoutFailingConnection() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket raw = connectRaw(port)) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                assertFalse(peer.send(null));
                assertFalse(peer.send("a".repeat(4097)));
                assertFalse(peer.send("\ud800"));
                assertFalse(peer.send("\udc00"));
                assertTrue(peer.send("valid"));
                var in = new java.io.DataInputStream(raw.getInputStream());
                assertEquals(5, in.readInt());
                assertEquals("valid", new String(in.readNBytes(5), StandardCharsets.UTF_8));
                assertEquals(ScenePeer.State.CONNECTED, peer.state());
            }
        }
    }

    @Test
    void incomingAndOutgoingShare256PendingSlotsAndPollRestoresCapacity() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket raw = connectRaw(port)) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                DataOutputStream out = new DataOutputStream(raw.getOutputStream());
                for (int i = 0; i < 256; i++) frame(out, Integer.toString(i));
                // Once all slots are incoming, sends cannot free themselves via the worker.
                await(() -> pending(network, peer) == 256);
                assertFalse(peer.send("probe"));
                assertEquals(ScenePeer.State.CONNECTED, peer.state());
                List<ScenePeer.Message> messages = peer.poll();
                assertEquals(256, messages.size());
                for (int i = 0; i < 256; i++) assertEquals(Integer.toString(i), messages.get(i).text());
                assertTrue(peer.send("after drain"));
                peer.close();
                assertTrue(peer.poll().isEmpty());
                assertFalse(peer.send("after close"));
            }
        }
    }

    @Test
    void stalledReaderCannotGrowTheOutgoingQueuePast256Messages() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork(); ServerSocket server = new ServerSocket(0)) {
            server.setReceiveBufferSize(1024);
            ScenePeer peer = network.connect("127.0.0.1", server.getLocalPort());
            try (Socket stalled = server.accept()) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                String large = "界".repeat(4096);
                int accepted = 0;
                while (accepted < 10000 && peer.send(large)) {
                    accepted++;
                    assertTrue(pending(network, peer) <= 256);
                }
                assertTrue(accepted > 0 && accepted < 10000, "bounded outgoing queue rejects backpressure");
                assertEquals(256, pending(network, peer));
                assertEquals(ScenePeer.State.CONNECTED, peer.state());
                assertTimeout(Duration.ofMillis(250), peer::close);
                assertTrue(peer.poll().isEmpty());
            }
        }
    }

    @Test
    void stalledWritesHaveADeadlineAndClearQueuedDataOnFailure() throws Exception {
        var deadlines = new ManagedSceneNetwork.Deadlines(5_000_000_000L, 5_000_000_000L, 200_000_000L);
        try (ManagedSceneNetwork network = new ManagedSceneNetwork(deadlines); ServerSocket server = new ServerSocket(0)) {
            server.setReceiveBufferSize(1024);
            ScenePeer peer = network.connect("127.0.0.1", server.getLocalPort());
            try (Socket stalled = server.accept()) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                String large = "界".repeat(4096);
                while (peer.send(large)) { }
                await(() -> peer.state() == ScenePeer.State.FAILED);
                assertEquals("Message send timed out", peer.error());
                assertEquals(0, pending(network, peer));
            }
        }
    }

    @Test
    void waitingConnectCanTimeOutOrBeCancelledWithoutWorkerLeaks() throws Exception {
        // Saturate a real loopback accept backlog; no external unreachable address
        // can reliably distinguish an immediate route refusal from a waiting connect.
        try (ServerSocket server = new ServerSocket(0, 1)) {
            List<Socket> backlog = new ArrayList<>();
            try {
                boolean saturated = false;
                for (int i = 0; i < 64; i++) {
                    Socket queued = new Socket();
                    try {
                        queued.connect(new InetSocketAddress("127.0.0.1", server.getLocalPort()), 50);
                        backlog.add(queued);
                    } catch (SocketTimeoutException full) {
                        queued.close();
                        saturated = true;
                        break;
                    }
                }
                assertTrue(saturated, "test prerequisite: loopback accept backlog must saturate");
                var deadlines = new ManagedSceneNetwork.Deadlines(5_000_000_000L, 200_000_000L, 5_000_000_000L);
                try (ManagedSceneNetwork network = new ManagedSceneNetwork(deadlines)) {
                    ScenePeer peer = network.connect("127.0.0.1", server.getLocalPort());
                    await(() -> peer.state() == ScenePeer.State.FAILED);
                    assertEquals("Connection timed out", peer.error());
                }
                try (ManagedSceneNetwork waiting = new ManagedSceneNetwork()) {
                    ScenePeer connecting = waiting.connect("127.0.0.1", server.getLocalPort());
                    Thread.sleep(50);
                    assertEquals(ScenePeer.State.CONNECTING, connecting.state());
                    assertTimeout(Duration.ofMillis(250), waiting::close);
                    assertEquals(ScenePeer.State.CLOSED, connecting.state());
                    awaitWorkerExit(waiting);
                }
            } finally {
                for (Socket queued : backlog) queued.close();
            }
        }
    }

    private static void awaitWorkerExit(ManagedSceneNetwork network) throws Exception {
        var thread = ManagedSceneNetwork.class.getDeclaredField("worker");
        thread.setAccessible(true);
        Thread worker = (Thread) thread.get(network);
        await(() -> !worker.isAlive());
    }

    @Test
    void cancellingAcceptReleasesTheListeningPortAndSelectorWorker() throws Exception {
        int port = port();
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            ScenePeer listener = network.host(port);
            Thread.sleep(50);
            network.close();
            assertEquals(ScenePeer.State.CLOSED, listener.state());
            awaitWorkerExit(network);
            try (ServerSocket reused = new ServerSocket(port)) {
                assertEquals(port, reused.getLocalPort());
            }
        }
    }

    @Test
    void firstAcceptedPeerClosesTheListenerAndNoSecondPeerCanConnect() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket first = connectRaw(port)) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                try (Socket second = new Socket()) {
                    assertThrows(IOException.class,
                            () -> second.connect(new InetSocketAddress("127.0.0.1", port), 250));
                }
                assertTrue(peer.send("first only"));
                var in = new java.io.DataInputStream(first.getInputStream());
                assertEquals(10, in.readInt());
                assertEquals("first only", new String(in.readNBytes(10), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void closingAnEstablishedPeerAllowsRehostingOnTheSamePort() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer first = network.host(port);
            try (Socket raw = connectRaw(port)) {
                await(() -> first.state() == ScenePeer.State.CONNECTED);
                first.close();
                assertEquals(-1, raw.getInputStream().read());
            }
            ScenePeer replacement = network.host(port);
            try (Socket raw = connectRaw(port)) {
                await(() -> replacement.state() == ScenePeer.State.CONNECTED);
                frame(new DataOutputStream(raw.getOutputStream()), "fresh");
                List<ScenePeer.Message> messages = new ArrayList<>();
                await(() -> { messages.addAll(replacement.poll()); return !messages.isEmpty(); });
                assertEquals("fresh", messages.getFirst().text());
            }
        }
    }

    @Test
    void incomingOverflowFailsExplicitlyRatherThanSilentlyDroppingMessages() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket raw = connectRaw(port)) {
                DataOutputStream out = new DataOutputStream(raw.getOutputStream());
                for (int i = 0; i < 257; i++) frame(out, "x");
                await(() -> peer.state() == ScenePeer.State.FAILED);
                assertEquals("Incoming message queue full", peer.error());
                assertTrue(peer.poll().isEmpty());
                peer.close();
                assertEquals(ScenePeer.State.FAILED, peer.state());
                assertEquals("Incoming message queue full", peer.error());
            }
        }
    }

    @Test
    void malformedLengthsEncodingCharacterCountsAndTruncatedFramesFail() throws Exception {
        List<byte[]> badFrames = List.of(
                java.nio.ByteBuffer.allocate(4).putInt(-1).array(),
                java.nio.ByteBuffer.allocate(4).putInt(16385).array(),
                new byte[] {0, 0, 0, 2, (byte) 0xc3, 0x28},
                java.nio.ByteBuffer.allocate(4 + 4097).putInt(4097).put("x".repeat(4097).getBytes(StandardCharsets.UTF_8)).array(),
                new byte[] {0, 0},
                new byte[] {0, 0, 0, 8, 65});
        for (byte[] frame : badFrames) {
            try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
                int port = port();
                ScenePeer peer = network.host(port);
                try (Socket raw = connectRaw(port)) {
                    raw.getOutputStream().write(frame);
                    raw.shutdownOutput();
                    await(() -> peer.state() == ScenePeer.State.FAILED);
                    assertNotNull(peer.error());
                    assertTrue(peer.error().length() <= 256);
                    assertFalse(peer.send("failed"));
                }
            }
        }
    }

    @Test
    void fragmentedFramesAreReassembledAndCleanEofRetainsCompleteMessages() throws Exception {
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket raw = connectRaw(port)) {
                byte[] bytes = new byte[] {0, 0, 0, 3, 97, 98, 99};
                for (byte value : bytes) {
                    raw.getOutputStream().write(value);
                    raw.getOutputStream().flush();
                    Thread.sleep(2);
                }
                raw.shutdownOutput();
                await(() -> peer.state() == ScenePeer.State.CLOSED);
                assertEquals(List.of("abc"), peer.poll().stream().map(ScenePeer.Message::text).toList());
                assertNull(peer.error());
            }
        }
    }

    @Test
    void acceptAndPartialMessageDeadlinesFailAndReleaseSockets() throws Exception {
        var deadlines = new ManagedSceneNetwork.Deadlines(200_000_000L, 200_000_000L, 200_000_000L);
        int port = port();
        try (ManagedSceneNetwork network = new ManagedSceneNetwork(deadlines)) {
            ScenePeer listener = network.host(port);
            await(() -> listener.state() == ScenePeer.State.FAILED);
            assertEquals("Timed out waiting for a peer", listener.error());
            ScenePeer receiver = network.host(port);
            try (Socket raw = connectRaw(port)) {
                raw.getOutputStream().write(0);
                await(() -> receiver.state() == ScenePeer.State.FAILED);
                assertEquals("Message receive timed out", receiver.error());
                assertEquals(-1, raw.getInputStream().read());
            }
        }
    }

    @Test
    void bindAndConnectionRefusalAreAsynchronousFailures() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0); ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            ScenePeer bound = network.host(occupied.getLocalPort());
            await(() -> bound.state() == ScenePeer.State.FAILED);
            assertNotNull(bound.error());
            ScenePeer refused = network.connect("localhost", port());
            await(() -> refused.state() == ScenePeer.State.FAILED);
            assertNotNull(refused.error());
        }
    }

    @Test
    void closeCancelsWaitingAcceptConnectAndEstablishedReadWithoutBlockingCaller() throws Exception {
        for (boolean hosting : new boolean[] {true, false}) {
            ManagedSceneNetwork network = new ManagedSceneNetwork();
            ScenePeer peer = hosting ? network.host(port()) : network.connect("192.0.2.1", 12345);
            assertTimeout(Duration.ofMillis(250), () -> {
                peer.poll();
                peer.send("x");
                network.close();
                network.close();
            });
            assertTrue(peer.state() == ScenePeer.State.CLOSED || peer.state() == ScenePeer.State.FAILED);
            assertTrue(peer.poll().isEmpty());
        }
        try (ManagedSceneNetwork network = new ManagedSceneNetwork()) {
            int port = port();
            ScenePeer peer = network.host(port);
            try (Socket raw = connectRaw(port)) {
                await(() -> peer.state() == ScenePeer.State.CONNECTED);
                assertTimeout(Duration.ofMillis(250), network::close);
                assertEquals(ScenePeer.State.CLOSED, peer.state());
                assertEquals(-1, raw.getInputStream().read(), "worker closes its established socket");
            }
        }
    }
}
