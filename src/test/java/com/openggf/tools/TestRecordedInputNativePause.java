package com.openggf.tools;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises native Pause_Game admission through the actual production loop. */
@RequiresRom(SonicGame.SONIC_2)
class TestRecordedInputNativePause {
    @Test
    void recordedStartPausesNativeGameplayOncePerEdgeAndUnpausesAtTheNextEdge() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            var level = GameServices.level();
            // Observe the production fresh-entry frontier rather than forcing
            // a mode or consuming native title/setup work in this test.
            int readyRows = 0;
            do {
                session.step(null);
                session.renderFrame();
                assertTrue(++readyRows < 600, "production level entry did not become ready");
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || level.hasPendingFreshLevelTransitionBoundary());
            var state = GameServices.gameState();
            assertFalse(state.isGamePaused());
            int before = GameServices.sprites().getFrameCounter();
            var startHeld = new Bk2FrameInput(0, 0, 0, true, "recorded Start");

            session.step(startHeld);
            assertTrue(state.isGamePaused(), "the owned recorded edge must reach native Pause_Game");
            assertFalse(session.loop().isUserPaused(), "native pause must not become host pause");
            assertEquals(before, GameServices.sprites().getFrameCounter(), "pause skips gameplay admission");
            assertEquals(PresentationMode.FORWARD, session.loop().presentationModeForOuterFrame(false, false),
                    "native pause retains the sound driver clock");

            session.step(startHeld);
            assertTrue(state.isGamePaused(), "holding Start must not invent a second edge");
            assertEquals(before, GameServices.sprites().getFrameCounter());
            session.step(null);
            assertTrue(state.isGamePaused(), "release alone does not unpause");
            assertEquals(before, GameServices.sprites().getFrameCounter());

            session.step(new Bk2FrameInput(3, 0, 0, true, "second recorded Start"));
            assertFalse(state.isGamePaused(), "the next real Start edge resumes native gameplay");
            assertFalse(session.loop().isUserPaused());
            assertEquals(before + 1, GameServices.sprites().getFrameCounter(),
                    "the unpause edge admits the same native gameplay iteration");
        }
    }
}
