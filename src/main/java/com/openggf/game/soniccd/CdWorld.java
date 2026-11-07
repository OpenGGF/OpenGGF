package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
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
 SonicCdAct3FutureLoadingVisit.Snapshot captureR13(){idle();if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign capture source");return b.core.capture();}
 SonicCdR31aLoadingVisit.Snapshot captureR31(){idle();if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign capture source");return b.core.capture();}
 void validateRestore(SonicCdAct3FutureLoadingVisit.Snapshot snapshot){idle();if(!(binding instanceof R13 b))throw new IllegalArgumentException("Foreign capture source");b.core.validateRestore(snapshot);}
 void validateRestore(SonicCdR31aLoadingVisit.Snapshot snapshot){idle();if(!(binding instanceof R31 b))throw new IllegalArgumentException("Foreign capture source");b.core.validateRestore(snapshot);}
 void restore(SonicCdAct3FutureLoadingVisit.Snapshot snapshot){validateRestore(snapshot);((R13)binding).core.restore(snapshot);}
 void restore(SonicCdR31aLoadingVisit.Snapshot snapshot){validateRestore(snapshot);((R31)binding).core.restore(snapshot);}
 void draw(){idle();if(binding instanceof R13 b)b.host.draw();else ((R31)binding).host.draw();}
 /** Idle lifecycle guards also cover loading snapshots; active Core accesses are never captured. */
 void requireLifecycleIdle(){idle();}
 /** Retirement also runs after the owning session detached or the Frame failed. Validate
  * retained exact relations, thread and render-idle before cleanup; never require a new session. */
 void close(){
  if(Thread.currentThread()!=thread||running)throw new IllegalStateException("World close thread/reentry");
  if(binding instanceof R13 b){
   b.host.requireRenderIdle();if(frame!=b.frame||b.core.assets()!=b.assets||!frame.stageSource().ownsAct3Future(b.assets)||!b.art.ownsAssets(b.assets))throw new IllegalStateException("Retiring foreign R13 pair");
  }else{var b=(R31)binding;b.host.requireRenderIdle();if(b.core.assets()!=b.assets||!frame.stageSource().ownsR31aAssets(b.assets)||!b.art.ownsAssets(b.assets))throw new IllegalStateException("Retiring foreign R31 pair");}
  Throwable original=null;
  try{binding.discard();}catch(RuntimeException|Error fault){original=fault;}
  try{if(binding instanceof R13 b)b.host.close();else ((R31)binding).host.close();}
  catch(RuntimeException|Error fault){if(original==null)original=fault;else if(original!=fault)original.addSuppressed(fault);}
  try{frame.close();}catch(RuntimeException|Error fault){if(original==null)original=fault;else if(original!=fault)original.addSuppressed(fault);}
  closed=true;
  if(original instanceof RuntimeException fault)throw fault;if(original instanceof Error fault)throw fault;
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
  var fp=frame.prepareAct3FutureLevelMainDeathCamera(assets,actor,environment,cameraY);var hp=host.prepareLevelMainDeathCamera(cameraY);
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
  public void mainMusic(){
   require();host.serviceVInt();var g=host.globals();int old=g.pauseMusicBit().orElseThrow();boolean paused=frame.paused();var sounds=new ArrayList<SonicCdGameModule.PastSound>();
   if(paused)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)));
   if(paused?((old&128)==0):((old&128)!=0)){var e=frame.environment();boolean pcm=(e.movement().gates().timeZone()&127)==0&&e.globals().invincible()==0&&e.movement().gates().speedShoes()==0;var command=paused?(pcm?SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA):(pcm?SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA);sounds.add(new SonicCdGameModule.PastSubCpuSound(command));}
   globals(SonicCdAct3FutureWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),OptionalInt.of(paused?old|128:old&127),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()),sounds);
  }
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
 private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
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
  var result=publication.result();boolean dead=result.boundary()==CdPlayerBody.Boundary.DEAD_RETURN;
  var placement=host.placement();var placed=placement.capture();var artImage=frame.artState();var clock=frame.vIntClock();boolean paused=frame.paused();
  if(!placed.pool().slots().equals(pool.slots()))throw new IllegalArgumentException("Stale lifecycle full96 input");
  if(!dead&&result.boundary()!=CdPlayerBody.Boundary.RESTART_RETURN)throw new IllegalArgumentException("Not an original lifecycle return");
  CdPlayerBody.DeadCheckGone gone=null;CdPlayerBody.RestartCut restart=null;int resets=0;var aliases=new ArrayList<CdPlayerBody.UncheckedAliasWrite>();var driver=new ArrayList<CdPlayerBody.Z80RamWrite>();
  for(var effect:result.effects()){
   if(effect instanceof CdPlayerBody.DeadCheckGone v){if(gone!=null||restart!=null||!dead)throw new IllegalArgumentException("Repeated/foreign death summary");gone=v;}
   else if(effect instanceof CdPlayerBody.RestartCut v){if(gone!=null||restart!=null||dead)throw new IllegalArgumentException("Repeated/foreign restart summary");restart=v;}
   else if(effect instanceof CdPlayerBody.ResetSavedObjectFlagsRequest)resets++;
   else if(effect instanceof CdPlayerBody.UncheckedAliasWrite v)aliases.add(v);
   else if(effect instanceof CdPlayerBody.Z80RamWrite v)driver.add(v);
   else if(effect instanceof CdPlayerBody.ObjectTouchResult||effect instanceof CdPlayerBody.ObjectTouchResponse||effect instanceof CdPlayerBody.ObjectBossTouchResponse||effect instanceof CdPlayerBody.TimeWarpCall||effect instanceof CdPlayerBody.ModeEffect)throw new IllegalArgumentException("Lifecycle return mixed another primary owner");
  }
  var ring=g.ring();var level=before.level();var nextPool=pool;boolean alias=false;
  if(gone!=null){
   if(resets!=0||!driver.isEmpty()||gone.updateHudTime()!=0)throw new IllegalArgumentException("Death summary changed restart nonwrites");
   var found=gone.allocation().map(CdPlayerTouchResponse.FindObjSlot::wholeIndex).orElse(OptionalInt.empty());
   if(gone.allocation().isPresent()){
    OptionalInt first=OptionalInt.empty();for(var slot:pool.slots())if(slot.id()==0){first=OptionalInt.of(slot.identity().wholeIndex());break;}
    if(!first.equals(found))throw new IllegalArgumentException("Death allocation is not the native first-free slot");
   }
   alias=gone.allocation().isPresent()&&found.isEmpty();
   var expectedAliases=alias?List.of(new CdPlayerBody.UncheckedAliasWrite(0xFFF000,0x3B)):List.of();
   if(!aliases.equals(expectedAliases))throw new IllegalArgumentException("Death alias differs from actual exhausted allocation");
   if(gone.gameOverImage().isPresent()){
    var image=gone.gameOverImage().orElseThrow();var expected=pool.slots().get(image.identity().dynamicIndex()).moveByte(CdObjectState.ByteField.ID,0x3B);
    if(!image.equals(expected))throw new IllegalArgumentException("Death allocation changed bytes other than oID");
    nextPool=CdObjectPool.overlay(pool,List.of(image));
   }
   ring=new CdRingGlobals(ring.rings(),gone.lives(),ring.livesFlags(),ring.updateHudRings(),gone.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame());
   level=new CdLevelGlobals(level.levelRestart(),gone.updateHudTime(),level.checkpoint(),level.spawnMode(),level.plcLoadFlags(),level.flowerCount(),level.fadeEnableDisplay(),level.powerup(),level.timeOver());
  }else if(restart!=null){
   var next=restart.next();if(!restart.resetSavedObjectFlags()||resets!=1||next.levelRestart()!=1||next.flowerCount()!=0||next.fadeEnableDisplay()!=0||!aliases.isEmpty()||!driver.equals(List.of(new CdPlayerBody.Z80RamWrite(0x1C3E,1))))throw new IllegalArgumentException("Restart omitted its native complete publication");
   level=next;
  }else if(resets!=0||!aliases.isEmpty()||!driver.isEmpty()||result.effects().stream().anyMatch(CdPlayerBody.LevelWrite.class::isInstance))throw new IllegalArgumentException("Lifecycle writes omitted their one summary");
  var sounds=new ArrayList<SonicCdGameModule.PastSound>();for(var sound:publication.sounds())sounds.add(new SonicCdGameModule.PastFrameSound(sound));
  var globals=SonicCdAct3FutureWorldHost.with(g,ring,g.live(),g.contact(),g.score(),g.pauseMusicBit(),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7());
  var ready=active.prepare(before,before.section(),level,before.hudSlots(),before.normalPalette(),before.displayedPalette());var hp=host.prepareOrdinary(g,host.video(),globals,sounds,host.video().completedCpu());
  placement.pool().validateRestore(nextPool);active.validate(before);host.validatePrimaryDeathPublication(hp);
  var now=placement.capture();if(core.state()!=before||frame.state()!=result.state()||frame.environment()!=result.environment()||frame.display()!=result.display()||frame.artState()!=artImage||frame.vIntClock()!=clock||frame.paused()!=paused||!placed.status().equals(now.status())||!placed.pool().slots().equals(now.pool().slots())||!same(placed.savedFlags(),now.savedFlags()))throw new IllegalStateException("Lifecycle changed another full actual owner during preparation");
  // All prebuilt values/ownership checks precede this callback-free native commit prefix.
  placement.pool().restore(nextPool);if(restart!=null)placement.resetSavedObjectFlags();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
  // The original whole Body is atomic; no buffer/slot stands in for the unowned $FFF000 target.
  // Its completed actor/global prefix is retained, but there is no successful PrimaryCall/Draw/physical tail.
  if(alias)throw new Stopped(Boundary.PRIMARY_DEATH_ALIAS);
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
   public void mainMusic(){
    require();active.finishVInt(active.state());var g=host.globals();int old=g.pauseMusicBit().orElseThrow();boolean paused=frame.paused();
    var sounds=new ArrayList<SonicCdGameModule.PastSound>();
    if(paused)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)));
    if(paused?((old&128)==0):((old&128)!=0)){
     var e=frame.environment();boolean pcm=(e.movement().gates().timeZone()&127)==0&&e.globals().invincible()==0&&e.movement().gates().speedShoes()==0;
     var command=paused?(pcm?SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA):(pcm?SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA);
     sounds.add(new SonicCdGameModule.PastSubCpuSound(command));
    }
    globals(SonicCdR31aWorldHost.with(g,g.ring(),g.live(),g.contact(),g.score(),OptionalInt.of(paused?old|128:old&127),g.bonus(),g.animations(),g.paletteSteps7(),g.paletteTimers7()),sounds);
   }
   public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> selected){require();host.settleOrdinary(selected);}
   public void spawnObjects(){require();host.placement().spawnObjects(host.scroll().foreground().cameraX(),frame.environment().movement().gates().timeZone());}
   public void runObjects(int levelFrame){require();walkReturned=false;objects.runWalk(levelFrame);walkReturned=true;}
   public CdOrdinaryLoop.MainContinuation postObjectCamera(){
    require();if(!walkReturned)throw new IllegalStateException("No completed actual physical walk");var s=active.state();
    if(s.program().demoDataIndexWord()==0x800||s.level().levelRestart()!=0)throw new Unowned(Boundary.LEVEL_FLOW);
    if(frame.environment().globals().debugMode()==0&&frame.state().motion().routine()>=6)throw new Unowned(Boundary.PRIMARY_DEATH_CAMERA);
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
   return new SonicCdR31aObjectPass.Cut(active.state(),r,host.currentTerrain(),g.contact(),new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),score.score(),score.nextLifeScore(),score.updateHudScore()),host.noBumperByte(),f.cameraX(),f.cameraYWord(),f.scrollLock(),g.layerByte(),v.cpuSat(),v.completedCpu(),host.sounds());
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
    if(candidate.phase()!=before.phase()||candidate.initialization()!=before.initialization()||candidate.section()!=before.section()||candidate.program()!=before.program()||candidate.level()!=before.level()||candidate.normalPalette()!=before.normalPalette()||candidate.fadePalette()!=before.fadePalette()||candidate.waterPalette()!=before.waterPalette()||candidate.displayedPalette()!=before.displayedPalette()||candidate.blocksRam()!=before.blocksRam()||candidate.layoutRam()!=before.layoutRam()||candidate.initialFadeSteps()!=before.initialFadeSteps()||candidate.fromFadeSteps()!=before.fromFadeSteps()||candidate.displayEnabled()!=before.displayEnabled()||!candidate.calls().equals(before.calls())||candidate.returnKind()!=before.returnKind()||candidate.music()!=before.music())throw new IllegalArgumentException("Pass changed unowned Core fields");
    if(next.currentTerrain()!=host.currentTerrain()||!next.noBumperByte().equals(host.noBumperByte()))throw new IllegalArgumentException("Nonprimary pass changed terrain/scratch");
    var ready=active.prepare(before,before.section(),before.level(),candidate.reserved(),before.normalPalette(),before.displayedPalette());
    var hp=host.prepareOrdinary(host.globals(),host.video(),worldValues(next),soundSuffix(next),next.completedCpu());
    if(next.scrollLockWord()!=host.scroll().foreground().scrollLock())hp=host.prepareScrollLock(hp,next.scrollLockWord());
    active.validatePrepared(before,ready);host.validateOrdinary(hp);original.validateBefore();original.applyNative();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
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
    var ready=active.prepare(before,before.section(),before.level(),before.reserved(),before.normalPalette(),before.displayedPalette());
    var hp=host.preparePrimary(original,worldValues(next),soundSuffix(next));
    active.validatePrepared(before,ready);host.validateOrdinary(hp);original.validateBefore();original.applyNative();active.adoptAfterNative(before,ready);host.adoptAfterNative(hp);
   }
   public void submitTitlePlc(SonicCdR31aObjectPass.PlcSubmission original){requireCut(Cut.RUN_OBJECTS);if(original.owner()!=objects||original.nativeId()!=2)throw new IllegalArgumentException("Foreign actual Title PLC2");original.validateBefore();active.appendTitlePlc(active.state());original.acknowledge();}
  }
 }
 private static void verify(Throwable original,Runnable verification){try{verification.run();}catch(RuntimeException|Error fault){if(fault!=original)original.addSuppressed(fault);}}

}
