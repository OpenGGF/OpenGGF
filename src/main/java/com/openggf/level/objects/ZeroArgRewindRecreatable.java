package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt with a no-arg constructor.
 *
 * <p>Use this only when constructor defaults are placeholders and the standard
 * scalar-restore pass reapplies the captured state immediately after recreate.
 * Objects needing spawn coordinates, services, or graph relinking should use a
 * more specific recreate hook.
 */
public interface ZeroArgRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements ZeroArgRewindRecreatable "
                            + "but has no no-arg constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed zero-arg rewind recreate", e);
        }
    }
}
