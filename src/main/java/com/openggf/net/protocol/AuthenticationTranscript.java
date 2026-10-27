package com.openggf.net.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.HexFormat;
import java.util.Objects;

/** Internal, canonical Ed25519 authentication transcript. Never accept a peer-supplied binding. */
public final class AuthenticationTranscript {
    public enum Role { MASTER, ROOM_DIRECT, ROOM_RELAY }
    public static final String LOCAL_PLAINTEXT = "plaintext-literal-loopback/v1";

    private AuthenticationTranscript() { }

    public static String certificateBinding(Certificate certificate) {
        try {
            return "tls-leaf-sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("cannot bind TLS certificate", failure);
        }
    }

    public static byte[] encode(Role role, ControlMessage.Hello hello, byte[] nonce,
                                String authority, String transportBinding) {
        Objects.requireNonNull(role);
        Objects.requireNonNull(hello);
        Objects.requireNonNull(authority);
        Objects.requireNonNull(transportBinding);
        if (nonce == null || nonce.length != 32) throw new IllegalArgumentException("invalid nonce");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            field(out, "OpenGGF/Ed25519/client-auth/v4");
            field(out, role.name());
            out.writeInt(hello.protocolVersion());
            field(out, hello.pubKeyBase64());
            field(out, hello.displayName());
            field(out, hello.determinismFingerprint());
            field(out, hello.appearance());
            field(out, hello.grantReference());
            out.writeLong(hello.routeGeneration());
            out.writeInt(nonce.length);
            out.write(nonce);
            field(out, authority);
            field(out, transportBinding);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void field(DataOutputStream out, String value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        // String.getBytes replaces malformed UTF-16 with '?'. Reject it so two
        // different Hello strings cannot collapse into the same signed bytes.
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (++index == value.length() || !Character.isLowSurrogate(value.charAt(index)))
                    throw new IllegalArgumentException("malformed Unicode in authentication transcript");
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("malformed Unicode in authentication transcript");
            }
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
