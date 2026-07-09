package com.openggf.net;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.host.HostMasterLink;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.master.GuestTunnelRouter;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRelayFallbackTunnel {
    static final class QueueSink implements HostMasterLink.MessageSink {
        final ArrayDeque<ControlMessage> control = new ArrayDeque<>();
        final ArrayDeque<byte[]> binary = new ArrayDeque<>();
        @Override public void sendControl(ControlMessage message) { control.add(message); }
        @Override public void sendBinary(byte[] data) { binary.add(data); }
    }

    static final class FakeGuestConnection implements HubConnection {
        final List<String> text = new ArrayList<>();
        final List<byte[]> binary = new ArrayList<>();
        String closedReason;
        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] data) { binary.add(data); }
        @Override public void close(String reason) { closedReason = reason; }
        @Override public String remoteHost() { return "guest"; }
    }

    @Test
    void guestHandshakesAndStreamsThroughTheTunnel(@TempDir Path dir) throws Exception {
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        RoomHost room = new RoomHost(new RoomHostConfig("LAN", "s3k", 0, 0,
                "OPEN", null, 8, "0.6:cafe"), hostIdentity,
                System::currentTimeMillis, TrackValidationProfileSource.none());
        QueueSink hostOut = new QueueSink();
        HostMasterLink link = new HostMasterLink(room::onConnected, room::onText,
                room::onBinary, room::onDisconnected, hostOut);

        FakeGuestConnection guest = new FakeGuestConnection();
        QueueSink masterToHost = new QueueSink();
        GuestTunnelRouter router = new GuestTunnelRouter();
        router.registerHost("room-1", new HubConnection() {
            @Override public void sendText(String value) {
                masterToHost.control.add(ControlCodec.decode(value).message());
            }
            @Override public void sendBinary(byte[] data) { masterToHost.binary.add(data); }
            @Override public void close(String reason) { }
            @Override public String remoteHost() { return "host"; }
        });
        int guestId = router.openGuest("room-1", guest).orElseThrow();

        Runnable pump = () -> {
            while (!masterToHost.control.isEmpty()) {
                link.onMasterControl(masterToHost.control.poll());
            }
            while (!masterToHost.binary.isEmpty()) {
                link.onMasterBinary(masterToHost.binary.poll());
            }
            while (!hostOut.control.isEmpty()) {
                router.onHostControl(hostOut.control.poll());
            }
            while (!hostOut.binary.isEmpty()) {
                router.onHostBinary(hostOut.binary.poll());
            }
        };
        pump.run();

        PlayerIdentity guestIdentity = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        ClientHandshake handshake = new ClientHandshake(guestIdentity, "GUEST", "0.6:cafe");
        router.guestText(guestId, ControlCodec.encode(null, handshake.hello()));
        pump.run();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) ControlCodec.decode(
                guest.text.getLast()).message();
        router.guestText(guestId, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        pump.run();
        assertTrue(guest.text.stream().map(value -> ControlCodec.decode(value).message())
                .anyMatch(ControlMessage.JoinAccepted.class::isInstance));

        byte[] frame = new byte[com.openggf.game.ghost.GhostFrameCodec.BYTES];
        router.guestBinary(guestId, GhostPackets.encodeFrames(1, 0, frame));
        pump.run();
        room.tick();
        pump.run();
        assertNull(guest.closedReason);

        router.guestDisconnected(guestId);
        pump.run();
        assertEquals(0, router.activeTunnels());
    }
}
