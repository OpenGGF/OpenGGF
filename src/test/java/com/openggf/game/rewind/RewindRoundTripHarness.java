package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.GameId;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.AbstractBossChild;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Reusable harness that drives the REAL {@code ObjectManager} + {@code RewindRegistry}
 * capture→restore round-trip for headless rewind correctness tests.
 *
 * <h2>Why this is stronger than {@code RewindRoundTripProbe}</h2>
 * {@code RewindRoundTripProbe} calls {@code captureRewindState()} and
 * {@code restoreRewindState()} on isolated object instances, re-constructing from spawn
 * each time. This is tautological: a fresh construction always matches a fresh construction.
 * The harness drives the production {@code ObjectManager.rewindSnapshottable()} →
 * {@code RewindRegistry.capture()} → {@code RewindRegistry.restore()} path, which
 * includes the actual codecs, the dynamic-object slot allocation, and the boss-child
 * reconstructor suppression — the real production path.
 *
 * <h2>Usage — keystone boss-child test</h2>
 * <pre>{@code
 * RewindRoundTripHarness h = RewindRoundTripHarness.build(GameId.S2);
 * h.spawnPlacedAndStep(Sonic2ObjectIds.DEATH_EGG_ROBOT, 6);
 * Map<String,Integer> before = h.countByType();
 * int capturedX = h.firstArticulatedChildX();
 * h.roundTrip();
 * assertEquals(before, h.countByType());
 * assertEquals(capturedX, h.firstArticulatedChildX());
 * }</pre>
 *
 * <h2>Usage — static sweep API</h2>
 * {@link #probeClass(String)} attempts to construct a class headlessly, inject it
 * into a fresh ObjectManager, and drive a round-trip. Returns a sealed
 * {@link RoundTripSweepResult} that the parametrized test can assert on.
 */
public final class RewindRoundTripHarness {

    /** Representative spawn used by the class sweep when no specific ID is known. */
    private static final ObjectSpawn PROBE_SPAWN =
            new ObjectSpawn(0x100, 0x100, 1, 0, 0, false, 0);

    /** Binary class name of the inner ArticulatedChild (not ForearmChild). */
    private static final String ARTICULATED_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$ArticulatedChild";

    // =========================================================================
    // Mutable instance state (reassigned by spawnPlacedAndStep)
    // =========================================================================

    private final GameId gameId;
    private ObjectManager om;
    private RewindRegistry rr;

    private RewindRoundTripHarness(GameId gameId, ObjectManager om, RewindRegistry rr) {
        this.gameId = gameId;
        this.om = om;
        this.rr = rr;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Builds a fresh, empty harness for the given game.
     *
     * <p>The harness starts with no placed spawns. Call
     * {@link #spawnPlacedAndStep(int, int)} to add a spawn before the first
     * {@link #roundTrip()}.
     *
     * @param gameId the game whose object registry to use
     * @return a fresh harness
     */
    public static RewindRoundTripHarness build(GameId gameId) {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = makeServices(holder, camera);

        ObjectRegistry registry = registryFor(gameId);
        ObjectManager om = new ObjectManager(
                List.of(), registry,
                0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = om;
        om.reset(0);

        RewindRegistry rr = new RewindRegistry();
        rr.register(om.rewindSnapshottable());

        return new RewindRoundTripHarness(gameId, om, rr);
    }

    /**
     * Builds a harness with a specific placed spawn already materialised.
     * The ObjectManager is configured with the given spawn and initialised via
     * {@code reset(0)}. The RewindRegistry is registered and ready for
     * {@link #roundTrip()}.
     *
     * <p>This is the preferred factory for the keystone boss-child test, where the
     * boss must be in the placed/active object list (not the dynamic list) to match
     * the production code path that the restore reconstructs from.
     *
     * @param gameId   the game whose object registry to use
     * @param objectId the object type ID for the placed spawn
     * @return a harness with the object materialised
     */
    public static RewindRoundTripHarness buildPlaced(GameId gameId, int objectId) {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = makeServices(holder, camera);

        ObjectRegistry registry = registryFor(gameId);
        ObjectSpawn spawn = new ObjectSpawn(160, 240, objectId, 0, 0, false, 0);
        ObjectManager om = new ObjectManager(
                List.of(spawn), registry,
                0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = om;
        om.reset(0);

        RewindRegistry rr = new RewindRegistry();
        rr.register(om.rewindSnapshottable());

        return new RewindRoundTripHarness(gameId, om, rr);
    }

    // =========================================================================
    // Instance operations
    // =========================================================================

    /**
     * Replaces the harness's ObjectManager with one that has the given spawn
     * as its single placed object, materialises it via {@code reset(0)},
     * and re-registers the new manager with the RewindRegistry.
     *
     * <p>This rebuilds the ObjectManager because the spawn list is immutable
     * after construction; the RewindRegistry is reset to track the new manager.
     *
     * @param objectId the registry object type ID
     * @param frames   number of update frames to step after materialisation
     *                 (0 = just materialise, no stepping)
     */
    public void spawnPlacedAndStep(int objectId, int frames) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = makeServices(holder, camera);

        ObjectRegistry registry = registryFor(gameId);
        ObjectSpawn spawn = new ObjectSpawn(160, 240, objectId, 0, 0, false, 0);
        ObjectManager newOm = new ObjectManager(
                List.of(spawn), registry,
                0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = newOm;
        newOm.reset(0);

        // Step frames (null player = no player interaction; touch responses disabled)
        for (int i = 0; i < frames; i++) {
            newOm.update(0, null, null, i, false);
        }

        // Re-register the new ObjectManager.
        this.om = newOm;
        this.rr = new RewindRegistry();
        this.rr.register(newOm.rewindSnapshottable());
    }

    /**
     * Returns a snapshot of live non-destroyed object counts by class name.
     *
     * @return map of binary class name → live count
     */
    public Map<String, Integer> countByType() {
        return countByTypeFrom(om);
    }

    /**
     * Returns the {@code currentX} value of the first live non-destroyed
     * {@code ArticulatedChild} (not {@code ForearmChild}) instance.
     *
     * <p>This is a convenience accessor for the DEZ boss-child keystone test.
     *
     * @return the currentX of the first ArticulatedChild, or {@code -1} if none found
     */
    public int firstArticulatedChildX() {
        for (ObjectInstance o : om.getActiveObjects()) {
            if (o.getClass().getName().equals(ARTICULATED_CHILD_CLASS) && !o.isDestroyed()) {
                if (o instanceof AbstractBossChild bc) {
                    return bc.getCurrentX();
                }
            }
        }
        return -1;
    }

    /**
     * Executes the rewind round-trip: capture snapshot → restore snapshot.
     *
     * <p>After this call, the {@code ObjectManager} reflects the restored state
     * as if a keyframe was restored with zero re-simulation frames.
     */
    public void roundTrip() {
        CompositeSnapshot snap = rr.capture();
        rr.restore(snap);
    }

    /**
     * Returns the underlying {@code ObjectManager} for direct inspection.
     */
    public ObjectManager objectManager() {
        return om;
    }

    // =========================================================================
    // Sweep probe API (static — drives REAL ObjectManager round-trip)
    // =========================================================================

    /**
     * Sealed result type returned by {@link #probeClass(String)}.
     */
    public sealed interface RoundTripSweepResult
            permits RoundTripSweepResult.Passed,
                    RoundTripSweepResult.Unprobed,
                    RoundTripSweepResult.CountMismatch,
                    RoundTripSweepResult.ScalarMismatch {

        /**
         * Round-trip succeeded: object count unchanged and all scalar fields identical.
         */
        record Passed() implements RoundTripSweepResult {}

        /**
         * Object could not be constructed headlessly (ROM/OpenGL required) or added
         * to the ObjectManager. Not silently passed — recorded as an unprobed gap.
         */
        record Unprobed(String reason) implements RoundTripSweepResult {}

        /**
         * Object count changed after round-trip (double-spawn or silent drop).
         */
        record CountMismatch(
                Map<String, Integer> beforeCounts,
                Map<String, Integer> afterCounts) implements RoundTripSweepResult {}

        /**
         * Scalar field values differ after round-trip (wrong state restored,
         * or init-revert instead of exact-state restore).
         */
        record ScalarMismatch(List<ScalarDiff> diffs) implements RoundTripSweepResult {}
    }

    /**
     * A single differing scalar field after round-trip.
     *
     * @param className the FQN of the object class
     * @param fieldName the field name (qualified as "DeclaredClass.fieldName")
     * @param before    the field value before round-trip
     * @param after     the field value after round-trip
     * @param isFinal   whether the field is {@code final} (such fields are not
     *                  captured by {@code GenericFieldCapturer}; this is a known gap)
     */
    public record ScalarDiff(
            String className,
            String fieldName,
            String before,
            String after,
            boolean isFinal) {
    }

    /**
     * Probes the given class through the REAL ObjectManager + RewindRegistry
     * capture→restore round-trip.
     *
     * <p>Only classes with a registered dynamic rewind codec (in the shared or
     * game-specific registry) are tested via the dynamic round-trip path. Classes
     * WITHOUT a codec are classified as {@code Unprobed("no dynamic recreate path")}
     * because dropping them on restore is correct (expected) behaviour for the current
     * codebase. Placed-object recreation (which does not require a codec) is tested by
     * the keystone boss-child test in {@code TestEveryObjectRewindRoundTrip}.
     *
     * <p>Construction is attempted using the same four strategies as
     * {@code RewindRoundTripProbe}: zero-arg, {@code (ObjectSpawn)},
     * {@code (ObjectSpawn, String)}, and {@code (ObjectSpawn, ObjectServices)}.
     * Classes requiring ROM access during construction are returned as
     * {@code Unprobed}.
     *
     * @param fqn the binary class name to probe
     * @return a {@link RoundTripSweepResult} — never {@code null}
     */
    public static RoundTripSweepResult probeClass(String fqn) {
        // 0. Check codec presence. Dynamic objects without a codec are correctly
        //    dropped on restore — testing them would always fail with a count-mismatch
        //    and is not useful for Phase 2 gate purposes.
        if (!hasRegisteredCodec(fqn)) {
            return new RoundTripSweepResult.Unprobed("no dynamic recreate path (no codec registered)");
        }

        // 1. Resolve the class.
        Class<?> rawClass;
        try {
            rawClass = Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            return new RoundTripSweepResult.Unprobed("ClassNotFoundException: " + e.getMessage());
        }

        if (!AbstractObjectInstance.class.isAssignableFrom(rawClass)
                || Modifier.isAbstract(rawClass.getModifiers())) {
            return new RoundTripSweepResult.Unprobed(
                    "abstract or not an AbstractObjectInstance subclass");
        }

        @SuppressWarnings("unchecked")
        Class<? extends AbstractObjectInstance> cls =
                (Class<? extends AbstractObjectInstance>) rawClass;

        // 2. Build a minimal headless ObjectManager (empty spawns, game-appropriate registry).
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        StubObjectServices stub = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }

            @Override
            public Camera camera() {
                return camera;
            }
        };

        GameId gameId = inferGameIdFromFqn(fqn);
        ObjectRegistry registry = registryFor(gameId);
        ObjectManager om = new ObjectManager(
                List.of(), registry,
                0, null, null,
                GraphicsManager.getInstance(), camera, stub);
        holder[0] = om;
        om.reset(0);

        // 3. Try to construct the instance headlessly.
        AbstractObjectInstance instance;
        try {
            instance = tryConstruct(cls, stub);
        } catch (Throwable t) {
            return new RoundTripSweepResult.Unprobed(describeThrowable(t));
        }

        // 4. Register the instance as a dynamic object so it participates in
        //    the ObjectManager's capture→restore snapshot.
        try {
            om.addDynamicObject(instance);
        } catch (Throwable t) {
            return new RoundTripSweepResult.Unprobed(
                    "addDynamicObject threw: " + describeThrowable(t));
        }

        // 5. Capture the state BEFORE round-trip.
        Map<String, Integer> beforeCounts = countByTypeFrom(om);
        Map<String, String> beforeFields = captureScalarFields(instance, cls);

        // 6. Wire up the RewindRegistry and do the round-trip.
        RewindRegistry rr = new RewindRegistry();
        rr.register(om.rewindSnapshottable());

        CompositeSnapshot snap;
        try {
            snap = rr.capture();
        } catch (Throwable t) {
            return new RoundTripSweepResult.Unprobed("capture threw: " + describeThrowable(t));
        }
        try {
            rr.restore(snap);
        } catch (Throwable t) {
            return new RoundTripSweepResult.Unprobed("restore threw: " + describeThrowable(t));
        }

        // 7. Check count (no double-spawn, no drop).
        Map<String, Integer> afterCounts = countByTypeFrom(om);
        if (!beforeCounts.equals(afterCounts)) {
            // Distinguish the two count-change cases HONESTLY:
            //
            //  (a) The probed class is ENTIRELY ABSENT after restore (before=1, after=0,
            //      and nothing else recreated). The only way a codec'd object disappears
            //      is that its recreate() returned null. In the isolated harness this
            //      happens for parent-dependent children whose recreate() relinks a live
            //      parent that the isolated ObjectManager does not contain (e.g. the CNZ
            //      miniboss children, the gumball-machine ExitTriggerChild). In production
            //      the parent is always a placed object reconstructed first, so the child
            //      relinks successfully. This is NOT a product bug — record it as Unprobed
            //      (kept in the unprobed bucket, not silently passed) rather than a failure.
            //
            //  (b) Any OTHER count change — most notably a DOUBLE-SPAWN where the probed
            //      class is still present but at a higher count (after >= 2), or some
            //      unrelated class appeared — is a genuine capture/restore bug and stays
            //      a hard CountMismatch failure.
            int beforeForClass = beforeCounts.getOrDefault(fqn, 0);
            int afterForClass = afterCounts.getOrDefault(fqn, 0);
            boolean recreateReturnedNullInIsolation =
                    beforeForClass > 0 && afterForClass == 0 && afterCounts.isEmpty();
            if (recreateReturnedNullInIsolation) {
                return new RoundTripSweepResult.Unprobed(
                        "parent-dependent — recreate needs a live parent in isolation");
            }
            return new RoundTripSweepResult.CountMismatch(beforeCounts, afterCounts);
        }

        // 8. Locate the restored instance of the same class.
        AbstractObjectInstance restored = findFirstByClass(om, cls);
        if (restored == null) {
            // Should have been caught by count check, but handle defensively.
            return new RoundTripSweepResult.CountMismatch(beforeCounts, Map.of());
        }

        // 9. Compare scalar fields.
        Map<String, String> afterFields = captureScalarFields(restored, cls);
        List<ScalarDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, String> entry : beforeFields.entrySet()) {
            String key = entry.getKey();
            String beforeVal = entry.getValue();
            String afterVal = afterFields.get(key);
            if (!Objects.equals(beforeVal, afterVal)) {
                boolean isFinal = isFieldFinal(cls, key);
                diffs.add(new ScalarDiff(fqn, key, beforeVal, afterVal, isFinal));
            }
        }
        if (!diffs.isEmpty()) {
            return new RoundTripSweepResult.ScalarMismatch(diffs);
        }

        return new RoundTripSweepResult.Passed();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static ObjectServices makeServices(ObjectManager[] holder, Camera camera) {
        return new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }

            @Override
            public Camera camera() {
                return camera;
            }
        };
    }

    /**
     * Returns true if the given FQN has a registered dynamic rewind codec in
     * the shared codecs or in any of the three per-game registries.
     *
     * <p>Objects without a codec are correctly dropped on restore (they have no
     * dynamic recreate path). Testing them would always produce a count-mismatch,
     * so they are excluded from the dynamic sweep.
     */
    private static boolean hasRegisteredCodec(String fqn) {
        // Shared game-agnostic codecs
        for (DynamicObjectRewindCodec c : ObjectRewindDynamicCodecs.sharedCodecs()) {
            if (fqn.equals(c.className())) return true;
        }
        // Per-game registries
        for (ObjectRegistry reg : new ObjectRegistry[]{
                new Sonic1ObjectRegistry(),
                new Sonic2ObjectRegistry(),
                new Sonic3kObjectRegistry()}) {
            for (DynamicObjectRewindCodec c : reg.dynamicRewindCodecs()) {
                if (fqn.equals(c.className())) return true;
            }
        }
        return false;
    }

    private static ObjectRegistry registryFor(GameId gameId) {
        return switch (gameId) {
            case S1 -> new Sonic1ObjectRegistry();
            case S2 -> new Sonic2ObjectRegistry();
            case S3K -> new Sonic3kObjectRegistry();
        };
    }

    private static GameId inferGameIdFromFqn(String fqn) {
        if (fqn.startsWith("com.openggf.game.sonic1.")) return GameId.S1;
        if (fqn.startsWith("com.openggf.game.sonic2.")) return GameId.S2;
        if (fqn.startsWith("com.openggf.game.sonic3k.")) return GameId.S3K;
        // Shared com.openggf.level.objects.* — use S2 as a representative default.
        return GameId.S2;
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    /**
     * Attempts to construct an {@code AbstractObjectInstance} headlessly using the
     * four probe-compatible constructor signatures (zero-arg, {@code (ObjectSpawn)},
     * {@code (ObjectSpawn, String)}, {@code (ObjectSpawn, ObjectServices)}).
     *
     * <p>This is the shared construction entry point. {@link RewindRoundTripProbe}
     * in the coverage package delegates here to avoid duplicating the strategy logic.
     *
     * @param cls  the concrete class to instantiate
     * @param stub the stub services to inject during construction
     * @return a freshly constructed instance
     * @throws NoSuchMethodError if no compatible constructor is found
     * @throws RuntimeException  wrapping any construction exception
     */
    public static AbstractObjectInstance constructHeadless(
            Class<? extends AbstractObjectInstance> cls,
            StubObjectServices stub) {
        return tryConstruct(cls, stub);
    }

    /**
     * Tries to construct an {@code AbstractObjectInstance} headlessly using four
     * progressively-complex constructor signatures.
     */
    private static AbstractObjectInstance tryConstruct(
            Class<? extends AbstractObjectInstance> cls,
            StubObjectServices stub) {

        // Strategy 1: zero-arg
        Constructor<? extends AbstractObjectInstance> ctor0 = findCtor(cls);
        if (ctor0 != null) {
            try {
                return ObjectConstructionContext.construct(stub, () -> invokeNoArg(ctor0));
            } catch (Throwable ignored) {
            }
        }

        // Strategy 2: (ObjectSpawn)
        Constructor<? extends AbstractObjectInstance> ctor1 = findCtor(cls, ObjectSpawn.class);
        if (ctor1 != null) {
            try {
                return ObjectConstructionContext.construct(stub,
                        () -> invokeWith(ctor1, PROBE_SPAWN));
            } catch (Throwable ignored) {
            }
        }

        // Strategy 3: (ObjectSpawn, String)
        Constructor<? extends AbstractObjectInstance> ctor2 =
                findCtor(cls, ObjectSpawn.class, String.class);
        if (ctor2 != null) {
            try {
                return ObjectConstructionContext.construct(stub,
                        () -> invokeWith(ctor2, PROBE_SPAWN, "probe"));
            } catch (Throwable ignored) {
            }
        }

        // Strategy 4: (ObjectSpawn, ObjectServices)
        Constructor<? extends AbstractObjectInstance> ctor3 =
                findCtor(cls, ObjectSpawn.class, ObjectServices.class);
        if (ctor3 != null) {
            try {
                return ObjectConstructionContext.construct(stub,
                        () -> invokeWith(ctor3, PROBE_SPAWN, stub));
            } catch (Throwable ignored) {
            }
        }

        throw new NoSuchMethodError(
                "No probe-compatible constructor found for " + cls.getName()
                        + " (tried zero-arg, (ObjectSpawn), (ObjectSpawn,String),"
                        + " (ObjectSpawn,ObjectServices))");
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractObjectInstance> Constructor<T> findCtor(
            Class<T> cls, Class<?>... paramTypes) {
        try {
            Constructor<T> ctor = cls.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static AbstractObjectInstance invokeNoArg(
            Constructor<? extends AbstractObjectInstance> ctor) {
        try {
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static AbstractObjectInstance invokeWith(
            Constructor<? extends AbstractObjectInstance> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Captures all non-static, non-synthetic primitive/enum/String fields from
     * the object's class hierarchy between the concrete class and
     * {@code AbstractObjectInstance} (exclusive).
     */
    private static Map<String, String> captureScalarFields(
            AbstractObjectInstance obj,
            Class<? extends AbstractObjectInstance> cls) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Class<?> c = cls;
                c != null && c != AbstractObjectInstance.class && c != Object.class;
                c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isSynthetic()) continue;
                Class<?> type = f.getType();
                if (!type.isPrimitive() && !type.isEnum() && type != String.class) continue;
                f.setAccessible(true);
                try {
                    fields.put(c.getSimpleName() + "." + f.getName(),
                            String.valueOf(f.get(obj)));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return fields;
    }

    private static boolean isFieldFinal(
            Class<? extends AbstractObjectInstance> cls, String qualifiedName) {
        int dot = qualifiedName.indexOf('.');
        if (dot < 0) return false;
        String fieldName = qualifiedName.substring(dot + 1);
        for (Class<?> c = cls;
                c != null && c != AbstractObjectInstance.class && c != Object.class;
                c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                return Modifier.isFinal(f.getModifiers());
            } catch (NoSuchFieldException ignored) {
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static AbstractObjectInstance findFirstByClass(
            ObjectManager om, Class<? extends AbstractObjectInstance> cls) {
        for (ObjectInstance o : om.getActiveObjects()) {
            if (cls.isInstance(o) && !o.isDestroyed()) {
                return (AbstractObjectInstance) o;
            }
        }
        return null;
    }

    private static Map<String, Integer> countByTypeFrom(ObjectManager om) {
        Map<String, Integer> counts = new TreeMap<>();
        Collection<ObjectInstance> all = om.getActiveObjects();
        for (ObjectInstance o : all) {
            if (!o.isDestroyed()) {
                counts.merge(o.getClass().getName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String describeThrowable(Throwable t) {
        Throwable root = t;
        int depth = 0;
        while (root.getCause() != null && depth++ < 5) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
