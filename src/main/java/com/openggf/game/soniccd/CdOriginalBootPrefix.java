package com.openggf.game.soniccd;

import com.openggf.game.GameId;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveProfileAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.soniccd.audio.CdFmPlaySound;
import com.openggf.game.soniccd.audio.CdFmSoundAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdR31aSectionArt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Original-USA pre-storage engine boot owner, not native Title/firmware/driver execution.
 * N1ecc5f3a: SPX299-304; IPX28-48,1307-1414; MDINIT24-62,152-186;
 * BRAM Initialization51-107,215-230; BRAM Sub31-64,WriteTempSaveData524-530.
 * Every private action below adopts finite engine outputs at its actual source cut.
 * No CPU/bus/VDP model, address port, source-data replay, clock seed or live disc exists here.
 */
final class CdOriginalBootPrefix {
    enum Call {
        SYSTEM_VARIABLES_CLEAR, IPX_HANDLER, MAIN_VARIABLES_CLEAR, WORK_FILE_CLEAR,
        MDINIT_SOURCE, MD_VIDEO_CONFIGURATION, CONTROLLER_SETUP, PATTERN_CLEAR,
        VSCROLL_CLEAR, IPX_VIDEO_CACHE, HSCROLL_CLEAR, CRAM_CLEAR, FM_BANK_INSTALL,
        TITLE_FLAGS_90, FM_RETURN_STOP, IPX_RETURN_BLANK, BURAM_SOURCE,
        BURAM_WORDRAM_CLEAR, BURAM_HANDLER, BRAM_VARIABLES_CLEAR,
        BRAM_SELECTOR_CLEAR, BACKEND_INITIALIZED, BRAM_PARAMETERS, STORAGE_SEARCH,
        STORAGE_RETURNED, INITIALIZATION_RETURN, R31_CONTINUATION_ISSUED
    }
    enum Phase { PREPARING, STORAGE, PREPARED, WORLD_BOUND, INITIALIZATION_RETURNED, R31_PENDING, CONSUMED }
    enum Boundary { R31_WORLD_OWNER }
    enum InterruptOwner { UNKNOWN, IPX, BURAM }
    /** Actual already-adopted engine prefix only; observing it cannot resume the failed attempt. */
    static final class Failed extends IOException {
        private final State completedPrefix;
        private Failed(State prefix,IOException original){super("Original boot prefix I/O did not return",original);completedPrefix=prefix;}
        State completedPrefix(){return completedPrefix;}
    }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;
        private Unowned(Boundary boundary){super("ORIGINAL_BOOT_"+boundary);this.boundary=boundary;}
        Boundary boundary(){return boundary;}
    }
    record MainImage(CdLevelGlobals level,int sectionWord,int demoModeWord,int usePlayer2Byte,int palFadeFlagsByte,int levelStartedByte) { }
    record Video(Optional<CdAssetBytes> displayedPalette,Optional<CdAssetBytes> plane,Optional<CdAssetBytes> sat,
                 OptionalLong hscroll,OptionalLong vscroll,Optional<Boolean> displayEnabled,boolean patternsCleared) { }
    record CpuPalettes(CdAssetBytes normal,CdAssetBytes fade) { }
    record WordRamKnowledge(List<OptionalInt> mainTail,List<OptionalInt> temporaryTail,OptionalInt subPolicy) {
        WordRamKnowledge { mainTail=List.copyOf(mainTail);temporaryTail=List.copyOf(temporaryTail);Objects.requireNonNull(subPolicy); }
    }
    record FmImage(CdFmSoundAssets assets,CdFmPlaySound.NativeView view,CdFmPlaySound.State resident) { }
    record State(Phase phase,CdProgramInitialization.State initialization,SonicCdProgramLoadServices.State loads,
                 CdIpxProgress.State progress,Optional<MainImage> main,Video video,Optional<CpuPalettes> cpuPalettes,
                 WordRamKnowledge wordRam,Optional<FmImage> fm,List<Call> calls) {
        State { calls=List.copyOf(calls); }
    }
    static final class Preparation {
        private final CdOriginalBootPrefix boot;private final CdSelectedProfileVisit.Prepared selected;
        private Preparation(CdOriginalBootPrefix boot,CdSelectedProfileVisit.Prepared selected){this.boot=boot;this.selected=selected;}
        CdOriginalBootPrefix boot(){return boot;}CdSelectedProfileVisit.Prepared selected(){return selected;}
    }

    // BEGIN named original clear products for the subsequent genuine R31 source handoff.
    // These immutable values are observations of fixed writers, never invocation/capture authority.
    record MainClears(CdAssetBytes savedFlags,CdAssetBytes cloudRam,int levelFrames,int miniSonic,
            int speedShoes,int debugMode,int debugCheat,int invincible,int bossMusic,int debugAngle,
            int debugAngleShift,int debugQuadrant,int debugFloorDistance,int goodFutureFlags,
            int noBumperByte,CdAssetBytes shieldCpu) {
        MainClears{if((noBumperByte&~255)!=0||Objects.requireNonNull(shieldCpu).length()!=0x480)throw new IllegalArgumentException("Named MAINVARS scratch extents");}
    }
    record WorkClears(com.openggf.game.soniccd.player.CdPlayerBody.State primary,
            com.openggf.game.soniccd.player.CdPlayerBody.Environment environment,
            com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State playerArt,
            SonicCdR31aWorldHost.Globals globals,SonicCdR31aLoadingVisit.Scroll scroll,
            CdAssetBytes reserved,CdAssetBytes waterPalette,CdAssetBytes cpuSat,CdAssetBytes nemesisTable,
            SonicCdR31aWorldHost.NativeCarry nativeCarry,PlcClears plc,int gameModeByte,int demoDataIndexWord) { }
    record PlcClears(com.openggf.game.soniccd.resources.CdR11aPlcBuffer.State queue,
            com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder saved,int remaining,int budget,int hint,int pad) { }
    private MainClears mainClears;
    private WorkClears workClears;
    record PhysicalClears(CdAssetBytes playerResident,CdAssetBytes hscroll) { }
    private PhysicalClears mdClears;
    PhysicalClears physicalClears(){return Objects.requireNonNull(mdClears,"Original MD physical clear not reached");}
    MainClears mainClears(){return Objects.requireNonNull(mainClears,"Original MAINVARS clear not reached");}
    WorkClears workClears(){return Objects.requireNonNull(workClears,"Original broad BRAM clear not reached");}
    private WorkClears clearedWork(){
        var c=mainClears();
        // BRAM Initialization64-69 [FFA000,FFFC00), together with the retained MAINVARS outputs.
        // Definitions125-167: actual primary/history/artCPU/reserved/pool. No actor Init executes here.
        var actor=new com.openggf.game.soniccd.player.CdPlayerBody.State(new com.openggf.game.soniccd.player.CdPlayerMotionState(
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerBody.AnimationCursor(0,0,0),new com.openggf.game.soniccd.player.CdPlayerBody.Header(0,0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerBody.Timers(0,0,0,0),new com.openggf.game.soniccd.player.CdPlayerBody.History(zero(256),0),0,new com.openggf.game.soniccd.player.CdPlayerBody.ObjectLinks(0,0),0);
        var environment=new com.openggf.game.soniccd.player.CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
                new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0,0,0),new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(0,0,c.miniSonic(),0,0,c.speedShoes(),0),
                new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(0,0,0,0,0,0,c.debugAngle(),c.debugAngleShift(),c.debugQuadrant(),c.debugFloorDistance())),
                new com.openggf.game.soniccd.player.CdPlayerBody.Controls(0,0,0,0,0),new com.openggf.game.soniccd.player.CdPlayerBody.Globals(c.levelFrames(),0,0,c.debugMode(),c.debugCheat(),0,c.invincible(),c.bossMusic()),com.openggf.game.soniccd.player.CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var live=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.writeTimeLong(com.openggf.game.soniccd.objects.CdCheckpointLiveAux.unknown(),0);
        live=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.writeWaterHeight2(live,0);live=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.writeWaterRoutine(live,0);live=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.writeWaterFullscreen(live,0);
        byte[] mask=new byte[31];java.util.Arrays.fill(mask,(byte)1);
        var saved=com.openggf.game.soniccd.objects.CdCheckpointSaveData.State.supplied(zero(mask.length),CdAssetBytes.copyOf(mask));
        var globals=new SonicCdR31aWorldHost.Globals(new com.openggf.game.soniccd.objects.CdRingGlobals(0,0,0,0,0,0,0,0),live,
                new com.openggf.game.soniccd.player.CdPlayerBody.ContactGlobals(0,0,0),new com.openggf.game.soniccd.objects.CdOpeningObjectRuntime.ScoreGlobals(0,0,0),OptionalInt.of(0),saved,OptionalInt.of(0),OptionalInt.of(0),OptionalInt.of(0),
                Optional.of(new com.openggf.game.soniccd.resources.CdLevelHud.BonusValues(0,0,0)),Optional.of(new SonicCdR31aWorldHost.Tail(0,0,0,0,0)),
                Optional.of(new com.openggf.game.soniccd.objects.CdGlobalObjectAnimation.Extras(0,0,0,0,new com.openggf.game.soniccd.objects.CdRingSpillGlobals(0,0,0))),zero(7),zero(7),OptionalInt.of(0),OptionalInt.of(0),OptionalInt.of(0));
        var fg=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0);
        var bg=new com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State(0,0,0,0,0,0,zero(8),0,0,0,c.cloudRam(),zero(512),zero(1024),0);
        var camera=new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera(0,0);
        var copies=new SonicCdR31aLoadingVisit.Copies(0,0,new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.CopiedCameras(camera,camera,camera),0,new com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.FlagWords(0,0,0));
        return new WorkClears(actor,environment,new com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State(zero(768),physicalClears().playerResident(),0,false),globals,new SonicCdR31aLoadingVisit.Scroll(fg,bg,copies,0,0,0),zero(1984),zero(128),zero(640),zero(512),new SonicCdR31aWorldHost.NativeCarry(OptionalLong.of(0),OptionalInt.empty(),OptionalLong.of(0),OptionalInt.of(0)),
                new PlcClears(com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0),0,0);
    }
    // END named original clear products.

    private final CdGameDataSource source;
    private final SaveManager manager;
    private final int slot;
    private final SonicCdGameModule.ProfileEntry entry;
    private final CdProgramInitialization.ExecutionEnvironment environment;
    private final Object attemptIdentity=new Object();
    private State state;
    private CdSelectedProfileVisit.Prepared selectedPrepared;
    private CdSaveProfileBootstrap pendingBootstrap;
    private SaveProfileAccess.BackendInitialization backendInitialization;
    private SonicCdGameModule module;
    private CdSelectedProfileVisit visit;
    private Object invocation;
    private WorldSession world;
    private Object scope;
    private Thread thread;
    private boolean failed,consumed,retired;
    private Continuation continuation;
    private InterruptOwner interruptOwner=InterruptOwner.UNKNOWN;

    private CdOriginalBootPrefix(CdGameDataSource source,SaveManager manager,int slot,
                                 SonicCdGameModule.ProfileEntry entry,CdProgramInitialization.ExecutionEnvironment environment){
        this.source=Objects.requireNonNull(source);this.manager=Objects.requireNonNull(manager);
        this.entry=Objects.requireNonNull(entry);this.environment=Objects.requireNonNull(environment);
        if(slot<=0)throw new IllegalArgumentException("Actual positive selected slot required");this.slot=slot;
        state=new State(Phase.PREPARING,CdProgramInitialization.unknown(),SonicCdProgramLoadServices.unknown(),CdIpxProgress.unknown(),
                Optional.empty(),new Video(Optional.empty(),Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),Optional.empty(),false),
                Optional.empty(),new WordRamKnowledge(unknownTail(),unknownTail(),OptionalInt.empty()),Optional.empty(),List.of());
    }
    static Preparation prepare(CdGameDataSource source,SaveManager manager,int slot,SonicCdGameModule.ProfileEntry entry,
                               CdProgramInitialization.ExecutionEnvironment environment)throws IOException{
        var boot=new CdOriginalBootPrefix(source,manager,slot,entry,environment);
        boot.scope=new Object();boot.thread=Thread.currentThread();
        try{
            boot.requireSources();boot.runToSearch();
            var selected=CdSelectedProfileVisit.prepare(source,manager,slot,entry,boot);
            if(selected.healthy()){
                // A VALID read overwrites the shared main buffer, including UNKNOWN/dirty tail knowledge.
                // Earlier clear0 is usable only by the scoped MISSING initialization, never after this read.
                var word=new WordRamKnowledge(selected.result().receipt().orElseThrow().profile().tail(),boot.state.wordRam().temporaryTail(),boot.state.wordRam().subPolicy());
                boot.replace(boot.state.initialization(),boot.state.loads(),boot.state.progress(),boot.state.main(),boot.state.video(),boot.state.cpuPalettes(),word,boot.state.fm());
            }
            boot.selectedPrepared=selected;boot.phase(Phase.PREPARED);boot.call(Call.STORAGE_RETURNED);
            if(!selected.healthy())boot.failed=true;
            return new Preparation(boot,selected);
        }catch(IOException failure){boot.failed=true;throw new Failed(boot.state,failure);}
        catch(RuntimeException|Error failure){boot.failed=true;throw failure;}
        finally{boot.pendingBootstrap=null;boot.scope=null;boot.thread=null;}
    }
    State state(){return state;}
    CdGameDataSource source(){return source;}
    CdProgramInitialization.ExecutionEnvironment environment(){return environment;}

    private void requireSources(){
        var system=source.systemProgramSource().orElseThrow(()->new IllegalArgumentException("Same-CUE SPX source is absent"));
        var fm=source.fmSoundAssets().orElseThrow(()->new IllegalArgumentException("Same-CUE MDINIT source is absent"));
        var initial=source.initialSaveAssets().orElseThrow(()->new IllegalArgumentException("Same-CUE BRAM source pair is absent"));
        String disc=source.assets().identity().observedWholeDiscSha256();
        if(source.release().variant()!=CdReleaseVariant.USA||!disc.equals(source.release().normalizedSha256())
                ||!disc.equals(system.source().wholeDiscSha256())||!disc.equals(fm.source().wholeDiscSha256())
                ||!disc.equals(initial.identity().observedWholeDiscSha256())
                ||!fm.source().mdinitSha256().equals("abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e")
                ||!initial.identity().initializationSha256().equals("5895de24b19d0a362f9184ff049d4e5e857f32b0e591184027b0802342765fe6"))
            throw new IllegalArgumentException("Original SPX/IPX/MDINIT/BRAM source relation differs");
        // Holders have private authenticated constructors. No equal Source/Identity record is a holder.
    }
    private void requirePreparing(){if(failed||retired||scope==null||thread!=Thread.currentThread())throw new IllegalStateException("Expired original pre-storage scope");}
    private void runToSearch()throws IOException{
        requirePreparing();
        var loads=SonicCdProgramLoadServices.initializeOriginalSystem(state.loads(),source.systemProgramSource().orElseThrow());
        replace(state.initialization(),loads,state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),state.fm());call(Call.SYSTEM_VARIABLES_CLEAR);
        interruptOwner=InterruptOwner.IPX;call(Call.IPX_HANDLER);clearMain(); // IPX32-37, before its first original MD command.
        call(Call.WORK_FILE_CLEAR);call(Call.MDINIT_SOURCE);clearMain(); // MD25-30 is a second action.
        video(new Video(state.video().displayedPalette(),state.video().plane(),state.video().sat(),state.video().hscroll(),state.video().vscroll(),Optional.of(false),false));call(Call.MD_VIDEO_CONFIGURATION);
        replace(CdProgramInitialization.initializeControllers(state.initialization()),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),state.fm());call(Call.CONTROLLER_SETUP);
        // MD170-179 clears all physical patterns. These are the finite selected engine outputs,
        // not a virtual VRAM bank or stage-art default. Source-selected residency is projected later.
        var physicalReady=new PhysicalClears(zero(768),zero(1024)); // MD170-179 covers resident F000..F300 and physical HScroll FC00..10000.
        video(new Video(state.video().displayedPalette(),Optional.of(zero(4096)),Optional.of(zero(640)),state.video().hscroll(),state.video().vscroll(),state.video().displayEnabled(),true));mdClears=physicalReady;call(Call.PATTERN_CLEAR);
        video(new Video(state.video().displayedPalette(),state.video().plane(),state.video().sat(),state.video().hscroll(),OptionalLong.of(0),state.video().displayEnabled(),true));call(Call.VSCROLL_CLEAR);
        call(Call.IPX_VIDEO_CACHE);
        video(new Video(state.video().displayedPalette(),state.video().plane(),state.video().sat(),OptionalLong.of(0),state.video().vscroll(),state.video().displayEnabled(),true));call(Call.HSCROLL_CLEAR);
        video(new Video(Optional.of(zero(128)),state.video().plane(),state.video().sat(),state.video().hscroll(),state.video().vscroll(),state.video().displayEnabled(),true));call(Call.CRAM_CLEAR);
        var assets=source.fmSoundAssets().orElseThrow();var view=CdFmPlaySound.NativeView.fromAssets(assets);
        var fm=new FmImage(assets,view,CdFmPlaySound.State.supplied(view,List.of()));
        replace(state.initialization(),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),Optional.of(fm));call(Call.FM_BANK_INSTALL);
        var p=state.progress();progress(new CdIpxProgress.State(p.savedLevel(),p.goodFuturesByte(),OptionalInt.of(0x90),p.timeAttackUnlockByte(),p.unknownBackupByte(),p.currentSpecialStageByte(),p.timeStonesByte(),p.saveDisabledByte()));call(Call.TITLE_FLAGS_90);
        returnStopAndBlank();
        call(Call.WORK_FILE_CLEAR);call(Call.BURAM_SOURCE);
        // Sub54-60: [80000,C0000), including buramData80300..80302/policy80028.
        // SaveDataTemp[7400,76C2) is outside this and all other proven startup clears.
        var word=new WordRamKnowledge(List.of(OptionalInt.of(0),OptionalInt.of(0)),unknownTail(),OptionalInt.of(0));
        replace(state.initialization(),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),word,state.fm());call(Call.BURAM_WORDRAM_CLEAR);
        interruptOwner=InterruptOwner.BURAM;call(Call.BURAM_HANDLER);
        // BRAM64-69, original18-byte operands at file012E: count16FF =>5888 LONGs,
        // [FFA000,FFFC00). Both CPU palettes are established HERE, never by RunMMD.
        var palettes=new CpuPalettes(zero(128),zero(128));
        var workReady=clearedWork();
        replace(state.initialization(),state.loads(),state.progress(),state.main(),state.video(),Optional.of(palettes),state.wordRam(),state.fm());workClears=workReady;call(Call.BRAM_VARIABLES_CLEAR);
        call(Call.BRAM_SELECTOR_CLEAR);
        // Actual configured backing-store work replaces hardware-facing InitBuRAM73.
        // No slot/payload/probe/fallback, and no firmware or future-writeability claim.
        var initialized=SaveProfileAccess.initializeBackend(manager,GameId.SCD);
        if(!initialized.isFor(manager,GameId.SCD))throw new IllegalStateException("Another backend initialization result");
        backendInitialization=initialized;call(Call.BACKEND_INITIALIZED);call(Call.BRAM_PARAMETERS);phase(Phase.STORAGE);
    }
    private void clearMain(){
        var mainReady=new MainClears(zero(766),zero(512),0,0,0,0,0,0,0,0,0,0,0,0,0,zero(0x480)); // MAINVARS21-192, before selected reads.
        var z=OptionalInt.of(0);var p=new CdIpxProgress.State(new CdIpxProgress.SavedLevel(z,z),z,z,z,z,z,z,z);
        var main=new MainImage(new CdLevelGlobals(0,0,0,0,0,0,0,0,0),0,0,0,0,0);
        replace(CdProgramInitialization.clearMainVariables(state.initialization()),state.loads(),p,Optional.of(main),state.video(),state.cpuPalettes(),state.wordRam(),state.fm());mainClears=mainReady;call(Call.MAIN_VARIABLES_CLEAR);
    }
    private void returnStopAndBlank(){
        // IPX1375-77: the actual Main CPU queue2 store, not StopSound/driver execution.
        // No represented variable other than1C0A has been written by this owner.
        var fm=state.fm().orElseThrow();var next=new FmImage(fm.assets(),fm.view(),CdFmPlaySound.State.supplied(fm.view(),List.of(new CdFmPlaySound.ByteStore(0x1C0A,0xE0))));
        replace(state.initialization(),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),Optional.of(next));call(Call.FM_RETURN_STOP);
        interruptOwner=InterruptOwner.IPX;
        var v=state.video();video(new Video(v.displayedPalette(),v.plane(),v.sat(),v.hscroll(),v.vscroll(),Optional.of(false),v.patternsCleared()));call(Call.IPX_RETURN_BLANK);
        // IPX1404-14 owns this compulsory blank service, not Main.VInterrupt's level counter.
    }
    CdSaveProfileBootstrap.Result search(CdSaveProfileBootstrap service,CdGameDataSource actual,SaveManager actualManager,int actualSlot,SonicCdGameModule.ProfileEntry actualEntry){
        requirePreparing();if(state.phase()!=Phase.STORAGE||pendingBootstrap!=null||actual!=source||actualManager!=manager||actualSlot!=slot||actualEntry!=entry
                ||backendInitialization==null||!backendInitialization.isFor(manager,GameId.SCD))throw new IllegalStateException("Not this original initialized Search");
        pendingBootstrap=Objects.requireNonNull(service);call(Call.STORAGE_SEARCH);
        try{return service.openSelectedSlotFromOriginalBoot(slot,this);}finally{pendingBootstrap=null;}
    }
    void requireStorageSearch(CdSaveProfileBootstrap service,int actualSlot){requirePreparing();if(state.phase()!=Phase.STORAGE||service!=pendingBootstrap||actualSlot!=slot)throw new IllegalStateException("Not the original pending Search");}
    CdSaveProfile initialProfile(CdSaveProfileBootstrap service){
        requireStorageSearch(service,slot);
        if(!state.wordRam().mainTail().equals(List.of(OptionalInt.of(0),OptionalInt.of(0))))throw new IllegalStateException("Original Word RAM tail clear is absent");
        return CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withTail(state.wordRam().mainTail());
    }
    void bindVisit(SonicCdGameModule owner,CdSelectedProfileVisit actual,Object originalInvocation,CdSelectedProfileVisit.Prepared prepared){
        if(failed||retired||module!=null||state.phase()!=Phase.PREPARED||selectedPrepared!=prepared||!prepared.healthy())throw new IllegalStateException("Original fresh preparation cannot be rebound");
        var m=Objects.requireNonNull(owner);var v=Objects.requireNonNull(actual);var i=Objects.requireNonNull(originalInvocation);
        module=m;visit=v;invocation=i;
    }
    void bindWorld(SonicCdGameModule owner,WorldSession actual,Object originalInvocation){
        if(owner!=module||originalInvocation!=invocation||failed||retired||(interruptOwner!=InterruptOwner.BURAM&&interruptOwner!=InterruptOwner.IPX))throw new IllegalStateException("Another original boot invocation");
        requireWorld(actual);if(world!=null&&world!=actual)throw new IllegalStateException("Original boot cannot change Worlds");
        var before=state;var ready=before.phase()==Phase.PREPARED?new State(Phase.WORLD_BOUND,before.initialization(),before.loads(),before.progress(),before.main(),before.video(),before.cpuPalettes(),before.wordRam(),before.fm(),before.calls()):before;
        world=actual;state=ready;
    }
    private void requireWorld(WorldSession actual){
        if(actual==null||actual!=SessionManager.getCurrentWorldSession()||SessionManager.getCurrentGameplayMode()==null
                ||SessionManager.getCurrentGameplayMode().getWorldSession()!=actual||actual.rootGameModule()!=module||actual.resolvedGameModule()!=module
                ||actual.getDataSource()!=source||!module.ownsSelectedProfileVisit(visit))throw new IllegalStateException("Actual original boot Module/source/World required");
        var save=actual.getSaveSessionContext();if(save==null||!GameId.SCD.code().equals(save.gameCode())||save.activeSlot().isEmpty()||save.activeSlot().getAsInt()!=slot)throw new IllegalStateException("Actual final selected slot differs");
    }
    void completeInitializationReturn(SonicCdGameModule owner,CdSelectedProfileVisit.Publication original){
        requireWorld(world);if(owner!=module||failed||retired||state.phase()!=Phase.WORLD_BOUND)throw new IllegalStateException("Original initialization already returned or retired");
        visit.requireInitializationReturn(owner,original);call(Call.INITIALIZATION_RETURN);returnStopAndBlank();phase(Phase.INITIALIZATION_RETURNED);
    }
    Continuation issueR31(SonicCdGameModule owner,CdSelectedProfileVisit.Publication original){
        requireWorld(world);if(owner!=module||failed||retired||continuation!=null||state.phase()!=Phase.INITIALIZATION_RETURNED)throw new IllegalStateException("Original R31 continuation is unavailable");
        visit.requireProgrammeHandoff(owner,original);
        continuation=new Continuation();call(Call.R31_CONTINUATION_ISSUED);phase(Phase.R31_PENDING);return continuation;
    }
    Unowned unownedR31(Continuation original){if(original!=continuation)throw new IllegalArgumentException("Another continuation");failed=true;original.retired=true;return new Unowned(Boundary.R31_WORLD_OWNER);}

    /** Private two-stage authority. An old Publication is deliberately NOT retained as authority.
     * Future loading waits may retire the selected callback normally. A genuine Core callback,
     * bound full pair and this original generation are required at final consumption.
     */
    final class Continuation {
        private final Object attempt=attemptIdentity;
        private final SonicCdGameModule owner=module;
        private final WorldSession issuedWorld=world;
        private final CdGameDataSource issuedSource=source;
        private SonicCdR31aLoadingVisit core;
        private SonicCdR31aLoadingVisit.Host host;
        private CdPlayerFrameRuntime frame;
        private CdR31aSectionArt art;
        private Thread loadingThread;
        private boolean consumed,retired;
        private Continuation(){ }
        private void require(){
            if(this!=continuation||attempt!=attemptIdentity||owner!=module||issuedWorld!=world||issuedSource!=source
                    ||failed||CdOriginalBootPrefix.this.retired||retired||consumed||CdOriginalBootPrefix.this.consumed)throw new IllegalStateException("Expired original R31 continuation");
            requireWorld(issuedWorld);
        }
        void bindLoading(SonicCdR31aLoadingVisit actual,SonicCdR31aLoadingVisit.Host actualHost,CdPlayerFrameRuntime actualFrame,CdR31aSectionArt actualArt){
            require();
            try{
                if(core!=null)throw new IllegalStateException("Original continuation already has a loading generation");
                var c=Objects.requireNonNull(actual);var h=Objects.requireNonNull(actualHost);var f=Objects.requireNonNull(actualFrame);var a=Objects.requireNonNull(actualArt);var t=Thread.currentThread();
                owner.requireOriginalBootR31Loading(this,c,h,f,a);
                core=c;host=h;frame=f;art=a;loadingThread=t;
            }catch(RuntimeException|Error failure){retired=true;CdOriginalBootPrefix.this.failed=true;throw failure;}
        }
        void consume(SonicCdR31aLoadingVisit actual,SonicCdR31aLoadingVisit.Host actualHost,SonicCdR31aLoadingVisit.Publication original){
            require();
            try{
                if(core==null||actual!=core||actualHost!=host||loadingThread!=Thread.currentThread())throw new IllegalStateException("No original bound R31 load generation");
                var before=state;
                var ready=new State(Phase.CONSUMED,before.initialization(),before.loads(),before.progress(),before.main(),before.video(),before.cpuPalettes(),before.wordRam(),before.fm(),before.calls());
                core.requirePublication(host,original);owner.requireOriginalBootR31Loading(this,core,host,frame,art);
                if(state!=before)throw new IllegalStateException("Original boot preimage changed during load validation");
                consumed=true;CdOriginalBootPrefix.this.consumed=true;state=ready;
            }catch(RuntimeException|Error failure){retired=true;CdOriginalBootPrefix.this.failed=true;throw failure;}
        }
    }
    void requireConsumed(SonicCdGameModule owner,WorldSession actual,Object originalInvocation){requireWorld(actual);if(owner!=module||originalInvocation!=invocation||!consumed||continuation==null||!continuation.consumed||failed||retired)throw new IllegalStateException("No consumed original R31 generation");}
    void requireHandoffStarted(SonicCdGameModule owner,WorldSession actual,Object originalInvocation){
        requireWorld(actual);if(owner!=module||originalInvocation!=invocation||continuation==null||continuation.core==null||failed||retired||continuation.retired)throw new IllegalStateException("No actual R31 loading continuation was handed off");
    }
    void retire(WorldSession actual){if(world==actual){retired=true;if(continuation!=null)continuation.retired=true;}}

    private static CdAssetBytes zero(int bytes){return CdAssetBytes.copyOf(new byte[bytes]);}
    private static List<OptionalInt> unknownTail(){return List.of(OptionalInt.empty(),OptionalInt.empty());}
    private void phase(Phase phase){state=new State(phase,state.initialization(),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),state.fm(),state.calls());}
    private void call(Call call){var calls=new ArrayList<>(state.calls());calls.add(call);state=new State(state.phase(),state.initialization(),state.loads(),state.progress(),state.main(),state.video(),state.cpuPalettes(),state.wordRam(),state.fm(),calls);}
    private void video(Video video){replace(state.initialization(),state.loads(),state.progress(),state.main(),video,state.cpuPalettes(),state.wordRam(),state.fm());}
    private void progress(CdIpxProgress.State progress){replace(state.initialization(),state.loads(),progress,state.main(),state.video(),state.cpuPalettes(),state.wordRam(),state.fm());}
    private void replace(CdProgramInitialization.State init,SonicCdProgramLoadServices.State loads,CdIpxProgress.State progress,
                         Optional<MainImage> main,Video video,Optional<CpuPalettes> palettes,WordRamKnowledge word,Optional<FmImage> fm){
        state=new State(state.phase(),init,loads,progress,main,video,palettes,word,fm,state.calls());
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
