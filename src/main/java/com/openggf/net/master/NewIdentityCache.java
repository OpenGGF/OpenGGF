package com.openggf.net.master;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded LRU/TTL home for identities that have not earned a durable row. */
public final class NewIdentityCache {
    private record Entry(long firstSeenMillis, long touchedMillis) {
    }

    private final long ttlMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry> entries;

    public NewIdentityCache(int maxSize, long ttlMillis, LongSupplier clock) {
        if (maxSize < 1 || ttlMillis < 0) {
            throw new IllegalArgumentException("cache size must be positive and TTL non-negative");
        }
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        entries = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxSize;
            }
        };
    }

    public long firstSeenOf(String fingerprint) {
        long now = clock.getAsLong();
        Entry entry = entries.get(fingerprint);
        if (entry == null || now - entry.touchedMillis() > ttlMillis) {
            entry = new Entry(now, now);
        } else {
            entry = new Entry(entry.firstSeenMillis(), now);
        }
        entries.put(fingerprint, entry);
        return entry.firstSeenMillis();
    }

    public int size() {
        return entries.size();
    }
}
