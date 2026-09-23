package com.openggf.net.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestPlayerIdentity {
    @Test
    void createsThenReloadsSameIdentity(@TempDir Path dir) throws Exception {
        PlayerIdentity first = PlayerIdentity.loadOrCreate(dir);
        PlayerIdentity second = PlayerIdentity.loadOrCreate(dir);
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length()); // sha-256 hex
    }

    @Test
    void signaturesVerifyAndTamperFails(@TempDir Path dir) throws Exception {
        PlayerIdentity id = PlayerIdentity.loadOrCreate(dir);
        byte[] msg = "nonce:serverfp".getBytes(StandardCharsets.UTF_8);
        byte[] sig = id.sign(msg);
        assertTrue(PlayerIdentity.verify(id.publicKeyEncoded(), msg, sig));
        msg[0] ^= 0x01;
        assertFalse(PlayerIdentity.verify(id.publicKeyEncoded(), msg, sig));
    }

    @Test
    void distinctDirsProduceDistinctIdentities(@TempDir Path a, @TempDir Path b) throws Exception {
        assertNotEquals(PlayerIdentity.loadOrCreate(a).fingerprint(),
                PlayerIdentity.loadOrCreate(b).fingerprint());
    }

    @Test
    void verifyReturnsFalseOnMalformedPublicKey(@TempDir Path dir) throws Exception {
        PlayerIdentity id = PlayerIdentity.loadOrCreate(dir);
        byte[] msg = "message".getBytes(StandardCharsets.UTF_8);
        byte[] sig = id.sign(msg);
        byte[] garbageKey = new byte[] {1, 2, 3, 4, 5};
        assertFalse(PlayerIdentity.verify(garbageKey, msg, sig)); // must not throw
    }

    @Test
    void newlyCreatedIdentityDirectoryIsPrivate(@TempDir Path parent) throws Exception {
        assumeTrue(Files.getFileStore(parent).supportsFileAttributeView("posix"));
        Path dir = parent.resolve("identity");
        PlayerIdentity.loadOrCreate(dir);
        assertEquals(PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(dir));
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(dir.resolve("player-identity.key")));
    }

    @Test
    void reloadRestrictsLegacyPrivateKeyBeforeUse(@TempDir Path dir) throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        PlayerIdentity created = PlayerIdentity.loadOrCreate(dir);
        Path key = dir.resolve("player-identity.key");
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-r--r--"));

        PlayerIdentity reloaded = PlayerIdentity.loadOrCreate(dir);

        assertEquals(created.fingerprint(), reloaded.fingerprint());
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(key));
    }

    @Test
    void refusesSymlinkPrivateKey(@TempDir Path dir) throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        Path identityDir = dir.resolve("identity");
        PlayerIdentity.loadOrCreate(identityDir);
        Path key = identityDir.resolve("player-identity.key");
        Path external = dir.resolve("external.key");
        Files.move(key, external);
        Files.createSymbolicLink(key, external);

        assertThrows(java.io.IOException.class,
                () -> PlayerIdentity.loadOrCreate(identityDir));
    }
}
