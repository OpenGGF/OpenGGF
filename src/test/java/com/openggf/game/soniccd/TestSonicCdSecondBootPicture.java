package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glGetError;

/**
 * A second Sonic CD boot in one process draws what the first drew. Each {@code HeadlessGameBoot} makes a new GL
 * context and closes it without the graphics owner's cleanup; GL object names restart in the new context, so a name
 * an owner kept from the closed one names nothing or another object there. Before {@code GraphicsManager.init} forgot
 * them, the second boot's first draw hit the parallax background's scroll textures (a 1D texture name now a 2D
 * texture), its shader program and quad VAO, and failed the native SAT stencil save with GL error 0x502. Here R11A is
 * booted twice, three frames drawn each time: every second-boot frame must equal the first boot's, pixel for pixel,
 * with no GL error.
 */
@EnabledOnOs(OS.LINUX)
class TestSonicCdSecondBootPicture {
    private static final int FRAMES = 3;

    @Test
    void aSecondBootDrawsTheFramesTheFirstDrew() throws Exception {
        java.nio.file.Path cue = CdTestMedia.usaCue();
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<List<int[]>> boots = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            for (int b = 0; b < 2; b++) {
                List<int[]> frames = new ArrayList<>();
                try (CdFreshStageBoots boot = new CdFreshStageBoots(320, 224)) {
                    boot.boot(disc, CdStagePayload.R11A);
                    HeadlessTestRunner runner = new HeadlessTestRunner(
                            (AbstractPlayableSprite) GameServices.camera().getFocusedSprite());
                    for (int f = 0; f < FRAMES; f++) {
                        runner.stepIdleFrames(1);
                        frames.add(pixels(render()));
                        assertEquals(GL_NO_ERROR, glGetError(), "GL error after boot " + (b + 1) + " frame " + f);
                    }
                }
                boots.add(frames);
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        for (int f = 0; f < FRAMES; f++) {
            assertArrayEquals(boots.get(0).get(f), boots.get(1).get(f), "frame " + f + " of the second boot");
        }
    }

    private static int[] pixels(RgbaImage image) {
        int[] out = new int[320 * 224];
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) out[y * 320 + x] = image.argb(x, y);
        return out;
    }

    private static RgbaImage render() {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
        graphics.flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
