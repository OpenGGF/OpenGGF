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
 * <p>Capture and restore run at a frame boundary, with no subsystem mid-step.
 * Adapters restore in registration order, which is part of the composition
 * contract: reconstruction owners must precede adapters that relink or reserve
 * their resources. The shared RNG restores after reconstruction, and all
 * post-restore callbacks run after that final RNG restore. Registration order
 * must therefore be preserved when refreshing dependent adapters.
 *
 * <p>Restore is tolerant of unknown keys (a subsystem that was
 * registered when a snapshot was captured may have been deregistered
 * since); such entries are skipped. The reverse — registered subsystems
 * with no entry in the snapshot — explicitly resets them, failing closed when
 * a subsystem does not provide a missing-snapshot reset implementation. An
 * admitted native restore guard instead rejects a missing payload before any
 * subsystem is restored or reset.
 * This key compatibility applies only after world admission: engine registries
 * reject foreign or unscoped composites before any subsystem mutation. Public
 * constructors create unscoped private registries compatible with Map snapshots
 * and other unscoped registries.
 */
@com.openggf.game.ModApi
public final class RewindRegistry {
    private static final String GAME_RNG_KEY = "gamerng";

    // Engine sessions own their registry admission. A creator may still build a private registry.
    private final ClassLoader mutationAuthority = mutationCallerLoader();

    private final Map<String, RewindSnapshottable<?>> entries = new LinkedHashMap<>();
    private final Map<String, Object> nativePublicationAuthorities = new LinkedHashMap<>();
    private final Map<String, Runnable> postRestoreCallbacks = new LinkedHashMap<>();
    private final SectionProfiler profiler;
    private final Object modeAdapter;
    private final Object scope;
    private long layoutVersion;
    private long courseLayoutVersion;
    private LayoutState layoutState;

    // Assign directly: public private-registry construction must not invoke the engine-scoped constructor.
    public RewindRegistry() {
        this.profiler = null;
        this.modeAdapter = null;
        this.scope = null;
    }

    public RewindRegistry(SectionProfiler profiler) {
        this.profiler = profiler;
        this.modeAdapter = null;
        this.scope = null;
    }

    /** The session supplies its actual controller; creator keys never select the partition. */
    public RewindRegistry(SectionProfiler profiler,
                          com.openggf.game.mode.GameplayFrameController controller) {
        this.profiler = profiler;
        this.modeAdapter = controller instanceof RewindSnapshottable<?> ? controller : null;
        this.scope = null;
    }

    RewindRegistry(SectionProfiler profiler,
                   com.openggf.game.mode.GameplayFrameController controller, Object scope) {
        this.profiler = profiler;
        this.modeAdapter = controller instanceof RewindSnapshottable<?> ? controller : null;
        this.scope = scope;
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
        validateNativePublication(adapter, authority);
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

    /** Preflight a native companion bundle before either key is inserted. */
    void validateNativePublication(RewindSnapshottable<?> adapter, Object authority) {
        requireMutationAuthority();
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(authority, "authority");
        String key = Objects.requireNonNull(adapter.key(), "native publication key");
        RewindSnapshottable<?> existing = entries.get(key);
        Object previousAuthority = nativePublicationAuthorities.get(key);
        if (existing == adapter && previousAuthority != null && previousAuthority != authority)
            throw new IllegalStateException("Native rewind publication authority cannot change: " + key);
        if (existing != null && existing != adapter && previousAuthority != authority)
            throw new IllegalStateException("RewindSnapshottable already registered: " + key);
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
        return com.openggf.util.EngineCallerAccess
                .callerOutsideOrBoundary(RewindRegistry.class).getClassLoader();
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
        requireScope(cs);
        validateNativePublications(cs, false);
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
        var keys = new ArrayList<String>(entries.size());
        var values = new ArrayList<Object>(entries.size());
        entries.forEach((key, adapter) -> {
            if (adapter != modeAdapter) {
                keys.add(key);
                values.add(Objects.requireNonNull(adapter.capture(), key));
            }
        });
        return CompositeSnapshot.owned(
                CompositeSnapshotLayout.fromKeys(courseLayoutVersion, keys, scope), values.toArray());
    }

    /** Validates the entire partition before mutating any subsystem. */
    public void requireCourseLayout(CompositeSnapshot snapshot) {
        requireScope(snapshot);
        var keys = new java.util.LinkedHashSet<>(entries.keySet());
        keys.removeIf(key -> entries.get(key) == modeAdapter);
        if (!keys.equals(snapshot.entries().keySet()))
            throw new IllegalArgumentException("Course checkpoint belongs to a different registry layout");
        validateNativePublications(snapshot, true);
    }

    /** A current native service may reject a retired visit before unrelated adapters can mutate. */
    private void validateNativePublications(CompositeSnapshot snapshot, boolean courseOnly) {
        for (var entry : entries.entrySet()) {
            var adapter = entry.getValue();
            if (courseOnly && adapter == modeAdapter) continue;
            // Authority belongs to this registry's actual root publication, never an interface or reported key alone.
            if (nativePublicationAuthorities.containsKey(entry.getKey())
                    && adapter instanceof NativeRewindRestoreGuard<?> guard) {
                if (!snapshot.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Missing native rewind capture: " + entry.getKey());
                }
                validateNativeGuard(guard, snapshot.get(entry.getKey()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateNativeGuard(NativeRewindRestoreGuard<?> guard, Object snapshot) {
        ((NativeRewindRestoreGuard<Object>) guard).validateRestore(snapshot);
    }

    private void requireScope(CompositeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.layout().hasScope(scope)) {
            throw new IllegalArgumentException("Rewind snapshot belongs to a different world scope");
        }
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
                CompositeSnapshotLayout.fromKeys(layoutVersion, keys, scope),
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
