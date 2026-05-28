package com.openggf.sprites.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
