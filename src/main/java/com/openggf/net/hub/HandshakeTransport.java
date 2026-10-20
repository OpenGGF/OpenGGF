package com.openggf.net.hub;

import com.openggf.net.protocol.AuthenticationTranscript;

/** Internal transport-owned context; never populated from a control message. */
public interface HandshakeTransport {
    String authenticationBinding();
    default AuthenticationTranscript.Role authenticationRole() {
        return AuthenticationTranscript.Role.ROOM_DIRECT;
    }

    static String binding(HubConnection connection) {
        if (connection instanceof HandshakeTransport transport) return transport.authenticationBinding();
        throw new IllegalArgumentException("connection has no authenticated transport binding");
    }

    static AuthenticationTranscript.Role role(HubConnection connection) {
        return connection instanceof HandshakeTransport transport ? transport.authenticationRole()
                : AuthenticationTranscript.Role.ROOM_DIRECT;
    }
}
