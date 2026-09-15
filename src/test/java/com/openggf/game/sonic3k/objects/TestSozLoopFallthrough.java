package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSozLoopFallthrough {
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    private SozLoopFallthroughObjectInstance controller(int subtype) {
        AbstractObjectInstance.updateCameraBounds(0,0,0x1000,0x1000,0);
        var o=assertInstanceOf(SozLoopFallthroughObjectInstance.class,new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        }.create(new ObjectSpawn(0x300,0x400,0x3B,subtype,0,false,0)));
        o.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> null,List::of)));
        return o;
    }
    private TestablePlayableSprite player(int x,int y,int speed) {
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setCentreX((short)x); p.setCentreY((short)y); p.setYSpeed((short)speed);
        return p;
    }
    @Test void captureReturnsBeforeMovementAndWritesLiteralRadii() {
        var o=controller(0x21); var p=player(0x300,0x400,0x800);
        p.setSubpixelRaw(0x1234,0x5678); p.setXSpeed((short)0x123);
        p.setRollingJump(true); p.setJumping(true); p.setDoubleJumpFlag(1);
        o.update(0,p);
        assertTrue(p.isOnObject()); assertTrue(p.isObjectControlled()); assertTrue(p.getAir());
        assertFalse(p.isObjectControlAllowsCpu()); assertTrue(p.isObjectControlSuppressesMovement());
        assertEquals(0x400,p.getCentreY()); assertEquals(0x5678,p.getYSubpixelRaw());
        assertEquals(0,p.getXSpeed()); assertEquals(0x800,p.getYSpeed());
        assertEquals(7,p.getXRadius()); assertEquals(14,p.getYRadius()); assertEquals(2,p.getAnimationId());
        assertFalse(p.getRollingJump()); assertFalse(p.isJumping()); assertEquals(0,p.getDoubleJumpFlag());
        o.update(1,p);
        assertEquals(0x408,p.getCentreY()); assertEquals(0x838,p.getYSpeed());
        assertEquals(0x5678,p.getYSubpixelRaw());
    }
    @Test void captureUsesExclusiveWordRangesAndSignedSpeedThreshold() {
        for(int[] c:new int[][]{{0x310,0x400,0x800},{0x300,0x410,0x800},{0x300,0x400,0x7FF},{0x300,0x400,0xFFFF}}) {
            var o=controller(0x21); var p=player(c[0],c[1],c[2]); o.update(0,p); assertFalse(p.isOnObject());
        }
        var p=player(0x2F0,0x3F0,0x800); controller(0x21).update(0,p); assertTrue(p.isOnObject());
    }
    @Test void nativeRoutineAndOwnershipGatesPreventCapture() {
        for(int gate=0;gate<4;gate++) {
            var p=player(0x300,0x400,0x800);
            switch(gate) { case 0 -> p.setHurt(true); case 1 -> p.setDead(true); case 2 -> p.setOnObject(true); case 3 -> p.setObjectControlled(true); }
            controller(0x21).update(0,p); assertFalse(p.getRolling());
        }
    }
    @Test void releaseAtEqualityRetainsRollVelocityAndFractionsAndMasksSubtypeBitSeven() {
        var o=controller(0x81); var p=player(0x300,0x400,0x800); o.update(0,p);
        p.setCentreY((short)0x408); p.setSubpixelRaw(0,0xA123); o.update(1,p);
        assertEquals(0x410,p.getCentreY()); assertEquals(0xA123,p.getYSubpixelRaw());
        assertFalse(p.isOnObject()); assertFalse(p.isObjectControlled()); assertTrue(p.getRolling());
        assertEquals(0x838,p.getYSpeed());
    }
    @Test void heldPlayersRemainIndependentAcrossRecreation() {
        var o=controller(0x21); var p=player(0x300,0x400,0x800); var follower=player(0x300,0x400,0x900);
        var svc=new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,() -> List.of(follower)));
        o.setServices(svc); o.update(0,p);
        var restored=controller(0x21); restored.setServices(svc); restored.restoreRewindState(o.captureRewindState());
        p.setCentreY((short)0x608); restored.update(1,p);
        assertFalse(p.isObjectControlled()); assertTrue(follower.isObjectControlled()); assertEquals(0x409,follower.getCentreY());
    }
    @Test void hczBindingIsPreserved() {
        var r=new Sonic3kObjectRegistry() { @Override protected int currentRomZoneId() { return 1; } };
        assertInstanceOf(HCZWaterWallObjectInstance.class,r.create(new ObjectSpawn(0,0,0x3B,0,0,false,0)));
    }
}
