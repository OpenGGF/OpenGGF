package openggf.racing.server.master;

import com.openggf.net.client.MasterClient;
import com.openggf.net.client.DirectRoomRegistration;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RaceConnection;
import com.openggf.net.client.RoomAccessRequiredException;
import com.openggf.net.client.RoomJoinOptions;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import openggf.racing.server.host.RaceHostImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class TestPrivateRoomTransports {
    private static final String BUILD = "private-transport:test";
    private static final String PASSWORD = "a private room password for transport checks";
    private static final String NEW_PASSWORD = "a replacement private room password";
    private static final RoomAccessPolicy PRIVATE = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true);
    private final List<MasterClient> masters = new ArrayList<>();
    private final List<RaceConnection> joined = new ArrayList<>();
    private MasterServer server;
    private RaceRoomHost direct;
    private DirectRoomRegistration directRegistration;

    @AfterEach
    void close() {
        joined.forEach(RaceConnection::close);
        if (directRegistration != null) directRegistration.close();
        masters.forEach(MasterClient::close);
        if (direct != null) direct.close();
        if (server != null) server.close();
    }

    @Test
    void relayCreatorAndGuestsRequirePasswordAndShareAuthoritativeInvitePolicy(@TempDir Path directory)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), directory.resolve("master"));
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(directory.resolve("owner"));
        MasterClient registration = connect(owner, "Owner");
        establish(owner);
        ControlMessage.RoomCreated created;
        try (RoomPassword password = RoomPassword.of(PASSWORD)) {
            created = registration.createRelayRoom(descriptor(), BUILD, List.of(), password)
                    .get(15, TimeUnit.SECONDS);
        }
        assertNotNull(created.instanceId());
        MasterClient missing = connect(owner, "Owner");
        ControlMessage.RoomJoinResult metadata = missing.requestJoin(created.roomId()).get(10, TimeUnit.SECONDS);
        try (RoomJoinOptions options = RoomJoinOptions.publicRoom()) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> missing.joinResolved(
                    metadata, owner, "Owner", "", BUILD, options).get(10, TimeUnit.SECONDS));
            assertInstanceOf(RoomAccessRequiredException.class, failure.getCause());
        }
        missing.close();
        assertEquals(0, relayPlayers(created.roomId()));

        MasterClient wrong = connect(owner, "Owner");
        ControlMessage.RoomJoinResult wrongMetadata = wrong.requestJoin(created.roomId()).get(10, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> join(wrong, wrongMetadata, owner, "Owner",
                "the wrong private room password"));
        wrong.close();
        assertEquals(0, relayPlayers(created.roomId()));

        MasterClient ownerSocket = connect(owner, "Owner");
        RaceConnection ownerRoom = join(ownerSocket,
                ownerSocket.requestJoin(created.roomId()).get(10, TimeUnit.SECONDS), owner, "Owner", PASSWORD);
        assertEquals(0, ownerRoom.joinAccepted().playerSlot());
        assertEquals(PRIVATE, ownerRoom.joinAccepted().room().accessPolicy());
        ownerRoom.sendControl(new ControlMessage.RoomInviteRequest());
        ControlMessage.RoomInvite invitation = await(ownerRoom, ControlMessage.RoomInvite.class);

        PlayerIdentity guest = PlayerIdentity.loadOrCreate(directory.resolve("guest"));
        MasterClient guestMaster = connect(guest, "Guest");
        assertTrue(guestMaster.listRooms(null, 0).get(10, TimeUnit.SECONDS).rooms().isEmpty());
        assertThrows(ExecutionException.class, () -> guestMaster.requestJoin(created.roomId()).get(10, TimeUnit.SECONDS));
        ControlMessage.RoomJoinResult resolved = guestMaster.resolveInvite(invitation.code()).get(10, TimeUnit.SECONDS);
        RaceConnection guestRoom = join(guestMaster, resolved, guest, "Guest", PASSWORD);
        assertEquals(2, relayPlayers(created.roomId()));
        guestRoom.sendControl(new ControlMessage.RoomInviteRequest());
        assertEquals(invitation.code(), await(guestRoom, ControlMessage.RoomInvite.class).code());
        guestRoom.sendControl(new ControlMessage.RoomInviteRotate());
        await(guestRoom, ControlMessage.RoomInviteRejected.class);

        ownerRoom.sendControl(new ControlMessage.RoomInviteSharing(false));
        ControlMessage.RoomInvite rotated = await(ownerRoom, ControlMessage.RoomInvite.class);
        assertNotEquals(invitation.code(), rotated.code());
        guestRoom.sendControl(new ControlMessage.RoomInviteRequest());
        await(guestRoom, ControlMessage.RoomInviteRejected.class);
        assertEquals(2, relayPlayers(created.roomId()));

        try (RoomPassword password = RoomPassword.of(NEW_PASSWORD)) {
            ownerRoom.sendControl(new ControlMessage.RoomPasswordChange(password));
        }
        long nextEpoch = await(ownerRoom, ControlMessage.RoomPasswordChanged.class).accessEpoch();
        assertEquals(created.accessEpoch() + 1, nextEpoch);
        ControlMessage.RoomJoinResult changed = registration.requestJoin(created.roomId()).get(10, TimeUnit.SECONDS);
        assertEquals(nextEpoch, changed.accessEpoch());
        assertTrue(changed.accessPolicy().passwordRequired());
        assertFalse(changed.accessPolicy().membersCanInvite());
        guestRoom.sendControl(new ControlMessage.Ping(42));
        assertEquals(42, await(guestRoom, ControlMessage.Pong.class).t0ClientMillis());
        assertEquals(2, relayPlayers(created.roomId()));
    }

    @Test
    void privateFallbackTunnelUsesSamePasswordGateAndOwnerMasterInviteProvider(@TempDir Path directory)
            throws Exception {
        server = MasterServer.start(TestMasterServer.testConfig(), directory.resolve("master"));
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(directory.resolve("owner"));
        MasterClient registration = connect(owner, "Owner");
        establish(owner);
        try (RoomPassword password = RoomPassword.of(PASSWORD)) {
            direct = RaceHostImpl.NETTY.startTls(0, new RoomHostConfig("Private", "s3k", 0, 0,
                    "OPEN", null, 8, BUILD, List.of(), false, PRIVATE, password, "private-tunnel"),
                    owner, TrackValidationProfileSource.none());
        }
        int unreachable;
        try (ServerSocket reserve = new ServerSocket(0)) { unreachable = reserve.getLocalPort(); }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        direct.execute(() -> {
            direct.room().brokerPending();
            pending.complete(null);
        });
        pending.get(5, TimeUnit.SECONDS);
        directRegistration = DirectRoomRegistration.register(registration, direct, unreachable,
                BUILD, List.of()).get(15, TimeUnit.SECONDS);
        ControlMessage.RoomCreated created = directRegistration.created();
        RoomInviteService invites = registration.inviteService(created.roomId());
        ControlMessage.RoomInviteControlResult issued = invites.control(created.instanceId(),
                created.accessEpoch(), created.inviteEpoch(), ControlMessage.InviteOperation.FETCH, true)
                .get(10, TimeUnit.SECONDS);

        PlayerIdentity guest = PlayerIdentity.loadOrCreate(directory.resolve("guest"));
        MasterClient wrong = connect(guest, "Guest");
        ControlMessage.RoomJoinResult wrongMetadata = wrong.resolveInvite(issued.invite().code()).get(10, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> join(wrong, wrongMetadata, guest, "Guest",
                "the wrong private room password"));
        wrong.close();
        assertEquals(0, directPlayers());

        MasterClient correct = connect(guest, "Guest");
        ControlMessage.RoomJoinResult metadata = correct.resolveInvite(issued.invite().code()).get(10, TimeUnit.SECONDS);
        RaceConnection room = join(correct, metadata, guest, "Guest", PASSWORD);
        assertEquals(1, directPlayers());
        assertEquals(PRIVATE, room.joinAccepted().room().accessPolicy());
        room.sendControl(new ControlMessage.RoomInviteRequest());
        assertEquals(issued.invite().code(), await(room, ControlMessage.RoomInvite.class).code());
        room.sendControl(new ControlMessage.RoomInviteRotate());
        await(room, ControlMessage.RoomInviteRejected.class);
        assertTrue(registration.listRooms(null, 0).get(10, TimeUnit.SECONDS).rooms().isEmpty());
    }

    private static ControlMessage.RoomDescriptor descriptor() {
        return new ControlMessage.RoomDescriptor("Private", "s3k", 0, 0, "OPEN", null, 8, false, PRIVATE);
    }

    private MasterClient connect(PlayerIdentity identity, String name) throws Exception {
        MasterClient master = MasterClient.connect(URI.create("ws://127.0.0.1:" + server.port() + "/master"),
                identity, name, BUILD, null).get(15, TimeUnit.SECONDS);
        masters.add(master);
        return master;
    }

    private void establish(PlayerIdentity identity) throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        server.execute(() -> {
            server.establishForTest(identity.fingerprint());
            completed.complete(null);
        });
        completed.get(5, TimeUnit.SECONDS);
    }

    private RaceConnection join(MasterClient master, ControlMessage.RoomJoinResult metadata,
                                 PlayerIdentity identity, String name, String credential) throws Exception {
        try (RoomPassword password = RoomPassword.of(credential);
             RoomJoinOptions options = new RoomJoinOptions(password, metadata.instanceId(), metadata.accessEpoch())) {
            RaceConnection connection = master.joinResolved(metadata, identity, name, "", BUILD, options)
                    .get(15, TimeUnit.SECONDS);
            joined.add(connection);
            return connection;
        }
    }

    private int relayPlayers(String roomId) throws Exception {
        CompletableFuture<Integer> count = new CompletableFuture<>();
        server.execute(() -> {
            RelayRoomManager.RoomAccess access = server.relays().find(roomId).orElseThrow();
            access.loop().execute(() -> count.complete(access.room().playerCount()));
        });
        return count.get(5, TimeUnit.SECONDS);
    }

    private int directPlayers() throws Exception {
        CompletableFuture<Integer> count = new CompletableFuture<>();
        direct.execute(() -> count.complete(direct.room().playerCount()));
        return count.get(5, TimeUnit.SECONDS);
    }

    private static <T extends ControlMessage> T await(RaceConnection room, Class<T> type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            for (RaceClient.InboundEvent event : room.drainInbound()) {
                if (event instanceof RaceClient.Control control && type.isInstance(control.message())) {
                    return type.cast(control.message());
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("timed out waiting for " + type.getSimpleName());
    }
}
