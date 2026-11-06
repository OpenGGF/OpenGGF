package com.openggf.net.client;

import com.openggf.net.host.HostMasterLink;
import com.openggf.net.host.RaceRoomHost;
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
import com.openggf.net.flow.WorkBudget;
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
import java.nio.charset.StandardCharsets;
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
import java.util.function.BooleanSupplier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;

/** Client for master browsing plus direct/relay room join orchestration. */
@com.openggf.game.ModApi
public final class MasterClient implements AutoCloseable {
    public static final long MASTER_REPLY_TIMEOUT_MILLIS = 20_000;
    /**
     * Upper bound for {@link #connect(String, PlayerIdentity, String, String, SSLContext)}: DNS
     * discovery (JNDI worst case about 7 s for one resolver) plus candidate dials and admission.
     */
    public static final long CONNECT_DEADLINE_MILLIS = 25_000;
    /** DNS stage cap; JNDI's 1 s initial timeout with two doubling retries needs about 7 s. */
    static final long DNS_STAGE_MILLIS = 8_000;
    /** TCP connect timeout for one discovered candidate. */
    static final long DIAL_MILLIS = 5_000;
    public static final int MAX_PENDING_REQUESTS = 128;
    private static final long CANCELED_CREATE_RETENTION_MILLIS = 60_000;

    private final BoundedInboundQueue<RaceClient.InboundEvent> inbound =
            new BoundedInboundQueue<>();
    private final Map<Long, PendingRequest<?>> pendingRequests = new LinkedHashMap<>();
    private final Map<Long, Long> canceledCreates = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Void>> directRetirements = new LinkedHashMap<>();
    private long nextRequestId;
    private final AtomicReference<RelayRaceConnection> attached = new AtomicReference<>();
    private final AtomicBoolean retired = new AtomicBoolean();
    /** Failed retirement discards writes; graceful close flushes writes already accepted. */
    private volatile boolean discardQueuedSends;
    private final Object sendLock = new Object();
    private final OutboundWriter writer;
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
    private final AtomicReference<HostMasterLink> hostLink = new AtomicReference<>();
    private volatile boolean open;
    private final URI endpoint;
    private MasterClient(URI endpoint, PlayerIdentity identity, String displayName,
                         String determinismFingerprint) {
        this.endpoint = endpoint;
        this.identity = identity;
        handshake = new ClientHandshake(identity, displayName, determinismFingerprint, null,
                AuthenticationTranscript.Role.MASTER, () -> transportBinding.get());
        writer = new OutboundWriter(WorkBudget.PROCESS, 1024, 8L * 1024 * 1024,
                () -> webSocket, () -> retire("master outbound limit exceeded"));
    }

    /**
     * Connects and is admitted to a master server.
     *
     * <p>{@code address} is a bare DNS host name (for example {@code multi-dev.openggf.com}) or an
     * explicit {@code wss://host[:port][/master]} URL ({@code ws://} only to a literal loopback
     * address). A host name's port comes from its {@code _openggf._tcp} SRV records; every record
     * must name the typed host itself, so the certificate is still checked against that name.
     * Candidates are tried in RFC 2782 order, each with a fresh connection and TLS observation;
     * only a failure to reach a candidate before the WebSocket upgrade moves on to the next.
     * Certificate, protocol and admission failures end the attempt. Discovery fails closed with a
     * {@link MasterDiscoveryException}; there is no fallback port and no cache, so each call
     * looks the name up again. Invalid address text fails with {@link IllegalArgumentException}.
     * The whole operation completes within {@link #CONNECT_DEADLINE_MILLIS}.
     *
     * @param sslContextOrNull the trust to use for {@code wss}, or null for the default trust store;
     *                         callers decide any relaxed trust from the address text, never from DNS
     */
    public static CompletableFuture<MasterClient> connect(
            String address, PlayerIdentity identity, String displayName,
            String determinismFingerprint, SSLContext sslContextOrNull) {
        return connect(address, identity, displayName, determinismFingerprint, sslContextOrNull,
                JndiSrvLookup.system(), java.util.concurrent.ThreadLocalRandom.current(),
                java.util.function.UnaryOperator.identity());
    }

    /** The admitted endpoint this client is connected to, including a discovered port. */
    public URI endpoint() {
        return endpoint;
    }

    /**
     * Test seam: the same operation with an injected DNS lookup and weight randomness. {@code route}
     * maps each discovered candidate to the URI actually dialled; production uses the identity
     * (tests route a multi-label test name to their loopback listeners).
     */
    static CompletableFuture<MasterClient> connect(
            String address, PlayerIdentity identity, String displayName,
            String determinismFingerprint, SSLContext sslContextOrNull,
            MasterAddressResolver.SrvLookup lookup, java.util.random.RandomGenerator random,
            java.util.function.UnaryOperator<URI> route) {
        MasterAddressResolver.Address parsed;
        try {
            parsed = MasterAddressResolver.parse(address);
        } catch (IllegalArgumentException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
        return switch (parsed) {
            case MasterAddressResolver.Explicit explicit -> dial(explicit.endpoint(), identity, displayName,
                    determinismFingerprint, sslContextOrNull, MASTER_REPLY_TIMEOUT_MILLIS,
                    MASTER_REPLY_TIMEOUT_MILLIS, null);
            case MasterAddressResolver.Named named -> discover(named, identity, displayName,
                    determinismFingerprint, sslContextOrNull, lookup, random, route);
        };
    }

    private static CompletableFuture<MasterClient> discover(
            MasterAddressResolver.Named named, PlayerIdentity identity, String displayName,
            String determinismFingerprint, SSLContext sslContextOrNull,
            MasterAddressResolver.SrvLookup lookup, java.util.random.RandomGenerator random,
            java.util.function.UnaryOperator<URI> route) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECT_DEADLINE_MILLIS);
        CompletableFuture<MasterClient> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<MasterClient>> current = new AtomicReference<>();
        result.whenComplete((client, failure) -> {
            CompletableFuture<MasterClient> attempt = current.get();
            if (failure != null && attempt != null) attempt.cancel(true);
        });
        Thread.ofVirtual().name("master-discovery").start(() -> {
            try {
                List<MasterAddressResolver.Srv> answer;
                try {
                    answer = lookup.query(named.srvOwner(),
                            Duration.ofMillis(Math.min(DNS_STAGE_MILLIS, remainingMillis(deadline))));
                } catch (java.io.IOException failed) {
                    throw new MasterDiscoveryException(MasterDiscoveryException.Reason.LOOKUP_FAILED,
                            named.asciiHost(), failed);
                }
                Throwable unreachable = null;
                for (URI candidate : MasterAddressResolver.candidates(named, answer, random)) {
                    long remaining = remainingMillis(deadline);
                    if (result.isDone() || remaining <= 0) break;
                    AtomicBoolean upgraded = new AtomicBoolean();
                    CompletableFuture<MasterClient> attempt = dial(route.apply(candidate), identity, displayName,
                            determinismFingerprint, sslContextOrNull, Math.min(DIAL_MILLIS, remaining),
                            remaining, upgraded);
                    current.set(attempt);
                    if (result.isDone()) {
                        attempt.cancel(true);
                        return;
                    }
                    try {
                        MasterClient admitted = attempt.get();
                        if (!result.complete(admitted)) admitted.close();
                        return;
                    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException e) {
                        Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
                        if (upgraded.get() || !unreachableBeforeUpgrade(cause)) {
                            result.completeExceptionally(cause);
                            return;
                        }
                        unreachable = cause;
                    }
                }
                result.completeExceptionally(unreachable != null ? unreachable
                        : new TimeoutException("master connection deadline passed"));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        result.orTimeout(CONNECT_DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
        return result;
    }

    private static long remainingMillis(long deadlineNanos) {
        return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    }

    /**
     * Whether a candidate failed only to be reached: TCP refusal, no route or a connect timeout,
     * before any TLS or HTTP exchange. TLS (including certificate) and protocol failures are not
     * reachability failures, so they never move on to another candidate.
     */
    static boolean unreachableBeforeUpgrade(Throwable failure) {
        boolean reachability = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof javax.net.ssl.SSLException
                    || cause instanceof java.security.GeneralSecurityException
                    || cause instanceof java.net.http.WebSocketHandshakeException
                    || cause instanceof RaceClient.JoinRejectedException
                    || cause instanceof ProtocolViolationException) {
                return false;
            }
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.net.http.HttpConnectTimeoutException
                    || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.PortUnreachableException) {
                reachability = true;
            }
        }
        return reachability;
    }

    /** One fresh client, HTTP client and TLS observer for one endpoint; never reused. */
    private static CompletableFuture<MasterClient> dial(
            URI uri, PlayerIdentity identity, String displayName, String determinismFingerprint,
            SSLContext sslContextOrNull, long connectTimeoutMillis, long admissionTimeoutMillis,
            AtomicBoolean upgraded) {
        try { NetworkEndpointPolicy.requireWebSocket(uri); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.failedFuture(invalid); }
        MasterClient client = new MasterClient(uri, identity, displayName, determinismFingerprint);
        CompletableFuture<MasterClient> admitted = new CompletableFuture<>();
        WebSocket.Listener listener = client.listener(admitted);
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMillis)));
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
                    if (upgraded != null) upgraded.set(true);
                    client.webSocket = socket;
                    if (admitted.isDone()) {
                        socket.abort();
                        return;
                    }
                    client.sendRawControl(null, client.handshake.hello());
                });
        admitted.orTimeout(Math.max(1, admissionTimeoutMillis), TimeUnit.MILLISECONDS)
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
        CompletableFuture<Void> retirement;
        synchronized (sendLock) {
            retirement = directRetirements.get(roomId);
        }
        return retirement == null
                ? request(new ControlMessage.RoomJoinRequest(roomId), ControlMessage.RoomJoinResult.class)
                : retirement.thenCompose(ignored -> request(new ControlMessage.RoomJoinRequest(roomId),
                        ControlMessage.RoomJoinResult.class));
    }

    CompletableFuture<ControlMessage.RoomActivated> activateRoom(String roomId, String instanceId) {
        return request(new ControlMessage.RoomActivate(roomId, instanceId),
                ControlMessage.RoomActivated.class);
    }

    CompletableFuture<ControlMessage.RoomGrantDecision> redeemDirectGrant(
            String roomId, String instanceId, String grantReference,
            long routeGeneration, String fingerprint) {
        return request(new ControlMessage.RoomGrantRedeem(roomId, instanceId,
                grantReference, routeGeneration, fingerprint),
                ControlMessage.RoomGrantDecision.class);
    }

    private CompletableFuture<ControlMessage.RoomJoinResult> retireDirectRoute(
            ControlMessage.RoomJoinResult failedRoute) {
        if (failedRoute.grantReference() == null || failedRoute.routeGeneration() <= 0
                || failedRoute.instanceId() == null) {
            return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException(
                    "direct route has no broker retirement authority"));
        }
        return request(new ControlMessage.RoomRouteRetire(failedRoute.roomId(),
                failedRoute.instanceId(), failedRoute.grantReference(),
                failedRoute.routeGeneration()), ControlMessage.RoomRouteRenewed.class)
                .thenApply(ControlMessage.RoomRouteRenewed::join);
    }

    CompletableFuture<ControlMessage.RoomCreated> createRoom(
            ControlMessage.RoomDescriptor descriptor, String routing, int directPort,
            String determinismFingerprint, List<String> voteTrackKeys, String certificateSha256,
            String instanceId, long accessEpoch, RoomPassword password) {
        return request(new ControlMessage.RoomCreate(descriptor, routing, directPort,
                determinismFingerprint, voteTrackKeys, certificateSha256, instanceId, accessEpoch,
                password), ControlMessage.RoomCreated.class);
    }

    public CompletableFuture<ControlMessage.RoomCreated> createRelayRoom(
            ControlMessage.RoomDescriptor descriptor, String determinismFingerprint,
            List<String> voteTrackKeys, RoomPassword password) {
        return createRoom(descriptor, "RELAY", 0, determinismFingerprint, voteTrackKeys,
                null, null, 0, password);
    }

    public CompletableFuture<ControlMessage.RoomJoinResult> resolveInvite(String code) {
        CompletableFuture<?>[] retiring;
        synchronized (sendLock) {
            retiring = directRetirements.values().stream()
                    .map(retirement -> retirement.handle((value, failure) -> null))
                    .toArray(CompletableFuture<?>[]::new);
        }
        return CompletableFuture.allOf(retiring).thenCompose(ignored ->
                request(new ControlMessage.RoomInviteResolve(code), ControlMessage.RoomJoinResult.class));
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
            send = reserveMasterControl(joinAccepted.sessionToken(), message, requestId,
                    () -> pending.canceled);
            pendingRequests.put(requestId, pending);
        }
        future.orTimeout(MASTER_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((value, failure) -> {
                    if (!future.isCancelled() && !(failure instanceof TimeoutException)) return;
                    PendingSend cancel = null;
                    synchronized (sendLock) {
                        if (pendingRequests.remove(requestId, pending)) {
                            pending.canceled = true;
                            if (pending.creation) {
                                canceledCreates.put(requestId,
                                        System.currentTimeMillis() + CANCELED_CREATE_RETENTION_MILLIS);
                                cancel = reserveMasterControl(new ControlMessage.MasterRequestCancel(requestId));
                            }
                        }
                    }
                    if (cancel != null) cancel.dispatch();
                });
        send.dispatch();
        return future;
    }

    private static final class PendingRequest<T extends ControlMessage> {
        final CompletableFuture<T> future;
        final Class<T> replyType;
        final boolean creation;
        volatile boolean canceled;
        boolean replyClaimed;
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
        return joinPrepared(result, roomIdentity, displayName, appearance,
                determinismFingerprint, options, null);
    }

    CompletableFuture<RaceConnection> joinLocalOwner(
            ControlMessage.RoomJoinResult result, RaceRoomHost listener, PlayerIdentity roomIdentity,
            String displayName, String appearance, String determinismFingerprint,
            RoomJoinOptions options) {
        try {
            if (result == null || listener == null || roomIdentity == null
                    || !"DIRECT".equals(result.routing())
                    || !identity.fingerprint().equals(roomIdentity.fingerprint())
                    || !identity.fingerprint().equals(result.hostServerId())
                    || !Objects.equals(listener.room().instanceId(), result.instanceId())
                    || !Objects.equals(listener.tlsCertificateSha256(), result.certificateSha256())
                    || listener.port() < 1 || listener.port() > 65535) {
                throw new IllegalArgumentException("local owner route does not match the registered host");
            }
            URI loopback = new URI("wss", null, "127.0.0.1", listener.port(), "/race", null, null);
            return joinPrepared(result, roomIdentity, displayName, appearance,
                    determinismFingerprint, options, loopback);
        } catch (RuntimeException | URISyntaxException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
    }

    private CompletableFuture<RaceConnection> joinPrepared(
            ControlMessage.RoomJoinResult result, PlayerIdentity roomIdentity, String displayName,
            String appearance, String determinismFingerprint, RoomJoinOptions options,
            URI directEndpoint) {
        final RoomJoinOptions owned;
        try {
            requirePrepared(result, options);
            owned = options.withRoute(result.grantReference(), result.routeGeneration());
        } catch (RuntimeException invalid) { return CompletableFuture.failedFuture(invalid); }
        CompletableFuture<RaceConnection> joined = connectPrepared(result, roomIdentity, displayName,
                appearance, determinismFingerprint, owned, directEndpoint);
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
            String appearance, String build, RoomJoinOptions options, URI directEndpoint) {
        if ("RELAY".equals(result.routing())) return relayHandshake(result.roomId(), identity,
                displayName, appearance, build, result.hostServerId(), options);
        if (!"DIRECT".equals(result.routing()) || result.certificateSha256() == null || result.hostServerId() == null) {
            return CompletableFuture.failedFuture(new RaceClient.JoinRejectedException("direct room has no authenticated host identity"));
        }
        final URI uri;
        try { uri = directEndpoint == null
                ? new URI("wss", null, result.directHost(), result.directPort(), "/race", null, null)
                : directEndpoint; }
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
                startDirectRetirement(result);
                if (client != null) client.close();
                return;
            }
            if (failure == null || directEndpoint != null || !allowsRelayFallback(failure)) {
                if (failure == null) ownDirectRoute(result, client);
                else startDirectRetirement(result);
                finishJoin(joined, client, failure);
                return;
            }
            if (result.inviteExpiresAtMillis() != 0) {
                try { requireLiveInvite(result); }
                catch (RuntimeException expired) {
                    startDirectRetirement(result);
                    joined.completeExceptionally(expired);
                    return;
                }
            }
            CompletableFuture<RaceConnection> relay = retireDirectRoute(result).thenCompose(fresh -> {
                requireSameRoom(result, fresh);
                if (fresh.routeGeneration() <= result.routeGeneration()
                        || Objects.equals(fresh.grantReference(), result.grantReference())) {
                    throw new ProtocolViolationException("broker did not renew join route");
                }
                try (RoomJoinOptions renewed = options.withRoute(
                        fresh.grantReference(), fresh.routeGeneration())) {
                    return relayHandshake(fresh.roomId(), identity, displayName,
                            appearance, build, fresh.hostServerId(), renewed);
                }
            });
            active.set(relay);
            if (joined.isDone()) stopJoin(relay);
            relay.whenComplete((connection, error) -> finishJoin(joined, connection, error));
        });
        return joined;
    }

    private void ownDirectRoute(ControlMessage.RoomJoinResult route, RaceClient connection) {
        connection.closing().thenRun(() -> startDirectRetirement(route));
    }

    private void startDirectRetirement(ControlMessage.RoomJoinResult route) {
        if (route.grantReference() == null || route.routeGeneration() <= 0
                || route.instanceId() == null) return;
        CompletableFuture<Void> retirement = new CompletableFuture<>();
        boolean exhausted;
        synchronized (sendLock) {
            if (retired.get() || directRetirements.containsKey(route.roomId())) return;
            exhausted = directRetirements.size() >= MAX_PENDING_REQUESTS;
            if (!exhausted) directRetirements.put(route.roomId(), retirement);
        }
        if (exhausted) {
            retire("direct route retirement limit exceeded");
            return;
        }
        request(new ControlMessage.RoomRouteRetire(route.roomId(), route.instanceId(),
                route.grantReference(), route.routeGeneration(), false),
                ControlMessage.RoomRouteRetired.class).thenAccept(receipt -> {
                    if (!Objects.equals(route.roomId(), receipt.roomId())
                            || !Objects.equals(route.instanceId(), receipt.instanceId())
                            || !Objects.equals(route.grantReference(), receipt.grantReference())
                            || route.routeGeneration() != receipt.routeGeneration()) {
                        throw new ProtocolViolationException("direct retirement receipt mismatch");
                    }
                }).whenComplete((receipt, failure) -> {
            if (failure == null) {
                synchronized (sendLock) {
                    directRetirements.remove(route.roomId(), retirement);
                }
                retirement.complete(null);
            } else {
                retirement.completeExceptionally(failure);
            }
        });
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

    /** Only classified reachability failures can enter the route-retirement fallback barrier. */
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
                    || current instanceof java.net.http.HttpConnectTimeoutException
                    || current instanceof RaceClient.DirectRouteLostException) return true;
            current = current.getCause();
        }
        return false;
    }

    private CompletableFuture<RaceConnection> relayHandshake(
            String roomId, PlayerIdentity roomIdentity, String displayName, String appearance,
            String determinismFingerprint, String expectedServerId, RoomJoinOptions options) {
        try {
            return completeRoomHandshake(attachRelay(roomId, options), roomIdentity,
                    displayName, appearance, determinismFingerprint, expectedServerId, options);
        } catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
    }

    RaceConnection attachRelay(String roomId, RoomJoinOptions options) {
        if (options == null || options.grantReference() == null
                || options.routeGeneration() <= 0) {
            throw new IllegalArgumentException("relay grant required");
        }
        RelayRaceConnection connection = new RelayRaceConnection(this);
        PendingSend send;
        synchronized (sendLock) {
            if (!open || retired.get()) {
                throw new RaceClient.JoinRejectedException("master connection is closed; reconnect before retrying");
            }
            if (!pendingRequests.isEmpty()) {
                throw new IllegalStateException("master requests are still outstanding; retry after they complete or cancel");
            }
            if (!attached.compareAndSet(null, connection)) {
                throw new IllegalStateException("master socket is already attached to a room");
            }
            send = reserveMasterControl(new ControlMessage.RelayAttach(roomId,
                    options.grantReference(), options.routeGeneration()));
        }
        send.dispatch();
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
                            : () -> { throw new IllegalStateException("relay has no authenticated master transport"); },
                    options.grantReference(), options.routeGeneration());
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

    void bindHostLink(HostMasterLink link) {
        HostMasterLink previous;
        synchronized (sendLock) {
            if (retired.get() && link != null) {
                throw new IllegalStateException("master connection is closed");
            }
            if (link != null) HostLinkAuthentication.bind(link, () -> transportBinding.get());
            previous = hostLink.getAndSet(link);
        }
        if (previous != null && previous != link) previous.onMasterLost();
    }

    void unbindHostLink(HostMasterLink expected) {
        if (expected != null && hostLink.compareAndSet(expected, null)) {
            expected.onMasterLost();
        }
    }

    boolean hostLinkIs(HostMasterLink expected) {
        return hostLink.get() == expected;
    }

    public void sendControl(ControlMessage message) {
        if (open) {
            sendRawControl(joinAccepted.sessionToken(), message);
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
        writer.stop(Duration.ofSeconds(2), true);
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
        final ControlJsonCodec.Decoded decoded;
        final ControlMessage message;
        try {
            decoded = codec.decodeMasterServer(text, Protocol.MAX_MASTER_FRAME_BYTES);
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
            if ((decoded.requestId() != 0 || attached.get() == null) && completeReply(decoded)) return;
        } catch (ProtocolViolationException invalid) {
            fail(socket, admitted, invalid);
            return;
        }
        RelayRaceConnection relay = attached.get();
        if (relay != null) {
            relay.acceptText(text);
            return;
        }
        HostMasterLink link = hostLink.get();
        if (link != null && (message instanceof ControlMessage.RelayGuestOpen
                || message instanceof ControlMessage.RelayGuestText
                || message instanceof ControlMessage.RelayGuestClose
                || message instanceof ControlMessage.RoomRouteRetireNotice)) {
            link.onMasterControl(message);
            return;
        }
        enqueueInbound(socket, new RaceClient.Control(message));
    }

    private void receiveAdmission(WebSocket socket, ControlMessage message,
                                  CompletableFuture<MasterClient> admitted) {
        try {
            switch (message) {
                case ControlMessage.Welcome welcome -> sendRawControl(
                        null, handshake.onWelcome(welcome));
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
                send = reserveSend(() -> dispatchPowText(socket), Protocol.MAX_CONTROL_BYTES);
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
                    || message instanceof ControlMessage.RoomActivated
                    || message instanceof ControlMessage.RoomCreateRejected
                    || message instanceof ControlMessage.RoomListResult
                    || message instanceof ControlMessage.RoomJoinResult
                    || message instanceof ControlMessage.RoomJoinRejected
                    || message instanceof ControlMessage.RoomGrantDecision
                    || message instanceof ControlMessage.RoomRouteRenewed
                    || message instanceof ControlMessage.RoomRouteRetired
                    || message instanceof ControlMessage.RoomRouteUnavailable
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
                if (pending.replyClaimed) return true;
                if (message instanceof ControlMessage.RoomCreateRejected rejected && pending.creation) {
                    failure = new RaceClient.JoinRejectedException(rejected.reason());
                } else if (message instanceof ControlMessage.RoomJoinRejected rejected
                        && pending.replyType == ControlMessage.RoomJoinResult.class) {
                    failure = new RaceClient.JoinRejectedException(rejected.reason());
                } else if (message instanceof ControlMessage.RoomRouteUnavailable unavailable
                        && (pending.replyType == ControlMessage.RoomRouteRenewed.class
                        || pending.replyType == ControlMessage.RoomRouteRetired.class)) {
                    failure = new RaceClient.JoinRejectedException(unavailable.reason());
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
                if (pending.creation) pending.replyClaimed = true;
                else pendingRequests.remove(requestId);
            }
        }
        boolean completed = pending != null && pending.complete(message, failure);
        PendingSend cleanup = null;
        synchronized (sendLock) {
            if (pending != null && pending.creation) pendingRequests.remove(requestId, pending);
            if ((abandonedCreation || pending != null && pending.creation && !completed)
                    && message instanceof ControlMessage.RoomCreated created && !retired.get()) {
                // After handoff, the cancellation reserved before RelayAttach owns removal.
                if (attached.get() == null) {
                    cleanup = reserveMasterControls(List.of(
                            new ControlMessage.MasterRequestCancel(requestId),
                            new ControlMessage.RoomLeave(created.roomId())));
                }
            }
        }
        if (cleanup != null) cleanup.dispatch();
        return true;
    }

    private void receiveBinary(byte[] data) {
        if (retired.get()) return;
        RelayRaceConnection relay = attached.get();
        if (relay != null) {
            relay.acceptBinary(data);
            return;
        }
        HostMasterLink link = hostLink.get();
        if (link != null) {
            link.onMasterBinary(data);
        }
    }

    /** Reserves admitted master traffic under sendLock, before any relay handoff. */
    private PendingSend reserveMasterControl(ControlMessage message) {
        return reserveMasterControl(joinAccepted.sessionToken(), message, 0, () -> false);
    }

    private PendingSend reserveMasterControl(String token, ControlMessage message,
                                             long requestId, BooleanSupplier canceled) {
        WebSocket socket = webSocket;
        return new PendingSend(writer.prepareEncoded(
                () -> codec.encode(token, message, requestId), Protocol.MAX_MASTER_FRAME_BYTES,
                text -> () -> discardQueuedSends || canceled.getAsBoolean()
                        ? CompletableFuture.completedFuture(socket) : socket.sendText(text, true))
                .map(List::of).orElse(null),
                () -> retire("master outbound limit exceeded"));
    }

    private PendingSend reserveMasterControls(List<ControlMessage> messages) {
        WebSocket socket = webSocket;
        List<Supplier<String>> encoders = messages.stream()
                .<Supplier<String>>map(message -> () -> codec.encode(
                        joinAccepted.sessionToken(), message)).toList();
        return new PendingSend(writer.prepareEncodedBatch(encoders,
                Protocol.MAX_MASTER_FRAME_BYTES,
                text -> () -> discardQueuedSends ? CompletableFuture.completedFuture(socket)
                        : socket.sendText(text, true)).orElse(null),
                () -> retire("master outbound limit exceeded"));
    }

    private void sendRawControl(String token, ControlMessage message) {
        WebSocket socket = webSocket;
        if (socket == null) return;
        PendingSend send;
        synchronized (sendLock) {
            if (retired.get()) return;
            send = reserveMasterControl(token, message, 0, () -> false);
        }
        send.dispatch();
    }

    private void sendRawBinary(byte[] data) {
        WebSocket socket = webSocket;
        if (socket == null || !open) {
            return;
        }
        PendingSend send;
        synchronized (sendLock) {
            if (retired.get()) return;
            send = new PendingSend(writer.prepareBinary(data).map(List::of).orElse(null),
                    () -> retire("master outbound limit exceeded"));
        }
        send.dispatch();
    }

    /** Must be called under sendLock; dispatch is deliberately separate and outside it. */
    private PendingSend reserveSend(Supplier<CompletableFuture<WebSocket>> action, long bytes) {
        return new PendingSend(writer.prepare(new OutboundWriter.Action(action, bytes))
                .map(List::of).orElse(null),
                () -> retire("master outbound limit exceeded"));
    }

    private record PendingSend(List<OutboundWriter.Prepared> prepared, Runnable rejected) {
        void dispatch() {
            if (prepared == null) rejected.run();
            else prepared.forEach(OutboundWriter.Prepared::dispatch);
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
        HostMasterLink lost;
        synchronized (sendLock) {
            // Publish failure before any future continuation can release an
            // earlier transport write. Failure also escalates a graceful close
            // whose accepted writes or close frame have not yet been dispatched.
            if (discardAcceptedSends) discardQueuedSends = true;
            if (!retired.compareAndSet(false, true)) return false;
            open = false;
            lost = hostLink.getAndSet(null);
            for (PendingRequest<?> request : pendingRequests.values()) pending.add(request.future);
            pendingRequests.clear();
            canceledCreates.clear();
            pending.addAll(directRetirements.values());
            directRetirements.clear();
        }
        if (discardAcceptedSends) writer.abort();
        if (lost != null) lost.onMasterLost();
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
                enqueue(new RaceClient.Control(parent.codec.decodeRoomServer(text).message()));
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
                parent.sendRawControl(sessionToken(), message);
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
