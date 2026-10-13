package com.openggf.mods.mutators;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicySource;
import com.openggf.game.mutators.LevelMutatorPolicySource;
import com.openggf.game.mutators.LevelMutatorRuntime;
import com.openggf.game.mutators.StageEntryKind;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.mods.mutators.MutatorScope.*;
import static org.junit.jupiter.api.Assertions.*;

/** World lifecycle and historical scheduling through actual services; no ROM-load or native-pixel claim. */
@Isolated
class TestMutatorWorldServices {
    @TempDir Path root;
    String previousRoot;
    final List<MutatorWorldState> ownedWorlds = new ArrayList<>();

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        previousRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", root.toString());
    }
    @AfterEach void cleanup() {
        ownedWorlds.forEach(MutatorWorldState::retire);
        SessionManager.clear();
        if (previousRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", previousRoot);
    }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void enemyAfflictionsUseAdmittedWorldPolicyAndRetireWithTheirWorld(String game) {
        var fixture=fixture(game);
        var owner=fixture.owner();
        owner.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var heads=service(fixture.world(),com.openggf.game.mutators.ObjectHeadScaleSource.class);
        var badnik=com.openggf.game.mutators.ObjectHeadRole.BADNIK;
        var boss=com.openggf.game.mutators.ObjectHeadRole.BOSS;
        assertEquals(100,heads.percent(badnik,100));
        owner.state.requestEnabled("fixture:heads",true);
        owner.state.boundary(LIVE);
        assertEquals(200,heads.percent(badnik,100));
        assertEquals(100,heads.percent(boss,100));
        owner.state.onForwardTick();
        var captured=owner.capture();
        owner.state.requestOption("fixture:heads","bosses",true);
        owner.state.boundary(LIVE);
        assertEquals(200,heads.percent(boss,100));
        owner.restore(captured);
        assertEquals(200,heads.percent(badnik,100));
        assertEquals(100,heads.percent(boss,100),"rewind restores admitted choices, not the present-day draft");
        assertEquals(100,heads.percent(com.openggf.game.mutators.ObjectHeadRole.NONE,100));
        owner.retire();
        assertEquals(100,heads.percent(badnik,100));
    }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void restoredUpcomingSpeedSetsPresentationBudgetWithoutEarlyPublicationOrCreatorReplay(String game) {
        var fixture = fixture(game);
        var owner = fixture.owner();
        owner.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        owner.state.onForwardTick();
        var before = owner.capture();
        owner.state.requestEnabled("fixture:speed", true);
        owner.state.requestOption("fixture:speed", "percent", 25);
        assertTrue(owner.state.boundary(LIVE).accepted());
        var pacing = service(fixture.world(), GameplayMutatorPacing.class);
        for (int i=0; i<3; i++) assertEquals(0, pacing.stepsForPresentation());
        assertEquals(1, pacing.stepsForPresentation());
        owner.state.onForwardTick();
        assertEquals(25, service(fixture.world(), GameplayMutatorPolicySource.class).policy().gameplaySpeedPercent());
        int prepared = fixture.prepared().get();
        owner.restore(before);
        assertTrue(owner.state.requested().get("fixture:speed").enabled(), "Present-day saved draft is independent from history");
        assertEquals(100, service(fixture.world(), GameplayMutatorPolicySource.class).policy().gameplaySpeedPercent());
        assertEquals(25, pacing.policy().gameplaySpeedPercent(), "Only the scheduling owner previews the pending historical event");
        for (int i=0; i<3; i++) {
            assertEquals(0, pacing.stepsForPresentation());
            assertEquals(before.policies().tick(), owner.state.snapshot().tick());
            assertEquals(100, service(fixture.world(), GameplayMutatorPolicySource.class).policy().gameplaySpeedPercent());
        }
        assertEquals(1, pacing.stepsForPresentation());
        owner.state.onForwardTick();
        assertEquals(25, service(fixture.world(), GameplayMutatorPolicySource.class).policy().gameplaySpeedPercent());
        assertEquals(prepared, fixture.prepared().get(), "Historical scheduling and replay do not invoke creators");
    }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void compositeRestoreKeepsFractionAndUnconsumedActionEdgesTogether(String game) {
        var fixture=fixture(game);
        var owner=fixture.owner();
        owner.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        owner.state.requestEnabled("fixture:speed", true);
        owner.state.requestOption("fixture:speed", "percent", 25);
        owner.state.boundary(LIVE);
        var pacing=service(fixture.world(),GameplayMutatorPacing.class);
        assertEquals(0,pacing.stepsForPresentation());
        var jump=PlayerInputState.of(0,0,InputActionMasks.ACTION_C,InputActionMasks.ACTION_C,false,false);
        var rescue=PlayerInputState.of(0,0,0,0,true,true);
        pacing.retain(LogicalInputSnapshot.ofPlayers(jump,rescue));
        pacing.retain(LogicalInputSnapshot.neutral());
        var captured=owner.capture();
        assertTrue(pacing.hasPendingInput());
        assertEquals(InputActionMasks.ACTION_C,pacing.pendingPlayer1().actionPressedMask());
        assertTrue(pacing.pendingPlayer2().startPressed());
        pacing.reset();
        owner.restore(captured);
        assertEquals(captured.pacing(),pacing.capture());
        assertEquals(0,pacing.stepsForPresentation());
        assertEquals(0,pacing.stepsForPresentation());
        assertEquals(1,pacing.stepsForPresentation());
        assertTrue(pacing.hasPendingInput(),"A budget calculation cannot consume native input");
    }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void capturedEntryCompletesAcrossLaterDenialAndReplayButCannotSurviveWorldRetirement(String game) {
        var fixture=fixture(game);
        var owner=fixture.owner();
        owner.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var runtime=service(fixture.world(),LevelMutatorRuntime.class);
        long permit=runtime.tryAdmit(StageEntryKind.SPECIAL);
        assertNotEquals(0,permit);
        var captured=owner.capture();
        owner.state.requestEnabled("fixture:special",true);
        owner.state.boundary(LIVE);
        assertTrue(service(fixture.world(),LevelMutatorPolicySource.class).policy().noSpecialStages());
        assertEquals(0,runtime.tryAdmit(StageEntryKind.SPECIAL));
        var publications=new AtomicInteger();
        assertTrue(runtime.publish(StageEntryKind.SPECIAL,permit,publications::incrementAndGet));
        assertFalse(runtime.publish(StageEntryKind.SPECIAL,permit,publications::incrementAndGet));
        owner.restore(captured);
        assertFalse(service(fixture.world(),LevelMutatorPolicySource.class).policy().noSpecialStages());
        assertTrue(runtime.publish(StageEntryKind.SPECIAL,permit,publications::incrementAndGet));
        assertEquals(2,publications.get());
        owner.retire();
        assertEquals(0,runtime.tryAdmit(StageEntryKind.SPECIAL));
        assertFalse(runtime.publish(StageEntryKind.SPECIAL,permit,publications::incrementAndGet));
        assertThrows(IllegalStateException.class,()->runtime.restore(captured.entries()));
        assertThrows(IllegalArgumentException.class,()->fixture(game).owner().restore(captured));
    }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void freshAssemblyResetsPacingWhileNonQualifyingLoadsRetainIt(String game) {
        var fixture=fixture(game);
        var owner=fixture.owner();
        owner.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        owner.state.requestEnabled("fixture:speed",true);
        owner.state.requestOption("fixture:speed","percent",25);
        owner.state.boundary(LIVE);
        var pacing=service(fixture.world(),GameplayMutatorPacing.class);
        pacing.stepsForPresentation();
        pacing.retain(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,0,InputActionMasks.ACTION_A,false,false),null));
        var captured=pacing.capture();
        for (var cause: List.of(LevelLoadCause.DECODE_ONLY,LevelLoadCause.PREVIEW,LevelLoadCause.CHECKPOINT_RESTORE,
                LevelLoadCause.EDITOR_SWAP,LevelLoadCause.SEAMLESS_HANDOFF)) {
            owner.beforeAssembly(cause);
            assertEquals(captured,pacing.capture());
        }
        owner.beforeAssembly(LevelLoadCause.FULL_RESTART);
        assertEquals(0,pacing.capture().remainder());
        assertFalse(pacing.hasPendingInput());
        owner.failedAssembly(LevelLoadCause.FULL_DEATH_RELOAD);
        assertTrue(owner.state.isClosed());
        assertEquals(0,service(fixture.world(),LevelMutatorRuntime.class).tryAdmit(StageEntryKind.SPECIAL));
    }

    @org.junit.jupiter.api.Test void boundaryRevokesBeforeCreatorAndCleansNativeChildrenDespiteFailure() {
        var observer=new BoundaryObserver();
        var nativeChild=org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        var nativeAdapter=org.mockito.Mockito.mock(com.openggf.game.rewind.RewindSnapshottable.class);
        org.mockito.Mockito.when(nativeAdapter.capture()).thenReturn(0);
        org.mockito.Mockito.when(nativeChild.rewindAdapter()).thenReturn(nativeAdapter);
        var fixture=fixture("s3k",observer,nativeChild);
        var temporary=(TemporaryMutatorState)service(fixture.world(),com.openggf.game.mutators.TransientMutatorPolicySource.class);
        var context=org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
        org.mockito.Mockito.when(context.getWorldSession()).thenReturn(fixture.world());
        fixture.owner().beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        WorldSessionPolicyAccess.onPresentation(context,LogicalInputSnapshot.neutral(),com.openggf.game.GameplayObservation.Phase.ACTIVE);
        try(var port=temporary.open("fixture",EnumSet.allOf(MutatorCapability.class))) {
            long game=port.activate("pong",Map.of(),3).activationId();
            assertTrue(port.complete(game,true).accepted());
            assertTrue(port.activate("speed",Map.of("percent",25),4).accepted());
            assertEquals(1,temporary.completions().size());assertFalse(temporary.entries().isEmpty());
            observer.boundaryCheck=scoped -> {
                assertTrue(scoped.active().isEmpty(),"source leases are revoked before creator cleanup");
                assertTrue(temporary.completions().isEmpty(),"pending native reward jobs are also revoked");
                assertFalse(scoped.activate("speed",Map.of(),4).accepted(),"leaving phase cannot readmit work");
                throw new IllegalStateException("creator cleanup fault");
            };
            var nativeCleaned=new java.util.concurrent.atomic.AtomicBoolean();
            org.mockito.Mockito.doAnswer(call -> {
                assertTrue(temporary.entries().isEmpty());assertTrue(temporary.completions().isEmpty());
                nativeCleaned.set(true);return null;
            }).when(nativeChild).leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.TRANSITION);
            assertThrows(IllegalStateException.class,()->WorldSessionPolicyAccess.leaveOrdinaryGameplay(
                    fixture.world(),com.openggf.game.GameplayObservation.Phase.TRANSITION));
            assertTrue(nativeCleaned.get(),"native-child cleanup follows even when creator cleanup fails");
        }
    }
    @org.junit.jupiter.api.Test void nativeMenuFallbackRevokesBeforePresentationAndDoesNotRepeatSetupCleanup() {
        var observer=new BoundaryObserver();
        var nativeChild=org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        var adapter=org.mockito.Mockito.mock(com.openggf.game.rewind.RewindSnapshottable.class);
        org.mockito.Mockito.when(adapter.capture()).thenReturn(0);
        org.mockito.Mockito.when(nativeChild.rewindAdapter()).thenReturn(adapter);
        var fixture=fixture("s3k",observer,nativeChild);
        var temporary=(TemporaryMutatorState)service(fixture.world(),com.openggf.game.mutators.TransientMutatorPolicySource.class);
        var context=org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
        org.mockito.Mockito.when(context.getWorldSession()).thenReturn(fixture.world());
        WorldSessionPolicyAccess.onPresentation(context,LogicalInputSnapshot.neutral(),com.openggf.game.GameplayObservation.Phase.ACTIVE);
        var boundaries=new AtomicInteger();
        observer.boundaryCheck=port -> {
            assertTrue(port.active().isEmpty());assertTrue(temporary.completions().isEmpty());boundaries.incrementAndGet();
        };
        try(var port=temporary.open("fixture",EnumSet.allOf(MutatorCapability.class))) {
            long modal=port.activate("pong",Map.of(),3).activationId();assertTrue(port.complete(modal,true).accepted());
            assertTrue(port.activate("speed",Map.of(),5).accepted());
            WorldSessionPolicyAccess.onMenuPresentation(fixture.world(),LogicalInputSnapshot.neutral(),320,224,ignored -> { });
            assertTrue(temporary.entries().isEmpty());assertTrue(temporary.completions().isEmpty());
            assertEquals(com.openggf.game.GameplayObservation.Phase.SETUP,fixture.owner().capture().observation().phase());
            assertTrue(fixture.owner().capture().observation().leader().isEmpty());
            WorldSessionPolicyAccess.onMenuPresentation(fixture.world(),LogicalInputSnapshot.neutral(),320,224,ignored -> { });
            assertEquals(1,boundaries.get());
            org.mockito.Mockito.verify(nativeChild,org.mockito.Mockito.times(1))
                    .leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.SETUP);
        }
    }

    @org.junit.jupiter.api.Test void rawRegistryRestoreDiscardsAnAbandonedZeroTickBoundaryBeforeBranching() {
        var observer=new BoundaryObserver();
        var nativeChild=org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        var nativeAdapter=org.mockito.Mockito.mock(com.openggf.game.rewind.RewindSnapshottable.class);
        org.mockito.Mockito.when(nativeAdapter.capture()).thenReturn(0);
        org.mockito.Mockito.when(nativeChild.rewindAdapter()).thenReturn(nativeAdapter);
        var fixture=fixture("s3k",observer,nativeChild);
        var context=org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
        org.mockito.Mockito.when(context.getWorldSession()).thenReturn(fixture.world());
        WorldSessionPolicyAccess.onPresentation(context,LogicalInputSnapshot.neutral(),com.openggf.game.GameplayObservation.Phase.ACTIVE);
        var registry=new com.openggf.game.rewind.RewindRegistry();WorldSessionPolicyAccess.registerRewind(fixture.world(),registry);
        WorldSessionPolicyAccess.setHistoryRecording(fixture.world(),true);
        var before=registry.capture();
        var cleanups=new AtomicInteger();
        org.mockito.Mockito.doAnswer(call -> { cleanups.incrementAndGet();return null; }).when(nativeChild)
                .leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.TRANSITION);
        WorldSessionPolicyAccess.leaveOrdinaryGameplay(fixture.world(),com.openggf.game.GameplayObservation.Phase.TRANSITION);
        assertEquals(1,cleanups.get());
        registry.restore(before);
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getWorldSession()).thenReturn(fixture.world());
        var frame=org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        org.mockito.Mockito.when(frame.levelManager()).thenReturn(level);
        WorldSessionPolicyAccess.beforeNativeTick(frame);WorldSessionPolicyAccess.afterNativeTick(frame);
        var branch=WorldSessionPolicyAccess.captureHistory(fixture.world());
        WorldSessionPolicyAccess.restoreHistoryBefore(fixture.world(),branch);
        assertEquals(1,cleanups.get(),"the abandoned future's presentation cleanup is not journalled into the new branch");
        WorldSessionPolicyAccess.restoreHistoryAfter(fixture.world(),branch);
    }

    @ParameterizedTest @ValueSource(strings={"GUMBALL","GLOWING_SPHERE","SLOT_MACHINE"})
    void realNativeBonusCoordinatorPreventsTemporaryAdmissionAndOrdinaryPackConsumption(String stage) {
        var observer=new BoundaryObserver();
        var nativeChild=org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        var nativeAdapter=org.mockito.Mockito.mock(com.openggf.game.rewind.RewindSnapshottable.class);
        org.mockito.Mockito.when(nativeAdapter.capture()).thenReturn(0);
        org.mockito.Mockito.when(nativeChild.rewindAdapter()).thenReturn(nativeAdapter);
        var fixture=fixture("s3k",observer,nativeChild);
        var provider=new com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator();
        provider.onEnter(com.openggf.game.BonusStageType.valueOf(stage),new com.openggf.game.BonusStageState(
                0,0,25,0,0,0,0,0,0,0,0,0,(byte)12,(byte)13,0,0L));
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getWorldSession()).thenReturn(fixture.world());
        var leader=org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        org.mockito.Mockito.when(leader.characterKey()).thenReturn(com.openggf.game.CharacterKey.SONIC);
        var sprites=org.mockito.Mockito.mock(com.openggf.sprites.managers.SpriteManager.class);
        org.mockito.Mockito.when(sprites.getMainPlayable()).thenReturn(leader);
        org.mockito.Mockito.when(sprites.getAllSprites()).thenReturn(List.of(leader));
        org.mockito.Mockito.when(com.openggf.sprites.managers.SpriteManagerInternalAccess.publishedInput(sprites))
                .thenReturn(LogicalInputSnapshot.neutral());
        var frame=org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        org.mockito.Mockito.when(frame.levelManager()).thenReturn(level);
        org.mockito.Mockito.when(frame.spriteManager()).thenReturn(sprites);
        org.mockito.Mockito.when(frame.bonusStageProvider()).thenReturn(provider);
        var observed=new java.util.concurrent.atomic.AtomicBoolean();
        observer.tickCheck=(observation,port) -> {
            assertEquals(com.openggf.game.GameplayObservation.Phase.STAGE,observation.phase());
            assertEquals("none",observation.leader().orElseThrow().shield());
            assertFalse(port.activate("speed",Map.of("percent",25),4).accepted());observed.set(true);
        };
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        provider.onExit(); // Exit during the row must not reclassify its tail as ordinary gameplay.
        WorldSessionPolicyAccess.afterNativeTick(frame);
        assertTrue(observed.get());
        org.mockito.Mockito.verify(nativeChild,org.mockito.Mockito.never()).beforeNativeTick(org.mockito.Mockito.any(),org.mockito.Mockito.any());
        org.mockito.Mockito.verify(nativeChild,org.mockito.Mockito.never()).afterNativeTick(org.mockito.Mockito.any(),org.mockito.Mockito.any());
        assertEquals(0,fixture.owner().capture().nativeTicks());
    }

    private static final class BoundaryObserver implements com.openggf.game.GameplayObserver,
            com.openggf.game.rewind.RewindSnapshottable<Integer> {
        java.util.function.Consumer<TemporaryMutators> boundaryCheck=ignored -> { };
        java.util.function.BiConsumer<com.openggf.game.GameplayObservation,TemporaryMutators> tickCheck=(observation,mutators) -> { };
        @Override public void onTick(com.openggf.game.GameplayObservation observation,TemporaryMutators mutators) { tickCheck.accept(observation,mutators); }
        public String key(){return "boundary-observer";}
        public Integer capture(){return 0;}
        public void restore(Integer value){}
        @Override public void onBoundary(com.openggf.game.GameplayObservation observation,TemporaryMutators mutators) { boundaryCheck.accept(mutators); }
    }

    private static <T> T service(WorldSession world,Class<T> type) {
        return assertNotNullService(WorldSessionPolicyAccess.getService(world,type));
    }
    private static <T> T assertNotNullService(T service) { assertNotNull(service);return service; }
    private Fixture fixture(String game) { return fixture(game,null,null); }
    private Fixture fixture(String game,BoundaryObserver observer,com.openggf.game.session.WorldSessionPolicyState nativeChild) {
        GameModule nativeModule=switch(game) {
            case "s1" -> new Sonic1GameModule();
            case "s2" -> new Sonic2GameModule();
            default -> new Sonic3kGameModule();
        };
        var preparations=new AtomicInteger();
        var speed=new MutatorDefinition("speed","Speed","",LIVE,LIVE,
                List.of(new MutatorOption.IntegerSlider("percent","Percent","",LIVE,100,25,400,25,"%")),
                Set.of(MutatorCapability.GAMEPLAY_SPEED),options->{preparations.incrementAndGet();
                    return List.of(new MutatorPolicy.GameplaySpeed(options.integer("percent"),false));});
        var special=new MutatorDefinition("special","Special","",LIVE,LIVE,List.of(),
                Set.of(MutatorCapability.NO_SPECIAL_STAGES),options->List.of(new MutatorPolicy.NoSpecialStages()));
        var faults=new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),
                owners->new ModStateSaveResult.Saved(),owners->{});
        var heads=new MutatorDefinition("heads","Heads","",LIVE,LIVE,
                List.of(new MutatorOption.Checkbox("bosses","Bosses","",LIVE,false)),
                Set.of(MutatorCapability.BIG_HEAD),options->List.of(new MutatorPolicy.BigHead(200,
                    options.checkbox("bosses")?Set.of(MutatorPolicy.HeadTarget.BADNIKS,MutatorPolicy.HeadTarget.BOSSES)
                            :Set.of(MutatorPolicy.HeadTarget.BADNIKS))));
        var catalog=new MutatorCatalog(List.of(new OwnedMutator("fixture",speed),new OwnedMutator("fixture",special),
                new OwnedMutator("fixture",heads)),faults);
        if(observer!=null) {
            var pong=new MutatorDefinition("pong","Pong","",LIVE,LIVE,List.of(),
                    Set.of(MutatorCapability.GAMEPLAY_HOLD,MutatorCapability.OVERLAY_CONTENT,MutatorCapability.RING_ECONOMY),
                    options -> List.of(new GameplayHoldPolicy(3),new OverlayPolicy(OverlayPolicy.Mode.PONG),
                            new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD,5)));
            catalog=catalog.append(List.of(new OwnedMutator("fixture",pong)),faults);
        }
        var provider=new MutatorWorldProvider(catalog);
        var observers=org.mockito.Mockito.mock(com.openggf.mods.code.GameplayObserverRegistry.class);
        if(observer!=null) {
            org.mockito.Mockito.when(observers.entries()).thenReturn(List.of(
                    new com.openggf.mods.code.GameplayObserverRegistry.Entry("fixture",observer,observer)));
            org.mockito.Mockito.when(observers.adapters()).thenReturn(List.of(observer));
        }
        var profile=new MutatorSupportProfile() {
            @Override public Set<MutatorCapability> capabilities(int zone,int act) { return EnumSet.allOf(MutatorCapability.class); }
            @Override public boolean supportsPlayer(String key,boolean leader) { return true; }
        };
        var decorated=new DelegatingGameModule(nativeModule,"fixture:lab") {
            @Override public <T> T getGameService(Class<T> type) {
                if(type==WorldSessionPolicyProvider.class)return type.cast((WorldSessionPolicyProvider) world ->
                        com.openggf.game.session.CompositeWorldSessionPolicyState.of(provider.open(world),nativeChild));
                if((type==com.openggf.mods.code.GameplayObserverRegistry.class
                        || type==com.openggf.game.session.GameplayObserverSnapshots.class) && observer!=null)
                    return type.cast(observers);
                if(type==MutatorSupportProfile.class) return type.cast(profile);
                return super.getGameService(type);
            }
        };
        var world=new WorldSession(nativeModule,decorated,null);
        var owner=WorldSessionPolicyAccess.getService(world,MutatorWorldState.class);
        ownedWorlds.add(owner);
        return new Fixture(world,owner,preparations);
    }
    private record Fixture(WorldSession world,MutatorWorldState owner,AtomicInteger prepared) { }
}
