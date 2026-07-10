package com.openggf.net.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRoomBroker {
    static final class FakeConnection implements HubConnection {
        final List<String> text = new ArrayList<>();
        String closedReason;

        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] data) { }
        @Override public void close(String reason) { closedReason = reason; }
        @Override public String remoteHost() { return "10.0.0.1"; }
    }

    static final class FakeRelays implements RoomBroker.RelayRoomDirectory {
        final List<String> created = new ArrayList<>();
        final List<String> attached = new ArrayList<>();

        @Override public String createRelayRoom(SessionRegistry.RoomEntry entry) {
            created.add(entry.roomId());
            return entry.roomId();
        }
        @Override public void noteGuestTier(String fingerprint, boolean isNew) { }
        @Override public boolean attach(HubConnection connection, String roomId,
                                        String fingerprint, String displayName) {
            attached.add(roomId + ":" + fingerprint);
            return true;
        }
        @Override public void hostLeft(String roomId) { }
    }

    static final class FakeTunnels implements RoomBroker.DirectTunnelDirectory {
        final List<String> registered = new ArrayList<>();
        final List<String> opened = new ArrayList<>();

        @Override public void registerHost(String roomId, HubConnection hostConnection) {
            registered.add(roomId);
        }
        @Override public OptionalInt openGuest(String roomId, HubConnection guestConnection) {
            opened.add(roomId);
            return OptionalInt.of(7);
        }
        @Override public void unregisterHost(String roomId) { registered.remove(roomId); }
    }

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomBroker broker;
    private FakeRelays relays;
    private SqliteIdentityStore store;
    private TrustLadder ladder;
    private FakeTunnels tunnels;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        MasterConfig config = new MasterConfig(null, null, null, false, null, null,
                null, 0, 0, 0, 0, 8, 8, false, 0, 0, 0, 0, 0, 0, 0);
        store = new SqliteIdentityStore(dir.resolve("ids.db"));
        NewIdentityCache cache = new NewIdentityCache(1000, 3_600_000, () -> now);
        ladder = new TrustLadder(store, cache, config.thresholds(), () -> now);
        relays = new FakeRelays();
        tunnels = new FakeTunnels();
        registry = new SessionRegistry(() -> now, config);
        broker = new RoomBroker(PlayerIdentity.loadOrCreate(dir.resolve("master")), config,
                registry, store, ladder, cache, () -> now, relays, tunnels,
                java.util.Set.of("s3k:0:0", "s3k:0:1")::contains);
    }

    @AfterEach
    void closeStore() {
        store.close();
    }

    private static ControlMessage lastMessage(FakeConnection connection) {
        return ControlCodec.decode(connection.text.getLast()).message();
    }

    private String admit(FakeConnection connection, Path identityDir, String name)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        ClientHandshake handshake = new ClientHandshake(identity, name, "0.6:cafe");
        broker.onConnected(connection);
        broker.onText(connection, ControlCodec.encode(null, handshake.hello()));
        broker.onText(connection, ControlCodec.encode(null,
                handshake.onWelcome((ControlMessage.Welcome) lastMessage(connection))));
        if (lastMessage(connection) instanceof ControlMessage.PowChallenge challenge
                && "IDENTITY".equals(challenge.kind())) {
            broker.onText(connection, ControlCodec.encode(null,
                    new ControlMessage.PowSolution("IDENTITY",
                            identity.creationPowNonce(challenge.difficultyBits()))));
        }
        return ((ControlMessage.JoinAccepted) lastMessage(connection)).sessionToken();
    }

    private void establish(String fingerprint) {
        ladder.onCleanRound(fingerprint);
        now += TrustLadder.Thresholds.defaults().establishedAgeMillis() + 1;
        for (int i = 0; i < 9; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            ladder.onCleanRound(fingerprint);
        }
    }

    @Test
    void unknownIdentityMustSolveCreationPow(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        assertNotNull(admit(connection, idDir, "A"));
        assertTrue(connection.text.stream().map(text -> ControlCodec.decode(text).message())
                .anyMatch(ControlMessage.PowChallenge.class::isInstance));
    }

    @Test
    void newTierCannotCreateEstablishedCan(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        String token = admit(connection, idDir, "A");
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Room", "s3k", 0, 0, "OPEN", null, 8, false);
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe")));
        assertInstanceOf(ControlMessage.RoomCreateRejected.class, lastMessage(connection));

        establish(PlayerIdentity.loadOrCreate(idDir).fingerprint());
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe")));
        assertInstanceOf(ControlMessage.RoomCreated.class, lastMessage(connection));
        assertEquals(1, tunnels.registered.size());
    }

    @Test
    void relayCreateSpinsRelayRoomAndJoinAttaches(@TempDir Path hostDir,
                                                  @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Big", "s3k", 0, 0, "OPEN", null, 256, false);
        broker.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoomCreate(descriptor, "RELAY", 0, "0.6:cafe")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        assertEquals(List.of(roomId), relays.created);

        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertEquals("RELAY", ((ControlMessage.RoomJoinResult) lastMessage(guest)).routing());
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RelayAttach(roomId)));
        assertEquals(1, relays.attached.size());
    }

    @Test
    void joinGatesOnDeterminismFingerprint(@TempDir Path hostDir,
                                           @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:AAAA")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();

        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertInstanceOf(ControlMessage.RoomJoinRejected.class, lastMessage(guest));
    }

    @Test
    void bannedIdentityRejectedAtHandshake(@TempDir Path idDir) throws Exception {
        PlayerIdentity banned = PlayerIdentity.loadOrCreate(idDir);
        ladder.sanction(new IdentityStore.SanctionRecord(banned.fingerprint(), "BAN",
                "cheat", "operator", now, Long.MAX_VALUE));
        FakeConnection connection = new FakeConnection();
        assertThrows(Exception.class, () -> admit(connection, idDir, "BAD"));
        assertTrue(connection.text.stream().map(text -> ControlCodec.decode(text).message())
                .anyMatch(ControlMessage.JoinRejected.class::isInstance));
        assertNotNull(connection.closedReason);
    }

    @Test
    void hostDisconnectRemovesTheirRooms(@TempDir Path hostDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:cafe")));
        broker.onDisconnected(host);
        assertTrue(tunnels.registered.isEmpty());

        FakeConnection browser = new FakeConnection();
        String browserToken = admit(browser, hostDir.resolve("other"), "B");
        broker.onText(browser, ControlCodec.encode(browserToken,
                new ControlMessage.RoomListRequest(null, 0)));
        assertTrue(((ControlMessage.RoomListResult) lastMessage(browser)).rooms().isEmpty());
    }

    @Test
    void directRoomsAreCappedAtEightPlayers(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        String token = admit(connection, idDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(idDir).fingerprint());
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Huge", "s3k", 0, 0, "OPEN", null, 64, false);
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe")));
        assertInstanceOf(ControlMessage.RoomCreateRejected.class, lastMessage(connection));
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "RELAY", 0, "0.6:cafe")));
        assertInstanceOf(ControlMessage.RoomCreated.class, lastMessage(connection));
    }

    @Test
    void relayAttachWithoutGrantedJoinIsRejected(@TempDir Path hostDir,
                                                  @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("Big", "s3k", 0, 0, "OPEN", null, 64, false),
                "RELAY", 0, "0.6:cafe")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();

        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RelayAttach(roomId)));
        assertInstanceOf(ControlMessage.RoomJoinRejected.class, lastMessage(guest));
        assertTrue(relays.attached.isEmpty());
    }

    @Test
    void votePoolAndTrackUpdatesRequireKnownSameGameTracks(@TempDir Path idDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, idDir, "HOST");
        String fingerprint = PlayerIdentity.loadOrCreate(idDir).fingerprint();
        establish(fingerprint);
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "R", "s3k", 0, 0, "OPEN", null, 8, false);
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of("s2:0:0"))));
        assertInstanceOf(ControlMessage.RoomCreateRejected.class, lastMessage(host));
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of("s3k:0:1"))));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        assertEquals(List.of("s3k:0:1"), registry.find(roomId).orElseThrow().voteTrackKeys());

        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomTrackUpdate(roomId, 99, 99)));
        assertEquals(0, registry.find(roomId).orElseThrow().descriptor().zone());
        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomTrackUpdate(roomId, 0, 1)));
        assertEquals(1, registry.find(roomId).orElseThrow().descriptor().act());
    }
}
