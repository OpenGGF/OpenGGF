package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.io.*;
import com.openggf.mods.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit-patch callbacks must retain engine-recorded ownership without stealing inherited callbacks. */
@org.junit.jupiter.api.parallel.Isolated
public class TestOwnerBoundGamePatch {
    @TempDir Path temp;

    public interface Controller extends RewindSnapshottable<Integer>, AutoCloseable {
        boolean beforeTick();
        void afterTick();
        void drawOverlay();
        @Override void close();
    }
    public interface Service { Controller controller(); }
    static final class Mode implements Controller {
        String failure;
        int state;
        String suppliedKey = "claimed-other-owner:mode";
        Mode(String failure) { this.failure = failure; }
        void check(String callback) { if (callback.equals(failure)) throw new IllegalStateException(callback); }
        @Override public String key() { return suppliedKey; }
        @Override public boolean beforeTick() { check("beforeTick"); return true; }
        @Override public void afterTick() { check("afterTick"); state++; }
        @Override public void drawOverlay() { check("drawOverlay"); }
        @Override public void close() { check("close"); state = -1; }
        @Override public Integer capture() { check("capture"); return state; }
        @Override public void restore(Integer value) { check("restore"); state = value; }
        @Override public void resetForMissingSnapshot() { check("reset"); state = 0; }
    }
    static final class Patched extends DelegatingGameModule {
        final Mode mode;
        final TitleScreenProvider title;
        Patched(GameModule base, Mode mode, TitleScreenProvider title) { super(base, "claimed-other-owner:patch"); this.mode = mode; this.title = title; }
        @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new ArrayList<>(base().rewindAdapters()); adapters.add(mode); return adapters;
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == Controller.class) return type.cast(mode);
            if (type == Service.class) return type.cast((Service)() -> mode);
            return base().getGameService(type);
        }
    }
    record Fixture(ModFaultBoundary boundary, ModRuntimeFindingStore findings, AtomicReference<Set<String>> disabled) { }
    Fixture fixture() {
        var findings = new ModRuntimeFindingStore(); var disabled = new AtomicReference<Set<String>>();
        return new Fixture(new ModFaultBoundary(Map.of("dependent", Set.of("registered-owner")), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set), findings, disabled);
    }
    GameModule wrap(GameModule base, GameModule result, Fixture fixture) throws Exception {
        return wrappedPatch(ignored -> result, fixture).apply(base, null);
    }
    GamePatch wrappedPatch(java.util.function.Function<GameModule, GameModule> apply, Fixture fixture) throws Exception {
        return wrappedPatch("registered-owner", apply, fixture);
    }
    GamePatch wrappedPatch(String owner, java.util.function.Function<GameModule, GameModule> apply, Fixture fixture) throws Exception {
        Class<?> helper = Class.forName("com.openggf.mods.runtime.OwnerBoundGamePatch");
        return (GamePatch)helper.getMethod("wrap", String.class, GamePatch.class, ModFaultBoundary.class)
                .invoke(null, owner, patch(apply), fixture.boundary());
    }
    static GamePatch patch(java.util.function.Function<GameModule, GameModule> apply) {
        return new GamePatch() {
            @Override public String id() { return "patch"; }
            @Override public String displayName() { return "claimed-other-owner"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return apply.apply(base); }
        };
    }
    void abort(Fixture fixture, String callback, Runnable action) {
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, action::run);
        assertEquals("registered-owner", aborted.owner()); assertEquals(callback, aborted.getCause().getMessage());
        assertEquals(Set.of("registered-owner", "dependent"), fixture.disabled().get());
        assertEquals("MOD_CALLBACK_FAILED", fixture.findings().findingsFor("registered-owner").getFirst().code());
        assertTrue(fixture.findings().findingsFor("claimed-other-owner").isEmpty());
    }

    @Test void providerAndControllerCallbacksIncludingCloseAreOwnerBoundedAndStable() throws Exception {
        for (String callback : List.of("beforeTick", "afterTick", "drawOverlay", "close")) {
            Fixture fixture = fixture(); var mode = new Mode(callback); GameModule base = mock(GameModule.class);
            when(base.rewindAdapters()).thenReturn(List.of());
            GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
            Controller controller = wrapped.getGameService(Controller.class);
            assertSame(controller, wrapped.getGameService(Controller.class));
            assertSame(controller, wrapped.rewindAdapters().getFirst(), "one object can implement controller and rewind interfaces");
            abort(fixture, callback, switch (callback) {
                case "beforeTick" -> () -> controller.beforeTick();
                case "afterTick" -> controller::afterTick;
                case "drawOverlay" -> controller::drawOverlay;
                default -> controller::close;
            });
        }
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var title = mock(TitleScreenProvider.class);
        doThrow(new IllegalStateException("title draw")).when(title).draw();
        GameModule wrapped = wrap(base, new Patched(base, new Mode(null), title), fixture);
        assertSame(wrapped.getTitleScreenProvider(), wrapped.getTitleScreenProvider());
        abort(fixture, "title draw", wrapped.getTitleScreenProvider()::draw);
    }

    @Test void rewindCaptureRestoreResetAndNestedCallbacksHaveStableOwnerBoundaries() throws Exception {
        for (String callback : List.of("capture", "restore", "reset")) {
            Fixture fixture = fixture(); var mode = new Mode(callback); GameModule base = mock(GameModule.class);
            when(base.rewindAdapters()).thenReturn(List.of());
            GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
            @SuppressWarnings("unchecked") var adapter = (RewindSnapshottable<Integer>)wrapped.rewindAdapters().getFirst();
            assertSame(adapter, wrapped.rewindAdapters().getFirst());
            abort(fixture, callback, switch (callback) {
                case "capture" -> () -> adapter.capture();
                case "restore" -> () -> adapter.restore(7);
                default -> adapter::resetForMissingSnapshot;
            });
        }
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var mode = new Mode("close");
        GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
        abort(fixture, "close", wrapped.getGameService(Service.class).controller()::close);
    }

    @Test void inheritedNativeAndEarlierOwnedProvidersAndAdaptersKeepTheirIdentityAndAttribution() throws Exception {
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var nativeTitle = mock(TitleScreenProvider.class);
        var nativeAdapter = new Mode("capture"); when(base.getTitleScreenProvider()).thenReturn(nativeTitle);
        when(base.rewindAdapters()).thenReturn(List.of(nativeAdapter));
        doThrow(new IllegalStateException("native title")).when(nativeTitle).draw();
        GameModule wrapped = wrap(base, new Patched(base, new Mode(null), nativeTitle), fixture);
        assertSame(nativeTitle, wrapped.getTitleScreenProvider());
        assertSame(nativeAdapter, wrapped.rewindAdapters().getFirst());
        assertThrows(IllegalStateException.class, wrapped.getTitleScreenProvider()::draw);
        assertThrows(IllegalStateException.class, wrapped.rewindAdapters().getFirst()::capture);
        assertNull(fixture.disabled().get()); assertTrue(fixture.findings().snapshot().isEmpty());
        assertSame(base, wrap(base, base, fixture), "an identity patch adds no owner scope");

        var inheritedFailure = new IllegalStateException("native getter");
        when(base.getTitleScreenProvider()).thenThrow(inheritedFailure);
        GameModule inherited = wrap(base, new DelegatingGameModule(base, "inherited") { }, fixture);
        assertSame(inheritedFailure, assertThrows(IllegalStateException.class, inherited::getTitleScreenProvider));
        assertNull(fixture.disabled().get());
    }

    static final class ExpertRegistryModule extends DelegatingGameModule {
        final com.openggf.game.PlayableCharacterRegistry registry;
        ExpertRegistryModule(GameModule base, com.openggf.game.PlayableCharacterRegistry registry) {
            super(base,"reported-other-owner:registry");this.registry=registry;
        }
        @Override public com.openggf.game.PlayableCharacterRegistry getPlayableCharacterRegistry() { return registry; }
    }
    static com.openggf.game.CharacterDefinition expertDefinition(com.openggf.game.CharacterKey key,String failure) {
        return new com.openggf.game.CharacterDefinition(key,"Expert runner",
                (code,x,y)->new ExpertSprite(code,(short)x,(short)y,failure),
                controller->new com.openggf.sprites.playable.SidekickRespawnStrategy() {
                    public boolean updateApproaching(com.openggf.sprites.playable.AbstractPlayableSprite sidekick,
                            com.openggf.sprites.playable.AbstractPlayableSprite leader,int frame) {
                        throw new IllegalStateException("expert respawn");
                    }
                    public boolean beginApproach(com.openggf.sprites.playable.AbstractPlayableSprite sidekick,
                            com.openggf.sprites.playable.AbstractPlayableSprite leader) { return true; }
                },com.openggf.game.PlayerCharacter.SONIC_ALONE,com.openggf.sprites.playable.SecondaryAbility.NONE,
                false,code->{throw new java.io.IOException("expert art");});
    }
    static final class ExpertSprite extends com.openggf.sprites.playable.Sonic {
        final String failure;
        ExpertSprite(String code,short x,short y,String failure) { super(code,x,y);this.failure=failure; }
        @Override protected boolean onAbilityActivate(boolean up,boolean down,boolean left,boolean right) {
            if ("ability".equals(failure)) throw new IllegalStateException("expert ability");
            return false;
        }
    }
    @Test void freshExpertCharacterRegistryIsCachedAndDefinitionCallbacksBelongToActualPatchOwner() throws Exception {
        for (String failure:List.of("art","respawn","ability")) {
            com.openggf.tests.TestEnvironment.resetAll();
            try {
                Fixture fixture=fixture();GameModule base=new com.openggf.game.sonic2.Sonic2GameModule();
                var key=com.openggf.game.CharacterKey.SONIC;
                var raw=expertDefinition(key,failure);
                var registry=com.openggf.game.PlayableCharacterRegistry.empty().register(key,raw);
                GameModule wrapped=wrap(base,new ExpertRegistryModule(base,registry),fixture);
                var mapped=wrapped.getPlayableCharacterRegistry();
                assertSame(mapped,wrapped.getPlayableCharacterRegistry());
                var definition=mapped.find(key).orElseThrow();
                assertNotSame(raw,definition);assertEquals(key,definition.key());
                switch(failure) {
                    case "art" -> {
                        var aborted=assertThrows(ModFaultBoundary.CallbackAborted.class,()->definition.artSupplier().load("sonic"));
                        assertEquals("registered-owner",aborted.owner());
                    }
                    case "respawn" -> abort(fixture,"expert respawn",()->definition.respawnStrategyFactory()
                            .create(null).updateApproaching(null,null,0));
                    default -> {
                        com.openggf.game.GameModuleRegistry.setCurrent(wrapped);
                        var configuration=mock(com.openggf.configuration.SonicConfigurationService.class);
                        when(configuration.getString(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("sonic");
                        when(configuration.getString(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE)).thenReturn("");
                        var sprites=new com.openggf.sprites.managers.SpriteManager(configuration);
                        var sprite=com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(wrapped,sprites,configuration).mainSprite();
                        assertSame(sprite,sprites.getSprite("sonic"),"Actual team consumer publishes the owned expert sprite");
                        abort(fixture,"expert ability",()->com.openggf.sprites.playable.CharacterRuntimeHooks.activateAbility(sprite));
                    }
                }
                assertEquals(Set.of("registered-owner","dependent"),fixture.disabled().get());
            } finally { com.openggf.tests.TestEnvironment.resetAll();com.openggf.game.GameModuleRegistry.reset(); }
        }
    }
    @Test void inheritedCharacterDefinitionsRetainIdentityAndFreshForeignKeysAbortActualOwner() throws Exception {
        Fixture earlierFixture=fixture();GameModule root=mock(GameModule.class);
        when(root.getPlayableCharacterRegistry()).thenReturn(com.openggf.game.PlayableCharacterRegistry.empty());
        var earlierKey=com.openggf.game.CharacterKey.mod("registered-owner","runner");
        var earlierRegistry=com.openggf.game.PlayableCharacterRegistry.empty().register(earlierKey,expertDefinition(earlierKey,"art"));
        GameModule earlier=wrap(root,new ExpertRegistryModule(root,earlierRegistry),earlierFixture);
        var earlierDefinition=earlier.getPlayableCharacterRegistry().find(earlierKey).orElseThrow();
        var laterFixture=new Fixture(new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),ignored->{}),new ModRuntimeFindingStore(),new AtomicReference<>());
        var freshKey=com.openggf.game.CharacterKey.mod("later-owner","runner");
        var combined=earlier.getPlayableCharacterRegistry().register(freshKey,expertDefinition(freshKey,"art"));
        GameModule later=wrappedPatch("later-owner",ignored->new ExpertRegistryModule(earlier,combined),laterFixture).apply(earlier,null);
        assertSame(earlierDefinition,later.getPlayableCharacterRegistry().find(earlierKey).orElseThrow());
        var inheritedFailure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->earlierDefinition.artSupplier().load(earlierKey.persisted()));
        assertEquals("registered-owner",inheritedFailure.owner());

        Fixture foreignFixture=fixture();var foreignKey=com.openggf.game.CharacterKey.mod("foreign-owner","runner");
        var foreign=com.openggf.game.PlayableCharacterRegistry.empty().register(foreignKey,expertDefinition(foreignKey,"art"));
        GameModule attempted=wrap(root,new ExpertRegistryModule(root,foreign),foreignFixture);
        var aborted=assertThrows(ModFaultBoundary.CallbackAborted.class,attempted::getPlayableCharacterRegistry);
        assertEquals("registered-owner",aborted.owner());
        assertEquals(Set.of("registered-owner","dependent"),foreignFixture.disabled().get());
    }

    @Test void normalLaunchMetadataConsumerRetainsOwnedCharacterCallbacksBeforePatchApplication() throws Exception {
        Fixture fixture=fixture();var key=com.openggf.game.CharacterKey.mod("registered-owner","runner");
        var definitions=new LinkedHashMap<com.openggf.game.CharacterKey,com.openggf.game.CharacterDefinition>();
        definitions.put(key,expertDefinition(key,"art"));
        GamePatch metadata=new GamePatch() {
            public String id() { return "metadata"; }
            public String displayName() { return "Expert metadata"; }
            public String baseGameId() { return "s2"; }
            public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            public List<String> providedMainCharacters() { return List.of(key.persisted()); }
            public Map<com.openggf.game.CharacterKey,com.openggf.game.CharacterDefinition> providedCharacterDefinitions() { return definitions; }
            public GameModule apply(GameModule base,PatchContext context) { throw new AssertionError("Metadata discovery must not apply the patch"); }
        };
        GamePatch owned=com.openggf.mods.runtime.OwnerBoundGamePatch.wrap("registered-owner",metadata,fixture.boundary());
        var registration=new RegisteredPatch(new PatchOwner.Mod("registered-owner"),"registered-owner:metadata",owned,0);
        var resolver=new ModuleResolutionService(List.of(),PatchEnablement.ALL_ENABLED,new LogicalRomResolver(()->null),
                mock(com.openggf.configuration.SonicConfigurationService.class),
                ignored->new ModuleResolutionService.PatchPlan(List.of(registration),Map.of()));
        var launch=resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD);
        var discovered=resolver.availableMainCharacterRegistry(launch,"s2").find(key).orElseThrow();
        assertNotSame(definitions.get(key),discovered);
        assertSame(discovered,resolver.availableMainCharacterRegistry(launch,"s2").find(key).orElseThrow());
        definitions.put(key,expertDefinition(key,"respawn"));
        assertNotSame(discovered,resolver.availableMainCharacterRegistry(launch,"s2").find(key).orElseThrow(),
                "Mutable expert metadata produces a fresh bound snapshot while retained earlier callbacks stay owned");
        var aborted=assertThrows(ModFaultBoundary.CallbackAborted.class,()->discovered.artSupplier().load(key.persisted()));
        assertEquals("registered-owner",aborted.owner());assertEquals(Set.of("registered-owner","dependent"),fixture.disabled().get());
    }

    @Test void patchApplyFailuresUseTrustedRegistrationOwner() throws Exception {
        Fixture fixture = fixture(); GamePatch wrapped = wrappedPatch(base -> { throw new IllegalStateException("apply"); }, fixture);
        abort(fixture, "apply", () -> wrapped.apply(mock(GameModule.class), null));
    }

    @Test void earlierOwnedAdapterIsNotReattributedToTheNextPatch() throws Exception {
        Fixture fixture = fixture(); GameModule nativeBase = mock(GameModule.class);
        when(nativeBase.rewindAdapters()).thenReturn(List.of());
        GameModule earlier = wrappedPatch("earlier-owner", base -> new Patched(base, new Mode("capture"), null), fixture)
                .apply(nativeBase, null);
        GameModule later = wrap(earlier, new Patched(earlier, new Mode(null), null), fixture);
        assertSame(earlier.rewindAdapters().getFirst(), later.rewindAdapters().getFirst());
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, later.rewindAdapters().getFirst()::capture);
        assertEquals("earlier-owner", aborted.owner());
        assertEquals(Set.of("earlier-owner"), fixture.disabled().get());
        assertTrue(fixture.findings().findingsFor("registered-owner").isEmpty());
    }

    @Test void powerUpFactoriesRetainOwnershipThroughTheRealConsumer() throws Exception {
        Fixture fixture = fixture();
        GameModule base = new com.openggf.game.sonic2.Sonic2GameModule();
        GameModule effective = wrap(base, new DelegatingGameModule(base, "factories") {
            @Override public java.util.function.BiFunction<com.openggf.sprites.playable.AbstractPlayableSprite,
                    com.openggf.game.ShieldType, com.openggf.level.objects.ShieldObjectInstance> getShieldFactory() {
                return (player, type) -> {
                    assertEquals("registered-owner", OwnerCallbackScope.current());
                    throw new IllegalStateException("shield factory");
                };
            }
        }, fixture);
        var services = new com.openggf.level.objects.StubObjectServices() {
            @Override public GameModule gameModule() { return effective; }
        };
        var objects = new com.openggf.level.objects.ObjectManager(List.of(), null, 0,
                null, null, null, null, services);
        var consumer = new com.openggf.level.objects.DefaultPowerUpSpawner(objects);
        abort(fixture, "shield factory", () -> consumer.spawnShield(
                mock(com.openggf.sprites.playable.AbstractPlayableSprite.class), com.openggf.game.ShieldType.BASIC));
    }

    @Test void composedReturnedFactoriesRetainTheCreatorBoundary() throws Exception {
        Fixture fixture = fixture();
        GameModule base = new com.openggf.game.sonic2.Sonic2GameModule();
        GameModule module = wrap(base, new DelegatingGameModule(base, "factory") {
            @Override public java.util.function.BiFunction<Integer, Integer,
                    com.openggf.level.objects.AbstractObjectInstance> getWaterSplashFactory() {
                return (x, y) -> {
                    assertEquals("registered-owner", OwnerCallbackScope.current());
                    throw new IllegalStateException("composed factory");
                };
            }
        }, fixture);
        var factory = module.getWaterSplashFactory().andThen(value -> value);
        abort(fixture, "composed factory", () -> factory.apply(1, 2));
    }

    @Test void hostileRewindKeysCannotReplaceHostOrOtherOwnerStateAtRegistration() throws Exception {
        com.openggf.game.session.EngineServices.configure(
                com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        for (String key : List.of("gamerng", "rings", "level", "mode")) {
            Fixture fixture = fixture();
            GameModule nativeBase = new com.openggf.game.sonic2.Sonic2GameModule();
            var first = new Mode(null); first.suppliedKey = key; first.state = 11;
            GameModule earlier = wrappedPatch("earlier-owner", base -> new Patched(base, first, null), fixture)
                    .apply(nativeBase, null);
            var second = new Mode(null); second.suppliedKey = key; second.state = 22;
            GameModule effective = wrap(earlier, new Patched(earlier, second, null), fixture);
            var gameplay = new com.openggf.game.session.GameplayModeContext(
                    new com.openggf.game.session.WorldSession(effective));
            var rng = new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2);
            gameplay.attachGameplayManagers(new com.openggf.camera.Camera(), new com.openggf.timer.TimerManager(),
                    new com.openggf.game.GameStateManager(), new com.openggf.graphics.FadeManager(), rng,
                    new com.openggf.game.solid.DefaultSolidExecutionRegistry());
            var registry = gameplay.getRewindRegistry();
            var snapshot = registry.capture();
            assertEquals(rng.capture(), snapshot.get("gamerng"));
            var adapters = effective.rewindAdapters();
            assertNotEquals(key, adapters.get(adapters.size() - 2).key());
            assertNotEquals(adapters.get(adapters.size() - 2).key(), adapters.getLast().key());
            assertEquals(11, snapshot.get(adapters.get(adapters.size() - 2).key()));
            assertEquals(22, snapshot.get(adapters.getLast().key()));
            first.state = 100; second.state = 200;
            gameplay.registerGameModuleRewindAdapters();
            registry.restore(snapshot);
            assertEquals(11, first.state); assertEquals(22, second.state);
            assertSame(effective.getGameService(Controller.class), adapters.getLast());
        }
    }

    public static final class ActualController implements com.openggf.game.mode.GameplayFrameController, RewindSnapshottable<Integer> {
        int value = 7;
        public String key() { return "mode"; }
        public Integer capture() { return value; }
        public void restore(Integer value) { this.value = value; }
        public boolean beforeTick(com.openggf.game.mode.CourseControl course, com.openggf.control.LogicalInputSnapshot input) { return true; }
        public void afterTick(com.openggf.game.mode.CourseControl course, boolean advanced) { }
    }
    @Test void actualSessionControllerUsesTheSameProxyAndStaysOutsideCourseRollback() throws Exception {
        var controller = new ActualController();
        Fixture fixture = fixture();
        GameModule base = new com.openggf.game.sonic2.Sonic2GameModule();
        GameModule module = wrap(base, new DelegatingGameModule(base, "controller") {
            @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() { return controller; }
            @Override public List<RewindSnapshottable<?>> rewindAdapters() { return List.of(controller); }
            @Override public <T> T getGameService(Class<T> type) {
                return type == com.openggf.game.mode.GameplayFrameController.class ? type.cast(controller) : super.getGameService(type);
            }
        }, fixture);
        var adapter = module.rewindAdapters().getFirst();
        assertSame(module.gameplayFrameController(), adapter);
        assertSame(module.getGameService(com.openggf.game.mode.GameplayFrameController.class), adapter);
        var registry = new com.openggf.game.rewind.RewindRegistry(null, module.gameplayFrameController());
        registry.register(adapter);
        var full = registry.capture();
        var course = registry.captureCourse();
        assertFalse(course.containsKey(adapter.key()));
        assertEquals(7, full.get(adapter.key()));
        controller.value = 99;
        registry.restoreCourse(course);
        assertEquals(99, controller.value);
        registry.restore(full);
        assertEquals(7, controller.value);
    }

    static final class ExpertEvents extends com.openggf.game.AbstractLevelEventManager {
        final Mode extra = new Mode(null);
        int reconciliations;
        @Override protected int getRoutineStride() { return 2; }
        @Override protected int getEventDataFgSize() { return 8; }
        @Override protected int getEventDataBgSize() { return 8; }
        @Override public com.openggf.game.PlayerCharacter getPlayerCharacter() { return com.openggf.game.PlayerCharacter.SONIC_ALONE; }
        @Override protected void onInitLevel(int zone,int act) { eventRoutineFg=0; }
        @Override protected void onUpdate() { eventRoutineFg++; }
        @Override public List<RewindSnapshottable<?>> extraRewindAdapters() { return List.of(extra); }
        @Override public void reconcileAfterRewindRestore() { reconciliations++; }
    }
    @Test void deferredInitializationActionsRetainOwnershipThroughTheRealLoaderAndOtherConsumers() throws Exception {
        Fixture fixture=fixture();
        var base=mock(GameModule.class);var inherited=mock(com.openggf.game.LevelInitProfile.class);
        when(base.getLevelInitProfile()).thenReturn(inherited);
        var profile=mock(com.openggf.game.LevelInitProfile.class);
        when(profile.levelLoadSteps(any())).thenReturn(List.of(new com.openggf.game.InitStep("CreatorLoad","original",()-> {
            assertEquals("registered-owner",OwnerCallbackScope.current());throw new IllegalStateException("deferred load");
        })));
        GameModule module=wrap(base,new DelegatingGameModule(base,"profile") {
            @Override public com.openggf.game.LevelInitProfile getLevelInitProfile() { return profile; }
        },fixture);
        var failure=assertThrows(java.io.IOException.class,()->com.openggf.level.DecodedLevelTransformAssertions.executeLoadProfile(module));
        var aborted=assertInstanceOf(ModFaultBoundary.CallbackAborted.class,failure.getCause());
        assertEquals("registered-owner",aborted.owner());assertEquals(Set.of("registered-owner","dependent"),fixture.disabled().get());
        for(String action:List.of("teardown","reset","fixup")) {
            Fixture other=fixture();var deferred=mock(com.openggf.game.LevelInitProfile.class);
            Runnable callback=()-> { assertEquals("registered-owner",OwnerCallbackScope.current());throw new IllegalStateException(action); };
            when(deferred.levelTeardownSteps()).thenReturn(List.of(new com.openggf.game.InitStep("Teardown","original",callback)));
            when(deferred.perTestResetSteps()).thenReturn(List.of(new com.openggf.game.InitStep("Reset","original",callback)));
            when(deferred.postTeardownFixups()).thenReturn(List.of(new com.openggf.game.StaticFixup("Fixup","original",callback)));
            var owned=wrap(base,new DelegatingGameModule(base,"deferred") {
                @Override public com.openggf.game.LevelInitProfile getLevelInitProfile() { return deferred; }
            },other).getLevelInitProfile();
            abort(other,action,()-> {
                if(action.equals("fixup")) owned.postTeardownFixups().forEach(com.openggf.game.StaticFixup::apply);
                else (action.equals("teardown")?owned.levelTeardownSteps():owned.perTestResetSteps()).forEach(com.openggf.game.InitStep::execute);
            });
        }
    }

    @Test void forwardedInitializationActionsKeepTheEarlierOwnersAttribution() throws Exception {
        Fixture fixture=fixture();var base=mock(GameModule.class);when(base.getLevelInitProfile()).thenReturn(mock(com.openggf.game.LevelInitProfile.class));
        var first=mock(com.openggf.game.LevelInitProfile.class);
        when(first.levelTeardownSteps()).thenReturn(List.of(new com.openggf.game.InitStep("Earlier","original",()-> {
            assertEquals("earlier-owner",OwnerCallbackScope.current());throw new IllegalStateException("earlier initialization");
        })));
        GameModule earlier=wrappedPatch("earlier-owner",ignored->new DelegatingGameModule(base,"earlier") {
            @Override public com.openggf.game.LevelInitProfile getLevelInitProfile() { return first; }
        },fixture).apply(base,null);
        var inherited=earlier.getLevelInitProfile().levelTeardownSteps().getFirst();
        var second=mock(com.openggf.game.LevelInitProfile.class);
        when(second.levelTeardownSteps()).thenReturn(List.of(new com.openggf.game.InitStep("Forwarded","creator label",inherited.action())));
        GameModule later=wrap(earlier,new DelegatingGameModule(earlier,"later") {
            @Override public com.openggf.game.LevelInitProfile getLevelInitProfile() { return second; }
        },fixture);
        var action=later.getLevelInitProfile().levelTeardownSteps().getFirst();assertSame(inherited.action(),action.action());
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,action::execute);
        assertEquals("earlier-owner",failure.owner());assertEquals(Set.of("earlier-owner"),fixture.disabled().get());
    }

    @Test void expertEventManagerKeepsPrimaryExtraStateAndReconciliationBehindItsOwnerProjection() throws Exception {
        Fixture fixture=fixture();
        var base=new com.openggf.game.sonic2.Sonic2GameModule();
        var events=new ExpertEvents();events.extra.suppliedKey="gamerng";events.extra.state=7;
        GameModule module=wrap(base,new DelegatingGameModule(base,"expert-events") {
            @Override public com.openggf.game.LevelEventProvider getLevelEventProvider() { return events; }
        },fixture);
        var provider=module.getLevelEventProvider();provider.initLevel(0,0);provider.update();
        var adapters=com.openggf.game.LevelEventRewindResolver.adapters(provider,0);
        assertEquals(2,adapters.size());
        var registry=new com.openggf.game.rewind.RewindRegistry();
        var host=new Mode(null);host.suppliedKey="gamerng";host.state=13;registry.register(host);
        adapters.forEach(registry::registerOrRefresh);
        var captured=registry.capture();provider.update();events.extra.state=99;
        registry.restore(captured);
        var expected=(com.openggf.game.rewind.snapshot.LevelEventSnapshot)captured.get(adapters.getFirst().key());
        var actual=events.capture();
        assertEquals(expected.currentZone(),actual.currentZone());assertEquals(expected.currentAct(),actual.currentAct());
        assertEquals(expected.eventRoutineFg(),actual.eventRoutineFg());assertEquals(expected.eventRoutineBg(),actual.eventRoutineBg());
        assertEquals(expected.frameCounter(),actual.frameCounter());assertEquals(expected.timerFrames(),actual.timerFrames());
        assertEquals(expected.bossActive(),actual.bossActive());assertArrayEquals(expected.eventDataFg(),actual.eventDataFg());
        assertArrayEquals(expected.eventDataBg(),actual.eventDataBg());assertArrayEquals(expected.extra(),actual.extra());
        assertEquals(7,events.extra.state);
        assertEquals(13,host.state);assertTrue(adapters.stream().allMatch(adapter->adapter.key().startsWith("mod:registered-owner:")));
        com.openggf.game.LevelEventRewindResolver.reconcile(provider,0);assertEquals(1,events.reconciliations);
    }

    public interface BridgeBinding { String bind(Object adapter, String owner, String localKey); }
    public interface PatchOwnershipBinding { GamePatch wrap(String owner,GamePatch patch,ModFaultBoundary boundary); }
    public interface StandaloneOwnershipBinding { GameModule wrap(String owner,GameModule module,ModFaultBoundary boundary); }
    public interface CallbackConstruction {
        com.openggf.mods.runtime.OwnerBoundCallbacks create(String owner, ModFaultBoundary boundary, int limit,
                java.util.Map<String,RewindSnapshottable<?>> adapters);
    }
    public static final class OwnershipProbe {
        public static BridgeBinding hiddenBinding() { return com.openggf.game.rewind.RewindAdapterOwnership::bind; }
        public static PatchOwnershipBinding hiddenPatchOwner() { return com.openggf.mods.runtime.OwnerBoundGamePatch::wrap; }
        public static StandaloneOwnershipBinding hiddenStandaloneOwner() { return com.openggf.mods.runtime.OwnerBoundGamePatch::wrapStandaloneRewinds; }
        public static CallbackConstruction hiddenCallbacks() { return com.openggf.mods.runtime.OwnerBoundCallbacks::new; }
        public static java.util.function.BiConsumer<com.openggf.game.session.WorldSession,List<RewindSnapshottable<?>>> hiddenInstaller() {
            return com.openggf.game.session.ModZoneRuntimeInstaller::install;
        }
        public static java.util.function.BiConsumer<com.openggf.game.patch.PatchEnablement,
                com.openggf.game.patch.ModuleResolutionService.PatchPlanSource> hiddenPlans(
                com.openggf.game.patch.ModuleResolutionService service) { return service::installModPlanSource; }
        public static String forge() {
            return com.openggf.game.rewind.RewindAdapterOwnership.bind(new Object(), "other-owner", "gamerng");
        }
        public static void erase(com.openggf.game.rewind.RewindRegistry registry) {
            registry.deregister("gamerng");
        }
        public static void preempt(com.openggf.game.rewind.RewindRegistry registry, RewindSnapshottable<?> adapter) {
            registry.registerOrRefresh(adapter);
        }
        public static void preemptByMethodReference(com.openggf.game.rewind.RewindRegistry registry, RewindSnapshottable<?> adapter) {
            List.of(adapter).forEach(registry::registerOrRefresh);
        }
        public static com.openggf.game.rewind.CompositeSnapshot privateRegistry(RewindSnapshottable<?> adapter) {
            var registry = new com.openggf.game.rewind.RewindRegistry();
            List.of(adapter).forEach(registry::registerOrRefresh);
            return registry.capture();
        }
        public static void replacePlans(com.openggf.game.patch.ModuleResolutionService service) {
            service.installModPlanSource(com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                    ignored -> com.openggf.game.patch.ModuleResolutionService.PatchPlan.empty());
        }
    }
    @Test void childLoadedCreatorCannotBindAnotherOwnersIdentityThroughThePublicEngineBridge() throws Exception {
        Path jar = temp.resolve("ownership-probe.jar");
        String name = OwnershipProbe.class.getName();
        String resource = name.replace('.', '/') + ".class";
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar));
             var bytes = getClass().getClassLoader().getResourceAsStream(resource)) {
            out.putNextEntry(new java.util.jar.JarEntry(resource));
            out.write(java.util.Objects.requireNonNull(bytes).readAllBytes());
            out.closeEntry();
        }
        ClassLoader engineParent = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (className.equals(name)) throw new ClassNotFoundException(className);
                return super.loadClass(className, resolve);
            }
        };
        try (var loader = new ModDependencyClassLoader("hostile", new java.net.URL[]{jar.toUri().toURL()}, engineParent, List.of())) {
            Class<?> probe = loader.loadClass(name);
            assertSame(loader, probe.getClassLoader());
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> probe.getMethod("forge").invoke(null));
            assertInstanceOf(SecurityException.class, failure.getCause());
            var hiddenBinding = (BridgeBinding)probe.getMethod("hiddenBinding").invoke(null);
            assertThrows(SecurityException.class, () -> hiddenBinding.bind(new Object(),"other-owner","mode"));
            var hiddenPatchOwner=(PatchOwnershipBinding)probe.getMethod("hiddenPatchOwner").invoke(null);
            assertThrows(SecurityException.class,()->hiddenPatchOwner.wrap("other-owner",patch(base->base),fixture().boundary()));
            var hiddenStandaloneOwner=(StandaloneOwnershipBinding)probe.getMethod("hiddenStandaloneOwner").invoke(null);
            assertThrows(SecurityException.class,()->hiddenStandaloneOwner.wrap("other-owner",new com.openggf.game.sonic2.Sonic2GameModule(),fixture().boundary()));
            PatchOwnershipBinding enginePatchOwner=com.openggf.mods.runtime.OwnerBoundGamePatch::wrap;
            assertDoesNotThrow(()->enginePatchOwner.wrap("engine-known",patch(base->base),fixture().boundary()));
            StandaloneOwnershipBinding engineStandaloneOwner=com.openggf.mods.runtime.OwnerBoundGamePatch::wrapStandaloneRewinds;
            assertDoesNotThrow(()->engineStandaloneOwner.wrap("engine-known",new com.openggf.game.sonic2.Sonic2GameModule(),fixture().boundary()));
            var hiddenCallbacks=(CallbackConstruction)probe.getMethod("hiddenCallbacks").invoke(null);
            assertThrows(SecurityException.class,()->hiddenCallbacks.create("other-owner",fixture().boundary(),10,Map.of()));
            @SuppressWarnings("unchecked") var hiddenInstaller=(java.util.function.BiConsumer<
                    com.openggf.game.session.WorldSession,List<RewindSnapshottable<?>>>)probe.getMethod("hiddenInstaller").invoke(null);
            assertThrows(SecurityException.class,()->hiddenInstaller.accept(null,List.of()));
            BridgeBinding engineBinding=com.openggf.game.rewind.RewindAdapterOwnership::bind;
            assertEquals("mod:engine-known:mode",engineBinding.bind(new Object(),"engine-known","mode"));
            CallbackConstruction engineCallbacks=com.openggf.mods.runtime.OwnerBoundCallbacks::new;
            assertDoesNotThrow(()->engineCallbacks.create("engine-known",fixture().boundary(),10,Map.of()));
            java.util.function.BiConsumer<com.openggf.game.session.WorldSession,List<RewindSnapshottable<?>>> engineInstaller=
                    com.openggf.game.session.ModZoneRuntimeInstaller::install;
            assertDoesNotThrow(()->engineInstaller.accept(null,List.of()));
            var registry = new com.openggf.game.rewind.RewindRegistry();
            Mode state = new Mode(null); state.suppliedKey = "gamerng"; state.state = 5;
            registry.register(state);
            var erase = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> probe.getMethod("erase", registry.getClass()).invoke(null, registry));
            assertInstanceOf(SecurityException.class, erase.getCause());
            var preempt = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> probe.getMethod("preempt", registry.getClass(), RewindSnapshottable.class)
                            .invoke(null, registry, state));
            assertInstanceOf(SecurityException.class, preempt.getCause());
            var reference = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> probe.getMethod("preemptByMethodReference", registry.getClass(), RewindSnapshottable.class)
                            .invoke(null, registry, state));
            assertInstanceOf(SecurityException.class, reference.getCause());
            assertEquals(5, registry.capture().get("gamerng"));
            var privateSnapshot = (com.openggf.game.rewind.CompositeSnapshot)probe
                    .getMethod("privateRegistry", RewindSnapshottable.class).invoke(null, state);
            assertEquals(5, privateSnapshot.get("gamerng"));
            var resolver = new com.openggf.game.patch.ModuleResolutionService(List.of(),
                    com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                    new com.openggf.game.patch.LogicalRomResolver(() -> null),
                    com.openggf.configuration.SonicConfigurationService.getInstance());
            var sourceReplacement = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> probe.getMethod("replacePlans", resolver.getClass()).invoke(null, resolver));
            assertInstanceOf(SecurityException.class, sourceReplacement.getCause());
            @SuppressWarnings("unchecked") var hiddenPlans=(java.util.function.BiConsumer<com.openggf.game.patch.PatchEnablement,
                    com.openggf.game.patch.ModuleResolutionService.PatchPlanSource>)probe.getMethod("hiddenPlans",resolver.getClass()).invoke(null,resolver);
            assertThrows(SecurityException.class,()->hiddenPlans.accept(com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                    ignored->com.openggf.game.patch.ModuleResolutionService.PatchPlan.empty()));
            java.util.function.BiConsumer<com.openggf.game.patch.PatchEnablement,
                    com.openggf.game.patch.ModuleResolutionService.PatchPlanSource> enginePlans=resolver::installModPlanSource;
            assertDoesNotThrow(() -> enginePlans.accept(
                    com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                    ignored -> com.openggf.game.patch.ModuleResolutionService.PatchPlan.empty()));
        }
    }

    @Test void freshAdapterForTheSameOwnedIdentityCanRefreshButReportedKeysCannot() throws Exception {
        Fixture fixture = fixture();
        GameModule base = new com.openggf.game.sonic2.Sonic2GameModule();
        Mode old = new Mode(null); old.suppliedKey = "counter"; old.state = 7;
        RewindSnapshottable<?> oldAdapter = wrap(base, new Patched(base, old, null), fixture)
                .rewindAdapters().getLast();
        var registry = new com.openggf.game.rewind.RewindRegistry();
        registry.registerOrRefresh(oldAdapter);
        long generation = registry.courseLayoutVersion();
        registry.registerOrRefresh(oldAdapter);
        assertEquals(generation, registry.courseLayoutVersion());
        var snapshot = registry.capture();
        Mode replacement = new Mode(null); replacement.suppliedKey = "counter"; replacement.state = 99;
        RewindSnapshottable<?> fresh = wrap(base, new Patched(base, replacement, null), fixture)
                .rewindAdapters().getLast();
        registry.registerOrRefresh(fresh);
        assertTrue(registry.courseLayoutVersion() > generation);
        registry.restore(snapshot);
        assertEquals(7, replacement.state);
        Mode hostile = new Mode(null); hostile.suppliedKey = fresh.key();
        assertThrows(IllegalStateException.class, () -> registry.registerOrRefresh(hostile));
        assertEquals(7, registry.capture().get(fresh.key()));
        replacement.suppliedKey = "gamerng";
        assertEquals(oldAdapter.key(), fresh.key(), "Identity is fixed when the engine binds the adapter");
    }

    @Test void invalidNewAdapterListFailsInsideItsOwnerBoundary() throws Exception {
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class);
        when(base.rewindAdapters()).thenReturn(List.of());
        GameModule wrapped = wrap(base, new DelegatingGameModule(base, "bad-list") {
            @Override public List<RewindSnapshottable<?>> rewindAdapters() { return null; }
        }, fixture);
        abort(fixture, "rewindAdapters must return a list", wrapped::rewindAdapters);
    }

    @Test void standaloneRewindListIsOwnerBoundedWithoutReplacingItsExistingProviderBoundary() throws Exception {
        Fixture fixture = fixture(); GameModule delegate = mock(GameModule.class); var mode = new Mode("restore");
        when(delegate.rewindAdapters()).thenReturn(List.of(mode));
        GameModule standalone = OwnerAwareStandaloneModule.wrap("registered-owner", delegate, fixture.boundary(), Map.of());
        Class<?> helper = Class.forName("com.openggf.mods.runtime.OwnerBoundGamePatch");
        GameModule wrapped = (GameModule)helper.getMethod("wrapStandaloneRewinds", String.class, GameModule.class, ModFaultBoundary.class)
                .invoke(null, "registered-owner", standalone, fixture.boundary());
        @SuppressWarnings("unchecked") var adapter = (RewindSnapshottable<Integer>)wrapped.rewindAdapters().getFirst();
        assertSame(adapter, wrapped.rewindAdapters().getFirst());
        abort(fixture, "restore", () -> adapter.restore(3));
    }

    public static final class RuntimeEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerGamePatch(patch(base -> new DelegatingGameModule(base, "registered-owner:runtime") {
                private final Mode mode = new Mode("capture");
                @Override public List<RewindSnapshottable<?>> rewindAdapters() { return List.of(mode); }
            }));
        }
    }
    public static final class RuntimeContentEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerObject("marker", (spawn, registry) -> null);
            new RuntimeEntrypoint().register(context);
        }
    }
    @Test void runtimeRegistrationInstallsBoundaryForExplicitPatchesWithoutContentBacking() throws Exception {
        runtimeRegistrationInstallsBoundary(RuntimeEntrypoint.class, 1);
    }
    @Test void runtimeRegistrationInstallsBoundaryForExplicitPatchesAfterContentBacking() throws Exception {
        runtimeRegistrationInstallsBoundary(RuntimeContentEntrypoint.class, 2);
    }
    void runtimeRegistrationInstallsBoundary(Class<? extends GgfMod> entrypoint, int registrationCount) throws Exception {
        Path assets = Files.createDirectory(temp.resolve("assets"));
        var snapshot = ModAssetRoot.snapshotDirectory(temp, assets, ModInputLimits.production(), DirectoryAccess.TEST);
        var manifest = new ModManifest(1, "registered-owner", "Owner", SemanticVersion.parse("1.0.0"), List.of("test"),
                "test", VersionRange.parse("*"), ModType.PATCH, "s2", entrypoint.getName(),
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        var descriptor = new ModDescriptor(assets, manifest, "0".repeat(64), true, List.of());
        var loader = new ModDependencyClassLoader("registered-owner", new java.net.URL[0], getClass().getClassLoader(), List.of());
        try (var runtime = new ModRuntime(Map.of("registered-owner", loader), Map.of("registered-owner", snapshot),
                Map.of("registered-owner", descriptor), Map.of())) {
            Fixture fixture = fixture(); runtime.installFaultBoundary(fixture.boundary());
            var plan = runtime.newRegistrationPlan();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            assertEquals(registrationCount, plan.registrations().size());
            var registration = plan.registrations().getLast();
            assertEquals(new PatchOwner.Mod("registered-owner"), registration.owner());
            GameModule wrapped = registration.patch().apply(mock(GameModule.class), null);
            abort(fixture, "capture", wrapped.rewindAdapters().getFirst()::capture);
        }
    }
}
