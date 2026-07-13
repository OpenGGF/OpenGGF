package com.openggf.level.objects;

import com.openggf.graphics.RenderPriority;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.game.rewind.schema.RewindCaptureContext;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.function.Supplier;

/** Session-bound router for creator-owned object callbacks. */
final class ObjectCallbackRouter {
    private final ModFaultBoundary boundary;
    private final Supplier<String> scopedOwner;
    private final IdentityHashMap<ObjectInstance, String> owners = new IdentityHashMap<>();
    private String currentOwner;

    ObjectCallbackRouter(ObjectRegistry registry) {
        Object candidate = registry instanceof Supplier<?> supplier ? supplier.get() : null;
        if (candidate instanceof java.util.Map.Entry<?, ?> entry
                && entry.getKey() instanceof ModFaultBoundary modBoundary
                && entry.getValue() instanceof Supplier<?> ownerSupplier) {
            boundary = modBoundary;
            scopedOwner = () -> (String) ownerSupplier.get();
        } else {
            boundary = candidate instanceof ModFaultBoundary modBoundary ? modBoundary : null;
            scopedOwner = () -> null;
        }
    }

    <T> T call(ObjectInstance instance, Supplier<T> callback) {
        String owner = owners.get(instance);
        if (owner == null || boundary == null) return callback.get();
        String previous = currentOwner;
        currentOwner = owner;
        try { return boundary.callStandalone(owner, callback); }
        finally { currentOwner = previous; }
    }

    void run(ObjectInstance instance, Runnable callback) {
        call(instance, () -> { callback.run(); return null; });
    }

    void register(ObjectInstance instance, ObjectSpawn spawn) {
        Objects.requireNonNull(instance, "instance");
        String owner = Objects.requireNonNull(spawn, "spawn").ownerModId();
        if (owner == null) owners.remove(instance);
        else owners.put(instance, owner);
    }

    <T extends ObjectInstance> T construct(ObjectSpawn spawn, Supplier<T> factory) {
        return construct(Objects.requireNonNull(spawn, "spawn").ownerModId(), factory);
    }

    <T extends ObjectInstance> T construct(String owner, Supplier<T> factory) {
        String previous = currentOwner;
        currentOwner = owner;
        try {
            T instance = owner == null || boundary == null
                    ? factory.get() : boundary.callStandalone(owner, factory);
            if (instance != null) {
                if (owner == null) owners.remove(instance);
                else owners.put(instance, owner);
            }
            return instance;
        } finally {
            currentOwner = previous;
        }
    }

    void registerInherited(ObjectInstance instance) {
        Objects.requireNonNull(instance, "instance");
        String owner = currentOwner != null ? currentOwner : scopedOwner.get();
        if (owner != null) owners.put(instance, owner);
    }

    void runAndUnregister(ObjectInstance instance, Runnable callback) {
        try { run(instance, callback); }
        finally { owners.remove(instance); }
    }

    void unregister(ObjectInstance instance) { owners.remove(instance); }

    String owner(ObjectInstance instance) { return owners.get(instance); }

    void unregisterAll(Iterable<? extends ObjectInstance> instances) {
        for (ObjectInstance instance : instances) owners.remove(instance);
    }

    void inheritOwners(ObjectCallbackRouter source, Iterable<? extends ObjectInstance> instances) {
        for (ObjectInstance instance : instances) {
            String owner = source.owner(instance);
            if (owner != null) owners.put(instance, owner);
        }
    }

    void clear() {
        owners.clear();
        currentOwner = null;
    }

    long renderBucketKey(ObjectInstance instance) {
        long slot = instance instanceof AbstractObjectInstance object
                ? call(instance, object::getSlotIndex) : Integer.MAX_VALUE;
        int bucket = RenderPriority.clamp(call(instance, instance::getPriorityBucket))
                - RenderPriority.MIN;
        return (slot << 8) | (long) (bucket << 1)
                | (call(instance, instance::isHighPriority) ? 1L : 0L);
    }

    PerObjectRewindSnapshot captureRewind(AbstractObjectInstance object, RewindCaptureContext context) {
        return hasLegacyRewindOverride(object.getClass(), "captureRewindState")
                ? call(object, object::captureRewindState)
                : call(object, () -> object.captureRewindState(context));
    }

    void restoreRewind(AbstractObjectInstance object, PerObjectRewindSnapshot snapshot,
            RewindCaptureContext context) {
        if (hasLegacyRewindOverride(object.getClass(), "restoreRewindState",
                PerObjectRewindSnapshot.class)) run(object, () -> object.restoreRewindState(snapshot));
        else run(object, () -> object.restoreRewindState(snapshot, context));
    }

    private static boolean hasLegacyRewindOverride(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null && current != AbstractObjectInstance.class;
                current = current.getSuperclass()) {
            if (current == AbstractBadnikInstance.class) continue;
            try {
                var method = current.getDeclaredMethod(name, parameters);
                if (!Modifier.isAbstract(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) return true;
            } catch (NoSuchMethodException ignored) { }
        }
        return false;
    }

}
