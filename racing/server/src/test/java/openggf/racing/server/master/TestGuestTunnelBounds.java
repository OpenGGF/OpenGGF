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
        String closed;
        @Override public void sendText(String value) {
            assertTrue(text.size() < 32, "response budget exceeded");
            assertTrue(utf8(value) <= Protocol.MAX_MASTER_FRAME_BYTES,
                    "oversized wrapper would close the host master link");
            text.add(ControlCodec.decode(value, Protocol.MAX_MASTER_FRAME_BYTES).message());
        }
        @Override public void sendBinary(byte[] value) {
            assertTrue(binary.size() < 16, "binary response budget exceeded");
            // Exercise the same wrapper parser as the receiving host link.
            GhostPackets.decodeRelayGuestBinary(value);
            binary.add(value.clone());
        }
        @Override public void close(String reason) { closed = reason; }
        @Override public String remoteHost() { return "127.0.0.1"; }
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
            String witness = ControlCodec.encode(null, new ControlMessage.Ping(123));
            router.guestText(healthyId, witness);
            ControlMessage.RelayGuestText forwarded = assertInstanceOf(
                    ControlMessage.RelayGuestText.class, host.text.getLast());
            assertEquals(healthyId, forwarded.guestId());
            assertEquals(witness, forwarded.text());
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
        f.assertOnlyOffenderRemoved();
        assertTrue(f.host.binary.isEmpty());
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
            f.assertOnlyOffenderRemoved();
            assertTrue(f.host.binary.isEmpty());
        }
    }

    private static int utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
