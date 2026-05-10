package com.openggf.game;


import com.openggf.game.session.EngineServices;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

/**
 * Thin service locator for non-object code.
 * <p>
 * Gameplay-scoped managers resolve through
 * {@link SessionManager#getCurrentGameplayMode()}; engine globals (audio, ROM,
 * config, debug overlay, graphics, ROM detection, cross-game features) stay
 * behind the engine-services root. The bonus stage provider is gameplay-mode
 * state and is exposed through {@link GameplayModeContext}.
 * <p>
 * Object instances should use {@code services()} instead of this class.
 */
public final class GameServices {

    private GameServices() {
    }

    private static GameplayModeContext requireGameplayMode(String accessor) {
        GameplayModeContext mode = SessionManager.getCurrentGameplayMode();
        if (mode == null || mode.getCamera() == null) {
            throw new IllegalStateException(
                    "GameServices." + accessor + "() requires an active gameplay mode. "
                    + "Open a gameplay session and attach managers before accessing gameplay-scoped managers.");
        }
        return mode;
    }


    /**
     * Returns {@code true} when a gameplay mode is active and core managers are
     * attached. Unified with {@link #gameplayModeOrNull()} so callers that guard
     * on {@code hasRuntime()} and read via {@code gameplayModeOrNull()} (or any
     * of the {@code *OrNull()} accessors) see consistent results across
     * editor transitions that can clear gameplay mode state while
     * session-owned state is being rebuilt.
     */
    public static boolean hasRuntime() {
        return gameplayModeOrNull() != null;
    }

    private static GameplayModeContext gameplayModeOrNull() {
        GameplayModeContext mode = SessionManager.getCurrentGameplayMode();
        return mode != null && mode.getCamera() != null ? mode : null;
    }

    public static GameModule bootstrapGameModule() {
        return GameModuleRegistry.getBootstrapDefault();
    }

    public static GameModule currentOrBootstrapGameModule() {
        WorldSession world = SessionManager.getCurrentWorldSession();
        if (world != null && world.getGameModule() != null) {
            return world.getGameModule();
        }
        return bootstrapGameModule();
    }

    public static Camera cameraOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getCamera() : null;
    }

    public static LevelManager levelOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getLevelManager() : null;
    }

    public static GameStateManager gameStateOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getGameStateManager() : null;
    }

    public static TimerManager timersOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getTimerManager() : null;
    }

    public static GameRng rngOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getRng() : null;
    }

    public static ParallaxManager parallaxOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getParallaxManager() : null;
    }

    public static FadeManager fadeOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getFadeManager() : null;
    }

    public static SpriteManager spritesOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getSpriteManager() : null;
    }

    public static CollisionSystem collisionOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getCollisionSystem() : null;
    }

    public static TerrainCollisionManager terrainCollisionOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getTerrainCollisionManager() : null;
    }

    public static WaterSystem waterOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getWaterSystem() : null;
    }

    public static AnimatedTileChannelGraph animatedTileChannelGraphOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getAnimatedTileChannelGraph() : null;
    }

    public static ZoneLayoutMutationPipeline zoneLayoutMutationPipelineOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getZoneLayoutMutationPipeline() : null;
    }

    public static SpecialRenderEffectRegistry specialRenderEffectRegistryOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getSpecialRenderEffectRegistry() : null;
    }

    public static AdvancedRenderModeController advancedRenderModeControllerOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getAdvancedRenderModeController() : null;
    }

    public static BonusStageProvider bonusStageOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getActiveBonusStageProvider() : null;
    }

    public static SolidExecutionRegistry solidExecutionRegistryOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getSolidExecutionRegistry() : null;
    }

    // â”€â”€ Gameplay-scoped managers (resolve through SessionManager) â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Global camera accessor for non-object code (HUD, level loading, rendering).
     * Object instances should use {@code services().camera()} instead.
     */
    public static Camera camera() {
        return requireGameplayMode("camera").getCamera();
    }

    public static LevelManager level() {
        return requireGameplayMode("level").getLevelManager();
    }

    /**
     * Global game state accessor for non-object code.
     * Object instances should use {@code services().gameState()} instead.
     */
    public static GameStateManager gameState() {
        return requireGameplayMode("gameState").getGameStateManager();
    }

    public static TimerManager timers() {
        return requireGameplayMode("timers").getTimerManager();
    }

    public static GameRng rng() {
        return requireGameplayMode("rng").getRng();
    }

    public static FadeManager fade() {
        return requireGameplayMode("fade").getFadeManager();
    }

    public static SpriteManager sprites() {
        return requireGameplayMode("sprites").getSpriteManager();
    }

    public static CollisionSystem collision() {
        return requireGameplayMode("collision").getCollisionSystem();
    }

    public static TerrainCollisionManager terrainCollision() {
        return requireGameplayMode("terrainCollision").getTerrainCollisionManager();
    }

    public static ParallaxManager parallax() {
        return requireGameplayMode("parallax").getParallaxManager();
    }

    public static WaterSystem water() {
        return requireGameplayMode("water").getWaterSystem();
    }

    public static AnimatedTileChannelGraph animatedTileChannelGraph() {
        return requireGameplayMode("animatedTileChannelGraph").getAnimatedTileChannelGraph();
    }

    public static ZoneLayoutMutationPipeline zoneLayoutMutationPipeline() {
        return requireGameplayMode("zoneLayoutMutationPipeline").getZoneLayoutMutationPipeline();
    }

    public static SpecialRenderEffectRegistry specialRenderEffectRegistry() {
        return requireGameplayMode("specialRenderEffectRegistry").getSpecialRenderEffectRegistry();
    }

    public static AdvancedRenderModeController advancedRenderModeController() {
        return requireGameplayMode("advancedRenderModeController").getAdvancedRenderModeController();
    }

    public static WorldSession worldSession() {
        WorldSession ws = SessionManager.getCurrentWorldSession();
        if (ws == null) {
            throw new IllegalStateException(
                    "GameServices.worldSession() requires an active WorldSession. "
                    + "Open one via SessionManager.openGameplaySession() before accessing world data.");
        }
        return ws;
    }

    public static GameModule module() {
        return worldSession().getGameModule();
    }

    /**
     * Returns the active bonus stage provider. Returns {@link NoOpBonusStageProvider}
     * when not in a bonus stage. Objects call this to signal stage completion
     * via {@link BonusStageProvider#requestExit()}.
     */
    public static BonusStageProvider bonusStage() {
        return requireGameplayMode("bonusStage").getActiveBonusStageProvider();
    }

    public static SolidExecutionRegistry solidExecutionRegistry() {
        return requireGameplayMode("solidExecutionRegistry").getSolidExecutionRegistry();
    }

    public static ZoneRuntimeRegistry zoneRuntimeRegistry() {
        return requireGameplayMode("zoneRuntimeRegistry").getZoneRuntimeRegistry();
    }

    public static ZoneRuntimeRegistry zoneRuntimeRegistryOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getZoneRuntimeRegistry() : null;
    }

    public static ZoneRuntimeState zoneRuntimeState() {
        return zoneRuntimeRegistry().current();
    }

    public static PaletteOwnershipRegistry paletteOwnershipRegistry() {
        return requireGameplayMode("paletteOwnershipRegistry").getPaletteOwnershipRegistry();
    }

    public static PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() {
        GameplayModeContext mode = gameplayModeOrNull();
        return mode != null ? mode.getPaletteOwnershipRegistry() : null;
    }
    //â”€â”€ Engine globals (stay as direct singleton calls) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static RomManager rom() {
        return EngineServices.current().roms();
    }

    public static DebugOverlayManager debugOverlay() {
        return EngineServices.current().debugOverlay();
    }

    public static AudioManager audio() {
        return EngineServices.current().audio();
    }

    public static SonicConfigurationService configuration() {
        return EngineServices.current().configuration();
    }

    public static GraphicsManager graphics() {
        return EngineServices.current().graphics();
    }

    public static PerformanceProfiler profiler() {
        return EngineServices.current().profiler();
    }

    public static PlaybackDebugManager playbackDebug() {
        return EngineServices.current().playbackDebug();
    }

    public static RomDetectionService romDetection() {
        return EngineServices.current().romDetection();
    }

    public static CrossGameFeatureProvider crossGameFeatures() {
        return EngineServices.current().crossGameFeatures();
    }
}
