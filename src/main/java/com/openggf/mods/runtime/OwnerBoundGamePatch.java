package com.openggf.mods.runtime;

import com.openggf.game.GameModule;
import com.openggf.game.ModKeySyntax;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.code.ModFaultBoundary;

import java.lang.reflect.*;
import java.util.*;

/** Engine-owned callback ownership for explicit patches; owner comes from the published runtime plan. */
public final class OwnerBoundGamePatch {
    private OwnerBoundGamePatch() { }

    public static GamePatch wrap(String owner, GamePatch patch, ModFaultBoundary boundary) {
        requireEngineCaller();
        String trustedOwner = ModKeySyntax.requireManifestId(owner);
        Objects.requireNonNull(patch, "patch");
        if (boundary == null) return patch; // Registration-only callers may intentionally have no runtime callback boundary.
        IdentityHashMap<Object,CharacterMetadata> characterMetadata = new IdentityHashMap<>();
        return (GamePatch)Proxy.newProxyInstance(GamePatch.class.getClassLoader(), new Class<?>[]{GamePatch.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, trustedOwner);
                    if (method.getName().equals("apply"))
                        com.openggf.game.rewind.NativeRewindAdapterPublication.reserve((GameModule)args[0]);
                    return boundary.call(trustedOwner, () -> {
                        Object result = ownedInvoke(patch, method, args);
                        if (method.getName().equals("apply")) {
                            GameModule base = (GameModule)args[0];
                            com.openggf.game.rewind.NativeRewindAdapterPublication.reserve(base);
                            GameModule module = Objects.requireNonNull((GameModule)result, "Patch apply returned null");
                            return module == base ? base : module(trustedOwner, module, base, boundary, false);
                        }
                        if (method.getName().equals("providedCharacterDefinitions"))
                            return characterMetadata(trustedOwner,result,boundary,characterMetadata);
                        return result;
                    });
                });
    }

    /** Existing standalone wrappers already guard providers; this adds the missing adapter-list element boundary. */
    public static GameModule wrapStandaloneRewinds(String owner, GameModule delegate, ModFaultBoundary boundary) {
        requireEngineCaller();
        return module(ModKeySyntax.requireManifestId(owner), Objects.requireNonNull(delegate, "delegate"), null,
                Objects.requireNonNull(boundary, "boundary"), true);
    }

    /** Engine-only access to the verified inherited module behind one patch projection. */
    public static GameModule inheritedModule(GameModule module) {
        requireEngineCaller();
        if (!Proxy.isProxyClass(module.getClass())) return null;
        var handler = Proxy.getInvocationHandler(module);
        return handler instanceof Handler owned && owned.base instanceof GameModule inherited ? inherited : null;
    }

    private static void requireEngineCaller() {
        ClassLoader caller = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                StackWalker.Option.SHOW_HIDDEN_FRAMES)).walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != OwnerBoundGamePatch.class && type.getClassLoader() != null)
                .findFirst().orElseThrow().getClassLoader());
        if (caller != OwnerBoundGamePatch.class.getClassLoader())
            throw new SecurityException("Creator patch ownership belongs to its engine registration");
    }

    private static GameModule module(String owner, GameModule delegate, GameModule base,
                                     ModFaultBoundary boundary, boolean rewindOnly) {
        var handler = new Handler(owner, delegate, base, boundary, new IdentityHashMap<>(), rewindOnly);
        return (GameModule)Proxy.newProxyInstance(GameModule.class.getClassLoader(), new Class<?>[]{GameModule.class}, handler);
    }

    private static final class Handler implements InvocationHandler {
        final String owner;
        final Object delegate;
        final Object base;
        final ModFaultBoundary boundary;
        final IdentityHashMap<Object, Object> wrappers;
        final boolean rewindOnly;
        String rewindKey;

        Handler(String owner, Object delegate, Object base, ModFaultBoundary boundary,
                IdentityHashMap<Object, Object> wrappers, boolean rewindOnly) {
            this.owner = owner; this.delegate = delegate; this.base = base;
            this.boundary = boundary; this.wrappers = wrappers; this.rewindOnly = rewindOnly;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, owner);
            if (rewindKey != null && method.getName().equals("key") && method.getParameterCount() == 0) return rewindKey;
            if (delegate instanceof com.openggf.game.AbstractLevelEventManager manager
                    && method.getDeclaringClass() == com.openggf.game.LevelEventRewindResolver.class) {
                return boundary.call(owner, () -> switch (method.getName()) {
                    case "resolveLevelEventRewindManager" -> null; // Concrete mutable manager stays behind its owner projection.
                    case "resolveLevelEventRewindAdapters" -> {
                        var result = new ArrayList<RewindSnapshottable<?>>();
                        result.add((RewindSnapshottable<?>)proxy);
                        for (RewindSnapshottable<?> extra : manager.extraRewindAdapters())
                            result.add((RewindSnapshottable<?>)callback(Objects.requireNonNull(extra),RewindSnapshottable.class,null));
                        yield List.copyOf(result);
                    }
                    case "reconcileLevelEventAfterRewindRestore" -> { manager.reconcileAfterRewindRestore(); yield null; }
                    default -> throw new IllegalArgumentException("Unknown level event resolver method: " + method);
                });
            }
            boolean adapters = delegate instanceof GameModule && method.getName().equals("rewindAdapters");
            if (rewindOnly && !adapters || !rewindOnly && inheritedModuleMethod(method)) return directInvoke(delegate, method, args);
            // Getter identity is compared outside this owner's boundary: a native or earlier owner's failure stays theirs.
            Object inherited = base != null && !method.getName().startsWith("create")
                    && (adapters || (queryInterface(method, args) != null || characterRegistryQuery(method, args))
                        && (method.getParameterCount() == 0 || method.getName().equals("getGameService")))
                    ? directInvoke(base, method, args) : null;
            return boundary.call(owner, () -> returned(method, args, ownedInvoke(delegate, method, args), inherited, adapters));
        }
        private boolean inheritedModuleMethod(Method method) throws NoSuchMethodException {
            if (!(delegate instanceof GameModule) || !(base instanceof GameModule)) return false;
            Method implementation = delegate.getClass().getMethod(method.getName(), method.getParameterTypes());
            Class<?> declaring = implementation.getDeclaringClass();
            if (declaring == DelegatingGameModule.class || declaring == GameModule.class) return true;
            // A native subclass that only overrides one seam must not claim its other inherited native callbacks.
            return !Proxy.isProxyClass(delegate.getClass()) && !Proxy.isProxyClass(base.getClass())
                    && declaring == base.getClass().getMethod(method.getName(), method.getParameterTypes()).getDeclaringClass();
        }
        private Object returned(Method method, Object[] args, Object value, Object inherited, boolean adapters) {
            value = OwnerBoundInitialization.returned(owner,boundary,com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES,
                    delegate,method,value);
            if (delegate instanceof RewindSnapshottable<?> && method.getName().equals("capture")
                    && method.getParameterCount() == 0)
                value = Objects.requireNonNull(value,"Owned rewind snapshot");
            if (delegate instanceof com.openggf.game.zone.ZoneRuntimeState && method.getName().equals("captureBytes"))
                value = Objects.requireNonNull(value,"Owned zone runtime snapshot");
            if (delegate instanceof com.openggf.game.render.SpecialRenderEffect && method.getName().equals("stage"))
                value = Objects.requireNonNull(value,"Owned render effect stage");
            if (delegate instanceof com.openggf.game.save.SaveSnapshotProvider && method.getName().equals("captureRuntimeFields"))
                return com.openggf.game.save.RuntimeSaveCapture.freezeFields(saveFields(value));
            boolean eventAdapters = delegate instanceof com.openggf.game.LevelEventRewindResolver
                    && method.getName().equals("resolveLevelEventRewindAdapters");
            if (adapters || eventAdapters) {
                if (!(value instanceof List<?> list)) throw new IllegalArgumentException("rewindAdapters must return a list");
                if (value == inherited) return value;
                Set<Object> nativeAdapters = Collections.newSetFromMap(new IdentityHashMap<>());
                if (inherited instanceof List<?> inheritedList) nativeAdapters.addAll(inheritedList);
                var result = new ArrayList<RewindSnapshottable<?>>(list.size());
                for (Object entry : list) {
                    if (!(entry instanceof RewindSnapshottable<?> adapter)) throw new IllegalArgumentException("Invalid rewind adapter");
                    result.add(nativeAdapters.contains(adapter) ? adapter
                            : (RewindSnapshottable<?>)callback(adapter, RewindSnapshottable.class, null));
                }
                return List.copyOf(result);
            }
            if (characterRegistryQuery(method, args) || value instanceof com.openggf.game.PlayableCharacterRegistry) {
                if (value == inherited && value != null) return value;
                synchronized (wrappers) {
                    Object cached = wrappers.get(value);
                    if (cached != null) return cached;
                    requireCacheRoom();
                    var registry = com.openggf.mods.code.OwnedCharacterRegistry.bind(owner,
                            (com.openggf.game.PlayableCharacterRegistry)value,
                            (com.openggf.game.PlayableCharacterRegistry)inherited,boundary);
                    wrappers.put(value,registry);
                    return registry;
                }
            }
            if (value == null || value == inherited) return value;
            Class<?> contract = queryInterface(method, args);
            if (contract == null) return value;
            return callback(value, contract, inherited);
        }
        private Object callback(Object value, Class<?> contract, Object inherited) {
            if (!callbackInterface(contract)) return value;
            if (value instanceof RewindSnapshottable<?>
                    && com.openggf.game.rewind.RewindAdapterOwnership.isBound(value)) return value;
            synchronized (wrappers) {
                Object cached = wrappers.get(value);
                if (cached != null) return cached;
                requireCacheRoom();
                var interfaces = new LinkedHashSet<Class<?>>(); interfaces.add(contract);
                // A controller can also be a rewind adapter. One stable proxy must preserve all its public engine contracts.
                collectInterfaces(value.getClass(), interfaces);
                if (value instanceof com.openggf.game.AbstractLevelEventManager)
                    interfaces.add(com.openggf.game.LevelEventRewindResolver.class);
                Handler handler = new Handler(owner, value, inherited, boundary, wrappers, false);
                ClassLoader loader = contract.getClassLoader() == null ? GameModule.class.getClassLoader() : contract.getClassLoader();
                Object wrapped = Proxy.newProxyInstance(loader, interfaces.toArray(Class<?>[]::new), handler);
                if (value instanceof RewindSnapshottable<?> adapter) {
                    handler.rewindKey = com.openggf.game.rewind.RewindAdapterOwnership.bindDelegate(wrapped, value, owner,
                            boundary.call(owner, adapter::key));
                }
                wrappers.put(value, wrapped); return wrapped;
            }
        }
        private void requireCacheRoom() {
            if (wrappers.size() >= com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
                throw new IllegalArgumentException("Too many owned callback and character registry identities");
        }
    }

    private static boolean characterRegistryQuery(Method method, Object[] args) {
        return method.getReturnType() == com.openggf.game.PlayableCharacterRegistry.class
                || method.getName().equals("getGameService") && args != null && args.length == 1
                    && args[0] == com.openggf.game.PlayableCharacterRegistry.class;
    }
    private record CharacterMetadata(Map<com.openggf.game.CharacterKey,com.openggf.game.CharacterDefinition> source,
            Map<com.openggf.game.CharacterKey,com.openggf.game.CharacterDefinition> bound) { }

    private static Object characterMetadata(String owner,Object value,ModFaultBoundary boundary,
            IdentityHashMap<Object,CharacterMetadata> cache) {
        int limit=com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES;
        if (!(value instanceof Map<?,?> definitions) || definitions.size()>limit)
            throw new IllegalArgumentException("Provided character metadata requires a bounded map");
        var snapshot=new LinkedHashMap<com.openggf.game.CharacterKey,com.openggf.game.CharacterDefinition>();
        for (var entry:definitions.entrySet()) {
            if (snapshot.size()>=limit) throw new IllegalArgumentException("Too many provided character definitions");
            if (!(entry.getKey() instanceof com.openggf.game.CharacterKey key)
                    || !(entry.getValue() instanceof com.openggf.game.CharacterDefinition definition))
                throw new IllegalArgumentException("Invalid provided character metadata");
            snapshot.put(key,definition);
        }
        synchronized (cache) {
            CharacterMetadata earlier=cache.get(value);
            if (earlier!=null && earlier.source().equals(snapshot)) return earlier.bound();
            if (earlier==null && cache.size()>=limit) throw new IllegalArgumentException("Too many provided character metadata identities");
            var registry=com.openggf.game.PlayableCharacterRegistry.empty();
            for (var entry:snapshot.entrySet()) registry=registry.register(entry.getKey(),entry.getValue());
            var mapped=com.openggf.mods.code.OwnedCharacterRegistry.bind(owner,registry,null,boundary).definitions();
            cache.put(value,new CharacterMetadata(Collections.unmodifiableMap(snapshot),mapped));
            return mapped;
        }
    }
    private static Class<?> queryInterface(Method method, Object[] args) {
        if (method.getName().equals("getGameService") && args != null && args.length == 1 && args[0] instanceof Class<?> requested)
            return callbackInterface(requested) ? requested : null;
        Class<?> type = method.getReturnType();
        // Factory methods may create native state. Do not invoke the base factory a second time just to compare its output.
        if (callbackInterface(type) && (type.getPackageName().equals("java.util.function")
                || Arrays.stream(type.getMethods()).filter(value -> Modifier.isAbstract(value.getModifiers())
                    && value.getDeclaringClass() != Object.class).map(value -> value.getName()
                    + Arrays.toString(value.getParameterTypes())).distinct().count() == 1)) return type;
        return method.getParameterCount() == 0 && !method.getName().startsWith("create") && callbackInterface(type) ? type : null;
    }
    private static boolean callbackInterface(Class<?> type) {
        return type.isInterface() && !type.isSealed() && Modifier.isPublic(type.getModifiers())
                && (type.getPackageName().startsWith("com.openggf")
                    || type.getPackageName().equals("java.util.function"));
    }
    private static void collectInterfaces(Class<?> type, Set<Class<?>> interfaces) {
        if (type == null) return;
        for (Class<?> contract : type.getInterfaces()) {
            if (callbackInterface(contract)) interfaces.add(contract);
            collectInterfaces(contract, interfaces);
        }
        collectInterfaces(type.getSuperclass(), interfaces);
    }
    private static Object objectMethod(Object proxy, Method method, Object[] args, String owner) {
        return switch (method.getName()) {
            case "toString" -> "OwnerBoundGamePatch[" + owner + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<String,Object> saveFields(Object value) {
        return (java.util.Map<String,Object>)value;
    }
    private static Object directInvoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static Object ownedInvoke(Object target, Method method, Object[] args) {
        try { return directInvoke(target, method, args); }
        catch (RuntimeException failure) { throw failure; }
        catch (Error failure) { throw failure; }
        catch (Throwable failure) { throw new CheckedCallbackFailure(failure); }
    }
    private static final class CheckedCallbackFailure extends RuntimeException {
        CheckedCallbackFailure(Throwable cause) { super(cause); }
    }
}
