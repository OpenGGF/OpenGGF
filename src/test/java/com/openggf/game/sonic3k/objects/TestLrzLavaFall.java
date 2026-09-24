package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZLavaFall} (sonic3k.asm:88770-88808, ROM {@code $436A0}) and the drops it allocates
 * ({@code loc_4374C} / {@code loc_43764}, :88813-88827).
 *
 * <p>The emitter has no level clock of its own in this harness, so each case drives {@code update}
 * with the clock value the ROM would have read in {@code vIntRunCount} -- which is what
 * {@code levelFrameCounterOrFallback} falls back to when no {@code LevelManager} is attached.
 */
class TestLrzLavaFall {

    private static final int BASE_X = 0x0500;
    private static final int BASE_Y = 0x0300;

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

    /**
     * {@code move.w (Level_frame_counter).w,d0 / andi.w #$FF,d0 / cmp.w $30(a0),d0 / blo}
     * (sonic3k.asm:88777-88780): nothing happens while the clock's low byte is below the subtype.
     */
    @Test
    void nothingIsEmittedWhileTheClockIsBelowTheSubtype() {
        Harness harness = Harness.create();
        LrzLavaFallObjectInstance fall = harness.fall(0x60);
        assertEquals(0x60, fall.clockThreshold());

        for (int clock = 0; clock < 0x60; clock++) {
            fall.update(clock, null);
        }
        assertEquals(0, harness.drops().size(), "the fall is off below its threshold");
        assertEquals(0, fall.dropTimer(), "and the drop timer never ran");
    }

    /**
     * {@code subq.b #1,anim_frame_timer(a0) / bpl} then {@code move.b #5} (:88781-88784): a drop
     * every sixth frame once the clock is above the threshold.
     */
    @Test
    void aDropIsEmittedEverySixthFrameAboveTheThreshold() {
        Harness harness = Harness.create();
        LrzLavaFallObjectInstance fall = harness.fall(0x60);

        for (int clock = 0x60; clock < 0x60 + 18; clock++) {
            fall.update(clock, null);
        }
        // Frames $60, $66 and $6C: the counter starts at the zero of a cleared slot, so the first
        // frame above the threshold already emits.
        assertEquals(3, harness.drops().size(), "eighteen frames is three drops");
    }

    /**
     * {@code addq.b #1,$25(a0) / cmpi.b #2 / blo} (:88788-88792): the second of every two drops is
     * the one given the routine that plays {@code sfx_LavaFall}.
     */
    @Test
    void everySecondDropCarriesTheSoundRoutine() {
        Harness harness = Harness.create();
        LrzLavaFallObjectInstance fall = harness.fall(0x60);

        for (int clock = 0x60; clock < 0x60 + 24; clock++) {
            fall.update(clock, null);
        }
        List<LrzLavaFallDropInstance> drops = harness.drops();
        assertEquals(4, drops.size());
        assertFalse(drops.get(0).playsSound(), "the first drop is the quiet one");
        assertTrue(drops.get(1).playsSound(), "$25 reached 2 on the second");
        assertFalse(drops.get(2).playsSound());
        assertTrue(drops.get(3).playsSound());
    }

    /** {@code move.w #$1C,$2E(a1)} and the flipped {@code #$24} (sonic3k.asm:88804, :88806). */
    @Test
    void theDropsLifeComesFromTheEmittersFlipFlag() {
        Harness harness = Harness.create();
        assertEquals(0x1C, harness.drop(false, false).life(), "an unflipped emitter");
        assertEquals(0x24, harness.drop(false, true).life(), "status bit 0 set");
    }

    /**
     * {@code subq.w #1,$2E(a0) / bmi} before {@code MoveSprite2} (:88820-88823), and
     * {@code move.w #$800,y_vel(a1)} through the routine's {@code ext.l / lsl.l #8}: eight pixels
     * a frame straight down, for {@code $2E + 1} frames.
     */
    @Test
    void aDropFallsEightPixelsAFrameAndDiesWhenItsLifeRunsOut() {
        Harness harness = Harness.create();
        LrzLavaFallDropInstance drop = harness.drop(false, false);

        for (int frame = 1; frame <= 4; frame++) {
            drop.update(frame, null);
        }
        assertEquals(BASE_Y + 32, drop.getCentreY(), "four frames of $800 is thirty-two pixels");
        assertEquals(BASE_X, drop.getCentreX(), "x never moves");

        for (int frame = 5; frame <= 0x1D; frame++) {
            drop.update(frame, null);
        }
        assertTrue(drop.isDestroyed(), "$1C + 1 frames and the drop is gone");
    }

    /** {@code move.b #$99,collision_flags(a1)} (sonic3k.asm:88802). */
    @Test
    void theDropIsHarmful() {
        Harness harness = Harness.create();
        assertEquals(0x99, harness.drop(false, false).getCollisionFlags());
    }

    private record Harness(ObjectManager objectManager) {

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
            return new Harness(objectManager);
        }

        LrzLavaFallObjectInstance fall(int subtype) {
            return objectManager.createDynamicObject(() -> new LrzLavaFallObjectInstance(
                    new ObjectSpawn(BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE,
                            subtype, 0, false, 0)));
        }

        LrzLavaFallDropInstance drop(boolean playsSound, boolean longLived) {
            return objectManager.createDynamicObject(
                    () -> new LrzLavaFallDropInstance(BASE_X, BASE_Y, playsSound, longLived));
        }

        List<LrzLavaFallDropInstance> drops() {
            return objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzLavaFallDropInstance.class && !o.isDestroyed())
                    .map(LrzLavaFallDropInstance.class::cast)
                    .toList();
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x0480; }
            @Override public short getY() { return 0x0280; }
            @Override public short getWidth() { return 0x140; }
            @Override public short getHeight() { return 0xE0; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
