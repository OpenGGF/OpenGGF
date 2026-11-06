package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Room-host rules for many attempts per round: timing refusals and private rank refresh. */
class TestRoomHostRepeatedAttempts {
    private static final String FP = "0.6:cafe1234";
    private static final ControlMessage.RoundConfig ONE_SECOND_WINDOW =
            new ControlMessage.RoundConfig("s3k", 0, 0, 1, "OPEN", null);
    private static final ControlMessage.RoundConfig LONG_WINDOW =
            new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null);

    @TempDir Path dir;
    private long now = 1_000_000;
    private final List<String> roundOutcomes = new ArrayList<>();

    @Test
    void timingRefusedStartsDropTheirTrafficWithoutViolations() throws Exception {
        RoomHost room = room(false);
        FakeHubConnection sender = new FakeHubConnection();
        FakeHubConnection witness = new FakeHubConnection();
        String token = admit(room, sender, "A", "a");
        admit(room, witness, "B", "b");
        assertTrue(room.requestStartRound(ONE_SECOND_WINDOW));

        // A client clock running ahead of the hub starts during the countdown.
        control(room, sender, token, new ControlMessage.AttemptStart(1));
        sendFrames(room, sender, 1, 30);
        control(room, sender, token, new ControlMessage.AttemptReset(1));
        room.tick();
        assertNull(sender.closedReason);
        assertTrue(witness.binary.isEmpty(), "refused frames are never relayed");

        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();
        finishAttempt(room, sender, token, 2);
        assertEquals(1, room.round().results().size(), "the next start is accepted");

        // Starting after the deadline is refused while finishes keep their grace.
        now += 1001;
        room.tick();
        assertEquals(HostRoundEngine.Phase.RUNNING, room.round().phase());
        control(room, sender, token, new ControlMessage.AttemptStart(3));
        sendFrames(room, sender, 3, 30);
        control(room, sender, token, new ControlMessage.AttemptReset(3));
        control(room, sender, token, new ControlMessage.AttemptFinish(3, 1, 0, 1,
                "cd".repeat(32), "00".repeat(32), null));
        assertEquals(1, room.round().results().size());
        assertEquals(1, room.round().results().getFirst().timeFrames());

        now += HostRoundEngine.FINISH_GRACE_MILLIS;
        room.tick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, room.round().phase());
        sendFrames(room, sender, 3, 3);
        assertNull(sender.closedReason);
        assertEquals(List.of(fingerprint("a") + ":true", fingerprint("b") + ":false"),
                roundOutcomes, "a clean round proves no attempt violation was counted");
    }

    @Test
    void nonTimingStartRefusalsAndForeignTrafficRemainViolations() throws Exception {
        RoomHost room = room(false);
        FakeHubConnection active = new FakeHubConnection();
        FakeHubConnection refused = new FakeHubConnection();
        String activeToken = admit(room, active, "A", "a");
        String refusedToken = admit(room, refused, "B", "b");
        assertTrue(room.requestStartRound(LONG_WINDOW));

        control(room, refused, refusedToken, new ControlMessage.AttemptStart(5));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();
        control(room, active, activeToken, new ControlMessage.AttemptStart(1));

        // Frames for an attempt other than the refused one are still violations.
        for (int packet = 0; packet < GhostStreamValidator.KICK_THRESHOLD - 1; packet++) {
            room.onBinary(refused, frames(6, packet, 1));
        }
        assertNull(refused.closedReason, "the timing refusal itself counted nothing");
        // Reusing the refused id keeps attempt ordering strict.
        control(room, refused, refusedToken, new ControlMessage.AttemptStart(5));
        assertEquals("attempt protocol violations", refused.closedReason);

        for (int attempt = 2; attempt < 2 + GhostStreamValidator.KICK_THRESHOLD; attempt++) {
            assertNull(active.closedReason);
            control(room, active, activeToken, new ControlMessage.AttemptStart(attempt));
        }
        assertEquals("attempt protocol violations", active.closedReason,
                "a start while another attempt is active is still a violation");
    }

    @Test
    void playerPushedFromTenthToEleventhReceivesPrivateRank() throws Exception {
        RoomHost room = room(true);
        FakeHubConnection pushed = new FakeHubConnection();
        FakeHubConnection other = new FakeHubConnection();
        admit(room, pushed, "A", "a");
        admit(room, other, "B", "b");
        assertTrue(room.requestStartRound(LONG_WINDOW));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();

        room.round().onAttemptFinish(0, "A", "sonic", finish(1, 1010, "aa"), false);
        for (int slot = 2; slot <= 10; slot++) {
            room.round().onAttemptFinish(slot, "P" + slot, "sonic",
                    finish(1, 998 + slot, "aa"), false);
        }
        assertEquals(10, lastDelta(pushed).rows().getLast().rank());
        assertEquals(0, lastDelta(pushed).rows().getLast().slot());
        assertTrue(rankUpdates(pushed).isEmpty(), "still inside the broadcast top ten");

        room.round().onAttemptFinish(11, "P11", "sonic", finish(1, 1009, "aa"), false);
        assertEquals(List.of(new ControlMessage.RankUpdate(11, 1010)), rankUpdates(pushed));
        assertTrue(rankUpdates(other).isEmpty(), "only the pushed-out participant is told");
        ControlMessage.StandingsDelta delta = lastDelta(other);
        assertEquals(10, delta.rows().size(), "the broadcast payload is unchanged");
        assertTrue(delta.rows().stream().noneMatch(row -> row.slot() == 0));

        room.round().onAttemptFinish(12, "P12", "sonic", finish(1, 999, "aa"), false);
        assertEquals(1, rankUpdates(pushed).size(),
                "a player already outside the top ten pages on its own");
    }

    @Test
    void failedClaimFallingOutOfTopTenReceivesPrivateRank() throws Exception {
        RoomHost room = room(true);
        FakeHubConnection player = new FakeHubConnection();
        admit(room, player, "A", "a");
        assertTrue(room.requestStartRound(LONG_WINDOW));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();

        room.round().onAttemptFinish(0, "A", "sonic", finish(1, 2000, "aa"), false);
        room.round().onVerdict(0, 1, true);
        for (int slot = 2; slot <= 11; slot++) {
            room.round().onAttemptFinish(slot, "P" + slot, "sonic",
                    finish(1, 998 + slot, "bb"), false);
        }
        assertEquals(List.of(new ControlMessage.RankUpdate(11, 2000)), rankUpdates(player));
        room.round().onAttemptFinish(0, "A", "sonic", finish(2, 900, "cc"), false);
        assertEquals(0, lastDelta(player).rows().getFirst().slot());

        room.round().onVerdict(0, 2, false);
        assertEquals(List.of(new ControlMessage.RankUpdate(11, 2000),
                new ControlMessage.RankUpdate(11, 2000)), rankUpdates(player),
                "falling back to the verified time outside the cap refreshes the rank");
        assertEquals("VERIFIED", room.round().standings().get(10).verifyState());
    }

    private RoomHost room(boolean verified) throws Exception {
        RoomHostHooks hooks = new RoomHostHooks(false, null,
                (fingerprint, clean) -> roundOutcomes.add(fingerprint + ":" + clean),
                null, null);
        return new RoomHost(new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP,
                List.of(), verified), PlayerIdentity.loadOrCreate(dir.resolve("host")),
                () -> now, TrackValidationProfileSource.none(), hooks);
    }

    private String admit(RoomHost room, FakeHubConnection connection, String name,
                         String identity) throws Exception {
        ClientHandshake handshake = new ClientHandshake(
                PlayerIdentity.loadOrCreate(dir.resolve(identity)), name, FP);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) messages(connection).getLast();
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        return messages(connection).stream()
                .filter(ControlMessage.JoinAccepted.class::isInstance)
                .map(ControlMessage.JoinAccepted.class::cast)
                .findFirst().orElseThrow().sessionToken();
    }

    private String fingerprint(String identity) throws Exception {
        return PlayerIdentity.loadOrCreate(dir.resolve(identity)).fingerprint();
    }

    private static void control(RoomHost room, FakeHubConnection connection, String token,
                                ControlMessage message) {
        room.onText(connection, ControlCodec.encode(token, message));
    }

    private static void sendFrames(RoomHost room, FakeHubConnection connection,
                                   int attemptId, int packets) {
        for (int packet = 0; packet < packets; packet++) {
            room.onBinary(connection, frames(attemptId, packet, 1));
        }
    }

    private static byte[] frames(int attemptId, int startFrame, int count) {
        byte[] data = new byte[GhostFrameCodec.BYTES * count];
        for (int frame = 0; frame < count; frame++) {
            GhostFrameCodec.encode(new GhostFrame(100 + startFrame + frame, 200, 1,
                    false, false, false, 2, false), data, frame * GhostFrameCodec.BYTES);
        }
        return GhostPackets.encodeFrames(attemptId, startFrame, data);
    }

    private static void finishAttempt(RoomHost room, FakeHubConnection connection,
                                      String token, int attemptId) {
        control(room, connection, token, new ControlMessage.AttemptStart(attemptId));
        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> room.onBinary(connection, packet));
        publisher.beginAttempt(attemptId);
        publisher.onFrame(new GhostFrame(100, 200, 1, false, false, false, 2, false));
        publisher.onFrame(new GhostFrame(101, 200, 1, false, false, false, 2, false));
        publisher.finishAttempt();
        control(room, connection, token, new ControlMessage.AttemptFinish(attemptId, 1, 0, 1,
                "ab".repeat(32), HexFormat.of().formatHex(publisher.streamHashSha256()), null));
    }

    private static ControlMessage.AttemptFinish finish(int attemptId, int frames, String hash) {
        return new ControlMessage.AttemptFinish(attemptId, frames, 5, 5 + frames,
                hash.repeat(32), "cd".repeat(32), null);
    }

    private static List<ControlMessage> messages(FakeHubConnection connection) {
        return connection.text.stream().map(text -> ControlCodec.decode(text).message())
                .toList();
    }

    private static List<ControlMessage.RankUpdate> rankUpdates(FakeHubConnection connection) {
        return messages(connection).stream()
                .filter(ControlMessage.RankUpdate.class::isInstance)
                .map(ControlMessage.RankUpdate.class::cast).toList();
    }

    private static ControlMessage.StandingsDelta lastDelta(FakeHubConnection connection) {
        return messages(connection).stream()
                .filter(ControlMessage.StandingsDelta.class::isInstance)
                .map(ControlMessage.StandingsDelta.class::cast)
                .reduce((first, second) -> second).orElseThrow();
    }
}
