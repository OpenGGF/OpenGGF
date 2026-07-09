package com.openggf.net.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRelayRoomManager {
    static final class FakeConnection implements HubConnection {
        final List<String> text = new ArrayList<>();
        final List<byte[]> binary = new ArrayList<>();
        String closedReason;

        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] data) { binary.add(data); }
        @Override public void close(String reason) { closedReason = reason; }
        @Override public String remoteHost() { return "10.0.0.2"; }
    }

    private static ControlMessage lastMessage(FakeConnection connection) {
        return ControlCodec.decode(connection.text.getLast()).message();
    }

    private static <T extends ControlMessage> T lastOfType(
            FakeConnection connection, Class<T> type) {
        for (int i = connection.text.size() - 1; i >= 0; i--) {
            ControlMessage message = ControlCodec.decode(connection.text.get(i)).message();
            if (type.isInstance(message)) {
                return type.cast(message);
            }
        }
        throw new AssertionError("missing message " + type.getSimpleName());
    }

    private static RelayRoomManager manager(PlayerIdentity master, TrustLadder ladder,
                                             long[] now) {
        return new RelayRoomManager(master, ladder, TrackValidationProfileSource.none(),
                List.of(Runnable::run), Runnable::run, () -> now[0],
                (roomId, count) -> { });
    }

    @Test
    void createAttachAndRoomHandshakeWork(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            RelayRoomManager manager = manager(
                    PlayerIdentity.loadOrCreate(dir.resolve("m")), ladder, now);
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0],
                    MasterConfig.defaults()).create(new ControlMessage.RoomDescriptor(
                            "Big", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", "host-fp", "1.1.1.1", 0, "0.6:cafe");
            assertEquals(entry.roomId(), manager.createRelayRoom(entry));
            assertEquals(1, manager.roomCount());

            FakeConnection guest = new FakeConnection();
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
            assertTrue(manager.attach(guest, entry.roomId(), identity.fingerprint(), "GUEST"));
            ClientHandshake handshake = new ClientHandshake(identity, "GUEST", "0.6:cafe");
            var access = manager.find(entry.roomId()).orElseThrow();
            access.room().onText(guest, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(guest, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(guest))));
            lastOfType(guest, ControlMessage.JoinAccepted.class);

            manager.hostLeft(entry.roomId());
            assertEquals(0, manager.roomCount());
            assertFalse(manager.attach(new FakeConnection(), entry.roomId(), "x", "X"));
        }
    }

    @Test
    void wrongFingerprintGuestIsRejectedByTheRoom(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            RelayRoomManager manager = manager(
                    PlayerIdentity.loadOrCreate(dir.resolve("m")), ladder, now);
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0],
                    MasterConfig.defaults()).create(new ControlMessage.RoomDescriptor(
                            "Big", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", "host-fp", "1.1.1.1", 0, "0.6:cafe");
            manager.createRelayRoom(entry);

            FakeConnection guest = new FakeConnection();
            manager.attach(guest, entry.roomId(), "guest-fp", "GUEST");
            ClientHandshake handshake = new ClientHandshake(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", "0.6:cafe");
            var access = manager.find(entry.roomId()).orElseThrow();
            access.room().onText(guest, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(guest, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(guest))));
            assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(guest));
        }
    }

    @Test
    void creatorCanStartRoundAndImpostorCannotAttach(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            RelayRoomManager manager = manager(
                    PlayerIdentity.loadOrCreate(dir.resolve("m")), ladder, now);
            PlayerIdentity creatorIdentity = PlayerIdentity.loadOrCreate(dir.resolve("creator"));
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0],
                    MasterConfig.defaults()).create(new ControlMessage.RoomDescriptor(
                            "Big", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", creatorIdentity.fingerprint(), "1.1.1.1", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();

            FakeConnection creator = new FakeConnection();
            manager.attach(creator, entry.roomId(), creatorIdentity.fingerprint(), "HOST");
            ClientHandshake handshake = new ClientHandshake(
                    creatorIdentity, "HOST", "0.6:cafe");
            access.room().onText(creator, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(creator, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(creator))));
            String token = lastOfType(creator, ControlMessage.JoinAccepted.class).sessionToken();
            access.room().onText(creator, ControlCodec.encode(token,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 60, "OPEN", null))));
            assertTrue(creator.text.stream().map(text -> ControlCodec.decode(text).message())
                    .anyMatch(ControlMessage.RoundStart.class::isInstance));

            FakeConnection impostor = new FakeConnection();
            manager.attach(impostor, entry.roomId(), creatorIdentity.fingerprint(), "BAD");
            ClientHandshake wrong = new ClientHandshake(
                    PlayerIdentity.loadOrCreate(dir.resolve("wrong")), "BAD", "0.6:cafe");
            access.room().onText(impostor, ControlCodec.encode(null, wrong.hello()));
            access.room().onText(impostor, ControlCodec.encode(null,
                    wrong.onWelcome((ControlMessage.Welcome) lastMessage(impostor))));
            assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(impostor));
        }
    }
}
