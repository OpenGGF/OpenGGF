package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
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
class TestDezMinibossEncounter {
    @org.junit.jupiter.api.AfterEach void resetViewport() {
        com.openggf.configuration.SonicConfigurationService.getInstance().clearSessionOverrides();
        com.openggf.game.session.SessionManager.clear();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320, 800})
    void romPlacementEntersTheEncounterThroughTheNativeFramedCameraAtBothWidths(int width) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,
                (width == 800 ? com.openggf.configuration.WidescreenAspect.SUPER_32_9
                        : com.openggf.configuration.WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var game = HeadlessTestFixture.builder().withZoneAndAct(11, 0)
                .startPosition((short) 0x3740, (short) 0x2E0).startPositionIsCentre().build();
        game.sprite().setRingCount(20);
        game.stepIdleFrames(200);
        var manager = GameServices.level().getObjectManager();
        var root = manager.activeObjectsOfType(DezMinibossInstance.class).getFirst();
        assertEquals(width, GameServices.camera().getWidth());
        assertEquals(0x7DE6E, root.codePointer, "production placement reaches first attack phase");
        assertEquals(0x3680, GameServices.camera().getMinX() & 65535);
        assertEquals(0x36C0, GameServices.camera().getMaxX() & 65535);
        assertEquals(8, manager.activeObjectsOfType(DezMinibossOrb.class).size());
        assertFalse(game.sprite().getDead());
    }

    private record Fixture(HeadlessTestFixture game,ObjectManager manager,DezMinibossInstance root) { }
    private Fixture boot() {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x3740,(short)0x3AC).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); game.stepIdleFrames(1);
        for (var object : List.copyOf(GameServices.level().getObjectManager().getActiveObjects()))
            if (object instanceof DezMinibossSprite bossPart) bossPart.setDestroyed(true);
        game.stepIdleFrames(1);
        var camera=GameServices.camera(); camera.setX((short)0x3680); camera.setY((short)0x28C);
        camera.setMinX((short)0x3680); camera.setMaxX((short)0x36C0);
        camera.setMinY((short)0x28C); camera.setMaxY((short)0x28C);
        var manager=GameServices.level().getObjectManager();
        var root=new DezMinibossInstance(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0));
        manager.addDynamicObject(root); return new Fixture(game,manager,root);
    }
    private DezMinibossEye eye(Fixture f) { return f.manager.activeObjectsOfType(DezMinibossEye.class).getFirst(); }
    private void enter(Fixture f) {
        for(int i=0;i<200 && f.root.codePointer!=0x7DE6E;i++) f.game.stepIdleFrames(1);
        assertEquals(0x7DE6E,f.root.codePointer); assertTrue(eye(f).publishesTouchResponseListEntryThisFrame());
    }
    private void hit(Fixture f) {
        var eye=eye(f);
        for(int i=0;i<33 && eye.getCollisionFlags()==0;i++) f.game.stepIdleFrames(1);
        assertEquals(0x17,eye.getCollisionFlags()); assertTrue(eye.publishesTouchResponseListEntryThisFrame());
        eye.onPlayerAttack(f.game.sprite(),null); f.game.stepIdleFrames(2);
    }
    private void phaseTwo(Fixture f) {
        enter(f); for(int i=0;i<8;i++)hit(f);
        for(int i=0;i<330 && f.root.codePointer!=0x7E0A6;i++) f.game.stepIdleFrames(1);
        assertEquals(0x7E0A6,f.root.codePointer); f.game.stepIdleFrames(2);
    }
    @Test void initialGateBuildsTenSlotsThenArmsTheEyeAfterTheNativeDescent() {
        var f=boot(); f.game.stepIdleFrames(1);
        assertEquals(0x7DE28,f.root.codePointer); assertFalse(f.root.visible);
        assertEquals(8,f.manager.activeObjectsOfType(DezMinibossOrb.class).size());
        assertEquals(1,f.manager.activeObjectsOfType(DezMinibossEye.class).size());
        assertEquals(10,f.manager.getActiveObjects().stream().filter(o->o instanceof DezMinibossSprite).count());
        assertFalse(eye(f).publishesTouchResponseListEntryThisFrame());
        assertTrue(((S3kDezZoneRuntimeState)TestEnvironment.objectServices().zoneRuntimeState()).bossFlag());
        f.game.stepIdleFrames(120); assertEquals(0x7DE28,f.root.codePointer);
        f.game.stepIdleFrames(1); assertEquals(0x7DE46,f.root.codePointer);
        f.game.stepIdleFrames(31); assertEquals(0xFC00,f.root.word3C); assertFalse(eye(f).publishesTouchResponseListEntryThisFrame());
        f.game.stepIdleFrames(1); assertEquals(0x7DE6E,f.root.codePointer); assertEquals(0,f.root.word3C);
        assertTrue(eye(f).publishesTouchResponseListEntryThisFrame());
        assertFalse((Object)f.root instanceof TouchResponseAttackable,"root is not a damage publisher");
    }
    @Test void eighthFirstPhaseHitMakesTwoControllersAndOpensTheFloorOnlyAfterItsWait() {
        var f=boot(); enter(f); for(int i=0;i<8;i++)hit(f);
        assertEquals(0x7DF8C,f.root.codePointer); assertEquals(0x40,f.root.status&0x40);
        assertEquals(2,f.manager.activeObjectsOfType(DezMinibossExplosionController.class).stream()
                .filter(o->o.getSpawn().subtype()==0xE).count());
        assertFalse(eye(f).publishesTouchResponseListEntryThisFrame());
        for(int i=0;i<127;i++) { f.game.stepIdleFrames(1); assertEquals(0,f.root.routineForTest()); }
        f.game.stepIdleFrames(1); assertEquals(2,f.root.routineForTest()); assertTrue(f.root.visible);
        assertEquals(2,f.manager.activeObjectsOfType(DezMinibossDebris.class).stream().filter(o->o.frame==0x11).count());
        f.game.stepIdleFrames(160); assertEquals(0x7E0A6,f.root.codePointer);
        assertEquals(0,f.root.collisionProperty); assertEquals(0,f.root.status&0x40);
        assertEquals(2,f.manager.activeObjectsOfType(DezMinibossArm.class).size());
        assertEquals(2,f.manager.activeObjectsOfType(DezMinibossArm.Spike.class).size());
        assertEquals(1,f.manager.activeObjectsOfType(DezMinibossInstance.Mask.class).size());
    }
    @Test void secondEightHitsAwardScoreAndReplaceTheRootSlotWithSignFlowWhileTransportSurvives() {
        var f=boot(); phaseTwo(f); int slot=f.root.getSlotIndex();
        int score=GameServices.gameState().getScore();
        for(int i=0;i<8;i++)hit(f);
        assertEquals(0x85668,f.root.codePointer); assertEquals(0x80,f.root.status&0x80);
        assertEquals(score+1000,GameServices.gameState().getScore());
        for(int i=0;i<64;i++)f.game.stepIdleFrames(1);
        assertTrue(f.root.isDestroyed()); assertEquals(1,f.manager.activeObjectsOfType(DezMinibossTransport.class).size());
        var signFlow=f.manager.activeObjectsOfType(S3kBossDefeatSignpostFlow.class).getFirst(); assertEquals(slot,signFlow.getSlotIndex());
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertTrue(f.manager.activeObjectsOfType(DezMinibossInstance.Mask.class).isEmpty());
        f.game.stepIdleFrames(1);
        assertEquals(1,f.manager.activeObjectsOfType(DezMinibossTransport.class).getFirst().stateForTest());
    }
    @Test void realManagerRecreatesTheCompletePhaseTwoGraphAndReplaysAttackAndArmCrossLinks() {
        var f=boot(); phaseTwo(f); hit(f); f.game.stepIdleFrames(80);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture(); var expected=rows(f,180);
        for(var o:List.copyOf(f.manager.getActiveObjects())) if(o instanceof DezMinibossSprite d)d.setDestroyed(true);
        f.game.stepIdleFrames(1); registry.restore(saved);
        var root=f.manager.activeObjectsOfType(DezMinibossInstance.class).getFirst(); assertNotSame(f.root,root);
        assertSame(root,eye(f).parentForTest());
        var arms=f.manager.activeObjectsOfType(DezMinibossArm.class);
        assertSame(root,arms.getFirst().parentForTest()); assertSame(arms.get(1),arms.getFirst().crossLinkForTest());
        assertSame(root,arms.get(1).parentForTest());
        assertEquals(expected,rows(f,180));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-32, 0, 32})
    void connectedDefeatCarriesTheInvisibleTransportAcrossTheActualReload(int approachOffset) {
        var f = boot(); phaseTwo(f);
        for (int i = 0; i < 8; i++) hit(f);
        f.game.sprite().setCentreXPreserveSubpixel((short) (0x3740 + approachOffset));
        f.game.sprite().setCentreYPreserveSubpixel((short) 0x32C);
        f.game.sprite().setDebugMode(false); f.game.sprite().setRingCount(20);
        for (int i = 0; i < 2000 && GameServices.level().getCurrentAct() == 0; i++) f.game.stepIdleFrames(1);
        assertEquals(1, GameServices.level().getCurrentAct(), "real sign/results publication and reload");
        var manager = GameServices.level().getObjectManager();
        var transport = manager.activeObjectsOfType(DezMinibossTransport.class).getFirst();
        assertFalse(transport.participatesInRomWorldTransitionOffset(), "native invisible SST render_flags is zero");
        assertFalse(f.game.sprite().getDead());
        for (int i = 0; i < 2000 && !transport.isDestroyed(); i++) f.game.stepIdleFrames(1);
        assertTrue(transport.isDestroyed(), "transport reaches landing/title and releases control");
        assertFalse(f.game.sprite().isControlLocked());
        assertFalse(f.game.sprite().getDead());
    }
    @Test void initialEyeAndOrbAllocationsKeepEveryAvailablePrefixWithoutRetry() {
        for (int free = 0; free <= 9; free++) {
            com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
            var f = boot();
            f.manager.reserveAllButNFreeSlots(free);
            f.game.stepIdleFrames(1);
            assertEquals(Math.min(1, free), f.manager.activeObjectsOfType(DezMinibossEye.class).size());
            assertEquals(Math.max(0, free - 1), f.manager.activeObjectsOfType(DezMinibossOrb.class).size());
            assertEquals(0x7DE28, f.root.codePointer);
            f.game.stepIdleFrames(3);
            assertEquals(Math.max(0, free - 1), f.manager.activeObjectsOfType(DezMinibossOrb.class).size());
        }
    }
    private List<String> rows(Fixture f,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {
            f.game.stepIdleFrames(1);
            rows.add(f.manager.getActiveObjects().stream().filter(o->o instanceof DezMinibossSprite)
                    .map(o->(DezMinibossSprite)o).map(o->o.getClass().getSimpleName()+":"+o.getSlotIndex()+":"+o.posX+":"+o.posY
                            +":"+o.word3A+":"+o.word3C+":"+o.control+":"+o.status+":"+o.frame+":"+o.visible+":"+o.collisionProperty)
                    .sorted().toList().toString());
        }
        return rows;
    }
}
