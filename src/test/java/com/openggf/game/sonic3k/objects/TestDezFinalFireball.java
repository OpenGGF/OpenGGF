package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalFireball {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true); return f;
    }
    private TestDezFinalHand.Root root(int y) {
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,y,0,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    private DezFinalFireball emitter(TestDezFinalHand.Root root) {
        var emitter=new DezFinalFireball(root); GameServices.level().getObjectManager().addDynamicObject(emitter); return emitter;
    }
    private List<DezFinalFireball.Fragment> flames() { return GameServices.level().getObjectManager().activeObjectsOfType(DezFinalFireball.Fragment.class); }
    @Test void airborneCadencePreservesTheShippedUndoubledTableIndex() {
        boot(); var emitter=emitter(root(0xAF)); emitter.update(0,null);
        assertEquals(0x5B0,emitter.getX()); assertEquals(0x97,emitter.getY()); assertTrue(flames().isEmpty());
        int[][] expected={{0x5B8,0x93},{0x5B4,0x97},{0x5BC,0x9F}};
        for(int i=0;i<3;i++) {
            emitter.update(i*2+1,null); assertEquals(i+1,flames().size());
            assertEquals(expected[i][0],emitter.getX()); assertEquals(expected[i][1],emitter.getY());
            var flame=flames().get(i); flame.update(0,null);
            assertEquals(emitter.getX(),flame.getX()); assertEquals(emitter.getY(),flame.getY());
            emitter.update(i*2+2,null); assertEquals(i+1,flames().size());
        }
    }
    @Test void floorTransitionCancelsYTrackingAndAlternatesBothFloorFlamesEveryThreePasses() {
        boot(); var root=root(0xE0); var emitter=emitter(root); emitter.update(0,null); // y=$C8
        emitter.update(1,null); assertEquals(0xCF,emitter.getY()); assertEquals(1,flames().size());
        flames().getFirst().update(0,null); assertTrue(flames().getFirst().flipX);
        root.writeY(0x100); emitter.update(2,null); emitter.update(3,null); assertEquals(1,flames().size());
        emitter.update(4,null); assertEquals(0xD7,emitter.getY()); assertEquals(2,flames().size());
        var right=flames().get(1); right.update(0,null); assertFalse(right.flipX); assertEquals(0xD7,right.getY());
        emitter.update(5,null); emitter.update(6,null); emitter.update(7,null);
        var left=flames().get(2); left.update(0,null); assertTrue(left.flipX); assertEquals(0xCF,left.getY());
        for(int i=0;i<230;i++) emitter.update(i,null);
        assertTrue(emitter.isDestroyed(),"offset $300 deletes immediately");
    }
    @Test void allThreeScriptsStartAtFrameSixAndKeepFloorYIndependentOfScriptDelta() {
        boot(); var root=root(0xAF); var manager=GameServices.level().getObjectManager();
        for(int subtype=0;subtype<3;subtype++) {
            var flame=new DezFinalFireball.Fragment(root.getSlotIndex(),0x5B0,0xCF,0xB0,-0x18,subtype);
            manager.addDynamicObject(flame); flame.update(0,null);
            assertEquals(6,flame.frame); assertEquals(0x8B,flame.getCollisionFlags()); assertEquals(0x10,flame.getShieldReactionFlags());
            flame.update(1,null); assertEquals(subtype==0?7:13,flame.frame);
            assertEquals(subtype==0?0x9B:0xCF,flame.getY()); assertEquals(subtype==0?0x5B0:0x5B4,flame.getX());
            for(int i=0;i<30;i++) flame.update(i,null);
            assertTrue(flame.isDestroyed());
        }
    }
    @Test void failedEmissionsStillAdvanceMovementAndDeleteTheEmitter() {
        boot(); var emitter=emitter(root(0xE0)); GameServices.level().getObjectManager().reserveAllButNFreeSlots(0);
        for(int i=0;i<230;i++) emitter.update(i,null);
        assertTrue(emitter.isDestroyed()); assertTrue(flames().isEmpty());
    }
    @Test void rootRetirementAndRewindPreserveSstAddressTracking() {
        var fixture=boot(); var root=root(0xAF); var emitter=emitter(root); emitter.update(0,null); emitter.update(1,null);
        var flame=flames().getFirst(); flame.update(0,null); root.setDestroyed(true); fixture.stepIdleFrames(1);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        emitter.update(4,null); flame.update(4,null); int ex=emitter.getX(),ey=emitter.getY(),fx=flame.getX(),fy=flame.getY();
        registry.restore(saved);
        var restored=GameServices.level().getObjectManager().activeObjectsOfType(DezFinalFireball.class).getFirst();
        var restoredFlame=flames().getFirst(); restored.update(4,null); restoredFlame.update(4,null);
        assertEquals(ex,restored.getX()); assertEquals(ey,restored.getY());
        assertEquals(fx,restoredFlame.getX()); assertEquals(fy,restoredFlame.getY());
        assertTrue(fx<0x500,"empty native root slot contributes zero X");
    }
}
