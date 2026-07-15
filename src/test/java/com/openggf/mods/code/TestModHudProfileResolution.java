package com.openggf.mods.code;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameplayPolicyProvider;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
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
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModHudProfileResolution {
    private static final ZoneKey.Mod FLAPPY =
            (ZoneKey.Mod) ZoneKey.mod("alpha", "flappy");
    private static final HudProfile FLAPPY_PROFILE = new HudProfile(List.of(
            new HudRow(true, HudLabel.SCORE, HudMetric.RINGS,
                    16, 40, 64, 40, 3, HudWarningPolicy.NONE)));

    @Test
    void modBackedProviderPublishesOwnedHudProfileAndPreservesInheritedStockPolicy() {
        ModRegistrationPlan plan = new ModRegistrationPlan(
                "alpha", "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(), List.of(), Map.of(), Map.of(), null, Map.of(),
                Map.of(), Map.of(), Map.of(FLAPPY, FLAPPY_PROFILE));
        GameplayPolicyProvider inherited = new GameplayPolicyProvider() {
            @Override
            public Optional<HudProfile> hudProfile(ZoneKey destination) {
                return destination instanceof ZoneKey.Stock
                        ? Optional.of(HudProfile.stock()) : Optional.empty();
            }
        };
        GameModule base = mock(GameModule.class);
        when(base.getGameplayPolicyProvider()).thenReturn(inherited);

        GameModule resolved = new ModBackedGamePatch(plan, boundary())
                .apply(base, mock(PatchContext.class));

        assertEquals(FLAPPY_PROFILE,
                resolved.getGameplayPolicyProvider().hudProfile(FLAPPY).orElseThrow());
        assertEquals(HudProfile.stock(), resolved.getGameplayPolicyProvider()
                .hudProfile(ZoneKey.stock(0)).orElseThrow());
    }

    @Test
    void levelLoadResolvesAfterPublicationAndStockLoadResetsTheProfile() throws Exception {
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(module.getZoneRegistry()).thenReturn(zones);
        when(zones.zoneKey(1)).thenReturn(FLAPPY);
        when(zones.zoneKey(0)).thenReturn(ZoneKey.stock(0));
        Level loaded = mock(Level.class);
        when(loaded.getBlockPixelSize()).thenReturn(128);
        when(loaded.getChunksPerBlockSide()).thenReturn(8);
        when(loaded.getLayerWidthBlocks(org.mockito.ArgumentMatchers.anyByte())).thenReturn(1);
        when(loaded.getLayerHeightBlocks(org.mockito.ArgumentMatchers.anyByte())).thenReturn(1);
        when(module.loadLevelOverride(org.mockito.ArgumentMatchers.anyInt())).thenReturn(loaded);

        WorldSession world = new WorldSession(module);
        world.setCurrentZone(1);
        GameplayPolicyProvider policies = new GameplayPolicyProvider() {
            @Override
            public Optional<HudProfile> hudProfile(ZoneKey destination) {
                assertSame(loaded, world.getCurrentLevel(),
                        "HUD policy must resolve after the level is published");
                return Optional.of(FLAPPY_PROFILE);
            }
        };
        when(module.getGameplayPolicyProvider()).thenReturn(policies);
        LevelManager manager = manager(world);
        setField(manager, "gameModule", module);
        HudRenderManager hud = new HudRenderManager(
                mock(GraphicsManager.class), mock(Camera.class), mock(GameStateManager.class));
        setField(manager, "hudRenderManager", hud);

        manager.loadLevelData(0x400);
        assertEquals(FLAPPY_PROFILE, HudProfileAccess.current(hud));

        world.setCurrentZone(0);
        setField(manager, "currentZone", 0);
        manager.loadLevelData(0);
        assertEquals(HudProfile.stock(), HudProfileAccess.current(hud));
    }

    private static LevelManager manager(WorldSession world) {
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

    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
