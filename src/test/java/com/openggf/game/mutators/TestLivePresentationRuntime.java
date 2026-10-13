package com.openggf.game.mutators;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.graphics.*;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.mods.mutators.SpritePresentationPolicy;
import com.openggf.game.mutators.TransientMutatorPolicySource.Entry;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLivePresentationRuntime {
    private static final class Source implements WorldSessionPolicyState,TransientMutatorPolicySource,RewindSnapshottable<List<Entry>> {
        List<Entry> values=List.of();
        final Map<Long,String> reports=new HashMap<>();
        public List<Entry> entries(){return values;}public boolean acknowledge(long id){return false;}
        public boolean report(long id,String s){reports.put(id,s);return true;}
        public <T>T getService(Class<T> type){return type==TransientMutatorPolicySource.class?type.cast(this):null;}
        public RewindSnapshottable<?> rewindAdapter(){return this;}public String key(){return "source";}
        public List<Entry> capture(){return values;}public void restore(List<Entry> values){this.values=values;}
        public void beforeAssembly(LevelLoadCause c){}public void bindRoster(SpriteManager m){}public void failedAssembly(LevelLoadCause c){}public void closeScreens(){}public void retire(){}
    }
    record Fixture(WorldSession world,Source source,LivePresentationRuntime runtime){}
    private Fixture fixture() {
        return fixture(null);
    }
    private Fixture fixture(com.openggf.level.render.ZonePictureSource pictureSource) {
        var source=new Source();var module=mock(GameModule.class);when(module.getIdentifier()).thenReturn("presentation-test");
        var data=mock(GameDataSource.class);
        when(data.rom()).thenReturn(Optional.of(mock(com.openggf.data.Rom.class)));
        if(pictureSource!=null)when(module.getGameService(com.openggf.level.render.ZonePictureSource.Factory.class))
                .thenReturn(rom->pictureSource);
        LivePresentationRuntime[] runtime=new LivePresentationRuntime[1];
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world->{runtime[0]=new LivePresentationRuntime(world);return CompositeWorldSessionPolicyState.of(source,runtime[0]);});
        var world=new WorldSession(module,module,data,null);return new Fixture(world,source,runtime[0]);
    }
    private void trail(Fixture f,long id) {
        f.source.values=List.of(new TransientMutatorPolicySource.Entry("test",id,new SpritePresentationPolicy(SpritePresentationPolicy.Mode.TRAIL,SpritePresentationPolicy.Target.LEADER,100,120),0,600,false));
        f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
    }
    private Frame frame(int x,int pixel,boolean suppressed) {
        var pattern=new Pattern();pattern.setPixel(0,0,(byte)pixel);
        var subject=new Subject("sonic",Part.BODY,suppressed,100,null,new Body(Role.LEADER,x,40));
        var tile=new Tile(Layer.PLAYER,42,0,true,true,true,x,40,8,8,true,5,false,1,2,7,subject);
        return new Frame(List.of(tile),List.of(),Map.of(42,SpritePresentationRenderer.version(pattern)));
    }
    @Test void trailPosesAreBoundedFrozenAndRestoreThenReplayExactly() {
        var f=fixture();trail(f,1);
        for(int i=0;i<8;i++)f.runtime.remember(frame(i,i+1,false),100,200);
        assertEquals(4,f.runtime.trails().size());assertEquals(4,f.runtime.trails().getFirst().frame().tiles().getFirst().x());
        var saved=f.runtime.capture();
        f.runtime.remember(frame(9,9,false),100,200);var forward=f.runtime.capture();
        f.runtime.restore(saved);f.runtime.remember(frame(9,9,false),100,200);assertEquals(forward,f.runtime.capture());
        assertEquals(5,SpritePresentationRenderer.pattern(saved.trails().getFirst().frame().patternVersions().get(42)).getPixel(0,0));
        for(var pose:f.runtime.trails()) { var tile=pose.frame().tiles().getFirst();assertEquals(2,tile.rowStart());assertEquals(7,tile.rowEnd());assertTrue(tile.hFlip());assertTrue(tile.priority()); }
    }
    @Test void invisibleOrUnversionedPicturesCannotEnterHistoryAndPauseDoesNotTick() {
        var f=fixture();trail(f,1);f.runtime.remember(frame(1,2,true),0,0);assertTrue(f.runtime.trails().isEmpty());
        var unversioned=frame(1,2,false);f.runtime.remember(new Frame(unversioned.tiles()),0,0);assertTrue(f.runtime.trails().isEmpty());
        f.runtime.remember(frame(2,3,false),0,0);var saved=f.runtime.capture();
        for(var phase:List.of(GameplayObservation.Phase.PAUSED,GameplayObservation.Phase.HELD,GameplayObservation.Phase.REWIND)) {
            f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),phase);f.runtime.leaveOrdinaryGameplay(phase);assertEquals(saved,f.runtime.capture());
        }
    }
    @Test void expiryReplacementAndStageEdgesReleaseOnlyOwnedArtAndRestoreReacquiresFrozenHistory() {
        var f=fixture();trail(f,1);f.runtime.remember(frame(1,2,false),0,0);var saved=f.runtime.capture();
        var graphics=new GraphicsManager();graphics.initHeadless();
        Object other=new Object();var bank=PresentationGraphicsAccess.acquire(graphics,other);int otherId=PresentationGraphicsAccess.base(bank,graphics,other);
        graphics.cachePatternTexture(new Pattern(),otherId);
        int own=f.runtime.trailBank(graphics);graphics.cachePatternTexture(new Pattern(),own);
        f.runtime.leaveOrdinaryGameplay(GameplayObservation.Phase.STAGE);assertTrue(f.runtime.trails().isEmpty());
        assertNull(SpritePresentation.patternSample(graphics,own));assertNotNull(SpritePresentation.patternSample(graphics,otherId));
        f.runtime.restore(saved);assertEquals(1,f.runtime.trails().size());assertEquals(own,f.runtime.trailBank(graphics));
        Entry active=f.source.values.getFirst();
        f.source.values=List.of(new Entry(active.owner(),active.activationId(),active.policy(),600,0,false));
        f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);assertTrue(f.runtime.trails().isEmpty());
        trail(f,2);f.runtime.remember(frame(3,3,false),0,0);trail(f,3);assertTrue(f.runtime.trails().isEmpty(),"new activation must not inherit old trail");
        var another=fixture();assertThrows(IllegalArgumentException.class,()->another.runtime.restore(saved));
        var foreignContext=mock(GameplayModeContext.class);when(foreignContext.getWorldSession()).thenReturn(another.world);
        var unchanged=f.runtime.capture();f.runtime.onPresentation(foreignContext,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
        assertEquals(unchanged,f.runtime.capture(),"another world's presentation cannot advance this runtime");
        f.runtime.retire();assertThrows(IllegalStateException.class,()->f.runtime.restore(saved));
        PresentationGraphicsAccess.release(bank,other);graphics.cleanup();
    }
    @Test void sceneExpiryReleasesOnlyRegisteredRenderOwnersWithoutWaitingForALoad() {
        var f=fixture();
        f.source.values=List.of(new Entry("test",7,new com.openggf.mods.mutators.ScenePresentationPolicy(
                com.openggf.mods.mutators.ScenePresentationPolicy.Mode.NEGATIVE,0,120),0,10,false));
        f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
        var closed=new java.util.concurrent.atomic.AtomicInteger();Object owner=new Object();
        f.runtime.registerRenderResource(owner,closed::incrementAndGet);
        f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.HELD);
        assertEquals(0,closed.get());
        f.source.values=List.of();f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
        assertEquals(1,closed.get());f.runtime.retire();assertEquals(1,closed.get(),"expired resource is removed from this world's ownership set");
    }
    @Test void failingRenderCleanupStillClearsLogicalPoliciesAndRetiresHud() {
        var f=fixture();trail(f,1);var hud=f.runtime.getService(HudPresentationRuntime.class);var hudState=hud.capture();
        f.runtime.registerRenderResource(new Object(),()->{throw new IllegalStateException("owned renderer failed to close");});
        var closed=new java.util.concurrent.atomic.AtomicInteger();f.runtime.registerRenderResource(new Object(),closed::incrementAndGet);
        assertThrows(IllegalStateException.class,f.runtime::retire);assertEquals(1,closed.get());
        assertTrue(f.runtime.spritePolicies().isEmpty());assertTrue(f.runtime.trails().isEmpty());
        assertFalse(hud.active());assertThrows(IllegalStateException.class,()->hud.restore(hudState));
    }
    @Test void pausedClearAndHeldCancellationRefreshPoliciesWithoutAdvancingClockOrRetainedPoses() {
        for(var phase:List.of(GameplayObservation.Phase.PAUSED,GameplayObservation.Phase.HELD)) {
            var f=fixture();trail(f,1);f.runtime.remember(frame(1,2,false),0,0);
            Entry trail=f.source.values.getFirst();
            f.source.values=List.of(trail,new Entry("test",2,new com.openggf.mods.mutators.ScenePresentationPolicy(
                    com.openggf.mods.mutators.ScenePresentationPolicy.Mode.NEGATIVE,0,120),0,100,false));
            f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
            var saved=f.runtime.capture();var closed=new java.util.concurrent.atomic.AtomicInteger();
            f.runtime.registerRenderResource(new Object(),closed::incrementAndGet);
            f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),phase);
            assertEquals(saved,f.runtime.capture(),"unchanged leases freeze every logical presentation value");
            f.source.values=List.of(trail);f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),phase);
            assertTrue(f.runtime.scenePolicies().isEmpty());assertEquals(1,closed.get());
            assertEquals(saved.trails(),f.runtime.trails(),"unrelated cancellation must retain surviving trail poses");
            assertEquals(saved.activePresentations(),f.runtime.activePresentations());
            f.source.values=List.of();f.runtime.onPresentation(null,LogicalInputSnapshot.neutral(),phase);
            assertTrue(f.runtime.spritePolicies().isEmpty());assertTrue(f.runtime.trails().isEmpty());
            assertEquals(saved.activePresentations(),f.runtime.activePresentations());assertEquals(saved.input(),f.runtime.input());
        }
    }
    @Test void unsupportedExternalSceneryReportsItsSourceWithoutPublishingAFallbackPicture() {
        var pictureSource=mock(com.openggf.level.render.ZonePictureSource.class);
        var f=fixture(pictureSource);
        f.source.values=List.of(new Entry("test",13,new com.openggf.mods.mutators.ScenePresentationPolicy(
                com.openggf.mods.mutators.ScenePresentationPolicy.Mode.SCENERY,0,120,13),0,120,false));
        var context=mock(GameplayModeContext.class);when(context.getWorldSession()).thenReturn(f.world);
        var camera=mock(com.openggf.camera.Camera.class);when(context.getCamera()).thenReturn(camera);
        when(camera.getWidth()).thenReturn((short)320);when(camera.getHeight()).thenReturn((short)224);
        f.runtime.onPresentation(context,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE);
        assertEquals("Scenery unavailable: Native ROM scenery 13 is not prepared",f.source.reports.get(13L));
        assertNull(f.runtime.sceneryPicture());
        verify(pictureSource).background(0x16,1);
        f.source.values=List.of();f.runtime.onPresentation(context,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.PAUSED);
        assertNull(f.runtime.sceneryPicture());assertTrue(f.runtime.scenePolicies().isEmpty());
    }
}
