package com.openggf.level.objects;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt with zero/false scalar placeholder constructor arguments.
 *
 * <p>Use this only when the concrete class has a constructor whose parameters
 * are all {@code int} or {@code boolean}, and every constructor value is
 * restored immediately by the generic scalar pass. When several such
 * constructors exist, the longest one is used unless another constructor has
 * the same arity. Objects needing spawn coordinates, services, or graph
 * relinking should use a more specific recreate hook.
 */
public interface ZeroScalarArgsRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            Constructor<?> constructor = findConstructor(objectClass);
            Object[] args = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = parameterTypes[i] == boolean.class ? Boolean.FALSE : 0;
            }
            constructor.setAccessible(true);
            return objectClass.cast(constructor.newInstance(args));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements ZeroScalarArgsRewindRecreatable "
                            + "but has no unique zero-scalar constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed zero-scalar rewind recreate",
                    e);
        }
    }

    private static Constructor<?> findConstructor(Class<? extends AbstractObjectInstance> objectClass)
            throws NoSuchMethodException {
        Constructor<?> best = null;
        for (Constructor<?> constructor : objectClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 0 || !allZeroScalars(parameterTypes)) {
                continue;
            }
            if (best == null || parameterTypes.length > best.getParameterCount()) {
                best = constructor;
            } else if (parameterTypes.length == best.getParameterCount()) {
                throw new NoSuchMethodException(
                        objectClass.getName() + " has multiple zero-scalar constructors");
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(objectClass.getName() + ".<init>(zero scalars)");
        }
        return best;
    }

    private static boolean allZeroScalars(Class<?>[] parameterTypes) {
        for (Class<?> parameterType : parameterTypes) {
            if (parameterType != int.class && parameterType != boolean.class) {
                return false;
            }
        }
        return true;
    }
}
