package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TestRoomHost {
    private static final String FP = "0.6:cafe1234";

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomHost room;
    private PlayerIdentity hostIdentity;

    @BeforeEach
    void setUp() throws Exception {
        hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        room = new RoomHost(
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                hostIdentity, () -> now, TrackValidationProfileSource.none());
    }

    private ControlMessage.JoinAccepted admit(FakeHubConnection connection, String name,
                                               Path identityDir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        ClientHandshake handshake = new ClientHandshake(identity, name, FP);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) lastMessage(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        return (ControlMessage.JoinAccepted) connection.text.stream()
                .map(text -> ControlCodec.decode(text).message())
                .filter(message -> message instanceof ControlMessage.JoinAccepted)
                .findFirst().orElseThrow();
    }

    private static ControlMessage lastMessage(FakeHubConnection connection) {
        return ControlCodec.decode(connection.text.get(connection.text.size() - 1)).message();
    }

    @Test
    void admitsPlayersAssignsSlotsAndBroadcastsRoomState() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        ControlMessage.JoinAccepted joinA = admit(a, "A", dir.resolve("a"));
        ControlMessage.JoinAccepted joinB = admit(b, "B", dir.resolve("b"));
        assertEquals(0, joinA.playerSlot());
        assertEquals(1, joinB.playerSlot());
        assertFalse(joinA.room().verified());
        assertEquals("LOBBY", joinA.round().phase());
        assertEquals(2, ((ControlMessage.RoomState) lastMessage(a)).players().size());
        assertEquals(2, room.playerCount());
    }

    @Test
    void rejectsOversizeDisplayNameAndIgnoresOversizeCharacter() throws Exception {
        FakeHubConnection rejected = new FakeHubConnection();
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("oversize"));
        ClientHandshake handshake = new ClientHandshake(identity, "x".repeat(65), FP);
        room.onConnected(rejected);
        room.onText(rejected, ControlCodec.encode(null, handshake.hello()));
        room.onText(rejected, ControlCodec.encode(null,
                handshake.onWelcome((ControlMessage.Welcome) lastMessage(rejected))));
        assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(rejected));
        assertEquals(0, room.playerCount());

        FakeHubConnection accepted = new FakeHubConnection();
        String token = admit(accepted, "Player", dir.resolve("accepted")).sessionToken();
        room.onText(accepted, ControlCodec.encode(token,
                new ControlMessage.SelectCharacter("x".repeat(33))));
        assertEquals("sonic", room.players().getFirst().character());
        room.onText(accepted, ControlCodec.encode(token,
                new ControlMessage.SelectCharacter("tails")));
        assertEquals("tails", room.players().getFirst().character());
        assertTrue(accepted.text.stream().filter(text -> ControlCodec.decode(text).message()
                        instanceof ControlMessage.RoomState)
                .allMatch(text -> text.getBytes(StandardCharsets.UTF_8).length
                        <= Protocol.MAX_CONTROL_BYTES));
    }

    @Test
    void maximalTwoHundredFiftySixPlayerRosterFitsClientFrame() {
        List<ControlMessage.PlayerInfo> players = new ArrayList<>();
        for (int slot = 0; slot < Protocol.MAX_PLAYERS_RELAY; slot++) {
            players.add(new ControlMessage.PlayerInfo(slot, "f".repeat(64),
                    "n".repeat(64), "c".repeat(32), false));
        }
        String wire = ControlCodec.encode(null, new ControlMessage.RoomState(players));
        assertTrue(wire.getBytes(StandardCharsets.UTF_8).length
                <= Protocol.MAX_CONTROL_BYTES);
        String tunneled = ControlCodec.encode("t".repeat(32),
                new ControlMessage.RelayGuestText(255, wire));
        assertTrue(tunneled.getBytes(StandardCharsets.UTF_8).length
                <= Protocol.MAX_MASTER_FRAME_BYTES);
        assertEquals(wire, ((ControlMessage.RelayGuestText) ControlCodec.decode(
                tunneled, Protocol.MAX_MASTER_FRAME_BYTES).message()).text());
        assertEquals(Protocol.MAX_PLAYERS_RELAY,
                ((ControlMessage.RoomState) ControlCodec.decode(wire).message())
                        .players().size());
    }

    @Test
    void rejectsWhenFullAndOnDuplicateIdentity() throws Exception {
        RoomHost tiny = new RoomHost(
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 1, FP),
                hostIdentity, () -> now, TrackValidationProfileSource.none());
        FakeHubConnection a = new FakeHubConnection();
        PlayerIdentity idA = PlayerIdentity.loadOrCreate(dir.resolve("a"));
        ClientHandshake hsA = new ClientHandshake(idA, "A", FP);
        tiny.onConnected(a);
        tiny.onText(a, ControlCodec.encode(null, hsA.hello()));
        tiny.onText(a, ControlCodec.encode(null,
                hsA.onWelcome((ControlMessage.Welcome) lastMessage(a))));

        FakeHubConnection b = new FakeHubConnection();
        PlayerIdentity idB = PlayerIdentity.loadOrCreate(dir.resolve("b"));
        ClientHandshake hsB = new ClientHandshake(idB, "B", FP);
        tiny.onConnected(b);
        tiny.onText(b, ControlCodec.encode(null, hsB.hello()));
        tiny.onText(b, ControlCodec.encode(null,
                hsB.onWelcome((ControlMessage.Welcome) lastMessage(b))));
        assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(b));
        assertNotNull(b.closedReason);
    }

    @Test
    void rejectsDuplicateIdentity() throws Exception {
        FakeHubConnection first = new FakeHubConnection();
        admit(first, "A", dir.resolve("same"));
        FakeHubConnection duplicate = new FakeHubConnection();
        room.onConnected(duplicate);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("same"));
        ClientHandshake handshake = new ClientHandshake(identity, "A2", FP);
        room.onText(duplicate, ControlCodec.encode(null, handshake.hello()));
        room.onText(duplicate, ControlCodec.encode(null,
                handshake.onWelcome((ControlMessage.Welcome) lastMessage(duplicate))));
        assertInstanceOf(ControlMessage.JoinRejected.class, lastMessage(duplicate));
        assertNotNull(duplicate.closedReason);
    }

    @Test
    void enforcesSessionTokenOnAdmittedMessages() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        ControlMessage.JoinAccepted join = admit(a, "A", dir.resolve("a"));
        admit(b, "B", dir.resolve("b"));
        room.onText(a, ControlCodec.encode("wrongtoken", new ControlMessage.Chat("hi")));
        assertTrue(b.text.stream().map(text -> ControlCodec.decode(text).message())
                .noneMatch(message -> message instanceof ControlMessage.ChatBroadcast));
        room.onText(a, ControlCodec.encode(join.sessionToken(), new ControlMessage.Chat("hi")));
        assertTrue(b.text.stream().map(text -> ControlCodec.decode(text).message())
                .anyMatch(message -> message instanceof ControlMessage.ChatBroadcast));
    }

    @Test
    void threeBadTokensCloseConnection() throws Exception {
        FakeHubConnection connection = new FakeHubConnection();
        admit(connection, "A", dir.resolve("a"));
        for (int i = 0; i < 3; i++) {
            room.onText(connection,
                    ControlCodec.encode("wrong", new ControlMessage.Chat("hi")));
        }
        assertEquals("session token violations", connection.closedReason);
        assertEquals(0, room.playerCount());
    }

    @Test
    void chatIsRateLimited() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        String token = admit(a, "A", dir.resolve("a")).sessionToken();
        admit(b, "B", dir.resolve("b"));
        room.onText(a, ControlCodec.encode(token, new ControlMessage.Chat("one")));
        room.onText(a, ControlCodec.encode(token, new ControlMessage.Chat("two")));
        assertEquals(1, b.text.stream().map(text -> ControlCodec.decode(text).message())
                .filter(message -> message instanceof ControlMessage.ChatBroadcast).count());
    }

    @Test
    void onlyHostFingerprintMayStartRounds() throws Exception {
        FakeHubConnection guest = new FakeHubConnection();
        String guestToken = admit(guest, "G", dir.resolve("g")).sessionToken();
        ControlMessage.RoundConfig config =
                new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null);
        room.onText(guest,
                ControlCodec.encode(guestToken, new ControlMessage.RoundConfigure(config)));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase());
        FakeHubConnection host = new FakeHubConnection();
        String hostToken = admit(host, "Host", dir.resolve("host")).sessionToken();
        room.onText(host,
                ControlCodec.encode(hostToken, new ControlMessage.RoundConfigure(config)));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
    }

    @Test
    void ghostFramesFlowToOtherPlayersAfterTick() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        String token = admit(a, "A", dir.resolve("a")).sessionToken();
        admit(b, "B", dir.resolve("b"));
        room.requestStartRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 300, "OPEN", null));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();
        room.onText(a, ControlCodec.encode(token, new ControlMessage.AttemptStart(1)));
        byte[] frame = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1,
                false, false, false, 2, false), frame, 0);
        room.onBinary(a, GhostPackets.encodeFrames(1, 0, frame));
        room.tick();
        assertEquals(1, b.binary.size());
        assertEquals(0,
                GhostPackets.decodeAggregate(b.binary.get(0)).entries().get(0).playerSlot());
    }

    @Test
    void activeAttemptCanFinishAfterDeadlineTickWithinTransitGrace() throws Exception {
        FakeHubConnection player = new FakeHubConnection();
        String token = admit(player, "A", dir.resolve("a")).sessionToken();
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 1, "OPEN", null)));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();
        room.onText(player, ControlCodec.encode(token, new ControlMessage.AttemptStart(1)));

        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> room.onBinary(player, packet));
        publisher.beginAttempt(1);
        publisher.onFrame(new GhostFrame(100, 200, 1,
                false, false, false, 2, false));
        publisher.onFrame(new GhostFrame(101, 200, 1,
                false, false, false, 2, false));
        publisher.finishAttempt();

        now += 1001;
        room.tick();
        room.onText(player, ControlCodec.encode(token,
                new ControlMessage.AttemptFinish(1, 1, 0, 1,
                        "ab".repeat(32), HexFormat.of().formatHex(
                        publisher.streamHashSha256()), null)));
        assertEquals(HostRoundEngine.Phase.RUNNING, room.round().phase());
        assertEquals(1, room.round().standings().size());
    }

    @Test
    void attemptCannotStartAfterDeadlineEvenWhileFinishesHaveGrace() throws Exception {
        FakeHubConnection player = new FakeHubConnection();
        String token = admit(player, "A", dir.resolve("a")).sessionToken();
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 1, "OPEN", null)));
        now += HostRoundEngine.COUNTDOWN_MILLIS + 1001;
        room.tick();
        assertEquals(HostRoundEngine.Phase.RUNNING, room.round().phase());

        room.onText(player, ControlCodec.encode(token, new ControlMessage.AttemptStart(1)));
        GhostStreamPublisher publisher = new GhostStreamPublisher(
                packet -> room.onBinary(player, packet));
        publisher.beginAttempt(1);
        publisher.onFrame(new GhostFrame(100, 200, 1,
                false, false, false, 2, false));
        publisher.onFrame(new GhostFrame(101, 200, 1,
                false, false, false, 2, false));
        publisher.finishAttempt();
        room.onText(player, ControlCodec.encode(token,
                new ControlMessage.AttemptFinish(1, 1, 0, 1,
                        "ab".repeat(32), HexFormat.of().formatHex(
                        publisher.streamHashSha256()), null)));

        assertTrue(room.round().standings().isEmpty());
    }

    @Test
    void attemptTrafficOutsideRunningRoundIsNotRelayed() throws Exception {
        FakeHubConnection sender = new FakeHubConnection();
        FakeHubConnection recipient = new FakeHubConnection();
        String token = admit(sender, "A", dir.resolve("a")).sessionToken();
        admit(recipient, "B", dir.resolve("b"));

        room.onText(sender,
                ControlCodec.encode(token, new ControlMessage.AttemptStart(1)));
        byte[] frame = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1,
                false, false, false, 2, false), frame, 0);
        room.onBinary(sender, GhostPackets.encodeFrames(1, 0, frame));
        room.tick();

        assertTrue(recipient.binary.isEmpty());
    }

    @Test
    void admissionTimeoutClosesLoiterers() {
        FakeHubConnection loiterer = new FakeHubConnection();
        room.onConnected(loiterer);
        now += RoomHost.ADMISSION_TIMEOUT_MILLIS + 1;
        room.tick();
        assertNotNull(loiterer.closedReason);
    }

    @Test
    void malformedTextClosesConnection() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        admit(a, "A", dir.resolve("a"));
        room.onText(a, "garbage not json");
        assertNotNull(a.closedReason);
        assertEquals(0, room.playerCount());
    }

    @Test
    void disconnectRevokesAndBroadcasts() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        String tokenA = admit(a, "A", dir.resolve("a")).sessionToken();
        admit(b, "B", dir.resolve("b"));
        room.onDisconnected(a);
        assertEquals(1, room.playerCount());
        assertEquals(1, ((ControlMessage.RoomState) lastMessage(b)).players().size());
        room.onText(a, ControlCodec.encode(tokenA, new ControlMessage.Chat("ghost of a")));
        assertTrue(b.text.stream().map(text -> ControlCodec.decode(text).message())
                .noneMatch(message -> message instanceof ControlMessage.ChatBroadcast));
    }

    @Test
    void standingsPageRequestsAreUnicastAndRateLimited() throws Exception {
        FakeHubConnection player = new FakeHubConnection();
        String token = admit(player, "A", dir.resolve("a")).sessionToken();
        room.requestStartRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 300, "OPEN", null));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();
        room.round().onAttemptFinish(0, "A", "sonic",
                new ControlMessage.AttemptFinish(1, 1000, 5, 1005,
                        "ab".repeat(32), "cd".repeat(32), null), false);

        int before = player.text.size();
        room.onText(player, ControlCodec.encode(token,
                new ControlMessage.StandingsPageRequest(0)));
        assertEquals(before + 1, player.text.size());
        ControlMessage.StandingsPage page = assertInstanceOf(
                ControlMessage.StandingsPage.class, lastMessage(player));
        assertEquals(1, page.rows().size());
        room.onText(player, ControlCodec.encode(token,
                new ControlMessage.StandingsPageRequest(0)));
        assertEquals(before + 1, player.text.size(), "second request inside 2s is dropped");
    }
}
