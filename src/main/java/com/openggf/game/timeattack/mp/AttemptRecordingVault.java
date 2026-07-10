package com.openggf.game.timeattack.mp;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/** In-memory custody for finished attempt recordings awaiting upload requests. */
public final class AttemptRecordingVault {
    public static final long GRACE_MILLIS = 10 * 60_000L;
    private static final long UNSTAMPED = -1;

    private record Entry(byte[] bytes, long expiresAt) {
        private Entry withExpiry(long expiry) {
            return new Entry(bytes, expiry);
        }
    }

    private final LongSupplier clockMillis;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public AttemptRecordingVault(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public void put(String hashHex, byte[] encodedRecording) {
        entries.put(hashHex, new Entry(Arrays.copyOf(encodedRecording,
                encodedRecording.length), UNSTAMPED));
    }

    public Optional<byte[]> get(String hashHex) {
        Entry entry = entries.get(hashHex);
        return entry == null ? Optional.empty()
                : Optional.of(Arrays.copyOf(entry.bytes(), entry.bytes().length));
    }

    public void onRoundEnd() {
        long expiry = clockMillis.getAsLong() + GRACE_MILLIS;
        entries.replaceAll((ignored, entry) -> entry.expiresAt() == UNSTAMPED
                ? entry.withExpiry(expiry) : entry);
    }

    public int evictExpired() {
        long now = clockMillis.getAsLong();
        int before = entries.size();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() != UNSTAMPED
                && now >= entry.getValue().expiresAt());
        return before - entries.size();
    }

    public int size() {
        return entries.size();
    }
}
