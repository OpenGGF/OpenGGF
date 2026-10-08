package com.openggf.game.rewind;

import com.openggf.game.GameModule;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/** Engine-only admission of a native root's current service incarnation. */
public final class NativeRewindAdapterPublication {
    private NativeRewindAdapterPublication() { }

    /** Derives the native owner through immutable decorators and engine-owned patch provenance. */
    public static GameModule nativeRoot(GameModule selected) {
        requireEngineCaller();
        Set<GameModule> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (selected != null) {
            if (!seen.add(selected) || seen.size() > com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
                throw new IllegalArgumentException("Invalid native module lineage");
            if (selected instanceof com.openggf.game.patch.DelegatingGameModule decorator) {
                selected = decorator.base();
            } else if (Proxy.isProxyClass(selected.getClass())) {
                selected = com.openggf.mods.runtime.OwnerBoundGamePatch.inheritedModule(selected);
            } else {
                return selected.getClass().getClassLoader() == GameModule.class.getClassLoader() ? selected : null;
            }
        }
        return null;
    }

    /** Reserves only existing native publications before creator factories can capture their identities. */
    public static void reserve(GameModule selected) {
        requireEngineCaller();
        GameModule root = nativeRoot(selected);
        if (root == null) return;
        var adapters = publications(root);
        for (var adapter : adapters) RewindAdapterOwnership.markNativePublication(adapter);
    }

    public static void register(RewindRegistry registry, GameModule root, RewindSnapshottable<?> adapter) {
        requireEngineCaller();
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(adapter, "adapter");
        if (publications(root).stream().noneMatch(published -> published == adapter))
            throw new IllegalArgumentException("Adapter is not the current native root publication");
        RewindAdapterOwnership.markNativePublication(adapter);
        registry.registerNativePublication(adapter, root);
    }

    private static java.util.List<RewindSnapshottable<?>> publications(GameModule root) {
        if (root.getClass().getClassLoader() != GameModule.class.getClassLoader()
                || Proxy.isProxyClass(root.getClass()))
            throw new SecurityException("Native rewind authority belongs to the actual engine root module");
        var publications = new LinkedHashMap<String,RewindSnapshottable<?>>();
        var adapters = Objects.requireNonNull(root.rewindAdapters(), "Native rewind publications");
        if (adapters.size() > com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
            throw new IllegalArgumentException("Too many native rewind publications");
        for (var published : adapters) {
            Objects.requireNonNull(published, "Native rewind publication");
            var previous = publications.putIfAbsent(Objects.requireNonNull(published.key(), "Native publication key"), published);
            if (previous != null && previous != published)
                throw new IllegalStateException("Distinct native adapters share a publication key: " + published.key());
            if (RewindAdapterOwnership.isOwned(published) && !RewindAdapterOwnership.isNativePublication(published))
                throw new IllegalArgumentException("A creator-owned adapter is not a native root publication");
        }
        return java.util.List.copyOf(publications.values());
    }

    private static void requireEngineCaller() {
        Class<?> caller = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                StackWalker.Option.SHOW_HIDDEN_FRAMES)).walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != NativeRewindAdapterPublication.class && type.getClassLoader() != null)
                .findFirst().orElseThrow());
        if (caller.getClassLoader() != NativeRewindAdapterPublication.class.getClassLoader())
            throw new SecurityException("Native rewind publication belongs to the engine load lifecycle");
    }
}
