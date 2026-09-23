package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
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
class TestDezEndBossEncounter {
    @org.junit.jupiter.api.AfterEach void resetViewport() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }
    private record Fixture(HeadlessTestFixture game,ObjectManager manager,DezEndBossInstance root) { }
    private Fixture boot() { return boot(320); }
    private Fixture boot(int width) {
        configureWidth(width);
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x34B0,(short)0x320).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); DezEndBossTestSupport.retirePlacedEncounter(game);
        var camera=GameServices.camera(); camera.setX((short)(0x3400-framing(width))); camera.setY((short)0x218);
        camera.setMinX((short)0x3400); camera.setMaxX((short)0x34E0);
        camera.setMinY((short)0x218); camera.setMaxY((short)0x288);
        var manager=GameServices.level().getObjectManager();
        var root=new DezEndBossInstance(new ObjectSpawn(0x3510,0x200,0xA7,0,0,false,0));
        manager.addDynamicObject(root); return new Fixture(game,manager,root);
    }
    private static int framing(int width) {
        return com.openggf.camera.DeadzoneGeometry.rightEdge(width)-com.openggf.camera.DeadzoneGeometry.rightEdge(320);
    }
    private void configureWidth(int width) {
        var config=com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,
                (width==800?com.openggf.configuration.WidescreenAspect.SUPER_32_9:
                        com.openggf.configuration.WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }
    private void enter(Fixture f) {
        for(int i=0;i<200&&f.root.routineForTest()!=2;i++) f.game.stepIdleFrames(1);
        assertEquals(0x7F0DA,f.root.codePointer); assertEquals(2,f.root.routineForTest());
    }
    private void patrol(Fixture f) { enter(f); f.game.stepIdleFrames(192); assertEquals(4,f.root.routineForTest()); }
    private void sevenHits(Fixture f) {
        for(int i=0;i<7;i++) {
            assertEquals(DezEndBossDamageState.Contact.HIT,f.root.enemyContact(f.root.getX(),f.root.getY(),false,-1));
            f.game.stepIdleFrames(32);
        }
        assertEquals(1,f.root.healthForTest());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={320,800})
    void realPlacementReachesItsLaunchCycleAtBothWidths(int width) {
        configureWidth(width);
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x34B0,(short)0x300).startPositionIsCentre().build();
        game.sprite().setRingCount(200); game.stepIdleFrames(550);
        var manager=GameServices.level().getObjectManager();
        var root=manager.activeObjectsOfType(DezEndBossInstance.class).getFirst();
        assertEquals(0x7F0DA,root.codePointer); assertTrue(root.enemyCountForTest()>0);
        assertEquals(0x3400,GameServices.camera().getMinX()&65535);
        assertEquals(0x34E0,GameServices.camera().getMaxX()&65535);
        assertFalse(game.sprite().getDead());
    }
    @Test void cameraEntryThenNativeDescentCreatesTheBumperBeforeFirstAttack() {
        var f=boot(); f.game.stepIdleFrames(1);
        assertEquals(0x7F0CE,f.root.codePointer);
        assertEquals(2,f.manager.activeObjectsOfType(DezEndBossEscape.class).size());
        assertFalse(f.root.visible); enter(f);
        assertEquals(2,f.manager.activeObjectsOfType(DezEndBossBumper.class).size(),"fixture has native P1 and P2");
        assertFalse(TouchResponseAttackable.class.isInstance(f.root));
        assertFalse(TouchResponseProvider.class.isInstance(f.root),"root collision byte is zero");
        f.game.stepIdleFrames(191); assertEquals(2,f.root.routineForTest()); assertEquals(0x2BF,f.root.getY());
        f.game.stepIdleFrames(1); assertEquals(4,f.root.routineForTest()); assertEquals(0x2C0,f.root.getY());
        assertEquals(8,f.root.healthForTest());
    }
    @Test void firstAttackAllocatesShieldEnemyAndNestedVisorBeforeOpening() {
        var f=boot(); patrol(f); f.game.stepIdleFrames(180);
        assertEquals(6,f.root.routineForTest()); assertEquals(1,f.root.enemyCountForTest());
        var shield=f.manager.activeObjectsOfType(DezEndBossShield.class).getFirst();
        var enemy=f.manager.activeObjectsOfType(DezEndBossEnemy.class).getFirst();
        assertEquals(0x16,shield.frame); assertEquals(0x11,enemy.frame);
        assertEquals(1,f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).size());
        assertEquals(8,f.manager.getActiveObjects().stream().filter(o->o instanceof DezEndBossSprite).count(),
                "root, Robotnik, door, two bumpers, shield, enemy and visor");
        f.game.stepIdleFrames(8); assertEquals(6,f.root.routineForTest());
        f.game.stepIdleFrames(1); assertEquals(8,f.root.routineForTest());
        assertEquals(2,shield.stateForTest()); assertEquals(2,enemy.stateForTest());
    }
    @Test void killingHitInheritsAttackWaitAndGravityClearerWinsOnItsNextPass() {
        var f=boot(); patrol(f); sevenHits(f);
        for(int i=0;i<100&&f.root.routineForTest()!=4;i++) f.game.stepIdleFrames(1);
        assertEquals(4,f.root.routineForTest()); int before=f.root.timerForTest();
        f.root.enemyContact(f.root.getX(),f.root.getY(),false,-1); f.game.stepIdleFrames(1);
        assertEquals(0x85694,f.root.codePointer); assertEquals(before-1,f.root.timerForTest());
        assertEquals(0,f.root.healthForTest());
        assertEquals(1,f.manager.activeObjectsOfType(DezEndBossEscape.GravityClearer.class).size());
        GameServices.gameState().setReverseGravityActive(true); f.game.stepIdleFrames(1);
        assertFalse(GameServices.gameState().isReverseGravityActive());
        assertTrue((f.root.runtime().bossSignals()&1)!=0);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={320,800})
    void defeatPathsOpenDoorPublishForegroundChangeReleaseCameraAndRequestFinalAct(int width) {
        var f=boot(width); patrol(f); sevenHits(f);
        f.root.enemyContact(f.root.getX(),f.root.getY(),false,-1); f.game.stepIdleFrames(1);
        for(int i=0;i<700&&f.root.codePointer!=0x7F2DC;i++) f.game.stepIdleFrames(1);
        assertEquals(0x7F2DC,f.root.codePointer); assertFalse(f.root.runtime().bossFlag());
        assertEquals(8,f.root.runtime().foregroundRoutine(),"actual event consumes the door request");
        assertTrue((f.root.runtime().bossSignals()&2)!=0);
        assertEquals(0x3620,f.root.runtime().cameraStoredMaxX());
        for(int i=0;i<100&&(GameServices.camera().getMaxX()&65535)!=0x3620;i++) f.game.stepIdleFrames(1);
        assertEquals(0x3620,GameServices.camera().getMaxX()&65535);
        GameServices.camera().setX((short)(0x3620-framing(width))); f.root.update(0,null);
        assertEquals(0x7F2FE,f.root.codePointer); assertEquals(0x3660,GameServices.camera().getMaxX()&65535);
        NativePositionOps.writeXPosPreserveSubpixel(f.game.sprite(),0x377F); f.root.update(1,null);
        assertFalse(f.root.isDestroyed());
        NativePositionOps.writeXPosPreserveSubpixel(f.game.sprite(),0x3780); f.root.update(2,null);
        assertTrue(f.root.isDestroyed()); var transitions=GameServices.level().getTransitions();
        assertTrue(transitions.consumeZoneActRequest()); assertEquals(0x17,transitions.getRequestedZone());
        assertEquals(0,transitions.getRequestedAct());
    }
    @Test void widescreenExitWaitsForBoundaryWorkerWithoutAdvancingTheCameraItself() {
        var f=boot(800);
        f.root.codePointer=0x7F2DC;
        var camera=GameServices.camera();
        camera.setX((short)(0x3620-framing(800)));
        camera.setMaxX((short)0x361E);
        f.root.update(0,null);
        assertEquals(0x7F2DC,f.root.codePointer,"worker has not finished");
        assertEquals(0x3620-framing(800),camera.getMinX()&65535);
        assertEquals(0x3620-framing(800),camera.getX()&65535);
        camera.setMaxX((short)0x3620);
        f.root.update(1,null);
        assertEquals(0x7F2FE,f.root.codePointer);
        assertEquals(0x3660,camera.getMaxX()&65535);
    }

    @Test void entryAllocationPreservesEveryPrefixWithoutRetry() {
        for(int free=0;free<=2;free++) {
            var f=boot(); f.manager.reserveAllButNFreeSlots(free);
            f.game.stepIdleFrames(1);
            var children=f.manager.activeObjectsOfType(DezEndBossEscape.class);
            assertEquals(free,children.size());
            if(free>0) assertEquals(DezEndBossEscape.ROBOTNIK,children.getFirst().kindForTest());
            f.game.stepIdleFrames(2);
            assertEquals(free,f.manager.activeObjectsOfType(DezEndBossEscape.class).size());
        }
    }
    @Test void launchAllocationKeepsCounterWhenEnemySuffixFailsAndVisorIsIndependent() {
        for(int free=0;free<=3;free++) {
            var f=boot(); patrol(f); f.game.stepIdleFrames(179);
            f.manager.reserveAllButNFreeSlots(free); f.game.stepIdleFrames(1);
            assertEquals(1,f.root.enemyCountForTest(),"reservation precedes all allocations");
            assertEquals(free>0?1:0,f.manager.activeObjectsOfType(DezEndBossShield.class).size());
            assertEquals(free>1?1:0,f.manager.activeObjectsOfType(DezEndBossEnemy.class).size());
            assertEquals(free>2?1:0,f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).size());
            f.game.stepIdleFrames(2);
            assertEquals(free>1?1:0,f.manager.activeObjectsOfType(DezEndBossEnemy.class).size());
            assertEquals(free>2?1:0,f.manager.activeObjectsOfType(DezEndBossShield.Visor.class).size());
        }
    }
    @Test void landingPreservesEveryPathAndDebrisPrefixWithoutReconstruction() {
        for(int free=0;free<=9;free++) {
            var f=boot(); enter(f);
            f.root.codePointer=0x7F220; f.root.writeY(0x318); f.root.yVelocity=0;
            f.manager.reserveAllButNFreeSlots(free); f.game.stepIdleFrames(1);
            var children=f.manager.activeObjectsOfType(DezEndBossEscape.class);
            assertEquals(Math.min(free,2),children.stream().filter(o->o.kindForTest()==DezEndBossEscape.PATH).count());
            assertEquals(Math.min(Math.max(free-2,0),6),children.stream().filter(o->o.kindForTest()==DezEndBossEscape.DEBRIS).count());
            assertEquals(free==9?1:0,f.manager.activeObjectsOfType(DezMinibossExplosionController.class).size());
            f.game.stepIdleFrames(1);
            assertEquals(0x7F266,f.root.codePointer);
            assertEquals(Math.min(Math.max(free-2,0),6),f.manager.activeObjectsOfType(DezEndBossEscape.class)
                    .stream().filter(o->o.kindForTest()==DezEndBossEscape.DEBRIS).count());
        }
    }
    @Test void gravityClearerAllocationFailureIsNotHealed() {
        var f=boot(); patrol(f); sevenHits(f);
        f.manager.reserveAllButNFreeSlots(0);
        f.root.enemyContact(f.root.getX(),f.root.getY(),false,-1); f.game.stepIdleFrames(1);
        assertEquals(0,f.root.healthForTest());
        assertTrue(f.manager.activeObjectsOfType(DezEndBossEscape.GravityClearer.class).isEmpty());
        GameServices.gameState().setReverseGravityActive(true); f.game.stepIdleFrames(2);
        assertTrue(GameServices.gameState().isReverseGravityActive());
        assertTrue(f.manager.activeObjectsOfType(DezEndBossEscape.GravityClearer.class).isEmpty());
    }

    @Test void defeatGraphRecreatesAndReplaysThroughForegroundAndCameraPublication() {
        var f=boot(); patrol(f); sevenHits(f);
        f.root.enemyContact(f.root.getX(),f.root.getY(),false,-1);
        for(int i=0;i<500&&f.root.codePointer!=0x7F266;i++) f.game.stepIdleFrames(1);
        assertEquals(0x7F266,f.root.codePointer);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved=registry.capture(); var expected=defeatRows(f,200);
        for(var object:List.copyOf(f.manager.getActiveObjects()))
            if(object instanceof DezEndBossSprite part) part.setDestroyed(true);
        f.game.stepIdleFrames(1); registry.restore(saved);
        var restored=f.manager.activeObjectsOfType(DezEndBossInstance.class).getFirst();
        assertNotSame(f.root,restored);
        for(var child:f.manager.activeObjectsOfType(DezEndBossEscape.class)) assertSame(restored,child.parentForTest());
        assertEquals(expected,defeatRows(new Fixture(f.game,f.manager,restored),200));
    }
    private List<String> defeatRows(Fixture f,int frames) {
        var rows=new ArrayList<String>();
        for(int i=0;i<frames;i++) {
            f.game.stepIdleFrames(1);
            rows.add(f.root.codePointer+":"+f.root.timerForTest()+":"+f.root.runtime().bossSignals()
                    +":"+f.root.runtime().foregroundRoutine()+":"+GameServices.camera().getMaxX()
                    +":"+GameServices.gameState().isReverseGravityActive()+":"
                    +f.manager.getActiveObjects().stream().filter(o->o instanceof DezEndBossSprite)
                    .map(o->{var part=(DezEndBossSprite)o; return part.getClass().getSimpleName()+","+part.getSlotIndex()
                            +","+part.posX+","+part.posY+","+part.frame+","+part.visible+","+part.pendingDelete;}).toList());
        }
        return rows;
    }

    @Test void fullLiveAttackGraphRecreatesAndReplaysWithoutHealing() {
        var f=boot(); patrol(f); f.game.stepIdleFrames(200);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(f,120);
        for(var o:List.copyOf(f.manager.getActiveObjects())) if(o instanceof DezEndBossSprite part) part.setDestroyed(true);
        f.game.stepIdleFrames(1); registry.restore(saved);
        var restored=f.manager.activeObjectsOfType(DezEndBossInstance.class).getFirst(); assertNotSame(f.root,restored);
        for(var bumper:f.manager.activeObjectsOfType(DezEndBossBumper.class)) assertSame(restored,bumper.parentForTest());
        for(var enemy:f.manager.activeObjectsOfType(DezEndBossEnemy.class)) assertSame(restored,enemy.parentForTest());
        assertEquals(expected,rows(new Fixture(f.game,f.manager,restored),120));
    }
    private List<String> rows(Fixture f,int count) {
        List<String> rows=new ArrayList<>();
        for(int i=0;i<count;i++) {
            f.game.stepIdleFrames(1); rows.add(f.root.posX+":"+f.root.posY+":"+f.root.routineForTest()+":"+f.root.frame
                    +":"+f.root.healthForTest()+":"+f.manager.activeObjectsOfType(DezEndBossEnemy.class).stream()
                    .map(o->o.posX+","+o.posY+","+o.frame+","+o.stateForTest()).toList());
        }
        return rows;
    }
}
