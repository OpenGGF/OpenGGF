package com.openggf.net.host;

import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Player-host endpoint for guests tunneled through the host's master connection. */
@com.openggf.game.ModApi
public final class HostMasterLink {
    @com.openggf.game.ModApi
    public interface MessageSink {
        void sendControl(ControlMessage message);
        void sendBinary(byte[] data);
    }

    private final Consumer<HubConnection> onConnected;
    private final BiConsumer<HubConnection, String> onText;
    private final BiConsumer<HubConnection, byte[]> onBinary;
    private final Consumer<HubConnection> onDisconnected;
    private final MessageSink sink;
    private final Map<Integer, HubConnection> guests = new HashMap<>();

    public HostMasterLink(Consumer<HubConnection> onConnected,
                          BiConsumer<HubConnection, String> onText,
                          BiConsumer<HubConnection, byte[]> onBinary,
                          Consumer<HubConnection> onDisconnected,
                          MessageSink sink) {
        this.onConnected = onConnected;
        this.onText = onText;
        this.onBinary = onBinary;
        this.onDisconnected = onDisconnected;
        this.sink = sink;
    }

    public static HostMasterLink forServer(RaceHostServer server, MessageSink sink) {
        return new HostMasterLink(
                connection -> server.execute(() -> server.room().onConnected(connection)),
                (connection, text) -> server.execute(() ->
                        server.room().onText(connection, text)),
                (connection, data) -> server.execute(() ->
                        server.room().onBinary(connection, data)),
                connection -> server.execute(() ->
                        server.room().onDisconnected(connection)), sink);
    }

    public void onMasterControl(ControlMessage message) {
        switch (message) {
            case ControlMessage.RelayGuestOpen open -> {
                HubConnection connection = new TunnelConnection(open.guestId());
                HubConnection previous = guests.put(open.guestId(), connection);
                if (previous != null) {
                    onDisconnected.accept(previous);
                }
                onConnected.accept(connection);
            }
            case ControlMessage.RelayGuestText text -> {
                HubConnection connection = guests.get(text.guestId());
                if (connection != null) {
                    onText.accept(connection, text.text());
                }
            }
            case ControlMessage.RelayGuestClose close -> {
                HubConnection connection = guests.remove(close.guestId());
                if (connection != null) {
                    onDisconnected.accept(connection);
                }
            }
            default -> { }
        }
    }

    public void onMasterBinary(byte[] packet) {
        try {
            GhostPackets.RelayGuestBinary wrapped =
                    GhostPackets.decodeRelayGuestBinary(packet);
            HubConnection connection = guests.get(wrapped.guestId());
            if (connection != null) {
                onBinary.accept(connection, wrapped.payload());
            }
        } catch (ProtocolViolationException ignored) {
            // The host master pump may also carry unrelated master binary traffic.
        }
    }

    private final class TunnelConnection implements HubConnection {
        private final int guestId;

        private TunnelConnection(int guestId) {
            this.guestId = guestId;
        }

        @Override public void sendText(String text) {
            sink.sendControl(new ControlMessage.RelayGuestText(guestId, text));
        }

        @Override public void sendBinary(byte[] data) {
            sink.sendBinary(GhostPackets.encodeRelayGuestBinary(guestId, data));
        }

        @Override public void close(String reason) {
            if (guests.remove(guestId, this)) {
                sink.sendControl(new ControlMessage.RelayGuestClose(guestId, reason));
            }
        }

        @Override public String remoteHost() {
            return "relay:" + guestId;
        }
    }
}
