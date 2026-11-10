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
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.EngineServices;
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
import com.openggf.game.soniccd.objects.CdSonicHole;
import com.openggf.game.soniccd.objects.CdTunnelDoorVertical;
// END R12B Disc/Door imports.
import com.openggf.game.soniccd.objects.CdTunnelDoorSplash;
import com.openggf.game.soniccd.objects.CdR4PlayerBubbles;
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
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerEngineRules;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdBlockDefinitionStaging;
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
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Real stock CD module retaining its default STATIC R11A terrain admission and
 * separately named terrain-only and USA opening-object development PLAYER entries.
 * The opening entry executes the bounded Ring/Mosqui owners, not a retail title
 * ingress or full route. Native title/new-game/HUD/audio driver, saves, export and
 * editor entry remain unavailable from those default/development entries. The distinct selected-profile
 * entry owns pre-level storage/progress only and fences actual programme/clock/level admission.
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
    private final CdSelectedProfileVisit selectedProfile;
    private WorldSession selectedProfilePolicyWorld;
    private com.openggf.game.session.WorldSessionPolicyState selectedProfilePolicyOwner;
    private final Object selectedProfileInvocation;
    private final CdOriginalBootPrefix originalBoot; // Fresh factory only; never reconstructed from values/captures.
    private final Object r11TimeWarpSavedLifetime=new Object();
    // Absent without original Boot; never borrow OpeningObjectRuntime's DEV bank.
    private CdTimeWarpSaveData.State r11TimeWarpSaved;
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

    // BEGIN IPX PERSISTENT PROGRESS OWNER 20261107
    /** The actual Core/Module owns live timeStones. This retained image deliberately stores no duplicate. */
    private record IpxProgressFacts(CdIpxProgress.SavedLevel savedLevel,OptionalInt goodFuturesByte,
            OptionalInt titleFlagsByte,OptionalInt timeAttackUnlockByte,OptionalInt unknownBackupByte,
            OptionalInt currentSpecialStageByte,OptionalInt saveDisabledByte){
        private IpxProgressFacts{new CdIpxProgress.State(savedLevel,goodFuturesByte,titleFlagsByte,timeAttackUnlockByte,
                unknownBackupByte,currentSpecialStageByte,OptionalInt.empty(),saveDisabledByte);}
        private CdIpxProgress.State project(int actualTimeStones){return new CdIpxProgress.State(savedLevel,goodFuturesByte,
                titleFlagsByte,timeAttackUnlockByte,unknownBackupByte,currentSpecialStageByte,OptionalInt.of(actualTimeStones),saveDisabledByte);}
        private static IpxProgressFacts from(CdIpxProgress.State value){return new IpxProgressFacts(value.savedLevel(),value.goodFuturesByte(),
                value.titleFlagsByte(),value.timeAttackUnlockByte(),value.unknownBackupByte(),value.currentSpecialStageByte(),value.saveDisabledByte());}
    }
    private IpxProgressFacts ipxProgress=IpxProgressFacts.from(CdIpxProgress.unknown());
    private Object ipxProgressLifetime=new Object();
    /** Exact original shared-payload cut pairing; equality of facts is never restore/publication authority. */
    private record IpxProgressImage(SonicCdGameModule owner,CdGameDataSource source,Object lifetime,
                                    Object cut,IpxProgressFacts facts){}
    private IpxProgressImage captureIpxProgress(Object cut){return new IpxProgressImage(this,Objects.requireNonNull(source),
            ipxProgressLifetime,Objects.requireNonNull(cut),ipxProgress);}
    private void validateIpxProgress(IpxProgressImage image,Object cut){
        if(image==null||image.owner()!=this||image.source()!=source||image.lifetime()!=ipxProgressLifetime
                ||image.cut()!=cut||image.facts()==null)throw new IllegalArgumentException("Foreign progress/source/lifetime/shared capture pair");
    }
    /** Pure observation only; live timeStones is sampled from its actual current programme owner. */
    CdIpxProgress.State ipxProgressState(){if(r31Loading!=null)return ipxProgress.project(r31Loading.state().program().timeStonesByte());if(selectedProfile!=null)return selectedProfile.state().progress();return ipxProgress.project(levelProgramState().timeStones());}
    // END IPX PERSISTENT PROGRESS OWNER 20261107

    // BEGIN genuine fresh selected-profile R31 loading assembly 20261107.
    private final SonicCdR31aLevelInitProfile r31Profile=new SonicCdR31aLevelInitProfile(this);
    private final SonicCdR31aZoneRegistry r31Zones=new SonicCdR31aZoneRegistry(this);
    private com.openggf.game.soniccd.level.CdR31aAssets stagedR31;
    private LevelLoadContext stagedR31Context;
    private com.openggf.game.soniccd.level.SonicCdR31aLevel r31Level;
    private SonicCdR31aLoadingVisit r31Loading;
    private SonicCdR31aWorldHost r31Host;
    private CdWorld r31World;
    private com.openggf.game.soniccd.resources.CdR31aSectionArt r31Art;
    private CdOriginalBootPrefix.Continuation r31Permit;
    private CdSelectedProfileVisit.State r31SelectedFinal;
    private Object r31Epoch;
    private Thread r31Thread;
    private long r31LoadGeneration=-1,r31PreviousGeneration=-1;
    private boolean constructingR31,r31PermitConsumed,r31ServiceActive,r31Retired;
    private record R31Shared(SonicCdR31aLoadingVisit.Snapshot loading,ProgramServicesState services,
            OptionalInt goodFutureFlags,IpxProgressImage progress,Object epoch,CdSelectedProfileVisit.State selected){}
    boolean r31RegistrySelected(){return stagedR31!=null||r31Loading!=null;}
    boolean r31LoadingSelected(){return r31Loading!=null;}
    com.openggf.game.soniccd.level.CdR31aAssets actualR31RegistryAssets(){
        if(!r31RegistrySelected())throw new IllegalStateException("No actual R31 programme selection");
        return stagedR31!=null?stagedR31:r31Level.assets();
    }
    SonicCdR31aLoadingVisit r31LoadingVisit(){validateR31LoadingCut();return r31Loading;}
    void drawR31World(){Objects.requireNonNull(r31World).draw();}
    void drawAct3FutureWorld(){Objects.requireNonNull(act3FutureWorld).draw();}
    SonicCdR31aWorldHost r31WorldHost(){return Objects.requireNonNull(r31Host,"No actual R31 loading Host");}
    private void requireR31Pair(SonicCdR31aLoadingVisit core,SonicCdR31aLoadingVisit.Host host,
            CdPlayerFrameRuntime frame,com.openggf.game.soniccd.resources.CdR31aSectionArt art){
        var mode=SessionManager.getCurrentGameplayMode();var world=SessionManager.getCurrentWorldSession();
        if(originalBoot==null||selectedProfile==null||r31Retired||r31Permit==null||r31Epoch==null||r31Thread!=Thread.currentThread()
                ||core==null||core!=r31Loading||host!=r31Host||frame!=playerFrame||frame==null||frame.closed()||art!=r31Art
                ||r31Level==null||mode==null||mode!=playerMode||world!=programWorld||mode.getWorldSession()!=world
                ||world.rootGameModule()!=this||world.resolvedGameModule()!=this||world.getDataSource()!=source
                ||source!=originalBoot.source()||source.r31aAssets().orElseThrow()!=r31Level.assets()
                ||selectedProfile.state()!=r31SelectedFinal||!frame.stageSource().ownsR31aAssets(r31Level.assets())||!art.ownsAssets(r31Level.assets())
                ||mode.getLevelManager().getCurrentLevel()!=r31Level||playerActor!=null||openingObjects!=null
                ||!mode.getSpriteManager().getAllSprites().isEmpty()||!mode.getSpriteManager().getRegisteredSidekicks().isEmpty())
            throw new IllegalStateException("Actual original R31 source/World/Frame/Host/Core/epoch pair changed");
        long expected=stagedR31!=null?r31PreviousGeneration:r31LoadGeneration;
        if(mode.getLevelManager().getCompletedProductionLoadGeneration()!=expected)throw new IllegalStateException("Actual R31 load generation changed");
        if(mode.getCamera().getWidth()!=320||mode.getCamera().getHeight()!=224)throw SonicCd.unsupported("Native R31 viewport");
        if(constructingR31)validateR31LoadRequest(stagedR31Context);
        else{
            if(r31PermitConsumed)originalBoot.requireConsumed(this,world,selectedProfileInvocation);
            else originalBoot.requireHandoffStarted(this,world,selectedProfileInvocation);
        }
    }
    void validateR31LoadingCut(){playerPresentation.requirePastRenderIdle();requireR31Pair(r31Loading,r31Host,playerFrame,r31Art);playerFrame.captureActor();playerFrame.captureShared();}
    void serviceR31Loading(int held){
        playerPresentation.requirePastRenderIdle();validateR31LoadingCut();r31Loading.requireServiceIdle();
        if(r31ServiceActive)throw new IllegalStateException("R31 native service reentry");
        if((held&~255)!=0)throw new IllegalArgumentException("Native held BYTE required");
        r31ServiceActive=true;
        try{r31Loading.service(held);syncR31Initialization();r31Host.projectCamera(GameServices.camera());}
        catch(RuntimeException|Error original){
            try{syncR31Initialization();}catch(RuntimeException|Error projection){if(projection!=original)original.addSuppressed(projection);}
            r31Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(original);
            throw original;
        }finally{r31ServiceActive=false;}
    }
    private void syncR31Initialization(){if(r31Loading!=null&&programServices.initialization()!=r31Loading.state().initialization())programServices=new ProgramServicesState(programServices.loads(),r31Loading.state().initialization(),programServices.environment(),programServices.completedCalls());}
    void validateR31LoadRequest(LevelLoadContext context){
        var mode=SessionManager.getCurrentGameplayMode();var world=SessionManager.getCurrentWorldSession();
        if(originalBoot==null||r31Retired||stagedR31==null||context==null||context!=stagedR31Context||r31Permit==null||r31Thread!=Thread.currentThread()
                ||source!=originalBoot.source()||source.r31aAssets().orElseThrow()!=stagedR31||selectedProfile.state()!=r31SelectedFinal
                ||mode==null||world!=programWorld||world!=mode.getWorldSession()||world.rootGameModule()!=this||world.resolvedGameModule()!=this||world.getDataSource()!=source
                ||context.getLevelIndex()!=r31Zones.loadingIndex()||context.getLoadMode()!=LevelLoadMode.FULL
                ||context.getLoadCause()!=LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY||context.getAssemblyKind()!=LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                ||context.getZone()!=1||context.getAct()!=0||!context.isIncludePostLoadAssembly()||context.hasCheckpoint()||context.getRom()!=null
                ||context.isShowTitleCard()||context.isTitleCardRequiredInHeadlessMode()||context.isQueueFreshLevelRuntimeArt())
            throw SonicCd.unsupported("R31 FULL requires its original applied fresh selected-profile handoff");
    }
    private final SonicCdR31aWorldHost.Authority r31Authority=new SonicCdR31aWorldHost.Authority(){
        @Override public void require(SonicCdR31aWorldHost host,SonicCdR31aLoadingVisit core,CdPlayerFrameRuntime frame,
                com.openggf.game.soniccd.level.CdR31aAssets assets,com.openggf.game.soniccd.resources.CdR31aSectionArt art){
            if(assets!=actualR31RegistryAssets())throw new IllegalArgumentException("Foreign actual R31 aggregate");requireR31Pair(core,host,frame,art);
        }
        @Override public void levelMusic(SonicCdR31aWorldHost host,CdCommand command){
            requireR31Pair(r31Loading,host,playerFrame,r31Art);r31Loading.requireAction(host,SonicCdR31aLoadingVisit.Action.PLAY_LEVEL_MUSIC,r31Loading.state());
            if(r31Loading.state().music().orElseThrow()!=command)throw new IllegalArgumentException("Not the selected source-backed R31 music");
        }
        @Override public void resetGoodFutureFlags(SonicCdR31aWorldHost host){
            requireR31Pair(r31Loading,host,playerFrame,r31Art);r31Loading.requireAction(host,SonicCdR31aLoadingVisit.Action.FRESH_RESET_SUFFIX,r31Loading.state());
            var p=levelProgram;levelProgram=new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),OptionalInt.of(0));
        }
        @Override public void serviceVInt(SonicCdR31aWorldHost host){requireR31Pair(r31Loading,host,playerFrame,r31Art);r31Loading.requireAction(host,SonicCdR31aLoadingVisit.Action.VINT_COMPLETED,r31Loading.state());EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();}
        @Override public void loadingPublication(SonicCdR31aWorldHost host,SonicCdR31aLoadingVisit core,SonicCdR31aLoadingVisit.Publication original,CdAssetBytes palette){
            requireR31Pair(core,host,playerFrame,r31Art);host.requireLoadingPublication(original,palette);
            if(!r31PermitConsumed){r31Permit.consume(core,host,original);r31PermitConsumed=true;}
            else originalBoot.requireConsumed(SonicCdGameModule.this,programWorld,selectedProfileInvocation);
        }
    };
    void installR31LoadingVisit(){
        validateR31LoadRequest(stagedR31Context);
        if(playerFrame!=null||playerActor!=null||openingObjects!=null||pastLoading!=null||act2Loading!=null||act2PastLoading!=null||act3FutureLoading!=null||r31Loading!=null)throw new IllegalStateException("No old gameplay owner may be grafted into fresh R31");
        playerMode=SessionManager.getCurrentGameplayMode();
        if(!(playerMode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdR31aLevel level)||level.assets()!=stagedR31)throw new IllegalArgumentException("Foreign prepared R31 terrain");
        try{
            // Actual IPX RunMMD source-selected PCM/header/clear prefix, before this programme's Start.
            var loads=SonicCdProgramLoadServices.loadLevelR3Pcm(programServices.loads(),source.r3PcmSource().orElseThrow());
            programServices=withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM,loads,programServices.initialization());
            var clear=originalBoot.workClears();var main=originalBoot.mainClears();var selected=r31SelectedFinal.globals().orElseThrow();
            var environment=originalProgrammeEnvironment(selected);
            // RunMMD [FF2000,FFFB00) repeats the named work clears; MAINVARS/palettes/cloud/saved carry survives.
            playerFrame=CdPlayerFrameRuntime.fromR31aEntry(stagedR31,clear.primary(),environment,clear.playerArt(),new CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.empty(),0));
            var g=clear.globals();var ring=g.ring();var live=CdCheckpointLiveAux.writeTimeLong(g.live(),selected.time());
            var globals=new SonicCdR31aWorldHost.Globals(new CdRingGlobals(selected.rings(),selected.lives().orElseThrow(),ring.livesFlags(),ring.updateHudRings(),ring.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame()),live,g.contact(),
                    new CdOpeningObjectRuntime.ScoreGlobals(selected.score(),g.score().nextLifeScore(),g.score().updateHudScore()),OptionalInt.of(selected.projector()),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());
            var hud=com.openggf.game.soniccd.level.CdR31aHudAssets.fromAssets(stagedR31);
            var current=com.openggf.game.soniccd.resources.CdR31aArtResidency.unknownForNativeHud(stagedR31,hud);
            if(!originalBoot.state().video().patternsCleared())throw new IllegalStateException("Original MD physical clear is absent");
            current=com.openggf.game.soniccd.resources.CdR31aArtResidency.clear(current); // projection of the real earlier MD clear, not a new service
            var originalVideo=originalBoot.state().video();var plane=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State(originalVideo.plane().orElseThrow());
            var physicalHscroll=originalBoot.physicalClears().hscroll();int[] selectedHscroll=new int[256];for(int i=0;i<256;i++)selectedHscroll[i]=(int)physicalHscroll.unsignedLong(i*4);
            // Disabled retained physical picture from actual MD pattern/name/SAT/HScroll/CRAM clears.
            var scene=new com.openggf.graphics.RetainedPlaneSceneRenderer.Scene(null,null,selectedHscroll,(int)originalVideo.vscroll().orElseThrow(),originalVideo.displayedPalette().orElseThrow().copy(),originalVideo.displayEnabled().orElseThrow(),com.openggf.graphics.SpritePresentation.Frame.empty(),java.util.Map.of(),0x20);
            r31Level=level;r31Art=com.openggf.game.soniccd.resources.CdR31aSectionArt.fromAssets(stagedR31);
            var plc=clear.plc();var section=r31Art.fromValues(clear.nemesisTable(),current,originalBoot.state().main().orElseThrow().sectionWord(),plc.queue(),Optional.empty(),plc.saved(),plc.remaining(),plc.budget(),plc.hint(),plc.pad());
            // The cold actual placement/pool is created at this real RunMMD clear, not by Host defaults.
            var placement=CdObjectPlacementRuntime.fromR31aAssets(stagedR31,main.savedFlags());
            var carriedNative=clear.nativeCarry();
            var nativeCarry=new SonicCdR31aWorldHost.NativeCarry(carriedNative.collisionPointer(),carriedNative.fadeInfoWithoutTail(),carriedNative.rngSeedLong(),carriedNative.targetRightBoundWord(),selected.amyCaptured());
            r31Host=new SonicCdR31aWorldHost(r31Authority,stagedR31,playerFrame,r31Art,new SonicCdR31aWorldHost.Initial(globals,clear.scroll(),new SonicCdR31aWorldHost.Planes(plane,plane),new SonicCdR31aWorldHost.Video(clear.cpuSat(),Optional.empty(),playerFrame.artState(),current,new SonicCdR31aWorldHost.Carried(scene)),placement,nativeCarry));
            var m=originalBoot.state().main().orElseThrow();var p=new SonicCdR31aLoadingVisit.Program(m.demoModeWord(),r31SelectedFinal.progress().timeStonesByte().orElseThrow(),selected.goodFuture(),m.usePlayer2Byte(),selected.specialStage(),m.palFadeFlagsByte(),clear.gameModeByte(),m.levelStartedByte(),clear.demoDataIndexWord());
            var oldLevel=m.level();var levelGlobals=new CdLevelGlobals(oldLevel.levelRestart(),oldLevel.updateHudTime(),selected.checkpoint(),oldLevel.spawnMode(),selected.plcLoadFlags().orElseThrow(),oldLevel.flowerCount(),oldLevel.fadeEnableDisplay(),oldLevel.powerup(),oldLevel.timeOver());
            levelProgram=new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),0,LevelProgramPhase.R31_LOADING,OptionalInt.of(main.goodFutureFlags()));
            var palettes=originalBoot.state().cpuPalettes().orElseThrow();
            r31Loading=new SonicCdR31aLoadingVisit(r31Host,stagedR31,playerFrame,r31Art,new SonicCdR31aLoadingVisit.Entry(programServices.initialization(),originalBoot.environment(),section,p,levelGlobals,palettes.normal(),palettes.fade(),clear.waterPalette(),originalVideo.displayedPalette().orElseThrow(),clear.reserved(),originalVideo.displayEnabled().orElseThrow(),originalBoot.state().blockDefinitions()));
            r31Host.install(r31Loading);r31Host.retainOriginalMainClears(r31Authority,main);
            r31World=new CdWorld(r31Authority,r31Loading,r31Host,playerFrame,stagedR31,r31Art,com.openggf.game.soniccd.level.CdR31aObjectAssets.fromAssets(stagedR31));
            r31Host.bindOrdinaryWorld(r31Authority,r31World);r31Loading.bindOrdinaryOwner(r31Host,r31World);
            r31Permit.bindLoading(r31Loading,r31Host,playerFrame,r31Art);
            r31Loading.begin();syncR31Initialization();r31Host.projectCamera(GameServices.camera());
        }catch(IOException failure){throw new UncheckedIOException("Cannot assemble actual original R31 source",failure);}
        catch(SonicCdProgramLoadServices.Unsupported stopped){programServices=new ProgramServicesState(stopped.completedPrefix(),programServices.initialization(),programServices.environment(),programServices.completedCalls());throw stopped;}
    }
    void registerR31RestoreProjection(){validateR31LoadingCut();var registry=Objects.requireNonNull(playerMode.getRewindRegistry());registry.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);registry.registerPostRestoreCallback(RESTORE_CALLBACK_KEY,playerPresentation::afterRestore);}
    private R31Shared captureR31(){
        validateR31LoadingCut();originalBoot.requireConsumed(this,programWorld,selectedProfileInvocation);validateProgramServicesState(programServices);
        var snapshot=r31World.captureR31();return new R31Shared(snapshot,programServices,levelProgram.goodFutureFlags(),captureIpxProgress(snapshot.state()),r31Epoch,r31SelectedFinal);
    }
    private void validateR31Restore(R31Shared s){
        validateR31LoadingCut();originalBoot.requireConsumed(this,programWorld,selectedProfileInvocation);
        if(s==null||s.epoch()!=r31Epoch||s.selected()!=r31SelectedFinal)throw new IllegalArgumentException("Foreign R31 selected/native epoch capture");
        r31World.validateRestore(s.loading());validateProgramServicesState(s.services());validateIpxProgress(s.progress(),s.loading().state());
        var c=s.loading().state();var p=c.program();
        var complete=new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),c.initialFadeSteps(),LevelProgramPhase.R31_LOADING,s.goodFutureFlags());
        if(!s.services().initialization().equals(c.initialization()))throw new IllegalArgumentException("Foreign complete R31 programme/initialization capture");
    }
    private void restoreR31(R31Shared s){validateR31Restore(s);var c=s.loading().state();var p=c.program();var ready=new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),c.initialFadeSteps(),LevelProgramPhase.R31_LOADING,s.goodFutureFlags());try{r31World.restore(s.loading());programServices=s.services();levelProgram=ready;ipxProgress=s.progress().facts();}catch(RuntimeException|Error failure){r31Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}}
    // END genuine fresh selected-profile R31 loading assembly.

    // R13 is admitted only by the privately staged, completed old R12B/IPX return.
    private final SonicCdAct3FutureLevelInitProfile act3FutureProfile = new SonicCdAct3FutureLevelInitProfile(this);
    private final SonicCdAct3ZoneRegistry act3Zones = new SonicCdAct3ZoneRegistry(this);
    private Act3FutureCarry stagedAct3Future;
    private LevelLoadContext stagedAct3FutureContext;
    private SonicCdAct3FutureLoadingVisit act3FutureLoading;
    private SonicCdAct3FutureWorldHost act3FutureHost;
    private CdWorld act3FutureWorld;
    private com.openggf.game.soniccd.level.SonicCdAct3FutureLevel act3FutureLevel;
    private com.openggf.game.soniccd.resources.CdR13SectionArt act3FutureArt;
    private com.openggf.game.soniccd.level.CdR13ProgrammeAssets act3ProgrammeAssets;
    private long act3FutureLoadGeneration = -1;
    private int act3SelectedGoodFuture = -1;
    private boolean constructingAct3Future;
    private record Act3FutureCarry(com.openggf.game.soniccd.level.CdR13FutureAssets assets,
                                  CdPlayerBody.Environment environment, CdPlayerFrameRuntime.VIntClock clock,
                                  SonicCdAct2PastLoadingVisit.State core, SonicCdAct2PastWorldHost.Globals globals,
                                  SonicCdAct2PastWorldHost.Scroll scroll, CdAssetBytes savedFlags,
                                  long generation, LevelProgramState program) { }
    private record Act3FutureShared(SonicCdAct3FutureLoadingVisit.Snapshot loading,
                                   ProgramServicesState services, OptionalInt goodFutureFlags, IpxProgressImage ipxProgress) { }
    boolean act3FutureRegistrySelected() { return stagedAct3Future != null || act3FutureLoading != null; }
    boolean act3FutureLoadingSelected() { return act3FutureLoading != null; }
    com.openggf.game.soniccd.level.CdR13FutureAssets actualAct3RegistryAssets() {
        if (!act3FutureRegistrySelected()) throw new IllegalStateException("No actual R13 programme selection");
        return stagedAct3Future != null ? stagedAct3Future.assets() : act3FutureLevel.assets();
    }
    CdGameDataSource actualAct3RegistrySource() { actualAct3RegistryAssets();return source; }
    SonicCdAct3FutureLoadingVisit act3FutureLoadingVisit() { validateAct3FutureLoadingCut(); return act3FutureLoading; }
    SonicCdAct3FutureWorldHost act3FutureWorldHost() { return Objects.requireNonNull(act3FutureHost,"No actual R13 host"); }
    void requireAct3FutureLoadingVisit(SonicCdAct3FutureLoadingVisit visit) {
        var mode=SessionManager.getCurrentGameplayMode();
        if(!coldProgramStartAdmission||visit==null||visit!=act3FutureLoading||act3FutureHost==null
                ||playerFrame==null||playerFrame.closed()||playerActor!=null||openingObjects!=null
                ||pastLoading!=null||act2Loading!=null||act2PastLoading!=null||mode==null||mode!=playerMode
                ||mode.getWorldSession()!=programWorld||programWorld.rootGameModule()!=this
                ||programWorld.resolvedGameModule()!=this||programWorld.getDataSource()!=source
                ||mode.getLevelManager().getCurrentLevel()!=act3FutureLevel
                ||!playerFrame.stageSource().ownsAct3Future(act3FutureLevel.assets())
                ||source.act3FutureAssets().orElseThrow().select(act3SelectedGoodFuture)!=act3FutureLevel.assets()
                ||mode.getLevelManager().getCompletedProductionLoadGeneration()
                    !=(stagedAct3Future==null?act3FutureLoadGeneration:stagedAct3Future.generation())
                ||!mode.getSpriteManager().getAllSprites().isEmpty()
                ||!mode.getSpriteManager().getRegisteredSidekicks().isEmpty())
            throw new IllegalStateException("Actual R13 source/World/Frame/load lifetime changed");
        if(mode.getCamera().getWidth()!=320||mode.getCamera().getHeight()!=224)
            throw SonicCd.unsupported("R13 requires the native viewport");
    }
    void validateAct3FutureLoadingCut() {
        playerPresentation.requirePastRenderIdle();requireAct3FutureLoadingVisit(act3FutureLoading);
        playerFrame.captureShared();playerFrame.captureActor();
    }
    void serviceAct3FutureLoading(int held) {
        playerPresentation.requirePastRenderIdle();validateAct3FutureLoadingCut();act3FutureLoading.requireServiceIdle();
        if((held&~255)!=0)throw new IllegalArgumentException("Native held BYTE required");
        try { act3FutureLoading.service(held);act3FutureHost.projectCamera(GameServices.camera()); }
        catch(RuntimeException|Error failure){
            boolean returned=failure instanceof SonicCdAct3FutureLoadingVisit.UnsupportedLoading stopped
                    &&(act3FutureLoading.state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED
                        ||act3FutureLoading.state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_BOSS_RETURNED
                        ||act3FutureLoading.state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R3_SAVE
                        ||act3FutureLoading.state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R1_TITLE_STOP
                        ||act3FutureLoading.state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R1_GOOD_FUTURES_STOP)
                    &&!act3FutureLoading.failed()
                    &&(stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_BOSS_SAVED_ZONE_OWNER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_BOSS_GAME_OVER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_TIME_ATTACK_UNLOCK_OWNER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_UNLOCK_CONTINUATION_OWNER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_TITLE_FLAGS_OWNER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R1_GOOD_FUTURES_OWNER
                    ||stopped.boundary()==SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R3_WRITE_SAVE_OWNER);
            if(!returned)closePlayerCycleAfterFailure(failure);throw failure;
        }
    }
    void validateAct3FutureLoadRequest(LevelLoadContext context) {
        var mode=SessionManager.getCurrentGameplayMode();
        if(!coldProgramStartAdmission||stagedAct3Future==null||context==null||context!=stagedAct3FutureContext
                ||mode==null||programWorld!=mode.getWorldSession()||programWorld.getDataSource()!=source
                ||programWorld.rootGameModule()!=this||programWorld.resolvedGameModule()!=this
                ||context.getLevelIndex()!=2||context.getLoadMode()!=LevelLoadMode.FULL
                ||context.getLoadCause()!=LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY
                ||context.getAssemblyKind()!=LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                ||!context.isIncludePostLoadAssembly()||context.hasCheckpoint()||context.getRom()!=null
                ||context.isShowTitleCard()||context.isTitleCardRequiredInHeadlessMode()||context.isQueueFreshLevelRuntimeArt()
                ||source.act3FutureAssets().orElseThrow().select(stagedAct3Future.core().program().goodFutureByte())!=stagedAct3Future.assets())
            throw SonicCd.unsupported("R13 FULL load requires its exact private old programme return");
    }
    private void loadActualAct3FutureWorld() {
        validateAct2PastLoadingCut();act2PastLoading.requireServiceIdle();
        var state=act2PastLoading.state();var globals=act2PastHost.globals();var gates=playerFrame.environment().movement().gates();
        if(state.phase()!=SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED||act2PastLoading.failed()
                ||globals.ring().lives()==0||gates.zoneAct()!=2||gates.timeZone()!=2
                ||state.program().specialStageByte()!=0||state.level().levelRestart()!=0)
            throw new IllegalStateException("No completed old R12B/IPX return to RunR13");
        var data=source.act3FutureAssets().orElseThrow(()->SonicCd.unsupported("Original R13 C/D pair was not retained before media closure"));
        var selected=data.select(state.program().goodFutureByte());
        if(!selected.identity().observedWholeDiscSha256().equals(source.assets().identity().observedWholeDiscSha256()))
            throw new IllegalArgumentException("R13 belongs to another original disc");
        stagedAct3Future=new Act3FutureCarry(selected,playerFrame.environment(),playerFrame.vIntClock(),state,globals,
                act2PastHost.scroll(),act2PastHost.placement().capture().savedFlags(),
                playerMode.getLevelManager().getCompletedProductionLoadGeneration(),levelProgramState());
        stagedAct3FutureContext=SonicCdLevelInitProfile.staticStageContext();
        stagedAct3FutureContext.setLevelIndex(2);stagedAct3FutureContext.setZone(0);stagedAct3FutureContext.setAct(2);
        stagedAct3FutureContext.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        try {
            playerMode.getLevelManager().loadLevel(2,LevelLoadMode.FULL,stagedAct3FutureContext);
            var mode=SessionManager.getCurrentGameplayMode();
            if(mode.getLevelManager().getCompletedProductionLoadGeneration()!=act3FutureLoadGeneration
                    ||mode.getLevelManager().getCurrentLevel()!=act3FutureLevel)
                throw new IllegalStateException("Actual R13 FULL did not commit its own generation");
        } catch(IOException failure){throw new UncheckedIOException("Cannot load actual same-World R13",failure);}
        finally{stagedAct3Future=null;stagedAct3FutureContext=null;}
    }
    void registerAct3FutureRestoreProjection() {
        validateAct3FutureLoadingCut();var registry=Objects.requireNonNull(playerMode.getRewindRegistry());
        registry.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
        registry.registerPostRestoreCallback(RESTORE_CALLBACK_KEY,playerPresentation::afterRestore);
    }
    private final SonicCdAct3FutureWorldHost.Authority act3Authority = new SonicCdAct3FutureWorldHost.Authority() {
        @Override public void require(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit core,
                                      CdWorld world,CdPlayerFrameRuntime frame,
                                      com.openggf.game.soniccd.level.CdR13FutureAssets assets,
                                      com.openggf.game.soniccd.resources.CdR13SectionArt art) {
            if(host!=act3FutureHost||core!=act3FutureLoading||frame!=playerFrame||art!=act3FutureArt
                    ||act3FutureLevel==null||assets!=act3FutureLevel.assets()
                    ||(world==null?!constructingAct3Future:world!=act3FutureWorld))
                throw new IllegalStateException("No actual R13 private Host/Core/World relation");
            requireAct3FutureLoadingVisit(core);
        }
        @Override public int selectedGoodFutureByte(SonicCdAct3FutureWorldHost host) {
            if(host!=act3FutureHost)throw new IllegalArgumentException("Foreign R13 host");
            requireAct3FutureLoadingVisit(act3FutureLoading);return act3SelectedGoodFuture;
        }
        @Override public PastSound levelMusic(SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit.Program program,
                                              int rawPeriod,int timeAttack) {
            if(host!=act3FutureHost)throw new IllegalArgumentException("Foreign R13 music host");
            requireAct3FutureLoadingVisit(act3FutureLoading);
            act3FutureLoading.requireAction(host,SonicCdAct3FutureLoadingVisit.Action.PLAY_LEVEL_MUSIC,act3FutureLoading.state());
            if(program!=act3FutureLoading.state().program()||rawPeriod!=playerFrame.environment().movement().gates().timeZone()
                    ||timeAttack!=playerFrame.environment().globals().timeAttackMode())
                throw new IllegalArgumentException("Not the actual reached R13 music selector");
            return new PastLevelMusic(act3ProgrammeAssets.selectLevelMusic(
                    playerFrame.environment().movement().gates().zoneAct(),rawPeriod,timeAttack,program.goodFutureByte()));
        }
        @Override public void serviceVInt(SonicCdAct3FutureWorldHost host) {
            if(host!=act3FutureHost)throw new IllegalArgumentException("Foreign R13 V-int host");
            requireAct3FutureLoadingVisit(act3FutureLoading);
            EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
        }
        @Override public Optional<SonicCdAct3FutureObjectPass.ResultsFlagsImage> resultsFlags(
                SonicCdAct3FutureWorldHost host,SonicCdAct3FutureObjectPass pass) {
            if(host!=act3FutureHost||act3FutureWorld==null)
                throw new IllegalStateException("No actual R13 Module flags owner");
            requireAct3FutureLoadingVisit(act3FutureLoading);act3FutureWorld.requireObjectPass(pass);
            if(!pass.isCurrentEndOwnersRead())throw new IllegalStateException("No original end-object flags read");
            var p=Objects.requireNonNull(levelProgram,"Actual Module programme owner");
            return Optional.of(new SonicCdAct3FutureObjectPass.ResultsFlagsImage(p,p.goodFutureFlags()));
        }
        @Override public SonicCdAct3FutureWorldHost.ResultsFlagsStore prepareResultsFlags(
                SonicCdAct3FutureWorldHost host,SonicCdAct3FutureObjectPass pass,
                SonicCdAct3FutureObjectPass.Adoption actual,
                SonicCdAct3FutureObjectPass.ResultsFlagsImage expected,int value) {
            if(host!=act3FutureHost||act3FutureWorld==null)
                throw new IllegalStateException("No actual R13 Module flags publication");
            requireAct3FutureLoadingVisit(act3FutureLoading);act3FutureWorld.requireObjectPass(pass);
            Objects.requireNonNull(actual);Objects.requireNonNull(expected);
            var before=Objects.requireNonNull(levelProgram);
            if((value&~255)!=0||!pass.isCurrentEndFlagsPublication(actual)
                    ||actual.endExpected().flags().orElseThrow()!=expected||expected.fence()!=before
                    ||!expected.value().equals(before.goodFutureFlags())
                    ||actual.endNext().flagsByte().orElseThrow()!=value)
                throw new IllegalArgumentException("Not the original complete Module flags pair");
            // CLR.B establishes known zero without reading an unknown old byte. Only this
            // original pending native publication supplies authority; values are not commands.
            var after=new LevelProgramState(before.demoMode(),before.timeStones(),before.specialStage(),
                    before.usePlayer2(),before.palFadeFlags(),before.gameMode(),before.levelStarted(),
                    before.whiteFadeSteps(),before.phase(),OptionalInt.of(value));
            return new SonicCdAct3FutureWorldHost.ResultsFlagsStore() {
                private boolean applied;
                @Override public void validate() {
                    if(applied||levelProgram!=before||!pass.isCurrentEndFlagsPublication(actual))
                        throw new IllegalStateException("Original Module flags publication expired");
                    requireAct3FutureLoadingVisit(act3FutureLoading);act3FutureWorld.requireObjectPass(pass);
                }
                @Override public void adoptAfterNative() {
                    // All owners were preflighted before native stores. The publication is now
                    // applied, so its before-native predicate must not be re-entered here.
                    if(applied||levelProgram!=before)throw new IllegalStateException("Module flags pair changed");
                    levelProgram=after;applied=true;
                }
            };
        }
        // BEGIN ORIGINAL IPX MODULE RECEIPT 20261107
        @Override public Optional<SonicCdAct3FutureWorldHost.IpxProgressStore> prepareIpxBossReturn(
                SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit.IpxBossReturnCall call){
            if(host!=act3FutureHost)throw new IllegalStateException("Foreign IPX progress Host");
            host.requireIpxBossAuthorityPreparation(call);requireAct3FutureLoadingVisit(act3FutureLoading);
            var core=act3FutureLoading;var before=ipxProgress;var programme=levelProgram;var services=programServices;var media=source;var lifetime=ipxProgressLifetime;
            if(call.state()!=core.state()||call.livesByte()!=host.globals().ring().lives())throw new IllegalStateException("Stale actual boss return operands");
            var result=CdIpxProgress.bossReturned(before.project(core.state().program().timeStonesByte()),call.livesByte());
            var ready=result.missingRead().isPresent()?before:IpxProgressFacts.from(result.progress()); // all fallible aggregates BEFORE any saved/checkpoint store
            return Optional.of(new SonicCdAct3FutureWorldHost.IpxProgressStore(){
                private boolean applied;
                @Override public CdIpxProgress.Result result(){return result;}
                @Override public void validate(){
                    if(applied||ipxProgress!=before||levelProgram!=programme||programServices!=services||source!=media||ipxProgressLifetime!=lifetime)
                        throw new IllegalStateException("Original complete Module progress preimage expired");
                    requireAct3FutureLoadingVisit(core);core.requireIpxProgressCandidate(host,call,this);
                }
                @Override public void adoptAfterNative(){
                    core.requireIpxProgressCommit(host,call,this);
                    if(applied||ipxProgress!=before||levelProgram!=programme||programServices!=services||source!=media||ipxProgressLifetime!=lifetime)
                        throw new IllegalStateException("Original prevalidated Module progress pair expired");
                    ipxProgress=ready;applied=true; // saved high first; no callback/read/allocation follows this assignment
                }
            });
        }
        @Override public OptionalInt runR1UnlockByte(SonicCdAct3FutureWorldHost host){
            if(host!=act3FutureHost)throw new IllegalStateException("Foreign returned RunR1 Host");
            requireAct3FutureLoadingVisit(act3FutureLoading);act3FutureLoading.requireRunR1UnlockRead(host);
            return ipxProgress.timeAttackUnlockByte(); // actual historical operand; empty is UNKNOWN, never zero
        }
        @Override public Optional<SonicCdAct3FutureWorldHost.IpxRunR1Store> prepareRunR1(
                SonicCdAct3FutureWorldHost host,SonicCdAct3FutureLoadingVisit.IpxRunR1Call call){
            if(host!=act3FutureHost)throw new IllegalStateException("Foreign RunR1 Host");
            host.requireRunR1AuthorityPreparation(call);requireAct3FutureLoadingVisit(act3FutureLoading);
            var core=act3FutureLoading;var before=ipxProgress;var programme=levelProgram;var services=programServices;var media=source;var lifetime=ipxProgressLifetime;
            if(call.state()!=core.state())throw new IllegalStateException("Stale original RunR1 Core cut");
            var result=CdIpxProgress.runR1AfterR13(before.project(core.state().program().timeStonesByte()),OptionalInt.of(call.state().program().goodFutureByte()));
            boolean headerWrite=result.stores().contains(CdIpxProgress.Store.TIME_ATTACK_UNLOCK_WRITE)||result.stores().contains(CdIpxProgress.Store.TITLE_BIT6_SET);
            var data=IpxProgressFacts.from(result.progress());
            var header=headerWrite?new IpxProgressFacts(data.savedLevel(),before.goodFuturesByte(),data.titleFlagsByte(),data.timeAttackUnlockByte(),data.unknownBackupByte(),data.currentSpecialStageByte(),data.saveDisabledByte()):before;
            var finalFacts=result.stores().contains(CdIpxProgress.Store.GOOD_FUTURES_BIT0_SET)?data:header;

            return Optional.of(new SonicCdAct3FutureWorldHost.IpxRunR1Store(){
                private int step;
                public CdIpxProgress.Result result(){return result;}
                public void validate(){
                    if(step!=0||ipxProgress!=before||levelProgram!=programme||programServices!=services||source!=media||ipxProgressLifetime!=lifetime)throw new IllegalStateException("RunR1 complete Module preimage expired");
                    requireAct3FutureLoadingVisit(core);core.requireRunR1ProgressCandidate(host,call,this);
                }
                private void commit(int expected,IpxProgressFacts facts,LevelProgramState program){
                    core.requireRunR1ProgressCommit(host,call,this);
                    if(step!=expected||ipxProgress!=facts||levelProgram!=program||programServices!=services||source!=media||ipxProgressLifetime!=lifetime)throw new IllegalStateException("RunR1 prevalidated Module suffix expired");
                }
                public void adoptHeader(){commit(0,before,programme);ipxProgress=header;step=1;}
                public void adoptHistory(){commit(1,header,programme);ipxProgress=finalFacts;step=2;}
            });
        }
        // END ORIGINAL IPX MODULE RECEIPT 20261107

        @Override public void resetGoodFutureFlags(SonicCdAct3FutureWorldHost host) {
            if(host!=act3FutureHost)throw new IllegalArgumentException("Foreign R13 first-load host");
            requireAct3FutureLoadingVisit(act3FutureLoading);var p=levelProgram;
            // Only Host's actual first-load native reset calls this private retained authority.
            levelProgram=new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),p.palFadeFlags(),
                    p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),OptionalInt.of(0));
        }
    };
    void installAct3FutureLoadingVisit() {
        validateAct3FutureLoadRequest(stagedAct3FutureContext);
        if(playerFrame!=null||playerActor!=null||openingObjects!=null||pastLoading!=null||act2Loading!=null
                ||act2PastLoading!=null||act3FutureLoading!=null)
            throw new IllegalStateException("Retire old programme before actual R13 assembly");
        var c=stagedAct3Future;playerMode=SessionManager.getCurrentGameplayMode();
        if(!(playerMode.getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct3FutureLevel level)
                ||level.assets()!=c.assets())throw new IllegalArgumentException("Foreign prepared R13 terrain");
        try {
            var loaded=SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(),source.timeWarpPrograms().orElseThrow());
            programServices=withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM,loaded,programServices.initialization());
        } catch(SonicCdProgramLoadServices.Unsupported stopped) {
            programServices=new ProgramServicesState(stopped.completedPrefix(),programServices.initialization(),
                    programServices.environment(),programServices.completedCalls());throw stopped;
        }
        if(CdProgramInitialization.selectStartBranch(programServices.initialization())!=CdProgramInitialization.Branch.GAME_INIT)
            throw SonicCd.unsupported("R13 Start did not select GameInit");
        // RunMMD clears WORKRAMFILE..FFFB00, including primary, art CPU buffer and camera storage.
        // MAINVARS (clock, sectionID, copied cameras, scroll speeds and fade target) remains carried.
        var emptyArt=new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES],0,false);
        var current=com.openggf.game.soniccd.resources.CdR13ArtResidency.unknownForNativeHud(c.assets());
        var video=playerPresentation.initializeProgramVideo(current,emptyArt);
        programServices=withProgramCall(ProgramStartCall.INIT_VDP,programServices.loads(),programServices.initialization());
        programServices=withProgramCall(ProgramStartCall.INIT_CONTROLLERS,programServices.loads(),
                CdProgramInitialization.initializeControllers(programServices.initialization()));
        var old=c.environment();var g=old.globals();var m=old.movement();var gate=m.gates();var mg=m.globals();
        var environment=new CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
                new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(gate.zoneAct(),gate.timeZone(),gate.miniSonic(),0,0,gate.speedShoes(),0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(0,0,0,0,0,0,
                        mg.debugAngle(),mg.debugAngleShift(),mg.debugQuadrant(),mg.debugFloorDistance())),
                new CdPlayerBody.Controls(0,0,0,0,0),
                new CdPlayerBody.Globals(g.levelFrames(),0,g.timeAttackMode(),g.debugMode(),g.debugCheat(),0,g.invincible(),g.bossMusic()),old.dynamicSlots());
        var state=new CdPlayerBody.State(new com.openggf.game.soniccd.player.CdPlayerMotionState(
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),
                new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),
                new CdPlayerBody.History(zeroPast(256),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
        playerFrame=CdPlayerFrameRuntime.fromAct3FutureEntry(c.assets(),state,environment,video.playerArt(),c.clock());
        var fg=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0);
        var bg=new CdR11aBackgroundScroll.State(0,0,0,0,0,0,zeroPast(8),0,0,0,
                c.scroll().background().cloudRam(),zeroPast(512),zeroPast(1024),0);
        var copies=c.scroll().copies();
        var scroll=new SonicCdAct3FutureLoadingVisit.Scroll(fg,bg,new SonicCdAct3FutureLoadingVisit.Copies(
                copies.fgX16_16(),copies.fgY16_16(),copies.bg(),copies.fgFlags(),copies.bgFlags()),0,0);
        var oldGlobals=c.globals();
        var tail=oldGlobals.tail().map(t->new SonicCdAct3FutureWorldHost.Tail(t.player2ControlsWord(),t.player2BoredWord(),
                t.unknownLevelByte(),t.demoS1IndexWord(),t.paletteFadeInfoWord()));
        var globals=new SonicCdAct3FutureWorldHost.Globals(oldGlobals.ring(),oldGlobals.live(),oldGlobals.contact(),oldGlobals.score(),
                OptionalInt.of(oldGlobals.projectorByte()),oldGlobals.checkpointSaved(),oldGlobals.drownTimerWord(),oldGlobals.debugBlockWord(),
                oldGlobals.pauseMusicBit(),oldGlobals.bonus(),tail,oldGlobals.animations(),oldGlobals.paletteSteps7(),oldGlobals.paletteTimers7(),
                OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty());
        var selected=new com.openggf.graphics.RetainedPlaneSceneRenderer.Scene(null,null,new int[256],0,
                video.selection().displayedPalette().copy(),false,com.openggf.graphics.SpritePresentation.Frame.empty(),java.util.Map.of(),0x20);
        act3FutureLevel=level;act3FutureLoadGeneration=Math.incrementExact(c.generation());
        act3SelectedGoodFuture=c.core().program().goodFutureByte();levelProgram=c.program();
        act3FutureArt=com.openggf.game.soniccd.resources.CdR13SectionArt.fromAssets(c.assets());
        act3ProgrammeAssets=com.openggf.game.soniccd.level.CdR13ProgrammeAssets.fromAssets(c.assets());
        var section=act3FutureArt.fromValues(zeroPast(512),video.currentPatterns(),c.core().section().sectionWord(),
                com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),java.util.Optional.empty(),
                new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                        com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
        var p=c.core().program();
        var program=new SonicCdAct3FutureLoadingVisit.Program(p.demoModeWord(),p.timeStonesByte(),p.goodFutureByte(),p.usePlayer2Byte(),
                p.specialStageByte(),p.palFadeFlagsByte(),programServices.initialization().palClearFlagBit0(),p.gameModeByte(),p.levelStartedByte(),p.demoDataIndexWord());
        constructingAct3Future=true;
        try {
            act3FutureHost=new SonicCdAct3FutureWorldHost(act3Authority,c.assets(),playerFrame,act3FutureArt,
                    new SonicCdAct3FutureWorldHost.Initial(globals,scroll,video.selection().backgroundPlane(),video.selection().backgroundPlane(),
                            new SonicCdAct3FutureWorldHost.Video(zeroPast(640),java.util.Optional.empty(),video.playerArt(),video.currentPatterns(),
                                    new SonicCdAct3FutureWorldHost.Carried(selected)),c.savedFlags()));
            act3FutureLoading=new SonicCdAct3FutureLoadingVisit(act3FutureHost,c.assets(),playerFrame,act3FutureArt,
                    new SonicCdAct3FutureLoadingVisit.Entry(section,program,c.core().level(),c.core().normalPalette(),c.core().fadePalette(),
                            zeroPast(128),video.selection().displayedPalette(),zeroPast(1984),false));
            act3FutureHost.install(act3FutureLoading);
            act3FutureWorld=new CdWorld(act3Authority,act3FutureLoading,act3FutureHost,playerFrame,c.assets(),act3FutureArt,
                    com.openggf.game.soniccd.level.CdR13ObjectAssets.fromAssets(c.assets()),
                    com.openggf.game.soniccd.level.CdR13PlayerCollisionAssets.fromAssets(c.assets()));
            act3FutureHost.bindWorld(act3FutureWorld);act3FutureLoading.bindOrdinaryOwner(act3FutureHost,act3FutureWorld);
            act3FutureLoading.begin();act3FutureHost.projectCamera(GameServices.camera());
        } finally {constructingAct3Future=false;}
    }


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
                                  ProgramServicesState services, OptionalInt goodFutureFlags, IpxProgressImage ipxProgress) { }
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
        validateAct2PastLoadingCut();act2PastLoading.requireServiceIdle();
        if((held&~255)!=0)throw new IllegalArgumentException("Native held BYTE required");
        if(act2PastLoading.state().phase()==SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED
                &&act2PastHost.globals().ring().lives()!=0
                &&(playerFrame.environment().movement().gates().timeZone()&0x80)==0
                &&act2PastLoading.state().program().specialStageByte()==0) {
            loadActualAct3FutureWorld();return;
        }
        try {
            act2PastLoading.service(held);act2PastHost.projectCamera(GameServices.camera());
        } catch (RuntimeException | Error failure) {
            boolean returned=failure instanceof SonicCdAct2PastLoadingVisit.UnsupportedLoading stopped
                    &&act2PastLoading.state().phase()==SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED
                    &&!act2PastLoading.failed()&&(stopped.boundary()==SonicCdAct2PastLoadingVisit.Boundary.NEXT_ACT_PROGRAMME_ADMISSION
                    ||stopped.boundary()==SonicCdAct2PastLoadingVisit.Boundary.IPX_LEVEL_GAME_OVER
                    ||stopped.boundary()==SonicCdAct2PastLoadingVisit.Boundary.IPX_LEVEL_TIME_WARP
                    ||stopped.boundary()==SonicCdAct2PastLoadingVisit.Boundary.IPX_LEVEL_SPECIAL_STAGE);
            if(!returned)closePlayerCycleAfterFailure(failure);throw failure;
        }
    }
    /** Narrow first-load flags receipt. The private actual Core stack supplies authority. */
    private PreparedAct2PastOldExitReset pendingAct2PastOldExitReset;
    static final class PreparedAct2PastOldExitReset {
        private final SonicCdGameModule owner;private final SonicCdAct2PastLoadingVisit visit;private boolean applied;
        private final LevelProgramState before,after;
        private PreparedAct2PastOldExitReset(SonicCdGameModule owner,SonicCdAct2PastLoadingVisit visit,LevelProgramState before,LevelProgramState after){this.owner=owner;this.visit=visit;this.before=before;this.after=after;}
    }
    PreparedAct2PastOldExitReset prepareAct2PastOldExitReset(SonicCdAct2PastLoadingVisit visit) {
        requireAct2PastLoadingVisit(visit);visit.requireOldExitStore();var p=levelProgram;
        if((visit.state().level().plcLoadFlags()&1)!=0||pendingAct2PastOldExitReset!=null)throw new IllegalStateException("No actual first-load old0 BSET");
        var ready=new PreparedAct2PastOldExitReset(this,visit,p,new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),
                p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),OptionalInt.of(0)));
        pendingAct2PastOldExitReset=ready;return ready;
    }
    void validateAct2PastOldExitReset(PreparedAct2PastOldExitReset ready) {
        Objects.requireNonNull(ready);requireAct2PastLoadingVisit(ready.visit);ready.visit.requireOldExitStore();
        if(ready.owner!=this||pendingAct2PastOldExitReset!=ready||ready.applied||ready.before!=levelProgram)
            throw new IllegalStateException("Stale/foreign old-exit reset receipt");
    }
    void applyAct2PastOldExitReset(PreparedAct2PastOldExitReset ready) {
        validateAct2PastOldExitReset(ready);levelProgram=ready.after;ready.applied=true;pendingAct2PastOldExitReset=null;
    }
    /** Ephemeral retirement only: no image/value can become current through cleanup. */
    void retireAct2PastOldExitReset(PreparedAct2PastOldExitReset ready) {if(pendingAct2PastOldExitReset==ready)pendingAct2PastOldExitReset=null;}
    int act2PastOldProgrammeReturnCode() {
        requireAct2PastLoadingVisit(act2PastLoading);var phase=act2PastLoading.state().phase();
        if(phase!=SonicCdAct2PastLoadingVisit.Phase.OLD_RETURNED&&phase!=SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED)
            throw new IllegalStateException("No completed old R12B MMD return");
        return CdRetainedProgrammeReturn.clearedPlcReturnByte(); // Existing semantic result, no CPU register owner.
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
                                     LevelProgramState program, CdWarpScene.State warp, List<ProgramSound> sounds, IpxProgressImage ipxProgress) {
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
    sealed interface PastSound permits PastIntroFm, PastIntroSubCpu, PastCcFm, PastKamaSickleFm, PastTentouExplosionFm, PastCheckpointFm, PastFrameSound, PastSubCpuSound, PastRingFm, PastRingSubCpu, PastSpringFm, PastTunnelDoorFm, PastMonitorFm, PastTimePostSubCpu, PastTamabbohFm, PastCollapsePlatformFm, PastMosquiFm, PastLostRingFm, PastRobotGeneratorFm, PastProjectorFm, PastSignpostSound, PastBigRingFm, PastResultsSound, PastProgrammeSound, PastWallFm, PastTunnelPathFm, PastMonitorItemShieldFm, PastMonitorItemRing, PastTunnelDoorSplashFm, PastDiscFm, PastVerticalDoorFm, PastSpeedShoesSubCpu, PastLevelMusic, PastR13BossFm, PastR13BossSubCpu, PastR13CapsuleExplosion, PastR13CapsuleSubCpu, PastGameOverSubCpu, PastBumperSubCpu, PastMechanismHoleFm, PastTtSwitchFm, PastTtWaterAirFm { }
    record PastR13CapsuleExplosion() implements PastSound { }
    record PastR13CapsuleSubCpu(com.openggf.game.soniccd.objects.CdR13Capsule.CpuCommand command) implements PastSound {
        PastR13CapsuleSubCpu { Objects.requireNonNull(command); }
    }
    record PastR13BossFm(com.openggf.game.soniccd.objects.CdR13Boss.FmSound sound) implements PastSound {
        PastR13BossFm { Objects.requireNonNull(sound); }
    }
    record PastR13BossSubCpu(com.openggf.game.soniccd.objects.CdR13Boss.CpuCommand command) implements PastSound {
        PastR13BossSubCpu { Objects.requireNonNull(command); }
    }
    record PastSpeedShoesSubCpu(com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand command) implements PastSound {
        PastSpeedShoesSubCpu { Objects.requireNonNull(command); }
    }
    record PastLevelMusic(CdCommand command) implements PastSound {
        PastLevelMusic { Objects.requireNonNull(command); }
    }
    public record PastMonitorItemRing(CdMonitorItem.RingAwardSound sound) implements PastSound {
        public PastMonitorItemRing { Objects.requireNonNull(sound); }
    }
    // BEGIN R12B Disc/Door immutable FM alternatives; no operation/cut is retained.
    record PastMechanismHoleFm(CdSonicHole.FmSound sound) implements PastSound {
        PastMechanismHoleFm { Objects.requireNonNull(sound); }
    }
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
    // BEGIN R13 GAME OVER IMMUTABLE SUBCPU REQUEST 20261107
    record PastGameOverSubCpu(com.openggf.game.soniccd.objects.CdGameOver.SubCpuCommand command) implements PastSound {
        PastGameOverSubCpu { Objects.requireNonNull(command); }
    }
    // END R13 GAME OVER IMMUTABLE SUBCPU REQUEST 20261107
    /** Completed B9 ledger metadata only; private original pass publication owns the operation. */
    record PastKamaSickleFm() implements PastSound { }
    /** Original TentouBomb PlayFMSound FM_EXPLODE=$9E; request metadata only. */
    record PastTentouExplosionFm() implements PastSound { }
    record PastCcFm(int id) implements PastSound {PastCcFm{if(id!=0xB7)throw new IllegalArgumentException("Actual CC Spikes FM B7");}}
    record PastBumperSubCpu(int nativeId) implements PastSound {PastBumperSubCpu{if(nativeId!=0x7a&&nativeId!=0x7f)throw new IllegalArgumentException("Actual Bumper SubCPU");}}
    record PastIntroFm(int nativeId) implements PastSound {PastIntroFm{if(nativeId!=0xA3&&nativeId!=0xCA)throw new IllegalArgumentException("Actual intro FM");}}
    record PastIntroSubCpu(int nativeId) implements PastSound {PastIntroSubCpu{if(nativeId!=0x7D)throw new IllegalArgumentException("Actual intro SubCPU");}}
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
    /** Completed request metadata in the existing ledger, not playback/replay/owner authority.
     * Header/priority resolve through the actual authenticated MDINIT bank, including DF's
     * native adjacent-header priority byte; no invented 80th priority declaration. */
    record PastTtWaterAirFm(CdR4PlayerBubbles.FmSound sound,int headerAddress,int priorityByte) implements PastSound {
        PastTtWaterAirFm{Objects.requireNonNull(sound);if((headerAddress&~65535)!=0||(priorityByte&~255)!=0)throw new IllegalArgumentException("Native FM source operands");}
        int nativeId(){return sound.nativeId();}
    }
    record PastTtSwitchFm(int nativeId) implements PastSound {PastTtSwitchFm{if(nativeId!=0xBF)throw new IllegalArgumentException("Switch native FM_BF only");}}
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
                                     LevelProgramState program, IpxProgressImage ipxProgress) { }

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
        return CdRetainedProgrammeReturn.clearedPlcReturnByte();
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

    // BEGIN R12B BigRing read-only actual programme fence. Core.Program owns specialStage.
    LevelProgramState act2PastBigRingProgram(SonicCdAct2PastLoadingVisit core,SonicCdAct2PastObjectPass pass) {
        requireAct2PastLoadingVisit(core);core.requireBigRingOwner(pass);
        if(pass==null||!pass.isCurrentBigRingOwnersRead())throw new IllegalStateException("No actual R12B BigRing owner borrow");
        return Objects.requireNonNull(levelProgram);
    }
    // END R12B BigRing read-only actual programme fence.
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
        if(constructingOriginalR11){validateOriginalR11Assembly(r11AssemblyContext);if(levelIndex!=SonicCd.sourceLevelIndex(originalLoadingSource))throw new IllegalArgumentException("Original selected descriptor");try{return SonicCdLevel.fromLoadingSource(originalLoadingSource);}catch(IOException failure){throw new UncheckedIOException("Actual original selected geometry read failed",failure);}}
        if(stagedR31!=null){
            validateR31LoadRequest(stagedR31Context);
            if(levelIndex!=r31Zones.loadingIndex())throw SonicCd.unsupported("R31 descriptor index");
            try{return new com.openggf.game.soniccd.level.SonicCdR31aLevel(stagedR31);}
            catch(IOException failure){throw new UncheckedIOException("Original R31 geometry read failed",failure);}
        }
        if (stagedAct3Future != null) {
            validateAct3FutureLoadRequest(stagedAct3FutureContext);
            if(levelIndex!=2)throw SonicCd.unsupported("R13 level index");
            return new com.openggf.game.soniccd.level.SonicCdAct3FutureLevel(stagedAct3Future.assets());
        }
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
        var environment = CdOriginalProgrammeWorkClear.environment(c.environment());
        var state = CdOriginalProgrammeWorkClear.primary();
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
        INIT_CONTROLLERS, DECLARED_BEGIN_LEVEL_INGRESS, CLEAR_MAIN_VARIABLES, START_SCRATCH_CLEAR, GAME_MODE_LEVEL
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
        var servicesBefore=programServices;var progressBefore=ipxProgress;var lifetimeBefore=ipxProgressLifetime;
        var cleared = CdProgramInitialization.clearMainVariables(servicesBefore.initialization());
        var progressReady=IpxProgressFacts.from(CdIpxProgress.mainVariablesCleared());
        var servicesReady=withProgramCall(ProgramStartCall.CLEAR_MAIN_VARIABLES, servicesBefore.loads(), cleared);
        // Genuine authenticated once-only clear, before the following fallible PCM source read.
        // Prebuild both existing owners; never retrofit a current level/capture or mint a new lifetime.
        if(programServices!=servicesBefore||ipxProgress!=progressBefore||ipxProgressLifetime!=lifetimeBefore
                ||programWorld!=world)throw new IllegalStateException("Named MAINVARS complete preimage changed");
        ipxProgress=progressReady;programServices=servicesReady;
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
        if(originalBoot!=null){validateOriginalBootServices(value);return;}
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

    enum LevelProgramPhase { LEVEL, WHITE_FADE, RETURNED, WARP, PAST_PENDING, PAST_LOADING, OLD_EXIT_PRELUDE, OLD_BLACK_FADE, OLD_CLEAR_WAIT, OLD_RETURNED, OLD_IPX_RETURNED, ACT2_LOADING, ACT2_PAST_LOADING, ACT3_FUTURE_LOADING, R31_LOADING
    }
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
        if(r31Loading!=null){var s=r31Loading.state();var p=s.program();return new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),s.initialFadeSteps(),LevelProgramPhase.R31_LOADING,levelProgram.goodFutureFlags());}
        if (act3FutureLoading != null) {
            var state=act3FutureLoading.state();var p=state.program();
            return new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),
                    p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),act3FutureLoading.oldProgrammeSelected()?act3FutureLoading.oldExitSteps():state.initialFadeSteps(),
                    switch(state.phase()) {
                        case OLD_EXIT_PRELUDE -> LevelProgramPhase.OLD_EXIT_PRELUDE;
                        case OLD_BLACK_FADE -> LevelProgramPhase.OLD_BLACK_FADE;
                        case OLD_CLEAR_WAIT -> LevelProgramPhase.OLD_CLEAR_WAIT;
                        case OLD_RETURNED -> LevelProgramPhase.OLD_RETURNED;
                        case OLD_IPX_RETURNED, OLD_IPX_BOSS_RETURNED, OLD_IPX_RUN_R3_SAVE, OLD_IPX_RUN_R1_TITLE_STOP, OLD_IPX_RUN_R1_GOOD_FUTURES_STOP -> LevelProgramPhase.OLD_IPX_RETURNED;
                        default -> LevelProgramPhase.ACT3_FUTURE_LOADING;
                    },levelProgram.goodFutureFlags());
        }
        if (act2PastLoading != null) {
            var p = act2PastLoading.state().program();
            return new LevelProgramState(p.demoModeWord(),p.timeStonesByte(),p.specialStageByte(),p.usePlayer2Byte(),
                    p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),act2PastLoading.oldProgrammeSelected()?act2PastLoading.oldExitSteps():act2PastLoading.state().initialWhiteSteps(),
                    switch(act2PastLoading.state().phase()){
                        case OLD_EXIT_PRELUDE -> LevelProgramPhase.OLD_EXIT_PRELUDE;
                        case OLD_BLACK_FADE -> LevelProgramPhase.OLD_BLACK_FADE;
                        case OLD_CLEAR_WAIT -> LevelProgramPhase.OLD_CLEAR_WAIT;
                        case OLD_RETURNED -> LevelProgramPhase.OLD_RETURNED;
                        case OLD_IPX_RETURNED -> LevelProgramPhase.OLD_IPX_RETURNED;
                        default -> LevelProgramPhase.ACT2_PAST_LOADING;
                    },levelProgram.goodFutureFlags());
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
        bindWarpScene(requireRuntimeSource().timeWarpPrograms().orElseThrow(
                () -> new UnsupportedRestart(RestartBoundary.WARP_PROGRAM_START)), () -> { });
        var initial = warpScene.initial(0);
        var sounds = List.of(ProgramSound.STOP_FM);
        warpState = initial; programSounds = sounds;
        levelProgram = withProgramPhase(program, LevelProgramPhase.WARP);
        // No old Frame/ObjectManager V-int is serviced. Its owners remain inactive and capturable.
    }

    /** Shared finite binding only. Caller-supplied source/lifetime admission surrounds fallible IO.
     * Native WARP Start:128-133 clears its private counter; it never borrows the level LONG. */
    private void bindWarpScene(com.openggf.game.soniccd.media.CdTimeWarpPrograms retained,Runnable requireOwner) {
        Objects.requireNonNull(retained);Objects.requireNonNull(requireOwner).run();
        if(warpScene!=null){
            if(warpAssets==null||warpAssets.source()!=retained.source())throw new IllegalArgumentException("Different retained WARP source");
            requireOwner.run();return;
        }
        try {
            var assets=CdWarpAssets.fromPrograms(retained);requireOwner.run();
            var scene=new CdWarpScene(assets);scene.validate(scene.initial(0));requireOwner.run();
            if(warpScene!=null||warpAssets!=null)throw new IllegalStateException("WARP binding changed during finite IO");
            warpAssets=assets;warpScene=scene;
        }catch(IOException failure){throw new UncheckedIOException("Cannot bind original WARP programme",failure);}
    }
    /** The sole native scene-step/publication leaf for retained and original callers. */
    private void stepWarpScene(int rawHeldByte,Runnable requireOwner) {
        requireOwner.run();var before=Objects.requireNonNull(warpState);var scene=Objects.requireNonNull(warpScene);
        var sounds=programSounds;var result=scene.step(before,rawHeldByte);
        var expected=before.phase()==CdWarpScene.Phase.PREPARE?List.of(CdWarpScene.SoundRequest.FM_D8):List.<CdWarpScene.SoundRequest>of();
        if(!result.sounds().equals(expected))throw new IllegalStateException("WARP sound request left its actual source cut");
        var ready=expected.isEmpty()?sounds:List.of(ProgramSound.STOP_FM,ProgramSound.WARP_DIRECT_FM_D8);
        scene.validate(result.state());requireOwner.run();
        if(warpState!=before||warpScene!=scene||programSounds!=sounds)throw new IllegalStateException("Changed WARP step preimage");
        warpState=result.state();programSounds=ready;
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
            stepWarpScene(rawHeldByte,this::validateWarpProgrammeOwner);
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
                                      List<ProgramSound> sounds, ProgramServicesState services, IpxProgressImage ipxProgress) {
        private OpeningSharedState { sounds = List.copyOf(sounds); Objects.requireNonNull(services); }
    }
    private final NativeRewindRestoreGuard<Object> sharedOpeningAdapter = new NativeRewindRestoreGuard<>() {
        @Override public String key() { return OPENING_SHARED_REWIND_KEY; }
        @Override public Object capture() {
            if(act3FutureLoading!=null) {
                validateAct3FutureLoadingCut();validateProgramServicesState(programServices);
                var loading=act3FutureWorld.captureR13();
                return new Act3FutureShared(loading,programServices,levelProgram.goodFutureFlags(),captureIpxProgress(loading.state()));
            }
            if (act2PastLoading != null) {
                validateAct2PastLoadingCut(); validateProgramServicesState(programServices);
                var loading=act2PastLoading.capture();
                return new Act2PastShared(loading,programServices,levelProgram.goodFutureFlags(),captureIpxProgress(loading));
            }
            if (act2Loading != null) {
                validateAct2LoadingCut(); validateProgramServicesState(programServices);
                var loading=act2Loading.capture();
                return new Act2OpeningShared(loading, programServices, levelProgramState(), warpState, programSounds,captureIpxProgress(loading));
            }
            if (pastLoading != null) {
                validatePastLoadingCut(); validateProgramServicesState(programServices);
                var loading=pastLoading.capture();
                return new PastOpeningShared(loading, programServices, levelProgramState(),captureIpxProgress(loading));
            }
            requireTerrainPlayerReady();
            requireOpeningClosedCut();
            playerActor.requireCompletedNativeFrame();
            validateProgramServicesState(programServices);
            var shared=playerFrame.captureShared(); // Reject pending input before terminal Runtime capture.
            var objects=openingObjects.captureClosedCut();
            return new OpeningSharedState(shared, objects,
                    playerPresentation.captureScrollPublication(), levelProgramState(), warpState, programSounds, programServices,captureIpxProgress(objects));
        }
        @Override public void validateRestore(Object value) {
            if(act3FutureLoading!=null) {
                if(!(value instanceof Act3FutureShared future))throw new IllegalArgumentException("Foreign programme epoch in R13");
                validateAct3FutureLoadingCut();act3FutureWorld.validateRestore(future.loading());validateProgramServicesState(future.services());validateIpxProgress(future.ipxProgress(),future.loading().state());
                if((future.loading().state().phase()==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_BOSS_RETURNED||SonicCdAct3FutureLoadingVisit.runR1Closed(future.loading().state().phase()))
                        &&future.ipxProgress().facts().savedLevel().zoneByte().isEmpty())
                    throw new IllegalArgumentException("Returned boss capture requires its original known saved high BYTE");
                var phase=future.loading().state().phase();var progress=future.ipxProgress().facts();
                if(phase==SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R3_SAVE
                        &&(progress.timeAttackUnlockByte().isEmpty()||progress.timeAttackUnlockByte().getAsInt()<3||progress.titleFlagsByte().isEmpty()||(progress.titleFlagsByte().getAsInt()&0x60)!=0x60))throw new IllegalArgumentException("Captured RunR1 completion lacks original unlock/title prefix");
                if(future.goodFutureFlags().isPresent()&&(future.goodFutureFlags().getAsInt()&~255)!=0)
                    throw new IllegalArgumentException("Captured R13 flag exceeds BYTE");
                return;
            }
            if (act2PastLoading != null) {
                if (!(value instanceof Act2PastShared past)) throw new IllegalArgumentException("Foreign programme epoch in R12B");
                validateAct2PastLoadingCut(); act2PastLoading.validateRestore(past.loading()); validateProgramServicesState(past.services());validateIpxProgress(past.ipxProgress(),past.loading());
                // This private capture carries the actual mutable Results/first-load flag BYTE.
                // Core's same-owner Snapshot was validated above; public equal values mint no restore.
                if(past.goodFutureFlags().isPresent()&&(past.goodFutureFlags().getAsInt()&~255)!=0)
                    throw new IllegalArgumentException("Captured R12B flag exceeds BYTE");
                return;
            }
            if (act2Loading != null) {
                if (!(value instanceof Act2OpeningShared act2)) throw new IllegalArgumentException("Foreign programme epoch in Act2");
                act2Loading.validateRestore(act2.loading(), act2.program()); validateProgramServicesState(act2.services());validateIpxProgress(act2.ipxProgress(),act2.loading());
                validateWarpProgramCut(act2.program(), act2.warp(), act2.sounds());
                return;
            }
            if (pastLoading != null) {
                if (!(value instanceof PastOpeningShared past)) throw new IllegalArgumentException("Prior programme snapshot in Past epoch");
                pastLoading.validateRestore(past.loading(), past.program()); validateProgramServicesState(past.services());validateIpxProgress(past.ipxProgress(),past.loading());
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
            validateProgramServicesState(snapshot.services());validateIpxProgress(snapshot.ipxProgress(),snapshot.objects());
        }
        @Override public void restore(Object value) {
            validateRestore(value); // Keep direct adapter restore guarded as well as composite restore.
            if(value instanceof Act3FutureShared future) {
                var p=levelProgram;var ready=new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),
                        p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),future.goodFutureFlags());
                try{act3FutureWorld.restore(future.loading());levelProgram=ready;programServices=future.services();ipxProgress=future.ipxProgress().facts();}
                catch(RuntimeException|Error failure){closePlayerCycleAfterFailure(failure);throw failure;}
                return;
            }
            if (value instanceof Act2PastShared past) {
                var p=levelProgram;var ready=new LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),
                        p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),past.goodFutureFlags());
                try { act2PastLoading.restore(past.loading());levelProgram=ready;programServices = past.services();ipxProgress=past.ipxProgress().facts(); }
                catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
                return;
            }
            if (value instanceof Act2OpeningShared act2) {
                try {
                    levelProgram = act2.program(); act2Loading.restore(act2.loading(), act2.program()); programServices = act2.services();
                    warpState = act2.warp(); programSounds = act2.sounds();ipxProgress=act2.ipxProgress().facts();
                } catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
                return;
            }
            if (value instanceof PastOpeningShared past) {
                try {
                    // The outer preflight validated the whole pair before this first adoption.
                    // Restore the programme phase before its flags/scroll projections select admission.
                    levelProgram = past.program();
                    pastLoading.restore(past.loading(), past.program()); programServices = past.services();ipxProgress=past.ipxProgress().facts();
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
                ipxProgress=snapshot.ipxProgress().facts(); // original captured facts, no NewGame/entry/effects replay
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
        this(playerAdmission, openingObjectsAdmission, explicitEnvironment, null);
    }

    private SonicCdGameModule(boolean playerAdmission, boolean openingObjectsAdmission,
                              CdProgramInitialization.ExecutionEnvironment explicitEnvironment,
                              CdSelectedProfileVisit.Prepared selected) {
        this(playerAdmission,openingObjectsAdmission,explicitEnvironment,selected,null);
    }

    // BEGIN actual original boot constructor bridge 20261107. Legacy ctor4 always supplies null.
    private SonicCdGameModule(boolean playerAdmission,boolean openingObjectsAdmission,
                              CdProgramInitialization.ExecutionEnvironment explicitEnvironment,
                              CdSelectedProfileVisit.Prepared selected,CdOriginalBootPrefix boot) {
        originalBoot=boot;
        r11TimeWarpSaved=boot==null?null:boot.timeWarpSavedAfterMainClear();
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
        selectedProfileInvocation = selected == null ? null : new Object();
        selectedProfile = selected == null ? null : boot==null?new CdSelectedProfileVisit(this, selected, selectedProfileInvocation)
                :new CdSelectedProfileVisit(this,selected,selectedProfileInvocation,boot);
        if(boot!=null){
            source=boot.source();ipxProgress=IpxProgressFacts.from(boot.state().progress());
            programServices=new ProgramServicesState(boot.state().loads(),boot.state().initialization(),boot.environment(),List.of());
        }
    }
    // END actual original boot constructor bridge.

    // BEGIN actual selected-profile pre-level Module owner 20261107.
    /** Supplied engine-menu request only; this does not execute or certify native Title. */
    public enum ProfileEntry { NEW_GAME, LOAD_GAME }
    /** Actual storage outcome; only this factory can construct an attempt/fresh Module bundle. */
    public static final class SelectedProfileAttempt {
        private final CdSaveProfileBootstrap.Result result;private final SonicCdGameModule module;
        private SelectedProfileAttempt(CdSaveProfileBootstrap.Result result,SonicCdGameModule module){this.result=result;this.module=module;}
        public String outcome(){return result.disposition().name();}
        public Optional<SonicCdGameModule> module(){return Optional.ofNullable(module);}
        public Optional<Throwable> failure(){return result.failure();}
        public Optional<Throwable> quarantineFailure(){return result.quarantineFailure();}
        public Optional<Throwable> cleanupFailure(){return result.cleanupFailure();}
        public Optional<Boolean> persistentPublication(){return result.persistentPublication();}
        /** Opens only this genuine fresh prepared selection; no caller-supplied context DTO. */
        public GameplayModeContext openSession(){if(module==null)throw new IllegalStateException("No healthy selected Module");return module.selectedProfile.openSession(module,module.selectedProfileInvocation);}
    }
    /** Real strict selected-slot storage, original same-CUE source, and a fresh pre-level Module.
     * No existing Module is mutated; enum/attempt values grant no World or programme-entry authority. */
    public static SelectedProfileAttempt prepareSelectedProfile(CdGameDataSource source,
            com.openggf.game.save.SaveManager manager,int slot,ProfileEntry entry){
        var prepared=CdSelectedProfileVisit.prepare(source,manager,slot,entry);
        var module=prepared.healthy()?new SonicCdGameModule(true,true,null,prepared):null;
        return new SelectedProfileAttempt(prepared.result(),module);
    }
    /** Fresh original boot with independently explicit execution-region bits; slot is a real user choice. */
    public static SelectedProfileAttempt prepareSelectedProfile(CdGameDataSource source,
            com.openggf.game.save.SaveManager manager,int slot,ProfileEntry entry,int executionVersionC0)throws IOException{
        var region=new CdProgramInitialization.ExecutionRegion(executionVersionC0);
        return prepareSelectedProfileFromOriginalBoot(source,manager,slot,entry,new CdProgramInitialization.ExecutionEnvironment(Optional.of(region)));
    }
    // BEGIN original pre-storage fresh factory/private continuation bridge 20261107.
    static SelectedProfileAttempt prepareSelectedProfileFromOriginalBoot(CdGameDataSource source,
            com.openggf.game.save.SaveManager manager,int slot,ProfileEntry entry,
            CdProgramInitialization.ExecutionEnvironment environment)throws IOException{
        var preparation=CdOriginalBootPrefix.prepare(source,manager,slot,entry,environment);
        var prepared=preparation.selected();
        var module=prepared.healthy()?new SonicCdGameModule(true,true,null,prepared,preparation.boot()):null;
        return new SelectedProfileAttempt(prepared.result(),module);
    }
    private void completeOriginalBootInitialization(CdSelectedProfileVisit.Publication original){
        originalBoot.completeInitializationReturn(this,original);
    }
    // Genuine original selected R11 Start and shared LevelStart prefix; first loading service remains fenced.
    // One reached prefix under the original selected lifetime, not another loading loop.
    // Main.WaitPLC:148-149 requires the privately staged original engine loading pair.
    record R11LoadingPrefix(CdOriginalBootPrefix.MainImage main,int goodFuture,int gameMode,int goodFutureFlags,
            CdRingGlobals ring,CdOpeningObjectRuntime.ScoreGlobals score,
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State section,
            CdAssetBytes normalPalette,CdAssetBytes fadePalette,List<CdLevelStart.Cut> calls,CdBlockDefinitionStaging blockDefinitions){
        R11LoadingPrefix{Objects.requireNonNull(blockDefinitions);Objects.requireNonNull(main);Objects.requireNonNull(ring);Objects.requireNonNull(score);Objects.requireNonNull(section);Objects.requireNonNull(normalPalette);Objects.requireNonNull(fadePalette);calls=List.copyOf(calls);}
        private R11LoadingPrefix blocks(CdBlockDefinitionStaging bank){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,score,section,normalPalette,fadePalette,calls,bank);}
        R11LoadingPrefix main(CdOriginalBootPrefix.MainImage m){return new R11LoadingPrefix(m,goodFuture,gameMode,goodFutureFlags,ring,score,section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix future(int b){return new R11LoadingPrefix(main,b,gameMode,goodFutureFlags,ring,score,section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix mode(int b){return new R11LoadingPrefix(main,goodFuture,b,goodFutureFlags,ring,score,section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix score(CdOpeningObjectRuntime.ScoreGlobals value){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,value,section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix freshSuffix(int lives){var r=ring;return new R11LoadingPrefix(main,goodFuture,gameMode,0,new CdRingGlobals(r.rings(),lives,r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),new CdOpeningObjectRuntime.ScoreGlobals(0,score.nextLifeScore(),score.updateHudScore()),section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix art(com.openggf.game.soniccd.resources.CdR11aSectionArt.State a){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,score,a,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix palette(CdAssetBytes p){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,score,section,p,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix palettes(CdAssetBytes n,CdAssetBytes f){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,score,section,n,f,calls,blockDefinitions);}
        R11LoadingPrefix level(CdLevelGlobals l){return main(new CdOriginalBootPrefix.MainImage(l,main.sectionWord(),main.demoModeWord(),main.usePlayer2Byte(),main.palFadeFlagsByte(),main.levelStartedByte()));}
        R11LoadingPrefix globals(SonicCdR31aWorldHost.Globals g){return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,g.ring(),g.score(),section,normalPalette,fadePalette,calls,blockDefinitions);}
        R11LoadingPrefix completed(CdLoadedLevelSetup.Ready ready){return new R11LoadingPrefix(new CdOriginalBootPrefix.MainImage(main.level(),main.sectionWord(),main.demoModeWord(),main.usePlayer2Byte(),main.palFadeFlagsByte(),ready.levelStartedByte()),goodFuture,ready.gameModeByte(),goodFutureFlags,ring,score,section,normalPalette,fadePalette,calls,blockDefinitions);}

        R11LoadingPrefix call(CdLevelStart.Cut cut){var ready=new java.util.ArrayList<>(calls);ready.add(cut);return new R11LoadingPrefix(main,goodFuture,gameMode,goodFutureFlags,ring,score,section,normalPalette,fadePalette,ready,blockDefinitions);}
    }
    // No live bank adopter: only the actual original clear or decoder call updates this value.
    private R11LoadingPrefix r11LoadingPrefix;
    private com.openggf.game.soniccd.level.CdLevelLoadingSource originalLoadingSource;
    // Authentication is bounded by one real service invocation; every primitive still
    // checks this exact source, live context, thread and lifetime. The retained MMD
    // owns a cloned private array with copy/read-only accessors.
    private com.openggf.game.soniccd.level.CdLevelLoadingSource r11ServiceLoadingSource;
    private CdSelectedProfileVisit.Programme originalLoadingProgramme;
    private CdSelectedProfileVisit.Publication originalLoadingPublication;
    private boolean originalSourceLoadingInstalled;
    private boolean originalWaterStage(){return originalLoadingSource!=null&&originalLoadingSource.completion()==com.openggf.game.soniccd.level.CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE;}
    private boolean ownsOriginalLoadingFrame(CdPlayerFrameRuntime actual){
        return actual!=null&&!actual.closed()&&originalLoadingSource!=null
            &&originalLoadingSource.ownsPlayerSource(actual.stageSource());
    }
    /** Partial knowledge only. Each Optional becomes known at its reached native store.
     * Current/selected/pending have single storage for Root's subsequent private water
     * binding; no capture can create invocation, and no unknown byte has a zero default. */
    record OriginalLoadingWater(OptionalInt waterYWord,OptionalInt staticYWord,OptionalInt targetYWord,
            OptionalInt routineByte,OptionalInt fullByte,OptionalInt speedByte,OptionalInt splitByte,
            OptionalInt pendingByte,Optional<CdAssetBytes> selectedPalette,boolean returnedPaletteLoaded,OptionalInt swayAngleByte){
        OriginalLoadingWater{for(var v:List.of(waterYWord,staticYWord,targetYWord))if(v.isPresent()&&(v.getAsInt()&~65535)!=0)throw new IllegalArgumentException("Water WORD");
            for(var v:List.of(routineByte,fullByte,speedByte,splitByte,pendingByte,swayAngleByte))if(v.isPresent()&&(v.getAsInt()&~255)!=0)throw new IllegalArgumentException("Water BYTE");
            Objects.requireNonNull(selectedPalette);selectedPalette.ifPresent(v->{if(v.length()!=128)throw new IllegalArgumentException("Selected water128");});}
        static OriginalLoadingWater unknown(){return new OriginalLoadingWater(OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),false,OptionalInt.empty());}
        private List<Object> scalars(){return List.of(waterYWord,staticYWord,targetYWord,routineByte,fullByte,speedByte,splitByte,pendingByte,returnedPaletteLoaded,swayAngleByte);}
        @Override public boolean equals(Object o){return this==o||o instanceof OriginalLoadingWater w&&scalars().equals(w.scalars())&&selectedPalette.isPresent()==w.selectedPalette.isPresent()&&(selectedPalette.isEmpty()||java.util.Arrays.equals(selectedPalette.orElseThrow().copy(),w.selectedPalette.orElseThrow().copy()));}
        @Override public int hashCode(){return Objects.hash(scalars(),selectedPalette.map(v->java.util.Arrays.hashCode(v.copy())).orElse(0));}
        OriginalLoadingWater store(int ordinal,int value){return new OriginalLoadingWater(ordinal==0?OptionalInt.of(value):waterYWord,ordinal==1?OptionalInt.of(value):staticYWord,ordinal==2?OptionalInt.of(value):targetYWord,ordinal==3?OptionalInt.of(value):routineByte,ordinal==4?OptionalInt.of(value):fullByte,ordinal==5?OptionalInt.of(value):speedByte,splitByte,pendingByte,selectedPalette,returnedPaletteLoaded,swayAngleByte);}
        OriginalLoadingWater returnedPalette(){return new OriginalLoadingWater(waterYWord,staticYWord,targetYWord,routineByte,fullByte,speedByte,splitByte,pendingByte,selectedPalette,true,swayAngleByte);}
    }
    record OriginalLoadingAnimation(CdAssetBytes frames,CdAssetBytes timers){
        OriginalLoadingAnimation{if(frames.length()!=6||timers.length()!=6)throw new IllegalArgumentException("Actual stage art frames/timers6");}
        OriginalLoadingAnimation store(boolean timer,int ordinal,int value){Objects.checkIndex(ordinal,6);if((value&~255)!=0)throw new IllegalArgumentException("Stage art BYTE");var bytes=(timer?timers:frames).copy();bytes[ordinal]=(byte)value;return timer?new OriginalLoadingAnimation(frames,CdAssetBytes.copyOf(bytes)):new OriginalLoadingAnimation(CdAssetBytes.copyOf(bytes),timers);}
        @Override public boolean equals(Object o){return this==o||o instanceof OriginalLoadingAnimation a&&java.util.Arrays.equals(frames.copy(),a.frames.copy())&&java.util.Arrays.equals(timers.copy(),a.timers.copy());}
        @Override public int hashCode(){return Objects.hash(java.util.Arrays.hashCode(frames.copy()),java.util.Arrays.hashCode(timers.copy()));}
    }
    private Optional<OriginalLoadingAnimation> originalLoadingAnimation=Optional.empty();
    enum OriginalScrollField { UNUSED_X,UNUSED_Y,UNUSED_DIE,UNUSED_TIMER,EVENT_ROUTINE,
        ROUTINE,TARGET_TOP,X_KEEP,LOOP_CHUNKS }
    // Absence is unknown. Actual InitScroll stores, never the source fact DTO, establish knowledge.
    private java.util.Map<OriginalScrollField,Integer> originalScrollFields=java.util.Map.of();
    private void storeOriginalScrollField(OriginalScrollField field,int value){
        requireOriginalR11Service();var before=originalScrollFields;
        var ready=new java.util.EnumMap<OriginalScrollField,Integer>(OriginalScrollField.class);ready.putAll(before);ready.put(field,value);
        requireOriginalR11Service();if(originalScrollFields!=before)throw new IllegalStateException("Changed InitScroll storage preimage");
        originalScrollFields=java.util.Collections.unmodifiableMap(ready);
    }
    private Optional<OriginalLoadingWater> originalLoadingWater=Optional.empty();
    Optional<OriginalLoadingWater> originalLoadingWaterImage(){requireOriginalR11EntryCut();return originalLoadingWater;}
    // One current water bank, already in R11EntryImage.waterPalette. Arm aliases section.hintWord;
    // water-fade aliases the actual CPU-SAT tail. No duplicate banks or default arm values.
    CdAssetBytes originalLoadingWaterPalette(){requireOriginalR11EntryCut();if(!originalWaterStage())throw new IllegalStateException("No water programme");return r11WaterPalette;}

    private CdObjectPlacementRuntime r11Placement;
    private com.openggf.game.soniccd.resources.CdR11aSectionArt r11LoadingArt;
    // Actual BRAM work-clear product, then only original Palette Fade/ClearScreen stores.
    private CdAssetBytes r11WaterPalette;
    private Object r11LevelLifetime;
    private com.openggf.game.soniccd.objects.r4.CdR4SwitchBank r11Switches;
    private int r11ClosureSpecialStage;
    private SonicCdR31aLoadingVisit.ReturnKind r11ReturnKind=SonicCdR31aLoadingVisit.ReturnKind.NONE;
    R11LoadingPrefix originalR11LoadingPrefix(){requireOriginalR11EntryCut();return Objects.requireNonNull(r11LoadingPrefix);}
    CdObjectPlacementRuntime.Snapshot originalR11PlacementImage(){requireOriginalR11EntryCut();return r11Placement.capture();}
    R11LoadingState originalR11LoadingImage(){requireOriginalR11EntryCut();return Objects.requireNonNull(r11LoadingState);}
    Optional<CdWorld.R11Snapshot> originalR11WorldImage(){requireOriginalR11EntryCut();return r11World==null?Optional.empty():Optional.of(r11World.captureR11());}
    private void enterOriginalLevelStart(CdSelectedProfileVisit.Publication original,Runnable guard){
        guard.run();
        var m=originalBoot.state().main().orElseThrow();var w=originalBoot.workClears();var g=r11SelectedFinal.globals().orElseThrow();var c=originalBoot.mainClears();var p=w.plc();
        var l=m.level();var level=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),g.checkpoint(),l.spawnMode(),g.plcLoadFlags().orElseThrow(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());
        var ring=w.globals().ring();var score=w.globals().score();
        r11WaterPalette=w.waterPalette();
        // NewGame has no lives store; retain the original clear until LevelStart freshSuffix writes it.
        try{r11LoadingArt=com.openggf.game.soniccd.resources.CdR11aSectionArt.fromLoadingSource(originalLoadingSource);}
        catch(IOException failure){throw new UncheckedIOException("Original R11 source assembly failed",failure);}
        // Exact original clear products, never devState(), preloaded object art or a new save lifetime.
        r11Placement=originalWaterStage()?CdObjectPlacementRuntime.fromPlacements(originalLoadingSource.placements(),c.savedFlags()):CdObjectPlacementRuntime.fromAssets(source.assets(),c.savedFlags());
        var art=r11LoadingArt.fromInactiveValues(programStartVideo.currentPatterns(),w.nemesisTable(),m.sectionWord(),p.queue(),p.saved(),p.remaining(),p.budget(),p.hint(),p.pad());
        var colours=originalBoot.state().cpuPalettes().orElseThrow();
        r11ClosureSpecialStage=r11SelectedFinal.globals().orElseThrow().specialStage();
        r11LoadingPrefix=new R11LoadingPrefix(new CdOriginalBootPrefix.MainImage(level,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte(),m.levelStartedByte()),g.goodFuture(),w.gameModeByte(),c.goodFutureFlags(),
                new CdRingGlobals(g.rings(),g.lives().orElse(ring.lives()),ring.livesFlags(),ring.updateHudRings(),ring.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame()),
                new CdOpeningObjectRuntime.ScoreGlobals(g.score(),score.nextLifeScore(),score.updateHudScore()),art,colours.normal(),colours.fade(),List.of(),originalBoot.state().blockDefinitions());
        originalR11LevelStartPrefix(r11PreparedFrame,guard);
        guard.run();
        originalR11LevelStartFade(r11PreparedFrame,true);
    }

    /** Both genuine callers reach the same existing LevelStart semantic driver. The private
     * guard is synchronous and subordinate to the original selected/Frame scope; no token escapes. */
    private void originalR11LevelStartPrefix(CdPlayerFrameRuntime actual,Runnable guard){
        CdLevelStart.enter(new CdLevelStart.Port(){
            private void require(){guard.run();if(!ownsOriginalLoadingFrame(actual))throw new IllegalStateException("No actual original R11 loading Frame");}
            public void clearDemo(){require();var m=r11LoadingPrefix.main();r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(m.level(),m.sectionWord(),0,m.usePlayer2Byte(),m.palFadeFlagsByte(),m.levelStartedByte())).call(CdLevelStart.Cut.CLEAR_DEMO);}
            public int timeStones(){require();return r11SelectedFinal.progress().timeStonesByte().orElseThrow();}
            public int timeAttack(){require();return actual.environment().globals().timeAttackMode();}
            public void forceGoodFuture(){require();r11LoadingPrefix=r11LoadingPrefix.future(1);}
            public void entryStores(){require();var m=r11LoadingPrefix.main();var ready=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(m.level(),m.sectionWord(),m.demoModeWord(),0,m.palFadeFlagsByte(),0)).call(CdLevelStart.Cut.ENTRY_STORES);r11LoadingPrefix=ready;actual.beginLevelStart();if(r11LevelLifetime==null)r11LevelLifetime=new Object(); /* capture owner is stable until actual World retirement */}
            public boolean setPlcLoaded(){require();var m=r11LoadingPrefix.main();var l=m.level();boolean loaded=(l.plcLoadFlags()&1)!=0;var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|1,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(next,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte(),m.levelStartedByte())).call(CdLevelStart.Cut.PLC_LOADED);return loaded;}
            public void freshPrefix(){require();var m=r11LoadingPrefix.main();var l=m.level();var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),0,l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());var score=r11LoadingPrefix.score();r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(next,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),0,m.levelStartedByte())).score(new CdOpeningObjectRuntime.ScoreGlobals(score.score(),5000,score.updateHudScore())).call(CdLevelStart.Cut.FRESH_PREFIX);}
            public void resetSavedFlags(){require();var ready=r11LoadingPrefix.call(CdLevelStart.Cut.RESET_SAVED_FLAGS);r11Placement.resetSavedObjectFlags();r11LoadingPrefix=ready;}
            public void freshSuffix(){require();var m=r11LoadingPrefix.main();var l=m.level();var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(next,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte(),m.levelStartedByte())).freshSuffix(3);if(timeAttack()!=0)r11LoadingPrefix=r11LoadingPrefix.freshSuffix(1);r11LoadingPrefix=r11LoadingPrefix.call(CdLevelStart.Cut.FRESH_SUFFIX);}
            public void markInitializing(){require();r11LoadingPrefix=r11LoadingPrefix.mode(r11LoadingPrefix.gameMode()|128).call(CdLevelStart.Cut.MARK_INITIALIZING);}
            public void clearPlcs(){require();r11LoadingPrefix=r11LoadingPrefix.art(r11LoadingArt.clearPlcs(r11LoadingPrefix.section())).call(CdLevelStart.Cut.CLEAR_PLCS);}
        });
    }
    private void originalR11LevelStartFade(CdPlayerFrameRuntime actual,boolean firstEntry){
        // One shared Main59-81 branch kernel; the genuine caller retains all source authority.
        if(r11ClosureSpecialStage!=0)throw SonicCd.unsupported("Original R11 FromSpecialStage FadeToWhite owner absent");
        if(!firstEntry)r11LoadingState=r11LoadingState.fade(0,r11LoadingState.displayEnabled());
        CdLevelStart.initialFade(new CdLevelStart.InitialFade(){
            public int specialStage(){return r11ClosureSpecialStage;}
            public int rawPeriod(){return actual.environment().movement().gates().timeZone();}
            public boolean setPaletteFadeFlag(){var m=r11LoadingPrefix.main();boolean old=(m.palFadeFlagsByte()&1)!=0;r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(m.level(),m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte()|1,m.levelStartedByte()));return old;}
            public void beginFade(boolean white){
                if(firstEntry)throw SonicCd.unsupported("Original R11 initial entry unexpectedly requires palette fade service");
                // Palette Fade.asm:274/105 precedes the first selector12 service.
                r11LoadingState=r11LoadingState.globals(r11LoadingState.globals().fadeInfo(0x003F));
                r11LoadingState=r11LoadingState.fade(0,r11LoadingState.displayEnabled()).phase(white?SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE:SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK);
            }
            public void skipped(boolean white){originalR11AfterInitialFade(actual,firstEntry,white,false);}
        });
    }
    private void originalR11AfterInitialFade(CdPlayerFrameRuntime actual,boolean firstEntry,boolean white,boolean completedFade){
        if(white&&CdLevelStart.afterWhite(completedFade,new CdLevelStart.WhiteReturn(){
            public void clearPaletteFadeFlag(){var m=r11LoadingPrefix.main();r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(m.level(),m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte()&~1,m.levelStartedByte()));}
            public void clearDirection(){actual.clearLevelStartWarpDirection();}
            public int restartWord(){return r11LoadingPrefix.main().level().levelRestart();}
            public void clearRestartWord(){var l=r11LoadingPrefix.main().level();r11LoadingPrefix=r11LoadingPrefix.level(new CdLevelGlobals(0,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));}
            public int actByte(){return actual.environment().movement().gates().zoneAct()&255;}
            public void clearPeriodMarker(){throw SonicCd.unsupported("Original R11A act0 does not own act3 marker clear");}
            public void returned(){
                if(firstEntry)throw SonicCd.unsupported("Original R11 native initial early-return continuation owner absent");
                r11ReturnKind=SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART;
                r11LoadingState=r11LoadingState.phase(SonicCdR31aLoadingVisit.Phase.RETURNED);
                issueOriginalWarpReturn();
            }
        }))return;
        var m=r11LoadingPrefix.main();var l=m.level();
        if(!white&&l.levelRestart()==2){
            l=new CdLevelGlobals(0,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());
            r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(l,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),0,m.levelStartedByte()));
            originalR11ClearReturn(SonicCdR31aLoadingVisit.ReturnKind.NEXT_LEVEL,firstEntry);return;
        }
        if(!white&&r11LoadingPrefix.ring().lives()==0){
            l=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),0,0,0,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());
            r11LoadingPrefix=r11LoadingPrefix.main(new CdOriginalBootPrefix.MainImage(l,m.sectionWord(),m.demoModeWord(),m.usePlayer2Byte(),0,m.levelStartedByte()));
            originalR11ClearReturn(SonicCdR31aLoadingVisit.ReturnKind.NO_LIVES,firstEntry);return;
        }
        if(originalBoot.workClears().demoDataIndexWord()==0x800)throw SonicCd.unsupported("Original R11 native demo return owner absent");
        boolean fill=CdProgramInitialization.requirePalClearFlagBit0(programServices.initialization())
                ||(actual.environment().movement().gates().timeZone()&128)!=0;
        r11LoadingPrefix=r11LoadingPrefix.palette(CdLevelStart.normalLoadPalette(fill)).call(CdLevelStart.Cut.NORMAL_PALETTE);
        if(!firstEntry)r11LoadingState=r11LoadingState.fade(0,r11LoadingState.displayEnabled()).phase(SonicCdR31aLoadingVisit.Phase.FIRST_PLC);
    }
    private void originalR11ClearReturn(SonicCdR31aLoadingVisit.ReturnKind kind,boolean firstEntry){
        if(firstEntry)throw SonicCd.unsupported("Original R11 native initial early-return continuation owner absent");
        r11LoadingPrefix=r11LoadingPrefix.palette(loadingZero(128));r11ReturnKind=kind;
        r11LoadingState=r11LoadingState.phase(SonicCdR31aLoadingVisit.Phase.CLEAR_RETURN_WAIT);
    }
    private void restartOriginalR11LevelStart(CdWorld world){
        requireOriginalR11Service();world.requireOriginalR11LevelStartOwner(this);
        if(r11LoadingPrefix.main().level().levelRestart()==0)throw new IllegalStateException("Actual R11 LevelStart branch not reached");
        world.abandonOriginalR11LevelStartWalk(this);r11ReturnKind=SonicCdR31aLoadingVisit.ReturnKind.NONE;
        originalR11LevelStartPrefix(playerFrame,this::requireOriginalR11Service);
        var g=r11LoadingState.globals();r11LoadingState=r11LoadingState.globals(SonicCdR31aWorldHost.with(g,r11LoadingPrefix.ring(),g.live(),g.contact(),r11LoadingPrefix.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));
        originalR11LevelStartFade(playerFrame,false);
    }
    private CdR11aCameraArtResidency.State originalR11LoadingUnknownPatterns(){
        try{return CdR11aCameraArtResidency.unknownForLoading(originalLoadingSource);}
        catch(IOException failure){throw new UncheckedIOException("Original R11 loading source binding",failure);}
    }
    record R11FirstLoad(CdOriginalR11Display.Selection selection,com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State foreground,
            com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State background,
            SonicCdR31aLoadingVisit.Copies copies,boolean levelLoaded,boolean standardLoaded,int completedWaits,
            List<CdCommand> music){R11FirstLoad{Objects.requireNonNull(selection);Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(copies);music=List.copyOf(music);}}
    static final class R11LoadingStopped extends UnsupportedOperationException{
        private final R11FirstLoad completed;private final R11LoadingPrefix prefix;private final R11LoadingState loading;
        private final CdPlayerFrameRuntime.ActorState actor;private final CdPlayerFrameRuntime.SharedState shared;
        private R11LoadingStopped(R11FirstLoad completed,R11LoadingPrefix prefix,R11LoadingState loading,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared){super("ORIGINAL_R11_ORDINARY_BINDING: Main330 follows actual BeginLevel; CdWorld R11 physical/reserved-object publication is not yet bound");this.completed=completed;this.prefix=prefix;this.loading=loading;this.actor=actor;this.shared=shared;}
        R11FirstLoad completed(){return completed;}R11LoadingPrefix prefix(){return prefix;}R11LoadingState loading(){return loading;}CdPlayerFrameRuntime.ActorState actor(){return actor;}CdPlayerFrameRuntime.SharedState shared(){return shared;}
    }
    private boolean constructingOriginalR11,r11EngineAssembled,r11ServiceActive;
    private LevelLoadContext r11AssemblyContext;private Level r11AssembledLevel;
    private R11FirstLoad r11FirstLoad;
    private R11LoadingState r11LoadingState;
    private CdWorld r11World;private boolean r11WorldConstructing;private OriginalR11Access r11OrdinaryAccess;

    boolean originalR11AssemblySelected(){return constructingOriginalR11||r11EngineAssembled;}
    public boolean originalR11EngineLoadingAssembled(){
        if(!r11EngineAssembled||r11Retired||r11ServiceActive||constructingOriginalR11||playerFrame==null||playerFrame.closed()||playerFrame!=r11EntryFrame||r11Thread!=Thread.currentThread()||SessionManager.getCurrentWorldSession()!=programWorld
                ||SessionManager.getCurrentGameplayMode()!=playerMode||r11EntryLevelManager==null||r11EntryLoadGeneration.isEmpty()
                ||r11EntryLevelManager.getCurrentLevel()!=r11EntryLevel||r11EntryLevelManager.getCompletedProductionLoadGeneration()!=r11EntryLoadGeneration.orElseThrow()
                ||!(r11EntryLevel instanceof SonicCdLevel level)||level.loadingSource()!=originalLoadingSource)return false;
        requireOriginalR11EntryCut();return true;
    }
    /** Only the genuine completed selected publication can return its installed source loading pair. */
    boolean originalSelectedSourceLoadingAssembled(CdSelectedProfileVisit.Publication original){
        if(original==null||original!=originalLoadingPublication||!originalSourceLoadingInstalled)return false;
        requireOriginalR11EntryCut();return true;
    }
    int selectedLoadingZone(){return originalR11AssemblySelected()?originalLoadingSource.nativeZoneActWord()>>>8:1;}
    int selectedLoadingIndex(){return originalR11AssemblySelected()?SonicCd.sourceLevelIndex(originalLoadingSource):r31Zones.loadingIndex();}
    void validateSelectedLoadingRequest(LevelLoadContext context){if(constructingOriginalR11)validateOriginalR11Assembly(context);else validateR31LoadRequest(context);}
    void prepareSelectedLoadingAssembly(LevelLoadContext context){validateSelectedLoadingRequest(context);if(!constructingOriginalR11)closeTerrainPlayer();}
    void installSelectedLoadingVisit(){if(constructingOriginalR11)installOriginalR11FirstLoad();else installR31LoadingVisit();}
    private void validateOriginalR11Assembly(LevelLoadContext context){
        var mode=SessionManager.getCurrentGameplayMode();var world=SessionManager.getCurrentWorldSession();
        if(!constructingOriginalR11||r11Retired||!r11StartComplete||context!=r11AssemblyContext||mode!=playerMode||world!=programWorld
            ||world==null||world.rootGameModule()!=this||world.resolvedGameModule()!=this||world.getDataSource()!=source
            ||mode.getLevelManager()!=r11EntryLevelManager||r11EntryLevelManager==null||r11ServiceActive
            ||r11Thread!=Thread.currentThread()||playerFrame!=r11EntryFrame||playerFrame.closed()
            ||context.getLevelIndex()!=SonicCd.sourceLevelIndex(originalLoadingSource)||context.getZone()!=(originalLoadingSource.nativeZoneActWord()>>>8)||context.getAct()!=0||context.getLoadMode()!=LevelLoadMode.FULL
            ||context.getAssemblyKind()!=LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY||context.getLoadCause()!=LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY
            ||!context.isIncludePostLoadAssembly()||context.hasCheckpoint()||context.getRom()!=null||context.isShowTitleCard()
            ||context.isTitleCardRequiredInHeadlessMode()||context.isQueueFreshLevelRuntimeArt())throw new IllegalStateException("No original R11 FULL assembly request");
        if(originalPastStage()){
            originalPastArrival.require();
            if(!originalDestinationEntering||r11FirstLoad==null||!originalSourceLoadingInstalled)
                throw new IllegalStateException("No actual private Past FULL occurrence");
        }
        originalBoot.requireConsumed(this,world,selectedProfileInvocation);playerFrame.captureActor();playerFrame.captureShared();
    }
    private void assembleOriginalR11(){
        if(originalDestinationEntering){Objects.requireNonNull(originalPastArrival).require();}
        else requireOriginalR11EntryCut();
        long before=r11EntryLevelManager.getCompletedProductionLoadGeneration();long expected=Math.incrementExact(before);
        var context=SonicCdLevelInitProfile.staticStageContext();context.setLevelIndex(SonicCd.sourceLevelIndex(originalLoadingSource));context.setZone(originalLoadingSource.nativeZoneActWord()>>>8);context.setAct(0);context.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);r11AssemblyContext=context;constructingOriginalR11=true;
        try{zones.bind(source,originalLoadingSource);r11EntryLevelManager.loadLevel(SonicCd.sourceLevelIndex(originalLoadingSource),LevelLoadMode.FULL,context);
            if(r11AssembledLevel==null||r11EntryLevelManager.getCurrentLevel()!=r11AssembledLevel||r11EntryLevelManager.getCompletedProductionLoadGeneration()!=expected)throw new IllegalStateException("Original R11 FULL generation did not commit");
            r11EntryLevel=r11AssembledLevel;r11EntryLoadGeneration=java.util.OptionalLong.of(expected);r11EngineAssembled=true;
        }catch(IOException failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw new UncheckedIOException("Original R11 assembly failed",failure);}
        catch(RuntimeException|Error failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        finally{constructingOriginalR11=false;r11AssemblyContext=null;}
        r11WorldConstructing=true;try{r11World=(originalWaterStage()||originalPastStage())?new CdWorld(this,playerFrame,originalLoadingSource,r11LoadingArt,r11Placement):new CdWorld(this,playerFrame,source.assets(),r11LoadingArt,r11Placement);}
        catch(IOException failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw new UncheckedIOException(failure);}
        catch(RuntimeException|Error failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        finally{r11WorldConstructing=false;}
    }
    private void installOriginalR11FirstLoad(){
        validateOriginalR11Assembly(r11AssemblyContext);var level=r11EntryLevelManager.getCurrentLevel();
        if(!(level instanceof SonicCdLevel actual)||actual.loadingSource()!=originalLoadingSource)throw new IllegalStateException("Foreign actual original programme geometry");
        originalLoadingSource.requirePlayerSource(playerFrame.stageSource());
        if(originalSourceLoadingInstalled){
            if(!(originalWaterStage()||originalPastStage())||r11FirstLoad==null||r11LoadingState==null)throw new IllegalStateException("Missing actual selected loading image");
        }else installOriginalLoadingImage();
        r11AssembledLevel=actual;
    }
    private void installOriginalLoadingImage(){
        var v=programStartVideo.selection();
        // InitVDP General111-155 actually cleared both physical name-table windows. The
        // existing video DTO's background is never relabelled as a foreground source.
        var fg=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(new byte[4096]));
        var input=new SonicCdPastDisplay.Inputs(Optional.empty(),v.displayedSat(),v.displayedPalette(),originalBoot.physicalClears().hscroll(),v.screenVScrollLongBits(),v.displayEnabled(),null,null,null,playerFrame.artState());
        if(v.displayEnabled())throw new IllegalStateException("Original InitVDP must retain disabled display");
        var selected=CdOriginalR11Display.project(new CdOriginalR11Display.Inputs(input.selectedCpu(),input.selectedSat(),input.palette(),input.hscroll(),input.vscrollLongBits(),false,fg,v.backgroundPlane(),r11LoadingPrefix.section().residency(),playerFrame.artState()));
        r11FirstLoad=new R11FirstLoad(selected,fg,v.backgroundPlane(),originalBoot.workClears().scroll().copies(),false,false,0,List.of());
        var g=originalBoot.workClears().globals();g=SonicCdR31aWorldHost.with(g,r11LoadingPrefix.ring(),g.live(),g.contact(),r11LoadingPrefix.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
        r11LoadingState=new R11LoadingState(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,originalBoot.workClears().scroll(),g,originalBoot.workClears().reserved(),CdFlowerPositions.unknown(),originalBoot.workClears().cpuSat(),Optional.empty(),Optional.empty(),originalBoot.workClears().nativeCarry(),originalBoot.mainClears().shieldCpu(),originalBoot.mainClears().noBumperByte(),0,0,false,List.of());
    }
    private void installOriginalSourceLoading(){
        if(!originalWaterStage()||!r11StartComplete||playerFrame!=r11EntryFrame||r11FirstLoad!=null||r11LoadingState!=null)
            throw new IllegalStateException("Original source loading already installed");
        installOriginalLoadingImage();originalSourceLoadingInstalled=true;
    }
    void validateOriginalR11Projection(){if(constructingOriginalR11)validateOriginalR11Assembly(r11AssemblyContext);else originalR11FirstLoad();}
    void validateOriginalR11ProjectionReset(){
        playerPresentation.requirePastRenderIdle();
        if(r11Thread!=Thread.currentThread()||r11ServiceActive)
            throw new IllegalStateException("Original R11 projection reset during another thread or loading service");
        // The provider owns no mutable projection to clear here. Teardown calls it after
        // the Frame and level manager retire; do not borrow a healthy native cut to dispose it.
        if(r11Retired)return;
        validateOriginalR11Projection();
    }

    private final com.openggf.level.scroll.ZoneScrollHandler r11ScrollProjection=new com.openggf.level.scroll.ZoneScrollHandler(){
        private final Object binding=new Object();
        private CdAssetBytes h(){validateOriginalR11Projection();return r11LoadingState.scroll().background().hscrollRam();}
        public void update(int[] output,int cameraX,int cameraY,int frameCounter,int actId){if(actId!=0)throw new IllegalArgumentException("Original R11 act0 projection");var bytes=h();Objects.requireNonNull(output);if(output.length>256)throw new IllegalArgumentException("Native256 HScroll LONGs");for(int i=0;i<output.length;i++)output[i]=(int)bytes.unsignedLong(i*4);}
        public short getVscrollFactorBG(){validateOriginalR11Projection();return(short)r11LoadingState.scroll().background().vscroll();}
        public short getVscrollFactorFG(){validateOriginalR11Projection();return(short)(r11LoadingState.scroll().background().vscroll()>>>16);}
        public int getMinScrollOffset(){return extreme(true);}public int getMaxScrollOffset(){return extreme(false);}
        private int extreme(boolean minimum){var bytes=h();int result=minimum?Integer.MAX_VALUE:Integer.MIN_VALUE;for(int i=0;i<224;i++){int word=(int)bytes.unsignedLong(i*4);int offset=(short)word-(short)(word>>>16);result=minimum?Math.min(result,offset):Math.max(result,offset);}return result;}
        public int getBgCameraX(){validateOriginalR11Projection();return(short)r11LoadingState.scroll().background().cameraBgXWord();}
        public int getBgPeriodWidth(){validateOriginalR11Projection();return 512;}
        public Object captureRewindState(){originalR11FirstLoad();return binding;}
        public void restoreRewindState(Object value){originalR11FirstLoad();if(value!=binding)throw new IllegalArgumentException("Foreign original R11 projection lifetime");}
    };
    com.openggf.level.scroll.ZoneScrollHandler originalR11ScrollProjection(){validateOriginalR11Projection();return r11ScrollProjection;}
    R11FirstLoad originalR11FirstLoad(){requireOriginalR11EntryCut();if((!r11EngineAssembled&&!originalSourceLoadingInstalled)||r11ServiceActive)throw new IllegalStateException("No idle original programme loading pair");return Objects.requireNonNull(r11FirstLoad);}
    /** Idle immutable DATA only; native marked periods80..82 remain the same retained Frame. */
    CdTimeWarpSaveData.State originalR11TimeWarpSavedImage(){
        originalR11FirstLoad();if(r11OrdinaryAccess!=null)throw new IllegalStateException("Original save operation is active");
        return Objects.requireNonNull(r11TimeWarpSaved,"No original warp-save owner");
    }
    Optional<com.openggf.game.soniccd.objects.r4.CdR4SwitchBank> originalR11SwitchesImage(){originalR11FirstLoad();return Optional.ofNullable(r11Switches);}
    void failOriginalR11LoadingDraw(CdOriginalR11Display.Selection expected,Throwable original){
        if(r11Thread!=Thread.currentThread()||r11ServiceActive||!r11EngineAssembled||playerFrame!=r11EntryFrame
            ||r11FirstLoad==null||r11FirstLoad.selection()!=expected){original.addSuppressed(new IllegalStateException("Original R11 render failure pair changed"));return;}
        r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(original);
    }
    private void bindOriginalR11FinishTimers(CdPlayerFrameRuntime actual){
        actual.bindFinishTimersOwner(new CdPlayerFrameRuntime.FinishTimersOwner(){
            private void require(CdPlayerFrameRuntime.FinishTimerCall call,boolean writing){
                call.requireCurrent(actual);requireOriginalR11Service();
                if(actual!=playerFrame||call.writing()!=writing||r11LoadingState==null)
                    throw new IllegalStateException("No actual original R11 Finish timer site");
            }
            public int readSecondary(CdPlayerFrameRuntime.FinishTimerCall call){
                require(call,false);return r11LoadingState.globals().tail()
                        .orElseThrow(CdPlayerFrameRuntime.UnownedSecondaryBoredTimer::new).player2BoredWord();
            }
            public void writeSecondary(CdPlayerFrameRuntime.FinishTimerCall call,int word){
                require(call,true);var before=r11LoadingState;var ready=before.globals(before.globals().secondaryBored(word));
                require(call,true);if(r11LoadingState!=before)throw new IllegalStateException("Changed actual timer Tail preimage");r11LoadingState=ready;
            }
        });
    }
    private void requireOriginalR11Service(){
        if(!r11ServiceActive||r11ServiceLoadingSource!=originalLoadingSource||!r11StartComplete||(!r11EngineAssembled&&!originalSourceLoadingInstalled)||r11Retired||r11Thread!=Thread.currentThread()
            ||SessionManager.getCurrentGameplayMode()!=playerMode||SessionManager.getCurrentWorldSession()!=programWorld
            ||programWorld.getDataSource()!=source||selectedProfile.state()!=r11SelectedFinal||r11SelectedFinal.programme().orElseThrow()!=originalLoadingProgramme||playerMode.getLevelManager()!=r11EntryLevelManager
            ||(r11EntryLevelManager!=null&&(r11EntryLevelManager.getCurrentLevel()!=r11EntryLevel||r11EntryLevelManager.getCompletedProductionLoadGeneration()!=r11EntryLoadGeneration.orElseThrow()))
            ||playerFrame!=r11EntryFrame||playerFrame.closed()||!ownsOriginalLoadingFrame(playerFrame))throw new IllegalStateException("No original active programme loading service");
        originalBoot.requireConsumed(this,programWorld,selectedProfileInvocation);
    }
    /** One lifetime-complete original loading image. Source/Frame/placement identities remain
     * in the enclosing original entry; this value never authorizes invocation or input. */
    record R11LoadingState(SonicCdR31aLoadingVisit.Phase phase,SonicCdR31aLoadingVisit.Scroll scroll,SonicCdR31aWorldHost.Globals globals,
            CdAssetBytes reserved,CdFlowerPositions flowers,CdAssetBytes cpuSat,Optional<CdAssetBytes> blocks,
            Optional<CdAssetBytes> layout,SonicCdR31aWorldHost.NativeCarry nativeCarry,CdAssetBytes shieldCpu,
            int noBumperByte,int bossStartedByte,int fadeSteps,boolean displayEnabled,List<CdLevelStartStorage.Cut> clears){
        R11LoadingState{Objects.requireNonNull(phase);Objects.requireNonNull(scroll);Objects.requireNonNull(globals);Objects.requireNonNull(flowers);Objects.requireNonNull(blocks);Objects.requireNonNull(layout);Objects.requireNonNull(nativeCarry);if(reserved.length()!=1984||cpuSat.length()!=640||shieldCpu.length()!=0x480||(noBumperByte&~255)!=0||(bossStartedByte&~255)!=0||fadeSteps<0||fadeSteps>22)throw new IllegalArgumentException("Complete original loading image required");clears=List.copyOf(clears);}
        R11LoadingState scroll(SonicCdR31aLoadingVisit.Scroll s){return new R11LoadingState(phase,s,globals,reserved,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState globals(SonicCdR31aWorldHost.Globals g){return new R11LoadingState(phase,scroll,g,reserved,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState reserved(CdAssetBytes r){return new R11LoadingState(phase,scroll,globals,r,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState geometry(Optional<CdAssetBytes> b,Optional<CdAssetBytes> l){return new R11LoadingState(phase,scroll,globals,reserved,flowers,cpuSat,b,l,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState phase(SonicCdR31aLoadingVisit.Phase p){return new R11LoadingState(p,scroll,globals,reserved,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState fade(int n,boolean e){return new R11LoadingState(phase,scroll,globals,reserved,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,n,e,clears);}
        R11LoadingState carry(SonicCdR31aWorldHost.NativeCarry n){return new R11LoadingState(phase,scroll,globals,reserved,flowers,cpuSat,blocks,layout,n,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,clears);}
        R11LoadingState cleared(CdLevelStartStorage.Cut cut){var c=new java.util.ArrayList<>(clears);c.add(cut);return new R11LoadingState(phase,scroll,globals,reserved,flowers,cpuSat,blocks,layout,nativeCarry,shieldCpu,noBumperByte,bossStartedByte,fadeSteps,displayEnabled,c);}
    }
    /** Private actual Main clear operation; only its constructor can request Flower source
     * knowledge, under the current source/World/thread/generation and complete preimage. */
    final class OriginalR11ClearCall {
        private final CdLevelStartStorage.Cut cut;private final R11LoadingState before=r11LoadingState;
        private final Thread thread=Thread.currentThread();private boolean preparing=true;
        private OriginalR11ClearCall(CdLevelStartStorage.Cut cut){this.cut=cut;}
        void requireDrawQueueOwner(SonicCdGameModule owner,CdWorld world){
            requireOriginalR11Service();if(owner!=SonicCdGameModule.this||world!=r11World||cut!=CdLevelStartStorage.Cut.DRAW_QUEUE||!preparing||thread!=Thread.currentThread()||r11LoadingState!=before||r11Clear!=this)throw new IllegalStateException("No original R11 Draw queue clear");
        }
        Object flowerPositionClearSource(){
            requireOriginalR11Service();if(cut!=CdLevelStartStorage.Cut.FLOWERS||!preparing||thread!=Thread.currentThread()||r11LoadingState!=before||r11Clear!=this)throw new IllegalStateException("No original R11 Flower clear");return (originalWaterStage()||originalPastStage())?originalLoadingSource:source.assets();
        }
    }
    private OriginalR11ClearCall r11Clear;
    private static CdAssetBytes loadingZero(int n){return CdAssetBytes.copyOf(new byte[n]);}
    private void storeR11Scroll(SonicCdR31aLoadingVisit.Scroll expected,SonicCdR31aLoadingVisit.Scroll ready){requireOriginalR11Service();if(r11LoadingState.scroll()!=expected)throw new IllegalStateException("Changed R11 scroll preimage");r11LoadingState=r11LoadingState.scroll(ready);}
    private void clearOriginalR11(CdLevelStartStorage.Cut cut){
        requireOriginalR11Service();if(r11Clear!=null)throw new IllegalStateException("Native clear reentry");
        var original=new OriginalR11ClearCall(cut);r11Clear=original;
        try{var before=r11LoadingState;var prefix=r11LoadingPrefix;var l=prefix.main().level();
            switch(cut){
                case POWERUP_FLOWERS -> r11LoadingPrefix=prefix.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),0,0,0,l.timeOver()));
                case DRAW_QUEUE -> {if(r11World!=null)r11World.originalR11QueueCleared(original);}
                case UNKNOWN_BUFFER -> { /* no addressable/readable buffer in this semantic view */ }
                case FLOWERS -> {
                    var positions=CdFlowerPositions.originalClear(original);var f=playerFrame.prepareLevelStartFlowersClear();
                    var ready=new R11LoadingState(before.phase(),CdLevelStartStorage.flowersCleared(before.scroll()),before.globals().flowers(),before.reserved(),positions,before.cpuSat(),before.blocks(),before.layout(),before.nativeCarry(),loadingZero(0x480),0,before.bossStartedByte(),before.fadeSteps(),before.displayEnabled(),before.clears());
                    playerFrame.validateLevelStartClear(f);requireOriginalR11Service();playerFrame.applyLevelStartClear(f);r11LoadingState=ready;
                }
                case OBJECTS -> {
                    var f=playerFrame.prepareLevelStartObjectClear();var staged=com.openggf.game.soniccd.objects.CdObjectPool.stagingCopy(r11Placement.capture().pool());staged.clearDynamicSlots();var pool=staged.capture();
                    var ready=before.reserved(loadingZero(1984));r11Placement.pool().validateRestore(pool);playerFrame.validateLevelStartClear(f);requireOriginalR11Service();
                    playerFrame.applyLevelStartClear(f);r11Placement.pool().restore(pool);r11LoadingState=ready;
                }
                case MISC -> {if(originalWaterStage())originalLoadingAnimation=Optional.of(new OriginalLoadingAnimation(loadingZero(6),loadingZero(6)));var n=before.nativeCarry();r11LoadingState=before.globals(before.globals().misc()).carry(new SonicCdR31aWorldHost.NativeCarry(n.collisionPointer(),n.fadeInfoWithoutTail(),java.util.OptionalLong.of(0),n.targetRightBoundWord(),n.amyCapturedByte()));}
                case CAMERA -> {
                    var switches=new com.openggf.game.soniccd.objects.r4.CdR4SwitchBank(new byte[32],-1);
                    var f=playerFrame.prepareLevelStartCameraClear();var n=before.nativeCarry();var ready=new R11LoadingState(before.phase(),CdLevelStartStorage.cameraCleared(before.scroll()),before.globals().camera(),before.reserved(),before.flowers(),before.cpuSat(),before.blocks(),before.layout(),new SonicCdR31aWorldHost.NativeCarry(n.collisionPointer(),n.fadeInfoWithoutTail(),n.rngSeedLong(),OptionalInt.of(0),n.amyCapturedByte()),before.shieldCpu(),before.noBumperByte(),0,before.fadeSteps(),before.displayEnabled(),before.clears());
                    playerFrame.validateLevelStartClear(f);requireOriginalR11Service();playerFrame.applyLevelStartClear(f);r11Placement.clearLevelStartCameraValues();
                    // Actual Main.ClearCamera FFF700..FFF800 contains switchFlags FFF7E0..FFF800.
                    // This private source/current-clear occurrence, never a DATA/default bank, owns all32 zero writes.
                    r11Switches=switches;r11LoadingState=ready;
                }
                case DEMO_INDEX -> { /* source DEMO=0, demoDataIndex was actually cleared by BRAM and is retained zero */ }
            }
            r11LoadingState=r11LoadingState.cleared(cut);r11LoadingPrefix=r11LoadingPrefix.globals(r11LoadingState.globals());
        }finally{original.preparing=false;r11Clear=null;}
    }
    private void projectOriginalR11Selection(CdOriginalR11Display.Inputs selected){
        requireOriginalR11Service();var old=r11FirstLoad;r11FirstLoad=new R11FirstLoad(CdOriginalR11Display.project(selected),old.foreground(),old.background(),old.copies(),old.levelLoaded(),old.standardLoaded(),old.completedWaits(),old.music());
    }
    void requireOriginalR11WorldConstruction(CdPlayerFrameRuntime frame,CdR11aAssets assets,com.openggf.game.soniccd.resources.CdR11aSectionArt art,CdObjectPlacementRuntime placement){
        requireOriginalR11EntryCut();if(!r11WorldConstructing||r11World!=null||frame!=playerFrame||assets!=source.assets()||art!=r11LoadingArt||placement!=r11Placement)throw new IllegalStateException("No actual original R11 World construction");
    }
    void requireOriginalProgrammeWorldConstruction(CdPlayerFrameRuntime frame,com.openggf.game.soniccd.level.CdLevelLoadingSource loading,com.openggf.game.soniccd.resources.CdR11aSectionArt art,CdObjectPlacementRuntime placement){
        requireOriginalR11EntryCut();if(!r11WorldConstructing||r11World!=null||!(originalWaterStage()||originalPastStage())||loading!=originalLoadingSource||frame!=playerFrame||frame.stageSource()!=loading.retainedPlayerSource()||art!=r11LoadingArt||!art.ownsLoadingSource(loading,frame.stageSource())||placement!=r11Placement||placement.placements()!=loading.placements())throw new IllegalStateException("No actual original source World construction");
        if(originalPastStage())originalPastArrival.require();
    }
    void requireOriginalR11WorldIdle(CdWorld world){requireOriginalR11EntryCut();if(world!=r11World||r11OrdinaryAccess!=null)throw new IllegalStateException("Foreign original R11 World");}
    void requireOriginalR11WorldRetiring(CdWorld world,CdPlayerFrameRuntime frame,CdR11aAssets assets){if(r11Thread!=Thread.currentThread()||r11ServiceActive||world!=r11World||frame!=r11EntryFrame||((originalWaterStage()||originalPastStage())?assets!=null:assets!=source.assets()))throw new IllegalStateException("Foreign retiring original R11 World");playerPresentation.requirePastRenderIdle();}
    void drawOriginalR11World(){playerPresentation.drawOriginalR11Loading();}
    void drawOriginalR11Selected(){originalR11FirstLoad();if(originalWarpSceneSelected()){playerPresentation.drawOriginalR11Loading();return;}if(r11World==null&&originalWaterStage()){playerPresentation.drawOriginalR11Loading();return;}if(r11World==null)throw new IllegalStateException("No original R11 World renderer");r11World.draw();}
    final class OriginalR11Access {
        private OriginalR11Access(){}
        void requireOwner(CdWorld expected){requireOriginalR11Service();if(r11OrdinaryAccess!=this||r11World!=expected||r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.READY)throw new IllegalStateException("No actual original R11 ordinary scope");}
        R11LoadingState state(){requireOwner(r11World);return r11LoadingState;}
        PastTtWaterAirFm prepareWaterAirFm(CdR4PlayerBubbles.FmCall original){
            requireOwner(r11World);r11World.requireOriginalTtWaterAirFm(SonicCdGameModule.this,original);
            if(!originalWaterStage()||originalBoot==null)throw new IllegalStateException("No original TT authenticated FM owner");
            var fm=source.fmSoundAssets().orElseThrow(()->new IllegalStateException("No original MDINIT FM source"));
            var installed=originalBoot.state().fm().orElseThrow(()->new IllegalStateException("Original FM bank installation not reached"));
            if(installed.assets()!=fm||fm.source().variant()!=CdReleaseVariant.USA
                    ||!fm.source().wholeDiscSha256().equals(source.assets().identity().observedWholeDiscSha256()))
                throw new IllegalStateException("Original TT request differs from its installed authenticated FM pipeline");
            var sound=original.sound();var request=new PastTtWaterAirFm(sound,fm.sfxHeaderAddress(sound.nativeId()),fm.nativePriorityByte(sound.nativeId()));
            requireOwner(r11World);r11World.requireOriginalTtWaterAirFm(SonicCdGameModule.this,original);return request;
        }
        CdTimeWarpSaveData.State timeWarpSaved(){requireOwner(r11World);return Objects.requireNonNull(r11TimeWarpSaved,"No original warp-save owner");}
        void timeWarpSaved(CdWorld.OriginalR11TimeWarpSaveStore original){
            requireOwner(r11World);Objects.requireNonNull(original,"actual shared-save occurrence");
            var before=Objects.requireNonNull(r11TimeWarpSaved,"No original warp-save owner");
            var expected=original.requireExpected(r11World,before);
            requireOwner(r11World);if(expected!=before||r11TimeWarpSaved!=before)throw new IllegalStateException("Changed original warp-save preimage");
            var ready=Objects.requireNonNull(original.ready(),"completed original store prefix");
            requireOwner(r11World);if(r11TimeWarpSaved!=before)throw new IllegalStateException("Changed original warp-save adoption cut");
            r11TimeWarpSaved=ready;
        }
        com.openggf.game.soniccd.objects.r4.CdR4SwitchBank switches(){requireOwner(r11World);return r11Switches;}
        int switchByte(int index){requireOwner(r11World);var bank=r11Switches;if(bank==null)throw new UnsupportedOperationException("Original current switch BYTE before actual camera clear");return bank.read(index);}
        /** Private active World publication only; bank exists solely after the genuine camera clear. */
        com.openggf.game.soniccd.objects.r4.CdR4SwitchBank switchByte(com.openggf.game.soniccd.objects.r4.CdR4SwitchBank expected,int index,int value){
            requireOwner(r11World);if(expected==null||r11Switches!=expected||(value&~255)!=0)throw new IllegalArgumentException("Changed current switch-bank store cut");
            expected.read(index);var ready=expected.write(index,value);requireOwner(r11World);if(r11Switches!=expected)throw new IllegalStateException("Changed switch adoption cut");r11Switches=ready;return ready;
        }
        R11LoadingPrefix prefix(){requireOwner(r11World);return r11LoadingPrefix;}
        void state(R11LoadingState before,R11LoadingState ready){requireOwner(r11World);if(r11LoadingState!=before)throw new IllegalStateException("Changed full original R11 cut");Objects.requireNonNull(ready);var prefix=r11LoadingPrefix.globals(ready.globals());r11LoadingState=ready;r11LoadingPrefix=prefix;}
        void level(CdLevelGlobals before,CdLevelGlobals ready){requireOwner(r11World);if(r11LoadingPrefix.main().level()!=before)throw new IllegalStateException("Changed original R11 level");r11LoadingPrefix=r11LoadingPrefix.level(ready);}
        void primaryLifecycle(R11LoadingState before,R11LoadingPrefix prefix,R11LoadingState ready,R11LoadingPrefix readyPrefix){
            requireOwner(r11World);if(r11LoadingState!=before||r11LoadingPrefix!=prefix||ready==null||readyPrefix==null
                ||!readyPrefix.ring().equals(ready.globals().ring())||!readyPrefix.score().equals(ready.globals().score()))throw new IllegalStateException("Changed complete original R11 lifecycle pair");
            r11LoadingState=ready;r11LoadingPrefix=readyPrefix;
        }
        void palette(CdAssetBytes ready){requireOwner(r11World);if(ready.length()!=128)throw new IllegalArgumentException("Palette128");r11LoadingPrefix=r11LoadingPrefix.palette(ready);}
        void section(com.openggf.game.soniccd.resources.CdR11aSectionArt.State before,com.openggf.game.soniccd.resources.CdR11aSectionArt.State ready){requireOwner(r11World);if(before!=r11LoadingPrefix.section())throw new IllegalStateException("Changed original R11 art");r11LoadingArt.validateState(ready);originalR11Art(a->ready);}
        int closureSpecialStage(){requireOwner(r11World);return r11ClosureSpecialStage;}
        int closureTimeStones(){requireOwner(r11World);return r11SelectedFinal.progress().timeStonesByte().orElseThrow();}
        void closureSpecialStage(int value){requireOwner(r11World);if(value!=1)throw new IllegalArgumentException("BigRing writes BYTE1 only");r11ClosureSpecialStage=value;}
        void beginLevelStart(CdWorld world){requireOwner(world);restartOriginalR11LevelStart(world);}
        void powerup(){requireOwner(r11World);copyOriginalR11Powerup();}
        void slow(){originalR11Art(a->r11LoadingArt.serviceSlow(a,r11LoadingState.scroll().foreground().scrollLock()));}
        void prepare(){originalR11Art(r11LoadingArt::prepare);}
        void updateSection(){originalR11Art(a->r11LoadingArt.updateSection(a,r11LoadingState.scroll().foreground().cameraX()));}
        void append(int id){originalR11Art(a->r11LoadingArt.appendList(a,id));}
        void copyDraw(){copyAndDrawOriginalR11();}
        void serviceCount(){requireOwner(r11World);EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();}
        void publish(java.util.Optional<CdObjectDrawQueue.CpuSat> cpu){requireOwner(r11World);var old=r11FirstLoad;var scroll=r11LoadingState.scroll();
            projectOriginalR11Selection(new CdOriginalR11Display.Inputs(cpu,r11LoadingState.cpuSat(),originalWaterStage()?originalLoadingWater.orElseThrow().selectedPalette().orElseThrow():r11LoadingPrefix.normalPalette(),CdSelectedHscroll.copyNativePrefix(old.selection().inputs().hscroll(),scroll.background().hscrollRam()),scroll.background().vscroll(),r11LoadingState.displayEnabled(),old.foreground(),old.background(),r11LoadingPrefix.section().residency(),playerFrame.artState()));}
        void refresh(){refreshOriginalR11CurrentArt();}
        void cpu(CdObjectDrawQueue.CpuSat cpu){requireOwner(r11World);var s=r11LoadingState;var next=new R11LoadingState(s.phase(),s.scroll(),s.globals(),s.reserved(),s.flowers(),CdAssetBytes.copyOf(cpu.bytes()),s.blocks(),s.layout(),s.nativeCarry(),s.shieldCpu(),s.noBumperByte(),s.bossStartedByte(),s.fadeSteps(),s.displayEnabled(),s.clears());state(s,next);}
        void reserved(int whole,CdAssetBytes expected,CdAssetBytes ready){requireOwner(r11World);if(whole<1||whole>31||ready.length()!=64)throw new IllegalArgumentException("Reserved full64");var s=r11LoadingState;var bank=s.reserved();if(!java.util.Arrays.equals(bank.slice((whole-1)*64,64).copy(),expected.copy()))throw new IllegalStateException("Changed reserved source");var bytes=bank.copy();System.arraycopy(ready.copy(),0,bytes,(whole-1)*64,64);state(s,s.reserved(CdAssetBytes.copyOf(bytes)));}
        void scroll(SonicCdR31aLoadingVisit.Scroll before,SonicCdR31aLoadingVisit.Scroll ready){requireOwner(r11World);storeR11Scroll(before,ready);}
        com.openggf.game.soniccd.level.CdLevelLoadingSource loadingSource(){requireOwner(r11World);return originalLoadingSource;}
        int waterRead(CdR4WaterAirPorts.Value field){requireOwner(r11World);var w=originalLoadingWater.orElseThrow();var e=playerFrame.environment();return switch(field){
            case WATER_Y->w.waterYWord().orElseThrow();case STATIC_WATER_Y->w.staticYWord().orElseThrow();case TARGET_WATER_Y->w.targetYWord().orElseThrow();case WATER_SPEED->w.speedByte().orElseThrow();case WATER_ANGLE->w.swayAngleByte().orElseThrow();case WATER_FULL->w.fullByte().orElseThrow();case SPLIT->w.splitByte().orElseThrow();
            case CAMERA_X->r11LoadingState.scroll().foreground().cameraX();case CAMERA_Y->r11LoadingState.scroll().foreground().cameraYWord();case ACT->e.movement().gates().zoneAct()&255;case ZONE->e.movement().gates().zoneAct()>>>8;case ZONE_WORD->e.movement().gates().zoneAct();case TIME_ZONE->e.movement().gates().timeZone();case PAUSED->playerFrame.paused()?1:0;case DEBUG_MODE->e.globals().debugMode();case BOSS_STARTED->r11LoadingState.bossStartedByte();case DROWN_TIMER->r11LoadingState.globals().drownTimerWord().orElseThrow();case SCROLL_LOCK->r11LoadingState.scroll().foreground().scrollLock()>>>8;
            case WATER_CURRENT->e.movement().gates().windTunnel();case HELD->e.controls().logicalHeld();case VBLANK_LOW->(int)(playerFrame.vIntClock().absoluteValue().orElseThrow(()->new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("absolute native VBlank LOW"))&255);case STAGE_FRAMES->e.globals().levelFrames();case STAGE_FRAMES_LOW->e.globals().levelFrames()&255;default->throw new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("actual TT scalar "+field);};}
        void waterWrite(CdR4WaterAirPorts.Value field,int value){
            requireOwner(r11World);CdR4WaterAirPorts.checked(value,field.bytes,field.name());
            if(field==CdR4WaterAirPorts.Value.DROWN_TIMER){
                // Native Main CPU Variables.inc:58 WORD; PlayerResetDrown stores30 at
                // objects/r4/player_bubbles.asm:175. Reuse the sole captured Globals owner.
                r11World.requireOriginalTtDrownTimerWrite(playerFrame,value);
                var before=r11LoadingState;var g=before.globals();
                var ready=new SonicCdR31aWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),OptionalInt.of(value),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());
                state(before,before.globals(ready));return;
            }
            var w=originalLoadingWater.orElseThrow();
            var n=new OriginalLoadingWater(field==CdR4WaterAirPorts.Value.WATER_Y?OptionalInt.of(value):w.waterYWord(),field==CdR4WaterAirPorts.Value.STATIC_WATER_Y?OptionalInt.of(value):w.staticYWord(),field==CdR4WaterAirPorts.Value.TARGET_WATER_Y?OptionalInt.of(value):w.targetYWord(),w.routineByte(),field==CdR4WaterAirPorts.Value.WATER_FULL?OptionalInt.of(value):w.fullByte(),field==CdR4WaterAirPorts.Value.WATER_SPEED?OptionalInt.of(value):w.speedByte(),field==CdR4WaterAirPorts.Value.SPLIT?OptionalInt.of(value):w.splitByte(),w.pendingByte(),w.selectedPalette(),w.returnedPaletteLoaded(),field==CdR4WaterAirPorts.Value.WATER_ANGLE?OptionalInt.of(value):w.swayAngleByte());
            if(field!=CdR4WaterAirPorts.Value.WATER_Y&&field!=CdR4WaterAirPorts.Value.STATIC_WATER_Y&&field!=CdR4WaterAirPorts.Value.TARGET_WATER_Y&&field!=CdR4WaterAirPorts.Value.WATER_FULL&&field!=CdR4WaterAirPorts.Value.WATER_SPEED&&field!=CdR4WaterAirPorts.Value.SPLIT&&field!=CdR4WaterAirPorts.Value.WATER_ANGLE)throw new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("actual TT scalar store "+field);
            if(field==CdR4WaterAirPorts.Value.STATIC_WATER_Y||field==CdR4WaterAirPorts.Value.WATER_FULL){var g=r11LoadingState.globals();var live=field==CdR4WaterAirPorts.Value.STATIC_WATER_Y?CdCheckpointLiveAux.writeWaterHeight2(g.live(),value):CdCheckpointLiveAux.writeWaterFullscreen(g.live(),value);state(r11LoadingState,r11LoadingState.globals(SonicCdR31aWorldHost.with(g,g.ring(),live,g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7())));}
            originalLoadingWater=Optional.of(n);
        }
        int waterArm(){requireOwner(r11World);return r11LoadingPrefix.section().hintWord();}
        void waterArm(int value){requireOwner(r11World);originalR11Art(s->r11LoadingArt.writeHintWord(s,value));}
        int waterPending(){requireOwner(r11World);return originalLoadingWater.orElseThrow().pendingByte().orElseThrow();}
        void waterPending(int value){requireOwner(r11World);if((value&~255)!=0)throw new IllegalArgumentException("Pending BYTE");var w=originalLoadingWater.orElseThrow();originalLoadingWater=Optional.of(new OriginalLoadingWater(w.waterYWord(),w.staticYWord(),w.targetYWord(),w.routineByte(),w.fullByte(),w.speedByte(),w.splitByte(),OptionalInt.of(value),w.selectedPalette(),w.returnedPaletteLoaded(),w.swayAngleByte()));}
        void selectWaterInitial(){requireOwner(r11World);selectWaterImage(waterRead(CdR4WaterAirPorts.Value.WATER_FULL)!=0?r11WaterPalette:r11LoadingPrefix.normalPalette());}
        private void selectWaterImage(CdAssetBytes value){var w=originalLoadingWater.orElseThrow();originalLoadingWater=Optional.of(new OriginalLoadingWater(w.waterYWord(),w.staticYWord(),w.targetYWord(),w.routineByte(),w.fullByte(),w.speedByte(),w.splitByte(),w.pendingByte(),Optional.of(value),w.returnedPaletteLoaded(),w.swayAngleByte()));}
        int currentWaterLong(int at){requireOwner(r11World);Objects.checkFromIndexSize(at,4,128);return(int)r11WaterPalette.unsignedLong(at);}
        void selectWaterLong(int at,int bits){requireOwner(r11World);Objects.checkFromIndexSize(at,4,128);var bytes=originalLoadingWater.orElseThrow().selectedPalette().orElseThrow().copy();bytes[at]=(byte)(bits>>>24);bytes[at+1]=(byte)(bits>>>16);bytes[at+2]=(byte)(bits>>>8);bytes[at+3]=(byte)bits;selectWaterImage(CdAssetBytes.copyOf(bytes));}
        CdAssetBytes currentWater(){requireOwner(r11World);return r11WaterPalette;}
        CdAssetBytes currentWaterFade(){requireOwner(r11World);return r11LoadingState.cpuSat().slice(512,128);}
        void copyWaterCameras(){requireOwner(r11World);copyOriginalR11Cameras();}
        void drawWaterStage(){requireOwner(r11World);drawOriginalR11CopiedStage();}
        void animateWaterStage(){requireOwner(r11World);if(r11LoadingState.globals().ring().timeStopTimer()==0&&!playerFrame.paused()){powerup();animateOriginalLoadingStage();}}
        int waterRandom(){requireOwner(r11World);var n=r11LoadingState.nativeCarry();var seed=n.rngSeedLong().orElseThrow(()->new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("actual TT RNG seed"));var rng=new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2,seed);int result=rng.nextWord();requireOwner(r11World);if(r11LoadingState.nativeCarry()!=n)throw new IllegalStateException("Changed actual RNG preimage");var ready=new SonicCdR31aWorldHost.NativeCarry(n.collisionPointer(),n.fadeInfoWithoutTail(),java.util.OptionalLong.of(rng.getSeed()),n.targetRightBoundWord(),n.amyCapturedByte());r11LoadingState=r11LoadingState.carry(ready);return result;}
        void waterScroll(){requireOwner(r11World);initializeOriginalWaterScroll();}

    }
    void requireOriginalR11LoadingPictureOwner(CdWorld expected){
        requireOriginalR11Service();
        if(r11World!=expected||r11OrdinaryAccess!=null||r11Clear!=null||r11LoadingState.phase()==SonicCdR31aLoadingVisit.Phase.READY)
            throw new IllegalStateException("No original loading publication scope");
    }
    private void publishOriginalR11Loading(){
        requireOriginalR11Service();var old=r11FirstLoad;var scroll=r11LoadingState.scroll();
        var retained=r11World==null?Optional.<CdObjectDrawQueue.CpuSat>empty():r11World.originalR11LoadingCpu(this);
        projectOriginalR11Selection(new CdOriginalR11Display.Inputs(retained,r11LoadingState.cpuSat(),r11LoadingPrefix.normalPalette(),CdSelectedHscroll.copyNativePrefix(old.selection().inputs().hscroll(),scroll.background().hscrollRam()),scroll.background().vscroll(),r11LoadingState.displayEnabled(),old.foreground(),old.background(),r11LoadingPrefix.section().residency(),playerFrame.artState()));
    }
    private void retainOriginalR11Planes(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State fg,com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State bg,SonicCdR31aLoadingVisit.Copies copies){
        requireOriginalR11Service();var old=r11FirstLoad;var input=old.selection().inputs();
        var selected=CdOriginalR11Display.project(new CdOriginalR11Display.Inputs(input.selectedCpu(),input.selectedSat(),input.palette(),input.hscroll(),input.vscrollLongBits(),input.displayEnabled(),fg,bg,r11LoadingPrefix.section().residency(),playerFrame.artState()));
        r11FirstLoad=new R11FirstLoad(selected,fg,bg,copies,old.levelLoaded(),old.standardLoaded(),old.completedWaits(),old.music());
    }
    private void copyAndDrawOriginalR11(){copyOriginalR11Cameras();drawOriginalR11CopiedStage();}
    private void copyOriginalR11Cameras(){
        requireOriginalR11Service();var s=r11LoadingState.scroll();var f=s.foreground();var b=s.background();
        var cams=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.CopiedCameras(new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));
        var c=new SonicCdR31aLoadingVisit.Copies((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16(),cams,f.scrollFlags(),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3()));
        storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,b,c,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
    }
    private void drawOriginalR11CopiedStage(){
        requireOriginalR11Service();var s=r11LoadingState.scroll();var c=s.copies();var cams=c.background();
        var live=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.LiveCameras(cams.bg1(),cams.bg2(),cams.bg3());
        com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Result out;
        try{out=originalLoadingSource.backgroundPlane().withBlockDefinitions(r11LoadingPrefix.blockDefinitions()).incremental(r11FirstLoad.background(),live,c.background(),c.backgroundFlags());}
        catch(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.InvalidPlane original){retainOriginalR11Background(original,c,true);throw original;}
        c=new SonicCdR31aLoadingVisit.Copies(c.foregroundX16_16(),c.foregroundY16_16(),cams,c.foregroundFlags(),out.flags());retainOriginalR11Planes(r11FirstLoad.foreground(),out.state(),c);
        // PP DrawBGBlockCol restores a0; otherwise a0 is BGCameraSectIDs+1. Preserve the
        // actual directory cursor, including an off-end metadata read as unavailable.
        int index=1;for(var read:out.sections())if(read.phase()==com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN)index=read.tableIndex()+1;
        var ids=originalLoadingSource.backgroundSectionIds();
        var metadata=new com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.CallerMetadata(index>=0&&index<ids.length()?OptionalInt.of(ids.unsignedByte(index)):OptionalInt.empty());
        var t=originalLoadingSource.retainedPlayerSource().terrain();var writer=com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.fromTables(t.blocks(),t.chunks(),t.layout().ram()).withBlockDefinitions(r11LoadingPrefix.blockDefinitions());
        try{var result=writer.incremental(r11FirstLoad.foreground(),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(c.foregroundX16_16(),c.foregroundY16_16()),c.foregroundFlags(),metadata);retainOriginalR11Foreground(result,c);}
        catch(com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.InvalidPlane original){retainOriginalR11Foreground(original.completedPrefix(),c);throw original;}
    }
    private void retainOriginalR11Background(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.InvalidPlane original,SonicCdR31aLoadingVisit.Copies before,boolean consume){
        try{var prefix=original.completedPrefix();if(prefix.isPresent()){var result=prefix.orElseThrow();var copies=consume?new SonicCdR31aLoadingVisit.Copies(before.foregroundX16_16(),before.foregroundY16_16(),before.background(),before.foregroundFlags(),result.flags()):before;retainOriginalR11Planes(r11FirstLoad.foreground(),result.state(),copies);}}
        catch(RuntimeException|Error retention){if(retention!=original)original.addSuppressed(retention);}
    }
    private void retainOriginalR11Foreground(com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.Result result,SonicCdR31aLoadingVisit.Copies before){
        var c=new SonicCdR31aLoadingVisit.Copies(before.foregroundX16_16(),before.foregroundY16_16(),before.background(),result.flagsWord(),before.backgroundFlags());var s=r11LoadingState.scroll();storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(s.foreground(),s.background(),c,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));retainOriginalR11Planes(result.state(),r11FirstLoad.background(),c);
    }
    /** One private actual caller of native LoadShieldArt, shared by the post-PLC and
     * ordinary paths. Completed BCLR7, CPU and each tile publication retain their own prefixes.
     * No flag-to-slot synthesis: the native ID3 producer and shared World dispatch own slot6. */
    private void copyOriginalR11Powerup(){
        requireOriginalR11Service();
        if(originalPastStage()){
            originalPastArrival.require();
            if((r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.LEVEL_PLC&&r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.READY)
                    ||playerFrame.stageSource()!=originalLoadingSource.retainedPlayerSource()
                    ||!r11LoadingArt.ownsLoadingSource(originalLoadingSource))
                throw new IllegalStateException("No actual Past Shield source/Frame/current-art/phase owner");
            originalLoadingSource.standardPastObjectSource().requirePlacement(r11Placement);
            r11LoadingArt.validateState(r11LoadingPrefix.section());
        }
        var l=r11LoadingPrefix.main().level();var gate=com.openggf.game.soniccd.resources.CdPowerupArtRuntime.gate(l.powerup());r11LoadingPrefix=r11LoadingPrefix.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),gate.powerup(),l.timeOver()));if(!gate.copyRequired())return;
        var runtime=originalPastStage()?com.openggf.game.soniccd.resources.CdPowerupArtRuntime.fromRawSources(originalLoadingSource.shield()):originalWaterStage()?com.openggf.game.soniccd.resources.CdPowerupArtRuntime.fromRawSources(originalLoadingSource.objectSource().powerupArtSources()):new com.openggf.game.soniccd.resources.CdPowerupArtRuntime(source.assets().shield());var request=runtime.prepareActiveCopy(r11LoadingState.shieldCpu(),gate.powerup());var s=r11LoadingState;r11LoadingState=new R11LoadingState(s.phase(),s.scroll(),s.globals(),s.reserved(),s.flowers(),s.cpuSat(),s.blocks(),s.layout(),s.nativeCarry(),request.bytes(),s.noBumperByte(),s.bossStartedByte(),s.fadeSteps(),s.displayEnabled(),s.clears());
        for(int at=0;at<request.bytes().length();at+=32){int dest=com.openggf.game.soniccd.resources.CdPowerupArtRuntime.VRAM_BYTE_ADDRESS+at;var bytes=request.bytes().slice(at,32);originalR11Art(a->r11LoadingArt.writeCurrentPowerupTile(a,dest,bytes));}
    }
    private void originalR11VIntReturned(){requireOriginalR11Service();EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();var old=r11FirstLoad;r11FirstLoad=new R11FirstLoad(old.selection(),old.foreground(),old.background(),old.copies(),old.levelLoaded(),old.standardLoaded(),old.completedWaits()+1,old.music());}
    private void originalR11Art(java.util.function.UnaryOperator<com.openggf.game.soniccd.resources.CdR11aSectionArt.State> op){
        requireOriginalR11Service();var before=r11LoadingPrefix.section();
        try{r11LoadingPrefix=r11LoadingPrefix.art(op.apply(before));}
        catch(com.openggf.game.soniccd.resources.CdR11aSectionArt.SectionArtException failure){
            r11LoadingPrefix=r11LoadingPrefix.art(failure.state());failure.completedRamPrefix().ifPresent(b->r11LoadingState=r11LoadingState.geometry(Optional.of(b),r11LoadingState.layout()));
            try{refreshOriginalR11CurrentArt();}catch(RuntimeException|Error publication){if(publication!=failure)failure.addSuppressed(publication);}throw failure;
        }
        if(before.residency()!=r11LoadingPrefix.section().residency())refreshOriginalR11CurrentArt();
    }
    private void refreshOriginalR11CurrentArt(){
        requireOriginalR11Service();var i=r11FirstLoad.selection().inputs();projectOriginalR11Selection(new CdOriginalR11Display.Inputs(i.selectedCpu(),i.selectedSat(),i.palette(),i.hscroll(),i.vscrollLongBits(),i.displayEnabled(),r11FirstLoad.foreground(),r11FirstLoad.background(),r11LoadingPrefix.section().residency(),playerFrame.artState()));
    }
    enum OriginalR11TransferBoundary { RUN_R12_DESTINATION_ENTRY_OWNER, RUN_LEVEL_GAME_OVER_OWNER, RUN_LEVEL_TIME_WARP_OWNER, RUN_LEVEL_SPECIAL_STAGE_OWNER, WARP_RETURN_BYTE_AND_DESTINATION_OWNER, ORIGINAL_WARP_INITIAL_SOURCE_OWNER, ORIGINAL_WARP_DESTINATION_ENTRY_OWNER }
    enum OriginalR11IpxPhase { NOT_STARTED, RETURN_BYTE, FM_STOPPED, BLANK_RETURNED }
    private enum OriginalR11IpxOrigin { CLEARED_LEVEL, RETURNED_WARP }
    private record OriginalR11IpxState(OriginalR11IpxPhase phase,OptionalInt returnByte,OriginalR11IpxOrigin origin){
        OriginalR11IpxState(OriginalR11IpxPhase phase,OptionalInt returnByte){this(phase,returnByte,OriginalR11IpxOrigin.CLEARED_LEVEL);}
        OriginalR11IpxState{
            Objects.requireNonNull(phase);Objects.requireNonNull(returnByte);Objects.requireNonNull(origin);
            if(origin==OriginalR11IpxOrigin.CLEARED_LEVEL){
                if((phase==OriginalR11IpxPhase.NOT_STARTED)!=returnByte.isEmpty()||returnByte.isPresent()&&returnByte.orElseThrow()!=CdRetainedProgrammeReturn.clearedPlcReturnByte())throw new IllegalArgumentException("Original IPX semantic return pair");
            }else if(phase==OriginalR11IpxPhase.NOT_STARTED||returnByte.isPresent())throw new IllegalArgumentException("Returned WARP owns a reached UNKNOWN byte write");
        }
    }
    private OriginalR11IpxState r11ClosureIpx=new OriginalR11IpxState(OriginalR11IpxPhase.NOT_STARTED,OptionalInt.empty());
    static final class OriginalR11TransferStopped extends UnsupportedOperationException {
        private final OriginalR11TransferBoundary boundary;
        private OriginalR11TransferStopped(OriginalR11TransferBoundary boundary){super("Original R11 native programme return requires "+boundary+"; its actual IPX/destination continuation remains unowned");this.boundary=boundary;}
        OriginalR11TransferBoundary boundary(){return boundary;}
    }
    /** Original controller continuation, never callable from capture/DTO/effects.
     * Native hardware arbitration is omitted for immutable detached programme data, as in the
     * existing retained caller. Actual display-disable and represented blank remain explicit. */
    private void originalR11StageCompleteIpx(){
        requireOriginalR11Service();var g=playerFrame.environment().movement().gates();
        if(r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.RETURNED||r11ReturnKind!=SonicCdR31aLoadingVisit.ReturnKind.NEXT_LEVEL
                ||r11LoadingPrefix.main().level().levelRestart()!=0||g.zoneAct()!=1||g.timeZone()!=1||r11World==null||r11OrdinaryAccess!=null)
            throw new IllegalStateException("No original R1 actual stage-complete return");
        originalBoot.requireConsumed(this,programWorld,selectedProfileInvocation);
        if(r11ClosureIpx.phase()==OriginalR11IpxPhase.NOT_STARTED){originalR11ReturnTail(false);return;}
        if(r11ClosureIpx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED)throw new IllegalStateException("Unreturned original IPX prefix");
        var decision=CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){public int lives(){requireOriginalR11Service();return r11LoadingState.globals().ring().lives();}public int rawPeriod(){requireOriginalR11Service();return playerFrame.environment().movement().gates().timeZone();}public int specialStage(){requireOriginalR11Service();return r11ClosureSpecialStage;}});
        throw new OriginalR11TransferStopped(switch(decision){case GAME_OVER->OriginalR11TransferBoundary.RUN_LEVEL_GAME_OVER_OWNER;case TIME_WARP->OriginalR11TransferBoundary.RUN_LEVEL_TIME_WARP_OWNER;case SPECIAL_STAGE->OriginalR11TransferBoundary.RUN_LEVEL_SPECIAL_STAGE_OWNER;case RETURN_TO_ACT_CALLER->OriginalR11TransferBoundary.RUN_R12_DESTINATION_ENTRY_OWNER;});
    }
    /** Original marked LevelStart RTS -> IPX.RunMMD -> WARP. This is an independent
     * selected-programme control partition; the old level stays RETURNED, open and inactive. */
    enum OriginalWarpPhase { NONE, LEVEL_RETURN_TAIL, WARP_ACTIVE, WARP_RETURNED, WARP_IPX_TAIL, DESTINATION_SELECTED }
    private OriginalWarpPhase originalWarpPhase=OriginalWarpPhase.NONE;
    private OriginalWarpAuthority originalWarpAuthority;
    private OriginalWarpDestination originalWarpDestination;
    /** Observation only. Neither this value nor its DATA can invoke a destination entry. */
    record OriginalWarpDestinationView(CdIpxProgress.WarpDestination selection,CdCommand command,CdStageKey stage,
            Optional<com.openggf.game.soniccd.level.CdLevelLoadingSource> loadingSource) { }
    Optional<OriginalWarpDestinationView> originalWarpDestination(){requireOriginalR11EntryCut();return Optional.ofNullable(originalWarpDestination).map(d->d.view);}
    CdRetainedProgrammeReturn.ReturnKnowledge originalWarpReturnKnowledge(){requireOriginalR11EntryCut();return new CdRetainedProgrammeReturn.ReturnKnowledge(r11ClosureIpx.returnByte());}
    /** IPX screen-disable is distinct from WARP's retained internal display variable. */
    boolean originalWarpDisplayEnabled(){return originalWarpSceneSelected()&&warpState.displayEnabled()
            &&!(r11ClosureIpx.origin()==OriginalR11IpxOrigin.RETURNED_WARP&&r11ClosureIpx.phase()==OriginalR11IpxPhase.BLANK_RETURNED);}
    private final class OriginalWarpDestination {
        private final OriginalWarpAuthority ticket=originalWarpAuthority;
        private final com.openggf.game.soniccd.level.CdInitialProgramSource initial;
        private final OriginalWarpDestinationView view;
        private OriginalWarpDestination(com.openggf.game.soniccd.level.CdInitialProgramSource initial,CdIpxProgress.WarpDestination selection){
            this.initial=Objects.requireNonNull(initial);var command=CdCommand.fromId(selection.commandByte());
            var catalogue=CdProgramCatalogue.forRelease(source.release().variant());var payload=catalogue.stageForCommand(command).orElseThrow(()->new IllegalArgumentException("Actual initial command has no stage consumer"));
            Optional<com.openggf.game.soniccd.level.CdLevelLoadingSource> loading=payload==CdStagePayload.R11B?Optional.of(initial.pastLoadingSource()):payload==CdStagePayload.R11A?Optional.of(originalLoadingSource):Optional.empty();
            view=new OriginalWarpDestinationView(selection,command,payload.stage(),loading);require(ticket);
        }
        private void require(OriginalWarpAuthority expected){
            if(ticket==null||ticket!=expected||source.initialProgramSource().orElse(null)!=initial)throw new IllegalArgumentException("Foreign original WARP destination occurrence");
            ticket.lifetime();initial.requireDisc(source.release().normalizedSha256());
            var choice=view.selection();if(initial.runR11CommandByte(choice.row().commandByteOffset())!=choice.commandByte()
                ||CdCommand.fromId(choice.commandByte())!=view.command()
                ||!CdProgramCatalogue.forRelease(source.release().variant()).stageForCommand(view.command()).orElseThrow().stage().equals(view.stage()))throw new IllegalArgumentException("Original destination row/consumer differs");
            if(view.command()==CdCommand.R11B){if(view.loadingSource().orElse(null)!=initial.pastLoadingSource())throw new IllegalArgumentException("Different retained actual Past reader");}
            else if(view.command()==CdCommand.R11A){if(view.loadingSource().orElse(null)!=originalLoadingSource)throw new IllegalArgumentException("Different retained actual Present source");}
            else if(view.loadingSource().isPresent())throw new IllegalArgumentException("Unowned future loading source");
            view.loadingSource().ifPresent(com.openggf.game.soniccd.level.CdLevelLoadingSource::requireRetained);
        }
    }
    private final class OriginalWarpAuthority {
        private final SonicCdGameModule owner=SonicCdGameModule.this;
        private final CdGameDataSource data=source;
        private final CdOriginalBootPrefix boot=originalBoot;
        private final Object loading=originalLoadingSource,world=programWorld,mode=playerMode,level=r11EntryLevel;
        private final Object frame=r11EntryFrame,worldOwner=r11World,levelLifetime=r11LevelLifetime,bankLifetime=r11TimeWarpSavedLifetime;
        private final Thread thread=Thread.currentThread();
        private final java.util.OptionalLong generation=r11EntryLoadGeneration;
        private final CdTimeWarpSaveData.State bank=Objects.requireNonNull(r11TimeWarpSaved);
        private final com.openggf.game.soniccd.media.CdTimeWarpPrograms programs=source.timeWarpPrograms().orElseThrow(()->SonicCd.unsupported("Original selected WARP source absent"));
        private void lifetime(){
            if(owner!=SonicCdGameModule.this||data!=source||boot!=originalBoot||loading!=originalLoadingSource
                ||world!=programWorld||mode!=playerMode||level!=r11EntryLevel||frame!=r11EntryFrame||worldOwner!=r11World
                ||levelLifetime!=r11LevelLifetime||bankLifetime!=r11TimeWarpSavedLifetime||thread!=Thread.currentThread()
                ||thread!=r11Thread||!generation.equals(r11EntryLoadGeneration)||source.timeWarpPrograms().orElse(null)!=programs)
                throw new IllegalArgumentException("Foreign or expired original WARP occurrence");
        }
    }
    /** Private constructor site is ONLY the actual native WhiteReturn.returned callback. */
    private void issueOriginalWarpReturn(){
        requireOriginalR11Service();var g=playerFrame.environment().movement().gates();
        if(originalWarpPhase!=OriginalWarpPhase.NONE||originalWarpAuthority!=null||warpState!=null
            ||!programSounds.isEmpty()||r11ClosureIpx.phase()!=OriginalR11IpxPhase.NOT_STARTED||originalWaterStage()
            ||r11World==null||g.zoneAct()!=0||g.timeZone()<0x80||g.timeZone()>0x82
            ||r11ReturnKind!=SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART||r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.RETURNED
            ||r11LoadingPrefix.main().level().levelRestart()!=0||r11LoadingPrefix.main().level().spawnMode()!=2)
            throw new IllegalStateException("No genuine selected marked LevelStart RTS");
        var issued=new OriginalWarpAuthority();issued.lifetime();originalWarpAuthority=issued;originalWarpPhase=OriginalWarpPhase.LEVEL_RETURN_TAIL;
    }
    boolean originalWarpProgrammeSelected(){return originalWarpPhase!=OriginalWarpPhase.NONE;}
    boolean originalWarpSceneSelected(){return originalWarpPhase==OriginalWarpPhase.WARP_ACTIVE||originalWarpPhase==OriginalWarpPhase.WARP_RETURNED||originalWarpPhase==OriginalWarpPhase.WARP_IPX_TAIL||originalWarpPhase==OriginalWarpPhase.DESTINATION_SELECTED;}
    OriginalWarpPhase originalWarpPhase(){requireOriginalR11EntryCut();return originalWarpPhase;}
    private void requireOriginalWarpHostIdle(){
        var level=r11EntryLevelManager;
        if(level==null||playerMode.getFadeManager().isActive()||level.isTitleCardRequested()
            ||level.hasPendingInLevelTitleCardHeldCounterDispatch()||level.hasPendingFreshLevelTransitionBoundary()
            ||level.hasPendingLevelExit()||level.isLevelInactiveForTransition()||level.isRespawnRequestedForRewind()
            // This exact native profile keeps its music-suppression flag true permanently;
            // its inherited host VBlank music callback is a no-op, not pending host work.
            ||getTitleCardProvider()!=com.openggf.game.NoOpTitleCardProvider.INSTANCE||getLevelInitProfile()!=r31Profile)
            throw SonicCd.unsupported("Original WARP requires inactive host title/transition/music owners");
    }
    private void requireOriginalWarpService(){
        requireOriginalR11Service();Objects.requireNonNull(originalWarpAuthority,"No issued original WARP RTS").lifetime();
        if(originalWarpPhase==OriginalWarpPhase.NONE||r11LoadingState.phase()!=SonicCdR31aLoadingVisit.Phase.RETURNED
            ||r11ReturnKind!=SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART||r11TimeWarpSaved!=originalWarpAuthority.bank
            ||r11OrdinaryAccess!=null)throw new IllegalStateException("Original level is not suspended at its actual marked return");
        playerFrame.captureActor();playerFrame.captureShared();requireOriginalWarpHostIdle();
    }
    void validateOriginalWarpProgrammeOwner(){
        requireOriginalR11EntryCut();Objects.requireNonNull(originalWarpAuthority,"No original WARP ticket").lifetime();
        if(!originalWarpProgrammeSelected()||r11TimeWarpSaved!=originalWarpAuthority.bank)throw new IllegalStateException("No selected original WARP occurrence");
        requireOriginalWarpHostIdle();validateOriginalWarpCut(originalWarpPhase,originalWarpAuthority,warpAssets,warpScene,warpState,programSounds,r11ClosureIpx,originalWarpDestination);
    }
    private void originalR11ReturnTail(boolean warpReturn){
        requireOriginalR11Service();if(warpReturn)requireOriginalWarpService();
            CdRetainedProgrammeReturn.enter(new CdRetainedProgrammeReturn.Port(){
                public void returnByte(int value){requireOriginalR11Service();r11ClosureIpx=new OriginalR11IpxState(OriginalR11IpxPhase.RETURN_BYTE,OptionalInt.of(value));}
                public void stopFm(){requireOriginalR11Service();if(r11ClosureIpx.phase()!=OriginalR11IpxPhase.RETURN_BYTE)throw new IllegalStateException("Original return-byte prefix missing");var ready=new java.util.ArrayList<>(programSounds);ready.add(ProgramSound.STOP_FM);var next=List.copyOf(ready);var phase=new OriginalR11IpxState(OriginalR11IpxPhase.FM_STOPPED,r11ClosureIpx.returnByte());programSounds=next;r11ClosureIpx=phase;}
                public void blank(){requireOriginalR11Service();if(r11ClosureIpx.phase()!=OriginalR11IpxPhase.FM_STOPPED)throw new IllegalStateException("Original FM-stop prefix missing");var i=r11FirstLoad.selection().inputs();var next=r11LoadingState.fade(r11LoadingState.fadeSteps(),false);var selected=new CdOriginalR11Display.Inputs(i.selectedCpu(),i.selectedSat(),i.palette(),i.hscroll(),i.vscrollLongBits(),false,r11FirstLoad.foreground(),r11FirstLoad.background(),r11LoadingPrefix.section().residency(),playerFrame.artState());r11LoadingState=next;projectOriginalR11Selection(selected);if(warpReturn){var completed=r11FirstLoad;r11FirstLoad=new R11FirstLoad(completed.selection(),completed.foreground(),completed.background(),completed.copies(),completed.levelLoaded(),completed.standardLoaded(),completed.completedWaits()+1,completed.music());}else originalR11VIntReturned();r11ClosureIpx=new OriginalR11IpxState(OriginalR11IpxPhase.BLANK_RETURNED,r11ClosureIpx.returnByte());}
            });    }
    /** One synchronous actual WARP RTS occurrence. No public DTO or idle callback enters it. */
    private void originalReturnedWarpTail(){
        requireOriginalWarpService();
        if(originalWarpPhase!=OriginalWarpPhase.WARP_RETURNED||warpState.phase()!=CdWarpScene.Phase.RETURNED
                ||r11ClosureIpx.origin()!=OriginalR11IpxOrigin.CLEARED_LEVEL||r11ClosureIpx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED
                ||originalWarpDestination!=null)throw new IllegalStateException("No genuine returned WARP tail");
        var ticket=originalWarpAuthority;var scene=warpScene;var state=warpState;
        var unknown=CdRetainedProgrammeReturn.ReturnKnowledge.unknown();
        class Call implements CdRetainedProgrammeReturn.KnowledgePort {
            private int next;private boolean active=true;
            private OriginalR11IpxState ipx=r11ClosureIpx;private List<ProgramSound> sounds=programSounds;
            private void check(int ordinal){requireOriginalWarpService();if(!active||next!=ordinal||originalWarpAuthority!=ticket||warpScene!=scene||warpState!=state
                    ||r11ClosureIpx!=ipx||programSounds!=sounds||originalWarpDestination!=null
                    ||originalWarpPhase!=(ordinal==0?OriginalWarpPhase.WARP_RETURNED:OriginalWarpPhase.WARP_IPX_TAIL))throw new IllegalStateException("Copied/expired original WARP tail callback");}
            public void returnByte(CdRetainedProgrammeReturn.ReturnKnowledge value){
                check(0);if(value!=unknown||value.value().isPresent())throw new IllegalArgumentException("Only issued unknown WARP result");
                var ready=new OriginalR11IpxState(OriginalR11IpxPhase.RETURN_BYTE,value.value(),OriginalR11IpxOrigin.RETURNED_WARP);check(0);
                r11ClosureIpx=ready;originalWarpPhase=OriginalWarpPhase.WARP_IPX_TAIL;ipx=ready;next=1;
            }
            public void stopFm(){
                check(1);var ready=new java.util.ArrayList<>(sounds);ready.add(ProgramSound.STOP_FM);var requests=List.copyOf(ready);
                var completed=new OriginalR11IpxState(OriginalR11IpxPhase.FM_STOPPED,ipx.returnByte(),OriginalR11IpxOrigin.RETURNED_WARP);check(1);
                programSounds=requests;r11ClosureIpx=completed;sounds=requests;ipx=completed;next=2;
            }
            public void blank(){
                check(2);var completed=new OriginalR11IpxState(OriginalR11IpxPhase.BLANK_RETURNED,ipx.returnByte(),OriginalR11IpxOrigin.RETURNED_WARP);check(2);
                // Existing selected retained renderer derives IPX's blank from this exact prefix.
                // Neither old level clock nor scene Cursor.pulse is serviced by this IPX VSync.
                r11ClosureIpx=completed;ipx=completed;next=3;
            }
        }
        var call=new Call();try{CdRetainedProgrammeReturn.enter(call,unknown);}finally{call.active=false;}
    }
    private void selectOriginalWarpDestination(){
        requireOriginalWarpService();
        if(originalWarpPhase!=OriginalWarpPhase.WARP_IPX_TAIL||warpState.phase()!=CdWarpScene.Phase.RETURNED
                ||r11ClosureIpx.origin()!=OriginalR11IpxOrigin.RETURNED_WARP||r11ClosureIpx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED
                ||originalWarpDestination!=null)throw new IllegalStateException("Original WARP IPX blank has not returned");
        var initial=source.initialProgramSource().orElseThrow(()->new OriginalR11TransferStopped(OriginalR11TransferBoundary.ORIGINAL_WARP_INITIAL_SOURCE_OWNER));
        initial.requireDisc(source.release().normalizedSha256());
        var choice=CdIpxProgress.postWarpDestination(new CdIpxProgress.WarpDestinationReads(){
            public int rawPeriod(){requireOriginalWarpService();return playerFrame.environment().movement().gates().timeZone();}
            public int commandByte(CdIpxProgress.WarpCommandRow row){requireOriginalWarpService();return initial.runR11CommandByte(row.commandByteOffset());}
            public int goodFuture(){requireOriginalWarpService();return r11LoadingPrefix.goodFuture();}
        });
        var selected=new OriginalWarpDestination(initial,choice);requireOriginalWarpService();selected.require(originalWarpAuthority);
        originalWarpDestination=selected;originalWarpPhase=OriginalWarpPhase.DESTINATION_SELECTED;
        // No live source/FULL/generation swap or old Frame retirement occurs in this slice.
    }
    private boolean originalDestinationEntering;
    private OriginalPastArrival originalPastArrival;
    private final class OriginalPastArrival {
        private final com.openggf.game.soniccd.level.CdInitialProgramSource initial;
        private final com.openggf.game.soniccd.level.CdLevelLoadingSource loading;
        private final CdPlayerFrameRuntime frame;
        private final Object lifetime,bankLifetime;
        private final Thread thread=Thread.currentThread();
        private OriginalPastArrival(OriginalWarpDestination request,CdPlayerFrameRuntime actual){
            initial=request.initial;loading=request.view.loadingSource().orElseThrow();frame=actual;
            lifetime=r11LevelLifetime;bankLifetime=r11TimeWarpSavedLifetime;
        }
        private void require(){
            if(thread!=Thread.currentThread()||thread!=r11Thread||source.initialProgramSource().orElse(null)!=initial
                ||initial.pastLoadingSource()!=loading||originalLoadingSource!=loading||playerFrame!=frame||r11EntryFrame!=frame
                ||frame.closed()||r11LevelLifetime!=lifetime||r11TimeWarpSavedLifetime!=bankLifetime)
                throw new IllegalStateException("Expired original Past destination lifetime");
            initial.requireDisc(source.release().normalizedSha256());loading.requirePlayerSource(frame.stageSource());
        }
    }
    private boolean originalPastStage(){return originalPastArrival!=null;}
    /** The actual public controller is the only issuer. The old request is validated in full
     * before its physical World is retired; no metadata/restore path can recreate this call.
     * Repeated RunMMD clears WORKRAMFILE, then original Start and shared LevelStart run once.
     * Native N1ecc IPX.RunMMD:1311-1319, PPZ Main:1-18, common Level/Main:46-329. */
    private void enterOriginalPastDestination(){
        validateOriginalWarpProgrammeOwner();var request=Objects.requireNonNull(originalWarpDestination);
        request.require(originalWarpAuthority);
        if(originalDestinationEntering||originalPastArrival!=null||request.view.command()!=CdCommand.R11B
                ||request.view.loadingSource().orElse(null)!=request.initial.pastLoadingSource()
                ||r11EntryLevelManager==null||r11World==null||originalWaterStage())
            throw new OriginalR11TransferStopped(OriginalR11TransferBoundary.ORIGINAL_WARP_DESTINATION_ENTRY_OWNER);
        var oldFrame=playerFrame;var oldWorld=r11World;var before=r11LoadingState;var prefix=r11LoadingPrefix;
        var saved=Objects.requireNonNull(r11TimeWarpSaved);var bankLifetime=r11TimeWarpSavedLifetime;
        var savedFlags=r11Placement.capture().savedFlags();
        var generation=r11EntryLoadGeneration;var oldLevel=r11EntryLevel;var manager=r11EntryLevelManager;
        var clock=oldFrame.vIntClock();var environment=CdOriginalProgrammeWorkClear.environment(oldFrame.environment());
        if(environment.movement().gates().zoneAct()!=0||environment.movement().gates().timeZone()!=0x80
                ||prefix.main().level().spawnMode()!=2||prefix.main().level().levelRestart()!=0
                ||(prefix.main().level().plcLoadFlags()&3)!=3||prefix.main().palFadeFlagsByte()!=0)
            throw new IllegalStateException("Actual Past marked entry prefix differs");
        var loading=request.initial.pastLoadingSource();loading.requireRetained();
        if(CdProgramInitialization.selectStartBranch(programServices.initialization())!=CdProgramInitialization.Branch.GAME_INIT)
            throw new IllegalStateException("Original Past requires actual GameInit branch");
        // Complete all refusal checks before a source/Frame retirement or repeated native clear.
        playerPresentation.requirePastRenderIdle();requireOriginalWarpHostIdle();request.require(originalWarpAuthority);
        originalDestinationEntering=true;
        try{
            oldWorld.close(); // authentic old lifetime; no Module/session teardown or retained save-bank clear
            if(!oldFrame.closed())throw new IllegalStateException("Old original World failed to retire Frame");
            playerPresentation.unbind();r11World=null;playerFrame=null;r11EntryFrame=null;
            r11EngineAssembled=false;originalSourceLoadingInstalled=false;r11FirstLoad=null;r11LoadingState=null;
            r11AssembledLevel=null;r11LevelLifetime=null;r11OrdinaryAccess=null;r11Switches=null;
            originalLoadingWater=Optional.empty();originalLoadingAnimation=Optional.empty();originalScrollFields=java.util.Map.of();
            originalLoadingSource=loading;
            Runnable guard=()->{
                if(!originalDestinationEntering||r11Thread!=Thread.currentThread()||r11Retired
                    ||SessionManager.getCurrentWorldSession()!=programWorld||SessionManager.getCurrentGameplayMode()!=playerMode
                    ||programWorld.getDataSource()!=source||playerMode.getLevelManager()!=manager
                    ||manager.getCurrentLevel()!=oldLevel||!generation.equals(r11EntryLoadGeneration)
                    ||manager.getCompletedProductionLoadGeneration()!=generation.orElseThrow()
                    ||originalLoadingSource!=loading||source.initialProgramSource().orElse(null)!=request.initial
                    ||r11TimeWarpSaved!=saved||r11TimeWarpSavedLifetime!=bankLifetime||r11World!=null)
                    throw new IllegalStateException("Changed actual original Past source/entry generation");
                loading.requireRetained();
            };
            guard.run();
            // Actual RunMMD has retired and cleared the old working projection before
            // the Sub CPU's level PCM suffix and the new original Start are serviced.
            var emptyArt=new CdPlayerArtRuntime.State(new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES],
                    new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES],0,false);
            var loads=SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(),source.timeWarpPrograms().orElseThrow());
            guard.run();programServices=withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM,loads,programServices.initialization());
            enterOriginalProgrammeStart(guard,()->programStartVideo=playerPresentation.initializeProgramVideo(originalR11LoadingUnknownPatterns(),emptyArt));
            guard.run();
            var actual=CdPlayerFrameRuntime.fromMarkedProgrammeEntry(loading.retainedPlayerSource(),
                    CdOriginalProgrammeWorkClear.primary(),environment,programStartVideo.playerArt(),clock);
            r11PreparedFrame=actual;playerFrame=actual;r11EntryFrame=actual;bindOriginalR11FinishTimers(actual);
            r11LoadingArt=com.openggf.game.soniccd.resources.CdR11aSectionArt.fromLoadingSource(loading);
            r11Placement=CdObjectPlacementRuntime.fromPlacements(loading.placements(),savedFlags);
            var art=r11LoadingArt.fromInactiveValues(programStartVideo.currentPatterns(),loadingZero(512),prefix.main().sectionWord(),
                    com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),
                    new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
            r11WaterPalette=loadingZero(128);
            r11LoadingPrefix=new R11LoadingPrefix(prefix.main(),prefix.goodFuture(),0,prefix.goodFutureFlags(),
                    prefix.ring(),prefix.score(),art,prefix.normalPalette(),prefix.fadePalette(),List.of(),CdBlockDefinitionStaging.afterFileBufferClear());
            originalR11LevelStartPrefix(actual,guard);guard.run();originalR11LevelStartFade(actual,true);guard.run();
            var scroll=before.scroll();var bg=scroll.background();
            var clearedBackground=new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State(0,0,0,0,0,0,loadingZero(8),0,0,0,bg.cloudRam(),loadingZero(512),loadingZero(1024),0);
            var clearedScroll=new SonicCdR31aLoadingVisit.Scroll(new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0),clearedBackground,scroll.copies(),0,0,0);
            var globals=originalRunMmdGlobals(before.globals());
            var carry=new SonicCdR31aWorldHost.NativeCarry(java.util.OptionalLong.of(0),OptionalInt.empty(),java.util.OptionalLong.of(0),OptionalInt.of(0),before.nativeCarry().amyCapturedByte());
            r11LoadingState=new R11LoadingState(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,clearedScroll,globals,
                    loadingZero(1984),before.flowers(),loadingZero(640),Optional.empty(),Optional.empty(),carry,
                    before.shieldCpu(),before.noBumperByte(),0,0,false,List.of());
            r11LoadingPrefix=r11LoadingPrefix.globals(globals);
            var video=programStartVideo.selection();var fg=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State(loadingZero(4096));
            var selected=CdOriginalR11Display.project(new CdOriginalR11Display.Inputs(Optional.empty(),video.displayedSat(),video.displayedPalette(),
                    loadingZero(1024),video.screenVScrollLongBits(),false,fg,video.backgroundPlane(),art.residency(),actual.artState()));
            r11FirstLoad=new R11FirstLoad(selected,fg,video.backgroundPlane(),clearedScroll.copies(),false,false,0,List.of());
            originalSourceLoadingInstalled=true;r11ReturnKind=SonicCdR31aLoadingVisit.ReturnKind.NONE;
            originalPastArrival=new OriginalPastArrival(request,actual);
            originalWarpPhase=OriginalWarpPhase.NONE;originalWarpAuthority=null;originalWarpDestination=null;warpState=null;warpScene=null;warpAssets=null;
            r11ClosureIpx=new OriginalR11IpxState(OriginalR11IpxPhase.NOT_STARTED,OptionalInt.empty());
            // Prior STOP_FM/WARP requests remain in the actual ledger; no replay/reset effects.
            originalPastArrival.require();assembleOriginalR11();originalPastArrival.require();
        }catch(SonicCdProgramLoadServices.Unsupported stopped){
            programServices=new ProgramServicesState(stopped.completedPrefix(),programServices.initialization(),programServices.environment(),programServices.completedCalls());
            r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(stopped);throw stopped;
        }catch(IOException failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw new UncheckedIOException("Original Past destination failed",failure);}
        catch(RuntimeException|Error failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        finally{r11PreparedFrame=null;originalDestinationEntering=false;}
    }
    private static SonicCdR31aWorldHost.Globals originalRunMmdGlobals(SonicCdR31aWorldHost.Globals old){
        var cleared=old.camera().misc();var tail=cleared.tail().map(t->new SonicCdR31aWorldHost.Tail(0,0,t.unknownLevelByte(),t.demoS1IndexWord(),0));
        return new SonicCdR31aWorldHost.Globals(cleared.ring(),cleared.live(),cleared.contact(),cleared.score(),cleared.projectorByte(),cleared.checkpointSaved(),
                OptionalInt.of(0),cleared.debugBlockWord(),cleared.pauseMusicBit(),cleared.bonus(),tail,cleared.animations(),
                cleared.paletteSteps7(),cleared.paletteTimers7(),cleared.layerByte(),cleared.bossFlagsByte(),cleared.eventRoutineWord());
    }
    /** Actual controller service lease only; direct idle/draw/capture/restore cannot enter it. */
    private void serviceOriginalWarpProgramme(int held){
        requireOriginalWarpService();
        if(originalWarpPhase==OriginalWarpPhase.DESTINATION_SELECTED){originalWarpDestination.require(originalWarpAuthority);throw new OriginalR11TransferStopped(OriginalR11TransferBoundary.ORIGINAL_WARP_DESTINATION_ENTRY_OWNER);}
        if(originalWarpPhase==OriginalWarpPhase.WARP_IPX_TAIL){selectOriginalWarpDestination();return;}
        if(originalWarpPhase==OriginalWarpPhase.WARP_RETURNED){originalReturnedWarpTail();return;}
        if(originalWarpPhase==OriginalWarpPhase.LEVEL_RETURN_TAIL){
            if(r11ClosureIpx.phase()==OriginalR11IpxPhase.NOT_STARTED){originalR11ReturnTail(true);return;}
            if(r11ClosureIpx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED)throw new IllegalStateException("Original WARP return prefix incomplete");
            var decision=CdIpxProgress.levelReturned(new CdIpxProgress.LevelReturnReads(){
                public int lives(){requireOriginalWarpService();return r11LoadingState.globals().ring().lives();}
                public int rawPeriod(){requireOriginalWarpService();return playerFrame.environment().movement().gates().timeZone();}
                public int specialStage(){requireOriginalWarpService();return r11ClosureSpecialStage;}
            });
            if(decision!=CdIpxProgress.LevelReturn.TIME_WARP)throw new OriginalR11TransferStopped(switch(decision){
                case GAME_OVER->OriginalR11TransferBoundary.RUN_LEVEL_GAME_OVER_OWNER;
                case SPECIAL_STAGE->OriginalR11TransferBoundary.RUN_LEVEL_SPECIAL_STAGE_OWNER;
                case RETURN_TO_ACT_CALLER->OriginalR11TransferBoundary.RUN_R12_DESTINATION_ENTRY_OWNER;
                case TIME_WARP->throw new AssertionError("checked above");});
            bindWarpScene(originalWarpAuthority.programs,this::requireOriginalWarpService);
            var initial=warpScene.initial(0);warpScene.validate(initial);requireOriginalWarpService();
            warpState=initial;originalWarpPhase=OriginalWarpPhase.WARP_ACTIVE;return;
        }
        stepWarpScene(held,this::requireOriginalWarpService);
        if(warpState.phase()==CdWarpScene.Phase.RETURNED)originalWarpPhase=OriginalWarpPhase.WARP_RETURNED;
        // WARP Cursor.pulse owns its private WORD; old level/Frame/ObjectManager/EngineTiming stay untouched.
    }
    private void validateOriginalWarpCut(OriginalWarpPhase phase,OriginalWarpAuthority ticket,CdWarpAssets assets,
            CdWarpScene scene,CdWarpScene.State state,List<ProgramSound> sounds,OriginalR11IpxState ipx,OriginalWarpDestination destination){
        if(phase==OriginalWarpPhase.NONE){if(ticket!=null||state!=null||destination!=null)throw new IllegalArgumentException("Unissued original WARP image");return;}
        Objects.requireNonNull(ticket).lifetime();
        if(phase==OriginalWarpPhase.LEVEL_RETURN_TAIL){
            if(destination!=null||ipx.origin()!=OriginalR11IpxOrigin.CLEARED_LEVEL)throw new IllegalArgumentException("Foreign original level return origin");
            boolean stopped=ipx.phase()==OriginalR11IpxPhase.FM_STOPPED||ipx.phase()==OriginalR11IpxPhase.BLANK_RETURNED;
            if(state!=null||!(stopped?sounds.equals(List.of(ProgramSound.STOP_FM)):sounds.isEmpty()))
                throw new IllegalArgumentException("Different original return prefix");
            return;
        }
        if(assets==null||scene==null||state==null||assets.source()!=ticket.programs.source())throw new IllegalArgumentException("Different original selected WARP binding");
        scene.validate(state);
        boolean tail=phase==OriginalWarpPhase.WARP_IPX_TAIL||phase==OriginalWarpPhase.DESTINATION_SELECTED;
        if(tail){
            if(state.phase()!=CdWarpScene.Phase.RETURNED||ipx.origin()!=OriginalR11IpxOrigin.RETURNED_WARP||ipx.returnByte().isPresent()
                    ||ipx.phase()==OriginalR11IpxPhase.NOT_STARTED)throw new IllegalArgumentException("Different actual WARP result publication");
            boolean stopped=ipx.phase()!=OriginalR11IpxPhase.RETURN_BYTE;
            var expected=stopped?List.of(ProgramSound.STOP_FM,ProgramSound.WARP_DIRECT_FM_D8,ProgramSound.STOP_FM):List.of(ProgramSound.STOP_FM,ProgramSound.WARP_DIRECT_FM_D8);
            if(!sounds.equals(expected)||(phase==OriginalWarpPhase.DESTINATION_SELECTED)!=(destination!=null))throw new IllegalArgumentException("Different returned WARP prefix/destination cut");
            if(destination!=null){if(ipx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED)throw new IllegalArgumentException("Destination before actual IPX blank");destination.require(ticket);}
            return;
        }
        if(destination!=null||ipx.origin()!=OriginalR11IpxOrigin.CLEARED_LEVEL||ipx.phase()!=OriginalR11IpxPhase.BLANK_RETURNED)throw new IllegalArgumentException("Different original WARP entry prefix");
        if((phase==OriginalWarpPhase.WARP_RETURNED)!=(state.phase()==CdWarpScene.Phase.RETURNED))throw new IllegalArgumentException("Different original WARP return occurrence");
        var expected=state.phase()==CdWarpScene.Phase.DELAY||state.phase()==CdWarpScene.Phase.PREPARE?List.of(ProgramSound.STOP_FM):List.of(ProgramSound.STOP_FM,ProgramSound.WARP_DIRECT_FM_D8);
        if(!sounds.equals(expected))throw new IllegalArgumentException("Different original WARP request prefix");
    }
    /** Sealed pairing of actual original scene/source with its exact whole selected native image. */
    private final class OriginalWarpImage {
        private final SonicCdGameModule owner=SonicCdGameModule.this;
        private final Object frame=r11EntryFrame,levelLifetime=r11LevelLifetime,bankLifetime=r11TimeWarpSavedLifetime;
        private final Thread thread=Thread.currentThread();
        private final Object actor,shared,world;
        private final R11LoadingPrefix prefix=r11LoadingPrefix;
        private final R11LoadingState loading=r11LoadingState;
        private final OriginalR11IpxState ipx=r11ClosureIpx;
        private final List<ProgramSound> sounds=programSounds;
        private final OriginalWarpPhase phase=originalWarpPhase;
        private final OriginalWarpAuthority ticket=originalWarpAuthority;
        private final CdWarpAssets assets=warpAssets;
        private final CdWarpScene scene=warpScene;
        private final CdWarpScene.State state=warpState;
        private final OriginalWarpDestination destination=originalWarpDestination;
        private OriginalWarpImage(Object actor,Object shared,Object world){this.actor=actor;this.shared=shared;this.world=world;}
        private void require(R11EntryImage whole){
            if(owner!=SonicCdGameModule.this||frame!=r11EntryFrame||levelLifetime!=r11LevelLifetime||bankLifetime!=r11TimeWarpSavedLifetime
                ||thread!=Thread.currentThread()||thread!=r11Thread||whole.actor()!=actor||whole.shared()!=shared||whole.world()!=world
                ||whole.prefix()!=prefix||whole.loading()!=loading||whole.closureIpx()!=ipx||whole.programmeSounds()!=sounds)
                throw new IllegalArgumentException("Copied/foreign/stale original WARP capture");
            if(phase!=OriginalWarpPhase.NONE){
                if(ticket==null||ticket.bank!=whole.warpSaved().saved)throw new IllegalArgumentException("Different original saved bank occurrence");
                requireOriginalWarpHostIdle();
            }
            if(destination!=null&&destination.view.selection().rawPeriodByte()!=whole.shared().environment().movement().gates().timeZone())throw new IllegalArgumentException("Different captured live destination period");
            validateOriginalWarpCut(phase,ticket,assets,scene,state,sounds,ipx,destination);
        }
    }
    void serviceOriginalR11Loading(int held){
        if((held&~255)!=0)throw new IllegalArgumentException("Native held BYTE required");
        if(r11ServiceActive||originalDestinationEntering)throw new IllegalStateException("Original R11 loading reentry");originalR11FirstLoad();
        if(originalWarpPhase==OriginalWarpPhase.DESTINATION_SELECTED){enterOriginalPastDestination();return;}
        if(originalWarpProgrammeSelected())validateOriginalWarpProgrammeOwner(); // refusal stays outside fatal source work
        r11ServiceActive=true;
        try{originalLoadingSource.requireRetained();r11ServiceLoadingSource=originalLoadingSource;
        if(originalWarpProgrammeSelected()){serviceOriginalWarpProgramme(held);return;}
        if(r11LoadingState.phase()==SonicCdR31aLoadingVisit.Phase.RETURNED){
            if(r11ReturnKind==SonicCdR31aLoadingVisit.ReturnKind.NEXT_LEVEL){originalR11StageCompleteIpx();return;}
            if(r11ReturnKind==SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART)
                throw new IllegalStateException("Marked original RTS lacks its private returned occurrence");
            throw SonicCd.unsupported("Original R11 native programme return owner: "+r11ReturnKind);
        }
        CdLoadingWaits.service(r11LoadingState.phase(),held,new CdLoadingWaits.Programme(){
            public void plc(int row){serviceOriginalR11Plc(row);}
            public void palette(int row,boolean initial){serviceOriginalR11Fade(row,initial);}
            public void ordinary(int row){if(r11World==null||r11OrdinaryAccess!=null)throw new IllegalStateException("No original R11 World");var actual=new OriginalR11Access();r11OrdinaryAccess=actual;try{r11World.runOriginalR11(row,actual);}finally{r11OrdinaryAccess=null;}}
            public void returnWait(int row){
                if(r11ReturnKind==SonicCdR31aLoadingVisit.ReturnKind.NEXT_LEVEL)
                    playerFrame.serviceOldProgrammeExitLoad(row,playerFrame.state(),playerFrame.environment(),SonicCdGameModule.this::publishOriginalR11Loading,SonicCdGameModule.this::copyAndDrawOriginalR11,()->originalR11Art(r11LoadingArt::serviceFast));
                else playerFrame.serviceLevelLoad(row,SonicCdGameModule.this::publishOriginalR11Loading,SonicCdGameModule.this::copyAndDrawOriginalR11,()->originalR11Art(r11LoadingArt::serviceFast));
                originalR11VIntReturned();r11LoadingState=r11LoadingState.phase(SonicCdR31aLoadingVisit.Phase.RETURNED);
            }
        });
        }catch(OriginalR11TransferStopped unfinished){throw unfinished;}
        catch(com.openggf.game.soniccd.resources.CdR11aSectionArt.SectionArtException failure){r11LoadingPrefix=r11LoadingPrefix.art(failure.state());r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        catch(RuntimeException|Error failure){r11Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        finally{r11ServiceLoadingSource=null;r11ServiceActive=false;}
    }
    private void serviceOriginalR11Plc(int held){
        var phase=r11LoadingState.phase();CdLoadingWaits.plc(held,new CdLoadingWaits.Plc(){
                public void selectorC(int row){playerFrame.serviceLevelLoad(row,SonicCdGameModule.this::publishOriginalR11Loading,SonicCdGameModule.this::copyAndDrawOriginalR11,()->originalR11Art(r11LoadingArt::serviceFast));originalR11VIntReturned();}
                public boolean processAndTestQueue(){originalR11Art(r11LoadingArt::prepare);return r11LoadingPrefix.section().queue().headSource()==0&&r11LoadingPrefix.section().remainingTilesWord()==0;}
                public void returned(){if(phase==SonicCdR31aLoadingVisit.Phase.FIRST_PLC)loadOriginalR11Data();else finishOriginalR11Data();}
            });
    }
    private void serviceOriginalR11Fade(int held,boolean initial){
        var phase=r11LoadingState.phase();CdLoadingWaits.palette(held,new CdLoadingWaits.Palette(){
                public void selector12(int row){java.util.function.BiConsumer<Runnable,Runnable> service=(publish,fast)->{
                    if(playerFrame.environment().movement().gates().zoneAct()==1&&r11LoadingPrefix.main().level().levelRestart()==2)
                        playerFrame.serviceOldProgrammeExitPaletteFade(row,playerFrame.state(),playerFrame.environment(),publish,fast);
                    else playerFrame.serviceLevelPaletteFade(row,publish,fast);
                };service.accept(()->{publishOriginalR11Loading();if(r11LoadingPrefix.main().level().fadeEnableDisplay()==1){var l=r11LoadingPrefix.main().level();r11LoadingPrefix=r11LoadingPrefix.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),2,l.powerup(),l.timeOver()));r11LoadingState=r11LoadingState.fade(r11LoadingState.fadeSteps(),true);var i=r11FirstLoad.selection().inputs();projectOriginalR11Selection(new CdOriginalR11Display.Inputs(i.selectedCpu(),i.selectedSat(),i.palette(),i.hscroll(),i.vscrollLongBits(),true,r11FirstLoad.foreground(),r11FirstLoad.background(),r11LoadingPrefix.section().residency(),playerFrame.artState()));}},()->originalR11Art(r11LoadingArt::serviceFast));originalR11VIntReturned();}
                public void colours(){
                    if(initial){
                        var pair=new com.openggf.game.soniccd.resources.CdLevelPaletteFade.Palettes(r11LoadingPrefix.normalPalette(),r11WaterPalette);
                        var next=phase==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE?com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepToWhite(pair):com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepToBlack(pair);
                        r11LoadingPrefix=r11LoadingPrefix.palette(next.palette());r11WaterPalette=next.waterPalette();
                    }else{var normal=phase==SonicCdR31aLoadingVisit.Phase.FROM_WHITE?com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepFromWhite(r11LoadingPrefix.normalPalette(),r11LoadingPrefix.fadePalette()):com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepFromBlack(r11LoadingPrefix.normalPalette(),r11LoadingPrefix.fadePalette(),0x202F);r11LoadingPrefix=r11LoadingPrefix.palette(normal);/* Both native From paths leave water loading to the source completion below. */}
                }
                public void processPlcs(){originalR11Art(r11LoadingArt::prepare);}
                public boolean countAndTestLast(){r11LoadingState=r11LoadingState.fade(r11LoadingState.fadeSteps()+1,r11LoadingState.displayEnabled());return CdLoadedLevelSetup.returnedFade(r11LoadingState.fadeSteps());}
                public void returned(){if(initial)originalR11AfterInitialFade(playerFrame,false,phase==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,true);else CdLoadedLevelSetup.completedFade(r11LoadingState.fadeSteps(),originalR11LoadedSetup());}
            });
    }
    private void loadOriginalR11Data(){
        requireOriginalR11Service();
        CdInitialLevelArt.load(new CdInitialLevelArt.Port(){
            public CdCommand selectMusic(){requireOriginalR11Service();var g=playerFrame.environment().movement().gates();return originalLoadingSource.selectLevelMusic(g.zoneAct(),g.timeZone(),playerFrame.environment().globals().timeAttackMode(),r11LoadingPrefix.goodFuture());}
            public void publishMusic(CdCommand command){requireOriginalR11Service();var old=r11FirstLoad;r11FirstLoad=new R11FirstLoad(old.selection(),old.foreground(),old.background(),old.copies(),false,false,old.completedWaits(),List.of(command));}
            public int levelPlcId(){return originalLoadingSource.levelPlcId();}
            public void loadLevel(int id){originalR11Art(a->r11LoadingArt.loadImmediateList(a,id));var old=r11FirstLoad;r11FirstLoad=new R11FirstLoad(old.selection(),old.foreground(),old.background(),old.copies(),true,false,old.completedWaits(),old.music());}
            public void loadStandard(){originalR11Art(a->r11LoadingArt.loadImmediateList(a,1));var old=r11FirstLoad;r11FirstLoad=new R11FirstLoad(old.selection(),old.foreground(),old.background(),old.copies(),old.levelLoaded(),true,old.completedWaits(),old.music());}
        });
        var oldClears=r11LoadingState;
        r11LoadingState=new R11LoadingState(oldClears.phase(),oldClears.scroll(),oldClears.globals(),oldClears.reserved(),oldClears.flowers(),oldClears.cpuSat(),oldClears.blocks(),oldClears.layout(),oldClears.nativeCarry(),oldClears.shieldCpu(),oldClears.noBumperByte(),oldClears.bossStartedByte(),oldClears.fadeSteps(),oldClears.displayEnabled(),List.of());
        CdLevelStartStorage.clear(cut->{if(!originalWaterStage()||cut!=CdLevelStartStorage.Cut.UNKNOWN_BUFFER)clearOriginalR11(cut);});
        // Main.ClearScreen owns both planes, SAT640 (including the water-fade alias),
        // its extra LONG into waterPalette, HScroll1024 and its extra primary-header LONG.
        // The latter alias values are already actual zero outputs of the reached clear owners.
        var old=r11LoadingState;var b=old.scroll().background();var s=old.scroll();
        var bg=CdLevelStartStorage.bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),loadingZero(1024),0);
        byte[] sat=old.cpuSat().copy();java.util.Arrays.fill(sat,(byte)0);
        byte[] water=r11WaterPalette.copy();java.util.Arrays.fill(water,0,4,(byte)0);r11WaterPalette=CdAssetBytes.copyOf(water);
        if(playerFrame.state().header().slotId()!=0||playerFrame.state().header().tile()!=0||playerFrame.state().motion().spriteFlags()!=0)throw new IllegalStateException("Unowned original primary-header ClearScreen alias");
        initializeOriginalWater();
        r11LoadingState=new R11LoadingState(old.phase(),new SonicCdR31aLoadingVisit.Scroll(s.foreground(),bg,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()),old.globals().drown(),old.reserved(),old.flowers(),CdAssetBytes.copyOf(sat),old.blocks(),old.layout(),old.nativeCarry(),old.shieldCpu(),old.noBumperByte(),old.bossStartedByte(),old.fadeSteps(),old.displayEnabled(),old.clears());
        retainOriginalR11Planes(new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State(loadingZero(4096)),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State(loadingZero(4096)),r11FirstLoad.copies());
        var sonic=originalLoadingSource.playerPalette();byte[] normal=r11LoadingPrefix.normalPalette().copy(),fade=r11LoadingPrefix.fadePalette().copy();System.arraycopy(sonic.copy(),0,normal,0,32);r11LoadingPrefix=r11LoadingPrefix.palette(CdAssetBytes.copyOf(normal));System.arraycopy(sonic.copy(),0,fade,0,32);r11LoadingPrefix=r11LoadingPrefix.palettes(r11LoadingPrefix.normalPalette(),CdAssetBytes.copyOf(fade));
        initializeOriginalR11SizeScroll();
        try{var blocks=r11LoadingArt.decodeBlocksToRam(r11LoadingPrefix.section());r11LoadingPrefix=r11LoadingPrefix.art(blocks.state()).blocks(r11LoadingPrefix.blockDefinitions().withCompletedPrefix(blocks.blocks()));r11LoadingState=r11LoadingState.geometry(Optional.of(blocks.blocks()),r11LoadingState.layout());}
        catch(com.openggf.game.soniccd.resources.CdR11aSectionArt.SectionArtException failure){r11LoadingPrefix=r11LoadingPrefix.art(failure.state());failure.completedRamPrefix().ifPresent(p->{r11LoadingPrefix=r11LoadingPrefix.blocks(r11LoadingPrefix.blockDefinitions().withCompletedPrefix(p));r11LoadingState=r11LoadingState.geometry(Optional.of(p),r11LoadingState.layout());});throw failure;}
        r11LoadingState=r11LoadingState.geometry(r11LoadingState.blocks(),Optional.of(originalLoadingSource.retainedPlayerSource().terrain().layout().ram()));
        byte[] target=r11LoadingPrefix.fadePalette().copy();System.arraycopy(originalLoadingSource.levelPalette().copy(),0,target,32,96);r11LoadingPrefix=r11LoadingPrefix.palettes(r11LoadingPrefix.normalPalette(),CdAssetBytes.copyOf(target));
        var l=r11LoadingPrefix.main().level();if(l.spawnMode()!=0)originalR11Art(a->r11LoadingArt.loadSection(a,r11LoadingState.scroll().foreground().cameraX()));else if((l.plcLoadFlags()&2)!=0){int plc=originalLoadingSource.blocksPlcId();if(plc!=0)originalR11Art(a->r11LoadingArt.appendList(a,plc));}
        initializeOriginalR11Planes();
        long pointer=originalLoadingSource.collisionPointer(playerFrame.environment().movement().gates().zoneAct());
        if(pointer!=originalLoadingSource.collisionSourceAddress())throw new IllegalStateException("Actual R11 collision source differs");
        var n=r11LoadingState.nativeCarry();r11LoadingState=r11LoadingState.carry(new SonicCdR31aWorldHost.NativeCarry(java.util.OptionalLong.of(pointer),n.fadeInfoWithoutTail(),n.rngSeedLong(),n.targetRightBoundWord(),n.amyCapturedByte())).phase(SonicCdR31aLoadingVisit.Phase.LEVEL_PLC);
    }
    /** Actual synchronous source call; no public DTO can construct or retain it. Each reached
     * read/store checks complete State+Environment plus the common image before and after.
     * First fault escapes unchanged; finally retires the scope without retry or rollback. */
    private final class OriginalLoadingAnimationCall implements CdLoadedLevelSetup.LoadingAnimation {
        private final CdPlayerFrameRuntime frame=playerFrame;
        private final com.openggf.game.soniccd.level.CdLevelLoadingSource data=originalLoadingSource;
        private final Object lifetime=r11LevelLifetime;private final Thread thread=Thread.currentThread();
        private R11LoadingState loading=r11LoadingState;private R11LoadingPrefix prefix=r11LoadingPrefix;
        private CdPlayerBody.State state=frame.state();private CdPlayerBody.Environment environment=frame.environment();
        private Optional<OriginalLoadingAnimation> animation=originalLoadingAnimation;private boolean active=true;
        private OriginalLoadingAnimationCall(){}
        private void require(){requireOriginalR11Service();if(!active||originalLoadingAnimationCall!=this||frame!=playerFrame||data!=originalLoadingSource||lifetime!=r11LevelLifetime||thread!=Thread.currentThread()||loading!=r11LoadingState||prefix!=r11LoadingPrefix||state!=frame.state()||environment!=frame.environment()||animation!=originalLoadingAnimation)throw new IllegalStateException("Expired complete loading animation primitive");}
        private void adopted(){loading=r11LoadingState;prefix=r11LoadingPrefix;animation=originalLoadingAnimation;require();}
        public int timer(int ordinal){require();int v=animation.orElseThrow().timers().unsignedByte(ordinal);require();return v;}
        public int frame(int ordinal){require();int v=animation.orElseThrow().frames().unsignedByte(ordinal);require();return v;}
        public void timer(int ordinal,int value){store(true,ordinal,value);}
        public void frame(int ordinal,int value){store(false,ordinal,value);}
        private void store(boolean timer,int ordinal,int value){require();var ready=animation.orElseThrow().store(timer,ordinal,value);require();originalLoadingAnimation=Optional.of(ready);adopted();}
        public long script(int ordinal){require();long v=data.stageAnimationScript(ordinal);require();return v;}
        public int sourceByte(long address){require();int v=data.stageAnimationByte(address);require();return v;}
        public int sourceLong(long address){require();int v=data.stageAnimationLong(address);require();return v;}
        public void cpuLong(int at,int bits){
            require();var bytes=loading.shieldCpu().copy();Objects.checkFromIndexSize(at,4,bytes.length);
            bytes[at]=(byte)(bits>>>24);bytes[at+1]=(byte)(bits>>>16);bytes[at+2]=(byte)(bits>>>8);bytes[at+3]=(byte)bits;
            var v=loading;var ready=new R11LoadingState(v.phase(),v.scroll(),v.globals(),v.reserved(),v.flowers(),v.cpuSat(),v.blocks(),v.layout(),v.nativeCarry(),CdAssetBytes.copyOf(bytes),v.noBumperByte(),v.bossStartedByte(),v.fadeSteps(),v.displayEnabled(),v.clears());
            require();r11LoadingState=ready;adopted();
        }
        public void publishCpu(int destination,int bytes){
            require();for(int at=0;at<bytes;at+=32){var tile=loading.shieldCpu().slice(at,32);int dest=destination+at;
                require();originalR11Art(a->r11LoadingArt.writeCurrentStageAnimationTile(a,dest,tile));adopted();}
        }
    }
    private OriginalLoadingAnimationCall originalLoadingAnimationCall;
    private void animateOriginalLoadingStage(){
        requireOriginalR11Service();if(!originalWaterStage())return;
        if(originalLoadingAnimationCall!=null)throw new IllegalStateException("Loading animation reentry");
        var f=r11LoadingState.scroll().foreground();
        // Main calls AnimateStageGfx directly: it does not test time_stop. Zero here selects
        // the metadata's ungated call mode; it is never published as a gameplay value.
        var calls=com.openggf.game.soniccd.resources.CdR4ArtServiceMetadata.stageAnimation(originalLoadingSource.artStrategy(),0,playerFrame.paused()?1:0,f.cameraX(),f.cameraYWord());
        var call=new OriginalLoadingAnimationCall();originalLoadingAnimationCall=call;
        try{for(var window:calls.orderedScripts())CdLoadedLevelSetup.animateStage(call,window.scriptOrdinal(),window.destination(),window.bytes());}
        finally{call.active=false;originalLoadingAnimationCall=null;}
    }
    /** Native r4/main191-212: the source-selected WORD read precedes six distinct stores.
     * Failure before the read publishes none; each completed store remains in the common image. */
    private void initializeOriginalWater(){
        requireOriginalR11Service();if(!originalWaterStage())return;
        int zoneAct=playerFrame.environment().movement().gates().zoneAct();
        int period=playerFrame.environment().movement().gates().timeZone();
        // The semantic split BYTE comes from the reached hblank line-value write, not beam timing.
        int split=originalLoadingSource.loadingSplitByte();requireOriginalR11Service();
        var unknown=OriginalLoadingWater.unknown();
        var prior=originalLoadingWater;
        var angle=prior.isPresent()?prior.orElseThrow().swayAngleByte():OptionalInt.of(originalBoot.mainClears().waterSwayAngleByte());
        var pending=prior.isPresent()?prior.orElseThrow().pendingByte():OptionalInt.of(originalBoot.workClears().waterDeferredByte());
        originalLoadingWater=Optional.of(new OriginalLoadingWater(unknown.waterYWord(),unknown.staticYWord(),unknown.targetYWord(),unknown.routineByte(),unknown.fullByte(),unknown.speedByte(),OptionalInt.of(split),pending,unknown.selectedPalette(),false,angle));
        int y=originalLoadingSource.waterHeight((zoneAct&255)*8+(period&127)*2);
        requireOriginalR11Service();
        for(int ordinal=0;ordinal<6;ordinal++){
            requireOriginalR11Service();var before=originalLoadingWater.orElseThrow();
            var ready=before.store(ordinal,ordinal<3?y:ordinal==5?1:0);
            requireOriginalR11Service();if(originalLoadingWater.orElseThrow()!=before)throw new IllegalStateException("Changed water store preimage");
            originalLoadingWater=Optional.of(ready);
        }
    }
    /** r4/main297-305. This adapter runs only after the real returned fade, before Ready.
     * It executes reached source-LONG/store pairs; a read fault retains the written prefix.
     * No public facade result or equal palette can invoke this private continuation. */
    private void loadOriginalReturnedWaterPalette(){
        requireOriginalR11Service();if(!originalWaterStage())return;
        if(r11LoadingState.fadeSteps()!=22||r11LoadingPrefix.main().levelStartedByte()!=0
                ||(r11LoadingPrefix.gameMode()&128)==0||originalLoadingWater.orElseThrow().returnedPaletteLoaded())
            throw new IllegalStateException("Not the actual first returned water fade");
        var binding=originalLoadingSource.waterPalette(0);
        for(int index=0;index<binding.longCount();index++){
            requireOriginalR11Service();var before=r11WaterPalette;var state=playerFrame.state();var environment=playerFrame.environment();
            int bits=(int)binding.bytes().unsignedLong(index*4);
            requireOriginalR11Service();if(r11WaterPalette!=before||playerFrame.state()!=state||playerFrame.environment()!=environment)
                throw new IllegalStateException("Changed returned water palette read/store cut");
            int at=Math.addExact(binding.byteOffset(),index*4);Objects.checkFromIndexSize(at,4,before.length());
            byte[] bytes=before.copy();bytes[at]=(byte)(bits>>>24);bytes[at+1]=(byte)(bits>>>16);bytes[at+2]=(byte)(bits>>>8);bytes[at+3]=(byte)bits;
            r11WaterPalette=CdAssetBytes.copyOf(bytes);
        }
        originalLoadingWater=Optional.of(originalLoadingWater.orElseThrow().returnedPalette());
    }
    private void validateOriginalWaterRestore(R11EntryImage r){
        Objects.requireNonNull(r.water());Objects.requireNonNull(r.animation());if(originalLoadingAnimationCall!=null)throw new IllegalStateException("In-flight loading animation cannot be captured/restored");
        if(!originalWaterStage()){if(r.water().isPresent())throw new IllegalArgumentException("Water image on standard programme");return;}
        var phase=r.loading().phase();boolean reached=phase!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC;
        if(reached?r.scrollFields().size()!=OriginalScrollField.values().length:!r.scrollFields().isEmpty())throw new IllegalArgumentException("Incomplete native InitScroll store image");
        if(r.animation().isPresent()!=reached)throw new IllegalArgumentException("Actual stage-animation clear/source cut differs");
        if(r.water().isPresent()!=reached)throw new IllegalArgumentException("Water stores differ from actual loading cut");
        if(reached){var w=r.water().orElseThrow();
            if(w.swayAngleByte().isEmpty()||w.pendingByte().isEmpty())throw new IllegalArgumentException("Unknown original clear/retained water scalars");
            if(w.waterYWord().isEmpty()||w.staticYWord().isEmpty()||w.targetYWord().isEmpty()||w.routineByte().isEmpty()||w.fullByte().isEmpty()||w.speedByte().isEmpty()
                    ||w.returnedPaletteLoaded()!=(phase==SonicCdR31aLoadingVisit.Phase.READY))
                throw new IllegalArgumentException("Incomplete water/current/loading capture pair");
        }
    }
    /** TT's actual first UpdateScroll: entry flag clears -> common foreground arithmetic ->
     * native R41Events target store -> existing TT background kernel. No PP event selection.
     * N1ecc5f3 r4/scroll_1a165-182; r4/events.asm:59-61. This is only loading, not an ordinary binding. */
    private void initializeOriginalWaterScroll(){
        requireOriginalR11Service();var s=r11LoadingState.scroll();var f=s.foreground();var b=s.background();
        if((f.scrollLock()>>>8)!=0)return;
        f=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),0,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
        b=CdLevelStartStorage.bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());
        storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,b,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
        s=r11LoadingState.scroll();var m=playerFrame.state().motion();var e=playerFrame.environment();
        var input=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Input(new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),e.movement().bounds(),s.topBoundWord(),e.globals().cameraXCentre(),e.movement().globals().cameraYCentre(),playerFrame.stageSource().variant(),r11LoadingPrefix.main().level().updateHudTime(),320,224);
        var out=com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.stepAfterEvents(s.foreground(),input);
        playerFrame.applyScrollFeedback(out.bounds(),out.cameraYCentre());
        // R41Events has exactly one target-bottom store. Target equals current on this genuine
        // loading path; no ordinary convergence is assumed or supplied by this adapter.
        var next=out.state();int target=originalLoadingSource.initialEventTargetBottomWord();
        next=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(next.cameraX(),next.cameraY16_16(),next.horizontalCrossed(),next.verticalCrossed(),next.scrollXDiff(),next.scrollYDiff(),next.scrollFlags(),target,next.destLeftBound(),next.bottomBoundShift(),next.scrollLock());
        storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(next,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
        if(out.bounds().bottom()!=target)throw SonicCd.unsupported("Original loading R41Events bound convergence is not this InitScroll entry");
        s=r11LoadingState.scroll();f=s.foreground();
        try{
            var result=com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.step(originalLoadingSource.waterScrollProfile(),s.background(),new com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8,originalLoadingWater.orElseThrow().waterYWord().orElseThrow(),s.waterDeformWord()),originalLoadingSource.waterScrollTables());
            retainOriginalWaterScroll(result,true);
        }catch(com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.ScrollFault original){
            try{retainOriginalWaterScroll(original.prefix(),false);}catch(RuntimeException|Error retention){if(retention!=original)original.addSuppressed(retention);}throw original;
        }
    }
    private void retainOriginalWaterScroll(com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.Result result,boolean completed){
        requireOriginalR11Service();var s=r11LoadingState.scroll();var f=s.foreground();
        f=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags()|(completed?0x400:0),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
        storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,result.state(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),result.deform()));
    }
    private void initializeOriginalR11SizeScroll(){
        requireOriginalR11Service();
        if(originalWaterStage()){
            storeOriginalScrollField(OriginalScrollField.UNUSED_X,0);storeOriginalScrollField(OriginalScrollField.UNUSED_Y,0);
            storeOriginalScrollField(OriginalScrollField.UNUSED_DIE,0);storeOriginalScrollField(OriginalScrollField.UNUSED_TIMER,0);
            storeOriginalScrollField(OriginalScrollField.EVENT_ROUTINE,0);
        }
        var bounds=originalLoadingSource.bounds();
        if(originalWaterStage()){
            storeOriginalScrollField(OriginalScrollField.ROUTINE,bounds.unusedWord());
            // Target left/bottom/right already belong to foreground/nativeCarry; only target top is new storage.
            storeOriginalScrollField(OriginalScrollField.TARGET_TOP,bounds.top());
            storeOriginalScrollField(OriginalScrollField.X_KEEP,(bounds.left()+0x240)&65535);
        }
        var s=r11LoadingState.scroll();var f=s.foreground();
        playerFrame.initializeLevelSizePlayerValues(new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bounds.bottom(),playerFrame.environment().movement().bounds().bossFight()),bounds.cameraCentreY());
        f=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),16,16,f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),bounds.bottom(),bounds.left(),f.bottomBoundShift(),f.scrollLock());storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),bounds.top(),s.waterDeformWord()));
        // N1ecc5f3a PP Scroll (Act 1 Present).asm:61-84. All preceding level-size
        // stores remain completed if this exact native call faults.
        if(r11LoadingPrefix.main().level().spawnMode()==2)loadOriginalR11TimeWarp();
        else if(r11LoadingPrefix.main().level().spawnMode()!=0)loadOriginalR11Checkpoint();
        else {var start=originalLoadingSource.initialCentre();playerFrame.initializeLevelSizeCentrePlayerValues(start.centreX(),start.centreY());}
        int startX=playerFrame.state().motion().centreX16_16()>>>16,startY=playerFrame.state().motion().centreY16_16()>>>16;
        s=r11LoadingState.scroll();f=com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.checkpointSetupCamera(s.foreground(),startX,startY,playerFrame.environment().movement().bounds());
        var bg=originalWaterStage()?com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.initialize(originalLoadingSource.waterScrollProfile(),s.background(),f.cameraX(),f.cameraYWord())
            :originalLoadingSource.standardBackgroundProgram()==com.openggf.game.soniccd.level.CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS
            ?com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll.init(s.background(),f)
            :com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.init(s.background(),new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.InitRegisters(f.cameraYWord(),f.cameraX(),startX));storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,bg,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
        var n=r11LoadingState.nativeCarry();r11LoadingState=r11LoadingState.carry(new SonicCdR31aWorldHost.NativeCarry(n.collisionPointer(),n.fadeInfoWithoutTail(),n.rngSeedLong(),OptionalInt.of(bounds.right()),n.amyCapturedByte()));
        s=r11LoadingState.scroll();var m=playerFrame.state().motion();var e=playerFrame.environment();
        if(originalWaterStage()){storeOriginalScrollField(OriginalScrollField.LOOP_CHUNKS,originalLoadingSource.loopChunksLong());initializeOriginalWaterScroll();return;}
        var input=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Input(new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),e.movement().bounds(),s.topBoundWord(),e.globals().cameraXCentre(),e.movement().globals().cameraYCentre(),playerFrame.stageSource().variant(),r11LoadingPrefix.main().level().updateHudTime(),320,224);
        var out=com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.stepForPeriodBackground(s.foreground(),input,e.movement().gates().timeZone());playerFrame.applyScrollFeedback(out.bounds(),out.cameraYCentre());
        f=out.state();storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
        var back=originalLoadingSource.standardBackgroundProgram()==com.openggf.game.soniccd.level.CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS
            ?com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll.step(r11LoadingState.scroll().background(),new com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8))
            :com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.bind(source.assets()).step(r11LoadingState.scroll().background(),new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)m.xVelocity(),m.centreX16_16()>>>16,m.playerControl(),f.scrollLock()>>>8)).state();
        s=r11LoadingState.scroll();f=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags()|0x400,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());storeR11Scroll(s,new SonicCdR31aLoadingVisit.Scroll(f,back,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
    }
    private OriginalR11WarpLoadCall r11WarpLoad;
    /** Only the actual original InitLevelSize.StartPos spawn2 call enters this issuer.
     * It delegates every source read and gate to the existing shared native helper.
     * Its current tuple is private, synchronous and expires even on the first fault. */
    private void loadOriginalR11TimeWarp(){
        requireOriginalR11Service();
        if(!originalPastStage()||r11WarpLoad!=null||r11LoadingPrefix.main().level().spawnMode()!=2)
            throw new IllegalStateException("No original Past LoadTimeWarpData occurrence");
        originalPastArrival.require();var call=new OriginalR11WarpLoadCall();r11WarpLoad=call;
        try{CdTimeWarpLoadData.load(call.saved,call);call.require();if(call.cursor!=22)throw new IllegalStateException("Incomplete actual warp load");}
        finally{call.active=false;r11WarpLoad=null;}
    }
    private final class OriginalR11WarpLoadCall implements CdTimeWarpLoadData.Port {
        private final CdPlayerFrameRuntime frame=playerFrame;
        private final Object lifetime=r11LevelLifetime,bankLifetime=r11TimeWarpSavedLifetime;
        private final Object sourceOwner=originalLoadingSource,placement=r11Placement;
        private final Thread thread=Thread.currentThread();
        private final CdTimeWarpSaveData.State saved=Objects.requireNonNull(r11TimeWarpSaved);
        private CdPlayerBody.State actor=frame.state();private CdPlayerBody.Environment environment=frame.environment();
        private final Object display=frame.display(),art=frame.artState(),clock=frame.vIntClock();
        private final boolean paused=frame.paused();
        private R11LoadingState expected=r11LoadingState;private R11LoadingPrefix prefix=r11LoadingPrefix;
        private boolean active=true;private int cursor;
        private CdTimeWarpLoadData.StoreTarget observedTarget;private OptionalInt observed;
        private void require(){
            requireOriginalR11Service();originalPastArrival.require();
            if(!active||r11WarpLoad!=this||thread!=Thread.currentThread()||playerFrame!=frame
                ||r11LevelLifetime!=lifetime||r11TimeWarpSavedLifetime!=bankLifetime||r11TimeWarpSaved!=saved
                ||originalLoadingSource!=sourceOwner||r11Placement!=placement||r11LoadingState!=expected||r11LoadingPrefix!=prefix
                ||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=art
                ||frame.vIntClock()!=clock||frame.paused()!=paused||expected.phase()!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC
                ||frame.environment().movement().gates().zoneAct()!=0||prefix.main().level().spawnMode()!=2)
                throw new IllegalStateException("Stale/foreign original warp-load primitive");
        }
        public CdTimeWarpSaveData.State currentSaved(){require();return saved;}
        public OptionalInt readLive(CdTimeWarpLoadData.LiveRead field){
            require();
            if(field==CdTimeWarpLoadData.LiveRead.ZONE&&cursor==20){cursor=21;return OptionalInt.of(frame.environment().movement().gates().zoneAct()>>>8);}
            if(field==CdTimeWarpLoadData.LiveRead.SPAWN_MODE&&cursor==21){cursor=22;return OptionalInt.of(prefix.main().level().spawnMode());}
            throw new IllegalStateException("Original warp-load live-read cursor differs");
        }
        private OptionalInt value(CdTimeWarpLoadData.StoreTarget target){
            var g=expected.globals();return switch(target){
                case PRIMARY_X,PRIMARY_Y,PRIMARY_FLAGS,PRIMARY_GVEL,PRIMARY_X_VELOCITY,PRIMARY_Y_VELOCITY,BOTTOM_BOUND,MINI_SONIC,LEFT_BOUND -> OptionalInt.of(frame.readTimeWarpLoadField(target));
                case RINGS -> OptionalInt.of(g.ring().rings());
                case LIVES_FLAGS -> OptionalInt.of(g.ring().livesFlags());
                case TIME_LONG -> originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.TIME_LONG);
                case WATER_ROUTINE -> originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.WATER_ROUTINE);
                default -> OptionalInt.of(originalCheckpointCameraWord(expected.scroll(),warpCameraField(target)));
            };
        }
        public OptionalInt read(CdTimeWarpLoadData.StoreTarget target){require();observedTarget=Objects.requireNonNull(target);observed=value(target);return observed;}
        public void commit(CdTimeWarpLoadData.StoreCall call){
            require();Objects.requireNonNull(call);
            if(cursor>=20||call.instruction()!=CdTimeWarpLoadData.NativeStore.values()[cursor]
                ||observedTarget!=call.target()||!call.expectedValue().equals(observed)||!call.expectedValue().equals(value(call.target())))
                throw new IllegalArgumentException("Original warp-load source order/full preimage differs");
            var next=expected;var nextPrefix=prefix;
            switch(call.target()){
                case PRIMARY_X,PRIMARY_Y,PRIMARY_FLAGS,PRIMARY_GVEL,PRIMARY_X_VELOCITY,PRIMARY_Y_VELOCITY,BOTTOM_BOUND -> frame.applyTimeWarpLoadStore(call);
                case RINGS,LIVES_FLAGS,TIME_LONG,WATER_ROUTINE -> {
                    var target=switch(call.target()){
                        case RINGS -> CdCheckpointLoadData.StoreTarget.RINGS;case LIVES_FLAGS -> CdCheckpointLoadData.StoreTarget.LIVES_FLAGS;
                        case TIME_LONG -> CdCheckpointLoadData.StoreTarget.TIME_LONG;case WATER_ROUTINE -> CdCheckpointLoadData.StoreTarget.WATER_ROUTINE;
                        default -> throw new AssertionError();};
                    var globals=originalCheckpointGlobalStore(expected.globals(),new CdCheckpointLoadData.StoreCall(target,call.expectedValue(),call.value()));
                    next=expected.globals(globals);nextPrefix=prefix.globals(globals);
                }
                default -> next=expected.scroll(originalCheckpointCameraStore(expected.scroll(),new CdCheckpointLoadData.StoreCall(warpCameraField(call.target()),call.expectedValue(),call.value())));
            }
            r11LoadingState=next;r11LoadingPrefix=nextPrefix;expected=next;prefix=nextPrefix;
            actor=frame.state();environment=frame.environment();cursor++;observedTarget=null;observed=null;
        }
    }
    /** These distinct native helpers write the same existing nine camera WORD owners. */
    private static CdCheckpointLoadData.StoreTarget warpCameraField(CdTimeWarpLoadData.StoreTarget target){
        return switch(target){
            case DEST_BOTTOM_BOUND -> CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND;
            case FG_X -> CdCheckpointLoadData.StoreTarget.FG_X;case FG_Y -> CdCheckpointLoadData.StoreTarget.FG_Y;
            case BG_X -> CdCheckpointLoadData.StoreTarget.BG_X;case BG_Y -> CdCheckpointLoadData.StoreTarget.BG_Y;
            case BG2_X -> CdCheckpointLoadData.StoreTarget.BG2_X;case BG2_Y -> CdCheckpointLoadData.StoreTarget.BG2_Y;
            case BG3_X -> CdCheckpointLoadData.StoreTarget.BG3_X;case BG3_Y -> CdCheckpointLoadData.StoreTarget.BG3_Y;
            default -> throw new IllegalArgumentException("Not a shared camera WORD");};
    }
    /** One private issuer at the original StartPos call. Saved/capture/result values
     * cannot construct or reopen it. All fields are transient and expire in finally. */
    public final class OriginalR11CheckpointLoadCall implements CdCheckpointLoadData.Port {
        private final CdPlayerFrameRuntime frame=playerFrame;
        private final CdObjectPlacementRuntime placement=r11Placement;
        private final Object lifetime=r11LevelLifetime;
        private final Thread thread=Thread.currentThread();
        private com.openggf.game.soniccd.player.CdPlayerBody.State actor=frame.state();
        private com.openggf.game.soniccd.player.CdPlayerBody.Environment environment=frame.environment();
        private final Object display=frame.display(),art=frame.artState(),clock=frame.vIntClock();
        private final boolean paused=frame.paused();
        private final CdCheckpointSaveData.State saved=r11LoadingState.globals().checkpointSaved();
        private final CdObjectPlacementRuntime.Snapshot placed=placement.capture();
        private R11LoadingState expected=r11LoadingState;
        private R11LoadingPrefix prefix=r11LoadingPrefix;
        private CdPlayerFrameRuntime.CheckpointLoadPort port;
        private boolean active=true,started;
        private int cursor;
        private CdCheckpointLoadData.StoreTarget observedTarget;
        private OptionalInt observed;
        private OriginalR11CheckpointLoadCall(){}
        /** Cross-package Frame handshake. Constructor and active issuer remain private. */
        public void requireFrame(CdPlayerFrameRuntime actual){
            requireOriginalR11Service();
            if(!active||r11CheckpointLoad!=this||thread!=Thread.currentThread()||actual!=frame
                    ||playerFrame!=frame||r11Placement!=placement||r11LevelLifetime!=lifetime
                    ||r11LoadingState!=expected||r11LoadingPrefix!=prefix
                    ||expected.globals().checkpointSaved()!=saved||frame.state()!=actor||frame.environment()!=environment
                    ||frame.display()!=display||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused
                    ||expected.phase()!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC
                    ||frame.environment().movement().gates().zoneAct()!=0)
                throw new IllegalStateException("No actual original R11 checkpoint load call");
            var now=placement.capture();
            if(!now.status().equals(placed.status())||!now.pool().slots().equals(placed.pool().slots())
                    ||!java.util.Arrays.equals(now.savedFlags().copy(),placed.savedFlags().copy()))
                throw new IllegalStateException("Original checkpoint placement cut changed");
        }
        /** Only the existing Frame can mint a matching active port. No replayed result. */
        public void load(CdPlayerFrameRuntime actual,CdPlayerFrameRuntime.CheckpointLoadPort actualPort){
            requireFrame(actual);
            if(started||!Objects.requireNonNull(actualPort).isOriginalInvocation(this,actual))
                throw new IllegalStateException("Original checkpoint invocation cannot be replayed");
            started=true;port=actualPort;
            // Shared helper owns native saved read/store order, branch gates and first fault.
            // Incoming2 stops at the separately unowned warp transfer before any saved read.
            CdCheckpointLoadData.load(prefix.main().level().spawnMode(),
                    actual.environment().movement().gates().zoneAct()>>>8,saved,this);
            int count=19+((prefix.main().level().spawnMode()&128)!=0?1:0);
            if(cursor!=count)throw new IllegalStateException("Incomplete actual original checkpoint stores");
        }
        private void requireLoad(){
            requireFrame(frame);
            if(!started||port==null||!port.isOriginalInvocation(this,frame))
                throw new IllegalStateException("No current original checkpoint Frame operation");
        }
        @Override public CdCheckpointSaveData.State currentSaved(){requireLoad();return saved;}
        private OptionalInt value(CdCheckpointLoadData.StoreTarget target){
            var g=expected.globals();var r=g.ring();
            return switch(target){
                case SPAWN_MODE->OptionalInt.of(prefix.main().level().spawnMode());
                case PRIMARY_X,PRIMARY_Y,BOTTOM_BOUND,LEFT_BOUND,MINI_SONIC->OptionalInt.of(port.read(frameField(target)));
                case RINGS->OptionalInt.of(r.rings());
                case LIVES_FLAGS->OptionalInt.of(r.livesFlags());
                case TIME_LONG->originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.TIME_LONG);
                case TIME_FRAMES->g.live().knownByte(0xFF1517);
                case TIME_SECONDS->g.live().knownByte(0xFF1516);
                case WATER_ROUTINE->originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.WATER_ROUTINE);
                case WATER_HEIGHT2->originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                case WATER_FULLSCREEN->originalCheckpointAux(g.live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                default->OptionalInt.of(originalCheckpointCameraWord(expected.scroll(),target));
            };
        }
        @Override public OptionalInt read(CdCheckpointLoadData.StoreTarget target){
            requireLoad();observedTarget=Objects.requireNonNull(target);observed=value(target);return observed;
        }
        @Override public void commit(CdCheckpointLoadData.StoreCall call){
            requireLoad();Objects.requireNonNull(call);
            // This original programme is zone0: shared helper suppresses mini/water reads.
            var instruction=cursor<19?CdCheckpointLoadData.NativeStore.values()[cursor]
                    :cursor==19&&(prefix.main().level().spawnMode()&128)!=0?CdCheckpointLoadData.NativeStore.SET_LEFT_BOUND:null;
            if(instruction==null||instruction.target()!=call.target()||observedTarget!=call.target()
                    ||!call.expectedValue().equals(observed)||!call.expectedValue().equals(value(call.target())))
                throw new IllegalArgumentException("Original checkpoint native cursor/full preimage changed");
            var nextState=expected;var nextPrefix=prefix;
            switch(call.target()){
                case SPAWN_MODE->nextPrefix=prefix.level(levelWithSpawn(prefix.main().level(),call.value()));
                case PRIMARY_X,PRIMARY_Y,BOTTOM_BOUND,LEFT_BOUND,MINI_SONIC->port.store(
                        new CdPlayerFrameRuntime.CheckpointLoadStore(frameField(call.target()),call.expectedValue().orElseThrow(),call.value()));
                case RINGS,LIVES_FLAGS,TIME_LONG,TIME_FRAMES,TIME_SECONDS,WATER_ROUTINE,WATER_HEIGHT2,WATER_FULLSCREEN->{
                    var globals=originalCheckpointGlobalStore(expected.globals(),call);
                    nextState=expected.globals(globals);nextPrefix=prefix.globals(globals);
                }
                default->nextState=expected.scroll(originalCheckpointCameraStore(expected.scroll(),call));
            }
            // No callback or fallible work between these current-owner reference stores.
            r11LoadingState=nextState;r11LoadingPrefix=nextPrefix;expected=nextState;prefix=nextPrefix;
            actor=frame.state();environment=frame.environment();
            cursor++;observedTarget=null;observed=null;
        }
        private void expire(){active=false;port=null;observedTarget=null;observed=null;}
    }
    private OriginalR11CheckpointLoadCall r11CheckpointLoad;
    private void loadOriginalR11Checkpoint(){
        requireOriginalR11Service();
        if(r11CheckpointLoad!=null)throw new IllegalStateException("Original checkpoint load reentry");
        var call=new OriginalR11CheckpointLoadCall();r11CheckpointLoad=call;
        try{playerFrame.loadOriginalCheckpoint(call);}
        finally{call.expire();r11CheckpointLoad=null;}
        // Cleanup performs assignments only: cannot replace/suppress the native first fault.
    }
    private static OptionalInt originalCheckpointAux(CdCheckpointLiveAux.State live,CdCheckpointLiveAux.Field field){
        int value=0;
        for(int i=0;i<field.width();i++){
            var b=live.knownByte(field.nativeAddress()+i);if(b.isEmpty())return OptionalInt.empty();
            value=(value<<8)|b.getAsInt();
        }
        return OptionalInt.of(value);
    }
    /** Pure existing-value projections below grant no native invocation authority. */
    static SonicCdR31aWorldHost.Globals originalCheckpointGlobalStore(SonicCdR31aWorldHost.Globals g,CdCheckpointLoadData.StoreCall call){
        var r=g.ring();var live=g.live();
        switch(call.target()){
            case RINGS->r=ringWithCount(r,call.value());
            case LIVES_FLAGS->r=ringWithLivesFlags(r,call.value());
            case TIME_LONG->live=CdCheckpointLiveAux.writeTimeLong(live,call.value());
            case TIME_FRAMES->live=CdCheckpointLiveAux.writeTimeByte(live,CdCheckpointLiveAux.TimeByte.FRAMES,call.value());
            case TIME_SECONDS->live=CdCheckpointLiveAux.writeTimeByte(live,CdCheckpointLiveAux.TimeByte.SECONDS,call.value());
            case WATER_ROUTINE->live=CdCheckpointLiveAux.writeWaterRoutine(live,call.value());
            case WATER_HEIGHT2->live=CdCheckpointLiveAux.writeWaterHeight2(live,call.value());
            case WATER_FULLSCREEN->live=CdCheckpointLiveAux.writeWaterFullscreen(live,call.value());
            default->throw new IllegalArgumentException("Not an original checkpoint scalar store");
        }
        return SonicCdR31aWorldHost.with(g,r,live,g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
    }
    static int originalCheckpointCameraWord(SonicCdR31aLoadingVisit.Scroll s,CdCheckpointLoadData.StoreTarget target){
        var f=s.foreground();var b=s.background();
        return switch(target){
            case DEST_BOTTOM_BOUND->f.destBottomBound();case FG_X->f.cameraX();case FG_Y->f.cameraYWord();
            case BG_X->b.cameraBgXWord();case BG_Y->b.cameraBgYWord();
            case BG2_X->b.cameraBg2XWord();case BG2_Y->b.cameraBg2YWord();
            case BG3_X->b.cameraBg3XWord();case BG3_Y->b.cameraBg3YWord();
            default->throw new IllegalArgumentException("Not an original checkpoint camera store");
        };
    }
    private static int originalCheckpointWord(int raw,int word){return (word<<16)|(raw&65535);}
    static SonicCdR31aLoadingVisit.Scroll originalCheckpointCameraStore(SonicCdR31aLoadingVisit.Scroll s,CdCheckpointLoadData.StoreCall call){
        if(!call.expectedValue().equals(OptionalInt.of(originalCheckpointCameraWord(s,call.target()))))
            throw new IllegalArgumentException("Stale original checkpoint camera WORD");
        var t=call.target();int v=call.value();var f=s.foreground();var b=s.background();
        if(t==CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND||t==CdCheckpointLoadData.StoreTarget.FG_X||t==CdCheckpointLoadData.StoreTarget.FG_Y){
            f=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(
                    t==CdCheckpointLoadData.StoreTarget.FG_X?v:f.cameraX(),
                    t==CdCheckpointLoadData.StoreTarget.FG_Y?originalCheckpointWord(f.cameraY16_16(),v):f.cameraY16_16(),
                    f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),
                    t==CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND?v:f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
        }else{
            b=new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State(
                    t==CdCheckpointLoadData.StoreTarget.BG_X?originalCheckpointWord(b.cameraBgX(),v):b.cameraBgX(),
                    t==CdCheckpointLoadData.StoreTarget.BG_Y?originalCheckpointWord(b.cameraBgY(),v):b.cameraBgY(),
                    t==CdCheckpointLoadData.StoreTarget.BG2_X?originalCheckpointWord(b.cameraBg2X(),v):b.cameraBg2X(),
                    t==CdCheckpointLoadData.StoreTarget.BG2_Y?originalCheckpointWord(b.cameraBg2Y(),v):b.cameraBg2Y(),
                    t==CdCheckpointLoadData.StoreTarget.BG3_X?originalCheckpointWord(b.cameraBg3X(),v):b.cameraBg3X(),
                    t==CdCheckpointLoadData.StoreTarget.BG3_Y?originalCheckpointWord(b.cameraBg3Y(),v):b.cameraBg3Y(),
                    b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());
        }
        return new SonicCdR31aLoadingVisit.Scroll(f,b,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());
    }

    private void initializeOriginalR11Planes(){
        requireOriginalR11Service();var t=originalLoadingSource.retainedPlayerSource().terrain();var s=r11LoadingState.scroll();var f=s.foreground();var bg=originalLoadingSource.backgroundLayout();int cursor=64+bg.unsignedByte(1)*128+bg.unsignedByte(0)+1;var ram=r11LoadingState.layout().orElseThrow();var metadata=new com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.CallerMetadata(cursor<ram.length()?OptionalInt.of(ram.unsignedByte(cursor)):OptionalInt.empty());
        var writer=com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.fromTables(t.blocks(),t.chunks(),ram).withBlockDefinitions(r11LoadingPrefix.blockDefinitions());
        try{var out=writer.initialize(r11FirstLoad.foreground(),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16()),f.scrollFlags(),metadata);retainOriginalR11Planes(out.state(),r11FirstLoad.background(),r11FirstLoad.copies());}
        catch(com.openggf.game.soniccd.scroll.CdR11bForegroundPlane.InvalidPlane original){retainOriginalR11Planes(original.completedPrefix().state(),r11FirstLoad.background(),r11FirstLoad.copies());throw original;}
        var b=s.background();var live=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.LiveCameras(new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));
        try{var backgroundWriter=originalLoadingSource.backgroundPlane().withBlockDefinitions(r11LoadingPrefix.blockDefinitions());var flags=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3());
            var result=originalWaterStage()?backgroundWriter.initializeWaterCompressedRows(r11FirstLoad.background(),live,flags,f.cameraX()):backgroundWriter.initialize(r11FirstLoad.background(),live,flags);
            retainOriginalR11Planes(r11FirstLoad.foreground(),result.state(),r11FirstLoad.copies());}
        catch(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.InvalidPlane original){retainOriginalR11Background(original,r11FirstLoad.copies(),false);throw original;}
    }
    private CdLoadedLevelSetup.Programme<com.openggf.game.soniccd.resources.CdR11aSectionArt.State> originalR11LoadedSetup(){return new CdLoadedLevelSetup.Programme<>(){
        public CdPlayerFrameRuntime frame(){requireOriginalR11Service();return playerFrame;}
        public CdLoadedLevelSetup.Values<com.openggf.game.soniccd.resources.CdR11aSectionArt.State> values(){requireOriginalR11Service();return new CdLoadedLevelSetup.Values<>(r11LoadingPrefix.section(),r11LoadingPrefix.main().level(),r11LoadingState.reserved(),programServices.initialization(),r11LoadingPrefix.normalPalette(),r11LoadingPrefix.fadePalette());}
        public void store(CdLoadedLevelSetup.Values<com.openggf.game.soniccd.resources.CdR11aSectionArt.State> v,CdLoadedLevelSetup.Event event){requireOriginalR11Service();r11LoadingArt.validateState(v.art());var services=programServices;if(v.initialization()!=services.initialization())services=new ProgramServicesState(services.loads(),v.initialization(),services.environment(),services.completedCalls());var prefix=r11LoadingPrefix.level(v.level()).art(v.art()).palettes(v.normal(),v.fade());var state=r11LoadingState.reserved(v.reserved());
            switch(event){case BEFORE_INPUT_TIMERS->{if(originalWaterStage()){var pool=r11Placement.pool();var before=pool.state(32);var ready=before.moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.ID,4);requireOriginalR11Service();if(pool.state(32)!=before)throw new IllegalStateException("Changed source-selected first dynamic spawn slot");pool.write(ready);}}case ANIMATED_TILES->{animateOriginalLoadingStage();state=r11LoadingState;prefix=r11LoadingPrefix;}case SECONDARY_TIMER->state=state.globals(state.globals().secondaryBored(0));case TITLE->{var p=playerFrame.prepareObjectTitleCardGlobal(playerFrame.state(),playerFrame.environment(),com.openggf.game.soniccd.objects.CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1);playerFrame.validatePreparedObjectSolidPublication(p);playerFrame.applyPreparedObjectSolidPublication(p);}case BEGIN_MAIN->state=state.globals(state.globals().beginMain(r11LoadingPrefix.main().level().spawnMode()));case FROM_WHITE->state=state.globals(state.globals().fromWhite());default->{}}
            programServices=services;r11LoadingState=state;r11LoadingPrefix=prefix.globals(state.globals());}
        public void lifeIcon(int period){originalR11Art(a->r11LoadingArt.loadLifeIcon(a,period));}
        public com.openggf.game.soniccd.resources.CdR11aSectionArt.State clearSectionHighByte(com.openggf.game.soniccd.resources.CdR11aSectionArt.State a){return r11LoadingArt.clearSectionHighByte(a);}
        public void unavailablePowerup(){if(originalPastStage()){copyOriginalR11Powerup();return;}throw new UnsupportedOperationException("Original R11 active powerup loading owner");}public void unavailablePeriod(){throw new UnsupportedOperationException("Original R11 loading period owner");}
        public int gameMode(){requireOriginalR11Service();return r11LoadingPrefix.gameMode();}
        public void completed(CdLoadedLevelSetup.Ready ready){requireOriginalR11Service();loadOriginalReturnedWaterPalette();requireOriginalR11Service();r11LoadingPrefix=r11LoadingPrefix.completed(ready);r11LoadingState=r11LoadingState.phase(ready.phase());}
    };}
    private void finishOriginalR11Data(){
        // The shared setup publishes into the current image; select phase only after those stores return.
        var phase=CdLoadedLevelSetup.afterSecondPlc(originalR11LoadedSetup());r11LoadingState=r11LoadingState.phase(phase);
    }
    private CdOriginalBootPrefix.Continuation r11Permit;
    private CdSelectedProfileVisit.State r11SelectedFinal;
    private CdPlayerFrameRuntime r11PreparedFrame;
    private CdPlayerFrameRuntime r11EntryFrame;
    private Thread r11Thread;
    private com.openggf.level.LevelManager r11EntryLevelManager;
    private Level r11EntryLevel;
    private java.util.OptionalLong r11EntryLoadGeneration=java.util.OptionalLong.empty();
    private boolean r11StartActive,r11StartComplete,r11Retired;
    private void requireR11Start(CdSelectedProfileVisit.Publication original){
        var world=SessionManager.getCurrentWorldSession();
        if(!r11StartActive||r11Retired||r11StartComplete||originalBoot==null||r11Permit==null||source!=originalBoot.source()
                ||world==null||world!=programWorld||world.getDataSource()!=source||world.rootGameModule()!=this||world.resolvedGameModule()!=this
                ||selectedProfile.state()!=r11SelectedFinal||playerFrame!=null||playerActor!=null||openingObjects!=null||levelProgram!=null)
            throw new IllegalStateException("No current original selected R11 Start");
        selectedProfile.requireProgrammeHandoff(this,original,CdSelectedProfileVisit.Programme.R11A);
    }
    private void enterOriginalR11Programme(CdSelectedProfileVisit.Publication original){
        originalLoadingProgramme=CdSelectedProfileVisit.Programme.R11A;originalLoadingPublication=original;
        try{originalLoadingSource=source.assets().identity().variant()==CdReleaseVariant.USA
                ?source.assets().retainedStandardLoadingSource()
                :com.openggf.game.soniccd.level.CdLevelLoadingSource.fromOriginalR11(source.assets());}
        catch(IOException failure){throw new UncheckedIOException("Actual original R11 standard source read failed",failure);}
        r11Permit=originalBoot.issueR11(this,original);r11SelectedFinal=original.after();programWorld=SessionManager.getCurrentWorldSession();playerMode=SessionManager.getCurrentGameplayMode();
        r11Thread=Thread.currentThread();r11EntryLevelManager=playerMode.getLevelManager();
        // Before engine assembly the actual mode can have no LevelManager. Retain that
        // absence as part of this cut, never install one or invent a generation zero.
        r11EntryLevel=r11EntryLevelManager==null?null:r11EntryLevelManager.getCurrentLevel();
        r11EntryLoadGeneration=r11EntryLevelManager==null?java.util.OptionalLong.empty():java.util.OptionalLong.of(r11EntryLevelManager.getCompletedProductionLoadGeneration());r11StartActive=true;
        try{
            requireR11Start(original);
            // The private loader already authenticated the actual original R11A file, including
            // MMD header and whole programme hash. No named/equal Source record grants this entry.
            var binding=source.assets().identity();var profile=source.assets().profile();
            if(binding.variant()!=CdReleaseVariant.USA||binding.file()!=CdNativeFile.R11A
                    ||binding.domain()!=com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    ||!binding.mmdSha256().equals(profile.expectedMmdSha256())||!binding.observedWholeDiscSha256().equals(source.release().normalizedSha256()))
                throw new IllegalArgumentException("Actual original R11 programme source differs");
            var loads=SonicCdProgramLoadServices.loadLevelR1Pcm(programServices.loads(),source.timeWarpPrograms().orElseThrow());
            var loaded=withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM,loads,programServices.initialization());requireR11Start(original);programServices=loaded;
            enterOriginalProgrammeStart(()->requireR11Start(original),()->{
                var video=playerPresentation.initializeProgramVideo(originalR11LoadingUnknownPatterns(),originalBoot.workClears().playerArt());
                programStartVideo=video;
            });
            // Original IPX RunMMD's work clear and selected stores, not ColdSnapshot/DEV defaults.
            var clear=originalBoot.workClears();var environment=originalProgrammeEnvironment(r11SelectedFinal.globals().orElseThrow());
            r11PreparedFrame=CdPlayerFrameRuntime.fromPresentEntry(source.assets(),clear.primary(),environment,programStartVideo.playerArt(),new CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.empty(),0));
            bindOriginalR11FinishTimers(r11PreparedFrame);
            enterOriginalLevelStart(original,()->requireR11Start(original));
            var ready=r11Permit.prepareR11Start(r11PreparedFrame,original);requireR11Start(original);
            playerFrame=r11PreparedFrame;r11EntryFrame=r11PreparedFrame;r11Permit.adoptR11Start(ready);r11StartComplete=true;
        }catch(SonicCdProgramLoadServices.Unsupported stopped){programServices=new ProgramServicesState(stopped.completedPrefix(),programServices.initialization(),programServices.environment(),programServices.completedCalls());r11Retired=true;originalBoot.retire(programWorld);throw stopped;}
        catch(RuntimeException|Error originalFailure){r11Retired=true;originalBoot.retire(programWorld);try{if(r11PreparedFrame!=null)r11PreparedFrame.close();}catch(RuntimeException|Error close){if(close!=originalFailure)originalFailure.addSuppressed(close);}throw originalFailure;}
        finally{r11PreparedFrame=null;r11StartActive=false;}
        if(r11EntryLevelManager!=null)assembleOriginalR11();
    }
    /** One shared native Initialization.Start instruction sequence. Guards bracket each reached
     * call; callbacks publish only their actual first-fault prefixes. No result is replayed as calls. */
    private void enterOriginalProgrammeStart(Runnable requireCurrent,Runnable initializeVideo){
        CdProgramInitialization.enterLevelProgram(new CdProgramInitialization.StartPort(){
            public CdProgramInitialization.State initialization(){requireCurrent.run();return programServices.initialization();}
            public CdProgramInitialization.ExecutionEnvironment executionEnvironment(){requireCurrent.run();return originalBoot.environment();}
            public void clearScratch(){requireCurrent.run();originalBoot.mainClears();var ready=withProgramCall(ProgramStartCall.START_SCRATCH_CLEAR,programServices.loads(),programServices.initialization());requireCurrent.run();programServices=ready;}
            public void writeVersion(CdProgramInitialization.ExecutionRegion r){requireCurrent.run();var ready=withProgramCall(ProgramStartCall.WRITE_VERSION_CACHE,programServices.loads(),CdProgramInitialization.writeVersionCache(programServices.initialization(),r));requireCurrent.run();programServices=ready;}
            public void writeMarker(){requireCurrent.run();var ready=withProgramCall(ProgramStartCall.WRITE_INIT_MARKER,programServices.loads(),CdProgramInitialization.writeInitMarker(programServices.initialization()));requireCurrent.run();programServices=ready;}
            public void initializeVideo(){requireCurrent.run();var ready=withProgramCall(ProgramStartCall.INIT_VDP,programServices.loads(),programServices.initialization());initializeVideo.run();requireCurrent.run();programServices=ready;}
            public void initializeControllers(){requireCurrent.run();var ready=withProgramCall(ProgramStartCall.INIT_CONTROLLERS,programServices.loads(),CdProgramInitialization.initializeControllers(programServices.initialization()));requireCurrent.run();programServices=ready;}
            public void selectLevelMode(){requireCurrent.run();var ready=withProgramCall(ProgramStartCall.GAME_MODE_LEVEL,programServices.loads(),programServices.initialization());requireCurrent.run();programServices=ready;}
        });
    }
    private void enterOriginalR41Programme(CdSelectedProfileVisit.Publication original){
        var permit=originalBoot.issueR41(this,original);
        var world=SessionManager.getCurrentWorldSession();var mode=SessionManager.getCurrentGameplayMode();
        var manager=mode.getLevelManager();var level=manager==null?null:manager.getCurrentLevel();
        var generation=manager==null?java.util.OptionalLong.empty():java.util.OptionalLong.of(manager.getCompletedProductionLoadGeneration());
        programWorld=world;playerMode=mode;r11Permit=permit;r11SelectedFinal=original.after();
        originalLoadingProgramme=CdSelectedProfileVisit.Programme.R41A;originalLoadingPublication=original;
        originalLoadingSource=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
        r11Thread=Thread.currentThread();r11EntryLevelManager=manager;r11EntryLevel=level;r11EntryLoadGeneration=generation;r11StartActive=true;
        permit.beginR41Start(this,original);
        Runnable requireCurrent=()->{
            permit.requireR41Start(this,original);
            if(SessionManager.getCurrentWorldSession()!=world||SessionManager.getCurrentGameplayMode()!=mode
                    ||source!=originalBoot.source()||mode.getLevelManager()!=manager
                    ||(manager!=null&&(manager.getCurrentLevel()!=level||manager.getCompletedProductionLoadGeneration()!=generation.orElseThrow()))
                    ||playerFrame!=null||playerActor!=null||openingObjects!=null||levelProgram!=null)
                throw new IllegalStateException("Original R41 entry source/World/load generation changed");
        };
        try{
            requireCurrent.run();
            var pcm=source.r4PcmSource().orElseThrow(()->originalBoot.stopR41Pcm(permit,this,original));
            if(!pcm.source().wholeDiscSha256().equals(source.release().normalizedSha256()))
                throw new IllegalArgumentException("Original R4PCM source differs");
            if(SonicCdProgramLoadServices.r4PcmFile(CdNativeFile.R41A)!=pcm.file())
                throw new IllegalArgumentException("Original selected programme PCM selector differs");
            var loads=SonicCdProgramLoadServices.loadLevelR4Pcm(programServices.loads(),pcm);
            var ready=withProgramCall(ProgramStartCall.LOAD_LEVEL_PCM,loads,programServices.initialization());
            requireCurrent.run();programServices=ready;
            enterOriginalProgrammeStart(requireCurrent,()->{
                programStartVideo=playerPresentation.initializeProgramVideo(originalR11LoadingUnknownPatterns(),originalBoot.workClears().playerArt());
            });
            requireCurrent.run();
            var clear=originalBoot.workClears();
            r11PreparedFrame=CdPlayerFrameRuntime.fromProgrammeEntry(originalLoadingSource.retainedPlayerSource(),clear.primary(),
                    originalProgrammeEnvironment(r11SelectedFinal.globals().orElseThrow()),programStartVideo.playerArt(),
                    new CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.empty(),0));
            bindOriginalR11FinishTimers(r11PreparedFrame);
            enterOriginalLevelStart(original,requireCurrent);
            var publication=permit.prepareR41Start(r11PreparedFrame,original);requireCurrent.run();
            playerFrame=r11PreparedFrame;r11EntryFrame=r11PreparedFrame;
            permit.adoptR41Start(publication);r11StartComplete=true;
            installOriginalSourceLoading();
        }catch(SonicCdProgramLoadServices.Unsupported stopped){
            programServices=new ProgramServicesState(stopped.completedPrefix(),programServices.initialization(),programServices.environment(),programServices.completedCalls());
            permit.failR41Start();throw stopped;
        }catch(RuntimeException|Error failure){permit.failR41Start();r11Retired=true;
            try{if(r11PreparedFrame!=null)r11PreparedFrame.close();}catch(RuntimeException|Error close){if(close!=failure)failure.addSuppressed(close);}throw failure;}
        finally{r11PreparedFrame=null;r11StartActive=false;}
        if(r11EntryLevelManager!=null)assembleOriginalR11();
    }
    /** Called only by Root's Boot continuation prepareR41Start, at the actual private handoff. */
    void requireOriginalBootR41Frame(CdOriginalBootPrefix.Continuation permit,CdPlayerFrameRuntime actual,CdSelectedProfileVisit.Publication original){
        permit.requireR41Start(this,original);
        if(permit!=r11Permit||original!=originalLoadingPublication||!r11StartActive||r11StartComplete||r11Retired
                ||r11Thread!=Thread.currentThread()||SessionManager.getCurrentWorldSession()!=programWorld
                ||SessionManager.getCurrentGameplayMode()!=playerMode||source!=originalBoot.source()
                ||actual!=r11PreparedFrame||!ownsOriginalLoadingFrame(actual)||playerFrame!=null
                ||playerMode.getLevelManager()!=r11EntryLevelManager
                ||(r11EntryLevelManager!=null&&(r11EntryLevelManager.getCurrentLevel()!=r11EntryLevel||r11EntryLevelManager.getCompletedProductionLoadGeneration()!=r11EntryLoadGeneration.orElseThrow())))
            throw new IllegalStateException("No actual original programme loading birth");
        actual.captureActor();actual.captureShared();
    }

    /** Shared fresh programme birth projection; values are not admission or an existing-Frame setter. */
    private CdPlayerBody.Environment originalProgrammeEnvironment(CdSelectedProfileVisit.Globals selected){
        var before=originalBoot.workClears().environment();var movement=before.movement();var gate=movement.gates();
        return new CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(movement.physics(),movement.bounds(),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(selected.zoneAct().orElseThrow(),selected.period(),gate.miniSonic(),gate.waterSlide(),gate.windTunnel(),gate.speedShoes(),gate.timeWarpDirection()),movement.globals()),before.controls(),
                new CdPlayerBody.Globals(before.globals().levelFrames(),before.globals().cameraXCentre(),selected.timeAttack(),before.globals().debugMode(),before.globals().debugCheat(),before.globals().timeWarpTimer(),before.globals().invincible(),before.globals().bossMusic()),before.dynamicSlots());
    }
    void requireOriginalBootR11Frame(CdOriginalBootPrefix.Continuation permit,CdPlayerFrameRuntime actual,CdSelectedProfileVisit.Publication original){
        requireR11Start(original);if(permit!=r11Permit||actual==null||actual!=r11PreparedFrame||!actual.stageSource().ownsPresent(source.assets()))throw new IllegalStateException("Foreign prepared original R11 Frame");actual.captureActor();actual.captureShared();
    }
    /** One actual closed reached loading pair. A rejected observation/restore is a healthy nonwrite. */
    private void requireOriginalR11EntryCut(){
        if(r11ServiceActive||constructingOriginalR11||originalDestinationEntering&&!r11WorldConstructing)throw new IllegalStateException("Original R11 loading operation is active");
        playerPresentation.requirePastRenderIdle();
        var mode=SessionManager.getCurrentGameplayMode();var world=SessionManager.getCurrentWorldSession();
        if(!r11StartComplete||r11Retired||r11StartActive||r11Thread!=Thread.currentThread()
                ||playerFrame==null||playerFrame!=r11EntryFrame||playerFrame.closed()
                ||mode==null||mode!=playerMode||world==null||world!=programWorld||mode.getWorldSession()!=world
                ||world.rootGameModule()!=this||world.resolvedGameModule()!=this||world.getDataSource()!=source
                ||originalBoot==null||source!=originalBoot.source()||selectedProfile.state()!=r11SelectedFinal
                ||!ownsOriginalLoadingFrame(playerFrame)||playerActor!=null||openingObjects!=null
                ||mode.getLevelManager()!=r11EntryLevelManager
                ||(r11EntryLevelManager==null&&(r11EntryLevel!=null||r11EntryLoadGeneration.isPresent()))
                ||(r11EntryLevelManager!=null&&(r11EntryLevelManager.getCurrentLevel()!=r11EntryLevel
                    ||r11EntryLoadGeneration.isEmpty()||r11EntryLevelManager.getCompletedProductionLoadGeneration()!=r11EntryLoadGeneration.orElseThrow())))
            throw new IllegalStateException("No current original R11 source/World/context/thread/Frame/load cut");
        originalBoot.requireConsumed(this,world,selectedProfileInvocation);
        // Unlike restore's component owner checks, these also reject pending input/Body work.
        playerFrame.captureActor();playerFrame.captureShared();
        if(r11LoadingPrefix==null||r11Placement==null||r11LoadingArt==null)throw new IllegalStateException("No original R11 LevelStart prefix");
        r11LoadingArt.validateState(r11LoadingPrefix.section());if(originalPastStage())originalPastArrival.require();
    }
    CdPlayerFrameRuntime originalR11EntryFrame(){requireOriginalR11EntryCut();return playerFrame;}
    private void enterOriginalR31Programme(CdSelectedProfileVisit.Publication original){
        // This is the original applied selected-entry cut. Its scope is NOT retained for later frames.
        var permit=originalBoot.issueR31(this,original);
        r31Permit=permit;r31SelectedFinal=original.after();stagedR31=source.r31aAssets().orElseThrow(()->SonicCd.unsupported("Same-CUE original R31 data absent"));
        programWorld=SessionManager.getCurrentWorldSession();r31Thread=Thread.currentThread();r31Epoch=new Object();
        playerMode=SessionManager.getCurrentGameplayMode();r31PreviousGeneration=playerMode.getLevelManager().getCompletedProductionLoadGeneration();r31LoadGeneration=Math.incrementExact(r31PreviousGeneration);
        stagedR31Context=SonicCdLevelInitProfile.staticStageContext();stagedR31Context.setLevelIndex(r31Zones.loadingIndex());stagedR31Context.setZone(1);stagedR31Context.setAct(0);stagedR31Context.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        constructingR31=true;
        try{playerMode.getLevelManager().loadLevel(r31Zones.loadingIndex(),LevelLoadMode.FULL,stagedR31Context);
            if(playerMode.getLevelManager().getCurrentLevel()!=r31Level||playerMode.getLevelManager().getCompletedProductionLoadGeneration()!=r31LoadGeneration)throw new IllegalStateException("R31 FULL generation did not commit");
            selectedProfile.completeProgrammeHandoff(this,original);
        }catch(IOException failure){r31Retired=true;originalBoot.retire(programWorld);throw new UncheckedIOException("Original R31 FULL assembly failed",failure);}
        catch(RuntimeException|Error failure){r31Retired=true;originalBoot.retire(programWorld);closePlayerCycleAfterFailure(failure);throw failure;}
        finally{constructingR31=false;stagedR31=null;stagedR31Context=null;}
    }
    void requireOriginalBootR31Loading(CdOriginalBootPrefix.Continuation permit,SonicCdR31aLoadingVisit core,
            SonicCdR31aLoadingVisit.Host host,CdPlayerFrameRuntime frame,
            com.openggf.game.soniccd.resources.CdR31aSectionArt art){
        if(permit!=r31Permit)throw new IllegalArgumentException("Another original R31 continuation");
        requireR31Pair(core,host,frame,art);
    }
    private void validateOriginalBootServices(ProgramServicesState value){
        originalBoot.requireConsumed(this,SessionManager.getCurrentWorldSession(),selectedProfileInvocation);
        if(!value.environment().equals(originalBoot.environment()))throw new IllegalArgumentException("Another original execution environment");
        if(source.r4PcmSource().isPresent())
            SonicCdProgramLoadServices.validateWithR4ForCapture(value.loads(),source.timeWarpPrograms().orElseThrow(),source.r3PcmSource().orElseThrow(),source.r4PcmSource().orElseThrow());
        else SonicCdProgramLoadServices.validateWithR3ForCapture(value.loads(),source.timeWarpPrograms().orElseThrow(),source.r3PcmSource().orElseThrow());
    }
    // END original pre-storage fresh factory/private continuation bridge.
    boolean ownsSelectedProfileVisit(CdSelectedProfileVisit expected){return selectedProfile!=null&&selectedProfile==expected;}
    CdSelectedProfileVisit.State selectedProfileState(){if(selectedProfile==null)throw new IllegalStateException("No selected profile owner");return selectedProfile.state();}
    private void runSelectedProfile(CdGameDataSource candidate){
        var world=SessionManager.getCurrentWorldSession();
        if(world==null||world.rootGameModule()!=this||world.resolvedGameModule()!=this||world.getDataSource()!=candidate||candidate!=selectedProfile.source()
                ||SessionManager.getCurrentGameplayMode()==null||SessionManager.getCurrentGameplayMode().getWorldSession()!=world)
            throw new IllegalStateException("Actual selected fresh Module/source/World required");
        var context=world.getSaveSessionContext();
        if(context==null||!getGameCode().equals(context.gameCode())||context.activeSlot().isEmpty())throw new IllegalStateException("Actual final selected save context required");
        if(originalBoot!=null)originalBoot.bindWorld(this,world,selectedProfileInvocation);
        // World/context/source validation in the visit precedes any initial publication or I/O.
        selectedProfile.run(world,original->{
            selectedProfile.requirePublication(this,original);
            if(playerFrame!=null||playerActor!=null||openingObjects!=null||levelProgram!=null||stagedRestart!=null||stagedPast!=null||stagedAct2!=null||stagedAct2Past!=null||stagedAct3Future!=null)
                throw new IllegalStateException("Selected pre-level visit cannot borrow an old gameplay owner");
            var before=ipxProgress;var services=programServices;var lifetime=ipxProgressLifetime;var priorSource=this.source;
            var expected=IpxProgressFacts.from(original.before().progress());
            if(!before.equals(expected))throw new IllegalStateException("Selected complete progress preimage differs");
            var ready=IpxProgressFacts.from(original.after().progress()); // every fallible aggregate BEFORE the first store
            selectedProfile.requirePublication(this,original);
            if(ipxProgress!=before||programServices!=services||ipxProgressLifetime!=lifetime||this.source!=priorSource)
                throw new IllegalStateException("Selected Module complete preimage changed");
            original.applyNative(this);this.source=candidate;ipxProgress=ready;
            if(originalBoot!=null){
                if(original.after().calls().getLast()==CdSelectedProfileVisit.Call.TEMPORARY_PREFIX_704)completeOriginalBootInitialization(original);
                if(original.after().phase()==CdSelectedProfileVisit.Phase.PROGRAMME_ENTRY){
                    if(original.after().programme().orElseThrow()==CdSelectedProfileVisit.Programme.R31A)enterOriginalR31Programme(original);
                    else if(original.after().programme().orElseThrow()==CdSelectedProfileVisit.Programme.R11A)enterOriginalR11Programme(original);
                    else if(original.after().programme().orElseThrow()==CdSelectedProfileVisit.Programme.R41A){
                        enterOriginalR41Programme(original);
                    }
                }
            }
        },selectedProfileInvocation);
        if(originalBoot!=null){
            if(originalR11EngineLoadingAssembled()||originalSourceLoadingInstalled){requireOriginalR11EntryCut();return;}
            originalBoot.requireHandoffStarted(this,world,selectedProfileInvocation);return;
        }
        throw new AssertionError("Selected native programme entry must remain an explicit boundary");
    }
    private record SelectedShared(SonicCdGameModule owner,CdGameDataSource source,Object progressLifetime,
                                  CdSelectedProfileVisit.Snapshot visit,IpxProgressFacts facts,Optional<R11EntryImage> r11) { }
    /** A capture never manufactures saved knowledge from a public supplied State. The private
     * issued image binds the exact bank/shared/World references and current original lifetime. */
    private final class OriginalTimeWarpSavedImage {
        private final SonicCdGameModule owner=SonicCdGameModule.this;
        private final CdOriginalBootPrefix boot=originalBoot;
        private final CdGameDataSource retainedSource=source;
        private final WorldSession world=programWorld;
        private final Thread thread=Thread.currentThread();
        private final Object lifetime=r11TimeWarpSavedLifetime,levelLifetime=r11LevelLifetime;
        private final java.util.OptionalLong generation=r11EntryLoadGeneration;
        private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;
        private final Optional<CdWorld.R11Snapshot> worldImage;
        private final R11LoadingPrefix prefix=r11LoadingPrefix;
        private final R11LoadingState loading=r11LoadingState;
        private final R11FirstLoad firstLoad=r11FirstLoad;
        private final SonicCdR31aLoadingVisit.ReturnKind returnKind=r11ReturnKind;
        private final CdTimeWarpSaveData.State saved;
        private final Optional<com.openggf.game.soniccd.objects.r4.CdR4SwitchBank> switches=Optional.ofNullable(r11Switches);
        private OriginalTimeWarpSavedImage(CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,Optional<CdWorld.R11Snapshot> worldImage){
            this.actor=Objects.requireNonNull(actor);this.shared=Objects.requireNonNull(shared);this.worldImage=Objects.requireNonNull(worldImage);
            saved=Objects.requireNonNull(r11TimeWarpSaved,"No reached original warp-save product");
        }
        private void require(SonicCdGameModule actual,R11EntryImage image){
            if(actual!=owner||boot==null||actual.originalBoot!=boot||actual.source!=retainedSource
                    ||actual.programWorld!=world||thread!=Thread.currentThread()||actual.r11Thread!=thread
                    ||actual.r11TimeWarpSavedLifetime!=lifetime||actual.r11LevelLifetime!=levelLifetime
                    ||!generation.equals(actual.r11EntryLoadGeneration)||image.actor()!=actor||image.shared()!=shared||image.world()!=worldImage||image.switches()!=switches
                    ||image.prefix()!=prefix||image.loading()!=loading||image.firstLoad()!=firstLoad||image.returnKind()!=returnKind)
                throw new IllegalArgumentException("Foreign original warp-save capture/source/lifetime");
        }
    }
    private R11EntryImage captureOriginalR11EntryImage(){
        if(originalWarpProgrammeSelected())validateOriginalWarpProgrammeOwner();
        var actor=originalR11EntryFrame().captureActor();var shared=playerFrame.captureShared();
        var services=programServices;var video=programStartVideo;var prefix=r11LoadingPrefix;var placement=r11Placement.capture();
        var first=r11FirstLoad;var loading=r11LoadingState;Optional<CdWorld.R11Snapshot> worldImage=r11World==null?Optional.empty():Optional.of(r11World.captureR11());
        var saved=new OriginalTimeWarpSavedImage(actor,shared,worldImage);
        return new R11EntryImage(actor,shared,services,video,prefix,placement,first,loading,worldImage,r11WaterPalette,r11LevelLifetime,r11ReturnKind,originalLoadingWater,originalScrollFields,originalLoadingAnimation,r11ClosureSpecialStage,r11ClosureIpx,programSounds,saved.switches,saved,new OriginalWarpImage(actor,shared,worldImage));
    }
    private record R11EntryImage(CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,ProgramServicesState services,SonicCdPlayerPresentation.PresentVideoInit video,R11LoadingPrefix prefix,CdObjectPlacementRuntime.Snapshot placement,R11FirstLoad firstLoad,R11LoadingState loading,Optional<CdWorld.R11Snapshot> world,CdAssetBytes waterPalette,Object levelLifetime,SonicCdR31aLoadingVisit.ReturnKind returnKind,Optional<OriginalLoadingWater> water,java.util.Map<OriginalScrollField,Integer> scrollFields,Optional<OriginalLoadingAnimation> animation,int closureSpecialStage,OriginalR11IpxState closureIpx,List<ProgramSound> programmeSounds,Optional<com.openggf.game.soniccd.objects.r4.CdR4SwitchBank> switches,OriginalTimeWarpSavedImage warpSaved,OriginalWarpImage originalWarp){R11EntryImage{scrollFields=java.util.Map.copyOf(scrollFields);}}
    private void validateOriginalR11Restore(R11EntryImage r){
        var f=originalR11EntryFrame();Objects.requireNonNull(r);Objects.requireNonNull(r.warpSaved()).require(this,r);var state=r.loading();
        Objects.requireNonNull(r.originalWarp()).require(r);
        Objects.requireNonNull(r.closureIpx());Objects.requireNonNull(r.programmeSounds());if(r.closureIpx().phase()!=OriginalR11IpxPhase.NOT_STARTED&&state.phase()!=SonicCdR31aLoadingVisit.Phase.RETURNED)throw new IllegalArgumentException("IPX prefix before actual R11 RTS");
        if((r.closureSpecialStage()&~255)!=0)throw new IllegalArgumentException("Original closure specialStage BYTE");
        if(r.levelLifetime()!=r11LevelLifetime||r.waterPalette()==null||r.waterPalette().length()!=128||r.returnKind()==null)throw new IllegalArgumentException("Different original R11 LevelStart/water lifetime");
        if((state==null)!=(r11LoadingState==null)||(r.firstLoad()==null)!=(r11FirstLoad==null)
                ||r.world().isPresent()!=(r11World!=null))throw new IllegalArgumentException("Different original R11 loading/World generation");
        if(state==null&&(r.firstLoad()!=null||r.world().isPresent()))throw new IllegalArgumentException("Loading owners before original R11 assembly");
        if(r11World!=null)r11World.validateR11Cpu(r.world().orElseThrow(),Objects.requireNonNull(state).cpuSat());
        if(r11Clear!=null||originalLoadingAnimationCall!=null||r.video()!=programStartVideo||r.services().loads()!=programServices.loads()||!r.services().completedCalls().equals(programServices.completedCalls())||!r.services().environment().equals(programServices.environment()))throw new IllegalArgumentException("Different original R11 Start/PCM/source lifetime");
        validateOriginalBootServices(r.services());r11LoadingArt.validateState(r.prefix().section());
        r11Placement.validateRestore(r.placement());f.validateActorRestore(r.actor());f.validateSharedRestore(r.shared());
        // The genuine Start/LevelStart prefix is capturable before engine assembly.
        // No selected display or loading state exists at that earlier reached cut.
        // A prior level's genuinely cleared bank is retained until the next CAMERA clear.
        // The private capture seal binds that exact reference even in the earlier reload prefix.
        if((state!=null&&state.clears().contains(CdLevelStartStorage.Cut.CAMERA)&&r.switches().isEmpty())
            ||r.switches().isPresent()&&r.switches().orElseThrow().knownMask()!=-1)throw new IllegalArgumentException("Different actual original camera-clear/switch-bank capture");
        if(state==null)return;
        if(!originalWaterStage()&&!originalPastStage())state.flowers().requireSource(source.assets());
        validateOriginalWaterRestore(r);
        var gates=r.shared().environment().movement().gates();var phase=state.phase();
        // These additional phases are DATA from the exact privately issued actor/shared/
        // loading/World ticket above; equal phase/period DTOs cannot reopen a continuation.
        boolean marked=!originalWaterStage()&&r.world().isPresent()
            &&originalLoadingSource.retainedPlayerSource().nativeZoneActWord()==0&&gates.zoneAct()==0
            &&gates.timeZone()>=0x80&&gates.timeZone()<=0x82;
        boolean activeWarp=phase==SonicCdR31aLoadingVisit.Phase.READY&&r.world().isPresent()
            &&gates.timeZone()>=0x80&&gates.timeZone()<=0x82;
        var level=r.prefix().main().level();int fadeBit=r.prefix().main().palFadeFlagsByte()&1;
        boolean markedCommon=marked&&level.spawnMode()==2&&r.prefix().main().levelStartedByte()==0
            &&(r.prefix().gameMode()&128)!=0&&(r.closureIpx().phase()==OriginalR11IpxPhase.NOT_STARTED||r.originalWarp().phase!=OriginalWarpPhase.NONE);
        boolean markedWhite=markedCommon&&phase==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE
            &&r.returnKind()==SonicCdR31aLoadingVisit.ReturnKind.NONE&&level.levelRestart()!=0&&fadeBit==1
            &&state.fadeSteps()<22&&state.globals().tail().orElseThrow().paletteFadeInfoWord()==0x003F;
        boolean markedRts=markedCommon&&phase==SonicCdR31aLoadingVisit.Phase.RETURNED
            &&r.returnKind()==SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART&&level.levelRestart()==0
            &&gates.timeWarpDirection()==0&&((state.fadeSteps()==22&&fadeBit==0)||(state.fadeSteps()==0&&fadeBit==1));
        if(phase==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE&&!markedWhite)
            throw new IllegalArgumentException("Unissued original marked white-fade cut");
        if(phase!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC&&phase!=SonicCdR31aLoadingVisit.Phase.LEVEL_PLC&&phase!=SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK&&phase!=SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE&&phase!=SonicCdR31aLoadingVisit.Phase.FROM_WHITE&&phase!=SonicCdR31aLoadingVisit.Phase.FROM_BLACK&&phase!=SonicCdR31aLoadingVisit.Phase.READY&&phase!=SonicCdR31aLoadingVisit.Phase.CLEAR_RETURN_WAIT&&phase!=SonicCdR31aLoadingVisit.Phase.RETURNED)throw new IllegalArgumentException("Unadmitted original R11 capture phase");
        if((gates.zoneAct()!=originalLoadingSource.retainedPlayerSource().nativeZoneActWord()&&!(originalLoadingSource.retainedPlayerSource().nativeZoneActWord()==0&&gates.zoneAct()==1&&gates.timeZone()==1&&(r.prefix().main().level().levelRestart()==2||phase==SonicCdR31aLoadingVisit.Phase.INITIAL_TO_BLACK||phase==SonicCdR31aLoadingVisit.Phase.CLEAR_RETURN_WAIT||phase==SonicCdR31aLoadingVisit.Phase.RETURNED)))||((gates.timeZone()&127)!=originalLoadingSource.retainedPlayerSource().ordinaryPeriod()&&!activeWarp&&!markedWhite&&!markedRts)||r.prefix().main().levelStartedByte()!=(phase==SonicCdR31aLoadingVisit.Phase.READY?1:0)
            ||(phase==SonicCdR31aLoadingVisit.Phase.READY&&state.fadeSteps()!=22)||(phase==SonicCdR31aLoadingVisit.Phase.FROM_WHITE||phase==SonicCdR31aLoadingVisit.Phase.FROM_BLACK)&&state.fadeSteps()>=22
            ||phase!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC&&state.clears().size()!=CdLevelStartStorage.Cut.values().length-(originalWaterStage()?1:0)
            ||!r.prefix().ring().equals(state.globals().ring())||!r.prefix().score().equals(state.globals().score()))throw new IllegalArgumentException("Different complete original R11 loading cut: phase="+phase+", zoneAct="+gates.zoneAct()+", period="+gates.timeZone()+", started="+r.prefix().main().levelStartedByte()+", fade="+state.fadeSteps()+", clears="+state.clears().size()+"/"+CdLevelStartStorage.Cut.values().length+", ringPair="+r.prefix().ring().equals(state.globals().ring())+", scorePair="+r.prefix().score().equals(state.globals().score()));
        boolean returning=phase==SonicCdR31aLoadingVisit.Phase.CLEAR_RETURN_WAIT||phase==SonicCdR31aLoadingVisit.Phase.RETURNED;
        if(returning!=(r.returnKind()!=SonicCdR31aLoadingVisit.ReturnKind.NONE)||returning&&r.returnKind()!=SonicCdR31aLoadingVisit.ReturnKind.NO_LIVES&&r.returnKind()!=SonicCdR31aLoadingVisit.ReturnKind.NEXT_LEVEL&&!markedRts)throw new IllegalArgumentException("Different original R11 return cut");
        if(r.firstLoad()==null||r.firstLoad().levelLoaded()!=r.firstLoad().standardLoaded()||phase!=SonicCdR31aLoadingVisit.Phase.FIRST_PLC&&(!r.firstLoad().standardLoaded()||state.blocks().isEmpty()||state.layout().isEmpty()))throw new IllegalArgumentException("Incomplete source/art/geometry loading cut");
        if(state.layout().isPresent()&&!java.util.Arrays.equals(state.layout().orElseThrow().copy(),originalLoadingSource.retainedPlayerSource().terrain().layout().ram().copy()))throw new IllegalArgumentException("Captured actual programme layout differs");
        if(state.blocks().isPresent()&&!java.util.Arrays.equals(state.blocks().orElseThrow().copy(),originalLoadingSource.retainedPlayerSource().terrain().blocks().copy()))throw new IllegalArgumentException("Captured actual programme blocks differ");
    }
    private com.openggf.game.session.WorldSessionPolicyState openSelectedProfilePolicy(WorldSession world){
        if(selectedProfilePolicyOwner!=null)throw new IllegalStateException("Selected profile policy already belongs to its original World");
        var policy=new com.openggf.game.session.WorldSessionPolicyState(){
            private void requireInstalled(){if(selectedProfilePolicyWorld!=world||selectedProfilePolicyOwner!=this||com.openggf.game.session.WorldSessionPolicyAccess.getService(world,getClass())!=this)throw new IllegalStateException("Not this World's original selected policy");}
            private final NativeRewindRestoreGuard<Object> capture=new NativeRewindRestoreGuard<>(){
                @Override public String key(){return "soniccd-selected-profile-pre-level";}
                @Override public Object capture(){requireInstalled();if(r31Retired)throw new IllegalStateException("Selected R31 lifetime retired");if(r31LoadingSelected())return captureR31();return new SelectedShared(SonicCdGameModule.this,source,ipxProgressLifetime,selectedProfile.capture(world),ipxProgress,r11StartComplete?Optional.of(captureOriginalR11EntryImage()):Optional.empty());}
                @Override public void validateRestore(Object value){
                    requireInstalled();if(r31Retired)throw new IllegalStateException("Selected R31 lifetime retired");if(r31LoadingSelected()){if(!(value instanceof R31Shared r31))throw new IllegalArgumentException("Pre-level capture cannot restore a consumed R31 permit");validateR31Restore(r31);return;}
                    if(!(value instanceof SelectedShared image))throw new IllegalArgumentException("Foreign selected pre-level capture");
                    if(image.owner()!=SonicCdGameModule.this||image.source()!=source||image.progressLifetime()!=ipxProgressLifetime)throw new IllegalArgumentException("Foreign selected Module/source/progress capture");
                    selectedProfile.validateRestore(image.visit(),world);var facts=IpxProgressFacts.from(image.visit().state().progress());if(!facts.equals(image.facts()))throw new IllegalArgumentException("Selected complete progress/capture pair differs");
                    if(image.r11().isPresent()!=r11StartComplete)throw new IllegalArgumentException("Different original R11 entry generation");
                    if(r11StartComplete)validateOriginalR11Restore(image.r11().orElseThrow());
                }
                @Override public void restore(Object value){
                    validateRestore(value);if(value instanceof R31Shared r31){restoreR31(r31);return;}
                    var image=(SelectedShared)value;
                    requireInstalled();
                    if(image==null||image.owner()!=SonicCdGameModule.this||image.source()!=source||image.progressLifetime()!=ipxProgressLifetime)
                        throw new IllegalArgumentException("Foreign selected Module/source/progress capture");
                    selectedProfile.validateRestore(image.visit(),world);var ready=IpxProgressFacts.from(image.visit().state().progress());
                    if(!ready.equals(image.facts()))throw new IllegalArgumentException("Selected complete progress/capture pair differs");
                    if(image.r11().isPresent()){
                        var r=image.r11().orElseThrow();validateOriginalR11Restore(r);var f=playerFrame;
                        // Complete Frame/placement/art/phase/source/progress preflight ABOVE every mutation.
                        // These setters adopt captured data only; neither Boot/Start/I-O nor service replays.
                        f.restoreActor(r.actor());f.restoreShared(r.shared());r11Placement.restore(r.placement());
                        programServices=r.services();programStartVideo=r.video();r11LoadingPrefix=r.prefix();r11FirstLoad=r.firstLoad();r11LoadingState=r.loading();r11WaterPalette=r.waterPalette();r11ReturnKind=r.returnKind();originalLoadingWater=r.water();originalScrollFields=r.scrollFields();originalLoadingAnimation=r.animation();r11ClosureSpecialStage=r.closureSpecialStage();r11ClosureIpx=r.closureIpx();programSounds=r.programmeSounds();r11Switches=r.switches().orElse(null);r11TimeWarpSaved=r.warpSaved().saved;var warp=r.originalWarp();originalWarpPhase=warp.phase;originalWarpAuthority=warp.ticket;warpAssets=warp.assets;warpScene=warp.scene;warpState=warp.state;originalWarpDestination=warp.destination;if(r11World!=null)r11World.restore(r.world().orElseThrow());
                    }
                    selectedProfile.restore(image.visit(),world);ipxProgress=image.facts();
                }
            };
            @Override public <T>T getService(Class<T> type){return type==getClass()?type.cast(this):null;}
            @Override public RewindSnapshottable<?> rewindAdapter(){return capture;}
            @Override public void beforeAssembly(LevelLoadCause cause){ }
            @Override public void bindRoster(com.openggf.sprites.managers.SpriteManager sprites){ }
            @Override public void failedAssembly(LevelLoadCause cause){ }
            @Override public void closeScreens(){ }
            @Override public boolean holdsGameplay(){return !r31LoadingSelected()&&!r11EngineAssembled;} // actual loading owns HOLD; the common World owns ordinary service
            @Override public void retire(){requireInstalled();if(r11ServiceActive)throw new IllegalStateException("Original R11 policy retirement during native service");if(SessionManager.getCurrentWorldSession()==world)throw new IllegalStateException("Actual World retirement must precede selected profile retirement");selectedProfile.retire(world);if(originalBoot!=null)originalBoot.retire(world);if(r11StartComplete){r11Retired=true;if(r11World!=null)r11World.close();else if(playerFrame!=null)playerFrame.close();}}
        };
        selectedProfilePolicyWorld=world;selectedProfilePolicyOwner=policy;return policy;
    }
    // END actual selected-profile pre-level Module owner.

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
        if(r31RegistrySelected())return "Sonic CD (selected profile; original R31 loading)";
        if(selectedProfile!=null)return "Sonic CD (selected profile; pre-level native programme entry fenced)";
        return openingObjectsAdmission ? "Sonic CD (development USA R11A opening objects PLAYER)"
                : playerAdmission ? "Sonic CD (development terrain-only R11A PLAYER)" : "Sonic CD (development STATIC R11A)";
    }
    @Override public GameId getGameId() { return GameId.SCD; }

    @Override public Game createGame(GameDataSource dataSource) {
        CdGameDataSource candidate = SonicCd.requireSource(dataSource);
        if(constructingOriginalR11){if(candidate!=source)throw new IllegalArgumentException("Foreign original R11 source");validateOriginalR11Assembly(r11AssemblyContext);zones.bind(candidate,originalLoadingSource);return new SonicCd(candidate,originalLoadingSource);}
        if(selectedProfile!=null&&(r11EngineAssembled||originalSourceLoadingInstalled))throw new IllegalStateException("Original programme assembly already consumed");
        if(selectedProfile!=null&&r31Loading!=null&&stagedR31==null)throw new IllegalStateException("Actual selected programme handoff already completed");
        if(selectedProfile!=null&&stagedR31==null){runSelectedProfile(candidate);return originalLoadingSource==null?new SonicCd(candidate):new SonicCd(candidate,originalLoadingSource);}
        if(stagedR31!=null){if(candidate!=source)throw new IllegalArgumentException("Foreign selected source object");validateR31LoadRequest(stagedR31Context);return new SonicCd(candidate);}
        authenticateOpeningSource(candidate); // Actual retained media before replacing source/presentation state.
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD module is pinned to another authenticated source; open a new module/world");
        }
        if (playerAdmission && playerFrame != null && !playerFrame.closed()) {
            throw new IllegalStateException("Retire the admitted CD player before binding a fresh level");
        }
        if (stagedPast == null && stagedAct2 == null && stagedAct2Past == null && stagedAct3Future==null && coldProgramStartAdmission) prepareColdProgramStart(candidate);
        source = candidate;
        zones.bind(candidate);
        if(stagedAct3Future!=null)validateAct3FutureLoadRequest(stagedAct3FutureContext);
        else if (stagedAct2Past != null) validateAct2PastLoadRequest(stagedAct2PastContext);
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
            // Only this distinct fresh semantic entry establishes NewGame's source-written progress facts.
            // The pre-existing later BeginLevel programme already supplies native timeStones0; no old stores replay.
            var nextIpx=ipxProgress;var nextIpxLifetime=ipxProgressLifetime;
            if(openingObjectsAdmission&&carry==null){
                var fresh=CdIpxProgress.newGameStores(ipxProgress.project(nextProgram.timeStones()));
                if(fresh.progress().timeStonesByte().orElseThrow()!=nextProgram.timeStones())throw new IllegalStateException("Fresh NewGame timeStones projection differs");
                nextIpx=IpxProgressFacts.from(fresh.progress());nextIpxLifetime=new Object();
            }
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
            ipxProgress=nextIpx;ipxProgressLifetime=nextIpxLifetime; // exact fresh/carry adoption, never restore/load initialization
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
        if(r11StartComplete){
            if(originalR11EngineLoadingAssembled())return playerFrame;
            throw SonicCd.unsupported("Original selected loading Frame requires its actual committed engine assembly");
        }
        if(r31Loading!=null){validateR31LoadingCut();return playerFrame;}
        if(act3FutureLoading!=null){validateAct3FutureLoadingCut();return playerFrame;}
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
        if(r31Loading!=null){r31Retired=true;if(originalBoot!=null)originalBoot.retire(programWorld);try{if(r31World!=null)r31World.close();else if(r31Host!=null)r31Host.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
        try { if(act3FutureWorld!=null)act3FutureWorld.close(); }
        catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
        try { if (openingObjects != null) openingObjects.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        try { if (playerFrame != null) playerFrame.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    /** Also called by the player controller's close(): session teardown does not run the profile. */
    void closeTerrainPlayer() {
        if (!playerAdmission) return;
        playerPresentation.requirePastRenderIdle(); // Reject a caught recursive close before retiring any owner.
        if(r11ServiceActive)throw new IllegalStateException("Original R11 close cannot interrupt loading service");
        if(r11EngineAssembled){r11Retired=true;if(originalBoot!=null)originalBoot.retire(programWorld);}
        if(r31Loading!=null){if(r31Thread!=Thread.currentThread()||r31ServiceActive)throw new IllegalStateException("R31 teardown cannot reenter its actual thread/service");r31Loading.requireRetirementIdle(r31Host);}
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
                try {
                    try { if(r31World!=null)r31World.close();else if(r31Host!=null)r31Host.close(); if(act3FutureWorld!=null)act3FutureWorld.close();else if(act3FutureHost!=null)act3FutureHost.close(); }
                    finally { if (act2PastHost != null) act2PastHost.close(); }
                }
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
                    act3FutureLoading=null;act3FutureHost=null;act3FutureWorld=null;act3FutureLevel=null;act3FutureArt=null;
                    act3ProgrammeAssets=null;act3FutureLoadGeneration=-1;act3SelectedGoodFuture=-1;constructingAct3Future=false;
                    if(r31Loading!=null){r31Retired=true;if(originalBoot!=null)originalBoot.retire(programWorld);}
                    r31Loading=null;r31Host=null;r31World=null;r31Level=null;r31Art=null;
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
        if(!r31RegistrySelected())authenticateOpeningSource(candidate);
        return candidate;
    }

    @Override public Level transformDecodedLevel(Level decoded) {
        var candidate = requireRuntimeSource();
        if(constructingOriginalR11){validateOriginalR11Assembly(r11AssemblyContext);if(!(decoded instanceof SonicCdLevel actual)||actual.loadingSource()!=originalLoadingSource)throw new IllegalArgumentException("Foreign original programme geometry");originalLoadingSource.requirePlayerSource(playerFrame.stageSource());return actual;}
        if(stagedR31!=null){validateR31LoadRequest(stagedR31Context);if(!(decoded instanceof com.openggf.game.soniccd.level.SonicCdR31aLevel r31)||r31.assets()!=stagedR31)throw new IllegalArgumentException("Foreign prepared R31 geometry");return r31;}
        if(stagedAct3Future!=null) {
            validateAct3FutureLoadRequest(stagedAct3FutureContext);
            if(!(decoded instanceof com.openggf.game.soniccd.level.SonicCdAct3FutureLevel future)
                    ||future.assets()!=stagedAct3Future.assets())throw new IllegalArgumentException("Foreign prepared R13 terrain");
            return future;
        }
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
        if (originalR11AssemblySelected() || pastLoading != null || act2Loading != null || act2PastLoading != null || act3FutureLoading!=null || r31Loading!=null) return null;
        return playerAdmission ? new SonicCdDevelopmentPlayerState() : new SonicCdStaticState.Level();
    }
    @Override public ZoneRegistry getZoneRegistry() {
        if(r31RegistrySelected())return r31Zones;
        if(act3FutureRegistrySelected())return act3Zones;
        return stagedAct2 != null || act2Loading != null || stagedAct2Past != null || act2PastLoading != null ? act2Zones : zones;
    }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return playerAdmission ? playerPresentation : presentation; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return null; }
    @Override public PhysicsProvider getPhysicsProvider() { return rules; }
    @Override public GameAudioProfile getAudioProfile() { return audio; }
    @Override public LevelInitProfile getLevelInitProfile() {
        if(originalR11AssemblySelected()||r31RegistrySelected())return r31Profile;
        if(act3FutureRegistrySelected())return act3FutureProfile;
        return stagedAct2Past != null || act2PastLoading != null ? act2PastProfile : stagedAct2 != null || act2Loading != null ? act2Profile : stagedPast != null || pastLoading != null ? pastProfile : playerAdmission ? playerProfile : profile;
    }
    @Override public GameplayFrameController gameplayFrameController() { return playerAdmission ? playerPresentation : presentation; }
    @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() { return characters; }
    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        // Publish the exact installed World-policy adapter as a native owner so its
        // complete preflight runs before camera/timer/other registry restore writes.
        if(selectedProfile!=null)return selectedProfilePolicyOwner==null?List.of():List.of(selectedProfilePolicyOwner.rewindAdapter());
        return openingObjectsAdmission ? List.of(sharedOpeningAdapter)
                : playerAdmission ? List.of(sharedPlayerAdapter) : List.of();
    }
    @Override public <T> T getGameService(Class<T> type) {
        if(selectedProfile!=null&&type==com.openggf.game.session.WorldSessionPolicyProvider.class)
            return type.cast((com.openggf.game.session.WorldSessionPolicyProvider)this::openSelectedProfilePolicy);
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
    @Override public SaveSnapshotProvider getSaveSnapshotProvider() { if(selectedProfile!=null)return selectedProfile.genericProvider();throw SonicCd.unsupported("campaign saves/BRAM"); }
}
