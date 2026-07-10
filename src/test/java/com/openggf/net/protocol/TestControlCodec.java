package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestControlCodec {
    @Test
    void roundTripsHelloWithToken() {
        ControlMessage.Hello hello = new ControlMessage.Hello(
                Protocol.VERSION, "cHVia2V5", "Farrell", "0.6:cafe1234");
        String wire = ControlCodec.encode("tok123", hello);
        ControlCodec.DecodedControl back = ControlCodec.decode(wire);
        assertEquals("tok123", back.token());
        assertEquals(hello, back.message());
    }

    @Test
    void roundTripsNullTokenAndNestedRecords() {
        ControlMessage.JoinAccepted accepted = new ControlMessage.JoinAccepted(
                "tok", 2,
                new ControlMessage.RoomDescriptor("LAN Room", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0L, 0L, List.of()));
        ControlCodec.DecodedControl back = ControlCodec.decode(ControlCodec.encode(null, accepted));
        assertNull(back.token());
        assertEquals(accepted, back.message());
        assertFalse(((ControlMessage.JoinAccepted) back.message()).room().verified());
    }

    @Test
    void roundTripsEveryMessageType() {
        ControlMessage.RoundConfig cfg =
                new ControlMessage.RoundConfig("s3k", 0, 0, 300, "LOCKED", "sonic");
        List<ControlMessage.StandingsRow> rows =
                List.of(new ControlMessage.StandingsRow(0, "A", "sonic", 3600, 1, "NONE"));
        List<ControlMessage> all = List.of(
                new ControlMessage.Hello(1, "a", "b", "c"),
                new ControlMessage.Welcome(1, "bm9uY2U=", "serverfp"),
                new ControlMessage.AuthProof("c2ln"),
                new ControlMessage.JoinRejected("room full"),
                new ControlMessage.Kick("protocol violation"),
                new ControlMessage.RoomState(
                        List.of(new ControlMessage.PlayerInfo(0, "fp", "A", "sonic", false))),
                new ControlMessage.SelectCharacter("tails"),
                new ControlMessage.Chat("hi"),
                new ControlMessage.ChatBroadcast(0, "A", "hi"),
                new ControlMessage.Ping(12345L),
                new ControlMessage.Pong(12345L, 99999L),
                new ControlMessage.RoundConfigure(cfg),
                new ControlMessage.RoundStart(cfg, 1000L, 301000L),
                new ControlMessage.RoundEnd(rows),
                new ControlMessage.StandingsDelta(rows),
                new ControlMessage.AttemptStart(1),
                new ControlMessage.AttemptFinish(
                        1, 3600, 12, 3612, "ab".repeat(32), "cd".repeat(32), null),
                new ControlMessage.AttemptReset(1),
                new ControlMessage.TrackVote("s3k:0:0"),
                new ControlMessage.RecordingRequest(1, "ab".repeat(32), null));
        for (ControlMessage msg : all) {
            assertEquals(msg, ControlCodec.decode(ControlCodec.encode("t", msg)).message(),
                    "round-trip failed for " + msg.getClass().getSimpleName());
        }
    }

    @Test
    void rejectsOversizedText() {
        String big = "{\"v\":1,\"msg\":{\"type\":\"Chat\",\"text\":\""
                + "x".repeat(Protocol.MAX_CONTROL_BYTES) + "\"}}";
        assertThrows(ProtocolViolationException.class, () -> ControlCodec.decode(big));
    }

    @Test
    void rejectsUnknownTypeWrongVersionAndGarbage() {
        assertThrows(ProtocolViolationException.class,
                () -> ControlCodec.decode("{\"v\":1,\"msg\":{\"type\":\"Nope\"}}"));
        assertThrows(ProtocolViolationException.class,
                () -> ControlCodec.decode(
                        "{\"v\":2,\"msg\":{\"type\":\"Ping\",\"t0ClientMillis\":1}}"));
        assertThrows(ProtocolViolationException.class,
                () -> ControlCodec.decode("{\"v\":1}"));
        assertThrows(ProtocolViolationException.class, () -> ControlCodec.decode("not json at all"));
    }

    @Test
    void ignoresUnknownFieldsForForwardCompat() {
        ControlCodec.DecodedControl back = ControlCodec.decode(
                "{\"v\":1,\"token\":null,\"msg\":{\"type\":\"Ping\","
                        + "\"t0ClientMillis\":7,\"futureField\":true}}");
        assertEquals(new ControlMessage.Ping(7L), back.message());
    }
}
