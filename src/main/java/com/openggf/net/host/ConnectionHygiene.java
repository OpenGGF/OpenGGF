package com.openggf.net.host;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared per-IP connection and per-channel message-rate hygiene. */
public final class ConnectionHygiene {
    public static final int MESSAGE_RATE_BURST = 120;
    public static final int MESSAGES_PER_SECOND = 60;

    private ConnectionHygiene() { }

    public static final class ConnectionCounter {
        private final int maxPerIp;
        private final ConcurrentHashMap<String, AtomicInteger> counts =
                new ConcurrentHashMap<>();

        public ConnectionCounter(int maxPerIp) {
            this.maxPerIp = maxPerIp;
        }

        public boolean tryAcquire(String host) {
            AtomicInteger count = counts.computeIfAbsent(host, ignored -> new AtomicInteger());
            if (count.incrementAndGet() <= maxPerIp) {
                return true;
            }
            release(host);
            return false;
        }

        public void release(String host) {
            counts.computeIfPresent(host, (ignored, count) ->
                    count.decrementAndGet() <= 0 ? null : count);
        }
    }

    public static final class RateBucket {
        private double tokens = MESSAGE_RATE_BURST;
        private long lastRefillNanos = System.nanoTime();

        public boolean consume() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(MESSAGE_RATE_BURST,
                    tokens + elapsed * MESSAGES_PER_SECOND);
            lastRefillNanos = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens--;
            return true;
        }
    }
}
