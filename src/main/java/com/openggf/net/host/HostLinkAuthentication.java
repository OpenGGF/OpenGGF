package com.openggf.net.host;

import com.openggf.net.hub.HandshakeTransport;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.AuthenticationTranscript;
import java.util.function.Supplier;

/** Internal bridge from the master client's observed TLS connection to tunneled guests. */
public final class HostLinkAuthentication {
    private HostLinkAuthentication() { }
    public static void bind(HostMasterLink link, Supplier<String> binding) {
        link.authenticationBinding = binding;
    }

    /** Keeps internal transport metadata out of the creator-facing link's declarations. */
    public static HubConnection boundTunnel(HubConnection connection, Supplier<String> binding) {
        return new BoundTunnel(connection, binding);
    }

    private record BoundTunnel(HubConnection connection, Supplier<String> binding)
            implements HubConnection, HandshakeTransport {
        @Override public String authenticationBinding() { return binding.get(); }
        @Override public AuthenticationTranscript.Role authenticationRole() {
            return AuthenticationTranscript.Role.ROOM_RELAY;
        }
        @Override public void sendText(String text) { connection.sendText(text); }
        @Override public void sendBinary(byte[] data) { connection.sendBinary(data); }
        @Override public void close(String reason) { connection.close(reason); }
        @Override public String remoteHost() { return connection.remoteHost(); }
        @Override public int queuedBytes() { return connection.queuedBytes(); }
    }
}
