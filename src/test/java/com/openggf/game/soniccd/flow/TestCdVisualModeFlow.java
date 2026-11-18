package com.openggf.game.soniccd.flow;

import com.openggf.NativeMenuPresentationAccess;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

@EnabledOnOs(OS.LINUX)
class TestCdVisualModeFlow {
    @Test
    void titleOpensVisualModeAndAllThreeFilmsReturnWithoutWritingSaveData() throws Exception {
        var saved = CdSaveRecord.decode(new byte[CdSaveRecord.FILE_BYTES]);
        saved.store(new CdSaveVariables(0x0215, 3, 0x04, 0x15, 0, 4, 0x0f));
        try (var harness = new CdFlowHarness(module -> CdFlowHarness.attachReadOnlySave(module, saved))) {
            harness.selectTitleOption(1);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.OPTIONS
                    && !GameServices.fade().isActive(), 600, 0);

            int[][] films = new int[3][];
            int[][] endings = new int[2][];
            for (int i = 0; i < films.length; i++) {
                int[] menu = draw(harness);
                assertTrue(Arrays.stream(menu).distinct().count() > 8, "the browser has drawn its choices");
                harness.step(0, CdFlowHarness.A, false);
                harness.idle(220);
                films[i] = draw(harness);
                assertFalse(Arrays.equals(menu, films[i]), "confirm starts a video");
                assertTrue(Arrays.stream(films[i]).distinct().count() > 8, "the disc supplies visible video pixels");
                harness.idle(60);
                assertFalse(Arrays.equals(films[i], draw(harness)), "the video advances");
                if (i > 0) {
                    // Both endings share the early footage. Their separate sequences are visible at frame 2500.
                    harness.idle(2500 - 280);
                    endings[i - 1] = draw(harness);
                }

                harness.step(0, CdFlowHarness.C, false);
                assertArrayEquals(menu, draw(harness), "Back restores the same selected row");
                harness.idle(1);
                harness.step(CdFlowHarness.DOWN, 0, false);
                harness.idle(1);
            }
            assertFalse(Arrays.equals(films[0], films[1]));
            assertFalse(Arrays.equals(endings[0], endings[1]), "the two ending choices play different clips");

            harness.step(0, CdFlowHarness.A, false);
            harness.until(() -> harness.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertNull(harness.scenes.pendingEnding());
            assertEquals(saved, harness.scenes.flow().backupRam().readSave());
        }
    }

    private static int[] draw(CdFlowHarness harness) {
        assertEquals(GameMode.OPTIONS, harness.loop.getCurrentGameMode());
        var screen = NativeMenuPresentationAccess.screen(harness.loop);
        assertNotNull(screen);
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        GameServices.graphics().flushScreenSpace();
        return ScreenshotCapture.captureFramebuffer(320, 224).pixels();
    }
}
