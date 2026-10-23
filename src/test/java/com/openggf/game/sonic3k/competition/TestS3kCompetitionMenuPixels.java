package com.openggf.game.sonic3k.competition;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;

@Isolated
class TestS3kCompetitionMenuPixels {
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void actualRomMenusUseTheirNativePlanesMappingsAndIntensityWithoutAdvancingTheWorld() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            capture.step(null); capture.step(null);
            var context = SessionManager.getCurrentGameplayMode();
            int frame = context.getLevelManager().getFrameCounter();
            var camera = context.getCamera().capture();
            var graphics = GameServices.graphics();
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            var rom = GameServices.rom().getRom();
            var composer = new S3kCompetitionMenuScene(rom, new S3kCompetitionAssets(rom));
            var records = new S3kCompetitionRecords(new byte[80]);
            int unpack = glGenBuffers();
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, 4L, GL_STREAM_DRAW);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/capture/s3k-competition/menus"));
            try (var renderer = new S3kCompetitionMenuRenderer(graphics)) {
                for (var phase : List.of(S3kCompetitionScreen.Phase.MODE, S3kCompetitionScreen.Phase.PLAYER_SELECT,
                        S3kCompetitionScreen.Phase.ARENA_SELECT, S3kCompetitionScreen.Phase.RESULTS,
                        S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS)) {
                    var mode = phase == S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS
                            ? S3kCompetitionSession.Mode.TIME_ATTACK : S3kCompetitionSession.Mode.GRAND_PRIX;
                    var view = new S3kCompetitionScreen.MenuView(phase, 8, 0, true, mode, 0, 2,
                            List.of(0, 1), List.of(0x40, 0x95), List.of(false, false), 0, 0, result(mode));
                    var scene = composer.compose(view, records);
                    var picture = S3kCompetitionMenuRenderer.rasterize(scene);
                    int[] expected = picture.rgb(scene.palettes(), new PaletteFadePresentation(),
                            DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL);
                    glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT);
                    glEnable(GL_SCISSOR_TEST); glScissor(7, 9, 11, 13);
                    int target = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                    PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
                        // Seed at command execution, after the outer screen compositor's FBO setup.
                        graphics.registerCommand((cx, cy, width, height) -> {
                            assertEquals(GL_NO_ERROR, glGetError(), "before hostile menu input");
                            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
                            glPixelStorei(GL_UNPACK_ALIGNMENT, 8);
                            glPixelStorei(GL_UNPACK_ROW_LENGTH, 2);
                            glPixelStorei(GL_UNPACK_SKIP_ROWS, 1);
                            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 1);
                        });
                        renderer.draw(scene);
                        graphics.registerCommand((cx, cy, width, height) -> {
                    assertEquals(unpack, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
                    assertEquals(8, glGetInteger(GL_UNPACK_ALIGNMENT));
                    assertEquals(2, glGetInteger(GL_UNPACK_ROW_LENGTH));
                    assertEquals(1, glGetInteger(GL_UNPACK_SKIP_ROWS));
                    assertEquals(1, glGetInteger(GL_UNPACK_SKIP_PIXELS));
                            assertEquals(GL_NO_ERROR, glGetError(), "menu command boundary");
                            // Retire this fixture's hostile state before unrelated flush cleanup.
                            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                        });
                        graphics.flushScreenSpace();
                    });
                    assertEquals(target, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                    assertTrue(glIsEnabled(GL_SCISSOR_TEST));
                    int[] clip = new int[4]; glGetIntegerv(GL_SCISSOR_BOX, clip);
                    assertArrayEquals(new int[]{7, 9, 11, 13}, clip);
                    glDisable(GL_SCISSOR_TEST);
                    assertEquals(GL_NO_ERROR, glGetError(), "outer flush cleanup");
                    var actual = ScreenshotCapture.captureFramebuffer(320, 224);
                    ScreenshotCapture.savePNG(actual, java.nio.file.Path.of("target/capture/s3k-competition/menus/"
                            + phase.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
                    for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++)
                        assertEquals(0xFF000000 | expected[y * 320 + x], actual.argb(x, y), phase + " " + x + "," + y);
                    assertTrue(java.util.Arrays.stream(expected).distinct().count() > 8, "native scene contains ROM art");
                    assertEquals(GL_NO_ERROR, glGetError());
                }
                var pending = composer.compose(new S3kCompetitionScreen.MenuView(S3kCompetitionScreen.Phase.MODE,
                        8, 0, true, S3kCompetitionSession.Mode.GRAND_PRIX, 0, 2,
                        List.of(0, 1), List.of(0x40, 0x95), List.of(false, false), 0, 0, null), records);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                renderer.draw(pending);
                renderer.close();
                assertThrows(IllegalStateException.class, () -> renderer.draw(pending));
                glClearColor(1, 1, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
                graphics.flushScreenSpace();
                assertEquals(0xFFFFFF00, ScreenshotCapture.captureFramebuffer(320, 224).argb(160, 112),
                        "closed renderer discards already queued commands");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); glDeleteBuffers(unpack);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4); glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0); glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            }
            assertEquals(frame, context.getLevelManager().getFrameCounter());
            assertEquals(camera, context.getCamera().capture());
        }
    }

    private static S3kCompetitionSession.Result result(S3kCompetitionSession.Mode mode) {
        var times = List.of(101, 202, 303, 404, 505);
        var first = new S3kCompetitionSession.PlayerResult(0, S3kCompetitionPlayer.Character.SONIC,
                5, times, 1515, true, 0);
        var second = new S3kCompetitionSession.PlayerResult(1, S3kCompetitionPlayer.Character.TAILS,
                5, times, 1515, true, 0);
        return new S3kCompetitionSession.Result(mode, S3kCompetitionSession.Arena.AZURE_LAKE,
                mode == S3kCompetitionSession.Mode.TIME_ATTACK ? List.of(first) : List.of(first, second),
                List.of(times, times));
    }
}
