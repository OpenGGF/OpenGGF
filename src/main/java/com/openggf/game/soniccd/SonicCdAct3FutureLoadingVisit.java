package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.function.Supplier;

/**
 * Source-ordered original-USA R13C/D loading Core and finite actual-Host contract.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff IPX.asm:371-374,606-614;
 * Main.asm:19-329; Scroll(Act3):7 includes Act2Past:11-137 with ACT3 variant;
 * Load Level Data(Act3):11-43; Palette Fade.asm:11-30,178-201,273-309.
 * Complete entry values are data only. Host must authenticate its actual module programme,
 * selected goodFuture operand, sole Frame/assets/art and current native phase on every call.
 * This component creates no Module/World/GPU/clock owner or public admission factory.
 */
final class SonicCdAct3FutureLoadingVisit {
    enum Phase { ENTRY, INITIAL_TO_WHITE, INITIAL_TO_BLACK, FIRST_PLC, LEVEL_PLC, FROM_WHITE, FROM_BLACK, READY, OLD_EXIT_PRELUDE, OLD_BLACK_FADE, OLD_CLEAR_WAIT, OLD_RETURNED, OLD_IPX_RETURNED, OLD_IPX_BOSS_RETURNED }
    enum Boundary { ENTRY, PAL_CLEAR_KNOWLEDGE, CHECKPOINT_LOAD, LEVEL_RESTART_RETURN, NO_LIVES_RETURN, DEMO_RETURN, ACTIVE_POWERUP, ORDINARY_OWNER, OLD_EXIT_ENTRY, OLD_EXIT_WHITE_RETURN, IPX_BOSS_GAME_OVER, IPX_BOSS_SAVED_ZONE_OWNER, IPX_RUN_R1_TIME_ATTACK_UNLOCK_OWNER, IPX_RUN_R1_UNLOCK_CONTINUATION_OWNER }
    enum Action {
        ENTRY_STORES,FRESH_RESET_PREFIX,RESET_SAVED_OBJECT_FLAGS,FRESH_RESET_SUFFIX,INITIAL_FADE_SETUP,PRELUDE_END,
        NORMAL_LOAD_PALETTE,PLAY_LEVEL_MUSIC,CLEAR_POWERUP_FLOWERS,CLEAR_DRAW_QUEUE,CLEAR_FLOWERS,CLEAR_OBJECTS,
        CLEAR_UNKNOWN_BUFFER,CLEAR_MISC,CLEAR_CAMERA,CLEAR_DEMO_INDEX,CLEAR_SCREEN,DROWN_TIMER,LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL,LEVEL_SCROLL,FORCE_LEFT_COLUMN,LEVEL_LAYOUT,INITIAL_FOREGROUND,INITIAL_BACKGROUND,
        COLLISION_POINTER,RESERVED_HUD,RESERVED_TIME_ICON,TITLE_CARD_CREATE,BEGIN_MAIN,UPDATE_ANIMATED_TILES,
        ENABLE_FADE_AND_CLEAR_MARKER,FROM_WHITE_INFO,BEGIN_LEVEL,ENABLE_DISPLAY,COPY_AND_DRAW,CURRENT_ART,VINT_COMPLETED,OLD_EXIT_FADE_SETUP,OLD_EXIT_SETTLE_CURRENT
    }
    enum Call {
        ENTRY_STORES,FRESH_RESET_PREFIX,RESET_SAVED_OBJECT_FLAGS,FRESH_RESET_SUFFIX,INITIAL_FADE_SETUP,PRELUDE_END,
        NORMAL_LOAD_PALETTE,PLAY_LEVEL_MUSIC,CLEAR_POWERUP_FLOWERS,CLEAR_DRAW_QUEUE,CLEAR_FLOWERS,CLEAR_OBJECTS,
        CLEAR_UNKNOWN_BUFFER,CLEAR_MISC,CLEAR_CAMERA,CLEAR_DEMO_INDEX,CLEAR_SCREEN,DROWN_TIMER,LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL,LEVEL_SCROLL,FORCE_LEFT_COLUMN,LEVEL_LAYOUT,INITIAL_FOREGROUND,INITIAL_BACKGROUND,
        COLLISION_POINTER,RESERVED_HUD,RESERVED_TIME_ICON,TITLE_CARD_CREATE,BEGIN_MAIN,UPDATE_ANIMATED_TILES,
        ENABLE_FADE_AND_CLEAR_MARKER,FROM_WHITE_INFO,BEGIN_LEVEL,ENABLE_DISPLAY,COPY_AND_DRAW,CURRENT_ART,VINT_COMPLETED,
        CLEAR_PLCS,INITIAL_FADE_SERVICE,INITIAL_FADE_COLOURS,FIRST_WAIT,LEVEL_WAIT,PROCESS_PLCS,IMMEDIATE_LEVEL,
        IMMEDIATE_STANDARD,PALETTE_SONIC,BLOCKS_RAM,PALETTE_LEVEL,QUEUED_LEVEL,LOAD_PLAYER,LIFE_ICON,
        FROM_FADE_SERVICE,FROM_FADE_COLOURS,TITLE_PLC_APPEND,BOSS_PLC_APPEND,RESULTS_PLC_APPEND,
        OLD_EXIT_FADE_SETUP,OLD_EXIT_SETTLE_CURRENT,OLD_EXIT_BLANK,OLD_EXIT_BEGIN,OLD_EXIT_BLACK_SERVICE,
        OLD_EXIT_BLACK_COLOURS,OLD_EXIT_CLEAR,OLD_EXIT_CLEAR_SERVICE,OLD_EXIT_RTS,OLD_EXIT_IPX_RETURN,IPX_BOSS_RETURN
    }
    static final class UnsupportedLoading extends UnsupportedOperationException {
        private final Boundary boundary;
        private UnsupportedLoading(Boundary boundary){super("R13 loading "+boundary);this.boundary=boundary;}
        Boundary boundary(){return boundary;}
    }
    /** Private actual-Host seam, not a generic public callback registration or authority carried by records.
     * validate must work during active Frame V-int callbacks without capture/reentry.
     * selectedGoodFutureByte is the actual RunBossLevel selection operand, not the later mutated BYTE.
     * events executes complete USA PPZ3 and common-bound stores before Core's X/Y continuation.
     * It owns its original partial external prefix on a throw; no synthesized completed-event input.
     * act executes external native projections (globals, bank, planes, sound, timing); it must throw
     * at its first unowned action. TITLE_CARD_CREATE projects whole4 and actual ctrlLocked BYTE1.
     * Capture is a complete immutable external image with stable value equality and no writes.
     */
    interface Host {
        void validate(SonicCdAct3FutureLoadingVisit visit,CdPlayerFrameRuntime frame,CdR13FutureAssets assets,CdR13SectionArt art);
        int selectedGoodFutureByte();int livesByte();
        Scroll scroll();void storeScroll(Scroll before,Scroll ready);void events(State state);
        void act(Action action,State state);void publish(Publication selected);
        Object capture();void validateRestore(Object snapshot);void restore(Object snapshot);
    }
    record Copies(int foregroundX16_16,int foregroundY16_16,CdR11aBackgroundPlane.CopiedCameras background,
                  int foregroundFlags,CdR11aBackgroundPlane.FlagWords backgroundFlags){
        Copies{Objects.requireNonNull(background);word(foregroundFlags);Objects.requireNonNull(backgroundFlags);}
    }
    /** One Host-owned complete live/copied scroll value; Core never keeps another mutable copy. */
    record Scroll(CdR11aForegroundScroll.State foreground,CdR11aBackgroundScroll.State background,Copies copies,
                  int foregroundXFractionWord,int topBoundWord){
        Scroll{Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(copies);word(foregroundXFractionWord);word(topBoundWord);}
    }
    record Program(int demoModeWord,int timeStonesByte,int goodFutureByte,int usePlayer2Byte,int specialStageByte,
                   int palFadeFlagsByte,CdProgramInitialization.KnownBit palClearFlagBit0,int gameModeByte,
                   int levelStartedByte,int demoDataIndexWord){
        Program{word(demoModeWord);word(demoDataIndexWord);Objects.requireNonNull(palClearFlagBit0);for(int b:new int[]{timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,gameModeByte,levelStartedByte})octet(b);}
        private Program entry(boolean force){return new Program(0,timeStonesByte,force?1:goodFutureByte,0,specialStageByte,palFadeFlagsByte,palClearFlagBit0,gameModeByte,0,demoDataIndexWord);}
        private Program initializing(){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,palClearFlagBit0,gameModeByte|128,levelStartedByte,demoDataIndexWord);}
        private Program fade(int b){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,b,palClearFlagBit0,gameModeByte,levelStartedByte,demoDataIndexWord);}
        private Program demoIndex(int w){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,palClearFlagBit0,gameModeByte,levelStartedByte,w);}
        private Program palClear(CdProgramInitialization.KnownBit b){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,b,gameModeByte,levelStartedByte,demoDataIndexWord);}
        private Program started(){return new Program(demoModeWord,timeStonesByte,goodFutureByte,usePlayer2Byte,specialStageByte,palFadeFlagsByte,palClearFlagBit0,gameModeByte&127,1,demoDataIndexWord);}
    }
    record Entry(CdR13SectionArt.State section,Program program,CdLevelGlobals level,CdAssetBytes normalPalette,
                 CdAssetBytes fadePalette,CdAssetBytes waterPalette,CdAssetBytes displayedPalette,CdAssetBytes hudSlots,boolean displayEnabled){
        Entry{Objects.requireNonNull(section);Objects.requireNonNull(program);Objects.requireNonNull(level);palette(normalPalette);palette(fadePalette);palette(waterPalette);palette(displayedPalette);extent(hudSlots,1984);}
    }
    record Publication(int selector,Phase phase,CdAssetBytes normalPalette,CdAssetBytes waterPalette,
                       boolean displayEnabled,CdR13SectionArt.State section){
        Publication{if(selector!=0xC&&selector!=0x12)throw new IllegalArgumentException("Native loading selector C/12 required");Objects.requireNonNull(phase);palette(normalPalette);palette(waterPalette);Objects.requireNonNull(section);}
    }
    record State(Phase phase,CdR13SectionArt.State section,Program program,CdLevelGlobals level,CdAssetBytes normalPalette,
                 CdAssetBytes fadePalette,CdAssetBytes waterPalette,CdAssetBytes displayedPalette,CdAssetBytes hudSlots,
                 Optional<CdAssetBytes> blocksRam,Optional<CdAssetBytes> layoutRam,int initialFadeSteps,int fromFadeSteps,
                 boolean displayEnabled,List<Call> calls){
        State{Objects.requireNonNull(phase);Objects.requireNonNull(section);Objects.requireNonNull(program);Objects.requireNonNull(level);palette(normalPalette);palette(fadePalette);palette(waterPalette);palette(displayedPalette);extent(hudSlots,1984);Objects.requireNonNull(blocksRam);Objects.requireNonNull(layoutRam);layoutRam.ifPresent(b->extent(b,2048));if(initialFadeSteps<0||initialFadeSteps>22||fromFadeSteps<0||fromFadeSteps>22)throw new IllegalArgumentException("Native fade progress0..22");calls=List.copyOf(calls);}
        private State phase(Phase p){return new State(p,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State art(CdR13SectionArt.State a){return new State(phase,a,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State program(Program p){return new State(phase,section,p,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State level(CdLevelGlobals l){return new State(phase,section,program,l,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State colours(CdAssetBytes n,CdAssetBytes f,CdAssetBytes w,CdAssetBytes d){return new State(phase,section,program,level,n,f,w,d,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State hud(CdAssetBytes h){return new State(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,h,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State geometry(Optional<CdAssetBytes> b,Optional<CdAssetBytes> l){return new State(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,b,l,initialFadeSteps,fromFadeSteps,displayEnabled,calls);}
        private State progress(int i,int f,boolean e){return new State(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,i,f,e,calls);}
        private State calls(List<Call> c){return new State(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialFadeSteps,fromFadeSteps,displayEnabled,c);}
    }
    static final class Snapshot {
        private final SonicCdAct3FutureLoadingVisit owner;private final State state;private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;private final Object host;
        private Snapshot(SonicCdAct3FutureLoadingVisit owner,State state,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,Object host){this.owner=owner;this.state=state;this.actor=actor;this.shared=shared;this.host=host;}
        State state(){return state;}
    }
    interface OrdinaryOwner {void run(int held,OrdinaryAccess access);}
    final class OrdinaryAccess {
        private OrdinaryAccess(){}
        private State preparedBefore,prepared;
        private void require(){if(this!=activeOrdinary||!running||failed||values.phase()!=Phase.READY||ordinary==null)throw new IllegalStateException("No actual R13 ordinary continuation");validateHost();}
        void requireOwner(OrdinaryOwner expected){require();if(expected!=ordinary)throw new IllegalArgumentException("Another ordinary owner");}
        State state(){require();return values;}
        void validate(State expected){require();if(expected!=values)throw new IllegalArgumentException("Stale complete Core cut");}
        /** Prepared data alone has no adoption authority; only this private live access retains its original object. */
        State prepare(State expected,CdR13SectionArt.State section,CdLevelGlobals level,CdAssetBytes hud,
                      CdAssetBytes normal,CdAssetBytes displayed){
            validate(expected);art.initSection(Objects.requireNonNull(section));Objects.requireNonNull(level);extent(hud,1984);palette(normal);palette(displayed);
            preparedBefore=expected;prepared=expected.art(section).level(level).hud(hud).colours(normal,expected.fadePalette(),expected.waterPalette(),displayed);return prepared;
        }
        void adopt(State expected,State ready){validate(expected);if(preparedBefore!=expected||prepared!=ready)throw new IllegalArgumentException("No original prepared ordinary value");values=ready;preparedBefore=null;prepared=null;}
        /** Commit the already validated original pending pair after native publication. No Host callback/read occurs here. */
        void adoptAfterNative(State expected,State ready){
            if(this!=activeOrdinary||!running||failed||values!=expected||preparedBefore!=expected||prepared!=ready)throw new IllegalStateException("Original prevalidated Core pair expired");
            values=ready;preparedBefore=null;prepared=null;
        }
        /** Only this original ordinary continuation and its completed physical World walk may transfer. */
        void beginOldExit(CdWorld expected){
            require();if(expected==null||expected!=ordinary)throw new IllegalStateException("No actual completed R13 World walk");
            expected.requireOldExitOwner(SonicCdAct3FutureLoadingVisit.this);beginRetainedOldExit(expected);
        }
        /** World must first validate the original pending Title receipt; this is the actual operation, never effect replay. */
        void appendTitlePlc(State expected){validate(expected);artOp(a->art.appendList(a,2));call(Call.TITLE_PLC_APPEND);}
        /** Actual caller first authenticates its original pending Boss PLC4 receipt. */
        void appendBossPlc(State expected){validate(expected);artOp(a->art.appendList(a,4));call(Call.BOSS_PLC_APPEND);}
        // BEGIN R13 RESULTS ORDINARY CORE JOIN 20261106
        /** Actual original Results PLC16; the World first validates its privately issued receipt. */
        void appendResultsPlc(State expected){validate(expected);artOp(a->art.appendList(a,16));call(Call.RESULTS_PLC_APPEND);}
        /** Pure complete ordinary prebuild. The live private access, not these values, retains authority. */
        State prepareResults(State expected,Program program,CdR13SectionArt.State section,CdLevelGlobals level,
                             CdAssetBytes hud,CdAssetBytes normal,CdAssetBytes displayed){
            validate(expected);Objects.requireNonNull(program);var old=expected.program();
            if(program!=old&&(program.demoModeWord()!=old.demoModeWord()||program.timeStonesByte()!=old.timeStonesByte()||program.usePlayer2Byte()!=old.usePlayer2Byte()||program.specialStageByte()!=old.specialStageByte()||program.palFadeFlagsByte()!=old.palFadeFlagsByte()||program.palClearFlagBit0()!=old.palClearFlagBit0()||program.gameModeByte()!=old.gameModeByte()||program.levelStartedByte()!=old.levelStartedByte()||program.demoDataIndexWord()!=old.demoDataIndexWord()||program.goodFutureByte()!=0&&program.goodFutureByte()!=1))throw new IllegalArgumentException("Results only writes native goodFuture0/1 in Program");
            Objects.requireNonNull(section);var a=expected.section();if(section!=a&&(section.sectionWord()!=0||section.queue()!=a.queue()||section.codeTable()!=a.codeTable()||section.residency()!=a.residency()||!section.decoder().equals(a.decoder())||!section.savedDecoder().equals(a.savedDecoder())||section.remainingTilesWord()!=a.remainingTilesWord()||section.processBudgetWord()!=a.processBudgetWord()||section.hintWord()!=a.hintWord()||section.padWord()!=a.padWord()||section.stopped()!=a.stopped()))throw new IllegalArgumentException("Results only clears actual section word");
            var ready=prepare(expected,section,level,hud,normal,displayed);prepared=ready.program(program);return prepared;
        }
        // END R13 RESULTS ORDINARY CORE JOIN 20261106
        /** Pure palette-only prebuild; the private live access retains the original prepared pair. */
        State prepareNormalPalette(State expected,CdAssetBytes normal){validate(expected);palette(normal);return prepare(expected,expected.section(),expected.level(),expected.hudSlots(),normal,expected.displayedPalette());}
        /** Bound original services retain the actual decoder/current prefix, including a thrown fault. */
        void serviceSlowPlc(State expected,int scrollLockWord){validate(expected);word(scrollLockWord);artOp(a->art.serviceSlow(a,scrollLockWord));}
        void preparePlc(State expected){validate(expected);artOp(art::prepare);}
        /** Actual shared HUD operation over this exact section/level cut, not an arbitrary result adoption. */
        CdLevelHud.Act3FutureResult runHud(State expected,CdLevelHud.Act3FutureState input,CdLevelHud.Inputs inputs){
            validate(expected);Objects.requireNonNull(input);Objects.requireNonNull(inputs);
            if(input.section()!=values.section()||input.level()!=values.level())throw new IllegalArgumentException("HUD requires actual Core pair");
            if(hud==null)hud=new CdLevelHud.Act3Future(CdR13ObjectAssets.fromAssets(assets).hudAssets(),art);
            try{var result=hud.run(input,inputs);retainHud(result.state());return result;}
            catch(CdLevelHud.UnsupportedAct3FutureHud original){try{retainHud(original.state());}catch(RuntimeException|Error publication){if(publication!=original)original.addSuppressed(publication);}throw original;}
            catch(CdLevelHud.Act3FutureArtFailure prefix){try{retainHud(prefix.state());}catch(RuntimeException|Error publication){if(publication!=prefix.original())prefix.original().addSuppressed(publication);}throw prefix;}
        }
        private void retainHud(CdLevelHud.Act3FutureState next){
            var before=values.section();values=values.art(next.section()).level(next.level());
            if(before.residency()!=next.section().residency())act(Action.CURRENT_ART);
        }
        /** Actual Main loop tail only. Native Act3 RTS is an exact no-write value operation. */
        void updateSection(State expected){validate(expected);if(art.updateSection(values.section())!=values.section())throw new IllegalStateException("Act3 section RTS wrote state");}
    }
    private final Host host;private final CdR13FutureAssets assets;private final CdPlayerFrameRuntime frame;
    private final CdR13SectionArt art;private final CdR13FutureScrollPalette scroll;private final int selectedGoodFuture;
    private CdLevelHud.Act3Future hud;
    private State values;private boolean running,failed,eventsActive,observing,checking,validating;private Action activeAction;private Publication activePublication;
    private Scroll activeScrollBefore,activeScrollReady;private OrdinaryOwner ordinary;private OrdinaryAccess activeOrdinary;
    private boolean oldBlankStore;private SonicCdAct3FutureWorldHost.Prepared oldBlankPrepared;private State oldBlankReady;

    SonicCdAct3FutureLoadingVisit(Host host,CdR13FutureAssets assets,CdPlayerFrameRuntime frame,CdR13SectionArt art,Entry entry){
        this.host=Objects.requireNonNull(host);this.assets=Objects.requireNonNull(assets);this.frame=Objects.requireNonNull(frame);this.art=Objects.requireNonNull(art);Objects.requireNonNull(entry);
        if(!frame.stageSource().ownsAct3Future(assets)||!art.ownsAssets(assets))throw new IllegalArgumentException("Exact original R13 Frame/assets/art relation required");
        selectedGoodFuture=entry.program().goodFutureByte();
        if(assets.identity().file()!=(selectedGoodFuture==0?CdNativeFile.R13D:CdNativeFile.R13C))throw new IllegalArgumentException("Actual RunBossLevel selection differs from bound MMD");
        art.initSection(entry.section()); // Pure owner validation; NOT a fabricated native InitSection invocation.
        scroll=CdR13FutureScrollPalette.bind(assets);
        values=new State(Phase.ENTRY,entry.section(),entry.program(),entry.level(),entry.normalPalette(),entry.fadePalette(),entry.waterPalette(),entry.displayedPalette(),entry.hudSlots(),Optional.empty(),Optional.empty(),0,0,entry.displayEnabled(),List.of());
    }
    State state(){return values;}CdR13FutureAssets assets(){return assets;}boolean failed(){return failed;}
    private void validateHost(){if(validating)throw new IllegalStateException("Reentrant Host validation");validating=true;try{host.validate(this,frame,assets,art);}finally{validating=false;}}
    private record Observation(State core,CdPlayerBody.State actor,CdPlayerBody.Environment environment,CdPlayerArtRuntime.State art,
                               Optional<CdPlayerBody.DisplayPublication> display,CdPlayerFrameRuntime.VIntClock clock,boolean paused,boolean closed,Object host){}
    private Observation observation(){return new Observation(values,frame.state(),frame.environment(),frame.artState(),frame.display(),frame.vIntClock(),frame.paused(),frame.closed(),Objects.requireNonNull(host.capture()));}
    private void unchanged(Observation before){
        if(values!=before.core()||frame.state()!=before.actor()||frame.environment()!=before.environment()||frame.artState()!=before.art()
                ||frame.display()!=before.display()||frame.vIntClock()!=before.clock()||frame.paused()!=before.paused()||frame.closed()!=before.closed()
                ||!Objects.equals(host.capture(),before.host()))throw new IllegalStateException("Host observation changed its complete Core/Frame/external cut");
        validateHost();
    }
    private <T>T observe(Supplier<T> read){
        if(observing)throw new IllegalStateException("Reentrant Host observation");observing=true;
        Observation before=null;
        try{before=observation();T result=read.get();unchanged(before);return result;}
        catch(RuntimeException|Error original){if(before==null)terminal(original);else try{unchanged(before);}catch(RuntimeException|Error violation){if(violation!=original)original.addSuppressed(violation);terminal(original);}throw original;}
        finally{observing=false;}
    }
    private void current(){if(running||failed||observing||checking||validating||activeAction!=null||activePublication!=null||eventsActive||activeScrollBefore!=null)throw new IllegalStateException("R13 loading active or failed");checking=true;try{validateHost();frame.captureActor();frame.captureShared();if(!frame.stageSource().ownsAct3Future(assets)||!art.ownsAssets(assets))throw new IllegalStateException("R13 source relation changed");}finally{checking=false;}}
    void requireServiceIdle(){current();}
    void requireAction(Host expected,Action action,State state){if(expected!=host||!running||failed||activeAction!=action||values!=state)throw new IllegalStateException("No original Core action");validateHost();}
    void requirePublication(Host expected,Publication selected){if(expected!=host||!running||failed||activePublication==null)throw new IllegalStateException("No original Core publication");if(selected!=activePublication)throw new IllegalArgumentException("Equal values are not the original publication");validateHost();}
    void requireScrollStore(Host expected,Scroll before,Scroll ready){if(expected!=host||!running||failed||activeScrollBefore==null)throw new IllegalStateException("No original Core scroll store");if(before!=activeScrollBefore||ready!=activeScrollReady)throw new IllegalArgumentException("Equal scroll values are not the original store");validateHost();}
    void requireEvents(Host expected,State state){if(expected!=host||!running||failed||!eventsActive||values!=state)throw new IllegalStateException("No original Core event call");validateHost();}
    private void call(Call c){var list=new ArrayList<>(values.calls());list.add(c);values=values.calls(list);}
    private void act(Action action){validateHost();if(activeAction!=null)throw new IllegalStateException("Reentrant Core action");activeAction=action;try{host.act(action,values);validateHost();}finally{activeAction=null;}call(Call.valueOf(action.name()));}
    private void stop(Boundary b){throw new UnsupportedLoading(b);}
    private void terminal(Throwable f){failed=true;try{frame.close();}catch(RuntimeException|Error close){if(close!=f)f.addSuppressed(close);}}
    private Scroll readScroll(){validateHost();return Objects.requireNonNull(observe(host::scroll));}
    private void storeScroll(Scroll before,Scroll ready){if(before==ready)return;validateHost();if(activeScrollBefore!=null)throw new IllegalStateException("Reentrant scroll store");activeScrollBefore=before;activeScrollReady=ready;try{host.storeScroll(before,ready);validateHost();if(host.scroll()!=ready)throw new IllegalStateException("Original scroll value was not synchronously adopted");}finally{activeScrollBefore=null;activeScrollReady=null;}}
    void bindOrdinaryOwner(Host expected,OrdinaryOwner owner){current();if(expected!=host||values.phase()!=Phase.ENTRY||ordinary!=null)throw new IllegalStateException("Bind actual ordinary owner once before begin");ordinary=Objects.requireNonNull(owner);}

    void begin(){
        current();if(values.phase()!=Phase.ENTRY)throw new IllegalStateException("Already began");
        validateHost();int selected=observe(host::selectedGoodFutureByte);validateHost();octet(selected);
        if(selected!=selectedGoodFuture)throw new IllegalStateException("Actual programme selection changed before entry");
        var gates=frame.environment().movement().gates();if(gates.zoneAct()!=2||(gates.timeZone()!=2&&gates.timeZone()!=0x82)||!zeroPrimary(frame.state()))stop(Boundary.ENTRY);
        running=true;
        try{
            var p=values.program();values=values.program(p.entry(p.timeStonesByte()==0x7F&&frame.environment().globals().timeAttackMode()==0));frame.beginLevelStart();act(Action.ENTRY_STORES);
            var l=values.level();boolean reset=(l.plcLoadFlags()&1)==0;values=values.level(level(l,l.levelRestart(),l.updateHudTime(),reset?0:l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|1,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
            if(reset){values=values.program(values.program().fade(0));act(Action.FRESH_RESET_PREFIX);act(Action.RESET_SAVED_OBJECT_FLAGS);l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));act(Action.FRESH_RESET_SUFFIX);}
            values=values.program(values.program().initializing());artOp(art::clearPlcs);call(Call.CLEAR_PLCS);
            boolean white=values.program().specialStageByte()!=0||(gates.timeZone()&128)!=0;
            boolean fade=white?values.program().specialStageByte()!=0||(values.program().palFadeFlagsByte()&1)!=0:(values.program().palFadeFlagsByte()&1)!=0;
            if(values.program().specialStageByte()==0)values=values.program(values.program().fade(values.program().palFadeFlagsByte()|1));
            if(fade){values=values.phase(white?Phase.INITIAL_TO_WHITE:Phase.INITIAL_TO_BLACK);act(Action.INITIAL_FADE_SETUP);}else afterInitial(white);
        }catch(RuntimeException|Error f){terminal(f);throw f;}finally{running=false;}
    }
    private void afterInitial(boolean white){
        if(white){frame.clearLevelStartWarpDirection();act(Action.PRELUDE_END);if(values.level().levelRestart()!=0)stop(Boundary.LEVEL_RESTART_RETURN);}
        else{
            if(values.level().levelRestart()==2)stop(Boundary.LEVEL_RESTART_RETURN);
            validateHost();int lives=observe(host::livesByte);validateHost();octet(lives);if(lives==0)stop(Boundary.NO_LIVES_RETURN);
        }
        if(values.program().demoDataIndexWord()==0x800){values=values.program(values.program().demoIndex(0).fade(0));stop(Boundary.DEMO_RETURN);}
        if(values.program().palClearFlagBit0()==CdProgramInitialization.KnownBit.UNKNOWN)stop(Boundary.PAL_CLEAR_KNOWLEDGE);
        boolean fillWhite=values.program().palClearFlagBit0()==CdProgramInitialization.KnownBit.SET||(frame.environment().movement().gates().timeZone()&128)!=0;
        values=values.colours(fillWhite?white():zero(128),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(Phase.FIRST_PLC);act(Action.NORMAL_LOAD_PALETTE);
    }
    // BEGIN R13 retained old-programme exit. No public State or raw destination grants this scope.
    boolean oldProgrammeSelected(){return oldPhase(values.phase());}
    private static boolean oldPhase(Phase p){return p==Phase.OLD_EXIT_PRELUDE||p==Phase.OLD_BLACK_FADE||p==Phase.OLD_CLEAR_WAIT||p==Phase.OLD_RETURNED||p==Phase.OLD_IPX_RETURNED||p==Phase.OLD_IPX_BOSS_RETURNED;}
    int oldExitSteps(){return Collections.frequency(values.calls(),Call.OLD_EXIT_BLACK_COLOURS);}
    private void requireRetainedExitGates(){
        var g=frame.environment().movement().gates();
        if(!frame.stageSource().ownsAct3Future(assets)||g.zoneAct()!=0x100||g.timeZone()!=1)stop(Boundary.OLD_EXIT_ENTRY);
    }
    private void beginRetainedOldExit(CdWorld actual){
        // Main405-408 reaches LevelStart only AFTER the actual increasing RunObjects returned.
        requireRetainedExitGates();if(values.level().levelRestart()!=2||values.program().demoDataIndexWord()==0x800)stop(Boundary.OLD_EXIT_ENTRY);
        actual.abandonOldExitWalk(this);values=values.phase(Phase.OLD_EXIT_PRELUDE);call(Call.OLD_EXIT_BEGIN);
        var p=values.program();values=values.program(p.entry(p.timeStonesByte()==0x7F&&frame.environment().globals().timeAttackMode()==0));frame.beginLevelStart();act(Action.ENTRY_STORES);
        var l=values.level();boolean reset=(l.plcLoadFlags()&1)==0;values=values.level(level(l,l.levelRestart(),l.updateHudTime(),reset?0:l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|1,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
        if(reset){values=values.program(values.program().fade(0));act(Action.FRESH_RESET_PREFIX);act(Action.RESET_SAVED_OBJECT_FLAGS);l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));act(Action.FRESH_RESET_SUFFIX);}
        values=values.program(values.program().initializing());artOp(art::clearPlcs);call(Call.CLEAR_PLCS);
        // A special-stage white return is a different branch, never inferred from the Results black path.
        if(values.program().specialStageByte()!=0)stop(Boundary.OLD_EXIT_WHITE_RETURN);
        boolean fade=(values.program().palFadeFlagsByte()&1)!=0;values=values.program(values.program().fade(values.program().palFadeFlagsByte()|1));
        if(fade){values=values.phase(Phase.OLD_BLACK_FADE);act(Action.OLD_EXIT_FADE_SETUP);}else clearRetainedExitPalette();
    }
    private void clearRetainedExitPalette(){
        var l=values.level();if(l.levelRestart()!=2)stop(Boundary.OLD_EXIT_ENTRY);
        values=values.level(level(l,0,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver())).program(values.program().fade(0));
        values=values.colours(zero(128),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(Phase.OLD_CLEAR_WAIT);call(Call.OLD_EXIT_CLEAR);
    }
    void requireOldExitBlankPreparation(Host expected){
        if(expected!=host||!running||failed||!oldBlankStore||oldBlankPrepared!=null||oldBlankReady!=null||values.phase()!=Phase.OLD_RETURNED)throw new IllegalStateException("No original IPX blank preparation");validateHost();
    }
    void requireOldExitBlankCandidate(Host expected,SonicCdAct3FutureWorldHost.Prepared candidate){
        if(expected!=host||!running||failed||!oldBlankStore||oldBlankPrepared!=candidate||oldBlankReady==null||values.phase()!=Phase.OLD_RETURNED)throw new IllegalStateException("Original IPX blank candidate expired");
    }
    void requireOldExitBlankCommit(Host expected,SonicCdAct3FutureWorldHost.Prepared candidate){
        requireOldExitBlankCandidate(expected,candidate);if(values!=oldBlankReady)throw new IllegalStateException("No original prevalidated IPX pair");
    }
    private void disableRetainedIpxDisplay(){
        if(!(host instanceof SonicCdAct3FutureWorldHost actual))throw new IllegalStateException("No actual retained R13 Host");
        oldBlankStore=true;
        try{oldBlankPrepared=actual.prepareOldExitBlank();oldBlankReady=values.progress(values.initialFadeSteps(),values.fromFadeSteps(),false);actual.validateOldExitBlank(oldBlankPrepared);
            values=oldBlankReady;actual.adoptOldExitBlankAfterNative(oldBlankPrepared);call(Call.OLD_EXIT_BLANK);
        }finally{oldBlankPrepared=null;oldBlankReady=null;oldBlankStore=false;}
    }
    private void serviceRetainedOldExit(int held){
        requireRetainedExitGates();
        if(values.phase()==Phase.OLD_RETURNED){
            // IPX RunMMD.StopMMD: original StopFM, display disable, then ONE routine-zero blank.
            // It does not ReadControllers/Finish/count and never invokes a destination Frame.
            disableRetainedIpxDisplay();
            act(Action.VINT_COMPLETED);values=values.phase(Phase.OLD_IPX_RETURNED);call(Call.OLD_EXIT_IPX_RETURN);return;
        }
        boolean fade=values.phase()==Phase.OLD_BLACK_FADE;
        if(fade)frame.serviceAct3FutureOldProgrammeExitPaletteFade(held,assets,frame.state(),frame.environment(),()->{
            publish(0x12);if(values.level().fadeEnableDisplay()==1){var l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),2,l.powerup(),l.timeOver())).progress(values.initialFadeSteps(),values.fromFadeSteps(),true);act(Action.ENABLE_DISPLAY);}
        },()->artOp(art::serviceFast));
        else frame.serviceAct3FutureOldProgrammeExitLoad(held,assets,frame.state(),frame.environment(),()->publish(0xC),this::copyAndDraw,()->artOp(art::serviceFast));
        act(Action.VINT_COMPLETED);call(fade?Call.OLD_EXIT_BLACK_SERVICE:Call.OLD_EXIT_CLEAR_SERVICE);
        if(fade){var p=CdLevelPaletteFade.stepToBlack(new CdLevelPaletteFade.Palettes(values.normalPalette(),values.waterPalette()));values=values.colours(p.palette(),values.fadePalette(),p.waterPalette(),values.displayedPalette());call(Call.OLD_EXIT_BLACK_COLOURS);artOp(art::prepare);call(Call.PROCESS_PLCS);if(oldExitSteps()==22)clearRetainedExitPalette();}
        else{values=values.phase(Phase.OLD_RETURNED);call(Call.OLD_EXIT_RTS);}
        act(Action.OLD_EXIT_SETTLE_CURRENT);
    }
    // END R13 retained old-programme exit.

    // BEGIN IPX BOSS RETURN OWNER 20261107
    /** Only the actual service stack creates this call; its complete observed cut grants no public authority. */
    static final class IpxBossReturnCall {
        private final Observation before;private final int livesByte;
        private IpxBossReturnCall(Observation before,int livesByte){this.before=before;this.livesByte=livesByte;}
        State state(){return before.core();}int livesByte(){return livesByte;}Object hostSnapshot(){return before.host();}
    }
    private IpxBossReturnCall ipxBossCall;
    private SonicCdAct3FutureWorldHost.IpxBossReturn ipxBossPrepared;
    private boolean ipxBossCommitting,runR1UnlockRead;
    private void requireIpxBossBase(Host expected,IpxBossReturnCall call){
        if(expected!=host||!running||failed||call==null||call!=ipxBossCall||ipxBossCommitting
                ||values!=call.before.core()||values.phase()!=Phase.OLD_IPX_RETURNED)
            throw new IllegalStateException("No original live IPX boss-return call");
        unchanged(call.before); // actual full Frame pair/art/clock/display + Host/pool96/bank/status
    }
    void requireIpxBossReturnPreparation(Host expected,IpxBossReturnCall call){
        requireIpxBossBase(expected,call);if(ipxBossPrepared!=null)throw new IllegalStateException("Original IPX preparation already returned");
    }
    void requireIpxBossReturnCandidate(Host expected,SonicCdAct3FutureWorldHost.IpxBossReturn candidate){
        requireIpxBossBase(expected,ipxBossCall);if(candidate==null||candidate!=ipxBossPrepared)throw new IllegalStateException("Not the original complete IPX candidate");
    }
    void requireIpxProgressCandidate(Host expected,IpxBossReturnCall call,SonicCdAct3FutureWorldHost.IpxProgressStore store){
        requireIpxBossBase(expected,call);if(ipxBossPrepared==null||!ipxBossPrepared.ownsProgress(store))throw new IllegalStateException("Not the original Module IPX pair");
    }
    void requireIpxProgressCommit(Host expected,IpxBossReturnCall call,SonicCdAct3FutureWorldHost.IpxProgressStore store){
        if(expected!=host||!running||failed||!ipxBossCommitting||call==null||call!=ipxBossCall
                ||values!=call.before.core()||ipxBossPrepared==null||!ipxBossPrepared.ownsProgress(store))
            throw new IllegalStateException("Original prevalidated Module IPX pair expired");
    }
    void requireIpxBossReturnCommit(Host expected,SonicCdAct3FutureWorldHost.IpxBossReturn candidate){
        if(expected!=host||!running||failed||!ipxBossCommitting||ipxBossCall==null
                ||values!=ipxBossCall.before.core()||candidate==null||candidate!=ipxBossPrepared)
            throw new IllegalStateException("Original prevalidated Host IPX pair expired");
    }
    void requireRunR1UnlockRead(Host expected){
        if(expected!=host||!running||failed||!runR1UnlockRead||values.phase()!=Phase.OLD_IPX_BOSS_RETURNED)
            throw new IllegalStateException("No original returned RunR1 unlock read");validateHost();
    }
    private void serviceIpxBossReturn(){
        int lives=-1;
        if(values.phase()==Phase.OLD_IPX_RETURNED){lives=observe(host::livesByte);octet(lives);if(lives==0)stop(Boundary.IPX_BOSS_GAME_OVER);}
        if(!(host instanceof SonicCdAct3FutureWorldHost))stop(Boundary.IPX_BOSS_SAVED_ZONE_OWNER);
        var actual=(SonicCdAct3FutureWorldHost)host;
        if(values.phase()==Phase.OLD_IPX_RETURNED){
            Boundary missing=null;running=true;
            try{
                ipxBossCall=new IpxBossReturnCall(observation(),lives);
                var prepared=actual.prepareIpxBossReturn(ipxBossCall);
                if(prepared.isEmpty())missing=Boundary.IPX_BOSS_SAVED_ZONE_OWNER;
                else{
                    ipxBossPrepared=prepared.orElseThrow();var nativeResult=ipxBossPrepared.result();
                    if(nativeResult.missingRead().isPresent()){
                        if(nativeResult.missingRead().orElseThrow()!=CdIpxProgress.Read.SAVED_LEVEL_ZONE)
                            throw new IllegalStateException("Unexpected IPX first-read result");
                        missing=Boundary.IPX_BOSS_SAVED_ZONE_OWNER;
                    }else{
                        var stores=nativeResult.writes();
                        if(nativeResult.cut()!=CdIpxProgress.Cut.RETURN_TO_BOSS_CALLER
                                ||!stores.checkpointByte().equals(OptionalInt.of(0))||stores.plcLoadFlagsByte().isPresent()
                                ||stores.zoneActWord().isPresent()||stores.projectorByte().isPresent()
                                ||stores.goodFutureByte().isPresent()||stores.amyCapturedByte().isPresent())
                            throw new IllegalStateException("Not the original bounded boss return");
                        var l=values.level();var calls=new ArrayList<>(values.calls());calls.add(Call.IPX_BOSS_RETURN);
                        var ready=values.level(level(l,l.levelRestart(),l.updateHudTime(),0,l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()))
                                .phase(Phase.OLD_IPX_BOSS_RETURNED).calls(calls);
                        actual.validateIpxBossReturn(ipxBossPrepared); // every fallible aggregate/owner preflight precedes native stores
                        ipxBossCommitting=true;
                        actual.adoptIpxBossReturnAfterNative(ipxBossPrepared); // saved high first; Host is a strict nonwrite
                        values=ready; // actual checkpoint BYTE0, then native RTS/caller phase
                    }
                }
            }catch(RuntimeException|Error original){terminal(original);throw original;}
            finally{ipxBossCommitting=false;ipxBossPrepared=null;ipxBossCall=null;running=false;}
            if(missing!=null)stop(missing); // actual missing-owner stops keep healthy retained owners open
        }
        OptionalInt unlock;running=true;runR1UnlockRead=true;
        try{unlock=Objects.requireNonNull(observe(actual::runR1UnlockByte));unlock.ifPresent(SonicCdAct3FutureLoadingVisit::octet);}
        catch(RuntimeException|Error original){terminal(original);throw original;}
        finally{runR1UnlockRead=false;running=false;}
        // Current fresh/unknown retained lineages have no historical unlock producer. No title/save default.
        stop(unlock.isEmpty()?Boundary.IPX_RUN_R1_TIME_ATTACK_UNLOCK_OWNER:Boundary.IPX_RUN_R1_UNLOCK_CONTINUATION_OWNER);
    }
    // END IPX BOSS RETURN OWNER 20261107

    void service(int held){
        current();octet(held);if(values.phase()==Phase.ENTRY)throw new IllegalStateException("Begin before service");if(values.phase()==Phase.READY&&ordinary==null)stop(Boundary.ORDINARY_OWNER);
        if(values.phase()==Phase.OLD_IPX_RETURNED||values.phase()==Phase.OLD_IPX_BOSS_RETURNED){serviceIpxBossReturn();return;}
        running=true;try{switch(values.phase()){
            case INITIAL_TO_WHITE,INITIAL_TO_BLACK -> fadeService(held,true);
            case FROM_WHITE,FROM_BLACK -> fadeService(held,false);
            case FIRST_PLC,LEVEL_PLC -> plcService(held);
            case OLD_BLACK_FADE,OLD_CLEAR_WAIT,OLD_RETURNED -> serviceRetainedOldExit(held);
            case READY -> {var access=new OrdinaryAccess();activeOrdinary=access;try{ordinary.run(held,access);}finally{activeOrdinary=null;}}
            default -> throw new IllegalStateException("Unserviceable loading phase");
        }}catch(RuntimeException|Error f){terminal(f);throw f;}finally{running=false;}
    }
    private void publish(int selector){
        validateHost();var p=new Publication(selector,values.phase(),values.normalPalette(),values.waterPalette(),values.displayEnabled(),values.section());if(activePublication!=null)throw new IllegalStateException("Reentrant publication");activePublication=p;try{host.publish(p);validateHost();}finally{activePublication=null;}
        values=values.colours(values.normalPalette(),values.fadePalette(),values.waterPalette(),p.normalPalette());
    }
    private void plcService(int held){
        var phase=values.phase();frame.serviceLevelLoad(held,()->publish(0xC),this::copyAndDraw,()->artOp(art::serviceFast));act(Action.VINT_COMPLETED);call(phase==Phase.FIRST_PLC?Call.FIRST_WAIT:Call.LEVEL_WAIT);artOp(art::prepare);call(Call.PROCESS_PLCS);
        if(values.section().queue().headSource()==0&&values.section().remainingTilesWord()==0){if(phase==Phase.FIRST_PLC)mainLoad();else finishLoad();}
    }
    private void fadeService(int held,boolean initial){
        var phase=values.phase();frame.serviceLevelPaletteFade(held,()->{publish(0x12);if(!initial&&values.level().fadeEnableDisplay()==1){var l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),2,l.powerup(),l.timeOver())).progress(values.initialFadeSteps(),values.fromFadeSteps(),true);act(Action.ENABLE_DISPLAY);}},()->artOp(art::serviceFast));
        act(Action.VINT_COMPLETED);call(initial?Call.INITIAL_FADE_SERVICE:Call.FROM_FADE_SERVICE);
        if(initial){var pair=new CdLevelPaletteFade.Palettes(values.normalPalette(),values.waterPalette());var out=phase==Phase.INITIAL_TO_WHITE?CdLevelPaletteFade.stepToWhite(pair):CdLevelPaletteFade.stepToBlack(pair);values=values.colours(out.palette(),values.fadePalette(),out.waterPalette(),values.displayedPalette());call(Call.INITIAL_FADE_COLOURS);}
        else{var normal=phase==Phase.FROM_WHITE?CdLevelPaletteFade.stepFromWhite(values.normalPalette(),values.fadePalette()):CdLevelPaletteFade.stepFromBlack(values.normalPalette(),values.fadePalette(),0x202F);values=values.colours(normal,values.fadePalette(),values.waterPalette(),values.displayedPalette());call(Call.FROM_FADE_COLOURS);}
        artOp(art::prepare);call(Call.PROCESS_PLCS);values=values.progress(values.initialFadeSteps()+(initial?1:0),values.fromFadeSteps()+(initial?0:1),values.displayEnabled());
        if(initial&&values.initialFadeSteps()==22){if(phase==Phase.INITIAL_TO_WHITE)values=values.program(values.program().fade(values.program().palFadeFlagsByte()&~1));afterInitial(phase==Phase.INITIAL_TO_WHITE);}
        else if(!initial&&values.fromFadeSteps()==22){values=values.program(values.program().started()).phase(Phase.READY);act(Action.BEGIN_LEVEL);}
    }
    private void artOp(UnaryOperator<CdR13SectionArt.State> operation){
        var before=values.section();try{values=values.art(operation.apply(before));}catch(CdR13SectionArt.SectionArtException f){values=values.art(f.state());f.completedRamPrefix().ifPresent(b->values=values.geometry(Optional.of(b),values.layoutRam()));try{act(Action.CURRENT_ART);}catch(RuntimeException|Error publication){if(publication!=f)f.addSuppressed(publication);}throw f;}
        if(values.section().residency()!=before.residency())act(Action.CURRENT_ART);
    }
    private void mainLoad(){
        act(Action.PLAY_LEVEL_MUSIC);int id=assets.view(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX).unsignedByte(0);if(id!=0){artOp(a->art.loadImmediateList(a,id));call(Call.IMMEDIATE_LEVEL);}artOp(a->art.loadImmediateList(a,1));call(Call.IMMEDIATE_STANDARD);
        var l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),0,0,0,l.timeOver()));act(Action.CLEAR_POWERUP_FLOWERS);act(Action.CLEAR_DRAW_QUEUE);
        frame.clearLevelStartFlowersPlayerValues();clearFlowersScroll();act(Action.CLEAR_FLOWERS);if(!zeroPrimary(frame.state()))stop(Boundary.ENTRY);values=values.hud(zero(1984));act(Action.CLEAR_OBJECTS);act(Action.CLEAR_UNKNOWN_BUFFER);act(Action.CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues();clearCameraScroll();act(Action.CLEAR_CAMERA);values=values.program(values.program().demoIndex(0));act(Action.CLEAR_DEMO_INDEX);
        byte[] water=values.waterPalette().copy();Arrays.fill(water,0,4,(byte)0);values=values.colours(values.normalPalette(),values.fadePalette(),CdAssetBytes.copyOf(water),values.displayedPalette());clearScreenScroll();act(Action.CLEAR_SCREEN);act(Action.DROWN_TIMER);
        paletteCopy(assets.view(CdR13FutureAssets.Asset.PAL_SONIC),0,true);paletteCopy(assets.view(CdR13FutureAssets.Asset.PAL_SONIC),0,false);call(Call.PALETTE_SONIC);
        var b=assets.bounds();frame.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),frame.environment().movement().bounds().bossFight()),b.cameraCentreY());initializeSizeScroll();act(Action.LEVEL_SIZE);
        if(values.level().spawnMode()!=0)stop(Boundary.CHECKPOINT_LOAD);
        var start=assets.initialCentre();frame.initializeLevelSizeCentrePlayerValues(start.centreX(),start.centreY());setupScroll();act(Action.SETUP_CAMERA_AND_SCROLL);levelScroll();act(Action.LEVEL_SCROLL);forceLeftColumn();act(Action.FORCE_LEFT_COLUMN);
        try{var blocks=art.decodeBlocksToRam(values.section());values=values.art(blocks.state()).geometry(Optional.of(blocks.blocks()),values.layoutRam());}catch(CdR13SectionArt.SectionArtException f){values=values.art(f.state());f.completedRamPrefix().ifPresent(bytes->values=values.geometry(Optional.of(bytes),values.layoutRam()));throw f;}call(Call.BLOCKS_RAM);
        // Existing semantic Layout product is exactly native clear2048 + bounded FG/BG rows; no parallel decoder.
        values=values.geometry(values.blocksRam(),Optional.of(assets.terrain().layout().ram()));act(Action.LEVEL_LAYOUT);paletteCopy(assets.view(CdR13FutureAssets.Asset.PAL_LEVEL),32,false);call(Call.PALETTE_LEVEL);
        if((values.level().plcLoadFlags()&2)!=0){int plc=assets.levelIndex().blocks().plcId();if(plc!=0){artOp(a->art.appendList(a,plc));call(Call.QUEUED_LEVEL);}}
        // Act3 LoadLevelData has NO InitSection/LoadSection call; do not transplant R12B submission.
        act(Action.INITIAL_FOREGROUND);act(Action.INITIAL_BACKGROUND);act(Action.COLLISION_POINTER);values=values.phase(Phase.LEVEL_PLC);
    }
    private void finishLoad(){
        if(values.level().spawnMode()==0)frame.applyLevelStartNormalPlayer();else frame.applyLevelStartWarpPlayer();call(Call.LOAD_PLAYER);
        byte[] h=values.hudSlots().copy();for(int whole:new int[]{2,3,5})h[(whole-1)*64]=0x1C;h[2*64+0x28]=1;h[4*64+0x29]=1;values=values.hud(CdAssetBytes.copyOf(h));act(Action.RESERVED_HUD);
        artOp(a->art.loadLifeIcon(a,frame.environment().movement().gates().timeZone()));call(Call.LIFE_ICON);h=values.hudSlots().copy();h[30*64]=0x19;h[30*64+0x28]=10;values=values.hud(CdAssetBytes.copyOf(h));act(Action.RESERVED_TIME_ICON);
        var l=values.level();boolean title=(l.plcLoadFlags()&2)==0;values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags()|2,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
        if(title){h=values.hudSlots().copy();h[3*64]=0x3C;values=values.hud(CdAssetBytes.copyOf(h));act(Action.TITLE_CARD_CREATE);if(frame.environment().controls().controlLocked()!=1)throw new IllegalStateException("Actual Title setup control lock was not stored");var a=values.section();values=values.art(art.fromValues(a.codeTable(),a.residency(),a.sectionWord()&255,a.queue(),a.decoder(),a.savedDecoder(),a.remainingTilesWord(),a.processBudgetWord(),a.hintWord(),a.padWord()));}
        frame.clearLevelStartControlsAndBoredTimer();frame.clearLevelStartPlayerGlobals();l=values.level();values=values.level(level(l,0,1,l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),0));act(Action.BEGIN_MAIN);
        var gate=CdPowerupArtRuntime.gate(values.level().powerup());if(gate.copyRequired())stop(Boundary.ACTIVE_POWERUP);act(Action.UPDATE_ANIMATED_TILES);
        l=values.level();values=values.level(level(l,l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),1,l.powerup(),l.timeOver()));boolean marked=frame.clearLevelStartTimeZoneMarker();
        if(frame.environment().movement().gates().timeZone()!=2)stop(Boundary.ENTRY);boolean white=marked;
        if(!marked){var bit=values.program().palClearFlagBit0();if(bit==CdProgramInitialization.KnownBit.UNKNOWN)stop(Boundary.PAL_CLEAR_KNOWLEDGE);white=bit==CdProgramInitialization.KnownBit.SET;values=values.program(values.program().palClear(CdProgramInitialization.KnownBit.CLEAR));}
        act(Action.ENABLE_FADE_AND_CLEAR_MARKER);if(white)act(Action.FROM_WHITE_INFO);byte[] normal=values.normalPalette().copy();if(white)normal=white().copy();else Arrays.fill(normal,32,128,(byte)0);values=values.colours(CdAssetBytes.copyOf(normal),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(white?Phase.FROM_WHITE:Phase.FROM_BLACK);
    }
    private void paletteCopy(CdAssetBytes source,int offset,boolean normal){byte[] bytes=(normal?values.normalPalette():values.fadePalette()).copy();System.arraycopy(source.copy(),0,bytes,offset,source.length());values=values.colours(normal?CdAssetBytes.copyOf(bytes):values.normalPalette(),normal?values.fadePalette():CdAssetBytes.copyOf(bytes),values.waterPalette(),values.displayedPalette());}
    private void clearFlowersScroll(){var s=readScroll();var b=s.background();var c=new CdR11aBackgroundPlane.Camera(0,0);var copies=new Copies(0,0,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),0,new CdR11aBackgroundPlane.FlagWords(0,0,0));storeScroll(s,new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),zero(512),b.deformationRam(),b.hscrollRam(),b.vscroll()),copies,s.foregroundXFractionWord(),s.topBoundWord()));}
    private void clearCameraScroll(){var s=readScroll();var b=s.background();storeScroll(s,new Scroll(new CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0),bg(b,0,0,0,0,0,0,zero(8),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll()),s.copies(),0,0));}
    private void clearScreenScroll(){var s=readScroll();var b=s.background();storeScroll(s,new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),zero(1024),0),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));}
    private void initializeSizeScroll(){var s=readScroll();var f=s.foreground();var b=assets.bounds();storeScroll(s,new Scroll(new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),16,16,f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),b.bottom(),b.left(),f.bottomBoundShift(),f.scrollLock()),s.background(),s.copies(),s.foregroundXFractionWord(),b.top()));}
    private void setupScroll(){var s=readScroll();var m=frame.state().motion();var f=CdR11aForegroundScroll.checkpointSetupCamera(s.foreground(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,frame.environment().movement().bounds());storeScroll(s,new Scroll(f,scroll.initializeBackground(s.background(),f),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));}
    private void levelScroll(){
        var s=readScroll();var f=s.foreground();if((f.scrollLock()&0xFF00)!=0)return;
        var b=s.background();var cleared=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),0,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());storeScroll(s,new Scroll(cleared,bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll()),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));
        eventsActive=true;try{host.events(values);validateHost();}finally{eventsActive=false;}
        s=readScroll();var m=frame.state().motion();var env=frame.environment();var movement=env.movement();var input=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),movement.bounds(),s.topBoundWord(),env.globals().cameraXCentre(),movement.globals().cameraYCentre(),frame.stageSource().variant(),values.level().updateHudTime(),320,224);
        var result=scroll.foregroundAfterEvents(s.foreground(),input);f=result.state();frame.applyScrollFeedback(result.bounds(),result.cameraYCentre());storeScroll(s,new Scroll(f,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));
        s=readScroll();var next=scroll.background(s.background(),new CdR13FutureScrollPalette.BackgroundInput(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8));storeScroll(s,new Scroll(f,next,s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));
    }
    private void forceLeftColumn(){var s=readScroll();var f=s.foreground();var forced=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags()|0x400,f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock());storeScroll(s,new Scroll(forced,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord()));}
    private void copyAndDraw(){var s=readScroll();var f=s.foreground();var b=s.background();var cameras=new CdR11aBackgroundPlane.CopiedCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(),b.cameraBgY()),new CdR11aBackgroundPlane.Camera(b.cameraBg2X(),b.cameraBg2Y()),new CdR11aBackgroundPlane.Camera(b.cameraBg3X(),b.cameraBg3Y()));var copies=new Copies((f.cameraX()<<16)|s.foregroundXFractionWord(),f.cameraY16_16(),cameras,f.scrollFlags(),new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3()));storeScroll(s,new Scroll(f,b,copies,s.foregroundXFractionWord(),s.topBoundWord()));act(Action.COPY_AND_DRAW);}
    private static CdR11aBackgroundScroll.State bg(CdR11aBackgroundScroll.State before,int x,int y,int x2,int y2,int x3,int y3,CdAssetBytes crossed,int flags,int flags2,int flags3,CdAssetBytes cloud,CdAssetBytes deform,CdAssetBytes hscroll,int vscroll){return new CdR11aBackgroundScroll.State(x,y,x2,y2,x3,y3,crossed,flags,flags2,flags3,cloud,deform,hscroll,vscroll);}

    Snapshot capture(){current();var external=Objects.requireNonNull(observe(host::capture));validateHost();return new Snapshot(this,values,frame.captureActor(),frame.captureShared(),external);}
    void validateRestore(Snapshot snapshot){current();Objects.requireNonNull(snapshot);if(snapshot.owner!=this)throw new IllegalArgumentException("Another R13 visit capture");frame.validateActorRestore(snapshot.actor);frame.validateSharedRestore(snapshot.shared);observe(()->{host.validateRestore(snapshot.host);return Boolean.TRUE;});validateHost();art.initSection(snapshot.state.section());var g=snapshot.shared.environment().movement().gates();var s=snapshot.state;
        if(oldPhase(s.phase())){
            int returned=Collections.frequency(s.calls(),Call.IPX_BOSS_RETURN);
            if(returned!=(s.phase()==Phase.OLD_IPX_BOSS_RETURNED?1:0)||(s.phase()==Phase.OLD_IPX_BOSS_RETURNED&&s.level().checkpoint()!=0))throw new IllegalArgumentException("Captured native boss caller stack/checkpoint differs");
            int steps=Collections.frequency(s.calls(),Call.OLD_EXIT_BLACK_COLOURS);
            if(g.zoneAct()!=0x100||g.timeZone()!=1||s.section().stopped()||s.phase()==Phase.OLD_EXIT_PRELUDE||s.fromFadeSteps()!=22||steps<0||steps>22||s.program().demoModeWord()!=0||s.program().usePlayer2Byte()!=0||s.program().levelStartedByte()!=0||(s.program().gameModeByte()&128)==0||(s.level().plcLoadFlags()&1)==0||Collections.frequency(s.calls(),Call.OLD_EXIT_BEGIN)!=1
                    ||(s.phase()==Phase.OLD_BLACK_FADE?(steps>=22||s.level().levelRestart()!=2||(s.program().palFadeFlagsByte()&1)==0):(s.level().levelRestart()!=0||s.program().palFadeFlagsByte()!=0))
                    ||((s.phase()==Phase.OLD_IPX_RETURNED||s.phase()==Phase.OLD_IPX_BOSS_RETURNED)&&s.displayEnabled()))throw new IllegalArgumentException("Captured retained R13 exit source/phase/progress differs");return;
        }
        if(g.zoneAct()!=2||(g.timeZone()!=2&&g.timeZone()!=0x82)||s.section().stopped()||(s.phase()==Phase.READY?s.fromFadeSteps()!=22:s.fromFadeSteps()==22)||(s.phase()==Phase.INITIAL_TO_BLACK||s.phase()==Phase.INITIAL_TO_WHITE)&&s.initialFadeSteps()>=22)throw new IllegalArgumentException("Captured R13 source/phase/progress differs");}
    void restore(Snapshot snapshot){validateRestore(snapshot);running=true;try{host.restore(snapshot.host);validateHost();frame.restoreActor(snapshot.actor);frame.restoreShared(snapshot.shared);values=snapshot.state;}catch(RuntimeException|Error f){terminal(f);throw f;}finally{running=false;}}
    private static CdLevelGlobals level(CdLevelGlobals l,int restart,int update,int checkpoint,int spawn,int flags,int flowers,int fade,int power,int over){return new CdLevelGlobals(restart,update,checkpoint,spawn,flags,flowers,fade,power,over);}
    private static boolean zeroPrimary(CdPlayerBody.State state){var m=state.motion();var h=state.header();var a=state.animation();var t=state.timers();return m.equals(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0))&&h.slotId()==0&&h.mappingAddress()==0&&h.tile()==0&&h.priority()==0&&h.width()==0&&a.scriptIndex()==0&&a.timer()==0&&a.mapFrame()==0&&t.hurt()==0&&t.invincibility()==0&&t.speedShoes()==0&&t.reset()==0&&state.rotationDistance()==0&&state.rotationAngle()==0&&state.objectLinks().equals(new CdPlayerBody.ObjectLinks(0,0));}
    private static void word(int v){if((v&~0xFFFF)!=0)throw new IllegalArgumentException("Native WORD required");}private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void extent(CdAssetBytes b,int n){if(Objects.requireNonNull(b).length()!=n)throw new IllegalArgumentException("Complete explicit native value required");}private static void palette(CdAssetBytes b){extent(b,128);}
    private static CdAssetBytes zero(int n){return CdAssetBytes.copyOf(new byte[n]);}private static CdAssetBytes white(){byte[] b=new byte[128];for(int i=0;i<128;i+=2){b[i]=14;b[i+1]=(byte)238;}return CdAssetBytes.copyOf(b);}
}
