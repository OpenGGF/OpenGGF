package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;
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
class TestDezFinalEscapeShip {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true);
        f.camera().setX((short)0x600); f.camera().setY((short)0x20); return f;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private DezFinalEscapeShip ship() {
        var ship=new DezFinalEscapeShip(); GameServices.level().getObjectManager().addDynamicObject(ship); return ship;
    }
    private void enterChase(DezFinalEscapeShip ship) {
        ship.update(0,null);
        for(int i=0;i<0xF0;i++) ship.update(i,null);
    }
    @Test void initAndRiseUseCameraCopiesThenLiveYAndCreateSeparateChildTables() {
        var f=boot(); var ship=ship(); f.camera().setXAfterRenderCopy((short)0x700);
        ship.update(0,null); assertEquals(0x660,ship.getX()); assertEquals(0x160,ship.getY());
        assertTrue(ship.visible); assertEquals(0xF,ship.getCollisionFlags()); assertEquals(8,ship.getCollisionProperty());
        var m=GameServices.level().getObjectManager();
        assertEquals(1,m.activeObjectsOfType(DezFinalShipDecoration.class).size());
        assertEquals(1,m.activeObjectsOfType(DezFinalEscapeScenery.class).size());
        assertEquals(1,m.activeObjectsOfType(DezFinalEmerald.class).size());
        assertTrue(m.activeObjectsOfType(DezFinalEscapeScenery.class).getFirst().getSlotIndex()
                <m.activeObjectsOfType(DezFinalEmerald.class).getFirst().getSlotIndex());
        for(int i=0;i<0xEF;i++) ship.update(i,null);
        assertEquals(0x71,ship.getY()); assertEquals(0,ship.xVelocity);
        ship.update(0,null); assertEquals(0x70,ship.getY()); assertEquals(0x500,ship.xVelocity);
        assertEquals(0xC0,ship.yVelocity); assertEquals(2,m.activeObjectsOfType(DezFinalShipDecoration.class).size());
    }
    @Test void fractionalCameraOvershootAndSwingRestoreAtNativeBoundary() {
        var f=boot(); var ship=ship(); enterChase(ship);
        // Keep the ship below the transition threshold while measuring all acceleration passes.
        int fixed=0x600<<16;
        for(int pass=1;pass<=66;pass++) {
            ship.writeX(f.camera().getX()&0xFFFF); ship.update(pass,null);
            fixed+=Math.min(pass,65)*0x1000;
            assertEquals(fixed>>>16,f.camera().getX()&0xFFFF);
            assertEquals(fixed&0xFFFF,state().escapeCameraFraction());
            assertEquals(Math.min(pass,64)*0x1000,state().escapeCameraSpeed());
            assertEquals(f.camera().getX(),f.camera().getMinX()); assertEquals(f.camera().getX(),f.camera().getMaxX());
        }
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        ship.update(67,null); int x=f.camera().getX(),fraction=state().escapeCameraFraction(),y=ship.posY;
        registry.restore(saved); var restored=GameServices.level().getObjectManager().activeObjectsOfType(DezFinalEscapeShip.class).getFirst();
        restored.update(67,null); assertEquals(x,f.camera().getX()); assertEquals(fraction,state().escapeCameraFraction()); assertEquals(y,restored.posY);
    }
    @Test void shippedFlashRowsAndCreditedPlayerBrakeArePreserved() {
        for(boolean p2Hit:new boolean[]{false,true}) {
            boot(); var ship=ship(); ship.update(0,null); ship.control|=0x80;
            var p1=mock(PlayableEntity.class); var p2=mock(PlayableEntity.class);
            var services=spy(TestEnvironment.objectServices());
            doReturn(new ObjectPlayerQuery(()->p1,()->List.of(p2))).when(services).playerQuery();
            var palette=mock(PaletteOwnershipRegistry.class); List<PaletteWrite> writes=new ArrayList<>();
            doAnswer(call->{writes.add(call.getArgument(0)); return null;}).when(palette).submit(any());
            doReturn(palette).when(services).paletteOwnershipRegistryOrNull(); ship.setServices(services);
            ship.onPlayerAttack(p2Hit?p2:p1,null);
            for(int pass=0;pass<32;pass++) {
                ship.update(pass,null); int[] colors=pass%2==0?new int[]{0x222,0x888,0xCCC}:new int[]{8,0x866,0x222};
                for(int i=0;i<3;i++) {
                    var write=writes.get(pass*3+i); assertEquals(0,write.lineIndex()); assertEquals(new int[]{7,14,15}[i],write.startColor());
                    assertArrayEquals(new byte[]{(byte)(colors[i]>>>8),(byte)colors[i]},write.segaData());
                }
                assertEquals(pass==31?0xF:0,ship.getCollisionFlags());
            }
            verify(p2Hit?p2:p1).setXSpeed((short)0); verify(p2Hit?p2:p1).setGSpeed((short)0);
            verify(p2Hit?p1:p2,never()).setXSpeed(anyShort()); assertEquals(7,ship.getCollisionProperty());
        }
    }
    @Test void killingHitWaitsFortyEightThenSixteenPassesBeforeEmeraldRelease() {
        var f=boot(); var ship=ship(); ship.update(0,null); int score=GameServices.gameState().getScore();
        for(int hit=0;hit<8;hit++) {
            ship.onPlayerAttack(f.sprite(),null);
            for(int pass=0;pass<(hit==7?1:32);pass++) ship.update(pass,null);
        }
        assertEquals(0x8565E,ship.codePointer); assertEquals(score+1000,GameServices.gameState().getScore());
        assertEquals(0,ship.status&0x80,"native ship defeat does not set head's defeated bit");
        int x=ship.posX,y=ship.posY;
        for(int pass=0;pass<47;pass++) ship.update(pass,null);
        assertEquals(0,state().bossSignals()&8); ship.update(47,null); assertEquals(8,state().bossSignals()&8);
        for(int pass=0;pass<15;pass++) ship.update(pass,null);
        assertEquals(0,state().bossSignals()&16); ship.update(15,null); assertEquals(16,state().bossSignals()&16);
        assertEquals(0x802C0,ship.codePointer); assertEquals(x,ship.posX); assertEquals(y,ship.posY);
        assertEquals(score+1000,GameServices.gameState().getScore());
    }
    @Test void fadeUsesExactCompletionAndChoosesNativeCharacterEmeraldExit() {
        for(PlayerCharacter character:PlayerCharacter.values()) for(int emeralds:new int[]{0,7}) {
            boot(); var ship=ship(); var manager=GameServices.level().getObjectManager();
            var services=spy(TestEnvironment.objectServices());
            var state=new DezFinalBossZoneRuntimeState(character); doReturn(state).when(services).zoneRuntimeState();
            var game=spy(GameServices.gameState()); doReturn(emeralds).when(game).getEmeraldCount(); doReturn(game).when(services).gameState();
            doNothing().when(services).requestZoneAndAct(anyInt(),anyInt(),anyBoolean());
            doNothing().when(services).requestSessionSave(any()); ship.setServices(services);
            ship.codePointer=0x80382; ship.writeX(0x680); ship.update(0,null);
            var fade=manager.activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
            ship.update(1,null); verify(services,never()).requestSessionSave(any());
            for(int pass=0;pass<=28;pass++) fade.update(pass,null);
            ship.update(2,null); verify(services).requestSessionSave(any()); assertTrue(ship.pendingDelete);
            if(character==PlayerCharacter.KNUCKLES) assertEquals(com.openggf.game.GameOverExit.TITLE_SCREEN,GameServices.level().getGameOverExitRequested());
            else if(character==PlayerCharacter.TAILS_ALONE || emeralds==0) verify(services).requestZoneAndAct(0xD,1,true);
            else verify(services).requestZoneAndAct(0xC,0,true);
        }
    }
    @Test void independentAllocationTablesKeepTheirSuccessfulPrefixesWithoutHealing() {
        for(int free=0;free<=4;free++) {
            boot(); var ship=ship(); var manager=GameServices.level().getObjectManager();
            manager.reserveAllButNFreeSlots(free); ship.update(0,null);
            assertEquals(free>=1?1:0,manager.activeObjectsOfType(DezFinalArenaSignal.class).size());
            assertEquals(free>=2?1:0,manager.activeObjectsOfType(DezFinalShipDecoration.class).size());
            assertEquals(free>=3?1:0,manager.activeObjectsOfType(DezFinalEscapeScenery.class).size());
            assertEquals(free>=4?1:0,manager.activeObjectsOfType(DezFinalEmerald.class).size());
            manager.releaseDynamicSlot(80); ship.update(1,null);
            assertEquals(free>=4?1:0,manager.activeObjectsOfType(DezFinalEmerald.class).size(),"missing suffix does not heal");
        }
    }
    @Test void forcedFadeOverwritesAbsoluteSlot64WithoutLaterFreeingItsReplacement() {
        var f=boot(); var ship=ship(); var manager=GameServices.level().getObjectManager();
        var victim=new TestDezFinalHand.Root(new ObjectSpawn(0,0,0,0,0,false,0));
        manager.addDynamicObjectAtSlot(victim,64);
        // Real SST occupants survive rewind; the allocator-only pressure helper does not.
        while(manager.hasFreeDynamicSlot()) manager.createDynamicObject(
                ()->new TestDezFinalHand.Root(new ObjectSpawn(0,0,0,0,0,false,0)));
        ship.codePointer=0x80382; ship.writeX(0x680); ship.update(0,null);
        var fade=manager.activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
        assertEquals(64,fade.getSlotIndex()); assertTrue(victim.isDestroyed()); assertEquals(-1,victim.getSlotIndex());
        assertFalse(manager.hasFreeDynamicSlot());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        for(int pass=0;pass<=28;pass++) fade.update(pass,null);
        registry.restore(saved); var restored=manager.activeObjectsOfType(DezFinalEscapeShip.class).getFirst();
        var restoredFade=manager.activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
        assertEquals(64,restoredFade.getSlotIndex()); assertFalse(manager.hasFreeDynamicSlot());
        var services=spy(TestEnvironment.objectServices()); doNothing().when(services).requestZoneAndAct(anyInt(),anyInt(),anyBoolean());
        doNothing().when(services).requestSessionSave(any()); restored.setServices(services);
        restored.update(1,null); verify(services,never()).requestSessionSave(any());
        for(int pass=0;pass<=28;pass++) restoredFade.update(pass,null);
        restored.update(2,null); verify(services).requestSessionSave(any());
    }
    @Test void externalFadeDeletionDoesNotPublishNativeCompletion() {
        boot(); var ship=ship(); ship.codePointer=0x80382; ship.writeX(0x680); ship.update(0,null);
        var fade=GameServices.level().getObjectManager().activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
        fade.setDestroyed(true); var services=spy(TestEnvironment.objectServices()); ship.setServices(services);
        ship.update(1,null); verify(services,never()).requestSessionSave(any()); assertFalse(ship.pendingDelete);
    }
    @Test void restoredShipGraphRetainsAllChildrenAndParentsWithoutReconstructionSpawns() {
        var f=boot(); var ship=ship(); ship.update(0,null); var m=GameServices.level().getObjectManager();
        for(var child:m.activeObjectsOfType(DezFinalShipDecoration.class)) child.update(0,null);
        for(var child:m.activeObjectsOfType(DezFinalEscapeScenery.class)) child.update(0,null);
        for(var child:m.activeObjectsOfType(DezFinalEmerald.class)) child.update(0,null);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var before=m.getActiveObjects().stream().filter(o->o instanceof DezFinalBossSprite || o instanceof DezFinalArenaSignal).map(o->o.getClass().getSimpleName()).sorted().toList(); ship.writeX(0x900); ship.writeY(0x100);
        registry.restore(saved); assertEquals(before,m.getActiveObjects().stream().filter(o->o instanceof DezFinalBossSprite || o instanceof DezFinalArenaSignal).map(o->o.getClass().getSimpleName()).sorted().toList());
        var restored=m.activeObjectsOfType(DezFinalEscapeShip.class).getFirst(); assertNotSame(ship,restored);
        restored.writeX(0x800); restored.writeY(0x90);
        var head=m.activeObjectsOfType(DezFinalShipDecoration.class).getFirst(); head.update(1,null);
        var crane=m.activeObjectsOfType(DezFinalEscapeScenery.class).getFirst(); crane.update(1,null);
        var emerald=m.activeObjectsOfType(DezFinalEmerald.class).getFirst(); emerald.update(1,null);
        assertEquals(0x800,head.getX()); assertEquals(0x74,head.getY());
        assertEquals(0x800,crane.getX()); assertEquals(0xB3,crane.getY());
        assertEquals(0x800,emerald.getX()); assertEquals(0xCB,emerald.getY());
    }

    @Test void chaseDefeatRunsThroughSignalsExplosionSweepAndNativeFade() {
        var f=boot(); var ship=ship(); var m=GameServices.level().getObjectManager();
        var services=spy(TestEnvironment.objectServices()); doNothing().when(services).requestZoneAndAct(anyInt(),anyInt(),anyBoolean());
        doNothing().when(services).requestSessionSave(any()); ship.setServices(services);
        ship.update(0,null);
        int frame=1;
        for(;frame<1000 && (ship.control&0x80)==0;frame++) ship.update(frame,null);
        assertTrue(frame<1000,"native rise, chase and wait reach attack phase");
        for(int hit=0;hit<8;hit++) {
            ship.onPlayerAttack(f.sprite(),null);
            for(int pass=0;pass<(hit==7?1:32);pass++) ship.update(frame++,null);
        }
        assertEquals(0x8565E,ship.codePointer);
        for(int pass=0;pass<64;pass++) ship.update(frame++,null);
        assertEquals(0x802C0,ship.codePointer);
        int guard=0;
        while(ship.codePointer!=0x803D6 && guard++<500) ship.update(frame++,null);
        assertEquals(0x803D6,ship.codePointer); assertEquals(0x38,state().bossSignals()&0x38);
        assertEquals(5,m.activeObjectsOfType(DezMinibossExplosionController.class).size(),"defeat then four independent sweep workers");
        var fade=m.activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
        for(int pass=0;pass<=28;pass++) { fade.update(frame,null); ship.update(frame++,null); }
        verify(services).requestSessionSave(any()); verify(services).requestZoneAndAct(0xD,1,true);
        assertTrue(ship.pendingDelete); ship.update(frame,null); assertTrue(ship.isDestroyed());
    }

    @Test void realSweepExecutesBothParentedExplosionKindsAndRestoresTheirOwner() {
        var f=boot(); f.stepIdleFrames(1); var ship=ship();
        ship.codePointer=0x8030E;
        f.stepIdleFrames(1);
        var manager=GameServices.level().getObjectManager();
        var workers=manager.activeObjectsOfType(DezMinibossExplosionController.class);
        assertEquals(4,workers.size());
        for(var worker:workers) assertSame(ship,worker.parentForTest());
        assertEquals(2,manager.activeObjectsOfType(DezMinibossExplosionController.NormalExplosion.class).size());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved=registry.capture(); f.stepIdleFrames(4);
        int x=ship.getX();
        int normals=manager.activeObjectsOfType(DezMinibossExplosionController.NormalExplosion.class).size();
        registry.restore(saved); f.stepIdleFrames(4);
        var restored=manager.activeObjectsOfType(DezFinalEscapeShip.class).getFirst();
        assertEquals(x,restored.getX());
        assertEquals(normals,manager.activeObjectsOfType(DezMinibossExplosionController.NormalExplosion.class).size());
        for(var worker:manager.activeObjectsOfType(DezMinibossExplosionController.class))
            assertSame(restored,worker.parentForTest());
    }

    @Test void realObjectSweepExecutesForwardChildrenOnTheirAllocationPass() {
        var f=boot(); f.stepIdleFrames(1); var ship=ship(); f.stepIdleFrames(1);
        var m=GameServices.level().getObjectManager();
        var head=m.activeObjectsOfType(DezFinalShipDecoration.class).getFirst();
        var crane=m.activeObjectsOfType(DezFinalEscapeScenery.class).getFirst();
        assertTrue(head.visible); assertEquals(0,head.frame,"first raw frame waits for next dispatch");
        assertTrue(crane.visible); assertEquals(0x14,crane.getOnScreenHalfHeight());
        assertEquals(ship.getX(),head.getX()); assertEquals(ship.getY()-0x1C,head.getY());
        assertEquals(1,m.activeObjectsOfType(DezFinalEmerald.class).size());
    }

}
