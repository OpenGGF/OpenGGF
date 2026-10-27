package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.RoomAdmissionBridge;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Owns a listed direct room from pending listener through exact broker cleanup. */
@com.openggf.game.ModApi
public final class DirectRoomRegistration implements AutoCloseable {
    private final MasterClient master;
    private final RaceRoomHost server;
    private ControlMessage.RoomCreated created;
    private HostMasterLink link;
    private CompletableFuture<?> pending;
    private boolean closed;

    private DirectRoomRegistration(MasterClient master, RaceRoomHost server) {
        this.master = Objects.requireNonNull(master, "master");
        this.server = Objects.requireNonNull(server, "server");
    }

    public static CompletableFuture<DirectRoomRegistration> register(
            MasterClient master, RaceRoomHost server, String determinismFingerprint,
            List<String> voteTrackKeys) {
        return register(master, server, server.port(), determinismFingerprint, voteTrackKeys);
    }

    public static CompletableFuture<DirectRoomRegistration> register(
            MasterClient master, RaceRoomHost server, int advertisedPort,
            String determinismFingerprint, List<String> voteTrackKeys) {
        DirectRoomRegistration registration = new DirectRoomRegistration(master, server);
        CompletableFuture<DirectRoomRegistration> result = new CompletableFuture<>();
        result.whenComplete((value, failure) -> {
            if (failure != null) registration.close();
        });
        List<String> tracks = List.copyOf(voteTrackKeys);
        server.room().ready().thenCompose(ready -> registration.onOwner(() -> {
            if (server.room().listenerAdmission() != RoomHost.ListenerAdmission.BROKER_PENDING
                    || server.tlsCertificateSha256() == null) {
                throw new IllegalStateException("direct registration requires a pending TLS listener");
            }
            synchronized (registration) {
                registration.requireOpen();
                var creation = master.createRoom(server.room().descriptor(), "DIRECT", advertisedPort,
                        determinismFingerprint, tracks, server.tlsCertificateSha256(),
                        server.room().instanceId(), server.room().accessEpoch(), null);
                registration.pending = creation;
                return creation;
            }
        })).thenCompose(creation -> creation).thenCompose(registration::install)
                .thenCompose(ignored -> {
                    synchronized (registration) {
                        registration.requireCurrent();
                        var activation = master.activateRoom(registration.created.roomId(),
                                registration.created.instanceId());
                        registration.pending = activation;
                        return activation;
                    }
                }).whenComplete((activated, failure) -> {
                    if (failure != null) result.completeExceptionally(failure);
                    else if (!activated.active()
                            || !registration.created.roomId().equals(activated.roomId())
                            || !registration.created.instanceId().equals(activated.instanceId())) {
                        result.completeExceptionally(new RaceClient.JoinRejectedException(
                                "direct room activation unavailable"));
                    } else if (!result.complete(registration)) registration.close();
                });
        return result;
    }

    private CompletableFuture<Void> install(ControlMessage.RoomCreated room) {
        synchronized (this) {
            created = room;
            if (closed) {
                master.leaveRoom(room.roomId());
                return CompletableFuture.failedFuture(new IllegalStateException("registration is closed"));
            }
            link = HostMasterLink.forServer(server, new HostMasterLink.MessageSink() {
                @Override public void sendControl(ControlMessage message) { master.sendControl(message); }
                @Override public void sendBinary(byte[] data) { master.sendBinary(data); }
            });
            master.bindHostLink(link);
        }
        return onOwner(() -> {
            synchronized (this) {
                requireCurrent();
                RoomAdmissionBridge.install(server.room(), room.roomId(),
                        master.inviteService(room.roomId()), room.inviteEpoch(),
                        request -> master.redeemDirectGrant(request.roomId(), request.instanceId(),
                                request.grantReference(), request.routeGeneration(), request.fingerprint()));
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> onOwner(Supplier<T> action) {
        CompletableFuture<T> completed = new CompletableFuture<>();
        try {
            server.executeControl(() -> {
                try {
                    synchronized (this) { requireOpen(); }
                    completed.complete(action.get());
                } catch (RuntimeException failure) { completed.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { completed.completeExceptionally(failure); }
        return completed;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("registration is closed");
    }

    private void requireCurrent() {
        requireOpen();
        if (!master.hostLinkIs(link)) throw new IllegalStateException("registration was replaced");
    }

    public synchronized ControlMessage.RoomCreated created() {
        return created;
    }

    /** Joins the registered owner over its local listener using the broker's prepared grant. */
    public CompletableFuture<RaceConnection> joinOwner(
            ControlMessage.RoomJoinResult prepared, PlayerIdentity identity, String displayName,
            String appearance, String determinismFingerprint, RoomJoinOptions options) {
        synchronized (this) {
            try {
                requireCurrent();
                if (prepared == null || created == null
                        || !created.roomId().equals(prepared.roomId())
                        || !created.instanceId().equals(prepared.instanceId())) {
                    throw new IllegalArgumentException("prepared join is not for this registration");
                }
            } catch (RuntimeException invalid) {
                return CompletableFuture.failedFuture(invalid);
            }
        }
        return master.joinLocalOwner(prepared, server, identity, displayName,
                appearance, determinismFingerprint, options);
    }

    @Override
    public void close() {
        CompletableFuture<?> cancel;
        ControlMessage.RoomCreated room;
        HostMasterLink installed;
        synchronized (this) {
            if (closed) return;
            closed = true;
            cancel = pending;
            room = created;
            installed = link;
        }
        if (cancel != null) cancel.cancel(true);
        try { master.unbindHostLink(installed); }
        finally { if (room != null) master.leaveRoom(room.roomId()); }
    }
}
