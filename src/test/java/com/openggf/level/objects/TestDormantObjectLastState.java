package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.camera.CameraPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NATIVE widescreen last states: an unloaded stationary object's last render state replaces
 * its frozen pose; moving objects keep the spawn pose; the derived cache never outlives a
 * reset or a rewind restore and recording it runs no object logic.
 */
class TestDormantObjectLastState {
    private static final ObjectSpawn STILL = new ObjectSpawn(0x400, 0x100, 0x31, 0, 0, false, 0x100, 0);
    private static final ObjectSpawn WALKER = new ObjectSpawn(0x440, 0x100, 0x32, 0, 0, false, 0x100, 1);

    private SonicConfigurationService configuration;
    private Camera camera;
    private ObjectManager manager;
    private final List<TestObject> created = new ArrayList<>();

    /** Counts logic and draw calls so recording can prove it ran neither. */
    private static class TestObject extends AbstractObjectInstance {
        final int step;
        int x;
        int updates;
        int draws;

        TestObject(ObjectSpawn spawn, int step) {
            super(spawn, "dormant-last-state");
            this.step = step;
            this.x = spawn.x();
        }

        @Override public int getX() { return x; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { updates++; x += step; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { draws++; }
    }

    @BeforeEach
    void setUp() {
        configuration = SonicConfigurationService.createStandalone();
        configuration.resetToDefaults();
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9");
        configuration.setConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE, "NATIVE");
        configuration.resolveDisplayAspect();
        camera = new Camera(configuration);
        camera.resetState();
        ObjectServices services = new StubObjectServices() {
            @Override public Camera camera() { return camera; }
            @Override public SonicConfigurationService configuration() { return configuration; }
        };
        ObjectRegistry registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                var object = new TestObject(spawn, spawn == WALKER ? 1 : 0);
                created.add(object);
                return object;
            }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "dormant-last-state"; }
        };
        manager = new ObjectManager(List.of(STILL, WALKER), registry, 0, null, null, null, camera, services);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    private void step(int cameraX, int frame) {
        camera.setX((short) cameraX);
        AbstractObjectInstance.updateCameraBounds(cameraX, 0, cameraX + camera.getWidth(), 224, 0);
        manager.update(cameraX, null, null, frame);
    }

    /** Loads both objects at the camera, then walks the camera far right until both unload. */
    private void loadThenUnload() {
        assertEquals(320, camera.getWidth());
        assertTrue(CameraPresentationAccess.hasMargin(camera), "32:9 NATIVE presents a margin");
        manager.prepareDormantPresentation(null); // no registry: no poses, but the layout is current
        manager.reset(0x300);
        for (int frame = 1; frame <= 4; frame++) step(0x300, frame);
        assertEquals(2, manager.getActiveObjects().size(), "both objects are live");
        step(0x1400, 5);
        assertTrue(manager.getActiveObjects().isEmpty(), "both objects unloaded");
    }

    @Test
    void unloadRecordsTheStationaryObjectOnlyWithoutRunningItsLogic() {
        loadThenUnload();
        assertEquals(1, manager.dormantLayer.lastStateCount(), "only the stationary object keeps a last state");
        TestObject still = created.stream().filter(o -> o.getSpawn() == STILL).findFirst().orElseThrow();
        int updates = still.updates;
        assertEquals(0, still.draws, "recording a last state draws nothing");

        // A frozen pose for the still object is replaced by its recorded last state.
        var frozen = new DormantObjectLayer.Frame(new TestObject(STILL, 0), new List[0]);
        var pose = new DormantObjectLayer.Pose(STILL, new DormantObjectLayer.Frame[]{frozen}, new int[]{0});
        assertSame(still, manager.dormantLayer.frameFor(pose).instance());
        var walkerPose = new DormantObjectLayer.Pose(WALKER, new DormantObjectLayer.Frame[]{frozen}, new int[]{0});
        assertSame(frozen, manager.dormantLayer.frameFor(walkerPose), "a moving object returns to its spawn pose");
        // A looping pose keeps looping even with a last state.
        var second = new DormantObjectLayer.Frame(new TestObject(STILL, 0), new List[0]);
        var looped = new DormantObjectLayer.Pose(STILL, new DormantObjectLayer.Frame[]{frozen, second}, new int[]{0, 1});
        var drawn = manager.dormantLayer.frameFor(looped);
        assertTrue(drawn == frozen || drawn == second);
        assertEquals(updates, still.updates, "recording ran no object logic");
    }

    @Test
    void resetForgetsLastStates() {
        loadThenUnload();
        assertEquals(1, manager.dormantLayer.lastStateCount());
        manager.reset(0);
        assertEquals(0, manager.dormantLayer.lastStateCount());
    }

    @Test
    void rewindRestoreForgetsLastStatesRecordedAfterTheSnapshot() {
        manager.prepareDormantPresentation(null);
        manager.reset(0x300);
        for (int frame = 1; frame <= 4; frame++) step(0x300, frame);
        var rewind = manager.rewindSnapshottable();
        var saved = rewind.capture();
        step(0x1400, 5);
        assertEquals(1, manager.dormantLayer.lastStateCount());
        rewind.restore(saved);
        assertEquals(0, manager.dormantLayer.lastStateCount(), "a restored past cannot show a future last state");
        assertFalse(manager.getActiveObjects().isEmpty(), "the restore brought the live objects back");
    }

    @Test
    void nothingIsRecordedWithoutAPresentationMargin() {
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        configuration.resolveDisplayAspect();
        camera.refreshViewportDimensions(configuration);
        assertFalse(CameraPresentationAccess.hasMargin(camera));
        manager.prepareDormantPresentation(null);
        manager.reset(0x300);
        for (int frame = 1; frame <= 4; frame++) step(0x300, frame);
        step(0x1400, 5);
        assertEquals(0, manager.dormantLayer.lastStateCount(), "NATIVE_4_3 records no presentation state");
    }
}
