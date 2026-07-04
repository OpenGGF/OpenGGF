package com.openggf.net.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
}
