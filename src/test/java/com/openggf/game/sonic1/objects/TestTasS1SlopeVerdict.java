package com.openggf.game.sonic1.objects;

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
    void currentLimitationOffscreenPlatformReleasesStandingInsteadOfKeepingSlopeLockTrigger(boolean offscreen) throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,5,2);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)0x1a0,(short)0x1ed).startPositionIsCentre().build();
            var s=f.sprite();var objects=GameServices.level().getObjectManager();
            f.stepIdleFrames(2);
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
            for(int i=0;i<25&&s.getCentreX()<=edge;i++)f.stepFrame(false,false,false,true,false);
            assertTrue(s.getCentreX()>edge,"Right input really walks beyond the native solid's standing range");
            assertFalse(block.isDestroyed(),"This tests offscreen collision dispatch, not deletion");
            // Native parity assertion (expected offscreen) was executed red first.
            // FBlock_Action's outer render gate skips SolidObject's release entirely.
            assertFalse(s.isOnObject(),"Confirmed limitation: the engine releases even the offscreen rider");
            assertNull(objects.getRidingObject(s));
            if(offscreen)assertNotEquals(true,s.isOnObject(),"Native retained-standing result is not implemented");
        } finally {level.dispose();}
    }
}
