package openggf.racing.server.master;

import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A fallback guest must never poison the master link shared by its host's rooms. */
@Timeout(10)
class TestGuestTunnelBounds {
    private static final class Peer implements HubConnection {
        final List<ControlMessage> text = new ArrayList<>();
        final List<byte[]> binary = new ArrayList<>();
        int queued;
        String closed;
        @Override public void sendText(String value) {
            assertTrue(text.size() < 32, "response budget exceeded");
            assertTrue(utf8(value) <= Protocol.MAX_MASTER_FRAME_BYTES,
                    "oversized wrapper would close the host master link");
            text.add(ControlCodec.decode(value, Protocol.MAX_MASTER_FRAME_BYTES).message());
        }
        @Override public void sendBinary(byte[] value) {
            assertTrue(binary.size() < 16, "binary response budget exceeded");
            binary.add(value.clone());
        }
        @Override public void close(String reason) { closed = reason; }
        @Override public String remoteHost() { return "127.0.0.1"; }
        @Override public int queuedBytes() { return queued; }
    }

    private static final class Fixture {
        final GuestTunnelRouter router = new GuestTunnelRouter();
        final Peer host = new Peer();
        final Peer offender = new Peer();
        final Peer healthy = new Peer();
        final int offenderId;
        final int healthyId;
        Fixture() {
            router.registerHost("offender-room", host);
            router.registerHost("unrelated-room", host);
            offenderId = router.openGuest("offender-room", offender).orElseThrow();
            healthyId = router.openGuest("unrelated-room", healthy).orElseThrow();
            host.text.clear();
        }
        void assertOnlyOffenderRemoved() {
            assertNotNull(offender.closed);
            assertNull(host.closed, "offender closed the shared host link");
            assertNull(healthy.closed, "offender closed an unrelated room's guest");
            assertEquals(1, router.activeTunnels());
            ControlMessage.RelayGuestClose close = assertInstanceOf(
                    ControlMessage.RelayGuestClose.class, host.text.getFirst());
            assertEquals(offenderId, close.guestId());
            assertEquals(1, host.text.size(), "offending payload reached host");
            host.queued = 0; // The shared transport drains; its registrations must survive.
            String witness = ControlCodec.encode(null, new ControlMessage.Ping(123));
            router.guestText(healthyId, witness);
            ControlMessage.RelayGuestText forwarded = assertInstanceOf(
                    ControlMessage.RelayGuestText.class, host.text.getLast());
            assertEquals(healthyId, forwarded.guestId());
            assertEquals(witness, forwarded.text());
            byte[] binaryWitness = {1, 2, 3};
            router.guestBinary(healthyId, binaryWitness);
            GhostPackets.RelayGuestBinary forwardedBinary =
                    GhostPackets.decodeRelayGuestBinary(host.binary.getLast());
            assertEquals(healthyId, forwardedBinary.guestId());
            assertArrayEquals(binaryWitness, forwardedBinary.payload());
            router.onHostControl(host, new ControlMessage.RelayGuestText(healthyId, witness));
            assertEquals(new ControlMessage.Ping(123), healthy.text.getLast());
            router.onHostBinary(host, GhostPackets.encodeRelayGuestBinary(healthyId, binaryWitness));
            assertArrayEquals(binaryWitness, healthy.binary.getLast());
            // Both registrations survive: the offender room can accept a new guest.
            Peer replacement = new Peer();
            int replacementId = router.openGuest("offender-room", replacement).orElseThrow();
            assertEquals(2, router.activeTunnels());
            router.guestDisconnected(replacementId);
            router.guestDisconnected(healthyId);
            assertEquals(0, router.activeTunnels());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST_TEXT", "GUEST_BINARY", "HOST_TEXT", "HOST_BINARY"})
    void overBudgetReceiverRemovesOnlyTheAffectedTunnel(String direction) {
        for (int queued : new int[] {1_048_577, Integer.MAX_VALUE}) {
            Fixture f = new Fixture();
            Peer receiver = direction.startsWith("GUEST") ? f.host : f.offender;
            receiver.queued = queued;

            forwardPayload(f, direction);

            assertNotNull(f.offender.closed, direction + " queued=" + queued);
            assertTrue(f.host.binary.isEmpty(), "payload reached slow host");
            assertTrue(f.offender.text.isEmpty(), "payload reached slow guest");
            assertTrue(f.offender.binary.isEmpty(), "payload reached slow guest");
            // In-flight input and a later disconnect must not revive a removed tunnel
            // or emit repeated close notifications.
            forwardPayload(f, "GUEST_TEXT");
            forwardPayload(f, "GUEST_BINARY");
            forwardPayload(f, "HOST_TEXT");
            forwardPayload(f, "HOST_BINARY");
            f.router.guestDisconnected(f.offenderId);
            assertTrue(f.host.binary.isEmpty());
            assertTrue(f.offender.text.isEmpty());
            assertTrue(f.offender.binary.isEmpty());
            f.assertOnlyOffenderRemoved();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST_TEXT", "GUEST_BINARY", "HOST_TEXT", "HOST_BINARY"})
    void receiverAtOrBelowOneMebibyteStillForwards(String direction) {
        for (int queued : new int[] {1_048_575, 1_048_576}) {
            Fixture f = new Fixture();
            Peer receiver = direction.startsWith("GUEST") ? f.host : f.offender;
            receiver.queued = queued;

            forwardPayload(f, direction);

            switch (direction) {
                case "GUEST_TEXT" -> assertEquals(new ControlMessage.RelayGuestText(
                        f.offenderId, ControlCodec.encode(null, new ControlMessage.Ping(456))),
                        f.host.text.getLast());
                case "GUEST_BINARY" -> {
                    GhostPackets.RelayGuestBinary forwarded =
                            GhostPackets.decodeRelayGuestBinary(f.host.binary.getLast());
                    assertEquals(f.offenderId, forwarded.guestId());
                    assertArrayEquals(new byte[] {4, 5, 6}, forwarded.payload());
                }
                case "HOST_TEXT" -> assertEquals(new ControlMessage.Ping(456),
                        f.offender.text.getLast());
                case "HOST_BINARY" -> assertArrayEquals(new byte[] {4, 5, 6},
                        f.offender.binary.getLast());
                default -> throw new AssertionError(direction);
            }
            assertNull(f.host.closed);
            assertNull(f.offender.closed);
            assertNull(f.healthy.closed);
            assertEquals(2, f.router.activeTunnels());
        }
    }

    private static void forwardPayload(Fixture f, String direction) {
        String text = ControlCodec.encode(null, new ControlMessage.Ping(456));
        byte[] binary = {4, 5, 6};
        switch (direction) {
            case "GUEST_TEXT" -> f.router.guestText(f.offenderId, text);
            case "GUEST_BINARY" -> f.router.guestBinary(f.offenderId, binary);
            case "HOST_TEXT" -> f.router.onHostControl(f.host,
                    new ControlMessage.RelayGuestText(f.offenderId, text));
            case "HOST_BINARY" -> f.router.onHostBinary(f.host,
                    GhostPackets.encodeRelayGuestBinary(f.offenderId, binary));
            default -> throw new AssertionError(direction);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASCII", "MULTIBYTE", "ESCAPED_WRAPPER"})
    void oversizedGuestTextRemovesOnlyItsTunnel(String kind) {
        Fixture f = new Fixture();
        String payload = switch (kind) {
            case "ASCII" -> "a".repeat(Protocol.MAX_CONTROL_BYTES + 1);
            case "MULTIBYTE" -> "é".repeat(Protocol.MAX_CONTROL_BYTES / 2 + 1);
            case "ESCAPED_WRAPPER" -> "\u0001".repeat(Protocol.MAX_CONTROL_BYTES / 2);
            default -> throw new AssertionError(kind);
        };
        assertDoesNotThrow(() -> f.router.guestText(f.offenderId, payload), kind);
        assertTrue(f.host.binary.isEmpty());
        f.assertOnlyOffenderRemoved();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASCII", "MULTIBYTE", "QUOTES_AND_BACKSLASHES"})
    void maximumValidControlSurvivesWrapping(String kind) {
        Fixture f = new Fixture();
        int available = Protocol.MAX_CONTROL_BYTES
                - utf8(ControlCodec.encode(null, new ControlMessage.Chat("")));
        String content = switch (kind) {
            case "ASCII" -> "a".repeat(available);
            case "MULTIBYTE" -> "é".repeat(available / 2) + "a".repeat(available % 2);
            case "QUOTES_AND_BACKSLASHES" -> "\\\"".repeat(available / 4)
                    + "a".repeat(available % 4);
            default -> throw new AssertionError(kind);
        };
        String payload = ControlCodec.encode(null, new ControlMessage.Chat(content));
        assertEquals(Protocol.MAX_CONTROL_BYTES, utf8(payload));
        assertDoesNotThrow(() -> f.router.guestText(f.offenderId, payload), kind);
        ControlMessage.RelayGuestText forwarded = assertInstanceOf(
                ControlMessage.RelayGuestText.class, f.host.text.getLast());
        assertEquals(payload, forwarded.text());
        assertNull(f.offender.closed);
        assertNull(f.host.closed);
        assertEquals(2, f.router.activeTunnels());
    }

    @Test
    void maximumBinaryPayloadIncludesTheWrapperHeaderInItsBudget() {
        Fixture f = new Fixture();
        int headerBytes = Byte.BYTES + Short.BYTES; // type byte + unsigned guest id
        byte[] payload = new byte[Protocol.MAX_BINARY_BYTES - headerBytes];
        assertDoesNotThrow(() -> f.router.guestBinary(f.offenderId, payload));
        byte[] wrapped = f.host.binary.getFirst();
        assertEquals(Protocol.MAX_BINARY_BYTES, wrapped.length);
        assertArrayEquals(payload, GhostPackets.decodeRelayGuestBinary(wrapped).payload());
        assertNull(f.offender.closed);
        assertNull(f.host.closed);
        assertEquals(2, f.router.activeTunnels());
    }

    @Test
    void malformedBinaryRemovesOnlyItsGuest() {
        int maxInnerBytes = Protocol.MAX_BINARY_BYTES - Byte.BYTES - Short.BYTES;
        for (int size : new int[] {0, maxInnerBytes + 1,
                Protocol.MAX_BINARY_BYTES, Protocol.MAX_MASTER_FRAME_BYTES}) {
            Fixture f = new Fixture();
            assertDoesNotThrow(() -> f.router.guestBinary(f.offenderId, new byte[size]),
                    "payload bytes=" + size);
            assertTrue(f.host.binary.isEmpty());
            f.assertOnlyOffenderRemoved();
        }
    }

    private static int utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
