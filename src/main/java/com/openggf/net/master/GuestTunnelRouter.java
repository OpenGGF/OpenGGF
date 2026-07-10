package com.openggf.net.master;

import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Master-side pairing and routing for direct-room fallback guests. */
public final class GuestTunnelRouter implements RoomBroker.DirectTunnelDirectory {
    private record Tunnel(String roomId, HubConnection guest, HubConnection host) { }

    private final Map<String, HubConnection> hostsByRoom = new HashMap<>();
    private final Map<Integer, Tunnel> tunnels = new HashMap<>();
    private int nextGuestId;

    @Override
    public void registerHost(String roomId, HubConnection hostConnection) {
        if (hostsByRoom.containsKey(roomId)) {
            unregisterHost(roomId);
        }
        hostsByRoom.put(roomId, hostConnection);
    }

    @Override
    public OptionalInt openGuest(String roomId, HubConnection guestConnection) {
        HubConnection host = hostsByRoom.get(roomId);
        if (host == null) {
            return OptionalInt.empty();
        }
        int guestId = allocateGuestId();
        if (guestId < 0) {
            return OptionalInt.empty();
        }
        tunnels.put(guestId, new Tunnel(roomId, guestConnection, host));
        host.sendText(ControlCodec.encode(null,
                new ControlMessage.RelayGuestOpen(guestId)));
        return OptionalInt.of(guestId);
    }

    public void guestText(int guestId, String text) {
        Tunnel tunnel = tunnels.get(guestId);
        if (tunnel != null) {
            tunnel.host().sendText(ControlCodec.encode(null,
                    new ControlMessage.RelayGuestText(guestId, text)));
        }
    }

    public void guestBinary(int guestId, byte[] packet) {
        Tunnel tunnel = tunnels.get(guestId);
        if (tunnel != null) {
            tunnel.host().sendBinary(GhostPackets.encodeRelayGuestBinary(guestId, packet));
        }
    }

    public void guestDisconnected(int guestId) {
        Tunnel tunnel = tunnels.remove(guestId);
        if (tunnel != null) {
            tunnel.host().sendText(ControlCodec.encode(null,
                    new ControlMessage.RelayGuestClose(guestId, "guest disconnected")));
        }
    }

    public void onHostControl(ControlMessage message) {
        onHostControl(null, message);
    }

    public void onHostControl(HubConnection source, ControlMessage message) {
        switch (message) {
            case ControlMessage.RelayGuestText text -> {
                Tunnel tunnel = tunnels.get(text.guestId());
                if (ownedBy(tunnel, source)) {
                    tunnel.guest().sendText(text.text());
                }
            }
            case ControlMessage.RelayGuestClose close -> {
                Tunnel tunnel = tunnels.get(close.guestId());
                if (ownedBy(tunnel, source)) {
                    tunnels.remove(close.guestId());
                    tunnel.guest().close(close.reason());
                }
            }
            default -> { }
        }
    }

    public void onHostBinary(byte[] wrapped) {
        onHostBinary(null, wrapped);
    }

    public void onHostBinary(HubConnection source, byte[] wrapped) {
        try {
            GhostPackets.RelayGuestBinary payload =
                    GhostPackets.decodeRelayGuestBinary(wrapped);
            Tunnel tunnel = tunnels.get(payload.guestId());
            if (ownedBy(tunnel, source)) {
                tunnel.guest().sendBinary(payload.payload());
            }
        } catch (ProtocolViolationException ignored) {
            // Non-tunnel binary is handled by the caller's ordinary master path.
        }
    }

    public int activeTunnels() {
        return tunnels.size();
    }

    @Override
    public void unregisterHost(String roomId) {
        hostsByRoom.remove(roomId);
        tunnels.entrySet().removeIf(entry -> {
            if (entry.getValue().roomId().equals(roomId)) {
                entry.getValue().guest().close("host disconnected");
                return true;
            }
            return false;
        });
    }

    private int allocateGuestId() {
        for (int i = 0; i <= 0xFFFF; i++) {
            int candidate = ++nextGuestId & 0xFFFF;
            if (!tunnels.containsKey(candidate)) {
                return candidate;
            }
        }
        return -1;
    }

    private static boolean ownedBy(Tunnel tunnel, HubConnection source) {
        return tunnel != null && (source == null || tunnel.host() == source);
    }
}
