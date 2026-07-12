package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

class TestFbzPlayerTransportObjects {
    @Test void everyBentPipeSubtypeUsesTheExactRomSolidDimensions() {
        int[][] expected = {{0x18,0x10},{0x10,0x08},{0x18,0x10}};
        for (int subtype = 0; subtype < 3; subtype++) {
            var pipe = new FbzBentPipeObjectInstance(spawn(0x76, subtype, 0));
            assertEquals(expected[subtype][0] + 0x0B, pipe.getSolidParams().halfWidth());
            assertEquals(expected[subtype][1], pipe.getSolidParams().airHalfHeight());
            assertEquals(expected[subtype][1] + 1, pipe.getSolidParams().groundHalfHeight());
            assertEquals(subtype, pipe.mappingFrame());
            assertEquals(4,pipe.getPriorityBucket());
        }
    }

    @Test void launcherUsesExactTwelveTickAccelerationAndOnePixelReturn() {
        var launcher = new FbzDezPlayerLauncherObjectInstance(spawn(0x78, 0, 0));
        launcher.beginLaunchForTest();
        assertEquals(0x100, launcher.xVelocity());
        launcher.stepMotionForTest(); assertEquals(0x200, launcher.xVelocity());
        launcher.stepMotionForTest(); assertEquals(0x400, launcher.xVelocity());
        launcher.stepMotionForTest(); assertEquals(0x800, launcher.xVelocity());
        launcher.stepMotionForTest(); assertEquals(0x1000, launcher.xVelocity());
        for (int i = 4; i < 12; i++) launcher.stepMotionForTest();
        assertTrue(launcher.returning());
        int before = launcher.getX();
        launcher.stepMotionForTest();
        assertEquals(before - 1, launcher.getX());
        assertEquals(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                launcher.participationPolicy());
        assertEquals(5,launcher.getPriorityBucket());
    }

    @Test void firstStandingParticipantStartsOneFloorLauncherEdgeAndPreservesNativeFraction() {
        var launcher=new FbzDezPlayerLauncherObjectInstance(spawn(0x78,0,0));RecordingServices services=new RecordingServices();launcher.setServices(services);
        TestSprite p=new TestSprite();p.setSubpixelRaw(0x1234,0x5678);
        launcher.onSolidContact(p,new SolidContact(true,false,false,false,false,0,false),0);
        assertEquals(1,services.count);assertEquals(Sonic3kSfx.FLOOR_LAUNCHER.id,services.last);
        assertEquals(0x1234,p.getXSubpixelRaw());
        launcher.onSolidContact(p,new SolidContact(true,false,false,false,false,0,false),1);
        assertEquals(1,services.count,"12-tick phase plays once");
    }

    @Test void launcherWritesFacingOnEveryRiderContact() {
        TestSprite p=new TestSprite();var right=new FbzDezPlayerLauncherObjectInstance(spawn(0x78,0,0));right.setServices(new RecordingServices());right.onSolidContact(p,new SolidContact(true,false,false,false,false,0,false),0);assertEquals(com.openggf.physics.Direction.RIGHT,p.getDirection());
        var left=new FbzDezPlayerLauncherObjectInstance(spawn(0x78,0,1));left.setServices(new RecordingServices());left.onSolidContact(p,new SolidContact(true,false,false,false,false,0,false),1);assertEquals(com.openggf.physics.Direction.LEFT,p.getDirection());
    }

    @Test void bentPipeKeepsFullMappingSubtypeButMasksOnlySizeLookupAndUsesCoarseCull() {
        var pipe=new FbzBentPipeObjectInstance(spawn(0x76,0x82,0));assertEquals(0x82,pipe.mappingFrame());assertEquals(0x23,pipe.getSolidParams().halfWidth());assertFalse(pipe.isCustomOutOfRange(0x1000));assertTrue(pipe.isCustomOutOfRange(0x2000));
    }

    @Test void bentPipeRejectsMalformedMaskedSizeIndexThreeInsteadOfAliasingRowTwo() {
        assertThrows(IllegalArgumentException.class,()->new FbzBentPipeObjectInstance(spawn(0x76,0x83,0)));
    }

    private static final class RecordingServices extends TestObjectServices {int count,last;@Override public void playSfx(int id){count++;last=id;}}
    private static final class TestSprite extends AbstractPlayableSprite {TestSprite(){super("sonic",(short)0,(short)0);}@Override public void draw(){}@Override public void defineSpeeds(){}@Override protected void createSensorLines(){}}

    private static ObjectSpawn spawn(int id, int subtype, int flags) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, flags, true, 4);
    }
}
