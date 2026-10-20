package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TestMasterPowLifecycle {
    @Test
    void closeClearsCoalescedProofBeforeBackpressureReleases(@TempDir Path dir) throws Exception {
        var client = client(dir);
        var firstSend = new CompletableFuture<WebSocket>();
        var closed = new CountDownLatch(1);
        var socket = new Socket() {
            @Override public CompletableFuture<WebSocket> sendText(CharSequence text, boolean last) {
                super.sendText(text, last); return firstSend;
            }
            @Override public CompletableFuture<WebSocket> sendClose(int code, String reason) {
                closed.countDown(); return CompletableFuture.completedFuture(this);
            }
        };
        set(client, "webSocket", socket);
        var admitted = new CompletableFuture<MasterClient>();
        try {
            receive(client, socket, new ControlMessage.PowChallenge("JOIN", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            receive(client, socket, new ControlMessage.PowChallenge("IDENTITY", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            assertNotNull(field(client, "pendingPowText"));
            client.close();
            assertNull(field(client, "pendingPowText"), "retirement releases pending proof custody immediately");
            firstSend.complete(socket);
            assertTrue(closed.await(1, TimeUnit.SECONDS));
            assertEquals(1, socket.messages.size(), "retirement cannot dispatch the pending proof");
        } finally { client.close(); firstSend.complete(socket); }
    }

    @Test
    void solvedChallengeStormRetainsOnePendingProofWrite(@TempDir Path dir) throws Exception {
        var client = client(dir);
        var firstSend = new CompletableFuture<WebSocket>();
        var socket = new Socket() {
            @Override public CompletableFuture<WebSocket> sendText(CharSequence text, boolean last) {
                super.sendText(text, last);
                return messages.size() == 1 ? firstSend : CompletableFuture.completedFuture(this);
            }
        };
        set(client, "webSocket", socket);
        var admitted = new CompletableFuture<MasterClient>();
        try {
            receive(client, socket, new ControlMessage.PowChallenge("JOIN", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            assertEquals(1, socket.messages.size());
            receive(client, socket, new ControlMessage.PowChallenge("JOIN", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            Object pendingTail = field(client, "sendChain");
            for (int index = 0; index < 500; index++) {
                receive(client, socket, new ControlMessage.PowChallenge(
                        index == 499 ? "IDENTITY" : "JOIN", "AA==", 0), admitted);
                ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
                assertSame(pendingTail, field(client, "sendChain"),
                        "solved stale proofs must replace one pending write, not append retained continuations");
            }
            assertEquals(1, socket.messages.size());
            firstSend.complete(socket);
            ((CompletableFuture<?>) pendingTail).get(1, TimeUnit.SECONDS);
            assertEquals(2, socket.messages.size());
            var last = assertInstanceOf(ControlMessage.PowSolution.class,
                    new com.openggf.net.protocol.ControlJsonCodec().decode(socket.messages.getLast()).message());
            assertEquals("IDENTITY", last.kind());
        } finally { client.close(); firstSend.complete(socket); }
    }

    @Test
    void repeatedChallengesKeepOnlyOneQueuedSolverAndOnlyLatestProof(@TempDir Path dir) throws Exception {
        var client = client(dir);
        var socket = new Socket();
        set(client, "webSocket", socket);
        var admitted = new CompletableFuture<MasterClient>();
        var executor = (ThreadPoolExecutor) field(client, "powExecutor");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException ignored) {} });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        try {
            for (int i = 0; i < 100; i++) {
                receive(client, socket, new ControlMessage.PowChallenge(i == 99 ? "IDENTITY" : "JOIN", "AA==", 0), admitted);
                assertEquals(1, executor.getActiveCount());
                assertTrue(executor.getQueue().size() <= 1);
            }
            release.countDown();
            assertTrue(socket.sent.await(2, TimeUnit.SECONDS));
            assertEquals(1, socket.messages.size());
            var solution = assertInstanceOf(ControlMessage.PowSolution.class,
                    new com.openggf.net.protocol.ControlJsonCodec().decode(socket.messages.getFirst()).message());
            assertEquals("IDENTITY", solution.kind());
            assertEquals(0, solution.nonce());
        } finally { release.countDown(); client.close(); }
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void closingCancelsRunningHashWorkAndRetiresWorker(@TempDir Path dir) throws Exception {
        var client = client(dir);
        var socket = new Socket();
        var admitted = new CompletableFuture<MasterClient>();
        receive(client, socket, new ControlMessage.PowChallenge("JOIN", "AA==", 24), admitted);
        var executor = (ThreadPoolExecutor) field(client, "powExecutor");
        client.close();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS), "close must stop the solver, not just drop its output");
        assertEquals(0, socket.messages.size());
    }

    @Test
    void expiredDeadlineStopsHashing() {
        assertThrows(CancellationException.class, () -> ProofOfWork.solve(
                new byte[]{1}, 256, System.nanoTime() - 1));
    }

    @Test
    void hashingWorkerStopsAtDeadlineWithoutExternalInterrupt() throws Exception {
        var stopped = new CompletableFuture<Throwable>();
        Thread.ofVirtual().start(() -> {
            try { ProofOfWork.solve(new byte[]{1}, 256, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20)); }
            catch (Throwable failure) { stopped.complete(failure); }
        });
        assertInstanceOf(CancellationException.class, stopped.get(1, TimeUnit.SECONDS));
    }

    @Test
    void supersededProofWaitingBehindTransportBackpressureIsDiscarded(@TempDir Path dir) throws Exception {
        var client = client(dir);
        var socket = new Socket();
        set(client, "webSocket", socket);
        var heldSend = new CompletableFuture<Void>();
        set(client, "sendChain", heldSend);
        var admitted = new CompletableFuture<MasterClient>();
        try {
            receive(client, socket, new ControlMessage.PowChallenge("JOIN", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            receive(client, socket, new ControlMessage.PowChallenge("IDENTITY", "AA==", 0), admitted);
            ((Future<?>) field(client, "powTask")).get(1, TimeUnit.SECONDS);
            heldSend.complete(null);
            assertTrue(socket.sent.await(1, TimeUnit.SECONDS));
            assertEquals(1, socket.messages.size());
            var solution = assertInstanceOf(ControlMessage.PowSolution.class,
                    new com.openggf.net.protocol.ControlJsonCodec().decode(socket.messages.getFirst()).message());
            assertEquals("IDENTITY", solution.kind());
        } finally { heldSend.complete(null); client.close(); }
    }

    private static MasterClient client(Path dir) throws Exception {
        var constructor = MasterClient.class.getDeclaredConstructor(PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(PlayerIdentity.loadOrCreate(dir), "Alice", "build");
    }
    private static Object field(MasterClient client, String name) throws Exception {
        var field = MasterClient.class.getDeclaredField(name); field.setAccessible(true); return field.get(client);
    }
    private static void set(MasterClient client, String name, Object value) throws Exception {
        var field = MasterClient.class.getDeclaredField(name); field.setAccessible(true); field.set(client, value);
    }
    private static void receive(MasterClient client, Socket socket, ControlMessage challenge,
                                CompletableFuture<MasterClient> admitted) throws Exception {
        var receive = MasterClient.class.getDeclaredMethod("receiveAdmission", WebSocket.class,
                ControlMessage.class, CompletableFuture.class);
        receive.setAccessible(true); receive.invoke(client, socket, challenge, admitted);
    }
    @Test
    void excessiveChallengeFailsAdmissionWithoutStartingUnboundedWork(@TempDir Path dir) throws Exception {
        var constructor = MasterClient.class.getDeclaredConstructor(PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        var client = constructor.newInstance(PlayerIdentity.loadOrCreate(dir), "Alice", "build");
        var admitted = new CompletableFuture<MasterClient>();
        var receive = MasterClient.class.getDeclaredMethod("receiveAdmission", WebSocket.class,
                ControlMessage.class, CompletableFuture.class);
        receive.setAccessible(true);
        try {
            receive.invoke(client, new Socket(), new ControlMessage.PowChallenge("JOIN", "AA==", 25), admitted);
            assertThrows(ExecutionException.class, () -> admitted.get(1, TimeUnit.SECONDS));
        } finally { client.close(); }
    }

    @Test
    void interruptStopsTheActualHashLoop() throws Exception {
        var stopped = new CompletableFuture<Throwable>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try { ProofOfWork.solve(new byte[]{1}, 256); stopped.complete(null); }
            catch (Throwable failure) { stopped.complete(failure); }
        });
        worker.interrupt();
        assertInstanceOf(CancellationException.class, stopped.get(1, TimeUnit.SECONDS));
    }

    private static class Socket implements WebSocket {
        final java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CountDownLatch sent = new CountDownLatch(1);
        public CompletableFuture<WebSocket> sendText(CharSequence d, boolean l) {
            messages.add(d.toString()); sent.countDown(); return CompletableFuture.completedFuture(this);
        }
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer d, boolean l) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPing(ByteBuffer d) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPong(ByteBuffer d) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendClose(int c, String r) { return CompletableFuture.completedFuture(this); }
        public void request(long n) { }
        public String getSubprotocol() { return ""; }
        public boolean isOutputClosed() { return false; }
        public boolean isInputClosed() { return false; }
        public void abort() { }
    }
}
