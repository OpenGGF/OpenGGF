package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.*;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class TestMasterClientTimeoutRecovery {
    private enum Operation { LIST, CREATE, JOIN }
    private static final ControlJsonCodec CODEC = new ControlJsonCodec();

    private CompletableFuture<?> request(MasterClient client, Operation operation) {
        return switch (operation) {
            case LIST -> client.listRooms(null, 0);
            case CREATE -> client.createRoom(new ControlMessage.RoomDescriptor(
                    "Test", "s3k", 0, 0, "OPEN", null, 8, false), "RELAY", 0, "test");
            case JOIN -> client.requestJoin("test");
        };
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void deadlineExpiresOnlyItsCorrelatedRequestAndSocketRemainsUsable(Operation operation) throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            CompletableFuture<?> timed = request(client, operation);
            long oldId = CODEC.decode(socket.sent.getFirst()).requestId();
            timed.orTimeout(20, TimeUnit.MILLISECONDS);
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> timed.get(1, TimeUnit.SECONDS)).getCause());
            assertTrue(client.isOpen());
            CompletableFuture<?> fresh = client.requestJoin("fresh");
            long freshId = socket.sent.stream().map(CODEC::decode)
                    .filter(frame -> frame.message() instanceof ControlMessage.RoomJoinRequest)
                    .reduce((a,b) -> b).orElseThrow().requestId();
            receive(client, socket, new ControlMessage.RoomJoinRejected("old"), oldId);
            assertFalse(fresh.isDone());
            receive(client, socket, result("fresh"), freshId);
            assertEquals("fresh", ((ControlMessage.RoomJoinResult) fresh.get(1, TimeUnit.SECONDS)).roomId());
            assertFalse(socket.aborted);
        }
    }

    @Test
    void sameTypeRepliesCanCompleteInReverseOrderAndDuplicatesCannotStealRequests() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var slow = client.createRoom(descriptor(), "RELAY", 0, "test");
            var fast = client.createRoom(descriptor(), "RELAY", 0, "test");
            long slowId = CODEC.decode(socket.sent.get(0)).requestId();
            long fastId = CODEC.decode(socket.sent.get(1)).requestId();
            receive(client, socket, new ControlMessage.RoomCreated("fast"), fastId);
            assertEquals("fast", fast.get(1, TimeUnit.SECONDS).roomId());
            assertFalse(slow.isDone());
            receive(client, socket, new ControlMessage.RoomCreated("fast"), fastId);
            assertFalse(slow.isDone());
            receive(client, socket, new ControlMessage.RoomCreateRejected("unavailable"), slowId);
            assertThrows(ExecutionException.class, () -> slow.get(1, TimeUnit.SECONDS));
            assertTrue(client.isOpen());
            assertTrue(socket.sent.stream().map(CODEC::decode).noneMatch(frame -> frame.message() instanceof ControlMessage.RoomLeave));
        }
    }

    @Test
    void canceledCreationSendsScopedCancelAndLateRoomIsRemovedWithoutPublishingIt() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var creation = client.createRoom(descriptor(), "RELAY", 0, "test");
            long id = CODEC.decode(socket.sent.getFirst()).requestId();
            creation.cancel(false);
            var canceled = assertInstanceOf(ControlMessage.MasterRequestCancel.class,
                    CODEC.decode(socket.sent.getLast()).message());
            assertEquals(id, canceled.requestId());
            receive(client, socket, new ControlMessage.RoomCreated("late"), id);
            assertInstanceOf(ControlMessage.RoomLeave.class, CODEC.decode(socket.sent.getLast()).message());
            assertTrue(creation.isCancelled());
            assertTrue(client.isOpen());
            var next = client.requestJoin("next");
            long nextId = CODEC.decode(socket.sent.getLast()).requestId();
            assertTrue(nextId > id);
            receive(client, socket, result("next"), nextId);
            assertEquals("next", next.get(1, TimeUnit.SECONDS).roomId());
        }
    }

    @Test
    void canceledListDoesNotRetireSocketAndLateReplyDoesNotCompleteNewList() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var old = client.listRooms(null, 0);
            long oldId = CODEC.decode(socket.sent.getLast()).requestId();
            old.cancel(false);
            var current = client.listRooms(null, 1);
            long id = CODEC.decode(socket.sent.getLast()).requestId();
            receive(client, socket, new ControlMessage.RoomListResult(List.of(), 0, 1), oldId);
            assertFalse(current.isDone());
            receive(client, socket, new ControlMessage.RoomListResult(List.of(), 1, 2), id);
            assertEquals(1, current.get(1, TimeUnit.SECONDS).page());
            assertTrue(client.isOpen());
        }
    }

    @Test
    void mismatchedReplyTypeFailsConnectionAndCannotCompleteAnotherOperation() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var creation = client.createRoom(descriptor(), "RELAY", 0, "test");
            long id = CODEC.decode(socket.sent.getLast()).requestId();
            var list = client.listRooms(null, 0);
            receive(client, socket, result("wrong-type"), id);
            assertTrue(creation.isCompletedExceptionally());
            assertTrue(list.isCompletedExceptionally());
            assertFalse(client.isOpen());
            assertTrue(socket.aborted);
        }
    }

    @Test
    void uncorrelatedMasterReplyIsAProtocolFailure() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var pending = client.requestJoin("test");
            receive(client, socket, result("test"), 0);
            assertTrue(pending.isCompletedExceptionally());
            assertFalse(client.isOpen());
        }
    }

    @Test
    void pendingMapIsBoundedAndCancellationReleasesCapacityWithoutReusingIds() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            List<CompletableFuture<?>> pending = new ArrayList<>();
            for (int i = 0; i < MasterClient.MAX_PENDING_REQUESTS; i++) pending.add(client.listRooms(null, i));
            assertTrue(client.listRooms(null, 1000).isCompletedExceptionally());
            long previous = CODEC.decode(socket.sent.getLast()).requestId();
            pending.getFirst().cancel(false);
            var next = client.listRooms(null, 1001);
            assertFalse(next.isDone());
            assertTrue(CODEC.decode(socket.sent.getLast()).requestId() > previous);
        }
    }

    @Test
    void inviteResolveAndOwnerProviderRepliesAreCorrelatedAndCarryRetryTiming() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var resolve = client.resolveInvite("7K3M9X");
            long resolveId = CODEC.decode(socket.sent.getLast()).requestId();
            var invite = client.inviteService("room").control("instance", 0, 1,
                    ControlMessage.InviteOperation.FETCH, true);
            long inviteId = CODEC.decode(socket.sent.getLast()).requestId();
            var reply = new ControlMessage.RoomInviteControlResult(RoomAccessPolicy.PUBLIC,
                    new ControlMessage.RoomInvite("7K3M9X", "instance", 0, 1, Long.MAX_VALUE));
            receive(client, socket, reply, inviteId);
            assertEquals(reply, invite.get(1, TimeUnit.SECONDS));
            assertFalse(resolve.isDone());
            receive(client, socket, new ControlMessage.RoomInviteRejected("unavailable", 3210), resolveId);
            RoomInviteRejectedException failure = assertInstanceOf(RoomInviteRejectedException.class,
                    assertThrows(ExecutionException.class, () -> resolve.get(1, TimeUnit.SECONDS)).getCause());
            assertEquals(3210, failure.retryAfterMillis());
            assertFalse(failure.toString().contains("7K3M9X"));
        }
    }

    @Test
    void closeCompletesPendingAndGracefulClosePreservesAcceptedWriteOrder() throws Exception {
        SilentSocket socket = new SilentSocket();
        socket.firstWrite = new CompletableFuture<>();
        MasterClient client = admittedClient(socket);
        var first = client.requestJoin("first");
        var second = client.requestJoin("second");
        client.close();
        assertTrue(first.isCompletedExceptionally());
        assertTrue(second.isCompletedExceptionally());
        assertEquals(List.of("TEXT"), socket.events);
        socket.firstWrite.complete(socket);
        assertEquals(List.of("TEXT", "TEXT", "CLOSE"), socket.events);
    }

    @Test
    void completionContinuationsRunWithoutHoldingTheRequestLock() throws Exception {
        SilentSocket socket = new SilentSocket();
        try (MasterClient client = admittedClient(socket)) {
            var first = client.requestJoin("first");
            long id = CODEC.decode(socket.sent.getLast()).requestId();
            var continuation = first.handle((value, failure) -> {
                CompletableFuture<Void> completed = new CompletableFuture<>();
                Thread.ofVirtual().start(() -> {
                    client.requestJoin("next");
                    completed.complete(null);
                });
                assertDoesNotThrow(() -> completed.get(1, TimeUnit.SECONDS));
                return null;
            });
            receive(client, socket, new ControlMessage.RoomJoinRejected("unavailable"), id);
            continuation.get(2, TimeUnit.SECONDS);
        }
    }

    static ControlMessage.RoomDescriptor descriptor() {
        return new ControlMessage.RoomDescriptor("Test", "s3k", 0, 0, "OPEN", null, 8, false);
    }

    static ControlMessage.RoomJoinResult result(String id) {
        return new ControlMessage.RoomJoinResult(id, "RELAY", null, 0, "host", "test", null,
                "instance", RoomAccessPolicy.PUBLIC, 0, 0, 0);
    }

    static MasterClient admittedClient(SilentSocket socket) throws Exception {
        var constructor = MasterClient.class.getDeclaredConstructor(PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        MasterClient client = constructor.newInstance(null, "TEST", "test");
        for (var entry : Map.<String,Object>of("webSocket", socket, "joinAccepted",
                new ControlMessage.JoinAccepted("token", -1, null, null), "open", true).entrySet()) {
            Field field = MasterClient.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(client, entry.getValue());
        }
        return client;
    }

    static void receive(MasterClient client, WebSocket socket, ControlMessage message, long requestId) throws Exception {
        Method method = MasterClient.class.getDeclaredMethod("receiveText", WebSocket.class, String.class, CompletableFuture.class);
        method.setAccessible(true);
        method.invoke(client, socket, CODEC.encode("token", message, requestId), CompletableFuture.completedFuture(client));
    }

    static class SilentSocket implements WebSocket {
        final List<String> sent = new CopyOnWriteArrayList<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile boolean aborted;
        CompletableFuture<WebSocket> firstWrite;
        Consumer<String> onSend;
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString()); events.add("TEXT");
            if (onSend != null) onSend.accept(data.toString());
            return firstWrite != null && sent.size() == 1 ? firstWrite : CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int status, String reason) { events.add("CLOSE"); return CompletableFuture.completedFuture(this); }
        @Override public void request(long count) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return aborted; }
        @Override public boolean isInputClosed() { return aborted; }
        @Override public void abort() { aborted = true; events.add("ABORT"); }
    }
}
