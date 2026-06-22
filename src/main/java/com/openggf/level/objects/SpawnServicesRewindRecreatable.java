package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt directly from the captured {@link ObjectSpawn} and restore-time
 * {@link ObjectServices}.
 *
 * <p>Use this only when the concrete class has an
 * {@code (ObjectSpawn, ObjectServices)} constructor and that constructor does
 * not consume runtime state such as RNG, allocate children, or perform custom
 * art lookups that need a purpose-built rewind factory. Graph-linked objects
 * should continue to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnServicesRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor(ObjectSpawn.class, ObjectServices.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ctx.spawn(), ctx.objectServices());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnServicesRewindRecreatable "
                            + "but has no (ObjectSpawn, ObjectServices) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-services rewind recreate", e);
        }
    }
}
