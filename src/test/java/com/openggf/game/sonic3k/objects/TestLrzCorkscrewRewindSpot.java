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
import com.openggf.physics.Direction;
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
 * Rewind spot for {@code Obj_LRZCorkscrew}: before the capture, mid-ride and after the release,
 * each restore followed by a forward replay.
 *
 * <p>The ride accumulator is the thing worth pinning. It is a long whose high word is the ride
 * parameter (sonic3k.asm:87613-87616), so a restore that brought back only a truncated value, or
 * that reset it to zero, would still look plausible frame to frame while putting the rider at the
 * wrong point of the turn. Each case therefore asserts the accumulator itself and then replays
 * enough frames to land on the state the uninterrupted timeline reached.
 */
class TestLrzCorkscrewRewindSpot {

    private static final int BASE_X = 0x0800;
    private static final int BASE_Y = 0x0600;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_PLAYER_LAUNCHER, 0, 0, false, 72);

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

    /** BEFORE: captured idle, diverged into a ride, restored to idle. */
    @Test
    void corkscrewRewindSpotBeforeTheCapture() {
        Harness harness = Harness.create();
        LrzCorkscrewObjectInstance corkscrew = harness.corkscrew();
        TestablePlayableSprite rider = rider();

        CompositeSnapshot before = harness.capture();

        for (int frame = 1; frame <= 12; frame++) {
            corkscrew.update(frame, rider);
        }
        assertTrue(corkscrew.isRidingFor(true), "the diverging timeline started a ride");

        harness.restore(before);
        LrzCorkscrewObjectInstance restored = harness.onlyCorkscrew();
        assertNotSame(corkscrew, restored, "restore recreates the corkscrew");
        assertFalse(restored.isRidingFor(true), "the standing bit restores clear");
        assertEquals(0, restored.accumulatorFor(true), "the accumulator restores to zero");
    }

    /** DURING: captured mid-ride, diverged to the end, restored, replayed the same frame count. */
    @Test
    void corkscrewRewindSpotMidRideReplaysToTheSameParameter() {
        Harness harness = Harness.create();
        LrzCorkscrewObjectInstance corkscrew = harness.corkscrew();
        TestablePlayableSprite rider = rider();

        for (int frame = 1; frame <= 20; frame++) {
            corkscrew.update(frame, rider);
        }
        assertTrue(corkscrew.isRidingFor(true), "precondition: mid-ride");
        int midwayAccumulator = corkscrew.accumulatorFor(true);
        int midwayParameter = corkscrew.rideParameter(true);
        assertTrue(midwayParameter > 0 && midwayParameter < 0x700, "precondition: inside the turn");
        short midwaySpeed = rider.getGSpeed();

        CompositeSnapshot midway = harness.capture();

        for (int frame = 21; frame <= 60; frame++) {
            corkscrew.update(frame, rider);
        }
        int uninterruptedParameter = corkscrew.rideParameter(true);

        harness.restore(midway);
        LrzCorkscrewObjectInstance restored = harness.onlyCorkscrew();
        assertEquals(midwayAccumulator, restored.accumulatorFor(true),
                "the whole long restores, not just its high word");
        assertEquals(midwayParameter, restored.rideParameter(true), "the ride parameter restores");
        assertTrue(restored.isRidingFor(true), "the standing bit restores set");

        // The rider's own ground_vel is not the object's state, so put it back before replaying.
        rider.setGSpeed(midwaySpeed);
        for (int frame = 21; frame <= 60; frame++) {
            restored.update(frame, rider);
        }
        assertEquals(uninterruptedParameter, restored.rideParameter(true),
                "forward replay lands on the uninterrupted ride parameter");
    }

    /** AFTER: captured released, diverged by riding again, restored to released. */
    @Test
    void corkscrewRewindSpotAfterTheRelease() {
        Harness harness = Harness.create();
        LrzCorkscrewObjectInstance corkscrew = harness.corkscrew();
        TestablePlayableSprite rider = rider();
        rider.setGSpeed((short) 0x1000);

        corkscrew.update(1, rider);
        assertTrue(corkscrew.isRidingFor(true), "precondition: the ride started");
        int frame = 2;
        while (corkscrew.isRidingFor(true) && frame < 400) {
            corkscrew.update(frame++, rider);
        }
        assertFalse(corkscrew.isRidingFor(true), "precondition: the ride has ended");
        assertTrue(rider.getGSpeed() < 0, "precondition: the rider was turned around");

        CompositeSnapshot after = harness.capture();

        // Diverge: put the rider back in a state the corkscrew would catch and let it.
        rider.setGSpeed((short) 0x0600);
        rider.setCentreX((short) BASE_X);
        rider.setCentreY((short) BASE_Y);
        rider.setAirForTest(false);
        rider.setDirection(Direction.RIGHT);
        corkscrew.update(500, rider);

        harness.restore(after);
        LrzCorkscrewObjectInstance restored = harness.onlyCorkscrew();
        assertFalse(restored.isRidingFor(true), "the released standing bit restores clear");
        assertEquals(0, restored.accumulatorFor(true),
                "the release zeroed the accumulator and the restore keeps it zeroed");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static TestablePlayableSprite rider() {
        TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) BASE_X, (short) BASE_Y);
        player.setCentreX((short) BASE_X);
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        player.setGSpeed((short) 0x0600);
        player.setDirection(Direction.RIGHT);
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

        LrzCorkscrewObjectInstance corkscrew() {
            return objectManager.createDynamicObject(() -> new LrzCorkscrewObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzCorkscrewObjectInstance onlyCorkscrew() {
            List<LrzCorkscrewObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzCorkscrewObjectInstance.class && !o.isDestroyed())
                    .map(LrzCorkscrewObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live corkscrew after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x1000; }
            @Override public short getHeight() { return 0x1000; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
