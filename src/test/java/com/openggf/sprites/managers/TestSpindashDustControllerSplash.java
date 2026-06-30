package com.openggf.sprites.managers;

import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression guard for the water-entry/exit splash.
 *
 * <p>The S2/S3K splash rides the fixed Sonic_Dust object (ROM writes
 * {@code anim=1} into it; sonic3k.asm:22241,22281) rather than spawning an
 * object slot. A 2026-05-26 refactor stubbed the splash path out entirely, so
 * no splash showed in S3K water zones (e.g. CNZ Act 2). This verifies the
 * controller plays and then ends the splash animation.
 */
class TestSpindashDustControllerSplash {

    @Test
    void splashTriggersAndAnimatesToCompletion() {
        // Null sprite/renderer is fine: update() ticks the splash before the
        // spindash-dust path (which guards null sprite), and we only assert on
        // the splash lifecycle, not rendering.
        SpindashDustController controller = new SpindashDustController(null, null);

        assertFalse(controller.isSplashActive(), "no splash before trigger");

        controller.triggerSplash(0x100, 0x200, false);
        assertTrue(controller.isSplashActive(), "splash active immediately after trigger");

        // 10 frames * 3 ticks/frame = 30 ticks; run a comfortable margin past it.
        boolean endedWithinWindow = false;
        for (int i = 0; i < 40; i++) {
            controller.update();
            if (!controller.isSplashActive()) {
                endedWithinWindow = true;
                break;
            }
        }
        assertTrue(endedWithinWindow, "splash animation should finish and clear itself");
    }

    /**
     * Surface-emerge splash (ROM Obj_DashDust anim 4 / Ani_DashSplashDrown byte_18DE8:
     * frames $16..$1D, duration 5 -> 6 displayed frames each, then $FD 0 ends it).
     * This is the LBZ1 "break the surface" splash, driven through the controller so it
     * never becomes an SST object (ROM mutates the fixed Dust slot).
     */
    @Test
    void surfaceSplashPlaysFramesSixteenThroughTwentyNineThenEnds() {
        SpindashDustController controller = new SpindashDustController(null, null);
        PlayerSpriteRenderer splashRenderer = mock(PlayerSpriteRenderer.class);

        assertFalse(controller.isSurfaceSplashActive(), "no surface splash before trigger");

        controller.triggerSurfaceSplash(splashRenderer, 0x00B0, 0x05C0);
        assertTrue(controller.isSurfaceSplashActive(), "active immediately after trigger");
        assertEquals(0x16, controller.surfaceSplashMappingFrame(), "starts on frame $16");

        // Each of the 8 frames ($16..$1D) holds for 6 update ticks.
        for (int frame = 0x16; frame <= 0x1D; frame++) {
            for (int tick = 0; tick < 6; tick++) {
                controller.update();
                if (frame < 0x1D || tick < 5) {
                    assertTrue(controller.isSurfaceSplashActive(),
                            "alive while frame 0x" + Integer.toHexString(frame) + " plays");
                    assertEquals(frame, controller.surfaceSplashMappingFrame(),
                            "frame 0x" + Integer.toHexString(frame) + " holds for 6 ticks");
                }
            }
        }

        // After 8*6 = 48 ticks the $FD 0 sentinel ends the effect on the next advance.
        controller.update();
        assertFalse(controller.isSurfaceSplashActive(), "surface splash ends after its final frame");
    }

    @Test
    void surfaceSplashIgnoresNullRenderer() {
        SpindashDustController controller = new SpindashDustController(null, null);
        controller.triggerSurfaceSplash(null, 0, 0);
        assertFalse(controller.isSurfaceSplashActive(), "null renderer must not start a splash");
    }
}
