package com.openggf.mods.scene.host;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.render.DetachedBackground;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestRomSceneBackground {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonicOneUsesNativeLayeredScrollWithoutLiveServices() throws Exception {
        check(new Sonic1GameModule(), new int[] {2, 4});
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonicTwoUsesNativeCloudsRippleAndCaveScrollWithoutLiveServices() throws Exception {
        check(new Sonic2GameModule(), new int[] {0, 1, 5});
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonicThreeUsesNativeScrollAndPopulatedWideBackgroundPeriods() throws Exception {
        check(new Sonic3kGameModule(), new int[] {0, 1, 6, 11});
    }

    private void check(GameModule module, int[] zones) throws Exception {
        var source = module.getGameService(ZonePictureSource.Factory.class).create(TestEnvironment.currentRom());
        try (var services = Mockito.mockStatic(GameServices.class, invocation -> {
            throw new AssertionError("Detached background touched GameServices." + invocation.getMethod().getName());
        })) {
            for (int zone : zones) {
                DetachedBackground expected = source.background(zone, 0);
                assertNotNull(expected);
                DetachedBackground rendered = source.background(zone, 0);
                assertNotSame(expected, rendered, "Each view owns independent scroll state");
                var view = new RomSceneBackground(rendered);
                for (int width : new int[] {320, 400}) {
                    for (int[] camera : new int[][] {{0, 0}, {511, 287}, {512, 304}, {1023, 900}, {3072, 2000}}) {
                        int x = camera[0], y = camera[1];
                        int[] actual = render(view, width, x, y, x + y);
                        expected.update(x, y, x + y);
                        int[] art = expected.picture(width).argb();
                        for (int py = 0; py < 224; py++) for (int px = 0; px < width; px++) {
                            int sx = Math.floorMod(px + expected.sourceX(py), expected.periodWidth(width));
                            int sy = Math.floorMod(expected.sourceY(py), expected.periodHeight());
                            assertEquals(art[sy * expected.picture(width).width() + sx], actual[py * width + px],
                                    "Native scanline sampling at zone " + zone + ", camera " + x + "," + y);
                        }
                        assertArrayEquals(actual, render(view, width, x, y, x + y), "Repeated draw is idempotent");
                    }
                }
            }
        }
    }

    static int[] render(RomSceneBackground view, int width, int x, int y, long tick) {
        int[] pixels = new int[width * 224];
        boolean[] covered = new boolean[pixels.length];
        SceneCanvas canvas = (SceneCanvas) Proxy.newProxyInstance(TestRomSceneBackground.class.getClassLoader(),
                new Class<?>[] {SceneCanvas.class}, (proxy, method, args) -> {
                    if (method.getName().equals("width")) return width;
                    if (method.getName().equals("height")) return 224;
                    assertEquals("drawRegion", method.getName());
                    SceneImage image = (SceneImage) args[0];
                    int sx = (int) args[1], sy = (int) args[2], w = (int) args[3], h = (int) args[4];
                    int dx = ((Number) args[5]).intValue(), dy = ((Number) args[6]).intValue();
                    assertEquals(w, ((Number) args[7]).intValue());
                    assertEquals(h, ((Number) args[8]).intValue(), "No stretching");
                    assertTrue(sx >= 0 && sx + w <= image.width() && sy >= 0 && sy + h <= image.height());
                    for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
                        int index = (dy + py) * width + dx + px;
                        assertFalse(covered[index], "No overlapping strips");
                        covered[index] = true;
                        pixels[index] = image.pixel(sx + px, sy + py);
                    }
                    return null;
                });
        view.draw(canvas, x, y, tick);
        for (boolean pixel : covered) assertTrue(pixel, "Every pixel, including bottom and right edges, is drawn");
        return pixels;
    }
}
