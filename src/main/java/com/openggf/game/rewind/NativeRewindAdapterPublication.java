package com.openggf.game.rewind;

import com.openggf.game.GameModule;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Engine-only admission of a native root's current service incarnation. */
public final class NativeRewindAdapterPublication {
    private NativeRewindAdapterPublication() { }

    /** Derives the native owner through immutable decorators and engine-owned patch provenance. */
    public static GameModule nativeRoot(GameModule selected) {
        requireEngineCaller();
        return com.openggf.game.internal.NativeGameModuleLineage.root(selected);
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

    /** The authenticated native factory reserves its new owner before returning it to the world. */
    public static void reserveWorldPlcCreation(com.openggf.game.session.WorldSession world,
            com.openggf.game.session.WorldPlcLifecycleFactory factory,
            com.openggf.game.resources.PlcLifecycleService owner) {
        requireEngineCaller();
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(owner, "owner");
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeRewindAdapterPublication.class);
        GameModule root = nativeRoot(world.rootGameModule());
        if (caller != factory.getClass() || caller.getClassLoader() != GameModule.class.getClassLoader()
                || root == null || root.getGameService(com.openggf.game.session.WorldPlcLifecycleFactory.class) != factory
                || com.openggf.game.session.WorldPlcLifecycleAccess.owned(world) != null
                || !(owner instanceof RewindSnapshottable<?>))
            throw new IllegalArgumentException("World PLC creation must come from its actual native factory");
        var companions = worldPlcCompanions(owner);
        // Reserve the complete exact bundle before the factory exposes any
        // identity to a world/context or a contributed provider.
        if (RewindAdapterOwnership.isOwned(owner) && !RewindAdapterOwnership.isNativePublication(owner))
            throw new IllegalArgumentException("A creator-owned PLC owner cannot become native");
        for (var companion : companions) {
            if (RewindAdapterOwnership.isOwned(companion)
                    && !RewindAdapterOwnership.isNativePublication(companion))
                throw new IllegalArgumentException("A creator-owned PLC companion cannot become native");
        }
        RewindAdapterOwnership.markNativePublication(owner);
        for (var companion : companions) RewindAdapterOwnership.markNativePublication(companion);
    }

    /** Core world PLC state is not a replaceable module service incarnation. */
    public static void registerWorldPlc(RewindRegistry registry,
            com.openggf.game.session.WorldSession world, RewindSnapshottable<?> adapter) {
        requireEngineCaller();
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(adapter, "adapter");
        if (nativeRoot(world.rootGameModule()) == null
                || com.openggf.game.session.WorldPlcLifecycleAccess.owned(world) != adapter
                || !RewindAdapterOwnership.isNativePublication(adapter))
            throw new IllegalArgumentException("Adapter is not this world's native PLC owner");
        var companions = worldPlcCompanions(adapter);
        for (var companion : companions) {
            if (!RewindAdapterOwnership.isNativePublication(companion))
                throw new IllegalArgumentException("World PLC companion was not reserved by its native factory");
        }
        // Preflight the whole strongly bound bundle, so a colliding companion
        // cannot leave a newly registered half-owner behind.
        registry.validateNativePublication(adapter, world);
        for (var companion : companions) registry.validateNativePublication(companion, world);
        registry.registerNativePublication(adapter, world);
        for (var companion : companions) registry.registerNativePublication(companion, world);
    }

    /** Reserve before the exact factory exposes this final world DATA owner. */
    public static void reserveWorldDataCreation(java.lang.invoke.MethodHandles.Lookup lookup,
            com.openggf.game.session.WorldSession world,
            com.openggf.game.internal.WorldRewindAdapterFactory factory, RewindSnapshottable<?> owner) {
        requireEngineCaller();
        Objects.requireNonNull(world); Objects.requireNonNull(factory); Objects.requireNonNull(owner);
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeRewindAdapterPublication.class);
        GameModule root = nativeRoot(world.rootGameModule());
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || caller != factory.getClass() || caller.getClassLoader() != GameModule.class.getClassLoader()
                || root == null || root.getGameService(com.openggf.game.internal.WorldRewindAdapterFactory.class) != factory
                || com.openggf.game.session.WorldRewindAdapterAccess.owned(java.lang.invoke.MethodHandles.lookup(), world) != null
                || RewindAdapterOwnership.isOwned(owner))
            throw new SecurityException("World DATA reservation requires its original native factory construction");
        RewindAdapterOwnership.markNativePublication(owner);
    }

    public static void registerWorldData(RewindRegistry registry,
            com.openggf.game.session.WorldSession world, RewindSnapshottable<?> adapter) {
        requireEngineCaller();
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeRewindAdapterPublication.class);
        if (caller != com.openggf.game.session.WorldRewindAdapterAccess.class
                || nativeRoot(Objects.requireNonNull(world).rootGameModule()) == null
                || com.openggf.game.session.WorldRewindAdapterAccess.owned(java.lang.invoke.MethodHandles.lookup(), world) != adapter
                || adapter == null || !RewindAdapterOwnership.isNativePublication(adapter))
            throw new SecurityException("Only this world's reserved DATA owner may be published");
        Objects.requireNonNull(registry).validateNativePublication(adapter, world);
        registry.registerNativePublication(adapter, world);
    }

    private static java.util.List<RewindSnapshottable<?>> worldPlcCompanions(Object owner) {
        if (!(owner instanceof com.openggf.game.internal.WorldPlcPatternPublication publication))
            return java.util.List.of();
        var companions = new java.util.ArrayList<RewindSnapshottable<?>>();
        companions.add(Objects.requireNonNull(publication.recipeState(), "native PLC recipe companion"));
        var schedule = publication.ordinaryScheduleState();
        if (schedule != null) companions.add(schedule);
        var keys = new java.util.HashSet<String>();
        keys.add(Objects.requireNonNull(((RewindSnapshottable<?>) owner).key(), "native PLC owner key"));
        for (var companion : companions) {
            if (companion == owner || companion.getClass().getClassLoader() != owner.getClass().getClassLoader()
                    || Proxy.isProxyClass(companion.getClass())
                    || !keys.add(Objects.requireNonNull(companion.key(), "native PLC companion key")))
                throw new IllegalArgumentException("Invalid native world PLC companion bundle");
        }
        return java.util.List.copyOf(companions);
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
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeRewindAdapterPublication.class);
        if (caller.getClassLoader() != NativeRewindAdapterPublication.class.getClassLoader())
            throw new SecurityException("Native rewind publication belongs to the engine load lifecycle");
    }
}
