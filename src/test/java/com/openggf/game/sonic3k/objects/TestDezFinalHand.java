package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalHand {
    static final class Root extends DezFinalBossSprite implements DezFinalHand.Owner, RewindRecreatable {
        int deaths;
        Root(ObjectSpawn spawn) { super(spawn, "TestFinalRoot"); }
        public int handControl() { return control; }
        public void handControl(int value) { control = value; }
        public void handDestroyed(int subtype) { deaths |= subtype == 0 ? 0xFF00 : 0xFF; }
        public void update(int clock, PlayableEntity player) { }
        public Root recreateForRewind(RewindRecreateContext context) { return new Root(context.spawn()); }
    }
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        // This fixture supplies its own Root and SST layout. Do not also enter the arena.
        ((com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState())
                .markScreenInitApplied();
        fixture.sprite().setDebugMode(true); return fixture;
    }
    private Root root() {
        var root = new Root(new ObjectSpawn(0x500, 0x98, 0, 0, 0, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    private List<DezFinalHand.Finger> fingers() { return GameServices.level().getObjectManager().activeObjectsOfType(DezFinalHand.Finger.class); }
    @Test void eachHandAllocatesExactlyTheAvailableForwardPrefixWithoutHealing() {
        for (int free = 0; free <= 3; free++) {
            boot(); var manager = GameServices.level().getObjectManager(); var root = root();
            var hand = new DezFinalHand(root, 0); manager.addDynamicObject(hand); manager.reserveAllButNFreeSlots(free);
            hand.update(0, null); assertEquals(free, fingers().size());
            for (int i = 0; i < 10; i++) hand.update(i, null);
            assertEquals(free, fingers().size());
        }
    }
    @Test void fingersUseTheRomOffsetsAndStayNoncollidableUntilTheOpenHand() {
        var fixture = boot(); var root = root(); var hand = new DezFinalHand(root, 0);
        GameServices.level().getObjectManager().addDynamicObject(hand); hand.update(0, null);
        for (var finger : fingers()) finger.update(0, null);
        var fingers = fingers();
        assertEquals(3, fingers.size());
        assertEquals(List.of(0x480, 0x460, 0x4A0), fingers.stream().map(DezFinalHand.Finger::getX).toList());
        assertTrue(fingers.stream().allMatch(f -> f.getY() == 0xD3 && f.getCollisionFlags() == 0));
        for (var finger : fingers) finger.onPlayerAttack(fixture.sprite(), null);
        assertTrue(fingers.stream().allMatch(f -> f.getCollisionProperty() == 3));
        root.control |= 2;
        for (int pass = 0; pass < 80 && fingers.getFirst().getCollisionFlags() == 0; pass++) {
            hand.update(pass, null); for (var finger : fingers) finger.update(pass, null);
        }
        assertTrue(fingers.stream().allMatch(f -> f.getCollisionFlags() == 0x1A && f.isHighPriority()));
        fingers.getFirst().onPlayerAttack(fixture.sprite(), null); fingers.getFirst().update(81, null);
        assertEquals(2, fingers.getFirst().getCollisionProperty());
        assertEquals(0, fingers.getFirst().getCollisionFlags());
        assertEquals(0x17, fingers.getFirst().frame);
    }
    @Test void threeHitsPerFingerPublishOneHandByteOnlyAfterAllThreeDie() {
        var fixture = boot(); var root = root(); var hand = new DezFinalHand(root, 2);
        GameServices.level().getObjectManager().addDynamicObject(hand); root.control = 2;
        hand.update(0, null); var fingers = fingers(); int hits = 0;
        for (int pass = 0; pass < 700 && root.deaths == 0; pass++) {
            if (root.control == 0) root.control = 2;
            hand.update(pass, null);
            for (var finger : fingers) {
                if (finger.isDestroyed()) continue;
                finger.update(pass, null);
                if (finger.getCollisionFlags() != 0 && finger.frame == 4) {
                    finger.onPlayerAttack(fixture.sprite(), null); hits++;
                }
            }
        }
        assertEquals(9, hits); assertEquals(0xFF, root.deaths);
        assertTrue(fingers.stream().allMatch(f -> f.getCollisionProperty() == 0));
        assertFalse(hand.isDestroyed(), "Go_Delete_Sprite retires next dispatch");
        hand.update(701, null); assertTrue(hand.isDestroyed());
    }
    @Test void dyingFingerReadsTheRetiredHandSlotAndSurvivesRewindWithoutItsParent() {
        var fixture = boot(); var root = root(); root.control = 2;
        var hand = new DezFinalHand(root,0); var manager = GameServices.level().getObjectManager();
        manager.addDynamicObject(hand); hand.update(0,null);
        var fingers = fingers();
        for (int pass=0; pass<700 && root.deaths==0; pass++) {
            if(root.control==0) root.control=2;
            hand.update(pass,null);
            for(var finger:fingers) {
                if(finger.isDestroyed()) continue;
                finger.update(pass,null);
                if(finger.getCollisionFlags()!=0 && finger.frame==4) finger.onPlayerAttack(fixture.sprite(),null);
            }
        }
        assertEquals(0xFF00,root.deaths);
        int retiredSlot=hand.getSlotIndex();
        hand.update(701,null); fixture.stepIdleFrames(1);
        var dying=fingers().stream().filter(f->!f.isDestroyed()).toList();
        assertFalse(dying.isEmpty(),"last finger's 32-update death delay outlives the hand");
        for(var finger:dying) assertEquals(0xFFF0,finger.getY(),"empty SST has cleared position words");
        var saved=TestEnvironment.activeGameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(40);
        TestEnvironment.activeGameplayMode().getRewindRegistry().restore(saved);
        var replacement=new Root(new ObjectSpawn(0x123,0x234,0,0,0,false,0)); manager.addDynamicObject(replacement);
        assertEquals(retiredSlot,replacement.getSlotIndex(),"allocator reuses the freed hand address");
        fixture.stepIdleFrames(1);
        assertTrue(fingers().stream().filter(f->!f.isDestroyed()).allMatch(f->f.getY()==0x224),
                "native dangling SST pointer follows the replacement position");
        fixture.stepIdleFrames(40);
        assertTrue(fingers().stream().noneMatch(f->!f.isDestroyed()),"death callback still retires after restore");
    }

    @Test void initializedHandAndFingersRecreateWithTheSameParentAndAttackPhase() {
        var fixture = boot(); var root = root(); root.control = 2;
        var hand = new DezFinalHand(root, 0); var manager = GameServices.level().getObjectManager(); manager.addDynamicObject(hand);
        hand.update(0, null); for (var finger : fingers()) finger.update(0, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        hand.setDestroyed(true); fingers().forEach(f -> f.setDestroyed(true)); root.setDestroyed(true);
        fixture.stepIdleFrames(1); registry.restore(saved);
        var restored = manager.activeObjectsOfType(DezFinalHand.class).getFirst();
        assertNotSame(hand, restored); assertEquals(3, fingers().size());
        for (int i = 0; i < 40; i++) { restored.update(i, null); for (var f : fingers()) f.update(i, null); }
        assertTrue(fingers().stream().allMatch(f -> f.getCollisionFlags() == 0x1A));
    }
}
