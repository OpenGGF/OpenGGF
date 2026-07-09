package com.openggf.net.master;

import java.util.List;
import java.util.Optional;

/** Durable identity, trust, and sanction persistence seam. */
public interface IdentityStore extends AutoCloseable {
    record IdentityRecord(String fingerprint, long firstSeenMillis, long lastSeenMillis,
                          String displayName, String tier, int cleanRounds) {
    }

    record SanctionRecord(String fingerprint, String type, String reason, String issuer,
                          long issuedAtMillis, long expiryMillis) {
    }

    Optional<IdentityRecord> find(String fingerprint);

    void persistOnDurableEvent(String fingerprint, long firstSeenMillis, long nowMillis);

    void recordCleanRound(String fingerprint, long nowMillis);

    void resetCleanRounds(String fingerprint);

    void setDisplayName(String fingerprint, String displayName);

    void setTier(String fingerprint, String tier);

    void addSanction(SanctionRecord sanction);

    List<SanctionRecord> activeSanctions(String fingerprint, long nowMillis);

    int gcInactiveNewIdentities(long inactiveSinceMillis);

    @Override
    void close();
}
