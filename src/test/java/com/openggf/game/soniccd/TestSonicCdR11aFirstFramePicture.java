package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Gate B: a production-renderer picture of Palmtree Panic Act 1 Present against the native
 * BizHawk frame {@code player-reference-r1/final/observed-1/postframe-02590.png}.
 *
 * <p>That native frame is levelFrames 188 (trace row 201 of the same retail route): Sonic
 * idles at ($40,$76A), mapping frame 1, camera and background camera ($0,$70A). It is the
 * earliest captured native gameplay frame; the camera has not moved since levelFrames 2, so
 * terrain, palette and Sonic are comparable. HUD and object sprites are not hosted yet, so
 * their boxes are excluded; the background has no deformation yet, so it is reported separately.
 */
@EnabledOnOs(OS.LINUX)
class TestSonicCdR11aFirstFramePicture {
    /**
     * The native frame is levelFrames 188's picture, which shows the state after level frame 187 (the V-INT that starts
     * the video frame DMAs what the previous iteration wrote, Level/Main.asm:829-831). The loaded stage is level frame 1
     * ({@code CdFirstMainLoopIteration}), so the engine steps to 187.
     */
    private static final int ENGINE_LEVEL_FRAME = 188 - 1;

    @Test
    void foregroundTerrainPaletteAndSonicMatchTheNativeFrame() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path nativeFrame = CdTestMedia.nativeFile("player-reference-r1/final/observed-1/postframe-02590.png");
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A), SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            for (int frame = 1; frame < ENGINE_LEVEL_FRAME; frame++) loop.step();
            assertEquals(0x70A, GameServices.camera().getY() & 0xFFFF, "camera Y at the compared frame");

            GraphicsManager graphics = GameServices.graphics();
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            var level = GameServices.level();
            level.setClearColor();
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            level.drawWithSpritePriority(GameServices.sprites(), true);
            graphics.flush();
            if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
            glFinish();
            RgbaImage engine = ScreenshotCapture.captureFramebuffer(320, 224);
            RgbaImage reference = ScreenshotCapture.loadPNG(nativeFrame);

            Path out = Path.of("target", "soniccd-gate-b");
            Files.createDirectories(out);
            ScreenshotCapture.savePNG(engine, out.resolve("engine-r11a-levelframes-188.png"));
            ScreenshotCapture.savePNG(ScreenshotCapture.createDiffImage(reference, engine, 0), out.resolve("diff.png"));

            int[] foreground = compare(reference, engine, 0, 168, 320, 224, true);   // ground band, below the BG horizon
            int[] sonic = compare(reference, engine, 40, 72, 80, 120, true);         // Sonic and the ground beside him
            int[] whole = compare(reference, engine, 0, 0, 320, 224, true);
            String report = String.format("foreground %d/%d, Sonic box %d/%d, whole frame (HUD/rings excluded) %d/%d",
                    foreground[0], foreground[1], sonic[0], sonic[1], whole[0], whole[1]);
            System.out.println("Gate B: " + report);
            Files.writeString(out.resolve("report.txt"), report + System.lineSeparator());
            assertTrue(foreground[0] * 100L >= foreground[1] * 99L, "foreground terrain/palette: " + report);
            assertTrue(sonic[0] * 100L >= sonic[1] * 99L, "Sonic: " + report);
            // Rings and one monitor are unhosted objects; the background lacks deformation.
            assertTrue(whole[0] * 1000L >= whole[1] * 995L, "whole frame: " + report);
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    /** {matching, compared} pixels by 3-bit-per-channel colour, skipping the HUD and lives boxes. */
    private static int[] compare(RgbaImage reference, RgbaImage engine, int x0, int y0, int x1, int y1, boolean skipHud) {
        int same = 0, total = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (skipHud && ((x < 104 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40))) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) same++;
            }
        }
        return new int[] {same, total};
    }

    private static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }
}
