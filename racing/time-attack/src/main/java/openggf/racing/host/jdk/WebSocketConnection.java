package openggf.racing.host.jdk;

import openggf.racing.host.ConnectionHygiene;
import openggf.racing.hub.HubConnection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * One peer of a {@link JdkRaceHostServer}: a reader thread that performs the TLS and
 * WebSocket handshakes and then decodes frames, and a writer thread that drains the
 * outbound queue so the room thread never blocks on a slow peer.
 *
 * <p>Room callbacks are never invoked here; every inbound event is handed to the host's
 * serialized room executor, in order, after {@code onConnected} and before
 * {@code onDisconnected}.
 */
final class WebSocketConnection implements HubConnection {
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int WRITE_BUFFER_BYTES = 16384;

    private record Outbound(byte[] frame, boolean last) {
    }

    private final JdkRaceHostServer host;
    private final Socket socket;
    private final String remoteHost;
    private final int id;
    private final JdkHostLimits limits;
    private final ConnectionHygiene.RateBucket rateBucket = new ConnectionHygiene.RateBucket();
    private final LinkedBlockingQueue<Outbound> outbound = new LinkedBlockingQueue<>();
    private final Outbound stopWriter = new Outbound(new byte[0], true);
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final AtomicBoolean aborted = new AtomicBoolean();
    private volatile Thread writer;

    WebSocketConnection(JdkRaceHostServer host, Socket socket, String remoteHost, int id,
                        JdkHostLimits limits) {
        this.host = host;
        this.socket = socket;
        this.remoteHost = remoteHost;
        this.id = id;
        this.limits = limits;
    }

    int id() {
        return id;
    }

    // ---- HubConnection (room thread) ----

    @Override
    public void sendText(String text) {
        if (!closeRequested.get()) {
            enqueue(WebSocketFrames.encode(WebSocketFrames.OP_TEXT,
                    text.getBytes(StandardCharsets.UTF_8)), false);
        }
    }

    @Override
    public void sendBinary(byte[] data) {
        if (!closeRequested.get()) {
            enqueue(WebSocketFrames.encode(WebSocketFrames.OP_BINARY, data), false);
        }
    }

    /** Flushes queued frames, sends a 1000 close frame carrying {@code reason}, then drops the socket. */
    @Override
    public void close(String reason) {
        requestClose(WebSocketFrames.closePayload(WebSocketFrames.CLOSE_NORMAL, reason));
    }

    @Override
    public String remoteHost() {
        return remoteHost;
    }

    @Override
    public int queuedBytes() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, queuedBytes.get()));
    }

    // ---- lifecycle ----

    /** Reader-thread body: handshakes, frame loop, then exactly one disconnect and slot release. */
    void run() {
        boolean registered = false;
        try {
            OutputStream output;
            InputStream input;
            Future<?> handshakeDeadline = host.scheduleAbort(this, limits.handshakeTimeoutMillis());
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout((int) limits.handshakeTimeoutMillis());
                Socket stream = socket;
                SSLContext tls = host.tlsContext();
                if (tls != null) {
                    SSLSocket secured = (SSLSocket) tls.getSocketFactory()
                            .createSocket(socket, null, true);
                    secured.setUseClientMode(false);
                    secured.startHandshake();
                    stream = secured;
                }
                input = new BufferedInputStream(stream.getInputStream(), READ_BUFFER_BYTES);
                output = new BufferedOutputStream(stream.getOutputStream(), WRITE_BUFFER_BYTES);
                try {
                    String accept = WebSocketHandshake.readUpgrade(input,
                            limits.maxRequestLineBytes(), limits.maxRequestHeaderBytes());
                    WebSocketHandshake.writeAccept(output, accept);
                } catch (WebSocketHandshake.Rejected rejected) {
                    WebSocketHandshake.writeRejection(output, rejected);
                    // Lingering close: closing with unread request bytes would send a
                    // reset that can destroy the response before the client reads it.
                    socket.shutdownOutput();
                    host.scheduleAbort(this, limits.closeGraceMillis());
                    discardUntilClosed(input);
                    return;
                }
            } finally {
                handshakeDeadline.cancel(false);
            }
            if (aborted.get()) {
                return;
            }
            socket.setSoTimeout((int) limits.idleTimeoutMillis());
            OutputStream writerOutput = output;
            writer = host.startIoThread("race-host-write-" + id, () -> writeLoop(writerOutput));
            registered = host.submitRoom(this, room -> room.onConnected(this));
            if (registered) {
                readLoop(input);
            }
        } catch (IOException | RuntimeException ignored) {
            // EOF, reset, read-idle timeout, TLS failure or an abort from another thread.
        } finally {
            finish(registered);
        }
    }

    /** Immediately drops the socket; pending frames are discarded. Idempotent and thread-safe. */
    void abort() {
        if (!aborted.compareAndSet(false, true)) {
            return;
        }
        closeRequested.set(true);
        outbound.clear();
        outbound.add(stopWriter);
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private void finish(boolean registered) {
        Thread writerThread = writer;
        if (closeRequested.get() && !aborted.get() && writerThread != null) {
            // Let the queued close frame reach the peer before the socket goes away.
            try {
                writerThread.join(Duration.ofMillis(limits.closeGraceMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        abort();
        if (registered) {
            host.submitRoom(this, room -> room.onDisconnected(this));
        }
        host.connectionFinished(this);
    }

    private void readLoop(InputStream input) throws IOException {
        WebSocketFrames.Reader reader = new WebSocketFrames.Reader(input,
                limits.maxFramePayloadBytes());
        ByteArrayOutputStream message = null;
        int messageOpcode = -1;
        try {
            while (true) {
                WebSocketFrames.Frame frame = reader.next();
                if (closeRequested.get()) {
                    // Our close frame is out: discard traffic until the peer's close or EOF.
                    if (frame.opcode() == WebSocketFrames.OP_CLOSE) {
                        return;
                    }
                    continue;
                }
                switch (frame.opcode()) {
                    case WebSocketFrames.OP_PING -> {
                        if (consumeRate()) {
                            enqueue(WebSocketFrames.encode(WebSocketFrames.OP_PONG,
                                    frame.payload()), false);
                        }
                    }
                    case WebSocketFrames.OP_PONG -> consumeRate();
                    case WebSocketFrames.OP_CLOSE -> {
                        requestClose(validatedClose(frame.payload()));
                        return;
                    }
                    case WebSocketFrames.OP_TEXT, WebSocketFrames.OP_BINARY -> {
                        if (messageOpcode != -1) {
                            throw new WebSocketFrames.Violation(
                                    WebSocketFrames.CLOSE_PROTOCOL_ERROR,
                                    "new message inside a fragmented message");
                        }
                        messageOpcode = frame.opcode();
                        message = new ByteArrayOutputStream();
                        append(message, messageOpcode, frame.payload());
                    }
                    case WebSocketFrames.OP_CONTINUATION -> {
                        if (messageOpcode == -1) {
                            throw new WebSocketFrames.Violation(
                                    WebSocketFrames.CLOSE_PROTOCOL_ERROR,
                                    "continuation without a message");
                        }
                        append(message, messageOpcode, frame.payload());
                    }
                    default -> throw new WebSocketFrames.Violation(
                            WebSocketFrames.CLOSE_PROTOCOL_ERROR, "reserved opcode");
                }
                if (!frame.control() && frame.fin()) {
                    deliver(messageOpcode, message.toByteArray());
                    messageOpcode = -1;
                    message = null;
                }
            }
        } catch (WebSocketFrames.Violation violation) {
            // The frame stream may be desynchronized: send the close frame, then discard
            // raw bytes until the peer closes or the close grace aborts the socket.
            requestClose(WebSocketFrames.closePayload(violation.closeCode(),
                    violation.getMessage()));
            discardUntilClosed(input);
        }
    }

    private void discardUntilClosed(InputStream input) {
        byte[] scratch = new byte[READ_BUFFER_BYTES];
        try {
            while (!aborted.get() && input.read(scratch) >= 0) {
                // Discard.
            }
        } catch (IOException ignored) {
            // Closed by the peer or by the close-grace abort.
        }
    }

    private void append(ByteArrayOutputStream message, int opcode, byte[] payload)
            throws WebSocketFrames.Violation {
        int total = message.size() + payload.length;
        if (total > limits.maxMessageBytes()) {
            throw new WebSocketFrames.Violation(WebSocketFrames.CLOSE_MESSAGE_TOO_BIG,
                    "message exceeds " + limits.maxMessageBytes() + " bytes");
        }
        if (opcode == WebSocketFrames.OP_BINARY && total > limits.maxBinaryMessageBytes()) {
            throw new WebSocketFrames.Violation(WebSocketFrames.CLOSE_MESSAGE_TOO_BIG,
                    "binary packet exceeds " + limits.maxBinaryMessageBytes() + " bytes");
        }
        message.writeBytes(payload);
    }

    private void deliver(int opcode, byte[] payload) throws WebSocketFrames.Violation {
        // Same order as RaceHostChannelHandler: rate budget first, then the payload checks.
        if (!consumeRate()) {
            return;
        }
        if (opcode == WebSocketFrames.OP_TEXT) {
            String text = WebSocketFrames.decodeUtf8(payload);
            host.submitRoom(this, room -> room.onText(this, text));
        } else {
            host.submitRoom(this, room -> room.onBinary(this, payload));
        }
    }

    /** One token per inbound data message or control frame; an empty bucket closes the peer. */
    private boolean consumeRate() {
        if (rateBucket.consume()) {
            return true;
        }
        requestClose(WebSocketFrames.closePayload(WebSocketFrames.CLOSE_POLICY_VIOLATION,
                "message rate exceeded"));
        return false;
    }

    /** RFC 6455 §5.5.1: echo a well-formed close; reject a malformed one. */
    private static byte[] validatedClose(byte[] payload) throws WebSocketFrames.Violation {
        if (payload.length == 0) {
            return payload;
        }
        if (payload.length == 1) {
            throw new WebSocketFrames.Violation(WebSocketFrames.CLOSE_PROTOCOL_ERROR,
                    "one-byte close payload");
        }
        int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        if (!WebSocketFrames.validCloseCode(code)) {
            throw new WebSocketFrames.Violation(WebSocketFrames.CLOSE_PROTOCOL_ERROR,
                    "invalid close status " + code);
        }
        byte[] reason = new byte[payload.length - 2];
        System.arraycopy(payload, 2, reason, 0, reason.length);
        WebSocketFrames.decodeUtf8(reason);
        return payload;
    }

    private void requestClose(byte[] closePayload) {
        if (!closeRequested.compareAndSet(false, true)) {
            return;
        }
        if (writer == null) {
            abort();
            return;
        }
        enqueue(WebSocketFrames.encode(WebSocketFrames.OP_CLOSE, closePayload), true);
        host.scheduleAbort(this, limits.closeGraceMillis());
    }

    private void enqueue(byte[] frame, boolean last) {
        if (aborted.get()) {
            return;
        }
        long total = queuedBytes.addAndGet(frame.length);
        if (!last && total > limits.maxQueuedOutboundBytes()) {
            // The peer stopped reading long after the hub's backpressure ladder gave up on it.
            queuedBytes.addAndGet(-frame.length);
            abort();
            return;
        }
        outbound.add(new Outbound(frame, last));
    }

    private void writeLoop(OutputStream output) {
        try {
            while (true) {
                Outbound next = outbound.take();
                if (next == stopWriter) {
                    return;
                }
                output.write(next.frame());
                queuedBytes.addAndGet(-next.frame().length);
                if (next.last()) {
                    output.flush();
                    // Half-close so the peer reads the close frame before EOF; the reader
                    // keeps draining until the peer's close, EOF or the close grace expires.
                    socket.shutdownOutput();
                    return;
                }
                if (outbound.isEmpty()) {
                    output.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abort();
        } catch (IOException | RuntimeException e) {
            abort();
        }
    }
}
