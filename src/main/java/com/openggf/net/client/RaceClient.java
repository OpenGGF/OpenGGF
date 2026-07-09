package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.ProtocolViolationException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Network-thread WebSocket client. Listener callbacks only enqueue typed events;
 * the game thread owns all state mutation through {@link #drainInbound()}.
 */
public final class RaceClient {
    public static final long JOIN_TIMEOUT_MILLIS = 3000;

    public static final class JoinRejectedException extends RuntimeException {
        public JoinRejectedException(String reason) {
            super(reason);
        }
    }

    public sealed interface InboundEvent permits Control, GhostData, Disconnected {
    }

    public record Control(ControlMessage message) implements InboundEvent {
        public Control {
            Objects.requireNonNull(message, "message");
        }
    }

    public record GhostData(GhostPackets.Aggregate aggregate) implements InboundEvent {
        public GhostData {
            Objects.requireNonNull(aggregate, "aggregate");
        }
    }

    public record Disconnected(String reason) implements InboundEvent {
    }

    private final ConcurrentLinkedQueue<InboundEvent> inbound = new ConcurrentLinkedQueue<>();
    private final Object sendLock = new Object();
    private volatile WebSocket webSocket;
    private volatile ControlMessage.JoinAccepted joinAccepted;
    private volatile boolean open;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    private RaceClient() {
    }

    public static CompletableFuture<RaceClient> connect(
            URI wsUri, PlayerIdentity identity, String displayName,
            String determinismFingerprint) {
        RaceClient client = new RaceClient();
        ClientHandshake handshake = new ClientHandshake(
                identity, displayName, determinismFingerprint);
        CompletableFuture<RaceClient> joined = new CompletableFuture<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            private final FrameAssembler assembler = new FrameAssembler();

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
                        client.inbound.add(new GhostData(GhostPackets.decodeAggregate(packet)));
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
                String detail = reason == null || reason.isBlank()
                        ? "connection closed" : reason;
                client.inbound.add(new Disconnected(detail));
                joined.completeExceptionally(
                        new JoinRejectedException("connection closed during join: " + detail));
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                client.open = false;
                client.inbound.add(new Disconnected(
                        error.getMessage() == null ? error.getClass().getSimpleName()
                                : error.getMessage()));
                joined.completeExceptionally(error);
            }

            private void handleText(WebSocket ws, String text) {
                final ControlMessage message;
                try {
                    message = ControlCodec.decode(text).message();
                } catch (ProtocolViolationException e) {
                    fail(ws, e);
                    return;
                }
                if (client.joinAccepted != null) {
                    client.inbound.add(new Control(message));
                    return;
                }
                try {
                    switch (message) {
                        case ControlMessage.Welcome welcome -> client.enqueueSendText(
                                ControlCodec.encode(null, handshake.onWelcome(welcome)));
                        case ControlMessage.JoinAccepted accepted -> {
                            client.joinAccepted = accepted;
                            client.open = true;
                            joined.complete(client);
                        }
                        case ControlMessage.JoinRejected rejected -> {
                            joined.completeExceptionally(
                                    new JoinRejectedException(rejected.reason()));
                            ws.abort();
                        }
                        default -> client.inbound.add(new Control(message));
                    }
                } catch (Exception e) {
                    joined.completeExceptionally(e);
                    ws.abort();
                }
            }

            private void fail(WebSocket ws, ProtocolViolationException cause) {
                client.open = false;
                client.inbound.add(new Disconnected("protocol violation"));
                joined.completeExceptionally(cause);
                ws.abort();
            }
        };

        HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(JOIN_TIMEOUT_MILLIS))
                .build()
                .newWebSocketBuilder()
                .buildAsync(wsUri, listener)
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        joined.completeExceptionally(error);
                        return;
                    }
                    client.webSocket = ws;
                    if (joined.isDone()) {
                        ws.abort();
                        return;
                    }
                    client.enqueueSendText(ControlCodec.encode(null, handshake.hello()));
                });

        joined.orTimeout(JOIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        client.open = false;
                        WebSocket ws = client.webSocket;
                        if (ws != null) {
                            ws.abort();
                        }
                    }
                });
        return joined;
    }

    public List<InboundEvent> drainInbound() {
        List<InboundEvent> events = new ArrayList<>();
        InboundEvent event;
        while ((event = inbound.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    public void sendControl(ControlMessage message) {
        if (!open) {
            return;
        }
        enqueueSendText(ControlCodec.encode(sessionToken(), message));
    }

    public void sendBinary(byte[] data) {
        WebSocket ws = webSocket;
        if (ws == null || !open) {
            return;
        }
        byte[] packet = data.clone();
        synchronized (sendLock) {
            sendChain = sendChain.thenCompose(
                            ignored -> ws.sendBinary(ByteBuffer.wrap(packet), true))
                    .exceptionally(error -> null);
        }
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

    public void close() {
        open = false;
        WebSocket ws = webSocket;
        if (ws != null) {
            synchronized (sendLock) {
                sendChain = sendChain.thenCompose(
                                ignored -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
                        .exceptionally(error -> null);
            }
        }
    }

    private void enqueueSendText(String text) {
        WebSocket ws = webSocket;
        if (ws == null) {
            return;
        }
        synchronized (sendLock) {
            sendChain = sendChain.thenCompose(ignored -> ws.sendText(text, true))
                    .exceptionally(error -> null);
        }
    }
}
