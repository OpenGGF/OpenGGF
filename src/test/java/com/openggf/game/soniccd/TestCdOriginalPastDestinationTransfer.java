package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.scene.CdWarpScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict original-controller requirements written before the destination implementation.
 * Constructed metadata never enters a source. All Java/runtime checks remain UNRUN. */
@Isolated class TestCdOriginalPastDestinationTransfer extends TestCdOriginalR11LoadingWorld {
 @Test void actualWarpRequestLoadsItsSameReaderPastThroughReadyAndFirstOrdinaryThenReplays()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);toDestination(loop,m);
   var requested=m.originalWarpDestination().orElseThrow();assertEquals(CdCommand.R11B,requested.command());
   var old=m.originalR11EntryFrame();var oldLevel=SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel();var manager=SessionManager.getCurrentGameplayMode().getLevelManager();long generation=manager.getCompletedProductionLoadGeneration();
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var oldFull=registry.capture();var oldCourse=registry.captureCourse();var saved=m.originalR11TimeWarpSavedImage();var callsBefore=m.programServicesState().completedCalls();var soundsBefore=m.programSoundRequests();
   var beforeRefusals=image(m);var offThread=new java.util.concurrent.atomic.AtomicReference<Throwable>();
   var foreign=new Thread(()->{try{m.serviceOriginalR11Loading(0);}catch(Throwable fault){offThread.set(fault);}});foreign.start();foreign.join();
   assertInstanceOf(IllegalStateException.class,offThread.get());CdCapturedValueAssertions.assertCapturedValueEquals(beforeRefusals,image(m));assertFalse(old.closed());
   var generationField=SonicCdGameModule.class.getDeclaredField("r11EntryLoadGeneration");generationField.setAccessible(true);var generationValue=generationField.get(m);
   generationField.set(m,java.util.OptionalLong.of(generation+1));try{assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));assertFalse(old.closed());}finally{generationField.set(m,generationValue);}
   CdCapturedValueAssertions.assertCapturedValueEquals(beforeRefusals,image(m));
   var sourceField=SonicCdGameModule.class.getDeclaredField("originalLoadingSource");sourceField.setAccessible(true);var originalSource=sourceField.get(m);
   sourceField.set(m,requested.loadingSource().orElseThrow());try{assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));assertFalse(old.closed());}finally{sourceField.set(m,originalSource);}
   CdCapturedValueAssertions.assertCapturedValueEquals(beforeRefusals,image(m));
   var context=SonicCdLevelInitProfile.staticStageContext();assertThrows(RuntimeException.class,()->m.validateSelectedLoadingRequest(context));CdCapturedValueAssertions.assertCapturedValueEquals(beforeRefusals,image(m));
   step(loop,0);
   var actual=m.originalR11EntryFrame();assertNotSame(old,actual);assertTrue(old.closed(),"The old physical World/Frame must retire exactly once");assertNotSame(oldLevel,manager.getCurrentLevel());assertEquals(generation+1,manager.getCompletedProductionLoadGeneration());
   var loading=requested.loadingSource().orElseThrow();assertSame(loading.retainedPlayerSource(),actual.stageSource());assertEquals(CdStagePayload.R11B.stage(),actual.stageSource().stage());assertEquals(0x80,actual.environment().movement().gates().timeZone(),"Main315 alone clears the marker");assertSame(saved,m.originalR11TimeWarpSavedImage());
   var calls=m.programServicesState().completedCalls();assertEquals(callsBefore,calls.subList(0,callsBefore.size()));assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),calls.subList(callsBefore.size(),calls.size()));assertEquals(soundsBefore,m.programSoundRequests(),"Arrival must not replay STOP_FM or WARP sound effects");
   assertEquals(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,m.originalR11LoadingImage().phase());assertEquals(SonicCdGameModule.OriginalWarpPhase.NONE,m.originalWarpPhase());
   var privatePort=Arrays.stream(SonicCdGameModule.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("OriginalR11WarpLoadCall")).findFirst().orElseThrow();var constructor=privatePort.getDeclaredConstructor(SonicCdGameModule.class);constructor.setAccessible(true);
   var idlePort=(com.openggf.game.soniccd.objects.CdTimeWarpLoadData.Port)constructor.newInstance(m);var idleBefore=image(m);
   assertThrows(IllegalStateException.class,idlePort::currentSaved);assertThrows(IllegalStateException.class,()->idlePort.read(com.openggf.game.soniccd.objects.CdTimeWarpLoadData.StoreTarget.PRIMARY_X));
   assertThrows(IllegalStateException.class,()->idlePort.commit(new com.openggf.game.soniccd.objects.CdTimeWarpLoadData.StoreCall(com.openggf.game.soniccd.objects.CdTimeWarpLoadData.NativeStore.RESTORE_X,OptionalInt.of(0),1)));
   CdCapturedValueAssertions.assertCapturedValueEquals(idleBefore,image(m));
   for(int n=0;n<256&&m.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase(),"A typed loading stop cannot satisfy Ready");assertEquals(0,actual.environment().movement().gates().timeZone());assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());
   step(loop,0);assertEquals(1,actual.environment().globals().levelFrames());assertEquals(2,actual.state().motion().routine());assertFalse(actual.closed());
   var full=registry.capture();var course=registry.captureCourse();var returned=new ArrayList<Object>();for(int n=0;n<3;n++){step(loop,0);returned.add(image(m));}
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);for(int n=0;n<3;n++){step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(returned.get(n),image(m));}}
   var before=image(m);var current=manager.getCurrentLevel();assertThrows(RuntimeException.class,()->registry.restore(oldFull));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));assertSame(current,manager.getCurrentLevel());assertThrows(RuntimeException.class,()->registry.restoreCourse(oldCourse));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 @Test void repeatedRunMmdClearRetainsMainvarsAndClearsOnlyTheOwnedWorkProjection(){
  var old=nonzero();var out=CdOriginalProgrammeWorkClear.environment(old);
  assertEquals(old.movement().gates().zoneAct(),out.movement().gates().zoneAct());assertEquals(old.movement().gates().timeZone(),out.movement().gates().timeZone());assertEquals(old.globals().levelFrames(),out.globals().levelFrames());assertEquals(old.movement().globals().debugFloorDistance(),out.movement().globals().debugFloorDistance());assertEquals(old.globals().invincible(),out.globals().invincible());
  assertEquals(new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0,0,0),out.movement().physics());assertEquals(new com.openggf.game.soniccd.player.CdPlayerBody.Controls(0,0,0,0,0),out.controls());assertEquals(0,out.globals().timeWarpTimer());assertEquals(0,out.movement().gates().timeWarpDirection());
 }
 @Test void immutableLoadingDataDoesNotGrantOriginalDestinationAssembly()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var loading=source.initialProgramSource().orElseThrow().pastLoadingSource();assertEquals(CdStagePayload.R11B.stage(),loading.stage());
  var m=new SonicCdGameModule();var context=SonicCdLevelInitProfile.staticStageContext();assertThrows(RuntimeException.class,()->m.validateSelectedLoadingRequest(context));
 }
 @Test void markedComponentBirthCannotInvokeStandardServiceOrBorrowAnExternalOwner()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var loading=source.initialProgramSource().orElseThrow().pastLoadingSource();
  var actor=CdOriginalProgrammeWorkClear.primary();var env=CdOriginalProgrammeWorkClear.environment(nonzero());
  var art=new com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State(new byte[com.openggf.game.soniccd.resources.CdPlayerArtRuntime.CPU_BUFFER_BYTES],new byte[com.openggf.game.soniccd.resources.CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES],0,false);
  var frame=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.fromMarkedProgrammeEntry(loading.retainedPlayerSource(),actor,env,art,new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.empty(),0));
  var before=frame.captureActor();var shared=frame.captureShared();
  var owner=(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.OriginalProgrammeOwner)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{com.openggf.game.soniccd.player.CdPlayerFrameRuntime.OriginalProgrammeOwner.class},(proxy,method,args)->{throw new AssertionError("Marked refusal must precede any external callback: "+method.getName());});
  assertThrows(RuntimeException.class,()->frame.serviceOriginalStandardProgrammeVBlank(com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL,owner));assertFalse(frame.closed());assertEquals(before,frame.captureActor());assertEquals(shared,frame.captureShared());frame.close();
 }
 private static com.openggf.game.soniccd.player.CdPlayerBody.Environment nonzero(){
  return new com.openggf.game.soniccd.player.CdPlayerBody.Environment(new com.openggf.game.soniccd.player.CdPlayerMovement.Environment(
   new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(0x600,0x18,0x80),new com.openggf.game.soniccd.player.CdPlayerMovement.Bounds(0,6000,2048,0),
   new com.openggf.game.soniccd.player.CdPlayerMovement.Gates(0,0x80,1,2,3,4,0xFF),new com.openggf.game.soniccd.player.CdPlayerMovement.Globals(1,96,3,4,5,6,7,8,0,10)),
   new com.openggf.game.soniccd.player.CdPlayerBody.Controls(8,0,8,0,0),new com.openggf.game.soniccd.player.CdPlayerBody.Globals(29,160,1,0,1,211,2,3),com.openggf.game.soniccd.player.CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalR11WorldImage(),m.programServicesState(),m.originalR11TimeWarpSavedImage(),m.programSoundRequests());}
 private static void toDestination(com.openggf.GameLoop loop,SonicCdGameModule m){
  for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
  for(int n=0;n<6351;n++)step(loop,((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8);
  for(int n=0;n<22;n++)step(loop,8);step(loop,0);step(loop,0);for(int n=0;n<90;n++)step(loop,0);step(loop,0);for(int n=0;n<24;n++)step(loop,0);assertEquals(CdWarpScene.Phase.MAIN,m.warpSceneState().orElseThrow().phase());step(loop,0x80);for(int n=0;n<24;n++)step(loop,0);step(loop,0);step(loop,0);assertEquals(SonicCdGameModule.OriginalWarpPhase.DESTINATION_SELECTED,m.originalWarpPhase());
 }
}
