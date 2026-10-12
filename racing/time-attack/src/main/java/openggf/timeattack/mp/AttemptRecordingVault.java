package openggf.timeattack.mp;

import openggf.timeattack.AttemptInputRecording;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Bounded in-memory custody for finished attempts awaiting hash-specific upload requests. */
public final class AttemptRecordingVault {
    public static final long GRACE_MILLIS = 10 * 60_000L;
    static final int MAX_ENTRIES = 128;
    static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    private static final long UNSTAMPED = -1;

    /** Rejection leaves accepted custody intact, including its existing round-end expiry. */
    public enum PutResult { STORED, DUPLICATE, FULL, TOO_LARGE, CONFLICT, INVALID_KEY }

    private record Entry(byte[] bytes, long expiresAt) {
        private Entry withExpiry(long expiry) {
            return new Entry(bytes, expiry);
        }
    }

    private final LongSupplier clockMillis;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private int retainedBytes;

    public AttemptRecordingVault(LongSupplier clockMillis) {
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    /**
     * Admits before copying. UNSTAMPED custody intentionally survives until round end; a full
     * vault rejects new attempts rather than evicting those records or inventing an expiry.
     */
    public PutResult put(String hashHex, byte[] encodedRecording) {
        Objects.requireNonNull(encodedRecording, "encodedRecording");
        if (hashHex == null || hashHex.isEmpty() || hashHex.length() > 192) return PutResult.INVALID_KEY;
        if (encodedRecording.length > AttemptInputRecording.MAX_ENCODED_BYTES) return PutResult.TOO_LARGE;
        evictExpired();
        Entry existing = entries.get(hashHex);
        if (existing != null) {
            return Arrays.equals(existing.bytes(), encodedRecording) ? PutResult.DUPLICATE : PutResult.CONFLICT;
        }
        if (entries.size() >= MAX_ENTRIES || encodedRecording.length > MAX_TOTAL_BYTES - retainedBytes) {
            return PutResult.FULL;
        }
        entries.put(hashHex, new Entry(encodedRecording.clone(), UNSTAMPED));
        retainedBytes += encodedRecording.length;
        return PutResult.STORED;
    }

    public Optional<byte[]> get(String hashHex) {
        evictExpired();
        Entry entry = entries.get(hashHex);
        return entry == null ? Optional.empty() : Optional.of(entry.bytes().clone());
    }

    public void onRoundEnd() {
        long now = clockMillis.getAsLong();
        long expiry = now > Long.MAX_VALUE - GRACE_MILLIS ? Long.MAX_VALUE : now + GRACE_MILLIS;
        entries.replaceAll((ignored, entry) -> entry.expiresAt() == UNSTAMPED
                ? entry.withExpiry(expiry) : entry);
    }

    public int evictExpired() {
        long now = clockMillis.getAsLong();
        int before = entries.size();
        entries.entrySet().removeIf(entry -> {
            Entry value = entry.getValue();
            if (value.expiresAt() == UNSTAMPED || now < value.expiresAt()) return false;
            retainedBytes -= value.bytes().length;
            return true;
        });
        return before - entries.size();
    }

    public int size() {
        return entries.size();
    }

    int retainedBytes() {
        return retainedBytes;
    }
}
