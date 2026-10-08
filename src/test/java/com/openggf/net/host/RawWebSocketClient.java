package com.openggf.net.host;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Byte-level WebSocket client for host transport tests: lets a test send malformed
 * upgrade requests and frames that a conforming client library refuses to produce.
 */
public final class RawWebSocketClient implements AutoCloseable {
    public static final int OP_CONTINUATION = 0x0;
    public static final int OP_TEXT = 0x1;
    public static final int OP_BINARY = 0x2;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    /** A server frame, or the end of the stream ({@code opcode == -1}). */
    public record Frame(int opcode, byte[] payload) {
        public boolean endOfStream() {
            return opcode < 0;
        }

        public int closeCode() {
            return payload.length >= 2 ? ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF) : -1;
        }

        public String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final SecureRandom random = new SecureRandom();
    private String sessionToken;

    private RawWebSocketClient(Socket socket) throws IOException {
        this.socket = socket;
        socket.setSoTimeout(10_000);
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = socket.getOutputStream();
    }

    public static RawWebSocketClient connect(int port) throws IOException {
        return new RawWebSocketClient(new Socket("127.0.0.1", port));
    }

    /** TLS without certificate checks: raw transport tests are not about the pin. */
    public static RawWebSocketClient connectTls(int port) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String type) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String type) { }
            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", port);
        socket.startHandshake();
        return new RawWebSocketClient(socket);
    }

    public Socket socket() {
        return socket;
    }

    public String sessionToken() {
        return sessionToken;
    }

    /** Sends a standard RFC 6455 upgrade for {@code /race} and returns the HTTP status. */
    public int upgrade() throws IOException {
        byte[] key = new byte[16];
        random.nextBytes(key);
        return sendRequest("GET /race HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(key) + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n");
    }

    /** Writes {@code request} verbatim and returns the response status code (-1 on EOF). */
    public int sendRequest(String request) throws IOException {
        output.write(request.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
        return readResponseStatus();
    }

    public int readResponseStatus() throws IOException {
        String status = readLine();
        if (status == null) {
            return -1;
        }
        String line;
        do {
            line = readLine();
        } while (line != null && !line.isEmpty());
        String[] parts = status.split(" ");
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
    }

    /** Full Hello/Welcome/AuthProof admission; returns the JoinAccepted (or JoinRejected) message. */
    public ControlMessage join(PlayerIdentity identity, String displayName, String fingerprint)
            throws IOException, java.security.GeneralSecurityException {
        if (upgrade() != 101) {
            throw new IOException("upgrade refused");
        }
        ClientHandshake handshake = new ClientHandshake(identity, displayName, fingerprint);
        sendText(ControlCodec.encode(null, handshake.hello()));
        ControlMessage welcome = nextControl();
        if (!(welcome instanceof ControlMessage.Welcome typed)) {
            return welcome;
        }
        sendText(ControlCodec.encode(null, handshake.onWelcome(typed)));
        ControlMessage result = nextControl();
        if (result instanceof ControlMessage.JoinAccepted accepted) {
            sessionToken = accepted.sessionToken();
        }
        return result;
    }

    /** The next text frame decoded as a room control message; skips other frames. */
    public ControlMessage nextControl() throws IOException {
        while (true) {
            Frame frame = readFrame();
            if (frame.endOfStream() || frame.opcode() == OP_CLOSE) {
                throw new EOFException("server closed before the expected message");
            }
            if (frame.opcode() == OP_TEXT) {
                return ControlCodec.decode(frame.text()).message();
            }
        }
    }

    public void sendText(String text) throws IOException {
        sendFrame(true, OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    public void sendFrame(boolean fin, int opcode, byte[] payload) throws IOException {
        sendFrame((fin ? 0x80 : 0) | opcode, true, payload, -1);
    }

    /**
     * Sends one frame with full control: {@code firstByte} carries FIN/RSV/opcode,
     * {@code masked} controls the mask bit, and {@code lengthBytes} forces a 0-, 2- or
     * 8-byte extended length ({@code -1} chooses the minimal form).
     */
    public void sendFrame(int firstByte, boolean masked, byte[] payload, int lengthBytes)
            throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(firstByte);
        int length = payload.length;
        int form = lengthBytes >= 0 ? lengthBytes : length < 126 ? 0 : length <= 0xFFFF ? 2 : 8;
        int maskBit = masked ? 0x80 : 0;
        if (form == 0) {
            frame.write(maskBit | length);
        } else if (form == 2) {
            frame.write(maskBit | 126);
            frame.write(length >>> 8);
            frame.write(length);
        } else {
            frame.write(maskBit | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((long) length >>> (8 * i)));
            }
        }
        writeMaskedPayload(frame, masked, payload);
        output.write(frame.toByteArray());
        output.flush();
    }

    /** A frame header announcing {@code announcedLength} via the 64-bit form, with no payload. */
    public void sendOversizedHeader(int firstByte, long announcedLength) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(firstByte);
        frame.write(0x80 | 127);
        for (int i = 7; i >= 0; i--) {
            frame.write((int) (announcedLength >>> (8 * i)));
        }
        frame.write(new byte[4], 0, 4);
        output.write(frame.toByteArray());
        output.flush();
    }

    public void sendRaw(byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }

    /** Next server frame; an EOF or reset yields {@link Frame#endOfStream()}. */
    public Frame readFrame() throws IOException {
        try {
            int first = input.read();
            if (first < 0) {
                return new Frame(-1, new byte[0]);
            }
            int second = readByte();
            long length = second & 0x7F;
            if (length == 126) {
                length = ((long) readByte() << 8) | readByte();
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | readByte();
                }
            }
            byte[] payload = input.readNBytes((int) length);
            if (payload.length != length) {
                return new Frame(-1, new byte[0]);
            }
            return new Frame(first & 0x0F, payload);
        } catch (SocketTimeoutException timeout) {
            throw timeout;
        } catch (IOException reset) {
            return new Frame(-1, new byte[0]);
        }
    }

    /**
     * Reads until the server ends the stream. Returns the close code the server sent
     * before EOF, {@code 0} for a close frame without a code, or {@code -1} for a bare EOF.
     *
     * @throws SocketTimeoutException if the server keeps the connection open
     */
    public int awaitServerClose() throws IOException {
        int closeCode = -1;
        while (true) {
            Frame frame = readFrame();
            if (frame.endOfStream()) {
                return closeCode;
            }
            if (frame.opcode() == OP_CLOSE) {
                closeCode = Math.max(0, frame.closeCode());
            }
        }
    }

    public void setReadTimeoutMillis(int millis) throws IOException {
        socket.setSoTimeout(millis);
    }

    /** Drops the TCP connection with a reset and without a close frame. */
    public void abortWithReset() throws IOException {
        socket.setSoLinger(true, 0);
        socket.close();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void writeMaskedPayload(ByteArrayOutputStream frame, boolean masked, byte[] payload) {
        if (!masked) {
            frame.write(payload, 0, payload.length);
            return;
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask, 0, 4);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i & 3]);
        }
    }

    private int readByte() throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException();
        }
        return value;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
            }
            if (value == '\n') {
                String text = line.toString(StandardCharsets.ISO_8859_1);
                return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
            }
            line.write(value);
        }
    }
}
