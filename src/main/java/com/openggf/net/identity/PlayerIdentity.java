package com.openggf.net.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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
@com.openggf.game.ModApi
public final class PlayerIdentity {
    private static final String ALGORITHM = "Ed25519";
    private static final String KEY_FILE = "player-identity.key";
    private static final String PUB_FILE = "player-identity.pub";
    private static final String POW_FILE = "player-identity.pow";

    private final Path identityDir;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String fingerprint;

    private PlayerIdentity(Path identityDir, PrivateKey privateKey, PublicKey publicKey)
            throws GeneralSecurityException {
        this.identityDir = identityDir;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.fingerprint = fingerprintOf(publicKey.getEncoded());
    }

    public static PlayerIdentity loadOrCreate(Path dir) throws IOException, GeneralSecurityException {
        Files.createDirectories(dir);
        Path keyPath = dir.resolve(KEY_FILE);
        Path pubPath = dir.resolve(PUB_FILE);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        if (Files.exists(keyPath) && Files.exists(pubPath)) {
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyPath)));
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pubPath)));
            return new PlayerIdentity(dir, priv, pub);
        }
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        Files.write(keyPath, pair.getPrivate().getEncoded());
        restrictPrivateKeyPermissions(keyPath);
        Files.write(pubPath, pair.getPublic().getEncoded());
        return new PlayerIdentity(dir, pair.getPrivate(), pair.getPublic());
    }

    public String fingerprint() { return fingerprint; }
    public byte[] publicKeyEncoded() { return publicKey.getEncoded(); }

    public static String fingerprintOf(byte[] publicKeyEncoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(publicKeyEncoded));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

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

    /** Returns the persisted reusable creation stamp for at least this difficulty. */
    public synchronized long creationPowNonce(int difficultyBits) throws IOException {
        if (difficultyBits < 0 || difficultyBits > 256) {
            throw new IllegalArgumentException("difficulty must be between 0 and 256 bits");
        }
        Path powPath = identityDir.resolve(POW_FILE);
        if (Files.exists(powPath)) {
            try {
                String[] parts = Files.readString(powPath).trim().split("\\R");
                if (parts.length == 2) {
                    int storedBits = Integer.parseInt(parts[0]);
                    long storedNonce = Long.parseLong(parts[1]);
                    if (storedBits >= difficultyBits
                            && ProofOfWork.verify(publicKeyEncoded(), storedNonce,
                            difficultyBits)) {
                        return storedNonce;
                    }
                }
            } catch (NumberFormatException ignored) {
                // A truncated or malformed stamp is safely replaced below.
            }
        }
        long nonce = ProofOfWork.solve(publicKeyEncoded(), difficultyBits);
        Files.writeString(powPath, difficultyBits + System.lineSeparator() + nonce);
        return nonce;
    }

    private static void restrictPrivateKeyPermissions(Path keyPath) {
        try {
            if (Files.getFileStore(keyPath).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (UnsupportedOperationException | IOException e) {
            // Best-effort: POSIX perms not supported on this filesystem (e.g., Windows NTFS)
        }
    }
}
