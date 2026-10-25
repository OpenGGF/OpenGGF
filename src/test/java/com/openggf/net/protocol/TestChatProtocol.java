package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestChatProtocol {
    private final ControlJsonCodec codec = new ControlJsonCodec();

    @Test
    void privateAdmissionAndCommunicationMetadataShareTheStrictWireContract() {
        var policy = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, false);
        var descriptor = new ControlMessage.RoomDescriptor("Private", "s2", 1, 0,
                "OPEN", null, 8, false, policy, "QUICK_CHAT");
        var admitted = new ControlMessage.JoinAccepted("token", 0, descriptor,
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of()));
        String wire = codec.encode(null, admitted);
        assertEquals(admitted, codec.decode(wire).message());
        assertThrows(ProtocolViolationException.class, () -> codec.decode(
                wire.replace("\"v\":" + Protocol.VERSION, "\"v\":6")));
    }

    @Test
    void modesRestrictCapabilitiesAndTheirIntersectionNeverAddsOne() {
        assertTrue(ChatMode.FULL_CHAT.allowsText());
        assertTrue(ChatMode.FULL_CHAT.allowsQuickChat());
        assertFalse(ChatMode.QUICK_CHAT.allowsText());
        assertTrue(ChatMode.QUICK_CHAT.allowsQuickChat());
        assertFalse(ChatMode.OFF.allowsText());
        assertFalse(ChatMode.OFF.allowsQuickChat());
        for (ChatMode requested : ChatMode.values()) {
            for (ChatMode limit : ChatMode.values()) {
                ChatMode effective = requested.intersect(limit);
                assertEquals(requested.allowsText() && limit.allowsText(), effective.allowsText());
                assertEquals(requested.allowsQuickChat() && limit.allowsQuickChat(), effective.allowsQuickChat());
                assertEquals(effective, limit.intersect(requested));
            }
        }
        assertEquals(ChatMode.QUICK_CHAT, ChatMode.parse("QUICK_CHAT"));
        assertNull(ChatMode.parse("quick_chat"));
        assertNull(ChatMode.parse(null));
        assertNull(ChatMode.parse("OFF "));
    }

    @Test
    void quickChatUsesStableSemanticIds() {
        assertEquals(List.of("BRUTAL", "TOO_SLOW", "KNOW_THE_WAY", "THIS_IS_THE_WAY",
                "THATS_NO_GOOD", "THIS_IS_ZONE", "GOOD_LUCK", "WELL_PLAYED"),
                java.util.Arrays.stream(QuickChatPhrase.values()).map(Enum::name).toList());
        assertEquals(QuickChatPhrase.BRUTAL, QuickChatPhrase.parse("BRUTAL"));
        assertNull(QuickChatPhrase.parse("Brutal!"));
        assertNull(QuickChatPhrase.parse(null));
    }

    @Test
    void strictCodecRoundTripsModesPhrasesAndDirectoryMetadata() {
        var descriptor = new ControlMessage.RoomDescriptor(
                "Room", "s2", 1, 0, "OPEN", null, 8, false, "QUICK_CHAT");
        var summary = new ControlMessage.RoomSummary(
                "r1", "Room", "s2", 1, 0, "OPEN", 2, 8, "DIRECT", false, "OFF");
        List<ControlMessage> messages = List.of(
                new ControlMessage.SetChatMode("QUICK_CHAT"),
                new ControlMessage.ChatModeState("OFF"),
                new ControlMessage.QuickChat("GOOD_LUCK"),
                new ControlMessage.QuickChatBroadcast(1, "Player", "THIS_IS_ZONE", "s2", 1, 0),
                new ControlMessage.RoomChatModeUpdate("r1", "OFF"),
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "fp"),
                new ControlMessage.RoomListResult(List.of(summary), 0, 1),
                new ControlMessage.JoinAccepted("token", 1, descriptor,
                        new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        for (ControlMessage message : messages) {
            String wire = codec.encode("token", message);
            assertEquals(message, codec.decode(wire).message());
            assertEquals("token", codec.decode(wire).token());
        }
    }

    @Test
    void oldJavaConstructorsDefaultToFullChatButReceivedMetadataRequiresAKnownMode() {
        var descriptor = new ControlMessage.RoomDescriptor("R", "s1", 0, 0, "OPEN", null, 8, false);
        var summary = new ControlMessage.RoomSummary("r", "R", "s1", 0, 0, "OPEN", 1, 8, "RELAY", false);
        assertEquals("FULL_CHAT", descriptor.chatMode());
        assertEquals("FULL_CHAT", summary.chatMode());
        List<ControlMessage> metadata = List.of(
                new ControlMessage.RoomCreate(descriptor, "RELAY", 0, "fp"),
                new ControlMessage.RoomListResult(List.of(summary), 0, 1),
                new ControlMessage.JoinAccepted("token", 0, descriptor,
                        new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        for (ControlMessage message : metadata) {
            String wire = codec.encode(null, message);
            for (String malformed : List.of("null", "123", "true", "{}", "[]", "\"UNKNOWN\"")) {
                assertThrows(ProtocolViolationException.class, () -> codec.decode(
                        wire.replace("\"chatMode\":\"FULL_CHAT\"", "\"chatMode\":" + malformed)));
            }
            assertThrows(ProtocolViolationException.class, () -> codec.decode(
                    wire.replace(",\"chatMode\":\"FULL_CHAT\"", "")));
            assertThrows(ProtocolViolationException.class, () -> codec.decode(
                    wire.replace("\"v\":" + Protocol.VERSION, "\"v\":5")));
        }
    }

    @Test
    void chatFieldsRejectWrongJsonTypesAndClientSuppliedTemplateArguments() {
        for (String type : List.of("SetChatMode", "ChatModeState")) {
            for (String invalid : List.of("null", "true", "7", "{}", "[]")) {
                reject("{\"type\":\"" + type + "\",\"mode\":" + invalid + "}");
            }
        }
        for (String invalid : List.of("null", "true", "7", "{}", "[]")) {
            reject("{\"type\":\"QuickChat\",\"phraseId\":" + invalid + "}");
        }
        reject("{\"type\":\"QuickChat\",\"phraseId\":\"BRUTAL\",\"displayName\":\"Forged\"}");
        reject("{\"type\":\"QuickChat\",\"phraseId\":\"THIS_IS_ZONE\",\"zone\":7}");
        reject("{\"type\":\"QuickChatBroadcast\",\"slot\":0,\"displayName\":123,"
                + "\"phraseId\":\"BRUTAL\",\"gameId\":\"s2\",\"zone\":1,\"act\":0}");
        reject("{\"type\":\"QuickChatBroadcast\",\"slot\":0,\"displayName\":\"A\","
                + "\"phraseId\":\"BRUTAL\",\"gameId\":\"s2\",\"zone\":\"1\",\"act\":0}");
        reject("{\"type\":\"ChatModeState\",\"mode\":\"UNKNOWN\"}");
        reject("{\"type\":\"RoomChatModeUpdate\",\"roomId\":\"r\",\"mode\":\"UNKNOWN\"}");
    }

    private void reject(String body) {
        assertThrows(ProtocolViolationException.class,
                () -> codec.decode("{\"v\":" + Protocol.VERSION + ",\"token\":null,\"msg\":" + body + "}"));
    }
}
