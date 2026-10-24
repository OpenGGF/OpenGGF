package com.openggf.game.rewind;

import com.openggf.game.GameModule;
import com.openggf.game.internal.InheritedGameModuleProvider;
import com.openggf.game.internal.NativeRewindPublicationOwner;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.io.ModInputLimits;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Engine-only admission of a native publication owner's current service incarnation. */
public final class NativeRewindAdapterPublication {
    private NativeRewindAdapterPublication() { }

    /** Returns the canonical base, preserving the established decorator-unwrapping contract. */
    public static GameModule nativeRoot(GameModule selected) {
        requireEngineCaller();
        return lineage(selected, null).canonical();
    }

    /**
     * Resolves the selected native publisher only when the actual session root occurs
     * by identity in its verified structural lineage. A shared canonical base does
     * not anchor a sibling publisher. Outer creator decorators/projections contribute
     * no native authority. Validation continues beyond an owner through the whole chain.
     */
    public static GameModule publicationOwner(GameModule actualRoot, GameModule selected) {
        requireEngineCaller();
        var lineage = lineage(selected, Objects.requireNonNull(actualRoot, "actualRoot"));
        return lineage.anchored() ? lineage.owner() : null;
    }

    private static Lineage lineage(GameModule selected, GameModule anchor) {
        Set<GameModule> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        GameModule owner = null;
        boolean anchored = false;
        boolean ownerLineageTrusted = true;
        while (selected != null) {
            if (!seen.add(selected) || seen.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
                throw new IllegalArgumentException("Invalid native module lineage");
            anchored |= selected == anchor;
            boolean engineLoaded = selected.getClass().getClassLoader() == GameModule.class.getClassLoader();
            if (Proxy.isProxyClass(selected.getClass())) {
                // A proxy's marker interfaces/classloader say nothing about its handler.
                // Below an owner, even a verified creator projection may append owned
                // publications; its reported inherited lineage cannot endorse that inventory.
                if (owner != null) ownerLineageTrusted = false;
                var handler = Proxy.getInvocationHandler(selected);
                selected = handler.getClass().getClassLoader() == GameModule.class.getClassLoader()
                        && handler instanceof InheritedGameModuleProvider inherited
                        ? inherited.inheritedModule() : null;
            } else if (selected instanceof DelegatingGameModule decorator) {
                if (owner != null && !engineLoaded) ownerLineageTrusted = false;
                if (owner == null && engineLoaded && selected instanceof NativeRewindPublicationOwner)
                    owner = selected;
                // base() is final engine code, including on foreign decorators.
                selected = decorator.base();
            } else {
                GameModule canonical = engineLoaded ? selected : null;
                return new Lineage(canonical, canonical == null || !ownerLineageTrusted
                        ? null : owner == null ? canonical : owner, anchored);
            }
        }
        return new Lineage(null, null, anchored);
    }

    /**
     * Reserves both validated inventories before creator callbacks. The suppressed
     * canonical services remain reachable through final base(); they are reserved,
     * never registered alongside the selected owner's same-key replacements.
     * Reading the inventories creates no absent service or queued work.
     */
    public static void reserve(GameModule selected) {
        requireEngineCaller();
        var lineage = lineage(selected, selected);
        var inventories = inventories(lineage);
        inventories.reserve();
    }

    public static void register(RewindRegistry registry, GameModule root, RewindSnapshottable<?> adapter) {
        requireEngineCaller();
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(adapter, "adapter");
        var lineage = lineage(root, root);
        if (lineage.owner() != root)
            throw new SecurityException("Native rewind authority belongs to the verified native publication owner");
        var inventories = inventories(lineage);
        if (inventories.selected().stream().noneMatch(published -> published == adapter))
            throw new IllegalArgumentException("Adapter is not the current native owner publication");
        inventories.reserve();
        registry.registerNativePublication(adapter, root);
    }

    private static Inventories inventories(Lineage lineage) {
        var canonical = lineage.canonical() == null ? List.<RewindSnapshottable<?>>of()
                : publications(lineage.canonical());
        var selected = lineage.owner() == null ? List.<RewindSnapshottable<?>>of()
                : lineage.owner() == lineage.canonical() ? canonical : publications(lineage.owner());
        // Validate both completely before marking either, without merging by key.
        return new Inventories(canonical, selected);
    }

    private static List<RewindSnapshottable<?>> publications(GameModule root) {
        if (root.getClass().getClassLoader() != GameModule.class.getClassLoader()
                || Proxy.isProxyClass(root.getClass()))
            throw new SecurityException("Native rewind authority belongs to the actual engine module");
        var publications = new LinkedHashMap<String,RewindSnapshottable<?>>();
        var adapters = Objects.requireNonNull(root.rewindAdapters(), "Native rewind publications");
        if (adapters.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
            throw new IllegalArgumentException("Too many native rewind publications");
        int count = 0;
        for (var published : adapters) {
            if (++count > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
                throw new IllegalArgumentException("Too many native rewind publications");
            Objects.requireNonNull(published, "Native rewind publication");
            var previous = publications.putIfAbsent(Objects.requireNonNull(published.key(), "Native publication key"), published);
            if (previous != null && previous != published)
                throw new IllegalStateException("Distinct native adapters share a publication key: " + published.key());
            if (RewindAdapterOwnership.isOwned(published) && !RewindAdapterOwnership.isNativePublication(published))
                throw new IllegalArgumentException("A creator-owned adapter is not a native root publication");
        }
        return List.copyOf(publications.values());
    }

    private record Lineage(GameModule canonical, GameModule owner, boolean anchored) { }
    private record Inventories(List<RewindSnapshottable<?>> canonical, List<RewindSnapshottable<?>> selected) {
        void reserve() {
            canonical.forEach(RewindAdapterOwnership::markNativePublication);
            selected.forEach(RewindAdapterOwnership::markNativePublication);
        }
    }

    private static void requireEngineCaller() {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeRewindAdapterPublication.class);
        if (caller.getClassLoader() != NativeRewindAdapterPublication.class.getClassLoader())
            throw new SecurityException("Native rewind publication belongs to the engine load lifecycle");
    }
}
