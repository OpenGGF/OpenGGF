package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt from spawn coordinates plus two zero placeholder constructor values.
 *
 * <p>Use this only when the concrete class has an {@code (int x, int y, int, int)}
 * constructor and the two trailing values are scalar-restored immediately after
 * recreate. Graph-linked objects should continue to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnCoordinateZeroPairRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor(
                    int.class, int.class, int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ctx.spawn().x(), ctx.spawn().y(), 0, 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnCoordinateZeroPairRewindRecreatable "
                            + "but has no (int, int, int, int) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-coordinate zero-pair rewind recreate",
                    e);
        }
    }
}
