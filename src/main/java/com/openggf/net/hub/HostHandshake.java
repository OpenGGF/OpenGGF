package com.openggf.net.hub;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Host-side version, determinism, and server-bound nonce challenge. */
public final class HostHandshake {
    public sealed interface Step {
    }

    public record SendWelcome(ControlMessage.Welcome welcome) implements Step {
    }

    public record Reject(String reason) implements Step {
    }

    public record Admit(String fingerprint, String displayName, byte[] publicKeyEncoded,
                        String determinismFingerprint)
            implements Step {
        public Admit {
            publicKeyEncoded = publicKeyEncoded.clone();
        }

        @Override
        public byte[] publicKeyEncoded() {
            return publicKeyEncoded.clone();
        }
    }

    private enum State { EXPECT_HELLO, EXPECT_PROOF, DONE }

    private final String serverId;
    private final String requiredDeterminismFingerprint;
    private final SecureRandom random = new SecureRandom();

    private State state = State.EXPECT_HELLO;
    private byte[] nonce;
    private byte[] publicKeyEncoded;
    private String displayName;
    private String determinismFingerprint;

    public HostHandshake(String serverId, String requiredDeterminismFingerprint) {
        this.serverId = serverId;
        this.requiredDeterminismFingerprint = requiredDeterminismFingerprint;
    }

    public Step onHello(ControlMessage.Hello hello) {
        if (state != State.EXPECT_HELLO || hello == null) {
            return reject("handshake out of order");
        }
        if (hello.protocolVersion() != Protocol.VERSION) {
            return reject("protocol version mismatch");
        }
        if (requiredDeterminismFingerprint != null
                && !requiredDeterminismFingerprint.equals(hello.determinismFingerprint())) {
            return reject("determinism fingerprint mismatch (different game build or ROM)");
        }
        try {
            publicKeyEncoded = Base64.getDecoder().decode(hello.pubKeyBase64());
        } catch (IllegalArgumentException | NullPointerException e) {
            return reject("invalid public key");
        }
        displayName = hello.displayName() == null ? "" : hello.displayName();
        determinismFingerprint = hello.determinismFingerprint();
        nonce = new byte[32];
        random.nextBytes(nonce);
        state = State.EXPECT_PROOF;
        return new SendWelcome(new ControlMessage.Welcome(
                Protocol.VERSION, Base64.getEncoder().encodeToString(nonce), serverId));
    }

    public Step onAuthProof(ControlMessage.AuthProof proof) {
        if (state != State.EXPECT_PROOF || proof == null) {
            return reject("handshake out of order");
        }
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(proof.signatureBase64());
        } catch (IllegalArgumentException | NullPointerException e) {
            return reject("invalid signature encoding");
        }
        if (!PlayerIdentity.verify(publicKeyEncoded, signedBytes(nonce, serverId), signature)) {
            return reject("invalid signature");
        }
        state = State.DONE;
        return new Admit(sha256Hex(publicKeyEncoded), displayName, publicKeyEncoded,
                determinismFingerprint);
    }

    public static byte[] signedBytes(byte[] nonce, String serverId) {
        byte[] serverBytes = serverId.getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[nonce.length + serverBytes.length];
        System.arraycopy(nonce, 0, message, 0, nonce.length);
        System.arraycopy(serverBytes, 0, message, nonce.length, serverBytes.length);
        return message;
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Step reject(String reason) {
        state = State.DONE;
        return new Reject(reason);
    }
}
