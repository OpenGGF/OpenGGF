package com.openggf.net.identity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributeView;
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
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

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
    private static final Set<PosixFilePermission> PRIVATE_KEY_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

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
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            Path existingAncestor = dir.toAbsolutePath().normalize().getParent();
            while (existingAncestor != null
                    && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
                existingAncestor = existingAncestor.getParent();
            }
            if (existingAncestor == null) {
                throw new IOException("identity directory has no existing ancestor");
            }
            FileAttribute<?> initialDirectoryPermissions =
                    Files.getFileStore(existingAncestor).supportsFileAttributeView("posix")
                            ? PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rwx------"))
                            : ownerOnlyAclAttribute(existingAncestor);
            Files.createDirectories(dir, initialDirectoryPermissions);
        }
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("identity path is not a directory");
        }
        Path keyPath = dir.resolve(KEY_FILE);
        Path pubPath = dir.resolve(PUB_FILE);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        boolean keyExists = Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS);
        boolean pubExists = Files.exists(pubPath, LinkOption.NOFOLLOW_LINKS);
        if (keyExists || pubExists) {
            if (!keyExists || !pubExists
                    || !Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(pubPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("identity keypair is incomplete or not regular files");
            }
            restrictPrivateKeyPermissions(keyPath);
            byte[] encodedPrivate;
            byte[] encodedPublic;
            try (var privateInput = Files.newInputStream(keyPath, LinkOption.NOFOLLOW_LINKS);
                 var publicInput = Files.newInputStream(pubPath, LinkOption.NOFOLLOW_LINKS)) {
                encodedPrivate = privateInput.readAllBytes();
                encodedPublic = publicInput.readAllBytes();
            }
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivate));
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(encodedPublic));
            return new PlayerIdentity(dir, priv, pub);
        }
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        byte[] encodedKey = pair.getPrivate().getEncoded();
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        boolean posix = Files.getFileStore(dir).supportsFileAttributeView("posix");
        FileAttribute<?> initialPermissions = posix
                ? PosixFilePermissions.asFileAttribute(PRIVATE_KEY_PERMISSIONS)
                : ownerOnlyAclAttribute(dir);
        try (SeekableByteChannel channel = Files.newByteChannel(keyPath, options,
                initialPermissions)) {
            verifyPrivateKeyPermissions(keyPath, posix);
            ByteBuffer buffer = ByteBuffer.wrap(encodedKey);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
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

    private static void restrictPrivateKeyPermissions(Path keyPath) throws IOException {
        var fileStore = Files.getFileStore(keyPath.getParent());
        if (fileStore.supportsFileAttributeView("posix")) {
            Files.getFileAttributeView(keyPath, PosixFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS).setPermissions(PRIVATE_KEY_PERMISSIONS);
            verifyPrivateKeyPermissions(keyPath, true);
        } else if (fileStore.supportsFileAttributeView("acl")) {
            Files.getFileAttributeView(keyPath, AclFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS).setAcl(ownerOnlyAcl(keyPath.getParent()));
            verifyPrivateKeyPermissions(keyPath, false);
        } else {
            throw new IOException("filesystem cannot restrict identity key permissions");
        }
    }

    private static FileAttribute<List<AclEntry>> ownerOnlyAclAttribute(Path dir)
            throws IOException {
        if (!Files.getFileStore(dir).supportsFileAttributeView("acl")) {
            throw new IOException("filesystem cannot create a private identity key");
        }
        List<AclEntry> acl = ownerOnlyAcl(dir);
        return new FileAttribute<>() {
            @Override public String name() { return "acl:acl"; }
            @Override public List<AclEntry> value() { return acl; }
        };
    }

    private static List<AclEntry> ownerOnlyAcl(Path dir) throws IOException {
        String processUser = ProcessHandle.current().info().user()
                .orElseThrow(() -> new IOException("unable to identify identity key owner"));
        var user = dir.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(processUser);
        return List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
    }

    private static void verifyPrivateKeyPermissions(Path keyPath, boolean posix)
            throws IOException {
        if (posix) {
            if (!Files.getPosixFilePermissions(keyPath, LinkOption.NOFOLLOW_LINKS)
                    .equals(PRIVATE_KEY_PERMISSIONS)) {
                throw new IOException("identity key has unsafe POSIX permissions");
            }
            return;
        }
        List<AclEntry> acl = Files.getFileAttributeView(keyPath,
                AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).getAcl();
        var owner = ownerOnlyAcl(keyPath.getParent()).getFirst().principal();
        boolean ownerCanRead = false;
        for (AclEntry entry : acl) {
            if (entry.type() == AclEntryType.ALLOW
                    && entry.permissions().contains(AclEntryPermission.READ_DATA)) {
                if (!entry.principal().equals(owner)) {
                    throw new IOException("identity key ACL permits another reader");
                }
                ownerCanRead = true;
            }
        }
        if (!ownerCanRead) {
            throw new IOException("identity key ACL denies owner read access");
        }
    }
}
