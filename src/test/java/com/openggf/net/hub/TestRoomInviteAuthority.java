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

class TestRoomInviteAuthority {
    @TempDir Path dir;
    final ControlJsonCodec codec = new ControlJsonCodec();
    final long now = 1_000_000;
    final ArrayDeque<Runnable> work = new ArrayDeque<>();
    final RoomPasswordService passwords = new RoomPasswordService(work::addLast);
    static final String OLD_PASSWORD = "original room password";
    static final String NEW_PASSWORD = "replacement room password";
    PlayerIdentity owner;
    final Map<FakeHubConnection, String> tokens = new IdentityHashMap<>();

    RoomHost create(boolean privateRoom) throws Exception {
        owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        RoomHostConfig config;
        try (RoomPassword password = RoomPassword.of(OLD_PASSWORD)) {
            config = new RoomHostConfig("Room", "s3k", 0, 0, "OPEN", null, 2, "build", List.of(), false,
                    new RoomAccessPolicy(privateRoom ? RoomAccessPolicy.Mode.PRIVATE : RoomAccessPolicy.Mode.PUBLIC, true),
                    privateRoom ? password : null, null);
            RoomHost room = new RoomHost(config, owner, () -> now, TrackValidationProfileSource.none(),
                    RoomHostHooks.none(), codec, passwords);
            if (privateRoom) work.removeFirst().run();
            room.ready().join();
            return room;
        }
    }

    FakeHubConnection prove(RoomHost room, PlayerIdentity identity) throws Exception {
        FakeHubConnection connection = new FakeHubConnection() {
            @Override public String remoteHost() { return identity.fingerprint(); }
        };
        ClientHandshake handshake = new ClientHandshake(identity, "Player", "build");
        room.onConnected(connection);
        room.onText(connection, codec.encode(null, handshake.hello()));
        room.onText(connection, codec.encode(null, handshake.onWelcome(last(connection, ControlMessage.Welcome.class))));
        return connection;
    }

    void submit(RoomHost room, FakeHubConnection connection, String password) {
        try (RoomPassword secret = RoomPassword.of(password)) {
            room.onText(connection, codec.encode(null, new ControlMessage.RoomAccessSubmit(
                    last(connection, ControlMessage.RoomAccessChallenge.class).challengeId(), secret)));
        }
    }

    FakeHubConnection admit(RoomHost room, PlayerIdentity identity, boolean privateRoom) throws Exception {
        FakeHubConnection connection = prove(room, identity);
        if (privateRoom) { submit(room, connection, OLD_PASSWORD); work.removeFirst().run(); room.tick(); }
        assertNotNull(last(connection, ControlMessage.JoinAccepted.class));
        tokens.put(connection, last(connection, ControlMessage.JoinAccepted.class).sessionToken());
        return connection;
    }

    <T> T last(FakeHubConnection connection, Class<T> type) {
        return connection.text.stream().map(s -> codec.decode(s).message()).filter(type::isInstance)
                .map(type::cast).reduce((a,b) -> b).orElse(null);
    }

    void command(RoomHost room, FakeHubConnection connection, ControlMessage message) {
        room.onText(connection, codec.encode(tokens.get(connection), message));
    }

    ControlMessage.RoomInviteControlResult result(RoomHost room, boolean sharing, long access, long invite, long expiry) {
        return new ControlMessage.RoomInviteControlResult(new RoomAccessPolicy(room.accessPolicy().mode(), sharing),
                new ControlMessage.RoomInvite("7K3M9X", room.instanceId(), access, invite, expiry));
    }

    @Test
    void memberFetchWorksAndOwnerOnlyControlsAreAuthoritative() throws Exception {
        RoomHost room = create(false);
        var host = admit(room, owner, false);
        var guest = admit(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")), false);
        List<ControlMessage.InviteOperation> operations = new ArrayList<>();
        room.setInviteService((instance, access, invite, operation, sharing) -> {
            operations.add(operation);
            return CompletableFuture.completedFuture(result(room, sharing, access, invite + 1, now + 600_000));
        });
        command(room, guest, new ControlMessage.RoomInviteRequest(7)); room.tick();
        assertEquals(7, last(guest, ControlMessage.RoomInvite.class).disclosureId());
        assertNull(last(host, ControlMessage.RoomInvite.class));
        command(room, guest, new ControlMessage.RoomInviteRotate());
        command(room, guest, new ControlMessage.RoomInviteSharing(false));
        assertEquals(List.of(ControlMessage.InviteOperation.FETCH), operations);
        assertNotNull(last(guest, ControlMessage.RoomInviteRejected.class));
        command(room, host, new ControlMessage.RoomInviteSharing(false)); room.tick();
        assertFalse(room.accessPolicy().membersCanInvite());
        assertEquals(2, room.inviteEpoch());
        guest.text.clear();
        command(room, guest, new ControlMessage.RoomInviteRequest(8)); room.tick();
        assertNull(last(guest, ControlMessage.RoomInvite.class));
        assertEquals(2, operations.size());
        assertEquals(2, room.playerCount());
        room.close("done");
    }

    @Test
    void canonicalFetchAfterLostSharingAckUpdatesPolicyBeforeDisclosureToMember() throws Exception {
        RoomHost room = create(false);
        var host = admit(room, owner, false);
        var guest = admit(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")), false);
        CompletableFuture<ControlMessage.RoomInviteControlResult> failedAck = new CompletableFuture<>();
        room.setInviteService((instance, access, invite, operation, sharing) -> operation == ControlMessage.InviteOperation.FETCH
                ? CompletableFuture.completedFuture(result(room, false, 0, 3, now + 600_000)) : failedAck);
        command(room, host, new ControlMessage.RoomInviteSharing(false));
        failedAck.completeExceptionally(new TimeoutException()); room.tick();
        assertTrue(room.accessPolicy().membersCanInvite());
        command(room, guest, new ControlMessage.RoomInviteRequest(5)); room.tick();
        assertFalse(room.accessPolicy().membersCanInvite());
        assertEquals(3, room.inviteEpoch());
        assertNull(last(guest, ControlMessage.RoomInvite.class));
        assertNotNull(last(guest, ControlMessage.RoomInviteRejected.class));
        room.close("done");
    }

    @Test
    void expiredAndWrongInstanceProviderRepliesNeverDiscloseAndDepartedMemberGetsNoReply() throws Exception {
        RoomHost room = create(false);
        var guest = admit(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")), false);
        var response = new CompletableFuture<ControlMessage.RoomInviteControlResult>();
        room.setInviteService((instance, access, invite, op, sharing) -> response);
        command(room, guest, new ControlMessage.RoomInviteRequest(1));
        response.complete(result(room, true, 0, 1, now)); room.tick();
        assertNull(last(guest, ControlMessage.RoomInvite.class));
        room.setInviteService((instance, access, invite, op, sharing) -> CompletableFuture.completedFuture(
                new ControlMessage.RoomInviteControlResult(RoomAccessPolicy.PUBLIC,
                        new ControlMessage.RoomInvite("7K3M9X", "replacement", 0, 1, now+600_000))));
        command(room, guest, new ControlMessage.RoomInviteRequest(2)); room.tick();
        assertNull(last(guest, ControlMessage.RoomInvite.class));
        var late = new CompletableFuture<ControlMessage.RoomInviteControlResult>();
        room.setInviteService((instance, access, invite, op, sharing) -> late);
        command(room, guest, new ControlMessage.RoomInviteRequest(3));
        room.onDisconnected(guest); guest.text.clear();
        late.complete(result(room, true, 0, 1, now + 600_000)); room.tick();
        assertTrue(guest.text.isEmpty());
        room.close("done");
    }

    @Test
    void passwordAckInstallsPreparedVerifierEvenAfterInitiatingOwnerLeaves() throws Exception {
        RoomHost room = create(true);
        var host = admit(room, owner, true);
        var guest = admit(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")), true);
        var ack = new CompletableFuture<ControlMessage.RoomInviteControlResult>();
        room.setInviteService((instance, access, invite, operation, sharing) -> ack);
        try (RoomPassword password = RoomPassword.of(NEW_PASSWORD)) {
            command(room, host, new ControlMessage.RoomPasswordChange(password));
        }
        work.removeFirst().run(); room.tick();
        assertTrue(room.accessChangePending());
        room.onDisconnected(host); host.text.clear();
        ack.complete(result(room, true, 1, 1, now + 600_000)); room.tick();
        assertFalse(room.accessChangePending());
        assertEquals(1, room.accessEpoch());
        assertEquals(1, room.playerCount());
        assertNull(last(host, ControlMessage.RoomPasswordChanged.class));
        var newGuest = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("new-guest")));
        submit(room, newGuest, NEW_PASSWORD); work.removeFirst().run(); room.tick();
        assertNotNull(last(newGuest, ControlMessage.JoinAccepted.class));
        assertNotNull(last(guest, ControlMessage.RoomAccessState.class));
        room.close("done");
    }

    @Test
    void lostPasswordAckFailsClosedThenCanonicalFetchRecoversPreparedVerifier() throws Exception {
        RoomHost room = create(true);
        var host = admit(room, owner, true);
        var ack = new CompletableFuture<ControlMessage.RoomInviteControlResult>();
        room.setInviteService((instance, access, invite, operation, sharing) -> operation == ControlMessage.InviteOperation.FETCH
                ? CompletableFuture.completedFuture(result(room, true, 1, 2, now + 600_000)) : ack);
        try (RoomPassword password = RoomPassword.of(NEW_PASSWORD)) {
            command(room, host, new ControlMessage.RoomPasswordChange(password));
        }
        work.removeFirst().run(); room.tick();
        ack.completeExceptionally(new TimeoutException()); room.tick();
        assertTrue(room.accessChangePending());
        var waiting = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("waiting")));
        assertNull(last(waiting, ControlMessage.RoomAccessChallenge.class));
        assertNotNull(last(waiting, ControlMessage.JoinRejected.class));
        command(room, host, new ControlMessage.RoomInviteRequest(9)); room.tick();
        assertFalse(room.accessChangePending());
        assertEquals(1, room.accessEpoch());
        assertEquals(2, room.inviteEpoch());
        assertEquals(9, last(host, ControlMessage.RoomInvite.class).disclosureId());
        var fresh = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("fresh")));
        submit(room, fresh, NEW_PASSWORD); work.removeFirst().run(); room.tick();
        assertNotNull(last(fresh, ControlMessage.JoinAccepted.class));
        room.close("done");
    }

    @Test
    void oldQueuedPasswordSuccessCannotAdmitAfterPasswordReplacement() throws Exception {
        RoomHost room = create(true);
        var host = admit(room, owner, true);
        var pending = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("pending")));
        submit(room, pending, OLD_PASSWORD);
        try (RoomPassword password = RoomPassword.of(NEW_PASSWORD)) {
            command(room, host, new ControlMessage.RoomPasswordChange(password));
        }
        work.removeLast().run(); room.tick();
        assertEquals(1, room.accessEpoch());
        work.removeFirst().run(); room.tick();
        assertNull(last(pending, ControlMessage.JoinAccepted.class));
        assertEquals(1, room.playerCount());
        room.close("done");
    }
    @Test
    void completedPasswordChecksRecheckCapacityAndDuplicateIdentityOnTheRoomThread() throws Exception {
        for (boolean duplicate : new boolean[]{false, true}) {
            RoomHost room = create(true);
            List<FakeHubConnection> connections = new ArrayList<>();
            for (int index = 0; index < (duplicate ? 2 : 3); index++) {
                String name = duplicate ? "duplicate" : "capacity" + index;
                var connection = prove(room, PlayerIdentity.loadOrCreate(dir.resolve(name)));
                connections.add(connection);
                submit(room, connection, OLD_PASSWORD);
            }
            while (!work.isEmpty()) work.removeFirst().run();
            assertEquals(0, room.playerCount());
            room.tick();
            assertEquals(duplicate ? 1 : 2, room.playerCount());
            assertNull(last(connections.getLast(), ControlMessage.JoinAccepted.class));
            assertNotNull(last(connections.getLast(), ControlMessage.JoinRejected.class));
            room.close("done");
        }
    }

    @Test
    void verificationOverloadRejectsBeforeAnyMembershipPublication() throws Exception {
        boolean[] reject = {false};
        RoomPasswordService service = new RoomPasswordService(command -> {
            if (reject[0]) throw new RejectedExecutionException();
            work.addLast(command);
        });
        try (RoomPassword password = RoomPassword.of(OLD_PASSWORD)) {
            var owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
            RoomHost room = new RoomHost(new RoomHostConfig("Private", "s3k", 0, 0, "OPEN", null,
                    2, "build", List.of(), false, new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true),
                    password, null), owner, () -> now, TrackValidationProfileSource.none(),
                    RoomHostHooks.none(), codec, service);
            work.removeFirst().run(); room.ready().join();
            var peer = prove(room, PlayerIdentity.loadOrCreate(dir.resolve("guest")));
            reject[0] = true;
            submit(room, peer, OLD_PASSWORD); room.tick();
            assertEquals(0, room.playerCount());
            assertNull(last(peer, ControlMessage.JoinAccepted.class));
            assertNull(last(peer, ControlMessage.RoomState.class));
            assertEquals("room access denied", last(peer, ControlMessage.JoinRejected.class).reason());
            room.close("done");
        }
    }

}
