package com.openggf.game.rewind;

import com.openggf.debug.SectionProfiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the list of {@link RewindSnapshottable} subsystems for the
 * current gameplay session. Owned by {@code GameplayModeContext}.
 *
 * <p>Capture and restore are atomic per frame: no subsystem is mid-step
 * during these operations, so registration order does not affect
 * correctness. Order is preserved for predictable diffing during
 * debugging.
 *
 * <p>Restore is tolerant of unknown keys (a subsystem that was
 * registered when a snapshot was captured may have been deregistered
 * since); such entries are skipped. The reverse — registered subsystems
 * with no entry in the snapshot — leaves them at their current state.
 */
public final class RewindRegistry {
    private static final String GAME_RNG_KEY = "gamerng";

    private final Map<String, RewindSnapshottable<?>> entries = new LinkedHashMap<>();
    private final Map<String, Runnable> postRestoreCallbacks = new LinkedHashMap<>();
    private final SectionProfiler profiler;

    public RewindRegistry() {
        this.profiler = null;
    }

    public RewindRegistry(SectionProfiler profiler) {
        this.profiler = profiler;
    }

    public void register(RewindSnapshottable<?> s) {
        Objects.requireNonNull(s, "s");
        if (entries.putIfAbsent(s.key(), s) != null) {
            throw new IllegalStateException(
                    "RewindSnapshottable already registered: " + s.key());
        }
    }

    public void deregister(String key) {
        entries.remove(key);
    }

    public void registerPostRestoreCallback(String key, Runnable callback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(callback, "callback");
        if (postRestoreCallbacks.putIfAbsent(key, callback) != null) {
            throw new IllegalStateException(
                    "Post-restore callback already registered: " + key);
        }
    }

    public void deregisterPostRestoreCallback(String key) {
        postRestoreCallbacks.remove(key);
    }

    public CompositeSnapshot capture() {
        if (profiler != null) {
            profiler.beginSection("rewind.capture");
        }
        try {
            var bundle = new LinkedHashMap<String, Object>(entries.size());
            for (var e : entries.entrySet()) {
                bundle.put(e.getKey(), Objects.requireNonNull(
                        e.getValue().capture(),
                        "Rewind snapshot must not be null for key: " + e.getKey()));
            }
            return CompositeSnapshot.owned(bundle);
        } finally {
            if (profiler != null) {
                profiler.endSection("rewind.capture");
            }
        }
    }

    public void restore(CompositeSnapshot cs) {
        Objects.requireNonNull(cs, "cs");
        if (profiler != null) {
            profiler.beginSection("rewind.restore");
        }
        try {
            for (var e : entries.entrySet()) {
                if (restoresAfterReconstruction(e.getKey())) {
                    continue;
                }
                if (!cs.containsKey(e.getKey())) {
                    e.getValue().resetForMissingSnapshot();
                    continue;
                }
                restoreEntry(e.getValue(), cs.get(e.getKey()));
            }
            for (var e : entries.entrySet()) {
                if (!restoresAfterReconstruction(e.getKey())) {
                    continue;
                }
                if (!cs.containsKey(e.getKey())) {
                    e.getValue().resetForMissingSnapshot();
                    continue;
                }
                restoreEntry(e.getValue(), cs.get(e.getKey()));
            }
            for (Runnable callback : postRestoreCallbacks.values()) {
                callback.run();
            }
        } finally {
            if (profiler != null) {
                profiler.endSection("rewind.restore");
            }
        }
    }

    private static boolean restoresAfterReconstruction(String key) {
        // Object restore may recreate objects whose constructors consume the shared
        // ROM RNG before their captured state blobs are applied. Restore the RNG
        // cursor after those reconstruction side effects so the snapshot's seed is
        // the final post-restore seed.
        return GAME_RNG_KEY.equals(key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreEntry(RewindSnapshottable<?> entry, Object snapshot) {
        RewindSnapshottable raw = entry;
        raw.restore(snapshot);
    }
}
