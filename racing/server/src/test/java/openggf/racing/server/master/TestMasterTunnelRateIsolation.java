package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import static openggf.racing.server.master.TestRoomBrokerAdversarial.FP;
import static org.junit.jupiter.api.Assertions.*;

/** Host tunnel fan-out needs independent quotas without exempting unowned traffic. */
@Timeout(30)
class TestMasterTunnelRateIsolation {
    private static final int FLOOD_LIMIT = 1000;
    private record Guest(int id, TestRoomBrokerAdversarial.Connection connection) { }

    private static final class Peer implements AutoCloseable {
        final TestMasterTransportLifecycle.HeldExecutor loop =
                new TestMasterTransportLifecycle.HeldExecutor();
        final TestMasterTransportLifecycle.HeldOutbound output =
                new TestMasterTransportLifecycle.HeldOutbound();
        final TestMasterTransportLifecycle.LoopbackChannel channel;
        String token;

        Peer(TestRoomBrokerAdversarial.Fixture fixture) {
            channel = new TestMasterTransportLifecycle.LoopbackChannel(output,
                    new MasterChannelHandler(fixture.broker, fixture.relays, fixture.tunnels,
                            loop, new ConnectionHygiene.ConnectionCounter(4)));
            channel.pipeline().fireUserEventTriggered(
                    new WebSocketServerProtocolHandler.HandshakeComplete(
                            "/master", new DefaultHttpHeaders(), null));
            settle();
        }

        void admit(TestRoomBrokerAdversarial.Fixture fixture, PlayerIdentity identity)
                throws GeneralSecurityException {
            fixture.establish(identity);
            ClientHandshake handshake = fixture.handshake(identity);
            text(null, handshake.hello());
            var welcome = assertInstanceOf(ControlMessage.Welcome.class, output.read());
            text(null, handshake.onWelcome(welcome));
            token = assertInstanceOf(ControlMessage.JoinAccepted.class,
                    output.read()).sessionToken();
        }

        void settle() {
            // A coalesced burst hands one frame back and forth between both loops.
            for (int pass = 0; pass < 2048; pass++) {
                loop.drain();
                channel.runPendingTasks();
                if (loop.tasks.isEmpty()) return;
            }
            fail("master loops did not quiesce");
        }

        void text(String sessionToken, ControlMessage message) {
            if (channel.isActive()) {
                channel.writeInbound(new TextWebSocketFrame(ControlCodec.encode(sessionToken, message)));
                settle();
            }
        }

        void binary(byte[] packet) {
            if (channel.isActive()) {
                channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(packet)));
                settle();
            }
        }

        @Override public void close() {
            channel.finishAndReleaseAll();
            settle();
            output.releaseAll();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TestRoomBrokerAdversarial.Fixture broker;
        final Peer owner;
        final String roomId;
        final List<Guest> guests = new ArrayList<>();

        Fixture(Path dir, int guestCount) throws Exception {
            broker = new TestRoomBrokerAdversarial.Fixture(dir);
            owner = new Peer(broker);
            owner.admit(broker, PlayerIdentity.loadOrCreate(dir.resolve("owner")));
            owner.text(owner.token, new ControlMessage.RoomCreate(
                    new ControlMessage.RoomDescriptor("Tunnel quota", "s3k", 0, 0,
                            "OPEN", null, 8, false), "DIRECT", 27888, FP,
                    List.of(), "ab".repeat(32)));
            roomId = assertInstanceOf(ControlMessage.RoomCreated.class,
                    owner.output.read()).roomId();
            for (int i = 0; i < guestCount; i++) {
                PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("guest-" + i));
                broker.establish(identity);
                var guest = new TestRoomBrokerAdversarial.Connection();
                String token = broker.admit(guest, identity);
                broker.send(guest, token, new ControlMessage.RoomJoinRequest(roomId));
                assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
                broker.send(guest, token, new ControlMessage.RelayAttach(roomId));
                var attached = broker.broker.takeAttachResult(guest).orElseThrow();
                assertEquals(RoomBroker.AttachMode.TUNNEL_GUEST, attached.mode());
                var open = assertInstanceOf(ControlMessage.RelayGuestOpen.class, owner.output.read());
                assertEquals(attached.guestId(), open.guestId());
                guest.received.clear();
                guests.add(new Guest(open.guestId(), guest));
            }
        }

        void assertOwnerRetained(int tunnelCount) {
            assertTrue(owner.channel.isActive(), "tunnel fan-out closed the shared host link");
            assertTrue(broker.registry.find(roomId).isPresent(), "host room was removed");
            assertEquals(tunnelCount, broker.tunnels.activeTunnels());
        }

        @Override public void close() {
            owner.close();
            broker.close();
        }
    }

    private static byte[] payload(int sequence) {
        return GhostPackets.encodeAggregate(sequence, List.of());
    }

    @Test
    void fourOwnedGuestBurstsDoNotShareTheBrokerQuota(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, 4)) {
            // 124 wrappers exceed the channel burst; every recipient uses only 31.
            for (int sequence = 0; sequence < 31; sequence++) {
                for (Guest guest : f.guests) {
                    f.owner.binary(GhostPackets.encodeRelayGuestBinary(guest.id(), payload(sequence)));
                }
            }
            f.assertOwnerRetained(4);
            for (Guest guest : f.guests) {
                assertNull(guest.connection().closed);
                assertEquals(31, guest.connection().binary.size());
                for (int sequence = 0; sequence < 31; sequence++) {
                    assertArrayEquals(payload(sequence), guest.connection().binary.get(sequence));
                }
            }
        }
    }

    @Test
    void coalescedOwnedGuestBurstsSurviveBeforeBrokerClassification(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir, 4)) {
            for (int sequence = 0; sequence < 31; sequence++) {
                for (Guest guest : f.guests) {
                    if (f.owner.channel.isActive()) {
                        byte[] wrapped = GhostPackets.encodeRelayGuestBinary(guest.id(), payload(sequence));
                        f.owner.channel.writeInbound(
                                new BinaryWebSocketFrame(Unpooled.wrappedBuffer(wrapped)));
                    }
                }
            }
            assertTrue(f.owner.channel.isActive(), "valid coalesced fan-out exceeded the pending bound");
            assertEquals(1, f.owner.loop.tasks.size(), "coalesced frames bypassed serialized classification");
            f.owner.settle();
            f.assertOwnerRetained(4);
            for (Guest guest : f.guests) {
                assertNull(guest.connection().closed);
                assertEquals(31, guest.connection().binary.size());
                for (int sequence = 0; sequence < 31; sequence++) {
                    assertArrayEquals(payload(sequence), guest.connection().binary.get(sequence));
                }
            }
        }
    }

    @Test
    void coalescedMaximumBinaryBurstAcrossEightGuestsFitsPendingBudgets(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir, Protocol.MAX_PLAYERS_DIRECT)) {
            byte[] payload = new byte[Protocol.MAX_BINARY_BYTES - 3];
            for (int sequence = 0; sequence < ConnectionHygiene.MESSAGE_RATE_BURST; sequence++) {
                payload[0] = (byte) sequence;
                for (Guest guest : f.guests) {
                    byte[] wrapped = GhostPackets.encodeRelayGuestBinary(guest.id(), payload);
                    f.owner.channel.writeInbound(
                            new BinaryWebSocketFrame(Unpooled.wrappedBuffer(wrapped)));
                }
            }
            assertTrue(f.owner.channel.isActive(), "maximum valid binary fan-out exceeded pending budgets");
            assertEquals(1, f.owner.loop.tasks.size(), "coalesced frames bypassed serialized classification");
            f.owner.settle();
            f.assertOwnerRetained(Protocol.MAX_PLAYERS_DIRECT);
            for (Guest guest : f.guests) {
                assertNull(guest.connection().closed);
                assertEquals(ConnectionHygiene.MESSAGE_RATE_BURST,
                        guest.connection().binary.size());
                for (int sequence = 0; sequence < ConnectionHygiene.MESSAGE_RATE_BURST; sequence++) {
                    assertEquals((byte) sequence, guest.connection().binary.get(sequence)[0]);
                }
            }
        }
    }

    @Test
    void brokerPendingBytesBoundLargeUnclassifiedText(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, 2)) {
            Guest guest = f.guests.getFirst();
            String large = ControlCodec.encode(f.owner.token,
                    new ControlMessage.RelayGuestText(guest.id(),
                            "x".repeat(Protocol.MAX_CONTROL_BYTES)));
            int submitted = 0;
            while (submitted < ConnectionHygiene.MESSAGE_RATE_BURST
                    && f.owner.channel.isActive()) {
                f.owner.channel.writeInbound(new TextWebSocketFrame(large));
                submitted++;
            }
            assertFalse(f.owner.channel.isActive(),
                    "count-bounded queue retained an excessive unclassified text backlog");
            assertTrue(submitted < ConnectionHygiene.MESSAGE_RATE_BURST,
                    "frame count, rather than retained bytes, closed the broker link");
            f.owner.settle();
            assertTrue(f.broker.registry.find(f.roomId).isEmpty());
            assertEquals(0, f.broker.tunnels.activeTunnels());
        }
    }

    @Test
    void brokerBinaryDispatchKeepsOneInFlightTaskAndBoundsPendingFrames(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir, 2)) {
            Guest guest = f.guests.getFirst();
            byte[] wrapped = GhostPackets.encodeRelayGuestBinary(guest.id(), payload(0));
            f.owner.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(wrapped)));
            assertEquals(1, f.owner.loop.tasks.size(), "first binary must await broker ownership check");
            int pendingLimit = ConnectionHygiene.MESSAGE_RATE_BURST * Protocol.MAX_PLAYERS_DIRECT;
            for (int sequence = 0; sequence < pendingLimit; sequence++) {
                if (!f.owner.channel.isActive()) break;
                f.owner.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(wrapped)));
                assertTrue(f.owner.channel.isActive(), "supported per-tunnel bursts exceeded the pending bound");
                assertEquals(1, f.owner.loop.tasks.size(), "binary frames bypassed serialized dispatch");
            }
            assertTrue(f.owner.channel.isActive());
            f.owner.channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(wrapped)));
            assertFalse(f.owner.channel.isActive(), "unvalidated pending frames had no bound");
            assertEquals(2, f.owner.loop.tasks.size(), "only in-flight dispatch and disconnect may be queued");
            f.owner.settle();
            assertTrue(f.broker.registry.find(f.roomId).isEmpty());
            assertEquals(0, f.broker.tunnels.activeTunnels());
            for (Guest owned : f.guests) assertNotNull(owned.connection().closed);
        }
    }

    @Test
    void mixedTextAndBinaryExcessClosesOnlyItsOwnedTunnel(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, 2)) {
            Guest offender = f.guests.getFirst();
            Guest healthy = f.guests.getLast();
            for (int sequence = 0; sequence < FLOOD_LIMIT && offender.connection().closed == null;
                 sequence++) {
                if ((sequence & 1) == 0) {
                    f.owner.binary(GhostPackets.encodeRelayGuestBinary(offender.id(), payload(sequence)));
                } else {
                    f.owner.text(f.owner.token, new ControlMessage.RelayGuestText(offender.id(),
                            ControlCodec.encode(null, new ControlMessage.Ping(sequence))));
                }
            }
            f.assertOwnerRetained(1);
            assertNotNull(offender.connection().closed);
            int accepted = offender.connection().binary.size() + offender.connection().received.size();
            assertTrue(accepted >= ConnectionHygiene.MESSAGE_RATE_BURST);
            assertTrue(accepted < FLOOD_LIMIT, "tunnel flood did not reach its independent quota");
            assertEquals((accepted + 1) / 2, offender.connection().binary.size());
            assertEquals(accepted / 2, offender.connection().received.size());
            for (int sequence = 0; sequence < accepted; sequence++) {
                if ((sequence & 1) == 0) {
                    assertArrayEquals(payload(sequence), offender.connection().binary.get(sequence / 2));
                } else {
                    assertEquals(new ControlMessage.Ping(sequence),
                            offender.connection().received.get(sequence / 2));
                }
            }
            f.owner.binary(GhostPackets.encodeRelayGuestBinary(healthy.id(), payload(999)));
            f.owner.text(f.owner.token, new ControlMessage.RelayGuestText(healthy.id(),
                    ControlCodec.encode(null, new ControlMessage.Ping(999))));
            assertNull(healthy.connection().closed);
            assertEquals(1, healthy.connection().binary.size());
            assertArrayEquals(payload(999), healthy.connection().binary.getFirst());
            assertEquals(List.of(new ControlMessage.Ping(999)), healthy.connection().received);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAUTHENTICATED", "FOREIGN_BINARY", "FOREIGN_TEXT", "UNKNOWN_ID",
            "MALFORMED_BINARY", "WRONG_TOKEN"})
    void invalidWrappersRetainOffenderQuotaAndCannotSpendVictimQuota(String kind,
                                                                   @TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, 2); Peer attacker = new Peer(f.broker)) {
            if (!kind.equals("UNAUTHENTICATED")) {
                attacker.admit(f.broker, PlayerIdentity.loadOrCreate(dir.resolve("attacker")));
            }
            Guest victim = f.guests.getFirst();
            Guest healthy = f.guests.getLast();
            for (int sequence = 0; sequence < FLOOD_LIMIT && attacker.channel.isActive(); sequence++) {
                switch (kind) {
                    case "UNAUTHENTICATED", "FOREIGN_BINARY" -> attacker.binary(
                            GhostPackets.encodeRelayGuestBinary(victim.id(), payload(sequence)));
                    case "FOREIGN_TEXT" -> attacker.text(attacker.token,
                            new ControlMessage.RelayGuestText(victim.id(),
                                    ControlCodec.encode(null, new ControlMessage.Ping(sequence))));
                    case "UNKNOWN_ID" -> attacker.binary(
                            GhostPackets.encodeRelayGuestBinary(65_535, payload(sequence)));
                    case "MALFORMED_BINARY" -> attacker.binary(
                            new byte[] {(byte) GhostPackets.TYPE_RELAY_GUEST_BINARY});
                    case "WRONG_TOKEN" -> attacker.text("wrong-token",
                            new ControlMessage.RelayGuestText(victim.id(),
                                    ControlCodec.encode(null, new ControlMessage.Ping(sequence))));
                    default -> throw new AssertionError(kind);
                }
            }
            assertFalse(attacker.channel.isActive(), "invalid wrappers obtained exempt quota: " + kind);
            assertTrue(victim.connection().binary.isEmpty(), "invalid binary reached victim");
            assertTrue(victim.connection().received.isEmpty(), "invalid text reached victim");
            f.assertOwnerRetained(2);

            // An attacker must not charge the victim's bucket by guessing its ID.
            for (int sequence = 0; sequence < 120; sequence++) {
                f.owner.binary(GhostPackets.encodeRelayGuestBinary(victim.id(), payload(sequence)));
            }
            f.assertOwnerRetained(2);
            assertNull(victim.connection().closed);
            assertEquals(120, victim.connection().binary.size());
            for (int sequence = 0; sequence < 120; sequence++) {
                assertArrayEquals(payload(sequence), victim.connection().binary.get(sequence));
            }
            f.owner.binary(GhostPackets.encodeRelayGuestBinary(healthy.id(), payload(999)));
            assertNull(healthy.connection().closed);
            assertEquals(1, healthy.connection().binary.size());
            assertArrayEquals(payload(999), healthy.connection().binary.getFirst());
        }
    }
}
