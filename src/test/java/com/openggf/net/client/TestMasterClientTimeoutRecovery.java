package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The transport deliberately never answers; all request/timeout behavior belongs to the real client. */
class TestMasterClientTimeoutRecovery {
    private enum CompletionPath { REPLY, CLOSE, SYNCHRONOUS_SEND }
    private enum QueuedSend {
        REQUEST, CONTROL, BINARY;

        void queue(MasterClient client) {
            switch (this) {
                case REQUEST -> client.requestJoin("second-room");
                case CONTROL -> client.sendControl(new ControlMessage.RoomLeave("second-room"));
                case BINARY -> client.sendBinary(new byte[] {1, 2});
            }
        }

        String event() { return this == BINARY ? "BINARY" : "TEXT"; }
    }

    private enum Operation {
        LIST, CREATE, JOIN;

        CompletableFuture<?> request(MasterClient client) {
            return switch (this) {
                case LIST -> client.listRooms("s3k", 0);
                case CREATE -> client.createRoom(new ControlMessage.RoomDescriptor(
                        "Test", "s3k", 0, 0, "OPEN", null, 8, false), "RELAY", 0, "test");
                case JOIN -> client.requestJoin("test-room");
            };
        }
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void missingReplyRetiresConnectionAndRejectsNextOperation(Operation operation) throws Exception {
        SilentSocket socket = new SilentSocket();
        MasterClient client = admittedClient(socket);
        try {
            CompletableFuture<?> missing = operation.request(client);
            // Accelerate the public future's deadline; do not wait five seconds per case.
            missing.orTimeout(20, TimeUnit.MILLISECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> missing.get(1, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());

            assertTrue(socket.aborted.await(1, TimeUnit.SECONDS),
                    "an ambiguous missing reply must retire the socket rather than poison later FIFO requests");
            assertFalse(client.isOpen());
            CompletableFuture<?> next = operation.request(client);
            ExecutionException nextFailure = assertThrows(ExecutionException.class,
                    () -> next.get(1, TimeUnit.SECONDS));
            assertTrue(nextFailure.getCause().getMessage().contains("reconnect"));
            assertEquals(1, socket.sent.size(), "no later request may enter the retired socket");
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void timeoutFailsOtherPendingRequestsAndFreshConnectionCanRecover(Operation operation) throws Exception {
        SilentSocket socket = new SilentSocket();
        MasterClient client = admittedClient(socket);
        SilentSocket replacementSocket = new SilentSocket();
        MasterClient replacement = admittedClient(replacementSocket);
        try {
            CompletableFuture<?> missing = operation.request(client);
            CompletableFuture<?> queued = client.requestJoin("waiting-room");
            missing.orTimeout(20, TimeUnit.MILLISECONDS);
            assertThrows(ExecutionException.class, () -> missing.get(1, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> queued.get(1, TimeUnit.SECONDS),
                    "other outstanding operations must not wait for their independent deadlines");

            // A late frame from the retired connection cannot revive a pending operation.
            receive(client, socket, new ControlMessage.RoomJoinRejected("late reply"));
            assertTrue(queued.isCompletedExceptionally());

            CompletableFuture<?> recovered = replacement.requestJoin("fresh-room");
            ControlMessage.RoomJoinResult reply = new ControlMessage.RoomJoinResult(
                    "fresh-room", "DIRECT", "localhost", 1234, "host", "test", "ab".repeat(32));
            receive(replacement, replacementSocket, reply);
            assertEquals("fresh-room", assertInstanceOf(ControlMessage.RoomJoinResult.class,
                    recovered.get(1, TimeUnit.SECONDS)).roomId());
            assertTrue(replacement.isOpen());
        } finally {
            client.close();
            replacement.close();
        }
    }

    @Test
    void cancelledRequestAlsoRetiresItsAmbiguousConnection() throws Exception {
        SilentSocket socket = new SilentSocket();
        MasterClient client = admittedClient(socket);
        CompletableFuture<?> cancelled = client.listRooms(null, 0);
        cancelled.cancel(false);
        assertTrue(socket.aborted.await(1, TimeUnit.SECONDS));
        assertFalse(client.isOpen());
        assertThrows(ExecutionException.class, () -> client.requestJoin("later").get(1, TimeUnit.SECONDS));
        assertEquals(1, socket.sent.size());
    }

    @Test
    void explicitCloseCompletesPendingOperations() throws Exception {
        MasterClient client = admittedClient(new SilentSocket());
        CompletableFuture<?> pending = client.requestJoin("waiting-room");
        client.close();
        assertFalse(client.isOpen());
        assertThrows(ExecutionException.class, () -> pending.get(1, TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @EnumSource(QueuedSend.class)
    void gracefulClosePreservesAcceptedSendOrder(QueuedSend queued) throws Exception {
        BackpressuredSocket socket = new BackpressuredSocket();
        MasterClient client = admittedClient(socket);
        try {
            client.requestJoin("first-room");
            queued.queue(client);
            client.close();
            assertEquals(List.of("TEXT"), socket.events,
                    "accepted sends remain behind the first incomplete transport write");

            socket.firstSend.complete(socket);

            assertEquals(List.of("TEXT", queued.event(), "CLOSE"), socket.events,
                    "graceful close must flush accepted writes before its close frame");
        } finally {
            socket.firstSend.complete(socket);
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(QueuedSend.class)
    void timeoutDiscardsQueuedSendsBeforeAbortEvenWhenContinuationReleasesTransport(QueuedSend queued)
            throws Exception {
        BackpressuredSocket socket = new BackpressuredSocket();
        MasterClient client = admittedClient(socket);
        AtomicBoolean releasedBeforeAbort = new AtomicBoolean();
        try {
            CompletableFuture<?> missing = client.requestJoin("first-room");
            queued.queue(client);
            CompletableFuture<?> probe = client.requestJoin("retirement-probe");
            probe.whenComplete((value, failure) -> {
                releasedBeforeAbort.set(socket.aborted.getCount() != 0);
                // Retirement completes pending operations before aborting. If
                // queued actions lose their failure guard, they execute here.
                socket.firstSend.complete(socket);
            });
            missing.orTimeout(20, TimeUnit.MILLISECONDS);
            assertThrows(ExecutionException.class, () -> missing.get(1, TimeUnit.SECONDS));
            assertTrue(socket.aborted.await(1, TimeUnit.SECONDS));

            assertTrue(releasedBeforeAbort.get(), "the test must exercise the pre-abort continuation window");
            assertEquals(List.of("TEXT", "ABORT"), socket.events,
                    "neither queued broker side effects nor the probe may be sent after timeout");
        } finally {
            socket.firstSend.complete(socket);
            client.close();
        }
    }

    @Test
    void failureEscalatesQueuedGracefulCloseToDiscard() throws Exception {
        BackpressuredSocket socket = new BackpressuredSocket();
        MasterClient client = admittedClient(socket);
        try {
            client.requestJoin("first-room");
            client.sendControl(new ControlMessage.RoomLeave("second-room"));
            client.close();
            Method fail = MasterClient.class.getDeclaredMethod("fail", WebSocket.class,
                    CompletableFuture.class, Throwable.class);
            fail.setAccessible(true);
            fail.invoke(client, socket, CompletableFuture.completedFuture(client),
                    new IllegalStateException("transport failed while closing"));

            socket.firstSend.complete(socket);

            assertEquals(List.of("TEXT", "ABORT"), socket.events,
                    "failed retirement suppresses accepted writes and the queued graceful close frame");
        } finally {
            socket.firstSend.complete(socket);
            client.close();
        }
    }

    @ParameterizedTest
    @EnumSource(CompletionPath.class)
    void completionContinuationCanCoordinateWithAnotherClientOperation(CompletionPath path) throws Exception {
        SilentSocket socket = new SilentSocket();
        MasterClient client = admittedClient(socket);
        CompletableFuture<?> first = client.requestJoin("first-room");
        CompletableFuture<?> continuation = first.handle((value, failure) -> {
            CountDownLatch attemptingOperation = new CountDownLatch(1);
            CompletableFuture<Void> operationReturned = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> {
                attemptingOperation.countDown();
                try {
                    client.requestJoin("from-continuation");
                    operationReturned.complete(null);
                } catch (Throwable error) {
                    operationReturned.completeExceptionally(error);
                }
            });
            try {
                assertTrue(attemptingOperation.await(1, TimeUnit.SECONDS));
                operationReturned.get(1, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new AssertionError("a future continuation must not hold the client send lock", error);
            }
            return null;
        });
        try {
            CompletableFuture<Void> completing = CompletableFuture.runAsync(() -> {
                if (path == CompletionPath.CLOSE) {
                    client.close();
                    return;
                }
                Runnable reply = () -> {
                    try {
                        receive(client, socket, new ControlMessage.RoomJoinRejected("first rejected"));
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                };
                if (path == CompletionPath.SYNCHRONOUS_SEND) {
                    // A transport can call back synchronously before sendText returns.
                    // The outer request must not retain the lock around that send.
                    socket.onSend = ignored -> reply.run();
                    client.requestJoin("second-room");
                } else {
                    reply.run();
                }
            });
            continuation.get(4, TimeUnit.SECONDS);
            completing.get(4, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }

    private static MasterClient admittedClient(WebSocket socket) throws Exception {
        var constructor = MasterClient.class.getDeclaredConstructor(PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        MasterClient client = constructor.newInstance(null, "TEST", "test");
        set(client, "webSocket", socket);
        set(client, "joinAccepted", new ControlMessage.JoinAccepted("token", -1, null, null));
        set(client, "open", true);
        return client;
    }

    private static void set(MasterClient client, String name, Object value) throws Exception {
        Field field = MasterClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(client, value);
    }

    private static void receive(MasterClient client, WebSocket socket, ControlMessage message) throws Exception {
        Method method = MasterClient.class.getDeclaredMethod("receiveText", WebSocket.class, String.class,
                CompletableFuture.class);
        method.setAccessible(true);
        method.invoke(client, socket, new ControlJsonCodec().encode("token", message),
                CompletableFuture.completedFuture(client));
    }

    private static class SilentSocket implements WebSocket {
        final List<String> sent = new ArrayList<>();
        final CountDownLatch aborted = new CountDownLatch(1);
        volatile Consumer<String> onSend;
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            Consumer<String> callback = onSend;
            onSend = null;
            if (callback != null) callback.accept(data.toString());
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) { return CompletableFuture.completedFuture(this); }
        @Override public void request(long n) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return aborted.getCount() == 0; }
        @Override public boolean isInputClosed() { return aborted.getCount() == 0; }
        @Override public void abort() { aborted.countDown(); }
    }

    private static final class BackpressuredSocket extends SilentSocket {
        final CompletableFuture<WebSocket> firstSend = new CompletableFuture<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            events.add("TEXT");
            return events.size() == 1 ? firstSend : CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            events.add("BINARY");
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            events.add("CLOSE");
            return CompletableFuture.completedFuture(this);
        }
        @Override public void abort() {
            events.add("ABORT");
            super.abort();
        }
    }
}
