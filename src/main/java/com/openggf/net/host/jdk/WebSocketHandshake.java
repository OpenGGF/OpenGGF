package com.openggf.net.host.jdk;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server half of the RFC 6455 §4 opening handshake for the {@code /race} endpoint.
 *
 * <p>Reads exactly the request head (never past the blank line, so frames a client sends
 * before reading the 101 stay in the stream), validates it and answers with either the
 * 101 upgrade or a closing error response. No subprotocol or extension is negotiated,
 * matching {@code WebSocketServerProtocolHandler("/race", null, ...)}.
 */
final class WebSocketHandshake {
    static final String PATH = "/race";
    static final String VERSION = "13";
    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WebSocketHandshake() {
    }

    /** A request that must be answered with {@code status} and then closed. */
    static final class Rejected extends Exception {
        private final int status;
        private final String reason;

        Rejected(int status, String reason, String detail) {
            super(detail, null, false, false);
            this.status = status;
            this.reason = reason;
        }

        int status() {
            return status;
        }

        String reason() {
            return reason;
        }
    }

    /** Reads and validates the upgrade request; returns the {@code Sec-WebSocket-Accept} value. */
    static String readUpgrade(InputStream input, int maxRequestLineBytes, int maxHeaderBytes)
            throws IOException, Rejected {
        String requestLine = readLine(input, maxRequestLineBytes, 414, "URI Too Long");
        String[] parts = requestLine.split(" ", -1);
        if (parts.length != 3) {
            throw new Rejected(400, "Bad Request", "malformed request line");
        }
        if (!"HTTP/1.1".equals(parts[2])) {
            throw new Rejected(400, "Bad Request", "WebSocket upgrades require HTTP/1.1");
        }
        if (!"GET".equals(parts[0])) {
            throw new Rejected(405, "Method Not Allowed", "upgrade must use GET");
        }
        if (!PATH.equals(parts[1])) {
            throw new Rejected(404, "Not Found", "unknown path");
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        int headerBudget = maxHeaderBytes;
        while (true) {
            String line = readLine(input, headerBudget, 431, "Request Header Fields Too Large");
            if (line.isEmpty()) {
                break;
            }
            headerBudget = Math.max(0, headerBudget - line.length() - 2);
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new Rejected(400, "Bad Request", "obsolete header folding");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new Rejected(400, "Bad Request", "malformed header");
            }
            String name = line.substring(0, colon);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c <= ' ' || c >= 0x7F) {
                    throw new Rejected(400, "Bad Request", "malformed header name");
                }
            }
            headers.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .add(line.substring(colon + 1).strip());
        }

        if (headers.containsKey("transfer-encoding")
                || headers.getOrDefault("content-length", List.of()).stream()
                .anyMatch(value -> !"0".equals(value))) {
            throw new Rejected(400, "Bad Request", "upgrade requests carry no body");
        }
        if (!hasToken(headers.get("upgrade"), "websocket")) {
            throw new Rejected(400, "Bad Request", "missing Upgrade: websocket");
        }
        if (!hasToken(headers.get("connection"), "upgrade")) {
            throw new Rejected(400, "Bad Request", "missing Connection: Upgrade");
        }
        List<String> versions = headers.get("sec-websocket-version");
        if (versions == null || versions.size() != 1 || !VERSION.equals(versions.getFirst())) {
            throw new Rejected(426, "Upgrade Required", "unsupported WebSocket version");
        }
        List<String> keys = headers.get("sec-websocket-key");
        if (keys == null || keys.size() != 1) {
            throw new Rejected(400, "Bad Request", "missing Sec-WebSocket-Key");
        }
        String key = keys.getFirst();
        try {
            if (Base64.getDecoder().decode(key).length != 16) {
                throw new Rejected(400, "Bad Request", "Sec-WebSocket-Key must encode 16 bytes");
            }
        } catch (IllegalArgumentException e) {
            throw new Rejected(400, "Bad Request", "Sec-WebSocket-Key is not base64");
        }
        return acceptKey(key);
    }

    static String acceptKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((key + ACCEPT_GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is a mandatory JDK algorithm", e);
        }
    }

    static void writeAccept(OutputStream output, String accept) throws IOException {
        output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    static void writeRejection(OutputStream output, Rejected rejected) throws IOException {
        String extra = rejected.status() == 426
                ? "Sec-WebSocket-Version: " + VERSION + "\r\n" : "";
        output.write(("HTTP/1.1 " + rejected.status() + " " + rejected.reason() + "\r\n"
                + extra
                + "Content-Length: 0\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static boolean hasToken(List<String> values, String token) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            for (String candidate : value.split(",")) {
                if (candidate.strip().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * One CRLF- (or bare LF-) terminated ISO-8859-1 line whose content, excluding the
     * terminator, is at most {@code maxBytes} bytes; longer lines are rejected without
     * buffering them.
     */
    private static String readLine(InputStream input, int maxBytes, int status, String reason)
            throws IOException, Rejected {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("connection closed during the upgrade request");
            }
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r'
                        ? bytes.length - 1 : bytes.length;
                if (length > maxBytes) {
                    throw new Rejected(status, reason, "request head line too long");
                }
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            // One byte of slack admits the CR of a CRLF terminator.
            if (line.size() > maxBytes) {
                throw new Rejected(status, reason, "request head line too long");
            }
            line.write(value);
        }
    }
}
