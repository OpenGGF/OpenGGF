package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSozMechanisms {
    @AfterEach void reset() { Sonic3kLevelTriggerManager.reset(); AbstractObjectInstance.resetCameraBoundsForTests(); }
    private TestablePlayableSprite player(int x) {
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);p.setCentreX((short)x);p.setCentreY((short)100);p.setPushing(true);return p;
    }
    private SozPushSwitchObjectInstance button(int subtype,int flip,TestablePlayableSprite p) {
        var o=new SozPushSwitchObjectInstance(new ObjectSpawn(300,100,0x45,subtype,flip,false,0));
        o.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of)));
        o.setPlayerPushing(p,true);return o;
    }
    @Test void analogPushNeedsFourPassesForOnePixelAndSaturatesAt128() {
        var p=player(280);var o=button(1,0,p);p.setSubpixelRaw(0x1234,0x5678);
        for(int i=1;i<=128;i++) {
            o.update(i,p);assertEquals(i,SozZoneRuntimeState.trigger(1));assertEquals(300+i/4,o.getX());
        }
        assertEquals(312,p.getCentreX());assertEquals(0x1234,p.getXSubpixelRaw());
        o.update(129,p);assertEquals(128,SozZoneRuntimeState.trigger(1));
    }
    @Test void wrongSideDoesNotChargeAndFlipReversesPlayerMotion() {
        var p=player(320);var wrong=button(1,0,p);wrong.update(0,p);assertEquals(0,SozZoneRuntimeState.trigger(1));
        var flipped=button(1,1,p);for(int i=0;i<4;i++)flipped.update(i,p);
        assertEquals(299,flipped.getX());assertEquals(319,p.getCentreX());
    }
    @Test void releaseDecaysEveryTenEligiblePassesOrEveryPassForFastSubtypes() {
        var p=player(280);p.setPushing(false);var slow=button(1,0,p);SozZoneRuntimeState.writeTrigger(1,20);
        slow.update(0,p);assertEquals(19,SozZoneRuntimeState.trigger(1));
        for(int i=0;i<9;i++)slow.update(i,p);assertEquals(19,SozZoneRuntimeState.trigger(1));
        slow.update(10,p);assertEquals(18,SozZoneRuntimeState.trigger(1));
        var fast=button(0x11,0,p);for(int i=0;i<18;i++)fast.update(i,p);assertEquals(0,SozZoneRuntimeState.trigger(1));
    }
    @Test void decayAndParticipantStateRestoreWithoutResettingSharedSignal() {
        var p=player(280);var o=button(1,0,p);for(int i=0;i<12;i++)o.update(i,p);
        var state=o.captureRewindState();var signals=Sonic3kLevelTriggerManager.snapshot();
        p.setPushing(false);o.update(0,p);int expected=SozZoneRuntimeState.trigger(1);
        var restored=button(1,0,p);restored.restoreRewindState(state);Sonic3kLevelTriggerManager.restore(signals);
        restored.update(0,p);assertEquals(expected,SozZoneRuntimeState.trigger(1));assertEquals(o.getX(),restored.getX());
    }
    @Test void doorInitializesAtTriggerThenApproachesOnePixelInBothDirections() {
        for(int subtype:new int[]{1,0x11})for(int flip:new int[]{0,1}) {
            SozZoneRuntimeState.writeTrigger(1,20);
            var door=new SozDoorObjectInstance(new ObjectSpawn(300,300,0x46,subtype,flip,false,0));
            door.setServices(new StubObjectServices());door.update(0,null);
            int sign=flip==0?-1:1;
            assertEquals(300+sign*20,subtype==1?door.getY():door.getX());
            SozZoneRuntimeState.writeTrigger(1,128);door.update(1,null);
            assertEquals(300+sign*21,subtype==1?door.getY():door.getX());
            SozZoneRuntimeState.writeTrigger(1,0);door.update(2,null);
            assertEquals(300+sign*20,subtype==1?door.getY():door.getX());
            assertFalse(door.carriesRiderOnHorizontalMove(null));
        }
    }
    @Test void offscreenSwitchDecaysOnRetainPassThenYieldsToReloadedPlacement() {
        var manager=mock(ObjectManager.class);
        var camera=mock(com.openggf.camera.Camera.class);when(camera.getX()).thenReturn((short)0x2000);
        var p=player(280);p.setPushing(false);
        var spawn=new ObjectSpawn(300,100,0x45,0x11,0,false,0);
        var o=new SozPushSwitchObjectInstance(spawn);
        var services=new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public com.openggf.camera.Camera camera() { return camera; }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of));
        o.setServices(services);when(manager.getActiveObjects()).thenReturn(List.of(o));
        SozZoneRuntimeState.writeTrigger(1,10);o.update(0,p);
        assertEquals(8,SozZoneRuntimeState.trigger(1),"normal update falls through into retained decay");
        assertFalse(o.isDestroyed());assertFalse(o.isSolidFor(p));
        verify(manager).releaseSpawnForRespawn(o,spawn);
        var snapshot=o.captureRewindState();o.update(1,p);assertEquals(7,SozZoneRuntimeState.trigger(1));
        var restored=new SozPushSwitchObjectInstance(spawn);restored.setServices(services);restored.restoreRewindState(snapshot);
        when(manager.getActiveObjects()).thenReturn(List.of(restored));SozZoneRuntimeState.writeTrigger(1,8);
        restored.update(1,p);assertEquals(7,SozZoneRuntimeState.trigger(1));
        var replacement=new SozPushSwitchObjectInstance(spawn);
        when(manager.getActiveObjects()).thenReturn(List.of(restored,replacement));
        restored.update(2,p);assertTrue(restored.isDestroyed());assertEquals(7,SozZoneRuntimeState.trigger(1));
    }
    @Test void firstEligiblePlayerOwnsChargeAndWrongSideLetsFollowerPush() {
        var p=player(320);var follower=player(280);
        var o=button(1,0,p);
        o.setServices(new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> p,() -> List.of(follower))));
        o.setPlayerPushing(follower,true);
        for(int i=0;i<4;i++)o.update(i,p);
        assertEquals(4,SozZoneRuntimeState.trigger(1));assertEquals(281,follower.getCentreX());assertEquals(320,p.getCentreX());
        p.setCentreX((short)280);
        for(int i=0;i<4;i++)o.update(i,p);
        assertEquals(8,SozZoneRuntimeState.trigger(1));assertEquals(281,p.getCentreX());assertEquals(281,follower.getCentreX());
    }
    @Test void followerOnlyContactRestoresInQueryOrderEvenWithoutFreshCollision() {
        var p=player(280);p.setPushing(false);var follower=player(280);
        var spawn=new ObjectSpawn(300,100,0x45,1,0,false,0);
        var o=new SozPushSwitchObjectInstance(spawn);
        var context=mock(com.openggf.game.solid.ObjectSolidExecutionContext.class);
        var services=new StubObjectServices() {
            @Override public com.openggf.game.solid.ObjectSolidExecutionContext solidExecution() { return context; }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> p,() -> List.of(follower)));
        o.setServices(services);
        when(context.resolveSolidNowAll()).thenAnswer(i -> { o.setPlayerPushing(follower,true);return null; }).thenReturn(null);
        o.update(0,p);assertEquals(1,SozZoneRuntimeState.trigger(1));
        var state=o.captureRewindState();var signals=Sonic3kLevelTriggerManager.snapshot();
        o.update(1,p);assertEquals(2,SozZoneRuntimeState.trigger(1));
        var restored=new SozPushSwitchObjectInstance(spawn);restored.setServices(services);
        restored.restoreRewindState(state);Sonic3kLevelTriggerManager.restore(signals);
        restored.update(1,p);assertEquals(2,SozZoneRuntimeState.trigger(1));
    }
    @Test void cnzAndSozKeepSeparateOwnersForAllThreePointerSlots() {
        var cnz=new Sonic3kObjectRegistry() { @Override protected int currentRomZoneId() { return 3; } };
        var soz=new Sonic3kObjectRegistry() { @Override protected int currentRomZoneId() { return 8; } };
        int[] ids={0x42,0x45,0x46};
        Class<?>[] cnzOwners={CnzCannonInstance.class,CnzLightBulbInstance.class,CnzHoverFanInstance.class};
        Class<?>[] sozOwners={SozFloatingPillarObjectInstance.class,SozPushSwitchObjectInstance.class,SozDoorObjectInstance.class};
        for(int i=0;i<ids.length;i++) {
            var spawn=new ObjectSpawn(100,100,ids[i],0,0,false,0);
            assertInstanceOf(cnzOwners[i],cnz.create(spawn));assertInstanceOf(sozOwners[i],soz.create(spawn));
        }
    }
    @Test void runtimeStoresNativeSlotAndSharesExistingTriggerBytes() {
        var state=new SozZoneRuntimeState(1,PlayerCharacter.SONIC_AND_TAILS);
        state.publishPushableRockSlot(42);var bytes=state.captureBytes();state.publishPushableRockSlot(7);state.restoreBytes(bytes);
        assertEquals(42,state.pushableRockSlot());
        SozZoneRuntimeState.writeTrigger(3,0xA5);assertTrue(Sonic3kLevelTriggerManager.testBit(3,7));
        Sonic3kLevelTriggerManager.clearBit(3,7);assertEquals(0x25,SozZoneRuntimeState.trigger(3));
    }
}
