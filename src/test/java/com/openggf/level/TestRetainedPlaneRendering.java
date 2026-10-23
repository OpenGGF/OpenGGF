package com.openggf.level;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Actual LevelRenderer/native-view consumption, with an independent known-pixel oracle. */
@Isolated
class TestRetainedPlaneRendering {
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void nativeViewsWrapBothAuthoritativePlanesAtTheirExactWidthAndHeight() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings);
             var renderer = new NativeCompetitionRenderer()) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            session.step(null);
            session.step(null);
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            var context = SessionManager.getCurrentGameplayMode();
            var lm = context.getLevelManager();
            var previousProvider = lm.zoneFeatureProvider;
            var camera = context.getCamera().capture();
            int frame = lm.getFrameCounter();
            var graphics = GameServices.graphics();
            assertTrue(lm.getCurrentLevel().getPatternCount() > 2);
            var asymmetric = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                asymmetric.setPixel(x, y, (byte) (1 + (x / 4) + 2 * (y / 4)));
            }
            graphics.cachePatternTexture(asymmetric, 1);
            graphics.cachePatternTexture(new Pattern(), 2); // Hidden plane is explicitly transparent.
            for (int palette = 0; palette < 4; palette++) {
                var row = new Palette();
                for (int index = 1; index <= 4; index++) {
                    int rgb = rgb(palette, index);
                    row.setColor(index, new Palette.Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb));
                }
                graphics.cachePaletteTexture(row, palette);
            }
            lm.tilemapManager.setPatternLookupDirty(true);
            try {
                for (int[] size : new int[][]{{128, 32}, {64, 64}}) {
                    for (boolean foreground : new boolean[]{true, false}) {
                        var visible = new TestRetainedPlaneDescriptors.MutablePlane(size[0], size[1]);
                        var hidden = new TestRetainedPlaneDescriptors.MutablePlane(size[0], size[1]);
                        Arrays.fill(hidden.words, 2);
                        for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
                            visible.words[y * size[0] + x] = 1 | (((x + x / 64 + y / 16) & 3) << 13)
                                    | ((x & 4) != 0 ? 0x800 : 0) | ((y & 1) != 0 ? 0x1000 : 0)
                                    | (((x + y) & 1) != 0 ? 0x8000 : 0);
                        }
                        lm.zoneFeatureProvider = TestRetainedPlaneDescriptors.features(
                                foreground ? visible : hidden, foreground ? hidden : visible);
                        int x = size[0] * 8 - 8;
                        int y = size[1] * 8 - 8;
                        assertPixels(draw(renderer, x, y), visible, x, y, foreground);
                        assertEquals((float) size[1], graphics.getTilemapGpuRenderer().getBgVdpWrapHeight());
                        byte[] fgBytes = lm.tilemapManager.getForegroundTilemapData();
                        byte[] bgBytes = lm.tilemapManager.getBackgroundTilemapData();
                        int generation = graphics.getTilemapGpuRenderer().getBackgroundContentGeneration();
                        int reads = visible.reads;
                        // Distinct camera/native-register page, including BPZ's row31 -> row32.
                        // An accidental 32-row BG wrap cannot satisfy the known colors here.
                        assertPixels(draw(renderer, 505, 248), visible, 505, 248, foreground);
                        assertSame(fgBytes, lm.tilemapManager.getForegroundTilemapData());
                        assertSame(bgBytes, lm.tilemapManager.getBackgroundTilemapData());
                        assertEquals(generation, graphics.getTilemapGpuRenderer().getBackgroundContentGeneration());
                        assertEquals(reads, visible.reads, "view movement cannot regenerate plane descriptors");
                        visible.words[0] = 0xE001;
                        visible.revision++;
                        assertPixels(draw(renderer, 0, 0), visible, 0, 0, foreground);
                    }
                }
                assertEquals(camera, context.getCamera().capture(), "native rendering does not advance the camera");
                assertEquals(frame, lm.getFrameCounter());
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                lm.zoneFeatureProvider = previousProvider;
            }
        }
    }

    private static RgbaImage draw(NativeCompetitionRenderer renderer, int x, int y) {
        var context = SessionManager.getCurrentGameplayMode();
        var scroll = new int[224];
        Arrays.fill(scroll, ((-x & 0xFFFF) << 16) | (-x & 0xFFFF));
        var view = new NativeCompetitionRenderer.View(context.getCamera().capture(), 0, 224,
                y, y, x, 512, scroll, SpritePresentation.Frame.empty());
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(),
                () -> renderer.draw(context, List.of(view), 0x123456));
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void assertPixels(RgbaImage image, TestRetainedPlaneDescriptors.MutablePlane plane,
                                     int offsetX, int offsetY, boolean foreground) {
        int[] xs = {0, 1, 3, 4, 7, 8, 9, 15, 16, 63, 127, 255, 319};
        int[] ys = {0, 1, 3, 4, 7, 8, 9, 15, 16, 63, 127, 223};
        for (int y : ys) for (int x : xs) {
            int worldX = Math.floorMod(offsetX + x, plane.width * 8);
            int worldY = Math.floorMod(offsetY + y, plane.height * 8);
            int word = plane.words[(worldY / 8) * plane.width + worldX / 8];
            int tileX = worldX % 8, tileY = worldY % 8;
            if ((word & 0x800) != 0) tileX = 7 - tileX;
            if ((word & 0x1000) != 0) tileY = 7 - tileY;
            int index = 1 + tileX / 4 + 2 * (tileY / 4);
            int expected = 0xFF000000 | rgb((word >>> 13) & 3, index);
            assertEquals(expected, image.argb(x, y), (foreground ? "FG " : "BG ")
                    + plane.width + "x" + plane.height + " source=" + worldX + "," + worldY
                    + " pixel=" + x + "," + y);
        }
    }

    private static int rgb(int palette, int index) {
        return ((35 + palette * 50) << 16) | ((40 + index * 45) << 8) | (220 - palette * 35 - index * 10);
    }
}
