package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dash elevator's owed mid-ride rewind spot: before the rider latches, part-way down the
 * shaft, and at the travel clamp.
 *
 * <p>{@code $32(a0)} is a 16.16 position whose low word never reaches the drawn frame -- the
 * platform is placed on {@code $46(a0) + ($32 >> 16)} -- so two timelines can share a pixel and
 * separate up to seven frames later. Each case therefore asserts the whole {@code $32} and then
 * replays the same frames on both sides.
 */
class TestLrzDashElevatorRewindSpot {

    private static final int OBJECT_X = 0x1000;
    private static final int BASE_Y = 0x0600;
    /** {@code (subtype & $7F) * 8}: a {@code $100}-pixel shaft (sonic3k.asm:88391-88393). */
    private static final int SUBTYPE = 0x20;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            OBJECT_X, BASE_Y, Sonic3kObjectIds.LBZ_SPIN_LAUNCHER, SUBTYPE, 0, false, 0);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x4000, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    /** BEFORE: captured unlatched, diverged by a ride, restored to the top of the shaft. */
    @Test
    void dashElevatorRewindSpotBeforeTheRideStarts() {
        Harness harness = Harness.create();
        LrzDashElevatorObjectInstance elevator = harness.elevator();
        TestablePlayableSprite player = rider(elevator);

        CompositeSnapshot before = harness.capture();

        latch(elevator, player);
        ride(elevator, player, 40);
        assertTrue(elevator.position() > 0, "the diverging timeline moved the platform");

        harness.restore(before);
        LrzDashElevatorObjectInstance restored = harness.only();
        assertNotSame(elevator, restored, "restore recreates the platform");
        assertEquals(0, restored.position(), "$32(a0) restores to the placed position");
        assertEquals(BASE_Y, restored.getCentreY());
    }

    /** DURING: captured mid-shaft, diverged to the clamp, restored, replayed the same frames. */
    @Test
    void dashElevatorRewindSpotMidRideReplaysToTheSamePosition() {
        Harness harness = Harness.create();
        LrzDashElevatorObjectInstance elevator = harness.elevator();
        TestablePlayableSprite player = rider(elevator);

        latch(elevator, player);
        ride(elevator, player, 37);
        int capturedPosition = elevator.position();
        int capturedY = elevator.getCentreY();
        assertTrue(capturedPosition > 0 && capturedPosition < elevator.maxPosition(),
                "precondition: inside the shaft");

        CompositeSnapshot midway = harness.capture();

        ride(elevator, player, 400);
        int uninterruptedPosition = elevator.position();
        assertEquals(elevator.maxPosition(), uninterruptedPosition,
                "precondition: the uninterrupted timeline clamped at $34(a0)");

        harness.restore(midway);
        LrzDashElevatorObjectInstance restored = harness.only();
        assertEquals(capturedPosition, restored.position(),
                "the whole 16.16 position restores, not a value re-derived from y_pos");
        assertEquals(capturedY, restored.getCentreY());
        assertNotEquals(capturedPosition, uninterruptedPosition,
                "the two sampled states differ, so the comparison could have disagreed");

        // The rider is not part of the object's blob, so re-latch before replaying.
        latch(restored, player);
        ride(restored, player, 400);
        assertEquals(uninterruptedPosition, restored.position(), "forward replay clamps the same");
    }

    /** AFTER: captured at the clamp, diverged by climbing back, restored to the clamp. */
    @Test
    void dashElevatorRewindSpotAtTheTravelClamp() {
        Harness harness = Harness.create();
        LrzDashElevatorObjectInstance elevator = harness.elevator();
        TestablePlayableSprite player = rider(elevator);

        latch(elevator, player);
        ride(elevator, player, 400);
        assertEquals(elevator.maxPosition(), elevator.position(), "precondition: clamped");
        int clampedY = elevator.getCentreY();

        CompositeSnapshot after = harness.capture();

        player.setDirection(Direction.RIGHT);
        ride(elevator, player, 60);
        assertTrue(elevator.position() < elevator.maxPosition(),
                "the diverging timeline climbed back");

        harness.restore(after);
        LrzDashElevatorObjectInstance restored = harness.only();
        assertEquals(elevator.maxPosition(), restored.position(), "the clamp restores");
        assertEquals(clampedY, restored.getCentreY());
    }

    // ----- harness ------------------------------------------------------------------------------

    /** {@code Obj_LRZDashElevator} latches a rider only while its {@code anim} is 9 (:88415). */
    private static void latch(LrzDashElevatorObjectInstance elevator, TestablePlayableSprite player) {
        player.setAirForTest(false);
        player.setAnimationId(Sonic3kAnimationIds.SPINDASH.id());
        elevator.onSolidContact(player, standingContact(), 0);
        elevator.update(1, player);
    }

    private static void ride(LrzDashElevatorObjectInstance elevator,
                             TestablePlayableSprite player, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            elevator.onSolidContact(player, standingContact(), frame);
            elevator.update(frame, player);
        }
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static TestablePlayableSprite rider(LrzDashElevatorObjectInstance elevator) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) elevator.getCentreX(), (short) BASE_Y);
        player.setCentreX((short) elevator.getCentreX());
        player.setCentreY((short) BASE_Y);
        player.setAirForTest(false);
        player.setDirection(Direction.LEFT);
        player.setSpindashCounter((short) 0);
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
                    List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objectManager;
            objectManager.reset(0);
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);
            RewindRegistry registry = new RewindRegistry();
            registry.register(objectManager.rewindSnapshottable());
            return new Harness(objectManager, registry);
        }

        LrzDashElevatorObjectInstance elevator() {
            return objectManager.createDynamicObject(
                    () -> new LrzDashElevatorObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzDashElevatorObjectInstance only() {
            List<LrzDashElevatorObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzDashElevatorObjectInstance.class
                            && !o.isDestroyed())
                    .map(LrzDashElevatorObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live dash elevator after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x4000; }
            @Override public short getHeight() { return 0x4000; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
