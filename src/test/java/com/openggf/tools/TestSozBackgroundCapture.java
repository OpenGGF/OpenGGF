package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.graphics.RgbaImage;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SOZ's static nametable period must survive a viewport wider than the VDP plane. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozBackgroundCapture {
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void productionBackgroundPassKeepsItsPlanePeriodInWidescreen(int viewportWidth) throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                viewportWidth, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            session.step(null);
            var image = session.render(false);
            assertEquals(viewportWidth, image.width());
            var renderer = GameServices.graphics().getBackgroundRenderer();
            var width = BackgroundRenderer.class.getDeclaredField("renderWidth");
            width.setAccessible(true);
            assertEquals(512, width.getInt(renderer),
                    "BGTextureWidth controls the compositor's modulo, not the display width");
        }
    }

    @Test
    void exposedDesertSkyHasNoBlackStripAtTheWideRightEdge() throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                528, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            RgbaImage image = null;
            // Ordinary entry: jump/right for 20 frames, then keep moving right.
            // This exposes the wrap boundary before foreground terrain covers it.
            for (int frame = 0; frame <= 60; frame++) {
                boolean jump = frame < 20;
                session.step(new Bk2FrameInput(frame,
                        INPUT_RIGHT | (jump ? INPUT_JUMP : 0), jump ? 1 : 0, false, ""));
                image = session.render();
            }
            assertEquals(96, GameServices.camera().getX(), "exposed-sky camera boundary");
            for (int y = 50; y < 160; y++) {
                for (int x = 500; x < 528; x++) {
                    assertNotEquals(0, image.argb(x, y) & 0xFFFFFF,
                            "black seam in desert sky at " + x + "," + y);
                }
            }
        }
    }


    @ParameterizedTest
    @ValueSource(ints = {320, 528})
    void foregroundPixelsFollowRomShimmerAndRestore(int viewportWidth) throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                viewportWidth, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            for (int frame = 0; frame <= 150; frame++) {
                boolean jump = frame % 80 < 20;
                session.step(new Bk2FrameInput(frame,
                        INPUT_RIGHT | (jump ? INPUT_JUMP : 0), jump ? 1 : 0, false, ""));
                session.render();
            }
            var level = GameServices.level();
            var modes = GameServices.advancedRenderModeControllerOrNull();
            var savedModes = modes.capture();
            publishCurrentScroll();
            var shimmer = session.render(false);
            modes.clear();
            publishCurrentScroll();
            var plain = session.render(false);
            // Stay above the sloped ceiling edge, where transparent foreground
            // reveals independently scrolling BG pixels. The right wall is opaque
            // foreground terrain in this short
            // ordinary route. SOZ1 loc_55DF2 uses the shared ROM wave, not a
            // shader-generated sine; compare its precise row displacement.
            byte[] wave = GameServices.rom().getRom().readBytes(0x5077E, 64);
            int phase = ((level.getFrameCounter() >> 1)
                    + 2 * GameServices.camera().getY()) & 0x3E;
            int displacedPixels = 0;
            for (int y = 20; y < 60; y++) {
                int entry = (((phase >> 1) + y) & 31) * 2;
                int displacement = (short) (((wave[entry] & 255) << 8) | (wave[entry + 1] & 255));
                for (int x = viewportWidth - 70; x < viewportWidth - 24; x++) {
                    assertEquals(plain.argb(x - displacement, y), shimmer.argb(x, y),
                            "ROM foreground wave at " + x + "," + y);
                    if (shimmer.argb(x, y) != plain.argb(x, y)) displacedPixels++;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(displacedPixels > 100,
                    "registered haze must actually displace foreground pixels");
            modes.restore(savedModes);
            publishCurrentScroll();
            var restored = session.render(false);
            for (int y = 0; y < 224; y++) for (int x = 0; x < viewportWidth; x++)
                assertEquals(shimmer.argb(x, y), restored.argb(x, y), "restored render mode");
        }
    }

    private static void publishCurrentScroll() {
        com.openggf.level.LevelSpritePresentation.prepare(GameServices.level(), GameServices.sprites());
        com.openggf.level.LevelSpritePresentation.publish(GameServices.level(),
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL);
    }

}
