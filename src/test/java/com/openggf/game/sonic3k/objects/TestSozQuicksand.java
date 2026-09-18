package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** ROM Obj_SOZQuicksand: acquisition must return before its held-state update. */
class TestSozQuicksand {
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    private ObjectInstance sand(int subtype) { return sand(subtype,0); }
    private ObjectInstance sand(int subtype, int flips) {
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x1000, 0x1000, 0);
        ObjectInstance instance = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        }.create(new ObjectSpawn(0x230, 0x640, 0x38, subtype, flips, false, 0));
        ((AbstractObjectInstance)instance).setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> null, java.util.List::of)));
        return instance;
    }
    private TestablePlayableSprite player(int x, int y) {
        var p = new TestablePlayableSprite("sonic", (short)0, (short)0);
        p.setCentreX((short)x); p.setCentreY((short)y); p.setAir(true);
        p.setXSpeed((short)-0x101); p.setYSpeed((short)0x201);
        return p;
    }
    @Test void nativeDeadCpuRoutineCannotAcquireOrReceiveUpwardDamping() {
        for(int subtype:new int[]{0x10,0x50,0x90,0xD0}) for(short velocity:new short[]{0x201,-0x700}) {
            var p=org.mockito.Mockito.spy(player(0x230,0x640));
            var cpu=org.mockito.Mockito.mock(com.openggf.sprites.playable.SidekickCpuController.class);
            org.mockito.Mockito.when(cpu.getState()).thenReturn(com.openggf.sprites.playable.SidekickCpuController.State.DEAD_FALLING);
            org.mockito.Mockito.doReturn(cpu).when(p).getCpuController();
            p.setCpuControlled(true);p.setYSpeed(velocity);
            assertFalse(p.getDead(),"boundary-dead CPU uses its dispatch state instead of generic dead flag");
            sand(subtype).update(0,p);
            assertFalse(p.isOnObject(),"native routine6 cannot acquire subtype"+subtype);
            assertEquals(velocity,p.getYSpeed(),"routine gate precedes ascending damping");
        }
    }
    @Test void firstPlacedSandAcquiresThenSinksOnTheFollowingObjectPass() {
        var o=sand(0x10); var p=player(0x230,0x640);
        o.update(0,p);
        assertTrue(p.isOnObject(), "SKL $38 must bind to native quicksand");
        assertEquals(-0x81,p.getXSpeed()); assertEquals(0x100,p.getYSpeed());
        assertEquals(0x640,p.getCentreY()); assertEquals(14,p.getYRadius());
        o.update(1,p);
        assertEquals(-0x41,p.getXSpeed()); assertEquals(0xA8,p.getYSpeed());
    }
    @Test void normalSandLaunchUsesPressedInputAndSamePassGravity() {
        var o=sand(0x10); var p=player(0x230,0x640); o.update(0,p);
        p.setLogicalInputState(false,false,false,false,true,true);
        o.update(1,p); assertEquals(-0x798,p.getYSpeed());
        p.setLogicalInputState(false,false,false,false,true,false);
        o.update(2,p); assertEquals(-0x730,p.getYSpeed());
    }
    @Test void normalSandRightAndBottomEdgesAreExclusiveAndReleaseOwnership() {
        var o=sand(0x10); var p=player(0x240,0x640); o.update(0,p);
        assertFalse(p.isOnObject());
        p.setCentreX((short)0x220); o.update(1,p); assertTrue(p.isOnObject());
        p.setCentreY((short)0x6C0); o.update(2,p); assertFalse(p.isOnObject());
    }
    @Test void slideJumpReleasesDownwardAndHasThirtyPassRecaptureDelay() {
        var o=sand(0x50); var p=player(0x230,0x640); o.update(0,p);
        p.setLogicalInputState(false,false,false,false,true,true); o.update(1,p);
        assertEquals(0x400,p.getYSpeed()); assertFalse(p.isOnObject());
        p.setLogicalInputState(false,false,false,false,false,false);
        for(int i=0;i<30;i++) { o.update(i+2,p); assertFalse(p.isOnObject()); }
        o.update(32,p); assertTrue(p.isOnObject());
    }
    @Test void slideUsesFixedPointDriftWithoutInventingVelocity() {
        var o=sand(0x50); var p=player(0x230,0x640); o.update(0,p);
        p.setSubpixelRaw(0x9000,0x4321); o.update(1,p);
        assertEquals(0x231,p.getCentreX()); assertEquals(0x4000,p.getXSubpixelRaw());
        assertEquals(0x4321,p.getYSubpixelRaw()); assertEquals(0,p.getYSpeed());
    }
    @Test void waterfallDampingUsesLevelClockRatherThanVIntClock() {
        var o=(AbstractObjectInstance)sand(0x90); var p=player(0x230,0x640);
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        o.setServices(new StubObjectServices() {
            @Override public com.openggf.level.LevelManager levelManager() { return level; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p, java.util.List::of); }
        });
        p.setRolling(true); p.applyCustomRadii(7,14);
        o.update(99,p); assertEquals(-0x101,p.getXSpeed());
        assertFalse(p.getRolling()); assertEquals(14,p.getYRadius());
        assertEquals(1,p.getFlipAngle()); assertEquals(0x7F,p.getFlipsRemaining());
        org.mockito.Mockito.when(level.getFrameCounter()).thenReturn(7);
        o.update(8,p); assertEquals(-0x101,p.getXSpeed());
        org.mockito.Mockito.when(level.getFrameCounter()).thenReturn(8);
        o.update(9,p); assertEquals(-0x81,p.getXSpeed());
    }
    @Test void waterfallCaptureEndsTheEngineGlidePoseProjection() {
        // Recorded s3k-knuckles-complete-superemeralds SOZ1 row 2038: gliding Knuckles is
        // captured by a subtype-$80 sand fall; row 2039 records anim 0, not the glide $20.
        var o=(AbstractObjectInstance)sand(0x90); var p=player(0x230,0x640);
        o.setServices(new StubObjectServices() {
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p, java.util.List::of); }
        });
        p.setDoubleJumpFlag(1); p.setAnimationId(0x20); p.setForcedAnimationId(0x20);
        p.setObjectMappingFrameControl(true);
        o.update(0,p);
        assertEquals(0,p.getDoubleJumpFlag());
        assertEquals(0,p.getAnimationId());
        assertEquals(-1,p.getForcedAnimationId(),"the retained glide anim must not republish $20");
        assertFalse(p.isObjectMappingFrameControl(),"Animate owns the mapping after the anim write");
    }
    @Test void extraFollowersHaveIndependentCaptureAndCooldownState() {
        var o=(AbstractObjectInstance)sand(0x50);
        var leader=player(0x230,0x640); var p2=player(0x230,0x640); var p3=player(0x230,0x640);
        o.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(
                () -> leader, () -> java.util.List.of(p2,p3))));
        o.update(0,leader);
        assertTrue(leader.isOnObject()); assertTrue(p2.isOnObject()); assertTrue(p3.isOnObject());
        p2.setLogicalInputState(false,false,false,false,true,true); o.update(1,leader);
        assertTrue(leader.isOnObject()); assertFalse(p2.isOnObject()); assertTrue(p3.isOnObject());
    }
    @Test void slideCooldownSurvivesRecreationAndForwardReplay() {
        var o=(AbstractObjectInstance)sand(0x50); var p=player(0x230,0x640); o.update(0,p);
        p.setLogicalInputState(false,false,false,false,true,true); o.update(1,p);
        p.setLogicalInputState(false,false,false,false,false,false);
        var snapshot=o.captureRewindState();
        var restored=(AbstractObjectInstance)sand(0x50); restored.restoreRewindState(snapshot);
        for(int i=0;i<30;i++) { restored.update(i,p); assertFalse(p.isOnObject()); }
        restored.update(30,p); assertTrue(p.isOnObject());
    }
    @Test void sharedSlotStillResolvesHczFanInS3kl() {
        var registry=new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 1; }
        };
        assertInstanceOf(HCZCGZFanObjectInstance.class,
                registry.create(new ObjectSpawn(0,0,0x38,0,0,false,0)));
    }
    @Test void flippedSlideMovesLeftWithFractionalBorrow() {
        var o=sand(0x50,1); var p=player(0x230,0x640); o.update(0,p);
        p.setSubpixelRaw(0x9000,0x4321); o.update(1,p);
        assertEquals(0x22F,p.getCentreX()); assertEquals(0xE000,p.getXSubpixelRaw());
    }
    @Test void flippedNormalAndDeepSandUseNativeUpwardSpeed() {
        for(int subtype:new int[]{0x10,0xD0}) {
            var o=sand(subtype,2); var p=player(0x230,0x640);
            o.update(0,p); o.update(1,p); assertEquals(-0x198,p.getYSpeed());
        }
    }
    @Test void upwardWaterfallAdmitsGroundedPlayerAndPreservesRadii() {
        var o=(AbstractObjectInstance)sand(0x90,2); var p=player(0x230,0x640);
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        o.setServices(new StubObjectServices() {
            @Override public com.openggf.level.LevelManager levelManager() { return level; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p, java.util.List::of); }
        });
        p.setAirForTest(false); p.setYSpeed((short)0); p.applyCustomRadii(10,10);
        o.update(0,p); assertTrue(p.isOnObject()); assertTrue(p.getAir());
        assertEquals(10,p.getYRadius()); o.update(1,p); assertEquals(-0x330,p.getYSpeed());
        o.update(2,p); assertEquals(-0x2C8,p.getYSpeed());
    }
    @Test void knucklesDeepSandJumpUsesNativeCharacterBranch() {
        var o=sand(0xD0);
        var p=new TestablePlayableSprite("knuckles",(short)0,(short)0) {
            @Override public com.openggf.game.CharacterKey characterKey() {
                return com.openggf.game.CharacterKey.KNUCKLES;
            }
        };
        p.setCentreX((short)0x230); p.setCentreY((short)0x640); p.setAir(true);
        o.update(0,p); p.setLogicalInputState(false,false,false,false,true,true); o.update(1,p);
        assertEquals(-0x600,p.getYSpeed()); assertEquals(0x640,p.getCentreY());
    }
    @Test void distinctParticipantStatesSurviveRecreation() {
        var o=(AbstractObjectInstance)sand(0x50);
        var p1=player(0x230,0x640); var p2=player(0x230,0x640); var p3=player(0x230,0x640);
        var services=new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(
                () -> p1, () -> java.util.List.of(p2,p3)));
        o.setServices(services); o.update(0,p1);
        p2.setLogicalInputState(false,false,false,false,true,true); o.update(1,p1);
        var snapshot=o.captureRewindState();
        var restored=(AbstractObjectInstance)sand(0x50); restored.setServices(services);
        restored.restoreRewindState(snapshot);
        p2.setLogicalInputState(false,false,false,false,false,false);
        restored.update(2,p1);
        assertTrue(p1.isOnObject()); assertFalse(p2.isOnObject()); assertTrue(p3.isOnObject());
        for(int i=0;i<29;i++) { restored.update(i+3,p1); assertFalse(p2.isOnObject()); }
        restored.update(32,p1); assertTrue(p2.isOnObject());
    }
    @Test void deepSandAllowsHurtEntryAndRestoresDefaultBodyWithoutMovingCentre() {
        var o=sand(0xD0); var p=player(0x230,0x640);
        p.setHurt(true); p.setRollingFlagPreserveRadii(true); p.applyCustomRadii(7,14);
        int before=p.getCentreY();
        o.update(0,p);
        assertTrue(p.isOnObject()); assertFalse(p.isHurt()); assertFalse(p.getRolling());
        assertEquals(before,p.getCentreY()); assertEquals(120,p.getInvulnerableFrames());
        p.setLogicalInputState(false,false,false,false,true,true); o.update(1,p);
        assertEquals(-0x680,p.getYSpeed()); assertFalse(p.isOnObject());
        assertEquals(before,p.getCentreY());
    }
}
