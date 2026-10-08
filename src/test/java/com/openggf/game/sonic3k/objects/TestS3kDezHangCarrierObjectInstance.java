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

class TestS3kDezHangCarrierObjectInstance {
    @Test
    void captureRectangleIsHalfOpenAndPositiveObjectControlIsAdmitted() {
        int[][] cases={{-17,40,0},{-16,40,1},{15,63,1},{16,40,0},{0,39,0},{0,40,1},{0,63,1},{0,64,0}};
        for(int[] c:cases) {
            var p=player(0x100+c[0],0x400+c[1]);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
            var carrier=carrier(1,0,p,List.of(),new ArrayList<>());carrier.update(0,p);
            assertEquals(c[2]!=0,carrier.grabbedForTest(0),java.util.Arrays.toString(c));
        }
        for(int gate=0;gate<4;gate++) {
            var p=player(0x100,0x428);
            if(gate==0)ObjectControlState.nativeBit7FullControl().applyTo(p);
            if(gate==1)p.setHurt(true);
            if(gate==2)p.setDead(true);
            if(gate==3)p.setDebugMode(true);
            var carrier=carrier(1,0,p,List.of(),new ArrayList<>());carrier.update(0,p);
            assertFalse(carrier.grabbedForTest(0));
        }
    }

    @Test
    void captureClearsOnlyTheAirStatusBitWithoutSynthesizingTerrainLanding() {
        var p=player(0x100,0x428);p.setAir(true);p.setJumping(true);
        p.setRollingJump(true);p.setDoubleJumpFlag(2);p.applyCustomRadii(10,10);
        var carrier=carrier(0,0,p,List.of(),new ArrayList<>());carrier.update(0,p);
        assertFalse(p.getAir());assertTrue(p.isJumping());assertTrue(p.getRollingJump());
        assertEquals(2,p.getDoubleJumpFlag());assertEquals(10,p.getYRadius());
    }

    @Test
    void onlyPreviousMainGrabStartsRiseAndMotionIntegratesBeforeAcceleration() {
        var p=player(0x100,0x428);var p2=player(0x100,0x428);var extra=player(0x100,0x428);
        var carrier=carrier(1,0,null,List.of(p2,extra),new ArrayList<>());
        carrier.update(0,null);assertTrue(carrier.grabbedForTest(1));assertFalse(extra.isObjectControlled());
        carrier.update(1,null);assertEquals(0,carrier.routineForTest());
        carrier=carrier(1,0,p,List.of(),new ArrayList<>());
        carrier.update(0,p);assertEquals(0,carrier.routineForTest());
        carrier.update(1,p);assertEquals(1,carrier.routineForTest());assertEquals(0,carrier.yVelocityForTest());
        carrier.update(2,p);assertEquals(0x400,carrier.getY());assertEquals(-8,carrier.yVelocityForTest());
        carrier.update(3,p);assertEquals(0x3FF,carrier.getY());assertEquals(0xF800,carrier.yFractionForTest());
        assertEquals(-16,carrier.yVelocityForTest());
    }

    @Test
    void ceilingCorrectionKeepsFractionAndFiniteTravelHonoursStatusFlip() {
        for(int flip=0;flip<2;flip++) {
            var p=player(0x100,0x428);var carrier=carrier(1,flip,p,List.of(),new ArrayList<>());
            carrier.update(0,p);carrier.update(1,p);carrier.update(2,p);
            carrier.ceiling=-3;carrier.update(3,p);
            assertEquals(0x402,carrier.getY());assertEquals(0xF800,carrier.yFractionForTest());
            assertEquals(2,carrier.routineForTest());assertEquals(0,carrier.yVelocityForTest());
            assertEquals(0x100,carrier.getX(),"no horizontal movement on the ceiling pass");
            for(int i=1;i<=4;i++) {
                carrier.update(0,p);assertEquals(0x100+i*(flip==0?2:-2),carrier.getX());
                assertEquals(4-i,carrier.remainingTravelForTest());
            }
            carrier.update(0,p);assertEquals(0x100+(flip==0?8:-8),carrier.getX());
        }
    }

    @Test
    void anyHeldDirectionExtendsReleaseDelayAndRightWinsBothHeldDirections() {
        for(int direction=0;direction<5;direction++) {
            var p=player(0x100,0x428);var carrier=carrier(0,0,p,List.of(),new ArrayList<>());
            carrier.update(0,p);p.setSubpixelRaw(0x1234,0x5678);
            p.setLogicalInputState(direction==1,direction==2,direction==3||direction==4,direction==4,true,true);
            carrier.update(1,p);
            assertFalse(p.isObjectControlled());assertFalse(carrier.grabbedForTest(0));
            assertEquals(direction==0?18:60,carrier.cooldownForTest(0));
            assertEquals(direction==3?-0x200:direction==4?0x200:0,p.getXSpeed());
            assertEquals(-0x380,p.getYSpeed());assertEquals(0x428,p.getCentreY());
            assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x5678,p.getYSubpixelRaw());
            assertEquals(7,p.getXRadius());assertEquals(14,p.getYRadius());
            assertTrue(p.getAir());assertTrue(p.isJumping());assertTrue(p.getRolling());
            assertFalse(p.getRollingJump());assertEquals(0,p.getFlipAngle());assertEquals(2,p.getAnimationId());
        }
    }

    @Test
    void cooldownExpiryReturnsBeforeCaptureAndHeldJumpWithoutEdgeDoesNotRelease() {
        var p=player(0x100,0x428);var carrier=carrier(0,0,null,List.of(p),new ArrayList<>());
        carrier.update(0,null);
        p.setLogicalInputState(false,false,false,false,true,false);carrier.update(0,null);
        assertTrue(carrier.grabbedForTest(1));
        p.setLogicalInputState(false,false,false,false,true,true);carrier.update(0,null);
        assertEquals(18,carrier.cooldownForTest(1));
        p.setLogicalInputState(false,false,false,false,false,false);
        for(int i=0;i<18;i++){carrier.update(0,null);assertFalse(carrier.grabbedForTest(1));}
        assertEquals(0,carrier.cooldownForTest(1));carrier.update(0,null);assertTrue(carrier.grabbedForTest(1));
    }

    @Test
    void lostRenderFlagOrHurtReleasesWithoutLaunchingAndPinUsesLevelClock() {
        var p=player(0x100,0x428);var p2=player(0x100,0x428);var sounds=new ArrayList<Integer>();
        var carrier=carrier(0,0,p,List.of(p2),sounds);carrier.update(0,p);
        assertEquals(List.of(0x5B,0x5B),sounds);sounds.clear();
        p.setSubpixelRaw(0x1234,0x5678);carrier.update(1,p);
        assertEquals(List.of(0xD0,0xD0),sounds);assertEquals(0x5678,p.getYSubpixelRaw());
        p.setRenderFlagOnScreen(false);p2.setHurt(true);carrier.update(2,p);
        assertFalse(p.isObjectControlled());assertFalse(p2.isObjectControlled());
        assertEquals(60,carrier.cooldownForTest(0));assertEquals(60,carrier.cooldownForTest(1));
        assertEquals(0,p.getYSpeed());assertFalse(p.getAir());
    }

    private TestPlayableSprite player(int x,int y) {
        var p=new TestPlayableSprite();NativePositionOps.writeXPosResetSubpixel(p,x);
        NativePositionOps.writeYPosResetSubpixel(p,y);p.setRenderFlagOnScreen(true);return p;
    }
    private ProbeCarrier carrier(int subtype,int flags,TestPlayableSprite main,List<TestPlayableSprite> followers,List<Integer> sounds) {
        var level=mock(LevelManager.class);when(level.getFrameCounter()).thenReturn(16);
        var c=new ProbeCarrier(new ObjectSpawn(0x100,0x400,0x4C,subtype,flags,false,0));
        c.setServices(new TestObjectServices(){
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->main,()->followers);}
            @Override public void playSfx(int id){sounds.add(id);}
        }.withLevelManager(level));return c;
    }
    private static final class ProbeCarrier extends S3kDezHangCarrierObjectInstance {
        int ceiling=10;
        ProbeCarrier(ObjectSpawn spawn){super(spawn);}
        @Override protected int ceilingDistance(){return ceiling;}
    }
}
