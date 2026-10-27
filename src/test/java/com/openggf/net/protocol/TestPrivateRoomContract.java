package com.openggf.net.protocol;

import com.openggf.net.hub.RoomHostConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPrivateRoomContract {
    private final ControlJsonCodec codec = new ControlJsonCodec();

    @Test
    void sourceOverloadsAreExplicitlyPublicButWireRequiresPolicy() {
        var room = new ControlMessage.RoomDescriptor("Room", "s3k", 0, 0, "OPEN", null, 8, false);
        assertEquals(RoomAccessPolicy.PUBLIC, room.accessPolicy());
        String wire = codec.encode(null, new ControlMessage.RoomCreate(room, "RELAY", 0, "build"));
        assertEquals(room, ((ControlMessage.RoomCreate) codec.decode(wire).message()).room());
        assertThrows(ProtocolViolationException.class, () -> codec.decode(wire.replace(
                ",\"accessPolicy\":{\"mode\":\"PUBLIC\",\"membersCanInvite\":true}", "")));
        assertThrows(ProtocolViolationException.class, () -> codec.decode(wire.replace(
                "\"mode\":\"PUBLIC\"", "\"mode\":null")));
    }

    @Test
    void correlationIsOptionalNonnegativeAndLossless() {
        var ping = new ControlMessage.Ping(5);
        assertEquals(19, codec.decode(codec.encode("t", ping, 19)).requestId());
        assertEquals(0, codec.decode(codec.encode("t", ping)).requestId());
        assertThrows(IllegalArgumentException.class, () -> codec.encode("t", ping, -1));
        String wire = codec.encode(null, ping, 1);
        for (String invalid : new String[]{"-1", "1.5", "null", "\"1\"", "9223372036854775808"}) {
            assertThrows(ProtocolViolationException.class,
                    () -> codec.decode(wire.replace("\"requestId\":1", "\"requestId\":" + invalid)));
        }
    }

    @Test
    void credentialsRoundTripOnlyInExplicitCredentialMessagesAndRedactDiagnostics() {
        String secret = "a sufficiently long password";
        try (var password = RoomPassword.of(secret)) {
            var message = new ControlMessage.RoomAccessSubmit("challenge", password);
            String wire = codec.encode(null, message);
            var decoded = (ControlMessage.RoomAccessSubmit) codec.decode(wire).message();
            try (RoomPassword received = decoded.password()) {
                assertArrayEquals(secret.toCharArray(), received.copyCharacters());
            }
            assertFalse(password.toString().contains(secret));
            assertFalse(message.toString().contains(secret));
            assertFalse(new ControlMessage.RoomPasswordChange(password).toString().contains(secret));
            assertFalse(new ControlMessage.RelayGuestText(1, wire).toString().contains(secret));
            var config = new RoomHostConfig("Room", "s3k", 0, 0, "OPEN", null, 8,
                    "build", java.util.List.of(), false,
                    new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true), password, "instance");
            assertFalse(config.toString().contains(secret));
        }
        ProtocolViolationException error = assertThrows(ProtocolViolationException.class,
                () -> codec.decode(codec.encode(null, new ControlMessage.Ping(1))
                        .replace("\"t0ClientMillis\":1", "\"t0ClientMillis\":\"" + secret + "\"")));
        assertNull(error.getCause());
        assertFalse(error.toString().contains(secret));
    }

    @Test
    void privateRelayCreationPreservesCredentialWhileDirectRegistrationIsNonSecret() {
        var policy = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true);
        var room = new ControlMessage.RoomDescriptor("Room", "s3k", 0, 0, "OPEN", null, 8, false, policy);
        try (RoomPassword password = RoomPassword.of("a sufficiently long password")) {
            var creation = new ControlMessage.RoomCreate(room, "RELAY", 0, "build", java.util.List.of(),
                    null, null, 0, password);
            var decoded = (ControlMessage.RoomCreate) codec.decode(codec.encode(null, creation, 1)).message();
            try (RoomPassword received = decoded.password()) {
                assertArrayEquals(password.copyCharacters(), received.copyCharacters());
                assertTrue(decoded.room().accessPolicy().passwordRequired());
            }
            assertThrows(IllegalArgumentException.class, () -> new ControlMessage.RoomCreate(room,
                    "DIRECT", 1, "build", java.util.List.of(), "ab".repeat(32), "instance", 0, password));
            assertNull(new ControlMessage.RoomCreate(room, "DIRECT", 1, "build", java.util.List.of(),
                    "ab".repeat(32), "instance", 0, null).password());
        }
    }

    @Test
    void passwordBoundsPreserveWhitespaceUnicodeAndRejectMalformedUtf16() {
        assertThrows(IllegalArgumentException.class, () -> RoomPassword.of("short"));
        assertThrows(IllegalArgumentException.class, () -> RoomPassword.of("x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> RoomPassword.of("x".repeat(15) + '\uD800'));
        String value = "  " + "\uD83D\uDE80".repeat(15) + "  ";
        RoomPassword password = RoomPassword.of(value);
        assertArrayEquals(value.toCharArray(), password.copyCharacters());
        password.close();
        assertThrows(IllegalStateException.class, password::copyCharacters);
    }

    @Test
    void codeParsingAcceptsOnlyDocumentedAsciiForms() {
        assertEquals("0011AZ", RoomInviteCode.parse("o0-iL az").canonical());
        assertEquals("7K3 M9X", RoomInviteCode.parse("7k3-m9x").display());
        for (String invalid : new String[]{"12345", "1234567", "12345U", "12345\t", "１２３４５６", " ".repeat(33)}) {
            assertThrows(IllegalArgumentException.class, () -> RoomInviteCode.parse(invalid));
        }
        assertFalse(RoomInviteCode.parse("7K3M9X").toString().contains("7K3M9X"));
    }
}
