package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt directly from the captured {@link ObjectSpawn}.
 *
 * <p>Use this only when the concrete class has an {@code ObjectSpawn}
 * constructor and needs no parent/sibling lookup or placeholder constructor
 * arguments during recreate. Graph-linked objects should continue to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor(ObjectSpawn.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ctx.spawn());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnRewindRecreatable "
                            + "but has no ObjectSpawn constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-based rewind recreate", e);
        }
    }
}
