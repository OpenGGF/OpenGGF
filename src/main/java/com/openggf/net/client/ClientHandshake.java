package com.openggf.net.client;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.AuthenticationTranscript;
import java.util.function.Supplier;

import java.security.GeneralSecurityException;
import java.util.Base64;

/** Client side of the server-identity-bound nonce handshake. */
public final class ClientHandshake {
    private final PlayerIdentity identity;
    private final String displayName;
    private final String appearance;
    private final String determinismFingerprint;
    private final String expectedServerId;
    private final AuthenticationTranscript.Role role;
    private final Supplier<String> transportBinding;
    private boolean signed;
    private String serverId;

    public ClientHandshake(PlayerIdentity identity, String displayName,
                           String determinismFingerprint) {
        this(identity, displayName, determinismFingerprint, null);
    }

    public ClientHandshake(PlayerIdentity identity, String displayName,
                           String determinismFingerprint, String expectedServerId) {
        this(identity, displayName, determinismFingerprint, expectedServerId,
                AuthenticationTranscript.Role.ROOM_DIRECT, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
    }

    public ClientHandshake(PlayerIdentity identity, String displayName,
                           String determinismFingerprint, String expectedServerId,
                           AuthenticationTranscript.Role role, Supplier<String> transportBinding) {
        this(identity, displayName, "", determinismFingerprint, expectedServerId, role, transportBinding);
    }

    /** @param appearance canonical {@link com.openggf.net.identity.PlayerAppearance} wire form */
    public ClientHandshake(PlayerIdentity identity, String displayName, String appearance,
                           String determinismFingerprint, String expectedServerId,
                           AuthenticationTranscript.Role role, Supplier<String> transportBinding) {
        this.identity = identity;
        this.displayName = displayName;
        this.appearance = java.util.Objects.requireNonNull(appearance, "appearance");
        this.determinismFingerprint = determinismFingerprint;
        this.expectedServerId = expectedServerId;
        this.role = role;
        this.transportBinding = transportBinding;
    }

    public ControlMessage.Hello hello() {
        return new ControlMessage.Hello(
                Protocol.VERSION,
                Base64.getEncoder().encodeToString(identity.publicKeyEncoded()),
                displayName,
                determinismFingerprint,
                appearance);
    }

    public ControlMessage.AuthProof onWelcome(ControlMessage.Welcome welcome)
            throws GeneralSecurityException {
        if (signed || welcome.protocolVersion() != Protocol.VERSION) {
            throw new GeneralSecurityException("protocol version mismatch");
        }
        if (expectedServerId != null && !expectedServerId.equals(welcome.serverId())) {
            throw new GeneralSecurityException("host identity mismatch");
        }
        serverId = welcome.serverId();
        final byte[] nonce;
        try {
            nonce = Base64.getDecoder().decode(welcome.nonceBase64());
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("invalid nonce", e);
        }
        if (nonce.length != 32) throw new GeneralSecurityException("invalid nonce length");
        byte[] signature = identity.sign(AuthenticationTranscript.encode(
                role, hello(), nonce, serverId, transportBinding.get()));
        signed = true;
        return new ControlMessage.AuthProof(Base64.getEncoder().encodeToString(signature));
    }

    public String serverId() {
        return serverId;
    }
}
