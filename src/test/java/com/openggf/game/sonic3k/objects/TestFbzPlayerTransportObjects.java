package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

class TestFbzPlayerTransportObjects {
    @AfterEach void resetObjectCameraBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

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

    @Test void launcherTerminalOutwardFrameEjectsRiderOnce() {
        var launcher = launcherInTerminalOutwardFrame();
        TestSprite rider = new TestSprite();
        rider.setAirRaw(false);
        rider.setYSpeed((short) 0x456);
        rider.setAnimationId(7);
        TestSprite secondRider = new TestSprite();
        secondRider.setAirRaw(false);
        secondRider.setYSpeed((short) 0x654);
        secondRider.setAnimationId(9);

        launcher.onSolidContact(rider, standingContact(), 12);
        launcher.onSolidContact(secondRider, standingContact(), 12);

        assertTrue(rider.getAir());
        assertEquals(0, rider.getYSpeed());
        assertEquals(0, rider.getAnimationId());
        assertTrue(secondRider.getAir());
        assertEquals(0, secondRider.getYSpeed());
        assertEquals(0, secondRider.getAnimationId(),
                "each independently standing participant receives sub_3B9D8's eject state");
    }

    @Test void launcherTerminalTwelfthTickZerosVelocityBeforeMoveSprite2() {
        var launcher = new FbzDezPlayerLauncherObjectInstance(spawn(0x78, 0, 0));
        launcher.setServices(new RecordingServices());
        launcher.beginLaunchForTest();
        for (int i = 0; i < 11; i++) launcher.stepMotionForTest();
        int tickElevenX = launcher.getX();

        launcher.stepMotionForTest();

        assertEquals(tickElevenX, launcher.getX(),
                "loc_3B97A clears x_vel before the terminal MoveSprite2 call");
        assertEquals(0, launcher.xVelocity());
        assertTrue(launcher.returning());
    }

    @Test void launcherReturnContactDoesNotReEjectOrZeroRiderVelocity() {
        var launcher = launcherInTerminalOutwardFrame();
        TestSprite rider = new TestSprite();
        launcher.onSolidContact(rider, standingContact(), 12);
        assertEquals(launcher.getX() + 4, rider.getCentreX(),
                "terminal outward contact keeps the native launcher anchor before ejecting");
        launcher.stepMotionForTest();
        rider.setAirRaw(false);
        rider.setYSpeed((short) 0x456);
        rider.setCentreX((short) 0x2222);
        rider.setDirection(com.openggf.physics.Direction.LEFT);

        launcher.onSolidContact(rider, standingContact(), 13);

        assertFalse(rider.getAir());
        assertEquals(0x456, rider.getYSpeed());
        assertEquals(0x2222, rider.getCentreX(),
                "loc_3BA4A return contact must not re-anchor the launched player");
        assertEquals(com.openggf.physics.Direction.LEFT, rider.getDirection(),
                "loc_3BA4A return frames do not execute sub_3B9D8's facing write");
    }

    @Test void launcherReturnEqualityFrameDoesNotFallThroughToNormalCallback() {
        RecordingServices services = new RecordingServices();
        var launcher = new FbzDezPlayerLauncherObjectInstance(spawn(0x78, 0, 0));
        launcher.setServices(services);
        launcher.beginLaunchForTest();
        for (int i = 0; i < 12; i++) launcher.stepMotionForTest();
        while (launcher.getX() != 0x1000) launcher.stepMotionForTest();
        assertTrue(launcher.returning(), "arrival at the anchor still belongs to loc_3BA4A");

        launcher.stepMotionForTest();
        assertFalse(launcher.returning(), "equality switches the next-frame routine to loc_3B97A");

        TestSprite rider = new TestSprite();
        rider.setCentreX((short) 0x2222);
        rider.setXSpeed((short) 0x345);
        rider.setGSpeed((short) 0x456);
        rider.setDirection(com.openggf.physics.Direction.LEFT);
        launcher.onSolidContact(rider, standingContact(), 200);

        assertEquals(0x2222, rider.getCentreX(), "equality frame must not re-anchor the rider");
        assertEquals(0x345, rider.getXSpeed(), "equality frame must not transfer launcher velocity");
        assertEquals(0x456, rider.getGSpeed(), "equality frame must not transfer ground velocity");
        assertEquals(com.openggf.physics.Direction.LEFT, rider.getDirection(),
                "equality frame must not write facing");
        assertEquals(0, launcher.xVelocity(), "equality frame must not relaunch");
        assertEquals(0, services.count, "equality frame must not replay the launcher SFX");
    }

    @Test void launcherReturnSolidContactUsesSavedPreMoveXAnchor() {
        var launcher = launcherInTerminalOutwardFrame();
        TestSprite rider = new TestSprite();
        launcher.snapshotPreUpdatePosition();
        int savedX = launcher.getPreUpdateX();
        launcher.stepMotionForTest();

        assertEquals(savedX - 1, launcher.getX());
        assertTrue(launcher.usesPreUpdatePositionForSolidContact(rider),
                "loc_3BA4A passes the saved pre-move x_pos to SolidObjectTop");
        assertEquals(savedX, launcher.getPreUpdateX());
    }

    @Test void launcherLifetimeCullUsesFixedAnchorAfterOutwardDisplacement() {
        Camera inRange = cameraAt(0x0E00, 0);
        AbstractObjectInstance.updateCameraBounds(0x0E00, 0, 0x0F40, 0x00E0, 0);

        var stationary = launcherAt(0x107F, 0x0100, inRange);
        stationary.update(0, null);
        assertFalse(stationary.isDestroyed());

        var displaced = launcherAt(0x107F, 0x0100, inRange);
        displaced.beginLaunchForTest();
        for (int i = 0; i < 11; i++) displaced.stepMotionForTest();
        assertTrue(displaced.getX() > 0x10C0, "test must cross the moving-sprite rectangle");
        displaced.update(11, null);
        assertFalse(displaced.isDestroyed(),
                "Sprite_OnScreen_Test2 tests saved anchor $44, not moving x_pos");

        Camera outOfRange = cameraAt(0x0D80, 0);
        var stationaryOutside = launcherAt(0x107F, 0x0100, outOfRange);
        stationaryOutside.update(0, null);
        assertTrue(stationaryOutside.isDestroyed(),
                "anchor deletion begins only after the native coarse-X threshold");
    }

    @Test void launcherLifetimeCullIgnoresVerticalCameraSeparation() {
        Camera camera = cameraAt(0x0E00, 0);
        AbstractObjectInstance.updateCameraBounds(0x0E00, 0, 0x0F40, 0x00E0, 0);
        var launcher = launcherAt(0x1000, 0x0800, camera);

        launcher.update(0, null);

        assertFalse(launcher.isDestroyed(),
                "Sprite_OnScreen_Test2 lifetime is coarse-X only, regardless of camera Y");
    }

    @Test void launcherLifetimeCullKeepsFixedAnchorVisibleOnlyThroughWidescreenExtension() {
        Camera camera = cameraAt(0, 0x0540);
        AbstractObjectInstance.updateCameraBounds(0, 0x0540, 800, 0x0620, 0);
        var widescreenLauncher = launcherAt(0x02A0, 0x0100, camera);

        widescreenLauncher.update(0, null);

        assertFalse(widescreenLauncher.isDestroyed(),
                "the viewport extension must retain a fixed launcher anchor inside the visible route");

        AbstractObjectInstance.updateCameraBounds(0, 0x0540, 320, 0x0620, 0);
        var nativeLauncher = launcherAt(0x02A0, 0x0100, camera);

        nativeLauncher.update(0, null);

        assertTrue(nativeLauncher.isDestroyed(),
                "native width must retain Sprite_OnScreen_Test2's exact $280 coarse-X threshold");
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

    @Test void launcherNormalRoutineWritesFacingOnRiderContact() {
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
    private static final class TestSprite extends AbstractPlayableSprite {TestSprite(){super("sonic",(short)0,(short)0);}void setAirRaw(boolean value){air=value;}@Override public void draw(){}@Override public void defineSpeeds(){}@Override protected void createSensorLines(){}}

    private static FbzDezPlayerLauncherObjectInstance launcherInTerminalOutwardFrame() {
        var launcher = new FbzDezPlayerLauncherObjectInstance(spawn(0x78, 0, 0));
        launcher.setServices(new RecordingServices());
        launcher.beginLaunchForTest();
        for (int i = 0; i < 12; i++) launcher.stepMotionForTest();
        assertTrue(launcher.returning());
        return launcher;
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, false, false, 0, false);
    }

    private static FbzDezPlayerLauncherObjectInstance launcherAt(int x, int y, Camera camera) {
        var launcher = new FbzDezPlayerLauncherObjectInstance(
                new ObjectSpawn(x, y, 0x78, 0, 0, true, 4));
        launcher.setServices(new RecordingServices().withCamera(camera));
        return launcher;
    }

    private static Camera cameraAt(int x, int y) {
        return new Camera() {
            @Override public short getX() { return (short) x; }
            @Override public short getY() { return (short) y; }
        };
    }

    private static ObjectSpawn spawn(int id, int subtype, int flags) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, flags, true, 4);
    }
}
