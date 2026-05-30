package com.openggf.integration.presence.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.integration.presence.PresencePayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDiscordIpcPresenceClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void connect_sendsHandshakeWithOpenGgfApplicationId() throws IOException {
        FakeTransport transport = new FakeTransport();
        DiscordIpcPresenceClient client = new DiscordIpcPresenceClient(() -> transport);

        client.connect();

        Frame frame = transport.frames.get(0);
        JsonNode json = MAPPER.readTree(frame.json);
        assertEquals(0, frame.opcode);
        assertEquals(1, json.get("v").asInt());
        assertEquals("1510395080652099754", json.get("client_id").asText());
    }

    @Test
    void update_sendsSetActivityWithNonceAndPayload() throws IOException {
        FakeTransport transport = new FakeTransport();
        DiscordIpcPresenceClient client = new DiscordIpcPresenceClient(() -> transport);

        client.connect();
        client.update(new PresencePayload("OpenGGF - Sonic 2",
                "Emerald Hill Zone Act 1 as Sonic - 0:01"));

        Frame frame = transport.frames.get(1);
        JsonNode json = MAPPER.readTree(frame.json);
        assertEquals(1, frame.opcode);
        assertEquals("SET_ACTIVITY", json.get("cmd").asText());
        assertNotNull(json.get("nonce").asText());
        assertEquals("OpenGGF - Sonic 2",
                json.at("/args/activity/details").asText());
        assertEquals("Emerald Hill Zone Act 1 as Sonic - 0:01",
                json.at("/args/activity/state").asText());
    }

    @Test
    void update_reusesStableStartTimestampAcrossActivityUpdates() throws IOException {
        FakeTransport transport = new FakeTransport();
        DiscordIpcPresenceClient client = new DiscordIpcPresenceClient(() -> transport);

        client.connect();
        client.update(new PresencePayload("OpenGGF - Sonic 2",
                "Emerald Hill Zone Act 1 as Sonic - 0:01"));
        client.update(new PresencePayload("OpenGGF - Sonic 2",
                "Emerald Hill Zone Act 1 as Sonic - 0:16"));

        JsonNode first = MAPPER.readTree(transport.frames.get(1).json);
        JsonNode second = MAPPER.readTree(transport.frames.get(2).json);
        long firstStart = first.at("/args/activity/timestamps/start").asLong();
        long secondStart = second.at("/args/activity/timestamps/start").asLong();

        assertTrue(firstStart > 0);
        assertEquals(firstStart, secondStart);
    }

    @Test
    void clear_sendsSetActivityWithNullActivity() throws IOException {
        FakeTransport transport = new FakeTransport();
        DiscordIpcPresenceClient client = new DiscordIpcPresenceClient(() -> transport);

        client.connect();
        client.clear();

        JsonNode json = MAPPER.readTree(transport.frames.get(1).json);
        assertTrue(json.at("/args/activity").isNull());
    }

    @Test
    void close_closesTransport() throws IOException {
        FakeTransport transport = new FakeTransport();
        DiscordIpcPresenceClient client = new DiscordIpcPresenceClient(() -> transport);

        client.connect();
        client.close();

        assertTrue(transport.closed);
    }

    private record Frame(int opcode, String json) {
    }

    private static final class FakeTransport implements DiscordIpcTransport {
        private final List<Frame> frames = new ArrayList<>();
        private boolean closed;

        @Override
        public void send(int opcode, String json) {
            frames.add(new Frame(opcode, json));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
