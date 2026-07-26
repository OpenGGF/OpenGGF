package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestObjectManagerInitialS3kSetupPass {

    private final List<String> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void coordinatorOwnedDynamicStageLoadsExecutesAndFlushesWithoutGameplayState() {
        Camera camera = new Camera(SonicConfigurationService.getInstance());
        camera.setX((short) 0);
        camera.setY((short) 0);
        camera.setMinY((short) 0);

        ObjectSpawn visible = new ObjectSpawn(0x0100, 0x0080, 0x41, 0, 0, false, 0x0080);
        ObjectSpawn hidden = new ObjectSpawn(0x3000, 0x0080, 0x42, 0, 0, false, 0x0080);
        RecordingRegistry registry = new RecordingRegistry(visible, hidden, false);
        RecordingSolidRegistry solids = new RecordingSolidRegistry();
        TouchResponseTable touchTable = mock(TouchResponseTable.class);
        when(touchTable.getWidthRadius(anyInt())).thenReturn(16);
        when(touchTable.getHeightRadius(anyInt())).thenReturn(16);
        TestObjectServices services = new TestObjectServices()
                .withCamera(camera)
                .withDebugOverlay(mock(DebugOverlayManager.class))
                .withSolidExecutionRegistry(solids);
        ObjectManager manager = new ObjectManager(
                List.of(visible, hidden), registry, -1, null, touchTable,
                null, camera, services);
        registry.manager = manager;
        manager.enableExecThenLoadPlacement();

        Sonic player = new Sonic("sonic", (short) 0x100, (short) 0x80);
        Tails sidekick = new Tails("tails", (short) 0x120, (short) 0x90);
        sidekick.setCpuControlled(true);
        new SidekickCpuController(sidekick, player);
        int frameBefore = manager.getFrameCounter();
        int vblankBefore = manager.getVblaCounter();

        try (InitialObjectDispatchScope scope =
                     manager.beginInitialProcessSprites(0, player, List.of(sidekick))) {
            manager.loadInitialDynamicSlots(scope);
            manager.processInitialDynamicSlots(scope);
            manager.finishInitialProcessSprites(scope);
        }

        ProbeObject probe = registry.instances.get(visible);
        assertEquals(1, registry.visibleCreations);
        assertEquals(0, registry.hiddenCreations);
        assertEquals(1, probe.updates);
        assertEquals(frameBefore + 1, manager.getFrameCounter());
        assertEquals(vblankBefore, manager.getVblaCounter());
        assertTrue(manager.getActiveObjects().contains(probe));
        assertTrue(manager.getActiveObjects().contains(probe.child),
                "post-exec allocations must be materialized before the pass returns");
        manager.snapshotTouchResponseState(true);
        assertTrue(manager.touchUsesPreviousCollisionResponseList());
        assertEquals(List.of(probe, probe.child), manager.getTouchResponseObjects(),
                "the setup pass must capture the post-flush collision list for the following frame");
        assertEquals(0, probe.touches, "the setup pass must not run touch responses");
        assertEquals(List.of(
                "SolidExecutionRegistry.beginFrame",
                "SolidExecutionRegistry.finishFrame"), ledger);

        manager.runTouchResponsesForPlayer(player, 99);
        assertEquals(1, probe.touches,
                "the touch probe must be live, proving the setup pass specifically skipped it");
    }

    @Test
    void coordinatorOwnedDynamicStageBalancesRegistryAndPropagatesFailureWithoutRetry() {
        Camera camera = new Camera(SonicConfigurationService.getInstance());
        camera.setX((short) 0);
        camera.setY((short) 0);
        camera.setMinY((short) 0);
        ObjectSpawn visible = new ObjectSpawn(0x0100, 0x0080, 0x41, 0, 0, false, 0x0080);
        RecordingRegistry registry = new RecordingRegistry(visible, null, true);
        RecordingSolidRegistry solids = new RecordingSolidRegistry();
        ObjectManager manager = new ObjectManager(
                List.of(visible), registry, -1, null, null, null, camera,
                new TestObjectServices().withCamera(camera).withSolidExecutionRegistry(solids));
        registry.manager = manager;
        manager.enableExecThenLoadPlacement();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (InitialObjectDispatchScope scope =
                         manager.beginInitialProcessSprites(0, null, List.<PlayableEntity>of())) {
                manager.loadInitialDynamicSlots(scope);
                manager.processInitialDynamicSlots(scope);
                manager.finishInitialProcessSprites(scope);
            }
        });

        assertEquals("setup boom", failure.getMessage());
        assertEquals(1, registry.instances.get(visible).updates);
        assertEquals(1, solids.beginCount);
        assertEquals(1, solids.finishCount);
        assertEquals(0, registry.instances.get(visible).touches);
        manager.snapshotTouchResponseState(true);
        assertTrue(manager.getTouchResponseObjects().isEmpty(),
                "an exceptional setup pass must not publish a partial following-frame collision list");
        assertEquals(List.of(
                "SolidExecutionRegistry.beginFrame",
                "SolidExecutionRegistry.finishFrame"), ledger);
    }

    private final class RecordingSolidRegistry implements SolidExecutionRegistry {
        int beginCount;
        int finishCount;

        @Override
        public void beginFrame(int frameCounter, List<? extends PlayableEntity> players) {
            beginCount++;
            ledger.add("SolidExecutionRegistry.beginFrame");
        }

        @Override public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {}
        @Override public ObjectSolidExecutionContext currentObject() { return null; }
        @Override public PlayerStandingState previousStanding(ObjectInstance object, PlayableEntity player) {
            return PlayerStandingState.NONE;
        }
        @Override public void publishCheckpoint(SolidCheckpointBatch batch) {}
        @Override public void endObject(ObjectInstance object) {}

        @Override
        public void finishFrame() {
            finishCount++;
            ledger.add("SolidExecutionRegistry.finishFrame");
        }

        @Override public void clearTransientState() {}
    }

    private static final class RecordingRegistry implements ObjectRegistry {
        private final ObjectSpawn visible;
        private final ObjectSpawn hidden;
        private final boolean throwsOnUpdate;
        private final Map<ObjectSpawn, ProbeObject> instances = new IdentityHashMap<>();
        private int visibleCreations;
        private int hiddenCreations;
        private ObjectManager manager;

        private RecordingRegistry(ObjectSpawn visible, ObjectSpawn hidden, boolean throwsOnUpdate) {
            this.visible = visible;
            this.hidden = hidden;
            this.throwsOnUpdate = throwsOnUpdate;
        }

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            if (spawn == visible) visibleCreations++;
            if (spawn == hidden) hiddenCreations++;
            ProbeObject instance = new ProbeObject(spawn, throwsOnUpdate);
            instance.managerSupplier = () -> manager;
            instances.put(spawn, instance);
            return instance;
        }

        @Override public void reportCoverage(List<ObjectSpawn> spawns) {}
        @Override public String getPrimaryName(int objectId) { return "Probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    }

    private static final class ProbeObject extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener {
        private final boolean throwsOnUpdate;
        private java.util.function.Supplier<ObjectManager> managerSupplier;
        private int updates;
        private int touches;
        private ProbeObject child;

        private ProbeObject(ObjectSpawn spawn, boolean throwsOnUpdate) {
            super(spawn, "SetupProbe");
            this.throwsOnUpdate = throwsOnUpdate;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updates++;
            if (throwsOnUpdate) {
                throw new IllegalStateException("setup boom");
            }
            if (child == null) {
                child = new ProbeObject(
                        new ObjectSpawn(getX(), getY(), 0, 0, 0, false, getY()), false);
                managerSupplier.get().queueDynamicObjectAfterExec(child);
            }
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            touches++;
        }

        @Override public int getCollisionFlags() { return 0xC7; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
    }
}
