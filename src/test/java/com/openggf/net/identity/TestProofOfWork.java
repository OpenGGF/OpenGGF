package com.openggf.net.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestProofOfWork {
    @Test
    void solveProducesVerifiableNonce() {
        byte[] payload = "challenge".getBytes(StandardCharsets.UTF_8);
        long nonce = ProofOfWork.solve(payload, 12);
        assertTrue(ProofOfWork.verify(payload, nonce, 12));
        assertTrue(ProofOfWork.verify(payload, nonce, 8));
        assertFalse(ProofOfWork.verify(payload, nonce + 1, 12)
                && ProofOfWork.verify(payload, nonce + 2, 12)
                && ProofOfWork.verify(payload, nonce + 3, 12));
        assertFalse(ProofOfWork.verify("other".getBytes(StandardCharsets.UTF_8), nonce, 12));
    }

    @Test
    void meetsDifficultyCountsLeadingZeroBits() {
        byte[] hash = new byte[32];
        hash[0] = 0x00;
        hash[1] = 0x0F;
        assertTrue(ProofOfWork.meetsDifficulty(hash, 12));
        assertFalse(ProofOfWork.meetsDifficulty(hash, 13));
    }

    @Test
    void solveReusesHashingStorageAcrossNonceTrials() {
        byte[] payload = "openggf-scale-probe-v1".getBytes(StandardCharsets.UTF_8);
        ProofOfWork.solve(payload, 0);
        var bean = (com.sun.management.ThreadMXBean)
                java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long thread = Thread.currentThread().threadId();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                bean.isThreadAllocatedMemoryEnabled()
                        && bean.getThreadAllocatedBytes(thread) >= 0);

        long before = bean.getThreadAllocatedBytes(thread);
        long nonce = ProofOfWork.solve(payload, 18);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;

        assertEquals(55_088, nonce);
        assertTrue(ProofOfWork.verify(payload, nonce, 18));
        assertTrue(allocated < 256 * 1024,
                "proof solve allocated " + allocated + " bytes");
    }

    @Test
    void identityCreationStampPersistsAndReloads(@TempDir Path dir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir);
        long nonce = identity.creationPowNonce(10);
        assertTrue(ProofOfWork.verify(identity.publicKeyEncoded(), nonce, 10));
        assertTrue(Files.exists(dir.resolve("player-identity.pow")));

        PlayerIdentity reloaded = PlayerIdentity.loadOrCreate(dir);
        assertEquals(nonce, reloaded.creationPowNonce(10));

        long harder = reloaded.creationPowNonce(12);
        assertTrue(ProofOfWork.verify(identity.publicKeyEncoded(), harder, 12));
        assertEquals(harder, PlayerIdentity.loadOrCreate(dir).creationPowNonce(12));
    }
}
