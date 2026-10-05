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
        String trustedOwner = ModKeySyntax.requireManifestId(owner);
        Objects.requireNonNull(patch, "patch");
        if (boundary == null) return patch; // Registration-only callers may intentionally have no runtime callback boundary.
        return (GamePatch)Proxy.newProxyInstance(GamePatch.class.getClassLoader(), new Class<?>[]{GamePatch.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, trustedOwner);
                    return boundary.call(trustedOwner, () -> {
                        Object result = ownedInvoke(patch, method, args);
                        if (method.getName().equals("apply")) {
                            GameModule base = (GameModule)args[0];
                            GameModule module = Objects.requireNonNull((GameModule)result, "Patch apply returned null");
                            return module == base ? base : module(trustedOwner, module, base, boundary, false);
                        }
                        return result;
                    });
                });
    }

    /** Existing standalone wrappers already guard providers; this adds the missing adapter-list element boundary. */
    public static GameModule wrapStandaloneRewinds(String owner, GameModule delegate, ModFaultBoundary boundary) {
        return module(ModKeySyntax.requireManifestId(owner), Objects.requireNonNull(delegate, "delegate"), null,
                Objects.requireNonNull(boundary, "boundary"), true);
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

        Handler(String owner, Object delegate, Object base, ModFaultBoundary boundary,
                IdentityHashMap<Object, Object> wrappers, boolean rewindOnly) {
            this.owner = owner; this.delegate = delegate; this.base = base;
            this.boundary = boundary; this.wrappers = wrappers; this.rewindOnly = rewindOnly;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args, owner);
            boolean adapters = delegate instanceof GameModule && method.getName().equals("rewindAdapters");
            if (rewindOnly && !adapters || !rewindOnly && inheritedModuleMethod(method)) return directInvoke(delegate, method, args);
            // Getter identity is compared outside this owner's boundary: a native or earlier owner's failure stays theirs.
            Object inherited = base != null && (adapters || queryInterface(method, args) != null)
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
            if (adapters) {
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
            if (value == null || value == inherited) return value;
            Class<?> contract = queryInterface(method, args);
            if (contract == null) return value;
            return callback(value, contract, inherited);
        }
        private Object callback(Object value, Class<?> contract, Object inherited) {
            if (!callbackInterface(contract)) return value;
            synchronized (wrappers) {
                Object cached = wrappers.get(value);
                if (cached != null) return cached;
                var interfaces = new LinkedHashSet<Class<?>>(); interfaces.add(contract);
                // A controller can also be a rewind adapter. One stable proxy must preserve all its public engine contracts.
                collectInterfaces(value.getClass(), interfaces);
                Object wrapped = Proxy.newProxyInstance(contract.getClassLoader(), interfaces.toArray(Class<?>[]::new),
                        new Handler(owner, value, inherited, boundary, wrappers, false));
                wrappers.put(value, wrapped); return wrapped;
            }
        }
    }

    private static Class<?> queryInterface(Method method, Object[] args) {
        if (method.getName().equals("getGameService") && args != null && args.length == 1 && args[0] instanceof Class<?> requested)
            return callbackInterface(requested) ? requested : null;
        Class<?> type = method.getReturnType();
        // Factory methods may create native state. Do not invoke the base factory a second time just to compare its output.
        return method.getParameterCount() == 0 && !method.getName().startsWith("create") && callbackInterface(type) ? type : null;
    }
    private static boolean callbackInterface(Class<?> type) {
        return type.isInterface() && !type.isSealed() && Modifier.isPublic(type.getModifiers())
                && type.getPackageName().startsWith("com.openggf");
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
