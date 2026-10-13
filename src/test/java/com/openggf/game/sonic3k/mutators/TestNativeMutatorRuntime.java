package com.openggf.game.sonic3k.mutators;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.mutators.TransientMutatorPolicySource;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.game.sonic3k.objects.NativeGustObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.EggRoboShotInstance;
import com.openggf.game.sonic3k.objects.badniks.RhinobotBadnikInstance;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeMutatorRuntime {
    private static final class Rig {
        final AtomicReference<List<TransientMutatorPolicySource.Entry>> entries=new AtomicReference<>(List.of());
        final TransientMutatorPolicySource source=mock(TransientMutatorPolicySource.class);
        final AtomicReference<List<TransientMutatorPolicySource.Completion>> completions=new AtomicReference<>(List.of());
        final AtomicReference<com.openggf.game.mutators.LevelMutatorPolicy> levelPolicy=new AtomicReference<>(com.openggf.game.mutators.LevelMutatorPolicy.STOCK);
        final LevelManager level=mock(LevelManager.class);
        final com.openggf.camera.Camera camera=mock(com.openggf.camera.Camera.class);
        final TestObjectServices services;
        final TestablePlayableSprite player=spy(new TestablePlayableSprite("sonic",(short)0,(short)0));
        final SpriteManager sprites=mock(SpriteManager.class);
        final ObjectManager objects;
        final NativeMutatorRuntime runtime;
        final LevelFrameContext frame=mock(LevelFrameContext.class);
        Rig() throws java.io.IOException {
            var module=mock(GameModule.class);
            var data=mock(com.openggf.game.GameDataSource.class);
            var rom=com.openggf.data.Rom.fromReader(com.openggf.data.RomByteReader.fromBytes(new byte[128]),"native-runtime fixture");
            when(data.rom()).thenReturn(Optional.of(rom));
            var sizes=mock(TouchResponseTable.class);
            when(sizes.getWidthRadius(anyInt())).thenReturn(16); when(sizes.getHeightRadius(anyInt())).thenReturn(16);
            when(module.createTouchResponseTable(any(com.openggf.data.RomByteReader.class))).thenReturn(sizes);
            when(source.completions()).thenAnswer(i->completions.get());
            when(source.acknowledgeCompletion(anyLong())).thenAnswer(i->{completions.set(List.of());return true;});
            when(source.ownerAvailable(anyString())).thenReturn(true);
            when(source.entries()).thenAnswer(invocation->entries.get());
            var child=new WorldSessionPolicyState() {
                public <T>T getService(Class<T> type){
                    if(type==com.openggf.game.mutators.LevelMutatorPolicySource.class)
                        return type.cast((com.openggf.game.mutators.LevelMutatorPolicySource)levelPolicy::get);
                    return type.isInstance(source)?type.cast(source):null;
                }
                public RewindSnapshottable<?> rewindAdapter(){return new RewindSnapshottable<Boolean>(){
                    public String key(){return "test-source";}public Boolean capture(){return true;}public void restore(Boolean state){} };}
                public void beforeAssembly(LevelLoadCause cause){} public void bindRoster(SpriteManager manager){}
                public void failedAssembly(LevelLoadCause cause){}public void closeScreens(){}public void retire(){}
            };
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(
                    world->CompositeWorldSessionPolicyState.of(child,new NativeMutatorRuntime(world)));
            var world=new WorldSession(module,module,data,null);
            runtime=WorldSessionPolicyAccess.getService(world,NativeMutatorRuntime.class);
            when(camera.getMaxX()).thenReturn((short)4096);when(camera.getWidth()).thenReturn((short)320);
            when(level.getCurrentLevel()).thenReturn(emptyLevel());
            when(sprites.getMainPlayable()).thenReturn(player);
            when(sprites.getSidekicks()).thenReturn(List.of());
            services=new TestObjectServices().withWorldSession(world).withLevelManager(level)
                    .withSpriteManager(sprites).withCamera(camera).withGameState(mock(GameStateManager.class));
            objects=new ObjectManager(List.of(),null,0,null,null,null,null,services);
            services.withDirectObjectManager(objects);
            when(level.getObjectManager()).thenReturn(objects);
            when(frame.spriteManager()).thenReturn(sprites);when(frame.levelManager()).thenReturn(level);
            NativePositionOps.writeXPosPreserveSubpixel(player,100);NativePositionOps.writeYPosPreserveSubpixel(player,100);
        }
        void active(MutatorPolicy policy,boolean oneShot) {
            entries.set(List.of(new TransientMutatorPolicySource.Entry("test",1,policy,0,oneShot?0:600,oneShot)));
            runtime.beforeNativeTick(frame,LogicalInputSnapshot.neutral());
        }
    }
    private static com.openggf.level.Level emptyLevel() {
        var level=mock(com.openggf.level.Level.class);var block=new com.openggf.level.Block(8);
        for(int y=0;y<8;y++) for(int x=0;x<8;x++) block.setChunkDesc(x,y,new com.openggf.level.ChunkDesc(0));
        when(level.getBlockPixelSize()).thenReturn(128);
        when(level.getLayerWidthBlocks(anyInt())).thenReturn(32);when(level.getLayerHeightBlocks(anyInt())).thenReturn(16);
        when(level.getMap()).thenReturn(new com.openggf.level.Map(2,32,16));
        when(level.getBlockCount()).thenReturn(1);when(level.getBlock(0)).thenReturn(block);
        return level;
    }
    @Test void pendingCheckpointWithoutTargetFailsOnceAndRewindsItsOpportunityBudget() throws Exception {
        var rig=new Rig();rig.active(new RelocationPolicy(RelocationPolicy.Kind.LAST_CHECKPOINT),true);
        var state=rig.runtime.capture();
        for(int tick=0;tick<178;tick++)rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        verify(rig.source,never()).acknowledge(anyLong());
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        verify(rig.source).acknowledge(1);verify(rig.source).report(eq(1L),contains("Failed: last checkpoint"));
        rig.runtime.restore(state);rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertEquals(2,rig.runtime.capture().opportunities().get(1L));
    }
    @Test void completionIsNativeOnceOnlyAndNoRingsOrOwnerFaultDominates() throws Exception {
        var rig=new Rig();rig.completions.set(List.of(new TransientMutatorPolicySource.Completion("test",7,5)));
        rig.active(new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD,5),false);
        verify(rig.player,times(1)).addRings(5);
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());verify(rig.player,times(1)).addRings(5);
        rig.levelPolicy.set(new com.openggf.game.mutators.LevelMutatorPolicy(java.util.Set.of(),true,false,false,false));
        rig.completions.set(List.of(new TransientMutatorPolicySource.Completion("test",8,5)));
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());verify(rig.player,times(1)).addRings(5);
        when(rig.source.ownerAvailable("test")).thenReturn(false);
        rig.completions.set(List.of(new TransientMutatorPolicySource.Completion("test",9,5)));
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());verify(rig.player,times(1)).addRings(5);
        verify(rig.source).acknowledgeCompletion(7);verify(rig.source).acknowledgeCompletion(8);verify(rig.source).acknowledgeCompletion(9);
    }
    @Test void nativeEncounterGeometryRejectsTerrainSolidsAndActorIntersections() throws Exception {
        var rig=new Rig();rig.active(new RingEconomyPolicy(RingEconomyPolicy.Kind.BOUNTY),false);
        var body=new NativeEncounterGeometry.Body(164,100,25,17);
        assertTrue(NativeEncounterGeometry.clear(rig.services,body,null));
        assertFalse(NativeEncounterGeometry.clear(rig.services,NativeEncounterGeometry.actor(rig.player),null));
        rig.objects.createDynamicObject(()->new com.openggf.game.sonic3k.objects.IczIceBlockObjectInstance(new ObjectSpawn(164,100,0,0,0,false,0)));
        assertFalse(NativeEncounterGeometry.clear(rig.services,body,null));
        var block=rig.services.currentLevel().getBlock(0);block.setChunkDesc(2,2,new com.openggf.level.ChunkDesc(0x1001));
        var chunk=mock(com.openggf.level.Chunk.class);when(chunk.getSolidTileIndex()).thenReturn(1);
        byte[] heights=new byte[16];java.util.Arrays.fill(heights,(byte)16);
        when(rig.services.currentLevel().getChunkCount()).thenReturn(2);when(rig.services.currentLevel().getChunk(1)).thenReturn(chunk);
        when(rig.services.currentLevel().getSolidTileCount()).thenReturn(2);
        when(rig.services.currentLevel().getSolidTile(1)).thenReturn(new com.openggf.level.SolidTile(1,heights,new byte[16],(byte)0));
        assertFalse(com.openggf.level.render.LevelFloorScanner.clearRectangle(rig.services.currentLevel(),32,32,48,48));
        assertTrue(com.openggf.level.render.LevelFloorScanner.clearRectangle(rig.services.currentLevel(),32,16,48,32));
    }
    @Test void companionSteeringRejectsSameCodeAlienAndWrongCarryLeaderThenExpiresCapturedLease() throws Exception {
        var rig=new Rig();rig.active(new TransportPolicy(TransportPolicy.Kind.CARRY),false);
        var carrier=mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        var alien=mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        var carry=mock(com.openggf.sprites.playable.TailsCarryController.class);
        when(carrier.getCode()).thenReturn("helper");when(alien.getCode()).thenReturn("helper");
        when(carrier.getTailsCarryController()).thenReturn(carry);when(rig.sprites.getSprite("helper")).thenReturn(carrier);
        when(carry.isCarryingMainCharacter()).thenReturn(true);
        var state=rig.runtime.capture();var lease=new NativeMutatorRuntime.Companion(1,"test","helper",2,true,true,192,100);
        rig.runtime.restore(new NativeMutatorRuntime.State(state.generation(),state.assemblyEpoch(),state.admitted(),state.owned(),state.clocks(),state.tasks(),state.opportunities(),state.fieldTargets(),state.ownedChildren(),state.projectileAges(),state.budded(),state.impacts(),state.lastAir(),List.of(lease),state.rides()));
        assertTrue(rig.runtime.inputFor(alien).isEmpty());assertTrue(rig.runtime.inputFor(carrier).isEmpty());
        when(carry.isCarrying(rig.player)).thenReturn(true);assertTrue(rig.runtime.inputFor(carrier).isPresent());
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertTrue(rig.runtime.inputFor(carrier).isEmpty());verify(rig.sprites).removeTemporarySidekick(carrier);verify(carry).clearAndReleaseMain();
    }
    @Test void bountyBudgetCountsDefeatsRatherThanNativeTicksAndDrawReadsNoWorld() throws Exception {
        var rig=new Rig();rig.active(new RingEconomyPolicy(RingEconomyPolicy.Kind.BOUNTY),false);
        for(int tick=0;tick<40;tick++)rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        var badnik=rig.objects.createDynamicObject(()->new RhinobotBadnikInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
        rig.runtime.badnikDefeated(badnik,rig.player);
        assertEquals(3,rig.objects.objectQuery().activeObjectsOfType(com.openggf.level.rings.LostRingObjectInstance.class).size());
        clearInvocations(rig.level);rig.runtime.drawOverlay(mock(com.openggf.mods.ui.PixelCanvas.class));verifyNoInteractions(rig.level);
    }
    @Test void nativeFrameFromAnotherWorldCannotRebindOwnershipOrRetireCollidingObjectIds() throws Exception {
        var first=new Rig();var second=new Rig();
        first.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        second.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var alien=second.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst();
        first.runtime.beforeNativeTick(second.frame,LogicalInputSnapshot.neutral());first.runtime.retire();
        assertFalse(alien.isDestroyed());
        assertTrue(first.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst().isDestroyed());
    }
    @Test void ringBalanceNeverRemovesAnExistingHighBankBeyondItsTenRingBound() throws Exception {
        var rig=new Rig();doReturn(120).when(rig.player).getRingCount();
        var rng=mock(com.openggf.game.GameRng.class);when(rng.nextRaw()).thenReturn(0);rig.services.withRng(rng);
        rig.active(new RingEconomyPolicy(RingEconomyPolicy.Kind.BALANCE),true);
        verify(rig.player).setRingCount(110);
    }
    @Test void snapshotsCannotCrossWorldAssemblyOrRetirementAndPausesRetainOwnedTails() throws Exception {
        var first=new Rig();var second=new Rig();
        first.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var state=first.runtime.capture();
        assertThrows(IllegalArgumentException.class,()->second.runtime.restore(state));
        first.runtime.leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.PAUSED);
        assertEquals(1,first.runtime.capture().owned().size());
        first.runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        assertThrows(IllegalArgumentException.class,()->first.runtime.restore(state));
        var fresh=first.runtime.capture();first.runtime.retire();
        assertThrows(IllegalStateException.class,()->first.runtime.restore(fresh));
    }

    @Test void acknowledgedGustRetainsItsNativeTailAndRecreatedIdentity() throws Exception {
        var rig=new Rig();rig.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var gust=rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst();
        var id=rig.objects.objectQuery().identityOf(gust).orElseThrow();
        var objectState=rig.objects.rewindSnapshottable().capture();var policyState=rig.runtime.capture();
        rig.entries.set(List.of());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertFalse(gust.isDestroyed(),"acknowledgement must not cancel the native pressure phase");
        ObjectLifetimeOps.expireDynamic(gust);
        rig.objects.rewindSnapshottable().restore(objectState);rig.runtime.restore(policyState);
        var restored=rig.objects.objectQuery().resolve(id).orElseThrow();
        assertInstanceOf(NativeGustObjectInstance.class,restored);
        int before=rig.player.getCentreX();restored.update(0,rig.player);
        assertTrue(rig.player.getCentreX()>before,"recreated gust executes real Cluckoid pressure");
    }
    @Test void buddingHappensOnOneDefeatAndItsChildrenCannotBudAgain() throws Exception {
        var rig=new Rig();rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.BUDDING),true);
        var original=rig.objects.createDynamicObject(()->new RhinobotBadnikInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
        rig.runtime.afterRoutine(original,rig.player);
        assertEquals(1,rig.objects.objectQuery().activeObjectsOfType(RhinobotBadnikInstance.class).size(),"living badnik must not multiply");
        rig.runtime.badnikDefeated(original,rig.player);
        var children=rig.objects.objectQuery().activeObjectsOfType(RhinobotBadnikInstance.class);
        assertEquals(3,children.size());
        rig.runtime.badnikDefeated(children.stream().filter(o->o!=original).findFirst().orElseThrow(),rig.player);
        assertEquals(3,rig.objects.objectQuery().activeObjectsOfType(RhinobotBadnikInstance.class).size());
        verify(rig.source).acknowledge(1);
    }
    @Test void fieldsWriteNativeProjectileCoordinatesAndCancellationLeavesNoContribution() throws Exception {
        var rig=new Rig();var shot=rig.objects.createDynamicObject(()->new EggRoboShotInstance(new ObjectSpawn(50,100,0,0,1,false,0)));
        rig.active(new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE,ObjectFieldPolicy.Mode.ATTRACT),false);
        rig.runtime.afterRoutine(shot,rig.player);assertEquals(51,shot.getX());
        rig.entries.set(List.of());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        rig.runtime.afterRoutine(shot,rig.player);assertEquals(51,shot.getX());
    }
    @Test void projectileFieldCapturesSixNearestIdentitiesAndBoundsReplacementAcquisitions() throws Exception {
        var rig=new Rig();var shots=new java.util.ArrayList<EggRoboShotInstance>();
        for(int index=0;index<9;index++) {final int x=20+index*8;
            shots.add(rig.objects.createDynamicObject(()->new EggRoboShotInstance(new ObjectSpawn(x,100,0,0,1,false,0))));}
        rig.active(new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE,ObjectFieldPolicy.Mode.ATTRACT),false);
        var state=rig.runtime.capture();var objectState=rig.objects.rewindSnapshottable().capture();var selected=state.fieldTargets().get(1L).identities();assertEquals(6,selected.size());
        for(var shot:shots) {int before=shot.getX();rig.runtime.afterRoutine(shot,rig.player);
            assertEquals(before+(selected.contains(rig.objects.objectQuery().identityOf(shot).orElseThrow())?1:0),shot.getX());}
        for(var id:selected)ObjectLifetimeOps.expireDynamic((AbstractObjectInstance)rig.objects.objectQuery().resolve(id).orElseThrow());
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertEquals(9,rig.runtime.capture().fieldTargets().get(1L).acquired());
        rig.objects.rewindSnapshottable().restore(objectState);rig.runtime.restore(state);
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertEquals(selected,rig.runtime.capture().fieldTargets().get(1L).identities());
    }
    @Test void acknowledgedOwnedRootRetainsAndRewindsItsExactNativeDescendantGraph() throws Exception {
        var rig=new Rig();rig.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var root=rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst();
        var child=rig.objects.createDynamicObject(()->new NativeGustObjectInstance(new ObjectSpawn(300,100,0,0,1,false,0)));
        var unrelated=rig.objects.createDynamicObject(()->new NativeGustObjectInstance(new ObjectSpawn(400,100,0,0,1,false,0)));
        rig.runtime.childSpawned(root,child);
        var unrelatedId=rig.objects.objectQuery().identityOf(unrelated).orElseThrow();
        var state=rig.runtime.capture();var objects=rig.objects.rewindSnapshottable().capture();
        rig.runtime.ownerRetired("test");assertTrue(root.isDestroyed());assertTrue(child.isDestroyed());assertFalse(unrelated.isDestroyed());
        rig.objects.rewindSnapshottable().restore(objects);rig.runtime.restore(state);
        var ownedRoot=rig.objects.objectQuery().resolve(state.owned().getFirst().identity()).orElseThrow();
        var ownedChild=rig.objects.objectQuery().resolve(state.ownedChildren().values().iterator().next().getFirst()).orElseThrow();
        rig.runtime.leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.TRANSITION);
        assertTrue(ownedRoot.isDestroyed());assertTrue(ownedChild.isDestroyed());
        assertFalse(rig.objects.objectQuery().resolve(unrelatedId).orElseThrow().isDestroyed());
    }
    @Test void completedPowerupGrantSurvivesSourceCancellationAndReportsNativeOwnership() throws Exception {
        var rig=new Rig();doNothing().when(rig.player).giveInvincibility(600);
        rig.active(new NativePowerupPolicy(NativePowerupPolicy.Kind.INVINCIBILITY,600),false);
        verify(rig.player).giveInvincibility(600);verify(rig.source).report(eq(1L),contains("Applied: native invincibility grant"));
        rig.entries.set(List.of());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        verify(rig.player,never()).setInvincibleFrames(0);
    }
    @Test void stasisRetainsContactAndTruceRetainsTheRoutine() throws Exception {
        var rig=new Rig();var enemy=rig.objects.createDynamicObject(()->new RhinobotBadnikInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.STASIS),false);
        assertFalse(rig.runtime.permitsRoutine(enemy,rig.player));assertTrue(rig.runtime.permitsContact(enemy,rig.player));
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.TRUCE),false);
        assertTrue(rig.runtime.permitsRoutine(enemy,rig.player));assertFalse(rig.runtime.permitsContact(enemy,rig.player));
    }
}
