package com.openggf.integration.presence.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.integration.presence.PresenceClient;
import com.openggf.integration.presence.PresencePayload;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public final class DiscordIpcPresenceClient implements PresenceClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;

    private final DiscordIpcTransportFactory transportFactory;
    private final long activityStartEpochSeconds;
    private DiscordIpcTransport transport;

    public DiscordIpcPresenceClient(DiscordIpcTransportFactory transportFactory) {
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
        this.activityStartEpochSeconds = System.currentTimeMillis() / 1000L;
    }

    @Override
    public void connect() throws IOException {
        if (transport != null) {
            return;
        }
        transport = transportFactory.open();
        ObjectNode handshake = MAPPER.createObjectNode();
        handshake.put("v", 1);
        handshake.put("client_id", DiscordPresenceConstants.APPLICATION_ID);
        transport.send(OPCODE_HANDSHAKE, MAPPER.writeValueAsString(handshake));
    }

    @Override
    public void update(PresencePayload payload) throws IOException {
        ensureConnected();
        transport.send(OPCODE_FRAME, MAPPER.writeValueAsString(setActivity(payload)));
    }

    @Override
    public void clear() throws IOException {
        ensureConnected();
        transport.send(OPCODE_FRAME, MAPPER.writeValueAsString(setActivity(null)));
    }

    @Override
    public void close() throws IOException {
        if (transport != null) {
            transport.close();
            transport = null;
        }
    }

    private void ensureConnected() throws IOException {
        if (transport == null) {
            connect();
        }
    }

    private ObjectNode setActivity(PresencePayload payload) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("cmd", "SET_ACTIVITY");
        ObjectNode args = root.putObject("args");
        args.put("pid", ProcessHandle.current().pid());
        if (payload == null) {
            args.set("activity", MAPPER.nullNode());
        } else {
            ObjectNode activity = args.putObject("activity");
            activity.put("details", payload.details());
            if (payload.state() != null && !payload.state().isBlank()) {
                activity.put("state", payload.state());
            }
            activity.putObject("timestamps").put("start", activityStartEpochSeconds);
        }
        root.put("nonce", UUID.randomUUID().toString());
        return root;
    }
}
