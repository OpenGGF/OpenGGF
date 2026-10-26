package com.openggf.game.sonic2.titlescreen;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.TitleScreenProvider;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 3584M/5735S describes P2 Start saving a boot frame at the last visible text.
 * This exercises the engine's final modeled non-black step (21/22), not a
 * synchronized native frame. TitleScreenManager's intro fade ignores input;
 * the unsupported capability is this P2-dependent intro transition. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2IntroP2Start {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @Test
    void currentLimitationP2StartAtIntroBoundarySavesNoFrame() throws Exception {
        int ordinary = toTitle(false);
        int p2 = toTitle(true);
        assertEquals(17, ordinary, "ordinary boundary-to-title control");
        assertEquals(ordinary, p2, "current limitation: modeled intro owner ignores P2 Start; both take 17");
    }
    private int toTitle(boolean pressP2) throws Exception {
        TestEnvironment.resetAll();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            var title = assertInstanceOf(TitleScreenManager.class, session.loop().getTitleScreenProvider());
            Field timer = TitleScreenManager.class.getDeclaredField("introTextTimer");
            timer.setAccessible(true);
            int ticks = 0;
            while (!(title.getState() == TitleScreenProvider.State.INTRO_TEXT_FADE_OUT
                    && timer.getInt(title) == 21) && ticks++ < 1000) com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertTrue(ticks < 1000, "must naturally reach the final modeled intro-text fade step");
            assertEquals(21, timer.getInt(title));
            com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(0, 0, 0, false, 0, 0, pressP2, "intro boundary"));
            int elapsed = 1;
            while (title.getState() != TitleScreenProvider.State.ACTIVE && elapsed < 400) {
                com.openggf.tests.reel.TestReelCapture.step(session,null); elapsed++;
            }
            assertTrue(elapsed < 400, "must reach the title after the recorded input");
            return elapsed;
        }
    }
}
