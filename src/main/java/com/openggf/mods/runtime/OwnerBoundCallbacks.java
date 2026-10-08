package com.openggf.mods.runtime;

import com.openggf.game.ModKeySyntax;
import com.openggf.game.rewind.RewindAdapterOwnership;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.code.ModFaultBoundary;
import java.lang.reflect.*;
import java.util.*;

/** One bounded identity graph for a successfully created owner contribution lifetime. */
public final class OwnerBoundCallbacks {
    private final String owner;
    private final ModFaultBoundary boundary;
    private final int limit;
    private final IdentityHashMap<Object,String> adapterKeys = new IdentityHashMap<>();
    private final IdentityHashMap<Object,Object> wrappers = new IdentityHashMap<>();

    public OwnerBoundCallbacks(String owner, ModFaultBoundary boundary, int limit,
            Map<String,? extends RewindSnapshottable<?>> adapters) {
        Class<?> caller = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                StackWalker.Option.SHOW_HIDDEN_FRAMES)).walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != OwnerBoundCallbacks.class && type.getClassLoader() != null)
                .findFirst().orElseThrow());
        if (caller.getClassLoader() != OwnerBoundCallbacks.class.getClassLoader())
            throw new SecurityException("Creator callback authority belongs to its engine registration");
        this.owner = ModKeySyntax.requireManifestId(owner);
        this.boundary = Objects.requireNonNull(boundary);
        if (limit < 1) throw new IllegalArgumentException("Callback limit must be positive");
        this.limit = limit;
        if (adapters.size() > limit) throw new IllegalArgumentException("Too many contributed rewind adapters");
        adapters.forEach((key,value) -> {
            Objects.requireNonNull(value);
            if (RewindAdapterOwnership.isOwned(value) && !RewindAdapterOwnership.hasIdentity(value, owner, key))
                throw new IllegalArgumentException("A declared rewind adapter must retain this verified owner and local identity");
            String old = adapterKeys.put(value,Objects.requireNonNull(key));
            if (old != null && !old.equals(key))
                throw new IllegalArgumentException("One shared adapter must have one local identity");
        });
    }

    public <T> T bind(Class<T> contract, T value) {
        if (value == null) return null;
        return boundary.call(owner, () -> {
            if (value instanceof RewindSnapshottable<?> && RewindAdapterOwnership.isOwned(value)
                    && !RewindAdapterOwnership.isBoundToOwner(value, owner))
                throw new IllegalArgumentException("A new service or runtime contribution cannot publish another owner's adapter");
            return publicInterface(contract) ? contract.cast(wrap(contract,value)) : value;
        });
    }
    public RewindSnapshottable<?> adapter(RewindSnapshottable<?> value) {
        return bind(RewindSnapshottable.class, value);
    }
    public <T> java.util.function.Supplier<T> dynamic(Class<T> contract,
            java.util.function.Supplier<? extends T> provider) {
        Objects.requireNonNull(provider);
        return () -> boundary.call(owner, () -> bind(contract,contract.cast(provider.get())));
    }
    private Object wrap(Class<?> contract, Object value) {
        if (value instanceof RewindSnapshottable<?> && RewindAdapterOwnership.isBound(value)) return value;
        synchronized (wrappers) {
            Object existing = wrappers.get(value);
            if (existing != null) return existing;
            if (wrappers.size() >= limit) throw new IllegalArgumentException("Owner callback identity limit exceeded");
            LinkedHashSet<Class<?>> interfaces = new LinkedHashSet<>();
            interfaces.add(contract);
            collect(value.getClass(),interfaces);
            String rewindKey = null;
            if (value instanceof RewindSnapshottable<?> adapter) {
                String declared = adapterKeys.get(value);
                rewindKey = declared == null ? adapter.key() : declared;
            }
            String local = rewindKey;
            final String[] canonical = {null};
            InvocationHandler handler = (proxy,method,args) -> {
                if (method.getDeclaringClass() == Object.class) return switch(method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "OwnedCallback["+owner+"]";
                    default -> throw new UnsupportedOperationException(method.toString());
                };
                if (canonical[0] != null && method.getName().equals("key") && method.getParameterCount() == 0)
                    return canonical[0];
                return boundary.call(owner, () -> {
                    Object result = OwnerBoundInitialization.returned(owner,boundary,limit,value,method,invoke(value,method,args));
                    if (value instanceof RewindSnapshottable<?> && method.getName().equals("capture")
                            && method.getParameterCount() == 0)
                        result = Objects.requireNonNull(result,"Owned rewind snapshot");
                    if (value instanceof com.openggf.game.zone.ZoneRuntimeState && method.getName().equals("captureBytes"))
                        result = Objects.requireNonNull(result,"Owned zone runtime snapshot");
                    if (value instanceof com.openggf.game.render.SpecialRenderEffect && method.getName().equals("stage"))
                        result = Objects.requireNonNull(result,"Owned render effect stage");
                    if (value instanceof com.openggf.game.save.SaveSnapshotProvider && method.getName().equals("captureRuntimeFields"))
                        return com.openggf.game.save.RuntimeSaveCapture.freezeFields(saveFields(result));
                    Class<?> returned = method.getReturnType();
                    return result != null && publicInterface(returned)
                            && (method.getParameterCount() == 0 || functionalInterface(returned))
                            ? wrap(returned,result) : result;
                });
            };
            ClassLoader loader = value.getClass().getClassLoader();
            if (loader == null) loader = OwnerBoundCallbacks.class.getClassLoader();
            Object proxy = Proxy.newProxyInstance(loader,interfaces.toArray(Class<?>[]::new),handler);
            if (local != null) canonical[0] = RewindAdapterOwnership.bindDelegate(proxy,value,owner,local);
            wrappers.put(value,proxy);
            return proxy;
        }
    }
    private static boolean publicInterface(Class<?> type) {
        return type.isInterface() && Modifier.isPublic(type.getModifiers()) && !type.isSealed()
                && (type.getPackageName().startsWith("com.openggf")
                    || type.getPackageName().equals("java.util.function")
                    || type.getClassLoader() != null && type.getClassLoader() != OwnerBoundCallbacks.class.getClassLoader());
    }
    private static boolean functionalInterface(Class<?> type) {
        return Arrays.stream(type.getMethods()).filter(method -> Modifier.isAbstract(method.getModifiers())
                && method.getDeclaringClass() != Object.class).map(method -> method.getName()
                + Arrays.toString(method.getParameterTypes())).distinct().count() == 1;
    }
    private static void collect(Class<?> type, Set<Class<?>> interfaces) {
        if (type == null) return;
        for (Class<?> contract : type.getInterfaces()) {
            if (publicInterface(contract)) interfaces.add(contract);
            collect(contract,interfaces);
        }
        collect(type.getSuperclass(),interfaces);
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> saveFields(Object value) { return (Map<String,Object>)value; }
    private static Object invoke(Object value, Method method, Object[] args) {
        try { return method.invoke(value,args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Checked creator callback failure",cause);
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
}
