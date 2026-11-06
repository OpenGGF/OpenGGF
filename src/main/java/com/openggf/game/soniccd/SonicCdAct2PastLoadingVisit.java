package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;

/**
 * Loading-only actual original-USA R12B continuation. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Main.asm:19-329; Scroll (Act 2 Past).asm:11-137; Load Saved Data.asm:7-45;
 * Palmtree Panic/Load Level Data.asm:11-98; Palette Fade.asm:178-201,273-309.
 * Explicit entry values are actual caller projections, never fresh-game defaults or route inputs.
 * Host is the existing World's private loading projection, not a second Frame or invocation owner.
 */
final class SonicCdAct2PastLoadingVisit {
    enum Phase { ENTRY, INITIAL_WHITE, FIRST_PLC, SECTION_PLC, FROM_WHITE, BEGIN_LEVEL }
    enum Boundary { ENTRY, FRESH_RESET, TITLE_CARD, DEMO_RETURN, LEVEL_RESTART, POWERUP_ART, ORDINARY_OBJECT_PASS }
    enum Action {
        ENTRY_STORES, WHITE_PRELUDE_END, NORMAL_LOAD_PALETTE, PLAY_LEVEL_MUSIC,
        CLEAR_POWERUP_FLOWERS, CLEAR_DRAW_QUEUE, CLEAR_FLOWERS, CLEAR_OBJECTS, CLEAR_UNKNOWN_BUFFER,
        CLEAR_MISC, CLEAR_CAMERA, CLEAR_DEMO_INDEX, CLEAR_SCREEN, DROWN_TIMER, LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL, LEVEL_SCROLL, LEVEL_LAYOUT, INITIAL_FOREGROUND, INITIAL_BACKGROUND,
        COLLISION_POINTER, RESERVED_HUD, BEGIN_MAIN, UPDATE_ANIMATED_TILES, ENABLE_FADE_AND_CLEAR_MARKER,
        BEGIN_LEVEL, ENABLE_DISPLAY, COPY_AND_DRAW, CURRENT_ART, VINT_COMPLETED
    }
    enum Call {
        ENTRY_STORES, WHITE_PRELUDE_END, NORMAL_LOAD_PALETTE, PLAY_LEVEL_MUSIC,
        CLEAR_POWERUP_FLOWERS, CLEAR_DRAW_QUEUE, CLEAR_FLOWERS, CLEAR_OBJECTS, CLEAR_UNKNOWN_BUFFER,
        CLEAR_MISC, CLEAR_CAMERA, CLEAR_DEMO_INDEX, CLEAR_SCREEN, DROWN_TIMER, LEVEL_SIZE,
        SETUP_CAMERA_AND_SCROLL, LEVEL_SCROLL, LEVEL_LAYOUT, INITIAL_FOREGROUND, INITIAL_BACKGROUND,
        COLLISION_POINTER, RESERVED_HUD, BEGIN_MAIN, UPDATE_ANIMATED_TILES, ENABLE_FADE_AND_CLEAR_MARKER,
        BEGIN_LEVEL, ENABLE_DISPLAY, COPY_AND_DRAW, CURRENT_ART, VINT_COMPLETED,
        CLEAR_PLCS, INITIAL_WHITE_SERVICE, INITIAL_WHITE_COLOURS, FIRST_WAIT, SECTION_WAIT, PROCESS_PLCS,
        IMMEDIATE_LEVEL, IMMEDIATE_STANDARD, PALETTE_SONIC, LOAD_SAVED, SAVED_STORE, BLOCKS_RAM,
        PALETTE_LEVEL, SECTION_ART, LOAD_PLAYER, LIFE_ICON, RESERVED_TIME_ICON, FROM_WHITE_SERVICE, FROM_WHITE_COLOURS
    }
    static final class UnsupportedLoading extends UnsupportedOperationException {
        private final Boundary boundary;
        private UnsupportedLoading(Boundary boundary) { super("R12B loading " + boundary); this.boundary = boundary; }
        Boundary boundary() { return boundary; }
    }

    /** Synchronous actual-World seams. validate must work inside an active Frame service without capture/reentry. */
    interface Host {
        void validate(SonicCdAct2PastLoadingVisit visit, CdPlayerFrameRuntime frame,
                      CdR12bTerrainAssets terrain, CdR12bPlayerAssets player);
        /** Complete immutable image with stable semantic equality across no-write observations. */
        Object capture();
        void validateRestore(Object snapshot);
        void restore(Object snapshot);
        void act(Action action, State state);
        void publish(Publication selected);
        int cameraXWord();
        OptionalLong readLoadTarget(CdTimeWarpLoadData.StoreTarget target);
        void storeLoadTarget(CdTimeWarpLoadData.StoreCall call);
    }

    /** Sole programme value; goodFuture differs from goodFutureFlags. Readonly pal-clear bit knowledge remains ProgramServices-owned. */
    record Program(int demoModeWord, int timeStonesByte, int goodFutureByte, int usePlayer2Byte,
                   int specialStageByte, int palFadeFlagsByte, CdProgramInitialization.KnownBit palClearFlagBit0, int gameModeByte,
                   int levelStartedByte, int demoDataIndexWord) {
        Program {
            word(demoModeWord); word(demoDataIndexWord); Objects.requireNonNull(palClearFlagBit0, "readonly ProgramServices bit0 knowledge");
            for (int b : new int[]{timeStonesByte, goodFutureByte, usePlayer2Byte, specialStageByte,
                    palFadeFlagsByte, gameModeByte, levelStartedByte}) nativeByte(b);
        }
        private Program prelude(boolean forceGood) {
            return new Program(0, timeStonesByte, forceGood ? 1 : goodFutureByte, 0, specialStageByte,
                    palFadeFlagsByte, palClearFlagBit0, gameModeByte | 0x80, 0, demoDataIndexWord);
        }
        private Program fadeFlag(int value) {
            return new Program(demoModeWord, timeStonesByte, goodFutureByte, usePlayer2Byte, specialStageByte,
                    value, palClearFlagBit0, gameModeByte, levelStartedByte, demoDataIndexWord);
        }
        private Program clearedDemoIndex() {
            return new Program(demoModeWord, timeStonesByte, goodFutureByte, usePlayer2Byte, specialStageByte,
                    palFadeFlagsByte, palClearFlagBit0, gameModeByte, levelStartedByte, 0);
        }
        private Program started() {
            return new Program(demoModeWord, timeStonesByte, goodFutureByte, usePlayer2Byte, specialStageByte,
                    palFadeFlagsByte, palClearFlagBit0, gameModeByte & 0x7F, 1, demoDataIndexWord);
        }
    }
    record Entry(CdTimeWarpSaveData.State saved, CdR12bSectionArt.State section, Program program,
                 CdLevelGlobals level, CdAssetBytes normalPalette, CdAssetBytes fadePalette,
                 CdAssetBytes waterPalette, CdAssetBytes displayedPalette, CdAssetBytes hudSlots,
                 int oldWhiteSteps, boolean displayEnabled) {
        Entry {
            Objects.requireNonNull(saved); Objects.requireNonNull(section); Objects.requireNonNull(program); Objects.requireNonNull(level);
            palette(normalPalette); palette(fadePalette); palette(waterPalette); palette(displayedPalette); extent(hudSlots, 31 * 64);
            if (oldWhiteSteps != 0 && oldWhiteSteps != 22) throw new IllegalArgumentException("Actual old white history is0 or22");
        }
    }
    record Publication(int selector, Phase phase, CdAssetBytes normalPalette, CdAssetBytes waterPalette,
                       boolean displayEnabled, CdR12bSectionArt.State section) {
        Publication {
            if (selector != 0xC && selector != 0x12) throw new IllegalArgumentException("Loading selectorC or12 required");
            Objects.requireNonNull(phase); palette(normalPalette); palette(waterPalette); Objects.requireNonNull(section);
        }
    }
    record State(Phase phase, CdR12bSectionArt.State section, Program program, CdLevelGlobals level,
                 CdAssetBytes normalPalette, CdAssetBytes fadePalette, CdAssetBytes waterPalette, CdAssetBytes displayedPalette,
                 CdAssetBytes hudSlots, Optional<CdAssetBytes> blocksRam, Optional<CdAssetBytes> layoutRam,
                 int oldWhiteSteps, int initialWhiteSteps, int fromWhiteSteps, boolean displayEnabled, List<Call> calls) {
        State {
            Objects.requireNonNull(phase); Objects.requireNonNull(section); Objects.requireNonNull(program); Objects.requireNonNull(level);
            palette(normalPalette); palette(fadePalette); palette(waterPalette); palette(displayedPalette); extent(hudSlots,31 * 64);
            Objects.requireNonNull(blocksRam); Objects.requireNonNull(layoutRam); calls = List.copyOf(calls);
            if ((oldWhiteSteps != 0 && oldWhiteSteps != 22) || initialWhiteSteps < 0 || initialWhiteSteps > 22
                    || fromWhiteSteps < 0 || fromWhiteSteps > 22) throw new IllegalArgumentException("Native fade DBF progress");
            layoutRam.ifPresent(b -> extent(b,2048));
        }
        private State copy(Phase p, CdR12bSectionArt.State a, Program g, CdLevelGlobals l,
                           CdAssetBytes n, CdAssetBytes f, CdAssetBytes w, CdAssetBytes d, CdAssetBytes h,
                           Optional<CdAssetBytes> b, Optional<CdAssetBytes> r, int i, int j, boolean e, List<Call> c) {
            return new State(p,a,g,l,n,f,w,d,h,b,r,oldWhiteSteps,i,j,e,c);
        }
        private State phase(Phase p) { return copy(p,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State section(CdR12bSectionArt.State a) { return copy(phase,a,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State program(Program g) { return copy(phase,section,g,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State level(CdLevelGlobals l) { return copy(phase,section,program,l,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State palettes(CdAssetBytes n,CdAssetBytes f,CdAssetBytes w,CdAssetBytes d) { return copy(phase,section,program,level,n,f,w,d,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State hud(CdAssetBytes h) { return copy(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,h,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State geometry(Optional<CdAssetBytes> b,Optional<CdAssetBytes> r) { return copy(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,b,r,initialWhiteSteps,fromWhiteSteps,displayEnabled,calls); }
        private State progress(int i,int j,boolean e) { return copy(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,i,j,e,calls); }
        private State calls(List<Call> c) { return copy(phase,section,program,level,normalPalette,fadePalette,waterPalette,displayedPalette,hudSlots,blocksRam,layoutRam,initialWhiteSteps,fromWhiteSteps,displayEnabled,c); }
    }
    static final class Snapshot {
        private final SonicCdAct2PastLoadingVisit owner;
        private final State state;
        private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;
        private final Object host;
        private Snapshot(SonicCdAct2PastLoadingVisit owner,State state,CdPlayerFrameRuntime.ActorState actor,
                         CdPlayerFrameRuntime.SharedState shared,Object host) {
            this.owner=owner;this.state=state;this.actor=actor;this.shared=shared;this.host=host;
        }
        State state() { return state; }
    }

    private final Host host;
    private final CdR12bTerrainAssets terrain;
    private final CdR12bPlayerAssets player;
    private final CdPlayerFrameRuntime frame;
    private final CdR12bSectionArt art;
    private final CdTimeWarpSaveData.State saved;
    private State values;
    private boolean running, failed, loadingSaved;
    private int savedCursor;

    SonicCdAct2PastLoadingVisit(Host host,CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,
                               CdPlayerFrameRuntime frame,CdR12bSectionArt art,Entry entry) {
        this.host=Objects.requireNonNull(host);this.terrain=Objects.requireNonNull(terrain);this.player=Objects.requireNonNull(player);
        this.frame=Objects.requireNonNull(frame);this.art=Objects.requireNonNull(art);Objects.requireNonNull(entry);
        if (!frame.stageSource().ownsAct2Past(terrain,player)) throw new IllegalArgumentException("Actual bound R12B pair required");
        art.withSectionWord(entry.section(),entry.section().sectionWord()); // Owner validation only; no adoption/clear/service.
        if(entry.section().stopped())throw new IllegalArgumentException("Stopped art cannot enter a healthy visit");
        saved=entry.saved();values=new State(Phase.ENTRY,entry.section(),entry.program(),entry.level(),entry.normalPalette(),entry.fadePalette(),
                entry.waterPalette(),entry.displayedPalette(),entry.hudSlots(),Optional.empty(),Optional.empty(),entry.oldWhiteSteps(),0,0,entry.displayEnabled(),List.of());
    }
    State state() { return values; }
    CdTimeWarpSaveData.State saved() { return saved; }
    boolean failed() { return failed; }
    List<CdTimeWarpLoadData.NativeStore> completedSavedStores() {
        int count=Collections.frequency(values.calls(),Call.SAVED_STORE);
        return List.copyOf(Arrays.asList(CdTimeWarpLoadData.NativeStore.values()).subList(0,count));
    }
    private void current() {
        if(failed||running||loadingSaved)throw new IllegalStateException("R12B loading failed or operation is active");
        validateHost();frame.captureShared();frame.captureActor();
        if(!frame.stageSource().ownsAct2Past(terrain,player))throw new IllegalStateException("R12B source changed");
    }
    private void validateHost() { host.validate(this,frame,terrain,player); }
    private void act(Action action) { validateHost();host.act(action,values);validateHost();call(Call.valueOf(action.name())); }
    private void call(Call call) { var c=new ArrayList<>(values.calls());c.add(call);values=values.calls(c); }
    private void stop(Boundary boundary) { throw new UnsupportedLoading(boundary); }
    private void terminal(Throwable failure) { failed=true;try{frame.close();}catch(RuntimeException close){failure.addSuppressed(close);} }

    void begin() {
        current();if(values.phase()!=Phase.ENTRY)throw new IllegalStateException("R12B entry already began");
        var gates=frame.environment().movement().gates();var p=values.program();var l=values.level();
        if(gates.zoneAct()!=1||gates.timeZone()!=0x80||l.spawnMode()!=2||p.specialStageByte()!=0)stop(Boundary.ENTRY);
        if((l.plcLoadFlags()&1)==0)stop(Boundary.FRESH_RESET);
        if((l.plcLoadFlags()&2)==0)stop(Boundary.TITLE_CARD);
        if(l.levelRestart()!=0)stop(Boundary.LEVEL_RESTART);
        if(p.demoDataIndexWord()==0x800)stop(Boundary.DEMO_RETURN);
        if(!zeroPrimary(frame.state()))stop(Boundary.ENTRY);
        running=true;
        try {
            values=values.program(p.prelude(p.timeStonesByte()==0x7F&&frame.environment().globals().timeAttackMode()==0));
            frame.beginLevelStart();act(Action.ENTRY_STORES);
            artOp(art::clearPlcs);call(Call.CLEAR_PLCS); // Native Main57; abandons incoming work only here.
            boolean fade=(values.program().palFadeFlagsByte()&1)!=0;
            values=values.program(values.program().fadeFlag(values.program().palFadeFlagsByte()|1));
            if(fade)values=values.phase(Phase.INITIAL_WHITE);else normalLoad();
        } catch(RuntimeException|Error failure){terminal(failure);throw failure;}finally{running=false;}
    }
    private void normalLoad() {
        frame.clearLevelStartWarpDirection();act(Action.WHITE_PRELUDE_END);
        values=values.palettes(white(),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(Phase.FIRST_PLC);
        act(Action.NORMAL_LOAD_PALETTE); // Marked native branch fills normal CPU palette; no selected-WARP substitution.
    }

    void service(int held) {
        current();nativeByte(held);
        if(values.phase()==Phase.ENTRY)throw new IllegalStateException("Install then begin actual loading first");
        if(values.phase()==Phase.BEGIN_LEVEL)stop(Boundary.ORDINARY_OBJECT_PASS);
        running=true;
        try {
            switch(values.phase()) {
                case INITIAL_WHITE -> whiteService(held,true);
                case FROM_WHITE -> whiteService(held,false);
                case FIRST_PLC,SECTION_PLC -> plcService(held);
                default -> throw new IllegalStateException("Unserviceable loading phase");
            }
        } catch(RuntimeException|Error failure){terminal(failure);throw failure;}finally{running=false;}
    }
    private void plcService(int held) {
        Phase phase=values.phase();frame.serviceLevelLoad(held,()->publish(0xC),()->act(Action.COPY_AND_DRAW),()->artOp(art::serviceFast));
        act(Action.VINT_COMPLETED);call(phase==Phase.FIRST_PLC?Call.FIRST_WAIT:Call.SECTION_WAIT);
        artOp(art::prepare);call(Call.PROCESS_PLCS);
        if(values.section().queue().headSource()==0&&values.section().remainingTilesWord()==0) {
            if(phase==Phase.FIRST_PLC)mainLoadPrefix();else finishSectionWait();
        }
    }
    private void whiteService(int held,boolean initial) {
        frame.serviceLevelPaletteFade(held,()->{
            publish(0x12); // DoVIntUpdates precedes VInt_PalFade's actual enable stores.
            if(!initial&&values.level().fadeEnableDisplay()==1) {
                var l=values.level();values=values.level(level(l,2,l.powerup(),l.spawnMode(),l.plcLoadFlags(),l.levelRestart(),l.updateHudTime(),l.timeOver()));
                values=values.progress(values.initialWhiteSteps(),values.fromWhiteSteps(),true);act(Action.ENABLE_DISPLAY);
            }
        },()->artOp(art::serviceFast));
        act(Action.VINT_COMPLETED);call(initial?Call.INITIAL_WHITE_SERVICE:Call.FROM_WHITE_SERVICE);
        if(initial) {
            var colours=CdLevelPaletteFade.stepToWhite(new CdLevelPaletteFade.Palettes(values.normalPalette(),values.waterPalette()));
            values=values.palettes(colours.palette(),values.fadePalette(),colours.waterPalette(),values.displayedPalette());call(Call.INITIAL_WHITE_COLOURS);
        } else {
            var colours=CdLevelPaletteFade.stepFromWhite(values.normalPalette(),values.fadePalette());
            values=values.palettes(colours,values.fadePalette(),values.waterPalette(),values.displayedPalette());call(Call.FROM_WHITE_COLOURS);
        }
        artOp(art::prepare);call(Call.PROCESS_PLCS);
        values=values.progress(values.initialWhiteSteps()+(initial?1:0),values.fromWhiteSteps()+(initial?0:1),values.displayEnabled());
        if(initial&&values.initialWhiteSteps()==22) {
            values=values.program(values.program().fadeFlag(values.program().palFadeFlagsByte()&~1));normalLoad();
        } else if(!initial&&values.fromWhiteSteps()==22) {
            values=values.program(values.program().started()).phase(Phase.BEGIN_LEVEL);act(Action.BEGIN_LEVEL);
        }
    }
    private void publish(int selector) {
        validateHost();host.publish(new Publication(selector,values.phase(),values.normalPalette(),values.waterPalette(),values.displayEnabled(),values.section()));validateHost();
        values=values.palettes(values.normalPalette(),values.fadePalette(),values.waterPalette(),values.normalPalette());
    }
    private void artOp(UnaryOperator<CdR12bSectionArt.State> operation) {
        var before=values.section();
        try { values=values.section(operation.apply(before)); }
        catch(CdR12bSectionArt.SectionArtException failure) {
            values=values.section(failure.state());
            failure.completedRamPrefix().ifPresent(b->values=values.geometry(Optional.of(b),values.layoutRam()));
            try{act(Action.CURRENT_ART);}catch(RuntimeException|Error publication){failure.addSuppressed(publication);}throw failure;
        }
        if(values.section().residency()!=before.residency())act(Action.CURRENT_ART);
    }

    private void mainLoadPrefix() {
        act(Action.PLAY_LEVEL_MUSIC);
        int id=terrain.view(LEVEL_DATA_INDEX).unsignedByte(0);
        if(id!=0){artOp(s->art.loadImmediateList(s,id));call(Call.IMMEDIATE_LEVEL);}
        artOp(s->art.loadImmediateList(s,1));call(Call.IMMEDIATE_STANDARD);
        var l=values.level();values=values.level(new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),0,0,0,l.timeOver()));
        act(Action.CLEAR_POWERUP_FLOWERS);act(Action.CLEAR_DRAW_QUEUE);
        frame.clearLevelStartFlowersPlayerValues();act(Action.CLEAR_FLOWERS);
        if(!zeroPrimary(frame.state()))stop(Boundary.ENTRY);
        values=values.hud(zero(31*64));act(Action.CLEAR_OBJECTS);act(Action.CLEAR_UNKNOWN_BUFFER);act(Action.CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues();act(Action.CLEAR_CAMERA);
        values=values.program(values.program().clearedDemoIndex());act(Action.CLEAR_DEMO_INDEX);
        // FixBugs=0 ClearScreen/General:212-218 writes161 LONGs: F800..FA84, four bytes into waterPalette.
        // The hscroll DBF's extra LONG touches the already-cleared primary header; it adds no new owner.
        byte[] water=values.waterPalette().copy();Arrays.fill(water,0,4,(byte)0);
        values=values.palettes(values.normalPalette(),values.fadePalette(),CdAssetBytes.copyOf(water),values.displayedPalette());
        act(Action.CLEAR_SCREEN);act(Action.DROWN_TIMER);
        paletteCopy(terrain.view(PAL_SONIC),0,true);paletteCopy(terrain.view(PAL_SONIC),0,false);call(Call.PALETTE_SONIC);
        var b=terrain.loadPlan().bounds();frame.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),frame.environment().movement().bounds().bossFight()),b.cameraCentreY());
        act(Action.LEVEL_SIZE);loadSaved();call(Call.LOAD_SAVED);act(Action.SETUP_CAMERA_AND_SCROLL);act(Action.LEVEL_SCROLL);
        try { var blocks=art.decodeBlocksToRam(values.section());values=values.section(blocks.state()).geometry(Optional.of(blocks.blocks()),values.layoutRam()); }
        catch(CdR12bSectionArt.SectionArtException failure){values=values.section(failure.state());failure.completedRamPrefix().ifPresent(bts->values=values.geometry(Optional.of(bts),values.layoutRam()));throw failure;}
        call(Call.BLOCKS_RAM);values=values.geometry(values.blocksRam(),Optional.of(copyLayout()));act(Action.LEVEL_LAYOUT);
        paletteCopy(terrain.view(PAL_LEVEL),32,false);call(Call.PALETTE_LEVEL);
        validateHost();int camera=host.cameraXWord();validateHost();word(camera);artOp(s->art.loadSection(s,camera));call(Call.SECTION_ART);
        act(Action.INITIAL_FOREGROUND);act(Action.INITIAL_BACKGROUND);act(Action.COLLISION_POINTER);values=values.phase(Phase.SECTION_PLC);
    }
    private void finishSectionWait() {
        frame.applyLevelStartWarpPlayer();call(Call.LOAD_PLAYER);
        byte[] hud=values.hudSlots().copy();for(int slot:new int[]{2,3,5})hud[(slot-1)*64]=0x1C;
        hud[2*64+0x28]=1;hud[4*64+0x29]=1;values=values.hud(CdAssetBytes.copyOf(hud));act(Action.RESERVED_HUD);
        artOp(s->art.loadLifeIcon(s,frame.environment().movement().gates().timeZone()));call(Call.LIFE_ICON);
        hud=values.hudSlots().copy();hud[30*64]=0x19;hud[30*64+0x28]=10;values=values.hud(CdAssetBytes.copyOf(hud));act(Action.RESERVED_HUD);call(Call.RESERVED_TIME_ICON);
        frame.clearLevelStartControlsAndBoredTimer();frame.clearLevelStartPlayerGlobals();
        var l=values.level();values=values.level(new CdLevelGlobals(0,1,l.checkpoint(),0,l.plcLoadFlags()|2,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),0));act(Action.BEGIN_MAIN);
        var gate=CdPowerupArtRuntime.gate(values.level().powerup());
        l=values.level();values=values.level(level(l,l.fadeEnableDisplay(),gate.powerup(),l.spawnMode(),l.plcLoadFlags(),l.levelRestart(),l.updateHudTime(),l.timeOver()));
        if(gate.copyRequired())stop(Boundary.POWERUP_ART);act(Action.UPDATE_ANIMATED_TILES);
        l=values.level();values=values.level(level(l,1,l.powerup(),l.spawnMode(),l.plcLoadFlags(),l.levelRestart(),l.updateHudTime(),l.timeOver()));
        if(!frame.clearLevelStartTimeZoneMarker()||frame.environment().movement().gates().timeZone()!=0)stop(Boundary.ENTRY);
        act(Action.ENABLE_FADE_AND_CLEAR_MARKER);values=values.palettes(white(),values.fadePalette(),values.waterPalette(),values.displayedPalette()).phase(Phase.FROM_WHITE);
    }
    private void paletteCopy(CdAssetBytes source,int offset,boolean normal) {
        byte[] target=(normal?values.normalPalette():values.fadePalette()).copy();System.arraycopy(source.copy(),0,target,offset,source.length());
        values=values.palettes(normal?CdAssetBytes.copyOf(target):values.normalPalette(),normal?values.fadePalette():CdAssetBytes.copyOf(target),values.waterPalette(),values.displayedPalette());
    }
    private CdAssetBytes copyLayout() {
        byte[] ram=new byte[2048];copyPlane(ram,0,terrain.view(LAYOUT_FG));copyPlane(ram,64,terrain.view(LAYOUT_BG));
        var actual=terrain.terrain().layout().ram();if(!Arrays.equals(ram,actual.copy()))throw new IllegalStateException("Executed R12B layout differs from actual source product");return actual;
    }
    private static void copyPlane(byte[] ram,int base,CdAssetBytes source) {
        int columns=source.unsignedByte(0)+1,rows=source.unsignedByte(1)+1,at=2;
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)ram[base+y*128+x]=(byte)source.unsignedByte(at++);
        if(at!=source.length())throw new IllegalArgumentException("Complete native layout source required");
    }

    private void loadSaved() {
        if(loadingSaved||!completedSavedStores().isEmpty())throw new IllegalStateException("Actual saved helper already invoked");
        loadingSaved=true;savedCursor=0;
        try {
            var result=CdTimeWarpLoadData.load(saved,new CdTimeWarpLoadData.Port(){
                private CdTimeWarpLoadData.StoreTarget observed;
                private OptionalInt preimage;
                private CdTimeWarpLoadData.StoreCall committed;
                private State expectedState;
                private CdPlayerFrameRuntime.ActorState actor;
                private CdPlayerFrameRuntime.SharedState shared;
                private Object external;
                private void active(){validateHost();if(!running||!loadingSaved)throw new IllegalStateException("No private actual R12B load invocation");}
                public CdTimeWarpSaveData.State currentSaved(){active();return saved;}
                public OptionalInt read(CdTimeWarpLoadData.StoreTarget target){
                    active();var stateBefore=values;var actorBefore=frame.captureActor();var sharedBefore=frame.captureShared();
                    Object hostBefore=host.capture();validateHost();OptionalInt value;
                    if(frameTarget(target))value=OptionalInt.of(frame.readTimeWarpLoadField(target));
                    else {var raw=Objects.requireNonNull(host.readLoadTarget(target));validateHost();value=rawInt(raw,target.width());}
                    if(values!=stateBefore||!frame.captureActor().equals(actorBefore)||!frame.captureShared().equals(sharedBefore)
                            ||!Objects.equals(host.capture(),hostBefore))throw new IllegalStateException("Saved target observation changed its complete owner cut");
                    if(committed!=null) {
                        if(target!=committed.target()||value.isEmpty()||value.getAsInt()!=committed.value())
                            throw new IllegalStateException("Actual saved store was not synchronously published");
                        call(Call.SAVED_STORE);committed=null;
                    }
                    observed=target;preimage=value;expectedState=values;actor=actorBefore;shared=sharedBefore;external=hostBefore;validateHost();return value;
                }
                public OptionalInt readLive(CdTimeWarpLoadData.LiveRead source){
                    active();if(source==CdTimeWarpLoadData.LiveRead.ZONE&&savedCursor==20){savedCursor++;return OptionalInt.of(frame.environment().movement().gates().zoneAct()>>>8);}
                    if(source==CdTimeWarpLoadData.LiveRead.SPAWN_MODE&&savedCursor==21){savedCursor++;return OptionalInt.of(values.level().spawnMode());}
                    throw new IllegalStateException("Wrong actual R12B conditional read");
                }
                public void commit(CdTimeWarpLoadData.StoreCall call){
                    active();if(savedCursor>=20||call.instruction()!=CdTimeWarpLoadData.NativeStore.values()[savedCursor]
                            ||call.target()!=observed||!call.expectedValue().equals(preimage)||values!=expectedState
                            ||!frame.captureActor().equals(actor)||!frame.captureShared().equals(shared)||!Objects.equals(host.capture(),external))
                        throw new IllegalStateException("Stale or out-of-order actual R12B saved store");
                    if(frameTarget(call.target()))frame.applyTimeWarpLoadStore(call);else{host.storeLoadTarget(call);validateHost();}
                    savedCursor++;committed=call;observed=null;
                }
            });
            if(savedCursor!=22||result.completedNativeStores().size()!=20)throw new IllegalStateException("Actual PP/spawn2 load prefix incomplete");
        } finally {loadingSaved=false;savedCursor=0;}
    }
    private static OptionalInt rawInt(OptionalLong raw,int width) {
        if(raw.isEmpty())return OptionalInt.empty();long value=raw.getAsLong();long max=width==4?0xFFFFFFFFL:width==2?0xFFFFL:0xFFL;
        if(value<0||value>max)throw new IllegalArgumentException("Host preimage exceeds native unsigned width");return OptionalInt.of((int)value);
    }
    private static boolean frameTarget(CdTimeWarpLoadData.StoreTarget t) {
        return switch(t){case PRIMARY_X,PRIMARY_Y,PRIMARY_FLAGS,PRIMARY_GVEL,PRIMARY_X_VELOCITY,PRIMARY_Y_VELOCITY,BOTTOM_BOUND,MINI_SONIC,LEFT_BOUND->true;default->false;};
    }

    Snapshot capture() { current();Object h=host.capture();validateHost();return new Snapshot(this,values,frame.captureActor(),frame.captureShared(),h); }
    void validateRestore(Snapshot snapshot) {
        current();Objects.requireNonNull(snapshot);if(snapshot.owner!=this)throw new IllegalArgumentException("Another actual R12B visit capture");
        frame.validateActorRestore(snapshot.actor);frame.validateSharedRestore(snapshot.shared);host.validateRestore(snapshot.host);validateHost();
        var v=snapshot.state;art.withSectionWord(v.section(),v.section().sectionWord());if(v.section().stopped())throw new IllegalArgumentException("Stopped loading capture");
        int period=snapshot.shared.environment().movement().gates().timeZone();
        if(snapshot.shared.environment().movement().gates().zoneAct()!=1||period!=(v.phase()==Phase.FROM_WHITE||v.phase()==Phase.BEGIN_LEVEL?0:0x80)
                ||(v.phase()==Phase.BEGIN_LEVEL?v.fromWhiteSteps()!=22:v.fromWhiteSteps()==22)
                ||(v.phase()==Phase.INITIAL_WHITE&&v.initialWhiteSteps()>=22))throw new IllegalArgumentException("Captured R12B phase/source/progress differs");
    }
    void restore(Snapshot snapshot) {
        validateRestore(snapshot);running=true;
        try{host.restore(snapshot.host);validateHost();frame.restoreActor(snapshot.actor);frame.restoreShared(snapshot.shared);values=snapshot.state;}
        catch(RuntimeException|Error failure){terminal(failure);throw failure;}finally{running=false;}
    }
    private static CdLevelGlobals level(CdLevelGlobals l,int fade,int power,int spawn,int flags,int restart,int update,int over) {
        return new CdLevelGlobals(restart,update,l.checkpoint(),spawn,flags,l.flowerCount(),fade,power,over);
    }
    private static boolean zeroPrimary(CdPlayerBody.State state) {
        var m=state.motion();var h=state.header();var a=state.animation();var t=state.timers();var links=state.objectLinks();
        return m.equals(new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0))&&h.slotId()==0&&h.mappingAddress()==0&&h.tile()==0&&h.priority()==0&&h.width()==0
                &&a.scriptIndex()==0&&a.timer()==0&&a.mapFrame()==0&&t.hurt()==0&&t.invincibility()==0&&t.speedShoes()==0&&t.reset()==0
                &&state.rotationDistance()==0&&state.rotationAngle()==0&&links.equals(new CdPlayerBody.ObjectLinks(0,0));
    }
    private static void word(int value){if((value&~0xFFFF)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void nativeByte(int value){if((value&~0xFF)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void extent(CdAssetBytes value,int bytes){Objects.requireNonNull(value);if(value.length()!=bytes)throw new IllegalArgumentException("Complete native value extent required");}
    private static void palette(CdAssetBytes value){extent(value,128);}
    private static CdAssetBytes zero(int count){return CdAssetBytes.copyOf(new byte[count]);}
    private static CdAssetBytes white(){byte[] b=new byte[128];for(int i=0;i<b.length;i+=2){b[i]=14;b[i+1]=(byte)238;}return CdAssetBytes.copyOf(b);}
}
