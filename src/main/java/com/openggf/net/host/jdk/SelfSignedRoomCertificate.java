package com.openggf.net.host.jdk;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.HexFormat;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * Fresh per-room TLS identity built from JDK APIs only.
 *
 * <p>Same contract as the Netty host's {@code SelfSignedCertificate("openggf-direct-room")}:
 * a single self-signed X.509 v3 certificate, valid from one year before creation until
 * 9999-12-31, whose SHA-256 over the DER encoding is the pin that invites and broker
 * listings carry and {@code RaceClient}'s pinning trust manager checks. The key is an
 * ECDSA P-256 pair (Netty used RSA-2048); clients pin the certificate digest, not the
 * algorithm. Nothing is written to disk.
 */
final class SelfSignedRoomCertificate {
    static final String COMMON_NAME = "openggf-direct-room";
    static final String KEY_TYPE = "EC";
    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String OID_ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2";
    private static final String OID_COMMON_NAME = "2.5.4.3";
    private static final String KEY_ALIAS = "openggf-direct-room";
    /** Netty's {@code SelfSignedCertificate} back-dates notBefore by one year to tolerate clock skew. */
    private static final long NOT_BEFORE_SKEW_MILLIS = 365L * 24 * 60 * 60 * 1000;
    /** 9999-12-31T23:59:59Z, the RFC 5280 "no well-defined expiration" value Netty also uses. */
    private static final long NOT_AFTER_EPOCH_MILLIS = 253_402_300_799_000L;

    private final X509Certificate certificate;
    private final PrivateKey privateKey;
    private final String sha256Hex;

    private SelfSignedRoomCertificate(X509Certificate certificate, PrivateKey privateKey,
                                      String sha256Hex) {
        this.certificate = certificate;
        this.privateKey = privateKey;
        this.sha256Hex = sha256Hex;
    }

    static SelfSignedRoomCertificate generate(long nowMillis) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_TYPE);
        generator.initialize(new ECGenParameterSpec(CURVE), random);
        KeyPair keys = generator.generateKeyPair();

        byte[] signatureAlgorithm = Der.sequence(Der.oid(OID_ECDSA_WITH_SHA256));
        byte[] name = Der.sequence(Der.set(Der.sequence(
                Der.oid(OID_COMMON_NAME), Der.utf8String(COMMON_NAME))));
        long notBefore = Math.floorDiv(nowMillis - NOT_BEFORE_SKEW_MILLIS, 1000L) * 1000L;
        // RFC 5280 §4.1.2.2: positive, at most 20 octets; 63 random bits plus one is never zero.
        BigInteger serial = new BigInteger(63, random).add(BigInteger.ONE);
        byte[] toBeSigned = Der.sequence(
                Der.explicit(0, Der.integer(BigInteger.TWO)), // v3
                Der.integer(serial),
                signatureAlgorithm,
                name,
                Der.sequence(Der.time(Instant.ofEpochMilli(notBefore)),
                        Der.time(Instant.ofEpochMilli(NOT_AFTER_EPOCH_MILLIS))),
                name,
                keys.getPublic().getEncoded()); // already a DER SubjectPublicKeyInfo

        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
        signer.initSign(keys.getPrivate(), random);
        signer.update(toBeSigned);
        byte[] encoded = Der.sequence(toBeSigned, signatureAlgorithm, Der.bitString(signer.sign()));

        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
        certificate.verify(keys.getPublic());
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        return new SelfSignedRoomCertificate(certificate, keys.getPrivate(), digest);
    }

    X509Certificate certificate() {
        return certificate;
    }

    /** Lower-case hex SHA-256 of the DER certificate (the direct-room pin). */
    String sha256Hex() {
        return sha256Hex;
    }

    /** Server-only TLS context presenting this certificate; client certificates are never requested. */
    SSLContext serverContext() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[] {new RoomKeyManager(certificate, privateKey)}, null,
                new SecureRandom());
        return context;
    }

    /** Presents exactly one EC key/certificate; no keystore, password or file is involved. */
    private static final class RoomKeyManager extends X509ExtendedKeyManager {
        private final X509Certificate certificate;
        private final PrivateKey privateKey;

        RoomKeyManager(X509Certificate certificate, PrivateKey privateKey) {
            this.certificate = certificate;
            this.privateKey = privateKey;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return KEY_TYPE.equals(keyType) ? KEY_ALIAS : null;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                                              SSLEngine engine) {
            return chooseServerAlias(keyType, issuers, null);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return KEY_TYPE.equals(keyType) ? new String[] {KEY_ALIAS} : null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return KEY_ALIAS.equals(alias) ? new X509Certificate[] {certificate} : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return KEY_ALIAS.equals(alias) ? privateKey : null;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                                              SSLEngine engine) {
            return null;
        }
    }
}
