package com.openggf.game.rewind;

import com.openggf.debug.SectionProfiler;

import java.util.LinkedHashMap;
import java.util.ArrayList;
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
 * with no entry in the snapshot — explicitly resets them, failing closed when
 * a subsystem does not provide a missing-snapshot reset implementation.
 */
@com.openggf.game.ModApi
public final class RewindRegistry {
    private static final String GAME_RNG_KEY = "gamerng";
    private static final StackWalker CALLERS = StackWalker.getInstance(java.util.Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES));

    // Engine sessions own their registry admission. A creator may still build a private registry.
    private final ClassLoader mutationAuthority = mutationCallerLoader();

    private final Map<String, RewindSnapshottable<?>> entries = new LinkedHashMap<>();
    private final Map<String, Object> nativePublicationAuthorities = new LinkedHashMap<>();
    private final Map<String, Runnable> postRestoreCallbacks = new LinkedHashMap<>();
    private final SectionProfiler profiler;
    private final Object modeAdapter;
    private long layoutVersion;
    private long courseLayoutVersion;
    private LayoutState layoutState;

    public RewindRegistry() {
        this.profiler = null;
        this.modeAdapter = null;
    }

    public RewindRegistry(SectionProfiler profiler) {
        this.profiler = profiler;
        this.modeAdapter = null;
    }

    /** The session supplies its actual controller; creator keys never select the partition. */
    public RewindRegistry(SectionProfiler profiler,
                          com.openggf.game.mode.GameplayFrameController controller) {
        this.profiler = profiler;
        this.modeAdapter = controller instanceof RewindSnapshottable<?> ? controller : null;
    }

    public void register(RewindSnapshottable<?> s) {
        requireMutationAuthority();
        insert(s);
    }

    private void insert(RewindSnapshottable<?> s) {
        Objects.requireNonNull(s, "s");
        String key = s.key();
        if (entries.putIfAbsent(key, s) != null) {
            throw new IllegalStateException(
                    "RewindSnapshottable already registered: " + key);
        }
        invalidateLayout();
        if (s != modeAdapter) courseLayoutVersion++;
    }

    /**
     * Refreshes the same adapter or another engine-bound adapter with the same owner/local identity.
     * Unowned callers cannot replace a registered subsystem by reporting its key.
     */
    public void registerOrRefresh(RewindSnapshottable<?> adapter) {
        requireMutationAuthority();
        Objects.requireNonNull(adapter, "adapter");
        String key = adapter.key();
        RewindSnapshottable<?> existing = entries.get(key);
        if (existing == adapter) return;
        if (existing != null && RewindAdapterOwnership.sameAuthority(existing, adapter)) {
            entries.put(key, adapter);
            invalidateLayout();
            if (existing != modeAdapter || adapter != modeAdapter) courseLayoutVersion++;
        } else {
            insert(adapter);
        }
    }

    // Only NativeRewindAdapterPublication admits the exact current root-module
    // service. The ordinary creator-facing refresh contract stays strict.
    void registerNativePublication(RewindSnapshottable<?> adapter, Object authority) {
        requireMutationAuthority();
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(authority, "authority");
        String key = adapter.key();
        RewindSnapshottable<?> existing = entries.get(key);
        Object previousAuthority = nativePublicationAuthorities.get(key);
        if (existing == adapter) {
            if (previousAuthority != null && previousAuthority != authority)
                throw new IllegalStateException("Native rewind publication authority cannot change: " + key);
            nativePublicationAuthorities.put(key, authority);
            return;
        }
        if (existing == null) {
            insert(adapter);
            nativePublicationAuthorities.put(key, authority);
            return;
        }
        if (previousAuthority != authority)
            throw new IllegalStateException("RewindSnapshottable already registered: " + key);
        entries.put(key, adapter);
        invalidateLayout();
        if (existing != modeAdapter || adapter != modeAdapter) courseLayoutVersion++;
    }

    public void deregister(String key) {
        requireMutationAuthority();
        var removed = entries.remove(key);
        nativePublicationAuthorities.remove(key);
        if (removed != null) {
            invalidateLayout();
            if (removed != modeAdapter) courseLayoutVersion++;
        }
    }

    public void registerPostRestoreCallback(String key, Runnable callback) {
        requireMutationAuthority();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(callback, "callback");
        if (postRestoreCallbacks.putIfAbsent(key, callback) != null) {
            throw new IllegalStateException(
                    "Post-restore callback already registered: " + key);
        }
    }

    public void deregisterPostRestoreCallback(String key) {
        requireMutationAuthority();
        postRestoreCallbacks.remove(key);
    }

    private static ClassLoader mutationCallerLoader() {
        return CALLERS.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != RewindRegistry.class && type.getClassLoader() != null)
                .findFirst().orElse(RewindRegistry.class).getClassLoader());
    }

    private void requireMutationAuthority() {
        if (mutationCallerLoader() != mutationAuthority) {
            throw new SecurityException("Session rewind registration belongs to the engine; contribute through ModContext or GameModule");
        }
    }

    public CompositeSnapshot capture() {
        if (profiler != null) {
            profiler.beginSection("rewind.capture");
        }
        try {
            LayoutState state = layoutState();
            Object[] values = new Object[state.adapters.length];
            for (int i = 0; i < state.adapters.length; i++) {
                Object value = state.adapters[i].capture();
                if (value == null) {
                    throw new NullPointerException(
                            "Rewind snapshot must not be null for key: "
                                    + state.layout.keyAt(i)
                                    + " (" + state.adapters[i].getClass().getName() + ")");
                }
                values[i] = value;
            }
            return CompositeSnapshot.owned(state.layout, values);
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
            LayoutState state = layoutState();
            if (cs.layout() == state.layout) {
                restoreSameLayout(state, cs);
            } else {
                restoreCrossLayout(state, cs);
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

    /** Registry generation changes even when an adapter is replaced under the same key. */
    public long courseLayoutVersion() { return courseLayoutVersion; }

    /** Captures a course partition, omitting only the session controller by identity. */
    public CompositeSnapshot captureCourse() {
        var values = new LinkedHashMap<String, Object>();
        entries.forEach((key, adapter) -> {
            if (adapter != modeAdapter) values.put(key, Objects.requireNonNull(adapter.capture(), key));
        });
        return new CompositeSnapshot(values);
    }

    /** Validates the entire partition before mutating any subsystem. */
    public void requireCourseLayout(CompositeSnapshot snapshot) {
        var keys = new java.util.LinkedHashSet<>(entries.keySet());
        keys.removeIf(key -> entries.get(key) == modeAdapter);
        if (!keys.equals(snapshot.entries().keySet()))
            throw new IllegalArgumentException("Course checkpoint belongs to a different registry layout");
    }

    /** Restores the exact course partition while retaining the match/mode ledger. */
    public void restoreCourse(CompositeSnapshot snapshot) {
        requireCourseLayout(snapshot);
        for (var entry : entries.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() != modeAdapter && !restoresAfterReconstruction(key))
                restoreAdapter(entry.getValue(), snapshot.get(key));
        }
        if (snapshot.containsKey(GAME_RNG_KEY)) restoreAdapter(entries.get(GAME_RNG_KEY), snapshot.get(GAME_RNG_KEY));
        postRestoreCallbacks.values().forEach(Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private static void restoreAdapter(RewindSnapshottable<?> adapter, Object snapshot) {
        ((RewindSnapshottable<Object>) adapter).restore(snapshot);
    }

    private static boolean restoresAfterReconstruction(String key) {
        // Object restore may recreate objects whose constructors consume the shared
        // ROM RNG before their captured state blobs are applied. Restore the RNG
        // cursor after those reconstruction side effects so the snapshot's seed is
        // the final post-restore seed.
        return GAME_RNG_KEY.equals(key);
    }

    private void invalidateLayout() {
        layoutVersion++;
        layoutState = null;
    }

    private LayoutState layoutState() {
        LayoutState state = layoutState;
        if (state != null) {
            return state;
        }
        ArrayList<String> keys = new ArrayList<>(entries.size());
        RewindSnapshottable<?>[] adapters = new RewindSnapshottable<?>[entries.size()];
        int index = 0;
        int gameRngIndex = -1;
        for (var entry : entries.entrySet()) {
            String key = entry.getKey();
            keys.add(key);
            adapters[index] = entry.getValue();
            if (restoresAfterReconstruction(key)) {
                gameRngIndex = index;
            }
            index++;
        }
        state = new LayoutState(
                CompositeSnapshotLayout.fromKeys(layoutVersion, keys),
                adapters,
                gameRngIndex);
        layoutState = state;
        return state;
    }

    private static void restoreSameLayout(LayoutState state, CompositeSnapshot snapshot) {
        for (int i = 0; i < state.adapters.length; i++) {
            if (i != state.gameRngIndex) {
                restoreEntry(state.adapters[i], snapshot.valueAt(i));
            }
        }
        if (state.gameRngIndex >= 0) {
            restoreEntry(state.adapters[state.gameRngIndex],
                    snapshot.valueAt(state.gameRngIndex));
        }
    }

    private static void restoreCrossLayout(LayoutState state, CompositeSnapshot snapshot) {
        for (int i = 0; i < state.adapters.length; i++) {
            if (i != state.gameRngIndex) {
                restoreCrossLayoutEntry(state, snapshot, i);
            }
        }
        if (state.gameRngIndex >= 0) {
            restoreCrossLayoutEntry(state, snapshot, state.gameRngIndex);
        }
    }

    private static void restoreCrossLayoutEntry(
            LayoutState state, CompositeSnapshot snapshot, int adapterIndex) {
        int snapshotIndex = snapshot.indexOf(state.layout.keyAt(adapterIndex));
        RewindSnapshottable<?> adapter = state.adapters[adapterIndex];
        if (snapshotIndex < 0) {
            adapter.resetForMissingSnapshot();
            return;
        }
        restoreEntry(adapter, snapshot.valueAt(snapshotIndex));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreEntry(RewindSnapshottable<?> entry, Object snapshot) {
        RewindSnapshottable raw = entry;
        raw.restore(snapshot);
    }

    private record LayoutState(
            CompositeSnapshotLayout layout,
            RewindSnapshottable<?>[] adapters,
            int gameRngIndex) {
    }
}
