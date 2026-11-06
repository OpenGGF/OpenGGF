package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One source-bound R12B unpaused RunObjects walk and its deferred CPU DrawObjects drain.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/Object Functions.asm:11-46 (RunObjects),
 * Object Functions.asm:137-196,243-365,382-567; each cached body cites its native owner.
 * Root owns selector8, level-frame increment, SpawnObjects, camera/tail and every retained partition.
 * This adapter owns no native image, Frame, pool, bank, clock, renderer or rewind key. Its queue and
 * callback receipts are transient. Public/descriptive values never establish invocation authority.
 */
final class SonicCdAct2PastObjectPass {
    interface Ports {
        void validate(SonicCdAct2PastObjectPass pass,CdPlayerFrameRuntime frame,CdR12bTerrainAssets terrain,
                      CdR12bPlayerAssets player,CdObjectPlacementRuntime placement);
        Cut read();
        /** Preflight all actual scalar owners; applyNative once, then adopt next with no callback gap. */
        void adopt(Adoption adoption);
    }
    record Cut(SonicCdAct2PastWorldHost.Globals globals,CdLevelGlobals level,int goodFutureByte,
               OptionalInt goodFutureFlags,CdAssetBytes reservedSlots,CdAssetBytes cpuSat,
               Optional<CdObjectDrawQueue.CpuSat> completedCpu,List<SonicCdGameModule.PastSound> sounds,
               int cameraXWord,int cameraYWord,int timeAttackModeByte,OptionalInt layerByte) {
        Cut {
            Objects.requireNonNull(globals);Objects.requireNonNull(level);octet(goodFutureByte);
            Objects.requireNonNull(goodFutureFlags);if(goodFutureFlags.isPresent())octet(goodFutureFlags.getAsInt());
            extent(reservedSlots,31*64);extent(cpuSat,640);Objects.requireNonNull(completedCpu);
            sounds=List.copyOf(sounds);word(cameraXWord);word(cameraYWord);octet(timeAttackModeByte);
            Objects.requireNonNull(layerByte);if(layerByte.isPresent())octet(layerByte.getAsInt());
        }
    }
    enum Boundary { OBJECT_KIND, OBJECT_INDEX, SONIC_MAIN, PLAYER_SLOT, UNKNOWN_LAYER, UNKNOWN_VINT }
    static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary;private final int wholeIndex,value;
        private Unowned(Boundary boundary,int wholeIndex,int value){super(boundary+" at R12B whole "+wholeIndex+", value $"+Integer.toHexString(value));this.boundary=boundary;this.wholeIndex=wholeIndex;this.value=value;}
        Boundary boundary(){return boundary;}int wholeIndex(){return wholeIndex;}int value(){return value;}
    }
    /** Actual opaque per-port receipt. Constructor and executable closures are private to this pass. */
    final class Adoption {
        private final Cut expected,next;
        private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placementCut;
        private final CdObjectPool.Snapshot poolOutput;
        private final Runnable preflight,nativeWrite;
        private boolean applied;
        private CdPlayerFrameRuntime.ActorState afterActor;
        private CdPlayerFrameRuntime.SharedState afterShared;
        private CdObjectPlacementRuntime.Snapshot afterPlacement;
        private Adoption(Cut expected,Cut next,CdObjectPool.Snapshot poolOutput,Runnable preflight,Runnable nativeWrite){
            this.expected=expected;this.next=next;this.poolOutput=poolOutput;this.preflight=preflight;this.nativeWrite=nativeWrite;
            actor=frame.captureActor();shared=frame.captureShared();placementCut=placement.capture();
            if(poolOutput!=null)placement.pool().validateRestore(poolOutput);
        }
        SonicCdAct2PastObjectPass owner(){return SonicCdAct2PastObjectPass.this;}
        Cut expected(){return expected;}Cut next(){return next;}
        void applyNative(){
            if(!isCurrentAdoption(this)||applied)throw new IllegalStateException("Escaped/repeated R12B adoption");
            guard();requireCut(expected);requireFrame(actor,shared);requirePlacement(placementCut);
            preflight.run(); // All pure preflights precede the first pool/Frame write.
            if(poolOutput!=null)placement.pool().restore(poolOutput);
            nativeWrite.run();applied=true;
            afterActor=frame.captureActor();afterShared=frame.captureShared();afterPlacement=placement.capture();
        }
    }
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,RESERVED,RING,SPRING,MONITOR,TIME_POST,COLLAPSE,PLATFORM,TAM,ANIMAL,SWING,ROBOT,ANTON,BOULDER,WALL,NULL }
    private final Ports ports;private final CdR12bTerrainAssets terrain;private final CdR12bPlayerAssets player;
    private final CdPlayerFrameRuntime frame;private final CdObjectPlacementRuntime placement;
    private final CdAssetBytes objectIndex;private final CdObjectMappings mappings;
    private final CdPlayerMath math;private final CdPlayerTerrain probes;private final CdSolidObject solid;
    private final CdPlayerObjectTouch primaryScanner;private final CdPlayerTouchResponse primaryResponse;
    private CdRing ring;private CdSpring spring;private CdReservedHud hud;
    private CdMonitor monitor;private CdTimePost post;private CdCollapsePlatform collapse;
    private CdPlatform platform;private CdTamabboh tam;private CdAnimal animal;private CdBoulder boulder;
    private CdSwing swing;private CdRobotGenerator robot;private CdAnton anton;
    private CdBreakableWall wall;
    private Phase phase=Phase.READY;private CdObjectDrawQueue queue;private Dispatch dispatch;
    private boolean port,queuePort;private Adoption pending;
    private static final class Dispatch {
        final Kind kind;final int whole;final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean drawn;long ordinal;Object operation;boolean prepared,completed,used;
        CdObjectPlacementRuntime.Snapshot observedCut;Cut observedGlobals;
        CdPlayerFrameRuntime.ActorState ownerActor;CdPlayerFrameRuntime.SharedState ownerShared;Cut ownerCut;
        CdPlayerFrameRuntime.ActorState actor;CdPlayerFrameRuntime.SharedState shared;
        List<byte[]> expectedImages;byte[] expectedSaved;CdSwing.PlayerCut playerCut;
        int wallBranch,wallSolids,wallFms,wallHalves,wallDraws;
        Dispatch(Kind kind,int whole){this.kind=kind;this.whole=whole;}
    }
    SonicCdAct2PastObjectPass(Ports ports,CdR12bTerrainAssets terrain,CdR12bPlayerAssets player,
                            CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement){
        this.ports=Objects.requireNonNull(ports);this.terrain=Objects.requireNonNull(terrain);this.player=Objects.requireNonNull(player);
        this.frame=Objects.requireNonNull(frame);this.placement=Objects.requireNonNull(placement);
        if(!frame.stageSource().ownsAct2Past(terrain,player)||placement.placements()!=terrain.placements())
            throw new IllegalArgumentException("Pass requires the exact retained R12B Frame/pair/placement");
        objectIndex=terrain.objects().view(CdR12bObjectAssets.View.OBJECT_INDEX);extent(objectIndex,256);
        mappings=CdObjectMappings.fromAct2PastAssets(terrain,player);
        math=new CdPlayerMath(player.player());probes=CdPlayerTerrain.fromStageTerrain(terrain.terrain());
        solid=new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        primaryScanner=new CdPlayerObjectTouch(player.touchDimensions());
        primaryResponse=new CdPlayerTouchResponse(player.view(CdR12bPlayerAssets.Asset.ENEMY_POINTS));
        ring=CdRing.fromAct2PastAssets(placement,terrain);
        spring=CdSpring.fromAct2PastAssets(placement,terrain,math,solid,
                c->{requireKind(Kind.SPRING);claim(c,()->true);poolCut(c.callCut());commitSolid(c.callCut(),c.expectedPlayer(),c.expectedGlobals(),new CdSolidObject.Completed(c.result()));},
                c->{requireKind(Kind.SPRING);claim(c,()->true);requireIdentity(c.identity());var cut=read();
                    publish(cut,cut,null,()->frame.validateObjectSpringLaunch(c),()->frame.applyObjectSpringLaunch(c));},
                (p,id)->drawDynamic(Kind.SPRING,p,id,()->true),s->{requireKind(Kind.SPRING);sound(new SonicCdGameModule.PastSpringFm(s));});
        hud=CdReservedHud.fromAct2PastAssets(placement,terrain,new CdReservedHud.Slots(){
            public CdAssetBytes read(int whole){requireWhole(Kind.RESERVED,whole);return reserved(SonicCdAct2PastObjectPass.this.read(),whole);}
            public void write(int whole,CdAssetBytes expected,CdAssetBytes next){requireWhole(Kind.RESERVED,whole);var cut=SonicCdAct2PastObjectPass.this.read();
                if(!same(expected,reserved(cut,whole)))throw new IllegalArgumentException("Stale reserved full64");
                var after=withReserved(cut,replace(cut.reservedSlots(),whole,next));publish(cut,after,null,()->{},()->{});}
        },whole->{requireWhole(Kind.RESERVED,whole);drawReserved(whole);});
        boulder=CdBoulder.fromAct2PastAssets(placement,terrain,solid,c->{requireKind(Kind.BOULDER);
            claim(c,()->boulder.isCurrentPrefixSolidCall(c));poolCut(c.callCut());
            commitSolid(c.callCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());},
                (p,id)->drawDynamic(Kind.BOULDER,p,id,()->true));
        wall=CdBreakableWall.fromAct2PastAssets(placement,terrain,solid,c->{
            wallCall(c,c.cut());var d=dispatch;if(d.wallBranch!=2||d.wallSolids!=0||d.wallDraws!=0)throw new IllegalStateException("Wall Solid order");
            commitSolid(c.cut().placement().pool(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());d.wallSolids=1;wallAck(c);
        },c->{
            wallCall(c,c.cut());var d=dispatch;if(d.wallBranch!=3||d.wallFms!=1||d.wallHalves!=0||d.wallDraws!=0)throw new IllegalStateException("Wall ASR order");
            if(!c.expectedPlayer().equals(primary()))throw new IllegalArgumentException("Stale complete Wall player");
            var prepared=frame.prepareObjectBreakableWallXVelocity(frame.state(),frame.environment());
            if(prepared.finalState().motion().xVelocity()!=c.finalXVelocity())throw new IllegalArgumentException("Not actual Wall ASR value");
            var cut=read();publish(cut,cut,null,()->frame.validatePreparedObjectSolidPublication(prepared),()->frame.applyPreparedObjectSolidPublication(prepared));
            d.wallHalves=1;wallAck(c);
        },(p,id)->{
            requireKind(Kind.WALL);wall.validateCurrentDraw(p,id);claim(id,()->wall.matchesPendingOperation(id));var d=dispatch;
            if(d.wallDraws!=0||d.wallBranch==2&&d.wallSolids!=1||d.wallBranch==3&&(d.wallFms!=1||d.wallHalves!=1))throw new IllegalStateException("Wall Draw order");
            drawDynamic(Kind.WALL,p,id,()->wall.matchesPendingOperation(id));d.wallDraws=1;wallAck(id);
        },c->{
            wallCall(c,c.cut());var d=dispatch;if(d.wallBranch!=3||d.wallFms!=0||d.wallHalves!=0||d.wallDraws!=0)throw new IllegalStateException("Wall FM order");
            sound(new SonicCdGameModule.PastWallFm(c.nativeId()));d.wallFms=1;wallAck(c);
        },new CdBreakableWall.DispatchObserver(){
            public void published(Object call){requireKind(Kind.WALL);
                if(!wall.matchesPendingOperation(call)||dispatch.operation!=call||!dispatch.consumed.contains(call))throw new IllegalStateException("Wall omitted original publication receipt");
                dispatch.operation=null;
            }
            public void completed(CdBreakableWall.Result result){requireKind(Kind.WALL);wall.validateCurrentCall(result);var d=dispatch;
                if(d.completed||d.operation!=null||d.wallBranch==2&&(d.wallSolids!=1||d.wallDraws!=1)
                        ||d.wallBranch==3&&(d.wallFms!=1||d.wallHalves!=1)||!result.state().equals(placement.pool().state(d.whole)))
                    throw new IllegalStateException("Wall omitted actual completed source/operations");
                d.completed=true;d.operation=result;
            }
            public void failed(Throwable original){requireKind(Kind.WALL);wall.validateCurrentCall(original);}
        });
        monitor=CdMonitor.fromAct2PastAssets(placement,terrain,solid,probes,new CdMonitor.PrefixSolidOwner(){
            public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut c){requireKind(Kind.MONITOR);requireIdentity(c.objectIdentity());poolCut(c.callCut());
                if(!c.expectedPlayer().equals(primary())||!c.expectedGates().equals(gates())||!c.expectedGlobals().equals(solidGlobals()))throw new IllegalArgumentException("Stale Monitor readback");
                return liveMonitor();}
            public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall c){requireKind(Kind.MONITOR);claim(c,()->true);requireIdentity(c.cut().objectIdentity());poolCut(c.cut().callCut());
                commitSolid(c.cut().callCut(),c.cut().expectedPlayer(),c.cut().expectedGlobals(),c.outcome());return liveMonitor();}
        },(before,value)->{requireKind(Kind.MONITOR);angle(before,value);},
                (p,id)->drawDynamic(Kind.MONITOR,p,id,()->true),s->{requireKind(Kind.MONITOR);sound(new SonicCdGameModule.PastMonitorFm(s));});
        post=CdTimePost.fromAct2PastAssets(placement,terrain,c->{requireKind(Kind.TIME_POST);claim(c,()->true);requireIdentity(c.identity());
            var cut=read();publish(cut,cut,null,()->frame.validateObjectTimePostDirection(c.expectedRawDirection(),c.rawDirection()),
                    ()->frame.applyObjectTimePostDirection(c.expectedRawDirection(),c.rawDirection()));},
                s->{requireKind(Kind.TIME_POST);sound(new SonicCdGameModule.PastTimePostSubCpu(s));},
                (p,id)->drawDynamic(Kind.TIME_POST,p,id,()->true));
        collapse=CdCollapsePlatform.fromAct2PastAssets(placement,terrain,solid,c->{requireKind(Kind.COLLAPSE);claim(c,()->collapse.isCurrentSolidCall(c));
            poolCut(c.actualCut());commitSolid(c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());},
                (p,id)->drawDynamic(Kind.COLLAPSE,p,id,()->collapse.isCurrentDrawCall(p,id)),
                c->{requireKind(Kind.COLLAPSE);claim(c,()->collapse.isCurrentFmRequest(c));poolCut(c.actualCut());source(c.expectedSource());sound(new SonicCdGameModule.PastCollapsePlatformFm(c.soundId()));},
                c->{requireKind(Kind.COLLAPSE);if(!collapse.isCurrentOperation(c))throw new IllegalStateException("Foreign Collapse completion");guard();});
        platform=CdPlatform.fromAct2PastAssets(placement,terrain,math,probes,solid,c->{requireKind(Kind.PLATFORM);claim(c,()->platform.isCurrentSolidCall(c));
            poolCut(c.actualCut());commitSolid(c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());},
                c->{requireKind(Kind.PLATFORM);claim(c,()->platform.isCurrentPrimaryAngleCall(c));poolCut(c.actualCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
                (p,id)->drawDynamic(Kind.PLATFORM,p,id,()->true));
        anton=CdAnton.fromAct2PastAssets(placement,terrain,c->{requireKind(Kind.ANTON);claim(c,()->anton.isCurrentPrimaryAngleCall(c));
            poolCut(c.callCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
                (p,id)->drawDynamic(Kind.ANTON,p,id,()->true));
        tam=CdTamabboh.fromAct2PastAssets(placement,terrain,c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentPrimaryAngleCall(c));
            poolCut(c.callCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
                c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentPlayerReadCall(c));poolCut(c.callCut());source(c.expectedSource());
                    return c.field()==CdTamabboh.PlayerField.X_WORD?(frame.state().motion().centreX16_16()>>>16):(frame.state().motion().centreY16_16()>>>16);},
                c->{requireKind(Kind.TAM);claim(c,()->tam.isCurrentFmCall(c));poolCut(c.callCut());source(c.expectedSource());
                    if(placement.pool().findObjSlot().orElse(-1)!=c.firstFreeWholeIndex())throw new IllegalArgumentException("Stale Tam first-free receipt");
                    sound(new SonicCdGameModule.PastTamabbohFm(c.sound()));},
                (p,id)->drawDynamic(Kind.TAM,p,id,()->tam.isCurrentDrawCall(p,id)));
        animal=CdAnimal.fromAct2PastAssets(placement,terrain);
        swing=CdSwing.fromAct2PastAssets(placement,terrain,player,solid,new CdSwing.PlayerPort(){
            public CdSwing.PlayerCut read(CdSwing.PlayerReadCall c){requireKind(Kind.SWING);claim(c,()->swing.isCurrentCall(c));placementCut(c.actualPoolCut());source(c.expectedSource());
                var raw=reserved(SonicCdAct2PastObjectPass.this.read(),1);var selected=c.slot()==CdSolidObject.PlayerSlot.PRIMARY?primary():secondary(raw);
                var answer=new CdSwing.PlayerCut(selected,gates(),solidGlobals(),new CdSolidObject.PlayerSelection(c.slot(),
                        c.slot()==CdSolidObject.PlayerSlot.PRIMARY?raw.unsignedByte(0x22):frame.state().motion().flags()));dispatch.playerCut=answer;return answer;}
            public void commitSolid(CdSwing.SolidCall c){commitSwing(c);}
            public void commitY(CdSwing.PlayerYCall c){swingY(c);}
        },c->{requireKind(Kind.SWING);claim(c,()->swing.isCurrentCall(c));placementCut(c.actualPoolCut());source(c.expectedSource());
            drawDynamic(Kind.SWING,placement.pool(),c.expectedSource().identity(),()->swing.isCurrentCall(c));},
                new CdSwing.DispatchObserver(){
                    public void beforePublication(CdSwing.Publication c){observeBefore(c,c.ordinal(),c.actualPoolCut(),c.kind().name(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value());}
                    public void afterPublication(CdSwing.Publication c){observeAfter(c,true);}
                    public void beforeCall(Object c){observeBeforeCall(c,swing.currentCallOrdinal(c));}
                    public void afterCall(Object c){observeAfter(c,false);}
                },new CdSwing.ReceiptGuard(){public void prepared(Object c){receiptPrepared(c);}public void completed(Object c){receiptCompleted(c);}});
        robot=CdRobotGenerator.fromAct2PastAssets(placement,terrain,solid,c->{requireKind(Kind.ROBOT);claim(c,()->robot.isCurrentCall(c));
            placementCut(c.actualCut());source(c.expectedSource());commitSolid(c.actualCut().pool(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());},
                c->{requireKind(Kind.ROBOT);claim(c,()->robot.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());var cut=read();
                    if(cut.goodFutureByte()!=c.expectedGoodFuture())throw new IllegalArgumentException("Stale goodFuture");
                    publish(cut,withGoodFuture(cut,c.value()),null,()->{},()->{});},
                c->{requireKind(Kind.ROBOT);claim(c,()->robot.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());points(c.points());},
                c->{requireKind(Kind.ROBOT);claim(c,()->robot.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());sound(new SonicCdGameModule.PastRobotGeneratorFm(c.nativeId()));},
                c->{requireKind(Kind.ROBOT);claim(c,()->robot.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());
                    drawDynamic(Kind.ROBOT,placement.pool(),c.expectedSource().identity(),()->robot.isCurrentCall(c));},
                new CdRobotGenerator.DispatchObserver(){
                    public void beforePublication(CdRobotGenerator.Publication c){observeBefore(c,c.ordinal(),c.actualCut(),c.kind().name(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value());}
                    public void afterPublication(CdRobotGenerator.Publication c){observeAfter(c,true);}
                    public void beforeCall(Object c){observeBeforeCall(c,robot.currentCallOrdinal(c));}
                    public void afterCall(Object c){observeAfter(c,false);}
                },new CdRobotGenerator.ReceiptGuard(){public void prepared(Object c){receiptPrepared(c);}public void completed(Object c){receiptCompleted(c);}});
    }
    boolean isCurrentAdoption(Adoption a){return a!=null&&pending==a&&port&&(phase==Phase.WALKING||phase==Phase.DRAINING);}
    /** Caller has already admitted selector8, incremented levelFrames once and run actual SpawnObjects. */
    void runWalk(int actualLevelFrame){
        if(phase!=Phase.READY&&phase!=Phase.DONE||port||queuePort)throw new IllegalStateException("R12B pass is not idle");
        word(actualLevelFrame);guard();if(frame.paused())throw new IllegalStateException("Paused loop has no unpaused physical walk");
        if(frame.environment().globals().levelFrames()!=actualLevelFrame)throw new IllegalArgumentException("Not actual levelFrames");
        Cut cut=read();queue=new CdObjectDrawQueue(new DrawSlots(),mappings,cut.cpuSat().copy(),
                List.of(new CdObjectDrawQueue.CdSlotRef(1),new CdObjectDrawQueue.CdSlotRef(2),new CdObjectDrawQueue.CdSlotRef(3),new CdObjectDrawQueue.CdSlotRef(5),new CdObjectDrawQueue.CdSlotRef(31)));
        phase=Phase.WALKING;
        try {
            queue.beginPass();
            for(int whole=0;whole<128;whole++){
                guard();int id=id(whole);if(id==0)continue;
                int pointer=handler(id,whole);Kind kind=kind(id,whole,pointer);dispatch=new Dispatch(kind,whole);
                try{if(kind!=Kind.PRIMARY)checkpoint();execute(kind,whole,actualLevelFrame);}finally{dispatch=null;}
            }
            phase=Phase.WALKED;
        }catch(RuntimeException|Error failure){phase=Phase.FAILED;queue.close();throw failure;}
    }
    CdObjectDrawQueue.CpuSat finishDraw(CdObjectDrawQueue.DrawCameras cameras){
        if(phase!=Phase.WALKED||port||queuePort)throw new IllegalStateException("No complete R12B walk to drain");
        Objects.requireNonNull(cameras);guard();var live=read();
        if(cameras.foreground().xWord()!=live.cameraXWord()||cameras.foreground().yWord()!=live.cameraYWord())throw new IllegalArgumentException("Not live post-scroll foreground cameras");phase=Phase.DRAINING;
        try{var cpu=queue.finishAfterCamera(cameras);var cut=read();
            publish(cut,copy(cut,cut.globals(),cut.goodFutureByte(),cut.reservedSlots(),CdAssetBytes.copyOf(cpu.bytes()),Optional.of(cpu),cut.sounds()),null,()->{},()->{});
            queue.close();queue=null;phase=Phase.DONE;return cpu;
        }catch(RuntimeException|Error failure){phase=Phase.FAILED;if(queue!=null)queue.close();throw failure;}
    }
    void discard(){if(port||queuePort||phase==Phase.WALKING||phase==Phase.DRAINING)throw new IllegalStateException("Active R12B pass");if(queue!=null)queue.close();queue=null;phase=Phase.DISCARDED;}
    private void execute(Kind kind,int whole,int actualLevelFrame){
        var cut=read();var e=frame.environment();var g=e.movement().gates();
        switch(kind){
            case PRIMARY->{if(frame.state().motion().routine()==2){runPrimaryMain(actualLevelFrame);break;}
                if(frame.state().motion().routine()!=0)throw stop(Boundary.SONIC_MAIN,whole,frame.state().motion().routine());
                var publication=frame.runBody(actualLevelFrame);checkpoint();if(publication.result().boundary()!=CdPlayerBody.Boundary.INIT_RETURN||frame.display().isPresent())throw new IllegalStateException("Primary Init did not return its native no-draw boundary");
                if(!publication.sounds().isEmpty()){var after=read();var sounds=new ArrayList<>(after.sounds());for(var sound:publication.sounds())sounds.add(new SonicCdGameModule.PastFrameSound(sound));
                    publish(after,withSounds(after,sounds),null,()->{},()->{});}}
            case RESERVED->hud.run(whole,new CdReservedHud.Globals(e.globals().debugCheat(),cut.globals().ring().rings(),vIntLow(),
                    cut.timeAttackModeByte(),g.timeWarpDirection(),e.globals().timeWarpTimer(),actualLevelFrame));
            case NULL->{if(whole>=32)placement.pool().objNull(whole);else if(whole==0)throw stop(Boundary.PLAYER_SLOT,whole,id(whole));
                else{var raw=reserved(cut,whole).copy();raw[0]=0;publish(cut,withReserved(cut,replace(cut.reservedSlots(),whole,CdAssetBytes.copyOf(raw))),null,()->{},()->{});}}
            case RING->ring.executeSelected(whole,new CdRing.Inputs(frame.stageSource().variant(),frame.stageSource().stage(),g.zoneAct()>>>8,cut.cameraXWord(),g.timeZone(),g.timeWarpDirection()),
                    new CdRing.GlobalsPort(){public CdRingGlobals read(){requireKind(Kind.RING);return SonicCdAct2PastObjectPass.this.read().globals().ring();}
                        public void write(CdRingGlobals next){requireKind(Kind.RING);var old=read();var c=SonicCdAct2PastObjectPass.this.read();if(!old.equals(c.globals().ring()))throw new IllegalStateException("Stale Ring reward");
                            publish(c,withRing(c,next),null,()->{},()->{});}},
                    new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound s){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingFm(s));}
                        public void subCpuCommand(CdRing.SubCpuCommand s){requireKind(Kind.RING);sound(new SonicCdGameModule.PastRingSubCpu(s));}
                        public void drawObject(CdObjectPool p,int w){requireWhole(Kind.RING,w);drawDynamic(Kind.RING,p,p.state(w).identity(),()->true);}});
            case SPRING->spring.run(whole,new CdSpring.Globals(cut.cameraXWord()),new CdSpring.SolidInputs(primary(),gates(),solidGlobals()));
            case BOULDER->boulder.run(whole,new CdBoulder.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),
                    new CdBoulder.SolidInputs(primary(),gates(),solidGlobals()));
            case WALL->{var entry=placement.pool().state(whole);dispatch.wallBranch=entry.routine()==4?4:entry.unsignedByte(COLLISION_STATUS)==0?2:3;
                var result=wall.run(whole,new CdBreakableWall.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdBreakableWall.Inputs(primary(),gates(),solidGlobals()));
                requireKind(Kind.WALL);if(!dispatch.completed||dispatch.operation!=result)throw new IllegalStateException("Not actual completed Wall dispatch");}
            case MONITOR->monitor.runPast(whole,monitorGlobals(),new CdMonitor.SolidInputs(primary(),gates(),solidGlobals()));
            case TIME_POST->post.runPast(whole,postGlobals());
            case COLLAPSE->collapse.run(whole,new CdCollapsePlatform.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdCollapsePlatform.Inputs(primary(),gates(),solidGlobals()));
            case PLATFORM->platform.run(whole,new CdPlatform.Globals(g.zoneAct(),cut.globals().ring().timeStopTimer(),cut.cameraXWord(),cut.cameraYWord(),g.timeZone()),new CdPlatform.Inputs(primary(),gates(),solidGlobals()));
            case ANTON->anton.run(whole,new CdAnton.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),cut.cameraXWord(),solidGlobals().primaryAngle()));
            case TAM->tam.run(whole,new CdTamabboh.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),e.globals().debugMode(),cut.cameraXWord(),solidGlobals().primaryAngle()));
            case ANIMAL->animal.runWithReads(whole,new CdAnimal.Reads(){public int timeZone(){return reached().movement().gates().timeZone();}public int goodFuture(){requireKind(Kind.ANIMAL);return read().goodFutureByte();}
                public int projDestroyed(){requireKind(Kind.ANIMAL);return read().globals().projectorByte();}public int timeWarpDir(){return reached().movement().gates().timeWarpDirection();}
                public int cameraXWord(){requireKind(Kind.ANIMAL);return read().cameraXWord();}private CdPlayerBody.Environment reached(){requireKind(Kind.ANIMAL);return frame.environment();}});
            case SWING->swing.run(whole,new CdSwing.Globals(cut.cameraXWord(),g.timeZone(),g.timeWarpDirection()));
            case ROBOT->robot.run(whole,new CdRobotGenerator.Globals(cut.goodFutureByte(),g.timeZone(),g.zoneAct()&255,g.timeWarpDirection(),cut.cameraXWord()),new CdRobotGenerator.Inputs(primary(),gates(),solidGlobals()));
        }
    }
    /** Genuine routine2 call over the same post-Spawn pool; Frame owns actor adoption once. */
    private void runPrimaryMain(int actualLevelFrame){
        guard();var before=read();var placed=placement.capture();var pool=placed.pool();
        var state=frame.state();var environment=frame.environment();var display=frame.display();
        var art=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();
        var r=before.globals().ring();var score=before.globals().score();
        var response=new CdPlayerBody.ResponseContext(primaryResponse,before.globals().contact(),
                new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()));
        var support=new CdPlayerMovement.ObjectContext(pool,state.objectLinks().standObject(),state.header().slotId(),state.header().width());
        // Absent reserved/save contexts are explicit unowned reached branches. Zero direction/charge
        // return before those reads, using the genuine shared helper; no default timer or result is supplied.
        var warp=new CdPlayerTimeWarp.Context(OptionalInt.of(before.level().spawnMode()),Optional.empty(),Optional.empty(),OptionalInt.of(before.level().levelRestart()));
        var publication=frame.runBody(actualLevelFrame,Optional.of(new CdPlayerBody.TouchContext(primaryScanner,pool,response)),
                Optional.of(new CdPlayerBody.LevelContext(pool,r,before.level())),Optional.of(support),
                Optional.of(new CdPlayerBody.TimeWarpContext(warp,before.globals().contact())));
        guard();requireCut(before);requirePlacement(placed);
        if(frame.state()!=publication.result().state()||frame.environment()!=publication.result().environment()
                ||frame.display()!=publication.result().display()||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused)
            throw new IllegalStateException("Primary Frame left its original completed result");
        var plan=SonicCdAct2PastPrimaryPublication.prepare(publication,pool,before.globals(),before.level(),before.reservedSlots());
        var sounds=new ArrayList<>(before.sounds());sounds.addAll(plan.sounds());
        var next=new Cut(plan.globals(),plan.level(),before.goodFutureByte(),before.goodFutureFlags(),plan.reserved(),before.cpuSat(),before.completedCpu(),sounds,
                before.cameraXWord(),before.cameraYWord(),before.timeAttackModeByte(),before.layerByte());
        checkpoint();publish(before,next,plan.pool(),()->{},()->{});
        var selected=plan.display();
        if(selected.eligible()){
            if(dispatch.drawn)throw new IllegalStateException("Primary DrawObject repeated");dispatch.drawn=true;
            draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),selected.spriteFlags(),selected.centreXWord(),selected.centreYWord(),
                    selected.yRadius(),selected.width(),selected.priority()));
        }
    }
    private int id(int whole){return whole==0?frame.state().header().slotId():whole<32?reserved(read(),whole).unsignedByte(0):placement.pool().state(whole).id();}
    private int handler(int id,int whole){int at=(id-1)*4;if(at<0||at>objectIndex.length()-4)throw stop(Boundary.OBJECT_INDEX,whole,id);return(int)objectIndex.unsignedLong(at);}
    private Kind kind(int id,int whole,int pointer){
        if(pointer==0x203D4A)return Kind.NULL; // Actual authenticated ObjNull aliases, ID-only MOVE.B #0.
        if(whole==0){if(id==1||id==2)return Kind.PRIMARY;throw stop(Boundary.PLAYER_SLOT,whole,id);}
        if(whole<32){if((whole==2||whole==3||whole==5)&&id==0x1C||whole==31&&id==0x19)return Kind.RESERVED;
            throw stop(Boundary.PLAYER_SLOT,whole,id);}
        return switch(id){case 0x0A->Kind.SPRING;case 0x10->Kind.RING;case 0x1B->Kind.BOULDER;case 0x36->Kind.WALL;case 0x19->placement.pool().state(whole).subtype()<8?Kind.MONITOR:Kind.TIME_POST;
            case 0x20->Kind.COLLAPSE;case 0x21->Kind.PLATFORM;case 0x22->Kind.TAM;case 0x24->Kind.ANIMAL;case 0x29->Kind.SWING;case 0x2D->Kind.ROBOT;case 0x3F->Kind.ANTON;
            default->throw stop(Boundary.OBJECT_KIND,whole,id);};
    }
    private void guard(){
        if(frame.closed()||!frame.stageSource().ownsAct2Past(terrain,player)||placement.placements()!=terrain.placements())throw new IllegalStateException("Closed/foreign R12B pass binding");
        ports.validate(this,frame,terrain,player,placement);
    }
    private Cut read(){guard();return Objects.requireNonNull(ports.read(),"actual R12B cut");}
    private void requireCut(Cut expected){var actual=read();
        if(!expected.globals().equals(actual.globals())||!expected.level().equals(actual.level())
                ||expected.goodFutureByte()!=actual.goodFutureByte()||!expected.goodFutureFlags().equals(actual.goodFutureFlags())
                ||!same(expected.reservedSlots(),actual.reservedSlots())||!same(expected.cpuSat(),actual.cpuSat())
                ||!expected.completedCpu().equals(actual.completedCpu())||!expected.sounds().equals(actual.sounds())
                ||expected.cameraXWord()!=actual.cameraXWord()||expected.cameraYWord()!=actual.cameraYWord()
                ||expected.timeAttackModeByte()!=actual.timeAttackModeByte()||!expected.layerByte().equals(actual.layerByte()))
            throw new IllegalArgumentException("Stale complete R12B scalar/reserved/CPU/sound cut");}
    private void requireFrame(CdPlayerFrameRuntime.ActorState a,CdPlayerFrameRuntime.SharedState s){if(!a.equals(frame.captureActor())||!s.equals(frame.captureShared()))throw new IllegalArgumentException("Stale complete R12B Frame cut");}
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot cut){placement.validateRestore(cut);var live=placement.capture();
        if(!cut.status().equals(live.status())||!cut.pool().slots().equals(live.pool().slots())||!same(cut.savedFlags(),live.savedFlags()))throw new IllegalArgumentException("Stale complete R12B placement/bank");}
    private void placementCut(CdObjectPlacementRuntime.Snapshot cut){requirePlacement(cut);}
    private void poolCut(CdObjectPool.Snapshot cut){placement.pool().validateRestore(cut);if(!cut.slots().equals(placement.pool().capture().slots()))throw new IllegalArgumentException("Stale full96 callback cut");}
    private void source(CdObjectState s){requireIdentity(s.identity());if(!placement.pool().state(dispatch.whole).equals(s))throw new IllegalArgumentException("Stale source full64");}
    private void requireIdentity(CdObjectState.Identity id){if(dispatch==null||id.wholeIndex()!=dispatch.whole||dispatch.whole<32)throw new IllegalStateException("Foreign physical callback identity");}
    private void requireKind(Kind kind){if(phase!=Phase.WALKING||dispatch==null||dispatch.kind!=kind||port||queuePort)throw new IllegalStateException("No private current "+kind+" dispatch");guard();if(dispatch.ownerActor!=null){requireFrame(dispatch.ownerActor,dispatch.ownerShared);requireCut(dispatch.ownerCut);}}
    private void requireWhole(Kind kind,int whole){requireKind(kind);if(dispatch.whole!=whole)throw new IllegalStateException("Wrong callback whole index");}
    private void claim(Object call,BooleanSupplier actual){if(dispatch==null||call==null||!actual.getAsBoolean()||!dispatch.consumed.add(call))throw new IllegalStateException("Cloned/repeated/foreign native callback");
        if(dispatch.kind==Kind.SWING||dispatch.kind==Kind.ROBOT){if(dispatch.operation!=call||!dispatch.prepared||dispatch.used)throw new IllegalStateException("Missing original prepared receipt");dispatch.used=true;}}
    private void wallCall(Object call,CdBreakableWall.SourceCut cut){requireKind(Kind.WALL);wall.validateCurrentCall(call);
        claim(call,()->wall.matchesPendingOperation(call));placementCut(cut.placement());source(cut.source());}
    private void wallAck(Object call){dispatch.operation=call;wall.acknowledgeCurrentCall(call);}
    private void publish(Cut expected,Cut next,CdObjectPool.Snapshot pool,Runnable preflight,Runnable write){
        if(port||pending!=null)throw new IllegalStateException("Recursive R12B adoption");var a=new Adoption(expected,next,pool,preflight,write);pending=a;port=true;
        try{requireCut(expected);ports.adopt(a);if(!a.applied)throw new IllegalStateException("Root omitted actual adoption receipt");requireCut(next);requireFrame(a.afterActor,a.afterShared);requirePlacement(a.afterPlacement);if(dispatch!=null)checkpoint();}
        finally{pending=null;port=false;}
    }
    private void checkpoint(){dispatch.ownerActor=frame.captureActor();dispatch.ownerShared=frame.captureShared();dispatch.ownerCut=read();}
    private void sound(SonicCdGameModule.PastSound sound){var cut=read();var sounds=new ArrayList<>(cut.sounds());sounds.add(Objects.requireNonNull(sound));publish(cut,withSounds(cut,sounds),null,()->{},()->{});}
    private void angle(int expected,int value){if(solidGlobals().primaryAngle()!=expected)throw new IllegalArgumentException("Stale primary angle");var cut=read();
        publish(cut,cut,null,()->{if(solidGlobals().primaryAngle()!=expected)throw new IllegalArgumentException("Stale primary angle");},()->frame.applyObjectPrimaryAngle(value));}
    private void commitSolid(CdObjectPool.Snapshot pool,CdSolidObject.Player expected,CdSolidObject.Globals globals,CdSolidObject.Outcome outcome){
        poolCut(pool);CdObjectState image;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication prepared;
        if(outcome instanceof CdSolidObject.Completed c){var r=c.completedResult();image=r.object();others=r.otherSlots();fm=r.fmRequests();prepared=frame.prepareObjectSolidResult(expected,globals,r);}
        else{var p=((CdSolidObject.NativeStopped)outcome).prefix();image=p.object();others=p.otherSlots();fm=p.fmRequests();prepared=frame.prepareObjectSolidPrefix(expected,globals,p);}
        requireIdentity(image.identity());var images=new ArrayList<CdObjectState>();images.add(image);images.addAll(others);var output=CdObjectPool.overlay(pool,images);
        var cut=read();var sounds=new ArrayList<>(cut.sounds());for(var s:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(s)));
        publish(cut,withSounds(cut,sounds),output,()->frame.validatePreparedObjectSolidPublication(prepared),()->frame.applyPreparedObjectSolidPublication(prepared));
    }
    private CdSolidObject.Player primary(){var s=frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    private CdSolidObject.Gates gates(){var e=frame.environment();return new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic());}
    private CdSolidObject.Globals solidGlobals(){var e=frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    private CdMonitor.LiveSolid liveMonitor(){return new CdMonitor.LiveSolid(primary(),gates(),solidGlobals());}
    private int vIntLow(){var raw=frame.vIntClock().absoluteValue();if(raw.isEmpty())throw stop(Boundary.UNKNOWN_VINT,dispatch.whole,0);return(int)raw.getAsLong()&255;}
    private CdMonitor.PastGlobals monitorGlobals(){return new CdMonitor.PastGlobals(){
        public int rawTimeZone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeZone();}public int rawTimeWarpDirection(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().timeWarpDirection();}
        public int cameraXWord(){requireKind(Kind.MONITOR);return read().cameraXWord();}public int timeAttackMode(){requireKind(Kind.MONITOR);return read().timeAttackModeByte();}
        public int zone(){requireKind(Kind.MONITOR);return frame.environment().movement().gates().zoneAct()>>>8;}public int layer(){requireKind(Kind.MONITOR);return read().layerByte().orElseThrow(()->stop(Boundary.UNKNOWN_LAYER,dispatch.whole,0));}
        public int timeStopTimer(){requireKind(Kind.MONITOR);return read().globals().ring().timeStopTimer();}};}
    private CdTimePost.PastGlobals postGlobals(){return new CdTimePost.PastGlobals(){
        public int rawTimeZone(){requireKind(Kind.TIME_POST);return frame.environment().movement().gates().timeZone();}public int rawTimeWarpDirection(){requireKind(Kind.TIME_POST);return frame.environment().movement().gates().timeWarpDirection();}
        public int cameraXWord(){requireKind(Kind.TIME_POST);return read().cameraXWord();}public int timeAttackMode(){requireKind(Kind.TIME_POST);return read().timeAttackModeByte();}
        public int zone(){requireKind(Kind.TIME_POST);return frame.environment().movement().gates().zoneAct()>>>8;}public OptionalInt layer(){requireKind(Kind.TIME_POST);return read().layerByte();}};}
    private void points(int value){var cut=read();var r=cut.globals().ring();var score=cut.globals().score();
        var result=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()),value);var w=result.world();
        var nextRing=new CdRingGlobals(r.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());
        var next=withGlobals(cut,globals(cut.globals(),nextRing,new CdOpeningObjectRuntime.ScoreGlobals(w.score(),w.nextLifeScore(),w.updateHudScore())));
        if(result.command().isPresent()){var sounds=new ArrayList<>(next.sounds());sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(result.command().orElseThrow())));next=withSounds(next,sounds);}
        publish(cut,next,null,()->{},()->{});
    }
    private void drawDynamic(Kind kind,CdObjectPool pool,CdObjectState.Identity identity,BooleanSupplier actual){
        requireKind(kind);requireIdentity(identity);if(pool!=placement.pool()||!actual.getAsBoolean()||dispatch.drawn)throw new IllegalStateException("Foreign/repeated actual DrawObject");
        var state=pool.state(identity.wholeIndex());dispatch.drawn=true;draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(identity.wholeIndex()),
                state.spriteFlags(),state.centreX(),state.centreY(),state.unsignedByte(Y_RADIUS),state.unsignedByte(WIDTH),state.unsignedByte(PRIORITY)));
    }
    private void drawReserved(int whole){requireWhole(Kind.RESERVED,whole);if(dispatch.drawn)throw new IllegalStateException("Repeated reserved DrawObject");
        var raw=reserved(read(),whole);dispatch.drawn=true;draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),raw.unsignedByte(1),raw.unsignedWord(8),raw.unsignedWord(12),raw.unsignedByte(22),raw.unsignedByte(25),raw.unsignedByte(24)));
    }
    private void draw(CdObjectDrawQueue.DrawCallCut call){if(queuePort||port)throw new IllegalStateException("Recursive queue callback");var cut=read();queuePort=true;
        try{queue.drawObject(call,new CdObjectDrawQueue.CameraWords(cut.cameraXWord(),cut.cameraYWord()));}finally{queuePort=false;}}
    private final class DrawSlots implements CdObjectDrawQueue.CdDrawSlots {
        private void current(){if(!queuePort&&phase!=Phase.DRAINING||queue==null||port)throw new IllegalStateException("No private live queue dereference");guard();}
        public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){current();int whole=ref.wholeIndex();
            if(whole==0){var s=frame.state();var m=s.motion();var h=s.header();return new CdObjectDrawQueue.LiveDrawSlot(h.slotId(),m.spriteFlags(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.centreX16_16()&65535,h.mappingAddress(),s.animation().mapFrame(),h.tile());}
            if(whole<32){var r=reserved(SonicCdAct2PastObjectPass.this.read(),whole);return new CdObjectDrawQueue.LiveDrawSlot(r.unsignedByte(0),r.unsignedByte(1),r.unsignedWord(8),r.unsignedWord(12),r.unsignedWord(10),(int)r.unsignedLong(4),r.unsignedByte(26),r.unsignedWord(2));}
            var s=placement.pool().state(whole);return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));
        }
        public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){current();int whole=ref.wholeIndex();int bit=value?128:0;var cut=SonicCdAct2PastObjectPass.this.read();
            if(whole==0){publish(cut,cut,null,()->{},()->frame.publishDrawObjectOnScreen(value));return;}
            if(whole<32){var raw=reserved(cut,whole).copy();raw[1]=(byte)((raw[1]&127)|bit);publish(cut,withReserved(cut,replace(cut.reservedSlots(),whole,CdAssetBytes.copyOf(raw))),null,()->{},()->{});return;}
            var pool=placement.pool();var before=pool.capture();var image=pool.state(whole);var after=image.moveByte(SPRITE_FLAGS,(image.spriteFlags()&127)|bit);
            publish(cut,cut,CdObjectPool.overlay(before,List.of(after)),()->{},()->{});
        }
    }
    private boolean actualObserved(Object call){return dispatch.kind==Kind.SWING?swing.isCurrentCall(call):dispatch.kind==Kind.ROBOT&&robot.isCurrentCall(call);}
    private void observeBeforeCall(Object call,long ordinal){
        observedStart(call,ordinal);dispatch.expectedImages=null;dispatch.expectedSaved=null;
    }
    private void observeBefore(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,String kind,CdObjectState source,CdObjectState before,CdObjectState output,int savedIndex,int previous,int value){
        observedStart(call,ordinal);placementCut(cut);source(source);var d=dispatch;var images=new ArrayList<byte[]>();for(var s:cut.pool().slots())images.add(s.copyBytes());byte[] saved=cut.savedFlags().copy();
        switch(kind){
            case "SOURCE_STORE","CHILD_STORE"->{if(before==null||output==null||!before.identity().equals(output.identity()))throw new IllegalArgumentException("Bad native source/child store");
                if(!cut.pool().slots().get(before.identity().dynamicIndex()).equals(before))throw new IllegalArgumentException("Stale native store preimage");
                // overlay validates the actual pool owner even when all public identity/value fields match.
                CdObjectPool.overlay(cut.pool(),List.of(output));images.set(output.identity().dynamicIndex(),output.copyBytes());}
            case "DELETE"->images.set(source.identity().dynamicIndex(),new byte[64]);
            case "SAVED_FLAG_CLEAR"->{if(savedIndex<0||savedIndex>=saved.length||Byte.toUnsignedInt(saved[savedIndex])!=previous||value!=(previous&127))throw new IllegalArgumentException("Stale saved BCLR");saved[savedIndex]=(byte)value;}
            default->throw new IllegalArgumentException("Unknown native publication "+kind);
        }
        d.expectedImages=List.copyOf(images);d.expectedSaved=saved;
    }
    private void observedStart(Object call,long ordinal){
        if(dispatch==null||dispatch.kind!=Kind.SWING&&dispatch.kind!=Kind.ROBOT||port||queuePort||dispatch.operation!=null||!actualObserved(call)||ordinal!=dispatch.ordinal+1)throw new IllegalStateException("No original next native operation");
        guard();var d=dispatch;d.operation=call;d.prepared=false;d.completed=false;d.used=false;d.observedCut=placement.capture();d.observedGlobals=read();d.actor=frame.captureActor();d.shared=frame.captureShared();
    }
    private void receiptPrepared(Object call){var d=dispatch;if(d==null||d.operation!=call||d.prepared||!actualObserved(call))throw new IllegalStateException("Missing original preparation receipt");
        requireFrame(d.actor,d.shared);requireCut(d.observedGlobals);requirePlacement(d.observedCut);d.prepared=true;}
    private void observeAfter(Object call,boolean publication){var d=dispatch;if(d==null||d.operation!=call||!d.prepared||d.completed||!actualObserved(call))throw new IllegalStateException("Missing native operation completion");
        guard();if(publication){requireFrame(d.actor,d.shared);requireCut(d.observedGlobals);var live=placement.capture();
            if(!d.observedCut.status().equals(live.status())||!Arrays.equals(d.expectedSaved,live.savedFlags().copy()))throw new IllegalStateException("Native store changed saved/status nonwrites");
            for(int n=0;n<96;n++)if(!Arrays.equals(d.expectedImages.get(n),live.pool().slots().get(n).copyBytes()))throw new IllegalStateException("Native publication full96 mismatch");
        }else if(!d.used)throw new IllegalStateException("Callback omitted private consumption receipt");
        d.completed=true;
    }
    private void receiptCompleted(Object call){var d=dispatch;if(d==null||d.operation!=call||!d.completed||!actualObserved(call))throw new IllegalStateException("Missing completed original receipt");
        d.ordinal++;d.operation=null;d.observedCut=null;d.observedGlobals=null;d.actor=null;d.shared=null;d.expectedImages=null;d.expectedSaved=null;
    }
    private void requirePlayerCut(CdSwing.PlayerCut c){
        var d=dispatch;var raw=reserved(read(),1);boolean primary=c.selection().selected()==CdSolidObject.PlayerSlot.PRIMARY;
        if(c!=d.playerCut||!c.player().equals(primary?primary():secondary(raw))||!c.gates().equals(gates())||!c.globals().equals(solidGlobals())
                ||c.selection().counterpartFlags()!=(primary?raw.unsignedByte(0x22):frame.state().motion().flags()))throw new IllegalArgumentException("Not original fresh selected-player read");
    }
    private void commitSwing(CdSwing.SolidCall c){
        requireKind(Kind.SWING);claim(c,()->swing.isCurrentCall(c));placementCut(c.actualPoolCut());source(c.expectedSource());requirePlayerCut(c.expectedPlayerCut());
        var selected=c.expectedPlayerCut();var actual=c.genuineSelectedOutcome();CdObjectState image;List<CdObjectState> others;CdSolidObject.Player playerAfter;Set<CdSolidObject.PlayerField> writes;List<CdPlayerTouchResponse.FmSound> fm;
        if(actual.outcome() instanceof CdSolidObject.Completed done){var r=done.completedResult();image=r.object();others=r.otherSlots();playerAfter=r.player();writes=r.playerWrites();fm=r.fmRequests();}
        else{var p=((CdSolidObject.NativeStopped)actual.outcome()).prefix();image=p.object();others=p.otherSlots();playerAfter=p.player();writes=p.playerWrites();fm=p.fmRequests();}
        requireIdentity(image.identity());var images=new ArrayList<CdObjectState>();images.add(image);images.addAll(others);var pool=CdObjectPool.overlay(c.actualPoolCut().pool(),images);
        var prepared=frame.prepareObjectSelectedSolid(frame.state(),frame.environment(),selected.player(),selected.selection(),actual);
        var cut=read();var raw=reserved(cut,1);CdAssetBytes nextRaw;
        if(selected.selection().selected()==CdSolidObject.PlayerSlot.SECONDARY)nextRaw=secondaryImage(raw,selected.player(),playerAfter,writes);
        else{byte[] bytes=raw.copy();if(actual.counterpart().stored())bytes[0x22]=(byte)actual.counterpart().value();nextRaw=CdAssetBytes.copyOf(bytes);}
        var next=withReserved(cut,replace(cut.reservedSlots(),1,nextRaw));var sounds=new ArrayList<>(cut.sounds());for(var s:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(s)));next=withSounds(next,sounds);
        publish(cut,next,pool,()->frame.validatePreparedObjectSolidPublication(prepared),()->frame.applyPreparedObjectSolidPublication(prepared));dispatch.playerCut=null;
    }
    private void swingY(CdSwing.PlayerYCall c){
        requireKind(Kind.SWING);claim(c,()->swing.isCurrentCall(c));placementCut(c.actualPoolCut());source(c.expectedSource());requirePlayerCut(c.expectedPlayerCut());var cut=read();
        if(c.expectedPlayerCut().selection().selected()==CdSolidObject.PlayerSlot.PRIMARY){var p=frame.prepareObjectSwingYWord(frame.state(),frame.environment(),c.expectedYWord(),c.value());
            publish(cut,cut,null,()->frame.validatePreparedObjectSolidPublication(p),()->frame.applyPreparedObjectSolidPublication(p));}
        else{var raw=reserved(cut,1).copy();if(((raw[12]&255)<<8|(raw[13]&255))!=c.expectedYWord())throw new IllegalArgumentException("Stale secondary Y WORD");putWord(raw,12,c.value());
            publish(cut,withReserved(cut,replace(cut.reservedSlots(),1,CdAssetBytes.copyOf(raw))),null,()->{},()->{});}
        dispatch.playerCut=null;
    }
    private static CdSolidObject.Player secondary(CdAssetBytes raw){extent(raw,64);
        var m=new CdPlayerMotionState((int)raw.unsignedLong(8),(int)raw.unsignedLong(12),(short)raw.unsignedWord(16),(short)raw.unsignedWord(18),(short)raw.unsignedWord(20),
                raw.unsignedByte(36),raw.unsignedByte(38),raw.unsignedByte(34),raw.unsignedByte(1),raw.unsignedByte(23),raw.unsignedByte(22),raw.unsignedByte(28),raw.unsignedByte(29),
                raw.unsignedByte(60),raw.unsignedByte(42),(short)raw.unsignedWord(62),raw.unsignedByte(56),raw.unsignedByte(44),raw.unsignedByte(54),raw.unsignedByte(55));
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(m,raw.unsignedWord(2),raw.unsignedByte(24),raw.unsignedWord(48),raw.unsignedByte(57)),raw.unsignedByte(0),raw.unsignedByte(32),raw.unsignedByte(61));
    }
    private static CdAssetBytes secondaryImage(CdAssetBytes raw,CdSolidObject.Player before,CdSolidObject.Player after,Set<CdSolidObject.PlayerField> writes){
        if(!secondary(raw).equals(before))throw new IllegalArgumentException("Stale actual secondary full64");var bytes=raw.copy();var a=after.actor();var m=a.motion();
        for(var field:writes){int value=switch(field){case TILE->a.tile();case CENTRE_X->m.centreX16_16()>>>16;case CENTRE_X_FRACTION->m.centreX16_16()&65535;
            case CENTRE_Y->m.centreY16_16()>>>16;case CENTRE_Y_FRACTION->m.centreY16_16()&65535;case X_VELOCITY->m.xVelocity();case Y_VELOCITY->m.yVelocity();case GROUND_VELOCITY->m.groundVelocity();
            case Y_RADIUS->m.yRadius();case X_RADIUS->m.xRadius();case PRIORITY->a.priority();case ANIMATION->m.animation();case PUSH_OBJECT->after.pushObject();case FLAGS->m.flags();
            case ROUTINE->m.routine();case ANGLE->m.angle();case PLAYER_CONTROL->m.playerControl();case HURT->a.hurt();case STICK->m.stick();case ROTATION_DISTANCE->a.rotationDistance();case PLAYER_JUMP->m.playerJump();case STAND_OBJECT->after.standObject();};
            switch(field){case TILE,CENTRE_X,CENTRE_X_FRACTION,CENTRE_Y,CENTRE_Y_FRACTION,X_VELOCITY,Y_VELOCITY,GROUND_VELOCITY,HURT->putWord(bytes,field.byteOffset(),value);default->bytes[field.byteOffset()]=(byte)value;}
        }
        var result=CdAssetBytes.copyOf(bytes);if(!secondary(result).equals(after))throw new IllegalArgumentException("Undeclared secondary semantic write");return result;
    }
    private static SonicCdAct2PastWorldHost.Globals globals(SonicCdAct2PastWorldHost.Globals g,CdRingGlobals ring,CdOpeningObjectRuntime.ScoreGlobals score){
        return new SonicCdAct2PastWorldHost.Globals(ring,g.live(),g.contact(),score,g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());}
    private static Cut copy(Cut c,SonicCdAct2PastWorldHost.Globals globals,int goodFuture,CdAssetBytes reserved,CdAssetBytes cpu,Optional<CdObjectDrawQueue.CpuSat> completed,List<SonicCdGameModule.PastSound> sounds){
        return new Cut(globals,c.level(),goodFuture,c.goodFutureFlags(),reserved,cpu,completed,sounds,c.cameraXWord(),c.cameraYWord(),c.timeAttackModeByte(),c.layerByte());}
    private static Cut withGlobals(Cut c,SonicCdAct2PastWorldHost.Globals g){return copy(c,g,c.goodFutureByte(),c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut withRing(Cut c,CdRingGlobals r){return withGlobals(c,globals(c.globals(),r,c.globals().score()));}
    private static Cut withGoodFuture(Cut c,int value){return copy(c,c.globals(),value,c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut withReserved(Cut c,CdAssetBytes bank){return copy(c,c.globals(),c.goodFutureByte(),bank,c.cpuSat(),c.completedCpu(),c.sounds());}
    private static Cut withSounds(Cut c,List<SonicCdGameModule.PastSound> sounds){return copy(c,c.globals(),c.goodFutureByte(),c.reservedSlots(),c.cpuSat(),c.completedCpu(),sounds);}
    private static CdAssetBytes reserved(Cut c,int whole){if(whole<1||whole>31)throw new IllegalArgumentException("Reserved whole1..31 only");return c.reservedSlots().slice((whole-1)*64,64);}
    private static CdAssetBytes replace(CdAssetBytes bank,int whole,CdAssetBytes image){extent(bank,31*64);extent(image,64);if(whole<1||whole>31)throw new IllegalArgumentException("Reserved whole1..31 only");var bytes=bank.copy();System.arraycopy(image.copy(),0,bytes,(whole-1)*64,64);return CdAssetBytes.copyOf(bytes);}
    private static Unowned stop(Boundary boundary,int whole,int value){return new Unowned(boundary,whole,value);}
    private static void putWord(byte[] bytes,int at,int value){bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void extent(CdAssetBytes b,int n){if(Objects.requireNonNull(b).length()!=n)throw new IllegalArgumentException("Expected exact byte extent "+n);}
    private static void word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Unsigned native WORD");}
    private static void octet(int value){if((value&~255)!=0)throw new IllegalArgumentException("Unsigned native BYTE");}
}
