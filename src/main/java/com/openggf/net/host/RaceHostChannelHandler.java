package com.openggf.net.host;

import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.protocol.Protocol;
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

/** Netty adapter that keeps all room mutations on the host event-loop thread. */
final class RaceHostChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final RoomHost room;
    private final ConnectionHygiene.ConnectionCounter counter;
    private final ConnectionHygiene.RateBucket rateBucket =
            new ConnectionHygiene.RateBucket();
    private HubConnection connection;
    private String remoteHost;
    private boolean acquired;

    RaceHostChannelHandler(RoomHost room, ConnectionHygiene.ConnectionCounter counter) {
        this.room = room;
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
            connection = new NettyHubConnection(context.channel(), remoteHost);
            room.onConnected(connection);
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
            room.onText(connection, text.text());
        } else if (frame instanceof BinaryWebSocketFrame binary) {
            if (binary.content().readableBytes() > Protocol.MAX_BINARY_BYTES) {
                context.close();
                return;
            }
            room.onBinary(connection, ByteBufUtil.getBytes(binary.content()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        if (connection != null) {
            room.onDisconnected(connection);
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

    private record NettyHubConnection(Channel channel, String remoteHost) implements HubConnection {
        @Override
        public void sendText(String text) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }

        @Override
        public void sendBinary(byte[] data) {
            channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
        }

        @Override
        public void close(String reason) {
            channel.close();
        }

        @Override
        public int queuedBytes() {
            io.netty.channel.ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
            return buffer == null ? 0 : (int) Math.min(
                    Integer.MAX_VALUE, buffer.totalPendingWriteBytes());
        }
    }
}
