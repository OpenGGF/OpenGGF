package com.openggf.game.soniccd.flow;

import com.openggf.game.GameMode;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.special.CdSpecialStageProvider;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class TestCdAttractSpecialStages {
    @ParameterizedTest
    @CsvSource({"2,0,0,160", "4,5,4,60"})
    void titleTimeoutRunsTheDiscDemoAndReturnsToTheTitle(
            int precedingDemos, int stageIndex, int ufosLeft, int rings) throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            for (int i = 0; i < precedingDemos; i++) h.scenes.flow().titleExited(CdProgramFlow.EXIT_DEMO);
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE, 6000, 0);
            var provider = assertInstanceOf(CdSpecialStageProvider.class, h.loop.getActiveSpecialStageProvider());
            assertEquals(stageIndex, provider.getCurrentStage());
            assertTrue(provider.session().stage().flags().demoInput());

            h.until(provider::isFinished, 3000, 0);
            var result = provider.result();
            assertEquals(ufosLeft, result.ufosLeft());
            assertEquals(rings, result.rings());
            assertFalse(result.showsResults());
            h.until(() -> {
                GameMode mode = h.loop.getCurrentGameMode();
                assertTrue(mode == GameMode.SPECIAL_STAGE || mode == GameMode.OPTIONS
                        || mode == GameMode.TITLE_SCREEN, "unexpected return mode " + mode);
                return mode == GameMode.TITLE_SCREEN;
            }, 120, 0);
            assertTrue(h.scenes.route().stream().anyMatch(step -> step.startsWith("special stage ended ")));
            assertEquals(0, h.scenes.flow().timeStones());
        }
    }
}
