package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.HubConnection;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.flow.FlowControlHandler;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static openggf.racing.server.master.TestRoomBrokerAdversarial.FP;

/** Cross-loop close races are deterministic; a few raw sockets verify the actual pipeline. */
@Timeout(30)
class TestMasterTransportLifecycle {
    static final class HeldExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) {
            assertTrue(tasks.size() < 128, "test executor budget exceeded");
            tasks.addLast(task);
        }
        void drain() {
            for (int i = 0; !tasks.isEmpty(); i++) {
                assertTrue(i < 256, "executor did not quiesce");
                tasks.removeFirst().run();
            }
        }
    }

    static final class LoopbackChannel extends EmbeddedChannel {
        LoopbackChannel(ChannelHandler... handlers) { super(handlers); }
        @Override protected SocketAddress remoteAddress0() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }
        void peerDisconnectBeforePendingTasks() throws Exception {
            // Both close() and the Unsafe wrapper drain EmbeddedChannel tasks. A real
            // peer can become inactive before queued cross-loop callbacks execute.
            super.doClose();
            pipeline().fireChannelInactive();
        }
    }

    static final class HeldOutbound extends ChannelOutboundHandlerAdapter {
        final ArrayDeque<TextWebSocketFrame> frames = new ArrayDeque<>();
        @Override public void write(ChannelHandlerContext context, Object message,
                                    ChannelPromise promise) {
            assertTrue(frames.size() < 16, "held response budget exceeded");
            frames.addLast((TextWebSocketFrame) message);
            promise.setSuccess();
        }
        @Override public void flush(ChannelHandlerContext context) {
            // Reaching EmbeddedChannel's Unsafe flush auto-drains queued IO work.
        }
        ControlMessage read() {
            TextWebSocketFrame frame = frames.pollFirst();
            assertNotNull(frame, "missing control response");
            try { return ControlCodec.decode(frame.text()).message(); }
            finally { frame.release(); }
        }
        void releaseAll() {
            TextWebSocketFrame frame;
            while ((frame = frames.pollFirst()) != null) frame.release();
        }
    }

    static final class HeldWrites extends ChannelOutboundHandlerAdapter {
        final ArrayDeque<WebSocketFrame> frames = new ArrayDeque<>();
        @Override public void write(ChannelHandlerContext context, Object message,
                                    ChannelPromise promise) {
            frames.addLast((WebSocketFrame) message);
        }
        void releaseAll() {
            WebSocketFrame frame;
            while ((frame = frames.pollFirst()) != null) frame.release();
        }
    }

    private static void upgraded(EmbeddedChannel channel) {
        channel.pipeline().fireUserEventTriggered(
                new WebSocketServerProtocolHandler.HandshakeComplete(
                        "/master", new DefaultHttpHeaders(), null));
    }

    @Test
    void repeatedUpgradeAndCloseCannotGrowBrokerLifecycleBacklogWithoutAdmission() {
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        var counter = new ConnectionHygiene.ConnectionCounter(4);
        RoomBroker broker = mock(RoomBroker.class);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8_192, 1));
        int before = WorkBudget.PROCESS.retainedItems();
        try {
            for (int cycle = 0; cycle < 4_200; cycle++) {
                LoopbackChannel channel = new LoopbackChannel(new MasterChannelHandler(
                        broker, null, null, queued::addLast, counter));
                try {
                    if (channel.isActive()) upgraded(channel);
                } finally {
                    channel.finishAndReleaseAll();
                }
            }
            assertTrue(queued.size() <= 8_192,
                    "closed peers still own their queued broker lifecycle work");
            assertTrue(WorkBudget.PROCESS.retainedItems() > before,
                    "queued lifecycle callbacks retain process admission");
        } finally {
            while (!queued.isEmpty()) queued.removeFirst().run();
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void admittedMasterCleanupKeepsItsReservedCellWhenTheProcessIsFull() {
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        RoomBroker broker = mock(RoomBroker.class);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8_192, 1));
        int before = WorkBudget.PROCESS.retainedItems();
        LoopbackChannel channel = new LoopbackChannel(new MasterChannelHandler(
                broker, null, null, queued::addLast,
                new ConnectionHygiene.ConnectionCounter(4)));
        List<WorkBudget.Reservation> occupied = new ArrayList<>();
        try {
            upgraded(channel);
            queued.removeFirst().run();
            assertTrue(WorkBudget.PROCESS.retainedItems() > before,
                    "an admitted source owns its future cleanup capacity");
            var filler = WorkBudget.PROCESS.account(8_192, 1);
            for (;;) {
                var reservation = WorkBudget.PROCESS.tryReserve(filler, filler,
                        new WorkBudget.Cost(1, 0));
                if (reservation.isEmpty()) break;
                occupied.add(reservation.get());
            }
            channel.close();
            assertEquals(1, queued.size());
            queued.removeFirst().run();
            org.mockito.Mockito.verify(broker).onDisconnected(org.mockito.ArgumentMatchers.any());
        } finally {
            channel.finishAndReleaseAll();
            while (!queued.isEmpty()) queued.removeFirst().run();
            occupied.forEach(WorkBudget.Reservation::close);
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void unavailableBrokerLoopReleasesTheWholeUnfinishedConnection() {
        RoomBroker broker = mock(RoomBroker.class);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8_192, 1));
        int before = WorkBudget.PROCESS.retainedItems();
        LoopbackChannel channel = new LoopbackChannel(new MasterChannelHandler(
                broker, null, null, ignored -> {
                    throw new java.util.concurrent.RejectedExecutionException("broker stopped");
                }, new ConnectionHygiene.ConnectionCounter(4)));
        try {
            upgraded(channel);
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void stalledMasterPhysicalWritesKeepAnItemReservationUntilClose(@TempDir Path dir)
            throws Exception {
        try (var fixture = new TestRoomBrokerAdversarial.Fixture(dir)) {
            int before = WorkBudget.PROCESS.retainedItems();
            HeldWrites held = new HeldWrites();
            var handler = new MasterChannelHandler(fixture.broker, fixture.relays,
                    fixture.tunnels, Runnable::run,
                    new ConnectionHygiene.ConnectionCounter(4));
            LoopbackChannel channel = new LoopbackChannel(held, handler);
            try {
                upgraded(channel);
                Field connection = MasterChannelHandler.class.getDeclaredField("connection");
                connection.setAccessible(true);
                HubConnection peer = (HubConnection) connection.get(handler);
                for (int i = 0; i < 512; i++) peer.sendBinary(new byte[0]);
                assertTrue(channel.isActive());
                peer.sendBinary(new byte[0]);
                assertFalse(channel.isActive(), "the 513th held write must retire the peer");
                assertEquals(before, WorkBudget.PROCESS.retainedItems());
            } finally {
                channel.finishAndReleaseAll();
                held.releaseAll();
            }
        }
    }

    @Test
    void stalledMasterPhysicalWriteExpiresWithoutReaderIdle(@TempDir Path dir) throws Exception {
        try (var fixture = new TestRoomBrokerAdversarial.Fixture(dir)) {
            int before = WorkBudget.PROCESS.retainedItems();
            HeldWrites held = new HeldWrites();
            var handler = new MasterChannelHandler(fixture.broker, fixture.relays,
                    fixture.tunnels, Runnable::run, new ConnectionHygiene.ConnectionCounter(4));
            LoopbackChannel channel = new LoopbackChannel(held, handler);
            try {
                upgraded(channel);
                Field connection = MasterChannelHandler.class.getDeclaredField("connection");
                connection.setAccessible(true);
                ((HubConnection) connection.get(handler)).sendBinary(new byte[0]);
                assertEquals(1, held.frames.size());
                channel.advanceTimeBy(2_100, TimeUnit.MILLISECONDS);
                channel.runScheduledPendingTasks();
                assertFalse(channel.isActive(), "a held write cannot live on reader-idle traffic");
                assertEquals(before, WorkBudget.PROCESS.retainedItems());
            } finally {
                channel.finishAndReleaseAll();
                held.releaseAll();
            }
        }
    }

    @Test
    void fullAttachedRoomBackpressuresAnUnrelatedSourceUntilTheMailboxDrains(@TempDir Path dir)
            throws Exception {
        try (var fixture = new TestRoomBrokerAdversarial.Fixture(dir)) {
            ArrayDeque<Runnable> destination = new ArrayDeque<>();
            var access = new RelayRoomManager.RoomAccess(
                    mock(com.openggf.net.hub.RoomHost.class), destination::addLast);
            List<LoopbackChannel> channels = new ArrayList<>();
            try {
                for (int source = 0; source < 17; source++) {
                    var handler = new MasterChannelHandler(fixture.broker, fixture.relays,
                            fixture.tunnels, Runnable::run,
                            new ConnectionHygiene.ConnectionCounter(64));
                    LoopbackChannel channel = new LoopbackChannel(new FlowControlHandler(), handler);
                    channels.add(channel);
                    upgraded(channel);
                    Field mode = MasterChannelHandler.class.getDeclaredField("mode");
                    mode.setAccessible(true);
                    mode.set(handler, Arrays.stream(mode.getType().getEnumConstants())
                            .filter(value -> value.toString().equals("ROOM")).findFirst().orElseThrow());
                    Field roomAccess = MasterChannelHandler.class.getDeclaredField("roomAccess");
                    roomAccess.setAccessible(true);
                    roomAccess.set(handler, access);
                    if (source == 16) {
                        channel.writeInbound(new BinaryWebSocketFrame(
                                        Unpooled.wrappedBuffer(new byte[4096])),
                                new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[4096])));
                    } else for (int packet = 0; packet < 32; packet++)
                        channel.writeInbound(new BinaryWebSocketFrame(
                                Unpooled.wrappedBuffer(new byte[4096])));
                }
                LoopbackChannel innocent = channels.getLast();
                assertTrue(innocent.isActive(), "a full shared room mailbox must not close a new peer");
                assertEquals(512, access.inboundWork().retainedItems());
                while (!destination.isEmpty()) destination.removeFirst().run();
                innocent.advanceTimeBy(60, TimeUnit.MILLISECONDS);
                innocent.runScheduledPendingTasks();
                assertTrue(innocent.isActive());
                assertEquals(2, access.inboundWork().retainedItems(),
                        "both pipelined frames resume in order after capacity returns");
            } finally {
                channels.forEach(LoopbackChannel::finishAndReleaseAll);
                destination.forEach(Runnable::run);
            }
            assertEquals(0, access.inboundWork().retainedItems());
        }
    }

    @Test
    void partialRoomCapacityDoesNotForceASecondQueuedFramePastThePause() throws Exception {
        RoomBroker broker = mock(RoomBroker.class);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8192, 1));
        ArrayDeque<Runnable> destination = new ArrayDeque<>();
        var access = new RelayRoomManager.RoomAccess(
                mock(com.openggf.net.hub.RoomHost.class), destination::addLast);
        var handler = new MasterChannelHandler(broker, null, null, Runnable::run,
                new ConnectionHygiene.ConnectionCounter(4));
        LoopbackChannel channel = new LoopbackChannel(new FlowControlHandler(), handler);
        WorkBudget.Reservation full = null;
        WorkBudget.Reservation partial = null;
        try {
            upgraded(channel);
            Field mode = MasterChannelHandler.class.getDeclaredField("mode");
            mode.setAccessible(true);
            mode.set(handler, Arrays.stream(mode.getType().getEnumConstants())
                    .filter(value -> value.toString().equals("ROOM")).findFirst().orElseThrow());
            Field roomAccess = MasterChannelHandler.class.getDeclaredField("roomAccess");
            roomAccess.setAccessible(true);
            roomAccess.set(handler, access);
            WorkBudget.Account other = WorkBudget.PROCESS.account(1024, 1);
            full = WorkBudget.PROCESS.tryReserve(other, access.inboundWork(),
                    new WorkBudget.Cost(512, 0)).orElseThrow();
            channel.writeInbound(
                    new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[64])),
                    new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[64])),
                    new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[64])),
                    new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[64])));
            assertTrue(channel.isActive());
            full.close();
            full = null;
            partial = WorkBudget.PROCESS.tryReserve(other, access.inboundWork(),
                    new WorkBudget.Cost(511, 0)).orElseThrow();
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
            assertTrue(channel.isActive(),
                    "a single free room slot must not force the next decoded frame through");
            partial.close();
            partial = null;
            for (int retry = 0; retry < 5 && destination.size() < 4; retry++) {
                channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
                channel.runScheduledPendingTasks();
            }
            assertEquals(4, destination.size());
        } finally {
            if (full != null) full.close();
            if (partial != null) partial.close();
            channel.finishAndReleaseAll();
            while (!destination.isEmpty()) destination.removeFirst().run();
        }
    }

    @Test
    void brokerCompletionDoesNotForceQueuedFramesPastAFullMailbox() throws Exception {
        RoomBroker broker = mock(RoomBroker.class);
        WorkBudget.Account brokerInbound = WorkBudget.PROCESS.account(4096, 8L * 1024 * 1024);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8192, 1));
        org.mockito.Mockito.when(broker.inboundWork()).thenReturn(brokerInbound);
        ArrayDeque<Runnable> brokerLoop = new ArrayDeque<>();
        var handler = new MasterChannelHandler(broker, null, null, brokerLoop::addLast,
                new ConnectionHygiene.ConnectionCounter(4));
        LoopbackChannel channel = new LoopbackChannel(new FlowControlHandler(), handler);
        WorkBudget.Reservation held = null;
        try {
            upgraded(channel);
            brokerLoop.removeFirst().run();
            String request = ControlCodec.encode(null, new ControlMessage.RoomListRequest(null, 0));
            channel.writeInbound(new TextWebSocketFrame(request), new TextWebSocketFrame(request),
                    new TextWebSocketFrame(request), new TextWebSocketFrame(request));
            WorkBudget.Account other = WorkBudget.PROCESS.account(4096, 1);
            held = WorkBudget.PROCESS.tryReserve(other, brokerInbound,
                    new WorkBudget.Cost(4095, 0)).orElseThrow();
            brokerLoop.removeFirst().run();
            channel.runPendingTasks();
            assertTrue(channel.isActive(),
                    "completion must leave later decoded frames behind a refused first frame");
        } finally {
            if (held != null) held.close();
            channel.finishAndReleaseAll();
            while (!brokerLoop.isEmpty()) brokerLoop.removeFirst().run();
        }
    }

    @Test
    void lifecycleActivationDoesNotForceQueuedFramesPastAFullMailbox() throws Exception {
        RoomBroker broker = mock(RoomBroker.class);
        WorkBudget.Account brokerLifecycle = WorkBudget.PROCESS.account(6, 1);
        WorkBudget.Account brokerInbound = WorkBudget.PROCESS.account(4096, 8L * 1024 * 1024);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(brokerLifecycle);
        org.mockito.Mockito.when(broker.inboundWork()).thenReturn(brokerInbound);
        WorkBudget.Account other = WorkBudget.PROCESS.account(4100, 1);
        WorkBudget.Reservation lifecycleHold = WorkBudget.PROCESS.tryReserve(
                other, brokerLifecycle, new WorkBudget.Cost(4, 0)).orElseThrow();
        WorkBudget.Reservation inboundHold = null;
        var handler = new MasterChannelHandler(broker, null, null, Runnable::run,
                new ConnectionHygiene.ConnectionCounter(4));
        LoopbackChannel channel = new LoopbackChannel(new FlowControlHandler(), handler);
        try {
            Field connection = MasterChannelHandler.class.getDeclaredField("connection");
            connection.setAccessible(true);
            connection.set(handler, mock(HubConnection.class));
            String request = ControlCodec.encode(null, new ControlMessage.RoomListRequest(null, 0));
            channel.writeInbound(new TextWebSocketFrame(request), new TextWebSocketFrame(request),
                    new TextWebSocketFrame(request), new TextWebSocketFrame(request));
            inboundHold = WorkBudget.PROCESS.tryReserve(other, brokerInbound,
                    new WorkBudget.Cost(4096, 0)).orElseThrow();
            lifecycleHold.close();
            lifecycleHold = null;
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
            assertTrue(channel.isActive(),
                    "activation must leave later decoded frames behind a refused first frame");
        } finally {
            if (lifecycleHold != null) lifecycleHold.close();
            if (inboundHold != null) inboundHold.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void fullBrokerIngressBackpressuresAnUnrelatedSourceUntilAnotherLeaves() {
        RoomBroker broker = mock(RoomBroker.class);
        WorkBudget.Account brokerInbound = WorkBudget.PROCESS.account(4096, 8L * 1024 * 1024);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8192, 1));
        org.mockito.Mockito.when(broker.inboundWork()).thenReturn(brokerInbound);
        ArrayDeque<Runnable> brokerLoop = new ArrayDeque<>();
        var counter = new ConnectionHygiene.ConnectionCounter(64);
        int before = WorkBudget.PROCESS.retainedItems();
        List<LoopbackChannel> channels = new ArrayList<>();
        try {
            for (int source = 0; source < 6; source++) {
                LoopbackChannel channel = new LoopbackChannel(new MasterChannelHandler(
                        broker, null, null, brokerLoop::addLast, counter));
                channels.add(channel);
                upgraded(channel);
                int frames = source == 5 ? 1 : source == 4 ? 820 : 819;
                for (int packet = 0; packet < frames; packet++)
                    channel.writeInbound(new TextWebSocketFrame("{}"));
            }
            assertEquals(4096, brokerInbound.retainedItems());
            LoopbackChannel innocent = channels.getLast();
            assertTrue(innocent.isActive());
            channels.getFirst().close();
            innocent.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            innocent.runScheduledPendingTasks();
            assertTrue(innocent.isActive());
            assertEquals(4096 - 818 + 1, brokerInbound.retainedItems(),
                    "one closed holder releases its queued frames and the innocent frame enters");
        } finally {
            channels.forEach(LoopbackChannel::finishAndReleaseAll);
            while (!brokerLoop.isEmpty()) {
                try { brokerLoop.removeFirst().run(); }
                catch (com.openggf.net.protocol.ProtocolViolationException ignored) { }
            }
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void processFullOfStalledMasterWritersDoesNotCloseAnUnrelatedWriter(@TempDir Path dir)
            throws Exception {
        try (var fixture = new TestRoomBrokerAdversarial.Fixture(dir)) {
            int before = WorkBudget.PROCESS.retainedItems();
            int deferredBefore = WorkBudget.DEFERRED.retainedItems();
            var counter = new ConnectionHygiene.ConnectionCounter(64);
            List<LoopbackChannel> channels = new ArrayList<>();
            List<HeldWrites> writes = new ArrayList<>();
            List<HubConnection> peers = new ArrayList<>();
            try {
                for (int i = 0; i < 17; i++) {
                    HeldWrites held = new HeldWrites();
                    var handler = new MasterChannelHandler(fixture.broker, fixture.relays,
                            fixture.tunnels, Runnable::run, counter);
                    LoopbackChannel channel = new LoopbackChannel(held, handler);
                    channels.add(channel);
                    writes.add(held);
                    upgraded(channel);
                    Field field = MasterChannelHandler.class.getDeclaredField("connection");
                    field.setAccessible(true);
                    peers.add((HubConnection) field.get(handler));
                }
                int next = 1;
                while (WorkBudget.PROCESS.retainedItems() < 8192) {
                    peers.get(next).sendBinary(new byte[0]);
                    assertTrue(channels.get(next).isActive(),
                            "each stalled holder remains below its own 512-write cap");
                    next = next == 16 ? 1 : next + 1;
                }
                for (int reply = 0; reply < 32; reply++) peers.getFirst().sendBinary(new byte[0]);
                assertTrue(channels.getFirst().isActive(),
                        "a sustained burst from the unrelated writer waits instead of closing");
                assertTrue(writes.getFirst().frames.isEmpty());
                assertEquals(deferredBefore + 32, WorkBudget.DEFERRED.retainedItems());
                assertTrue(peers.getFirst().queuedBytes() >= 32 * 16,
                        "congestion reporting includes the deferred outbound payloads");
                assertTrue(WorkBudget.retainedProcessBytes() <= WorkBudget.PROCESS_BYTE_LIMIT);
                channels.get(1).close();
                channels.getFirst().advanceTimeBy(60, TimeUnit.MILLISECONDS);
                channels.getFirst().runScheduledPendingTasks();
                assertTrue(channels.getFirst().isActive());
                assertEquals(32, writes.getFirst().frames.size(),
                        "all deferred responses are sent when a stalled holder leaves");
            } finally {
                channels.forEach(LoopbackChannel::finishAndReleaseAll);
                writes.forEach(HeldWrites::releaseAll);
            }
            assertEquals(before, WorkBudget.PROCESS.retainedItems());
            assertEquals(deferredBefore, WorkBudget.DEFERRED.retainedItems());
        }
    }

    @Test
    void fullDeferredReserveEvictsItsHeavyHolderInsteadOfTheNewWriter(@TempDir Path dir)
            throws Exception {
        try (var fixture = new TestRoomBrokerAdversarial.Fixture(dir)) {
            int before = WorkBudget.PROCESS.retainedItems();
            int deferredBefore = WorkBudget.DEFERRED.retainedItems();
            var counter = new ConnectionHygiene.ConnectionCounter(4);
            HeldWrites oldWrites = new HeldWrites();
            HeldWrites newWrites = new HeldWrites();
            var oldHandler = new MasterChannelHandler(fixture.broker, fixture.relays,
                    fixture.tunnels, Runnable::run, counter);
            var newHandler = new MasterChannelHandler(fixture.broker, fixture.relays,
                    fixture.tunnels, Runnable::run, counter);
            LoopbackChannel oldChannel = new LoopbackChannel(oldWrites, oldHandler);
            LoopbackChannel newChannel = new LoopbackChannel(newWrites, newHandler);
            WorkBudget.Reservation filler = null;
            try {
                upgraded(oldChannel);
                upgraded(newChannel);
                Field field = MasterChannelHandler.class.getDeclaredField("connection");
                field.setAccessible(true);
                HubConnection oldPeer = (HubConnection) field.get(oldHandler);
                HubConnection newPeer = (HubConnection) field.get(newHandler);
                WorkBudget.Account source = WorkBudget.PROCESS.account(8192, 1);
                filler = WorkBudget.PROCESS.tryReserve(source, source,
                        new WorkBudget.Cost(8192 - WorkBudget.PROCESS.retainedItems(), 0))
                        .orElseThrow();
                while (WorkBudget.DEFERRED_BYTE_RESERVE - WorkBudget.DEFERRED.retainedBytes()
                        > 131_088)
                    oldPeer.sendBinary(new byte[131_072]);
                int last = (int) (WorkBudget.DEFERRED_BYTE_RESERVE
                        - WorkBudget.DEFERRED.retainedBytes() - 16);
                oldPeer.sendBinary(new byte[last]);
                assertEquals(WorkBudget.DEFERRED_BYTE_RESERVE,
                        WorkBudget.DEFERRED.retainedBytes());
                newPeer.sendBinary(new byte[0]);
                assertFalse(oldChannel.isActive(), "the heavy deferred holder yields capacity");
                assertTrue(newChannel.isActive(), "the new writer keeps its connection");
                assertEquals(deferredBefore + 1, WorkBudget.DEFERRED.retainedItems());
                filler.close();
                newChannel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
                newChannel.runScheduledPendingTasks();
                assertEquals(1, newWrites.frames.size());
            } finally {
                if (filler != null) filler.close();
                oldChannel.finishAndReleaseAll();
                newChannel.finishAndReleaseAll();
                oldWrites.releaseAll();
                newWrites.releaseAll();
            }
            assertEquals(before, WorkBudget.PROCESS.retainedItems());
            assertEquals(deferredBefore, WorkBudget.DEFERRED.retainedItems());
        }
    }

    @Test
    void fullProcessLedgerWaitsForANewMasterConnectionWithoutLosingItsLifecycle() {
        RoomBroker broker = mock(RoomBroker.class);
        org.mockito.Mockito.when(broker.lifecycleWork()).thenReturn(
                WorkBudget.PROCESS.account(8192, 1));
        org.mockito.Mockito.when(broker.inboundWork()).thenReturn(
                WorkBudget.PROCESS.account(4096, 8L * 1024 * 1024));
        ArrayDeque<Runnable> brokerLoop = new ArrayDeque<>();
        int before = WorkBudget.PROCESS.retainedItems();
        WorkBudget.Account filler = WorkBudget.PROCESS.account(8192, 1);
        WorkBudget.Reservation held = WorkBudget.PROCESS.tryReserve(filler, filler,
                new WorkBudget.Cost(8192 - before, 0)).orElseThrow();
        LoopbackChannel channel = null;
        try {
            channel = new LoopbackChannel(new FlowControlHandler(),
                    new MasterChannelHandler(broker, null, null, brokerLoop::addLast,
                            new ConnectionHygiene.ConnectionCounter(4)));
            assertTrue(channel.isActive(), "an unrelated new connection waits for cleanup capacity");
            assertFalse(channel.config().isAutoRead());
            for (int retry = 0; retry < 2; retry++) {
                channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
                channel.runScheduledPendingTasks();
                assertTrue(channel.isActive());
                assertFalse(channel.config().isAutoRead(),
                        "a refused lifecycle retry must keep the handshake unread");
            }
            held.close();
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
            assertTrue(channel.config().isAutoRead());
            upgraded(channel);
            while (!brokerLoop.isEmpty()) brokerLoop.removeFirst().run();
            org.mockito.Mockito.verify(broker).onConnected(org.mockito.ArgumentMatchers.any());
            channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(null,
                    new ControlMessage.RoomListRequest(null, 0))));
            while (!brokerLoop.isEmpty()) brokerLoop.removeFirst().run();
            channel.runPendingTasks();
            assertTrue(channel.isActive(), "the first post-handshake frame must survive");
        } finally {
            held.close();
            if (channel != null) channel.finishAndReleaseAll();
            while (!brokerLoop.isEmpty()) brokerLoop.removeFirst().run();
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void closeBeforeBrokerConnectTaskRunsDoesNotRegisterNullPeer(@TempDir Path dir)
            throws Exception {
        HeldExecutor brokerLoop = new HeldExecutor();
        try (var f = new TestRoomBrokerAdversarial.Fixture(dir)) {
            var counter = new ConnectionHygiene.ConnectionCounter(4);
            EmbeddedChannel channel = new LoopbackChannel(new MasterChannelHandler(
                    f.broker, f.relays, f.tunnels, brokerLoop, counter));
            try {
                upgraded(channel);
                channel.close();
                assertDoesNotThrow(brokerLoop::drain,
                        "queued connect must retain its peer until disconnect cleanup runs");
                f.now[0] += 5_001;
                assertDoesNotThrow(f.broker::tick);
                assertTrue(counter.tryAcquire("127.0.0.1"));
                counter.release("127.0.0.1");
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "RELAY"})
    void closeAfterAttachBeforeIoCallbackReleasesOwnershipWithoutTimeout(String routing,
                                                                       @TempDir Path dir)
            throws Exception {
        HeldExecutor brokerLoop = new HeldExecutor();
        int before = WorkBudget.PROCESS.retainedItems();
        try (var f = new TestRoomBrokerAdversarial.Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            var witness = new TestRoomBrokerAdversarial.Connection();
            String witnessToken = f.admit(witness, owner);
            String roomId = f.create(witness, witnessToken, routing);
            int roomWork = WorkBudget.PROCESS.retainedItems();
            PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
            f.establish(guest);
            HeldOutbound output = new HeldOutbound();
            LoopbackChannel channel = new LoopbackChannel(output, new MasterChannelHandler(
                    f.broker, f.relays, f.tunnels, brokerLoop,
                    new ConnectionHygiene.ConnectionCounter(4)));
            try {
                upgraded(channel);
                brokerLoop.drain();
                ClientHandshake handshake = f.handshake(guest);
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(null,
                        handshake.hello())));
                brokerLoop.drain();
                channel.runPendingTasks();
                ControlMessage.Welcome welcome = assertInstanceOf(ControlMessage.Welcome.class,
                        output.read());
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(null,
                        handshake.onWelcome(welcome))));
                brokerLoop.drain();
                channel.runPendingTasks();
                String token = assertInstanceOf(ControlMessage.JoinAccepted.class,
                        output.read()).sessionToken();
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(token,
                        new ControlMessage.RoomJoinRequest(roomId))));
                brokerLoop.drain();
                channel.runPendingTasks();
                ControlMessage.RoomJoinResult route = assertInstanceOf(
                        ControlMessage.RoomJoinResult.class, output.read());
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(token,
                        new ControlMessage.RelayAttach(roomId,
                                route.grantReference(), route.routeGeneration()))));
                brokerLoop.drain(); // Attachment exists, IO mode transition still pending.
                if (routing.equals("DIRECT")) {
                    assertEquals(1, f.tunnels.activeTunnels());
                } else {
                    // Exercise the room owner's independent queue before the IO mode
                    // callback. The room already owns this connection after attach.
                    var access = f.relays.find(roomId).orElseThrow();
                    var connection = f.relayAttachments.getLast();
                    ClientHandshake roomHandshake = new ClientHandshake(guest, "Guest", "", FP,
                            null, AuthenticationTranscript.Role.ROOM_RELAY,
                            () -> AuthenticationTranscript.LOCAL_PLAINTEXT,
                            route.grantReference(), route.routeGeneration());
                    access.room().onText(connection, ControlCodec.encode(null, roomHandshake.hello()));
                    ControlMessage.Welcome roomWelcome = assertInstanceOf(
                            ControlMessage.Welcome.class, output.read());
                    access.room().onText(connection, ControlCodec.encode(null,
                            roomHandshake.onWelcome(roomWelcome)));
                    assertEquals(1, access.room().playerCount());
                    output.releaseAll();
                }
                channel.peerDisconnectBeforePendingTasks();
                brokerLoop.drain();
                channel.runPendingTasks();
                brokerLoop.drain();
                assertFalse(f.broker.isSessionTokenValid(token));
                assertEquals(0, f.tunnels.activeTunnels(), "closed attach retained its tunnel");
                f.relays.find(roomId).ifPresent(access -> assertEquals(0,
                        access.room().playerCount(), "closed attach retained its room player"));
                assertEquals(1, f.registry.list(null).size(), "guest close must preserve witness room");
                assertNull(witness.closed);
                assertEquals(roomWork, WorkBudget.PROCESS.retainedItems(),
                        "late attachment cleanup preserves only the live room's retirement cell");
            } finally {
                channel.finishAndReleaseAll();
                output.releaseAll();
            }
        }
        assertEquals(before, WorkBudget.PROCESS.retainedItems());
    }

    @Test
    void pipelinedRelayAttachAndHelloKeepOrderAndShutdownClosesAcceptedSockets(@TempDir Path dir)
            throws Exception {
        MasterServer server = MasterServer.start(TestMasterServer.testConfig(), dir);
        int port = server.port();
        try (Wire owner = Wire.connect(port); Socket silent = new Socket("127.0.0.1", port)) {
            Field clients = MasterServer.class.getDeclaredField("clientChannels");
            clients.setAccessible(true);
            ChannelGroup accepted = (ChannelGroup) clients.get(server);
            assertTrue(accepted.stream().anyMatch(channel -> {
                var flow = channel.pipeline().context(FlowControlHandler.class);
                var application = channel.pipeline().context(MasterChannelHandler.class);
                return flow != null && application != null
                        && channel.pipeline().names().indexOf(flow.name())
                        < channel.pipeline().names().indexOf(application.name());
            }), "production socket must pause decoded frames before master routing");
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            String token = owner.admit(identity, AuthenticationTranscript.Role.MASTER);
            onBroker(server, () -> server.establishForTest(identity.fingerprint()));
            owner.send(token, new ControlMessage.RoomCreate(new ControlMessage.RoomDescriptor(
                    "Pipeline", "s3k", 0, 0, "OPEN", null, 8, false),
                    "RELAY", 0, FP));
            String roomId = owner.await(ControlMessage.RoomCreated.class).roomId();
            owner.send(token, new ControlMessage.RoomJoinRequest(roomId));
            ControlMessage.RoomJoinResult route = owner.await(ControlMessage.RoomJoinResult.class);
            ClientHandshake roomHandshake = new ClientHandshake(identity, "Owner", "", FP, null,
                    AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT,
                    route.grantReference(), route.routeGeneration());
            owner.sendTogether(ControlCodec.encode(token, new ControlMessage.RelayAttach(roomId,
                            route.grantReference(), route.routeGeneration())),
                    ControlCodec.encode(null, roomHandshake.hello()));
            ControlMessage.Welcome welcome = owner.await(ControlMessage.Welcome.class);
            owner.send(null, roomHandshake.onWelcome(welcome));
            ControlMessage.JoinAccepted room = owner.await(ControlMessage.JoinAccepted.class);
            assertEquals(0, room.playerSlot());
            assertNotNull(room.room());
            server.close();
            owner.awaitClosed();
            silent.setSoTimeout(3_000);
            assertEquals(-1, silent.getInputStream().read());
            assertThrows(IllegalStateException.class, () -> server.execute(() -> { }));
        } finally {
            server.close();
        }
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    @Test
    void fifthAddressConnectionIsRejectedAndCapacityReturnsAfterAbruptClose(@TempDir Path dir)
            throws Exception {
        try (MasterServer server = MasterServer.start(TestMasterServer.testConfig(), dir)) {
            List<Wire> peers = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) peers.add(Wire.connect(server.port()));
                assertThrows(IOException.class, () -> Wire.connect(server.port()));
                peers.removeFirst().close();
                Wire replacement = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (replacement == null && System.nanoTime() < deadline) {
                    try { replacement = Wire.connect(server.port()); }
                    catch (IOException notReleasedYet) { Thread.sleep(10); }
                }
                assertNotNull(replacement, "per-IP capacity did not recover");
                peers.add(replacement);
                String token = replacement.admit(PlayerIdentity.loadOrCreate(dir.resolve("replacement")),
                        AuthenticationTranscript.Role.MASTER);
                replacement.send(token, new ControlMessage.RoomListRequest(null, 0));
                assertTrue(replacement.await(ControlMessage.RoomListResult.class).rooms().isEmpty());
            } finally {
                for (Wire peer : peers) peer.close();
            }
        }
    }

    static void onBroker(MasterServer server, Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.execute(() -> {
            try { task.run(); done.complete(null); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        done.get(5, TimeUnit.SECONDS);
    }

    /** Master-only wire helper; malformed/concatenated messages bypass client-side admission. */
    static final class Wire implements AutoCloseable {
        final Socket socket;
        final DataInputStream input;
        private Wire(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(3_000);
            input = new DataInputStream(socket.getInputStream());
        }
        static Wire connect(int port) throws IOException {
            Wire peer = new Wire(new Socket("127.0.0.1", port));
            try {
                peer.socket.getOutputStream().write(("GET /master HTTP/1.1\r\nHost: localhost\r\n"
                        + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                        + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "
                        + Base64.getEncoder().encodeToString(new byte[16]) + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                String status = peer.line();
                if (!status.contains(" 101 ")) throw new IOException("upgrade rejected: " + status);
                while (!peer.line().isEmpty()) { }
                return peer;
            } catch (IOException | RuntimeException failure) {
                peer.close();
                throw failure;
            }
        }
        String line() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int i = 0; i < 8_192; i++) {
                int next = input.read();
                if (next < 0) throw new EOFException();
                if (next == '\n') return bytes.toString(StandardCharsets.US_ASCII).stripTrailing();
                bytes.write(next);
            }
            throw new IOException("HTTP line exceeded test budget");
        }
        void send(String token, ControlMessage message) throws IOException {
            sendTogether(ControlCodec.encode(token, message));
        }
        void sendTogether(String... messages) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (String message : messages) {
                byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                assertTrue(payload.length <= 65_535);
                bytes.write(0x81);
                if (payload.length < 126) bytes.write(0x80 | payload.length);
                else { bytes.write(0xFE); bytes.write(payload.length >>> 8); bytes.write(payload.length); }
                bytes.write(new byte[4]); // A zero mask remains a valid masked client frame.
                bytes.write(payload);
            }
            socket.getOutputStream().write(bytes.toByteArray());
            socket.getOutputStream().flush();
        }
        <T extends ControlMessage> T await(Class<T> type) throws IOException {
            for (int count = 0; count < 32; count++) {
                int first = input.read();
                if (first < 0) throw new EOFException("awaiting " + type.getSimpleName());
                int length = input.readUnsignedByte();
                assertEquals(0, length & 0x80, "server frame must be unmasked");
                if (length == 126) length = input.readUnsignedShort();
                else if (length == 127) {
                    long wide = input.readLong();
                    assertTrue(wide >= 0 && wide <= 132_096, "server frame exceeds test budget");
                    length = (int) wide;
                }
                byte[] payload = input.readNBytes(length);
                assertEquals(length, payload.length);
                if ((first & 15) == 8) throw new EOFException("closed awaiting " + type.getSimpleName());
                assertEquals(0x81, first, "expected complete text response");
                ControlMessage message = ControlCodec.decode(new String(payload,
                        StandardCharsets.UTF_8)).message();
                if (type.isInstance(message)) return type.cast(message);
            }
            throw new AssertionError("response budget exhausted awaiting " + type.getSimpleName());
        }
        String admit(PlayerIdentity identity, AuthenticationTranscript.Role role) throws Exception {
            ClientHandshake handshake = new ClientHandshake(identity, "Peer", FP, null, role,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
            send(null, handshake.hello());
            send(null, handshake.onWelcome(await(ControlMessage.Welcome.class)));
            ControlMessage.PowChallenge challenge = await(ControlMessage.PowChallenge.class);
            send(null, new ControlMessage.PowSolution("IDENTITY",
                    identity.creationPowNonce(challenge.difficultyBits())));
            return await(ControlMessage.JoinAccepted.class).sessionToken();
        }
        void awaitClosed() throws IOException {
            for (int budget = 0; budget < 65_536; budget++) {
                if (input.read() < 0) return;
            }
            fail("closed server emitted excessive data");
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
