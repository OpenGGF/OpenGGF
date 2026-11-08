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
        default void bumper(BumperPublication original){throw stop(Boundary.BUMPER_OWNER,original.wholeIndex(),0x1d);}
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
        PRIMARY_DISPLAY_OWNER,CURRENT_TERRAIN_OWNER,VINT,LAYER,TITLE_PLC_OWNER,INTRO_GLOBAL_OWNER,INTRO_PLC_OWNER,P2_ART_CLOCK_OWNER,P2_ANIMATION_OWNER,P2_PARENT_OWNER,SPRING_CHILD_OWNER,BUMPER_OWNER }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;private final int wholeIndex,value;
        private Unowned(Boundary b,int w,int v){super("R31 initial "+b+" at whole "+w+" value$"+Integer.toHexString(v));boundary=b;wholeIndex=w;value=v;}
        Boundary boundary(){return boundary;}int wholeIndex(){return wholeIndex;}int value(){return value;}
    }
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,HUD,TITLE,MONITOR,RING,INTRO,METAL,AMY,HEART,EXHAUST,ROLL,CC_SPIKES,BLOCK,HIDDEN,METAL_PLATFORM,DOOR,FIRE,DRUM,GAME_OVER,KAMA,SICKLE,EXPLOSION,POINTS,BUMPER,TIME_POST }
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
        private final TimePostInvocation timePostOwner=timePostInvocation;
        private final ReadFrame timePostReadyFrame;
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
            timePostReadyFrame=timePostOwner==null?null:f==null?before:new ReadFrame(f.finalState(),f.finalEnvironment(),before.display(),before.art(),before.clock(),before.paused());
            if(timePostOwner!=null){if(timePostOwner.current==null||!timePostOwner.used)throw new IllegalStateException("No actual Time Post callback publication");timePost.requireCurrent(timePostOwner.current);}
            if(p!=null)placement.pool().validateRestore(p);if(f!=null)frame.validatePreparedObjectSolidPublication(f);
        }
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}Cut expected(){return expected;}Cut next(){return next;}boolean introPalette(){return introPalette;}
        CdGameOver.ScalarCall gameOverScalar(){
            if(gameOverScalar!=null){requireGameOverOperation(gameOverOwner);if(!gameOver.isCurrentPrimitive(gameOverScalar)||!gameOverOwner.used)throw new IllegalStateException("No original Game Over scalar adoption");}
            return gameOverScalar;
        }
        void validateBefore(){currentAdoption(this);
            if(timePostOwner!=null){if(timePostOwner!=timePostInvocation||timePostOwner.current==null||!timePostOwner.used)throw new IllegalStateException("Expired Time Post adoption");timePost.requireCurrent(timePostOwner.current);}
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
            if(timePostOwner!=null){timePostOwner.frameCut=timePostReadyFrame;timePostOwner.cut=next;}
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
    private final CdR31aBumperSource bumperSource;private final CdR31PinballAssets bumperAssets;private final CdPlayerMath bumperMath;
    private BumperInvocation bumper;
    private P2Invocation p2;
    private EnemyInvocation enemy;
    private CdStageTerrain kamaTerrain;
    private CdKamaKama kama;
    private final CdKamaSickle sickle;
    private ChildInvocation childEffect;
    private final CdExplosion explosion;private final CdPoints points;
    private final CdTimePost timePost;private TimePostInvocation timePostInvocation;
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
        bumperSource=CdR31aBumperSource.fromAssets(assets);bumperAssets=bumperSource.assets();bumperMath=new CdPlayerMath(frame.stageSource().player());
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
        explosion=CdExplosion.fromR31aAssets(placement,assets,common,new CdExplosion.DispatchObserver(){
            public void beforePublication(CdExplosion.Publication c){childPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output());}
            public void afterPublication(CdExplosion.Publication c){childAfter(c);}
            public void beforeCall(Object c){var draw=(CdExplosion.DrawCall)c;childDrawBegin(c,draw.cut(),draw.source());}
            public void afterCall(Object c){childAfter(c);}
        },c->childDraw(c,c.source()),new CdExplosion.ReceiptGuard(){public void prepared(Object c){childPrepared(c);}public void completed(Object c){childCompleted(c);}});
        points=CdPoints.fromR31aAssets(placement,assets,common,new CdPoints.DispatchObserver(){
            public void beforePublication(CdPoints.Publication c){childPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output());}
            public void afterPublication(CdPoints.Publication c){childAfter(c);}
            public void beforeCall(Object c){var draw=(CdPoints.DrawCall)c;childDrawBegin(c,draw.cut(),draw.source());}
            public void afterCall(Object c){childAfter(c);}
        },c->childDraw(c,c.source()),new CdPoints.ReceiptGuard(){public void prepared(Object c){childPrepared(c);}public void completed(Object c){childCompleted(c);}});
        originalTerrain=assets.terrain();terrain=CdPlayerTerrain.fromStageTerrain(originalTerrain);solid=new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        bindKama(originalTerrain);
        var sicklePorts=new EnemyPorts(Kind.SICKLE);sickle=new CdKamaSickle(placement,sicklePorts,sicklePorts);sicklePorts.bind(sickle);
        hud=new CdReservedHud(common.reservedHudAssets(),new CdReservedHud.Slots(){
            public CdAssetBytes read(int w){requireWhole(Kind.HUD,w);return reserved(SonicCdR31aObjectPass.this.read(),w);}
            public void write(int w,CdAssetBytes e,CdAssetBytes n){reservedWrite(Kind.HUD,w,e,n);}},w->drawReserved(Kind.HUD,w));
        title=new CdTitleCard(placement,common.titleCardAssets(),new CdTitleCard.ReservedSlots(){
            public CdAssetBytes read(int w){requireWhole(Kind.TITLE,w);return reserved(SonicCdR31aObjectPass.this.read(),w);}
            public void write(int w,CdAssetBytes e,CdAssetBytes n){reservedWrite(Kind.TITLE,w,e,n);}},
            this::titleRead,this::titleRead,this::titleStore,this::titlePlc,this::titleDraw);
        timePost=CdTimePost.withOriginalOperations(placement,common.monitorAssets(),this::timePostDirection,
            this::timePostSound,this::timePostDraw,new CdTimePost.NativeObserver(){
                public void before(CdTimePost.OriginalCall c){beforeTimePost(c);}
                public void after(CdTimePost.OriginalCall c){afterTimePost(c);}
                public void failed(CdTimePost.OriginalCall c,Throwable original){failedTimePost(c,original);}
            });
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
                guard();int id=id(w);if(id==0)continue;var kind=kind(w,id);if(kind==Kind.KAMA)bindKama(read().currentTerrain());int routine=routine(w);
                dispatch=new Dispatch(kind,w,routine,body(kind));
                try{if(kind!=Kind.PRIMARY)checkpoint();execute(kind,w,levelFrames);
                    if(dispatch.solidCut!=null)throw new IllegalStateException("Unretired original Solid call");}
                finally{dispatch=null;}
            }phase=Phase.WALKED;
        }catch(RuntimeException|Error original){phase=Phase.FAILED;queue.close();throw original;}
        finally{walkingThread=null;dispatch=null;primaryPending=null;pending=null;plcPending=null;primaryDraw=null;intro=null;introPlc=null;gameOverOperation=null;enemy=null;childEffect=null;timePostInvocation=null;}
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
    /** Original World transfer only, after the actual walk and before its unexecuted draw tail. */
    void abandonCompletedWalk(){
        if(phase!=Phase.WALKED||queue==null||queue.phase()!=CdObjectDrawQueue.Phase.COLLECTING||busy()||dispatch!=null||walkingThread!=null)
            throw new IllegalStateException("No complete unborrowed R31 walk to abandon");
        if(frame.closed()||!frame.stageSource().ownsR31aAssets(assets)||!placement.ownsR31aAssets(assets)||!common.ownsAssets(assets))throw new IllegalStateException("R31 walk source retired");
        queue.close();queue=null;phase=Phase.DONE;
    }
    void discard(){if(busy()||phase==Phase.WALKING||phase==Phase.DRAINING||dispatch!=null)throw new IllegalStateException("Active R31 pass");if(queue!=null)queue.close();queue=null;phase=Phase.DISCARDED;}
    private boolean busy(){return timePostInvocation!=null||bumper!=null||enemy!=null||childEffect!=null||gameOverOperation!=null||p2!=null||intro!=null||introPlc!=null||primaryInvocation!=null||placeCall!=null||checking||reading||adopting||drawing||plc||pending!=null||primaryPending!=null||plcPending!=null||primaryDraw!=null;}
    private Object body(Kind k){return switch(k){case PRIMARY->frame;case HUD->hud;case TITLE->title;case MONITOR->monitor;case RING->ring;case INTRO->spikes;case METAL,EXHAUST->metal;case AMY,HEART->amy;case ROLL->roll;case CC_SPIKES->ccSpikes;case BLOCK->block;case HIDDEN->hidden;case METAL_PLATFORM->metalPlatform;case DOOR->door;case FIRE->fire;case DRUM->drum;case GAME_OVER->gameOver;case KAMA->kama;case SICKLE->sickle;case EXPLOSION->explosion;case POINTS->points;case BUMPER->CdBumper.class;case TIME_POST->timePost;};}
    private void execute(Kind kind,int w,int levelFrames){var c=read();var e=frame.environment();var g=e.movement().gates();switch(kind){
        case PRIMARY->runPrimary(levelFrames);
        case BUMPER->runBumper(w);
        case TIME_POST->runTimePost(w);
        case GAME_OVER->{var result=gameOver.run(w,new CdGameOver.GlobalsPort(){
            public CdGameOver.Globals read(){var op=gameOverPrimitive(CdGameOver.ReadCall.class);var current=op.current;return new CdGameOver.Globals(current.ring().lives(),current.core().level().powerup(),current.core().level().timeOver());}
            public void writePowerup(int value){gameOverStore(CdGameOver.Scalar.POWERUP,value);}
            public void writeTimeOver(int value){gameOverStore(CdGameOver.Scalar.TIME_OVER,value);}
        });if(result!=dispatch.gameOverResult||!result.state().equals(placement.pool().state(w))||gameOverOperation!=null)throw new IllegalStateException("No original Game Over completion");}
        case KAMA,SICKLE->runKamaSickle(kind,w);
        case EXPLOSION,POINTS->runChildEffect(kind,w);
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
    /** One reached private body invocation; no completed value can manufacture this receiver scope. */
    private final class BumperInvocation {
        final int whole;final Thread thread=Thread.currentThread();final Object receiver=CdBumper.class;
        CdPinballContext context;int ordinal;BumperStep step;
        BumperInvocation(int whole){this.whole=whole;}
    }
    private final class BumperStep {
        final CdPinballContext.Operation original;final int ordinal;final int whole=bumper.whole;
        final Cut cut;final ReadFrame before;final CdObjectPlacementRuntime.Snapshot placed;
        final CdPinballContext.PrimaryResult prefix;
        BumperPublication publication;boolean preparing=true,drawClearPending;
        BumperStep(CdPinballContext.Operation op){
            original=op;ordinal=op.ordinal();cut=read();before=readFrame();placed=placement.capture();prefix=op.primaryBefore();
        }
    }
    /** Original-operation publication. Each site changes ONE live owner: pool, Frame, bank or Host.
     * Score/lives/sound are one prebuilt existing Host image; Core/reserved are retained and fenced.
     * In particular no pool/Frame write is followed by a fallible Host/Core adoption. */
    final class BumperPublication {
        private final BumperStep site;private final Cut next;
        private final CdObjectPool.Snapshot poolOutput;
        private final CdPlayerFrameRuntime.BumperPlayerPublication playerOutput;
        private final CdObjectPlacementRuntime.BumperSavedPublication savedOutput;
        private boolean applying,applied,acknowledged;
        private BumperPublication(BumperStep site){
            this.site=site;site.publication=this;var call=CdR31BumperCall.fromOriginal(this);var op=site.original;var c=site.cut;Cut n=c;
            CdObjectPool.Snapshot pool=null;CdPlayerFrameRuntime.BumperPlayerPublication player=null;
            CdObjectPlacementRuntime.BumperSavedPublication saved=null;
            if(op instanceof CdPinballContext.ObjectStore o){
                if(o.expected()!=placement.pool().state(o.wholeIndex()))throw new IllegalArgumentException("Foreign original Bumper slot reference");
                if(o.output().isPresent())pool=CdObjectPool.overlay(site.placed.pool(),List.of(o.output().orElseThrow()));
                else{var stage=CdObjectPool.stagingCopy(site.placed.pool());stage.deleteObject(o.wholeIndex());pool=stage.capture();}
                placement.pool().validateRestore(pool);
            }else if(op instanceof CdPinballContext.PlayerStore){player=frame.prepareBumperPlayerStore(call);
            }else if(op instanceof CdPinballContext.SavedStore){saved=placement.prepareBumperSavedStore(call);
            }else if(op instanceof CdPinballContext.ScoreStore o){
                if(!o.expected().equals(c.world()))throw new IllegalArgumentException("Changed original AddPoints input");
                n=bumperValues(c,o.output(),c.sounds());
            }else if(op instanceof CdPinballContext.Sound o){
                if(o.kind()!=CdPinballContext.SoundKind.SUB_CPU||(o.nativeId()!=0x7a&&o.nativeId()!=0x7f))throw new IllegalStateException("No Bumper FM/other sound site");
                var sounds=new ArrayList<>(c.sounds());sounds.add(new SonicCdGameModule.PastBumperSubCpu(o.nativeId()));
                n=bumperValues(c,c.world(),sounds);
            }else throw new IllegalArgumentException("Not a Bumper publication operation");
            next=n;poolOutput=pool;playerOutput=player;savedOutput=saved;
        }
        CdPlayerFrameRuntime frameOwner(){return frame;}CdObjectPlacementRuntime placementOwner(){return placement;}
        void requireCallPreparation(){if(site.publication!=this)throw new IllegalStateException("Foreign original Bumper receipt");bumperPreparation(site);}
        void requireCallApplication(){if(site.publication!=this)throw new IllegalStateException("Foreign original Bumper receipt");bumperApplication(site);}
        SonicCdR31aObjectPass owner(){return SonicCdR31aObjectPass.this;}
        int wholeIndex(){return site.whole;}CdPinballContext.Operation operation(){return site.original;}Cut expected(){return site.cut;}Cut next(){return next;}
        boolean changesHost(){return site.original instanceof CdPinballContext.ScoreStore||site.original instanceof CdPinballContext.Sound;}
        void validateBefore(){
            requireBumperSite(site);if(site.publication!=this||applied||applying||acknowledged)throw new IllegalStateException("Expired Bumper publication");
            requireCut(site.cut);requireFrame(site.before);requirePlacement(site.placed);requireBumperPrefix(site.prefix);
            if(poolOutput!=null)placement.pool().validateRestore(poolOutput);
            if(playerOutput!=null)frame.validateBumperPlayerStore(playerOutput);
            if(savedOutput!=null)placement.validateBumperSavedStore(savedOutput);
        }
        void applyNative(){
            validateBefore();applying=true;
            try{
                // Each branch owns one native write. All references/ledgers were prepared before here.
                if(poolOutput!=null)placement.pool().restore(poolOutput);
                else if(playerOutput!=null)frame.applyBumperPlayerStore(playerOutput);
                else if(savedOutput!=null)placement.applyBumperSavedStore(savedOutput);
                applied=true;
            }finally{applying=false;}
        }
        /** Called immediately after the one ready Host reference assignment, or unchanged Host fence.
         * No callbacks/allocations and no further live-owner assignments follow this receipt. */
        void acknowledge(){if(bumper==null||bumper.step!=site||!applied||acknowledged||bumper.thread!=Thread.currentThread())throw new IllegalStateException("Not original Bumper adoption");acknowledged=true;}
        private void verifyReached(){
            requireCut(acknowledged?next:site.cut);
            if(applied&&poolOutput!=null)requirePlacementOutput(site.placed,poolOutput);
            else if(applied&&savedOutput!=null){
                var now=placement.capture();if(!now.status().equals(site.placed.status())||!now.pool().slots().equals(site.placed.pool().slots()))throw new IllegalArgumentException("Saved store changed placement nonwrites");
                var bytes=site.placed.savedFlags().copy();var op=(CdPinballContext.SavedStore)site.original;
                int at=op.kind()==CdPinballContext.SavedKind.BUMPER_LINEAR_COUNTER?2+op.savedId():2+3*op.savedId()+op.period();bytes[at]=(byte)op.outputByte();
                if(!Arrays.equals(bytes,now.savedFlags().copy()))throw new IllegalArgumentException("Bumper bank prefix differs");
            }else requirePlacement(site.placed);
            if(applied&&playerOutput!=null){
                if(frame.state()!=((CdPinballContext.PlayerStore)site.original).output()||frame.environment()!=site.before.environment()
                        ||frame.display()!=site.before.display()||frame.artState()!=site.before.art()||frame.vIntClock()!=site.before.clock()||frame.paused()!=site.before.paused())throw new IllegalArgumentException("Bumper Frame nonwrites changed");
            }else requireFrame(site.before);
        }
    }
    private void requireBumperInvocation(){
        if(bumper==null||phase!=Phase.WALKING||walkingThread!=Thread.currentThread()||bumper.thread!=Thread.currentThread()
                ||dispatch==null||dispatch.kind!=Kind.BUMPER||dispatch.body!=bumper.receiver||dispatch.whole!=bumper.whole
                ||!bumperSource.owns(assets)||bumperSource.assets()!=bumperAssets)throw new IllegalStateException("No actual cached Bumper invocation");
        guard();
    }
    private void requireBumperSite(BumperStep s){
        requireBumperInvocation();if(s==null||bumper.step!=s)throw new IllegalStateException("Not original pending Bumper site");
        s.original.requireOwner(bumper.context);if(s.original.ordinal()!=s.ordinal||bumper.ordinal!=s.ordinal)throw new IllegalStateException("Bumper cursor/order differs");
    }
    private void bumperPreparation(BumperStep s){
        requireBumperSite(s);if(!s.preparing&&(s.publication==null||s.publication.applied||s.publication.applying))throw new IllegalStateException("Not Bumper preparation");
        requireCut(s.cut);requireFrame(s.before);requirePlacement(s.placed);requireBumperPrefix(s.prefix);
    }
    private void bumperApplication(BumperStep s){
        requireBumperSite(s);if(s.publication==null||!s.publication.applying||s.publication.applied)throw new IllegalStateException("No original Bumper application");
        requireCut(s.cut);requireFrame(s.before);requirePlacement(s.placed);requireBumperPrefix(s.prefix);
    }
    private void requireBumperPrefix(CdPinballContext.PrimaryResult p){
        poolCut(p.pool());var c=read();var placed=placement.capture();
        if(p.primary()!=frame.state()||p.environment()!=frame.environment()||!p.score().equals(c.world())||!same(p.savedFlags(),placed.savedFlags()))throw new IllegalArgumentException("Stale original primary-only Bumper prefix");
        // read()/sameCut also fence the COMPLETE Core including reserved1984/whole1, not merely pool96.
    }
    private void atBumper(CdPinballContext.Operation op,Runnable action){
        requireKind(Kind.BUMPER);requireBumperInvocation();if(bumper.step!=null)throw new IllegalStateException("Bumper callback reentry");
        op.requireOwner(bumper.context);if(op.ordinal()!=bumper.ordinal+1)throw new IllegalStateException("Not next original Bumper operation");claim(op);
        var s=new BumperStep(op);bumper.ordinal=s.ordinal;bumper.step=s;
        try{requireBumperPrefix(s.prefix);action.run();}
        catch(RuntimeException|Error original){verify(original,()->{if(s.publication!=null)s.publication.verifyReached();else{requireCut(s.cut);requireFrame(s.before);requirePlacement(s.placed);}});throw original;}
        finally{bumper.step=null;}
    }
    private void bumperStore(CdPinballContext.Operation op,Runnable complete){atBumper(op,()->{
        var s=bumper.step;var p=new BumperPublication(s);s.publication=p;s.preparing=false;
        p.validateBefore();ports.bumper(p);if(!p.applied||!p.acknowledged)throw new IllegalStateException("Actual Bumper owner omitted original publication");
        p.verifyReached();checkpoint();complete.run();p.verifyReached();
    });}
    private final CdPinballContext.Ports bumperPorts=new CdPinballContext.Ports(){
        public void objectStore(CdPinballContext.ObjectStore o){bumperStore(o,o::applyNative);}
        public void playerStore(CdPinballContext.PlayerStore o){bumperStore(o,o::applyNative);}
        public void scoreStore(CdPinballContext.ScoreStore o){bumperStore(o,o::applyNative);}
        public void savedStore(CdPinballContext.SavedStore o){bumperStore(o,o::applyNative);}
        public void sound(CdPinballContext.Sound o){bumperStore(o,o::completedNative);}
        public void attachment(CdPinballContext.Attachment o){atBumper(o,()->{throw new IllegalStateException("Bumper has no attachment site");});}
        public void draw(CdPinballContext.Draw o){atBumper(o,()->{
            if(dispatch.drawn||o.wholeIndex()!=bumper.whole)throw new IllegalStateException("Repeated/foreign Bumper Draw");
            var source=o.sourceBeforeBit7Clear();var live=placement.pool().state(bumper.whole);
            if(!source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127).equals(live))throw new IllegalArgumentException("Not actual Bumper post-clear source");
            dispatch.drawn=true;bumper.step.drawClearPending=true;
            SonicCdR31aObjectPass.this.draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(bumper.whole),source.spriteFlags(),source.centreX(),source.centreY(),source.unsignedByte(Y_RADIUS),source.unsignedByte(WIDTH),source.unsignedByte(PRIORITY)));
            if(bumper.step.drawClearPending)throw new IllegalStateException("Queue omitted Bumper clear continuation");
            requireCut(bumper.step.cut);requireFrame(bumper.step.before);requirePlacement(bumper.step.placed);o.completedNative();
        });}
        public void despawn(CdPinballContext.Despawn o){atBumper(o,()->{
            var source=placement.pool().state(bumper.whole);if(!o.usesSavedAnchor()||o.wholeIndex()!=bumper.whole||o.xWord()!=source.word(MOVING_SPRING_BASE_Y))throw new IllegalArgumentException("Not original Bumper anchor operand");
            var g=frame.environment().movement().gates();var result=despawn.despawnAtX(source,o.xWord(),new CdSpinTunnelTag.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),read().cameraXWord()));
            requireCut(bumper.step.cut);requireFrame(bumper.step.before);requirePlacement(bumper.step.placed);o.completedNative(result.deleteObject());
        });}
    };
    private void runBumper(int whole){
        requireKind(Kind.BUMPER);if(bumper!=null)throw new IllegalStateException("Bumper invocation reentry");
        bumper=new BumperInvocation(whole);
        try{var c=read();var e=frame.environment();var g=e.movement().gates();var p=placement.capture();
            bumper.context=CdPinballContext.forPrimary(new CdPinballContext.PrimaryInput(p.pool(),frame.state(),e,c.world(),p.savedFlags(),g.timeZone(),c.core().program().goodFutureByte(),e.globals().debugMode(),g.zoneAct()&255),bumperAssets,bumperMath,bumperPorts);
            var result=CdBumper.runPrimary(bumper.context,whole);if(bumper.step!=null)throw new IllegalStateException("Unretired Bumper operation");requireBumperPrefix(result);
        }finally{bumper=null;}
    }
    private static Cut bumperValues(Cut c,CdPlayerTouchResponse.WorldGlobals w,List<SonicCdGameModule.PastSound> sounds){
        var r=c.ring();var ring=new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());
        return new Cut(c.core(),ring,c.currentTerrain(),c.contact(),w,c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),c.scrollLockWord(),c.layerByte(),c.cpuSat(),c.completedCpu(),sounds,c.amyCapturedByte());
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
    // Original R31A Kama/Sickle joins: one physical walk, no completed-effect replay.
    private enum EnemyOp { NONE,GLOBAL,PLAYER,MAPPING,ANIMATION,ANGLE,SOUND,DRAW }
    private final class EnemyInvocation {
        final Kind kind;final int whole;final Object receiver;final Thread thread=Thread.currentThread();
        final CdStageTerrain terrainAtVisit;EnemyOp op=EnemyOp.NONE;
        EnemyInvocation(Kind kind,int whole){this.kind=kind;this.whole=whole;receiver=body(kind);terrainAtVisit=read().currentTerrain();}
    }
    /** These factories borrow immutable references only, with NO query/decode/native byte read.
     * Native PlaceBlock may publish a new authenticated current terrain before this visit.
     * Keep one cached receiver for that exact reference; all authoritative object state stays in
     * the same pool. Do not replace its receiver during an invocation or reuse an old port. */
    private void bindKama(CdStageTerrain current){
        if(enemy!=null)throw new IllegalStateException("Active Kama source borrow");
        if(kama==null||kamaTerrain!=current){var port=new EnemyPorts(Kind.KAMA);
            var next=new CdKamaKama(placement,port,CdPlayerTerrain.fromStageTerrain(Objects.requireNonNull(current)),port);
            port.bind(next);kamaTerrain=current;kama=next;}
    }
    private void runKamaSickle(Kind kind,int whole){
        requireWhole(kind,whole);if(enemy!=null)throw new IllegalStateException("Enemy invocation reentry");
        var original=new EnemyInvocation(kind,whole);enemy=original;
        try{if(kind==Kind.KAMA){if(kamaTerrain!=original.terrainAtVisit)throw new IllegalStateException("Stale current Kama terrain");kama.run(whole);}
            else if(kind==Kind.SICKLE)sickle.run(whole);else throw new IllegalStateException("Foreign enemy receiver");
            if(enemy!=original||original.op!=EnemyOp.NONE)throw new IllegalStateException("Unretired enemy site");
            requireKind(kind);
        }finally{enemy=null;}
    }
    private void requireEnemy(EnemyPorts port){
        var current=enemy;
        if(current==null||dispatch==null||current.kind!=port.kind||current.whole!=dispatch.whole||current.receiver!=port.receiver
                ||current.receiver!=dispatch.body||current.receiver!=body(current.kind)||current.thread!=Thread.currentThread())
            throw new IllegalStateException("No original cached enemy receiver/physical visit");
        requireKind(current.kind);
        if(read().currentTerrain()!=current.terrainAtVisit||current.kind==Kind.KAMA&&kamaTerrain!=current.terrainAtVisit)
            throw new IllegalArgumentException("Changed current terrain during original enemy invocation");
    }
    private <T>T enemyAt(EnemyPorts port,EnemyOp site,java.util.function.Supplier<T> action){
        requireEnemy(port);var current=enemy;if(current.op!=EnemyOp.NONE)throw new IllegalStateException("Enemy site reentry");current.op=site;
        try{return action.get();}finally{current.op=EnemyOp.NONE;}
    }
    /** Native reads have a complete read-only World/Frame/environment/full96/status/bank cut.
     * Preserve the original throwable and attach only distinct verification faults; never undo. */
    private <T>T enemyRead(EnemyPorts port,EnemyOp site,java.util.function.Supplier<T> action){return enemyAt(port,site,()->{
        var f=readFrame();var placed=placement.capture();var cut=read();
        try{var result=action.get();requireFrame(f);requirePlacement(placed);requireCut(cut);return result;}
        catch(RuntimeException|Error original){verify(original,()->{requireFrame(f);requirePlacement(placed);requireCut(cut);});throw original;}
    });}
    private void enemySource(CdObjectState expected,CdObjectPlacementRuntime.Snapshot placed){
        if(expected!=placement.pool().state(enemy.whole)||expected.id()!=(enemy.kind==Kind.KAMA?0x24:0x25))throw new IllegalArgumentException("Not the actual enemy source image");
        identity(expected.identity());requirePlacement(placed);
    }
    /** One private port per cached receiver, bound exactly once and never exported. Enum/value
     * equality, a retained component call or completed sound metadata cannot enter this scope. */
    private final class EnemyPorts implements CdCczEnemyPorts,CdCczEnemyPorts.Assets {
        private final Kind kind;private Object receiver;
        private EnemyPorts(Kind kind){this.kind=kind;}
        private void bind(Object original){if(receiver!=null||original==null)throw new IllegalStateException("Enemy receiver already bound");receiver=original;}
        public int global(CdCczEnemyPorts.Global field){return enemyRead(this,EnemyOp.GLOBAL,()->{
            var e=frame.environment();var g=e.movement().gates();
            return switch(field){
                case GOOD_FUTURE->{if(kind!=Kind.KAMA)throw new IllegalStateException("No Sickle GoodFuture site");yield read().core().program().goodFutureByte();}
                case TIME_ZONE->g.timeZone();case WARP_DIRECTION->g.timeWarpDirection();case CAMERA_X->read().cameraXWord();
                case INVINCIBLE->{if(kind!=Kind.SICKLE||placement.pool().state(enemy.whole).routine()!=4)throw new IllegalStateException("No current Sickle invincible site");yield e.globals().invincible();}
            };
        });}
        public int player(CdCczEnemyPorts.Player field){return enemyRead(this,EnemyOp.PLAYER,()->{
            int routine=placement.pool().state(enemy.whole).routine();
            if(kind==Kind.KAMA&&(routine!=4||field!=CdCczEnemyPorts.Player.X&&field!=CdCczEnemyPorts.Player.Y)
                    ||kind==Kind.SICKLE&&routine!=4)throw new IllegalStateException("No original enemy player-read site");
            var m=frame.state().motion();return switch(field){case X->m.centreX16_16()>>>16;case Y->m.centreY16_16()>>>16;case FLAGS->m.flags();case X_VELOCITY->m.xVelocity()&65535;};
        });}
        public CdPlayerMath.SineCosine sine(int angle){requireEnemy(this);throw new IllegalStateException("Kama/Sickle native graph has no sine read");}
        public CdCczEnemyPorts.MappingView mapping(CdCczEnemyPorts.Mapping which){return enemyRead(this,EnemyOp.MAPPING,()->{
            var source=placement.pool().state(enemy.whole);
            if(source.routine()!=0||kind==Kind.KAMA&&which!=CdCczEnemyPorts.Mapping.KAMA_NORMAL&&which!=CdCczEnemyPorts.Mapping.KAMA_DAMAGED
                    ||kind==Kind.SICKLE&&which!=CdCczEnemyPorts.Mapping.SICKLE)throw new IllegalStateException("No original enemy mapping STORE");
            return common.kamaSickleAssets().mapping(which);
        });}
        public CdObjectAnimation.AnimationTable animation(CdCczEnemyPorts.Animation which){return enemyRead(this,EnemyOp.ANIMATION,()->{
            if(which!=(kind==Kind.KAMA?CdCczEnemyPorts.Animation.KAMA:CdCczEnemyPorts.Animation.SICKLE))throw new IllegalStateException("Foreign selected enemy animation");
            return common.kamaSickleAssets().animation(which);
        });}
        public int chainLinkCount(){requireEnemy(this);throw new IllegalStateException("Kama/Sickle native graph has no chain-count read");}
        public void primaryAngle(CdCczEnemyPorts.AngleCall call){enemyAt(this,EnemyOp.ANGLE,()->{
            if(kind!=Kind.KAMA||call==null||!call.isCurrent())throw new IllegalStateException("No original Kama collision-angle call");
            enemySource(call.expectedSource(),call.callCut());claim(call);
            int routine=call.expectedSource().routine();if(routine!=2&&routine!=10)throw new IllegalStateException("No original Kama terrain-probe routine");
            // Prepare the existing complete Frame pair BEFORE publication. Each distinct native
            // same-value store still gets its own original call and adoption, never coalesced.
            var ready=frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),call.value());
            var cut=read();publish(cut,cut,null,ready);return null;
        });}
        public void sound(CdCczEnemyPorts.SoundCall call){enemyAt(this,EnemyOp.SOUND,()->{
            if(kind!=Kind.SICKLE||call==null||!call.isCurrent()||call.sound()!=CdCczEnemyPorts.Sound.SICKLE)
                throw new IllegalStateException("No original Sickle FM B9 call");
            enemySource(call.expectedSource(),call.callCut());claim(call);var source=call.expectedSource();
            if(source.routine()!=4||source.unsignedByte(MAP_FRAME)!=0||(source.spriteFlags()&128)==0||dispatch.fm)
                throw new IllegalStateException("Wrong/repeated original Sickle FM site");
            dispatch.fm=true;SonicCdR31aObjectPass.this.sound(new SonicCdGameModule.PastKamaSickleFm());return null;
        });}
        public void draw(CdObjectPool pool,CdObjectState.Identity identity){enemyAt(this,EnemyOp.DRAW,()->{
            drawDynamic(kind,pool,identity);checkpoint();return null;
        });}
    }
    // Original Explosion/Points invocation only. Result/effects are completed metadata, never commands.
    private final class ChildInvocation {
        final Kind kind;final int whole,routine;final Object receiver;final Dispatch visit;final Thread thread=Thread.currentThread();
        ReadFrame frameCut;Cut cut;CdObjectPlacementRuntime.Snapshot placed;ChildOperation operation;
        long publications;Object lastStore;boolean header,child,animated,terminal,drew,returned;
        ChildInvocation(Kind kind,int whole){this.kind=kind;this.whole=whole;routine=dispatch.routine;receiver=body(kind);visit=dispatch;sync();}
        void sync(){var nextFrame=readFrame();var nextCut=read();var nextPlacement=placement.capture();frameCut=nextFrame;cut=nextCut;placed=nextPlacement;}
    }
    private final class ChildOperation {
        final Object call;final long ordinal;final boolean publication;final Object store;
        final ReadFrame frameCut;final Cut cut;final CdObjectPlacementRuntime.Snapshot placed;
        final List<byte[]> expected;boolean prepared,used,after;
        ChildOperation(Object call,long ordinal,boolean publication,Object store,CdObjectPlacementRuntime.Snapshot placed,List<byte[]> expected){
            this.call=call;this.ordinal=ordinal;this.publication=publication;this.store=store;this.placed=placed;this.expected=List.copyOf(expected);frameCut=readFrame();cut=read();}
    }
    private void requireChildInvocation(){var current=childEffect;
        if(current==null||dispatch==null||current.visit!=dispatch||current.whole!=dispatch.whole||current.receiver!=dispatch.body
                ||current.receiver!=body(current.kind)||current.thread!=Thread.currentThread()||current.returned)
            throw new IllegalStateException("No original cached child-effect physical invocation");
        requireKind(current.kind);
    }
    private boolean actualChild(Object call){return call!=null&&childEffect!=null&&switch(childEffect.kind){
        case EXPLOSION->explosion.isCurrentCall(call);case POINTS->points.isCurrentCall(call);default->false;};}
    private ChildOperation currentChild(Object call){requireChildInvocation();var op=childEffect.operation;
        if(op==null||op.call!=call||!actualChild(call))throw new IllegalStateException("No original pending child-effect site");return op;}
    private void runChildEffect(Kind kind,int whole){
        requireWhole(kind,whole);if(childEffect!=null)throw new IllegalStateException("Child-effect reentry");
        var original=new ChildInvocation(kind,whole);childEffect=original;
        try{CdObjectState result;if(kind==Kind.EXPLOSION)result=explosion.run(whole).state();else if(kind==Kind.POINTS)result=points.run(whole).state();else throw new IllegalStateException("Foreign child effect");
            requireChildInvocation();if(original.operation!=null||result!=placement.pool().state(whole)||!original.terminal&&!original.drew)
                throw new IllegalStateException("No completed original child-effect return");
            requireFrame(original.frameCut);requireCut(original.cut);requirePlacement(original.placed);original.returned=true;
        }catch(RuntimeException|Error fault){verify(fault,()->childFailed(original));throw fault;}
        finally{childEffect=null;}
    }
    private void childPublication(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot placed,CdObjectState source,CdObjectState before,CdObjectState output){
        requireChildInvocation();var current=childEffect;
        if(current.operation!=null||!actualChild(call)||ordinal!=current.publications+1)throw new IllegalStateException("Not next original child publication");
        requireFrame(current.frameCut);requireCut(current.cut);requirePlacement(current.placed);requirePlacement(placed);
        if(source!=placement.pool().state(current.whole)||before==null||before!=placement.pool().state(before.identity().wholeIndex()))throw new IllegalArgumentException("Foreign child source/preimage");
        Object store;
        if(call instanceof CdExplosion.Publication c){if(current.kind!=Kind.EXPLOSION)throw new IllegalStateException("Foreign Explosion receiver");store=c.kind();childExplosionOrder(c);}
        else if(call instanceof CdPoints.Publication c){if(current.kind!=Kind.POINTS||current.publications!=0)throw new IllegalStateException("Foreign/repeated Points publication");store=c.kind();
            if(c.kind()==CdPoints.StoreKind.DELETE){if(current.routine!=2||source.unsignedByte(POINTS_TIMER)!=1||output!=null)throw new IllegalStateException("No original Points delete site");}
            else if(current.routine!=0&&current.routine!=2||output==null)throw new IllegalStateException("No original Points rise site");
        }else throw new IllegalStateException("Foreign child publication type");
        int target=before.identity().wholeIndex();
        if(call instanceof CdExplosion.Publication c&&c.kind()==CdExplosion.StoreKind.POINTS_CHILD){
            // Source FindObjSlot=SpawnObject returns A1; validate that actual first-free result,
            // never reinterpret its child as A0, clear it, reserve it or dispatch it recursively.
            if(placement.pool().findObjSlot().orElse(-1)!=target||before.id()!=0||output==null||output.id()!=0x1C)throw new IllegalArgumentException("Not actual first-free A1 child");
        }else if(target!=current.whole)throw new IllegalArgumentException("Foreign child-effect store target");
        if(output!=null){if(!output.identity().equals(before.identity()))throw new IllegalArgumentException("Changed physical output");CdObjectPool.overlay(placed.pool(),List.of(output));}
        var expected=childRows(placed);expected.set(target-32,output==null?new byte[64]:output.copyBytes());
        childEffect.operation=new ChildOperation(call,ordinal,true,store,placed,expected);
    }
    private void childExplosionOrder(CdExplosion.Publication c){var i=childEffect;var kind=c.kind();
        boolean start=i.routine==0&&i.header&&!i.animated&&(i.lastStore==CdExplosion.StoreKind.INIT_HEADER||i.lastStore==CdExplosion.StoreKind.POINTS_CHILD)
            ||i.routine==2&&i.publications==0;
        switch(kind){
            case INIT_HEADER->{if(i.routine!=0||i.publications!=0||c.output()==null)throw new IllegalStateException("No original Explosion header");}
            case POINTS_CHILD->{if(i.routine!=0||!i.header||i.child||i.lastStore!=CdExplosion.StoreKind.INIT_HEADER||c.source().unsignedByte(ROUTINE_2)!=0)throw new IllegalStateException("No original MakePoints site");}
            case ANIMATION_RESULT,FAILURE_RESET->{if(!start||c.output()==null)throw new IllegalStateException("No original animation start");}
            case FAILURE_TIMER->{if(!(start||i.lastStore==CdExplosion.StoreKind.FAILURE_RESET)||c.output()==null)throw new IllegalStateException("No original animation timer prefix");}
            case FAILURE_SPEED->{if(i.lastStore!=CdExplosion.StoreKind.FAILURE_TIMER||c.output()==null)throw new IllegalStateException("No original animation speed prefix");}
            case FLOWER->{if(i.routine!=4||i.publications!=0||c.source().unsignedByte(ROUTINE_2)!=0||c.output()==null||c.output().id()!=0x1F||c.output().routine()!=0)throw new IllegalStateException("No original Flower conversion");}
            case DELETE->{if(i.routine!=4||i.publications!=0||c.source().unsignedByte(ROUTINE_2)==0||c.output()!=null)throw new IllegalStateException("No original Explosion delete");}
        }
        // A free result at the authentic MakePoints cut must have its original child store receipt.
        if(start&&(kind==CdExplosion.StoreKind.ANIMATION_RESULT||kind==CdExplosion.StoreKind.FAILURE_RESET||kind==CdExplosion.StoreKind.FAILURE_TIMER)&&i.routine==0&&!i.child&&c.source().unsignedByte(ROUTINE_2)==0&&placement.pool().findObjSlot().isPresent())
            throw new IllegalStateException("Omitted original A1 child publication");
    }
    private void childDrawBegin(Object call,CdObjectPlacementRuntime.Snapshot placed,CdObjectState source){
        requireChildInvocation();var i=childEffect;
        if(i.operation!=null||!actualChild(call)||i.drew||i.terminal||i.kind==Kind.EXPLOSION&&!i.animated||i.kind==Kind.POINTS&&i.publications!=1)
            throw new IllegalStateException("No original next child Draw site");
        requireFrame(i.frameCut);requireCut(i.cut);requirePlacement(i.placed);requirePlacement(placed);
        if(source!=placement.pool().state(i.whole))throw new IllegalArgumentException("Foreign physical Draw source");
        var expected=childRows(placed);var row=source.copyBytes();row[1]&=127;expected.set(i.whole-32,row);
        i.operation=new ChildOperation(call,0,false,null,placed,expected);
    }
    private static ArrayList<byte[]> childRows(CdObjectPlacementRuntime.Snapshot placed){var out=new ArrayList<byte[]>(96);for(var s:placed.pool().slots())out.add(s.copyBytes());return out;}
    private void childPrepared(Object call){var op=currentChild(call);if(op.prepared||op.after)throw new IllegalStateException("Repeated child preparation");
        requireFrame(op.frameCut);requireCut(op.cut);requirePlacement(op.placed);claim(call);op.prepared=true;}
    private void childDraw(Object call,CdObjectState source){var op=currentChild(call);
        if(op.publication||!op.prepared||op.used||op.after||source!=placement.pool().state(childEffect.whole))throw new IllegalStateException("No unconsumed original child Draw");
        requireFrame(op.frameCut);requireCut(op.cut);requirePlacement(op.placed);op.used=true;
        drawDynamic(childEffect.kind,placement.pool(),source.identity());
    }
    private void childAfter(Object call){var op=currentChild(call);if(!op.prepared||op.after||!op.publication&&!op.used)throw new IllegalStateException("Missing original child operation receipt");
        childPost(op);op.after=true;
    }
    private void childCompleted(Object call){var op=currentChild(call);if(!op.after)throw new IllegalStateException("Missing original child completion");childPost(op);
        var i=childEffect;
        // Keep the original operation alive until all next cut references and verification reads
        // have completed. A checkpoint fault still verifies its actual reached native prefix.
        i.sync();checkpoint();
        if(op.publication){i.publications=op.ordinal;i.lastStore=op.store;
            if(op.store==CdExplosion.StoreKind.INIT_HEADER)i.header=true;if(op.store==CdExplosion.StoreKind.POINTS_CHILD)i.child=true;
            if(op.store==CdExplosion.StoreKind.ANIMATION_RESULT)i.animated=true;
            if(op.store==CdExplosion.StoreKind.FLOWER||op.store==CdExplosion.StoreKind.DELETE)i.terminal=true;
        }else i.drew=true;
        i.operation=null;
    }
    private void childPost(ChildOperation op){requireFrame(op.frameCut);requireCut(op.cut);placement.validateRestore(op.placed);var actual=placement.capture();
        if(!op.placed.status().equals(actual.status())||!same(op.placed.savedFlags(),actual.savedFlags()))throw new IllegalArgumentException("Child changed full status/saved nonwrites");
        for(int n=0;n<96;n++)if(!Arrays.equals(op.expected.get(n),actual.pool().slots().get(n).copyBytes()))throw new IllegalArgumentException("Child changed an unowned full96 byte at "+(n+32));
    }
    /** Fault verification admits only the old cut or the prebuilt single reached store/Draw prefix.
     * It never grants completion: healthy continuation still needs every opaque after/completed receipt. */
    private void childFailed(ChildInvocation original){
        if(childEffect!=original||dispatch!=original.visit||Thread.currentThread()!=original.thread)throw new IllegalStateException("Foreign failed child invocation");
        var op=original.operation;
        if(op==null){requireFrame(original.frameCut);requireCut(original.cut);requirePlacement(original.placed);return;}
        requireFrame(op.frameCut);requireCut(op.cut);
        if(!op.prepared){requirePlacement(op.placed);return;}
        if(op.after){childPost(op);return;}
        // A direct native write is atomic over one immutable slot; a throwing observer may have
        // stopped immediately before or after it. Preserve that exact prefix, never replay it.
        var actual=placement.capture();placement.validateRestore(op.placed);
        if(!op.placed.status().equals(actual.status())||!same(op.placed.savedFlags(),actual.savedFlags()))throw new IllegalArgumentException("Failed child changed status/saved nonwrites");
        boolean before=true,after=true;for(int n=0;n<96;n++){var row=actual.pool().slots().get(n).copyBytes();before&=Arrays.equals(row,op.placed.pool().slots().get(n).copyBytes());after&=Arrays.equals(row,op.expected.get(n));}
        if(!before&&!after)throw new IllegalArgumentException("Failed child is outside its exact native prefix");
    }
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
        if(w>=32&&id==0x1d)return Kind.BUMPER;
        if(w>=32){var selected=switch(id){case 0x20->Kind.CC_SPIKES;case 0x21->Kind.BLOCK;case 0x22->Kind.HIDDEN;case 0x23->Kind.METAL_PLATFORM;case 0x24->Kind.KAMA;case 0x25->Kind.SICKLE;case 0x26->Kind.DOOR;case 0x27->Kind.FIRE;case 0x28->Kind.DRUM;default->null;};if(selected!=null)return selected;if(id==0x0A)throw stop(Boundary.SPRING_CHILD_OWNER,w,id);}
        if(w>=32&&id>=0x30&&id<=0x35)return switch(id){case 0x30->Kind.INTRO;case 0x31->Kind.METAL;case 0x32->Kind.AMY;case 0x33->Kind.HEART;case 0x34->Kind.EXHAUST;default->Kind.ROLL;};
        if(id==0x3B){if(w<32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.GAME_OVER;}
        if(id==0x3C)return Kind.TITLE;
        if(id==0x10){if(w<32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.RING;}
        if(id==0x18){if(w<32)throw stop(Boundary.PHYSICAL_OWNER,w,id);return Kind.EXPLOSION;}
        if(id==0x1C){if(w>=32){if((placement.pool().state(w).subtype()&128)!=0)return Kind.POINTS;throw stop(Boundary.PHYSICAL_OWNER,w,id);}return Kind.HUD;}
        if(id==0x19){if(w<32)return Kind.HUD;return placement.pool().state(w).subtype()>=8?Kind.TIME_POST:Kind.MONITOR;}
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
    static boolean sameCutForBumper(Cut a,Cut b){return sameCut(a,b);}
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
    /** Only the cached kernel's current private operation may borrow this actual dispatch.
     * Per-call snapshots are comparison cuts, never a second placement/Frame or capture owner. */
    private final class TimePostInvocation {
        final Thread thread=Thread.currentThread();final int whole;final CdObjectState source;
        Cut cut;ReadFrame frameCut;CdObjectPlacementRuntime.Snapshot placed;
        CdTimePost.OriginalCall current;long ordinal;boolean used;
        TimePostInvocation(int whole){this.whole=whole;source=placement.pool().state(whole);cut=read();frameCut=readFrame();placed=placement.capture();}
    }
    private TimePostInvocation timePostScope(){
        var i=timePostInvocation;
        if(i==null||i.thread!=Thread.currentThread()||dispatch==null||dispatch.kind!=Kind.TIME_POST
                ||dispatch.body!=timePost||dispatch.whole!=i.whole||dispatch.thread!=Thread.currentThread()
                ||walkingThread!=Thread.currentThread()||phase!=Phase.WALKING||adopting||drawing||plc)
            throw new IllegalStateException("No original current cached Time Post producer");
        guard();return i;
    }
    private void beforeTimePost(CdTimePost.OriginalCall c){
        var i=timePostScope();timePost.requireCurrent(c);
        if(i.current!=null||c.ordinal()!=i.ordinal+1)throw new IllegalStateException("Wrong/repeated native Time Post cursor");
        requireCut(i.cut);requireFrame(i.frameCut);requirePlacement(i.placed);requirePlacement(c.before());
        i.current=c;i.ordinal=c.ordinal();i.used=false;
    }
    private CdTimePost.OriginalCall timePostCall(CdTimePost.Site site,Object argument,boolean hasArgument){
        var i=timePostScope();var c=hasArgument?timePost.requireCurrentArgument(site,argument):timePost.requireCurrentSite(site);
        if(c!=i.current||i.used)throw new IllegalStateException("Expired/clone/repeated Time Post native site");
        requireCut(i.cut);requireFrame(i.frameCut);requirePlacement(i.placed);c.verifyNativePrefix();
        claim(c);i.used=true;return c;
    }
    private void afterTimePost(CdTimePost.OriginalCall c){
        var i=timePostScope();timePost.requireCurrent(c);
        if(i.current!=c||!c.returned())throw new IllegalStateException("Missing actual Time Post operation return");
        switch(c.site()){
            case OBJECT_STORE,SAVED_STORE,DELETE -> { }
            default -> {if(!i.used)throw new IllegalStateException("Original Time Post callback omitted");}
        }
        requireCut(i.cut);requireFrame(i.frameCut);c.verifyNativePrefix();
        // All fallible comparison capture is before these invocation-local reference assignments.
        var reached=placement.capture();i.placed=reached;i.current=null;checkpoint();
    }
    private void failedTimePost(CdTimePost.OriginalCall c,Throwable original){
        // The kernel captured original BEFORE any fallible observer/preparation, including read faults.
        // Distinct suppression belongs to that kernel; never replace it or suppress the same instance.
        var i=timePostScope();timePost.requireCurrent(c);
        requireCut(i.cut);requireFrame(i.frameCut);c.verifyNativePrefix();
        var reached=placement.capture();i.placed=reached;i.current=null;
    }
    private void runTimePost(int whole){
        requireWhole(Kind.TIME_POST,whole);
        if(timePostInvocation!=null)throw new IllegalStateException("Recursive Time Post body");
        var i=new TimePostInvocation(whole);timePostInvocation=i;
        try{
            var result=timePost.runWithReads(whole,new CdTimePost.PastGlobals(){
                public int timeAttackMode(){timePostCall(CdTimePost.Site.TIME_ATTACK_READ,null,false);return frame.environment().globals().timeAttackMode();}
                public int zone(){timePostCall(CdTimePost.Site.ZONE_READ,null,false);return frame.environment().movement().gates().zoneAct()>>>8;}
                public int rawTimeZone(){timePostCall(CdTimePost.Site.TIME_ZONE_READ,null,false);return frame.environment().movement().gates().timeZone();}
                public int rawTimeWarpDirection(){timePostCall(CdTimePost.Site.DIRECTION_READ,null,false);return frame.environment().movement().gates().timeWarpDirection();}
                public int cameraXWord(){timePostCall(CdTimePost.Site.CAMERA_READ,null,false);return read().cameraXWord();}
                public OptionalInt layer(){timePostCall(CdTimePost.Site.LAYER_READ,null,false);return read().layerByte();}
            });
            if(i.current!=null||!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Incomplete original Time Post body");
            requireCut(i.cut);requireFrame(i.frameCut);requirePlacement(i.placed);
        }catch(RuntimeException|Error original){
            verify(original,()->{requireCut(i.cut);requireFrame(i.frameCut);
                if(i.current!=null)i.current.verifyNativePrefix();else requirePlacement(i.placed);});throw original;
        }finally{timePostInvocation=null;}
    }
    private void timePostDirection(CdTimePost.DirectionCall actual){
        timePostCall(CdTimePost.Site.DIRECTION_STORE,actual,true);identity(actual.identity());
        var i=timePostScope();var before=read();
        var ready=frame.prepareObjectTimePostDirection(frame.state(),frame.environment(),actual.expectedRawDirection(),actual.rawDirection());
        publish(before,before,null,ready);
        // Adoption prebuilt and advanced the original invocation fence in its callback-free suffix.
    }
    private void timePostSound(CdTimePost.SubCpuCommand actual){
        timePostCall(CdTimePost.Site.SUB_CPU,actual,true);
        sound(new SonicCdGameModule.PastTimePostSubCpu(actual));
    }
    private void timePostDraw(CdObjectPool pool,CdObjectState.Identity actual){
        timePostCall(CdTimePost.Site.DRAW,actual,true);
        drawDynamic(Kind.TIME_POST,pool,actual);
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
            else{
                if(phase==Phase.WALKING&&bumper!=null&&bumper.step!=null&&bumper.step.original instanceof CdPinballContext.Draw){
                    var site=bumper.step;requireBumperSite(site);
                    if(w!=bumper.whole||value||!site.drawClearPending||(placement.pool().state(w).spriteFlags()&128)!=0)throw new IllegalStateException("Not original already-cleared Bumper queue continuation");
                    site.drawClearPending=false;return;
                }
                var p=placement.pool().capture();var s=placement.pool().state(w);publish(c,c,CdObjectPool.overlay(p,List.of(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|bit))),null);}}
    }

    private static Cut withReserved(Cut c,CdAssetBytes bank){extent(bank,1984);var s=c.core();var next=new SonicCdR31aLoadingVisit.State(s.phase(),s.initialization(),s.section(),s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),bank,s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls(),s.returnKind(),s.music());return copy(c,next,c.scrollLockWord(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut withSectionAndCalls(Cut c,com.openggf.game.soniccd.resources.CdR31aSectionArt.State section,List<SonicCdR31aLoadingVisit.Call> calls){var s=c.core();var next=new SonicCdR31aLoadingVisit.State(s.phase(),s.initialization(),section,s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.reserved(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),calls,s.returnKind(),s.music());return copy(c,next,c.scrollLockWord(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut copy(Cut c,SonicCdR31aLoadingVisit.State core,int scroll,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds){return new Cut(core,c.ring(),c.currentTerrain(),c.contact(),c.world(),c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),scroll,c.layerByte(),cpu,completed,sounds,c.amyCapturedByte());}
    private static CdAssetBytes reserved(Cut c,int w){if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");return c.core().reserved().slice((w-1)*64,64);}
    private static CdAssetBytes replace(CdAssetBytes bank,int w,CdAssetBytes row){extent(bank,1984);extent(row,64);if(w<1||w>31)throw new IllegalArgumentException("Reserved whole1..31");var next=bank.copy();System.arraycopy(row.copy(),0,next,(w-1)*64,64);return CdAssetBytes.copyOf(next);}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void extent(CdAssetBytes a,int n){if(Objects.requireNonNull(a).length()!=n)throw new IllegalArgumentException("Complete extent "+n+" required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE");}private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD");}
    private static void verify(Throwable original,Runnable check){try{check.run();}catch(RuntimeException|Error fault){if(fault!=original){for(var old:original.getSuppressed())if(old==fault)return;original.addSuppressed(fault);}}}
    private static Unowned stop(Boundary b,int w,int v){return new Unowned(b,w,v);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
