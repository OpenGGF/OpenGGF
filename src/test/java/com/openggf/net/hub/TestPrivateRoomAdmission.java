package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TestPrivateRoomAdmission {
    @TempDir Path dir;
    final ControlJsonCodec codec = new ControlJsonCodec();
    static final String SECRET = "a sufficiently long room password";
    long now = 1_000_000;

    record Peer(FakeHubConnection connection, ControlMessage.RoomAccessChallenge challenge) { }

    RoomHost room(PlayerIdentity owner) {
        try (RoomPassword password = RoomPassword.of(SECRET)) {
            RoomHost host = new RoomHost(new RoomHostConfig("Private", "s3k", 0, 0, "OPEN", null,
                    2, "build", List.of(), false,
                    new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true), password, null),
                    owner, () -> now, TrackValidationProfileSource.none());
            host.ready().join();
            return host;
        }
    }

    Peer prove(RoomHost room, PlayerIdentity identity) throws Exception {
        return prove(room, identity, new FakeHubConnection());
    }

    Peer prove(RoomHost room, PlayerIdentity identity, FakeHubConnection connection) throws Exception {
        ClientHandshake handshake = new ClientHandshake(identity, "Player", "build");
        room.onConnected(connection);
        room.onText(connection, codec.encode(null, handshake.hello()));
        var welcome = last(connection, ControlMessage.Welcome.class);
        room.onText(connection, codec.encode(null, handshake.onWelcome(welcome)));
        return new Peer(connection, last(connection, ControlMessage.RoomAccessChallenge.class));
    }

    <T> T last(FakeHubConnection connection, Class<T> type) {
        return connection.text.stream().map(s -> codec.decode(s).message()).filter(type::isInstance)
                .map(type::cast).reduce((a,b) -> b).orElse(null);
    }

    void submit(RoomHost room, Peer peer, String secret) {
        try (RoomPassword password = RoomPassword.of(secret)) {
            room.onText(peer.connection, codec.encode(null,
                    new ControlMessage.RoomAccessSubmit(peer.challenge.challengeId(), password)));
        }
    }

    void settled(RoomHost room, Peer peer) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (last(peer.connection, ControlMessage.JoinAccepted.class) == null
                && peer.connection.closedReason == null && System.nanoTime() < end) {
            room.tick();
            Thread.sleep(5);
        }
    }

    @Test
    void identityAndChallengePublishNoMembershipAndWrongPasswordNeverAdmits() throws Exception {
        RoomHost room = room(PlayerIdentity.loadOrCreate(dir.resolve("owner")));
        Peer peer = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
        assertNotNull(peer.challenge);
        assertEquals(room.instanceId(), peer.challenge.instanceId());
        assertEquals(0, room.playerCount());
        assertTrue(room.players().isEmpty());
        assertTrue(peer.connection.binary.isEmpty());
        assertNull(last(peer.connection, ControlMessage.JoinAccepted.class));
        assertNull(last(peer.connection, ControlMessage.RoomState.class));
        submit(room, peer, "the wrong room password");
        settled(room, peer);
        assertEquals("room access denied", last(peer.connection, ControlMessage.JoinRejected.class).reason());
        assertEquals(0, room.playerCount());
        room.close("done");
    }

    @Test
    void correctPasswordAdmitsAndCreatorCompletesSameGate() throws Exception {
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        RoomHost room = room(owner);
        Peer peer = prove(room, owner);
        assertEquals(0, room.playerCount());
        submit(room, peer, SECRET);
        settled(room, peer);
        assertNotNull(last(peer.connection, ControlMessage.JoinAccepted.class));
        assertEquals(1, room.playerCount());
        assertEquals(1, last(peer.connection, ControlMessage.RoomState.class).players().size());
        assertNull(last(peer.connection, ControlMessage.RoomInvite.class));
        room.close("done");
    }

    @Test
    void challengeIsSingleUseAndConnectionBound() throws Exception {
        RoomHost room = room(PlayerIdentity.loadOrCreate(dir.resolve("owner")));
        Peer a = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("a")));
        Peer b = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("b")));
        try (RoomPassword password = RoomPassword.of(SECRET)) {
            room.onText(b.connection, codec.encode(null,
                    new ControlMessage.RoomAccessSubmit(a.challenge.challengeId(), password)));
        }
        assertEquals("room access denied", last(b.connection, ControlMessage.JoinRejected.class).reason());
        submit(room, a, SECRET);
        submit(room, a, SECRET);
        settled(room, a);
        assertNull(last(a.connection, ControlMessage.JoinAccepted.class));
        assertEquals(0, room.playerCount());
        room.close("done");
    }

    @Test
    void disconnectCloseAndDeadlineInvalidateQueuedCompletions() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            RoomHost room = room(PlayerIdentity.loadOrCreate(dir.resolve("owner"+mode)));
            Peer peer = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("guest"+mode)));
            submit(room, peer, SECRET);
            if (mode == 0) room.onDisconnected(peer.connection);
            if (mode == 1) room.close("closed");
            if (mode == 2) now += RoomHost.ACCESS_TIMEOUT_MILLIS + 1;
            Thread.sleep(400);
            room.tick();
            assertNull(last(peer.connection, ControlMessage.JoinAccepted.class));
            assertEquals(0, room.playerCount());
            room.close("done");
        }
    }

    @Test
    void privateInitializationShedsOverloadAndNeverFallsBackToPublic() throws Exception {
        var rejecting = new RoomPasswordService(command -> { throw new RejectedExecutionException(); });
        try (RoomPassword password = RoomPassword.of(SECRET)) {
            RoomHost room = new RoomHost(new RoomHostConfig("Private", "s3k", 0, 0, "OPEN", null,
                    2, "build", List.of(), false,
                    new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true), password, null),
                    PlayerIdentity.loadOrCreate(dir.resolve("owner")), () -> now,
                    TrackValidationProfileSource.none(), RoomHostHooks.none(), codec, rejecting);
            assertThrows(CompletionException.class, () -> room.ready().join());
            var peer = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
            assertNull(peer.challenge);
            assertEquals(0, room.playerCount());
            assertNotNull(last(peer.connection, ControlMessage.JoinRejected.class));
            room.close("done");
        }
    }

    @Test
    void admissionRatesSurviveConnectionsAndRoomsAndTunnelsSkipOnlySource() {
        RoomAdmissionLimiter limiter = new RoomAdmissionLimiter();
        for (int i = 0; i < RoomAdmissionLimiter.IDENTITY_ATTEMPTS; i++) {
            assertTrue(limiter.allow("same", "source"+i, "room"+i, now));
        }
        assertFalse(limiter.allow("same", "different", "different", now));
        for (int i = 0; i < RoomAdmissionLimiter.SOURCE_ATTEMPTS; i++) {
            assertTrue(limiter.allow("identity"+i, "same-source", "other"+i, now));
        }
        assertFalse(limiter.allow("rotated", "same-source", "another", now));
        assertTrue(limiter.allow("new-tunnel", null, "tunnel-room", now));
        assertTrue(limiter.allow("same", "source", "room", now + RoomAdmissionLimiter.WINDOW_MILLIS));
    }

    @Test
    void signedCreatorStillVerifiesPasswordAfterOtherRoomsFillSourceQuota() throws Exception {
        String source = "198.51.100.27";
        long wallClock = System.currentTimeMillis();
        for (int attempt = 0; attempt < RoomAdmissionLimiter.SOURCE_ATTEMPTS; attempt++) {
            assertTrue(RoomAdmissionLimiter.SHARED.allow("other-identity-" + attempt,
                    source, "other-room-" + attempt, wallClock));
        }
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        RoomHost room = room(owner);
        try {
            FakeHubConnection connection = new FakeHubConnection() {
                @Override public String remoteHost() { return source; }
            };
            Peer creator = prove(room, owner, connection);
            submit(room, creator, SECRET);
            settled(room, creator);
            assertNotNull(last(connection, ControlMessage.JoinAccepted.class));
            assertEquals(1, room.playerCount());
            Peer wrongPassword = prove(room, owner, new FakeHubConnection() {
                @Override public String remoteHost() { return source; }
            });
            submit(room, wrongPassword, "a different room password");
            settled(room, wrongPassword);
            assertEquals("room access denied", last(wrongPassword.connection,
                    ControlMessage.JoinRejected.class).reason());
            assertEquals(1, room.playerCount());
        } finally { room.close("done"); }
    }
}
