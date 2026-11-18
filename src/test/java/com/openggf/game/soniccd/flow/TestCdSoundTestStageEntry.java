package com.openggf.game.soniccd.flow;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.special.CdSpecialStageProvider;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.game.soniccd.title.secrets.CdSecretsScreen;
import com.openggf.graphics.FadeManager.FadeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

@EnabledOnOs(OS.LINUX)
class TestCdSoundTestStageEntry {
    @Test
    void soundTestLaunchesTheSecretStageAndStartReturnsToTitle() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
            h.until(() -> title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
            int[][] code = {{CdFlowHarness.DOWN, 0}, {CdFlowHarness.DOWN, 0}, {CdFlowHarness.DOWN, 0},
                    {CdFlowHarness.LEFT, 0}, {CdFlowHarness.RIGHT, 0}, {0, CdFlowHarness.A}};
            for (int[] press : code) {
                for (int i = 0; i < 4; i++) h.step(press[0], press[1], false);
                h.idle(12);
            }

            h.until(() -> h.loop.getCurrentGameMode() == GameMode.OPTIONS, 300, 0);
            var screen = assertInstanceOf(CdSecretsScreen.class, h.scenes.lastProgramScreen());
            var soundTest = screen.soundTest();
            for (int column = 0; column < 3; column++) {
                int index = column;
                if (column != 0) h.until(() -> soundTest.soundType() == index, 30, CdFlowHarness.RIGHT);
                h.until(() -> soundTest.selection()[index] == 7, 120, CdFlowHarness.DOWN);
            }
            h.idle(1);
            h.step(0, 0, true);

            int stones = h.scenes.flow().timeStones();
            int nextStage = h.scenes.flow().currentSpecialStage();
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE, 300, 0);
            var provider = assertInstanceOf(CdSpecialStageProvider.class, h.loop.getActiveSpecialStageProvider());
            assertEquals(7, provider.getCurrentStage());
            assertTrue(provider.session().stage().flags().secret());
            h.until(() -> !provider.session().fadingIn(), 120, 0);

            var input = h.loop.getInputHandler();
            int enter = GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY);
            input.handleKeyEvent(enter, GLFW_PRESS);
            for (int i = 0; !provider.isFinished() && i < 10; i++) h.step(0, 0, true);
            input.handleKeyEvent(enter, GLFW_RELEASE);
            assertFalse(h.loop.isUserPaused());
            assertTrue(provider.result().manualExit());
            assertEquals(FadeState.FADING_TO_WHITE, GameServices.fade().getState());
            h.until(() -> {
                GameMode mode = h.loop.getCurrentGameMode();
                assertTrue(mode == GameMode.SPECIAL_STAGE || mode == GameMode.OPTIONS
                        || mode == GameMode.TITLE_SCREEN, "unexpected return mode " + mode);
                return mode == GameMode.TITLE_SCREEN;
            }, 120, 0);
            assertTrue(h.scenes.route().stream().anyMatch(step -> step.startsWith("special stage ended ")));
            assertEquals(stones, h.scenes.flow().timeStones());
            assertEquals(nextStage, h.scenes.flow().currentSpecialStage());
        }
    }
}
