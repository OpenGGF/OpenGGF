package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezEndBossEnemy {
    private static final class Parent extends DezEndBossSprite implements RewindRecreatable,DezEndBossEnemy.Owner {
        private int remaining=1;
        private boolean contacts;
        private final DezEndBossDamageState damage=new DezEndBossDamageState();
        Parent() { this(new ObjectSpawn(0x3500,0x260,0xA7,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"EnemyTestParent"); control=8; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int clock,PlayableEntity player) { damage.update(services()); }
        @Override public DezEndBossDamageState.Contact enemyContact(int x,int y,boolean flipY,int velocity) {
            return contacts?damage.enemyContact(getX(),getY(),x,y,flipY,velocity):DezEndBossDamageState.Contact.NONE;
        }
        @Override public void enemyRetired() { remaining=(remaining-1)&255; }
    }
    private record Fixture(HeadlessTestFixture game,ObjectManager manager,Parent parent,DezEndBossEnemy enemy) { }
    private Fixture fixture(boolean reverse) {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x3500,(short)0x320).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); DezEndBossTestSupport.retirePlacedEncounter(game);
        var manager=GameServices.level().getObjectManager(); var parent=new Parent(); manager.addDynamicObject(parent);
        var enemy=new DezEndBossEnemy(parent); manager.addDynamicObject(enemy);
        GameServices.gameState().setReverseGravityActive(reverse);
        game.stepIdleFrames(1); parent.control|=4;
        return new Fixture(game,manager,parent,enemy);
    }
    private void land(Fixture f) {
        for(int i=0;i<300&&f.enemy.stateForTest()!=5;i++) f.game.stepIdleFrames(1);
        assertEquals(5,f.enemy.stateForTest(),"enemy must reach real arena terrain");
    }
    @Test void bothGravitySignsIntegrateAccelerationBeforeYDuringTheReleaseWait() {
        for(boolean reverse:new boolean[]{false,true}) {
            var f=fixture(reverse);
            for(int i=0;i<16;i++) f.enemy.update(i,null);
            int before=f.enemy.posY;
            f.enemy.update(16,null);
            assertEquals(3,f.enemy.stateForTest()); assertEquals(reverse?-0x38:0x38,f.enemy.yVelocity);
            assertEquals(before+((reverse?-0x38:0x38)<<8),f.enemy.posY);
            f.enemy.update(17,null);
            assertEquals(reverse?-0x70:0x70,f.enemy.yVelocity);
            assertEquals(before+((reverse?-0xA8:0xA8)<<8),f.enemy.posY);
        }
    }
    @Test void normalFloorEnemyReleasesWhenGravityChangesAndKeepsItsXVelocityForLanding() {
        var f=fixture(false); land(f); assertEquals(2,f.enemy.routineForTest());
        int velocity=f.enemy.xVelocity; assertEquals(0x80,Math.abs(velocity));
        GameServices.gameState().setReverseGravityActive(true); f.game.stepIdleFrames(1);
        assertEquals(4,f.enemy.routineForTest()); assertEquals(0,f.enemy.xVelocity); assertEquals(0,f.enemy.yVelocity);
        int before=f.enemy.posY; f.game.stepIdleFrames(1);
        assertEquals(before-(0x38<<8),f.enemy.posY);
        assertEquals(-0x38,f.enemy.yVelocity);
        for(int i=0;i<200&&f.enemy.routineForTest()==4;i++) f.game.stepIdleFrames(1);
        assertEquals(0,f.enemy.routineForTest(),"unflipped enemy hangs from the ceiling");
    }
    @Test void shippedFallingBranchKeepsTheEnemyUntilLandingAfterRootDeath() {
        var f=fixture(false); f.game.stepIdleFrames(18); assertEquals(3,f.enemy.stateForTest());
        f.parent.status=0x80; f.game.stepIdleFrames(1);
        assertFalse(f.enemy.pendingDelete); assertEquals(1,f.parent.remaining);
        land(f); f.game.stepIdleFrames(1);
        assertEquals(6,f.enemy.stateForTest()); assertEquals(0,f.parent.remaining);
    }
    @Test void ceilingRollingContactKicksThenFlipsAfterSevenOwnPasses() {
        var f=fixture(true); land(f); assertEquals(0,f.enemy.routineForTest());
        var player=f.game.sprite(); player.setAnimationId(2); player.setAir(false);
        player.setXSpeed((short)-0x100); player.setGSpeed((short)-0x700);
        f.enemy.update(0,null); f.enemy.orCollisionProperty(1); f.enemy.update(1,null);
        assertEquals(6,f.enemy.routineForTest()); assertEquals(-0x200,f.enemy.xVelocity); assertEquals(0x700,f.enemy.yVelocity);
        assertFalse(f.enemy.flipY);
        // Reverse gravity would pull the kicked enemy back into the ceiling; switch down,
        // as the arena's real pads can, to observe the whole seven-entry flip countdown.
        GameServices.gameState().setReverseGravityActive(false);
        for(int i=0;i<6;i++) { f.enemy.update(i,null); assertFalse(f.enemy.flipY); }
        f.enemy.update(6,null); assertTrue(f.enemy.flipY);
    }
    @Test void airborneRollDestroysCeilingEnemyWithoutTheThreeShotBurst() {
        var f=fixture(true); land(f); f.enemy.update(0,null);
        var player=f.game.sprite(); player.setAnimationId(2); player.setAir(true);
        f.enemy.orCollisionProperty(1); f.enemy.update(1,null);
        assertEquals(6,f.enemy.stateForTest()); assertEquals(0,f.parent.remaining);
        assertEquals(-0x300,player.getYSpeed()); assertTrue(player.getAir());
        for(int i=0;i<8;i++) f.enemy.update(i,null);
        assertTrue(f.enemy.pendingDelete); assertTrue(f.manager.activeObjectsOfType(DezEndBossEnemy.Shot.class).isEmpty());
    }
    @Test void timedBurstReparentsThreeChildrenBeforeTheirFirstMovingPassAndReplaysAfterRecreation() {
        var f=fixture(false); land(f);
        for(int i=0;i<650&&f.enemy.stateForTest()!=6;i++) f.game.stepIdleFrames(1);
        assertEquals(6,f.enemy.stateForTest()); assertEquals(0,f.parent.remaining);
        f.game.stepIdleFrames(8);
        var shots=f.manager.activeObjectsOfType(DezEndBossEnemy.Shot.class);
        assertEquals(3,shots.size());
        for(var shot:shots) { assertSame(f.parent,shot.parentForTest()); assertFalse(shot.visible); }
        assertEquals(List.of(-0x16A,0,0x16A),shots.stream().map(o->o.xVelocity).toList());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(f.game,shots,24);
        for(var shot:shots) shot.setDestroyed(true);
        f.parent.setDestroyed(true); f.game.stepIdleFrames(1); registry.restore(saved);
        var restored=f.manager.activeObjectsOfType(DezEndBossEnemy.Shot.class);
        assertEquals(3,restored.size()); assertNotSame(f.parent,restored.getFirst().parentForTest());
        assertEquals(expected,rows(f.game,restored,24));
    }
    @Test void realEnemyPublishesOneHitAndTheRootConsumesItOnItsNextPass() {
        var f=fixture(false); land(f);
        // Deliberately set the qualifying orientation for this short producer check.
        // The separate kick test drives the real seven-pass orientation change.
        f.enemy.flipY=true; f.parent.contacts=true;
        f.parent.writeX(f.enemy.getX()); f.parent.writeY(f.enemy.getY());
        f.enemy.update(0,null);
        assertEquals(6,f.enemy.stateForTest()); assertEquals(8,f.parent.damage.health());
        assertEquals(0,f.parent.remaining); f.parent.update(1,null);
        assertEquals(7,f.parent.damage.health());
    }
    @Test void everyThreeShotPrefixStopsAtTheFirstFailedForwardAllocation() {
        for(int free=0;free<=3;free++) {
            com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
            var f=fixture(false); land(f);
            for(int i=0;i<650&&f.enemy.stateForTest()!=6;i++) f.game.stepIdleFrames(1);
            assertEquals(6,f.enemy.stateForTest()); f.game.stepIdleFrames(7);
            f.manager.reserveAllButNFreeSlots(free); f.game.stepIdleFrames(1);
            var shots=f.manager.activeObjectsOfType(DezEndBossEnemy.Shot.class);
            assertEquals(free,shots.size(),"available prefix "+free);
            for(var shot:shots) { assertFalse(shot.visible); assertSame(f.parent,shot.parentForTest()); }
            f.game.stepIdleFrames(2);
            assertEquals(free,f.manager.activeObjectsOfType(DezEndBossEnemy.Shot.class).size());
        }
    }
    @Test void shotCullingDefersDeletionButRootDeathDeletesImmediately() {
        var f=fixture(false);
        var shot=new DezEndBossEnemy.Shot(f.enemy,0); f.manager.addDynamicObject(shot);
        shot.update(0,null); shot.writeX(0x1000); shot.update(1,null);
        assertTrue(shot.pendingDelete); assertFalse(shot.isDestroyed()); assertFalse(shot.visible);
        shot.update(2,null); assertTrue(shot.isDestroyed());
        var other=new DezEndBossEnemy.Shot(f.enemy,2); f.manager.addDynamicObject(other);
        other.update(0,null); f.parent.status=0x80; other.update(1,null);
        assertTrue(other.isDestroyed());
    }
    private List<String> rows(HeadlessTestFixture game,List<DezEndBossEnemy.Shot> shots,int count) {
        List<String> rows=new ArrayList<>();
        for(int i=0;i<count;i++) {
            game.stepIdleFrames(1);
            for(var shot:shots) rows.add(shot.getX()+":"+shot.getY()+":"+shot.frame+":"+shot.visible+":"+shot.isDestroyed());
        }
        return rows;
    }
}
