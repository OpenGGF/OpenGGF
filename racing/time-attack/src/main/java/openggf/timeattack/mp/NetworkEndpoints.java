package openggf.timeattack.mp;

import java.net.InetAddress;
import java.net.URI;

/** Time Attack's network admission, before connecting or retaining upload work. */
final class NetworkEndpoints {
    private NetworkEndpoints() { }
    static URI webSocket(URI uri) { return require(uri, "wss", "ws"); }
    static URI upload(URI uri) { return require(uri, "https", "http"); }
    private static URI require(URI uri, String tls, String plain) {
        if (uri == null || uri.isOpaque() || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
            throw new IllegalArgumentException("invalid network endpoint");
        if (tls.equalsIgnoreCase(uri.getScheme())) return uri;
        if (plain.equalsIgnoreCase(uri.getScheme()) && literalLoopback(uri.getHost())) return uri;
        throw new IllegalArgumentException("remote endpoints require " + tls + "; plaintext is literal-loopback only");
    }
    static boolean literalLoopback(String host) {
        if (host == null) return false;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (!host.matches("[0-9a-fA-F:]+") && !host.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        if (!host.contains(":") && !host.contains(".")) return false;
        try { return InetAddress.ofLiteral(host).isLoopbackAddress(); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
