package com.openggf.net.host.jdk;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.host.HostLinkAuthentication;
import com.openggf.net.host.HostMasterLink;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(30)
class TestJdkRaceHostLifecycle {
    private static final String BUILD = "test-build";
    private static final String PASSWORD = "original private room password";
    private static final String BINDING = AuthenticationTranscript.LOCAL_PLAINTEXT;
    private final ControlJsonCodec codec = new ControlJsonCodec();
    private JdkRaceHostServer server;
    private PlayerIdentity owner;
    @TempDir Path dir;

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void closeRetiresEveryTunnelAndErasesPrivateVerifier() throws Exception {
        start();
        Tunnel tunnel = new Tunnel();
        tunnel.admit(1, owner);
        tunnel.admit(2, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
        tunnel.open(3);
        Object verifier = preparedVerifier();
        assertEquals(2, onRoom(() -> server.room().playerCount()));
        assertFalse(allZero((byte[]) field(verifier, "hash")));

        server.close();

        assertEquals(0, server.room().playerCount());
        assertEquals(List.of(1, 2, 3), tunnel.closedGuests());
        assertErased(verifier);
        assertTrue(server.released());
        assertThrows(IllegalStateException.class, () -> server.execute(() -> fail("closed host ran work")));
        server.close();
        assertEquals(List.of(1, 2, 3), tunnel.closedGuests());
    }

    @Test
    void roomThreadCloseDiscardsLateCredentialAuthorityCompletion() throws Exception {
        start();
        Tunnel tunnel = new Tunnel();
        String token = tunnel.admit(1, owner);
        tunnel.admit(2, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
        CompletableFuture<ControlMessage.RoomInviteControlResult> authority = new CompletableFuture<>();
        CountDownLatch requested = new CountDownLatch(1);
        onRoom(() -> {
            server.room().setInviteService((instance, access, invite, operation, sharing) -> {
                requested.countDown();
                return authority;
            });
            return null;
        });
        try (RoomPassword replacement = RoomPassword.of("replacement private room password")) {
            tunnel.send(1, token, new ControlMessage.RoomPasswordChange(replacement));
        }
        assertTrue(requested.await(10, TimeUnit.SECONDS));
        Object verifier = onRoom(() -> {
            try { return field(field(server.room(), "passwordChange"), "verifier"); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        assertNotNull(verifier);

        onRoom(() -> { server.close(); return null; });
        await(server::released);
        authority.complete(new ControlMessage.RoomInviteControlResult(
                server.room().accessPolicy(), new ControlMessage.RoomInvite("7K3M9X",
                server.room().instanceId(), 1, 1, System.currentTimeMillis() + 600_000)));

        assertEquals(0, server.room().accessEpoch());
        assertEquals(0, server.room().playerCount());
        assertEquals(List.of(1, 2), tunnel.closedGuests());
        assertNull(tunnel.last(1, ControlMessage.RoomPasswordChanged.class));
        assertNull(tunnel.last(1, ControlMessage.RoomInvite.class));
        assertTrue(((Queue<?>) field(server.room(), "completions")).isEmpty());
        assertErased(preparedVerifier());
        assertErased(verifier);
    }

    @Test
    void closeDeadlineInterruptsRoomWorkWithoutCancelingRetirement() throws Exception {
        start();
        Tunnel tunnel = new Tunnel();
        tunnel.admit(1, owner);
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        server.execute(() -> {
            busy.countDown();
            try { resume.await(); }
            catch (InterruptedException e) { interrupted.set(true); }
        });
        try {
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            long started = System.nanoTime();
            server.close();
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(7), "bounded shutdown");
            await(server::released);
            assertTrue(interrupted.get());
            assertEquals(0, server.room().playerCount());
            assertEquals(List.of(1), tunnel.closedGuests());
            assertErased(preparedVerifier());
        } finally {
            resume.countDown();
        }
    }

    @Test
    void busyRoomRejectsExcessRetainedWorkAndDiscardsItOnClose() throws Exception {
        start();
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean ranQueued = new AtomicBoolean();
        server.execute(() -> {
            busy.countDown();
            try { resume.await(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            int accepted = 0;
            for (int i = 0; i < 600; i++) {
                try { server.execute(() -> ranQueued.set(true)); accepted++; }
                catch (IllegalStateException full) { break; }
            }
            assertTrue(accepted <= 512, "the running task and mailbox share one item limit");
            server.close();
            await(server::released);
            assertFalse(ranQueued.get(), "queued data is discarded before owner retirement");
        } finally {
            resume.countDown();
        }
    }

    @Test
    void fullSharedRoomMailboxWaitsForAnInnocentPeerWithoutAbortingIt() throws Exception {
        start();
        List<WebSocketConnection> peers = new java.util.ArrayList<>();
        List<Socket> sockets = new java.util.ArrayList<>();
        List<WorkBudget.Reservation> held = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                Socket socket = new Socket();
                sockets.add(socket);
                peers.add(new WebSocketConnection(server, socket, "127.0.0.1", 2000 + i,
                        JdkHostLimits.defaults()));
            }
            for (int i = 0; i < 4; i++) {
                for (int packet = 0; packet < 128; packet++) {
                    held.add(server.reserveRoom(peers.get(i), 1));
                    assertNotNull(held.getLast());
                }
            }
            CompletableFuture<WorkBudget.Reservation> waiting = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> waiting.complete(server.reserveRoom(peers.get(4), 1)));
            assertFalse(waiting.isDone(), "shared saturation should backpressure the fifth peer");
            assertFalse(peers.get(4).aborted());
            held.removeLast().close();
            try (WorkBudget.Reservation admitted = waiting.get(5, TimeUnit.SECONDS)) {
                assertNotNull(admitted);
                assertFalse(peers.get(4).aborted());
            }
        } finally {
            held.forEach(WorkBudget.Reservation::close);
            peers.forEach(peer -> { peer.abort(); server.connectionFinished(peer); });
            sockets.forEach(socket -> { try { socket.close(); } catch (java.io.IOException ignored) { } });
        }
    }

    @Test
    void deferredTunnelTasksStayAheadOfLaterTasksFromTheSameGuest() throws Exception {
        start();
        List<WebSocketConnection> peers = new java.util.ArrayList<>();
        List<Socket> sockets = new java.util.ArrayList<>();
        List<WorkBudget.Reservation> held = new java.util.ArrayList<>();
        Queue<Integer> order = new ConcurrentLinkedQueue<>();
        CountDownLatch completed = new CountDownLatch(2);
        WorkBudget.Account guest = WorkBudget.PROCESS.account(2, 1);
        WorkBudget.Reservation firstCell = WorkBudget.PROCESS.tryReserve(guest, guest,
                new WorkBudget.Cost(1, 0)).orElseThrow();
        WorkBudget.Reservation secondCell = WorkBudget.PROCESS.tryReserve(guest, guest,
                new WorkBudget.Cost(1, 0)).orElseThrow();
        WorkBudget.Task first = WorkBudget.Task.reserved(firstCell, List.of(firstCell),
                () -> { order.add(1); completed.countDown(); }, () -> { });
        WorkBudget.Task second = WorkBudget.Task.reserved(secondCell, List.of(secondCell),
                () -> { order.add(2); completed.countDown(); }, () -> { });
        try {
            for (int i = 0; i < 4; i++) {
                Socket socket = new Socket();
                sockets.add(socket);
                peers.add(new WebSocketConnection(server, socket, "127.0.0.1", 2000 + i,
                        JdkHostLimits.defaults()));
            }
            for (WebSocketConnection peer : peers) {
                for (int packet = 0; packet < 128; packet++) {
                    WorkBudget.Reservation admitted = server.reserveRoom(peer, 1);
                    assertNotNull(admitted);
                    held.add(admitted);
                }
            }
            synchronized (field(server, "mailboxLock")) {
                server.execute(first);
                held.removeLast().close();
                server.execute(second);
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2), List.copyOf(order),
                    "a later tunnel batch must not overtake a deferred earlier batch");
        } finally {
            first.close();
            second.close();
            held.forEach(WorkBudget.Reservation::close);
            peers.forEach(peer -> { peer.abort(); server.connectionFinished(peer); });
            sockets.forEach(socket -> { try { socket.close(); } catch (IOException ignored) { } });
        }
    }

    @Test
    void busyRoomBoundsOwnerControlsWhilePreservingPeerCleanupAdmission() throws Exception {
        start();
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        server.execute(() -> {
            busy.countDown();
            try { resume.await(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try (Socket socket = new Socket()) {
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            int accepted = 0;
            for (int i = 0; i < 600; i++) {
                try { server.executeControl(() -> { }); accepted++; }
                catch (IllegalStateException full) { break; }
            }
            assertTrue(accepted <= 512, "owner controls must have a finite retained-work limit");
            WebSocketConnection peer = new WebSocketConnection(server, socket,
                    "127.0.0.1", 1234, JdkHostLimits.defaults());
            try {
                assertTrue(server.submitConnectionControl(peer,
                    room -> room.onDisconnected(peer), true),
                    "disconnect needs capacity independent of ordinary owner controls");
            } finally { server.connectionFinished(peer); }
        } finally {
            resume.countDown();
        }
        server.close();
        assertTrue(server.retirementReceipt().authorityRetired());
    }

    @Test
    void loadedTrackProfileUsesItsReservedControlSlotAboveAFullOwnerMailbox()
            throws Exception {
        start();
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        server.execute(() -> {
            busy.countDown();
            try { resume.await(); }
            catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(busy.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 512; i++) server.executeControl(() -> { });
            TrackValidationProfile first = new TrackValidationProfile(100, 100, 16, 60);
            TrackValidationProfile latest = new TrackValidationProfile(320, 240, 32, 60);
            server.applyTrackValidationProfile(first);
            server.applyTrackValidationProfile(latest);
            resume.countDown();
            assertEquals(latest, onRoom(() -> {
                try { return field(field(server.room(), "hub"), "currentProfile"); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }));
        } finally {
            resume.countDown();
        }
    }

    @Test
    void masterLossRetiresListedAdmissionThroughSaturatedOwnerControls() throws Exception {
        start();
        onRoom(() -> { server.room().brokerPending(); return null; });
        HostMasterLink link = HostMasterLink.forServer(server, new HostMasterLink.MessageSink() {
            @Override public void sendControl(ControlMessage message) { }
            @Override public void sendBinary(byte[] data) { }
        });
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        server.execute(() -> {
            busy.countDown();
            try { resume.await(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            int accepted = 0;
            for (int i = 0; i < 600; i++) {
                try { server.executeControl(() -> { }); accepted++; }
                catch (IllegalStateException full) { break; }
            }
            assertEquals(512, accepted);
            assertDoesNotThrow(link::onMasterLost);
        } finally {
            resume.countDown();
        }
        await(() -> server.room().listenerAdmission() == RoomHost.ListenerAdmission.BROKER_UNAVAILABLE);
    }

    @Test
    void masterLossAfterHostCloseIsAlreadyRetired() throws Exception {
        start();
        HostMasterLink link = HostMasterLink.forServer(server, new HostMasterLink.MessageSink() {
            @Override public void sendControl(ControlMessage message) { }
            @Override public void sendBinary(byte[] data) { }
        });
        server.close();
        assertDoesNotThrow(link::onMasterLost);
        assertTrue(server.retirementReceipt().authorityRetired());
    }

    @Test
    void processSaturationCannotConsumeTheMasterLinkRetirementCell() throws Exception {
        start();
        CountDownLatch retired = new CountDownLatch(1);
        try (WorkBudget.Reservation held = saturateProcessItems()) {
            assertDoesNotThrow(() -> server.executeLinkRetirement(retired::countDown));
            assertTrue(retired.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,
                    () -> server.executeLinkRetirement(() -> { }));
        }
    }

    @Test
    void processSaturationCannotConsumeAnAcceptedPeersDisconnectCell() throws Exception {
        start();
        try (Socket socket = new Socket()) {
            WebSocketConnection peer = new WebSocketConnection(server, socket, "127.0.0.1", 1234,
                    JdkHostLimits.defaults());
            CountDownLatch disconnected = new CountDownLatch(1);
            try (WorkBudget.Reservation held = saturateProcessItems()) {
                assertTrue(server.submitConnectionControl(peer, room -> disconnected.countDown(), true));
                assertTrue(disconnected.await(5, TimeUnit.SECONDS));
            } finally { server.connectionFinished(peer); }
        }
    }

    @Test
    void processSaturationBackpressuresNewPeerAdmissionUntilCleanupCapacityReturns()
            throws Exception {
        start();
        try (Socket socket = new Socket()) {
            WorkBudget.Account filler = WorkBudget.PROCESS.account(8192, 1);
            WorkBudget.Reservation held = WorkBudget.PROCESS.tryReserve(filler, filler,
                    new WorkBudget.Cost(8192 - WorkBudget.PROCESS.retainedItems(), 0))
                    .orElseThrow();
            CompletableFuture<WebSocketConnection> admitted = new CompletableFuture<>();
            CountDownLatch attempting = new CountDownLatch(1);
            try {
                Thread.ofVirtual().start(() -> {
                    try {
                        attempting.countDown();
                        admitted.complete(new WebSocketConnection(server, socket,
                                "127.0.0.1", 99, JdkHostLimits.defaults()));
                    } catch (Throwable failure) {
                        admitted.completeExceptionally(failure);
                    }
                });
                assertTrue(attempting.await(5, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> admitted.get(100, TimeUnit.MILLISECONDS),
                        "another origin's retained work is backpressure");
            } finally {
                held.close();
            }
            WebSocketConnection peer = admitted.get(5, TimeUnit.SECONDS);
            try { assertFalse(peer.aborted()); }
            finally { peer.abort(); server.connectionFinished(peer); }
        }
    }

    @Test
    void processSaturationDefersAnAcceptedPeersOutboundFrameWithoutAbortingIt()
            throws Exception {
        start();
        try (Socket socket = new Socket()) {
            WebSocketConnection peer = new WebSocketConnection(server, socket,
                    "127.0.0.1", 100, JdkHostLimits.defaults());
            WorkBudget.Account outbound = (WorkBudget.Account) field(peer, "outboundWork");
            int deferredBefore = WorkBudget.DEFERRED.retainedItems();
            try {
                try (WorkBudget.Reservation held = saturateProcessItems()) {
                    for (int reply = 0; reply < 32; reply++) peer.sendBinary(new byte[0]);
                    assertFalse(peer.aborted());
                    assertEquals(0, outbound.retainedItems());
                    assertEquals(deferredBefore + 32, WorkBudget.DEFERRED.retainedItems());
                    assertTrue(WorkBudget.retainedProcessBytes() <= WorkBudget.PROCESS_BYTE_LIMIT);
                }
                await(() -> outbound.retainedItems() == 32);
                assertFalse(peer.aborted());
            } finally {
                peer.abort();
                server.connectionFinished(peer);
            }
            assertEquals(0, outbound.retainedItems());
            assertEquals(deferredBefore, WorkBudget.DEFERRED.retainedItems());
        }
    }

    @Test
    void stalledJdkPhysicalWriteExpiresIndependentlyOfReaderIdle() throws Exception {
        start();
        try (Socket socket = new Socket()) {
            WebSocketConnection peer = new WebSocketConnection(server, socket,
                    "127.0.0.1", 101, JdkHostLimits.defaults());
            WorkBudget.Account outbound = (WorkBudget.Account) field(peer, "outboundWork");
            CountDownLatch writing = new CountDownLatch(1);
            OutputStream held = new OutputStream() {
                @Override public void write(int value) throws IOException {
                    writing.countDown();
                    while (!peer.aborted()) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    throw new IOException("socket aborted");
                }
            };
            Method writeLoop = WebSocketConnection.class.getDeclaredMethod("writeLoop", OutputStream.class);
            writeLoop.setAccessible(true);
            Thread writer = Thread.ofVirtual().start(() -> {
                try { writeLoop.invoke(peer, held); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            try {
                peer.sendBinary(new byte[0]);
                assertTrue(writing.await(5, TimeUnit.SECONDS));
                await(peer::aborted);
                writer.join(5_000);
                assertFalse(writer.isAlive());
                assertEquals(0, outbound.retainedItems());
            } finally {
                peer.abort();
                writer.join(5_000);
                server.connectionFinished(peer);
            }
        }
    }

    @Test
    void fullDeferredReserveEvictsAHeavyJdkWriterBeforeAdmittingAnInnocentPeer()
            throws Exception {
        start();
        JdkHostLimits limits = JdkHostLimits.defaults();
        List<Socket> sockets = new java.util.ArrayList<>();
        List<WebSocketConnection> peers = new java.util.ArrayList<>();
        WorkBudget.Reservation filler = null;
        int deferredBefore = WorkBudget.DEFERRED.retainedItems();
        try {
            for (int i = 0; i < 3; i++) {
                Socket socket = new Socket();
                sockets.add(socket);
                peers.add(new WebSocketConnection(server, socket, "127.0.0.1", 110 + i, limits));
            }
            WorkBudget.Account source = WorkBudget.PROCESS.account(8192, 1);
            filler = WorkBudget.PROCESS.tryReserve(source, source,
                    new WorkBudget.Cost(8192 - WorkBudget.PROCESS.retainedItems(), 0))
                    .orElseThrow();
            for (int i = 0; i < 2; i++) {
                WebSocketConnection peer = peers.get(i);
                WorkBudget.Account waiting = (WorkBudget.Account) field(peer, "deferredWork");
                while (limits.maxQueuedOutboundBytes() - waiting.retainedBytes()
                        > WebSocketFrames.encodedLength(65_536))
                    peer.sendBinary(new byte[65_536]);
                int remaining = (int) (limits.maxQueuedOutboundBytes() - waiting.retainedBytes());
                int payload = Math.max(0, remaining - 10);
                while (WebSocketFrames.encodedLength(payload) < remaining) payload++;
                assertEquals(remaining, WebSocketFrames.encodedLength(payload));
                peer.sendBinary(new byte[payload]);
                assertEquals(limits.maxQueuedOutboundBytes(), waiting.retainedBytes());
            }
            assertEquals(WorkBudget.DEFERRED_BYTE_RESERVE,
                    WorkBudget.DEFERRED.retainedBytes());
            WebSocketConnection innocent = peers.get(2);
            WorkBudget.Account innocentDeferred =
                    (WorkBudget.Account) field(innocent, "deferredWork");
            WorkBudget.Account innocentOutbound =
                    (WorkBudget.Account) field(innocent, "outboundWork");
            innocent.sendBinary(new byte[0]);
            assertFalse(innocent.aborted());
            await(() -> peers.get(0).aborted() || peers.get(1).aborted());
            assertEquals(1, innocentDeferred.retainedItems());
            filler.close();
            await(() -> innocentOutbound.retainedItems() == 1);
        } finally {
            if (filler != null) filler.close();
            peers.forEach(peer -> { peer.abort(); server.connectionFinished(peer); });
            sockets.forEach(socket -> { try { socket.close(); } catch (IOException ignored) { } });
        }
        assertEquals(deferredBefore, WorkBudget.DEFERRED.retainedItems());
    }

    private static WorkBudget.Reservation saturateProcessItems() {
        WorkBudget.Account held = WorkBudget.PROCESS.account(8192, 1);
        return WorkBudget.PROCESS.tryReserve(held, held,
                new WorkBudget.Cost(8192 - WorkBudget.PROCESS.retainedItems(), 0)).orElseThrow();
    }

    @Test
    void ignoringInterruptLeavesAuthorityRetirementObservablyIncomplete() throws Exception {
        owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        JdkHostLimits defaults = JdkHostLimits.defaults();
        JdkHostLimits shortDeadline = new JdkHostLimits(defaults.maxConnectionsPerIp(),
                defaults.maxConnections(), defaults.handshakeTimeoutMillis(),
                defaults.idleTimeoutMillis(), defaults.tickMillis(), defaults.closeGraceMillis(),
                100, defaults.maxRequestLineBytes(), defaults.maxRequestHeaderBytes(),
                defaults.maxFramePayloadBytes(), defaults.maxMessageBytes(),
                defaults.maxBinaryMessageBytes(), defaults.maxQueuedOutboundBytes());
        server = JdkRaceHostServer.start(0, config(), owner,
                TrackValidationProfileSource.none(), System::currentTimeMillis, false,
                shortDeadline);
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        server.execute(() -> {
            busy.countDown();
            while (resume.getCount() > 0) {
                try { resume.await(); }
                catch (InterruptedException ignored) { }
            }
        });
        try {
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            server.close();
            assertFalse(server.released());
            assertFalse(receiptFlag("authorityRetired"));
            assertFalse(receiptFlag("ioReleased"));
            assertTrue(receiptFlag("timedOut"));
        } finally {
            resume.countDown();
        }
        await(() -> {
            try { return receiptFlag("authorityRetired"); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        await(() -> {
            try { return receiptFlag("ioReleased"); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    private boolean receiptFlag(String name) throws ReflectiveOperationException {
        Object receipt = server.getClass().getMethod("retirementReceipt").invoke(server);
        return (boolean) receipt.getClass().getMethod(name).invoke(receipt);
    }

    @Test
    void concurrentAndNestedCloseKeepRoomOwnershipAndRejectExternalWork() throws Exception {
        start();
        Tunnel tunnel = new Tunnel();
        tunnel.admit(1, owner);
        tunnel.admit(2, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
        CountDownLatch retiring = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        tunnel.onClose = () -> {
            server.close();
            retiring.countDown();
            try { assertTrue(resume.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        };
        CompletableFuture<Void> close = CompletableFuture.runAsync(server::close, Thread::startVirtualThread);
        try {
            assertTrue(retiring.await(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.execute(() -> fail("external work ran")));
            CompletableFuture.runAsync(server::close, Thread::startVirtualThread).get(2, TimeUnit.SECONDS);
        } finally {
            resume.countDown();
            close.get(10, TimeUnit.SECONDS);
        }
        assertEquals(0, server.room().playerCount());
        assertEquals(List.of(1, 2), tunnel.closedGuests());
        assertTrue(server.released());
        assertTrue(tunnel.closeThreads.stream().allMatch(thread -> thread == tunnel.ownerThread));
    }

    @Test
    void bindFailureRetiresThePreparedRoom() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0);
             var rooms = mockConstruction(RoomHost.class, (room, context) ->
                     when(room.ready()).thenReturn(CompletableFuture.completedFuture(room)))) {
            assertThrows(UncheckedIOException.class, () -> JdkRaceHostServer.start(
                    occupied.getLocalPort(), config(), PlayerIdentity.loadOrCreate(dir.resolve("owner")),
                    TrackValidationProfileSource.none()));
            verify(rooms.constructed().getFirst()).close(anyString());
        }
    }

    @Test
    void invalidBindAddressRetiresThePreparedRoom() throws Exception {
        try (var rooms = mockConstruction(RoomHost.class, (room, context) ->
                when(room.ready()).thenReturn(CompletableFuture.completedFuture(room)))) {
            assertThrows(IllegalArgumentException.class, () -> JdkRaceHostServer.start(
                    -1, config(), PlayerIdentity.loadOrCreate(dir.resolve("owner")),
                    TrackValidationProfileSource.none()));
            verify(rooms.constructed().getFirst()).close(anyString());
        }
    }

    private RoomHostConfig config() {
        return new RoomHostConfig("Private", "s3k", 0, 0, "OPEN", null, 8, BUILD);
    }

    private void start() throws Exception {
        owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        try (RoomPassword password = RoomPassword.of(PASSWORD)) {
            RoomHostConfig config = new RoomHostConfig("Private", "s3k", 0, 0, "OPEN", null,
                    8, BUILD, List.of(), false,
                    new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true), password, null);
            server = JdkRaceHostServer.start(0, config, owner, TrackValidationProfileSource.none());
        }
    }

    private <T> T onRoom(Supplier<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private Object preparedVerifier() throws ReflectiveOperationException {
        return ((CompletableFuture<?>) field(server.room(), "preparation")).join();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void assertErased(Object verifier) throws ReflectiveOperationException {
        assertTrue(allZero((byte[]) field(verifier, "salt")), "retired verifier salt erased");
        assertTrue(allZero((byte[]) field(verifier, "hash")), "retired verifier hash erased");
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) if (value != 0) return false;
        return true;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "lifecycle transition completed");
            Thread.sleep(5);
        }
    }

    private final class Tunnel implements HostMasterLink.MessageSink {
        private final ConcurrentLinkedQueue<ControlMessage> outbound = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Thread> closeThreads = new ConcurrentLinkedQueue<>();
        private final HostMasterLink link = HostMasterLink.forServer(server, this);
        private volatile Thread ownerThread;
        private volatile Runnable onClose = () -> { };

        Tunnel() {
            HostLinkAuthentication.bind(link, () -> BINDING);
        }

        void open(int id) throws Exception { open(id, "guest"); }

        void open(int id, String fingerprint) throws Exception {
            onRoom(() -> { link.onMasterControl(new ControlMessage.RelayGuestOpen(id,
                    "room", server.room().instanceId(), fingerprint, "grant", 1, 1)); return null; });
            onRoom(() -> null);
        }

        void send(int id, String token, ControlMessage message) throws Exception {
            String text = codec.encode(token, message);
            onRoom(() -> { link.onMasterControl(new ControlMessage.RelayGuestText(id, text)); return null; });
            onRoom(() -> null);
        }

        String admit(int id, PlayerIdentity identity) throws Exception {
            open(id, identity.fingerprint());
            ClientHandshake handshake = new ClientHandshake(identity, "Player", "", BUILD,
                    owner.fingerprint(), AuthenticationTranscript.Role.ROOM_RELAY, () -> BINDING);
            send(id, null, handshake.hello());
            send(id, null, handshake.onWelcome(last(id, ControlMessage.Welcome.class)));
            var challenge = last(id, ControlMessage.RoomAccessChallenge.class);
            assertNotNull(challenge);
            try (RoomPassword password = RoomPassword.of(PASSWORD)) {
                send(id, null, new ControlMessage.RoomAccessSubmit(challenge.challengeId(), password));
            }
            await(() -> last(id, ControlMessage.JoinAccepted.class) != null);
            return last(id, ControlMessage.JoinAccepted.class).sessionToken();
        }

        <T> T last(int id, Class<T> type) {
            return outbound.stream().filter(ControlMessage.RelayGuestText.class::isInstance)
                    .map(ControlMessage.RelayGuestText.class::cast).filter(text -> text.guestId() == id)
                    .map(text -> codec.decode(text.text()).message()).filter(type::isInstance)
                    .map(type::cast).reduce((first, last) -> last).orElse(null);
        }

        List<Integer> closedGuests() {
            return outbound.stream().filter(ControlMessage.RelayGuestClose.class::isInstance)
                    .map(ControlMessage.RelayGuestClose.class::cast)
                    .map(ControlMessage.RelayGuestClose::guestId).toList();
        }

        @Override
        public void sendControl(ControlMessage message) {
            if (ownerThread == null) ownerThread = Thread.currentThread();
            outbound.add(message);
            if (message instanceof ControlMessage.RelayGuestClose) {
                closeThreads.add(Thread.currentThread());
                onClose.run();
            }
        }

        @Override
        public void sendBinary(byte[] data) { }
    }
}
