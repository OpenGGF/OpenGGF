package com.openggf.net.master;

import com.openggf.net.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVerifierRegistry {

    @Test
    void registerComputesWorkerIdAndIssuesToken(@TempDir Path dir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir);
        VerifierRegistry registry = new VerifierRegistry(() -> 0, 100);
        VerifierRegistry.Worker worker = registry.register(
                identity.publicKeyEncoded(), Set.of("0.6:cafe"));
        assertEquals(identity.fingerprint(), worker.workerId());
        assertTrue(registry.authenticate(worker.workerId(), worker.workerToken()).isPresent());
        assertTrue(registry.authenticate(worker.workerId(), "wrong").isEmpty());
    }

    @Test
    void staleWorkerNotAvailableUntilReregister(@TempDir Path dir) throws Exception {
        long[] now = {0};
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir);
        VerifierRegistry registry = new VerifierRegistry(() -> now[0], 10);
        VerifierRegistry.Worker worker = registry.register(
                identity.publicKeyEncoded(), Set.of("0.6:cafe"));
        assertTrue(registry.verifierAvailable("0.6:cafe"));
        now[0] = 11;
        assertFalse(registry.verifierAvailable("0.6:cafe"));
        assertTrue(registry.authenticate(worker.workerId(), worker.workerToken()).isEmpty());
        registry.register(identity.publicKeyEncoded(), Set.of("0.6:cafe"));
        assertTrue(registry.verifierAvailable("0.6:cafe"));
    }
}
