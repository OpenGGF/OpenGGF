package com.openggf.level.objects;

import java.util.function.Supplier;

/** Internal cross-package bridge for engine-owned object callback dispatch. */
public final class ObjectCallbackDispatch {
    private ObjectCallbackDispatch() { }

    public static void run(ObjectManager manager, ObjectInstance instance, Runnable callback) {
        manager.runObjectCallback(instance, callback);
    }

    public static <T> T call(ObjectManager manager, ObjectInstance instance, Supplier<T> callback) {
        return manager == null ? callback.get() : manager.callObjectCallback(instance, callback);
    }

    public static ObjectSpawn managedSpawn(ObjectManager manager, ObjectInstance instance) {
        return manager == null ? null : manager.managedSpawn(instance);
    }

    public static void inheritOwners(ObjectManager target, ObjectManager source,
            Iterable<? extends ObjectInstance> instances) {
        target.inheritObjectCallbackOwners(source, instances);
    }
}
