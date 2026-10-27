package openggf.racing.server.master;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.flow.WorkBudget;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Netty adapter routing one master socket between broker, relay room, and tunnel modes. */
final class MasterChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private enum Mode { BROKER, ROOM, TUNNEL_GUEST }
    private sealed interface PendingFrame {
        long retainedBytes();
        WorkBudget.Reservation reservation();
    }
    private record PendingText(String text, long retainedBytes,
                               WorkBudget.Reservation reservation) implements PendingFrame { }
    private record PendingBinary(byte[] data, long retainedBytes,
                                 WorkBudget.Reservation reservation) implements PendingFrame { }
    private record PendingIgnored(long retainedBytes,
                                  WorkBudget.Reservation reservation) implements PendingFrame { }
    private record PausedFrame(WebSocketFrame frame, WorkBudget.Reservation reservation) { }
    // Classification has not yet identified the recipient: allow one burst for
    // each supported direct-room player while keeping unvalidated work bounded.
    private static final int MAX_PENDING_BROKER_FRAMES =
            ConnectionHygiene.MESSAGE_RATE_BURST * Protocol.MAX_PLAYERS_DIRECT;
    // One full-size binary burst for all eight supported direct-room guests fits.
    // Text can retain more memory than its UTF-8 payload, so account it at 2x.
    private static final long MAX_PENDING_BROKER_BYTES = 4L * 1024 * 1024;

    private final RoomBroker broker;
    private final RelayRoomManager relays;
    private final GuestTunnelRouter tunnels;
    private final Executor brokerLoop;
    private final ConnectionHygiene.ConnectionCounter counter;
    private final ConnectionHygiene.RateBucket rateBucket =
            new ConnectionHygiene.RateBucket();
    private final WorkBudget.Account sourceWork = WorkBudget.PROCESS.account(
            MAX_PENDING_BROKER_FRAMES + 1,
            8L * 1024 * 1024);
    private final WorkBudget.Account lifecycleWork = WorkBudget.PROCESS.account(3, 1);
    private final WorkBudget.Account deferredFrameWork = WorkBudget.DEFERRED.account(1,
            2L * Protocol.MAX_MASTER_FRAME_BYTES);
    private WorkBudget.Reservation brokerConnect;
    private WorkBudget.Reservation brokerDisconnect;
    private final AtomicReference<WorkBudget.Reservation> attachDisconnect = new AtomicReference<>();

    private HubConnection connection;
    private String remoteHost;
    private boolean acquired;
    private Mode mode = Mode.BROKER;
    private RelayRoomManager.RoomAccess roomAccess;
    private int guestId = -1;
    private boolean brokerInFlight;
    private final ArrayDeque<PendingFrame> pendingFrames = new ArrayDeque<>();
    private long pendingBrokerBytes;
    private final AtomicReference<PausedFrame> capacityPending = new AtomicReference<>();
    private final AtomicBoolean deferredFrameEvicted = new AtomicBoolean();
    private PendingFrame transferPending;
    private boolean lifecyclePending;
    private boolean capacityRetryScheduled;

    MasterChannelHandler(RoomBroker broker, RelayRoomManager relays,
                         GuestTunnelRouter tunnels, Executor brokerLoop,
                         ConnectionHygiene.ConnectionCounter counter) {
        this.broker = broker;
        this.relays = relays;
        this.tunnels = tunnels;
        this.brokerLoop = brokerLoop;
        this.counter = counter;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        InetSocketAddress address = (InetSocketAddress) context.channel().remoteAddress();
        remoteHost = address.getAddress().getHostAddress();
        acquired = counter.tryAcquire(remoteHost);
        if (!acquired) {
            context.close();
            return;
        }
        deferredFrameWork.onPressure(() -> {
            deferredFrameEvicted.set(true);
            PausedFrame paused = capacityPending.getAndSet(null);
            if (paused != null) {
                paused.frame().release();
                paused.reservation().close();
            }
            context.close();
        });
        tryActivateLifecycle(context);
    }

    private void tryActivateLifecycle(ChannelHandlerContext context) throws Exception {
        var reserved = WorkBudget.PROCESS.tryReserveBatchDetailed(lifecycleWork, broker.lifecycleWork(),
                List.of(new WorkBudget.Cost(1, 0), new WorkBudget.Cost(1, 0),
                        new WorkBudget.Cost(1, 0)));
        if (!reserved.accepted()) {
            if (reserved.refusal() != WorkBudget.Refusal.ORIGIN) {
                lifecyclePending = true;
                context.channel().config().setAutoRead(false);
                scheduleCapacityRetry(context);
                return;
            }
            context.close();
            return;
        }
        lifecyclePending = false;
        brokerConnect = reserved.value().get(0);
        brokerDisconnect = reserved.value().get(1);
        attachDisconnect.set(reserved.value().get(2));
        super.channelActive(context);
        context.channel().config().setAutoRead(true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            if (brokerConnect == null || connection != null || !context.channel().isActive()) return;
            HubConnection connected = new NettyMasterConnection(context.channel(), remoteHost);
            connection = connected;
            WorkBudget.Reservation reserved = brokerConnect;
            brokerConnect = null;
            submitLifecycle(context, brokerLoop, () -> broker.onConnected(connected), reserved);
            return;
        }
        if (event instanceof IdleStateEvent) {
            if (lifecyclePending || capacityPending.get() != null || transferPending != null) return;
            context.close();
            return;
        }
        super.userEventTriggered(context, event);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) {
        if (lifecyclePending || capacityPending.get() != null) {
            // Auto-read has stopped. A second already-decoded frame exceeds the
            // one-frame uncharged handoff allowance for this source.
            context.close();
            return;
        }
        if (transferPending != null) {
            deferFrame(context, frame);
            return;
        }
        if (connection == null) {
            context.close();
            return;
        }
        int readableBytes = frame.content().readableBytes();
        if (mode == Mode.BROKER) {
            // Broker binary is a relay wrapper with a 4 KiB inner packet.
            if (frame instanceof BinaryWebSocketFrame
                    && readableBytes > GhostPackets.MAX_RELAY_GUEST_BINARY_BYTES) {
                clearPendingFrames();
                context.close();
                return;
            }
        } else if (frame instanceof BinaryWebSocketFrame
                && readableBytes > Protocol.MAX_MASTER_FRAME_BYTES) {
            context.close();
            return;
        }
        long retainedBytes = frame instanceof TextWebSocketFrame
                ? 2L * readableBytes : readableBytes;
        if (mode == Mode.BROKER) {
            if (brokerInFlight && (pendingFrames.size() >= MAX_PENDING_BROKER_FRAMES
                    || retainedBytes > MAX_PENDING_BROKER_BYTES - pendingBrokerBytes)) {
                clearPendingFrames();
                context.close();
                return;
            }
        }
        WorkBudget.Account destination = mode == Mode.ROOM && roomAccess != null
                ? roomAccess.inboundWork() : broker.inboundWork();
        var acquired = WorkBudget.PROCESS.tryReserveDetailed(sourceWork, destination,
                new WorkBudget.Cost(1, retainedBytes));
        if (!acquired.accepted()) {
            if (acquired.refusal() != WorkBudget.Refusal.ORIGIN) {
                deferFrame(context, frame);
                return;
            }
            clearPendingFrames();
            context.close();
            return;
        }
        PendingFrame pending;
        try {
            if (frame instanceof TextWebSocketFrame text) {
                pending = new PendingText(text.text(), retainedBytes, acquired.value());
            } else if (frame instanceof BinaryWebSocketFrame binary) {
                pending = new PendingBinary(ByteBufUtil.getBytes(binary.content()),
                        retainedBytes, acquired.value());
            } else {
                pending = new PendingIgnored(retainedBytes, acquired.value());
            }
        } catch (RuntimeException | Error failure) {
            acquired.value().close();
            throw failure;
        }
        if (brokerInFlight) {
            // Auto-read pauses new socket reads; frames already decoded by the pipeline
            // still need a bound while authentication/ownership runs on the broker loop.
            pendingFrames.addLast(pending);
            pendingBrokerBytes += pending.retainedBytes();
            return;
        }
        routeFrame(context, pending);
    }

    private void routeFrame(ChannelHandlerContext context, PendingFrame frame) {
        HubConnection current = connection;
        if (mode == Mode.BROKER) {
            brokerInFlight = true;
            context.channel().config().setAutoRead(false);
            try {
                brokerLoop.execute(() -> routeBrokerFrame(context, current, frame));
            } catch (RuntimeException | Error stopped) {
                frame.reservation().close();
                context.close();
                if (!(stopped instanceof RejectedExecutionException)) throw stopped;
            }
            return;
        }
        WorkBudget.Account destination = mode == Mode.ROOM && roomAccess != null
                ? roomAccess.inboundWork() : broker.inboundWork();
        if (!frame.reservation().transfer(destination)) {
            transferPending = frame;
            context.channel().config().setAutoRead(false);
            scheduleCapacityRetry(context);
            return;
        }
        // After attachment, the channel carries one guest's traffic again. Its
        // previous broker task has completed before this IO-loop mode transition.
        if (!rateBucket.consume()) {
            frame.reservation().close();
            context.close();
            return;
        }
        switch (mode) {
            case ROOM -> {
                RelayRoomManager.RoomAccess access = roomAccess;
                if (access == null) {
                    frame.reservation().close();
                    context.close();
                    return;
                }
                switch (frame) {
                    case PendingText text -> {
                        if (RoomBroker.isPasswordSubmission(text.text())) {
                            try {
                                brokerLoop.execute(() -> {
                                    if (broker.allowPasswordSubmission(current)) {
                                        submitRetained(context, access.loop(),
                                                () -> access.room().onText(current, text.text()), frame);
                                    } else {
                                        frame.reservation().close();
                                        current.close("room admission rate limited");
                                    }
                                });
                            } catch (RuntimeException | Error stopped) {
                                frame.reservation().close();
                                context.close();
                                if (!(stopped instanceof RejectedExecutionException)) throw stopped;
                            }
                        } else {
                            submitRetained(context, access.loop(),
                                    () -> access.room().onText(current, text.text()), frame);
                        }
                    }
                    case PendingBinary binary -> submitRetained(context, access.loop(),
                            () -> access.room().onBinary(current, binary.data()), frame);
                    case PendingIgnored ignored -> frame.reservation().close();
                }
            }
            case TUNNEL_GUEST -> {
                switch (frame) {
                    case PendingText text -> submitRetained(context, brokerLoop,
                            () -> tunnels.guestText(guestId, text.text()), frame);
                    case PendingBinary binary -> submitRetained(context, brokerLoop,
                            () -> tunnels.guestBinary(guestId, binary.data()), frame);
                    case PendingIgnored ignored -> frame.reservation().close();
                }
            }
            case BROKER -> throw new IllegalStateException("broker frames require serialized dispatch");
        }
    }

    private static void submitRetained(ChannelHandlerContext context, Executor loop,
                                       Runnable action, PendingFrame frame) {
        try {
            loop.execute(() -> {
                try { action.run(); }
                finally { frame.reservation().close(); }
            });
        } catch (RuntimeException | Error stopped) {
            frame.reservation().close();
            context.close();
            if (!(stopped instanceof RejectedExecutionException)) throw stopped;
        }
    }

    private void routeBrokerFrame(ChannelHandlerContext context, HubConnection current,
                                  PendingFrame frame) {
        boolean handedBack = false;
        try {
            boolean accepted = switch (frame) {
                case PendingText text -> routeBrokerText(context, current, text.text());
                case PendingBinary binary -> routeBrokerBinary(context, current, binary.data());
                case PendingIgnored ignored -> consumeBrokerQuota(context);
            };
            if (!accepted) return;
            var attach = broker.takeAttachResult(current);
            context.executor().execute(() -> {
                try {
                    brokerInFlight = false;
                    if (!context.channel().isActive()) {
                        // channelInactive observed BROKER mode, so release the broker result here.
                        attach.ifPresent(result -> releaseAttach(current, result));
                        clearPendingFrames();
                        return;
                    }
                    attach.ifPresent(result -> applyAttach(context, result));
                    if (context.channel().isActive()) drainPending(context);
                    if (context.channel().isActive() && transferPending == null
                            && capacityPending.get() == null && !brokerInFlight) {
                        context.channel().config().setAutoRead(true);
                    }
                } finally {
                    frame.reservation().close();
                    if (!context.channel().isActive()) releaseAttachCell();
                }
            });
            handedBack = true;
        } catch (RuntimeException | Error e) {
            context.close();
            if (!(e instanceof RejectedExecutionException)) throw e;
        } finally {
            if (!handedBack) {
                frame.reservation().close();
                releaseAttachCell();
            }
        }
    }

    private boolean consumeBrokerQuota(ChannelHandlerContext context) {
        if (!rateBucket.consume()) {
            context.executor().execute(context::close);
            return false;
        }
        return true;
    }

    private boolean routeBrokerText(ChannelHandlerContext context, HubConnection current,
                                    String text) {
        final ControlCodec.DecodedControl decoded;
        try {
            decoded = ControlCodec.decodeMasterClient(text, Protocol.MAX_MASTER_FRAME_BYTES);
        } catch (ProtocolViolationException invalid) {
            if (!consumeBrokerQuota(context)) return false;
            throw invalid;
        }
        int target = switch (decoded.message()) {
            case ControlMessage.RelayGuestText relay -> relay.guestId();
            case ControlMessage.RelayGuestClose relay -> relay.guestId();
            default -> -1;
        };
        if (target >= 0 && broker.acceptsToken(current, decoded.token())
                && tunnels.ownsTunnel(current, target)) {
            tunnels.onHostControl(current, decoded.message());
        } else {
            if (!consumeBrokerQuota(context)) return false;
            broker.onText(current, text);
        }
        return true;
    }

    private boolean routeBrokerBinary(ChannelHandlerContext context, HubConnection current,
                                      byte[] data) {
        if (!GhostPackets.hasRelayGuestBinaryHeader(data)) {
            if (!consumeBrokerQuota(context)) return false;
            context.executor().execute(context::close);
            return false;
        }
        GhostPackets.RelayGuestBinary wrapped = null;
        try {
            wrapped = GhostPackets.decodeRelayGuestBinary(data);
        } catch (ProtocolViolationException ignored) {
            // Malformed wrappers never receive the host fan-out quota exemption.
        }
        if (wrapped != null && broker.isAdmitted(current)
                && tunnels.ownsTunnel(current, wrapped.guestId())) {
            tunnels.onHostBinary(current, data);
            return true;
        }
        return consumeBrokerQuota(context);
    }

    private void releaseAttach(HubConnection current, RoomBroker.AttachResult result) {
        WorkBudget.Reservation reserved = attachDisconnect.getAndSet(null);
        if (reserved == null) return;
        if (result.mode() == RoomBroker.AttachMode.ROOM) {
            var access = relays.find(result.roomId());
            if (access.isPresent()) submitRoomRetirement(null, access.get(), current, reserved);
            else reserved.close();
        } else {
            submitLifecycle(null, brokerLoop,
                    () -> tunnels.guestDisconnected(result.guestId()), reserved);
        }
    }

    private void releaseAttachCell() {
        WorkBudget.Reservation reserved = attachDisconnect.getAndSet(null);
        if (reserved != null) reserved.close();
    }

    private static void submitRoomRetirement(ChannelHandlerContext context,
                                             RelayRoomManager.RoomAccess access,
                                             HubConnection peer, WorkBudget.Reservation reserved) {
        if (!reserved.transfer(access.lifecycleWork())) {
            reserved.close();
            throw new IllegalStateException("reserved room retirement capacity unavailable");
        }
        submitLifecycle(context, access.loop(), () -> access.room().onDisconnected(peer), reserved);
    }

    private static void submitLifecycle(ChannelHandlerContext context, Executor loop,
                                        Runnable action, WorkBudget.Reservation reserved) {
        try {
            loop.execute(() -> {
                try { action.run(); }
                finally { reserved.close(); }
            });
        } catch (RuntimeException | Error stopped) {
            reserved.close();
            if (context != null) context.close();
            if (!(stopped instanceof RejectedExecutionException)) throw stopped;
        }
    }

    private void drainPending(ChannelHandlerContext context) {
        while (!brokerInFlight && transferPending == null && !pendingFrames.isEmpty()
                && context.channel().isActive()) {
            PendingFrame pending = pendingFrames.removeFirst();
            pendingBrokerBytes -= pending.retainedBytes();
            routeFrame(context, pending);
        }
    }

    private void clearPendingFrames() {
        PendingFrame frame;
        while ((frame = pendingFrames.pollFirst()) != null) frame.reservation().close();
        pendingBrokerBytes = 0;
    }

    private void deferFrame(ChannelHandlerContext context, WebSocketFrame frame) {
        long bytes = frame instanceof TextWebSocketFrame
                ? 2L * frame.content().readableBytes() : frame.content().readableBytes();
        WorkBudget.Cost cost = new WorkBudget.Cost(1, bytes);
        var admission = WorkBudget.DEFERRED.tryReserve(deferredFrameWork, deferredFrameWork, cost);
        while (admission.isEmpty() && WorkBudget.DEFERRED.evictHeaviest(deferredFrameWork))
            admission = WorkBudget.DEFERRED.tryReserve(deferredFrameWork, deferredFrameWork, cost);
        if (admission.isEmpty()) {
            context.close();
            return;
        }
        PausedFrame paused = new PausedFrame(frame.retain(), admission.get());
        if (!capacityPending.compareAndSet(null, paused)) {
            paused.frame().release();
            paused.reservation().close();
            context.close();
            return;
        }
        if (deferredFrameEvicted.get()) {
            PausedFrame evicted = capacityPending.getAndSet(null);
            if (evicted != null) {
                evicted.frame().release();
                evicted.reservation().close();
            }
            context.close();
            return;
        }
        context.channel().config().setAutoRead(false);
        scheduleCapacityRetry(context);
    }

    private void scheduleCapacityRetry(ChannelHandlerContext context) {
        if (capacityRetryScheduled) return;
        capacityRetryScheduled = true;
        context.executor().schedule(() -> {
            capacityRetryScheduled = false;
            if (!context.channel().isActive()) return;
            if (lifecyclePending) {
                try { tryActivateLifecycle(context); }
                catch (Exception failure) { context.close(); }
            }
            if (lifecyclePending) return;
            if (transferPending != null && context.channel().isActive()) {
                PendingFrame frame = transferPending;
                transferPending = null;
                routeFrame(context, frame);
            }
            PausedFrame paused = capacityPending.getAndSet(null);
            if (paused != null) {
                paused.reservation().close();
                try {
                    if (context.channel().isActive()) channelRead0(context, paused.frame());
                } finally { paused.frame().release(); }
            }
            if (context.channel().isActive() && transferPending == null
                    && capacityPending.get() == null && !brokerInFlight) {
                drainPending(context);
                if (transferPending == null && !brokerInFlight) {
                    context.channel().config().setAutoRead(true);
                }
            }
        }, 50, TimeUnit.MILLISECONDS);
    }

    private void applyAttach(ChannelHandlerContext context, RoomBroker.AttachResult result) {
        if (result.mode() == RoomBroker.AttachMode.ROOM) {
            roomAccess = relays.find(result.roomId()).orElse(null);
            if (roomAccess == null) {
                context.close();
                return;
            }
            WorkBudget.Reservation reserved = attachDisconnect.get();
            if (reserved == null || !reserved.transfer(roomAccess.lifecycleWork())) {
                context.close();
                return;
            }
            mode = Mode.ROOM;
        } else {
            guestId = result.guestId();
            mode = Mode.TUNNEL_GUEST;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        clearPendingFrames();
        deferredFrameWork.onPressure(null);
        PausedFrame paused = capacityPending.getAndSet(null);
        if (paused != null) {
            paused.frame().release();
            paused.reservation().close();
        }
        if (transferPending != null) {
            transferPending.reservation().close();
            transferPending = null;
        }
        lifecyclePending = false;
        if (connection != null) {
            HubConnection disconnected = connection;
            if (disconnected instanceof NettyMasterConnection netty) netty.discardOutbound();
            RelayRoomManager.RoomAccess disconnectedRoom = roomAccess;
            int disconnectedGuestId = guestId;
            if (mode == Mode.ROOM && roomAccess != null) {
                WorkBudget.Reservation reserved = attachDisconnect.getAndSet(null);
                if (reserved != null) submitRoomRetirement(context, disconnectedRoom,
                        disconnected, reserved);
            } else if (mode == Mode.TUNNEL_GUEST && guestId >= 0) {
                WorkBudget.Reservation reserved = attachDisconnect.getAndSet(null);
                if (reserved != null) submitLifecycle(context, brokerLoop,
                        () -> tunnels.guestDisconnected(disconnectedGuestId), reserved);
            } else if (!brokerInFlight) {
                releaseAttachCell();
            }
            WorkBudget.Reservation reserved = brokerDisconnect;
            brokerDisconnect = null;
            if (reserved != null) submitLifecycle(context, brokerLoop,
                    () -> broker.onDisconnected(disconnected), reserved);
            connection = null;
        } else {
            releaseAttachCell();
        }
        if (brokerConnect != null) { brokerConnect.close(); brokerConnect = null; }
        if (brokerDisconnect != null) { brokerDisconnect.close(); brokerDisconnect = null; }
        if (acquired) {
            counter.release(remoteHost);
            acquired = false;
        }
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        context.close();
    }

    private static final class NettyMasterConnection
            implements HubConnection, com.openggf.net.hub.HandshakeTransport {
        private record DeferredWrite(long bytes, Supplier<WebSocketFrame> encoding,
                                     WorkBudget.Reservation reservation) { }
        private static final long WRITE_STALL_MILLIS = 2_000;
        private final Channel channel;
        private final String remoteHost;
        private final WorkBudget.Account outboundWork = WorkBudget.PROCESS.account(512,
                8L * 1024 * 1024);
        private final WorkBudget.Account deferredWork = WorkBudget.DEFERRED.account(512,
                8L * 1024 * 1024);
        private final Set<WorkBudget.Reservation> retainedWrites =
                ConcurrentHashMap.newKeySet();
        private final ConcurrentLinkedDeque<DeferredWrite> deferredWrites = new ConcurrentLinkedDeque<>();
        private final AtomicLong deferredBytes = new AtomicLong();
        private final AtomicBoolean pressureRetired = new AtomicBoolean();
        private boolean retryScheduled;

        private NettyMasterConnection(Channel channel, String remoteHost) {
            this.channel = channel;
            this.remoteHost = remoteHost;
            deferredWork.onPressure(this::evictDeferred);
        }

        private synchronized void discardOutbound() {
            pressureRetired.set(true);
            deferredWork.onPressure(null);
            DeferredWrite waiting;
            while ((waiting = deferredWrites.pollFirst()) != null) {
                deferredBytes.addAndGet(-waiting.bytes());
                waiting.reservation().close();
            }
            retainedWrites.forEach(WorkBudget.Reservation::close);
            retainedWrites.clear();
        }

        private void evictDeferred() {
            if (!pressureRetired.compareAndSet(false, true)) return;
            DeferredWrite waiting;
            while ((waiting = deferredWrites.pollFirst()) != null) {
                deferredBytes.addAndGet(-waiting.bytes());
                waiting.reservation().close();
            }
            channel.close();
        }

        private synchronized void write(long bytes, Supplier<WebSocketFrame> encoding) {
            if (pressureRetired.get() || !channel.isActive()) return;
            if (!deferredWrites.isEmpty()) {
                defer(bytes, encoding);
                return;
            }
            var admission = WorkBudget.PROCESS.tryReserveDetailed(outboundWork,
                    outboundWork, new WorkBudget.Cost(1, bytes));
            if (!admission.accepted()) {
                if (admission.refusal() == WorkBudget.Refusal.PROCESS) defer(bytes, encoding);
                else channel.close();
                return;
            }
            send(admission.value(), encoding);
        }

        private void defer(long bytes, Supplier<WebSocketFrame> encoding) {
            if (pressureRetired.get()) return;
            if (outboundWork.retainedItems() + deferredWrites.size() >= 512
                    || bytes > 8L * 1024 * 1024 - outboundWork.retainedBytes()
                    - deferredBytes.get()) {
                channel.close();
                return;
            }
            WorkBudget.Cost cost = new WorkBudget.Cost(1, bytes);
            var reserved = WorkBudget.DEFERRED.tryReserve(deferredWork, deferredWork, cost);
            while (reserved.isEmpty() && WorkBudget.DEFERRED.evictHeaviest(deferredWork))
                reserved = WorkBudget.DEFERRED.tryReserve(deferredWork, deferredWork, cost);
            if (reserved.isEmpty()) {
                channel.close();
                return;
            }
            if (pressureRetired.get()) {
                reserved.get().close();
                return;
            }
            DeferredWrite waiting = new DeferredWrite(bytes, encoding, reserved.get());
            deferredWrites.addLast(waiting);
            deferredBytes.addAndGet(bytes);
            if (pressureRetired.get()) {
                if (deferredWrites.remove(waiting)) {
                    deferredBytes.addAndGet(-bytes);
                    waiting.reservation().close();
                }
                return;
            }
            scheduleRetry();
        }

        private synchronized void scheduleRetry() {
            if (pressureRetired.get() || retryScheduled || deferredWrites.isEmpty()) return;
            retryScheduled = true;
            channel.eventLoop().schedule(this::retryDeferred, 50, TimeUnit.MILLISECONDS);
        }

        private synchronized void retryDeferred() {
            retryScheduled = false;
            if (pressureRetired.get() || !channel.isActive()) {
                DeferredWrite waiting;
                while ((waiting = deferredWrites.pollFirst()) != null) {
                    deferredBytes.addAndGet(-waiting.bytes());
                    waiting.reservation().close();
                }
                return;
            }
            while (!deferredWrites.isEmpty()) {
                DeferredWrite next = deferredWrites.peekFirst();
                var admission = WorkBudget.PROCESS.tryReserveDetailed(outboundWork,
                        outboundWork, new WorkBudget.Cost(1, next.bytes()));
                if (!admission.accepted()) {
                    scheduleRetry();
                    return;
                }
                if (!deferredWrites.remove(next)) {
                    admission.value().close();
                    continue;
                }
                deferredBytes.addAndGet(-next.bytes());
                next.reservation().close();
                if (pressureRetired.get()) admission.value().close();
                else send(admission.value(), next.encoding());
            }
        }

        private void send(WorkBudget.Reservation reservation, Supplier<WebSocketFrame> encoding) {
            retainedWrites.add(reservation);
            WebSocketFrame frame = null;
            java.util.concurrent.ScheduledFuture<?> deadline = null;
            try {
                deadline = channel.eventLoop().schedule(() -> {
                    if (retainedWrites.contains(reservation) && channel.isActive()) channel.close();
                }, WRITE_STALL_MILLIS, TimeUnit.MILLISECONDS);
                java.util.concurrent.ScheduledFuture<?> scheduled = deadline;
                frame = encoding.get();
                channel.writeAndFlush(frame).addListener(done -> {
                    scheduled.cancel(false);
                    retainedWrites.remove(reservation);
                    reservation.close();
                    if (!done.isSuccess() && channel.isActive()) channel.close();
                    else scheduleRetry();
                });
            } catch (RuntimeException | Error failure) {
                if (deadline != null) deadline.cancel(false);
                if (frame != null) ReferenceCountUtil.release(frame);
                retainedWrites.remove(reservation);
                reservation.close();
                channel.close();
                throw failure;
            }
        }

        private static long utf8Bytes(String text) {
            return text.codePoints().mapToLong(codePoint -> codePoint < 0x80 ? 1
                    : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4).sum();
        }

        @Override public String authenticationBinding() {
            return openggf.racing.server.host.NettyHandshakeBinding.localCertificate(channel, remoteHost);
        }
        @Override public com.openggf.net.protocol.AuthenticationTranscript.Role authenticationRole() {
            return com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY;
        }
        @Override public void sendText(String text) {
            write(utf8Bytes(text) + 16, () -> new TextWebSocketFrame(text));
        }
        @Override public void sendBinary(byte[] data) {
            byte[] owned = data.clone();
            write((long) owned.length + 16,
                    () -> new BinaryWebSocketFrame(Unpooled.wrappedBuffer(owned)));
        }
        @Override public void close(String reason) { channel.close(); }
        @Override public String remoteHost() { return remoteHost; }
        @Override public int queuedBytes() {
            var buffer = channel.unsafe().outboundBuffer();
            return (int) Math.min(Integer.MAX_VALUE, deferredBytes.get()
                    + (buffer == null ? 0 : buffer.totalPendingWriteBytes()));
        }
    }
}
