package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzBossPlatformObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBossPlatformsHeadless {
    @AfterEach void reset() { SessionManager.clear(); TestEnvironment.activeGameplayMode(); }
    private List<LrzBossPlatformObjectInstance> platforms() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o instanceof LrzBossPlatformObjectInstance)
                .map(o->(LrzBossPlatformObjectInstance)o).toList();
    }
    private void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        for(String key:expected.entries().keySet()) assertTrue(
                RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->key+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
    @Test void checkpointRouteCreatesPlatformGraphAndRestoresRemovedParentsAndUndersides() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle()
                .startPosition((short)0x9C0,(short)0x368).startPositionIsCentre().build();
        var renderer=GameServices.level().getObjectRenderManager().getRenderer(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.LRZ3_PLATFORM);
        assertNotNull(renderer,"boss-act platform art must be registered outside the HPZ cutscene group");
        assertTrue(renderer.isReady());
        for(int i=0;i<110;i++) fixture.stepFrame(false,false,false,false,false);
        var dynamic=platforms().stream().filter(p->p.getSpawn().objectId()==0).toList();
        assertTrue(dynamic.size()>=4,"two rising/falling platforms plus their undersides");
        assertTrue(dynamic.stream().anyMatch(p->p.getRomCodePointer()==0x79E7C));
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,false,false); var after=registry.capture();
        dynamic.forEach(GameServices.level().getObjectManager()::removeDynamicObject);
        registry.restore(before); same(before,registry.capture());
        assertEquals(dynamic.size(),platforms().stream().filter(p->p.getSpawn().objectId()==0).count());
        fixture.stepFrame(false,false,false,false,false); same(after,registry.capture());
    }
    @Test void unpositionedBossRemainsAliveWhileWaitingForTheEntryPlatform() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle()
                .startPosition((short)0x9C0,(short)0x368).startPositionIsCentre().build();
        var manager=GameServices.level().getObjectManager();
        var boss=manager.createDynamicObject(com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance::new);
        for(int i=0;i<3;i++) fixture.stepFrame(false,false,false,false,false);
        assertTrue(manager.getActiveObjects().contains(boss),
                "Obj_LRZEndBoss waits at uninitialized coordinates without a range-delete tail");
        assertEquals(0,boss.getX()); assertEquals(0,boss.getY());
        assertEquals(boss.getSlotIndex(),com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                .currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct().bossSlot());
    }

    @Test void bossCreatesItsPersistentChildrenAndRestoresTheirParentGraph() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle()
                .startPosition((short)0x9C0,(short)0x368).startPositionIsCentre().build();
        var manager=GameServices.level().getObjectManager();
        manager.createDynamicObject(com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance::new);
        fixture.stepFrame(false,false,false,false,false);
        com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().bossAct().publishEntryPlatformReady();
        for(int i=0;i<124;i++) fixture.stepFrame(false,false,false,false,false);
        var children=manager.getActiveObjects().stream()
                .filter(o->o instanceof com.openggf.game.sonic3k.objects.LrzEndBossChild).toList();
        assertEquals(2,children.size(),"ChildObjDat_7A18C crest and pilot");
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,false,false); var after=registry.capture();
        manager.getActiveObjects().stream().filter(o->o instanceof com.openggf.game.sonic3k.objects.LrzEndBossChild
                || o instanceof com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance)
                .toList().forEach(manager::removeDynamicObject);
        registry.restore(before); same(before,registry.capture());
        fixture.stepFrame(false,false,false,false,false); same(after,registry.capture());
    }

    @Test void nativeMaskFramesCoverTheLavaHorizonAtTheirOwnPriority() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle().build();
        assertTrue(new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider().useSpriteSatMasking(22));
        var graphics=org.mockito.Mockito.mock(com.openggf.graphics.GraphicsManager.class);
        org.mockito.Mockito.when(graphics.isSpriteSatCollectionActive()).thenReturn(true);
        var rom=com.openggf.data.RomManager.getInstance().getRom();
        // LRZ's $84 mask covers 64 lines, whereas SOZ's $40 covers 32.
        com.openggf.game.sonic3k.S3kSpriteMaskSupport.submitFrame(graphics,rom,8,0,0x3C0);
        org.mockito.Mockito.verify(graphics).submitSpriteSatControlEntry(8,0x3A0,1,4,0x7C0);
        org.mockito.Mockito.verify(graphics).submitSpriteSatControlEntry(0,0x3A0,1,4,0);
        org.mockito.Mockito.verify(graphics).submitSpriteSatControlEntry(8,0x3C0,1,4,0x7C0);
        org.mockito.Mockito.verify(graphics).submitSpriteSatControlEntry(0,0x3C0,1,4,0);
        org.mockito.Mockito.verify(graphics).requestSpriteMask();
        org.mockito.Mockito.verify(graphics).isSpriteSatCollectionActive();
        org.mockito.Mockito.verifyNoMoreInteractions(graphics);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,16,17})
    void topSolidUsesD3AndTheNativeStrictOverlapWindow(int penetration) {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle().build();
        var manager=GameServices.level().getObjectManager();
        var player=fixture.sprite(); player.giveShield(com.openggf.game.ShieldType.FIRE);
        var platform=manager.createDynamicObject(()->new LrzBossPlatformObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(0xB00,0x500,0xAD,3,0,false,0)));
        var lava=manager.getActiveObjects().stream().filter(
                o->o instanceof com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance).findFirst().orElseThrow();
        for(var object:List.of(platform,lava)) {
            object.snapshotPreUpdatePosition();
            int plane=object.getY()-(object==platform ? 0xD : 0x30);
            player.setCentreX((short)object.getX());
            player.setCentreY((short)(plane-player.getYRadius()-4+penetration));
            player.setAir(true); player.setYSpeed((short)0x100);
            manager.processImmediateInlineSolidCheckpoint(object,player,List.of());
            boolean accepted=penetration>=1 && penetration<=16;
            assertEquals(accepted,manager.isRidingObject(player,object),"penetration="+penetration+" object="+object+" air="+player.getAir()+" controlled="+player.isObjectControlled()+" debug="+player.isDebugMode()+" dead="+player.getDead()+" y="+player.getCentreY()+" radius="+player.getYRadius()+" skip="+object.isSkipSolidContactThisFrame());
            if(accepted) assertEquals(plane-player.getYRadius()-1,player.getCentreY());
        }
    }

    /** Range target isolates sub_79FFE; this is not a completed boss-encounter test. */
    static final class RangeTarget extends com.openggf.level.objects.AbstractObjectInstance
            implements com.openggf.level.objects.RewindRecreatable {
        RangeTarget(com.openggf.level.objects.ObjectSpawn spawn) { super(spawn,"LrzPlatformRangeTarget"); }
        @Override public void update(int clock,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
        @Override public RangeTarget recreateForRewind(com.openggf.level.objects.RewindRecreateContext context) {
            return new RangeTarget(context.spawn());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10})
    void platformBreakupKeepsOnlyTheAllocatedPrefixAndReplaysItsParticles(int capacity) {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle().build();
        fixture.stepFrame(false,false,false,false,false);
        var runtime=com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        runtime.bossAct().setStreamDirection(1);
        var camera=GameServices.camera(); camera.setX((short)0xA00); camera.setY((short)0x560);
        camera.setMinX((short)0xA00); camera.setMaxX((short)0xA00);
        camera.setMinY((short)0x560); camera.setMaxY((short)0x560); camera.setMaxYTarget((short)0x560);
        var manager=GameServices.level().getObjectManager();
        var target=manager.createDynamicObject(()->new RangeTarget(new com.openggf.level.objects.ObjectSpawn(0xB10,0x600,0,0,0,false,0)));
        runtime.bossAct().setBossSlot(target.getSlotIndex());
        var floating=manager.createDynamicObject(()->LrzBossPlatformObjectInstance.floating(0xB10));
        fixture.stepFrame(false,false,false,false,false);
        assertEquals(0x612,floating.getY()); assertEquals(5,floating.getPriorityBucket(),"stream platforms keep ObjDat priority $280");
        manager.reserveAllButNFreeSlots(capacity);
        fixture.stepFrame(false,false,false,false,false);
        assertTrue(floating.isDestroyed());
        var debris=platforms().stream().filter(p->p.getRomCodePointer()==0x85102).toList();
        assertEquals(capacity,debris.size());
        int[] dx={-16,0,16,-8,8,-12,12,-20,20,0};
        int[] dy={-8,-8,-8,8,8,-4,-4,8,8,12};
        for(int j=0;j<debris.size();j++) {
            var part=debris.get(j); int index=part.getSpawn().subtype()/2;
            assertEquals(0xB10+dx[index],part.getX()); assertEquals(0x612+dy[index],part.getY());
            assertEquals(1,part.getPriorityBucket()); assertTrue(part.isHighPriority());
        }
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,false,false); var after=registry.capture();
        assertEquals(capacity,platforms().stream().filter(p->p.getRomCodePointer()==0x85102).count());
        if(capacity==10) {
            debris.forEach(manager::removeDynamicObject);
            registry.restore(before); same(before,registry.capture());
            fixture.stepFrame(false,false,false,false,false); same(after,registry.capture());
        }
    }

}
