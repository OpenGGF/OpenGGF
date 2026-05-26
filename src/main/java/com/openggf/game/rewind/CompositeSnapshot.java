package com.openggf.game.rewind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable composite of per-subsystem snapshots, keyed by
 * {@link RewindSnapshottable#key()}, in registration order. Returned by
 * {@link RewindRegistry#capture()} and consumed by
 * {@link RewindRegistry#restore(CompositeSnapshot)}.
 *
 * <p>The public constructor defensively copies its input to preserve the
 * documented immutability contract for arbitrary callers. The production
 * capture hot path uses {@link #owned(LinkedHashMap)} instead, which
 * wraps without copying and is package-private so the ownership transfer
 * is contained to {@link RewindRegistry#capture()}.
 */
public final class CompositeSnapshot {

    private final Map<String, Object> entries;

    public CompositeSnapshot(Map<String, Object> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    private CompositeSnapshot(LinkedHashMap<String, Object> entries, boolean transferOwnership) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    /**
     * Wraps {@code entries} as the snapshot's backing store with NO defensive
     * copy. Ownership transfers to the snapshot; the caller must not retain a
     * reference or mutate the map afterwards. Package-private — only
     * {@link RewindRegistry#capture()} should call this.
     */
    static CompositeSnapshot owned(LinkedHashMap<String, Object> entries) {
        Objects.requireNonNull(entries, "entries");
        return new CompositeSnapshot(entries, true);
    }

    public Map<String, Object> entries() {
        return entries;
    }

    public Object get(String key) {
        return entries.get(key);
    }

    public boolean containsKey(String key) {
        return entries.containsKey(key);
    }
}
