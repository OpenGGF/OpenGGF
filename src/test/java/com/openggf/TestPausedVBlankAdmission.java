package com.openggf;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/** Clock admission remains separate from gameplay execution and PLC preparation. */
class TestPausedVBlankAdmission {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void pauseServicesOneTickEvenWhenTheSameTokenIsVisitedTwice() {
        for (var phase : new PlcLifecyclePhase[]{PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.SPECIAL_STAGE_PAUSE}) {
            var context = context();
            var objects = context.levelManager().getObjectManager();
            var frame = new PlcFrameLifecycleCoordinator(context.gameModule()).latchBeforeFadeUpdate();
            LevelFrameStep.serviceVBlankOnly(context, frame, phase);
            LevelFrameStep.serviceVBlankOnly(context, frame, phase);
            frame.finish();
            verify(objects, times(1)).advanceVblaCounter();
            verify(objects, never()).update(anyInt(), any(), anyList(), anyInt(), anyBoolean());
        }
    }

    @Test void lagAndAnAlreadyOwnedFadeRetainTheirExistingClockOwners() {
        var context = context();
        var objects = context.levelManager().getObjectManager();
        var lifecycle = new PlcFrameLifecycleCoordinator(context.gameModule());
        var lag = lifecycle.latchBeforeFadeUpdate();
        LevelFrameStep.serviceVBlankOnly(context, lag, PlcLifecyclePhase.LAG);
        lag.finish();
        var fade = lifecycle.latchBeforeFadeUpdate();
        fade.claim(PlcLifecyclePhase.PALETTE_FADE);
        LevelFrameStep.dispatchGameVBlank(context, fade);
        LevelFrameStep.serviceVBlankOnly(context, fade, PlcLifecyclePhase.NORMAL_PAUSE);
        fade.finish();
        verify(objects, never()).advanceVblaCounter();
    }

    private static LevelFrameContext context() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var original = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
        var level = mock(LevelManager.class);
        when(level.getObjectManager()).thenReturn(mock(ObjectManager.class));
        return new LevelFrameContext(original.gameModule(), original.gameRules(), original.levelEventProvider(),
                original.bonusStageProvider(), original.spriteManager(), GameServices.gameState(), original.timerManager(),
                original.paletteOwnershipRegistry(), original.kosinskiModuleQueue(), original.hardwareTiming(),
                original.hardwareTimingBoundaryObserver(), original.runtimeArtCoordinator(), level);
    }
}
