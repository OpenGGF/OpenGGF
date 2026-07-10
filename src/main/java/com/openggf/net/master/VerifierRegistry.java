package com.openggf.net.master;

import com.openggf.net.identity.PlayerIdentity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/** Broker-loop registry of authenticated verifier identities and capabilities. */
public final class VerifierRegistry {
    public record Worker(String workerId, byte[] publicKeyEncoded, String workerToken,
                         Set<String> fingerprints, long lastSeenMillis) {
        public Worker {
            publicKeyEncoded = Arrays.copyOf(publicKeyEncoded, publicKeyEncoded.length);
            fingerprints = Set.copyOf(fingerprints);
        }

        @Override
        public byte[] publicKeyEncoded() {
            return Arrays.copyOf(publicKeyEncoded, publicKeyEncoded.length);
        }
    }

    private final LongSupplier clock;
    private final long staleAfterMillis;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Worker> workers = new LinkedHashMap<>();

    public VerifierRegistry(LongSupplier clock, long staleAfterMillis) {
        this.clock = clock;
        this.staleAfterMillis = staleAfterMillis;
    }

    public Worker register(byte[] publicKeyEncoded, Set<String> fingerprints) {
        String workerId = PlayerIdentity.fingerprintOf(publicKeyEncoded);
        Worker existing = workers.get(workerId);
        String token = existing == null ? newToken() : existing.workerToken();
        Worker worker = new Worker(workerId, publicKeyEncoded, token,
                fingerprints, clock.getAsLong());
        workers.put(workerId, worker);
        return worker;
    }

    public Optional<Worker> authenticate(String workerId, String workerToken) {
        Worker worker = workers.get(workerId);
        if (worker == null || !worker.workerToken().equals(workerToken)
                || stale(worker)) {
            return Optional.empty();
        }
        Worker refreshed = new Worker(worker.workerId(), worker.publicKeyEncoded(),
                worker.workerToken(), worker.fingerprints(), clock.getAsLong());
        workers.put(workerId, refreshed);
        return Optional.of(refreshed);
    }

    public boolean verifierAvailable(String determinismFingerprint) {
        return workers.values().stream().anyMatch(worker -> !stale(worker)
                && worker.fingerprints().contains(determinismFingerprint));
    }

    public int expireStale() {
        int before = workers.size();
        workers.values().removeIf(this::stale);
        return before - workers.size();
    }

    private boolean stale(Worker worker) {
        return clock.getAsLong() - worker.lastSeenMillis() > staleAfterMillis;
    }

    private String newToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
