package openggf.racing.server.master;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.hub.HubConnection;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
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
import java.util.concurrent.Executor;

/** Netty adapter routing one master socket between broker, relay room, and tunnel modes. */
final class MasterChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private enum Mode { BROKER, ROOM, TUNNEL_GUEST }
    private sealed interface PendingFrame { long retainedBytes(); }
    private record PendingText(String text, long retainedBytes) implements PendingFrame { }
    private record PendingBinary(byte[] data, long retainedBytes) implements PendingFrame { }
    private record PendingIgnored(long retainedBytes) implements PendingFrame { }
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

    private HubConnection connection;
    private String remoteHost;
    private boolean acquired;
    private Mode mode = Mode.BROKER;
    private RelayRoomManager.RoomAccess roomAccess;
    private int guestId = -1;
    private boolean brokerInFlight;
    private final ArrayDeque<PendingFrame> pendingFrames = new ArrayDeque<>();
    private long pendingBrokerBytes;

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
        super.channelActive(context);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            HubConnection connected = new NettyMasterConnection(context.channel(), remoteHost);
            connection = connected;
            brokerLoop.execute(() -> broker.onConnected(connected));
            return;
        }
        if (event instanceof IdleStateEvent) {
            context.close();
            return;
        }
        super.userEventTriggered(context, event);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) {
        if (connection == null) {
            context.close();
            return;
        }
        long retainedBytes = 0;
        if (mode == Mode.BROKER) {
            int readableBytes = frame.content().readableBytes();
            // Broker binary is always a RelayGuestBinary wrapper, whose outer cap
            // is 4 KiB. Reject larger data before copying it into the backlog.
            if (frame instanceof BinaryWebSocketFrame
                    && readableBytes > Protocol.MAX_BINARY_BYTES) {
                clearPendingFrames();
                context.close();
                return;
            }
            retainedBytes = frame instanceof TextWebSocketFrame
                    ? 2L * readableBytes : readableBytes;
            if (brokerInFlight && (pendingFrames.size() >= MAX_PENDING_BROKER_FRAMES
                    || retainedBytes > MAX_PENDING_BROKER_BYTES - pendingBrokerBytes)) {
                clearPendingFrames();
                context.close();
                return;
            }
        } else if (frame instanceof BinaryWebSocketFrame binary
                && binary.content().readableBytes() > Protocol.MAX_MASTER_FRAME_BYTES) {
            context.close();
            return;
        }
        PendingFrame pending;
        if (frame instanceof TextWebSocketFrame text) {
            pending = new PendingText(text.text(), retainedBytes);
        } else if (frame instanceof BinaryWebSocketFrame binary) {
            pending = new PendingBinary(ByteBufUtil.getBytes(binary.content()), retainedBytes);
        } else {
            pending = new PendingIgnored(retainedBytes);
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
            brokerLoop.execute(() -> routeBrokerFrame(context, current, frame));
            return;
        }
        // After attachment, the channel carries one guest's traffic again. Its
        // previous broker task has completed before this IO-loop mode transition.
        if (!rateBucket.consume()) {
            context.close();
            return;
        }
        switch (mode) {
            case ROOM -> {
                switch (frame) {
                    case PendingText text -> roomAccess.loop().execute(() ->
                            roomAccess.room().onText(current, text.text()));
                    case PendingBinary binary -> roomAccess.loop().execute(() ->
                            roomAccess.room().onBinary(current, binary.data()));
                    case PendingIgnored ignored -> { }
                }
            }
            case TUNNEL_GUEST -> {
                switch (frame) {
                    case PendingText text -> brokerLoop.execute(() -> tunnels.guestText(guestId, text.text()));
                    case PendingBinary binary -> brokerLoop.execute(() -> tunnels.guestBinary(guestId, binary.data()));
                    case PendingIgnored ignored -> { }
                }
            }
            case BROKER -> throw new IllegalStateException("broker frames require serialized dispatch");
        }
    }

    private void routeBrokerFrame(ChannelHandlerContext context, HubConnection current,
                                  PendingFrame frame) {
        try {
            boolean accepted = switch (frame) {
                case PendingText text -> routeBrokerText(context, current, text.text());
                case PendingBinary binary -> routeBrokerBinary(context, current, binary.data());
                case PendingIgnored ignored -> consumeBrokerQuota(context);
            };
            if (!accepted) return;
            var attach = broker.takeAttachResult(current);
            context.executor().execute(() -> {
                brokerInFlight = false;
                if (!context.channel().isActive()) {
                    // The broker may hand off ownership before the IO loop observes
                    // it. Inactive ran in BROKER mode, so release that new owner here.
                    attach.ifPresent(result -> releaseAttach(current, result));
                    clearPendingFrames();
                    return;
                }
                attach.ifPresent(result -> applyAttach(context, result));
                if (context.channel().isActive()) {
                    context.channel().config().setAutoRead(true);
                    context.read();
                    drainPending(context);
                }
            });
        } catch (RuntimeException e) {
            context.executor().execute(context::close);
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
            decoded = ControlCodec.decode(text, Protocol.MAX_MASTER_FRAME_BYTES);
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
        if (data.length == 0 || (data[0] & 0xFF) != GhostPackets.TYPE_RELAY_GUEST_BINARY) {
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
        if (result.mode() == RoomBroker.AttachMode.ROOM) {
            relays.find(result.roomId()).ifPresent(access -> access.loop().execute(
                    () -> access.room().onDisconnected(current)));
        } else {
            brokerLoop.execute(() -> tunnels.guestDisconnected(result.guestId()));
        }
    }

    private void drainPending(ChannelHandlerContext context) {
        while (!brokerInFlight && !pendingFrames.isEmpty()
                && context.channel().isActive()) {
            PendingFrame pending = pendingFrames.removeFirst();
            pendingBrokerBytes -= pending.retainedBytes();
            routeFrame(context, pending);
        }
    }

    private void clearPendingFrames() {
        pendingFrames.clear();
        pendingBrokerBytes = 0;
    }

    private void applyAttach(ChannelHandlerContext context, RoomBroker.AttachResult result) {
        if (result.mode() == RoomBroker.AttachMode.ROOM) {
            roomAccess = relays.find(result.roomId()).orElse(null);
            if (roomAccess == null) {
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
        if (connection != null) {
            HubConnection disconnected = connection;
            RelayRoomManager.RoomAccess disconnectedRoom = roomAccess;
            int disconnectedGuestId = guestId;
            if (mode == Mode.ROOM && roomAccess != null) {
                disconnectedRoom.loop().execute(() ->
                        disconnectedRoom.room().onDisconnected(disconnected));
            } else if (mode == Mode.TUNNEL_GUEST && guestId >= 0) {
                brokerLoop.execute(() -> tunnels.guestDisconnected(disconnectedGuestId));
            }
            brokerLoop.execute(() -> broker.onDisconnected(disconnected));
            connection = null;
        }
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

    private record NettyMasterConnection(Channel channel, String remoteHost)
            implements HubConnection, com.openggf.net.hub.HandshakeTransport {
        @Override public String authenticationBinding() {
            return openggf.racing.server.host.NettyHandshakeBinding.localCertificate(channel, remoteHost);
        }
        @Override public com.openggf.net.protocol.AuthenticationTranscript.Role authenticationRole() {
            return com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY;
        }
        @Override public void sendText(String text) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }
        @Override public void sendBinary(byte[] data) {
            channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
        }
        @Override public void close(String reason) { channel.close(); }
        @Override public int queuedBytes() {
            var buffer = channel.unsafe().outboundBuffer();
            return buffer == null ? 0 : (int) Math.min(
                    Integer.MAX_VALUE, buffer.totalPendingWriteBytes());
        }
    }
}
