package com.openggf.tools;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pixel readback must be optional without suppressing any frame's real drawing. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureFrameRendering {
    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void titleDrawingWithoutReadbackPreservesTheNativeTitlePixels() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            try (var readback = mockStatic(ScreenshotCapture.class, CALLS_REAL_METHODS)) {
                for (int frame = 0; frame < 600; frame++) {
                    session.step(null);
                    session.renderFrame();
                }
                readback.verifyNoInteractions();
            }
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
            RgbaImage drawn = ScreenshotCapture.captureFramebuffer(320, GameplayCaptureSession.HEIGHT);
            RgbaImage captured = session.render();
            assertArrayEquals(captured.pixels(), drawn.pixels());
            assertVisiblePixels(drawn);
        }
    }

    @Test
    void resultsDrawingWithoutReadbackPreservesTheNativeResultsPixels() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            for (int frame = 0; frame < 240 && GameServices.fade().isActive(); frame++) {
                session.step(null);
                session.renderFrame();
            }
            assertFalse(GameServices.fade().isActive(), "initial level reveal must finish before stage entry");
            session.loop().enterSpecialStage();
            for (int frame = 0; frame < 1200
                    && session.loop().getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS; frame++) {
                session.step(null);
                session.renderFrame();
            }
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, session.loop().getCurrentGameMode());
            for (int frame = 0; frame < 120; frame++) {
                session.step(null);
                session.renderFrame();
            }
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, session.loop().getCurrentGameMode());
            RgbaImage captured = session.render();
            try (var readback = mockStatic(ScreenshotCapture.class, CALLS_REAL_METHODS)) {
                session.renderFrame();
                readback.verifyNoInteractions();
            }
            RgbaImage drawn = ScreenshotCapture.captureFramebuffer(320, GameplayCaptureSession.HEIGHT);
            assertArrayEquals(captured.pixels(), drawn.pixels());
            assertVisiblePixels(drawn);
        }
    }

    @ParameterizedTest
    @CsvSource({"320,false", "800,false", "320,true", "800,true"})
    void drawingWithoutReadbackPreservesPixelsAndState(int width, boolean titles) throws Exception {
        Frame captured = run(width, titles, true);
        Frame drawn = run(width, titles, false);
        assertEquals(captured.state(), drawn.state());
        assertArrayEquals(captured.image().pixels(), drawn.image().pixels(),
                "drawing, sprites and visible title overlays must survive omission of readback");
        assertVisiblePixels(drawn.image());
    }

    private Frame run(int width, boolean titles, boolean captureEveryFrame) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off",
                null, null, null, null, titles, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            if (captureEveryFrame) {
                for (int frame = 0; frame < 80; frame++) {
                    session.step(null);
                    session.render();
                }
            } else {
                try (var readback = mockStatic(ScreenshotCapture.class, CALLS_REAL_METHODS)) {
                    for (int frame = 0; frame < 80; frame++) {
                        session.step(null);
                        session.renderFrame();
                    }
                    readback.verifyNoInteractions();
                }
            }
            if (titles) {
                assertNotNull(session.loop().getTitleCardProvider());
                assertTrue(session.loop().getCurrentGameMode() == GameMode.TITLE_CARD
                        || session.loop().getTitleCardProvider().isOverlayActive(),
                        "the comparison must exercise an active title overlay");
            }
            return new Frame(session.stateLine(80, null),
                    ScreenshotCapture.captureFramebuffer(width, GameplayCaptureSession.HEIGHT));
        }
    }

    private void assertVisiblePixels(RgbaImage image) {
        long colours = java.util.Arrays.stream(image.pixels()).distinct().count();
        assertTrue(colours > 1,
                "a uniform framebuffer cannot prove render parity; colours=" + colours);
    }

    private record Frame(String state, RgbaImage image) { }
}
