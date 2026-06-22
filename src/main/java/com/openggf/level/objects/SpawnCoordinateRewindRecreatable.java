package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt directly from the captured spawn coordinates.
 *
 * <p>Use this only when the concrete class has an {@code (int x, int y)}
 * constructor and needs no parent/sibling lookup or placeholder constructor
 * arguments during recreate. Graph-linked objects should continue to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnCoordinateRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor(int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ctx.spawn().x(), ctx.spawn().y());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnCoordinateRewindRecreatable "
                            + "but has no (int, int) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-coordinate rewind recreate", e);
        }
    }
}
