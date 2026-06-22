package com.openggf.level.objects;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt from the captured {@link ObjectSpawn} plus zero placeholder ints.
 *
 * <p>Use this only when the concrete class has an {@code ObjectSpawn}
 * constructor followed only by {@code int} parameters, and every trailing
 * constructor value is scalar-restored immediately after recreate. When several
 * such constructors exist, the longest one is used. Graph-linked objects should
 * continue to implement
 * {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)} directly.
 */
public interface SpawnTrailingZeroIntsRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            Constructor<?> constructor = findConstructor(objectClass);
            Object[] args = new Object[constructor.getParameterCount()];
            args[0] = ctx.spawn();
            for (int i = 1; i < args.length; i++) {
                args[i] = 0;
            }
            constructor.setAccessible(true);
            return objectClass.cast(constructor.newInstance(args));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnTrailingZeroIntsRewindRecreatable "
                            + "but has no (ObjectSpawn, int...) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn trailing-zero-ints rewind recreate",
                    e);
        }
    }

    private static Constructor<?> findConstructor(Class<? extends AbstractObjectInstance> objectClass)
            throws NoSuchMethodException {
        Constructor<?> best = null;
        for (Constructor<?> constructor : objectClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < 2 || parameterTypes[0] != ObjectSpawn.class) {
                continue;
            }
            boolean allTrailingInts = true;
            for (int i = 1; i < parameterTypes.length; i++) {
                if (parameterTypes[i] != int.class) {
                    allTrailingInts = false;
                    break;
                }
            }
            if (allTrailingInts && (best == null
                    || parameterTypes.length > best.getParameterCount())) {
                best = constructor;
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(
                    objectClass.getName() + ".<init>(ObjectSpawn, int...)");
        }
        return best;
    }
}
