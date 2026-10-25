package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.NetworkEndpointPolicy;
import com.openggf.net.host.HostLinkAuthentication;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.protocol.RoomPassword;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;

/** Client for master browsing plus direct/relay room join orchestration. */
@com.openggf.game.ModApi
public final class MasterClient implements AutoCloseable {
    public static final long MASTER_REPLY_TIMEOUT_MILLIS = 20_000;
    public static final int MAX_PENDING_REQUESTS = 128;
    private static final long CANCELED_CREATE_RETENTION_MILLIS = 60_000;

    private final BoundedInboundQueue<RaceClient.InboundEvent> inbound =
            new BoundedInboundQueue<>();
    private final Map<Long, PendingRequest<?>> pendingRequests = new LinkedHashMap<>();
    private final Map<Long, Long> canceledCreates = new LinkedHashMap<>();
    private long nextRequestId;
    private final AtomicReference<RelayRaceConnection> attached = new AtomicReference<>();
    private final AtomicBoolean retired = new AtomicBoolean();
    /** Failed retirement discards writes; graceful close flushes writes already accepted. */
    private volatile boolean discardQueuedSends;
    private final Object sendLock = new Object();
    private final ControlJsonCodec codec = new ControlJsonCodec();
    private final PlayerIdentity identity;
    private final ClientHandshake handshake;
    private volatile Supplier<String> transportBinding = () -> AuthenticationTranscript.LOCAL_PLAINTEXT;
    private final Object powLock = new Object();
    private final ThreadPoolExecutor powExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofVirtual().name("master-pow-", 0).factory());
    private FutureTask<Void> powTask;
    private volatile long powGeneration;
    /** Under sendLock: one replaceable proof behind the current transport write. */
    private String pendingPowText;
    private long pendingPowGeneration;
    private boolean powWriteQueued;
    private volatile WebSocket webSocket;
    private volatile ControlMessage.JoinAccepted joinAccepted;
    private volatile HostMasterLink hostLink;
    private volatile boolean open;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    private MasterClient(PlayerIdentity identity, String displayName,
                         String determinismFingerprint) {
        this.identity = identity;
        handshake = new ClientHandshake(identity, displayName, determinismFingerprint, null,
                AuthenticationTranscript.Role.MASTER, () -> transportBinding.get());
    }

    public static CompletableFuture<MasterClient> connect(
            URI uri, PlayerIdentity identity, String displayName,
            String determinismFingerprint, SSLContext sslContextOrNull) {
        try { NetworkEndpointPolicy.requireWebSocket(uri); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.failedFuture(invalid); }
        MasterClient client = new MasterClient(identity, displayName, determinismFingerprint);
        CompletableFuture<MasterClient> admitted = new CompletableFuture<>();
        WebSocket.Listener listener = client.listener(admitted);
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(MASTER_REPLY_TIMEOUT_MILLIS));
        if ("wss".equalsIgnoreCase(uri.getScheme())) {
            try {
                ObservedTlsContext observed = new ObservedTlsContext(
                        sslContextOrNull == null ? SSLContext.getDefault() : sslContextOrNull);
                client.transportBinding = observed::binding;
                http.sslContext(observed);
            } catch (java.security.GeneralSecurityException invalid) {
                client.close();
                return CompletableFuture.failedFuture(invalid);
            }
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
        return request(new ControlMessage.RoomListRequest(gameFilter, page), ControlMessage.RoomListResult.class);
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
        return createRoom(descriptor, routing, directPort, determinismFingerprint,
                voteTrackKeys, certificateSha256, null, 0, null);
    }

    public CompletableFuture<ControlMessage.RoomJoinResult> requestJoin(String roomId) {
        return request(new ControlMessage.RoomJoinRequest(roomId), ControlMessage.RoomJoinResult.class);
    }

    public CompletableFuture<ControlMessage.RoomCreated> createRoom(
            ControlMessage.RoomDescriptor descriptor, String routing, int directPort,
            String determinismFingerprint, List<String> voteTrackKeys, String certificateSha256,
            String instanceId, long accessEpoch, RoomPassword password) {
        return request(new ControlMessage.RoomCreate(descriptor, routing, directPort,
                determinismFingerprint, voteTrackKeys, certificateSha256, instanceId, accessEpoch,
                password), ControlMessage.RoomCreated.class);
    }

    public CompletableFuture<ControlMessage.RoomJoinResult> resolveInvite(String code) {
        return request(new ControlMessage.RoomInviteResolve(code), ControlMessage.RoomJoinResult.class);
    }

    public RoomInviteService inviteService(String roomId) {
        return (instanceId, accessEpoch, inviteEpoch, operation, membersCanInvite) -> request(
                new ControlMessage.RoomInviteControl(roomId, instanceId, accessEpoch, inviteEpoch,
                        operation, membersCanInvite), ControlMessage.RoomInviteControlResult.class);
    }

    private <T extends ControlMessage> CompletableFuture<T> request(ControlMessage message, Class<T> replyType) {
        CompletableFuture<T> future = new CompletableFuture<>();
        PendingSend send;
        long requestId;
        PendingRequest<T> pending = new PendingRequest<>(future, replyType,
                message instanceof ControlMessage.RoomCreate);
        synchronized (sendLock) {
            if (retired.get() || !open || attached.get() != null) {
                return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException(
                        "master connection is unavailable; reconnect before retrying"));
            }
            long now = System.currentTimeMillis();
            canceledCreates.values().removeIf(deadline -> deadline <= now);
            long creations = pendingRequests.values().stream().filter(entry -> entry.creation).count();
            if (pendingRequests.size() >= MAX_PENDING_REQUESTS || nextRequestId == Long.MAX_VALUE
                    || pending.creation && canceledCreates.size() + creations >= MAX_PENDING_REQUESTS) {
                return CompletableFuture.failedFuture(new IllegalStateException("master request limit exceeded"));
            }
            requestId = ++nextRequestId;
            String text = codec.encode(joinAccepted.sessionToken(), message, requestId);
            WebSocket socket = webSocket;
            pendingRequests.put(requestId, pending);
            send = reserveSend(() -> discardQueuedSends || pending.canceled
                    ? CompletableFuture.completedFuture(socket) : socket.sendText(text, true));
        }
        future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((value, failure) -> {
                    boolean cancelCreation = false;
                    synchronized (sendLock) {
                        if (pendingRequests.remove(requestId, pending)
                                && (future.isCancelled() || failure instanceof TimeoutException)) {
                            pending.canceled = true;
                            if (pending.creation) {
                                canceledCreates.put(requestId,
                                        System.currentTimeMillis() + CANCELED_CREATE_RETENTION_MILLIS);
                                cancelCreation = true;
                            }
                        }
                    }
                    if (cancelCreation) sendControl(new ControlMessage.MasterRequestCancel(requestId));
                });
        send.dispatch();
        return future;
    }

    private static final class PendingRequest<T extends ControlMessage> {
        final CompletableFuture<T> future;
        final Class<T> replyType;
        final boolean creation;
        volatile boolean canceled;
        PendingRequest(CompletableFuture<T> future, Class<T> replyType, boolean creation) {
            this.future = future;
            this.replyType = replyType;
            this.creation = creation;
        }
        boolean complete(ControlMessage message, Throwable error) {
            return error == null ? future.complete(replyType.cast(message)) : future.completeExceptionally(error);
        }
    }

    public CompletableFuture<RaceConnection> joinRoom(
            String roomId, PlayerIdentity roomIdentity, String displayName,
            String determinismFingerprint) {
        return joinRoom(roomId, roomIdentity, displayName, "", determinismFingerprint);
    }

    /** Joins with the player's look (canonical {@link com.openggf.net.identity.PlayerAppearance} form). */
    public CompletableFuture<RaceConnection> joinRoom(
            String roomId, PlayerIdentity roomIdentity, String displayName, String appearance,
            String determinismFingerprint) {
        return joinAfterPreparation(requestJoin(roomId), result -> {
            try (RoomJoinOptions options = optionsFor(result, null)) {
                return joinResolved(result, roomIdentity, displayName, appearance, determinismFingerprint, options);
            }
        });
    }

    public CompletableFuture<RaceConnection> joinResolved(
            ControlMessage.RoomJoinResult result, PlayerIdentity roomIdentity, String displayName,
            String appearance, String determinismFingerprint, RoomJoinOptions options) {
        final RoomJoinOptions owned;
        try {
            requirePrepared(result, options);
            owned = options.copy();
        } catch (RuntimeException invalid) { return CompletableFuture.failedFuture(invalid); }
        CompletableFuture<RaceConnection> joined = connectPrepared(result, roomIdentity, displayName,
                appearance, determinismFingerprint, owned);
        joined.whenComplete((connection, failure) -> owned.close());
        return joined;
    }

    public CompletableFuture<RaceConnection> joinInvite(
            String code, ControlMessage.RoomJoinResult expected, PlayerIdentity roomIdentity,
            String displayName, String appearance, String determinismFingerprint, RoomPassword password) {
        final RoomJoinOptions options;
        try {
            requireLiveInvite(expected);
            options = optionsFor(expected, password);
        } catch (RuntimeException invalid) { return CompletableFuture.failedFuture(invalid); }
        CompletableFuture<RaceConnection> joined = joinAfterPreparation(resolveInvite(code), fresh -> {
            requireSameRoom(expected, fresh);
            requireLiveInvite(fresh);
            return joinResolved(fresh, roomIdentity, displayName, appearance, determinismFingerprint, options);
        });
        joined.whenComplete((connection, failure) -> options.close());
        return joined;
    }

    private static RoomJoinOptions optionsFor(ControlMessage.RoomJoinResult result, RoomPassword password) {
        return new RoomJoinOptions(password, result.instanceId(), result.instanceId() == null ? -1 : result.accessEpoch());
    }

    private static void requirePrepared(ControlMessage.RoomJoinResult result, RoomJoinOptions options) {
        if (result == null || result.accessPolicy() == null) throw new ProtocolViolationException("missing room access metadata");
        if (options.expectedInstanceId() != null && (!options.expectedInstanceId().equals(result.instanceId())
                || options.expectedAccessEpoch() != result.accessEpoch())) {
            throw new RaceClient.JoinRejectedException("room access changed; enter credentials again");
        }
        if (result.inviteExpiresAtMillis() != 0) requireLiveInvite(result);
        if (result.accessPolicy().passwordRequired() && !options.hasPassword()) {
            throw new RoomAccessRequiredException(result.instanceId(), result.accessEpoch(), result.accessPolicy());
        }
        if (!result.accessPolicy().passwordRequired() && options.hasPassword()) {
            throw new RaceClient.JoinRejectedException("room access changed");
        }
    }

    private static void requireLiveInvite(ControlMessage.RoomJoinResult result) {
        if (result == null || result.inviteExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new RaceClient.JoinRejectedException("room invite expired; request a fresh invitation");
        }
    }

    private static void requireSameRoom(ControlMessage.RoomJoinResult expected, ControlMessage.RoomJoinResult fresh) {
        if (!Objects.equals(expected.roomId(), fresh.roomId())
                || !Objects.equals(expected.instanceId(), fresh.instanceId())
                || expected.accessEpoch() != fresh.accessEpoch()
                || expected.accessPolicy().mode() != fresh.accessPolicy().mode()
                || expected.inviteEpoch() != fresh.inviteEpoch()
                || !Objects.equals(expected.hostServerId(), fresh.hostServerId())
                || !Objects.equals(expected.certificateSha256(), fresh.certificateSha256())
                || !Objects.equals(expected.determinismFingerprint(), fresh.determinismFingerprint())) {
            throw new RaceClient.JoinRejectedException("room access changed; enter credentials again");
        }
    }

    private CompletableFuture<RaceConnection> connectPrepared(
            ControlMessage.RoomJoinResult result, PlayerIdentity identity, String displayName,
            String appearance, String build, RoomJoinOptions options) {
        if ("RELAY".equals(result.routing())) return relayHandshake(result.roomId(), identity,
                displayName, appearance, build, result.hostServerId(), options);
        if (!"DIRECT".equals(result.routing()) || result.certificateSha256() == null || result.hostServerId() == null) {
            return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException("direct room has no authenticated host identity"));
        }
        final URI uri;
        try { uri = new URI("wss", null, result.directHost(), result.directPort(), "/race", null, null); }
        catch (URISyntaxException invalid) { return CompletableFuture.failedFuture(new IllegalArgumentException("invalid direct room endpoint")); }
        CompletableFuture<RaceConnection> joined = new CompletableFuture<>();
        AtomicReference<CompletableFuture<? extends RaceConnection>> active = new AtomicReference<>();
        joined.whenComplete((connection, failure) -> {
            if (joined.isCancelled()) stopJoin(active.get());
        });
        CompletableFuture<RaceClient> direct = RaceClient.connect(uri, identity, displayName, appearance,
                build, result.certificateSha256(), result.hostServerId(), options);
        active.set(direct);
        direct.whenComplete((client, failure) -> {
            if (joined.isDone()) {
                if (client != null) client.close();
                return;
            }
            if (failure == null || !allowsRelayFallback(failure)) {
                finishJoin(joined, client, failure);
                return;
            }
            if (result.inviteExpiresAtMillis() != 0) {
                try { requireLiveInvite(result); }
                catch (RuntimeException expired) { joined.completeExceptionally(expired); return; }
            }
            CompletableFuture<RaceConnection> relay = relayHandshake(result.roomId(), identity, displayName,
                    appearance, build, result.hostServerId(), options);
            active.set(relay);
            if (joined.isDone()) stopJoin(relay);
            relay.whenComplete((connection, error) -> finishJoin(joined, connection, error));
        });
        return joined;
    }

    private static CompletableFuture<RaceConnection> joinAfterPreparation(
            CompletableFuture<ControlMessage.RoomJoinResult> preparation,
            java.util.function.Function<ControlMessage.RoomJoinResult, CompletableFuture<RaceConnection>> connect) {
        CompletableFuture<RaceConnection> joined = new CompletableFuture<>();
        AtomicReference<CompletableFuture<RaceConnection>> active = new AtomicReference<>();
        joined.whenComplete((connection, failure) -> {
            if (joined.isCancelled()) {
                preparation.cancel(true);
                stopJoin(active.get());
            }
        });
        preparation.whenComplete((result, failure) -> {
            if (joined.isDone()) return;
            if (failure != null) { joined.completeExceptionally(failure); return; }
            try {
                CompletableFuture<RaceConnection> attempt = connect.apply(result);
                active.set(attempt);
                if (joined.isDone()) stopJoin(attempt);
                attempt.whenComplete((connection, error) -> finishJoin(joined, connection, error));
            } catch (RuntimeException error) { joined.completeExceptionally(error); }
        });
        return joined;
    }

    private static void finishJoin(CompletableFuture<RaceConnection> joined,
                                   RaceConnection connection, Throwable failure) {
        if (failure != null) joined.completeExceptionally(failure);
        else if (!joined.complete(connection)) connection.close();
    }

    private static void stopJoin(CompletableFuture<? extends RaceConnection> attempt) {
        if (attempt == null) return;
        if (!attempt.cancel(true) && !attempt.isCompletedExceptionally()) {
            RaceConnection connection = attempt.getNow(null);
            if (connection != null) connection.close();
        }
    }

    /** Only classified reachability failures can reuse a prepared relay grant. */
    public static boolean allowsRelayFallback(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof javax.net.ssl.SSLException
                    || current instanceof java.security.GeneralSecurityException
                    || current instanceof ProtocolViolationException
                    || current instanceof RaceClient.JoinRejectedException
                    || current instanceof RoomAccessRequiredException
                    || current instanceof RoomInviteRejectedException
                    || current instanceof IllegalArgumentException) return false;
            current = current.getCause();
        }
        current = failure;
        while (current != null) {
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.NoRouteToHostException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.http.HttpConnectTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }

    private CompletableFuture<RaceConnection> relayHandshake(
            String roomId, PlayerIdentity roomIdentity, String displayName, String appearance,
            String determinismFingerprint, String expectedServerId, RoomJoinOptions options) {
        try {
            return completeRoomHandshake(attachRelay(roomId), roomIdentity,
                    displayName, appearance, determinismFingerprint, expectedServerId, options);
        } catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
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
        return completeRoomHandshake(raw, identity, displayName, "",
                determinismFingerprint, null, RoomJoinOptions.publicRoom());
    }

    private static CompletableFuture<RaceConnection> completeRoomHandshake(
            RaceConnection raw, PlayerIdentity identity, String displayName, String appearance,
            String determinismFingerprint, String expectedServerId, RoomJoinOptions options) {
        CompletableFuture<RaceConnection> joined = new CompletableFuture<>();
        ClientAccessGate gate = new ClientAccessGate(options);
        joined.whenComplete((value, failure) -> gate.close());
        Thread.ofVirtual().name("relay-room-handshake").start(() -> {
            ClientHandshake handshake = new ClientHandshake(
                    identity, displayName, appearance, determinismFingerprint, expectedServerId,
                    AuthenticationTranscript.Role.ROOM_RELAY,
                    raw instanceof RelayRaceConnection relay ? () -> relay.parent.transportBinding.get()
                            : () -> { throw new IllegalStateException("relay has no authenticated master transport"); });
            raw.sendControl(handshake.hello());
            boolean identityProofSent = false;
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
                                    case ControlMessage.Welcome welcome -> {
                                        raw.sendControl(handshake.onWelcome(welcome));
                                        identityProofSent = true;
                                    }
                                    case ControlMessage.RoomAccessChallenge challenge -> {
                                        if (!identityProofSent) throw new ProtocolViolationException("access challenge before identity");
                                        ControlMessage.RoomAccessSubmit submission = gate.onChallenge(challenge);
                                        if (submission != null) {
                                            try (var password = submission.password()) { raw.sendControl(submission); }
                                        }
                                    }
                                    case ControlMessage.JoinAccepted accepted -> {
                                        if (!identityProofSent) throw new ProtocolViolationException("admission before identity");
                                        gate.requireAdmission(accepted);
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
        HostLinkAuthentication.bind(link, () -> transportBinding.get());
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
        final ControlJsonCodec.Decoded decoded;
        final ControlMessage message;
        try {
            decoded = codec.decode(text, Protocol.MAX_MASTER_FRAME_BYTES);
            message = decoded.message();
        } catch (ProtocolViolationException e) {
            fail(socket, admitted, e);
            return;
        }
        if (joinAccepted == null) {
            receiveAdmission(socket, message, admitted);
            return;
        }
        try {
            if (completeReply(decoded)) return;
        } catch (ProtocolViolationException invalid) {
            fail(socket, admitted, invalid);
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
                        startChallenge(socket, challenge, admitted);
                case ControlMessage.JoinAccepted accepted -> {
                    cancelPow();
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

    private void startChallenge(WebSocket socket, ControlMessage.PowChallenge challenge,
                                CompletableFuture<MasterClient> admitted) {
        if (challenge.difficultyBits() < 0 || challenge.difficultyBits() > 24)
            throw new ProtocolViolationException("proof challenge exceeds client work budget");
        byte[] payload = switch (challenge.kind()) {
            case "IDENTITY" -> identity.publicKeyEncoded();
            case "JOIN" -> Base64.getDecoder().decode(challenge.prefixBase64());
            default -> throw new ProtocolViolationException("unknown proof challenge");
        };
        synchronized (powLock) {
            if (retired.get() || admitted.isDone()) return;
            long generation = ++powGeneration;
            if (powTask != null) powTask.cancel(true);
            powExecutor.purge();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            powTask = new FutureTask<>(() -> {
                try {
                    long nonce = ProofOfWork.solve(payload, challenge.difficultyBits(), deadline);
                    synchronized (powLock) {
                        if (generation != powGeneration || retired.get() || admitted.isDone()) return null;
                        // Supersession and proof reservation share the lock; stale workers cannot reorder proofs.
                        sendPowText(codec.encode(null, new ControlMessage.PowSolution(challenge.kind(), nonce)), generation);
                    }
                } catch (Exception failure) {
                    synchronized (powLock) {
                        if (generation != powGeneration || retired.get()) return null;
                    }
                    fail(socket, admitted, failure);
                }
                return null;
            });
            powExecutor.execute(powTask);
        }
    }

    private void cancelPow() {
        synchronized (powLock) {
            ++powGeneration;
            if (powTask != null) { powTask.cancel(true); powTask = null; }
            powExecutor.shutdownNow();
        }
        synchronized (sendLock) {
            pendingPowText = null;
        }
    }

    private void sendPowText(String text, long generation) {
        WebSocket socket = webSocket;
        if (socket == null) return;
        PendingSend send = null;
        synchronized (sendLock) {
            if (retired.get() || generation != powGeneration) return;
            pendingPowText = text;
            pendingPowGeneration = generation;
            if (!powWriteQueued) {
                powWriteQueued = true;
                // Reserve this ordering position once. Further solves replace the
                // slot instead of retaining another continuation per challenge.
                send = reserveSend(() -> dispatchPowText(socket));
            }
        }
        if (send != null) send.dispatch();
    }

    private CompletableFuture<WebSocket> dispatchPowText(WebSocket socket) {
        String text;
        long generation;
        synchronized (sendLock) {
            text = pendingPowText;
            generation = pendingPowGeneration;
            pendingPowText = null;
            powWriteQueued = false;
        }
        // No transport calls or user continuations run while holding sendLock.
        return text == null || retired.get() || discardQueuedSends || generation != powGeneration
                ? CompletableFuture.completedFuture(socket) : socket.sendText(text, true);
    }

    private boolean completeReply(ControlJsonCodec.Decoded decoded) {
        long requestId = decoded.requestId();
        ControlMessage message = decoded.message();
        if (requestId == 0) {
            if (message instanceof ControlMessage.RoomCreated
                    || message instanceof ControlMessage.RoomCreateRejected
                    || message instanceof ControlMessage.RoomListResult
                    || message instanceof ControlMessage.RoomJoinResult
                    || message instanceof ControlMessage.RoomJoinRejected
                    || message instanceof ControlMessage.RoomInviteControlResult
                    || message instanceof ControlMessage.RoomInviteRejected) {
                throw new ProtocolViolationException("master reply has no request ID");
            }
            return false;
        }
        PendingRequest<?> pending;
        boolean abandonedCreation;
        Throwable failure = null;
        synchronized (sendLock) {
            if (retired.get()) return true;
            pending = pendingRequests.get(requestId);
            abandonedCreation = pending == null && message instanceof ControlMessage.RoomCreated
                    && canceledCreates.remove(requestId) != null;
            if (pending != null) {
                if (message instanceof ControlMessage.RoomCreateRejected rejected && pending.creation) {
                    failure = new RaceClient.JoinRejectedException(rejected.reason());
                } else if (message instanceof ControlMessage.RoomJoinRejected rejected
                        && pending.replyType == ControlMessage.RoomJoinResult.class) {
                    failure = new RaceClient.JoinRejectedException(rejected.reason());
                } else if (message instanceof ControlMessage.RoomInviteRejected rejected
                        && (pending.replyType == ControlMessage.RoomJoinResult.class
                        || pending.replyType == ControlMessage.RoomInviteControlResult.class)) {
                    failure = new RoomInviteRejectedException(rejected.retryAfterMillis());
                } else if (!pending.replyType.isInstance(message)) {
                    throw new ProtocolViolationException("unexpected master reply type");
                } else if (message instanceof ControlMessage.RoomListResult result
                        && result.totalPages() == Protocol.ROOM_LIST_RATE_LIMITED) {
                    failure = new IllegalStateException("room list rate limited");
                }
                pendingRequests.remove(requestId);
            }
        }
        boolean completed = pending != null && pending.complete(message, failure);
        if ((abandonedCreation || pending != null && pending.creation && !completed)
                && message instanceof ControlMessage.RoomCreated created) {
            sendControl(new ControlMessage.MasterRequestCancel(requestId));
            sendControl(new ControlMessage.RoomLeave(created.roomId()));
        }
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
            for (PendingRequest<?> request : pendingRequests.values()) pending.add(request.future);
            pendingRequests.clear();
            canceledCreates.clear();
        }
        cancelPow();
        for (CompletableFuture<?> future : pending) future.completeExceptionally(failure);
        RelayRaceConnection relay = attached.get();
        if (relay != null) relay.signalDisconnected(failure.getMessage());
        else signalDisconnected(failure.getMessage());
        return true;
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
                } else if (type == GhostPackets.TYPE_LOBBY_AGGREGATE) {
                    enqueue(new RaceClient.LobbyGhostData(GhostPackets.decodeLobbyAggregate(data)));
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
