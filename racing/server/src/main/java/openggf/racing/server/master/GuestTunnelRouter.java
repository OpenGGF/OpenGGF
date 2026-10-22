package openggf.racing.server.master;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.GhostHub;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.nio.charset.StandardCharsets;

/** Master-side pairing and routing for direct-room fallback guests. */
public final class GuestTunnelRouter implements RoomBroker.DirectTunnelDirectory {
    private record Tunnel(String roomId, HubConnection guest, HubConnection host,
                          ConnectionHygiene.RateBucket hostRate) { }

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
        tunnels.put(guestId, new Tunnel(roomId, guestConnection, host,
                new ConnectionHygiene.RateBucket()));
        host.sendText(ControlCodec.encode(null,
                new ControlMessage.RelayGuestOpen(guestId)));
        return OptionalInt.of(guestId);
    }

    public void guestText(int guestId, String text) {
        Tunnel tunnel = tunnels.get(guestId);
        if (tunnel != null) {
            if (text == null || text.getBytes(StandardCharsets.UTF_8).length
                    > Protocol.MAX_CONTROL_BYTES) {
                rejectGuest(guestId, tunnel, "relay guest text exceeds inner frame cap");
                return;
            }
            String wrapped = ControlCodec.encode(null,
                    new ControlMessage.RelayGuestText(guestId, text));
            // Untrusted raw text can expand sixfold during JSON escaping. Never
            // send a wrapper which would tear down the host's shared master link.
            if (wrapped.getBytes(StandardCharsets.UTF_8).length
                    > Protocol.MAX_MASTER_FRAME_BYTES) {
                rejectGuest(guestId, tunnel, "relay guest text exceeds wrapper frame cap");
                return;
            }
            if (canForwardTo(guestId, tunnel, tunnel.host())) {
                tunnel.host().sendText(wrapped);
            }
        }
    }

    public void guestBinary(int guestId, byte[] packet) {
        Tunnel tunnel = tunnels.get(guestId);
        if (tunnel != null) {
            final byte[] wrapped;
            try {
                // The shared encoder owns the binary cap, including its type/id header.
                wrapped = GhostPackets.encodeRelayGuestBinary(guestId, packet);
            } catch (ProtocolViolationException invalid) {
                rejectGuest(guestId, tunnel, "relay guest binary exceeds payload bounds");
                return;
            }
            if (canForwardTo(guestId, tunnel, tunnel.host())) {
                tunnel.host().sendBinary(wrapped);
            }
        }
    }

    private boolean canForwardTo(int guestId, Tunnel tunnel, HubConnection receiver) {
        // Match the hub's strict cutoff. This samples transport occupancy, not
        // cross-event-loop work or an atomic reservation for the next payload.
        if (receiver.queuedBytes() > GhostHub.BP_DISCONNECT_BYTES) {
            // Even when the host is slow, only this guest leaves: the host link
            // and its other rooms/tunnels retain their registration and ownership.
            rejectGuest(guestId, tunnel, "relay receiver too slow");
            return false;
        }
        return true;
    }

    private void rejectGuest(int guestId, Tunnel tunnel, String reason) {
        guestDisconnected(guestId);
        tunnel.guest().close(reason);
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

    /** Broker-loop-only check before exempting a wrapper from its socket's broker quota. */
    boolean ownsTunnel(HubConnection source, int guestId) {
        return source != null && ownedBy(tunnels.get(guestId), source);
    }

    private boolean consumeHostQuota(int guestId, Tunnel tunnel) {
        if (!tunnel.hostRate().consume()) {
            rejectGuest(guestId, tunnel, "relay host tunnel message rate exceeded");
            return false;
        }
        return true;
    }

    public void onHostControl(HubConnection source, ControlMessage message) {
        switch (message) {
            case ControlMessage.RelayGuestText text -> {
                Tunnel tunnel = tunnels.get(text.guestId());
                if (ownedBy(tunnel, source)
                        && consumeHostQuota(text.guestId(), tunnel)
                        && canForwardTo(text.guestId(), tunnel, tunnel.guest())) {
                    tunnel.guest().sendText(text.text());
                }
            }
            case ControlMessage.RelayGuestClose close -> {
                Tunnel tunnel = tunnels.get(close.guestId());
                if (ownedBy(tunnel, source) && consumeHostQuota(close.guestId(), tunnel)) {
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
            if (ownedBy(tunnel, source)
                    && consumeHostQuota(payload.guestId(), tunnel)
                    && canForwardTo(payload.guestId(), tunnel, tunnel.guest())) {
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
