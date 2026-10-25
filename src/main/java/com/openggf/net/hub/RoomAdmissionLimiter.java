package com.openggf.net.hub;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Expiring process-wide password budgets. Active buckets are never evicted for new keys. */
final class RoomAdmissionLimiter {
    static final RoomAdmissionLimiter SHARED = new RoomAdmissionLimiter();
    static final long WINDOW_MILLIS = 60_000;
    static final int IDENTITY_ATTEMPTS = 5;
    static final int SOURCE_ATTEMPTS = 20;
    static final int ROOM_ATTEMPTS = 30;
    private static final int MAX_BUCKETS = 8192;
    private final Map<String, ArrayDeque<Long>> identities = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> sources = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> rooms = new HashMap<>();

    synchronized boolean allow(String identity, String source, String room, long now) {
        prune(identities, now);
        prune(sources, now);
        prune(rooms, now);
        if (!available(identities, identity, IDENTITY_ATTEMPTS)
                || !available(rooms, room, ROOM_ATTEMPTS)
                || source != null && !available(sources, source, SOURCE_ATTEMPTS)) return false;
        add(identities, identity, now);
        add(rooms, room, now);
        if (source != null) add(sources, source, now);
        return true;
    }

    private static boolean available(Map<String, ArrayDeque<Long>> buckets, String key, int maximum) {
        ArrayDeque<Long> bucket = buckets.get(key);
        return bucket == null ? buckets.size() < MAX_BUCKETS : bucket.size() < maximum;
    }

    private static void add(Map<String, ArrayDeque<Long>> buckets, String key, long now) {
        buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(now);
    }

    private static void prune(Map<String, ArrayDeque<Long>> buckets, long now) {
        buckets.values().removeIf(values -> {
            while (!values.isEmpty() && now - values.getFirst() >= WINDOW_MILLIS) values.removeFirst();
            return values.isEmpty();
        });
    }
}
