package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Structured envelope mutations that random-byte fuzzing is unlikely to reach. */
class TestControlCodecAdversarial {
    private final ControlJsonCodec codec = new ControlJsonCodec();

    @Test
    void protocolVersionCannotAliasTheSupportedVersionByIntegerTruncation() {
        String valid = codec.encode(null, new ControlMessage.Ping(7));
        assertEquals(new ControlMessage.Ping(7), codec.decode(valid).message());
        for (String version : List.of("4294967299", "-4294967293",
                "18446744073709551619", "3.0", "\"3\"", "null", "true")) {
            String mutated = valid.replace("\"v\":3", "\"v\":" + version);
            assertNotEquals(valid, mutated);
            assertThrows(ProtocolViolationException.class, () -> codec.decode(mutated),
                    "version=" + version);
        }
    }

    @Test
    void oneFrameMustContainExactlyOneEnvelope() {
        String valid = codec.encode("session", new ControlMessage.Chat("hello"));
        assertEquals(new ControlMessage.Chat("hello"), codec.decode(" \n" + valid + "\t ").message());
        for (String suffix : List.of(valid, "{}", "[]", "null", "true", "7", "\"tail\"")) {
            String mutated = valid + " \n" + suffix;
            assertThrows(ProtocolViolationException.class, () -> codec.decode(mutated),
                    "trailing root=" + suffix);
        }
    }

    @Test
    void nestedIntegerFieldsRejectFractionalAndOutOfRangeValues() {
        String valid = codec.encode("session", new ControlMessage.AttemptStart(1));
        assertEquals(new ControlMessage.AttemptStart(1), codec.decode(valid).message());
        for (String value : List.of("4294967297", "-4294967295", "1.5")) {
            String mutated = valid.replace("\"attemptId\":1", "\"attemptId\":" + value);
            assertThrows(ProtocolViolationException.class, () -> codec.decode(mutated),
                    "attemptId=" + value);
        }
    }

    @Test
    void membershipDeltaSlotsRejectExplicitNull() {
        String left = codec.encode("session", new ControlMessage.PlayerLeft(4));
        assertThrows(ProtocolViolationException.class,
                () -> codec.decode(left.replace("\"slot\":4", "\"slot\":null")));

        String upsert = codec.encode("session", new ControlMessage.PlayerUpsert(
                new ControlMessage.PlayerInfo(4, "identity", "Player", "sonic")));
        assertThrows(ProtocolViolationException.class,
                () -> codec.decode(upsert.replace("\"slot\":4", "\"slot\":null")));
    }
}
