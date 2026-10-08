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

    private static <T> T service(WorldSession world,Class<T> type) {
        return assertNotNullService(WorldSessionPolicyAccess.getService(world,type));
    }
    private static <T> T assertNotNullService(T service) { assertNotNull(service);return service; }
    private Fixture fixture(String game) {
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
        var catalog=new MutatorCatalog(List.of(new OwnedMutator("fixture",speed),new OwnedMutator("fixture",special)),faults);
        var provider=new MutatorWorldProvider(catalog);
        var profile=new MutatorSupportProfile() {
            @Override public Set<MutatorCapability> capabilities(int zone,int act) { return EnumSet.allOf(MutatorCapability.class); }
            @Override public boolean supportsPlayer(String key,boolean leader) { return true; }
        };
        var decorated=new DelegatingGameModule(nativeModule,"fixture:lab") {
            @Override public <T> T getGameService(Class<T> type) {
                if(type==WorldSessionPolicyProvider.class) return type.cast(provider);
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
