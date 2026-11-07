package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Original USA R31 INITIAL RunObjects/DrawObjects, never another World or writable bank.
 * N1ecc5f3a: Level/Object Functions.asm RunObjects11-33/DrawObjects243-365;
 * objects/player.asm PlayerInit139-157 ends RTS. Actual R3 Legacy include/handler operands,
 * Monitor/HUD/Title wrappers and first layout row are authenticated in the external startup audit.
 * Ports is private Root wiring: it must require its real original Core OrdinaryAccess, source,
 * Frame, placement, physical phase and complete live Host cut. Equal values grant no authority.
 * All executable receipts below have private constructors, current invocation/thread/identity
 * checks and finally retirement. Completed component effects are never executable commands.
 */
final class SonicCdR31aObjectPass {
    interface Ports {
        void validate(SonicCdR31aObjectPass pass,CdR31aAssets assets,CdPlayerFrameRuntime frame,
                      CdObjectPlacementRuntime placement,CdR31aObjectAssets common);
        Cut read();
        /** Prebuild Core/Host aggregates, validate original receipt, applyNative, adopt ready references.
         * No callback/fallible source read may intervene between the coupled owner assignments. */
        void adopt(Adoption original);
        CdPlayerBody.R31Context primaryContext();
        /** Same rule for genuine Frame R31BodyCall plus its complete nativeValues and sound ledger. */
        void primary(PrimarySubmission original);
        /** Only original Frame helper calls inside this pass, never completed effects. */
        default void placeBlock(SonicCdR31aObjectPass pass,CdPlayerFrameRuntime.R31PlaceBlockCall original){throw new IllegalStateException("No original PlaceBlock World owner");}
        default void placeScreen(SonicCdR31aObjectPass pass,CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){throw new IllegalStateException("No original PlaceBlock screen World owner");}
        /** Invoke actual active Core OrdinaryAccess.appendTitlePlc(current), then acknowledge return. */
        void submitTitlePlc(PlcSubmission original);
    }
    /** Fresh typed actual owners, not a programme token, second bank or captured invocation. */
    record Cut(SonicCdR31aLoadingVisit.State core,CdRingGlobals ring,CdStageTerrain currentTerrain,
               CdPlayerBody.ContactGlobals contact,CdPlayerTouchResponse.WorldGlobals world,OptionalInt noBumperByte,
               int cameraXWord,int cameraYWord,int scrollLockWord,OptionalInt layerByte,
               CdAssetBytes cpuSat,Optional<CdObjectDrawQueue.CpuSat> completedCpu,List<SonicCdGameModule.PastSound> sounds) {
        Cut {Objects.requireNonNull(core);Objects.requireNonNull(ring);Objects.requireNonNull(currentTerrain);
            Objects.requireNonNull(contact);Objects.requireNonNull(world);Objects.requireNonNull(noBumperByte);
            noBumperByte.ifPresent(SonicCdR31aObjectPass::octet);word(cameraXWord);word(cameraYWord);word(scrollLockWord);
            Objects.requireNonNull(layerByte);layerByte.ifPresent(SonicCdR31aObjectPass::octet);extent(cpuSat,640);
            Objects.requireNonNull(completedCpu);sounds=List.copyOf(sounds);}
    }
    enum Boundary { OBJECT_INDEX,OBJECT_FAMILY,PHYSICAL_OWNER,PRIMARY_ROUTINE,PRIMARY_CONTEXT_OWNER,
        PRIMARY_DISPLAY_OWNER,CURRENT_TERRAIN_OWNER,VINT,LAYER,TITLE_PLC_OWNER }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;private final int wholeIndex,value;
        private Unowned(Boundary b,int w,int v){super("R31 initial "+b+" at whole "+w+" value$"+Integer.toHexString(v));boundary=b;wholeIndex=w;value=v;}
        Boundary boundary(){return boundary;}int wholeIndex(){return wholeIndex;}int value(){return value;}
    }
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,HUD,TITLE,MONITOR }
    private static final class Dispatch {
        final Kind kind;final int whole,routine;final Object body;final Thread thread=Thread.currentThread();
        final Set<Object> calls=Collections.newSetFromMap(new IdentityHashMap<>());
        ReadFrame frame;Cut cut;boolean drawn,fm;int titleOrder;CdMonitor.PrefixSolidCut solidCut;
        Dispatch(Kind k,int w,int r,Object b){kind=k;whole=w;routine=r;body=b;}
    }
    private record ReadFrame(CdPlayerBody.State state,CdPlayerBody.Environment environment,
                             Optional<CdPlayerBody.DisplayPublication> display,Object art,
                             CdPlayerFrameRuntime.VIntClock clock,boolean paused) { }

    /** Original synchronous publication; never constructible, captured, cloned or resumed. */
    final class Adoption {
        private final Cut expected,next;private final ReadFrame before;
        private final CdObjectPlacementRuntime.Snapshot placed;
        private final CdObjectPool.Snapshot poolOutput;
        private final CdPlayerFrameRuntime.ObjectSolidPublication frameOutput;
        private final Thread thread=Thread.currentThread();private boolean applied;
        // BEGIN original primary drain flag publication 20261107.
        private Boolean nativeScreenFlag;private ReadFrame screenReady;
        private void prepareScreenFlag(boolean value){
            if(nativeScreenFlag!=null||poolOutput!=null||frameOutput!=null||phase!=Phase.DRAINING
                    ||queue==null||queue.phase()!=CdObjectDrawQueue.Phase.DRAINING||primaryDraw!=null)
                throw new IllegalStateException("No original primary drain-only flag preparation");
            screenReady=screenFlagFrame(before,value);nativeScreenFlag=value;
        }
        // END original primary drain flag publication 20261107.
        private Adoption(Cut e,Cut n,CdObjectPool.Snapshot p,CdPlayerFrameRuntime.ObjectSolidPublication f){
            expected=e;next=n;poolOutput=p;frameOutput=f;before=readFrame();placed=placement.capture();
            if(p!=null)placement.pool().validateRestore(p);if(f!=null)frame.validatePreparedObjectSolidPublication(f);
        }
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}Cut expected(){return expected;}Cut next(){return next;}
        void validateBefore(){currentAdoption(this);requireCut(expected);requireFrame(before);requirePlacement(placed);
            if(poolOutput!=null)placement.pool().validateRestore(poolOutput);
            if(frameOutput!=null)frame.validatePreparedObjectSolidPublication(frameOutput);}
        void applyNative(){validateBefore();
            // All output values and sound/Core aggregates are ready; no Host/Frame callback is run.
            // BEGIN original primary drain flag application 20261107.
            if(nativeScreenFlag!=null){
                if(phase!=Phase.DRAINING||queue==null||queue.phase()!=CdObjectDrawQueue.Phase.DRAINING
                        ||poolOutput!=null||frameOutput!=null||screenReady==null||primaryDraw!=null)
                    throw new IllegalStateException("Expired primary native drain store");
                frame.publishDrawObjectOnScreen(nativeScreenFlag.booleanValue());
            }else{
                if(poolOutput!=null)placement.pool().restore(poolOutput);
                if(frameOutput!=null)frame.applyPreparedObjectSolidPublication(frameOutput);
            }
            // END original primary drain flag application 20261107.
            applied=true;
        }
    }
    /** Only the original Frame producer can create this synchronous handoff. */
    final class PrimarySubmission {
        private final CdPlayerFrameRuntime.R31BodyCall original;private final Cut expected,next;
        private final CdPlayerBody.R31Context context;private final CdObjectPlacementRuntime.Snapshot placed;
        private final CdPlayerBody.State readyState;
        private final Thread thread=Thread.currentThread();private boolean applied;
        private PrimarySubmission(CdPlayerFrameRuntime.R31BodyCall c,Cut e,CdPlayerBody.R31Context context,CdPlayerBody.State readyState,List<CdPlayerFrameRuntime.Sound> reachedSounds){
            original=c;expected=e;this.context=context;this.readyState=Objects.requireNonNull(readyState);placed=placement.capture();
            var publication=c.publication();var values=publication.nativeValues();var sounds=new ArrayList<>(e.sounds());
            for(var sound:soundSuffix(reachedSounds,publication.body().sounds()))sounds.add(new SonicCdGameModule.PastFrameSound(sound));
            next=withNativeValues(e,values,sounds);
            placement.pool().validateRestore(values.snapshot());
        }
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}
        CdPlayerFrameRuntime.R31Publication publication(){return original.publication();}
        /** Actual original pending-display preparation DATA; publication remains pre-Draw data. */
        CdPlayerBody.State readyState(){return readyState;}
        Cut expected(){return expected;}Cut next(){return next;}
        void validateBefore(){currentPrimary(this);original.requireOwner(frame);requireCut(expected);requirePlacement(placed);
            if(primaryDraw==null||primaryDraw.original!=original||primaryDraw.readyState!=readyState
                    ||primaryDraw.invoking||primaryDraw.required&&(!primaryDraw.returned||!primaryDraw.clearSeen))
                throw new IllegalStateException("No completed original primary Draw preparation");
            if(context.source()!=frame.stageSource()||context.assets()!=assets)throw new IllegalArgumentException("Foreign original R31 context");
            placement.pool().validateRestore(publication().nativeValues().snapshot());}
        void applyNative(){validateBefore();original.applyNative();placement.pool().restore(publication().nativeValues().snapshot());applied=true;}
        Unowned unowned(){validateBefore();return stop(Boundary.PRIMARY_CONTEXT_OWNER,0,dispatch.routine);}
    }
    /** Actual Title deferred PLC2 call, not a prepared arbitrary Core/art setter. */
    final class PlcSubmission {
        private final CdTitleCard.PlcCall original;private final Cut expected;
        private final ReadFrame before;private final CdObjectPlacementRuntime.Snapshot placed;
        private final Thread thread=Thread.currentThread();private Cut after;private boolean acknowledged;
        private PlcSubmission(CdTitleCard.PlcCall c){original=c;expected=read();before=readFrame();placed=placement.capture();}
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}
        int nativeId(){return original.nativeId();}int wholeIndex(){return original.cut().wholeIndex();}
        void validateBefore(){currentPlc(this);if(acknowledged)throw new IllegalStateException("Already completed Title PLC");
            requireCut(expected);requireFrame(before);requirePlacement(placed);titleSource(original.cut());}
        void acknowledge(){currentPlc(this);if(acknowledged)throw new IllegalStateException("Repeated Title PLC completion");
            requireFrame(before);requirePlacement(placed);titleSource(original.cut());var actual=read();requireOnlyTitlePlc(expected,actual,true);
            after=actual;acknowledged=true;}
        Unowned unowned(){validateBefore();return stop(Boundary.TITLE_PLC_OWNER,wholeIndex(),nativeId());}
    }

    /** Ephemeral original four-argument Body invocation; not captured or made from a result. */
    private static final class PrimaryInvocation {
        final CdPlayerBody.R31Context context;final CdObjectPlacementRuntime.Snapshot placement;
        Cut current;List<CdPlayerFrameRuntime.Sound> sounds=List.of();
        PrimaryInvocation(CdPlayerBody.R31Context context,CdObjectPlacementRuntime.Snapshot placement,Cut current){this.context=context;this.placement=placement;this.current=current;}
    }
    private PrimaryInvocation primaryInvocation;
    private CdPlayerFrameRuntime.R31PlaceCall placeCall;
    private Cut placeNext;private boolean placeApplied;
    /** World must authenticate this pass and its own original OrdinaryAccess in addition to Frame. */
    void requirePlace(CdPlayerFrameRuntime.R31PlaceCall original){
        requireKind(Kind.PRIMARY);
        if(primaryInvocation==null||placeCall!=original||placeApplied||primaryDraw!=null||primaryPending!=null
                ||original.source()!=frame.stageSource())throw new IllegalStateException("No original physical PlaceBlock invocation");
        original.requireOwner(frame);requireCut(primaryInvocation.current);
        var prefix=original.before();requirePrefix(prefix,primaryInvocation.current);
        requirePlacementOutput(primaryInvocation.placement,prefix.nativeValues().snapshot());
    }
    Cut preparedPlace(CdPlayerFrameRuntime.R31PlaceBlockCall original){requirePlace(original);return Objects.requireNonNull(placeNext);}
    /** All full fences and pool ownership are validated before this allocation-free native prefix.
     * Snapshot is immutable and same-owner: restore assigns exactly96 already validated references. */
    void applyPlace(CdPlayerFrameRuntime.R31PlaceBlockCall original){
        requirePlace(original);placement.pool().validateRestore(original.prefix().nativeValues().snapshot());
        original.applyNative();placement.pool().restore(original.prefix().nativeValues().snapshot());
        primaryInvocation.current=placeNext;primaryInvocation.sounds=original.sounds();placeApplied=true;
    }
    void verifyPlaceScreen(CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){requirePlace(original);original.verifyNative();placeApplied=true;}
    private void requirePrefix(CdPlayerBody.R31Prefix prefix,Cut external){
        var values=prefix.nativeValues();
        if(frame.state()!=prefix.state()||frame.environment()!=prefix.environment()||values.terrain()!=external.currentTerrain()
                ||!values.noBumperByte().equals(external.noBumperByte())||!values.contact().equals(external.contact())
                ||!values.world().equals(external.world()))throw new IllegalArgumentException("Changed full original PlaceBlock comparison cut");
        poolCut(values.snapshot());
    }
    private void publishPlace(CdPlayerFrameRuntime.R31PlaceCall original){
        if(placeCall!=null||primaryInvocation==null)throw new IllegalStateException("PlaceBlock reentry");
        placeCall=original;placeApplied=false;
        try{
            requirePlace(original);
            if(original instanceof CdPlayerFrameRuntime.R31PlaceBlockCall store){
                var sounds=new ArrayList<>(primaryInvocation.current.sounds());
                for(var sound:soundSuffix(primaryInvocation.sounds,store.sounds()))sounds.add(new SonicCdGameModule.PastFrameSound(sound));
                placeNext=withNativeValues(primaryInvocation.current,store.prefix().nativeValues(),sounds);
                placement.pool().validateRestore(store.prefix().nativeValues().snapshot());
                ports.placeBlock(this,store);
                if(!placeApplied)throw new IllegalStateException("Original PlaceBlock store omitted");
                requireCut(primaryInvocation.current);requirePrefix(store.prefix(),primaryInvocation.current);
                requirePlacementOutput(primaryInvocation.placement,store.prefix().nativeValues().snapshot());
            }else{
                ports.placeScreen(this,(CdPlayerFrameRuntime.R31PlaceBlockScreenCall)original);
                if(!placeApplied)throw new IllegalStateException("Original screen verification omitted");
                requireCut(primaryInvocation.current);requirePrefix(original.before(),primaryInvocation.current);
                requirePlacementOutput(primaryInvocation.placement,original.before().nativeValues().snapshot());
            }
        }catch(RuntimeException|Error fault){
            verify(fault,()->{requireCut(primaryInvocation.current);var prefix=placeApplied&&original instanceof CdPlayerFrameRuntime.R31PlaceBlockCall store?store.prefix():original.before();requirePrefix(prefix,primaryInvocation.current);requirePlacementOutput(primaryInvocation.placement,prefix.nativeValues().snapshot());});throw fault;
        }finally{placeCall=null;placeNext=null;placeApplied=false;}
    }
    private static List<CdPlayerFrameRuntime.Sound> soundSuffix(List<CdPlayerFrameRuntime.Sound> before,List<CdPlayerFrameRuntime.Sound> next){
        if(next.size()<before.size()||!next.subList(0,before.size()).equals(before))throw new IllegalArgumentException("Original reached sound prefix replaced");
        return next.subList(before.size(),next.size());
    }
    private static Cut withNativeValues(Cut e,CdPlayerBody.R31Values values,List<SonicCdGameModule.PastSound> sounds){
        var r=e.ring();var w=values.world();
        var ring=new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());
        return new Cut(e.core(),ring,values.terrain(),values.contact(),w,values.noBumperByte(),e.cameraXWord(),e.cameraYWord(),e.scrollLockWord(),e.layerByte(),e.cpuSat(),e.completedCpu(),sounds);
    }

    // BEGIN scoped original primary queue operation 20261107.
    private final class PrimaryDraw {
        private final CdPlayerFrameRuntime.R31BodyCall original;private final Cut cut;
        private final ReadFrame before;private final CdObjectPlacementRuntime.Snapshot placed;
        private final Thread thread=Thread.currentThread();private final boolean required;
        private CdPlayerBody.State readyState;private CdObjectDrawQueue.DrawCallCut drawCut;
        private boolean invoking,clearSeen,returned;
        private PrimaryDraw(CdPlayerFrameRuntime.R31BodyCall original,Cut cut){
            this.original=original;this.cut=cut;before=readFrame();placed=placement.capture();
            var result=original.publication().body().result();readyState=result.state();
            required=result.display().isPresent()&&result.display().orElseThrow().queued();
        }
        private void validate(){
            if(primaryDraw!=this||thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()
                    ||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.PRIMARY||dispatch.whole!=0||dispatch.body!=frame
                    ||adopting||plc)throw new IllegalStateException("No original current primary Draw scope");
            original.requireOwner(frame);requireFrame(before);requirePlacement(placed);requireCut(cut);
        }
    }
    private PrimaryDraw primaryDraw;
    // END scoped original primary queue operation 20261107.
    private final CdR31aAssets assets;private final CdPlayerFrameRuntime frame;
    private final CdObjectPlacementRuntime placement;private final CdR31aObjectAssets common;private final Ports ports;
    private final CdStageTerrain originalTerrain;
    private final CdObjectMappings mappings;private final CdPlayerTerrain terrain;private final CdSolidObject solid;
    private final CdReservedHud hud;private final CdTitleCard title;private final CdMonitor monitor;
    private Phase phase=Phase.READY;private CdObjectDrawQueue queue;private Dispatch dispatch;
    private boolean checking,reading,adopting,drawing,plc;private Thread walkingThread;
    private Adoption pending;private PrimarySubmission primaryPending;private PlcSubmission plcPending;

    SonicCdR31aObjectPass(CdR31aAssets assets,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement,
                         CdR31aObjectAssets common,Ports ports) throws java.io.IOException {
        this.assets=Objects.requireNonNull(assets);this.frame=Objects.requireNonNull(frame);this.placement=Objects.requireNonNull(placement);
        this.common=Objects.requireNonNull(common);this.ports=Objects.requireNonNull(ports);
        if(!frame.stageSource().ownsR31aAssets(assets)||!placement.ownsR31aAssets(assets)
                ||placement.placements()!=assets.placements()||!common.ownsAssets(assets))
            throw new IllegalArgumentException("Exact retained original R31 assets/Frame/placement/common metadata required");
        mappings=CdObjectMappings.fromR31aAssets(assets,common);
        originalTerrain=assets.terrain();terrain=CdPlayerTerrain.fromStageTerrain(originalTerrain);solid=new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        hud=new CdReservedHud(common.reservedHudAssets(),new CdReservedHud.Slots(){
            public CdAssetBytes read(int w){requireWhole(Kind.HUD,w);return reserved(SonicCdR31aObjectPass.this.read(),w);}
            public void write(int w,CdAssetBytes e,CdAssetBytes n){reservedWrite(Kind.HUD,w,e,n);}},w->drawReserved(Kind.HUD,w));
        title=new CdTitleCard(placement,common.titleCardAssets(),new CdTitleCard.ReservedSlots(){
            public CdAssetBytes read(int w){requireWhole(Kind.TITLE,w);return reserved(SonicCdR31aObjectPass.this.read(),w);}
            public void write(int w,CdAssetBytes e,CdAssetBytes n){reservedWrite(Kind.TITLE,w,e,n);}},
            this::titleRead,this::titleRead,this::titleStore,this::titlePlc,this::titleDraw);
        monitor=CdMonitor.withPastOwner(placement,common.monitorAssets(),solid,terrain,new CdMonitor.PrefixSolidOwner(){
            public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut c){requireKind(Kind.MONITOR);identity(c.objectIdentity());poolCut(c.callCut());
                monitorSite(2);
                if(dispatch.solidCut!=null||!c.expectedPlayer().equals(primary())||!c.expectedGates().equals(gates())||!c.expectedGlobals().equals(solidGlobals()))
                    throw new IllegalArgumentException("Stale/repeated actual Monitor Solid preflight");
                requireOriginalTerrain();dispatch.solidCut=c;return liveMonitor();}
            public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall c){requireKind(Kind.MONITOR);claim(c);
                if(c.cut()!=dispatch.solidCut)throw new IllegalArgumentException("Not original preflighted Monitor Solid");
                identity(c.cut().objectIdentity());commitSolid(c);dispatch.solidCut=null;return liveMonitor();}},
            this::angle,(pool,id)->drawMonitor(pool,id),sound->{requireKind(Kind.MONITOR);monitorSite(4);
                if(dispatch.routine!=4||dispatch.fm)throw new IllegalStateException("No next original Monitor FM96 site");
                dispatch.fm=true;sound(new SonicCdGameModule.PastMonitorFm(sound));});
    }

    /** Caller already performed real selector8, levelFrames increment and SpawnObjects. */
    void runWalk(int levelFrames){
        if((phase!=Phase.READY&&phase!=Phase.DONE)||busy()||dispatch!=null||queue!=null)throw new IllegalStateException("R31 pass not idle");
        word(levelFrames);guard();if(frame.paused()||frame.environment().globals().levelFrames()!=levelFrames)
            throw new IllegalArgumentException("Not actual unpaused already-incremented levelFrames");
        var cut=read();var reservedRefs=new ArrayList<CdObjectDrawQueue.CdSlotRef>();for(int w=1;w<32;w++)reservedRefs.add(new CdObjectDrawQueue.CdSlotRef(w));
        queue=new CdObjectDrawQueue(new DrawSlots(),mappings,cut.cpuSat().copy(),reservedRefs);
        phase=Phase.WALKING;walkingThread=Thread.currentThread();
        try{queue.beginPass();for(int w=0;w<128;w++){
                guard();int id=id(w);if(id==0)continue;var kind=kind(w,id);int routine=routine(w);
                dispatch=new Dispatch(kind,w,routine,body(kind));
                try{if(kind!=Kind.PRIMARY)checkpoint();execute(kind,w,levelFrames);
                    if(dispatch.solidCut!=null)throw new IllegalStateException("Unretired original Solid call");}
                finally{dispatch=null;}
            }phase=Phase.WALKED;
        }catch(RuntimeException|Error original){phase=Phase.FAILED;queue.close();throw original;}
        finally{walkingThread=null;dispatch=null;primaryPending=null;pending=null;plcPending=null;primaryDraw=null;}
    }
    CdObjectDrawQueue.CpuSat finishDraw(CdObjectDrawQueue.DrawCameras cameras){
        if(phase!=Phase.WALKED||busy()||dispatch!=null)throw new IllegalStateException("No complete R31 walk to drain");
        Objects.requireNonNull(cameras);guard();var cut=read();
        if(cameras.foreground().xWord()!=cut.cameraXWord()||cameras.foreground().yWord()!=cut.cameraYWord())throw new IllegalArgumentException("Not actual post-scroll foreground camera");
        phase=Phase.DRAINING;walkingThread=Thread.currentThread();
        try{var cpu=queue.finishAfterCamera(cameras);cut=read();publish(cut,copy(cut,cut.core(),cut.scrollLockWord(),CdAssetBytes.copyOf(cpu.bytes()),Optional.of(cpu),cut.sounds()),null,null);
            queue.close();queue=null;phase=Phase.DONE;return cpu;
        }catch(RuntimeException|Error original){phase=Phase.FAILED;queue.close();throw original;}finally{walkingThread=null;}
    }
    void discard(){if(busy()||phase==Phase.WALKING||phase==Phase.DRAINING||dispatch!=null)throw new IllegalStateException("Active R31 pass");if(queue!=null)queue.close();queue=null;phase=Phase.DISCARDED;}
    private boolean busy(){return primaryInvocation!=null||placeCall!=null||checking||reading||adopting||drawing||plc||pending!=null||primaryPending!=null||plcPending!=null||primaryDraw!=null;}
    private Object body(Kind k){return switch(k){case PRIMARY->frame;case HUD->hud;case TITLE->title;case MONITOR->monitor;};}
    private void execute(Kind kind,int w,int levelFrames){var c=read();var e=frame.environment();var g=e.movement().gates();switch(kind){
        case PRIMARY->runPrimary(levelFrames);
        case HUD->hud.runLive(w,new CdReservedHud.LiveGlobals(e.globals().debugCheat(),c.ring().rings(),e.globals().timeAttackMode(),g.timeWarpDirection(),e.globals().timeWarpTimer(),levelFrames,this::vIntLow));
        case TITLE->title.run(w);
        case MONITOR->{var result=monitor.runPast(w,monitorReads(),new CdMonitor.SolidInputs(primary(),gates(),solidGlobals()));
            if(!result.state().equals(placement.pool().state(w))||!result.finalPlayer().equals(primary())||!result.finalGlobals().equals(solidGlobals()))throw new IllegalStateException("Incomplete genuine Monitor return");}
    }}
    private void runPrimary(int levelFrames){
        requireKind(Kind.PRIMARY);int routine=frame.state().motion().routine();
        if(routine==0){var before=read();var placed=placement.capture();var original=frame.runBody(levelFrames);
            if(original.result().boundary()!=CdPlayerBody.Boundary.INIT_RETURN||original.result().display().isPresent()||!original.sounds().isEmpty())
                throw new IllegalStateException("R31 native Init RTS contract changed");requireCut(before);requirePlacement(placed);return;}
        if(routine!=2&&routine!=4)throw stop(Boundary.PRIMARY_ROUTINE,0,routine);
        var before=read();var saved=readFrame();var placed=placement.capture();CdPlayerBody.R31Context context;
        try{reading=true;context=Objects.requireNonNull(ports.primaryContext());requireFrame(saved);requirePlacement(placed);}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(saved);requirePlacement(placed);});throw original;}finally{reading=false;}
        if(context.source()!=frame.stageSource()||context.assets()!=assets||context.currentTerrain()!=before.currentTerrain()
                ||context.cameraXWord()!=before.cameraXWord()||context.cameraYWord()!=before.cameraYWord()
                ||!context.noBumperByte().equals(before.noBumperByte())||!context.response().contact().equals(before.contact())
                ||!context.response().world().equals(before.world()))throw new IllegalArgumentException("Foreign complete R31 primary context");
        poolCut(context.snapshot());requireCut(before);
        primaryInvocation=new PrimaryInvocation(context,placed,before);
        try{frame.runR31Body(levelFrames,context,original->{
            requireKind(Kind.PRIMARY);original.requireOwner(frame);
            var comparison=original.lastPrefix().orElseThrow(()->new IllegalStateException("No actual opt-in last prefix"));
            requirePrefix(comparison,primaryInvocation.current);requirePlacementOutput(placed,comparison.nativeValues().snapshot());
            var current=primaryInvocation.current;
            var completed=original.publication().body().result();
            // BEGIN genuine original pending primary Draw join 20261107.
            var scope=new PrimaryDraw(original,current);primaryDraw=scope;
            try{
                if(scope.required){
                    scope.validate();var selected=primaryEligibility(completed);
                    scope.drawCut=new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),selected.spriteFlags(),
                        selected.centreXWord(),selected.centreYWord(),selected.yRadius(),selected.width(),selected.priority());
                    dispatch.drawn=true;scope.invoking=true;
                    try{draw(scope.drawCut);}finally{scope.invoking=false;}
                    scope.returned=true;if(!scope.clearSeen)throw new IllegalStateException("Actual primary queue omitted native clear");
                }
                var p=new PrimarySubmission(original,current,context,scope.readyState,primaryInvocation.sounds);primaryPending=p;
                try{p.validateBefore();ports.primary(p);if(!p.applied)throw new IllegalStateException("Missing actual R31 primary adoption");
                    requireCut(p.next());if(!frame.state().equals(p.readyState())||!frame.environment().equals(completed.environment())||!frame.display().equals(completed.display()))
                        throw new IllegalStateException("Incomplete original Frame pair adoption");poolCut(original.publication().nativeValues().snapshot());}
                finally{primaryPending=null;}
            }finally{primaryDraw=null;}
            // END genuine original pending primary Draw join 20261107.
        },new CdPlayerFrameRuntime.R31PlaceBlockPort(){
            public void store(CdPlayerFrameRuntime.R31PlaceBlockCall original){publishPlace(original);}
            public void screen(CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){publishPlace(original);}
        });}finally{primaryInvocation=null;}

    }
    // BEGIN original primary source-cut and flag helpers 20261107.
    private CdPlayerBody.DisplayEligibility primaryEligibility(CdPlayerBody.Result actual){
        if(primaryDraw==null||actual!=primaryDraw.original.publication().body().result()||!primaryDraw.required||dispatch.drawn)
            throw new IllegalStateException("No original unconsumed primary display producer");
        CdPlayerBody.DisplayEligibility selected=null;
        for(var value:actual.effects())if(value instanceof CdPlayerBody.DisplayEligibility d){
            if(selected!=null)throw new IllegalStateException("Ambiguous original primary display cut");selected=d;
        }
        if(selected==null||!selected.eligible()||actual.display().isEmpty()||!actual.display().orElseThrow().queued())
            throw new IllegalStateException("Original primary Draw/eligibility contract differs");
        // This authentic original-call data selects the one live queue operation here, never after return.
        return selected;
    }
    private void stagePrimaryClear(){
        var scope=primaryDraw;if(scope==null||!scope.required||!scope.invoking||scope.returned||scope.clearSeen
                ||!drawing||queue==null||queue.phase()!=CdObjectDrawQueue.Phase.COLLECTING||scope.drawCut==null
                ||scope.drawCut.ref().wholeIndex()!=0||!dispatch.drawn)
            throw new IllegalStateException("No actual pending primary DrawObject entry-clear");
        scope.validate();var flags=scope.original.publication().body().result().state().motion().spriteFlags()&127;
        scope.readyState=scope.original.prepareDisplayFlag(flags);scope.clearSeen=true;
        requireFrame(scope.before);requirePlacement(scope.placed);requireCut(scope.cut);
    }
    private static ReadFrame screenFlagFrame(ReadFrame before,boolean value){
        var s=before.state();var m=s.motion();int flags=(m.spriteFlags()&127)|(value?128:0);
        var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
            m.routine(),m.angle(),m.flags(),flags,m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),
            m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var state=new CdPlayerBody.State(motion,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
        var display=before.display().map(p->new CdPlayerBody.DisplayPublication(p.queued(),p.mappingAddress(),p.tile(),p.priority(),p.width(),p.mapFrame(),
            (p.spriteFlags()&127)|(value?128:0),p.centreX16_16(),p.centreY16_16()));
        // Comparison-only full expected data. The actual Frame port, not this record, performs the native store.
        return new ReadFrame(state,before.environment(),display,before.art(),before.clock(),before.paused());
    }
    private void requireScreenFrame(ReadFrame expected){
        var now=readFrame();if(frame.closed()||!now.state().equals(expected.state())||now.environment()!=expected.environment()
                ||!now.display().equals(expected.display())||now.art()!=expected.art()||now.clock()!=expected.clock()||now.paused()!=expected.paused())
            throw new IllegalStateException("Primary drain changed a complete Frame nonwrite");
    }
    private void publishPrimaryScreenFlag(boolean value){
        if(!value||phase!=Phase.DRAINING||queue==null||queue.phase()!=CdObjectDrawQueue.Phase.DRAINING
                ||adopting||pending!=null||primaryPending!=null||primaryDraw!=null||walkingThread!=Thread.currentThread())
            throw new IllegalStateException("No actual primary DrawDone store");
        var cut=read();var a=new Adoption(cut,cut,null,null);a.prepareScreenFlag(value);pending=a;adopting=true;
        try{a.validateBefore();ports.adopt(a);if(!a.applied)throw new IllegalStateException("Original primary drain adoption omitted");
            requireCut(cut);requireScreenFrame(a.screenReady);requirePlacement(a.placed);
        }catch(RuntimeException|Error original){verify(original,()->{requireCut(cut);requirePlacement(a.placed);
                if(a.applied)requireScreenFrame(a.screenReady);else requireFrame(a.before);});throw original;
        }finally{pending=null;adopting=false;}
    }
    // END original primary source-cut and flag helpers 20261107.
    private int id(int w){return w==0?frame.state().header().slotId():w<32?reserved(read(),w).unsignedByte(0):placement.pool().state(w).id();}
    private int routine(int w){return w==0?frame.state().motion().routine():w<32?reserved(read(),w).unsignedByte(0x24):placement.pool().state(w).routine();}
    private Kind kind(int w,int id){if(id<1||id>0x3C)throw stop(Boundary.OBJECT_INDEX,w,id);
        if(w==0){if(id!=1)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.PRIMARY;}
        if(id==0x3C)return Kind.TITLE;
        if(id==0x1C){if(w>=32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.HUD;}
        if(id==0x19){if(w<32)return Kind.HUD;if(placement.pool().state(w).subtype()>=8)throw stop(Boundary.OBJECT_FAMILY,w,id);return Kind.MONITOR;}
        throw stop(Boundary.OBJECT_FAMILY,w,id);
    }

    private void guard(){if(checking||reading)throw new IllegalStateException("Recursive R31 Ports access");
        if(frame.closed()||!frame.stageSource().ownsR31aAssets(assets)||!placement.ownsR31aAssets(assets)||!common.ownsAssets(assets))throw new IllegalStateException("Closed/foreign R31 binding");
        var before=readFrame();var placed=placement.capture();checking=true;
        try{ports.validate(this,assets,frame,placement,common);requireFrame(before);requirePlacement(placed);}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(before);requirePlacement(placed);});throw original;}finally{checking=false;}
    }
    private ReadFrame readFrame(){return new ReadFrame(frame.state(),frame.environment(),frame.display(),frame.artState(),frame.vIntClock(),frame.paused());}
    private void requireFrame(ReadFrame b){if(frame.closed()||frame.state()!=b.state()||frame.environment()!=b.environment()||frame.display()!=b.display()||frame.artState()!=b.art()||frame.vIntClock()!=b.clock()||frame.paused()!=b.paused())throw new IllegalArgumentException("Changed complete Frame preimage");}
    private Cut read(){guard();var before=readFrame();var placed=placement.capture();reading=true;
        try{var c=Objects.requireNonNull(ports.read());requireFrame(before);requirePlacement(placed);if(c.core().phase()!=SonicCdR31aLoadingVisit.Phase.READY)throw new IllegalArgumentException("Actual ordinary Core READY values required");return c;}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(before);requirePlacement(placed);});phase=Phase.FAILED;throw original;}finally{reading=false;}}
    private void requireCut(Cut e){var n=read();if(!sameCut(e,n))throw new IllegalArgumentException("Stale complete Core/Host/global cut");}
    private static boolean sameCut(Cut a,Cut b){return a.core().equals(b.core())&&a.ring().equals(b.ring())&&a.currentTerrain()==b.currentTerrain()
        &&a.contact().equals(b.contact())&&a.world().equals(b.world())&&a.noBumperByte().equals(b.noBumperByte())
        &&a.cameraXWord()==b.cameraXWord()&&a.cameraYWord()==b.cameraYWord()&&a.scrollLockWord()==b.scrollLockWord()
        &&a.layerByte().equals(b.layerByte())&&same(a.cpuSat(),b.cpuSat())&&a.completedCpu().equals(b.completedCpu())&&a.sounds().equals(b.sounds());}
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot e){placement.validateRestore(e);var n=placement.capture();
        if(!e.status().equals(n.status())||!e.pool().slots().equals(n.pool().slots())||!same(e.savedFlags(),n.savedFlags()))throw new IllegalArgumentException("Changed full96/status/saved bank");}
    private void requirePlacementOutput(CdObjectPlacementRuntime.Snapshot before,CdObjectPool.Snapshot output){
        placement.validateRestore(before);placement.pool().validateRestore(output);var actual=placement.capture();
        if(!actual.pool().slots().equals(output.slots())||!actual.status().equals(before.status())||!same(actual.savedFlags(),before.savedFlags()))
            throw new IllegalArgumentException("Pool publication changed placement status/saved nonwrites");
    }
    private void poolCut(CdObjectPool.Snapshot e){placement.pool().validateRestore(e);if(!e.slots().equals(placement.pool().capture().slots()))throw new IllegalArgumentException("Stale full96 native cut");}
    private void requireKind(Kind k){if(phase!=Phase.WALKING||dispatch==null||dispatch.kind!=k||dispatch.body!=body(k)
            ||dispatch.thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()||adopting||plc||drawing)
            throw new IllegalStateException("No original current "+k+" producer");guard();if(dispatch.frame!=null){requireFrame(dispatch.frame);requireCut(dispatch.cut);}}
    private void requireWhole(Kind k,int w){requireKind(k);if(dispatch.whole!=w)throw new IllegalArgumentException("Foreign physical native callback");}
    private void checkpoint(){dispatch.frame=readFrame();dispatch.cut=read();}
    private void claim(Object c){if(c==null||dispatch==null||!dispatch.calls.add(c))throw new IllegalStateException("Repeated/foreign original native call");}
    private void identity(CdObjectState.Identity i){if(dispatch==null||dispatch.whole<32||i.wholeIndex()!=dispatch.whole||!placement.pool().state(dispatch.whole).identity().equals(i))throw new IllegalArgumentException("Foreign current physical source");}
    private void currentAdoption(Adoption a){if(a==null||a!=pending||!adopting||a.applied||a.thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()
            ||phase!=Phase.WALKING&&phase!=Phase.DRAINING)throw new IllegalStateException("No original pending R31 adoption");guard();}
    private void currentPrimary(PrimarySubmission p){if(p==null||p!=primaryPending||p.applied||p.thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()
            ||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.PRIMARY||dispatch.body!=frame||dispatch.whole!=0||adopting||drawing||plc)
            throw new IllegalStateException("No original R31 primary port");guard();}
    private void currentPlc(PlcSubmission p){if(p==null||p!=plcPending||!plc||p.thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()||dispatch==null||dispatch.kind!=Kind.TITLE||p.wholeIndex()!=dispatch.whole||adopting||drawing)throw new IllegalStateException("No original Title PLC invocation");guard();}
    private void publish(Cut e,Cut n,CdObjectPool.Snapshot output,CdPlayerFrameRuntime.ObjectSolidPublication ready){
        if(adopting||pending!=null||primaryPending!=null)throw new IllegalStateException("Recursive R31 publication");var a=new Adoption(e,n,output,ready);pending=a;adopting=true;
        try{a.validateBefore();ports.adopt(a);if(!a.applied)throw new IllegalStateException("Original R31 adoption omitted");requireCut(n);
            if(output!=null)requirePlacementOutput(a.placed,output);if(ready!=null){if(!frame.state().equals(ready.finalState())||!frame.environment().equals(ready.finalEnvironment()))throw new IllegalStateException("Incomplete prepared Frame pair");}
            else requireFrame(a.before);
            if(output==null)requirePlacement(a.placed);if(dispatch!=null)checkpoint();
        }catch(RuntimeException|Error original){verify(original,()->{
                if(!a.applied){requireCut(e);requireFrame(a.before);requirePlacement(a.placed);}
                else{requireCut(n);if(output!=null)requirePlacementOutput(a.placed,output);else requirePlacement(a.placed);
                    if(ready!=null){if(!frame.state().equals(ready.finalState())||!frame.environment().equals(ready.finalEnvironment()))throw new IllegalStateException("Fault changed prepared Frame output");}
                    else requireFrame(a.before);}
            });throw original;
        }finally{pending=null;adopting=false;}
    }
    private void sound(SonicCdGameModule.PastSound s){var c=read();var list=new ArrayList<>(c.sounds());list.add(Objects.requireNonNull(s));publish(c,copy(c,c.core(),c.scrollLockWord(),c.cpuSat(),c.completedCpu(),list),null,null);}
    private CdSolidObject.Player primary(){var s=frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    private CdSolidObject.Gates gates(){return new CdSolidObject.Gates(frame.environment().globals().debugMode(),frame.environment().movement().gates().miniSonic());}
    private CdSolidObject.Globals solidGlobals(){var e=frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    private CdMonitor.LiveSolid liveMonitor(){return new CdMonitor.LiveSolid(primary(),gates(),solidGlobals());}
    private void requireOriginalTerrain(){if(read().currentTerrain()!=originalTerrain)throw stop(Boundary.CURRENT_TERRAIN_OWNER,dispatch.whole,0);}
    private void monitorSite(int routine){var source=placement.pool().state(dispatch.whole);if(source.id()!=0x19||source.routine()!=routine)
        throw new IllegalStateException("Wrong live Monitor routine at native callback");}
    private void angle(int expected,int value){requireKind(Kind.MONITOR);monitorSite(2);if(placement.pool().state(dispatch.whole).unsignedByte(ROUTINE_2)==0)
            throw new IllegalStateException("No original falling Monitor floor-angle site");
        var ready=frame.prepareObjectPrimaryAngle(expected,value);var c=read();publish(c,c,null,ready);
        // The first callback is ObjGetFloorDist's actual angle pre-zero BEFORE terrain.floor.
        // A changed current terrain stops here, retaining gravity and the native pre-zero prefix.
        requireOriginalTerrain();}
    private void commitSolid(CdMonitor.PrefixSolidCall c){poolCut(c.cut().callCut());CdObjectState own;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication ready;
        if(c.outcome() instanceof CdSolidObject.Completed done){var r=done.completedResult();own=r.object();others=r.otherSlots();fm=r.fmRequests();ready=frame.prepareObjectSolidResult(c.cut().expectedPlayer(),c.cut().expectedGlobals(),r);}
        else{var p=((CdSolidObject.NativeStopped)c.outcome()).prefix();own=p.object();others=p.otherSlots();fm=p.fmRequests();ready=frame.prepareObjectSolidPrefix(c.cut().expectedPlayer(),c.cut().expectedGlobals(),p);}
        identity(own.identity());var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);var output=CdObjectPool.overlay(c.cut().callCut(),images);
        var cut=read();var sounds=new ArrayList<>(cut.sounds());for(var s:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(s)));
        publish(cut,copy(cut,cut.core(),cut.scrollLockWord(),cut.cpuSat(),cut.completedCpu(),sounds),output,ready);
    }
    private CdMonitor.PastGlobals monitorReads(){return new CdMonitor.PastGlobals(){
        public int rawTimeZone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeZone();}
        public int rawTimeWarpDirection(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeWarpDirection();}
        public int cameraXWord(){requireKind(Kind.MONITOR);return read().cameraXWord();}
        public int timeAttackMode(){requireKind(Kind.MONITOR);return frame.environment().globals().timeAttackMode();}
        public int zone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().zoneAct()>>>8;}
        public int layer(){requireKind(Kind.MONITOR);return read().layerByte().orElseThrow(()->stop(Boundary.LAYER,dispatch.whole,0));}
        public int timeStopTimer(){requireKind(Kind.MONITOR);return read().ring().timeStopTimer();}};}
    private int vIntLow(){var actual=frame.vIntClock().absoluteValue();if(actual.isEmpty())throw stop(Boundary.VINT,dispatch.whole,0);return(int)actual.getAsLong()&255;}

    private void reservedWrite(Kind k,int w,CdAssetBytes e,CdAssetBytes n){requireWhole(k,w);extent(n,64);var cut=read();if(!same(e,reserved(cut,w)))throw new IllegalArgumentException("Stale selected reserved full64");publish(cut,withReserved(cut,replace(cut.core().reserved(),w,n)),null,null);}
    private void titleSource(CdTitleCard.SourceCut c){if(dispatch==null||dispatch.kind!=Kind.TITLE||c.wholeIndex()!=dispatch.whole)throw new IllegalArgumentException("Foreign Title call cut");requirePlacement(c.actualPlacement());
        var selected=dispatch.whole<32?reserved(read(),dispatch.whole):CdAssetBytes.copyOf(placement.pool().state(dispatch.whole).copyBytes());if(!same(selected,c.expectedSource()))throw new IllegalArgumentException("Stale Title selected64");}
    private int titleRead(CdTitleCard.ReadCall c){requireKind(Kind.TITLE);claim(c);titleSource(c.cut());
        if(c.field()==CdTitleCard.ReadField.ACT_BYTE){if(dispatch.routine!=0||c.cut().expectedSource().unsignedByte(0x24)!=2)throw new IllegalStateException("No original Title Init ACT site");return frame.environment().movement().gates().zoneAct()&255;}
        if(dispatch.routine!=10||c.cut().expectedSource().unsignedByte(0x24)!=10||dispatch.titleOrder!=0)throw new IllegalStateException("No original Title Wait PLC-head site");
        return(int)read().core().section().queue().headSource();}
    private void titleStore(CdTitleCard.StoreCall c){requireKind(Kind.TITLE);claim(c);titleSource(c.cut());var cut=read();
        if(c.cut().expectedSource().unsignedByte(0x24)!=10)throw new IllegalStateException("Wrong live Title store routine");
        if(c.field()==CdTitleCard.StoreField.CONTROL_LOCK_BYTE){if(dispatch.routine!=10||dispatch.titleOrder!=1||c.value()!=0)throw new IllegalStateException("No next original Title control clear");
            var ready=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),c.field(),c.value());publish(cut,cut,null,ready);dispatch.titleOrder=2;}
        else{if(dispatch.titleOrder!=0||dispatch.routine!=(c.value()==1?6:10))throw new IllegalStateException("No next original Title scroll-high store");
            publish(cut,copy(cut,cut.core(),(cut.scrollLockWord()&255)|(c.value()<<8),cut.cpuSat(),cut.completedCpu(),cut.sounds()),null,null);dispatch.titleOrder=1;}}
    private void titlePlc(CdTitleCard.PlcCall c){requireKind(Kind.TITLE);claim(c);titleSource(c.cut());if(c.nativeId()!=2||plcPending!=null||dispatch.routine!=6||dispatch.titleOrder!=1||c.cut().expectedSource().unsignedByte(0x24)!=10)throw new IllegalStateException("Foreign/repeated Title PLC");dispatch.titleOrder=2;var p=new PlcSubmission(c);plcPending=p;plc=true;
        try{p.validateBefore();ports.submitTitlePlc(p);if(!p.acknowledged)throw new IllegalStateException("Actual Core omitted PLC return receipt");requireCut(p.after);requireFrame(p.before);requirePlacement(p.placed);checkpoint();}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(p.before);requirePlacement(p.placed);requireOnlyTitlePlc(p.expected,read(),false);});throw original;}
        finally{plcPending=null;plc=false;}}
    private static void requireOnlyTitlePlc(Cut e,Cut n,boolean completed){var calls=new ArrayList<>(e.core().calls());calls.add(SonicCdR31aLoadingVisit.Call.TITLE_PLC_APPEND);
        if(!n.core().calls().equals(calls)&&(completed||!n.core().calls().equals(e.core().calls())))throw new IllegalArgumentException("Not the actual single Title PLC observation");
        var replaced=withSectionAndCalls(n,e.core().section(),e.core().calls());if(!sameCut(e,replaced))throw new IllegalArgumentException("Title PLC changed non-art owners");}
    private void titleDraw(CdTitleCard.DrawCall c){requireKind(Kind.TITLE);claim(c);titleSource(c.cut());if(dispatch.whole<32)drawReserved(Kind.TITLE,dispatch.whole);else drawDynamic(Kind.TITLE,placement.pool(),placement.pool().state(dispatch.whole).identity());}
    private void drawMonitor(CdObjectPool p,CdObjectState.Identity i){drawDynamic(Kind.MONITOR,p,i);}
    private void drawDynamic(Kind k,CdObjectPool p,CdObjectState.Identity i){requireKind(k);identity(i);if(p!=placement.pool()||dispatch.drawn)throw new IllegalStateException("Foreign/repeated actual Draw");var s=p.state(i.wholeIndex());dispatch.drawn=true;
        draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)));}
    private void drawReserved(Kind k,int w){requireWhole(k,w);if(dispatch.drawn)throw new IllegalStateException("Repeated reserved Draw");var s=reserved(read(),w);dispatch.drawn=true;
        draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(w),s.unsignedByte(1),s.unsignedWord(8),s.unsignedWord(12),s.unsignedByte(22),s.unsignedByte(25),s.unsignedByte(24)));}
    private void draw(CdObjectDrawQueue.DrawCallCut c){if(drawing||adopting)throw new IllegalStateException("Recursive Draw");var cut=read();drawing=true;try{queue.drawObject(c,new CdObjectDrawQueue.CameraWords(cut.cameraXWord(),cut.cameraYWord()));}finally{drawing=false;}}
    private final class DrawSlots implements CdObjectDrawQueue.CdDrawSlots {
        private void current(){if(queue==null||adopting||!drawing&&phase!=Phase.DRAINING||walkingThread!=Thread.currentThread())throw new IllegalStateException("No actual private queue dereference");guard();}
        public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){current();int w=ref.wholeIndex();
            // BEGIN original/live primary queue dereference 20261107.
            if(w==0){
                CdPlayerBody.State s;
                if(phase==Phase.WALKING){var scope=primaryDraw;if(scope==null||!scope.invoking)throw stop(Boundary.PRIMARY_DISPLAY_OWNER,0,frame.state().motion().routine());scope.validate();s=scope.readyState;}
                else{if(phase!=Phase.DRAINING||primaryDraw!=null)throw new IllegalStateException("No actual live primary drain");s=frame.state();}
                var m=s.motion();var h=s.header();return new CdObjectDrawQueue.LiveDrawSlot(h.slotId(),m.spriteFlags(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.centreX16_16()&65535,h.mappingAddress(),s.animation().mapFrame(),h.tile());
            }
            // END original/live primary queue dereference 20261107.
            if(w<32){var r=reserved(SonicCdR31aObjectPass.this.read(),w);return new CdObjectDrawQueue.LiveDrawSlot(r.unsignedByte(0),r.unsignedByte(1),r.unsignedWord(8),r.unsignedWord(12),r.unsignedWord(10),(int)r.unsignedLong(4),r.unsignedByte(26),r.unsignedWord(2));}
            var s=placement.pool().state(w);return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));}
        public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){current();int w=ref.wholeIndex(),bit=value?128:0;var c=SonicCdR31aObjectPass.this.read();
            // BEGIN actual primary native queue flag store 20261107.
            if(w==0){if(phase==Phase.WALKING){if(value)throw new IllegalStateException("Draw entry only clears primary bit7");stagePrimaryClear();}
                else publishPrimaryScreenFlag(value);return;}
            // END actual primary native queue flag store 20261107.
            if(w<32){var raw=reserved(c,w).copy();raw[1]=(byte)((raw[1]&127)|bit);publish(c,withReserved(c,replace(c.core().reserved(),w,CdAssetBytes.copyOf(raw))),null,null);}
            else{var p=placement.pool().capture();var s=placement.pool().state(w);publish(c,c,CdObjectPool.overlay(p,List.of(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|bit))),null);}}
    }

    private static Cut withReserved(Cut c,CdAssetBytes bank){extent(bank,1984);var s=c.core();var next=new SonicCdR31aLoadingVisit.State(s.phase(),s.initialization(),s.section(),s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),bank,s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls(),s.returnKind(),s.music());return copy(c,next,c.scrollLockWord(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut withSectionAndCalls(Cut c,com.openggf.game.soniccd.resources.CdR31aSectionArt.State section,List<SonicCdR31aLoadingVisit.Call> calls){var s=c.core();var next=new SonicCdR31aLoadingVisit.State(s.phase(),s.initialization(),section,s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.reserved(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),calls,s.returnKind(),s.music());return copy(c,next,c.scrollLockWord(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut copy(Cut c,SonicCdR31aLoadingVisit.State core,int scroll,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds){return new Cut(core,c.ring(),c.currentTerrain(),c.contact(),c.world(),c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),scroll,c.layerByte(),cpu,completed,sounds);}
    private static CdAssetBytes reserved(Cut c,int w){if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");return c.core().reserved().slice((w-1)*64,64);}
    private static CdAssetBytes replace(CdAssetBytes bank,int w,CdAssetBytes row){extent(bank,1984);extent(row,64);if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");var next=bank.copy();System.arraycopy(row.copy(),0,next,(w-1)*64,64);return CdAssetBytes.copyOf(next);}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void extent(CdAssetBytes a,int n){if(Objects.requireNonNull(a).length()!=n)throw new IllegalArgumentException("Complete extent "+n+" required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE");}private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD");}
    private static void verify(Throwable original,Runnable check){try{check.run();}catch(RuntimeException|Error fault){if(fault!=original)original.addSuppressed(fault);}}
    private static Unowned stop(Boundary b,int w,int v){return new Unowned(b,w,v);}
}
