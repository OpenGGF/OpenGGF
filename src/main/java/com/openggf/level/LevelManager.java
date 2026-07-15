package com.openggf.level;

import com.openggf.game.session.EngineContext;
import com.openggf.game.*;
import com.openggf.Engine;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.data.Game;
import com.openggf.data.AnimatedPaletteProvider;
import com.openggf.data.AnimatedPatternProvider;
import com.openggf.data.Rom;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.DynamicStartPositionProvider;
import com.openggf.debug.DebugObjectArtViewer;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.CustomZonePaletteBridge;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.game.modzone.ModZoneRuntimeProfile;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.CollisionRules;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.PatternWindowSessionState;
import com.openggf.mods.code.OwnerAwareGameplayInputFilter;
import com.openggf.level.rewind.LevelRewindSnapshotAdapter;
import com.openggf.level.objects.HudPaletteBridgeAccess;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.audio.AudioManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindClassResolver;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.sprites.Sprite;
import com.openggf.game.PowerUpObject;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * Manages the loading and rendering of game levels.
 */
@com.openggf.game.ModApi
public class LevelManager {
    static final Logger LOGGER = Logger.getLogger(LevelManager.class.getName());
    static final int OBJECT_PATTERN_BASE = PatternAtlasRange.OBJECTS.base();
    private static final int HUD_PATTERN_BASE = PatternAtlasRange.HUD.base();
    /** Base for extra sidekick-style DPLC banks — above water (0x30000) and below title cards (0x40000). */
    public static final int SIDEKICK_PATTERN_BASE = PatternAtlasRange.SIDEKICK_BANKS.base();
    private static final Palette.Color BLACK_BACKDROP = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
    // Local mirror of the loaded Level owned by WorldSession. Reads use this
    // field directly for speed; writes go through writeCurrentLevel() to keep
    // the world session in sync.
    Level level;
    int blockPixelSize = 128;  // cached from level
    // Block-grid index math (pow2 fast path); recomputed in cacheLevelDimensions().
    private BlockGridIndexer blockGrid = new BlockGridIndexer(128);
    private int chunksPerBlockSide = 8;
    // Cached level pixel dimensions (immutable once level loads).
    // Avoids repeated getLayerWidthBlocks()*blockPixelSize in hot-path collision lookups.
    private int cachedFgWidthPx;
    private int cachedFgHeightPx;
    private int cachedBgWidthPx;           // Full map width for BG layer (used for block lookups)
    int cachedBgContiguousWidthPx; // Contiguous BG data width from column 0 (for bgTilemapBaseX wrapping)
    private int cachedBgHeightPx;
    Game game;
    GameModule gameModule;

    public Game getGame() {
        return game;
    }

    public GameModule getGameModule() {
        return gameModule;
    }

    GameModule activeGameModule() {
        if (gameModule != null) {
            return gameModule;
        }
        WorldSession world = SessionManager.getCurrentWorldSession();
        if (world != null && world.getGameModule() != null) {
            return world.getGameModule();
        }
        return GameServices.currentOrBootstrapGameModule();
    }

    PlayableCharacterRegistry playableCharacterRegistry() {
        return worldSession.getPlayableCharacterRegistry();
    }

    GameDataSource worldDataSource() {
        return worldSession.getDataSource();
    }

    /** Collision model metadata only; frame scheduling may still use inline checkpoints. */
    private boolean isUnifiedCollisionModel() {
        GameRules rules = activeGameModule().getRules();
        return rules != null
                && rules.collision() != null
                && rules.collision().collisionModel() == com.openggf.game.CollisionModel.UNIFIED;
    }

    /** Returns the tilemap lifecycle delegate. */
    public LevelTilemapManager getTilemapManager() {
        return tilemapManager;
    }

    GraphicsManager graphicsManager;
    AudioManager audioManager;
    SpriteManager spriteManager;
    private CollisionSystem collisionSystem;
    WaterSystem waterSystem;
    private GameStateManager gameState;
    SonicConfigurationService configService;
    DebugOverlayManager overlayManager;
    LevelDebugRenderer debugRenderer;
    PerformanceProfiler profiler;
    private CrossGameFeatureProvider crossGameFeatures;
    final List<List<LevelDescriptor>> levels = new ArrayList<>();
    private final List<PendingLostRingSpawn> pendingLostRingSpawns = new ArrayList<>();
    private final WorldSession worldSession;
    private ZoneProgressionPlan zoneProgressionPlan = ZoneProgressionPlan.LINEAR;
    private ZoneProgressionPlan.ZoneTopology zoneProgressionTopology;
    // Local mirror of zone/act state owned by WorldSession. Reads use these
    // fields directly for speed; writes go through writeCurrentZone /
    // writeCurrentAct / writeApparentAct so both copies stay in sync.
    int currentAct = 0;
    private int apparentAct = 0;
    int currentZone = 0;
    private boolean sidekickRomVisibleReloadFrameCounterBridgeActive;
    private boolean sidekickRomVisibleReloadFrameCounterBridgePrimed;

    void writeCurrentZone(int zone) {
        this.currentZone = zone;
        worldSession.setCurrentZone(zone);
    }

    void writeCurrentAct(int act) {
        this.currentAct = act;
        worldSession.setCurrentAct(act);
    }

    private void writeApparentAct(int act) {
        this.apparentAct = act;
        worldSession.setApparentAct(act);
    }

    private void writeCurrentLevel(Level level) {
        this.level = level;
        worldSession.setCurrentLevel(level);
    }
    int frameCounter = 0;
    ObjectManager objectManager;
    private RewindClassResolver rewindClassResolver = RewindClassResolver.ENGINE_ONLY;

    public void setRewindClassResolver(RewindClassResolver resolver) {
        rewindClassResolver = java.util.Objects.requireNonNull(resolver, "resolver");
        if (objectManager != null) objectManager.setRewindClassResolver(resolver);
    }
    RingManager ringManager;
    ZoneFeatureProvider zoneFeatureProvider;
    private TouchResponseTable touchResponseTable;
    ObjectRenderManager objectRenderManager;
    HudRenderManager hudRenderManager;
    private HudProfile activeHudProfile = HudProfile.stock();
    AnimatedPatternManager animatedPatternManager;
    AnimatedPaletteManager animatedPaletteManager;
    private ModZoneRuntimeProfile activeModZoneRuntimeProfile;
    private ModZoneRuntimeContribution activeModZoneRuntimeContribution;
    private CustomZonePaletteBridge activeCustomZonePaletteBridge;
    LevelState levelGamestate;

    // GPU tilemap lifecycle delegate (build/cache/upload/invalidate)
    LevelTilemapManager tilemapManager;

    // All transition request/consume state lives in the coordinator
    private final LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();

    // ROM: LZ3/SBZ2 vertical wrapping — FG layer wraps Y instead of clamping
    boolean verticalWrapEnabled = false;

    // Background rendering support
    ParallaxManager parallaxManager;
    boolean useShaderBackground = true; // Feature flag for shader background


    // Cached screen dimensions (avoids repeated config service lookups)
    int cachedScreenWidth;
    int cachedScreenHeight;

    // Camera reference for frustum culling
    Camera camera;

    // Rendering pipeline (extracted from LevelManager — see LevelRenderer).
    private final LevelRenderer levelRenderer = new LevelRenderer(this);
    final LevelFrameRuntimeUpdater frameRuntimeUpdater = new LevelFrameRuntimeUpdater(this);
    private final LevelPlayableArtInitializer playableArtInitializer;
    private final LevelDirtyRegionDispatcher dirtyRegionDispatcher;
    final LevelWaterCoordinator waterCoordinator;
    final LevelCheckpointCoordinator checkpointCoordinator;
    private final LevelActTransitionExecutor actTransitionExecutor;
    private EngineContext engineServices; private EditorSaveManager editorSaveManager;

    @Deprecated(forRemoval = true)
    protected LevelManager() {
        throw new IllegalStateException("LevelManager requires explicit gameplay-mode dependencies");
    }

    /**
     * Constructs a LevelManager with explicit manager dependencies.
     * Used by session-owned gameplay-mode construction to inject peers instead of
     * reading from singletons.
     */
    public LevelManager(Camera camera, SpriteManager spriteManager,
                        ParallaxManager parallaxManager, CollisionSystem collisionSystem,
                        WaterSystem waterSystem, GameStateManager gameState,
                        EngineContext engineServices, WorldSession worldSession) {
        this.camera = camera;
        this.spriteManager = spriteManager;
        this.parallaxManager = parallaxManager;
        this.collisionSystem = collisionSystem;
        this.waterSystem = waterSystem;
        this.gameState = gameState;
        this.worldSession = worldSession;
        this.graphicsManager = engineServices.graphics();
        this.audioManager = engineServices.audio();
        this.configService = engineServices.configuration();
        this.overlayManager = engineServices.debugOverlay();
        this.profiler = engineServices.profiler();
        this.crossGameFeatures = engineServices.crossGameFeatures();
        this.engineServices = engineServices;
        this.playableArtInitializer = new LevelPlayableArtInitializer(
                this, spriteManager, graphicsManager, configService, crossGameFeatures);
        this.dirtyRegionDispatcher = new LevelDirtyRegionDispatcher(this);
        this.waterCoordinator = new LevelWaterCoordinator(this);
        this.checkpointCoordinator = new LevelCheckpointCoordinator(this);
        this.actTransitionExecutor = new LevelActTransitionExecutor(this);
        this.cachedScreenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        this.cachedScreenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        // Inherit any zone/act metadata and loaded Level already on the
        // world session (e.g. after editor exit when WorldSession survives
        // but LevelManager is freshly constructed).
        this.currentZone = worldSession.getCurrentZone();
        this.currentAct = worldSession.getCurrentAct();
        this.apparentAct = worldSession.getApparentAct();
        this.level = worldSession.getCurrentLevel();
    }

    public void setEditorSaveManager(EditorSaveManager editorSaveManager) { this.editorSaveManager = editorSaveManager; }

    /**
     * Refreshes the zone list from the current GameModule's ZoneRegistry.
     * Called during level loading to ensure zones match the current game.
     */
    void refreshZoneList() {
        levels.clear();
        com.openggf.game.ZoneRegistry registry = gameModule.getZoneRegistry();
        levels.addAll(registry.getAllZones());
        setZoneProgressionPlan(registry.progressionPlan(), registry.progressionTopology());
    }

    /**
     * Installs one immutable progression plan and the registry snapshot it was
     * built against. Mod catalog integration owns construction of both values;
     * the level manager only executes the frozen result.
     */
    public void setZoneProgressionPlan(ZoneProgressionPlan plan,
                                       ZoneProgressionPlan.ZoneTopology topology) {
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(topology, "topology");
        validateProgressionTopology(topology);
        plan.requireCompatible(topology);
        this.zoneProgressionPlan = plan;
        this.zoneProgressionTopology = topology;
    }

    private ZoneProgressionPlan.ZoneTopology activeProgressionTopology() {
        if (zoneProgressionTopology != null) {
            validateProgressionTopology(zoneProgressionTopology);
            return zoneProgressionTopology;
        }
        int[] actCounts = new int[levels.size()];
        for (int zone = 0; zone < levels.size(); zone++) {
            actCounts[zone] = levels.get(zone).size();
        }
        return ZoneProgressionPlan.ZoneTopology.linear(actCounts);
    }

    private void validateProgressionTopology(ZoneProgressionPlan.ZoneTopology topology) {
        if (levels.isEmpty()) {
            return;
        }
        if (topology.zoneCount() != levels.size()) {
            throw new IllegalArgumentException("Progression topology does not match the zone registry size");
        }
        for (int zone = 0; zone < levels.size(); zone++) {
            if (topology.actCount(zone) != levels.get(zone).size()) {
                throw new IllegalArgumentException("Progression topology act count does not match zone " + zone);
            }
        }
    }

    /**
     * Loads the specified level into memory.
     *
     * @param levelIndex the index of the level to load
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex) throws IOException {
        loadLevel(levelIndex, LevelLoadMode.FULL);
    }

    /**
     * Loads the specified level into memory with explicit load mode.
     *
     * @param levelIndex the index of the level to load
     * @param loadMode   profile execution mode
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex, LevelLoadMode loadMode) throws IOException {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setLevelIndex(levelIndex);
        ctx.setLoadMode(loadMode);
        loadLevel(levelIndex, loadMode, ctx);
    }

    /**
     * Loads the specified level into memory with explicit load mode and context.
     * <p>
     * When the context has {@code includePostLoadAssembly} set, the profile will
     * include post-load steps (checkpoint restore, player spawn, camera, etc.).
     *
     * @param levelIndex the index of the level to load
     * @param loadMode   profile execution mode
     * @param ctx        pre-built context with checkpoint snapshot and spawn data
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex, LevelLoadMode loadMode, LevelLoadContext ctx) throws IOException {
        try {
            GameModule module = activeGameModule();
            LevelInitProfile profile = module.getLevelInitProfile();
            ctx.setLevelIndex(levelIndex);
            ctx.setLoadMode(loadMode);

            List<InitStep> steps = profile.levelLoadSteps(ctx);
            if (steps.isEmpty()) {
                throw new IllegalStateException(
                    "No level load steps defined for " +
                    module.getClass().getSimpleName() +
                    ". All game modules must implement levelLoadSteps().");
            }
            for (InitStep step : steps) {
                long start = System.nanoTime();
                step.execute();
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                LOGGER.fine(() -> String.format("  [%s] %dms — %s", step.name(), elapsed, step.romRoutine()));
            }
            // The LoadLevelData step stores the result in ctx
            if (ctx.getLevel() != null) {
                writeCurrentLevel(ctx.getLevel());
            }
            resetRewindBufferAfterLevelBoundary();
        } catch (Exception e) {
            // Profile steps wrap checked exceptions in RuntimeException; unwrap if cause is IOException
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                LOGGER.log(SEVERE, "Failed to load level " + levelIndex, ioe);
                throw ioe;
            }
            LOGGER.log(SEVERE, "Unexpected error while loading level " + levelIndex, e);
            throw new IOException("Failed to load level due to unexpected error.", e);
        }
    }

    private void resetRewindBufferAfterLevelBoundary() {
        markRewindLevelLoadBoundary();
    }

    static void markRewindLevelLoadBoundary() {
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        }
    }

    /**
     * Phase A: Initialize ROM access, parallax, game module, and zone registry.
     */
    public void initGameModule(int levelIndex) throws IOException {
        GameDataSource source = worldSession.getDataSource();
        Rom rom = source.rom().orElse(null);
        parallaxManager.load(rom);
        gameModule = GameServices.module();
        refreshZoneList();
        game = gameModule.createGame(source);
    }

    /**
     * Phase C/F: Configure audio manager and play level music.
     */
    public void initAudio(int levelIndex) throws IOException {
        audioManager.setAudioProfile(gameModule.getAudioProfile());
        audioManager.setRom(worldSession.getDataSource().rom().orElse(null));
        audioManager.setSoundMap(game.getSoundMap());
        audioManager.resetRingSound();
        if (!transitions.isSuppressNextMusicChange()) {
            com.openggf.game.MusicReference music =
                    gameModule.getLevelMusicReference(currentZone, currentAct);
            if (music instanceof com.openggf.game.MusicReference.Stock stock) {
                audioManager.playMusic(stock.musicId());
            } else {
                com.openggf.game.MusicReference.Namespaced namespaced =
                        (com.openggf.game.MusicReference.Namespaced) music;
                audioManager.playNamespacedMusic(new com.openggf.audio.StreamedMusicPort.TrackRef(
                        namespaced.owner(), namespaced.localName()));
            }
        }
        transitions.setSuppressNextMusicChange(false);
    }

    /**
     * Phase A-C: Initialize game module, configure audio manager, and play level music.
     */
    public void initGameModuleAndAudio(int levelIndex) throws IOException {
        initGameModule(levelIndex);
        initAudio(levelIndex);
    }

    /**
     * Phase E-F: Delegate to Game.loadLevel(), cache level dimensions, and reset dirty flags.
     *
     * @return the loaded Level instance (also assigned to {@code this.level})
     */
    public Level loadLevelData(int levelIndex) throws IOException {
        installGameplayInputFilter();
        activeModZoneRuntimeContribution = gameModule == null ? null
                : gameModule.getZoneRegistry().modZoneRuntimeContribution(levelIndex);
        activeModZoneRuntimeProfile = activeModZoneRuntimeContribution != null
                ? activeModZoneRuntimeContribution.runtimeProfile() : null;
        activeCustomZonePaletteBridge = null;
        Level loaded = gameModule.loadLevelOverride(levelIndex);
        if (loaded == null) loaded = game.loadLevel(levelIndex);
        writeCurrentLevel(loaded);
        installHudProfile();
        rebuildLevelDerivedState();
        return loaded;
    }

    private void installHudProfile() {
        HudProfile resolved = HudProfile.stock();
        if (gameModule != null) {
            ZoneKey destination = gameModule.getZoneRegistry().zoneKey(currentZone);
            if (destination instanceof ZoneKey.Mod) {
                resolved = gameModule.getGameplayPolicyProvider().hudProfile(destination)
                        .orElse(HudProfile.stock());
            }
        }
        activeHudProfile = resolved;
        if (hudRenderManager != null) {
            hudRenderManager.setProfile(activeHudProfile);
        }
    }

    private void installGameplayInputFilter() {
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null || gameModule == null) {
            return;
        }
        ZoneKey destination = gameModule.getZoneRegistry().zoneKey(currentZone);
        GameplayInputFilter filter = gameModule.getGameplayPolicyProvider()
                .inputFilter(destination)
                .orElse(GameplayInputFilter.IDENTITY);
        if (destination instanceof ZoneKey.Mod mod
                && filter != GameplayInputFilter.IDENTITY
                && !(filter instanceof OwnerAwareGameplayInputFilter)) {
            filter = new OwnerAwareGameplayInputFilter(mod.ownerModId(), filter);
        }
        GameplayInputFilterAccess.install(gameplayMode, filter);
    }

    /**
     * Re-runs the post-load setup steps over the currently-loaded {@link Level}
     * (block dimensions, debug renderer, dimension cache, tilemap manager).
     * Used both by {@link #loadLevelData(int)} after a fresh ROM read and by
     * the editor mode exit path when LevelManager is freshly constructed but
     * inherits its Level from {@code WorldSession}. Safe to call multiple
     * times; each call rebuilds dependent state from {@code level}.
     */
    public void rebuildLevelDerivedState() {
        if (level == null) {
            return;
        }
        blockPixelSize = level.getBlockPixelSize();
        chunksPerBlockSide = level.getChunksPerBlockSide();
        debugRenderer = new LevelDebugRenderer(new LevelDebugContext(
                level, blockPixelSize, overlayManager, graphicsManager,
                cachedScreenWidth, cachedScreenHeight));
        cacheLevelDimensions();
        tilemapManager = new LevelTilemapManager(buildGeometry(), graphicsManager, gameState);
    }

    /**
     * Restores the read/render level view needed while editor mode is active
     * after gameplay mode teardown has reset gameplay-owned managers. This
     * intentionally rebuilds only level-derived rendering state; gameplay
     * object, ring, collision, and event systems are recreated on playtest
     * resume.
     */
    public void restoreEditorLevelView(Level editorLevel) {
        Level restoredLevel = editorLevel != null ? editorLevel : worldSession.getCurrentLevel();
        if (restoredLevel == null) {
            return;
        }
        writeCurrentLevel(restoredLevel);
        currentZone = worldSession.getCurrentZone();
        currentAct = worldSession.getCurrentAct();
        apparentAct = worldSession.getApparentAct();
        gameModule = worldSession.getGameModule();
        rebuildLevelDerivedState();
    }

    /**
     * Restores a loaded level inherited from {@code WorldSession} into a
     * freshly-constructed LevelManager — the path used after editor mode exit
     * when the gameplay mode is rebuilt around the surviving world
     * data. Re-runs the standard {@link #loadZoneAndAct(int, int)} flow to
     * orchestrate every gameplay subsystem (game module, audio, animated
     * content, objects, rings, zone features, art, water, etc.), then if the
     * inherited Level was a {@link MutableLevel}, swaps it back in via
     * {@link #setLevel(Level)} so any mutations made before editor entry
     * survive the round trip.
     * <p>
     * Used by the editor exit flow after the old gameplay mode has been
     * torn down and a fresh gameplay mode has been built over the surviving
     * {@code WorldSession}.
     */
    public void restoreInheritedLevel() throws IOException {
        Level inherited = level;
        if (inherited == null) {
            return;
        }
        int zone = currentZone;
        int act = currentAct;
        loadZoneAndAct(zone, act);
        if (inherited instanceof MutableLevel) {
            setLevel(inherited);
        }
    }

    /**
     * Phase E: Initialize animated pattern and palette managers for the loaded level.
     */
    public void initAnimatedContent() {
        initAnimatedPatterns();
        initAnimatedPalettes();
    }

    /**
     * Swaps the current level for a new one (e.g. a MutableLevel snapshot).
     * Re-initialises animated content managers so they read from the new
     * level's Pattern/Palette arrays, and invalidates the foreground tilemap
     * to trigger a full rebuild.
     *
     * @param newLevel the level to swap in
     */
    public void setLevel(Level newLevel) {
        writeCurrentLevel(newLevel);
        blockPixelSize = newLevel.getBlockPixelSize();
        chunksPerBlockSide = newLevel.getChunksPerBlockSide();
        cacheLevelDimensions();
        initAnimatedContent();
        if (tilemapManager != null) {
            tilemapManager.updateGeometry(buildGeometry());
            tilemapManager.invalidateAllTilemaps();
        } else {
            invalidateForegroundTilemap();
        }
    }

    /**
     * Processes dirty regions from a MutableLevel, dispatching incremental
     * updates to the relevant subsystems. This is a no-op when the current
     * level is not a MutableLevel, so there is zero performance impact on
     * normal gameplay.
     * <p>
     * Called from {@code LevelFrameStep} at the start of each frame.
     */
    public void processDirtyRegions() {
        dirtyRegionDispatcher.processDirtyRegions();
    }

    /**
     * Phase G: Create ObjectManager, TouchResponseTable, and wire CollisionSystem.
     */
    public void initObjectManager() throws IOException {
        touchResponseTable = gameModule.createTouchResponseTable(worldSession.getDataSource());
        objectManager = new ObjectManager(level.getObjects(),
                gameModule.createObjectRegistry(),
                gameModule.getPlaneSwitcherObjectId(),
                gameModule.getPlaneSwitcherConfig(),
                touchResponseTable,
                graphicsManager,
                camera,
                buildObjectServices());
        objectManager.setRewindClassResolver(rewindClassResolver);

        // S1 parity: counter-based respawn tracking DISABLED pending fix for
        // load/unload/reload incompatibility. The counter system prevents respawns
        // because forward and backward counters assign different respawn indices
        // to the same object. The ROM doesn't have this issue because ObjPosLoad
        // never unloads objects — they persist until their own code deletes them.
        // S1 counter-based respawn tracking.
        GameRules gameRules = gameModule.getRules();
        if (gameRules != null
                && gameRules.collision() != null
                && gameRules.collision().collisionModel() == com.openggf.game.CollisionModel.UNIFIED) {
            objectManager.enableCounterBasedRespawn();
        } else {
            objectManager.enableExecThenLoadPlacement();
            objectManager.enforceSlotLimit();
        }

        // S3K parity: ROM's Object_respawn_table bit 7 stays set permanently
        // after a player kill (sonic3k.asm loc_1BA40 / Touch_EnemyNormal). Match
        // by latching destroyedInWindow for the rest of the level.
        if (gameRules != null
                && gameRules.objectInteraction() != null
                && gameRules.objectInteraction().permanentRespawnTableLatch()) {
            objectManager.enablePermanentDestroyLatch();
        }

        // Wire up CollisionSystem with ObjectManager for unified collision pipeline
        collisionSystem.setObjectManager(objectManager);

        // Inject PowerUpSpawner into all playable sprites
        injectPowerUpSpawner();
    }

    /**
     * Injects a {@link DefaultPowerUpSpawner} backed by the current
     * {@link ObjectManager} into the main player and all sidekicks.
     */
    String resolveMainCharacterCode() {
        return ActiveGameplayTeamResolver.resolveMainCharacterCode(configService);
    }

    private void injectPowerUpSpawner() {
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(objectManager);
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (player instanceof AbstractPlayableSprite playable) {
            playable.setPowerUpSpawner(spawner);
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.setPowerUpSpawner(spawner);
        }
    }

    /**
     * Phase G: Reset camera bounds and initialize object placement window.
     */
    public void initCameraBounds() {
        // Reset camera state from previous level (signpost may have locked it)
        camera.setFrozen(false);
        // ROM: LevelSizeLoad sets v_limitleft2 and v_limitright2 from LevelSizeArray.
        // Use the level's ROM boundaries (not map pixel width) so the camera is
        // constrained to the same region as the original hardware.
        camera.setMinX((short) level.getMinX());
        camera.setMaxX((short) level.getMaxX());
        objectManager.reset(camera.getX());
    }

    /**
     * Phase G: Create ObjectManager, wire CollisionSystem, and reset camera bounds.
     * Also registers level and object-manager rewind adapters with the active
     * {@link com.openggf.game.session.GameplayModeContext} (if one exists).
     */
    public void initObjectSystem() throws IOException {
        initObjectManager();
        initCameraBounds();
        com.openggf.game.session.GameplayModeContext gameplayMode =
                com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerLevelAdapters(this);
        }
    }

    /**
     * Phase H: Reset game-specific object state for the new level.
     */
    public void initGameplayState() {
        // Clear end-of-level flags left over from the previous act's results screen.
        // Without this, stale endOfLevelFlag=true persists across full zone transitions
        // (e.g. AIZ2 results → HCZ1 load), causing the next zone's act transition to
        // fire immediately when the BG event handler first checks the flag.
        GameServices.gameState().resetForLevel();
        gameModule.onLevelLoad();
    }

    /**
     * Phase H: Create RingManager and cache ring patterns.
     */
    public void initRings() {
        RingSpriteSheet ringSpriteSheet = level.getRingSpriteSheet();
        ringManager = new RingManager(level.getRings(), ringSpriteSheet, this, touchResponseTable, audioManager);
        ringManager.reset(camera.getX());
        ringManager.ensurePatternsCached(graphicsManager, level.getPatternCount());
        com.openggf.game.session.GameplayModeContext gameplayMode =
                com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerRingAdapter(ringManager);
        }
    }

    /**
     * Phase H: Initialize zone-specific features (CNZ bumpers, CPZ pylon, water surface, etc.).
     */
    public void initZoneFeatures() throws IOException {
        zoneFeatureProvider = activeModZoneRuntimeProfile == null
                ? gameModule.getZoneFeatureProvider() : null;
        resetZoneScopedRegistriesForLevelLoad();
        if (activeModZoneRuntimeProfile != null) {
            var zoneRuntime = GameServices.zoneRuntimeRegistryOrNull();
            var animatedTiles = GameServices.animatedTileChannelGraphOrNull();
            if (zoneRuntime != null) {
                zoneRuntime.clear();
            }
            if (animatedTiles != null) {
                animatedTiles.clear();
            }
        }
        applyLevelLoadPaletteOverrides();
        initializeZoneFeatureProvider(zoneFeatureProvider);
    }

    void reinitializeZoneFeaturesForActTransition() throws IOException {
        if (zoneFeatureProvider == null && activeModZoneRuntimeProfile == null) {
            zoneFeatureProvider = gameModule.getZoneFeatureProvider();
        }
        resetZoneScopedRegistriesForLevelLoad();
        applyLevelLoadPaletteOverrides();
        initializeZoneFeatureProvider(zoneFeatureProvider);
    }

    void resetZoneScopedRegistriesForLevelLoad() {
        PaletteOwnershipRegistry paletteOwnershipRegistry = GameServices.paletteOwnershipRegistryOrNull();
        SpecialRenderEffectRegistry specialRenderEffectRegistry = GameServices.specialRenderEffectRegistryOrNull();
        AdvancedRenderModeController advancedRenderModeController = GameServices.advancedRenderModeControllerOrNull();
        if (paletteOwnershipRegistry != null) {
            paletteOwnershipRegistry.clear();
        }
        if (specialRenderEffectRegistry != null) {
            specialRenderEffectRegistry.clear();
        }
        if (advancedRenderModeController != null) {
            advancedRenderModeController.clear();
        }
    }

    private void applyLevelLoadPaletteOverrides() {
        if (game instanceof LevelLoadPaletteOverrideProvider provider && level != null) {
            provider.applyLevelLoadPaletteOverrides(level, currentZone, currentAct);
        }
    }

    void initializeZoneFeatureProvider(ZoneFeatureProvider zoneFeatureProvider) throws IOException {
        SpecialRenderEffectRegistry specialRenderEffectRegistry = GameServices.specialRenderEffectRegistryOrNull();
        AdvancedRenderModeController advancedRenderModeController = GameServices.advancedRenderModeControllerOrNull();
        if (zoneFeatureProvider != null) {
            Rom rom = worldSession.getDataSource().rom().orElse(null);
            zoneFeatureProvider.reset();
            zoneFeatureProvider.initZoneFeatures(rom, getFeatureZoneId(), getFeatureActId(), camera.getX());
            // Cache zone feature patterns (water surface, etc.)
            int waterPatternBase = 0x30000; // High offset to avoid collision
            zoneFeatureProvider.ensurePatternsCached(graphicsManager, waterPatternBase);
            if (specialRenderEffectRegistry != null) {
                zoneFeatureProvider.registerSpecialRenderEffects(
                        specialRenderEffectRegistry, getFeatureZoneId(), getFeatureActId());
            }
            if (advancedRenderModeController != null) {
                zoneFeatureProvider.registerAdvancedRenderModes(
                        advancedRenderModeController, getFeatureZoneId(), getFeatureActId());
            }
        }
    }

    /**
     * Phase H: Reset game-specific state, create RingManager, and initialize zone features.
     */
    public void initGameState() throws IOException {
        initGameplayState();
        initRings();
        initZoneFeatures();
    }

    /**
     * Phase C: Load object art and player sprite art into the pattern atlas.
     */
    public void initArt() {
        initObjectArt();
        playableArtInitializer.initialize();
    }

    /**
     * Phase C: Reset player state, initialize checkpoint, and create level gamestate.
     */
    public void initPlayerAndCheckpoint() {
        resetPlayerState();
        checkpointCoordinator.prepareForLevelStart();
        levelGamestate = gameModule.createLevelState();
    }

    /**
     * Phase C: Load object art, player sprite art, reset player state,
     * and initialize checkpoint and level gamestate.
     */
    public void initArtAndPlayer() {
        initArt();
        initPlayerAndCheckpoint();
    }

    /**
     * Phase B: Initialize the water system for the current level.
     */
    public void initWater() throws IOException {
        waterCoordinator.initialize();
    }

    /**
     * Initialize the water system, with optional seamless-transition awareness.
     * ROM: CheckLevelForWater (sonic3k.asm:9754-9759) compares Apparent_zone_and_act
     * to Current_zone_and_act. During seamless transitions Apparent != Current,
     * which enables water in cases that a direct load would disable (AIZ2 Knuckles).
     *
     * @param seamlessTransition true when called during a seamless act transition
     */
    void initWater(boolean seamlessTransition) throws IOException {
        waterCoordinator.initialize(seamlessTransition);
    }

    /**
     * Engine-specific: Pre-allocate BG FBO at the maximum required size.
     */
    public void initBackgroundRenderer() {
        // Pre-allocate the background FBO at maximum required size to avoid
        // mid-frame GPU reallocation hitches (e.g., AIZ intro ocean->beach transition)
        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null && bgRenderer.isInitialized()) {
            int maxBgWidth;
            if (zoneFeatureProvider != null && !zoneFeatureProvider.bgWrapsHorizontally()) {
                // S3K uses full-width BG data (e.g., AIZ intro ocean-to-beach transition)
                maxBgWidth = Math.max(cachedScreenWidth, getLayerLevelWidthPx((byte) 1));
            } else {
                // S1/S2 use VDP-width (512px) background periods.
                // Pre-allocating to full level width can exceed GPU max texture size
                // (S2: 128 blocks * 128px = 16384, right at GPU limit).
                maxBgWidth = Math.max(cachedScreenWidth, LevelTilemapManager.VDP_BG_PLANE_WIDTH_PX);
            }
            int fboHeight = 256 + LevelConstants.CHUNK_HEIGHT;
            graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                    (cx, cy, cw, ch) -> bgRenderer.ensureCapacity(maxBgWidth, fboHeight)));
        }
    }

    /**
     * Updates object positions before player physics.
     * This must be called BEFORE spriteManager.update() so that SolidContacts
     * sees the current frame's platform positions, fixing 1-frame lag on
     * fast-moving platforms (SwingingPlatform, CNZ Elevators).
     *
     * <p>Update order is critical:
     * <ol>
     *   <li>OscillationManager - oscillation values first</li>
     *   <li>objectManager - platforms read oscillation, move to new positions</li>
     *   <li>spriteManager - SolidContacts now sees updated positions</li>
     * </ol>
     */
    public void updateObjectPositions() {
        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            objectManager.update(camera.getX(), playable, sidekicks, frameCounter + 1);
        }

        // ROM parity: OscillateNumDo runs AFTER ExecuteObjects in both S1
        // (sonic.asm:3205→3223) and S2 (s2.asm:5091→5104). Objects must read
        // the previous frame's oscillation values, then OscillateNumDo advances
        // them for the next frame.
        advanceGlobalOscillation();
    }

    /**
     * Returns true when the active module executes objects after player physics and
     * solid checkpoints are resolved during object execution. Driven by the
     * {@link ObjectInteractionRules#objectsExecuteAfterPlayerPhysics()} flag — independent
     * of collision model so S1 (UNIFIED) and S2/S3K (DUAL_PATH) can share the
     * post-physics ordering per the 2026-04-18-solid-ordering-rom-accuracy plan.
     */
    public boolean objectsExecuteAfterPlayerPhysics() {
        GameModule activeModule = activeGameModule();
        if (activeModule == null
                || activeModule.getRules() == null
                || activeModule.getRules().objectInteraction() == null) {
            return false;
        }
        return activeModule.getRules().objectInteraction().objectsExecuteAfterPlayerPhysics();
    }

    /**
     * Returns true when the active module advances the dynamic water level (move
     * toward target) BEFORE the player's underwater check, matching S1/S2 ROM
     * order ({@code LZWaterFeatures}/{@code WaterEffects} run before
     * {@code ExecuteObjects}/{@code RunObjects}). S3K returns false because
     * {@code Process_Sprites} runs before {@code Handle_Onscreen_Water_Height},
     * so the player reads the previous frame's water level there. Driven by the
     * {@link CollisionRules#advanceWaterLevelBeforePlayerPhysics()} flag.
     */
    public boolean advanceWaterLevelBeforePlayerPhysics() {
        GameModule activeModule = activeGameModule();
        if (activeModule == null
                || activeModule.getRules() == null
                || activeModule.getRules().collision() == null) {
            return false;
        }
        return activeModule.getRules().collision().advanceWaterLevelBeforePlayerPhysics();
    }

    /**
     * Advances the dynamic water level (move toward target) for the current
     * level. Extracted from {@link #update()} so the inline-order path can run it
     * BEFORE the player physics step when
     * {@link #advanceWaterLevelBeforePlayerPhysics()} is set, matching ROM order
     * where {@code v_waterpos2}/{@code Water_Level} is moved before the player's
     * {@code Sonic_Water} underwater check. This relocates only the level MOVE;
     * the per-act target ({@code DynWaterHeight}) is still set by the zone
     * feature provider in {@link #update()}.
     */
    public void advanceDynamicWaterLevel() {
        waterCoordinator.advanceDynamicWaterLevel();
    }

    /**
     * Run touch responses for a single player. Called from tickPlayablePhysics
     * after handleMovement but before post-movement solid contacts, matching
     * the ROM's ReactToItem timing within ExecuteObjects.
     */
    public void applyTouchResponses(PlayableEntity player) {
        if (objectManager != null) {
            objectManager.runTouchResponsesForPlayer(
                    player,
                    frameCounter + 1,
                    objectsExecuteAfterPlayerPhysics());
        }
        if (ringManager != null && player instanceof AbstractPlayableSprite playable && !playable.getDead()) {
            if (!ringManager.usesObjectTouchCollection()) {
                ringManager.collectStageRings(playable, frameCounter + 1);
            }
            // Lost (spilled) ring collection now runs through the unified slot-ordered touch
            // loop in ObjectManager.runTouchResponsesForPlayer (above) via the type-keyed
            // LostRingObjectInstance branch — see ObjectManager Touch_ChkValue lost-ring gate.
            // The legacy RingManager.checkLostRingCollection scan has been removed.
        }
    }

    /**
     * Refreshes object touch snapshots before inline-order player physics runs.
     * This keeps ReactToItem aligned to the current frame's pre-object-update state.
     */
    public void prepareTouchResponseSnapshots() {
        if (objectManager != null) objectManager.snapshotTouchResponseState(touchResponseUsesPreviousCollisionResponseList());
    }

    private boolean touchResponseUsesPreviousCollisionResponseList() {
        GameModule activeModule = activeGameModule();
        return activeModule != null
                && activeModule.getRules() != null
                && activeModule.getRules().objectInteraction() != null
                && activeModule.getRules().objectInteraction().touchResponseUsesPreviousCollisionResponseList();
    }

    /**
     * Advances object streaming/execution without any touch responses.
     * Used by non-interactive ending demo preroll phases so objects can become
     * visible and animate without hurting/collecting from the frozen player.
     */
    public void updateObjectPositionsWithoutTouches() {
        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;

            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            objectManager.update(camera.getX(), playable, sidekicks, frameCounter + 1, false);
        }

        // ROM parity: OscillateNumDo runs AFTER ExecuteObjects in both S1
        // (sonic.asm:3205→3223) and S2 (s2.asm:5091→5104). Objects must read
        // the previous frame's oscillation values, then OscillateNumDo advances
        // them for the next frame. Placing this call before objectManager.update()
        // caused a 1-frame phase shift in oscillating platform positions.
        advanceGlobalOscillation();
    }

    /**
     * Runs legacy post-player object hooks after the playable step has completed.
     * This is for ROM behaviors where later SST slots read Sonic's current
     * post-movement state and write globals for the following frame.
     */
    public void updateObjectPostPlayerHooks() {
        if (objectManager == null) {
            return;
        }
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        AbstractPlayableSprite playable =
                player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
        objectManager.runPostPlayerHooks(playable, frameCounter + 1);
    }

    /**
     * Runs object execution after player physics with inline solid resolution.
     * Used by inline-order modules, where the ROM executes the player slot first,
     * then processes solid objects in slot order against the player's already-moved state.
     */
    public void updateObjectPositionsPostPhysicsWithoutTouches() {
        updateObjectPositionsPostPhysicsWithoutTouches(null);
    }

    public void updateObjectPositionsPostPhysicsWithoutTouches(Runnable afterExecBeforePlacement) {
        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            objectManager.update(camera.getX(), playable, sidekicks, frameCounter + 1,
                    false, true, true, afterExecBeforePlacement);
        }

        // ROM parity: objects read the previous frame's oscillation values, then
        // OscillateNumDo advances them for the next frame after ExecuteObjects.
        advanceGlobalOscillation();
    }

    private void advanceGlobalOscillation() {
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        if (zoneFeatureProvider != null
                && !zoneFeatureProvider.shouldAdvanceGlobalOscillation(featureZone, featureAct)) {
            return;
        }
        OscillationManager.update(frameCounter);
    }

    /**
     * Post-camera object placement catch-up: runs the placement window update
     * using the current (post-camera-update) camera position.
     * <p>
     * ROM parity: {@code ObjPosLoad} runs <b>after</b> {@code DeformLayers}
     * (camera update), using the post-camera position. The main placement pass
     * inside {@code ObjectManager.update()} uses the pre-camera position;
     * this call closes the gap when the camera advance crosses a chunk boundary.
     * <p>
     * The Placement class short-circuits when the camera chunk hasn't changed,
     * so this is a no-op on most frames. When the camera has crossed a chunk
     * boundary, the placement's active set is updated to include newly-windowed
     * spawns. On the next frame, {@code syncActiveSpawns()} creates instances.
     */
    public void postCameraObjectPlacementSync() {
        if (objectManager != null) {
            objectManager.postCameraPlacementUpdate(camera.getX());
        }
    }

    public void refreshObjectPostCameraRenderState() {
        if (objectManager != null) {
            objectManager.refreshPostCameraRenderState();
        }
    }

    /**
     * Advances zone scroll handlers that own foreground camera movement.
     *
     * @return true when the normal player-follow camera step should be skipped
     */
    public boolean advanceCameraDrivenScrollForFrame() {
        return parallaxManager != null
                && parallaxManager.advanceCameraDrivenScroll(currentZone, currentAct, camera, frameCounter);
    }

    /**
     * Runs pre-physics zone feature updates (e.g., LZ water slides and wind tunnels).
     *
     * <p>ROM order: {@code LZWaterFeatures} runs before {@code ExecuteObjects},
     * so water slides set {@code f_slidemode} and {@code obInertia} before
     * {@code Sonic_Move} executes. This method must be called before
     * {@code spriteManager.update()} to match that ordering.
     */
    public void updateZoneFeaturesPrePhysics() {
        if (zoneFeatureProvider != null && level != null) {
            Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            zoneFeatureProvider.updatePrePhysics(playable, camera.getX(), getFeatureZoneId());
        }
    }

    public void updateZoneFeaturesAfterPlayablePhysics(AbstractPlayableSprite playable) {
        if (zoneFeatureProvider != null && level != null && playable != null) {
            zoneFeatureProvider.updateAfterPlayablePhysics(playable, camera.getX(), getFeatureZoneId());
        }
    }

    public void update() {
        // NOTE: OscillationManager and objectManager are now updated via updateObjectPositions()
        // which is called earlier in GameLoop to fix platform riding sync (1-frame lag fix).

        // Advance the frame counter. This drives OscillationManager dedup (via
        // updateObjectPositionsWithoutTouches), ring/object frame tracking, and
        // parallax animation timing. Must increment here (in the logic path)
        // rather than in drawWithSpritePriority() so headless tests see the
        // counter advance even when rendering is disabled.
        frameCounter++;
        processPendingLostRingSpawns();

        Sprite player = null;
        AbstractPlayableSprite playable = null;
        boolean needsPlayer = ringManager != null || zoneFeatureProvider != null || levelGamestate != null;
        if (needsPlayer) {
            player = spriteManager.getSprite(resolveMainCharacterCode());
            playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
        }
        if (ringManager != null) {
            ringManager.update(camera.getX(), playable, frameCounter + 1, false);
            // Per-ring spilled-ring physics now runs in the object exec loop
            // (LostRingObjectInstance); this only advances the shared decelerating
            // spin once per frame (ROM ChangeRingFrame / Ring_spill_anim_*).
            ringManager.updateLostRingPhysics(frameCounter + 1);
        }
        // Water movement — ROM order: MoveWater (move toward target) runs BEFORE
        // DynWaterHeight (zone features set new target for next frame).
        // Use effective feature zone/act so S1 SBZ3 (loaded from LZ act 4 slot)
        // resolves to SBZ3 water behavior while retaining LZ tile/object resources.
        waterCoordinator.advanceDynamicWaterLevelAfterPlayerPhysicsIfNeeded();

        // Update zone-specific features (CNZ bumpers, S1 DynWaterHeight, etc.)
        if (zoneFeatureProvider != null && level != null) {
            zoneFeatureProvider.update(playable, camera.getX(), getFeatureZoneId());
        }
        if (levelGamestate != null) {
            if (!isHudSuppressed()) {
                levelGamestate.update();
            }
            if (levelGamestate.isTimeOver() && playable != null && !playable.getDead()) {
                playable.applyHurtOrDeath(0, DamageCause.TIME_OVER, false);
            }
        }

        frameRuntimeUpdater.updateParallaxAndAnimatedContent();

        // Legacy object-before-physics modules update playable water state here.
        // Inline-order modules run this immediately after the player slot in
        // LevelFrameStep, before ExecuteObjects, matching S3K's Sonic_Water /
        // Tails_Water ordering.
        if (!objectsExecuteAfterPlayerPhysics()) {
            updatePlayableWaterStatesForCurrentLevel();
        }
    }

    /**
     * Advances non-player scene systems for ending-demo preroll phases.
     * Keeps water and zone features in sync while player physics/input are frozen.
     */
    public void updateEndingDemoScene() {
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;

        if (ringManager != null) {
            ringManager.update(camera.getX(), null, frameCounter + 1);
        }

        // Water movement before zone features (ROM order: MoveWater before DynWaterHeight)
        waterCoordinator.advanceDynamicWaterLevel();

        if (zoneFeatureProvider != null && level != null) {
            zoneFeatureProvider.update(playable, camera.getX(), getFeatureZoneId());
        }

        updatePlayableWaterStatesForCurrentLevel();
    }

    public void updatePlayableWaterStatesForCurrentLevel() {
        waterCoordinator.updatePlayableWaterStatesForCurrentLevel();
    }

    public void updatePlayableWaterStateForCurrentLevel(AbstractPlayableSprite playable) {
        waterCoordinator.updatePlayableWaterStateForCurrentLevel(playable);
    }

    boolean shouldSuppressUnderwaterPalette(int zoneId, int actId) {
        return waterCoordinator.shouldSuppressUnderwaterPalette(zoneId, actId);
    }

    public void applyPlaneSwitchers(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        if (objectManager != null) {
            objectManager.applyPlaneSwitchers(player);
        }
        // Sonic 1 loop-based plane switching (and any other game-specific plane logic)
        GameModule module = activeGameModule();
        if (module != null) {
            module.applyPlaneSwitching(player);
        }
    }

    public LevelState getLevelGamestate() {
        return levelGamestate;
    }

    /**
     * Replaces the current level gamestate with a fresh instance.
     * Used by non-seamless S3K act transitions where acts share level data
     * and no level reload occurs. The results screen calls this to reset
     * timer and rings for the new act.
     */
    public void resetLevelGamestate(LevelState newState) {
        this.levelGamestate = newState;
    }

    /**
     * Rebuilds playable sprite renderers after scripts add a sidekick during
     * gameplay, such as MGZ2's boss-transition Tails rescue object.
     */
    public void refreshPlayableSpriteArt() {
        playableArtInitializer.initialize();
    }

    /**
     * Computes the running VRAM bank offset for each sidekick within SIDEKICK_PATTERN_BASE.
     * Every sidekick unconditionally gets its own isolated bank — no name-based slot
     * optimization (which missed ART_TILE collisions like Knuckles/Sonic sharing 0x0680).
     *
     * @param bankSizes the bank size of each sidekick's art set, in order
     * @return list of offsets (one per sidekick) within SIDEKICK_PATTERN_BASE
     */
    public static List<Integer> computeSidekickBankOffsets(List<Integer> bankSizes) {
        return LevelPlayableArtInitializer.computeSidekickBankOffsets(bankSizes);
    }

    /**
     * Reserves an isolated virtual pattern bank from the sidekick DPLC range
     * without registering a gameplay sidekick. Render-only systems such as
     * trace ghosts use this to avoid corrupting real player/sidekick DPLC state.
     */
    public int reserveSidekickPatternBank(int bankSize) {
        return playableArtInitializer.reserveSidekickPatternBank(bankSize);
    }

    private void resetPlayerState() {
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (player instanceof AbstractPlayableSprite playable) {
            playable.resetState();
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.resetState();
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().reset();
            }
        }
    }

    private void initObjectArt() {
        activeCustomZonePaletteBridge = null;
        PatternAtlas patternAtlas = graphicsManager.getPatternAtlas();
        if (patternAtlas != null) {
            patternAtlas.clearRanges();
            PatternWindowSessionState.of(worldSession).registerRanges(patternAtlas::registerRange);
        }
        ObjectArtProvider provider = gameModule != null ? gameModule.getObjectArtProvider() : null;
        if (provider == null) {
            objectRenderManager = null;
            registerPlcArtAdapter(null);
            return;
        }

        try {
            int zoneIndex = level != null ? level.getZoneIndex() : -1;
            int artZoneIndex = activeModZoneRuntimeProfile != null
                    && !activeModZoneRuntimeProfile.plcLoads() ? -1 : zoneIndex;
            provider.loadArtForZone(artZoneIndex);

            objectRenderManager = new ObjectRenderManager(provider);
            LOGGER.info("Initializing Object Art. Base Index: " + OBJECT_PATTERN_BASE);
            objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);

            // Register level-tile-based object art (must be after level load)
            provider.registerLevelTileArt(level, artZoneIndex);
            int objectEndIndex = objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);
            if (patternAtlas != null) {
                patternAtlas.registerRange(
                    OBJECT_PATTERN_BASE,
                    alignPatternRangeSize(objectEndIndex - OBJECT_PATTERN_BASE), "Objects");
            }

            hudRenderManager = new HudRenderManager(graphicsManager, camera, gameState);
            hudRenderManager.setProfile(activeHudProfile);
            hudRenderManager.setHudPalettes(provider.getHudTextPaletteLine(), provider.getHudFlashPaletteLine());
            if (activeModZoneRuntimeContribution != null) {
                activeCustomZonePaletteBridge = gameModule.createCustomZonePaletteBridge(
                        activeModZoneRuntimeContribution, level, provider);
            }
            if (activeCustomZonePaletteBridge != null) {
                HudPaletteBridgeAccess.routeLivesPaletteOverrideThroughOwnership(
                        hudRenderManager, true);
            }
            // Wire up HUD to unified UI render pipeline
            if (graphicsManager.getUiRenderPipeline() != null) {
                graphicsManager.getUiRenderPipeline().setHudRenderManager(hudRenderManager);
            }

            // HUD uses a fixed pattern base to avoid collisions with dynamically registered object sheets
            int hudBaseIndex = HUD_PATTERN_BASE;
            Pattern[] hudDigits = provider.getHudDigitPatterns();
            if (hudDigits != null) {
                LOGGER.info("Cached " + hudDigits.length + " HUD Digit patterns at index " + hudBaseIndex);
                for (int i = 0; i < hudDigits.length; i++) {
                    graphicsManager.cachePatternTexture(hudDigits[i], hudBaseIndex + i);
                }
                hudRenderManager.setDigitPatternIndex(hudBaseIndex);

                int nextHudIndex = hudBaseIndex + hudDigits.length;
                HudStaticArt staticHudArt = provider.getHudStaticArt();
                if (staticHudArt != null && staticHudArt.patterns() != null) {
                    LOGGER.info("Cached " + staticHudArt.patterns().length
                            + " HUD Static patterns at index " + nextHudIndex);
                    for (int i = 0; i < staticHudArt.patterns().length; i++) {
                        graphicsManager.cachePatternTexture(staticHudArt.patterns()[i], nextHudIndex + i);
                    }
                    hudRenderManager.setStaticHudArt(nextHudIndex, staticHudArt);
                    hudRenderManager.setLivesPaletteOverrideSupplier(provider::getHudLivesPaletteOverride);
                    nextHudIndex += staticHudArt.patterns().length;
                }

                Pattern[] hudLivesNumbers = provider.getHudLivesNumbers();
                if (hudLivesNumbers != null) {
                    LOGGER.info("Cached " + hudLivesNumbers.length + " HUD Lives Numbers patterns at index "
                            + nextHudIndex);
                    for (int i = 0; i < hudLivesNumbers.length; i++) {
                        graphicsManager.cachePatternTexture(hudLivesNumbers[i], nextHudIndex + i);
                    }
                    hudRenderManager.setLivesNumbersPatternIndex(nextHudIndex);
                    nextHudIndex += hudLivesNumbers.length;
                }

                Pattern[] hudHexDigits = provider.getHudHexDigitPatterns();
                if (hudHexDigits != null) {
                    LOGGER.info("Cached " + hudHexDigits.length + " HUD Hex Digit patterns at index "
                            + nextHudIndex);
                    for (int i = 0; i < hudHexDigits.length; i++) {
                        graphicsManager.cachePatternTexture(hudHexDigits[i], nextHudIndex + i);
                    }
                    hudRenderManager.setHexDigitsPatternIndex(nextHudIndex);
                }
            }

        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load object art.", e);
            objectRenderManager = null;
        }
        registerPlcArtAdapter(activeModZoneRuntimeProfile != null
                && !activeModZoneRuntimeProfile.plcLoads() ? null : provider);
    }

    void submitCustomZonePaletteClaimsForEngine(PaletteOwnershipRegistry registry) {
        if (activeCustomZonePaletteBridge != null && registry != null) {
            activeCustomZonePaletteBridge.submitFrameClaims(registry);
        }
    }

    private void registerPlcArtAdapter(ObjectArtProvider provider) {
        com.openggf.game.session.GameplayModeContext gameplayMode =
                com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerPlcArtAdapter(provider);
        }
    }

    private static int alignPatternRangeSize(int size) {
        int positiveSize = Math.max(size, 1);
        return Math.multiplyExact(Math.floorDiv(Math.addExact(positiveSize, 0xFFF), 0x1000), 0x1000);
    }

    boolean isHudSuppressed() {
        return transitions.isForceHudSuppressed()
                || (zoneFeatureProvider != null
                    && zoneFeatureProvider.shouldSuppressHud(currentZone, currentAct));
    }

    private void initAnimatedPatterns() {
        animatedPatternManager = null;
        if (activeModZoneRuntimeProfile != null
                && !activeModZoneRuntimeProfile.animatedTiles()) {
            registerPatternAnimatorAdapter(null);
            return;
        }
        if (!(game instanceof AnimatedPatternProvider provider)) {
            registerPatternAnimatorAdapter(null);
            return;
        }
        try {
            animatedPatternManager = provider.loadAnimatedPatternManager(level, level.getZoneIndex());
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load animated patterns.", e);
            animatedPatternManager = null;
        }
        registerPatternAnimatorAdapter(animatedPatternManager);
    }

    private void registerPatternAnimatorAdapter(AnimatedPatternManager manager) {
        com.openggf.game.session.GameplayModeContext gameplayMode =
                com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerPatternAnimatorAdapter(manager);
        }
    }

    private void initAnimatedPalettes() {
        animatedPaletteManager = null;
        if (activeModZoneRuntimeProfile != null
                && !activeModZoneRuntimeProfile.animatedTiles()) {
            return;
        }
        if (!(game instanceof AnimatedPaletteProvider provider)) {
            return;
        }
        try {
            animatedPaletteManager = provider.loadAnimatedPaletteManager(level, level.getZoneIndex());
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load animated palettes.", e);
            animatedPaletteManager = null;
        }
    }

    /**
     * Debug Functionality to print each pattern to the screen.
     */
    public void drawAllPatterns() {
        if (debugRenderer != null) {
            debugRenderer.drawAllPatterns();
        }
    }

    /**
     * Renders the current level by processing and displaying collision data.
     * This is currently for debugging purposes to visualize collision areas.
     */
    public void draw() {
        drawWithSpritePriority(null, true);
    }

    @com.openggf.game.ModApi
    public record LevelRenderOptions(boolean includePlayerSprites,
                                     boolean includeObjectSprites,
                                     boolean includeRings,
                                     boolean includeHud,
                                     boolean includeDebugOverlays,
                                     boolean includeObjectArtViewer,
                                     boolean includeWaterSurface) {
        public static LevelRenderOptions gameplay() {
            return new LevelRenderOptions(true, true, true, true, true, true, true);
        }

        public static LevelRenderOptions tilesOnly() {
            return new LevelRenderOptions(false, false, false, false, false, false, false);
        }

        public static LevelRenderOptions previewCapture() {
            return new LevelRenderOptions(false, true, false, false, false, false, false);
        }

        public boolean hasGameplayPass() {
            return includePlayerSprites || includeObjectSprites || includeRings;
        }
    }

    public void drawWithSpritePriority(SpriteManager spriteManager) {
        drawWithSpritePriority(spriteManager, true);
    }

    public void drawWithSpritePriority(SpriteManager spriteManager, boolean includeSpritePass) {
        drawWithRenderOptions(spriteManager,
                includeSpritePass ? LevelRenderOptions.gameplay() : LevelRenderOptions.tilesOnly());
    }

    public void drawWithRenderOptions(SpriteManager spriteManager, LevelRenderOptions renderOptions) {
        levelRenderer.drawWithRenderOptions(spriteManager, renderOptions);
    }

    /**
     * Renders the shared sprite/object gameplay pass used after tile rendering.
     * Delegates to {@link LevelRenderer}.
     */
    public void renderSpriteObjectPass(SpriteManager spriteManager, boolean includeWaterSurface) {
        levelRenderer.renderSpriteObjectPass(spriteManager, includeWaterSurface);
    }

    /**
     * Renders the DEZ background during the ending cutscene.
     * Delegates to {@link LevelRenderer}.
     */
    public void renderEndingBackground(int bgVscroll) {
        levelRenderer.renderEndingBackground(bgVscroll);
    }

    /**
     * Renders the DEZ star field background for the ending cutscene, with an
     * optional backdrop color override. Delegates to {@link LevelRenderer}.
     */
    public void renderEndingBackground(int bgVscroll, float[] backdropOverride) {
        levelRenderer.renderEndingBackground(bgVscroll, backdropOverride);
    }

    public void recomputeParallaxAfterRewindRestore() {
        frameRuntimeUpdater.recomputeParallaxAfterRewindRestore();
    }

    /**
     * Test-only entry point that delegates to {@link LevelRenderer}'s special
     * render effect dispatch. Retained on {@code LevelManager} because existing
     * reflection-based unit tests expect the method to live here.
     */
    @SuppressWarnings("unused")
    private void dispatchSpecialRenderEffects(SpecialRenderEffectStage stage, int frameCounter) {
        levelRenderer.dispatchSpecialRenderEffects(stage, frameCounter);
    }


    void ensureBackgroundTilemapData() {
        if (tilemapManager != null) {
            int bgCameraX = parallaxManager != null ? parallaxManager.getBgCameraX() : Integer.MIN_VALUE;
            applyBackgroundTilemapWindowSelection(bgCameraX);
            tilemapManager.ensureBackgroundTilemapData(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    /**
     * Selects the BG tilemap cache window used by wrapped-background zones.
     * This must run before both render-driven and ad-hoc tilemap builds so they
     * see the same MGZ state-8 cache configuration.
     *
     * @return true when MGZ state 8 should use the full-width per-line BG tilemap path
     */
    boolean applyBackgroundTilemapWindowSelection(int bgCameraX) {
        if (tilemapManager == null) {
            return false;
        }
        boolean fullWidthPerLineTilemap = zoneFeatureProvider != null
                && zoneFeatureProvider.useFullWidthBackgroundTilemapWindow(
                currentZone, currentAct, bgCameraX, cachedBgContiguousWidthPx);
        int newBgPeriodWidth = parallaxManager != null
                ? parallaxManager.getBgPeriodWidth()
                : LevelTilemapManager.VDP_BG_PLANE_WIDTH_PX;
        if (fullWidthPerLineTilemap) {
            if (tilemapManager.getBgTilemapBaseX() != 0) {
                tilemapManager.setBgTilemapBaseX(0);
                tilemapManager.setBackgroundTilemapDirty(true);
            }
            newBgPeriodWidth = cachedBgContiguousWidthPx;
        } else if (bgCameraX != Integer.MIN_VALUE
                && zoneFeatureProvider != null && zoneFeatureProvider.bgWrapsHorizontally()) {
            int newBase = Math.floorDiv(bgCameraX, 16) * 16;
            if (newBase != tilemapManager.getBgTilemapBaseX()) {
                // Window-only change: eligible for the incremental one-column shift.
                tilemapManager.requestBgWindowBaseX(newBase);
            }
        } else if (tilemapManager.getBgTilemapBaseX() != 0) {
            tilemapManager.setBgTilemapBaseX(0);
            tilemapManager.setBackgroundTilemapDirty(true);
        }

        if (newBgPeriodWidth != tilemapManager.getCurrentBgPeriodWidth()) {
            tilemapManager.setCurrentBgPeriodWidth(newBgPeriodWidth);
            tilemapManager.setBackgroundTilemapDirty(true);
        }

        // S3K CNZ miniboss loops a fixed BG band (CNZ1BGE_Boss); anchor/clamp the BG
        // tilemap to that band so the looping scroll excludes the room floor below it.
        int loopBandBaseY = zoneFeatureProvider != null
                ? zoneFeatureProvider.backgroundLoopBandBaseY(currentZone, currentAct)
                : -1;
        if (loopBandBaseY != tilemapManager.getBgLoopBandBaseY()) {
            tilemapManager.setBgLoopBandBaseY(loopBandBaseY);
            tilemapManager.setBackgroundTilemapDirty(true);
        }
        return fullWidthPerLineTilemap;
    }

    void ensureForegroundTilemapData() {
        if (tilemapManager != null) {
            tilemapManager.ensureForegroundTilemapData(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    /**
     * Ensures the live foreground tilemap cache represents the current layout
     * before a ROM-style script mutates layout RAM without requesting a redraw.
     */
    public void snapshotForegroundTilemapBeforeRuntimeLayoutMutation() {
        ensureForegroundTilemapData();
    }

    /**
     * Retrieves the block at a given position.
     *
     * @param layer the layer to retrieve the block from
     * @return the Block at the specified position, or null if not found
     */
    private int getLayerLevelWidthPx(byte layer) {
        if (level == null) {
            return blockPixelSize;
        }
        int widthBlocks = Math.max(1, level.getLayerWidthBlocks(layer));
        return widthBlocks * blockPixelSize;
    }

    private int getLayerLevelHeightPx(byte layer) {
        if (level == null) {
            return blockPixelSize;
        }
        int heightBlocks = Math.max(1, level.getLayerHeightBlocks(layer));
        return heightBlocks * blockPixelSize;
    }

    /**
     * Populates cached FG/BG pixel dimensions from the current level.
     * Must be called after a level is loaded (dimensions are immutable during gameplay).
     */
    private void cacheLevelDimensions() {
        if (level != null) {
            cachedFgWidthPx = getLayerLevelWidthPx((byte) 0);
            cachedFgHeightPx = getLayerLevelHeightPx((byte) 0);
            cachedBgWidthPx = getLayerLevelWidthPx((byte) 1);  // Full map width (matches reference)
            cachedBgContiguousWidthPx = computeActualBgDataWidthPx();  // For bgTilemapBaseX wrapping
            cachedBgHeightPx = getLayerLevelHeightPx((byte) 1);
        } else {
            cachedFgWidthPx = blockPixelSize;
            cachedFgHeightPx = blockPixelSize;
            cachedBgWidthPx = blockPixelSize;
            cachedBgContiguousWidthPx = blockPixelSize;
            cachedBgHeightPx = blockPixelSize;
        }
        blockGrid = new BlockGridIndexer(blockPixelSize);
    }

    /**
     * Builds a LevelGeometry snapshot from the current cached dimensions.
     */
    private LevelGeometry buildGeometry() {
        return new LevelGeometry(level, cachedFgWidthPx, cachedFgHeightPx,
                cachedBgWidthPx, cachedBgContiguousWidthPx, cachedBgHeightPx,
                blockPixelSize, chunksPerBlockSide);
    }

    /**
     * Scan the BG layer (layer 1) to find the contiguous data width.
     * On the Mega Drive, the BG nametable is a 512px-wide ring buffer.
     * The scroll handler fills it from the BG map, wrapping at the map's
     * data width.  The Map stores both FG and BG with the same total width
     * (e.g., 128 blocks = 16384px), but BG data typically only spans a
     * small contiguous region from column 0 (e.g., 8 blocks for HTZ).
     * <p>
     * Using the contiguous BG data width for X wrapping ensures that queries
     * at large camera X positions wrap back to valid BG data rather than
     * reading empty columns in the unused portion of the map.
     * <p>
     * Example: HTZ BG data spans 8 contiguous columns (1024px) within a
     * 128-column map.  Without this fix, bgTilemapBaseX=6144 queries
     * column 48 (empty).  With contiguous width = 1024px wrapping,
     * 6144 mod 1024 = 0 → column 0 (valid).
     */
    private int computeActualBgDataWidthPx() {
        if (level == null || level.getMap() == null) {
            return blockPixelSize;
        }
        Map map = level.getMap();
        int mapWidth = map.getWidth();
        int mapHeight = map.getHeight();

        // Scan left-to-right to find the first all-zero column.
        // This gives the contiguous BG data width starting from column 0,
        // ignoring any stray non-zero blocks at distant columns.
        int contiguousWidth = 0;
        for (int col = 0; col < mapWidth; col++) {
            boolean hasData = false;
            for (int row = 0; row < mapHeight; row++) {
                if ((map.getValue(1, col, row) & 0xFF) != 0) {
                    hasData = true;
                    break;
                }
            }
            if (hasData) {
                contiguousWidth = col + 1;
            } else {
                // Found first empty column - stop here
                break;
            }
        }

        if (contiguousWidth == 0) {
            // No BG data at all — use full map width as fallback
            return mapWidth * blockPixelSize;
        }

        int dataWidthPx = contiguousWidth * blockPixelSize;

        if (dataWidthPx < mapWidth * blockPixelSize) {
            LOGGER.fine("BG contiguous data width: " + contiguousWidth + " blocks ("
                    + dataWidthPx + "px) out of " + mapWidth + " map columns");
        }

        return dataWidthPx;
    }

    /** Fast cached getter for layer pixel width (avoids per-call getLayerWidthBlocks). */
    private int getCachedLayerWidthPx(byte layer) {
        int cached = layer == 0 ? cachedFgWidthPx : cachedBgWidthPx;
        return cached > 0 ? cached : getLayerLevelWidthPx(layer);
    }

    /** Fast cached getter for layer pixel height (avoids per-call getLayerHeightBlocks). */
    private int getCachedLayerHeightPx(byte layer) {
        int cached = layer == 0 ? cachedFgHeightPx : cachedBgHeightPx;
        return cached > 0 ? cached : getLayerLevelHeightPx(layer);
    }

    Block getBlockAtPosition(byte layer, int x, int y) {
        if (level == null || level.getMap() == null) {
            LOGGER.warning("Level or Map is not initialized.");
            return null;
        }

        int levelWidth = getCachedLayerWidthPx(layer);
        int levelHeight = getCachedLayerHeightPx(layer);

        // Handle wrapping for X
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;

        // Handle wrapping for Y
        int wrappedY = y;
        if (layer == 1) {
            // Background loops vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (verticalWrapEnabled) {
            // ROM: LZ3/SBZ2 — FG also wraps vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else {
            // Foreground Clamps
            if (wrappedY < 0 || wrappedY >= levelHeight)
                return null;
        }

        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;

        byte value = map.getValue(layer, mapX, mapY);

        // Mask the value to treat the byte as unsigned
        int blockIndex = value & 0xFF;

        if (blockIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(blockIndex);
        if (block == null) {
            LOGGER.warning("Block at index " + blockIndex + " is null.");
        }

        return block;
    }

    /**
     * Returns the raw block index (0-255) at the given pixel position in the foreground layer.
     * Equivalent to the ROM's Level_Layout lookup used by OilSlides.
     *
     * @param x pixel X coordinate
     * @param y pixel Y coordinate
     * @return block index (0-255), or -1 if out of bounds
     */
    public int getBlockIdAt(int x, int y) {
        if (level == null || level.getMap() == null) {
            return -1;
        }
        int levelWidth = cachedFgWidthPx;
        int levelHeight = cachedFgHeightPx;
        if (levelWidth <= 0 || levelHeight <= 0 || blockPixelSize <= 0) {
            return -1;
        }
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;
        int wrappedY = y;
        if (verticalWrapEnabled) {
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return -1;
        }
        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;
        return map.getValue(0, mapX, mapY) & 0xFF;
    }

    public ChunkDesc getChunkDescAt(byte layer, int x, int y) {
        if (level == null || level.getMap() == null) {
            return null;
        }

        int levelWidth = getCachedLayerWidthPx(layer);
        int levelHeight = getCachedLayerHeightPx(layer);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return null;
        }

        // Wrap X (always wraps)
        int wrappedX = BlockGridIndexer.wrapCoordinate(x, levelWidth);

        // Wrap or clamp Y depending on layer
        int wrappedY = y;
        if (layer == 1 || verticalWrapEnabled) {
            // Background loops vertically; ROM: LZ3/SBZ2 — FG also wraps vertically
            wrappedY = BlockGridIndexer.wrapCoordinate(wrappedY, levelHeight);
        } else {
            // Foreground clamps
            if (wrappedY < 0 || wrappedY >= levelHeight)
                return null;
        }

        // Block lookup (inlined from getBlockAtPosition to reuse wrappedX/wrappedY).
        Map map = level.getMap();
        int mapX = blockGrid.blockIndex(wrappedX);
        int mapY = blockGrid.blockIndex(wrappedY);

        byte value = map.getValue(layer, mapX, mapY);
        int blockIndex = value & 0xFF;

        if (blockIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(blockIndex);
        if (block == null) {
            return null;
        }

        // Intra-block position (reuses already-wrapped coordinates)
        return block.getChunkDesc(blockGrid.blockLocal(wrappedX) / LevelConstants.CHUNK_WIDTH,
                blockGrid.blockLocal(wrappedY) / LevelConstants.CHUNK_HEIGHT);
    }

    /**
     * Returns the ChunkDesc at the given pixel position, optionally resolving
     * Sonic 1 loop collision (low plane uses alternate block index).
     *
     * @param layer        0 = foreground, 1 = background
     * @param x            pixel X
     * @param y            pixel Y
     * @param loopLowPlane if true and layer == 0, resolve collision block index via Level
     * @return the ChunkDesc, or null if out of bounds
     */
    public ChunkDesc getChunkDescAt(byte layer, int x, int y, boolean loopLowPlane) {
        if (!loopLowPlane || layer != 0) {
            return getChunkDescAt(layer, x, y);
        }

        // Loop low plane: resolve collision block via Level.resolveCollisionBlockIndex
        if (level == null || level.getMap() == null) {
            return null;
        }

        int levelWidth = getCachedLayerWidthPx((byte) 0);
        int levelHeight = getCachedLayerHeightPx((byte) 0);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return null;
        }
        int wrappedX = BlockGridIndexer.wrapCoordinate(x, levelWidth);
        int wrappedY = y;
        if (verticalWrapEnabled) {
            wrappedY = BlockGridIndexer.wrapCoordinate(wrappedY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return null;
        }

        Map map = level.getMap();
        int mapX = blockGrid.blockIndex(wrappedX);
        int mapY = blockGrid.blockIndex(wrappedY);

        int rawBlockIndex = map.getValue(0, mapX, mapY) & 0xFF;
        int resolvedIndex = level.resolveCollisionBlockIndex(rawBlockIndex, mapX, mapY);

        if (resolvedIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(resolvedIndex);
        if (block == null) {
            return null;
        }

        return block.getChunkDesc(
                blockGrid.blockLocal(wrappedX) / LevelConstants.CHUNK_WIDTH,
                blockGrid.blockLocal(wrappedY) / LevelConstants.CHUNK_HEIGHT);
    }

    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc, int solidityBitIndex) {
        return getSolidTileForChunkDesc(chunkDesc, solidityBitIndex, solidityBitIndex >= 0x0E);
    }

    public SolidTile getSolidTileForChunkDesc(
            ChunkDesc chunkDesc, int solidityBitIndex, boolean useSecondaryCollisionPath) {
        try {
            if (chunkDesc == null) {
                return null;
            }
            if (!chunkDesc.isSolidityBitSet(solidityBitIndex)) {
                return null;
            }

            Chunk chunk = level.getChunk(chunkDesc.getChunkIndex());
            if (chunk == null) {
                return null;
            }
            // Get collision index - ROM treats index 0 as "no collision"
            // (s2.asm FindFloor line 42963: beq.s loc_1E7E2)
            int collisionIndex = useSecondaryCollisionPath
                    ? chunk.getSolidTileAltIndex()
                    : chunk.getSolidTileIndex();
            if (collisionIndex == 0) {
                return null; // No collision shape assigned
            }
            return level.getSolidTile(collisionIndex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Deprecated or convenience method for backward compatibility if needed,
    // but better to remove or update callers.
    // For now, let's overload it to default to Layer 0 (Primary) if not specified,
    // or we can force update. GroundSensor is the main one.
    // I'll leave a deprecated one just in case, or remove it.
    // GroundSensor calls it. I should update GroundSensor.
    // But I can't leave this here without updating GroundSensor first or it won't
    // compile?
    // Wait, I can overload.
    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc, byte layer) {
        int solidityBitIndex = (layer == 0) ? 0x0C : 0x0E;
        return getSolidTileForChunkDesc(chunkDesc, solidityBitIndex);
    }

    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc) {
        return getSolidTileForChunkDesc(chunkDesc, (byte) 0);
    }

    /**
     * Returns the current level.
     *
     * @return the current Level object
     */
    public Level getCurrentLevel() {
        return level;
    }

    public int getCurrentZone() {
        return currentZone;
    }

    /**
     * Returns the ROM zone ID for the currently loaded level.
     * Unlike {@link #getCurrentZone()} which returns the zone registry progression
     * index, this returns the game-specific zone identifier from the ROM data
     * (e.g. Sonic1Constants.ZONE_MZ = 2 for Marble Zone regardless of gameplay order).
     * Use this when comparing against game-specific zone constants.
     */
    public int getRomZoneId() {
        return level != null ? level.getZoneIndex() : -1;
    }

    /**
     * Returns the effective zone ID for zone features/water logic.
     *
     * <p>Sonic 1 SBZ3 uses the LZ zone slot ({@code id_LZ act 3}) for map/art data,
     * but gameplay systems treat it as SBZ act 3. For feature systems that are keyed
     * by zone/act (water palettes/heights), map that specific case back to SBZ.
     */
    public int getFeatureZoneId() {
        if (level == null || gameModule == null) {
            return level != null ? level.getZoneIndex() : -1;
        }
        int remapped = gameModule.getRemappedFeatureZone(currentZone, currentAct, level.getZoneIndex());
        return remapped >= 0 ? remapped : level.getZoneIndex();
    }

    /**
     * Returns the effective act index for zone features/water logic.
     */
    public int getFeatureActId() {
        if (level == null || gameModule == null) {
            return currentAct;
        }
        int remapped = gameModule.getRemappedFeatureAct(currentZone, currentAct, level.getZoneIndex());
        return remapped >= 0 ? remapped : currentAct;
    }

    public int getCurrentAct() {
        return currentAct;
    }

    /**
     * Returns the apparent act for title card display.
     * ROM: {@code Apparent_act} — stays at 0 during AIZ's seamless fire
     * transition even though {@code Current_act} changes to 1.
     */
    public int getApparentAct() {
        return apparentAct;
    }

    /**
     * Sets the apparent act for title card display.
     * ROM: {@code move.b #1,(Apparent_act).w} — called by the results
     * screen when act 1 ends, so subsequent death/restart title cards
     * show the correct act number.
     */
    public void setApparentAct(int act) {
        this.apparentAct = act;
        worldSession.setApparentAct(act);
    }

    /**
     * Updates a specific palette line with new color data.
     * This is used to load boss palettes during boss fights.
     *
     * @param paletteIndex The palette line to update (0-3)
     * @param paletteData  The raw Sega-format palette data (32 bytes for 16 colors)
     */
    public void updatePalette(int paletteIndex, byte[] paletteData) {
        if (level == null || paletteIndex < 0 || paletteIndex >= 4) {
            return;
        }

        // Create a new palette from the data
        Palette newPalette = new Palette();
        newPalette.fromSegaFormat(paletteData);

        // Update the level's palette object so palette cycling uses the new palette
        // This is critical - without this, palette cycling would re-cache the original
        // level palette, overwriting the boss palette we just loaded
        level.setPalette(paletteIndex, newPalette);

        // Update the graphics manager's cached palette texture
        GraphicsManager graphicsMan = graphicsManager;
        if (graphicsMan.isGlInitialized()) {
            graphicsMan.cachePaletteTexture(newPalette, paletteIndex);
        }

        LOGGER.fine("Updated palette line " + paletteIndex + " with " + paletteData.length + " bytes");
    }

    /**
     * Marks the foreground tilemap as dirty, forcing a rebuild on next render.
     * Call this after modifying the level layout (e.g., placing boss arena walls).
     * This is equivalent to setting Screen_redraw_flag in the original ROM.
     */
    public void invalidateForegroundTilemap() {
        if (tilemapManager != null) {
            tilemapManager.invalidateForegroundTilemap();
        }
    }

    public void reuploadDirtyPatterns(java.util.BitSet dirtyPatterns) {
        dirtyRegionDispatcher.reuploadDirtyPatterns(dirtyPatterns);
    }

    public void resyncObjectSpawnListFromLevel() {
        if (objectManager == null || level == null) {
            return;
        }
        objectManager.resyncSpawnList(level.getObjects());
    }

    public void resyncRingSpawnListFromLevel() {
        if (ringManager == null || level == null) {
            return;
        }
        ringManager.resyncSpawnList(level.getRings());
    }

    public void flushQueuedLayoutMutations() {
        Level currentLevel = getCurrentLevel();
        if (currentLevel == null || !GameServices.hasRuntime()) {
            return;
        }

        LevelMutationSurface surface = LevelMutationSurface.forLevel(currentLevel);
        LayoutMutationContext context = new LayoutMutationContext(surface, this::applyMutationEffects);
        GameServices.zoneLayoutMutationPipeline().flush(context);
    }

    public void applyMutationEffects(MutationEffects effects) {
        dirtyRegionDispatcher.applyMutationEffects(effects);
    }

    /**
     * Reads the foreground tile descriptor currently represented by level data at world coordinates.
     * This resolves block/chunk indirection plus chunk descriptor flips, matching tilemap build logic.
     */
    public int getForegroundTileDescriptorAtWorld(int worldX, int worldY) {
        return getTileDescriptorAtWorld((byte) 0, worldX, worldY);
    }

    /**
     * Reads the background tile descriptor currently represented by level data at world coordinates.
     * This resolves block/chunk indirection plus chunk descriptor flips, matching tilemap build logic.
     */
    public int getBackgroundTileDescriptorAtWorld(int worldX, int worldY) {
        return getTileDescriptorAtWorld((byte) 1, worldX, worldY);
    }

    /**
     * Copies one 16x16 BG source row into the live Plane B tilemap buffer.
     *
     * <p>S3K's Slot Machine bonus stage does this from {@code sub_4ECAA}: {@code d0/d1}
     * select the source BG map row, {@code d5} is the destination VDP plane address,
     * and {@code d6} is the number of longwords to draw. Each longword represents
     * two horizontal 8x8 cells, and the routine writes both 8x8 rows of the
     * 16x16 source block row.
     */
    public boolean copyBackgroundTileRowFromWorldToVdpPlane(int sourceWorldX, int sourceWorldY,
                                                            int destVramAddress, int longWordCount) {
        if (tilemapManager == null || longWordCount <= 0) {
            return false;
        }
        ensureBackgroundTilemapData();
        int bgWidthTiles = tilemapManager.getBackgroundTilemapWidthTiles();
        int bgHeightTiles = tilemapManager.getBackgroundTilemapHeightTiles();
        if (bgWidthTiles <= 0 || bgHeightTiles <= 0) {
            return false;
        }

        int destPlaneOffsetBytes = Math.floorMod(destVramAddress - 0xE000, 0x1000);
        int destCell = destPlaneOffsetBytes / 2;
        int cellCount = longWordCount * 2;
        int sourceStartX = (sourceWorldX >> 4) << 4;
        boolean changed = false;
        for (int row = 0; row < 2; row++) {
            int sourceRowY = sourceWorldY + row * Pattern.PATTERN_HEIGHT;
            int destRowCell = destCell + row * 64; // Plane B is 64 cells wide, so +0x80 bytes per 8x8 row.
            for (int i = 0; i < cellCount; i++) {
                int planeCell = (destRowCell + i) & 0x7FF; // Plane B is 64x32 cells.
                int destTileX = planeCell & 0x3F;
                int destTileY = (planeCell >>> 6) & 0x1F;
                if (destTileX >= bgWidthTiles || destTileY >= bgHeightTiles) {
                    continue;
                }
                int descriptor = getBackgroundTileDescriptorAtWorld(sourceStartX + i * Pattern.PATTERN_WIDTH,
                        sourceRowY);
                changed |= tilemapManager.setBackgroundTileDescriptorAtTilemapCell(destTileX, destTileY, descriptor);
            }
        }
        return changed;
    }

    private int getTileDescriptorAtWorld(byte layer, int worldX, int worldY) {
        if (level == null || level.getMap() == null) {
            return 0;
        }

        int levelWidth = getLayerLevelWidthPx(layer);
        int levelHeight = getLayerLevelHeightPx(layer);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return 0;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (layer == 1 || verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return 0;
        }

        Block block = getBlockAtPosition(layer, wrappedX, wrappedY);
        if (block == null) {
            return 0;
        }

        int xBlockBit = (wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH;
        int yBlockBit = (wrappedY % blockPixelSize) / LevelConstants.CHUNK_HEIGHT;
        ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);
        if (chunkDesc == null) {
            return 0;
        }

        int chunkIndex = chunkDesc.getChunkIndex();
        if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
            return 0;
        }

        Chunk chunk = level.getChunk(chunkIndex);
        if (chunk == null) {
            return 0;
        }

        int tileX = (wrappedX & (LevelConstants.CHUNK_WIDTH - 1)) / Pattern.PATTERN_WIDTH;
        int tileY = (wrappedY & (LevelConstants.CHUNK_HEIGHT - 1)) / Pattern.PATTERN_HEIGHT;
        int logicalX = chunkDesc.getHFlip() ? 1 - tileX : tileX;
        int logicalY = chunkDesc.getVFlip() ? 1 - tileY : tileY;
        PatternDesc patternDesc = chunk.getPatternDesc(logicalX, logicalY);
        if (patternDesc == null) {
            return 0;
        }

        int descriptor = patternDesc.get();
        if (chunkDesc.getHFlip()) {
            descriptor ^= 0x800;
        }
        if (chunkDesc.getVFlip()) {
            descriptor ^= 0x1000;
        }
        return descriptor & 0xFFFF;
    }

    /**
     * Overwrites one foreground tile descriptor at world coordinates in the live FG tilemap buffer.
     * Call {@link #uploadForegroundTilemap()} once after batching writes.
     *
     * @return true if tilemap bytes changed
     */
    public boolean setForegroundTileDescriptorAtWorld(int worldX, int worldY, int descriptor) {
        if (tilemapManager == null) {
            return false;
        }
        return tilemapManager.setForegroundTileDescriptorAtWorld(worldX, worldY, descriptor,
                this::getBlockAtPosition, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
    }

    /**
     * Reads a foreground tile descriptor from the live foreground tilemap buffer at world coordinates.
     * Unlike {@link #getForegroundTileDescriptorAtWorld(int, int)}, this returns the currently visible
     * descriptor after runtime tilemap writes.
     */
    public int getForegroundTileDescriptorFromTilemapAtWorld(int worldX, int worldY) {
        if (tilemapManager == null) {
            return 0;
        }
        return tilemapManager.getForegroundTileDescriptorFromTilemapAtWorld(worldX, worldY,
                this::getBlockAtPosition, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
    }

    /**
     * Uploads the current foreground tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadForegroundTilemap() {
        if (tilemapManager != null) {
            tilemapManager.uploadForegroundTilemap();
        }
    }

    /**
     * Uploads the current background tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadBackgroundTilemap() {
        if (tilemapManager != null) {
            tilemapManager.uploadBackgroundTilemap();
        }
    }

    /**
     * Marks background/foreground tilemaps and pattern lookup as dirty.
     * Use this after runtime terrain art/chunk overlays so the GPU tilemap
     * data is rebuilt on the next render.
     */
    public void invalidateAllTilemaps() {
        if (tilemapManager != null) {
            tilemapManager.invalidateAllTilemaps();
        }
    }

    /**
     * Pre-builds FG and BG tilemap data from the current level state.
     * The pre-built data can later be swapped in via {@link #swapToPrebuiltTilemaps()}
     * to avoid the expensive full-level tilemap rebuild on the transition frame.
     */
    public void prebuildTransitionTilemaps() {
        if (tilemapManager != null) {
            tilemapManager.prebuildTransitionTilemaps(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    /**
     * Swaps pre-built tilemap data into the live arrays, uploads to GPU,
     * and clears FG/BG dirty flags. Still marks pattern lookup dirty
     * (cheap rebuild, needed if pattern count changed from the overlay).
     *
     * @return true if pre-built data was available and swapped in
     */
    public boolean swapToPrebuiltTilemaps() {
        if (tilemapManager == null) {
            return false;
        }
        return tilemapManager.swapToPrebuiltTilemaps();
    }

    /**
     * Returns whether pre-built transition tilemap data is available.
     */
    public boolean hasPrebuiltTilemaps() {
        return tilemapManager != null && tilemapManager.hasPrebuiltTilemaps();
    }

    /**
     * Gets the music ID for the current level.
     * Returns -1 if no level is loaded or music ID cannot be determined.
     */
    public int getCurrentLevelMusicId() {
        if (game == null || levels == null || levels.isEmpty()) {
            return -1;
        }
        try {
            int levelIdx = levels.get(currentZone).get(currentAct).levelIndex();
            return game.getMusicId(levelIdx);
        } catch (Exception e) {
            LOGGER.warning("Failed to get music ID for current level: " + e.getMessage());
            return -1;
        }
    }

    public Collection<ObjectSpawn> getActiveObjectSpawns() {
        if (objectManager == null) {
            return List.of();
        }
        return objectManager.getActiveSpawns();
    }

    public ObjectRenderManager getObjectRenderManager() {
        return objectRenderManager;
    }

    public RingManager getRingManager() {
        return ringManager;
    }

    public int getFrameCounter() {
        return frameCounter;
    }

    /**
     * Aligns this manager's level frame counter during one-time replay/bootstrap
     * setup. ROM {@code Level_frame_counter} is already incremented before
     * {@code Process_Sprites}; the engine stores the previous completed level
     * frame here until {@link #update()} runs near the end of
     * {@code LevelFrameStep}.
     */
    public void setFrameCounter(int frameCounter) {
        this.frameCounter = frameCounter;
    }

    public ZoneFeatureProvider getZoneFeatureProvider() {
        return zoneFeatureProvider;
    }

    public AnimatedPatternManager getAnimatedPatternManager() {
        return animatedPatternManager;
    }

    public AnimatedPaletteManager getAnimatedPaletteManager() {
        return animatedPaletteManager;
    }

    public boolean areAllRingsCollected() {
        return ringManager != null && ringManager.areAllCollected();
    }

    public ObjectManager getObjectManager() {
        return objectManager;
    }

    public void spawnLostRings(AbstractPlayableSprite player, int frameCounter) {
        if (ringManager == null || player == null) {
            return;
        }
        int count = player.getRingCount();
        if (count <= 0) {
            return;
        }
        ringManager.spawnLostRings(player, count, frameCounter);
    }

    public void spawnLostRingsAfterCurrentFrame(AbstractPlayableSprite player, int frameCounter) {
        if (player == null || ringManager == null) {
            return;
        }
        int count = player.getRingCount();
        if (count <= 0) {
            return;
        }
        int preallocatedFirstSlot = -1;
        if (objectManager != null && objectManager.preallocatesLostRingOwnerSlot()) {
            preallocatedFirstSlot = objectManager.allocateDynamicSlotAvoidingCurrentPassFrees();
        }
        int[] preallocatedSlots = preallocatedFirstSlot >= 0
                ? new int[] {preallocatedFirstSlot}
                : new int[0];
        boolean slotsFullyReserved = false;
        if (preallocatedFirstSlot >= 0 && objectManager != null
                && objectManager.lostRingRemainderAllocatesAfterOwnerSlot()) {
            int requested = Math.min(count, 32);
            int[] reserved = new int[requested];
            reserved[0] = preallocatedFirstSlot;
            int reservedCount = 1;
            int previousSlot = preallocatedFirstSlot;
            while (reservedCount < requested) {
                int slot = objectManager.allocateSlotAfter(previousSlot);
                if (slot < 0) {
                    break;
                }
                reserved[reservedCount++] = slot;
                previousSlot = slot;
            }
            preallocatedSlots = java.util.Arrays.copyOf(reserved, reservedCount);
            slotsFullyReserved = true;
        }
        pendingLostRingSpawns.add(new PendingLostRingSpawn(
                player, count, player.getCentreX(), player.getCentreY(), frameCounter,
                preallocatedSlots, slotsFullyReserved));
    }

    private void processPendingLostRingSpawns() {
        if (pendingLostRingSpawns.isEmpty() || ringManager == null) {
            return;
        }
        Iterator<PendingLostRingSpawn> iterator = pendingLostRingSpawns.iterator();
        while (iterator.hasNext()) {
            PendingLostRingSpawn pending = iterator.next();
            if (frameCounter <= pending.frameCounter()) {
                continue;
            }
            if (pending.player().getRingCount() > 0) {
                ringManager.spawnLostRingsWithInitialObjectStep(
                        pending.player(), pending.ringCount(), frameCounter,
                        pending.x(), pending.y(), pending.preallocatedSlots(),
                        pending.slotsFullyReserved());
            } else if (objectManager != null) {
                for (int slot : pending.preallocatedSlots()) {
                    objectManager.releaseDynamicSlot(slot);
                }
            }
            iterator.remove();
        }
    }

    private record PendingLostRingSpawn(
            AbstractPlayableSprite player, int ringCount, int x, int y, int frameCounter,
            int[] preallocatedSlots, boolean slotsFullyReserved) {
    }

    // ── Post-load assembly methods ──────────────────────────────────────
    // Extracted from loadCurrentLevel() so profile steps can delegate to them.
    // Each method corresponds to one post-load InitStep (steps 14-20).

    /**
     * Step 14: Restore checkpoint state after loadLevel() clears it.
     * ROM: S1 Lamp_LoadInfo, S2 Obj79_LoadData, S3K Saved_zone_and_act restore.
     */
    public void restoreCheckpointState(LevelLoadContext ctx) {
        checkpointCoordinator.restoreCheckpointState(ctx);
    }

    private void restoreCheckpointRuntimeState(LevelLoadContext ctx) {
        checkpointCoordinator.restoreRuntimeState(ctx);
    }

    /**
     * Step 15: Set player position from checkpoint or level start.
     * ROM: S1/S2 StartLocations / Obj79_LoadData, S3K Get_PlayerStart.
     */
    public void spawnPlayerAtStartPosition(LevelLoadContext ctx) {
        String mainCode = resolveMainCharacterCode();
        Sprite player = spriteManager.getSprite(mainCode);
        if (player == null) {
            LOGGER.warning("SpawnPlayer: no sprite registered for code '" + mainCode
                    + "' — skipping. Register the player sprite before loadZoneAndAct().");
            return;
        }
        LevelDescriptor levelData = ctx.getLevelData();
        if (levelData == null) {
            levelData = resolveLevelData();
            if (levelData == null) {
                throw new IllegalStateException(
                    "LevelLoadContext.levelData is null and could not be auto-resolved " +
                    "from the levels map (zone=" + currentZone + ", act=" + currentAct + "). " +
                    "Ensure InitGameModule has run before SpawnPlayer.");
            }
            ctx.setLevelData(levelData);
            LOGGER.info("Auto-resolved levelData from levels map: " + levelData);
        }

        int spawnY = -1;
        // ROM: Level_FromSavedGame sets Saved2_* position before level init.
        if (transitions.hasBigRingReturn()) {
            BigRingReturnState br = transitions.getBigRingReturn();
            player.setCentreX((short) br.playerX());
            player.setCentreY((short) br.playerY());
            spawnY = br.playerY();
            LOGGER.info("Set player position from big ring return: X=" + br.playerX() +
                    ", Y=" + br.playerY() + " (center coordinates)");
        } else if (ctx.hasCheckpoint()) {
            player.setCentreX((short) ctx.getCheckpointX());
            player.setCentreY((short) ctx.getCheckpointY());
            spawnY = ctx.getCheckpointY();
            LOGGER.info("Set player position from checkpoint: X=" + ctx.getCheckpointX() +
                    ", Y=" + ctx.getCheckpointY() + " (center coordinates)");
        } else {
            int spawnX = levelData.startX();
            spawnY = levelData.startY();

            if (game instanceof DynamicStartPositionProvider dynamicStartProvider) {
                try {
                    int[] dynamicStart = dynamicStartProvider.getStartPosition(currentZone, currentAct);
                    if (dynamicStart != null && dynamicStart.length >= 2) {
                        spawnX = dynamicStart[0];
                        spawnY = dynamicStart[1];
                        LOGGER.info("Set player position from dynamic start provider: X=" + spawnX +
                                ", Y=" + spawnY + " (zone=" + currentZone + ", act=" + currentAct + ")");
                    } else {
                        LOGGER.info("Dynamic start provider unavailable, using levelData fallback for " +
                                levelData.toString());
                    }
                } catch (IOException e) {
                    LOGGER.warning("DynamicStartPositionProvider failed, using levelData fallback: " + e.getMessage());
                }
            }

            player.setCentreX((short) spawnX);
            player.setCentreY((short) spawnY);
            LOGGER.info("Set player position from level start: X=" + spawnX +
                    ", Y=" + spawnY + " (center coordinates)" +
                    ", level: " + levelData);
        }
        ctx.setSpawnY(spawnY);
    }

    /**
     * Step 16: Reset player state for level start.
     * ROM: S2 InitPlayers state clear, S3K object constructor defaults.
     */
    public void resetPlayerForLevelStart(LevelLoadContext ctx) {
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        playable.resetState();
        playable.setXSpeed((short) 0);
        playable.setYSpeed((short) 0);
        playable.setGSpeed((short) 0);
        // ROM: SBZ3 (spawnY=0) spawns airborne — set air=true so gravity applies.
        playable.setAir(ctx.getSpawnY() == 0);
        LOGGER.info("Player state after loadCurrentLevel: air=" + playable.getAir() +
                ", ySpeed=" + playable.getYSpeed() + ", layer=" + player.getLayer());
        playable.setRolling(false);
        playable.setDead(false);
        playable.setHurt(false);
        playable.setDeathCountdown(0);
        playable.setInvulnerableFrames(0);
        playable.setInvincibleFrames(0);
        playable.setDirection(Direction.RIGHT);
        playable.setAngle((byte) 0);
        player.setLayer((byte) 0);
        playable.setHighPriority(false);
        playable.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        playable.setRingCount(0);
        if (ctx.hasCheckpoint() && ctx.hasCheckpointSolidBits()) {
            playable.setTopSolidBit(ctx.getCheckpointTopSolidBit());
            playable.setLrbSolidBit(ctx.getCheckpointLrbSolidBit());
        }
        audioManager.setSpeedShoes(false);
    }

    /**
     * Step 17: Initialize camera for level start.
     * ROM: S1/S2 SetScreen/InitCameraValues, S3K Get_LevelSizeStart.
     */
    public void initCameraForLevel() {
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        int preSnapCameraX = camera.getX();
        camera.setFrozen(false);
        camera.setFocusedSprite(playable);
        camera.updatePosition(true);

        Level currentLevel = getCurrentLevel();
        if (currentLevel != null) {
            camera.setMinX((short) currentLevel.getMinX());
            camera.setMaxX((short) currentLevel.getMaxX());
            camera.setMinY((short) currentLevel.getMinY());
            camera.setMaxY((short) currentLevel.getMaxY());
            // Vertical wrapping: enabled when minY < 0. The wrap range differs per game:
            // S1 (UNIFIED): 0x800 (DeformLayers.asm LZ3/SBZ2 loop sections)
            // S3K (DUAL_PATH): level height in pixels (e.g. 0x1000 for MGZ1's 32-row map).
            //   The S3K block lookup masks the row index (Layout_row_index_mask=$7C),
            //   so Y coordinates wrap at the map height — rows above the level (negative Y)
            //   map to the bottom rows of the layout.
            if (currentLevel.getMinY() < 0) {
                int wrapRange = isUnifiedCollisionModel()
                        ? Camera.VERTICAL_WRAP_RANGE  // S1: 0x800
                        : cachedFgHeightPx;            // S3K: level height
                camera.setVerticalWrapEnabled(true, wrapRange);
            } else {
                camera.setVerticalWrapEnabled(false);
            }
            verticalWrapEnabled = camera.isVerticalWrapEnabled();
            camera.updatePosition(true);
            if (objectManager != null
                    && (objectManager.usesTwoAxisCursorPlacement()
                            || (camera.getX() != preSnapCameraX
                                    && !objectManager.usesCounterBasedRespawn()))) {
                // The object manager is constructed before the level-start
                // camera snap. Rebuild its initial window once Camera_X_pos
                // matches the new start, otherwise cross-zone loads can seed
                // objects from the previous level's camera band (e.g. SCZ ->
                // WFZ missing ObjB2 at x=$0060). S1 counter-based ObjPosLoad
                // is initialized before the snap; replaying OPL_Main from the
                // snapped camera drops early route objects from the SST window.
                // S3K also needs this for its separate Y-camera placement pass.
                objectManager.reset(camera.getX());
            }
            // ROM parity: only when Get_LevelSizeStart had to clamp the camera
            // Y down to Camera_max_Y_pos does the immediately-following
            // DeformBgLayer call advance Camera_Y_pos past maxY. Levels whose
            // player spawn sits within maxY have no maxY clamp on the snap, so
            // the engine's normal first scroll converges without the ROM quirk.
            // (For S3K AIZ1: player spawn is below maxY, snap clamps to $0390,
            // setup-DeformBgLayer scrolls to $0396; for S1 GHZ1 player spawn is
            // within maxY, no clamp/scroll quirk -- the engine matches ROM
            // exactly without arming the flag.)
        }

        // Apply per-game fast vertical scroll cap from typed camera rules.
        // S1/S2: 16px/frame (s2.asm:18190), S3K: 24px/frame (sonic3k.asm:loc_1C1B0).
        CameraRules cameraRules = cameraRulesFor(activeGameModule());
        if (cameraRules != null) {
            camera.setFastScrollCap(cameraRules.fastScrollCap());
            // ROM S1 leaves the leftward horizontal camera move uncapped (FixBugs=0);
            // S2/S3K cap both directions.
            camera.setUncappedLeftwardScroll(cameraRules.uncappedLeftwardHorizontalScroll());
        }
    }

    private CameraRules cameraRulesFor(GameModule module) {
        GameRules rules = gameRulesFor(module);
        return rules != null ? rules.camera() : null;
    }

    private GameRules gameRulesFor(GameModule module) {
        if (module == null) {
            return null;
        }
        try {
            GameRules rules = module.getRules();
            if (rules != null) {
                return rules;
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
        return null;
    }

    /**
     * Step 18: Initialize level events for dynamic boundary updates.
     * All games: LevelEventProvider.initLevel(zone, act).
     */
    public void initLevelEventsForLevel() {
        LevelEventProvider levelEvents = activeGameModule().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.initLevel(currentZone, currentAct);
        }
    }

    /**
     * Step 19: Spawn sidekicks (Tails etc.) near the main player.
     * S2: InitPlayers multi-char. S3K: SpawnLevelMainSprites_SpawnPlayers (-$20 X, +4 Y).
     *
     * @param xOffset sidekick X offset from player (negative = behind). S2 uses -40, S3K uses -32.
     * @param yOffset sidekick Y offset from player. S2 uses 0, S3K uses +4.
     */
    public void spawnSidekicks(int xOffset, int yOffset) {
        spriteManager.removeTemporarySidekicks();
        Sprite player = spriteManager.getSprite(resolveMainCharacterCode());
        if (player == null) {
            return;
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.setX((short) (player.getX() + xOffset));
            sidekick.setY((short) (player.getY() + yOffset));
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setAir(false);
            sidekick.setDead(false);
            sidekick.setDeathCountdown(0);
            sidekick.setHighPriority(false);
            sidekick.setDirection(Direction.RIGHT);
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().setLevelBounds(
                        (int) camera.getMinX(),
                        (int) camera.getMaxX(),
                        (int) Math.max(camera.getMaxY(), camera.getMaxYTarget()));
                // Capture the leader's spawn centre as the level-start anchor for
                // the deferred sidekick placement / Pos_table prefill. ROM
                // SpawnLevelMainSprites_SpawnPlayers places the CPU sidekick and
                // fills Sonic_Pos_Record_Buf while the leader is at its spawn
                // position, before the first LevelLoop physics tick
                // (sonic3k.asm:8359-8369). The engine's controller placement is
                // deferred to its first updateInit tick, which can land after the
                // leader has moved on a mid-run zone entry, so anchor to this
                // captured spawn centre rather than the live (moved) one.
                if (player instanceof AbstractPlayableSprite leaderSprite) {
                    sidekick.getCpuController().captureLevelStartLeaderAnchor(
                            leaderSprite.getCentreX(),
                            leaderSprite.getCentreY());
                }
            }
        }
    }

    /**
     * Step 20: Request title card display.
     * Skipped in headless mode and when zone feature provider suppresses it.
     */
    public void requestTitleCardIfNeeded(LevelLoadContext ctx) {
        if (ctx.isShowTitleCard()
                && !graphicsManager.isHeadlessMode()
                && !(zoneFeatureProvider != null && zoneFeatureProvider.shouldSuppressInitialTitleCard(currentZone, currentAct))) {
            // ROM: title card reads Apparent_act, not Current_act.
            // After AIZ's seamless fire transition, Current_act is 1 but
            // Apparent_act stays 0 until the results screen exits.
            requestTitleCard(currentZone, apparentAct);
        }
    }

    /**
     * Resolves the {@link LevelDescriptor} for the current zone and act from the
     * {@code levels} map.
     * <p>
     * Used as a fallback when {@code LevelLoadContext.levelData} has not been
     * pre-seeded by the caller. Returns {@code null} if the levels map is
     * empty or the current zone/act is out of bounds.
     */
    private LevelDescriptor resolveLevelData() {
        if (levels.isEmpty() || currentZone < 0 || currentZone >= levels.size()) {
            return null;
        }
        List<LevelDescriptor> acts = levels.get(currentZone);
        if (acts == null || currentAct < 0 || currentAct >= acts.size()) {
            return null;
        }
        return acts.get(currentAct);
    }

    /**
     * Loads the current level with title card.
     * Use this for fresh level starts (zone/act changes).
     */
    public void loadCurrentLevel() {
        loadCurrentLevel(true);
    }

    /**
     * Loads the current level for death respawn (no title card).
     */
    public void respawnPlayer() {
        loadCurrentLevel(false);
    }

    public void loadCurrentLevel(LevelLoadMode loadMode, boolean showTitleCard) {
        loadCurrentLevel(showTitleCard, loadMode);
    }

    /**
     * Loads the current level with optional title card.
     *
     * @param showTitleCard true to show title card on fresh starts, false for death
     *                      respawns
     */
    private void loadCurrentLevel(boolean showTitleCard) {
        loadCurrentLevel(showTitleCard, LevelLoadMode.FULL);
    }

    private void loadCurrentLevel(boolean showTitleCard, LevelLoadMode loadMode) {
        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            transitions.setLevelInactiveForTransition(false);

            if (levels.isEmpty()) {
                // ROM is already loaded by Engine.initializeGame(), so
                // GameModuleRegistry has the correct module. Just bootstrap
                // the zone list for level data lookup.
                gameModule = GameServices.module();
                refreshZoneList();
            }
            LevelDescriptor levelData = levels.get(currentZone).get(currentAct);

            LevelLoadContext ctx = new LevelLoadContext();
            ctx.setShowTitleCard(showTitleCard);
            ctx.setLevelData(levelData);
            ctx.setIncludePostLoadAssembly(true);
            ctx.snapshotCheckpoint(checkpointCoordinator.state());

            loadLevel(levelData.levelIndex(), loadMode, ctx);
            if (loadMode != LevelLoadMode.PREVIEW_CAPTURE) {
                applyPersistedEditorEdits();
            }
            restoreCheckpointRuntimeState(ctx);

            frameCounter = 0;
            sidekickRomVisibleReloadFrameCounterBridgeActive = false;
            sidekickRomVisibleReloadFrameCounterBridgePrimed = false;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyPersistedEditorEdits() {
        if (level == null || gameModule == null) {
            return;
        }
        if (editorSaveManager == null) {
            return;
        }
        MutableLevel mutableLevel = level instanceof MutableLevel existing
                ? existing
                : MutableLevel.snapshot(level);
        EditorSaveManager.ApplyResult result = editorSaveManager.tryApplyEdits(
                gameModule.getGameId(), gameModule.getObjectPlacementEncoding(),
                currentZone, currentAct, mutableLevel);
        if (result == EditorSaveManager.ApplyResult.APPLIED && mutableLevel != level) {
            setLevel(mutableLevel);
        }
    }

    public void nextAct() throws IOException {
        writeCurrentAct(currentAct + 1);
        if (currentAct >= levels.get(currentZone).size()) {
            writeCurrentAct(0);
        }
        writeApparentAct(currentAct);
        // Clear checkpoint when manually changing level
        checkpointCoordinator.clear();
        loadCurrentLevel();
    }

    /**
     * Advance to the next level in progression order.
     * Unlike nextAct() which wraps, this advances to next zone when acts are
     * exhausted.
     * Called by results screen after tally completes.
     * <p>
     * S1/S2 results-screen objects call this directly (bypassing the
     * request/consume transition queue GameLoop otherwise drives), so a
     * finished/abandoned time attack attempt is gated here rather than at a
     * GameLoop consume site: when {@code GameStateManager.isTimeAttackActive()}
     * is true, this queues a {@link LevelTransitionCoordinator#requestTimeAttackMenuReturn()}
     * and returns without touching the zone/act counters or loading anything —
     * GameLoop consumes that request on the next frame and routes to the time
     * attack menu instead.
     */
    public void advanceToNextLevel() throws IOException {
        if (gameState.isTimeAttackActive()) {
            transitions.requestTimeAttackMenuReturn();
            return;
        }
        ZoneProgressionPlan.ZoneTopology topology = activeProgressionTopology();
        ZoneProgressionPlan.ProgressionResult next = zoneProgressionPlan.next(
                topology, currentZone, currentAct);
        if (next == ZoneProgressionPlan.Credits.INSTANCE) {
            // Preserve an out-of-range sentinel rather than wrapping to zone
            // zero, including when a terminal stock zone precedes appended mods.
            writeCurrentZone(topology.zoneCount());
            writeCurrentAct(0);
            requestCreditsTransition();
            return;
        }
        ZoneProgressionPlan.Successor successor = (ZoneProgressionPlan.Successor) next;
        writeCurrentZone(successor.zone());
        writeCurrentAct(successor.act());
        writeApparentAct(currentAct);
        // Clear checkpoint when advancing
        checkpointCoordinator.clear();
        loadCurrentLevel();
    }

    /**
     * Advances zone/act counters without loading the level.
     * Used when entering special stage from big ring - the ROM advances
     * the level counters before entering the special stage (Got_NextLevel).
     */
    public void advanceZoneActOnly() {
        ZoneProgressionPlan.ProgressionResult next = zoneProgressionPlan.next(
                activeProgressionTopology(), currentZone, currentAct);
        if (next == ZoneProgressionPlan.Credits.INSTANCE) {
            writeCurrentAct(0);
            writeCurrentZone(0);
        } else {
            ZoneProgressionPlan.Successor successor = (ZoneProgressionPlan.Successor) next;
            writeCurrentZone(successor.zone());
            writeCurrentAct(successor.act());
        }
        writeApparentAct(currentAct);
        checkpointCoordinator.clear();
        transitions.setSpecialStageReturnLevelReloadRequested(true);
    }

    public void loadZoneAndAct(int zone, int act) throws IOException {
        loadZoneAndAct(zone, act, LevelLoadMode.FULL);
    }

    public void loadZoneAndAct(int zone, int act, LevelLoadMode loadMode) throws IOException {
        writeCurrentAct(act);
        writeApparentAct(act);
        writeCurrentZone(zone);
        // Clear checkpoint when manually changing level
        checkpointCoordinator.clear();
        loadCurrentLevel(loadMode != LevelLoadMode.PREVIEW_CAPTURE, loadMode);
    }

    /**
     * Performs a ROM-aligned act transition: reloads layout + collision,
     * resets managers, applies offsets, and restores camera bounds.
     * <p>
     * This bypasses the profile system entirely because act transitions
     * are NOT level loads in the ROM — they are in-place data swaps
     * performed by level event background routines.
     * <p>
     * ROM reference: S3K zone BG event handlers (e.g. AIZ Act 2 transition
     * at sonic3k.asm). Pattern: set zone/act → Load_Level + LoadSolids →
     * Offset_ObjectsDuringTransition → clear managers → restore camera bounds.
     *
     * @param request the transition request with target zone/act, offsets, etc.
     * @throws IOException if level data loading fails
     */
    public void executeActTransition(SeamlessLevelTransitionRequest request) throws IOException {
        actTransitionExecutor.execute(request);
    }

    void restoreCameraBoundsForCurrentLevel(Camera cam) {
        Level currentLevel = getCurrentLevel();
        if (currentLevel == null) {
            return;
        }
        cam.setMinX((short) currentLevel.getMinX());
        cam.setMaxX((short) currentLevel.getMaxX());
        cam.setMinY((short) currentLevel.getMinY());
        cam.setMaxY((short) currentLevel.getMaxY());
        if (currentLevel.getMinY() < 0) {
            int wrapRange = isUnifiedCollisionModel()
                    ? Camera.VERTICAL_WRAP_RANGE
                    : cachedFgHeightPx;
            cam.setVerticalWrapEnabled(true, wrapRange);
        } else {
            cam.setVerticalWrapEnabled(false);
        }
        verticalWrapEnabled = cam.isVerticalWrapEnabled();
    }

    void applyPostTransitionCameraOverrides(SeamlessLevelTransitionRequest request, Camera cam) {
        if (request == null) {
            return;
        }
        Integer minX = request.postTransitionMinX();
        if (minX != null) {
            cam.setMinX((short) (int) minX);
        }
        Integer maxX = request.postTransitionMaxX();
        if (maxX != null) {
            cam.setMaxX((short) (int) maxX);
        }
        Integer minY = request.postTransitionMinY();
        if (minY != null) {
            cam.setMinY((short) (int) minY);
        }
        Integer maxY = request.postTransitionMaxY();
        if (maxY != null) {
            cam.setMaxY((short) (int) maxY);
        }
        Integer maxYTarget = request.postTransitionMaxYTarget();
        if (maxYTarget != null) {
            cam.setMaxYTarget((short) (int) maxYTarget);
        }
    }

    /**
     * Shifts persistent dynamic objects carried across a seamless reload by the
     * transition world delta, mirroring ROM {@code Offset_ObjectsDuringTransition}.
     * The delta matches the player offset (player/camera/object offsets are the
     * same world shift for every S3K seamless act transition).
     */
    void offsetCarriedObjectsForTransition(List<ObjectInstance> carried,
                                                   SeamlessLevelTransitionRequest request) {
        if (request == null || carried == null || carried.isEmpty()) {
            return;
        }
        int offsetX = request.playerOffsetX();
        int offsetY = request.playerOffsetY();
        if (offsetX == 0 && offsetY == 0) {
            return;
        }
        for (ObjectInstance instance : carried) {
            if (instance != null && !com.openggf.level.objects.ObjectCallbackDispatch.call(
                    objectManager, instance, instance::isDestroyed)) {
                com.openggf.level.objects.ObjectCallbackDispatch.run(objectManager, instance,
                        () -> instance.onCarriedAcrossSeamlessTransition(offsetX, offsetY));
            }
        }
    }

    void applySeamlessOffsets(SeamlessLevelTransitionRequest request, Camera cam) {
        if (request == null) {
            return;
        }
        if (cam.getFocusedSprite() instanceof AbstractPlayableSprite playable) {
            int newX = playable.getCentreX() + request.playerOffsetX();
            int newY = playable.getCentreY() + request.playerOffsetY();
            // ROM transition offset code adjusts the position words only
            // (for AIZ1->AIZ2: sub.w d0/d1 from x_pos/y_pos). The subpixel
            // words must survive the reload or fixed-point motion resumes from
            // the wrong fraction.
            playable.setCentreXPreserveSubpixel((short) newX);
            playable.setCentreYPreserveSubpixel((short) newY);
            // The level reload replaced the pattern buffer; force DPLC re-upload
            // so the player sprite is visible on the next draw.
            if (playable.getSpriteRenderer() != null) {
                playable.getSpriteRenderer().invalidateDplcCache();
            }
            // Persistent insta-shield survives transitions but the ObjectManager was rebuilt
            // (rebuildManagersForActTransition creates a new one). Re-register + invalidate DPLC.
            if (playable.getInstaShieldObject() != null) {
                playable.markInstaShieldForReregistration();
                playable.getInstaShieldObject().invalidateDplcCache();
            }
            // ROM Load_Level clears Dynamic_object_RAM. If the player was riding
            // an act-1 transition helper, the next ExecuteObjects pass clears the
            // stale on-object bit and produces the one-frame airborne handoff.
            if (request.forceAirOnStaleObjectSupportLoss() && objectManager != null) {
                objectManager.forceAirOnStaleObjectSupportLoss(playable);
            }
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            int newX = sidekick.getCentreX() + request.playerOffsetX();
            int newY = sidekick.getCentreY() + request.playerOffsetY();
            sidekick.setCentreXPreserveSubpixel((short) newX);
            sidekick.setCentreYPreserveSubpixel((short) newY);
            if (sidekick.getSpriteRenderer() != null) {
                sidekick.getSpriteRenderer().invalidateDplcCache();
            }
            if (request.forceAirOnStaleObjectSupportLoss() && objectManager != null) {
                objectManager.forceAirOnStaleObjectSupportLoss(sidekick);
            }
        }
        cam.setX((short) (cam.getX() + request.cameraOffsetX()));
        cam.setY((short) (cam.getY() + request.cameraOffsetY()));
    }

    /**
     * Rebuilds object and ring managers with the new act's spawn data.
     * <p>
     * ROM behavior: {@code Load_Level} swaps the object/ring position index
     * pointers, then clears {@code Dynamic_object_RAM} and
     * {@code Ring_status_table}. Because our managers hold immutable spawn
     * lists from construction, a simple {@code reset()} only clears runtime
     * state without swapping in the new act's spawn sources. We must
     * reconstruct both managers so they reference {@code level.getObjects()}
     * and {@code level.getRings()} from the newly loaded act.
     */
    void rebuildManagersForActTransition(Camera cam, List<ObjectInstance> persistentDynamicObjects) {
        int cameraX = cam.getX();
        ObjectManager previousObjectManager = objectManager;

        // Rebuild ObjectManager with the new act's object spawns
        objectManager = new ObjectManager(level.getObjects(),
                gameModule.createObjectRegistry(),
                gameModule.getPlaneSwitcherObjectId(),
                gameModule.getPlaneSwitcherConfig(),
                touchResponseTable,
                graphicsManager,
                camera,
                buildObjectServices());
        objectManager.setRewindClassResolver(rewindClassResolver);
        GameRules gameRules = gameModule.getRules();
        if (gameRules != null
                && gameRules.collision() != null
                && gameRules.collision().collisionModel() == com.openggf.game.CollisionModel.UNIFIED) {
            objectManager.enableCounterBasedRespawn();
        } else {
            objectManager.enableExecThenLoadPlacement();
            objectManager.enforceSlotLimit();
        }
        if (gameRules != null
                && gameRules.objectInteraction() != null
                && gameRules.objectInteraction().permanentRespawnTableLatch()) {
            objectManager.enablePermanentDestroyLatch();
        }
        collisionSystem.setObjectManager(objectManager);
        objectManager.reset(cameraX);
        if (previousObjectManager != null && persistentDynamicObjects != null) {
            com.openggf.level.objects.ObjectCallbackDispatch.inheritOwners(
                    objectManager, previousObjectManager, persistentDynamicObjects);
        }

        // Rebuild RingManager with the new act's ring spawns
        RingSpriteSheet ringSpriteSheet = level.getRingSpriteSheet();
        ringManager = new RingManager(level.getRings(), ringSpriteSheet, this, touchResponseTable, audioManager);
        ringManager.reset(cameraX);
        ringManager.ensurePatternsCached(graphicsManager, level.getPatternCount());

        // Re-register player dynamic objects (shield, invincibility) that were
        // orphaned when the old ObjectManager was replaced.
        // ROM: these live in Dynamic_object_RAM which persists across act transitions.
        reregisterPlayerDynamicObjects(cam.getFocusedSprite());
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            reregisterPlayerDynamicObjects(sidekick);
        }
        if (persistentDynamicObjects != null) {
            for (ObjectInstance object : persistentDynamicObjects) {
                if (object != null && !com.openggf.level.objects.ObjectCallbackDispatch.call(
                        objectManager, object, object::isDestroyed)) {
                    objectManager.addDynamicObject(object);
                }
            }
        }
    }

    private ObjectServices buildObjectServices() {
        var gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null && gameplayMode.getLevelManager() == this && engineServices != null) {
            return new DefaultObjectServices(gameplayMode, engineServices);
        }
        throw new IllegalStateException("LevelManager.buildObjectServices() requires the active GameplayModeContext");
    }

    private void reregisterPlayerDynamicObjects(Sprite sprite) {
        if (!(sprite instanceof AbstractPlayableSprite playable)) {
            return;
        }
        // Re-inject spawner since ObjectManager was rebuilt
        playable.setPowerUpSpawner(new DefaultPowerUpSpawner(objectManager));
        PowerUpObject shield = playable.getShieldObject();
        if (shield != null && !shield.isDestroyed()) {
            playable.getPowerUpSpawner().registerObject(shield);
        }
        PowerUpObject invincibility = playable.getInvincibilityObject();
        if (invincibility != null && !invincibility.isDestroyed()) {
            playable.getPowerUpSpawner().registerObject(invincibility);
        }
    }

    void initLevelEventsForCurrentZoneAct() {
        LevelEventProvider levelEvents = activeGameModule().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.initLevel(currentZone, currentAct);
        }
    }

    public void nextZone() throws IOException {
        writeCurrentZone(currentZone + 1);
        if (currentZone >= levels.size()) {
            writeCurrentZone(0);
        }
        writeCurrentAct(0);
        writeApparentAct(0);
        // Clear checkpoint when manually changing level
        checkpointCoordinator.clear();
        loadCurrentLevel();
    }

    public void loadZone(int zone) throws IOException {
        writeCurrentZone(zone);
        writeCurrentAct(0);
        writeApparentAct(0);
        // Clear checkpoint when manually changing level
        checkpointCoordinator.clear();
        loadCurrentLevel();
    }

    public RespawnState getCheckpointState() {
        return checkpointCoordinator.state();
    }

    public CheckpointState.RewindState captureCheckpointStateForRewind() {
        return checkpointCoordinator.captureRewindState();
    }

    public void restoreCheckpointStateForRewind(CheckpointState.RewindState checkpointRewindState) {
        if (checkpointRewindState == null) {
            return;
        }
        checkpointCoordinator.restoreRewindState(checkpointRewindState);
    }

    // ==================== Transition Coordinator Delegation ====================
    // Thin wrappers that delegate to LevelTransitionCoordinator.
    /** Returns the transition coordinator. */
    public LevelTransitionCoordinator getTransitions() { return transitions; }

    /** @see LevelTransitionCoordinator#requestSpecialStageFromCheckpoint() */
    public void requestSpecialStageFromCheckpoint() { transitions.requestSpecialStageFromCheckpoint(); }

    /** @see LevelTransitionCoordinator#requestSpecialStageEntry() */
    public void requestSpecialStageEntry() { transitions.requestSpecialStageEntry(); }

    /** @see LevelTransitionCoordinator#consumeSpecialStageRequest() */
    public boolean consumeSpecialStageRequest() { return transitions.consumeSpecialStageRequest(); }

    /** @see LevelTransitionCoordinator#consumeSpecialStageReturnLevelReloadRequest() */
    public boolean consumeSpecialStageReturnLevelReloadRequest() { return transitions.consumeSpecialStageReturnLevelReloadRequest(); }

    /** @see LevelTransitionCoordinator#requestBonusStageEntry(BonusStageType) */
    public void requestBonusStageEntry(BonusStageType type) { transitions.requestBonusStageEntry(type); }

    /** @see LevelTransitionCoordinator#consumeBonusStageRequest() */
    public BonusStageType consumeBonusStageRequest() { return transitions.consumeBonusStageRequest(); }

    /** @see LevelTransitionCoordinator#saveBigRingReturn(BigRingReturnState) */
    public void saveBigRingReturn(BigRingReturnState state) { transitions.saveBigRingReturn(state); }

    /** @see LevelTransitionCoordinator#hasBigRingReturn() */
    public boolean hasBigRingReturn() { return transitions.hasBigRingReturn(); }

    /** @see LevelTransitionCoordinator#getBigRingReturn() */
    public BigRingReturnState getBigRingReturn() { return transitions.getBigRingReturn(); }

    /** @see LevelTransitionCoordinator#clearBigRingReturn() */
    public void clearBigRingReturn() { transitions.clearBigRingReturn(); }

    /** @see LevelTransitionCoordinator#setBonusStageReturnCheckpointIndex(int) */
    public void setBonusStageReturnCheckpointIndex(int idx) { transitions.setBonusStageReturnCheckpointIndex(idx); }

    /** @see LevelTransitionCoordinator#isBonusStageReturn() */
    public boolean isBonusStageReturn() { return transitions.isBonusStageReturn(); }

    /** @see LevelTransitionCoordinator#getBonusStageReturnCheckpointIndex() */
    public int getBonusStageReturnCheckpointIndex() { return transitions.getBonusStageReturnCheckpointIndex(); }

    /** @see LevelTransitionCoordinator#clearBonusStageReturn() */
    public void clearBonusStageReturn() { transitions.clearBonusStageReturn(); }

    /** @see LevelTransitionCoordinator#requestTitleCard(int, int) */
    public void requestTitleCard(int zone, int act) { transitions.requestTitleCard(zone, act); }

    /** @see LevelTransitionCoordinator#requestInLevelTitleCard(int, int) */
    public void requestInLevelTitleCard(int zone, int act) { transitions.requestInLevelTitleCard(zone, act); }

    /** @see LevelTransitionCoordinator#isTitleCardRequested() */
    public boolean isTitleCardRequested() { return transitions.isTitleCardRequested(); }

    /**
     * @return true if vertical wrapping is active (ROM: LZ3/SBZ2 loop sections)
     */
    public boolean isVerticalWrapEnabled() {
        return verticalWrapEnabled;
    }

    /** @see LevelTransitionCoordinator#consumeTitleCardRequest() */
    public boolean consumeTitleCardRequest() { return transitions.consumeTitleCardRequest(); }

    /** @see LevelTransitionCoordinator#consumeInLevelTitleCardRequest() */
    public boolean consumeInLevelTitleCardRequest() { return transitions.consumeInLevelTitleCardRequest(); }

    /** @see LevelTransitionCoordinator#getTitleCardZone() */
    public int getTitleCardZone() { return transitions.getTitleCardZone(); }

    /** @see LevelTransitionCoordinator#getTitleCardAct() */
    public int getTitleCardAct() { return transitions.getTitleCardAct(); }

    /** @see LevelTransitionCoordinator#getInLevelTitleCardZone() */
    public int getInLevelTitleCardZone() { return transitions.getInLevelTitleCardZone(); }

    /** @see LevelTransitionCoordinator#getInLevelTitleCardAct() */
    public int getInLevelTitleCardAct() { return transitions.getInLevelTitleCardAct(); }

    /**
     * Resets gameplay-owned mutable state without clearing the durable
     * {@link com.openggf.game.session.WorldSession} level and zone metadata.
     */
    public void resetGameplayState() {
        com.openggf.game.session.GameplayModeContext gameplayMode =
                com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null && gameplayMode.getRewindRegistry() != null) {
            gameplayMode.getRewindRegistry().deregister("level");
            gameplayMode.getRewindRegistry().deregister("object-manager");
            gameplayMode.getRewindRegistry().deregister("level-event");
        }
        level = null;
        game = null;
        gameModule = null;
        objectManager = null;
        ringManager = null;
        zoneFeatureProvider = null;
        levelRenderer.resetState();
        objectRenderManager = null;
        hudRenderManager = null;
        activeHudProfile = HudProfile.stock();
        activeModZoneRuntimeContribution = null;
        activeModZoneRuntimeProfile = null;
        activeCustomZonePaletteBridge = null;
        animatedPatternManager = null;
        animatedPaletteManager = null;
        checkpointCoordinator.resetState();
        levelGamestate = null;
        if (tilemapManager != null) {
            tilemapManager.resetState();
        }
        tilemapManager = null;
        currentZone = 0;
        currentAct = 0;
        apparentAct = 0;
        frameCounter = 0;
        sidekickRomVisibleReloadFrameCounterBridgeActive = false;
        sidekickRomVisibleReloadFrameCounterBridgePrimed = false;
        transitions.resetState();
        verticalWrapEnabled = false;
        touchResponseTable = null;
        useShaderBackground = true;
        cacheLevelDimensions();
        levels.clear();
        activeModZoneRuntimeProfile = null;
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Replaces the reflection-based tearDown hacks in test classes.
     */
    public void resetState() {
        resetGameplayState();
        writeCurrentLevel(null);
        writeCurrentZone(0);
        writeCurrentAct(0);
        writeApparentAct(0);
    }

    /**
     * Reset the frame counter to 0.
     * Used for deterministic visual regression testing to ensure animations
     * are in a consistent state between reference generation and test runs.
     */
    public void resetFrameCounter() {
        this.frameCounter = 0;
        sidekickRomVisibleReloadFrameCounterBridgeActive = false;
        sidekickRomVisibleReloadFrameCounterBridgePrimed = false;
    }

    public void setClearColor() {
        if (level == null) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            return;
        }
        Palette.Color backdrop = resolveLevelBackdropColor();
        glClearColor(backdrop.rFloat(), backdrop.gFloat(), backdrop.bFloat(), 1.0f);
    }

    Palette.Color resolveLevelBackdropColor() {
        if (level == null) {
            return BLACK_BACKDROP;
        }
        if (isForceBlackBackdrop()) {
            return BLACK_BACKDROP;
        }
        return level.getBackdropColor();
    }

    private boolean isForceBlackBackdrop() {
        ZoneFeatureProvider zfp = zoneFeatureProvider;
        return zfp != null && zfp.isForceBlackBackdrop();
    }

    /**
     * Reloads the current level's palettes into the graphics manager.
     * Call this after returning from special stage to restore level colors.
     */
    public void reloadLevelPalettes() {
        if (level == null) {
            LOGGER.warning("Cannot reload palettes: no level loaded");
            return;
        }

        int paletteCount = level.getPaletteCount();
        for (int i = 0; i < paletteCount; i++) {
            Palette palette = level.getPalette(i);
            if (palette != null) {
                graphicsManager.cachePaletteTexture(palette, i);
            }
        }
        LOGGER.fine("Reloaded " + paletteCount + " level palettes");
    }

    // ==================== Transition Request Delegation ====================
    // These delegate to LevelTransitionCoordinator so external callers keep working.

    /** @see LevelTransitionCoordinator#requestRespawn() */
    public void requestRespawn() { transitions.requestRespawn(); }

    /** @see LevelTransitionCoordinator#consumeRespawnRequest() */
    public boolean consumeRespawnRequest() { return transitions.consumeRespawnRequest(); }

    public boolean isRespawnRequestedForRewind() { return transitions.isRespawnRequested(); }

    public void restoreRespawnRequestedForRewind(boolean respawnRequested) {
        transitions.restoreRespawnRequested(respawnRequested);
    }

    /** @see LevelTransitionCoordinator#requestNextAct() */
    public void requestNextAct() { transitions.requestNextAct(); }

    /** @see LevelTransitionCoordinator#consumeNextActRequest() */
    public boolean consumeNextActRequest() { return transitions.consumeNextActRequest(); }

    /** @see LevelTransitionCoordinator#requestNextZone() */
    public void requestNextZone() { transitions.requestNextZone(); }

    /** @see LevelTransitionCoordinator#consumeNextZoneRequest() */
    public boolean consumeNextZoneRequest() { return transitions.consumeNextZoneRequest(); }

    /** @see LevelTransitionCoordinator#requestZoneAndAct(int, int) */
    public void requestZoneAndAct(int zone, int act) { transitions.requestZoneAndAct(zone, act); }

    /** @see LevelTransitionCoordinator#requestZoneAndAct(int, int, boolean) */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) { transitions.requestZoneAndAct(zone, act, deactivateLevelNow); }

    /** @see LevelTransitionCoordinator#requestSeamlessTransition(SeamlessLevelTransitionRequest) */
    public void requestSeamlessTransition(SeamlessLevelTransitionRequest request) { transitions.requestSeamlessTransition(request); }

    /** @see LevelTransitionCoordinator#consumeSeamlessTransitionRequest() */
    public SeamlessLevelTransitionRequest consumeSeamlessTransitionRequest() { return transitions.consumeSeamlessTransitionRequest(); }

    /**
     * Applies a seamless transition immediately.
     * <p>
     * Routes through {@link #executeActTransition} for RELOAD types,
     * which bypasses the profile system and matches ROM behavior.
     */
    public void applySeamlessTransition(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }

        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            switch (request.type()) {
                case MUTATE_ONLY -> applySeamlessMutation(request.mutationKey());
                case RELOAD_SAME_LEVEL -> {
                    SeamlessLevelTransitionRequest adjusted = SeamlessLevelTransitionRequest
                            .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                            .targetZoneAct(currentZone, currentAct)
                            .deactivateLevelNow(request.deactivateLevelNow())
                            .preserveMusic(request.preserveMusic())
                            .preserveLevelGamestate(request.preserveLevelGamestate())
                            .showInLevelTitleCard(request.showInLevelTitleCard())
                            .forceAirOnStaleObjectSupportLoss(request.forceAirOnStaleObjectSupportLoss())
                            .preserveOffsetCameraPosition(request.preserveOffsetCameraPosition())
                            .postTransitionMinXIfPresent(request.postTransitionMinX())
                            .postTransitionMaxXIfPresent(request.postTransitionMaxX())
                            .postTransitionMinYIfPresent(request.postTransitionMinY())
                            .postTransitionMaxYIfPresent(request.postTransitionMaxY())
                            .postTransitionMaxYTargetIfPresent(request.postTransitionMaxYTarget())
                            .playerOffset(request.playerOffsetX(), request.playerOffsetY())
                            .cameraOffset(request.cameraOffsetX(), request.cameraOffsetY())
                            .mutationKey(request.mutationKey())
                            .musicOverrideId(request.musicOverrideId())
                            .build();
                    executeActTransition(adjusted);
                    advanceFrameCounterAcrossSeamlessReload();
                }
                case RELOAD_TARGET_LEVEL -> {
                    executeActTransition(request);
                    advanceFrameCounterAcrossSeamlessReload();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply seamless transition", e);
        } finally {
            transitions.setLevelInactiveForTransition(false);
        }
    }

    /**
     * ROM {@code Level_frame_counter} (incremented in VInt_0_Main every gameplay
     * frame) keeps ticking through the act-reload frame; the engine's
     * {@code GameLoop} and headless test runner both {@code return} after
     * applying a seamless reload transition, skipping
     * {@code SpriteManager.update()} (which is where
     * {@code SpriteManager.frameCounter} normally increments). Bump it
     * explicitly here so sidekick AI gates that read
     * {@code (Level_frame_counter & MASK)} — e.g. sonic3k.asm:26775 loc_13E9C
     * 64-frame jump-cadence check — fire on the same frames as the ROM after
     * AIZ act 1 → act 2 reload.
     *
     * <p>S3K sidekick CPU now resolves this gate through the stored
     * {@code LevelManager.frameCounter}, so keep that counter aligned with the
     * sprite-manager gameplay counter here.
     *
     * <p>Only applies to RELOAD transitions: MUTATE_ONLY runs in places
     * (e.g. AIZ1 fire-transition art overlay) that may execute mid-frame
     * without skipping the rest of the gameplay loop.
     */
    private void advanceFrameCounterAcrossSeamlessReload() {
        // The reload is requested by ScreenEvents, but the ROM returns to the
        // remainder of LevelLoop afterward: OscillateNumDo still runs before
        // the next VBlank (docs/skdisasm/sonic3k.asm:7884-7910,
        // 104722-104774). The engine applies the pending reload at the next
        // frame top and returns from RecordingFrameDriver/GameLoop, so preserve
        // that native post-ScreenEvents oscillator tick explicitly.
        advanceGlobalOscillation();

        // ROM keeps Level_frame_counter ticking through AIZ's reload frame
        // (docs/skdisasm/sonic3k.asm:7884-7894); S3K Tails CPU reads it for
        // loc_13E9C's 64-frame auto-jump gate (docs/skdisasm/sonic3k.asm:26775-26782).
        frameCounter++;
        sidekickRomVisibleReloadFrameCounterBridgeActive = true;
        sidekickRomVisibleReloadFrameCounterBridgePrimed = true;
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager != null) {
            spriteManager.setFrameCounter(spriteManager.getFrameCounter() + 1);
        }
    }

    public boolean isSidekickRomVisibleReloadFrameCounterBridgeActive() {
        return sidekickRomVisibleReloadFrameCounterBridgeActive;
    }

    public boolean isSidekickRomVisibleReloadResumeFrameCounterBridgeActive() {
        return currentAct != apparentAct || isPostReloadFrameCounterBridgeStillVisible();
    }

    public void clearSidekickRomVisibleReloadFrameCounterBridge() {
        sidekickRomVisibleReloadFrameCounterBridgeActive = false;
    }

    private boolean isPostReloadFrameCounterBridgeStillVisible() {
        if (!sidekickRomVisibleReloadFrameCounterBridgePrimed) {
            return false;
        }
        SpriteManager spriteManager = GameServices.spritesOrNull();
        return spriteManager != null && spriteManager.getFrameCounter() == frameCounter + 1;
    }

    void applySeamlessMutation(String mutationKey) {
        gameModule.applySeamlessMutation(this, mutationKey);
    }

    /** @see LevelTransitionCoordinator#consumeZoneActRequest() */
    public boolean consumeZoneActRequest() { return transitions.consumeZoneActRequest(); }

    /** @see LevelTransitionCoordinator#getRequestedZone() */
    public int getRequestedZone() { return transitions.getRequestedZone(); }

    /** @see LevelTransitionCoordinator#getRequestedAct() */
    public int getRequestedAct() { return transitions.getRequestedAct(); }

    /** @see LevelTransitionCoordinator#isLevelInactiveForTransition() */
    public boolean isLevelInactiveForTransition() { return transitions.isLevelInactiveForTransition(); }

    /** @see LevelTransitionCoordinator#requestCreditsTransition() */
    public void requestCreditsTransition() { transitions.requestCreditsTransition(); }

    /** @see LevelTransitionCoordinator#consumeCreditsRequest() */
    public boolean consumeCreditsRequest() { return transitions.consumeCreditsRequest(); }

    /** @see LevelTransitionCoordinator#requestTimeAttackMenuReturn() */
    public void requestTimeAttackMenuReturn() { transitions.requestTimeAttackMenuReturn(); }

    /** @see LevelTransitionCoordinator#consumeTimeAttackMenuReturnRequest() */
    public boolean consumeTimeAttackMenuReturnRequest() { return transitions.consumeTimeAttackMenuReturnRequest(); }

    /** @see LevelTransitionCoordinator#setForceHudSuppressed(boolean) */
    public void setForceHudSuppressed(boolean suppressed) { transitions.setForceHudSuppressed(suppressed); }

    public void setBonusStageHudLayout(boolean enabled) {
        if (hudRenderManager != null) {
            hudRenderManager.setBonusStageHudLayout(enabled);
        }
    }

    /** @see LevelTransitionCoordinator#setSuppressNextMusicChange(boolean) */
    public void setSuppressNextMusicChange(boolean suppress) { transitions.setSuppressNextMusicChange(suppress); }

    /**
     * Finds the offset from a reference position to the first pattern within a tile index range.
     * Scans the level chunks around the reference position looking for patterns that use
     * VRAM tile indices within the specified range.
     * <p>
     * This is used by CNZ slot machines to find where the slot display tiles are positioned
     * relative to the cage object, as this varies between CNZ1 (below) and CNZ2 (above).
     *
     * @param refX       Reference X position (world coordinates, typically cage center)
     * @param refY       Reference Y position (world coordinates, typically cage center)
     * @param minTileIdx Minimum VRAM tile index to search for (inclusive)
     * @param maxTileIdx Maximum VRAM tile index to search for (inclusive)
     * @param searchRadius Radius in pixels to search around the reference position
     * @return int[2] with {offsetX, offsetY} from ref to first matching pattern center,
     *         or null if no matching pattern found
     */
    public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) {
        if (level == null) {
            return null;
        }

        Map map = level.getMap();
        if (map == null) {
            return null;
        }

        // Calculate search bounds in world coordinates
        int startX = refX - searchRadius;
        int startY = refY - searchRadius;
        int endX = refX + searchRadius;
        int endY = refY + searchRadius;

        // Clamp to level bounds
        startX = Math.max(startX, level.getMinX());
        startY = Math.max(startY, level.getMinY());
        endX = Math.min(endX, level.getMaxX());
        endY = Math.min(endY, level.getMaxY());

        // Scan through patterns (8x8 pixel grid)
        for (int worldY = startY; worldY < endY; worldY += 8) {
            for (int worldX = startX; worldX < endX; worldX += 8) {
                int tileIdx = getPatternIndexAt(worldX, worldY, map);
                if (tileIdx >= minTileIdx && tileIdx <= maxTileIdx) {
                    // Found a matching pattern - snap to actual pattern boundary
                    // Patterns are 8x8 and aligned to 8-pixel grid within the level
                    int patternLeftX = worldX - (Math.floorMod(worldX, 8));
                    int patternTopY = worldY - (Math.floorMod(worldY, 8));
                    // Calculate offset from ref to pattern center
                    int offsetX = (patternLeftX + 4) - refX;
                    int offsetY = (patternTopY + 4) - refY;
                    return new int[]{offsetX, offsetY};
                }
            }
        }

        return null;
    }

    /**
     * Gets the VRAM tile index for the pattern at the given world coordinates.
     * Traverses the map -> block -> chunk -> pattern hierarchy.
     *
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param map    The level map
     * @return The pattern's VRAM tile index, or -1 if out of bounds
     */
    private int getPatternIndexAt(int worldX, int worldY, Map map) {
        try {
            // Block is 128x128 pixels
            int blockX = worldX / blockPixelSize;
            int blockY = worldY / blockPixelSize;

            if (blockX < 0 || blockX >= map.getWidth() || blockY < 0 || blockY >= map.getHeight()) {
                return -1;
            }

            // Get block index from map (layer 0 = foreground)
            int blockIdx = map.getValue(0, blockX, blockY) & 0xFF;
            if (blockIdx == 0 || blockIdx >= level.getBlockCount()) {
                return -1;
            }

            Block block = level.getBlock(blockIdx);
            if (block == null) {
                return -1;
            }

            // Chunk within block (16x16 pixels each, 8x8 grid of chunks)
            int chunkX = (worldX % blockPixelSize) / 16;
            int chunkY = (worldY % blockPixelSize) / 16;
            ChunkDesc chunkDesc = block.getChunkDesc(chunkX, chunkY);
            if (chunkDesc == null) {
                return -1;
            }

            int chunkIdx = chunkDesc.getChunkIndex();
            if (chunkIdx == 0 || chunkIdx >= level.getChunkCount()) {
                return -1;
            }

            Chunk chunk = level.getChunk(chunkIdx);
            if (chunk == null) {
                return -1;
            }

            // Pattern within chunk (8x8 pixels each, 2x2 grid)
            int patternX = (worldX % 16) / 8;
            int patternY = (worldY % 16) / 8;
            PatternDesc patternDesc = chunk.getPatternDesc(patternX, patternY);
            if (patternDesc == null) {
                return -1;
            }

            return patternDesc.getPatternIndex();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns a {@link com.openggf.game.rewind.RewindSnapshottable} adapter for level state.
     * Captures block/chunk array references and map data; restores via copy-on-write.
     */
    public RewindSnapshottable<LevelSnapshot> levelRewindSnapshottable() {
        return LevelRewindSnapshotAdapter.create(this);
    }
}
