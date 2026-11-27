package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Exercises the production GameLoop mode/fade/reload path, not just provider counters. */
class TestContinueScreenRoundTrip {
    @Nested @RequiresRom(SonicGame.SONIC_1)
    class Sonic1 {
        @Test void continueEarnedInSpecialStageSurvivesGameOver() throws Exception { roundTrip(SonicGame.SONIC_1, true); }
        @Test void acceptedContinueReloadsAct() throws Exception { roundTrip(SonicGame.SONIC_1, false); }
    }
    @Nested @RequiresRom(SonicGame.SONIC_2)
    class Sonic2 {
        @Test void acceptedContinueReloadsAct() throws Exception { roundTrip(SonicGame.SONIC_2, false); }
    }
    @Nested @RequiresRom(SonicGame.SONIC_3K)
    class Sonic3k {
        @Test void acceptedContinueReloadsAct() throws Exception { roundTrip(SonicGame.SONIC_3K, false); }
    }

    private static void roundTrip(SonicGame game, boolean earnInSpecialStage) throws Exception {
        var shared = SharedLevel.load(game, 0, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            var input = new InputHandler();
            var loop = new GameLoop(input);
            loop.setGameMode(GameMode.LEVEL);
            var state = GameServices.gameState();
            state.addScore(1200);
            var level = GameServices.level();
            if (earnInSpecialStage) {
                earnSonic1Continue();
                while (state.getLives() > 1) state.loseLife();
                var sprite = fixture.sprite();
                assertTrue(sprite.applyHurtOrDeath(sprite.getCentreX(), DamageCause.SPIKE, false));
                for (int frame = 0; frame < 1200 && level.getGameOverExitRequested() == null; frame++) {
                    fixture.stepIdleFrames(1);
                }
                assertEquals(GameOverExit.CONTINUE_SCREEN, level.getGameOverExitRequested());
            } else {
                while (state.getLives() > 0) state.loseLife();
                state.addContinue();
                state.addContinue();
                level.requestGameOverExit(GameOverExit.CONTINUE_SCREEN);
            }
            for (int i = 0; i < 120 && loop.getCurrentGameMode() != GameMode.CONTINUE_SCREEN; i++) loop.step();
            assertEquals(GameMode.CONTINUE_SCREEN, loop.getCurrentGameMode());
            assertNotNull(loop.getContinueScreenProvider());
            for (int i = 0; i < 100; i++) loop.step();
            int start = GameServices.configuration().getInt(SonicConfiguration.START);
            input.handleKeyEvent(start, GLFW_PRESS);
            loop.step();
            input.handleKeyEvent(start, GLFW_RELEASE);
            assertTrue(loop.getContinueScreenProvider().isAccepted());
            for (int i = 0; i < 180 && loop.getContinueScreenProvider() != null; i++) loop.step();
            assertNull(loop.getContinueScreenProvider(), "departure and fade must reach reload");
            assertEquals(3, state.getLives());
            assertEquals(earnInSpecialStage ? 0 : 1, state.getContinues());
            assertEquals(0, state.getScore());
            assertEquals(0, level.getCurrentZone());
            assertTrue(loop.getCurrentGameMode() == GameMode.LEVEL || loop.getCurrentGameMode() == GameMode.TITLE_CARD);
            assertFalse(level.isLevelInactiveForTransition());
        } finally {
            shared.dispose();
        }
    }

    private static void earnSonic1Continue() throws Exception {
        var stage = new com.openggf.game.sonic1.specialstage.Sonic1SpecialStageManager();
        try {
            stage.initialize(0);
            stage.advanceToEntryPresentation();
            stage.update();
            var position = stage.captureComparisonState();
            int row = ((int) (position.sonicPosY() >> 16) + 0x50) / 24;
            int column = ((int) (position.sonicPosX() >> 16) + 0x20) / 24;
            var layout = stage.getClass().getDeclaredField("layout");
            layout.setAccessible(true);
            ((byte[]) layout.get(stage))[row * 128 + column] = 0x3A;
            stage.setRingsCollected(49);
            stage.update();
            assertEquals(50, stage.getRingsCollected());
        } finally {
            stage.reset();
        }
    }
}
