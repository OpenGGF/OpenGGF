package com.openggf.net.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HexFormat;

/** Manual LAN invite: address plus the host's TLS certificate and identity pins. */
public record DirectJoinAddress(URI uri, String certificateSha256,
                                String hostFingerprint) {
    private static final int PIN_BYTES = 32;
    private static final int CODE_BYTES = PIN_BYTES * 2;

    public static String shareCode(String certificateSha256, String hostFingerprint) {
        byte[] certificate = decodeHexPin(certificateSha256);
        byte[] identity = decodeHexPin(hostFingerprint);
        byte[] packed = new byte[CODE_BYTES];
        System.arraycopy(certificate, 0, packed, 0, PIN_BYTES);
        System.arraycopy(identity, 0, packed, PIN_BYTES, PIN_BYTES);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
    }

    public static DirectJoinAddress parse(String address, int defaultPort) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("LAN invite is empty");
        }
        String[] parts = address.trim().split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("LAN invite requires a host share code");
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid LAN share code", e);
        }
        if (packed.length != CODE_BYTES || !Base64.getUrlEncoder().withoutPadding()
                .encodeToString(packed).equals(parts[1])) {
            throw new IllegalArgumentException("invalid LAN share code length or encoding");
        }
        String addressPart = parts[0];
        URI supplied = URI.create(addressPart.contains("://")
                ? addressPart : "wss://" + addressPart);
        int port = supplied.getPort() == -1 ? defaultPort : supplied.getPort();
        if (!"wss".equalsIgnoreCase(supplied.getScheme()) || supplied.getHost() == null
                || supplied.getUserInfo() != null || supplied.getQuery() != null
                || supplied.getFragment() != null || port < 1 || port > 65_535
                || (supplied.getPath() != null && !supplied.getPath().isEmpty()
                && !"/race".equals(supplied.getPath()))) {
            throw new IllegalArgumentException("LAN invite must use a wss host and valid port");
        }
        try {
            URI uri = new URI("wss", null, supplied.getHost(), port, "/race", null, null);
            return new DirectJoinAddress(uri,
                    HexFormat.of().formatHex(packed, 0, PIN_BYTES),
                    HexFormat.of().formatHex(packed, PIN_BYTES, CODE_BYTES));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid LAN host", e);
        }
    }

    private static byte[] decodeHexPin(String pin) {
        if (pin == null || !pin.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("LAN invite pins must be 64 lowercase hex digits");
        }
        return HexFormat.of().parseHex(pin);
    }
}
