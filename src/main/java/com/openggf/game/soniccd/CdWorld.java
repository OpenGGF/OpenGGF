package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.r4.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import java.util.*;
import java.io.IOException;
import static com.openggf.game.soniccd.CdOrdinaryLoop.Cut;

/** One source-bound standard CD World lifetime. Original Core accesses alone enter service.
 * Binding classes contain authentic primary/zone joins, never independent schedulers or owners.
 * Completed values are inert; no public constructor/token/actor-write API is introduced.
 */
final class CdWorld implements SonicCdAct3FutureLoadingVisit.OrdinaryOwner,
        SonicCdR31aLoadingVisit.OrdinaryOwner {
 private final CdPlayerFrameRuntime frame;
 private final Binding binding;
 private final CdOrdinaryLoop loop;
 private final Thread thread=Thread.currentThread();
 private Cut cut;
 private boolean running,closed;
 private abstract class Binding {
  abstract void idle(); abstract void require(); abstract Object scope();
  abstract CdOrdinaryLoop.Ports ports(); abstract void clear(); abstract void discard();
  abstract void playerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete);
 }
 CdWorld(SonicCdAct3FutureWorldHost.Authority authority,SonicCdAct3FutureLoadingVisit core,
         SonicCdAct3FutureWorldHost host,CdPlayerFrameRuntime frame,CdR13FutureAssets assets,
         CdR13SectionArt art,CdR13ObjectAssets metadata,CdR13PlayerCollisionAssets collision){
  this.frame=Objects.requireNonNull(frame);
  binding=new R13(authority,core,host,frame,assets,art,metadata,collision);
  loop=new CdOrdinaryLoop(frame,frame.stageSource(),binding.ports());
  frame.bindArtPublication(binding::playerArt);
 }
 CdWorld(SonicCdR31aWorldHost.Authority authority,SonicCdR31aLoadingVisit core,
         SonicCdR31aWorldHost host,CdPlayerFrameRuntime frame,CdR31aAssets assets,
         CdR31aSectionArt art,CdR31aObjectAssets metadata)throws IOException{
  this.frame=Objects.requireNonNull(frame);
  binding=new R31(authority,core,host,frame,assets,art,metadata);
  loop=new CdOrdinaryLoop(frame,frame.stageSource(),binding.ports());
  frame.bindArtPublication(binding::playerArt);
 }
 CdWorld(SonicCdGameModule module,CdPlayerFrameRuntime frame,CdR11aAssets assets,CdR11aSectionArt art,CdObjectPlacementRuntime placement)throws IOException{
  this.frame=Objects.requireNonNull(frame);module.requireOriginalR11WorldConstruction(frame,assets,art,placement);
  binding=new R11(module,assets,art,placement);loop=new CdOrdinaryLoop(frame,frame.stageSource(),binding.ports());frame.bindArtPublication(binding::playerArt);
 }
 CdWorld(SonicCdGameModule module,CdPlayerFrameRuntime frame,CdLevelLoadingSource source,CdR11aSectionArt art,CdObjectPlacementRuntime placement)throws IOException{
  this.frame=Objects.requireNonNull(frame);module.requireOriginalProgrammeWorldConstruction(frame,source,art,placement);
  binding=new R11(module,source,art,placement);loop=new CdOrdinaryLoop(frame,frame.stageSource(),binding.ports());frame.bindArtPublication(binding::playerArt);
 }
 void runOriginalR11(int held,SonicCdGameModule.OriginalR11Access access){
  if(Thread.currentThread()!=thread||closed||running||frame.closed())throw new IllegalStateException("Original R11 World retired/reentry");
  if((held&~255)!=0)throw new IllegalArgumentException("Held BYTE");if(!(binding instanceof R11 b))throw new IllegalArgumentException("Foreign original programme");
  Objects.requireNonNull(access).requireOwner(this);b.active=access;b.serviceGeneration=Math.incrementExact(b.serviceGeneration);service(held);
 }
 private void idle(){
  if(Thread.currentThread()!=thread||closed||frame.closed()||running)
   throw new IllegalStateException("CD World retired, foreign thread or reentry");
  binding.idle();
 }
 private void preflight(int held){idle();if((held&~255)!=0)throw new IllegalArgumentException("Native held BYTE required");}
 @Override public void run(int held,SonicCdAct3FutureLoadingVisit.OrdinaryAccess access){
  preflight(held);if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign primary/source strategy");
  Objects.requireNonNull(access).requireOwner(this);b.active=access;service(held);
 }
 @Override public void run(int held,SonicCdR31aLoadingVisit.OrdinaryAccess access){
  preflight(held);if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign primary/source strategy");
  Objects.requireNonNull(access).requireOwner(this);b.active=access;service(held);
 }
 private void service(int held){
  running=true;
  try{binding.require();loop.run(held);}
  catch(RuntimeException|Error original){closed=true;try{binding.discard();}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
  finally{cut=null;binding.clear();running=false;}
 }
 Object actualScope(){binding.require();if(!running)throw new IllegalStateException("No original World invocation");return binding.scope();}
 boolean isCurrentScope(Object scope){return !closed&&running&&Thread.currentThread()==thread&&scope!=null&&binding.scope()==scope&&cut!=null;}
 boolean isOrdinaryRunning(){return running;}
 void requireCut(Cut expected){
  if(Thread.currentThread()!=thread||closed||!running||cut!=expected)throw new IllegalStateException("No actual CD World "+expected);
  binding.require();
 }
 void requireObjectPass(SonicCdAct3FutureObjectPass pass){requireCut(Cut.RUN_OBJECTS);if(!(binding instanceof R13 b)||b.objects!=pass)throw new IllegalArgumentException("Foreign physical pass");}
 void requirePrimary(SonicCdR31aObjectPass.PrimarySubmission original){requireCut(Cut.RUN_OBJECTS);if(!(binding instanceof R31 b)||original.owner()!=b.objects)throw new IllegalArgumentException("Foreign original primary producer");original.validateBefore();}
 void requirePlace(CdPlayerFrameRuntime.R31PlaceCall original){requireCut(Cut.RUN_OBJECTS);if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign PlaceBlock programme");b.objects.requirePlace(original);}
 void requireOldExitOwner(SonicCdAct3FutureLoadingVisit core){requireCut(Cut.POST_OBJECT_CAMERA);if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign transfer programme");b.requireOldExitOwner(core);}
 void abandonOldExitWalk(SonicCdAct3FutureLoadingVisit core){requireOldExitOwner(core);((R13)binding).abandonOldExitWalk(core);}
 void requireLevelStartOwner(SonicCdR31aLoadingVisit core){requireCut(Cut.POST_OBJECT_CAMERA);if(!(binding instanceof R31 b)||core!=b.core||!b.walkReturned)throw new IllegalStateException("No original completed R31 walk transfer");}
 void abandonLevelStartWalk(SonicCdR31aLoadingVisit core){requireLevelStartOwner(core);var b=(R31)binding;b.objects.abandonCompletedWalk();b.walkReturned=false;}
 void requireOriginalTtDrownTimerWrite(CdPlayerFrameRuntime expected,int value){
  if(expected!=frame||!(binding instanceof R11 b))throw new IllegalStateException("Foreign original TT scalar owner");b.requireDrownTimerWrite(value);
 }
 void requireOriginalTtWaterAirFm(SonicCdGameModule module,CdR4PlayerBubbles.FmCall original){
  if(!(binding instanceof R11 b)||b.module!=module)throw new IllegalStateException("Foreign original TT FM World");b.requireAirFm(original);
 }
 void requireOriginalR11LevelStartOwner(SonicCdGameModule module){requireCut(Cut.POST_OBJECT_CAMERA);if(!(binding instanceof R11 b)||b.module!=module||!b.walkReturned||b.gameOverScope!=null||b.closureScope!=null)throw new IllegalStateException("No actual completed R11 LevelStart walk");}
 void abandonOriginalR11LevelStartWalk(SonicCdGameModule module){requireOriginalR11LevelStartOwner(module);var b=(R11)binding;if(b.queue==null)throw new IllegalStateException("No current R11 Draw queue");b.queue.close();b.queue=null;b.walkReturned=false;}
 void originalR11QueueCleared(SonicCdGameModule.OriginalR11ClearCall call){if(closed||running||!(binding instanceof R11 b))throw new IllegalStateException("No original R11 loading queue clear");call.requireDrawQueueOwner(b.module,this);if(b.queue!=null||b.gameOverScope!=null||b.closureScope!=null)throw new IllegalStateException("An R11 walk is still active");b.cpu=Optional.empty();}
 SonicCdAct3FutureLoadingVisit.Snapshot captureR13(){idle();if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign capture source");return b.core.capture();}
 SonicCdR31aLoadingVisit.Snapshot captureR31(){idle();if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign capture source");return b.core.capture();}
 void validateRestore(SonicCdAct3FutureLoadingVisit.Snapshot snapshot){idle();if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign capture source");b.core.validateRestore(snapshot);}
 void validateRestore(SonicCdR31aLoadingVisit.Snapshot snapshot){idle();if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign capture source");b.core.validateRestore(snapshot);}
 void restore(SonicCdAct3FutureLoadingVisit.Snapshot snapshot){validateRestore(snapshot);((R13)binding).core.restore(snapshot);}
 void restore(SonicCdR31aLoadingVisit.Snapshot snapshot){validateRestore(snapshot);((R31)binding).core.restore(snapshot);}
 void draw(){idle();if(binding instanceof R13 b)b.host.draw();else if(binding instanceof R31 b)b.host.draw();else ((R11)binding).module.drawOriginalR11World();}
 /** Idle lifecycle guards also cover loading snapshots; active Core accesses are never captured. */
 void requireLifecycleIdle(){idle();}
 /** Retirement also runs after the owning session detached or the Frame failed. Validate
  * retained exact relations, thread and render-idle before cleanup; never require a new session. */
 void close(){
  if(Thread.currentThread()!=thread||running)throw new IllegalStateException("World close thread/reentry");
  if(binding instanceof R13 b){
   b.host.requireRenderIdle();if(frame!=b.frame||b.core.assets()!=b.assets||!frame.stageSource().ownsAct3Future(b.assets)||!b.art.ownsAssets(b.assets))throw new IllegalStateException("Retiring foreign R13 pair");
  }else if(binding instanceof R31 b){b.host.requireRenderIdle();if(b.core.assets()!=b.assets||!frame.stageSource().ownsR31aAssets(b.assets)||!b.art.ownsAssets(b.assets))throw new IllegalStateException("Retiring foreign R31 pair");}else{var b=(R11)binding;b.module.requireOriginalR11WorldRetiring(this,frame,b.assets);}
  Throwable original=null;
  try{binding.discard();}catch(RuntimeException|Error fault){original=fault;}
  try{if(binding instanceof R13 b)b.host.close();else if(binding instanceof R31 b)b.host.close();}
  catch(RuntimeException|Error fault){if(original==null)original=fault;else if(original!=fault)original.addSuppressed(fault);}
  try{frame.close();}catch(RuntimeException|Error fault){if(original==null)original=fault;else if(original!=fault)original.addSuppressed(fault);}
  closed=true;
  if(original instanceof RuntimeException fault)throw fault;if(original instanceof Error fault)throw fault;
 }
 /** Only the object's queue and sound ledger are captured here; native values/Frame/pool
  * remain once in the original Module's complete image. No shadow save or actor lifetime. */
 /** Completed physical-service observations only. No operation/invocation or restore authority. */
 record MechanismService(int id,int whole,int entryRoutine,int returnedRoutine,int operations){}
 record ClosureService(int id,int whole,int entryRoutine,int returnedRoutine,int operations){}
 record TunnelService(int id,int whole,int entryRoutine,int returnedRoutine,int operations){}
 static final class UnownedMechanismTerrain extends UnsupportedOperationException {
  private final CdOriginalMechanismProtocol.NativeSite operation;private final CdObjectState prefix;
  private UnownedMechanismTerrain(CdOriginalMechanismProtocol.NativeSite operation,CdObjectState prefix,RuntimeException cause){super("Original R11 mechanism reached "+operation+" at whole="+prefix.identity().wholeIndex()+" with unowned changed/current collision terrain",cause);this.operation=operation;this.prefix=prefix;}
  CdOriginalMechanismProtocol.NativeSite operation(){return operation;}CdObjectState prefix(){return prefix;}
 }
 static final class UnownedTunnelDescendant extends UnsupportedOperationException{
  private final CdObjectState prefix;
  private UnownedTunnelDescendant(CdObjectState prefix){super("Original R11 $0E ObjSpinSplash body owner missing at whole="+prefix.identity().wholeIndex()+", routine="+prefix.routine()+"; Sonic Main:159-202 allocation prefix retained; Spin Tunnel/Main (Waterfall Splash).asm:7-51 remains unbound");this.prefix=prefix;}
  CdObjectState prefix(){return prefix;}
 }
 static final class R11Snapshot {
  private final CdWorld owner;private final Optional<CdObjectDrawQueue.CpuSat> cpu;private final List<SonicCdGameModule.PastSound> sounds;private final List<TunnelService> tunnelServices;private final List<MechanismService> mechanismServices;private final List<ClosureService> closureServices;
  private R11Snapshot(CdWorld owner,Optional<CdObjectDrawQueue.CpuSat> cpu,List<SonicCdGameModule.PastSound> sounds){this.owner=owner;this.cpu=cpu;this.sounds=List.copyOf(sounds);this.tunnelServices=List.copyOf(((R11)owner.binding).tunnelServices);this.mechanismServices=List.copyOf(((R11)owner.binding).mechanismServices);this.closureServices=List.copyOf(((R11)owner.binding).closureServices);}
  Optional<CdObjectDrawQueue.CpuSat> cpu(){return cpu;}
  List<SonicCdGameModule.PastSound> sounds(){return sounds;}
  List<TunnelService> tunnelServices(){return tunnelServices;}
  List<MechanismService> mechanismServices(){return mechanismServices;}
  List<ClosureService> closureServices(){return closureServices;}
 }
 Optional<CdObjectDrawQueue.CpuSat> originalR11LoadingCpu(SonicCdGameModule module){
  if(Thread.currentThread()!=thread||closed||frame.closed()||running||!(binding instanceof R11 b)||b.module!=module
      ||b.physical!=-1||b.drawing||b.draining||b.queue!=null||b.gameOverScope!=null)
   throw new IllegalStateException("No completed original R11 loading picture");
  module.requireOriginalR11LoadingPictureOwner(this);return b.cpu;
 }
 R11Snapshot captureR11(){idle();if(!(binding instanceof R11 b))throw new IllegalArgumentException("Foreign R11 capture");return new R11Snapshot(this,b.cpu,b.sounds);}
 void validateRestore(R11Snapshot snapshot){idle();if(!(binding instanceof R11 b)||snapshot==null||snapshot.owner!=this)throw new IllegalArgumentException("Foreign original R11 World capture");if(b.queue!=null)throw new IllegalStateException("Pending original physical queue");}
 void restore(R11Snapshot snapshot){validateRestore(snapshot);var b=(R11)binding;b.cpu=snapshot.cpu;b.sounds=snapshot.sounds;b.tunnelServices=snapshot.tunnelServices;b.mechanismServices=snapshot.mechanismServices;b.closureServices=snapshot.closureServices;}
 void validateR11Cpu(R11Snapshot snapshot,CdAssetBytes raw){validateRestore(snapshot);if(raw.length()!=640)throw new IllegalArgumentException("Full CPU SAT");if(snapshot.cpu.isPresent()){if(!Arrays.equals(snapshot.cpu.orElseThrow().bytes(),raw.copy()))throw new IllegalArgumentException("CPU SAT/raw capture pair differs");}else for(byte b:raw.copy())if(b!=0)throw new IllegalArgumentException("No completed DrawObjects producer for nonzero CPU bytes");}
 // This is only immutable/current geometry validation, never invocation authority.
 // Original R11 currently retains its loaded collision geometry; changed geometry needs a real
 // current-terrain owner instead of silently reusing the initial decoder or inventing known bytes.
 static void requireOriginalR11FloatBlockTerrain(CdStagePlayerSource source,CdR11aAssets assets,
         CdAssetBytes blocks,CdAssetBytes layout){
  Objects.requireNonNull(source);Objects.requireNonNull(assets);Objects.requireNonNull(blocks);Objects.requireNonNull(layout);
  if(!source.ownsPresent(assets)||!same(blocks,assets.terrain().blocks())||!same(layout,assets.terrain().layout().ram()))
   throw new IllegalArgumentException("Original FloatBlock requires its exact retained source and current loaded terrain");
 }
 /** Original Past Anton's immutable-floor relation only. This does not issue a physical
  * invocation or accept a foreign equal source/current layout as runtime authority. */
 static void requireOriginalPastAntonTerrain(CdStagePlayerSource source,CdLevelLoadingSource loading,
         CdAssetBytes blocks,CdAssetBytes layout){
  Objects.requireNonNull(source);Objects.requireNonNull(loading);Objects.requireNonNull(blocks);Objects.requireNonNull(layout);
  if(loading.file()!=CdNativeFile.R11B||!source.hasRetainedPpzProgrammeSource()||source!=loading.retainedPlayerSource()
      ||!same(blocks,source.terrain().blocks())||!same(layout,source.terrain().layout().ram()))
   throw new IllegalArgumentException("Original Past Anton requires exact retained source and complete unchanged current terrain");
 }
 /** Immutable current-geometry fence only; actual invocation stays private to World/Frame.
  * Pure probes reuse the real retained TT source until a genuine current-terrain owner exists. */
 static void requireOriginalTtBlockTerrain(CdStagePlayerSource source,CdLevelLoadingSource loading,
         CdAssetBytes blocks,CdAssetBytes layout){
  Objects.requireNonNull(source);Objects.requireNonNull(loading);Objects.requireNonNull(blocks);Objects.requireNonNull(layout);
  if(loading.file()!=CdNativeFile.R41A||source!=loading.retainedPlayerSource()
      ||!same(blocks,source.terrain().blocks())||!same(layout,source.terrain().layout().ram()))
   throw new IllegalArgumentException("Original TT Solid requires exact source and unchanged complete current geometry");
 }
 /** Comparison-only projection of the reached raw save operand; no invocation or default knowledge.
  * N1ecc5f3a Checkpoint/Main.asm:18-43. BG integer WORDs come from each independent LONG,
  * never from copied draw cameras, the primary, or an initial source load. */
 static int originalR11CheckpointSource(CdCheckpointSaveData.Source source,CdPlayerBody.State actor,
         CdPlayerBody.Environment environment,SonicCdR31aLoadingVisit.Scroll scroll,
         SonicCdR31aWorldHost.Globals globals,CdLevelGlobals level){
  var m=actor.motion();var f=scroll.foreground();var b=scroll.background();
  return switch(source){
   case SPAWN_MODE -> level.spawnMode();case PRIMARY_X -> m.centreX16_16()>>>16;case PRIMARY_Y -> m.centreY16_16()>>>16;
   case BOTTOM_BOUND -> environment.movement().bounds().bottom();
   case FG_X -> f.cameraX();case FG_Y -> f.cameraYWord();
   case BG_X -> b.cameraBgX()>>>16;case BG_Y -> b.cameraBgY()>>>16;
   case BG2_X -> b.cameraBg2X()>>>16;case BG2_Y -> b.cameraBg2Y()>>>16;
   case BG3_X -> b.cameraBg3X()>>>16;case BG3_Y -> b.cameraBg3Y()>>>16;
   case WATER_ROUTINE -> CdCheckpointLiveAux.read(globals.live(),CdCheckpointLiveAux.Field.WATER_ROUTINE);
   case WATER_HEIGHT2 -> CdCheckpointLiveAux.read(globals.live(),CdCheckpointLiveAux.Field.WATER_HEIGHT2);
   case WATER_FULLSCREEN -> CdCheckpointLiveAux.read(globals.live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
   case TIME_LONG -> CdCheckpointLiveAux.read(globals.live(),CdCheckpointLiveAux.Field.TIME_LONG);
   case MINI_SONIC -> environment.movement().gates().miniSonic();
  };
 }
 static final class OriginalR11FlowerPositionStore {
  private final R11 owner;private final CdFlower.SavedCall call;private final CdFlowerPositions before;private final Thread thread=Thread.currentThread();
  private OriginalR11FlowerPositionStore(R11 owner,CdFlower.SavedCall call,CdFlowerPositions before){this.owner=owner;this.call=call;this.before=before;}
  CdR11aAssets requirePositionSource(CdFlowerPositions actual){var op=owner.currentFlower(call);if(thread!=Thread.currentThread()||actual!=before||op.before.state.flowers()!=before||op.positionStore!=this||!op.prepared||!op.used||op.after||(call.site()!=CdFlower.SavedSite.POSITION_X&&call.site()!=CdFlower.SavedSite.POSITION_Y))throw new IllegalStateException("No actual original R11 Flower position store");return owner.assets;}
  int offset(){return call.offset();}int value(){return call.value();}
 }
 /** Private World-issued original primary occurrence. Values and captures cannot mint one. */
 public static final class OriginalR11TimeWarpEntry extends CdOriginalTimeWarpEntry {
  private final R11 owner;private final Object active;private final long generation;private final Thread thread=Thread.currentThread();
  private final R11.R11WarpCut before;private CdPlayerFrameRuntime.OriginalR11TimeWarpCall call;
  private boolean retired;private long ordinal;private CdPlayerTimeWarp.Result result;
  private OriginalR11TimeWarpEntry(R11 owner){this.owner=owner;active=owner.active;generation=owner.serviceGeneration;before=owner.new R11WarpCut();}
  public void requireFrame(CdPlayerFrameRuntime frame){owner.requireWarpEntry(this);if(frame!=owner.frameValue()||call!=null)throw new IllegalStateException("Foreign/repeated original TimeWarp Body entry");before.check();}
  public void beforeBodyPrefix(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call){owner.requireWarpEntry(this);call.requireEntry(this);if(this.call!=null)throw new IllegalStateException("Repeated original TimeWarp call");before.check();this.call=call;}
  public void beginNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call){current(call);before.nonPlayer();owner.originalWarpExpected=owner.new R11WarpCut();}
  private void current(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call){owner.requireWarpEntry(this);if(this.call!=call)throw new IllegalStateException("Foreign TimeWarp Frame call");call.requireEntry(this);}
  public void beforeNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);owner.warpBefore(call,primitive);}
  public CdReservedObjectBank.Snapshot reserved(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);return owner.warpReserved(call,primitive);}
  public void store(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);owner.warpStore(call,primitive);}
  public int scalar(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);return owner.warpScalar(call,primitive);}
  public CdTimeWarpSaveData.State saved(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);return owner.warpSaved(call,primitive);}
  public int source(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);return owner.warpSource(call,primitive);}
  public void activeStore(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);owner.warpActiveStore(call,primitive);}
  public void afterNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){current(call);owner.warpAfter(call,primitive);}
  public void failedNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive,Throwable first){current(call);owner.warpFailed(call,primitive,first);}
  public void completedNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.Result result){current(call);owner.originalWarpExpected.check();if(owner.originalWarpOperation!=null||result.restartResult().isPresent()||result.state().timer()!=owner.frameValue().environment().globals().timeWarpTimer()||result.state().direction()!=owner.frameValue().environment().movement().gates().timeWarpDirection()||result.state().timeWarp()!=owner.active.state().globals().contact().timeWarp())throw new IllegalStateException("Incomplete original TimeWarp native return");if(result.saveResult().isPresent()){var saved=result.saveResult().orElseThrow();if(saved.finalSavedState()!=owner.active.timeWarpSaved()||saved.finalPeriodByte()!=owner.frameValue().environment().movement().gates().timeZone()||saved.finalSpawnByte()!=owner.active.prefix().main().level().spawnMode()||saved.finalScrollLockWord()!=owner.active.state().scroll().foreground().scrollLock())throw new IllegalStateException("Unpublished original save observations");}this.result=result;}
 }
 /** A current shared SaveTimeWarpData occurrence, not authority from its public descriptor. */
 static final class OriginalR11TimeWarpSaveStore {
  private final CdWorld world;private final R11 owner;private final R11.R11WarpOperation op;private final CdTimeWarpSaveData.StoreCall store;
  private final Thread thread=Thread.currentThread();private boolean checked,used;
  private OriginalR11TimeWarpSaveStore(CdWorld world,R11 owner,R11.R11WarpOperation op,CdTimeWarpSaveData.StoreCall store){this.world=world;this.owner=owner;this.op=op;this.store=store;}
  private void current(){
   if(thread!=Thread.currentThread()||used||owner.warpOperation(op.frameCall,op.call,false)!=op||op.savedStore!=this||!op.used||op.call.site()!=CdPlayerTimeWarp.OriginalSite.ACTIVE_STORE||!(op.call.saveEffect() instanceof CdPlayerTimeWarp.SavedWrite saved)||saved.call()!=store||owner.active.timeWarpSaved()!=store.expectedState())throw new IllegalStateException("No current original TimeWarp saved store");
  }
  CdTimeWarpSaveData.State requireExpected(CdWorld actual,CdTimeWarpSaveData.State current){current();if(actual!=world||checked||current!=store.expectedState())throw new IllegalStateException("Foreign/duplicate warp saved owner");checked=true;return store.expectedState();}
  CdTimeWarpSaveData.State ready(){current();if(!checked)throw new IllegalStateException("Unprepared warp saved store");used=true;return store.finalState();}
 }
 private final class R11 extends Binding {
  private final SonicCdGameModule module;private final CdR11aAssets assets;private final CdR11aSectionArt art;private final CdObjectPlacementRuntime placement;
  private final CdOriginalWorldBindings programme;
  private final CdLevelLoadingSource pastSource;private final CdR11bObjectData pastObjects;
  private final CdLevelLoadingSource ttSource;private final CdProgrammeObjectSource ttObjects;private final CdR11aObjectAssets metadata;private final CdR11aStartupSource startup;private final CdObjectMappings mappings;private final List<CdObjectDrawQueue.CdSlotRef> reservedRefs;private CdObjectDrawQueue queue;private Optional<CdObjectDrawQueue.CpuSat> cpu=Optional.empty();
  private final CdPataBata enemyPataBata;
  private final CdAnton enemyAnton;
  private final CdTamabboh enemyTamabboh;
  private final CdFlower flower;private final CdTimeStars timeStars;
  private final CdRing ring;private final CdLostRing lostRing;private final CdMosqui mosqui;private final CdAnimal animal;private final CdReservedHud hud;private final CdTitleCard title;
  // Original stage-closure family: one cached kernel per handler, current private callback only.
  private CdBigRing closureBigRing;private CdGoalPost closureGoal;private CdSignpost closureSignpost;private CdResults closureResults;
  private ClosureScope closureScope;private int closureOperations;private List<ClosureService> closureServices=List.of();
  private final class ClosureScope {
   final CdStageClosureInvocation.Call original;FamilyCut expected=new FamilyCut();final int special=active.closureSpecialStage();int nextSpecial=special;boolean used;
   ClosureScope(CdStageClosureInvocation.Call c){original=c;}
   void check(){requirePhysical(physical);if(!closureCurrent(original)||active.closureSpecialStage()!=nextSpecial)throw new IllegalStateException("Expired original closure issuer/owner");expected.check(expected.placed.pool());}
  }
  private boolean closureCurrent(CdStageClosureInvocation.Call c){return switch(physicalId){case 0x14->closureBigRing.isCurrentOriginalCall(c);case 0x16->closureGoal.isCurrentOriginalCall(c);case 0x17->closureSignpost.isCurrentOriginalCall(c);case 0x3A->closureResults.isCurrentOriginalCall(c);default->false;};}
  private final CdStageClosureInvocation.Observer closureObserver=new CdStageClosureInvocation.Observer(){
   public void before(CdStageClosureInvocation.Call c){
    requirePhysical(physical);if(closureScope!=null||!closureCurrent(c)||c.completed()||c.source().identity().wholeIndex()!=physical||c.source().id()!=physicalId)throw new IllegalStateException("No original physical closure operation");
    sourceCall(c.cut().pool(),c.source(),physicalId);var actual=placement.capture();if(!actual.status().equals(c.cut().status())||!same(actual.savedFlags(),c.cut().savedFlags()))throw new IllegalStateException("Changed closure saved/status cut");closureScope=new ClosureScope(c);closureScope.check();
   }
   public void after(CdStageClosureInvocation.Call c,boolean returned){
    var op=closureScope;if(op==null||op.original!=c)throw new IllegalStateException("No closure callback scope");
    try{op.check();if(returned&&(!c.completed()||!op.used))throw new IllegalStateException("Closure primitive did not return exactly once");if(returned)closureOperations++;}finally{closureScope=null;}
   }
  };
  private <T>T closurePrimitive(Object argument,Class<T> type){var op=closureScope;if(op==null||op.used||op.original.argument()!=argument||!type.isInstance(argument))throw new IllegalStateException("Foreign/replayed closure argument");op.check();op.used=true;return type.cast(argument);}
  private void closureCheckpoint(){var op=closureScope;if(op==null||!op.used)throw new IllegalStateException("No closure primitive checkpoint");op.expected=new FamilyCut();op.nextSpecial=active.closureSpecialStage();op.check();}
  private void closureFrame(CdPlayerFrameRuntime.ObjectSolidPublication p){closureScope.expected.publish(null,p,null,List.of());closureCheckpoint();}
  private void closureGlobals(SonicCdR31aWorldHost.Globals next){closureScope.expected.publish(null,null,next,List.of());closureCheckpoint();}
  private void closureLevel(CdLevelGlobals next){var before=active.prefix().main().level();closureScope.check();active.level(before,next);closureCheckpoint();}
  private void closureSound(SonicCdGameModule.PastSound request){closureScope.expected.publish(null,null,null,List.of(request));closureCheckpoint();}
  private void closureDraw(Object argument){closurePrimitive(argument,argument.getClass());var c=closureScope.original;familyDraw(placement.pool(),c.source().identity(),()->closureCurrent(c));closureCheckpoint();}
  private void closurePlc(Object argument,int id){
   closurePrimitive(argument,argument.getClass());var before=closureScope.expected;
   try{active.append(id);closureCheckpoint();}
   catch(RuntimeException|Error original){
    try{var now=active.state();var p=active.prefix();if(now!=before.state||frame.state()!=before.actor||frame.environment()!=before.environment||frame.display()!=before.display||frame.artState()!=before.artImage||frame.vIntClock()!=before.clock||frame.paused()!=before.paused||sounds!=before.oldSounds||queue!=before.originalQueue||cpu!=before.originalCpu
       ||p.main()!=before.prefix.main()||p.goodFuture()!=before.prefix.goodFuture()||p.gameMode()!=before.prefix.gameMode()||p.goodFutureFlags()!=before.prefix.goodFutureFlags()||p.ring()!=before.prefix.ring()||p.score()!=before.prefix.score()||p.normalPalette()!=before.prefix.normalPalette()||p.fadePalette()!=before.prefix.fadePalette()||!p.calls().equals(before.prefix.calls())||p.blockDefinitions()!=before.prefix.blockDefinitions())throw new IllegalStateException("PLC fault changed an unowned closure partition");
     art.validateState(p.section());var placed=placement.capture();if(!placed.status().equals(before.placed.status())||!placed.pool().slots().equals(before.placed.pool().slots())||!same(placed.savedFlags(),before.placed.savedFlags()))throw new IllegalStateException("PLC fault changed closure placement");closureCheckpoint();
    }catch(RuntimeException|Error verify){if(verify!=original)original.addSuppressed(verify);}throw original;
   }
  }
  private void closureDestLeft(int value){var s=active.state().scroll();var f=s.foreground();var next=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),value,f.bottomBoundShift(),f.scrollLock());closureScope.check();active.scroll(s,new SonicCdR31aLoadingVisit.Scroll(next,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));closureCheckpoint();}
  private void closureLock(){var s=active.state().scroll();var f=s.foreground();var next=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),(f.scrollLock()&255)|0x100);closureScope.check();active.scroll(s,new SonicCdR31aLoadingVisit.Scroll(next,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));closureCheckpoint();}
  private int closureBigRead(CdBigRing.ReadCall c){closurePrimitive(c,CdBigRing.ReadCall.class);var m=frame.state().motion();var e=frame.environment();return switch(c.field()){
   case RINGS_WORD->active.state().globals().ring().rings();case TIME_STONES_BYTE->active.closureTimeStones();case TIME_ATTACK_BYTE->e.globals().timeAttackMode();
   case PLAYER_X_RADIUS_BYTE->m.xRadius();case PLAYER_Y_RADIUS_BYTE->m.yRadius();case PLAYER_X_WORD->m.centreX16_16()>>>16;case PLAYER_Y_WORD->m.centreY16_16()>>>16;
   case CAMERA_X_WORD->camera().xWord();case TIME_ZONE_BYTE->e.movement().gates().timeZone();case TIME_WARP_DIRECTION_BYTE->e.movement().gates().timeWarpDirection();};}
  private void closureBigPlayer(CdBigRing.PlayerStore c){closurePrimitive(c,CdBigRing.PlayerStore.class);closureFrame(frame.prepareObjectBigRingPlayerStore(frame.state(),frame.environment(),c));}
  private void closureBigSpecial(CdBigRing.SpecialStageCall c){closurePrimitive(c,CdBigRing.SpecialStageCall.class);active.closureSpecialStage(1);closureCheckpoint();}
  private void closureBigLock(CdBigRing.ScrollLockHighByteOne c){closurePrimitive(c,CdBigRing.ScrollLockHighByteOne.class);closureLock();}
  private void closureBigSound(CdBigRing.SoundCall c){closurePrimitive(c,CdBigRing.SoundCall.class);closureSound(new SonicCdGameModule.PastBigRingFm());}
  private int closureGoalRead(CdGoalPost.ReadCall c){closurePrimitive(c,CdGoalPost.ReadCall.class);return c.field()==CdGoalPost.PlayerField.X_WORD?frame.state().motion().centreX16_16()>>>16:frame.state().motion().centreY16_16()>>>16;}
  private void closureGoalReached(CdGoalPost.GoalReachedCall c){
   closurePrimitive(c,CdGoalPost.GoalReachedCall.class);closureFrame(frame.prepareObjectSignpostStore(frame.state(),frame.environment(),CdSignpost.StoreField.LEFT_BOUND_WORD,c.cameraXWord()));closureDestLeft(c.cameraXWord());closureFrame(frame.prepareObjectGoalPostTimeWarpReset(frame.state(),frame.environment()));
   var g=active.state().globals();var contact=g.contact();closureGlobals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),new CdPlayerBody.ContactGlobals(0,contact.shield(),contact.combineRing()),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));
  }
  private int closureSignRead(CdSignpost.ReadCall c){closurePrimitive(c,CdSignpost.ReadCall.class);var e=frame.environment();var m=frame.state().motion();int time;return switch(c.field()){
   case ZONE_BYTE->e.movement().gates().zoneAct()>>>8;case ZONE_ACT_WORD->e.movement().gates().zoneAct();case TIME_ZONE_BYTE->e.movement().gates().timeZone();
   case PLAYER_Y_WORD->m.centreY16_16()>>>16;case PLAYER_X_WORD->m.centreX16_16()>>>16;case CAMERA_X_WORD->camera().xWord();case RINGS_WORD->active.state().globals().ring().rings();
   case TIME_MINUTES_BYTE->(CdCheckpointLiveAux.read(active.state().globals().live(),CdCheckpointLiveAux.Field.TIME_LONG)>>>16)&255;
   case TIME_SECONDS_BYTE->(CdCheckpointLiveAux.read(active.state().globals().live(),CdCheckpointLiveAux.Field.TIME_LONG)>>>8)&255;};}
  private CdLevelGlobals closureLevelValue(CdLevelGlobals l,Integer restart,Integer hud,Integer checkpoint,Integer spawn,Integer plc,boolean flowers){return new CdLevelGlobals(restart==null?l.levelRestart():restart,hud==null?l.updateHudTime():hud,checkpoint==null?l.checkpoint():checkpoint,spawn==null?l.spawnMode():spawn,plc==null?l.plcLoadFlags():plc,flowers?0:l.flowerCount(),flowers?0:l.fadeEnableDisplay(),l.powerup(),l.timeOver());}
  private void closureSignStore(CdSignpost.StoreCall c){
   closurePrimitive(c,CdSignpost.StoreCall.class);var l=active.prefix().main().level();switch(c.field()){
    case LEFT_BOUND_WORD,SPEED_SHOES_BYTE,INVINCIBLE_BYTE,CONTROL_LOCK_BIT0,PLAYER_CONTROL_HOLD_WORD->closureFrame(frame.prepareObjectSignpostStore(frame.state(),frame.environment(),c.field(),c.value()));
    case DEST_LEFT_BOUND_WORD->closureDestLeft(c.value());case UPDATE_HUD_TIME_BYTE->closureLevel(closureLevelValue(l,null,c.value(),null,null,null,false));
    case UPDATE_HUD_BONUS_BYTE->closureBonus(c.value(),null,null);case TIME_BONUS_WORD->closureBonus(null,c.value(),null);case RING_BONUS_WORD->closureBonus(null,null,c.value());
   }
  }
  private void closureBonus(Integer update,Integer time,Integer rings){var g=active.state().globals();var b=g.bonus().orElseThrow(()->new IllegalStateException("Actual original tally values unknown"));closureGlobals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),Optional.of(new CdLevelHud.BonusValues(time==null?b.timeBonusWord():time,rings==null?b.ringBonusWord():rings,update==null?b.updateHudBonusByte():update)),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
  private void closureSignSound(CdSignpost.SoundCall c){closurePrimitive(c,CdSignpost.SoundCall.class);closureSound(new SonicCdGameModule.PastSignpostSound(c.sound()));}
  private int closureResultsRead(CdResults.ReadCall c){closurePrimitive(c,CdResults.ReadCall.class);var g=active.state().globals();var e=frame.environment();return switch(c.field()){
   case PLC_HEAD_LONG->(int)active.prefix().section().queue().headSource();case ZONE_ACT_WORD->e.movement().gates().zoneAct();case ACT_BYTE->e.movement().gates().zoneAct()&255;
   case CAMERA_X_WORD->camera().xWord();case PLAYER_X_WORD->frame.state().motion().centreX16_16()>>>16;case GOOD_FUTURE_BYTE->active.prefix().goodFuture();
   case TIME_BONUS_WORD->g.bonus().orElseThrow().timeBonusWord();case RING_BONUS_WORD->g.bonus().orElseThrow().ringBonusWord();
   case SPECIAL_STAGE_BYTE->active.closureSpecialStage();case TIME_ATTACK_MODE_BYTE->e.globals().timeAttackMode();case TIME_STONES_BYTE->active.closureTimeStones();case GOOD_FUTURE_FLAGS_BYTE->active.prefix().goodFutureFlags();};}
  private void closureResultsBonus(CdResults.BonusStoreCall c){closurePrimitive(c,CdResults.BonusStoreCall.class);switch(c.field()){case UPDATE_HUD_BONUS_BYTE->closureBonus(c.value(),null,null);case TIME_BONUS_WORD->closureBonus(null,c.value(),null);case RING_BONUS_WORD->closureBonus(null,null,c.value());}}
  private void closureResultsPoints(CdResults.PointsCall c){
   closurePrimitive(c,CdResults.PointsCall.class);var g=active.state().globals();var r=g.ring();var s=g.score();var result=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),s.score(),s.nextLifeScore(),s.updateHudScore()),c.points());var n=result.world();
   closureGlobals(SonicCdR31aWorldHost.with(g,new CdRingGlobals(n.rings(),n.lives(),r.livesFlags(),r.updateHudRings(),n.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),g.live(),g.contact(),new CdOpeningObjectRuntime.ScoreGlobals(n.score(),n.nextLifeScore(),n.updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));
   result.command().ifPresent(cmd->closureSound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(cmd))));
  }
  private void closureResultsSound(CdResults.SoundCall c){closurePrimitive(c,CdResults.SoundCall.class);closureSound(new SonicCdGameModule.PastResultsSound(c.sound()));}
  private void closurePrefix(SonicCdGameModule.R11LoadingPrefix next){var before=active.state();var old=active.prefix();closureScope.check();active.primaryLifecycle(before,old,before,next);closureCheckpoint();}
  private void closureResultsProgramme(CdResults.ProgrammeStoreCall c){
   closurePrimitive(c,CdResults.ProgrammeStoreCall.class);var l=active.prefix().main().level();var p=active.prefix();var m=p.main();switch(c.field()){
    case LEVEL_RESTART_WORD->closureLevel(closureLevelValue(l,c.value(),null,null,null,null,false));case SPAWN_MODE_BYTE->closureLevel(closureLevelValue(l,null,null,null,c.value(),null,false));
    case CHECKPOINT_BYTE->closureLevel(closureLevelValue(l,null,null,c.value(),null,null,false));case FLOWER_COUNT_LONG->closureLevel(closureLevelValue(l,null,null,null,null,null,true));
    case PLC_LOAD_FLAGS_CLEAR_BIT0->closureLevel(closureLevelValue(l,null,null,null,null,l.plcLoadFlags()&~1,false));case PLC_LOAD_FLAGS_CLEAR_BIT1->closureLevel(closureLevelValue(l,null,null,null,null,l.plcLoadFlags()&~2,false));
    case TIME_ZONE_BYTE,ZONE_ACT_WORD->closureFrame(frame.prepareObjectResultsProgrammeGates(frame.state(),frame.environment(),c.field(),c.value()));
    case SECTION_ID_WORD->{var before=p.section();var next=art.clearSectionWord(before);active.section(before,next);closureCheckpoint();p=active.prefix();m=p.main();closurePrefix(p.main(new CdOriginalBootPrefix.MainImage(m.level(),0,m.demoModeWord(),m.usePlayer2Byte(),m.palFadeFlagsByte(),m.levelStartedByte())));}
    case GOOD_FUTURE_BYTE->closurePrefix(p.future(c.value()));
    case GOOD_FUTURE_FLAGS_CLEAR_BYTE,GOOD_FUTURE_FLAGS_SET_BIT->{int flags=c.field()==CdResults.ProgrammeField.GOOD_FUTURE_FLAGS_CLEAR_BYTE?0:p.goodFutureFlags()|(1<<c.value());closurePrefix(new SonicCdGameModule.R11LoadingPrefix(p.main(),p.goodFuture(),p.gameMode(),flags,p.ring(),p.score(),p.section(),p.normalPalette(),p.fadePalette(),p.calls(),p.blockDefinitions()));}
    case UNKNOWN_LEVEL_BYTE,PROJ_DESTROYED_BYTE->{var g=active.state().globals();var t=g.tail();if(c.field()==CdResults.ProgrammeField.UNKNOWN_LEVEL_BYTE){var v=t.orElseThrow(()->new IllegalStateException("Actual original tail unknown"));t=Optional.of(new SonicCdR31aWorldHost.Tail(v.player2ControlsWord(),v.player2BoredWord(),c.value(),v.demoS1IndexWord(),v.paletteFadeInfoWord()));}
     closureGlobals(new SonicCdR31aWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),c.field()==CdResults.ProgrammeField.PROJ_DESTROYED_BYTE?OptionalInt.of(c.value()):g.projectorByte(),g.checkpointSaved(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),t,g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord()));}
   }
  }
  private void closureResultsAction(CdResults.ProgrammeActionCall c){closurePrimitive(c,CdResults.ProgrammeActionCall.class);switch(c.action()){
   case RESET_SAVED_OBJECT_FLAGS->{closureScope.check();placement.resetSavedObjectFlags();closureCheckpoint();}
   case FADE_OUT_MUSIC->closureSound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA)));
  }}
  private void closureFamily(int whole,int id){
   requirePhysical(whole);if(closureScope!=null)throw new IllegalStateException("Closure dispatch reentry");closureOperations=0;
   try{switch(id){case 0x14->closureBigRing.run(whole);case 0x16->{var e=frame.environment().movement().gates();closureGoal.run(whole,new CdGoalPost.Globals(e.zoneAct(),e.timeZone(),active.prefix().goodFuture(),e.timeWarpDirection(),camera().xWord()));}case 0x17->closureSignpost.run(whole);case 0x3A->closureResults.run(whole);default->throw new IllegalArgumentException("Foreign closure handler");}}
   finally{closureScope=null;}
  }
  private final CdLogShadow logShadow;private final CdExplosion explosion;private final CdPoints points;
  private final CdCollapsePlatform collapsePlatform;private final CdPlatform platform;
   private final Cd3dPlant plant;
   private final CdCheckpoint checkpoint;private final CdTimePost timePost;
   private boolean checkpointDispatch,timePostDispatch;private int checkpointStores;
   private boolean checkpointSaved;private TimePostScope timePostScope;
  // Assigned only in this authenticated constructor; private cached kernels are never restored/replaced.
  private CdSpinTunnelTag tunnelTag;private TunnelOperation tunnelOperation;
  private CdTunnelDoor tunnelDoor;private CdTunnelDoorVertical tunnelDoorVertical;
  private CdTunnelDoorSplash tunnelDoorSplash;private CdTunnelPath tunnelPath;
  private int tunnelOperations;private List<TunnelService> tunnelServices=List.of();
  // Actual $0E body borrows the already admitted source bytes and the same live physical pool.
  // These are immutable cached relations plus stack-only occurrence checks, never capture owners.
  private final CdSpinTunnelSplash waterfallSplash;private final CdSpinTunnelSplash.Data waterfallData;
  private WaterfallOperation waterfallOperation;private boolean waterfallDispatch;private long waterfallOrdinal;private int waterfallOperations;
  private CdSpinningDisc mechanismDisc;private CdMovingSpring mechanismMovingSpring;private CdSpringboard mechanismBoard;
  private CdSonicHole mechanismHole;private CdBreakableWall mechanismWall;private MechanismOperation mechanismOperation;
  private long mechanismOrdinal;private List<MechanismService> mechanismServices=List.of();
  private final Cd3dRamp ramp;private final CdBoulder boulder;private final CdSpikes spikes;private final CdSpring spring;
  private CdSpring.OriginalCall springCall;private FamilyCut springBefore,springAfter;
  private CdObjectPool.Snapshot springPool;private long springOrdinal;private boolean springPrepared,springUsed;
  private final CdFloatBlock floatBlock;private int floatTopOrdinal=-1;
  private final CdGameOver gameOver;private GameOverScope gameOverScope;
  // Original R11 Monitor graph: facades/receipts below own no second gameplay or capture bank.
  private final CdMonitor monitor;private final CdMonitorItem monitorItem;private final CdShieldPowerup monitorShield;private final CdReservedObjectBank monitorReserved;
  private Object monitorItemOperation;private CdShieldPowerup.Call monitorShieldOperation;
  private final CdSolidObject ttBlockSolid;
  private final CdPlayerObjectTouch scanner;private final CdPlayerTouchResponse response;private final CdOriginalWorldBindings.Hud<CdR11aSectionArt.State> font;
  private SonicCdGameModule.OriginalR11Access active;private int physical=-1,physicalId;private boolean drawing,draining,walkReturned;
  private List<SonicCdGameModule.PastSound> sounds=List.of();
  R11(SonicCdGameModule module,CdR11aAssets assets,CdR11aSectionArt art,CdObjectPlacementRuntime placement)throws IOException{
   this.module=Objects.requireNonNull(module);this.assets=Objects.requireNonNull(assets);this.art=Objects.requireNonNull(art);this.placement=Objects.requireNonNull(placement);
   pastSource=null;pastObjects=null;ttSource=null;ttObjects=null;ttBlockSolid=null;var originalProgramme=new CdR11OriginalWorldBindings(frame.stageSource(),assets,art,placement.placements());programme=originalProgramme;
   metadata=originalProgramme.objectAssets();startup=originalProgramme.startupObjects();scanner=CdPlayerObjectTouch.fromAssets(metadata);response=CdPlayerTouchResponse.fromAssets(metadata);
   // No DrawObjects receipt exists at birth. Original BRAM and Main.ClearScreen own
   // the actual raw CPU buffer. A queue is born only for the real live physical pass,
   // just as the retained R31 pass does, and retires after its actual drain.
   var refs=new ArrayList<CdObjectDrawQueue.CdSlotRef>();for(int w=1;w<32;w++)refs.add(new CdObjectDrawQueue.CdSlotRef(w));reservedRefs=List.copyOf(refs);
   mappings=originalProgramme.mappings(metadata,startup);
  enemyPataBata=CdPataBata.fromOriginalPresentAssets(placement,assets,metadata,this::enemyDraw,enemyObserver());
  enemyAnton=CdAnton.fromOriginalPresentAssets(placement,assets,metadata,this::enemyAntonAngle,this::enemyDraw,enemyObserver());
  enemyTamabboh=CdTamabboh.fromOriginalPresentAssets(placement,assets,metadata,this::enemyTamabbohAngle,this::enemyTamabbohPlayer,this::enemyTamabbohFm,this::enemyDraw,enemyObserver());
   closureBigRing=CdBigRing.fromOriginalPresentAssets(placement,assets,this::closureBigRead,this::closureBigSpecial,this::closureBigPlayer,this::closureBigLock,this::closureBigSound,this::closureDraw,closureObserver);
   closureGoal=CdGoalPost.fromOriginalPresentAssets(placement,assets,this::closureGoalRead,this::closureGoalReached,c->closurePlc(c,c.nativeId()),c->{closurePrimitive(c,CdGoalPost.PlcHeadCall.class);return (int)active.prefix().section().queue().headSource();},this::closureDraw,closureObserver);
   closureSignpost=CdSignpost.fromOriginalPresentAssets(placement,assets,this::closureSignRead,this::closureSignStore,this::closureSignSound,this::closureDraw,closureObserver);
   closureResults=CdResults.fromOriginalPresentAssets(placement,assets,this::closureResultsRead,this::closureResultsBonus,this::closureResultsPoints,c->closurePlc(c,c.nativeId()),this::closureResultsSound,this::closureDraw,this::closureResultsProgramme,this::closureResultsAction,closureObserver);

   flower=CdFlower.withSavedOwner(placement,CdFlower.Assets.fromObjectAssets(metadata),new CdPlayerTerrain(assets.terrain()),
       this::flowerSaved,this::flowerAngle,this::flowerDraw,new CdFlower.DispatchObserver(){
        public void beforePublication(CdFlower.Publication c){flowerBegin(c,c.ordinal(),c.cut(),c.source(),c.output());}
        public void afterPublication(CdFlower.Publication c){flowerAfter(c);}
        public void beforeCall(Object c){flowerBefore(c);}public void afterCall(Object c){flowerAfter(c);}
       },new CdFlower.ReceiptGuard(){public void prepared(Object c){flowerPrepared(c);}public void completed(Object c){flowerCompleted(c);}});
   ring=CdRing.fromAssets(placement,metadata);animal=new CdAnimal(placement);
   lostRing=CdLostRing.fromOriginalPresentAssets(placement,assets,new CdPlayerMath(assets.player()),
       new CdPlayerTerrain(assets.terrain()),this::lostRingAngle,this::lostRingSound,this::lostRingDraw,
       this::lostRingCollect,lostRingAnimationGuard());
   plant=new Cd3dPlant(placement,Cd3dPlant.Assets.fromObjectAssets(metadata),this::drawDynamic);
   var solid=CdSolidObject.fromAssets(assets);
   collapsePlatform=CdCollapsePlatform.fromOriginalPresentAssets(placement,assets,solid,this::collapsePlatformSolid,
       this::collapsePlatformDraw,this::collapsePlatformFm,this::collapsePlatformPublished);
   platform=CdPlatform.fromOriginalPresentAssets(placement,assets,solid,this::platformSolid,this::platformAngle,this::platformDraw);
   ramp=Cd3dRamp.fromOriginalPresentAssets(placement,assets,this::rampPrimary,
       this::rampDraw);
   boulder=CdBoulder.fromOriginalPresentAssets(placement,assets,solid,
       this::boulderSolid,
       this::boulderDraw);
   spikes=CdSpikes.fromOriginalPresentAssets(placement,assets,solid,
       this::spikesSolid,
       this::spikesRewind,this::spikesHurt,this::spikesDraw);
   spring=createSpring(solid);
   var mechanismObserver=new CdOriginalMechanismProtocol.Observer(){
    public void before(CdOriginalMechanismProtocol.Call c){beforeMechanism(c);}
    public void after(CdOriginalMechanismProtocol.Call c){afterMechanism(c);}
    public void failed(CdOriginalMechanismProtocol.Call c,Throwable original){failedMechanism(c,original);}
   };
   mechanismDisc=CdSpinningDisc.fromOriginalPresentMechanism(placement,assets,new CdPlayerMath(assets.player()),solid,
       c->{useMechanism(c);var p=c.expectedPlayer();familySolid(()->mechanismCurrent(mechanismOperation.call),c.actualCut(),discSolidPlayer(p),discSolidGlobals(p),c.outcome());mechanismCheckpoint();},
       new CdSpinningDisc.PlayerOwner(){
        public CdSpinningDisc.PlayerCut read(){useMechanism(CdOriginalMechanismProtocol.NativeSite.DISC_READ);var value=new CdSpinningDisc.PlayerCut(frame.state(),frame.environment());mechanismReadCheckpoint();return value;}
        public void commit(CdSpinningDisc.PlayerCall c){useMechanism(c);mechanismOperation.before.publish(null,frame.prepareOriginalMechanismDisc(c),null,List.of());mechanismCheckpoint();}
       },c->{useMechanism(c);if(!new CdSpinningDisc.PlayerCut(frame.state(),frame.environment()).equals(c.expectedPlayer()))throw new IllegalArgumentException("Stale original Disc FM pair");mechanismOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastDiscFm(c.sound())));mechanismCheckpoint();},
       (pool,id)->mechanismDraw(CdOriginalMechanismProtocol.NativeSite.DISC_DRAW,pool,id),mechanismObserver);
   mechanismMovingSpring=createMovingSpring(new CdPlayerTerrain(assets.terrain()),mechanismObserver);
   mechanismBoard=CdSpringboard.fromOriginalPresentMechanism(placement,assets,
       c->{useMechanism(c);mechanismOperation.before.publish(null,frame.prepareObjectSpringboardPlayerStore(c),null,List.of());mechanismCheckpoint();},
       c->mechanismDraw(c,placement.pool(),c.source().identity()),mechanismObserver);
   mechanismHole=CdSonicHole.fromOriginalPresentMechanism(placement,assets,
       (field,pool,source)->{useMechanism(field);mechanismSource(pool,source);var m=frame.state().motion();int value=switch(field){case CONTROL_2C->m.playerControl();case X_WORD->m.centreX16_16()>>>16;case Y_WORD->m.centreY16_16()>>>16;};mechanismReadCheckpoint();return value;},
       (sound,pool,source)->{useMechanism(sound);mechanismSource(pool,source);mechanismOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastMechanismHoleFm(sound)));mechanismCheckpoint();},
       (pool,id)->mechanismDraw(CdOriginalMechanismProtocol.NativeSite.HOLE_DRAW,pool,id),mechanismObserver);
   mechanismWall=CdBreakableWall.fromOriginalPresentMechanism(placement,assets,solid,
       c->{useMechanism(c);mechanismWall.validateCurrentCall(c);familySolid(()->mechanismCurrent(mechanismOperation.call),c.cut().placement().pool(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());mechanismCheckpoint();},
       c->{useMechanism(c);mechanismWall.validateCurrentCall(c);if(!floatInputs().player().equals(c.expectedPlayer()))throw new IllegalArgumentException("Stale original Wall ASR player");mechanismOperation.before.publish(null,frame.prepareObjectBreakableWallXVelocity(frame.state(),frame.environment()),null,List.of());mechanismCheckpoint();},
       (pool,id)->{mechanismWall.validateCurrentDraw(pool,id);mechanismDraw(id,pool,id);},
       c->{useMechanism(c);mechanismWall.validateCurrentCall(c);mechanismOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastWallFm(c.nativeId())));mechanismCheckpoint();},mechanismObserver);
   tunnelTag=CdSpinTunnelTag.fromOriginalPresentAssets(placement,assets,new CdSpinTunnelTag.OriginalPorts(){
    public CdPlayerMotionState motion(CdSpinTunnelTag.OriginalCall c){useTunnel(c,()->tunnelTag.isCurrentCall(c));var value=frame.state().motion();tunnelReadCheckpoint();return value;}
    public int read(CdSpinTunnelTag.OriginalCall c,CdSpinTunnelTag.Read field){
     useTunnel(c,()->tunnelTag.isCurrentCall(c));if(c.read()!=field||c.site()!=CdSpinTunnelTag.Site.READ)throw new IllegalStateException("Wrong original Tag read");
     var e=frame.environment();int value=switch(field){case ZONE->e.movement().gates().zoneAct()>>>8;case DEBUG_MODE->e.globals().debugMode();case DEBUG_CHEAT->e.globals().debugCheat();case RAW_HELD->e.controls().rawHeld();};tunnelReadCheckpoint();return value;
    }
    public void player(CdSpinTunnelTag.OriginalCall c,CdSpinTunnelTag.PlayerResult value){
     useTunnel(c,()->tunnelTag.isCurrentCall(c));if(c.site()!=CdSpinTunnelTag.Site.PLAYER||c.player()!=value)throw new IllegalStateException("Wrong original Tag player store");
     var before=tunnelOperation.before;var fp=frame.prepareObjectSpinTunnelResult(frame.state(),frame.environment(),value);before.publish(null,fp,null,List.of());tunnelCheckpoint();
    }
    public void draw(CdSpinTunnelTag.OriginalCall c){useTunnel(c,()->tunnelTag.isCurrentCall(c));if(c.site()!=CdSpinTunnelTag.Site.DRAW)throw new IllegalStateException("Wrong original Tag Draw");tunnelDrawPrefix();familyDraw(placement.pool(),c.source().identity(),()->tunnelTag.isCurrentCall(c));tunnelCheckpoint();}
   },new CdSpinTunnelTag.OriginalObserver(){
    public void before(CdSpinTunnelTag.OriginalCall c){
     beginTunnel(c,()->tunnelTag.isCurrentCall(c),c.cut(),c.source(),CdSpinTunnelTag.OBJECT_ID);
     switch(c.site()){
      case OBJECT->tunnelOperation.pool=CdObjectPool.overlay(c.cut().pool(),List.of(c.output()));
      case DELETE->{var pool=CdObjectPool.stagingCopy(c.cut().pool());pool.deleteObject(physical);tunnelOperation.pool=pool.capture();}
      case SAVED->tunnelSaved(c.savedIndex());default->tunnelOperation.callbackRequired=true;
     }
    }
    public void after(CdSpinTunnelTag.OriginalCall c){endTunnel(c,()->tunnelTag.isCurrentCall(c),c.completed());}
    public void failed(CdSpinTunnelTag.OriginalCall c,Throwable original){failTunnel(c,()->tunnelTag.isCurrentCall(c),c.completed(),original);}
   });
   tunnelDoor=CdTunnelDoor.fromOriginalPresentAssets(placement,assets,()->{
    var call=tunnelCall(CdTunnelDoor.SecondaryReadCall.class);useTunnel(call,()->tunnelDoor.isCurrentCall(call));var bytes=row(1);tunnelReadCheckpoint();return bytes;
   },solid,new CdTunnelDoor.PrefixSolidOwner(){
    public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){useTunnel(c,()->tunnelDoor.isCurrentCall(c));doorInputs(c);tunnelReadCheckpoint();return doorLive();}
    public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){
     useTunnel(c,()->tunnelDoor.isCurrentCall(c));var cut=c.cut();doorInputs(cut);
     if(cut.participant()==CdTunnelDoor.Participant.PRIMARY)familySolid(()->tunnelDoor.isCurrentCall(c),cut.callCut(),cut.expectedPrimary(),cut.expectedGlobals(),c.outcome());
     else{
      var d=(CdSolidObject.Completed)c.outcome();var result=d.completedResult();
      if(!result.otherSlots().isEmpty()||!result.fmRequests().isEmpty()||!result.globals().equals(cut.expectedGlobals()))throw new IllegalArgumentException("Secondary cannot publish primary/shared values");
      tunnelOperation.before.publish(CdObjectPool.overlay(cut.callCut(),List.of(result.object())),null,null,List.of());
     }
     tunnelCheckpoint();return doorLive();
    }
   },(pool,id)->{var c=tunnelCall(CdTunnelDoor.DrawCall.class);useTunnel(c,()->tunnelDoor.isCurrentCall(c));tunnelDrawPrefix();familyDraw(pool,id,()->tunnelDoor.isCurrentCall(c));tunnelCheckpoint();},
   fm->{var c=tunnelCall(CdTunnelDoor.FmCall.class);useTunnel(c,()->tunnelDoor.isCurrentCall(c));if(c.sound()!=fm)throw new IllegalStateException("Wrong Door FM");tunnelOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastTunnelDoorFm(fm)));tunnelCheckpoint();},
   new CdTunnelDoor.DispatchObserver(){
    public void beforePublication(CdTunnelDoor.Publication c){beginTunnel(c,()->tunnelDoor.isCurrentCall(c),c.cut(),c.source(),CdTunnelDoor.OBJECT_ID);switch(c.kind()){
     case SOURCE,CHILD->tunnelOperation.pool=CdObjectPool.overlay(c.cut().pool(),List.of(c.output()));
     case SAVED->tunnelSaved(c.savedIndex());case DELETE->tunnelDelete(c.cut().pool());}}
    public void afterPublication(CdTunnelDoor.Publication c){endTunnel(c,()->tunnelDoor.isCurrentCall(c),true);}
    public void beforeCall(Object c){var cut=doorCallCut(c);beginTunnel(c,()->tunnelDoor.isCurrentCall(c),cut,placement.pool().state(physical),CdTunnelDoor.OBJECT_ID);tunnelOperation.callbackRequired=true;}
    public void afterCall(Object c,Object value){endTunnel(c,()->tunnelDoor.isCurrentCall(c),true);}
    public void failedCall(Object c,Throwable original){failTunnel(c,()->tunnelDoor.isCurrentCall(c),false,original);}
   });
   tunnelDoorVertical=CdTunnelDoorVertical.fromOriginalPresentAssets(placement,assets,solid,c->{
    var original=tunnelCall(CdTunnelDoorVertical.OriginalCall.class);useTunnel(original,()->tunnelDoorVertical.isCurrentOriginalCall(original));
    if(original.site()!=CdTunnelDoorVertical.OriginalSite.SOLID||original.payload()!=c)throw new IllegalStateException("Wrong DoorV Solid");
    familySolid(()->tunnelDoorVertical.isCurrentPrefixSolidCall(c),c.callCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());tunnelCheckpoint();
   },(pool,id)->{var c=tunnelCall(CdTunnelDoorVertical.OriginalCall.class);useTunnel(c,()->tunnelDoorVertical.isCurrentOriginalCall(c));if(c.site()!=CdTunnelDoorVertical.OriginalSite.DRAW)throw new IllegalStateException("Wrong DoorV Draw");tunnelDrawPrefix();familyDraw(pool,id,()->tunnelDoorVertical.isCurrentDrawCall(pool,id));tunnelCheckpoint();},
   fm->{var c=tunnelCall(CdTunnelDoorVertical.OriginalCall.class);useTunnel(c,()->tunnelDoorVertical.isCurrentOriginalCall(c));if(c.site()!=CdTunnelDoorVertical.OriginalSite.FM||c.payload()!=fm)throw new IllegalStateException("Wrong DoorV FM");tunnelOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastVerticalDoorFm(fm)));tunnelCheckpoint();},
   new CdTunnelDoorVertical.OriginalObserver(){
    public void before(CdTunnelDoorVertical.OriginalCall c){beginTunnel(c,()->tunnelDoorVertical.isCurrentOriginalCall(c),c.cut(),c.source(),CdTunnelDoorVertical.OBJECT_ID);switch(c.site()){
     case SOURCE->tunnelOperation.pool=CdObjectPool.overlay(c.cut().pool(),List.of(c.output()));case SAVED->tunnelSaved(c.savedIndex());case DELETE->tunnelDelete(c.cut().pool());default->tunnelOperation.callbackRequired=true;}}
    public void after(CdTunnelDoorVertical.OriginalCall c){endTunnel(c,()->tunnelDoorVertical.isCurrentOriginalCall(c),c.completed());}
    public void failed(CdTunnelDoorVertical.OriginalCall c,Throwable original){failTunnel(c,()->tunnelDoorVertical.isCurrentOriginalCall(c),c.completed(),original);}
   });
   tunnelDoorSplash=CdTunnelDoorSplash.fromOriginalPresentAssets(placement,assets,new CdTunnelDoorSplash.DispatchObserver(){
    public void beforePublication(CdTunnelDoorSplash.Publication c){beginTunnel(c,()->tunnelDoorSplash.isCurrentCall(c),c.cut(),c.source(),CdTunnelDoorSplash.OBJECT_ID);if(c.kind()==CdTunnelDoorSplash.StoreKind.DELETE)tunnelDelete(c.cut().pool());else tunnelOperation.pool=CdObjectPool.overlay(c.cut().pool(),List.of(c.output()));}
    public void afterPublication(CdTunnelDoorSplash.Publication c){endTunnel(c,()->tunnelDoorSplash.isCurrentCall(c),tunnelDoorSplash.isCurrentCallCompleted(c));}
    public void beforeCall(Object c){var cut=c instanceof CdTunnelDoorSplash.DrawCall d?d.cut():((CdTunnelDoorSplash.FmCall)c).cut();beginTunnel(c,()->tunnelDoorSplash.isCurrentCall(c),cut,placement.pool().state(physical),CdTunnelDoorSplash.OBJECT_ID);tunnelOperation.callbackRequired=true;}
    public void afterCall(Object c){endTunnel(c,()->tunnelDoorSplash.isCurrentCall(c),tunnelDoorSplash.isCurrentCallCompleted(c));}
    public void failed(Object c,Throwable original){failTunnel(c,()->tunnelDoorSplash.isCurrentCall(c),tunnelDoorSplash.isCurrentCallCompleted(c),original);}
   },c->{useTunnel(c,()->tunnelDoorSplash.isCurrentCall(c));tunnelDrawPrefix();familyDraw(placement.pool(),c.source().identity(),()->tunnelDoorSplash.isCurrentCall(c));tunnelCheckpoint();},
   c->{useTunnel(c,()->tunnelDoorSplash.isCurrentCall(c));tunnelOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastTunnelDoorSplashFm(c.sound())));tunnelCheckpoint();},
   new CdTunnelDoorSplash.ReceiptGuard(){public void prepared(Object c){if(!tunnelDoorSplash.isCurrentCall(c)||tunnelOperation==null||tunnelOperation.call!=c)throw new IllegalStateException("Unprepared original Splash operation");}public void completed(Object c){if(!tunnelDoorSplash.isCurrentCallCompleted(c))throw new IllegalStateException("Unreturned original Splash operation");}});
   tunnelPath=CdTunnelPath.fromOriginalPresentAssets(placement,assets,new CdTunnelPath.PlayerOwner(){
    public CdTunnelPath.PlayerCut read(){var c=tunnelCall(CdTunnelPath.PlayerReadCall.class);useTunnel(c,()->tunnelPath.isCurrentCall(c));var value=new CdTunnelPath.PlayerCut(frame.state(),frame.environment());tunnelReadCheckpoint();return value;}
    public void commit(CdTunnelPath.PlayerCall c){
     useTunnel(c,()->tunnelPath.isCurrentCall(c));sourceCall(c.actualCut(),c.expectedSource(),CdTunnelPath.OBJECT_ID);
     var fp=frame.prepareObjectTunnelPathPlayerResult(c.expectedPlayer().state(),c.expectedPlayer().environment(),c.result());
     var pool=CdObjectPool.overlay(c.actualCut(),List.of(c.finalSource()));tunnelOperation.before.publish(pool,fp,null,List.of());tunnelCheckpoint();
    }
   },c->{useTunnel(c,()->tunnelPath.isCurrentCall(c));if(frame.state()!=c.expectedPlayer().state()||frame.environment()!=c.expectedPlayer().environment())throw new IllegalArgumentException("Stale original TunnelPath FM pair");tunnelOperation.before.publish(null,null,null,List.of(new SonicCdGameModule.PastTunnelPathFm(c.sound())));tunnelCheckpoint();},
   new CdTunnelPath.DispatchObserver(){
    public void beforeOperation(Object c){var cut=pathCallCut(c);beginTunnel(c,()->tunnelPath.isCurrentCall(c),cut,placement.pool().state(physical),CdTunnelPath.OBJECT_ID);
     if(c instanceof CdTunnelPath.Publication d){switch(d.kind()){case SOURCE->tunnelOperation.pool=CdObjectPool.overlay(cut.pool(),List.of(d.output()));case SAVED_BYTE->tunnelOperation.saved=d.savedFlags();case DELETE->tunnelDelete(cut.pool());}}
     else if(!(c instanceof CdTunnelPath.WordReadCall))tunnelOperation.callbackRequired=true;
    }
    public void afterOperation(Object c){tunnelPath.currentResult(c);endTunnel(c,()->tunnelPath.isCurrentCall(c),true);}
    public void failedOperation(Object c,Throwable original){failTunnel(c,()->tunnelPath.isCurrentCall(c),false,original);}
   },new CdTunnelPath.ReceiptGuard(){public void prepared(Object c){if(!tunnelPath.isCurrentCall(c)||tunnelOperation==null||tunnelOperation.call!=c)throw new IllegalStateException("Unprepared original TunnelPath operation");}public void completed(Object c){tunnelPath.currentResult(c);}});
   waterfallData=new CdSpinTunnelSplash.Data(CdSpinTunnelSplash.MAPPING_ADDRESS,
       new CdObjectAnimation.AnimationTable(metadata.view(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_ANIMATIONS).bytes(),0,1));
   waterfallSplash=CdSpinTunnelSplash.fromOriginalPresentAssets(placement,assets,waterfallData,
       new CdSpinTunnelSplash.Guard(){
        public void before(CdSpinTunnelSplash.Operation call){waterfallBefore(call);}
        public void after(CdSpinTunnelSplash.Operation call){waterfallAfter(call);}
       },this::waterfallTimeZone,this::waterfallDraw);
   floatBlock=CdFloatBlock.fromOriginalPresentAssets(placement,assets,this::floatTop,this::floatDraw);
   mosqui=CdMosqui.fromOriginalPresentAssets(placement,metadata,new CdPlayerTerrain(assets.terrain()),call->{sourceCall(call.actualCut(),call.expectedSource(),CdMosqui.OBJECT_ID);var p=frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),call.value());frame.validatePreparedObjectSolidPublication(p);frame.applyPreparedObjectSolidPublication(p);},call->{sourceCall(call.actualCut(),call.expectedSource(),CdMosqui.OBJECT_ID);sound(new SonicCdGameModule.PastMosquiFm(call.sound()));},this::drawDynamic);
   // Same original source and live placement. These cached kernels execute their own stores,
   // first-free child allocation, one guarded Draw and deletion; completed effects are not replayed.
   logShadow=new CdLogShadow(placement,CdLogShadow.Assets.fromObjectAssets(metadata),this::drawDynamic);
   explosion=new CdExplosion(placement,CdExplosion.Assets.fromObjectAssets(metadata),this::drawDynamic);
   points=new CdPoints(placement,CdPoints.Assets.fromObjectAssets(metadata),this::drawDynamic);
   hud=new CdReservedHud(startup.hudAssets(),new CdReservedHud.Slots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::drawReserved);
   title=new CdTitleCard(placement,startup.titleAssets(),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::titleRead,this::titleRead,this::titleStore,call->{titleSource(call.cut());active.append(call.nativeId());},call->{titleSource(call.cut());drawWhole(physical);});
   var monitorFamily=newMonitorFamily(CdMonitor.Assets.fromObjectAssets(metadata),CdTimeStars.Assets.fromObjectAssets(metadata),CdSolidObject.fromAssets(assets),new CdPlayerTerrain(assets.terrain()),CdShieldPowerup.LayerPolicy.READ_ZONE_AND_OPTIONAL_LAYER);
   monitorReserved=monitorFamily.reserved();monitor=monitorFamily.monitor();monitorItem=monitorFamily.item();monitorShield=monitorFamily.shield();
    timeStars=CdTimeStars.withWorldOwner(monitorReserved,CdTimeStars.Assets.fromObjectAssets(metadata),new CdTimeStars.PowerupPort(){
     public int read(){starPrimitive(CdTimeStars.PowerupReadCall.class);return active.prefix().main().level().powerup();}
     public void write(int value){var op=starPrimitive(CdTimeStars.PowerupWriteCall.class);var call=(CdTimeStars.PowerupWriteCall)op.call;var old=active.prefix().main().level();if(call.previousValue()!=old.powerup()||call.value()!=value)throw new IllegalStateException("Changed original TimeStars powerup");active.level(old,new CdLevelGlobals(old.levelRestart(),old.updateHudTime(),old.checkpoint(),old.spawnMode(),old.plcLoadFlags(),old.flowerCount(),old.fadeEnableDisplay(),value,old.timeOver()));op.expected=new R11WarpCut();}
    },(bank,id)->{var op=starPrimitive(CdTimeStars.DrawCall.class);if(bank!=monitorReserved||id.wholeIndex()!=physical)throw new IllegalArgumentException("Foreign TimeStars Draw");drawReserved(physical);starDrawLanded(op);},starObserver(),starGuard());
    checkpoint=CdCheckpoint.fromOriginalPresentAssets(placement,assets,new CdCheckpoint.LivePort(){
     public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall call){checkpointCall(call);return active.prefix().main().level();}
     public int readParent(CdCheckpoint.ParentByteCall call){checkpointCall(call);return checkpointParent(call.rawAddressBits());}
     public CdCheckpointSaveData.State readSaved(CdCheckpoint.SavedReadCall call){checkpointCall(call);return active.state().globals().checkpointSaved();}
     public void store(CdCheckpoint.GlobalStoreCall call){checkpointStore(call);}
     public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall call){return checkpointSave(call);}
     public void fm(CdCheckpoint.FmRequestCall call){checkpointCall(call);if(!checkpointSaved||checkpointStores!=2)throw new IllegalStateException("Checkpoint sound before actual save");sound(new SonicCdGameModule.PastCheckpointFm(call.sound()));}
    },this::checkpointDraw);
    // Existing source-neutral lazy kernel, with original native operation scopes. Never the Monitor<8 branch.
    timePost=newTimePost(CdMonitor.Assets.fromObjectAssets(metadata));
    font=originalProgramme.hud(startup);
   gameOver=CdGameOver.withOriginalOperations(placement,CdGameOver.Assets.fromObjectAssets(metadata),this::gameOverDraw,this::gameOverSound,
       new CdGameOver.DispatchObserver(){
        public void beforeOperation(Object call){beforeGameOver(call);}
        public void afterOperation(Object call){var op=gameOverScope(call);op.verify();if(call instanceof CdGameOver.Publication||call instanceof CdGameOver.CompletionCall)op.used=true;if(!op.used)throw new IllegalStateException("Missing actual R11 GameOver primitive");op.after=true;}
        public void failedOperation(Object call,Throwable original){try{if(gameOverScope!=null)gameOverScope(call).verify();}finally{gameOverScope=null;}}
       },new CdGameOver.ReceiptGuard(){
        public void prepared(Object call){var op=gameOverScope(call);op.verify();op.prepared=true;}
        public void completed(Object call){var op=gameOverScope(call);op.verify();if(!op.after||!op.used)throw new IllegalStateException("Unreturned R11 GameOver operation");gameOverScope=null;}
       });
  }
  R11(SonicCdGameModule module,CdLevelLoadingSource source,CdR11aSectionArt art,CdObjectPlacementRuntime placement)throws IOException{
   this.module=Objects.requireNonNull(module);this.assets=null;this.art=Objects.requireNonNull(art);this.placement=Objects.requireNonNull(placement);Objects.requireNonNull(source);
   if(source.file()==CdNativeFile.R11B){
    pastSource=source;pastObjects=source.standardPastObjectSource();ttSource=null;ttObjects=null;ttBlockSolid=null;
    var standard=new CdR11OriginalWorldBindings(frame.stageSource(),source,art,placement.placements());programme=standard;pastObjects.requirePlacement(placement);
    metadata=null;startup=null;mappings=pastObjects.mappings();var refs=new ArrayList<CdObjectDrawQueue.CdSlotRef>();for(int w=1;w<32;w++)refs.add(new CdObjectDrawQueue.CdSlotRef(w));reservedRefs=List.copyOf(refs);
    ring=CdRing.fromStandardPastSource(placement,pastObjects);
    hud=new CdReservedHud(pastObjects.hudAssets(),new CdReservedHud.Slots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::drawReserved);
    title=new CdTitleCard(placement,pastObjects.titleAssets(),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::titleRead,this::titleRead,this::titleStore,call->{titleSource(call.cut());active.append(call.nativeId());},call->{titleSource(call.cut());drawWhole(physical);});font=standard.pastHud();
    scanner=pastObjects.touchScanner();response=pastObjects.touchResponse();
    monitorReserved=newShieldBank();monitor=null;monitorItem=null;monitorShield=newShield(monitorReserved,pastObjects.powerupAssets(),CdShieldPowerup.LayerPolicy.READ_ZONE_AND_OPTIONAL_LAYER);explosion=null;
    enemyAnton=CdAnton.fromOriginalStandardPastSource(placement,pastObjects,this::enemyAntonAngle,this::enemyDraw,enemyObserver());
    enemyPataBata=CdPataBata.fromOriginalStandardPastSource(placement,pastObjects,this::enemyDraw,enemyObserver());
    enemyTamabboh=CdTamabboh.fromOriginalStandardPastSource(placement,pastObjects,this::enemyTamabbohAngle,this::enemyTamabbohPlayer,this::enemyTamabbohFm,this::enemyDraw,enemyObserver());
    mosqui=CdMosqui.fromOriginalStandardPastSource(placement,pastObjects,this::pastMosquiAngle,this::pastMosquiFm,pastMosquiObserver(),this::pastMosquiDraw);
    flower=null;timeStars=null;lostRing=null;animal=null;plant=null;
    collapsePlatform=null;platform=null;ramp=null;boulder=null;spikes=null;spring=null;waterfallSplash=null;waterfallData=null;mechanismMovingSpring=null;
    floatBlock=null;logShadow=null;points=null;checkpoint=null;timePost=null;gameOver=null;
    return;
   }
   pastSource=null;pastObjects=null;ttSource=source;
   var tt=new CdTtOriginalWorldBindings(frame.stageSource(),source,art,placement);programme=tt;ttObjects=tt.objectSource();ttBlockSolid=new CdSolidObject(CdPlayerMovement.forOriginalLoading(frame.stageSource()));metadata=null;startup=null;
   mappings=ttObjects.mappings();var refs=new ArrayList<CdObjectDrawQueue.CdSlotRef>();for(int w=1;w<32;w++)refs.add(new CdObjectDrawQueue.CdSlotRef(w));reservedRefs=List.copyOf(refs);
   ring=ttObjects.ring(placement);hud=new CdReservedHud(ttObjects.hudAssets(),new CdReservedHud.Slots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::drawReserved);
   title=new CdTitleCard(placement,ttObjects.titleAssets(),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int w){requirePhysical(w);return row(w);}public void write(int w,CdAssetBytes before,CdAssetBytes ready){requirePhysical(w);active.reserved(w,before,ready);}},this::titleRead,this::titleRead,this::titleStore,call->{titleSource(call.cut());active.append(call.nativeId());},call->{titleSource(call.cut());drawWhole(physical);});font=tt.hud();
   var monitorFamily=newMonitorFamily(ttObjects.timePostAssets(),ttObjects.powerupAssets(),ttBlockSolid,CdPlayerTerrain.fromStageTerrain(ttSource.terrain()),CdShieldPowerup.LayerPolicy.OMIT_ZONE_AND_LAYER);
   monitorReserved=monitorFamily.reserved();monitor=monitorFamily.monitor();monitorItem=monitorFamily.item();monitorShield=monitorFamily.shield();
   explosion=CdExplosion.withOriginalOperations(placement,ttObjects.explosionAssets(),CdExplosion.TilePriority.FIXED_LOW,monitorExplosionObserver(),this::monitorExplosionDraw,monitorExplosionGuard());
   enemyPataBata=null;enemyAnton=null;enemyTamabboh=null;flower=null;timeStars=null;lostRing=null;mosqui=null;animal=null;plant=null;
   collapsePlatform=null;platform=null;ramp=null;boulder=null;spikes=null;spring=createSpring(ttBlockSolid);waterfallSplash=null;waterfallData=null;
   mechanismMovingSpring=createMovingSpring(CdPlayerTerrain.fromStageTerrain(frame.stageSource().terrain()),new CdOriginalMechanismProtocol.Observer(){public void before(CdOriginalMechanismProtocol.Call c){beforeMechanism(c);}public void after(CdOriginalMechanismProtocol.Call c){afterMechanism(c);}public void failed(CdOriginalMechanismProtocol.Call c,Throwable t){failedMechanism(c,t);}});
   floatBlock=null;logShadow=null;points=null;checkpoint=null;timePost=newTimePost(ttObjects.timePostAssets());scanner=ttObjects.touchScanner();response=null;gameOver=null;
  }
  private void springTerrain(){if(waterProgramme())ttBlockTerrain();else floatTerrain();}
  private CdSpring createSpring(CdSolidObject solid)throws IOException{
   var ports=new CdSpring.LivePorts(){
        public void checkSolid(CdSpring.OriginalCall c,CdSolidObject.Entry entry){useSpring(c);if(c.site()!=CdSpring.Site.SOLID_OWNER||c.payload()!=entry)throw new IllegalStateException("Wrong Spring Solid owner");springTerrain();springAfter=new FamilyCut();}
        public int read(CdSpring.OriginalCall c){return springRead(c);}
        public void solid(CdSpring.OriginalCall c,CdSpring.PrefixSolidCall p){useSpring(c);if(c.site()!=CdSpring.Site.SOLID||c.payload()!=p)throw new IllegalStateException("Wrong Spring Solid");familySolid(()->spring.isCurrentCall(c),p.cut(),p.player(),p.globals(),p.outcome(),R11.this::springTerrain);springAfter=new FamilyCut();}
        public void launch(CdSpring.OriginalCall c,CdSpring.LaunchCall p){useSpring(c);if(c.site()!=CdSpring.Site.LAUNCH||c.payload()!=p||!p.identity().equals(c.source().identity()))throw new IllegalStateException("Wrong Spring launch");springBefore.publish(null,frame.prepareObjectSpringLaunch(p),null,List.of());springAfter=new FamilyCut();}
        public void sound(CdSpring.OriginalCall c,CdSpring.FmSound sound){useSpring(c);if(c.site()!=CdSpring.Site.SOUND||c.payload()!=sound||sound!=CdSpring.FmSound.FM_SPRING)throw new IllegalStateException("Wrong Spring FM");springBefore.publish(null,null,null,List.of(new SonicCdGameModule.PastSpringFm(sound)));springAfter=new FamilyCut();}
        public void draw(CdSpring.OriginalCall c){useSpring(c);if(c.site()!=CdSpring.Site.DRAW)throw new IllegalStateException("Wrong Spring Draw");familyDraw(placement.pool(),c.source().identity(),()->spring.isCurrentCall(c));springAfter=new FamilyCut();}
       };
   var observer=new CdSpring.NativeObserver(){public void before(CdSpring.OriginalCall c){beforeSpring(c);}public void after(CdSpring.OriginalCall c){afterSpring(c);}};
   var guard=new CdSpring.ReceiptGuard(){public void prepared(CdSpring.OriginalCall c){currentSpring(c);if(springPrepared||c.completed())throw new IllegalStateException("Repeated Spring preparation");springBefore.check(springBefore.placed.pool());springPrepared=true;}public void completed(CdSpring.OriginalCall c){currentSpring(c);if(!c.completed())throw new IllegalStateException("Unreturned Spring");springCall=null;springBefore=null;springAfter=null;springPool=null;}};
   if(waterProgramme())return CdSpring.fromProgrammeSource(placement,ttObjects,new CdPlayerMath(frame.stageSource().player()),solid,ports,observer,guard);
   return CdSpring.fromOriginalPresentAssets(placement,assets,new CdPlayerMath(assets.player()),solid,ports,observer,guard);
  }
  private CdMovingSpring createMovingSpring(CdPlayerTerrain terrain,CdOriginalMechanismProtocol.Observer observer)throws IOException{
   CdMovingSpring.PrimaryAngleCommit angle=c->{useMechanism(c);mechanismOperation.before.publish(null,frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),c.value()),null,List.of());mechanismCheckpoint();};
   CdMovingSpring.SupportRead support=c->{useMechanism(c);int w=c.wholeIndex();var value=w==0?new CdPlayerMovement.RawObjectSupport(0,frame.state().header().slotId(),frame.state().motion().flags()):new CdPlayerMovement.RawObjectSupport(w,placement.pool().state(w).id(),placement.pool().state(w).flags());mechanismReadCheckpoint();return Optional.of(value);};
   CdMovingSpring.DrawObjectCall draw=c->mechanismDraw(c,placement.pool(),c.expectedSource().identity());
   if(waterProgramme())return CdMovingSpring.fromProgrammeMechanism(placement,ttObjects,terrain,angle,support,draw,observer);
   return CdMovingSpring.fromOriginalPresentMechanism(placement,assets,terrain,angle,support,draw,observer);
  }
  private CdR4Switch.Call ttSwitchCall;private FamilyCut ttSwitchBefore,ttSwitchAfter;
  private CdObjectPool.Snapshot ttSwitchExpectedPool;private CdR4SwitchBank ttSwitchBank;
  private CdTimeWarpSaveData.State ttSwitchSaved;private long ttSwitchOrdinal;
  private void requireTtSwitch(CdR4Switch.Call c){
   requirePhysical(physical);if(!waterProgramme()||physicalId!=0x27||c.pool()!=placement.pool()||c.wholeIndex()!=physical||c.data()!=ttObjects.switchData()||frame.stageSource()!=ttSource.retainedPlayerSource())throw new IllegalStateException("Foreign current original Switch source");
   c.requireCurrent(ttSwitchOwner);ttObjects.requirePlacement(placement);
  }
  private void useTtSwitch(CdR4Switch.Call c){requireTtSwitch(c);if(ttSwitchCall!=c||ttSwitchBefore==null)throw new IllegalStateException("No prepared original Switch occurrence");ttSwitchBefore.check(ttSwitchBefore.placed.pool());if(active.switches()!=ttSwitchBank||active.timeWarpSaved()!=ttSwitchSaved)throw new IllegalStateException("Changed Switch bank/save cut");}
  private final CdR4Switch.Owner ttSwitchOwner=new CdR4Switch.Owner(){
   public long generation(){return serviceGeneration;}
   public void before(CdR4Switch.Call c){
    requireTtSwitch(c);if(ttSwitchCall!=null||c.completed()||c.ordinal()!=ttSwitchOrdinal+1)throw new IllegalStateException("Repeated/nested original Switch occurrence");
    var before=new FamilyCut();sourceCall(before.placed.pool(),c.source(),0x27);ttSwitchCall=c;ttSwitchBefore=before;ttSwitchAfter=null;ttSwitchExpectedPool=before.placed.pool();ttSwitchBank=active.switches();ttSwitchSaved=active.timeWarpSaved();
    if(c instanceof CdR4Switch.StoreCall store)ttSwitchExpectedPool=CdObjectPool.overlay(ttSwitchExpectedPool,List.of(store.output()));
    else if(c.kind()==CdR4Switch.Kind.DRAW)ttSwitchExpectedPool=CdObjectPool.overlay(ttSwitchExpectedPool,List.of(c.source().moveByte(CdObjectState.ByteField.SPRITE_FLAGS,c.source().spriteFlags()&127)));
    before.check(before.placed.pool());
   }
   public void after(CdR4Switch.Call c,Throwable failure){
    try{requireTtSwitch(c);if(ttSwitchCall!=c||ttSwitchBefore==null)throw new IllegalStateException("Foreign Switch completion");if(active.switches()!=ttSwitchBank||active.timeWarpSaved()!=ttSwitchSaved)throw new IllegalStateException("Switch changed unrelated bank/save owner");
     if(ttSwitchAfter!=null)ttSwitchAfter.check(ttSwitchAfter.placed.pool());else{boolean landed=placement.capture().pool().slots().equals(ttSwitchExpectedPool.slots());ttSwitchBefore.check(c.completed()||landed?ttSwitchExpectedPool:ttSwitchBefore.placed.pool());}ttSwitchOrdinal=c.ordinal();
    }finally{ttSwitchCall=null;ttSwitchBefore=null;ttSwitchAfter=null;ttSwitchExpectedPool=null;ttSwitchBank=null;ttSwitchSaved=null;}
   }
   public int primary(CdR4Switch.ReadCall c){useTtSwitch(c);var m=frame.state().motion();int v;if(c.bytes()==2&&c.offset()==12)v=m.centreY16_16()>>>16;else if(c.bytes()==1&&c.offset()==0x16)v=m.yRadius();else throw unavailable("Switch unrelated primary operand "+c.offset()+"/"+c.bytes());ttSwitchAfter=new FamilyCut();return v;}
   public void primaryY(CdR4Switch.PrimaryYCall c){useTtSwitch(c);ttSwitchBefore.publish(null,frame.prepareObjectYWord(frame.state(),frame.environment(),frame.state().motion().centreY16_16()>>>16,c.word()),null,List.of());ttSwitchAfter=new FamilyCut();}
   public int parent(CdR4Switch.ReadCall c){useTtSwitch(c);int address=c.index()&65535;if(address<0xD800||address>0xEFC0||(address&63)!=0)throw unavailable("Switch reached unowned parent WORD "+Integer.toHexString(address));var p=placement.pool().state((address-0xD000)/64);if(p.id()!=TT_SWING_ID||CdAssetBytes.copyOf(p.copyBytes()).unsignedByte(0x3C)!=6)throw unavailable("Switch linked parent is no longer its actual Swing outer part");if(c.bytes()!=2||(c.offset()!=8&&c.offset()!=12&&c.offset()!=0x36))throw unavailable("Switch unrelated parent operand");int v=CdAssetBytes.copyOf(p.copyBytes()).unsignedWord(c.offset());ttSwitchAfter=new FamilyCut();return v;}
   public int switchByte(CdR4Switch.ReadCall c){useTtSwitch(c);int v=active.switchByte(c.index());ttSwitchAfter=new FamilyCut();return v;}
   public void switchByte(CdR4Switch.SwitchStoreCall c){useTtSwitch(c);ttSwitchBank=active.switchByte(ttSwitchBank,c.index(),c.value());ttSwitchAfter=new FamilyCut();}
   public boolean solid(CdR4Switch.Call c){useTtSwitch(c);if(c.kind()!=CdR4Switch.Kind.SOLID)throw new IllegalArgumentException("Foreign Switch Solid");return ttPlainSolid(()->{requireTtSwitch(c);return true;},()->{requireTtSwitch(c);ttSwitchAfter=new FamilyCut();})!=0;}
   public void sound(CdR4Switch.ReadCall c){useTtSwitch(c);if(c.kind()!=CdR4Switch.Kind.SOUND||c.index()!=0xBF)throw new IllegalArgumentException("Foreign Switch FM");ttSwitchBefore.publish(null,null,null,List.of(new SonicCdGameModule.PastTtSwitchFm(c.index())));ttSwitchAfter=new FamilyCut();}
   public void draw(CdR4Switch.Call c){useTtSwitch(c);if(c.kind()!=CdR4Switch.Kind.DRAW)throw new IllegalArgumentException("Foreign Switch Draw");familyDraw(placement.pool(),c.source().identity(),()->{requireTtSwitch(c);return true;});ttSwitchAfter=new FamilyCut();}
   public void despawn(CdR4Switch.ReadCall c){
    useTtSwitch(c);var row=placement.pool().state(physical);var g=frame.environment().movement().gates();CdSpinTunnelTag.DespawnResult result;
    if(c.kind()==CdR4Switch.Kind.DESPAWN)result=CdSpinTunnelTag.checkObjectDespawnAtX(row,c.index(),new CdSpinTunnelTag.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),camera().xWord()));
    else if(c.kind()==CdR4Switch.Kind.DESPAWN_LINKED){if(!CdSpinTunnelTag.outsideCameraWindow(c.index(),camera().xWord())){ttSwitchAfter=new FamilyCut();return;}int savedId=row.savedFlagsId();if(savedId==0)result=new CdSpinTunnelTag.DespawnResult(true,Optional.empty());else{int period=g.timeZone();if(period>2)throw unavailable("Switch raw-period saved BYTE alias "+period);result=new CdSpinTunnelTag.DespawnResult(true,Optional.of(new CdSpinTunnelTag.LoadedClear(savedId,period,2+3*savedId+period)));}}
    else throw new IllegalArgumentException("Foreign Switch tail");ttSwitchAfter=ttApplyDespawn(ttSwitchBefore,result);
   }
  };
  private void ttSwitch(int whole){requirePhysical(whole);if(physicalId!=0x27||ttSwitchCall!=null)throw new IllegalStateException("Original Switch reentry");ttSwitchOrdinal=0;try{CdR4Switch.run(new CdR4Switch.Inputs(placement.pool(),whole,ttObjects.switchData()),ttSwitchOwner);}finally{ttSwitchCall=null;ttSwitchBefore=null;ttSwitchAfter=null;ttSwitchExpectedPool=null;ttSwitchBank=null;ttSwitchSaved=null;}}
  private CdR4Door.Call ttDoorCall;private FamilyCut ttDoorBefore,ttDoorAfter;private CdObjectPool.Snapshot ttDoorExpected;
  private CdTimeWarpSaveData.State ttDoorSaved;private com.openggf.game.soniccd.objects.r4.CdR4SwitchBank ttDoorSwitches;private long ttDoorOrdinal;
  private void requireTtDoor(CdR4Door.Call c){
   requirePhysical(physical);if(!waterProgramme()||physicalId!=0x28||c==null)throw new IllegalStateException("No original R41A Door physical occurrence");
   c.requireCurrent(ttDoorOwner);if(c.pool()!=placement.pool()||c.wholeIndex()!=physical||c.data()!=ttObjects.doorData()||frame.stageSource()!=ttSource.retainedPlayerSource())throw new IllegalStateException("Foreign original Door source/pool/Frame");ttObjects.requirePlacement(placement);
  }
  private void useTtDoor(CdR4Door.Call c){requireTtDoor(c);if(ttDoorCall!=c||ttDoorBefore==null)throw new IllegalStateException("No prepared current Door primitive");ttDoorBefore.check(ttDoorBefore.placed.pool());if(active.timeWarpSaved()!=ttDoorSaved||active.switches()!=ttDoorSwitches)throw new IllegalStateException("Changed current Door saved/switch bank");}
  private final CdR4Door.Owner ttDoorOwner=new CdR4Door.Owner(){
   public long generation(){return serviceGeneration;}
   public void before(CdR4Door.Call c){
    requireTtDoor(c);if(ttDoorCall!=null||c.completed()||c.ordinal()!=ttDoorOrdinal+1)throw new IllegalStateException("Repeated/nested original Door primitive");
    var before=new FamilyCut();sourceCall(before.placed.pool(),c.source(),0x28);if(!c.beforePool().slots().equals(before.placed.pool().slots()))throw new IllegalArgumentException("Changed Door whole-pool cut");
    ttDoorCall=c;ttDoorBefore=before;ttDoorAfter=null;ttDoorExpected=before.placed.pool();ttDoorSaved=active.timeWarpSaved();ttDoorSwitches=active.switches();
    if(c instanceof CdR4Door.StoreCall store)ttDoorExpected=CdObjectPool.overlay(ttDoorExpected,List.of(store.output()));
    else if(c.kind()==CdR4Door.Kind.DRAW)ttDoorExpected=CdObjectPool.overlay(ttDoorExpected,List.of(c.source().moveByte(CdObjectState.ByteField.SPRITE_FLAGS,c.source().spriteFlags()&127)));
   }
   public void after(CdR4Door.Call c,Throwable failure){
    try{requireTtDoor(c);if(ttDoorCall!=c||ttDoorBefore==null)throw new IllegalStateException("Foreign Door completion");
     if(active.timeWarpSaved()!=ttDoorSaved||active.switches()!=ttDoorSwitches)throw new IllegalStateException("Door changed unrelated saved/switch bank");
     if(ttDoorAfter!=null)ttDoorAfter.check(ttDoorAfter.placed.pool());else{boolean landed=placement.capture().pool().slots().equals(ttDoorExpected.slots());ttDoorBefore.check(c.completed()||landed?ttDoorExpected:ttDoorBefore.placed.pool());}
     ttDoorOrdinal=c.ordinal();
    }finally{ttDoorCall=null;ttDoorBefore=null;ttDoorAfter=null;ttDoorExpected=null;ttDoorSaved=null;ttDoorSwitches=null;}
   }
   public int primary(CdR4Door.ReadCall c){useTtDoor(c);var m=frame.state().motion();int v;if(c.bytes()==2&&c.offset()==8)v=m.centreX16_16()>>>16;else if(c.bytes()==2&&c.offset()==12)v=m.centreY16_16()>>>16;else if(c.bytes()==1&&c.offset()==0x16)v=m.yRadius();else if(c.bytes()==1&&c.offset()==0x17)v=m.xRadius();else throw unavailable("Door reached unrelated primary operand "+c.offset()+"/"+c.bytes());ttDoorAfter=new FamilyCut();return v;}
   public int switches(CdR4Door.ReadCall c){useTtDoor(c);if(c.kind()!=CdR4Door.Kind.SWITCH_READ)throw new IllegalArgumentException("Foreign Door switch read");int v=active.switchByte(c.index());ttDoorAfter=new FamilyCut();return v;}
   public void solid(CdR4Door.Call c){useTtDoor(c);if(c.kind()!=CdR4Door.Kind.SOLID)throw new IllegalArgumentException("Foreign Door Solid");ttPlainSolid(()->{requireTtDoor(c);return true;},()->{requireTtDoor(c);ttDoorAfter=new FamilyCut();});}
   public void draw(CdR4Door.Call c){useTtDoor(c);if(c.kind()!=CdR4Door.Kind.DRAW)throw new IllegalArgumentException("Foreign Door Draw");familyDraw(placement.pool(),c.source().identity(),()->{requireTtDoor(c);return true;});ttDoorAfter=new FamilyCut();}
   public void despawn(CdR4Door.ReadCall c){useTtDoor(c);if(c.kind()!=CdR4Door.Kind.DESPAWN)throw new IllegalArgumentException("Foreign Door despawn");var row=placement.pool().state(physical);var g=frame.environment().movement().gates();var result=CdSpinTunnelTag.checkObjectDespawnAtX(row,c.index(),new CdSpinTunnelTag.DespawnGlobals(g.timeZone(),g.timeWarpDirection(),camera().xWord()));ttDoorAfter=ttApplyDespawn(ttDoorBefore,result);}
  };
  private void ttDoor(int whole){requirePhysical(whole);if(physicalId!=0x28||ttDoorCall!=null)throw new IllegalStateException("Original Door reentry");ttDoorOrdinal=0;try{CdR4Door.run(new CdR4Door.Inputs(placement.pool(),whole,ttObjects.doorData()),ttDoorOwner);}finally{ttDoorCall=null;ttDoorBefore=null;ttDoorAfter=null;ttDoorExpected=null;ttDoorSaved=null;ttDoorSwitches=null;}}
  private static final int TT_BLOCK_ID=0x34,TT_COLLAPSE_ID=0x39,TT_HMOVE_ID=0x24; // N1ecc r4/object_index_1a.asm.
  private static final int TT_SWING_ID=0x29;
  private int ttMoverId=TT_BLOCK_ID;
  private CdR4SwitchBank ttMoverSwitches;private CdTimeWarpSaveData.State ttMoverSaved;
  private CdR4SolidMover.Operation ttBlockOperation;
  private java.util.function.BooleanSupplier ttPlainSolidIssuer;private Runnable ttPlainSolidPublished;private int ttPlainSolidId=-1;
  private FamilyCut ttBlockBefore,ttBlockLanded;private CdObjectPool.Snapshot ttBlockStorePool;private long ttBlockOrdinal;
  private void ttBlockTerrain(){
   requirePhysical(physical);ttObjects.requirePlacement(placement);
   requireOriginalTtBlockTerrain(frame.stageSource(),ttSource,
       active.state().blocks().orElseThrow(()->unavailable("Block current block definitions owner")),
       active.state().layout().orElseThrow(()->unavailable("Block current foreground layout owner")));
  }
  private void requireTtBlockCall(CdR4SolidMover.Call c){
   requirePhysical(physical);if(!waterProgramme()||c.placement()!=placement||c.wholeIndex()!=physical
       ||(ttPlainSolidIssuer==null?physicalId!=ttMoverId:physicalId!=ttPlainSolidId||!ttPlainSolidIssuer.getAsBoolean()))
    throw new IllegalStateException("No original physical TT Block/plain-Solid source");
   c.requireCurrent(ttBlockOwner);ttObjects.requirePlacement(placement);
   if(frame.stageSource()!=ttSource.retainedPlayerSource())throw new IllegalStateException("Changed TT Block Frame/source");
  }
  private void useTtBlock(CdR4SolidMover.Operation c){
   requireTtBlockCall(c);if(ttBlockOperation!=c||ttBlockBefore==null)throw new IllegalStateException("No actual prepared Block operation");
   ttBlockBefore.check(ttBlockBefore.placed.pool());
  }
  private final CdR4SolidMover.LiveOwner ttBlockOwner=new CdR4SolidMover.LiveOwner(){
   public long generation(){return serviceGeneration;}
   public void requireCurrent(CdR4SolidMover.Call c){requireTtBlockCall(c);}
   public void before(CdR4SolidMover.Operation c){
    requireTtBlockCall(c);if(ttBlockOperation!=null||c.completed()||c.ordinal()!=ttBlockOrdinal+1)throw new IllegalStateException("Repeated/nested Block primitive");
    var before=new FamilyCut();sourceCall(before.placed.pool(),c.source(),physicalId);
    ttBlockOperation=c;ttBlockBefore=before;ttBlockLanded=null;ttBlockStorePool=null;
    ttMoverSwitches=active.switches();ttMoverSaved=active.timeWarpSaved();
    if(c instanceof CdR4SolidMover.StoreCall store){
     if(store.before()!=c.source()||!store.beforePool().slots().equals(before.placed.pool().slots()))throw new IllegalArgumentException("Changed Block STORE source cut");
     ttBlockStorePool=CdObjectPool.overlay(before.placed.pool(),List.of(store.output()));
    }else if(c instanceof CdR4SolidMover.ChildStoreCall store){
     if((ttMoverId!=TT_COLLAPSE_ID&&ttMoverId!=TT_HMOVE_ID&&ttMoverId!=TT_SWING_ID)||store.before()!=placement.pool().state(store.targetWhole()))throw new IllegalArgumentException("Foreign TT same-family child store");
     ttBlockStorePool=CdObjectPool.overlay(before.placed.pool(),List.of(store.output()));
    }else if(c instanceof CdR4SolidMover.DeleteCall deleted){
     if(ttMoverId!=TT_COLLAPSE_ID)throw new IllegalArgumentException("Foreign CollapseFloor DeleteObject");ttBlockStorePool=CdObjectPool.overlay(before.placed.pool(),List.of(deleted.output()));
    }else if(c instanceof CdR4SolidMover.PrimaryYCall y){
     if(ttMoverId!=TT_SWING_ID||!y.expectedPlayer().equals(floatInputs().player()))throw new IllegalArgumentException("Foreign/stale Swing primary Y store");
    }else if(c instanceof CdR4SolidMover.SolidCall solid){
     ttBlockTerrain();var current=floatInputs();
     if(!solid.beforePool().slots().equals(before.placed.pool().slots())||!solid.primary().equals(current.player())||!solid.gates().equals(current.gates())||!solid.globals().equals(current.globals()))throw new IllegalArgumentException("Changed Block primary Solid cut");
    }
    before.check(before.placed.pool());
   }
   public void after(CdR4SolidMover.Operation c){
    requireTtBlockCall(c);if(ttBlockOperation!=c||ttBlockBefore==null)throw new IllegalStateException("No Block completion scope");
    try{
     if(active.switches()!=ttMoverSwitches||active.timeWarpSaved()!=ttMoverSaved)throw new IllegalStateException("TT mover changed unrelated switch/save owner");
     if(c instanceof CdR4SolidMover.StoreCall||c instanceof CdR4SolidMover.ChildStoreCall||c instanceof CdR4SolidMover.DeleteCall)ttBlockBefore.check(c.completed()?ttBlockStorePool:ttBlockBefore.placed.pool());
     else if(ttBlockLanded!=null)ttBlockLanded.check(ttBlockLanded.placed.pool());
     else ttBlockBefore.check(ttBlockBefore.placed.pool());
     ttBlockOrdinal=c.ordinal();
    }finally{ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;}
   }
   public void solid(CdR4SolidMover.SolidCall c){
    useTtBlock(c);if(ttMoverId==TT_COLLAPSE_ID){if(c.entry()!=CdSolidObject.Entry.TOP_SOLID_OBJECT&&c.entry()!=CdSolidObject.Entry.GET_OFF_OBJECT)throw new IllegalArgumentException("Foreign CollapseFloor Solid entry");}
    else if(ttMoverId==TT_SWING_ID){if(c.entry()!=CdSolidObject.Entry.TOP_SOLID_OBJECT)throw new IllegalArgumentException("Swing reaches TopSolid only");}
    else if(c.entry()!=CdSolidObject.Entry.SOLID_OBJECT)throw new IllegalArgumentException("Block only reaches native plain Solid");
    familySolid(()->{requireTtBlockCall(c);return true;},c.beforePool(),c.primary(),c.globals(),c.outcome(),R11.this::ttBlockTerrain);
    ttBlockLanded=new FamilyCut();
    if(ttPlainSolidPublished!=null){ttPlainSolidPublished.run();ttBlockLanded.check(ttBlockLanded.placed.pool());}
   }
   public CdPlayerMath.SineCosine sine(CdR4SolidMover.SineCall c){
    useTtBlock(c);if(ttMoverId!=TT_HMOVE_ID&&ttMoverId!=TT_SWING_ID)throw new IllegalArgumentException("Foreign original mover sine occurrence");
    return new CdPlayerMath(frame.stageSource().player()).calcSine(c.angle());
   }
   public void primaryY(CdR4SolidMover.PrimaryYCall c){
    useTtBlock(c);if(ttMoverId!=TT_SWING_ID||!c.expectedPlayer().equals(floatInputs().player()))throw new IllegalArgumentException("Stale source-issued Swing alignment");
    ttBlockBefore.publish(null,frame.prepareObjectYWord(frame.state(),frame.environment(),frame.state().motion().centreY16_16()>>>16,c.word()),null,List.of());
    ttBlockLanded=new FamilyCut();if(!c.output().equals(floatInputs().player()))throw new IllegalStateException("Swing alignment changed another primary field");
   }
   public void sound(CdR4SolidMover.SoundCall c){
    useTtBlock(c);if(ttMoverId!=TT_COLLAPSE_ID||c.nativeId()!=0xA3)throw new IllegalArgumentException("Foreign CollapseFloor FM");
    ttBlockBefore.publish(null,null,null,List.of(new SonicCdGameModule.PastCollapsePlatformFm(c.nativeId())));ttBlockLanded=new FamilyCut();
   }
   public void tail(CdR4SolidMover.TailCall c){ttBlockTail(c);}
  };
  private void ttBlock(int whole){
   requirePhysical(whole);if(physicalId!=TT_BLOCK_ID||ttBlockOperation!=null)throw new IllegalStateException("Block dispatch reentry");ttBlockOrdinal=0;
   var current=floatInputs();
   try{CdR4Block.run(new CdR4SolidMover.PrimaryInputs(placement,whole,current.player(),current.gates(),current.globals(),ttBlockSolid,ttObjects.blockData()),ttBlockOwner);}
   finally{ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;}
  }
  private void ttCollapseFloor(int whole){
   requirePhysical(whole);if(physicalId!=TT_COLLAPSE_ID||ttBlockOperation!=null||ttPlainSolidIssuer!=null)throw new IllegalStateException("CollapseFloor dispatch reentry");
   ttMoverId=TT_COLLAPSE_ID;ttBlockOrdinal=0;
   try{var current=floatInputs();CdR4CollapseFloor.run(new CdR4SolidMover.PrimaryInputs(placement,whole,current.player(),current.gates(),current.globals(),ttBlockSolid,ttObjects.collapseFloorData()),ttBlockOwner);}
   finally{ttMoverId=TT_BLOCK_ID;ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;}
  }
  private void ttHMove(int whole){
   requirePhysical(whole);if(physicalId!=TT_HMOVE_ID||ttBlockOperation!=null||ttPlainSolidIssuer!=null)throw new IllegalStateException("HMove dispatch reentry");
   ttMoverId=TT_HMOVE_ID;ttBlockOrdinal=0;
   try{var current=floatInputs();CdR4HMoveBlock.run(new CdR4SolidMover.PrimaryInputs(placement,whole,current.player(),current.gates(),current.globals(),ttBlockSolid,ttObjects.hMoveData()),ttBlockOwner);}
   finally{ttMoverId=TT_BLOCK_ID;ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;}
  }
  private void ttSwing(int whole){
   requirePhysical(whole);if(physicalId!=TT_SWING_ID||ttBlockOperation!=null||ttPlainSolidIssuer!=null)throw new IllegalStateException("Swing dispatch reentry");
   ttMoverId=TT_SWING_ID;ttBlockOrdinal=0;
   try{var p=floatInputs();CdR4Swing.run(new CdR4SolidMover.PrimaryInputs(placement,whole,p.player(),p.gates(),p.globals(),ttBlockSolid,ttObjects.swingData()),ttBlockOwner);}
   finally{ttMoverId=TT_BLOCK_ID;ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;ttMoverSwitches=null;ttMoverSaved=null;}
  }
  /** Existing source-issued plain Solid component shared with another actual TT handler.
   * The private enclosing handler proves its own current receipt; unrelated DATA reads reject. */
  private int ttPlainSolid(java.util.function.BooleanSupplier actual){return ttPlainSolid(actual,()->{});}
  private int ttPlainSolid(java.util.function.BooleanSupplier actual,Runnable published){
   requirePhysical(physical);Objects.requireNonNull(actual);Objects.requireNonNull(published);if(!waterProgramme()||!actual.getAsBoolean()||ttBlockOperation!=null||ttPlainSolidIssuer!=null)throw new IllegalStateException("No actual TT plain-Solid caller");
   ttPlainSolidIssuer=actual;ttPlainSolidPublished=published;ttPlainSolidId=physicalId;ttBlockOrdinal=0;
   var data=new CdR4SolidMover.Data(){
    private UnsupportedOperationException missing(Object operand){ttObjects.requireRetained();return unavailable("plain Solid has no unrelated DATA read "+operand);}
    public int mapping(CdR4SolidMover.Mapping m){throw missing(m);}public CdAssetBytes table(CdR4SolidMover.Table t){throw missing(t);}public int address(CdR4SolidMover.Address a){throw missing(a);}
   };
   try{var current=floatInputs();var result=CdR4SolidMover.runPlainSolid(new CdR4SolidMover.PrimaryInputs(placement,physical,current.player(),current.gates(),current.globals(),ttBlockSolid,data),ttBlockOwner);
    if(!actual.getAsBoolean())throw new IllegalStateException("Expired actual TT plain-Solid caller");return result;
   }finally{ttPlainSolidIssuer=null;ttPlainSolidPublished=null;ttPlainSolidId=-1;ttBlockOperation=null;ttBlockBefore=null;ttBlockLanded=null;ttBlockStorePool=null;}
  }
  private void ttBlockTail(CdR4SolidMover.TailCall c){
   useTtBlock(c);
   if(ttMoverId==TT_COLLAPSE_ID){
    if(c.tail()==CdR4SolidMover.Tail.NONE)return;
    if(c.tail()==CdR4SolidMover.Tail.DRAW){familyDraw(placement.pool(),c.source().identity(),()->{requireTtBlockCall(c);return true;});ttBlockLanded=new FamilyCut();return;}
   }
   if(ttMoverId==TT_SWING_ID){
    if(c.tail()!=CdR4SolidMover.Tail.DRAW_SWING_WINDOW)throw new IllegalArgumentException("Foreign Swing tail");
    familyDraw(placement.pool(),c.source().identity(),()->{requireTtBlockCall(c);return true;});ttBlockLanded=new FamilyCut();
    var row=placement.pool().state(physical);int anchor=CdAssetBytes.copyOf(row.copyBytes()).unsignedWord(0x36);
    if(CdSpinTunnelTag.outsideCameraWindow(anchor,camera().xWord())){var before=ttBlockLanded;var staged=CdObjectPool.stagingCopy(before.placed.pool());staged.deleteObject(physical);before.publish(staged.capture(),null,null,List.of());ttBlockLanded=new FamilyCut();}
    return; // Swing uses DeleteObject, never the saved-loaded-BCLR Despawn helper.
   }
   boolean hMove=ttMoverId==TT_HMOVE_ID;
   if(c.tail()!=(hMove?CdR4SolidMover.Tail.DRAW_H_WINDOW:CdR4SolidMover.Tail.DRAW_DESPAWN_X))throw new IllegalArgumentException("Foreign original TT mover tail");
   familyDraw(placement.pool(),c.source().identity(),()->{requireTtBlockCall(c);return true;});ttBlockLanded=new FamilyCut();
   // Native Draw has returned. Reload its real bit7 before invoking the one existing common helper.
   var before=ttBlockLanded;var row=placement.pool().state(physical);var gates=frame.environment().movement().gates();
   CdSpinTunnelTag.DespawnResult tail;
   if(hMove){
    // loc_20C224 always tests anchor36, regardless of Draw bit7; it has no warp gate.
    if(!CdSpinTunnelTag.outsideCameraWindow(CdAssetBytes.copyOf(row.copyBytes()).unsignedWord(0x36),camera().xWord()))return;
    int savedId=row.savedFlagsId();
    if(savedId==0)tail=new CdSpinTunnelTag.DespawnResult(true,Optional.empty());
    else{int period=gates.timeZone();if(period>2)throw unavailable("HMove raw period saved-BYTE alias "+period);tail=new CdSpinTunnelTag.DespawnResult(true,Optional.of(new CdSpinTunnelTag.LoadedClear(savedId,period,2+3*savedId+period)));}
   }else tail=CdSpinTunnelTag.checkObjectDespawnAtX(row,row.centreX(),new CdSpinTunnelTag.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),camera().xWord()));
   ttBlockLanded=ttApplyDespawn(before,tail);
  }
  /** Existing source-equivalent saved-BCLR then DeleteObject owner shared by TT tails. */
  private FamilyCut ttApplyDespawn(FamilyCut before,CdSpinTunnelTag.DespawnResult tail){
   before.check(before.placed.pool());if(!tail.deleteObject())return before;
   var landed=before;var staged=CdObjectPool.stagingCopy(before.placed.pool());staged.deleteObject(physical);var deleted=staged.capture();
   var saved=before.placed.savedFlags();
   if(tail.loadedClear().isPresent()){
    var clear=tail.loadedClear().orElseThrow();Objects.checkIndex(clear.index(),CdObjectPlacementRuntime.SAVED_BANK_BYTES);
    int old=placement.savedFlag(clear.savedId(),clear.period());var bytes=saved.copy();
    if((bytes[clear.index()]&255)!=old)throw new IllegalStateException("Changed native despawn saved BYTE");bytes[clear.index()]=(byte)(old&127);saved=CdAssetBytes.copyOf(bytes);
    before.check(before.placed.pool());placement.bclrSavedFlag(clear.savedId(),clear.period(),CdObjectPlacementRuntime.SavedBit.LOADED);
    before.check(before.placed.pool(),saved);landed=new FamilyCut();
   }
   // Original BCLR precedes DeleteObject. Complete bank/pool checks precede each callback-free write.
   landed.check(landed.placed.pool());placement.pool().deleteObject(physical);
   before.check(deleted,saved);landed=new FamilyCut();
   return landed;
  }

  private static final int TT_BUBBLE_SPAWNER_ID=0x20;
  private static final int TT_WATER_SURFACE_ID=0x04; // N1ecc r4/object_index_1a.asm: WaterSurfaceObject.
  private boolean waterProgramme(){return ttSource!=null;}
  private boolean standardPastProgramme(){return pastSource!=null;}
  private boolean standardSourcePair(){
   if(!standardPastProgramme())return frame.stageSource().ownsPresent(assets)&&art.ownsLoadingAssets(assets)&&placement.placements()==assets.placements();
   pastObjects.requirePlacement(placement);
   return pastSource.standardPastObjectSource()==pastObjects&&pastSource.retainedPlayerSource()==frame.stageSource()
       &&art.ownsLoadingSource(pastSource,frame.stageSource());
  }
  private long serviceGeneration;private CdPlayerFrameRuntime.OriginalProgrammeCall bodyDrawCall;
  private void live(){if(active==null||closed||frame.closed()||Thread.currentThread()!=thread)throw new IllegalStateException("No actual original R11 scope");active.requireOwner(CdWorld.this);}
  @Override void idle(){module.requireOriginalR11WorldIdle(CdWorld.this);if(physical!=-1||drawing||draining||queue!=null||ttDoorCall!=null||ttSwitchCall!=null||ttBlockOperation!=null||monitorItemOperation!=null||monitorShieldOperation!=null||tunnelOperation!=null||waterfallOperation!=null||waterfallDispatch||flowerInvocation!=null||monitorExplosionDispatch||monitorEffectOperation!=null||originalWarpEntry!=null||starOperation!=null||enemyIssuer!=null||enemyOperation!=null||pastMosquiRunning||pastMosquiOperation!=null||lostOccurrence!=null||mechanismOperation!=null||currentTailCut!=null||currentStore!=null||ttAirInvocation!=null||ttDrownWrite!=null)throw new IllegalStateException("Unclosed original object work");}
  @Override void require(){live();}
  @Override Object scope(){return active;}
  @Override CdOrdinaryLoop.Ports ports(){return new Ports();}
  @Override void clear(){active=null;ttSwitchCall=null;ttSwitchBefore=null;ttSwitchAfter=null;ttSwitchExpectedPool=null;ttSwitchBank=null;ttSwitchSaved=null;ttBlockOperation=null;ttMoverSwitches=null;ttMoverSaved=null;ttDoorCall=null;ttDoorBefore=null;ttDoorAfter=null;ttDoorExpected=null;ttDoorSaved=null;ttDoorSwitches=null;selectedWaterCpu=Optional.empty();bodyDrawCall=null;physical=-1;physicalId=0;drawing=false;draining=false;walkReturned=false;monitorItemOperation=null;monitorShieldOperation=null;tunnelOperation=null;waterfallOperation=null;waterfallDispatch=false;flowerInvocation=null;monitorEffectOperation=null;monitorExplosionDispatch=false;originalWarpEntry=null;originalWarpOperation=null;originalWarpExpected=null;starOperation=null;starDispatch=false;enemyOperation=null;enemyBefore=null;enemyAfter=null;enemyIssuer=null;pastMosquiRunning=false;pastMosquiOperation=null;pastMosquiOrdinal=0;lostOccurrence=null;mechanismOperation=null;currentTailCut=null;currentStore=null;}
  @Override void discard(){if(queue!=null){queue.close();queue=null;}}
  @Override void playerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete){requireCut(Cut.PUBLISH_LEVEL);CdOriginalWorldBindings.requirePlayerArt(request,complete);active.refresh();}
  private void sound(SonicCdGameModule.PastSound value){live();var next=new ArrayList<>(sounds);next.add(Objects.requireNonNull(value));sounds=List.copyOf(next);}
  private void globals(SonicCdR31aWorldHost.Globals ready){var s=active.state();active.state(s,s.globals(ready));}
  private void ring(CdRingGlobals ready){var g=active.state().globals();globals(SonicCdR31aWorldHost.with(g,ready,g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
  private void requirePhysical(int whole){requireCut(Cut.RUN_OBJECTS);if(physical!=whole||drawing||draining)throw new IllegalStateException("Foreign actual physical callback");}
  private CdAssetBytes row(int whole){if(whole<1||whole>31)throw new IllegalArgumentException("Reserved whole slot");return active.state().reserved().slice((whole-1)*64,64);}
  private void sourceCall(CdObjectPool.Snapshot cut,CdObjectState source,int id){requirePhysical(source.identity().wholeIndex());if(physicalId!=id||source!=placement.pool().state(physical))throw new IllegalStateException("Foreign native object call");placement.pool().validateRestore(cut);if(!cut.slots().equals(placement.pool().capture().slots()))throw new IllegalStateException("Changed native pool cut");}
  private void titleSource(CdTitleCard.SourceCut cut){requirePhysical(cut.wholeIndex());if(physicalId!=CdTitleCard.OBJECT_ID)throw new IllegalStateException("No actual title dispatch");placement.validateRestore(cut.actualPlacement());if(!cut.actualPlacement().pool().slots().equals(placement.pool().capture().slots()))throw new IllegalStateException("Changed title pool");var actual=physical<32?row(physical):CdAssetBytes.copyOf(placement.pool().state(physical).copyBytes());if(!Arrays.equals(actual.copy(),cut.expectedSource().copy()))throw new IllegalStateException("Changed actual title source");}
  private int titleRead(CdTitleCard.ReadCall call){titleSource(call.cut());return call.field()==CdTitleCard.ReadField.ACT_BYTE?frame.environment().movement().gates().zoneAct()&255:(int)active.prefix().section().queue().headSource();}
  private void titleStore(CdTitleCard.StoreCall call){titleSource(call.cut());if(call.field()==CdTitleCard.StoreField.CONTROL_LOCK_BYTE){var p=frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),call.field(),call.value());frame.validatePreparedObjectSolidPublication(p);frame.applyPreparedObjectSolidPublication(p);}else{var s=active.state().scroll();var f=s.foreground();var n=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),(call.value()<<8)|(f.scrollLock()&255));active.scroll(s,new SonicCdR31aLoadingVisit.Scroll(n,s.background(),s.copies(),s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord()));}}
  private record MonitorFamily(CdReservedObjectBank reserved,CdMonitor monitor,CdMonitorItem item,CdShieldPowerup shield){}
  private MonitorFamily newMonitorFamily(CdMonitor.Assets data,CdTimeStars.Assets powerup,CdSolidObject solid,CdPlayerTerrain terrain,CdShieldPowerup.LayerPolicy layerPolicy){
   var reserved=newShieldBank();
   // withPastOwner is the retained API name for the existing semantic prefix-Solid mode.
   // Each actual constructor supplies its authenticated programme data and compiled semantic profile.
   var body=CdMonitor.withWorldInvocationOwner(placement,data,solid,terrain,new CdMonitor.PrefixSolidOwner(){
    public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut call){return monitorSolidPreflight(call);}
    public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall call){return monitorSolidCommit(call);}
   },this::monitorPrimaryAngle,this::drawDynamic,value->{requireMonitor();sound(new SonicCdGameModule.PastMonitorFm(value));},whole->{requireMonitor();if(physical!=whole)throw new IllegalStateException("Foreign Monitor invocation slot");});
   var item=CdMonitorItem.withWorldOwner(placement,data,this::monitorShoes,this::monitorShieldAward,this::monitorRings,this::drawDynamic,itemObserver(),itemGuard());
   var shield=newShield(reserved,powerup,layerPolicy);
   return new MonitorFamily(reserved,body,item,shield);
  }
  private CdReservedObjectBank newShieldBank(){
   return CdReservedObjectBank.backedBy(new CdReservedObjectBank.RawBacking(){
    public CdAssetBytes read(){live();return active.state().reserved();}
    public boolean compareAndSet(CdAssetBytes before,CdAssetBytes next){return monitorReservedStore(before,next);}
   });
  }
  private CdShieldPowerup newShield(CdReservedObjectBank reserved,CdTimeStars.Assets powerup,CdShieldPowerup.LayerPolicy layerPolicy){
   return CdShieldPowerup.withWorldOwnerLayerPolicy(reserved,powerup,this::monitorShieldRead,new CdShieldPowerup.PowerupPort(){
    public int read(){shieldPrimitive(CdShieldPowerup.PowerupReadCall.class);return active.prefix().main().level().powerup();}
    public void write(int value){var call=shieldPrimitive(CdShieldPowerup.PowerupWriteCall.class);var old=active.prefix().main().level();if(call.previousValue()!=old.powerup()||call.value()!=value)throw new IllegalStateException("Changed original powerup write");active.level(old,new CdLevelGlobals(old.levelRestart(),old.updateHudTime(),old.checkpoint(),old.spawnMode(),old.plcLoadFlags(),old.flowerCount(),old.fadeEnableDisplay(),value,old.timeOver()));}
   },(bank,identity)->{shieldPrimitive(CdShieldPowerup.DrawCall.class);if(bank!=monitorReserved||identity.wholeIndex()!=6)throw new IllegalArgumentException("Foreign original Shield Draw");drawReserved(6);},shieldObserver(),shieldGuard(),layerPolicy);
  }
  private void requireMonitorFamilySource(){
   requirePhysical(physical);if(standardPastProgramme()&&!standardSourcePair())throw new IllegalStateException("Changed original Past Shield source/Frame/current-art pair");if(waterProgramme()){ttObjects.requirePlacement(placement);if(frame.stageSource()!=ttSource.retainedPlayerSource())throw new IllegalStateException("Changed original TT Monitor family source/Frame");}
  }
  private void monitorPrimaryAngle(int before,int value){
   requireMonitor();var p=frame.prepareObjectPrimaryAngle(before,value);frame.validatePreparedObjectSolidPublication(p);frame.applyPreparedObjectSolidPublication(p);
   if(waterProgramme())ttBlockTerrain(); // Actual prezero lands before an unowned current floor read stops.
  }
  private void monitorTime(int whole){
   requirePhysical(whole);requireMonitorFamilySource();if(placement.pool().state(whole).subtype()>=8)timePost(whole);else monitor.runPast(whole,monitorGlobals(),monitorInputs());
  }
  private void requireMonitor(){requireMonitorFamilySource();if(physical<32||physicalId!=CdMonitor.OBJECT_ID)throw new IllegalStateException("No original R11 Monitor occurrence");}
  private CdMonitor.LiveSolid monitorLiveSolid(){var s=frame.state();var h=s.header();var e=frame.environment();var g=e.movement().globals();return new CdMonitor.LiveSolid(new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject()),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle()));}
  private CdMonitor.SolidInputs monitorInputs(){var v=monitorLiveSolid();return new CdMonitor.SolidInputs(v.player(),v.gates(),v.globals());}
  private CdMonitor.PastGlobals monitorGlobals(){return new CdMonitor.PastGlobals(){
   public int rawTimeZone(){requireMonitor();return frame.environment().movement().gates().timeZone();}
   public int rawTimeWarpDirection(){requireMonitor();return frame.environment().movement().gates().timeWarpDirection();}
   public int cameraXWord(){requireMonitor();return camera().xWord();}
   public int timeAttackMode(){requireMonitor();return frame.environment().globals().timeAttackMode();}
   public int zone(){requireMonitor();return frame.environment().movement().gates().zoneAct()>>>8;}
   public int layer(){requireMonitor();return active.state().globals().layerByte().orElseThrow(()->new UnsupportedOperationException("Original R11 layer BYTE owner"));}
   public int timeStopTimer(){requireMonitor();return active.state().globals().ring().timeStopTimer();}
  };}
  private CdMonitor.LiveSolid monitorSolidPreflight(CdMonitor.PrefixSolidCut call){requireMonitor();if(waterProgramme())ttBlockTerrain();if(call.objectIdentity().wholeIndex()!=physical)throw new IllegalArgumentException("Foreign Monitor Solid a0");sourceCall(call.callCut(),call.callCut().slots().get(physical-32),CdMonitor.OBJECT_ID);var actual=monitorLiveSolid();if(!actual.player().equals(call.expectedPlayer())||!actual.gates().equals(call.expectedGates())||!actual.globals().equals(call.expectedGlobals()))throw new IllegalStateException("Changed original Monitor primary cut");return actual;}
  private CdMonitor.LiveSolid monitorSolidCommit(CdMonitor.PrefixSolidCall call){
   var cut=call.cut();monitorSolidPreflight(cut);var outcome=call.outcome();CdObjectState object;List<CdObjectState> others;List<com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication publication;
   if(outcome instanceof CdSolidObject.Completed done){var r=done.completedResult();object=r.object();others=r.otherSlots();fm=r.fmRequests();publication=frame.prepareObjectSolidResult(cut.expectedPlayer(),cut.expectedGlobals(),r);}
   else{var p=((CdSolidObject.NativeStopped)outcome).prefix();object=p.object();others=p.otherSlots();fm=p.fmRequests();publication=frame.prepareObjectSolidPrefix(cut.expectedPlayer(),cut.expectedGlobals(),p);}
   var readySounds=new ArrayList<SonicCdGameModule.PastSound>(sounds);
   for(var request:fm)readySounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(request)));
   var soundLedger=List.copyOf(readySounds);
   var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var ready=CdObjectPool.overlay(cut.callCut(),images);placement.pool().validateRestore(ready);frame.validatePreparedObjectSolidPublication(publication);monitorSolidPreflight(cut);
   // Both aggregates were prepared before the first store. This adopts the genuine current
   // shared Outcome, not its effect metadata; the component rethrows NativeStopped unchanged.
   placement.pool().restore(ready);frame.applyPreparedObjectSolidPublication(publication);sounds=soundLedger;return monitorLiveSolid();
  }
  private void requireItemOperation(Object call){requireMonitorFamilySource();if(physical<32||physicalId!=CdMonitorItem.OBJECT_ID||call==null||monitorItemOperation!=call||!monitorItem.isCurrentCall(call))throw new IllegalStateException("No original R11 Monitor Item operation");}
  private CdMonitorItem.DispatchObserver itemObserver(){return new CdMonitorItem.DispatchObserver(){
   public void beforeOperation(Object call){requireMonitorFamilySource();if(physical<32||physicalId!=CdMonitorItem.OBJECT_ID||monitorItemOperation!=null||!monitorItem.isCurrentCall(call))throw new IllegalStateException("Foreign original Item occurrence");monitorItemOperation=call;}
   public void afterOperation(Object call){requireItemOperation(call);}
   public void failedOperation(Object call,Throwable original){if(monitorItemOperation==call)monitorItemOperation=null;}
  };}
  private CdMonitorItem.ReceiptGuard itemGuard(){return new CdMonitorItem.ReceiptGuard(){public void prepared(Object call){requireItemOperation(call);}public void completed(Object call){try{requireItemOperation(call);}finally{monitorItemOperation=null;}}};}
  private <T>T itemPrimitive(Class<T> type){var call=monitorItemOperation;requireItemOperation(call);if(!type.isInstance(call)||!monitorItem.isCurrentPrimitive(call))throw new IllegalStateException("No original Item primitive");return type.cast(call);}
  private CdMonitorItem.RingAwardResult monitorRings(CdMonitorItem.RingAwardCall request){var call=itemPrimitive(CdMonitorItem.RingCall.class);if(call.request()!=request||request.identity().wholeIndex()!=physical)throw new IllegalArgumentException("Foreign original Rings request");sourceCall(call.cut().pool(),call.source(),CdMonitorItem.OBJECT_ID);var before=active.state().globals().ring();var reward=before.monitorItemTen();var value=reward.oneUp()?CdMonitorItem.RingAwardSound.SCMD_YESSFX:CdMonitorItem.RingAwardSound.FM_RING;var result=new CdMonitorItem.RingAwardResult(value);ring(reward.globals());sound(new SonicCdGameModule.PastMonitorItemRing(value));return result;}
  private CdMonitorItem.ShieldAwardResult monitorShieldAward(CdMonitorItem.ShieldAwardCall request){var call=itemPrimitive(CdMonitorItem.ShieldCall.class);if(call.request()!=request||request.identity().wholeIndex()!=physical)throw new IllegalArgumentException("Foreign original Shield request");sourceCall(call.cut().pool(),call.source(),CdMonitorItem.OBJECT_ID);var g=active.state().globals();var old=g.contact();var next=new CdPlayerBody.ContactGlobals(old.timeWarp(),1,old.combineRing());var result=new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);
   // Native GiveShield: contact BYTE, then only whole6 ID BYTE, then FM; no recursive child run.
   globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),next,g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));monitorReserved.write(monitorReserved.state(6).moveByte(CdObjectState.ByteField.ID,3));sound(new SonicCdGameModule.PastMonitorItemShieldFm(CdMonitorItem.ShieldSound.FM_SHIELD));return result;}
  private CdMonitorItem.AwardResult monitorShoes(CdMonitorItem.AwardCall request){var call=itemPrimitive(CdMonitorItem.ShoesCall.class);if(call.request()!=request||request.identity().wholeIndex()!=physical)throw new IllegalArgumentException("Foreign original Shoes request");sourceCall(call.cut().pool(),call.source(),CdMonitorItem.OBJECT_ID);var m=frame.environment().movement();var before=new CdPlayerSpeedShoesReward.State(m.gates().speedShoes(),frame.state().timers().speedShoes(),m.physics());var reward=CdPlayerSpeedShoesReward.give(before,frame.stageSource().variant(),m.gates().timeZone());var prepared=frame.prepareObjectSpeedShoesReward(before,reward);var result=new CdMonitorItem.AwardResult(reward.commands().stream().map(command->switch(command){case SCMD_FADEPCM->CdMonitorItem.AwardSound.SCMD_FADEPCM;case SCMD_SHOESMUS->CdMonitorItem.AwardSound.SCMD_SHOESMUS;}).toList());frame.validatePreparedObjectSolidPublication(prepared);frame.applyPreparedObjectSolidPublication(prepared);for(var command:reward.commands())sound(new SonicCdGameModule.PastSpeedShoesSubCpu(command));return result;}
  private void requireShieldOperation(CdShieldPowerup.Call call){requireMonitorFamilySource();requirePhysical(6);if(physicalId!=CdShieldPowerup.OBJECT_ID||call==null||monitorShieldOperation!=call||!monitorShield.isCurrentCall(call))throw new IllegalStateException("No original R11 Shield operation");}
  private CdShieldPowerup.DispatchObserver shieldObserver(){return new CdShieldPowerup.DispatchObserver(){public void beforeOperation(CdShieldPowerup.Call call){requireMonitorFamilySource();requirePhysical(6);if(physicalId!=3||monitorShieldOperation!=null||!monitorShield.isCurrentCall(call))throw new IllegalStateException("Foreign original Shield occurrence");monitorShieldOperation=call;}public void afterOperation(CdShieldPowerup.Call call){requireShieldOperation(call);}public void failedOperation(CdShieldPowerup.Call call,Throwable original){if(monitorShieldOperation==call)monitorShieldOperation=null;}};}
  private CdShieldPowerup.ReceiptGuard shieldGuard(){return new CdShieldPowerup.ReceiptGuard(){public void prepared(CdShieldPowerup.Call call){requireShieldOperation(call);}public void completed(CdShieldPowerup.Call call){try{requireShieldOperation(call);}finally{monitorShieldOperation=null;}}};}
  private <T extends CdShieldPowerup.Call>T shieldPrimitive(Class<T> type){var call=monitorShieldOperation;requireShieldOperation(call);if(!type.isInstance(call)||!monitorShield.isCurrentPrimitive(call))throw new IllegalStateException("No original Shield primitive");return type.cast(call);}
  private int monitorShieldRead(CdShieldPowerup.ReadField field){var call=shieldPrimitive(CdShieldPowerup.ReadCall.class);if(call.field()!=field)throw new IllegalStateException("Foreign original Shield read");var m=frame.state().motion();var e=frame.environment();return switch(field){case SHIELD->active.state().globals().contact().shield();case TIME_WARP->active.state().globals().contact().timeWarp();case INVINCIBLE->e.globals().invincible();case PLAYER_X_WORD->m.centreX16_16()>>>16;case PLAYER_Y_WORD->m.centreY16_16()>>>16;case PLAYER_FLAGS->m.flags();case ZONE->e.movement().gates().zoneAct()>>>8;case LAYER->active.state().globals().layerByte().orElseThrow(()->new UnsupportedOperationException("Original R11 Shield layer BYTE owner"));};}
  private boolean monitorReservedStore(CdAssetBytes before,CdAssetBytes next){
   live();requirePhysical(physical);if(originalWarpOperation!=null&&physical==0)return warpReservedStore(before,next);if(starOperation!=null&&physical>=12&&physical<=15)return starReservedStore(before,next);if(before.length()!=31*64||next.length()!=31*64)throw new IllegalArgumentException("Complete original reserved bank required");var actual=active.state().reserved();if(!Arrays.equals(actual.copy(),before.copy()))return false;var old=before.slice(5*64,64);var ready=next.slice(5*64,64);var a=before.copy();var b=next.copy();for(int at=0;at<a.length;at++)if((at<5*64||at>=6*64)&&a[at]!=b[at])throw new IllegalStateException("Monitor graph changed another reserved slot");
   if(physical==6){var call=shieldPrimitive(CdShieldPowerup.Publication.class);if(!Arrays.equals(old.copy(),call.source().copyBytes())||!Arrays.equals(ready.copy(),call.output().copyBytes()))throw new IllegalStateException("Changed original Shield publication");}
   else{itemPrimitive(CdMonitorItem.ShieldCall.class);var expected=old.copy();expected[0]=3;if(!Arrays.equals(expected,ready.copy()))throw new IllegalStateException("Shield award owns only whole6 ID BYTE");}
   active.reserved(6,old,ready);return true;
  }
  private OriginalR11TimeWarpEntry originalWarpEntry;private R11WarpOperation originalWarpOperation;private R11WarpCut originalWarpExpected;
  private boolean starDispatch;private StarOperation starOperation;
  private final class R11WarpCut {
   final SonicCdGameModule.R11LoadingState state=active.state();final SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
   final CdTimeWarpSaveData.State saved=active.timeWarpSaved();
   final Object actor=frame.state(),environment=frame.environment(),display=frame.display(),artImage=frame.artState(),clock=frame.vIntClock();
   final boolean paused=frame.paused();final CdObjectPlacementRuntime.Snapshot placed=placement.capture();
   final List<SonicCdGameModule.PastSound> oldSounds=sounds;final CdObjectDrawQueue oldQueue=queue;final Optional<CdObjectDrawQueue.CpuSat> oldCpu=cpu;
   void nonPlayer(){requirePhysical(physical);var now=placement.capture();if(active.timeWarpSaved()!=saved||active.state()!=state||active.prefix()!=prefix||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=oldSounds||queue!=oldQueue||cpu!=oldCpu||!now.status().equals(placed.status())||!same(now.savedFlags(),placed.savedFlags())||!now.pool().slots().equals(placed.pool().slots()))throw new IllegalStateException("Changed full original TimeWarp/TimeStars nonwrites");}
   void check(){nonPlayer();if(frame.state()!=actor||frame.environment()!=environment)throw new IllegalStateException("Changed original TimeWarp/TimeStars Frame pair");}
   void checkActive(SonicCdGameModule.R11LoadingState ready,SonicCdGameModule.R11LoadingPrefix readyPrefix,Object readyEnvironment,CdTimeWarpSaveData.State readySaved,List<SonicCdGameModule.PastSound> readySounds){
    requirePhysical(physical);var now=placement.capture();if(!active.state().equals(ready)||!active.prefix().equals(readyPrefix)||active.timeWarpSaved()!=readySaved||frame.state()!=actor||frame.environment()!=readyEnvironment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!sounds.equals(readySounds)||queue!=oldQueue||cpu!=oldCpu||!now.status().equals(placed.status())||!same(now.savedFlags(),placed.savedFlags())||!now.pool().slots().equals(placed.pool().slots()))throw new IllegalStateException("Native TimeWarp active store changed a complete nonwrite");
   }
  }
  private CdPlayerFrameRuntime frameValue(){return frame;}
  private void requireWarpEntry(OriginalR11TimeWarpEntry entry){
   requirePhysical(0);if(entry==null||originalWarpEntry!=entry||entry.owner!=this||entry.active!=active||entry.generation!=serviceGeneration||entry.thread!=Thread.currentThread()||entry.retired||waterProgramme()||physicalId!=1||queue==null||!standardSourcePair())throw new IllegalStateException("No original source-issued R11 TimeWarp entry");
  }
  private final class R11WarpOperation {
   final CdPlayerFrameRuntime.OriginalR11TimeWarpCall frameCall;final CdPlayerTimeWarp.OriginalCall call;R11WarpCut expected;boolean used,reservedWritten;OriginalR11TimeWarpSaveStore savedStore;
   R11WarpOperation(CdPlayerFrameRuntime.OriginalR11TimeWarpCall frameCall,CdPlayerTimeWarp.OriginalCall call){this.frameCall=frameCall;this.call=call;expected=originalWarpExpected;}
  }
  private R11WarpOperation warpOperation(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive,boolean use){
   requireWarpEntry(originalWarpEntry);call.requireEntry(originalWarpEntry);primitive.requireCurrent(call);var op=originalWarpOperation;
   if(op==null||op.frameCall!=call||op.call!=primitive)throw new IllegalStateException("Foreign/retired original TimeWarp operation");op.expected.check();
   if(use){primitive.requirePrimitive(call);if(op.used)throw new IllegalStateException("Repeated original TimeWarp primitive");op.used=true;}return op;
  }
  private void warpBefore(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){
   requireWarpEntry(originalWarpEntry);call.requireEntry(originalWarpEntry);primitive.requireCurrent(call);if(originalWarpOperation!=null||originalWarpExpected==null||primitive.ordinal()!=originalWarpEntry.ordinal+1)throw new IllegalStateException("Nested/out-of-order original TimeWarp operation");originalWarpExpected.check();originalWarpOperation=new R11WarpOperation(call,primitive);
  }
  private void requireOriginalStarSource(){
   live();if(waterProgramme()||!frame.stageSource().ownsPresent(assets)||!art.ownsLoadingAssets(assets)||placement.placements()!=assets.placements()||!metadata.identity().equals(assets.identity())||!active.state().clears().contains(CdLevelStartStorage.Cut.OBJECTS))throw new IllegalStateException("No genuine original R11 reserved clear/source");
   var expected=CdTimeStars.Assets.fromObjectAssets(metadata);if(timeStars.assets().mappingAddressBits()!=expected.mappingAddressBits()||timeStars.assets().animations().bytes()!=expected.animations().bytes())throw new IllegalStateException("Changed original TimeStars source views");
  }
  private CdReservedObjectBank.Snapshot warpReserved(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){var op=warpOperation(call,primitive,true);if(primitive.site()!=CdPlayerTimeWarp.OriginalSite.RESERVED_READ)throw new IllegalArgumentException("Not the reached star1 read");requireOriginalStarSource();var answer=monitorReserved.capture();op.expected.check();return answer;}
  private void warpStore(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){
   var op=warpOperation(call,primitive,true);if(primitive.site()!=CdPlayerTimeWarp.OriginalSite.STORE)throw new IllegalArgumentException("Not a native TimeWarp store");var store=primitive.store();
   if(store instanceof CdPlayerTimeWarp.GlobalWrite write){
    if(write.field()!=CdPlayerTimeWarp.GlobalField.TIME_WARP_BYTE){var before=op.expected;call.publishGlobal(primitive);before.nonPlayer();if(frame.state()!=before.actor)throw new IllegalStateException("TimeWarp scalar changed actor");op.expected=new R11WarpCut();}
    else{var g=active.state().globals();var contact=g.contact();if(contact.timeWarp()!=write.expectedValue())throw new IllegalStateException("Changed original TimeWarp contact byte");var ready=new CdPlayerBody.ContactGlobals(write.value(),contact.shield(),contact.combineRing());globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),ready,g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));op.expected=new R11WarpCut();}
   }else{requireOriginalStarSource();var write=(CdPlayerTimeWarp.ReservedWrite)store;var source=monitorReserved.state(write.wholeIndex());int at=write.field()==CdPlayerTimeWarp.ReservedField.ID?0:0x1C;if(source.byteAt(at)!=write.expectedValue())throw new IllegalStateException("Changed original star BYTE preimage");monitorReserved.write(source.moveByteAt(at,write.value()));if(!op.reservedWritten)throw new IllegalStateException("Missing original star BYTE adoption");}
  }
  private int warpScalar(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){
   var op=warpOperation(call,primitive,true);if(primitive.site()!=CdPlayerTimeWarp.OriginalSite.SCALAR_READ)throw new IllegalStateException("Not a reached native warp scalar");
   int value=switch(primitive.value()){case SPAWN_MODE_BYTE->active.prefix().main().level().spawnMode();case SCROLL_LOCK_WORD->active.state().scroll().foreground().scrollLock();case LEVEL_RESTART_WORD->active.prefix().main().level().levelRestart();};op.expected.check();return value;
  }
  private CdTimeWarpSaveData.State warpSaved(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){var op=warpOperation(call,primitive,true);if(primitive.site()!=CdPlayerTimeWarp.OriginalSite.SAVED_STATE_READ)throw new IllegalStateException("Not an actual saved-bank read");var answer=active.timeWarpSaved();op.expected.check();return answer;}
  private int warpSource(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){
   var op=warpOperation(call,primitive,true);if(primitive.site()!=CdPlayerTimeWarp.OriginalSite.SAVED_SOURCE_READ)throw new IllegalStateException("Not an actual warp save operand");var actor=frame.state();var motion=actor.motion();var e=frame.environment();var scroll=active.state().scroll();var g=active.state().globals();var level=active.prefix().main().level();
   int value=switch(primitive.source()){
    case PRIMARY_GVEL->motion.groundVelocity()&65535;case PRIMARY_X_VELOCITY->motion.xVelocity()&65535;case PRIMARY_Y_VELOCITY->motion.yVelocity()&65535;case PRIMARY_FLAGS->motion.flags();case RINGS->g.ring().rings();case LIVES_FLAGS->g.ring().livesFlags();
    default->originalR11CheckpointSource(CdCheckpointSaveData.Source.valueOf(primitive.source().name()),actor,e,scroll,g,level);
   };op.expected.check();return value;
  }
  private void warpActiveStore(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){
   var op=warpOperation(call,primitive,true);var before=op.expected;
   if(primitive.site()==CdPlayerTimeWarp.OriginalSite.RESTART_STORE){
    var write=primitive.restart();var l=active.prefix().main().level();if(l.levelRestart()!=write.expectedRestartWord())throw new IllegalStateException("Changed original warp restart WORD");
    var ready=closureLevelValue(l,write.finalRestartWord(),null,null,null,null,false);var prefix=before.prefix.level(ready);active.level(l,ready);before.checkActive(before.state,prefix,before.environment,before.saved,before.oldSounds);
   }else if(primitive.site()==CdPlayerTimeWarp.OriginalSite.FADE_OUT_MUSIC){
    var sound=new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA));var ready=new ArrayList<SonicCdGameModule.PastSound>(before.oldSounds);ready.add(sound);sound(sound);before.checkActive(before.state,before.prefix,before.environment,before.saved,List.copyOf(ready));
   }else if(primitive.site()==CdPlayerTimeWarp.OriginalSite.ACTIVE_STORE){
    var effect=primitive.saveEffect();
    if(effect instanceof CdPlayerTimeWarp.LockWrite write){
     var scroll=before.state.scroll();var f=scroll.foreground();if(f.scrollLock()!=write.expected())throw new IllegalStateException("Changed original scrollLock WORD");
     var foreground=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),write.value());
     var ready=new SonicCdR31aLoadingVisit.Scroll(foreground,scroll.background(),scroll.copies(),scroll.foregroundXFractionWord(),scroll.topBoundWord(),scroll.waterDeformWord());active.scroll(scroll,ready);before.checkActive(before.state.scroll(ready),before.prefix,before.environment,before.saved,before.oldSounds);
    }else if(effect instanceof CdPlayerTimeWarp.PeriodWrite){
     call.publishPeriod(primitive);before.nonPlayer();if(frame.state()!=before.actor)throw new IllegalStateException("Period store changed actor");
    }else if(effect instanceof CdPlayerTimeWarp.SavedWrite write){
     if(write.call().expectedState()!=active.timeWarpSaved())throw new IllegalStateException("Changed current warp saved cut");var token=new OriginalR11TimeWarpSaveStore(CdWorld.this,this,op,write.call());op.savedStore=token;try{active.timeWarpSaved(token);}catch(RuntimeException|Error first){try{if(token.used&&active.timeWarpSaved()==write.call().finalState()){before.checkActive(before.state,before.prefix,before.environment,write.call().finalState(),before.oldSounds);op.expected=new R11WarpCut();}else before.check();}catch(RuntimeException|Error verify){if(verify!=first)first.addSuppressed(verify);}throw first;}if(!token.used||active.timeWarpSaved()!=write.call().finalState())throw new IllegalStateException("Missing original saved-store adoption");before.checkActive(before.state,before.prefix,before.environment,write.call().finalState(),before.oldSounds);
    }else if(effect instanceof CdPlayerTimeWarp.SpawnWrite write){
     var l=active.prefix().main().level();if(l.spawnMode()!=write.expected())throw new IllegalStateException("Changed actual spawn BYTE");var ready=closureLevelValue(l,null,null,null,write.value(),null,false);active.level(l,ready);before.checkActive(before.state,before.prefix.level(ready),before.environment,before.saved,before.oldSounds);
    }else if(effect instanceof CdPlayerTimeWarp.SubCpuRequest command){
     if(command.command()!=CdPlayerTimeWarp.SubCpuCommand.SCMD_FADEPCM)throw new IllegalStateException("Unknown original warp SubCPU");var sound=new SonicCdGameModule.PastLevelMusic(CdCommand.FADEPCM);var ready=new ArrayList<SonicCdGameModule.PastSound>(before.oldSounds);ready.add(sound);sound(sound);before.checkActive(before.state,before.prefix,before.environment,before.saved,List.copyOf(ready));
    }else throw new IllegalStateException("Unknown original warp effect");
   }else throw new IllegalStateException("No active original warp store site");
   op.expected=new R11WarpCut();
  }
  private boolean warpReservedStore(CdAssetBytes before,CdAssetBytes ready){
   var op=originalWarpOperation;if(op==null||op.reservedWritten||!op.used)throw new IllegalStateException("No original TimeWarp reserved writer");op.call.requirePrimitive(op.frameCall);op.expected.check();
   if(!(op.call.store() instanceof CdPlayerTimeWarp.ReservedWrite write))throw new IllegalArgumentException("Not a TimeWarp star store");requireOriginalStarSource();var actual=active.state().reserved();if(!same(before,actual))throw new IllegalStateException("Changed complete original reserved bank");var bytes=before.copy();int at=(write.wholeIndex()-1)*64+(write.field()==CdPlayerTimeWarp.ReservedField.ID?0:0x1C);if(Byte.toUnsignedInt(bytes[at])!=write.expectedValue())throw new IllegalStateException("Changed star BYTE");bytes[at]=(byte)write.value();if(!Arrays.equals(bytes,ready.copy()))throw new IllegalStateException("Star BYTE changed a native nonwrite");
   var old=before.slice((write.wholeIndex()-1)*64,64);var next=ready.slice((write.wholeIndex()-1)*64,64);op.expected.check();active.reserved(write.wholeIndex(),old,next);op.reservedWritten=true;op.expected=new R11WarpCut();return true;
  }
  private void warpAfter(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive){var op=warpOperation(call,primitive,false);if(!op.used)throw new IllegalStateException("Uncalled original TimeWarp primitive");op.expected.check();originalWarpExpected=op.expected;originalWarpEntry.ordinal=primitive.ordinal();originalWarpOperation=null;}
  private void warpFailed(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive,Throwable first){var op=originalWarpOperation;if(op!=null&&op.frameCall==call&&op.call==primitive){try{op.expected.check();}finally{originalWarpOperation=null;}}else if(originalWarpExpected!=null)originalWarpExpected.check();}
  private final class StarOperation {
   final Object call;R11WarpCut expected=new R11WarpCut();boolean prepared,used;
   StarOperation(Object call){this.call=call;}
  }
  private void requireStar(){requirePhysical(physical);if(!starDispatch||physical<12||physical>15||physicalId!=3)throw new IllegalStateException("No actual R11 TimeStars physical occurrence");requireOriginalStarSource();}
  private StarOperation starOperation(Object call){requireStar();var op=starOperation;if(op==null||op.call!=call||!timeStars.isCurrentCall(call))throw new IllegalStateException("Foreign/retired TimeStars operation");op.expected.check();return op;}
  private StarOperation starPrimitive(Class<?> type){var op=starOperation(starOperation==null?null:starOperation.call);if(!op.prepared||op.used||!type.isInstance(op.call)||!timeStars.isCurrentPrimitive(op.call))throw new IllegalStateException("No original TimeStars primitive");op.used=true;return op;}
  private CdTimeStars.DispatchObserver starObserver(){return new CdTimeStars.DispatchObserver(){
   public void beforeOperation(Object call){requireStar();if(starOperation!=null||!timeStars.isCurrentCall(call))throw new IllegalStateException("Nested TimeStars operation");CdReservedObjectBank.Snapshot cut;CdReservedObjectBank.Image source;if(call instanceof CdTimeStars.Publication c){cut=c.cut();source=c.source();}else if(call instanceof CdTimeStars.PowerupReadCall c){cut=c.cut();source=c.source();}else if(call instanceof CdTimeStars.PowerupWriteCall c){cut=c.cut();source=c.source();}else if(call instanceof CdTimeStars.DrawCall c){cut=c.cut();source=c.source();}else throw new IllegalStateException("Unknown original TimeStars operation");monitorReserved.validateRestore(cut);if(source.identity().wholeIndex()!=physical||!source.equals(monitorReserved.state(physical))||!cut.slots().equals(monitorReserved.capture().slots()))throw new IllegalStateException("Changed complete native TimeStars call cut");starOperation=new StarOperation(call);}
   public void afterOperation(Object call){var op=starOperation(call);if(!op.prepared||!op.used)throw new IllegalStateException("Unprepared TimeStars completion");op.expected.check();}
   public void failedOperation(Object call,Throwable first){if(starOperation!=null&&starOperation.call==call)starOperation.expected.check();}
  };}
  private CdTimeStars.ReceiptGuard starGuard(){return new CdTimeStars.ReceiptGuard(){public void prepared(Object call){var op=starOperation(call);if(op.prepared)throw new IllegalStateException("Repeated TimeStars preparation");op.prepared=true;}public void completed(Object call){try{var op=starOperation(call);if(!op.prepared||!op.used)throw new IllegalStateException("Incomplete TimeStars operation");}finally{starOperation=null;}}};}
  private boolean starReservedStore(CdAssetBytes before,CdAssetBytes ready){
   var op=starPrimitive(CdTimeStars.Publication.class);var call=(CdTimeStars.Publication)op.call;monitorReserved.validateRestore(call.cut());if(call.source().identity().wholeIndex()!=physical||!Arrays.equals(call.source().copyBytes(),row(physical).copy()))throw new IllegalStateException("Changed actual TimeStars source");
   var bytes=before.copy();if(!same(before,active.state().reserved()))throw new IllegalStateException("Changed TimeStars complete bank");System.arraycopy(call.output().copyBytes(),0,bytes,(physical-1)*64,64);if(!Arrays.equals(bytes,ready.copy()))throw new IllegalStateException("TimeStars publication changed another reserved image");op.expected.check();active.reserved(physical,row(physical),CdAssetBytes.copyOf(call.output().copyBytes()));op.expected=new R11WarpCut();return true;
  }
  private void starDrawLanded(StarOperation op){
   var before=op.expected;var actual=active.state();var bytes=before.state.reserved().copy();int at=(physical-1)*64+1;bytes[at]=(byte)((bytes[at]&127)|(actual.reserved().unsignedByte(at)&128));
   if(!same(actual.reserved(),CdAssetBytes.copyOf(bytes))||!actual.equals(before.state.reserved(actual.reserved()))||!active.prefix().equals(before.prefix.globals(actual.globals())))throw new IllegalStateException("TimeStars Draw changed a native nonwrite");
   if(frame.state()!=before.actor||frame.environment()!=before.environment||frame.display()!=before.display||frame.artState()!=before.artImage||frame.vIntClock()!=before.clock||frame.paused()!=before.paused||sounds!=before.oldSounds||queue!=before.oldQueue||cpu!=before.oldCpu)throw new IllegalStateException("TimeStars Draw changed another owner");var placed=placement.capture();if(!placed.status().equals(before.placed.status())||!same(placed.savedFlags(),before.placed.savedFlags())||!placed.pool().slots().equals(before.placed.pool().slots()))throw new IllegalStateException("TimeStars Draw changed physical pool");op.expected=new R11WarpCut();
  }
  private void timeStar(int whole){requirePhysical(whole);if(starDispatch||starOperation!=null)throw new IllegalStateException("TimeStars reentry");requireOriginalStarSource();starDispatch=true;try{var f=frame.state();var e=frame.environment();timeStars.run(whole,new CdTimeStars.Globals(e.movement().gates().zoneAct()>>>8,active.state().globals().contact().timeWarp(),f.motion().flags(),f.history().bytes(),f.history().index()));}finally{starOperation=null;starDispatch=false;}}
  private boolean monitorExplosionDispatch;private long monitorExplosionOrdinal;private EffectOperation monitorEffectOperation;
  private final class EffectOperation {
   final Object call;final FamilyCut before=new FamilyCut();CdObjectPool.Snapshot ready;FamilyCut landed;boolean prepared,after;
   EffectOperation(Object call){this.call=call;}
  }
  private void monitorExplosionRequire(){requireMonitorFamilySource();if(!waterProgramme()||physical<32||physicalId!=CdExplosion.OBJECT_ID||!monitorExplosionDispatch)throw new IllegalStateException("No actual TT Explosion source dispatch");}
  private void monitorEffectBegin(Object call,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,CdObjectState target,CdObjectState output){
   monitorExplosionRequire();if(monitorEffectOperation!=null||!explosion.isCurrentCall(call)||source!=placement.pool().state(physical))throw new IllegalStateException("Foreign original effect occurrence");
   var op=new EffectOperation(call);placement.validateRestore(cut);op.before.check(cut.pool(),cut.savedFlags());if(!cut.status().equals(op.before.placed.status()))throw new IllegalStateException("Changed original effect placement cursor");
   if(call instanceof CdExplosion.Publication p){if(p.ordinal()!=monitorExplosionOrdinal+1||target!=placement.pool().state(target.identity().wholeIndex()))throw new IllegalStateException("Changed original effect store/ordinal");if(output!=null){if(output.identity()!=target.identity())throw new IllegalArgumentException("Foreign effect store target");op.ready=CdObjectPool.overlay(op.before.placed.pool(),List.of(output));}}
   op.before.check(op.before.placed.pool());monitorEffectOperation=op;
  }
  private EffectOperation monitorEffect(Object call){monitorExplosionRequire();var op=monitorEffectOperation;if(op==null||op.call!=call||!explosion.isCurrentCall(call))throw new IllegalStateException("Retired original effect receipt");return op;}
  private void monitorEffectAfter(Object call){
   var op=monitorEffect(call);if(!op.prepared||op.after)throw new IllegalStateException("Missing original effect preparation");
   if(call instanceof CdExplosion.Publication p){if(p.output()==null){var cleared=placement.pool().state(p.before().identity().wholeIndex());for(byte value:cleared.copyBytes())if(value!=0)throw new IllegalStateException("Original effect delete did not clear64");op.ready=CdObjectPool.overlay(op.before.placed.pool(),List.of(cleared));}op.before.check(op.ready);monitorExplosionOrdinal=p.ordinal();}
   else{if(op.landed==null)throw new IllegalStateException("Missing original effect Draw");op.landed.check(op.landed.placed.pool());}op.after=true;
  }
  private CdExplosion.DispatchObserver monitorExplosionObserver(){return new CdExplosion.DispatchObserver(){
   public void beforePublication(CdExplosion.Publication c){monitorEffectBegin(c,c.cut(),c.source(),c.before(),c.output());}
   public void afterPublication(CdExplosion.Publication c){monitorEffectAfter(c);}
   public void beforeCall(Object c){if(!(c instanceof CdExplosion.DrawCall d))throw new IllegalStateException("Unknown effect call");monitorEffectBegin(c,d.cut(),d.source(),d.source(),null);}
   public void afterCall(Object c){monitorEffectAfter(c);}
  };}
  private CdExplosion.ReceiptGuard monitorExplosionGuard(){return new CdExplosion.ReceiptGuard(){
   public void prepared(Object c){var op=monitorEffect(c);if(op.prepared)throw new IllegalStateException("Repeated original effect preparation");op.before.check(op.before.placed.pool());op.prepared=true;}
   public void completed(Object c){var op=monitorEffect(c);if(!op.after)throw new IllegalStateException("Missing original effect after receipt");if(op.landed!=null)op.landed.check(op.landed.placed.pool());else op.before.check(op.ready);monitorEffectOperation=null;}
  };}
  private void monitorExplosionDraw(CdExplosion.DrawCall c){var op=monitorEffect(c);if(!op.prepared||op.landed!=null)throw new IllegalStateException("Unprepared/repeated original effect Draw");familyDraw(placement.pool(),c.source().identity(),()->explosion.isCurrentCall(c));op.landed=new FamilyCut();}
  private void monitorExplosion(int whole){
   requirePhysical(whole);if(monitorExplosionDispatch||monitorEffectOperation!=null)throw new IllegalStateException("Original effect dispatch reentry");monitorExplosionDispatch=true;monitorExplosionOrdinal=0;
   try{explosion.run(whole);monitorExplosionRequire();if(monitorEffectOperation!=null)throw new IllegalStateException("Unclosed original effect call");}
   catch(RuntimeException|Error original){try{var op=monitorEffectOperation;if(op!=null){if(op.landed!=null)op.landed.check(op.landed.placed.pool());else{var actual=placement.pool().capture();var expected=op.ready!=null&&actual.slots().equals(op.ready.slots())?op.ready:op.before.placed.pool();op.before.check(expected);}}}catch(RuntimeException|Error verify){if(verify!=original)original.addSuppressed(verify);}throw original;}
   finally{monitorEffectOperation=null;monitorExplosionDispatch=false;}
  }
  private CdObjectDrawQueue.CameraWords camera(){var f=active.state().scroll().foreground();return new CdObjectDrawQueue.CameraWords(f.cameraX(),f.cameraYWord());}
  private void drawDynamic(CdObjectPool pool,CdObjectState.Identity identity){requirePhysical(identity.wholeIndex());if(pool!=placement.pool())throw new IllegalArgumentException("Foreign DrawObject pool");drawWhole(physical);}
  private void drawReserved(int whole){requirePhysical(whole);drawWhole(whole);}
  private void drawWhole(int whole){requirePhysical(whole);var raw=whole==0?null:whole<32?row(whole):CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes());
   if(raw==null)throw new IllegalStateException("Primary requires actual Body Display");draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),raw.unsignedByte(1),raw.unsignedWord(8),raw.unsignedWord(12),raw.unsignedByte(22),raw.unsignedByte(25),raw.unsignedByte(24)));}
  private void draw(CdObjectDrawQueue.DrawCallCut cut){requireCut(Cut.RUN_OBJECTS);if(drawing||draining||physical!=cut.ref().wholeIndex())throw new IllegalStateException("DrawObject reentry");drawing=true;try{queue.drawObject(cut,camera());}finally{drawing=false;}}
  private final class Slots implements CdObjectDrawQueue.CdDrawSlots {
   private void current(){live();if(!drawing&&!draining)throw new IllegalStateException("No original queue dereference");}
   public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){current();int w=ref.wholeIndex();if(w==0){var s=frame.state();var m=s.motion();var h=s.header();return new CdObjectDrawQueue.LiveDrawSlot(h.slotId(),m.spriteFlags(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.centreX16_16()&65535,h.mappingAddress(),s.animation().mapFrame(),h.tile());}var b=w<32?row(w):CdAssetBytes.copyOf(placement.pool().state(w).copyBytes());return new CdObjectDrawQueue.LiveDrawSlot(b.unsignedByte(0),b.unsignedByte(1),b.unsignedWord(8),b.unsignedWord(12),b.unsignedWord(10),(int)b.unsignedLong(4),b.unsignedByte(26),b.unsignedWord(2));}
   public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){current();int w=ref.wholeIndex();if(w==0){if(bodyDrawCall!=null)bodyDrawCall.publishDrawOnScreen(value);else frame.publishDrawObjectOnScreen(value);return;}if(w<32){var b=row(w);var raw=b.copy();raw[1]=(byte)((raw[1]&127)|(value?128:0));active.reserved(w,b,CdAssetBytes.copyOf(raw));}else{var b=placement.pool().state(w);placement.pool().write(b.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,(b.spriteFlags()&127)|(value?128:0)));}}
  }
  private void primary(int levelFrame){
   requirePhysical(0);if(waterProgramme()){frame.runOriginalProgrammeBody(waterOwner);return;}var pool=placement.pool().capture();var g=active.state().globals();var l=active.prefix().main().level();var player=frame.state();
   var responseContext=new CdPlayerBody.ResponseContext(response,g.contact(),new CdPlayerTouchResponse.WorldGlobals(g.ring().rings(),g.ring().lives(),g.ring().updateHudLives(),g.score().score(),g.score().nextLifeScore(),g.score().updateHudScore()));
   var warpEntry=new OriginalR11TimeWarpEntry(this);if(originalWarpEntry!=null)throw new IllegalStateException("Original primary TimeWarp reentry");originalWarpEntry=warpEntry;CdPlayerFrameRuntime.BodyPublication publication;
   try{publication=frame.runBodyWithOriginalTimeWarp(levelFrame,Optional.of(new CdPlayerBody.TouchContext(scanner,pool,responseContext)),Optional.of(new CdPlayerBody.LevelContext(pool,g.ring(),l)),Optional.of(new CdPlayerMovement.ObjectContext(pool,player.objectLinks().standObject(),player.header().slotId(),player.header().width())),Optional.of(new CdPlayerBody.TimeWarpContext(new CdPlayerTimeWarp.Context(OptionalInt.of(l.spawnMode()),Optional.empty(),Optional.empty(),OptionalInt.empty()),g.contact())),Optional.empty(),warpEntry);}
   finally{warpEntry.retired=true;originalWarpEntry=null;originalWarpOperation=null;originalWarpExpected=null;}
   g=active.state().globals();l=active.prefix().main().level();
   requirePhysical(0);if(publication.result().boundary()==CdPlayerBody.Boundary.DEAD_RETURN||publication.result().boundary()==CdPlayerBody.Boundary.RESTART_RETURN){primaryLifecycle(publication,pool,g,l);return;}var changes=new LinkedHashMap<Integer,CdObjectState>();var ring=g.ring();var score=g.score();var contact=g.contact();CdPlayerBody.DisplayEligibility display=null;boolean touched=false;
   for(var effect:publication.result().effects()){
    if(effect instanceof CdPlayerBody.TimeWarpCall warp){var result=warp.result();if(result!=warpEntry.result)throw new IllegalStateException("TimeWarp observation is not the completed original occurrence"); /* All primitive stores already landed; never replay effects/patches. */}
    else if(effect instanceof CdPlayerBody.ModeEffect mode&&mode.effect() instanceof CdPlayerMovement.ObjectPatch write){var patch=write.patch();CdObjectPool.overlay(pool,List.of(patch.before()));CdObjectPool.overlay(pool,List.of(patch.after()));var prior=changes.getOrDefault(patch.before().identity().wholeIndex(),pool.slots().get(patch.before().identity().dynamicIndex()));if(!prior.equals(patch.before()))throw new IllegalStateException("Changed MODE call cut");changes.put(patch.after().identity().wholeIndex(),patch.after());}
    else if(effect instanceof CdPlayerBody.DisplayEligibility cut){if(display!=null)throw new IllegalStateException("Repeated original Display");display=cut;}
    else if(effect instanceof CdPlayerBody.ObjectTouchResult touch){if(touched)throw new IllegalStateException("Repeated actual touch");touched=true;touch.result().changedSlot().ifPresent(row->changes.put(row.identity().wholeIndex(),row));}
    else if(effect instanceof CdPlayerBody.ObjectTouchResponse touch){if(touched)throw new IllegalStateException("Repeated actual touch");touched=true;var ready=CdPrimaryTouchValues.response(touch,ring);ring=ready.ring();score=ready.score();contact=ready.contact();for(var row:ready.changed())changes.put(row.identity().wholeIndex(),row);}
    else if(effect instanceof CdPlayerBody.DeadCheckGone||effect instanceof CdPlayerBody.RestartCut)throw new IllegalStateException("Lifecycle summary outside its completed boundary");
   }
   var staged=CdObjectPool.overlay(pool,List.copyOf(changes.values()));placement.pool().validateRestore(staged);if(!pool.slots().equals(placement.pool().capture().slots())||active.state().globals()!=g||active.prefix().main().level()!=l)throw new IllegalStateException("Changed full primary World preimage");
   var next=SonicCdR31aWorldHost.with(g,ring,g.live(),contact,score,g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());globals(next);placement.pool().restore(staged);
   for(var sound:publication.sounds())sound(new SonicCdGameModule.PastFrameSound(sound));
   if(display!=null&&display.eligible())draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),display.spriteFlags(),display.centreXWord(),display.centreYWord(),display.yRadius(),display.width(),display.priority()));
  }
  private void primaryLifecycle(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot pool,SonicCdR31aWorldHost.Globals g,CdLevelGlobals level){
   requirePhysical(0);var before=active.state();var prefix=active.prefix();var placed=placement.capture();var result=publication.result();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();
   var alias=CdPrimaryLifecycle.aliasReached(result)?new Stopped(Boundary.PRIMARY_DEATH_ALIAS):null;
   try{
    if(before.globals()!=g||prefix.main().level()!=level||!placed.pool().slots().equals(pool.slots()))throw new IllegalStateException("Changed original lifecycle full preimage");
    var plan=CdPrimaryLifecycle.prepare(publication,pool,g.ring(),level);
    var globals=SonicCdR31aWorldHost.with(g,plan.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
    var ready=before.globals(globals);var readyPrefix=prefix.level(plan.level()).globals(globals);
    placement.pool().validateRestore(plan.pool());var now=placement.capture();
    if(active.state()!=before||active.prefix()!=prefix||frame.state()!=result.state()||frame.environment()!=result.environment()||frame.display()!=result.display()||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!Arrays.equals(placed.savedFlags().copy(),now.savedFlags().copy()))throw new IllegalStateException("Original lifecycle changed another complete owner");
    // Prebuilt values and complete owner checks precede callback-free native adoption.
    placement.pool().restore(plan.pool());if(plan.resetSaved())placement.resetSavedObjectFlags();active.primaryLifecycle(before,prefix,ready,readyPrefix);
    for(var request:plan.sounds())sound(request);
   }catch(RuntimeException|Error preparation){if(alias==null)throw preparation;if(preparation!=alias)alias.addSuppressed(preparation);throw alias;}
   // Preserve the actual bounded alias stop; do not complete Draw or a physical pass.
   if(alias!=null)throw alias;
   CdPlayerBody.DisplayEligibility display=null;for(var effect:result.effects())if(effect instanceof CdPlayerBody.DisplayEligibility value){if(display!=null)throw new IllegalStateException("Repeated lifecycle Display");display=value;}
   if(display!=null&&display.eligible())draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),display.spriteFlags(),display.centreXWord(),display.centreYWord(),display.yRadius(),display.width(),display.priority()));
  }
  private void walk(int levelFrame){requireCut(Cut.RUN_OBJECTS);if(walkReturned||physical!=-1)throw new IllegalStateException("Physical walk reentry");if(queue!=null)throw new IllegalStateException("Undrained original queue");queue=new CdObjectDrawQueue(new Slots(),mappings,active.state().cpuSat().copy(),reservedRefs);queue.beginPass();mechanismServices=List.of();var servicedMechanisms=new ArrayList<MechanismService>();closureServices=List.of();var servicedClosure=new ArrayList<ClosureService>();tunnelServices=List.of();var servicedTunnels=new ArrayList<TunnelService>();CdPhysicalObjectWalk.run(0,128,w->{requireCut(Cut.RUN_OBJECTS);physical=w;try{int id=w==0?frame.state().header().slotId():w<32?row(w).unsignedByte(0):placement.pool().state(w).id();physicalId=id;if(id==0)return;
    if(standardPastProgramme()){
     if(!standardSourcePair())throw new IllegalStateException("Changed original Past source/Frame/current-art pair");
     var handler=pastObjects.dispatch(id).orElseThrow();
     if(handler.nativeNull()&&w>=32){var before=new FamilyCut();var expected=CdObjectPool.overlay(before.placed.pool(),List.of(placement.pool().state(w).moveByte(CdObjectState.ByteField.ID,0)));before.check(before.placed.pool());placement.pool().objNull(w);before.check(expected);return;}
     if(!((w==0&&id==1)||(w>=32&&id==0x10)||(w>=32&&id==CdAnton.OBJECT_ID)||(w>=32&&(id==CdPataBata.OBJECT_ID||id==CdMosqui.OBJECT_ID||id==CdTamabboh.OBJECT_ID))||(w==CdShieldPowerup.WHOLE_INDEX&&id==CdShieldPowerup.OBJECT_ID)||(w<32&&(id==0x1C||id==0x19))||id==CdTitleCard.OBJECT_ID))
      throw new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("R11B physical handler unbound before dispatch: whole="+w+" id=$"+Integer.toHexString(id)+" handler=$"+Integer.toHexString(handler.handlerAddress()));
    }
    var e=frame.environment();var gates=e.movement().gates();if(w==0){if(id!=1)throw new UnsupportedOperationException("Original primary ID "+id);primary(levelFrame);}
    else if(waterProgramme()&&w>=32&&ttObjects.isNullHandler(id)){var before=new FamilyCut();var expected=CdObjectPool.overlay(before.placed.pool(),List.of(placement.pool().state(w).moveByte(CdObjectState.ByteField.ID,0)));before.check(before.placed.pool());placement.pool().objNull(w);before.check(expected);return;}
    else if(waterProgramme()&&w>=32&&id==TT_BLOCK_ID)ttBlock(w);
    else if(waterProgramme()&&w>=32&&id==TT_COLLAPSE_ID)ttCollapseFloor(w);
    else if(waterProgramme()&&w>=32&&id==TT_HMOVE_ID)ttHMove(w);
    else if(waterProgramme()&&w>=32&&id==0x28)ttDoor(w);
    else if(waterProgramme()&&w>=32&&id==TT_SWING_ID)ttSwing(w);
    else if(waterProgramme()&&w>=32&&id==0x27)ttSwitch(w);
    else if(w>=32&&id==CdMonitor.OBJECT_ID)monitorTime(w);
    else if(waterProgramme()&&w>=32&&id==CdExplosion.OBJECT_ID)monitorExplosion(w);
    else if(waterProgramme()&&w>=32&&(id==0x22||id==0x2B||id==0x2F||id==0x32))ttEcology(w,id);
    else if(waterProgramme()&&((w==7&&id==0x21)||(w>=32&&(id==0x21||id==0x0B))))ttAir(w,id);
    else if(waterProgramme()&&w>=32&&id==TT_BUBBLE_SPAWNER_ID)bubbleSpawner(w);
    else if(waterProgramme()&&w>=32&&id==TT_WATER_SURFACE_ID)waterSurface(w);
    else if(id==CdTitleCard.OBJECT_ID)title.run(w);
    else if(w==CdShieldPowerup.WHOLE_INDEX&&id==CdShieldPowerup.OBJECT_ID)monitorShield.run(w);
    else if(!waterProgramme()&&w>=12&&w<=15&&id==CdTimeStars.OBJECT_ID)timeStar(w);
    else if(w>=32&&id==CdMonitorItem.OBJECT_ID){var s=frame.state();monitorItem.run(w,Optional.of(new CdMonitorItem.PrimarySupport(s.header().slotId(),s.motion().flags())));}
    else if(waterProgramme()&&!((w<32&&(id==0x1C||id==0x19))||(w>=32&&(id==0x10||id==CdSpring.OBJECT_ID||id==CdMovingSpring.OBJECT_ID))))throw new UnsupportedOperationException("Actual R41A physical handler is unbound: whole="+w+" id=$"+Integer.toHexString(id));
    else if(w<32&&(id==0x1C||id==0x19))hud.runLive(w,new CdReservedHud.LiveGlobals(e.globals().debugCheat(),active.state().globals().ring().rings(),e.globals().timeAttackMode(),gates.timeWarpDirection(),e.globals().timeWarpTimer(),e.globals().levelFrames(),()->(int)(frame.vIntClock().absoluteValue().orElseThrow(()->new IllegalStateException("Actual HUD clock unknown"))&255)));
    else if(w>=32&&(id==0x14||id==0x16||id==0x17||id==0x3A)){
     int entry=placement.pool().state(w).routine();closureFamily(w,id);servicedClosure.add(new ClosureService(id,w,entry,placement.pool().state(w).routine(),closureOperations));closureServices=List.copyOf(servicedClosure);
    }
    else if(w>=32&&id==CdGameOver.OBJECT_ID)gameOver();
    else if(w>=32&&id==0x10)ring.executeSelected(w,new CdRing.Inputs(frame.stageSource().variant(),frame.stageSource().stage(),gates.zoneAct()>>>8,camera().xWord(),gates.timeZone(),gates.timeWarpDirection()),new CdRing.GlobalsPort(){public CdRingGlobals read(){requirePhysical(w);return active.state().globals().ring();}public void write(CdRingGlobals next){requirePhysical(w);var before=active.state().globals().ring();if(next.timeStopTimer()!=before.timeStopTimer()||next.ringAnimationTimer()!=before.ringAnimationTimer()||next.ringAnimationFrame()!=before.ringAnimationFrame())throw new IllegalStateException("Foreign Ring global owner");ring(next);}},new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound value){requirePhysical(w);sound(new SonicCdGameModule.PastRingFm(value));}public void subCpuCommand(CdRing.SubCpuCommand value){requirePhysical(w);sound(new SonicCdGameModule.PastRingSubCpu(value));}public void drawObject(CdObjectPool pool,int whole){drawDynamic(pool,pool.state(whole).identity());}});
    else if(w>=32&&id==CdLostRing.OBJECT_ID)lostRing(w);
    else if(w>=32&&(id==0x09||id==0x0F||id==0x28||id==0x31||id==0x36)){
     int entry=placement.pool().state(w).routine();mechanismOrdinal=0;mechanismFamily(w,id);
     servicedMechanisms.add(new MechanismService(id,w,entry,placement.pool().state(w).routine(),Math.toIntExact(mechanismOrdinal)));
    }
    else if(w>=32&&(id==Cd3dRamp.OBJECT_ID||id==CdBoulder.OBJECT_ID||id==CdSpikes.OBJECT_ID||id==CdSpring.OBJECT_ID))solidHazard(w,id);
    else if(w>=32&&(id==CdCollapsePlatform.OBJECT_ID||id==CdPlatform.OBJECT_ID))platformFamily(w,id);
    else if(w>=32&&(id==0x34||id==0x0D||id==0x35||id==0x0B||id==0x07)){
     int entry=placement.pool().state(w).routine();tunnelOperations=0;tunnelFamily(w,id);
     servicedTunnels.add(new TunnelService(id,w,entry,placement.pool().state(w).routine(),tunnelOperations));
    }
    else if(w>=32&&id==CdSpinTunnelSplash.OBJECT_ID){
     int entry=placement.pool().state(w).routine();waterfall(w);
     servicedTunnels.add(new TunnelService(id,w,entry,placement.pool().state(w).routine(),waterfallOperations));
    }
     else if(w>=32&&id==CdCheckpoint.OBJECT_ID)checkpoint(w);
     else if(w>=32&&id==CdFloatBlock.OBJECT_ID)floatBlock(w);
    else if(w>=32&&id==CdMosqui.OBJECT_ID){var p=frame.state().motion();var globals=new CdMosqui.Globals(active.prefix().goodFuture(),gates.timeZone(),gates.timeWarpDirection(),e.globals().debugMode(),camera().xWord(),p.centreX16_16()>>>16,p.centreY16_16()>>>16);if(standardPastProgramme())pastMosquiDispatch(w,globals);else mosqui.run(w,globals);}
    else if(w>=32&&id==CdPataBata.OBJECT_ID)enemyDispatch(id,enemyPataBata::isCurrentOriginalPrimitive,()->enemyPataBata.run(w,new CdPataBata.Globals(active.prefix().goodFuture(),gates.timeZone(),gates.timeWarpDirection(),camera().xWord())));
    else if(w>=32&&id==CdAnton.OBJECT_ID)enemyDispatch(id,enemyAnton::isCurrentOriginalPrimitive,()->{enemyAnton.run(w,new CdAnton.Globals(active.prefix().goodFuture(),gates.timeZone(),gates.timeWarpDirection(),camera().xWord(),frame.environment().movement().globals().primaryAngle()));});
    else if(w>=32&&id==CdTamabboh.OBJECT_ID)enemyDispatch(id,enemyTamabboh::isCurrentOriginalPrimitive,()->enemyTamabboh.run(w,new CdTamabboh.Reads(){
     public int goodFuture(){return active.prefix().goodFuture();}public int timeZone(){return frame.environment().movement().gates().timeZone();}public int timeWarpDirection(){return frame.environment().movement().gates().timeWarpDirection();}public int debugMode(){return frame.environment().globals().debugMode();}public int cameraXWord(){return camera().xWord();}public int primaryAngle(){return frame.environment().movement().globals().primaryAngle();}
    }));
    // N1ecc5f3a Present Object Index: $33 LogShadow, $18 Explosion, $1C HUD/Points.
    // Each physical visit obtains fresh raw globals; camera is still the pre-scroll foreground WORD.
    else if(w>=32&&id==CdLogShadow.OBJECT_ID)logShadow.run(w,new CdLogShadow.Globals(gates.timeZone(),active.prefix().goodFuture(),gates.zoneAct()&255,gates.timeWarpDirection(),camera().xWord()));
    else if(w>=32&&id==CdExplosion.OBJECT_ID)explosion.run(w);
    else if(w>=32&&id==CdPoints.OBJECT_ID)points.run(w);
    else if(w>=32&&id==CdFlower.OBJECT_ID)flower(w);
    else if(w>=32&&id==Cd3dPlant.OBJECT_ID){var p=frame.state().motion();plant.run(w,new Cd3dPlant.Player(p.centreX16_16()>>>16,p.playerControl()),new Cd3dPlant.Globals(gates.timeZone(),gates.timeWarpDirection(),camera().xWord()));}
    else if(w>=32&&id==CdAnimal.OBJECT_ID)animal.run(w,new CdAnimal.Globals(gates.timeZone(),active.prefix().goodFuture(),active.state().globals().projectorByte().orElseThrow(),gates.timeWarpDirection(),camera().xWord()));
    else throw new UnsupportedOperationException("Original R11 physical handler source not yet joined: slot="+w+" id="+id);
   }finally{physical=-1;physicalId=0;}});mechanismServices=List.copyOf(servicedMechanisms);tunnelServices=List.copyOf(servicedTunnels);walkReturned=true;}
   private void checkpoint(int whole){
    requirePhysical(whole);if(physicalId!=CdCheckpoint.OBJECT_ID||checkpointDispatch||timePostDispatch)throw new IllegalStateException("No original Checkpoint dispatch");
    checkpointDispatch=true;checkpointStores=0;checkpointSaved=false;
    try{checkpoint.runWithReads(whole,new CdCheckpoint.DespawnReads(){
     public int rawTimeZoneByte(){checkpointDespawnRead(CdCheckpoint.DespawnOperand.PERIOD);return frame.environment().movement().gates().timeZone();}
     public int rawDirectionByte(){checkpointDespawnRead(CdCheckpoint.DespawnOperand.DIRECTION);return frame.environment().movement().gates().timeWarpDirection();}
     public int cameraXWord(){checkpointDespawnRead(CdCheckpoint.DespawnOperand.CAMERA_X);return camera().xWord();}
    });}
    finally{checkpointDispatch=false;checkpointStores=0;checkpointSaved=false;}
   }
   private void checkpointDespawnRead(CdCheckpoint.DespawnOperand operand){
    requirePhysical(physical);if(!checkpointDispatch||physicalId!=CdCheckpoint.OBJECT_ID||!checkpoint.isCurrentDespawnRead(operand))throw new IllegalStateException("No original Checkpoint despawn operand");
   }
   private void checkpointCall(CdCheckpoint.LiveCall call){
    requirePhysical(physical);if(!checkpointDispatch||physicalId!=CdCheckpoint.OBJECT_ID||!checkpoint.isCurrentLiveCall(call))throw new IllegalStateException("No current original Checkpoint operation");
    sourceCall(call.cut().pool(),call.cut().source(),CdCheckpoint.OBJECT_ID);
   }
   private void checkpointDraw(CdObjectPool pool,CdObjectState.Identity id){
    requirePhysical(id.wholeIndex());if(!checkpointDispatch||!checkpoint.isCurrentDrawCall(pool,id))throw new IllegalStateException("No original Checkpoint Draw");drawDynamic(pool,id);
   }
   private int checkpointParent(int raw){
    int at=raw&0xFFFFFF;
    if(at>=0xFFD800&&at<0xFFF000){int offset=at-0xFFD000;return placement.pool().state(offset/64).copyBytes()[offset%64]&255;}
    if(at>=0xFFD040&&at<0xFFD800){int offset=at-0xFFD000;return row(offset/64).unsignedByte(offset%64);}
    // Primary's opaque $2E and absent storage are unknown. Do not synthesize a whole primary image.
    throw new CdCheckpoint.UnownedParentByte(raw);
   }
   private void checkpointStore(CdCheckpoint.GlobalStoreCall call){
    checkpointCall(call);var c=call.store();var before=active.prefix().main().level();
    var field=checkpointStores==0?CdCheckpoint.GlobalField.CHECKPOINT:CdCheckpoint.GlobalField.SPAWN_MODE;
    if(checkpointStores>=2||c.field()!=field||c.expectedSourceObject()!=call.cut().source()||c.expectedLevel()!=before)
     throw new IllegalArgumentException("Stale/out-of-order original Checkpoint store");
    var next=new CdLevelGlobals(before.levelRestart(),before.updateHudTime(),field==CdCheckpoint.GlobalField.CHECKPOINT?call.cut().source().subtype():before.checkpoint(),field==CdCheckpoint.GlobalField.SPAWN_MODE?1:before.spawnMode(),before.plcLoadFlags(),before.flowerCount(),before.fadeEnableDisplay(),before.powerup(),before.timeOver());
    if(!next.equals(c.finalLevel()))throw new IllegalArgumentException("Foreign Checkpoint scalar nonwrites");
    active.level(before,c.finalLevel());checkpointStores++;
   }
   private CdCheckpointSaveData.Result checkpointSave(CdCheckpoint.SaveDataCall call){
    checkpointCall(call);var c=call.save();var before=active.state();var prefix=active.prefix();
    if(checkpointStores!=2||checkpointSaved||c.expectedSourceObject()!=call.cut().source()||c.expectedLevel()!=prefix.main().level()||c.expectedSaved()!=before.globals().checkpointSaved())
     throw new IllegalArgumentException("Stale original Checkpoint save cut");
    var writer=new CheckpointWriter(call);var result=CdCheckpointSaveData.save(c.expectedSaved(),writer);
    writer.verify();if(writer.ordinal!=CdCheckpointSaveData.Store.values().length)throw new IllegalStateException("Missing actual Checkpoint stores");
    checkpointSaved=true;return result;
   }
   // One private synchronous save invocation. Each operand is read from its current raw owner;
   // the existing kernel alone clamps time and stages overlapping saved-water bytes.
   private final class CheckpointWriter implements CdCheckpointSaveData.Port {
    final CdCheckpoint.SaveDataCall call;final Object actor=frame.state(),environment=frame.environment(),display=frame.display(),art=frame.artState(),clock=frame.vIntClock();
    final boolean paused=frame.paused();final List<SonicCdGameModule.PastSound> oldSounds=sounds;
    SonicCdGameModule.R11LoadingState expected=active.state();SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
    int ordinal,raw;boolean read;
    CheckpointWriter(CdCheckpoint.SaveDataCall call){this.call=call;}
    void verify(){checkpointCall(call);if(active.state()!=expected||active.prefix()!=prefix||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=oldSounds)throw new IllegalStateException("Changed complete original Checkpoint save owner");}
    public CdCheckpointSaveData.State currentState(){verify();return expected.globals().checkpointSaved();}
    public int read(CdCheckpointSaveData.Source source){
     verify();if(read||ordinal>=CdCheckpointSaveData.Store.values().length||CdCheckpointSaveData.Store.values()[ordinal].source()!=source)throw new IllegalStateException("Not next original Checkpoint operand");
     raw=originalR11CheckpointSource(source,frame.state(),frame.environment(),expected.scroll(),expected.globals(),prefix.main().level());read=true;return raw;
    }
    public void commit(CdCheckpointSaveData.StoreCall c){
     verify();if(ordinal>=CdCheckpointSaveData.Store.values().length)throw new IllegalStateException("Past final Checkpoint store");var store=CdCheckpointSaveData.Store.values()[ordinal];
     if(!read||c.store()!=store||c.expectedState()!=expected.globals().checkpointSaved()||(store!=CdCheckpointSaveData.Store.TIME_LONG&&c.rawValue()!=raw))throw new IllegalArgumentException("Stale original Checkpoint saved store");
     var g=expected.globals();var next=new SonicCdR31aWorldHost.Globals(g.ring(),g.live(),g.contact(),g.score(),g.projectorByte(),c.finalState(),g.drownTimerWord(),g.debugBlockWord(),g.pauseMusicBit(),g.bonus(),g.tail(),g.animations(),g.paletteSteps7(),g.paletteTimers7(),g.layerByte(),g.bossFlagsByte(),g.eventRoutineWord());
     globals(next);expected=active.state();prefix=active.prefix();ordinal++;read=false;verify();
    }
   }
   // Both exact original sources use the same synchronous TimePost lifecycle and ports.
   private CdTimePost newTimePost(CdMonitor.Assets data){
    return CdTimePost.withOriginalOperations(placement,data,this::timePostDirection,this::timePostSound,
        (pool,id)->{var op=timePostOperation(CdTimePost.Site.DRAW,id);drawDynamic(pool,id);op.verify();},new CdTimePost.NativeObserver(){
         public void before(CdTimePost.OriginalCall call){beforeTimePost(call);}
         public void after(CdTimePost.OriginalCall call){var op=timePostOperation(call);op.verify();timePostScope=null;}
         public void failed(CdTimePost.OriginalCall call,Throwable original){try{if(timePostScope!=null)timePostOperation(call).verify();}finally{timePostScope=null;}}
        });
   }
   private final class TimePostScope {
    final CdTimePost.OriginalCall call;final Object actor=frame.state(),display=frame.display(),art=frame.artState(),clock=frame.vIntClock();Object environment=frame.environment();
    final boolean paused=frame.paused();final SonicCdGameModule.R11LoadingState state=active.state();final SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
    List<SonicCdGameModule.PastSound> expectedSounds=sounds;boolean used;
    TimePostScope(CdTimePost.OriginalCall call){this.call=call;}
    void verify(){requirePhysical(physical);timePost.requireCurrent(call);if(!timePostDispatch||physicalId!=CdTimePost.OBJECT_ID||active.state()!=state||active.prefix()!=prefix||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=expectedSounds)throw new IllegalStateException("Changed complete original Time Post owner");}
   }
   private void beforeTimePost(CdTimePost.OriginalCall call){
    requirePhysical(physical);if(!timePostDispatch||physicalId!=CdTimePost.OBJECT_ID||timePostScope!=null)throw new IllegalStateException("No original Time Post scope");
    if(waterProgramme()){ttObjects.requirePlacement(placement);if(frame.stageSource()!=ttSource.retainedPlayerSource())throw new IllegalStateException("Changed original TT TimePost reader/Frame");}
    timePost.requireCurrent(call);if(call.before().pool().slots().get(physical-32).id()!=CdTimePost.OBJECT_ID)throw new IllegalArgumentException("Foreign Time Post physical source");
    timePostScope=new TimePostScope(call);timePostScope.verify();
   }
   private TimePostScope timePostOperation(CdTimePost.OriginalCall call){var op=timePostScope;if(op==null||op.call!=call)throw new IllegalStateException("Expired original Time Post operation");op.verify();return op;}
   private TimePostScope timePostOperation(CdTimePost.Site site,Object argument){var op=timePostOperation(timePost.requireCurrentArgument(site,argument));if(op.used)throw new IllegalStateException("Repeated Time Post primitive");op.used=true;return op;}
   private void timePostRead(CdTimePost.Site site){timePostOperation(timePost.requireCurrentSite(site));}
   private void timePostDirection(CdTimePost.DirectionCall call){
    var op=timePostOperation(CdTimePost.Site.DIRECTION_STORE,call);if(call.identity().wholeIndex()!=physical)throw new IllegalArgumentException("Foreign direction slot");
    var p=frame.prepareObjectTimePostDirection(frame.state(),frame.environment(),call.expectedRawDirection(),call.rawDirection());frame.validatePreparedObjectSolidPublication(p);op.verify();
    frame.applyPreparedObjectSolidPublication(p);op.environment=frame.environment();op.verify();
   }
   private void timePostSound(CdTimePost.SubCpuCommand command){var op=timePostOperation(CdTimePost.Site.SUB_CPU,command);sound(new SonicCdGameModule.PastTimePostSubCpu(command));op.expectedSounds=sounds;op.verify();}
   private void timePost(int whole){
    requirePhysical(whole);if(physicalId!=CdTimePost.OBJECT_ID||checkpointDispatch||timePostDispatch)throw new IllegalStateException("No original Time Post dispatch");
    timePostDispatch=true;
    try{timePost.runWithReads(whole,new CdTimePost.PastGlobals(){
     public int rawTimeZone(){timePostRead(CdTimePost.Site.TIME_ZONE_READ);return frame.environment().movement().gates().timeZone();}
     public int rawTimeWarpDirection(){timePostRead(CdTimePost.Site.DIRECTION_READ);return frame.environment().movement().gates().timeWarpDirection();}
     public int cameraXWord(){timePostRead(CdTimePost.Site.CAMERA_READ);return camera().xWord();}
     public int timeAttackMode(){timePostRead(CdTimePost.Site.TIME_ATTACK_READ);return frame.environment().globals().timeAttackMode();}
     public int zone(){timePostRead(CdTimePost.Site.ZONE_READ);return frame.environment().movement().gates().zoneAct()>>>8;}
     public OptionalInt layer(){timePostRead(CdTimePost.Site.LAYER_READ);return active.state().globals().layerByte();}
    });}finally{timePostDispatch=false;timePostScope=null;}
   }
   // N1ecc5f3a Floating Block/Main.asm:156-165 calls PRIMARY twice; no secondary player
  // projection/read is made. Existing Solid owns primary alias0 and dynamic counterpart release.
  // A reached reserved1 link keeps NativeStopped's actual prefix; see the handoff's lazy owner proposal.
  private void floatTerrain(){
   requireOriginalR11FloatBlockTerrain(frame.stageSource(),assets,active.state().blocks().orElseThrow(
       ()->new IllegalStateException("Original FloatBlock has no completed current blocks")),
       active.state().layout().orElseThrow(()->new IllegalStateException("Original FloatBlock has no current layout")));
  }
  private CdFloatBlock.SolidInputs floatInputs(){
   var s=frame.state();var e=frame.environment();var h=s.header();var g=e.movement().globals();
   return new CdFloatBlock.SolidInputs(new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject()),
       new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),
       new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle()));
  }
  private void floatBlock(int whole){
   requirePhysical(whole);if(physicalId!=CdFloatBlock.OBJECT_ID||floatTopOrdinal!=-1)throw new IllegalStateException("No original FloatBlock dispatch");
   floatTerrain();floatTopOrdinal=0;
   try{var g=frame.environment().movement().gates();var camera=camera();floatBlock.run(whole,new CdFloatBlock.Globals(g.timeZone(),camera.xWord(),camera.yWord()),floatInputs());}
   finally{floatTopOrdinal=-1;}
  }
  private void floatDraw(CdObjectPool pool,CdObjectState.Identity identity){
   requirePhysical(identity.wholeIndex());
   if(physicalId!=CdFloatBlock.OBJECT_ID||floatTopOrdinal<0||!floatBlock.isCurrentDrawCall(pool,identity))throw new IllegalStateException("No original FloatBlock Draw producer");
   drawDynamic(pool,identity);
  }
  private void floatTop(CdFloatBlock.PrefixTopSolidCall call){
   // NativeStopped's original Throwable remains primary even if prefix preparation fails.
   RuntimeException original=call.outcome() instanceof CdSolidObject.NativeStopped stopped?stopped.stop().originalException():null;
   try{floatTopPrepared(call);}
   catch(RuntimeException|Error failure){if(original==null)throw failure;if(failure!=original)original.addSuppressed(failure);throw original;}
  }
  private void floatTopPrepared(CdFloatBlock.PrefixTopSolidCall call){
   requirePhysical(physical);
   if(physicalId!=CdFloatBlock.OBJECT_ID||floatTopOrdinal<0||!floatBlock.isCurrentPrefixTopSolidCall(call,floatTopOrdinal))throw new IllegalStateException("No actual cached original FloatBlock Top producer");
   floatTerrain();var placed=placement.capture();placement.pool().validateRestore(call.callCut());
   if(!placed.pool().slots().equals(call.callCut().slots()))throw new IllegalStateException("Changed original FloatBlock full pool cut");
   var state=active.state();var prefix=active.prefix();var actor=frame.state();var environment=frame.environment();var display=frame.display();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();var oldSounds=sounds;
   CdObjectState own;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication prepared;
   if(call.outcome() instanceof CdSolidObject.Completed completed){var r=completed.completedResult();own=r.object();others=r.otherSlots();fm=r.fmRequests();prepared=frame.prepareObjectSolidResult(call.player(),call.globals(),r);}
   else{var r=((CdSolidObject.NativeStopped)call.outcome()).prefix();own=r.object();others=r.otherSlots();fm=r.fmRequests();prepared=frame.prepareObjectSolidPrefix(call.player(),call.globals(),r);}
   if(own.identity().wholeIndex()!=physical)throw new IllegalStateException("Foreign original Top a0");
   var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);
   var output=CdObjectPool.overlay(call.callCut(),images);placement.pool().validateRestore(output);
   var nextSounds=new ArrayList<>(sounds);for(var value:fm)nextSounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(value)));var readySounds=List.copyOf(nextSounds);
   // All pool, primary/global and ordered FM values are ready before the first native adoption.
   // Public projections and value publications are not the producer: recheck the same live pending call.
   frame.validatePreparedObjectSolidPublication(prepared);requirePhysical(physical);
   var now=placement.capture();
   if(!floatBlock.isCurrentPrefixTopSolidCall(call,floatTopOrdinal)||active.state()!=state||active.prefix()!=prefix||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=oldSounds||!now.status().equals(placed.status())||!same(now.savedFlags(),placed.savedFlags())||!now.pool().slots().equals(call.callCut().slots()))throw new IllegalStateException("Original FloatBlock complete Top cut changed during preparation");
   // No callback between these adoptions; next Top starts only after all three partitions are live.
   placement.pool().restore(output);frame.applyPreparedObjectSolidPublication(prepared);sounds=readySounds;floatTopOrdinal++;
  }
  // Actual $11 source scope. The shared kernel alone owns native pool writes/allocation/deletion.
  private LostRingOccurrence lostOccurrence;
  private final class LostRingOccurrence {
   final int whole;final CdLostRing.InputReads inputs;final CdLostRing.Ports ports;
   final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());FamilyCut expected;
   LostRingOccurrence(int whole){this.whole=whole;inputs=new CdLostRing.InputReads(){
    public int levelVIntCounter(){lostRingExpected();return(int)frame.vIntClock().absoluteValue().orElseThrow(()->new IllegalStateException("Original RingLoss VInt unknown at reached descending phase read"));}
    public int runObjectsD7(){lostRingExpected();return 127-whole;}
    public int zoneByte(){lostRingExpected();return frame.environment().movement().gates().zoneAct()>>>8;}
    public int bottomBoundWord(){lostRingExpected();return frame.environment().movement().bounds().bottom();}
   };ports=lostRingPorts();expected=new FamilyCut();}
  }
  private void lostRing(int whole){
   requirePhysical(whole);if(physicalId!=CdLostRing.OBJECT_ID||lostOccurrence!=null)throw new IllegalStateException("Original RingLoss dispatch reentry");
   var current=new LostRingOccurrence(whole);lostOccurrence=current;
   try{lostRing.run(whole,current.inputs,current.ports);current.expected.check(placement.capture().pool());}finally{lostOccurrence=null;}
  }
  private LostRingOccurrence lostRingInvocation(){
   requirePhysical(physical);var current=lostOccurrence;
   if(current==null||current.whole!=physical||physicalId!=CdLostRing.OBJECT_ID||!lostRing.isCurrentInvocation(physical,current.inputs,current.ports))throw new IllegalStateException("No actual original RingLoss invocation");return current;
  }
  private void lostRingExpected(){var current=lostRingInvocation();current.expected.check(placement.capture().pool());}
  private CdLostRing.SourceCut lostRingCall(Object operation){
   var current=lostRingInvocation();var cut=lostRing.currentSource(operation);
   if(!current.consumed.add(cut))throw new IllegalStateException("Repeated original RingLoss source receipt");
   sourceCall(cut.placement().pool(),cut.source(),CdLostRing.OBJECT_ID);current.expected.check(cut.placement().pool());
   placement.validateRestore(cut.placement());if(!placement.status().equals(cut.placement().status())||!same(placement.capture().savedFlags(),cut.placement().savedFlags()))throw new IllegalStateException("Changed original RingLoss status/saved owner");return cut;
  }
  private void lostRingPublished(){lostOccurrence.expected=new FamilyCut();}
  private CdGlobalObjectAnimation.Extras lostRingSpillOwner(){lostRingExpected();return active.state().globals().animations().orElseThrow(()->new IllegalStateException("Actual original RingLoss spill globals unknown"));}
  private CdLostRing.Ports lostRingPorts(){return new CdLostRing.Ports(new CdRing.GlobalsPort(){
   public CdRingGlobals read(){lostRingExpected();return active.state().globals().ring();}
   public void write(CdRingGlobals value){lostRingCall(value);var g=active.state().globals();new FamilyCut().publish(null,null,SonicCdR31aWorldHost.with(g,Objects.requireNonNull(value),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()),List.of());lostRingPublished();}
  },new CdLostRing.SpillGlobalsPort(){
   public CdRingSpillGlobals read(){return lostRingSpillOwner().spill();}
   public void write(CdRingSpillGlobals value){lostRingCall(value);var g=active.state().globals();var old=lostRingSpillOwner();var next=new CdGlobalObjectAnimation.Extras(old.logSpikeTimerByte(),old.logSpikeFrameByte(),old.unusedTimerByte(),old.unusedFrameByte(),Objects.requireNonNull(value));new FamilyCut().publish(null,null,SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),Optional.of(next),g.paletteSteps7(),g.paletteTimers7()),List.of());lostRingPublished();}
  });}
  private void lostRingAngle(CdLostRing.PrimaryAngleCall call){
   lostRingCall(call);var ready=frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),call.value());new FamilyCut().publish(null,ready,null,List.of());lostRingPublished();
   // ObjGetFloorDist's initial zero-angle store precedes its actual terrain read. Preserve it
   // if the existing current-geometry fence rejects, never borrow stale loaded geometry.
   floatTerrain();
  }
  private void lostRingSound(CdLostRing.SoundCall call){
   lostRingCall(call);SonicCdGameModule.PastSound request=switch(call.request()){
    case CdLostRing.SoundRequest value->new SonicCdGameModule.PastLostRingFm(value.sound());
    case CdLostRing.RewardFmRequest value->new SonicCdGameModule.PastRingFm(value.sound());
    case CdLostRing.RewardSubCpuRequest value->new SonicCdGameModule.PastRingSubCpu(value.command());
    default->throw new IllegalArgumentException("Foreign original RingLoss sound");
   };new FamilyCut().publish(null,null,null,List.of(request));lostRingPublished();
  }
  private void lostRingDraw(CdObjectPool pool,CdObjectState.Identity identity){
   lostRingCall(identity);if(!lostRing.isCurrentDrawCall(pool,identity))throw new IllegalStateException("Foreign original RingLoss Draw");
   familyDraw(pool,identity,()->lostRing.isCurrentDrawCall(pool,identity));lostRingPublished();
  }
  private void lostRingCollect(CdRing.GlobalsPort globals,CdRing.SourceCalls calls){
   var current=lostRingInvocation();lostRingExpected();
   if(!lostRing.isCurrentCollectCall(globals,calls)||!current.consumed.add(globals)||!current.consumed.add(calls))throw new IllegalStateException("Foreign/repeated original CollectRing");
   CdLostRing.collectRing(globals,calls);lostRingExpected();
  }
  private CdObjectAnimation.Guard lostRingAnimationGuard(){return new CdObjectAnimation.Guard(){
   private void check(CdObjectAnimation.Operation operation){lostRingExpected();if(operation.wholeIndex()!=physical||!lostRing.isCurrentAnimationCall(operation))throw new IllegalStateException("No actual original RingLoss animation primitive");}
   public void before(CdObjectAnimation.Operation operation){check(operation);}
   public void after(CdObjectAnimation.Operation operation){check(operation);}
  };}
  // One physical occurrence fence; no second walk, queue, capture bank or result replay.
  private java.util.function.Predicate<CdEnemyPrimitiveScope.Call> enemyIssuer;
  private CdEnemyPrimitiveScope.Call enemyOperation;private FamilyCut enemyBefore,enemyAfter;
  private long enemyGeneration,enemyOrdinal;private boolean enemyUsed;
  private CdEnemyPrimitiveScope.Observer enemyObserver(){return new CdEnemyPrimitiveScope.Observer(){
   public void before(CdEnemyPrimitiveScope.Call c){enemyBefore(c);}
   public void after(CdEnemyPrimitiveScope.Call c){enemyAfter(c);}
  };}
  private void requirePastAntonSource(){
   requirePhysical(physical);
   if(!standardPastProgramme()||physicalId!=CdAnton.OBJECT_ID||enemyAnton==null||!standardSourcePair())
    throw new IllegalStateException("No actual retained original Past Anton source/Frame/current-art pair");
   pastObjects.requirePlacement(placement);
  }
  private void enemyAntonTerrain(){
   if(!standardPastProgramme()){floatTerrain();return;}
   requirePastAntonSource();
   requireOriginalPastAntonTerrain(frame.stageSource(),pastSource,active.state().blocks().orElseThrow(
       ()->new IllegalStateException("Original Past Anton has no completed current blocks")),
       active.state().layout().orElseThrow(()->new IllegalStateException("Original Past Anton has no completed current layout")));
  }
  private void enemyDispatch(int id,java.util.function.Predicate<CdEnemyPrimitiveScope.Call> issuer,Runnable body){
   requirePhysical(physical);if(standardPastProgramme()&&id==CdAnton.OBJECT_ID)requirePastAntonSource();else if(standardPastProgramme())requirePastConnectedEnemySource(id);
   if(physicalId!=id||enemyIssuer!=null||enemyOperation!=null)throw new IllegalStateException("Original enemy dispatch reentry");
   enemyIssuer=issuer;enemyGeneration=0;enemyOrdinal=0;
   try{body.run();}finally{enemyOperation=null;enemyBefore=null;enemyAfter=null;enemyIssuer=null;}
  }
  private void enemyCurrent(CdEnemyPrimitiveScope.Call c){
   requirePhysical(physical);if(standardPastProgramme()&&physicalId==CdAnton.OBJECT_ID)requirePastAntonSource();else if(standardPastProgramme())requirePastConnectedEnemySource(physicalId);if(enemyIssuer==null||!enemyIssuer.test(c)||c.whole()!=physical||c.id()!=physicalId)throw new IllegalStateException("No cached original enemy issuer");
  }
  private void enemyBefore(CdEnemyPrimitiveScope.Call c){
   enemyCurrent(c);if(enemyOperation!=null||c.completed())throw new IllegalStateException("Repeated original enemy operation");
   if(c.kind()==CdEnemyPrimitiveScope.Kind.ENTER){if(enemyOrdinal!=0||placement.pool().state(physical).id()!=physicalId)throw new IllegalStateException("Wrong original enemy entry");enemyGeneration=c.generation();}
   if(c.generation()!=enemyGeneration||c.ordinal()!=enemyOrdinal+1)throw new IllegalStateException("Stale original enemy generation/ordinal");
   placement.validateRestore(c.before());var now=placement.capture();
   if(!now.status().equals(c.before().status())||!same(now.savedFlags(),c.before().savedFlags())||!now.pool().slots().equals(c.before().pool().slots()))throw new IllegalStateException("Changed original enemy full96/status/saved");
   enemyOperation=c;enemyBefore=new FamilyCut();enemyAfter=null;enemyUsed=false;enemyBefore.check(c.before().pool());
  }
  private CdEnemyPrimitiveScope.Call enemyUse(CdEnemyPrimitiveScope.Kind kind){
   var c=enemyOperation;enemyCurrent(c);if(c.kind()!=kind||c.completed()||enemyUsed)throw new IllegalStateException("Wrong/repeated original enemy primitive");
   enemyBefore.check(c.before().pool());enemyUsed=true;return c;
  }
  private void enemyAfter(CdEnemyPrimitiveScope.Call c){
   try{
    enemyCurrent(c);if(enemyOperation!=c||enemyBefore==null)throw new IllegalStateException("Unprepared original enemy completion");
    var pool=c.before().pool();var saved=c.before().savedFlags();
    if(c.completed()){
     if(c.output()!=null)pool=CdObjectPool.overlay(pool,List.of(c.output()));
     if(c.kind()==CdEnemyPrimitiveScope.Kind.DELETE){var cleared=placement.pool().state(physical);for(byte b:cleared.copyBytes())if(b!=0)throw new IllegalStateException("Original enemy DeleteObject did not clear64");pool=CdObjectPool.overlay(pool,List.of(cleared));}
     if(c.kind()==CdEnemyPrimitiveScope.Kind.SAVED_CLEAR){var b=saved.copy();b[c.savedIndex()]&=0x7F;saved=CdAssetBytes.copyOf(b);}
     if(c.kind()==CdEnemyPrimitiveScope.Kind.ANGLE||c.kind()==CdEnemyPrimitiveScope.Kind.FM||c.kind()==CdEnemyPrimitiveScope.Kind.PLAYER||c.kind()==CdEnemyPrimitiveScope.Kind.DRAW){if(!enemyUsed)throw new IllegalStateException("Missing actual original enemy primitive");}
    }else{
     // A fallible original callback may have completed its one native publication before throwing.
     // Only the private callback records that exact landed cut; no arbitrary observed image is adopted.
     if(enemyAfter!=null){enemyAfter.check(enemyAfter.placed.pool());return;}
    }
    if(enemyAfter!=null)enemyAfter.check(pool,saved);else enemyBefore.check(pool,saved);
    enemyOrdinal=c.ordinal();
   }finally{enemyOperation=null;enemyBefore=null;enemyAfter=null;enemyUsed=false;}
  }
  private void enemyDraw(CdObjectPool pool,CdObjectState.Identity id){
   var c=enemyUse(CdEnemyPrimitiveScope.Kind.DRAW);if(pool!=placement.pool()||id.wholeIndex()!=c.whole()||id!=pool.state(physical).identity())throw new IllegalStateException("Foreign original enemy Draw");drawDynamic(pool,id);
  }
  private void enemyAngle(int expected,int value){
   enemyUse(CdEnemyPrimitiveScope.Kind.ANGLE);
   var p=frame.prepareObjectPrimaryAngle(expected,value);frame.validatePreparedObjectSolidPublication(p);enemyBefore.check(enemyBefore.placed.pool());
   frame.applyPreparedObjectSolidPublication(p);enemyAfter=new FamilyCut();enemyAfter.check(enemyAfter.placed.pool());
  }
  private void enemyAntonAngle(CdAnton.PrimaryAngleCall c){if(!enemyAnton.isCurrentPrimaryAngleCall(c))throw new IllegalStateException("No original Anton primary store");sourceCall(c.callCut(),c.expectedSource(),CdAnton.OBJECT_ID);enemyAngle(c.expectedPrimaryAngle(),c.value());enemyAntonTerrain();}
  private void enemyTamabbohAngle(CdTamabboh.PrimaryAngleCall c){if(!enemyTamabboh.isCurrentPrimaryAngleCall(c))throw new IllegalStateException("No original Tamabboh angle store");sourceCall(c.callCut(),c.expectedSource(),CdTamabboh.OBJECT_ID);enemyAngle(c.expectedPrimaryAngle(),c.value());pastConnectedEnemyTerrain();}
  private int enemyTamabbohPlayer(CdTamabboh.PlayerReadCall c){if(!enemyTamabboh.isCurrentPlayerReadCall(c))throw new IllegalStateException("No original Tamabboh player read");sourceCall(c.callCut(),c.expectedSource(),CdTamabboh.OBJECT_ID);enemyUse(CdEnemyPrimitiveScope.Kind.PLAYER);var m=frame.state().motion();return c.field()==CdTamabboh.PlayerField.Y_WORD?m.centreY16_16()>>>16:m.centreX16_16()>>>16;}
  private void enemyTamabbohFm(CdTamabboh.FmCall c){if(!enemyTamabboh.isCurrentFmCall(c))throw new IllegalStateException("No original Tamabboh FM");sourceCall(c.callCut(),c.expectedSource(),CdTamabboh.OBJECT_ID);enemyUse(CdEnemyPrimitiveScope.Kind.FM);sound(new SonicCdGameModule.PastTamabbohFm(c.sound()));enemyAfter=new FamilyCut();}
  /** Same source/art/placement guard for exactly the connected B PPZ enemy adapters. */
  private void requirePastConnectedEnemySource(int id){
   requirePhysical(physical);
   boolean cached=id==CdPataBata.OBJECT_ID?enemyPataBata!=null:id==CdMosqui.OBJECT_ID?mosqui!=null:id==CdTamabboh.OBJECT_ID&&enemyTamabboh!=null;
   if(!standardPastProgramme()||physicalId!=id||!cached||!standardSourcePair())throw new IllegalStateException("No actual retained original Past connected enemy source/current-art pair");
   pastObjects.requirePlacement(placement);
  }
  private void pastConnectedEnemyTerrain(){
   if(!standardPastProgramme()){floatTerrain();return;}
   requirePastConnectedEnemySource(physicalId);
   requireOriginalPastAntonTerrain(frame.stageSource(),pastSource,active.state().blocks().orElseThrow(
       ()->new IllegalStateException("Original Past enemy has no completed current blocks")),active.state().layout().orElseThrow(
       ()->new IllegalStateException("Original Past enemy has no completed current layout")));
  }
  // Mosqui already owns synchronous original pool/saved/fault prefixes. This adapter adds
  // the same complete current Frame/World checks used by the other PPZ enemy primitives;
  // no routine or animation is repeated and no returned metadata is replayed.
  private boolean pastMosquiRunning;private long pastMosquiOrdinal;
  private PastMosquiOperation pastMosquiOperation;
  private final class PastMosquiOperation {
   final Object call;final FamilyCut before=new FamilyCut();FamilyCut after;
   CdObjectPool.Snapshot expected=before.placed.pool();CdAssetBytes saved=before.placed.savedFlags();boolean landed,used;
   PastMosquiOperation(Object call){this.call=call;}
   void check(){if(after!=null)after.check(after.placed.pool());else before.check(landed?expected:before.placed.pool(),landed?saved:before.placed.savedFlags());}
  }
  private void pastMosquiCurrent(Object call){
   requirePastConnectedEnemySource(CdMosqui.OBJECT_ID);
   if(!pastMosquiRunning||!mosqui.isCurrentCall(call))throw new IllegalStateException("No cached original B Mosqui operation");
  }
  private void pastMosquiStart(Object call,CdObjectPool.Snapshot pool,CdObjectState source){
   pastMosquiCurrent(call);if(pastMosquiOperation!=null)throw new IllegalStateException("Nested original B Mosqui operation");
   sourceCall(pool,source,CdMosqui.OBJECT_ID);var operation=new PastMosquiOperation(call);operation.before.check(pool);pastMosquiOperation=operation;
  }
  private PastMosquiOperation pastMosquiUse(Object call){
   pastMosquiCurrent(call);var operation=pastMosquiOperation;
   if(operation==null||operation.call!=call||operation.used)throw new IllegalStateException("Foreign/repeated original B Mosqui callback");
   operation.before.check(operation.before.placed.pool());operation.used=true;return operation;
  }
  private CdMosqui.DispatchObserver pastMosquiObserver(){return new CdMosqui.DispatchObserver(){
   public void beforePublication(CdMosqui.Publication c){
    pastMosquiStart(c,c.cut().pool(),c.source());var o=pastMosquiOperation;
    if(c.ordinal()!=pastMosquiOrdinal+1||!c.cut().status().equals(o.before.placed.status())||!same(c.cut().savedFlags(),o.before.placed.savedFlags()))throw new IllegalStateException("Changed original B Mosqui publication ordinal/bank/status");
    if(c.savedIndex()>=0){var bytes=o.saved.copy();if(c.savedIndex()>=bytes.length)throw new IllegalArgumentException("Unowned Mosqui saved byte");bytes[c.savedIndex()]&=127;o.saved=CdAssetBytes.copyOf(bytes);}
    else if(c.output()!=null)o.expected=CdObjectPool.overlay(o.expected,List.of(c.output()));
    else{var comparison=CdObjectPool.stagingCopy(o.expected);comparison.deleteObject(physical);o.expected=comparison.capture();}
   }
   public void afterPublication(CdMosqui.Publication c){
    pastMosquiCurrent(c);var o=pastMosquiOperation;if(o==null||o.call!=c)throw new IllegalStateException("Unprepared original B Mosqui publication");o.landed=true;o.check();pastMosquiOrdinal=c.ordinal();pastMosquiOperation=null;
   }
   public void beforeCall(Object c){
    if(c instanceof CdMosqui.DrawCall d){pastMosquiStart(c,d.cut().pool(),d.source());if(!d.cut().status().equals(pastMosquiOperation.before.placed.status())||!same(d.cut().savedFlags(),pastMosquiOperation.before.placed.savedFlags()))throw new IllegalStateException("Changed original B Mosqui Draw status/bank");}
    else if(c instanceof CdMosqui.PrimaryAngleCall a)pastMosquiStart(c,a.actualCut(),a.expectedSource());
    else if(c instanceof CdMosqui.FmCall f)pastMosquiStart(c,f.actualCut(),f.expectedSource());
    else throw new IllegalArgumentException("Unknown original B Mosqui callback");
   }
   public void afterCall(Object c){pastMosquiCurrent(c);var o=pastMosquiOperation;if(o==null||o.call!=c||!o.used||o.after==null)throw new IllegalStateException("Uncompleted original B Mosqui callback");o.check();pastMosquiOperation=null;}
  };}
  private void pastMosquiDispatch(int whole,CdMosqui.Globals globals){
   requirePastConnectedEnemySource(CdMosqui.OBJECT_ID);
   if(whole!=physical||pastMosquiRunning||pastMosquiOperation!=null||enemyIssuer!=null)throw new IllegalStateException("Original B Mosqui dispatch reentry");
   pastMosquiRunning=true;pastMosquiOrdinal=0;Throwable original=null;
   try{mosqui.run(whole,globals);if(pastMosquiOperation!=null)throw new IllegalStateException("Unreturned original B Mosqui operation");}
   catch(RuntimeException|Error failure){original=failure;throw failure;}
   finally{try{if(pastMosquiOperation!=null)pastMosquiOperation.check();}catch(RuntimeException|Error verification){if(original==null)throw verification;if(verification!=original)original.addSuppressed(verification);}finally{pastMosquiOperation=null;pastMosquiRunning=false;pastMosquiOrdinal=0;}}
  }
  private void pastMosquiAngle(CdMosqui.PrimaryAngleCall c){
   var o=pastMosquiUse(c);var p=frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),c.value());frame.validatePreparedObjectSolidPublication(p);o.before.check(o.before.placed.pool());
   frame.applyPreparedObjectSolidPublication(p);o.after=new FamilyCut();o.check();pastConnectedEnemyTerrain();
  }
  private void pastMosquiFm(CdMosqui.FmCall c){var o=pastMosquiUse(c);sound(new SonicCdGameModule.PastMosquiFm(c.sound()));o.after=new FamilyCut();o.check();}
  private void pastMosquiDraw(CdMosqui.DrawCall c){
   var o=pastMosquiUse(c);drawDynamic(placement.pool(),c.source().identity());o.after=new FamilyCut();o.check();
  }
  private void rampDraw(CdObjectPool p,CdObjectState.Identity id){familyDraw(p,id,()->ramp.isCurrentDrawCall(p,id));}
  private void boulderDraw(CdObjectPool p,CdObjectState.Identity id){familyDraw(p,id,()->boulder.isCurrentDrawCall(p,id));}
  private void spikesDraw(CdObjectPool p,CdObjectState.Identity id){familyDraw(p,id,()->spikes.isCurrentDrawCall(p,id));}
  private void boulderSolid(CdBoulder.PrefixSolidCall c){familySolid(()->physicalId==CdBoulder.OBJECT_ID&&boulder.isCurrentPrefixSolidCall(c),c.callCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());}
  private void spikesSolid(CdSpikes.PrefixSolidCall c){familySolid(()->physicalId==CdSpikes.OBJECT_ID&&spikes.isCurrentPrefixSolidCall(c),c.actualCut(),c.expectedPlayer(),c.expectedGlobals(),c.outcome());}
  // Shared semantic publication adapter: full96 + saved/status + complete Frame/World cut.
  // Outcomes are values. Each caller must prove its cached body's currently pending operation.
  private final class FamilyCut {
   final SonicCdGameModule.R11LoadingState state=active.state();
   final SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
   final Object actor=frame.state(),environment=frame.environment(),display=frame.display(),artImage=frame.artState(),clock=frame.vIntClock();
   final boolean paused=frame.paused();final CdObjectPlacementRuntime.Snapshot placed=placement.capture();
   final List<SonicCdGameModule.PastSound> oldSounds=sounds;
   final CdObjectDrawQueue originalQueue=queue;final Optional<CdObjectDrawQueue.CpuSat> originalCpu=cpu;
   void check(CdObjectPool.Snapshot expected){check(expected,placed.savedFlags());}
   void check(CdObjectPool.Snapshot expected,CdAssetBytes saved){check(expected,saved,oldSounds);}
   void checkSoundOnly(List<SonicCdGameModule.PastSound> expectedSounds){check(placed.pool(),placed.savedFlags(),expectedSounds);}
   private void check(CdObjectPool.Snapshot expected,CdAssetBytes saved,List<SonicCdGameModule.PastSound> expectedSounds){
    requirePhysical(physical);placement.pool().validateRestore(expected);var now=placement.capture();
    if(active.state()!=state||active.prefix()!=prefix||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=expectedSounds||queue!=originalQueue||cpu!=originalCpu||!now.status().equals(placed.status())||!same(now.savedFlags(),saved)||!now.pool().slots().equals(expected.slots()))throw new IllegalStateException("Changed complete original family cut");
   }
   void publish(CdObjectPool.Snapshot pool,CdPlayerFrameRuntime.ObjectSolidPublication player,
           SonicCdR31aWorldHost.Globals globals,List<SonicCdGameModule.PastSound> fm){
    var readyState=globals==null?state:state.globals(globals);var readyPrefix=globals==null?prefix:prefix.globals(globals);var next=new ArrayList<>(oldSounds);next.addAll(fm);var readySounds=List.copyOf(next);
    if(pool!=null)placement.pool().validateRestore(pool);if(player!=null)frame.validatePreparedObjectSolidPublication(player);
    check(placed.pool()); // No callbacks after preflight or between the prepared owner adoptions.
    if(pool!=null)placement.pool().restore(pool);if(player!=null)frame.applyPreparedObjectSolidPublication(player);
    if(readyState!=state)active.primaryLifecycle(state,prefix,readyState,readyPrefix);sounds=readySounds;
   }
  }
  /** Native ObjSpinSplash (Spin Tunnel/Main (Waterfall Splash).asm:7-51) owns its
   * stores/Animate/Draw/delete. Sonic's existing allocation prefix is never repeated here.
   * A FamilyCut checks complete current Frame/Module/queue/sound/full96/status/saved state;
   * after compares the landed image, never a stale pre-store source object identity. */
  private final class WaterfallOperation {
   final CdSpinTunnelSplash.Operation call;final FamilyCut before=new FamilyCut();
   final CdObjectPool.Snapshot output;boolean used;
   WaterfallOperation(CdSpinTunnelSplash.Operation call){
    this.call=call;var pool=before.placed.pool();
    if(call.output()!=null)output=CdObjectPool.overlay(pool,List.of(call.output()));
    else if(call.kind()==CdSpinTunnelSplash.Kind.DELETE_OBJECT){var comparison=CdObjectPool.stagingCopy(pool);comparison.deleteObject(physical);output=comparison.capture();}
    else if(call.kind()==CdSpinTunnelSplash.Kind.DRAW_OBJECT)output=CdObjectPool.overlay(pool,List.of(call.before().moveByte(CdObjectState.ByteField.SPRITE_FLAGS,call.before().spriteFlags()&127)));
    else output=pool;
   }
  }
  private void waterfallSource(){
   requirePhysical(physical);
   if(!waterfallDispatch||physical<32||physicalId!=CdSpinTunnelSplash.OBJECT_ID||queue==null
      ||!frame.stageSource().ownsPresent(assets)||!art.ownsLoadingAssets(assets)
      ||placement.placements()!=assets.placements()||!metadata.identity().equals(assets.identity())
      ||waterfallData.mappingsAddress()!=CdSpinTunnelSplash.MAPPING_ADDRESS
      ||waterfallData.animation().bytes()!=metadata.view(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_ANIMATIONS).bytes()
      ||waterfallData.animation().tableOffset()!=0||waterfallData.animation().scriptCount()!=1
      ||waterfallData.animation().bytes().length()!=CdSpinTunnelSplash.ANIMATION_BYTES
      ||metadata.view(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_MAPPINGS).bytes().length()!=CdSpinTunnelSplash.MAPPING_BYTES)
    throw new IllegalStateException("No actual original waterfall source/physical/Frame cut");
  }
  private void waterfallBefore(CdSpinTunnelSplash.Operation call){
   waterfallSource();
   if(!waterfallSplash.isCurrentCall(call)||call.completed()||waterfallOperation!=null
      ||call.wholeIndex()!=physical||call.ordinal()!=waterfallOrdinal+1)
    throw new IllegalStateException("Foreign/repeated original waterfall occurrence");
   // Shared Prefix and this wrapper each hold immutable value images of the same store;
   // their Java references need not coincide. Current private occurrence + exact whole cut
   // proves the source, then the existing helper receives the actual live pool object.
   var current=placement.pool().state(physical);
   if(!call.before().equals(current))throw new IllegalStateException("Changed original waterfall source image");
   sourceCall(call.cut().pool(),current,CdSpinTunnelSplash.OBJECT_ID);
   placement.validateRestore(call.cut());var operation=new WaterfallOperation(call);
   operation.before.check(call.cut().pool(),call.cut().savedFlags());
   if(!operation.before.placed.status().equals(call.cut().status()))throw new IllegalStateException("Changed original waterfall placement cursor");
   waterfallOperation=operation;waterfallOrdinal=call.ordinal();waterfallOperations++;
  }
  private void waterfallAfter(CdSpinTunnelSplash.Operation call){
   var operation=waterfallOperation;
   // Guard.after also runs when before refused. With no prepared scope and no completed action
   // there is nothing to adopt/check against a fabricated World cut. The kernel retains its
   // original fault and independently checks its full placement before/after this callback.
   if(operation==null){if(call.completed())throw new IllegalStateException("Unprepared completed waterfall occurrence");return;}
   if(operation.call!=call)throw new IllegalStateException("Foreign waterfall unwind");
   try{
    waterfallSource();if(!waterfallSplash.isCurrentCall(call))throw new IllegalStateException("Retired original waterfall occurrence");
    boolean landed=placement.pool().capture().slots().equals(operation.output.slots());
    operation.before.check(call.completed()||landed?operation.output:operation.before.placed.pool());
    if(call.completed()&&(call.kind()==CdSpinTunnelSplash.Kind.TIME_ZONE||call.kind()==CdSpinTunnelSplash.Kind.DRAW_OBJECT)&&!operation.used)
     throw new IllegalStateException("Completed waterfall callback never entered its actual owner");
   }finally{waterfallOperation=null;}
  }
  private WaterfallOperation waterfallUse(CdSpinTunnelSplash.Kind kind){
   waterfallSource();var operation=waterfallOperation;
   if(operation==null||operation.used||operation.call.kind()!=kind||!waterfallSplash.isCurrentCall(operation.call)||operation.call.completed())
    throw new IllegalStateException("No current original waterfall callback");
   operation.before.check(operation.before.placed.pool());operation.used=true;return operation;
  }
  private int waterfallTimeZone(){
   var operation=waterfallUse(CdSpinTunnelSplash.Kind.TIME_ZONE);
   int value=frame.environment().movement().gates().timeZone();operation.before.check(operation.before.placed.pool());return value;
  }
  private void waterfallDraw(CdSpinTunnelSplash.Operation call){
   var operation=waterfallUse(CdSpinTunnelSplash.Kind.DRAW_OBJECT);
   if(operation.call!=call)throw new IllegalStateException("Foreign original waterfall Draw");
   familyDraw(placement.pool(),call.before().identity(),()->waterfallSplash.isCurrentCall(call));
  }
  private void waterfall(int whole){
   requirePhysical(whole);if(waterfallDispatch||waterfallOperation!=null)throw new IllegalStateException("Original waterfall dispatch reentry");
   waterfallDispatch=true;waterfallOrdinal=0;waterfallOperations=0;
   try{waterfallSplash.run(whole);}finally{waterfallOperation=null;waterfallDispatch=false;}
  }
  // N1ecc5f3a ObjFlower (Flower/Main.asm:11-162). All gameplay remains in the shared
  // kernel; this scope authenticates its synchronous stores, floor angles, saved operands and Draw.
  private UnsupportedOperationException flowerUnavailable(String site){return new UnsupportedOperationException("Original R11 "+site+" at dynamic whole="+physical);}
  private FlowerInvocation flowerInvocation;
  private final class FlowerInvocation {
   final Thread thread=Thread.currentThread();final int whole=physical;final Object owner=active;
   final ArrayDeque<FlowerOperation> operations=new ArrayDeque<>();FamilyCut expected=new FamilyCut();long ordinal;boolean drew;
   int savedStage,period,oldCount;
  }
  private final class FlowerOperation {
   final Object call;final FamilyCut before=new FamilyCut();final CdObjectPool.Snapshot output;
   FamilyCut expected=before;boolean prepared,used,after;OriginalR11FlowerPositionStore positionStore;
   FlowerOperation(Object call,CdObjectState output){this.call=call;this.output=output==null?before.placed.pool():CdObjectPool.overlay(before.placed.pool(),List.of(output));}
  }
  private void requireFlower(){
   requirePhysical(physical);var i=flowerInvocation;
   if(i==null||i.owner!=active||i.thread!=Thread.currentThread()||i.whole!=physical||physicalId!=CdFlower.OBJECT_ID||queue==null
      ||waterProgramme()||!frame.stageSource().ownsPresent(assets)||!art.ownsLoadingAssets(assets)
      ||placement.placements()!=assets.placements()||!metadata.identity().equals(assets.identity())
      ||flower.assets().flower()!=metadata.flower()||flower.assets().mappingsAddress()!=metadata.view(CdR11aObjectAssets.Table.FLOWER_MAPPINGS).range().nativeAddress())
    throw new IllegalStateException("No actual original R11 Flower source/World/physical occurrence");
  }
  private FlowerOperation currentFlower(Object call){requireFlower();var op=flowerInvocation.operations.peek();if(op==null||op.call!=call||!flower.isCurrentCall(call))throw new IllegalStateException("Foreign, stale or repeated original Flower operation");return op;}
  private void flowerBegin(Object call,long ordinal,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,CdObjectState output){
   requireFlower();var i=flowerInvocation;if(!flower.isCurrentCall(call)||ordinal!=i.ordinal+1||!source.equals(placement.pool().state(physical)))throw new IllegalStateException("Changed Flower source/ordinal");
   var parent=i.operations.peek();if(parent!=null){if(!(parent.call instanceof CdFlower.FloorCall)||!(call instanceof CdFlower.PrimaryAngleCall)||!parent.prepared||parent.after)throw new IllegalStateException("Only actual Flower floor-angle nesting");parent.expected.check(parent.output);}
   else i.expected.check(i.expected.placed.pool());
   var before=new FamilyCut();placement.validateRestore(cut);before.check(cut.pool(),cut.savedFlags());if(!cut.status().equals(before.placed.status()))throw new IllegalStateException("Changed Flower placement cursor");
   if(output!=null&&!output.identity().equals(source.identity()))throw new IllegalArgumentException("Foreign Flower store slot");
   i.operations.push(new FlowerOperation(call,output));i.ordinal=ordinal;
  }
  private void flowerBefore(Object c){switch(c){
   case CdFlower.FloorCall a->flowerBegin(a,a.ordinal(),a.cut(),a.source(),null);
   case CdFlower.PrimaryAngleCall a->flowerBegin(a,a.ordinal(),a.cut(),a.source(),null);
   case CdFlower.DrawCall a->flowerBegin(a,a.ordinal(),a.cut(),a.source(),a.source().moveByte(CdObjectState.ByteField.SPRITE_FLAGS,a.source().spriteFlags()&127));
   case CdFlower.SavedCall a->flowerBegin(a,a.ordinal(),a.cut(),a.source(),null);
   default->throw new IllegalStateException("Unknown original Flower operation");}}
  private void flowerPrepared(Object c){var op=currentFlower(c);if(op.prepared||op.after)throw new IllegalStateException("Repeated Flower preparation");op.before.check(op.before.placed.pool());op.prepared=true;}
  private FlowerOperation flowerUse(Object c){var op=currentFlower(c);if(!op.prepared||op.used||op.after)throw new IllegalStateException("Unprepared/repeated Flower primitive");op.before.check(op.before.placed.pool());op.used=true;return op;}
  private void flowerAngle(CdFlower.PrimaryAngleCall c){
   var op=flowerUse(c);if(flowerInvocation.operations.size()!=2)throw new IllegalStateException("Flower angle without actual floor parent");
   op.before.publish(null,frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),c.value()),null,List.of());op.expected=new FamilyCut();
   // ObjGetFloorDist's real prezero has already landed. Refuse an unowned changed/current
   // terrain BEFORE the kernel can read retained geometry; never restore or invent the angle.
   var s=active.state();if(s.blocks().isEmpty()||s.layout().isEmpty()||!same(s.blocks().orElseThrow(),assets.terrain().blocks())||!same(s.layout().orElseThrow(),assets.terrain().layout().ram()))throw flowerUnavailable("Flower current floor block/layout owner after primary-angle prefix");
  }
  private void flowerDraw(CdFlower.DrawCall c){var op=flowerUse(c);if(flowerInvocation.drew)throw new IllegalStateException("Repeated Flower Draw");familyDraw(placement.pool(),c.source().identity(),()->flower.isCurrentCall(c));op.expected=new FamilyCut();}
  private int flowerSaved(CdFlower.SavedCall c){
   var op=flowerUse(c);var i=flowerInvocation;switch(c.site()){
    case TIME_ZONE->{int routine=c.source().routine();if(routine==0){if(i.savedStage!=0)throw new IllegalStateException("Wrong Flower Init saved order");}else if(routine==2){if(i.savedStage!=0&&i.savedStage!=3)throw new IllegalStateException("Wrong Flower landing saved order");i.savedStage=i.savedStage==0?1:4;}else throw new IllegalStateException("No Flower period site");int raw=frame.environment().movement().gates().timeZone();i.period=raw&127;return raw;}
    case COUNT_READ->{if(i.savedStage!=1||c.offset()!=i.period)throw new IllegalStateException("Wrong Flower count read");if(c.offset()>3)throw flowerUnavailable("Flower count BYTE alias $"+Integer.toHexString(c.offset()));i.oldCount=SonicCdR31aObjectPass.flowerCountByte(op.before.prefix.main().level(),c.offset());i.savedStage=2;return i.oldCount;}
    case COUNT_INCREMENT->{if(i.savedStage!=2||c.offset()!=i.period||c.value()!=((i.oldCount+1)&255))throw new IllegalStateException("Wrong Flower count increment");var next=SonicCdR31aObjectPass.flowerCountLevel(op.before.prefix.main().level(),c);var prefix=op.before.prefix.level(next);op.before.check(op.before.placed.pool());active.primaryLifecycle(op.before.state,op.before.prefix,op.before.state,prefix);op.expected=new FamilyCut();i.savedStage=3;return c.value();}
    case POSITION_X,POSITION_Y->{boolean x=c.site()==CdFlower.SavedSite.POSITION_X;if(i.savedStage!=(x?4:5))throw new IllegalStateException("Wrong Flower position order");int offset=((i.oldCount&63)*4+(i.period<<8))&65535;if(c.offset()!=(x?offset:(offset+2)&65535)||c.value()!=(x?c.source().centreX():c.source().centreY()))throw new IllegalStateException("Wrong Flower native position operand");
     var s=op.before.state;var prefix=op.before.prefix;var ready=s;
     if(c.offset()<CdFlowerPositions.BYTES){op.positionStore=new OriginalR11FlowerPositionStore(this,c,s.flowers());var positions=CdFlowerPositions.originalStore(s.flowers(),op.positionStore);ready=new SonicCdGameModule.R11LoadingState(s.phase(),s.scroll(),s.globals(),s.reserved(),positions,s.cpuSat(),s.blocks(),s.layout(),s.nativeCarry(),s.shieldCpu(),s.noBumperByte(),s.bossStartedByte(),s.fadeSteps(),s.displayEnabled(),s.clears());}
     else if(c.offset()==0x300||c.offset()==0x302)prefix=prefix.level(CdFlowerPositions.aliasWord(prefix.main().level(),c.offset(),c.value()));
     else throw flowerUnavailable("Flower position WORD owner at $"+Integer.toHexString(c.offset()));
     op.before.check(op.before.placed.pool());active.primaryLifecycle(op.before.state,op.before.prefix,ready,prefix);op.expected=new FamilyCut();i.savedStage=x?5:6;return c.value();}
   }throw new IllegalStateException("Unknown Flower saved site");
  }
  private void flowerAfter(Object c){var op=currentFlower(c);if(!op.prepared||op.after)throw new IllegalStateException("Missing Flower preparation");if(c instanceof CdFlower.FloorCall a){flower.currentFloorResult(a);op.used=true;}if(!(c instanceof CdFlower.Publication)&&!op.used)throw new IllegalStateException("Missing Flower owner callback");op.expected.check(op.output);op.after=true;}
  private void flowerCompleted(Object c){var op=currentFlower(c);if(!op.after)throw new IllegalStateException("Missing Flower after receipt");op.expected.check(op.output);var i=flowerInvocation;var next=new FamilyCut();i.operations.pop();var parent=i.operations.peek();if(parent!=null){if(!(parent.call instanceof CdFlower.FloorCall)||!(c instanceof CdFlower.PrimaryAngleCall))throw new IllegalStateException("Foreign Flower completion nesting");parent.expected=next;}else i.expected=next;if(c instanceof CdFlower.DrawCall)i.drew=true;}
  private void flower(int whole){
   requirePhysical(whole);if(flowerInvocation!=null)throw new IllegalStateException("Flower dispatch reentry");var i=new FlowerInvocation();flowerInvocation=i;
   try{var result=flower.run(whole);requireFlower();if(!i.operations.isEmpty()||!i.drew||result.state()!=placement.pool().state(whole))throw new IllegalStateException("Unreturned original Flower dispatch");i.expected.check(i.expected.placed.pool());}
   catch(RuntimeException|Error fault){try{var op=i.operations.peek();if(op==null)i.expected.check(i.expected.placed.pool());else{var actual=placement.pool().capture();boolean landed=actual.slots().equals(op.output.slots());op.expected.check(landed?op.output:op.before.placed.pool());}}catch(RuntimeException|Error verify){if(verify!=fault)fault.addSuppressed(verify);}throw fault;}
   finally{flowerInvocation=null;}
  }
  private final class MechanismOperation {
   final CdOriginalMechanismProtocol.Call call;final FamilyCut before=new FamilyCut();FamilyCut after;
   final Object scope=active;CdObjectPool.Snapshot pool=before.placed.pool();CdAssetBytes saved=before.placed.savedFlags();boolean used;
   MechanismOperation(CdOriginalMechanismProtocol.Call c){call=c;}
  }
  private boolean mechanismCurrent(CdOriginalMechanismProtocol.Call c){
   requirePhysical(physical);return switch(physicalId){case 9->mechanismDisc.isCurrentOriginalOperation(c);case 15->mechanismMovingSpring.isCurrentOriginalOperation(c);case 40->mechanismBoard.isCurrentOriginalOperation(c);case 49->mechanismHole.isCurrentOriginalOperation(c);case 54->mechanismWall.isCurrentOriginalOperation(c);default->false;};
  }
  private void beforeMechanism(CdOriginalMechanismProtocol.Call c){
   requirePhysical(physical);if(mechanismOperation!=null||!mechanismCurrent(c)||c.completed()||c.ordinal()!=mechanismOrdinal+1||c.source()!=placement.pool().state(physical))throw new IllegalStateException("Foreign/repeated original mechanism primitive");
   placement.validateRestore(c.cut());var op=new MechanismOperation(c);op.before.check(c.cut().pool(),c.cut().savedFlags());if(!op.before.placed.status().equals(c.cut().status()))throw new IllegalArgumentException("Stale mechanism placement cursor");mechanismOperation=op;
   switch(c.site()){
    case STORE->op.pool=CdObjectPool.overlay(op.pool,List.of(Objects.requireNonNull(c.output())));
    case DELETE->{var staged=CdObjectPool.stagingCopy(op.pool);staged.deleteObject(physical);op.pool=staged.capture();}
    case SAVED->{if(c.savedIndex()<2||c.savedIndex()>=CdObjectPlacementRuntime.SAVED_BANK_BYTES||(c.savedValue()&~255)!=0)throw new IllegalArgumentException("Invalid native mechanism bank BYTE");var b=op.saved.copy();b[c.savedIndex()]=(byte)c.savedValue();op.saved=CdAssetBytes.copyOf(b);}
    case CALLBACK->{
     if(c.payload()==CdOriginalMechanismProtocol.NativeSite.FLOOR_OWNER||c.payload()==CdOriginalMechanismProtocol.NativeSite.DISC_SOLID_OWNER||c.payload()==CdOriginalMechanismProtocol.NativeSite.WALL_SOLID_OWNER){try{if(waterProgramme()&&physicalId==CdMovingSpring.OBJECT_ID)ttBlockTerrain();else floatTerrain();}catch(IllegalArgumentException|IllegalStateException missing){throw new UnownedMechanismTerrain((CdOriginalMechanismProtocol.NativeSite)c.payload(),c.source(),missing);}op.used=true;mechanismReadCheckpoint();}
    }
    case ANIMATION_READ->{}
   }
  }
  private void useMechanism(Object payload){var op=mechanismOperation;requirePhysical(physical);if(op==null||op.scope!=active||op.call.payload()!=payload||op.call.site()!=CdOriginalMechanismProtocol.Site.CALLBACK||op.used||!mechanismCurrent(op.call))throw new IllegalStateException("No original mechanism callback");op.before.check(op.before.placed.pool());op.used=true;}
  private void mechanismSource(CdObjectPool.Snapshot pool,CdObjectState source){var op=mechanismOperation;if(source!=op.call.source()||!pool.slots().equals(op.before.placed.pool().slots()))throw new IllegalArgumentException("Foreign original mechanism source64/full96");}
  private void mechanismCheckpoint(){mechanismOperation.after=new FamilyCut();}
  private void mechanismReadCheckpoint(){var op=mechanismOperation;op.before.check(op.before.placed.pool());mechanismCheckpoint();}
  private void afterMechanism(CdOriginalMechanismProtocol.Call c){
   var op=mechanismOperation;if(op==null||op.call!=c||op.scope!=active||!mechanismCurrent(c))throw new IllegalStateException("Unreturned mechanism primitive");
   if(c.site()==CdOriginalMechanismProtocol.Site.CALLBACK&&(!op.used||op.after==null))throw new IllegalStateException("Omitted native mechanism callback");
   if(op.after!=null)op.after.check(op.after.placed.pool());else op.before.check(c.completed()?op.pool:op.before.placed.pool(),c.completed()?op.saved:op.before.placed.savedFlags());
   mechanismOrdinal=c.ordinal();mechanismOperation=null;
  }
  private void failedMechanism(CdOriginalMechanismProtocol.Call c,Throwable original){
   try{var op=mechanismOperation;if(op==null)return;if(op.call!=c||op.scope!=active||!mechanismCurrent(c))throw new IllegalStateException("Foreign mechanism fault");
    if(op.after!=null)op.after.check(op.after.placed.pool());else{var now=placement.capture();boolean landed=now.pool().slots().equals(op.pool.slots())&&same(now.savedFlags(),op.saved);op.before.check(landed?op.pool:op.before.placed.pool(),landed?op.saved:op.before.placed.savedFlags());}
   }catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}finally{mechanismOperation=null;}
  }
  private void mechanismDraw(Object payload,CdObjectPool pool,CdObjectState.Identity id){useMechanism(payload);familyDraw(pool,id,()->mechanismCurrent(mechanismOperation.call));mechanismCheckpoint();}
  private CdSolidObject.Player discSolidPlayer(CdSpinningDisc.PlayerCut p){var s=p.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
  private CdSolidObject.Globals discSolidGlobals(CdSpinningDisc.PlayerCut p){var e=p.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
  private void mechanismFamily(int whole,int id){
   requirePhysical(whole);if(mechanismOperation!=null)throw new IllegalStateException("Mechanism reentry");var e=frame.environment();var g=e.movement().gates();int x=camera().xWord(),time=g.timeZone(),direction=g.timeWarpDirection();
   try{switch(id){
    case 9->mechanismDisc.run(whole,new CdSpinningDisc.Globals(active.state().globals().ring().timeStopTimer(),x,time,direction));
    case 15->mechanismMovingSpring.run(whole,new CdMovingSpring.Globals(x,active.state().globals().ring().timeStopTimer()));
    case 40->mechanismBoard.run(whole,new CdSpringboard.Globals(time,direction,x),new CdSpringboard.PrimaryInputs(frame.state(),frame.environment()));
    case 49->mechanismHole.run(whole,new CdSonicHole.Globals(time,direction,x));
    case 54->{var p=floatInputs();mechanismWall.run(whole,new CdBreakableWall.Globals(time,direction,x),new CdBreakableWall.Inputs(p.player(),p.gates(),p.globals()));}
    default->throw new IllegalArgumentException("Foreign mechanism ID");
   }}finally{mechanismOperation=null;}
  }
  private void tunnelFamily(int whole,int id){
   requirePhysical(whole);if(tunnelOperation!=null)throw new IllegalStateException("Tunnel dispatch reentry");
   var g=frame.environment().movement().gates();int time=g.timeZone(),direction=g.timeWarpDirection(),x=camera().xWord();
   try{switch(id){
    case 0x34->tunnelTag.runOriginal(whole,new CdSpinTunnelTag.DespawnGlobals(time,direction,x));
    case 0x0D->{var p=floatInputs();tunnelDoor.run(whole,new CdTunnelDoor.Globals(time,direction,x),new CdTunnelDoor.SolidInputs(p.player(),p.gates(),p.globals()));}
    case 0x35->{var p=floatInputs();tunnelDoorVertical.run(whole,new CdTunnelDoorVertical.Globals(time,direction,x),new CdTunnelDoorVertical.SolidInputs(p.player(),p.gates(),p.globals()));}
    case 0x0B->tunnelDoorSplash.run(whole);
    case 0x07->tunnelPath.run(whole,new CdTunnelPath.Globals(time,direction,x));
    default->throw new IllegalArgumentException("Foreign original tunnel family");
   }}finally{tunnelOperation=null;}
  }
  private <T>T tunnelCall(Class<T> type){requirePhysical(physical);if(tunnelOperation==null||!type.isInstance(tunnelOperation.call))throw new IllegalStateException("Wrong original tunnel primitive");return type.cast(tunnelOperation.call);}
  private void tunnelDelete(CdObjectPool.Snapshot before){var staged=CdObjectPool.stagingCopy(before);staged.deleteObject(physical);tunnelOperation.pool=staged.capture();}
  private CdTunnelDoor.LiveSolid doorLive(){var p=floatInputs();return new CdTunnelDoor.LiveSolid(p.player(),p.gates(),p.globals());}
  private void doorInputs(CdTunnelDoor.PrefixSolidCut c){
   var live=doorLive();if(!live.primary().equals(c.expectedPrimary())||!live.gates().equals(c.expectedGates())||!live.globals().equals(c.expectedGlobals()))throw new IllegalArgumentException("Stale original Door primary/shared cut");
   if(c.secondary().isPresent()&&!same(c.secondary().orElseThrow(),row(1)))throw new IllegalArgumentException("Changed actual reserved secondary bytes");
  }
  private CdObjectPlacementRuntime.Snapshot doorCallCut(Object c){
   if(c instanceof CdTunnelDoor.SecondaryReadCall d)return d.cut();if(c instanceof CdTunnelDoor.DrawCall d)return d.cut();if(c instanceof CdTunnelDoor.FmCall d)return d.cut();
   var cut=c instanceof CdTunnelDoor.PrefixSolidCut d?d:((CdTunnelDoor.PrefixSolidCall)c).cut();var now=placement.capture();
   if(!cut.callCut().slots().equals(now.pool().slots()))throw new IllegalArgumentException("Changed original Door helper pool");return now;
  }
  private CdObjectPlacementRuntime.Snapshot pathCallCut(Object c){
   if(c instanceof CdTunnelPath.Publication d)return d.cut();if(c instanceof CdTunnelPath.PlayerReadCall d)return d.cut();if(c instanceof CdTunnelPath.WordReadCall d)return d.cut();
   var pool=c instanceof CdTunnelPath.PlayerCall d?d.actualCut():((CdTunnelPath.FmCall)c).actualCut();var now=placement.capture();
   if(!pool.slots().equals(now.pool().slots()))throw new IllegalArgumentException("Changed original Path callback pool");return now;
  }
  /** Stack-only operation fence; no original operation can be reconstructed from its values. */
  private final class TunnelOperation {
   final Object call;final FamilyCut before=new FamilyCut();FamilyCut after;boolean used,callbackRequired;
   CdObjectPool.Snapshot pool=before.placed.pool();CdAssetBytes saved=before.placed.savedFlags();
   TunnelOperation(Object call){this.call=call;}
  }
  private void beginTunnel(Object call,java.util.function.BooleanSupplier actual,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,int id){
   requirePhysical(physical);if(tunnelOperation!=null||physicalId!=id||!actual.getAsBoolean()||source!=placement.pool().state(physical))throw new IllegalStateException("Foreign/reentrant original tunnel operation");
   placement.validateRestore(cut);var op=new TunnelOperation(call);op.before.check(cut.pool(),cut.savedFlags());
   if(!cut.status().equals(op.before.placed.status()))throw new IllegalStateException("Changed original placement cursor");tunnelOperation=op;tunnelOperations++;
  }
  private void tunnelSaved(int index){if(index<2||index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw new IllegalArgumentException("Reached tunnel saved BYTE outside bank");var bytes=tunnelOperation.saved.copy();bytes[index]&=0x7F;tunnelOperation.saved=CdAssetBytes.copyOf(bytes);}
  private void useTunnel(Object call,java.util.function.BooleanSupplier actual){
   requirePhysical(physical);var op=tunnelOperation;if(op==null||op.call!=call||op.used||!actual.getAsBoolean())throw new IllegalStateException("No pending original tunnel callback");op.before.check(op.before.placed.pool());op.used=true;
  }
  private void tunnelReadCheckpoint(){var op=tunnelOperation;op.before.check(op.before.placed.pool());tunnelCheckpoint();}
  private void tunnelCheckpoint(){tunnelOperation.after=new FamilyCut();}
  private void tunnelDrawPrefix(){var op=tunnelOperation;var source=placement.pool().state(physical);op.pool=CdObjectPool.overlay(op.before.placed.pool(),List.of(source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,source.spriteFlags()&127)));}
  private void endTunnel(Object call,java.util.function.BooleanSupplier actual,boolean complete){
   var op=tunnelOperation;if(op==null||op.call!=call||!actual.getAsBoolean()||!complete)throw new IllegalStateException("Unreturned original tunnel operation");
   if(op.callbackRequired&&!op.used)throw new IllegalStateException("Original tunnel callback did not run");if(op.after!=null)op.after.check(op.after.placed.pool());else op.before.check(op.pool,op.saved);tunnelOperation=null;
  }
  private void failTunnel(Object call,java.util.function.BooleanSupplier actual,boolean landed,Throwable original){
   try{var op=tunnelOperation;if(op==null)return;if(op.call!=call||!actual.getAsBoolean())throw new IllegalStateException("Foreign failed tunnel operation");
    if(op.after!=null)op.after.check(op.after.placed.pool());else{
     var now=placement.capture();boolean prepared=now.pool().slots().equals(op.pool.slots())&&same(now.savedFlags(),op.saved);
     // A throwing primitive may have refused or landed exactly its prepared native prefix.
     op.before.check(landed||prepared?op.pool:op.before.placed.pool(),landed||prepared?op.saved:op.before.placed.savedFlags());
    }
   }catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}finally{tunnelOperation=null;}
  }
  private List<SonicCdGameModule.PastSound> solidFm(List<CdPlayerTouchResponse.FmSound> fm){
   var out=new ArrayList<SonicCdGameModule.PastSound>();for(var sound:fm)out.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(sound)));return List.copyOf(out);
  }
  private void familySolid(java.util.function.BooleanSupplier actual,CdObjectPool.Snapshot cut,
          CdSolidObject.Player player,CdSolidObject.Globals globals,CdSolidObject.Outcome outcome){
   familySolid(actual,cut,player,globals,outcome,this::floatTerrain);
  }
  private void familySolid(java.util.function.BooleanSupplier actual,CdObjectPool.Snapshot cut,
          CdSolidObject.Player player,CdSolidObject.Globals globals,CdSolidObject.Outcome outcome,Runnable terrainPreflight){
   var original=outcome instanceof CdSolidObject.NativeStopped stopped?stopped.stop().originalException():null;
   try{
    requirePhysical(physical);if(!actual.getAsBoolean())throw new IllegalStateException("No actual cached Solid producer");
    terrainPreflight.run();var before=new FamilyCut();placement.pool().validateRestore(cut);
    if(!cut.slots().equals(before.placed.pool().slots()))throw new IllegalArgumentException("Foreign complete Solid pool cut");
    CdObjectState own;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication fp;
    if(outcome instanceof CdSolidObject.Completed d){var r=d.completedResult();own=r.object();others=r.otherSlots();fm=r.fmRequests();fp=frame.prepareObjectSolidResult(player,globals,r);}
    else{var r=((CdSolidObject.NativeStopped)outcome).prefix();own=r.object();others=r.otherSlots();fm=r.fmRequests();fp=frame.prepareObjectSolidPrefix(player,globals,r);}
    if(!own.identity().equals(placement.pool().state(physical).identity()))throw new IllegalArgumentException("Foreign Solid a0");
    var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);var output=CdObjectPool.overlay(cut,images);
    if(!actual.getAsBoolean())throw new IllegalStateException("Expired native Solid call");before.publish(output,fp,null,solidFm(fm));
   }catch(RuntimeException|Error failure){if(original==null)throw failure;if(failure!=original)original.addSuppressed(failure);throw original;}
  }
  private void familyDraw(CdObjectPool pool,CdObjectState.Identity id,java.util.function.BooleanSupplier actual){
   requirePhysical(id.wholeIndex());if(pool!=placement.pool()||!actual.getAsBoolean())throw new IllegalStateException("No original family Draw producer");
   var before=new FamilyCut();var old=pool.state(physical);drawDynamic(pool,id);
   before.check(CdObjectPool.overlay(before.placed.pool(),List.of(old.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,old.spriteFlags()&127))));
  }
  private CdPlayerTouchResponse.PlayerGlobals hurtPlayer(){
   var e=frame.environment();var g=active.state().globals();var c=g.contact();return new CdPlayerTouchResponse.PlayerGlobals(c.timeWarp(),e.globals().invincible(),c.shield(),c.combineRing(),e.globals().debugCheat(),e.globals().debugMode(),e.movement().globals().scoreChain(),e.movement().gates().miniSonic());
  }
  private CdPlayerTouchResponse.WorldGlobals hurtWorld(){var g=active.state().globals();var r=g.ring();var s=g.score();return new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),s.score(),s.nextLifeScore(),s.updateHudScore());}
  private void rampPrimary(Cd3dRamp.PrimaryCall call){
   requirePhysical(physical);if(physicalId!=Cd3dRamp.OBJECT_ID||!ramp.isCurrentPrimaryCall(call))throw new IllegalStateException("No original Ramp MOVE.W producer");
   sourceCall(call.cut(),call.source(),Cd3dRamp.OBJECT_ID);var before=new FamilyCut();var s=frame.state();var e=frame.environment();var m=s.motion();
   var p=new Cd3dRamp.Player(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.yVelocity()&65535,m.flags(),m.animation(),m.playerControl());
   if(!p.equals(call.expectedPlayer()))throw new IllegalArgumentException("Stale original Ramp primary");
   var fp=frame.prepareObjectYVelocity(s,e,m.yVelocity()&65535,call.signedWord()&65535);
   if(!ramp.isCurrentPrimaryCall(call))throw new IllegalStateException("Expired Ramp store");before.publish(null,fp,null,List.of());
  }
  private void spikesRewind(CdSpikes.YRewindCall call){
   requirePhysical(physical);if(physicalId!=CdSpikes.OBJECT_ID||!spikes.isCurrentYRewindCall(call)||!call.sourceIdentity().equals(placement.pool().state(physical).identity()))throw new IllegalStateException("No original Spikes Y rewind");
   var before=new FamilyCut();before.publish(null,frame.prepareObjectPreHurtYRewind(call.expectedActor(),call.finalYBits()),null,List.of());
  }
  private void spikesHurt(CdSpikes.HurtCall call){
   requirePhysical(physical);if(physicalId!=CdSpikes.OBJECT_ID||!spikes.isCurrentHurtCall(call))throw new IllegalStateException("No original direct Hurt producer");
   sourceCall(call.callCut(),call.sourceA2(),CdSpikes.OBJECT_ID);
   if(!hurtPlayer().equals(call.expectedPlayer())||!hurtWorld().equals(call.expectedWorld()))throw new IllegalArgumentException("Stale complete Hurt globals");
   var before=new FamilyCut();var r=call.result();var g=active.state().globals();
   var values=CdPrimaryTouchValues.response(new CdPlayerBody.ObjectTouchResponse(r),g.ring());
   var ready=SonicCdR31aWorldHost.with(g,values.ring(),g.live(),values.contact(),values.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
   var pool=CdObjectPool.overlay(call.callCut(),r.changedSlots());var fp=frame.prepareObjectHurtResult(call.expectedActor(),call.expectedPlayer(),r);
   var fm=new ArrayList<SonicCdGameModule.PastSound>();for(var sound:r.calls()){
    if(sound instanceof CdPlayerTouchResponse.PlayFm value)fm.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(value.sound())));
    else if(sound instanceof CdPlayerTouchResponse.SubCpu value)fm.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(value.command())));
   }
   before.publish(pool,fp,ready,List.copyOf(fm));
  }
  // N1ecc Object Index:20 Collapse/21 Platform. No new scheduler, pool or capture partition.
  private void platformFamily(int whole,int id){
   requirePhysical(whole);var e=frame.environment();var gates=e.movement().gates();var cam=camera();var p=floatInputs();
   if(id==CdCollapsePlatform.OBJECT_ID)collapsePlatform.run(whole,
       new CdCollapsePlatform.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),cam.xWord()),
       new CdCollapsePlatform.Inputs(p.player(),p.gates(),p.globals()));
   else if(id==CdPlatform.OBJECT_ID)platform.run(whole,
       new CdPlatform.Globals(gates.zoneAct(),active.state().globals().ring().timeStopTimer(),cam.xWord(),cam.yWord(),gates.timeZone()),
       new CdPlatform.Inputs(p.player(),p.gates(),p.globals()));
   else throw new IllegalArgumentException("Foreign original platform handler");
  }
  private void collapsePlatformSolid(CdCollapsePlatform.SolidCall call){
   familySolid(()->physicalId==CdCollapsePlatform.OBJECT_ID&&collapsePlatform.isCurrentSolidCall(call),call.actualCut(),call.expectedPlayer(),call.expectedGlobals(),call.outcome());
  }
  private void platformSolid(CdPlatform.SolidCall call){
   familySolid(()->physicalId==CdPlatform.OBJECT_ID&&platform.isCurrentSolidCall(call),call.actualCut(),call.expectedPlayer(),call.expectedGlobals(),call.outcome());
  }
  private void collapsePlatformDraw(CdObjectPool pool,CdObjectState.Identity id){
   familyDraw(pool,id,()->physicalId==CdCollapsePlatform.OBJECT_ID&&collapsePlatform.isCurrentDrawCall(pool,id));
  }
  private void platformDraw(CdObjectPool pool,CdObjectState.Identity id){
   familyDraw(pool,id,()->physicalId==CdPlatform.OBJECT_ID&&platform.isCurrentDrawCall(pool,id));
  }
  private void collapsePlatformFm(CdCollapsePlatform.FmRequest call){
   requirePhysical(physical);if(physicalId!=CdCollapsePlatform.OBJECT_ID||!collapsePlatform.isCurrentFmRequest(call))throw new IllegalStateException("No current original Collapse FM");
   sourceCall(call.actualCut(),call.expectedSource(),CdCollapsePlatform.OBJECT_ID);var before=new FamilyCut();
   before.publish(null,null,null,List.of(new SonicCdGameModule.PastCollapsePlatformFm(call.soundId())));
  }
  private void collapsePlatformPublished(Object call){
   requirePhysical(physical);if(physicalId!=CdCollapsePlatform.OBJECT_ID||!collapsePlatform.isCurrentOperation(call))throw new IllegalStateException("Foreign Collapse publication acknowledgement");
  }
  private void platformAngle(CdPlatform.PrimaryAngleCall call){
   requirePhysical(physical);if(physicalId!=CdPlatform.OBJECT_ID||!platform.isCurrentPrimaryAngleCall(call))throw new IllegalStateException("No original Platform angle store");
   sourceCall(call.actualCut(),call.expectedSource(),CdPlatform.OBJECT_ID);floatTerrain();var before=new FamilyCut();
   var ready=frame.prepareObjectPrimaryAngle(call.expectedPrimaryAngle(),call.value());
   if(!platform.isCurrentPrimaryAngleCall(call))throw new IllegalStateException("Expired original Platform angle store");
   before.publish(null,ready,null,List.of());
  }
  private void solidHazard(int whole,int id){
   requirePhysical(whole);var e=frame.environment();var gates=e.movement().gates();var p=floatInputs();var cam=camera();
   switch(id){
    case Cd3dRamp.OBJECT_ID->{var m=frame.state().motion();ramp.run(whole,new Cd3dRamp.Player(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.yVelocity()&65535,m.flags(),m.animation(),m.playerControl()),new Cd3dRamp.Globals(gates.timeZone(),gates.timeWarpDirection(),cam.xWord()));}
    case CdBoulder.OBJECT_ID->{floatTerrain();boulder.run(whole,new CdBoulder.Globals(gates.timeZone(),gates.timeWarpDirection(),cam.xWord()),new CdBoulder.SolidInputs(p.player(),p.gates(),p.globals()));}
    case CdSpikes.OBJECT_ID->{floatTerrain();spikes.run(whole,new CdSpikes.Globals(gates.timeZone(),gates.timeWarpDirection(),cam.xWord()),new CdSpikes.Inputs(p.player(),p.gates(),p.globals(),hurtPlayer(),hurtWorld()));}
    case CdSpring.OBJECT_ID->{springOrdinal=0;if(springCall!=null)throw new IllegalStateException("Spring reentry");try{spring.run(whole,new CdSpring.Globals(cam.xWord()),new CdSpring.SolidInputs(p.player(),p.gates(),p.globals()));}finally{springCall=null;springBefore=null;springAfter=null;springPool=null;}}
    default->throw new IllegalArgumentException("Foreign solid/hazard dispatch");
   }
  }
  private void currentSpring(CdSpring.OriginalCall c){requirePhysical(physical);if(physicalId!=CdSpring.OBJECT_ID||springCall!=c||!spring.isCurrentCall(c))throw new IllegalStateException("No current original Spring occurrence");}
  private void beforeSpring(CdSpring.OriginalCall c){
   requirePhysical(physical);if(springCall!=null||!spring.isCurrentCall(c)||c.completed()||c.ordinal()!=springOrdinal+1||c.source()!=placement.pool().state(physical))throw new IllegalStateException("Foreign/repeated Spring occurrence");
   placement.validateRestore(c.cut());var now=placement.capture();if(!now.status().equals(c.cut().status())||!same(now.savedFlags(),c.cut().savedFlags())||!now.pool().slots().equals(c.cut().pool().slots()))throw new IllegalStateException("Changed complete Spring cut");
   springCall=c;springBefore=new FamilyCut();springAfter=null;springPrepared=false;springUsed=false;springPool=c.cut().pool();
   if(c.output()!=null)springPool=CdObjectPool.overlay(springPool,List.of(c.output()));
   else if(c.site()==CdSpring.Site.DELETE){var staged=CdObjectPool.stagingCopy(springPool);staged.deleteObject(physical);springPool=staged.capture();}
  }
  private void useSpring(CdSpring.OriginalCall c){currentSpring(c);if(!springPrepared||springUsed||c.completed())throw new IllegalStateException("Unprepared/repeated Spring callback");springBefore.check(springBefore.placed.pool());springUsed=true;}
  private void afterSpring(CdSpring.OriginalCall c){
   currentSpring(c);if(!springPrepared||!c.completed())throw new IllegalStateException("Unprepared Spring return");
   if(c.site()==CdSpring.Site.STORE||c.site()==CdSpring.Site.DELETE||c.site()==CdSpring.Site.ANIMATION)springBefore.check(springPool);
   else{if(!springUsed||springAfter==null)throw new IllegalStateException("Missing Spring native publication/read");springAfter.check(springAfter.placed.pool());}
   springOrdinal=c.ordinal();
  }
  private int springRead(CdSpring.OriginalCall c){
   useSpring(c);int value;
   if(c.site()==CdSpring.Site.CAMERA_X)value=camera().xWord();
   else if(c.site()==CdSpring.Site.PARENT_X||c.site()==CdSpring.Site.PARENT_Y){
    int address=c.source().word(CdObjectState.WordField.SPRING_PARENT);
    if(address<0xD000||address>=0xF000||((address-0xD000)&63)!=0)throw new UnsupportedOperationException("Original Spring parent WORD is outside object storage: "+address);
    int w=(address-0xD000)>>>6;boolean x=c.site()==CdSpring.Site.PARENT_X;
    if(w==0)value=x?frame.state().motion().centreX16_16()>>>16:frame.state().motion().centreY16_16()>>>16;
    else if(w<32)value=row(w).unsignedWord(x?8:12);else value=x?placement.pool().state(w).centreX():placement.pool().state(w).centreY();
   }else throw new IllegalStateException("Wrong Spring read site");
   springAfter=new FamilyCut();return value;
  }
  // Original shared ObjGameOver; these scopes only fence this existing physical dispatch.
  // The component alone owns slot writes/FindObjSlot/Draw ordering and full96 verification.
  private final class GameOverScope {
   final Object call,actor=frame.state(),environment=frame.environment(),display=frame.display(),art=frame.artState(),clock=frame.vIntClock();
   final boolean paused=frame.paused();SonicCdGameModule.R11LoadingState state=active.state();SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
   List<SonicCdGameModule.PastSound> expectedSounds=sounds;boolean prepared,used,after;
   GameOverScope(Object call){this.call=call;}
   void verify(){requirePhysical(physical);if(physicalId!=CdGameOver.OBJECT_ID||!gameOver.isCurrentCall(call)||active.state()!=state||active.prefix()!=prefix||sounds!=expectedSounds||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=art||frame.vIntClock()!=clock||frame.paused()!=paused)throw new IllegalStateException("Original R11 GameOver full caller cut changed");}
  }
  private GameOverScope gameOverScope(Object call){var op=gameOverScope;if(op==null||op.call!=call)throw new IllegalStateException("No original R11 GameOver operation");return op;}
  private void beforeGameOver(Object call){
   requirePhysical(physical);if(physicalId!=CdGameOver.OBJECT_ID||gameOverScope!=null||!gameOver.isCurrentCall(call))throw new IllegalStateException("No physical R11 GameOver source");
   CdObjectState source;
   if(call instanceof CdGameOver.Publication c)source=c.source();else if(call instanceof CdGameOver.ReadCall c)source=c.source();else if(call instanceof CdGameOver.ScalarCall c)source=c.source();else if(call instanceof CdGameOver.SoundCall c)source=c.source();else if(call instanceof CdGameOver.DrawCall c)source=c.source();else if(call instanceof CdGameOver.CompletionCall c)source=c.source();else throw new IllegalArgumentException("Unknown GameOver operation");
   if(source.identity().wholeIndex()!=physical)throw new IllegalArgumentException("Different GameOver physical source");
   gameOverScope=new GameOverScope(call);gameOverScope.verify();
  }
  private GameOverScope gameOverPrimitive(Class<?> type){var op=gameOverScope;if(op==null||!type.isInstance(op.call)||!op.prepared||op.used||!gameOver.isCurrentPrimitive(op.call))throw new IllegalStateException("Wrong/repeated R11 GameOver primitive");op.verify();op.used=true;return op;}
  private void gameOverScalar(CdGameOver.Scalar field,int value){
   var op=gameOverPrimitive(CdGameOver.ScalarCall.class);var call=(CdGameOver.ScalarCall)op.call;
   if(call.field()!=field||call.value()!=value)throw new IllegalArgumentException("Different R11 GameOver scalar");
   var level=op.prefix.main().level();var next=SonicCdR31aObjectPass.gameOverLevel(level,call);active.level(level,next);op.prefix=active.prefix();
  }
  private void gameOverSound(CdGameOver.SubCpuCommand command){var op=gameOverPrimitive(CdGameOver.SoundCall.class);if(((CdGameOver.SoundCall)op.call).command()!=command)throw new IllegalArgumentException("Different R11 GameOver command");var out=new ArrayList<>(sounds);out.add(new SonicCdGameModule.PastGameOverSubCpu(command));sounds=List.copyOf(out);op.expectedSounds=sounds;}
  private void gameOverDraw(CdObjectPool pool,CdObjectState.Identity slot){var op=gameOverPrimitive(CdGameOver.DrawCall.class);if(((CdGameOver.DrawCall)op.call).source().identity()!=slot)throw new IllegalArgumentException("Different R11 GameOver Draw source");drawDynamic(pool,slot);}
  private void gameOver(){gameOver.run(physical,new CdGameOver.GlobalsPort(){
   public CdGameOver.Globals read(){var op=gameOverPrimitive(CdGameOver.ReadCall.class);return new CdGameOver.Globals(op.state.globals().ring().lives(),op.prefix.main().level().powerup(),op.prefix.main().level().timeOver());}
   public void writePowerup(int value){gameOverScalar(CdGameOver.Scalar.POWERUP,value);}
   public void writeTimeOver(int value){gameOverScalar(CdGameOver.Scalar.TIME_OVER,value);}
  });}
  private void deathCamera(){
   requireCut(Cut.POST_OBJECT_CAMERA);if(!walkReturned)throw new IllegalStateException("No completed actual R11 physical walk");
   var before=active.state();var prefix=active.prefix();var actor=frame.state();var environment=frame.environment();var display=frame.display();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();var placed=placement.capture();var scroll=before.scroll();int cameraY=scroll.foreground().cameraYWord();
   var fp=frame.prepareLevelMainDeathCamera(frame.stageSource(),actor,environment,cameraY);
   var foreground=CdPrimaryLifecycle.freezeDestination(scroll.foreground(),cameraY);var ready=new SonicCdR31aLoadingVisit.Scroll(foreground,scroll.background(),scroll.copies(),scroll.foregroundXFractionWord(),scroll.topBoundWord(),scroll.waterDeformWord());
   frame.validatePreparedObjectSolidPublication(fp);var now=placement.capture();
   if(active.state()!=before||active.prefix()!=prefix||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags()))throw new IllegalStateException("R11 death camera changed an unowned completed-walk partition");
   frame.applyPreparedObjectSolidPublication(fp);active.scroll(scroll,ready);
  }
  private void scroll(){
   if(waterProgramme()){active.waterScroll();return;}
   var s=active.state().scroll();var e=frame.environment();var m=frame.state().motion();var f=s.foreground();

   var in=new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Input(new com.openggf.game.soniccd.scroll.CdR11aForegroundScroll.Actor(m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.flags(),m.groundVelocity(),m.animation(),m.playerControl()),e.movement().bounds(),s.topBoundWord(),e.globals().cameraXCentre(),e.movement().globals().cameraYCentre(),frame.stageSource().variant(),active.prefix().main().level().updateHudTime(),320,224);
   var result=programme.scroll(s,in,e.movement().gates().timeZone(),(short)m.xVelocity(),m.centreX16_16()>>>16,m.playerControl());
   frame.applyScrollFeedback(result.foreground().bounds(),result.foreground().cameraYCentre());active.scroll(s,result.scroll());
  }
  private void font(){
   var p=active.prefix();var g=active.state().globals();var e=frame.environment();var m=frame.state().motion();var inputs=new CdLevelHud.Inputs(new CdLevelHud.Globals(e.globals().debugCheat(),(g.pauseMusicBit().orElseThrow()|(frame.paused()?1:0))<<8,e.controls().controlLocked(),e.movement().gates().timeZone(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,e.movement().gates().zoneAct()),new CdLevelHud.DebugData(OptionalInt.of(placement.capture().savedFlags().unsignedByte(0)),active.state().layout(),g.debugBlockWord()));
   font.run(new CdOriginalWorldBindings.HudState<>(p.section(),g.ring(),p.main().level(),g.score(),g.live(),g.bonus()),inputs,this::retainFont);
  }
  private void retainFont(CdOriginalWorldBindings.HudState<CdR11aSectionArt.State> next){var p=active.prefix();var g=active.state().globals();var ready=SonicCdR31aWorldHost.with(g,next.ring(),next.liveTime(),g.contact(),next.score(),g.pauseMusicBit(),next.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());active.section(p.section(),next.section());active.level(p.main().level(),next.level());globals(ready);}
  private CdPlayerFrameRuntime.OriginalProgrammeUnavailable unavailable(String site){return new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("R41A "+site);}
  private void waterAt(Cut at,Runnable action){live();cut=at;action.run();live();}
  private Optional<CdObjectDrawQueue.CpuSat> selectedWaterCpu=Optional.empty();
  /** Standard PPZ VBlank only. Body/TimeWarp use the existing original primary path above. */
  private final CdPlayerFrameRuntime.OriginalProgrammeOwner standardOwner=new CdPlayerFrameRuntime.OriginalProgrammeOwner(){
   public long generation(){return serviceGeneration;}
   public void requireCurrent(CdPlayerFrameRuntime.OriginalProgrammeCall c){live();c.requireCurrent(frame);if(!standardPastProgramme()||c.source()!=pastSource.retainedPlayerSource()||frame.stageSource()!=programme.playerSource()||!standardSourcePair())throw new IllegalStateException("Foreign original standard source cycle");}
   public void palette(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.PUBLISH_LEVEL,()->active.publish(selectedWaterCpu));}
   public void levelArt(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.UPDATE_ANIM_TILES,active::powerup);waterAt(Cut.COPY_CAMERA_DRAW_LEVEL,active::copyDraw);waterAt(Cut.SERVICE_SLOW_PLC,active::slow);waterAt(Cut.UPDATE_HUD,R11.this::font);}
   public void afterFinish(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);}
   public void mainMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.MAIN_MUSIC,()->new Ports().mainMusic());}
   private UnsupportedOperationException body(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return new CdPlayerFrameRuntime.OriginalProgrammeUnavailable("Standard PPZ Body requires its actual original physical primary owner");}
   public int levelFrames(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public Optional<CdPlayerMovement.ObjectContext> objects(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void flipperAnimation(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerMovement.SlotPatch p){throw body(c);}
   public CdR4WaterAirPorts.Calls water(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void draw(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void sound(CdPlayerFrameRuntime.OriginalProgrammeCall c,int id){throw body(c);}
   public void chargeSound(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Sound s){throw body(c);}
   public void beforeCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){throw body(c);}
   public void afterCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){throw body(c);}
   public void restoreStageMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void collideObjects(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void timeWarp(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public CdPlayerBody.LevelContext level(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void storeLevel(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.LevelField f,int v){throw body(c);}
   public void spawnGameOver(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void restartSoundFlag(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void resetSavedObjectFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw body(c);}
   public void completed(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.Boundary b){throw body(c);}
  };
  private final CdPlayerFrameRuntime.OriginalWaterCurrentOwner waterOwner=new CdPlayerFrameRuntime.OriginalWaterCurrentOwner(){
   public long generation(){return serviceGeneration;}
   public void requireCurrent(CdPlayerFrameRuntime.OriginalProgrammeCall c){live();c.requireCurrent(frame);if(!waterProgramme()||c.source()!=ttSource.retainedPlayerSource()||frame.stageSource()!=programme.playerSource())throw new IllegalStateException("Foreign original TT source cycle");}
   public void palette(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);CdR4WaterEvents.update(waterCalls);active.selectWaterInitial();active.waterArm(1);waterAt(Cut.PUBLISH_LEVEL,()->active.publish(selectedWaterCpu));}
   public void levelArt(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.UPDATE_ANIM_TILES,active::animateWaterStage);}
   public void afterFinish(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);}
   public void mainMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.MAIN_MUSIC,()->new Ports().mainMusic());}
   public int levelFrames(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return frame.environment().globals().levelFrames();}
   public Optional<CdPlayerMovement.ObjectContext> objects(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);requirePhysical(0);var p=frame.state();return Optional.of(new CdPlayerMovement.ObjectContext(placement.pool().capture(),p.objectLinks().standObject(),p.header().slotId(),p.header().width()));}
   public void flipperAnimation(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerMovement.SlotPatch patch){requireCurrent(c);throw unavailable("reached Flipper support publication");}
   public CdR4WaterAirPorts.Calls water(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);requirePhysical(0);return waterCalls;}
   public void draw(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);requirePhysical(0);var s=c.state();var m=s.motion();var h=s.header();bodyDrawCall=c;try{R11.this.draw(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),m.spriteFlags(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.yRadius(),h.width(),h.priority()));}finally{bodyDrawCall=null;}}
   public void sound(CdPlayerFrameRuntime.OriginalProgrammeCall c,int nativeId){
    requireCurrent(c);requirePhysical(0);
    // Original MovePlayer balance/jump/skid requests reuse the existing typed FM ledger.
    // Unknown Body/Water sound IDs retain their reached owner boundary.
    for(var sound:CdPlayerMovement.Sound.values())if(sound.nativeId()==nativeId){
     R11.this.sound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.MovementSound(sound)));return;
    }
    throw unavailable("native Body sound $"+Integer.toHexString(nativeId));
   }
   public void chargeSound(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Sound s){requireCurrent(c);R11.this.sound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(s)));}
   public void beforeCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){requireCurrent(c);requirePhysical(0);}
   public void afterCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){requireCurrent(c);requirePhysical(0);}
   public void restoreStageMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);throw unavailable("native restore-stage-music request");}
   public void collideObjects(CdPlayerFrameRuntime.OriginalProgrammeCall c){
    requireCurrent(c);requirePhysical(0);ttObjects.requirePlacement(placement);
    if(!scanner.ownsProgrammeSource(ttObjects))throw new IllegalStateException("Foreign actual TT Touch source");
    var before=new FamilyCut();var actor=frame.state();var m=actor.motion();
    var selected=scanner.scan(new CdPlayerObjectTouch.PlayerCut(m.centreX16_16()>>>16,m.centreY16_16()>>>16,
            m.yRadius(),actor.animation().mapFrame(),actor.timers().hurt()),before.placed.pool());
    requireCurrent(c);before.check(before.placed.pool());
    if(selected==CdPlayerObjectTouch.NoTouch.INSTANCE)return;
    if(selected instanceof CdPlayerObjectTouch.Item item){
     // Native generic item is one ADDQ.B #2 or its hurt>=90 no-write return.
     // The actual complete private cut is checked before the callback-free pool adoption.
     if(item.changedSlot().isPresent())before.publish(CdObjectPool.overlay(before.placed.pool(),List.of(item.changedSlot().orElseThrow())),null,null,List.of());
     return;
    }
    var handler=(CdPlayerObjectTouch.HandlerBoundary)selected;var row=handler.selectedSlot().orElseThrow();
    // DATA/selection are now genuine. Never replay a pure response's effect list as native writes,
    // nor borrow PPZ SPECIAL rules. The next selected synchronous response owner remains explicit.
    throw unavailable("reached Touch "+handler.kind()+" native response publication, whole "+row.identity().wholeIndex()+" id $"+Integer.toHexString(row.id()));
   }
   public void timeWarp(CdPlayerFrameRuntime.OriginalProgrammeCall c){
    requireCurrent(c);requirePhysical(0);var e=frame.environment();var m=frame.state().motion();var g=e.movement().gates();var l=active.prefix().main().level();
    var r=CdPlayerTimeWarp.step(new CdPlayerTimeWarp.Input(g.zoneAct(),g.timeZone(),m.centreX16_16()>>>16,m.charge(),m.groundVelocity()&65535,g.timeWarpDirection(),e.globals().timeWarpTimer(),active.state().globals().contact().timeWarp()),new CdPlayerTimeWarp.Context(OptionalInt.of(l.spawnMode()),Optional.empty(),Optional.empty(),OptionalInt.of(l.levelRestart())));
    if(!r.stores().isEmpty()||!r.reservedPatches().isEmpty()||r.saveResult().isPresent()||r.restartResult().isPresent())throw unavailable("reached TimeWarp native publication");
   }
   public CdPlayerBody.LevelContext level(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return new CdPlayerBody.LevelContext(placement.pool().capture(),active.state().globals().ring(),active.prefix().main().level());}
   public void storeLevel(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.LevelField f,int v){requireCurrent(c);throw unavailable("reached primary Level store "+f);}
   public void spawnGameOver(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);throw unavailable("reached native GameOver full96 allocation");}
   public void restartSoundFlag(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);throw unavailable("reached native restart driver flag");}
   public void resetSavedObjectFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);placement.resetSavedObjectFlags();}
   public void completed(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.Boundary b){requireCurrent(c);requirePhysical(0);}
   public void copyCamerasAndFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.COPY_CAMERA_DRAW_LEVEL,active::copyWaterCameras);}
   public int splitByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return active.waterRead(CdR4WaterAirPorts.Value.SPLIT);}
   public int splitArmWord(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return active.waterArm();}
   public int pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return active.waterPending();}
   public void clearSplitArm(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);active.waterArm(0);}
   public void pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c,int v){requireCurrent(c);active.waterPending(v);}
   public int currentWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at){requireCurrent(c);return active.currentWaterLong(at);}
   public void selectWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at,int bits){requireCurrent(c);active.selectWaterLong(at,bits);}
   public void drawStage(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.COPY_CAMERA_DRAW_LEVEL,active::drawWaterStage);}
   public void slowPlc(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.SERVICE_SLOW_PLC,active::slow);}
   public void updateHud(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);waterAt(Cut.UPDATE_HUD,R11.this::font);}
   public void requireCurrentTail(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);currentTail(c);}
   public CdR4WaterAirPorts.Calls currentWater(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrentTail(c);return waterCalls;}
   public CdAssetBytes currentRecords(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrentTail(c);return ttObjects.waterCurrents();}
   public int currentSwitchByte(CdPlayerFrameRuntime.OriginalProgrammeCall c,int index){requireCurrentTail(c);throw unavailable("current switch BYTE "+index+" (actual captured switch bank absent)");}
   public void beforeCurrentStore(CdPlayerFrameRuntime.OriginalWaterStore p){
    p.requireCurrent(frame);requireCurrentTail(p.cycle());if(currentStore!=null||p.landed()||p.beforeState()!=frame.state()||p.beforeEnvironment()!=frame.environment())throw new IllegalStateException("Foreign/repeated native current store");currentStore=p;
   }
   public void afterCurrentStore(CdPlayerFrameRuntime.OriginalWaterStore p){
    p.requireCurrent(frame);currentTailLocation();
    // after also runs if before refused: no fabricated landed cut is adopted.
    if(currentStore==null){if(p.landed())throw new IllegalStateException("Unprepared landed current store");currentTail(p.cycle());return;}
    if(currentStore!=p)throw new IllegalStateException("Foreign current-store unwind");
    try{
     if(p.landed()){currentTailCut.check(p.completedState(),p.completedEnvironment());currentTailCut.actor=p.completedState();currentTailCut.environment=p.completedEnvironment();}
     else currentTailCut.check(p.beforeState(),p.beforeEnvironment());
    }finally{currentStore=null;}
   }
  };
  /** Stack-only observation of the actual common-tail cut. The Frame's issued receipt alone
   * permits its one landed State/Environment change; every external partition must remain exact. */
  private final class CurrentTailCut {
   final SonicCdGameModule.R11LoadingState moduleState=active.state();final SonicCdGameModule.R11LoadingPrefix prefix=active.prefix();
   CdPlayerBody.State actor=frame.state();CdPlayerBody.Environment environment=frame.environment();
   final Object display=frame.display(),artImage=frame.artState(),clock=frame.vIntClock();final boolean paused=frame.paused();
   final CdObjectPlacementRuntime.Snapshot placed=placement.capture();final List<SonicCdGameModule.PastSound> oldSounds=sounds;
   final CdObjectDrawQueue originalQueue=queue;final Optional<CdObjectDrawQueue.CpuSat> originalCpu=cpu;
   void check(CdPlayerBody.State expectedActor,CdPlayerBody.Environment expectedEnvironment){
    currentTailLocation();placement.validateRestore(placed);var now=placement.capture();
    if(active.state()!=moduleState||active.prefix()!=prefix||frame.state()!=expectedActor||frame.environment()!=expectedEnvironment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||sounds!=oldSounds||queue!=originalQueue||cpu!=originalCpu||!now.status().equals(placed.status())||!same(now.savedFlags(),placed.savedFlags())||!now.pool().slots().equals(placed.pool().slots()))throw new IllegalStateException("Changed complete native current-tail cut");
   }
  }
  private CurrentTailCut currentTailCut;private CdPlayerFrameRuntime.OriginalWaterStore currentStore;
  private void currentTailLocation(){
   requireCut(Cut.POST_OBJECT_CAMERA);live();
   if(!waterProgramme()||!walkReturned||physical!=-1||drawing||draining||queue==null)throw new IllegalStateException("No returned actual TT physical-walk/current tail");
   ttObjects.requirePlacement(placement);
  }
  private void currentTail(CdPlayerFrameRuntime.OriginalProgrammeCall c){
   c.requireCurrent(frame);currentTailLocation();if(currentTailCut==null)throw new IllegalStateException("No original CheckWaterCurrents invocation");currentTailCut.check(currentTailCut.actor,currentTailCut.environment);
  }
  private void currents(){
   currentTailLocation();if(currentTailCut!=null||currentStore!=null)throw new IllegalStateException("Current-tail reentry");currentTailCut=new CurrentTailCut();
   try{frame.runOriginalProgrammeWaterCurrents(waterOwner);}finally{currentTailCut=null;currentStore=null;}
  }
  // Stack-only connected first-water family authority. Native scalar/slot values are not leases.
   private Object ttAirInvocation;private Integer ttDrownWrite;
   // Stack-only source-issued FM authority. No extra persistent/capture or replay owner.
   private CdR4WaterAirPorts.Slot ttAirFmSlot;private CdR4WaterAirPorts.BubbleData ttAirFmData;
   private CdR4PlayerBubbles.FmCall ttAirFmCall;private FamilyCut ttAirFmBefore;
   private List<SonicCdGameModule.PastSound> ttAirFmExpected;private long ttAirFmOrdinal;
   private void requireAirFm(CdR4PlayerBubbles.FmCall c){
    requirePhysical(physical);if(c==null)throw new IllegalStateException("No original PlayerBubbles FM occurrence");
    c.requireCurrent(ttAirFmOwner);
    requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.SOUND,null,null,c.sound().nativeId());
    if(physicalId!=0x21||ttAirInvocation==null||ttAirFmSlot==null||ttAirFmData==null
       ||c.slot()!=ttAirFmSlot||c.calls()!=waterCalls||c.data()!=ttAirFmData
       ||ttAirFmData!=ttObjects.bubbleSpawner()||frame.stageSource()!=ttSource.retainedPlayerSource()
       ||frame.stageSource()!=programme.playerSource())throw new IllegalStateException("Foreign actual TT PlayerBubbles source/Frame/FM issuer");
   }
   private final CdR4PlayerBubbles.FmOwner ttAirFmOwner=new CdR4PlayerBubbles.FmOwner(){
    public long generation(){return serviceGeneration;}
    public void before(CdR4PlayerBubbles.FmCall c){
     requireAirFm(c);if(ttAirFmCall!=null||c.consumed()||c.ordinal()!=ttAirFmOrdinal+1)throw new IllegalStateException("Repeated original TT air FM occurrence");
     var before=new FamilyCut();before.check(before.placed.pool());ttAirFmCall=c;ttAirFmBefore=before;ttAirFmExpected=before.oldSounds;ttAirFmOrdinal=c.ordinal();
    }
    public void sound(CdR4PlayerBubbles.FmCall c){
     requireAirFm(c);if(ttAirFmCall!=c||ttAirFmBefore==null)throw new IllegalStateException("No current original TT air FM callback");
     ttAirFmBefore.check(ttAirFmBefore.placed.pool());var request=active.prepareWaterAirFm(c);
     ttAirFmBefore.check(ttAirFmBefore.placed.pool());c.consume(this);
     ttAirFmBefore.publish(null,null,null,List.of(request));ttAirFmExpected=sounds;
    }
    public void after(CdR4PlayerBubbles.FmCall c){
     requireAirFm(c);if(ttAirFmCall==null)return;
     if(ttAirFmCall!=c)throw new IllegalStateException("Different original TT air FM completion");
     try{ttAirFmBefore.checkSoundOnly(ttAirFmExpected);}finally{ttAirFmCall=null;ttAirFmBefore=null;ttAirFmExpected=null;}
    }
   };
  private boolean bodyWater(){return physical==0&&physicalId==1;}
  private void requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind kind,Object target,Object field,int value){
   requirePhysical(physical);if(!waterProgramme())throw new IllegalStateException("Foreign original water action");ttObjects.requirePlacement(placement);
   if(bodyWater()){frame.requireOriginalWaterPort(kind,target,field,value);return;}
   if(ttAirInvocation==null||(physicalId!=0x21&&physicalId!=0x0B)||(physical<32&&physical!=7))throw new IllegalStateException("No actual connected TT air/splash action");
  }
  private void requireDrownTimerWrite(int value){
   requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.VALUE_WRITE,null,CdR4WaterAirPorts.Value.DROWN_TIMER,value);
   if(ttDrownWrite==null||ttDrownWrite!=value)throw new IllegalStateException("No privately issued actual TT drown WORD store");
  }
  private void ttAir(int whole,int id){
   requirePhysical(whole);if(ttAirInvocation!=null||!waterProgramme()||physicalId!=id||(whole<32&&whole!=7))throw new IllegalStateException("Foreign TT air/splash dispatch");
   ttAirInvocation=new Object();
   try{
    if(id==0x21){var data=ttObjects.bubbleSpawner();if(data.mappingsBits()!=ttObjects.waterEntryMappings(CdR4WaterAirPorts.Animation.PLAYER_BUBBLES))throw new IllegalStateException("Changed same-reader bubbles map");ttAirFmSlot=whole==7?new WaterReservedSlot(false):new WaterSlot(whole);ttAirFmData=data;ttAirFmOrdinal=0;CdR4PlayerBubbles.runOriginal(ttAirFmSlot,waterCalls,data,ttAirFmOwner);}
    else if(id==0x0B&&whole>=32)CdR4Splash.run(new WaterSlot(whole),waterCalls,ttObjects.waterEntryMappings(CdR4WaterAirPorts.Animation.SPLASH));
    else throw unavailable("unowned connected water-family physical row");
   }finally{ttAirFmCall=null;ttAirFmBefore=null;ttAirFmExpected=null;ttAirFmSlot=null;ttAirFmData=null;ttAirFmOrdinal=0;ttAirInvocation=null;ttDrownWrite=null;}
  }
  /** The one native reserved7 alias borrows the actual existing31-row bank, never a synthetic slot. */
  private final class WaterReservedSlot implements CdR4WaterAirPorts.Slot {
   private final Object airIssuer=ttAirInvocation;private final CdPlayerFrameRuntime.OriginalProgrammeCall bodyIssuer;
   private final int issuerWhole=physical,issuerId=physicalId;
   WaterReservedSlot(boolean body){bodyIssuer=body?frame.originalWaterPortCall():null;current();}
   private void current(){
    requirePhysical(issuerWhole);ttObjects.requirePlacement(placement);
    if(!waterProgramme()||physicalId!=issuerId)throw new IllegalStateException("Changed reserved Bubbles source");
    if(bodyIssuer!=null){if(!bodyWater()||frame.originalWaterPortCall()!=bodyIssuer)throw new IllegalStateException("Retired primary reserved Bubbles port");}
    else if(airIssuer==null||airIssuer!=ttAirInvocation||issuerId!=0x21||(issuerWhole!=7&&issuerWhole<32))throw new IllegalStateException("No actual reserved7 Bubbles alias");
   }
   private void port(CdPlayerFrameRuntime.OriginalWaterPortKind kind,Object field,int value){current();if(bodyIssuer!=null)frame.requireOriginalWaterPort(kind,this,field,value);}
   private CdAssetBytes bytes(){current();return row(7);}
   private int read(int at,int count){var b=bytes();Objects.checkFromIndexSize(at,count,64);int v=0;for(int i=0;i<count;i++)v=(v<<8)|b.unsignedByte(at+i);return v;}
   private void write(int at,int count,int value){current();var before=bytes();Objects.checkFromIndexSize(at,count,64);var out=before.copy();for(int i=0;i<count;i++)out[at+i]=(byte)(value>>>(8*(count-1-i)));active.reserved(7,before,CdAssetBytes.copyOf(out));}
   void delete(){current();active.reserved(7,bytes(),CdAssetBytes.copyOf(new byte[64]));}
   public int read(CdR4WaterAirPorts.B f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);return read(f.offset,1);}
   public int read(CdR4WaterAirPorts.W f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);return read(f.offset,2);}
   public int read(CdR4WaterAirPorts.L f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);return read(f.offset,4);}
   public void write(CdR4WaterAirPorts.B f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);CdR4WaterAirPorts.checked(v,1,f.name());write(f.offset,1,v);}
   public void write(CdR4WaterAirPorts.W f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);CdR4WaterAirPorts.checked(v,2,f.name());write(f.offset,2,v);}
   public void write(CdR4WaterAirPorts.L f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);write(f.offset,4,v);}
  }
  private void ttAirAnimate(WaterSlot slot,CdObjectAnimation.AnimationTable table){
   final CdObjectAnimation.Prefix[] producer=new CdObjectAnimation.Prefix[1];
   producer[0]=new CdObjectAnimation.Prefix(placement.pool(),slot.whole,new CdObjectAnimation.Guard(){
    private FamilyCut before;private CdObjectAnimation.Operation current;
    public void before(CdObjectAnimation.Operation op){slot.current();if(!producer[0].isCurrent(op)||current!=null||op.wholeIndex()!=slot.whole)throw new IllegalStateException("Foreign air animation prefix");before=new FamilyCut();before.check(before.placed.pool());current=op;}
    public void after(CdObjectAnimation.Operation op){if(current==null)return;try{slot.current();if(current!=op||!producer[0].isCurrent(op))throw new IllegalStateException("Retired air animation prefix");var expected=before.placed.pool();if(op.completed()&&op.kind()==CdObjectAnimation.Kind.STORE)expected=CdObjectPool.overlay(expected,List.of(expected.slots().get(slot.whole-32).moveByte(op.field(),op.value())));before.check(expected);}finally{before=null;current=null;}}
   });producer[0].step(table);
  }
  private final CdR4WaterAirPorts.Calls waterCalls=new CdR4WaterAirPorts.Calls(){
   private void current(){live();if(!waterProgramme())throw new IllegalStateException("Foreign water programme");ttObjects.requirePlacement(placement);}
   public int read(CdR4WaterAirPorts.Value f){current();if(bodyWater())frame.requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.VALUE_READ,null,f,0);return active.waterRead(f);}
   public void write(CdR4WaterAirPorts.Value f,int v){
    current();if(f!=CdR4WaterAirPorts.Value.DROWN_TIMER){active.waterWrite(f,v);return;}
    requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.VALUE_WRITE,null,f,v);if(ttDrownWrite!=null)throw new IllegalStateException("Recursive drown WORD store");ttDrownWrite=v;
    try{active.waterWrite(f,v);}finally{ttDrownWrite=null;}
   }
   public CdR4WaterAirPorts.Slot player(){current();return waterPrimary;}
   public CdPlayerMath.SineCosine sineCosine(int angle){current();return new CdPlayerMath(frame.stageSource().player()).calcSine(angle);}
   public CdR4WaterAirPorts.Slot reservedBubbles(){current();requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.RESERVED,null,null,0);return new WaterReservedSlot(bodyWater());}
   public Optional<CdR4WaterAirPorts.Slot> spawn(){current();requirePhysical(physical);if(physicalId!=TT_BUBBLE_SPAWNER_ID)requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.SPAWN,null,null,0);var cut=new FamilyCut();var free=placement.pool().findObjSlot();cut.check(cut.placed.pool());return free.isPresent()?Optional.of(new WaterSlot(free.getAsInt(),bodyWater())):Optional.empty();}
   public int random(){current();requirePhysical(physical);if(physicalId!=TT_BUBBLE_SPAWNER_ID)requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.RANDOM,null,null,0);return active.waterRandom();}
   public void sound(int id){current();requireWaterAction(CdPlayerFrameRuntime.OriginalWaterPortKind.SOUND,null,null,id);if(physicalId==0x0B&&(id==0xA1||id==0xA2)){R11.this.sound(new SonicCdGameModule.PastTunnelDoorSplashFm(id==0xA1?CdTunnelDoorSplash.FmSound.FM_A1:CdTunnelDoorSplash.FmSound.FM_A2));return;}throw unavailable("reached Water/Air FM $"+Integer.toHexString(id));}
   public void music(int id){current();throw unavailable("reached Water/Air music $"+Integer.toHexString(id));}
   public void setPlayerGround(){current();throw unavailable("reached Water/Air PlayerSetGround publication");}
   public void move(CdR4WaterAirPorts.Slot value){current();if(bodyWater())frame.requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.MOVE,value,null,0);if(!(value instanceof WaterSlot slot))throw unavailable("reached foreign water MoveObject");slot.move();}
   public void delete(CdR4WaterAirPorts.Slot value){current();if(bodyWater())frame.requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.DELETE,value,null,0);if(value instanceof WaterReservedSlot reserved){reserved.delete();return;}if(!(value instanceof WaterSlot slot))throw unavailable("reached foreign water DeleteObject");slot.delete();}
   public void animate(CdR4WaterAirPorts.Slot value,CdR4WaterAirPorts.Animation animation){current();if(bodyWater())frame.requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.ANIMATE,value,animation,0);if(!(value instanceof WaterSlot slot))throw unavailable("reached foreign water animation");slot.current();var table=switch(animation){case WATER_SURFACE->ttObjects.waterSurface().animation();case BUBBLE_SPAWNER->ttObjects.bubbleSpawnerAnimation();case PLAYER_BUBBLES,SPLASH->ttObjects.waterEntryAnimation(animation);};if(animation==CdR4WaterAirPorts.Animation.PLAYER_BUBBLES||animation==CdR4WaterAirPorts.Animation.SPLASH){ttAirAnimate(slot,table);return;}new CdObjectAnimation.Prefix(placement.pool(),slot.whole,new CdObjectAnimation.Guard(){public void before(CdObjectAnimation.Operation op){slot.current();}public void after(CdObjectAnimation.Operation op){slot.current();}}).step(table);}
   public void draw(CdR4WaterAirPorts.Slot value){current();if(bodyWater())frame.requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.DRAW,value,null,0);if(!(value instanceof WaterSlot slot))throw unavailable("reached non-WaterSurface Draw owner");slot.current();drawDynamic(placement.pool(),placement.pool().state(slot.whole).identity());}
  };
  private final CdR4WaterAirPorts.Slot waterPrimary=new CdR4WaterAirPorts.Slot(){
   private CdPlayerMotionState motion(){live();return frame.state().motion();}
   public int read(CdR4WaterAirPorts.B f){var m=motion();return switch(f){case ID->frame.state().header().slotId();case ROUTINE->m.routine();case ANIM->m.animation();case CONTROL->m.playerControl();case FLAGS->m.flags();case CHARGE->m.playerJump();default->throw unavailable("reached current primary BYTE "+f);};}
   public int read(CdR4WaterAirPorts.W f){var m=motion();return switch(f){case X->m.centreX16_16()>>>16;case Y->m.centreY16_16()>>>16;case VX->m.xVelocity()&65535;case VY->m.yVelocity()&65535;case GROUND_SPEED->m.groundVelocity()&65535;default->throw unavailable("reached current primary WORD "+f);};}
   public int read(CdR4WaterAirPorts.L f){throw unavailable("reached current primary LONG "+f);}
   public void write(CdR4WaterAirPorts.B f,int v){throw unavailable("reached post-object current primary BYTE store "+f);}
   public void write(CdR4WaterAirPorts.W f,int v){throw unavailable("reached post-object current primary WORD store "+f);}
   public void write(CdR4WaterAirPorts.L f,int v){throw unavailable("reached post-object current primary LONG store "+f);}
  };
  /** Only the actual physical WaterSurface occurrence issues this adapter. All64/full96 remain the existing pool. */
  private final class WaterSlot implements CdR4WaterAirPorts.Slot {
   private final int whole,issuerWhole=physical,issuerId=physicalId;private final Object airIssuer=ttAirInvocation;
   private final CdPlayerFrameRuntime.OriginalProgrammeCall bodyIssuer;
   WaterSlot(int w){this(w,false);}WaterSlot(int w,boolean body){whole=w;bodyIssuer=body?frame.originalWaterPortCall():null;current();}
   void current(){
    requirePhysical(issuerWhole);if(!waterProgramme()||physicalId!=issuerId||whole<32||whole>=128)throw new IllegalStateException("No original R41A water-family occurrence");ttObjects.requirePlacement(placement);
    if(bodyIssuer!=null){if(!bodyWater()||frame.originalWaterPortCall()!=bodyIssuer)throw new IllegalStateException("Retired primary splash allocation");}
    else if(issuerId==0x21||issuerId==0x0B){if(airIssuer==null||airIssuer!=ttAirInvocation)throw new IllegalStateException("Retired connected TT air/splash occurrence");}
    else if(issuerId!=TT_WATER_SURFACE_ID&&issuerId!=TT_BUBBLE_SPAWNER_ID)throw new IllegalStateException("Foreign original water family");
   }
   private void port(CdPlayerFrameRuntime.OriginalWaterPortKind kind,Object f,int value){current();if(bodyIssuer!=null)frame.requireOriginalWaterPort(kind,this,f,value);}
   private CdObjectState state(){current();return placement.pool().state(whole);}
   private void publish(CdObjectState next){current();var cut=new FamilyCut();var expected=CdObjectPool.overlay(cut.placed.pool(),List.of(next));cut.check(cut.placed.pool());placement.pool().write(next);cut.check(expected);}
   void delete(){current();var cut=new FamilyCut();var comparison=CdObjectPool.stagingCopy(cut.placed.pool());comparison.deleteObject(whole);var expected=comparison.capture();cut.check(cut.placed.pool());placement.pool().deleteObject(whole);cut.check(expected);}
   void move(){current();var old=state();int link=old.unsignedByte(CdObjectState.ByteField.STAND_LINK_3D);Optional<CdPlayerMovement.RawObjectSupport> support=Optional.empty();if((old.flags()&8)!=0){if(link==0)support=Optional.of(new CdPlayerMovement.RawObjectSupport(0,frame.state().header().slotId(),frame.state().motion().flags()));else if(link>=32&&link<128){var stood=placement.pool().state(link);support=Optional.of(new CdPlayerMovement.RawObjectSupport(link,stood.id(),stood.flags()));}}
    var out=CdPlayerMovement.moveObject(new CdPlayerMovement.RawObjectMotion(old.longBits(CdObjectState.LongField.X_POSITION),old.longBits(CdObjectState.LongField.Y_POSITION),old.signedWord(CdObjectState.WordField.X_VELOCITY),old.signedWord(CdObjectState.WordField.Y_VELOCITY),old.flags(),old.unsignedByte(CdObjectState.ByteField.RAW_CONTROL_2C),link),support);publish(old.moveLong(CdObjectState.LongField.X_POSITION,out.centreX16_16()));publish(state().moveLong(CdObjectState.LongField.Y_POSITION,out.centreY16_16()));
   }

   public int read(CdR4WaterAirPorts.B f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);return state().copyBytes()[f.offset]&255;}
   public int read(CdR4WaterAirPorts.W f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);var b=state().copyBytes();return(b[f.offset]&255)<<8|(b[f.offset+1]&255);}
   public int read(CdR4WaterAirPorts.L f){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_READ,f,0);var b=state().copyBytes();return(b[f.offset]&255)<<24|(b[f.offset+1]&255)<<16|(b[f.offset+2]&255)<<8|(b[f.offset+3]&255);}
   public void write(CdR4WaterAirPorts.B f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);CdR4WaterAirPorts.checked(v,1,f.name());var field=Arrays.stream(CdObjectState.ByteField.values()).filter(k->k.byteOffset()==f.offset).findFirst().orElseThrow(()->unavailable("WaterSurface BYTE alias "+f));var next=state().moveByte(field,v);publish(next);}
   public void write(CdR4WaterAirPorts.W f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);CdR4WaterAirPorts.checked(v,2,f.name());var field=Arrays.stream(CdObjectState.WordField.values()).filter(k->k.byteOffset()==f.offset).findFirst().orElseThrow(()->unavailable("WaterSurface WORD alias "+f));var next=state().moveWord(field,v);publish(next);}
   public void write(CdR4WaterAirPorts.L f,int v){port(CdPlayerFrameRuntime.OriginalWaterPortKind.SLOT_WRITE,f,v);var field=Arrays.stream(CdObjectState.LongField.values()).filter(k->k.byteOffset()==f.offset).findFirst().orElseThrow(()->unavailable("WaterSurface LONG alias "+f));var next=state().moveLong(field,v);publish(next);}
  }
  // Source-issued shared R4 primitives; outcomes/records never grant World authority.
  private CdR4ObjectScope ttEcology;
  private final class TtEcologyPrimitive {
   final CdR4ObjectPorts.Operation call;final FamilyCut before=new FamilyCut();FamilyCut landed;
   TtEcologyPrimitive(CdR4ObjectPorts.Operation c){call=c;}
  }
  private TtEcologyPrimitive ttEcologyPrimitive;
  // Invocation-local receipt for SpawnObjectAfter's ID-only allocation, never saved authority.
  private boolean ttFallInit;private int ttFallInitChild=-1;
  private void ttEcologyLocation(CdR4ObjectPorts.Operation c){
   requirePhysical(physical);if(!waterProgramme()||ttEcology==null||!ttEcology.isCurrent(c)||c.actor()!=physical
     ||(physicalId!=0x22&&physicalId!=0x2B&&physicalId!=0x2F&&physicalId!=0x32))throw new IllegalStateException("No actual original TT ecology primitive");
   ttObjects.requirePlacement(placement);
   if(c.target()!=physical){
    if(physicalId!=0x32||c.target()<32||c.target()>=128
       ||(c.kind()!=CdR4ObjectPorts.Kind.STORE&&c.kind()!=CdR4ObjectPorts.Kind.DELETE))
     throw new IllegalStateException("Foreign original TT ecology child primitive");
    if(ttFallInit){
     if(c.kind()!=CdR4ObjectPorts.Kind.STORE||(ttFallInitChild>=0&&c.target()!=ttFallInitChild))
      throw new IllegalStateException("Foreign FallSpikes Init child");
    }else if(placement.pool().state(physical).word(CdObjectState.WordField.BOSS_SAVED_X)
        !=(placement.pool().state(c.target()).identity().nativeAddress24()&65535))
     throw new IllegalStateException("Changed FallSpikes raw child link");
   }
  }
  private TtEcologyPrimitive ttEcologyUse(CdR4ObjectPorts.Kind kind){
   var p=ttEcologyPrimitive;if(p==null||p.call.kind()!=kind)throw new IllegalStateException("Wrong original TT ecology callback");
   ttEcologyLocation(p.call);var expected=p.landed==null?p.before:p.landed;expected.check(expected.placed.pool());return p;
  }
  /** Called only after an exact primitive/owner publication was independently validated. */
  private void ttEcologyLanded(){
   var p=ttEcologyPrimitive;if(p==null)throw new IllegalStateException("No TT ecology landed issuer");ttEcologyLocation(p.call);p.landed=new FamilyCut();
  }
  private CdR4ObjectPorts.Guard ttEcologyGuard(){return new CdR4ObjectPorts.Guard(){
   public void before(CdR4ObjectScope producer,CdR4ObjectPorts.Operation c){
    if(producer!=ttEcology||ttEcologyPrimitive!=null)throw new IllegalStateException("Foreign/reentrant original TT ecology scope");ttEcologyLocation(c);
    placement.validateRestore(c.cut());var p=new TtEcologyPrimitive(c);p.before.check(c.cut().pool(),c.cut().savedFlags());
    if(c.kind()==CdR4ObjectPorts.Kind.STORE&&(c.output()==null||!c.output().identity().equals(placement.pool().state(c.target()).identity())))throw new IllegalArgumentException("Foreign TT ecology store target");
    boolean firstChild=physicalId==0x32&&ttFallInit&&c.target()!=physical&&ttFallInitChild<0;
    if(firstChild){
     var current=placement.pool().state(c.target());
     if(c.kind()!=CdR4ObjectPorts.Kind.STORE||current.id()!=0
        ||placement.pool().findNextObjSlotAfterWholeIndex(physical).orElse(-1)!=c.target()
        ||!c.output().equals(current.moveByte(CdObjectState.ByteField.ID,0x32)))
      throw new IllegalArgumentException("Foreign FallSpikes after-current ID publication");
    }
    // Last preflight: native SpawnObjectAfter neither clears the row nor grants future authority.
    if(firstChild)ttFallInitChild=c.target();ttEcologyPrimitive=p;
   }
   public void after(CdR4ObjectScope producer,CdR4ObjectPorts.Operation c){
    var p=ttEcologyPrimitive;if(p==null)return;
    if(producer!=ttEcology||p.call!=c)throw new IllegalStateException("Foreign original TT ecology completion");
    try{
     ttEcologyLocation(c);
     if(c.kind()==CdR4ObjectPorts.Kind.STORE)p.before.check(CdObjectPool.overlay(c.cut().pool(),List.of(c.output())));
     else if(c.kind()==CdR4ObjectPorts.Kind.DELETE){var planned=CdObjectPool.stagingCopy(c.cut().pool());planned.deleteObject(c.target());p.before.check(planned.capture());}
     else {var expected=p.landed==null?p.before:p.landed;expected.check(expected.placed.pool());}
    }finally{ttEcologyPrimitive=null;}
   }
  };}
  private final CdR4ObjectPorts ttEcologyPorts=new CdR4ObjectPorts(){
   public int read(Read field){ttEcologyUse(Kind.READ);return switch(field){
    case GOOD_FUTURE->active.prefix().goodFuture();case TIME_ZONE->frame.environment().movement().gates().timeZone();
    case PROJECTOR_DESTROYED->active.state().globals().projectorByte().orElseThrow(()->unavailable("reached original TT projector BYTE without genuine producer"));
    case ACT->frame.environment().movement().gates().zoneAct()&255;
    case PLAYER_X->{if(physicalId!=0x32)throw unavailable("reached original TT ecology scalar "+field);yield frame.state().motion().centreX16_16()>>>16;}
    case PLAYER_Y->{if(physicalId!=0x32)throw unavailable("reached original TT ecology scalar "+field);yield frame.state().motion().centreY16_16()>>>16;}
    case STATIC_WATER_Y->{if(physicalId!=0x32)throw unavailable("reached original TT ecology scalar "+field);yield active.waterRead(CdR4WaterAirPorts.Value.STATIC_WATER_Y);}
    default->throw unavailable("reached original TT ecology scalar "+field);
   };}
   public int pointer(Data field){ttEcologyUse(Kind.POINTER);return ttObjects.ecologyPointer(field);}
   public int animalTile(int offset){ttEcologyUse(Kind.ANIMAL_TILE);return ttObjects.ecologyAnimalTile(offset);}
   public CdPlayerMath.SineCosine sine(int phase){ttEcologyUse(Kind.SINE);return new CdPlayerMath(frame.stageSource().player()).calcSine(phase);}
   public void animate(int whole,Data table,int pointer){
    ttEcologyUse(Kind.ANIMATE);if(whole!=physical||pointer!=ttObjects.ecologyPointer(table))throw new IllegalArgumentException("Foreign original TT ecology animation");
    final CdObjectAnimation.Prefix[] issuer=new CdObjectAnimation.Prefix[1];
    issuer[0]=new CdObjectAnimation.Prefix(placement.pool(),whole,new CdObjectAnimation.Guard(){
     private FamilyCut before;private CdObjectAnimation.Operation current;
     public void before(CdObjectAnimation.Operation c){
      ttEcologyUse(Kind.ANIMATE);if(current!=null||!issuer[0].isCurrent(c)||c.wholeIndex()!=physical)throw new IllegalStateException("Foreign TT ecology animation primitive");
      before=new FamilyCut();before.check(before.placed.pool());current=c;
     }
     public void after(CdObjectAnimation.Operation c){
      if(current==null)return;
      try{ttEcologyLocation(ttEcologyPrimitive.call);if(current!=c||!issuer[0].isCurrent(c))throw new IllegalStateException("Expired TT ecology animation primitive");
       var expected=before.placed.pool();if(c.completed()&&c.kind()==CdObjectAnimation.Kind.STORE)expected=CdObjectPool.overlay(expected,List.of(before.placed.pool().slots().get(physical-32).moveByte(c.field(),c.value())));
       before.check(expected);if(c.completed()&&c.kind()==CdObjectAnimation.Kind.STORE)ttEcologyLanded();
      }finally{current=null;before=null;}
     }
    });issuer[0].step(ttObjects.ecologyAnimation(table));
   }
   public void draw(int whole){var p=ttEcologyUse(Kind.DRAW);if(whole!=physical)throw new IllegalArgumentException("Foreign TT ecology Draw");familyDraw(placement.pool(),placement.pool().state(whole).identity(),()->ttEcology.isCurrent(p.call));ttEcologyLanded();}
   public void despawn(int whole,Integer anchor){
    var p=ttEcologyUse(anchor==null?Kind.DESPAWN_CURRENT:Kind.DESPAWN_ANCHOR);if(whole!=physical)throw new IllegalArgumentException("Foreign TT ecology despawn");
    var gates=frame.environment().movement().gates();var row=placement.pool().state(whole);
    var result=CdSpinTunnelTag.checkObjectDespawnAtX(row,anchor==null?row.centreX():anchor,new CdSpinTunnelTag.DespawnGlobals(gates.timeZone(),gates.timeWarpDirection(),camera().xWord()));
    if(!result.deleteObject())return;
    if(result.loadedClear().isPresent()){
     var clear=result.loadedClear().orElseThrow();if(clear.index()<2||clear.index()>=CdObjectPlacementRuntime.SAVED_BANK_BYTES||clear.period()>2)throw unavailable("reached TT ecology saved clear outside genuine finite bank");
     var before=new FamilyCut();var saved=before.placed.savedFlags().copy();saved[clear.index()]&=0x7F;before.check(before.placed.pool());
     placement.bclrSavedFlag(clear.savedId(),clear.period(),CdObjectPlacementRuntime.SavedBit.LOADED);before.check(before.placed.pool(),CdAssetBytes.copyOf(saved));ttEcologyLanded();
    }
    var before=new FamilyCut();var planned=CdObjectPool.stagingCopy(before.placed.pool());planned.deleteObject(whole);before.check(before.placed.pool());placement.pool().deleteObject(whole);before.check(planned.capture());ttEcologyLanded();
   }
   public int floor(int whole){
    ttEcologyUse(Kind.FLOOR);if(whole!=physical||physicalId!=0x32)throw unavailable("reached TT ecology floor owner");
    var row=placement.pool().state(whole);var terrain=CdPlayerTerrain.fromStageTerrain(frame.stageSource().terrain());
    final boolean[] first={true};
    // CheckBlockDown: signed height BYTE, primary-angle prezero, then the genuine current
    // geometry fence/read. Every completed angle write remains landed if a later read faults.
    return terrain.floorWithPrimaryZeroPrefix(row.centreX(),(row.centreY()+(byte)row.unsignedByte(CdObjectState.ByteField.Y_RADIUS))&65535,
      13,0,16,row.spriteFlags(),value->{
       ttEcologyUse(Kind.FLOOR);new FamilyCut().publish(null,frame.prepareObjectPrimaryAngle(frame.environment().movement().globals().primaryAngle(),value),null,List.of());ttEcologyLanded();
       if(first[0]){first[0]=false;ttBlockTerrain();}
      }).distance();
   }
   public int wall(int whole,boolean right,int width){ttEcologyUse(right?Kind.WALL_RIGHT:Kind.WALL_LEFT);throw unavailable("reached TT ecology wall owner");}
   public boolean solid(int whole){
    var p=ttEcologyUse(Kind.SOLID);if(whole!=physical||(physicalId!=0x22&&physicalId!=0x32))throw new IllegalStateException("Foreign original TT Spikes/FallSpikes Solid");
    return ttPlainSolid(()->ttEcology.isCurrent(p.call),()->ttEcologyLanded())!=0;
   }
   public void hurtWithYRewind(int whole){ttEcologyUse(Kind.HURT_WITH_Y_REWIND);throw unavailable("reached original TT Spikes Y-rewind/Hurt owner");}
   public void fm(int sound){ttEcologyUse(Kind.FM);throw unavailable("reached original TT ecology FM $"+Integer.toHexString(sound));}
   public void testSpawnPoints(int whole){ttEcologyUse(Kind.TEST_SPAWN_POINTS);throw unavailable("reached foreign TestObject/Points owner");}
  };
  private void ttEcology(int whole,int id){
   requirePhysical(whole);if(!waterProgramme()||physicalId!=id||ttEcologyPrimitive!=null)throw new IllegalStateException("Wrong original TT ecology dispatch");
   if(ttEcology==null)ttEcology=new CdR4ObjectScope(placement,ttEcologyPorts,ttEcologyGuard());
   var row=placement.pool().state(whole);ttFallInit=id==0x32&&row.routine()==0&&(byte)row.subtype()>=0;ttFallInitChild=-1;
   try{switch(id){case 0x22->CdR4Spikes.run(ttEcology,whole);case 0x2B->CdR4Tonbo.run(ttEcology,whole);case 0x2F->CdR4Animal.run(ttEcology,whole);case 0x32->CdR4FallSpikes.run(ttEcology,whole);default->throw new IllegalArgumentException("Unbound original TT ecology family");}}
   finally{ttEcologyPrimitive=null;ttFallInit=false;ttFallInitChild=-1;}
  }
  private void bubbleSpawner(int whole){CdR4BubbleSpawner.run(new WaterSlot(whole),waterCalls,ttObjects.bubbleSpawner(),CdR4BubbleSpawner.ContactProfile.USA);}
  private void waterSurface(int whole){var data=ttObjects.waterSurface();CdR4WaterSurface.run(new WaterSlot(whole),waterCalls,data.mappingsAddress(),data.offsets());}
  private void ttPalette(){
   var g=active.state().globals();var m=frame.state().motion();var result=((CdTtOriginalWorldBindings)programme).paletteAt(new CdOriginalWorldBindings.PaletteState(active.prefix().normalPalette(),g.paletteTimers7(),g.paletteSteps7()),active.currentWater(),active.prefix().fadePalette(),active.currentWaterFade(),camera().xWord(),camera().yWord(),g.ring().timeStopTimer());
   var next=result.state();active.palette(next.normal());globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),next.steps(),next.timers()));
   if(!result.completed())throw unavailable("CyclePalette source read "+result.fault().orElseThrow());
  }
  private final class Ports implements CdOrdinaryLoop.Ports {
   public Optional<CdPlayerFrameRuntime.OriginalProgrammeOwner> originalProgrammeOwner(){return waterProgramme()?Optional.of(waterOwner):Optional.empty();}
   public Optional<CdPlayerFrameRuntime.OriginalProgrammeOwner> originalStandardProgrammeOwner(){return standardPastProgramme()?Optional.of(standardOwner):Optional.empty();}
   public void requireCurrent(Cut at,CdPlayerFrameRuntime f,CdStagePlayerSource source){live();if(f!=frame||source!=frame.stageSource())throw new IllegalArgumentException("Foreign ordinary source");cut=at;}
   public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){live();sounds=List.of();selectedWaterCpu=cpu;return cpu;}
   public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> old){active.publish(old);}
   public void updateAnimTiles(){active.powerup();}
   public void copyCameraAndDrawLevel(){active.copyDraw();}
   public void serviceSlowPlc(){active.slow();}
   public void updateHud(){font();}
   public void mainMusic(){active.serviceCount();var g=active.state().globals();var result=CdMainMusic.step(g.pauseMusicBit().orElseThrow(),frame.paused(),frame.environment());for(var request:result.sounds())sound(request);globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),OptionalInt.of(result.pauseMusicBit()),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()));}
   public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> old){active.refresh();}
   public void spawnObjects(){placement.spawnObjects(camera().xWord(),frame.environment().movement().gates().timeZone());}
   public void runObjects(int levelFrame){walk(levelFrame);}
   public CdOrdinaryLoop.MainContinuation postObjectCamera(){if(!walkReturned)throw new IllegalStateException("No complete physical pass");if(active.prefix().main().level().levelRestart()!=0){active.beginLevelStart(CdWorld.this);return CdOrdinaryLoop.MainContinuation.TRANSFERRED;}if(frame.environment().globals().debugMode()==0&&frame.state().motion().routine()>=6)deathCamera();else scroll();if(waterProgramme())currents();return CdOrdinaryLoop.MainContinuation.ORDINARY_TAIL;}
   public void drawObjects(){if(!walkReturned)throw new IllegalStateException("No complete physical pass");var s=active.state().scroll();var b=s.background();draining=true;try{var cpu=queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera(),new CdObjectDrawQueue.CameraWords(b.cameraBgXWord(),b.cameraBgYWord()),new CdObjectDrawQueue.CameraWords(b.cameraBg3XWord(),b.cameraBg3Y()>>>16)));active.cpu(cpu);R11.this.cpu=Optional.of(cpu);queue.close();queue=null;}finally{draining=false;}}
   public int timeStopWord(){return active.state().globals().ring().timeStopTimer();}
   public void paletteCycle(){if(waterProgramme()){ttPalette();return;}var g=active.state().globals();CdOriginalWorldBindings.PaletteResult selected;try{selected=programme.paletteCycle(()->new CdOriginalWorldBindings.PaletteState(active.prefix().normalPalette(),g.paletteTimers7(),g.paletteSteps7()));}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}var result=selected.nativeResult();var ready=SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),selected.steps7(),selected.timers7());active.palette(result.state().palette());globals(ready);if(!result.completed())throw programme.paletteFailure(result.fault().orElseThrow());}
   public void updateSectionArt(){active.updateSection();}
   public void preparePlc(){active.prepare();}
   public void updateGlobalAnimations(){var g=active.state().globals();var n=CdGlobalObjectAnimation.step(g.animations().orElseThrow(),g.ring());globals(SonicCdR31aWorldHost.with(g,n.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),Optional.of(n.extras()),g.paletteSteps7(),g.paletteTimers7()));}
  }
 }
 private final class R13 extends Binding {
 private final SonicCdAct3FutureWorldHost.Authority authority;private final SonicCdAct3FutureLoadingVisit core;private final SonicCdAct3FutureWorldHost host;
 private final CdPlayerFrameRuntime frame;private final CdR13FutureAssets assets;private final CdR13SectionArt art;private final CdR13ObjectAssets metadata;
 private final CdR13FutureScrollPalette palette;private final CdPlayerObjectTouch scanner;private final CdPlayerTouchResponse response;
 private final SonicCdAct3FutureObjectPass objects;
 private SonicCdAct3FutureLoadingVisit.OrdinaryAccess active;private boolean walkReturned;
 R13(SonicCdAct3FutureWorldHost.Authority authority,SonicCdAct3FutureLoadingVisit core,SonicCdAct3FutureWorldHost host,
                               CdPlayerFrameRuntime frame,CdR13FutureAssets assets,CdR13SectionArt art,CdR13ObjectAssets metadata,CdR13PlayerCollisionAssets collision){
  this.authority=Objects.requireNonNull(authority);this.core=Objects.requireNonNull(core);this.host=Objects.requireNonNull(host);this.frame=Objects.requireNonNull(frame);this.assets=Objects.requireNonNull(assets);this.art=Objects.requireNonNull(art);this.metadata=Objects.requireNonNull(metadata);Objects.requireNonNull(collision);
  if(core.assets()!=assets||!frame.stageSource().ownsAct3Future(assets)||!art.ownsAssets(assets)||!metadata.ownsAssets(assets)||!collision.ownsAssets(assets))throw new IllegalArgumentException("Exact R13 Core/Frame/source/art/metadata relation required");
  palette=CdR13FutureScrollPalette.bind(assets);scanner=new CdPlayerObjectTouch(collision.touchDimensions());response=CdPlayerTouchResponse.fromAct3FutureAssets(collision);
  objects=new SonicCdAct3FutureObjectPass(new ObjectPorts(),assets,metadata,frame,host.placement());
 }
 private void requireActual(){host.requireRenderIdle();host.requireAuthority(authority,CdWorld.this);if(active==null||frame.closed()||!frame.stageSource().ownsAct3Future(assets))throw new IllegalStateException("No actual R13 ordinary continuation");active.requireOwner(CdWorld.this);}
 @Override void idle(){host.requireRenderIdle();host.requireAuthority(authority,CdWorld.this);}
 @Override void require(){requireActual();}
 @Override Object scope(){return active;}
 @Override CdOrdinaryLoop.Ports ports(){return new LoopPorts();}
 @Override void clear(){walkReturned=false;active=null;}
 @Override void discard(){objects.discard();}
 @Override void playerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete){publishPlayerArt(request,complete);}
 /** Private stack-bound completed-walk receipt; equal Core/State records cannot supply it. */
 void requireOldExitOwner(SonicCdAct3FutureLoadingVisit expected){requireCut(Cut.POST_OBJECT_CAMERA);if(expected!=core||!walkReturned)throw new IllegalStateException("No completed actual R13 walk");}
 void abandonOldExitWalk(SonicCdAct3FutureLoadingVisit expected){requireOldExitOwner(expected);objects.abandonCompletedWalk();walkReturned=false;}
 private void publishPlayerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete){requireCut(Cut.PUBLISH_LEVEL);if(request.vramByteAddress()!=0xF000||!Arrays.equals(request.bytes().copy(),complete.residentBank().slice(0,0x2E0).copy())||!Arrays.equals(frame.artState().residentBank().copy(),complete.residentBank().copy()))throw new IllegalStateException("Original selector8 player publication changed");}
 private void adopt(SonicCdAct3FutureWorldHost.Globals g,CdR13SectionArt.State section,CdLevelGlobals level,CdAssetBytes reserved,CdAssetBytes normal,CdAssetBytes displayed,List<SonicCdGameModule.PastSound> sounds,Optional<CdObjectDrawQueue.CpuSat> cpu){
  require();var before=active.state();var ready=active.prepare(before,section,level,reserved,normal,displayed);var hp=host.prepareOrdinary(host.globals(),host.video(),g,sounds,cpu);active.validate(before);host.validateOrdinary(hp);active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
 }
 private void globals(SonicCdAct3FutureWorldHost.Globals g,List<SonicCdGameModule.PastSound> sounds){var s=active.state();adopt(g,s.section(),s.level(),s.hudSlots(),s.normalPalette(),s.displayedPalette(),sounds,host.video().completedCpu());}
 private void retainHud(CdLevelHud.Act3FutureState next){require();var g=host.globals();var ready=SonicCdAct3FutureWorldHost.with(g,next.ring(),next.liveTime(),g.contact(),next.score(),g.pauseMusicBit(),next.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());var hp=host.prepareOrdinary(g,host.video(),ready,List.of(),host.video().completedCpu());host.validateOrdinary(hp);host.adoptAfterNative(hp);}
 private void updateHud(){
  requireCut(Cut.UPDATE_HUD);var s=active.state();var g=host.globals();var e=frame.environment();var m=frame.state().motion();var inputs=new CdLevelHud.Inputs(new CdLevelHud.Globals(e.globals().debugCheat(),(g.pauseMusicBit().orElseThrow()|(frame.paused()?1:0))<<8,e.controls().controlLocked(),e.movement().gates().timeZone(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,e.movement().gates().zoneAct()),new CdLevelHud.DebugData(OptionalInt.of(host.placement().capture().savedFlags().unsignedByte(0)),s.layoutRam(),g.debugBlockWord()));
  try{retainHud(active.runHud(s,new CdLevelHud.Act3FutureState(s.section(),g.ring(),s.level(),g.score(),g.live(),g.bonus()),inputs).state());}
  catch(CdLevelHud.UnsupportedAct3FutureHud original){try{retainHud(original.state());}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
  catch(CdLevelHud.Act3FutureArtFailure prefix){var original=prefix.original();try{retainHud(prefix.state());}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
 }
 // BEGIN R13 LEVEL MAIN DEATH CAMERA WORLD JOIN 20261107
 /** Main409-415, after the original complete physical walk. Every potentially failing
  * preparation/owner check precedes both callback-free prebuilt reference adoptions. */
 private void deathCamera(){
  requireCut(Cut.POST_OBJECT_CAMERA);if(!walkReturned)throw new IllegalStateException("No completed actual physical walk");
  var before=active.state();var actor=frame.state();var environment=frame.environment();var display=frame.display();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();var placement=host.placement();var placed=placement.capture();
  int cameraY=host.scroll().foreground().cameraYWord();
  var fp=frame.prepareLevelMainDeathCamera(frame.stageSource(),actor,environment,cameraY);var hp=host.prepareLevelMainDeathCamera(cameraY);
  active.validate(before);host.validatePrimaryDeathPublication(hp);frame.validatePreparedObjectSolidPublication(fp);
  var now=placement.capture();if(core.state()!=before||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags()))throw new IllegalStateException("Death camera changed an unowned completed-walk partition");
  frame.applyPreparedObjectSolidPublication(fp);host.adoptAfterNative(hp);
 }
 // END R13 LEVEL MAIN DEATH CAMERA WORLD JOIN 20261107
 private final class LoopPorts implements CdOrdinaryLoop.Ports {
  public void requireCurrent(Cut at,CdPlayerFrameRuntime f,CdStagePlayerSource a){require();if(f!=frame||a!=frame.stageSource())throw new IllegalArgumentException("Another ordinary loop");cut=at;}
  public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){require();return host.beforeSelector8();}
  public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> selected){require();var s=active.state();host.publishOrdinary(s.normalPalette(),selected);adopt(host.globals(),s.section(),s.level(),s.hudSlots(),s.normalPalette(),s.normalPalette(),List.of(),host.video().completedCpu());}
  public void updateAnimTiles(){require();var s=active.state();var l=s.level();var gate=CdPowerupArtRuntime.gate(l.powerup());var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),gate.powerup(),l.timeOver());adopt(host.globals(),s.section(),next,s.hudSlots(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());if(gate.copyRequired())throw new Stopped(Boundary.ACTIVE_POWERUP_CPU);}
  public void copyCameraAndDrawLevel(){require();host.copyAndDrawOrdinary();}
  public void serviceSlowPlc(){require();active.serviceSlowPlc(active.state(),host.scroll().foreground().scrollLock());}
  public void updateHud(){R13.this.updateHud();}
  public void mainMusic(){require();host.serviceVInt();var g=host.globals();var result=CdMainMusic.step(g.pauseMusicBit().orElseThrow(),frame.paused(),frame.environment());globals(SonicCdAct3FutureWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),OptionalInt.of(result.pauseMusicBit()),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()),result.sounds());}
  public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> selected){require();host.settleOrdinary(selected);}
  public void spawnObjects(){require();host.placement().spawnObjects(host.scroll().foreground().cameraX(),frame.environment().movement().gates().timeZone());}
  public void runObjects(int levelFrame){require();walkReturned=false;objects.runWalk(levelFrame);walkReturned=true;}
  public CdOrdinaryLoop.MainContinuation postObjectCamera(){
   require();var s=active.state();var g=frame.environment().movement().gates();
   if(s.program().demoDataIndexWord()!=0x800&&s.level().levelRestart()==2&&g.timeZone()==1&&g.zoneAct()==0x100){active.beginOldExit(CdWorld.this);return CdOrdinaryLoop.MainContinuation.TRANSFERRED;}
   continueCamera();return CdOrdinaryLoop.MainContinuation.ORDINARY_TAIL;
  }
  private void continueCamera(){require();var s=active.state();if(s.program().demoDataIndexWord()==0x800||s.level().levelRestart()!=0)throw new Stopped(Boundary.LEVEL_FLOW);if(frame.environment().globals().debugMode()==0&&frame.state().motion().routine()>=6){deathCamera();return;}host.scrollOrdinary(s.level().updateHudTime());}
  public void drawObjects(){require();var s=host.scroll();var b=s.background();objects.finishDraw(new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(s.foreground().cameraX(),s.foreground().cameraYWord()),new CdObjectDrawQueue.CameraWords(b.cameraBgXWord(),b.cameraBgYWord()),new CdObjectDrawQueue.CameraWords(b.cameraBg3X()>>>16,b.cameraBg3Y()>>>16)));}
  public int timeStopWord(){requireCut(Cut.READ_TIME_STOP_WORD);return host.globals().ring().timeStopTimer();}
  public void paletteCycle(){requireCut(Cut.PALETTE_CYCLE);var s=active.state();var g=host.globals();var result=palette.palette(new CdR13FutureScrollPalette.PaletteState(s.normalPalette(),g.paletteTimers7(),g.paletteSteps7()),g.ring().timeStopTimer());var n=result.state();var ready=SonicCdAct3FutureWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),n.steps(),n.timers());adopt(ready,s.section(),s.level(),s.hudSlots(),n.palette(),s.displayedPalette(),List.of(),host.video().completedCpu());if(!result.completed())throw new Stopped(Boundary.PALETTE_SOURCE);}
  public void updateSectionArt(){require();active.updateSection(active.state());}
  public void preparePlc(){require();active.preparePlc(active.state());}
  public void updateGlobalAnimations(){require();var g=host.globals();var extras=g.animations().orElseThrow(()->new Stopped(Boundary.MISSING_GLOBAL_ANIMATIONS));var out=CdGlobalObjectAnimation.step(extras,g.ring());globals(SonicCdAct3FutureWorldHost.with(g,out.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),Optional.of(out.extras()),g.paletteSteps7(),g.paletteTimers7()),List.of());}
 }
 private SonicCdAct3FutureObjectPass.Cut objectCut(){require();var s=active.state();var g=host.globals();var v=host.video();var f=host.scroll().foreground();return new SonicCdAct3FutureObjectPass.Cut(core,s.program(),g.ring(),s.hudSlots(),v.cpuSat(),v.completedCpu(),host.sounds(),f.cameraX(),f.cameraYWord(),frame.environment().globals().timeAttackMode(),g.layerByte(),OptionalInt.of(s.program().goodFutureByte()),g.projectorByte(),f.scrollLock(),OptionalLong.of(s.section().queue().headSource()));}
 private static boolean sameCut(SonicCdAct3FutureObjectPass.Cut a,SonicCdAct3FutureObjectPass.Cut b){return a.programmeOwner()==b.programmeOwner()&&a.programme()==b.programme()&&a.ring().equals(b.ring())&&same(a.reservedSlots(),b.reservedSlots())&&same(a.cpuSat(),b.cpuSat())&&a.completedCpu().equals(b.completedCpu())&&a.sounds().equals(b.sounds())&&a.cameraXWord()==b.cameraXWord()&&a.cameraYWord()==b.cameraYWord()&&a.timeAttackModeByte()==b.timeAttackModeByte()&&a.layerByte().equals(b.layerByte())&&a.goodFutureByte().equals(b.goodFutureByte())&&a.projectorByte().equals(b.projectorByte())&&a.scrollLockWord()==b.scrollLockWord()&&a.plcHeadLong().equals(b.plcHeadLong());}
 private final class ObjectPorts implements SonicCdAct3FutureObjectPass.Host {
  public void validate(SonicCdAct3FutureObjectPass p,CdPlayerFrameRuntime f,CdR13FutureAssets a,CdR13ObjectAssets m,CdObjectPlacementRuntime placement){require();if(p!=objects||f!=frame||a!=assets||m!=metadata||placement!=host.placement()||(cut!=Cut.RUN_OBJECTS&&cut!=Cut.DRAW_OBJECTS))throw new IllegalStateException("No actual increasing physical R13 pass");}
  public SonicCdAct3FutureObjectPass.ReusedOwners reusedOwners(SonicCdAct3FutureObjectPass.ReusedOwners expected){requireCut(Cut.RUN_OBJECTS);if(!objects.isCurrentReusedOwnersRead())throw new IllegalStateException("No original reused-family owner read");var g=host.globals();if(expected!=null){if(expected.fence()!=g||!expected.contact().equals(g.contact())||!expected.animations().equals(g.animations()))throw new IllegalArgumentException("Stale full Globals/contact/animations fence");return expected;}return new SonicCdAct3FutureObjectPass.ReusedOwners(g,g.contact(),g.animations());}
  public SonicCdAct3FutureObjectPass.BossOwners bossOwners(SonicCdAct3FutureObjectPass.BossOwners expected){requireCut(Cut.RUN_OBJECTS);var value=host.readBossOwners(objects);if(expected!=null&&(expected.hostFence()!=value.hostFence()||expected.coreFence()!=value.coreFence()))throw new IllegalArgumentException("Stale actual full Boss Core/Host fence");return value;}
  public SonicCdAct3FutureObjectPass.EndOwners endOwners(SonicCdAct3FutureObjectPass.EndOwners expected){requireCut(Cut.RUN_OBJECTS);var value=host.readEndOwners(objects);if(expected!=null&&(expected.hostFence()!=value.hostFence()||expected.coreFence()!=value.coreFence()))throw new IllegalArgumentException("Stale actual full Capsule/Results fence");return value;}
  public SonicCdAct3FutureObjectPass.Cut read(){require();return objectCut();}
  public void adopt(SonicCdAct3FutureObjectPass.Adoption p){
   require();if(!objects.isCurrentAdoption(p)||p.owner()!=objects||!sameCut(p.expected(),objectCut()))throw new IllegalArgumentException("Not original pending R13 publication");p.validateBefore();var next=p.next();var before=active.state();var g=host.globals();
   var end=p.endExpected();var endNext=p.endNext();if(end!=null){if(p.endCall()==null||!objects.isCurrentEndOperation(p.endCall())||end.coreFence()!=before||p.bossExpected()!=null||p.reusedExpected()!=null)throw new IllegalArgumentException("No original Capsule/Results publication");}else if(endNext!=null)throw new IllegalArgumentException("End values omitted full owner fence");
   boolean goodStore=p.endCall() instanceof CdResults.ProgrammeStoreCall c&&c.field()==CdResults.ProgrammeField.GOOD_FUTURE_BYTE;boolean projectorStore=p.endCall() instanceof CdResults.ProgrammeStoreCall c&&c.field()==CdResults.ProgrammeField.PROJ_DESTROYED_BYTE;
   if(next.programmeOwner()!=core||next.programme()!=before.program()||!next.layerByte().equals(g.layerByte())||!next.goodFutureByte().equals(OptionalInt.of(goodStore?endNext.goodFutureByte():before.program().goodFutureByte()))||!next.projectorByte().equals(projectorStore?OptionalInt.of(0):g.projectorByte())||!next.plcHeadLong().equals(OptionalLong.of(before.section().queue().headSource()))||next.cameraXWord()!=host.scroll().foreground().cameraX()||next.cameraYWord()!=host.scroll().foreground().cameraYWord()||next.timeAttackModeByte()!=frame.environment().globals().timeAttackMode())throw new IllegalArgumentException("Unowned source/programme scalar writer");
   if(p.endCall()!=null&&(!same(next.reservedSlots(),before.hudSlots())||!same(next.cpuSat(),host.video().cpuSat())||!next.completedCpu().equals(host.video().completedCpu())||next.scrollLockWord()!=host.scroll().foreground().scrollLock()))throw new IllegalArgumentException("End object changed reserved/CPU/scroll nonwrites");
   var boss=p.bossExpected();var bossNext=p.bossNext();if(boss!=null){if(p.bossCall()==null||!objects.isCurrentBossOperation(p.bossCall())||boss.coreFence()!=before||bossNext==null||p.reusedExpected()!=null||p.reusedNext()!=null)throw new IllegalArgumentException("No original Boss owner publication");}else if(bossNext!=null)throw new IllegalArgumentException("Boss values omitted their owner fence");
   var normal=endNext!=null?endNext.normalPalette():bossNext==null?before.normalPalette():bossNext.normalPalette();
   if(p.bossCall()!=null&&(!objects.isCurrentBossOperation(p.bossCall())||!same(next.reservedSlots(),before.hudSlots())||!same(next.cpuSat(),host.video().cpuSat())||!next.completedCpu().equals(host.video().completedCpu())||next.scrollLockWord()!=host.scroll().foreground().scrollLock()))throw new IllegalArgumentException("Boss wrote an unowned reserved/CPU/scroll partition");
   var program=before.program();if(goodStore){var q=program;program=new SonicCdAct3FutureLoadingVisit.Program(q.demoModeWord(),q.timeStonesByte(),endNext.goodFutureByte(),q.usePlayer2Byte(),q.specialStageByte(),q.palFadeFlagsByte(),q.palClearFlagBit0(),q.gameModeByte(),q.levelStartedByte(),q.demoDataIndexWord());}var section=before.section();if(p.endCall() instanceof CdResults.ProgrammeStoreCall c&&c.field()==CdResults.ProgrammeField.SECTION_ID_WORD){var a=section;section=art.fromValues(a.codeTable(),a.residency(),0,a.queue(),a.decoder(),a.savedDecoder(),a.remainingTilesWord(),a.processBudgetWord(),a.hintWord(),a.padWord());}
   var ready=end!=null?active.prepareResults(before,program,section,endNext==null?before.level():endNext.level(),next.reservedSlots(),normal,before.displayedPalette()):normal==before.normalPalette()?active.prepare(before,before.section(),before.level(),next.reservedSlots(),normal,before.displayedPalette()):active.prepareNormalPalette(before,normal);if(goodStore){p.prepareEndProgramme(before.program(),ready.program());next=p.next();}var sounds=host.sounds();if(next.sounds().size()<sounds.size()||!next.sounds().subList(0,sounds.size()).equals(sounds))throw new IllegalArgumentException("Sound prefix replaced");var contact=g.contact();var animations=g.animations();if(p.reusedExpected()!=null){var expected=p.reusedExpected();if(expected.fence()!=g||!expected.contact().equals(contact)||!expected.animations().equals(animations)||p.reusedNext()==null)throw new IllegalArgumentException("Unissued/stale reused-family values");contact=p.reusedNext().contact();animations=p.reusedNext().animations();}else if(p.reusedNext()!=null)throw new IllegalArgumentException("Reused values omitted their original owner fence");var globals=SonicCdAct3FutureWorldHost.with(g,next.ring(),g.live(),contact,bossNext==null?g.score():bossNext.score(),g.pauseMusicBit(),g.bonus(),animations,g.paletteSteps7(),g.paletteTimers7());var hp=host.prepareOrdinary(g,host.video(),globals,next.sounds().subList(sounds.size(),next.sounds().size()),next.completedCpu());if(next.scrollLockWord()!=host.scroll().foreground().scrollLock())hp=host.prepareScrollLock(hp,next.scrollLockWord());if(boss!=null)hp=host.prepareBossOwners(hp,objects,boss,bossNext);if(endNext!=null)hp=host.prepareEndOwners(hp,objects,p);var flagsReady=objects.isCurrentEndFlagsPublication(p)?host.prepareEndFlags(objects,p):null;
   active.validate(before);host.validateOrdinary(hp);if(flagsReady!=null)host.validateEndFlags(flagsReady);p.validateBefore();p.applyNative();if(flagsReady!=null)host.adoptEndFlagsAfterNative(flagsReady);active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
  }
  public void submitTitlePlc(SonicCdAct3FutureObjectPass.PlcSubmission p){requireCut(Cut.RUN_OBJECTS);if(p.owner()!=objects||!objects.isCurrentPlcSubmission(p)||p.nativeId()!=2)throw new IllegalArgumentException("Not actual deferred Title2");p.validate();active.appendTitlePlc(active.state());p.acknowledge();}
  public void submitBossPlc(SonicCdAct3FutureObjectPass.BossPlcSubmission p){
   requireCut(Cut.RUN_OBJECTS);if(p.owner()!=objects||!objects.isCurrentBossPlcSubmission(p)||p.nativeId()!=4)throw new IllegalArgumentException("Not actual Boss PLC4");p.validate();var before=active.state();if(p.expectedOwners().coreFence()!=before)throw new IllegalArgumentException("Stale original Boss Core image");
   try{active.appendBossPlc(before);var after=active.state();validateBossPlcCore(before,after,true);host.validateBossPlcPrefix(p,after);p.acknowledge();}
   catch(RuntimeException|Error original){try{var after=active.state();validateBossPlcCore(before,after,false);host.validateBossPlcPrefix(p,after);p.validatePrefix();}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}throw original;}
  }
  public void submitResultsPlc(SonicCdAct3FutureObjectPass.EndPlcSubmission p){
   requireCut(Cut.RUN_OBJECTS);if(p.owner()!=objects||!objects.isCurrentResultsPlcSubmission(p)||p.nativeId()!=16)throw new IllegalArgumentException("Not actual Results cue16");p.validate();var before=active.state();if(p.expectedOwners().coreFence()!=before)throw new IllegalArgumentException("Stale original Results Core image");
   try{active.appendResultsPlc(before);var after=active.state();validateResultsPlcCore(before,after,true);host.validateEndPlcPrefix(p,after);p.acknowledge();}catch(RuntimeException|Error original){try{var after=active.state();validateResultsPlcCore(before,after,false);host.validateEndPlcPrefix(p,after);p.validatePrefix();}catch(RuntimeException|Error check){if(check!=original)original.addSuppressed(check);}throw original;}
  }
  // BEGIN R13 ORIGINAL GAME OVER CORE SCALAR JOIN 20261107
  public SonicCdAct3FutureObjectPass.GameOverOwners gameOverOwners(SonicCdAct3FutureObjectPass.GameOverOwners expected){
   requireCut(Cut.RUN_OBJECTS);var actual=host.readGameOverOwners(objects);if(expected!=null&&(expected.hostFence()!=actual.hostFence()||expected.coreFence()!=actual.coreFence()||expected.level()!=actual.level()))throw new IllegalArgumentException("Stale actual GameOver owners");return actual;
  }
  public void storeGameOver(SonicCdAct3FutureObjectPass.GameOverStore actual){
   requireCut(Cut.RUN_OBJECTS);if(actual.owner()!=objects||!objects.isCurrentGameOverStore(actual))throw new IllegalArgumentException("No original GameOver scalar invocation");actual.validateBefore();var before=active.state();if(actual.expected().coreFence()!=before||actual.expected().level()!=before.level())throw new IllegalArgumentException("Different actual GameOver Core");
   var old=before.level();var call=actual.call();var next=new CdLevelGlobals(old.levelRestart(),old.updateHudTime(),old.checkpoint(),old.spawnMode(),old.plcLoadFlags(),old.flowerCount(),old.fadeEnableDisplay(),call.field()==CdGameOver.Scalar.POWERUP?call.value():old.powerup(),call.field()==CdGameOver.Scalar.TIME_OVER?call.value():old.timeOver());
   var ready=active.prepare(before,before.section(),next,before.hudSlots(),before.normalPalette(),before.displayedPalette());var hp=host.prepareGameOverScalar(actual,before,next);active.validate(before);host.validateGameOverScalar(hp,actual,before);
   active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);actual.acknowledge();
  }
  // END R13 ORIGINAL GAME OVER CORE SCALAR JOIN 20261107
  public void primary(SonicCdAct3FutureObjectPass.PrimaryCall call){
   requireCut(Cut.RUN_OBJECTS);if(call.owner()!=objects)throw new IllegalArgumentException("Another primary producer");var before=active.state();var g=host.globals();var placed=host.placement().capture();var pool=placed.pool();var state=frame.state();var env=frame.environment();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();var fence=host.prepareOrdinary(g,host.video(),g,List.of(),host.video().completedCpu());CdPlayerFrameRuntime.BodyPublication publication;
   if(state.motion().routine()==0){publication=frame.runBody(call.levelFrameWord());if(publication.result().boundary()!=CdPlayerBody.Boundary.INIT_RETURN)throw new IllegalStateException("Genuine Sonic Init omitted its original return");}
   else{
    if(state.motion().routine()!=2&&state.motion().routine()!=4&&state.motion().routine()!=6&&state.motion().routine()!=8)throw new Stopped(Boundary.PRIMARY_LIFECYCLE);
    var r=g.ring();var score=g.score();var responseContext=new CdPlayerBody.ResponseContext(response,g.contact(),new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()));var support=new CdPlayerMovement.ObjectContext(pool,state.objectLinks().standObject(),state.header().slotId(),state.header().width());var warp=new CdPlayerTimeWarp.Context(OptionalInt.of(before.level().spawnMode()),Optional.empty(),Optional.empty(),OptionalInt.of(before.level().levelRestart()));
    publication=frame.runBody(call.levelFrameWord(),Optional.of(new CdPlayerBody.TouchContext(scanner,pool,responseContext)),Optional.of(new CdPlayerBody.LevelContext(pool,r,before.level())),Optional.of(support),Optional.of(new CdPlayerBody.TimeWarpContext(warp,g.contact())),Optional.of(new CdPlayerBody.BossSpecialContext(response,pool,env.movement().bounds().bossFight())));
   }
   require();active.validate(before);host.validateOrdinary(fence);var now=host.placement().capture();if(!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags())||frame.state()!=publication.result().state()||frame.environment()!=publication.result().environment()||frame.display()!=publication.result().display()||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused)throw new IllegalStateException("Original Body result left its complete native cut");
   if(publication.result().boundary()==CdPlayerBody.Boundary.DEAD_RETURN||publication.result().boundary()==CdPlayerBody.Boundary.RESTART_RETURN)publishLifecycle(publication,pool,g,before);
   else if(publication.result().boundary()!=CdPlayerBody.Boundary.INIT_RETURN){var plan=SonicCdAct2PastPrimaryPublication.prepareBossValues(publication,pool,new SonicCdAct2PastPrimaryPublication.Values(g.ring(),g.score(),g.contact()),before.level(),before.hudSlots());var v=plan.values();var globals=SonicCdAct3FutureWorldHost.with(g,v.ring(),g.live(),v.contact(),v.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());var ready=active.prepare(before,before.section(),plan.level(),plan.reserved(),before.normalPalette(),before.displayedPalette());var hp=host.prepareOrdinary(g,host.video(),globals,plan.sounds(),host.video().completedCpu());host.placement().pool().validateRestore(plan.pool());active.validate(before);host.validateOrdinary(hp);host.placement().pool().restore(plan.pool());active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);}
   call.complete(publication);
  }
 }
 // BEGIN R13 COMPLETED PRIMARY LIFECYCLE WORLD JOIN 20261107
 /** Existing pure Body owns all actor/animation/history writes. Fold only its one
  * Runtime-owned death/restart summary; never replay LevelWrite/call/actor effects.
  * N1ecc Sonic/Main2397-2494; established native semantic publication precedent:
  * CdOpeningObjectRuntime.commitDeadGone/commitRestart. No GameOver or LevelStart owner is invented. */
 private void publishLifecycle(CdPlayerFrameRuntime.BodyPublication publication,CdObjectPool.Snapshot pool,
                               SonicCdAct3FutureWorldHost.Globals g,SonicCdAct3FutureLoadingVisit.State before){
  var nativeStop=CdPrimaryLifecycle.aliasReached(publication.result())?new Stopped(Boundary.PRIMARY_DEATH_ALIAS):null;
  try{
  var result=publication.result();boolean dead=result.boundary()==CdPlayerBody.Boundary.DEAD_RETURN;
  var placement=host.placement();var placed=placement.capture();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();
  if(!placed.pool().slots().equals(pool.slots()))throw new IllegalArgumentException("Stale lifecycle full96 input");
  if(!dead&&result.boundary()!=CdPlayerBody.Boundary.RESTART_RETURN)throw new IllegalArgumentException("Not an original lifecycle return");
  var plan=CdPrimaryLifecycle.prepare(publication,pool,g.ring(),before.level());
  var ring=plan.ring();var level=plan.level();var nextPool=plan.pool();var sounds=plan.sounds();boolean alias=plan.alias();
  var globals=SonicCdAct3FutureWorldHost.with(g,ring,g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
  var ready=active.prepare(before,before.section(),level,before.hudSlots(),before.normalPalette(),before.displayedPalette());var hp=host.prepareOrdinary(g,host.video(),globals,sounds,host.video().completedCpu());
  placement.pool().validateRestore(nextPool);active.validate(before);host.validatePrimaryDeathPublication(hp);
  var now=placement.capture();if(core.state()!=before||frame.state()!=result.state()||frame.environment()!=result.environment()||frame.display()!=result.display()||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags()))throw new IllegalStateException("Lifecycle changed another full actual owner during preparation");
  // All prebuilt values/ownership checks precede this callback-free native commit prefix.
  placement.pool().restore(nextPool);if(plan.resetSaved())placement.resetSavedObjectFlags();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
  // The original whole Body is atomic; no buffer/slot stands in for the unowned $FFF000 target.
  // Its completed actor/global prefix is retained, but there is no successful PrimaryCall/Draw/physical tail.
  }catch(RuntimeException|Error preparation){if(nativeStop==null)throw preparation;if(preparation!=nativeStop)nativeStop.addSuppressed(preparation);throw nativeStop;}
  if(nativeStop!=null)throw nativeStop;
 }
 // END R13 COMPLETED PRIMARY LIFECYCLE WORLD JOIN 20261107
 // BEGIN R13 ACTUAL BOSS PLC CORE NONWRITES 20261106
 private static void validateBossPlcCore(SonicCdAct3FutureLoadingVisit.State before,SonicCdAct3FutureLoadingVisit.State after,boolean success){
  if(after.phase()!=before.phase()||after.program()!=before.program()||after.level()!=before.level()||after.normalPalette()!=before.normalPalette()||after.fadePalette()!=before.fadePalette()||after.waterPalette()!=before.waterPalette()||after.displayedPalette()!=before.displayedPalette()||after.hudSlots()!=before.hudSlots()||after.blocksRam()!=before.blocksRam()||after.layoutRam()!=before.layoutRam()||after.initialFadeSteps()!=before.initialFadeSteps()||after.fromFadeSteps()!=before.fromFadeSteps()||after.displayEnabled()!=before.displayEnabled())throw new IllegalStateException("Boss PLC changed unowned full Core fields");
  var a=before.section();var b=after.section();if(!same(a.codeTable(),b.codeTable())||a.residency()!=b.residency()||!a.decoder().equals(b.decoder())||!a.savedDecoder().equals(b.savedDecoder())||a.sectionWord()!=b.sectionWord()||a.remainingTilesWord()!=b.remainingTilesWord()||a.processBudgetWord()!=b.processBudgetWord()||a.hintWord()!=b.hintWord()||a.padWord()!=b.padWord())throw new IllegalStateException("Boss append changed decoder/table/current/scalar nonwrites");
  var previous=before.calls();var calls=after.calls();if(calls.size()<previous.size()||!calls.subList(0,previous.size()).equals(previous))throw new IllegalStateException("Boss PLC replaced native audit prefix");var suffix=calls.subList(previous.size(),calls.size());
  if(success?!suffix.equals(List.of(SonicCdAct3FutureLoadingVisit.Call.BOSS_PLC_APPEND)):!(suffix.isEmpty()||suffix.equals(List.of(SonicCdAct3FutureLoadingVisit.Call.CURRENT_ART))))throw new IllegalStateException("Boss PLC completion/audit mismatch");
 }
 // END R13 ACTUAL BOSS PLC CORE NONWRITES 20261106

 // BEGIN R13 ACTUAL RESULTS PLC CORE NONWRITES 20261106
 private static void validateResultsPlcCore(SonicCdAct3FutureLoadingVisit.State before,SonicCdAct3FutureLoadingVisit.State after,boolean success){
  if(after.phase()!=before.phase()||after.program()!=before.program()||after.level()!=before.level()||after.normalPalette()!=before.normalPalette()||after.fadePalette()!=before.fadePalette()||after.waterPalette()!=before.waterPalette()||after.displayedPalette()!=before.displayedPalette()||after.hudSlots()!=before.hudSlots()||after.blocksRam()!=before.blocksRam()||after.layoutRam()!=before.layoutRam()||after.initialFadeSteps()!=before.initialFadeSteps()||after.fromFadeSteps()!=before.fromFadeSteps()||after.displayEnabled()!=before.displayEnabled())throw new IllegalStateException("Results PLC changed unowned full Core fields");
  var a=before.section();var b=after.section();if(!same(a.codeTable(),b.codeTable())||a.residency()!=b.residency()||!a.decoder().equals(b.decoder())||!a.savedDecoder().equals(b.savedDecoder())||a.sectionWord()!=b.sectionWord()||a.remainingTilesWord()!=b.remainingTilesWord()||a.processBudgetWord()!=b.processBudgetWord()||a.hintWord()!=b.hintWord()||a.padWord()!=b.padWord())throw new IllegalStateException("Results append changed decoder/table/current/scalar nonwrites");
  var previous=before.calls();var calls=after.calls();if(calls.size()<previous.size()||!calls.subList(0,previous.size()).equals(previous))throw new IllegalStateException("Results PLC replaced native audit prefix");var suffix=calls.subList(previous.size(),calls.size());
  if(success?!suffix.equals(List.of(SonicCdAct3FutureLoadingVisit.Call.RESULTS_PLC_APPEND)):!(suffix.isEmpty()||suffix.equals(List.of(SonicCdAct3FutureLoadingVisit.Call.CURRENT_ART))))throw new IllegalStateException("Results PLC completion/audit mismatch");
 }
 // END R13 ACTUAL RESULTS PLC CORE NONWRITES 20261106


 }
 enum Boundary { LEVEL_FLOW, PRIMARY_DEATH_CAMERA, PRIMARY_LIFECYCLE, PRIMARY_DEATH_ALIAS, ACTIVE_POWERUP_CPU, MISSING_GLOBAL_ANIMATIONS, ACTIVE_POWERUP, PALETTE_SOURCE, GLOBAL_ANIMATIONS }
 static final class Stopped extends UnsupportedOperationException {private final Boundary boundary;private Stopped(Boundary b){super("R13 ordinary "+b);boundary=b;}Boundary boundary(){return boundary;}}

 static final class Unowned extends UnsupportedOperationException {
  private final Boundary boundary;
  Unowned(Boundary boundary){super("CD World unowned "+boundary);this.boundary=boundary;}
  Boundary boundary(){return boundary;}
 }
 private final class R31 extends Binding {
  private final SonicCdR31aWorldHost.Authority authority;
  private final SonicCdR31aLoadingVisit core;
  private final SonicCdR31aWorldHost host;
  private final CdR31aAssets assets;
  private final CdR31aSectionArt art;
  private final CdR31aObjectAssets metadata;
  private final CdPlayerObjectTouch scanner;
  private final CdPlayerTouchResponse response;
  private final SonicCdR31aObjectPass objects;
  private SonicCdR31aLoadingVisit.OrdinaryAccess active;
  private boolean walkReturned;
  R31(SonicCdR31aWorldHost.Authority authority,SonicCdR31aLoadingVisit core,SonicCdR31aWorldHost host,
      CdPlayerFrameRuntime frame,CdR31aAssets assets,CdR31aSectionArt art,CdR31aObjectAssets metadata)throws IOException{
   this.authority=Objects.requireNonNull(authority);this.core=Objects.requireNonNull(core);this.host=Objects.requireNonNull(host);
   this.assets=Objects.requireNonNull(assets);this.art=Objects.requireNonNull(art);this.metadata=Objects.requireNonNull(metadata);
   if(core.assets()!=assets||!frame.stageSource().ownsR31aAssets(assets)||!art.ownsAssets(assets)||!metadata.ownsAssets(assets))
    throw new IllegalArgumentException("Exact original R31 Core/Frame/source/art/metadata relation required");
   scanner=CdPlayerObjectTouch.fromR31aAssets(assets);response=CdPlayerTouchResponse.fromR31aAssets(assets);
   objects=new SonicCdR31aObjectPass(assets,frame,host.placement(),metadata,new ObjectPorts());
  }
  @Override void idle(){host.requireRenderIdle();host.requireOrdinaryWorld(authority,CdWorld.this);}
  @Override void require(){
   idle();if(active==null||frame.closed()||!frame.stageSource().ownsR31aAssets(assets))throw new IllegalStateException("No original R31 ordinary continuation");
   active.requireOwner(CdWorld.this);
  }
  @Override Object scope(){return active;}
  @Override CdOrdinaryLoop.Ports ports(){return new LoopPorts();}
  @Override void clear(){walkReturned=false;active=null;}
  @Override void discard(){objects.discard();}
  @Override void playerArt(CdPlayerArtRuntime.DmaRequest request,CdPlayerArtRuntime.State complete){
   requireCut(Cut.PUBLISH_LEVEL);
   if(request.vramByteAddress()!=0xF000||!Arrays.equals(request.bytes().copy(),complete.residentBank().slice(0,0x2E0).copy())
      ||!Arrays.equals(frame.artState().residentBank().copy(),complete.residentBank().copy()))throw new IllegalStateException("Original selector8 player publication changed");
  }
  private void adopt(SonicCdR31aWorldHost.Globals g,CdR31aSectionArt.State section,CdLevelGlobals level,CdAssetBytes reserved,
                     CdAssetBytes normal,CdAssetBytes displayed,List<SonicCdGameModule.PastSound> sounds,Optional<CdObjectDrawQueue.CpuSat> cpu){
   require();var before=active.state();var ready=active.prepare(before,section,level,reserved,normal,displayed);
   var hp=host.prepareOrdinary(host.globals(),host.video(),g,sounds,cpu);
   active.validatePrepared(before,ready);host.validateOrdinary(hp);
   active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
  }
  private void globals(SonicCdR31aWorldHost.Globals g,List<SonicCdGameModule.PastSound> sounds){
   var s=active.state();adopt(g,s.section(),s.level(),s.reserved(),s.normalPalette(),s.displayedPalette(),sounds,host.video().completedCpu());
  }
  private SonicCdR31aWorldHost.Globals worldValues(SonicCdR31aObjectPass.Cut next){
   var g=host.globals();var w=next.world();var r=next.ring();
   // Response world owns these scalar outputs; preserve every other ring field exactly.
   r=new CdRingGlobals(w.rings(),w.lives(),r.livesFlags(),r.updateHudRings(),w.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame());
   return SonicCdR31aWorldHost.with(g,r,g.live(),next.contact(),new CdOpeningObjectRuntime.ScoreGlobals(w.score(),w.nextLifeScore(),w.updateHudScore()),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
  }
  private void retainHud(CdLevelHud.R31State next){
   require();var g=host.globals();var ready=SonicCdR31aWorldHost.with(g,next.ring(),next.liveTime(),g.contact(),next.score(),g.pauseMusicBit(),next.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
   var hp=host.prepareOrdinary(g,host.video(),ready,List.of(),host.video().completedCpu());host.validateOrdinary(hp);host.adoptAfterNative(hp);
  }
  private void updateHud(){
   requireCut(Cut.UPDATE_HUD);var s=active.state();var g=host.globals();var e=frame.environment();var m=frame.state().motion();
   var input=new CdLevelHud.Inputs(new CdLevelHud.Globals(e.globals().debugCheat(),(g.pauseMusicBit().orElseThrow()|(frame.paused()?1:0))<<8,e.controls().controlLocked(),e.movement().gates().timeZone(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,e.movement().gates().zoneAct()),
       new CdLevelHud.DebugData(OptionalInt.of(host.placement().capture().savedFlags().unsignedByte(0)),s.layoutRam(),g.debugBlockWord()));
   try{retainHud(active.runHud(s,new CdLevelHud.R31State(s.section(),g.ring(),s.level(),g.score(),g.live(),g.bonus()),input).state());}
   catch(CdLevelHud.UnsupportedR31Hud original){verify(original,()->retainHud(original.state()));throw original;}
   catch(CdLevelHud.R31ArtFailure prefix){var original=prefix.original();verify(original,()->retainHud(prefix.state()));throw original;}
  }
  private void deathCamera(){
   requireCut(Cut.POST_OBJECT_CAMERA);if(!walkReturned)throw new IllegalStateException("No completed actual physical walk");
   var before=active.state();var actor=frame.state();var environment=frame.environment();var display=frame.display();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();var placement=host.placement();var placed=placement.capture();
   int cameraY=host.scroll().foreground().cameraYWord();
   var fp=frame.prepareLevelMainDeathCamera(frame.stageSource(),actor,environment,cameraY);var hp=host.prepareLevelMainDeathCamera(cameraY);
   active.validate(before);host.validatePrimaryDeathPublication(hp);frame.validatePreparedObjectSolidPublication(fp);
   var now=placement.capture();if(core.state()!=before||frame.state()!=actor||frame.environment()!=environment||frame.display()!=display||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags()))throw new IllegalStateException("Death camera changed an unowned completed-walk partition");
   frame.applyPreparedObjectSolidPublication(fp);host.adoptAfterNative(hp);
  }
  private final class LoopPorts implements CdOrdinaryLoop.Ports {
   public void requireCurrent(Cut at,CdPlayerFrameRuntime f,CdStagePlayerSource source){require();if(f!=frame||source!=frame.stageSource())throw new IllegalArgumentException("Foreign source-bound loop");cut=at;}
   public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){require();return host.beforeSelector8();}
   public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> selected){require();var s=active.state();host.publishOrdinary(s.normalPalette(),selected);adopt(host.globals(),s.section(),s.level(),s.reserved(),s.normalPalette(),s.normalPalette(),List.of(),host.video().completedCpu());}
   public void updateAnimTiles(){
    require();var s=active.state();var l=s.level();var gate=CdPowerupArtRuntime.gate(l.powerup());
    var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),gate.powerup(),l.timeOver());
    adopt(host.globals(),s.section(),next,s.reserved(),s.normalPalette(),s.displayedPalette(),List.of(),host.video().completedCpu());
    if(gate.copyRequired())throw new Unowned(Boundary.ACTIVE_POWERUP);
   }
   // Three finite same-Core delegates required; see core-port-contract.patch (not an owned Core replacement).
   public void copyCameraAndDrawLevel(){require();active.copyCameraAndDrawLevel(active.state());}
   public void serviceSlowPlc(){require();active.serviceSlowPlc(active.state(),host.scroll().foreground().scrollLock());}
   public void updateHud(){R31.this.updateHud();}
   public void mainMusic(){require();active.finishVInt(active.state());var g=host.globals();var result=CdMainMusic.step(g.pauseMusicBit().orElseThrow(),frame.paused(),frame.environment());globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),OptionalInt.of(result.pauseMusicBit()),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()),result.sounds());}
   public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> selected){require();host.settleOrdinary(selected);}
   public void spawnObjects(){require();host.placement().spawnObjects(host.scroll().foreground().cameraX(),frame.environment().movement().gates().timeZone());}
   public void runObjects(int levelFrame){require();walkReturned=false;objects.runWalk(levelFrame);walkReturned=true;}
   public CdOrdinaryLoop.MainContinuation postObjectCamera(){
    require();if(!walkReturned)throw new IllegalStateException("No completed actual physical walk");var s=active.state();
    if(s.program().demoDataIndexWord()==0x800||s.level().levelRestart()!=0){active.beginLevelStart(CdWorld.this);return CdOrdinaryLoop.MainContinuation.TRANSFERRED;}
    if(frame.environment().globals().debugMode()==0&&frame.state().motion().routine()>=6){deathCamera();return CdOrdinaryLoop.MainContinuation.ORDINARY_TAIL;}
    active.scroll(s);return CdOrdinaryLoop.MainContinuation.ORDINARY_TAIL;
   }
   public void drawObjects(){require();var s=host.scroll();var b=s.background();objects.finishDraw(new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(s.foreground().cameraX(),s.foreground().cameraYWord()),new CdObjectDrawQueue.CameraWords(b.cameraBgXWord(),b.cameraBgYWord()),new CdObjectDrawQueue.CameraWords(b.cameraBg3X()>>>16,b.cameraBg3Y()>>>16)));}
   public int timeStopWord(){requireCut(Cut.READ_TIME_STOP_WORD);return host.globals().ring().timeStopTimer();}
   public void paletteCycle(){
    requireCut(Cut.PALETTE_CYCLE);var s=active.state();var g=host.globals();var result=active.preparePaletteCycles(s,g.paletteTimers7(),g.paletteSteps7(),0);
    var next=SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),result.steps(),result.timers());
    adopt(next,s.section(),s.level(),s.reserved(),result.palette(),s.displayedPalette(),List.of(),host.video().completedCpu());
    if(result.fault().isPresent())throw new Unowned(Boundary.PALETTE_SOURCE);
   }
   public void updateSectionArt(){require();active.updateSection(active.state(),host.scroll().foreground().cameraX());}
   public void preparePlc(){require();active.preparePlc(active.state());}
   public void updateGlobalAnimations(){require();var g=host.globals();var out=CdGlobalObjectAnimation.step(g.animations().orElseThrow(()->new Unowned(Boundary.GLOBAL_ANIMATIONS)),g.ring());globals(SonicCdR31aWorldHost.with(g,out.ring(),g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),Optional.of(out.extras()),g.paletteSteps7(),g.paletteTimers7()),List.of());}
  }
  private SonicCdR31aObjectPass.Cut objectCut(){
   require();var g=host.globals();var f=host.scroll().foreground();var v=host.video();var r=g.ring();var score=g.score();
   return new SonicCdR31aObjectPass.Cut(active.state(),r,host.currentTerrain(),g.contact(),new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()),host.noBumperByte(),f.cameraX(),f.cameraYWord(),f.scrollLock(),g.layerByte(),v.cpuSat(),v.completedCpu(),host.sounds(),host.amyCapturedByte(),g.projectorByte());
  }
  private List<SonicCdGameModule.PastSound> soundSuffix(SonicCdR31aObjectPass.Cut next){
   var before=host.sounds();if(next.sounds().size()<before.size()||!next.sounds().subList(0,before.size()).equals(before))throw new IllegalArgumentException("Original sound prefix replaced");return next.sounds().subList(before.size(),next.sounds().size());
  }
  private final class ObjectPorts implements SonicCdR31aObjectPass.Ports {
   public void validate(SonicCdR31aObjectPass pass,CdR31aAssets a,CdPlayerFrameRuntime f,CdObjectPlacementRuntime placement,CdR31aObjectAssets m){
    require();if(pass!=objects||a!=assets||f!=frame||placement!=host.placement()||m!=metadata||(cut!=Cut.RUN_OBJECTS&&cut!=Cut.DRAW_OBJECTS))throw new IllegalStateException("No original physical R31 pass");
   }
   public SonicCdR31aObjectPass.Cut read(){return objectCut();}
   public void adopt(SonicCdR31aObjectPass.Adoption original){
    require();if(original.owner()!=objects)throw new IllegalArgumentException("Foreign actual pass publication");original.validateBefore();
    var next=original.next();var before=active.state();var candidate=next.core();
    var position=original.flowerPositionStore();var projected=position==null?before:position.project(before);
    var scalar=original.gameOverScalar();var flowerCount=original.flowerCountStore();var level=position!=null?projected.level():flowerCount!=null?SonicCdR31aObjectPass.flowerCountLevel(before.level(),flowerCount):scalar==null?before.level():SonicCdR31aObjectPass.gameOverLevel(before.level(),scalar);
    if(!candidate.flowerPositions().equals(projected.flowerPositions())||position!=null&&!candidate.equals(projected))throw new IllegalArgumentException("Pass changed nonoriginal Flower position fields");
    if(!candidate.level().equals(level))throw new IllegalArgumentException("Pass changed nonoriginal Game Over level fields");
    if(candidate.phase()!=before.phase()||candidate.initialization()!=before.initialization()||candidate.section()!=before.section()||candidate.program()!=before.program()||(scalar==null&&flowerCount==null&&position==null&&candidate.level()!=before.level())||(!original.introPalette()&&candidate.normalPalette()!=before.normalPalette())||candidate.fadePalette()!=before.fadePalette()||candidate.waterPalette()!=before.waterPalette()||candidate.displayedPalette()!=before.displayedPalette()||candidate.blocksRam()!=before.blocksRam()||candidate.layoutRam()!=before.layoutRam()||candidate.initialFadeSteps()!=before.initialFadeSteps()||candidate.fromFadeSteps()!=before.fromFadeSteps()||candidate.displayEnabled()!=before.displayEnabled()||!candidate.calls().equals(before.calls())||candidate.returnKind()!=before.returnKind()||candidate.music()!=before.music())throw new IllegalArgumentException("Pass changed unowned Core fields");
    if(next.currentTerrain()!=host.currentTerrain()||!next.noBumperByte().equals(host.noBumperByte()))throw new IllegalArgumentException("Nonprimary pass changed terrain/scratch");
    var ready=position==null?active.prepare(before,before.section(),level,candidate.reserved(),candidate.normalPalette(),before.displayedPalette()):active.prepareFlowerPosition(before,position);
    var hp=host.prepareOrdinary(host.globals(),host.video(),worldValues(next),soundSuffix(next),next.completedCpu());
    hp=host.prepareAmyCaptured(hp,next.amyCapturedByte());
    if(next.scrollLockWord()!=host.scroll().foreground().scrollLock())hp=host.prepareScrollLock(hp,next.scrollLockWord());
    active.validatePrepared(before,ready);host.validateOrdinary(hp);original.validateBefore();original.applyNative();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
   }
   public void bumper(SonicCdR31aObjectPass.BumperPublication original){pinball(original);}
   public void pinball(SonicCdR31aObjectPass.PinballPublication original){
    requireCut(Cut.RUN_OBJECTS);if(original.owner()!=objects)throw new IllegalArgumentException("Foreign Bumper physical owner");original.validateBefore();
    var before=objectCut();if(!SonicCdR31aObjectPass.sameCutForBumper(before,original.expected()))throw new IllegalArgumentException("Changed complete Bumper Core/Host cut");
    // Every Bumper operation changes one owner. Core/reserved and the other owners are fenced,
    // so no redundant Core/Host assignments follow a pool/Frame/saved write.
    if(original.changesHost()){
     var next=original.next();var hp=host.prepareOrdinary(host.globals(),host.video(),worldValues(next),soundSuffix(next),next.completedCpu());
     host.validateOrdinary(hp);original.validateBefore();original.applyNative();host.adoptAfterNative(hp);original.acknowledge();
    }else{original.validateBefore();original.applyNative();original.acknowledge();}
   }
   public CdPlayerBody.R31Context primaryContext(){
    requireCut(Cut.RUN_OBJECTS);var c=objectCut();var state=frame.state();var pool=host.placement().capture().pool();
    return new CdPlayerBody.R31Context(frame.stageSource(),assets,c.currentTerrain(),pool,Optional.of(new CdPlayerMovement.ObjectContext(pool,state.objectLinks().standObject(),state.header().slotId(),state.header().width())),scanner,new CdPlayerBody.ResponseContext(response,c.contact(),c.world()),c.noBumperByte(),host.bossStartedByte(),OptionalInt.of(active.state().program().goodFutureByte()),c.cameraXWord(),c.cameraYWord());
   }
   public void placeBlock(SonicCdR31aObjectPass pass,CdPlayerFrameRuntime.R31PlaceBlockCall original){
    requirePlace(original);if(pass!=objects)throw new IllegalArgumentException("Foreign actual physical pass");
    var before=active.state();var next=objects.preparedPlace(original);
    var ready=active.prepare(before,before.section(),before.level(),before.reserved(),before.normalPalette(),before.displayedPalette());
    var hp=host.preparePlaceBlock(original,worldValues(next),soundSuffix(next));
    active.validatePrepared(before,ready);host.validateOrdinary(hp);objects.requirePlace(original);
    objects.applyPlace(original);active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
   }
   public void placeScreen(SonicCdR31aObjectPass pass,CdPlayerFrameRuntime.R31PlaceBlockScreenCall original){
    requirePlace(original);if(pass!=objects)throw new IllegalArgumentException("Foreign actual physical pass");
    var before=active.state();var hp=host.preparePlaceScreenFence(original);
    active.validate(before);host.validateOrdinary(hp);objects.requirePlace(original);objects.verifyPlaceScreen(original);
   }
   public void primary(SonicCdR31aObjectPass.PrimarySubmission original){
    requirePrimary(original);var before=active.state();var next=original.next();
    var ready=active.prepare(before,before.section(),next.core().level(),before.reserved(),before.normalPalette(),before.displayedPalette());
    var hp=host.preparePrimary(original,worldValues(next),soundSuffix(next));
    active.validatePrepared(before,ready);host.validateOrdinary(hp);original.validateBefore();original.applyNative();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
   }
   public void submitIntroPlc(SonicCdR31aObjectPass.IntroPlcSubmission original){requireCut(Cut.RUN_OBJECTS);if(original.owner()!=objects)throw new IllegalArgumentException("Foreign original Amy PLC11");original.validateBefore();active.appendIntroPlc(active.state());original.acknowledge();}
   public void submitTitlePlc(SonicCdR31aObjectPass.PlcSubmission original){requireCut(Cut.RUN_OBJECTS);if(original.owner()!=objects||original.nativeId()!=2)throw new IllegalArgumentException("Foreign actual Title PLC2");original.validateBefore();active.appendTitlePlc(active.state());original.acknowledge();}
  }
 }
 private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
 private static void verify(Throwable original,Runnable verification){try{verification.run();}catch(RuntimeException|Error fault){if(fault!=original)original.addSuppressed(fault);}}

}
