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
 * Rewind spot for {@code Obj_LRZWallRide}: before the capture, mid-ride and after the release, each
 * restore followed by a forward replay.
 *
 * <p>What has to survive is the ride accumulator, a long whose HIGH word is the ride parameter
 * (sonic3k.asm:87843-87844, :87853). A restore that brought back only the low half, or reset it to
 * zero, would keep the rider on the wall and still look plausible frame to frame while placing it
 * at the wrong point of the sweep, so each case asserts the accumulator itself and then replays
 * enough frames to land on the state the uninterrupted timeline reached.
 */
class TestLrzWallRideRewindSpot {

    private static final int BASE_X = 0x1E90;
    private static final int BASE_Y = 0x05E8;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_FLAME_THROWER, 0, 0, false, 0);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x2000, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    /** BEFORE: captured idle, diverged into a ride, restored to idle. */
    @Test
    void wallRideRewindSpotBeforeTheCapture() {
        Harness harness = Harness.create();
        LrzWallRideObjectInstance ride = harness.wallRide();
        TestablePlayableSprite runner = runner();

        CompositeSnapshot before = harness.capture();

        for (int frame = 1; frame <= 6; frame++) {
            ride.update(frame, runner);
        }
        assertTrue(ride.isRidingFor(true), "the diverging timeline started a ride");
        assertTrue(ride.accumulatorFor(true) > 0, "and moved along it");

        harness.restore(before);
        LrzWallRideObjectInstance restored = harness.onlyWallRide();
        assertNotSame(ride, restored, "restore recreates the wall ride");
        assertFalse(restored.isRidingFor(true), "the standing bit restores clear");
        assertEquals(0, restored.accumulatorFor(true), "the accumulator restores to zero");
    }

    /** DURING: captured mid-ride, diverged to the end, restored, replayed the same frame count. */
    @Test
    void wallRideRewindSpotMidRideReplaysToTheSameParameter() {
        Harness harness = Harness.create();
        LrzWallRideObjectInstance ride = harness.wallRide();
        TestablePlayableSprite runner = runner();

        for (int frame = 1; frame <= 5; frame++) {
            ride.update(frame, runner);
        }
        assertTrue(ride.isRidingFor(true), "precondition: mid-ride");
        int midwayAccumulator = ride.accumulatorFor(true);
        int midwayParameter = ride.rideParameter(true);
        assertTrue(midwayParameter > 0 && midwayParameter < 0x100, "precondition: inside the sweep");
        short midwaySpeed = runner.getGSpeed();

        CompositeSnapshot midway = harness.capture();

        for (int frame = 6; frame <= 12; frame++) {
            ride.update(frame, runner);
        }
        int uninterruptedParameter = ride.rideParameter(true);
        boolean uninterruptedRiding = ride.isRidingFor(true);

        harness.restore(midway);
        LrzWallRideObjectInstance restored = harness.onlyWallRide();
        assertEquals(midwayAccumulator, restored.accumulatorFor(true),
                "the whole long restores, not just its high word");
        assertEquals(midwayParameter, restored.rideParameter(true), "the ride parameter restores");
        assertTrue(restored.isRidingFor(true), "the standing bit restores set");

        // ground_vel belongs to the rider, not to the object, so put it back before replaying.
        runner.setGSpeed(midwaySpeed);
        for (int frame = 6; frame <= 12; frame++) {
            restored.update(frame, runner);
        }
        assertEquals(uninterruptedRiding, restored.isRidingFor(true),
                "forward replay reaches the same ride state");
        assertEquals(uninterruptedParameter, restored.rideParameter(true),
                "and the same ride parameter");
    }

    /** AFTER: captured released, diverged by riding again, restored to released. */
    @Test
    void wallRideRewindSpotAfterTheRelease() {
        Harness harness = Harness.create();
        LrzWallRideObjectInstance ride = harness.wallRide();
        TestablePlayableSprite runner = runner();
        runner.setGSpeed((short) 0x1000);

        ride.update(1, runner);
        assertTrue(ride.isRidingFor(true), "precondition: the ride started");
        int frame = 2;
        while (ride.isRidingFor(true) && frame < 400) {
            ride.update(frame++, runner);
        }
        assertFalse(ride.isRidingFor(true), "precondition: the ride has ended");
        int releasedAccumulator = ride.accumulatorFor(true);

        CompositeSnapshot after = harness.capture();

        // Diverge: put the rider back where the wall ride would catch it and let it.
        runner.setCentreX((short) BASE_X);
        runner.setCentreY((short) BASE_Y);
        runner.setAirForTest(false);
        runner.setGSpeed((short) 0x0600);
        runner.setDirection(Direction.RIGHT);
        ride.update(500, runner);

        harness.restore(after);
        LrzWallRideObjectInstance restored = harness.onlyWallRide();
        assertFalse(restored.isRidingFor(true), "the released standing bit restores clear");
        assertEquals(releasedAccumulator, restored.accumulatorFor(true),
                "and the accumulator the release left behind, which the ROM does not clear");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static TestablePlayableSprite runner() {
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

        LrzWallRideObjectInstance wallRide() {
            return objectManager.createDynamicObject(() -> new LrzWallRideObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzWallRideObjectInstance onlyWallRide() {
            List<LrzWallRideObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzWallRideObjectInstance.class && !o.isDestroyed())
                    .map(LrzWallRideObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live wall ride after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x1E00; }
            @Override public short getY() { return 0x0500; }
            @Override public short getWidth() { return 0x140; }
            @Override public short getHeight() { return 0xE0; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
