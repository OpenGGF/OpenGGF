package com.openggf.net.host.jdk;

import com.openggf.net.host.ConnectionHygiene;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.HubConnection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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
final class WebSocketConnection implements HubConnection, com.openggf.net.hub.HandshakeTransport {
    @Override public String authenticationBinding() {
        String pin = host.tlsCertificateSha256();
        if (pin != null) return "tls-leaf-sha256:" + pin;
        if (com.openggf.net.protocol.NetworkEndpointPolicy.literalLoopback(remoteHost()))
            return com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT;
        throw new IllegalArgumentException("plaintext room requires literal loopback");
    }
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int WRITE_BUFFER_BYTES = 16384;

    private record Outbound(byte[] frame, boolean last, WorkBudget.Reservation reservation) {
    }
    private record DeferredOutbound(byte[] frame, boolean last,
                                    WorkBudget.Reservation reservation) { }
    private static final long WRITE_STALL_MILLIS = 2_000;

    private final JdkRaceHostServer host;
    private final Socket socket;
    private final String remoteHost;
    private final int id;
    private final JdkHostLimits limits;
    private final WorkBudget.Account roomWork = WorkBudget.PROCESS.account(128,
            2L * 1024 * 1024);
    private final WorkBudget.Account controlWork = WorkBudget.PROCESS.account(2, 1);
    private WorkBudget.Reservation connectReservation;
    private WorkBudget.Reservation disconnectReservation;
    private final ConnectionHygiene.RateBucket rateBucket = new ConnectionHygiene.RateBucket();
    private final LinkedBlockingQueue<Outbound> outbound = new LinkedBlockingQueue<>();
    private final Outbound stopWriter = new Outbound(new byte[0], true, null);
    private final Object outboundLock = new Object();
    private final ConcurrentLinkedDeque<DeferredOutbound> deferredOutbound = new ConcurrentLinkedDeque<>();
    private final AtomicLong deferredBytes = new AtomicLong();
    private final AtomicBoolean pressureRetired = new AtomicBoolean();
    private boolean retryScheduled;
    private final WorkBudget.Account outboundWork;
    private final WorkBudget.Account deferredWork;
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
        outboundWork = WorkBudget.PROCESS.account(512, limits.maxQueuedOutboundBytes());
        deferredWork = WorkBudget.DEFERRED.account(512, limits.maxQueuedOutboundBytes());
        deferredWork.onPressure(this::evictDeferred);
        var controls = host.reserveConnectionControls(controlWork);
        connectReservation = controls.get(0);
        disconnectReservation = controls.get(1);
    }

    int id() {
        return id;
    }

    WorkBudget.Account roomWork() {
        return roomWork;
    }

    boolean aborted() {
        return aborted.get();
    }

    synchronized WorkBudget.Reservation takeControlReservation(boolean disconnect) {
        WorkBudget.Reservation result = disconnect ? disconnectReservation : connectReservation;
        if (disconnect) disconnectReservation = null;
        else connectReservation = null;
        return result;
    }

    synchronized void releaseControlReservations() {
        if (connectReservation != null) connectReservation.close();
        if (disconnectReservation != null) disconnectReservation.close();
        connectReservation = null;
        disconnectReservation = null;
    }

    // ---- HubConnection (room thread) ----

    @Override
    public void sendText(String text) {
        if (!closeRequested.get()) {
            int length = utf8Length(text);
            enqueue(() -> WebSocketFrames.encode(WebSocketFrames.OP_TEXT,
                    text.getBytes(StandardCharsets.UTF_8)),
                    WebSocketFrames.encodedLength(length), false);
        }
    }

    @Override
    public void sendBinary(byte[] data) {
        if (!closeRequested.get()) {
            enqueue(() -> WebSocketFrames.encode(WebSocketFrames.OP_BINARY, data),
                    WebSocketFrames.encodedLength(data.length), false);
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
            registered = host.submitConnectionControl(this,
                    room -> room.onConnected(this), false);
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
        pressureRetired.set(true);
        deferredWork.onPressure(null);
        closeRequested.set(true);
        synchronized (outboundLock) {
            DeferredOutbound waiting;
            while ((waiting = deferredOutbound.pollFirst()) != null) {
                queuedBytes.addAndGet(-waiting.frame().length);
                deferredBytes.addAndGet(-waiting.frame().length);
                waiting.reservation().close();
            }
            Outbound discarded;
            while ((discarded = outbound.poll()) != null) {
                if (discarded != stopWriter) {
                    queuedBytes.addAndGet(-discarded.frame().length);
                    discarded.reservation().close();
                }
            }
            outbound.add(stopWriter);
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private void evictDeferred() {
        if (!pressureRetired.compareAndSet(false, true)) return;
        DeferredOutbound waiting;
        while ((waiting = deferredOutbound.pollFirst()) != null) {
            queuedBytes.addAndGet(-waiting.frame().length);
            deferredBytes.addAndGet(-waiting.frame().length);
            waiting.reservation().close();
        }
        try { socket.close(); }
        catch (IOException ignored) { }
        Thread.ofVirtual().start(this::abort);
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
            host.submitConnectionControl(this, room -> room.onDisconnected(this), true);
        } else {
            host.connectionFinished(this);
        }
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
                            enqueue(() -> WebSocketFrames.encode(WebSocketFrames.OP_PONG,
                                            frame.payload()),
                                    WebSocketFrames.encodedLength(frame.payload().length), false);
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
                    deliver(messageOpcode, message);
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

    private void deliver(int opcode, ByteArrayOutputStream message)
            throws WebSocketFrames.Violation {
        // Same order as RaceHostChannelHandler: rate budget first, then the payload checks.
        if (!consumeRate()) {
            return;
        }
        WorkBudget.Reservation reservation = host.reserveRoom(this,
                opcode == WebSocketFrames.OP_TEXT ? 2L * message.size() : message.size());
        if (reservation == null) {
            abort();
            return;
        }
        try {
            byte[] payload = message.toByteArray();
            if (opcode == WebSocketFrames.OP_TEXT) {
                String text = WebSocketFrames.decodeUtf8(payload);
                if (!host.submitRoom(this, room -> room.onText(this, text), reservation)) {
                    abort();
                }
            } else if (!host.submitRoom(this, room -> room.onBinary(this, payload),
                    reservation)) {
                abort();
            }
        } catch (WebSocketFrames.Violation | RuntimeException | Error failure) {
            reservation.close();
            throw failure;
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
        enqueue(() -> WebSocketFrames.encode(WebSocketFrames.OP_CLOSE, closePayload),
                WebSocketFrames.encodedLength(closePayload.length), true);
        host.scheduleAbort(this, limits.closeGraceMillis());
    }

    private void enqueue(Supplier<byte[]> encoding, int reservedBytes, boolean last) {
        boolean retire = false;
        synchronized (outboundLock) {
            if (aborted.get() || pressureRetired.get() || closeRequested.get() && !last) return;
            if (!deferredOutbound.isEmpty()) {
                retire = !deferOutbound(encoding, reservedBytes, last);
            } else {
                var admission = WorkBudget.PROCESS.tryReserveDetailed(outboundWork,
                        outboundWork, new WorkBudget.Cost(1, reservedBytes));
                if (!admission.accepted()) {
                    retire = admission.refusal() != WorkBudget.Refusal.PROCESS
                            || !deferOutbound(encoding, reservedBytes, last);
                } else {
                    WorkBudget.Reservation reservation = admission.value();
                    byte[] frame;
                    try { frame = encoding.get(); }
                    catch (RuntimeException | Error failure) {
                        reservation.close();
                        abort();
                        throw failure;
                    }
                    queuedBytes.addAndGet(frame.length);
                    outbound.add(new Outbound(frame, last, reservation));
                }
            }
        }
        if (retire) abort();
    }

    private boolean deferOutbound(Supplier<byte[]> encoding, int reservedBytes, boolean last) {
        if (pressureRetired.get()) return true;
        if (outboundWork.retainedItems() + deferredOutbound.size() >= 512
                || reservedBytes > limits.maxQueuedOutboundBytes()
                - outboundWork.retainedBytes() - deferredBytes.get()) return false;
        var deferred = WorkBudget.DEFERRED.tryReserve(deferredWork, deferredWork,
                new WorkBudget.Cost(1, reservedBytes));
        while (deferred.isEmpty() && WorkBudget.DEFERRED.evictHeaviest(deferredWork))
            deferred = WorkBudget.DEFERRED.tryReserve(deferredWork, deferredWork,
                    new WorkBudget.Cost(1, reservedBytes));
        if (deferred.isEmpty()) return false;
        byte[] frame;
        try { frame = encoding.get(); }
        catch (RuntimeException | Error failure) {
            deferred.get().close();
            abort();
            throw failure;
        }
        if (pressureRetired.get()) {
            deferred.get().close();
            return true;
        }
        DeferredOutbound waiting = new DeferredOutbound(frame, last, deferred.get());
        deferredOutbound.addLast(waiting);
        deferredBytes.addAndGet(frame.length);
        queuedBytes.addAndGet(frame.length);
        if (pressureRetired.get()) {
            if (deferredOutbound.remove(waiting)) {
                deferredBytes.addAndGet(-frame.length);
                queuedBytes.addAndGet(-frame.length);
                waiting.reservation().close();
            }
            return true;
        }
        scheduleRetryLocked();
        return true;
    }

    private void scheduleRetryLocked() {
        if (retryScheduled || deferredOutbound.isEmpty() || aborted.get()
                || pressureRetired.get()) return;
        retryScheduled = true;
        if (!host.scheduleOutboundRetry(this::retryDeferred)) {
            retryScheduled = false;
            abort();
        }
    }

    private void retryDeferred() {
        synchronized (outboundLock) {
            retryScheduled = false;
            if (aborted.get() || pressureRetired.get()) return;
            while (!deferredOutbound.isEmpty()) {
                DeferredOutbound next = deferredOutbound.peekFirst();
                var admission = WorkBudget.PROCESS.tryReserveDetailed(outboundWork,
                        outboundWork, new WorkBudget.Cost(1, next.frame().length));
                if (!admission.accepted()) {
                    scheduleRetryLocked();
                    return;
                }
                if (!deferredOutbound.remove(next)) {
                    admission.value().close();
                    continue;
                }
                deferredBytes.addAndGet(-next.frame().length);
                next.reservation().close();
                if (pressureRetired.get()) {
                    queuedBytes.addAndGet(-next.frame().length);
                    admission.value().close();
                } else outbound.add(new Outbound(next.frame(), next.last(), admission.value()));
            }
        }
    }

    private static int utf8Length(String value) {
        int bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < 0x80) bytes++;
            else if (current < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(current) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else bytes += 3;
        }
        return bytes;
    }

    private void writeLoop(OutputStream output) {
        try {
            while (true) {
                Outbound next = outbound.take();
                if (next == stopWriter) {
                    return;
                }
                Future<?> writeDeadline = host.scheduleAbort(this, WRITE_STALL_MILLIS);
                try {
                    output.write(next.frame());
                    if (next.last()) {
                        output.flush();
                        // Half-close so the peer reads the close frame before EOF; the reader
                        // keeps draining until the peer's close, EOF or the close grace expires.
                        socket.shutdownOutput();
                        return;
                    }
                    if (outbound.isEmpty()) output.flush();
                } finally {
                    writeDeadline.cancel(false);
                    queuedBytes.addAndGet(-next.frame().length);
                    next.reservation().close();
                    synchronized (outboundLock) { scheduleRetryLocked(); }
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
