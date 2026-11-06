package com.openggf.game.soniccd;

import com.openggf.audio.GameAudioProfile;
import com.openggf.game.Game;
import com.openggf.game.*;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.NativeRewindRestoreGuard;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdCheckpoint;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointLoadData;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdCollapsePlatform;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdRing;
import com.openggf.game.soniccd.objects.CdSpring;
import com.openggf.game.soniccd.objects.CdTunnelDoor;
// BEGIN R12B Disc/Door imports.
import com.openggf.game.soniccd.objects.CdSpinningDisc;
import com.openggf.game.soniccd.objects.CdTunnelDoorVertical;
// END R12B Disc/Door imports.
import com.openggf.game.soniccd.objects.CdTunnelDoorSplash;
import com.openggf.game.soniccd.objects.CdTunnelPath;
import com.openggf.game.soniccd.objects.CdMonitor;
import com.openggf.game.soniccd.objects.CdMonitorItem;
import com.openggf.game.soniccd.objects.CdBreakableWall;
import com.openggf.game.soniccd.objects.CdTimePost;
import com.openggf.game.soniccd.objects.CdSignpost;
import com.openggf.game.soniccd.objects.CdBigRing;
import com.openggf.game.soniccd.objects.CdResults;
import com.openggf.game.soniccd.objects.CdTamabboh;
import com.openggf.game.soniccd.objects.CdMosqui;
import com.openggf.game.soniccd.objects.CdLostRing;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerEngineRules;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scene.CdWarpAssets;
import com.openggf.game.soniccd.scene.CdWarpScene;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.SecondaryAbility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Real stock CD module retaining its default STATIC R11A terrain admission and
 * separately named terrain-only and USA opening-object development PLAYER entries.
 * The opening entry executes the bounded Ring/Mosqui owners, not a retail title
 * ingress or full route. Native title/new-game/HUD/audio driver, saves, export and
 * editor entry remain unavailable.
 * The full native metadata catalogue is not a load-capability list. GameLoop
 * remains the sole mode writer.
 */
public final class SonicCdGameModule implements GameModule {
    private static final String SHARED_REWIND_KEY = "soniccd-terrain-player-shared";
    private static final String RESTORE_CALLBACK_KEY = "soniccd-terrain-player-projection";
    private static final String OPENING_SHARED_REWIND_KEY = "soniccd-opening-player-shared";
    private final boolean playerAdmission;
    private final boolean openingObjectsAdmission;
    private final SonicCdZoneRegistry zones = new SonicCdZoneRegistry();
    private final PhysicsProvider rules;
    private final SonicCdStaticAudioProfile audio = new SonicCdStaticAudioProfile();
    private final SonicCdStaticPresentation presentation = new SonicCdStaticPresentation(this);
    private final SonicCdLevelInitProfile profile = new SonicCdLevelInitProfile(this);
    private final SonicCdPlayerPresentation playerPresentation;
    private final SonicCdPlayerInitProfile playerProfile;
    private final PlayableCharacterRegistry characters;
    private CdGameDataSource source;
    private GameplayModeContext playerMode;
    private CdPlayerFrameRuntime playerFrame;
    private CdPlayableSonic playerActor;
    private CdOpeningObjectRuntime openingObjects;
    private PlcLifecycleService openingPlc;
    private PlcLifecycleService terrainPlc;
    private CdR11aAssets authenticatedOpeningAssets;
    private boolean constructingPlayer;
    private RestartCarry stagedRestart;
    private LevelLoadContext stagedRestartContext;
    private LevelProgramState levelProgram;


    // Actual R11B visit; descriptive contexts/values never grant load or callback authority.
    private final SonicCdPastLevelInitProfile pastProfile = new SonicCdPastLevelInitProfile(this);
    private PastCarry stagedPast;
    private LevelLoadContext stagedPastContext;
    private SonicCdPastLoadingVisit pastLoading;
    private com.openggf.game.soniccd.level.SonicCdPastLevel pastLevel;
    private long pastLoadGeneration = -1;

    // Actual R12B FULL assembly. The transient carry only crosses the retired load epoch.
    private final SonicCdAct2PastLevelInitProfile act2PastProfile = new SonicCdAct2PastLevelInitProfile(this);
    private Act2PastCarry stagedAct2Past;
    private LevelLoadContext stagedAct2PastContext;
    private SonicCdAct2PastLoadingVisit act2PastLoading;
    private SonicCdAct2PastWorldHost act2PastHost;
    private com.openggf.game.soniccd.level.SonicCdAct2PastLevel act2PastLevel;
    private long act2PastLoadGeneration = -1;
    private record Act2PastCarry(CdGameDataSource.Act2PastAssets data,
                                CdPlayerBody.Environment environment, CdPlayerFrameRuntime.VIntClock clock,
                                CdLevelGlobals level, int sectionWord,
                                SonicCdPlayerPresentation.ScrollCapture scroll,
                                SonicCdAct2LoadingVisit.PastReturn retained, long generation,
                                LevelProgramState program) { }
    private record Act2PastShared(SonicCdAct2PastLoadingVisit.Snapshot loading,
                                  ProgramServicesState services, OptionalInt goodFutureFlags) { }
    boolean act2PastRegistrySelected() { return stagedAct2Past != null || act2PastLoading != null; }
    boolean act2PastLoadingSelected() { return act2PastLoading != null; }
    SonicCdAct2PastLoadingVisit act2PastLoadingVisit() { validateAct2PastLoadingCut(); return act2PastLoading; }
    SonicCdAct2PastWorldHost act2PastWorldHost() {
        return Objects.requireNonNull(act2PastHost, "No actual R12B World host");
    }
    void requireAct2PastLoadingVisit(SonicCdAct2PastLoadingVisit visit) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || visit == null || visit != act2PastLoading || act2PastHost == null
                || playerFrame == null || playerFrame.closed() || playerActor != null || openingObjects != null
                || pastLoading != null || act2Loading != null || mode == null || mode != playerMode
                || mode.getWorldSession() != programWorld || programWorld.rootGameModule() != this
                || programWorld.resolvedGameModule() != this || programWorld.getDataSource() != source
                || !(mode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct2PastLevel level)
                || level != act2PastLevel
                || !playerFrame.stageSource().ownsAct2Past(level.assets(), source.act2PastAssets().orElseThrow().player())
                || mode.getLevelManager().getCompletedProductionLoadGeneration()
                    != (stagedAct2Past == null ? act2PastLoadGeneration : stagedAct2Past.generation())
                || !mode.getSpriteManager().getAllSprites().isEmpty()
                || !mode.getSpriteManager().getRegisteredSidekicks().isEmpty())
            throw new IllegalStateException("Actual R12B source/World/Frame/load lifetime changed");
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224)
            throw SonicCd.unsupported("R12B requires the native viewport");
    }
    OptionalInt act2PastGoodFutureFlags(SonicCdAct2PastLoadingVisit expected) {
        requireAct2PastLoadingVisit(expected);return levelProgram.goodFutureFlags();
    }
    void validateAct2PastLoadingCut() {
        playerPresentation.requirePastRenderIdle(); requireAct2PastLoadingVisit(act2PastLoading);
        playerFrame.captureShared(); playerFrame.captureActor();
    }
    void serviceAct2PastLoading(int held) {
        playerPresentation.requirePastRenderIdle(); // Reentry rejection precedes terminal failure closure.
        try {
            validateAct2PastLoadingCut(); act2PastLoading.service(held);
            act2PastHost.projectCamera(GameServices.camera());
        } catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
    }
    void validateAct2PastLoadRequest(LevelLoadContext context) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || stagedAct2Past == null || context == null || context != stagedAct2PastContext
                || mode == null || programWorld == null || programWorld != mode.getWorldSession()
                || programWorld.getDataSource() != source || programWorld.rootGameModule() != this
                || programWorld.resolvedGameModule() != this || context.getLevelIndex() != 1
                || context.getLoadMode() != LevelLoadMode.FULL
                || context.getLoadCause() != LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY
                || context.getAssemblyKind() != LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                || !context.isIncludePostLoadAssembly() || context.hasCheckpoint() || context.getRom() != null
                || context.isShowTitleCard() || context.isTitleCardRequiredInHeadlessMode()
                || context.isQueueFreshLevelRuntimeArt()
                || source.act2PastAssets().orElseThrow() != stagedAct2Past.data())
            throw SonicCd.unsupported("Actual R12B FULL load requires its privately staged completed WARP request");
    }
    private void loadActualAct2PastWorld() {
        validateWarpProgrammeOwner(); act2Loading.requireReturnedProgrammeIdle();
        var mode = playerMode; var old = act2Loading.observation(); var gates = playerFrame.environment().movement().gates();
        var p = levelProgramState();
        if (p.phase() != LevelProgramPhase.PAST_PENDING || gates.zoneAct() != 1 || gates.timeZone() != 0x80
                || p.specialStage() != 0 || old.level().spawnMode() != 2 || old.level().levelRestart() != 0
                || (old.level().plcLoadFlags() & 3) != 3 || old.ring().lives() == 0)
            throw act2Boundary(Act2Boundary.ENTRY);
        var actual = requireRuntimeSource().act2PastAssets().orElseThrow(
                () -> SonicCd.unsupported("R12B was not authenticated and retained before media closure"));
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) playerPresentation.getHandler(0).captureRewindState();
        stagedAct2Past = new Act2PastCarry(actual, playerFrame.environment(), playerFrame.vIntClock(), old.level(),
                old.section().sectionWord(), scroll, act2Loading.pastReturnCarry(),
                mode.getLevelManager().getCompletedProductionLoadGeneration(), p);
        stagedAct2PastContext = SonicCdLevelInitProfile.staticStageContext();
        stagedAct2PastContext.setLevelIndex(1); stagedAct2PastContext.setZone(0); stagedAct2PastContext.setAct(1);
        stagedAct2PastContext.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        try {
            mode.getLevelManager().loadLevel(1, LevelLoadMode.FULL, stagedAct2PastContext);
            if (mode.getLevelManager().getCompletedProductionLoadGeneration() != act2PastLoadGeneration
                    || mode.getLevelManager().getCurrentLevel() != act2PastLevel)
                throw new IllegalStateException("Actual R12B FULL load did not commit its generation");
        } catch (IOException failure) { throw new UncheckedIOException("Cannot load actual same-World R12B", failure); }
        finally { stagedAct2Past = null; stagedAct2PastContext = null; }
    }
    void installAct2PastLoadingVisit() {
        validateAct2PastLoadRequest(stagedAct2PastContext);
        if (playerFrame != null || playerActor != null || openingObjects != null || pastLoading != null
                || act2Loading != null || act2PastLoading != null)
            throw new IllegalStateException("Retire old programme before actual R12B assembly");
        var c = stagedAct2Past; playerMode = SessionManager.getCurrentGameplayMode();
        if (!(playerMode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct2PastLevel level)
                || level.assets() != c.data().terrain()) throw act2Boundary(Act2Boundary.ENTRY);
        try {
            var loaded = SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(), requireRuntimeSource().timeWarpPrograms().orElseThrow());
            programServices = withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM, loaded, programServices.initialization());
        } catch (SonicCdProgramLoadServices.Unsupported stopped) {
            programServices = new ProgramServicesState(stopped.completedPrefix(), programServices.initialization(),
                    programServices.environment(), programServices.completedCalls()); throw stopped;
        }
        if (CdProgramInitialization.selectStartBranch(programServices.initialization()) != CdProgramInitialization.Branch.GAME_INIT)
            throw act2Boundary(Act2Boundary.ENTRY);
        var emptyArt = new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES], 0, false);
        var video = playerPresentation.initializeProgramVideo(com.openggf.game.soniccd.resources.CdR12bCameraArtResidency.unknown(), emptyArt);
        programServices = withProgramCall(ProgramStartCall.INIT_VDP, programServices.loads(), programServices.initialization());
        programServices = withProgramCall(ProgramStartCall.INIT_CONTROLLERS, programServices.loads(),
                CdProgramInitialization.initializeControllers(programServices.initialization()));
        // RunMMD clears represented player/control/camera storage in [FF2000,FFFB00).
        // MAINVARS, including the independent LONG V-int counter, retain their actual values.
        var old = c.environment(); var g = old.globals(); var m = old.movement(); var gate = m.gates(); var mg = m.globals();
        var environment = new CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
                new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(gate.zoneAct(),gate.timeZone(),gate.miniSonic(),0,0,gate.speedShoes(),0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(0,0,0,0,0,0,
                        mg.debugAngle(),mg.debugAngleShift(),mg.debugQuadrant(),mg.debugFloorDistance())),
                new CdPlayerBody.Controls(0,0,0,0,0),
                new CdPlayerBody.Globals(g.levelFrames(),0,g.timeAttackMode(),g.debugMode(),g.debugCheat(),0,g.invincible(),g.bossMusic()),old.dynamicSlots());
        var state = new CdPlayerBody.State(new com.openggf.game.soniccd.player.CdPlayerMotionState(
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
                new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
        playerFrame = CdPlayerFrameRuntime.fromAct2PastEntry(c.data().terrain(),c.data().player(),state,environment,video.playerArt(),c.clock());
        var fg = new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0);
        // WARP cleared [8000,FC00); RunMMD repeats [2000,FB00). Deformation is consequently known zero.
        var bg = new CdR11aBackgroundScroll.State(0,0,0,0,0,0,zeroPast(8),0,0,0,
                c.scroll().background().cloudRam(),zeroPast(512),zeroPast(1024),0);
        var copies = c.scroll().copies();
        var hostScroll = new SonicCdAct2PastWorldHost.Scroll(fg,bg,video.selection().backgroundPlane(),video.selection().backgroundPlane(),
                new SonicCdAct2PastWorldHost.Copies(copies.fgX16_16(),copies.fgY16_16(),copies.bg(),copies.fgFlags(),copies.bgFlags()),0);
        var selected = SonicCdAct2PastDisplay.project(new SonicCdAct2PastDisplay.Inputs(java.util.Optional.empty(),
                video.selection().displayedSat(),video.selection().displayedPalette(),zeroPast(1024),0,false,
                video.selection().backgroundPlane(),video.selection().backgroundPlane(),video.currentPatterns(),video.playerArt()));
        var hostVideo = new SonicCdAct2PastWorldHost.Video(zeroPast(640),java.util.Optional.empty(),video.playerArt(),video.currentPatterns(),
                new SonicCdAct2PastWorldHost.Published(selected));
        act2PastLevel = level; act2PastLoadGeneration = Math.incrementExact(c.generation()); levelProgram = c.program();
        act2PastHost = new SonicCdAct2PastWorldHost(this,c.data().terrain(),c.data().player(),playerFrame,
                new SonicCdAct2PastWorldHost.Initial(c.retained().globals(),hostScroll,hostVideo,c.retained().savedFlags()));
        var art = com.openggf.game.soniccd.resources.CdR12bSectionArt.fromAssets(c.data().terrain());
        var section = art.fromValues(zeroPast(512),video.currentPatterns(),c.sectionWord(),
                com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),java.util.Optional.empty(),
                new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
        var p = c.program(); var programme = new SonicCdAct2PastLoadingVisit.Program(p.demoMode(),p.timeStones(),
                c.retained().goodFuture(),p.usePlayer2(),p.specialStage(),p.palFadeFlags(),programServices.initialization().palClearFlagBit0(),
                p.gameMode(),p.levelStarted(),0);
        // WARP clears [FB00,FC00) CPU normal/target, then RunMMD clears [FA80,FB00) water.
        // Selected WARP white pixels supply none of these CPU variable bytes.
        act2PastLoading = new SonicCdAct2PastLoadingVisit(act2PastHost,c.data().terrain(),c.data().player(),playerFrame,art,
                new SonicCdAct2PastLoadingVisit.Entry(c.retained().saved(),section,programme,c.level(),zeroPast(128),zeroPast(128),
                        zeroPast(128),video.selection().displayedPalette(),zeroPast(31*64),p.whiteFadeSteps(),false));
        act2PastHost.validate(act2PastLoading,playerFrame,c.data().terrain(),c.data().player());
        var ordinary=new SonicCdAct2PastOrdinaryWorld(this,act2PastLoading,act2PastHost,playerFrame,c.data().terrain(),c.data().player(),art);
        act2PastLoading.bindOrdinaryOwner(act2PastHost,ordinary);
        act2PastLoading.begin(); act2PastHost.projectCamera(GameServices.camera());
    }
    private static CdAssetBytes zeroPast(int count) { return CdAssetBytes.copyOf(new byte[count]); }
    void registerAct2PastRestoreProjection() {
        validateAct2PastLoadingCut(); var registry = Objects.requireNonNull(playerMode.getRewindRegistry());
        registry.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
        registry.registerPostRestoreCallback(RESTORE_CALLBACK_KEY,playerPresentation::afterRestore);
    }

    private final SonicCdAct2ZoneRegistry act2Zones = new SonicCdAct2ZoneRegistry(this);
    CdGameDataSource actualAct2RegistrySource() {
        if (stagedAct2 == null && act2Loading == null && stagedAct2Past == null && act2PastLoading == null) throw new IllegalStateException("No actual Act2 programme selection");
        return requireRuntimeSource();
    }
    private final SonicCdAct2LevelInitProfile act2Profile = new SonicCdAct2LevelInitProfile(this);
    private Act2Carry stagedAct2;
    private LevelLoadContext stagedAct2Context;
    private SonicCdAct2LoadingVisit act2Loading;
    private com.openggf.game.soniccd.level.SonicCdAct2Level act2Level;
    private long act2LoadGeneration = -1;
    enum Act2LoadPhase { FIRST_PLC, SECTION_PLC, FROM_BLACK, BEGIN_LEVEL, OLD_WHITE_FADE, OLD_RETURNED }
    enum Act2LoadCall {
        LOAD_LEVEL_PCM, INIT_VDP, INIT_CONTROLLERS, NORMAL_LOAD, LEVEL_SERVICE, PROCESS_PLCS,
        PLAY_LEVEL_MUSIC, IMMEDIATE_LEVEL, IMMEDIATE_STANDARD, CLEAR_FLOWERS, CLEAR_OBJECTS,
        CLEAR_MISC, CLEAR_CAMERA, CLEAR_SCREEN, PALETTE_SONIC, LEVEL_SIZE, LEVEL_SCROLL,
        BLOCKS_RAM, LEVEL_LAYOUT, PALETTE_LEVEL, INITIAL_FOREGROUND, INITIAL_BACKGROUND,
        COLLISION_POINTER, LOAD_PLAYER, RESERVED_HUD, LIFE_ICON, RESERVED_TIME_ICON, TITLE_CARD,
        BEGIN_MAIN, UPDATE_ANIMATED_TILES, FROM_BLACK_SERVICE, FROM_BLACK_COLOURS, LEVEL_STARTED,
        ORDINARY_PUBLICATION, ORDINARY_DRAW_LEVEL, ORDINARY_SLOW_PLC, UPDATE_HUD, ORDINARY_SERVICE,
        LEVEL_FRAME_INCREMENT, SPAWN_OBJECTS, PRIMARY_INIT, PRIMARY_MAIN, RESERVED_OBJECTS,
        RING_BODY, SPRING_BODY, ANIMAL_PRESENCE, TITLE_CARD_BODY, DYNAMIC_OBJECTS, CLEAR_DEMO_INDEX,
        POST_OBJECT_GUARDS, DRAW_OBJECTS, PALETTE_CYCLE, UPDATE_SECTION, PREPARE_PLCS,
        UPDATE_GLOBAL_ANIMATIONS, ORDINARY_MAIN_RETURN, MONITOR_SOLID, MONITOR_FLOOR_ANGLE, MONITOR_FM,
        TIME_POST_DIRECTION, TIME_POST_SUB_CPU, MONITOR_BODY, TIME_POST_BODY,
        BOULDER_BODY, BOULDER_SOLID, PLATFORM_BODY, PLATFORM_SOLID, PLATFORM_PRIMARY_ANGLE,
        ANTON_PRIMARY_ANGLE, ANTON_BODY, PATA_BATA_BODY,
        TAMABBOH_PRIMARY_ANGLE, TAMABBOH_PLAYER_Y, TAMABBOH_PLAYER_X, TAMABBOH_FM, TAMABBOH_BODY,
        FLOAT_BLOCK_SOLID, FLOAT_BLOCK_BODY,
        CHECKPOINT_LEVEL_READ, CHECKPOINT_PARENT_READ, CHECKPOINT_SAVED_READ, CHECKPOINT_GLOBAL,
        CHECKPOINT_SAVE_STORE, CHECKPOINT_SAVE, CHECKPOINT_FM, CHECKPOINT_BODY,
        WALL_BODY, WALL_SOLID, WALL_FM, WALL_HALVE_X,
        COLLAPSE_PLATFORM_BODY, COLLAPSE_PLATFORM_SOLID, COLLAPSE_PLATFORM_FM,
        LOG_SHADOW_BODY, MOSQUI_BODY, MOSQUI_PRIMARY_ANGLE, MOSQUI_FM,
        EXPLOSION_BODY, POINTS_BODY,
        TUNNEL_DOOR_BODY, TUNNEL_DOOR_PRIMARY_TOP, TUNNEL_DOOR_SECONDARY_TOP, TUNNEL_DOOR_SECONDARY_READ, TUNNEL_DOOR_FM,
        FLOWER_BODY, FLOWER_PRIMARY_ANGLE,
        TUNNEL_PATH_BODY, TUNNEL_PATH_PLAYER_READ, TUNNEL_PATH_SOURCE_READ, TUNNEL_PATH_PLAYER, TUNNEL_PATH_FM,
        TIME_STARS_INIT, TIME_STARS_ACTIVE, TIME_STARS_BODY,
        MONITOR_ITEM_BODY, MONITOR_ITEM_SHIELD_AWARD, SHIELD_BODY, SHIELD_INIT, SHIELD_ACTIVE,
        MONITOR_ITEM_RING_AWARD,
        TUNNEL_DOOR_SPLASH_BODY, TUNNEL_DOOR_SPLASH_FM,
        ROBOT_GENERATOR_BODY,
        MOVING_SPRING_BODY,
        SWING_BODY,
        OLD_EXIT_PRELUDE, OLD_EXIT_CLEAR_PLCS, OLD_EXIT_FADE_SERVICE, OLD_EXIT_WHITE_COLOURS, OLD_EXIT_RETURN
    }
    enum Act2Boundary { ENTRY, PRIMARY_BODY, RESERVED_HUD, DYNAMIC_OBJECTS, LEVEL_FLOW, POWERUP_ART, PALETTE_CYCLE, GLOBAL_ANIMATIONS, MONITOR_ITEM_REWARD, SHIELD_LAYER, OLD_EXIT_RESTART, OLD_EXIT_FIRST_LOAD, OLD_EXIT_SPECIAL_STAGE, OLD_EXIT_WARP_PROGRAMME, OLD_EXIT_LEVEL_OVER, R12B_LOAD }
    static final class UnsupportedAct2Loading extends UnsupportedOperationException {
        private final Act2Boundary boundary;
        UnsupportedAct2Loading(Act2Boundary boundary) { super("Actual R12A stopped at " + boundary); this.boundary = boundary; }
        Act2Boundary boundary() { return boundary; }
    }
    record Act2LoadState(com.openggf.game.soniccd.level.CdR12aTerrainAssets assets, Act2LoadPhase phase,
                         CdAssetBytes cpuPalette, CdAssetBytes displayedPalette, CdRingGlobals ring,
                         CdLevelGlobals level, com.openggf.game.soniccd.resources.CdR12aSectionArt.State section,
                         CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                         List<Act2LoadCall> calls, List<PastSound> sounds,
                         com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.Snapshot placement,
                         CdAssetBytes reservedObjects) {
        Act2LoadState { calls = List.copyOf(calls); sounds = List.copyOf(sounds); }
    }
    private record Act2Carry(CdGameDataSource.Act2Assets data, CdPlayerBody.Environment environment,
                             CdPlayerFrameRuntime.VIntClock clock, CdRingGlobals ring, CdLevelGlobals level,
                             int sectionWord, CdAssetBytes cloudRam, SonicCdPlayerPresentation.DrawCopies copies,
                             SonicCdPastLoadingVisit.ExitCarry mainvars, int displayedVScrollLongBits,
                             long generation, LevelProgramState program) { }
    private record Act2OpeningShared(SonicCdAct2LoadingVisit.Snapshot loading, ProgramServicesState services,
                                     LevelProgramState program, CdWarpScene.State warp, List<ProgramSound> sounds) {
        private Act2OpeningShared { sounds = List.copyOf(sounds); }
    }

    boolean act2LoadingSelected() { return act2Loading != null; }
    Act2LoadState act2LoadState() { validateAct2LoadingCut(); return act2Loading.observation(); }
    UnsupportedAct2Loading act2Boundary(Act2Boundary boundary) { return new UnsupportedAct2Loading(boundary); }
    void requireAct2LoadingVisit(SonicCdAct2LoadingVisit visit) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || visit == null || act2Loading != visit || playerFrame == null
                || playerFrame.closed() || playerActor != null || openingObjects != null || pastLoading != null
                || mode == null || mode != playerMode || levelProgram == null
                || !act2ProgrammePhase(levelProgram.phase())
                || mode.getWorldSession() != programWorld || programWorld.rootGameModule() != this
                || programWorld.resolvedGameModule() != this || programWorld.getDataSource() != source
                || !(mode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct2Level level)
                || level != act2Level || !visit.owns(level.assets(), playerFrame)
                || !playerFrame.stageSource().ownsAct2(level.assets(), source.act2Assets().orElseThrow().player())
                || mode.getLevelManager().getCompletedProductionLoadGeneration()
                        != (stagedAct2 == null ? act2LoadGeneration : stagedAct2.generation())
                || WorldSessionPolicyAccess.hasPolicies(programWorld)
                || GameplayInputFilterAccess.current(mode) != GameplayInputFilter.IDENTITY
                || !mode.getSpriteManager().getAllSprites().isEmpty()
                || !mode.getSpriteManager().getRegisteredSidekicks().isEmpty())
            throw new IllegalStateException("Act2 source/World/Frame/load epoch changed");
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224)
            throw SonicCd.unsupported("Act2 requires actual native viewport");
    }
    private static boolean act2ProgrammePhase(LevelProgramPhase phase) {
        return phase == LevelProgramPhase.ACT2_LOADING || phase == LevelProgramPhase.OLD_EXIT_PRELUDE
                || phase == LevelProgramPhase.WHITE_FADE || phase == LevelProgramPhase.OLD_RETURNED
                || phase == LevelProgramPhase.WARP || phase == LevelProgramPhase.PAST_PENDING;
    }
    /** Retained Act2 capture/rendering remains admitted; ordinary plane/camera mutations do not. */
    void requireAct2OrdinaryPresentation(CdPlayerFrameRuntime sourceFrame) {
        requireAct2LoadingVisit(act2Loading);
        if (sourceFrame != playerFrame || levelProgram.phase() != LevelProgramPhase.ACT2_LOADING)
            throw new IllegalStateException("No ordinary Act2 presentation mutation during old programme exit");
    }
    /** Native Main19-35, only after the actual complete Act2 physical walk. */
    void beginAct2OldProgrammeExit(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); visit.requireOldProgrammePrelude();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.ACT2_LOADING) throw new IllegalStateException("Act2 old exit already entered");
        playerFrame.beginLevelStart();
        levelProgram = new LevelProgramState(0, p.timeStones(), p.specialStage(), 0, p.palFadeFlags(),
                p.gameMode(), 0, 0, LevelProgramPhase.OLD_EXIT_PRELUDE, p.goodFutureFlags());
    }
    /** Main56 follows the reached loaded-PLC branch; an unowned first-load reset stops earlier. */
    void markAct2OldProgrammeInitializing(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_EXIT_PRELUDE) throw new IllegalStateException("No Act2 old initialization store");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode() | 0x80, p.levelStarted(), p.whiteFadeSteps(), p.phase(), p.goodFutureFlags());
    }
    /** Main64 branches on BSET's OLD bit; a skipped fade retains the newly set bit. */
    boolean selectAct2OldWhiteExit(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_EXIT_PRELUDE) throw new IllegalStateException("No Act2 old LevelStart prelude");
        boolean fade = (p.palFadeFlags() & 1) != 0;
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags() | 1, p.gameMode(), p.levelStarted(), 0,
                fade ? LevelProgramPhase.WHITE_FADE : p.phase(), p.goodFutureFlags());
        return fade;
    }
    /** One returned selector12/colour/ProcessPLCs iteration; native69 follows only the22nd. */
    void completeAct2OldWhiteFadeWait(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.WHITE_FADE || p.whiteFadeSteps() >= 22)
            throw new IllegalStateException("No actual Act2 old white-fade wait");
        int completed = p.whiteFadeSteps() + 1;
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                completed == 22 ? p.palFadeFlags() & ~1 : p.palFadeFlags(), p.gameMode(), p.levelStarted(),
                completed, p.phase(), p.goodFutureFlags());
    }
    /** Main72-81 has cleared direction then the whole restart WORD; native act1 retains bit7. */
    void completeAct2OldProgrammeReturn(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (!(p.phase() == LevelProgramPhase.OLD_EXIT_PRELUDE && p.whiteFadeSteps() == 0
                || p.phase() == LevelProgramPhase.WHITE_FADE && p.whiteFadeSteps() == 22))
            throw new IllegalStateException("No completed Act2 old LevelStart return");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), LevelProgramPhase.OLD_RETURNED,
                p.goodFutureFlags());
    }
    /** Null denotes a normal or captured completed-save cut; only the private original
     * Primary publication window can return its exact prepared lock reference. */
    CdOpeningObjectRuntime.TimeWarpLockPublication requireAct2TimeWarpAdmission(CdPlayerFrameRuntime sourceFrame) {
        requireAct2LoadingVisit(act2Loading);
        if (sourceFrame != playerFrame) throw new IllegalStateException("TimeWarp admission belongs to another Act2 Frame");
        return act2Loading.requireTimeWarpAdmission(sourceFrame);
    }
    void validateAct2LoadingCut() { requireAct2LoadingVisit(act2Loading); playerFrame.captureShared(); }
    SonicCdAct2Display.Selection act2SelectedDisplay() { validateAct2LoadingCut(); return act2Loading.selectedDisplay(); }
    void serviceAct2Loading(int held) {
        playerPresentation.requirePastRenderIdle();
        // Reject nested public ingress before the terminal failure owner is entered.
        if (act2Loading != null) { act2Loading.requireItemShieldServiceIdle(); act2Loading.requireOldProgrammeServiceIdle(); }
        try {
            validateAct2LoadingCut();
            var phase = levelProgramState().phase();
            if (phase == LevelProgramPhase.OLD_RETURNED) { beginAct2WarpProgram(); return; }
            if (phase == LevelProgramPhase.WARP) { serviceWarpProgram(held); return; }
            if (phase == LevelProgramPhase.PAST_PENDING) {
                validateWarpProgrammeOwner();
                // IPX.RunLevel557-563 uses the retained R12SubCmds row (653-654).
                // Past0 does not read goodFuture or use either MMD's returned BYTE.
                if (playerFrame.environment().movement().gates().zoneAct() != 1
                        || (playerFrame.environment().movement().gates().timeZone() & 0x7F) != 0)
                    throw act2Boundary(Act2Boundary.ENTRY);
                loadActualAct2PastWorld(); return;
            }
            act2Loading.service(held); playerPresentation.projectAct2Loading();
        }
        catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
    }
    void completeAct2LoadingBeginLevel(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit); playerFrame.captureShared(); var p = levelProgram;
        if (p.phase() != LevelProgramPhase.ACT2_LOADING) throw new IllegalStateException("Old Act2 programme cannot begin another ordinary level");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode() & 0x7F, 1, p.whiteFadeSteps(), p.phase(), p.goodFutureFlags());
    }
    void clearAct2PaletteFlag(SonicCdAct2LoadingVisit visit) {
        requireAct2LoadingVisit(visit);
        if (levelProgram.phase() != LevelProgramPhase.ACT2_LOADING) throw new IllegalStateException("Old Act2 programme has no initial palClear continuation");
        if (CdProgramInitialization.requirePalClearFlagBit0(programServices.initialization()))
            throw act2Boundary(Act2Boundary.ENTRY);
        // Main323 BCLR0 is reached: CLEAR remains CLEAR. Knowledge comes only from the cold MAINVARS clear.
    }
    void validateAct2LoadRequest(LevelLoadContext context) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || stagedAct2 == null || context == null || context != stagedAct2Context
                || mode == null || programWorld != mode.getWorldSession() || programWorld.getDataSource() != source
                || programWorld.rootGameModule() != this || programWorld.resolvedGameModule() != this
                || context.getLevelIndex() != 1 || context.getLoadMode() != LevelLoadMode.FULL
                || context.getLoadCause() != LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY
                || context.getAssemblyKind() != LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                || !context.isIncludePostLoadAssembly() || context.hasCheckpoint() || context.getRom() != null
                || context.isShowTitleCard() || context.isTitleCardRequiredInHeadlessMode() || context.isQueueFreshLevelRuntimeArt()
                || source.act2Assets().orElse(null) != stagedAct2.data())
            throw SonicCd.unsupported("Act2 FULL load requires the exact private old IPX return request");
    }
    private void loadActualAct2World() {
        validatePastLoadingCut();
        var old = pastLoading.observation(); var p = levelProgramState(); var gate = playerFrame.environment().movement().gates();
        if (old.phase() != PastLoadPhase.OLD_IPX_RETURNED || old.ring().lives() == 0
                || gate.timeZone() != 1 || gate.zoneAct() != 1 || p.specialStage() != 0
                || p.phase() != LevelProgramPhase.OLD_IPX_RETURNED || p.palFadeFlags() != 0
                || old.level().levelRestart() != 0 || (old.level().plcLoadFlags() & 3) != 1)
            throw pastLoadingBoundary(PastLoadBoundary.NEXT_ACT_PROGRAMME_ADMISSION);
        if (CdProgramInitialization.requirePalClearFlagBit0(programServices.initialization()))
            throw act2Boundary(Act2Boundary.ENTRY);
        var pair = source.act2Assets().orElseThrow(
                () -> pastLoadingBoundary(PastLoadBoundary.NEXT_ACT_PROGRAMME_ADMISSION));
        var disc = requireRuntimeSource().assets().identity().observedWholeDiscSha256();
        if (!pair.terrain().identity().observedWholeDiscSha256().equals(disc)
                || !pair.player().identity().observedWholeDiscSha256().equals(disc))
            throw new IllegalArgumentException("Retained R12A pair belongs to another original disc");
        var mainvars=pastLoading.oldIpxReturnCarry();
        if(mainvars.demoDataIndexWord()==0x0800) throw act2Boundary(Act2Boundary.ENTRY);
        var scroll = playerPresentation.capturePastScroll();
        stagedAct2 = new Act2Carry(pair, playerFrame.environment(), playerFrame.vIntClock(), old.ring(), old.level(),
                old.section().sectionWord(), scroll.background().cloudRam(), scroll.copies(), mainvars,
                pastLoading.selectedDisplay().inputs().vscrollLongBits(),
                playerMode.getLevelManager().getCompletedProductionLoadGeneration(), p);
        stagedAct2Context = SonicCdLevelInitProfile.staticStageContext();
        stagedAct2Context.setLevelIndex(1);
        stagedAct2Context.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        try {
            playerMode.getLevelManager().loadLevel(1, LevelLoadMode.FULL, stagedAct2Context);
            var mode = SessionManager.getCurrentGameplayMode();
            if (mode.getLevelManager().getCompletedProductionLoadGeneration() != act2LoadGeneration
                    || mode.getLevelManager().getCurrentLevel() != act2Level)
                throw new IllegalStateException("Actual Act2 FULL load did not commit its own epoch");
        } catch (IOException failure) { throw new UncheckedIOException("Cannot load actual same-World Act2", failure); }
        finally { stagedAct2 = null; stagedAct2Context = null; }
    }

    enum PastLoadPhase { FIRST_PLC, SECTION_PLC, ANIMATED_TILES, FROM_WHITE, BEGIN_LEVEL, OLD_BLACK_FADE, OLD_CLEAR_WAIT, OLD_RETURNED, OLD_IPX_RETURNED }
    enum PastLoadCall {
        LOAD_LEVEL_PCM, INIT_VDP, INIT_CONTROLLERS, NORMAL_LOAD, LEVEL_SERVICE, PROCESS_PLCS,
        PLAY_LEVEL_MUSIC, IMMEDIATE_LEVEL, IMMEDIATE_STANDARD, CLEAR_FLOWERS, CLEAR_OBJECTS,
        CLEAR_MISC, CLEAR_CAMERA, CLEAR_SCREEN, PALETTE_SONIC, LEVEL_SIZE, LOAD_SAVED, LEVEL_SCROLL,
        BLOCKS_RAM, LEVEL_LAYOUT, PALETTE_LEVEL, SECTION_ART, INITIAL_FOREGROUND,
        INITIAL_BACKGROUND, COLLISION_POINTER, LOAD_PLAYER, RESERVED_HUD, LIFE_ICON, RESERVED_TIME_ICON, BEGIN_MAIN,
        UPDATE_ANIMATED_TILES, FROM_WHITE_SERVICE, FROM_WHITE_COLOURS, LEVEL_STARTED,
        ORDINARY_PUBLICATION, ORDINARY_DRAW_LEVEL, ORDINARY_SLOW_PLC, UPDATE_HUD, ORDINARY_SERVICE, LEVEL_FRAME_INCREMENT,
        SPAWN_OBJECTS, PRIMARY_INIT, PRIMARY_MAIN, RESERVED_OBJECTS, RING_BODY, DYNAMIC_OBJECTS, SPRING_BODY, PLATFORM_BODY,
        TUNNEL_DOOR_PRIMARY_SOLID, TUNNEL_DOOR_SECONDARY_SOLID, TUNNEL_DOOR_BODY,
        MONITOR_SOLID, MONITOR_FLOOR_ANGLE, MONITOR_FM, TIME_POST_DIRECTION, TIME_POST_SUB_CPU, MONITOR_BODY, TIME_POST_BODY,
        CLEAR_DEMO_INDEX, POST_OBJECT_GUARDS, DEAD_CAMERA_FREEZE, DRAW_OBJECTS, PALETTE_CYCLE,
        UPDATE_SECTION, PREPARE_PLCS, UPDATE_GLOBAL_ANIMATIONS, ORDINARY_MAIN_RETURN,
        FLOAT_BLOCK_SOLID, FLOAT_BLOCK_BODY, ANIMAL_PRESENCE, PATA_BATA_BODY, TAMABBOH_BODY, COLLAPSE_PLATFORM_BODY, BOULDER_BODY, MOSQUI_BODY, SPIKES_BODY, ANTON_BODY, ROBOT_GENERATOR_BODY, EXPLOSION_BODY, GOAL_POST_BODY, SIGNPOST_BODY, BIG_RING_BODY, RESULTS_BODY, OLD_EXIT_PRELUDE, OLD_EXIT_CLEAR_PLCS, OLD_EXIT_FADE_SERVICE, OLD_EXIT_BLACK_COLOURS, OLD_EXIT_CLEAR_PALETTE, OLD_EXIT_LOAD_SERVICE, OLD_EXIT_RETURN, OLD_IPX_BLANK
    }
    enum PastLoadBoundary { ENTRY, ANIMATED_TILES, DISPLAY_PUBLICATION, POWERUP_ART, LEVEL_VINT_ART_PUBLICATION, PLACEMENT, PRIMARY_BODY, RESERVED_HUD, DYNAMIC_OBJECTS, LEVEL_FLOW, MONITOR_LAYER_READ, LEVEL_RESTART, PALETTE_CYCLE, GLOBAL_ANIMATIONS, LEVEL_END_PROGRAMME, OLD_EXIT_SPECIAL_STAGE, OLD_EXIT_MARKED_PERIOD, IPX_LEVEL_GAME_OVER, IPX_LEVEL_TIME_WARP, IPX_LEVEL_SPECIAL_STAGE, NEXT_ACT_PROGRAMME_ADMISSION }
    static final class UnsupportedPastLoading extends UnsupportedOperationException {
        private final PastLoadBoundary boundary;
        private UnsupportedPastLoading(PastLoadBoundary boundary) {
            super("Actual R11B loading stopped at " + boundary); this.boundary = boundary;
        }
        PastLoadBoundary boundary() { return boundary; }
    }
    // Typed request metadata and one native music-latch projection; no audio synthesis.
    sealed interface PastSound permits PastCheckpointFm, PastFrameSound, PastSubCpuSound, PastRingFm, PastRingSubCpu, PastSpringFm, PastTunnelDoorFm, PastMonitorFm, PastTimePostSubCpu, PastTamabbohFm, PastCollapsePlatformFm, PastMosquiFm, PastLostRingFm, PastRobotGeneratorFm, PastProjectorFm, PastSignpostSound, PastBigRingFm, PastResultsSound, PastProgrammeSound, PastWallFm, PastTunnelPathFm, PastMonitorItemShieldFm, PastMonitorItemRing, PastTunnelDoorSplashFm, PastDiscFm, PastVerticalDoorFm { }
    public record PastMonitorItemRing(CdMonitorItem.RingAwardSound sound) implements PastSound {
        public PastMonitorItemRing { Objects.requireNonNull(sound); }
    }
    // BEGIN R12B Disc/Door immutable FM alternatives; no operation/cut is retained.
    record PastDiscFm(CdSpinningDisc.FmSound sound) implements PastSound {
        PastDiscFm { Objects.requireNonNull(sound); }
    }
    record PastVerticalDoorFm(CdTunnelDoorVertical.FmSound sound) implements PastSound {
        PastVerticalDoorFm { Objects.requireNonNull(sound); }
    }
    // END R12B Disc/Door immutable FM alternatives.
    record PastMonitorItemShieldFm(CdMonitorItem.ShieldSound sound) implements PastSound {
        PastMonitorItemShieldFm { Objects.requireNonNull(sound); }
    }
    record PastTunnelPathFm(CdTunnelPath.FmSound sound) implements PastSound {
        PastTunnelPathFm { Objects.requireNonNull(sound); }
    }
    record PastCheckpointFm(CdCheckpoint.FmSound sound) implements PastSound {
        PastCheckpointFm { Objects.requireNonNull(sound); }
    }
    record PastFrameSound(CdPlayerFrameRuntime.Sound sound) implements PastSound {
        PastFrameSound { Objects.requireNonNull(sound); }
    }
    enum PastMusicCommand { SCMD_PAUSECDA, SCMD_PAUSEPCM, SCMD_UNPAUSECDA, SCMD_UNPAUSEPCM }
    record PastSubCpuSound(PastMusicCommand command) implements PastSound {
        PastSubCpuSound { Objects.requireNonNull(command); }
    }
    record PastRingFm(CdRing.FmSound sound) implements PastSound {
        PastRingFm { Objects.requireNonNull(sound); }
    }
    record PastRingSubCpu(CdRing.SubCpuCommand command) implements PastSound {
        PastRingSubCpu { Objects.requireNonNull(command); }
    }
    record PastSpringFm(CdSpring.FmSound sound) implements PastSound {
        PastSpringFm { Objects.requireNonNull(sound); }
    }
    record PastTunnelDoorSplashFm(CdTunnelDoorSplash.FmSound sound) implements PastSound {
        PastTunnelDoorSplashFm { Objects.requireNonNull(sound); }
    }
    record PastTunnelDoorFm(CdTunnelDoor.FmSound sound) implements PastSound {
        PastTunnelDoorFm { Objects.requireNonNull(sound); }
    }
    record PastMonitorFm(CdMonitor.FmSound sound) implements PastSound {
        PastMonitorFm { Objects.requireNonNull(sound); }
    }
    record PastTamabbohFm(CdTamabboh.FmSound sound) implements PastSound {
        PastTamabbohFm { Objects.requireNonNull(sound); }
    }
    /** Completed immutable native LostRing FM scalar; no request, SourceCut or invocation token. */
    record PastLostRingFm(CdLostRing.Sound sound) implements PastSound {
        PastLostRingFm { Objects.requireNonNull(sound); }
    }
    record PastMosquiFm(CdMosqui.Sound sound) implements PastSound {
        PastMosquiFm { Objects.requireNonNull(sound); }
    }
    record PastSignpostSound(CdSignpost.Sound sound) implements PastSound {
        PastSignpostSound { Objects.requireNonNull(sound); }
    }
    record PastBigRingFm() implements PastSound {
        int nativeId() { return CdBigRing.FM_BIG_RING; }
    }
    record PastProgrammeSound(ProgramSound sound) implements PastSound {
        PastProgrammeSound { Objects.requireNonNull(sound); }
        int nativeId() { return sound.nativeId(); }
    }
    record PastResultsSound(CdResults.Sound sound) implements PastSound {
        PastResultsSound { Objects.requireNonNull(sound); }
        int nativeId() { return sound.nativeId(); }
    }
    record PastProjectorFm(int nativeId) implements PastSound {
        PastProjectorFm { if(nativeId!=0x9E)throw new IllegalArgumentException("Projector native FM9E only"); }
    }
    record PastRobotGeneratorFm(int nativeId) implements PastSound {
        PastRobotGeneratorFm { if (nativeId != 0x9E && nativeId != 0xD9) throw new IllegalArgumentException("Robot Generator native FM"); }
    }
    record PastCollapsePlatformFm(int nativeId) implements PastSound {
        PastCollapsePlatformFm {
            if (nativeId != CdCollapsePlatform.COLLAPSE_FM) throw new IllegalArgumentException("Collapse requests only FM_A3");
        }
    }
    record PastWallFm(int nativeId) implements PastSound {
        PastWallFm { if(nativeId!=CdBreakableWall.BREAK_FM)throw new IllegalArgumentException("Wall emits native FM_B0 only"); }
    }
    record PastTimePostSubCpu(CdTimePost.SubCpuCommand command) implements PastSound {
        PastTimePostSubCpu { Objects.requireNonNull(command); }
    }
    record PastLoadState(com.openggf.game.soniccd.level.CdR11bAssets assets, PastLoadPhase phase,
                         CdAssetBytes cpuPalette, CdAssetBytes displayedPalette,
                         CdRingGlobals ring, CdLevelGlobals level,
                         com.openggf.game.soniccd.resources.CdR11bSectionArt.State section,
                         CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                         List<PastLoadCall> calls, List<PastSound> sounds) {
        PastLoadState { calls = List.copyOf(calls); sounds = List.copyOf(sounds); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PastLoadState v && assets == v.assets && phase == v.phase
                    && java.util.Arrays.equals(cpuPalette.copy(), v.cpuPalette.copy())
                    && java.util.Arrays.equals(displayedPalette.copy(), v.displayedPalette.copy())
                    && ring.equals(v.ring) && level.equals(v.level) && section.equals(v.section)
                    && actor.equals(v.actor) && shared.equals(v.shared) && calls.equals(v.calls) && sounds.equals(v.sounds);
        }
        @Override public int hashCode() {
            return Objects.hash(assets, phase, java.util.Arrays.hashCode(cpuPalette.copy()),
                    java.util.Arrays.hashCode(displayedPalette.copy()), ring, level, section, actor, shared, calls, sounds);
        }
    }
    private record PastCarry(com.openggf.game.soniccd.level.CdR11bAssets assets,
                             CdTimeWarpSaveData.State saved, CdPlayerBody.Environment environment,
                             CdPlayerFrameRuntime.VIntClock clock, CdRingGlobals ring, CdLevelGlobals level,
                             int sectionWord, CdAssetBytes cloudRam, SonicCdPlayerPresentation.DrawCopies copies,
                             CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                             CdOpeningObjectRuntime.ScoreGlobals score, int displayedVScrollLongBits,
                             long generation, LevelProgramState program, CdAssetBytes savedObjectFlags,
                             CdOpeningObjectRuntime.DevGlobals retainedDev, CdCheckpointSaveData.State checkpointSaved) { }
    private record PastOpeningShared(SonicCdPastLoadingVisit.Snapshot loading, ProgramServicesState services,
                                     LevelProgramState program) { }

    boolean pastLoadingSelected() { return pastLoading != null; }
    PastLoadState pastLoadState() {
        validatePastLoadingCut(); return pastLoading.observation();
    }
    UnsupportedPastLoading pastLoadingBoundary(PastLoadBoundary boundary) {
        return new UnsupportedPastLoading(Objects.requireNonNull(boundary));
    }
    void requirePastLoadingVisit(SonicCdPastLoadingVisit visit) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || visit == null || pastLoading != visit || playerFrame == null
                || playerFrame.closed() || playerActor != null || openingObjects != null || mode == null || mode != playerMode
                || levelProgram == null || !pastProgrammePhase(levelProgram.phase())
                || mode.getWorldSession() != programWorld || programWorld.rootGameModule() != this
                || programWorld.resolvedGameModule() != this || programWorld.getDataSource() != source
                || !(mode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdPastLevel level)
                || level != pastLevel || !visit.owns(level.assets(), playerFrame)
                || !playerFrame.stageSource().ownsPast(level.assets())
                || mode.getLevelManager().getCompletedProductionLoadGeneration()
                        != (stagedPast == null ? pastLoadGeneration : stagedPast.generation())
                || WorldSessionPolicyAccess.hasPolicies(programWorld)
                || GameplayInputFilterAccess.current(mode) != GameplayInputFilter.IDENTITY
                || !mode.getSpriteManager().getAllSprites().isEmpty()
                || !mode.getSpriteManager().getRegisteredSidekicks().isEmpty()) {
            throw new IllegalStateException("Past loading owner/source/World/Frame changed");
        }
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224)
            throw SonicCd.unsupported("Past load requires actual native viewport");
    }
    void validatePastLoadingCut() {
        requirePastLoadingVisit(pastLoading); playerFrame.captureShared();
    }
    SonicCdPastDisplay.Selection pastSelectedDisplay() {
        validatePastLoadingCut();
        return pastLoading.selectedDisplay();
    }
    void servicePastLoading(int held) {
        // Rejected graphics reentry is not a native producer failure and must not close Frame.
        playerPresentation.requirePastRenderIdle();
        try {
            validatePastLoadingCut();
            if (pastLoading.observation().phase() == PastLoadPhase.OLD_IPX_RETURNED
                    && pastLoading.observation().ring().lives() != 0
                    && (playerFrame.environment().movement().gates().timeZone() & 0x80) == 0
                    && levelProgramState().specialStage() == 0) { loadActualAct2World(); return; }
            pastLoading.service(held); playerPresentation.projectPastLoading();
        }
        catch (RuntimeException | Error failure) {
            // A completed RTS is a healthy retained old programme, not a native Frame failure.
            if (!(failure instanceof UnsupportedPastLoading stopped
                    && (stopped.boundary() == PastLoadBoundary.NEXT_ACT_PROGRAMME_ADMISSION
                        || stopped.boundary() == PastLoadBoundary.IPX_LEVEL_GAME_OVER
                        || stopped.boundary() == PastLoadBoundary.IPX_LEVEL_TIME_WARP
                        || stopped.boundary() == PastLoadBoundary.IPX_LEVEL_SPECIAL_STAGE))) closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }
    // Native Main:328-329, after all22 actual FromWhite waits. This is the existing
    // programme value, not permission to run an unbound ordinary player/art service.
    void completePastLoadingBeginLevel(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); playerFrame.captureShared();
        var p = levelProgram;
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode() & 0x7F, 1, p.whiteFadeSteps(), p.phase(), p.goodFutureFlags());
    }
    private static boolean pastProgrammePhase(LevelProgramPhase phase) {
        return phase == LevelProgramPhase.PAST_LOADING || phase == LevelProgramPhase.OLD_EXIT_PRELUDE
                || phase == LevelProgramPhase.OLD_BLACK_FADE || phase == LevelProgramPhase.OLD_CLEAR_WAIT
                || phase == LevelProgramPhase.OLD_RETURNED || phase == LevelProgramPhase.OLD_IPX_RETURNED;
    }
    boolean pastOldProgrammeExitSelected() {
        return pastLoading != null && levelProgram != null && levelProgram.phase() != LevelProgramPhase.PAST_LOADING
                && pastProgrammePhase(levelProgram.phase());
    }
    /** Narrow retained-image/callback admission; equal Gates or public values confer no authority. */
    void requirePastOldProgrammePresentation(SonicCdPlayerPresentation presentation, CdPlayerFrameRuntime frame) {
        requirePastLoadingVisit(pastLoading);
        if (presentation != playerPresentation || frame != playerFrame || !pastOldProgrammeExitSelected())
            throw new IllegalStateException("No actual old-programme Presentation binding");
        pastLoading.requireOldProgrammePresentation();
    }
    void requirePastOldProgrammeCallback(SonicCdPlayerPresentation presentation, CdPlayerFrameRuntime frame) {
        requirePastOldProgrammePresentation(presentation, frame); pastLoading.requireOldProgrammeCallback();
    }
    /** LevelStart19-57 after the real completed physical pass, never from a public value. */
    void beginPastOldProgrammeExit(SonicCdPastLoadingVisit visit, boolean reset) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammePrelude(reset);
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.PAST_LOADING) throw new IllegalStateException("Old exit already entered");
        var next = new LevelProgramState(0, p.timeStones(), p.specialStage(), 0,
                reset ? 0 : p.palFadeFlags(), p.gameMode() | 0x80, 0, 0,
                LevelProgramPhase.OLD_EXIT_PRELUDE, reset ? OptionalInt.of(0) : p.goodFutureFlags());
        playerFrame.beginLevelStart(); // Main35: only the existing pause authority changes.
        levelProgram = next;
    }
    /** Native BSET85 tests the OLD bit; no forced fade count or period branch. */
    boolean selectPastOldBlackExit(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_EXIT_PRELUDE) throw new IllegalStateException("No old exit prelude");
        boolean fade = (p.palFadeFlags() & 1) != 0;
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags() | 1, p.gameMode(), p.levelStarted(), 0,
                fade ? LevelProgramPhase.OLD_BLACK_FADE : LevelProgramPhase.OLD_CLEAR_WAIT, p.goodFutureFlags());
        return fade;
    }
    void completePastOldBlackFadeWait(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_BLACK_FADE || p.whiteFadeSteps() >= 22)
            throw new IllegalStateException("No actual old black-fade wait");
        int count = p.whiteFadeSteps() + 1;
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), count,
                count == 22 ? LevelProgramPhase.OLD_CLEAR_WAIT : p.phase(), p.goodFutureFlags());
    }
    void clearPastOldBlackExitFlags(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_CLEAR_WAIT) throw new IllegalStateException("No old clear continuation");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                0, p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), p.phase(), p.goodFutureFlags());
    }
    void completePastOldBlackExitLoad(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_CLEAR_WAIT || p.palFadeFlags() != 0)
            throw new IllegalStateException("No actual old selectorC return");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), LevelProgramPhase.OLD_RETURNED,
                p.goodFutureFlags());
    }
    void completePastOldIpxBlank(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); visit.requireOldProgrammeStoreContinuation();
        var p = levelProgram;
        if (p.phase() != LevelProgramPhase.OLD_RETURNED) throw new IllegalStateException("No actual MMD return tail");
        levelProgram = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), LevelProgramPhase.OLD_IPX_RETURNED,
                p.goodFutureFlags());
    }
    /** ClearPLCs DBF leaves native return BYTEFF; VSync/VInterrupt preserve it. Not a CPU register owner. */
    int pastOldProgrammeReturnCode() {
        requirePastLoadingVisit(pastLoading);
        if (levelProgram.phase() != LevelProgramPhase.OLD_RETURNED
                && levelProgram.phase() != LevelProgramPhase.OLD_IPX_RETURNED)
            throw new IllegalStateException("No completed old MMD semantic result");
        return 0xFF;
    }
    enum BigRingProgramBoundary { TIME_STONES_READ, SPECIAL_STAGE_STORE }
    static final class UnsupportedBigRingProgram extends UnsupportedOperationException {
        private final BigRingProgramBoundary boundary;
        private UnsupportedBigRingProgram(BigRingProgramBoundary boundary) {
            super("Big Ring has no actual programme preimage at " + boundary); this.boundary = boundary;
        }
        BigRingProgramBoundary boundary() { return boundary; }
    }
    /** Identity observation only: a low-ring invocation never reads programme scalars. */
    LevelProgramState pastBigRingProgramImage(SonicCdPastLoadingVisit visit) {
        requirePastLoadingVisit(visit); return levelProgram;
    }
    int pastBigRingTimeStones(SonicCdPastLoadingVisit visit, LevelProgramState expected) {
        if (expected == null || levelProgram == null) throw new UnsupportedBigRingProgram(BigRingProgramBoundary.TIME_STONES_READ);
        requirePastLoadingVisit(visit);
        if (levelProgram != expected) throw new IllegalStateException("Stale Big Ring programme read");
        return expected.timeStones();
    }
    static final class BigRingProgramPublication {
        private final SonicCdPastLoadingVisit visit;
        private final CdPlayerFrameRuntime frame;
        private final LevelProgramState expected, result;
        private BigRingProgramPublication(SonicCdPastLoadingVisit visit, CdPlayerFrameRuntime frame,
                                          LevelProgramState expected, LevelProgramState result) {
            this.visit = visit; this.frame = frame; this.expected = expected; this.result = result;
        }
        LevelProgramState result() { return result; }
    }
    BigRingProgramPublication preparePastBigRingSpecialStage(SonicCdPastLoadingVisit visit,
                                                            CdPlayerFrameRuntime frame, LevelProgramState expected) {
        if (expected == null || levelProgram == null) throw new UnsupportedBigRingProgram(BigRingProgramBoundary.SPECIAL_STAGE_STORE);
        requirePastLoadingVisit(visit);
        if (playerFrame != frame || levelProgram != expected) throw new IllegalStateException("Stale Big Ring programme preimage");
        visit.requireBigRingProgramPublication(expected);
        var p = expected;
        var result = new LevelProgramState(p.demoMode(), p.timeStones(), 1, p.usePlayer2(), p.palFadeFlags(),
                p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), p.phase(), p.goodFutureFlags());
        return new BigRingProgramPublication(visit, frame, expected, result);
    }
    void validatePreparedPastBigRingSpecialStage(BigRingProgramPublication ready) {
        Objects.requireNonNull(ready); requirePastLoadingVisit(ready.visit);
        if (playerFrame != ready.frame || levelProgram != ready.expected) throw new IllegalStateException("Stale Big Ring programme publication");
        ready.visit.requireBigRingProgramPublication(ready.expected);
    }
    /** Validated reference adoption; no programme selection, callback, allocation or load. */
    void applyPreparedPastBigRingSpecialStage(BigRingProgramPublication ready) {
        if (ready == null || pastLoading != ready.visit || playerFrame != ready.frame || levelProgram != ready.expected)
            throw new IllegalStateException("Stale Big Ring programme adoption");
        ready.visit.requireBigRingProgramPublication(ready.expected);
        levelProgram = ready.result;
    }

    /** Prepared finite flag value; only the actual Results cursor can publish it. */
    // BEGIN R12B Results private Module publication.
    LevelProgramState act2PastResultsProgram(SonicCdAct2PastLoadingVisit core,SonicCdAct2PastObjectPass pass) {
        requireAct2PastLoadingVisit(core);core.requireResultsOwner(pass);
        if(pass==null||!pass.isCurrentResultsOwnersRead())throw new IllegalStateException("No actual R12B Results owner borrow");
        return Objects.requireNonNull(levelProgram);
    }
    static final class Act2PastResultsProgramPublication {
        private final SonicCdAct2PastLoadingVisit core;private final CdPlayerFrameRuntime frame;
        private final SonicCdAct2PastObjectPass pass;private final SonicCdAct2PastObjectPass.Adoption operation;
        private final LevelProgramState expected,result;
        private Act2PastResultsProgramPublication(SonicCdAct2PastLoadingVisit core,CdPlayerFrameRuntime frame,SonicCdAct2PastObjectPass pass,
                SonicCdAct2PastObjectPass.Adoption operation,LevelProgramState expected,LevelProgramState result){this.core=core;this.frame=frame;this.pass=pass;this.operation=operation;this.expected=expected;this.result=result;}
    }
    Act2PastResultsProgramPublication prepareAct2PastResultsGoodFutureFlags(SonicCdAct2PastLoadingVisit core,CdPlayerFrameRuntime frame,
            SonicCdAct2PastObjectPass pass,SonicCdAct2PastObjectPass.Adoption operation,LevelProgramState expected,int flags) {
        requireAct2PastLoadingVisit(core);core.requireResultsOwner(pass);if(playerFrame!=frame||levelProgram!=expected||(flags&~255)!=0)throw new IllegalArgumentException("Stale R12B Results programme cut");
        pass.validateResultsFlagsPublication(operation,expected,false);var p=expected;
        var ready=new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),OptionalInt.of(flags));
        return new Act2PastResultsProgramPublication(core,frame,pass,operation,expected,ready);
    }
    void validatePreparedAct2PastResultsGoodFutureFlags(Act2PastResultsProgramPublication p) {
        Objects.requireNonNull(p);requireAct2PastLoadingVisit(p.core);p.core.requireResultsOwner(p.pass);
        if(playerFrame!=p.frame||levelProgram!=p.expected)throw new IllegalStateException("Stale R12B Results programme publication");p.pass.validateResultsFlagsPublication(p.operation,p.expected,false);
    }
    void applyPreparedAct2PastResultsGoodFutureFlags(Act2PastResultsProgramPublication p) {
        Objects.requireNonNull(p);requireAct2PastLoadingVisit(p.core);p.core.requireResultsOwner(p.pass);
        if(playerFrame!=p.frame||levelProgram!=p.expected)throw new IllegalStateException("Stale R12B Results programme adoption");p.pass.validateResultsFlagsPublication(p.operation,p.expected,true);levelProgram=p.result;
    }
    // END R12B Results private Module publication.
    static final class ResultsProgramPublication {
        private final SonicCdPastLoadingVisit visit;
        private final CdPlayerFrameRuntime frame;
        private final LevelProgramState expected, result;
        private ResultsProgramPublication(SonicCdPastLoadingVisit visit, CdPlayerFrameRuntime frame,
                                          LevelProgramState expected, LevelProgramState result) {
            this.visit = visit; this.frame = frame; this.expected = expected; this.result = result;
        }
        LevelProgramState result() { return result; }
    }
    ResultsProgramPublication preparePastResultsGoodFutureFlags(SonicCdPastLoadingVisit visit,
                                                               CdPlayerFrameRuntime frame,
                                                               LevelProgramState expected, int value) {
        requirePastLoadingVisit(visit);
        if (playerFrame != frame || levelProgram != expected) throw new IllegalStateException("Stale Results programme preimage");
        visit.requireResultsProgrammePublication(expected);
        var p = expected;
        var ready = new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), p.phase(), OptionalInt.of(value));
        return new ResultsProgramPublication(visit, frame, expected, ready);
    }
    void validatePreparedPastResultsGoodFutureFlags(ResultsProgramPublication ready) {
        Objects.requireNonNull(ready); requirePastLoadingVisit(ready.visit);
        if (playerFrame != ready.frame || levelProgram != ready.expected) throw new IllegalStateException("Stale Results programme publication");
        ready.visit.requireResultsProgrammePublication(ready.expected);
    }
    void applyPreparedPastResultsGoodFutureFlags(ResultsProgramPublication ready) {
        validatePreparedPastResultsGoodFutureFlags(ready);
        levelProgram = ready.result;
    }

    void validatePastLoadRequest(LevelLoadContext context) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (!coldProgramStartAdmission || stagedPast == null || context == null || context != stagedPastContext
                || mode == null || programWorld == null || programWorld != mode.getWorldSession()
                || programWorld.getDataSource() != source || programWorld.rootGameModule() != this
                || programWorld.resolvedGameModule() != this || context.getLevelIndex() != 0
                || context.getLoadMode() != LevelLoadMode.FULL
                || context.getLoadCause() != LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY
                || context.getAssemblyKind() != LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                || !context.isIncludePostLoadAssembly() || context.hasCheckpoint() || context.getRom() != null
                || context.isShowTitleCard() || context.isTitleCardRequiredInHeadlessMode()
                || context.isQueueFreshLevelRuntimeArt()) {
            throw SonicCd.unsupported("Actual Past FULL load requires the privately staged completed WARP request");
        }
    }
    @Override public Level loadLevelOverride(int levelIndex) {
        if (stagedAct2Past != null) {
            validateAct2PastLoadRequest(stagedAct2PastContext);
            if (levelIndex != 1) throw SonicCd.unsupported("R12B level index");
            return new com.openggf.game.soniccd.level.SonicCdAct2PastLevel(stagedAct2Past.data().terrain());
        }
        if (stagedAct2 != null) {
            validateAct2LoadRequest(stagedAct2Context);
            if (levelIndex != 1) throw SonicCd.unsupported("Act2 level index");
            return new com.openggf.game.soniccd.level.SonicCdAct2Level(stagedAct2.data().terrain());
        }
        if (stagedPast == null) return null;
        validatePastLoadRequest(stagedPastContext);
        if (levelIndex != 0) throw SonicCd.unsupported("Past level index");
        return new com.openggf.game.soniccd.level.SonicCdPastLevel(stagedPast.assets());
    }

    private void loadActualPastWorld() {
        if (!coldProgramStartAdmission) throw new UnsupportedRestart(RestartBoundary.R11B_LOAD);
        validateTerrainPlayerRequest();
        var mode = playerMode; var actualSource = requireRuntimeSource();
        if (programWorld != mode.getWorldSession() || programWorld.getDataSource() != actualSource)
            throw pastLoadingBoundary(PastLoadBoundary.ENTRY);
        var gate = playerFrame.environment().movement().gates();
        if (gate.timeZone() != 0x80 || gate.zoneAct() != 0 || levelProgramState().specialStage() != 0
                || levelProgramState().palFadeFlags() != 0 || openingObjects.levelGlobals().spawnMode() != 2
                || openingObjects.levelGlobals().levelRestart() != 0
                || (openingObjects.levelGlobals().plcLoadFlags() & 3) != 3
                || levelProgramState().phase() != LevelProgramPhase.PAST_PENDING)
            throw pastLoadingBoundary(PastLoadBoundary.ENTRY);
        validateWarpProgramCut(levelProgramState(), warpState, programSounds);
        com.openggf.game.soniccd.level.CdR11bAssets actual;
        try { actual = com.openggf.game.soniccd.level.CdR11bAssets.fromPrograms(actualSource.timeWarpPrograms().orElseThrow()); }
        catch (IOException failure) { throw new UncheckedIOException("Cannot authenticate actual Past source", failure); }
        var b = (SonicCdPlayerPresentation.ScrollCapture) playerPresentation.getHandler(0).captureRewindState();
        stagedPast = new PastCarry(actual, openingObjects.timeWarpSavedState(), playerFrame.environment(),
                playerFrame.vIntClock(), openingObjects.ringGlobals(), openingObjects.levelGlobals(),
                openingObjects.sectionArtState().sectionWord(), b.background().cloudRam(), b.copies(),
                openingObjects.checkpointState().live(), openingObjects.contactGlobals(), openingObjects.scoreGlobals(),
                warpState.displayedVscrollBgWord(), mode.getLevelManager().getCompletedProductionLoadGeneration(),
                levelProgramState(), openingObjects.placement().capture().savedFlags(), openingObjects.devGlobals(), openingObjects.checkpointState().saved());
        stagedPastContext = SonicCdLevelInitProfile.staticStageContext();
        stagedPastContext.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        try {
            mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, stagedPastContext);
            if (mode.getLevelManager().getCompletedProductionLoadGeneration() != pastLoadGeneration
                    || mode.getLevelManager().getCurrentLevel() != pastLevel)
                throw new IllegalStateException("Actual Past FULL load did not commit its own generation");
        }
        catch (IOException failure) { throw new UncheckedIOException("Cannot load actual same-World Past visit", failure); }
        finally { stagedPast = null; stagedPastContext = null; }
    }

    void installPastLoadingVisit() {
        validatePastLoadRequest(stagedPastContext);
        if (playerFrame != null || playerActor != null || openingObjects != null || pastLoading != null)
            throw new IllegalStateException("Retire Present before actual Past assembly");
        var c = stagedPast;
        playerMode = SessionManager.getCurrentGameplayMode();
        if (!(playerMode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdPastLevel level)
                || level.assets() != c.assets()) throw pastLoadingBoundary(PastLoadBoundary.ENTRY);
        var programs = requireRuntimeSource().timeWarpPrograms().orElseThrow();
        try {
            var loaded = SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(), programs);
            programServices = withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM, loaded, programServices.initialization());
        } catch (SonicCdProgramLoadServices.Unsupported stopped) {
            programServices = new ProgramServicesState(stopped.completedPrefix(), programServices.initialization(),
                    programServices.environment(), programServices.completedCalls()); throw stopped;
        }
        if (CdProgramInitialization.selectStartBranch(programServices.initialization()) != CdProgramInitialization.Branch.GAME_INIT)
            throw pastLoadingBoundary(PastLoadBoundary.ENTRY);
        // IPX.RunMMD1311-1319:3504*16 zero bytes over[FF2000,FFFB00).
        // Current waterPalette[FFFA80,FFFB00) is fully covered; normal/target are outside.
        // This projection belongs to this admitted programme entry, before Start/InitVDP.
        var runMmdClearedWaterPalette = CdAssetBytes.copyOf(new byte[128]);
        var emptyArt = new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES], 0, false);
        var video = playerPresentation.initializeProgramVideo(
                com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.unknown(), emptyArt);
        programServices = withProgramCall(ProgramStartCall.INIT_VDP, programServices.loads(), programServices.initialization());
        programServices = withProgramCall(ProgramStartCall.INIT_CONTROLLERS, programServices.loads(),
                CdProgramInitialization.initializeControllers(programServices.initialization()));
        var old = c.environment(); var g = old.globals(); var oldMove = old.movement();
        var oldGate = oldMove.gates(); var mg = oldMove.globals();
        // IPX.RunMMD clears 2000..FB00. Mainvars/debug-probe bytes and 1600..2000 survive
        // until their genuine later ClearFlowers; controller/camera/physics fields do not.
        var environment = new CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
                new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0, 0, 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0, 0, 0, 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(oldGate.zoneAct(), oldGate.timeZone(),
                        oldGate.miniSonic(), 0, 0, oldGate.speedShoes(), 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(0, 0, 0, 0, 0, 0,
                        mg.debugAngle(), mg.debugAngleShift(), mg.debugQuadrant(), mg.debugFloorDistance())),
                new CdPlayerBody.Controls(0, 0, 0, 0, 0),
                new CdPlayerBody.Globals(g.levelFrames(), 0, g.timeAttackMode(), g.debugMode(),
                        g.debugCheat(), 0, g.invincible(), g.bossMusic()), old.dynamicSlots());
        var state = new CdPlayerBody.State(new com.openggf.game.soniccd.player.CdPlayerMotionState(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.AnimationCursor(0, 0, 0), new CdPlayerBody.Header(0, 0, 0, 0, 0),
                new CdPlayerBody.Timers(0, 0, 0, 0), new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]), 0),
                0, new CdPlayerBody.ObjectLinks(0, 0), 0);
        playerFrame = CdPlayerFrameRuntime.fromPastEntry(c.assets(), state, environment, video.playerArt(), c.clock());
        playerFrame.beginLevelStart(); playerFrame.clearLevelStartWarpDirection();
        var foreground = new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var background = new CdR11aBackgroundScroll.State(0, 0, 0, 0, 0, 0, CdAssetBytes.copyOf(new byte[8]),
                0, 0, 0, c.cloudRam(), CdAssetBytes.copyOf(new byte[512]), CdAssetBytes.copyOf(new byte[1024]), 0);
        // FF1926..FF194E is outside RunMMD; the first real C replaces these copies.
        var copies = c.copies();
        playerPresentation.bindPastSource(c.assets(), playerFrame, foreground, background,
                video.selection().backgroundPlane(), copies);
        pastLevel = level; pastLoadGeneration = Math.incrementExact(c.generation());
        pastLoading = new SonicCdPastLoadingVisit(this, c.assets(), playerFrame, playerPresentation,
                c.saved(), c.ring(), c.level(), c.sectionWord(), video, c.live(), c.contact(), c.score(),
                c.displayedVScrollLongBits(), c.savedObjectFlags(), c.retainedDev(), runMmdClearedWaterPalette, c.checkpointSaved());
        var p = c.program();
        levelProgram = new LevelProgramState(0, p.timeStones(), 0, 0, p.palFadeFlags() | 1,
                p.gameMode() | 0x80, 0, 0, LevelProgramPhase.PAST_LOADING, p.goodFutureFlags());
    }

    void registerPastRestoreProjection() {
        validatePastLoadingCut();
        var rewind = Objects.requireNonNull(playerMode.getRewindRegistry());
        rewind.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
        rewind.registerPostRestoreCallback(RESTORE_CALLBACK_KEY, playerPresentation::afterRestore);
    }

    void installAct2LoadingVisit() {
        validateAct2LoadRequest(stagedAct2Context);
        if (playerFrame != null || playerActor != null || openingObjects != null || pastLoading != null || act2Loading != null)
            throw new IllegalStateException("Retire Present before actual Act2 assembly");
        var c = stagedAct2;
        playerMode = SessionManager.getCurrentGameplayMode();
        if (!(playerMode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct2Level level)
                || level.assets() != c.data().terrain()) throw act2Boundary(Act2Boundary.ENTRY);
        var programs = requireRuntimeSource().timeWarpPrograms().orElseThrow();
        try {
            var loaded = SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(), programs);
            programServices = withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM, loaded, programServices.initialization());
        } catch (SonicCdProgramLoadServices.Unsupported stopped) {
            programServices = new ProgramServicesState(stopped.completedPrefix(), programServices.initialization(),
                    programServices.environment(), programServices.completedCalls()); throw stopped;
        }
        if (CdProgramInitialization.selectStartBranch(programServices.initialization()) != CdProgramInitialization.Branch.GAME_INIT)
            throw act2Boundary(Act2Boundary.ENTRY);
        // IPX.RunMMD1311-1319:3504*16 zero bytes over[FF2000,FFFB00).
        // Current waterPalette[FFFA80,FFFB00) is fully covered; normal/target are outside.
        // This projection belongs to this admitted programme entry, before Start/InitVDP.
        var runMmdClearedWaterPalette = CdAssetBytes.copyOf(new byte[128]);
        var emptyArt = new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES], 0, false);
        var video = playerPresentation.initializeProgramVideo(
                com.openggf.game.soniccd.resources.CdR12aCameraArtResidency.unknown(), emptyArt);
        programServices = withProgramCall(ProgramStartCall.INIT_VDP, programServices.loads(), programServices.initialization());
        programServices = withProgramCall(ProgramStartCall.INIT_CONTROLLERS, programServices.loads(),
                CdProgramInitialization.initializeControllers(programServices.initialization()));
        var old = c.environment(); var g = old.globals(); var oldMove = old.movement();
        var oldGate = oldMove.gates(); var mg = oldMove.globals();
        // IPX.RunMMD clears 2000..FB00. Mainvars/debug-probe bytes and 1600..2000 survive
        // until their genuine later ClearFlowers; controller/camera/physics fields do not.
        var environment = new CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
                new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0, 0, 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0, 0, 0, 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(oldGate.zoneAct(), oldGate.timeZone(),
                        oldGate.miniSonic(), 0, 0, oldGate.speedShoes(), 0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(0, 0, 0, 0, 0, 0,
                        mg.debugAngle(), mg.debugAngleShift(), mg.debugQuadrant(), mg.debugFloorDistance())),
                new CdPlayerBody.Controls(0, 0, 0, 0, 0),
                new CdPlayerBody.Globals(g.levelFrames(), 0, g.timeAttackMode(), g.debugMode(),
                        g.debugCheat(), 0, g.invincible(), g.bossMusic()), old.dynamicSlots());
        var state = new CdPlayerBody.State(new com.openggf.game.soniccd.player.CdPlayerMotionState(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.AnimationCursor(0, 0, 0), new CdPlayerBody.Header(0, 0, 0, 0, 0),
                new CdPlayerBody.Timers(0, 0, 0, 0), new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]), 0),
                0, new CdPlayerBody.ObjectLinks(0, 0), 0);
        playerFrame = CdPlayerFrameRuntime.fromAct2Entry(c.data().terrain(), c.data().player(), state, environment, video.playerArt(), c.clock());
        playerFrame.beginLevelStart();
        var foreground = new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var background = new CdR11aBackgroundScroll.State(0, 0, 0, 0, 0, 0, CdAssetBytes.copyOf(new byte[8]),
                0, 0, 0, c.cloudRam(), CdAssetBytes.copyOf(new byte[512]), CdAssetBytes.copyOf(new byte[1024]), 0);
        // FF1926..FF194E is outside RunMMD; the first real C replaces these copies.
        var copies = c.copies();
        playerPresentation.bindAct2Source(c.data().terrain(), c.data().player(), playerFrame, foreground, background,
                video.selection().backgroundPlane(), copies);
        act2Level = level; act2LoadGeneration = Math.incrementExact(c.generation());
        var retainedDev = c.mainvars().retainedDev();
        if (c.program().timeStones() == 0x7F && old.globals().timeAttackMode() == 0)
            retainedDev = new CdOpeningObjectRuntime.DevGlobals(1, retainedDev.projDestroyed());
        act2Loading = new SonicCdAct2LoadingVisit(this, c.data().terrain(), playerFrame, playerPresentation,
                c.data().player(), c.ring(), c.level(), c.sectionWord(), video, c.mainvars().live(), c.mainvars().contact(), c.mainvars().score(),
                c.displayedVScrollLongBits(), c.mainvars().savedFlags(), retainedDev, runMmdClearedWaterPalette, c.mainvars().demoDataIndexWord(), c.mainvars().checkpointSaved(), c.mainvars().timeWarpSaved());
        var p = c.program();
        levelProgram = new LevelProgramState(0, p.timeStones(), 0, 0, p.palFadeFlags() | 1,
                0x80, 0, 0, LevelProgramPhase.ACT2_LOADING, p.goodFutureFlags());
    }

    void registerAct2RestoreProjection() {
        validateAct2LoadingCut();
        var rewind = Objects.requireNonNull(playerMode.getRewindRegistry());
        rewind.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
        rewind.registerPostRestoreCallback(RESTORE_CALLBACK_KEY, playerPresentation::afterRestore);
    }

    // One module/session prerequisite value, captured once in the existing shared payload.
    // This admission choice is a named DEV ingress, never a native completion flag.
    private final boolean coldProgramStartAdmission;
    private ProgramServicesState programServices;
    private WorldSession programWorld;
    // The actual selected startup output exists only before the explicitly later DEV cut,
    // or as a terminal failure prefix. It is never a parallel live current-pattern bank.
    private SonicCdPlayerPresentation.PresentVideoInit programStartVideo;

    enum ProgramStartCall {
        LOAD_LEVEL_PCM, WRITE_VERSION_CACHE, WRITE_INIT_MARKER, INIT_VDP,
        INIT_CONTROLLERS, DECLARED_BEGIN_LEVEL_INGRESS, CLEAR_MAIN_VARIABLES
    }
    record ProgramServicesState(SonicCdProgramLoadServices.State loads,
                                CdProgramInitialization.State initialization,
                                CdProgramInitialization.ExecutionEnvironment environment,
                                List<ProgramStartCall> completedCalls) {
        ProgramServicesState {
            Objects.requireNonNull(loads); Objects.requireNonNull(initialization); Objects.requireNonNull(environment);
            completedCalls = List.copyOf(completedCalls);
        }
    }
    /** Immutable completed observations only; reading these values invokes no source operation. */
    ProgramServicesState programServicesState() { return programServices; }

    private static ProgramServicesState initialProgramServices(CdProgramInitialization.ExecutionEnvironment explicit) {
        var unknown = CdProgramInitialization.unknown();
        var initial = explicit == null ? unknown : new CdProgramInitialization.State(
                CdProgramInitialization.KnownBit.CLEAR, unknown.initMarker(), unknown.versionCache());
        return new ProgramServicesState(explicit == null ? SonicCdProgramLoadServices.unknown()
                        : SonicCdProgramLoadServices.developmentKnownAbsent(), initial,
                explicit == null ? new CdProgramInitialization.ExecutionEnvironment(java.util.Optional.empty()) : explicit,
                List.of());
    }

    private ProgramServicesState withProgramCall(ProgramStartCall call,
                                                SonicCdProgramLoadServices.State loads,
                                                CdProgramInitialization.State initialization) {
        var calls = new java.util.ArrayList<>(programServices.completedCalls()); calls.add(call);
        return new ProgramServicesState(loads, initialization, programServices.environment(), calls);
    }

    /**
     * Earlier explicitly selected DEV ingress. Actual immutable A media has already been
     * authenticated. Its extended cold prelude first projects the authenticated MDINIT
     * MAINVARS clear (MDINIT.Start25-30, also IPX.Start32-37), then adopts PCM before
     * level Start (SPX:745-773 at N1ecc5f3a). Initialization:56-80 performs its finite branch
     * and presentation outputs. Omitted MDINIT/BIOS/Title work is not declared complete.
     * No BIOS/CPU/VDP protocol, driver update, input, clock or gameplay executes here.
     * The later A BeginLevel remains an explicit constructed cut, not a retail boot claim.
     */
    private void prepareColdProgramStart(CdGameDataSource candidate) {
        var mode = SessionManager.getCurrentGameplayMode();
        var world = mode == null ? null : mode.getWorldSession();
        if (world == null || world.rootGameModule() != this || world.resolvedGameModule() != this
                || world.getDataSource() != candidate) {
            throw new IllegalStateException("Named program Start requires its actual source-bound World");
        }
        if (programWorld != null) {
            if (programWorld != world || stagedRestart == null || stagedRestartContext == null) {
                throw new IllegalStateException("Program Start cannot be retried or rebound into another World");
            }
            return; // Actual same-program death/checkpoint entry jumps to LevelStart, not Start.
        }
        programWorld = world; // Consume this new-program attempt before its first fallible source operation.
        // This actual named prelude is earlier than the old level Start cut. Authenticate
        // the retained original source before the first clear projection; no file instructions,
        // FM installation, hardware protocol, input, video or clock work execute here.
        requireColdMainVariableSource(candidate);
        var cleared = CdProgramInitialization.clearMainVariables(programServices.initialization());
        programServices = withProgramCall(ProgramStartCall.CLEAR_MAIN_VARIABLES, programServices.loads(), cleared);
        var required = candidate.timeWarpPrograms().orElseThrow(
                () -> new IllegalStateException("Named program Start requires its authenticated PCM source"));
        SonicCdProgramLoadServices.State loaded;
        try { loaded = SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(), required); }
        catch (SonicCdProgramLoadServices.Unsupported stopped) {
            programServices = new ProgramServicesState(stopped.completedPrefix(), programServices.initialization(),
                    programServices.environment(), programServices.completedCalls());
            throw stopped;
        }
        programServices = withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM, loaded, programServices.initialization());
        if (CdProgramInitialization.selectStartBranch(programServices.initialization()) == CdProgramInitialization.Branch.DO_INIT) {
            // The source's $200-byte unkBuffer clear has no admitted PP consumer before the
            // later declared DEV cut. Its dead stores require no general-purpose RAM bank.
            var region = CdProgramInitialization.requireExecutionRegion(programServices.environment());
            var cached = CdProgramInitialization.writeVersionCache(programServices.initialization(), region);
            programServices = withProgramCall(ProgramStartCall.WRITE_VERSION_CACHE, programServices.loads(), cached);
            var marked = CdProgramInitialization.writeInitMarker(programServices.initialization());
            programServices = withProgramCall(ProgramStartCall.WRITE_INIT_MARKER, programServices.loads(), marked);
        }
        var afterVideo = withProgramCall(ProgramStartCall.INIT_VDP, programServices.loads(), programServices.initialization());
        // RunMMD clears these represented CPU-art/comparison bytes before Start. The old
        // resident preimage is unobserved and unconditionally overwritten by InitVDP below.
        var clearedCpu = new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES], 0, false);
        var video = playerPresentation.initializeProgramVideo(CdR11aCameraArtResidency.unknown(), clearedCpu);
        programStartVideo = video; programServices = afterVideo;
        var controls = CdProgramInitialization.initializeControllers(programServices.initialization());
        programServices = withProgramCall(ProgramStartCall.INIT_CONTROLLERS, programServices.loads(), controls);
    }

    /** Existing asset factory authenticates all original MDINIT bytes/header; the Module checks its actual join. */
    private void requireColdMainVariableSource(CdGameDataSource candidate) {
        var mdInit = candidate.fmSoundAssets().orElseThrow(
                () -> new IllegalStateException("Cold MAINVARS clear requires its authenticated MDINIT source"));
        var stamp = mdInit.source(); var binding = candidate.assets().identity();
        if (stamp.variant() != CdReleaseVariant.USA || stamp.file() != CdNativeFile.MDINIT
                || binding.variant() != CdReleaseVariant.USA || candidate.release().variant() != CdReleaseVariant.USA
                || !stamp.wholeDiscSha256().equals(binding.observedWholeDiscSha256())
                || !stamp.wholeDiscSha256().equals(candidate.release().normalizedSha256())
                || !stamp.mdinitSha256().equals("abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e"))
            throw new IllegalArgumentException("Cold MAINVARS clear source differs from the actual original USA stage/disc");
    }

    private void validateProgramServicesState(ProgramServicesState value) {
        Objects.requireNonNull(value, "program service capture");
        if (!coldProgramStartAdmission) {
            if (!value.equals(initialProgramServices(null))) {
                throw new IllegalArgumentException("Legacy DEV capture cannot manufacture earlier program history");
            }
            return;
        }
        if (programWorld != playerMode.getWorldSession() || programStartVideo != null
                || !value.environment().equals(programServices.environment())) {
            throw new IllegalArgumentException("Program service capture belongs to another entry or World");
        }
        SonicCdProgramLoadServices.validateForCapture(value.loads(), requireRuntimeSource().timeWarpPrograms().orElseThrow());
        var init = value.initialization();
        if (init.controllerSetupBit() != CdProgramInitialization.KnownBit.SET
                || init.initMarker() != CdProgramInitialization.MarkerKnowledge.EQUALS_INIT
                || init.versionCache().knownBits() != 0xFF
                || init.palClearFlagBit0() != CdProgramInitialization.KnownBit.CLEAR
                || value.completedCalls().isEmpty() || value.completedCalls().getFirst() != ProgramStartCall.CLEAR_MAIN_VARIABLES
                || value.completedCalls().stream().filter(call -> call == ProgramStartCall.CLEAR_MAIN_VARIABLES).count() != 1) {
            throw new IllegalArgumentException("A healthy named DEV cut requires its actually completed Start facts");
        }
    }

    private CdWarpAssets warpAssets;
    private CdWarpScene warpScene;
    private CdWarpScene.State warpState;
    private List<ProgramSound> programSounds = List.of();

    enum LevelProgramPhase { LEVEL, WHITE_FADE, RETURNED, WARP, PAST_PENDING, PAST_LOADING, OLD_EXIT_PRELUDE, OLD_BLACK_FADE, OLD_CLEAR_WAIT, OLD_RETURNED, OLD_IPX_RETURNED, ACT2_LOADING, ACT2_PAST_LOADING }
    /** Completed source requests only: no sound-driver execution or old Frame ledger mutation. */
    enum ProgramSound {
        STOP_FM(0xE0), WARP_DIRECT_FM_D8(0xD8);
        private final int nativeId;
        ProgramSound(int nativeId) { this.nativeId = nativeId; }
        int nativeId() { return nativeId; }
        int driverQueue() { return 2; }
    }
    java.util.Optional<CdWarpScene.State> warpSceneState() { return java.util.Optional.ofNullable(warpState); }
    List<ProgramSound> programSoundRequests() { return programSounds; }
    CdWarpAssets warpSceneAssets() { return Objects.requireNonNull(warpAssets, "No authenticated WARP binding"); }
    boolean warpProgramSelected() {
        var phase = levelProgramState().phase();
        return phase == LevelProgramPhase.WARP || phase == LevelProgramPhase.PAST_PENDING;
    }

    /**
     * Only the native programme globals not already owned by Frame/Runtime, plus FadeToWhite's
     * completed waits. The existing shared opening payload captures this immutable value once.
     * Fresh .BeginLevel is an explicit semantic ingress: IPX.RunMMD/NewGame and Main:328-329
     * justify these postconditions; the omitted Title/IPX/load prelude has not been executed.
     */
    record LevelProgramState(int demoMode, int timeStones, int specialStage, int usePlayer2,
                             int palFadeFlags, int gameMode, int levelStarted, int whiteFadeSteps,
                             LevelProgramPhase phase, OptionalInt goodFutureFlags) {
        LevelProgramState {
            Objects.requireNonNull(goodFutureFlags, "explicit good-future flag knowledge");
            if (goodFutureFlags.isPresent() && (goodFutureFlags.getAsInt() & ~0xFF) != 0)
                throw new IllegalArgumentException("goodFutureFlags must be a known BYTE or unknown");
            Objects.requireNonNull(phase, "native level programme phase");
            if ((demoMode & ~0xFFFF) != 0 || (timeStones & ~0xFF) != 0
                    || (specialStage & ~0xFF) != 0 || (usePlayer2 & ~0xFF) != 0
                    || (palFadeFlags & ~0xFF) != 0 || (gameMode & ~0xFF) != 0
                    || (levelStarted & ~0xFF) != 0 || whiteFadeSteps < 0 || whiteFadeSteps > 22
                    || (phase == LevelProgramPhase.LEVEL && whiteFadeSteps != 0)
                    || (phase == LevelProgramPhase.RETURNED && whiteFadeSteps != 22)
                    || ((phase == LevelProgramPhase.WARP || phase == LevelProgramPhase.PAST_PENDING)
                            && whiteFadeSteps != 0 && whiteFadeSteps != 22)) {
                throw new IllegalArgumentException("Incomplete native level programme value");
            }
        }

        /** Compatibility/value ingress: an omitted flag byte is explicitly unknown, never zero. */
        LevelProgramState(int demoMode, int timeStones, int specialStage, int usePlayer2,
                          int palFadeFlags, int gameMode, int levelStarted, int whiteFadeSteps,
                          LevelProgramPhase phase) {
            this(demoMode, timeStones, specialStage, usePlayer2, palFadeFlags, gameMode,
                    levelStarted, whiteFadeSteps, phase, OptionalInt.empty());
        }

        private static LevelProgramState freshBeginLevel() {
            return new LevelProgramState(0, 0, 0, 0, 1, 0, 1, 0, LevelProgramPhase.LEVEL, OptionalInt.empty());
        }
    }

    /** Pure value observation; no entry, fade, capture or native service is performed. */
    LevelProgramState levelProgramState() {
        if (act2PastLoading != null) {
            var p = act2PastLoading.state().program();
            return new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),
                    p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),act2PastLoading.state().initialWhiteSteps(),
                    LevelProgramPhase.ACT2_PAST_LOADING,levelProgram.goodFutureFlags());
        }
        return Objects.requireNonNull(levelProgram, "No admitted native opening programme");
    }

    public enum RestartBoundary { GAME_OVER_EXIT, RESTART_PATH, WARP_LEVEL_START, WARP_PROGRAM_START, R11B_LOAD }
    public static final class UnsupportedRestart extends UnsupportedOperationException {
        private final RestartBoundary boundary;
        private UnsupportedRestart(RestartBoundary boundary) { super(boundary.name()); this.boundary = boundary; }
        public RestartBoundary boundary() { return boundary; }
    }

    /**
     * Explicit immutable buffer ingress, not another runtime scroll owner. Deformation has no
     * LevelStart clear; HScroll is cleared by ClearScreen (Functions (General).asm:220-226,
     * called at Main:225). Capture reads both complete buffers, then restartCarry applies that native clear.
     * Fresh zero deformation is explicit DEV ingress, not inferred native-cold RAM.
     */
    record BackgroundBuffers(CdAssetBytes deformationRam, CdAssetBytes hscrollRam) {
        BackgroundBuffers {
            Objects.requireNonNull(deformationRam, "complete deformation RAM");
            Objects.requireNonNull(hscrollRam, "complete HScroll RAM");
            if (deformationRam.length() != CdR11aBackgroundScroll.DEFORMATION_RAM_BYTES
                    || hscrollRam.length() != CdR11aBackgroundScroll.HSCROLL_RAM_BYTES) {
                throw new IllegalArgumentException("Background ingress requires all 512 deformation and 1024 HScroll bytes");
            }
        }
    }

    /** Immutable handoff between visits; never part of a captured Frame or a second global store. */
    record RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                        CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                        CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                        BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                        CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                        CdTimeWarpSaveData.State timeWarpSavedState, LevelProgramState program) {
        RestartCarry {
            Objects.requireNonNull(ring); Objects.requireNonNull(contact); Objects.requireNonNull(score);
            Objects.requireNonNull(level); Objects.requireNonNull(savedObjectFlags);
            Objects.requireNonNull(player); Objects.requireNonNull(powerupArt); Objects.requireNonNull(background);
            Objects.requireNonNull(cameraArt, "explicit current camera art carry");
            Objects.requireNonNull(checkpointState, "explicit current checkpoint live/saved owner");
            Objects.requireNonNull(timeWarpSavedState, "explicit distinct time-warp saved value");
            if (savedObjectFlags.length() != CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
                throw new IllegalArgumentException("Complete native saved-object bank required");
            }
        }

        /** Legacy pure policy ingress carries no knowledge of the native programme globals. */
        RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                     CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                     CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                     BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                     CdTimeWarpSaveData.State timeWarpSavedState) {
            this(ring, contact, score, level, savedObjectFlags, player, powerupArt, background, cameraArt,
                    checkpointState, timeWarpSavedState, null);
        }

        /** Legacy explicit ingress has no represented time-warp saved bytes. */
        RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                     CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                     CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                     BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState) {
            this(ring, contact, score, level, savedObjectFlags, player, powerupArt, background, cameraArt,
                    checkpointState, CdTimeWarpSaveData.State.unknown());
        }
    }

    /**
     * LevelStart .NoReset/.NormalLoad (Level/Main.asm:37-53,154-216,278-329).
     * Present checkpoint0 retains its existing policy. The checkpoint path carries its
     * actual ring/checkpoint/spawn/HUD/section values until the new visit's native writers;
     * saved payload is not read here. Missing title-loaded bit stops before retirement.
     * This pure policy uses explicit values: score/lives/history/Sonic art and powerup VRAM carry; ClearFlowers
     * clears powerup CPU only. DEV omits fade/title/HUD/music/PLC rows, uses seed0 in install, carries rawHeld as
     * the last represented fade-time pad read, and keeps HUD flags/control lock at the existing DEV values.
     */
    static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level, CdPlayerBody.ContactGlobals contact,
                                     CdOpeningObjectRuntime.ScoreGlobals score, CdAssetBytes savedFlags,
                                     CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState) {
        return restartCarry(ring, level, contact, score, savedFlags, environment, actor, art, powerupArt,
                background, cameraArt, checkpointState, CdTimeWarpSaveData.State.unknown());
    }

    /** Actual visit handoff preserves the distinct saved value without admitting a warp load. */
    static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level, CdPlayerBody.ContactGlobals contact,
                                     CdOpeningObjectRuntime.ScoreGlobals score, CdAssetBytes savedFlags,
                                     CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                                     CdTimeWarpSaveData.State timeWarpSavedState) {
        return restartCarry(ring, level, contact, score, savedFlags, environment, actor, art, powerupArt,
                background, cameraArt, checkpointState, timeWarpSavedState, null);
    }

    /** Actual module handoff borrows its current immutable programme value; pure callers remain unknown. */
    private static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level,
                                     CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                                     CdAssetBytes savedFlags, CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                                     CdTimeWarpSaveData.State timeWarpSavedState, LevelProgramState program) {
        Objects.requireNonNull(ring); Objects.requireNonNull(level); Objects.requireNonNull(contact);
        Objects.requireNonNull(score); Objects.requireNonNull(savedFlags); Objects.requireNonNull(environment);
        Objects.requireNonNull(actor); Objects.requireNonNull(art); Objects.requireNonNull(powerupArt);
        Objects.requireNonNull(background, "explicit carried background buffers");
        Objects.requireNonNull(cameraArt, "explicit carried current camera art and Nemesis table");
        Objects.requireNonNull(checkpointState, "explicit carried checkpoint live/saved owner");
        Objects.requireNonNull(timeWarpSavedState, "explicit carried time-warp saved value");
        if (savedFlags.length() != CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Complete native saved-object bank required");
        }
        if (ring.lives() == 0) throw new UnsupportedRestart(RestartBoundary.GAME_OVER_EXIT);
        var gates = environment.movement().gates();
        boolean start = level.checkpoint() == 0 && level.spawnMode() == 0;
        boolean checkpoint = level.checkpoint() != 0 && level.spawnMode() == 1
                && (level.plcLoadFlags() & 3) == 3;
        // Main:271-275's title-omitted HIGH clear cannot follow a checkpoint LoadSectionArt
        // and be silently reordered. Only the actually title-loaded skip branch is admitted.
        if (level.levelRestart() != 1 || !(start || checkpoint)
                || (level.plcLoadFlags() & 1) == 0 || level.timeOver() != 0
                || (level.powerup() != 0 && level.powerup() != 8)
                || gates.zoneAct() != 0 || gates.timeZone() != 1 || environment.globals().timeAttackMode() != 0) {
            throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
        }
        var debug = environment.movement().globals();
        var player = new CdPlayerFrameRuntime.RestartIngress(actor.history().bytes(), art.cpuBuffer(), art.residentBank(),
                debug.debugAngle(), debug.debugAngleShift(), debug.debugQuadrant(), debug.debugFloorDistance(),
                environment.globals().debugCheat(), environment.controls().rawHeld());
        return new RestartCarry(checkpoint ? ring : new CdRingGlobals(0, ring.lives(), 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0),
                checkpoint ? new CdLevelGlobals(level.levelRestart(), level.updateHudTime(), level.checkpoint(),
                        level.spawnMode(), level.plcLoadFlags() | 3, 0, 2, 0, 0)
                        : new CdLevelGlobals(0, 0, 0, 0, level.plcLoadFlags() | 3, 0, 2, 0, 0), savedFlags, player,
                new CdPowerupArtRuntime.State(CdAssetBytes.copyOf(new byte[0x480]), powerupArt.residentBank()),
                // LevelStart:225 -> ClearScreen.ClearHScroll:220-226. The extra DBF long lands in
                // the player header. The collapsed cold builder keeps cleared flags/tile, then represents
                // LoadPlayer (Main:438-443) with slotId1; neither load-scroll owner reads that ID.
                new BackgroundBuffers(background.deformationRam(),
                        CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.HSCROLL_RAM_BYTES])),
                // Present/checkpoint0 death clears title-loaded bit (Sonic/Main.asm:2468-2472).
                // The represented title-omitted DEV rebuild applies Main:275's addressed HIGH clear.
                // Native immediate PLC3/Std1/title prehistory remains omitted: this table is the
                // LAST REPRESENTED table, not a fabricated post-load Ring code table. VRAM carries.
                new CdOpeningObjectRuntime.CameraArtRestartIngress(cameraArt.residency(),
                        cameraArt.codeTable512(), checkpoint ? cameraArt.sectionWord() : cameraArt.sectionWord() & 0xFF),
                checkpointState, timeWarpSavedState, program);
    }

    boolean restartStaged() { return stagedRestart != null; }
    boolean checkpointRestartStaged() {
        return openingObjectsAdmission && stagedRestart != null
                && stagedRestart.level().checkpoint() != 0 && stagedRestart.level().spawnMode() == 1;
    }
    boolean ownsRestartContext(LevelLoadContext context) {
        return openingObjectsAdmission && stagedRestart != null && context == stagedRestartContext;
    }

    /**
     * Called at the NEXT beforeFrame, after the restart-cut token and old-epoch recording have finished.
     * All preflight precedes retirement. The ordinary load owner emits the one boundary and generation;
     * no generic death/fade request, per-test reset or extra rewind reset is used.
     */
    void restartOpeningWorld() {
        if (!openingObjectsAdmission || stagedRestart != null) {
            throw new IllegalStateException("CD restart requires its unstaged opening owner");
        }
        try {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
            var program = levelProgramState();
            if (program.phase() == LevelProgramPhase.RETURNED) {
                beginWarpProgram();
                return;
            }
            if (program.phase() == LevelProgramPhase.PAST_PENDING) {
                // IPX.RunLevel:557-573 masks the actual marked period. R11SubCmds:650 selects
                // command1/R11B for Past0, never a checkpoint or an R11A flag-only reload.
                var retained = requireRuntimeSource().timeWarpPrograms().orElseThrow(
                        () -> new UnsupportedRestart(RestartBoundary.WARP_PROGRAM_START));
                if ((playerFrame.environment().movement().gates().timeZone() & 0x7F) != 0
                        || retained.r11b().fileBytes() != 262144) {
                    throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
                }
                loadActualPastWorld();
                return;
            }
            if (program.phase() != LevelProgramPhase.LEVEL) {
                throw new IllegalStateException("Old LevelStart cannot be entered twice");
            }
            var objects = openingObjects.captureClosedCut();
            var shared = playerFrame.captureShared(); var actor = playerFrame.captureActor();
            var gates = shared.environment().movement().gates();
            if ((objects.levelGlobals().levelRestart() & 0xFF00) == 0x0100
                    && objects.levelGlobals().spawnMode() == 2
                    && gates.timeZone() >= 0x80 && gates.timeZone() <= 0x82) {
                beginOldLevelStart(program, shared);
                return;
            }
            // No admitted ordinary writer changes these DEV globals yet. A future native owner
            // must extend the actual carry rather than defaulting retained good-future metadata.
            if (!openingObjects.devGlobals().equals(new CdOpeningObjectRuntime.DevGlobals(0, 0))) {
                throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
            }
            var carry = restartCarry(objects.ringGlobals(), objects.levelGlobals(), objects.contactGlobals(),
                    objects.scoreGlobals(), objects.placement().savedFlags(), shared.environment(), actor.state(),
                    shared.artState(), objects.powerupArtState(), playerPresentation.captureBackgroundBuffers(),
                    new CdOpeningObjectRuntime.CameraArtRestartIngress(objects.sectionArtState().residency(),
                            objects.sectionArtState().codeTable(), objects.sectionArtState().sectionWord()),
                    objects.checkpointState(), openingObjects.timeWarpSavedState(), program);
            var manager = playerMode.getLevelManager();
            stagedRestart = carry;
            stagedRestartContext = SonicCdPlayerInitProfile.openingObjectsRestartContext();
            try { manager.loadLevel(0, LevelLoadMode.FULL, stagedRestartContext); }
            catch (IOException failure) { throw new UncheckedIOException("Cannot reconstruct the CD restart visit", failure); }
        } catch (RuntimeException | Error failure) {
            closePlayerCycleAfterFailure(failure);
            throw failure;
        } finally {
            stagedRestart = null;
            stagedRestartContext = null;
        }
    }

    /**
     * Native LevelStart:19-81 at N1ecc5f3a. This bounded marked-period act0 entry owns the
     * scalar prefix and joins Runtime's existing pause/PLC/queue authorities. The actual old
     * BSET bit selects the white fade; special-stage and first-load paths remain typed stops.
     */
    private void beginOldLevelStart(LevelProgramState program, CdPlayerFrameRuntime.SharedState shared) {
        if (program.specialStage() != 0 || (program.palFadeFlags() & 1) == 0
                || (openingObjects.levelGlobals().plcLoadFlags() & 1) == 0) {
            throw new UnsupportedRestart(RestartBoundary.WARP_LEVEL_START);
        }
        boolean forceGoodFuture = program.timeStones() == 0x7F
                && shared.environment().globals().timeAttackMode() == 0;
        // Main:20,29,31,56. Borrow Frame's actual time attack and pause and Runtime's
        // actual goodFuture/plcLoadFlags; no parallel native-global bank is introduced.
        levelProgram = new LevelProgramState(0, program.timeStones(), program.specialStage(), 0,
                program.palFadeFlags(), program.gameMode() | 0x80, 0, 0, LevelProgramPhase.LEVEL, program.goodFutureFlags());
        openingObjects.beginWarpLevelStart(forceGoodFuture); // Main:35,37,57, including actual ClearPLCs.
        // Main:64 tests the OLD bit, proven set above, and preserves every other incoming bit.
        levelProgram = new LevelProgramState(0, program.timeStones(), program.specialStage(), 0,
                program.palFadeFlags() | 1, program.gameMode() | 0x80, 0, 0, LevelProgramPhase.WHITE_FADE, program.goodFutureFlags());
    }

    /** One admitted selector12 pulse. FadeToWhite's DBF21 owns exactly22 waits, never a route index. */
    void serviceOldLevelStartWhiteFade(int rawHeldByte) {
        try {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
            var program = levelProgramState();
            if (program.phase() != LevelProgramPhase.WHITE_FADE || program.whiteFadeSteps() >= 22) {
                throw new IllegalStateException("No old-LevelStart white-fade wait is admitted");
            }
            openingObjects.serviceLevelStartWhiteFade(rawHeldByte);
            int completed = program.whiteFadeSteps() + 1;
            levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                    program.usePlayer2(), program.palFadeFlags(), program.gameMode(), program.levelStarted(),
                    completed, LevelProgramPhase.WHITE_FADE, program.goodFutureFlags());
            if (completed == 22) {
                // Main:69 precedes direction clear:72, then complete restart WORD clear:75.
                levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                        program.usePlayer2(), program.palFadeFlags() & ~1, program.gameMode(), program.levelStarted(),
                        completed, LevelProgramPhase.WHITE_FADE, program.goodFutureFlags());
                openingObjects.finishWarpLevelStart();
                levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                        program.usePlayer2(), program.palFadeFlags() & ~1, program.gameMode(), program.levelStarted(),
                        completed, LevelProgramPhase.RETURNED, program.goodFutureFlags());
            }
        } catch (RuntimeException | Error failure) {
            closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    /** IPX.RunMMD:1372-1387: stop FM, one blank semantic pulse, then bounded WARP setup. */
    private void beginWarpProgram() {
        requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
        var program = levelProgramState();
        if (program.phase() != LevelProgramPhase.RETURNED || warpState != null || !programSounds.isEmpty())
            throw new IllegalStateException("WARP entry requires the actual completed old programme");
        startWarpProgram(program);
    }

    /** Actual RunR12 caller after the completed old R12A return. No old Frame pulse. */
    private void beginAct2WarpProgram() {
        validateAct2LoadingCut(); act2Loading.requireReturnedProgrammeIdle();
        var program = levelProgramState();
        if (program.phase() != LevelProgramPhase.OLD_RETURNED || warpState != null || !programSounds.isEmpty())
            throw new IllegalStateException("Act2 WARP requires its actual one-time MMD return");
        var old = act2Loading.observation();
        // RunMMD1373-1387 has returned/stopped FM and blanked before RunLevel's lives/marker gates.
        programSounds = List.of(ProgramSound.STOP_FM);
        if (old.ring().lives() == 0 || (playerFrame.environment().movement().gates().timeZone() & 0x80) == 0)
            throw act2Boundary(Act2Boundary.OLD_EXIT_LEVEL_OVER);
        startWarpProgram(program);
    }

    /** Shared authenticated scene binding; each actual caller establishes its own source authority. */
    private void startWarpProgram(LevelProgramState program) {
        if (warpScene == null) {
            var retained = requireRuntimeSource().timeWarpPrograms().orElseThrow(
                    () -> new UnsupportedRestart(RestartBoundary.WARP_PROGRAM_START));
            try {
                var assets = CdWarpAssets.fromPrograms(retained);
                var scene = new CdWarpScene(assets);
                // Explicit semantic setup cut: native Start cleared its own counter; synchronous
                // asset/setup work contributes no represented pulse. This is not a measured console
                // IRQ phase or a copy of the suspended level's V-int/read-controller state.
                var initial = scene.initial(0);
                scene.validate(initial);
                warpAssets = assets; warpScene = scene;
            } catch (IOException failure) { throw new UncheckedIOException("Cannot bind original WARP programme", failure); }
        }
        var initial = warpScene.initial(0);
        var sounds = List.of(ProgramSound.STOP_FM);
        warpState = initial; programSounds = sounds;
        levelProgram = withProgramPhase(program, LevelProgramPhase.WARP);
        // No old Frame/ObjectManager V-int is serviced. Its owners remain inactive and capturable.
    }

    /** One current controller HOLD row; neither draw nor restore calls this source owner. */
    void serviceWarpProgram(int rawHeldByte) {
        playerPresentation.requirePastRenderIdle(); // Rejected callback reentry never closes a healthy Frame.
        try {
            validateWarpProgrammeOwner();
            var program = levelProgramState();
            if (program.phase() != LevelProgramPhase.WARP || warpScene == null || warpState == null)
                throw new IllegalStateException("No executing WARP programme");
            if (warpState.phase() == CdWarpScene.Phase.RETURNED) {
                var ready = java.util.List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8, ProgramSound.STOP_FM);
                // WARP's own RunMMD return tail owns a second blank pulse/FMC_STOP. The final
                // scene remains inspectable; the following beforeFrame owns the actual Past load.
                programSounds = ready;
                levelProgram = withProgramPhase(program, LevelProgramPhase.PAST_PENDING);
                return;
            }
            var before = warpState;
            var result = warpScene.step(before, rawHeldByte);
            var expected = before.phase() == CdWarpScene.Phase.PREPARE
                    ? List.of(CdWarpScene.SoundRequest.FM_D8) : List.<CdWarpScene.SoundRequest>of();
            if (!result.sounds().equals(expected)) throw new IllegalStateException("WARP sound request left its actual source cut");
            var ready = expected.isEmpty() ? programSounds : List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8);
            warpScene.validate(result.state());
            warpState = result.state(); programSounds = ready;
        } catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
    }

    /** Read-only actual source/World/closed-cut admission, also used during owned scene rendering. */
    void validateWarpProgrammeOwner() {
        if (act2Loading != null) {
            validateAct2LoadingCut(); act2Loading.requireReturnedProgrammeIdle();
        } else {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
        }
        if (!warpProgramSelected()) throw new IllegalStateException("No selected actual WARP programme");
        validateWarpProgramCut(levelProgramState(), warpState, programSounds);
    }

    private static LevelProgramState withProgramPhase(LevelProgramState p, LevelProgramPhase phase) {
        return new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), phase, p.goodFutureFlags());
    }

    private void validateWarpProgramCut(LevelProgramState program, CdWarpScene.State scene, List<ProgramSound> sounds) {
        Objects.requireNonNull(sounds, "programme sound ledger");
        boolean selected = program.phase() == LevelProgramPhase.WARP || program.phase() == LevelProgramPhase.PAST_PENDING;
        if (!selected) {
            if (scene != null || !sounds.isEmpty()) throw new IllegalArgumentException("Level capture carries foreign programme state");
            return;
        }
        if (warpScene == null || warpAssets == null || scene == null) throw new IllegalArgumentException("Missing actual WARP binding");
        warpScene.validate(scene);
        boolean returned = program.phase() == LevelProgramPhase.PAST_PENDING;
        if (returned && scene.phase() != CdWarpScene.Phase.RETURNED) throw new IllegalArgumentException("WARP did not return");
        var expected = returned ? List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8, ProgramSound.STOP_FM)
                : scene.phase() == CdWarpScene.Phase.DELAY || scene.phase() == CdWarpScene.Phase.PREPARE
                        ? List.of(ProgramSound.STOP_FM) : List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8);
        if (!sounds.equals(expected)) throw new IllegalArgumentException("Programme requests disagree with the selected native cut");
    }

    // This stable module adapter owns shared state only. Actor Body/history/display,
    // sounds and the actual sprite packet live exclusively in the actor extra.
    private final RewindSnapshottable<CdPlayerFrameRuntime.SharedState> sharedPlayerAdapter = new RewindSnapshottable<>() {
        @Override public String key() { return SHARED_REWIND_KEY; }
        @Override public CdPlayerFrameRuntime.SharedState capture() {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            return playerFrame.captureShared();
        }
        @Override public void restore(CdPlayerFrameRuntime.SharedState snapshot) {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            playerFrame.validateSharedRestore(snapshot);
            playerFrame.restoreShared(snapshot);
        }
    };

    // One shared opening payload. Body/history/display remain in actor Extra;
    // FG/BG accumulator/camera remain together in their existing scroll adapter. The two
    // immutable render-scroll cuts belong here, once, beside the CPU/display SAT.
    private record OpeningSharedState(CdPlayerFrameRuntime.SharedState frame,
                                      CdOpeningObjectRuntime.Snapshot objects,
                                      SonicCdPlayerPresentation.ScrollPublication scroll,
                                      LevelProgramState program, CdWarpScene.State warp,
                                      List<ProgramSound> sounds, ProgramServicesState services) {
        private OpeningSharedState { sounds = List.copyOf(sounds); Objects.requireNonNull(services); }
    }
    private final NativeRewindRestoreGuard<Object> sharedOpeningAdapter = new NativeRewindRestoreGuard<>() {
        @Override public String key() { return OPENING_SHARED_REWIND_KEY; }
        @Override public Object capture() {
            if (act2PastLoading != null) {
                validateAct2PastLoadingCut(); validateProgramServicesState(programServices);
                return new Act2PastShared(act2PastLoading.capture(),programServices,levelProgram.goodFutureFlags());
            }
            if (act2Loading != null) {
                validateAct2LoadingCut(); validateProgramServicesState(programServices);
                return new Act2OpeningShared(act2Loading.capture(), programServices, levelProgramState(), warpState, programSounds);
            }
            if (pastLoading != null) {
                validatePastLoadingCut(); validateProgramServicesState(programServices);
                return new PastOpeningShared(pastLoading.capture(), programServices, levelProgramState());
            }
            requireTerrainPlayerReady();
            requireOpeningClosedCut();
            playerActor.requireCompletedNativeFrame();
            validateProgramServicesState(programServices);
            return new OpeningSharedState(playerFrame.captureShared(), openingObjects.captureClosedCut(),
                    playerPresentation.captureScrollPublication(), levelProgramState(), warpState, programSounds, programServices);
        }
        @Override public void validateRestore(Object value) {
            if (act2PastLoading != null) {
                if (!(value instanceof Act2PastShared past)) throw new IllegalArgumentException("Foreign programme epoch in R12B");
                validateAct2PastLoadingCut(); act2PastLoading.validateRestore(past.loading()); validateProgramServicesState(past.services());
                if (!past.goodFutureFlags().equals(levelProgram.goodFutureFlags())) throw new IllegalArgumentException("R12B preserved programme knowledge changed");
                return;
            }
            if (act2Loading != null) {
                if (!(value instanceof Act2OpeningShared act2)) throw new IllegalArgumentException("Foreign programme epoch in Act2");
                act2Loading.validateRestore(act2.loading(), act2.program()); validateProgramServicesState(act2.services());
                validateWarpProgramCut(act2.program(), act2.warp(), act2.sounds());
                return;
            }
            if (pastLoading != null) {
                if (!(value instanceof PastOpeningShared past)) throw new IllegalArgumentException("Prior programme snapshot in Past epoch");
                pastLoading.validateRestore(past.loading(), past.program()); validateProgramServicesState(past.services());
                if (!pastProgrammePhase(past.program().phase())) throw new IllegalArgumentException("Foreign Past programme phase");
                return;
            }
            if (!(value instanceof OpeningSharedState snapshot)) throw new IllegalArgumentException("Past snapshot in Present epoch");
            requireTerrainPlayerReady();
            requireOpeningClosedCut();
            playerActor.requireCompletedNativeFrame();
            playerFrame.captureShared(); // Pure pending-input/entitlement guard before runtime preflight.
            Objects.requireNonNull(snapshot, "opening shared capture");
            // All preflights are pure and run BEFORE any partition changes.
            playerFrame.validateSharedRestore(snapshot.frame());
            openingObjects.validateRestore(snapshot.objects());
            playerPresentation.validateScrollPublication(snapshot.scroll());
            validateLevelProgramCut(snapshot.program(), snapshot.frame(), snapshot.objects());
            validateWarpProgramCut(snapshot.program(), snapshot.warp(), snapshot.sounds());
            validateProgramServicesState(snapshot.services());
        }
        @Override public void restore(Object value) {
            validateRestore(value); // Keep direct adapter restore guarded as well as composite restore.
            if (value instanceof Act2PastShared past) {
                try { act2PastLoading.restore(past.loading()); programServices = past.services(); }
                catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
                return;
            }
            if (value instanceof Act2OpeningShared act2) {
                try {
                    levelProgram = act2.program(); act2Loading.restore(act2.loading(), act2.program()); programServices = act2.services();
                    warpState = act2.warp(); programSounds = act2.sounds();
                } catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
                return;
            }
            if (value instanceof PastOpeningShared past) {
                try {
                    // The outer preflight validated the whole pair before this first adoption.
                    // Restore the programme phase before its flags/scroll projections select admission.
                    levelProgram = past.program();
                    pastLoading.restore(past.loading(), past.program()); programServices = past.services();
                }
                catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
                return;
            }
            var snapshot = (OpeningSharedState) value;
            try {
                playerFrame.restoreShared(snapshot.frame());
                openingObjects.restore(snapshot.objects());
                playerPresentation.restoreScrollPublication(snapshot.scroll());
                levelProgram = snapshot.program(); // Exact immutable adoption; no entry, fade or scene replay.
                warpState = snapshot.warp(); programSounds = snapshot.sounds();
                programServices = snapshot.services(); // Exact value adoption; never replay Start/load requests.
            } catch (RuntimeException | Error failure) {
                closePlayerCycleAfterFailure(failure);
                throw failure;
            }
        }
    };

    private static void validateLevelProgramCut(LevelProgramState program,
                                               CdPlayerFrameRuntime.SharedState frame,
                                               CdOpeningObjectRuntime.Snapshot objects) {
        Objects.requireNonNull(program, "native opening programme capture");
        if (program.phase() == LevelProgramPhase.LEVEL) return;
        var level = objects.levelGlobals();
        var gates = frame.environment().movement().gates();
        boolean returned = program.phase() == LevelProgramPhase.RETURNED
                || program.phase() == LevelProgramPhase.WARP || program.phase() == LevelProgramPhase.PAST_PENDING;
        if (program.demoMode() != 0 || program.specialStage() != 0 || program.usePlayer2() != 0
                || program.levelStarted() != 0 || (program.gameMode() & 0x80) == 0
                || frame.paused() || level.spawnMode() != 2 || gates.zoneAct() != 0
                || gates.timeZone() < 0x80 || gates.timeZone() > 0x82
                || (returned ? program.whiteFadeSteps() != 22 || (program.palFadeFlags() & 1) != 0 || level.levelRestart() != 0
                        || gates.timeWarpDirection() != 0
                        : (program.palFadeFlags() & 1) == 0 || program.whiteFadeSteps() >= 22
                                || (level.levelRestart() & 0xFF00) != 0x0100)) {
            throw new IllegalArgumentException("Native programme capture is inconsistent with its existing owners");
        }
    }

    /** The stock/default entry retains its STATIC/HOLD/no-actor contract. */
    public SonicCdGameModule() { this(false, false); }

    private SonicCdGameModule(boolean playerAdmission, boolean openingObjectsAdmission) {
        this(playerAdmission, openingObjectsAdmission, null);
    }

    private SonicCdGameModule(boolean playerAdmission, boolean openingObjectsAdmission,
                              CdProgramInitialization.ExecutionEnvironment explicitEnvironment) {
        coldProgramStartAdmission = explicitEnvironment != null;
        programServices = initialProgramServices(explicitEnvironment);
        this.playerAdmission = playerAdmission;
        this.openingObjectsAdmission = openingObjectsAdmission;
        rules = playerAdmission ? new CdPlayerEngineRules() : new SonicCdStaticRules();
        playerPresentation = playerAdmission ? new SonicCdPlayerPresentation(this) : null;
        playerProfile = playerAdmission ? new SonicCdPlayerInitProfile(this, profile) : null;
        characters = playerAdmission ? PlayableCharacterRegistry.empty().register(CharacterKey.SONIC,
                new CharacterDefinition(CharacterKey.SONIC, openingObjectsAdmission
                        ? "Sonic (CD opening objects development)" : "Sonic (CD terrain development)",
                        this::constructTerrainPlayer, null, PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                        code -> { throw SonicCd.unsupported("generic playable-art loading; CD owns its complete resident bank"); }))
                : PlayableCharacterRegistry.empty();
    }

    /** Explicit private development entry; does not change the stock registry or menu. */
    public static SonicCdGameModule terrainOnlyDevelopmentPlayer() { return new SonicCdGameModule(true, false); }

    /** Private USA R11A DEV ingress; stock registry/configuration/menu remain unchanged. */
    public static SonicCdGameModule openingObjectsDevelopmentPlayer() { return new SonicCdGameModule(true, true); }

    /** Earlier named DEV inputs; the actual load executes the bounded MDINIT-clear/level-Start prelude, not a full boot. */
    static SonicCdGameModule openingObjectsDevelopmentPlayerFromColdProgramStart(
            CdProgramInitialization.ExecutionEnvironment explicitEnvironment) {
        return new SonicCdGameModule(true, true, Objects.requireNonNull(explicitEnvironment, "explicit execution environment"));
    }

    @Override public String getIdentifier() {
        return openingObjectsAdmission ? "Sonic CD (development USA R11A opening objects PLAYER)"
                : playerAdmission ? "Sonic CD (development terrain-only R11A PLAYER)" : "Sonic CD (development STATIC R11A)";
    }
    @Override public GameId getGameId() { return GameId.SCD; }

    @Override public Game createGame(GameDataSource dataSource) {
        CdGameDataSource candidate = SonicCd.requireSource(dataSource);
        authenticateOpeningSource(candidate); // Actual retained media before replacing source/presentation state.
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD module is pinned to another authenticated source; open a new module/world");
        }
        if (playerAdmission && playerFrame != null && !playerFrame.closed()) {
            throw new IllegalStateException("Retire the admitted CD player before binding a fresh level");
        }
        if (stagedPast == null && stagedAct2 == null && stagedAct2Past == null && coldProgramStartAdmission) prepareColdProgramStart(candidate);
        source = candidate;
        zones.bind(candidate);
        if (stagedAct2Past != null) validateAct2PastLoadRequest(stagedAct2PastContext);
        else if (stagedAct2 != null) validateAct2LoadRequest(stagedAct2Context);
        else if (stagedPast != null) validatePastLoadRequest(stagedPastContext);
        else if (playerAdmission) playerPresentation.bind(candidate.assets(), backgroundLoadBuffers());
        else presentation.bind(candidate.assets());
        return new SonicCd(candidate);
    }

    boolean terrainPlayerAdmission() { return playerAdmission; }
    boolean openingObjectsAdmission() { return openingObjectsAdmission; }

    private void authenticateOpeningSource(CdGameDataSource candidate) {
        if (!openingObjectsAdmission || authenticatedOpeningAssets == candidate.assets()) return;
        try { candidate.assets().openingObjects(); } // USA + actual MMD hash/tables/art, not a declared region alone.
        catch (IOException failure) { throw new UncheckedIOException("Cannot authenticate CD opening assets", failure); }
        authenticatedOpeningAssets = candidate.assets();
    }

    /** Load admission; the presentation can call this before each row's input/service too. */
    void validateTerrainPlayerRequest() {
        if (!playerAdmission) throw new IllegalStateException("Not the named CD player admission");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode(), "gameplay mode");
        var world = mode.getWorldSession();
        if (world.rootGameModule() != this || world.resolvedGameModule() != this
                || world.getPlayableCharacterRegistry() != characters || WorldSessionPolicyAccess.hasPolicies(world)
                || GameplayInputFilterAccess.current(mode) != GameplayInputFilter.IDENTITY) {
            throw SonicCd.unsupported("decorated modules, world policies or input filters in terrain-player admission");
        }
        var config = GameServices.configuration();
        String sidekick = config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        if (!"sonic".equals(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE))
                || (sidekick != null && !sidekick.isBlank())
                || config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)) {
            throw SonicCd.unsupported("terrain-player admission requires Sonic alone, without a donor");
        }
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224) {
            throw SonicCd.unsupported("terrain-player admission requires an actual 320x224 viewport");
        }
        requireRuntimeSource();
        // A fresh load may retire only this admission's previous actor. Never clear
        // an unknown roster in order to make the new load appear admissible.
        for (var actor : mode.getSpriteManager().getAllSprites()) {
            if (mode != playerMode || actor != playerActor) {
                throw SonicCd.unsupported("foreign or additional terrain-player roster");
            }
        }
        if (!mode.getSpriteManager().getRegisteredSidekicks().isEmpty()) {
            throw SonicCd.unsupported("terrain-player sidekicks");
        }
        if (mode == playerMode && playerActor != null
                && (com.openggf.sprites.managers.SpriteManagerInternalAccess.motionMutators(playerActor) != null
                || !com.openggf.sprites.playable.PlayableMutatorPolicy.STOCK.equals(
                        com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(playerActor)))) {
            throw SonicCd.unsupported("terrain-player motion or presentation mutators");
        }
    }

    private CdPlayableSonic constructTerrainPlayer(String code, int centreX, int centreY) {
        validateTerrainPlayerRequest();
        if (!constructingPlayer || playerMode != SessionManager.getCurrentGameplayMode()
                || playerFrame == null || playerFrame.closed() || playerActor != null
                || !"sonic".equals(code)
                || centreX != (playerFrame.state().motion().centreX16_16() >> 16)
                || centreY != (playerFrame.state().motion().centreY16_16() >> 16)) {
            throw new IllegalStateException("CD factory is available only for its staged cold player");
        }
        if (!openingObjectsAdmission) return new CdPlayableSonic(code, playerFrame, GameServices.graphics());
        var runtime = Objects.requireNonNull(openingObjects, "staged opening pass");
        return new CdPlayableSonic(code, playerFrame, GameServices.graphics(), new CdPlayableSonic.ObjectPass() {
            @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
                return runtime.runPlayerBody(levelFrames);
            }
            @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) { runtime.enqueuePlayer(cut); }
            @Override public void close() { runtime.close(); }
        });
    }

    /** Runs only after the composed terrain/reset/adapter stages have finished. */
    void installTerrainPlayer() {
        validateTerrainPlayerRequest();
        if (playerFrame != null || playerActor != null) throw new IllegalStateException("CD player already staged");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
        var candidate = requireRuntimeSource();
        if (!(mode.getLevelManager().getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != candidate.assets() || !mode.getSpriteManager().getAllSprites().isEmpty()) {
            throw new IllegalStateException("CD player requires its installed source terrain and an empty roster");
        }
        playerMode = mode;
        try {
            var carry = stagedRestart;
            // A fresh explicit .BeginLevel semantic ingress establishes this lineage once.
            // Same-program death/checkpoint assembly retains the actual handoff, including knowledge.
            var nextProgram = !openingObjectsAdmission ? null : carry == null
                    ? LevelProgramState.freshBeginLevel()
                    : Objects.requireNonNull(carry.program(), "Same-program reload requires its actual programme carry");
            // Declared DEV native V-int seed0 at this admitted cut, not measured retail entry.
            var loadVisit = openingObjectsAdmission ? new CheckpointLoadVisit(carry, candidate.assets(), mode) : null;
            boolean checkpoint = checkpointRestartStaged();
            playerFrame = openingObjectsAdmission
                    ? (carry == null ? CdPlayerFrameRuntime.externalTouchRequiredDevelopment(candidate.assets(), 0)
                            : checkpoint ? CdPlayerFrameRuntime.checkpointRestartDevelopment(
                                    candidate.assets(), 0, carry.player(), loadVisit::initialize)
                            : CdPlayerFrameRuntime.externalTouchRequiredDevelopment(candidate.assets(), 0, carry.player()))
                    : CdPlayerFrameRuntime.terrainOnlyDevelopment(candidate.assets());
            if (checkpoint) playerPresentation.completeCheckpointSetup(playerFrame);
            // LevelSizeLoad + one load-time LevelScroll (Main:245-246), live FG's forced flag
            // (:247) and initial BG draw (:249) all precede LoadPlayer/ObjSonic_Init.
            // No native selector-C load wait/fade V-int is fabricated by this DEV entry.
            playerPresentation.completeLoadScroll(playerFrame, loadVisit == null ? 0 : loadVisit.level.updateHudTime());
            // PP LoadLevelData:37-39 sees the newly restored spawn byte, before Main:301.
            // Read actual post-scroll FG X, never the engine camera's not-yet-bound projection.
            var loadSectionCameraX = checkpoint && loadVisit.level.spawnMode() != 0
                    ? OptionalInt.of(playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_X))
                    : OptionalInt.empty();
            if (loadVisit != null) loadVisit.finishMain(playerFrame);
            if (openingObjectsAdmission) {
                var banks = playerPresentation.reserveOpeningPictureBank();
                var camera = mode.getCamera();
                try {
                    // Explicit DEVELOPMENT startup policy, never title/new-game proof.
                    // Reserved owners 1..31 absent, goodFuture=0, object art preloaded.
                    var initialRing = loadVisit.ring;
                    var initialContact = carry == null ? new CdPlayerBody.ContactGlobals(0, 0, 0) : carry.contact();
                    var initialScore = carry == null ? new CdOpeningObjectRuntime.ScoreGlobals(0, 5000, 0) : carry.score();
                    var initialLevel = loadVisit.level;
                    var checkpointBindings = new CdOpeningObjectRuntime.CheckpointBindings(loadVisit.checkpointState,
                            checkpointReads(playerFrame, candidate.assets(), mode), loadSectionCameraX);
                    var timeWarpWorld = new TimeWarpWorldBridge(playerFrame, candidate.assets(), mode);
                    var timeWarpBindings = new CdOpeningObjectRuntime.TimeWarpBindings(
                            carry == null ? CdTimeWarpSaveData.State.unknown() : carry.timeWarpSavedState(), timeWarpWorld);
                    var foreground = checkpoint
                            ? new CdObjectDrawQueue.CameraWords(
                                    playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_X),
                                    playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_Y))
                            : new CdObjectDrawQueue.CameraWords(camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
                    openingObjects = carry == null ? CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                            candidate.assets(), playerFrame, initialRing, initialContact, initialScore,
                            // Declared post-LevelStart level globals (Level/Main.asm): levelRestart 0 (:299);
                            // updateHUDTime 0 is the DEV HUD omission, native :304 writes 1; checkpoint 0 (:41 on
                            // a first entry, no Checkpoint owner); spawnMode 0 (:301); plcLoadFlags $03 (BSET #0
                            // :37, BSET #1 :271); flowerCount 0 (:168); fadeEnableDisplay 2 (:314 writes 1, the
                            // next VInt_PalFade :897-899 adds 1); powerup 0 (:167); timeOver 0 (:293).
                            initialLevel, checkpointBindings,
                            new byte[CdObjectDrawQueue.SAT_BYTE_SIZE], banks,
                            foreground, playerFrame::applyObjectPrimaryAngle, timeWarpBindings)
                            : CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(candidate.assets(), playerFrame,
                                    initialRing, initialContact, initialScore, initialLevel, checkpointBindings, carry.savedObjectFlags(),
                                    carry.powerupArt(), carry.cameraArt(), new byte[CdObjectDrawQueue.SAT_BYTE_SIZE], banks, foreground,
                                    playerFrame::applyObjectPrimaryAngle, timeWarpBindings);
                    timeWarpWorld.bind(openingObjects);
                    openingPlc = composeOpeningPlc(openingObjects, playerFrame);
                } catch (IOException failure) {
                    throw new UncheckedIOException("Cannot stage authenticated CD opening objects", failure);
                }
            }
            if (!openingObjectsAdmission) terrainPlc = composeTerrainPlc(playerFrame);
            var definition = mode.getWorldSession().getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow();
            var loadedMotion = playerFrame.state().motion();
            int centreX = loadedMotion.centreX16_16() >> 16;
            int centreY = loadedMotion.centreY16_16() >> 16;
            constructingPlayer = true;
            try {
                playerActor = (CdPlayableSonic) CharacterConstructionScope.call(CharacterKey.SONIC, () ->
                        CharacterConstructionScope.validateFactoryResult(CharacterKey.SONIC,
                                definition.spriteFactory().create("sonic", centreX, centreY)));
            } finally {
                constructingPlayer = false;
            }
            mode.getSpriteManager().addSprite(playerActor);
            var selectedDevCut = programStartVideo == null ? programServices : withProgramCall(
                    ProgramStartCall.DECLARED_BEGIN_LEVEL_INGRESS, programServices.loads(), programServices.initialization());
            playerPresentation.bindFrame(playerFrame, playerActor, openingObjects);
            levelProgram = nextProgram;
            // This explicitly later DEV assembly supersedes the source-position clear outputs.
            // It does not pretend the omitted native Title/PLC/load prelude executed.
            programServices = selectedDevCut; programStartVideo = null;
            mode.registerLevelAdapters(mode.getLevelManager());
            mode.registerGameModuleRewindAdapters();
            var rewind = Objects.requireNonNull(mode.getRewindRegistry(), "rewind registry");
            rewind.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
            rewind.registerPostRestoreCallback(RESTORE_CALLBACK_KEY, () -> {
                requireTerrainPlayerReady();
                playerPresentation.afterRestore();
            });
            requireTerrainPlayerReady();
        } catch (RuntimeException | Error failure) {
            try { closeTerrainPlayer(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    /**
     * Construction-local actual load owners. Nothing here survives Runtime adoption or
     * enters a rewind partition. The privately minted Frame port cannot escape its callback.
     * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Load Saved Data:49-84.
     */
    /**
     * Main.ClearFlowers:178-184 clears $FF1600..$FF2000. The existing Ring owner
     * represents timeStopTimer at $FF190E (WORD) and ringAnimTimer/frame at $FF1912/13.
     * Apply this only to the new visit, before LoadCheckpointData, retaining the
     * incoming rings/lives/HUD values until their own reached native writers.
     */
    static CdRingGlobals clearFlowersRingGlobals(CdRingGlobals before) {
        Objects.requireNonNull(before, "pre-ClearFlowers ring globals");
        return new CdRingGlobals(before.rings(), before.lives(), before.livesFlags(),
                before.updateHudRings(), before.updateHudLives(), 0, 0, 0);
    }

    private final class CheckpointLoadVisit implements CdCheckpointLoadData.Port {
        private final RestartCarry carry;
        private final CdR11aAssets assets;
        private final GameplayModeContext mode;
        private final CdCheckpointSaveData.State saved;
        private CdOpeningObjectRuntime.CheckpointOwnerState checkpointState;
        private CdRingGlobals ring;
        private CdLevelGlobals level;
        private CdPlayerFrameRuntime.CheckpointLoadPort framePort;
        private boolean started;
        private boolean loading;
        private boolean loadFinished;
        private boolean finalized;
        private int cursor;
        // One ephemeral expected-value/knowledge cut, never a saved RAM image.
        private CdCheckpointLoadData.StoreTarget observedTarget;
        private OptionalInt observedValue;
        private CdOpeningObjectRuntime.CheckpointOwnerState observedCheckpoint;
        private CdRingGlobals observedRing;
        private CdLevelGlobals observedLevel;

        private CheckpointLoadVisit(RestartCarry carry, CdR11aAssets assets, GameplayModeContext mode) {
            this.carry = carry; this.assets = assets; this.mode = mode;
            ring = clearFlowersRingGlobals(carry == null ? new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0) : carry.ring());
            level = carry == null ? new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0) : carry.level();
            var incoming = carry == null ? new CdOpeningObjectRuntime.CheckpointOwnerState(
                    CdCheckpointLiveAux.unknown(), CdCheckpointSaveData.State.unknown()) : carry.checkpointState();
            // Actual admitted Main.ClearMiscVars:202-208 writes the four water bytes only.
            // Time/saved bytes survive. Unknown MAINVARS is never replaced with a zero image.
            checkpointState = new CdOpeningObjectRuntime.CheckpointOwnerState(
                    CdCheckpointLiveAux.clearMiscAtLevelStart(incoming.live()), incoming.saved());
            saved = incoming.saved();
            requireConstruction();
        }

        private void requireConstruction() {
            if (finalized || !openingObjectsAdmission || stagedRestart != carry || playerMode != mode
                    || SessionManager.getCurrentGameplayMode() != mode || playerActor != null || openingObjects != null
                    || !(mode.getLevelManager().getCurrentLevel() instanceof SonicCdLevel current)
                    || current.assets() != assets || checkpointState.saved() != saved) {
                throw new IllegalStateException("Checkpoint construction belongs to another or completed source visit");
            }
        }

        private void requireLoad() {
            requireConstruction();
            if (!loading || framePort == null || playerFrame != null || !checkpointRestartStaged()) {
                throw new IllegalStateException("Checkpoint load requires its one fresh Frame construction callback");
            }
        }

        private void initialize(CdPlayerFrameRuntime.CheckpointLoadPort actualFrame) {
            requireConstruction();
            if (started || carry == null || !checkpointRestartStaged() || playerFrame != null) {
                throw new IllegalStateException("Checkpoint load initializer cannot be reused");
            }
            started = true; loading = true;
            framePort = Objects.requireNonNull(actualFrame, "privately minted Frame load port");
            try {
                var result = CdCheckpointLoadData.load(carry.level().spawnMode(), 0, saved, this);
                int count = 19 + ((level.spawnMode() & 0x80) != 0 ? 1 : 0);
                if (cursor != count || result.completedNativeStores().size() != count) {
                    throw new IllegalStateException("R11A checkpoint load did not complete its exact native store prefix");
                }
                for (int index = 0; index < 19; index++) {
                    if (result.completedNativeStores().get(index) != CdCheckpointLoadData.NativeStore.values()[index]) {
                        throw new IllegalStateException("Checkpoint completion metadata differs from actual native cursor");
                    }
                }
                if (count == 20 && result.completedNativeStores().get(19) != CdCheckpointLoadData.NativeStore.SET_LEFT_BOUND) {
                    throw new IllegalStateException("Negative restored spawn requires the one wrapped left-bound store");
                }
                loadFinished = true; // Completed observations never drive stores, replay or a resume.
            } finally {
                loading = false; framePort = null; observedTarget = null; observedValue = null;
                observedCheckpoint = null; observedRing = null; observedLevel = null;
            }
        }

        @Override public CdCheckpointSaveData.State currentSaved() {
            requireLoad();
            return checkpointState.saved();
        }

        @Override public OptionalInt read(CdCheckpointLoadData.StoreTarget target) {
            requireLoad();
            var value = readTarget(Objects.requireNonNull(target, "reached load target"));
            observedTarget = target; observedValue = value; observedCheckpoint = checkpointState;
            observedRing = ring; observedLevel = level;
            return value;
        }

        private OptionalInt readTarget(CdCheckpointLoadData.StoreTarget target) {
            return switch (target) {
                case SPAWN_MODE -> OptionalInt.of(level.spawnMode());
                case PRIMARY_X, PRIMARY_Y, BOTTOM_BOUND, LEFT_BOUND, MINI_SONIC ->
                        OptionalInt.of(framePort.read(frameField(target)));
                case RINGS -> OptionalInt.of(ring.rings());
                case LIVES_FLAGS -> OptionalInt.of(ring.livesFlags());
                case TIME_LONG -> knownAux(CdCheckpointLiveAux.Field.TIME_LONG);
                case TIME_FRAMES -> checkpointState.live().knownByte(0xFF1517);
                case TIME_SECONDS -> checkpointState.live().knownByte(0xFF1516);
                case WATER_ROUTINE -> knownAux(CdCheckpointLiveAux.Field.WATER_ROUTINE);
                case WATER_HEIGHT2 -> knownAux(CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                case WATER_FULLSCREEN -> knownAux(CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y ->
                        OptionalInt.of(playerPresentation.readCheckpointLoadWord(target));
            };
        }

        private OptionalInt knownAux(CdCheckpointLiveAux.Field field) {
            int value = 0;
            for (int part = 0; part < field.width(); part++) {
                var next = checkpointState.live().knownByte(field.nativeAddress() + part);
                if (next.isEmpty()) return OptionalInt.empty();
                value = (value << 8) | next.getAsInt();
            }
            return OptionalInt.of(value);
        }

        private CdCheckpointLoadData.NativeStore nextInstruction() {
            if (cursor < 19) return CdCheckpointLoadData.NativeStore.values()[cursor];
            if (cursor == 19 && (level.spawnMode() & 0x80) != 0) {
                return CdCheckpointLoadData.NativeStore.SET_LEFT_BOUND;
            }
            throw new IllegalStateException("No remaining native R11A checkpoint store");
        }

        @Override public void commit(CdCheckpointLoadData.StoreCall call) {
            requireLoad();
            Objects.requireNonNull(call, "reached native checkpoint store");
            var instruction = nextInstruction();
            if (instruction.target() != call.target() || call.target() != observedTarget
                    || checkpointState != observedCheckpoint || ring != observedRing || level != observedLevel
                    || !call.expectedValue().equals(observedValue)
                    || !call.expectedValue().equals(readTarget(call.target()))) {
                throw new IllegalArgumentException("Checkpoint native cursor/current owner/value/knowledge CAS failed");
            }
            // Pure validation at this reached instruction. Unknown saved bytes fail first
            // inside the helper at their native read; no saved field is preread at ingress.
            int nativeValue = switch (instruction) {
                case CLEAR_RINGS, CLEAR_LIVES_FLAGS -> 0;
                case SET_FRAMES_59 -> 59;
                case DECREMENT_SECONDS -> (call.expectedValue().orElseThrow() - 1) & 0xFF;
                case SET_LEFT_BOUND -> (saved.read(CdCheckpointSaveData.SavedField.PRIMARY_X) - 160) & 0xFFFF;
                default -> saved.read(instruction.savedField().orElseThrow());
            };
            if (call.value() != nativeValue) throw new IllegalArgumentException("Not the reached native source value");
            switch (call.target()) {
                case SPAWN_MODE -> level = levelWithSpawn(level, call.value());
                case PRIMARY_X, PRIMARY_Y, BOTTOM_BOUND, LEFT_BOUND, MINI_SONIC -> framePort.store(
                        new CdPlayerFrameRuntime.CheckpointLoadStore(frameField(call.target()),
                                call.expectedValue().orElseThrow(), call.value()));
                case RINGS -> ring = ringWithCount(ring, call.value());
                case LIVES_FLAGS -> ring = ringWithLivesFlags(ring, call.value());
                case TIME_LONG -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeLong(checkpointState.live(), call.value()));
                case TIME_FRAMES -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeByte(checkpointState.live(), CdCheckpointLiveAux.TimeByte.FRAMES, call.value()));
                case TIME_SECONDS -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeByte(checkpointState.live(), CdCheckpointLiveAux.TimeByte.SECONDS, call.value()));
                case WATER_ROUTINE -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeWaterRoutine(checkpointState.live(), call.value()));
                case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y ->
                        playerPresentation.storeCheckpointLoadWord(call);
                // R11A never reaches these instructions. No broader zone admission is granted.
                case WATER_HEIGHT2, WATER_FULLSCREEN -> throw new IllegalStateException("Unowned non-R11A water restore");
            }
            cursor++; // Even a same-value store has synchronously executed exactly once.
        }

        private void finishMain(CdPlayerFrameRuntime actualFrame) {
            requireConstruction();
            if (loading || actualFrame != playerFrame || actualFrame.assets() != assets || actualFrame.closed()
                    || (checkpointRestartStaged() && !loadFinished)) {
                throw new IllegalStateException("Later Main policy requires the completed current load");
            }
            // Main:285-290 tests the RESTORED byte before Main:301 clears it.
            // The raw $00050000 checkpoint time therefore remains $0005FF3B for nonzero spawn.
            if (level.spawnMode() == 0) {
                ring = ringWithCount(ring, 0);
                checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.clearTimeAtSpawnZero(checkpointState.live()));
                ring = ringWithLivesFlags(ring, 0);
            }
            // Main:299 and :301 are separate addressed stores. Other existing Frame
            // clear policies are constructor-collapsed; no Body/input/clock service runs.
            level = new CdLevelGlobals(0, level.updateHudTime(), level.checkpoint(), level.spawnMode(),
                    level.plcLoadFlags(), level.flowerCount(), level.fadeEnableDisplay(), level.powerup(), level.timeOver());
            level = levelWithSpawn(level, 0);
            // Existing DEV HUD/fade policy: native Main:304 writes1 and :314 writes1,
            // but the omitted HUD/title/fade services retain HUD0/fade2 here.
            level = new CdLevelGlobals(level.levelRestart(), 0, level.checkpoint(), level.spawnMode(),
                    level.plcLoadFlags(), level.flowerCount(), level.fadeEnableDisplay(), level.powerup(), 0);
            finalized = true;
        }
    }

    private static CdPlayerFrameRuntime.CheckpointLoadField frameField(CdCheckpointLoadData.StoreTarget target) {
        return switch (target) {
            case PRIMARY_X -> CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X;
            case PRIMARY_Y -> CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y;
            case BOTTOM_BOUND -> CdPlayerFrameRuntime.CheckpointLoadField.CURRENT_BOTTOM;
            case LEFT_BOUND -> CdPlayerFrameRuntime.CheckpointLoadField.LEFT_BOUND;
            case MINI_SONIC -> CdPlayerFrameRuntime.CheckpointLoadField.MINI_SONIC;
            default -> throw new IllegalArgumentException("Not a Frame checkpoint load field");
        };
    }

    private static CdRingGlobals ringWithCount(CdRingGlobals before, int value) {
        return new CdRingGlobals(value, before.lives(), before.livesFlags(), before.updateHudRings(),
                before.updateHudLives(), before.timeStopTimer(), before.ringAnimationTimer(), before.ringAnimationFrame());
    }

    private static CdRingGlobals ringWithLivesFlags(CdRingGlobals before, int value) {
        return new CdRingGlobals(before.rings(), before.lives(), value, before.updateHudRings(),
                before.updateHudLives(), before.timeStopTimer(), before.ringAnimationTimer(), before.ringAnimationFrame());
    }

    private static CdLevelGlobals levelWithSpawn(CdLevelGlobals before, int value) {
        return new CdLevelGlobals(before.levelRestart(), before.updateHudTime(), before.checkpoint(), value,
                before.plcLoadFlags(), before.flowerCount(), before.fadeEnableDisplay(), before.powerup(), before.timeOver());
    }

    private static CdOpeningObjectRuntime.CheckpointOwnerState checkpointWithLive(
            CdOpeningObjectRuntime.CheckpointOwnerState before, CdCheckpointLiveAux.State live) {
        return new CdOpeningObjectRuntime.CheckpointOwnerState(live, before.saved());
    }

    /** Read-only binding to this exact Frame/Presentation/assets/visit; Runtime owns dispatch/cursor admission. */
    private CdOpeningObjectRuntime.CheckpointReads checkpointReads(CdPlayerFrameRuntime exactFrame,
                                                                   CdR11aAssets exactAssets, GameplayModeContext exactMode) {
        return new CdOpeningObjectRuntime.CheckpointReads() {
            private void requireCurrent() {
                if (playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                        || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode
                        || openingObjects == null || openingObjects.closed()) {
                    throw new IllegalStateException("Checkpoint read bridge belongs to a retired source visit");
                }
                playerPresentation.requireCheckpointReadOwner(exactFrame);
            }
            @Override public int readCameraWord(CdCheckpointSaveData.Source source) {
                requireCurrent();
                return playerPresentation.currentCheckpointCameraWord(source);
            }
            @Override public int readPrimaryByte(int byteOffset) {
                requireCurrent();
                var state = exactFrame.state(); var m = state.motion(); var h = state.header();
                // Deliberately partial scalar ownership, NOT a synthesized whole primary slot.
                // N Level/_Definitions.inc:33-63. Unrepresented $2E/$2F are never guessed clear.
                return switch (byteOffset) {
                    case 0x00 -> h.slotId();
                    case 0x01 -> m.spriteFlags();
                    case 0x02 -> h.tile() >>> 8;
                    case 0x03 -> h.tile() & 0xFF;
                    case 0x08 -> m.centreX16_16() >>> 24;
                    case 0x09 -> (m.centreX16_16() >>> 16) & 0xFF;
                    case 0x0A -> (m.centreX16_16() >>> 8) & 0xFF;
                    case 0x0B -> m.centreX16_16() & 0xFF;
                    case 0x0C -> m.centreY16_16() >>> 24;
                    case 0x0D -> (m.centreY16_16() >>> 16) & 0xFF;
                    case 0x0E -> (m.centreY16_16() >>> 8) & 0xFF;
                    case 0x0F -> m.centreY16_16() & 0xFF;
                    case 0x24 -> m.routine();
                    case 0x26 -> m.angle();
                    default -> throw new CdCheckpoint.UnownedParentByte(0xFFD000 + byteOffset);
                };
            }
        };
    }

    /**
     * Private visit binding only; Runtime retains the actual PLAYER_BODY invocation authority.
     * The Runtime reference is bound once after its construction. No borrowed scroll cut is retained.
     */
    private final class TimeWarpWorldBridge implements CdOpeningObjectRuntime.TimeWarpWorldPort {
        private final CdPlayerFrameRuntime exactFrame;
        private final CdR11aAssets exactAssets;
        private final GameplayModeContext exactMode;
        private CdOpeningObjectRuntime exactRuntime;

        private TimeWarpWorldBridge(CdPlayerFrameRuntime exactFrame, CdR11aAssets exactAssets,
                                    GameplayModeContext exactMode) {
            this.exactFrame = Objects.requireNonNull(exactFrame, "actual time-warp Frame");
            this.exactAssets = Objects.requireNonNull(exactAssets, "actual time-warp source assets");
            this.exactMode = Objects.requireNonNull(exactMode, "actual time-warp mode");
        }

        private void bind(CdOpeningObjectRuntime runtime) {
            Objects.requireNonNull(runtime, "actual constructed opening Runtime");
            if (exactRuntime != null || openingObjects != runtime || runtime.closed()
                    || playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                    || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode) {
                throw new IllegalStateException("Time-warp world bridge cannot bind another or retired visit");
            }
            exactRuntime = runtime;
        }

        private void requireCurrent() {
            if (exactRuntime == null || openingObjects != exactRuntime || exactRuntime.closed()
                    || playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                    || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode
                    || requireRuntimeSource().assets() != exactAssets
                    || exactRuntime.phase() != CdOpeningObjectRuntime.Phase.PLAYER_BODY) {
                throw new IllegalStateException("Time-warp world bridge requires its actual PLAYER_BODY visit");
            }
        }

        @Override public CdOpeningObjectRuntime.TimeWarpWorldCut read() {
            requireCurrent();
            return playerPresentation.borrowTimeWarpWorld(exactFrame);
        }

        @Override public void validate(CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
            requireCurrent();
            playerPresentation.validateTimeWarpLock(exactFrame, publication);
        }

        @Override public void apply(CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
            requireCurrent();
            playerPresentation.applyTimeWarpLock(exactFrame, publication);
        }
    }

    private PlcLifecycleService composeOpeningPlc(CdOpeningObjectRuntime owner, CdPlayerFrameRuntime frameOwner) {
        return new PlcLifecycleService() {
            private void requireCurrentOwner() {
                // A retained service from a prior visit must not close or advance
                // the new world. Rejection precedes the current-owner failure path.
                if (openingObjects != owner || playerFrame != frameOwner) {
                    throw new IllegalStateException("Opening PLC service belongs to a retired visit");
                }
            }
            @Override public void serviceVBlank(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                try {
                    requireTerrainPlayerReady();
                    playerPresentation.validatePlaneVBlank(phase); // Pure complete-tuple preflight before Frame consumption.
                    // Root-owned overload runs this exact callback after level art/before Finish, never on PP lag.
                    owner.serviceVBlank(phase, playerPresentation::servicePlaneAtVInt,
                            playerPresentation.currentScrollLockWord());
                    playerPresentation.publishOpeningScroll(phase);
                } catch (RuntimeException | Error failure) {
                    closePlayerCycleAfterFailure(failure);
                    throw failure;
                }
            }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                return owner.hasPreparationBoundary(phase);
            }
            @Override public boolean ownsTimedLoopTailArm() {
                requireCurrentOwner();
                return owner.ownsTimedLoopTailArm();
            }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                owner.prepareAfterLoop(phase);
            }
            @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
                requireCurrentOwner();
                return owner.captureQueueDiagnostics();
            }
        };
    }

    private PlcLifecycleService composeTerrainPlc(CdPlayerFrameRuntime owner) {
        return new PlcLifecycleService() {
            private void requireCurrentOwner() {
                if (playerFrame != owner) throw new IllegalStateException("Terrain PLC service belongs to a retired visit");
            }
            @Override public void serviceVBlank(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                try {
                    requireTerrainPlayerReady();
                    playerPresentation.validatePlaneVBlank(phase);
                    owner.serviceVBlank(phase, playerPresentation::servicePlaneAtVInt);
                    playerPresentation.publishTerrainPlane(phase);
                } catch (RuntimeException | Error failure) {
                    closePlayerCycleAfterFailure(failure);
                    throw failure;
                }
            }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                requireCurrentOwner(); return owner.hasPreparationBoundary(phase);
            }
            @Override public boolean ownsTimedLoopTailArm() {
                requireCurrentOwner(); return owner.ownsTimedLoopTailArm();
            }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
                requireCurrentOwner(); owner.prepareAfterLoop(phase);
            }
            @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
                requireCurrentOwner(); return owner.captureQueueDiagnostics();
            }
        };
    }

    void requireTerrainPlayerReady() {
        validateTerrainPlayerRequest();
        var mode = SessionManager.getCurrentGameplayMode();
        if (mode != playerMode || playerFrame == null || playerFrame.closed() || playerActor == null
                || playerFrame.assets() != requireRuntimeSource().assets()
                || mode.getSpriteManager().getAllSprites().size() != 1
                || mode.getSpriteManager().getSprite("sonic") != playerActor
                || mode.getCamera().getFocusedSprite() != playerActor
                || (openingObjectsAdmission && (openingObjects == null || openingObjects.closed()))) {
            throw new IllegalStateException("CD terrain-player admission is incomplete or its fixed roster changed");
        }
    }

    CdPlayerFrameRuntime requirePlayerFrame() {
        if (act2PastLoading != null) { validateAct2PastLoadingCut(); return playerFrame; }
        if (act2Loading != null) { validateAct2LoadingCut(); return playerFrame; }
        if (pastLoading != null) { validatePastLoadingCut(); return playerFrame; }
        requireTerrainPlayerReady(); return playerFrame;
    }
    CdPlayableSonic requirePlayerActor() { requireTerrainPlayerReady(); return playerActor; }

    private void requireOpeningClosedCut() {
        // Runtime mid-cycle capture is terminal. Reject diagnostics/rewind here
        // before calling that destructive predicate; do not pretend to complete a tail.
        if (openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
            throw new IllegalStateException("Opening rewind requires a completed native loop");
        }
    }

    /** Close the source siblings after an advanced failure; never fabricate rollback or a retry cut. */
    void closePlayerCycleAfterFailure(Throwable failure) {
        try { if (openingObjects != null) openingObjects.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        try { if (playerFrame != null) playerFrame.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    /** Also called by the player controller's close(): session teardown does not run the profile. */
    void closeTerrainPlayer() {
        if (!playerAdmission) return;
        playerPresentation.requirePastRenderIdle(); // Reject a caught recursive close before retiring any owner.
        var oldFrame = playerFrame;
        var oldObjects = openingObjects;
        var oldActor = playerActor;
        var oldMode = playerMode;
        constructingPlayer = false;
        try {
            try { if (oldObjects != null) oldObjects.close(); }
            finally { if (oldFrame != null) oldFrame.close(); }
        } finally {
            try {
                try { if (act2PastHost != null) act2PastHost.close(); }
                finally { playerPresentation.unbind(); }
            } finally {
                try {
                    try {
                        if (oldMode != null && oldMode.getRewindRegistry() != null) {
                            oldMode.getRewindRegistry().deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
                        }
                    } finally {
                        if (oldMode != null && oldActor != null && oldMode.getSpriteManager() != null
                                && oldMode.getSpriteManager().getSprite(oldActor.getCode()) == oldActor) {
                            oldMode.getSpriteManager().removeSprite(oldActor.getCode());
                        }
                    }
                } finally {
                    openingObjects = null;
                    pastLoading = null; pastLevel = null; pastLoadGeneration = -1;
                    act2Loading = null; act2Level = null; act2LoadGeneration = -1;
                    act2PastLoading = null; act2PastHost = null; act2PastLevel = null; act2PastLoadGeneration = -1;
                    levelProgram = null;
                    warpState = null; warpScene = null; warpAssets = null; programSounds = List.of();
                    openingPlc = null;
                    terrainPlc = null;
                    playerFrame = null;
                    playerActor = null;
                    playerMode = null;
                }
            }
        }
    }

    void rebindTerrainPresentation() { playerPresentation.bind(requireRuntimeSource().assets(), backgroundLoadBuffers()); }

    private BackgroundBuffers backgroundLoadBuffers() {
        if (stagedRestart != null) {
            if (!openingObjectsAdmission) throw new IllegalStateException("Only the source opening module stages restart buffers");
            return stagedRestart.background();
        }
        return SonicCdPlayerPresentation.developmentZeroBackgroundResidue();
    }

    CdGameDataSource requireRuntimeSource() {
        CdGameDataSource candidate = SonicCd.requireSource(GameServices.level().getWorldSession().getDataSource());
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD runtime source differs from this module's authenticated binding");
        }
        authenticateOpeningSource(candidate);
        return candidate;
    }

    @Override public Level transformDecodedLevel(Level decoded) {
        var candidate = requireRuntimeSource();
        if (stagedAct2Past != null) {
            validateAct2PastLoadRequest(stagedAct2PastContext);
            if (!(decoded instanceof com.openggf.game.soniccd.level.SonicCdAct2PastLevel past)
                    || past.assets() != stagedAct2Past.data().terrain()) throw new IllegalArgumentException("Foreign R12B prepared terrain");
            return past;
        }
        if (stagedAct2 != null) {
            validateAct2LoadRequest(stagedAct2Context);
            if (!(decoded instanceof com.openggf.game.soniccd.level.SonicCdAct2Level act2)
                    || act2.assets() != stagedAct2.data().terrain()) throw new IllegalArgumentException("Foreign prepared Act2 terrain");
            return act2;
        }
        if (stagedPast != null) {
            validatePastLoadRequest(stagedPastContext);
            if (!(decoded instanceof com.openggf.game.soniccd.level.SonicCdPastLevel past)
                    || past.assets() != stagedPast.assets()) throw new IllegalArgumentException("Foreign prepared Past terrain");
            return past; // Geometry preparation grants no current-pattern/palette/GPU readiness.
        }
        if (!(decoded instanceof SonicCdLevel level)
                || !candidate.assets().identity().equals(level.assets().identity())) {
            throw new IllegalArgumentException("CD decoded terrain differs from the authenticated runtime stage");
        }
        SonicCdStaticPresentation.publishDecodedGraphics(level, GameServices.graphics());
        return level;
    }

    void retirePriorAudio() { audio.retirePriorAudio(GameServices.audio()); }

    @Override public TouchResponseTable createTouchResponseTable(GameDataSource dataSource) {
        SonicCd.requireSource(dataSource);
        throw SonicCd.unsupported("native touch response (no actors are installed)");
    }

    @Override public ObjectRegistry createObjectRegistry() {
        return new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                throw SonicCd.unsupported("native object spawning");
            }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) {
                if (!spawns.isEmpty()) throw SonicCd.unsupported("executable object placements");
            }
            @Override public String getPrimaryName(int objectId) {
                throw SonicCd.unsupported("native object registry lookup");
            }
            @Override public ObjectSlotLayout objectSlotLayout() {
                throw SonicCd.unsupported("native object slot layout");
            }
        };
    }
    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() {
        throw SonicCd.unsupported("editor object encoding; raw native placements remain in CdR11aAssets");
    }
    @Override public int getPlaneSwitcherObjectId() { return 0; }
    @Override public PlaneSwitcherConfig getPlaneSwitcherConfig() { throw SonicCd.unsupported("native plane switching"); }
    @Override public LevelEventProvider getLevelEventProvider() { return null; }
    @Override public RespawnState createRespawnState() { return new SonicCdStaticState.Respawn(); }
    @Override public LevelState createLevelState() {
        // Loading has no independently ticking host HUD. Native ring/time values live
        // once in PastLoadingVisit and its existing module capture. The generic level
        // adapter explicitly supports an absent HUD partition; never invent a timer.
        if (pastLoading != null || act2Loading != null || act2PastLoading != null) return null;
        return playerAdmission ? new SonicCdDevelopmentPlayerState() : new SonicCdStaticState.Level();
    }
    @Override public ZoneRegistry getZoneRegistry() {
        return stagedAct2 != null || act2Loading != null || stagedAct2Past != null || act2PastLoading != null ? act2Zones : zones;
    }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return playerAdmission ? playerPresentation : presentation; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return null; }
    @Override public PhysicsProvider getPhysicsProvider() { return rules; }
    @Override public GameAudioProfile getAudioProfile() { return audio; }
    @Override public LevelInitProfile getLevelInitProfile() {
        return stagedAct2Past != null || act2PastLoading != null ? act2PastProfile : stagedAct2 != null || act2Loading != null ? act2Profile : stagedPast != null || pastLoading != null ? pastProfile : playerAdmission ? playerProfile : profile;
    }
    @Override public GameplayFrameController gameplayFrameController() { return playerAdmission ? playerPresentation : presentation; }
    @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() { return characters; }
    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        return openingObjectsAdmission ? List.of(sharedOpeningAdapter)
                : playerAdmission ? List.of(sharedPlayerAdapter) : List.of();
    }
    @Override public <T> T getGameService(Class<T> type) {
        if (openingObjectsAdmission) {
            // Even a failed owner remains selected until teardown: returning null here
            // would silently fall back to normal gameplay after a terminal failure.
            if (type == NativeDynamicObjectPass.class) return openingObjects == null ? null : type.cast(openingObjects);
            return type == PlcLifecycleService.class && openingPlc != null ? type.cast(openingPlc) : null;
        }
        return playerAdmission && type == PlcLifecycleService.class && terrainPlc != null ? type.cast(terrainPlc) : null;
    }
    @Override public boolean suppressesLevelSelect() { return true; }
    @Override public void onNewGameFromTitle(GameStateManager gameState) { throw SonicCd.unsupported("native new-game entry"); }
    @Override public TitleScreenProvider getTitleScreenProvider() { throw SonicCd.unsupported("native title entry"); }
    @Override public LevelSelectProvider getLevelSelectProvider() { throw SonicCd.unsupported("native level select"); }
    @Override public SaveSnapshotProvider getSaveSnapshotProvider() { throw SonicCd.unsupported("campaign saves/BRAM"); }
}
