package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kStageRingAnimation {
    @Test void byteTimerExpiresAtZeroAndWrapsIndependentlyOfLevelCounter() {
        var state = new Sonic3kGlobalAnimationState();
        state.advanceChangeRingFrame();
        assertEquals(7,state.ringTimer()); assertEquals(1,state.ringFrame());
        for(int i=0;i<7;i++) state.advanceChangeRingFrame();
        assertEquals(0,state.ringTimer()); assertEquals(1,state.ringFrame());
        state.advanceChangeRingFrame(); assertEquals(2,state.ringFrame());
        for(int i=0;i<16;i++) state.advanceChangeRingFrame();
        assertEquals(0,state.ringFrame());
        int vine=state.aizVineAngleWord(); state.resetFreshLevelRingAnimation();
        assertEquals(0,state.ringTimer()); assertEquals(0,state.ringFrame());
        assertEquals(vine,state.aizVineAngleWord(),"loc60DE excludes the vine word");
    }

    @Test void actualAnimatorSnapshotRestoresCursorAndForwardReplayTwice() {
        HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        var manager=animator();
        GameServices.level().consumePendingInitialProcessSpritesPass();
        assertEquals(0,manager.stageRingAnimationFrame(),"setup Animate_Tiles is not ChangeRingFrame");
        for(int i=0;i<11;i++) manager.update();
        var saved=manager.capture();
        for(int cycle=0;cycle<2;cycle++) {
            manager.restore(saved); assertEquals(2,manager.stageRingAnimationFrame());
            GameServices.level().setFrameCounter(0);
            for(int i=0;i<5;i++) manager.update();
            assertEquals(2,manager.stageRingAnimationFrame());
            manager.update(); assertEquals(3,manager.stageRingAnimationFrame());
        }
    }

    @Test void realSeamlessAnimatorReplacementRetainsRingCursor() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(4,0)
                .startPosition((short)0x2EE1,(short)0x0540).startPositionIsCentre().build();
        var old=animator();
        for(int i=0;i<11;i++) old.update();
        var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents();
        events.setEventsFg5(true);
        events.updateAct1BackgroundEvent(fixture.sprite().getCentreX(),fixture.sprite().getCentreY(),false);
        var next=animator(); assertNotSame(old,next);
        assertEquals(2,next.stageRingAnimationFrame());
        for(int i=0;i<5;i++) next.advanceForSeamlessTransition();
        assertEquals(2,next.stageRingAnimationFrame());
        next.advanceForSeamlessTransition(); assertEquals(3,next.stageRingAnimationFrame());
    }

    @Test void repeatedFreshProductionLoadsClearOnlyTheRingClockBeforeSetup() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        for(int cycle=0;cycle<2;cycle++) {
            var prior=animator();
            for(int i=0;i<11;i++) prior.update();
            int vine=prior.aizVineAngleWord();
            GameServices.level().loadZoneAndAct(4,0);
            var fresh=animator(); assertNotSame(prior,fresh);
            assertEquals(0,fresh.stageRingAnimationFrame());
            assertEquals(vine,fresh.aizVineAngleWord());
            GameServices.level().consumePendingInitialProcessSpritesPass();
            assertEquals(0,fresh.stageRingAnimationFrame());
            fresh.update(); assertEquals(1,fresh.stageRingAnimationFrame());
        }
    }

    private static Sonic3kLevelAnimationManager animator() {
        return (Sonic3kLevelAnimationManager)GameServices.level().getAnimatedPatternManager();
    }
}
