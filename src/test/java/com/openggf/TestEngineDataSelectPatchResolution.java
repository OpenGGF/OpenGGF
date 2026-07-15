package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.RomDetectionService;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.openggf.game.timeattack.TimeAttackLaunchRequest;
import com.openggf.game.patch.DeterministicPatchLaunches;

class TestEngineDataSelectPatchResolution {

    @TempDir
    Path saveRoot;

    private EngineContext previousEngineContext;

    interface PatchTrail {
        List<String> ids();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        if (previousEngineContext != null) {
            EngineServices.configure(previousEngineContext);
        }
    }

    @Test
    void dataSelectTeamChangeAlwaysReresolvesTwoPatchStackFromWorldRoot() {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        List<RegisteredPatch> builtIns = List.of(
                new RegisteredPatch(new PatchOwner.BuiltIn("one"), "one", patch("one"), 0),
                new RegisteredPatch(new PatchOwner.BuiltIn("two"), "two", patch("two"), 1));
        ModuleResolutionService resolver = new ModuleResolutionService(builtIns,
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config);
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        GameModule root = new Sonic2GameModule();
        GameModule initiallyResolved = resolver.resolveForLaunch(root,
                new GameplayLaunchRequest("s2", "knuckles", List.of()),
                ModuleResolutionService.LaunchPolicy.STANDARD);
        SessionManager.openGameplaySession(root, initiallyResolved, null);

        ModuleResolutionService.PreparedLaunch sonicLaunch = resolver.prepareLaunch(
                ModuleResolutionService.LaunchPolicy.STANDARD);
        engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                new SelectedTeam("sonic", List.of("tails")), 0, 0),
                List.of("sonic", "tails", "knuckles"), sonicLaunch);
        assertSame(root, SessionManager.requireCurrentGameModule());

        ModuleResolutionService.PreparedLaunch knucklesLaunch = resolver.prepareLaunch(
                ModuleResolutionService.LaunchPolicy.STANDARD);
        engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                new SelectedTeam("knuckles", List.of()), 0, 0),
                List.of("sonic", "tails", "knuckles"), knucklesLaunch);
        assertSame(root, SessionManager.getCurrentWorldSession().rootGameModule());
        assertEquals(List.of("one", "two"),
                ((PatchTrail) SessionManager.requireCurrentGameModule()).ids());
    }

    @Test
    void destinationPolicyReplacesOnlyTheLaunchContextTeamButSavesTheDurableTeam() throws Exception {
        LaunchHarness harness = launchHarness(new GameplayLaunchTeam(CharacterKey.TAILS, List.of()));
        SaveSessionContext durable = SaveSessionContext.forSlot("s2", 2,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);

        var gameplay = harness.engine().openDataSelectPatchSession(durable,
                List.of("sonic", "tails", "knuckles"), harness.prepared());

        SaveSessionContext launch = gameplay.getWorldSession().getSaveSessionContext();
        assertEquals(new SelectedTeam("tails", List.of()), launch.selectedTeam());
        assertEquals(new SelectedTeam("sonic", List.of("tails")), durable.selectedTeam());
        assertEquals("sonic", harness.config().getString(
                com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails", harness.config().getString(
                com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE));

        SaveManager saves = new SaveManager(saveRoot);
        launch.requestSave(SaveReason.NEW_SLOT_START,
                RuntimeSaveContext.forGameplayMode(null, launch),
                gameplay.getWorldSession().getGameModule().getSaveSnapshotProvider(), saves);
        var payload = saves.readSlotSummary("s2", 2).payload();
        assertEquals("sonic", payload.get("mainCharacter"));
        assertEquals(List.of("tails"), payload.get("sidekicks"));
    }

    @Test
    void engineLaunchFaultBoundsInheritedPolicyByDestinationOwner() {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        ZoneKey.Mod destination = (ZoneKey.Mod) ZoneKey.mod("alpha", "sky");
        GameModule tagged = rootWithDestination(destination);
        GameModule throwingRoot = new DelegatingGameModule(tagged, "throwing-policy") {
            @Override public com.openggf.game.GameplayPolicyProvider getGameplayPolicyProvider() {
                return new com.openggf.game.GameplayPolicyProvider() {
                    @Override
                    public java.util.Optional<GameplayLaunchTeam> launchTeam(ZoneKey ignored) {
                        throw new IllegalStateException("policy callback failed");
                    }
                };
            }
        };
        ModRegistrationPlan plan = new ModRegistrationPlan(
                "alpha", "s2", Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of(),
                Map.of(destination, com.openggf.level.objects.HudProfile.stock()));
        ZoneKey.Mod betaDestination = (ZoneKey.Mod) ZoneKey.mod("beta", "other");
        ModRegistrationPlan outerPlan = new ModRegistrationPlan(
                "beta", "s2", Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of(),
                Map.of(betaDestination, com.openggf.level.objects.HudProfile.stock()));
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> persisted = new ArrayList<>();
        List<Set<String>> processDisabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("alpha")), findings,
                owners -> { persisted.add(owners); return new ModStateSaveResult.Saved(); },
                processDisabled::add);
        GamePatch content = new ModBackedGamePatch(plan, boundary);
        GamePatch outerContent = new ModBackedGamePatch(outerPlan, boundary);
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                ignored -> new ModuleResolutionService.PatchPlan(List.of(
                        new RegisteredPatch(new PatchOwner.Mod("alpha"),
                                "alpha:content", content, 0),
                        new RegisteredPatch(new PatchOwner.Mod("beta"),
                                "beta:content", outerContent, 1)), Map.of()));
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        SessionManager.openGameplaySession(throwingRoot, throwingRoot, null);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                                new SelectedTeam("sonic", List.of("tails")), 0, 0),
                        List.of("sonic", "tails", "knuckles"),
                        resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD)));

        assertEquals("alpha", aborted.owner());
        assertEquals(Set.of("alpha", "dependent"), aborted.disabledOwners());
        assertEquals(List.of(aborted.disabledOwners()), persisted);
        assertEquals(persisted, processDisabled);
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("alpha").getFirst().code());
    }

    @Test
    void missingContributedCharacterAbortsInsteadOfFallingBack() {
        LaunchHarness harness = launchHarness(new GameplayLaunchTeam(
                CharacterKey.mod("missing", "hero"), List.of()));
        SaveSessionContext durable = SaveSessionContext.noSave("s2",
                new SelectedTeam("sonic", List.of("tails")), 0, 0);

        assertThrows(ModRegistrationException.class,
                () -> harness.engine().openDataSelectPatchSession(durable,
                        List.of("sonic", "tails", "knuckles"), harness.prepared()));
        assertEquals(new SelectedTeam("sonic", List.of("tails")), durable.selectedTeam());
    }

    @Test
    void engineInitializationAndRecordingRestartUseBehavioralResolutionSeams() {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                "knuckles");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                "tails");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.TEST_MODE_ENABLED, true);
        AtomicInteger scans = new AtomicInteger();
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(
                new RegisteredPatch(new PatchOwner.BuiltIn("one"), "one", patch("one"), 0)),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                enablement -> {
                    scans.incrementAndGet();
                    return new ModuleResolutionService.PatchPlan(List.of(
                            new RegisteredPatch(new PatchOwner.Mod("probe"),
                                    "probe:mod", patch("mod"), 0)), java.util.Map.of());
                });
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        GameModule root = new Sonic2GameModule();

        assertEquals(List.of("one"),
                ((PatchTrail) engine.resolveInitialModuleForLaunch(root)).ids());
        GameModule recordingModule = DeterministicPatchLaunches.forRecording(resolver, root,
                new RecordingLaunchContext("s2", 0, 0, "knuckles", List.of("tails"),
                        false, "test"));
        assertEquals(List.of("one"), ((PatchTrail) recordingModule).ids());
        GameModule timeAttackModule = engine.resolveTimeAttackModuleForLaunch(root,
                new TimeAttackLaunchRequest("s2", 0, 0, "knuckles", List.of()));
        assertEquals(List.of("one"), ((PatchTrail) timeAttackModule).ids());
        assertEquals(0, scans.get(),
                "deterministic Engine/recording/time-attack seams must not scan mod plans");
    }

    @Test
    void initialAndDataSelectCreatorApplyAbortsAreConsumedBeforeSessionReplacement() {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                "knuckles");
        GamePatch throwing = patch("throwing", true);
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                enablement -> new ModuleResolutionService.PatchPlan(List.of(
                        new RegisteredPatch(new PatchOwner.Mod("throwing"), "throwing:patch",
                                throwing, 0)), java.util.Map.of()));
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        AtomicInteger aborted = new AtomicInteger();
        engine.setPatchLaunchAbortHandlerForTests(ignored -> aborted.incrementAndGet());
        GameModule root = new Sonic2GameModule();

        assertNull(engine.resolveInitialModuleForLaunch(root));
        assertEquals(1, aborted.get());
        SessionManager.openGameplaySession(root, root, null);
        var before = SessionManager.getCurrentGameplayMode();
        assertNull(engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                        new SelectedTeam("knuckles", List.of()), 0, 0), List.of("sonic", "knuckles"),
                resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD)));
        assertSame(before, SessionManager.getCurrentGameplayMode(),
                "aborted data-select resolution must not replace the live session");
        assertEquals(2, aborted.get());
    }

    @Test
    void builtInApplyFailuresRemainProgrammerErrorsAndNeverReplaceDataSelectSession() {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                "knuckles");
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(
                new RegisteredPatch(new PatchOwner.BuiltIn("throwing"), "throwing",
                        patch("throwing", true), 0)), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        AtomicInteger consumed = new AtomicInteger();
        engine.setPatchLaunchAbortHandlerForTests(ignored -> consumed.incrementAndGet());
        GameModule root = new Sonic2GameModule();

        IllegalStateException initial = assertThrows(IllegalStateException.class,
                () -> engine.resolveInitialModuleForLaunch(root));
        assertEquals("creator mutated then failed", initial.getCause().getMessage());
        assertEquals(0, consumed.get());

        SessionManager.openGameplaySession(root, root, null);
        var before = SessionManager.getCurrentGameplayMode();
        IllegalStateException dataSelect = assertThrows(IllegalStateException.class,
                () -> engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                                new SelectedTeam("knuckles", List.of()), 0, 0),
                        List.of("sonic", "knuckles"), resolver.prepareLaunch(
                                ModuleResolutionService.LaunchPolicy.STANDARD)));
        assertEquals("creator mutated then failed", dataSelect.getCause().getMessage());
        assertSame(before, SessionManager.getCurrentGameplayMode());
        assertEquals(0, consumed.get());
    }

    private static GamePatch patch(String id) {
        return patch(id, false);
    }

    private static GamePatch patch(String id, boolean throwOnApply) {
        return new GamePatch() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) {
                return "knuckles".equals(request.mainCharacter());
            }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of("knuckles"); }
            @Override public GameModule apply(GameModule base, PatchContext context) {
                if (throwOnApply) throw new IllegalStateException("creator mutated then failed");
                List<String> trail = new ArrayList<>(
                        base instanceof PatchTrail current ? current.ids() : List.of());
                trail.add(id);
                class Patched extends DelegatingGameModule implements PatchTrail {
                    Patched() { super(base, id); }
                    @Override public List<String> ids() { return List.copyOf(trail); }
                }
                return new Patched();
            }
        };
    }

    private LaunchHarness launchHarness(GameplayLaunchTeam launchTeam) {
        previousEngineContext = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                "sonic");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                "tails");
        ZoneKey.Mod destination = (ZoneKey.Mod) ZoneKey.mod("alpha", "sky");
        GameModule root = rootWithDestination(destination);
        ModRegistrationPlan plan = new ModRegistrationPlan(
                "alpha", "s2", Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), null, Map.of(), Map.of(destination, launchTeam),
                Map.of(), Map.of());
        GamePatch content = new ModBackedGamePatch(plan, noOpBoundary());
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                ignored -> new ModuleResolutionService.PatchPlan(List.of(
                        new RegisteredPatch(new PatchOwner.Mod("alpha"),
                                "alpha:content", content, 0)), Map.of()));
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        SessionManager.openGameplaySession(root, root, null);
        return new LaunchHarness(engine, config,
                resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD));
    }

    private static GameModule rootWithDestination(ZoneKey.Mod destination) {
        GameModule stockModule = new Sonic2GameModule();
        ZoneRegistry stock = stockModule.getZoneRegistry();
        ZoneRegistry tagged = (ZoneRegistry) java.lang.reflect.Proxy.newProxyInstance(
                TestEngineDataSelectPatchResolution.class.getClassLoader(),
                new Class<?>[]{ZoneRegistry.class}, (proxy, method, args) -> {
                    if (method.getName().equals("zoneKey") && (int) args[0] == 0) {
                        return destination;
                    }
                    if (method.getName().equals("resolveZoneKey") && destination.equals(args[0])) {
                        return java.util.OptionalInt.of(0);
                    }
                    try {
                        return method.invoke(stock, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        return new DelegatingGameModule(stockModule, "tagged-destination") {
            @Override public ZoneRegistry getZoneRegistry() { return tagged; }
        };
    }

    private static ModFaultBoundary noOpBoundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private record LaunchHarness(Engine engine, SonicConfigurationService config,
                                 ModuleResolutionService.PreparedLaunch prepared) {
    }
}
