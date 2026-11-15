package com.openggf.game.soniccd.zones.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Disc-free checks of the Quartz Quadrant scroll programs against values worked by hand from scddisasm
 * src/r5 (pin 1ecc5f3a). They pin the routines' arithmetic and the swap thresholds; the native comparison
 * with the BizHawk screenshots is {@code TestQqzBackgroundPicture_*} (media-gated).
 */
class TestQqzScrollPrograms {
    private static QqzScrollHandler handler(QqzScrollHandler.Program program, int[] playerX) {
        return new QqzScrollHandler(program, () -> playerX[0]);
    }

    private static int[] buffer() {
        return new int[ScrollEffectComposer.VISIBLE_LINES];
    }

    @Test
    void everyStageSelectsItsProgram() {
        assertEquals(QqzScrollHandler.Program.A_ACT1, QqzScrollHandler.Program.of(CdStagePayload.R51A));
        assertEquals(QqzScrollHandler.Program.A_ACT2, QqzScrollHandler.Program.of(CdStagePayload.R52A));
        assertEquals(QqzScrollHandler.Program.B, QqzScrollHandler.Program.of(CdStagePayload.R51B));
        assertEquals(QqzScrollHandler.Program.B, QqzScrollHandler.Program.of(CdStagePayload.R52B));
        assertEquals(QqzScrollHandler.Program.C, QqzScrollHandler.Program.of(CdStagePayload.R51C));
        assertEquals(QqzScrollHandler.Program.C, QqzScrollHandler.Program.of(CdStagePayload.R52C));
        assertEquals(QqzScrollHandler.Program.D, QqzScrollHandler.Program.of(CdStagePayload.R51D));
        assertEquals(QqzScrollHandler.Program.D, QqzScrollHandler.Program.of(CdStagePayload.R52D));
        assertEquals(QqzScrollHandler.Program.C_ACT3, QqzScrollHandler.Program.of(CdStagePayload.R53C));
        assertEquals(QqzScrollHandler.Program.D_ACT3, QqzScrollHandler.Program.of(CdStagePayload.R53D));
    }

    /** scroll_1a:176-206 at foreground (x, y) = ($100, $200): bg = 5x/4, bg2 = x/2, bg3 = x/4, bg_y = y/2 + y/8 + y/16. */
    @Test
    void presentInitialBackgroundCameras() {
        var h = handler(QqzScrollHandler.Program.A_ACT1, new int[] {0x40});
        h.initLevelScroll(0x100, 0x200);
        assertEquals(0x140, h.backgroundCameraX() >>> 16);
        assertEquals(0x080, h.backgroundCamera2X() >>> 16);
        assertEquals(0x040, h.backgroundCamera3X() >>> 16);
        assertEquals(0x160, h.backgroundCameraY() >>> 16);
    }

    /** scroll_1b:187-209: bg_y = 3y/16, bg = 3x/4, bg2 = x/4, bg3 = 3 * (x/16). */
    @Test
    void pastInitialBackgroundCameras() {
        var h = handler(QqzScrollHandler.Program.B, new int[] {0x40});
        h.initLevelScroll(0x100, 0x200);
        assertEquals(0x060, h.backgroundCameraY() >>> 16);
        assertEquals(0x0C0, h.backgroundCameraX() >>> 16);
        assertEquals(0x040, h.backgroundCamera2X() >>> 16);
        assertEquals(0x030, h.backgroundCamera3X() >>> 16);
    }

    /** scroll_1c:187-203 and scroll_3c:176-191: bg_y = y/4, bg = x/2, bg2 = x/4, bg3 = 3 * (x/16). */
    @Test
    void goodFutureInitialBackgroundCameras() {
        for (var program : List.of(QqzScrollHandler.Program.C, QqzScrollHandler.Program.C_ACT3)) {
            var h = handler(program, new int[] {0x40});
            h.initLevelScroll(0x100, 0x200);
            assertEquals(0x080, h.backgroundCameraY() >>> 16, program.name());
            assertEquals(0x080, h.backgroundCameraX() >>> 16, program.name());
            assertEquals(0x040, h.backgroundCamera2X() >>> 16, program.name());
            assertEquals(0x030, h.backgroundCamera3X() >>> 16, program.name());
        }
    }

    /** scroll_1d:188-206 and scroll_3d:176-193: bg_y = y/8 + y/32 + y/16, bg = x/2, bg2 = x/4, bg3 = x/8. */
    @Test
    void badFutureInitialBackgroundCameras() {
        for (var program : List.of(QqzScrollHandler.Program.D, QqzScrollHandler.Program.D_ACT3)) {
            var h = handler(program, new int[] {0x40});
            h.initLevelScroll(0x100, 0x200);
            assertEquals(0x070, h.backgroundCameraY() >>> 16, program.name());
            assertEquals(0x080, h.backgroundCameraX() >>> 16, program.name());
            assertEquals(0x040, h.backgroundCamera2X() >>> 16, program.name());
            assertEquals(0x020, h.backgroundCamera3X() >>> 16, program.name());
        }
    }

    /** A camera step of 16 right: scroll_x_move = $1000 (8.8). Present rates 1/4, 1/2, 5/4 (scroll_1a:234-252). */
    @Test
    void presentBackgroundCamerasFollowTheirRates() {
        var h = handler(QqzScrollHandler.Program.A_ACT1, new int[] {0x40});
        h.update(buffer(), 0x100, 0x200, 0, 0);
        int bg = h.backgroundCameraX();
        int bg2 = h.backgroundCamera2X();
        int bg3 = h.backgroundCamera3X();
        h.update(buffer(), 0x110, 0x200, 1, 0);
        assertEquals(0x10 * 5 / 4, (h.backgroundCameraX() - bg) >> 16);
        assertEquals(0x10 / 2, (h.backgroundCamera2X() - bg2) >> 16);
        assertEquals(0x10 / 4, (h.backgroundCamera3X() - bg3) >> 16);
    }

    /** Past rates 3/4, 1/4, 3/16 (scroll_1b:237-258); Good Future 1/2, 1/4, 3/16; Bad Future 1/2, 1/4, 1/8. */
    @Test
    void otherPeriodsFollowTheirRates() {
        int[][] expected = {{12, 4, 3}, {8, 4, 3}, {8, 4, 2}};      // bg, bg2, bg3 gain for a 16 px step
        var programs = List.of(QqzScrollHandler.Program.B, QqzScrollHandler.Program.C, QqzScrollHandler.Program.D);
        for (int i = 0; i < programs.size(); i++) {
            var h = handler(programs.get(i), new int[] {0x40});
            h.update(buffer(), 0x100, 0x200, 0, 0);
            int bg = h.backgroundCameraX();
            int bg2 = h.backgroundCamera2X();
            int bg3 = h.backgroundCamera3X();
            h.update(buffer(), 0x110, 0x200, 1, 0);
            assertEquals(expected[i][0], (h.backgroundCameraX() - bg) >> 16, programs.get(i).name());
            assertEquals(expected[i][1], (h.backgroundCamera2X() - bg2) >> 16, programs.get(i).name());
            assertEquals(expected[i][2], (h.backgroundCamera3X() - bg3) >> 16, programs.get(i).name());
        }
    }

    /** The swap thresholds, background_swap_1a:56-65: r5_bg_change counts thresholds below the player's X. */
    @Test
    void backgroundSwapCountsThresholdsAndReinitialises() {
        int[] playerX = {0x40};
        var h = handler(QqzScrollHandler.Program.A_ACT1, playerX);
        List<Integer> seen = new ArrayList<>();
        h.setSwapListener(seen::add);
        h.update(buffer(), 0x100, 0x200, 0, 0);
        assertEquals(0, h.backgroundChange());
        playerX[0] = 0xE00;                                    // cmp.w obj.x,d1 ; bcc: a threshold equal to X is not passed
        h.update(buffer(), 0x100, 0x200, 1, 0);
        assertEquals(0, h.backgroundChange());
        playerX[0] = 0xE01;
        h.update(buffer(), 0x100, 0x200, 2, 0);
        assertEquals(1, h.backgroundChange());
        assertEquals(0x200 / 4, h.backgroundCameraY() >>> 16);  // InitBgScroll with r5_bg_change odd (scroll_1a:178-181)
        playerX[0] = 0x2301;
        h.update(buffer(), 0x100, 0x200, 3, 0);
        assertEquals(2, h.backgroundChange());
        assertEquals(List.of(1, 2), seen);
        playerX[0] = 0x40;                                     // r5_bg_change is recomputed each frame, so it returns to 0
        h.update(buffer(), 0x100, 0x200, 4, 0);
        assertEquals(0, h.backgroundChange());
    }

    /** Act 3 has no background-swap source (r5/object_index_3.asm:74-76). */
    @Test
    void actThreeNeverSwaps() {
        var h = handler(QqzScrollHandler.Program.C_ACT3, new int[] {0x3000});
        h.update(buffer(), 0x100, 0x200, 0, 0);
        assertEquals(0, h.backgroundChange());
    }

    /**
     * The ramp's divide: scroll_1a:324-332. Positive differences divide normally ((0x20 << 6) / $C = $AA,
     * << 10). A negative difference overflows divu.w, so d2 keeps its dividend $FFFFFF80 and ext.l takes the
     * low word $FF80 = -128 (<< 10).
     */
    @Test
    void rampStepFollowsDivu() {
        assertEquals(0xAA << 10, QqzScrollHandler.rampStep(0x20, 6, 0xC, 10));
        assertEquals(-128 << 10, QqzScrollHandler.rampStep(-2, 6, 0xC, 10));
        assertEquals(0, QqzScrollHandler.rampStep(0, 6, 0xC, 10));
    }

    /** With every camera at its start the Present's lines are one background word per block (no ramp offset yet). */
    @Test
    void presentLinesAtTheStartAreFlatPerBlockAndRampsStartAtTheirCamera() {
        var h = handler(QqzScrollHandler.Program.A_ACT1, new int[] {0x40});
        int[] lines = buffer();
        h.update(lines, 0, 0, 0, 0);
        for (int line = 0; line < lines.length; line++) {
            assertEquals(0, (short) lines[line], "foreground word of line " + line);
            assertEquals(0, (short) (lines[line] >> 16), "background word of line " + line);
        }
    }
}
