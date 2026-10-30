package com.openggf.tools;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

/** Capture's canonical step owner must publish ending scroll even without pictures.
 * Pixels belong to TestEndingBackgroundDrawBoundary's real Engine draw witnesses;
 * this case does not certify capture's separate ending rendering support. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2EndingCaptureUpdateBoundary {
    @Test
    void canonicalCaptureStepsPublishFirstAndMovingSkyThenRetireItForCredits() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 10, 0, settings);
            var level = GameServices.level();
            level.consumeTitleCardRequest();
            GameServices.fade().cancel();
            level.requestCreditsTransition(); // Declared ending ingress, not a boss-clear claim.
            session.step(null);
            var loop = session.loop();
            assertEquals(GameMode.ENDING_CUTSCENE, loop.getCurrentGameMode());
            var provider = loop.getEndingProvider();
            int photoFrame = level.getFrameCounter();
            for (int tick = 0; tick < 2200 && !provider.needsLevelBackground(); tick++) {
                assertEquals(photoFrame, level.getFrameCounter());
                session.step(null);
            }
            assertTrue(provider.needsLevelBackground());
            assertEquals(photoFrame + 1, level.getFrameCounter());
            var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
            assertEquals(0xC8, scroll.getVscrollFactorBG() & 0xFFFF);
            assertEquals(0xFFFA, scroll.getHScroll()[0], "first native star row: -(+6), FG=0");
            assertEquals(0xFFFD, scroll.getHScroll()[8], "next native strip: -(+3)");
            int firstFrame = level.getFrameCounter();
            for (int tick = 0; tick < 100 && provider.getBackgroundVscroll() == 0xC8; tick++) session.step(null);
            assertEquals(0xC9, provider.getBackgroundVscroll());
            int passes = level.getFrameCounter() - firstFrame + 1;
            assertEquals(0xC9, scroll.getVscrollFactorBG() & 0xFFFF);
            assertEquals((-6 * passes) & 0xFFFF, scroll.getHScroll()[0]);
            assertEquals((-3 * passes) & 0xFFFF, scroll.getHScroll()[7], "new Y shortens first strip to seven lines");
            boolean retired = false;
            for (int tick = 0; tick < 5000 && provider.needsLevelBackground(); tick++) {
                int before = level.getFrameCounter();
                session.step(null);
                assertEquals(before + (provider.needsLevelBackground() ? 1 : 0), level.getFrameCounter());
                retired = !provider.needsLevelBackground();
            }
            assertTrue(retired);
            int terminal = level.getFrameCounter();
            var rows = scroll.getHScroll().clone();
            for (int tick = 0; tick < 100 && loop.getCurrentGameMode() != GameMode.CREDITS_TEXT; tick++) {
                session.step(null);
                assertEquals(terminal, level.getFrameCounter());
                assertArrayEquals(rows, scroll.getHScroll());
            }
            assertEquals(GameMode.CREDITS_TEXT, loop.getCurrentGameMode());
            assertFalse(provider.needsLevelBackground());
        }
    }
}
