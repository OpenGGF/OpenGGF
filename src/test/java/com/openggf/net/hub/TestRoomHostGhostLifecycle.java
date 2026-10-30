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
    void unverifiedRoomDoesNotPublishClientClaimedStandings() throws Exception {
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
                .allMatch(delta -> delta.rows().isEmpty()),
                "unverified client claims must not become peer-visible rankings");
        assertTrue(room.round().standings().isEmpty());
        assertEquals(1, room.round().results().size(),
                "the operator may retain the claim for a bounded spot check");

        now += 300_001;
        room.tick();
        assertTrue(watcher.text.stream()
                .map(text -> ControlCodec.decode(text).message())
                .filter(ControlMessage.RoundEnd.class::isInstance)
                .map(ControlMessage.RoundEnd.class::cast)
                .allMatch(end -> end.finalStandings().isEmpty()),
                "unverified claims must not become final competitive results");
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
