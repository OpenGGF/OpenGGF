package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind spot for {@code Obj_LRZFallingSpike}: before the trigger, on the frame the drop starts,
 * and part-way down with a forward replay.
 *
 * <p>Both the routine and the fall's subpixel position have to come back. A restore that brought
 * the phase back but not the accumulated {@code y_pos} would put the spike at the top of its drop
 * again, and one that brought the position back but not the phase would leave it hanging in mid-air
 * and harmful forever, so each case asserts both.
 *
 * <p><b>Owed:</b> a spot on the landing boundary itself. Landing needs
 * {@code ObjCheckFloorDist} to find real terrain, which this object-only harness has none of; it
 * belongs with the cold-route spot for the {@code ($280,$504)} placement.
 */
class TestLrzFallingSpikeRewindSpot {

    private static final int BASE_X = 0x0280;
    private static final int BASE_Y = 0x0504;
    private static final int SUBTYPE = 4;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, SUBTYPE, 0, false, 0);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x1000, 0x1000, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    /** BEFORE: captured waiting, diverged into a drop, restored to waiting. */
    @Test
    void fallingSpikeRewindSpotBeforeTheTrigger() {
        Harness harness = Harness.create();
        LrzFallingSpikeObjectInstance spike = harness.spike();

        CompositeSnapshot before = harness.capture();

        for (int frame = 1; frame <= 40; frame++) {
            spike.update(frame, playerAt(BASE_X));
        }
        assertTrue(spike.isFalling(), "the diverging timeline released it");
        assertTrue(spike.getCentreY() > BASE_Y, "and it has dropped");

        harness.restore(before);
        LrzFallingSpikeObjectInstance restored = harness.onlySpike();
        assertNotSame(spike, restored, "restore recreates the spike");
        assertTrue(restored.isWaiting(), "the routine restores to the waiting one");
        assertEquals(BASE_Y, restored.getCentreY(), "and the spike is back where it hung");
        assertEquals(0x82, restored.getCollisionFlags(), "still harmful");
    }

    /** ON THE TRIGGER FRAME: captured the frame the routine flips, replayed to the same drop. */
    @Test
    void fallingSpikeRewindSpotOnTheTriggerFrame() {
        Harness harness = Harness.create();
        LrzFallingSpikeObjectInstance spike = harness.spike();

        spike.update(1, playerAt(BASE_X));
        assertTrue(spike.isFalling(), "precondition: the trigger fired on frame 1");
        assertEquals(BASE_Y, spike.getCentreY(), "and nothing has moved yet");

        CompositeSnapshot triggered = harness.capture();

        for (int frame = 2; frame <= 30; frame++) {
            spike.update(frame, playerAt(BASE_X));
        }
        int uninterruptedY = spike.getCentreY();

        harness.restore(triggered);
        LrzFallingSpikeObjectInstance restored = harness.onlySpike();
        assertTrue(restored.isFalling(), "the flipped routine restores");
        assertEquals(BASE_Y, restored.getCentreY());

        for (int frame = 2; frame <= 30; frame++) {
            restored.update(frame, playerAt(BASE_X));
        }
        assertEquals(uninterruptedY, restored.getCentreY(),
                "forward replay lands on the uninterrupted position");
    }

    /** DURING: captured part-way down, diverged further, restored, replayed the same frames. */
    @Test
    void fallingSpikeRewindSpotMidDropReplaysToTheSamePosition() {
        Harness harness = Harness.create();
        LrzFallingSpikeObjectInstance spike = harness.spike();

        for (int frame = 1; frame <= 20; frame++) {
            spike.update(frame, playerAt(BASE_X));
        }
        int midwayY = spike.getCentreY();
        assertTrue(midwayY > BASE_Y, "precondition: part-way down");

        CompositeSnapshot midway = harness.capture();

        for (int frame = 21; frame <= 50; frame++) {
            spike.update(frame, playerAt(BASE_X));
        }
        int uninterruptedY = spike.getCentreY();
        assertTrue(uninterruptedY > midwayY, "precondition: the timeline kept falling");

        harness.restore(midway);
        LrzFallingSpikeObjectInstance restored = harness.onlySpike();
        assertTrue(restored.isFalling());
        assertEquals(midwayY, restored.getCentreY(), "the drop's position restores");

        for (int frame = 21; frame <= 50; frame++) {
            restored.update(frame, playerAt(BASE_X));
        }
        assertEquals(uninterruptedY, restored.getCentreY(),
                "forward replay lands on the uninterrupted position, so the subpixel"
                        + " accumulator and y_vel came back too");
        assertFalse(restored.isLanded(), "this harness has no terrain to land on");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static TestablePlayableSprite playerAt(int x) {
        TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) x, (short) (BASE_Y + 0x40));
        player.setCentreX((short) x);
        player.setCentreY((short) (BASE_Y + 0x40));
        player.setAirForTest(false);
        return player;
    }

    private record Harness(ObjectManager objectManager, RewindRegistry registry) {

        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);

            RewindRegistry registry = new RewindRegistry();
            registry.register(objectManager.rewindSnapshottable());
            return new Harness(objectManager, registry);
        }

        LrzFallingSpikeObjectInstance spike() {
            return objectManager.createDynamicObject(
                    () -> new LrzFallingSpikeObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzFallingSpikeObjectInstance onlySpike() {
            List<LrzFallingSpikeObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzFallingSpikeObjectInstance.class
                            && !o.isDestroyed())
                    .map(LrzFallingSpikeObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live spike after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x0200; }
            @Override public short getY() { return 0x0480; }
            @Override public short getWidth() { return 0x140; }
            @Override public short getHeight() { return 0xE0; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
