package com.openggf.mods.code;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.GameplayPolicyProvider;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.HudLabel;
import com.openggf.level.objects.HudMetric;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudProfileAccess;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.HudRow;
import com.openggf.level.objects.HudWarningPolicy;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModGameplayPolicyFaultBoundary {
    private static final ZoneKey.Mod FLAPPY =
            (ZoneKey.Mod) ZoneKey.mod("alpha", "flappy");
    private static final ZoneKey.Mod OTHER =
            (ZoneKey.Mod) ZoneKey.mod("alpha", "other");
    private static final HudProfile FLAPPY_HUD = new HudProfile(List.of(
            new HudRow(true, HudLabel.SCORE, HudMetric.RINGS,
                    16, 40, 64, 40, 3, HudWarningPolicy.NONE)));

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void throwingRequiredFilterOnFirstGameplayFrameDisablesClosurePersistsAndAborts() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> persisted = new ArrayList<>();
        List<Set<String>> processDisabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("alpha")), findings,
                owners -> {
                    persisted.add(owners);
                    return new ModStateSaveResult.Saved();
                }, processDisabled::add);
        GameplayInputFilter throwing = raw -> {
            throw new IllegalStateException("boom");
        };
        GameModule resolved = resolvedModule(plan(Map.of(FLAPPY, throwing)),
                GameplayPolicyProvider.EMPTY, boundary);
        GameplayInputFilter installed = resolved.getGameplayPolicyProvider()
                .inputFilter(FLAPPY).orElseThrow();
        assertInstanceOf(OwnerAwareGameplayInputFilter.class, installed);

        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        GameplayModeContext gameplay = TestEnvironment.activeGameplayMode();
        GameplayInputFilterAccess.install(gameplay, installed);
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.neutral(), PlayerInputState.neutral()));

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> new SpriteManager().publishHeldInputForLevelEvents(input));

        assertEquals(Set.of("alpha", "dependent"), aborted.disabledOwners());
        assertEquals(List.of(aborted.disabledOwners()), persisted);
        assertEquals(persisted, processDisabled);
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("alpha").getFirst().code());
    }

    @Test
    void requiredLaunchAndHudProviderFailuresAbortInsteadOfFallingBack() {
        GameplayPolicyProvider throwing = new GameplayPolicyProvider() {
            @Override
            public Optional<com.openggf.game.GameplayLaunchTeam> launchTeam(ZoneKey destination) {
                throw new IllegalStateException("launch");
            }

            @Override
            public Optional<HudProfile> hudProfile(ZoneKey destination) {
                throw new IllegalStateException("hud");
            }
        };

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> resolvedModule(plan(Map.of(FLAPPY, GameplayInputFilter.IDENTITY)),
                        throwing, boundary()).getGameplayPolicyProvider().launchTeam(FLAPPY));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> resolvedModule(plan(Map.of(FLAPPY, GameplayInputFilter.IDENTITY)),
                        throwing, boundary()).getGameplayPolicyProvider().hudProfile(FLAPPY));
    }

    @Test
    void inheritedRawFilterIsNormalizedBeforeExecutionAndFaultBoundToDestinationOwner() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> persisted = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> {
                    persisted.add(owners);
                    return new ModStateSaveResult.Saved();
                }, owners -> { });
        GameplayPolicyProvider inherited = new GameplayPolicyProvider() {
            @Override
            public Optional<GameplayInputFilter> inputFilter(ZoneKey destination) {
                return Optional.of(raw -> {
                    throw new IllegalStateException("inherited filter");
                });
            }
        };
        // The current patch contributes at OTHER, forcing FLAPPY to flow through the inherited
        // provider branch while still exercising a real content-backed decorator.
        GameModule resolved = resolvedModule(
                plan(Map.of(OTHER, GameplayInputFilter.IDENTITY)), inherited, boundary);

        GameplayInputFilter filter = resolved.getGameplayPolicyProvider()
                .inputFilter(FLAPPY).orElseThrow();
        assertInstanceOf(OwnerAwareGameplayInputFilter.class, filter);
        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> filter.filter(PlayerInputState.neutral()));

        assertEquals("alpha", aborted.owner());
        assertEquals(List.of(Set.of("alpha")), persisted);
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("alpha").getFirst().code());
    }

    @Test
    void policyBackedPatchCannotPublishWithoutAnInstalledFaultBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModBackedGamePatch(
                        plan(Map.of(FLAPPY, GameplayInputFilter.IDENTITY))));
    }

    @Test
    void boundarylessContentPatchPreservesEmptyInheritedGameplayPolicies() {
        GameModule resolved = resolvedModule(contentOnlyPlan(), GameplayPolicyProvider.EMPTY, null);

        assertEquals(Optional.empty(), resolved.getGameplayPolicyProvider().launchTeam(FLAPPY));
        assertEquals(Optional.empty(), resolved.getGameplayPolicyProvider().inputFilter(FLAPPY));
        assertEquals(Optional.empty(), resolved.getGameplayPolicyProvider().hudProfile(FLAPPY));
    }

    @Test
    void boundarylessContentPatchRejectsRawInheritedFilterBeforeInvocation() {
        AtomicBoolean invoked = new AtomicBoolean();
        GameplayInputFilter raw = input -> {
            invoked.set(true);
            return input;
        };
        GameplayPolicyProvider inherited = new GameplayPolicyProvider() {
            @Override
            public Optional<GameplayInputFilter> inputFilter(ZoneKey destination) {
                return Optional.of(raw);
            }
        };
        GameModule resolved = resolvedModule(contentOnlyPlan(), inherited, null);

        assertThrows(IllegalStateException.class,
                () -> resolved.getGameplayPolicyProvider().inputFilter(FLAPPY));
        assertFalse(invoked.get(), "A raw inherited filter must be rejected before execution");
    }

    @Test
    void destroyingContextClearsSessionFilterAndInstalledHudBeforeFreshStockSession() throws Exception {
        GameModule module = mock(GameModule.class);
        WorldSession world = new WorldSession(module);
        GameplayModeContext first = new GameplayModeContext(world);
        GameplayInputFilter customFilter = raw -> PlayerInputState.neutral();
        GameplayInputFilterAccess.install(first, customFilter);

        LevelManager firstLevels = levelManager(world);
        HudRenderManager firstHud = hudRenderManager();
        HudProfileAccess.install(firstHud, FLAPPY_HUD);
        setField(firstLevels, "hudRenderManager", firstHud);
        setField(firstLevels, "activeHudProfile", FLAPPY_HUD);
        first.attachLevelManagers(mock(WaterSystem.class), mock(ParallaxManager.class),
                mock(TerrainCollisionManager.class), mock(CollisionSystem.class),
                mock(SpriteManager.class), firstLevels);

        first.destroy();

        assertSame(GameplayInputFilter.IDENTITY, GameplayInputFilterAccess.current(first));
        assertEquals(HudProfile.stock(), HudProfileAccess.current(firstHud));

        GameplayModeContext stock = new GameplayModeContext(new WorldSession(module));
        HudRenderManager stockHud = hudRenderManager();
        assertSame(GameplayInputFilter.IDENTITY, GameplayInputFilterAccess.current(stock));
        assertEquals(HudProfile.stock(), HudProfileAccess.current(stockHud));
    }

    private static ModRegistrationPlan plan(Map<ZoneKey.Mod, GameplayInputFilter> filters) {
        return new ModRegistrationPlan(
                "alpha", "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(), List.of(), Map.of(), Map.of(), null, Map.of(),
                Map.of(), filters, Map.of());
    }

    private static ModRegistrationPlan contentOnlyPlan() {
        return new ModRegistrationPlan(
                "alpha", "s3k", Map.of("alpha:dummy", mock(com.openggf.level.objects.ObjectFactory.class)),
                Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), null,
                Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static GameModule resolvedModule(ModRegistrationPlan plan,
                                             GameplayPolicyProvider inherited,
                                             ModFaultBoundary boundary) {
        GameModule base = mock(GameModule.class);
        when(base.getGameplayPolicyProvider()).thenReturn(inherited);
        return new ModBackedGamePatch(plan, boundary).apply(base, mock(PatchContext.class));
    }

    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static LevelManager levelManager(WorldSession world) {
        EngineContext services = mock(EngineContext.class);
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        when(services.graphics()).thenReturn(mock(GraphicsManager.class));
        when(services.audio()).thenReturn(mock(AudioManager.class));
        when(services.configuration()).thenReturn(configuration);
        when(services.debugOverlay()).thenReturn(mock(DebugOverlayManager.class));
        when(services.profiler()).thenReturn(mock(PerformanceProfiler.class));
        when(services.crossGameFeatures()).thenReturn(mock(CrossGameFeatureProvider.class));
        return new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class),
                mock(WaterSystem.class), mock(GameStateManager.class), services, world);
    }

    private static HudRenderManager hudRenderManager() {
        return new HudRenderManager(
                mock(GraphicsManager.class), mock(Camera.class), mock(GameStateManager.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
