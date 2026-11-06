package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdR12bSectionArt;
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
        default ResultsOwners resultsOwners(ResultsOwners expected){throw new IllegalStateException("No actual Results owner borrow");}
        default void submitResultsPlc(ResultsPlcSubmission value){value.validate();throw stop(Boundary.PLC_OWNER,value.wholeIndex(),0x10);}
        /** Preflight all actual scalar owners; applyNative once, then adopt next with no callback gap. */
        void adopt(Adoption adoption);
        /** Actual Core/section owner must bind this native submission; no successful unbound queue stub. */
        default void submitPlc(PlcSubmission submission){submission.validate();throw stop(Boundary.PLC_OWNER,submission.wholeIndex(),submission.nativeId());}
        default void submitGoalPlc(GoalPlcSubmission submission){submission.validate();throw stop(Boundary.PLC_OWNER,submission.wholeIndex(),submission.nativeId());}
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
    enum Boundary { OBJECT_KIND, OBJECT_INDEX, SONIC_MAIN, PLAYER_SLOT, UNKNOWN_LAYER, UNKNOWN_VINT, PLC_OWNER, MONITOR_REWARD_OWNER, SPILL_GLOBALS_OWNER, SPIN_SAVED_FLAGS, END_CLOCK, END_BONUS, RESULTS_BONUS, RESULTS_TAIL, RESULTS_FLAGS }
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
        private int destinationLeftWord=-1;private Object endOperation;
        private Object resultsOperation;private ResultsOwners resultsOwners;private int resultsSectionWord=-1;
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
        int destinationLeftWord(){return destinationLeftWord;}
        int resultsSectionWord(){return resultsSectionWord;}
        ResultsOwners resultsOwners(){return resultsOwners;}
        void applyNative(){
            if(!isCurrentAdoption(this)||applied)throw new IllegalStateException("Escaped/repeated R12B adoption");
            guard();requireCut(expected);requireFrame(actor,shared);requirePlacement(placementCut);
            preflight.run(); // All pure preflights precede the first pool/Frame write.
            if(poolOutput!=null)placement.pool().restore(poolOutput);
            nativeWrite.run();applied=true;
            afterActor=frame.captureActor();afterShared=frame.captureShared();afterPlacement=placement.capture();
        }
    }
    /** Opaque original Projector request, never a general section setter or retained snapshot token. */
    final class PlcSubmission {
        private final CdProjector.PlcCallRequest request;
        private final Cut expected;
        private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placementCut;
        private boolean adopted;
        private PlcSubmission(CdProjector.PlcCallRequest request){this.request=request;expected=read();actor=frame.captureActor();shared=frame.captureShared();placementCut=placement.capture();}
        SonicCdAct2PastObjectPass owner(){return SonicCdAct2PastObjectPass.this;}
        int nativeId(){return request.request().nativeId();}
        int wholeIndex(){return request.expectedSource().identity().wholeIndex();}
        void validate(){if(!isCurrentPlcSubmission(this)||adopted)throw new IllegalStateException("Escaped/repeated Projector PLC submission");guard();requireCut(expected);requireFrame(actor,shared);requirePlacement(placementCut);placementCut(request.actualCut());source(request.expectedSource());}
        void acknowledge(){validate();adopted=true;}
    }
    boolean isCurrentPlcSubmission(PlcSubmission submission){return submission!=null&&submission==pendingPlc&&plcPort&&phase==Phase.WALKING&&dispatch!=null&&dispatch.kind==Kind.PROJECTOR&&dispatch.operation==submission.request&&dispatch.prepared&&dispatch.used&&projector.isCurrentCall(submission.request);}
    private enum Phase { READY,WALKING,WALKED,DRAINING,DONE,FAILED,DISCARDED }
    private enum Kind { PRIMARY,RESERVED,RING,SPRING,MONITOR,TIME_POST,COLLAPSE,PLATFORM,TAM,ANIMAL,SWING,ROBOT,ANTON,BOULDER,WALL,FLOAT_BLOCK,SPIKES,LOG_SHADOW,NULL,PATA_BATA,PROJECTOR,EXPLOSION,FLOWER,POINTS,LOST_RING,MONITOR_ITEM,MOSQUI,SCENERY,SPRINGBOARD,DISC,DOOR,VERTICAL_DOOR,SPIN_TAG,GOAL,SIGNPOST,RESULTS,DOOR_SPLASH }
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
    private CdFloatBlock floatBlock;
    private CdSpikes spikes;private CdLogShadow logShadow;
    private CdPataBata pataBata;
    private CdProjector projector;
    private CdExplosion explosion;private CdFlower flower;private CdPoints points;
    private CdLostRing lostRing;private CdMonitorItem monitorItem;private CdMosqui mosqui;
    private CdScenery scenery;private CdSpringboard springboard;
    private CdTunnelDoorSplash doorSplash;private SplashInvocation splashInvocation;
    private CdGoalPost goalPost;private CdSignpost signpost;private EndInvocation endInvocation;private GoalPlcSubmission pendingGoalPlc;
    private CdResults results;private ResultsInvocation resultsInvocation;private ResultsPlcSubmission pendingResultsPlc;private boolean resultsBorrow;
    // BEGIN R12B Disc/Door bindings fields.
    private CdSpinningDisc spinningDisc;private CdTunnelDoor tunnelDoor;private CdTunnelDoorVertical verticalDoor;
    private DiscInvocation discInvocation;private DoorScope doorScope;private BoundInvocation verticalInvocation;
    // BEGIN R12B SpinTunnel R2 fields.
    private CdSpinTunnelTag spinTunnel;private SpinInvocation spinInvocation;
    // END R12B SpinTunnel R2 fields.
    // END R12B Disc/Door bindings fields.
    private Phase phase=Phase.READY;private CdObjectDrawQueue queue;private Dispatch dispatch;
    private boolean port,queuePort,plcPort;private Adoption pending;private PlcSubmission pendingPlc;
    private static final class Dispatch {
        final Kind kind;final int whole;final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean drawn;long ordinal;Object operation;boolean prepared,completed,used;
        CdObjectPlacementRuntime.Snapshot observedCut;Cut observedGlobals;
        CdPlayerFrameRuntime.ActorState ownerActor;CdPlayerFrameRuntime.SharedState ownerShared;Cut ownerCut;
        CdPlayerFrameRuntime.ActorState actor;CdPlayerFrameRuntime.SharedState shared;
        List<byte[]> expectedImages;byte[] expectedSaved;CdSwing.PlayerCut playerCut;
        int wallBranch,wallSolids,wallFms,wallHalves,wallDraws;
        int floatOrdinal;boolean floatStopped;CdObjectPlacementRuntime.Status floatStatus;CdAssetBytes floatBank;
        CdObjectPool.Snapshot spikePool;CdObjectPlacementRuntime.Snapshot spikeBase,spikeDrawCut;
        CdSolidObject.Result spikeSolid;boolean spikeVertical,spikeStopped,spikeY,spikeHurt;
        int spikeSolids,logInitialRoutine,logInitStores;boolean logSavedCleared,logDeleted;
        final Deque<EffectScope> effects=new ArrayDeque<>();long effectOrdinal,publications;
        CdMonitorItem.Result monitorResult;CdLostRing.Inputs lostInputs;CdLostRing.Ports lostPorts;boolean collecting;
        DecorScope decor;int decorInitialRoutine,sceneryInitStores;boolean decorSavedCleared,decorDeleted;
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
        goalPost=CdGoalPost.fromAct2PastAssets(placement,terrain,this::goalRead,this::goalReached,this::goalPlc,null,this::goalDraw);
        signpost=CdSignpost.fromAct2PastAssets(placement,terrain,this::signRead,this::signStore,this::signSound,this::signDraw);
        results=CdResults.fromAct2PastAssets(placement,terrain,this::resultsRead,this::resultsBonus,this::resultsPoints,this::resultsPlc,this::resultsSound,this::resultsDraw,this::resultsProgramme,this::resultsAction);
        // BEGIN R12B Disc/Door cached source factories.
        routeObjects();
        spinningDisc=CdSpinningDisc.fromAct2PastAssets(placement,terrain,math,solid,this::discSolid,
                new CdSpinningDisc.PlayerOwner(){public CdSpinningDisc.PlayerCut read(){return discRead();}
                    public void commit(CdSpinningDisc.PlayerCall c){discPlayer(c);}},this::discFm,this::discDraw);
        tunnelDoor=CdTunnelDoor.fromAct2PastAssets(placement,terrain,this::doorSecondary,solid,
                new CdTunnelDoor.PrefixSolidOwner(){
                    public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){doorCut(c);doorUse(c);return doorLive();}
                    public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){return doorSolid(c);}},
                (p,id)->{var c=doorOperation(CdTunnelDoor.DrawCall.class);doorUse(c);drawDynamic(Kind.DOOR,p,id,()->tunnelDoor.isCurrentCall(c));},
                sound->{var c=doorOperation(CdTunnelDoor.FmCall.class);if(c.sound()!=sound)throw new IllegalArgumentException("Foreign Door FM");
                    doorUse(c);SonicCdAct2PastObjectPass.this.sound(new SonicCdGameModule.PastTunnelDoorFm(sound));},
                new CdTunnelDoor.DispatchObserver(){
                    public void beforePublication(CdTunnelDoor.Publication c){doorPublication(c);}
                    public void afterPublication(CdTunnelDoor.Publication c){doorAfter(c,null);}
                    public void beforeCall(Object c){doorBefore(c);}
                    public void afterCall(Object c,Object result){doorAfter(c,result);}
                    public void failedCall(Object c,Throwable original){doorFailure(c,original);}});
        verticalDoor=CdTunnelDoorVertical.fromAct2PastAssets(placement,terrain,solid,this::verticalSolid,
                this::verticalDraw,this::verticalFm);
        // BEGIN R12B SpinTunnel R2 cached source factory.
        spinTunnel=CdSpinTunnelTag.fromAct2PastAssets(placement,terrain);
        // END R12B SpinTunnel R2 cached source factory.
        // END R12B Disc/Door cached source factories.
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
        floatBlock=CdFloatBlock.fromAct2PastAssets(placement,terrain,solid,c->{requireKind(Kind.FLOAT_BLOCK);floatNonwrites();
            var d=dispatch;if(d.floatStopped)throw new IllegalStateException("FloatBlock callback after native stop");
            claim(c,()->floatBlock.isCurrentPrefixTopSolidCall(c,d.floatOrdinal));poolCut(c.callCut());
            commitSolid(c.callCut(),c.player(),c.globals(),c.outcome());
            if(c.outcome() instanceof CdSolidObject.NativeStopped)d.floatStopped=true;else d.floatOrdinal++;
        },(p,id)->{requireKind(Kind.FLOAT_BLOCK);floatNonwrites();
            if(dispatch.floatStopped)throw new IllegalStateException("FloatBlock Draw after native stop");
            drawDynamic(Kind.FLOAT_BLOCK,p,id,()->floatBlock.isCurrentDrawCall(p,id));});
        spikes=CdSpikes.fromAct2PastAssetsWithPrefix(placement,terrain,solid,this::spikesSolid,this::spikesY,this::spikesHurt,
                (p,id)->{requireKind(Kind.SPIKES);spikesNonwrites();
                    if(dispatch.spikeStopped||dispatch.spikeY!=dispatch.spikeHurt||!dispatch.spikeHurt&&spikesHurtReached())throw new IllegalStateException("Spikes Draw before reached native calls");
                    drawDynamic(Kind.SPIKES,p,id,()->spikes.isCurrentDrawCall(p,id));dispatch.spikeDrawCut=placement.capture();});
        logShadow=CdLogShadow.fromAct2PastAssets(placement,terrain,new CdLogShadow.DispatchObserver(){
            public void beforePublication(CdLogShadow.Publication c){logBeforePublication(c);}
            public void afterPublication(CdLogShadow.Publication c){logAfter(c,true);}
            public void beforeCall(Object c){logBeforeDraw((CdLogShadow.DrawCall)c);}
            public void afterCall(Object c){logAfter(c,false);}
        },c->{requireKind(Kind.LOG_SHADOW);var d=dispatch;
            if(d.operation!=c||d.used||!logShadow.isCurrentCall(c))throw new IllegalStateException("Not the original Log Draw");
            claim(c,()->logShadow.isCurrentCall(c));placementCut(c.cut());source(c.source());d.used=true;
            drawDynamic(Kind.LOG_SHADOW,placement.pool(),c.source().identity(),()->logShadow.isCurrentCall(c));});
        // Actual retained object metadata follows the constructor's exact Frame/pair/layout gate.
        // No constructed Identity, visual hull or standalone body can establish World admission.
        var decorAssets=terrain.objects();
        if(decorAssets.identity()!=terrain.identity()||decorAssets.placements()!=placement.placements())
            throw new IllegalArgumentException("Scenery/Springboard require the retained R12B metadata/layout");
        scenery=new CdScenery(placement,new CdScenery.Assets(decorAssets.view(CdR12bObjectAssets.View.SCENERY_BASE_TILES),
                decorAssets.scenery(),CdR12bObjectAssets.View.MAP_SPR_SCENERY.range().nativeAddress()),new CdScenery.DispatchObserver(){
            public void beforePublication(CdScenery.Publication c){decorPublication(c,c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex());}
            public void afterPublication(CdScenery.Publication c){decorAfter(c);}
            public void beforeCall(Object c){var call=(CdScenery.DrawCall)c;decorDrawBefore(call,call.cut(),call.source());}
            public void afterCall(Object c){decorAfter(c);}
        },c->decorDraw(c,c.source()));
        springboard=new CdSpringboard(placement,CdSpringboard.Assets.fromAct2PastAssets(decorAssets),this::springboardPrimary,
                c->decorDraw(c,c.source()),new CdSpringboard.DispatchObserver(){
            public void beforePublication(CdSpringboard.Publication c){decorPublication(c,c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex());}
            public void afterPublication(CdSpringboard.Publication c){decorAfter(c);}
            public void beforeCall(Object c){
                if(c instanceof CdSpringboard.PrimaryCall call){var scope=decorBegin(call,call.ordinal(),call.cut(),call.source(),true,false);
                    if(call.expectedState()!=frame.state()||call.expectedEnvironment()!=frame.environment())
                        throw new IllegalArgumentException("Springboard requires the original complete primary pair");
                    // Root-verified narrow Frame dependency; source pin is recorded in the packet report.
                    // Build all output references before this callback can publish any owner value.
                    scope.primary=frame.prepareObjectSpringboardPlayerStore(call);
                    if(scope.primary.expectedState()!=call.expectedState()||scope.primary.expectedEnvironment()!=call.expectedEnvironment()
                            ||scope.primary.finalState()!=call.outputState()||scope.primary.finalEnvironment()!=call.expectedEnvironment())
                        throw new IllegalArgumentException("Frame did not prepare the actual Springboard one-store references");
                }else{var call=(CdSpringboard.DrawCall)c;decorDrawBefore(call,call.cut(),call.source());}
            }
            public void afterCall(Object c){decorAfter(c);}
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
        animal=CdAnimal.fromAct2PastAssets(placement,terrain,player,math,probes,
                c->{requireKind(Kind.ANIMAL);claim(c,()->animal.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());angle(c.expectedPrimaryAngle(),c.value());},
                c->{requireKind(Kind.ANIMAL);claim(c,()->animal.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());drawDynamic(Kind.ANIMAL,placement.pool(),c.expectedSource().identity(),()->animal.isCurrentCall(c));},
                new CdActiveAnimal.DispatchObserver(){
                    public void beforePublication(CdActiveAnimal.Publication c){observeBefore(c,c.ordinal(),c.actualCut(),c.kind().name(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value());}
                    public void afterPublication(CdActiveAnimal.Publication c){observeAfter(c,true);}
                    public void beforeCall(Object c){observeBeforeCall(c,animal.currentCallOrdinal(c));}
                    public void afterCall(Object c){observeAfter(c,false);}
                },new CdActiveAnimal.ReceiptGuard(){public void prepared(Object c){receiptPrepared(c);}public void completed(Object c){receiptCompleted(c);}});
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
        pataBata=CdPataBata.fromAct2PastAssets(placement,terrain,player,math,
                (p,id)->drawDynamic(Kind.PATA_BATA,p,id,()->pataBata.isCurrentDrawCall(p,id)));
        projector=CdProjector.fromAct2PastAssets(placement,terrain,solid,
                c->{requireKind(Kind.PROJECTOR);claim(c,()->projector.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());commitSolid(c.actualCut().pool(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());},
                c->{requireKind(Kind.PROJECTOR);claim(c,()->projector.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());var cut=read();var g=cut.globals();
                    var next=new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),c.value(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
                    publish(cut,withGlobals(cut,next),null,()->{},()->{});},
                this::submitProjectorPlc,
                c->{requireKind(Kind.PROJECTOR);claim(c,()->projector.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());sound(new SonicCdGameModule.PastProjectorFm(c.nativeId()));},
                c->{requireKind(Kind.PROJECTOR);claim(c,()->projector.isCurrentCall(c));placementCut(c.actualCut());source(c.expectedSource());drawDynamic(Kind.PROJECTOR,placement.pool(),c.expectedSource().identity(),()->projector.isCurrentCall(c));},
                new CdProjector.DispatchObserver(){
                    public void beforePublication(CdProjector.Publication c){observeBefore(c,c.ordinal(),c.actualCut(),c.kind().name(),c.expectedSource(),c.before(),c.output(),c.savedByteIndex(),c.previousValue(),c.value());}
                    public void afterPublication(CdProjector.Publication c){observeAfter(c,true);}
                    public void beforeCall(Object c){observeBeforeCall(c,projector.currentCallOrdinal(c));}
                    public void afterCall(Object c){observeAfter(c,false);}
                },new CdProjector.ReceiptGuard(){public void prepared(Object c){receiptPrepared(c);}public void completed(Object c){receiptCompleted(c);}});
        // BEGIN R12B Door Splash original cached producer binding.
        doorSplash=CdTunnelDoorSplash.fromAct2PastAssets(placement,terrain,new CdTunnelDoorSplash.DispatchObserver(){
            public void beforePublication(CdTunnelDoorSplash.Publication c){splashPublication(c);}
            public void afterPublication(CdTunnelDoorSplash.Publication c){splashAfter(c);}
            public void beforeCall(Object c){splashBeforeCall(c);}
            public void afterCall(Object c){splashAfter(c);}
        },c->{splashUse(c);drawDynamic(Kind.DOOR_SPLASH,placement.pool(),c.source().identity(),()->doorSplash.isCurrentCall(c)&&!doorSplash.isCurrentCallCompleted(c));},
                c->{splashUse(c);sound(new SonicCdGameModule.PastTunnelDoorSplashFm(c.sound()));},
                new CdTunnelDoorSplash.ReceiptGuard(){public void prepared(Object c){splashPrepared(c);}public void completed(Object c){splashCompleted(c);}});
        // END R12B Door Splash original cached producer binding.
        explosion=CdExplosion.fromAct2PastAssets(placement,terrain,new CdExplosion.DispatchObserver(){
            public void beforePublication(CdExplosion.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output(),-1);}
            public void afterPublication(CdExplosion.Publication c){effectAfter(c);}
            public void beforeCall(Object c){var v=(CdExplosion.DrawCall)c;effectCall(c,0,v.cut(),v.source(),true);}
            public void afterCall(Object c){effectAfter(c);}
        },c->effectPort(c,()->drawDynamic(Kind.EXPLOSION,placement.pool(),c.source().identity(),()->explosion.isCurrentCall(c))),
                new CdExplosion.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        points=CdPoints.fromAct2PastAssets(placement,terrain,new CdPoints.DispatchObserver(){
            public void beforePublication(CdPoints.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.before(),c.output(),-1);}
            public void afterPublication(CdPoints.Publication c){effectAfter(c);}
            public void beforeCall(Object c){var v=(CdPoints.DrawCall)c;effectCall(c,0,v.cut(),v.source(),true);}
            public void afterCall(Object c){effectAfter(c);}
        },c->effectPort(c,()->drawDynamic(Kind.POINTS,placement.pool(),c.source().identity(),()->points.isCurrentCall(c))),
                new CdPoints.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        flower=CdFlower.fromAct2PastAssets(placement,terrain,probes,new CdFlower.DispatchObserver(){
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
        mosqui=CdMosqui.fromAct2PastAssets(placement,terrain,probes,
                c->effectPort(c,()->angle(solidGlobals().primaryAngle(),c.value())),
                c->effectPort(c,()->sound(new SonicCdGameModule.PastMosquiFm(c.sound()))),new CdMosqui.DispatchObserver(){
            public void beforePublication(CdMosqui.Publication c){effectPublication(c,c.ordinal(),c.cut(),c.source(),c.source(),c.output(),c.savedIndex());effectPrepared(c);}
            public void afterPublication(CdMosqui.Publication c){effectAfter(c);effectCompleted(c);}
            public void beforeCall(Object c){switch(c){
                case CdMosqui.DrawCall v->effectCall(c,0,v.cut(),v.source(),true);
                case CdMosqui.PrimaryAngleCall v->{poolCut(v.actualCut());effectCall(c,0,placement.capture(),v.expectedSource(),false);}
                case CdMosqui.FmCall v->{poolCut(v.actualCut());effectCall(c,0,placement.capture(),v.expectedSource(),false);}
                default->throw new IllegalArgumentException("Unknown Mosqui native operation");}effectPrepared(c);}
            public void afterCall(Object c){effectAfter(c);effectCompleted(c);}
        },c->effectPort(c,()->drawDynamic(Kind.MOSQUI,placement.pool(),c.source().identity(),()->mosqui.isCurrentCall(c))));
        // Seven-argument original owner binding deliberately leaves unsupported Ring rewards ungranted.
        // The pass constructor already authenticates the retained pair and placement; Assets is descriptive.
        monitorItem=CdMonitorItem.withWorldOwner(placement,CdMonitor.Assets.fromAct2PastAssets(terrain.objects()),
                request->{var c=monitorOperation(CdMonitorItem.ShoesCall.class);if(c.request()!=request)throw new IllegalStateException("Foreign Shoes request");
                    effectUse(c);throw stop(Boundary.MONITOR_REWARD_OWNER,dispatch.whole,4);},
                request->{var c=monitorOperation(CdMonitorItem.ShieldCall.class);if(c.request()!=request)throw new IllegalStateException("Foreign Shield request");
                    effectUse(c);throw stop(Boundary.MONITOR_REWARD_OWNER,dispatch.whole,2);},
                (pool,identity)->{var c=monitorOperation(CdMonitorItem.DrawCall.class);effectPort(c,()->drawDynamic(Kind.MONITOR_ITEM,pool,identity,()->monitorItem.isCurrentPrimitive(c)));},
                new CdMonitorItem.DispatchObserver(){
                    public void beforeOperation(Object c){monitorBefore(c);}
                    public void afterOperation(Object c){effectAfter(c);}
                    public void failedOperation(Object c,Throwable original){effectFailed(c);}
                },new CdMonitorItem.ReceiptGuard(){public void prepared(Object c){effectPrepared(c);}public void completed(Object c){effectCompleted(c);}});
        lostRing=CdLostRing.fromAct2PastAssets(placement,terrain,math,probes,
                c->{lostCall(c,c.cut());angle(solidGlobals().primaryAngle(),c.value());},
                c->{lostCall(c,c.cut());switch(c.request()){
                    case CdLostRing.SoundRequest v->sound(new SonicCdGameModule.PastLostRingFm(v.sound()));
                    case CdLostRing.RewardFmRequest v->sound(new SonicCdGameModule.PastRingFm(v.sound()));
                    case CdLostRing.RewardSubCpuRequest v->sound(new SonicCdGameModule.PastRingSubCpu(v.command()));
                    default->throw new IllegalArgumentException("Unknown actual LostRing FM request");}},
                (pool,identity)->{lostInvocation();if(!lostRing.isCurrentDrawCall(pool,identity))throw new IllegalStateException("Foreign LostRing Draw");
                    lostCall(identity,lostRing.currentSource(identity));drawDynamic(Kind.LOST_RING,pool,identity,()->lostRing.isCurrentDrawCall(pool,identity));},
                (globals,calls)->{lostInvocation();var d=dispatch;if(d.collecting||!lostRing.isCurrentCollectCall(globals,calls)||!d.consumed.add(globals)||!d.consumed.add(calls))throw new IllegalStateException("Foreign/reentrant LostRing CollectRing");
                    var placed=placement.capture();var actor=frame.captureActor();var shared=frame.captureShared();d.collecting=true;Throwable original=null;
                    try{CdLostRing.collectRing(globals,calls);}catch(RuntimeException|Error failure){original=failure;throw failure;}
                    finally{try{requirePlacement(placed);requireFrame(actor,shared);}catch(RuntimeException|Error verification){if(original==null)throw verification;if(verification!=original)original.addSuppressed(verification);}finally{d.collecting=false;}}});
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
            case PRIMARY->{if(frame.state().motion().routine()==2||frame.state().motion().routine()==4){runPrimaryMain(actualLevelFrame);break;}
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
            // BEGIN R12B Disc/Door physical dispatch.
            case DISC->runDisc(whole);
            case DOOR->{requireKind(Kind.DOOR);routeObjects();if(doorScope!=null)throw new IllegalStateException("Nested Door dispatch");
                try{var result=tunnelDoor.run(whole,new CdTunnelDoor.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdTunnelDoor.SolidInputs(primary(),gates(),solidGlobals()));
                    if(doorScope!=null||!result.state().equals(placement.pool().state(whole))||!result.finalPrimary().equals(primary())||!result.finalGlobals().equals(solidGlobals()))throw new IllegalStateException("Incomplete Door return");}
                finally{doorScope=null;}}
            case VERTICAL_DOOR->{requireKind(Kind.VERTICAL_DOOR);routeObjects();if(verticalInvocation!=null)throw new IllegalStateException("Nested DoorV dispatch");
                verticalInvocation=new BoundInvocation(verticalDoor,whole,placement.capture());
                try{var result=verticalDoor.run(whole,new CdTunnelDoorVertical.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdTunnelDoorVertical.SolidInputs(primary(),gates(),solidGlobals()));
                    if(!result.state().equals(placement.pool().state(whole))||!result.finalPlayer().equals(primary())||!result.finalGlobals().equals(solidGlobals()))throw new IllegalStateException("Incomplete DoorV return");}
                finally{verticalInvocation=null;}}
            // BEGIN R12B SpinTunnel R2 physical dispatch.
            case SPIN_TAG->runSpinTunnel(whole);
            // END R12B SpinTunnel R2 physical dispatch.
            // END R12B Disc/Door physical dispatch.
            case SPRING->spring.run(whole,new CdSpring.Globals(cut.cameraXWord()),new CdSpring.SolidInputs(primary(),gates(),solidGlobals()));
            case BOULDER->boulder.run(whole,new CdBoulder.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),
                    new CdBoulder.SolidInputs(primary(),gates(),solidGlobals()));
            case WALL->{var entry=placement.pool().state(whole);dispatch.wallBranch=entry.routine()==4?4:entry.unsignedByte(COLLISION_STATUS)==0?2:3;
                var result=wall.run(whole,new CdBreakableWall.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdBreakableWall.Inputs(primary(),gates(),solidGlobals()));
                requireKind(Kind.WALL);if(!dispatch.completed||dispatch.operation!=result)throw new IllegalStateException("Not actual completed Wall dispatch");}
            case FLOAT_BLOCK->{var before=placement.capture();dispatch.floatStatus=before.status();dispatch.floatBank=before.savedFlags();
                try{var result=floatBlock.run(whole,new CdFloatBlock.Globals(g.timeZone(),cut.cameraXWord(),cut.cameraYWord()),new CdFloatBlock.SolidInputs(primary(),gates(),solidGlobals()));
                    requireKind(Kind.FLOAT_BLOCK);floatNonwrites();
                    if(dispatch.floatStopped||!result.state().equals(placement.pool().state(whole))
                            ||result.effects().stream().filter(CdFloatBlock.TopSolid.class::isInstance).count()!=dispatch.floatOrdinal
                            ||result.effects().stream().filter(effect->effect instanceof CdFloatBlock.CallReturned call&&call.call()==CdFloatBlock.NativeCall.DRAW_OBJECT).count()!=(dispatch.drawn?1:0))
                        throw new IllegalStateException("FloatBlock completed metadata lacks actual publications");
                }catch(RuntimeException|Error original){try{floatNonwrites();}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}throw original;}}
            case SPIKES->{var d=dispatch;d.spikeBase=placement.capture();d.spikePool=d.spikeBase.pool();d.spikeVertical=(placement.pool().state(whole).spriteFlags()&2)!=0;
                try{var result=spikes.run(whole,new CdSpikes.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),
                        new CdSpikes.Inputs(primary(),gates(),solidGlobals(),spikesPlayerGlobals(),spikesWorldGlobals()));
                    requireKind(Kind.SPIKES);verifySpikesTail(true);
                    if(d.spikeStopped||!d.drawn||!result.state().equals(placement.pool().state(whole))||!result.player().equals(primary())
                            ||!result.solidGlobals().equals(solidGlobals())||!result.playerGlobals().equals(spikesPlayerGlobals())||!result.worldGlobals().equals(spikesWorldGlobals())
                            ||result.effects().stream().filter(CdSpikes.Solid.class::isInstance).count()!=d.spikeSolids
                            ||result.effects().stream().filter(CdSpikes.YRewound.class::isInstance).count()!=(d.spikeY?1:0)
                            ||result.effects().stream().filter(CdSpikes.Hurt.class::isInstance).count()!=(d.spikeHurt?1:0))
                        throw new IllegalStateException("Spikes completed metadata differs from actual publications");
                }catch(RuntimeException|Error original){try{if(d.spikeDrawCut==null)spikesNonwrites();else verifySpikesTail(false);}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}throw original;}}
            case LOG_SHADOW->{dispatch.logInitialRoutine=placement.pool().state(whole).routine();var result=logShadow.run(whole,new CdLogShadow.Globals(g.timeZone(),cut.goodFutureByte(),g.zoneAct()&255,g.timeWarpDirection(),cut.cameraXWord()));
                requireKind(Kind.LOG_SHADOW);if(dispatch.operation!=null||!dispatch.drawn||dispatch.logInitialRoutine==0&&dispatch.logInitStores!=3
                        ||!result.state().equals(placement.pool().state(whole))||result.deleted()!=dispatch.logDeleted)
                    throw new IllegalStateException("Log omitted original native completion");}
            case SCENERY->{dispatch.decorInitialRoutine=placement.pool().state(whole).routine();
                try{var r=scenery.run(whole,new CdScenery.Globals(g.timeZone(),cut.goodFutureByte(),g.zoneAct()&255,g.timeWarpDirection(),cut.cameraXWord()));
                    decorResult(r.state(),r.deleted(),true);
                }catch(RuntimeException|Error original){decorFailed(original);throw original;}finally{dispatch.decor=null;}}
            case SPRINGBOARD->{dispatch.decorInitialRoutine=placement.pool().state(whole).routine();
                try{var r=springboard.run(whole,new CdSpringboard.Globals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),
                        new CdSpringboard.PrimaryInputs(frame.state(),frame.environment()));
                    decorResult(r.state(),r.deleted(),dispatch.decorInitialRoutine!=0);
                    if(r.primary()!=frame.state()||r.environment()!=frame.environment())throw new IllegalStateException("Springboard omitted its actual complete primary result");
                }catch(RuntimeException|Error original){decorFailed(original);throw original;}finally{dispatch.decor=null;}}
            case MONITOR->monitor.runPast(whole,monitorGlobals(),new CdMonitor.SolidInputs(primary(),gates(),solidGlobals()));
            case TIME_POST->post.runPast(whole,postGlobals());
            case COLLAPSE->collapse.run(whole,new CdCollapsePlatform.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()),new CdCollapsePlatform.Inputs(primary(),gates(),solidGlobals()));
            case PLATFORM->platform.run(whole,new CdPlatform.Globals(g.zoneAct(),cut.globals().ring().timeStopTimer(),cut.cameraXWord(),cut.cameraYWord(),g.timeZone()),new CdPlatform.Inputs(primary(),gates(),solidGlobals()));
            case PATA_BATA->pataBata.run(whole,new CdPataBata.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),cut.cameraXWord()));
            case DOOR_SPLASH->runDoorSplash(whole);
            case EXPLOSION->{var r=explosion.run(whole);effectResult(r.state());}
            case FLOWER->{var r=flower.run(whole);effectResult(r.state());}
            case POINTS->{var r=points.run(whole);effectResult(r.state());}
            case MOSQUI->{var r=mosqui.run(whole,new CdMosqui.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),e.globals().debugMode(),cut.cameraXWord(),frame.state().motion().centreX16_16()>>>16,frame.state().motion().centreY16_16()>>>16));effectResult(r.state());}
            case MONITOR_ITEM->{var r=monitorItem.run(whole,Optional.of(new CdMonitorItem.PrimarySupport(frame.state().header().slotId(),frame.state().motion().flags())));
                if(r!=dispatch.monitorResult)throw new IllegalStateException("MonitorItem did not return its original completed Result");effectResult(r.state());}
            case LOST_RING->{dispatch.lostInputs=new CdLostRing.Inputs(vIntWord(),127-whole,g.zoneAct()>>>8,e.movement().bounds().bottom());
                dispatch.lostPorts=lostPorts();try{var r=lostRing.run(whole,dispatch.lostInputs,dispatch.lostPorts);effectResult(r.state());}finally{dispatch.lostInputs=null;dispatch.lostPorts=null;}}

            case ANTON->anton.run(whole,new CdAnton.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),cut.cameraXWord(),solidGlobals().primaryAngle()));
            case TAM->tam.run(whole,new CdTamabboh.Globals(cut.goodFutureByte(),g.timeZone(),g.timeWarpDirection(),e.globals().debugMode(),cut.cameraXWord(),solidGlobals().primaryAngle()));
            case ANIMAL->{var result=animal.runWithReads(whole,new CdActiveAnimal.Reads(){public int timeZone(){return reached().movement().gates().timeZone();}public int goodFuture(){requireKind(Kind.ANIMAL);return read().goodFutureByte();}
                public int projDestroyed(){requireKind(Kind.ANIMAL);return read().globals().projectorByte();}public int timeWarpDir(){return reached().movement().gates().timeWarpDirection();}
                public int cameraXWord(){requireKind(Kind.ANIMAL);return read().cameraXWord();}public int act(){return reached().movement().gates().zoneAct()&255;}public int primaryAngle(){requireKind(Kind.ANIMAL);return solidGlobals().primaryAngle();}
                private CdPlayerBody.Environment reached(){requireKind(Kind.ANIMAL);return frame.environment();}});
                if(dispatch.operation!=null||!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Animal omitted original completion");}
            case PROJECTOR->{var result=projector.run(whole,new CdProjector.Reads(){public int projDestroyed(){requireKind(Kind.PROJECTOR);return read().globals().projectorByte();}
                public int act(){return reached().zoneAct()&255;}public int timeZone(){return reached().timeZone();}public int timeWarpDirection(){return reached().timeWarpDirection();}public int cameraXWord(){requireKind(Kind.PROJECTOR);return read().cameraXWord();}
                private CdPlayerMovement.Gates reached(){requireKind(Kind.PROJECTOR);return frame.environment().movement().gates();}},new CdProjector.Inputs(primary(),gates(),solidGlobals()));
                if(dispatch.operation!=null||!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Projector omitted original completion");}
            case GOAL->runGoal(whole);
            case SIGNPOST->runSignpost(whole);
            case RESULTS->runResults(whole);
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
        return switch(id){case 0x0A->Kind.SPRING;case 0x10->Kind.RING;case 0x12->Kind.FLOAT_BLOCK;case 0x1B->Kind.BOULDER;case 0x36->Kind.WALL;case 0x26->Kind.SPIKES;case 0x33->Kind.LOG_SHADOW;case 0x19->placement.pool().state(whole).subtype()<8?Kind.MONITOR:Kind.TIME_POST;
            case 0x20->Kind.COLLAPSE;case 0x21->Kind.PLATFORM;case 0x22->Kind.TAM;case 0x24->Kind.ANIMAL;case 0x29->Kind.SWING;case 0x2D->Kind.ROBOT;case 0x3F->Kind.ANTON;case 0x3E->Kind.PATA_BATA;case 0x2E->Kind.PROJECTOR;case 0x18->Kind.EXPLOSION;case 0x1F->Kind.FLOWER;case 0x11->Kind.LOST_RING;case 0x1A->Kind.MONITOR_ITEM;case 0x3D->Kind.MOSQUI;case 0x1C->{if((placement.pool().state(whole).subtype()&128)==0)throw stop(Boundary.OBJECT_KIND,whole,id);yield Kind.POINTS;}
            // BEGIN R12B Disc/Door retained native-index dispatch.
            case CdSpinningDisc.OBJECT_ID->Kind.DISC;
            case CdTunnelDoor.OBJECT_ID->Kind.DOOR;
            case CdTunnelDoorVertical.OBJECT_ID->Kind.VERTICAL_DOOR;
            // BEGIN R12B SpinTunnel R2 retained native-index dispatch.
            case CdSpinTunnelTag.OBJECT_ID->Kind.SPIN_TAG;
            // END R12B SpinTunnel R2 retained native-index dispatch.
            // END R12B Disc/Door retained native-index dispatch.
            case CdTunnelDoorSplash.OBJECT_ID->Kind.DOOR_SPLASH;
            case CdGoalPost.OBJECT_ID->Kind.GOAL;case CdSignpost.OBJECT_ID->Kind.SIGNPOST;case CdResults.OBJECT_ID->Kind.RESULTS;
            case CdScenery.OBJECT_ID->Kind.SCENERY;case CdSpringboard.OBJECT_ID->Kind.SPRINGBOARD;
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
        if(observedKind()){if(dispatch.operation!=call||!dispatch.prepared||dispatch.used)throw new IllegalStateException("Missing original prepared receipt");dispatch.used=true;}}
    private void wallCall(Object call,CdBreakableWall.SourceCut cut){requireKind(Kind.WALL);wall.validateCurrentCall(call);
        claim(call,()->wall.matchesPendingOperation(call));placementCut(cut.placement());source(cut.source());}
    private void wallAck(Object call){dispatch.operation=call;wall.acknowledgeCurrentCall(call);}
    private void floatNonwrites(){var actual=placement.capture();
        if(dispatch.floatStatus==null||!dispatch.floatStatus.equals(actual.status())||!same(dispatch.floatBank,actual.savedFlags()))
            throw new IllegalStateException("FloatBlock changed placement status or saved bank");}
    private void submitProjectorPlc(CdProjector.PlcCallRequest call){
        requireKind(Kind.PROJECTOR);claim(call,()->projector.isCurrentCall(call));placementCut(call.actualCut());source(call.expectedSource());
        if(plcPort||pendingPlc!=null)throw new IllegalStateException("Recursive Projector PLC submission");
        var submission=new PlcSubmission(call);pendingPlc=submission;plcPort=true;
        try{submission.validate();ports.submitPlc(submission);if(!submission.adopted)throw new IllegalStateException("Section owner omitted actual PLC receipt");requireCut(submission.expected);requireFrame(submission.actor,submission.shared);requirePlacement(submission.placementCut);checkpoint();}
        finally{pendingPlc=null;plcPort=false;}
    }
    private void publish(Cut expected,Cut next,CdObjectPool.Snapshot pool,Runnable preflight,Runnable write){
        if(port||pending!=null)throw new IllegalStateException("Recursive R12B adoption");var a=new Adoption(expected,next,pool,preflight,write);pending=a;port=true;
        try{requireCut(expected);ports.adopt(a);if(!a.applied)throw new IllegalStateException("Root omitted actual adoption receipt");requireCut(next);requireFrame(a.afterActor,a.afterShared);requirePlacement(a.afterPlacement);if(dispatch!=null)checkpoint();}
        finally{pending=null;port=false;}
    }
    private void checkpoint(){dispatch.ownerActor=frame.captureActor();dispatch.ownerShared=frame.captureShared();dispatch.ownerCut=read();}
    private void sound(SonicCdGameModule.PastSound sound){var cut=read();var sounds=new ArrayList<>(cut.sounds());sounds.add(Objects.requireNonNull(sound));publish(cut,withSounds(cut,sounds),null,()->{},()->{});}
    private void angle(int expected,int value){if(solidGlobals().primaryAngle()!=expected)throw new IllegalArgumentException("Stale primary angle");var cut=read();
        publish(cut,cut,null,()->{if(solidGlobals().primaryAngle()!=expected)throw new IllegalArgumentException("Stale primary angle");},()->frame.applyObjectPrimaryAngle(value));}
    private CdPlayerTouchResponse.PlayerGlobals spikesPlayerGlobals(){var c=read().globals().contact();var e=frame.environment();
        return new CdPlayerTouchResponse.PlayerGlobals(c.timeWarp(),e.globals().invincible(),c.shield(),c.combineRing(),e.globals().debugCheat(),e.globals().debugMode(),e.movement().globals().scoreChain(),e.movement().gates().miniSonic());}
    private CdPlayerTouchResponse.WorldGlobals spikesWorldGlobals(){var c=read().globals();var r=c.ring();var s=c.score();
        return new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),s.score(),s.nextLifeScore(),s.updateHudScore());}
    private void spikesNonwrites(){var d=dispatch;var actual=placement.capture();
        if(d.spikeBase==null||!d.spikeBase.status().equals(actual.status())||!same(d.spikeBase.savedFlags(),actual.savedFlags()))throw new IllegalStateException("Spikes changed placement status/bank before native tail");
        for(var image:d.spikePool.slots())if(image.identity().wholeIndex()!=d.whole&&!image.equals(placement.pool().state(image.identity().wholeIndex())))throw new IllegalStateException("Spikes changed unrelated full64");}
    private void spikesSolid(CdSpikes.PrefixSolidCall c){requireKind(Kind.SPIKES);spikesNonwrites();var d=dispatch;
        if(d.spikeSolids!=0||d.spikeStopped||d.spikeY||d.drawn)throw new IllegalStateException("Spikes Solid order");
        claim(c,()->spikes.isCurrentPrefixSolidCall(c));poolCut(c.actualCut());source(c.actualCut().slots().get(d.whole-32));
        commitSolid(c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());d.spikeSolids=1;d.spikePool=placement.pool().capture();
        if(c.outcome() instanceof CdSolidObject.Completed done)d.spikeSolid=done.completedResult();else d.spikeStopped=true;
    }
    private boolean spikesHurtReached(){var d=dispatch;if(d.spikeSolid==null||d.spikeStopped||d.spikeVertical)return false;var p=primary().actor();var c=spikesPlayerGlobals();
        return d.spikeSolid.d0().orElseThrow()!=0&&(placement.pool().state(d.whole).flags()&8)!=0&&c.timeWarp()==0&&c.invincible()==0&&p.motion().routine()<4&&p.hurt()==0;}
    private void spikesY(CdSpikes.YRewindCall c){requireKind(Kind.SPIKES);spikesNonwrites();var d=dispatch;
        if(d.spikeY||d.spikeHurt||d.drawn||!spikesHurtReached())throw new IllegalStateException("Spikes Y outside native Hurt gate");
        claim(c,()->spikes.isCurrentYRewindCall(c));requireIdentity(c.sourceIdentity());if(!c.expectedActor().equals(primary().actor()))throw new IllegalArgumentException("Stale Spikes Y actor");
        var prepared=frame.prepareObjectPreHurtYRewind(c.expectedActor(),c.finalYBits());var cut=read();
        publish(cut,cut,null,()->frame.validatePreparedObjectSolidPublication(prepared),()->frame.applyPreparedObjectSolidPublication(prepared));d.spikeY=true;
    }
    private void spikesHurt(CdSpikes.HurtCall c){requireKind(Kind.SPIKES);spikesNonwrites();var d=dispatch;
        if(!d.spikeY||d.spikeHurt||d.drawn||!spikesHurtReached())throw new IllegalStateException("Spikes Hurt without its native Y prefix");
        claim(c,()->spikes.isCurrentHurtCall(c));poolCut(c.callCut());source(c.sourceA2());
        var expected=c.expectedPlayer();var world=c.expectedWorld();var result=c.result();
        if(!c.expectedActor().equals(primary().actor())||!expected.equals(spikesPlayerGlobals())||!world.equals(spikesWorldGlobals())||!result.world().equals(world))throw new IllegalArgumentException("Stale/unowned Spikes Hurt projection");
        boolean find=expected.shield()==0&&world.rings()!=0;boolean kill=expected.shield()==0&&world.rings()==0&&expected.debugCheat()==0;
        var exit=kill?(expected.debugMode()==0?CdPlayerTouchResponse.Exit.KILL_PLAYER:CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE):CdPlayerTouchResponse.Exit.HURT;
        int shield=exit==CdPlayerTouchResponse.Exit.HURT?expected.shield()&~1:expected.shield();int combine=exit==CdPlayerTouchResponse.Exit.HURT&&(expected.shield()&1)==0?0:expected.combineRing();var next=result.player();
        if(result.exit()!=exit||next.timeWarp()!=expected.timeWarp()||next.shield()!=shield||next.combineRing()!=combine||next.debugCheat()!=expected.debugCheat()||next.debugMode()!=expected.debugMode()||next.miniSonic()!=expected.miniSonic())throw new IllegalArgumentException("Spikes Hurt changes unowned contact values");
        var free=OptionalInt.empty();if(find)for(var image:c.callCut().slots())if(image.id()==0){free=OptionalInt.of(image.identity().wholeIndex());break;}
        var calls=new ArrayList<CdPlayerTouchResponse.Call>();if(find)calls.add(new CdPlayerTouchResponse.FindObjSlot(free));
        if(exit!=CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE){calls.add(new CdPlayerTouchResponse.ResetOnFloor());if((c.expectedActor().motion().flags()&4)!=0)calls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP));if(exit==CdPlayerTouchResponse.Exit.KILL_PLAYER)calls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT));}
        if(!result.calls().equals(calls)||result.changedSlots().size()!=(free.isPresent()?1:0))throw new IllegalArgumentException("Spikes Hurt calls/allocations differ from reached native branch");
        if(free.isPresent()){var before=c.callCut().slots().get(free.getAsInt()-32);var image=before.moveByte(ID,CdLostRing.OBJECT_ID).moveWord(X,c.expectedActor().motion().centreX16_16()>>>16).moveWord(Y,c.expectedActor().motion().centreY16_16()>>>16);
            if(free.getAsInt()==d.whole||!result.changedSlots().getFirst().equals(image))throw new IllegalArgumentException("Spikes Hurt LostRing must be the genuine first-free ID/X/Y prefix");}
        var output=CdObjectPool.overlay(c.callCut(),result.changedSlots());var prepared=frame.prepareObjectHurtResult(c.expectedActor(),expected,result);var cut=read();var g=cut.globals();
        var globals=new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),new CdPlayerBody.ContactGlobals(expected.timeWarp(),shield,combine),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
        var sounds=new ArrayList<>(cut.sounds());for(var call:result.calls())if(call instanceof CdPlayerTouchResponse.PlayFm fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm.sound())));
        publish(cut,withSounds(withGlobals(cut,globals),sounds),output,()->frame.validatePreparedObjectSolidPublication(prepared),()->frame.applyPreparedObjectSolidPublication(prepared));d.spikeHurt=true;d.spikePool=placement.pool().capture();
    }
    /** Native CheckObjDespawn/DespawnObject; observation only, never a second store execution. */
    private boolean outsideOrWarp(CdObjectState source){var c=read();int period=frame.environment().movement().gates().timeZone();
        return (source.spriteFlags()&128)==0&&(((source.centreX()&0xFF80)-((c.cameraXWord()-128)&0xFF80))&65535)>0x280||(period&128)!=0;}
    private int savedTailIndex(CdObjectState source){var g=frame.environment().movement().gates();int period=g.timeZone()&127;
        if((g.timeZone()&128)!=0){int adjusted=(period-(byte)g.timeWarpDirection())&65535;period=(short)adjusted<0?0:adjusted<3?adjusted:2;}
        return 2+(short)((3*source.savedFlagsId()+period)&65535);}
    private void verifySpikesTail(boolean completed){var d=dispatch;if(d.spikeDrawCut==null)throw new IllegalStateException("Spikes omitted native Draw");var before=d.spikeDrawCut;var actual=placement.capture();var source=before.pool().slots().get(d.whole-32);boolean delete=outsideOrWarp(source);
        if(!before.status().equals(actual.status()))throw new IllegalStateException("Spikes tail changed placement status");for(var image:before.pool().slots())if(image.identity().wholeIndex()!=d.whole&&!image.equals(placement.pool().state(image.identity().wholeIndex())))throw new IllegalStateException("Spikes tail changed unrelated image");
        byte[] saved=before.savedFlags().copy();boolean bankWritable=delete&&source.savedFlagsId()!=0;int at=bankWritable?savedTailIndex(source):-1;boolean valid=at>=2&&at<saved.length;if(bankWritable&&valid)saved[at]&=127;
        var live=placement.pool().state(d.whole);boolean deleted=Arrays.equals(live.copyBytes(),new byte[64]);boolean bankFinal=Arrays.equals(saved,actual.savedFlags().copy());boolean bankBefore=same(before.savedFlags(),actual.savedFlags());
        if(completed){if(delete&&bankWritable&&!valid||!bankFinal||delete!=deleted||!delete&&!live.equals(source))throw new IllegalStateException("Spikes native tail output mismatch");}
        else if((!bankBefore&&!bankFinal)||!live.equals(source)&&!deleted||deleted&&(!delete||!bankFinal))throw new IllegalStateException("Spikes failed prefix exceeds native tail stores");
    }
    private void logStart(Object c){requireKind(Kind.LOG_SHADOW);var d=dispatch;if(d.operation!=null||!logShadow.isCurrentCall(c))throw new IllegalStateException("Not an original next Log operation");
        d.operation=c;d.used=false;d.observedCut=placement.capture();d.observedGlobals=read();d.actor=frame.captureActor();d.shared=frame.captureShared();}
    private void logBeforePublication(CdLogShadow.Publication c){logStart(c);var d=dispatch;placementCut(c.cut());source(c.source());if(c.ordinal()!=d.ordinal+1)throw new IllegalStateException("Log publication ordinal");
        var images=new ArrayList<byte[]>();for(var image:c.cut().pool().slots())images.add(image.copyBytes());byte[] saved=c.cut().savedFlags().copy();var source=c.source();CdObjectState expected=null;
        if(!d.drawn&&d.logInitialRoutine==0&&d.logInitStores<3){
            expected=switch(d.logInitStores){case 0->source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(PRIORITY,6).moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_LOG_SHADOW.range().nativeAddress()).moveByte(MAP_FRAME,source.subtype()).moveByte(WIDTH,16).moveByte(Y_RADIUS,16);
                case 1->source.moveWord(TILE,logBaseWord());case 2->source.moveWord(TILE,source.word(TILE)|0x4000);default->throw new IllegalStateException();};
            if(c.savedIndex()!=-1||!expected.equals(c.output()))throw new IllegalArgumentException("Log changed a native Init store/nonwrite");d.logInitStores++;
        }else{if(!d.drawn||!outsideOrWarp(source))throw new IllegalStateException("Log tail before its native despawn gate");
            if(c.savedIndex()>=0){int at=savedTailIndex(source);if(d.logSavedCleared||source.savedFlagsId()==0||c.savedIndex()!=at||at<2||at>=saved.length||!source.equals(c.output()))throw new IllegalArgumentException("Log saved-bank operation mismatch");saved[at]&=127;expected=source;d.logSavedCleared=true;}
            else{if(c.output()!=null||d.logDeleted||source.savedFlagsId()!=0&&!d.logSavedCleared)throw new IllegalArgumentException("Log Delete before native saved clear");d.logDeleted=true;}}
        if(expected!=null){CdObjectPool.overlay(c.cut().pool(),List.of(expected));images.set(source.identity().dynamicIndex(),expected.copyBytes());}else images.set(source.identity().dynamicIndex(),new byte[64]);d.expectedImages=List.copyOf(images);d.expectedSaved=saved;
    }
    private int logBaseWord(){var g=frame.environment().movement().gates();int index=g.timeZone()&127;if(index==2)index=(index&0xFF00)|((index+read().goodFutureByte())&255);index=(index+index)&65535;index=(index&0xFF00)|((index+(g.zoneAct()&255))&255);int at=(short)((index+index)&65535);
        var table=terrain.objects().view(CdR12bObjectAssets.View.LOG_SHADOW_BASE_TILES);if(at<0||(at&1)!=0||at>14||at>table.length()-2)throw new IllegalArgumentException("Unowned Log selected base");return table.unsignedWord(at);}
    private void logBeforeDraw(CdLogShadow.DrawCall c){logStart(c);var d=dispatch;placementCut(c.cut());source(c.source());if(d.drawn||d.logInitialRoutine==0&&d.logInitStores!=3)throw new IllegalStateException("Log Draw before completed Init");
        var images=new ArrayList<byte[]>();for(var image:c.cut().pool().slots())images.add(image.copyBytes());images.set(d.whole-32,c.source().moveByte(SPRITE_FLAGS,c.source().spriteFlags()&127).copyBytes());d.expectedImages=List.copyOf(images);d.expectedSaved=c.cut().savedFlags().copy();}
    private void logAfter(Object c,boolean publication){requireKind(Kind.LOG_SHADOW);var d=dispatch;if(d.operation!=c||!logShadow.isCurrentCall(c)||!publication&&!d.used)throw new IllegalStateException("Log lacks its original completed operation");
        requireFrame(d.actor,d.shared);requireCut(d.observedGlobals);var actual=placement.capture();if(!d.observedCut.status().equals(actual.status())||!Arrays.equals(d.expectedSaved,actual.savedFlags().copy()))throw new IllegalStateException("Log changed bank/status nonwrites");
        for(int n=0;n<96;n++)if(!Arrays.equals(d.expectedImages.get(n),actual.pool().slots().get(n).copyBytes()))throw new IllegalStateException("Log native full96 output mismatch");
        if(publication)d.ordinal++;d.operation=null;d.observedCut=null;d.observedGlobals=null;d.actor=null;d.shared=null;d.expectedImages=null;d.expectedSaved=null;checkpoint();
    }
    /** Transient original-operation scope only; no gameplay image or capture key is retained. */
    private static final class DecorScope {
        final Object call;final CdObjectPlacementRuntime.Snapshot before;final Cut cut;
        final CdPlayerFrameRuntime.ActorState actor;final CdPlayerFrameRuntime.SharedState shared;
        final boolean ordinal,publication;final List<byte[]> images;final byte[] saved;
        CdPlayerFrameRuntime.ObjectSolidPublication primary;boolean used,returned;
        DecorScope(Object call,CdObjectPlacementRuntime.Snapshot before,Cut cut,
                   CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared,boolean ordinal,boolean publication){
            this.call=call;this.before=before;this.cut=cut;this.actor=actor;this.shared=shared;this.ordinal=ordinal;this.publication=publication;
            images=new ArrayList<>();for(var state:before.pool().slots())images.add(state.copyBytes());saved=before.savedFlags().copy();
        }
    }
    private boolean actualDecor(Object call){return dispatch!=null&&switch(dispatch.kind){
        case SCENERY->scenery.isCurrentCall(call);case SPRINGBOARD->springboard.isCurrentCall(call);default->false;};}
    private DecorScope decorBegin(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot before,CdObjectState source,boolean counted,boolean publication){
        if(dispatch==null||dispatch.decor!=null||port||queuePort||plcPort||!actualDecor(call))
            throw new IllegalStateException("No original next Scenery/Springboard operation");
        requireKind(dispatch.kind);placementCut(before);source(source);
        if(terrain.objects().identity()!=terrain.identity()||terrain.objects().placements()!=placement.placements())
            throw new IllegalStateException("Foreign retained Scenery/Springboard source");
        if(counted&&ordinal!=dispatch.ordinal+1)throw new IllegalStateException("Scenery/Springboard native ordinal mismatch");
        var scope=new DecorScope(call,before,read(),frame.captureActor(),frame.captureShared(),counted,publication);dispatch.decor=scope;return scope;
    }
    private DecorScope decorCurrent(Object call){var scope=dispatch==null?null:dispatch.decor;
        if(scope==null||scope.call!=call||!actualDecor(call))throw new IllegalStateException("Escaped/cloned Scenery/Springboard receipt");return scope;}
    private void decorPublication(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot before,CdObjectState source,CdObjectState output,int savedIndex){
        var scope=decorBegin(call,ordinal,before,source,true,true);var d=dispatch;
        if(!d.consumed.add(call))throw new IllegalStateException("Repeated native Scenery/Springboard publication");
        if(savedIndex>=0){int at=savedTailIndex(source);
            if(!outsideOrWarp(source)||d.decorSavedCleared||source.savedFlagsId()==0||savedIndex!=at||at<2||at>=scope.saved.length||output!=source)
                throw new IllegalArgumentException("Scenery/Springboard saved clear exceeds its native tail");
            scope.saved[at]&=127;d.decorSavedCleared=true;
        }else if(output==null){
            if(!outsideOrWarp(source)||d.decorDeleted||source.savedFlagsId()!=0&&!d.decorSavedCleared)
                throw new IllegalArgumentException("Scenery/Springboard Delete before native tail");
            scope.images.set(d.whole-32,new byte[64]);d.decorDeleted=true;
        }else{
            requireIdentity(output.identity());CdObjectPool.overlay(before.pool(),List.of(output));
            if(d.kind==Kind.SCENERY){
                if(d.decorInitialRoutine!=0||d.drawn||d.sceneryInitStores>=3)throw new IllegalStateException("Scenery publication outside Init");
                var expected=switch(d.sceneryInitStores){
                    case 0->source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4)
                            .moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_SCENERY.range().nativeAddress())
                            .moveByte(MAP_FRAME,source.subtype()).moveByte(WIDTH,16).moveByte(Y_RADIUS,24);
                    case 1->source.moveWord(TILE,sceneryBaseWord());case 2->source.moveWord(TILE,source.word(TILE)|0x4000);
                    default->throw new AssertionError();};
                if(!expected.equals(output))throw new IllegalArgumentException("Scenery Init changed a native store/nonwrite");d.sceneryInitStores++;
            }
            scope.images.set(d.whole-32,output.copyBytes());
        }
    }
    private int sceneryBaseWord(){var g=frame.environment().movement().gates();int index=g.timeZone()&127;
        if(index==2)index=(1+read().goodFutureByte())&255;
        index=(index+index)&65535;index=(index&0xFF00)|((index+(g.zoneAct()&255))&255);int at=(short)((index+index)&65535);
        var table=terrain.objects().view(CdR12bObjectAssets.View.SCENERY_BASE_TILES);
        if(at<0||(at&1)!=0||at>10||at>table.length()-2)throw new IllegalArgumentException("Unowned Scenery selected base");return table.unsignedWord(at);}
    private void decorDrawBefore(Object call,CdObjectPlacementRuntime.Snapshot before,CdObjectState source){
        var scope=decorBegin(call,0,before,source,false,false);var d=dispatch;
        if(d.drawn||d.kind==Kind.SCENERY&&d.decorInitialRoutine==0&&d.sceneryInitStores!=3
                ||d.kind==Kind.SPRINGBOARD&&d.decorInitialRoutine==0)throw new IllegalStateException("Scenery/Springboard Draw at wrong native point");
        scope.images.set(d.whole-32,source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127).copyBytes());
    }
    private void decorUse(Object call){var scope=decorCurrent(call);requireKind(dispatch.kind);requireFrame(scope.actor,scope.shared);requireCut(scope.cut);requirePlacement(scope.before);
        if(scope.used||scope.publication||!dispatch.consumed.add(call))throw new IllegalStateException("Repeated native Scenery/Springboard callback");scope.used=true;}
    private void decorDraw(Object call,CdObjectState source){decorUse(call);drawDynamic(dispatch.kind,placement.pool(),source.identity(),()->actualDecor(call));}
    private void springboardPrimary(CdSpringboard.PrimaryCall call){var scope=decorCurrent(call);
        if(dispatch.kind!=Kind.SPRINGBOARD||scope.primary==null)throw new IllegalStateException("No actual prepared Springboard primary store");
        decorUse(call);var prepared=scope.primary;
        publish(scope.cut,scope.cut,null,()->{decorCurrent(call);frame.validatePreparedObjectSolidPublication(prepared);},
                ()->frame.applyPreparedObjectSolidPublication(prepared));
    }
    private boolean decorPlacementMatches(DecorScope scope,boolean output){var live=placement.capture();
        if(!scope.before.status().equals(live.status())||!Arrays.equals(output?scope.saved:scope.before.savedFlags().copy(),live.savedFlags().copy()))return false;
        for(int n=0;n<96;n++)if(!Arrays.equals(output?scope.images.get(n):scope.before.pool().slots().get(n).copyBytes(),live.pool().slots().get(n).copyBytes()))return false;return true;
    }
    private void decorVerify(DecorScope scope,boolean completed){guard();requireCut(scope.cut);
        if(!decorPlacementMatches(scope,true)&&(completed||!decorPlacementMatches(scope,false)))
            throw new IllegalStateException("Scenery/Springboard changed full96/bank/status nonwrites");
        var actor=scope.actor;if(scope.primary!=null){var prepared=scope.primary;
            var after=new CdPlayerFrameRuntime.ActorState(actor.owner(),prepared.finalState(),actor.display());
            if(frame.state()==prepared.finalState()&&frame.environment()==prepared.finalEnvironment())actor=after;
            else if(completed||frame.state()!=prepared.expectedState()||frame.environment()!=prepared.expectedEnvironment())
                throw new IllegalStateException("Springboard did not retain the original prepared Frame references");
        }
        requireFrame(actor,scope.shared);
    }
    private void decorAfter(Object call){var scope=decorCurrent(call);scope.returned=true;
        if(!scope.publication&&!scope.used)throw new IllegalStateException("Scenery/Springboard omitted original callback consumption");
        decorVerify(scope,true);if(scope.ordinal)dispatch.ordinal++;dispatch.decor=null;checkpoint();}
    private void decorFailed(Throwable original){var scope=dispatch.decor;if(scope!=null)try{decorVerify(scope,scope.returned);}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}}
    private void decorResult(CdObjectState state,boolean deleted,boolean drawn){requireKind(dispatch.kind);
        if(dispatch.decor!=null||dispatch.drawn!=drawn||deleted!=dispatch.decorDeleted||!state.equals(placement.pool().state(dispatch.whole))
                ||dispatch.kind==Kind.SCENERY&&dispatch.decorInitialRoutine==0&&dispatch.sceneryInitStores!=3)
            throw new IllegalStateException("Scenery/Springboard omitted original native completion");}
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
        case MONITOR_ITEM->monitorItem.isCurrentCall(c);case MOSQUI->mosqui.isCurrentCall(c);default->false;};}
    private EffectScope effectTop(Object c){var d=dispatch;if(d==null||d.effects.isEmpty()||d.effects.peek().call!=c||!actualEffect(c))throw new IllegalStateException("No original current effect operation");return d.effects.peek();}
    private EffectScope effectBegin(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,boolean publication){
        if(dispatch==null||port||queuePort||plcPort||!actualEffect(call))throw new IllegalStateException("No private current effect dispatch");
        requireKind(dispatch.kind);placementCut(cut);source(source);var d=dispatch;
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
        if(savedIndex>=0){if(output!=source||savedIndex>=scope.saved.length)throw new IllegalArgumentException("Bad Mosqui BCLR publication");scope.saved[savedIndex]&=127;}
        else if(output==null){ /* Delete is checked as all64 zero in effectVerify; no synthetic image or write. */ }
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
        case CdMosqui.Publication c->c.output()==null&&c.savedIndex()<0;case CdMonitorItem.DeleteCall ignored->true;default->false;};}
    private int effectSourceWhole(EffectScope s){return switch(s.call){
        case CdExplosion.Publication c->c.source().identity().wholeIndex();case CdPoints.Publication c->c.source().identity().wholeIndex();
        case CdMosqui.Publication c->c.source().identity().wholeIndex();case CdMonitorItem.DeleteCall c->c.source().identity().wholeIndex();
        default->dispatch.whole;};}
    private void effectVerify(EffectScope s){
        guard();requireFrame(s.actor,s.shared);requireCut(s.cut);var live=placement.capture();
        if(!s.before.status().equals(live.status())||!Arrays.equals(s.saved,live.savedFlags().copy()))throw new IllegalStateException("Effect changed placement status or saved nonwrites");
        int deleted=effectDelete(s)?effectSourceWhole(s):-1;
        for(int n=0;n<96;n++){byte[] expected=deleted==n+32?new byte[64]:s.expected.get(n).copyBytes();
            if(!Arrays.equals(expected,live.pool().slots().get(n).copyBytes()))throw new IllegalStateException("Effect changed an unowned full96 byte at whole "+(n+32));}
    }
    private void effectPrepared(Object call){var s=effectTop(call);if(s.prepared||s.after)throw new IllegalStateException("Repeated effect preparation");
        requireFrame(s.actor,s.shared);requireCut(s.cut);requirePlacement(s.before);s.prepared=true;}
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
    private int vIntWord(){var value=frame.vIntClock().absoluteValue();if(value.isEmpty())throw stop(Boundary.UNKNOWN_VINT,dispatch.whole,0);return(int)value.getAsLong()&65535;}
    private void lostInvocation(){requireKind(Kind.LOST_RING);if(dispatch.lostInputs==null||dispatch.lostPorts==null||!lostRing.isCurrentInvocation(dispatch.whole,dispatch.lostInputs,dispatch.lostPorts))throw new IllegalStateException("No original LostRing invocation");}
    private void lostCall(Object operation,CdLostRing.SourceCut cut){lostInvocation();if(cut!=lostRing.currentSource(operation)||!dispatch.consumed.add(cut))throw new IllegalStateException("Cloned/repeated LostRing source receipt");placementCut(cut.placement());source(cut.source());}
    private CdGlobalObjectAnimation.Extras spillOwner(){var value=read().globals().animations();if(value.isEmpty())throw stop(Boundary.SPILL_GLOBALS_OWNER,dispatch.whole,0);return value.orElseThrow();}
    private CdLostRing.Ports lostPorts(){return new CdLostRing.Ports(new CdRing.GlobalsPort(){
        public CdRingGlobals read(){lostInvocation();return SonicCdAct2PastObjectPass.this.read().globals().ring();}
        public void write(CdRingGlobals value){lostCall(value,lostRing.currentSource(value));var cut=SonicCdAct2PastObjectPass.this.read();publish(cut,withRing(cut,Objects.requireNonNull(value)),null,()->{},()->{});}
    },new CdLostRing.SpillGlobalsPort(){
        public CdRingSpillGlobals read(){lostInvocation();return spillOwner().spill();}
        public void write(CdRingSpillGlobals value){lostCall(value,lostRing.currentSource(value));var old=spillOwner();var cut=SonicCdAct2PastObjectPass.this.read();var g=cut.globals();
            var next=new CdGlobalObjectAnimation.Extras(old.logSpikeTimerByte(),old.logSpikeFrameByte(),old.unusedTimerByte(),old.unusedFrameByte(),Objects.requireNonNull(value));
            var globals=new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),Optional.of(next),g.paletteSteps7(),g.paletteTimers7());
            publish(cut,withGlobals(cut,globals),null,()->{},()->{});}
    });}
    private boolean observedKind(){return dispatch.kind==Kind.SWING||dispatch.kind==Kind.ROBOT||dispatch.kind==Kind.PROJECTOR||dispatch.kind==Kind.ANIMAL;}
    private boolean actualObserved(Object call){return switch(dispatch.kind){case SWING->swing.isCurrentCall(call);case ROBOT->robot.isCurrentCall(call);case PROJECTOR->projector.isCurrentCall(call);case ANIMAL->animal.isCurrentCall(call);default->false;};}
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
        if(dispatch==null||!observedKind()||port||queuePort||plcPort||dispatch.operation!=null||!actualObserved(call)||ordinal!=dispatch.ordinal+1)throw new IllegalStateException("No original next native operation");
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

    // BEGIN R12B Disc/Door World publication. Java UNRUN; N1ecc5f3, PP Spinning Disc/Main15-223,
    // PP Tunnel Door/Main7-94, Spin Tunnel/Main (Door)7-92. No returned effect executes work.
    private void routeObjects(){var objects=terrain.objects();
        if(objects.identity()!=terrain.identity()||objects.placements()!=placement.placements())
            throw new IllegalStateException("Foreign retained R12B route metadata");
        extent(objects.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS),72);
    }
    private static class BoundInvocation {
        final Object body;final int whole;final CdObjectPlacementRuntime.Snapshot before;int solids,fms;CdObjectState expectedSource;
        BoundInvocation(Object body,int whole,CdObjectPlacementRuntime.Snapshot before){this.body=body;this.whole=whole;this.before=before;expectedSource=before.pool().slots().get(whole-32);}
    }
    private enum DiscStep { SOLID,ACQUIRE,ORBIT,INPUT,JUMP_CLEAR,JUMP_VELOCITY,FM,HITBOX,DONE,STOPPED }
    private static final class DiscInvocation extends BoundInvocation {
        DiscStep next=DiscStep.SOLID;CdObjectPool.Snapshot pool;
        DiscInvocation(CdSpinningDisc body,int whole,CdObjectPlacementRuntime.Snapshot before){super(body,whole,before);pool=before.pool();}
    }
    private void routeNonwrites(BoundInvocation invocation){routeObjects();var actual=placement.capture();
        if(!invocation.before.status().equals(actual.status())||!same(invocation.before.savedFlags(),actual.savedFlags()))
            throw new IllegalStateException("Route callback changed status/saved-bank nonwrites");
    }
    private void runDisc(int whole){requireKind(Kind.DISC);if(discInvocation!=null)throw new IllegalStateException("Nested Disc dispatch");
        var g=frame.environment().movement().gates();var cut=read();discInvocation=new DiscInvocation(spinningDisc,whole,placement.capture());
        try{var result=spinningDisc.run(whole,new CdSpinningDisc.Globals(cut.globals().ring().timeStopTimer(),cut.cameraXWord(),g.timeZone(),g.timeWarpDirection()));
            if(!dispatch.drawn||!result.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Incomplete Disc native return");}
        finally{discInvocation=null;}
    }
    private CdObjectState discCurrent(){requireKind(Kind.DISC);var invocation=discInvocation;
        if(invocation==null||invocation.body!=spinningDisc||invocation.whole!=dispatch.whole||dispatch.drawn||plcPort)
            throw new IllegalStateException("No actual cached Disc body/whole invocation");
        routeNonwrites(invocation);var state=placement.pool().state(dispatch.whole);
        if(state.id()!=CdSpinningDisc.OBJECT_ID||state.routine()!=2||state.signedByte(SPRITE_FLAGS)>=0)
            throw new IllegalStateException("Disc callback outside visible native Main");
        poolCut(invocation.pool);return state;
    }
    private CdSpinningDisc.PlayerCut discRead(){discCurrent();return new CdSpinningDisc.PlayerCut(frame.state(),frame.environment());}
    private void discCut(CdObjectPool.Snapshot pool,CdObjectState source,CdSpinningDisc.PlayerCut pair){
        var actual=discCurrent();poolCut(pool);source(source);
        if(!actual.equals(source)||pair.state()!=frame.state()||pair.environment()!=frame.environment())throw new IllegalArgumentException("Stale full Disc source/pair");
    }
    private void discSolid(CdSpinningDisc.SolidCall call){discCut(call.actualCut(),call.expectedSource(),call.expectedPlayer());
        if(discInvocation.next!=DiscStep.SOLID||!dispatch.consumed.add(call))throw new IllegalStateException("Disc plain Solid order");
        CdObjectState object;List<CdObjectState> others;DiscStep next;
        if(call.outcome() instanceof CdSolidObject.Completed done){var r=done.completedResult();
            if(r.entry()!=CdSolidObject.Entry.SOLID_OBJECT||r.d0().isEmpty())throw new IllegalArgumentException("Not genuine plain Solid return");
            object=r.object();others=r.otherSlots();next=r.d0().orElseThrow()==0?DiscStep.DONE:DiscStep.ACQUIRE;
        }else{var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();if(prefix.entry()!=CdSolidObject.Entry.SOLID_OBJECT)throw new IllegalArgumentException("Not genuine plain Solid prefix");
            object=prefix.object();others=prefix.otherSlots();next=DiscStep.STOPPED;}
        var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var pool=CdObjectPool.overlay(call.actualCut(),images);
        commitSolid(call.actualCut(),primary(),solidGlobals(),call.outcome());discInvocation.pool=pool;discInvocation.next=next;
    }
    private DiscStep discNext(CdSpinningDisc.PlayerCall call){
        var fields=new ArrayList<CdSpinningDisc.DiscPlayerField>();var p=call.expectedPlayer();var s=p.state();var m=s.motion();DiscStep next;
        switch(discInvocation.next){
            case ACQUIRE->{fields.add(CdSpinningDisc.DiscPlayerField.CONTROL_ACQUIRE);
                if((m.playerControl()&1)==0){fields.add(CdSpinningDisc.DiscPlayerField.ANIMATION_ACQUIRE);fields.add(CdSpinningDisc.DiscPlayerField.ROTATION_ZERO);
                    if((m.centreX16_16()>>>16)<call.expectedSource().centreX())fields.add(CdSpinningDisc.DiscPlayerField.ROTATION_LEFT);fields.add(CdSpinningDisc.DiscPlayerField.DISTANCE_ACQUIRE);}
                if(m.routine()<6){fields.add(CdSpinningDisc.DiscPlayerField.ROTATION_ADVANCE);next=DiscStep.ORBIT;}else next=DiscStep.DONE;}
            case ORBIT->{fields.addAll(List.of(CdSpinningDisc.DiscPlayerField.ORBIT_X_BASE,CdSpinningDisc.DiscPlayerField.ORBIT_X_OFFSET));next=DiscStep.INPUT;}
            case INPUT->{fields.add(CdSpinningDisc.DiscPlayerField.SCRIPT_INDEX);if((s.rotationAngle()&0x3F)==0)fields.add(CdSpinningDisc.DiscPlayerField.DISTANCE_CARDINAL);
                fields.add(CdSpinningDisc.DiscPlayerField.LOGICAL_CONTROLS);boolean left=(m.centreX16_16()>>>16)<call.expectedSource().centreX();int held=p.environment().controls().rawHeld();
                if((held&(left?4:8))!=0)fields.add(CdSpinningDisc.DiscPlayerField.DISTANCE_GROW);
                else if((held&(left?8:4))!=0){int index=fields.size();fields.add(CdSpinningDisc.DiscPlayerField.DISTANCE_SHRINK);
                    if(call.result().stores().size()<=index)throw new IllegalArgumentException("Missing Disc native shrink");
                    if(call.result().stores().get(index).expectedRaw()==0)fields.add(CdSpinningDisc.DiscPlayerField.DISTANCE_CLAMP);}
                next=DiscStep.JUMP_CLEAR;}
            case JUMP_CLEAR->{if((p.environment().controls().logicalTap()&0x70)==0)throw new IllegalStateException("Disc jump not reached");fields.add(CdSpinningDisc.DiscPlayerField.CONTROL_CLEAR);next=DiscStep.JUMP_VELOCITY;}
            case JUMP_VELOCITY->{fields.addAll(List.of(CdSpinningDisc.DiscPlayerField.JUMP_X_VELOCITY,CdSpinningDisc.DiscPlayerField.JUMP_Y_VELOCITY,CdSpinningDisc.DiscPlayerField.FLAGS_AIR,
                    CdSpinningDisc.DiscPlayerField.FLAGS_PUSH_CLEAR,CdSpinningDisc.DiscPlayerField.JUMP_SET,CdSpinningDisc.DiscPlayerField.STICK_CLEAR));next=DiscStep.FM;}
            case HITBOX->{boolean mini=p.environment().movement().gates().miniSonic()!=0;
                fields.add(mini?CdSpinningDisc.DiscPlayerField.JUMP_Y_RADIUS_MINI:CdSpinningDisc.DiscPlayerField.JUMP_Y_RADIUS_NORMAL);fields.add(mini?CdSpinningDisc.DiscPlayerField.JUMP_X_RADIUS_MINI:CdSpinningDisc.DiscPlayerField.JUMP_X_RADIUS_NORMAL);
                if((m.flags()&4)!=0)fields.add(CdSpinningDisc.DiscPlayerField.FLAGS_ROLL_JUMP);
                else{fields.add(mini?CdSpinningDisc.DiscPlayerField.ROLL_Y_RADIUS_MINI:CdSpinningDisc.DiscPlayerField.ROLL_Y_RADIUS_NORMAL);fields.add(mini?CdSpinningDisc.DiscPlayerField.ROLL_X_RADIUS_MINI:CdSpinningDisc.DiscPlayerField.ROLL_X_RADIUS_NORMAL);
                    if(!mini)fields.add(CdSpinningDisc.DiscPlayerField.ROLL_Y_ADVANCE);fields.add(CdSpinningDisc.DiscPlayerField.FLAGS_ROLL);fields.add(CdSpinningDisc.DiscPlayerField.ANIMATION_ROLL);}next=DiscStep.DONE;}
            default->throw new IllegalStateException("Repeated/out-of-order Disc native player site");
        }
        if(call.result().stores().size()!=fields.size())throw new IllegalArgumentException("Disc native store count");
        for(int n=0;n<fields.size();n++)if(call.result().stores().get(n).field()!=fields.get(n))throw new IllegalArgumentException("Disc native store order at "+n);return next;
    }
    private void discPlayer(CdSpinningDisc.PlayerCall call){discCut(call.actualCut(),call.expectedSource(),call.expectedPlayer());
        if(!dispatch.consumed.add(call))throw new IllegalStateException("Repeated Disc direct result");
        frame.validateObjectDiscPlayerResult(call.expectedPlayer().state(),call.expectedPlayer().environment(),call.result());var next=discNext(call);var cut=read();var before=placement.capture();
        // PlayerResult already contains both immutable aggregates; no result construction after a pool write.
        publish(cut,cut,null,()->frame.validateObjectDiscPlayerResult(call.expectedPlayer().state(),call.expectedPlayer().environment(),call.result()),
                ()->frame.applyObjectDiscPlayerResult(call.expectedPlayer().state(),call.expectedPlayer().environment(),call.result()));
        requirePlacement(before);discInvocation.next=next;
    }
    private void discFm(CdSpinningDisc.FmCall call){discCut(call.actualCut(),call.expectedSource(),call.expectedPlayer());
        if(discInvocation.next!=DiscStep.FM||call.sound()!=CdSpinningDisc.FmSound.FM_A0||!dispatch.consumed.add(call))throw new IllegalStateException("Disc FM outside actual jump prefix");
        var before=placement.capture();var actor=frame.captureActor();var shared=frame.captureShared();sound(new SonicCdGameModule.PastDiscFm(call.sound()));
        requirePlacement(before);requireFrame(actor,shared);discInvocation.next=DiscStep.HITBOX;
    }
    private void discDraw(CdObjectPool pool,CdObjectState.Identity identity){requireKind(Kind.DISC);var invocation=discInvocation;
        if(invocation==null||invocation.body!=spinningDisc||invocation.whole!=dispatch.whole||pool!=placement.pool())throw new IllegalStateException("No actual Disc Draw invocation");
        routeNonwrites(invocation);requireIdentity(identity);
        if(invocation.next!=DiscStep.DONE&&invocation.next!=DiscStep.SOLID&&!(invocation.next==DiscStep.JUMP_CLEAR&&(frame.environment().controls().logicalTap()&0x70)==0))
            throw new IllegalStateException("Disc Draw before actual returning Main prefix");
        var expectedSource=invocation.pool.slots().get(dispatch.whole-32);
        if(expectedSource.routine()==0)expectedSource=expectedSource.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4)
                .moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_SPINNING_DISC.range().nativeAddress()).moveWord(TILE,routeTile(6)).moveByte(WIDTH,16).moveByte(Y_RADIUS,8);
        if(invocation.next==DiscStep.SOLID&&(expectedSource.spriteFlags()&128)!=0)throw new IllegalStateException("Visible Disc omitted actual Solid");
        var liveSource=pool.state(dispatch.whole);byte[] expectedBytes=expectedSource.copyBytes(),actualBytes=liveSource.copyBytes();
        if(read().globals().ring().timeStopTimer()==0)for(var field:List.of(ANIMATION,PREVIOUS_ANIMATION,ANIMATION_FRAME,ANIMATION_TIME,MAP_FRAME,SPRITE_FLAGS,FLAGS,ROUTINE,ROUTINE_2))expectedBytes[field.byteOffset()]=actualBytes[field.byteOffset()];
        if(!Arrays.equals(expectedBytes,actualBytes))throw new IllegalStateException("Disc changed a non-native source byte before Draw");
        var before=placement.capture();for(int n=0;n<96;n++)if(n!=dispatch.whole-32&&!before.pool().slots().get(n).equals(invocation.pool.slots().get(n)))throw new IllegalStateException("Disc animation changed another slot");
        var actor=frame.captureActor();var shared=frame.captureShared();var cut=read();drawDynamic(Kind.DISC,pool,identity,()->discInvocation==invocation);
        requireCut(cut);requireFrame(actor,shared);routeNonwrites(invocation);
        var expected=CdObjectPool.overlay(before.pool(),List.of(before.pool().slots().get(dispatch.whole-32).moveByte(SPRITE_FLAGS,before.pool().slots().get(dispatch.whole-32).spriteFlags()&127)));poolCut(expected);
    }
    private static final class DoorScope {
        final Object call;final CdObjectPlacementRuntime.Snapshot before;final Cut cut;final CdPlayerFrameRuntime.ActorState actor;final CdPlayerFrameRuntime.SharedState shared;
        CdObjectPool.Snapshot pool;byte[] saved;Cut next;CdPlayerFrameRuntime.ObjectSolidPublication primary;boolean used,deleted;
        DoorScope(Object call,CdObjectPlacementRuntime.Snapshot before,Cut cut,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared){
            this.call=call;this.before=before;this.cut=cut;this.next=cut;this.actor=actor;this.shared=shared;pool=before.pool();saved=before.savedFlags().copy();}
    }
    private DoorScope doorBegin(Object call,CdObjectPlacementRuntime.Snapshot before,CdObjectState source){requireKind(Kind.DOOR);routeObjects();
        if(doorScope!=null||!tunnelDoor.isCurrentCall(call)||plcPort)throw new IllegalStateException("No original next Door operation");
        placementCut(before);source(source);var scope=new DoorScope(call,before,read(),frame.captureActor(),frame.captureShared());doorScope=scope;return scope;
    }
    private DoorScope doorCurrent(Object call){requireKind(Kind.DOOR);var scope=doorScope;
        if(scope==null||scope.call!=call||!tunnelDoor.isCurrentCall(call))throw new IllegalStateException("Cloned/escaped Door receipt");return scope;
    }
    /** Only the original matching pending Adoption may preflight its own Door Solid publication. */
    private void doorPublicationCurrent(DoorScope scope,Object call){
        if(scope==null||doorScope!=scope||scope.call!=call||!scope.used||phase!=Phase.WALKING
                ||dispatch==null||dispatch.kind!=Kind.DOOR||queuePort||plcPort
                ||!isCurrentAdoption(pending)||pending.applied||pending.expected!=scope.cut
                ||pending.next!=scope.next||pending.poolOutput!=scope.pool||!tunnelDoor.isCurrentCall(call))
            throw new IllegalStateException("No matching private Door adoption preflight");
        guard();if(dispatch.ownerActor!=null){requireFrame(dispatch.ownerActor,dispatch.ownerShared);requireCut(dispatch.ownerCut);}
        requirePlacement(scope.before);requireFrame(scope.actor,scope.shared);requireCut(scope.cut);
    }
    private <T> T doorOperation(Class<T> type){var scope=doorScope;if(scope==null||!type.isInstance(scope.call))throw new IllegalStateException("Wrong native Door callback");doorCurrent(scope.call);return type.cast(scope.call);}
    private void doorUse(Object call){var scope=doorCurrent(call);if(scope.used||!dispatch.consumed.add(call))throw new IllegalStateException("Repeated Door callback");
        requirePlacement(scope.before);requireFrame(scope.actor,scope.shared);requireCut(scope.cut);scope.used=true;
    }
    private CdTunnelDoor.LiveSolid doorLive(){return new CdTunnelDoor.LiveSolid(primary(),gates(),solidGlobals());}
    private void doorCut(CdTunnelDoor.PrefixSolidCut cut){doorCurrent(cut);poolCut(cut.callCut());requireIdentity(cut.objectIdentity());
        if(!cut.expectedPrimary().equals(primary())||!cut.expectedGates().equals(gates())||!cut.expectedGlobals().equals(solidGlobals()))throw new IllegalArgumentException("Stale Door primary/gates/globals");
        if(cut.participant()==CdTunnelDoor.Participant.SECONDARY&&!same(cut.secondary().orElseThrow(),reserved(read(),1)))throw new IllegalArgumentException("Foreign Door actual reserved full64");
    }
    private CdAssetBytes doorSecondary(){var c=doorOperation(CdTunnelDoor.SecondaryReadCall.class);doorUse(c);return reserved(read(),1);}
    private void doorBefore(Object call){
        switch(call){
            case CdTunnelDoor.PrefixSolidCut c->{doorBegin(c,placement.capture(),placement.pool().state(dispatch.whole));}
            case CdTunnelDoor.PrefixSolidCall c->{var scope=doorBegin(c,placement.capture(),placement.pool().state(dispatch.whole));var cut=c.cut();poolCut(cut.callCut());requireIdentity(cut.objectIdentity());
                if(!cut.expectedPrimary().equals(primary())||!cut.expectedGates().equals(gates())||!cut.expectedGlobals().equals(solidGlobals()))throw new IllegalArgumentException("Stale Door commit readback");
                CdObjectState image;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdSolidObject.Player after;CdSolidObject.Globals globals;
                if(c.outcome() instanceof CdSolidObject.Completed done){var r=done.completedResult();image=r.object();others=r.otherSlots();fm=r.fmRequests();after=r.player();globals=r.globals();
                    if(cut.participant()==CdTunnelDoor.Participant.PRIMARY)scope.primary=frame.prepareObjectSolidResult(primary(),solidGlobals(),r);
                    else if(!after.equals(secondary(reserved(scope.cut,1)))||!globals.equals(solidGlobals())||!r.playerWrites().isEmpty()||!r.globalWrites().isEmpty()||!others.isEmpty()||!fm.isEmpty())throw new IllegalArgumentException("Inactive Door secondary changes primary/shared state");
                }else{if(cut.participant()!=CdTunnelDoor.Participant.PRIMARY)throw new IllegalArgumentException("Inactive Door secondary cannot stop");var r=((CdSolidObject.NativeStopped)c.outcome()).prefix();image=r.object();others=r.otherSlots();fm=r.fmRequests();after=r.player();globals=r.globals();scope.primary=frame.prepareObjectSolidPrefix(primary(),solidGlobals(),r);}
                if(cut.secondary().isPresent()&&!same(cut.secondary().orElseThrow(),reserved(scope.cut,1)))throw new IllegalArgumentException("Stale Door secondary full64");
                var images=new ArrayList<CdObjectState>();images.add(image);images.addAll(others);scope.pool=CdObjectPool.overlay(cut.callCut(),images);
                var sounds=new ArrayList<>(scope.cut.sounds());for(var sound:fm)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(sound)));scope.next=withSounds(scope.cut,sounds);}
            case CdTunnelDoor.SecondaryReadCall c->doorBegin(c,c.cut(),c.source());
            case CdTunnelDoor.FmCall c->{var scope=doorBegin(c,c.cut(),c.source());var sounds=new ArrayList<>(scope.cut.sounds());sounds.add(new SonicCdGameModule.PastTunnelDoorFm(c.sound()));scope.next=withSounds(scope.cut,sounds);}
            case CdTunnelDoor.DrawCall c->{var scope=doorBegin(c,c.cut(),c.source());scope.pool=CdObjectPool.overlay(scope.pool,List.of(c.source().moveByte(SPRITE_FLAGS,c.source().spriteFlags()&127)));}
            default->throw new IllegalArgumentException("Unknown original Door operation");
        }
    }
    private CdTunnelDoor.LiveSolid doorSolid(CdTunnelDoor.PrefixSolidCall call){var scope=doorCurrent(call);doorUse(call);var prepared=scope.primary;
        publish(scope.cut,scope.next,scope.pool,()->{doorPublicationCurrent(scope,call);if(prepared!=null)frame.validatePreparedObjectSolidPublication(prepared);},()->{if(prepared!=null)frame.applyPreparedObjectSolidPublication(prepared);});return doorLive();
    }
    private void doorPublication(CdTunnelDoor.Publication call){var scope=doorBegin(call,call.cut(),call.source());
        if(call.ordinal()!=dispatch.ordinal+1||!dispatch.consumed.add(call))throw new IllegalStateException("Door publication ordinal/repetition");scope.used=true;
        switch(call.kind()){
            case SOURCE,CHILD->{Objects.requireNonNull(call.output());scope.pool=CdObjectPool.overlay(scope.pool,List.of(call.output()));
                if(call.kind()==CdTunnelDoor.MutationKind.SOURCE)requireIdentity(call.output().identity());}
            case SAVED->{int at=call.savedIndex();if(!dispatch.drawn||!outsideOrWarp(call.source())||at!=savedTailIndex(call.source())||at<2||at>=scope.saved.length)throw new IllegalArgumentException("Door saved tail address");scope.saved[at]&=127;}
            case DELETE->{if(!dispatch.drawn||!outsideOrWarp(call.source()))throw new IllegalArgumentException("Door delete before tail");scope.deleted=true;}
        }
    }
    private void doorVerify(DoorScope scope,boolean completed){routeObjects();var actual=placement.capture();
        boolean before=actual.status().equals(scope.before.status())&&same(actual.savedFlags(),scope.before.savedFlags())&&actual.pool().slots().equals(scope.before.pool().slots());
        boolean after=actual.status().equals(scope.before.status())&&Arrays.equals(actual.savedFlags().copy(),scope.saved);
        for(int n=0;n<96;n++)after&=scope.deleted&&n==dispatch.whole-32?Arrays.equals(new byte[64],actual.pool().slots().get(n).copyBytes()):actual.pool().slots().get(n).equals(scope.pool.slots().get(n));
        if(!after&&(completed||!before))throw new IllegalStateException("Door full96/bank/status publication mismatch");
        boolean finalPair=scope.primary!=null&&frame.state()==scope.primary.finalState()&&frame.environment()==scope.primary.finalEnvironment();
        var actor=finalPair?new CdPlayerFrameRuntime.ActorState(scope.actor.owner(),scope.primary.finalState(),scope.actor.display()):scope.actor;
        var shared=finalPair?new CdPlayerFrameRuntime.SharedState(scope.shared.owner(),scope.primary.finalEnvironment(),scope.shared.artState(),scope.shared.paused(),scope.shared.vIntClock()):scope.shared;
        requireFrame(actor,shared);if(completed&&scope.primary!=null&&!finalPair)throw new IllegalStateException("Door omitted prebuilt Frame pair");
        if(completed)requireCut(scope.next);else{try{requireCut(scope.next);}catch(RuntimeException failure){requireCut(scope.cut);}}
    }
    private void doorAfter(Object call,Object actualReturn){var scope=doorCurrent(call);if(!scope.used)throw new IllegalStateException("Door omitted private callback receipt");doorVerify(scope,true);
        if(call instanceof CdTunnelDoor.PrefixSolidCut||call instanceof CdTunnelDoor.PrefixSolidCall){if(!doorLive().equals(actualReturn))throw new IllegalStateException("Door returned foreign live readback");}
        if(call instanceof CdTunnelDoor.SecondaryReadCall&&(!(actualReturn instanceof CdAssetBytes raw)||!same(raw,reserved(read(),1))))throw new IllegalStateException("Door returned foreign secondary");
        if(call instanceof CdTunnelDoor.Publication)dispatch.ordinal++;doorScope=null;checkpoint();
    }
    private void doorFailure(Object call,Throwable original){if(doorScope==null)return;try{doorCurrent(call);doorVerify(doorScope,false);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}finally{doorScope=null;}}
    private int routeTile(int selector){var table=terrain.objects().view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);return table.unsignedWord((short)table.unsignedWord(selector*2));}
    private CdObjectState verticalInitial(){var source=verticalInvocation.before.pool().slots().get(dispatch.whole-32);
        if(source.routine()==0)source=source.addByte(ROUTINE,2).moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_TUNNEL_DOOR_VERTICAL.range().nativeAddress()).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(WIDTH,4).moveByte(Y_RADIUS,24).moveWord(TILE,routeTile(12));return source;
    }
    private boolean verticalOpening(CdObjectState source){var motion=frame.state().motion();int oy=source.centreY(),py=motion.centreY16_16()>>>16;
        int dy=(oy-py)&65535;if(oy<py)dy=(-dy)&65535;int ox=source.centreX(),px=motion.centreX16_16()>>>16;return dy<32&&ox>=px&&((ox-px)&65535)<48;
    }
    private CdObjectState verticalBeforeSolid(){var source=verticalInitial();if(verticalOpening(source)){source=source.moveWord(TUNNEL_DOOR_TIMER,0).moveByte(ROUTINE,4);if((source.spriteFlags()&128)!=0)source=source.moveByte(MAP_FRAME,1);}return source;}
    private void verticalDraw(CdObjectPool pool,CdObjectState.Identity identity){var invocation=verticalCurrent();
        if(invocation.before.pool().slots().get(dispatch.whole-32).routine()==4){var expected=verticalInitial();int sum=expected.unsignedByte(TUNNEL_DOOR_TIMER_HIGH)+8;expected=expected.moveByte(TUNNEL_DOOR_TIMER_HIGH,sum);
            if(sum>255)expected=expected.addByte(ROUTINE,-2).moveByte(MAP_FRAME,0);invocation.expectedSource=expected;
        }else if(invocation.solids!=1)throw new IllegalStateException("DoorV Draw omitted actual plain Solid");
        if(!placement.pool().state(dispatch.whole).equals(invocation.expectedSource))throw new IllegalArgumentException("DoorV native source sequence before Draw");
        drawDynamic(Kind.VERTICAL_DOOR,pool,identity,()->verticalDoor.isCurrentDrawCall(pool,identity));
    }
    private BoundInvocation verticalCurrent(){requireKind(Kind.VERTICAL_DOOR);var invocation=verticalInvocation;
        if(invocation==null||invocation.body!=verticalDoor||invocation.whole!=dispatch.whole||plcPort)throw new IllegalStateException("No actual cached DoorV invocation");routeNonwrites(invocation);return invocation;}
    private void verticalSolid(CdTunnelDoorVertical.PrefixSolidCall call){var invocation=verticalCurrent();
        if(invocation.solids!=0||!verticalDoor.isCurrentPrefixSolidCall(call)||!dispatch.consumed.add(call))throw new IllegalStateException("Foreign/repeated DoorV Solid");
        poolCut(call.callCut());source(call.expectedSource());if(!call.expectedSource().equals(verticalBeforeSolid()))throw new IllegalArgumentException("DoorV original native prefix");if(!call.expectedPlayer().equals(primary())||!call.expectedGates().equals(gates())||!call.expectedGlobals().equals(solidGlobals()))throw new IllegalArgumentException("Stale DoorV full player/gates/globals");
        commitSolid(call.callCut(),call.expectedPlayer(),call.expectedGlobals(),call.outcome());invocation.expectedSource=placement.pool().state(dispatch.whole);invocation.solids++;
    }
    private void verticalFm(CdTunnelDoorVertical.FmSound value){var invocation=verticalCurrent();var source=placement.pool().state(dispatch.whole);
        if(invocation.fms!=0||invocation.solids!=0||value!=CdTunnelDoorVertical.FmSound.FM_A4||source.routine()!=4||source.word(TUNNEL_DOOR_TIMER)!=0||(source.spriteFlags()&128)==0)
            throw new IllegalStateException("DoorV FM outside original opening prefix");
        var expected=verticalInitial();if(!verticalOpening(expected)||!source.equals(expected.moveWord(TUNNEL_DOOR_TIMER,0).moveByte(ROUTINE,4)))throw new IllegalArgumentException("DoorV FM native source prefix");
        var before=placement.capture();var actor=frame.captureActor();var shared=frame.captureShared();sound(new SonicCdGameModule.PastVerticalDoorFm(value));requirePlacement(before);requireFrame(actor,shared);invocation.fms++;
    }
    // END R12B Disc/Door World publication.

    // BEGIN R12B SpinTunnel R2 World binding. All Java UNRUN.
    private enum SpinPhase { STEP,PREPARED,PUBLISHED,DRAW,TAIL,DONE }
    private static final class SpinInvocation {
        final CdSpinTunnelTag body;final int whole;final CdObjectPlacementRuntime.Snapshot before;final Cut cut;
        final CdPlayerFrameRuntime.ActorState actor;final CdPlayerFrameRuntime.SharedState shared;
        final CdPlayerBody.State state;final CdPlayerBody.Environment environment;
        CdObjectPool.Snapshot pool;CdObjectState source;byte[] saved;SpinPhase phase=SpinPhase.STEP;
        CdSpinTunnelTag.Result result;CdSpinTunnelTag.DespawnResult tail;CdPlayerFrameRuntime.ObjectSolidPublication prepared;
        boolean playerPublished;
        SpinInvocation(CdSpinTunnelTag body,int whole,CdObjectPlacementRuntime.Snapshot before,Cut cut,
                       CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared){
            this.body=body;this.whole=whole;this.before=before;this.cut=cut;this.actor=actor;this.shared=shared;
            state=actor.state();environment=shared.environment();pool=before.pool();source=pool.slots().get(whole-32);saved=before.savedFlags().copy();
        }
    }
    private void spinCurrent(SpinInvocation invocation,SpinPhase phase){requireKind(Kind.SPIN_TAG);spinValues(invocation,phase);}
    /** Only the matching private pending Adoption may run this read-only preflight with port active. */
    private void spinPublicationCurrent(SpinInvocation invocation,SpinPhase expectedPhase){
        if(invocation==null||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.SPIN_TAG||queuePort
                ||!isCurrentAdoption(pending)||pending.applied||pending.expected!=invocation.cut||pending.next!=invocation.cut)
            throw new IllegalStateException("No matching private SpinTunnel adoption preflight");
        guard();if(dispatch.ownerActor!=null){requireFrame(dispatch.ownerActor,dispatch.ownerShared);requireCut(dispatch.ownerCut);}
        spinValues(invocation,expectedPhase);
    }
    private void spinValues(SpinInvocation invocation,SpinPhase phase){routeObjects();
        if(invocation==null||spinInvocation!=invocation||invocation.body!=spinTunnel||invocation.whole!=dispatch.whole||invocation.phase!=phase||plcPort)
            throw new IllegalStateException("No original current SpinTunnel body/whole/phase");
        extent(terrain.objects().view(CdR12bObjectAssets.View.MAP_SPR_POWERUP),274);requireCut(invocation.cut);
        var actor=invocation.playerPublished?new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(),invocation.prepared.finalState(),invocation.actor.display()):invocation.actor;
        requireFrame(actor,invocation.shared);
        if(frame.state()!=(invocation.playerPublished?invocation.prepared.finalState():invocation.state)||frame.environment()!=invocation.environment)
            throw new IllegalArgumentException("SpinTunnel lost the original complete Frame references");
        var actual=placement.capture();if(!actual.status().equals(invocation.before.status())||!Arrays.equals(actual.savedFlags().copy(),invocation.saved))
            throw new IllegalArgumentException("SpinTunnel changed saved/status nonwrites");
        poolCut(invocation.pool);source(invocation.source);
    }
    /** One synchronous pure native body visit, with its original result held only by this private call. */
    private void runSpinTunnel(int whole){requireKind(Kind.SPIN_TAG);routeObjects();
        if(spinInvocation!=null)throw new IllegalStateException("Nested SpinTunnel native visit");
        var invocation=new SpinInvocation(spinTunnel,whole,placement.capture(),read(),frame.captureActor(),frame.captureShared());spinInvocation=invocation;
        try{
            spinCurrent(invocation,SpinPhase.STEP);var e=invocation.environment;var gates=e.movement().gates();
            invocation.result=spinTunnel.step(invocation.source,invocation.state.motion(),new CdSpinTunnelTag.Gates(gates.zoneAct()>>>8,e.globals().debugMode(),e.globals().debugCheat(),e.controls().rawHeld()));
            spinCurrent(invocation,SpinPhase.STEP);var result=invocation.result;var expected=invocation.source;
            if(expected.routine()==0)expected=expected.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,expected.spriteFlags()|4).moveWord(TILE,0x0544)
                    .moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_POWERUP.range().nativeAddress()).moveByte(MAP_FRAME,expected.subtype()).addByte(MAP_FRAME,1);
            if(result.expectedObject()!=invocation.source||!result.object().equals(expected)||result.player().expectedMotion()!=invocation.state.motion()
                    ||result.drawObject()!=((e.globals().debugCheat()>>>8)!=0))throw new IllegalArgumentException("Not the actual SpinTunnel source/Main receipt");
            var output=CdObjectPool.overlay(invocation.pool,List.of(result.object()));
            // Frame owns full-pair CAS and the exact native motion whitelist. Every aggregate is ready
            // before source Init stores; source publication precedes the existing typed player adoption.
            invocation.prepared=frame.prepareObjectSpinTunnelResult(invocation.state,invocation.environment,result.player());
            if(invocation.prepared.expectedState()!=invocation.state||invocation.prepared.expectedEnvironment()!=invocation.environment||invocation.prepared.finalEnvironment()!=invocation.environment)
                throw new IllegalArgumentException("SpinTunnel preparation did not retain its actual complete pair");
            invocation.phase=SpinPhase.PREPARED;spinCurrent(invocation,SpinPhase.PREPARED);
            boolean sourceStores=invocation.source.routine()==0,playerStores=!result.player().executedWrites().isEmpty();
            if(sourceStores||playerStores)publish(invocation.cut,invocation.cut,sourceStores?output:null,
                    ()->{spinPublicationCurrent(invocation,SpinPhase.PREPARED);frame.validatePreparedObjectSolidPublication(invocation.prepared);},
                    ()->{if(playerStores)frame.applyPreparedObjectSolidPublication(invocation.prepared);});
            invocation.pool=sourceStores?output:invocation.pool;invocation.source=result.object();invocation.playerPublished=playerStores;invocation.phase=SpinPhase.PUBLISHED;
            spinCurrent(invocation,SpinPhase.PUBLISHED);
            if(result.drawObject()){
                invocation.phase=SpinPhase.DRAW;spinCurrent(invocation,SpinPhase.DRAW);var before=invocation.pool;var source=invocation.source;
                drawDynamic(Kind.SPIN_TAG,placement.pool(),source.identity(),()->spinInvocation==invocation&&invocation.result==result&&invocation.phase==SpinPhase.DRAW);
                invocation.source=source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127);invocation.pool=CdObjectPool.overlay(before,List.of(invocation.source));
                spinCurrent(invocation,SpinPhase.DRAW);
            }
            // Reload the LIVE post-Draw full64; never adopt step.object() over Draw's bit7 write.
            invocation.phase=SpinPhase.TAIL;spinCurrent(invocation,SpinPhase.TAIL);var live=placement.pool().state(whole);
            invocation.tail=spinTunnel.despawn(live,new CdSpinTunnelTag.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),invocation.cut.cameraXWord()));
            spinCurrent(invocation,SpinPhase.TAIL);var tail=invocation.tail;boolean delete=outsideOrWarp(live);
            if(tail.deleteObject()!=delete||tail.loadedClear().isPresent()!=(delete&&live.savedFlagsId()!=0))throw new IllegalArgumentException("SpinTunnel native despawn gate mismatch");
            if(tail.loadedClear().isPresent()){
                var clear=tail.loadedClear().orElseThrow();int at=savedTailIndex(live);
                if(clear.savedId()!=live.savedFlagsId()||clear.index()!=at||clear.index()!=2+3*clear.savedId()+clear.period())throw new IllegalArgumentException("SpinTunnel reached saved address mismatch");
                if(at<2||at>=invocation.saved.length)throw stop(Boundary.SPIN_SAVED_FLAGS,whole,at);
                byte[] saved=invocation.saved.clone();saved[at]&=127;
                publish(invocation.cut,invocation.cut,null,()->spinPublicationCurrent(invocation,SpinPhase.TAIL),
                        ()->placement.bclrSavedFlag(clear.savedId(),clear.period(),CdObjectPlacementRuntime.SavedBit.LOADED));
                invocation.saved=saved;spinCurrent(invocation,SpinPhase.TAIL);
            }
            if(delete){
                // Actual DeleteObject owns the zero64 store. No manufactured foreign cleared image or
                // generic placement/Frame restoration is used to publish a pure decision.
                var before=invocation.pool;publish(invocation.cut,invocation.cut,null,()->spinPublicationCurrent(invocation,SpinPhase.TAIL),()->placement.pool().deleteObject(whole));
                var actual=placement.capture();if(!actual.status().equals(invocation.before.status())||!Arrays.equals(actual.savedFlags().copy(),invocation.saved))throw new IllegalStateException("SpinTunnel delete changed bank/status");
                for(int n=0;n<96;n++)if(n==whole-32?!Arrays.equals(new byte[64],actual.pool().slots().get(n).copyBytes()):!actual.pool().slots().get(n).equals(before.slots().get(n)))throw new IllegalStateException("SpinTunnel DeleteObject full96 mismatch");
                invocation.pool=actual.pool();invocation.source=actual.pool().slots().get(whole-32);
            }
            invocation.phase=SpinPhase.DONE;spinCurrent(invocation,SpinPhase.DONE);
            if(dispatch.drawn!=result.drawObject())throw new IllegalStateException("SpinTunnel omitted/repeated original optional Draw");
        }finally{spinInvocation=null;}
    }
    // END R12B SpinTunnel R2 World binding.

    // BEGIN R12B Goal/Signpost actual private World binding.
    private enum EndStep { INVALID,ZONE,Y,X,CAMERA_LEFT,LEFT,CAMERA_DEST,DEST,HUD,SHOES,INVINCIBLE,FM,
        PERIOD,FADE,MUSIC,LOCK,HOLD,ZONE_ACT,BONUS,MINUTES,SECONDS,TIME_BONUS,RINGS,RING_BONUS,
        GOAL_STORE,PLC,SPIN_DRAW,DRAW,DONE }
    private final class EndInvocation {
        final Kind kind;final int whole;final CdObjectState entry;final CdObjectPlacementRuntime.Snapshot before;
        final byte[] saved;CdObjectPool.Snapshot pool;CdObjectState source,drawBefore;Cut cut;
        CdPlayerFrameRuntime.ActorState actor;CdPlayerFrameRuntime.SharedState shared;
        EndStep step;Object operation;boolean drawn,deleted;int value,minutes,seconds;
        CdR12bSectionArt.SectionArtException terminalFailure;
        EndInvocation(Kind kind,int whole){this.kind=kind;this.whole=whole;before=placement.capture();pool=before.pool();
            entry=pool.slots().get(whole-32);source=entry;saved=before.savedFlags().copy();cut=read();actor=frame.captureActor();shared=frame.captureShared();}
    }
    /** Opaque receipt for the ORIGINAL reached Goal cue12 call, not a queue/state capability. */
    final class GoalPlcSubmission {
        private final EndInvocation invocation;private final CdGoalPost.PlcCall request;
        private final CdObjectPlacementRuntime.Snapshot placed;private boolean adopted;
        private GoalPlcSubmission(EndInvocation i,CdGoalPost.PlcCall request){invocation=i;this.request=request;placed=placement.capture();}
        SonicCdAct2PastObjectPass owner(){return SonicCdAct2PastObjectPass.this;}
        int nativeId(){return request.nativeId();}int wholeIndex(){return invocation.whole;}
        void validate(){if(!isCurrentGoalPlcSubmission(this)||adopted)throw new IllegalStateException("Escaped/repeated Goal PLC receipt");
            endVerify(invocation,false);requirePlacement(placed);placementCut(request.actualCut());source(request.expectedSource());}
        void acknowledge(){validate();adopted=true;}
        /** Failure receipt only: Core is now terminal, so no healthy scalar accessor may run. */
        void retainTerminal(CdR12bSectionArt.SectionArtException original){
            if(!isCurrentGoalPlcSubmission(this)||adopted||invocation.terminalFailure!=null||!original.state().stopped())
                throw new IllegalStateException("Not the actual pending Goal terminal throw");
            invocation.terminalFailure=original;
        }
    }
    boolean isCurrentGoalPlcSubmission(GoalPlcSubmission value){return value!=null&&pendingGoalPlc==value&&plcPort&&!port&&!queuePort
            &&phase==Phase.WALKING&&dispatch!=null&&dispatch.kind==Kind.GOAL&&endInvocation==value.invocation
            &&value.invocation.step==EndStep.PLC&&value.invocation.operation==value.request&&value.request.nativeId()==0x12
            &&value.request.site()==CdGoalPost.PlcSite.GOAL_REACHED;}
    private EndInvocation endCurrent(Kind kind){
        requireKind(kind);var i=endInvocation;if(i==null||i.kind!=kind||i.whole!=dispatch.whole||port||queuePort||plcPort||i.operation!=null)
            throw new IllegalStateException("No actual private End callback");endVerify(i,false);return i;
    }
    private void endPool(EndInvocation i,boolean failure){
        var actual=placement.capture();if(!actual.status().equals(i.before.status())||!Arrays.equals(actual.savedFlags().copy(),i.saved))
            throw new IllegalStateException("End changed spawner/saved nonwrites");
        for(int n=0;n<96;n++){
            var expected=i.pool.slots().get(n);var live=actual.pool().slots().get(n);
            if(i.deleted&&n==i.whole-32){if(!Arrays.equals(live.copyBytes(),new byte[64]))throw new IllegalStateException("Goal deletion lost zero64");}
            else if(!expected.equals(live)&&!(failure&&n==i.whole-32&&i.drawBefore!=null&&i.drawBefore.equals(live)))
                throw new IllegalStateException("End changed an unowned full64 at "+(n+32));
        }
    }
    private void endVerify(EndInvocation i,boolean failure){
        if(endInvocation!=i||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=i.kind||dispatch.whole!=i.whole)
            throw new IllegalStateException("Retired/foreign End invocation");
        requireFrame(i.actor,i.shared);endPool(i,failure);
        if(i.terminalFailure==null){guard();requireCut(i.cut);} // Terminal-only prefix has no healthy continuation.
    }
    private void endSource(EndInvocation i,CdObjectState value){i.source=value;i.pool=CdObjectPool.overlay(i.pool,List.of(value));}
    private void endCall(EndInvocation i,Object call,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,EndStep step){
        if(i.step!=step||i.operation!=null||call==null||!dispatch.consumed.add(call))throw new IllegalStateException("Cloned/reordered End call");
        endVerify(i,false);placementCut(cut);source(source);if(!i.source.equals(source))throw new IllegalArgumentException("Wrong native End source prefix");i.operation=call;
    }
    private void endRefresh(EndInvocation i){i.actor=frame.captureActor();i.shared=frame.captureShared();i.cut=read();}
    /** Preflight accepts ONLY its own pending End Adoption while port=true; ordinary reentry stays closed. */
    private void endPublicationPreflight(EndInvocation i,Object call,CdPlayerFrameRuntime.ObjectSolidPublication ready){
        if(!isCurrentAdoption(pending)||pending.applied||pending.endOperation!=call||i.operation!=call||pending.expected!=i.cut
                ||endInvocation!=i||dispatch==null||dispatch.kind!=i.kind||dispatch.whole!=i.whole||plcPort||queuePort)
            throw new IllegalStateException("Not the matching pending End adoption");
        endVerify(i,false);if(ready!=null)frame.validatePreparedObjectSolidPublication(ready);
    }
    private void endPublish(EndInvocation i,Object call,Cut next,CdPlayerFrameRuntime.ObjectSolidPublication ready,int destination){
        if(port||pending!=null||i.operation!=call)throw new IllegalStateException("Recursive End publication");
        var publication=new Adoption(i.cut,next,null,()->endPublicationPreflight(i,call,ready),()->{if(ready!=null)frame.applyPreparedObjectSolidPublication(ready);});
        publication.destinationLeftWord=destination;publication.endOperation=call;pending=publication;port=true;
        try{requireCut(i.cut);ports.adopt(publication);if(!publication.applied)throw new IllegalStateException("Missing End adoption receipt");
            requireCut(next);requireFrame(publication.afterActor,publication.afterShared);requirePlacement(publication.afterPlacement);
            checkpoint();endRefresh(i);
        }finally{pending=null;port=false;}
    }
    private static Cut endLevel(Cut c,CdLevelGlobals level){return new Cut(c.globals(),level,c.goodFutureByte(),c.goodFutureFlags(),c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds(),c.cameraXWord(),c.cameraYWord(),c.timeAttackModeByte(),c.layerByte());}
    private static SonicCdAct2PastWorldHost.Globals endGlobals(SonicCdAct2PastWorldHost.Globals g,CdPlayerBody.ContactGlobals contact,Optional<CdLevelHud.BonusValues> bonus){
        return new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),contact,g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),bonus,g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7());}
    private void beginEnd(Kind kind,int whole){requireKind(kind);if(endInvocation!=null||plcPort)throw new IllegalStateException("End reentry");
        var gates=frame.environment().movement().gates();if(gates.zoneAct()!=1||gates.timeZone()!=0)throw new IllegalStateException("Only the actual R12B ordinary period is bound");endInvocation=new EndInvocation(kind,whole);}
    private void endFailure(EndInvocation i,Throwable original){
        try{
            if(i.kind==Kind.SIGNPOST&&i.entry.routine()==4&&i.step==EndStep.SPIN_DRAW&&original instanceof CdSignpost.UnsupportedSignpost){
                var head=i.entry;if(head.unsignedByte(ANIMATION)!=head.unsignedByte(PREVIOUS_ANIMATION))head=head.moveByte(PREVIOUS_ANIMATION,head.unsignedByte(ANIMATION)).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0);
                head=head.addByte(ANIMATION_TIME,-1);
                if(head.signedByte(ANIMATION_TIME)<0&&head.unsignedByte(ANIMATION)==0){var script=terrain.objects().view(CdR12bObjectAssets.View.ANI_SIGNPOST);head=head.moveByte(ANIMATION_TIME,script.unsignedByte((short)script.unsignedWord(0)));}
                endSource(i,head);
            }
            if(i.terminalFailure!=null&&i.terminalFailure!=original)throw new IllegalStateException("Replaced original Goal terminal throw");
            endVerify(i,true);
        }catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
    }
    private void runGoal(int whole){
        beginEnd(Kind.GOAL,whole);var i=endInvocation;
        try{
            i.step=switch(i.entry.routine()){case 0,2->EndStep.Y;case 4->EndStep.DRAW;default->EndStep.INVALID;};
            if(i.entry.routine()==0)endSource(i,i.entry.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,i.entry.spriteFlags()|4).moveByte(PRIORITY,4)
                    .moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress()).moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,32).moveByte(MAP_FRAME,5)
                    .moveWord(TILE,terrain.objects().view(CdR12bObjectAssets.View.GOAL_POST_BASE_TILES).unsignedWord(8)|0x8000));
            var gates=frame.environment().movement().gates();var r=goalPost.run(whole,new CdGoalPost.Globals(gates.zoneAct(),gates.timeZone(),i.cut.goodFutureByte(),gates.timeWarpDirection(),i.cut.cameraXWord()));
            if(i.step!=EndStep.DONE||!i.drawn)throw new IllegalStateException("Goal omitted Draw tail");
            boolean deleted=i.source.signedByte(SPRITE_FLAGS)>=0&&(((i.source.centreX()&0xFF80)-((i.cut.cameraXWord()-0x80)&0xFF80))&65535)>0x280;
            if(deleted){int id=i.source.savedFlagsId();if(id!=0){int at=2+3*id;if(at>=i.saved.length)throw new IllegalStateException("Goal admitted an unowned saved byte");i.saved[at]&=0x7F;}i.deleted=true;}
            endVerify(i,false);if(r.deleted()!=deleted||!r.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Goal lost its native return");
        }catch(RuntimeException|Error original){endFailure(i,original);throw original;}finally{endInvocation=null;}
    }
    private int goalRead(CdGoalPost.ReadCall c){var i=endCurrent(Kind.GOAL);var step=c.field()==CdGoalPost.PlayerField.Y_WORD?EndStep.Y:EndStep.X;endCall(i,c,c.actualCut(),c.expectedSource(),step);
        try{int value=c.field()==CdGoalPost.PlayerField.Y_WORD?frame.state().motion().centreY16_16()>>>16:frame.state().motion().centreX16_16()>>>16;
            if(step==EndStep.Y){int dy=(value-i.source.centreY()+128)&65535;i.step=(short)dy<0||dy>=256?EndStep.DRAW:EndStep.X;}
            else{if(value<i.source.centreX())i.step=EndStep.DRAW;else{endSource(i,i.source.addByte(ROUTINE,2));i.step=EndStep.GOAL_STORE;}}
            return value;
        }finally{i.operation=null;}}
    private void goalReached(CdGoalPost.GoalReachedCall c){var i=endCurrent(Kind.GOAL);endCall(i,c,c.actualCut(),c.expectedSource(),EndStep.GOAL_STORE);
        try{if(c.cameraXWord()!=i.cut.cameraXWord())throw new IllegalArgumentException("Stale Goal camera");var ready=frame.prepareObjectGoalPostReached(i.actor.state(),i.shared.environment(),c.cameraXWord());
            var g=i.cut.globals();var contact=g.contact();var next=withGlobals(i.cut,endGlobals(g,new CdPlayerBody.ContactGlobals(0,contact.shield(),contact.combineRing()),g.bonus()));
            endPublish(i,c,next,ready,c.cameraXWord());i.step=EndStep.PLC;
        }finally{i.operation=null;}}
    private void goalPlc(CdGoalPost.PlcCall c){var i=endCurrent(Kind.GOAL);endCall(i,c,c.actualCut(),c.expectedSource(),EndStep.PLC);
        try{if(c.site()!=CdGoalPost.PlcSite.GOAL_REACHED||c.nativeId()!=0x12||pendingGoalPlc!=null)throw new IllegalArgumentException("Not Goal cue12");
            var submission=new GoalPlcSubmission(i,c);pendingGoalPlc=submission;plcPort=true;
            try{submission.validate();ports.submitGoalPlc(submission);if(!submission.adopted)throw new IllegalStateException("Missing Goal PLC completion");endRefresh(i);i.step=EndStep.DRAW;}
            finally{pendingGoalPlc=null;plcPort=false;}
        }finally{i.operation=null;}}
    private void endDraw(EndInvocation i,Object c,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source){
        endCall(i,c,cut,source,EndStep.DRAW);i.drawBefore=i.source;endSource(i,i.source.moveByte(SPRITE_FLAGS,i.source.spriteFlags()&127));
        try{drawDynamic(i.kind,placement.pool(),source.identity(),()->endInvocation==i&&i.operation==c&&i.step==EndStep.DRAW&&!i.drawn);
            endPool(i,false);i.drawn=true;i.step=EndStep.DONE;
        }finally{i.operation=null;}}
    private void goalDraw(CdGoalPost.DrawCall c){var i=endCurrent(Kind.GOAL);endDraw(i,c,c.actualCut(),c.expectedSource());}
    private void runSignpost(int whole){
        beginEnd(Kind.SIGNPOST,whole);var i=endInvocation;
        try{
            switch(i.entry.routine()){
                case 0->{endSource(i,i.entry.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,i.entry.spriteFlags()|4).moveByte(X_RADIUS,24).moveByte(WIDTH,24).moveByte(Y_RADIUS,32).moveByte(PRIORITY,4).moveWord(TILE,0x043C));i.step=EndStep.ZONE;}
                case 2->i.step=EndStep.Y;case 4->i.step=EndStep.SPIN_DRAW;
                case 6->{endSource(i,i.entry.addByte(LEVEL_END_TIMER,-1));i.step=i.source.unsignedByte(LEVEL_END_TIMER)==0?EndStep.PERIOD:EndStep.DRAW;}
                case 8->i.step=EndStep.DRAW;default->i.step=EndStep.INVALID;
            }
            var r=signpost.run(whole);endVerify(i,false);if(i.step!=EndStep.DONE||!i.drawn||!r.state().equals(placement.pool().state(whole)))throw new IllegalStateException("Signpost omitted native Draw/return");
        }catch(RuntimeException|Error original){endFailure(i,original);throw original;}finally{endInvocation=null;}
    }
    private int signRead(CdSignpost.ReadCall c){var i=endCurrent(Kind.SIGNPOST);EndStep required=switch(c.field()){
        case ZONE_BYTE->EndStep.ZONE;case PLAYER_Y_WORD->EndStep.Y;case PLAYER_X_WORD->EndStep.X;case CAMERA_X_WORD->i.step==EndStep.CAMERA_LEFT?EndStep.CAMERA_LEFT:EndStep.CAMERA_DEST;
        case TIME_ZONE_BYTE->EndStep.PERIOD;case ZONE_ACT_WORD->EndStep.ZONE_ACT;case TIME_MINUTES_BYTE->EndStep.MINUTES;case TIME_SECONDS_BYTE->EndStep.SECONDS;case RINGS_WORD->EndStep.RINGS;};
        endCall(i,c,c.actualCut(),c.expectedSource(),required);
        try{int value;switch(c.field()){
            case ZONE_BYTE->{value=frame.environment().movement().gates().zoneAct()>>>8;endSource(i,i.source.moveWord(TILE,value==3?0x043C:0x843C).moveLong(MAPPINGS,CdR12bObjectAssets.View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress()));i.step=EndStep.Y;}
            case PLAYER_Y_WORD->{value=frame.state().motion().centreY16_16()>>>16;int dy=(value-i.source.centreY()+128)&65535;i.step=(short)dy<0||dy>=256?EndStep.DRAW:EndStep.X;}
            case PLAYER_X_WORD->{value=frame.state().motion().centreX16_16()>>>16;i.step=value>i.source.centreX()?EndStep.CAMERA_LEFT:EndStep.DRAW;}
            case CAMERA_X_WORD->{value=i.cut.cameraXWord();i.value=value;i.step=required==EndStep.CAMERA_LEFT?EndStep.LEFT:EndStep.DEST;}
            case TIME_ZONE_BYTE->{value=frame.environment().movement().gates().timeZone();i.step=value==0?EndStep.FADE:EndStep.MUSIC;}
            case ZONE_ACT_WORD->{value=frame.environment().movement().gates().zoneAct();if(value!=1)throw new IllegalStateException("Foreign Signpost programme");
                endSource(i,i.source.moveByte(LEVEL_END_TIMER,180).addByte(ROUTINE,2));
                for(var image:i.pool.slots())if(image.id()==0){i.pool=CdObjectPool.overlay(i.pool,List.of(image.moveByte(ID,CdSignpost.RESULTS_ID).moveByte(RESULTS_TIMER,16)));break;}i.step=EndStep.BONUS;}
            case TIME_MINUTES_BYTE->{value=endClock(i,CdCheckpointLiveAux.TimeByte.MINUTES);i.minutes=value;i.step=EndStep.SECONDS;}
            case TIME_SECONDS_BYTE->{value=endClock(i,CdCheckpointLiveAux.TimeByte.SECONDS);i.seconds=value;int index=Math.min(((i.minutes*60+value)&65535)/15,20);i.value=terrain.objects().view(CdR12bObjectAssets.View.LEVEL_END_TIME_BONUSES).unsignedWord(index*2);i.step=EndStep.TIME_BONUS;}
            case RINGS_WORD->{value=i.cut.globals().ring().rings();i.value=(value*100)&65535;i.step=EndStep.RING_BONUS;}
            default->throw new IllegalStateException("Unknown Signpost read");}return value;
        }finally{i.operation=null;}}
    private int endClock(EndInvocation i,CdCheckpointLiveAux.TimeByte field){return i.cut.globals().live().knownByte(field.nativeAddress()).orElseThrow(()->stop(Boundary.END_CLOCK,i.whole,field.nativeAddress()));}
    private void signStore(CdSignpost.StoreCall c){var i=endCurrent(Kind.SIGNPOST);var required=switch(c.field()){
        case LEFT_BOUND_WORD->EndStep.LEFT;case DEST_LEFT_BOUND_WORD->EndStep.DEST;case UPDATE_HUD_TIME_BYTE->EndStep.HUD;case SPEED_SHOES_BYTE->EndStep.SHOES;case INVINCIBLE_BYTE->EndStep.INVINCIBLE;
        case CONTROL_LOCK_BIT0->EndStep.LOCK;case PLAYER_CONTROL_HOLD_WORD->EndStep.HOLD;case UPDATE_HUD_BONUS_BYTE->EndStep.BONUS;case TIME_BONUS_WORD->EndStep.TIME_BONUS;case RING_BONUS_WORD->EndStep.RING_BONUS;};
        endCall(i,c,c.actualCut(),c.expectedSource(),required);
        try{int expected=switch(required){case LEFT,DEST,TIME_BONUS,RING_BONUS->i.value;case LOCK,BONUS->1;case HOLD->0x0808;default->0;};
            if(c.value()!=expected)throw new IllegalArgumentException("Wrong reached Signpost store");var next=i.cut;CdPlayerFrameRuntime.ObjectSolidPublication ready=null;int destination=-1;
            switch(c.field()){
                case LEFT_BOUND_WORD,SPEED_SHOES_BYTE,INVINCIBLE_BYTE,CONTROL_LOCK_BIT0,PLAYER_CONTROL_HOLD_WORD->ready=frame.prepareObjectSignpostStore(i.actor.state(),i.shared.environment(),c.field(),c.value());
                case DEST_LEFT_BOUND_WORD->destination=c.value();
                case UPDATE_HUD_TIME_BYTE->{var l=i.cut.level();next=endLevel(i.cut,new CdLevelGlobals(l.levelRestart(),0,l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));}
                case UPDATE_HUD_BONUS_BYTE,TIME_BONUS_WORD,RING_BONUS_WORD->{var g=i.cut.globals();var b=g.bonus().orElseThrow(()->stop(Boundary.END_BONUS,i.whole,c.field().ordinal()));
                    var bonus=new CdLevelHud.BonusValues(c.field()==CdSignpost.StoreField.TIME_BONUS_WORD?c.value():b.timeBonusWord(),c.field()==CdSignpost.StoreField.RING_BONUS_WORD?c.value():b.ringBonusWord(),c.field()==CdSignpost.StoreField.UPDATE_HUD_BONUS_BYTE?c.value():b.updateHudBonusByte());next=withGlobals(i.cut,endGlobals(g,g.contact(),Optional.of(bonus)));}
            }
            endPublish(i,c,next,ready,destination);
            i.step=switch(required){case LEFT->EndStep.CAMERA_DEST;case DEST->EndStep.HUD;case HUD->EndStep.SHOES;case SHOES->EndStep.INVINCIBLE;case INVINCIBLE->EndStep.FM;
                case LOCK->EndStep.HOLD;case HOLD->EndStep.ZONE_ACT;case BONUS->EndStep.MINUTES;case TIME_BONUS->EndStep.RINGS;case RING_BONUS->EndStep.DRAW;default->throw new IllegalStateException("Unknown completed Signpost store");};
            if(required==EndStep.HUD)endSource(i,i.source.moveByte(LEVEL_END_TIMER,120).moveByte(MAP_FRAME,0).addByte(ROUTINE,2));
        }finally{i.operation=null;}}
    private void signSound(CdSignpost.SoundCall c){var i=endCurrent(Kind.SIGNPOST);var required=switch(c.sound()){case FM_SIGNPOST->EndStep.FM;case SCMD_FADEPCM->EndStep.FADE;case SCMD_RESULTMUS->EndStep.MUSIC;};endCall(i,c,c.actualCut(),c.expectedSource(),required);
        try{var sounds=new ArrayList<>(i.cut.sounds());sounds.add(new SonicCdGameModule.PastSignpostSound(c.sound()));endPublish(i,c,withSounds(i.cut,sounds),null,-1);
            i.step=required==EndStep.FM?EndStep.DRAW:required==EndStep.FADE?EndStep.MUSIC:EndStep.LOCK;
        }finally{i.operation=null;}}
    private void signDraw(CdSignpost.DrawCall c){
        EndInvocation i;
        if(endInvocation!=null&&endInvocation.step==EndStep.SPIN_DRAW){
            requireKind(Kind.SIGNPOST);i=endInvocation;
            if(i.kind!=Kind.SIGNPOST||i.whole!=dispatch.whole||i.operation!=null||port||queuePort||plcPort)throw new IllegalStateException("No actual Signpost animation tail");
            requireFrame(i.actor,i.shared);requireCut(i.cut);
        }else i=endCurrent(Kind.SIGNPOST);
        if(i.step==EndStep.SPIN_DRAW){var s=i.entry;var out=CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),s.unsignedByte(PREVIOUS_ANIMATION),s.unsignedByte(ANIMATION_FRAME),s.unsignedByte(ANIMATION_TIME),s.unsignedByte(MAP_FRAME),s.spriteFlags(),s.flags(),s.routine(),s.unsignedByte(ROUTINE_2)),new CdObjectAnimation.AnimationTable(terrain.objects().view(CdR12bObjectAssets.View.ANI_SIGNPOST),0,1));
            s=s.moveByte(ANIMATION,out.animationId()).moveByte(PREVIOUS_ANIMATION,out.previousId()).moveByte(ANIMATION_FRAME,out.scriptIndex()).moveByte(ANIMATION_TIME,out.timer()).moveByte(MAP_FRAME,out.mapFrame()).moveByte(SPRITE_FLAGS,out.spriteFlags()).moveByte(FLAGS,out.statusFlags()).moveByte(ROUTINE,out.routine()).moveByte(ROUTINE_2,out.routine2()).addByte(LEVEL_END_TIMER,-1);
            if(s.unsignedByte(LEVEL_END_TIMER)==0)s=s.addByte(ROUTINE,2).moveByte(MAP_FRAME,3).moveByte(LEVEL_END_TIMER,60);endSource(i,s);i.step=EndStep.DRAW;}
        endDraw(i,c,c.actualCut(),c.expectedSource());
    }
    // END R12B Goal/Signpost actual private World binding.

    // BEGIN R12B Results actual private World binding.
    // N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:42-366; no FixBugs alternative.
    // Physical source stores stay in CdResults; these images verify native prefixes, never replay them.
    /** Existing complete-owner references, no capture participant or caller authority. */
    record ResultsOwners(SonicCdAct2PastLoadingVisit.State core,SonicCdGameModule.LevelProgramState programme,SonicCdAct2PastWorldHost.Prepared hostFence) {
        ResultsOwners {Objects.requireNonNull(core);Objects.requireNonNull(programme);Objects.requireNonNull(hostFence);}
    }
    private enum ResultsStep { INVALID,PLC,HEAD,WAIT_ZONE,CAMERA,PLAYER_X,ROW_ZONE,ROW_GOOD,ROW_ACT,
        UPDATE,TIME,TIME_STORE,RING,RING_STORE,CHECK_TIME,CHECK_RING,SPECIAL,TALLY,KACHING,WARP,POINTS,DRAW,RETURN,ALLOCATION_STOP,
        PROGRAMME,SPAWN,SECTION,FLOWERS,UNKNOWN,PROJ,CHECKPOINT,INITIAL_ATTACK,PLC_BIT0,PLC_BIT1,PERIOD_PRESENT,ZONE_OPERAND,
        PERIOD_FUTURE,ZONE_STORE,RESET,FADE,TAIL_ACT,TAIL_ATTACK,TAIL_STONES,TAIL_GOOD,GOOD_CLEAR,FLAGS_SET,FLAGS_COMPARE,GOOD_SET,FLAGS_CLEAR }
    private final class ResultsInvocation {
        final int whole;final CdObjectState entry;final CdObjectPlacementRuntime.Snapshot before;
        CdObjectPool.Snapshot expectedPool;CdObjectPlacementRuntime.Status status;CdAssetBytes saved;
        CdPlayerFrameRuntime.ActorState actor;CdPlayerFrameRuntime.SharedState shared;Cut cut;ResultsOwners owners;
        ResultsStep step;Object operation;CdObjectState drawBefore;boolean drawn;
        int row,selected,root,edge,time,rings,points,nextZoneAct,actBit;
        ResultsInvocation(int whole){this.whole=whole;before=placement.capture();expectedPool=before.pool();status=before.status();saved=before.savedFlags();entry=placement.pool().state(whole);cut=read();actor=frame.captureActor();shared=frame.captureShared();}
    }
    boolean isCurrentResultsOwnersRead(){return resultsBorrow&&resultsInvocation!=null&&phase==Phase.WALKING&&dispatch!=null
            &&dispatch.kind==Kind.RESULTS&&dispatch.whole==resultsInvocation.whole&&!queuePort
            &&(!plcPort||pendingResultsPlc!=null&&isCurrentResultsPlcSubmission(pendingResultsPlc))
            &&(!port||pending!=null&&pending.resultsOperation==resultsInvocation.operation&&pending.resultsOwners==resultsInvocation.owners&&(!pending.applied||resultsFlagsField(pending)));}
    private ResultsOwners resultsOwners(ResultsInvocation i,ResultsOwners expected){
        if(resultsBorrow||resultsInvocation!=i)throw new IllegalStateException("Recursive/retired Results owner borrow");resultsBorrow=true;
        try{return Objects.requireNonNull(ports.resultsOwners(expected));}finally{resultsBorrow=false;}
    }
    private CdObjectState resultsSource(ResultsInvocation i){return i.expectedPool.slots().get(i.whole-32);}
    private void resultsImage(ResultsInvocation i,CdObjectState image){i.expectedPool=CdObjectPool.overlay(i.expectedPool,List.of(image));}
    private void resultsPool(ResultsInvocation i,boolean failure){var now=placement.capture();
        if(!i.status.equals(now.status())||!same(i.saved,now.savedFlags()))throw new IllegalStateException("Results changed saved bank/spawner nonwrites");
        for(int n=0;n<96;n++)if(!i.expectedPool.slots().get(n).equals(now.pool().slots().get(n))
                &&!(failure&&n==i.whole-32&&i.drawBefore!=null&&i.drawBefore.equals(now.pool().slots().get(n))))throw new IllegalStateException("Results differs from actual full96/source64 prefix at "+(n+32));
    }
    private void resultsVerify(ResultsInvocation i,boolean failure){
        if(resultsInvocation!=i||phase!=Phase.WALKING||dispatch==null||dispatch.kind!=Kind.RESULTS||dispatch.whole!=i.whole)throw new IllegalStateException("No actual private Results invocation");
        requireFrame(i.actor,i.shared);requireCut(i.cut);resultsPool(i,failure);if(i.owners!=null)resultsOwners(i,i.owners);
    }
    private ResultsInvocation currentResults(){requireKind(Kind.RESULTS);var i=resultsInvocation;if(i==null||i.operation!=null||port||queuePort||plcPort||resultsBorrow)throw new IllegalStateException("No original Results callback");resultsVerify(i,false);return i;}
    private void resultsCall(ResultsInvocation i,Object call,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,ResultsStep site){
        if(i.step!=site||call==null||i.operation!=null||!dispatch.consumed.add(call))throw new IllegalStateException("Cloned/repeated/reordered Results callback");resultsVerify(i,false);placementCut(cut);source(source);if(!resultsSource(i).equals(source))throw new IllegalArgumentException("Not actual Results source prefix");i.operation=call;
    }
    private void refreshResults(ResultsInvocation i){i.actor=frame.captureActor();i.shared=frame.captureShared();i.cut=read();i.owners=resultsOwners(i,null);}
    private boolean resultsAdoption(Adoption p){var i=resultsInvocation;return i!=null&&isCurrentAdoption(p)&&p.resultsOperation!=null&&p.resultsOperation==i.operation&&p.resultsOwners==i.owners&&p.expected==i.cut&&dispatch!=null&&dispatch.kind==Kind.RESULTS&&dispatch.whole==i.whole;}
    boolean isCurrentResultsSectionAdoption(Adoption p){return resultsAdoption(p)&&!p.applied&&resultsInvocation.step==ResultsStep.SECTION&&p.resultsSectionWord==0&&p.resultsOperation instanceof CdResults.ProgrammeStoreCall c&&c.field()==CdResults.ProgrammeField.SECTION_ID_WORD&&c.value()==0;}
    boolean isCurrentResultsFlagsAdoption(Adoption p){return resultsAdoption(p)&&!p.applied&&resultsFlagsField(p);}
    private boolean resultsFlagsField(Adoption p){var i=resultsInvocation;return i!=null&&p.resultsOperation instanceof CdResults.ProgrammeStoreCall c&&(i.step==ResultsStep.FLAGS_CLEAR&&c.field()==CdResults.ProgrammeField.GOOD_FUTURE_FLAGS_CLEAR_BYTE||i.step==ResultsStep.FLAGS_SET&&c.field()==CdResults.ProgrammeField.GOOD_FUTURE_FLAGS_SET_BIT);}
    void validateResultsFlagsPublication(Adoption p,SonicCdGameModule.LevelProgramState expected,boolean nativeApplied){
        if(!resultsAdoption(p)||p.applied!=nativeApplied||!resultsFlagsField(p)||p.resultsOwners.programme()!=expected||plcPort||queuePort)throw new IllegalStateException("Not original matching Results flag publication");resultsVerify(resultsInvocation,false);if(p.next.goodFutureFlags().isEmpty())throw new IllegalArgumentException("Missing actual flag result");
    }
    private void resultsPreflight(ResultsInvocation i,Object call,CdPlayerFrameRuntime.ObjectSolidPublication frameReady){
        if(!resultsAdoption(pending)||pending.applied||i.operation!=call||plcPort||queuePort)throw new IllegalStateException("Not matching unapplied Results Adoption");resultsVerify(i,false);if(frameReady!=null)frame.validatePreparedObjectSolidPublication(frameReady);
    }
    private void resultsPublish(ResultsInvocation i,Object call,Cut next,CdPlayerFrameRuntime.ObjectSolidPublication frameReady,int section){
        if(port||pending!=null||i.operation!=call)throw new IllegalStateException("Recursive Results publication");var p=new Adoption(i.cut,next,null,()->resultsPreflight(i,call,frameReady),()->{if(frameReady!=null)frame.applyPreparedObjectSolidPublication(frameReady);});p.resultsOperation=call;p.resultsOwners=i.owners;p.resultsSectionWord=section;pending=p;port=true;
        try{ports.adopt(p);if(!p.applied)throw new IllegalStateException("Missing Results adoption receipt");requireCut(next);requireFrame(p.afterActor,p.afterShared);requirePlacement(p.afterPlacement);}finally{pending=null;port=false;}
        checkpoint();refreshResults(i);
    }
    final class ResultsPlcSubmission {
        private final ResultsInvocation invocation;private final CdResults.PlcCall call;private final CdObjectPlacementRuntime.Snapshot placed;private boolean adopted;
        private ResultsPlcSubmission(ResultsInvocation i,CdResults.PlcCall call){invocation=i;this.call=call;placed=placement.capture();}
        SonicCdAct2PastObjectPass owner(){return SonicCdAct2PastObjectPass.this;}int wholeIndex(){return invocation.whole;}
        void validate(){if(!isCurrentResultsPlcSubmission(this)||adopted)throw new IllegalStateException("Escaped/repeated Results cue10");resultsVerify(invocation,false);requirePlacement(placed);placementCut(call.actualCut());source(call.expectedSource());}
        void acknowledge(SonicCdAct2PastLoadingVisit.State ready){
            if(!isCurrentResultsPlcSubmission(this)||adopted)throw new IllegalStateException("Not pending Results completion");requireFrame(invocation.actor,invocation.shared);requireCut(invocation.cut);requirePlacement(placed);var actual=resultsOwners(invocation,null);
            if(actual.core()!=ready||actual.programme()!=invocation.owners.programme())throw new IllegalStateException("Results PLC did not adopt its prepared existing Core");invocation.owners=actual;adopted=true;
        }
    }
    boolean isCurrentResultsPlcSubmission(ResultsPlcSubmission p){return p!=null&&p==pendingResultsPlc&&plcPort&&!port&&!queuePort&&resultsInvocation==p.invocation&&phase==Phase.WALKING&&dispatch!=null&&dispatch.kind==Kind.RESULTS&&dispatch.whole==p.invocation.whole&&p.invocation.step==ResultsStep.PLC&&p.invocation.operation==p.call&&p.call.nativeId()==0x10;}
    private void runResults(int whole){requireKind(Kind.RESULTS);if(resultsInvocation!=null||resultsBorrow||plcPort)throw new IllegalStateException("Results reentry");var i=new ResultsInvocation(whole);resultsInvocation=i;
        try{i.owners=resultsOwners(i,null);i.step=switch(i.entry.routine()){case 0->ResultsStep.PLC;case 2->ResultsStep.HEAD;case 4->ResultsStep.DRAW;case 6->ResultsStep.UPDATE;case 8->ResultsStep.PROGRAMME;default->ResultsStep.INVALID;};
            if(i.entry.routine()==0){resultsImage(i,i.entry.addByte(RESULTS_TIMER,-1));if(resultsSource(i).unsignedByte(RESULTS_TIMER)!=0)i.step=ResultsStep.RETURN;}else if(i.entry.routine()==4)resultsMove(i);
            var r=results.run(whole);resultsVerify(i,false);if(i.step!=ResultsStep.RETURN||!r.state().equals(resultsSource(i)))throw new IllegalStateException("Results omitted actual conditional tail");
        }catch(RuntimeException|Error original){try{resultsVerify(i,true);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}finally{resultsInvocation=null;}
    }
    private void resultsMove(ResultsInvocation i){var s=resultsSource(i);if(s.word(RESULTS_TIMER_WORD)!=0)s=s.addWord(RESULTS_TIMER_WORD,-1);int x=s.centreX(),dest=s.word(RESULTS_DEST_X);if(x!=dest)s=s.addWord(X,(short)dest<(short)x?-8:8);else if(s.unsignedByte(MAP_FRAME)==0)s=s.addByte(ROUTINE,2);resultsImage(i,s);i.step=s.word(RESULTS_TIMER_WORD)<472?ResultsStep.DRAW:ResultsStep.RETURN;}
    private void beginResultsRows(ResultsInvocation i){resultsImage(i,resultsSource(i).moveWord(RESULTS_TIMER_WORD,480));i.row=0;i.selected=i.whole;prepareResultsRow(i);}
    private void prepareResultsRow(ResultsInvocation i){resultsImage(i,i.expectedPool.slots().get(i.selected-32).moveWord(RESULTS_TIMER_WORD,480).moveByte(ID,0x3A).moveByte(ROUTINE,4).moveWord(TILE,0x83C4));i.step=ResultsStep.ROW_ZONE;}
    private void finishResultsRow(ResultsInvocation i){if(++i.row==3){i.step=ResultsStep.RETURN;return;}i.selected=-1;for(var s:i.expectedPool.slots())if(s.id()==0){i.selected=s.identity().wholeIndex();break;}if(i.selected<0){i.step=ResultsStep.ALLOCATION_STOP;return;}prepareResultsRow(i);}
    private void resultsRowData(ResultsInvocation i){var data=terrain.objects().view(CdR12bObjectAssets.View.RESULTS_INIT_DATA);int at=i.row*8;resultsImage(i,i.expectedPool.slots().get(i.selected-32).moveWord(RESULTS_SCREEN_Y_WORD,data.unsignedWord(at)).moveWord(X,data.unsignedWord(at+2)).moveWord(RESULTS_DEST_X,data.unsignedWord(at+4)).moveByte(MAP_FRAME,data.unsignedByte(at+7)));if(i.row==2)i.step=ResultsStep.ROW_ACT;else finishResultsRow(i);}
    private void resultsNoBonus(ResultsInvocation i){var s=resultsSource(i).addWord(RESULTS_TIMER_WORD,-1);int timer=s.word(RESULTS_TIMER_WORD);if((short)timer<0)s=s.addByte(ROUTINE,2);resultsImage(i,s);i.step=timer==30?ResultsStep.SPECIAL:ResultsStep.DRAW;}
    private void resultsHaveBonus(ResultsInvocation i){var s=resultsSource(i);if(s.word(RESULTS_TIMER_WORD)!=0)s=s.addWord(RESULTS_TIMER_WORD,-1);resultsImage(i,s);i.step=(s.unsignedByte(RESULTS_TIMER)&1)==0?ResultsStep.TALLY:ResultsStep.POINTS;}
    private CdLevelHud.BonusValues resultsBonusValue(ResultsInvocation i){return i.cut.globals().bonus().orElseThrow(()->stop(Boundary.RESULTS_BONUS,i.whole,i.step.ordinal()));}
    private int resultsFlags(ResultsInvocation i){return i.cut.goodFutureFlags().orElseThrow(()->stop(Boundary.RESULTS_FLAGS,i.whole,i.step.ordinal()));}
    private int resultsRead(CdResults.ReadCall c){var i=currentResults();var site=i.step;boolean reached=switch(c.field()){
        case PLC_HEAD_LONG->site==ResultsStep.HEAD;case ZONE_ACT_WORD->site==ResultsStep.WAIT_ZONE||site==ResultsStep.ROW_ZONE||site==ResultsStep.ZONE_OPERAND;case CAMERA_X_WORD->site==ResultsStep.CAMERA;case PLAYER_X_WORD->site==ResultsStep.PLAYER_X;case GOOD_FUTURE_BYTE->site==ResultsStep.ROW_GOOD||site==ResultsStep.TAIL_GOOD;case ACT_BYTE->site==ResultsStep.ROW_ACT||site==ResultsStep.TAIL_ACT;case TIME_BONUS_WORD->site==ResultsStep.TIME||site==ResultsStep.CHECK_TIME;case RING_BONUS_WORD->site==ResultsStep.RING||site==ResultsStep.CHECK_RING;case SPECIAL_STAGE_BYTE->site==ResultsStep.SPECIAL;case TIME_ATTACK_MODE_BYTE->site==ResultsStep.INITIAL_ATTACK||site==ResultsStep.TAIL_ATTACK;case TIME_STONES_BYTE->site==ResultsStep.TAIL_STONES;case GOOD_FUTURE_FLAGS_BYTE->site==ResultsStep.FLAGS_COMPARE;};if(!reached)throw new IllegalStateException("Wrong reached Results read");resultsCall(i,c,c.actualCut(),c.expectedSource(),site);
        try{int value=switch(c.field()){
            case PLC_HEAD_LONG->(int)i.owners.core().section().queue().headSource();case ZONE_ACT_WORD->frame.environment().movement().gates().zoneAct();case CAMERA_X_WORD->i.cut.cameraXWord();case PLAYER_X_WORD->frame.state().motion().centreX16_16()>>>16;case GOOD_FUTURE_BYTE->i.cut.goodFutureByte();case ACT_BYTE->frame.environment().movement().gates().zoneAct()&255;case TIME_BONUS_WORD->resultsBonusValue(i).timeBonusWord();case RING_BONUS_WORD->resultsBonusValue(i).ringBonusWord();case SPECIAL_STAGE_BYTE->i.owners.core().program().specialStageByte();case TIME_ATTACK_MODE_BYTE->i.cut.timeAttackModeByte();case TIME_STONES_BYTE->i.owners.core().program().timeStonesByte();case GOOD_FUTURE_FLAGS_BYTE->resultsFlags(i);};
            switch(site){
                case HEAD->i.step=value==0?ResultsStep.WAIT_ZONE:ResultsStep.RETURN;
                case WAIT_ZONE->{if(value==0x502)beginResultsRows(i);else i.step=ResultsStep.CAMERA;}
                case CAMERA->{i.edge=(value+336)&65535;i.step=ResultsStep.PLAYER_X;}
                case PLAYER_X->{if(i.edge<value)beginResultsRows(i);else i.step=ResultsStep.RETURN;}
                case ROW_ZONE->{var s=i.expectedPool.slots().get(i.selected-32);if(value==0x502)s=s.moveWord(TILE,0x82F2);i.root=CdR12bObjectAssets.View.MAP_SPR_RESULTS.range().nativeAddress()+(value==0x502?10:0);resultsImage(i,s.moveLong(MAPPINGS,i.root));i.step=ResultsStep.ROW_GOOD;}
                case ROW_GOOD->{if(value!=0)resultsImage(i,i.expectedPool.slots().get(i.selected-32).moveLong(MAPPINGS,i.root+20));resultsRowData(i);}
                case ROW_ACT->{resultsImage(i,i.expectedPool.slots().get(i.selected-32).addByte(MAP_FRAME,value));finishResultsRow(i);}
                case TIME->{i.time=value;i.step=value==0?ResultsStep.RING:ResultsStep.TIME_STORE;}
                case RING->{i.rings=value;if(value!=0)i.step=ResultsStep.RING_STORE;else if(i.time==0)resultsNoBonus(i);else i.step=ResultsStep.CHECK_TIME;}
                case CHECK_TIME->{if(value!=0)resultsHaveBonus(i);else i.step=ResultsStep.CHECK_RING;}
                case CHECK_RING->{if(value!=0)resultsHaveBonus(i);else i.step=ResultsStep.KACHING;}
                case SPECIAL->i.step=value==0?ResultsStep.DRAW:ResultsStep.WARP;
                case INITIAL_ATTACK->i.step=value==0?ResultsStep.PLC_BIT1:ResultsStep.PLC_BIT0;
                case ZONE_OPERAND->{int act=(value+1)&255;i.nextZoneAct=(value&0xFF00)|act;if(act==3)i.nextZoneAct=((value&0xFF00)+0x100)&0xFF00;i.step=act==2?ResultsStep.PERIOD_FUTURE:ResultsStep.ZONE_STORE;}
                case TAIL_ACT->{i.actBit=(value-1)&255;i.step=(byte)i.actBit<0?ResultsStep.FLAGS_CLEAR:ResultsStep.TAIL_ATTACK;}
                case TAIL_ATTACK->i.step=value!=0?ResultsStep.RETURN:ResultsStep.TAIL_STONES;
                case TAIL_STONES->i.step=value==0x7F?ResultsStep.GOOD_SET:ResultsStep.TAIL_GOOD;
                case TAIL_GOOD->i.step=value==0?ResultsStep.RETURN:ResultsStep.GOOD_CLEAR;
                case FLAGS_COMPARE->i.step=value==3?ResultsStep.GOOD_SET:ResultsStep.RETURN;
                default->throw new IllegalStateException("Unowned Results read transition");}
            return value;
        }finally{i.operation=null;}
    }
    private void resultsBonus(CdResults.BonusStoreCall c){var i=currentResults();var site=switch(c.field()){case UPDATE_HUD_BONUS_BYTE->ResultsStep.UPDATE;case TIME_BONUS_WORD->ResultsStep.TIME_STORE;case RING_BONUS_WORD->ResultsStep.RING_STORE;};resultsCall(i,c,c.actualCut(),c.expectedSource(),site);
        try{int value=site==ResultsStep.UPDATE?1:((site==ResultsStep.TIME_STORE?i.time:i.rings)-100)&65535;if(c.value()!=value)throw new IllegalArgumentException("Wrong Results bonus subtract");var b=resultsBonusValue(i);var g=i.cut.globals();var bonus=new CdLevelHud.BonusValues(site==ResultsStep.TIME_STORE?value:b.timeBonusWord(),site==ResultsStep.RING_STORE?value:b.ringBonusWord(),site==ResultsStep.UPDATE?1:b.updateHudBonusByte());resultsPublish(i,c,withGlobals(i.cut,endGlobals(g,g.contact(),Optional.of(bonus))),null,-1);if(site==ResultsStep.UPDATE)i.step=ResultsStep.TIME;else{i.points+=10;i.step=site==ResultsStep.TIME_STORE?ResultsStep.RING:ResultsStep.CHECK_TIME;}
        }finally{i.operation=null;}
    }
    private void resultsPoints(CdResults.PointsCall c){var i=currentResults();resultsCall(i,c,c.actualCut(),c.expectedSource(),ResultsStep.POINTS);
        try{if(c.points()!=i.points||i.points!=10&&i.points!=20)throw new IllegalArgumentException("Not original Results subtract points");var g=i.cut.globals();var r=g.ring();var score=g.score();var out=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()),c.points());var w=out.world();var ring=new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());var next=withGlobals(i.cut,globals(g,ring,new CdOpeningObjectRuntime.ScoreGlobals(w.score(),w.nextLifeScore(),w.updateHudScore())));var ledger=new ArrayList<>(next.sounds());out.command().ifPresent(command->ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(command))));resultsPublish(i,c,withSounds(next,ledger),null,-1);i.step=ResultsStep.DRAW;
        }finally{i.operation=null;}
    }
    private void resultsPlc(CdResults.PlcCall c){var i=currentResults();resultsCall(i,c,c.actualCut(),c.expectedSource(),ResultsStep.PLC);
        try{if(c.nativeId()!=0x10||pendingResultsPlc!=null)throw new IllegalArgumentException("Not Results cue10");var p=new ResultsPlcSubmission(i,c);pendingResultsPlc=p;plcPort=true;
            try{p.validate();ports.submitResultsPlc(p);if(!p.adopted)throw new IllegalStateException("Missing Results cue10 receipt");}finally{pendingResultsPlc=null;plcPort=false;}
            refreshResults(i);resultsImage(i,resultsSource(i).addByte(ROUTINE,2));i.step=ResultsStep.HEAD;
        }finally{i.operation=null;}
    }
    private void resultsSound(CdResults.SoundCall c){var i=currentResults();var site=switch(c.sound()){case FM_TALLY->ResultsStep.TALLY;case DRIVER_KACHING->ResultsStep.KACHING;case FM_SSWARP->ResultsStep.WARP;};resultsCall(i,c,c.actualCut(),c.expectedSource(),site);
        try{var ledger=new ArrayList<>(i.cut.sounds());ledger.add(new SonicCdGameModule.PastResultsSound(c.sound()));resultsPublish(i,c,withSounds(i.cut,ledger),null,-1);if(site==ResultsStep.KACHING&&resultsSource(i).word(RESULTS_TIMER_WORD)<45)resultsImage(i,resultsSource(i).moveWord(RESULTS_TIMER_WORD,45));i.step=site==ResultsStep.WARP?ResultsStep.DRAW:ResultsStep.POINTS;
        }finally{i.operation=null;}
    }
    private static Cut resultsFlagsCut(Cut c,int flags){return new Cut(c.globals(),c.level(),c.goodFutureByte(),OptionalInt.of(flags),c.reservedSlots(),c.cpuSat(),c.completedCpu(),c.sounds(),c.cameraXWord(),c.cameraYWord(),c.timeAttackModeByte(),c.layerByte());}
    private void resultsProgramme(CdResults.ProgrammeStoreCall c){var i=currentResults();var site=switch(c.field()){
        case LEVEL_RESTART_WORD->ResultsStep.PROGRAMME;case SPAWN_MODE_BYTE->ResultsStep.SPAWN;case SECTION_ID_WORD->ResultsStep.SECTION;case FLOWER_COUNT_LONG->ResultsStep.FLOWERS;case UNKNOWN_LEVEL_BYTE->ResultsStep.UNKNOWN;case PROJ_DESTROYED_BYTE->ResultsStep.PROJ;case CHECKPOINT_BYTE->ResultsStep.CHECKPOINT;case PLC_LOAD_FLAGS_CLEAR_BIT0->ResultsStep.PLC_BIT0;case PLC_LOAD_FLAGS_CLEAR_BIT1->ResultsStep.PLC_BIT1;case TIME_ZONE_BYTE->i.step==ResultsStep.PERIOD_FUTURE?ResultsStep.PERIOD_FUTURE:ResultsStep.PERIOD_PRESENT;case ZONE_ACT_WORD->ResultsStep.ZONE_STORE;case GOOD_FUTURE_BYTE->i.step==ResultsStep.GOOD_CLEAR?ResultsStep.GOOD_CLEAR:ResultsStep.GOOD_SET;case GOOD_FUTURE_FLAGS_CLEAR_BYTE->ResultsStep.FLAGS_CLEAR;case GOOD_FUTURE_FLAGS_SET_BIT->ResultsStep.FLAGS_SET;};resultsCall(i,c,c.actualCut(),c.expectedSource(),site);
        try{int value=switch(site){case PROGRAMME->2;case PLC_BIT1,PERIOD_PRESENT,GOOD_SET->1;case PERIOD_FUTURE->2;case ZONE_STORE->i.nextZoneAct;case FLAGS_SET->i.actBit&7;default->0;};if(c.value()!=value)throw new IllegalArgumentException("Wrong reached Results programme operand");var next=i.cut;var l=next.level();var g=next.globals();CdPlayerFrameRuntime.ObjectSolidPublication ready=null;int section=-1;
            switch(c.field()){
                case LEVEL_RESTART_WORD->next=endLevel(next,new CdLevelGlobals(2,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
                case SPAWN_MODE_BYTE->next=endLevel(next,new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),0,l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
                case SECTION_ID_WORD->section=0;
                case FLOWER_COUNT_LONG->next=endLevel(next,new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),0,0,l.powerup(),l.timeOver()));
                case UNKNOWN_LEVEL_BYTE->{var t=g.tail().orElseThrow(()->stop(Boundary.RESULTS_TAIL,i.whole,c.field().ordinal()));var tail=new SonicCdAct2PastWorldHost.Tail(t.player2ControlsWord(),t.player2BoredWord(),0,t.demoS1IndexWord(),t.paletteFadeInfoWord());next=withGlobals(next,new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),Optional.of(tail),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
                case PROJ_DESTROYED_BYTE->next=withGlobals(next,new SonicCdAct2PastWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),0,g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));
                case CHECKPOINT_BYTE->next=endLevel(next,new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),0,l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
                case PLC_LOAD_FLAGS_CLEAR_BIT0,PLC_LOAD_FLAGS_CLEAR_BIT1->next=endLevel(next,new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags()&~(1<<value),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()));
                case TIME_ZONE_BYTE,ZONE_ACT_WORD->ready=frame.prepareObjectResultsProgrammeGates(i.actor.state(),i.shared.environment(),c.field(),value);
                case GOOD_FUTURE_BYTE->next=withGoodFuture(next,value);
                case GOOD_FUTURE_FLAGS_CLEAR_BYTE->next=resultsFlagsCut(next,0);
                case GOOD_FUTURE_FLAGS_SET_BIT->next=resultsFlagsCut(next,resultsFlags(i)|(1<<value));
            }
            resultsPublish(i,c,next,ready,section);i.step=switch(site){case PROGRAMME->ResultsStep.SPAWN;case SPAWN->ResultsStep.SECTION;case SECTION->ResultsStep.FLOWERS;case FLOWERS->ResultsStep.UNKNOWN;case UNKNOWN->ResultsStep.PROJ;case PROJ->ResultsStep.CHECKPOINT;case CHECKPOINT->ResultsStep.INITIAL_ATTACK;case PLC_BIT0->ResultsStep.PLC_BIT1;case PLC_BIT1->ResultsStep.PERIOD_PRESENT;case PERIOD_PRESENT->ResultsStep.ZONE_OPERAND;case PERIOD_FUTURE->ResultsStep.ZONE_STORE;case ZONE_STORE->ResultsStep.RESET;case GOOD_CLEAR->ResultsStep.FLAGS_SET;case FLAGS_SET->ResultsStep.FLAGS_COMPARE;case GOOD_SET,FLAGS_CLEAR->ResultsStep.RETURN;default->throw new IllegalStateException("Unknown Results programme transition");};
        }finally{i.operation=null;}
    }
    private void resultsAction(CdResults.ProgrammeActionCall c){var i=currentResults();var site=c.action()==CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS?ResultsStep.RESET:ResultsStep.FADE;resultsCall(i,c,c.actualCut(),c.expectedSource(),site);
        try{if(site==ResultsStep.RESET){byte[] bytes=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];bytes[0]=bytes[1]=1;var saved=CdAssetBytes.copyOf(bytes);var s=i.status;var status=new CdObjectPlacementRuntime.Status(s.routine(),s.previousChunkWord(),s.rightRecordIndex(),s.leftRecordIndex(),1,1,s.nullRightByteOffset(),s.nullLeftByteOffset());resultsVerify(i,false);placement.resetSavedObjectFlags();i.saved=saved;i.status=status;resultsVerify(i,false);i.step=ResultsStep.FADE;}
            else{var ledger=new ArrayList<>(i.cut.sounds());ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA)));resultsPublish(i,c,withSounds(i.cut,ledger),null,-1);i.step=ResultsStep.DRAW;}
        }finally{i.operation=null;}
    }
    private void resultsDraw(CdResults.DrawCall c){var i=currentResults();resultsCall(i,c,c.actualCut(),c.expectedSource(),ResultsStep.DRAW);if(i.drawn)throw new IllegalStateException("Repeated Results Draw");i.drawBefore=resultsSource(i);resultsImage(i,i.drawBefore.moveByte(SPRITE_FLAGS,i.drawBefore.spriteFlags()&127));
        var old=i.owners;
        try{drawDynamic(Kind.RESULTS,placement.pool(),c.expectedSource().identity(),()->resultsInvocation==i&&i.operation==c&&!i.drawn);resultsPool(i,false);requireFrame(i.actor,i.shared);requireCut(i.cut);
            // Only actual queue BCLR7 publication replaced the complete Core/Host references.
            refreshResults(i);if(!old.core().equals(i.owners.core())||old.programme()!=i.owners.programme())throw new IllegalStateException("Results Draw changed Core/module values");i.drawn=true;i.step=i.entry.routine()==8?ResultsStep.TAIL_ACT:ResultsStep.RETURN;
        }catch(RuntimeException|Error original){
            try{resultsPool(i,true);requireFrame(i.actor,i.shared);requireCut(i.cut);var actual=resultsOwners(i,null);
                if(!old.core().equals(actual.core())||old.programme()!=actual.programme())throw new IllegalStateException("Failed Results Draw changed Core/module values");
                i.owners=actual; // Verifier fence only after actual queue publication; no store or continuation.
            }catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;
        }finally{i.operation=null;}
    }
    // END R12B Results actual private World binding.
    // BEGIN R12B Door Splash original private World receipts.
    // N1ecc5f3a Spin Tunnel/Main (Door).asm:135-177; R12B Init map21D652, Main animation21D63A, tile selectorD=0.
    private final class SplashInvocation {
        final CdTunnelDoorSplash producer;final int whole,entryRoutine;
        final CdPlayerFrameRuntime.ActorState actor;final CdPlayerFrameRuntime.SharedState shared;
        CdObjectPlacementRuntime.Snapshot closed;Cut cut;SplashOperation operation;long ordinal;boolean deleted;
        SplashInvocation(int whole){producer=doorSplash;this.whole=whole;closed=placement.capture();cut=read();entryRoutine=closed.pool().slots().get(whole-32).routine();actor=frame.captureActor();shared=frame.captureShared();}
    }
    private static final class SplashOperation {
        final Object original;final CdObjectPlacementRuntime.Snapshot before;final Cut cut;final boolean publication;
        CdObjectPool.Snapshot expected;Cut next;boolean deleted,prepared,used,after;
        SplashOperation(Object original,CdObjectPlacementRuntime.Snapshot before,Cut cut,boolean publication){this.original=original;this.before=before;this.cut=cut;this.publication=publication;expected=before.pool();next=cut;}
    }
    private SplashInvocation splashCurrent(){
        requireKind(Kind.DOOR_SPLASH);routeObjects();var i=splashInvocation;
        if(i==null||i.producer!=doorSplash||i.whole!=dispatch.whole||plcPort)throw new IllegalStateException("No private original Door Splash invocation");
        requireFrame(i.actor,i.shared);return i;
    }
    private SplashOperation splashOperation(Object call,boolean completed){var i=splashCurrent();var op=i.operation;
        if(op==null||op.original!=call||!doorSplash.isCurrentCall(call)||doorSplash.isCurrentCallCompleted(call)!=completed)
            throw new IllegalStateException("Cloned, retired or wrong-phase original Door Splash operation");return op;
    }
    private SplashOperation splashBegin(Object call,CdObjectPlacementRuntime.Snapshot before,CdObjectState image,boolean publication){var i=splashCurrent();
        if(i.operation!=null||!doorSplash.isCurrentCall(call)||doorSplash.isCurrentCallCompleted(call)||!dispatch.consumed.add(call))
            throw new IllegalStateException("Missing next original Door Splash producer operation");
        requireCut(i.cut);requirePlacement(i.closed);placementCut(before);source(image);
        var op=new SplashOperation(call,before,i.cut,publication);i.operation=op;return op;
    }
    private void splashPublication(CdTunnelDoorSplash.Publication call){var op=splashBegin(call,call.cut(),call.source(),true);var i=splashInvocation;
        if(call.ordinal()!=i.ordinal+1||!call.source().equals(call.before()))throw new IllegalArgumentException("Door Splash publication ordinal/source preimage");
        if(call.output()==null){if(call.kind()!=CdTunnelDoorSplash.StoreKind.DELETE)throw new IllegalArgumentException("Door Splash missing native output");op.deleted=true;}
        else{if(call.kind()==CdTunnelDoorSplash.StoreKind.DELETE)throw new IllegalArgumentException("Door Splash Delete supplied an image");requireIdentity(call.output().identity());
            op.expected=CdObjectPool.overlay(call.cut().pool(),List.of(call.output()));}
    }
    private void splashBeforeCall(Object call){switch(call){
        case CdTunnelDoorSplash.DrawCall c->{var op=splashBegin(c,c.cut(),c.source(),false);op.expected=CdObjectPool.overlay(c.cut().pool(),List.of(c.source().moveByte(SPRITE_FLAGS,c.source().spriteFlags()&127)));}
        case CdTunnelDoorSplash.FmCall c->{var op=splashBegin(c,c.cut(),c.source(),false);var sounds=new ArrayList<>(op.cut.sounds());sounds.add(new SonicCdGameModule.PastTunnelDoorSplashFm(c.sound()));op.next=withSounds(op.cut,sounds);}
        default->throw new IllegalArgumentException("Unknown original Door Splash call");
    }}
    private void splashPrepared(Object call){var op=splashOperation(call,false);var i=splashInvocation;
        if(op.prepared||op.after)throw new IllegalStateException("Repeated Door Splash preparation");requireFrame(i.actor,i.shared);requireCut(op.cut);requirePlacement(op.before);op.prepared=true;if(op.publication)op.used=true;
    }
    private void splashUse(Object call){var op=splashOperation(call,false);var i=splashInvocation;
        if(!op.prepared||op.publication||op.used||op.after)throw new IllegalStateException("Repeated or unprepared Door Splash port");
        requireFrame(i.actor,i.shared);requireCut(op.cut);requirePlacement(op.before);op.used=true;
    }
    private boolean splashPoolMatches(CdObjectPlacementRuntime.Snapshot before,CdObjectPool.Snapshot expected,int zeroWhole){
        placement.validateRestore(before);var actual=placement.capture();if(!before.status().equals(actual.status())||!same(before.savedFlags(),actual.savedFlags()))return false;
        for(int n=0;n<96;n++){
            var image=actual.pool().slots().get(n);if(n+32==zeroWhole){if(!Arrays.equals(new byte[64],image.copyBytes()))return false;}
            else if(!expected.slots().get(n).equals(image))return false;
        }return true;
    }
    private static boolean splashCutMatches(Cut a,Cut b){return a.globals().equals(b.globals())&&a.level().equals(b.level())
            &&a.goodFutureByte()==b.goodFutureByte()&&a.goodFutureFlags().equals(b.goodFutureFlags())&&same(a.reservedSlots(),b.reservedSlots())
            &&same(a.cpuSat(),b.cpuSat())&&a.completedCpu().equals(b.completedCpu())&&a.sounds().equals(b.sounds())
            &&a.cameraXWord()==b.cameraXWord()&&a.cameraYWord()==b.cameraYWord()&&a.timeAttackModeByte()==b.timeAttackModeByte()&&a.layerByte().equals(b.layerByte());}
    private void splashVerify(SplashInvocation i,SplashOperation op,boolean completed){
        guard();routeObjects();requireFrame(i.actor,i.shared);var cut=read();
        boolean after=splashPoolMatches(op.before,op.expected,op.deleted?i.whole:-1)&&splashCutMatches(op.next,cut);
        boolean before=!completed&&splashPoolMatches(op.before,op.before.pool(),-1)&&splashCutMatches(op.cut,cut);
        if(!after&&(completed||!before))throw new IllegalStateException("Door Splash changed full96/status/saved/Frame/reserved/CPU/sound nonwrites");
        if(completed&&!op.used)throw new IllegalStateException("Door Splash omitted actual callback consumption");
    }
    private void splashAfter(Object call){var op=splashOperation(call,true);var i=splashInvocation;
        if(!op.prepared||op.after)throw new IllegalStateException("Door Splash omitted original preparation");splashVerify(i,op,true);op.after=true;
    }
    private void splashCompleted(Object call){var op=splashOperation(call,true);var i=splashInvocation;
        if(!op.after)throw new IllegalStateException("Door Splash omitted original completion");splashVerify(i,op,true);
        if(op.publication)i.ordinal++;if(op.deleted)i.deleted=true;i.closed=placement.capture();i.cut=op.next;i.operation=null;checkpoint();
    }
    private void splashFailure(SplashInvocation i,Throwable original){try{
        guard();routeObjects();requireFrame(i.actor,i.shared);
        if(i.operation!=null)splashVerify(i,i.operation,i.operation.after);else{requirePlacement(i.closed);requireCut(i.cut);}
    }catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}}
    private void runDoorSplash(int whole){requireKind(Kind.DOOR_SPLASH);routeObjects();if(splashInvocation!=null)throw new IllegalStateException("Nested Door Splash dispatch");
        var i=new SplashInvocation(whole);splashInvocation=i;
        try{var result=doorSplash.run(whole);splashCurrent();requirePlacement(i.closed);requireCut(i.cut);
            if(i.operation!=null||!result.state().equals(placement.pool().state(whole))||result.deleted()!=i.deleted||i.deleted!=(i.entryRoutine==4)||dispatch.drawn==i.deleted)
                throw new IllegalStateException("Door Splash omitted native store/call/Draw completion");
        }catch(RuntimeException|Error original){splashFailure(i,original);throw original;}finally{splashInvocation=null;}
    }
    // END R12B Door Splash original private World receipts.

}
