package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.protocol.ControlCodec;
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
    @Test
    void browserShowsEffectiveRelayLimitAndOnlyDirectHostCanAdvertiseChanges(
            @TempDir Path hostDir, @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        String fingerprint = PlayerIdentity.loadOrCreate(hostDir).fingerprint();
        establish(fingerprint);
        var descriptor = new ControlMessage.RoomDescriptor("Communication", "s3k", 0, 0,
                "OPEN", null, 8, false, "FULL_CHAT");
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "RELAY", 0, "0.6:cafe")));
        String relayId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        assertEquals("QUICK_CHAT", registry.find(relayId).orElseThrow().descriptor().chatMode());
        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomChatModeUpdate(relayId, "FULL_CHAT")));
        assertEquals("QUICK_CHAT", registry.find(relayId).orElseThrow().descriptor().chatMode());

        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String directId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        activateDirect(host, token, directId);
        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomChatModeUpdate(directId, "OFF")));
        assertEquals("FULL_CHAT", registry.find(directId).orElseThrow().descriptor().chatMode());
        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomChatModeUpdate(directId, "OFF")));
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomListRequest(null, 0)));
        var listed = ((ControlMessage.RoomListResult) lastMessage(host)).rooms();
        assertEquals("OFF", listed.stream().filter(room -> room.roomId().equals(directId))
                .findFirst().orElseThrow().chatMode());
        assertEquals("QUICK_CHAT", listed.stream().filter(room -> room.roomId().equals(relayId))
                .findFirst().orElseThrow().chatMode());
    }

    private static final String DIRECT_CERT_PIN = "ab".repeat(32);

    @Test
    void directRoomIsNotJoinableDuringHostBridgeInstallation(
            @TempDir Path hostDir, @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("Pending", "s3k", 0, 0,
                        "OPEN", null, 8, false), "DIRECT", 27888, "0.6:cafe",
                List.of(), DIRECT_CERT_PIN, "pending-instance", 0, null)));
        String roomId = assertInstanceOf(ControlMessage.RoomCreated.class, lastMessage(host)).roomId();
        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        assertTrue(registry.listed(null).isEmpty(), "pending direct room was listed");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomListRequest(null, 0)));
        assertTrue(assertInstanceOf(ControlMessage.RoomListResult.class,
                lastMessage(guest)).rooms().isEmpty());
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertInstanceOf(ControlMessage.RoomJoinRejected.class, lastMessage(guest));
        assertTrue(tunnels.registered.isEmpty());
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomActivate(roomId, "pending-instance"), 11));
        assertTrue(!assertInstanceOf(ControlMessage.RoomActivated.class, lastMessage(guest)).active());
        broker.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoomActivate(roomId, "pending-instance"), 12));
        assertTrue(assertInstanceOf(ControlMessage.RoomActivated.class, lastMessage(host)).active());
        assertEquals(1, registry.listed(null).size());
        assertEquals(List.of(roomId), tunnels.registered);
        now += 10_000;
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomListRequest(null, 0)));
        assertEquals(roomId, assertInstanceOf(ControlMessage.RoomListResult.class,
                lastMessage(guest)).rooms().getFirst().roomId());
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertInstanceOf(ControlMessage.RoomJoinResult.class, lastMessage(guest));
    }

    @Test
    void directRoomOwnerCannotTunnelThroughItsOwnMasterConnection(@TempDir Path hostDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("Listed", "s3k", 0, 0,
                        "OPEN", null, 8, false), "DIRECT", 27888, "0.6:cafe",
                List.of(), DIRECT_CERT_PIN, "owner-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        activateDirect(host, token, roomId);
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomJoinRequest(roomId)));
        ControlMessage.RoomJoinResult prepared = assertInstanceOf(
                ControlMessage.RoomJoinResult.class, lastMessage(host));

        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RelayAttach(roomId,
                prepared.grantReference(), prepared.routeGeneration())));
        assertTrue(tunnels.opened.isEmpty(), "the host link cannot also be its own tunnel guest");
        assertTrue(broker.takeAttachResult(host).isEmpty());
        assertInstanceOf(ControlMessage.RoomJoinRejected.class, lastMessage(host));
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomListRequest(null, 0)));
        assertInstanceOf(ControlMessage.RoomListResult.class, lastMessage(host));
    }

    @Test
    void directGrantIsRedeemedOnlyByCurrentHostForProvedGuest(
            @TempDir Path hostDir, @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("Listed", "s3k", 0, 0,
                        "OPEN", null, 8, false), "DIRECT", 27888, "0.6:cafe",
                List.of(), DIRECT_CERT_PIN, "instance-1", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        activateDirect(host, hostToken, roomId);
        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        String guestFingerprint = PlayerIdentity.loadOrCreate(guestDir).fingerprint();
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        ControlMessage.RoomJoinResult prepared = assertInstanceOf(
                ControlMessage.RoomJoinResult.class, lastMessage(guest));
        assertNotNull(prepared.grantReference());
        assertTrue(prepared.routeGeneration() > 0);

        ControlMessage.RoomGrantRedeem redeem = new ControlMessage.RoomGrantRedeem(roomId,
                prepared.instanceId(),
                prepared.grantReference(), prepared.routeGeneration(), guestFingerprint);
        broker.onText(guest, ControlCodec.encode(guestToken, redeem, 10));
        assertTrue(!assertInstanceOf(ControlMessage.RoomGrantDecision.class,
                lastMessage(guest)).allowed());
        broker.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoomGrantRedeem(roomId, prepared.instanceId(),
                        prepared.grantReference(),
                        prepared.routeGeneration(), "other"), 11));
        assertTrue(!assertInstanceOf(ControlMessage.RoomGrantDecision.class,
                lastMessage(host)).allowed());
        broker.onText(host, ControlCodec.encode(hostToken, redeem, 12));
        assertTrue(assertInstanceOf(ControlMessage.RoomGrantDecision.class,
                lastMessage(host)).allowed());
        broker.onText(host, ControlCodec.encode(hostToken, redeem, 13));
        assertTrue(!assertInstanceOf(ControlMessage.RoomGrantDecision.class,
                lastMessage(host)).allowed());

        int guestMessagesBeforeRetire = guest.text.size();
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomRouteRetire(roomId, prepared.instanceId(),
                        prepared.grantReference(), prepared.routeGeneration()), 20));
        ControlMessage.RoomRouteRetireNotice notice = assertInstanceOf(
                ControlMessage.RoomRouteRetireNotice.class, lastMessage(host));
        assertEquals(guestMessagesBeforeRetire, guest.text.size(),
                "guest must wait for the room owner's retirement receipt");
        ControlMessage.RoomRouteRetireAck ack = new ControlMessage.RoomRouteRetireAck(
                roomId, prepared.instanceId(), prepared.grantReference(),
                prepared.routeGeneration(), notice.retirementId());
        broker.onText(guest, ControlCodec.encode(guestToken, ack));
        assertEquals(guestMessagesBeforeRetire, guest.text.size());
        broker.onText(host, ControlCodec.encode(hostToken, ack));
        ControlMessage.RoomRouteRenewed renewed = assertInstanceOf(
                ControlMessage.RoomRouteRenewed.class, lastMessage(guest));
        assertTrue(renewed.join().routeGeneration() > prepared.routeGeneration());
        assertTrue(!renewed.join().grantReference().equals(prepared.grantReference()));
    }
    static final class FakeConnection implements HubConnection, com.openggf.net.hub.HandshakeTransport {
        @Override public String authenticationBinding() { return com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT; }
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
        final List<String> removed = new ArrayList<>();

        @Override public String createRelayRoom(SessionRegistry.RoomEntry entry) {
            created.add(entry.roomId());
            return entry.roomId();
        }
        @Override public void noteGuestTier(String fingerprint, boolean isNew) { }
        @Override public boolean attach(HubConnection connection, String roomId,
                                        String fingerprint, String displayName,
                                        String instanceId, String grantReference, long routeGeneration) {
            attached.add(roomId + ":" + fingerprint);
            return true;
        }
        @Override public void hostLeft(String roomId) { removed.add(roomId); }
    }

    static final class FakeTunnels implements RoomBroker.DirectTunnelDirectory {
        final List<String> registered = new ArrayList<>();
        final List<String> opened = new ArrayList<>();

        @Override public void registerHost(String roomId,
                                           SessionRegistry.RegistrationLease registration) {
            registered.add(roomId);
        }
        @Override public OptionalInt openGuest(String roomId, HubConnection guestConnection,
                String instanceId, String fingerprint, String grantReference,
                long routeGeneration, SessionRegistry.RegistrationLease registration) {
            opened.add(roomId);
            return OptionalInt.of(7);
        }
        @Override public void unregisterHost(String roomId,
                                             SessionRegistry.RegistrationLease registration) {
            registered.remove(roomId);
        }
    }

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomBroker broker;
    private FakeRelays relays;
    private SqliteIdentityStore store;
    private TrustLadder ladder;
    private FakeTunnels tunnels;
    private SessionRegistry registry;
    private VerifierRegistry verifiers;

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
        verifiers = new VerifierRegistry(() -> now, 60_000);
        broker = new RoomBroker(PlayerIdentity.loadOrCreate(dir.resolve("master")), config,
                registry, store, ladder, cache, () -> now, relays, tunnels,
                java.util.Set.of("s3k:0:0", "s3k:0:1")::contains, verifiers);
    }

    @AfterEach
    void closeStore() {
        store.close();
    }

    private static ControlMessage lastMessage(FakeConnection connection) {
        return ControlCodec.decode(connection.text.getLast()).message();
    }

    private void activateDirect(FakeConnection owner, String token, String roomId) {
        String instanceId = registry.find(roomId).orElseThrow().instanceId();
        broker.onText(owner, ControlCodec.encode(token,
                new ControlMessage.RoomActivate(roomId, instanceId)));
        assertTrue(assertInstanceOf(ControlMessage.RoomActivated.class,
                lastMessage(owner)).active());
    }

    private String admit(FakeConnection connection, Path identityDir, String name)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        ClientHandshake handshake = new ClientHandshake(identity, name, "0.6:cafe", null,
                com.openggf.net.protocol.AuthenticationTranscript.Role.MASTER,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
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

    private void trust(String fingerprint) {
        store.establishForTest(fingerprint,
                now - TrustLadder.Thresholds.defaults().trustedAgeMillis() - 1,
                now, TrustLadder.Thresholds.defaults().trustedCleanRounds());
        assertEquals(TrustLadder.Tier.TRUSTED, ladder.tierOf(fingerprint));
    }

    @Test
    void unknownIdentityMustSolveCreationPow(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        assertNotNull(admit(connection, idDir, "A"));
        assertTrue(connection.text.stream().map(text -> ControlCodec.decode(text).message())
                .anyMatch(ControlMessage.PowChallenge.class::isInstance));
    }

    @Test
    void blankDisplayNameUsesFallbackWithoutPersistingIdentity(@TempDir Path idDir)
            throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(idDir);
        FakeConnection connection = new FakeConnection();
        assertNotNull(admit(connection, idDir, ""));
        assertTrue(store.find(identity.fingerprint()).isEmpty());
    }

    @Test
    void newTierCannotCreateEstablishedCan(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        String token = admit(connection, idDir, "A");
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Room", "s3k", 0, 0, "OPEN", null, 8, false);
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe",
                        List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        assertInstanceOf(ControlMessage.RoomCreateRejected.class, lastMessage(connection));

        establish(PlayerIdentity.loadOrCreate(idDir).fingerprint());
        broker.onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe",
                        List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        assertInstanceOf(ControlMessage.RoomCreated.class, lastMessage(connection));
        activateDirect(connection, token,
                ((ControlMessage.RoomCreated) lastMessage(connection)).roomId());
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
        ControlMessage.RoomJoinResult prepared =
                (ControlMessage.RoomJoinResult) lastMessage(guest);
        assertEquals("RELAY", prepared.routing());
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RelayAttach(roomId,
                        prepared.grantReference(), prepared.routeGeneration())));
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
                "DIRECT", 27888, "0.6:AAAA", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        activateDirect(host, hostToken, roomId);

        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertInstanceOf(ControlMessage.RoomJoinRejected.class, lastMessage(guest));
    }

    @Test
    void directRoomRequiresAndForwardsPinnedTlsCertificate(
            @TempDir Path hostDir, @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "R", "s3k", 0, 0, "OPEN", null, 8, false);
        assertThrows(com.openggf.net.protocol.ProtocolViolationException.class,
                () -> new com.openggf.net.protocol.ControlJsonCodec().decodeMasterClient(
                        ControlCodec.encode(hostToken,
                                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888,
                                        "0.6:cafe")),
                        com.openggf.net.protocol.Protocol.MAX_MASTER_FRAME_BYTES));

        broker.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoomCreate(descriptor, "DIRECT", 27888, "0.6:cafe",
                        List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        activateDirect(host, hostToken, roomId);
        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        ControlMessage.RoomJoinResult result = assertInstanceOf(
                ControlMessage.RoomJoinResult.class, lastMessage(guest));
        assertEquals(DIRECT_CERT_PIN, result.certificateSha256());
        assertEquals(PlayerIdentity.loadOrCreate(hostDir).fingerprint(),
                result.hostServerId());
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
    void liveSanctionRevokesTokenAndClosesHostedRoom(@TempDir Path hostDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        String fingerprint = PlayerIdentity.loadOrCreate(hostDir).fingerprint();
        establish(fingerprint);
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        ladder.sanction(new IdentityStore.SanctionRecord(fingerprint, "BAN", "cheat",
                "operator", now, Long.MAX_VALUE));
        broker.revokeSanctioned(fingerprint);

        assertNotNull(host.closedReason);
        assertTrue(broker.fingerprintForSessionToken(token).isEmpty());
        assertTrue(registry.find(roomId).isEmpty());
        assertTrue(tunnels.registered.isEmpty());
    }

    @Test
    void timeoutClosesAlreadyAttachedRelayGuest(@TempDir Path hostDir,
                                                 @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "RELAY", 0, "0.6:cafe")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        ControlMessage.RoomJoinResult prepared =
                (ControlMessage.RoomJoinResult) lastMessage(guest);
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RelayAttach(roomId,
                        prepared.grantReference(), prepared.routeGeneration())));

        String guestFingerprint = PlayerIdentity.loadOrCreate(guestDir).fingerprint();
        ladder.sanction(new IdentityStore.SanctionRecord(guestFingerprint, "TIMEOUT",
                "spam", "operator", now, now + 10_000));
        broker.revokeSanctioned(guestFingerprint);

        assertNotNull(guest.closedReason);
        assertTrue(broker.fingerprintForSessionToken(guestToken).isEmpty());
        assertTrue(registry.find(roomId).isPresent());
    }

    @Test
    void hostDisconnectRemovesTheirRooms(@TempDir Path hostDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        broker.onDisconnected(host);
        assertTrue(tunnels.registered.isEmpty());

        FakeConnection browser = new FakeConnection();
        String browserToken = admit(browser, hostDir.resolve("other"), "B");
        broker.onText(browser, ControlCodec.encode(browserToken,
                new ControlMessage.RoomListRequest(null, 0)));
        assertTrue(((ControlMessage.RoomListResult) lastMessage(browser)).rooms().isEmpty());
    }

    @Test
    void unrelatedConnectionOfTheSameIdentityCannotDeleteARegistration(@TempDir Path hostDir)
            throws Exception {
        FakeConnection owner = new FakeConnection();
        String ownerToken = admit(owner, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(owner, ControlCodec.encode(ownerToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(owner)).roomId();
        activateDirect(owner, ownerToken, roomId);
        FakeConnection unrelated = new FakeConnection();
        String unrelatedToken = admit(unrelated, hostDir, "HOST");

        broker.onText(unrelated, ControlCodec.encode(unrelatedToken, new ControlMessage.RoomLeave(roomId)));
        broker.onDisconnected(unrelated);

        assertTrue(registry.find(roomId).isPresent());
        assertEquals(List.of(roomId), tunnels.registered);
        broker.onDisconnected(owner);
        assertTrue(registry.find(roomId).isEmpty());
        assertTrue(tunnels.registered.isEmpty());
    }

    @Test
    void unrelatedSameIdentityCannotHeartbeatOrChangeTrackOrChat(@TempDir Path hostDir) throws Exception {
        FakeConnection owner = new FakeConnection();
        String ownerToken = admit(owner, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(owner, ControlCodec.encode(ownerToken, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(owner)).roomId();
        SessionRegistry.RoomEntry original = registry.find(roomId).orElseThrow();
        FakeConnection unrelated = new FakeConnection();
        String unrelatedToken = admit(unrelated, hostDir, "HOST");
        now += 1_000;
        broker.onText(unrelated, ControlCodec.encode(unrelatedToken, new ControlMessage.Heartbeat(roomId, 8)));
        broker.onText(unrelated, ControlCodec.encode(unrelatedToken, new ControlMessage.RoomChatModeUpdate(roomId, "OFF")));
        broker.onText(unrelated, ControlCodec.encode(unrelatedToken, new ControlMessage.RoomTrackUpdate(roomId, 0, 1)));
        assertEquals(original, registry.find(roomId).orElseThrow());
    }

    @Test
    void rateLimitedRoomListStillReceivesOneReply(@TempDir Path identityDir)
            throws Exception {
        FakeConnection browser = new FakeConnection();
        String token = admit(browser, identityDir, "BROWSER");
        broker.onText(browser, ControlCodec.encode(token,
                new ControlMessage.RoomListRequest(null, 0)));
        int before = browser.text.size();

        broker.onText(browser, ControlCodec.encode(token,
                new ControlMessage.RoomListRequest(null, 1)));

        assertEquals(before + 1, browser.text.size());
        ControlMessage.RoomListResult limited = assertInstanceOf(
                ControlMessage.RoomListResult.class, lastMessage(browser));
        assertEquals(1, limited.page());
        assertEquals(-1, limited.totalPages());
        assertTrue(limited.rooms().isEmpty());
    }

    @Test
    void invalidRoomCreateIsRejectedAtDecode(@TempDir Path identityDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, identityDir, "HOST");
        int before = host.text.size();

        assertThrows(com.openggf.net.protocol.ProtocolViolationException.class,
                () -> new com.openggf.net.protocol.ControlJsonCodec().decodeMasterClient(
                        ControlCodec.encode(token, new ControlMessage.RoomCreate(
                                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0,
                                        "OPEN", null, 8, false), "INVALID", 0, "0.6:cafe")),
                        com.openggf.net.protocol.Protocol.MAX_MASTER_FRAME_BYTES));
        assertEquals(before, host.text.size());
    }

    @Test
    void strikeLimitRemovesRelayRoomBeforeDisconnectCallback(@TempDir Path hostDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("R", "s3k", 0, 0, "OPEN", null, 8, false),
                "RELAY", 0, "0.6:cafe")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        for (int i = 0; i < 3; i++) {
            broker.onText(host, ControlCodec.encode(token, new ControlMessage.Chat("illegal")));
        }
        assertEquals(List.of(roomId), relays.removed);
        assertTrue(registry.find(roomId).isEmpty());
        assertTrue(broker.fingerprintForSessionToken(token).isEmpty());
        broker.onDisconnected(host);
        assertEquals(List.of(roomId), relays.removed);
    }

    @Test
    void rejectsRoomDescriptorFieldsThatCouldPoisonBrowserFrames(@TempDir Path hostDir)
            throws Exception {
        FakeConnection host = new FakeConnection();
        String token = admit(host, hostDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(hostDir).fingerprint());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                new ControlMessage.RoomDescriptor("x".repeat(65), "s3k", 0, 0,
                        "OPEN", null, 8, false), "RELAY", 0, "0.6:cafe")));
        assertInstanceOf(ControlMessage.RoomCreateRejected.class, lastMessage(host));
        assertTrue(registry.list(null).isEmpty());
    }

    @Test
    void directRoomsAreCappedAtEightPlayers(@TempDir Path idDir) throws Exception {
        FakeConnection connection = new FakeConnection();
        String token = admit(connection, idDir, "HOST");
        establish(PlayerIdentity.loadOrCreate(idDir).fingerprint());
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Huge", "s3k", 0, 0, "OPEN", null, 64, false);
        assertThrows(com.openggf.net.protocol.ProtocolViolationException.class,
                () -> new com.openggf.net.protocol.ControlJsonCodec().decodeMasterClient(
                        ControlCodec.encode(token, new ControlMessage.RoomCreate(descriptor,
                                "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN,
                                "fixture-instance", 0, null)),
                        com.openggf.net.protocol.Protocol.MAX_MASTER_FRAME_BYTES));
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
                new ControlMessage.RelayAttach(roomId, "ungranted", 1)));
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
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of("s2:0:0"),
                DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        assertEquals("invalid vote track pool: s2:0:0 is not a s3k act",
                ((ControlMessage.RoomCreateRejected) lastMessage(host)).reason());
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of("s3k:0:1", "s3k:12:0"),
                DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        assertEquals("invalid vote track pool: s3k:12:0 is not an eligible Time Attack act",
                ((ControlMessage.RoomCreateRejected) lastMessage(host)).reason());
        assertThrows(com.openggf.net.protocol.ProtocolViolationException.class,
                () -> new com.openggf.net.protocol.ControlJsonCodec().decodeMasterClient(
                        ControlCodec.encode(token, new ControlMessage.RoomCreate(
                                descriptor, "DIRECT", 27888, "0.6:cafe",
                                List.of("s3k:0:1", "s3k:0:1"), DIRECT_CERT_PIN,
                                "fixture-instance", 0, null)),
                        com.openggf.net.protocol.Protocol.MAX_MASTER_FRAME_BYTES));
        broker.onText(host, ControlCodec.encode(token, new ControlMessage.RoomCreate(
                descriptor, "DIRECT", 27888, "0.6:cafe", List.of("s3k:0:1"),
                DIRECT_CERT_PIN, "fixture-instance", 0, null)));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();
        assertEquals(List.of("s3k:0:1"), registry.find(roomId).orElseThrow().voteTrackKeys());

        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomTrackUpdate(roomId, 99, 99)));
        assertEquals(0, registry.find(roomId).orElseThrow().descriptor().zone());
        broker.onText(host, ControlCodec.encode(token,
                new ControlMessage.RoomTrackUpdate(roomId, 0, 1)));
        assertEquals(1, registry.find(roomId).orElseThrow().descriptor().act());
    }

    @Test
    void verifiedRoomsRequireRelayTrustedPlayersAndAvailableVerifier(
            @TempDir Path hostDir, @TempDir Path guestDir) throws Exception {
        FakeConnection host = new FakeConnection();
        String hostToken = admit(host, hostDir, "HOST");
        String hostFingerprint = PlayerIdentity.loadOrCreate(hostDir).fingerprint();
        ControlMessage.RoomDescriptor verified = new ControlMessage.RoomDescriptor(
                "Verified", "s3k", 0, 0, "OPEN", null, 8, true);

        establish(hostFingerprint);
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                verified, "DIRECT", 27888, "0.6:cafe", List.of(), DIRECT_CERT_PIN,
                "fixture-instance", 0, null)));
        assertEquals("verified rooms are relay-only",
                ((ControlMessage.RoomCreateRejected) lastMessage(host)).reason());
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                verified, "RELAY", 0, "0.6:cafe")));
        assertEquals("verified rooms require TRUSTED",
                ((ControlMessage.RoomCreateRejected) lastMessage(host)).reason());

        trust(hostFingerprint);
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                verified, "RELAY", 0, "0.6:cafe")));
        assertEquals("no verifier available for this build",
                ((ControlMessage.RoomCreateRejected) lastMessage(host)).reason());
        PlayerIdentity worker = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
        verifiers.register(worker.publicKeyEncoded(), java.util.Set.of("0.6:cafe"));
        broker.onText(host, ControlCodec.encode(hostToken, new ControlMessage.RoomCreate(
                verified, "RELAY", 0, "0.6:cafe")));
        String roomId = ((ControlMessage.RoomCreated) lastMessage(host)).roomId();

        FakeConnection guest = new FakeConnection();
        String guestToken = admit(guest, guestDir, "GUEST");
        broker.onText(guest, ControlCodec.encode(guestToken,
                new ControlMessage.RoomJoinRequest(roomId)));
        assertEquals("verified rooms require TRUSTED",
                ((ControlMessage.RoomJoinRejected) lastMessage(guest)).reason());
    }
}
