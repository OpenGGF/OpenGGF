package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalBossController {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true);
        f.camera().setX((short)0x80); f.camera().setY((short)0x20); f.camera().setScrollLocked(true); return f;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private DezFinalBossController root() {
        var root=new DezFinalBossController(); GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    private void reachHands(DezFinalBossController root) {
        root.update(0,null);
        for(int i=0;i<200 && root.routineForTest()!=6;i++) root.update(i,null);
        assertEquals(6,root.routineForTest()); state().bossSignals(2); root.update(0,null);
        for(int i=0;i<192;i++) root.update(i,null);
        assertEquals(0xA,root.routineForTest()); state().bossSignals(6); root.update(0,null);
        assertEquals(0xC,root.routineForTest());
    }
    private void reachCore(DezFinalBossController root) {
        reachHands(root); var player=TestEnvironment.objectServices().playerQuery().mainPlayerOrNull();
        player.setCentreX((short)0x700);
        for(int i=0;i<1500 && root.routineForTest()!=0x10;i++) root.update(i,null);
        assertEquals(0x10,root.routineForTest()); root.handDestroyed(0); root.handDestroyed(2); root.update(0,null);
        assertEquals(0x12,root.routineForTest());
        for(int i=0;i<600 && root.routineForTest()!=0x16;i++) root.update(i,null);
        assertEquals(0x16,root.routineForTest());
    }
    @Test void forcedEntryUsesNativePositionsSixPixelStepsStopAndCoverSignal() {
        var f=boot(); var root=root(); root.update(0,null);
        assertEquals(0x30,f.sprite().getCentreX()); assertEquals(0xCD,f.sprite().getCentreY());
        assertTrue(f.sprite().isObjectControlled()); assertEquals(0x600,f.sprite().getXSpeed());
        root.update(1,null); assertEquals(0x36,f.sprite().getCentreX()); assertEquals(2,root.routineForTest());
        for(int i=0;i<200 && root.routineForTest()!=6;i++) root.update(i,null);
        assertEquals(0x360,f.sprite().getCentreX()); assertEquals(0,f.sprite().getXSpeed()); assertEquals(5,f.sprite().getAnimationId());
        assertTrue(f.sprite().isObjectControlled()); root.update(0,null); assertEquals(6,root.routineForTest());
        state().bossSignals(2); root.update(0,null); assertEquals(8,root.routineForTest());
        assertFalse(f.sprite().isObjectControlled()); assertEquals(0xF8,root.getY());
        for(int i=0;i<191;i++) root.update(i,null);
        assertEquals(8,root.routineForTest()); root.update(0,null);
        assertEquals(0xA,root.routineForTest()); assertEquals(0x98,root.getY());
        assertEquals(root.getY(),state().bossY());
        assertFalse((Object)root instanceof TouchResponseProvider,"plane controller is never the hit target");
    }
    @Test void handsAndFingersFormDistinctOwnedGraphAndRewindWithoutHealing() {
        boot(); var root=root(); reachHands(root); var m=GameServices.level().getObjectManager();
        assertEquals(1,m.activeObjectsOfType(DezFinalCore.class).size()); assertEquals(1,m.activeObjectsOfType(DezFinalEmerald.class).size());
        var hands=m.activeObjectsOfType(DezFinalHand.class); assertEquals(2,hands.size());
        for(var hand:hands) hand.update(0,null);
        assertEquals(6,m.activeObjectsOfType(DezFinalHand.Finger.class).size());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        root.handControl(0x22); root.handDestroyed(0); root.handDestroyed(2); registry.restore(saved);
        var restored=m.activeObjectsOfType(DezFinalBossController.class).getFirst(); assertNotSame(root,restored);
        assertEquals(0,restored.handControl()); assertEquals(2,m.activeObjectsOfType(DezFinalHand.class).size());
        assertEquals(6,m.activeObjectsOfType(DezFinalHand.Finger.class).size());
    }
    @Test void twoHandDefeatsTriggerPlaneSwapAndCoreFireGateWithCapturedClock() {
        var f=boot(); var root=root(); reachCore(root); assertEquals(0xAF,root.getY());
        assertEquals(0x80,root.fireClock); assertEquals(0x6C0,state().windowBase());
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(DezFinalMouth.Button.class).size());
        root.handControl(root.handControl()|4); root.update(0,null); assertEquals(0x80,root.fireClock);
        root.handControl(root.handControl()&~4); root.fireClock=0;
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        root.update(1,null); assertEquals(0x140,root.fireClock);
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(DezFinalFireball.class).size());
        int x=root.getX(),cam=f.camera().getX(); registry.restore(saved);
        var restored=GameServices.level().getObjectManager().activeObjectsOfType(DezFinalBossController.class).getFirst();
        restored.update(1,null); assertEquals(x,restored.getX()); assertEquals(cam,f.camera().getX());
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(DezFinalFireball.class).size());
    }
    @Test void fatalCallbackSkipsWalkingAndRetiresAfterQueueingBothHandoffModules() {
        var f=boot(); var root=root(); reachCore(root);
        int x=root.getX(),y=root.getY(),camera=f.camera().getX(); root.status|=0x80; root.update(0,null);
        assertEquals(0x80102,root.codePointer); assertEquals(x,root.getX()); assertEquals(y,root.getY());
        assertEquals(camera,f.camera().getX(),"fatal stack unwind skips moving-camera tail");
        int guard=0; while(!root.pendingDelete && guard++<600) root.update(guard,null);
        assertTrue(root.pendingDelete); assertEquals(0,state().windowBase()); assertEquals(0,state().screenShake().flag());
        assertEquals(0x30,root.control&0x30); assertFalse(root.isDestroyed());
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(DezFinalEscapeShip.class).size());
        var queue=TestEnvironment.objectServices().kosinskiModuleQueue();
        var addresses=queue.queuedArchives().stream().map(a->a.archiveAddress()).toList();
        assertTrue(addresses.contains(0x1607D8)); assertTrue(addresses.contains(0x182ED8));
        root.update(guard+1,null); assertTrue(root.isDestroyed());
    }
    @Test void battleAllocationsKeepIndependentCoreEmeraldAndHandPrefixesWithoutRetry() {
        for(int free=0;free<=4;free++) {
            boot(); var root=root(); root.update(0,null);
            for(int i=0;i<200 && root.routineForTest()!=6;i++) root.update(i,null);
            state().bossSignals(2); root.update(0,null);
            for(int i=0;i<192;i++) root.update(i,null);
            var m=GameServices.level().getObjectManager(); m.reserveAllButNFreeSlots(free);
            state().bossSignals(6); root.update(0,null);
            assertEquals(free>=1?1:0,m.activeObjectsOfType(DezFinalCore.class).size());
            assertEquals(free>=2?1:0,m.activeObjectsOfType(DezFinalEmerald.class).size());
            assertEquals(Math.max(0,free-2),m.activeObjectsOfType(DezFinalHand.class).size());
            m.releaseDynamicSlot(80); root.update(1,null);
            assertEquals(Math.max(0,free-2),m.activeObjectsOfType(DezFinalHand.class).size(),"no suffix repair on later walks");
        }
    }

}
