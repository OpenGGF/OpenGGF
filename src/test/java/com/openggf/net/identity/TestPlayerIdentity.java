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
    void concurrentFirstUseAcrossCallersAndPathAliasesKeepsOneKeypair(@TempDir Path parent)
            throws Exception {
        Path directory = parent.resolve("identity");
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(24);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var identities = new java.util.ArrayList<java.util.concurrent.Future<PlayerIdentity>>();
            for (int index = 0; index < 24; index++) {
                Path alias = index % 2 == 0 ? directory : parent.resolve(".").resolve("identity");
                identities.add(workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    return PlayerIdentity.loadOrCreate(alias);
                }));
            }
            boolean allReady;
            try {
                allReady = ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                start.countDown();
            }
            assertTrue(allReady);
            PlayerIdentity persisted = identities.getFirst().get(10, java.util.concurrent.TimeUnit.SECONDS);
            byte[] message = "concurrent callers".getBytes(StandardCharsets.UTF_8);
            for (var future : identities) {
                PlayerIdentity identity = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(persisted.fingerprint(), identity.fingerprint());
                assertTrue(PlayerIdentity.verify(persisted.publicKeyEncoded(), message, identity.sign(message)));
            }
            assertEquals(persisted.fingerprint(), PlayerIdentity.loadOrCreate(directory).fingerprint());
        }
    }

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
    void failedCreationLeavesNoPartialKeypairAndRetrySucceeds(@TempDir Path dir)
            throws Exception {
        Path key = dir.resolve("player-identity.key");
        Path pub = dir.resolve("player-identity.pub");
        java.security.KeyPair pair = java.security.KeyPairGenerator
                .getInstance("Ed25519").generateKeyPair();

        java.io.IOException failure = assertThrows(java.io.IOException.class,
                () -> PlayerIdentity.createKeyPair(pair, key, pub, path -> {
                    throw new java.io.IOException("injected");
                }));

        assertEquals("injected", failure.getMessage());
        assertFalse(Files.exists(key));
        assertFalse(Files.exists(pub));
        assertEquals(64, PlayerIdentity.loadOrCreate(dir).fingerprint().length());
    }

    @Test
    void failedCreationDoesNotDeleteAnotherCreatorsKeypair(@TempDir Path dir)
            throws Exception {
        PlayerIdentity existing = PlayerIdentity.loadOrCreate(dir);
        Path key = dir.resolve("player-identity.key");
        Path pub = dir.resolve("player-identity.pub");
        java.security.KeyPair pair = java.security.KeyPairGenerator
                .getInstance("Ed25519").generateKeyPair();

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> PlayerIdentity.createKeyPair(pair, key, pub, path -> { }));

        assertEquals(existing.fingerprint(), PlayerIdentity.loadOrCreate(dir).fingerprint());
    }

    @Test
    void lonePublicKeyFromInterruptedCreationIsRegenerated(@TempDir Path dir)
            throws Exception {
        PlayerIdentity.loadOrCreate(dir);
        Files.delete(dir.resolve("player-identity.key"));

        PlayerIdentity regenerated = PlayerIdentity.loadOrCreate(dir);

        assertEquals(regenerated.fingerprint(), PlayerIdentity.loadOrCreate(dir).fingerprint());
    }

    @Test
    void lonePrivateKeyIsPreservedNotReplaced(@TempDir Path dir) throws Exception {
        PlayerIdentity.loadOrCreate(dir);
        Path key = dir.resolve("player-identity.key");
        byte[] original = Files.readAllBytes(key);
        Files.delete(dir.resolve("player-identity.pub"));

        assertThrows(java.io.IOException.class, () -> PlayerIdentity.loadOrCreate(dir));
        assertArrayEquals(original, Files.readAllBytes(key));
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
