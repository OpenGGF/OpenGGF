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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind spot for {@code Obj_LRZSmashingSpikePlatform}: before the fall has gone far, part-way
 * through the thirty-frame smash hold, and part-way back up.
 *
 * <p>None of the three states is recoverable from the block's drawn position.
 * <ul>
 *   <li>Falling, {@code y_vel(a0)} is a separate accumulator, so two timelines can share a
 *       {@code y_pos} and then move apart at different rates.</li>
 *   <li>Holding, the block does not move at all for thirty frames while {@code $3A(a0)} and
 *       {@code anim_frame(a0)} advance under it.</li>
 *   <li>Rising, {@code mapping_frame} flickers 8 / 0 on a parity no position carries.</li>
 * </ul>
 * Each case therefore asserts the fields and then replays forward to the state the uninterrupted
 * timeline reached.
 */
class TestLrzSmashingSpikePlatformRewindSpot {

    private static final int BASE_X = 0x0B20;
    private static final int BASE_Y = 0x0640;
    /** {@code $09 << 3} = 72 pixels, the shortest of Lava Reef's ten act 1 subtypes. */
    private static final int SUBTYPE = 0x09;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_GATE_LASER, SUBTYPE, 0, false, 0);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x2000, 0x2000, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    /** BEFORE: captured ten frames into the fall, diverged to the slam, restored mid-fall. */
    @Test
    void smashingSpikePlatformRewindSpotDuringTheFall() {
        Harness harness = Harness.create();
        LrzSmashingSpikePlatformObjectInstance block = harness.block();

        run(block, 1, 10);
        int capturedPixels = block.offsetPixels();
        int capturedVel = block.yVel();
        assertEquals(10 * 9 / 8, capturedPixels, "precondition: part-way down");
        assertEquals(10 * 0x40, capturedVel, "precondition: the accumulator is ahead of the pixels");

        CompositeSnapshot mid = harness.capture();

        run(block, 11, 40);
        assertTrue(block.smashed(), "the diverging timeline landed and started holding");

        harness.restore(mid);
        LrzSmashingSpikePlatformObjectInstance restored = harness.onlyBlock();
        assertNotSame(block, restored, "restore recreates the block");
        assertEquals(capturedPixels, restored.offsetPixels());
        assertEquals(capturedVel, restored.yVel(), "y_vel restores, not a rate re-derived from y_pos");
        assertEquals(false, restored.smashed());

        run(restored, 11, 25);
        assertTrue(restored.smashed(), "forward replay lands on the same frame as the first run");
        assertEquals(0x48, restored.offsetPixels());
        assertEquals(30, restored.holdTimer());
    }

    /** DURING: captured inside the smash hold, where the block is motionless. */
    @Test
    void smashingSpikePlatformRewindSpotInsideTheSmashHold() {
        Harness harness = Harness.create();
        LrzSmashingSpikePlatformObjectInstance block = harness.block();

        run(block, 1, 25);
        run(block, 26, 30);
        int heldY = block.getCentreY();
        assertEquals(25, block.holdTimer(), "precondition: five hold frames consumed");
        assertEquals(7, block.mappingFrame(), "precondition: RawAni_43196 entry 4");

        CompositeSnapshot held = harness.capture();

        run(block, 31, 200);
        int uninterruptedPixels = block.offsetPixels();
        int uninterruptedFrame = block.mappingFrame();

        harness.restore(held);
        LrzSmashingSpikePlatformObjectInstance restored = harness.onlyBlock();
        assertEquals(25, restored.holdTimer(), "$3A(a0) restores from the blob, not from y_pos");
        assertEquals(7, restored.mappingFrame());
        assertEquals(heldY, restored.getCentreY());
        assertTrue(restored.smashed());

        run(restored, 31, 200);
        assertEquals(uninterruptedPixels, restored.offsetPixels(), "forward replay matches");
        assertEquals(uninterruptedFrame, restored.mappingFrame());
    }

    /** AFTER: captured part-way back up, where only the 8 / 0 flicker parity says where it is. */
    @Test
    void smashingSpikePlatformRewindSpotDuringTheRise() {
        Harness harness = Harness.create();
        LrzSmashingSpikePlatformObjectInstance block = harness.block();

        run(block, 1, 55);
        assertEquals(0, block.holdTimer(), "precondition: the hold is spent");
        run(block, 56, 66);
        int risenPixels = block.offsetPixels();
        int risenFrame = block.mappingFrame();
        assertEquals(0x48 - 11, risenPixels, "precondition: eleven rise frames");

        CompositeSnapshot rising = harness.capture();

        run(block, 67, 400);
        int uninterruptedPixels = block.offsetPixels();
        boolean uninterruptedSmashed = block.smashed();

        harness.restore(rising);
        LrzSmashingSpikePlatformObjectInstance restored = harness.onlyBlock();
        assertEquals(risenPixels, restored.offsetPixels());
        assertEquals(risenFrame, restored.mappingFrame(), "the flicker parity restores");
        assertEquals(0, restored.holdTimer());
        assertTrue(restored.smashed());

        run(restored, 67, 400);
        assertEquals(uninterruptedPixels, restored.offsetPixels(), "forward replay matches");
        assertEquals(uninterruptedSmashed, restored.smashed());
    }

    // ----- harness ------------------------------------------------------------------------------

    private static void run(LrzSmashingSpikePlatformObjectInstance block, int first, int last) {
        for (int frame = first; frame <= last; frame++) {
            block.update(frame, null);
        }
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

        LrzSmashingSpikePlatformObjectInstance block() {
            return objectManager.createDynamicObject(
                    () -> new LrzSmashingSpikePlatformObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzSmashingSpikePlatformObjectInstance onlyBlock() {
            List<LrzSmashingSpikePlatformObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzSmashingSpikePlatformObjectInstance.class
                            && !o.isDestroyed())
                    .map(LrzSmashingSpikePlatformObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live smashing spike platform after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x2000; }
            @Override public short getHeight() { return 0x2000; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
