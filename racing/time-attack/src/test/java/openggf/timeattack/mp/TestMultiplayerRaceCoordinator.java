package openggf.timeattack.mp;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunInput;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunStep;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import openggf.timeattack.GhostStore;
import openggf.timeattack.TimeAttackLaunchRequest;
import openggf.timeattack.TimeAttackRuntime;
import openggf.timeattack.TimeAttackRuntimeTestBridge;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMultiplayerRaceCoordinator {
    private static final class FakeTransport implements RaceTransport {
        final ArrayDeque<RaceClient.InboundEvent> inbound = new ArrayDeque<>();
        final List<ControlMessage> sentControl = new ArrayList<>();
        final List<byte[]> sentBinary = new ArrayList<>();
        /** Attempt lifecycle and frame batches in send order. */
        final List<String> wire = new ArrayList<>();
        boolean open = true;

        @Override public List<RaceClient.InboundEvent> drainInbound() {
            List<RaceClient.InboundEvent> events = new ArrayList<>(inbound);
            inbound.clear();
            return events;
        }
        @Override public void sendControl(ControlMessage message) {
            sentControl.add(message);
            switch (message) {
                case ControlMessage.AttemptStart start -> wire.add("start " + start.attemptId());
                case ControlMessage.AttemptFinish finish -> wire.add("finish " + finish.attemptId());
                case ControlMessage.AttemptReset reset -> wire.add("reset " + reset.attemptId());
                default -> { }
            }
        }
        @Override public void sendBinary(byte[] data) {
            sentBinary.add(data.clone());
            GhostPackets.FramesBatch batch = GhostPackets.decodeFrames(data);
            wire.add("frames " + batch.attemptId() + "@" + batch.startFrameIndex()
                    + "+" + batch.frameCount());
        }
        @Override public int playerSlot() { return 0; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }

    private long now = 1_000_000;

    private record Rig(FakeTransport transport, ClientRaceSession session,
                       TimeAttackRuntime runtime, MultiplayerRaceCoordinator coordinator) {
    }

    private Rig rig(Path root, boolean attach) {
        FakeTransport transport = new FakeTransport();
        ClientRaceSession session = new ClientRaceSession(() -> now);
        session.applyJoin(new ControlMessage.JoinAccepted("tok", 0,
                new ControlMessage.RoomDescriptor(
                        "LAN", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        session.onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null),
                now, now + 60_000));
        TimeAttackRuntime runtime = armedRuntime(root);
        MultiplayerRaceCoordinator coordinator =
                new MultiplayerRaceCoordinator(transport, session, () -> now);
        if (attach) {
            coordinator.attachRuntime(runtime);
        }
        return new Rig(transport, session, runtime, coordinator);
    }

    private Rig rig(Path root) {
        return rig(root, true);
    }

    private static TimeAttackRuntime armedRuntime(Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(
                new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest(
                "s3k", 0, 0, "sonic", List.of()));
        return runtime;
    }

    private static GhostFrame frame(int x) {
        return new GhostFrame(x, 100, 1, false, false, false, 2, false);
    }

    /** Counts the run commands the coordinator issues through the runtime. */
    private static final class CountingHandle implements RunHandle {
        int retries;
        int leaves;

        @Override public void retry() { retries++; }
        @Override public void leave() { leaves++; }
        @Override public void spectate(boolean active, int dx, int dy) { }
        @Override public boolean isActive() { return true; }
    }

    private static CountingHandle handle(Rig rig) {
        CountingHandle handle = new CountingHandle();
        rig.runtime().attachHandle(handle);
        rig.runtime().setRetryKey(() -> SceneKeys.R);
        return handle;
    }

    /** The engine reloading the act (launch, retry key or act-completion retry). */
    private static void levelReady(TimeAttackRuntime runtime) {
        runtime.onLevelReady(new RunLevelStart(runtime.runSpec(), "0.6:cafe", false, 4096, 1024, false));
    }

    private static RunInput keys(int... pressed) {
        return new RunInput() {
            @Override public LogicalInputSnapshot input() { return LogicalInputSnapshot.neutral(); }
            @Override public boolean keyDown(int key) { return keyPressed(key); }
            @Override public boolean keyPressed(int key) {
                for (int candidate : pressed) {
                    if (candidate == key) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /** One engine frame: admit, then (when admitted) the step and its sampled frame. */
    private static boolean step(Rig rig, RunInput input, int heldMask, boolean actComplete, int x) {
        if (!rig.runtime().admitStep(input)) {
            return false;
        }
        rig.runtime().afterStep(new RunStep(0, heldMask, false,
                new PlayerPose(x, 100, 1, false, false, 2, false), actComplete, -1, false));
        return true;
    }

    /** A spawn frame, an input frame and a finish: one full three-frame batch. */
    private static void runToFinish(Rig rig, int x) {
        assertTrue(step(rig, keys(), 0, false, x));
        assertTrue(step(rig, keys(), 0x08, false, x + 1));
        assertTrue(step(rig, keys(), 0x08, true, x + 2));
        assertTrue(rig.runtime().isAttemptFinished());
    }

    @Test
    void attemptLifecyclePublishesEveryFrameAndHashes(@TempDir Path root) {
        Rig rig = rig(root);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        for (int i = 0; i < 4; i++) {
            TimeAttackRuntimeTestBridge.tick(
                    rig.runtime(), i == 0 ? 0 : 0x08, i == 3, frame(10 + i));
        }
        assertEquals(2, rig.transport().sentBinary.size());
        assertEquals(1, GhostPackets.decodeFrames(
                rig.transport().sentBinary.get(1)).frameCount());
        ControlMessage.AttemptFinish finish = rig.transport().sentControl.stream()
                .filter(ControlMessage.AttemptFinish.class::isInstance)
                .map(ControlMessage.AttemptFinish.class::cast).findFirst().orElseThrow();
        assertEquals(64, finish.inputRecordingHashHex().length());
        assertEquals(64, finish.ghostStreamHashHex().length());
    }

    @Test
    void attachingAfterLevelReadyRecoversTheActiveAttempt(@TempDir Path root) {
        Rig rig = rig(root, false);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        assertTrue(rig.transport().sentControl.stream()
                .noneMatch(ControlMessage.AttemptStart.class::isInstance));

        rig.coordinator().attachRuntime(rig.runtime());

        assertTrue(rig.transport().wire.isEmpty(), "the start waits for the first sampled frame");
        for (int frame = 0; frame < 3; frame++) {
            TimeAttackRuntimeTestBridge.tick(
                    rig.runtime(), 0, false, frame(10 + frame));
        }
        assertEquals(List.of("start 1", "frames 1@0+3"), rig.transport().wire);
    }

    @Test
    void countdownDefersAttemptStartAndPacingUntilRunning(@TempDir Path root) {
        Rig rig = rig(root);
        ControlMessage.RoundConfig config = new ControlMessage.RoundConfig(
                "s3k", 0, 0, 60, "OPEN", null);
        rig.session().onControl(new ControlMessage.RoundStart(
                config, now + 3_000, now + 63_000));

        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        assertTrue(rig.transport().sentControl.stream()
                .noneMatch(ControlMessage.AttemptStart.class::isInstance));

        assertFalse(step(rig, keys(), 0, false, 10), "the countdown holds the run");
        assertEquals(3, rig.coordinator().countdownDigit(), "the digit the HUD draws");
        now += 1_000;
        assertEquals(2, rig.coordinator().countdownDigit());
        now += 1_999;
        assertEquals(1, rig.coordinator().countdownDigit(), "1 until the count runs out");

        now += 1;
        assertEquals(0, rig.coordinator().countdownDigit(), "no digit once the round is running");
        rig.coordinator().pump();
        assertTrue(rig.transport().wire.isEmpty(), "RUNNING alone does not start the attempt");
        for (int frame = 0; frame < 3; frame++) {
            assertTrue(step(rig, keys(), 0, false, 10 + frame));
        }
        assertEquals(List.of("start 1", "frames 1@0+3"), rig.transport().wire);
    }

    @Test
    void deadlineVoidsArmedAttemptAndSendsReset(@TempDir Path root) {
        Rig rig = rig(root);
        ControlMessage.RoundConfig config = new ControlMessage.RoundConfig(
                "s3k", 0, 0, 10, "OPEN", null);
        rig.session().onControl(new ControlMessage.RoundStart(config, now, now + 10_000));
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        TimeAttackRuntimeTestBridge.tick(rig.runtime(), 0, false, frame(10));
        now += 10_001;
        rig.coordinator().afterLevelFrame();
        assertFalse(rig.runtime().isAttemptActive());
        assertTrue(rig.transport().sentControl.stream()
                .anyMatch(ControlMessage.AttemptReset.class::isInstance));
    }

    @Test
    void lobbyPumpProcessesRosterChatAndPingsWithoutRuntime(@TempDir Path root) {
        Rig rig = rig(root, false);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic"),
                new ControlMessage.PlayerInfo(1, "fp1", "PEER", "tails")))));
        rig.transport().inbound.add(new RaceClient.Control(
                new ControlMessage.ChatBroadcast(1, "PEER", "gl hf")));
        rig.coordinator().pump();
        rig.coordinator().pump();
        assertEquals(2, rig.session().players().size());
        assertEquals(List.of("PEER: gl hf"), rig.session().chatLines());
        assertEquals(1, rig.transport().sentControl.stream()
                .filter(ControlMessage.Ping.class::isInstance).count());
        assertFalse(rig.coordinator().holdGameplay());
    }

    @Test
    void aggregateBecomesRemoteGhost(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic"),
                new ControlMessage.PlayerInfo(3, "fp3", "PEER", "tails")))));
        feedPeerFrames(rig, 1, 200);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(1, rig.coordinator().remoteActiveGhosts().size());
        assertEquals("net:3", rig.coordinator().remoteActiveGhosts().get(0).slotId());
        assertEquals("tails", rig.coordinator().remoteActiveGhosts().get(0).characterCode());
    }

    @Test
    void aHurtPeersMarkerFlashesWhileHurtAndBrieflyAfter(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic"),
                new ControlMessage.PlayerInfo(3, "fp3", "PEER", "tails")))));
        feedPeerFrames(rig, 1, 200);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(0, rig.coordinator().hudState().hurtFlash(3), "not hurt");

        for (int i = 12; i < 24; i += 3) {
            byte[] data = new byte[3 * GhostFrameCodec.BYTES];
            for (int k = 0; k < 3; k++) {
                GhostFrameCodec.encode(new GhostFrame(210, 100, 26, false, false, false, 2, false, true),
                        data, k * GhostFrameCodec.BYTES);
            }
            rig.transport().inbound.add(new RaceClient.GhostData(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(3, 1, i, 3, data)))));
        }
        rig.coordinator().pump();
        int flashed = 0;
        for (int frame = 0; frame < 30 && flashed == 0; frame++) {
            rig.coordinator().afterLevelFrame();
            flashed = rig.coordinator().hudState().hurtFlash(3);
        }
        assertTrue(flashed > 0, "the peer's hurt frames start its flash");
        int tick = rig.coordinator().hudState().tick();
        for (int frame = 0; frame < 200; frame++) rig.coordinator().afterLevelFrame();
        assertEquals(0, rig.coordinator().hudState().hurtFlash(3), "the flash ends once the hits stop");
        assertTrue(rig.coordinator().hudState().tick() > tick, "the flash clock runs every level frame");
    }

    @Test
    void membershipDeltasReachBothSessionAndRemotePlayback(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic")))));
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.PlayerUpsert(
                new ControlMessage.PlayerInfo(3, "fp3", "PEER", "tails"))));
        feedPeerFrames(rig, 1, 200);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(2, rig.session().players().size());
        assertEquals(1, rig.coordinator().remoteActiveGhosts().size());
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.PlayerLeft(3)));
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(1, rig.session().players().size());
        assertTrue(rig.coordinator().remoteActiveGhosts().isEmpty());
    }

    @Test
    void countdownHoldsOnlyAttachedRuntime(@TempDir Path root) {
        Rig rig = rig(root);
        ControlMessage.RoundConfig config = new ControlMessage.RoundConfig(
                "s3k", 0, 0, 60, "OPEN", null);
        rig.session().onControl(new ControlMessage.RoundStart(config, now + 3000, now + 63_000));
        assertTrue(rig.coordinator().holdGameplay());
        now += 3000;
        assertFalse(rig.coordinator().holdGameplay());
        rig.coordinator().detachRuntime();
        assertFalse(rig.coordinator().holdGameplay());
    }

    @Test
    void disconnectReflectedInHud(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Disconnected("host gone"));
        rig.coordinator().pump();
        assertTrue(rig.coordinator().hudState().connectionLost());
    }

    @Test
    void pushedOutOfTheTopTenClearsTheStaleRankUntilTheHostReportsIt(@TempDir Path root) {
        Rig rig = rig(root, false);
        List<ControlMessage.StandingsRow> top = new ArrayList<>();
        for (int rank = 1; rank <= 10; rank++) {
            // The local player (slot 0) holds tenth place.
            top.add(new ControlMessage.StandingsRow(rank == 10 ? 0 : rank, "P" + rank,
                    "sonic", 1000 + rank, rank, "NONE"));
        }
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.StandingsDelta(top)));
        rig.coordinator().pump();
        assertEquals(10, rig.coordinator().hudState().localRank());

        // A faster retry by another player enters the top ten and pushes slot 0 out.
        List<ControlMessage.StandingsRow> pushed = new ArrayList<>();
        pushed.add(new ControlMessage.StandingsRow(42, "FAST", "sonic", 900, 1, "NONE"));
        for (int rank = 2; rank <= 10; rank++) {
            pushed.add(new ControlMessage.StandingsRow(rank - 1, "P" + (rank - 1),
                    "sonic", 1000 + rank - 1, rank, "NONE"));
        }
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.StandingsDelta(pushed)));
        rig.coordinator().pump();
        assertEquals(-1, rig.coordinator().hudState().localRank(),
                "the old top-ten rank is no longer true");

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RankUpdate(11, 1010)));
        rig.coordinator().pump();
        assertEquals(11, rig.coordinator().hudState().localRank());
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.StandingsPageRequest(1)));
    }

    @Test
    void rankOutsideBroadcastCapRequestsAndSplicesAroundYouPage(@TempDir Path root) {
        Rig rig = rig(root, false);
        List<ControlMessage.StandingsRow> top = new ArrayList<>();
        for (int rank = 1; rank <= 10; rank++) {
            top.add(new ControlMessage.StandingsRow(rank, "P" + rank,
                    "sonic", 1000 + rank, rank, "NONE"));
        }
        rig.transport().inbound.add(new RaceClient.Control(
                new ControlMessage.StandingsDelta(top)));
        rig.transport().inbound.add(new RaceClient.Control(
                new ControlMessage.RankUpdate(16, 2000)));
        rig.coordinator().pump();
        assertTrue(rig.transport().sentControl.stream()
                .anyMatch(message -> message.equals(new ControlMessage.StandingsPageRequest(1))));

        List<ControlMessage.StandingsRow> page = new ArrayList<>();
        for (int rank = 11; rank <= 20; rank++) {
            int slot = rank == 16 ? 0 : rank;
            page.add(new ControlMessage.StandingsRow(slot, "P" + rank,
                    "sonic", 1000 + rank, rank, "NONE"));
        }
        rig.transport().inbound.add(new RaceClient.Control(
                new ControlMessage.StandingsPage(page, 1, 3)));
        rig.coordinator().pump();
        assertEquals(15, rig.coordinator().hudState().standings().size());
        assertTrue(rig.coordinator().hudState().standings().stream()
                .anyMatch(row -> row.slot() == 0 && row.rank() == 16));
    }

    @Test
    void roundStartResetsRemoteAttemptIds(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(3, "fp3", "PEER", "tails")))));
        feedPeerFrames(rig, 5, 500);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(500, rig.coordinator().remoteActiveGhosts().get(0).pose().centreX());

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null),
                now, now + 60_000)));
        rig.coordinator().pump();
        feedPeerFrames(rig, 1, 100);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(100, rig.coordinator().remoteActiveGhosts().get(0).pose().centreX());
    }

    @Test
    void detachKeepsSocketAndNextRoundRestartsAttemptIds(@TempDir Path root) {
        Rig rig = rig(root);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        TimeAttackRuntimeTestBridge.tick(rig.runtime(), 0x08, true, frame(10));
        rig.coordinator().detachRuntime();
        assertTrue(rig.transport().open);

        TimeAttackRuntime second = armedRuntime(root.resolve("round2"));
        rig.coordinator().attachRuntime(second);
        TimeAttackRuntimeTestBridge.begin(second, "0.6:cafe");
        TimeAttackRuntimeTestBridge.tick(second, 0, false, frame(10));
        assertEquals(List.of(new ControlMessage.AttemptStart(1), new ControlMessage.AttemptStart(1)),
                rig.transport().sentControl.stream()
                        .filter(ControlMessage.AttemptStart.class::isInstance).toList());
        rig.coordinator().shutdown();
        assertFalse(rig.transport().open);
    }

    @Test
    void retryAfterAFinishStartsTheNextAttemptAtItsFirstSampledFrame(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        runToFinish(rig, 10);
        assertEquals(List.of("start 1", "frames 1@0+3", "finish 1"), rig.transport().wire);

        assertTrue(rig.runtime().retryOnActCompletion(), "the open window keeps the run going");
        levelReady(rig.runtime()); // the engine restarts the act after the results tally
        now += 2_500;              // fade-in and title card: no frames are sampled
        rig.coordinator().pump();
        assertEquals(3, rig.transport().wire.size(), "no start before the first sampled frame");

        runToFinish(rig, 20);
        assertEquals(List.of("start 1", "frames 1@0+3", "finish 1",
                "start 2", "frames 2@0+3", "finish 2"), rig.transport().wire);
        assertEquals(0, handle.leaves);
    }

    @Test
    void retryKeyMidRunResetsThenStartsTheNextAttempt(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0, false, 10));
        assertTrue(step(rig, keys(), 0x08, false, 11));

        assertTrue(rig.coordinator().mayRetry());
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.R)));
        assertEquals(1, handle.retries);
        assertEquals(List.of("start 1", "reset 1"), rig.transport().wire);

        levelReady(rig.runtime());
        for (int frame = 0; frame < 3; frame++) {
            assertTrue(step(rig, keys(), 0, false, 30 + frame));
        }
        assertEquals(List.of("start 1", "reset 1", "start 2", "frames 2@0+3"),
                rig.transport().wire);
    }

    @Test
    void closedWindowIgnoresRetryAndDropsADeferredAttemptsFrame(@TempDir Path root) {
        Rig rig = rig(root);
        rig.session().onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 10, "OPEN", null), now, now + 10_000));
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0, false, 10));
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.R)));
        assertEquals(1, handle.retries);

        now += 10_001; // the deadline passes during the reload
        levelReady(rig.runtime());
        assertFalse(rig.coordinator().mayRetry());
        assertDoesNotThrow(() -> rig.runtime().afterStep(new RunStep(0, 0, false,
                new PlayerPose(10, 100, 1, false, false, 2, false), false, -1, false)));
        assertFalse(rig.runtime().isAttemptActive(), "the deferred attempt is voided");
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.R)));
        assertEquals(1, handle.retries, "a closed window ignores the retry key");
        assertEquals(List.of("start 1", "reset 1"), rig.transport().wire,
                "the deferred attempt never reaches the wire");
        assertFalse(rig.runtime().retryOnActCompletion());
    }

    @Test
    void attemptBeganWhilePublishedResetsTheOldAttemptFirst(@TempDir Path root) {
        Rig rig = rig(root);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        TimeAttackRuntimeTestBridge.tick(rig.runtime(), 0x08, false, frame(10));

        // A reload that skipped the void: the next attempt begins over a published one.
        assertDoesNotThrow(() -> TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe"));
        assertEquals(List.of("start 1", "reset 1"), rig.transport().wire);
        for (int frame = 0; frame < 3; frame++) {
            TimeAttackRuntimeTestBridge.tick(rig.runtime(), 0, false, frame(20 + frame));
        }
        assertEquals(List.of("start 1", "reset 1", "start 2", "frames 2@0+3"),
                rig.transport().wire);
    }

    @Test
    void frameForAnAttemptThatNeverBeganStillThrows(@TempDir Path root) {
        Rig rig = rig(root);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        assertThrows(IllegalStateException.class,
                () -> rig.coordinator().onFrameSampled(7, frame(10)));
    }

    @Test
    void roundEndLeavesAnUnfinishedRunExactlyOnce(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0x08, false, 10));
        assertEquals(0, handle.leaves);

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundEnd(List.of())));
        rig.runtime().admitStep(keys());
        rig.runtime().admitStep(keys());
        assertEquals(1, handle.leaves);
        assertFalse(rig.coordinator().mayRetry());
        assertFalse(rig.runtime().retryOnActCompletion());

        rig.runtime().admitStep(keys(SceneKeys.R));
        assertEquals(0, handle.retries, "a run that is leaving does not retry");
        assertEquals(1, handle.leaves);
    }

    @Test
    void aNewerRoundLeavesTheOldRoundsRun(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0, false, 10));

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 1, 0, 60, "OPEN", null),
                now + 3_000, now + 63_000)));
        rig.runtime().admitStep(keys(SceneKeys.R));
        assertEquals(1, handle.leaves);
        assertEquals(0, handle.retries, "the newer round's countdown is not this run's");
        assertFalse(rig.coordinator().mayRetry());
        now += 3_000;
        assertFalse(rig.runtime().retryOnActCompletion(), "never retry onto the old track");
        assertTrue(rig.coordinator().holdGameplay(), "a leaving run stays held");
    }

    @Test
    void aLeavingRunNeverStepsIntoAnAlreadyRunningNewerRound(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0, false, 10));
        assertTrue(step(rig, keys(), 0x08, false, 11));

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 1, 0, 60, "OPEN", null), now, now + 60_000)));
        assertFalse(step(rig, keys(), 0x08, false, 12), "the leave frame does not step");
        assertEquals(1, handle.leaves);
        assertEquals(List.of("start 1"), rig.transport().wire,
                "no frame from the old track reaches the newer round");
    }

    @Test
    void aNewerRoundsCountdownNeverFreezesAFinishedTally(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        runToFinish(rig, 10);

        // Without a vote pool the host starts the next round straight from ROUND_END.
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundEnd(List.of())));
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 1, 0, 60, "OPEN", null),
                now + 3_000, now + 63_000)));
        assertTrue(step(rig, keys(SceneKeys.R), 0, false, 13), "the results tally keeps running");
        assertFalse(rig.coordinator().holdGameplay());
        assertEquals(0, handle.leaves, "a finished tally is not left");
        assertEquals(0, handle.retries, "the newer round's countdown is not this run's");
        assertFalse(rig.runtime().retryOnActCompletion(), "the tally's exit ends the run");
        assertEquals(List.of("start 1", "frames 1@0+3", "finish 1"), rig.transport().wire);
    }

    @Test
    void theEstimatedDeadlineVoidsTheAttemptButOnlyTheHostEndsTheRun(@TempDir Path root) {
        Rig rig = rig(root);
        rig.session().onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 10, "OPEN", null), now, now + 10_000));
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        assertTrue(step(rig, keys(), 0x08, false, 10));

        now += 10_001; // the client's estimate passes the deadline before the host closes the round
        assertTrue(step(rig, keys(), 0x08, false, 11));
        assertFalse(rig.runtime().isAttemptActive(), "the estimated deadline voids the attempt");
        assertEquals(0, handle.leaves, "an estimate alone never leaves the run");

        // A clock sample shows the hub 5 s behind this client: the window is open after all.
        rig.transport().inbound.add(new RaceClient.Control(
                new ControlMessage.Pong(now, now - 5_000)));
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.R)));
        assertEquals(1, handle.retries, "the corrected window takes a retry");
        assertEquals(0, handle.leaves);

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundEnd(List.of())));
        rig.runtime().admitStep(keys());
        assertEquals(1, handle.leaves, "the host's RoundEnd leaves the run");
    }

    @Test
    void aVoteOfferOrResultAlsoLeavesAnUnfinishedRun(@TempDir Path root) {
        for (ControlMessage closing : List.of(
                new ControlMessage.TrackVoteOffer(List.of("s3k:0:0", "s3k:1:0"), now + 15_000),
                new ControlMessage.TrackVoteResult("s3k:1:0"))) {
            String name = closing.getClass().getSimpleName();
            Rig rig = rig(root.resolve(name));
            CountingHandle handle = handle(rig);
            levelReady(rig.runtime());
            assertTrue(step(rig, keys(), 0x08, false, 10));

            rig.transport().inbound.add(new RaceClient.Control(closing));
            rig.runtime().admitStep(keys());
            rig.runtime().admitStep(keys());
            assertEquals(1, handle.leaves, name);
        }
    }

    @Test
    void anAttemptFinishedOnlyOutsideTheWindowNeverReachesTheWire(@TempDir Path root) {
        Rig rig = rig(root);
        TimeAttackRuntimeTestBridge.begin(rig.runtime(), "0.6:cafe");
        now += 60_001; // the deadline passes before the attempt's first sampled frame

        assertDoesNotThrow(() -> TimeAttackRuntimeTestBridge.tick(
                rig.runtime(), 0x08, true, frame(10)));
        assertTrue(rig.runtime().isAttemptFinished(), "the finish frame itself was dropped");
        assertTrue(rig.transport().wire.isEmpty(), "the host never saw this attempt");
        assertThrows(IllegalStateException.class,
                () -> rig.coordinator().onFrameSampled(1, frame(11)),
                "the dropped attempt is no longer pending");
    }

    @Test
    void roundEndKeepsAFinishedTallyAndEndsTheRunWhenItExits(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        levelReady(rig.runtime());
        runToFinish(rig, 10);

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundEnd(List.of())));
        rig.runtime().admitStep(keys());
        rig.runtime().admitStep(keys());
        assertEquals(0, handle.leaves, "the results tally stays on screen");
        assertFalse(rig.runtime().retryOnActCompletion(), "the tally's exit ends the run");
    }

    @Test
    void retryOnActCompletionFollowsTheWindowAndTheAttachment(@TempDir Path root) {
        Rig rig = rig(root);
        rig.session().onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 10, "OPEN", null), now, now + 10_000));
        assertTrue(rig.runtime().retryOnActCompletion());
        now += 10_001;
        assertFalse(rig.runtime().retryOnActCompletion());

        Rig solo = rig(root.resolve("solo"), false);
        assertFalse(solo.runtime().retryOnActCompletion(), "solo runs return to their host");
        rig.coordinator().detachRuntime();
        now -= 10_001;
        assertFalse(rig.runtime().retryOnActCompletion());
    }

    private static void feedPeerFrames(Rig rig, int attemptId, int x) {
        for (int i = 0; i < 12; i += 3) {
            byte[] data = new byte[3 * GhostFrameCodec.BYTES];
            for (int k = 0; k < 3; k++) {
                GhostFrameCodec.encode(frame(x), data, k * GhostFrameCodec.BYTES);
            }
            rig.transport().inbound.add(new RaceClient.GhostData(
                    new GhostPackets.Aggregate(i, List.of(
                            new GhostPackets.AggregateEntry(3, attemptId, i, 3, data)))));
        }
    }
    @Test
    void controllerQuickChatUsesUnusedControlsWhileMovementJumpAndTheRaceContinue(@TempDir Path root) {
        Rig rig = rig(root);
        handle(rig);
        levelReady(rig.runtime());
        ControllerInput input = new ControllerInput();
        input.logical = LogicalInputSnapshot.ofPlayers(com.openggf.control.PlayerInputState.of(0x08, 0,
                com.openggf.control.InputActionMasks.ACTION_A, 0, false, false),
                com.openggf.control.PlayerInputState.neutral());
        int held = input.logical.player1().heldMask();
        assertTrue(step(rig, input, held, false, 100)); // Baseline the connected pad.
        int before = rig.runtime().hudState().elapsedDisplayFrames();
        input.click = true;
        assertTrue(step(rig, input, held, false, 101));
        assertTrue(rig.coordinator().isQuickChatOpen());
        input.click = false;
        input.stickY = 1;
        assertTrue(step(rig, input, held, false, 102));
        input.trigger = 1;
        assertTrue(step(rig, input, held, false, 103));
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("THATS_NO_GOOD")));
        assertEquals(held, input.input().player1().heldMask());
        assertTrue(input.input().player1().actionHeldMask() != 0);
        assertFalse(rig.runtime().holdsPlayerInput());
        assertTrue(rig.runtime().hudState().elapsedDisplayFrames() > before);
    }

    @Test
    void controllerQuickChatBaselinesConnectionsAndDoesNotSendHeldTriggersOrStealFaceButtons(@TempDir Path root) {
        Rig rig = rig(root);
        ControllerInput input = new ControllerInput();
        input.click = true;
        input.trigger = 1;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen(), "connecting with controls held is not a press");
        input.click = false;
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        for (int i = 0; i < 30; i++) rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen(), "held click/trigger must not toggle or send");
        assertFalse(rig.transport().sentControl.stream().anyMatch(ControlMessage.QuickChat.class::isInstance));
        input.click = false;
        input.trigger = -1;
        input.faceButtons = true;
        input.stickY = -1;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen(), "movement and jump buttons belong to gameplay");
        input.trigger = 1;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertEquals(1, rig.transport().sentControl.stream().filter(ControlMessage.QuickChat.class::isInstance).count());
    }

    @Test
    void controllerChooserClosesOnDisconnectOffAndRetryWithoutReopeningFromHeldInput(@TempDir Path root) {
        Rig rig = rig(root);
        ControllerInput input = new ControllerInput();
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        input.connected = false;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        input.connected = true;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        input.click = false;
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        rig.session().onControl(new ControlMessage.ChatModeState("OFF"));
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        rig.session().onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen(), "changing policy does not create a fresh click");
        input.click = false;
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        levelReady(rig.runtime());
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen(), "retry must not turn a held click into a new menu");
    }

    private static final class ControllerInput implements RunInput {
        boolean connected = true, click, faceButtons, retry, resumed;
        float stickX, stickY, trigger = -1;
        LogicalInputSnapshot logical = LogicalInputSnapshot.neutral();
        @Override public LogicalInputSnapshot input() { return logical; }
        @Override public boolean keyDown(int key) { return keyPressed(key); }
        @Override public boolean keyPressed(int key) { return retry && key == SceneKeys.R; }
        @Override public boolean resumedAfterPause() { return resumed; }
        @Override public com.openggf.control.PhysicalInput physicalInput() {
            boolean[] buttons = new boolean[15];
            buttons[com.openggf.control.PhysicalGamepad.BUTTON_RIGHT_THUMB] = click;
            buttons[com.openggf.control.PhysicalGamepad.BUTTON_A] = faceButtons;
            float[] axes = {0, 0, stickX, stickY, -1, trigger};
            var pad = new com.openggf.control.PhysicalGamepad(0, "Xbox", buttons, axes);
            return new com.openggf.control.PhysicalInput(0, List.of(), connected ? List.of(pad) : List.of(), List.of(), 0);
        }
    }

    @Test
    void controllerControlsHeldDuringPauseAreBaselinedBeforeAcceptingActions(@TempDir Path root) {
        Rig rig = rig(root);
        ControllerInput input = new ControllerInput();
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        input.click = false;
        input.trigger = 1; // Pressed while paused; there were no host polls then.
        input.resumed = true;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertFalse(rig.transport().sentControl.stream().anyMatch(ControlMessage.QuickChat.class::isInstance));
        input.resumed = false;
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        assertTrue(rig.coordinator().isQuickChatOpen());
        input.resumed = true; // A second pause with the stick click held also cannot reopen it.
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        input.resumed = false;
        rig.coordinator().pollLocalInput(input);
        assertFalse(rig.coordinator().isQuickChatOpen());
        input.click = false;
        input.trigger = -1;
        rig.coordinator().pollLocalInput(input);
        input.click = true;
        rig.coordinator().pollLocalInput(input);
        input.click = false;
        input.stickY = -1;
        input.trigger = 1;
        rig.coordinator().pollLocalInput(input);
        assertEquals(1, rig.transport().sentControl.stream().filter(ControlMessage.QuickChat.class::isInstance).count());
    }

    @Test
    void controllerQuickChatDoesNotSwallowAnIndependentKeyboardRetry(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        ControllerInput input = new ControllerInput();
        rig.runtime().admitStep(input);
        input.click = true;
        input.retry = true;
        rig.runtime().admitStep(input);
        assertEquals(1, handle.retries);
        assertFalse(rig.coordinator().consumesRetryKey(), "only keyboard chat collisions consume a retry edge");
    }

    @Test
    void controllerQuickChatRemainsAvailableWithoutReleasingTheCountdown(@TempDir Path root) {
        Rig rig = rig(root, false);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null), now + 3000, now + 63_000)));
        rig.coordinator().pump();
        rig.coordinator().attachRuntime(rig.runtime());
        ControllerInput input = new ControllerInput();
        assertFalse(rig.coordinator().admit(input));
        input.click = true;
        assertFalse(rig.coordinator().admit(input));
        assertTrue(rig.coordinator().isQuickChatOpen());
        input.click = false;
        input.stickY = -1;
        input.trigger = 1;
        assertFalse(rig.coordinator().admit(input));
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertTrue(rig.coordinator().holdGameplay());
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("BRUTAL")));
    }

    @Test
    void quickChatPreservesHeldMovementAndJumpWhileGameplayContinues(@TempDir Path root) {
        Rig rig = rig(root);
        handle(rig);
        levelReady(rig.runtime());
        var player = com.openggf.control.PlayerInputState.of(0x08, 0,
                com.openggf.control.InputActionMasks.ACTION_A, 0, false, false);
        var logical = LogicalInputSnapshot.ofPlayers(player, com.openggf.control.PlayerInputState.neutral());
        var pressed = new java.util.concurrent.atomic.AtomicInteger(SceneKeys.Q);
        RunInput input = new RunInput() {
            @Override public LogicalInputSnapshot input() { return logical; }
            @Override public boolean keyDown(int key) { return key == pressed.get(); }
            @Override public boolean keyPressed(int key) { return key == pressed.get(); }
        };
        int before = rig.runtime().hudState().elapsedDisplayFrames();
        assertTrue(step(rig, input, player.heldMask(), false, 100));
        assertTrue(rig.coordinator().isQuickChatOpen());
        assertFalse(rig.runtime().holdsPlayerInput());
        pressed.set(SceneKeys.DIGIT_1);
        assertTrue(step(rig, input, player.heldMask(), false, 101));
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertSame(logical, input.input());
        assertEquals(player, input.input().player1());
        assertFalse(rig.runtime().holdsPlayerInput());
        assertTrue(rig.runtime().hudState().elapsedDisplayFrames() > before);
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("BRUTAL")));
    }

    @Test
    void quickChatNumberEdgesDoNotPauseOrTriggerTheSameRetryBinding(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        rig.runtime().setRetryKey(() -> SceneKeys.DIGIT_1);
        levelReady(rig.runtime());
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.Q)));
        assertTrue(rig.coordinator().isQuickChatOpen());
        assertFalse(rig.coordinator().holdGameplay());
        rig.runtime().admitStep(keys(SceneKeys.DIGIT_1));
        assertEquals(0, handle.retries);
        assertFalse(rig.coordinator().isQuickChatOpen());
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("BRUTAL")));
        rig.runtime().admitStep(keys(SceneKeys.DIGIT_1));
        assertEquals(1, handle.retries, "the ordinary retry edge still works once the chooser closes");
    }

    @Test
    void retryOnQAdvertisesAndUsesPAndHeldToggleDoesNotRepeat(@TempDir Path root) {
        Rig rig = rig(root);
        CountingHandle handle = handle(rig);
        rig.runtime().setRetryKey(() -> SceneKeys.Q);
        assertEquals("P", rig.coordinator().quickChatKeyLabel());
        assertTrue(rig.runtime().admitStep(keys(SceneKeys.P)));
        assertTrue(rig.coordinator().isQuickChatOpen());
        RunInput held = new RunInput() {
            @Override public LogicalInputSnapshot input() { return LogicalInputSnapshot.neutral(); }
            @Override public boolean keyDown(int key) { return key == SceneKeys.P; }
            @Override public boolean keyPressed(int key) { return false; }
        };
        for (int i = 0; i < 5; i++) rig.runtime().admitStep(held);
        assertTrue(rig.coordinator().isQuickChatOpen());
        assertEquals(0, handle.retries);
        rig.runtime().admitStep(keys(SceneKeys.P));
        assertFalse(rig.coordinator().isQuickChatOpen());
    }

    @Test
    void chooserDoesNotReleaseCountdownAndClosesOnModeRetryAndDetach(@TempDir Path root) {
        Rig rig = rig(root, false);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null), now + 3000, now + 63_000)));
        rig.coordinator().pump();
        rig.coordinator().attachRuntime(rig.runtime());
        assertFalse(rig.coordinator().admit(keys(SceneKeys.Q)));
        assertTrue(rig.coordinator().isQuickChatOpen());
        assertTrue(rig.coordinator().holdGameplay());
        assertFalse(rig.coordinator().admit(keys(SceneKeys.DIGIT_7)));
        assertTrue(rig.coordinator().holdGameplay());
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("GOOD_LUCK")));
        rig.coordinator().admit(keys(SceneKeys.Q));
        rig.session().onControl(new ControlMessage.ChatModeState("OFF"));
        rig.coordinator().admit(keys());
        assertFalse(rig.coordinator().isQuickChatOpen());
        rig.session().onControl(new ControlMessage.ChatModeState("FULL_CHAT"));
        rig.coordinator().admit(keys(SceneKeys.Q));
        levelReady(rig.runtime());
        assertFalse(rig.coordinator().isQuickChatOpen());
        rig.coordinator().admit(keys(SceneKeys.Q));
        rig.coordinator().detachRuntime();
        assertFalse(rig.coordinator().isQuickChatOpen());
    }

    @Test
    void liveEnginePreferencesHideTypedAndQuickFromRaceHudWithoutBlockingSend(@TempDir Path root) {
        Rig rig = rig(root);
        java.util.concurrent.atomic.AtomicBoolean disabled = new java.util.concurrent.atomic.AtomicBoolean();
        rig.coordinator().setChatPreferenceSource(() -> true, disabled::get);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.ChatBroadcast(1, "Racer", "hello")));
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.QuickChatBroadcast(
                1, "Racer", "THIS_IS_ZONE", "s2", 1, 0)));
        rig.coordinator().pump();
        List<String> shown = rig.coordinator().hudState().chatLines();
        assertEquals(2, shown.size());
        assertTrue(shown.getLast().contains("Chemical Plant"), shown.toString());
        HudTextLayout.Panel panel = MultiplayerHudRenderer.chatLayout(320, 224, rig.coordinator().hudState());
        assertNotNull(panel);
        assertTrue(panel.lines().stream().anyMatch(line -> line.plain().contains("hello")));
        disabled.set(true);
        rig.coordinator().pump();
        assertTrue(rig.coordinator().hudState().chatLines().isEmpty());
        assertNull(MultiplayerHudRenderer.chatLayout(320, 224, rig.coordinator().hudState()));
        rig.coordinator().sendChat("outgoing");
        rig.coordinator().sendQuickChat(com.openggf.net.protocol.QuickChatPhrase.WELL_PLAYED);
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.Chat("outgoing")));
        assertTrue(rig.transport().sentControl.contains(new ControlMessage.QuickChat("WELL_PLAYED")));
        disabled.set(false);
        rig.coordinator().pump();
        assertTrue(rig.coordinator().hudState().chatLines().isEmpty(), "no hidden backlog after re-enabling");
        rig.session().onControl(new ControlMessage.ChatModeState("OFF"));
        int before = rig.transport().sentControl.size();
        rig.coordinator().sendChat("blocked");
        rig.coordinator().sendQuickChat(com.openggf.net.protocol.QuickChatPhrase.BRUTAL);
        assertEquals(before, rig.transport().sentControl.size());
    }

}
