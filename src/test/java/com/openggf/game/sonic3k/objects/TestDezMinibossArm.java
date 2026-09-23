package com.openggf.game.sonic3k.objects;

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

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossArm {
    private static final class Parent extends DezMinibossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"ArmTestParent"); word3C=0x200; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int clock,PlayableEntity player) { }
    }
    private record Fixture(HeadlessTestFixture game,ObjectManager manager,Parent parent) { }
    private Fixture boot(int free) {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x3740,(short)0x2C0).startPositionIsCentre().build();
        game.sprite().setDebugMode(true);
        DezMinibossTestSupport.retirePlacedEncounter(game);
        var manager=GameServices.level().getObjectManager(); var parent=new Parent(); manager.addDynamicObject(parent);
        if(free>=0) manager.reserveAllButNFreeSlots(free);
        DezMinibossArm.spawnPair(parent); return new Fixture(game,manager,parent);
    }
    @Test void chainRewiresOnlyOnTheThirtySecondArmPassAndRetainsRootFractions() {
        var f=boot(-1); var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
        var first=arms.get(0); var second=arms.get(1);
        assertSame(f.parent,first.parentForTest()); assertSame(first,second.parentForTest());
        assertSame(f.parent,first.crossLinkForTest()); assertSame(f.parent,second.crossLinkForTest());
        f.game.stepIdleFrames(31); assertSame(first,second.parentForTest());
        assertEquals(0x3702,first.getX()); assertEquals(0x377E,second.getX());
        f.game.stepIdleFrames(1);
        assertSame(second,first.crossLinkForTest()); assertSame(f.parent,second.parentForTest());
        assertEquals(0x3700,first.getX()); assertEquals(0x3780,second.getX());
        f.parent.posX|=0x4567; f.parent.posY|=0x89AB;
        f.game.stepIdleFrames(1);
        assertEquals(8,f.parent.word3A,"normal rotation accelerates only in the first arm's slot");
        assertEquals(0xC008,first.word3C); assertEquals(0x4000,second.word3C);
        assertEquals(0x4567,first.posX&0xFFFF); assertEquals(0x89AB,first.posY&0xFFFF);
        assertEquals(0x4567,second.posX&0xFFFF); assertEquals(0x89AB,second.posY&0xFFFF);
        assertEquals(2,f.manager.activeObjectsOfType(DezMinibossArm.Spike.class).size());
    }
    @Test void everyPartialPrefixKeepsNativeLinksAndNeverHealsMissingArmsOrSpikes() {
        for(int free=0;free<=4;free++) {
            var f=boot(free); f.game.stepIdleFrames(32);
            var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
            assertEquals(Math.min(free,2),arms.size());
            assertEquals(Math.max(0,free-2),f.manager.activeObjectsOfType(DezMinibossArm.Spike.class).size());
            if(free==1) assertSame(f.parent,arms.getFirst().crossLinkForTest(),"missing second arm leaves $44=root");
            f.manager.releaseDynamicSlot(f.manager.getLastDynamicSlotExclusive()-1);
            f.game.stepIdleFrames(4);
            assertEquals(Math.min(free,2),f.manager.activeObjectsOfType(DezMinibossArm.class).size());
            assertEquals(Math.max(0,free-2),f.manager.activeObjectsOfType(DezMinibossArm.Spike.class).size());
        }
    }
    @Test void expansionAcceleratesBothSlotsAndHoldsAtNinetyFivePixelsBeforeContracting() {
        var f=boot(-1); f.game.stepIdleFrames(32); f.parent.control=0x80;
        var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
        f.game.stepIdleFrames(1); assertEquals(16,f.parent.word3A);
        assertEquals(0x4000,arms.getFirst().word3A);
        f.game.stepIdleFrames(32);
        for(var arm:arms) { assertEquals(0x5F00,arm.word3A); assertEquals(0x7E710,arm.stateForTest()); }
        f.game.stepIdleFrames(64);
        for(var arm:arms) { assertEquals(0x5F00,arm.word3A); assertEquals(0x7E724,arm.stateForTest()); }
        f.game.stepIdleFrames(31);
        assertEquals(0,f.parent.control&0x80);
        for(var arm:arms) assertEquals(0x4000,arm.word3A);
    }
    @Test void spikesAnimateAndBecomeHarmlessBeforeTheirParentsDelayedDeletion() {
        var f=boot(-1); f.game.stepIdleFrames(1);
        var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
        var spikes=f.manager.activeObjectsOfType(DezMinibossArm.Spike.class);
        for(var spike:spikes) { assertEquals(0xC,spike.frame); assertTrue(spike.publishesTouchResponseListEntryThisFrame()); }
        f.parent.status=0x80; f.game.stepIdleFrames(1);
        for(var spike:spikes) { assertTrue(spike.visible); assertFalse(spike.publishesTouchResponseListEntryThisFrame()); }
        for(var arm:arms) arm.writeY((GameServices.camera().getY()&0xFFFF)+0x190);
        f.game.stepIdleFrames(1);
        for(var arm:arms) { assertTrue(arm.pendingDelete); assertEquals(0x10,arm.control&0x10); }
        for(var spike:spikes) { assertTrue(spike.pendingDelete); assertFalse(spike.visible); }
        f.game.stepIdleFrames(1);
        for(var arm:arms) assertTrue(arm.isDestroyed());
        for(var spike:spikes) assertTrue(spike.isDestroyed());
    }
    @Test void managerRecreatesChainAndSettledCrossLinksAndReplaysExpansion() {
        for(int capturePass:new int[]{7,34,70}) {
            var f=boot(-1); f.game.stepIdleFrames(capturePass);
            f.parent.control=0x80;
            var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
            var expected=rows(f,140);
            for(var o:List.copyOf(f.manager.getActiveObjects())) if(o instanceof DezMinibossSprite d) d.setDestroyed(true);
            f.game.stepIdleFrames(1); registry.restore(saved);
            var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
            assertNotSame(f.parent,arms.getFirst().parentForTest());
            if(capturePass<32) assertSame(arms.getFirst(),arms.get(1).parentForTest());
            else { assertSame(arms.get(1),arms.getFirst().crossLinkForTest()); assertSame(arms.getFirst().parentForTest(),arms.get(1).parentForTest()); }
            for(var spike:f.manager.activeObjectsOfType(DezMinibossArm.Spike.class)) assertTrue(arms.contains(spike.parentForTest()));
            assertEquals(expected,rows(f,140));
        }
    }
    @Test void alignmentSignalsTheOtherArmAndTheJumpClearsTheRootsWaitBit() {
        for(int target:new int[]{0x200,0xFE00}) {
            var f=boot(-1); f.game.stepIdleFrames(32); f.parent.word3C=target; f.parent.control=8;
            var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
            int count=0;
            do { f.game.stepIdleFrames(1); } while((f.parent.control&8)!=0 && ++count<1024);
            assertTrue(count<1024,"alignment must terminate for either angular direction");
            assertEquals(0x4000,arms.getFirst().word3C);
            for(var arm:arms) assertEquals(0x7E64C,arm.stateForTest());
            f.parent.control|=4; f.game.stepIdleFrames(1);
            for(var arm:arms) assertEquals(0x7E668,arm.stateForTest());
            f.game.stepIdleFrames(8);
            for(var arm:arms) { assertEquals(0x7E67E,arm.stateForTest()); assertEquals(-0x400,arm.yVelocity); }
            f.game.stepIdleFrames(1);
            assertEquals(0,f.parent.control&4);
            for(var arm:arms) { assertEquals(0x7E6B6,arm.stateForTest()); assertEquals(f.parent.getY()+12,arm.getY()); }
        }
    }
    @Test void actualRiderIsCarriedThenReleasedAirborneOnTheArmDefeatPass() {
        var f=boot(-1); f.game.stepIdleFrames(32);
        var arm=f.manager.activeObjectsOfType(DezMinibossArm.class).getFirst();
        var player=f.game.sprite(); player.setDebugMode(false); player.setAir(true);
        player.setXSpeed((short)0); player.setGSpeed((short)0); player.setYSpeed((short)0x100);
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player,arm.getX());
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player,arm.getY()-8-player.getYRadius()-3);
        for(int i=0;i<8 && !f.manager.hasObjectStandingBit(player,arm);i++) f.game.stepIdleFrames(1);
        assertTrue(f.manager.hasObjectStandingBit(player,arm)); assertTrue(player.isOnObject());
        int dx=player.getCentreX()-arm.getX();
        f.game.stepIdleFrames(8); assertEquals(dx,player.getCentreX()-arm.getX());
        f.parent.status=0x80; f.game.stepIdleFrames(1);
        assertFalse(f.manager.hasObjectStandingBit(player,arm)); assertFalse(player.isOnObject()); assertTrue(player.getAir());
    }
    private List<String> rows(Fixture f,int count) {
        var result=new ArrayList<String>();
        for(int i=0;i<count;i++) {
            f.game.stepIdleFrames(1);
            result.add(f.manager.getActiveObjects().stream().filter(o->o instanceof DezMinibossSprite)
                    .map(o->(DezMinibossSprite)o).map(o->o.getSlotIndex()+":"+o.posX+":"+o.posY+":"+o.word3A+":"+o.word3C+":"+o.control+":"+o.frame+":"+o.visible)
                    .sorted().toList().toString());
        }
        return result;
    }
}
