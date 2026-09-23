package com.openggf.net.master;

import com.openggf.ghost.GhostFrame;
import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestSpotCheck {
    private static final class Connection implements HubConnection {
        final List<String> text = new ArrayList<>();
        @Override public void sendText(String value) { text.add(value); }
        @Override public void sendBinary(byte[] data) { }
        @Override public void close(String reason) { }
        @Override public String remoteHost() { return "127.0.0.1"; }
    }

    @Test
    void slotReuseKeepsSpotCheckOnOriginalIdentity(@TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        Path yaml = dir.resolve("master.yaml");
        Files.writeString(yaml, "plaintextForTest: true\nspotCheckTopTimes: 1\n");
        MasterConfig config = MasterConfig.load(yaml);
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            TrustLadder ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 60_000, () -> now[0]),
                    config.thresholds(), () -> now[0]);
            VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1000);
            VerifierRegistry verifiers = new VerifierRegistry(() -> now[0], 60_000);
            PlayerIdentity worker = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
            verifiers.register(worker.publicKeyEncoded(), Set.of("0.6:cafe"));
            RelayRoomManager manager = new RelayRoomManager(
                    PlayerIdentity.loadOrCreate(dir.resolve("master")), ladder,
                    TrackValidationProfileSource.none(), List.of(Runnable::run),
                    Runnable::run, () -> now[0], (roomId, count) -> { },
                    (roomId, owner, zone, act) -> { }, config, jobs,
                    new VerdictConsequences(store, ladder, () -> now[0], 0), verifiers);
            PlayerIdentity original = PlayerIdentity.loadOrCreate(dir.resolve("original"));
            PlayerIdentity replacement = PlayerIdentity.loadOrCreate(dir.resolve("replacement"));
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0], config)
                    .create(new ControlMessage.RoomDescriptor(
                                    "Casual", "s3k", 0, 0, "OPEN", null, 8, false),
                            "RELAY", original.fingerprint(), "host", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();
            Connection oldConnection = admit(manager, access, entry.roomId(), original, "Old");
            String oldToken = last(oldConnection,
                    ControlMessage.JoinAccepted.class).sessionToken();
            access.room().onText(oldConnection, ControlCodec.encode(oldToken,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 10, "OPEN", null))));
            now[0] += 3_000;
            access.room().tick();
            finish(access, oldConnection, oldToken, 1, 100, 5,
                    "aa".repeat(32), 100, now);
            access.room().onDisconnected(oldConnection);
            Connection newConnection = admit(manager, access, entry.roomId(),
                    replacement, "New");
            assertEquals(0, last(newConnection, ControlMessage.JoinAccepted.class).playerSlot());

            now[0] += 10_001;
            manager.tickAll();
            VerificationJobQueue.Job job = jobs.find("vj-1").orElseThrow();
            assertEquals(original.fingerprint(), job.identityFingerprint());
            assertFalse(newConnection.text.stream()
                    .map(text -> ControlCodec.decode(text).message())
                    .anyMatch(ControlMessage.RecordingRequest.class::isInstance));
        }
    }

    @Test
    void selectsOriginalFinishesThrottlesAndNeverMutatesCasualStandings(
            @TempDir Path dir) throws Exception {
        long[] now = {1_000_000};
        Path yaml = dir.resolve("master.yaml");
        Files.writeString(yaml, "plaintextForTest: true\nspotCheckTopTimes: 2\n");
        MasterConfig config = MasterConfig.load(yaml);
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            TrustLadder ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 60_000, () -> now[0]),
                    config.thresholds(), () -> now[0]);
            VerificationJobQueue jobs = new VerificationJobQueue(() -> now[0], 1000);
            VerdictConsequences consequences = new VerdictConsequences(
                    store, ladder, () -> now[0], 0);
            VerifierRegistry verifiers = new VerifierRegistry(() -> now[0], 60_000);
            PlayerIdentity worker = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
            verifiers.register(worker.publicKeyEncoded(), Set.of("0.6:cafe"));
            RelayRoomManager manager = new RelayRoomManager(
                    PlayerIdentity.loadOrCreate(dir.resolve("master")), ladder,
                    TrackValidationProfileSource.none(), List.of(Runnable::run),
                    Runnable::run, () -> now[0], (roomId, count) -> { },
                    (roomId, owner, zone, act) -> { }, config, jobs,
                    consequences, verifiers);
            PlayerIdentity first = PlayerIdentity.loadOrCreate(dir.resolve("first"));
            PlayerIdentity second = PlayerIdentity.loadOrCreate(dir.resolve("second"));
            SessionRegistry.RoomEntry entry = new SessionRegistry(() -> now[0], config)
                    .create(new ControlMessage.RoomDescriptor(
                                    "Casual", "s3k", 0, 0, "OPEN", null, 8, false),
                            "RELAY", first.fingerprint(), "host", 0, "0.6:cafe");
            manager.createRelayRoom(entry);
            var access = manager.find(entry.roomId()).orElseThrow();
            Connection firstConnection = admit(manager, access, entry.roomId(), first, "A");
            Connection secondConnection = admit(manager, access, entry.roomId(), second, "B");
            String firstToken = last(firstConnection,
                    ControlMessage.JoinAccepted.class).sessionToken();
            access.room().onText(firstConnection, ControlCodec.encode(firstToken,
                    new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig(
                            "s3k", 0, 0, 10, "OPEN", null))));
            now[0] += 3_000;
            access.room().tick();
            String secondToken = last(secondConnection,
                    ControlMessage.JoinAccepted.class).sessionToken();
            ControlMessage.AttemptFinish firstFinish = finish(
                    access, firstConnection, firstToken, 1, 100, 5,
                    "aa".repeat(32), 100, now);
            ControlMessage.AttemptFinish secondFinish = finish(
                    access, secondConnection, secondToken, 1, 120, 7,
                    "bb".repeat(32), 300, now);
            now[0] += 10_001;
            manager.tickAll();

            assertEquals(2, jobs.size());
            VerificationJobQueue.Job firstJob = jobs.find("vj-1").orElseThrow();
            VerificationJobQueue.Job secondJob = jobs.find("vj-2").orElseThrow();
            assertEquals(true, firstJob.spotCheck());
            assertEquals(firstFinish.inputRecordingHashHex(),
                    firstJob.inputRecordingHashHex());
            assertEquals(firstFinish.ghostStreamHashHex(), firstJob.ghostStreamHashHex());
            assertEquals(firstFinish.firstInputFrame(), firstJob.firstInputFrame());
            assertEquals(secondFinish.finishFrame(), secondJob.finishFrame());
            List<ControlMessage.StandingsRow> before = access.room().round().standings();

            jobs.onRecordingUploaded("aa".repeat(32), first.fingerprint());
            VerificationJobQueue.Job leased = jobs.lease("worker", Set.of("0.6:cafe"))
                    .orElseThrow();
            jobs.complete(leased.jobId(), "worker").orElseThrow();
            consequences.apply(new IdentityStore.VerdictRecord(
                    first.fingerprint(), leased.attemptRef(), "aa".repeat(32),
                    VerdictCodec.RESULT_FAIL_DIVERGENT, "sig", now[0]), "worker");
            manager.onVerdict(leased, false);
            assertEquals(before, access.room().round().standings());
            assertEquals(TrustLadder.Tier.SANCTIONED, ladder.tierOf(first.fingerprint()));

            now[0] += config.uploadDeadlineSeconds() * 1000L + 1;
            assertEquals(1, manager.voidExpiredUploads());
            assertEquals(before, access.room().round().standings());
            assertEquals(1, store.verdictsFor(second.fingerprint()).size());

            now[0] += com.openggf.net.hub.HostRoundEngine.ROUND_END_LINGER_MILLIS;
            manager.tickAll();
            access.room().requestStartRound(new ControlMessage.RoundConfig(
                    "s3k", 0, 0, 1, "OPEN", null));
            now[0] += 3_000;
            access.room().tick();
            finish(access, firstConnection, firstToken, 2, 90, 1,
                    "cc".repeat(32), 500, now);
            now[0] += 1_001;
            manager.tickAll();
            assertEquals(2, jobs.size(), "hourly throttle must suppress repeat spot-check");
        }
    }

    private static ControlMessage.AttemptFinish finish(
            RelayRoomManager.RoomAccess access, Connection connection, String token,
            int attemptId, int timeFrames, int firstInputFrame, String inputHash,
            int startX, long[] now) {
        int finishFrame = firstInputFrame + timeFrames;
        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> access.room().onBinary(connection, packet));
        access.room().onText(connection, ControlCodec.encode(token,
                new ControlMessage.AttemptStart(attemptId)));
        publisher.beginAttempt(attemptId);
        for (int frame = 0; frame <= finishFrame; frame++) {
            now[0] += 17;
            publisher.onFrame(new GhostFrame(startX + frame, 200, 1,
                    false, false, false, 2, false));
        }
        publisher.finishAttempt();
        ControlMessage.AttemptFinish finish = new ControlMessage.AttemptFinish(
                attemptId, timeFrames, firstInputFrame, finishFrame, inputHash,
                HexFormat.of().formatHex(publisher.streamHashSha256()), null);
        access.room().onText(connection, ControlCodec.encode(token, finish));
        return finish;
    }

    private static Connection admit(RelayRoomManager manager,
                                    RelayRoomManager.RoomAccess access,
                                    String roomId, PlayerIdentity identity, String name)
            throws Exception {
        Connection connection = new Connection();
        manager.attach(connection, roomId, identity.fingerprint(), name);
        ClientHandshake handshake = new ClientHandshake(identity, name, "0.6:cafe");
        access.room().onText(connection, ControlCodec.encode(null, handshake.hello()));
        access.room().onText(connection, ControlCodec.encode(null,
                handshake.onWelcome(last(connection, ControlMessage.Welcome.class))));
        return connection;
    }

    private static <T extends ControlMessage> T last(Connection connection, Class<T> type) {
        for (int index = connection.text.size() - 1; index >= 0; index--) {
            ControlMessage message = ControlCodec.decode(connection.text.get(index)).message();
            if (type.isInstance(message)) return type.cast(message);
        }
        throw new AssertionError("missing " + type.getSimpleName());
    }
}
