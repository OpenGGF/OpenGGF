package com.openggf.sprites.playable;

import com.openggf.tests.*;

import com.openggf.configuration.*;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozRecoverySupportProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    @Test void recoveryHandoffDiscardsGroundingCacheButPreservesNativeInteract() {
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(8,0).build();
        var leader=f.sprite();var tails=GameServices.sprites().getSidekicks().getFirst();
        var manager=GameServices.level().getObjectManager();
        var rock=new SozPushableRockObjectInstance(new ObjectSpawn(0x400,0x400,0x3E,0,0,false,0));
        manager.addDynamicObject(rock);manager.forceRidingObjectForBootstrap(tails,rock);
        int nativeInteract=tails.getInteractSlotIndex();
        assertTrue(manager.hasObjectStandingBit(tails,rock));
        tails.setOnObject(false);tails.setAir(true);
        assertTrue(manager.hasGroundingObjectSupport(tails),"reproduce retained engine cache during recovery");
        short[] x=new short[64],y=new short[64],input=new short[64];byte[] status=new byte[64];
        java.util.Arrays.fill(x,(short)0x700);java.util.Arrays.fill(y,(short)0x100);
        leader.hydrateRecordedHistory(x,y,input,status,16);leader.setCentreX((short)0x700);leader.setCentreY((short)0x100);
        tails.setCentreX((short)0x700);tails.setCentreY((short)0x100);
        var cpu=tails.getCpuController();cpu.forceStateForTest(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY,0);
        var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
        cpu.update(1);
        assertEquals(SidekickCpuController.State.NORMAL,cpu.getState());
        assertFalse(manager.hasGroundingObjectSupport(tails));assertNull(manager.getRidingObject(tails));
        assertEquals(nativeInteract,tails.getInteractSlotIndex(),"native interact byte is deliberately stale");
        assertTrue(manager.hasObjectStandingBit(tails,rock),"only the object owner clears its native standing bit");
        f.stepFrame(false,false,false,false,false);
        assertTrue(tails.getAir());assertEquals(0x38,tails.getYSpeed(),"handoff must reach ordinary air gravity");
        var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);cpu=tails.getCpuController();cpu.update(1);
        f.stepFrame(false,false,false,false,false);
        var replay=registry.capture();assertEquals(after.entries().keySet(),replay.entries().keySet());
        for(var key:after.entries().keySet())assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)).isEmpty(),key);
    }
}
