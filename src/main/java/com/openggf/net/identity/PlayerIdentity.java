package com.openggf.net.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Pseudonymous Ed25519 player identity (security spec §3). Phase 1 only
 * generates/persists the keypair and exposes sign/verify + fingerprint.
 */
public final class PlayerIdentity {
    private static final String ALGORITHM = "Ed25519";
    private static final String KEY_FILE = "player-identity.key";
    private static final String PUB_FILE = "player-identity.pub";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String fingerprint;

    private PlayerIdentity(PrivateKey privateKey, PublicKey publicKey) throws GeneralSecurityException {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
    }

    public static PlayerIdentity loadOrCreate(Path dir) throws IOException, GeneralSecurityException {
        Files.createDirectories(dir);
        Path keyPath = dir.resolve(KEY_FILE);
        Path pubPath = dir.resolve(PUB_FILE);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        if (Files.exists(keyPath) && Files.exists(pubPath)) {
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyPath)));
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pubPath)));
            return new PlayerIdentity(priv, pub);
        }
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        Files.write(keyPath, pair.getPrivate().getEncoded());
        Files.write(pubPath, pair.getPublic().getEncoded());
        return new PlayerIdentity(pair.getPrivate(), pair.getPublic());
    }

    public String fingerprint() { return fingerprint; }
    public byte[] publicKeyEncoded() { return publicKey.getEncoded(); }

    public byte[] sign(byte[] message) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    public static boolean verify(byte[] publicKeyEncoded, byte[] message, byte[] sig) {
        try {
            PublicKey pub = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(publicKeyEncoded));
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(pub);
            signature.update(message);
            return signature.verify(sig);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
