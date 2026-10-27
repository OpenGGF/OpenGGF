package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.hub.HandshakeTransport;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/** Small reproducible sequences exercise the broker, rather than only its JSON parser. */
@Timeout(30)
class TestRoomBrokerAdversarial {
    static final String FP = "adversarial:test-build";
    private static final long SEED = 0x4D4153544552L;

    enum Stage { NEW, CHALLENGED, IDENTITY_POW, JOIN_POW, ADMITTED }

    static final class Connection implements HubConnection, HandshakeTransport {
        final List<ControlMessage> received = new ArrayList<>();
        final List<byte[]> binary = new ArrayList<>();
        String closed;
        @Override public String authenticationBinding() { return AuthenticationTranscript.LOCAL_PLAINTEXT; }
        @Override public AuthenticationTranscript.Role authenticationRole() {
            return AuthenticationTranscript.Role.ROOM_RELAY;
        }
        @Override public String remoteHost() { return "127.0.0.1"; }
        @Override public void sendText(String text) {
            assertTrue(received.size() < 128, "test response budget exceeded");
            received.add(ControlCodec.decode(text).message());
        }
        @Override public void sendBinary(byte[] data) {
            assertTrue(binary.size() < 128, "test binary response budget exceeded");
            binary.add(data.clone());
        }
        @Override public void close(String reason) { closed = reason; }
        ControlMessage last() { return received.getLast(); }
    }

    static final class Fixture implements AutoCloseable {
        final long[] now = {1_000_000};
        final MasterConfig config;
        final SqliteIdentityStore store;
        final PlayerIdentity master;
        final SessionRegistry registry;
        final GuestTunnelRouter tunnels = new GuestTunnelRouter();
        final RelayRoomManager relays;
        final List<HubConnection> relayAttachments = new ArrayList<>();
        final RoomBroker broker;

        Fixture(Path dir) throws Exception { this(dir, Runnable::run); }
        Fixture(Path dir, Executor roomLoop) throws Exception {
            this(dir, roomLoop, TestMasterServer.testConfig());
        }
        Fixture(Path dir, Executor roomLoop, MasterConfig config) throws Exception {
            this.config = config;
            store = new SqliteIdentityStore(dir.resolve("ids.db"));
            master = PlayerIdentity.loadOrCreate(dir.resolve("master"));
            NewIdentityCache cache = new NewIdentityCache(100, 3_600_000, () -> now[0]);
            TrustLadder ladder = new TrustLadder(store, cache, config.thresholds(), () -> now[0]);
            registry = new SessionRegistry(() -> now[0], config);
            relays = new RelayRoomManager(master, ladder, TrackValidationProfileSource.none(),
                    List.of(roomLoop), Runnable::run, () -> now[0], registry::heartbeat);
            RoomBroker.RelayRoomDirectory recordingRelays = new RoomBroker.RelayRoomDirectory() {
                @Override public String createRelayRoom(SessionRegistry.RoomEntry entry) {
                    return relays.createRelayRoom(entry);
                }
                @Override public void noteGuestTier(String fingerprint, boolean isNew) {
                    relays.noteGuestTier(fingerprint, isNew);
                }
                @Override public boolean attach(HubConnection connection, String roomId,
                                                String fingerprint, String displayName,
                                                String instanceId, String grantReference,
                                                long routeGeneration) {
                    relayAttachments.add(connection);
                    return relays.attach(connection, roomId, fingerprint, displayName,
                            instanceId, grantReference, routeGeneration);
                }
                @Override public void hostLeft(String roomId) { relays.hostLeft(roomId); }
            };
            broker = new RoomBroker(master, config, registry, store, ladder, cache,
                    () -> now[0], recordingRelays, tunnels,
                    key -> "s3k:0:0".equals(key) || "s3k:0:1".equals(key));
        }

        void establish(PlayerIdentity identity) {
            store.establishForTest(identity.fingerprint(),
                    now[0] - config.thresholds().establishedAgeMillis() - 1,
                    now[0], config.thresholds().establishedCleanRounds());
        }

        ClientHandshake handshake(PlayerIdentity identity) {
            return new ClientHandshake(identity, "Peer", FP, null,
                    AuthenticationTranscript.Role.MASTER,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
        }

        String admit(Connection connection, PlayerIdentity identity) throws Exception {
            return admit(connection, identity, "Peer");
        }

        ControlMessage beginAdmission(Connection connection, PlayerIdentity identity, String name)
                throws Exception {
            ClientHandshake handshake = new ClientHandshake(identity, name, FP, null,
                    AuthenticationTranscript.Role.MASTER,
                    () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
            broker.onConnected(connection);
            send(connection, null, handshake.hello());
            send(connection, null, handshake.onWelcome(assertInstanceOf(
                    ControlMessage.Welcome.class, connection.last())));
            return connection.last();
        }

        String admit(Connection connection, PlayerIdentity identity, String name) throws Exception {
            beginAdmission(connection, identity, name);
            if (connection.last() instanceof ControlMessage.PowChallenge challenge) {
                send(connection, null, new ControlMessage.PowSolution("IDENTITY",
                        identity.creationPowNonce(challenge.difficultyBits())));
            }
            return assertInstanceOf(ControlMessage.JoinAccepted.class, connection.last()).sessionToken();
        }

        String create(Connection connection, String token, String routing) {
            send(connection, token, new ControlMessage.RoomCreate(
                    new ControlMessage.RoomDescriptor("Witness", "s3k", 0, 0,
                            "OPEN", null, 8, false), routing,
                    "DIRECT".equals(routing) ? 27888 : 0, FP,
                    List.of(), "ab".repeat(32),
                    "DIRECT".equals(routing) ? "witness-instance" : null, 0, null));
            ControlMessage.RoomCreated created = assertInstanceOf(
                    ControlMessage.RoomCreated.class, connection.last());
            if ("DIRECT".equals(routing)) {
                send(connection, token, new ControlMessage.RoomActivate(
                        created.roomId(), created.instanceId()));
                assertTrue(assertInstanceOf(ControlMessage.RoomActivated.class,
                        connection.last()).active());
            }
            return created.roomId();
        }

        void send(Connection connection, String token, ControlMessage message) {
            broker.onText(connection, ControlCodec.encode(token, message));
        }

        void attach(Connection connection, String token, String roomId) {
            ControlMessage.RoomJoinResult route = connection.received.stream()
                    .filter(ControlMessage.RoomJoinResult.class::isInstance)
                    .map(ControlMessage.RoomJoinResult.class::cast)
                    .filter(join -> join.roomId().equals(roomId))
                    .reduce((earlier, later) -> later).orElse(null);
            send(connection, token, route == null
                    ? new ControlMessage.RelayAttach(roomId, "ungranted", 1)
                    : new ControlMessage.RelayAttach(roomId, route.grantReference(),
                            route.routeGeneration()));
        }
        @Override public void close() {
            try { relays.close(); } finally { store.close(); }
        }
    }

    private static MasterConfig smallStampCacheConfig() {
        return new MasterConfig(0, null, null, true, "ids.db", 0, "test",
                0, 0, 0, 0, 8, 8, false, 0, 0, 0, 0, 0, 2, 1);
    }

    @Test
    void anonymousIdentityReconnectStampExpiresFromProofTime(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, Runnable::run, smallStampCacheConfig())) {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("anonymous"));
            Connection first = new Connection();
            f.admit(first, identity, "");
            assertTrue(f.store.find(identity.fingerprint()).isEmpty());
            f.broker.onDisconnected(first);
            f.now[0] += 30_000;
            Connection recent = new Connection();
            assertInstanceOf(ControlMessage.JoinAccepted.class,
                    f.beginAdmission(recent, identity, ""));
            f.broker.onDisconnected(recent);
            f.now[0] += 30_000;
            Connection expired = new Connection();
            assertEquals("IDENTITY", assertInstanceOf(ControlMessage.PowChallenge.class,
                    f.beginAdmission(expired, identity, "")).kind());
        }
    }

    @Test
    void anonymousIdentityReconnectStampsEvictLeastRecentlyUsed(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir, Runnable::run, smallStampCacheConfig())) {
            PlayerIdentity a = PlayerIdentity.loadOrCreate(dir.resolve("a"));
            PlayerIdentity b = PlayerIdentity.loadOrCreate(dir.resolve("b"));
            PlayerIdentity c = PlayerIdentity.loadOrCreate(dir.resolve("c"));
            for (PlayerIdentity identity : List.of(a, b)) {
                Connection connection = new Connection();
                f.admit(connection, identity, "");
                f.broker.onDisconnected(connection);
            }
            Connection recent = new Connection();
            assertInstanceOf(ControlMessage.JoinAccepted.class, f.beginAdmission(recent, a, ""));
            f.broker.onDisconnected(recent);
            Connection newest = new Connection();
            f.admit(newest, c, "");
            f.broker.onDisconnected(newest);
            for (PlayerIdentity retained : List.of(a, c)) {
                Connection connection = new Connection();
                assertInstanceOf(ControlMessage.JoinAccepted.class,
                        f.beginAdmission(connection, retained, ""));
                f.broker.onDisconnected(connection);
            }
            assertInstanceOf(ControlMessage.PowChallenge.class,
                    f.beginAdmission(new Connection(), b, ""));
        }
    }

    @Test
    void expiredRelayAndTunnelGrantsRejectAttachButFreshGrantsAttach(@TempDir Path dir)
            throws Exception {
        for (String routing : List.of("RELAY", "DIRECT")) {
            try (Fixture f = new Fixture(dir.resolve(routing))) {
                PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve(routing + "-owner"));
                f.establish(owner);
                Connection host = new Connection();
                String hostToken = f.admit(host, owner);
                String room = f.create(host, hostToken, routing);
                Connection guest = new Connection();
                String token = f.admit(guest, PlayerIdentity.loadOrCreate(dir.resolve(routing + "-guest")));
                f.send(guest, token, new ControlMessage.RoomJoinRequest(room));
                assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
                f.now[0] += 30_000;
                f.attach(guest, token, room);
                assertEquals("join not granted", assertInstanceOf(
                        ControlMessage.RoomJoinRejected.class, guest.last()).reason());
                assertTrue(f.broker.takeAttachResult(guest).isEmpty());
                assertTrue(f.relayAttachments.isEmpty());
                assertEquals(0, f.tunnels.activeTunnels());
                f.send(guest, token, new ControlMessage.RoomJoinRequest(room));
                f.attach(guest, token, room);
                RoomBroker.AttachResult result = f.broker.takeAttachResult(guest).orElseThrow();
                assertEquals(room, result.roomId());
                assertEquals("RELAY".equals(routing) ? RoomBroker.AttachMode.ROOM
                        : RoomBroker.AttachMode.TUNNEL_GUEST, result.mode());
            }
        }
    }

    @Test
    void repeatedJoinsRetainOnlyFiveRecentDistinctAttachGrants(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection guest = new Connection();
            String token = f.admit(guest, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
            List<String> rooms = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                // Independent live rooms let the guest accumulate grants without host caps.
                SessionRegistry.RegistrationLease registration =
                        new SessionRegistry.RegistrationLease(new Connection(), i + 1);
                SessionRegistry.RoomEntry room = f.registry.create(new ControlMessage.RoomDescriptor(
                        "Room", "s3k", 0, 0, "OPEN", null, 8, false), "DIRECT",
                        "host-" + i, "address-" + i, 27888, FP,
                        List.of(), "ab".repeat(32), registration);
                f.tunnels.registerHost(room.roomId(), registration);
                rooms.add(room.roomId());
                f.send(guest, token, new ControlMessage.RoomJoinRequest(room.roomId()));
                assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
                if (i % 5 == 4 && i < 19) f.now[0] += 10_000;
            }
            // The oldest grant from the final ten-second window is still unexpired,
            // but must have been evicted to keep only five pending rooms.
            f.attach(guest, token, rooms.get(10));
            assertEquals("join not granted", assertInstanceOf(
                    ControlMessage.RoomJoinRejected.class, guest.last()).reason());
            assertEquals(0, f.tunnels.activeTunnels());
            f.attach(guest, token, rooms.getLast());
            assertEquals(rooms.getLast(), f.broker.takeAttachResult(guest).orElseThrow().roomId());
        }
    }

    @Test
    void relayCreatorGrantExpiresAndFreshJoinStillAttachesHost(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            Connection host = new Connection();
            String token = f.admit(host, owner);
            String room = f.create(host, token, "RELAY");
            f.send(host, token, new ControlMessage.RoomJoinRequest(room));
            f.now[0] += 30_000;
            f.attach(host, token, room);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, host.last());
            assertTrue(f.relayAttachments.isEmpty());
            f.send(host, token, new ControlMessage.RoomJoinRequest(room));
            f.attach(host, token, room);
            assertEquals(RoomBroker.AttachMode.ROOM,
                    f.broker.takeAttachResult(host).orElseThrow().mode());
        }
    }

    @Test
    void releasedRelaySlotCanRejoinBeforeFullDirectoryCountRefreshes(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
            f.establish(owner);
            f.establish(guest);
            Connection host = new Connection();
            String hostToken = f.admit(host, owner);
            f.send(host, hostToken, new ControlMessage.RoomCreate(
                    new ControlMessage.RoomDescriptor("Capacity", "s3k", 0, 0,
                            "OPEN", null, 2, false), "RELAY", 0, FP));
            String roomId = assertInstanceOf(ControlMessage.RoomCreated.class, host.last()).roomId();
            f.send(host, hostToken, new ControlMessage.RoomJoinRequest(roomId));
            f.attach(host, hostToken, roomId);
            assertEquals(RoomBroker.AttachMode.ROOM, f.broker.takeAttachResult(host).orElseThrow().mode());
            RoomHost room = f.relays.find(roomId).orElseThrow().room();
            proveRelayIdentity(room, host, owner);
            assertEquals(1, room.playerCount());

            Connection first = new Connection();
            String firstToken = f.admit(first, guest);
            f.send(first, firstToken, new ControlMessage.RoomJoinRequest(roomId));
            assertInstanceOf(ControlMessage.RoomJoinResult.class, first.last());
            f.attach(first, firstToken, roomId);
            assertTrue(f.broker.takeAttachResult(first).isPresent());
            proveRelayIdentity(room, first, guest);
            assertEquals(2, room.playerCount());
            ControlMessage.JoinAccepted original = first.received.stream()
                    .filter(ControlMessage.JoinAccepted.class::isInstance)
                    .map(ControlMessage.JoinAccepted.class::cast)
                    .filter(accepted -> accepted.room() != null).findFirst().orElseThrow();
            assertEquals(2, room.playerCount());
            for (int tick = 0; tick < RelayRoomManager.PLAYER_COUNT_INTERVAL_TICKS; tick++) {
                f.relays.tickAll();
            }
            assertEquals(2, f.registry.find(roomId).orElseThrow().playerCount());

            room.onDisconnected(first);
            f.broker.onDisconnected(first);
            assertEquals(1, room.playerCount());
            assertEquals(2, f.registry.find(roomId).orElseThrow().playerCount(),
                    "discovery still has the previous full-room snapshot");
            Connection replacement = new Connection();
            String replacementToken = f.admit(replacement, guest);
            f.send(replacement, replacementToken, new ControlMessage.RoomJoinRequest(roomId));
            assertInstanceOf(ControlMessage.RoomJoinResult.class, replacement.last(),
                    "periodic discovery counts must not deny a released authoritative slot");
            f.attach(replacement, replacementToken, roomId);
            assertTrue(f.broker.takeAttachResult(replacement).isPresent());
            proveRelayIdentity(room, replacement, guest);
            ControlMessage.JoinAccepted recovered = replacement.received.stream()
                    .filter(ControlMessage.JoinAccepted.class::isInstance)
                    .map(ControlMessage.JoinAccepted.class::cast)
                    .filter(accepted -> accepted.room() != null).findFirst().orElseThrow();
            assertEquals(original.playerSlot(), recovered.playerSlot());
            assertNotEquals(original.sessionToken(), recovered.sessionToken());
            assertTrue(replacement.received.stream().anyMatch(
                    ControlMessage.RoomAccessChallenge.class::isInstance));
            assertEquals(2, room.playerCount());

            PlayerIdentity extra = PlayerIdentity.loadOrCreate(dir.resolve("overflow"));
            f.establish(extra);
            Connection overflow = new Connection();
            String overflowToken = f.admit(overflow, extra);
            f.send(overflow, overflowToken, new ControlMessage.RoomJoinRequest(roomId));
            assertInstanceOf(ControlMessage.RoomJoinResult.class, overflow.last());
            f.attach(overflow, overflowToken, roomId);
            assertTrue(f.broker.takeAttachResult(overflow).isPresent());
            proveRelayIdentity(room, overflow, extra);
            assertEquals("room full", assertInstanceOf(ControlMessage.JoinRejected.class,
                    overflow.last()).reason(), "the room actor still owns final capacity enforcement");
            assertEquals(2, room.playerCount());
            assertNull(host.closed);
            f.relays.hostLeft(roomId);
        }
    }

    private static void proveRelayIdentity(RoomHost room, Connection connection, PlayerIdentity identity)
            throws Exception {
        ControlMessage.RoomJoinResult route = connection.received.stream()
                .filter(ControlMessage.RoomJoinResult.class::isInstance)
                .map(ControlMessage.RoomJoinResult.class::cast)
                .reduce((earlier, later) -> later).orElse(null);
        ClientHandshake handshake = new ClientHandshake(identity, "Peer", "", FP, null,
                HandshakeTransport.role(connection),
                () -> AuthenticationTranscript.LOCAL_PLAINTEXT,
                route == null ? null : route.grantReference(),
                route == null ? 0 : route.routeGeneration());
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(
                assertInstanceOf(ControlMessage.Welcome.class, connection.last()))));
    }

    @Test
    void refusedTunnelAttachConsumesGrantBeforeRetry(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            Connection host = new Connection();
            String room = f.create(host, f.admit(host, owner), "DIRECT");
            Connection guest = new Connection();
            String token = f.admit(guest, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
            f.send(guest, token, new ControlMessage.RoomJoinRequest(room));
            SessionRegistry.RegistrationLease registration =
                    f.registry.find(room).orElseThrow().lease();
            f.tunnels.unregisterHost(room, registration);
            f.attach(guest, token, room);
            assertEquals("relay unavailable", assertInstanceOf(
                    ControlMessage.RoomJoinRejected.class, guest.last()).reason());
            f.tunnels.registerHost(room, registration);
            f.attach(guest, token, room);
            assertEquals("join not granted", assertInstanceOf(
                    ControlMessage.RoomJoinRejected.class, guest.last()).reason());
            assertEquals(0, f.tunnels.activeTunnels());
            f.send(guest, token, new ControlMessage.RoomJoinRequest(room));
            f.attach(guest, token, room);
            assertEquals(room, f.broker.takeAttachResult(guest).orElseThrow().roomId());
            f.attach(guest, token, room);
            assertTrue(f.broker.takeAttachResult(guest).isEmpty());
            assertEquals(1, f.tunnels.activeTunnels());
        }
    }

    @Test
    void seededOutOfOrderAndCredentialMutationsLeaveWitnessRoomIntact(@TempDir Path dir)
            throws Exception {
        Random random = new Random(SEED);
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            Connection witness = new Connection();
            String witnessToken = f.admit(witness, owner);
            String room = f.create(witness, witnessToken, "RELAY");
            for (Stage stage : Stage.values()) {
                for (int op = 0; op < 11; op++) {
                    String context = "seed=" + SEED + " state=" + stage + " op=" + op;
                    PlayerIdentity offender = PlayerIdentity.loadOrCreate(
                            dir.resolve("offender-" + stage + "-" + op));
                    f.broker.setAttackMode(stage == Stage.JOIN_POW);
                    if (stage == Stage.JOIN_POW) f.establish(offender);
                    Connection attack = new Connection();
                    ClientHandshake handshake = f.handshake(offender);
                    f.broker.onConnected(attack);
                    if (stage.ordinal() >= Stage.CHALLENGED.ordinal()) {
                        f.send(attack, null, handshake.hello());
                    }
                    if (stage.ordinal() >= Stage.IDENTITY_POW.ordinal()) {
                        f.send(attack, null, handshake.onWelcome(
                                assertInstanceOf(ControlMessage.Welcome.class, attack.last())));
                    }
                    String ownToken = null;
                    if (stage == Stage.ADMITTED) {
                        ControlMessage.PowChallenge challenge = assertInstanceOf(
                                ControlMessage.PowChallenge.class, attack.last());
                        f.send(attack, null, new ControlMessage.PowSolution("IDENTITY",
                                offender.creationPowNonce(challenge.difficultyBits())));
                        ownToken = assertInstanceOf(ControlMessage.JoinAccepted.class,
                                attack.last()).sessionToken();
                    }
                    List<ControlMessage> corpus = List.of(
                            new ControlMessage.AuthProof("%%%"),
                            new ControlMessage.PowSolution("JOIN", random.nextLong()),
                            new ControlMessage.RelayAttach(room, "forged", 1),
                            new ControlMessage.RoomLeave(room),
                            new ControlMessage.RoomTrackUpdate(room, 0, 1),
                            new ControlMessage.Heartbeat(room, Integer.MAX_VALUE),
                            new ControlMessage.AttemptStart(random.nextInt()),
                            new ControlMessage.RoomCreate(new ControlMessage.RoomDescriptor(
                                    "Attack", "s3k", 0, 0, "OPEN", null, 8, false), "UNKNOWN", -1, FP),
                            new ControlMessage.Hello(Integer.MAX_VALUE, "%%%", "Peer", FP, "", null, 0),
                            new ControlMessage.AuthProof(Base64.getEncoder().encodeToString(new byte[64])),
                            new ControlMessage.PowSolution(null, random.nextLong()));
                    ControlMessage payload = corpus.get(op);
                    // A witness token is valid globally but must never authorize this socket.
                    String stolen = random.nextBoolean() ? witnessToken : "wrong-token";
                    for (int strike = 0; strike < 3 && attack.closed == null; strike++) {
                        assertDoesNotThrow(() -> f.send(attack, stolen, payload), context);
                    }
                    assertNotNull(attack.closed, context);
                    assertTrue(f.broker.takeAttachResult(attack).isEmpty(), context);
                    if (ownToken != null) assertFalse(f.broker.isSessionTokenValid(ownToken), context);
                    assertEquals(1, f.registry.list(null).size(), context);
                    assertEquals(1, f.relays.roomCount(), context);
                    assertEquals(0, f.tunnels.activeTunnels(), context);
                    assertNull(witness.closed, context);
                    f.now[0] += 2_001;
                    witness.received.clear();
                    f.send(witness, witnessToken, new ControlMessage.RoomListRequest(null, 0));
                    assertEquals(room, assertInstanceOf(ControlMessage.RoomListResult.class,
                            witness.last()).rooms().getFirst().roomId(), context);
                    f.broker.onDisconnected(attack);
                }
            }
        }
    }

    @Test
    void seededOwnTokenDispatchCannotMutateAnotherOwnersRoom(@TempDir Path dir)
            throws Exception {
        Random random = new Random(SEED);
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            Connection witness = new Connection();
            String witnessToken = f.admit(witness, owner);
            String roomId = f.create(witness, witnessToken, "RELAY");
            SessionRegistry.RoomEntry originalRoom = f.registry.find(roomId).orElseThrow();
            ControlMessage.RoomDescriptor validRoom = new ControlMessage.RoomDescriptor(
                    "Attack", "s3k", 0, 0, "OPEN", null, 8, false);
            record Case(String name, ControlMessage message, boolean closes,
                        Class<? extends ControlMessage> reply) { }
            List<Case> cases = List.of(
                    new Case("attach-without-grant", new ControlMessage.RelayAttach(roomId,
                            "forged", 1),
                            false, ControlMessage.RoomJoinRejected.class),
                    new Case("leave-other-room", new ControlMessage.RoomLeave(roomId), false, null),
                    new Case("heartbeat-other-room", new ControlMessage.Heartbeat(roomId,
                            random.nextBoolean() ? 8 : 0), false, null),
                    new Case("track-other-room", new ControlMessage.RoomTrackUpdate(roomId, 0, 1),
                            true, null),
                    new Case("create-invalid-routing", new ControlMessage.RoomCreate(
                            validRoom, "UNKNOWN", 27888, FP), true, null),
                    new Case("create-excessive-capacity", new ControlMessage.RoomCreate(
                            new ControlMessage.RoomDescriptor("Attack", "s3k", 0, 0,
                                    "OPEN", null, Protocol.MAX_PLAYERS_RELAY + 1 + random.nextInt(32),
                                    false), "RELAY", 0, FP), true, null),
                    new Case("create-invalid-label", new ControlMessage.RoomCreate(
                            new ControlMessage.RoomDescriptor("bad\"" + random.nextInt(),
                                    "s3k", 0, 0, "OPEN", null, 8, false), "RELAY", 0, FP),
                            false, ControlMessage.RoomCreateRejected.class),
                    new Case("create-unknown-vote-track", new ControlMessage.RoomCreate(
                            validRoom, "RELAY", 0, FP, List.of("s3k:999:999")), false,
                            ControlMessage.RoomCreateRejected.class),
                    new Case("create-invalid-direct-port", new ControlMessage.RoomCreate(
                            validRoom, "DIRECT", random.nextBoolean() ? 0 : 65_536,
                            FP, List.of(), "ab".repeat(32)), true, null),
                    new Case("create-missing-direct-pin", new ControlMessage.RoomCreate(
                            validRoom, "DIRECT", 27888, FP), true, null),
                    new Case("room-control-at-broker", new ControlMessage.AttemptStart(random.nextInt()),
                            true, null));
            for (int op = 0; op < cases.size(); op++) {
                Case scenario = cases.get(op);
                String context = "seed=" + SEED + " state=ADMITTED_OWN_TOKEN op=" + op
                        + " case=" + scenario.name();
                PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("offender-" + op));
                // Established callers also reach room validation beyond the new-identity gate.
                f.establish(identity);
                Connection attack = new Connection();
                String token = f.admit(attack, identity);
                assertTrue(f.broker.acceptsToken(attack, token), context);
                f.send(attack, token, new ControlMessage.RoomListRequest(null, 0));
                assertInstanceOf(ControlMessage.RoomListResult.class, attack.last(), context);
                attack.received.clear();
                for (int strike = 0; strike < 3 && attack.closed == null; strike++) {
                    assertDoesNotThrow(() -> f.send(attack, token, scenario.message()), context);
                    assertEquals(originalRoom, f.registry.find(roomId).orElseThrow(), context);
                    assertEquals(1, f.registry.list(null).size(), context);
                    assertEquals(1, f.relays.roomCount(), context);
                    assertTrue(f.relayAttachments.isEmpty(), context);
                    assertTrue(f.broker.takeAttachResult(attack).isEmpty(), context);
                    assertEquals(0, f.tunnels.activeTunnels(), context);
                }
                assertEquals(scenario.closes(), attack.closed != null, context);
                assertEquals(!scenario.closes(), f.broker.isSessionTokenValid(token), context);
                if (scenario.reply() != null) {
                    assertInstanceOf(scenario.reply(), attack.last(), context);
                } else {
                    assertTrue(attack.received.isEmpty(), context);
                }
                assertTrue(attack.binary.isEmpty(), context);
                assertNull(witness.closed, context);
                f.now[0] += 2_001;
                witness.received.clear();
                f.send(witness, witnessToken, new ControlMessage.RoomListRequest(null, 0));
                assertEquals(roomId, assertInstanceOf(ControlMessage.RoomListResult.class,
                        witness.last(), context).rooms().getFirst().roomId(), context);
                if (!scenario.closes()) {
                    f.send(attack, token, new ControlMessage.RoomListRequest(null, 0));
                    assertInstanceOf(ControlMessage.RoomListResult.class, attack.last(), context);
                }
                f.broker.onDisconnected(attack);
                assertFalse(f.broker.isSessionTokenValid(token), context);
            }
        }
    }

    @Test
    void malformedRoomDescriptorIsRejectedAtWireBoundaryWithoutPublishing(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            f.establish(owner);
            Connection witness = new Connection();
            String token = f.admit(witness, owner);
            String roomId = f.create(witness, token, "RELAY");
            SessionRegistry.RoomEntry original = f.registry.find(roomId).orElseThrow();
            Connection attacker = new Connection();
            String attackToken = f.admit(attacker,
                    PlayerIdentity.loadOrCreate(dir.resolve("attacker")));
            ControlMessage.RoomCreate valid = new ControlMessage.RoomCreate(original.descriptor(),
                    "RELAY", 0, FP);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                    ControlCodec.encode(attackToken, valid));
            ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("msg")).putNull("room");
            for (int i = 0; i < 3; i++) f.broker.onText(attacker, envelope.toString());
            assertNotNull(attacker.closed);
            assertFalse(f.broker.isSessionTokenValid(attackToken));
            assertEquals(original, f.registry.find(roomId).orElseThrow());
            assertEquals(1, f.registry.list(null).size());
            assertEquals(1, f.relays.roomCount());
        }
    }

    @Test
    void disconnectedTokenCannotAuthorizeAnotherConnectionOrAnAttach(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("peer"));
            f.establish(identity);
            Connection old = new Connection();
            String stale = f.admit(old, identity);
            f.broker.onDisconnected(old);
            Connection replacement = new Connection();
            String current = f.admit(replacement, identity);
            assertNotEquals(stale, current);
            for (int i = 0; i < 3; i++) {
                f.attach(replacement, stale, "forged-room");
            }
            assertNotNull(replacement.closed);
            assertFalse(f.broker.isSessionTokenValid(stale));
            assertFalse(f.broker.isSessionTokenValid(current));
            assertTrue(f.broker.takeAttachResult(replacement).isEmpty());
            assertTrue(f.registry.list(null).isEmpty());
        }
    }

    @Test
    void seededTunnelHostSpoofingCannotWriteOrCloseAnotherHostsGuest() {
        Random random = new Random(SEED);
        GuestTunnelRouter router = new GuestTunnelRouter();
        Connection owner = new Connection();
        Connection attacker = new Connection();
        Connection guest = new Connection();
        SessionRegistry.RegistrationLease ownerRegistration =
                new SessionRegistry.RegistrationLease(owner, 1);
        SessionRegistry.RegistrationLease attackerRegistration =
                new SessionRegistry.RegistrationLease(attacker, 2);
        router.registerHost("witness-room", ownerRegistration);
        router.registerHost("attack-room", attackerRegistration);
        int guestId = router.openGuest("witness-room", guest, "witness-instance",
                "guest-fingerprint", "grant", 1, ownerRegistration).orElseThrow();
        String healthyText = ControlCodec.encode(null, new ControlMessage.Ping(1));
        byte[] healthyBinary = GhostPackets.encodeFrames(1, 0, new byte[GhostFrameCodec.BYTES]);
        for (int op = 0; op < 32; op++) {
            String context = "seed=" + SEED + " state=TUNNEL op=" + op;
            router.onHostControl(attacker, new ControlMessage.RelayGuestText(guestId, healthyText));
            router.onHostControl(attacker, new ControlMessage.RelayGuestClose(guestId, "forged"));
            router.onHostBinary(attacker, GhostPackets.encodeRelayGuestBinary(guestId, healthyBinary));
            byte[] malformed = new byte[random.nextInt(32)];
            random.nextBytes(malformed);
            assertDoesNotThrow(() -> router.onHostBinary(attacker, malformed), context);
            assertNull(guest.closed, context);
            assertTrue(guest.received.isEmpty(), context);
            assertTrue(guest.binary.isEmpty(), context);
            assertEquals(1, router.activeTunnels(), context);
        }
        router.onHostControl(owner, new ControlMessage.RelayGuestText(guestId, healthyText));
        router.onHostBinary(owner, GhostPackets.encodeRelayGuestBinary(guestId, healthyBinary));
        assertInstanceOf(ControlMessage.Ping.class, guest.last());
        assertArrayEquals(healthyBinary, guest.binary.getFirst());
        router.unregisterHost("attack-room", attackerRegistration);
        assertEquals(1, router.activeTunnels());
        assertNull(guest.closed);
        router.unregisterHost("witness-room", ownerRegistration);
        assertEquals(0, router.activeTunnels());
        assertNotNull(guest.closed);
    }
}
