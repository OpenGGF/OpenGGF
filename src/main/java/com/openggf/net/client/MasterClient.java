package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Client for master browsing plus direct/relay room join orchestration. */
@com.openggf.game.ModApi
public final class MasterClient implements AutoCloseable {
    public static final long MASTER_REPLY_TIMEOUT_MILLIS = 5_000;

    private final BoundedInboundQueue<RaceClient.InboundEvent> inbound =
            new BoundedInboundQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomListResult>>
            pendingLists = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomCreated>>
            pendingCreates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<ControlMessage.RoomJoinResult>>
            pendingJoins = new ConcurrentLinkedQueue<>();
    private final AtomicReference<RelayRaceConnection> attached = new AtomicReference<>();
    private final AtomicBoolean retired = new AtomicBoolean();
    /** Failed retirement discards writes; graceful close flushes writes already accepted. */
    private volatile boolean discardQueuedSends;
    private final Object sendLock = new Object();
    private final ControlJsonCodec codec = new ControlJsonCodec();
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
                    client.sendRawText(client.codec.encode(null, client.handshake.hello()));
                });
        admitted.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        client.retire("master admission failed");
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
        return request(pendingLists, new ControlMessage.RoomListRequest(gameFilter, page));
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
        return createRoom(descriptor, routing, directPort, determinismFingerprint,
                voteTrackKeys, null);
    }

    CompletableFuture<ControlMessage.RoomCreated> createRoom(
            ControlMessage.RoomDescriptor descriptor, String routing, int directPort,
            String determinismFingerprint, List<String> voteTrackKeys,
            String certificateSha256) {
        return request(pendingCreates, new ControlMessage.RoomCreate(descriptor, routing, directPort,
                determinismFingerprint, voteTrackKeys, certificateSha256));
    }

    public CompletableFuture<ControlMessage.RoomJoinResult> requestJoin(String roomId) {
        return request(pendingJoins, new ControlMessage.RoomJoinRequest(roomId));
    }

    private <T> CompletableFuture<T> request(ConcurrentLinkedQueue<CompletableFuture<T>> queue,
                                             ControlMessage message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        PendingSend send;
        synchronized (sendLock) {
            if (retired.get() || !open) {
                return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException(
                        "master connection is closed; reconnect before retrying"));
            }
            String text = codec.encode(joinAccepted.sessionToken(), message);
            WebSocket socket = webSocket;
            // Reserve both FIFO positions atomically, but invoke neither the
            // transport nor future continuations while holding the lock.
            queue.add(future);
            send = reserveSend(() -> discardQueuedSends ? CompletableFuture.completedFuture(socket)
                    : socket.sendText(text, true));
        }
        future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((value, failure) -> {
                    if (failure instanceof TimeoutException || future.isCancelled()) {
                        // No wire request IDs exist. Reusing this socket after an
                        // unanswered/cancelled request would make later replies ambiguous.
                        retire("master reply was not received");
                        WebSocket socket = webSocket;
                        if (socket != null) socket.abort();
                    }
                });
        send.dispatch();
        return future;
    }

    public CompletableFuture<RaceConnection> joinRoom(
            String roomId, PlayerIdentity roomIdentity, String displayName,
            String determinismFingerprint) {
        return requestJoin(roomId).thenCompose(result -> {
            if ("RELAY".equals(result.routing())) {
                return relayHandshake(roomId, roomIdentity, displayName,
                        determinismFingerprint, result.hostServerId());
            }
            if (result.certificateSha256() == null || result.hostServerId() == null) {
                return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException(
                        "direct room has no authenticated host identity"));
            }
            final URI directUri;
            try {
                // The broker supplies a numeric address, including unbracketed IPv6.
                directUri = new URI("wss", null, result.directHost(),
                        result.directPort(), "/race", null, null);
            } catch (URISyntaxException e) {
                return CompletableFuture.failedFuture(e);
            }
            return RaceClient.connect(directUri, roomIdentity, displayName,
                            determinismFingerprint, result.certificateSha256(),
                            result.hostServerId())
                    .thenApply(client -> {
                        if (!java.util.Objects.equals(
                                result.hostServerId(), client.serverId())) {
                            client.close();
                            throw new RaceClient.JoinRejectedException(
                                    "host identity mismatch");
                        }
                        return (RaceConnection) client;
                    }).exceptionallyCompose(error -> relayHandshake(roomId, roomIdentity,
                            displayName, determinismFingerprint, result.hostServerId()));
        });
    }

    private CompletableFuture<RaceConnection> relayHandshake(
            String roomId, PlayerIdentity roomIdentity, String displayName,
            String determinismFingerprint, String expectedServerId) {
        try {
            return completeRoomHandshake(attachRelay(roomId), roomIdentity,
                    displayName, determinismFingerprint, expectedServerId);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public RaceConnection attachRelay(String roomId) {
        if (!open || retired.get()) {
            throw new RaceClient.JoinRejectedException("master connection is closed; reconnect before retrying");
        }
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
        return completeRoomHandshake(raw, identity, displayName,
                determinismFingerprint, null);
    }

    private static CompletableFuture<RaceConnection> completeRoomHandshake(
            RaceConnection raw, PlayerIdentity identity, String displayName,
            String determinismFingerprint, String expectedServerId) {
        CompletableFuture<RaceConnection> joined = new CompletableFuture<>();
        Thread.ofVirtual().name("relay-room-handshake").start(() -> {
            ClientHandshake handshake = new ClientHandshake(
                    identity, displayName, determinismFingerprint, expectedServerId);
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
                                    // The broker answers a failed RelayAttach with
                                    // RoomJoinRejected on the already-attached socket.
                                    case ControlMessage.RoomJoinRejected rejected ->
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
            sendRawText(codec.encode(joinAccepted.sessionToken(), message));
        }
    }

    public void sendBinary(byte[] data) {
        sendRawBinary(data);
    }

    public List<RaceClient.InboundEvent> drainInbound() {
        return inbound.drain();
    }

    private void enqueueInbound(WebSocket socket, RaceClient.InboundEvent event) {
        if (!inbound.offer(event)) {
            retire("inbound event limit exceeded");
            socket.abort();
        }
    }

    private void signalDisconnected(String reason) {
        RaceClient.Disconnected event = new RaceClient.Disconnected(reason);
        if (!inbound.offer(event)) {
            inbound.clear();
            inbound.offer(event);
        }
    }

    public boolean isOpen() {
        return open && !retired.get();
    }

    @Override
    public void close() {
        if (!retire("master connection closed", false)) return;
        WebSocket socket = webSocket;
        if (socket != null) {
            PendingSend send;
            synchronized (sendLock) {
                send = reserveSend(() -> discardQueuedSends ? CompletableFuture.completedFuture(socket)
                        : socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye"));
            }
            send.dispatch();
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
                retire(reason == null || reason.isBlank() ? "connection closed" : reason);
                admitted.completeExceptionally(new RaceClient.JoinRejectedException(
                        "master closed during admission"));
                return null;
            }

            @Override
            public void onError(WebSocket socket, Throwable error) {
                retire(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                admitted.completeExceptionally(error);
            }
        };
    }

    private void receiveText(WebSocket socket, String text,
                             CompletableFuture<MasterClient> admitted) {
        if (retired.get()) return;
        RelayRaceConnection relay = attached.get();
        if (relay != null) {
            relay.acceptText(text);
            return;
        }
        final ControlMessage message;
        try {
            message = codec.decode(text, Protocol.MAX_MASTER_FRAME_BYTES).message();
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
        enqueueInbound(socket, new RaceClient.Control(message));
    }

    private void receiveAdmission(WebSocket socket, ControlMessage message,
                                  CompletableFuture<MasterClient> admitted) {
        try {
            switch (message) {
                case ControlMessage.Welcome welcome -> sendRawText(codec.encode(
                        null, handshake.onWelcome(welcome)));
                case ControlMessage.PowChallenge challenge ->
                        Thread.ofVirtual().start(() -> solveChallenge(socket, challenge, admitted));
                case ControlMessage.JoinAccepted accepted -> {
                    if (accepted.playerSlot() != -1 || accepted.room() != null
                            || accepted.round() != null) {
                        throw new ProtocolViolationException("invalid master admission");
                    }
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
            sendRawText(codec.encode(null,
                    new ControlMessage.PowSolution(challenge.kind(), nonce)));
        } catch (Exception e) {
            fail(socket, admitted, e);
        }
    }

    private boolean completeReply(ControlMessage message) {
        ReplyCompletion<?> reply;
        synchronized (sendLock) {
            if (retired.get()) return true;
            // Dequeuing claims this reply for exactly one request. Retirement
            // can fail the remaining queue, never reassign this frame to it.
            reply = switch (message) {
                case ControlMessage.RoomListResult result ->
                        result.totalPages() == Protocol.ROOM_LIST_RATE_LIMITED
                                ? takeNext(pendingLists, null,
                                        new IllegalStateException("room list rate limited"))
                                : takeNext(pendingLists, result, null);
                case ControlMessage.RoomCreated created -> takeNext(pendingCreates, created, null);
                case ControlMessage.RoomCreateRejected rejected -> takeNext(pendingCreates, null,
                        new RaceClient.JoinRejectedException(rejected.reason()));
                case ControlMessage.RoomJoinResult result -> takeNext(pendingJoins, result, null);
                case ControlMessage.RoomJoinRejected rejected -> takeNext(pendingJoins, null,
                        new RaceClient.JoinRejectedException(rejected.reason()));
                default -> null;
            };
        }
        if (reply == null) return false;
        reply.complete();
        return true;
    }

    private void receiveBinary(byte[] data) {
        if (retired.get()) return;
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
        PendingSend send;
        synchronized (sendLock) {
            if (retired.get()) return;
            send = reserveSend(() -> discardQueuedSends ? CompletableFuture.completedFuture(socket)
                    : socket.sendText(text, true));
        }
        send.dispatch();
    }

    private void sendRawBinary(byte[] data) {
        WebSocket socket = webSocket;
        if (socket == null || !open) {
            return;
        }
        byte[] copy = data.clone();
        PendingSend send;
        synchronized (sendLock) {
            if (retired.get()) return;
            send = reserveSend(() -> discardQueuedSends ? CompletableFuture.completedFuture(socket)
                    : socket.sendBinary(ByteBuffer.wrap(copy), true));
        }
        send.dispatch();
    }

    /** Must be called under sendLock; dispatch is deliberately separate and outside it. */
    private PendingSend reserveSend(Supplier<CompletableFuture<WebSocket>> action) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PendingSend send = new PendingSend(sendChain, completion, action);
        sendChain = completion;
        return send;
    }

    private record PendingSend(CompletableFuture<?> previous, CompletableFuture<Void> completion,
                               Supplier<CompletableFuture<WebSocket>> action) {
        void dispatch() {
            previous.handle((value, failure) -> null)
                    .thenCompose(ignored -> action.get())
                    .whenComplete((value, failure) -> completion.complete(null));
        }
    }

    private void fail(WebSocket socket, CompletableFuture<MasterClient> admitted,
                      Throwable error) {
        retire(error.getMessage() == null ? "master connection failed" : error.getMessage());
        admitted.completeExceptionally(error);
        socket.abort();
    }

    private boolean retire(String reason) {
        return retire(reason, true);
    }

    private boolean retire(String reason, boolean discardAcceptedSends) {
        RaceClient.JoinRejectedException failure = new RaceClient.JoinRejectedException(
                reason + "; reconnect before retrying");
        List<CompletableFuture<?>> pending = new ArrayList<>();
        synchronized (sendLock) {
            // Publish failure before any future continuation can release an
            // earlier transport write. Failure also escalates a graceful close
            // whose accepted writes or close frame have not yet been dispatched.
            if (discardAcceptedSends) discardQueuedSends = true;
            if (!retired.compareAndSet(false, true)) return false;
            open = false;
            drainPending(pendingLists, pending);
            drainPending(pendingCreates, pending);
            drainPending(pendingJoins, pending);
        }
        for (CompletableFuture<?> future : pending) future.completeExceptionally(failure);
        RelayRaceConnection relay = attached.get();
        if (relay != null) relay.signalDisconnected(failure.getMessage());
        else signalDisconnected(failure.getMessage());
        return true;
    }

    private static <T> void drainPending(ConcurrentLinkedQueue<CompletableFuture<T>> queue,
                                         List<CompletableFuture<?>> pending) {
        CompletableFuture<T> future;
        while ((future = queue.poll()) != null) pending.add(future);
    }

    private static <T> ReplyCompletion<T> takeNext(
            ConcurrentLinkedQueue<CompletableFuture<T>> queue, T value, Throwable error) {
        CompletableFuture<T> future = queue.poll();
        return future == null ? null : new ReplyCompletion<>(future, value, error);
    }

    private record ReplyCompletion<T>(CompletableFuture<T> future, T value, Throwable error) {
        void complete() {
            // An already completed/cancelled request still consumes its own
            // reply; completion cannot spill over into the next FIFO entry.
            if (error == null) future.complete(value);
            else future.completeExceptionally(error);
        }
    }

    private static final class RelayRaceConnection implements RaceConnection {
        private final MasterClient parent;
        private final BoundedInboundQueue<RaceClient.InboundEvent> inbound =
                new BoundedInboundQueue<>();
        private volatile ControlMessage.JoinAccepted joined;
        private volatile boolean open = true;

        private RelayRaceConnection(MasterClient parent) {
            this.parent = parent;
        }

        private void enqueue(RaceClient.InboundEvent event) {
            if (!inbound.offer(event)) {
                signalDisconnected("inbound event limit exceeded");
                close();
            }
        }

        private void signalDisconnected(String reason) {
            RaceClient.Disconnected event = new RaceClient.Disconnected(reason);
            if (!inbound.offer(event)) {
                inbound.clear();
                inbound.offer(event);
            }
        }

        void acceptText(String text) {
            try {
                enqueue(new RaceClient.Control(parent.codec.decodeRoom(text).message()));
            } catch (ProtocolViolationException e) {
                signalDisconnected("protocol violation");
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
                    enqueue(new RaceClient.GhostData(GhostPackets.decodeAggregate(data)));
                } else if (type == GhostPackets.TYPE_ROSTER) {
                    enqueue(new RaceClient.Roster(GhostPackets.decodeRoster(data)));
                } else {
                    throw new ProtocolViolationException(
                            "unexpected room binary packet type " + type);
                }
            } catch (ProtocolViolationException e) {
                signalDisconnected("protocol violation");
                close();
            }
        }

        void acceptJoin(ControlMessage.JoinAccepted accepted) {
            joined = accepted;
        }

        void restore(List<RaceClient.InboundEvent> events) {
            for (RaceClient.InboundEvent event : events) {
                enqueue(event);
            }
        }

        @Override public List<RaceClient.InboundEvent> drainInbound() {
            return inbound.drain();
        }

        @Override public void sendControl(ControlMessage message) {
            if (open) {
                parent.sendRawText(parent.codec.encode(sessionToken(), message));
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
