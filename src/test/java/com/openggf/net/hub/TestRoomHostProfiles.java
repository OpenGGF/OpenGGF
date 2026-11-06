package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** International names, looks and in-lobby profile updates on the room host. */
class TestRoomHostProfiles {
    private static final String FP = "0.6:cafe1234";
    private static final String RED = "00E.200E.300C.400A";
    private static final String GREEN = "0E0.20E0.30C0.40A0";

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomHost room;
    private final List<String[]> renames = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        RoomHostHooks hooks = new RoomHostHooks(false, null, null, null, null, null, null, null,
                (fingerprint, name) -> renames.add(new String[] {fingerprint, name}));
        room = new RoomHost(new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")), () -> now,
                TrackValidationProfileSource.none(), hooks);
    }

    @Test
    void internationalNamesAreAdmittedAndNonCanonicalNamesAreRejected() throws Exception {
        var join = admit(new FakeHubConnection(), "疾風の刃", "", dir.resolve("a"));
        assertNotNull(join);
        FakeHubConnection spoof = new FakeHubConnection();
        assertNull(admit(spoof, "evil‮gnp", "", dir.resolve("b")));
        assertEquals("invalid display name", rejection(spoof));
        FakeHubConnection decomposed = new FakeHubConnection();
        assertNull(admit(decomposed, "Café", "", dir.resolve("c")));
        assertEquals(1, room.playerCount());
    }

    @Test
    void malformedLooksAreRejectedAtJoin() throws Exception {
        FakeHubConnection bad = new FakeHubConnection();
        var identity = PlayerIdentity.loadOrCreate(dir.resolve("a"));
        var handshake = new ClientHandshake(identity, "Ana", "00F", FP, null,
                AuthenticationTranscript.Role.ROOM_DIRECT, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(bad);
        room.onText(bad, ControlCodec.encode(null, handshake.hello()));
        assertNotNull(bad.closedReason);
        assertTrue(bad.closedReason.contains("invalid appearance"));
        assertTrue(messages(bad).stream().noneMatch(message -> message instanceof ControlMessage.Welcome
                || message instanceof ControlMessage.JoinAccepted));
        assertEquals(0, room.playerCount());
    }

    @Test
    void lateJoinerReceivesEveryLookAndOthersLearnItsLook() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        var joinA = admit(a, "Ana", RED, dir.resolve("a"));
        assertEquals(List.of(new ControlMessage.PlayerLook(joinA.playerSlot(), RED)), looks(a),
                "a player is told its own look so its lobby row is coloured");
        a.text.clear();
        FakeHubConnection b = new FakeHubConnection();
        var joinB = admit(b, "Дмитрий", GREEN, dir.resolve("b"));
        assertEquals(List.of(new ControlMessage.PlayerLook(joinA.playerSlot(), RED),
                new ControlMessage.PlayerLook(joinB.playerSlot(), GREEN)), looks(b));
        assertEquals(List.of(new ControlMessage.PlayerLook(joinB.playerSlot(), GREEN)), looks(a));
        FakeHubConnection c = new FakeHubConnection();
        admit(c, "Plain", "", dir.resolve("c"));
        assertEquals(2, looks(c).size(), "stock looks are not sent");
    }

    @Test
    void lobbyRenameAndRecolourKeepTheIdentityAndReachEveryone() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        var joinA = admit(a, "Ana", "", dir.resolve("a"));
        admit(b, "Bo", "", dir.resolve("b"));
        a.text.clear();
        b.text.clear();
        room.onText(a, ControlCodec.encode(joinA.sessionToken(),
                new ControlMessage.UpdateProfile("Ана Мюллер", RED)));
        var upsert = (ControlMessage.PlayerUpsert) messages(b).stream()
                .filter(ControlMessage.PlayerUpsert.class::isInstance).findFirst().orElseThrow();
        assertEquals("Ана Мюллер", upsert.player().displayName());
        assertEquals(joinA.playerSlot(), upsert.player().slot());
        assertEquals(List.of(new ControlMessage.PlayerLook(joinA.playerSlot(), RED)), looks(b));
        assertEquals(List.of(new ControlMessage.PlayerLook(joinA.playerSlot(), RED)), looks(a));
        assertEquals(1, renames.size());
        assertEquals("Ана Мюллер", renames.getFirst()[1]);
    }

    @Test
    void profileUpdatesAreLobbyOnlyRateLimitedAndValidated() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        var joinA = admit(a, "Ana", "", dir.resolve("a"));
        admit(b, "Bo", "", dir.resolve("b"));
        b.text.clear();
        room.onText(a, ControlCodec.encode(joinA.sessionToken(), new ControlMessage.UpdateProfile("Ana", RED)));
        room.onText(a, ControlCodec.encode(joinA.sessionToken(), new ControlMessage.UpdateProfile("Ana", GREEN)));
        assertEquals(1, looks(b).size(), "a second update inside the interval is ignored");
        now += Protocol.PROFILE_MIN_INTERVAL_MILLIS;
        room.onText(a, ControlCodec.encode(joinA.sessionToken(), new ControlMessage.UpdateProfile(" Ana ", GREEN)));
        assertEquals(1, looks(b).size(), "non-canonical names are ignored");
        assertNull(a.closedReason);
        now += Protocol.PROFILE_MIN_INTERVAL_MILLIS;
        room.requestStartRound(new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null));
        room.onText(a, ControlCodec.encode(joinA.sessionToken(), new ControlMessage.UpdateProfile("Ana", GREEN)));
        assertEquals(1, looks(b).size(), "profiles are fixed once a round starts");
        assertTrue(renames.isEmpty());
    }

    @Test
    void malformedProfileUpdatesRetireTheSenderWithoutPublishingALook() throws Exception {
        FakeHubConnection a = new FakeHubConnection();
        FakeHubConnection b = new FakeHubConnection();
        var joined = admit(a, "Ana", "", dir.resolve("a"));
        admit(b, "Bo", "", dir.resolve("b"));
        b.text.clear();
        room.onText(a, ControlCodec.encode(joined.sessionToken(),
                new ControlMessage.UpdateProfile("Ana", "zz")));
        assertNotNull(a.closedReason);
        assertTrue(looks(b).isEmpty());
        assertEquals(1, room.playerCount());
    }

    private ControlMessage.JoinAccepted admit(FakeHubConnection connection, String name, String look,
                                               Path identityDir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        ClientHandshake handshake = new ClientHandshake(identity, name, look, FP, null,
                AuthenticationTranscript.Role.ROOM_DIRECT, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        var welcome = (ControlMessage.Welcome) messages(connection).getLast();
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        return messages(connection).stream().filter(ControlMessage.JoinAccepted.class::isInstance)
                .map(ControlMessage.JoinAccepted.class::cast).findFirst().orElse(null);
    }

    private static String rejection(FakeHubConnection connection) {
        return messages(connection).stream().filter(ControlMessage.JoinRejected.class::isInstance)
                .map(message -> ((ControlMessage.JoinRejected) message).reason()).findFirst().orElse(null);
    }

    private static List<ControlMessage.PlayerLook> looks(FakeHubConnection connection) {
        return messages(connection).stream().filter(ControlMessage.PlayerLook.class::isInstance)
                .map(ControlMessage.PlayerLook.class::cast).toList();
    }

    private static List<ControlMessage> messages(FakeHubConnection connection) {
        return connection.text.stream().map(text -> ControlCodec.decode(text).message()).toList();
    }
}
