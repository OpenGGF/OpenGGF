package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Source-bound original USA R13C/D startup RunObjects and deferred DrawObjects.
 * N1ecc5f3af: Level/Object Functions.asm:RunObjects11-33,DrawObjects243-365;
 * Main.asm:261-275; each cached shared body retains its own native algorithm/citations.
 * Caller owns primary Body/publication, actual Core/Host/Frame lifecycle, levelFrames/spawn/camera,
 * art services/current and capture. No CPU/VDP/bus emulation, fixture row, default global or effect replay.
 * Host.validate MUST require its actual private ordinary-access/World stack, exact pass and owners;
 * public cuts/equal values confer no invocation. This pass creates all executable receipts privately.
 */
final class SonicCdAct3FutureObjectPass {
    interface Host {
        void validate(SonicCdAct3FutureObjectPass pass,CdPlayerFrameRuntime frame,CdR13FutureAssets assets,
                      CdR13ObjectAssets objects,CdObjectPlacementRuntime placement);
        Cut read();
        /** Prepare every Core/Host aggregate and all pure CAS checks BEFORE applyNative; adopt next once. */
        void adopt(Adoption adoption);
        /** Actual caller owns Frame.runBody and its external publications; complete only its original result. */
        void primary(PrimaryCall call);
        /** Actual World active access invokes Core.appendTitlePlc(currentState), ack only on actual return. */
        void submitTitlePlc(PlcSubmission submission);
        /** Optional actual full-Globals fence and reached contact/animation values. No defaults. */
        default ReusedOwners reusedOwners(ReusedOwners expected){return null;}

    }
    record Cut(Object programmeOwner,Object programme,CdRingGlobals ring,CdAssetBytes reservedSlots,
               CdAssetBytes cpuSat,Optional<CdObjectDrawQueue.CpuSat> completedCpu,List<SonicCdGameModule.PastSound> sounds,
               int cameraXWord,int cameraYWord,int timeAttackModeByte,OptionalInt layerByte,
               OptionalInt goodFutureByte,OptionalInt projectorByte,int scrollLockWord,OptionalLong plcHeadLong) {
        Cut {Objects.requireNonNull(programmeOwner);Objects.requireNonNull(programme);Objects.requireNonNull(ring);
            extent(reservedSlots,1984);extent(cpuSat,640);Objects.requireNonNull(completedCpu);sounds=List.copyOf(sounds);
            word(cameraXWord);word(cameraYWord);octet(timeAttackModeByte);optionalByte(layerByte);optionalByte(goodFutureByte);optionalByte(projectorByte);word(scrollLockWord);
            Objects.requireNonNull(plcHeadLong);if(plcHeadLong.isPresent()&&(plcHeadLong.getAsLong()<0||plcHeadLong.getAsLong()>0xFFFF_FFFFL))throw new IllegalArgumentException("Native PLC head LONG");}
    }
    record ReusedOwners(Object fence,CdPlayerBody.ContactGlobals contact,Optional<CdGlobalObjectAnimation.Extras> animations) {
        ReusedOwners{Objects.requireNonNull(fence);Objects.requireNonNull(contact);Objects.requireNonNull(animations);}
    }
    record ReusedValues(CdPlayerBody.ContactGlobals contact,Optional<CdGlobalObjectAnimation.Extras> animations) {
        ReusedValues{Objects.requireNonNull(contact);Objects.requireNonNull(animations);}
    }
    enum Boundary { OBJECT_FAMILY,OBJECT_INDEX,PLAYER_SLOT,PRIMARY_OWNER,PRIMARY_RESULT,GOOD_FUTURE,PROJECTOR,
                    LAYER,VINT,PLC_OWNER,PLC_HEAD,REUSED_GLOBALS_OWNER,SPILL_GLOBALS_OWNER }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;private final int wholeIndex,value;
        private Unowned(Boundary b,int whole,int value){super("R13 startup "+b+" at whole"+whole+" value$"+Integer.toHexString(value));boundary=b;wholeIndex=whole;this.value=value;}
        Boundary boundary(){return boundary;}int wholeIndex(){return wholeIndex;}int value(){return value;}
    }
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,HUD,TITLE,RING,SPRING,MONITOR,ANIMAL,TAM,ROLL,EXPLOSION,POINTS,FLOWER,LOST_RING,MONITOR_ITEM,NULL }
    private static final class Dispatch {
        final Kind kind;final int whole;final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
        CdPlayerFrameRuntime.ActorState ownerActor,actor;CdPlayerFrameRuntime.SharedState ownerShared,shared;
        Cut ownerCut,observedCut;CdObjectPlacementRuntime.Snapshot observedPlacement;
        Object operation;long ordinal;boolean drawn,prepared,completed,used;
        List<byte[]> expectedImages;byte[] expectedSaved;
        final Deque<EffectScope> effects=new ArrayDeque<>();long effectOrdinal,publications;
        CdMonitorItem.Result monitorResult;CdLostRing.InputReads lostInputs;CdLostRing.Ports lostPorts;
        Dispatch(Kind kind,int whole){this.kind=kind;this.whole=whole;}
    }
    /** Opaque original publication. Values are prepared cuts, never a native invocation certificate. */
    final class Adoption {
        private final Cut expected,next;private final CdObjectPool.Snapshot output;
        private final CdPlayerFrameRuntime.ObjectSolidPublication frameOutput;
        private final CdPlayerFrameRuntime.ActorState actor;private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placementCut;
        private final Runnable nativeWrite;
        private final ReusedOwners reusedExpected;private final ReusedValues reusedNext;
        private boolean applied;
        private CdPlayerFrameRuntime.ActorState afterActor;private CdPlayerFrameRuntime.SharedState afterShared;
        private CdObjectPlacementRuntime.Snapshot afterPlacement;
        private Adoption(Cut expected,Cut next,CdObjectPool.Snapshot output,CdPlayerFrameRuntime.ObjectSolidPublication frameOutput,Runnable nativeWrite){this(expected,next,output,frameOutput,nativeWrite,null,null);}
        private Adoption(Cut expected,Cut next,CdObjectPool.Snapshot output,CdPlayerFrameRuntime.ObjectSolidPublication frameOutput,Runnable nativeWrite,ReusedOwners reusedExpected,ReusedValues reusedNext){
            this.expected=expected;this.next=next;this.output=output;this.frameOutput=frameOutput;this.nativeWrite=nativeWrite;this.reusedExpected=reusedExpected;this.reusedNext=reusedNext;
            actor=frame.captureActor();shared=frame.captureShared();placementCut=placement.capture();if(output!=null)placement.pool().validateRestore(output);}
        ReusedOwners reusedExpected(){return reusedExpected;}ReusedValues reusedNext(){return reusedNext;}
        SonicCdAct3FutureObjectPass owner(){return SonicCdAct3FutureObjectPass.this;}Cut expected(){return expected;}Cut next(){return next;}
        void validateBefore(){if(!isCurrentAdoption(this)||applied)throw new IllegalStateException("Not original pending unapplied R13 adoption");
            guard();requireCut(expected,false);requireFrame(actor,shared);requirePlacement(placementCut);if(reusedExpected!=null)borrowReused(reusedExpected);if(output!=null)placement.pool().validateRestore(output);if(frameOutput!=null)frame.validatePreparedObjectSolidPublication(frameOutput);}
        void applyNative(){validateBefore();if(output!=null)placement.pool().restore(output);if(frameOutput!=null)frame.applyPreparedObjectSolidPublication(frameOutput);
            nativeWrite.run();applied=true;afterActor=frame.captureActor();afterShared=frame.captureShared();afterPlacement=placement.capture();}
    }
    /** This exact call exists only during primary whole0; caller owns the original Body producer and external projection. */
    final class PrimaryCall {
        private final int levelFrame;private final Object programmeOwner,programme;
        private final Object art;private final CdPlayerFrameRuntime.VIntClock clock;private final boolean paused;
        private boolean completed;
        private PrimaryCall(int levelFrame){this.levelFrame=levelFrame;var cut=read();programmeOwner=cut.programmeOwner();programme=cut.programme();art=frame.artState();clock=frame.vIntClock();paused=frame.paused();}
        SonicCdAct3FutureObjectPass owner(){return SonicCdAct3FutureObjectPass.this;}
        int levelFrameWord(){currentPrimary(this);return levelFrame;}
        void complete(CdPlayerFrameRuntime.BodyPublication original){currentPrimary(this);if(completed)throw new IllegalStateException("Repeated original primary completion");Objects.requireNonNull(original);
            var result=original.result();var cut=read();
            if(cut.programmeOwner()!=programmeOwner||cut.programme()!=programme||frame.state()!=result.state()||frame.environment()!=result.environment()||frame.display()!=result.display()
                    ||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused)throw new IllegalArgumentException("Primary left its original Frame/programme result");
            CdPlayerBody.DisplayEligibility selected=null;for(var effect:result.effects())if(effect instanceof CdPlayerBody.DisplayEligibility d){if(selected!=null)throw new IllegalArgumentException("Multiple original primary Display cuts");selected=d;}
            if(result.boundary()==CdPlayerBody.Boundary.INIT_RETURN){if(selected!=null||result.display().isPresent())throw new IllegalArgumentException("Primary Init has no Draw");}
            else if(selected==null||result.display().isEmpty()||selected.eligible()!=result.display().orElseThrow().queued())throw stop(Boundary.PRIMARY_RESULT,0,result.boundary().ordinal());
            completed=true;checkpoint();if(selected!=null&&selected.eligible()){dispatch.drawn=true;draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),selected.spriteFlags(),selected.centreXWord(),selected.centreYWord(),selected.yRadius(),selected.width(),selected.priority()));}}
    }
    /** Actual deferred PLC2 call; no general art setter, capacity guess, rollback, replay or success on throw. */
    final class PlcSubmission {
        private final CdTitleCard.PlcCall call;private final Cut expected;
        private final CdPlayerFrameRuntime.ActorState actor;private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placed;private Cut after;private boolean acknowledged;
        private PlcSubmission(CdTitleCard.PlcCall call){this.call=call;expected=read();actor=frame.captureActor();shared=frame.captureShared();placed=placement.capture();}
        SonicCdAct3FutureObjectPass owner(){return SonicCdAct3FutureObjectPass.this;}
        int nativeId(){return call.nativeId();}int wholeIndex(){return call.cut().wholeIndex();}
        void validate(){currentPlc(this);if(acknowledged)throw new IllegalStateException("Repeated Title PLC receipt");requireCut(expected,false);requireFrame(actor,shared);requirePlacement(placed);titleSource(call.cut());}
        void acknowledge(){currentPlc(this);if(acknowledged)throw new IllegalStateException("Repeated Title PLC completion");requireCut(expected,true);requireFrame(actor,shared);requirePlacement(placed);titleSource(call.cut());after=read();acknowledged=true;}
        Unowned unowned(){validate();return stop(Boundary.PLC_OWNER,wholeIndex(),nativeId());}
    }

    private final Host host;private final CdR13FutureAssets assets;private final CdR13ObjectAssets objects;
    private final CdPlayerFrameRuntime frame;private final CdObjectPlacementRuntime placement;private final CdAssetBytes objectIndex;
    private final CdObjectMappings mappings;private final CdPlayerMath math;private final CdPlayerTerrain terrain;private final CdSolidObject solid;
    private final CdRing ring;private final CdSpring spring;private final CdReservedHud hud;private final CdMonitor monitor;private CdAnimal animal;private final CdTitleCard title;
    private final CdR13ReusedObjectAssets reused;private CdTamabboh tam;private final CdSpinTunnelTag roll;
    private CdExplosion explosion;private CdPoints points;private CdFlower flower;private CdLostRing lostRing;private CdMonitorItem monitorItem;
    private boolean reusedRead;
    private Phase phase=Phase.READY;private CdObjectDrawQueue queue;private Dispatch dispatch;
    private boolean port,queuePort,plcPort,reading,checking;private Adoption pending;private PrimaryCall primaryCall;private PlcSubmission pendingPlc;

    SonicCdAct3FutureObjectPass(Host host,CdR13FutureAssets assets,CdR13ObjectAssets objects,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement){
        this.host=Objects.requireNonNull(host);this.assets=Objects.requireNonNull(assets);this.objects=Objects.requireNonNull(objects);this.frame=Objects.requireNonNull(frame);this.placement=Objects.requireNonNull(placement);
        if(!frame.stageSource().ownsAct3Future(assets)||!objects.ownsAssets(assets)||!placement.ownsAct3FutureAssets(assets)||placement.placements()!=assets.placements())throw new IllegalArgumentException("R13 pass requires exact retained Frame/source/metadata/placement");
        reused=CdR13ReusedObjectAssets.fromAssets(assets,objects);objectIndex=objects.view(CdR13ObjectAssets.View.OBJECT_INDEX);extent(objectIndex,256);mappings=CdObjectMappings.fromAct3FutureAssets(assets,objects,reused);
        math=new CdPlayerMath(assets.player());terrain=CdPlayerTerrain.fromStageTerrain(assets.terrain());solid=new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        ring=CdRing.fromAct3FutureAssets(placement,assets,objects);
        spring=CdSpring.fromAct3FutureAssets(placement,assets,objects,math,solid,
            c->{requireKind(Kind.SPRING);claim(c,()->true);poolCut(c.callCut());commitSolid(c.callCut(),c.expectedPlayer(),c.expectedGlobals(),new CdSolidObject.Completed(c.result()));},
            c->{requireKind(Kind.SPRING);claim(c,()->true);identity(c.identity());var ready=frame.prepareObjectSpringLaunch(c);var cut=read();publish(cut,cut,null,ready,()->{});},
            (p,id)->drawDynamic(Kind.SPRING,p,id,()->true),s->{requireKind(Kind.SPRING);sound(new SonicCdGameModule.PastSpringFm(s));});
        hud=new CdReservedHud(new CdReservedHud.Assets(address(CdR13ObjectAssets.View.MAP_SPR_HUD),address(CdR13ObjectAssets.View.MAP_SPR_MONITOR_TIME)),
            new CdReservedHud.Slots(){public CdAssetBytes read(int whole){requireWhole(Kind.HUD,whole);return reserved(SonicCdAct3FutureObjectPass.this.read(),whole);}
                public void write(int whole,CdAssetBytes expected,CdAssetBytes next){reservedWrite(Kind.HUD,whole,expected,next);}},
            whole->{requireWhole(Kind.HUD,whole);drawReserved(Kind.HUD,whole);});
        monitor=CdMonitor.withPastOwner(placement,new CdMonitor.Assets(objects.monitorTime(),address(CdR13ObjectAssets.View.MAP_SPR_MONITOR_TIME)),solid,terrain,
            new CdMonitor.PrefixSolidOwner(){public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut c){requireKind(Kind.MONITOR);identity(c.objectIdentity());poolCut(c.callCut());if(!c.expectedPlayer().equals(primary())||!c.expectedGates().equals(gates())||!c.expectedGlobals().equals(solidGlobals()))throw new IllegalArgumentException("Stale original Monitor Solid cut");return liveMonitor();}
                public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall c){requireKind(Kind.MONITOR);claim(c,()->true);identity(c.cut().objectIdentity());poolCut(c.cut().callCut());commitSolid(c.cut().callCut(),c.cut().expectedPlayer(),c.cut().expectedGlobals(),c.outcome());return liveMonitor();}},
            (before,value)->{requireKind(Kind.MONITOR);angle(before,value);},(p,id)->drawDynamic(Kind.MONITOR,p,id,()->true),s->{requireKind(Kind.MONITOR);sound(new SonicCdGameModule.PastMonitorFm(s));});
        animal=CdAnimal.fromAct3FutureAssets(placement,assets,objects,math,terrain,
            c->{requireKind(Kind.ANIMAL);claim(c,()->animal.isCurrentCall(c));requirePlacement(c.actualCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
            c->{requireKind(Kind.ANIMAL);claim(c,()->animal.isCurrentCall(c));requirePlacement(c.actualCut());source(c.expectedSource());drawDynamic(Kind.ANIMAL,placement.pool(),c.expectedSource().identity(),()->animal.isCurrentCall(c));},
            new CdActiveAnimal.DispatchObserver(){public void beforePublication(CdActiveAnimal.Publication c){animalPublication(c);}public void afterPublication(CdActiveAnimal.Publication c){animalAfter(c,true);}public void beforeCall(Object c){animalStart(c,animal.currentCallOrdinal(c));}public void afterCall(Object c){animalAfter(c,false);}},
            new CdActiveAnimal.ReceiptGuard(){public void prepared(Object c){animalPrepared(c);}public void completed(Object c){animalCompleted(c);}});
        // BEGIN R13 REUSED CACHED PRODUCERS 20261106
        roll=CdSpinTunnelTag.fromAct3FutureAssets(placement,assets,reused);
        tam=CdTamabboh.fromAct3FutureAssets(placement,assets,reused,
            c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentPrimaryAngleCall(c));poolCut(c.callCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
            c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentPlayerReadCall(c));poolCut(c.callCut());source(c.expectedSource());return c.field()==CdTamabboh.PlayerField.X_WORD?frame.state().motion().centreX16_16()>>>16:frame.state().motion().centreY16_16()>>>16;},
            c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentFmCall(c));poolCut(c.callCut());source(c.expectedSource());if(placement.pool().findObjSlot().orElse(-1)!=c.firstFreeWholeIndex())throw new IllegalArgumentException("Stale actual Tam first-free");sound(new SonicCdGameModule.PastTamabbohFm(c.sound()));},
            (pool,id)->drawDynamic(Kind.TAM,pool,id,()->tam.isCurrentDrawCall(pool,id)));
        explosion=CdExplosion.fromAct3FutureAssets(placement,assets,reused,new CdExplosion.DispatchObserver(){
            public void beforePublication(CdExplosion.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output(),-1);}
            public void afterPublication(CdExplosion.Publication c){effectAfter(c);}
            public void beforeCall(Object c){var v=(CdExplosion.DrawCall)c;effectCall(c,0,v.cut(),v.source(),true);}
            public void afterCall(Object c){effectAfter(c);}
        },c->effectPort(c,()->drawDynamic(Kind.EXPLOSION,placement.pool(),c.source().identity(),()->explosion.isCurrentCall(c))),
                new CdExplosion.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        points=CdPoints.fromAct3FutureAssets(placement,assets,reused,new CdPoints.DispatchObserver(){
            public void beforePublication(CdPoints.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output(),-1);}
            public void afterPublication(CdPoints.Publication c){effectAfter(c);}
            public void beforeCall(Object c){var v=(CdPoints.DrawCall)c;effectCall(c,0,v.cut(),v.source(),true);}
            public void afterCall(Object c){effectAfter(c);}
        },c->effectPort(c,()->drawDynamic(Kind.POINTS,placement.pool(),c.source().identity(),()->points.isCurrentCall(c))),
                new CdPoints.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        flower=CdFlower.fromAct3FutureAssets(placement,assets,reused,terrain,new CdFlower.DispatchObserver(){
            public void beforePublication(CdFlower.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.source(),c.output(),-1);}
            public void afterPublication(CdFlower.Publication c){effectAfter(c);}
            public void beforeCall(Object c){switch(c){
                case CdFlower.FloorCall v->effectCall(c,v.ordinal(),v.cut(),v.source(),false);
                case CdFlower.PrimaryAngleCall v->effectCall(c,v.ordinal(),v.cut(),v.source(),false);
                case CdFlower.DrawCall v->effectCall(c,v.ordinal(),v.cut(),v.source(),true);
                default->throw new IllegalArgumentException("Unknown Flower native operation");}}
            public void afterCall(Object c){effectAfter(c);}
        },c->effectPort(c,()->angle(solidGlobals().primaryAngle(),c.value())),
                c->effectPort(c,()->drawDynamic(Kind.FLOWER,placement.pool(),c.source().identity(),()->flower.isCurrentCall(c))),
                new CdFlower.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        monitorItem=CdMonitorItem.fromAct3FutureAssets(placement,assets,reused,this::monitorShoes,this::monitorShield,this::monitorRings,
            (pool,id)->{var c=monitorOperation(CdMonitorItem.DrawCall.class);effectPort(c,()->drawDynamic(Kind.MONITOR_ITEM,pool,id,()->monitorItem.isCurrentPrimitive(c)));},
            new CdMonitorItem.DispatchObserver(){public void beforeOperation(Object c){monitorBefore(c);}public void afterOperation(Object c){effectAfter(c);}public void failedOperation(Object c,Throwable original){effectFailed(c);}},
            new CdMonitorItem.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        lostRing=CdLostRing.fromAct3FutureAssets(placement,assets,reused,math,terrain,
            c->{lostCall(c,c.cut());angle(solidGlobals().primaryAngle(),c.value());},
            c->{lostCall(c,c.cut());switch(c.request()){
                case CdLostRing.SoundRequest v->sound(new SonicCdGameModule.PastLostRingFm(v.sound()));
                case CdLostRing.RewardFmRequest v->sound(new SonicCdGameModule.PastRingFm(v.sound()));
                case CdLostRing.RewardSubCpuRequest v->sound(new SonicCdGameModule.PastRingSubCpu(v.command()));
                default->throw new IllegalArgumentException("Unknown actual LostRing sound");}},
            (pool,id)->drawDynamic(Kind.LOST_RING,pool,id,()->lostRing.isCurrentDrawCall(pool,id)),
            (globals,calls)->{lostInvocation();if(!lostRing.isCurrentCollectCall(globals,calls))throw new IllegalStateException("Foreign actual LostRing reward");CdLostRing.collectRing(globals,calls);});
        // END R13 REUSED CACHED PRODUCERS 20261106
        title=new CdTitleCard(placement,objects.titleCardAssets(),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int whole){requireWhole(Kind.TITLE,whole);return reserved(SonicCdAct3FutureObjectPass.this.read(),whole);}
            public void write(int whole,CdAssetBytes expected,CdAssetBytes next){reservedWrite(Kind.TITLE,whole,expected,next);}},
            this::titleRead,this::titleRead,this::titleStore,this::titlePlc,this::titleDraw);
    }
    private int address(CdR13ObjectAssets.View v){return v.range(assets.identity().file()).nativeAddress();}
    boolean isCurrentAdoption(Adoption a){return a!=null&&a==pending&&port&&(phase==Phase.WALKING||phase==Phase.DRAINING);}
    private void currentPrimary(PrimaryCall c){if(c==null||c!=primaryCall||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.PRIMARY||dispatch.whole!=0||port||queuePort||plcPort)throw new IllegalStateException("No actual current primary dispatch");guard();}
    boolean isCurrentPlcSubmission(PlcSubmission p){return p!=null&&p==pendingPlc&&plcPort&&!port&&!queuePort&&phase==Phase.WALKING&&dispatch!=null&&dispatch.kind==Kind.TITLE&&p.wholeIndex()==dispatch.whole;}
    private void currentPlc(PlcSubmission p){if(p==null||p!=pendingPlc||!plcPort||port||queuePort||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.TITLE||p.wholeIndex()!=dispatch.whole)throw new IllegalStateException("No original current Title PLC2");guard();}

    /** Caller already performed actual unpaused selector8, levelFrames increment and SpawnObjects. */
    void runWalk(int actualLevelFrame){
        if((phase!=Phase.READY&&phase!=Phase.DONE)||port||queuePort||plcPort||dispatch!=null||pending!=null||primaryCall!=null||reading||checking||reusedRead)throw new IllegalStateException("R13 pass not idle");
        word(actualLevelFrame);guard();if(frame.paused())throw new IllegalStateException("Paused loop has no unpaused object walk");
        if(frame.environment().globals().levelFrames()!=actualLevelFrame)throw new IllegalArgumentException("Not actual levelFrames");
        var cut=read();queue=new CdObjectDrawQueue(new DrawSlots(),mappings,cut.cpuSat().copy(),List.of(new CdObjectDrawQueue.CdSlotRef(1),new CdObjectDrawQueue.CdSlotRef(2),new CdObjectDrawQueue.CdSlotRef(3),new CdObjectDrawQueue.CdSlotRef(4),new CdObjectDrawQueue.CdSlotRef(5),new CdObjectDrawQueue.CdSlotRef(31)));
        phase=Phase.WALKING;try{queue.beginPass();for(int whole=0;whole<128;whole++){
            guard();int id=id(whole);if(id==0)continue;int handler=handler(id,whole);Kind kind=kind(id,whole,handler);dispatch=new Dispatch(kind,whole);
            try{if(kind!=Kind.PRIMARY)checkpoint();execute(kind,whole,actualLevelFrame);if(dispatch.operation!=null||!dispatch.effects.isEmpty())throw new IllegalStateException("Unretired original Animal operation");}finally{dispatch=null;}}
            phase=Phase.WALKED;
        }catch(RuntimeException|Error original){phase=Phase.FAILED;queue.close();throw original;}
    }
    CdObjectDrawQueue.CpuSat finishDraw(CdObjectDrawQueue.DrawCameras cameras){
        if(phase!=Phase.WALKED||port||queuePort||plcPort||dispatch!=null)throw new IllegalStateException("No complete R13 walk to drain");Objects.requireNonNull(cameras);guard();var live=read();
        if(cameras.foreground().xWord()!=live.cameraXWord()||cameras.foreground().yWord()!=live.cameraYWord())throw new IllegalArgumentException("Not actual post-scroll camera");
        phase=Phase.DRAINING;try{var cpu=queue.finishAfterCamera(cameras);var cut=read();publish(cut,copy(cut,cut.ring(),cut.reservedSlots(),CdAssetBytes.copyOf(cpu.bytes()),Optional.of(cpu),cut.sounds(),cut.scrollLockWord()),null,null,()->{});queue.close();queue=null;phase=Phase.DONE;return cpu;}
        catch(RuntimeException|Error original){phase=Phase.FAILED;if(queue!=null)queue.close();throw original;}
    }
    void discard(){if(port||queuePort||plcPort||phase==Phase.WALKING||phase==Phase.DRAINING||dispatch!=null)throw new IllegalStateException("Active R13 pass");if(queue!=null)queue.close();queue=null;phase=Phase.DISCARDED;}
    private void execute(Kind kind,int whole,int levelFrame){var cut=read();var e=frame.environment();var g=e.movement().gates();switch(kind){
        case PRIMARY->{var call=new PrimaryCall(levelFrame);primaryCall=call;try{host.primary(call);if(!call.completed)throw stop(Boundary.PRIMARY_OWNER,0,id(0));}finally{primaryCall=null;}}
        case HUD->hud.run(whole,new CdReservedHud.Globals(e.globals().debugCheat(),cut.ring().rings(),vIntLow(),cut.timeAttackModeByte(),g.timeWarpDirection(),e.globals().timeWarpTimer(),levelFrame));
        case TITLE->title.run(whole);
        case NULL->{if(whole==0)throw stop(Boundary.PLAYER_SLOT,whole,id(whole));if(whole>=32)placement.pool().objNull(whole);else{var raw=reserved(cut,whole).copy();raw[0]=0;publish(cut,withReserved(cut,replace(cut.reservedSlots(),whole,CdAssetBytes.copyOf(raw))),null,null,()->{});}}
        case RING->ring.executeSelected(whole,new CdRing.Inputs(frame.stageSource().variant(),frame.stageSource().stage(),g.zoneAct()>>>8,cut.cameraXWord(),g.timeZone(),g.timeWarpDirection()),
            new CdRing.GlobalsPort(){public CdRingGlobals read(){requireKind(Kind.RING);return SonicCdAct3FutureObjectPass.this.read().ring();}public void write(CdRingGlobals next){requireKind(Kind.RING);var old=SonicCdAct3FutureObjectPass.this.read();publish(old,withRing(old,next),null,null,()->{});}},
            new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound s){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingFm(s));}public void subCpuCommand(CdRing.SubCpuCommand c){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingSubCpu(c));}public void drawObject(CdObjectPool p,int w){requireWhole(Kind.RING,w);drawDynamic(Kind.RING,p,p.state(w).identity(),()->true);}});
        case SPRING->spring.run(whole,new CdSpring.Globals(cut.cameraXWord()),new CdSpring.SolidInputs(primary(),gates(),solidGlobals()));
        case MONITOR->{var result=monitor.runPast(whole,monitorReads(),new CdMonitor.SolidInputs(primary(),gates(),solidGlobals()));if(!result.state().equals(placement.pool().state(whole))||!result.finalPlayer().equals(primary())||!result.finalGlobals().equals(solidGlobals()))throw new IllegalStateException("Incomplete original Monitor return");}
        case TAM->{var result=tam.run(whole,tamReads());if(!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Incomplete original Tam return");}
        case ROLL->runRoll(whole);
        case EXPLOSION->effectResult(explosion.run(whole).state());
        case POINTS->effectResult(points.run(whole).state());
        case FLOWER->effectResult(flower.run(whole).state());
        case LOST_RING->{dispatch.lostInputs=lostInputs(whole);dispatch.lostPorts=lostPorts();var result=lostRing.run(whole,dispatch.lostInputs,dispatch.lostPorts);if(!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Incomplete original LostRing result");}
        case MONITOR_ITEM->{var result=monitorItem.run(whole,Optional.of(new CdMonitorItem.PrimarySupport(frame.state().header().slotId(),frame.state().motion().flags())));if(dispatch.monitorResult!=result)throw new IllegalStateException("Not original MonitorItem completion");effectResult(result.state());}
        case ANIMAL->{var result=animal.runWithReads(whole,animalReads());if(!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Incomplete original Animal return");}
    }}
    private int id(int whole){return whole==0?frame.state().header().slotId():whole<32?reserved(read(),whole).unsignedByte(0):placement.pool().state(whole).id();}
    private int handler(int id,int whole){int at=(id-1)*4;if(at<0||at>252)throw stop(Boundary.OBJECT_INDEX,whole,id);return (int)objectIndex.unsignedLong(at);}
    private Kind kind(int id,int whole,int handler){
        if(handler==(int)objectIndex.unsignedLong(3*4))return Kind.NULL; // Actual native ObjNull entry4 and its aliases, not a foreign programme address.
        if(whole==0){if(id==1||id==2)return Kind.PRIMARY;throw stop(Boundary.PLAYER_SLOT,whole,id);}
        if(whole<32){if(whole==4&&id==0x3C)return Kind.TITLE;if(((whole==2||whole==3||whole==5)&&id==0x1C)||(whole==31&&id==0x19))return Kind.HUD;throw stop(Boundary.PLAYER_SLOT,whole,id);}
        return switch(id){case 0x10->Kind.RING;case 0x0A->Kind.SPRING;case 0x19->{if(placement.pool().state(whole).subtype()>=8)throw stop(Boundary.OBJECT_FAMILY,whole,id);yield Kind.MONITOR;}case 0x24->Kind.ANIMAL;case 0x22->Kind.TAM;case 0x34->Kind.ROLL;case 0x18->Kind.EXPLOSION;case 0x1F->Kind.FLOWER;case 0x11->Kind.LOST_RING;case 0x1A->Kind.MONITOR_ITEM;case 0x1C->{if((placement.pool().state(whole).subtype()&128)==0)throw stop(Boundary.OBJECT_FAMILY,whole,id);yield Kind.POINTS;}case 0x3C->Kind.TITLE;default->throw stop(Boundary.OBJECT_FAMILY,whole,id);};
    }
    private void guard(){if(checking)throw new IllegalStateException("Reentrant R13 owner validation");if(frame.closed()||!frame.stageSource().ownsAct3Future(assets)||!objects.ownsAssets(assets)||!placement.ownsAct3FutureAssets(assets)||placement.placements()!=assets.placements())throw new IllegalStateException("Closed/foreign R13 source binding");checking=true;try{host.validate(this,frame,assets,objects,placement);}finally{checking=false;}}
    /** Read-only complete values work even at the genuine pre-primary Body entitlement, where capture is not admitted. */
    private record ReadFrame(CdPlayerBody.State state,CdPlayerBody.Environment environment,Optional<CdPlayerBody.DisplayPublication> display,
                             Object art,CdPlayerFrameRuntime.VIntClock clock,boolean paused) { }
    private ReadFrame readFrame(){return new ReadFrame(frame.state(),frame.environment(),frame.display(),frame.artState(),frame.vIntClock(),frame.paused());}
    private void requireReadFrame(ReadFrame before){if(frame.closed()||frame.state()!=before.state()||frame.environment()!=before.environment()||frame.display()!=before.display()||frame.artState()!=before.art()||frame.vIntClock()!=before.clock()||frame.paused()!=before.paused())throw new IllegalArgumentException("Host read changed its complete Frame preimage");}
    private Cut read(){guard();if(reading)throw new IllegalStateException("Reentrant semantic Host read");var before=readFrame();var placed=placement.capture();reading=true;
        try{var cut=Objects.requireNonNull(host.read(),"actual complete R13 Host cut");requireReadFrame(before);requirePlacement(placed);return cut;}
        catch(RuntimeException|Error original){try{requireReadFrame(before);requirePlacement(placed);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}finally{reading=false;}}
    private void requireCut(Cut expected,boolean allowHead){var now=read();if(expected.programmeOwner()!=now.programmeOwner()||expected.programme()!=now.programme()||!expected.ring().equals(now.ring())||!same(expected.reservedSlots(),now.reservedSlots())||!same(expected.cpuSat(),now.cpuSat())||!expected.completedCpu().equals(now.completedCpu())||!expected.sounds().equals(now.sounds())||expected.cameraXWord()!=now.cameraXWord()||expected.cameraYWord()!=now.cameraYWord()||expected.timeAttackModeByte()!=now.timeAttackModeByte()||!expected.layerByte().equals(now.layerByte())||!expected.goodFutureByte().equals(now.goodFutureByte())||!expected.projectorByte().equals(now.projectorByte())||expected.scrollLockWord()!=now.scrollLockWord()||!allowHead&&!expected.plcHeadLong().equals(now.plcHeadLong()))throw new IllegalArgumentException("Stale full R13 programme/scalar/reserved/CPU cut");}
    private void requireFrame(CdPlayerFrameRuntime.ActorState a,CdPlayerFrameRuntime.SharedState s){if(!a.equals(frame.captureActor())||!s.equals(frame.captureShared()))throw new IllegalArgumentException("Stale complete R13 Frame pair");}
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot expected){placement.validateRestore(expected);var now=placement.capture();if(!expected.status().equals(now.status())||!expected.pool().slots().equals(now.pool().slots())||!same(expected.savedFlags(),now.savedFlags()))throw new IllegalArgumentException("Stale R13 full96/status/bank cut");}
    private void poolCut(CdObjectPool.Snapshot cut){placement.pool().validateRestore(cut);if(!cut.slots().equals(placement.pool().capture().slots()))throw new IllegalArgumentException("Stale full96 callback cut");}
    private void requireKind(Kind kind){if(phase!=Phase.WALKING||dispatch==null||dispatch.kind!=kind||port||queuePort||plcPort)throw new IllegalStateException("No original current "+kind+" dispatch");guard();if(dispatch.ownerActor!=null){requireFrame(dispatch.ownerActor,dispatch.ownerShared);requireCut(dispatch.ownerCut,false);}}
    private void requireWhole(Kind kind,int whole){requireKind(kind);if(dispatch.whole!=whole)throw new IllegalStateException("Foreign physical callback index");}
    private void identity(CdObjectState.Identity id){if(dispatch==null||dispatch.whole<32||id.wholeIndex()!=dispatch.whole)throw new IllegalArgumentException("Foreign current source identity");}
    private void source(CdObjectState s){identity(s.identity());if(!placement.pool().state(dispatch.whole).equals(s))throw new IllegalArgumentException("Stale source64");}
    private void claim(Object call,BooleanSupplier original){if(dispatch==null||call==null||!original.getAsBoolean()||!dispatch.consumed.add(call))throw new IllegalStateException("Cloned/repeated/foreign native callback");if(dispatch.kind==Kind.ANIMAL){if(dispatch.operation!=call||!dispatch.prepared||dispatch.used)throw new IllegalStateException("Missing original Animal preparation");dispatch.used=true;}}
    private void checkpoint(){dispatch.ownerActor=frame.captureActor();dispatch.ownerShared=frame.captureShared();dispatch.ownerCut=read();}
    private void publish(Cut expected,Cut next,CdObjectPool.Snapshot output,CdPlayerFrameRuntime.ObjectSolidPublication prepared,Runnable nativeWrite){publish(expected,next,output,prepared,nativeWrite,null,null);}
    private void publish(Cut expected,Cut next,CdObjectPool.Snapshot output,CdPlayerFrameRuntime.ObjectSolidPublication prepared,Runnable nativeWrite,ReusedOwners reusedExpected,ReusedValues reusedNext){if(port||pending!=null)throw new IllegalStateException("Recursive R13 publication");var a=new Adoption(expected,next,output,prepared,nativeWrite,reusedExpected,reusedNext);pending=a;port=true;
        try{a.validateBefore();host.adopt(a);if(!a.applied)throw new IllegalStateException("Actual caller omitted adoption");requireCut(next,false);requireFrame(a.afterActor,a.afterShared);requirePlacement(a.afterPlacement);if(reusedNext!=null){var now=borrowReused(null);if(!reusedNext.contact().equals(now.contact())||!reusedNext.animations().equals(now.animations()))throw new IllegalStateException("Missing exact reused globals adoption");}if(dispatch!=null){checkpoint();effectCheckpoint();}}finally{pending=null;port=false;}}
    private void sound(SonicCdGameModule.PastSound sound){var cut=read();var sounds=new ArrayList<>(cut.sounds());sounds.add(Objects.requireNonNull(sound));publish(cut,withSounds(cut,sounds),null,null,()->{});}
    private void angle(int expected,int value){var ready=frame.prepareObjectPrimaryAngle(expected,value);var cut=read();publish(cut,cut,null,ready,()->{});}
    private void commitSolid(CdObjectPool.Snapshot pool,CdSolidObject.Player expected,CdSolidObject.Globals globals,CdSolidObject.Outcome outcome){poolCut(pool);CdObjectState image;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication ready;
        if(outcome instanceof CdSolidObject.Completed done){var r=done.completedResult();image=r.object();others=r.otherSlots();fm=r.fmRequests();ready=frame.prepareObjectSolidResult(expected,globals,r);}else{var p=((CdSolidObject.NativeStopped)outcome).prefix();image=p.object();others=p.otherSlots();fm=p.fmRequests();ready=frame.prepareObjectSolidPrefix(expected,globals,p);}
        identity(image.identity());var images=new ArrayList<CdObjectState>();images.add(image);images.addAll(others);var output=CdObjectPool.overlay(pool,images);var cut=read();var sounds=new ArrayList<>(cut.sounds());for(var s:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(s)));publish(cut,withSounds(cut,sounds),output,ready,()->{});}
    private CdSolidObject.Player primary(){var s=frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    private CdSolidObject.Gates gates(){var e=frame.environment();return new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic());}
    private CdSolidObject.Globals solidGlobals(){var e=frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    private CdMonitor.LiveSolid liveMonitor(){return new CdMonitor.LiveSolid(primary(),gates(),solidGlobals());}
    private int vIntLow(){var raw=frame.vIntClock().absoluteValue();if(raw.isEmpty())throw stop(Boundary.VINT,dispatch.whole,0);return (int)raw.getAsLong()&255;}
    private CdMonitor.PastGlobals monitorReads(){return new CdMonitor.PastGlobals(){public int rawTimeZone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeZone();}public int rawTimeWarpDirection(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeWarpDirection();}public int cameraXWord(){requireKind(Kind.MONITOR);return read().cameraXWord();}public int timeAttackMode(){requireKind(Kind.MONITOR);return read().timeAttackModeByte();}public int zone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().zoneAct()>>>8;}public int layer(){requireKind(Kind.MONITOR);return read().layerByte().orElseThrow(()->stop(Boundary.LAYER,dispatch.whole,0));}public int timeStopTimer(){requireKind(Kind.MONITOR);return read().ring().timeStopTimer();}};}
    private CdActiveAnimal.Reads animalReads(){return new CdActiveAnimal.Reads(){public int timeZone(){requireKind(Kind.ANIMAL);return frame.environment().movement().gates().timeZone();}public int goodFuture(){requireKind(Kind.ANIMAL);return read().goodFutureByte().orElseThrow(()->stop(Boundary.GOOD_FUTURE,dispatch.whole,0));}public int projDestroyed(){requireKind(Kind.ANIMAL);return read().projectorByte().orElseThrow(()->stop(Boundary.PROJECTOR,dispatch.whole,0));}public int timeWarpDir(){requireKind(Kind.ANIMAL);return frame.environment().movement().gates().timeWarpDirection();}public int cameraXWord(){requireKind(Kind.ANIMAL);return read().cameraXWord();}public int act(){requireKind(Kind.ANIMAL);return frame.environment().movement().gates().zoneAct()&255;}public int primaryAngle(){requireKind(Kind.ANIMAL);return solidGlobals().primaryAngle();}};}

    private void animalStart(Object call,long ordinal){if(dispatch==null||dispatch.kind!=Kind.ANIMAL||port||queuePort||plcPort||dispatch.operation!=null||!animal.isCurrentCall(call)||ordinal!=dispatch.ordinal+1)throw new IllegalStateException("No original next Animal operation");guard();var d=dispatch;d.operation=call;d.prepared=false;d.completed=false;d.used=false;d.observedPlacement=placement.capture();d.observedCut=read();d.actor=frame.captureActor();d.shared=frame.captureShared();d.expectedImages=null;d.expectedSaved=null;}
    private void animalPublication(CdActiveAnimal.Publication c){animalStart(c,c.ordinal());requirePlacement(c.actualCut());source(c.expectedSource());var d=dispatch;var images=new ArrayList<byte[]>();for(var s:c.actualCut().pool().slots())images.add(s.copyBytes());byte[] saved=c.actualCut().savedFlags().copy();switch(c.kind()){
        case SOURCE_STORE,CHILD_STORE->{var before=c.before();var output=c.output();if(before==null||output==null||!before.identity().equals(output.identity())||!c.actualCut().pool().slots().get(before.identity().dynamicIndex()).equals(before))throw new IllegalArgumentException("Stale original Animal source/child store");CdObjectPool.overlay(c.actualCut().pool(),List.of(output));images.set(output.identity().dynamicIndex(),output.copyBytes());}
        case DELETE->images.set(c.expectedSource().identity().dynamicIndex(),new byte[64]);
        case SAVED_FLAG_CLEAR->{if(c.savedByteIndex()<0||c.savedByteIndex()>=saved.length||Byte.toUnsignedInt(saved[c.savedByteIndex()])!=c.previousValue()||c.value()!=(c.previousValue()&127))throw new IllegalArgumentException("Stale Animal saved BCLR");saved[c.savedByteIndex()]=(byte)c.value();}}
        d.expectedImages=List.copyOf(images);d.expectedSaved=saved;}
    private void animalPrepared(Object c){var d=dispatch;if(d==null||d.operation!=c||d.prepared||!animal.isCurrentCall(c))throw new IllegalStateException("Missing original Animal preparation");requireFrame(d.actor,d.shared);requireCut(d.observedCut,false);requirePlacement(d.observedPlacement);d.prepared=true;}
    private void animalAfter(Object c,boolean publication){var d=dispatch;if(d==null||d.operation!=c||!d.prepared||d.completed||!animal.isCurrentCall(c))throw new IllegalStateException("Missing original Animal completion");guard();if(publication){requireFrame(d.actor,d.shared);requireCut(d.observedCut,false);var now=placement.capture();if(!d.observedPlacement.status().equals(now.status())||!Arrays.equals(d.expectedSaved,now.savedFlags().copy()))throw new IllegalStateException("Animal changed saved/status nonwrites");for(int n=0;n<96;n++)if(!Arrays.equals(d.expectedImages.get(n),now.pool().slots().get(n).copyBytes()))throw new IllegalStateException("Animal full96 mismatch");}else if(!d.used)throw new IllegalStateException("Animal callback omitted private consumption");d.completed=true;}
    private void animalCompleted(Object c){var d=dispatch;if(d==null||d.operation!=c||!d.completed||!animal.isCurrentCall(c))throw new IllegalStateException("Missing Animal completion receipt");d.ordinal++;d.operation=null;d.observedCut=null;d.observedPlacement=null;d.actor=null;d.shared=null;d.expectedImages=null;d.expectedSaved=null;}

    // BEGIN R13 REUSED SCOPED PUBLICATIONS 20261106
    /** Per-dispatch observer stack, not a retained native owner. Only Flower floor→angle may nest. */
    private static final class EffectScope {
        final Object call;final CdObjectPlacementRuntime.Snapshot before;final boolean publication;
        CdPlayerFrameRuntime.ActorState actor;CdPlayerFrameRuntime.SharedState shared;Cut cut;
        List<CdObjectState> expected;byte[] saved;boolean prepared,used,after;
        EffectScope(Object call,CdObjectPlacementRuntime.Snapshot before,boolean publication,
                    CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,Cut cut){
            this.call=call;this.before=before;this.publication=publication;this.actor=actor;this.shared=shared;this.cut=cut;
            expected=before.pool().slots();saved=before.savedFlags().copy();
        }
    }
    private boolean actualEffect(Object c){return dispatch!=null&&switch(dispatch.kind){
        case EXPLOSION->explosion.isCurrentCall(c);case FLOWER->flower.isCurrentCall(c);case POINTS->points.isCurrentCall(c);
        case MONITOR_ITEM->monitorItem.isCurrentCall(c);default->false;};}
    private EffectScope effectTop(Object c){var d=dispatch;if(d==null||d.effects.isEmpty()||d.effects.peek().call!=c||!actualEffect(c))throw new IllegalStateException("No original current effect operation");return d.effects.peek();}
    private EffectScope effectBegin(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,boolean publication){
        if(dispatch==null||port||queuePort||plcPort||!actualEffect(call))throw new IllegalStateException("No private current effect dispatch");
        requireKind(dispatch.kind);requirePlacement(cut);source(source);var d=dispatch;
        if(!d.effects.isEmpty()){var parent=d.effects.peek();if(!(parent.call instanceof CdFlower.FloorCall)||!(call instanceof CdFlower.PrimaryAngleCall)||!parent.prepared||parent.after)
                throw new IllegalStateException("Unsupported nested effect operation");effectVerify(parent);}
        long next=d.effectOrdinal+1;if(ordinal!=0&&ordinal!=next)throw new IllegalStateException("Not next original effect ordinal");d.effectOrdinal=next;
        var scope=new EffectScope(call,cut,publication,frame.captureActor(),frame.captureShared(),read());d.effects.push(scope);return scope;
    }
    private void effectPublication(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,CdObjectState before,CdObjectState output,int savedIndex){
        if(dispatch==null||!actualEffect(call))throw new IllegalStateException("No original effect publication");
        boolean operationOrdinal=dispatch.kind==Kind.FLOWER||dispatch.kind==Kind.MONITOR_ITEM;
        if(!operationOrdinal&&ordinal!=dispatch.publications+1)throw new IllegalStateException("Not next original native publication");
        var scope=effectBegin(call,operationOrdinal?ordinal:0,cut,source,true);if(!operationOrdinal)dispatch.publications=ordinal;
        var images=new ArrayList<>(cut.pool().slots());
        if(savedIndex!=-1)throw new IllegalArgumentException("No saved-bank write belongs to these effect publications");
        if(output==null){ /* Delete is checked as all64 zero in effectVerify; no synthetic image or write. */ }
        else{if(before==null||!before.identity().equals(output.identity())||!cut.pool().slots().get(before.identity().dynamicIndex()).equals(before))throw new IllegalArgumentException("Stale native effect publication preimage");
            CdObjectPool.overlay(cut.pool(),List.of(output));images.set(output.identity().dynamicIndex(),output);}
        // DeleteObject clears all64 bytes. Compare bytes separately; no fabricated state is written back.
        scope.expected=List.copyOf(images);
    }
    private void effectCall(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,boolean draw){
        var scope=effectBegin(call,ordinal,cut,source,false);
        if(draw){var images=new ArrayList<>(scope.expected);images.set(source.identity().dynamicIndex(),source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127));scope.expected=List.copyOf(images);}
    }
    private boolean effectDelete(EffectScope s){return switch(s.call){
        case CdExplosion.Publication c->c.output()==null;case CdPoints.Publication c->c.output()==null;
        case CdMonitorItem.DeleteCall ignored->true;default->false;};}
    private int effectSourceWhole(EffectScope s){return switch(s.call){
        case CdExplosion.Publication c->c.source().identity().wholeIndex();case CdPoints.Publication c->c.source().identity().wholeIndex();
        case CdMonitorItem.DeleteCall c->c.source().identity().wholeIndex();
        default->dispatch.whole;};}
    private void effectVerify(EffectScope s){
        guard();requireFrame(s.actor,s.shared);requireCut(s.cut,false);var live=placement.capture();
        if(!s.before.status().equals(live.status())||!Arrays.equals(s.saved,live.savedFlags().copy()))throw new IllegalStateException("Effect changed placement status or saved nonwrites");
        int deleted=effectDelete(s)?effectSourceWhole(s):-1;
        for(int n=0;n<96;n++){byte[] expected=deleted==n+32?new byte[64]:s.expected.get(n).copyBytes();
            if(!Arrays.equals(expected,live.pool().slots().get(n).copyBytes()))throw new IllegalStateException("Effect changed an unowned full96 byte at whole "+(n+32));}
    }
    private void effectPrepared(Object call){var s=effectTop(call);if(s.prepared||s.after)throw new IllegalStateException("Repeated effect preparation");
        requireFrame(s.actor,s.shared);requireCut(s.cut,false);requirePlacement(s.before);s.prepared=true;}
    private void effectUse(Object call){var s=effectTop(call);if(!s.prepared||s.used||s.after||!dispatch.consumed.add(call))throw new IllegalStateException("Repeated/cloned/unprepared effect callback");
        requireKind(dispatch.kind);requirePlacement(s.before);s.used=true;}
    private void effectPort(Object call,Runnable work){var s=effectTop(call);effectUse(call);work.run();s.actor=frame.captureActor();s.shared=frame.captureShared();s.cut=read();}
    private void effectAfter(Object call){var s=effectTop(call);if(!s.prepared||s.after)throw new IllegalStateException("Missing original effect preparation");
        if(call instanceof CdFlower.FloorCall c){Objects.requireNonNull(flower.currentFloorResult(c));s.used=true;}
        if(dispatch.kind==Kind.MONITOR_ITEM&&!s.publication&&!(call instanceof CdMonitorItem.DrawCall)&&!(call instanceof CdMonitorItem.DeleteCall)){
            Object result=monitorItem.currentResult(call);
            if(call instanceof CdMonitorItem.MappingReadCall){if(!(result instanceof Integer))throw new IllegalStateException("Missing actual mapping-read result");}
            else if(call instanceof CdMonitorItem.MotionCall){if(!(result instanceof CdPlayerMovement.RawObjectMoveResult))throw new IllegalStateException("Missing actual ObjMove result");}
            else if(call instanceof CdMonitorItem.CompletionCall){if(!(result instanceof CdMonitorItem.Result r))throw new IllegalStateException("Missing original MonitorItem completion");dispatch.monitorResult=r;}
            else if(call instanceof CdMonitorItem.ShoesCall){if(!(result instanceof CdMonitorItem.AwardResult))throw new IllegalStateException("Missing actual Shoes completion");}
            else if(call instanceof CdMonitorItem.ShieldCall){if(!(result instanceof CdMonitorItem.ShieldAwardResult))throw new IllegalStateException("Missing actual Shield completion");}
            else if(call instanceof CdMonitorItem.RingCall){if(!(result instanceof CdMonitorItem.RingAwardResult))throw new IllegalStateException("Missing actual Ring completion");}
            else throw new IllegalStateException("Unexpected completed Monitor reward port");s.used=true;
        }
        if(!s.publication&&!s.used&&!(call instanceof CdMonitorItem.DeleteCall))throw new IllegalStateException("Native callback omitted private receipt");
        effectVerify(s);s.after=true;
    }
    private void effectCompleted(Object call){var s=effectTop(call);if(!s.after)throw new IllegalStateException("Missing original effect completion");effectVerify(s);dispatch.effects.pop();checkpoint();
        if(!dispatch.effects.isEmpty()){var parent=dispatch.effects.peek();if(!(parent.call instanceof CdFlower.FloorCall)||!(call instanceof CdFlower.PrimaryAngleCall))throw new IllegalStateException("Unsupported completed effect nesting");
            // Only the genuine angle adoption crosses the suspended floor's Frame image.
            parent.actor=s.actor;parent.shared=s.shared;effectVerify(parent);}}
    private void effectFailed(Object call){var s=effectTop(call);effectVerify(s);dispatch.effects.pop();}
    private void effectResult(CdObjectState state){if(!dispatch.effects.isEmpty()||!placement.pool().state(dispatch.whole).equals(state))throw new IllegalStateException("Incomplete/foreign completed native effect result");}
    private <T> T monitorOperation(Class<T> type){requireKind(Kind.MONITOR_ITEM);var s=dispatch.effects.peek();if(s==null||!type.isInstance(s.call)||!monitorItem.isCurrentPrimitive(s.call))throw new IllegalStateException("No actual Monitor primitive callback");return type.cast(s.call);}
    private void monitorBefore(Object call){long ordinal=monitorItem.currentOrdinal(call);switch(call){
        case CdMonitorItem.Publication c->effectPublication(c,ordinal,c.cut(),c.source(),c.source(),c.output(),-1);
        case CdMonitorItem.MappingReadCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.MotionCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.ShoesCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.ShieldCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.RingCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.DeleteCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        case CdMonitorItem.DrawCall c->effectCall(c,ordinal,c.cut(),c.source(),true);
        case CdMonitorItem.CompletionCall c->effectCall(c,ordinal,c.cut(),c.source(),false);
        default->throw new IllegalArgumentException("Unknown original Monitor operation");}}
    private void effectCheckpoint(){if(dispatch!=null&&!dispatch.effects.isEmpty()){var scope=dispatch.effects.peek();if(scope.prepared){scope.actor=frame.captureActor();scope.shared=frame.captureShared();scope.cut=read();}}}
    private CdTamabboh.Reads tamReads(){return new CdTamabboh.Reads(){
        public int goodFuture(){requireKind(Kind.TAM);return read().goodFutureByte().orElseThrow(()->stop(Boundary.GOOD_FUTURE,dispatch.whole,0));}
        public int timeZone(){requireKind(Kind.TAM);return frame.environment().movement().gates().timeZone();}
        public int timeWarpDirection(){requireKind(Kind.TAM);return frame.environment().movement().gates().timeWarpDirection();}
        public int debugMode(){requireKind(Kind.TAM);return frame.environment().globals().debugMode();}
        public int cameraXWord(){requireKind(Kind.TAM);return read().cameraXWord();}
        public int primaryAngle(){requireKind(Kind.TAM);return solidGlobals().primaryAngle();}};}
    private void runRoll(int whole){requireKind(Kind.ROLL);var before=placement.capture();var state=frame.state();var env=frame.environment();var gates=env.movement().gates();var source=placement.pool().state(whole);var cut=read();
        var result=roll.step(source,state.motion(),new CdSpinTunnelTag.Gates(gates.zoneAct()>>>8,env.globals().debugMode(),env.globals().debugCheat(),env.controls().rawHeld()));
        requireKind(Kind.ROLL);requirePlacement(before);if(result.expectedObject()!=source||result.player().expectedMotion()!=state.motion())throw new IllegalStateException("Not original private Roll result");
        var ready=frame.prepareObjectSpinTunnelResult(state,env,result.player());var output=CdObjectPool.overlay(before.pool(),List.of(result.object()));
        publish(cut,cut,output,ready,()->{});if(result.drawObject())drawDynamic(Kind.ROLL,placement.pool(),source.identity(),()->true);
        // Reload AFTER actual Draw; saved BCLR precedes the actual full DeleteObject.
        var despawn=roll.despawn(placement.pool().state(whole),new CdSpinTunnelTag.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),read().cameraXWord()));
        if(despawn.deleteObject()){if(despawn.loadedClear().isPresent()){var c=despawn.loadedClear().orElseThrow();placement.savedFlag(c.savedId(),c.period());var live=read();publish(live,live,null,null,()->placement.bclrSavedFlag(c.savedId(),c.period(),CdObjectPlacementRuntime.SavedBit.LOADED));}
            var live=read();publish(live,live,null,null,()->placement.pool().deleteObject(whole));}}
    boolean isCurrentReusedOwnersRead(){return reusedRead&&phase==Phase.WALKING&&dispatch!=null&&!queuePort&&!plcPort;}
    private ReusedOwners borrowReused(ReusedOwners expected){
        if(reusedRead||phase!=Phase.WALKING||dispatch==null||queuePort||plcPort||dispatch.kind!=Kind.LOST_RING&&dispatch.kind!=Kind.MONITOR_ITEM
                ||port&&(pending==null||pending.reusedExpected==null))throw new IllegalStateException("No original reused owner read");
        if(dispatch.kind==Kind.LOST_RING) {if(!lostRing.isCurrentInvocation(dispatch.whole,dispatch.lostInputs,dispatch.lostPorts))throw new IllegalStateException("No original LostRing borrow");}
        else if(dispatch.effects.isEmpty()||!monitorItem.isCurrentPrimitive(dispatch.effects.peek().call))throw new IllegalStateException("No original Monitor reward borrow");
        guard();var before=readFrame();var placed=placement.capture();reusedRead=true;try{var value=host.reusedOwners(expected);requireReadFrame(before);requirePlacement(placed);if(value==null)throw stop(Boundary.REUSED_GLOBALS_OWNER,dispatch.whole,0);
            if(expected!=null&&(value.fence()!=expected.fence()||!value.contact().equals(expected.contact())||!value.animations().equals(expected.animations())))throw new IllegalArgumentException("Stale full actual reused Globals fence");return value;
        }catch(RuntimeException|Error original){try{requireReadFrame(before);requirePlacement(placed);}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}throw original;}finally{reusedRead=false;}}
    private int vIntWord(){var value=frame.vIntClock().absoluteValue();if(value.isEmpty())throw stop(Boundary.VINT,dispatch.whole,0);return(int)value.getAsLong()&65535;}
    private void lostInvocation(){requireKind(Kind.LOST_RING);if(dispatch.lostInputs==null||dispatch.lostPorts==null||!lostRing.isCurrentInvocation(dispatch.whole,dispatch.lostInputs,dispatch.lostPorts))throw new IllegalStateException("No original LostRing invocation");}
    private void lostCall(Object operation,CdLostRing.SourceCut cut){lostInvocation();if(cut!=lostRing.currentSource(operation)||!dispatch.consumed.add(cut))throw new IllegalStateException("Cloned/repeated LostRing receipt");requirePlacement(cut.placement());source(cut.source());}
    private CdLostRing.InputReads lostInputs(int whole){return new CdLostRing.InputReads(){
        public int levelVIntCounter(){requireWhole(Kind.LOST_RING,whole);return vIntWord();}
        public int runObjectsD7(){requireWhole(Kind.LOST_RING,whole);return 127-whole;}
        public int zoneByte(){requireWhole(Kind.LOST_RING,whole);return frame.environment().movement().gates().zoneAct()>>>8;}
        public int bottomBoundWord(){requireWhole(Kind.LOST_RING,whole);return frame.environment().movement().bounds().bottom();}};}
    private CdLostRing.Ports lostPorts(){return new CdLostRing.Ports(new CdRing.GlobalsPort(){
        public CdRingGlobals read(){lostInvocation();return SonicCdAct3FutureObjectPass.this.read().ring();}
        public void write(CdRingGlobals value){lostCall(value,lostRing.currentSource(value));var cut=SonicCdAct3FutureObjectPass.this.read();publish(cut,withRing(cut,Objects.requireNonNull(value)),null,null,()->{});}
    },new CdLostRing.SpillGlobalsPort(){
        public CdRingSpillGlobals read(){lostInvocation();return borrowReused(null).animations().orElseThrow(()->stop(Boundary.SPILL_GLOBALS_OWNER,dispatch.whole,0)).spill();}
        public void write(CdRingSpillGlobals value){lostCall(value,lostRing.currentSource(value));var expected=borrowReused(null);var old=expected.animations().orElseThrow(()->stop(Boundary.SPILL_GLOBALS_OWNER,dispatch.whole,0));
            var next=new CdGlobalObjectAnimation.Extras(old.logSpikeTimerByte(),old.logSpikeFrameByte(),old.unusedTimerByte(),old.unusedFrameByte(),Objects.requireNonNull(value));var cut=SonicCdAct3FutureObjectPass.this.read();publish(cut,cut,null,null,()->{},expected,new ReusedValues(expected.contact(),Optional.of(next)));}
    });}
    private CdMonitorItem.RingAwardResult monitorRings(CdMonitorItem.RingAwardCall request){var c=monitorOperation(CdMonitorItem.RingCall.class);if(c.request()!=request)throw new IllegalStateException("Foreign original Ring request");final CdMonitorItem.RingAwardResult[] done=new CdMonitorItem.RingAwardResult[1];effectPort(c,()->{
        var cut=read();var reward=cut.ring().monitorItemTen();var sound=reward.oneUp()?CdMonitorItem.RingAwardSound.SCMD_YESSFX:CdMonitorItem.RingAwardSound.FM_RING;var result=new CdMonitorItem.RingAwardResult(sound);
        publish(cut,withRing(cut,reward.globals()),null,null,()->{});sound(new SonicCdGameModule.PastMonitorItemRing(sound));done[0]=result;});return done[0];}
    private CdMonitorItem.ShieldAwardResult monitorShield(CdMonitorItem.ShieldAwardCall request){var c=monitorOperation(CdMonitorItem.ShieldCall.class);if(c.request()!=request)throw new IllegalStateException("Foreign original Shield request");var result=new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);effectPort(c,()->{
        var expected=borrowReused(null);var old=expected.contact();var contact=new CdPlayerBody.ContactGlobals(old.timeWarp(),1,old.combineRing());var cut=read();
        var values=new ReusedValues(contact,expected.animations());var raw=reserved(cut,6).copy();raw[0]=3;var reserved=withReserved(cut,replace(cut.reservedSlots(),6,CdAssetBytes.copyOf(raw)));
        var fm=new SonicCdGameModule.PastMonitorItemShieldFm(CdMonitorItem.ShieldSound.FM_SHIELD);var sounds=new ArrayList<>(reserved.sounds());sounds.add(fm);var sounded=withSounds(reserved,sounds);
        publish(cut,cut,null,null,()->{},expected,values);publish(cut,reserved,null,null,()->{});publish(reserved,sounded,null,null,()->{});});return result;}
    private CdMonitorItem.AwardResult monitorShoes(CdMonitorItem.AwardCall request){var c=monitorOperation(CdMonitorItem.ShoesCall.class);if(c.request()!=request)throw new IllegalStateException("Foreign original Shoes request");final CdMonitorItem.AwardResult[] done=new CdMonitorItem.AwardResult[1];effectPort(c,()->{
        var movement=frame.environment().movement();var before=new CdPlayerSpeedShoesReward.State(movement.gates().speedShoes(),frame.state().timers().speedShoes(),movement.physics());
        var reward=CdPlayerSpeedShoesReward.give(before,frame.stageSource().variant(),movement.gates().timeZone());var ready=frame.prepareObjectSpeedShoesReward(before,reward);
        var observations=reward.commands().stream().map(command->switch(command){case SCMD_FADEPCM->CdMonitorItem.AwardSound.SCMD_FADEPCM;case SCMD_SHOESMUS->CdMonitorItem.AwardSound.SCMD_SHOESMUS;}).toList();var result=new CdMonitorItem.AwardResult(observations);var cut=read();publish(cut,cut,null,ready,()->{});
        for(var command:reward.commands())sound(new SonicCdGameModule.PastSpeedShoesSubCpu(command));done[0]=result;});return done[0];}
    // END R13 REUSED SCOPED PUBLICATIONS 20261106

    private void reservedWrite(Kind kind,int whole,CdAssetBytes expected,CdAssetBytes next){requireWhole(kind,whole);extent(next,64);var cut=read();if(!same(expected,reserved(cut,whole)))throw new IllegalArgumentException("Stale reserved source64");publish(cut,withReserved(cut,replace(cut.reservedSlots(),whole,next)),null,null,()->{});}
    private void titleSource(CdTitleCard.SourceCut c){if(dispatch==null||dispatch.kind!=Kind.TITLE||dispatch.whole!=c.wholeIndex())throw new IllegalArgumentException("Foreign Title source");requirePlacement(c.actualPlacement());var actual=dispatch.whole<32?reserved(read(),dispatch.whole):CdAssetBytes.copyOf(placement.pool().state(dispatch.whole).copyBytes());if(!same(actual,c.expectedSource()))throw new IllegalArgumentException("Stale Title full64");}
    private int titleRead(CdTitleCard.ReadCall c){requireKind(Kind.TITLE);claim(c,()->true);titleSource(c.cut());if(c.field()==CdTitleCard.ReadField.ACT_BYTE)return frame.environment().movement().gates().zoneAct()&255;return (int)read().plcHeadLong().orElseThrow(()->stop(Boundary.PLC_HEAD,dispatch.whole,0));}
    private void titleStore(CdTitleCard.StoreCall c){requireKind(Kind.TITLE);claim(c,()->true);titleSource(c.cut());var cut=read();if(c.field()==CdTitleCard.StoreField.CONTROL_LOCK_BYTE){var ready=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),c.field(),c.value());publish(cut,cut,null,ready,()->{});}else{int word=(cut.scrollLockWord()&255)|(c.value()<<8);publish(cut,copy(cut,cut.ring(),cut.reservedSlots(),cut.cpuSat(),cut.completedCpu(),cut.sounds(),word),null,null,()->{});}}
    private void titlePlc(CdTitleCard.PlcCall c){requireKind(Kind.TITLE);claim(c,()->true);titleSource(c.cut());if(c.nativeId()!=2||pendingPlc!=null||plcPort)throw new IllegalStateException("Foreign/reentrant Title PLC");var p=new PlcSubmission(c);pendingPlc=p;plcPort=true;
        try{p.validate();host.submitTitlePlc(p);if(!p.acknowledged)throw new IllegalStateException("Actual Core omitted Title PLC receipt");requireCut(p.after,false);requireFrame(p.actor,p.shared);requirePlacement(p.placed);checkpoint();}
        catch(RuntimeException|Error original){try{requireCut(p.expected,true);requireFrame(p.actor,p.shared);requirePlacement(p.placed);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
        finally{pendingPlc=null;plcPort=false;}}
    private void titleDraw(CdTitleCard.DrawCall c){requireKind(Kind.TITLE);claim(c,()->true);titleSource(c.cut());if(dispatch.whole<32)drawReserved(Kind.TITLE,dispatch.whole);else drawDynamic(Kind.TITLE,placement.pool(),placement.pool().state(dispatch.whole).identity(),()->true);}
    private void drawDynamic(Kind kind,CdObjectPool pool,CdObjectState.Identity id,BooleanSupplier actual){requireKind(kind);identity(id);if(pool!=placement.pool()||!actual.getAsBoolean()||dispatch.drawn)throw new IllegalStateException("Foreign/repeated DrawObject");var s=pool.state(id.wholeIndex());dispatch.drawn=true;draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(id.wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)));}
    private void drawReserved(Kind kind,int whole){requireWhole(kind,whole);if(dispatch.drawn)throw new IllegalStateException("Repeated reserved DrawObject");var r=reserved(read(),whole);dispatch.drawn=true;draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),r.unsignedByte(1),r.unsignedWord(8),r.unsignedWord(12),r.unsignedByte(22),r.unsignedByte(25),r.unsignedByte(24)));}
    private void draw(CdObjectDrawQueue.DrawCallCut c){if(queuePort||port)throw new IllegalStateException("Recursive DrawObject");var cut=read();queuePort=true;try{queue.drawObject(c,new CdObjectDrawQueue.CameraWords(cut.cameraXWord(),cut.cameraYWord()));}finally{queuePort=false;}}
    private final class DrawSlots implements CdObjectDrawQueue.CdDrawSlots {
        private void current(){if((!queuePort&&phase!=Phase.DRAINING)||queue==null||port)throw new IllegalStateException("No private live R13 queue dereference");guard();}
        public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){current();int whole=ref.wholeIndex();if(whole==0){var s=frame.state();var m=s.motion();var h=s.header();return new CdObjectDrawQueue.LiveDrawSlot(h.slotId(),m.spriteFlags(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.centreX16_16()&65535,h.mappingAddress(),s.animation().mapFrame(),h.tile());}if(whole<32){var r=reserved(SonicCdAct3FutureObjectPass.this.read(),whole);return new CdObjectDrawQueue.LiveDrawSlot(r.unsignedByte(0),r.unsignedByte(1),r.unsignedWord(8),r.unsignedWord(12),r.unsignedWord(10),(int)r.unsignedLong(4),r.unsignedByte(26),r.unsignedWord(2));}var s=placement.pool().state(whole);return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));}
        public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){current();int whole=ref.wholeIndex(),bit=value?128:0;var cut=SonicCdAct3FutureObjectPass.this.read();if(whole==0){publish(cut,cut,null,null,()->frame.publishDrawObjectOnScreen(value));return;}if(whole<32){var raw=reserved(cut,whole).copy();raw[1]=(byte)((raw[1]&127)|bit);publish(cut,withReserved(cut,replace(cut.reservedSlots(),whole,CdAssetBytes.copyOf(raw))),null,null,()->{});return;}var pool=placement.pool().capture();var s=placement.pool().state(whole);publish(cut,cut,CdObjectPool.overlay(pool,List.of(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|bit))),null,()->{});}
    }
    private static Cut copy(Cut c,CdRingGlobals ring,CdAssetBytes reserved,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds,int scroll){return new Cut(c.programmeOwner(),c.programme(),ring,reserved,cpu,completed,sounds,c.cameraXWord(),c.cameraYWord(),c.timeAttackModeByte(),c.layerByte(),c.goodFutureByte(),c.projectorByte(),scroll,c.plcHeadLong());}
    private static Cut withRing(Cut c,CdRingGlobals r){return copy(c,r,c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds(),c.scrollLockWord());}
    private static Cut withReserved(Cut c,CdAssetBytes r){return copy(c,c.ring(),r,c.cpuSat(),c.completedCpu(),c.sounds(),c.scrollLockWord());}
    private static Cut withSounds(Cut c,List<SonicCdGameModule.PastSound> s){return copy(c,c.ring(),c.reservedSlots(),c.cpuSat(),c.completedCpu(),s,c.scrollLockWord());}
    private static CdAssetBytes reserved(Cut c,int whole){if(whole<1||whole>31)throw new IllegalArgumentException("Reserved index1..31");return c.reservedSlots().slice((whole-1)*64,64);}
    private static CdAssetBytes replace(CdAssetBytes bank,int whole,CdAssetBytes row){extent(bank,1984);extent(row,64);if(whole<1||whole>31)throw new IllegalArgumentException("Reserved index1..31");byte[] next=bank.copy();System.arraycopy(row.copy(),0,next,(whole-1)*64,64);return CdAssetBytes.copyOf(next);}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void extent(CdAssetBytes value,int count){if(Objects.requireNonNull(value).length()!=count)throw new IllegalArgumentException("Complete byte extent "+count+" required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE");}private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD");}
    private static void optionalByte(OptionalInt v){Objects.requireNonNull(v);if(v.isPresent())octet(v.getAsInt());}
    private static Unowned stop(Boundary b,int whole,int value){return new Unowned(b,whole,value);}
}
