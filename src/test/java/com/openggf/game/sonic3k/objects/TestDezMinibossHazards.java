package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossHazards {
    private static final class Parent extends DezMinibossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"HazardTestParent"); word3A=0x4000; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) { }
    }
    private ObjectServices services(ObjectManager manager,GameRng rng) throws Exception {
        var services=mock(ObjectServices.class);
        when(services.romReader()).thenReturn(TestEnvironment.objectServices().romReader());
        when(services.objectManager()).thenReturn(manager);
        when(services.rng()).thenReturn(rng);
        return services;
    }
    private HeadlessTestFixture boot() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x3740,(short)0x2C0).startPositionIsCentre().build();
        fixture.sprite().setDebugMode(true); DezMinibossTestSupport.retirePlacedEncounter(fixture); return fixture;
    }

    @Test void orbitUsesRomPriorityBandsAndHidesTheRearCentreWithoutClearingCollision() throws Exception {
        var parent=new Parent(); parent.word3C=0xFC00;
        var orb=new DezMinibossOrb(parent,0,false); orb.setServices(services(null,null));
        for(int pass=0;pass<64;pass++) {
            orb.update(pass,null);
            int angle=((pass+1)*4)&0xFF;
            boolean high=angle<64 || angle>=192;
            assertEquals(high,orb.highPriority,"angle "+angle);
            assertEquals(high?3:6,orb.priority);
            assertEquals(0x2BC,orb.getY()); assertEquals(0x86,orb.getCollisionFlags());
            boolean hidden=!high && Math.abs(orb.getX()-parent.getX())<32;
            assertEquals(!hidden,orb.visible,"angle "+angle);
            assertEquals(!hidden,orb.publishesTouchResponseListEntryThisFrame());
        }
        parent.status=0x40; orb.update(64,null); assertEquals(0,orb.getCollisionFlags());
    }

    @Test void launchIsConsumedByFirstOrbAndHasNoDrawUntilTheNextPass() throws Exception {
        var parent=new Parent(); var first=new DezMinibossOrb(parent,0,false);
        var second=new DezMinibossOrb(parent,2,false);
        first.setServices(services(null,null)); second.setServices(services(null,null));
        first.update(0,null); second.update(0,null);
        int x=first.getX(),y=first.getY(); parent.control=2;
        first.update(1,null); second.update(1,null);
        assertEquals(0,parent.control); assertFalse(first.visible); assertEquals(0,second.yVelocity);
        assertFalse(second.visible,"second orb remains hidden behind the centre");
        assertEquals(x,first.getX()); assertEquals(y,first.getY()); assertEquals(-0x400,first.yVelocity);
        first.update(2,null); assertEquals(y-4,first.getY()); assertEquals(-0x3C8,first.yVelocity);
        assertTrue(first.visible); assertEquals(0x15,first.frame); assertTrue(first.highPriority);
    }

    @Test void everyBurstAllocationPrefixStopsWithoutRetryAndUsesSameSweepFragmentInit() throws Exception {
        // Real SST allocator: controller first, then eight fragments; no reservation rollback.
        for(int free=0;free<=9;free++) {
            var fixture=boot(); var manager=GameServices.level().getObjectManager();
            var parent=new Parent(); manager.addDynamicObject(parent);
            var orb=new DezMinibossOrb(parent,0,false); manager.addDynamicObject(orb);
            fixture.stepIdleFrames(1); parent.control=2; fixture.stepIdleFrames(1);
            manager.reserveAllButNFreeSlots(free);
            fixture.stepIdleFrames(19); // -$400 + 19*$38 first crosses zero on pass 19
            assertTrue(orb.pendingDelete); assertFalse(orb.isDestroyed());
            var fragments=manager.activeObjectsOfType(DezMinibossOrb.class).stream().filter(o->o!=orb).toList();
            assertEquals(Math.max(0,free-1),fragments.size(),"free="+free);
            assertEquals(free==0?0:1,manager.activeObjectsOfType(DezMinibossExplosionController.class).size());
            for(int i=0;i<fragments.size();i++) {
                var child=fragments.get(i);
                assertEquals(orb.getX(),child.getX()); assertEquals(orb.getY(),child.getY());
                assertFalse(child.visible,"fragment init must run without drawing");
                assertEquals(0x98,child.getCollisionFlags());
                assertEquals(List.of(0,0x16A,0x200,0x16A,0,-0x16A,-0x200,-0x16A).get(i).intValue(),child.xVelocity);
            }
            fixture.stepIdleFrames(1); assertTrue(orb.isDestroyed());
            assertEquals(Math.max(0,free-1),manager.activeObjectsOfType(DezMinibossOrb.class).size());
        }
    }

    @Test void failedControllerAllocationConsumesTheAttemptButNoRandomNumber() throws Exception {
        var manager=mock(ObjectManager.class); var rng=mock(GameRng.class);
        var controller=new DezMinibossExplosionController(0x300,0x500,6);
        controller.setServices(services(manager,rng)); controller.setSlotIndex(7);
        when(manager.allocateSlotAfter(7)).thenReturn(-1,8,-1);
        when(rng.nextRaw()).thenReturn(0x001F0000);
        for(int i=0;i<3;i++) controller.update(i,null);
        verifyNoInteractions(rng);
        controller.update(3,null);
        var captor=org.mockito.ArgumentCaptor.forClass(S3kBossExplosionChild.class);
        verify(manager).addDynamicObjectAtSlot(captor.capture(),eq(8));
        assertEquals(0x2F0,captor.getValue().getX()); assertEquals(0x50F,captor.getValue().getY());
        for(int i=4;i<=9;i++) controller.update(i,null);
        assertTrue(controller.pendingDelete); assertFalse(controller.isDestroyed());
        controller.update(10,null); assertTrue(controller.isDestroyed());
        verify(manager,times(3)).allocateSlotAfter(7); verify(rng,times(1)).nextRaw();
    }

    @Test void followingControllerTracksParentAndStopsOnePassAfterControlFive() throws Exception {
        var parent=new Parent(); var manager=mock(ObjectManager.class); var rng=mock(GameRng.class);
        when(manager.allocateSlotAfter(anyInt())).thenReturn(-1);
        var controller=new DezMinibossExplosionController(parent,0xE);
        controller.setServices(services(manager,rng)); controller.setSlotIndex(4);
        for(int i=0;i<400;i++) { parent.writeX(0x3700+i); controller.update(i,null); }
        assertFalse(controller.pendingDelete); assertEquals(parent.getX(),controller.getX());
        parent.control=0x20; controller.update(400,null);
        assertTrue(controller.pendingDelete); assertFalse(controller.isDestroyed());
        controller.update(401,null); assertTrue(controller.isDestroyed()); verifyNoInteractions(rng);
    }

    @Test void parentedNormalBurstDecrementsInitialNegativeByteAndExpires() throws Exception {
        var parent=new Parent(); var manager=mock(ObjectManager.class); var rng=mock(GameRng.class);
        when(manager.allocateSlotAfter(anyInt())).thenReturn(-1);
        var controller=new DezMinibossExplosionController(parent,0x18);
        controller.setServices(services(manager,rng)); controller.setSlotIndex(4);
        for(int i=0;i<381;i++) {
            parent.writeX(0x3700+i); controller.update(i,null);
            assertEquals(parent.getX(),controller.getX());
            assertFalse(controller.pendingDelete,"pass="+i);
        }
        controller.update(381,null);
        assertTrue(controller.pendingDelete); assertFalse(controller.isDestroyed());
        controller.update(382,null); assertTrue(controller.isDestroyed());
        verify(manager,times(127)).allocateSlotAfter(4); verifyNoInteractions(rng);
    }

    @Test void normalExplosionHasNoAnimalAndUsesThreeThenEightPassFrames() throws Exception {
        var manager=mock(ObjectManager.class); var services=services(manager,null);
        var explosion=new DezMinibossExplosionController.NormalExplosion(new ObjectSpawn(0,0,0,0,0,false,0));
        explosion.setServices(services);
        for(int i=0;i<35;i++) {
            explosion.update(i,null);
            assertFalse(explosion.isDestroyed()); assertEquals(i<3?0:1+(i-3)/8,explosion.frame);
        }
        explosion.update(35,null); assertTrue(explosion.isDestroyed());
        verifyNoInteractions(manager); verify(services,times(1)).playSfx(0x3D);
    }

    @Test void realManagerRestoresParentLinksAndReplaysOrbitLaunchAndBurst() throws Exception {
        var fixture=boot(); var manager=GameServices.level().getObjectManager();
        var parent=new Parent(); manager.addDynamicObject(parent);
        var orb=new DezMinibossOrb(parent,0,false); manager.addDynamicObject(orb);
        var controller=new DezMinibossExplosionController(parent,0xE); manager.addDynamicObject(controller);
        fixture.stepIdleFrames(7); parent.control=2;
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(fixture,32);
        parent.setDestroyed(true); orb.setDestroyed(true); controller.setDestroyed(true); fixture.stepIdleFrames(1);
        registry.restore(saved);
        var restored=manager.activeObjectsOfType(DezMinibossOrb.class).getFirst();
        var restoredController=manager.activeObjectsOfType(DezMinibossExplosionController.class).getFirst();
        assertNotSame(orb,restored); assertNotSame(parent,restored.parentForTest());
        assertSame(restored.parentForTest(),restoredController.parentForTest());
        assertEquals(expected,rows(fixture,32));
        // Capture after the launch orb has been deleted, while its fragments and
        // finite explosion controller still live. Capturing before the burst alone
        // missed dangling creator references in both kinds of independent child.
        var detached = registry.capture();
        var detachedForward = rows(fixture,8);
        registry.restore(detached);
        assertEquals(detachedForward,rows(fixture,8));
    }
    private List<String> rows(HeadlessTestFixture fixture,int count) {
        var rows=new ArrayList<String>(); var manager=GameServices.level().getObjectManager();
        for(int i=0;i<count;i++) {
            fixture.stepIdleFrames(1);
            rows.add(manager.getActiveObjects().stream().filter(o->o instanceof DezMinibossSprite || o instanceof S3kBossExplosionChild)
                    .map(o->o.getClass().getSimpleName()+":"+((AbstractObjectInstance)o).getSlotIndex()+":"+o.getX()+":"+o.getY()
                            +(o instanceof DezMinibossSprite d?":"+d.posX+":"+d.posY+":"+d.frame+":"+d.visible+":"+d.pendingDelete:""))
                    .sorted().toList().toString());
        }
        return rows;
    }
}
