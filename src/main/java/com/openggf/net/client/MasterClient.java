package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Client for master browsing plus direct/relay room join orchestration. */
@com.openggf.game.ModApi
public final class MasterClient implements AutoCloseable {
    public static final long MASTER_REPLY_TIMEOUT_MILLIS = 5_000;

    private final ConcurrentLinkedQueue<RaceClient.InboundEvent> inbound =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomListResult>>
            pendingLists = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomCreated>>
            pendingCreates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomJoinResult>>
            pendingJoins = new ConcurrentLinkedQueue<>();
    private final AtomicReference<RelayRaceConnection> attached = new AtomicReference<>();
    private final Object sendLock = new Object();
    private final PlayerIdentity identity;
    private final ClientHandshake handshake;
    private volatile WebSocket webSocket;
    private volatile ControlMessage.JoinAccepted joinAccepted;
    private volatile HostMasterLink hostLink;
    private volatile boolean open;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    private MasterClient(PlayerIdentity identity, String displayName,
                         String determinismFingerprint) {
        this.identity = identity;
        handshake = new ClientHandshake(identity, displayName, determinismFingerprint);
    }

    public static CompletableFuture<MasterClient> connect(
            URI uri, PlayerIdentity identity, String displayName,
            String determinismFingerprint, SSLContext sslContextOrNull) {
        MasterClient client = new MasterClient(identity, displayName, determinismFingerprint);
        CompletableFuture<MasterClient> admitted = new CompletableFuture<>();
        WebSocket.Listener listener = client.listener(admitted);
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(MASTER_REPLY_TIMEOUT_MILLIS));
        if (sslContextOrNull != null) {
            http.sslContext(sslContextOrNull);
        }
        http.build().newWebSocketBuilder().buildAsync(uri, listener)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        admitted.completeExceptionally(error);
                        return;
                    }
                    client.webSocket = socket;
                    if (admitted.isDone()) {
                        socket.abort();
                        return;
                    }
                    client.sendRawText(ControlCodec.encode(null, client.handshake.hello()));
                });
        admitted.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        WebSocket socket = client.webSocket;
                        if (socket != null) {
                            socket.abort();
                        }
                    }
                });
        return admitted;
    }

    public CompletableFuture<ControlMessage.RoomListResult> listRooms(
            String gameFilter, int page) {
        CompletableFuture<ControlMessage.RoomListResult> future = new CompletableFuture<>();
        pendingLists.add(future);
        sendControl(new ControlMessage.RoomListRequest(gameFilter, page));
        return future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public String masterSessionToken() {
        return joinAccepted == null ? null : joinAccepted.sessionToken();
    }

    public CompletableFuture<ControlMessage.RoomCreated> createRoom(
            ControlMessage.RoomDescriptor descriptor, String routing, int directPort,
            String determinismFingerprint) {
        return createRoom(descriptor, routing, directPort, determinismFingerprint, List.of());
    }

    public CompletableFuture<ControlMessage.RoomCreated> createRoom(
            ControlMessage.RoomDescriptor descriptor, String routing, int directPort,
            String determinismFingerprint, List<String> voteTrackKeys) {
        CompletableFuture<ControlMessage.RoomCreated> future = new CompletableFuture<>();
        pendingCreates.add(future);
        sendControl(new ControlMessage.RoomCreate(descriptor, routing, directPort,
                determinismFingerprint, voteTrackKeys));
        return future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<ControlMessage.RoomJoinResult> requestJoin(String roomId) {
        CompletableFuture<ControlMessage.RoomJoinResult> future = new CompletableFuture<>();
        pendingJoins.add(future);
        sendControl(new ControlMessage.RoomJoinRequest(roomId));
        return future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<RaceConnection> joinRoom(
            String roomId, PlayerIdentity roomIdentity, String displayName,
            String determinismFingerprint) {
        return requestJoin(roomId).thenCompose(result -> {
            if ("RELAY".equals(result.routing())) {
                return relayHandshake(roomId, roomIdentity, displayName,
                        determinismFingerprint);
            }
            URI directUri = URI.create("ws://" + result.directHost() + ":"
                    + result.directPort() + "/race");
            return RaceClient.connect(directUri, roomIdentity, displayName,
                            determinismFingerprint)
                    .thenApply(client -> {
                        if (!java.util.Objects.equals(
                                result.hostServerId(), client.serverId())) {
                            client.close();
                            throw new RaceClient.JoinRejectedException(
                                    "host identity mismatch");
                        }
                        return (RaceConnection) client;
                    }).exceptionallyCompose(error -> relayHandshake(roomId, roomIdentity,
                            displayName, determinismFingerprint));
        });
    }

    private CompletableFuture<RaceConnection> relayHandshake(
            String roomId, PlayerIdentity roomIdentity, String displayName,
            String determinismFingerprint) {
        try {
            return completeRoomHandshake(attachRelay(roomId), roomIdentity,
                    displayName, determinismFingerprint);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public RaceConnection attachRelay(String roomId) {
        RelayRaceConnection connection = new RelayRaceConnection(this);
        if (!attached.compareAndSet(null, connection)) {
            throw new IllegalStateException("master socket is already attached to a room");
        }
        sendControl(new ControlMessage.RelayAttach(roomId));
        return connection;
    }

    public static CompletableFuture<RaceConnection> completeRoomHandshake(
            RaceConnection raw, PlayerIdentity identity, String displayName,
            String determinismFingerprint) {
        CompletableFuture<RaceConnection> joined = new CompletableFuture<>();
        Thread.ofVirtual().name("relay-room-handshake").start(() -> {
            ClientHandshake handshake = new ClientHandshake(
                    identity, displayName, determinismFingerprint);
            raw.sendControl(handshake.hello());
            long deadline = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(RaceClient.JOIN_TIMEOUT_MILLIS);
            try {
                while (!joined.isDone() && System.nanoTime() < deadline) {
                    List<RaceClient.InboundEvent> events = raw.drainInbound();
                    for (int index = 0; index < events.size(); index++) {
                        RaceClient.InboundEvent event = events.get(index);
                        switch (event) {
                            case RaceClient.Control control -> {
                                switch (control.message()) {
                                    case ControlMessage.Welcome welcome -> raw.sendControl(
                                            handshake.onWelcome(welcome));
                                    case ControlMessage.JoinAccepted accepted -> {
                                        if (raw instanceof RelayRaceConnection relay) {
                                            relay.acceptJoin(accepted);
                                            relay.restore(events.subList(index + 1, events.size()));
                                        }
                                        joined.complete(raw);
                                    }
                                    case ControlMessage.JoinRejected rejected ->
                                            joined.completeExceptionally(
                                                    new RaceClient.JoinRejectedException(
                                                            rejected.reason()));
                                    default -> { }
                                }
                            }
                            case RaceClient.Disconnected disconnected ->
                                    joined.completeExceptionally(
                                            new RaceClient.JoinRejectedException(
                                                    disconnected.reason()));
                            default -> { }
                        }
                        if (joined.isDone()) {
                            break;
                        }
                    }
                    if (!joined.isDone()) {
                        Thread.sleep(5);
                    }
                }
                if (!joined.isDone()) {
                    joined.completeExceptionally(new RaceClient.JoinRejectedException(
                            "room handshake timed out"));
                }
            } catch (Exception e) {
                joined.completeExceptionally(e);
            }
        });
        joined.whenComplete((ignored, error) -> {
            if (error != null) {
                raw.close();
            }
        });
        return joined;
    }

    public void heartbeat(String roomId, int playerCount) {
        sendControl(new ControlMessage.Heartbeat(roomId, playerCount));
    }

    public void leaveRoom(String roomId) {
        sendControl(new ControlMessage.RoomLeave(roomId));
    }

    public void bindHostLink(HostMasterLink link) {
        hostLink = link;
    }

    public void sendControl(ControlMessage message) {
        if (open) {
            sendRawText(ControlCodec.encode(joinAccepted.sessionToken(), message));
        }
    }

    public void sendBinary(byte[] data) {
        sendRawBinary(data);
    }

    public List<RaceClient.InboundEvent> drainInbound() {
        List<RaceClient.InboundEvent> result = new ArrayList<>();
        RaceClient.InboundEvent event;
        while ((event = inbound.poll()) != null) {
            result.add(event);
        }
        return result;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
        WebSocket socket = webSocket;
        if (socket != null) {
            synchronized (sendLock) {
                sendChain = sendChain.thenCompose(ignored ->
                                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
                        .exceptionally(error -> null);
            }
        }
    }

    private WebSocket.Listener listener(CompletableFuture<MasterClient> admitted) {
        return new WebSocket.Listener() {
            private final FrameAssembler assembler = new FrameAssembler(
                    Protocol.MAX_MASTER_FRAME_BYTES, Protocol.MAX_MASTER_FRAME_BYTES);

            @Override
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                try {
                    String whole = assembler.onTextPart(data, last);
                    if (whole != null) {
                        receiveText(socket, whole, admitted);
                    }
                } catch (ProtocolViolationException e) {
                    fail(socket, admitted, e);
                }
                socket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
                try {
                    byte[] whole = assembler.onBinaryPart(data, last);
                    if (whole != null) {
                        receiveBinary(whole);
                    }
                } catch (ProtocolViolationException e) {
                    fail(socket, admitted, e);
                }
                socket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
                open = false;
                RaceClient.Disconnected disconnected = new RaceClient.Disconnected(
                        reason == null || reason.isBlank() ? "connection closed" : reason);
                RelayRaceConnection relay = attached.get();
                if (relay != null) {
                    relay.inbound.add(disconnected);
                } else {
                    inbound.add(disconnected);
                }
                admitted.completeExceptionally(new RaceClient.JoinRejectedException(
                        "master closed during admission"));
                return null;
            }

            @Override
            public void onError(WebSocket socket, Throwable error) {
                open = false;
                RaceClient.Disconnected disconnected = new RaceClient.Disconnected(
                        error.getMessage() == null ? error.getClass().getSimpleName()
                                : error.getMessage());
                RelayRaceConnection relay = attached.get();
                if (relay != null) {
                    relay.inbound.add(disconnected);
                } else {
                    inbound.add(disconnected);
                }
                admitted.completeExceptionally(error);
            }
        };
    }

    private void receiveText(WebSocket socket, String text,
                             CompletableFuture<MasterClient> admitted) {
        RelayRaceConnection relay = attached.get();
        if (relay != null) {
            relay.acceptText(text);
            return;
        }
        final ControlMessage message;
        try {
            message = ControlCodec.decode(text, Protocol.MAX_MASTER_FRAME_BYTES).message();
        } catch (ProtocolViolationException e) {
            fail(socket, admitted, e);
            return;
        }
        if (joinAccepted == null) {
            receiveAdmission(socket, message, admitted);
            return;
        }
        if (completeReply(message)) {
            return;
        }
        HostMasterLink link = hostLink;
        if (link != null && (message instanceof ControlMessage.RelayGuestOpen
                || message instanceof ControlMessage.RelayGuestText
                || message instanceof ControlMessage.RelayGuestClose)) {
            link.onMasterControl(message);
            return;
        }
        inbound.add(new RaceClient.Control(message));
    }

    private void receiveAdmission(WebSocket socket, ControlMessage message,
                                  CompletableFuture<MasterClient> admitted) {
        try {
            switch (message) {
                case ControlMessage.Welcome welcome -> sendRawText(ControlCodec.encode(
                        null, handshake.onWelcome(welcome)));
                case ControlMessage.PowChallenge challenge ->
                        Thread.ofVirtual().start(() -> solveChallenge(socket, challenge, admitted));
                case ControlMessage.JoinAccepted accepted -> {
                    joinAccepted = accepted;
                    open = true;
                    admitted.complete(this);
                }
                case ControlMessage.JoinRejected rejected -> {
                    admitted.completeExceptionally(new RaceClient.JoinRejectedException(
                            rejected.reason()));
                    socket.abort();
                }
                default -> { }
            }
        } catch (Exception e) {
            fail(socket, admitted, e);
        }
    }

    private void solveChallenge(WebSocket socket, ControlMessage.PowChallenge challenge,
                                CompletableFuture<MasterClient> admitted) {
        try {
            long nonce = switch (challenge.kind()) {
                case "IDENTITY" -> identity.creationPowNonce(challenge.difficultyBits());
                case "JOIN" -> ProofOfWork.solve(
                        Base64.getDecoder().decode(challenge.prefixBase64()),
                        challenge.difficultyBits());
                default -> throw new ProtocolViolationException("unknown proof challenge");
            };
            sendRawText(ControlCodec.encode(null,
                    new ControlMessage.PowSolution(challenge.kind(), nonce)));
        } catch (Exception e) {
            fail(socket, admitted, e);
        }
    }

    private boolean completeReply(ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomListResult result -> {
                return completeNext(pendingLists, result, null);
            }
            case ControlMessage.RoomCreated created -> {
                return completeNext(pendingCreates, created, null);
            }
            case ControlMessage.RoomCreateRejected rejected -> {
                return completeNext(pendingCreates, null,
                        new RaceClient.JoinRejectedException(rejected.reason()));
            }
            case ControlMessage.RoomJoinResult result -> {
                return completeNext(pendingJoins, result, null);
            }
            case ControlMessage.RoomJoinRejected rejected -> {
                return completeNext(pendingJoins, null,
                        new RaceClient.JoinRejectedException(rejected.reason()));
            }
            default -> {
                return false;
            }
        }
    }

    private void receiveBinary(byte[] data) {
        RelayRaceConnection relay = attached.get();
        if (relay != null) {
            relay.acceptBinary(data);
            return;
        }
        HostMasterLink link = hostLink;
        if (link != null) {
            link.onMasterBinary(data);
        }
    }

    private void sendRawText(String text) {
        WebSocket socket = webSocket;
        if (socket == null) {
            return;
        }
        synchronized (sendLock) {
            sendChain = sendChain.thenCompose(ignored -> socket.sendText(text, true))
                    .exceptionally(error -> null);
        }
    }

    private void sendRawBinary(byte[] data) {
        WebSocket socket = webSocket;
        if (socket == null || !open) {
            return;
        }
        byte[] copy = data.clone();
        synchronized (sendLock) {
            sendChain = sendChain.thenCompose(ignored ->
                            socket.sendBinary(ByteBuffer.wrap(copy), true))
                    .exceptionally(error -> null);
        }
    }

    private void fail(WebSocket socket, CompletableFuture<MasterClient> admitted,
                      Throwable error) {
        open = false;
        admitted.completeExceptionally(error);
        socket.abort();
    }

    private static <T> boolean completeNext(
            ConcurrentLinkedQueue<CompletableFuture<T>> queue, T value, Throwable error) {
        CompletableFuture<T> future;
        while ((future = queue.poll()) != null && future.isDone()) {
            // discard timed-out/cancelled requests before matching the next reply
        }
        if (future == null) {
            return false;
        }
        if (error == null) {
            future.complete(value);
        } else {
            future.completeExceptionally(error);
        }
        return true;
    }

    private static final class RelayRaceConnection implements RaceConnection {
        private final MasterClient parent;
        private final ConcurrentLinkedQueue<RaceClient.InboundEvent> inbound =
                new ConcurrentLinkedQueue<>();
        private volatile ControlMessage.JoinAccepted joined;
        private volatile boolean open = true;

        private RelayRaceConnection(MasterClient parent) {
            this.parent = parent;
        }

        void acceptText(String text) {
            try {
                inbound.add(new RaceClient.Control(ControlCodec.decode(text).message()));
            } catch (ProtocolViolationException e) {
                inbound.add(new RaceClient.Disconnected("protocol violation"));
                close();
            }
        }

        void acceptBinary(byte[] data) {
            try {
                if (data.length == 0) {
                    throw new ProtocolViolationException("empty binary packet");
                }
                int type = data[0] & 0xFF;
                if (type == GhostPackets.TYPE_GHOST_AGGREGATE) {
                    inbound.add(new RaceClient.GhostData(GhostPackets.decodeAggregate(data)));
                } else if (type == GhostPackets.TYPE_ROSTER) {
                    inbound.add(new RaceClient.Roster(GhostPackets.decodeRoster(data)));
                } else {
                    throw new ProtocolViolationException(
                            "unexpected room binary packet type " + type);
                }
            } catch (ProtocolViolationException e) {
                inbound.add(new RaceClient.Disconnected("protocol violation"));
                close();
            }
        }

        void acceptJoin(ControlMessage.JoinAccepted accepted) {
            joined = accepted;
        }

        void restore(List<RaceClient.InboundEvent> events) {
            inbound.addAll(events);
        }

        @Override public List<RaceClient.InboundEvent> drainInbound() {
            List<RaceClient.InboundEvent> result = new ArrayList<>();
            RaceClient.InboundEvent event;
            while ((event = inbound.poll()) != null) {
                result.add(event);
            }
            return result;
        }

        @Override public void sendControl(ControlMessage message) {
            if (open) {
                parent.sendRawText(ControlCodec.encode(sessionToken(), message));
            }
        }
        @Override public void sendBinary(byte[] data) { parent.sendRawBinary(data); }
        @Override public int playerSlot() { return joined == null ? -1 : joined.playerSlot(); }
        @Override public String sessionToken() {
            return joined == null ? null : joined.sessionToken();
        }
        @Override public String uploadSessionToken() {
            return parent.joinAccepted == null ? null
                    : parent.joinAccepted.sessionToken();
        }
        @Override public ControlMessage.JoinAccepted joinAccepted() { return joined; }
        @Override public boolean isOpen() { return open && parent.isOpen(); }
        @Override public void close() {
            open = false;
            parent.close();
        }
    }
}
