package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalShipDecoration {
    private TestDezFinalHand.Root root() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(23,0).build();
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,0x80,0,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    @Test void headTracksFlipsAndRawAnimationThenRetiresOnControlRatherThanDefeat() {
        var root=root(); var manager=GameServices.level().getObjectManager();
        var head=DezFinalShipDecoration.head(root); manager.addDynamicObject(head);
        head.update(0,null); assertTrue(head.visible); assertEquals(0,head.frame); assertEquals(0x64,head.getY());
        head.update(1,null); assertEquals(1,head.frame);
        for(int i=2;i<=6;i++) { head.update(i,null); assertEquals(1,head.frame); }
        head.update(7,null); assertEquals(0,head.frame);
        root.status=0x40; root.flipY=true; head.update(8,null);
        assertEquals(2,head.frame); assertEquals(0x9C,head.getY()); assertTrue(head.flipY);
        root.status=0x80; head.update(9,null); assertEquals(3,head.frame); assertTrue(head.visible);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        head.update(10,null); assertEquals(3,head.frame); assertFalse(head.isDestroyed());
        registry.restore(saved);
        var restored=manager.activeObjectsOfType(DezFinalShipDecoration.class).getFirst();
        var restoredRoot=manager.activeObjectsOfType(TestDezFinalHand.Root.class).getFirst();
        restoredRoot.control=0x10; restored.update(10,null);
        assertFalse(restored.isDestroyed()); assertFalse(restored.visible);
        restoredRoot.setDestroyed(true); var retiring=registry.capture();
        restored.update(11,null); assertTrue(restored.isDestroyed()); registry.restore(retiring);
        var retiringRestored=manager.activeObjectsOfType(DezFinalShipDecoration.class).getFirst();
        retiringRestored.update(11,null); assertTrue(retiringRestored.isDestroyed());
    }
    @Test void eggRoboQueuesArtOnceAndUsesRobotnikCadenceAfterDefeatClears() {
        var root=root(); var manager=GameServices.level().getObjectManager();
        var head=DezFinalShipDecoration.head(root); manager.addDynamicObject(head);
        var state=new com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState(com.openggf.game.PlayerCharacter.KNUCKLES);
        var services=spy(TestEnvironment.objectServices()); doReturn(state).when(services).zoneRuntimeState();
        var timing=new com.openggf.game.timing.HardwareTimingService();
        var coordinator=new com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator(timing);
        doReturn(timing).when(services).hardwareTiming(); doReturn(coordinator).when(services).runtimeArtCoordinator();
        head.setServices(services); head.update(0,null); assertEquals(1,state.art().pendingCount());
        head.update(1,null); assertEquals(1,head.frame);
        for(int i=2;i<=16;i++) { head.update(i,null); assertEquals(1,head.frame); }
        head.update(17,null); assertEquals(0,head.frame);
        root.status=0x80; head.update(18,null); assertEquals(3,head.frame);
        root.status=0;
        for(int i=19;i<=32;i++) head.update(i,null);
        head.update(33,null); assertEquals(1,head.frame);
        for(int i=34;i<=38;i++) { head.update(i,null); assertEquals(1,head.frame); }
        head.update(39,null); assertEquals(0,head.frame,"end routine selects five-tick Robotnik script");
        assertEquals(1,state.art().pendingCount(),"no repeated head upload");
    }
    @Test void flameUsesVIntParityAndParentMotionWithImmediateRetirement() {
        var root=root(); root.flipX=true; root.xVelocity=0x500;
        var flame=DezFinalShipDecoration.flame(root); GameServices.level().getObjectManager().addDynamicObject(flame);
        flame.update(0,null); assertFalse(flame.visible); assertEquals(6,flame.frame);
        flame.update(1,null); assertFalse(flame.visible); assertEquals(0x4E2,flame.getX());
        flame.update(2,null); assertTrue(flame.visible); assertEquals(5,flame.getPriorityBucket());
        root.xVelocity=0; flame.update(4,null); assertFalse(flame.visible);
        root.xVelocity=-0x100; flame.update(6,null); assertTrue(flame.visible);
        root.control=0x10; flame.update(8,null); assertTrue(flame.isDestroyed()); assertFalse(flame.visible);
    }
}
