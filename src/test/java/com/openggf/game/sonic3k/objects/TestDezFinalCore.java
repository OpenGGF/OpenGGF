package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
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
class TestDezFinalCore {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(23,0).build();
        fixture.sprite().setDebugMode(true); return fixture;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private TestDezFinalHand.Root root() {
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,0x98,0,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    private DezFinalCore core(TestDezFinalHand.Root root) {
        var core=new DezFinalCore(root); GameServices.level().getObjectManager().addDynamicObject(core); return core;
    }
    @Test void onlyFullyOpenMouthPublishesTouchAndClosingClearsItImmediately() {
        var fixture=boot(); var root=root(); var core=core(root);
        core.update(0,null); assertFalse(core.visible); assertEquals(8,core.getCollisionProperty());
        state().mouthStatus(1); core.update(1,null);
        assertTrue(core.visible); assertEquals(0x56C,core.getX()); assertEquals(0xA0,core.getY());
        core.onPlayerAttack(fixture.sprite(),null); assertEquals(8,core.getCollisionProperty());
        state().mouthStatus(0x80); core.update(2,null); assertEquals(0x16,core.getCollisionFlags());
        state().mouthStatus(1); core.update(3,null); assertEquals(0,core.getCollisionFlags());
        assertFalse(core.publishesTouchResponseListEntryThisFrame());
        state().mouthStatus(0); core.update(4,null); assertFalse(core.visible);
    }
    @Test void flashUsesFiveRomColorsForThirtyTwoPassesAndKicksOnlyTheCreditedPlayer() {
        for(boolean second:new boolean[]{false,true}) {
            boot(); var core=core(root()); var p1=mock(PlayableEntity.class); var p2=mock(PlayableEntity.class);
            var services=spy(TestEnvironment.objectServices());
            doReturn(new ObjectPlayerQuery(()->p1,()->List.of(p2))).when(services).playerQuery();
            var palette=mock(PaletteOwnershipRegistry.class); List<PaletteWrite> writes=new ArrayList<>();
            doAnswer(call->{writes.add(call.getArgument(0));return null;}).when(palette).submit(any());
            doReturn(palette).when(services).paletteOwnershipRegistryOrNull(); core.setServices(services);
            state().mouthStatus(0x80); core.update(0,null); core.onPlayerAttack(second?p2:p1,null);
            int[][] colors={{0xEEE,0xEEE,0xCCC,0xAAA,0x888},{0x888,0x666,0x444,0x222,0}};
            for(int pass=0;pass<32;pass++) {
                core.update(pass,null); assertEquals(pass==31?0x16:0,core.getCollisionFlags());
                var write=writes.get(pass); assertEquals(1,write.lineIndex()); assertEquals(10,write.startColor());
                byte[] expected=new byte[10];
                for(int i=0;i<5;i++) { expected[2*i]=(byte)(colors[pass&1][i]>>>8); expected[2*i+1]=(byte)colors[pass&1][i]; }
                assertArrayEquals(expected,write.segaData());
            }
            assertEquals(7,core.getCollisionProperty()); assertEquals(32,writes.size());
            verify(second?p2:p1).setXSpeed((short)0x600); verify(second?p2:p1).setGSpeed((short)0x600);
            verify(second?p2:p1).setYSpeed((short)-0x300); verify(second?p1:p2,never()).setXSpeed(anyShort());
        }
    }
    @Test void closingFlashDoesNotRestoreCollisionUntilTheNextOpenPublication() {
        var fixture=boot(); var core=core(root()); state().mouthStatus(0x80); core.update(0,null);
        core.onPlayerAttack(fixture.sprite(),null); core.update(1,null); state().mouthStatus(1);
        for(int pass=0;pass<31;pass++) core.update(pass,null);
        assertEquals(0,core.getCollisionFlags()); assertEquals(0,core.status&0x40);
        state().mouthStatus(0); core.update(32,null); state().mouthStatus(0x80); core.update(33,null);
        assertEquals(0x16,core.getCollisionFlags()); assertEquals(7,core.getCollisionProperty());
    }
    @Test void eighthHitStopsTimerAndAwardsOnceButOnlyRootControlFourRetiresCore() {
        var fixture=boot(); var root=root(); var core=core(root); state().mouthStatus(0x80); core.update(0,null);
        int score=GameServices.gameState().getScore();
        for(int hit=0;hit<8;hit++) {
            assertEquals(0x16,core.getCollisionFlags()); core.onPlayerAttack(fixture.sprite(),null);
            for(int pass=0;pass<(hit==7?1:32);pass++) core.update(pass,null);
        }
        assertEquals(0,core.getCollisionProperty()); assertEquals(0x80,root.status&0x80);
        assertEquals(score+1000,GameServices.gameState().getScore());
        assertTrue(TestEnvironment.objectServices().levelGamestate().isTimerPaused());
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(DezMinibossExplosionController.class).size());
        state().laserOffset(0x1234); core.update(33,null); assertEquals(0,state().laserOffset());
        assertTrue(core.visible); assertFalse(core.isDestroyed()); assertEquals(score+1000,GameServices.gameState().getScore());
        root.control|=0x10; core.update(34,null); assertFalse(core.isDestroyed());
        core.update(35,null); assertTrue(core.isDestroyed());
    }
    @Test void restoreRecreatesParentAndReplaysPendingHitAndMouthState() {
        var fixture=boot(); var root=root(); var core=core(root); state().mouthStatus(0x80); core.update(0,null);
        core.onPlayerAttack(fixture.sprite(),null);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        for(int pass=0;pass<32;pass++) core.update(pass,null);
        core.setDestroyed(true); root.setDestroyed(true); fixture.stepIdleFrames(1); state().mouthStatus(0);
        registry.restore(saved);
        var restored=GameServices.level().getObjectManager().activeObjectsOfType(DezFinalCore.class).getFirst();
        assertNotSame(core,restored); assertEquals(0x80,state().mouthStatus());
        assertEquals(7,restored.getCollisionProperty()); assertEquals(0,restored.getCollisionFlags());
        for(int pass=0;pass<32;pass++) restored.update(pass,null);
        assertEquals(0x16,restored.getCollisionFlags()); assertEquals(0x56C,restored.getX());
    }
}
