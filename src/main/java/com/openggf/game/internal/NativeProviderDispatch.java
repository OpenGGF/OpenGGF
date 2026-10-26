package com.openggf.game.internal;

import com.openggf.util.EngineCallerAccess;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * Game-owned provenance for a provider projected by an engine callback host.
 * The final envelope forwards the original host dispatch exactly once. Its
 * immutable source is inspected without invoking provider getters or callbacks.
 */
public final class NativeProviderDispatch {
    private NativeProviderDispatch() { }

    /** Called at host composition, with that host's actual final source/handler. */
    public static InvocationHandler authenticate(InvocationHandler dispatch, Object source) {
        Class<?> caller = requireEngineCaller();
        Objects.requireNonNull(dispatch, "dispatch");
        Objects.requireNonNull(source, "source");
        if (dispatch.getClass().getClassLoader() != NativeProviderDispatch.class.getClassLoader()
                || dispatch.getClass().getNestHost() != caller.getNestHost())
            throw new SecurityException("Provider projection belongs to its actual engine callback host");
        return new PublishedHandler(dispatch, source);
    }

    /** Metadata-only proof; callers still invoke the selected provider/proxy. */
    public static boolean matchesNative(Object provider, Class<?> nativeType,
            String methodName, Class<?>... parameters) {
        requireEngineCaller();
        var visited = new IdentityHashMap<Object, Boolean>();
        for (int depth = 0; provider != null && depth < 32; depth++) {
            if (visited.put(provider, Boolean.TRUE) != null) return false;
            if (!Proxy.isProxyClass(provider.getClass())) {
                if (provider.getClass() != nativeType
                        || nativeType.getClassLoader() != NativeProviderDispatch.class.getClassLoader()) return false;
                try {
                    return nativeType.getMethod(methodName, parameters).getDeclaringClass() == nativeType;
                } catch (NoSuchMethodException missing) {
                    return false;
                }
            }
            InvocationHandler handler = Proxy.getInvocationHandler(provider);
            if (handler.getClass() != PublishedHandler.class) return false;
            provider = ((PublishedHandler) handler).source;
        }
        return false;
    }

    private static Class<?> requireEngineCaller() {
        Class<?> caller = EngineCallerAccess.callerOutside(NativeProviderDispatch.class);
        if (caller.getClassLoader() != NativeProviderDispatch.class.getClassLoader())
            throw new SecurityException("Native provider projection belongs to engine composition");
        return caller;
    }

    private static final class PublishedHandler implements InvocationHandler {
        private final InvocationHandler dispatch;
        private final Object source;

        private PublishedHandler(InvocationHandler dispatch, Object source) {
            this.dispatch = dispatch;
            this.source = source;
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return dispatch.invoke(proxy, method, args);
        }
    }
}
