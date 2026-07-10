package com.openggf.tools.net;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;

import java.nio.file.Path;

/** Headless, ROM-free room participant used by {@link GhostLoadTestTool}. */
public final class BotClient {
    private final RoomHost room;
    private final GhostLoadTestTool.Behavior behavior;
    private final Connection connection = new Connection();
    private String token;
    private int slot = -1;
    private int nextFrame;

    public BotClient(RoomHost room, GhostLoadTestTool.Behavior behavior,
                     Path identityDir, String fingerprint) throws Exception {
        this.room = room;
        this.behavior = behavior;
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        room.onConnected(connection);
        if (behavior == GhostLoadTestTool.Behavior.HANDSHAKE_ABANDON) {
            return;
        }
        ClientHandshake handshake = new ClientHandshake(identity,
                "BOT-" + identity.fingerprint().substring(0, 6), fingerprint);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) connection.lastControl();
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        ControlMessage.JoinAccepted accepted = connection.findJoin();
        token = accepted.sessionToken();
        slot = accepted.playerSlot();
    }

    public void publishTick() {
        if (connection.closed || slot < 0) {
            return;
        }
        switch (behavior) {
            case NORMAL, ADVERSARIAL_MIX -> publishFrames(3, false);
            case PACING_SLOW -> publishFrames(1, false);
            case TELEPORT -> publishFrames(3, true);
            case OVERSIZED -> room.onBinary(connection,
                    new byte[Protocol.MAX_BINARY_BYTES + 1]);
            case FLOOD -> {
                for (int i = 0; i < 5 && !connection.closed; i++) {
                    publishFrames(GhostPackets.MAX_UPSTREAM_FRAMES_PER_PACKET, false);
                }
            }
            case HANDSHAKE_ABANDON -> { }
        }
    }

    public void finish() {
        if (connection.closed || token == null || behavior != GhostLoadTestTool.Behavior.NORMAL) {
            return;
        }
        room.onText(connection, ControlCodec.encode(token,
                new ControlMessage.AttemptFinish(1, Math.max(1, nextFrame), 0,
                        nextFrame, "ab".repeat(32), "cd".repeat(32), null)));
    }

    public boolean adversaryCaught() {
        return behavior != GhostLoadTestTool.Behavior.NORMAL
                && behavior != GhostLoadTestTool.Behavior.ADVERSARIAL_MIX
                && (connection.closed || (slot >= 0 && room.hub().isAttemptFlagged(slot)));
    }

    public int maxObservedQueuedBytes() {
        return connection.maxPacketBytes;
    }

    public void disconnect() {
        room.onDisconnected(connection);
    }

    private void publishFrames(int count, boolean teleport) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int index = 0; index < count; index++) {
            int frame = nextFrame + index;
            int base = slot * 32;
            int x = teleport ? base + ((frame & 1) == 0 ? 0 : 1000)
                    : base + frame / 3;
            GhostFrameCodec.encode(new GhostFrame(x, 100, 1,
                    false, false, false, 0, false), data,
                    index * GhostFrameCodec.BYTES);
        }
        room.onBinary(connection, GhostPackets.encodeFrames(1, nextFrame, data));
        nextFrame += count;
    }

    private static final class Connection implements HubConnection {
        private final java.util.List<String> text = new java.util.ArrayList<>();
        private boolean closed;
        private int maxPacketBytes;

        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] data) {
            maxPacketBytes = Math.max(maxPacketBytes, data.length);
        }
        @Override public void close(String reason) { closed = true; }
        @Override public String remoteHost() { return "load-bot"; }

        private ControlMessage lastControl() {
            return ControlCodec.decode(text.getLast()).message();
        }

        private ControlMessage.JoinAccepted findJoin() {
            return text.stream().map(value -> ControlCodec.decode(value).message())
                    .filter(ControlMessage.JoinAccepted.class::isInstance)
                    .map(ControlMessage.JoinAccepted.class::cast)
                    .findFirst().orElseThrow();
        }
    }
}
