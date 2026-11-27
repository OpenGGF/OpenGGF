package com.openggf.game.sonic2.specialstage;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Obj5F START banner's flying pieces stay displayed while their centre is
 * inside 0..$100 ({@code loc_710A}, s2.asm:9637-9653), so the outer flags
 * straddle the edge of the H32 display (VDP $8C08, s2.asm:6564) as they leave.
 * The hardware shows nothing past that 256-pixel raster; the pillarbox around
 * the centred H32 region must stay black on every frame of the ordinary intro.
 */
@org.junit.jupiter.api.parallel.Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageStartBannerRasterClip {
    private static final int HEIGHT = GameplayCaptureSession.HEIGHT;

    @BeforeEach
    void requireExplicitIsolatedRendering() {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "requires explicitly selected display-free surfaceless EGL");
    }

    @ParameterizedTest(name = "START banner flyout stays inside H32, outer width {0}")
    @ValueSource(ints = {320, 528})
    void startBannerFlyoutNeverDrawsOutsideTheH32Raster(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 1, settings);
            for (int frame = 0; frame < 240 && GameServices.fade().isActive(); frame++) {
                session.step(null);
                session.renderFrame();
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            session.loop().enterSpecialStage();

            Sonic2SpecialStageProvider provider = null;
            int flyoutFrames = 0;
            int visibleFlyoutFrames = 0;
            for (int frame = 0; frame < 1200; frame++) {
                session.step(null);
                if (session.loop().getCurrentGameMode() != GameMode.SPECIAL_STAGE
                        || !(session.loop().getActiveSpecialStageProvider() instanceof Sonic2SpecialStageProvider p)
                        || !p.isEntryPresentationReady() || p.isEntryFadeToWhiteActive()
                        || GameServices.fade().isActive()) {
                    session.renderFrame();
                    continue;
                }
                provider = p;
                var intro = p.getManager().getIntro();
                if (intro.isComplete()) break;
                RgbaImage image = session.render();
                assertBorderBlack(image, width, p.getSpecialStageViewport(), frame);
                if (intro.isBannerInFlyoutPhase()) {
                    flyoutFrames++;
                    if (intro.getBannerLetters().stream().anyMatch(letter -> letter.visible)) visibleFlyoutFrames++;
                }
            }
            assertNotNull(provider, "ordinary S2 entry must reach the live stage presentation");
            assertTrue(provider.getManager().getIntro().isComplete(), "the intro must run to completion");
            assertTrue(flyoutFrames > 0 && visibleFlyoutFrames > 0,
                    "the comparison must cover the banner pieces' flight off the H32 edge");
        }
    }

    private static void assertBorderBlack(RgbaImage image, int width, SpecialStageViewport viewport, int frame) {
        assertEquals(width, image.width());
        assertEquals(HEIGHT, image.height());
        var inner = viewport.innerH32();
        assertEquals((width - SpecialStageViewport.H32_WIDTH) / 2, inner.x());
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= inner.x() && x < inner.x() + inner.width()) continue;
                int rgb = image.argb(x, y) & 0xFFFFFF;
                if (rgb != 0) {
                    fail(String.format("frame %d: pixel outside the H32 raster at (%d,%d) is %06X",
                            frame, x, y, rgb));
                }
            }
        }
    }
}
