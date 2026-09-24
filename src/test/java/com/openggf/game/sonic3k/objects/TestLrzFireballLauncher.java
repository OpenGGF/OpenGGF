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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZFireballLauncher} (sonic3k.asm:88151-88215, ROM {@code $42B4C}) and the fireball it
 * allocates ({@code loc_42C80}, :88198-88211).
 *
 * <p>The eleven subtypes enumerated below are the ones Lava Reef's twenty-seven placements actually
 * carry, read out of {@code Levels/LRZ/Object Pos/1.bin}.
 */
class TestLrzFireballLauncher {

    private static final int BASE_X = 0x06FD;
    private static final int BASE_Y = 0x0490;

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

    /** {@code moveq #0,d0 / move.b subtype(a0),d0 / lsl.w #2,d0} (sonic3k.asm:88159-88162). */
    @Test
    void thePeriodIsTheSubtypeTimesFour() {
        int[] placed = {0x10, 0x14, 0x16, 0x18, 0x1A, 0x1C, 0x20, 0x24, 0x28, 0x30, 0x38};
        for (int subtype : placed) {
            assertEquals(subtype * 4, launcher(subtype, false).period(),
                    "subtype $" + Integer.toHexString(subtype));
        }
    }

    /**
     * {@code subq.w #1,$2E(a0) / bpl} then {@code move.w $30(a0),$2E(a0)} (:88166-88168). The
     * counter starts at the zero of a cleared slot, so the first frame already reloads it, and the
     * gate on {@code render_flags} bit 7 means that first frame never fires.
     */
    @Test
    void theCounterReloadsOnTheFirstFrameAndCountsDownFromThere() {
        Harness harness = Harness.create();
        LrzFireballLauncherObjectInstance launcher = harness.launcher(0x10, false);
        assertEquals(0, launcher.countdown(), "a cleared slot starts at zero");

        launcher.update(1, null);
        assertEquals(0x40, launcher.countdown(), "reloaded with $30");
        assertEquals(0, harness.fireballs().size(), "and nothing was fired on that frame");

        launcher.update(2, null);
        assertEquals(0x3F, launcher.countdown(), "then one a frame");
    }

    /** {@code jsr (AllocateObjectAfterCurrent)} on the reload frame (:88170-88193). */
    @Test
    void aShotIsFiredEveryPeriodOnceTheLauncherHasBeenDrawn() {
        Harness harness = Harness.create();
        LrzFireballLauncherObjectInstance launcher = harness.launcher(0x10, false);

        // Frame 1 reloads without firing, then the counter runs $40 down to -1: the shot lands on
        // frame $42.
        for (int frame = 1; frame <= 0x42; frame++) {
            launcher.update(frame, null);
        }
        List<LrzFireballObjectInstance> shots = harness.fireballs();
        assertEquals(1, shots.size(), "exactly one shot by frame $42");
        assertEquals(BASE_X + 8, shots.get(0).getCentreX(), "addi.w #8,x_pos(a1)");
        assertEquals(BASE_Y, shots.get(0).getCentreY(), "y_pos is copied unchanged");
        assertEquals(0x200, shots.get(0).xVelocity(), "move.w #$200,x_vel(a1)");
    }

    /** {@code btst #0,status(a0)} (:88189-88191): the X-flip mirrors offset and velocity. */
    @Test
    void aMirroredLauncherFiresLeftFromTheOtherSide() {
        Harness harness = Harness.create();
        LrzFireballLauncherObjectInstance launcher = harness.launcher(0x10, true);
        for (int frame = 1; frame <= 0x42; frame++) {
            launcher.update(frame, null);
        }
        List<LrzFireballObjectInstance> shots = harness.fireballs();
        assertEquals(1, shots.size());
        // +8 then -2*8 leaves the shot eight pixels the other side of the nozzle.
        assertEquals(BASE_X - 8, shots.get(0).getCentreX(), "addi.w #8 then subi.w #2*8");
        assertEquals(-0x200, shots.get(0).xVelocity(), "neg.w x_vel(a1)");
    }

    /**
     * {@code jsr (MoveSprite2)} (:88208): {@code x_vel} only, no gravity and no {@code y_vel}. The
     * routine does {@code ext.l / lsl.l #8 / add.l} (sonic3k.asm:36054-36057), so {@code $200} is
     * two pixels a frame; adding the raw word to a 16.16 position instead moves 1/256 of that.
     */
    @Test
    void theShotTravelsHorizontallyAtTwoPixelsAFrame() {
        Harness harness = Harness.create();
        LrzFireballObjectInstance shot = harness.fireball(BASE_X, BASE_Y, false);
        for (int frame = 1; frame <= 8; frame++) {
            shot.update(frame, null);
        }
        assertEquals(BASE_X + 16, shot.getCentreX(), "eight frames of $200 is sixteen pixels");
        assertEquals(BASE_Y, shot.getCentreY(), "y never moves");
    }

    /** {@code move.b #$9B,collision_flags(a1)} (sonic3k.asm:88183). */
    @Test
    void theShotIsHarmful() {
        Harness harness = Harness.create();
        assertEquals(0x9B, harness.fireball(BASE_X, BASE_Y, false).getCollisionFlags());
    }

    private static LrzFireballLauncherObjectInstance launcher(int subtype, boolean mirrored) {
        return new LrzFireballLauncherObjectInstance(new ObjectSpawn(
                BASE_X, BASE_Y, Sonic3kObjectIds.LBZ_PIPE_PLUG, subtype,
                mirrored ? 1 : 0, false, 0));
    }

    @Test
    void fireDamageUsesImmunityWithoutShieldDeflection() {
        var hazard = Harness.create().fireball(BASE_X, BASE_Y, false);
        // Both parent routines set bit4. Touch_ChkHurt checks this against
        // the fire shield; only bit3 selects projectile deflection.
        assertEquals(0x10, hazard.getShieldReactionFlags());
        assertEquals(0x10, hazard.getTouchResponseProfile().shieldReactionFlags());
        assertEquals(com.openggf.level.objects.TouchShieldDeflectCapability.NONE,
                hazard.getTouchResponseProfile().shieldDeflectCapability());
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

        LrzFireballLauncherObjectInstance launcher(int subtype, boolean mirrored) {
            LrzFireballLauncherObjectInstance instance = objectManager.createDynamicObject(
                    () -> TestLrzFireballLauncher.launcher(subtype, mirrored));
            // tst.b render_flags(a0) / bpl: the ROM gate is "was drawn last frame", which the
            // engine answers from the camera bounds set in initHeadless.
            assertTrue(instance.getCentreX() > 0);
            return instance;
        }

        LrzFireballObjectInstance fireball(int x, int y, boolean mirrored) {
            return objectManager.createDynamicObject(
                    () -> new LrzFireballObjectInstance(x, y, mirrored));
        }

        List<LrzFireballObjectInstance> fireballs() {
            return objectManager.getActiveObjects().stream()
                    .filter(o -> o.getClass() == LrzFireballObjectInstance.class && !o.isDestroyed())
                    .map(LrzFireballObjectInstance.class::cast)
                    .toList();
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0x0680; }
            @Override public short getY() { return 0x0440; }
            @Override public short getWidth() { return 0x140; }
            @Override public short getHeight() { return 0xE0; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
