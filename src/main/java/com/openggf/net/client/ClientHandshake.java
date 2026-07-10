package com.openggf.net.client;

import com.openggf.net.hub.HostHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;

import java.security.GeneralSecurityException;
import java.util.Base64;

/** Client side of the server-identity-bound nonce handshake. */
public final class ClientHandshake {
    private final PlayerIdentity identity;
    private final String displayName;
    private final String determinismFingerprint;
    private String serverId;

    public ClientHandshake(PlayerIdentity identity, String displayName,
                           String determinismFingerprint) {
        this.identity = identity;
        this.displayName = displayName;
        this.determinismFingerprint = determinismFingerprint;
    }

    public ControlMessage.Hello hello() {
        return new ControlMessage.Hello(
                Protocol.VERSION,
                Base64.getEncoder().encodeToString(identity.publicKeyEncoded()),
                displayName,
                determinismFingerprint);
    }

    public ControlMessage.AuthProof onWelcome(ControlMessage.Welcome welcome)
            throws GeneralSecurityException {
        if (welcome.protocolVersion() != Protocol.VERSION) {
            throw new GeneralSecurityException("protocol version mismatch");
        }
        serverId = welcome.serverId();
        final byte[] nonce;
        try {
            nonce = Base64.getDecoder().decode(welcome.nonceBase64());
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("invalid nonce", e);
        }
        byte[] signature = identity.sign(HostHandshake.signedBytes(nonce, serverId));
        return new ControlMessage.AuthProof(Base64.getEncoder().encodeToString(signature));
    }

    public String serverId() {
        return serverId;
    }
}
