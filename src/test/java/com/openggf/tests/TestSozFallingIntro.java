package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.SozFallingIntroInstance;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozFallingIntro {
    @Test void coldEntryFallsWaitsForPressAndReplaysEmergence() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,0)
                .withFreshLevelStartLifecycle().build();
        var events=(Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider();
        var main=fixture.sprite();
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o instanceof SozFallingIntroInstance intro && intro.getSlotIndex()==5));
        assertTrue(main.getAir());
        assertEquals(2,main.getAnimationId());
        var registry=fixture.gameplayMode().getRewindRegistry();
        boolean buried=false;
        for(int frame=0;frame<180;frame++) {
            fixture.stepFrame(false,false,false,false,false);
            var intro=GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(SozFallingIntroInstance.class::isInstance)
                    .map(SozFallingIntroInstance.class::cast).findFirst().orElseThrow();
            if(intro.routine()==1) { buried=true; break; }
        }
        assertTrue(buried);
        var before=registry.capture();
        fixture.stepFrame(false,false,false,false,true);
        for(int i=0;i<20;i++)fixture.stepFrame(false,false,false,false,false);
        var after=registry.capture();
        assertFalse(main.isControlLocked());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(SozFallingIntroInstance.class::isInstance));
        registry.restore(before);
        fixture.stepFrame(false,false,false,false,true);
        for(int i=0;i<20;i++)fixture.stepFrame(false,false,false,false,false);
        for(String key:after.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(
                key,after.get(key),registry.capture().get(key)).isEmpty(),key);
    }
    @Test void explicitSkipDoesNotCreateTheColdController() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,0)
                .withSkippedZoneIntro().startPosition((short)0x330,(short)0x5D4)
                .startPositionIsCentre().build();
        ((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).applyZonePlayerStateAfterTitleCard();
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(SozFallingIntroInstance.class::isInstance));
    }
}
