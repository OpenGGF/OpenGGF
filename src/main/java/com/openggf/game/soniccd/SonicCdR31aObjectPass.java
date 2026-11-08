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
        default void submitIntroPlc(IntroPlcSubmission original){throw original.unowned();}
    }
    /** Fresh typed actual owners, not a programme token, second bank or captured invocation. */
    record Cut(SonicCdR31aLoadingVisit.State core,CdRingGlobals ring,CdStageTerrain currentTerrain,
               CdPlayerBody.ContactGlobals contact,CdPlayerTouchResponse.WorldGlobals world,OptionalInt noBumperByte,
               int cameraXWord,int cameraYWord,int scrollLockWord,OptionalInt layerByte,
               CdAssetBytes cpuSat,Optional<CdObjectDrawQueue.CpuSat> completedCpu,List<SonicCdGameModule.PastSound> sounds,OptionalInt amyCapturedByte) {
        Cut(SonicCdR31aLoadingVisit.State core,CdRingGlobals ring,CdStageTerrain terrain,CdPlayerBody.ContactGlobals contact,CdPlayerTouchResponse.WorldGlobals world,OptionalInt noBumper,int cx,int cy,int lock,OptionalInt layer,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds){this(core,ring,terrain,contact,world,noBumper,cx,cy,lock,layer,cpu,completed,sounds,OptionalInt.empty());}
        Cut {Objects.requireNonNull(amyCapturedByte);amyCapturedByte.ifPresent(SonicCdR31aObjectPass::octet);Objects.requireNonNull(core);Objects.requireNonNull(ring);Objects.requireNonNull(currentTerrain);
            Objects.requireNonNull(contact);Objects.requireNonNull(world);Objects.requireNonNull(noBumperByte);
            noBumperByte.ifPresent(SonicCdR31aObjectPass::octet);word(cameraXWord);word(cameraYWord);word(scrollLockWord);
            Objects.requireNonNull(layerByte);layerByte.ifPresent(SonicCdR31aObjectPass::octet);extent(cpuSat,640);
            Objects.requireNonNull(completedCpu);sounds=List.copyOf(sounds);}
    }
    enum Boundary { OBJECT_INDEX,OBJECT_FAMILY,PHYSICAL_OWNER,PRIMARY_ROUTINE,PRIMARY_CONTEXT_OWNER,
        PRIMARY_DISPLAY_OWNER,CURRENT_TERRAIN_OWNER,VINT,LAYER,TITLE_PLC_OWNER,INTRO_GLOBAL_OWNER,INTRO_PLC_OWNER,P2_ART_CLOCK_OWNER,P2_ANIMATION_OWNER,P2_PARENT_OWNER,SPRING_CHILD_OWNER }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;private final int wholeIndex,value;
        private Unowned(Boundary b,int w,int v){super("R31 initial "+b+" at whole "+w+" value$"+Integer.toHexString(v));boundary=b;wholeIndex=w;value=v;}
        Boundary boundary(){return boundary;}int wholeIndex(){return wholeIndex;}int value(){return value;}
    }
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,HUD,TITLE,MONITOR,RING,INTRO,METAL,AMY,HEART,EXHAUST,ROLL,CC_SPIKES,BLOCK,HIDDEN,METAL_PLATFORM,DOOR,FIRE,DRUM,GAME_OVER }
    private static final class Dispatch {
        final Kind kind;final int whole,routine;final Object body;final Thread thread=Thread.currentThread();
        final Set<Object> calls=Collections.newSetFromMap(new IdentityHashMap<>());
        ReadFrame frame;Cut cut;boolean drawn,fm;int titleOrder;long gameOverOrdinal;CdGameOver.Result gameOverResult;CdMonitor.PrefixSolidCut solidCut;
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
        private final boolean introPalette=intro!=null&&intro.op==IntroOp.PALETTE;
        private final GameOverOperation gameOverOwner=gameOverOperation;
        private final CdGameOver.ScalarCall gameOverScalar=gameOverOwner!=null&&gameOverOwner.call instanceof CdGameOver.ScalarCall c?c:null;
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
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}Cut expected(){return expected;}Cut next(){return next;}boolean introPalette(){return introPalette;}
        CdGameOver.ScalarCall gameOverScalar(){
            if(gameOverScalar!=null){requireGameOverOperation(gameOverOwner);if(!gameOver.isCurrentPrimitive(gameOverScalar)||!gameOverOwner.used)throw new IllegalStateException("No original Game Over scalar adoption");}
            return gameOverScalar;
        }
        void validateBefore(){currentAdoption(this);
            if(gameOverOwner!=null){requireGameOverOperation(gameOverOwner);if(!gameOverOwner.prepared||!gameOverOwner.used||!gameOver.isCurrentPrimitive(gameOverOwner.call))throw new IllegalStateException("Expired Game Over primitive adoption");}
            requireCut(expected);requireFrame(before);requirePlacement(placed);
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
            if(gameOverOwner!=null){gameOverOwner.current=next;if(poolOutput!=null)gameOverOwner.reached=poolOutput;}
        }
    }
    /** Only the original Frame producer can create this synchronous handoff. */
    final class PrimarySubmission {
        private final CdPlayerFrameRuntime.R31BodyCall original;private final Cut expected,next;
        private final CdPlayerBody.R31Context context;private final CdObjectPlacementRuntime.Snapshot placed;
        private final CdPlayerBody.State readyState;
        private final CdPrimaryLifecycle.Plan lifecycle;private final CdObjectPool.Snapshot poolOutput;
        private final Thread thread=Thread.currentThread();private boolean applied;
        private PrimarySubmission(CdPlayerFrameRuntime.R31BodyCall c,Cut e,CdPlayerBody.R31Context context,CdPlayerBody.State readyState,List<CdPlayerFrameRuntime.Sound> reachedSounds){
            original=c;expected=e;this.context=context;this.readyState=Objects.requireNonNull(readyState);placed=placement.capture();
            var publication=c.publication();var values=publication.nativeValues();var sounds=new ArrayList<>(e.sounds());
            for(var sound:soundSuffix(reachedSounds,publication.body().sounds()))sounds.add(new SonicCdGameModule.PastFrameSound(sound));
            var boundary=publication.body().result().boundary();
            lifecycle=boundary==CdPlayerBody.Boundary.DEAD_RETURN||boundary==CdPlayerBody.Boundary.RESTART_RETURN
                    ?CdPrimaryLifecycle.prepare(publication.body(),values.snapshot(),e.ring(),e.core().level()):null;
            poolOutput=lifecycle==null?values.snapshot():lifecycle.pool();
            var nativeNext=withNativeValues(e,values,sounds);
            if(lifecycle==null)next=nativeNext;
            else{var w=nativeNext.world();var r=lifecycle.ring();next=new Cut(e.core().level(lifecycle.level()),r,nativeNext.currentTerrain(),nativeNext.contact(),new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),w.score(),w.nextLifeScore(),w.updateHudScore()),nativeNext.noBumperByte(),nativeNext.cameraXWord(),nativeNext.cameraYWord(),nativeNext.scrollLockWord(),nativeNext.layerByte(),nativeNext.cpuSat(),nativeNext.completedCpu(),nativeNext.sounds(),nativeNext.amyCapturedByte());}
            placement.pool().validateRestore(poolOutput);
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
            placement.pool().validateRestore(poolOutput);}
        void applyNative(){validateBefore();original.applyNative();placement.pool().restore(poolOutput);if(lifecycle!=null&&lifecycle.resetSaved())placement.resetSavedObjectFlags();applied=true;}
        void requireOutput(){poolCut(poolOutput);}
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
        return new Cut(e.core(),ring,values.terrain(),values.contact(),w,values.noBumperByte(),e.cameraXWord(),e.cameraYWord(),e.scrollLockWord(),e.layerByte(),e.cpuSat(),e.completedCpu(),sounds,e.amyCapturedByte());
    }

    // BEGIN scoped original primary queue operation 20261107.
    private final class PrimaryDraw {
        private final CdPlayerFrameRuntime.R31BodyCall original;private final Cut cut;
        private final ReadFrame before;private final CdObjectPlacementRuntime.Snapshot placed;
        private final Thread thread=Thread.currentThread();private final boolean required;
        private CdPlayerBody.State readyState;private CdObjectDrawQueue.DrawCallCut drawCut;
        private boolean invoking,clearSeen,returned;
        private PrimaryDraw(CdPlayerFrameRuntime.R31BodyCall original,Cut cut,boolean stopped){
            this.original=original;this.cut=cut;before=readFrame();placed=placement.capture();
            var result=original.publication().body().result();readyState=result.state();
            required=!stopped&&result.display().isPresent()&&result.display().orElseThrow().queued();
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
    private final CdReservedHud hud;private final CdTitleCard title;private final CdMonitor monitor;private final CdRing ring;
    private final CdR31IntroAssets introAssets;private final CdIntroSpikes spikes;private final CdAmyRose amy;private final CdMetalSonic metal;private final CdRollTunnel roll;private final CdSpinTunnelTag despawn;
    private final CdR31aSolidsSource solidsSource;private final CdR31SolidHazardAssets solidsData;
    private final CdCcSpikes ccSpikes;private final CdBlock block;private final CdHiddenBlock hidden;private final CdMetalPlatform metalPlatform;private final CdDoor door;private final CdFireShooter fire;private final CdDrumPlatform drum;
    private final CdR31aGameOverSource gameOverSource;private final CdGameOver gameOver;
    private GameOverOperation gameOverOperation;
    private P2Invocation p2;
    private IntroInvocation intro;private IntroPlcSubmission introPlc;
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
        ring=CdRing.fromR31aAssets(placement,assets,common);
        gameOverSource=CdR31aGameOverSource.fromAssets(assets);
        gameOver=CdGameOver.withOriginalOperations(placement,gameOverSource.data(),this::gameOverDraw,this::gameOverSound,
            new CdGameOver.DispatchObserver(){
                public void beforeOperation(Object call){beforeGameOver(call);}
                public void afterOperation(Object call){afterGameOver(call);}
                public void failedOperation(Object call,Throwable original){failedGameOver(call);}
            },new CdGameOver.ReceiptGuard(){
                public void prepared(Object call){var op=currentGameOver(call);verifyGameOver(op,false);op.prepared=true;}
                public void completed(Object call){var op=currentGameOver(call);verifyGameOver(op,true);if(!op.after)throw new IllegalStateException("Missing original Game Over result");checkpoint();gameOverOperation=null;}
            });
        introAssets=CdR31IntroAssets.fromAssets(assets);spikes=new CdIntroSpikes(introAssets);amy=new CdAmyRose(introAssets);metal=new CdMetalSonic(introAssets);roll=new CdRollTunnel(CdRollTunnel.Assets.fromAssets(introAssets));despawn=new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(introAssets.rollMappings()));
        solidsSource=CdR31aSolidsSource.fromAssets(assets);solidsData=solidsSource.data();ccSpikes=new CdCcSpikes(solidsData);block=new CdBlock(solidsData);hidden=new CdHiddenBlock(solidsData);metalPlatform=new CdMetalPlatform(solidsData);door=new CdDoor(solidsData);fire=new CdFireShooter(solidsData);drum=new CdDrumPlatform(solidsData);
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
        finally{walkingThread=null;dispatch=null;primaryPending=null;pending=null;plcPending=null;primaryDraw=null;intro=null;introPlc=null;gameOverOperation=null;}
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
    private boolean busy(){return gameOverOperation!=null||p2!=null||intro!=null||introPlc!=null||primaryInvocation!=null||placeCall!=null||checking||reading||adopting||drawing||plc||pending!=null||primaryPending!=null||plcPending!=null||primaryDraw!=null;}
    private Object body(Kind k){return switch(k){case PRIMARY->frame;case HUD->hud;case TITLE->title;case MONITOR->monitor;case RING->ring;case INTRO->spikes;case METAL,EXHAUST->metal;case AMY,HEART->amy;case ROLL->roll;case CC_SPIKES->ccSpikes;case BLOCK->block;case HIDDEN->hidden;case METAL_PLATFORM->metalPlatform;case DOOR->door;case FIRE->fire;case DRUM->drum;case GAME_OVER->gameOver;};}
    private void execute(Kind kind,int w,int levelFrames){var c=read();var e=frame.environment();var g=e.movement().gates();switch(kind){
        case PRIMARY->runPrimary(levelFrames);
        case GAME_OVER->{var result=gameOver.run(w,new CdGameOver.GlobalsPort(){
            public CdGameOver.Globals read(){var op=gameOverPrimitive(CdGameOver.ReadCall.class);var current=op.current;return new CdGameOver.Globals(current.ring().lives(),current.core().level().powerup(),current.core().level().timeOver());}
            public void writePowerup(int value){gameOverStore(CdGameOver.Scalar.POWERUP,value);}
            public void writeTimeOver(int value){gameOverStore(CdGameOver.Scalar.TIME_OVER,value);}
        });if(result!=dispatch.gameOverResult||!result.state().equals(placement.pool().state(w))||gameOverOperation!=null)throw new IllegalStateException("No original Game Over completion");}
        case CC_SPIKES,BLOCK,HIDDEN,METAL_PLATFORM,DOOR,FIRE,DRUM->runP2(kind,w);
        case INTRO,METAL,AMY,HEART,EXHAUST,ROLL->runIntro(kind,w);
        case HUD->hud.runLive(w,new CdReservedHud.LiveGlobals(e.globals().debugCheat(),c.ring().rings(),e.globals().timeAttackMode(),g.timeWarpDirection(),e.globals().timeWarpTimer(),levelFrames,this::vIntLow));
        case TITLE->title.run(w);
        case RING->ring.executeSelected(w,new CdRing.Inputs(assets.identity().variant(),assets.identity().stage(),g.zoneAct()>>>8,c.cameraXWord(),g.timeZone(),g.timeWarpDirection()),
            new CdRing.GlobalsPort(){
                public CdRingGlobals read(){requireKind(Kind.RING);return SonicCdR31aObjectPass.this.read().ring();}
                public void write(CdRingGlobals next){requireKind(Kind.RING);var old=SonicCdR31aObjectPass.this.read();var world=old.world();
                    var values=new CdPlayerTouchResponse.WorldGlobals(next.rings(),next.lives(),next.updateHudLives(),world.score(),world.nextLifeScore(),world.updateHudScore());
                    publish(old,new Cut(old.core(),next,old.currentTerrain(),old.contact(),values,old.noBumperByte(),old.cameraXWord(),old.cameraYWord(),old.scrollLockWord(),old.layerByte(),old.cpuSat(),old.completedCpu(),old.sounds(),old.amyCapturedByte()),null,null);}
            },new CdRing.SourceCalls(){
                public void playFmSound(CdRing.FmSound s){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingFm(s));}
                public void subCpuCommand(CdRing.SubCpuCommand s){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingSubCpu(s));}
                public void drawObject(CdObjectPool p,int whole){requireWhole(Kind.RING,whole);drawDynamic(Kind.RING,p,p.state(whole).identity());}
            });
        case MONITOR->{var result=monitor.runPast(w,monitorReads(),new CdMonitor.SolidInputs(primary(),gates(),solidGlobals()));
            if(!result.state().equals(placement.pool().state(w))||!result.finalPlayer().equals(primary())||!result.finalGlobals().equals(solidGlobals()))throw new IllegalStateException("Incomplete genuine Monitor return");}
    }}
    /** Private producer/cursor scope. Public DTO equality never admits an operation. Pool values
     * compare complete96/status/saved; Frame compares the complete pair plus display/art/clock.
     * Same-value/change-and-restore interference is outside this synchronous value-CAS contract. */
    private final class GameOverOperation {
        final Object call;final long ordinal;final Thread thread=Thread.currentThread();
        final CdObjectPlacementRuntime.Snapshot placed;final ReadFrame frameBefore;
        final CdObjectPool.Snapshot output;Cut current;CdObjectPool.Snapshot reached;
        boolean prepared,used,after;
        GameOverOperation(Object call,CdObjectPlacementRuntime.Snapshot placed){
            this.call=call;this.placed=placed;ordinal=gameOver.currentOrdinal(call);frameBefore=readFrame();current=read();reached=placed.pool();
            if(call instanceof CdGameOver.Publication c){
                if(c.before()!=placement.pool().state(c.before().identity().wholeIndex()))throw new IllegalArgumentException("Stale original Game Over store");
                if(c.output()==null){var staging=CdObjectPool.stagingCopy(placed.pool());staging.deleteObject(dispatch.whole);output=staging.capture();}
                else output=CdObjectPool.overlay(placed.pool(),List.of(c.output()));
            }else output=null;
        }
    }
    private void requireGameOverOperation(GameOverOperation op){
        if(op==null||op!=gameOverOperation||op.thread!=Thread.currentThread()||walkingThread!=Thread.currentThread()
                ||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.GAME_OVER||dispatch.body!=gameOver
                ||!gameOverSource.owns(assets)||!gameOver.isCurrentCall(op.call)||gameOver.currentOrdinal(op.call)!=op.ordinal
                ||dispatch.gameOverOrdinal!=op.ordinal)throw new IllegalStateException("No private original Game Over operation");
        guard();
    }
    private GameOverOperation currentGameOver(Object call){var op=gameOverOperation;requireGameOverOperation(op);if(op.call!=call)throw new IllegalArgumentException("Foreign Game Over operation");return op;}
    private void verifyGameOver(GameOverOperation op,boolean returned){
        requireGameOverOperation(op);requireFrame(op.frameBefore);requireCut(op.current);
        requirePlacementOutput(op.placed,returned&&op.output!=null?op.output:op.reached);
    }
    private void beforeGameOver(Object call){
        requireKind(Kind.GAME_OVER);if(gameOverOperation!=null||!gameOver.isCurrentCall(call)
                ||gameOver.currentOrdinal(call)!=dispatch.gameOverOrdinal+1)throw new IllegalStateException("Game Over operation reentry/cursor");
        CdObjectPlacementRuntime.Snapshot cut;CdObjectState source;
        if(call instanceof CdGameOver.Publication c){cut=c.cut();source=c.source();}
        else if(call instanceof CdGameOver.ReadCall c){cut=c.cut();source=c.source();}
        else if(call instanceof CdGameOver.ScalarCall c){cut=c.cut();source=c.source();}
        else if(call instanceof CdGameOver.SoundCall c){cut=c.cut();source=c.source();}
        else if(call instanceof CdGameOver.DrawCall c){cut=c.cut();source=c.source();}
        else if(call instanceof CdGameOver.CompletionCall c){cut=c.cut();source=c.source();}
        else throw new IllegalArgumentException("Unknown Game Over native site");
        requirePlacement(cut);if(source!=placement.pool().state(dispatch.whole))throw new IllegalArgumentException("Foreign original Game Over source64");
        claim(call);var ready=new GameOverOperation(call,cut);dispatch.gameOverOrdinal=ready.ordinal;gameOverOperation=ready;verifyGameOver(ready,false);
    }
    private <T>GameOverOperation gameOverPrimitive(Class<T> type){
        var op=gameOverOperation;requireGameOverOperation(op);if(!type.isInstance(op.call)||!op.prepared||op.used||!gameOver.isCurrentPrimitive(op.call))throw new IllegalStateException("Wrong/repeated original Game Over primitive");
        verifyGameOver(op,false);op.used=true;return op;
    }
    private void gameOverStore(CdGameOver.Scalar field,int value){
        var op=gameOverPrimitive(CdGameOver.ScalarCall.class);var call=(CdGameOver.ScalarCall)op.call;
        if(call.field()!=field||call.value()!=value)throw new IllegalArgumentException("Different original Game Over scalar");
        var c=op.current;publish(c,copy(c,c.core().level(gameOverLevel(c.core().level(),call)),c.scrollLockWord(),c.cpuSat(),c.completedCpu(),c.sounds()),null,null);
    }
    static CdLevelGlobals gameOverLevel(CdLevelGlobals g,CdGameOver.ScalarCall call){
        return new CdLevelGlobals(g.levelRestart(),g.updateHudTime(),g.checkpoint(),g.spawnMode(),g.plcLoadFlags(),g.flowerCount(),g.fadeEnableDisplay(),call.field()==CdGameOver.Scalar.POWERUP?call.value():g.powerup(),call.field()==CdGameOver.Scalar.TIME_OVER?call.value():g.timeOver());
    }
    private void gameOverSound(CdGameOver.SubCpuCommand command){var op=gameOverPrimitive(CdGameOver.SoundCall.class);if(((CdGameOver.SoundCall)op.call).command()!=command)throw new IllegalArgumentException("Different original Game Over command");sound(new SonicCdGameModule.PastGameOverSubCpu(command));}
    private void gameOverDraw(CdObjectPool pool,CdObjectState.Identity identity){var op=gameOverPrimitive(CdGameOver.DrawCall.class);if(((CdGameOver.DrawCall)op.call).source().identity()!=identity)throw new IllegalArgumentException("Foreign original Game Over Draw identity");drawDynamic(Kind.GAME_OVER,pool,identity);}
    private void afterGameOver(Object call){var op=currentGameOver(call);verifyGameOver(op,true);
        if(call instanceof CdGameOver.Publication){op.reached=op.output;op.used=true;}
        else if(call instanceof CdGameOver.CompletionCall){dispatch.gameOverResult=(CdGameOver.Result)gameOver.currentResult(call);op.used=true;}
        if(!op.prepared||!op.used||op.after)throw new IllegalStateException("Missing/repeated Game Over primitive return");op.after=true;
    }
    private void failedGameOver(Object call){var op=gameOverOperation;if(op==null)return;
        try{currentGameOver(call);requireFrame(op.frameBefore);requireCut(op.current);
            // The original pool primitive may have returned before a subsequent observer fault.
            var actual=placement.pool().capture();if(op.output!=null&&actual.slots().equals(op.output.slots()))requirePlacementOutput(op.placed,op.output);
            else requirePlacementOutput(op.placed,op.reached);
        }finally{gameOverOperation=null;}
    }
    private void runPrimary(int levelFrames){
        requireKind(Kind.PRIMARY);int routine=frame.state().motion().routine();
        if(routine==0){var before=read();var placed=placement.capture();var original=frame.runBody(levelFrames);
            if(original.result().boundary()!=CdPlayerBody.Boundary.INIT_RETURN||original.result().display().isPresent()||!original.sounds().isEmpty())
                throw new IllegalStateException("R31 native Init RTS contract changed");requireCut(before);requirePlacement(placed);return;}
        if(routine!=2&&routine!=4&&routine!=6&&routine!=8)throw stop(Boundary.PRIMARY_ROUTINE,0,routine);
        var before=read();var saved=readFrame();var placed=placement.capture();CdPlayerBody.R31Context context;
        try{reading=true;context=Objects.requireNonNull(ports.primaryContext());requireFrame(saved);requirePlacement(placed);}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(saved);requirePlacement(placed);});throw original;}finally{reading=false;}
        if(context.source()!=frame.stageSource()||context.assets()!=assets||context.currentTerrain()!=before.currentTerrain()
                ||context.cameraXWord()!=before.cameraXWord()||context.cameraYWord()!=before.cameraYWord()
                ||!context.noBumperByte().equals(before.noBumperByte())||!context.response().contact().equals(before.contact())
                ||!context.response().world().equals(before.world()))throw new IllegalArgumentException("Foreign complete R31 primary context");
        poolCut(context.snapshot());requireCut(before);
        primaryInvocation=new PrimaryInvocation(context,placed,before);
        try{CdPlayerFrameRuntime.R31PublicationPort publish=original->{
            // Native unowned alias is reached before the Body's tail Draw. Capture its original
            // cause BEFORE any fresh read, pool/Frame/Core preparation, queue or publication.
            var completed=original.publication().body().result();
            var nativeStop=CdPrimaryLifecycle.aliasReached(completed)?new CdWorld.Unowned(CdWorld.Boundary.PRIMARY_DEATH_ALIAS):null;
            try{
            requireKind(Kind.PRIMARY);original.requireOwner(frame);
            var comparison=original.lastPrefix().orElseThrow(()->new IllegalStateException("No actual opt-in last prefix"));
            requirePrefix(comparison,primaryInvocation.current);requirePlacementOutput(placed,comparison.nativeValues().snapshot());
            var current=primaryInvocation.current;
            // BEGIN genuine original pending primary Draw join 20261107.
            var scope=new PrimaryDraw(original,current,nativeStop!=null);primaryDraw=scope;
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
                        throw new IllegalStateException("Incomplete original Frame pair adoption");p.requireOutput();}
                finally{primaryPending=null;}
            }finally{primaryDraw=null;}
            // END genuine original pending primary Draw join 20261107.
            }catch(RuntimeException|Error preparation){if(nativeStop==null)throw preparation;if(preparation!=nativeStop)nativeStop.addSuppressed(preparation);throw nativeStop;}
            if(nativeStop!=null)throw nativeStop;
        };
        if(routine==6||routine==8)frame.runR31Lifecycle(levelFrames,context,new CdPlayerBody.LevelContext(context.snapshot(),before.ring(),before.core().level()),publish);
        else frame.runR31Body(levelFrames,context,publish,new CdPlayerFrameRuntime.R31PlaceBlockPort(){
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
    private enum P2Op { NONE,PREFIX,READ,CONTACT,REWIND,HURT,LAUNCH,SINE,FM,DRAW,DESPAWN,PROJECTILE,PARENT_X,PARENT_Y,ANIMATE }
    /** Original current cached-body stack only. Staging/results are never an ingress token. */
    private final class P2Invocation {
        final Kind kind;final int whole,routine;final Object producer;final Thread thread=Thread.currentThread();
        final CdObjectPool staging=CdObjectPool.stagingCopy(placement.pool().capture());
        CdObjectPlacementRuntime.Snapshot live=placement.capture();P2Op op=P2Op.NONE;
        int contacts,lastD0,hurtRead,parentReads,fm,launch;boolean rewind,hurt,portFailed;int lastPeriod;
        P2Invocation(Kind kind,int whole){this.kind=kind;this.whole=whole;routine=dispatch.routine;producer=body(kind);}
    }
    private void requireP2(){
        if(p2==null||dispatch==null||p2.thread!=Thread.currentThread()||p2.producer!=dispatch.body||p2.producer!=body(p2.kind)
                ||p2.whole!=dispatch.whole||!solidsSource.owns(assets)||solidsSource.data()!=solidsData)
            throw new IllegalStateException("No original cached P2 invocation");
        requireKind(p2.kind);requirePlacement(p2.live);
    }
    private void p2Sync(){p2.live=placement.capture();p2.staging.restore(p2.live.pool());checkpoint();}
    /** Publish only native stores reached in this still-current original invocation. */
    private void p2Flush(){
        requireP2();var output=p2.staging.capture();placement.pool().validateRestore(output);
        if(!output.slots().equals(p2.live.pool().slots())){var cut=read();publish(cut,cut,output,null);}
        p2.live=placement.capture();
    }
    private <T>T p2Call(P2Op site,CdObjectPool staging,int whole,java.util.function.Supplier<T> action){
        requireP2();if(p2.op!=P2Op.NONE||staging!=p2.staging||whole!=p2.whole)throw new IllegalStateException("Foreign/reentrant P2 site/pool/whole");
        p2.op=site;
        try{p2Flush();return action.get();}catch(RuntimeException|Error original){p2.portFailed=true;throw original;}finally{p2.op=P2Op.NONE;}
    }
    private <T>T p2Call(P2Op site,java.util.function.Supplier<T> action){
        if(p2==null)throw new IllegalStateException("No P2 scalar producer");return p2Call(site,p2.staging,p2.whole,action);
    }
    private int p2Read(String field){return p2Call(P2Op.READ,()->{
        var f=readFrame();var placed=placement.capture();var cut=read();var e=frame.environment();var m=frame.state().motion();int v;
        switch(field){
            case "ACT"->{if(p2.kind!=Kind.CC_SPIKES||p2.routine!=0)throw new IllegalStateException("No Spikes Init act read");v=e.movement().gates().zoneAct()&255;}
            case "PERIOD"->{if(p2.kind!=Kind.BLOCK&&p2.kind!=Kind.FIRE&&p2.kind!=Kind.DRUM)throw new IllegalStateException("No P2 period site");v=e.movement().gates().timeZone();p2.lastPeriod=v;}
            case "GOOD"->{if(p2.kind!=Kind.FIRE||p2.lastPeriod==0||p2.lastPeriod==1)throw new IllegalStateException("No reached Future fire gate");v=cut.core().program().goodFutureByte();}
            case "CAMERA"->v=cut.cameraXWord();
            case "ART"->{if(p2.kind!=Kind.DRUM)throw new IllegalStateException("No Drum art clock site");throw stop(Boundary.P2_ART_CLOCK_OWNER,p2.whole,0);}
            case "X"->{if(p2.kind!=Kind.HIDDEN&&p2.kind!=Kind.FIRE)throw new IllegalStateException("No P2 player X site");v=m.centreX16_16()>>>16;}
            case "Y"->{if(p2.kind!=Kind.DOOR)throw new IllegalStateException("No Door player Y site");v=m.centreY16_16()>>>16;}
            default->{
                var source=p2.staging.state(p2.whole);
                if(p2.kind!=Kind.CC_SPIKES||p2.lastD0==0||source.unsignedByte(MAP_FRAME)!=0||(source.flags()&8)==0)
                    throw new IllegalStateException("No original Spikes Hurt guard");
                String[] order={"WARP","INV","ROUTINE","HURT"};if(p2.hurtRead>=order.length||!field.equals(order[p2.hurtRead++]))throw new IllegalStateException("Wrong Spikes guard order");
                v=switch(field){case "WARP"->cut.contact().timeWarp();case "INV"->e.globals().invincible();case "ROUTINE"->m.routine();case "HURT"->frame.state().timers().hurt();default->throw new IllegalStateException("Unknown P2 read");};
            }
        }
        requireFrame(f);requirePlacement(placed);requireCut(cut);return v;
    });}
    private final CdCcObjectServices p2Services=new CdCcObjectServices(){
        public int act(){return p2Read("ACT");}public int timeZone(){return p2Read("PERIOD");}public int goodFuture(){return p2Read("GOOD");}
        public int cameraX(){return p2Read("CAMERA");}public int animationArtTimer(){return p2Read("ART");}
        public int primaryX(){return p2Read("X");}public int primaryY(){return p2Read("Y");}
        public int timeWarp(){return p2Read("WARP");}public int invincible(){return p2Read("INV");}
        public int primaryRoutine(){return p2Read("ROUTINE");}public int primaryHurt(){return p2Read("HURT");}
        public int contact(CdObjectPool staging,int whole,CdSolidObject.Entry entry){return p2Call(P2Op.CONTACT,staging,whole,()->p2Contact(entry));}
        public void rewindPrimaryYBeforeHurt(){p2Call(P2Op.REWIND,()->{
            if(p2.kind!=Kind.CC_SPIKES||p2.hurtRead!=4||p2.rewind)throw new IllegalStateException("No original Spikes Y LONG rewind");
            var state=frame.state();var env=frame.environment();var m=state.motion();var ready=frame.prepareObjectIntroYRewind(state,env,m.centreY16_16()-((short)m.yVelocity()<<8));
            var cut=read();publish(cut,cut,null,ready);p2.rewind=true;p2Sync();return null;
        });}
        public void hurtPrimary(CdObjectPool staging,int whole){p2Call(P2Op.HURT,staging,whole,()->{
            if(p2.kind!=Kind.CC_SPIKES||!p2.rewind||p2.hurt)throw new IllegalStateException("No original Spikes direct Hurt");p2.hurt=true;p2Hurt();return null;
        });}
        public void launchPrimaryFromFlame(){p2Call(P2Op.LAUNCH,()->{
            var source=p2.staging.state(p2.whole);if(p2.kind!=Kind.FIRE||source.subtype()!=1||source.routine()!=4||p2.lastD0==0||p2.launch++!=0)throw new IllegalStateException("No original same-primary flame launch");
            var ready=frame.prepareObjectCcFlameLaunch(frame.state(),frame.environment());var cut=read();publish(cut,cut,null,ready);p2Sync();return null;
        });}
        public int sine(int angle){return p2Call(P2Op.SINE,()->{if(p2.kind!=Kind.METAL_PLATFORM)throw new IllegalStateException("No Metal bob sine site");return introAssets.sine(angle);});}
        public void playFm(int id){p2Call(P2Op.FM,()->{if(p2.kind!=Kind.CC_SPIKES||id!=0xB7||p2.fm++!=0||(p2.staging.state(p2.whole).spriteFlags()&128)==0)throw new IllegalStateException("No original Spikes FM B7");sound(new SonicCdGameModule.PastCcFm(id));p2Sync();return null;});}
        public void draw(CdObjectPool staging,int whole){p2Call(P2Op.DRAW,staging,whole,()->{drawDynamic(p2.kind,placement.pool(),placement.pool().state(whole).identity());p2Sync();return null;});}
        public void despawnR3(CdObjectPool staging,int whole){p2Call(P2Op.DESPAWN,staging,whole,()->{p2Despawn(false);return null;});}
        public void checkProjectileDespawn(CdObjectPool staging,int whole){p2Call(P2Op.PROJECTILE,staging,whole,()->{
            if(p2.kind!=Kind.FIRE)throw new IllegalStateException("No original projectile despawn");p2Despawn(true);return null;
        });}
        public int parentX(int raw,CdObjectPool staging){return p2Call(P2Op.PARENT_X,staging,p2.whole,()->p2Parent(raw,false));}
        public int parentY(int raw,CdObjectPool staging){return p2Call(P2Op.PARENT_Y,staging,p2.whole,()->p2Parent(raw,true));}
        public void animate(CdObjectPool staging,int whole,CdObjectAnimation.AnimationTable table){p2Call(P2Op.ANIMATE,staging,whole,()->{
            if(p2.kind!=Kind.FIRE||table!=solidsData.fireAnimation())throw new IllegalArgumentException("Foreign Fire selected animation");
            // The released pure owner has no original stopped-prefix publication port. A completed
            // State is not authority to retry/replay its writes. Do not clone the pending owner.
            throw stop(Boundary.P2_ANIMATION_OWNER,whole,p2.staging.state(whole).unsignedByte(ANIMATION));
        });}
    };
    private int p2Parent(int raw,boolean y){
        var source=p2.staging.state(p2.whole);
        if(p2.kind!=Kind.CC_SPIKES||!dispatch.drawn||raw!=source.word(SWING_MAX)||p2.parentReads!=(y?1:0))throw new IllegalStateException("No next original post-Draw parent read");
        p2.parentReads++;int at=(short)raw+(y?12:8)-(int)0xFFFFD800;
        if(at<0||at+1>=96*64)throw stop(Boundary.P2_PARENT_OWNER,p2.whole,raw);
        int first=Byte.toUnsignedInt(p2.staging.state(32+at/64).copyBytes()[at%64]);at++;
        return(first<<8)|Byte.toUnsignedInt(p2.staging.state(32+at/64).copyBytes()[at%64]);
    }
    private int p2Contact(CdSolidObject.Entry entry){
        boolean top=p2.kind==Kind.METAL_PLATFORM||p2.kind==Kind.DOOR||p2.kind==Kind.DRUM;
        if(entry!=(top?CdSolidObject.Entry.TOP_SOLID_OBJECT:CdSolidObject.Entry.SOLID_OBJECT)
                &&!(p2.kind==Kind.DRUM&&entry==CdSolidObject.Entry.GET_OFF_OBJECT))throw new IllegalArgumentException("Wrong native P2 contact entry");
        if(++p2.contacts>(p2.kind==Kind.METAL_PLATFORM||p2.kind==Kind.DRUM?2:1))throw new IllegalStateException("Repeated native P2 contact site");
        if(entry!=CdSolidObject.Entry.GET_OFF_OBJECT&&read().currentTerrain()!=originalTerrain)throw stop(Boundary.CURRENT_TERRAIN_OWNER,p2.whole,entry.ordinal());
        var state=frame.state();var env=frame.environment();var player=primary();var global=solidGlobals();var cut=read();var pool=p2.staging.capture();
        CdSolidObject.SelectedOutcome selected=null;CdSolidObject.PlayerSelection selection=null;CdSolidObject.Outcome outcome;
        if(entry==CdSolidObject.Entry.TOP_SOLID_OBJECT){
            selection=new CdSolidObject.PlayerSelection(CdSolidObject.PlayerSlot.PRIMARY,reserved(cut,1).unsignedByte(0x22));
            selected=solid.runWithPrefix(entry,p2.staging.state(p2.whole),player,gates(),global,pool,selection);outcome=selected.outcome();
        }else outcome=solid.runWithPrefix(entry,p2.staging.state(p2.whole),player,gates(),global,pool);
        // Capture the native cause FIRST. ALL later prefix preparation/read/publication failures
        // are subordinate to it; an unprepared prefix is never reported as adopted.
        if(outcome instanceof CdSolidObject.NativeStopped stopped){
            var original=stopped.stop().originalException();final var actualSelected=selected;final var actualSelection=selection;
            verify(original,()->publishP2Contact(state,env,player,global,cut,pool,actualSelection,actualSelected,outcome));throw original;
        }
        return publishP2Contact(state,env,player,global,cut,pool,selection,selected,outcome);
    }
    private int publishP2Contact(CdPlayerBody.State state,CdPlayerBody.Environment env,CdSolidObject.Player player,
            CdSolidObject.Globals global,Cut cut,CdObjectPool.Snapshot pool,CdSolidObject.PlayerSelection selection,
            CdSolidObject.SelectedOutcome selected,CdSolidObject.Outcome outcome){
        requireP2();requireCut(cut);var next=cut;CdPlayerFrameRuntime.ObjectSolidPublication ready;
        if(selected!=null){
            ready=frame.prepareObjectSelectedSolid(state,env,player,selection,selected);
            if(selected.counterpart().stored()){byte[] raw=reserved(cut,1).copy();raw[0x22]=(byte)selected.counterpart().value();next=withReserved(cut,replace(cut.core().reserved(),1,CdAssetBytes.copyOf(raw)));}
        }else ready=outcome instanceof CdSolidObject.Completed done?frame.prepareObjectSolidResult(player,global,done.completedResult()):frame.prepareObjectSolidPrefix(player,global,((CdSolidObject.NativeStopped)outcome).prefix());
        CdObjectState own;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;int d0=0;
        if(outcome instanceof CdSolidObject.Completed done){var result=done.completedResult();own=result.object();others=result.otherSlots();fm=result.fmRequests();d0=result.d0().orElse(0);}
        else{var prefix=((CdSolidObject.NativeStopped)outcome).prefix();own=prefix.object();others=prefix.otherSlots();fm=prefix.fmRequests();}
        var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);var output=CdObjectPool.overlay(pool,images);p2.staging.restore(output);
        var sounds=new ArrayList<>(next.sounds());for(var sound:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(sound)));
        var publication=copy(next,next.core(),next.scrollLockWord(),next.cpuSat(),next.completedCpu(),sounds);
        publish(cut,publication,output,ready);p2Sync();p2.lastD0=d0;return d0;
    }
    private void p2Hurt(){
        var cut=read();var e=frame.environment();var g=e.movement();var globals=new CdPlayerTouchResponse.PlayerGlobals(cut.contact().timeWarp(),e.globals().invincible(),cut.contact().shield(),cut.contact().combineRing(),e.globals().debugCheat(),e.globals().debugMode(),g.globals().scoreChain(),g.gates().miniSonic());
        var actor=primary().actor();var pool=p2.staging.capture();var result=CdPlayerTouchResponse.hurtPlayer(actor,globals,cut.world(),p2.staging.state(p2.whole),pool);
        var ready=frame.prepareObjectHurtResult(actor,globals,result);var output=CdObjectPool.overlay(pool,result.changedSlots());p2.staging.restore(output);
        var sounds=new ArrayList<>(cut.sounds());for(var event:result.calls())if(event instanceof CdPlayerTouchResponse.PlayFm f)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(f.sound())));
        var r=cut.ring();var w=result.world();var ring=new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());
        var next=new Cut(cut.core(),ring,cut.currentTerrain(),new CdPlayerBody.ContactGlobals(result.player().timeWarp(),result.player().shield(),result.player().combineRing()),w,cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),sounds,cut.amyCapturedByte());
        publish(cut,next,output,ready);p2Sync();
    }
    private void p2Despawn(boolean projectile){
        var source=p2.staging.state(p2.whole);int id=source.savedFlagsId(),period=frame.environment().movement().gates().timeZone();
        if(projectile){var g=frame.environment().movement().gates();var actual=despawn.despawn(source,new CdSpinTunnelTag.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),read().cameraXWord()));
            if(!actual.deleteObject())return;id=actual.loadedClear().map(CdSpinTunnelTag.LoadedClear::savedId).orElse(0);period=actual.loadedClear().map(CdSpinTunnelTag.LoadedClear::period).orElse(0);}
        if(id!=0)placement.savedFlag(id,period); // native reached bank validation BEFORE deletion
        var next=CdObjectPool.stagingCopy(p2.staging.capture());next.deleteObject(p2.whole);var output=next.capture();placement.pool().validateRestore(output);p2.staging.restore(output);
        requireP2();if(id!=0)placement.bclrSavedFlag(id,period,CdObjectPlacementRuntime.SavedBit.LOADED);placement.pool().restore(output);p2Sync();
    }
    private void runP2(Kind kind,int whole){
        if(p2!=null)throw new IllegalStateException("P2 reentry");p2=new P2Invocation(kind,whole);
        try{switch(kind){case CC_SPIKES->ccSpikes.run(p2.staging,whole,p2Services);case BLOCK->block.run(p2.staging,whole,p2Services);case HIDDEN->hidden.run(p2.staging,whole,p2Services);case METAL_PLATFORM->metalPlatform.run(p2.staging,whole,p2Services);case DOOR->door.run(p2.staging,whole,p2Services);case FIRE->fire.run(p2.staging,whole,p2Services);case DRUM->drum.run(p2.staging,whole,p2Services);default->throw new IllegalStateException("Not P2");}
            p2.op=P2Op.PREFIX;p2Flush();
        }catch(RuntimeException|Error original){if(!p2.portFailed)verify(original,()->{p2.op=P2Op.PREFIX;p2Flush();});throw original;}
        finally{p2=null;}
    }

    private enum IntroOp { NONE,READ,PLAYER,SOLID,GET_OFF,HURT,FM,PLC,CPU,CAPTURE,PALETTE,DRAW,DESPAWN,ROLL }
    /** Never returned/captured. Only the actual cached component stack owns this scope. */
    private final class IntroInvocation {
        final Kind kind;final int whole,routine;final Object producer;final Thread thread=Thread.currentThread();
        final CdObjectPlacementRuntime.Snapshot placed=placement.capture();final Set<Object> cuts=Collections.newSetFromMap(new IdentityHashMap<>());
        final int timer3B=Byte.toUnsignedInt(placement.pool().state(dispatch.whole).copyBytes()[0x3B]);
        IntroOp op=IntroOp.NONE;int prefixReads,xReads,fm,plcs,captures,palettes,cpu;boolean initPalette,awaitPalette,solid,getOff,hurt,despawned,yStored,solidNonzero;int hurtReads;int lastX;boolean abort;
        IntroInvocation(Kind k,int w){kind=k;whole=w;routine=dispatch.routine;producer=body(k);}
    }
    private final CdIntroSpikes.Ports introPorts=new CdIntroSpikes.Ports(){
        public int read(CdIntroSpikes.Read f,CdIntroSpikes.Cut c){return atIntro(IntroOp.READ,c,()->introRead(f,c));}
        public void playerStore(CdIntroSpikes.PlayerStore f,int v,CdIntroSpikes.Cut c){atIntro(IntroOp.PLAYER,c,()->{if(f!=CdIntroSpikes.PlayerStore.Y_LONG||intro.kind!=Kind.INTRO||!intro.solid||intro.hurtReads!=6||intro.yStored)throw new IllegalStateException("No original intro pre-Hurt Y site");var ready=frame.prepareObjectIntroYRewind(frame.state(),frame.environment(),v);var old=SonicCdR31aObjectPass.this.read();publish(old,old,null,ready);intro.yStored=true;return null;});}
        public boolean solid(CdIntroSpikes.Cut c){return atIntro(IntroOp.SOLID,c,()->{if(intro.kind!=Kind.INTRO||intro.solid||intro.getOff||c.source().routine()!=2)throw new IllegalStateException("No original intro Solid site");intro.solid=true;intro.solidNonzero=introSolid(c,CdSolidObject.Entry.SOLID_OBJECT);return intro.solidNonzero;});}
        public void getOff(CdIntroSpikes.Cut c){atIntro(IntroOp.GET_OFF,c,()->{if(intro.kind!=Kind.INTRO||intro.getOff||intro.solid||c.source().routine()!=4)throw new IllegalStateException("No original broken-parent GetOff site");intro.getOff=true;introSolid(c,CdSolidObject.Entry.GET_OFF_OBJECT);return null;});}
        public void hurt(CdIntroSpikes.Cut c){atIntro(IntroOp.HURT,c,()->{if(intro.kind!=Kind.INTRO||!intro.solid||!intro.yStored||intro.hurt)throw new IllegalStateException("No original intro Hurt site");intro.hurt=true;introHurt(c);return null;});}
        public void fm(int id,CdIntroSpikes.Cut c){atIntro(IntroOp.FM,c,()->{if(intro.fm++!=0||!(id==0xCA&&intro.kind==Kind.METAL&&(intro.routine==0||intro.routine==2)&&c.source().routine()==4||id==0xA3&&intro.kind==Kind.INTRO&&c.source().routine()==4))throw new IllegalStateException("No next original intro FM site");sound(new SonicCdGameModule.PastIntroFm(id));return null;});}
        public void loadPlc(int id,CdIntroSpikes.Cut c){atIntro(IntroOp.PLC,c,()->{if(id!=11||intro.kind!=Kind.AMY||intro.routine!=0||intro.prefixReads!=2||intro.plcs++!=0||c.source().copyBytes()[0x3E]<0)throw new IllegalStateException("No original AmyInit PLC11 site");introPlc(c);return null;});}
        public void subCpu(int id,CdIntroSpikes.Cut c){atIntro(IntroOp.CPU,c,()->{if(id!=0x7D||intro.kind!=Kind.AMY||intro.routine!=2||c.source().routine()!=4||intro.cpu++!=0)throw new IllegalStateException("No original Amy captured CPU125 site");sound(new SonicCdGameModule.PastIntroSubCpu(id));return null;});}
        public void setAmyCaptured(CdIntroSpikes.Cut c){atIntro(IntroOp.CAPTURE,c,()->{if(intro.kind!=Kind.AMY&&intro.kind!=Kind.METAL||intro.awaitPalette||++intro.captures>(intro.kind==Kind.METAL&&intro.routine==10?2:1))throw new IllegalStateException("No next original capture store");intro.awaitPalette=true;var old=SonicCdR31aObjectPass.this.read();publish(old,introCut(old,old.core(),OptionalInt.of(255)),null,null);return null;});}
        public void palette(CdAssetBytes bytes,CdIntroSpikes.Cut c){atIntro(IntroOp.PALETTE,c,()->{boolean capture=intro.awaitPalette;var expected=capture?introAssets.capturePalette():introAssets.amyPalette();if(!capture&&(intro.kind!=Kind.AMY||intro.routine!=0||intro.initPalette||intro.prefixReads!=2))throw new IllegalStateException("No original Amy palette site");if(!same(bytes,expected))throw new IllegalArgumentException("Foreign original32 palette source");var old=SonicCdR31aObjectPass.this.read();byte[] normal=old.core().normalPalette().copy();System.arraycopy(bytes.copy(),0,normal,32,32);var next=withNormal(old.core(),CdAssetBytes.copyOf(normal));publish(old,introCut(old,next,old.amyCapturedByte()),null,null);intro.awaitPalette=false;if(!capture)intro.initPalette=true;intro.palettes++;return null;});}
        public void draw(CdIntroSpikes.Cut c){atIntro(IntroOp.DRAW,c,()->{drawDynamic(intro.kind,placement.pool(),c.source().identity());return null;});}
        public void despawn(CdIntroSpikes.Cut c){atIntro(IntroOp.DESPAWN,c,()->{if(intro.kind!=Kind.HEART||!dispatch.drawn||intro.despawned)throw new IllegalStateException("No original Heart post-draw despawn");intro.despawned=true;introDespawn(c.source());return null;});}
    };
    private <T>T atIntro(IntroOp op,CdIntroSpikes.Cut c,java.util.function.Supplier<T> action){
        requireIntro(c);if(intro.op!=IntroOp.NONE||!intro.cuts.add(c))throw new IllegalStateException("Repeated/reentrant original intro cut");intro.op=op;
        try{return action.get();}finally{intro.op=IntroOp.NONE;}
    }
    private void requireIntro(CdIntroSpikes.Cut c){
        if(intro==null||dispatch==null||intro.thread!=Thread.currentThread()||intro.whole!=dispatch.whole||intro.producer!=dispatch.body||intro.producer!=body(intro.kind))throw new IllegalStateException("No current cached intro producer");
        requireKind(intro.kind);if(c==null||c.assets()!=introAssets||!introAssets.owns(assets)||c.source()!=placement.pool().state(intro.whole))throw new IllegalArgumentException("Foreign original intro source64");poolCut(c.pool());
        var p=placement.capture();if(!p.status().equals(intro.placed.status())||!same(p.savedFlags(),intro.placed.savedFlags()))throw new IllegalArgumentException("Intro callback changed placement/status/saved bank");
    }
    private int introRead(CdIntroSpikes.Read field,CdIntroSpikes.Cut c){
        var before=readFrame();var placed=placement.capture();var old=read();var m=frame.state().motion();var e=frame.environment();int value;
        if(field==CdIntroSpikes.Read.TIME_ATTACK){if(intro.kind!=Kind.AMY&&intro.kind!=Kind.INTRO||intro.prefixReads!=0)throw new IllegalStateException("No original timeAttack first read");intro.prefixReads=1;value=e.globals().timeAttackMode();intro.abort=value!=0;}
        else if(field==CdIntroSpikes.Read.AMY_CAPTURED){if(intro.prefixReads!=1||intro.abort||intro.kind!=Kind.AMY&&!(intro.kind==Kind.INTRO&&intro.routine==0))throw new IllegalStateException("No next original captured read");intro.prefixReads=2;value=old.amyCapturedByte().orElseThrow(()->stop(Boundary.INTRO_GLOBAL_OWNER,intro.whole,field.ordinal()));intro.abort=value!=0;}
        else if(field==CdIntroSpikes.Read.PLAYER_X){
            int max=intro.kind==Kind.AMY?(intro.routine==0?3:intro.routine==2?2:1):intro.kind==Kind.METAL?(intro.routine==6?2:intro.routine==8?(intro.timer3B>1?2:3):1):0;
            if(intro.xReads>=max||intro.kind==Kind.AMY&&intro.routine==0&&!intro.initPalette)throw new IllegalStateException("No next original intro player-X read");
            boolean facing=intro.xReads==0&&(intro.kind==Kind.AMY&&(intro.routine==0||intro.routine==2)&&intro.cpu==0||intro.kind==Kind.METAL&&(intro.routine==6||intro.routine==8));
            if(facing&&((c.source().flags()|c.source().spriteFlags())&1)!=0)throw new IllegalStateException("R94 facing clear must precede player-X");intro.xReads++;value=m.centreX16_16()>>>16;intro.lastX=value;
        }else{
            if(intro.kind!=Kind.INTRO||!intro.solidNonzero||(c.source().flags()&8)==0)throw new IllegalStateException("No original intro Hurt guard read");
            var sequence=new CdIntroSpikes.Read[]{CdIntroSpikes.Read.TIME_WARP,CdIntroSpikes.Read.INVINCIBLE,CdIntroSpikes.Read.PLAYER_ROUTINE,CdIntroSpikes.Read.PLAYER_HURT_WORD,CdIntroSpikes.Read.PLAYER_Y_LONG,CdIntroSpikes.Read.PLAYER_Y_VELOCITY};
            if(intro.hurtReads>=sequence.length||field!=sequence[intro.hurtReads++])throw new IllegalStateException("No next original intro Hurt read");
            value=switch(field){case TIME_WARP->old.contact().timeWarp();case INVINCIBLE->e.globals().invincible();case PLAYER_ROUTINE->m.routine();case PLAYER_HURT_WORD->frame.state().timers().hurt();case PLAYER_Y_LONG->m.centreY16_16();case PLAYER_Y_VELOCITY->m.yVelocity();default->throw new IllegalStateException("Unreached intro read "+field);};
        }
        requireFrame(before);requirePlacement(placed);requireCut(old);return value;
    }
    private void runIntro(Kind kind,int whole){
        if(intro!=null)throw new IllegalStateException("Intro reentry");intro=new IntroInvocation(kind,whole);
        try{CdObjectState result=switch(kind){case INTRO->spikes.run(placement.pool(),whole,introPorts);case METAL->metal.run(placement.pool(),whole,introPorts);case AMY->amy.run(placement.pool(),whole,introPorts);case HEART->amy.runHeart(placement.pool(),whole,introPorts);case EXHAUST->metal.runExhaust(placement.pool(),whole,introPorts);case ROLL->{runRoll(whole);yield placement.pool().state(whole);}default->throw new IllegalStateException("Foreign intro body");};
            if(result!=placement.pool().state(whole)||intro.op!=IntroOp.NONE||intro.awaitPalette)throw new IllegalStateException("Incomplete original intro return");
        }finally{intro=null;introPlc=null;}
    }
    private void runRoll(int whole){
        var before=read();var state=frame.state();var env=frame.environment();var placed=placement.capture();var m=state.motion();var g=env.movement().gates();
        var result=roll.step(placement.pool().state(whole),m,new CdRollTunnel.Gates(g.zoneAct()>>>8,env.globals().debugMode(),env.globals().debugCheat(),env.controls().rawHeld()));
        requireFrame(dispatch.frame);requirePlacement(placed);requireCut(before);
        var writes=java.util.EnumSet.noneOf(CdSpinTunnelTag.PlayerField.class);for(var f:result.executedWrites())writes.add(switch(f){case Y_WORD->CdSpinTunnelTag.PlayerField.CENTRE_Y_WORD;case X_VELOCITY->CdSpinTunnelTag.PlayerField.X_VELOCITY;case Y_VELOCITY->CdSpinTunnelTag.PlayerField.Y_VELOCITY;case GROUND_VELOCITY->CdSpinTunnelTag.PlayerField.GROUND_VELOCITY;case FLAGS->CdSpinTunnelTag.PlayerField.FLAGS;case Y_RADIUS->CdSpinTunnelTag.PlayerField.Y_RADIUS;case X_RADIUS->CdSpinTunnelTag.PlayerField.X_RADIUS;case ANIMATION->CdSpinTunnelTag.PlayerField.ANIMATION;default->throw new IllegalArgumentException("Unowned Roll store");});
        var ready=frame.prepareObjectSpinTunnelResult(state,env,new CdSpinTunnelTag.PlayerResult(m,result.motion(),writes));var output=CdObjectPool.overlay(placed.pool(),List.of(result.object()));
        intro.op=IntroOp.ROLL;try{publish(before,before,output,ready);}finally{intro.op=IntroOp.NONE;}
        if(result.draw())introPorts.draw(new CdIntroSpikes.Cut(introAssets,placement.pool().capture(),placement.pool().state(whole)));
        introDespawn(placement.pool().state(whole));
    }
    private void introDespawn(CdObjectState source){
        var e=frame.environment();var result=despawn.despawn(source,new CdSpinTunnelTag.DespawnGlobals(e.movement().gates().timeZone(),e.movement().gates().timeWarpDirection(),read().cameraXWord()));
        if(!result.deleteObject())return;
        // Prepare the whole deletion before either native assignment; saved-byte validation stays first.
        var placed=placement.capture();result.loadedClear().ifPresent(c->placement.savedFlag(c.savedId(),c.period()));var staging=CdObjectPool.stagingCopy(placed.pool());staging.deleteObject(source.identity().wholeIndex());var output=staging.capture();placement.pool().validateRestore(output);requirePlacement(placed);
        result.loadedClear().ifPresent(c->placement.bclrSavedFlag(c.savedId(),c.period(),CdObjectPlacementRuntime.SavedBit.LOADED));placement.pool().restore(output);
    }
    private boolean introSolid(CdIntroSpikes.Cut call,CdSolidObject.Entry entry){
        // GetOffObject (Object Solid.asm:15-55) never consults the retained probes.
        if(entry!=CdSolidObject.Entry.GET_OFF_OBJECT)requireOriginalIntroTerrain();var player=primary();var global=solidGlobals();var out=solid.runWithPrefix(entry,call.source(),player,gates(),global,call.pool());
        if(out instanceof CdSolidObject.NativeStopped stopped){
            var original=stopped.stop().originalException();
            verify(original,()->{var r=stopped.prefix();var ready=frame.prepareObjectSolidPrefix(player,global,r);
                publishIntroSolid(call,r.object(),r.otherSlots(),r.fmRequests(),ready);});
            throw original;
        }
        var r=((CdSolidObject.Completed)out).completedResult();var ready=frame.prepareObjectSolidResult(player,global,r);
        publishIntroSolid(call,r.object(),r.otherSlots(),r.fmRequests(),ready);return r.d0().orElse(0)!=0;
    }
    private void publishIntroSolid(CdIntroSpikes.Cut call,CdObjectState own,List<CdObjectState> other,
                                   List<CdPlayerTouchResponse.FmSound> fm,CdPlayerFrameRuntime.ObjectSolidPublication ready){
        var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(other);var output=CdObjectPool.overlay(call.pool(),images);
        var cut=read();var sounds=new ArrayList<>(cut.sounds());for(var f:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(f)));
        var next=copy(cut,cut.core(),cut.scrollLockWord(),cut.cpuSat(),cut.completedCpu(),sounds);publish(cut,next,output,ready);
    }
    private void requireOriginalIntroTerrain(){if(read().currentTerrain()!=originalTerrain)throw stop(Boundary.CURRENT_TERRAIN_OWNER,intro.whole,0);}
    private void introHurt(CdIntroSpikes.Cut call){
        var cut=read();var e=frame.environment();var g=e.movement();var p=new CdPlayerTouchResponse.PlayerGlobals(cut.contact().timeWarp(),e.globals().invincible(),cut.contact().shield(),cut.contact().combineRing(),e.globals().debugCheat(),e.globals().debugMode(),g.globals().scoreChain(),g.gates().miniSonic());
        var actor=primary().actor();var result=CdPlayerTouchResponse.hurtPlayer(actor,p,cut.world(),call.source(),call.pool());var ready=frame.prepareObjectHurtResult(actor,p,result);var output=CdObjectPool.overlay(call.pool(),result.changedSlots());var sounds=new ArrayList<>(cut.sounds());for(var event:result.calls())if(event instanceof CdPlayerTouchResponse.PlayFm f)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(f.sound())));
        var next=new Cut(cut.core(),cut.ring(),cut.currentTerrain(),new CdPlayerBody.ContactGlobals(result.player().timeWarp(),result.player().shield(),result.player().combineRing()),result.world(),cut.noBumperByte(),cut.cameraXWord(),cut.cameraYWord(),cut.scrollLockWord(),cut.layerByte(),cut.cpuSat(),cut.completedCpu(),sounds,cut.amyCapturedByte());publish(cut,next,output,ready);
    }
    private static Cut introCut(Cut c,SonicCdR31aLoadingVisit.State core,OptionalInt captured){return new Cut(core,c.ring(),c.currentTerrain(),c.contact(),c.world(),c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),c.scrollLockWord(),c.layerByte(),c.cpuSat(),c.completedCpu(),c.sounds(),captured);}
    private static SonicCdR31aLoadingVisit.State withNormal(SonicCdR31aLoadingVisit.State c,CdAssetBytes n){return new SonicCdR31aLoadingVisit.State(c.phase(),c.initialization(),c.section(),c.program(),c.level(),n,c.fadePalette(),c.waterPalette(),c.displayedPalette(),c.reserved(),c.blocksRam(),c.layoutRam(),c.initialFadeSteps(),c.fromFadeSteps(),c.displayEnabled(),c.calls(),c.returnKind(),c.music());}
    /** Original conditional AddGfxQueue invocation; no public constructor or value authority. */
    final class IntroPlcSubmission {
        final IntroInvocation invocation=intro;final Thread thread=Thread.currentThread();final Cut before=read();final ReadFrame frameBefore=readFrame();final CdObjectPlacementRuntime.Snapshot placed=placement.capture();boolean acknowledged;Cut after;
        private IntroPlcSubmission(){}
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}
        Unowned unowned(){return stop(Boundary.INTRO_PLC_OWNER,invocation.whole,11);}
        void validateBefore(){if(this!=introPlc||intro!=invocation||intro.op!=IntroOp.PLC||thread!=Thread.currentThread()||!plc||acknowledged)throw new IllegalStateException("No original current Amy PLC11");guard();requireCut(before);requireFrame(frameBefore);requirePlacement(placed);}
        void acknowledge(){if(this!=introPlc||intro!=invocation||thread!=Thread.currentThread()||acknowledged||!plc)throw new IllegalStateException("No original Amy PLC return");after=read();requireFrame(frameBefore);requirePlacement(placed);requireIntroPlcOnly(before,after);acknowledged=true;}
    }
    private void introPlc(CdIntroSpikes.Cut call){
        if(introPlc!=null||plc)throw new IllegalStateException("Intro PLC reentry");var original=new IntroPlcSubmission();introPlc=original;plc=true;
        try{original.validateBefore();ports.submitIntroPlc(original);if(!original.acknowledged)throw new IllegalStateException("Original Core omitted Amy PLC return");requireCut(original.after);checkpoint();}
        catch(RuntimeException|Error fault){verify(fault,()->{requireFrame(original.frameBefore);requirePlacement(original.placed);requireIntroPlcOnly(original.before,read());});throw fault;}
        finally{introPlc=null;plc=false;}
    }
    private static void requireIntroPlcOnly(Cut before,Cut after){var core=withSectionAndCalls(after,before.core().section(),before.core().calls());if(!sameCut(before,core))throw new IllegalArgumentException("Amy PLC changed non-section owners");}

    private int id(int w){return w==0?frame.state().header().slotId():w<32?reserved(read(),w).unsignedByte(0):placement.pool().state(w).id();}
    private int routine(int w){return w==0?frame.state().motion().routine():w<32?reserved(read(),w).unsignedByte(0x24):placement.pool().state(w).routine();}
    private Kind kind(int w,int id){if(id<1||id>0x3C)throw stop(Boundary.OBJECT_INDEX,w,id);
        if(w==0){if(id!=1)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.PRIMARY;}
        if(w>=32){var selected=switch(id){case 0x20->Kind.CC_SPIKES;case 0x21->Kind.BLOCK;case 0x22->Kind.HIDDEN;case 0x23->Kind.METAL_PLATFORM;case 0x26->Kind.DOOR;case 0x27->Kind.FIRE;case 0x28->Kind.DRUM;default->null;};if(selected!=null)return selected;if(id==0x0A)throw stop(Boundary.SPRING_CHILD_OWNER,w,id);}
        if(w>=32&&id>=0x30&&id<=0x35)return switch(id){case 0x30->Kind.INTRO;case 0x31->Kind.METAL;case 0x32->Kind.AMY;case 0x33->Kind.HEART;case 0x34->Kind.EXHAUST;default->Kind.ROLL;};
        if(id==0x3B){if(w<32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.GAME_OVER;}
        if(id==0x3C)return Kind.TITLE;
        if(id==0x10){if(w<32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.RING;}
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
        &&a.amyCapturedByte().equals(b.amyCapturedByte())&&a.contact().equals(b.contact())&&a.world().equals(b.world())&&a.noBumperByte().equals(b.noBumperByte())
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
    private static Cut copy(Cut c,SonicCdR31aLoadingVisit.State core,int scroll,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds){return new Cut(core,c.ring(),c.currentTerrain(),c.contact(),c.world(),c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),scroll,c.layerByte(),cpu,completed,sounds,c.amyCapturedByte());}
    private static CdAssetBytes reserved(Cut c,int w){if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");return c.core().reserved().slice((w-1)*64,64);}
    private static CdAssetBytes replace(CdAssetBytes bank,int w,CdAssetBytes row){extent(bank,1984);extent(row,64);if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");var next=bank.copy();System.arraycopy(row.copy(),0,next,(w-1)*64,64);return CdAssetBytes.copyOf(next);}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void extent(CdAssetBytes a,int n){if(Objects.requireNonNull(a).length()!=n)throw new IllegalArgumentException("Complete extent "+n+" required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE");}private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD");}
    private static void verify(Throwable original,Runnable check){try{check.run();}catch(RuntimeException|Error fault){if(fault!=original)original.addSuppressed(fault);}}
    private static Unowned stop(Boundary b,int w,int v){return new Unowned(b,w,v);}
}
