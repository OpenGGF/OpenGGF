package com.openggf.net.host.jdk;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.host.HostLinkAuthentication;
import com.openggf.net.host.HostMasterLink;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
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

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
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

        void open(int id) throws Exception {
            onRoom(() -> { link.onMasterControl(new ControlMessage.RelayGuestOpen(id)); return null; });
            onRoom(() -> null);
        }

        void send(int id, String token, ControlMessage message) throws Exception {
            String text = codec.encode(token, message);
            onRoom(() -> { link.onMasterControl(new ControlMessage.RelayGuestText(id, text)); return null; });
            onRoom(() -> null);
        }

        String admit(int id, PlayerIdentity identity) throws Exception {
            open(id);
            ClientHandshake handshake = new ClientHandshake(identity, "Player", BUILD,
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
