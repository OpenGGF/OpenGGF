package com.openggf.level.objects;

import com.openggf.graphics.RenderPriority;
import com.openggf.game.rewind.schema.RewindCaptureContext;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.function.Supplier;
import java.util.function.Function;

/** Session-bound router for creator-owned object callbacks. */
final class ObjectCallbackRouter {
    static {
        if (RenderPriority.MAX - RenderPriority.MIN >= 8) {
            throw new AssertionError("renderBucketKey bucket bits overflow");
        }
    }

    private final ObjectCallbackBoundary boundary;
    private final Supplier<String> scopedOwner;
    private final Function<ObjectInstance, String> createdOwner;
    private final IdentityHashMap<ObjectInstance, String> owners = new IdentityHashMap<>();
    private String currentOwner;
    private ObjectInstance[] bucketSnapshotInstances = new ObjectInstance[64];
    private long[] bucketSnapshotKeys = new long[64];
    private int bucketSnapshotCount;

    ObjectCallbackRouter(ObjectRegistry registry) {
        Object candidate = registry instanceof Supplier<?> supplier ? supplier.get() : null;
        if (candidate instanceof java.util.Map.Entry<?, ?> entry
                && entry.getKey() instanceof ObjectCallbackBoundary callbackBoundary
                && entry.getValue() instanceof Supplier<?> ownerSupplier) {
            boundary = callbackBoundary;
            scopedOwner = () -> (String) ownerSupplier.get();
            createdOwner = entry.getValue() instanceof Function<?, ?> ownerFunction
                    ? instance -> {
                        @SuppressWarnings("unchecked")
                        Function<ObjectInstance, String> lookup =
                                (Function<ObjectInstance, String>) ownerFunction;
                        return lookup.apply(instance);
                    }
                    : instance -> null;
        } else {
            boundary = candidate instanceof ObjectCallbackBoundary callbackBoundary
                    ? callbackBoundary : null;
            scopedOwner = () -> null;
            createdOwner = instance -> null;
        }
    }

    <T> T call(ObjectInstance instance, Supplier<T> callback) {
        String owner = owners.get(instance);
        if (owner == null || boundary == null) return callback.get();
        String previous = currentOwner;
        currentOwner = owner;
        try { return boundary.call(owner, callback); }
        finally { currentOwner = previous; }
    }

    void run(ObjectInstance instance, Runnable callback) {
        call(instance, () -> { callback.run(); return null; });
    }

    void register(ObjectInstance instance, ObjectSpawn spawn) {
        Objects.requireNonNull(instance, "instance");
        createdOwner.apply(instance);
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
                    ? factory.get() : boundary.call(owner, factory);
            if (instance != null) {
                createdOwner.apply(instance);
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
        String rememberedOwner = createdOwner.apply(instance);
        String owner = currentOwner != null ? currentOwner
                : rememberedOwner != null ? rememberedOwner : scopedOwner.get();
        if (owner != null) owners.put(instance, owner);
    }

    void runAndUnregister(ObjectInstance instance, Runnable callback) {
        try { run(instance, callback); }
        finally { owners.remove(instance); }
    }

    void unregister(ObjectInstance instance) { owners.remove(instance); }

    String owner(ObjectInstance instance) { return owners.get(instance); }

    void registerOwnerIfAbsent(ObjectInstance instance, String owner) {
        if (owner != null) owners.putIfAbsent(instance, owner);
    }

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

    void populateRenderBuckets(Collection<? extends ObjectInstance> active,
            Collection<? extends ObjectInstance> dynamic,
            List<ObjectInstance>[] lowPriorityBuckets,
            List<ObjectInstance>[] highPriorityBuckets) {
        for (int i = 0; i < lowPriorityBuckets.length; i++) {
            lowPriorityBuckets[i].clear();
            highPriorityBuckets[i].clear();
        }
        addToRenderBuckets(active, lowPriorityBuckets, highPriorityBuckets);
        addToRenderBuckets(dynamic, lowPriorityBuckets, highPriorityBuckets);

        // ROM parity: lower SST slots render later and therefore in front.
        for (int i = 0; i < lowPriorityBuckets.length; i++) {
            lowPriorityBuckets[i].sort(this::compareRenderSlotsDescending);
            highPriorityBuckets[i].sort(this::compareRenderSlotsDescending);
        }
        captureRenderBucketSnapshot(active, dynamic);
    }

    boolean renderBucketInputsChanged(Collection<? extends ObjectInstance> active,
            Collection<? extends ObjectInstance> dynamic) {
        if (active.size() + dynamic.size() != bucketSnapshotCount) return true;
        int position = 0;
        for (ObjectInstance instance : active) {
            if (renderBucketInputChanged(position++, instance)) return true;
        }
        for (ObjectInstance instance : dynamic) {
            if (renderBucketInputChanged(position++, instance)) return true;
        }
        return position != bucketSnapshotCount;
    }

    private boolean renderBucketInputChanged(int position, ObjectInstance instance) {
        return position >= bucketSnapshotCount
                || bucketSnapshotInstances[position] != instance
                || bucketSnapshotKeys[position] != renderBucketKey(instance);
    }

    private void captureRenderBucketSnapshot(Collection<? extends ObjectInstance> active,
            Collection<? extends ObjectInstance> dynamic) {
        int required = active.size() + dynamic.size();
        if (bucketSnapshotInstances.length < required) {
            int newLength = Math.max(required, bucketSnapshotInstances.length * 2);
            bucketSnapshotInstances = new ObjectInstance[newLength];
            bucketSnapshotKeys = new long[newLength];
        }
        int position = captureRenderBucketSnapshot(active, 0);
        position = captureRenderBucketSnapshot(dynamic, position);
        for (int i = position; i < bucketSnapshotCount; i++) bucketSnapshotInstances[i] = null;
        bucketSnapshotCount = position;
    }

    private int captureRenderBucketSnapshot(Collection<? extends ObjectInstance> instances,
            int position) {
        for (ObjectInstance instance : instances) {
            bucketSnapshotInstances[position] = instance;
            bucketSnapshotKeys[position] = renderBucketKey(instance);
            position++;
        }
        return position;
    }

    private void addToRenderBuckets(Collection<? extends ObjectInstance> instances,
            List<ObjectInstance>[] lowPriorityBuckets,
            List<ObjectInstance>[] highPriorityBuckets) {
        for (ObjectInstance instance : instances) {
            int index = RenderPriority.clamp(call(instance, instance::getPriorityBucket))
                    - RenderPriority.MIN;
            (call(instance, instance::isHighPriority)
                    ? highPriorityBuckets[index] : lowPriorityBuckets[index]).add(instance);
        }
    }

    private int compareRenderSlotsDescending(ObjectInstance first, ObjectInstance second) {
        int firstSlot = first instanceof AbstractObjectInstance object
                ? call(first, object::getSlotIndex) : Integer.MAX_VALUE;
        int secondSlot = second instanceof AbstractObjectInstance object
                ? call(second, object::getSlotIndex) : Integer.MAX_VALUE;
        return Integer.compare(secondSlot, firstSlot);
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
