package com.openggf.game.session;

import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.mods.mutators.*;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real session/assembly/fault owners; no trace rows or fixture-driven physics. */
@Isolated
class TestMutatorWorldIntegration {
    @TempDir Path temp;
    String oldRoot;
    ModFaultBoundary faults;
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap()); SessionManager.clear();
        oldRoot=System.getProperty("openggf.saveRoot"); System.setProperty("openggf.saveRoot",temp.resolve("fresh/player").toString());
        faults=new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),owners->new ModStateSaveResult.Saved(),owners->{});
    }
    @AfterEach void cleanup() {
        SessionManager.clear(); GameModuleRegistry.setCurrent(new Sonic2GameModule());
        if(oldRoot==null) System.clearProperty("openggf.saveRoot"); else System.setProperty("openggf.saveRoot",oldRoot);
    }
    MutatorDefinition definition(String id,MutatorScope enable,MutatorDefinition.PolicyFactory factory) {
        return new MutatorDefinition(id,id,"Bounded synthetic policy",enable,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.DRY_SONIC_GRAVITY),factory);
    }
    GameModule module(List<MutatorDefinition> definitions) {
        var catalog=new MutatorCatalog(definitions.stream().map(d->new OwnedMutator("test-mutators",d)).toList(),faults);
        return new DelegatingGameModule(new Sonic2GameModule(),"test-mutators:fixture") {
            @Override public boolean supportsSidekick() { return false; }
            @Override public <T> T getGameService(Class<T> type) {
                if(type==WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                if(type==MutatorCatalog.class) return type.cast(catalog);
                if(type==MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile(){
                    public Set<MutatorCapability> capabilities(int z,int a){ return Set.of(MutatorCapability.DRY_SONIC_GRAVITY); }
                    public boolean supportsPlayer(String key,boolean leader){ return leader; }
                });
                return super.getGameService(type);
            }
        };
    }
    WorldSession open(List<MutatorDefinition> definitions) {
        var m=module(definitions); GameModuleRegistry.setCurrent(m); SessionManager.openGameplaySession(new Sonic2GameModule(),m,null);
        TestEnvironment.activeGameplayMode(); return SessionManager.getCurrentWorldSession();
    }
    @Test void stockAndUnsupportedWorldsKeepNoPolicyStateOrPreferenceStore() {
        var stock = new WorldSession(new Sonic2GameModule());
        assertFalse(WorldSessionPolicyAccess.hasPolicies(stock));
        assertNull(MutatorWorldAccess.state(stock));
        assertFalse(Files.exists(temp.resolve("fresh/player/mutators")));
        var catalog = new MutatorCatalog(List.of(new OwnedMutator("test-mutators",
                definition("gravity", MutatorScope.LIVE,
                        options -> List.of(new MutatorPolicy.DrySonicGravity(50))))), faults);
        var unsupported = new DelegatingGameModule(new Sonic2GameModule(), "test-mutators:unsupported") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                return super.getGameService(type);
            }
        };
        assertNull(MutatorWorldAccess.state(new WorldSession(unsupported)));
        assertFalse(Files.exists(temp.resolve("fresh/player/mutators")));
    }
    @Test void backingProviderCombinesOwnersAndOpensIndependentWorldsWithOuterSupport() {
        GameModule backing = new Sonic2GameModule();
        for (String owner : List.of("first-owner", "second-owner")) {
            var plan = ModContextTestAccess.freezeWithMutator(owner, "s2",
                    definition("gravity", MutatorScope.LIVE,
                            options -> List.of(new MutatorPolicy.DrySonicGravity(50))));
            backing = new ModBackedGamePatch(plan, faults).apply(backing, null);
        }
        var provider = backing.getGameService(WorldSessionPolicyProvider.class);
        assertSame(provider, backing.getGameService(WorldSessionPolicyProvider.class));
        var resolved = new DelegatingGameModule(backing, "test-mutators:outer-support") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int zone, int act) {
                        return Set.of(MutatorCapability.DRY_SONIC_GRAVITY);
                    }
                    public boolean supportsPlayer(String key, boolean leader) { return leader; }
                });
                return super.getGameService(type);
            }
        };
        var first = MutatorWorldAccess.state(new WorldSession(new Sonic2GameModule(), resolved, null));
        var second = MutatorWorldAccess.state(new WorldSession(new Sonic2GameModule(), resolved, null));
        assertEquals(List.of("first-owner:gravity", "second-owner:gravity"),
                first.definitions().stream().map(OwnedMutator::key).toList());
        assertNotSame(first, second);
        assertNotEquals(first.generation(), second.generation());
        first.requestEnabled("first-owner:gravity", true);
        assertFalse(second.requested().get("first-owner:gravity").enabled());
    }
    @Test void ringScaledHeadFollowsLiveNativeInventoryWhileFixedAndOffModesStayUnchanged() {
        var rings=new java.util.concurrent.atomic.AtomicInteger();
        var bigHead=new MutatorDefinition("big-head","Big Head","",MutatorScope.LIVE,MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("percent","Head size","",MutatorScope.LIVE,150,100,200,10,"%"),
                        new MutatorOption.Checkbox("rings","Scale with rings","",MutatorScope.LIVE,false),
                        new MutatorOption.Checkbox("team","All team","",MutatorScope.LIVE,false)),
                Set.of(MutatorCapability.BIG_HEAD),o->List.of(
                        new MutatorPolicy.BigHead(o.integer("percent"),o.checkbox("team")?MutatorPolicy.Target.ALL_TEAM
                                :MutatorPolicy.Target.LEADER,o.checkbox("rings"))));
        var stealth=new MutatorDefinition("stealth","Stealth","",MutatorScope.LIVE,MutatorScope.LIVE,List.of(),
                Set.of(MutatorCapability.PLAYER_STEALTH),o->List.of(new MutatorPolicy.PlayerStealth(true,true,MutatorPolicy.Target.LEADER,false)));
        var catalog=new MutatorCatalog(List.of(new OwnedMutator("test-mutators",bigHead),new OwnedMutator("test-mutators",stealth)),faults);
        var m=new DelegatingGameModule(new Sonic2GameModule(),"test-mutators:heads") {
            @Override public boolean supportsSidekick() { return false; }
            @Override public <T> T getGameService(Class<T> type) {
                if(type==WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                if(type==MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile(){
                    public Set<MutatorCapability> capabilities(int z,int a){ return Set.of(MutatorCapability.BIG_HEAD,MutatorCapability.PLAYER_STEALTH); }
                    public boolean supportsPlayer(String key,boolean leader){ return true; }
                    public boolean supportsPlayer(MutatorCapability capability,String key,boolean leader){ return key.equals("sonic"); }
                });
                return super.getGameService(type);
            }
        };
        GameModuleRegistry.setCurrent(m);SessionManager.openGameplaySession(new Sonic2GameModule(),m,null);
        TestEnvironment.activeGameplayMode();
        var world=SessionManager.getCurrentWorldSession();var state=MutatorWorldAccess.state(world);
        var sonic=new com.openggf.sprites.playable.Sonic("sonic",(short)32,(short)48) {
            @Override public int getRingCount() { return rings.get(); }
        };
        GameServices.sprites().addSprite(sonic);
        java.util.function.IntSupplier head=()->com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(sonic).headScalePercent();
        state.requestEnabled("test-mutators:big-head",true);
        state.requestOption("test-mutators:big-head","percent",200);
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        rings.set(0);assertEquals(200,head.getAsInt(),"default fixed mode ignores rings");
        state.requestOption("test-mutators:big-head","rings",true);
        assertEquals(200,head.getAsInt(),"a LIVE edit waits for Resume");
        assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        int[][] expected={{0,100},{25,125},{50,150},{99,199},{100,200},{150,200},{999,200}};
        for(int[] pair:expected) { rings.set(pair[0]);assertEquals(pair[1],head.getAsInt(),pair[0]+" rings"); }
        rings.set(100);rings.set(40);assertEquals(140,head.getAsInt(),"losing rings shrinks the head");
        rings.set(80);assertEquals(180,head.getAsInt(),"restored inventory is read live, never cached");
        rings.set(0);assertSame(com.openggf.sprites.playable.PlayableMutatorPolicy.STOCK,
                com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(sonic),"zero rings is the stock presentation");
        state.requestOption("test-mutators:big-head","percent",150);assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        rings.set(50);assertEquals(125,head.getAsInt(),"the slider is the 100-ring maximum");
        rings.set(100);state.requestEnabled("test-mutators:stealth",true);assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        var hidden=com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(sonic);
        assertTrue(hidden.suppressBody(),"Stealth still owns visibility; the renderer hides the body before any head scale");
        var tails=new com.openggf.sprites.playable.Tails("tails",(short)32,(short)48);
        GameServices.sprites().addSprite(tails);
        assertEquals(100,com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(tails).headScalePercent(),
                "unsupported art keeps its stock head");
    }
    @Test void firstLaunchCreatesRequestedStoreAndWorldRetirementRejectsHistoricalState() {
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        var state=MutatorWorldAccess.state(world); state.requestEnabled("test-mutators:gravity",true);
        assertTrue(MutatorWorldAccess.save(world)); assertTrue(Files.isRegularFile(temp.resolve("fresh/player/mutators/mutator-preferences-s2.json")));
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var snapshot=state.snapshot(); SessionManager.clear(); assertTrue(state.isClosed());
        var next=open(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        assertTrue(MutatorWorldAccess.state(next).requested().get("test-mutators:gravity").enabled());
        assertThrows(IllegalArgumentException.class,()->MutatorWorldAccess.state(next).restore(snapshot));
    }
    @Test void failedTitleSaveKeepsDraftAndPriorAdmissionUntilOneSuccessfulRetry() throws Exception {
        var preparations=new java.util.concurrent.atomic.AtomicInteger();
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->{preparations.incrementAndGet();return List.of(new MutatorPolicy.DrySonicGravity(50));})));
        var state=MutatorWorldAccess.state(world);
        assertTrue(MutatorWorldAccess.save(world));
        var destination=preferenceDestination(); Files.delete(destination); Files.createDirectory(destination);
        state.requestEnabled("test-mutators:gravity",true);
        var prior=Map.copyOf(state.admitted());long revision=state.effective().revision();
        var cues=new ArrayList<MutatorConfigurationScreen.Cue>();
        var screen=new MutatorConfigurationScreen(world,"Save regression",null,cues::add);screen.initialize();
        var input=new com.openggf.control.InputHandler();
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,false);
        assertSaveHeld(screen,state,world,prior,revision);
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());assertEquals(0,preparations.get());
        Files.delete(destination);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,false);
        assertEquals(TitleScreenProvider.State.EXITING,screen.getState());
        assertEquals(50,state.effective().gravityPercent());assertTrue(state.admitted().get("test-mutators:gravity").enabled());
        assertTrue(MutatorWorldAccess.saveError(world).isBlank());
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,false);
        assertEquals(1,preparations.get());assertEquals(1,cues.stream().filter(c->c==MutatorConfigurationScreen.Cue.START).count());
        assertTrue(new MutatorPreferenceStore(destination.getParent(),"s2").load().preferences()
                .forSession(state.definitions()).get("test-mutators:gravity").enabled());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=LevelInputOverlay.Command.class,names={"RESUME","FULL_RESTART","RETURN_TO_HUB"})
    void failedGameplaySaveNeverAdmitsOrQueuesAndSuccessfulRetryQueuesExactlyOnce(LevelInputOverlay.Command expected) throws Exception {
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);assertTrue(MutatorWorldAccess.save(world));
        var destination=preferenceDestination();Files.delete(destination);Files.createDirectory(destination);
        state.requestEnabled("test-mutators:gravity",true);
        var prior=Map.copyOf(state.admitted());long revision=state.effective().revision();
        var screen=new MutatorConfigurationScreen(world,"Save regression",null,c->{});screen.initialize();
        var input=new com.openggf.control.InputHandler();
        press(screen,input,GameServices.configuration().getInt(com.openggf.configuration.SonicConfiguration.PAUSE_KEY),true);
        int row=switch(expected){case RESUME->1;case FULL_RESTART->2;case RETURN_TO_HUB->4;default->throw new AssertionError(expected);};
        for(int i=0;i<row;i++) press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,true);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,true);
        assertSaveHeld(screen,state,world,prior,revision);assertTrue(screen.pausesGameplay());
        Files.delete(destination);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,true);
        assertEquals(expected,screen.consumeCommand());assertTrue(MutatorWorldAccess.saveError(world).isBlank());
        if(expected==LevelInputOverlay.Command.RESUME) assertEquals(50,state.effective().gravityPercent());
        else { assertEquals(prior,state.admitted());assertEquals(revision,state.effective().revision()); }
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,true);
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE})
    void helpBackUsesFixedKeyboardEdgesWithoutExitingTitle(int backKey) throws Exception {
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        var screen=new MutatorConfigurationScreen(world,"Help controls",null,c->{});screen.initialize();
        var input=new com.openggf.control.InputHandler();
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,false);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,false);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,false);
        var page=MutatorConfigurationScreen.class.getDeclaredField("page");page.setAccessible(true);
        assertEquals("HELP",page.get(screen).toString());
        press(screen,input,backKey,false);
        assertEquals("HOME",page.get(screen).toString());
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
        assertEquals(100,MutatorWorldAccess.state(world).effective().gravityPercent());
    }
    @Test void titleBackSaveFailureRetainsDraftAndRetryQueuesHubExactlyOnce() throws Exception {
        var preparations=new java.util.concurrent.atomic.AtomicInteger();
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->{preparations.incrementAndGet();return List.of(new MutatorPolicy.DrySonicGravity(50));})));
        var state=MutatorWorldAccess.state(world);assertTrue(MutatorWorldAccess.save(world));
        var destination=preferenceDestination();Files.delete(destination);Files.createDirectory(destination);
        state.requestEnabled("test-mutators:gravity",true);
        var prior=Map.copyOf(state.admitted());long revision=state.effective().revision();
        var screen=new MutatorConfigurationScreen(world,"Back regression",null,c->{});screen.initialize();
        var input=new com.openggf.control.InputHandler();
        assertTrue(screen.ownsEscapeInput());
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,false);
        assertSaveHeld(screen,state,world,prior,revision);assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState());
        Files.delete(destination);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,false);
        assertEquals(LevelInputOverlay.Command.RETURN_TO_HUB,screen.consumeCommand());
        assertEquals(prior,state.admitted());assertEquals(revision,state.effective().revision());assertEquals(0,preparations.get());
        screen.commandQueued(false);
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,false);
        assertEquals(TitleScreenProvider.State.ACTIVE,screen.getState(),"accepted hub fade cannot be replaced by a Start");
        assertEquals(0,preparations.get());
        press(screen,input,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,false);
        assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
    }
    private Path preferenceDestination() { return temp.resolve("fresh/player/mutators/mutator-preferences-s2.json"); }
    private static void press(MutatorConfigurationScreen screen,com.openggf.control.InputHandler input,int key,boolean overlay) {
        input.handleKeyEvent(key,org.lwjgl.glfw.GLFW.GLFW_PRESS);
        if(overlay) screen.handleInput(input);else screen.update(input);
        input.update();input.handleKeyEvent(key,org.lwjgl.glfw.GLFW.GLFW_RELEASE);input.update();
    }
    private static void assertSaveHeld(MutatorConfigurationScreen screen,MutatorSessionState state,WorldSession world,
                                      Map<String,MutatorSessionState.Configuration> admitted,long revision) throws Exception {
        assertEquals(admitted,state.admitted());assertEquals(revision,state.effective().revision());assertEquals(100,state.effective().gravityPercent());
        assertTrue(state.requested().get("test-mutators:gravity").enabled());assertEquals(LevelInputOverlay.Command.NONE,screen.consumeCommand());
        assertFalse(MutatorWorldAccess.saveError(world).isBlank());
        var status=MutatorConfigurationScreen.class.getDeclaredField("status");status.setAccessible(true);
        assertTrue(((String)status.get(screen)).contains("Save failed"),"the retryable error stays visible");
    }
    @Test void launchPreparationAbortSurvivesProductionLevelAssemblyAndCleanup() {
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->{ throw new IllegalStateException("prepare failed"); })));
        MutatorWorldAccess.state(world).requestEnabled("test-mutators:gravity",true);
        var context=freshContext(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var mode=SessionManager.getCurrentGameplayMode();
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->mode.getLevelManager().loadLevel(0,LevelLoadMode.FULL,context));
        assertEquals("test-mutators",failure.owner()); assertFalse(faults.isOwnerAvailable("test-mutators"));
        assertDoesNotThrow(SessionManager::clear); assertFalse(mode.isGameplayRuntimeReady());
    }
    @Test void deferredLoadFactoryAbortIsTypedAndRetiresTheFailedSession() {
        var world=open(List.of(definition("gravity",MutatorScope.LOAD,o->{ throw new IllegalStateException("load prepare failed"); })));
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world); state.requestEnabled("test-mutators:gravity",true);
        assertTrue(state.boundary(MutatorScope.LIVE).accepted()); assertEquals(100,state.effective().gravityPercent());
        var context=freshContext(LevelLoadCause.FULL_RESTART);
        assertThrows(ModFaultBoundary.CallbackAborted.class,()->SessionManager.getCurrentGameplayMode().getLevelManager().loadLevel(0,LevelLoadMode.FULL,context));
        assertTrue(state.isClosed());
    }
    @Test void nonQualifyingLoadsNeverAdmitPendingActivation() {
        var world=open(List.of(definition("gravity",MutatorScope.LOAD,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);state.requestEnabled("test-mutators:gravity",true);
        for(var cause:List.of(LevelLoadCause.PREVIEW,LevelLoadCause.CHECKPOINT_RESTORE,LevelLoadCause.EDITOR_SWAP,LevelLoadCause.SEAMLESS_HANDOFF,LevelLoadCause.DECODE_ONLY)) MutatorWorldAccess.beforeAssembly(world,cause);
        assertEquals(100,state.effective().gravityPercent());
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_DEATH_RELOAD); assertEquals(50,state.effective().gravityPercent());
    }
    @Test void causeCannotPromoteDecodeRestoreOrPreviewIntoFreshAssembly() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var delegate=module(List.of(definition("gravity",MutatorScope.LOAD,o->{calls.incrementAndGet();return List.of(new MutatorPolicy.DrySonicGravity(50));})));
        var profile=org.mockito.Mockito.mock(LevelInitProfile.class);
        org.mockito.Mockito.when(profile.levelLoadSteps(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new InitStep("Decode-only fixture","Synthetic admission boundary",()->{})));
        var m=new DelegatingGameModule(delegate,"test-mutators:load-fixture") {
            @Override public LevelInitProfile getLevelInitProfile() { return profile; }
        };
        GameModuleRegistry.setCurrent(m);SessionManager.openGameplaySession(new Sonic2GameModule(),m,null);
        var mode=TestEnvironment.activeGameplayMode();var world=mode.getWorldSession();
        MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);state.requestEnabled("test-mutators:gravity",true);
        var decode=new LevelLoadContext();decode.setLoadCause(LevelLoadCause.FULL_RESTART);
        mode.getLevelManager().loadLevel(0,LevelLoadMode.FULL,decode);
        var restore=freshContext(LevelLoadCause.FULL_RESTART);restore.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);
        mode.getLevelManager().loadLevel(0,LevelLoadMode.FULL,restore);
        mode.getLevelManager().loadLevel(0,LevelLoadMode.PREVIEW_CAPTURE,freshContext(LevelLoadCause.FULL_RESTART));
        assertEquals(0,calls.get());assertEquals(100,state.effective().gravityPercent());assertFalse(state.isClosed());
        mode.getLevelManager().loadLevel(0,LevelLoadMode.FULL,freshContext(LevelLoadCause.FULL_RESTART));
        assertEquals(1,calls.get());assertEquals(50,state.effective().gravityPercent());
    }
    @Test void automaticLaunchConflictKeepsDefaultsAndPendingDraftRecoverable() {
        var a=definition("a",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)));
        var b=new MutatorDefinition("b","b","Synthetic launch conflict",1,MutatorScope.LIVE,MutatorScope.LIVE,
                List.of(),List.of(),Set.of(),Set.of("a"),Set.of(MutatorCapability.DRY_SONIC_GRAVITY),o->List.of(new MutatorPolicy.DrySonicGravity(150)));
        var world=open(List.of(a,b));var state=MutatorWorldAccess.state(world);
        state.requestEnabled("test-mutators:a",true);state.requestEnabled("test-mutators:b",true);
        assertDoesNotThrow(()->MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY));
        assertEquals(100,state.effective().gravityPercent());assertFalse(state.isClosed());assertEquals(2,state.pending().size());
        assertTrue(MutatorWorldAccess.admissionError(world).contains("Launch kept prior settings"));
        state.requestEnabled("test-mutators:b",false);assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        assertEquals(50,state.effective().gravityPercent());
    }
    @Test void automaticLoadRejectsConflictWithoutCrashingOrChangingPriorEffectiveGraph() {
        var a=definition("a",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)));
        var b=new MutatorDefinition("b","b","Synthetic deferred conflict",1,MutatorScope.LOAD,MutatorScope.LIVE,
                List.of(),List.of(),Set.of(),Set.of("a"),Set.of(MutatorCapability.DRY_SONIC_GRAVITY),o->List.of(new MutatorPolicy.DrySonicGravity(150)));
        var world=open(List.of(a,b));MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        var state=MutatorWorldAccess.state(world);state.requestEnabled("test-mutators:a",true);assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        long revision=state.effective().revision();state.requestEnabled("test-mutators:b",true);assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        assertDoesNotThrow(()->MutatorWorldAccess.beforeAssembly(world,LevelLoadCause.FULL_DEATH_RELOAD));
        assertEquals(revision,state.effective().revision());assertEquals(50,state.effective().gravityPercent());
        assertFalse(state.admitted().get("test-mutators:b").enabled());assertFalse(state.isClosed());
        assertTrue(MutatorWorldAccess.admissionError(world).contains("Conflicting mutators"));
    }
    @Test void quarantinedExplicitProviderCannotBlockNativeManagerTeardown() {
        var delegate=module(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        GamePatch patch=new GamePatch(){
            public String id(){return "fixture";}public String displayName(){return "fixture";}public String baseGameId(){return "s2";}
            public boolean activatesFor(GameplayLaunchRequest r){return true;}public Set<LogicalRom> romPrerequisites(){return Set.of();}
            public List<String> providedMainCharacters(){return List.of();}public GameModule apply(GameModule b,PatchContext c){return delegate;}
        };
        var wrapped=OwnerBoundGamePatch.wrap("test-mutators",patch,faults).apply(new Sonic2GameModule(),null);
        GameModuleRegistry.setCurrent(wrapped);SessionManager.openGameplaySession(new Sonic2GameModule(),wrapped,null);
        var mode=TestEnvironment.activeGameplayMode();var sprites=mode.getSpriteManager();
        assertThrows(ModFaultBoundary.CallbackAborted.class,()->faults.run("test-mutators",()->{throw new IllegalStateException("menu fault");}));
        assertDoesNotThrow(SessionManager::clear);assertNull(mode.getSpriteManager());assertFalse(mode.isGameplayRuntimeReady());
        assertNull(sprites.getMainPlayable());
    }
    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void nativeForwardReplayRestoresRevisionsAndRecreatedRosterWithoutSavedPreferenceLaundering() {
        var world=open(List.of(definition("gravity",MutatorScope.LIVE,o->List.of(new MutatorPolicy.DrySonicGravity(50)))));
        var fixture=com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        fixture.stepIdleFrames(90);
        var state=MutatorWorldAccess.state(world);
        var mode=SessionManager.getCurrentGameplayMode();
        var registry=mode.getRewindRegistry();
        state.requestEnabled("test-mutators:gravity",true);
        assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        fixture.stepIdleFrames(1);
        var before=registry.capture();
        assertTrue(before.containsKey("mutators"));
        var expected=new ArrayList<NativeFrame>();
        for(int frame=0;frame<36;frame++) {
            if(frame==0 || frame==18) {
                state.requestEnabled("test-mutators:gravity",frame==18);
                assertTrue(state.boundary(MutatorScope.LIVE).accepted());
            }
            fixture.stepFrame(false,false,false,true,frame==0);
            expected.add(nativeFrame(mode,state));
        }
        state.requestEnabled("test-mutators:gravity",false);
        assertTrue(MutatorWorldAccess.save(world));
        var prior=mode.getSpriteManager().getMainPlayable();
        mode.getSpriteManager().clearAllSprites();
        var recreated=GameplayTeamBootstrap.registerActiveTeam(world.getGameModule(),mode.getSpriteManager(),GameServices.configuration()).mainSprite();
        assertNotSame(prior,recreated);
        mode.getLevelManager().refreshPlayableSpriteArt();
        mode.getLevelManager().refreshPlayablePowerUpSpawners();
        registry.restore(before);
        assertFalse(state.requested().get("test-mutators:gravity").enabled(),"Saved present-day choice stays independent of history");
        assertEquals(50,state.effective().gravityPercent(),"Historical ON revision restores independently of saved OFF draft");
        var runner=new com.openggf.tests.HeadlessTestRunner(recreated);
        for(int frame=0;frame<36;frame++) {
            runner.stepFrame(false,false,false,true,frame==0);
            assertEquals(expected.get(frame),nativeFrame(mode,state),"Native replay frame "+frame);
        }
        assertEquals(100,expected.get(0).gravity());
        assertEquals(50,expected.get(18).gravity());
        assertTrue(expected.get(18).air(),"Recreated binding replay must compare an airborne gravity change");
    }
    private static NativeFrame nativeFrame(GameplayModeContext mode,MutatorSessionState state) {
        var sprite=mode.getSpriteManager().getMainPlayable();
        return new NativeFrame(sprite.getCentreX(),sprite.getCentreY(),sprite.getXSpeed(),sprite.getYSpeed(),sprite.getGSpeed(),sprite.getAir(),state.effective().revision(),state.effective().gravityPercent());
    }
    private static LevelLoadContext freshContext(LevelLoadCause cause) {
        var context=new LevelLoadContext();context.setLoadCause(cause);
        context.setIncludePostLoadAssembly(true);context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        return context;
    }
    private record NativeFrame(int x,int y,int xSpeed,int ySpeed,int groundSpeed,boolean air,long revision,int gravity) {}
}
