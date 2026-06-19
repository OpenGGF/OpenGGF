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
     *   <li>{@code (ObjectSpawn, boolean)} — spawn plus default false option</li>
     *   <li>zero-arg — no-argument default constructor</li>
     * </ol>
     *
     * <p><strong>Failure handling:</strong> a missing constructor signature
     * ({@link NoSuchMethodException}) is benign — the next strategy is tried, and if none
     * matches {@code null} is returned. But a constructor that EXISTS and throws mid-body
     * is a hard error: the probe-construction failure would otherwise silently produce a
     * {@code null} recreate (a missing object on rewind). Such failures are logged at
     * {@code WARNING} and re-thrown so Task-6 migration mistakes surface loudly rather than
     * corrupting restored state.
     *
     * @return the probe instance, or {@code null} if no compatible constructor signature exists
     * @throws RuntimeException if a matching constructor exists but throws while constructing
     */
    private static AbstractObjectInstance constructProbeForRewindRecreatable(
            Class<? extends AbstractObjectInstance> cls,
            DynamicObjectRecreateContext ctx) {
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

        Constructor<? extends AbstractObjectInstance> spawnCtor = findCtor(cls, ObjectSpawn.class);
        if (spawnCtor != null) {
            return invokeProbeCtor(cls, spawnCtor, ctx, spawn);
        }

        Constructor<? extends AbstractObjectInstance> spawnBooleanCtor =
                findCtor(cls, ObjectSpawn.class, boolean.class);
        if (spawnBooleanCtor != null) {
            return invokeProbeCtor(cls, spawnBooleanCtor, ctx, spawn, false);
        }

        Constructor<? extends AbstractObjectInstance> noArgCtor = findCtor(cls);
        if (noArgCtor != null) {
            return invokeProbeCtor(cls, noArgCtor, ctx);
        }

        // No (ObjectSpawn) or zero-arg constructor — cannot build a probe for this class.
        return null;
    }

    /**
     * Looks up a declared constructor with the given parameter types, returning {@code null}
     * when no such signature exists (a benign "try the next strategy" condition).
     */
    private static Constructor<? extends AbstractObjectInstance> findCtor(
            Class<? extends AbstractObjectInstance> cls, Class<?>... paramTypes) {
        try {
            Constructor<? extends AbstractObjectInstance> ctor =
                    cls.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Invokes a probe constructor that is known to exist. Any exception thrown while
     * constructing (the constructor body failing) is logged at {@code WARNING} and
     * re-thrown — it is never swallowed into a {@code null} recreate.
     */
    private static AbstractObjectInstance invokeProbeCtor(
            Class<? extends AbstractObjectInstance> cls,
            Constructor<? extends AbstractObjectInstance> ctor,
            DynamicObjectRecreateContext ctx,
            Object... args) {
        try {
            return ObjectConstructionContext.construct(ctx.objectServices(),
                    () -> {
                        try {
                            return ctor.newInstance(args);
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "genericRecreate: RewindRecreatable probe constructor threw for "
                            + cls.getName() + "; recreate cannot proceed", e);
            throw e;
        }
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
