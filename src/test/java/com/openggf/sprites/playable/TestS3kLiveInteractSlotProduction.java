package com.openggf.sprites.playable;

import com.openggf.configuration.*;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLiveInteractSlotProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    @ParameterizedTest @ValueSource(ints={4,2,0,-1})
    void nativeWordComparisonUsesCurrentOccupantAfterSlotReuse(int replacementWord) {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(8,0).build();
        // Initialize the ordinary player-owned shield graph before graph snapshots.
        f.stepFrame(false,false,false,false,false);
        var tails=GameServices.sprites().getSidekicks().getFirst();
        var manager=GameServices.level().getObjectManager();
        var original=new SozPushableRockObjectInstance(new ObjectSpawn(0x400,0x400,0x3E,0,0,false,0));
        manager.addDynamicObject(original);
        // Native interact survives after solid cleanup; a non-solid controller
        // (such as SOZ quicksand) can subsequently set Status_OnObj again.
        tails.setLatchedSolidObject(0x3E,original);tails.setOnObject(true);
        int slot=tails.getInteractSlotIndex();var cpu=tails.getCpuController();
        cpu.setInitialState(SidekickCpuController.State.NORMAL);
        tails.setRenderFlagOnScreen(true);cpu.update(1);
        assertEquals(4,cpu.getDiagnosticInteractId());
        manager.removeDynamicObject(original);
        if(replacementWord==4)manager.addDynamicObjectAtSlot(new SozPushableRockObjectInstance(new ObjectSpawn(0x500,0x400,0x3E,0,0,false,0)),slot);
        if(replacementWord==2)manager.addDynamicObjectAtSlot(new Sonic3kSpringObjectInstance(new ObjectSpawn(0x500,0x400,0x07,0,0,false,0)),slot);
        tails.setOnObject(true);tails.setAir(true);
        if(replacementWord==-1) {
            tails.setRenderFlagOnScreen(true);cpu.update(2);
            assertEquals(0,cpu.getDiagnosticInteractId(),"onscreen refresh observes empty SST word zero");
        }
        tails.setRenderFlagOnScreen(false);
        assertTrue(tails.isLatchedSolidObjectReleased());
        var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
        cpu.update(2);
        assertEquals((replacementWord==4||replacementWord==-1)?SidekickCpuController.State.NORMAL:SidekickCpuController.State.CATCH_UP_FLIGHT,cpu.getState());
        var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);tails.getCpuController().update(2);var replay=registry.capture();
        assertEquals(after.entries().keySet(),replay.entries().keySet());
        for(var key:after.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key));
            assertTrue(diff.isEmpty(),key+": "+diff);
        }
    }
}
