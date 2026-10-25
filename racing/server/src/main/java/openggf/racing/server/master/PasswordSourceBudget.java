package openggf.racing.server.master;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Observed master-socket addresses remain authoritative across relay guest IDs and reconnects. */
final class PasswordSourceBudget {
    private static final int LIMIT = 20;
    private static final long WINDOW_MILLIS = 60_000;
    private final LongSupplier clock;
    private final int capacity;
    private final Map<String, ArrayDeque<Long>> attemptsByAddress = new HashMap<>();

    PasswordSourceBudget(LongSupplier clock) { this(clock, 10_000); }

    PasswordSourceBudget(LongSupplier clock, int capacity) {
        this.clock = clock;
        if (capacity < 1) throw new IllegalArgumentException("invalid source budget capacity");
        this.capacity = capacity;
    }

    boolean admit(String observedAddress) {
        long now = clock.getAsLong();
        ArrayDeque<Long> attempts = attemptsByAddress.get(observedAddress);
        if (attempts == null) {
            attemptsByAddress.values().removeIf(bucket -> {
                expire(bucket, now);
                return bucket.isEmpty();
            });
            if (attemptsByAddress.size() >= capacity) return false;
            attempts = new ArrayDeque<>();
            attemptsByAddress.put(observedAddress, attempts);
        }
        expire(attempts, now);
        if (attempts.size() >= LIMIT) return false;
        attempts.addLast(now);
        return true;
    }

    private static void expire(ArrayDeque<Long> attempts, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= WINDOW_MILLIS) {
            attempts.removeFirst();
        }
    }
}
