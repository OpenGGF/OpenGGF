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
    private static final class LifetimeChildProducer extends AbstractObjectInstance implements RewindRecreatable {
        LifetimeChildProducer(ObjectSpawn spawn) { super(spawn,"lifetime-child-producer"); }
        @Override public void update(int frame,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        @Override public LifetimeChildProducer recreateForRewind(RewindRecreateContext context) { return new LifetimeChildProducer(context.spawn()); }
        private NativeGustObjectInstance gust() { return new NativeGustObjectInstance(new ObjectSpawn(300,100,0,0,1,false,0)); }
        void legacy() { spawnDynamicObject(gust()); }
        void lowestFree() { spawnDynamicObjectLowestFreeSlot(gust()); }
        void free() { spawnFreeChild(this::gust); }
        void afterSlot() { spawnChildAfterSlot(getSlotIndex(),this::gust); }
        void sibling() { spawnAfterCurrentSibling(this::gust); }
    }
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
        Rig() throws java.io.IOException { this(List.of()); }
        Rig(List<ObjectSpawn> spawns) throws java.io.IOException {
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
            var terrain=emptyLevel();
            when(level.getCurrentLevel()).thenReturn(terrain);
            when(sprites.getMainPlayable()).thenReturn(player);
            when(sprites.getSidekicks()).thenReturn(List.of());
            services=new TestObjectServices().withWorldSession(world).withLevelManager(level)
                    .withSpriteManager(sprites).withCamera(camera).withGameState(mock(GameStateManager.class));
            objects=new ObjectManager(spawns,null,0,null,null,null,null,services);
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
    @Test void rouletteUsesExactObservedCheckpointBanksAndNeverInfersLowerOrDuplicatePosts() throws Exception {
        var rig=new Rig(List.of(
                new ObjectSpawn(256,96,com.openggf.game.sonic3k.constants.Sonic3kObjectIds.STAR_POST,2,0,false,0),
                new ObjectSpawn(420,96,com.openggf.game.sonic3k.constants.Sonic3kObjectIds.STAR_POST,1,0,false,1),
                new ObjectSpawn(460,96,com.openggf.game.sonic3k.constants.Sonic3kObjectIds.STAR_POST,2,0,false,2)));
        var checkpoint=mock(com.openggf.game.RespawnState.class);
        when(rig.level.getCheckpointState()).thenReturn(checkpoint);
        when(checkpoint.getStarPostActivationMark()).thenReturn(2);
        when(checkpoint.getSavedX()).thenReturn(256);when(checkpoint.getSavedY()).thenReturn(96);
        var rng=mock(com.openggf.game.GameRng.class);when(rng.nextInt(anyInt())).thenAnswer(i->(int)i.getArgument(0)-1);
        rig.services.withRng(rng);
        var terrain=rig.services.currentLevel();var block=terrain.getBlock(0);
        for(int x=0;x<8;x++)block.setChunkDesc(x,7,new com.openggf.level.ChunkDesc(0x1001));
        var chunk=mock(com.openggf.level.Chunk.class);when(chunk.getSolidTileIndex()).thenReturn(1);
        byte[] heights=new byte[16];java.util.Arrays.fill(heights,(byte)16);
        when(terrain.getChunkCount()).thenReturn(2);when(terrain.getChunk(1)).thenReturn(chunk);
        when(terrain.getSolidTileCount()).thenReturn(2);
        when(terrain.getSolidTile(1)).thenReturn(new com.openggf.level.SolidTile(1,heights,new byte[16],(byte)0));
        rig.active(new RelocationPolicy(RelocationPolicy.Kind.RANDOM_CHECKPOINT),true);
        assertTrue(Math.abs(rig.player.getCentreX()-256)<=12,"Roulette must stage around the exact saved post, never the unvisited layout alternatives");
        verify(rng).nextInt(1);verify(rig.source).acknowledge(1);
        var first=rig.runtime.capture();assertEquals(List.of(new NativeMutatorRuntime.CheckpointDestination(256,96)),first.encounteredCheckpoints());
        when(checkpoint.getSavedX()).thenReturn(460);
        rig.runtime.afterNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertEquals(2,rig.runtime.capture().encounteredCheckpoints().size());
        when(checkpoint.getSavedX()).thenReturn(256);rig.runtime.restore(first);
        assertEquals(first.encounteredCheckpoints(),rig.runtime.capture().encounteredCheckpoints());
        rig.runtime.beforeAssembly(LevelLoadCause.FULL_DEATH_RELOAD);
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        assertEquals(first.encounteredCheckpoints(),rig.runtime.capture().encounteredCheckpoints(),"a respawn may seed only its exact authoritative saved bank");
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
        rig.runtime.restore(new NativeMutatorRuntime.State(state.generation(),state.assemblyEpoch(),state.admitted(),state.owned(),state.clocks(),state.tasks(),state.opportunities(),state.fieldTargets(),state.ownedChildren(),state.projectileAges(),state.budded(),state.impacts(),state.lastAir(),List.of(lease),state.rides(),state.encounteredCheckpoints()));
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
    @Test void ringLeakRetainsTheLastRingAndDoesNotRestoreSpentInventoryOnExpiry() throws Exception {
        for (int initial : new int[] { 0, 1, 2 }) {
            var rig = new Rig();
            rig.player.setRingCount(initial);
            rig.active(new RingEconomyPolicy(RingEconomyPolicy.Kind.LEAK), false);
            for (int tick = 1; tick < 120; tick++)
                rig.runtime.beforeNativeTick(rig.frame, LogicalInputSnapshot.neutral());
            assertEquals(Math.min(initial, 1), rig.player.getRingCount());
            assertEquals(initial == 2 ? 1 : 0, rig.objects.objectQuery()
                    .activeObjectsOfType(com.openggf.level.rings.LostRingObjectInstance.class).size());
            rig.entries.set(List.of());
            rig.runtime.beforeNativeTick(rig.frame, LogicalInputSnapshot.neutral());
            assertEquals(Math.min(initial, 1), rig.player.getRingCount());
        }
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
    @Test void failedBuddingCannotSpawnOrAcknowledgeAgainLaterInTheSameNativeTick() throws Exception {
        var rig=new Rig();
        rig.entries.set(List.of(new TransientMutatorPolicySource.Entry("test",1,new EnemyRulePolicy(EnemyRulePolicy.Kind.BUDDING),1200,0,true)));
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        var badnik=rig.objects.createDynamicObject(()->new RhinobotBadnikInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
        rig.runtime.badnikDefeated(badnik,rig.player);
        assertEquals(1,rig.objects.objectQuery().activeObjectsOfType(RhinobotBadnikInstance.class).size());
        verify(rig.source,times(1)).acknowledge(1);
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
    @Test void nativeAllocationHelpersCaptureOwnedTailsAfterRootRemovalAndLeaveIndependentSiblingsAlone() throws Exception {
        var rig=new Rig();rig.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var root=rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst();
        var producer=rig.objects.createDynamicObject(()->new LifetimeChildProducer(new ObjectSpawn(260,100,0,0,0,false,0)));
        rig.runtime.childSpawned(root,producer);
        producer.legacy();producer.lowestFree();producer.free();producer.afterSlot();producer.sibling();
        var producerId=rig.objects.objectQuery().identityOf(producer).orElseThrow();
        var state=rig.runtime.capture();var children=state.ownedChildren().get(producerId);
        assertEquals(4,children.size(),"all native structural allocation helpers must notify ownership");
        var sibling=rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).stream()
                .filter(object->object!=root && !children.contains(rig.objects.objectQuery().identityOf(object).orElseThrow())).findFirst().orElseThrow();
        var siblingId=rig.objects.objectQuery().identityOf(sibling).orElseThrow();
        rig.objects.removeDynamicObject(root);rig.entries.set(List.of());
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        rig.objects.removeDynamicObject(producer);
        rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        for(var id:children)assertFalse(rig.objects.objectQuery().resolve(id).orElseThrow().isDestroyed(),"native tails survive root removal under the bounded lease");
        var tail=rig.runtime.capture();var objects=rig.objects.rewindSnapshottable().capture();
        rig.runtime.ownerRetired("test");
        for(var id:children)assertTrue(rig.objects.objectQuery().resolve(id).orElseThrow().isDestroyed());
        assertFalse(sibling.isDestroyed());
        rig.objects.rewindSnapshottable().restore(objects);rig.runtime.restore(tail);
        rig.runtime.leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.CLOSED);
        for(var id:children)assertTrue(rig.objects.objectQuery().resolve(id).orElseThrow().isDestroyed());
        assertFalse(rig.objects.objectQuery().resolve(siblingId).orElseThrow().isDestroyed());
    }
    @Test void nativeDefeatReplacementAndDestructionChildrenBelongOnlyToTheExactOwnedGraph() throws Exception {
        var rig=new Rig();rig.active(new EncounterPolicy(EncounterPolicy.Kind.GUST,EncounterPolicy.Side.AHEAD),true);
        var root=rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class).getFirst();
        rig.runtime.badnikDefeated(root,rig.player);
        int slot=ObjectLifetimeOps.detachSlotForTransfer(root);root.setDestroyed(true);
        var config=new DestructionEffects.DestructionConfig(-1,
                (spawn,services)->new NativeGustObjectInstance(spawn),false,
                (spawn,services,points)->new NativeGustObjectInstance(spawn),null,false);
        DestructionEffects.destroyBadnik(root.getX(),root.getY(),root.getSpawn(),slot,rig.player,rig.services,config);
        var explosion=rig.objects.objectQuery().activeObjectsOfType(ExplosionObjectInstance.class).getFirst();
        explosion.update(0,rig.player);
        assertEquals(3,rig.runtime.capture().ownedChildren().values().stream().mapToInt(List::size).sum());
        var unrelated=rig.objects.createDynamicObject(()->new NativeGustObjectInstance(new ObjectSpawn(400,100,0,0,0,false,0)));
        rig.runtime.replacementSpawned(slot,unrelated);
        rig.runtime.ownerRetired("test");assertTrue(explosion.isDestroyed());
        for(var object:rig.objects.objectQuery().activeObjectsOfType(NativeGustObjectInstance.class))
            assertEquals(object!=unrelated,object.isDestroyed(),"replacement association is consumed once and cannot capture unrelated occupants");
    }
    @Test void completedPowerupGrantSurvivesSourceCancellationAndReportsNativeOwnership() throws Exception {
        var rig=new Rig();doNothing().when(rig.player).giveInvincibility(600);
        rig.active(new NativePowerupPolicy(NativePowerupPolicy.Kind.INVINCIBILITY,600),false);
        verify(rig.player).giveInvincibility(600);verify(rig.source).report(eq(1L),contains("Applied: native invincibility grant"));
        rig.entries.set(List.of());rig.runtime.beforeNativeTick(rig.frame,LogicalInputSnapshot.neutral());
        verify(rig.player,never()).setInvincibleFrames(0);
    }
    @Test void stasisRetainsContactAndTruceRetainsTheRoutine() throws Exception {
        var rig=new Rig();var enemy=rig.objects.createDynamicObject(()->new com.openggf.game.sonic3k.objects.badniks.ButterdroidBadnikInstance(new ObjectSpawn(100,100,0,0,0,false,0)));
        var initializing=rig.objects.createDynamicObject(()->new RhinobotBadnikInstance(new ObjectSpawn(140,100,0,0,0,false,0)));
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.STASIS),false);
        assertTrue(rig.runtime.permitsRoutine(initializing,rig.player),"stock init must establish contact before stasis pauses AI");
        assertFalse(rig.runtime.permitsRoutine(enemy,rig.player));assertTrue(rig.runtime.permitsContact(enemy,rig.player));
        rig.active(new EnemyRulePolicy(EnemyRulePolicy.Kind.TRUCE),false);
        assertTrue(rig.runtime.permitsRoutine(enemy,rig.player));assertFalse(rig.runtime.permitsContact(enemy,rig.player));
    }
}
