package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomPassword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TestOutboundRetirement {
    @Test
    void raceCloseDiscardsQueuedPacketsAndLateWriteCannotRestartThem() throws Exception {
        Constructor<RaceClient> constructor = RaceClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RaceClient client = constructor.newInstance();
        WebSocket socket = mock(WebSocket.class);
        CompletableFuture<WebSocket> stalled = new CompletableFuture<>();
        AtomicInteger writes = new AtomicInteger();
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenAnswer(invocation ->
                writes.incrementAndGet() == 1 ? stalled : CompletableFuture.completedFuture(socket));
        when(socket.sendClose(anyInt(), any())).thenReturn(CompletableFuture.completedFuture(socket));
        set(client, "webSocket", socket);
        set(client, "open", true);

        for (int i = 0; i < 100; i++) client.sendBinary(new byte[32]);
        client.close();
        client.close();
        stalled.complete(socket);

        assertEquals(1, writes.get(), "queued sends must be discarded at retirement");
        verify(socket, times(1)).sendClose(anyInt(), any());
    }

    @Test
    void raceOutboundBytesStayBoundedWhileFirstWriteStalls() throws Exception {
        Constructor<RaceClient> constructor = RaceClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RaceClient client = constructor.newInstance();
        WebSocket socket = mock(WebSocket.class);
        CompletableFuture<WebSocket> stalled = new CompletableFuture<>();
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenReturn(stalled);
        set(client, "webSocket", socket);
        set(client, "open", true);

        for (int i = 0; i < 1024 && client.isOpen(); i++) {
            client.sendBinary(new byte[4096]);
        }

        assertFalse(client.isOpen(), "a stalled writer must reject further retained packets");
        stalled.complete(socket);
    }

    @Test
    void fullRaceWriterRejectsBeforeEncodingTheNextControl() throws Exception {
        Constructor<RaceClient> constructor = RaceClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RaceClient client = constructor.newInstance();
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendBinary(any(ByteBuffer.class), eq(true)))
                .thenReturn(new CompletableFuture<>());
        set(client, "webSocket", socket);
        set(client, "open", true);
        for (int i = 0; i < 256; i++) client.sendBinary(new byte[4096]);
        assertTrue(client.isOpen());

        RoomPassword erased = RoomPassword.of("an original password");
        erased.close();
        assertDoesNotThrow(() -> client.sendControl(new ControlMessage.RoomPasswordChange(erased)));
        assertFalse(client.isOpen());
    }

    @Test
    void compoundEncodedAdmissionIsAtomicAndEncodingFailureReleasesAllCosts() {
        WorkBudget budget = new WorkBudget(8, 32);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        OutboundWriter writer = new OutboundWriter(budget, 8, 16,
                () -> socket, () -> fail("writer overflow"));
        AtomicInteger encodes = new AtomicInteger();
        Supplier<String> first = () -> { encodes.incrementAndGet(); return "first"; };
        Supplier<String> second = () -> { encodes.incrementAndGet(); return "second"; };
        assertTrue(writer.prepareEncodedBatch(List.of(first, second), 9,
                text -> () -> socket.sendText(text, true)).isEmpty());
        assertEquals(0, encodes.get());
        assertEquals(0, budget.retainedBytes());

        assertThrows(IllegalStateException.class, () -> writer.prepareEncodedBatch(
                List.of(first, () -> { throw new IllegalStateException("encode failed"); }), 8,
                text -> () -> socket.sendText(text, true)));
        assertEquals(0, budget.retainedItems());
        assertEquals(0, writer.retainedBytes());

        var accepted = writer.prepareEncodedBatch(List.of(first, second), 8,
                text -> () -> socket.sendText(text, true)).orElseThrow();
        assertEquals(11, budget.retainedBytes());
        accepted.forEach(OutboundWriter.Prepared::dispatch);
        assertEquals(0, budget.retainedItems());
        verify(socket).sendText("first", true);
        verify(socket).sendText("second", true);
    }

    @Test
    void masterGracefulCloseDrainsAcceptedPacketsInOrder(@TempDir Path dir)
            throws Exception {
        Constructor<MasterClient> constructor = MasterClient.class.getDeclaredConstructor(
                PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        MasterClient client = constructor.newInstance(
                PlayerIdentity.loadOrCreate(dir), "HOST", "build");
        WebSocket socket = mock(WebSocket.class);
        CompletableFuture<WebSocket> stalled = new CompletableFuture<>();
        AtomicInteger writes = new AtomicInteger();
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenAnswer(invocation ->
                writes.incrementAndGet() == 1 ? stalled : CompletableFuture.completedFuture(socket));
        when(socket.sendClose(anyInt(), any())).thenReturn(CompletableFuture.completedFuture(socket));
        set(client, "webSocket", socket);
        set(client, "open", true);

        for (int i = 0; i < 100; i++) client.sendBinary(new byte[32]);
        client.close();
        stalled.complete(socket);

        assertEquals(100, writes.get(), "graceful master close drains accepted writes");
        verify(socket, times(1)).sendClose(anyInt(), any());
    }

    @Test
    void masterFailureDiscardsQueuedPacketsAndLateWriteCannotRestartThem(@TempDir Path dir)
            throws Exception {
        Constructor<MasterClient> constructor = MasterClient.class.getDeclaredConstructor(
                PlayerIdentity.class, String.class, String.class);
        constructor.setAccessible(true);
        MasterClient client = constructor.newInstance(
                PlayerIdentity.loadOrCreate(dir), "HOST", "build");
        WebSocket socket = mock(WebSocket.class);
        CompletableFuture<WebSocket> stalled = new CompletableFuture<>();
        AtomicInteger writes = new AtomicInteger();
        when(socket.sendBinary(any(ByteBuffer.class), eq(true))).thenAnswer(invocation ->
                writes.incrementAndGet() == 1 ? stalled : CompletableFuture.completedFuture(socket));
        set(client, "webSocket", socket);
        set(client, "open", true);

        for (int i = 0; i < 100; i++) client.sendBinary(new byte[32]);
        Method retire = MasterClient.class.getDeclaredMethod("retire", String.class);
        retire.setAccessible(true);
        retire.invoke(client, "transport failure");
        stalled.complete(socket);

        assertEquals(1, writes.get());
        verify(socket, times(1)).abort();
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
