package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Shared dynamic-object rewind codec factories. Game-specific registries compose
 * these helpers with their concrete object classes.
 *
 * <p>The static {@link #genericRecreate(ObjectManagerSnapshot.DynamicObjectEntry, DynamicObjectRecreateContext)}
 * method is the Phase-2 uniform recreate entry point (Task 4). It supersedes per-object codecs
 * for classes that either:
 * <ul>
 *   <li>Can be reconstructed from the captured {@link ObjectSpawn} via
 *       {@link ObjectRegistry#create(ObjectSpawn)} (registry path), or</li>
 *   <li>Implement {@link RewindRecreatable} and supply their own creation hook
 *       ({@link RewindRecreatable} path).</li>
 * </ul>
 * Construction-spawned children continue to use the adoption keystone; they are never
 * passed to {@code genericRecreate}.
 */
public final class ObjectRewindDynamicCodecs {
    private static final Logger LOG = Logger.getLogger(ObjectRewindDynamicCodecs.class.getName());

    private ObjectRewindDynamicCodecs() {
    }

    /**
     * Generic recreate entry point for Phase-2 rewind (Task 4).
     *
     * <p>Decision tree:
     * <ol>
     *   <li>Load the captured class name.</li>
     *   <li>If the class implements {@link RewindRecreatable}: construct a minimal probe
     *       instance via {@link ObjectConstructionContext} (trying spawn-arg then zero-arg
     *       constructors) and delegate to
     *       {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)}.</li>
     *   <li>Else: rebuild via {@link ObjectRegistry#create(ObjectSpawn)} using the registry
     *       in {@link DynamicObjectRecreateContext#objectRegistry()}.</li>
     * </ol>
     *
     * <p><strong>Adoption safety:</strong> this method is called only AFTER
     * {@code ObjectManager.adoptRewindReconstructionChild} returns {@code null}, so it will
     * never be invoked for a class that the adoption keystone already handled. No additional
     * deduplication is needed here.
     *
     * @param entry the captured dynamic-object entry from the snapshot
     * @param ctx   restore-time context with services and registry
     * @return the recreated instance, or {@code null} if recreation is not possible
     */
    public static ObjectInstance genericRecreate(
            ObjectManagerSnapshot.DynamicObjectEntry entry,
            DynamicObjectRecreateContext ctx) {
        String className = entry.className();
        if (className == null) {
            return null;
        }
        Class<?> rawClass;
        try {
            rawClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOG.fine("genericRecreate: class not found: " + className);
            return null;
        }
        if (!AbstractObjectInstance.class.isAssignableFrom(rawClass)) {
            LOG.fine("genericRecreate: not an AbstractObjectInstance subclass: " + className);
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<? extends AbstractObjectInstance> cls =
                (Class<? extends AbstractObjectInstance>) rawClass;

        // Path 1: RewindRecreatable — class provides its own creation hook.
        if (RewindRecreatable.class.isAssignableFrom(cls)) {
            AbstractObjectInstance probe = constructProbeForRewindRecreatable(cls, ctx);
            if (probe instanceof RewindRecreatable rr) {
                RewindRecreateContext rewindCtx = new RewindRecreateContext(
                        entry.spawn(), entry.state(), ctx.objectServices());
                return rr.recreateForRewind(rewindCtx);
            }
            LOG.fine("genericRecreate: RewindRecreatable probe construction failed for " + className);
            return null;
        }

        // Path 2: Registry — rebuild from spawn via the game registry factory.
        ObjectRegistry registry = ctx.objectRegistry();
        if (registry == null || entry.spawn() == null) {
            return null;
        }
        try {
            return registry.create(entry.spawn());
        } catch (Exception e) {
            LOG.fine("genericRecreate: registry.create threw for " + className + ": " + e);
            return null;
        }
    }

    /**
     * Constructs a minimal probe instance of a {@link RewindRecreatable} class so that
     * {@link RewindRecreatable#recreateForRewind} can be called on it.
     *
     * <p>Tries constructors in order:
     * <ol>
     *   <li>{@code (ObjectSpawn)} — single-arg spawn constructor</li>
     *   <li>zero-arg — no-argument default constructor</li>
     * </ol>
     *
     * @return the probe instance, or {@code null} if no compatible constructor was found
     */
    private static AbstractObjectInstance constructProbeForRewindRecreatable(
            Class<? extends AbstractObjectInstance> cls,
            DynamicObjectRecreateContext ctx) {
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

        // Try (ObjectSpawn) constructor first.
        try {
            Constructor<? extends AbstractObjectInstance> ctor =
                    cls.getDeclaredConstructor(ObjectSpawn.class);
            ctor.setAccessible(true);
            Constructor<? extends AbstractObjectInstance> finalCtor = ctor;
            return ObjectConstructionContext.construct(ctx.objectServices(),
                    () -> {
                        try {
                            return finalCtor.newInstance(spawn);
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (NoSuchMethodException ignored) {
            // Fall through.
        } catch (Exception e) {
            LOG.fine("genericRecreate: (ObjectSpawn) probe failed for "
                    + cls.getName() + ": " + e);
        }

        // Try zero-arg constructor.
        try {
            Constructor<? extends AbstractObjectInstance> ctor =
                    cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            Constructor<? extends AbstractObjectInstance> finalCtor = ctor;
            return ObjectConstructionContext.construct(ctx.objectServices(),
                    () -> {
                        try {
                            return finalCtor.newInstance();
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (NoSuchMethodException ignored) {
            // No compatible constructor found.
        } catch (Exception e) {
            LOG.fine("genericRecreate: zero-arg probe failed for "
                    + cls.getName() + ": " + e);
        }

        return null;
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
                skidDustCodec(),
                // Batch-7: signpost ring sparkle (shared S1+S2; S3K uses S3kSignpostSparkleChild).
                // worldX/worldY are reapplied by the post-recreate non-final scalar restore.
                exactSpawnCodec(
                        SignpostSparkleObjectInstance.class,
                        spawn -> new SignpostSparkleObjectInstance(0, 0)));
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

    /**
     * Variant of {@link #exactSpawnCodec(Class, Function)} whose factory also
     * receives the restore-time {@link ObjectServices}, so codecs that need
     * runtime context (e.g. the current ROM zone id) can resolve it through the
     * injected service handle rather than a global {@code GameServices} lookup.
     */
    public static DynamicObjectRewindCodec exactSpawnCodec(
            Class<? extends AbstractObjectInstance> type,
            BiFunction<ObjectSpawn, ObjectServices, ? extends AbstractObjectInstance> factory) {
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
                return factory.apply(entry.spawn(), context.objectServices());
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
