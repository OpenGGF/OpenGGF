package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider runs a stage from a program-flow entry to its exit contract. The values are the
 * native stage-select Special Stage 1 run's (README of {@code ss1-demo}): all six UFOs broken,
 * 160 rings, timer stopped at 81, a temporary-mode stage that skips the results screen and
 * names stage 2 next ({@code .GetNextStage}, Sub.asm:341-356).
 */
class TestCdSpecialStageProvider {
    @Test
    void theStageSelectDemoEndsWithTheNativeOutcome() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            CdSpecialStageProvider provider = new CdSpecialStageProvider(() -> disc);
            provider.begin(CdSpecialStageEntry.demo(0));
            int frames = 0;
            while (!provider.isFinished() && frames < 10_000) {
                provider.handleInput(0, 0);
                provider.update();
                frames++;
            }
            assertTrue(provider.isFinished(), "finished after " + frames + " frames");
            CdSpecialStageResult result = provider.result();
            assertEquals(0, result.ufosLeft());
            assertEquals(160, result.rings());
            assertEquals(81, result.timer());
            assertFalse(result.timeStoneWon());
            assertFalse(result.manualExit());
            assertFalse(result.showsResults());
            assertEquals(1, result.nextStageId());
            // Native: update 1 at frame 2245, the last update at 3814, the stage leaves at the end of that pass.
            assertEquals(CdSpecialStageSession.FADE_IN_FRAMES + (3814 - 2245) + CdSpecialStageSession.FRAMES_PER_UPDATE,
                    frames, "frames from the first update to the end of the last pass, plus the fade");
        }
    }
}
