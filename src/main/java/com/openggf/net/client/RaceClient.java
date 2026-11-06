package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.NetworkEndpointPolicy;
import com.openggf.net.flow.WorkBudget;

import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Network-thread WebSocket client. Listener callbacks only enqueue typed events;
 * the game thread owns all state mutation through {@link #drainInbound()}.
 */
@com.openggf.game.ModApi
public final class RaceClient implements RaceConnection {
    public static final long JOIN_TIMEOUT_MILLIS = 20_000;

    @com.openggf.game.ModApi
    public static final class JoinRejectedException extends RuntimeException {
        public JoinRejectedException(String reason) {
            super(reason);
        }
    }

    /** The direct socket ended without an authenticated admission verdict. */
    static final class DirectRouteLostException extends java.io.IOException {
        DirectRouteLostException(String reason) { super(reason); }
        DirectRouteLostException(String reason, Throwable cause) { super(reason, cause); }
    }

    @com.openggf.game.ModApi
    public sealed interface InboundEvent permits Control, GhostData, LobbyGhostData, Roster, Disconnected {
    }

    @com.openggf.game.ModApi
    public record Control(ControlMessage message) implements InboundEvent {
        public Control {
            Objects.requireNonNull(message, "message");
        }
    }

    @com.openggf.game.ModApi
    public record GhostData(GhostPackets.Aggregate aggregate) implements InboundEvent {
        public GhostData {
            Objects.requireNonNull(aggregate, "aggregate");
        }
    }

    /** Nearby players' lobby poses between rounds ({@link GhostPackets#TYPE_LOBBY_AGGREGATE}). */
    @com.openggf.game.ModApi
    public record LobbyGhostData(GhostPackets.Aggregate aggregate) implements InboundEvent {
        public LobbyGhostData {
            Objects.requireNonNull(aggregate, "aggregate");
        }
    }

    @com.openggf.game.ModApi
    public record Roster(List<GhostPackets.RosterEntry> entries) implements InboundEvent {
        public Roster {
            entries = List.copyOf(entries);
        }
    }

    @com.openggf.game.ModApi
    public record Disconnected(String reason) implements InboundEvent {
    }

    private final BoundedInboundQueue<InboundEvent> inbound = new BoundedInboundQueue<>();
    private final ControlJsonCodec codec = new ControlJsonCodec();
    private final CompletableFuture<Void> closing = new CompletableFuture<>();
    private final OutboundWriter writer;
    private volatile WebSocket webSocket;
    private volatile ControlMessage.JoinAccepted joinAccepted;
    private volatile boolean open;
    private volatile String serverId;
    private RaceClient() {
        writer = new OutboundWriter(WorkBudget.PROCESS, 512, 1024 * 1024,
                () -> webSocket, () -> {
                    open = false;
                    signalDisconnected("outbound limit exceeded");
                });
    }

    public static CompletableFuture<RaceClient> connect(
            URI wsUri, PlayerIdentity identity, String displayName,
            String determinismFingerprint) {
        return connect(wsUri, identity, displayName, determinismFingerprint, null, null);
    }

    /** Connects to a broker-listed direct room using its pinned certificate and host identity. */
    public static CompletableFuture<RaceClient> connect(
            URI wsUri, PlayerIdentity identity, String displayName,
            String determinismFingerprint, String certificateSha256,
            String expectedServerId) {
        return connect(wsUri, identity, displayName, "", determinismFingerprint, certificateSha256,
                expectedServerId);
    }

    /**
     * As above, announcing the player's look ({@link com.openggf.net.identity.PlayerAppearance}
     * canonical wire form) in the signed Hello.
     */
    public static CompletableFuture<RaceClient> connect(
            URI wsUri, PlayerIdentity identity, String displayName, String appearance,
            String determinismFingerprint, String certificateSha256,
            String expectedServerId) {
        return connect(wsUri, identity, displayName, appearance, determinismFingerprint,
                certificateSha256, expectedServerId, RoomJoinOptions.publicRoom());
    }

    public static CompletableFuture<RaceClient> connect(
            URI wsUri, PlayerIdentity identity, String displayName, String appearance,
            String determinismFingerprint, String certificateSha256, String expectedServerId,
            RoomJoinOptions options) {
        try { NetworkEndpointPolicy.requireWebSocket(wsUri); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.failedFuture(invalid); }
        if ((certificateSha256 == null) != (expectedServerId == null)
                || (expectedServerId != null && expectedServerId.isBlank())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "direct-room certificate pin and host identity are both required"));
        }
        if (certificateSha256 != null && !"wss".equalsIgnoreCase(wsUri.getScheme())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "pinned direct rooms require wss"));
        }
        RaceClient client = new RaceClient();
        final ObservedTlsContext tls;
        try {
            tls = "wss".equalsIgnoreCase(wsUri.getScheme()) ? new ObservedTlsContext(
                    certificateSha256 == null ? SSLContext.getDefault() : pinnedSslContext(certificateSha256)) : null;
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
        ClientHandshake handshake = new ClientHandshake(
                identity, displayName, appearance, determinismFingerprint, expectedServerId,
                AuthenticationTranscript.Role.ROOM_DIRECT,
                tls == null ? () -> AuthenticationTranscript.LOCAL_PLAINTEXT : tls::binding,
                options.grantReference(), options.routeGeneration());
        CompletableFuture<RaceClient> joined = new CompletableFuture<>();
        ClientAccessGate gate = new ClientAccessGate(options);
        joined.whenComplete((value, error) -> gate.close());

        WebSocket.Listener listener = new WebSocket.Listener() {
            private final FrameAssembler assembler = new FrameAssembler();
            private boolean identityProofSent;

            @Override
            public void onOpen(WebSocket ws) {
                WebSocket.Listener.super.onOpen(ws);
            }

            @Override
            public CompletionStage<?> onText(
                    WebSocket ws, CharSequence data, boolean last) {
                try {
                    String text = assembler.onTextPart(data, last);
                    if (text != null) {
                        handleText(ws, text);
                    }
                } catch (ProtocolViolationException e) {
                    fail(ws, e);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(
                    WebSocket ws, ByteBuffer data, boolean last) {
                try {
                    byte[] packet = assembler.onBinaryPart(data, last);
                    if (packet != null) {
                        if (client.joinAccepted == null) {
                            throw new ProtocolViolationException("binary before room admission");
                        }
                        if (packet.length == 0) {
                            throw new ProtocolViolationException("empty binary packet");
                        }
                        int type = packet[0] & 0xFF;
                        if (type == GhostPackets.TYPE_GHOST_AGGREGATE) {
                            client.enqueueInbound(ws, new GhostData(
                                    GhostPackets.decodeAggregate(packet)));
                        } else if (type == GhostPackets.TYPE_LOBBY_AGGREGATE) {
                            client.enqueueInbound(ws, new LobbyGhostData(
                                    GhostPackets.decodeLobbyAggregate(packet)));
                        } else if (type == GhostPackets.TYPE_ROSTER) {
                            client.enqueueInbound(ws, new Roster(GhostPackets.decodeRoster(packet)));
                        } else {
                            throw new ProtocolViolationException(
                                    "unexpected room binary packet type " + type);
                        }
                    }
                } catch (ProtocolViolationException e) {
                    fail(ws, e);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(
                    WebSocket ws, int statusCode, String reason) {
                client.open = false;
                client.writer.abort();
                String detail = reason == null || reason.isBlank()
                        ? "connection closed" : reason;
                client.signalDisconnected(detail);
                joined.completeExceptionally(
                        new DirectRouteLostException("connection closed during join: " + detail));
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                client.open = false;
                client.writer.abort();
                client.signalDisconnected(
                        error.getMessage() == null ? error.getClass().getSimpleName()
                                : error.getMessage());
                joined.completeExceptionally(admissionTransportFailure(error));
            }

            private void handleText(WebSocket ws, String text) {
                final ControlMessage message;
                try {
                    message = client.codec.decodeRoomServer(text).message();
                } catch (ProtocolViolationException e) {
                    fail(ws, e);
                    return;
                }
                if (client.joinAccepted != null) {
                    client.enqueueInbound(ws, new Control(message));
                    return;
                }
                try {
                    switch (message) {
                        case ControlMessage.Welcome welcome -> {
                            client.serverId = welcome.serverId();
                            client.enqueueSendControl(null, handshake.onWelcome(welcome));
                            identityProofSent = true;
                        }
                        case ControlMessage.RoomAccessChallenge challenge -> {
                            if (!identityProofSent) throw new ProtocolViolationException("access challenge before identity");
                            ControlMessage.RoomAccessSubmit submission = gate.onChallenge(challenge);
                            if (submission != null) {
                                try (var password = submission.password()) {
                                    client.enqueueSendControl(null, submission);
                                }
                            }
                        }
                        case ControlMessage.JoinAccepted accepted -> {
                            if (!identityProofSent) throw new ProtocolViolationException("admission before identity");
                            gate.requireAdmission(accepted);
                            client.joinAccepted = accepted;
                            client.open = true;
                            joined.complete(client);
                        }
                        case ControlMessage.JoinRejected rejected -> {
                            joined.completeExceptionally(
                                    new JoinRejectedException(rejected.reason()));
                            ws.abort();
                        }
                        default -> throw new ProtocolViolationException("unexpected message before room admission");
                    }
                } catch (Exception e) {
                    joined.completeExceptionally(e);
                    ws.abort();
                }
            }

            private void fail(WebSocket ws, ProtocolViolationException cause) {
                client.open = false;
                client.writer.abort();
                client.signalDisconnected("protocol violation");
                joined.completeExceptionally(cause);
                ws.abort();
            }
        };

        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000));
        if (tls != null) http.sslContext(tls);
        http.build()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .buildAsync(wsUri, listener)
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        joined.completeExceptionally(admissionTransportFailure(error));
                        return;
                    }
                    client.webSocket = ws;
                    if (joined.isDone()) {
                        ws.abort();
                        return;
                    }
                    client.enqueueSendControl(null, handshake.hello());
                });

        CompletableFuture.delayedExecutor(JOIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> joined.completeExceptionally(
                        new DirectRouteLostException("direct room admission timed out")));
        joined.whenComplete((ignored, error) -> {
                    if (error != null) {
                        client.open = false;
                        client.writer.abort();
                        WebSocket ws = client.webSocket;
                        if (ws != null) {
                            ws.abort();
                        }
                    }
                });
        return joined;
    }

    private static Throwable admissionTransportFailure(Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof java.io.IOException
                && !(cause instanceof javax.net.ssl.SSLException)
                ? new DirectRouteLostException("direct connection lost before admission", error)
                : error;
    }

    private static SSLContext pinnedSslContext(String certificateSha256)
            throws GeneralSecurityException {
        final byte[] expected;
        try {
            expected = HexFormat.of().parseHex(certificateSha256);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("invalid direct-room certificate pin", e);
        }
        if (expected.length != 32) {
            throw new GeneralSecurityException("invalid direct-room certificate pin length");
        }
        X509ExtendedTrustManager pinning = new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException("client certificates are not accepted");
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType,
                                           Socket socket) throws CertificateException {
                checkClientTrusted(chain, authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType,
                                           SSLEngine engine) throws CertificateException {
                checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length != 1) {
                    throw new CertificateException("unexpected direct-room certificate chain");
                }
                byte[] actual;
                try {
                    actual = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                } catch (GeneralSecurityException e) {
                    throw new CertificateException("cannot inspect direct-room certificate", e);
                }
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw new CertificateException("direct-room certificate mismatch");
                }
                chain[0].checkValidity();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType,
                                           Socket socket) throws CertificateException {
                checkServerTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType,
                                           SSLEngine engine) throws CertificateException {
                checkServerTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {pinning}, null);
        return context;
    }

    public List<InboundEvent> drainInbound() {
        return inbound.drain();
    }

    private void enqueueInbound(WebSocket socket, InboundEvent event) {
        if (!inbound.offer(event)) {
            open = false;
            signalDisconnected("inbound event limit exceeded");
            socket.abort();
        }
    }

    private void signalDisconnected(String reason) {
        closing.complete(null);
        if (!inbound.offer(new Disconnected(reason))) {
            inbound.clear();
            inbound.offer(new Disconnected(reason));
        }
    }

    public void sendControl(ControlMessage message) {
        if (!open) {
            return;
        }
        enqueueSendControl(sessionToken(), message);
    }

    public void sendBinary(byte[] data) {
        if (webSocket != null && open) writer.offerBinary(data);
    }

    public int playerSlot() {
        return joinAccepted != null ? joinAccepted.playerSlot() : -1;
    }

    public String sessionToken() {
        return joinAccepted != null ? joinAccepted.sessionToken() : null;
    }

    public ControlMessage.JoinAccepted joinAccepted() {
        return joinAccepted;
    }

    public boolean isOpen() {
        return open;
    }

    public String serverId() {
        return serverId;
    }

    public void close() {
        open = false;
        closing.complete(null);
        writer.stop(Duration.ofSeconds(2));
    }

    CompletionStage<Void> closing() {
        return closing.minimalCompletionStage();
    }

    private void enqueueSendControl(String token, ControlMessage message) {
        if (webSocket != null) writer.offerEncoded(
                () -> codec.encode(token, message), Protocol.MAX_CONTROL_BYTES);
    }
}
