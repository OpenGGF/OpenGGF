package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.CharacterKey;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
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
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossTransport {
    private record Fixture(HeadlessTestFixture game,DezMinibossTransport transport) { }
    private Fixture boot() {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x140,(short)0x74C).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); game.stepIdleFrames(1);
        var transport=new DezMinibossTransport(); GameServices.level().getObjectManager().addDynamicObject(transport);
        NativePositionOps.writeXPosPreserveSubpixel(game.sprite(),0x140);
        NativePositionOps.writeYPosPreserveSubpixel(game.sprite(),0x74C);
        return new Fixture(game,transport);
    }
    private void start(Fixture f) {
        GameServices.gameState().setEndOfLevelActive(true); f.transport.update(0,null);
        assertEquals(1,f.transport.stateForTest());
        GameServices.gameState().setEndOfLevelActive(false); f.transport.update(1,null);
        assertEquals(3,f.transport.stateForTest());
    }
    @Test void resultsHandshakeStopsPrimaryAtActTwoCentreAndPublishesFloorAfterThirtyTwoPasses() {
        var f=boot(); var player=f.game.sprite(); f.transport.update(0,null); assertEquals(0,f.transport.stateForTest());
        player.setXSpeed((short)0x123); player.setYSpeed((short)-0x400); player.setGSpeed((short)0x456);
        start(f); assertTrue(player.isControlLocked()); assertEquals(0,player.getXSpeed()); assertEquals(0,player.getYSpeed()); assertEquals(0,player.getGSpeed());
        var manager=GameServices.level().getObjectManager();
        var explosions=manager.activeObjectsOfType(DezMinibossExplosionController.class);
        assertEquals(2,explosions.size()); assertEquals(List.of(0x100,0x180),explosions.stream().map(DezMinibossExplosionController::getX).toList());
        for(var explosion:explosions) assertEquals(0x760,explosion.getY());
        var state=(S3kDezZoneRuntimeState)TestEnvironment.objectServices().zoneRuntimeState();
        for(int i=0;i<31;i++) f.transport.update(i,null);
        assertEquals(0,state.eventsFg4()); f.transport.update(31,null); assertEquals(0xFF,state.eventsFg4());
        assertEquals(0,state.cameraStoredMinY()); assertEquals(0x2000,state.cameraStoredMaxY());
        assertEquals(2,manager.activeObjectsOfType(DezMinibossTransport.Bounds.class).size());
    }
    @Test void completeSpinLiftFlightAndReleaseUseNativeWordsAndAllocateTheDelayedTitle() {
        var f=boot(); start(f); var player=f.game.sprite();
        for(int i=0;i<32;i++) f.transport.update(i,null);
        player.setAir(false); f.transport.update(0,null); assertEquals(5,f.transport.stateForTest());
        assertTrue(player.isObjectControlled()); assertTrue(player.getAir());
        for(int i=0;i<95;i++) f.transport.update(i,null);
        assertEquals(5,f.transport.stateForTest()); f.transport.update(95,null); assertEquals(6,f.transport.stateForTest());
        assertFalse(player.isHighPriority()); assertEquals(0x74C,player.getCentreY());
        for(int i=0;i<37;i++) f.transport.update(i,null);
        assertEquals(7,f.transport.stateForTest()); assertEquals(0x4FC,player.getCentreY()); assertEquals(0x50C,f.transport.getY());
        int expectedY=0x50C<<16,velocity=-0x1000,passes=0;
        do {
            expectedY+=velocity<<8; velocity=(short)(velocity+0x38); passes++;
            f.transport.update(passes,null);
            if(velocity>=0 && (expectedY>>>16)>=0x3AC) expectedY=(0x3AC<<16)|(expectedY&0xFFFF);
            assertEquals(expectedY>>>16,player.getCentreY());
        } while(f.transport.stateForTest()!=9 && passes<200);
        assertTrue(passes<200); assertEquals(0x3AC,player.getCentreY()); assertEquals(0xBA,player.getMappingFrame());
        var owners=GameServices.level().getObjectManager().activeObjectsOfType(S3kTitleCardOwnerSlotObjectInstance.class);
        assertEquals(1,owners.size());
        for(int i=0;i<119;i++) f.transport.update(i,null);
        assertFalse(f.transport.isDestroyed()); f.transport.update(119,null);
        assertTrue(f.transport.isDestroyed()); assertFalse(player.isControlLocked()); assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectMappingFrameControl());
    }
    @Test void cameraWorkersReadLiveTargetsAndPreserveTheMaxTargetWord() {
        boot(); var services=TestEnvironment.objectServices(); var camera=GameServices.camera();
        var state=(S3kDezZoneRuntimeState)services.zoneRuntimeState();
        camera.setMinY((short)100); state.setCameraStoredMinY(99);
        camera.setMaxY((short)100); camera.setMaxYTarget((short)999); state.setCameraStoredMaxY(101);
        var min=new DezMinibossTransport.Bounds(true); min.setServices(services);
        var max=new DezMinibossTransport.Bounds(false); max.setServices(services);
        min.update(0,null); max.update(0,null); assertEquals(100,camera.getMinY()); assertEquals(100,camera.getMaxY());
        max.update(1,null); assertEquals(101,camera.getMaxY()); assertFalse(max.isDestroyed(),"equality retains max worker");
        max.update(2,null); assertTrue(max.isDestroyed()); assertEquals(999,camera.getMaxYTarget());
        min.update(1,null); min.update(2,null); assertFalse(min.isDestroyed()); min.update(3,null);
        assertTrue(min.isDestroyed()); assertEquals(99,camera.getMinY());
    }
    @Test void titleOwnerRequestsOnceOnItsOwnEntryAndRetiresWithoutSpawningSozController() {
        boot(); var manager=GameServices.level().getObjectManager();
        var owner=S3kTitleCardOwnerSlotObjectInstance.inLevel(11,1); manager.addDynamicObject(owner);
        var level=mock(com.openggf.level.LevelManager.class); owner.setServices(new TestObjectServices().withLevelManager(level));
        verifyNoInteractions(level); owner.update(0,null); owner.update(1,null);
        verify(level,times(1)).requestInLevelTitleCard(11,1,true,com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE);
        ((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).onTitleCardOwnerRetired();
        assertTrue(owner.isDestroyed()); assertTrue(manager.activeObjectsOfType(SozHyudoroControllerObjectInstance.class).isEmpty());
    }
    @Test void realManagerRecreatesTransportAndReplaysSpinAcrossLiftAndFlight() {
        var f=boot(); start(f); for(int i=0;i<32;i++)f.transport.update(i,null);
        f.game.sprite().setAir(false); f.transport.update(0,null);
        // Transport owns the player now; the floor event and free workers still run in the real sweep.
        f.game.stepIdleFrames(20);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(f.game,160); f.transport.setDestroyed(true); f.game.stepIdleFrames(1); registry.restore(saved);
        var restored=GameServices.level().getObjectManager().activeObjectsOfType(DezMinibossTransport.class).getFirst();
        assertNotSame(f.transport,restored); assertEquals(expected,rows(f.game,160));
    }
    private List<String> rows(HeadlessTestFixture game,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {
            game.stepIdleFrames(1); var p=game.sprite(); var camera=GameServices.camera();
            rows.add(p.getCentreX()+":"+p.getCentreY()+":"+p.getMappingFrame()+":"+p.isControlLocked()+":"+p.isObjectControlled()
                    +":"+camera.getMinY()+":"+camera.getMaxY()+":"+camera.getMaxYTarget());
        }
        return rows;
    }
}
