package com.openggf.level.objects;

import java.lang.reflect.InvocationTargetException;

/**
 * Marker for rewind-recreatable dynamic objects whose restore instance can be
 * rebuilt from the captured {@link ObjectSpawn} and the active ROM zone id.
 *
 * <p>Use this only when the concrete class has an
 * {@code (ObjectSpawn, int romZoneId)} constructor and the integer specifically
 * selects zone-dependent construction data. Generic {@code (ObjectSpawn, int)}
 * constructors whose integer is velocity, subtype, offset, or another semantic
 * value should use a more specific recreate hook.
 */
public interface SpawnRomZoneRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Class<? extends AbstractObjectInstance> objectClass =
                getClass().asSubclass(AbstractObjectInstance.class);
        try {
            var constructor = objectClass.getDeclaredConstructor(ObjectSpawn.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ctx.spawn(), romZoneId(ctx));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " implements SpawnRomZoneRewindRecreatable "
                            + "but has no (ObjectSpawn, int) constructor",
                    e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    objectClass.getName() + " failed spawn-ROM-zone rewind recreate",
                    e);
        }
    }

    private static int romZoneId(RewindRecreateContext ctx) {
        ObjectServices objectServices = ctx.objectServices();
        return objectServices != null ? objectServices.romZoneId() : -1;
    }
}
