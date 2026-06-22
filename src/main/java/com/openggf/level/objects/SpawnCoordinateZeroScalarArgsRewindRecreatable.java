package com.openggf.level.objects;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt from captured spawn coordinates plus zero/false scalar placeholders.
 *
 * <p>Use this only when the concrete class has a constructor beginning with
 * {@code (int x, int y)} followed only by {@code int} or {@code boolean}
 * parameters, and every trailing constructor value is restored immediately by
 * the generic scalar pass. When several such constructors exist, the longest
 * one is used unless another constructor has the same arity.
 */
public interface SpawnCoordinateZeroScalarArgsRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            Constructor<?> constructor = findConstructor(objectClass);
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            args[0] = ctx.spawn().x();
            args[1] = ctx.spawn().y();
            for (int i = 2; i < parameterTypes.length; i++) {
                args[i] = parameterTypes[i] == boolean.class ? Boolean.FALSE : 0;
            }
            constructor.setAccessible(true);
            return objectClass.cast(constructor.newInstance(args));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnCoordinateZeroScalarArgsRewindRecreatable "
                            + "but has no unique (int, int, zero scalars...) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-coordinate zero-scalar rewind recreate",
                    e);
        }
    }

    private static Constructor<?> findConstructor(Class<? extends AbstractObjectInstance> objectClass)
            throws NoSuchMethodException {
        Constructor<?> best = null;
        for (Constructor<?> constructor : objectClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < 3
                    || parameterTypes[0] != int.class
                    || parameterTypes[1] != int.class
                    || !allTrailingZeroScalars(parameterTypes)) {
                continue;
            }
            if (best == null || parameterTypes.length > best.getParameterCount()) {
                best = constructor;
            } else if (parameterTypes.length == best.getParameterCount()) {
                throw new NoSuchMethodException(
                        objectClass.getName()
                                + " has multiple spawn-coordinate zero-scalar constructors");
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(
                    objectClass.getName() + ".<init>(int, int, zero scalars...)");
        }
        return best;
    }

    private static boolean allTrailingZeroScalars(Class<?>[] parameterTypes) {
        for (int i = 2; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != int.class && parameterTypes[i] != boolean.class) {
                return false;
            }
        }
        return true;
    }
}
