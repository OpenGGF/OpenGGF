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
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind spot for {@code Obj_LRZSinkingRock}: before the first contact, part-way down, and after the
 * block has risen back, each restore followed by a forward replay.
 *
 * <p>{@code $2E(a0)} is the only state that matters and it is not recoverable from the block's drawn
 * position, because the sine curve is flat at both ends: angles {@code 0} and {@code 1} both put the
 * block on its placed Y, and {@code $3F} and {@code $40} both put it 31-32 pixels down. A restore
 * that rebuilt the angle from {@code y_pos} would therefore look right and then rise or sink at the
 * wrong rate, so every case asserts the angle itself and then replays to the state the
 * uninterrupted timeline reached.
 */
class TestLrzSinkingRockRewindSpot {

    private static final int BASE_X = 0x04F8;
    private static final int BASE_Y = 0x0543;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(
            BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_RIDE_GRAPPLE, 0, 0, false, 0);

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

    /** BEFORE: captured untouched, diverged by standing on it, restored to untouched. */
    @Test
    void sinkingRockRewindSpotBeforeTheFirstContact() {
        Harness harness = Harness.create();
        LrzSinkingRockObjectInstance rock = harness.rock();
        TestablePlayableSprite player = player();

        CompositeSnapshot before = harness.capture();

        stand(rock, player, 1, 20);
        assertEquals(20, rock.angle(), "the diverging timeline sank the block");

        harness.restore(before);
        LrzSinkingRockObjectInstance restored = harness.onlyRock();
        assertNotSame(rock, restored, "restore recreates the block");
        assertEquals(0, restored.angle(), "the angle restores to zero");
        assertEquals(BASE_Y, restored.getCentreY(), "and the block is back on its placed Y");
    }

    /** DURING: captured part-way down, diverged to the clamp, restored, replayed the same frames. */
    @Test
    void sinkingRockRewindSpotMidSinkReplaysToTheSameAngle() {
        Harness harness = Harness.create();
        LrzSinkingRockObjectInstance rock = harness.rock();
        TestablePlayableSprite player = player();

        stand(rock, player, 1, 20);
        assertEquals(20, rock.angle(), "precondition: part-way down");
        int midwayY = rock.getCentreY();
        assertTrue(midwayY > BASE_Y && midwayY < BASE_Y + 32, "precondition: inside the travel");

        CompositeSnapshot midway = harness.capture();

        stand(rock, player, 21, 100);
        int uninterruptedAngle = rock.angle();
        int uninterruptedY = rock.getCentreY();
        assertEquals(0x40, uninterruptedAngle, "precondition: the uninterrupted timeline clamped");

        harness.restore(midway);
        LrzSinkingRockObjectInstance restored = harness.onlyRock();
        assertEquals(20, restored.angle(), "the angle restores, not a value re-derived from y_pos");
        assertEquals(midwayY, restored.getCentreY(), "and the position with it");

        stand(restored, player, 21, 100);
        assertEquals(uninterruptedAngle, restored.angle(), "forward replay lands on the same angle");
        assertEquals(uninterruptedY, restored.getCentreY(), "and the same position");
    }

    /** AFTER: captured back at rest, diverged by standing again, restored to rest. */
    @Test
    void sinkingRockRewindSpotAfterItHasRisenBack() {
        Harness harness = Harness.create();
        LrzSinkingRockObjectInstance rock = harness.rock();
        TestablePlayableSprite player = player();

        stand(rock, player, 1, 0x40);
        assertEquals(0x40, rock.angle(), "precondition: fully sunk");
        for (int frame = 0x41; frame <= 0x41 + 0x40; frame++) {
            rock.update(frame, player);
        }
        assertEquals(0, rock.angle(), "precondition: risen back to rest");

        CompositeSnapshot after = harness.capture();

        stand(rock, player, 500, 530);
        assertTrue(rock.angle() > 0, "the diverging timeline sank it again");

        harness.restore(after);
        LrzSinkingRockObjectInstance restored = harness.onlyRock();
        assertEquals(0, restored.angle(), "the risen-back angle restores to zero");
        assertEquals(BASE_Y, restored.getCentreY());
    }

    // ----- harness ------------------------------------------------------------------------------

    /**
     * One standing frame is a solid contact followed by an update: the object reads the standing bit
     * the previous frame's {@code SolidObjectFull} left, which is what the engine's solid checkpoint
     * reproduces in production.
     */
    private static void stand(LrzSinkingRockObjectInstance rock, TestablePlayableSprite player,
                              int firstFrame, int lastFrame) {
        for (int frame = firstFrame; frame <= lastFrame; frame++) {
            rock.onSolidContact(player, standingContact(), frame);
            rock.update(frame, player);
        }
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static TestablePlayableSprite player() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) BASE_X, (short) (BASE_Y - 0x11));
        player.setCentreX((short) BASE_X);
        player.setCentreY((short) (BASE_Y - 0x11));
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

        LrzSinkingRockObjectInstance rock() {
            return objectManager.createDynamicObject(() -> new LrzSinkingRockObjectInstance(SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzSinkingRockObjectInstance onlyRock() {
            List<LrzSinkingRockObjectInstance> live = objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzSinkingRockObjectInstance.class && !o.isDestroyed())
                    .map(LrzSinkingRockObjectInstance.class::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live sinking rock after restore");
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
