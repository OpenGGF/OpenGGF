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

    /** Verified creator content origin, independent of a policy producer's callback scope. */
    public static String registeredContentOwner(ObjectManager manager, ObjectInstance source) {
        return manager == null ? null : manager.registeredContentOwner(source);
    }

    /** Engine-only fresh factory derivative; it never selects a constructor by a creator class name. */
    public static AbstractObjectInstance createRegisteredDerivative(ObjectManager manager, ObjectInstance source, int x, int y) {
        return manager == null ? null : manager.createRegisteredDerivative(source, x, y);
    }

    public static void inheritOwners(ObjectManager target, ObjectManager source,
            Iterable<? extends ObjectInstance> instances) {
        target.inheritObjectCallbackOwners(source, instances);
    }
}
