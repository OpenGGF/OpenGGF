package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.HandshakeTransport;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TestPrivateRoomBroker {
    private static final String BUILD = "private:test-build";
    private static final RoomAccessPolicy PRIVATE = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true);
    private static final String PASSWORD = "a bounded private room password";

    static final class Connection implements HubConnection, HandshakeTransport {
        final List<ControlCodec.DecodedControl> received = new ArrayList<>();
        final String address;
        String closed;
        String token;

        Connection(String address) { this.address = address; }
        @Override public String remoteHost() { return address; }
        @Override public String authenticationBinding() { return AuthenticationTranscript.LOCAL_PLAINTEXT; }
        @Override public void sendText(String text) { received.add(ControlCodec.decode(text)); }
        @Override public void sendBinary(byte[] bytes) { fail("unexpected binary publication"); }
        @Override public void close(String reason) { closed = reason; }
        ControlMessage last() { return received.getLast().message(); }
    }

    static final class Relays implements RoomBroker.RelayRoomDirectory {
        final List<CompletableFuture<PreparedRoom>> pending = new ArrayList<>();
        final List<SessionRegistry.RoomEntry> entries = new ArrayList<>();
        final List<String> published = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final List<RoomInviteService> services = new ArrayList<>();
        boolean hold;
        int discarded;

        @Override public String createRelayRoom(SessionRegistry.RoomEntry entry) {
            published.add(entry.roomId());
            return entry.roomId();
        }
        @Override public CompletableFuture<PreparedRoom> prepareRelayRoom(
                SessionRegistry.RoomEntry entry, RoomPassword password) {
            CompletableFuture<PreparedRoom> result = new CompletableFuture<>();
            pending.add(result);
            entries.add(entry);
            if (!hold) result.complete(prepared(entry));
            return result;
        }
        void complete(int index) { pending.get(index).complete(prepared(entries.get(index))); }
        PreparedRoom prepared(SessionRegistry.RoomEntry entry) {
            return new PreparedRoom() {
                @Override public void publish(RoomInviteService service) {
                    services.add(service);
                    published.add(entry.roomId());
                }
                @Override public void close() { discarded++; }
            };
        }
        @Override public void noteGuestTier(String fingerprint, boolean isNew) { }
        @Override public boolean attach(HubConnection connection, String roomId,
                                        String fingerprint, String displayName) { return true; }
        @Override public void hostLeft(String roomId) { removed.add(roomId); }
    }

    static final class Fixture implements AutoCloseable {
        final long[] now = {1_000_000};
        final Path directory;
        final SqliteIdentityStore store;
        final SessionRegistry registry;
        final GuestTunnelRouter tunnels = new GuestTunnelRouter();
        final Relays relays = new Relays();
        final RoomBroker broker;
        final MasterConfig config = new MasterConfig(0, null, null, true, "ids.db", 0, "test",
                0, 0, 0, 0, 8, 8, false, 8, 16, 30, 1, 0, 0, 0);
        final TrustLadder ladder;

        Fixture(Path directory) throws Exception {
            this.directory = directory;
            store = new SqliteIdentityStore(directory.resolve("ids.db"));
            NewIdentityCache cache = new NewIdentityCache(100, 3_600_000, () -> now[0]);
            ladder = new TrustLadder(store, cache, config.thresholds(), () -> now[0]);
            registry = new SessionRegistry(() -> now[0], config);
            broker = new RoomBroker(PlayerIdentity.loadOrCreate(directory.resolve("master")),
                    config, registry, store, ladder, cache, () -> now[0], relays, tunnels);
            tunnels.setAccessAdmission(broker::allowRoomText);
        }

        Connection admit(String name, String address) throws Exception { return admit(name, address, BUILD); }

        Connection admit(String name, String address, String build) throws Exception {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory.resolve(name));
            store.establishForTest(identity.fingerprint(), now[0] - config.thresholds().establishedAgeMillis() - 1,
                    now[0], config.thresholds().establishedCleanRounds());
            Connection connection = new Connection(address);
            ClientHandshake handshake = new ClientHandshake(identity, name, build, null,
                    AuthenticationTranscript.Role.MASTER, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
            broker.onConnected(connection);
            broker.onText(connection, ControlCodec.encode(null, handshake.hello()));
            broker.onText(connection, ControlCodec.encode(null, handshake.onWelcome(
                    assertInstanceOf(ControlMessage.Welcome.class, connection.last()))));
            connection.token = assertInstanceOf(ControlMessage.JoinAccepted.class, connection.last()).sessionToken();
            return connection;
        }

        void send(Connection connection, ControlMessage message, long requestId) {
            broker.onText(connection, ControlCodec.encode(connection.token, message, requestId));
        }

        ControlMessage.RoomCreate creation(RoomAccessPolicy policy, String routing, String instance) {
            return new ControlMessage.RoomCreate(new ControlMessage.RoomDescriptor("Room", "s3k", 0, 0,
                    "OPEN", null, "DIRECT".equals(routing) ? 8 : 256, false, policy), routing,
                    "DIRECT".equals(routing) ? 27888 : 0, BUILD, List.of(), "ab".repeat(32), instance, 0,
                    "RELAY".equals(routing) && policy.passwordRequired() ? RoomPassword.of(PASSWORD) : null);
        }

        String create(Connection owner, RoomAccessPolicy policy, long requestId) {
            send(owner, creation(policy, "DIRECT", "instance-" + requestId), requestId);
            return assertInstanceOf(ControlMessage.RoomCreated.class, owner.last()).roomId();
        }

        void control(Connection owner, String roomId, ControlMessage.InviteOperation operation,
                     boolean sharing, long requestId) {
            SessionRegistry.RoomEntry room = registry.find(roomId).orElseThrow();
            send(owner, new ControlMessage.RoomInviteControl(roomId, room.instanceId(), room.accessEpoch(),
                    room.inviteEpoch(), operation, sharing), requestId);
        }

        ControlMessage.RoomInvite invite(Connection owner, String roomId) {
            control(owner, roomId, ControlMessage.InviteOperation.FETCH, true, 90);
            return assertInstanceOf(ControlMessage.RoomInviteControlResult.class, owner.last()).invite();
        }

        @Override public void close() { store.close(); }
    }

    @Test
    void browserFiltersPrivateBeforePaginationAndInternalExpiryStillFindsThem(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String visible = f.create(owner, RoomAccessPolicy.PUBLIC, 1);
            f.create(owner, PRIVATE, 2);
            f.create(owner, PRIVATE, 3);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomListRequest(null, 0), 40);
            ControlMessage.RoomListResult page = assertInstanceOf(ControlMessage.RoomListResult.class, guest.last());
            assertEquals(List.of(visible), page.rooms().stream().map(ControlMessage.RoomSummary::roomId).toList());
            assertEquals(1, page.totalPages());
            assertEquals(3, f.registry.list(null).size());
            f.now[0] += 2_001;
            f.send(guest, new ControlMessage.RoomListRequest(null, 1), 41);
            page = assertInstanceOf(ControlMessage.RoomListResult.class, guest.last());
            assertTrue(page.rooms().isEmpty());
            assertEquals(1, page.totalPages());
            f.now[0] += 30_001;
            f.broker.tick();
            assertTrue(f.registry.list(null).isEmpty());
        }
    }

    @Test
    void rawPrivateIdDisclosesNoMetadataAndGrantsNoTunnelToUnrelatedIdentity(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomJoinRequest(roomId), 10);
            ControlMessage rejection = guest.last();
            f.send(guest, new ControlMessage.RoomJoinRequest("r-99999"), 11);
            assertEquals(rejection, guest.last());
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, rejection);
            f.send(guest, new ControlMessage.RelayAttach(roomId), 12);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, guest.last());
            assertEquals(0, f.tunnels.activeTunnels());
        }
    }

    @Test
    void publicLegacyRegistrationKeepsUnboundJoinMetadataAndCanStillAttach(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(RoomAccessPolicy.PUBLIC, "DIRECT", null), 1);
            ControlMessage.RoomCreated created = assertInstanceOf(ControlMessage.RoomCreated.class, owner.last());
            assertNull(created.instanceId());
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomJoinRequest(created.roomId()), 10);
            ControlMessage.RoomJoinResult result = assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
            assertNull(result.instanceId());
            assertEquals(RoomAccessPolicy.PUBLIC, result.accessPolicy());
            f.send(guest, new ControlMessage.RelayAttach(created.roomId()), 11);
            assertEquals(1, f.tunnels.activeTunnels());
        }
    }

    @Test
    void inviteResolutionReturnsExactPolicyAndBuildChecksStillApply(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite invite = f.invite(owner, roomId);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(invite.code()), 11);
            ControlMessage.RoomJoinResult result = assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
            assertEquals(roomId, result.roomId());
            assertEquals(PRIVATE, result.accessPolicy());
            assertEquals(invite.instanceId(), result.instanceId());
            assertEquals(invite.inviteEpoch(), result.inviteEpoch());
            assertEquals(invite.expiresAtMillis(), result.inviteExpiresAtMillis());
            assertEquals("192.0.2.1", result.directHost());
            assertEquals(11, guest.received.getLast().requestId());
            Connection otherBuild = f.admit("Other", "192.0.2.3", "different-build");
            f.send(otherBuild, new ControlMessage.RoomInviteResolve(invite.code()), 12);
            assertEquals("determinism fingerprint mismatch",
                    assertInstanceOf(ControlMessage.RoomJoinRejected.class, otherBuild.last()).reason());
        }
    }

    @Test
    void malformedAndSuccessfulResolvesShareIdentityAndNatBudgetsAcrossReconnects(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String code = f.invite(owner, f.create(owner, PRIVATE, 1)).code();
            Connection guest = f.admit("Guest", "192.0.2.2");
            for (int i = 0; i < 4; i++) f.send(guest, new ControlMessage.RoomInviteResolve("?"), 10 + i);
            f.send(guest, new ControlMessage.RoomInviteResolve(code), 14);
            assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
            f.broker.onDisconnected(guest);
            Connection reconnected = f.admit("Guest", "192.0.2.3");
            f.send(reconnected, new ControlMessage.RoomInviteResolve(code), 15);
            assertEquals(60_000, assertInstanceOf(ControlMessage.RoomInviteRejected.class,
                    reconnected.last()).retryAfterMillis());
            Connection natPeer = f.admit("Peer", "192.0.2.2");
            f.send(natPeer, new ControlMessage.RoomInviteResolve("?"), 16);
            assertEquals(reconnected.last(), natPeer.last());
        }
    }

    @Test
    void attachGrantCannotOutliveInviteExpiryOrBeReused(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite invite = f.invite(owner, roomId);
            f.now[0] += 599_995;
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(invite.code()), 10);
            assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
            f.now[0] += 5;
            f.send(guest, new ControlMessage.RelayAttach(roomId), 11);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, guest.last());
            assertEquals(0, f.tunnels.activeTunnels());
            ControlMessage.RoomInvite renewed = f.invite(owner, roomId);
            f.send(guest, new ControlMessage.RoomInviteResolve(renewed.code()), 12);
            f.send(guest, new ControlMessage.RelayAttach(roomId), 13);
            assertEquals(1, f.tunnels.activeTunnels());
        }
    }

    @Test
    void freshResolutionAfterThirtySecondsKeepsTheSameInstanceAndAccessEpoch(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            String code = f.invite(owner, roomId).code();
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(code), 10);
            ControlMessage.RoomJoinResult initial = assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last());
            f.now[0] += 30_001;
            f.send(guest, new ControlMessage.RelayAttach(roomId), 11);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, guest.last());
            f.send(guest, new ControlMessage.RoomInviteResolve(code), 12);
            assertEquals(initial, guest.last());
            f.send(guest, new ControlMessage.RelayAttach(roomId), 13);
            assertEquals(1, f.tunnels.activeTunnels());
        }
    }

    @Test
    void disablingSharingRotatesAtomicallyAndInvalidatesUnusedGrants(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite old = f.invite(owner, roomId);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(old.code()), 10);
            f.control(owner, roomId, ControlMessage.InviteOperation.SET_SHARING, false, 21);
            ControlMessage.RoomInviteControlResult changed = assertInstanceOf(
                    ControlMessage.RoomInviteControlResult.class, owner.last());
            assertFalse(changed.accessPolicy().membersCanInvite());
            assertNotEquals(old.code(), changed.invite().code());
            assertTrue(changed.invite().inviteEpoch() > old.inviteEpoch());
            f.send(guest, new ControlMessage.RelayAttach(roomId), 11);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, guest.last());
            f.send(guest, new ControlMessage.RoomInviteResolve(old.code()), 12);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, guest.last());
            assertFalse(f.registry.find(roomId).orElseThrow().descriptor().accessPolicy().membersCanInvite());
        }
    }

    @Test
    void failedRotationKeepsPolicyAndCurrentCodeRatherThanClaimingRevocation(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            f.invite(owner, roomId);
            for (int i = 0; i < 4; i++) f.control(owner, roomId, ControlMessage.InviteOperation.ROTATE, true, 30 + i);
            ControlMessage.RoomInvite last = assertInstanceOf(ControlMessage.RoomInviteControlResult.class, owner.last()).invite();
            f.control(owner, roomId, ControlMessage.InviteOperation.SET_SHARING, false, 40);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, owner.last());
            assertTrue(f.registry.find(roomId).orElseThrow().descriptor().accessPolicy().membersCanInvite());
            assertEquals(last, f.invite(owner, roomId));
        }
    }

    @Test
    void staleTupleAndUnrelatedSameIdentityCannotControlOrDeleteAnAlias(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite old = f.invite(owner, roomId);
            f.control(owner, roomId, ControlMessage.InviteOperation.ROTATE, true, 20);
            ControlMessage.RoomInvite current = assertInstanceOf(ControlMessage.RoomInviteControlResult.class, owner.last()).invite();
            f.send(owner, new ControlMessage.RoomInviteControl(roomId, old.instanceId(), old.accessEpoch(),
                    old.inviteEpoch(), ControlMessage.InviteOperation.ROTATE, true), 21);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, owner.last());
            Connection unrelated = f.admit("Owner", "192.0.2.1");
            f.control(unrelated, roomId, ControlMessage.InviteOperation.ROTATE, true, 22);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, unrelated.last());
            f.broker.onDisconnected(unrelated);
            assertEquals(current, f.invite(owner, roomId));
            f.broker.onDisconnected(owner);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(current.code()), 23);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, guest.last());
        }
    }

    @Test
    void passwordEpochUpdateInvalidatesOldAttachWithoutChangingRoomMode(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite old = f.invite(owner, roomId);
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(old.code()), 10);
            f.send(owner, new ControlMessage.RoomInviteControl(roomId, old.instanceId(), old.accessEpoch() + 1,
                    old.inviteEpoch(), ControlMessage.InviteOperation.ACCESS_CHANGED, true), 20);
            ControlMessage.RoomInvite changed = assertInstanceOf(ControlMessage.RoomInviteControlResult.class, owner.last()).invite();
            assertEquals(old.accessEpoch() + 1, changed.accessEpoch());
            assertEquals(PRIVATE, f.registry.find(roomId).orElseThrow().descriptor().accessPolicy());
            f.send(guest, new ControlMessage.RelayAttach(roomId), 11);
            assertInstanceOf(ControlMessage.RoomJoinRejected.class, guest.last());
        }
    }

    @Test
    void staleFetchRecoversCanonicalPolicyAndTupleButStaleMutationIsDenied(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite old = f.invite(owner, roomId);
            f.control(owner, roomId, ControlMessage.InviteOperation.SET_SHARING, false, 20);
            ControlMessage.RoomInviteControlResult current = assertInstanceOf(
                    ControlMessage.RoomInviteControlResult.class, owner.last());
            f.send(owner, new ControlMessage.RoomInviteControl(roomId, old.instanceId(), old.accessEpoch(),
                    old.inviteEpoch(), ControlMessage.InviteOperation.FETCH, true), 21);
            assertEquals(current, owner.last());
            f.send(owner, new ControlMessage.RoomInviteControl(roomId, old.instanceId(), old.accessEpoch(),
                    old.inviteEpoch(), ControlMessage.InviteOperation.SET_SHARING, true), 22);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, owner.last());
            assertFalse(f.registry.find(roomId).orElseThrow().descriptor().accessPolicy().membersCanInvite());
        }
    }

    @Test
    void retriedAccessChangeReturnsTheCommittedEpochWithoutRotatingAgain(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            ControlMessage.RoomInvite old = f.invite(owner, roomId);
            ControlMessage.RoomInviteControl change = new ControlMessage.RoomInviteControl(roomId,
                    old.instanceId(), old.accessEpoch() + 1, old.inviteEpoch(),
                    ControlMessage.InviteOperation.ACCESS_CHANGED, true);
            f.send(owner, change, 20);
            ControlMessage.RoomInviteControlResult committed = assertInstanceOf(
                    ControlMessage.RoomInviteControlResult.class, owner.last());
            SessionRegistry.RoomEntry current = f.registry.find(roomId).orElseThrow();
            f.send(owner, change, 21);
            assertEquals(committed, owner.last());
            assertEquals(current, f.registry.find(roomId).orElseThrow());
        }
    }

    @Test
    void explicitReregistrationSeedsLiveHostEpochOnlyFromItsNewExactProvider(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection oldOwner = f.admit("Owner", "192.0.2.1");
            f.send(oldOwner, f.creation(PRIVATE, "DIRECT", "same-live-instance"), 1);
            String oldRoomId = assertInstanceOf(ControlMessage.RoomCreated.class, oldOwner.last()).roomId();
            ControlMessage.RoomInvite oldInvite = f.invite(oldOwner, oldRoomId);
            Connection newOwner = f.admit("Owner", "192.0.2.1");
            f.send(newOwner, f.creation(PRIVATE, "DIRECT", "same-live-instance"), 2);
            String newRoomId = assertInstanceOf(ControlMessage.RoomCreated.class, newOwner.last()).roomId();

            f.send(oldOwner, new ControlMessage.RoomInviteControl(newRoomId, "same-live-instance", 0,
                    100, ControlMessage.InviteOperation.FETCH, true), 30);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, oldOwner.last());
            assertEquals(0, f.registry.find(newRoomId).orElseThrow().inviteEpoch());
            f.send(newOwner, new ControlMessage.RoomInviteControl(newRoomId, "same-live-instance", 0,
                    8, ControlMessage.InviteOperation.FETCH, true), 31);
            ControlMessage.RoomInvite canonical = assertInstanceOf(
                    ControlMessage.RoomInviteControlResult.class, newOwner.last()).invite();
            assertEquals(9, canonical.inviteEpoch());
            assertNotEquals(oldInvite.code(), canonical.code());
            f.send(newOwner, new ControlMessage.RoomInviteControl(newRoomId, "same-live-instance", 0,
                    8, ControlMessage.InviteOperation.ROTATE, true), 32);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, newOwner.last());
            f.broker.onDisconnected(oldOwner);
            assertTrue(f.registry.find(newRoomId).isPresent());
            Connection guest = f.admit("Guest", "192.0.2.2");
            f.send(guest, new ControlMessage.RoomInviteResolve(oldInvite.code()), 33);
            assertInstanceOf(ControlMessage.RoomInviteRejected.class, guest.last());
            f.send(guest, new ControlMessage.RoomInviteResolve(canonical.code()), 34);
            assertEquals(newRoomId, assertInstanceOf(ControlMessage.RoomJoinResult.class, guest.last()).roomId());
        }
    }

    @Test
    void immediateFailuresAndThrottledListsEchoTheirOwnRequestIds(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, new ControlMessage.RoomListRequest(null, 0), 101);
            f.send(owner, new ControlMessage.RoomListRequest(null, 1), 102);
            assertEquals(102, owner.received.getLast().requestId());
            f.send(owner, new ControlMessage.RoomJoinRequest("missing"), 103);
            assertEquals(103, owner.received.getLast().requestId());
            f.send(owner, new ControlMessage.RoomInviteResolve("?"), 104);
            assertEquals(104, owner.received.getLast().requestId());
            f.send(owner, f.creation(RoomAccessPolicy.PUBLIC, "invalid", null), 105);
            assertEquals(105, owner.received.getLast().requestId());
        }
    }

    @Test
    void canceledOrDisconnectedPreparationNeverPublishesEvenAfterReadyCompletes(@TempDir Path dir)
            throws Exception {
        try (Fixture f = new Fixture(dir)) {
            f.relays.hold = true;
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 11);
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 12);
            assertTrue(f.registry.list(null).isEmpty());
            f.send(owner, new ControlMessage.MasterRequestCancel(11), 0);
            f.broker.onDisconnected(owner);
            f.relays.complete(0);
            f.relays.complete(1);
            assertTrue(f.registry.list(null).isEmpty());
            assertTrue(f.relays.published.isEmpty());
            assertEquals(2, f.relays.discarded);
            assertTrue(owner.received.stream().noneMatch(reply -> reply.message() instanceof ControlMessage.RoomCreated));
        }
    }

    @Test
    void outOfOrderReadyAndFailureRepliesKeepTheirRequestIds(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            f.relays.hold = true;
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 41);
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 42);
            f.relays.complete(1);
            assertInstanceOf(ControlMessage.RoomCreated.class, owner.last());
            assertEquals(42, owner.received.getLast().requestId());
            f.relays.pending.get(0).completeExceptionally(new IllegalStateException("unavailable"));
            assertInstanceOf(ControlMessage.RoomCreateRejected.class, owner.last());
            assertEquals(41, owner.received.getLast().requestId());
            assertEquals(1, f.registry.list(null).size());
        }
    }

    @Test
    void timeoutRejectsItsRequestAndLateReadinessCannotRegisterAnAlias(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            f.relays.hold = true;
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 41);
            f.now[0] += 10_000;
            f.broker.tick();
            assertInstanceOf(ControlMessage.RoomCreateRejected.class, owner.last());
            assertEquals(41, owner.received.getLast().requestId());
            f.relays.complete(0);
            assertTrue(f.registry.list(null).isEmpty());
            assertTrue(f.relays.published.isEmpty());
            assertEquals(1, f.relays.discarded);
        }
    }

    @Test
    void lateReadyCompletionChecksDeadlineEvenBeforeTheNextBrokerTick(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            f.relays.hold = true;
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 41);
            f.now[0] += 10_000;
            f.relays.complete(0);
            assertInstanceOf(ControlMessage.RoomCreateRejected.class, owner.last());
            assertEquals(41, owner.received.getLast().requestId());
            assertTrue(f.registry.list(null).isEmpty());
            assertTrue(f.relays.published.isEmpty());
            assertEquals(1, f.relays.discarded);
        }
    }

    @Test
    void onlyOriginCanCancelItsUnclaimedPublishedCreation(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 51);
            Connection sameIdentity = f.admit("Owner", "192.0.2.1");
            f.send(sameIdentity, new ControlMessage.MasterRequestCancel(51), 0);
            assertTrue(f.registry.find(roomId).isPresent());
            f.send(owner, new ControlMessage.MasterRequestCancel(51), 0);
            assertTrue(f.registry.find(roomId).isEmpty());
            f.send(owner, new ControlMessage.MasterRequestCancel(51), 0);
            assertTrue(f.registry.list(null).isEmpty());
        }
    }

    @Test
    void claimedCreationSurvivesALaterCancellation(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            f.send(owner, f.creation(PRIVATE, "RELAY", null), 51);
            String roomId = assertInstanceOf(ControlMessage.RoomCreated.class, owner.last()).roomId();
            Connection roomOwner = f.admit("Owner", "192.0.2.2");
            f.send(roomOwner, new ControlMessage.RoomJoinRequest(roomId), 60);
            f.send(roomOwner, new ControlMessage.RelayAttach(roomId), 61);
            f.send(owner, new ControlMessage.MasterRequestCancel(51), 0);
            assertTrue(f.registry.find(roomId).isPresent());
        }
    }

    @Test
    void sourcePasswordBudgetRunsBeforeTunnelingAndSurvivesNewGuestIds(@TempDir Path dir) throws Exception {
        try (Fixture f = new Fixture(dir)) {
            Connection owner = f.admit("Owner", "192.0.2.1");
            String roomId = f.create(owner, PRIVATE, 1);
            for (int i = 0; i < 21; i++) {
                Connection routingOwner = f.admit("Owner", "192.0.2.2");
                f.send(routingOwner, new ControlMessage.RoomJoinRequest(roomId), 10);
                f.send(routingOwner, new ControlMessage.RelayAttach(roomId), 11);
                int guestId = f.broker.takeAttachResult(routingOwner).orElseThrow().guestId();
                int before = owner.received.size();
                try (RoomPassword password = RoomPassword.of(PASSWORD)) {
                    f.tunnels.guestText(guestId, ControlCodec.encode(null,
                            new ControlMessage.RoomAccessSubmit("challenge", password)));
                }
                if (i < 20) {
                    assertInstanceOf(ControlMessage.RelayGuestText.class, owner.last());
                    assertNull(routingOwner.closed);
                } else {
                    assertInstanceOf(ControlMessage.RelayGuestClose.class, owner.last());
                    assertNotNull(routingOwner.closed);
                    assertEquals(before + 1, owner.received.size());
                }
                f.tunnels.guestDisconnected(guestId);
                f.broker.onDisconnected(routingOwner);
            }
        }
    }
}
