package com.openggf.game.patch;

import com.openggf.game.GameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestPatchResolutionAtBoot {

    private EngineContext previousEngineContext;

    interface PatchTrail {
        List<String> appliedPatchIds();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        if (previousEngineContext != null) {
            EngineServices.configure(previousEngineContext);
        }
    }

    @Test
    void worldSessionRetainsRootAndResolvedModuleIdentities() {
        GameModule root = new Sonic2GameModule();
        GameModule resolved = new DelegatingGameModule(root, "fake");

        SessionManager.openGameplaySession(root, resolved, null);

        assertSame(root, SessionManager.getCurrentWorldSession().rootGameModule());
        assertSame(resolved, SessionManager.getCurrentWorldSession().resolvedGameModule());
        assertSame(resolved, SessionManager.requireCurrentGameModule());
    }

    @Test
    void repeatedDataSelectResolutionAlwaysStartsFromRootAndNeverDoubleWraps() {
        RegisteredPatch first = new RegisteredPatch(new PatchOwner.BuiltIn("one"),
                "one", trailPatch("one"), 0);
        RegisteredPatch second = new RegisteredPatch(new PatchOwner.BuiltIn("two"),
                "two", trailPatch("two"), 1);
        ModuleResolutionService service = ModuleResolutionService.forTests(
                List.of(first, second), PatchEnablement.ALL_ENABLED);
        GameModule root = new Sonic2GameModule();
        GameplayLaunchRequest request = new GameplayLaunchRequest("s2", "knuckles", List.of());

        GameModule firstResolution = service.resolveForLaunch(root, request,
                ModuleResolutionService.LaunchPolicy.STANDARD);
        SessionManager.openGameplaySession(root, firstResolution, null);
        GameModule secondResolution = service.resolveForLaunch(
                SessionManager.getCurrentWorldSession().rootGameModule(), request,
                ModuleResolutionService.LaunchPolicy.STANDARD);
        GameModule changedTeamResolution = service.resolveForLaunch(
                SessionManager.getCurrentWorldSession().rootGameModule(),
                new GameplayLaunchRequest("s2", "sonic", List.of("tails")),
                ModuleResolutionService.LaunchPolicy.STANDARD);
        GameModule thirdResolution = service.resolveForLaunch(
                SessionManager.getCurrentWorldSession().rootGameModule(), request,
                ModuleResolutionService.LaunchPolicy.STANDARD);

        assertEquals(List.of("one", "two"), ((PatchTrail) firstResolution).appliedPatchIds());
        assertEquals(List.of("one", "two"), ((PatchTrail) secondResolution).appliedPatchIds());
        assertSame(root, changedTeamResolution);
        assertEquals(List.of("one", "two"), ((PatchTrail) thirdResolution).appliedPatchIds());
    }

    @Test
    void headlessBootSeamUsesInjectedResolverAndConfigRequest() {
        AtomicInteger scans = new AtomicInteger();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(
                        new RegisteredPatch(new PatchOwner.BuiltIn("one"),
                                "one", trailPatch("one"), 0),
                        new RegisteredPatch(new PatchOwner.BuiltIn("two"),
                                "two", trailPatch("two"), 1)),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                enablement -> {
                    scans.incrementAndGet();
                    return new ModuleResolutionService.PatchPlan(List.of(
                            new RegisteredPatch(new PatchOwner.Mod("probe"),
                                    "probe:mod", trailPatch("mod"), 0)), Map.of());
                });
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        EngineContext legacy = EngineServices.current();
        previousEngineContext = legacy;
        EngineContext injected = new EngineContext(config, legacy.graphics(), legacy.audio(),
                legacy.roms(), legacy.profiler(), legacy.debugOverlay(), legacy.playbackDebug(),
                legacy.romDetection(), legacy.crossGameFeatures(), service);
        EngineServices.configure(injected);
        GameModule root = new Sonic2GameModule();

        com.openggf.game.GameDataSource metadataSource = mock(com.openggf.game.GameDataSource.class);
        when(metadataSource.rom()).thenReturn(java.util.Optional.empty());
        HeadlessGameBoot.openResolvedSessionForBoot(injected, root,
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC, metadataSource);

        assertSame(root, SessionManager.getCurrentWorldSession().rootGameModule());
        assertEquals(List.of("one", "two"), ((PatchTrail) SessionManager.requireCurrentGameModule())
                .appliedPatchIds());
        assertEquals(0, scans.get(), "headless boot must not scan mod plans");

        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        HeadlessGameBoot.openResolvedSessionForBoot(injected, root,
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC, metadataSource);
        assertSame(root, SessionManager.requireCurrentGameModule());
    }

    @Test
    void deterministicPolicyDisablesModPatchesButKeepsBuiltIns() {
        RegisteredPatch builtIn = new RegisteredPatch(new PatchOwner.BuiltIn("builtin"),
                "builtin", trailPatch("builtin"), 0);
        RegisteredPatch mod = new RegisteredPatch(new PatchOwner.Mod("mod"),
                "mod:patch", trailPatch("mod"), 1);
        AtomicInteger scans = new AtomicInteger();
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        ModuleResolutionService service = new ModuleResolutionService(List.of(builtIn),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                enablement -> {
                    scans.incrementAndGet();
                    return new ModuleResolutionService.PatchPlan(List.of(mod), Map.of());
                });

        GameModule resolved = service.resolveForLaunch(new Sonic2GameModule(),
                new GameplayLaunchRequest("s2", "knuckles", List.of()),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);

        assertEquals(List.of("builtin"), ((PatchTrail) resolved).appliedPatchIds());
        assertEquals(0, scans.get(), "deterministic launch must not scan external patch plans");
    }

    @Test
    void preparedLaunchFreezesOnePlanForAvailabilityAndResolution() {
        RegisteredPatch mod = new RegisteredPatch(new PatchOwner.Mod("mod"),
                "mod:patch", trailPatch("mod"), 0);
        AtomicInteger scans = new AtomicInteger();
        ModuleResolutionService service = new ModuleResolutionService(List.of(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null),
                SonicConfigurationService.createStandalone(), enablement ->
                        scans.getAndIncrement() == 0
                                ? new ModuleResolutionService.PatchPlan(List.of(mod), Map.of())
                                : ModuleResolutionService.PatchPlan.empty());

        ModuleResolutionService.PreparedLaunch launch = service.prepareLaunch(
                ModuleResolutionService.LaunchPolicy.STANDARD);

        assertEquals(List.of("knuckles"),
                service.availableMainCharacters(launch, "s2"));
        GameModule resolved = service.resolveForLaunch(launch, new Sonic2GameModule(),
                new GameplayLaunchRequest("s2", "knuckles", List.of()));
        assertEquals(List.of("mod"), ((PatchTrail) resolved).appliedPatchIds());
        assertEquals(1, scans.get());
    }

    @Test
    void sonicRequestReturnsTheSameBaseInstance() {
        ModuleResolutionService service = ModuleResolutionService.forTests(
                List.of(new RegisteredPatch(new PatchOwner.BuiltIn("fake"),
                        "fake", trailPatch("fake"), 0)), PatchEnablement.ALL_ENABLED);
        GameModule base = new Sonic2GameModule();

        GameModule resolved = service.resolveForLaunch(base,
                new GameplayLaunchRequest("s2", "sonic", List.of("tails")),
                ModuleResolutionService.LaunchPolicy.STANDARD);

        assertSame(base, resolved);
    }

    private static GamePatch trailPatch(String id) {
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
                List<String> trail = new ArrayList<>(base instanceof PatchTrail current
                        ? current.appliedPatchIds() : List.of());
                trail.add(id);
                class FakePatchedModule extends DelegatingGameModule implements PatchTrail {
                    FakePatchedModule() { super(base, id); }
                    @Override public List<String> appliedPatchIds() { return List.copyOf(trail); }
                }
                return new FakePatchedModule();
            }
        };
    }
}
