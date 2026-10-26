package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.objects.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestTasS1ObjectVerdicts {
    static SharedLevel level;
    @BeforeAll static void load() throws Exception {level=SharedLevel.load(SonicGame.SONIC_1,0,0);}
    @AfterAll static void close(){if(level!=null)level.dispose();}
    @ParameterizedTest @CsvSource({"3,false","3,true","5,false","5,true"})
    void fallingOntoMonitorGrantsItsPowerOnlyAfterBreakingAndIconRise(int subtype,boolean miss) {
        var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)(miss?480:384),(short)880).startPositionIsCentre().build();
        var s=f.sprite();
        var m=new Sonic1MonitorObjectInstance(new ObjectSpawn(384,944,0x26,subtype,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(m);
        s.setAir(true);s.setRolling(true);s.setAnimationId(2);s.setYSpeed((short)0x200);
        assertFalse(s.hasSpeedShoes());assertFalse((s.getInvincibleFrames()>0));
        int firstGrant=-1;
        for(int n=0;n<80;n++){
            f.stepFrame(false,false,false,false,false);
            if(firstGrant<0 && (s.hasSpeedShoes()||(s.getInvincibleFrames()>0)))firstGrant=n;
        }
        assertEquals(!miss,firstGrant>=0,"Only actual monitor contact may grant the contents");
        if(!miss){
            assertTrue(firstGrant>=32,"Grant waits for the native icon apex");
            if(subtype==3){assertTrue(s.hasSpeedShoes());assertEquals(0xc00,s.getMax());assertEquals(0x18,s.getRunAccel());}
            else { assertTrue((s.getInvincibleFrames()>0));int rings=s.getRingCount();assertFalse(s.applyHurtOrDeath(300,false,rings>0));assertFalse(s.isHurt());assertEquals(rings,s.getRingCount()); }
        } else {assertEquals(0x600,s.getMax());}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void ceilingContactSkipsTheRemainingApexDragFrames(boolean ceiling) {
        var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)384,(short)850).startPositionIsCentre().build();
        var s=f.sprite();
        if(ceiling)GameServices.level().getObjectManager().addDynamicObject(
            new Sonic1FloatingBlockObjectInstance(new ObjectSpawn(400,814,0x56,0,0,false,0),2));
        s.setAir(true);s.setXSpeed((short)0x800);s.setYSpeed((short)-0x200);
        for(int n=0;n<5;n++) {
            f.stepFrame(false,false,false,false,false);
            if(n==1){assertEquals(1922,s.getXSpeed());assertEquals(-400,s.getYSpeed());}
            if(n==2){assertEquals(1862,s.getXSpeed());assertEquals(ceiling?0:-344,s.getYSpeed());}
        }
        assertTrue(s.getAir());
        assertEquals(ceiling?1862:1748,s.getXSpeed(),"A real head contact avoids the last two 1/32 apex reductions");
        assertEquals(ceiling?112:-232,s.getYSpeed());
        assertEquals(ceiling?421:420,s.getCentreX());
    }
    @ParameterizedTest @CsvSource({"49,0,false","50,0,true","51,0,true","50,6,false"})
    void giantRingRequiresFiftyRingsAndDisappearsAfterSixEmeralds(int rings,int emeralds,boolean admits) {
        var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)384,(short)700).startPositionIsCentre().build();
        var s=f.sprite();s.setRingCount(rings);s.setAir(true);
        for(int i=0;i<emeralds;i++)GameServices.gameState().markEmeraldCollected(i);
        var ring=new Sonic1GiantRingObjectInstance(new ObjectSpawn(384,700,0x4b,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(ring);
        for(int i=0;i<25;i++)f.stepFrame(false,false,false,false,false);
        assertEquals(rings,s.getRingCount(),"The entry threshold is not changed by unrelated ring pickups");
        assertEquals(admits,s.isHidden(),"Real touch and ring-flash animation own entry hiding");
        if(emeralds==6)assertTrue(ring.isDestroyed());
    }
}
