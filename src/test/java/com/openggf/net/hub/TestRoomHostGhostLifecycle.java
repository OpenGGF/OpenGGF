package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Room-level attempt transitions as other members see them in the coarse roster. */
class TestRoomHostGhostLifecycle {
    private static final String FP = "0.6:cafe1234";

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomHost room;

    @BeforeEach
    void setUp() throws Exception {
        room = new RoomHost(new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")), () -> now,
                TrackValidationProfileSource.none(),
                new RoomHostHooks(true, null, null, null, null));
    }

    @Test
    void finishShowsFinishedResetShowsIdleAndNextBatchShowsRunning() throws Exception {
        FakeHubConnection runner = new FakeHubConnection();
        FakeHubConnection watcher = new FakeHubConnection();
        ControlMessage.JoinAccepted runnerJoin = admit(runner, "A", dir.resolve("a"));
        admit(watcher, "B", dir.resolve("b"));
        String token = runnerJoin.sessionToken();
        int slot = runnerJoin.playerSlot();
        room.requestStartRound(new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();

        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> room.onBinary(runner, packet));
        streamAttempt(runner, token, publisher, 1);
        room.onText(runner, ControlCodec.encode(token, new ControlMessage.AttemptFinish(
                1, 1, 0, 1, "ab".repeat(32),
                HexFormat.of().formatHex(publisher.streamHashSha256()), null)));
        assertEquals(GhostPackets.ROSTER_STATUS_FINISHED, statusAfterRoster(watcher, slot));

        streamAttempt(runner, token, publisher, 2);
        assertEquals(GhostPackets.ROSTER_STATUS_RUNNING, statusAfterRoster(watcher, slot));
        room.onText(runner, ControlCodec.encode(token, new ControlMessage.AttemptReset(2)));
        assertEquals(GhostPackets.ROSTER_STATUS_IDLE, statusAfterRoster(watcher, slot));
    }

    @Test
    void unverifiedRoomPublishesLabelledClientClaimedStandings() throws Exception {
        FakeHubConnection runner = new FakeHubConnection();
        FakeHubConnection watcher = new FakeHubConnection();
        ControlMessage.JoinAccepted runnerJoin = admit(runner, "A", dir.resolve("a"));
        admit(watcher, "B", dir.resolve("b"));
        room.requestStartRound(new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();

        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> room.onBinary(runner, packet));
        streamAttempt(runner, runnerJoin.sessionToken(), publisher, 1);
        room.onText(runner, ControlCodec.encode(runnerJoin.sessionToken(),
                new ControlMessage.AttemptFinish(1, 1, 0, 1,
                        "ab".repeat(32),
                        HexFormat.of().formatHex(publisher.streamHashSha256()), null)));

        assertTrue(watcher.text.stream()
                .map(text -> ControlCodec.decode(text).message())
                .filter(ControlMessage.StandingsDelta.class::isInstance)
                .map(ControlMessage.StandingsDelta.class::cast)
                .allMatch(delta -> delta.rows().stream().allMatch(row -> "UNVERIFIED".equals(row.verifyState()))),
                "published claims must be labelled unverified");
        assertEquals("UNVERIFIED", room.round().standings().getFirst().verifyState());
        assertEquals(1, room.round().results().size(),
                "the operator may retain the claim for a bounded spot check");

        now += 300_001;
        room.tick();
        assertTrue(watcher.text.stream()
                .map(text -> ControlCodec.decode(text).message())
                .filter(ControlMessage.RoundEnd.class::isInstance)
                .map(ControlMessage.RoundEnd.class::cast)
                .allMatch(end -> end.finalStandings().stream().allMatch(row -> "UNVERIFIED".equals(row.verifyState()))),
                "final claims must retain the unverified label");
    }

    @Test
    void arenaReleaseStreamsBeforeGoAndHubMediatesSwap() throws Exception {
        FakeHubConnection a = new FakeHubConnection(), b = new FakeHubConnection();
        var ja = admit(a, "A", dir.resolve("a"));
        var jb = admit(b, "B", dir.resolve("b"));
        room.configureTrackIdentity("ARENA","COMPETITION");
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig("s3k", 0, 0, 600,
                "OPEN", null, "ARENA", "COMPETITION", "ITEMS", 60)));
        room.applyTrackValidationProfile(new TrackValidationProfile(512,256,32,60,512,0,5,12));
        now += 9000;
        GhostStreamPublisher pa = new GhostStreamPublisher(packet -> room.onBinary(a,packet));
        GhostStreamPublisher pb = new GhostStreamPublisher(packet -> room.onBinary(b,packet));
        streamAttempt(a, ja.sessionToken(), pa, 1);
        streamAttempt(b, jb.sessionToken(), pb, 1);
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        assertEquals(0, room.hub().observation(ja.playerSlot()).target().progress());
        now += 1000; room.tick();
        // Refresh the streams at GO so neither is stale for partner selection.
        byte[] data = new byte[7];
        com.openggf.net.ghost.GhostFrameCodec.encode(new GhostFrame(102,200,1,false,false,false,2,false),data,0);
        for (FakeHubConnection c : List.of(a,b)) {
            room.onBinary(c,GhostPackets.encodeFrames(1,2,data));
        }
        room.onText(a,ControlCodec.encode(ja.sessionToken(),new ControlMessage.TeleportRequest(1,2)));
        assertEquals(1, room.swapLog().size());
        var command = room.swapLog().getFirst().command();
        assertEquals(ja.playerSlot(),command.slotA()); assertEquals(jb.playerSlot(),command.slotB());
        room.onText(a,ControlCodec.encode(ja.sessionToken(),new ControlMessage.TeleportOutcome(command.swapId(),3,"APPLIED")));
        assertEquals("APPLIED",room.swapLog().getFirst().outcomes().get(ja.playerSlot()).outcome());
        room.onDisconnected(b);
        assertEquals("DNF",room.round().standings().getFirst().outcome());
    }

    @Test
    void unverifiedFinisherOutsidePhysicalCapGetsItsTiedRankPrivately() throws Exception {
        FakeHubConnection runner = new FakeHubConnection();
        var join = admit(runner,"Runner",dir.resolve("runner"));
        room.requestStartRound(new ControlMessage.RoundConfig("s3k",0,0,300,"OPEN",null));
        now += HostRoundEngine.COUNTDOWN_MILLIS; room.tick();
        for (int i=100;i<110;i++) room.round().onAttemptFinish(i,"Rival"+i,"sonic",
                new ControlMessage.AttemptFinish(1,1,0,1,"ab".repeat(32),"cd".repeat(32),null),false);
        var publisher = new GhostStreamPublisher(packet -> room.onBinary(runner,packet));
        streamAttempt(runner,join.sessionToken(),publisher,1);
        room.onText(runner,ControlCodec.encode(join.sessionToken(),new ControlMessage.AttemptFinish(1,1,0,1,
                "ab".repeat(32),HexFormat.of().formatHex(publisher.streamHashSha256()),null)));
        assertTrue(runner.text.stream().map(text -> ControlCodec.decode(text).message())
                .anyMatch(m -> m.equals(new ControlMessage.RankUpdate(1,1,"TOTAL"))));
    }

    @Test
    void malformedRoundIdentityIsRefusedWithoutThrowing() {
        org.junit.jupiter.api.Assertions.assertFalse(room.requestStartRound(new ControlMessage.RoundConfig(
                "INVALID",0,0,300,"OPEN",null)));
    }

    @Test
    void equalCoordinatesDoNotAliasActsAndArenas() {
        var arena = new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null,"ARENA","COMPETITION","OFF",60);
        org.junit.jupiter.api.Assertions.assertFalse(room.requestStartRound(arena));
        room.configureTrackIdentity("ARENA","COMPETITION");
        org.junit.jupiter.api.Assertions.assertFalse(room.requestStartRound(new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null)));
        assertTrue(room.requestStartRound(arena));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->room.configureTrackIdentity("ACT","TIME_ATTACK"));
    }

    @Test
    void failedVerifiedCompetitionClaimKeepsDnfWithoutInvalidPrivateRank() throws Exception {
        var runner = new FakeHubConnection(); var watcher = new FakeHubConnection();
        var join = admit(runner,"Runner",dir.resolve("runner")); admit(watcher,"Watcher",dir.resolve("watcher"));
        room.round().setVerifiedRoom(true);
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null,"ACT","COMPETITION","NORMAL",60)));
        now += 10_000; room.tick();
        var publisher = new GhostStreamPublisher(packet -> room.onBinary(runner,packet));
        streamAttempt(runner,join.sessionToken(),publisher,1);
        room.onText(runner,ControlCodec.encode(join.sessionToken(),new ControlMessage.AttemptFinish(1,1,0,1,
                "ab".repeat(32),HexFormat.of().formatHex(publisher.streamHashSha256()),null)));
        for (int i=0;i<10;i++) {
            String id="seed"+i;
            room.round().register(100+i,id,id,"sonic"); room.round().startAttempt(id,1);
            room.round().onAttemptFinish(100+i,id,null,id,"sonic",new ControlMessage.AttemptFinish(1,2+i,0,2+i,
                    "ab".repeat(32),"cd".repeat(32),null),false);
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->room.round().onVerdict(join.playerSlot(),1,false));
        assertEquals("DNF",room.round().standings().getLast().outcome());
        assertEquals(join.playerSlot(),room.round().standings().getLast().slot());
        assertTrue(runner.text.stream().map(text -> ControlCodec.decode(text).message())
                .noneMatch(m -> m instanceof ControlMessage.RankUpdate r && r.bestTimeFrames() <= 0));
    }

    private void streamAttempt(FakeHubConnection runner, String token,
                               GhostStreamPublisher publisher, int attemptId) {
        room.onText(runner, ControlCodec.encode(token, new ControlMessage.AttemptStart(attemptId)));
        publisher.beginAttempt(attemptId);
        publisher.onFrame(new GhostFrame(100, 200, 1, false, false, false, 2, false));
        publisher.onFrame(new GhostFrame(101, 200, 1, false, false, false, 2, false));
        publisher.finishAttempt();
    }

    private int statusAfterRoster(FakeHubConnection watcher, int slot) {
        for (int tick = 0; tick < GhostHub.ROSTER_INTERVAL_TICKS; tick++) {
            room.tick();
        }
        List<GhostPackets.RosterEntry> roster = new ArrayList<>();
        for (byte[] packet : watcher.binary) {
            if ((packet[0] & 0xFF) == GhostPackets.TYPE_ROSTER) {
                roster = GhostPackets.decodeRoster(packet);
            }
        }
        return roster.stream().filter(entry -> entry.playerSlot() == slot)
                .findFirst().orElseThrow().status();
    }

    private ControlMessage.JoinAccepted admit(FakeHubConnection connection, String name,
                                               Path identityDir) throws Exception {
        ClientHandshake handshake = new ClientHandshake(
                PlayerIdentity.loadOrCreate(identityDir), name, FP);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) ControlCodec.decode(
                connection.text.get(connection.text.size() - 1)).message();
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        return (ControlMessage.JoinAccepted) connection.text.stream()
                .map(text -> ControlCodec.decode(text).message())
                .filter(message -> message instanceof ControlMessage.JoinAccepted)
                .findFirst().orElseThrow();
    }
}
