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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Rewind spots for the three slice 3c hazards whose only state is a small free-running counter:
 * {@code $1B Obj_LRZFireballLauncher}, {@code $1F Obj_LRZLavaFall} and
 * {@code $20 Obj_LRZSwingingSpikeBall}.
 *
 * <p>{@link com.openggf.game.rewind.TestEveryObjectRewindRoundTrip} proves each class round-trips
 * its blob, which is a different claim: it never runs the object. These cases capture mid-cycle,
 * diverge the live timeline, restore, and then replay the SAME number of frames on both sides and
 * compare. That is the claim that matters, because all three counters are invisible in the drawn
 * frame -- a launcher one frame from firing and one that has just fired look identical, the lava
 * fall's {@code $2E} decides whether the next drop carries the sound, and the spike ball's angle
 * repeats every 128 frames, so two timelines can share a ball position and diverge afterwards.
 */
class TestLrzHazardRewindSpots {

    private static final int BASE_X = 0x1200;
    private static final int BASE_Y = 0x0500;

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

    /** {@code $1B}: {@code $2E(a0)} counts down and reloads from {@code $30(a0)} (:88165-88168). */
    @Test
    void fireballLauncherRewindSpot() {
        // Subtype $34: (subtype & $F0) >> 2 = $D * 4 = a 52-frame period (sonic3k.asm:88159-88163).
        Harness<LrzFireballLauncherObjectInstance> harness = Harness.create(
                () -> new LrzFireballLauncherObjectInstance(spawn(0x34)),
                LrzFireballLauncherObjectInstance.class);
        LrzFireballLauncherObjectInstance launcher = harness.object();

        run(launcher, 1, 20);
        int capturedCountdown = launcher.countdown();
        CompositeSnapshot mid = harness.capture();

        run(launcher, 21, 90);
        int uninterrupted = launcher.countdown();
        assertNotEquals(capturedCountdown, uninterrupted, "the diverging timeline moved on");

        harness.restore(mid);
        LrzFireballLauncherObjectInstance restored = harness.only();
        assertNotSame(launcher, restored, "restore recreates the launcher");
        assertEquals(capturedCountdown, restored.countdown(), "$2E(a0) restores");
        assertEquals(launcher.period(), restored.period(), "$30(a0) restores");

        run(restored, 21, 90);
        assertEquals(uninterrupted, restored.countdown(), "forward replay lands on the same $2E");
    }

    /** {@code $1F}: the four-second cycle, the six-frame drop timer and the sound alternator. */
    @Test
    void lavaFallRewindSpot() {
        Harness<LrzLavaFallObjectInstance> harness = Harness.create(
                () -> new LrzLavaFallObjectInstance(spawn(0x00)),
                LrzLavaFallObjectInstance.class);
        LrzLavaFallObjectInstance fall = harness.object();

        run(fall, 1, 45);
        int capturedDrop = fall.dropTimer();
        int capturedAlternator = fall.soundAlternator();
        CompositeSnapshot mid = harness.capture();

        run(fall, 46, 200);
        int uninterruptedDrop = fall.dropTimer();
        int uninterruptedAlternator = fall.soundAlternator();

        harness.restore(mid);
        LrzLavaFallObjectInstance restored = harness.only();
        assertEquals(capturedDrop, restored.dropTimer(), "$2E(a0) restores");
        assertEquals(capturedAlternator, restored.soundAlternator(), "the alternator restores");
        assertEquals(fall.clockThreshold(), restored.clockThreshold());

        run(restored, 46, 200);
        assertEquals(uninterruptedDrop, restored.dropTimer(), "forward replay matches");
        assertEquals(uninterruptedAlternator, restored.soundAlternator());
    }

    /** {@code $20}: {@code $34(a0)} is a byte angle that repeats every 128 frames. */
    @Test
    void swingingSpikeBallRewindSpot() {
        // Subtype 3: three chain links (sonic3k.asm:88681-88683).
        Harness<LrzSwingingSpikeBallObjectInstance> harness = Harness.create(
                () -> new LrzSwingingSpikeBallObjectInstance(
                        new ObjectSpawn(BASE_X, BASE_Y,
                                Sonic3kObjectIds.MGZLBZ_SMASHING_PILLAR_ALT, 3, 0, false, 0)),
                LrzSwingingSpikeBallObjectInstance.class);
        LrzSwingingSpikeBallObjectInstance ball = harness.object();

        run(ball, 1, 37);
        int capturedAngle = ball.angle();
        int capturedX = ball.getCentreX();
        int capturedY = ball.getCentreY();
        CompositeSnapshot mid = harness.capture();

        run(ball, 38, 111);
        int uninterruptedAngle = ball.angle();
        int uninterruptedX = ball.getCentreX();
        int uninterruptedY = ball.getCentreY();

        harness.restore(mid);
        LrzSwingingSpikeBallObjectInstance restored = harness.only();
        assertEquals(capturedAngle, restored.angle(), "$34(a0) restores");
        assertEquals(ball.angleStep(), restored.angleStep(), "$36(a0) restores");
        // sub_43604 rebuilds the chain and the ball from the angle at the head of every update,
        // so the restored position appears on the next frame rather than in the blob itself.
        run(restored, 38, 111);
        assertEquals(uninterruptedAngle, restored.angle(), "forward replay lands on the same angle");
        assertEquals(uninterruptedX, restored.getCentreX());
        assertEquals(uninterruptedY, restored.getCentreY());
        assertNotEquals(capturedX + capturedY, uninterruptedX + uninterruptedY,
                "the two sampled points differ, so the comparison could have disagreed");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static void run(AbstractObjectInstance object, int first, int last) {
        for (int frame = first; frame <= last; frame++) {
            object.update(frame, null);
        }
    }

    private static ObjectSpawn spawn(int subtype) {
        return new ObjectSpawn(BASE_X, BASE_Y, 0, subtype, 0, false, 0);
    }

    private record Harness<T extends AbstractObjectInstance>(ObjectManager objectManager,
                                                             RewindRegistry registry,
                                                             Supplier<T> factory,
                                                             Class<T> type) {

        static <T extends AbstractObjectInstance> Harness<T> create(Supplier<T> factory,
                                                                    Class<T> type) {
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
            return new Harness<>(objectManager, registry, factory, type);
        }

        T object() {
            return objectManager.createDynamicObject(factory::get);
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        T only() {
            List<T> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == type && !o.isDestroyed())
                    .map(type::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live " + type.getSimpleName() + " after restore");
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
