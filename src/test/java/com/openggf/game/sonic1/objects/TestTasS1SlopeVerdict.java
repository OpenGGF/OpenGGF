package com.openggf.game.sonic1.objects;

import com.openggf.tests.reel.S1ReelFrames;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native FBlock_Action skips all SolidObject work offscreen, including release.
 * Local trigger test for Tee-N-Tee's SBZ3 slope lock: real landing then walking
 * beyond the platform. Only the camera pose is controlled, never standing state. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1SlopeVerdict {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void offscreenPlatformSkipsSolidReleaseAndRetainsSlopeLockTrigger(boolean offscreen) throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,5,2);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)0x1a0,(short)0x1ed).startPositionIsCentre().build();
            var s=f.sprite();var objects=GameServices.level().getObjectManager();
            S1ReelFrames.idle(f,2);
            var block=objects.getRidingObject(s);
            assertInstanceOf(Sonic1FloatingBlockObjectInstance.class,block);
            assertEquals(0x1a0,block.getX());assertEquals(0x210,block.getY());
            assertTrue(s.isOnObject());assertFalse(s.getAir());
            if(offscreen)f.camera().setY((short)200);
            f.camera().setFrozen(true);
            AbstractObjectInstance.updateCameraBounds(f.camera().getX(),f.camera().getY(),f.camera().getX()+320,f.camera().getY()+224,0);
            assertEquals(!offscreen,block.isWithinSolidContactBounds(),"Reach the native obRender collision gate");
            s.setGSpeed((short)0x600);s.setXSpeed((short)0x600);
            int edge=block.getX()+64+11;
            for(int i=0;i<25&&s.getCentreX()<=edge;i++)S1ReelFrames.step(f,false,false,false,true,false);
            assertTrue(s.getCentreX()>edge,"Right input really walks beyond the native solid's standing range");
            assertFalse(block.isDestroyed(),"This tests offscreen collision dispatch, not deletion");
            // Native parity assertion (expected offscreen) was executed red first.
            // FBlock_Action's outer render gate skips SolidObject's release entirely.
            assertEquals(offscreen,s.isOnObject(),"Only the admitted SolidObject checkpoint releases the rider");
            if(offscreen)assertSame(block,objects.getRidingObject(s));
            else assertNull(objects.getRidingObject(s));
        } finally {level.dispose();}
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"visible","jump","delete"})
    void retainedOffscreenRideReplaysVisibilityJumpAndRetirement(String exit) throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,5,2);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level)
                .startPosition((short)0x1a0,(short)0x1ed).startPositionIsCentre().build();
            var p=f.sprite();var objects=GameServices.level().getObjectManager();
            S1ReelFrames.idle(f,2);
            var original=objects.getRidingObject(p);
            assertInstanceOf(Sonic1FloatingBlockObjectInstance.class,original);
            short visibleY=f.camera().getY();
            f.camera().setY((short)200);f.camera().setFrozen(true);
            AbstractObjectInstance.updateCameraBounds(f.camera().getX(),200,f.camera().getX()+320,424,0);
            S1ReelFrames.idle(f,2);
            assertTrue(original.isSkipSolidContactThisFrame(),"an actual completed display pass hides the block");
            p.setGSpeed((short)0x600);p.setXSpeed((short)0x600);
            for(int tick=0;tick<25&&p.getCentreX()<=original.getX()+75;tick++)
                S1ReelFrames.step(f,false,false,false,true,false);
            assertTrue(p.getCentreX()>original.getX()+75);assertSame(original,objects.getRidingObject(p));
            assertTrue(p.isOnObject());assertTrue(objects.hasObjectStandingBit(p,original));
            p.setGSpeed((short)0);p.setXSpeed((short)0);
            var registry=com.openggf.tests.TestEnvironment.activeGameplayMode().getRewindRegistry();
            var before=registry.capture();
            java.util.List<String> expected=null;
            for(int replay=0;replay<3;replay++){
                if(replay!=0)registry.restore(before);
                var block=objects.getRidingObject(p);
                assertInstanceOf(Sonic1FloatingBlockObjectInstance.class,block);
                assertTrue(objects.hasObjectStandingBit(p,block));
                if(replay!=0)assertNotSame(original,block,"world restore recreates the object owner");
                if(exit.equals("visible")){
                    f.camera().setY(visibleY);
                    AbstractObjectInstance.updateCameraBounds(f.camera().getX(),visibleY,
                        f.camera().getX()+320,visibleY+224,0);
                }else if(exit.equals("delete")){
                    ((AbstractObjectInstance)block).setDestroyed(true);
                }
                var rows=new java.util.ArrayList<String>();
                for(int tick=0;tick<3;tick++){
                    S1ReelFrames.step(f,false,false,false,false,exit.equals("jump")&&tick==0);
                    var rider=objects.getRidingObject(p);
                    rows.add(java.util.List.of((int)p.getCentreX(),p.getXSubpixelRaw(),(int)p.getCentreY(),
                        p.getYSubpixelRaw(),(int)p.getXSpeed(),(int)p.getYSpeed(),p.getAir(),p.isOnObject(),
                        rider==null?"none":rider.getClass().getSimpleName(),
                        objects.hasObjectStandingBit(p,block)).toString());
                }
                if(exit.equals("visible")){
                    assertFalse(p.isOnObject());assertNull(objects.getRidingObject(p));
                }else if(exit.equals("jump")){
                    // Sonic_Jump (_incObj/01 Sonic.asm:1233-1237) sets InAir and
                    // clears Push, but does NOT clear OnObj. The omitted FBlock
                    // checkpoint cannot run SolidObject's airborne unseat either.
                    assertTrue(p.getAir(),"fresh jump must reach the actual jump owner: "+rows);
                    assertTrue(p.getYSpeed()<0);assertTrue(p.isOnObject());
                    assertSame(block,objects.getRidingObject(p));
                }else{
                    assertFalse(objects.isActiveObjectInstance(block),"the actual placed object is retired");
                    // The existing contact model retains the former interaction
                    // identity (native Sonic's stood-on slot address), but it is
                    // absent from the live object walk and its SST bit is cleared.
                    assertSame(block,objects.getRidingObject(p));
                    assertFalse(objects.hasObjectStandingBit(p,block));
                    // DeleteObject clears the object's SST, not Sonic's status.
                    assertTrue(p.isOnObject());
                }
                if(expected==null)expected=rows;else assertEquals(expected,rows,"registered-world restore-forward "+exit);
            }
        } finally {level.dispose();}
    }

}
