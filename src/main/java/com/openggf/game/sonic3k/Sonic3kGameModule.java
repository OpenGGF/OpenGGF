package com.openggf.game.sonic3k;

import com.openggf.audio.GameAudioProfile;
import com.openggf.game.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameOverFlowProvider;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.CrossGameDonorProvider;
import com.openggf.game.DonorCapabilities;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.CheckpointState;
import com.openggf.game.LevelGamestate;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.sonic3k.events.S3kSeamlessMutationExecutor;
import com.openggf.game.sonic3k.objects.AizIntroArtLoader;
import com.openggf.game.sonic3k.objects.AizIntroTerrainSwap;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider;
import com.openggf.game.sonic3k.sidekick.Sonic3kCnzCarryTrigger;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import com.openggf.game.sonic3k.dataselect.S3kSaveSnapshotProvider;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager;
import com.openggf.game.DataSelectProvider;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.levelselect.Sonic3kLevelSelectManager;
import com.openggf.game.GameId;
import com.openggf.game.GameRng;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.LoadTimeProfile;
import com.openggf.game.timing.LoadTimeProfileFactory;
import com.openggf.game.timing.LoadTimeSimulationMode;
import com.openggf.game.timing.ProfiledLoadTimeManifest;
import com.openggf.game.OscillationManager;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.level.LevelManager;
import com.openggf.level.InitialFixedSstDispatcher;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCarryTrigger;
import com.openggf.sprites.playable.SuperStateController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GameModule for Sonic 3 &amp; Knuckles.
 *
 * <p>Provides audio, zone registry, scroll handlers, object/art providers,
 * data select, special/bonus-stage providers, and level initialization hooks.
 */
public class Sonic3kGameModule implements GameModule, com.openggf.game.internal.SpriteTableBuildProfile {
    @Override
    public PlacedRings placedRingEmission() {
        return PlacedRings.BEFORE_OBJECT_BUCKETS;
    }

    @Override public PlacedRingY placedRingY() { return PlacedRingY.BIAS_BEFORE_SCREEN_WRAP; }


    private static final com.openggf.game.common.CommonObjectPlacementEncoding OBJECT_PLACEMENT_ENCODING =
            new com.openggf.game.common.CommonObjectPlacementEncoding();
    private static final Logger LOGGER = Logger.getLogger(Sonic3kGameModule.class.getName());

    static {
        // Register the S3K data-select renderer as a cross-game donor so S1/S2 modules
        // can request a donated presentation without naming the S3K-specific delegate.
        com.openggf.game.dataselect.CrossGameDataSelectPresentations.registerDonor(
                com.openggf.game.dataselect.CrossGameDataSelectPresentations.DONOR_S3K,
                S3kDataSelectManager::new);
    }

    private final com.openggf.game.session.WorldPlcLifecycleFactory worldPlcFactory =
            new com.openggf.game.session.WorldPlcLifecycleFactory() {
                @Override public com.openggf.game.resources.PlcLifecycleService open(
                        com.openggf.game.session.WorldSession world) {
                    if (com.openggf.util.EngineCallerAccess.callerOutside(getClass())
                            != com.openggf.game.session.WorldSession.class)
                        throw new SecurityException("Native world PLC creation belongs to world construction");
                    var owner = new Sonic3kLevelTitlePlcService(world.getDataSource());
                    com.openggf.game.rewind.NativeRewindAdapterPublication.reserveWorldPlcCreation(world, this, owner);
                    return owner;
                }
            };

    private final com.openggf.game.internal.WorldRewindAdapterFactory worldDataFactory =
            new com.openggf.game.internal.WorldRewindAdapterFactory() {
                @Override public com.openggf.game.internal.NativePhysicalWorldData open(
                        java.lang.invoke.MethodHandles.Lookup lookup,
                        com.openggf.game.session.WorldSession world) {
                    Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(getClass());
                    if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                            || caller != com.openggf.game.session.WorldSession.class
                            || com.openggf.game.rewind.NativeRewindAdapterPublication.nativeRoot(
                                    world.rootGameModule()) != Sonic3kGameModule.this)
                        throw new SecurityException("World DATA construction belongs to the actual world constructor");
                    var owner = com.openggf.game.sonic3k.render.S3kPhysicalDisplayWorld.create(
                            java.lang.invoke.MethodHandles.lookup(), this, world);
                    com.openggf.game.rewind.NativeRewindAdapterPublication.reserveWorldDataCreation(
                            java.lang.invoke.MethodHandles.lookup(), world, this, owner.dataRewindState());
                    return owner;
                }
            };
    private final com.openggf.game.internal.NativePhysicalLevelLoad.Writer worldDataWriter =
            new com.openggf.game.internal.NativePhysicalLevelLoad.Writer() {
                @Override public void clearFresh(com.openggf.game.internal.NativePhysicalLevelLoad.Clear invocation) {
                    var owner = invocation.target(java.lang.invoke.MethodHandles.lookup(), this);
                    owner.clearAtFreshLevel(invocation);
                    invocation.handlerReturned(java.lang.invoke.MethodHandles.lookup(), this);
                }
            };

    private final java.util.Map<Sonic3kAudioResourceProfile, GameAudioProfile> audioProfiles =
            new java.util.EnumMap<>(Sonic3kAudioResourceProfile.class);
    /**
     * ROM {@code AIZ_vine_angle}, advanced by {@code ChangeRingFrame}
     * (sonic3k.asm:9729) and never cleared by a level or special-stage init --
     * both clears stop one word short of it (sonic3k.asm:7654, 10642). The
     * module is the session-lived owner; the {@code Sonic3k} Game it is handed
     * to is rebuilt on every level load (LevelManager.java:459), so owning it
     * there reset the swing phase of every AIZ giant ride vine on re-entry.
     */
    private final Sonic3kGlobalAnimationState globalAnimationState =
            new Sonic3kGlobalAnimationState();
    private final Sonic3kLevelEventManager levelEventManager = new Sonic3kLevelEventManager();
    private final Sonic3kTitleCardManager titleCardManager = new Sonic3kTitleCardManager();
    private final Sonic3kZoneRegistry zoneRegistry = new Sonic3kZoneRegistry();
    private final Sonic3kTitleScreenManager titleScreenProvider = new Sonic3kTitleScreenManager();
    private final Sonic3kLevelSelectManager levelSelectProvider = new Sonic3kLevelSelectManager();
    private final Sonic3kCheatFlags cheatFlags = new Sonic3kCheatFlags();
    private final com.openggf.game.nativemenu.NativeTitleModeProvider nativeTitleModes =
            new com.openggf.game.sonic3k.competition.S3kNativeTitleModeProvider(
                    titleScreenProvider::standaloneSinglePlayerExit);
    private final com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile dataSelectHostProfile =
            new com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile();
    private DataSelectPresentationProvider dataSelectPresentationProvider;
    private final com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager specialStageManager =
            new com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager();
    private final Sonic3kSpecialStageProvider specialStageProvider =
            new Sonic3kSpecialStageProvider(specialStageManager);
    private final Sonic3kLevelInitProfile levelInitProfile = new Sonic3kLevelInitProfile(levelEventManager);
    private final SidekickCarryTrigger sidekickCarryTrigger = new Sonic3kCnzCarryTrigger();
    private final CrossGameDonorProvider donorProvider = new Sonic3kCrossGameDonorProvider();
    private final Sonic3kModZoneAdapter modZoneAdapter = new Sonic3kModZoneAdapter(this);
    private Sonic3kScrollHandlerProvider scrollHandlerProvider;
    private PhysicsProvider physicsProvider;
    private Sonic3kObjectArtProvider objectArtProvider;
    private ObjectRegistry objectRegistry;
    private final Sonic3kBonusStageCoordinator bonusStageCoordinator = new Sonic3kBonusStageCoordinator();
    private Sonic3k activeGame;

    @Override
    public String getIdentifier() {
        return "Sonic3k";
    }

    @Override
    public GameId getGameId() {
        return GameId.S3K;
    }

    @Override
    public Game createGame(Rom rom) {
        try {
            activeGame = new Sonic3k(rom, globalAnimationState);
            return activeGame;
        } catch (java.io.IOException e) {
            activeGame = null;
            LOGGER.severe("Failed to create S3K game: " + e.getMessage());
            return null;
        }
    }

    @Override
    public com.openggf.game.modzone.ModZoneAdapter getModZoneAdapter() {
        return modZoneAdapter;
    }

    @Override public com.openggf.game.modzone.ModLevelSettingsProvider getModLevelSettingsProvider() {
        return new Sonic3kModLevelSettingsProvider();
    }

    @Override
    public com.openggf.game.palette.CustomZonePaletteBridge createCustomZonePaletteBridge(
            com.openggf.game.modzone.ModZoneRuntimeContribution contribution,
            com.openggf.level.Level level,
            ObjectArtProvider objectArtProvider) {
        if (!S3kCustomZonePaletteBridge.supports(level)) {
            return null;
        }
        return new S3kCustomZonePaletteBridge(
                contribution.ownerModId(), contribution.ownerModId() + ":" + contribution.localKey(),
                level.getPalette(0), contribution.levelData().paletteClaims(),
                objectArtProvider.getHudFlashPaletteLine(), objectArtProvider.getHudStaticArt(),
                objectArtProvider.getHudLivesNumbers(), objectArtProvider::getHudLivesPaletteOverride);
    }

    Palette getAdditiveLevelCharacterPalette() {
        if (activeGame == null) throw new IllegalStateException("S3K game has not been created");
        return java.util.Objects.requireNonNull(activeGame.loadActiveMainCharacterPalette(),
                "active character palette");
    }

    @Override
    public synchronized com.openggf.level.rings.RingSpriteSheet getAdditiveLevelRingSpriteSheet()
            throws java.io.IOException {
        if (activeGame == null) throw new IllegalStateException("S3K game has not been created");
        return activeGame.loadRingSpriteSheet();
    }

    @Override
    public RuntimeArtCoordinator createRuntimeArtCoordinator(
            HardwareTimingService timing) {
        return new S3kRuntimeArtCoordinator(timing);
    }

    /** Generator-owned measured manifest; never hand-edited. */
    static final String PROFILED_LOAD_TIME_MANIFEST = "/load-time-profiles/s3k-v1.json";
    /**
     * Hand-tuned normal-play manifest, seeded as a copy of
     * {@link #PROFILED_LOAD_TIME_MANIFEST} and extended with entries the
     * measurement stream does not cover (the title-screen Sonic frames).
     */
    static final String FAST_LOAD_TIME_MANIFEST = "/load-time-profiles/s3k-fast-v1.json";

    @Override
    public LoadTimeProfile createLoadTimeProfile(
            LoadTimeSimulationMode mode,
            Consumer<String> warningSink) {
        return switch (mode) {
            case NONE -> LoadTimeProfile.IMMEDIATE;
            case FAST -> LoadTimeProfileFactory.resolve(
                    mode, LoadTimeProfile.IMMEDIATE,
                    loadS3kProfile(FAST_LOAD_TIME_MANIFEST, warningSink), warningSink);
            case PROFILED, REALISTIC -> LoadTimeProfileFactory.resolve(
                    mode, loadS3kProfile(PROFILED_LOAD_TIME_MANIFEST, warningSink),
                    null, warningSink);
        };
    }

    private static LoadTimeProfile loadS3kProfile(
            String resourceName, Consumer<String> warningSink) {
        LoadTimeProfile manifest;
        try {
            var resource = Sonic3kGameModule.class.getResourceAsStream(resourceName);
            if (resource == null) {
                throw new IllegalStateException("missing S3K load-time manifest " + resourceName);
            }
            try (resource) {
                manifest = ProfiledLoadTimeManifest.load(resource, warningSink);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "failed to load S3K load-time manifest " + resourceName, exception);
        }
        return (submission, handle) -> {
            if (submission.kind()
                    == com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE
                    && "kosinski_moduled".equals(submission.compressionVariant())) {
                return new com.openggf.game.timing.LoadTimeDecision(
                        0,
                        Set.of(),
                        com.openggf.game.timing.LoadTimeDecisionSource.IMMEDIATE,
                        "s3k-kos-v1-composite-parent");
            }
            return manifest.assign(submission, handle);
        };
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        if (objectRegistry == null) {
            objectRegistry = new Sonic3kObjectRegistry(levelInitProfile.freshTitleCardSst());
        }
        return objectRegistry;
    }

    @Override
    public com.openggf.level.objects.ObjectPlacementEncoding getObjectPlacementEncoding() {
        return OBJECT_PLACEMENT_ENCODING;
    }

    @Override
    public GameAudioProfile getAudioProfile() {
        var resources = Sonic3kAudioResourceProfile.LOCKED_ON;
        var roms = GameServices.rom();
        if (roms.isRomAvailable()) {
            try {
                resources = Sonic3kAudioResourceProfile.fromRom(roms.getRom());
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException("Cannot resolve the active audio cartridge", failure);
            }
        }
        // Cache immutable profiles, never a ROM reader or an old world's selection.
        return audioProfiles.computeIfAbsent(resources, Sonic3kAudioProfile::new);
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        return new TouchResponseTable(romReader,
                Sonic3kConstants.TOUCH_SIZES_ADDR,
                Sonic3kConstants.TOUCH_SIZES_COUNT);
    }

    @Override
    public int getPlaneSwitcherObjectId() {
        return Sonic3kObjectIds.PATH_SWAP;
    }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F);
    }

    @Override
    public int getCheckpointObjectId() {
        return Sonic3kObjectIds.STAR_POST;
    }

    @Override
    public LevelEventProvider getLevelEventProvider() {
        return levelEventManager;
    }

    @Override
    public InitialFixedSstDispatcher createInitialFixedSstDispatcher(
            SpriteManager sprites,
            ObjectManager objects,
            ZoneFeatureProvider zoneFeatures) {
        // Finish deferred ScreenInit before the coordinator captures its camera/load
        // window and walks the initial player slots. Ordinary pre-physics is too late:
        // Knuckles_Control would already run once before the SSZ arrival owns him.
        // SSZ ScreenInit belongs to a ROM zone's event script. An authored
        // level retains native fixed SST dispatch below, but cannot inherit a
        // previously selected stock zone's camera/arrival controller.
        if (Sonic3kLevel.currentLevelHasStockRomZoneIdentity()) {
            levelEventManager.prepareFreshScreenObjects();
        }
        InitialWaveSplashSstOwner waveOwner =
                zoneFeatures instanceof Sonic3kZoneFeatureProvider provider
                        ? provider.initialWaveSplashSstOwner()
                        : new InitialWaveSplashSstOwner() {
                            @Override public boolean isRegistered() { return false; }
                            @Override public void processInitialWaveSplash(
                                    com.openggf.sprites.managers.ProcessSpritesEpoch epoch) { }
                        };
        return new S3kInitialFixedSstDispatcher(
                levelEventManager, sprites, objects, waveOwner);
    }

    @Override
    public RespawnState createRespawnState() {
        return new CheckpointState();
    }

    @Override
    public LevelState createLevelState() {
        return new LevelGamestate();
    }

    @Override
    public ZoneRegistry getZoneRegistry() {
        return zoneRegistry;
    }

    @Override
    public GameRng.Flavour rngFlavour() {
        return GameRng.Flavour.S3K;
    }

    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        if (scrollHandlerProvider == null) {
            scrollHandlerProvider = new Sonic3kScrollHandlerProvider();
        }
        return scrollHandlerProvider;
    }

    private final GameOverFlowProvider gameOverFlowProvider = new Sonic3kGameOverFlowProvider();

    @Override
    public GameOverFlowProvider getGameOverFlowProvider() {
        return gameOverFlowProvider;
    }

    @Override
    public TitleCardProvider getTitleCardProvider() {
        return titleCardManager;
    }

    @Override
    public com.openggf.game.ContinueScreenProvider createContinueScreenProvider() {
        return new com.openggf.game.sonic3k.continuescreen.Sonic3kContinueScreenProvider(
                com.openggf.game.sonic3k.runtime.S3kRuntimeStates.resolvePlayerCharacter(
                        GameServices.zoneRuntimeRegistry(), GameServices.configuration()).ordinal(), false);
    }

    @Override
    public com.openggf.game.preview.TitlePreviewProvider getTitlePreviewProvider() {
        return new com.openggf.game.sonic3k.titlescreen.Sonic3kTitlePreviewProvider();
    }

    @Override
    public TitleScreenProvider getTitleScreenProvider() {
        return titleScreenProvider;
    }

    @Override
    public LevelSelectProvider getLevelSelectProvider() {
        return levelSelectProvider;
    }

    @Override
    public DataSelectProvider getDataSelectProvider() {
        if (Sonic3kCartridgePolicy.isStandaloneSk()) return null;
        return getDataSelectPresentationProvider();
    }

    @Override
    public DataSelectPresentationProvider getDataSelectPresentationProvider() {
        if (Sonic3kCartridgePolicy.isStandaloneSk()) return null;
        if (dataSelectPresentationProvider == null) {
            dataSelectPresentationProvider = new DataSelectPresentationProvider(S3kDataSelectManager::new,
                    new DataSelectSessionController(dataSelectHostProfile));
        }
        return dataSelectPresentationProvider;
    }

    @Override
    public DataSelectHostProfile getDataSelectHostProfile() {
        return dataSelectHostProfile;
    }

    @Override
    public com.openggf.game.save.SaveSnapshotProvider getSaveSnapshotProvider() {
        return new S3kSaveSnapshotProvider();
    }

    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
        return new Sonic3kZoneFeatureProvider();
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        return null;
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic3kObjectArtProvider();
        }
        return objectArtProvider;
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic3kPhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public WaterDataProvider getWaterDataProvider() {
        return new Sonic3kWaterDataProvider();
    }

    @Override
    public LevelInitProfile getLevelInitProfile() {
        return levelInitProfile;
    }

    @Override
    public boolean hasSeparateTailsTailArt() {
        return true;
    }

    @Override
    public SpriteArtSet loadTailsTailArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            Sonic3kPlayerArt s3kArt = new Sonic3kPlayerArt(RomByteReader.fromRom(rom));
            return s3kArt.loadTailsTail();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load S3K tails tail art", e);
            return SpriteArtSet.EMPTY;
        }
    }

    @Override
    public SuperStateController createSuperStateController(
            AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return GameServices.crossGameFeatures().createSuperStateController(player);
        }
        return new Sonic3kSuperStateController(player);
    }

    @Override
    public SidekickCarryTrigger getSidekickCarryTrigger() {
        return sidekickCarryTrigger;
    }

    @Override
    public void applySeamlessMutation(LevelManager levelManager, String mutationKey) {
        S3kSeamlessMutationExecutor.apply(levelManager, mutationKey);
    }

    @Override
    public void resetModuleScopedState() {
        AizIntroArtLoader.reset();
        AizIntroTerrainSwap.reset();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getGameService(Class<T> type) {
        if (type == com.openggf.game.nativemenu.NativeTitleModeProvider.class) return type.cast(nativeTitleModes);
        if (type == com.openggf.game.session.WorldSessionPolicyProvider.class) {
            return type.cast(com.openggf.game.sonic3k.mutators.Sonic3kMutatorPolicyProvider.INSTANCE);
        }
        if (type == Sonic3kCheatFlags.class) {
            return (T) cheatFlags;
        }
        if (type == com.openggf.level.render.ZonePictureSource.Factory.class) {
            // Mod scenes' zone pictures and title cards: detached builds that read only the ROM.
            return (T) (com.openggf.level.render.ZonePictureSource.Factory) Sonic3kZoneArt::new;
        }
        if (type == com.openggf.game.internal.SidekickCpuInitializationPolicy.class) {
            return (T) com.openggf.game.sonic3k.sidekick.Sonic3kSidekickCpuInitializationPolicy.INSTANCE;
        }
        if (type == com.openggf.game.internal.HudWarningPolicyProvider.class) {
            // Render_HUD / loc_DB68 tests bit 3 of Level_frame_counter,
            // independently of Timer_frame and Update_HUD_timer.
            return (T) new com.openggf.game.internal.HudWarningPolicyProvider() {
                @Override public boolean isFlashFrame(int frame) { return (frame & 8) == 0; }
                @Override public boolean isTimerWarning(int seconds) { return seconds / 60 == 9; }
            };
        }
        if (type == com.openggf.game.internal.HudLivesNumberPaletteProvider.class) {
            // HUD_Lives writes PlayerLifeIcon+9/+11, inside Map_HUD's
            // second lives piece ($210E), which selects palette line 1.
            return (T) (com.openggf.game.internal.HudLivesNumberPaletteProvider) () -> 1;
        }
        if (type == com.openggf.game.internal.WorldRewindAdapterFactory.class)
            return type.cast(worldDataFactory);
        if (type == com.openggf.game.internal.NativePhysicalLevelLoad.Writer.class)
            return type.cast(worldDataWriter);
        // No concrete owner alias: an explicit world and authenticated lifecycle are required.
        if (type == com.openggf.game.sonic3k.render.S3kPhysicalDisplayWorld.class) return null;
        if (type == com.openggf.game.session.WorldPlcLifecycleFactory.class) {
            return type.cast(worldPlcFactory);
        }
        if (type == com.openggf.game.internal.WorldTitleCardUpdate.class) {
            return type.cast((com.openggf.game.internal.WorldTitleCardUpdate)
                    com.openggf.game.sonic3k.titlecard.S3kTitleCardWorldAccess::update);
        }
        // A reused module cannot select a world. These aliases are available
        // only through that world's injected services / explicit engine bridge.
        if (type == Sonic3kLevelTitlePlcService.class
                || type == Sonic3kGameOverPlcService.class
                || type == com.openggf.game.resources.PlcLifecycleService.class)
            return null;
        if (type == com.openggf.game.LevelInputOverlay.class) {
            return (T) (com.openggf.game.LevelInputOverlay) input -> {
                if (Sonic3kStandaloneStartup.handleIntroInput(input)) return true;
                var ending = com.openggf.game.sonic3k.ending.S3kEndingCoordinator
                        .current(GameServices.zoneRuntimeRegistry()).orElse(null);
                // $0D01 owns input from entry, before Obj_5D86A's first pass.
                // An ordinary SSZ level still permits its normal pause until
                // the actual native ending owner has initialized there.
                if (ending == null || (!ending.initialized()
                        && !(GameServices.zoneRuntimeState() instanceof
                        com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState))) return false;
                ending.setStartHeld(input.logical().player1().startHeld() || input.logical().player2().startHeld());
                return true; // native ending owns Start; it is not a level-pause request
            };
        }
        if (type == com.openggf.game.internal.LevelEntryPresentation.class) {
            // Level loc_5FFC selects PaletteWhiteOut for the ending scene.
            return (T) (com.openggf.game.internal.LevelEntryPresentation)
                    (zone, act) -> zone == 0x0D && act == 1;
        }
        if (type == Sonic3kLevelEventManager.class) return (T) levelEventManager;
        if (type == Sonic3kTitleCardManager.class) return (T) titleCardManager;
        if (type == Sonic3kZoneRegistry.class) return (T) zoneRegistry;
        if (type == com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager.class)
            return (T) specialStageManager;
        return null;
    }

    @Override
    public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
        return List.of(); // The world registers its PLC owner with core adapters.
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values used by moving platforms, etc.
        OscillationManager.reset();
        // Reset button/trigger state for the new level
        Sonic3kLevelTriggerManager.reset();
    }

    @Override
    public SpecialStageProvider getSpecialStageProvider() {
        return specialStageProvider;
    }

    @Override
    public BonusStageProvider getBonusStageProvider() {
        return bonusStageCoordinator;
    }

    @Override
    public boolean supportsSidekick() {
        return true;
    }

    /**
     * The Doomsday flight controller's init ({@code loc_81554}, sonic3k.asm:173372-173377) zeroes
     * the whole Player 2 object before the first level frame, so a Sonic and Tails game plays the
     * zone as Sonic alone.
     */
    @Override
    public boolean isSidekickSuppressedForZone(int zoneId) {
        return zoneId == com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_DDZ;
    }

    @Override
    public java.util.function.Function<com.openggf.game.PlayableEntity,
            com.openggf.level.objects.AbstractObjectInstance> getInvincibilityStarsFactory() {
        return com.openggf.game.sonic3k.objects.Sonic3kInvincibilityStarsObjectInstance::new;
    }

    @Override
    public java.util.function.BiFunction<AbstractPlayableSprite, com.openggf.game.ShieldType,
            com.openggf.level.objects.ShieldObjectInstance> getShieldFactory() {
        // Same objects whether S3K is the host or the cross-game donor.
        return donorProvider.getShieldFactory();
    }

    @Override
    public java.util.function.Function<AbstractPlayableSprite,
            com.openggf.level.objects.AbstractObjectInstance> getInstaShieldFactory() {
        return donorProvider.getInstaShieldFactory();
    }

    @Override
    public DonorCapabilities getDonorCapabilities() {
        return Sonic3kDonorCapabilities.INSTANCE;
    }

    @Override
    public CrossGameDonorProvider getCrossGameDonorProvider() {
        return donorProvider;
    }

    /** Lazily-constructed singleton holding S3K donation metadata. */
    private static final class Sonic3kDonorCapabilities implements DonorCapabilities {

        static final Sonic3kDonorCapabilities INSTANCE = new Sonic3kDonorCapabilities();

        private static final java.util.Set<PlayerCharacter> CHARACTERS =
                java.util.Set.of(
                        PlayerCharacter.SONIC_ALONE,
                        PlayerCharacter.SONIC_AND_TAILS,
                        PlayerCharacter.TAILS_ALONE,
                        PlayerCharacter.KNUCKLES);

        private static final java.util.Map<CanonicalAnimation, CanonicalAnimation> FALLBACKS =
                buildFallbacks();

        private static java.util.Map<CanonicalAnimation, CanonicalAnimation> buildFallbacks() {
            return DonorCapabilities.buildFallbackMap(
                    Sonic3kAnimationIds.values(), Sonic3kAnimationIds::toCanonical,
                    java.util.Map.ofEntries(
                            // S1-specific animations -> nearest S3K native fallback
                            java.util.Map.entry(CanonicalAnimation.STOP,        CanonicalAnimation.SKID),
                            java.util.Map.entry(CanonicalAnimation.WARP1,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP2,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP3,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP4,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.FLOAT3,      CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.FLOAT4,      CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.LEAP1,       CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.LEAP2,       CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.SURF,        CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GET_AIR,     CanonicalAnimation.BLANK),
                            java.util.Map.entry(CanonicalAnimation.BURNT,       CanonicalAnimation.HURT),
                            java.util.Map.entry(CanonicalAnimation.SHRINK,      CanonicalAnimation.DEATH),
                            java.util.Map.entry(CanonicalAnimation.WATER_SLIDE, CanonicalAnimation.HURT_FALL),
                            java.util.Map.entry(CanonicalAnimation.NULL_ANIM,   CanonicalAnimation.BLANK),
                            // S2-specific animations not in S3K -> nearest S3K native fallback
                            java.util.Map.entry(CanonicalAnimation.SLIDE,       CanonicalAnimation.HURT_FALL),
                            java.util.Map.entry(CanonicalAnimation.HURT2,       CanonicalAnimation.HURT)
                    ));
        }

        @Override
        public java.util.Set<PlayerCharacter> getPlayableCharacters() { return CHARACTERS; }

        @Override
        public boolean hasSpindash() { return true; }

        @Override
        public boolean hasSuperTransform() { return true; }

        @Override
        public boolean hasHyperTransform() { return true; }

        @Override
        public boolean hasInstaShield() { return true; }

        @Override
        public boolean hasTailsFlight() { return true; }

        @Override
        public boolean hasElementalShields() { return true; }

        @Override
        public boolean hasSidekick() { return true; }

        @Override
        public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() {
            return FALLBACKS;
        }

        @Override
        public int resolveNativeId(CanonicalAnimation canonical) {
            return Sonic3kAnimationIds.fromCanonical(canonical);
        }

        @Override
        public com.openggf.sprites.art.PlayerSpriteArtProvider getPlayerArtProvider(
                com.openggf.data.RomByteReader reader) {
            var art = new Sonic3kPlayerArt(reader);
            return art::loadForCharacter;
        }
    }

    private static final class Sonic3kCrossGameDonorProvider implements CrossGameDonorProvider {
        @Override
        public java.util.function.BiFunction<AbstractPlayableSprite, com.openggf.game.ShieldType,
                com.openggf.level.objects.ShieldObjectInstance> getShieldFactory() {
            return (player, type) -> switch (type) {
                case FIRE -> new com.openggf.game.sonic3k.objects.FireShieldObjectInstance(player);
                case LIGHTNING -> new com.openggf.game.sonic3k.objects.LightningShieldObjectInstance(player);
                case BUBBLE -> new com.openggf.game.sonic3k.objects.BubbleShieldObjectInstance(player);
                default -> new com.openggf.level.objects.ShieldObjectInstance(player);
            };
        }

        @Override
        public java.util.function.Function<AbstractPlayableSprite,
                com.openggf.level.objects.AbstractObjectInstance> getInstaShieldFactory() {
            return com.openggf.game.sonic3k.objects.InstaShieldObjectInstance::new;
        }

        @Override
        public DonorCapabilities getDonorCapabilities() {
            return Sonic3kDonorCapabilities.INSTANCE;
        }

        @Override
        public com.openggf.sprites.art.PlayerSpriteArtProvider createPlayerArtProvider(RomByteReader reader) {
            return Sonic3kDonorCapabilities.INSTANCE.getPlayerArtProvider(reader);
        }

        @Override
        public com.openggf.sprites.art.SpindashDustArtProvider createSpindashDustArtProvider(RomByteReader reader) {
            Sonic3kDustArt dustArt = new Sonic3kDustArt(reader);
            return dustArt::loadForCharacter;
        }

        @Override
        public GameAudioProfile getAudioProfile() {
            return new Sonic3kAudioProfile();
        }

        @Override
        public Palette loadCharacterPalette(RomByteReader reader, String characterCode) {
            int paletteAddr;
            int paletteSize = Palette.PALETTE_SIZE_IN_ROM;
            if ("knuckles".equalsIgnoreCase(characterCode)) {
                paletteAddr = Sonic3kConstants.KNUCKLES_PALETTE_ADDR;
                paletteSize = 32;
            } else {
                paletteAddr = Sonic3kConstants.SONIC_PALETTE_ADDR;
            }
            byte[] data = reader.slice(paletteAddr, paletteSize);
            Palette palette = new Palette();
            palette.fromSegaFormat(data);
            return palette;
        }

        @Override
        public Palette loadHostCompatiblePalette(RomByteReader reader, String characterCode) {
            if (!"knuckles".equalsIgnoreCase(characterCode)) {
                return null;
            }
            byte[] data = reader.slice(Sonic3kConstants.KNUCKLES_S2_PALETTE_ADDR,
                    Palette.PALETTE_SIZE_IN_ROM);
            Palette palette = new Palette();
            palette.fromSegaFormat(data);
            Palette.Color gold = palette.getColor(14);
            Palette.Color idx4 = palette.getColor(4);
            idx4.r = gold.r;
            idx4.g = gold.g;
            idx4.b = gold.b;
            return palette;
        }

        @Override
        public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
            return new Sonic3kSuperStateController(player);
        }

        @Override
        public boolean hasSeparateTailsTailArt() {
            return true;
        }

        @Override
        public SpriteArtSet loadTailsTailArt(RomByteReader reader) throws IOException {
            return new Sonic3kPlayerArt(reader).loadTailsTail();
        }

        @Override
        public SpriteArtSet loadInstaShieldArt(RomByteReader reader) throws IOException {
            Pattern[] tiles = S3kSpriteDataLoader.loadArtTiles(reader,
                    Sonic3kConstants.ART_UNC_INSTA_SHIELD_ADDR,
                    Sonic3kConstants.ART_UNC_INSTA_SHIELD_SIZE);
            List<SpriteMappingFrame> mappings = S3kSpriteDataLoader.loadMappingFrames(
                    reader, Sonic3kConstants.MAP_INSTA_SHIELD_ADDR);
            List<SpriteDplcFrame> dplcs = S3kSpriteDataLoader.loadDplcFrames(
                    reader, Sonic3kConstants.DPLC_INSTA_SHIELD_ADDR);

            if (dplcs.size() > mappings.size()) {
                dplcs = new ArrayList<>(dplcs.subList(0, mappings.size()));
            }
            while (dplcs.size() < mappings.size()) {
                dplcs.add(new SpriteDplcFrame(List.of()));
            }

            int bankSize = S3kSpriteDataLoader.resolveBankSize(dplcs, mappings);
            SpriteAnimationSet animSet = S3kSpriteDataLoader.loadAnimationSet(reader,
                    Sonic3kConstants.ANI_INSTA_SHIELD_ADDR,
                    Sonic3kConstants.ANI_INSTA_SHIELD_COUNT);

            return new SpriteArtSet(tiles, mappings, dplcs,
                    0, Sonic3kConstants.ART_TILE_SHIELD, 1, bankSize, null, animSet);
        }
    }
}
