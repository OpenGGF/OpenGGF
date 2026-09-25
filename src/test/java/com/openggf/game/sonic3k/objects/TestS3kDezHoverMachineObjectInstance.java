package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kDezHoverMachineObjectInstance {
    @Test
    void semicircularWindowUsesHorizontalOffsetAndUnsignedHalfOpenBounds() {
        // Rotor first X=$100, Y=$180. These are GetSineCosine samples at
        // horizontal offsets $00/$20/$40, independently of its orbit phase.
        int[][] cases = {
                {-65,0,0,0}, {-64,-33,0,0}, {-64,-32,0,1}, {-64,0,-2,1},
                {-32,-78,0,0}, {-32,-77,0,1}, {-32,0,-5,1},
                {0,-97,0,0}, {0,-96,0,1}, {0,-80,-1,1}, {0,-1,-6,1},
                {0,0,-6,1}, {0,31,-2,1}, {0,32,0,0}, {64,0,0,0}
        };
        for (int[] c : cases) {
            var p = player(0x100+c[0],0x180+c[1]);
            rotor(p,List.of(),new ArrayList<>(),1).update(999,p);
            assertEquals(0x180+c[1]+c[2], p.getCentreY(), java.util.Arrays.toString(c));
            assertEquals(c[3] != 0,p.getAir(),java.util.Arrays.toString(c));
        }
    }

    @Test
    void liftPreservesFractionsAndHorizontalSpeedAndOnlyInitializesAnAbsentFlip() {
        var p=player(0x100,0x180);
        p.setSubpixelRaw(0x1234,0xABCD);
        p.setXSpeed((short)0x123);p.setYSpeed((short)0x456);p.setGSpeed((short)0x789);
        p.setRollingJump(true);p.setJumping(true);p.setDoubleJumpFlag(2);
        var rotor=rotor(p,List.of(),new ArrayList<>(),1);
        rotor.update(0,p);
        assertEquals(0x17A,p.getCentreY());assertEquals(0xABCD,p.getYSubpixelRaw());
        assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x123,p.getXSpeed());
        assertEquals(0,p.getYSpeed());assertEquals(1,p.getGSpeed());
        assertFalse(p.getRollingJump());assertFalse(p.isJumping());assertEquals(0,p.getDoubleJumpFlag());
        assertEquals(1,p.getFlipAngle());assertEquals(0x7F,p.getFlipsRemaining());assertEquals(8,p.getFlipSpeed());
        p.setFlipAngle(0x60);p.setFlipsRemaining(3);p.setFlipSpeed(2);
        rotor.update(0,p);
        assertEquals(0x60,p.getFlipAngle());assertEquals(3,p.getFlipsRemaining());assertEquals(2,p.getFlipSpeed());
    }

    @Test
    void nativePlayerSlotsAreIndependentAndSoundUsesLevelClockNotVint() {
        var p=player(0x100,0x180);var p2=player(0x100,0x180);var extra=player(0x100,0x180);
        var sounds=new ArrayList<Integer>();
        var rotor=rotor(p,List.of(p2,extra),sounds,16);
        rotor.update(1,p);
        assertEquals(0x17A,p.getCentreY());assertEquals(0x17A,p2.getCentreY());assertEquals(0x180,extra.getCentreY());
        assertEquals(List.of(0xD9,0xD9),sounds);
        sounds.clear();rotor(p,List.of(),sounds,17).update(16,p);assertTrue(sounds.isEmpty());
        for(int gate=0;gate<3;gate++) {
            p=player(0x100,0x180);p2=player(0x100,0x180);
            if(gate==0)p.setHurt(true);
            if(gate==1)p.setDead(true);
            if(gate==2)ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
            rotor(p,List.of(p2),new ArrayList<>(),1).update(0,p);
            assertEquals(0x180,p.getCentreY());assertEquals(0x17A,p2.getCentreY());
        }
    }

    @Test
    void oldAngleControlsOrbitAndFrontBackPriorityWithOriginalAnchorRetirement() {
        var rotor=rotor(null,List.of(),new ArrayList<>(),1);
        for(int tick=0;tick<129;tick++) {
            rotor.update(tick,null);
            assertEquals(((tick*2)&0x80)==0?4:6,rotor.getPriorityBucket(),"tick="+tick);
            if(tick==0||tick==128)assertEquals(0x100,rotor.getX());
            if(tick==32||tick==96)assertEquals(0xE0,rotor.getX());
            if(tick==64)assertEquals(0xC0,rotor.getX());
        }
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(0,0,320,224,0x800);
        var edge=new S3kDezHoverRotorObjectInstance(new ObjectSpawn(0x270,0,0x5E,0,0,false,0));
        assertEquals(0x290,edge.getX());assertFalse(edge.isCustomOutOfRange(0));assertTrue(edge.isCustomOutOfRange(-0x80));
    }

    private TestPlayableSprite player(int x,int y) {
        var p=new TestPlayableSprite();NativePositionOps.writeXPosResetSubpixel(p,x);
        NativePositionOps.writeYPosResetSubpixel(p,y);p.setAir(false);return p;
    }
    private S3kDezHoverRotorObjectInstance rotor(TestPlayableSprite main,List<TestPlayableSprite> followers,List<Integer> sounds,int clock) {
        var level=mock(LevelManager.class);when(level.getFrameCounter()).thenReturn(clock);
        var rotor=new S3kDezHoverRotorObjectInstance(new ObjectSpawn(0xE0,0x180,0x5E,0,0,false,0));
        rotor.setServices(new TestObjectServices() {
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->main,()->followers);}
            @Override public void playSfx(int id){sounds.add(id);}
        }.withLevelManager(level));
        return rotor;
    }
}
