package com.openggf.game.soniccd;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CheckpointState;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.Game;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.game.GameModule;
import com.openggf.game.internal.SpriteTableBuildProfile;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.internal.LostRingDisplayProfile;
import com.openggf.game.internal.StagePaletteWait;
import com.openggf.game.profiles.trace.TracePlaybackProfile;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.SonicCdObjectRegistry;
import com.openggf.game.soniccd.objects.boss.wwz.WwzBossRam;
import com.openggf.game.soniccd.player.CdAnimationIds;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.game.soniccd.player.SonicCdPhysicsProvider;
import com.openggf.game.soniccd.scroll.CdScrollHandlerProvider;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;

import java.util.List;

/**
 * Sonic CD as a stock game module. The level engine is Sonic 1's with additions, so
 * movement runs through the shared owner with Sonic 1's profile and the CD rule
 * differences in {@link com.openggf.game.soniccd.player.SonicCdRules}.
 * Stages come from the user's disc through {@link CdDiscDataSource}.
 */
public final class SonicCdGameModule implements GameModule, SpriteTableBuildProfile {
    @Override
    public PlacedRings placedRingEmission() {
        // ObjRing_Main (Level/_Objects/Ring/Main.asm:214-232) and
        // ObjRing_Sparkle (Level/_Objects/Ring/Main.asm:276-279) each call DrawObject.
        // DrawObject (Level/Object Functions.asm:137-193) culls before queuing by priority.
        return PlacedRings.OBJECT_SLOTS_WITH_LAYOUT_MARGINS;
    }

    // Donated sheets and scripts are loaded from the donor cartridge, independently of the disc.
    @Override public boolean supportsCrossGameHost() { return true; }
    @Override public java.util.List<String> getStockRunCharacters() { return java.util.List.of("sonic"); }

    /**
     * Trace playback facts for {@code scd} fixtures. Every multi-segment alignment is
     * off: no CD fixture yet crosses a level, results or special-stage boundary, so no
     * masked V-INT count has been measured (TracePlaybackProfile keeps games opt-in
     * until a movie establishes one). The recorder writes {@code zone_id} as the
     * {@code zone} byte ($FF1506) and {@code act} one-based, and the K3b projection
     * keys zone on the same {@code CdZone} ordinal with act index 0 = act 1 Present,
     * so the direct identity holds only for act 1 Present fixtures. Any other act or
     * period (act index = 4 * (act - 1) + period, SonicCdZoneRegistry) needs its own
     * identity mapping before a fixture uses it.
     */
    public static final TracePlaybackProfile CD_TRACE_PLAYBACK_PROFILE = new TracePlaybackProfile(
            -1, List.of(), -1, -1, false, false,
            TracePlaybackProfile.RecordedLevelIdentityProfile.DIRECT);

    private static final ObjectPlacementEncoding PLACEMENT_ENCODING = new CdObjectPlacementEncoding();
    private static final com.openggf.game.internal.SpawnerBeforeObjectPass SPAWNER_FIRST =
            new com.openggf.game.internal.SpawnerBeforeObjectPass() { };
    private static final com.openggf.game.internal.StoredOrderObjectLayout STORED_LAYOUT =
            new com.openggf.game.internal.StoredOrderObjectLayout() { };
    private static final com.openggf.game.internal.UntrackedEntriesSpawnWhileLive UNTRACKED_COPIES =
            new com.openggf.game.internal.UntrackedEntriesSpawnWhileLive() { };
    private static final com.openggf.game.internal.FailedSpawnUnmarksEntry FAILED_SPAWN_UNMARKS =
            new com.openggf.game.internal.FailedSpawnUnmarksEntry() { };
    private static final com.openggf.game.internal.RingExtraLifeReplacesPickupSound RING_EXTRA_LIFE_REPLACES_PICKUP =
            new com.openggf.game.internal.RingExtraLifeReplacesPickupSound() { };
    private static final com.openggf.game.internal.LostRingObjectId LOST_RING_ID = () -> 0x11;
    private static final com.openggf.game.internal.LostRingSpillAtOwnerTurn SPILL_AT_OWNER_TURN =
            new com.openggf.game.internal.LostRingSpillAtOwnerTurn() { };
    // loc_208B5A (objects/ring.asm:354-364): r8 puts layer 0 in bucket 0.
    // The other layer uses bucket 3 and clears the tile word's priority bit.
    private static final LostRingDisplayProfile.Priority FOREGROUND_SPILL =
            new LostRingDisplayProfile.Priority(0, true);
    private static final LostRingDisplayProfile.Priority BACKGROUND_SPILL =
            new LostRingDisplayProfile.Priority(3, false);
    private static final LostRingDisplayProfile MMZ_SPILL_DISPLAY =
            layer -> layer == 0 ? FOREGROUND_SPILL : BACKGROUND_SPILL;
    private static final com.openggf.game.internal.StageLoadClearsStageRam CLEARED_STAGE_RAM =
            new com.openggf.game.internal.StageLoadClearsStageRam() { };
    private final com.openggf.game.soniccd.objects.common.CdFixedObjects fixedObjects =
            new com.openggf.game.soniccd.objects.common.CdFixedObjects();
    private final SonicCdZoneRegistry zoneRegistry = new SonicCdZoneRegistry();
    /** Non-level programs: the IPX flow owner and its title, stage-select and not-hosted hosts. */
    private final com.openggf.game.soniccd.program.CdSceneCoordinator scenes =
            new com.openggf.game.soniccd.program.CdSceneCoordinator(this::disc);
    /** The campaign time owner (time-travel lane): period, warp, generator/projector, good future. */
    private final com.openggf.game.soniccd.timetravel.CdTimeTravel timeTravel =
            new com.openggf.game.soniccd.timetravel.CdTimeTravel(scenes::timeAttackMode, scenes::timeStones);
    /** {@code switchFlags} (Level/_Definitions.inc:336): written by switches, read by doors, fans and chains. */
    private final com.openggf.game.soniccd.objects.shared.SharedSwitchFlags switchFlags =
            new com.openggf.game.soniccd.objects.shared.SharedSwitchFlags();
    private final CdLevelEventProvider levelEvents = new CdLevelEventProvider(() -> timeTravel);
    private final CdLevelPause levelPause = new CdLevelPause();
    /** {@code levelVIntCounter} at stage start: cleared by every load, base 0 unless a replay seeds it. */
    private final CdLevelVIntCounter levelVIntCounter = new CdLevelVIntCounter();
    /** The frame a stage load runs in: it already ran iteration 1, so the loop runs no other that frame. */
    private final CdLoadFrame loadFrame = new CdLoadFrame();
    private final CdPlayerStage playerStage = new CdPlayerStage(() -> this.game);
    private final LevelInitProfile levelInitProfile = new SonicCdLevelInitProfile(levelEvents, timeTravel, switchFlags,
            playerStage, levelVIntCounter, loadFrame, levelPause, this::disc,
            payload -> this.audioProfile.subCpuAudio().loadStage(payload));
    private final ScrollHandlerProvider scrollHandlerProvider = new CdScrollHandlerProvider();
    private final PhysicsProvider physicsProvider = new SonicCdPhysicsProvider(playerStage);
    private final com.openggf.game.soniccd.audio.SonicCdAudioProfile audioProfile =
            new com.openggf.game.soniccd.audio.SonicCdAudioProfile(() -> this.disc());
    private final ZoneFeatureProvider zoneFeatures = new com.openggf.game.soniccd.zones.CdZoneFeatures(playerStage);
    private ObjectRegistry objectRegistry;
    private SonicCdGame game;
    private final com.openggf.game.soniccd.zones.CdZoneWater zoneWater =
            new com.openggf.game.soniccd.zones.CdZoneWater(() -> this.game == null ? null : this.game.currentImage());
    private final com.openggf.game.soniccd.objects.art.CdObjectArtProvider objectArt =
            new com.openggf.game.soniccd.objects.art.CdObjectArtProvider(
                    () -> game == null ? null : game.currentLevel(), () -> game == null ? null : game.currentImage(),
                    this::disc);

    {
        scenes.bindTimeTravel(() -> timeTravel);
    }

    /** The death's game-over branch (lane FLOW2). */
    private final com.openggf.game.soniccd.program.CdGameOverFlow gameOverFlow =
            new com.openggf.game.soniccd.program.CdGameOverFlow(scenes);
    private final com.openggf.game.soniccd.title.CdTitleScreen titleScreen =
            new com.openggf.game.soniccd.title.CdTitleScreen(scenes);
    private final com.openggf.game.soniccd.title.CdStageSelectScreen stageSelect =
            new com.openggf.game.soniccd.title.CdStageSelectScreen(scenes);
    private final com.openggf.game.nativemenu.NativeTitleModeProvider programHost =
            scenes::programScreen;

    /**
     * Interactive startup's backup RAM: the save owner's {@code soniccd} slot under {@link
     * com.openggf.game.save.SavePaths#root()} (lane TA's durable host), attached before IPX {@code Start}
     * opens it (BRAMINIT, then ReadSaveData, IPX.asm:41-48). Tests, replays and capture tools never call
     * this, so their backup RAM lives for the session only.
     */
    public void attachInteractiveBackupRam() {
        attachInteractiveBackupRam(com.openggf.game.save.SavePaths.root());
    }

    /** {@link #attachInteractiveBackupRam()} under {@code saveRoot}: {@code <saveRoot>/soniccd/slot0.json}. */
    public void attachInteractiveBackupRam(java.nio.file.Path saveRoot) {
        scenes.attachBackupRamHost(com.openggf.game.soniccd.backupram.CdBackupRamHost.durable(saveRoot), 0);
    }

    /** The launcher's durable-save seam: a player's launch keeps the backup RAM in the save root. */
    private final com.openggf.game.internal.DurableSaveHost durableSaves = this::attachInteractiveBackupRam;

    /** The session's disc: programs run before the first stage creates the {@link SonicCdGame}. */
    private CdDiscDataSource disc() {
        if (game != null) return game.disc();
        var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        return world != null && world.getDataSource() instanceof CdDiscDataSource source ? source : null;
    }

    @Override public com.openggf.game.modzone.ModZoneAdapter getModZoneAdapter() { return new CdModZoneAdapter(this::disc); }

    @Override public com.openggf.game.modzone.ModLevelSettingsProvider getModLevelSettingsProvider() {
        return new CdModLevelSettingsProvider(this::disc);
    }

    @Override public String getIdentifier() { return "SonicCD"; }
    @Override public com.openggf.game.TitleScreenProvider getTitleScreenProvider() { return titleScreen; }
    @Override public com.openggf.game.LevelSelectProvider getLevelSelectProvider() { return stageSelect; }
    @Override public GameId getGameId() { return GameId.SCD; }

    @Override
    public Game createGame(GameDataSource source) {
        if (!(source instanceof CdDiscDataSource disc)) {
            throw new IllegalStateException("Sonic CD requires a disc data source");
        }
        if (game == null || game.disc() != disc) {
            game = new SonicCdGame(disc);
            game.bindDemoSelection(scenes::titleDemo);
        }
        return game;
    }

    @Override
    public Game createGame(Rom rom) {
        throw new IllegalStateException("Sonic CD is hosted from a disc, not a cartridge ROM");
    }

    @Override
    public TouchResponseTable createTouchResponseTable(GameDataSource source) {
        var levels = GameServices.levelOrNull();
        if (levels != null && levels.getCurrentLevel() instanceof com.openggf.level.ModLevel
                && source instanceof CdDiscDataSource disc) {
            if (game != null) game.retireNativeStage();
            // An authored act has no native program. Its owned objects use the common disc table.
            try {
                var image = new com.openggf.game.soniccd.level.CdStageLoader(disc).readStage(CdStagePayload.R11A);
                return touchResponseTable(image, CdStagePayload.R11A);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException("Cannot read the Sonic CD authored touch sizes", failure);
            }
        }
        CdStageImage image = game == null ? null : game.currentImage();
        if (image == null) {
            throw new IllegalStateException("Sonic CD touch sizes come from the loaded stage");
        }
        return touchResponseTable(image, game.currentLevel().payload());
    }

    private static TouchResponseTable touchResponseTable(CdStageImage image, CdStagePayload payload) {
        // .CheckWidth: andi.w #$3F; lea -2(ObjColSizes,d0.w) (Player Object Collision.asm:41-45).
        int sizes = CdStageCatalogue.entry(payload).objColSizes();
        return new TouchResponseTable(image.reader(), sizes - 2, 0x40);
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        throw new IllegalStateException("Sonic CD touch sizes come from the loaded stage");
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        if (objectRegistry == null) objectRegistry = new SonicCdObjectRegistry();
        return objectRegistry;
    }

    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() { return PLACEMENT_ENCODING; }
    @Override public GameAudioProfile getAudioProfile() { return audioProfile; }

    // --- lane END: the ending after GameDone (ending/CdEndingProvider)
    @Override
    public com.openggf.game.EndingProvider getEndingProvider() {
        return new com.openggf.game.soniccd.ending.CdEndingProvider(scenes);
    }
    // --- end lane END
    @Override public int getPlaneSwitcherObjectId() { return 0; }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }

    @Override public LevelEventProvider getLevelEventProvider() { return levelEvents; }
    @Override public RespawnState createRespawnState() { return new CheckpointState(); }
    @Override public LevelState createLevelState() { return new CdLevelGamestate(); }
    @Override public ZoneRegistry getZoneRegistry() { return zoneRegistry; }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return scrollHandlerProvider; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return zoneFeatures; }
    @Override public com.openggf.game.WaterDataProvider getWaterDataProvider() { return zoneWater; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return objectArt; }

    private static final com.openggf.game.internal.HostedGameMedia HOSTED_MEDIA =
            new com.openggf.game.soniccd.media.CdHostedMedia();

    private static final com.openggf.game.internal.LoadedLevelFrameCounter LOADED_LEVEL_FRAMES =
            new com.openggf.game.internal.LoadedLevelFrameCounter(1);

    private final StagePaletteWait.Source stagePaletteWait = level -> {
        var ram = objectArt.zoneArt(WwzBossRam.class);
        return ram == null ? null : ram.pendingPaletteWait(level);
    };

    private final com.openggf.game.internal.LevelDemoPlayback demoPlayback =
            new com.openggf.game.internal.LevelDemoPlayback() {
                @Override public com.openggf.control.PlayerInputState inputForNextIteration() {
                    int sample = game.nextDemoSample(GameServices.level().getFrameCounter());
                    return CdStageDemo.decodeInput(game.demoWord(sample));
                }

                // Level_MainLoop (Level/Main.asm:333-355): live Start ends a demo after the
                // title-card control lock releases, while Sonic is alive.
                @Override public boolean playerCanInterrupt() {
                    var player = GameServices.sprites().getMainPlayable();
                    return !game.currentLevel().flags().ctrlLocked && player != null && !player.getDead();
                }

                @Override public boolean hasFinished() {
                    return game.nextDemoSample(GameServices.level().getFrameCounter())
                            >= CdStageDemo.INPUT_WORDS;
                }
            };

    @Override
    public <T> T getGameService(Class<T> type) {
        if (type == SpriteTableBuildProfile.class) return type.cast(this);
        if (type == StagePaletteWait.Source.class) return type.cast(stagePaletteWait);
        if (type == com.openggf.game.internal.LevelDemoPlayback.class) {
            var level = GameServices.levelOrNull();
            return game != null && game.currentDemo() != null && scenes.titleDemo() != null
                    && level != null && level.getCurrentLevel() == game.currentLevel() ? type.cast(demoPlayback) : null;
        }
        if (type == com.openggf.game.LevelInputOverlay.class) return type.cast(levelPause.recordedInputOverlay);
        if (type == com.openggf.game.internal.NativeLevelInputOwner.class) return type.cast(levelPause);
        if (type == com.openggf.game.internal.HostAnimationSource.class) return type.cast(
                new com.openggf.game.internal.HostAnimationSource() {
                    public java.util.Map<Integer, com.openggf.game.AnimationFamilies.Binding> families() {
                        // Ani_Sonic's first $1F entries retain S1's semantic order. Extended
                        // CD-only drawings have no cartridge counterpart and retain donor poses.
                        return com.openggf.game.AnimationFamilies.load("/animation-families/s1.json");
                    }
                    public com.openggf.sprites.art.SpriteArtSet load(String character) throws java.io.IOException {
                        if (game == null) throw new java.io.IOException("Host stage media is not prepared");
                        return game.loadPlayerSpriteArt(character);
                    }
                });
        if (type == com.openggf.game.internal.NativeProjectHost.class) return type.cast(
                new com.openggf.game.internal.NativeProjectHost() {
                    @Override public String stageLabel(int zone, int act) {
                        var stage = SonicCdZoneRegistry.payload(zone, act).stage();
                        return com.openggf.i18n.UiMessages.text("editor.project.native_stage", java.util.Map.of(
                                "act", stage.actNumber(), "period", stage.period().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
                    }
                    @Override public Target target(com.openggf.game.internal.NativeProjectSource source) throws java.io.IOException {
                        if (game == null) throw new java.io.IOException("Open the project disc before native playtesting");
                        var payload = new com.openggf.game.soniccd.level.CdStageLoader(game.disc()).validate(source);
                        return new Target(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload), payload.ordinal());
                    }
                    @Override public com.openggf.game.internal.NativeProjectLevel preview(com.openggf.game.internal.NativeProjectSource source)
                            throws java.io.IOException {
                        var target = target(source);
                        var stage = CdStagePayload.values()[target.levelIndex()];
                        return new com.openggf.game.soniccd.level.CdStageLoader(game.disc()).load(stage, target.zone(), false);
                    }
                    @Override public com.openggf.level.Level load(com.openggf.game.internal.NativeProjectSource source,
                            com.openggf.level.Level authored, int x, int y) throws java.io.IOException {
                        target(source);
                        return game.loadProject(source, authored, x, y);
                    }
                });
        if (type == com.openggf.game.internal.SceneEntryPreparation.class)
            return type.cast((com.openggf.game.internal.SceneEntryPreparation) world ->
                world.getLevelManager().getObjectManager().getActiveObjects().stream().anyMatch(object ->
                    object instanceof com.openggf.game.soniccd.objects.common.CdTitleCard && !object.isDestroyed()));
        if (type == com.openggf.game.internal.MediaSceneArt.class) return type.cast(new CdMediaSceneArt());
        if (type == com.openggf.game.soniccd.objects.art.CdObjectArtProvider.class) return type.cast(objectArt);
        // ObjHUD draws the HUD as level objects (HUD and Points/Main.asm:67-140).
        if (type == com.openggf.game.internal.ObjectDrawnHud.class) return type.cast(objectArt);
        // Lane CAPTURE: the capture tools open the disc and name stages through this.
        if (type == com.openggf.game.internal.HostedGameMedia.class) return type.cast(HOSTED_MEDIA);
        // Lane LAUNCH: a player's launch attaches the durable backup RAM before the title opens it.
        if (type == com.openggf.game.internal.DurableSaveHost.class) return type.cast(durableSaves);
        // SpawnObjects; RunObjects: the spawner precedes the object pass (Level/Main.asm:402-403).
        if (type == com.openggf.game.internal.SpawnerBeforeObjectPass.class) return type.cast(SPAWNER_FIRST);
        // SpawnObjects walks ObjectLayouts as stored and stops at the first entry beyond the
        // window (Object Spawner.asm:183-196, 125-142): no sort by X.
        if (type == com.openggf.game.internal.StoredOrderObjectLayout.class) return type.cast(STORED_LAYOUT);
        // SpawnObject skips an entry only through its saved-flags bit (Object Spawner.asm:267-282): an entry
        // without one is spawned again while an earlier copy is still live.
        if (type == com.openggf.game.internal.UntrackedEntriesSpawnWhileLive.class) return type.cast(UNTRACKED_COPIES);
        // A spawn that finds no free slot clears its entry's loaded bit again (.SpawnFailed, Object Spawner.asm:144-151).
        if (type == com.openggf.game.internal.FailedSpawnUnmarksEntry.class) return type.cast(FAILED_SPAWN_UNMARKS);
        // CollectRing (Level/_Objects/Ring/Main.asm:290-312) jumps to SubCPUCmd for
        // SCMD_YESSFX on .GainLife. Only .PlaySound submits FM_RING.
        if (type == com.openggf.game.internal.RingExtraLifeReplacesPickupSound.class)
            return type.cast(RING_EXTRA_LIFE_REPLACES_PICKUP);
        // Ring loss writes ID $11 into each spilled ring's slot (Level/_Objects/Ring/Main.asm:353; HurtPlayer,
        // Level/Palmtree Panic/Player Object Collision.asm:236), not Sonic 1's $37.
        if (type == com.openggf.game.internal.LostRingObjectId.class) return type.cast(LOST_RING_ID);
        // HurtPlayer only places the $11 owner; ObjLostRing_Init spills on the owner's turn (Ring/Main.asm:332-405).
        if (type == com.openggf.game.internal.LostRingSpillAtOwnerTurn.class) return type.cast(SPILL_AT_OWNER_TURN);
        if (type == LostRingDisplayProfile.class && game != null && game.currentLevel() != null
                && game.currentLevel().payload().stage().zone() == CdZone.METALLIC_MADNESS) {
            return type.cast(MMZ_SPILL_DISPLAY);
        }
        // LevelStart clears camera RAM (objSpawnRoutine too) and levelVIntCounter, then sets the start
        // camera before the first SpawnObjects_Init (Level/Main.asm:177-183, 210-216, 245, 402).
        if (type == com.openggf.game.internal.StageLoadClearsStageRam.class) return type.cast(CLEARED_STAGE_RAM);
        // RunObjects walks the reserved slots (HUD, title card) before dynObjects.
        if (type == com.openggf.game.internal.NativeFixedLevelObjectPass.class) return type.cast(fixedObjects);
        // Every solid object uses CD's SolidObject family (Object Solid.asm:159-479).
        if (type == com.openggf.game.internal.ReevaluatingSolidRules.class) {
            return type.cast(com.openggf.game.soniccd.objects.common.CdSolidRules.INSTANCE);
        }
        // Title exits to programs other than stages run in the OPTIONS host (docs/architecture/plans/2026-11-12-sonic-cd-programs.md).
        if (type == com.openggf.game.nativemenu.NativeTitleModeProvider.class) return type.cast(programHost);
        if (type == com.openggf.game.soniccd.program.CdSceneCoordinator.class) {
            getSpecialStageProvider(); // binds the flow's special-stage entry and exit
            return type.cast(scenes);
        }
        if (type == com.openggf.game.soniccd.timetravel.CdTimeTravel.class) return type.cast(timeTravel);
        if (type == com.openggf.game.soniccd.objects.shared.SharedSwitchFlags.class) return type.cast(switchFlags);
        // The player's water state outside the player object (bubbles read the timer and the current flag).
        if (type == com.openggf.game.soniccd.player.CdPlayerWater.class) return type.cast(playerStage.water());
        if (type == com.openggf.game.internal.LevelLifePolicy.class) return type.cast(CdLevelLifePolicy.INSTANCE);
        // AddPoints: cap and score extra life (HUD and Points/Main.asm:157-180).
        if (type == com.openggf.game.internal.ScoreAdditionPolicy.class) return type.cast(scenes.scorePolicy());
        // The load runs Level_MainLoop's first iteration, whose addq.w #1,levelFrames precedes its
        // object pass (Level/Main.asm:300, 400-403), so the loaded state is levelFrames 1.
        if (type == com.openggf.game.internal.LoadedLevelFrameCounter.class) return type.cast(LOADED_LEVEL_FRAMES);
        // LevelStart's .ClearFlowers clears levelVIntCounter (Level/Main.asm:178-184): no load carries it.
        if (type == com.openggf.game.internal.LevelLoadSetsVIntCounter.class
                || type == CdLevelVIntCounter.class) return type.cast(levelVIntCounter);
        // Replay only: a VBlank reaching VInterrupt (Level/Main.asm:681) while the previous handler still runs is
        // lost; the recorder observes entry and the timing contract admits the row (2026-11-13 extension).
        if (type == com.openggf.game.internal.UnservicedVBlankAdmission.class) {
            return type.cast(com.openggf.game.internal.UnservicedVBlankAdmission.DECLARED);
        }
        // The load runs Level_MainLoop's first iteration (CdFirstMainLoopIteration): its frame runs no other.
        if (type == com.openggf.game.internal.LevelLoadRunsFirstIteration.class) return type.cast(loadFrame);
        // FindLevelFloor's .CheckNegFloor (Collision Floor.asm:754-759, 843-848).
        if (type == com.openggf.game.internal.FloorProbeNegativeHeight.class) {
            return type.cast(com.openggf.game.internal.FloorProbeNegativeHeight.FULL_HEIGHT_FLAT);
        }
        // FindLevelWall negates a flipped width (neg.w d0) before its .NegWall/.MaxWall branches
        // (Collision Floor.asm:874-953, 956-1028).
        if (type == com.openggf.game.internal.ObjectWallProbeWidth.class) {
            return type.cast(com.openggf.game.internal.ObjectWallProbeWidth.NEGATE);
        }
        // An object floor probe over two blank blocks returns FindLevelFloor2 .IsBlank's
        // $F - (y & $F) plus $10 (Collision Floor.asm:697-702, 788-793).
        if (type == com.openggf.game.internal.ObjectFloorProbeBlankBlocks.class) {
            return type.cast(com.openggf.game.internal.ObjectFloorProbeBlankBlocks.NEXT_BLOCK_DISTANCE);
        }
        // ObjGetLWallDist / CheckBlockLeft pass X to FindLevelWall without eori.w #$F
        // (Collision Check.asm:401-409; common/object_block_collide.asm:384-396).
        if (type == com.openggf.game.internal.ObjectLeftWallProbeEntry.class) {
            return type.cast(com.openggf.game.internal.ObjectLeftWallProbeEntry.RAW);
        }
        // The wall check in front skips on addi.b #$40,d0; bmi.s alone, the ceiling's $80 included
        // (Level/_Objects/Sonic/Main.asm:1179-1182; objects/player.asm:1127-1130).
        if (type == com.openggf.game.internal.GroundWallCheckAngleGate.class) {
            return type.cast(com.openggf.game.internal.GroundWallCheckAngleGate.SIGN);
        }
        return null;
    }

    /** The loaded stage's {@code bossFlags} (level RAM the act 3 events read). */
    private final com.openggf.game.soniccd.objects.boss.CdBossFlagsRewind bossFlags =
            new com.openggf.game.soniccd.objects.boss.CdBossFlagsRewind(com.openggf.game.GameServices::levelOrNull);

    private final com.openggf.game.soniccd.level.CdTimeStopRewind timeStop =
            new com.openggf.game.soniccd.level.CdTimeStopRewind(com.openggf.game.GameServices::levelOrNull);

    /**
     * Campaign time state, the sub-CPU audio command state, the program flow with its backup RAM and the
     * level's switch flags, player and Tidal Tempest water state, boss flags, stop word and pending pause restart
     * are captured by rewind.
     */
    @Override
    public java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
        return java.util.List.of(timeTravel, audioProfile.subCpuAudio().rewindAdapter(), scenes.rewindAdapter(),
                switchFlags, playerStage.water(), playerStage.signalsRewindAdapter(), bossFlags, timeStop,
                zoneWater.tidalTempestRewindAdapter(), levelPause);
    }

    /** {@code ObjSonic_DeadChkGone}'s game-over object and restart timer (scddisasm/src/Level/_Objects/Sonic/Main.asm:2419-2444). */
    @Override public com.openggf.game.GameOverFlowProvider getGameOverFlowProvider() { return gameOverFlow; }

    /**
     * A stage started through the title or stage-select host: lives and score follow
     * {@code LevelStart}'s first-load reset or carry from the last stage (lane FLOW2).
     */
    @Override public void onNewGameFromTitle(com.openggf.game.GameStateManager gameState) {
        scenes.stageStartingFromHost(gameState);
    }

    /** ObjPowerup's stars are Sonic 1's trail stars (Powerup/Main.asm:95-133; Ani_Powerup 1-4). */
    @Override public boolean hasTrailInvincibilityStars() { return true; }
    @Override public PhysicsProvider getPhysicsProvider() { return physicsProvider; }

    private static final com.openggf.game.rules.GamePolicies POLICIES =
            new com.openggf.game.rules.GamePolicies(com.openggf.game.soniccd.player.SonicCdRules.RULES);

    @Override public com.openggf.game.rules.GamePolicies getPolicies() { return POLICIES; }

    private com.openggf.game.soniccd.special.CdSpecialStageProvider specialStage;

    /** The special stage program pair (SPMM__/SPSS__), read from the loaded disc. */
    @Override
    public com.openggf.game.SpecialStageProvider getSpecialStageProvider() {
        if (specialStage == null) {
            specialStage = new com.openggf.game.soniccd.special.CdSpecialStageProvider(() -> disc());
            // The program flow primes each entry and takes the exit contract (IPX RunMMD of SCMD_SPECSTAGE).
            scenes.bindSpecialStage(specialStage::prepare);
            specialStage.onEnded(result -> scenes.specialStageEnded(result, com.openggf.game.GameServices.levelOrNull()));
        }
        return specialStage;
    }
    @Override public LevelInitProfile getLevelInitProfile() { return levelInitProfile; }

    /** Generator-owned measured manifest (tools/soniccd/load_time_manifest.py); never hand-edited. */
    static final String PROFILED_LOAD_TIME_MANIFEST = "/load-time-profiles/soniccd-v1.json";
    /** The hand-tunable copy FAST reads, seeded from {@link #PROFILED_LOAD_TIME_MANIFEST}. */
    static final String FAST_LOAD_TIME_MANIFEST = "/load-time-profiles/soniccd-fast-v1.json";

    /**
     * Normal-play pacing of Sonic CD's stage loads and video reads ({@link CdLoadTiming}): {@code NONE} immediate,
     * {@code FAST} the hand-tunable copy, {@code PROFILED} (and the reserved {@code REALISTIC}) the measured manifest.
     * Trace replay does not read it (a replay's seed wins, {@link CdLevelVIntCounter}).
     */
    @Override
    public com.openggf.game.timing.LoadTimeProfile createLoadTimeProfile(
            com.openggf.game.timing.LoadTimeSimulationMode mode, java.util.function.Consumer<String> warningSink) {
        return switch (mode) {
            case NONE -> com.openggf.game.timing.LoadTimeProfile.IMMEDIATE;
            case FAST -> com.openggf.game.timing.LoadTimeProfileFactory.resolve(mode,
                    com.openggf.game.timing.LoadTimeProfile.IMMEDIATE, manifest(FAST_LOAD_TIME_MANIFEST, warningSink),
                    warningSink);
            case PROFILED, REALISTIC -> com.openggf.game.timing.LoadTimeProfileFactory.resolve(mode,
                    manifest(PROFILED_LOAD_TIME_MANIFEST, warningSink), null, warningSink);
        };
    }

    private static com.openggf.game.timing.LoadTimeProfile manifest(String resource,
                                                                   java.util.function.Consumer<String> warningSink) {
        try (var input = SonicCdGameModule.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("missing Sonic CD load-time manifest " + resource);
            return com.openggf.game.timing.ProfiledLoadTimeManifest.load(input, warningSink);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to load Sonic CD load-time manifest " + resource, e);
        }
    }
    @Override public TracePlaybackProfile getTracePlaybackProfile() { return CD_TRACE_PLAYBACK_PROFILE; }

    /** ObjSonic_SpecialChunks after the object pass (Sonic/Main.asm:350); see {@link CdPlayerStage}. */
    @Override
    public void applyPlaneSwitching(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        playerStage.applySpecialChunks(player);
    }

    /** CD is not a donor; its native IDs resolve directly ({@link CdAnimationIds}). */
    @Override
    public int resolveAnimationId(CanonicalAnimation canonical) {
        return CdAnimationIds.resolve(canonical);
    }
}
