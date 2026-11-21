package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager.LevelRenderOptions;
import com.openggf.level.render.PlaneRasterizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

@EnabledOnOs(OS.LINUX)
class TestCczStreamedBackgroundPicture {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;
    private static final int STATIC_ROW_REPEAT = 512;

    private enum RowKind { MOVING, STATIC }

    @Test
    void pastRowsRepeatOrStreamFromTheDiscLayout() throws Exception {
        // Camera on rehost-long/R31B trace row $189C, where the static rows cross nonrepeating layout columns.
        compareBackgroundRows(CdStagePayload.R31B, 0x2252, 0x0274, 0x33DA);
    }

    @Test
    void goodFutureRowsRepeatOrStreamFromTheDiscLayout() throws Exception {
        // Camera on rehost-long/R31C trace row $102A.
        compareBackgroundRows(CdStagePayload.R31C, 0x1A00, 0x0510, 0x34FE);
    }

    private static void compareBackgroundRows(CdStagePayload payload, int cameraX, int cameraY, int selectorOffset)
            throws Exception {
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boots = new CdFreshStageBoots(WIDTH, HEIGHT)) {
            var stage = new CdStageLoader(disc).readStage(payload);
            var loop = boots.boot(disc, payload);
            var camera = GameServices.camera();
            camera.setFrozen(true);
            int xOnRoute = camera.getX();
            int yOnRoute = camera.getY();
            while (xOnRoute != cameraX || yOnRoute != cameraY) {
                xOnRoute += Math.clamp(cameraX - xOnRoute, -16, 16);
                yOnRoute += Math.clamp(cameraY - yOnRoute, -16, 16);
                camera.setX((short) xOnRoute);
                camera.setY((short) yOnRoute);
                loop.step();
            }
            assertEquals(cameraX, camera.getX(), "foreground camera X");
            assertEquals(cameraY, camera.getY(), "foreground camera Y");

            var manager = GameServices.level();
            var graphics = GameServices.graphics();
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            manager.setClearColor();
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            manager.drawWithRenderOptions(GameServices.sprites(), LevelRenderOptions.tilesOnly());
            graphics.flush();
            glFinish();
            var actual = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);

            var level = manager.getCurrentLevel();
            PlaneRasterizer.TileSource tiles = (layer, index, x) -> level.getPattern(index);
            var scroll = GameServices.parallax();
            int[] words = scroll.getHScroll();
            for (RowKind kind : RowKind.values()) {
                int compared = 0;
                int mismatches = 0;
                for (int y = 0; y < HEIGHT; y++) {
                    int sourceY = y + scroll.getVscrollFactorBG();
                    int row = sourceY / 16;
                    // The first selector belongs to the offscreen row above the layout.
                    if (row < 0 || row >= 49) continue;
                    boolean staticRow = stage.u8(selectorOffset + 1 + row) == 0;
                    if (staticRow != (kind == RowKind.STATIC)) continue;

                    // The hosted foreground pass uses camera X even where the native scroll words wobble.
                    int fgY = y + scroll.getVscrollFactorFG();
                    int bgX = -(short) words[y];
                    int[] fgPixels = PlaneRasterizer.rasterize(level, tiles, 0, cameraX, fgY, WIDTH, 1, 0);
                    // The zero selector writes all 32 blocks from layout X=0.
                    // loc_20343A (r3/stage_draw_1b.asm:632-638), loc_20355E (r3/stage_draw_1c.asm:632-638).
                    int[] bgPixels = PlaneRasterizer.rasterize(level, tiles, 1, staticRow ? 0 : bgX,
                            sourceY, staticRow ? STATIC_ROW_REPEAT : WIDTH, 1, 0);
                    for (int x = 0; x < WIDTH; x++) {
                        int sourceX = bgX + x;
                        int expected = bgPixels[staticRow ? Math.floorMod(sourceX, STATIC_ROW_REPEAT) : x];
                        if (fgPixels[x] != 0 || expected == 0 || sourceX < STATIC_ROW_REPEAT) continue;
                        compared++;
                        if (CczNativePictureCheck.level(expected) != CczNativePictureCheck.level(actual.argb(x, y))) {
                            mismatches++;
                        }
                    }
                }
                String label = payload + " " + kind + " rows beyond the 512px repeat";
                assertTrue(compared >= WIDTH, label + ": too few exposed pixels (" + compared + ")");
                assertEquals(0, mismatches, label + ": mismatching pixels out of " + compared);
                System.out.println(label + ": " + compared + " exposed pixels match the disc layout");
            }
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
    }
}
