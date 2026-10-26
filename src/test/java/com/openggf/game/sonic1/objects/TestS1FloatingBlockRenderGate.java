package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** FBlock_Main/BuildSprites display extents and retained render-bit ownership. */
class TestS1FloatingBlockRenderGate {
    @AfterEach void resetBounds(){AbstractObjectInstance.resetCameraBoundsForTests();}

    @ParameterizedTest
    @CsvSource({"0,16","112,64"})
    void skippedCheckpointUsesCompletedDisplayAndNativeExtents(int subtype,int halfWidth) {
        var block=new Sonic1FloatingBlockObjectInstance(
            new ObjectSpawn(0x600,0x400,0x56,subtype,0,false,0),Sonic1Constants.ZONE_SYZ);
        block.setServices(new StubObjectServices());
        assertEquals(halfWidth,block.getOnScreenHalfWidth());
        assertEquals(32,block.getOnScreenHalfHeight(),"obRender bit4 is clear: BuildSprites assumes 32px");
        bounds(0x600+halfWidth,0x400+32);
        assertTrue(block.isSkipSolidContactThisFrame(),"initial obRender has no rendered bit");
        block.update(1,null);
        assertTrue(block.isSkipSolidContactThisFrame(),"DisplaySprite queues; only BuildSprites writes the bit");
        block.refreshPostCameraRenderState();
        assertFalse(block.isSkipSolidContactThisFrame(),"left and upper endpoints are inclusive");
        var visible=block.captureRewindState();
        bounds(0x600+halfWidth+1,0x400+32);
        assertFalse(block.isSkipSolidContactThisFrame(),"camera changes do not rewrite the prior display bit");
        block.update(2,null);block.refreshPostCameraRenderState();
        assertTrue(block.isSkipSolidContactThisFrame());
        assertTrue(block.suppressSlopeSampleThisFrame(null));
        block.restoreRewindState(visible);
        assertFalse(block.isSkipSolidContactThisFrame(),"rewind restores the native display latch");
        block.update(2,null);block.refreshPostCameraRenderState();
        assertTrue(block.isSkipSolidContactThisFrame());
        // No DisplaySprite call occurred: a second camera publication cannot invent one.
        bounds(0x600,0x400);block.refreshPostCameraRenderState();
        assertTrue(block.isSkipSolidContactThisFrame());
        block.update(3,null);block.refreshPostCameraRenderState();
        assertFalse(block.isSkipSolidContactThisFrame());
        bounds(0x600-halfWidth-320,0x400);
        block.update(4,null);block.refreshPostCameraRenderState();
        assertTrue(block.isSkipSolidContactThisFrame(),"right edge is exclusive");
        bounds(0x600,0x400-224-32);
        block.update(5,null);block.refreshPostCameraRenderState();
        assertTrue(block.isSkipSolidContactThisFrame(),"lower edge is exclusive");
    }
    private static void bounds(int x,int y){AbstractObjectInstance.updateCameraBounds(x,y,x+320,y+224,0);}
}
