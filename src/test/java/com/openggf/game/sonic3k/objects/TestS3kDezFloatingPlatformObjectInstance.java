package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kDezFloatingPlatformObjectInstance {
    @BeforeEach void reset() { TestEnvironment.resetAll(); SessionManager.clear(); }
    @AfterEach void resetCamera() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    @Test void allNineMoversUseTheSharedRomRoutinesButIgnoreTheSkinNibble() {
        for (int mode=0;mode<9;mode++) for(int flags=0;flags<4;flags++) {
            var p=platform(mode|0xF0,flags);
            var reference=new LrzSolidMovingPlatformObjectInstance(new ObjectSpawn(0x1000,0x800,0x2D,mode,flags,false,0));
            for(int i=0;i<600;i++) {
                p.update(i,null); reference.update(i,null);
                assertEquals(reference.getX(),p.getX(),"mode="+mode+",flags="+flags+",frame="+i);
                assertEquals(reference.getY(),p.getY());
                assertEquals((i+1)&1,p.mappingFrameForTest(),"DEZ alternates independently of the LRZ skin");
            }
        }
    }
    @Test void oscillatorOffsetsApplyStatusXFlipToEitherAxis() {
        for(int frame=0;frame<400;frame++) {
            OscillationManager.update(frame);
            // Full native layout includes the control word. Assert the ROM's
            // value byte, independently of the engine offset passed by either object.
            byte[] romTable=OscillationManager.snapshotRomFormatBytes();
            for(int mode:new int[]{1,2,4,5}) for(int flags=0;flags<4;flags++) {
                var p=platform(mode,flags);p.update(frame,null);
                var lrz=new LrzSolidMovingPlatformObjectInstance(new ObjectSpawn(0x1000,0x800,0x2D,mode,flags,false,0));
                lrz.update(frame,null);
                int d=(romTable[mode==1||mode==4?0xA:0x1E]&255)-(mode==1||mode==4?0x20:0x40);
                if((flags&1)!=0)d=-d;
                assertEquals(0x1000+(mode<3?d:0),p.getX());
                assertEquals(0x800+(mode>3?d:0),p.getY());
                assertEquals(p.getX(),lrz.getX());assertEquals(p.getY(),lrz.getY());
            }
        }
    }

    @Test void rampUsesHighByteOfWordAndTurnsOnTheExactShortAndLongThresholds() {
        for(int mode:new int[]{3,6,7,8}) {
            var p=platform(mode,0);int turn=mode<7?110:127;
            for(int n=1;n<=turn;n++) {
                p.update(17,null);
                assertEquals(4*n,p.rampVelocityForTest());
                assertEquals(2*n*(n+1),p.rampPositionForTest());
                assertEquals(n==turn,p.rampReturningForTest(),"first turning dispatch "+turn);
            }
            int pos=p.rampPositionForTest();p.update(17,null);
            assertEquals(4*(turn-1),p.rampVelocityForTest());
            assertEquals(pos+4*(turn-1),p.rampPositionForTest());
        }
    }
    @Test void initClearsArtFlipsWhileStatusStillMirrorsMovementInBothZones() {
        for(int flags=0;flags<4;flags++) {
            var services=mock(com.openggf.level.objects.ObjectServices.class);
            var renderManager=mock(com.openggf.level.objects.ObjectRenderManager.class);
            var renderer=mock(com.openggf.level.render.PatternSpriteRenderer.class);
            when(services.renderManager()).thenReturn(renderManager);
            when(renderManager.getRenderer(anyString())).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            var dez=platform(1,flags);dez.setServices(services);dez.update(0,null);
            dez.appendRenderCommands(new java.util.ArrayList<>());
            verify(renderer).drawFrameIndex(1,dez.getX(),dez.getY(),false,false);
            var lrz=new LrzSolidMovingPlatformObjectInstance(new ObjectSpawn(0x1000,0x800,0x2D,1,flags,false,0));
            lrz.setServices(services);lrz.update(0,null);lrz.appendRenderCommands(new java.util.ArrayList<>());
            verify(renderer).drawFrameIndex(0,lrz.getX(),lrz.getY(),false,false);
        }
    }
    @Test void lifetimeUsesOriginalAnchorAndViewportPolicy() {
        var p=new S3kDezFloatingPlatformObjectInstance(new ObjectSpawn(0x270,0x800,0x4A,3,0,false,0));
        p.update(0,null);assertEquals(0x210,p.getX());
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0x800);
        assertFalse(p.isCustomOutOfRange(0));assertTrue(p.isCustomOutOfRange(-128));
        AbstractObjectInstance.updateCameraBounds(0,0,800,224,0x800);
        assertFalse(p.isCustomOutOfRange(-128));
    }
    private S3kDezFloatingPlatformObjectInstance platform(int subtype,int flags) {
        return new S3kDezFloatingPlatformObjectInstance(new ObjectSpawn(0x1000,0x800,0x4A,subtype,flags,false,0));
    }
}
