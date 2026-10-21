package com.openggf.net.protocol;

import java.net.InetAddress;
import java.net.URI;

/** No DNS lookup is performed when granting the explicit local plaintext exception. */
public final class NetworkEndpointPolicy {
    private NetworkEndpointPolicy() { }

    public static boolean literalLoopback(String host) {
        if (host == null) return false;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (!host.matches("[0-9a-fA-F:]+") && !host.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        if (!host.contains(":") && !host.contains(".")) return false;
        try { return InetAddress.ofLiteral(host).isLoopbackAddress(); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    public static void requireWebSocket(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("invalid WebSocket endpoint");
        }
        if ("wss".equalsIgnoreCase(uri.getScheme())) return;
        if ("ws".equalsIgnoreCase(uri.getScheme()) && literalLoopback(uri.getHost())) return;
        throw new IllegalArgumentException("remote WebSocket endpoints require wss; ws is literal-loopback only");
    }
}
