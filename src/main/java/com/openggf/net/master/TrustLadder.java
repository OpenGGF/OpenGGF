package com.openggf.net.master;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Server-side trust attestation based on both observed age and clean participation. */
public final class TrustLadder {
    public enum Tier { NEW, ESTABLISHED, TRUSTED, SANCTIONED }

    public static final long NEW_CHAT_MUTE_MILLIS = 5 * 60_000L;
    public static final long ACCRUAL_MIN_INTERVAL_MILLIS = 5 * 60_000L;

    public record Thresholds(long establishedAgeMillis, int establishedCleanRounds,
                             long trustedAgeMillis, int trustedCleanRounds) {
        public Thresholds {
            if (establishedAgeMillis < 0 || establishedCleanRounds < 0
                    || trustedAgeMillis < establishedAgeMillis
                    || trustedCleanRounds < establishedCleanRounds) {
                throw new IllegalArgumentException("invalid trust thresholds");
            }
        }

        public static Thresholds defaults() {
            return new Thresholds(48L * 3_600_000, 10,
                    14L * 24 * 3_600_000, 50);
        }
    }

    private final IdentityStore store;
    private final NewIdentityCache cache;
    private final Thresholds thresholds;
    private final LongSupplier clock;
    private final Map<String, Long> lastAccrualMillis = new HashMap<>();

    public TrustLadder(IdentityStore store, NewIdentityCache cache, Thresholds thresholds,
                       LongSupplier clock) {
        this.store = store;
        this.cache = cache;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    public Tier tierOf(String fingerprint) {
        long now = clock.getAsLong();
        if (store.activeSanctions(fingerprint, now).stream()
                .anyMatch(sanction -> "BAN".equals(sanction.type()))) {
            return Tier.SANCTIONED;
        }
        IdentityStore.IdentityRecord record = store.find(fingerprint).orElse(null);
        if (record == null) {
            cache.firstSeenOf(fingerprint);
            return Tier.NEW;
        }
        long age = Math.max(0, now - record.firstSeenMillis());
        Tier computed;
        if (age >= thresholds.trustedAgeMillis()
                && record.cleanRounds() >= thresholds.trustedCleanRounds()) {
            computed = Tier.TRUSTED;
        } else if (age >= thresholds.establishedAgeMillis()
                && record.cleanRounds() >= thresholds.establishedCleanRounds()) {
            computed = Tier.ESTABLISHED;
        } else {
            computed = Tier.NEW;
        }
        if (!computed.name().equals(record.tier())) {
            store.setTier(fingerprint, computed.name());
        }
        return computed;
    }

    public void onCleanRound(String fingerprint) {
        long now = clock.getAsLong();
        Long previous = lastAccrualMillis.get(fingerprint);
        if (previous != null && now - previous < ACCRUAL_MIN_INTERVAL_MILLIS) {
            return;
        }
        long firstSeen = store.find(fingerprint)
                .map(IdentityStore.IdentityRecord::firstSeenMillis)
                .orElseGet(() -> cache.firstSeenOf(fingerprint));
        store.persistOnDurableEvent(fingerprint, firstSeen, now);
        store.recordCleanRound(fingerprint, now);
        lastAccrualMillis.put(fingerprint, now);
        tierOf(fingerprint);
    }

    public void onDisplayNameClaim(String fingerprint, String displayName) {
        long now = clock.getAsLong();
        long firstSeen = store.find(fingerprint)
                .map(IdentityStore.IdentityRecord::firstSeenMillis)
                .orElseGet(() -> cache.firstSeenOf(fingerprint));
        store.persistOnDurableEvent(fingerprint, firstSeen, now);
        store.setDisplayName(fingerprint, displayName);
    }

    public void sanction(IdentityStore.SanctionRecord record) {
        long now = clock.getAsLong();
        long firstSeen = store.find(record.fingerprint())
                .map(IdentityStore.IdentityRecord::firstSeenMillis)
                .orElseGet(() -> cache.firstSeenOf(record.fingerprint()));
        store.persistOnDurableEvent(record.fingerprint(), firstSeen, now);
        store.addSanction(record);
        if ("BAN".equals(record.type())) {
            store.setTier(record.fingerprint(), Tier.NEW.name());
            store.resetCleanRounds(record.fingerprint());
            lastAccrualMillis.remove(record.fingerprint());
        }
    }

    public boolean isBanned(String fingerprint) {
        return tierOf(fingerprint) == Tier.SANCTIONED;
    }

    public boolean canCreateRoom(String fingerprint) {
        Tier tier = tierOf(fingerprint);
        return tier != Tier.NEW && tier != Tier.SANCTIONED;
    }

    public boolean canChatYet(String fingerprint, long memberSinceMillis) {
        return tierOf(fingerprint) != Tier.NEW
                || clock.getAsLong() - memberSinceMillis > NEW_CHAT_MUTE_MILLIS;
    }
}
