package com.openggf.game.sonic3k;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kAniPlcVBlankLifecycle {
    @Test void lagRetainsSubmissionAndLegalHandlersDrainExactlyOncePerClaimedToken() {
        for (var legal : new PlcLifecyclePhase[]{PlcLifecyclePhase.PALETTE_FADE,
                PlcLifecyclePhase.LEVEL_TITLE_CARD, PlcLifecyclePhase.ORDINARY_LEVEL}) {
            HeadlessTestFixture.builder().withZoneAndAct(4, 0).build();
            var manager = (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
            GameServices.level().consumePendingInitialProcessSpritesPass();
            byte[] first = pattern();
            for (int i = 0; i < 8; i++) manager.update();
            var script = manager.patternAnimatorForTesting().scriptsForTesting().get(3);
            assertEquals(2, script.getFrameIndex());
            var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null);
            var context = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
            var lag = coordinator.latchBeforeFadeUpdate(); lag.claim(PlcLifecyclePhase.LAG);
            LevelFrameStep.dispatchGameVBlank(context, lag);
            assertArrayEquals(first, pattern(), "VInt0 cannot publish queued art"); lag.finish();
            var frame = coordinator.latchBeforeFadeUpdate(); frame.claim(legal);
            LevelFrameStep.dispatchGameVBlank(context, frame);
            byte[] second = pattern(); assertFalse(Arrays.equals(first, second), legal.toString());
            assertEquals(2, script.getFrameIndex(), "publication does not tick counters");
            for (int i = 0; i < 8; i++) manager.update();
            LevelFrameStep.dispatchGameVBlank(context, frame);
            assertArrayEquals(second, pattern(), "same token cannot drain newly submitted work twice");
            frame.finish();
            var next = coordinator.latchBeforeFadeUpdate(); next.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            LevelFrameStep.dispatchGameVBlank(context, next); assertArrayEquals(first, pattern()); next.finish();
        }
    }

    @Test void explicitExistingDmaDeclarationIsDistinctFromAnOrdinaryLagClassification() {
        HeadlessTestFixture.builder().withZoneAndAct(4, 0).build();
        var manager = (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
        GameServices.level().consumePendingInitialProcessSpritesPass(); byte[] first = pattern();
        for (int i = 0; i < 8; i++) manager.update();
        var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null);
        coordinator.markNextVblankServicesDmaQueue();
        var frame = coordinator.latchBeforeFadeUpdate(); frame.claim(PlcLifecyclePhase.LAG);
        assertTrue(frame.hasExplicitDmaQueueService());
        LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(SessionManager.getCurrentGameplayMode()), frame);
        assertFalse(Arrays.equals(first, pattern())); frame.finish();
    }

    private static byte[] pattern() {
        byte[] bytes = new byte[64]; GameServices.level().getCurrentLevel().getPattern(0x200).copyInto(bytes, 0); return bytes;
    }
}
