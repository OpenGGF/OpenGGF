package com.openggf.level.objects;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.badniks.Sonic1BurrobotBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestObjectManagerCounterBasedDynamicUnload {

    private ObjectManager objectManager;

    @BeforeEach
    public void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.camera().resetState();
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);

        ObjectServices services = new StubObjectServices() {
            @Override
            public com.openggf.camera.Camera camera() {
                return GameServices.camera();
            }
        };

        objectManager = new ObjectManager(List.of(), new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return null;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Test";
            }
        }, 0, null, null, null, GameServices.camera(), services);
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void counterBasedUpdateRemovesOutOfRangeDynamicObjects() {
        TestDynamicObject object = new TestDynamicObject(new ObjectSpawn(0x2000, 0x0100, 0x01, 0, 0, false, 0));
        objectManager.addDynamicObject(object);

        assertTrue(objectManager.getActiveObjects().contains(object), "Sanity check: dynamic object should be tracked before update");

        objectManager.update(0, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(object),
                "Counter-based S1 updates should unload out-of-range dynamic objects to preserve slot parity");
    }

    @Test
    public void counterBasedUpdateIgnoresSpawnlessFallbackDynamicsForOutOfRangeChecks() {
        SpawnlessFallbackObject object = new SpawnlessFallbackObject();
        objectManager.addDynamicObject(object);

        assertTrue(objectManager.getActiveObjects().contains(object),
                "Sanity check: spawnless fallback object should be tracked before update");

        assertDoesNotThrow(() -> objectManager.update(0, null, List.of(), 1),
                "Fallback dynamic objects without spawn-backed positions should not enter S1 out_of_range unload");

        assertTrue(objectManager.getActiveObjects().contains(object),
                "Spawnless fallback objects should remain active when counter-based unload only applies to spawned objects");
        assertTrue(object.updated, "Fallback object should still be updated normally");
    }

    @Test
    public void counterBasedResetPreloadsInWindowObjectsBeforeFirstExecuteObjectsPass() {
        ObjectSpawn spawn = new ObjectSpawn(0x0100, 0x0100, 0x31, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
                });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);

        TrackingObjectInstance instance = registry.instance;
        assertNotNull(instance, "Reset should preload in-window counter-based spawns");
        assertEquals(0, instance.updateCount,
                "Preloaded S1 counter-based objects should still wait for the first ExecuteObjects pass");

        objectManager.update(0, null, List.of(), 1);

        assertEquals(1, instance.updateCount,
                "Object should execute on the first gameplay frame after reset preload");
    }

    @Test
    public void counterBasedNonTrackedSpawnUnloadsWhenCameraMovesOutOfRange() {
        ObjectSpawn spawn = new ObjectSpawn(0x0100, 0x0100, 0x31, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
                });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);

        TrackingObjectInstance first = registry.instance;
        assertNotNull(first, "Initial reset should preload the in-window non-tracked spawn");
        assertEquals(1, registry.createCount, "Initial preload should create exactly one instance");

        objectManager.update(0x0400, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(first),
                "Non-tracked S1 objects should unload once their spawn leaves the placement window");
    }

    @Test
    public void counterBasedNonTrackedSpawnStaysAliveWhileCurrentPositionRemainsInRange() {
        ObjectSpawn spawn = new ObjectSpawn(0x0100, 0x0100, 0x31, 0, 0, false, 0);
        InRangeButMovedRegistry registry = new InRangeButMovedRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
                });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);

        InRangeButMovedObject instance = registry.instance;
        assertNotNull(instance, "Initial reset should preload the non-tracked spawn");

        objectManager.update(0, null, List.of(), 1);
        objectManager.postCameraPlacementUpdate(0x0200);
        objectManager.update(0x0200, null, List.of(), 2);

        assertTrue(objectManager.getActiveObjects().contains(instance),
                "Counter-based S1 updates should keep non-tracked objects alive until ROM out_of_range "
                        + "fails against the object's current/reference X");
    }

    @Test
    public void counterBasedBurrobotUnloadsWithRomRememberStateWindow() {
        ObjectSpawn spawn = new ObjectSpawn(0x0650, 0x00D0, Sonic1ObjectIds.BURROBOT, 0, 0, true, 0);
        BurrobotRegistry registry = new BurrobotRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
        });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0x0600);

        Sonic1BurrobotBadnikInstance burrobot = registry.instance;
        assertNotNull(burrobot, "Reset should preload the Burrobot before the exec pass");

        objectManager.postCameraPlacementUpdate(0x0704);
        objectManager.update(0x0704, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(burrobot),
                "Burrobot uses RememberState; docs/s1disasm/s1disasm/Macros.asm out_of_range "
                        + "deletes x=$0650 when screen x=$0704 rounds the window base to $0680");
    }

    @Test
    public void counterBasedOrbinautUnloadsWithRomOutOfRangeWindow() {
        ObjectSpawn spawn = new ObjectSpawn(0x09E0, 0x06E0, Sonic1ObjectIds.ORBINAUT, 0, 0, true, 0);
        OrbinautRegistry registry = new OrbinautRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
        });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0x0980);

        Sonic1OrbinautBadnikInstance orbinaut = registry.instance;
        assertNotNull(orbinaut, "Reset should preload the Orbinaut before the exec pass");

        GameServices.camera().setX((short) 0x0A81);
        objectManager.update(0x0A81, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(orbinaut),
                "Obj60 Orb_ChkDel uses docs/s1disasm/s1disasm/Macros.asm out_of_range, "
                        + "so x=$09E0 deletes when screen x=$0A81 rounds the window base to $0A00");
    }

    @Test
    public void launchedOrbinautSpikeDeletesWhenPreviousRenderFlagWouldBeClear() throws ReflectiveOperationException {
        Sonic1OrbinautBadnikInstance parent = new Sonic1OrbinautBadnikInstance(
                new ObjectSpawn(0x0100, 0x0060, Sonic1ObjectIds.ORBINAUT, 0, 0, true, 0));
        ObjectInstance spike = newOrbinautSpike(parent);
        setPrivateField(spike, "launched", true);
        setPrivateField(spike, "x", 0x0190);
        setPrivateField(spike, "y", 0x0060);
        setPrivateField(spike, "xVelocity", 0);
        objectManager.addDynamicObject(spike);

        assertTrue(objectManager.getActiveObjects().contains(spike), "Sanity check: launched spike should be active");

        objectManager.update(0, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(spike),
                "Obj60 Orb_ChkDel2 tests obRender after SpeedToPos; BuildSprites clears bit 7 "
                        + "when x=$0190 is beyond the 320px viewport plus width_pixels");
    }

    @Test
    public void numberedBreathingBubbleSurvivesRomAppearAndFlashWindow() {
        GameServices.camera().setX((short) 0x0BA5);
        GameServices.camera().setY((short) 0x058C);
        BreathingBubbleInstance bubble = new BreathingBubbleInstance(
                0x0C2F, 0x05EB, true, 4, "test", new int[] { 13, 18, 17, 16, 15, 14 }, 6, -0x88);
        objectManager.addDynamicObject(bubble);

        for (int frame = 0; frame < 91; frame++) {
            objectManager.update(0x0BA5, null, List.of(), frame);
        }

        assertTrue(objectManager.getActiveObjects().contains(bubble),
                "S1 Obj0A numbered bubbles use Ani_Drown appear/flash scripts before Drown_Delete; "
                        + "the SBZ3 trace keeps the f2762 number bubble in slot $5B through frame 2852");

        objectManager.update(0x0BA5, null, List.of(), 91);

        assertFalse(objectManager.getActiveObjects().contains(bubble),
                "S1 Obj0A numbered bubbles delete after the ROM appear/flash display window completes");
    }

    @Test
    public void counterBasedPostCameraPlacementCatchesUpAfterLargeCameraJump() {
        ObjectSpawn farSpawn = new ObjectSpawn(0x1000, 0x0100, 0x31, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        objectManager = new ObjectManager(List.of(farSpawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
                });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);

        assertEquals(0, registry.createCount,
                "Sanity check: far spawn should not preload into the initial window");

        objectManager.postCameraPlacementUpdate(0x1000);

        assertNotNull(registry.instance,
                "A large post-camera jump should populate the current counter-based window immediately");
        assertTrue(objectManager.getActiveSpawns().contains(farSpawn),
                "Placement active set should catch up to the jumped camera position");
    }

    private static final class TestDynamicObject extends AbstractObjectInstance {
        private TestDynamicObject(ObjectSpawn spawn) {
            super(spawn, "TestDynamicObject");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class SpawnlessFallbackObject implements ObjectInstance {
        private boolean updated;

        @Override
        public ObjectSpawn getSpawn() {
            return null;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updated = true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public void appendDebugRenderCommands(DebugRenderContext ctx) {
        }
    }

    private static final class TrackingRegistry implements ObjectRegistry {
        private TrackingObjectInstance instance;
        private int createCount;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            createCount++;
            instance = new TrackingObjectInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Tracking";
        }
    }

    private static final class TrackingObjectInstance extends AbstractObjectInstance {
        private int updateCount;

        private TrackingObjectInstance(ObjectSpawn spawn) {
            super(spawn, "TrackingObject");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updateCount++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class InRangeButMovedRegistry implements ObjectRegistry {
        private InRangeButMovedObject instance;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            instance = new InRangeButMovedObject(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "InRangeMoved";
        }
    }

    private static final class InRangeButMovedObject extends AbstractObjectInstance {
        private static final int CURRENT_X = 0x0200;

        private InRangeButMovedObject(ObjectSpawn spawn) {
            super(spawn, "InRangeMoved");
        }

        @Override
        public int getX() {
            return CURRENT_X;
        }

        @Override
        public int getOutOfRangeReferenceX() {
            return CURRENT_X;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static ObjectInstance newOrbinautSpike(Sonic1OrbinautBadnikInstance parent)
            throws ReflectiveOperationException {
        Class<?> spikeClass = Class.forName(
                "com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance$OrbSpikeObjectInstance");
        Constructor<?> constructor = spikeClass.getDeclaredConstructor(Sonic1OrbinautBadnikInstance.class, int.class);
        constructor.setAccessible(true);
        return (ObjectInstance) constructor.newInstance(parent, 0x40);
    }

    private static void setPrivateField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class BurrobotRegistry implements ObjectRegistry {
        private Sonic1BurrobotBadnikInstance instance;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            instance = new Sonic1BurrobotBadnikInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Burrobot";
        }
    }

    private static final class OrbinautRegistry implements ObjectRegistry {
        private Sonic1OrbinautBadnikInstance instance;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            instance = new Sonic1OrbinautBadnikInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Orbinaut";
        }
    }
}
