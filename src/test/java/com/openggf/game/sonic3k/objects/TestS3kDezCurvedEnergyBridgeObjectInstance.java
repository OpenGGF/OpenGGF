package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kDezCurvedEnergyBridgeObjectInstance {
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    @Test
    void halfOpenContactRectangleSelectsSecondaryTerrainWithoutObjectSolidContact() {
        int[][] cases={{-81,0,0},{-80,-48,1},{79,47,1},{80,0,0},{0,-49,0},{0,48,0}};
        for(int[] c:cases) {
            var p=player(0x100+c[0],0x180+c[1]);
            var b=bridge(0,p,List.of(),new AtomicInteger(),new ArrayList<>());b.update(99,p);
            assertEquals(c[2]==1?14:12,p.getTopSolidBit());assertEquals(c[2]==1?15:13,p.getLrbSolidBit());
            assertFalse(p.isOnObject());assertFalse(p.getAir());
        }
    }
    @Test
    void onlyNativePlayersAreSwitchedAndRoutineDoesNotGateTheirStatus() {
        var p=player(0x100,0x180);var p2=player(0x100,0x180);var third=player(0x100,0x180);
        p.setHurt(true);p.setDebugMode(true);p.setObjectControlled(true);
        var b=bridge(0,p,List.of(p2,third),new AtomicInteger(),new ArrayList<>());b.update(0,p);
        assertEquals(14,p.getTopSolidBit());assertEquals(14,p2.getTopSolidBit());assertEquals(12,third.getTopSolidBit());
    }
    @Test
    void leavingLitWindowRestoresPathWithoutAirButExpirySetsAirOnlyForItsAdmittedPlayer() {
        var p=player(0x100,0x180);var p2=player(0x100,0x180);var clock=new AtomicInteger();
        var b=bridge(0,p,List.of(p2),clock,new ArrayList<>());b.update(0,p);
        NativePositionOps.writeXPosResetSubpixel(p,0x150);clock.set(1);b.update(0,p);
        assertEquals(12,p.getTopSolidBit());assertFalse(p.getAir());assertFalse(b.admittedForTest(0));
        for(int frame=2;frame<64;frame++){clock.set(frame);b.update(0,p);}
        assertEquals(0,b.remainingForTest());assertTrue(b.drawPublishedForTest());
        assertTrue(p2.getAir());assertEquals(12,p2.getTopSolidBit());assertFalse(p.getAir());
        clock.set(64);b.update(0,p);assertFalse(b.drawPublishedForTest());
        NativePositionOps.writeXPosResetSubpixel(p,0x100);clock.set(128);b.update(0,p);
        assertEquals(63,b.remainingForTest());assertEquals(14,p.getTopSolidBit());
    }
    @Test
    void subtypeAndLevelClockChooseMidWindowEntryOrWaitForWrap() {
        int[][] cases={{0,0,63},{7,100,59},{0x31,0,71},{0xF8,0,0},{0xC,63,0},{0xC,1024,63}};
        for(int[] c:cases) {
            var b=bridge(c[0],null,List.of(),new AtomicInteger(c[1]),new ArrayList<>());b.update(0xFFFF,null);
            assertEquals(c[2],b.remainingForTest(),java.util.Arrays.toString(c));
            assertEquals(c[2]>0 || (c[0]==0xC&&c[1]==63),b.drawPublishedForTest());
        }
    }
    @Test
    void widePlacementWindowDoesNotLoadThenImmediatelyRetireVisibleFamilies() {
        var spawn=new ObjectSpawn(0x400,0x180,0,0,0,false,0);
        var objects=List.of(new S3kDezCurvedEnergyBridgeObjectInstance(spawn),
                new S3kDezEnergyBridgeObjectInstance(spawn),new S3kDezConveyorBeltObjectInstance(spawn),
                new S3kDezLightningObjectInstance(spawn),new S3kDezTorpedoLauncherObjectInstance(spawn),
                new S3kDezHoverMachineObjectInstance(spawn),new S3kDezHoverRotorObjectInstance(spawn),
                new S3kDezStaircaseObjectInstance(spawn),new S3kDezHangCarrierObjectInstance(spawn));
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0x800);
        for(var o:objects)assertTrue(o.isCustomOutOfRange(0),o.getClass().getSimpleName());
        AbstractObjectInstance.updateCameraBounds(0,0,800,224,0x800);
        // Aligned distance $480 is just outside $460 even at 800; moving
        // camera one coarse chunk admits it. Native $280 still rejects it.
        for(var o:objects) {
            assertTrue(o.isCustomOutOfRange(0),o.getClass().getSimpleName());
            assertFalse(o.isCustomOutOfRange(0x80),o.getClass().getSimpleName());
        }
    }
    @Test
    void soundReadsPreviousRenderedFlagAndTheLevelClock() {
        var clock=new AtomicInteger();var sounds=new ArrayList<Integer>();
        var b=bridge(0,null,List.of(),clock,sounds);
        AbstractObjectInstance.updateCameraBounds(0x80,0x100,0x1C0,0x1E0,0x800);
        b.update(8,null);assertTrue(sounds.isEmpty());b.refreshPostCameraRenderState();
        AbstractObjectInstance.updateCameraBounds(0x1000,0x100,0x1140,0x1E0,0x800);
        clock.set(8);b.update(0,null);assertEquals(List.of(0xA8),sounds);b.refreshPostCameraRenderState();
        clock.set(16);b.update(0,null);assertEquals(1,sounds.size());
    }
    private TestPlayableSprite player(int x,int y) {
        var p=new TestPlayableSprite();NativePositionOps.writeXPosResetSubpixel(p,x);NativePositionOps.writeYPosResetSubpixel(p,y);
        p.setTopSolidBit((byte)12);p.setLrbSolidBit((byte)13);return p;
    }
    private S3kDezCurvedEnergyBridgeObjectInstance bridge(int subtype,TestPlayableSprite main,List<TestPlayableSprite> players,AtomicInteger clock,List<Integer> sounds) {
        var level=mock(LevelManager.class);when(level.getFrameCounter()).thenAnswer(x->clock.get());
        var b=new S3kDezCurvedEnergyBridgeObjectInstance(new ObjectSpawn(0x100,0x180,0x56,subtype,0,false,0));
        b.setServices(new TestObjectServices(){
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->main,()->players);}
            @Override public void playSfx(int id){sounds.add(id);}
        }.withLevelManager(level));return b;
    }
}
