package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.title.CdTitleProgram;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glGetError;

/**
 * Native check for the hosted title screen: the engine's title picture against the native USA title
 * capture {@code native/usa-title-final-1800/run-1/frame-01500.ppm} (256x224, H32), recorded through the
 * same BIOS, SEGA-logo and opening-FMV Start presses as the stage fixtures.
 *
 * <p>Two comparisons. (1) Static: everything except the pixels that depend on the title's main-loop clock,
 * which the engine cannot align with native because it does not model native lag frames: the cloud lines
 * 0-95 (sub-CPU render count; DrawCloudsMap runs after DrawTilemaps, so cloud row 11 replaces the top
 * mountain row), the water lines 160-223 (scroll accumulators and palette cycle), the planet (hover table),
 * Sonic's arm (its wave runs in the first main-loop frames) and the PRESS START text (blink). It must
 * match at every engine main-loop frame.
 * (2) Full picture: every pixel, at the one engine frame whose main-loop clock best matches; the clock is
 * the only fitted input, it is reported, and the next-best frame must score clearly lower. A negative control
 * compares the static pixels with the native picture moved one pixel right, which must fail.
 */
@EnabledOnOs(OS.LINUX)
class TestCdTitlePicture {
    private static final int W = 256, H = 224, OFFSET = (320 - W) / 2;
    /** Fraction of compared pixels that must agree, in thousandths. */
    private static final int REQUIRED_PER_MILLE = 999;

    private record Shot(int frame, int halves, RgbaImage image) { }

    private record Score(int same, int total) {
        boolean passes() { return total > 10000 && same * 1000L >= total * (long) REQUIRED_PER_MILLE; }
        @Override public String toString() { return same + "/" + total; }
    }

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** Pixels whose colour depends on the main-loop clock (see the class comment). */
    static boolean clockDependent(int x, int y) {
        if (y < 96 || y >= 160) return true;                      // clouds; water
        if (x >= 226 - 24 && x < 226 + 24 && y < 64) return true;  // planet around (226, 24)
        if (x >= 132 && x < 176 && y < 128) return true;           // Sonic's arm around (140, 104)
        return x >= 83 - 48 && x < 83 + 96 && y >= 172 && y < 196; // PRESS START at (83, 180)
    }

    static RgbaImage ppm(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        int at = 0, fields = 0;
        int[] header = new int[3];
        StringBuilder token = new StringBuilder();
        at = 2;
        while (fields < 3) {
            char c = (char) data[at++];
            if (Character.isWhitespace(c)) {
                if (!token.isEmpty()) { header[fields++] = Integer.parseInt(token.toString()); token.setLength(0); }
            } else token.append(c);
        }
        int width = header[0], height = header[1];
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++, at += 3) {
            pixels[i] = 0xFF000000 | ((data[at] & 0xFF) << 16) | ((data[at + 1] & 0xFF) << 8) | (data[at + 2] & 0xFF);
        }
        return new RgbaImage(width, height, pixels);
    }

    private static Score compare(RgbaImage engine, RgbaImage reference, boolean staticOnly) {
        int same = 0, total = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (staticOnly && clockDependent(x, y)) continue;
                total++;
                if (level(engine.argb(OFFSET + x, y)) == level(reference.argb(x, y))) same++;
            }
        }
        return new Score(same, total);
    }

    private static RgbaImage capture(CdTitleScreen title) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        while (glGetError() != GL_NO_ERROR) { /* drain a previous boot's stale error */ }
        title.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        title.draw();
        graphics.flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    @Test
    void titlePictureMatchesTheNativeUsaCapture() throws Exception {
        Path nativeFrame = CdTestMedia.nativeFile("usa-title-final-1800/run-1/frame-01500.ppm");
        RgbaImage reference = ppm(nativeFrame);
        int[] moved = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 1; x < W; x++) moved[y * W + x] = reference.argb(x - 1, y);
        RgbaImage shifted = new RgbaImage(W, H, moved);
        assertEquals(W, reference.width(), "native title is H32");
        String oldGl = System.getProperty("openggf.headless.gl");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Path out = Path.of("target", "soniccd-title");
        Files.createDirectories(out);
        List<Shot> shots = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            boot.setBootToTitle(true);
            GameLoop loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
            CdTitleScreen title = assertInstanceOf(CdTitleScreen.class, loop.getTitleScreenProvider());
            for (int frame = 1; frame <= 600; frame++) {
                loop.step();
                CdTitleProgram program = title.program();
                if (program.phase().ordinal() >= CdTitleProgram.Phase.MENU_A1.ordinal() && program.halves() >= 1) {
                    shots.add(new Shot(frame, program.halves(), capture(title)));
                }
            }
        } finally {
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        assertTrue(shots.size() > 100, "the title reached its main loop");

        Shot best = null, second = null;
        Score bestScore = null, secondScore = null;
        StringBuilder report = new StringBuilder();
        int staticFailures = 0;
        Score worstStatic = null;
        for (Shot shot : shots) {
            Score full = compare(shot.image(), reference, false);
            Score still = compare(shot.image(), reference, true);
            if (!still.passes()) staticFailures++;
            if (worstStatic == null || still.same() * (long) worstStatic.total() < worstStatic.same() * (long) still.total()) {
                worstStatic = still;
            }
            report.append(String.format("frame %d halves %d full %s static %s%n", shot.frame(), shot.halves(), full, still));
            if (bestScore == null || full.same() > bestScore.same()) {
                second = best; secondScore = bestScore;
                best = shot; bestScore = full;
            } else if (secondScore == null || full.same() > secondScore.same()) {
                second = shot; secondScore = full;
            }
        }
        ScreenshotCapture.savePNG(best.image(), out.resolve("engine-best.png"));
        Score control = compare(best.image(), shifted, true);
        String summary = String.format("best frame %d (main-loop half %d): full %s; next best frame %d: %s; "
                        + "static worst %s over %d frames (%d failing); one-pixel-shift control static %s%n",
                best.frame(), best.halves(), bestScore, second.frame(), secondScore, worstStatic, shots.size(),
                staticFailures, control);
        Files.writeString(out.resolve("report.txt"), summary + report);
        System.out.println("CD title picture: " + summary);
        assertEquals(0, staticFailures, "static title pixels must match native at every main-loop frame: " + summary);
        assertTrue(bestScore.passes(), "full title picture at the fitted main-loop clock: " + summary);
        assertTrue(bestScore.total() - bestScore.same() < (secondScore.total() - secondScore.same()) / 2,
                "the fitted clock must be distinct from the next best: " + summary);
        assertTrue(!control.passes(), "the comparison accepted a picture moved by one pixel: " + summary);
    }
}
