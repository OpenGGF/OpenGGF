package com.openggf.net.master;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlCodec;
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
    private sealed interface PendingFrame { }
    private record PendingText(String text) implements PendingFrame { }
    private record PendingBinary(byte[] data) implements PendingFrame { }

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
            connection = new NettyMasterConnection(context.channel(), remoteHost);
            brokerLoop.execute(() -> broker.onConnected(connection));
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
        if (connection == null || !rateBucket.consume()) {
            context.close();
            return;
        }
        if (frame instanceof TextWebSocketFrame text) {
            if (brokerInFlight) {
                pendingFrames.addLast(new PendingText(text.text()));
                return;
            }
            routeText(context, text.text());
        } else if (frame instanceof BinaryWebSocketFrame binary) {
            if (binary.content().readableBytes() > Protocol.MAX_MASTER_FRAME_BYTES) {
                context.close();
                return;
            }
            byte[] data = ByteBufUtil.getBytes(binary.content());
            if (brokerInFlight) {
                pendingFrames.addLast(new PendingBinary(data));
                return;
            }
            routeBinary(context, data);
        }
    }

    private void routeText(ChannelHandlerContext context, String text) {
        HubConnection current = connection;
        switch (mode) {
            case ROOM -> roomAccess.loop().execute(() ->
                    roomAccess.room().onText(current, text));
            case TUNNEL_GUEST -> brokerLoop.execute(() -> tunnels.guestText(guestId, text));
            case BROKER -> {
                brokerInFlight = true;
                context.channel().config().setAutoRead(false);
                brokerLoop.execute(() -> routeBrokerText(context, current, text));
            }
        }
    }

    private void routeBrokerText(ChannelHandlerContext context, HubConnection current,
                                 String text) {
        try {
            ControlCodec.DecodedControl decoded = ControlCodec.decode(
                    text, Protocol.MAX_MASTER_FRAME_BYTES);
            if ((decoded.message() instanceof ControlMessage.RelayGuestText
                    || decoded.message() instanceof ControlMessage.RelayGuestClose)
                    && broker.acceptsToken(current, decoded.token())) {
                tunnels.onHostControl(current, decoded.message());
            } else {
                broker.onText(current, text);
            }
            var attach = broker.takeAttachResult(current);
            context.executor().execute(() -> {
                attach.ifPresent(result -> applyAttach(context, result));
                brokerInFlight = false;
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

    private void drainPending(ChannelHandlerContext context) {
        while (!brokerInFlight && !pendingFrames.isEmpty()
                && context.channel().isActive()) {
            switch (pendingFrames.removeFirst()) {
                case PendingText text -> routeText(context, text.text());
                case PendingBinary binary -> routeBinary(context, binary.data());
            }
        }
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

    private void routeBinary(ChannelHandlerContext context, byte[] data) {
        HubConnection current = connection;
        switch (mode) {
            case ROOM -> roomAccess.loop().execute(() ->
                    roomAccess.room().onBinary(current, data));
            case TUNNEL_GUEST -> brokerLoop.execute(() ->
                    tunnels.guestBinary(guestId, data));
            case BROKER -> {
                if (data.length == 0 || (data[0] & 0xFF) != GhostPackets.TYPE_RELAY_GUEST_BINARY) {
                    context.close();
                    return;
                }
                brokerLoop.execute(() -> tunnels.onHostBinary(current, data));
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
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
            implements HubConnection {
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
