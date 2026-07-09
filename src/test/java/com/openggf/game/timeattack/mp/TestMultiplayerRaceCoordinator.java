package com.openggf.game.timeattack.mp;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.game.timeattack.GhostStore;
import com.openggf.game.timeattack.TimeAttackLaunchRequest;
import com.openggf.game.timeattack.TimeAttackRuntime;
import com.openggf.game.timeattack.TimeAttackRuntimeTestBridge;
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
        boolean open = true;

        @Override public List<RaceClient.InboundEvent> drainInbound() {
            List<RaceClient.InboundEvent> events = new ArrayList<>(inbound);
            inbound.clear();
            return events;
        }
        @Override public void sendControl(ControlMessage message) { sentControl.add(message); }
        @Override public void sendBinary(byte[] data) { sentBinary.add(data.clone()); }
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
    void roundStartResetsRemoteAttemptIds(@TempDir Path root) {
        Rig rig = rig(root);
        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(3, "fp3", "PEER", "tails")))));
        feedPeerFrames(rig, 5, 500);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(500, rig.coordinator().remoteActiveGhosts().get(0).frame().x());

        rig.transport().inbound.add(new RaceClient.Control(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null),
                now, now + 60_000)));
        rig.coordinator().pump();
        feedPeerFrames(rig, 1, 100);
        rig.coordinator().pump();
        rig.coordinator().afterLevelFrame();
        assertEquals(100, rig.coordinator().remoteActiveGhosts().get(0).frame().x());
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
        assertEquals(2, rig.transport().sentControl.stream()
                .filter(ControlMessage.AttemptStart.class::isInstance).count());
        rig.coordinator().shutdown();
        assertFalse(rig.transport().open);
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
}
