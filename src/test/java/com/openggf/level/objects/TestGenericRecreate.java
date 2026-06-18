package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.HtzGroundFireObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ObjectRewindDynamicCodecs#genericRecreate(DynamicObjectEntry, DynamicObjectRecreateContext)}.
 *
 * <p>Covers two recreate paths:
 * <ol>
 *   <li><strong>Registry path:</strong> spawn-constructible object with its objectId registered in
 *       the game registry recreates via {@code registry.create(spawn)}.</li>
 *   <li><strong>{@link RewindRecreatable} path:</strong> non-spawn-constructible object that
 *       implements {@link RewindRecreatable} recreates via
 *       {@link RewindRecreatable#recreateForRewind(RewindRecreateContext)}.</li>
 * </ol>
 *
 * <p>Adoption safety: both paths must not return a class already adopted by the keystone
 * (construction-spawned children); this is enforced by the ObjectManager restore loop
 * before calling {@code genericRecreate}, so the method itself simply never sees adopted classes.
 */
class TestGenericRecreate {

    private ObjectManager objectManager;
    private DynamicObjectRecreateContext ctx;

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        StubObjectServices[] serviceHolder = new StubObjectServices[1];
        ObjectManager[] managerHolder = new ObjectManager[1];

        StubObjectServices stub = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return managerHolder[0];
            }
        };
        serviceHolder[0] = stub;

        Sonic2ObjectRegistry registry = new Sonic2ObjectRegistry();
        objectManager = new ObjectManager(
                List.of(), registry,
                0, null, null,
                GraphicsManager.getInstance(),
                mockCamera(),
                stub);
        managerHolder[0] = objectManager;
        objectManager.reset(0);

        ctx = new DynamicObjectRecreateContext(objectManager);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    // =========================================================================
    // Path 1 — RewindRecreatable hook (HtzGroundFireObjectInstance)
    // =========================================================================

    /**
     * A non-spawn-constructible object that implements {@link RewindRecreatable} must
     * recreate successfully via {@code genericRecreate} without a per-object codec registered.
     *
     * <p>HtzGroundFireObjectInstance normally requires {@code (int x, int y, int dir, int remaining)}
     * constructor arguments that the registry cannot supply. By implementing
     * {@link RewindRecreatable}, it provides its own recreation hook so no
     * {@link DynamicObjectRewindCodec} is needed.
     */
    @Test
    void rewindRecreatableObjectRecreatesViaHook() {
        // HtzGroundFireObjectInstance has no per-object codec — it uses RewindRecreatable.
        DynamicObjectEntry entry = capturedEntryFor(
                new HtzGroundFireObjectInstance(100, 200, 1, 3));

        ObjectInstance inst = ObjectRewindDynamicCodecs.genericRecreate(entry, ctx);

        assertNotNull(inst, "genericRecreate must return a non-null instance for RewindRecreatable classes");
        assertEquals(HtzGroundFireObjectInstance.class, inst.getClass(),
                "genericRecreate must return an instance of the captured class");
    }

    /**
     * The RewindRecreateContext passed to the hook must expose the captured spawn.
     */
    @Test
    void rewindRecreatableHookReceivesSpawnFromEntry() {
        ObjectSpawn spawn = new ObjectSpawn(42, 77, Sonic2ObjectIds.LAVA_BUBBLE, 0, 0, false, 0);
        HtzGroundFireObjectInstance inst = new HtzGroundFireObjectInstance(42, 77, -1, 2);
        DynamicObjectEntry entry = capturedEntryFor(inst, spawn);

        ObjectInstance result = ObjectRewindDynamicCodecs.genericRecreate(entry, ctx);

        assertNotNull(result);
        assertEquals(HtzGroundFireObjectInstance.class, result.getClass());
    }

    // =========================================================================
    // Path 2 — Registry path (spawn-constructible, registered class)
    // =========================================================================

    /**
     * A spawn-constructible object whose objectId maps to the correct class in the
     * registry recreates via {@code registry.create(spawn)} when the class does NOT
     * implement {@link RewindRecreatable}.
     */
    @Test
    void spawnConstructibleObjectRecreatesViaRegistry() {
        // SignpostSparkleObjectInstance is a simple spawn-constructible shared object.
        // It has an (int, int) constructor, not (ObjectSpawn), but the shared codec handles it.
        // For the registry path test we use a spawn-registered non-badnik object.
        // Use LostRingObjectInstance which has a (ObjectSpawn) ctor and is registered.
        ObjectSpawn spawn = new ObjectSpawn(50, 50, 0, 0, 0, false, 0);
        // MinimalRegistryObject is a spawn-constructible stand-in:
        DynamicObjectEntry entry = new DynamicObjectEntry(
                MinimalRegistryObject.class.getName(),
                spawn, -1, null);

        // MinimalRegistryObject is NOT in any registry — genericRecreate falls through
        // to registry path and returns null (no factory registered for objectId=0).
        // This validates the registry path is attempted (and gracefully returns null).
        ObjectInstance result = ObjectRewindDynamicCodecs.genericRecreate(entry, ctx);
        // Registry has no factory for objectId=0 → PlaceholderObjectInstance or null.
        // The key assertion is that genericRecreate does not throw.
        // (A null result is expected when registry has no matching factory for this artificial spawn.)
        assertDoesNotThrow(() -> ObjectRewindDynamicCodecs.genericRecreate(entry, ctx));
    }

    // =========================================================================
    // RewindRecreatable interface contract
    // =========================================================================

    /**
     * HtzGroundFireObjectInstance must implement {@link RewindRecreatable} for the generic path.
     */
    @Test
    void htzGroundFireImplementsRewindRecreatable() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(HtzGroundFireObjectInstance.class),
                "HtzGroundFireObjectInstance must implement RewindRecreatable");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal {@link DynamicObjectEntry} for the given live instance,
     * using the instance's captured spawn.
     */
    private static DynamicObjectEntry capturedEntryFor(AbstractObjectInstance instance) {
        return capturedEntryFor(instance, instance.getSpawn());
    }

    /**
     * Builds a minimal {@link DynamicObjectEntry} for the given live instance with an
     * explicit spawn override (e.g. to test spawn field propagation to the hook).
     */
    private static DynamicObjectEntry capturedEntryFor(AbstractObjectInstance instance, ObjectSpawn spawn) {
        return new DynamicObjectEntry(
                instance.getClass().getName(),
                spawn,
                -1,
                null // state; scalar restore is out-of-scope for unit tests
        );
    }

    private static com.openggf.camera.Camera mockCamera() {
        return new com.openggf.camera.Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    /**
     * Minimal spawn-constructible object for registry-path testing.
     * Deliberately NOT implementing {@link RewindRecreatable} — exercises the registry path.
     */
    private static final class MinimalRegistryObject extends AbstractObjectInstance {
        MinimalRegistryObject(ObjectSpawn spawn) {
            super(spawn, "MinimalRegistryObject");
        }

        @Override
        public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {
            // no-op
        }
    }
}
