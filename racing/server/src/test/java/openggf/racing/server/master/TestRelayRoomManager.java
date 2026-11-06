package openggf.racing.server.master;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRelayRoomManager {
    private final List<RelayRoomManager> ownedManagers = new ArrayList<>();

    private RelayRoomManager own(RelayRoomManager manager) {
        ownedManagers.add(manager);
        return manager;
    }

    @org.junit.jupiter.api.AfterEach
    void closeManagers() { ownedManagers.forEach(RelayRoomManager::close); }

    @Test
    void heldRoomCoalescesTicksAndBoundsAdmission(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir)) {
            for (int i = 0; i < 600; i++) fixture.manager.tickAll();
            int ticks = fixture.owner.size();
            int admitted = 0;
            for (int i = 0; i < 600; i++) {
                if (attach(fixture.manager, new FakeConnection(), fixture.entry.roomId(),
                        "a".repeat(64), "Guest")) admitted++;
            }
            int accepted = admitted;
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> assertTrue(ticks <= 1, "a held owner retains one scheduled tick"),
                    () -> assertTrue(accepted <= 512, "room admission callbacks have a finite mailbox"),
                    () -> assertTrue(WorkBudget.PROCESS.retainedBytes() > 0,
                            "retained admission identity must remain charged"));
        }
    }

    @Test
    void heldBrokerBoundsRoomStatusCallbacks(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir, false, true)) {
            for (int i = 0; i < 600 * RelayRoomManager.PLAYER_COUNT_INTERVAL_TICKS; i++)
                fixture.manager.tickAll();
            assertTrue(fixture.broker.size() <= 512,
                    "room-to-broker updates cannot retain an unbounded queue");
            assertTrue(fixture.manager.find(fixture.entry.roomId()).isPresent(),
                    "delayed discovery status must not retire the live room");
            fixture.drain();
            for (int i = 0; i < RelayRoomManager.PLAYER_COUNT_INTERVAL_TICKS; i++)
                fixture.manager.tickAll();
            fixture.drain();
            assertTrue(!fixture.publishedCounts.isEmpty(),
                    "status publication resumes after broker capacity returns");
        }
    }

    @Test
    void fullBrokerHandoffRetainsTheThirdRoomsVerificationUntilDrain(@TempDir Path dir)
            throws Exception {
        try (var fixture = new HeldRoom(dir, false, true)) {
            var second = fixture.registry.create(new ControlMessage.RoomDescriptor(
                    "Second", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", "second-host", "1.1.1.2", 0, "0.6:cafe");
            var innocent = fixture.registry.create(new ControlMessage.RoomDescriptor(
                    "Innocent", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", "third-host", "1.1.1.3", 0, "0.6:cafe");
            fixture.manager.createRelayRoom(second);
            fixture.manager.createRelayRoom(innocent);
            try {
                ControlMessage.AttemptFinish finish = new ControlMessage.AttemptFinish(
                        1, 100, 1, 101, "aa".repeat(32), "bb".repeat(32), null);
                for (int i = 0; i < 256; i++) {
                    fixture.manager.requestVerification(fixture.entry.roomId(), 1, "first",
                            finish, "s3k:0:0", "sonic", "0.6:cafe", true);
                    fixture.manager.requestVerification(second.roomId(), 1, "second",
                            finish, "s3k:0:0", "sonic", "0.6:cafe", true);
                }
                assertEquals(512, fixture.broker.size());
                fixture.manager.requestVerification(innocent.roomId(), 1, "third",
                        finish, "s3k:0:0", "sonic", "0.6:cafe", true);
                assertTrue(fixture.manager.find(innocent.roomId()).isPresent(),
                        "a third room must survive saturation caused by two other rooms");
                fixture.drain();
                fixture.manager.tickAll();
                assertEquals(1, fixture.broker.size(),
                        "the third room's charged verification handoff must retry after drain");
            } finally {
                fixture.manager.hostLeft(second.roomId());
                fixture.manager.hostLeft(innocent.roomId());
                fixture.drain();
            }
        }
    }

    @Test
    void coalescedTickRetainsDuePlayerCountPublication(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir)) {
            for (int i = 0; i < RelayRoomManager.PLAYER_COUNT_INTERVAL_TICKS; i++) {
                fixture.manager.tickAll();
            }
            assertEquals(1, fixture.owner.size());
            fixture.drain();
            assertEquals(List.of(0), fixture.publishedCounts);
        }
    }

    @Test
    void heldRoomRetainsItsCloseCellWhenTheProcessLedgerIsFull(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir)) {
            assertEquals(fixture.before + 1, WorkBudget.PROCESS.retainedItems(),
                    "room publication must already own its retirement cell");
            List<WorkBudget.Reservation> filler = new ArrayList<>();
            try {
                var source = WorkBudget.PROCESS.account(8192, 1);
                for (;;) {
                    var reservation = WorkBudget.PROCESS.tryReserve(source, source,
                            new WorkBudget.Cost(1, 0));
                    if (reservation.isEmpty()) break;
                    filler.add(reservation.get());
                }
                fixture.manager.hostLeft(fixture.entry.roomId());
            } finally { filler.forEach(WorkBudget.Reservation::close); }
            assertEquals(fixture.before + 1, WorkBudget.PROCESS.retainedItems());
            fixture.drain();
            assertEquals(fixture.before, WorkBudget.PROCESS.retainedItems());
        }
    }

    @Test
    void stalePreparedCloseCannotRemoveItsReplacement(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir)) {
            fixture.manager.hostLeft(fixture.entry.roomId());
            fixture.drain();
            var old = fixture.manager.prepareRelayRoom(fixture.entry, null).join();
            old.publish(null);
            old.close();
            var replacement = fixture.manager.prepareRelayRoom(fixture.entry, null).join();
            replacement.publish(null);
            var current = fixture.manager.find(fixture.entry.roomId()).orElseThrow();
            old.close();
            fixture.drain();
            org.junit.jupiter.api.Assertions.assertSame(current,
                    fixture.manager.find(fixture.entry.roomId()).orElseThrow());
            assertEquals(fixture.before + 1, WorkBudget.PROCESS.retainedItems());
        }
    }

    @Test
    void managerCloseFencesPreparedPublicationAndIsIdempotent(@TempDir Path dir) throws Exception {
        try (var fixture = new HeldRoom(dir)) {
            fixture.manager.hostLeft(fixture.entry.roomId());
            fixture.drain();
            var pending = fixture.manager.prepareRelayRoom(fixture.entry, null).join();
            fixture.manager.close();
            fixture.manager.close();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> pending.publish(null));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
                    () -> fixture.manager.prepareRelayRoom(fixture.entry, null).join());
            fixture.drain();
            assertEquals(0, fixture.manager.roomCount());
            assertEquals(fixture.before, WorkBudget.PROCESS.retainedItems());
        }
    }

    private static final class HeldRoom implements AutoCloseable {
        final int before = WorkBudget.PROCESS.retainedItems();
        final ArrayDeque<Runnable> owner = new ArrayDeque<>();
        final ArrayDeque<Runnable> broker = new ArrayDeque<>();
        final List<Integer> publishedCounts = new ArrayList<>();
        final SqliteIdentityStore store;
        final RelayRoomManager manager;
        final SessionRegistry registry;
        final SessionRegistry.RoomEntry entry;

        HeldRoom(Path dir) throws Exception {
            this(dir, true, false);
        }

        HeldRoom(Path dir, boolean holdOwner, boolean holdBroker) throws Exception {
            store = new SqliteIdentityStore(dir.resolve("held.db"));
            long[] now = {1_000_000};
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            manager = new RelayRoomManager(PlayerIdentity.loadOrCreate(dir.resolve("held-master")),
                    ladder, TrackValidationProfileSource.none(),
                    List.<Executor>of(holdOwner ? owner::addLast : Runnable::run),
                    holdBroker ? broker::addLast : Runnable::run,
                    () -> now[0], (room, count) -> publishedCounts.add(count));
            registry = new SessionRegistry(() -> now[0], MasterConfig.defaults());
            entry = registry.create(
                    new ControlMessage.RoomDescriptor("Held", "s3k", 0, 0, "OPEN", null, 64, false),
                    "RELAY", "host-fp", "1.1.1.1", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
        }

        void drain() {
            while (!owner.isEmpty() || !broker.isEmpty()) {
                if (!owner.isEmpty()) owner.removeFirst().run();
                if (!broker.isEmpty()) broker.removeFirst().run();
            }
        }

        @Override public void close() {
            try {
                manager.hostLeft(entry.roomId());
                drain();
                assertEquals(before, WorkBudget.PROCESS.retainedItems());
            } finally { store.close(); }
        }
    }

    @Test
    void publicRelayCapsOwnerChangesAndPublishesAuthoritativeMode(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        MasterConfig config = MasterConfig.defaults();
        SessionRegistry registry = new SessionRegistry(() -> now[0], config);
        try (var store = new SqliteIdentityStore(dir.resolve("chat-ids.db"))) {
            var ladder = new TrustLadder(store, new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    config.thresholds(), () -> now[0]);
            var manager = own(new RelayRoomManager(PlayerIdentity.loadOrCreate(dir.resolve("master")),
                    ladder, TrackValidationProfileSource.none(), List.of(Runnable::run), Runnable::run,
                    () -> now[0], registry::heartbeat,
                    (roomId, owner, zone, act) -> registry.updateTrack(roomId, owner, zone, act),
                    config, null, null, null, registry::updateChatMode));
            PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("chat-owner"));
            var entry = registry.create(new ControlMessage.RoomDescriptor("Chat", "s3k", 0, 0,
                    "OPEN", null, 8, false, "FULL_CHAT"), "RELAY", owner.fingerprint(), "local", 0,
                    "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();
            FakeConnection connection = new FakeConnection();
            String token = admit(manager, access, entry.roomId(), connection, owner, "OWNER");
            assertEquals("QUICK_CHAT", lastOfType(connection, ControlMessage.JoinAccepted.class)
                    .room().chatMode());
            access.room().onText(connection, ControlCodec.encode(token,
                    new ControlMessage.SetChatMode("OFF")));
            manager.tickAll();
            assertEquals("OFF", registry.find(entry.roomId()).orElseThrow().descriptor().chatMode());
            access.room().onText(connection, ControlCodec.encode(token,
                    new ControlMessage.SetChatMode("FULL_CHAT")));
            manager.tickAll();
            assertEquals("QUICK_CHAT", access.room().descriptor().chatMode());
            assertEquals("QUICK_CHAT", registry.find(entry.roomId()).orElseThrow().descriptor().chatMode());
            manager.hostLeft(entry.roomId());
        }
    }

    static final class FakeConnection implements HubConnection, com.openggf.net.hub.HandshakeTransport {
        @Override public String authenticationBinding() { return com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT; }
        @Override public com.openggf.net.protocol.AuthenticationTranscript.Role authenticationRole() {
            return com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY;
        }
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

    private RelayRoomManager manager(PlayerIdentity master, TrustLadder ladder,
                                             long[] now) {
        return own(new RelayRoomManager(master, ladder, TrackValidationProfileSource.none(),
                List.of(Runnable::run), Runnable::run, () -> now[0],
                (roomId, count) -> { }));
    }

    private static boolean attach(RelayRoomManager manager, HubConnection connection,
                                  String roomId, String fingerprint, String name) {
        String instanceId = manager.find(roomId)
                .map(access -> access.room().instanceId()).orElse("missing-instance");
        return manager.attach(connection, roomId, fingerprint, name,
                instanceId, "test-route", 1);
    }

    private static String admit(RelayRoomManager manager,
                                RelayRoomManager.RoomAccess access, String roomId,
                                FakeConnection connection, PlayerIdentity identity,
                                String name) throws Exception {
        assertTrue(attach(manager, connection, roomId, identity.fingerprint(), name));
        ClientHandshake handshake = new ClientHandshake(identity, name, "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
        access.room().onText(connection, ControlCodec.encode(null, handshake.hello()));
        access.room().onText(connection, ControlCodec.encode(null,
                handshake.onWelcome((ControlMessage.Welcome) lastMessage(connection))));
        return lastOfType(connection, ControlMessage.JoinAccepted.class).sessionToken();
    }

    private static void finish(RelayRoomManager.RoomAccess access,
                               FakeConnection connection, String token, long[] now) {
        finish(access, connection, token, now, 1, 100, "aa");
    }

    private static void finish(RelayRoomManager.RoomAccess access,
                               FakeConnection connection, String token, long[] now,
                               int attemptId, int timeFrames, String hashByte) {
        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> access.room().onBinary(connection, packet));
        access.room().onText(connection, ControlCodec.encode(token,
                new ControlMessage.AttemptStart(attemptId)));
        publisher.beginAttempt(attemptId);
        for (int frame = 0; frame <= timeFrames + 1; frame++) {
            now[0] += 17;
            publisher.onFrame(new GhostFrame(100 + frame, 200, 1,
                    false, false, false, 2, false));
        }
        publisher.finishAttempt();
        access.room().onText(connection, ControlCodec.encode(token,
                new ControlMessage.AttemptFinish(attemptId, timeFrames, 1, timeFrames + 1,
                        hashByte.repeat(32), HexFormat.of().formatHex(
                        publisher.streamHashSha256()), null)));
    }

    /** A verified room in which one player finished twice, the second time faster. */
    private record SupersededClaims(RelayRoomManager manager, VerificationJobQueue jobs,
                                    RelayRoomManager.RoomAccess access, MasterConfig config,
                                    String fingerprint) { }

    private SupersededClaims supersededClaims(Path dir, long[] now,
                                                     SqliteIdentityStore store)
            throws Exception {
        var ladder = new TrustLadder(store,
                new NewIdentityCache(100, 3_600_000, () -> now[0]),
                TrustLadder.Thresholds.defaults(), () -> now[0]);
        MasterConfig config = MasterConfig.defaults();
        VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1000);
        RelayRoomManager manager = own(new RelayRoomManager(
                PlayerIdentity.loadOrCreate(dir.resolve("m")), ladder,
                TrackValidationProfileSource.none(), List.of(Runnable::run),
                Runnable::run, () -> now[0], (roomId, count) -> { },
                (roomId, owner, zone, act) -> { }, config, jobs,
                new VerdictConsequences(store, ladder, () -> now[0], 0)));
        PlayerIdentity player = PlayerIdentity.loadOrCreate(dir.resolve("player"));
        SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0], config)
                .create(new ControlMessage.RoomDescriptor(
                                "Verified", "s3k", 0, 0, "OPEN", null, 8, true),
                        "RELAY", player.fingerprint(), "1.1.1.1", 0, "0.6:cafe");
        manager.createRelayRoom(entry);
        var access = manager.find(entry.roomId()).orElseThrow();
        FakeConnection connection = new FakeConnection();
        String token = admit(manager, access, entry.roomId(), connection, player, "P");
        // One present player under the default rule: ready up before the owner starts.
        access.room().onText(connection, ControlCodec.encode(token, new ControlMessage.SetReady(true)));
        access.room().onText(connection, ControlCodec.encode(token,
                new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                        "s3k", 0, 0, 60, "OPEN", null))));
        now[0] += 3_000;
        access.room().tick();
        finish(access, connection, token, now, 1, 100, "aa");
        finish(access, connection, token, now, 2, 90, "bb");
        assertEquals(VerificationJobQueue.State.AWAITING_UPLOAD, jobs.stateOf("vj-1"));
        assertEquals(VerificationJobQueue.State.AWAITING_UPLOAD, jobs.stateOf("vj-2"));
        ControlMessage.StandingsRow shown = access.room().round().standings().getFirst();
        assertEquals(90, shown.bestTimeFrames());
        assertEquals("PENDING", shown.verifyState());
        return new SupersededClaims(manager, jobs, access, config, player.fingerprint());
    }

    @Test
    void supersededClaimPassSurvivesTheNewerClaimFailing(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            SupersededClaims claims = supersededClaims(dir, now, store);
            assertTrue(claims.jobs().onRecordingUploaded("aa".repeat(32), claims.fingerprint()));
            VerificationJobQueue.Job older = claims.jobs().lease("worker",
                    java.util.Set.of("0.6:cafe")).orElseThrow();
            assertEquals("vj-1", older.jobId());
            claims.jobs().complete(older.jobId(), "worker").orElseThrow();
            claims.manager().onVerdict(older, true);
            assertEquals("PENDING",
                    claims.access().room().round().standings().getFirst().verifyState());

            assertTrue(claims.jobs().onRecordingUploaded("bb".repeat(32), claims.fingerprint()));
            VerificationJobQueue.Job newer = claims.jobs().lease("worker",
                    java.util.Set.of("0.6:cafe")).orElseThrow();
            claims.jobs().complete(newer.jobId(), "worker").orElseThrow();
            claims.manager().onVerdict(newer, false);
            ControlMessage.StandingsRow shown =
                    claims.access().room().round().standings().getFirst();
            assertEquals(100, shown.bestTimeFrames());
            assertEquals("VERIFIED", shown.verifyState());
        }
    }

    @Test
    void verifiedVerdictWaitsForAnUnrelatedFullRoomMailbox(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            SupersededClaims claims = supersededClaims(dir, now, store);
            assertTrue(claims.jobs().onRecordingUploaded("bb".repeat(32), claims.fingerprint()));
            VerificationJobQueue.Job newer = claims.jobs().lease("worker",
                    java.util.Set.of("0.6:cafe")).orElseThrow();
            claims.jobs().complete(newer.jobId(), "worker").orElseThrow();
            var roomHolder = WorkBudget.PROCESS.account(512, 1);
            WorkBudget.Reservation fullRoom = WorkBudget.PROCESS.tryReserve(roomHolder,
                    claims.access().inboundWork(), new WorkBudget.Cost(
                            512 - claims.access().inboundWork().retainedItems(), 0)).orElseThrow();
            var processHolder = WorkBudget.PROCESS.account(8192, 1);
            WorkBudget.Reservation fullProcess = WorkBudget.PROCESS.tryReserve(processHolder,
                    processHolder, new WorkBudget.Cost(
                            8192 - WorkBudget.PROCESS.retainedItems(), 0)).orElseThrow();
            try {
                claims.manager().onVerdict(newer, true);
                assertEquals("PENDING", claims.access().room().round().standings()
                        .getFirst().verifyState());
            } finally {
                fullProcess.close();
                fullRoom.close();
            }
            claims.manager().tickAll();
            assertEquals("VERIFIED", claims.access().room().round().standings()
                    .getFirst().verifyState(), "the charged verdict must retry after room drain");
        }
    }

    @Test
    void roundEndVoidsTheRouteOfEveryUnresolvedClaim(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            SupersededClaims claims = supersededClaims(dir, now, store);
            now[0] += 60_000 + com.openggf.net.hub.HostRoundEngine.FINISH_GRACE_MILLIS;
            claims.access().room().tick();
            assertEquals(com.openggf.net.hub.HostRoundEngine.Phase.ROUND_END,
                    claims.access().room().round().phase());
            now[0] += com.openggf.net.hub.HostRoundEngine.ROUND_END_LINGER_MILLIS
                    + claims.config().verifiedUploadDeadlineSeconds() * 1000L
                    + claims.config().verdictGraceMillis();
            claims.access().room().tick();

            assertEquals(VerificationJobQueue.State.VOID, claims.jobs().stateOf("vj-1"));
            assertEquals(VerificationJobQueue.State.VOID, claims.jobs().stateOf("vj-2"));
            assertTrue(claims.access().room().round().standings().isEmpty());
            assertEquals(List.of(VerdictCodec.RESULT_VOID_NO_UPLOAD,
                            VerdictCodec.RESULT_VOID_NO_UPLOAD),
                    store.verdictsFor(claims.fingerprint()).stream()
                            .map(IdentityStore.VerdictRecord::result).toList());
        }
    }

    @Test
    void supersededUploadedClaimVoidedAtLingerIsNotRecordedAsNoUpload(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            SupersededClaims claims = supersededClaims(dir, now, store);
            assertTrue(claims.jobs().onRecordingUploaded("aa".repeat(32), claims.fingerprint()));
            assertEquals("vj-1", claims.jobs().lease("worker",
                    java.util.Set.of("0.6:cafe")).orElseThrow().jobId());
            assertTrue(claims.jobs().onRecordingUploaded("bb".repeat(32), claims.fingerprint()));
            VerificationJobQueue.Job newer = claims.jobs().lease("worker",
                    java.util.Set.of("0.6:cafe")).orElseThrow();
            claims.jobs().complete(newer.jobId(), "worker").orElseThrow();
            claims.manager().onVerdict(newer, true);
            assertEquals(0, claims.access().room().round().pendingVerdictCount());

            // The shown best is verified, so the superseded claim does not hold the
            // round end; it expires at the linger while its upload is being verified.
            now[0] += 60_000 + com.openggf.net.hub.HostRoundEngine.FINISH_GRACE_MILLIS;
            claims.access().room().tick();
            now[0] += com.openggf.net.hub.HostRoundEngine.ROUND_END_LINGER_MILLIS;
            claims.access().room().tick();

            assertEquals(VerificationJobQueue.State.VOID, claims.jobs().stateOf("vj-1"));
            assertEquals(VerificationJobQueue.State.DONE, claims.jobs().stateOf("vj-2"));
            List<IdentityStore.VerdictRecord> verdicts =
                    store.verdictsFor(claims.fingerprint());
            assertEquals(1, verdicts.size());
            assertEquals("aa".repeat(32), verdicts.getFirst().inputRecordingHashHex());
            assertEquals(VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE,
                    verdicts.getFirst().result());
            assertEquals(0, store.activeSanctions(claims.fingerprint(), now[0]).size());
            ControlMessage.StandingsRow shown =
                    claims.access().room().round().standings().getFirst();
            assertEquals(90, shown.bestTimeFrames());
            assertEquals("VERIFIED", shown.verifyState());
        }
    }

    @Test
    void oldVerdictCannotVerifySameIdentityRejoinWithSameClaim(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            MasterConfig config = MasterConfig.defaults();
            VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1000);
            RelayRoomManager manager = own(new RelayRoomManager(
                    PlayerIdentity.loadOrCreate(dir.resolve("master")), ladder,
                    TrackValidationProfileSource.none(), List.of(Runnable::run),
                    Runnable::run, () -> now[0], (roomId, count) -> { },
                    (roomId, owner, zone, act) -> { }, config, jobs,
                    new VerdictConsequences(store, ladder, () -> now[0], 0)));
            PlayerIdentity player = PlayerIdentity.loadOrCreate(dir.resolve("player"));
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0], config)
                    .create(new ControlMessage.RoomDescriptor(
                                    "Verified", "s3k", 0, 0, "OPEN", null, 8, true),
                            "RELAY", player.fingerprint(), "1.1.1.1", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();
            FakeConnection oldConnection = new FakeConnection();
            String oldToken = admit(manager, access, entry.roomId(), oldConnection,
                    player, "Old");
            // One present player under the default rule: ready up before the owner starts.
            access.room().onText(oldConnection, ControlCodec.encode(oldToken, new ControlMessage.SetReady(true)));
            access.room().onText(oldConnection, ControlCodec.encode(oldToken,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 60, "OPEN", null))));
            now[0] += 3_000;
            access.room().tick();
            finish(access, oldConnection, oldToken, now);
            assertTrue(jobs.onRecordingUploaded("aa".repeat(32), player.fingerprint()));
            access.room().onDisconnected(oldConnection);

            FakeConnection newConnection = new FakeConnection();
            String newToken = admit(manager, access, entry.roomId(), newConnection,
                    player, "New");
            finish(access, newConnection, newToken, now);
            assertEquals(2, access.room().round().standings().size());
            assertNotEquals(jobs.find("vj-1").orElseThrow().attemptRef(),
                    jobs.find("vj-2").orElseThrow().attemptRef());
            VerificationJobQueue.Job oldJob = jobs.lease("worker", java.util.Set.of("0.6:cafe"))
                    .orElseThrow();
            jobs.complete(oldJob.jobId(), "worker").orElseThrow();
            manager.onVerdict(oldJob, true);

            var stateByName = access.room().round().standings().stream().collect(
                    java.util.stream.Collectors.toMap(ControlMessage.StandingsRow::displayName,
                            ControlMessage.StandingsRow::verifyState));
            assertEquals("VERIFIED", stateByName.get("Old"));
            assertEquals("PENDING", stateByName.get("New"));

            VerificationJobQueue.Job filler = jobs.find("vj-2").orElseThrow();
            for (int i = 2; i < VerificationJobQueue.MAX_TRACKED_JOBS; i++) {
                jobs.submit(filler, now[0] + 60_000);
            }
            access.room().onDisconnected(newConnection);
            FakeConnection thirdConnection = new FakeConnection();
            String thirdToken = admit(manager, access, entry.roomId(), thirdConnection,
                    player, "Third");
            finish(access, thirdConnection, thirdToken, now);
            stateByName = access.room().round().standings().stream().collect(
                    java.util.stream.Collectors.toMap(ControlMessage.StandingsRow::displayName,
                            ControlMessage.StandingsRow::verifyState));
            assertEquals("VERIFIED", stateByName.get("Old"));
            assertEquals("PENDING", stateByName.get("New"));
            assertFalse(stateByName.containsKey("Third"));
        }
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
            assertTrue(attach(manager, guest, entry.roomId(), identity.fingerprint(), "GUEST"));
            ClientHandshake handshake = new ClientHandshake(identity, "GUEST", "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
            var access = manager.find(entry.roomId()).orElseThrow();
            access.room().onText(guest, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(guest, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(guest))));
            lastOfType(guest, ControlMessage.JoinAccepted.class);

            manager.hostLeft(entry.roomId());
            assertEquals(0, manager.roomCount());
            assertFalse(attach(manager, new FakeConnection(), entry.roomId(), "x", "X"));
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
            attach(manager, guest, entry.roomId(), "guest-fp", "GUEST");
            ClientHandshake handshake = new ClientHandshake(
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "GUEST", "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
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
            attach(manager, creator, entry.roomId(), creatorIdentity.fingerprint(), "HOST");
            ClientHandshake handshake = new ClientHandshake(
                    creatorIdentity, "HOST", "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
            access.room().onText(creator, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(creator, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(creator))));
            String token = lastOfType(creator, ControlMessage.JoinAccepted.class).sessionToken();
            // One present player under the default rule: ready up before the owner starts.
            access.room().onText(creator, ControlCodec.encode(token, new ControlMessage.SetReady(true)));
            access.room().onText(creator, ControlCodec.encode(token,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 60, "OPEN", null))));
            assertTrue(creator.text.stream().map(text -> ControlCodec.decode(text).message())
                    .anyMatch(ControlMessage.RoundStart.class::isInstance));

            FakeConnection impostor = new FakeConnection();
            attach(manager, impostor, entry.roomId(), creatorIdentity.fingerprint(), "BAD");
            ClientHandshake wrong = new ClientHandshake(
                    PlayerIdentity.loadOrCreate(dir.resolve("wrong")), "BAD", "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
            access.room().onText(impostor, ControlCodec.encode(null, wrong.hello()));
            access.room().onText(impostor, ControlCodec.encode(null,
                    wrong.onWelcome((ControlMessage.Welcome) lastMessage(impostor))));
            assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(impostor));
        }
    }

    @Test
    void verifiedFinishQueuesJobRequestsUploadAndPassesVerdict(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            MasterConfig config = MasterConfig.defaults();
            VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1000);
            VerdictConsequences consequences = new VerdictConsequences(
                    store, ladder, () -> now[0], 0);
            RelayRoomManager manager = own(new RelayRoomManager(
                    PlayerIdentity.loadOrCreate(dir.resolve("m")), ladder,
                    TrackValidationProfileSource.none(), List.of(Runnable::run),
                    Runnable::run, () -> now[0], (roomId, count) -> { },
                    (roomId, owner, zone, act) -> { }, config, jobs, consequences));
            PlayerIdentity player = PlayerIdentity.loadOrCreate(dir.resolve("player"));
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0], config)
                    .create(new ControlMessage.RoomDescriptor(
                                    "Verified", "s3k", 0, 0, "OPEN", null, 8, true),
                            "RELAY", player.fingerprint(), "1.1.1.1", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();
            FakeConnection connection = new FakeConnection();
            attach(manager, connection, entry.roomId(), player.fingerprint(), "P");
            ClientHandshake handshake = new ClientHandshake(player, "P", "", "0.6:cafe", null, com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT,
                    "test-route", 1);
            access.room().onText(connection, ControlCodec.encode(null, handshake.hello()));
            access.room().onText(connection, ControlCodec.encode(null,
                    handshake.onWelcome((ControlMessage.Welcome) lastMessage(connection))));
            String token = lastOfType(connection,
                    ControlMessage.JoinAccepted.class).sessionToken();
            // One present player under the default rule: ready up before the owner starts.
            access.room().onText(connection, ControlCodec.encode(token, new ControlMessage.SetReady(true)));
            access.room().onText(connection, ControlCodec.encode(token,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 60, "OPEN", null))));
            now[0] += 3_000;
            access.room().tick();
            GhostStreamPublisher publisher = new GhostStreamPublisher(
                    packet -> access.room().onBinary(connection, packet));
            access.room().onText(connection, ControlCodec.encode(token,
                    new ControlMessage.AttemptStart(1)));
            publisher.beginAttempt(1);
            for (int frame = 0; frame <= 101; frame++) {
                now[0] += 17;
                publisher.onFrame(new GhostFrame(100 + frame, 200, 1,
                        false, false, false, 2, false));
            }
            publisher.finishAttempt();
            access.room().onText(connection, ControlCodec.encode(token,
                    new ControlMessage.AttemptFinish(1, 100, 1, 101,
                            "aa".repeat(32), HexFormat.of().formatHex(
                            publisher.streamHashSha256()), null)));

            assertInstanceOf(ControlMessage.RecordingRequest.class,
                    lastOfType(connection, ControlMessage.RecordingRequest.class));
            assertEquals(VerificationJobQueue.State.AWAITING_UPLOAD,
                    jobs.stateOf("vj-1"));
            assertEquals("PENDING",
                    access.room().round().standings().getFirst().verifyState());
            jobs.onRecordingUploaded("aa".repeat(32), player.fingerprint());
            VerificationJobQueue.Job job = jobs.lease("worker", java.util.Set.of("0.6:cafe"))
                    .orElseThrow();
            jobs.complete(job.jobId(), "worker").orElseThrow();
            manager.onVerdict(job, true);
            assertEquals("VERIFIED",
                    access.room().round().standings().getFirst().verifyState());
        }
    }

    @Test
    void stalledUploadedJobGetsDurableVoidVerdictWithoutSanction(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        try (var store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            var ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 3_600_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1_000);
            VerdictConsequences consequences = new VerdictConsequences(
                    store, ladder, () -> now[0], 0);
            RelayRoomManager manager = own(new RelayRoomManager(
                    PlayerIdentity.loadOrCreate(dir.resolve("master")), ladder,
                    TrackValidationProfileSource.none(), List.of(Runnable::run),
                    Runnable::run, () -> now[0], (roomId, count) -> { },
                    (roomId, owner, zone, act) -> { }, MasterConfig.defaults(),
                    jobs, consequences));
            manager.requestVerification("r-1", 1, "player",
                    new ControlMessage.AttemptFinish(1, 100, 1, 101,
                            "aa".repeat(32), "bb".repeat(32), null),
                    "s3k:0:0", "sonic", "0.6:cafe", true);
            assertEquals(true, jobs.onRecordingUploaded("aa".repeat(32), "player"));
            jobs.lease("worker", java.util.Set.of("0.6:cafe")).orElseThrow();
            now[0] += 3_600_001;

            assertEquals(1, manager.voidStalledVerificationJobs());
            assertEquals(VerificationJobQueue.State.VOID, jobs.stateOf("vj-1"));
            assertEquals(VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE,
                    store.verdictsFor("player").getFirst().result());
            assertEquals(0, store.activeSanctions("player", now[0]).size());
            assertEquals(0, manager.voidStalledVerificationJobs());
        }
    }
}
