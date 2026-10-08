package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
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
class TestDezFinalMouthSequence {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true); return f;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private TestDezFinalHand.Root root() {
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,0x98,0,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    @Test void buttonUsesForwardAllocationAndPublishesBeforeMouthInitialization() {
        var f=boot(); var root=root(); var button=new DezFinalMouth.Button(root);
        var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(button);
        button.update(0,null); assertEquals(8,button.getCollisionFlags());
        assertEquals(0x5A0,button.getX()); assertEquals(0x64,button.getY());
        button.onPlayerAttack(f.sprite(),null); button.update(1,null);
        assertEquals(4,root.control&4); assertEquals(0xFF00,state().eventsFg5());
        assertEquals(0,state().mouthStatus()); assertEquals(0xFF,button.getCollisionProperty());
        var mouth=manager.activeObjectsOfType(DezFinalMouth.class).getFirst();
        assertTrue(mouth.getSlotIndex()>button.getSlotIndex());
        mouth.update(1,null); assertEquals(1,state().mouthStatus()); assertEquals(0xC4,mouth.getY());
    }
    @Test void allocationFailureLeavesButtonLatchedWithoutRetry() {
        var f=boot(); var root=root(); var button=new DezFinalMouth.Button(root);
        var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(button);
        manager.reserveAllButNFreeSlots(0); button.update(0,null); button.onPlayerAttack(f.sprite(),null);
        for(int i=0;i<80;i++) button.update(i,null);
        assertEquals(4,root.control&4); assertEquals(0,button.getCollisionFlags());
        assertTrue(manager.activeObjectsOfType(DezFinalMouth.class).isEmpty());
    }
    @Test void missingBeamLeavesMouthOpenAndInvisibleAfterSixteenOpeningPasses() {
        boot(); var root=root(); var mouth=new DezFinalMouth(root);
        var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(mouth); manager.reserveAllButNFreeSlots(0);
        for(int i=0;i<15;i++) { mouth.update(i,null); assertEquals(1,state().mouthStatus()); }
        mouth.update(15,null); assertEquals(0x80,state().mouthStatus()); assertEquals(0x100,mouth.getY());
        assertTrue(mouth.visible); assertTrue(manager.activeObjectsOfType(DezFinalBeam.class).isEmpty());
        for(int i=0;i<200;i++) mouth.update(i,null);
        assertEquals(0x80,state().mouthStatus()); assertFalse(mouth.visible); assertFalse(mouth.isDestroyed());
    }
    @Test void beamPublishesRomLaserFramesThenReleasesMouthAfterNativeWait() {
        boot(); var root=root(); root.control=4; root.fireClock=0x123;
        var mouth=new DezFinalMouth(root); var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(mouth);
        for(int i=0;i<16;i++) mouth.update(i,null);
        var beam=manager.activeObjectsOfType(DezFinalBeam.class).getFirst();
        List<Integer> changes=new ArrayList<>(); int prior=0; int laserEnded=-1, released=-1;
        for(int i=0;i<400;i++) {
            beam.update(i,null); int current=state().laserOffset();
            if(current!=prior) { changes.add(current); prior=current; if(current==0) laserEnded=i; }
            if(beam.isDestroyed()) { released=i; break; }
        }
        assertEquals(List.of(6,14,22,30,22,14,6,0),changes);
        // The final zero frame lasts one update; its F4 callback immediately
        // consumes the first of the following 96 wait passes ($5F through -1).
        assertEquals(96,released-laserEnded); assertEquals(0,mouth.control&4);
        for(int i=0;i<15;i++) { mouth.update(i,null); assertEquals(1,state().mouthStatus()); }
        mouth.update(16,null); assertEquals(0,state().mouthStatus()); assertEquals(0,root.control&4);
        assertEquals(0,root.fireClock); assertTrue(mouth.visible); assertFalse(mouth.isDestroyed());
        mouth.update(17,null); assertTrue(mouth.isDestroyed());
    }
    @Test void finalChargeHoldUsesEvenDispatchRegisterAndAllocationFailureChangesEarlierFlash() {
        boot(); var root=root(); var mouth=new DezFinalMouth(root); var manager=GameServices.level().getObjectManager();
        manager.addDynamicObject(mouth); for(int i=0;i<16;i++) mouth.update(i,null);
        var beam=manager.activeObjectsOfType(DezFinalBeam.class).getFirst(); manager.reserveAllButNFreeSlots(0);
        List<Integer> frames=new ArrayList<>();
        for(int i=0;i<250;i++) { beam.update(0,null); if(!beam.visible) break; frames.add(beam.frame); }
        assertTrue(frames.size()>120);
        assertTrue(frames.subList(frames.size()-40,frames.size()).stream().allMatch(frame->frame==0x1F));
        assertEquals(0x1A,frames.get(frames.size()-41));
    }
    @Test void activeMouthBeamGraphRecreatesAndFinishesTheSameRelease() {
        var fixture=boot(); var root=root(); root.control=4;
        var mouth=new DezFinalMouth(root); var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(mouth);
        for(int i=0;i<16;i++) mouth.update(i,null);
        var beam=manager.activeObjectsOfType(DezFinalBeam.class).getFirst();
        for(int i=0;i<80;i++) beam.update(i,null);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        int expected=finish(beam,mouth); registry.restore(saved);
        var restored=manager.activeObjectsOfType(DezFinalBeam.class).getFirst();
        var restoredMouth=manager.activeObjectsOfType(DezFinalMouth.class).getFirst();
        assertEquals(expected,finish(restored,restoredMouth)); assertEquals(0,state().mouthStatus());
        assertEquals(0,manager.activeObjectsOfType(TestDezFinalHand.Root.class).getFirst().control&4);
    }
    @Test void laserUsesHalfOpenNativeCenterRangeForBothPlayerSlots() {
        int[][] edges={{0,-32,1},{287,31,1},{-1,0,0},{288,0,0},{0,-33,0},{0,32,0}};
        for(int[] edge:edges) for(boolean second:new boolean[]{false,true}) {
            boot(); var root=root(); var mouth=new DezFinalMouth(root);
            var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(mouth);
            for(int i=0;i<16;i++) mouth.update(i,null);
            var beam=manager.activeObjectsOfType(DezFinalBeam.class).getFirst();
            var p1=mock(com.openggf.game.PlayableEntity.class); var p2=mock(com.openggf.game.PlayableEntity.class);
            var target=second?p2:p1;
            when(target.getCentreX()).thenReturn((short)(0x58C+edge[0])); when(target.getCentreY()).thenReturn((short)(0xA0+edge[1]));
            when(target.getRingCount()).thenReturn(1); when(target.hasShield()).thenReturn(true);
            var services=spy(TestEnvironment.objectServices());
            doReturn(new com.openggf.level.objects.ObjectPlayerQuery(()->p1,()->List.of(p2))).when(services).playerQuery();
            beam.setServices(services);
            for(int i=0;i<250 && beam.frame!=30;i++) beam.update(i,null);
            assertEquals(30,beam.frame,"laser reached the sole damaging frame");
            verify(target,times(edge[2])).applyHurtOrDeath(0x58C,com.openggf.game.DamageCause.NORMAL,true);
            verify(second?p1:p2,never()).applyHurtOrDeath(anyInt(),any(),anyBoolean());
        }
    }
    @Test void mouthArtUsesLevelTileOneRatherThanTheMiscSpriteBase() {
        var plan=com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.getPlan(23,0);
        var mouth=plan.levelArt().stream().filter(e->e.key().equals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.DEZ_FINAL_MOUTH)).findFirst().orElseThrow();
        assertEquals(1,mouth.artTileBase()); assertEquals(0x187888,mouth.mappingAddr());
    }
    @Test void particleSurvivesParentRetirementAndRestoresItsMotion() {
        var fixture=boot(); var root=root(); var mouth=new DezFinalMouth(root);
        var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(mouth);
        for(int i=0;i<16;i++) mouth.update(i,null);
        var beam=manager.activeObjectsOfType(DezFinalBeam.class).getFirst(); beam.update(0,null);
        var particle=manager.activeObjectsOfType(DezFinalBeam.Particle.class).getFirst(); particle.update(0,null);
        root.status|=0x80; beam.update(1,null); beam.update(2,null);
        fixture.stepIdleFrames(1);
        assertTrue(beam.isDestroyed()); assertFalse(particle.isDestroyed());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        particle.update(3,null); int x=particle.getX(),y=particle.getY();
        registry.restore(saved);
        var restored=manager.activeObjectsOfType(DezFinalBeam.Particle.class).getFirst(); restored.update(3,null);
        assertEquals(x,restored.getX()); assertEquals(y,restored.getY());
        for(int i=0;i<30;i++) restored.update(i,null);
        assertTrue(restored.isDestroyed());
    }
    private int finish(DezFinalBeam beam,DezFinalMouth mouth) {
        int i=0;
        for(;i<400 && !beam.isDestroyed();i++) beam.update(i,null);
        for(int j=0;j<17;j++) mouth.update(j,null);
        assertTrue(beam.isDestroyed()); assertTrue(mouth.isDestroyed()); return i;
    }
}
