package com.openggf.level.objects;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt from the captured {@link ObjectSpawn} plus one nullable reference.
 *
 * <p>Use this only when the concrete class has exactly one
 * {@code (ObjectSpawn, reference)} constructor and the second constructor value
 * is deliberately nullable during recreate. The generic scalar-restore pass
 * must restore any captured state immediately after construction. Parent- or
 * sibling-linked objects that must resolve live graph references should continue
 * to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnNullableReferenceRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            Constructor<?> constructor = findConstructor(objectClass);
            constructor.setAccessible(true);
            return objectClass.cast(constructor.newInstance(ctx.spawn(), null));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnNullableReferenceRewindRecreatable "
                            + "but has no unique (ObjectSpawn, reference) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn nullable-reference rewind recreate",
                    e);
        }
    }

    private static Constructor<?> findConstructor(Class<? extends AbstractObjectInstance> objectClass)
            throws NoSuchMethodException {
        Constructor<?> match = null;
        for (Constructor<?> constructor : objectClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 2
                    && parameterTypes[0] == ObjectSpawn.class
                    && !parameterTypes[1].isPrimitive()) {
                if (match != null) {
                    throw new NoSuchMethodException(
                            objectClass.getName()
                                    + " has multiple (ObjectSpawn, reference) constructors");
                }
                match = constructor;
            }
        }
        if (match == null) {
            throw new NoSuchMethodException(
                    objectClass.getName() + ".<init>(ObjectSpawn, reference)");
        }
        return match;
    }
}
