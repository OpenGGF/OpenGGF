package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.game.CharacterKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TestModContextAndFaultBoundary {
    @Test void finalDecodedTransformRunsOnceForNativeOverrideAndPreparedInstalls() throws Exception {
        com.openggf.level.DecodedLevelTransformAssertions.verifyAllLoadPaths();
    }

    @Test
    void productionResolverAppliesSoftOrderButDoesNotDisableOptionalNeighborsOnFault() {
        var applied = new ArrayList<String>();
        var a = new com.openggf.game.patch.PatchOwner.Mod("a");
        var b = new com.openggf.game.patch.PatchOwner.Mod("b");
        java.util.function.Function<String, GamePatch> patch = owner -> new GamePatch() {
            public String id() { return owner+":patch"; } public String displayName() { return owner; }
            public String baseGameId() { return "s2"; } public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            public Set<LogicalRom> romPrerequisites() { return Set.of(); } public List<String> providedMainCharacters() { return List.of(); }
            public GameModule apply(GameModule base, PatchContext context) { applied.add(owner); return base; }
        };
        var plan = new com.openggf.game.patch.ModuleResolutionService.PatchPlan(List.of(
                new com.openggf.game.patch.RegisteredPatch(b,"b:patch",patch.apply("b"),0),
                new com.openggf.game.patch.RegisteredPatch(a,"a:patch",patch.apply("a"),1)), Map.of(), Map.of(b,Set.of(a)));
        var resolver = new com.openggf.game.patch.ModuleResolutionService(List.of(),
                com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                new com.openggf.game.patch.LogicalRomResolver(() -> null),
                com.openggf.configuration.SonicConfigurationService.getInstance(), ignored -> plan);
        resolver.resolveForLaunch(new com.openggf.game.sonic2.Sonic2GameModule(),
                new GameplayLaunchRequest("s2","sonic",List.of()),com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD);
        assertEquals(List.of("a","b"),applied);
        var cycle = new com.openggf.game.patch.ModuleResolutionService.PatchPlan(plan.registrations(),Map.of(),Map.of(a,Set.of(b),b,Set.of(a)));
        var cyclic = new com.openggf.game.patch.ModuleResolutionService(List.of(),com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                new com.openggf.game.patch.LogicalRomResolver(() -> null),com.openggf.configuration.SonicConfigurationService.getInstance(),ignored -> cycle);
        assertThrows(IllegalArgumentException.class, () -> cyclic.prepareLaunch(com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD));
        var missing = new com.openggf.game.patch.ModuleResolutionService.PatchPlan(plan.registrations(),Map.of(),Map.of(a,Set.of(new com.openggf.game.patch.PatchOwner.Mod("missing"))));
        var optional = new com.openggf.game.patch.ModuleResolutionService(List.of(),com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                new com.openggf.game.patch.LogicalRomResolver(() -> null),com.openggf.configuration.SonicConfigurationService.getInstance(),ignored -> missing);
        assertDoesNotThrow(() -> optional.prepareLaunch(com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD));
        GamePatch faulting = new GamePatch() {
            public String id() { return "a:bad"; } public String displayName() { return "a"; }
            public String baseGameId() { return "s2"; }
            public boolean activatesFor(GameplayLaunchRequest request) { throw new IllegalStateException("optional neighbor callback"); }
            public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            public List<String> providedMainCharacters() { return List.of(); }
            public GameModule apply(GameModule base, PatchContext context) { fail("faulting owner cannot apply"); return base; }
        };
        var faultPlan = new com.openggf.game.patch.ModuleResolutionService.PatchPlan(List.of(
                new com.openggf.game.patch.RegisteredPatch(a,"a:bad",faulting,0),
                new com.openggf.game.patch.RegisteredPatch(b,"b:patch",patch.apply("b"),1)),Map.of(),Map.of(b,Set.of(a)));
        var isolated = new com.openggf.game.patch.ModuleResolutionService(List.of(),com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                new com.openggf.game.patch.LogicalRomResolver(() -> null),com.openggf.configuration.SonicConfigurationService.getInstance(),ignored -> faultPlan);
        var launch = isolated.prepareLaunch(com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD);
        applied.clear();
        var resolved = assertInstanceOf(com.openggf.game.patch.ResolutionResult.Resolved.class,
                isolated.resolveForLaunchResult(launch,new com.openggf.game.sonic2.Sonic2GameModule(),
                        new GameplayLaunchRequest("s2","sonic",List.of())));
        assertEquals(List.of("b"),applied);
        assertEquals(Set.of(a),resolved.ownerFailures().keySet());
    }

    public static final class SameNameState { public SameNameState() { } }
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void concreteServicesWithIdenticalBinaryNamesFromDifferentOwnersStayIndependent(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        String name = SameNameState.class.getName(); String resource = name.replace('.','/')+".class";
        java.nio.file.Path jar=temp.resolve("state.jar");
        try(var output=new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar));
            var bytes=getClass().getClassLoader().getResourceAsStream(resource)) {
            output.putNextEntry(new java.util.jar.JarEntry(resource)); output.write(java.util.Objects.requireNonNull(bytes).readAllBytes()); output.closeEntry();
        }
        ClassLoader engineParent=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if(requested.equals(name)) throw new ClassNotFoundException(requested); return super.loadClass(requested,resolve);
            }
        };
        try(var firstLoader=new ModDependencyClassLoader("first",new java.net.URL[]{jar.toUri().toURL()},engineParent,List.of());
            var secondLoader=new ModDependencyClassLoader("second",new java.net.URL[]{jar.toUri().toURL()},engineParent,List.of())) {
            Class first=firstLoader.loadClass(name), second=secondLoader.loadClass(name);
            assertEquals(first.getName(),second.getName()); assertNotSame(first,second);
            Object firstState=first.getConstructor().newInstance(), secondState=second.getConstructor().newInstance();
            var a=new ModContext("first","s2",ModAssetRoot.forTests("first"));a.registerService("state",first,firstState);
            var b=new ModContext("second","s2",ModAssetRoot.forTests("second"));b.registerService("state",second,secondState);
            var aPlan=a.freeze(); var bPlan=b.freeze(); var boundary=testBoundary(new ModRuntimeFindingStore());
            GameModule module=OwnedModServices.decorate(OwnedModServices.decorate(new com.openggf.game.sonic2.Sonic2GameModule(),aPlan,boundary),bPlan,boundary);
            assertSame(firstState,module.getGameService(first));assertSame(secondState,module.getGameService(second));
            var report=ModContributionReport.build(new java.util.LinkedHashMap<>(Map.of("first",aPlan,"second",bPlan)),Map.of());
            assertEquals(Set.of("service-bundle:first:state","service-bundle:second:state"),
                    report.targets().stream().map(ModContributionReport.Target::contribution).collect(java.util.stream.Collectors.toSet()));
            assertTrue(report.targets().stream().allMatch(target -> target.winner()==null && target.shadowedOwners().size()==1));
        }
    }

    public interface CounterService { int increment(); }
    static final class CapturedController implements com.openggf.game.mode.GameplayFrameController,
            com.openggf.game.rewind.RewindSnapshottable<Integer>, CounterService {
        int value;
        public String key() { return "gamerng"; }
        public Integer capture() { return value; }
        public void restore(Integer state) { value = state; }
        public int increment() { assertEquals("owner", OwnerCallbackScope.current()); return ++value; }
        public boolean beforeTick(com.openggf.game.mode.CourseControl course, com.openggf.control.LogicalInputSnapshot input) { increment(); return true; }
        public void afterTick(com.openggf.game.mode.CourseControl course, boolean advanced) { }
    }
    private static ModFaultBoundary testBoundary(ModRuntimeFindingStore findings) {
        return new ModFaultBoundary(Map.of("dependent", Set.of("owner")), findings,
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
    }
    @Test
    void applicationBundlesShareControllerIdentityPreserveConcreteQueriesAndRefreshRewind() {
        var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        var made = new ArrayList<CapturedController>();
        context.registerServiceBundle("session", () -> {
            var controller = new CapturedController(); made.add(controller);
            return com.openggf.game.GameServiceBundle.builder().frameController("controller", controller)
                    .service(CapturedController.class, controller).service(CounterService.class, controller).build();
        });
        var plan = context.freeze();
        var boundary = testBoundary(new ModRuntimeFindingStore());
        GameModule base = org.mockito.Mockito.mock(GameModule.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        GameModule nativeBase = new com.openggf.game.sonic2.Sonic2GameModule();
        var backing = new ModBackedGamePatch(plan, boundary);
        var registration = new com.openggf.game.patch.RegisteredPatch(new com.openggf.game.patch.PatchOwner.Mod("owner"),backing.id(),backing,0);
        var resolver = new com.openggf.game.patch.ModuleResolutionService(List.of(),com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                new com.openggf.game.patch.LogicalRomResolver(() -> null),com.openggf.configuration.SonicConfigurationService.getInstance(),
                ignored -> new com.openggf.game.patch.ModuleResolutionService.PatchPlan(List.of(registration),Map.of()));
        GameModule first = resolver.resolveForLaunch(nativeBase,new GameplayLaunchRequest("s2","sonic",List.of()),
                com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD);
        assertSame(made.getFirst(), first.getGameService(CapturedController.class));
        var firstController=(com.openggf.game.rewind.RewindSnapshottable<?>)first.gameplayFrameController();
        assertTrue(first.rewindAdapters().stream().anyMatch(adapter->adapter==firstController));
        assertSame(first.gameplayFrameController(), first.getGameService(CounterService.class));
        assertEquals(1, first.getGameService(CounterService.class).increment());
        com.openggf.game.session.EngineServices.configure(com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        var gameplay = new com.openggf.game.session.GameplayModeContext(new com.openggf.game.session.WorldSession(first));
        var rng = new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2);
        gameplay.attachGameplayManagers(new com.openggf.camera.Camera(),new com.openggf.timer.TimerManager(),
                new com.openggf.game.GameStateManager(),new com.openggf.graphics.FadeManager(),rng,new com.openggf.game.solid.DefaultSolidExecutionRegistry());
        var registry = gameplay.getRewindRegistry();
        var captured = registry.capture();
        var course = registry.captureCourse();
        assertFalse(course.containsKey(firstController.key()), "Actual session course partition omits the live shared mode controller");
        assertTrue(course.containsKey("gamerng"), "Course-owned stock state is retained");
        first.getGameService(CounterService.class).increment();
        registry.restore(captured); assertEquals(1, made.getFirst().value);
        GameModule second = resolver.resolveForLaunch(nativeBase,new GameplayLaunchRequest("s2","sonic",List.of()),
                com.openggf.game.patch.ModuleResolutionService.LaunchPolicy.STANDARD);
        assertEquals(2, made.size()); assertNotSame(first.gameplayFrameController(), second.gameplayFrameController());
        var secondController=(com.openggf.game.rewind.RewindSnapshottable<?>)second.gameplayFrameController();
        registry.registerOrRefresh(secondController);
        registry.restore(captured); assertEquals(1, made.getLast().value);
        assertEquals("mod:owner:services/session/controller", secondController.key());
    }
    @Test void newCapturedBundleCannotRepublishAnotherOwnersBoundControllerOrAdapter() {
        var context=new ModContext("owner","s2",ModAssetRoot.forTests("owner"));
        context.registerServiceBundle("original",()->com.openggf.game.GameServiceBundle.builder()
                .frameController("controller",new CapturedController()).build());
        GameModule original=OwnedModServices.decorate(new com.openggf.game.sonic2.Sonic2GameModule(),context.freeze(),
                testBoundary(new ModRuntimeFindingStore()));
        var foreign=(com.openggf.game.rewind.RewindSnapshottable<?>)original.gameplayFrameController();
        var boundary=new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),ignored->{});
        var thief=new ModContext("thief","s2",ModAssetRoot.forTests("thief"));
        thief.registerServiceBundle("stolen",()->com.openggf.game.GameServiceBundle.builder()
                .capturedService("controller",com.openggf.game.rewind.RewindSnapshottable.class,foreign).build());
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->OwnedModServices.decorate(original,thief.freeze(),boundary));
        assertEquals("thief",failure.owner());assertEquals(Set.of("thief","dependent"),failure.disabledOwners());
        assertFalse(failure.disabledOwners().contains("owner"));
        assertSame(foreign,original.gameplayFrameController());
        var inherited=new com.openggf.game.patch.DelegatingGameModule(original,"thief:forward-only") { };
        assertSame(foreign,inherited.gameplayFrameController(),"Inherited forwarding retains the original identity");
        var runtimeBoundary=new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),ignored->{});
        var runtimeFactory=new OwnedModZoneRuntimeFactory("thief","zone",ignored->
                com.openggf.game.modzone.ModZoneRuntimeServices.builder().rewindAdapter("stolen",foreign).build(),runtimeBoundary);
        var runtimeFailure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->runtimeFactory.create(
                new com.openggf.game.modzone.ModZoneRuntimeContext(new com.openggf.game.ZoneKey.Mod("thief","zone"),"s2",3,0,
                        org.mockito.Mockito.mock(com.openggf.level.Level.class))));
        assertEquals("thief",runtimeFailure.owner());assertFalse(runtimeFailure.disabledOwners().contains("owner"));
        var raw=new CapturedController();
        var rawContext=new ModContext("owner","s2",ModAssetRoot.forTests("owner"));
        rawContext.registerServiceBundle("concrete",()->com.openggf.game.GameServiceBundle.builder()
                .capturedService("state",CapturedController.class,raw).build());
        GameModule rawOriginal=OwnedModServices.decorate(new com.openggf.game.sonic2.Sonic2GameModule(),rawContext.freeze(),
                testBoundary(new ModRuntimeFindingStore()));
        assertSame(raw,rawOriginal.getGameService(CapturedController.class));
        var rawAdapter=rawOriginal.rewindAdapters().stream()
                .filter(adapter->adapter.key().equals("mod:owner:services/concrete/state")).findFirst().orElseThrow();
        assertNotSame(raw,rawAdapter);
        var rawThief=new ModContext("thief","s2",ModAssetRoot.forTests("thief"));
        rawThief.registerServiceBundle("stolen-raw",()->com.openggf.game.GameServiceBundle.builder()
                .capturedService("state",CapturedController.class,raw).build());
        var rawFailure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->OwnedModServices.decorate(rawOriginal,rawThief.freeze(),
                new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                        ignored->new ModStateSaveResult.Saved(),ignored->{})));
        assertEquals("thief",rawFailure.owner());assertEquals(Set.of("thief","dependent"),rawFailure.disabledOwners());
    }
    @Test
    void bundleAndDynamicProviderFailuresDisableActualOwnerAndDependents() {
        for (String failureAt : List.of("factory", "dynamic", "duplicate")) {
            var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
            context.registerServiceBundle("session", () -> {
                if (failureAt.equals("factory")) throw new IllegalStateException("factory");
                return com.openggf.game.GameServiceBundle.builder().dynamicService(CounterService.class,
                        () -> { throw new IllegalStateException("dynamic"); }).build();
            });
            if (failureAt.equals("duplicate")) context.registerServiceBundle("other", () ->
                    com.openggf.game.GameServiceBundle.builder().service(CounterService.class, () -> 1).build());
            var plan = context.freeze();
            var boundary = testBoundary(new ModRuntimeFindingStore());
            var base = org.mockito.Mockito.mock(GameModule.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            Runnable consumer = failureAt.equals("dynamic")
                    ? () -> OwnedModServices.decorate(base, plan, boundary).getGameService(CounterService.class)
                    : () -> OwnedModServices.decorate(base, plan, boundary);
            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, consumer::run);
            assertEquals("owner", failure.owner()); assertEquals(Set.of("owner", "dependent"), failure.disabledOwners());
        }
    }
    @Test
    void storageUsesInjectedRootAndDecodedTemplateBindsRegisteredLocalFactory() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("owned-mod-context");
        var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"), null, false, root);
        context.storage().write("settings.json", "{}") ;
        assertEquals("{}", java.nio.file.Files.readString(root.resolve("mods/owner/settings.json")));
        context.registerObject("spring", (spawn, services) -> null);
        context.decodedLevelPatch("placements", com.openggf.level.LevelPatch.empty()
                .select(spawn -> spawn.objectId() == 3).bind("spring"));
        var plan = context.freeze();
        GameModule base = org.mockito.Mockito.mock(GameModule.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        GameModule decorated = OwnedModServices.decorate(base, plan, testBoundary(new ModRuntimeFindingStore()));
        com.openggf.level.Level source = org.mockito.Mockito.mock(com.openggf.level.Level.class);
        org.mockito.Mockito.when(source.getObjects()).thenReturn(List.of(new com.openggf.level.objects.ObjectSpawn(10, 20, 3, 0, 0, false, 20, 5, null, null)));
        org.mockito.Mockito.when(source.getRings()).thenReturn(List.of());
        org.mockito.Mockito.when(source.getMap()).thenReturn(new com.openggf.level.Map(2, 1, 1));
        var transformed = decorated.transformDecodedLevel(source);
        assertEquals("owner", transformed.getObjects().getFirst().ownerModId());
        assertEquals("owner:spring", transformed.getObjects().getFirst().objectKey());
        assertNull(source.getObjects().getFirst().ownerModId());
    }

    @Test
    void standaloneModuleRegistrationIsExclusiveExactlyOnceAndAtomic() {
        GameModule module = org.mockito.Mockito.mock(GameModule.class);
        org.mockito.Mockito.when(module.getGameId()).thenReturn(com.openggf.game.GameId.STANDALONE);
        org.mockito.Mockito.when(module.getIdentifier()).thenReturn("owner");
        org.mockito.Mockito.when(module.getGameCode()).thenReturn("owner");

        ModContext missing = new ModContext(
                "owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class, missing::freeze);

        ModContext valid = new ModContext(
                "owner", null, ModAssetRoot.forTests("owner"), null, true);
        valid.registerGameModule(module);
        ModRegistrationPlan plan = valid.freeze();
        assertSame(module, plan.standaloneModule());
        assertNull(plan.baseGameId());

        ModContext duplicate = new ModContext(
                "owner", null, ModAssetRoot.forTests("owner"), null, true);
        duplicate.registerGameModule(module);
        ModRegistrationException collision = assertThrows(ModRegistrationException.class,
                () -> duplicate.registerGameModule(module));
        assertSame(collision, assertThrows(ModRegistrationException.class, duplicate::freeze));

        ModContext patch = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class, () -> patch.registerGameModule(module));
    }

    @Test
    void transactionNamespacesContentRejectsDuplicatesAndFreezesAtomically() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerObject("buzzer", (spawn, registry) -> null);
        context.registerObjectArt("buzzer", new BakedSheetRef("art/buzzer.ggfsheet"));
        context.registerGamePatch(new Patch("extra", "s2"));

        ModRegistrationPlan plan = context.freeze();
        assertEquals(Set.of("owner:buzzer"), plan.objectFactories().keySet());
        assertEquals(Set.of("owner:buzzer"), plan.objectArt().keySet());
        assertEquals(1, plan.explicitPatches().size());
        assertEquals("owner:extra", plan.explicitPatches().get(0).id());
        assertThrows(ModRegistrationException.class,
                () -> context.registerObject("later", (spawn, registry) -> null));

        ModContext badBase = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class,
                () -> badBase.registerGamePatch(new Patch("extra", "s1")));
        ModContext duplicate = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        duplicate.registerObject("same", (spawn, registry) -> null);
        assertThrows(ModRegistrationException.class,
                () -> duplicate.registerObject("same", (spawn, registry) -> null));
        assertThrows(ModRegistrationException.class,
                () -> duplicate.registerGamePatch(new Patch("other:foreign", "s2")));
    }

    @Test
    void firstRejectedMutationPermanentlyPoisonsTransactionAndPlanKeepsInsertionOrder() {
        ModContext poisoned = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        poisoned.registerObject("first", (spawn, registry) -> null);
        poisoned.registerObject("second", (spawn, registry) -> null);
        ModRegistrationException firstFailure = assertThrows(ModRegistrationException.class,
                () -> poisoned.registerObject("first", (spawn, registry) -> null));
        assertSame(firstFailure, assertThrows(ModRegistrationException.class,
                () -> poisoned.registerObjectArt("art", new BakedSheetRef("art/a.bin"))));
        assertSame(firstFailure, assertThrows(ModRegistrationException.class, poisoned::freeze));

        ModContext ordered = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        ordered.registerObject("z-last", (spawn, registry) -> null);
        ordered.registerObject("a-first", (spawn, registry) -> null);
        ordered.registerObjectArt("z-last", new BakedSheetRef("art/z.bin"));
        ordered.registerObjectArt("a-first", new BakedSheetRef("art/a.bin"));
        ModRegistrationPlan plan = ordered.freeze();
        assertEquals(List.of("owner:z-last", "owner:a-first"),
                new ArrayList<>(plan.objectFactories().keySet()));
        assertEquals(List.of("owner:z-last", "owner:a-first"),
                new ArrayList<>(plan.objectArt().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.objectFactories().clear());
    }

    @Test
    void characterCollisionPoisonsTransactionAndCannotPublishPartialRegistry() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        var key = CharacterKey.mod("owner", "runner");
        var definition = new com.openggf.game.CharacterDefinition(key, "Runner",
                (code, x, y) -> null, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false, code -> null);
        context.registerCharacter("runner", definition);
        ModRegistrationException collision = assertThrows(ModRegistrationException.class,
                () -> context.registerCharacter("runner", definition));
        assertSame(collision, assertThrows(ModRegistrationException.class, context::freeze));
        GameModule base = org.mockito.Mockito.mock(GameModule.class);
        org.mockito.Mockito.when(base.getPlayableCharacterRegistry())
                .thenReturn(com.openggf.game.PlayableCharacterRegistry.empty());
        assertTrue(base.getPlayableCharacterRegistry().definitions().isEmpty(),
                "A poisoned transaction never reaches patch publication");
    }

    @Test
    void ownerTransactionEnforcesTheAssetRootsCollectionBudgetBeforePublishing() {
        var limits = com.openggf.io.ModInputLimits.loweringBuilder().maxCollectionEntries(1).build();
        var context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner", limits));
        context.registerObject("first", (spawn, registry) -> null);
        ModRegistrationException rejected = assertThrows(ModRegistrationException.class,
                () -> context.registerObject("second", (spawn, registry) -> null));
        assertTrue(rejected.getMessage().contains("registration limit"));
        assertSame(rejected, assertThrows(ModRegistrationException.class, context::freeze));
        var acts = new ModContext("owner", "s2", ModAssetRoot.forTests("owner", limits));
        var authored = ModZoneContribution.multiAct("campaign",List.of(
                new BakedLevelRef("levels/first/level.json"),new BakedLevelRef("levels/second/level.json")),null,null,false);
        var tooMany = assertThrows(ModRegistrationException.class, () -> acts.registerZone(authored));
        assertTrue(tooMany.getMessage().contains("authored-act limit"));
        assertSame(tooMany,assertThrows(ModRegistrationException.class,acts::freeze));
    }

    @Test
    void registeredPatchMetadataReachesCharacterDiscoveryAndOptionalRomLookup() {
        var key = CharacterKey.mod("owner", "runner");
        var definition = new com.openggf.game.CharacterDefinition(key, "Runner",
                (code, x, y) -> null, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false, code -> null);
        GamePatch original = new GamePatch() {
            public String id() { return "metadata"; }
            public String displayName() { return "Metadata"; }
            public String baseGameId() { return "s2"; }
            public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            public Set<LogicalRom> optionalRomPrerequisites() { return Set.of(LogicalRom.S1); }
            public List<String> providedMainCharacters() { return List.of(key.persisted()); }
            public Map<CharacterKey, com.openggf.game.CharacterDefinition> providedCharacterDefinitions() {
                return Map.of(key, definition);
            }
            public GameModule apply(GameModule base, PatchContext patchContext) { return base; }
        };
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerGamePatch(original);
        GamePatch registered = context.freeze().explicitPatches().getFirst();
        var owner = new com.openggf.game.patch.PatchOwner.Mod("owner");
        var resolution = com.openggf.game.patch.ResolutionContext.forTests(
                com.openggf.game.patch.PatchEnablement.ALL_ENABLED,
                List.of(new com.openggf.game.patch.RegisteredPatch(owner, registered.id(), registered, 0)),
                Map.of());
        var resolver = com.openggf.game.patch.ModuleResolutionService.forTests(
                com.openggf.game.patch.PatchEnablement.ALL_ENABLED);
        assertSame(definition, resolver.availableMainCharacterRegistry(resolution, "s2").find(key).orElseThrow());
        assertEquals(Set.of(LogicalRom.S1), registered.optionalRomPrerequisites());
    }

    @Test
    void namespacedPatchDeclaresEveryGamePatchMethodIncludingDefaults() throws Exception {
        Class<?> namespaced = Class.forName(ModContext.class.getName() + "$NamespacedPatch");
        for (var method : GamePatch.class.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            assertDoesNotThrow(() -> namespaced.getDeclaredMethod(method.getName(), method.getParameterTypes()),
                    "Namespaced patch must forward " + method);
        }
    }

    @Test
    void callbackFailureDisablesClosurePublishesFindingAndThrowsTypedAbort() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> persisted = new ArrayList<>();
        List<Set<String>> processDisabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("owner"), "transitive", Set.of("dependent")),
                findings, owners -> { persisted.add(owners); return new ModStateSaveResult.Saved(); },
                processDisabled::add);

        IllegalArgumentException cause = new IllegalArgumentException("boom");
        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("owner", () -> { throw cause; }));
        assertSame(cause, aborted.getCause());
        assertEquals(Set.of("owner", "dependent", "transitive"), aborted.disabledOwners());
        assertEquals(List.of(aborted.disabledOwners()), persisted);
        assertEquals(persisted, processDisabled);
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").get(0).code());
    }

    @Test
    void ownerDerivedCharacterAndStandaloneBoundariesPreserveDirectAndAbortSignals() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> saved = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("owner")), findings,
                owners -> { saved.add(owners); return new ModStateSaveResult.Saved(); }, owners -> {});
        assertEquals("builtin", boundary.callCharacter(CharacterKey.SONIC, () -> "builtin"));
        var characterAbort = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.callCharacter(CharacterKey.mod("owner", "runner"),
                        () -> { throw new IllegalStateException("character"); }));
        assertEquals(Set.of("owner", "dependent"), characterAbort.disabledOwners());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
        var standaloneAbort = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.callStandalone("standalone", () -> { throw new IllegalStateException("game"); }));
        assertEquals(Set.of("standalone"), standaloneAbort.disabledOwners());
        assertEquals(List.of(Set.of("owner", "dependent"), Set.of("standalone")), saved);
    }

    @Test
    void saveFailureIsSuppressedAndVmFatalFailuresEscapeWithoutSideEffects() {
        AtomicBoolean disabled = new AtomicBoolean();
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Failed("disk full"), owners -> disabled.set(true));
        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("owner", () -> { throw new IllegalStateException("callback"); }));
        assertTrue(disabled.get());
        assertEquals(1, aborted.getCause().getSuppressed().length);
        assertEquals(List.of("MOD_CALLBACK_FAILED", "MOD_DISABLE_SAVE_FAILED"),
                findings.findingsFor("owner").stream().map(com.openggf.mods.ModFinding::code).toList());

        disabled.set(false);
        assertThrows(OutOfMemoryError.class,
                () -> boundary.run("owner", () -> { throw new OutOfMemoryError("fatal"); }));
        assertFalse(disabled.get());
    }

    @Test
    void callbackAndSaveFailureFindingsUpsertByStableCodeWithoutClobberingDiagnostics() {
        for (boolean throwFromSave : List.of(false, true)) {
            ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
            findings.replaceOwner("owner", List.of(new com.openggf.mods.ModFinding(
                    com.openggf.mods.ModFindingSeverity.WARNING, "EXISTING", "keep", null)));
            ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                    owners -> {
                        if (throwFromSave) throw new IllegalStateException("disk exception");
                        return new ModStateSaveResult.Failed("disk full");
                    }, owners -> { });

            assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> boundary.run("owner", () -> { throw new IllegalStateException("first"); }));
            assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> boundary.run("owner", () -> { throw new IllegalStateException("second"); }));

            assertEquals(List.of("EXISTING", "MOD_CALLBACK_FAILED", "MOD_DISABLE_SAVE_FAILED"),
                    findings.findingsFor("owner").stream()
                            .map(com.openggf.mods.ModFinding::code).toList());
            assertEquals(3, findings.findingsFor("owner").size());
        }
    }

    @Test
    void runtimeFindingUpsertPreservesUnrelatedOwnerBadges() {
        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        store.replaceOwner("owner", List.of(new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "EXISTING", "existing warning", null)));
        store.upsertOwnerFinding("owner", new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "S2_MOD_ZONE_MISSING", "missing", null));
        store.upsertOwnerFinding("owner", new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "S2_MOD_ZONE_MISSING", "updated", null));

        assertEquals(List.of("EXISTING", "S2_MOD_ZONE_MISSING"),
                store.findingsFor("owner").stream().map(com.openggf.mods.ModFinding::code).toList());
        assertEquals("updated", store.findingsFor("owner").getLast().message());
    }

    private record Patch(String id, String baseGameId) implements GamePatch {
        @Override public String displayName() { return id; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
    }
}
