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
class TestDezEndBossShield {
    private static final class Parent extends DezEndBossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3500,0x2A0,0xA7,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"ShieldTestParent"); control=8; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) { }
    }
    private record Fixture(HeadlessTestFixture game,ObjectManager manager,Parent parent,DezEndBossShield shield) { }
    private Fixture fixture() {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x3500,(short)0x320).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); DezEndBossTestSupport.retirePlacedEncounter(game);
        var manager=GameServices.level().getObjectManager(); var parent=new Parent(); manager.addDynamicObject(parent);
        var shield=new DezEndBossShield(parent); manager.addDynamicObject(shield);
        return new Fixture(game,manager,parent,shield);
    }
    @Test void visorInitializesInTheSameSweepAndNeverPublishesItsHeaderCollisionByte() {
        var f=fixture(); f.game.stepIdleFrames(1);
        var visor=f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).getFirst();
        assertEquals(0x16,f.shield.frame); assertEquals(0x13,visor.frame);
        assertEquals(f.shield.getY(),visor.getY()); assertSame(f.shield,visor.parentForTest());
        assertTrue(f.shield.publishesTouchResponseListEntryThisFrame());
        assertFalse(TouchResponseProvider.class.isInstance(visor));
        f.game.stepIdleFrames(1); assertEquals(0x17,f.shield.frame,"first animate pass skips the initial pair");
    }
    @Test void openingDisablesOnlyShieldTouchForSixteenTravelAndSevenHoldPasses() {
        var f=fixture(); f.game.stepIdleFrames(1); f.parent.control|=4;
        for(int pass=0;pass<16;pass++) {
            f.game.stepIdleFrames(1);
            assertEquals(2,f.shield.stateForTest());
            assertEquals(0x14+(pass+2)/2,f.shield.childDyForTest());
            assertFalse(f.shield.publishesTouchResponseListEntryThisFrame());
        }
        for(int pass=0;pass<7;pass++) {
            f.game.stepIdleFrames(1); assertEquals(3,f.shield.stateForTest());
            assertEquals(0x1C,f.shield.childDyForTest());
            assertFalse(f.shield.publishesTouchResponseListEntryThisFrame());
        }
        f.game.stepIdleFrames(1);
        assertEquals(4,f.shield.stateForTest()); assertEquals(0x1B,f.shield.childDyForTest());
        assertTrue(f.shield.publishesTouchResponseListEntryThisFrame());
        f.parent.control&=~8; f.game.stepIdleFrames(1);
        assertTrue(f.shield.pendingDelete); assertFalse(f.shield.visible);
        var visor=f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).getFirst();
        assertTrue(visor.pendingDelete); assertFalse(visor.visible);
        f.game.stepIdleFrames(1); assertTrue(f.shield.isDestroyed()); assertTrue(visor.isDestroyed());
    }
    @Test void visorAllocationFailureDoesNotSuppressTheShieldOrRetry() {
        var f=fixture(); f.manager.reserveAllButNFreeSlots(0); f.game.stepIdleFrames(1);
        assertEquals(1,f.shield.stateForTest()); assertTrue(f.shield.visible);
        assertTrue(f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).isEmpty());
        f.game.stepIdleFrames(40);
        assertTrue(f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).isEmpty());
    }
    @Test void rootDeathPublishesThroughShieldToVisorOnTheirOwnPasses() {
        var f=fixture(); f.game.stepIdleFrames(1);
        var visor=f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).getFirst();
        f.parent.status=0x80; f.game.stepIdleFrames(1);
        assertTrue(f.shield.pendingDelete); assertTrue(visor.pendingDelete);
        assertFalse(f.shield.publishesTouchResponseListEntryThisFrame()); assertFalse(visor.visible);
    }
    @Test void midOpeningGraphRecreationReplaysTheWholeChildChain() {
        var f=fixture(); f.game.stepIdleFrames(1); f.parent.control|=4; f.game.stepIdleFrames(9);
        var visor=f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).getFirst();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(f.game,f.shield,visor,40);
        f.shield.setDestroyed(true); visor.setDestroyed(true); f.parent.setDestroyed(true); f.game.stepIdleFrames(1);
        registry.restore(saved);
        var shield=f.manager.activeObjectsOfType(DezEndBossShield.class).getFirst();
        var restoredVisor=f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).getFirst();
        assertNotSame(f.shield,shield); assertNotSame(f.parent,shield.parentForTest());
        assertSame(shield,restoredVisor.parentForTest());
        assertEquals(expected,rows(f.game,shield,restoredVisor,40));
        assertEquals(1,f.manager.activeObjectsOfType(DezEndBossShield.class).size());
        assertEquals(1,f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).size());
    }
    private List<String> rows(HeadlessTestFixture game,DezEndBossShield shield,DezEndBossShield.Visor visor,int count) {
        List<String> result=new ArrayList<>();
        for(int i=0;i<count;i++) {
            game.stepIdleFrames(1); result.add(shield.getX()+":"+shield.getY()+":"+shield.frame+":"+shield.stateForTest()
                    +":"+shield.publishesTouchResponseListEntryThisFrame()+":"+visor.getX()+":"+visor.getY()+":"+visor.frame);
        }
        return result;
    }
}
