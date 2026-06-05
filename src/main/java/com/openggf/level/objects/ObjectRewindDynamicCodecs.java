package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Function;

/**
 * Shared dynamic-object rewind codec factories. Game-specific registries compose
 * these helpers with their concrete object classes.
 */
public final class ObjectRewindDynamicCodecs {
    private ObjectRewindDynamicCodecs() {
    }

    public static List<DynamicObjectRewindCodec> sharedCodecs() {
        return List.of(
                animalCodec(),
                new LostRingRewindCodec(),
                deferredPlayerBoundCodec(ShieldObjectInstance.class, ShieldObjectInstance.class),
                deferredPlayerBoundCodec(
                        InvincibilityStarsObjectInstance.class,
                        InvincibilityStarsObjectInstance.class),
                explosionCodec(),
                skidDustCodec());
    }

    public static DynamicObjectRewindCodec deferredPlayerBoundCodec(
            Class<? extends ObjectInstance> exactClass, Class<?> baseTypeKey) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == exactClass;
            }

            @Override
            public String className() {
                return exactClass.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                context.objectManager().enqueuePendingPlayerBoundEntry(baseTypeKey, entry);
                return null;
            }
        };
    }

    public static DynamicObjectRewindCodec pointsCodec(Class<? extends AbstractPointsObjectInstance> type) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    Constructor<? extends AbstractPointsObjectInstance> ctor =
                            type.getDeclaredConstructor(ObjectSpawn.class, ObjectServices.class, int.class);
                    return ctor.newInstance(entry.spawn(), context.objectServices(), 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + type.getName(), e);
                }
            }
        };
    }

    public static DynamicObjectRewindCodec exactSpawnCodec(
            Class<? extends AbstractObjectInstance> type,
            Function<ObjectSpawn, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return factory.apply(entry.spawn());
            }
        };
    }

    private static DynamicObjectRewindCodec animalCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof AnimalObjectInstance;
            }

            @Override
            public String className() {
                return AnimalObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return AnimalObjectInstance.forRewindRecreate(
                        entry.spawn(), context.objectServices());
            }
        };
    }

    private static DynamicObjectRewindCodec explosionCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof ExplosionObjectInstance;
            }

            @Override
            public String className() {
                return ExplosionObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                ObjectRenderManager renderManager = context.objectServices().renderManager();
                return new ExplosionObjectInstance(
                        spawn.objectId(), spawn.x(), spawn.y(), renderManager, -1);
            }
        };
    }

    private static DynamicObjectRewindCodec skidDustCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof SkidDustObjectInstance;
            }

            @Override
            public String className() {
                return SkidDustObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return SkidDustObjectInstance.forRewindRecreate(
                        entry.spawn(), context.objectServices());
            }
        };
    }
}
