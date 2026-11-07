package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Original-USA R31A Start/LevelStart, through the loading return or a native RTS.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Initialization.asm56-93,
 * Main.asm19-329,555-583; Palette Fade.asm11-62,178-231; CC Scroll26-304.
 * Complete Entry values do not establish boot, source invocation or World authority.
 * Host owns actual source lifetime, full external images and original synchronous publications.
 */
final class SonicCdR31aLoadingVisit {
    enum Phase { START, INITIAL_TO_WHITE, INITIAL_TO_BLACK, FIRST_PLC, LEVEL_PLC, FROM_WHITE, FROM_BLACK, CLEAR_RETURN_WAIT, RETURNED, READY }
    enum ReturnKind { NONE, WHITE_RESTART, NEXT_LEVEL, NO_LIVES, DEMO }
    enum Boundary { ENTRY, CHECKPOINT_LOAD, ACTIVE_POWERUP, MMD_RETURN_OWNER, ORDINARY_OWNER }
    enum Action {
        START_SCRATCH_CLEAR,VERSION_CACHE,INIT_MARKER,INIT_VDP,CLEAR_DISPLAY_PALETTE,CLEAR_SCROLL_OUTPUT,INIT_CONTROLLERS,GAME_MODE,
        ENTRY_STORES,FRESH_RESET_PREFIX,RESET_SAVED_OBJECT_FLAGS,FRESH_RESET_SUFFIX,INITIAL_FADE_SETUP,PRELUDE_END,
        NORMAL_LOAD_PALETTE,PLAY_LEVEL_MUSIC,CLEAR_POWERUP_FLOWERS,CLEAR_DRAW_QUEUE,CLEAR_FLOWERS,CLEAR_OBJECTS,
        CLEAR_UNKNOWN_BUFFER,CLEAR_MISC,CLEAR_CAMERA,CLEAR_DEMO_INDEX,CLEAR_SCREEN,DROWN_TIMER,LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL,LEVEL_SCROLL,FORCE_LEFT_COLUMN,LEVEL_LAYOUT,INITIAL_FOREGROUND,INITIAL_BACKGROUND,
        COLLISION_POINTER,RESERVED_HUD,RESERVED_TIME_ICON,TITLE_CARD_CREATE,BEGIN_MAIN,UPDATE_ANIMATED_TILES,
        ENABLE_FADE_AND_CLEAR_MARKER,FROM_WHITE_INFO,BEGIN_LEVEL,ENABLE_DISPLAY,COPY_AND_DRAW,CURRENT_ART,VINT_COMPLETED,RETURN_CLEAR
    }
    enum Call {
        START_SCRATCH_CLEAR,VERSION_CACHE,INIT_MARKER,INIT_VDP,CLEAR_DISPLAY_PALETTE,CLEAR_SCROLL_OUTPUT,INIT_CONTROLLERS,GAME_MODE,
        ENTRY_STORES,FRESH_RESET_PREFIX,RESET_SAVED_OBJECT_FLAGS,FRESH_RESET_SUFFIX,INITIAL_FADE_SETUP,PRELUDE_END,
        NORMAL_LOAD_PALETTE,PLAY_LEVEL_MUSIC,CLEAR_POWERUP_FLOWERS,CLEAR_DRAW_QUEUE,CLEAR_FLOWERS,CLEAR_OBJECTS,
        CLEAR_UNKNOWN_BUFFER,CLEAR_MISC,CLEAR_CAMERA,CLEAR_DEMO_INDEX,CLEAR_SCREEN,DROWN_TIMER,LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL,LEVEL_SCROLL,FORCE_LEFT_COLUMN,LEVEL_LAYOUT,INITIAL_FOREGROUND,INITIAL_BACKGROUND,
        COLLISION_POINTER,RESERVED_HUD,RESERVED_TIME_ICON,TITLE_CARD_CREATE,BEGIN_MAIN,UPDATE_ANIMATED_TILES,
        ENABLE_FADE_AND_CLEAR_MARKER,FROM_WHITE_INFO,BEGIN_LEVEL,ENABLE_DISPLAY,COPY_AND_DRAW,CURRENT_ART,VINT_COMPLETED,RETURN_CLEAR,
        CLEAR_PLCS,INITIAL_FADE_SERVICE,INITIAL_FADE_COLOURS,FIRST_WAIT,LEVEL_WAIT,PROCESS_PLCS,IMMEDIATE_LEVEL,
        IMMEDIATE_STANDARD,PALETTE_SONIC,BLOCKS_RAM,PALETTE_LEVEL,QUEUED_LEVEL,SECTION_LEVEL,NO_PLC,LOAD_PLAYER,LIFE_ICON,
        FROM_FADE_SERVICE,FROM_NORMAL_COLOURS,FROM_WATER_COLOURS,NATIVE_RTS,RETURN_WAIT,TITLE_PLC_APPEND
    }
    static final class UnsupportedLoading extends UnsupportedOperationException {
        private final Boundary boundary;
        private UnsupportedLoading(Boundary b){super("R31 loading "+b);boundary=b;}
        Boundary boundary(){return boundary;}
    }
    /**
     * validate is active-Frame-callback safe and authenticates actual source/epoch/thread/Module ownership.
     * act/storeScroll/publish must prebuild every coupled external reference before native owner writes,
     * authenticate this original pending call, and retain their original completed prefix on failure.
     * No returned State/effect/CPU bytes confer authority. capture is a pure full external value fence.
     * INIT_VDP projects controls/display disable; subsequent finite actions project its CRAM,
     * scroll-output and actual current-pattern/plane clears in order. CLEAR_SCREEN owns the native CPU SAT
     * clear and its extra LONG; CPU tail512..639 remains the same owner, never a second fade buffer.
     */
    interface Host {
        void validate(SonicCdR31aLoadingVisit core,CdPlayerFrameRuntime frame,CdR31aAssets assets,CdR31aSectionArt art);
        int livesByte();
        Scroll scroll();void storeScroll(Scroll before,Scroll ready);void events(State state);
        CdAssetBytes cpuSat(State expectedState);
        void act(Action action,State state);
        /**
         * Publish under the original scope. Selector C always uses normal colours; selector12
         * reads the actual Host-owned waterFullscreen byte (Main.asm DoVIntUpdates:942-952).
         * An unknown owner stops at that read. Return the selected input reference only after
         * the actual publication; this value grants no invocation or replay authority.
         */
        CdAssetBytes publish(Publication selected);
        Object capture();void validateRestore(Object snapshot);void restore(Object snapshot);
    }
    record Copies(int foregroundX16_16,int foregroundY16_16,CdR11aBackgroundPlane.CopiedCameras background,
                  int foregroundFlags,CdR11aBackgroundPlane.FlagWords backgroundFlags){
        Copies{Objects.requireNonNull(background);word(foregroundFlags);Objects.requireNonNull(backgroundFlags);}
    }
    record Scroll(CdR11aForegroundScroll.State foreground,CdR11aBackgroundScroll.State background,Copies copies,
                  int foregroundXFractionWord,int topBoundWord,int waterDeformWord){
        Scroll{Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(copies);word(foregroundXFractionWord);word(topBoundWord);word(waterDeformWord);}
    }
    record Program(int demoModeWord,int timeStonesByte,int goodFutureByte,int usePlayer2Byte,int specialStageByte,
                   int palFadeFlagsByte,int gameModeByte,int levelStartedByte,int demoDataIndexWord){
        Program{word(demoModeWord);word(demoDataIndexWord);for(int b:new int[]{timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,gameModeByte,levelStartedByte})octet(b);}
        private Program gameMode(int b){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,b,levelStartedByte,demoDataIndexWord);}
        private Program entry(boolean force){return new Program(0,timeStonesByte,force?1:goodFutureByte,0,specialStageByte,palFadeFlagsByte,gameModeByte,0,demoDataIndexWord);}
        private Program fade(int b){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,b,gameModeByte,levelStartedByte,demoDataIndexWord);}
        private Program demoIndex(int w){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,gameModeByte,levelStartedByte,w);}
        private Program started(){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,gameModeByte&127,1,demoDataIndexWord);}
    }
    record Entry(CdProgramInitialization.State initialization,CdProgramInitialization.ExecutionEnvironment executionEnvironment,
                 CdR31aSectionArt.State section,Program program,CdLevelGlobals level,CdAssetBytes normalPalette,
                 CdAssetBytes fadePalette,CdAssetBytes waterPalette,CdAssetBytes displayedPalette,CdAssetBytes reserved,boolean displayEnabled){
        Entry{Objects.requireNonNull(initialization);Objects.requireNonNull(executionEnvironment);Objects.requireNonNull(section);Objects.requireNonNull(program);Objects.requireNonNull(level);palette(normalPalette);palette(fadePalette);palette(waterPalette);palette(displayedPalette);extent(reserved,1984);}
    }
    record Publication(int selector,Phase phase,CdAssetBytes normalPalette,CdAssetBytes waterPalette,
                       boolean displayEnabled,CdR31aSectionArt.State section){
        Publication{if(selector!=0xC&&selector!=0x12)throw new IllegalArgumentException("Native loading selector C/12 required");Objects.requireNonNull(phase);palette(normalPalette);palette(waterPalette);Objects.requireNonNull(section);}
    }
    record State(Phase phase,CdProgramInitialization.State initialization,CdR31aSectionArt.State section,Program program,
                 CdLevelGlobals level,CdAssetBytes normalPalette,CdAssetBytes fadePalette,CdAssetBytes waterPalette,
                 CdAssetBytes displayedPalette,CdAssetBytes reserved,Optional<CdAssetBytes> blocksRam,Optional<CdAssetBytes> layoutRam,
                 int initialFadeSteps,int fromFadeSteps,boolean displayEnabled,List<Call> calls,ReturnKind returnKind,Optional<CdCommand> music){
        State{Objects.requireNonNull(phase);Objects.requireNonNull(initialization);Objects.requireNonNull(section);Objects.requireNonNull(program);Objects.requireNonNull(level);palette(normalPalette);palette(fadePalette);palette(waterPalette);palette(displayedPalette);extent(reserved,1984);Objects.requireNonNull(blocksRam);Objects.requireNonNull(layoutRam);layoutRam.ifPresent(b->extent(b,2048));if(initialFadeSteps<0||initialFadeSteps>22||fromFadeSteps<0||fromFadeSteps>22)throw new IllegalArgumentException("Native fade progress0..22");calls=List.copyOf(calls);Objects.requireNonNull(returnKind);Objects.requireNonNull(music);}
        private State phase(Phase p){return new State(p,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State initialization(CdProgramInitialization.State i){return new State(phase,i,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State art(CdR31aSectionArt.State a){return new State(phase,initialization,a,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State program(Program p){return new State(phase,initialization,section,p,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State level(CdLevelGlobals l){return new State(phase,initialization,section,program,l,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State colours(CdAssetBytes n,CdAssetBytes f,CdAssetBytes w,CdAssetBytes d){return new State(phase,initialization,section,program,level,n,f,w,d,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State reserved(CdAssetBytes h){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,h,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State geometry(Optional<CdAssetBytes> b,Optional<CdAssetBytes> l){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,b,l,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,music);}
        private State progress(int i,int f,boolean e){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,i,f,e,calls,returnKind,music);}
        private State calls(List<Call> c){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,c,returnKind,music);}
        private State returnKind(ReturnKind k){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,k,music);}
        private State music(Optional<CdCommand> c){return new State(phase,initialization,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,reserved,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls,returnKind,c);}
    }
    static final class Snapshot {
        private final SonicCdR31aLoadingVisit owner;private final State state;private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;private final Object host;
        private Snapshot(SonicCdR31aLoadingVisit owner,State state,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,Object host){this.owner=owner;this.state=state;this.actor=actor;this.shared=shared;this.host=host;}
        State state(){return state;}
    }
    interface OrdinaryOwner {void run(int held,OrdinaryAccess access);}
    record CycleResult(CdAssetBytes palette,CdAssetBytes timers,CdAssetBytes steps,List<CdLevelPaletteCycle.Write> writes,
                       Optional<CdLevelPaletteCycle.Fault> fault){
        CycleResult{SonicCdR31aLoadingVisit.palette(palette);extent(timers,7);extent(steps,7);writes=List.copyOf(writes);Objects.requireNonNull(fault);}
    }
    final class OrdinaryAccess {
        // BEGIN R31 ordinary complete-pair prebuild 20261107. Private live-access identity only.
        private State preparedBefore,prepared;
        private OrdinaryAccess(){}
        private void require(){if(this!=activeOrdinary||!running||failed||values.phase()!=Phase.READY||ordinary==null)throw new IllegalStateException("No actual R31 ordinary continuation");validateHost();}
        void requireOwner(OrdinaryOwner expected){require();if(expected!=ordinary)throw new IllegalArgumentException("Another ordinary owner");}
        State state(){require();return values;}
        void validate(State expected){require();if(expected!=values)throw new IllegalArgumentException("Stale complete Core cut");}
        /** Pure complete prebuild; neither the candidate nor an equal clone is publication authority. */
        State prepare(State expected,CdR31aSectionArt.State section,CdLevelGlobals level,CdAssetBytes reserved,
                      CdAssetBytes normal,CdAssetBytes displayed){
            validate(expected);art.validateState(Objects.requireNonNull(section));Objects.requireNonNull(level);
            extent(reserved,1984);palette(normal);palette(displayed);
            State ready=expected.art(section).level(level).reserved(reserved)
                    .colours(normal,expected.fadePalette(),expected.waterPalette(),displayed);
            preparedBefore=expected;prepared=ready;return ready;
        }
        /** Validate the original pending full pair before any coupled native owner assignment. */
        void validatePrepared(State expected,State ready){
            validate(expected);
            if(preparedBefore!=expected||prepared!=ready)throw new IllegalArgumentException("No original prepared R31 ordinary pair");
        }
        /** Adopt only ready references after native publication. No Host/Frame callback or input read. */
        void adoptAfterNative(State expected,State ready){
            if(this!=activeOrdinary||!running||failed||values!=expected||preparedBefore!=expected||prepared!=ready)
                throw new IllegalStateException("Original prevalidated R31 Core pair expired");
            values=ready;preparedBefore=null;prepared=null;
        }
        /** Native slow service reads the actual scrollLock WORD; artOp retains reached fault prefixes. */
        void serviceSlowPlc(State expected,int scrollLockWord){validate(expected);word(scrollLockWord);artOp(a->art.serviceSlow(a,scrollLockWord));}
        /** Actual ProcessPLCs over the current descriptor/decoder pair, not completed-effect replay. */
        void preparePlc(State expected){validate(expected);artOp(art::prepare);}
        /** R31 has real camera sections. Do not substitute the native R13 no-op section programme. */
        void updateSection(State expected,int cameraXWord){validate(expected);word(cameraXWord);artOp(a->art.updateSection(a,cameraXWord));}
        // END R31 ordinary complete-pair prebuild 20261107.
        // BEGIN R31 ordinary native HUD adoption 20261107.
        /** Actual shared HUD call over the current Core pair. External ring/score/live/bonus
         * prefixes remain in the typed result/fault for the original World owner to adopt once. */
        CdLevelHud.R31Result runHud(State expected,CdLevelHud.R31State input,CdLevelHud.Inputs inputs){
            validate(expected);Objects.requireNonNull(input);Objects.requireNonNull(inputs);
            if(input.section()!=values.section()||input.level()!=values.level())throw new IllegalArgumentException("HUD requires actual R31 Core pair");
            if(hud==null)hud=new CdLevelHud.R31(assets,CdR31aHudAssets.fromAssets(assets),art);
            CdLevelHud.R31Result result;
            try{result=hud.run(input,inputs);}
            catch(CdLevelHud.UnsupportedR31Hud original){try{retainHud(original.state());}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
            catch(CdLevelHud.R31ArtFailure prefix){try{retainHud(prefix.state());}catch(RuntimeException|Error publication){if(publication!=prefix.original())prefix.original().addSuppressed(publication);}throw prefix;}
            // A Host publication fault is not a failure of the already completed HUD operation.
            retainHud(result.state());return result;
        }
        private void retainHud(CdLevelHud.R31State next){
            var before=values.section();values=values.art(next.section()).level(next.level());
            if(before.residency()!=next.section().residency())act(Action.CURRENT_ART);
        }
        // END R31 ordinary native HUD adoption 20261107.
        /** Only the original active OrdinaryAccess can invoke these existing Core algorithms. */
        void copyCameraAndDrawLevel(State expected){validate(expected);copyAndDraw();}
        void scroll(State expected){validate(expected);levelScroll();}
        void finishVInt(State expected){validate(expected);act(Action.VINT_COMPLETED);}
        /** Native deferred Title PLC2; actual World first authenticates the original Title receipt. */
        void appendTitlePlc(State expected){validate(expected);artOp(a->art.appendList(a,2));call(Call.TITLE_PLC_APPEND);}
        /** Pure Main425 programme over the cached four scripts; caller must prebuild/adopt its coupled
         * timer/step/normal owners. This result grants no native completion/publication authority. */
        CycleResult preparePaletteCycles(State expected,CdAssetBytes timers,CdAssetBytes steps,int timeStopByte){
            validate(expected);extent(timers,7);extent(steps,7);octet(timeStopByte);
            if(timeStopByte!=0)return new CycleResult(values.normalPalette(),timers,steps,List.of(),Optional.empty());
            var normal=values.normalPalette();byte[] t=timers.copy(),s=steps.copy();var writes=new ArrayList<CdLevelPaletteCycle.Write>();Optional<CdLevelPaletteCycle.Fault> fault=Optional.empty();
            for(int i=0;i<4;i++){
                var out=CdLevelPaletteCycle.single(cycles.get(i),new CdLevelPaletteCycle.SingleState(normal,t[i]&255,s[i]&255));
                normal=out.state().palette();t[i]=(byte)out.state().timerByte();s[i]=(byte)out.state().stepByte();
                for(var w:out.writes())writes.add(new CdLevelPaletteCycle.Write(i,w.paletteIndex(),w.beforeWord(),w.afterWord()));
                if(out.fault().isPresent()){var f=out.fault().orElseThrow();fault=Optional.of(new CdLevelPaletteCycle.Fault(i,f.access(),f.byteOffset(),f.bytes()));break;}
            }
            var result=new CycleResult(normal,CdAssetBytes.copyOf(t),CdAssetBytes.copyOf(s),writes,fault);
            return result;
        }
    }
    private final Host host;private final CdR31aAssets assets;private final CdPlayerFrameRuntime frame;private final CdR31aSectionArt art;
    private final CdProgramInitialization.ExecutionEnvironment executionEnvironment;private final CdR31aBackgroundScroll background;
    private final CdR31aProgrammeAssets programme;private final CdStageTerrain terrain;private final List<CdLevelPaletteCycle.Script> cycles;
    private CdLevelHud.R31 hud; // Immutable source-bound facade cache; no invocation/capture authority.
    private State values;private boolean running,failed,eventsActive,cpuReading,observing,checking,validating;
    private Action activeAction;private Publication activePublication;private Scroll activeScrollBefore,activeScrollReady;
    private OrdinaryOwner ordinary;private OrdinaryAccess activeOrdinary;

    SonicCdR31aLoadingVisit(Host host,CdR31aAssets assets,CdPlayerFrameRuntime frame,CdR31aSectionArt art,Entry entry)throws IOException{
        this.host=Objects.requireNonNull(host);this.assets=Objects.requireNonNull(assets);this.frame=Objects.requireNonNull(frame);this.art=Objects.requireNonNull(art);Objects.requireNonNull(entry);
        if(!frame.stageSource().ownsR31aAssets(assets)||!art.ownsAssets(assets))throw new IllegalArgumentException("Exact original R31 Frame/assets/art relation required");
        art.validateState(entry.section());executionEnvironment=entry.executionEnvironment();
        background=CdR31aBackgroundScroll.fromAssets(assets);programme=CdR31aProgrammeAssets.fromAssets(assets);terrain=assets.terrain();
        var c=assets.view(CdR31aAssets.Asset.PALETTE_CYCLES);
        cycles=List.of(new CdLevelPaletteCycle.Script(c.slice(0,8),c.slice(8,6)),new CdLevelPaletteCycle.Script(c.slice(14,34),c.slice(48,4)),new CdLevelPaletteCycle.Script(c.slice(52,32),c.slice(84,6)),new CdLevelPaletteCycle.Script(c.slice(90,6),c.slice(96,4)));
        values=new State(Phase.START,entry.initialization(),entry.section(),entry.program(),entry.level(),entry.normalPalette(),entry.fadePalette(),entry.waterPalette(),entry.displayedPalette(),entry.reserved(),Optional.empty(),Optional.empty(),0,0,entry.displayEnabled(),List.of(),ReturnKind.NONE,Optional.empty());
    }
    State state(){return values;}CdR31aAssets assets(){return assets;}boolean failed(){return failed;}
    private void validateHost(){if(validating)throw new IllegalStateException("Reentrant Host validation");validating=true;try{host.validate(this,frame,assets,art);}finally{validating=false;}}
    private record Observation(State core,CdPlayerBody.State actor,CdPlayerBody.Environment environment,CdPlayerArtRuntime.State art,
                               Optional<CdPlayerBody.DisplayPublication> display,CdPlayerFrameRuntime.VIntClock clock,boolean paused,boolean closed,Object host){}
    private Observation observation(){return new Observation(values,frame.state(),frame.environment(),frame.artState(),frame.display(),frame.vIntClock(),frame.paused(),frame.closed(),Objects.requireNonNull(host.capture()));}
    private void unchanged(Observation b){if(values!=b.core()||frame.state()!=b.actor()||frame.environment()!=b.environment()||frame.artState()!=b.art()||frame.display()!=b.display()||frame.vIntClock()!=b.clock()||frame.paused()!=b.paused()||frame.closed()!=b.closed()||!Objects.equals(host.capture(),b.host()))throw new IllegalStateException("Host observation changed complete Core/Frame/external cut");validateHost();}
    private <T>T observe(Supplier<T> read){
        if(observing)throw new IllegalStateException("Reentrant Host observation");observing=true;Observation before=null;
        try{before=observation();T result=read.get();unchanged(before);return result;}
        catch(RuntimeException|Error original){if(before==null)terminal(original);else try{unchanged(before);}catch(RuntimeException|Error violation){if(violation!=original)original.addSuppressed(violation);terminal(original);}throw original;}
        finally{observing=false;}
    }
    private void current(){
        if(running||failed||observing||checking||validating||activeAction!=null||activePublication!=null||eventsActive||cpuReading||activeScrollBefore!=null)throw new IllegalStateException("R31 loading active or failed");
        checking=true;try{validateHost();frame.captureActor();frame.captureShared();requireSource();art.validateState(values.section());}finally{checking=false;}
    }
    private void requireSource(){var g=frame.environment().movement().gates();if(!frame.stageSource().ownsR31aAssets(assets)||!art.ownsAssets(assets)||g.zoneAct()!=0x100||g.timeZone()!=1)throw new IllegalStateException("R31 raw source/gates changed");}
    void requireServiceIdle(){current();}
    /** Retirement checks local activity without borrowing an expired boot/session permit.
     * The Module/World still validate their exact retained pair, thread and render lifetime. */
    void requireRetirementIdle(Host expected){
        if(expected!=host||running||observing||checking||validating||activeAction!=null||activePublication!=null
                ||eventsActive||cpuReading||activeScrollBefore!=null||activeOrdinary!=null)
            throw new IllegalStateException("R31 retirement cannot interrupt an original Core operation");
    }
    void requireAction(Host expected,Action action,State state){if(expected!=host||!running||failed||activeAction!=action||values!=state)throw new IllegalStateException("No original Core action");validateHost();}
    void requirePublication(Host expected,Publication p){if(expected!=host||!running||failed||activePublication==null)throw new IllegalStateException("No original Core publication");if(p!=activePublication)throw new IllegalArgumentException("Equal values are not original publication");validateHost();}
    void requireScrollStore(Host expected,Scroll before,Scroll ready){if(expected!=host||!running||failed||activeScrollBefore==null)throw new IllegalStateException("No original Core scroll store");if(before!=activeScrollBefore||ready!=activeScrollReady)throw new IllegalArgumentException("Equal values are not original scroll store");validateHost();}
    void requireEvents(Host expected,State state){if(expected!=host||!running||failed||!eventsActive||values!=state)throw new IllegalStateException("No original Core event call");validateHost();}
    void requireCpuSat(Host expected,State state){if(expected!=host||!running||failed||!cpuReading||values!=state)throw new IllegalStateException("No original from-fade CPU read");validateHost();}
    private void call(Call c){var list=new ArrayList<>(values.calls());list.add(c);values=values.calls(list);}
    private void act(Action action){validateHost();if(activeAction!=null)throw new IllegalStateException("Reentrant Core action");activeAction=action;try{host.act(action,values);validateHost();}finally{activeAction=null;}call(Call.valueOf(action.name()));}
    private static void stop(Boundary b){throw new UnsupportedLoading(b);}
    private void terminal(Throwable original){failed=true;try{frame.close();}catch(RuntimeException|Error close){if(close!=original)original.addSuppressed(close);}}
    private Scroll readScroll(){validateHost();return Objects.requireNonNull(observe(host::scroll));}
    private void storeScroll(Scroll before,Scroll ready){if(before==ready)return;validateHost();if(activeScrollBefore!=null)throw new IllegalStateException("Reentrant scroll store");activeScrollBefore=before;activeScrollReady=ready;try{host.storeScroll(before,ready);validateHost();if(host.scroll()!=ready)throw new IllegalStateException("Original scroll was not synchronously adopted");}finally{activeScrollBefore=null;activeScrollReady=null;}}
    void bindOrdinaryOwner(Host expected,OrdinaryOwner owner){current();if(expected!=host||values.phase()!=Phase.START||ordinary!=null)throw new IllegalStateException("Bind actual ordinary owner once before Start");ordinary=Objects.requireNonNull(owner);}

    void begin(){
        current();if(values.phase()!=Phase.START)throw new IllegalStateException("Already began");
        running=true;try{
            if(CdProgramInitialization.selectStartBranch(values.initialization())==CdProgramInitialization.Branch.DO_INIT){
                act(Action.START_SCRATCH_CLEAR);
                var region=CdProgramInitialization.requireExecutionRegion(executionEnvironment);
                values=values.initialization(CdProgramInitialization.writeVersionCache(values.initialization(),region));act(Action.VERSION_CACHE);
                values=values.initialization(CdProgramInitialization.writeInitMarker(values.initialization()));act(Action.INIT_MARKER);
            }
            values=values.progress(0,0,false);act(Action.INIT_VDP);
            values=values.colours(values.normalPalette(),values.fadePalette(),values.waterPalette(),zero(128));act(Action.CLEAR_DISPLAY_PALETTE);
            var s=readScroll();var b=s.background();storeScroll(s,new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),0),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));act(Action.CLEAR_SCROLL_OUTPUT);
            artOp(art::clearCurrentPatterns);
            values=values.initialization(CdProgramInitialization.initializeControllers(values.initialization()));act(Action.INIT_CONTROLLERS);
            values=values.program(values.program().gameMode(0));act(Action.GAME_MODE);
            levelStart();
        }catch(RuntimeException|Error original){terminal(original);throw original;}finally{running=false;}
    }
    private void levelStart(){
        var p=values.program();values=values.program(p.entry(p.timeStonesByte()==0x7F&&frame.environment().globals().timeAttackMode()==0));frame.beginLevelStart();act(Action.ENTRY_STORES);
        var l=values.level();boolean fresh=(l.plcLoadFlags()&1)==0;
        values=values.level(level(l,l.levelRestart(),l.updateHudTime(),fresh?0:l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|1,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
        if(fresh){values=values.program(values.program().fade(0));act(Action.FRESH_RESET_PREFIX);act(Action.RESET_SAVED_OBJECT_FLAGS);l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));act(Action.FRESH_RESET_SUFFIX);}
        values=values.program(values.program().gameMode(values.program().gameModeByte()|128));artOp(art::clearPlcs);call(Call.CLEAR_PLCS);
        boolean white=values.program().specialStageByte()!=0||(frame.environment().movement().gates().timeZone()&128)!=0;
        boolean fade=values.program().specialStageByte()!=0||(values.program().palFadeFlagsByte()&1)!=0;
        if(values.program().specialStageByte()==0)values=values.program(values.program().fade(values.program().palFadeFlagsByte()|1));
        if(fade){values=values.phase(white?Phase.INITIAL_TO_WHITE:Phase.INITIAL_TO_BLACK);act(Action.INITIAL_FADE_SETUP);}
        else afterInitial(white);
    }
    private void afterInitial(boolean white){
        var l=values.level();
        if(white){frame.clearLevelStartWarpDirection();act(Action.PRELUDE_END);if(l.levelRestart()!=0){values=values.level(level(l,0,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));returned(ReturnKind.WHITE_RESTART);return;}}
        else if(l.levelRestart()==2){values=values.level(level(l,0,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver())).program(values.program().fade(0));clearReturn(ReturnKind.NEXT_LEVEL);return;}
        else{int lives=observe(host::livesByte);octet(lives);if(lives==0){values=values.level(level(l,l.levelRestart(),l.updateHudTime(),0,0,0,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver())).program(values.program().fade(0));clearReturn(ReturnKind.NO_LIVES);return;}}
        if(values.program().demoDataIndexWord()==0x800){values=values.program(values.program().demoIndex(0).entry(false).fade(0));returned(ReturnKind.DEMO);return;}
        boolean fill=CdProgramInitialization.requirePalClearFlagBit0(values.initialization())||(frame.environment().movement().gates().timeZone()&128)!=0;
        values=values.colours(fill?white():zero(128),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(Phase.FIRST_PLC);act(Action.NORMAL_LOAD_PALETTE);
    }
    private void clearReturn(ReturnKind kind){values=values.colours(zero(128),values.fadePalette(),values.waterPalette(),values.displayedPalette()).returnKind(kind).phase(Phase.CLEAR_RETURN_WAIT);act(Action.RETURN_CLEAR);}
    private void returned(ReturnKind kind){values=values.returnKind(kind).phase(Phase.RETURNED);call(Call.NATIVE_RTS);}
    void service(int held){
        current();octet(held);if(values.phase()==Phase.START)throw new IllegalStateException("Begin before service");
        if(values.phase()==Phase.RETURNED)stop(Boundary.MMD_RETURN_OWNER);
        if(values.phase()==Phase.READY&&ordinary==null)stop(Boundary.ORDINARY_OWNER);
        running=true;try{switch(values.phase()){
            case INITIAL_TO_WHITE,INITIAL_TO_BLACK -> fadeService(held,true);
            case FROM_WHITE,FROM_BLACK -> fadeService(held,false);
            case FIRST_PLC,LEVEL_PLC -> plcService(held);
            case CLEAR_RETURN_WAIT -> {loadService(held);call(Call.RETURN_WAIT);returned(values.returnKind());}
            case READY -> {var access=new OrdinaryAccess();activeOrdinary=access;try{ordinary.run(held,access);}finally{activeOrdinary=null;}}
            default -> throw new IllegalStateException("Unserviceable loading phase");
        }}catch(RuntimeException|Error original){terminal(original);throw original;}finally{running=false;}
    }
    private void publish(int selector){
        validateHost();var p=new Publication(selector,values.phase(),values.normalPalette(),values.waterPalette(),values.displayEnabled(),values.section());
        if(activePublication!=null)throw new IllegalStateException("Reentrant publication");activePublication=p;
        CdAssetBytes displayed;
        try{
            displayed=host.publish(p);validateHost();
            if(displayed!=p.normalPalette()&&(selector!=0x12||displayed!=p.waterPalette()))
                throw new IllegalArgumentException("Publication did not return its original selected palette");
        }finally{activePublication=null;}
        values=values.colours(values.normalPalette(),values.fadePalette(),values.waterPalette(),displayed);
    }
    private void loadService(int held){frame.serviceLevelLoad(held,()->publish(0xC),this::copyAndDraw,()->artOp(art::serviceFast));act(Action.VINT_COMPLETED);}
    private void plcService(int held){
        var phase=values.phase();loadService(held);call(phase==Phase.FIRST_PLC?Call.FIRST_WAIT:Call.LEVEL_WAIT);artOp(art::prepare);call(Call.PROCESS_PLCS);
        if(values.section().queue().headSource()==0&&values.section().remainingTilesWord()==0){if(phase==Phase.FIRST_PLC)mainLoad();else finishLoad();}
    }
    private void fadeService(int held,boolean initial){
        var phase=values.phase();
        frame.serviceLevelPaletteFade(held,()->{publish(0x12);if(values.level().fadeEnableDisplay()==1){var l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),2,l.powerup(),l.timeOver())).progress(values.initialFadeSteps(),values.fromFadeSteps(),true);act(Action.ENABLE_DISPLAY);}},()->artOp(art::serviceFast));
        act(Action.VINT_COMPLETED);call(initial?Call.INITIAL_FADE_SERVICE:Call.FROM_FADE_SERVICE);
        if(initial){var pair=new CdLevelPaletteFade.Palettes(values.normalPalette(),values.waterPalette());var out=phase==Phase.INITIAL_TO_WHITE?CdLevelPaletteFade.stepToWhite(pair):CdLevelPaletteFade.stepToBlack(pair);values=values.colours(out.palette(),values.fadePalette(),out.waterPalette(),values.displayedPalette());call(Call.INITIAL_FADE_COLOURS);}
        else{
            var normal=phase==Phase.FROM_WHITE?CdLevelPaletteFade.stepFromWhite(values.normalPalette(),values.fadePalette()):CdLevelPaletteFade.stepFromBlack(values.normalPalette(),values.fadePalette(),0x202F);
            values=values.colours(normal,values.fadePalette(),values.waterPalette(),values.displayedPalette());call(Call.FROM_NORMAL_COLOURS);
            // Native zone1 continuation reads the real live CPU SAT alias only AFTER normal colours.
            CdAssetBytes cpu;cpuReading=true;try{cpu=observe(()->host.cpuSat(values));}finally{cpuReading=false;}extent(cpu,640);
            var water=phase==Phase.FROM_WHITE?CdLevelPaletteFade.stepFromWhite(values.waterPalette(),cpu.slice(512,128)):CdLevelPaletteFade.stepFromBlack(values.waterPalette(),cpu.slice(512,128),0x202F);
            values=values.colours(values.normalPalette(),values.fadePalette(),water,values.displayedPalette());call(Call.FROM_WATER_COLOURS);
        }
        artOp(art::prepare);call(Call.PROCESS_PLCS);values=values.progress(values.initialFadeSteps()+(initial?1:0),values.fromFadeSteps()+(initial?0:1),values.displayEnabled());
        if(initial&&values.initialFadeSteps()==22){if(phase==Phase.INITIAL_TO_WHITE)values=values.program(values.program().fade(values.program().palFadeFlagsByte()&~1));afterInitial(phase==Phase.INITIAL_TO_WHITE);}
        else if(!initial&&values.fromFadeSteps()==22){values=values.program(values.program().started()).phase(Phase.READY);act(Action.BEGIN_LEVEL);}
    }
    private void artOp(UnaryOperator<CdR31aSectionArt.State> operation){
        var before=values.section();try{values=values.art(operation.apply(before));}
        catch(CdR31aSectionArt.SectionArtException original){values=values.art(original.state());original.completedRamPrefix().ifPresent(b->values=values.geometry(Optional.of(b),values.layoutRam()));try{act(Action.CURRENT_ART);}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
        if(values.section().residency()!=before.residency())act(Action.CURRENT_ART);
    }
    private void mainLoad(){
        var g=frame.environment().movement().gates();
        values=values.music(Optional.of(programme.selectLevelMusic(g.zoneAct(),g.timeZone(),frame.environment().globals().timeAttackMode(),values.program().goodFutureByte())));act(Action.PLAY_LEVEL_MUSIC);
        int id=assets.view(CdR31aAssets.Asset.LEVEL_DATA_INDEX).unsignedByte(0);if(id!=0){artOp(a->art.loadImmediateList(a,id));call(Call.IMMEDIATE_LEVEL);}artOp(a->art.loadImmediateList(a,1));call(Call.IMMEDIATE_STANDARD);
        var l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),0,0,0,l.timeOver()));act(Action.CLEAR_POWERUP_FLOWERS);act(Action.CLEAR_DRAW_QUEUE);
        frame.clearLevelStartFlowersPlayerValues();clearFlowersScroll();act(Action.CLEAR_FLOWERS);
        values=values.reserved(zero(1984));act(Action.CLEAR_OBJECTS);act(Action.CLEAR_UNKNOWN_BUFFER);act(Action.CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues();clearCameraScroll();act(Action.CLEAR_CAMERA);values=values.program(values.program().demoIndex(0));act(Action.CLEAR_DEMO_INDEX);
        byte[] water=values.waterPalette().copy();Arrays.fill(water,0,4,(byte)0);values=values.colours(values.normalPalette(),values.fadePalette(),CdAssetBytes.copyOf(water),values.displayedPalette());clearScreenScroll();act(Action.CLEAR_SCREEN);act(Action.DROWN_TIMER);
        paletteCopy(assets.view(CdR31aAssets.Asset.PAL_SONIC),0,true);paletteCopy(assets.view(CdR31aAssets.Asset.PAL_SONIC),0,false);call(Call.PALETTE_SONIC);
        var bounds=assets.bounds();frame.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(bounds.left(),bounds.right(),bounds.bottom(),frame.environment().movement().bounds().bossFight()),bounds.cameraCentreY());initializeSizeScroll();act(Action.LEVEL_SIZE);
        if(values.level().spawnMode()!=0)stop(Boundary.CHECKPOINT_LOAD);
        var start=assets.initialCentre();frame.initializeLevelSizeCentrePlayerValues(start.centreX(),start.centreY());setupScroll();act(Action.SETUP_CAMERA_AND_SCROLL);levelScroll();act(Action.LEVEL_SCROLL);forceLeftColumn();act(Action.FORCE_LEFT_COLUMN);
        try{var blocks=art.decodeBlocksToRam(values.section());values=values.art(blocks.state()).geometry(Optional.of(blocks.blocks()),values.layoutRam());}
        catch(CdR31aSectionArt.SectionArtException original){values=values.art(original.state());original.completedRamPrefix().ifPresent(b->values=values.geometry(Optional.of(b),values.layoutRam()));throw original;}call(Call.BLOCKS_RAM);
        values=values.geometry(values.blocksRam(),Optional.of(terrain.layout().ram()));act(Action.LEVEL_LAYOUT);paletteCopy(assets.view(CdR31aAssets.Asset.PAL_LEVEL),32,false);call(Call.PALETTE_LEVEL);
        if(values.level().spawnMode()!=0){int x=readScroll().foreground().cameraX();artOp(a->art.initSection(a,x));call(Call.SECTION_LEVEL);}
        else if((values.level().plcLoadFlags()&2)!=0){int plc=assets.levelIndex().blocks().plcId();if(plc!=0){artOp(a->art.appendList(a,plc));call(Call.QUEUED_LEVEL);}else call(Call.NO_PLC);}
        else call(Call.NO_PLC);
        act(Action.INITIAL_FOREGROUND);act(Action.INITIAL_BACKGROUND);act(Action.COLLISION_POINTER);values=values.phase(Phase.LEVEL_PLC);
    }
    private void finishLoad(){
        if(values.level().spawnMode()==0)frame.applyLevelStartNormalPlayer();else frame.applyLevelStartWarpPlayer();call(Call.LOAD_PLAYER);
        byte[] h=values.reserved().copy();for(int whole:new int[]{2,3,5})h[(whole-1)*64]=0x1C;h[2*64+0x28]=1;h[4*64+0x29]=1;values=values.reserved(CdAssetBytes.copyOf(h));act(Action.RESERVED_HUD);
        artOp(a->art.loadLifeIcon(a,frame.environment().movement().gates().timeZone()));call(Call.LIFE_ICON);h=values.reserved().copy();h[30*64]=0x19;h[30*64+0x28]=10;values=values.reserved(CdAssetBytes.copyOf(h));act(Action.RESERVED_TIME_ICON);
        var l=values.level();boolean title=(l.plcLoadFlags()&2)==0;values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|2,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
        if(title){h=values.reserved().copy();h[3*64]=0x3C;values=values.reserved(CdAssetBytes.copyOf(h));act(Action.TITLE_CARD_CREATE);if(frame.environment().controls().controlLocked()!=1)throw new IllegalStateException("Actual Title setup control lock was not stored");var a=values.section();values=values.art(art.fromValues(a.codeTable(),a.residency(),a.sectionWord()&255,a.queue(),a.decoder(),a.savedDecoder(),a.remainingTilesWord(),a.processBudgetWord(),a.hintWord(),a.padWord()));}
        frame.clearLevelStartControlsAndBoredTimer();frame.clearLevelStartPlayerGlobals();l=values.level();values=values.level(level(l,0,1,l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),0));act(Action.BEGIN_MAIN);
        if(CdPowerupArtRuntime.gate(values.level().powerup()).copyRequired())stop(Boundary.ACTIVE_POWERUP);act(Action.UPDATE_ANIMATED_TILES);
        l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),1,l.powerup(),l.timeOver()));boolean marked=frame.clearLevelStartTimeZoneMarker();
        if(frame.environment().movement().gates().timeZone()!=1)stop(Boundary.ENTRY);
        boolean white=marked;if(!marked){white=CdProgramInitialization.requirePalClearFlagBit0(values.initialization());var i=values.initialization();values=values.initialization(new CdProgramInitialization.State(i.controllerSetupBit(),i.initMarker(),i.versionCache(),CdProgramInitialization.KnownBit.CLEAR));}
        act(Action.ENABLE_FADE_AND_CLEAR_MARKER);if(white)act(Action.FROM_WHITE_INFO);
        byte[] normal=values.normalPalette().copy();if(white)normal=white().copy();else Arrays.fill(normal,32,128,(byte)0);
        values=values.colours(CdAssetBytes.copyOf(normal),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(white?Phase.FROM_WHITE:Phase.FROM_BLACK);
    }
    private void paletteCopy(CdAssetBytes source,int offset,boolean normal){byte[] bytes=(normal?values.normalPalette():values.fadePalette()).copy();System.arraycopy(source.copy(),0,bytes,offset,source.length());values=values.colours(normal?CdAssetBytes.copyOf(bytes):values.normalPalette(),normal?values.fadePalette():CdAssetBytes.copyOf(bytes),values.waterPalette(),values.displayedPalette());}
    private void clearFlowersScroll(){var s=readScroll();var b=s.background();var c=new CdR11aBackgroundPlane.Camera(0,0);var copies=new Copies(0,0,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),0,new CdR11aBackgroundPlane.FlagWords(0,0,0));storeScroll(s,new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),zero(512),b.deformationRam(),b.hscrollRam(),b.vscroll()),copies,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));}
    private void clearCameraScroll(){var s=readScroll();var b=s.background();storeScroll(s,new Scroll(new CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0),bg(b,0,0,0,0,0,0,zero(8),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll()),s.copies(),0,0,0));}
    private void clearScreenScroll(){var s=readScroll();var b=s.background();storeScroll(s,new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),zero(1024),0),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));}
    private void initializeSizeScroll(){var s=readScroll();var f=s.foreground();var b=assets.bounds();storeScroll(s,new Scroll(new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),16,16,f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),b.bottom(),b.left(),f.bottomBoundShift(),f.scrollLock()),s.background(),s.copies(),s.foregroundXFractionWord(),b.top(),s.waterDeformWord()));}
    private void setupScroll(){var s=readScroll();var m=frame.state().motion();var f=CdR11aForegroundScroll.checkpointSetupCamera(s.foreground(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,frame.environment().movement().bounds());var b=background.initialize(s.background(),new CdR31aBackgroundScroll.InitInput(f.cameraX(),f.cameraYWord()));storeScroll(s,new Scroll(f,b,s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));}
    private void levelScroll(){
        var s=readScroll();var f=s.foreground();if((f.scrollLock()&0xFF00)!=0)return;
        var b=s.background();var clear=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),0,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());
        storeScroll(s,new Scroll(clear,bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll()),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));
        eventsActive=true;try{host.events(values);validateHost();}finally{eventsActive=false;}
        s=readScroll();var m=frame.state().motion();var env=frame.environment();var movement=env.movement();
        var input=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),movement.bounds(),s.topBoundWord(),env.globals().cameraXCentre(),movement.globals().cameraYCentre(),frame.stageSource().variant(),values.level().updateHudTime(),320,224);
        var result=CdR11aForegroundScroll.stepAfterEvents(s.foreground(),input);f=result.state();var ready=new Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());frame.applyScrollFeedback(result.bounds(),result.cameraYCentre());storeScroll(s,ready);
        s=readScroll();try{var r=background.step(s.background(),new CdR31aBackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),f.scrollLock()>>>8,s.waterDeformWord()));storeScroll(s,new Scroll(f,r.state(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),r.waterDeformWord()));}
        catch(CdR31aBackgroundScroll.ScrollFault original){var r=original.prefix();try{storeScroll(s,new Scroll(f,r.state(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),r.waterDeformWord()));}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
    }
    private void forceLeftColumn(){var s=readScroll();var f=s.foreground();var forced=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags()|0x400,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());storeScroll(s,new Scroll(forced,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));}
    private void copyAndDraw(){var s=readScroll();var f=s.foreground();var b=s.background();var cams=new CdR11aBackgroundPlane.CopiedCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));var c=new Copies((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16(),cams,f.scrollFlags(),new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3()));storeScroll(s,new Scroll(f,b,c,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));act(Action.COPY_AND_DRAW);}
    private static CdR11aBackgroundScroll.State bg(CdR11aBackgroundScroll.State old,int x,int y,int x2,int y2,int x3,int y3,CdAssetBytes cross,int flags,int flags2,int flags3,CdAssetBytes cloud,CdAssetBytes deform,CdAssetBytes hscroll,int vscroll){return new CdR11aBackgroundScroll.State(x,y,x2,y2,x3,y3,cross,flags,flags2,flags3,cloud,deform,hscroll,vscroll);}

    Snapshot capture(){current();var external=Objects.requireNonNull(observe(host::capture));validateHost();return new Snapshot(this,values,frame.captureActor(),frame.captureShared(),external);}
    void validateRestore(Snapshot s){
        current();Objects.requireNonNull(s);if(s.owner!=this)throw new IllegalArgumentException("Another R31 visit capture");
        frame.validateActorRestore(s.actor);frame.validateSharedRestore(s.shared);observe(()->{host.validateRestore(s.host);return Boolean.TRUE;});art.validateState(s.state.section());
        var g=s.shared.environment().movement().gates();var v=s.state;
        if(g.zoneAct()!=0x100||g.timeZone()!=1||(v.phase()==Phase.READY&&v.fromFadeSteps()!=22)||(v.phase()==Phase.INITIAL_TO_BLACK||v.phase()==Phase.INITIAL_TO_WHITE)&&v.initialFadeSteps()>=22||(v.phase()==Phase.RETURNED||v.phase()==Phase.CLEAR_RETURN_WAIT)!=(v.returnKind()!=ReturnKind.NONE))throw new IllegalArgumentException("Captured R31 source/phase/progress differs");
    }
    void restore(Snapshot s){validateRestore(s);running=true;try{host.restore(s.host);validateHost();frame.restoreActor(s.actor);frame.restoreShared(s.shared);values=s.state;}catch(RuntimeException|Error original){terminal(original);throw original;}finally{running=false;}}
    private static CdLevelGlobals level(CdLevelGlobals old,int restart,int update,int checkpoint,int spawn,int flags,int flowers,int fade,int power,int over){return new CdLevelGlobals(restart,update,checkpoint,spawn,flags,flowers,fade,power,over);}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void extent(CdAssetBytes b,int n){if(Objects.requireNonNull(b).length()!=n)throw new IllegalArgumentException("Complete explicit native value required");}
    private static void palette(CdAssetBytes b){extent(b,128);}
    private static CdAssetBytes zero(int n){return CdAssetBytes.copyOf(new byte[n]);}
    private static CdAssetBytes white(){byte[] b=new byte[128];for(int i=0;i<128;i+=2){b[i]=14;b[i+1]=(byte)238;}return CdAssetBytes.copyOf(b);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
