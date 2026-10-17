package com.openggf.graphics;

import com.openggf.game.GameServices;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.PaletteDisplayColor;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * S3K Pal_IncColor2 / Pal_DecColor2 (skdisasm 044fa46, $3CF0..$3D2A and the
 * corresponding Pal_ToWhite body) differ: FromWhite lowers all components every
 * third V-int; ToWhite raises one component R/G/B per V-int. Both callers wait 22 V-ints. White is
 * a 3-bit palette value, before the user's colour profile and expansion.
 * These component inlets are deliberately independent of any BK2/frame index.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeWhitePaletteFade {
    @AfterEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @CsvSource({"RAW_RGB,FULL", "RAW_RGB,LIMITED", "MD_ANALOG,FULL",
            "MD_ANALOG,LIMITED", "NTSC_SOFT,FULL", "NTSC_SOFT,LIMITED"})
    void everyGenesisColourUsesNativeComponentPassesBeforeDisplayConversion(
            DisplayColorProfile profile, DisplayColorExpansion expansion) throws Exception {
        withSession(profile, expansion, (session, graphics) -> {
            var fade = GameServices.fade();
            var row = new Palette();
            graphics.cachePaletteTexture(row, 0);
            Integer texture = graphics.getCombinedPaletteTextureId();
            assertNotNull(texture);
            glBindTexture(GL_TEXTURE_2D, texture);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            ByteBuffer readback = ByteBuffer.allocateDirect(16 * height * 4);
            for (boolean fromWhite : new boolean[]{true, false}) {
                fade.cancel();
                if (fromWhite) fade.startFadeFromWhite(null);
                else fade.startFadeToWhite(() -> { });
                for (int frame = 0; frame <= 22; frame++) {
                    if (frame > 0) fade.update();
                    for (int first = 0; first < 512; first += 16) {
                        for (int i = 0; i < 16; i++) {
                            int bits = first + i;
                            row.setColor(i, colour(word(bits & 7, (bits >>> 3) & 7, (bits >>> 6) & 7)));
                        }
                        // Exercise the actual CRAM upload, not the converter-only test helper.
                        graphics.cachePaletteTexture(row, 0);
                        glBindTexture(GL_TEXTURE_2D, texture);
                        readback.clear();
                        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, readback);
                        for (int i = 0; i < 16; i++) {
                            int bits = first + i;
                            int r = bits & 7, g = (bits >>> 3) & 7, b = (bits >>> 6) & 7;
                            int expectedWord = nativeWord(r, g, b, frame, fromWhite);
                            Palette.Color expected = colour(expectedWord);
                            int packed = DisplayColorConverter.toPackedRgb(expected.r & 255, expected.g & 255,
                                    expected.b & 255, profile, expansion);
                            int offset = i * 4;
                            int actual = ((readback.get(offset) & 255) << 16)
                                    | ((readback.get(offset + 1) & 255) << 8) | (readback.get(offset + 2) & 255);
                            if (actual != packed) fail("native white fade: from=" + fromWhite + " frame=" + frame
                                    + " ROM word=$" + Integer.toHexString(word(r, g, b)) + " expected RGB=$"
                                    + Integer.toHexString(packed) + " actual=$" + Integer.toHexString(actual));
                            assertEquals(i == 0 ? 0 : 255, readback.get(offset + 3) & 255);
                        }
                    }
                }
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"RAW_RGB,FULL", "RAW_RGB,LIMITED", "MD_ANALOG,FULL",
            "MD_ANALOG,LIMITED", "NTSC_SOFT,FULL", "NTSC_SOFT,LIMITED"})
    void nativeBackdropAndFramebufferUseTheSameWhiteFloorWithoutAnOverlay(
            DisplayColorProfile profile, DisplayColorExpansion expansion) throws Exception {
        withSession(profile, expansion, (session, graphics) -> {
            var fade = GameServices.fade();
            fade.startFadeFromWhite(null);
            fade.update();
            fade.update();
            // Pal_DecColor2 has run once: $EEE -> $CCC for a black target.
            Palette.Color expectedColour = colour(0x0CCC);
            int expected = DisplayColorConverter.toPackedRgb(expectedColour.r & 255,
                    expectedColour.g & 255, expectedColour.b & 255, profile, expansion);
            assertEquals(expected, PaletteDisplayColor.packed(colour(0), graphics),
                    "VDP colour zero follows the same displayed CRAM generation");
            PaletteDisplayColor.glClear(colour(0), graphics);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            fade.render();
            glFinish();
            int pixel = ScreenshotCapture.captureFramebuffer(320, 224).argb(0, 0) & 0xFFFFFF;
            assertEquals(expected, pixel, "a sequential/additive fullscreen overlay must not change native CRAM RGB");
        });
    }

    @Test
    void pendingAndDisplayedWhiteGenerationsRestoreAndReplayIndependently() throws Exception {
        withSession(DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED, (session, graphics) -> {
            var fade = GameServices.fade();
            fade.startFadeFromWhite(null);
            PaletteUploadPresentation.publishAndLatch(graphics);
            for (int frame = 1; frame <= 4; frame++) tick(graphics, fade);
            var snapshot = fade.capture();
            var displayed = PaletteUploadPresentation.captureDisplayedFade(graphics);
            int before = PaletteDisplayColor.packed(colour(0), graphics);
            assertEquals(0xCCCCCC, before, "frame 4 wrote $AAA; displayed V-int still has frame 3's $CCC");
            int[] expected = new int[8];
            for (int i = 0; i < expected.length; i++) {
                tick(graphics, fade);
                expected[i] = PaletteDisplayColor.packed(colour(0), graphics);
            }
            for (int replay = 0; replay < 2; replay++) {
                fade.restore(snapshot);
                PaletteUploadPresentation.restoreDisplayedFade(graphics, displayed);
                assertEquals(before, PaletteDisplayColor.packed(colour(0), graphics), "immediate displayed restore");
                fade.update(); // existing restore contract holds the restored presentation before execution resumes
                for (int i = 0; i < expected.length; i++) {
                    tick(graphics, fade);
                    assertEquals(expected[i], PaletteDisplayColor.packed(colour(0), graphics),
                            "white generation forward replay " + replay + "/" + i);
                }
            }
        });
    }

    @Test
    void fromWhiteWaitsForTheTwentySecondVintBeforeReturningToItsCaller() throws Exception {
        withSession(DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED, (session, graphics) -> {
            var completed = new java.util.concurrent.atomic.AtomicInteger();
            var fade = GameServices.fade();
            fade.startFadeFromWhite(completed::incrementAndGet);
            for (int frame = 1; frame < 22; frame++) {
                fade.update();
                assertEquals(0, completed.get(), "Pal_FadeFromWhite has not finished V-int " + frame);
                assertEquals(FadeManager.FadeState.FADING_FROM_WHITE, fade.getState());
            }
            fade.update();
            assertEquals(1, completed.get(), "dbf #$15 returns after 22 waits, including the no-op pass");
            assertEquals(FadeManager.FadeState.NONE, fade.getState());
        });
    }

    private static void tick(GraphicsManager graphics, FadeManager fade) {
        PaletteUploadPresentation.publishAndLatch(graphics);
        fade.update();
    }

    private static int nativeWord(int r, int g, int b, int frame, boolean fromWhite) {
        if (fromWhite) {
            int floor = Math.max(0, 7 - (frame + 2) / 3);
            return word(Math.max(r, floor), Math.max(g, floor), Math.max(b, floor));
        }
        // Pal_AddColor2 changes just the first nonwhite component. Closed form,
        // independent of the production per-pass loop, for the three native branches.
        int passes = Math.min(21, frame);
        int red = Math.min(7, r + passes);
        passes -= red - r;
        int green = Math.min(7, g + passes);
        passes -= green - g;
        return word(red, green, Math.min(7, b + passes));
    }

    private static int word(int r, int g, int b) {
        return (r << 1) | (g << 5) | (b << 9);
    }

    private static Palette.Color colour(int word) {
        var colour = new Palette.Color();
        colour.fromSegaFormat(word);
        return colour;
    }

    private static void withSession(DisplayColorProfile profile, DisplayColorExpansion expansion,
                                    CheckedScene check) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off",
                null, null, null, null, false, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            session.renderFrame();
            var graphics = GameServices.graphics();
            GameServices.fade().cancel();
            PaletteUploadPresentation.release(graphics); // blocking scene palettes publish immediately
            graphics.setDisplayColorProfile(profile);
            graphics.setDisplayColorExpansion(expansion);
            check.run(session, graphics);
        }
    }

    @FunctionalInterface
    private interface CheckedScene {
        void run(GameplayCaptureSession session, GraphicsManager graphics) throws Exception;
    }
}
