package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.scene.CdWarpScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import static org.junit.jupiter.api.Assertions.*;

/** Strict actual original ingress. No copied phase/pose/scene opens the WARP programme. */
@Isolated class TestCdOriginalSelectedWarpProgramme extends TestCdOriginalR11LoadingWorld {
 @Test void actualMarkedRtsOwnsReturnBlankThenWarpLoopAndBothRewindKinds() throws Exception {
  var source=TestCdOriginalBootPrefix.source();
  var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
  var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);IntConsumer run=held->step(loop,held);
   readyAndActualMarkedRts(m,run);
   var frame=m.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();
   var bank=m.originalR11TimeWarpSavedImage();var world=m.originalR11WorldImage();var levelClock=frame.vIntClock();
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var rtsFull=registry.capture();var rtsCourse=registry.captureCourse();
   assertEquals(SonicCdGameModule.OriginalWarpPhase.LEVEL_RETURN_TAIL,m.originalWarpPhase());
   assertTrue(m.warpSceneState().isEmpty());
   run.accept(8); // only the actual level RunMMD return/FM/blank prefix; no scene pulse
   assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM),m.programSoundRequests());
   assertTrue(m.warpSceneState().isEmpty());assertFalse(m.originalR11LoadingImage().displayEnabled());
   var blankFull=registry.capture();var blankCourse=registry.captureCourse();
   run.accept(8);assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_ACTIVE,m.originalWarpPhase());
   var initial=m.warpSceneState().orElseThrow();assertEquals(CdWarpScene.Phase.DELAY,initial.phase());
   assertEquals(90,initial.timerWord());assertEquals(0,initial.vIntCounterWord());assertTrue(initial.heldByte().isEmpty());
   var initialFull=registry.capture();var initialCourse=registry.captureCourse();
   for(int n=0;n<90;n++)run.accept(0x80);
   assertEquals(CdWarpScene.Phase.PREPARE,m.warpSceneState().orElseThrow().phase());
   assertEquals(90,m.warpSceneState().orElseThrow().vIntCounterWord());assertTrue(m.warpSceneState().orElseThrow().heldByte().isEmpty());
   run.accept(0);assertEquals(CdWarpScene.Phase.FADE_IN,m.warpSceneState().orElseThrow().phase());
   assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8),m.programSoundRequests());
   for(int n=0;n<24;n++)run.accept(0);
   assertEquals(CdWarpScene.Phase.MAIN,m.warpSceneState().orElseThrow().phase());
   var main=m.warpSceneState().orElseThrow();var mainFull=registry.capture();var mainCourse=registry.captureCourse();
   assertRejectedIdleAndWrongThread(m);
   var beforeDraw=image(m);var callbacks=new java.util.concurrent.atomic.AtomicInteger();
   com.openggf.game.GameServices.graphics().registerCommand((x,y,width,height)->{
    assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));
    assertThrows(RuntimeException.class,()->registry.capture());
    assertThrows(RuntimeException.class,()->registry.restore(mainFull));
    assertThrows(RuntimeException.class,()->registry.restoreCourse(mainCourse));
    assertThrows(IllegalStateException.class,()->m.gameplayFrameController().drawOverlay());callbacks.incrementAndGet();
   });
   m.gameplayFrameController().drawOverlay();m.gameplayFrameController().drawOverlay();assertEquals(1,callbacks.get());
   CdCapturedValueAssertions.assertCapturedValueEquals(beforeDraw,image(m));assertFalse(frame.closed());
   run.accept(0x80);assertEquals(CdWarpScene.Phase.FADE_OUT,m.warpSceneState().orElseThrow().phase());
   for(int n=0;n<24;n++)run.accept(0);
   assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_RETURNED,m.originalWarpPhase());
   assertEquals(CdWarpScene.Phase.RETURNED,m.warpSceneState().orElseThrow().phase());
   var returned=image(m);var returnedFull=registry.capture();var returnedCourse=registry.captureCourse();
   assertFalse(frame.closed());assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(levelClock,frame.vIntClock());
   CdCapturedValueAssertions.assertCapturedValueEquals(actor,frame.captureActor());
   CdCapturedValueAssertions.assertCapturedValueEquals(shared,frame.captureShared());
   CdCapturedValueAssertions.assertCapturedValueEquals(world,m.originalR11WorldImage());
   for(boolean course:new boolean[]{false,true}) {
    if(course)registry.restoreCourse(rtsCourse);else registry.restore(rtsFull);
    assertTrue(m.warpSceneState().isEmpty());run.accept(8);if(course)registry.restoreCourse(blankCourse);else registry.restore(blankFull);
    run.accept(8);assertEquals(initial,m.warpSceneState().orElseThrow());
    if(course)registry.restoreCourse(initialCourse);else registry.restore(initialFull);
    for(int n=0;n<90;n++)run.accept(0x80);run.accept(0);for(int n=0;n<24;n++)run.accept(0);
    assertEquals(main,m.warpSceneState().orElseThrow());
    if(course)registry.restoreCourse(mainCourse);else registry.restore(mainFull);
    run.accept(0x80);for(int n=0;n<24;n++)run.accept(0);
    CdCapturedValueAssertions.assertCapturedValueEquals(returned,image(m));
    if(course)registry.restoreCourse(returnedCourse);else registry.restore(returnedFull);
    CdCapturedValueAssertions.assertCapturedValueEquals(returned,image(m));
   }
   // The genuine returned scene now owns its distinct UNKNOWN/FM/IPX-blank tail.
   run.accept(0);assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_IPX_TAIL,m.originalWarpPhase());assertTrue(m.originalWarpReturnKnowledge().value().isEmpty());
   run.accept(0);assertEquals(SonicCdGameModule.OriginalWarpPhase.DESTINATION_SELECTED,m.originalWarpPhase());assertEquals(CdCommand.R11B,m.originalWarpDestination().orElseThrow().command());
   // The next actual public service consumes this private R11B request and returns
   // the new marked FIRST_PLC cut; it does not execute a loading wait, Ready or Main.
   var arrivalRequest=m.originalWarpDestination().orElseThrow();var arrivalSource=arrivalRequest.loadingSource().orElseThrow();
   assertSame(source.initialProgramSource().orElseThrow().pastLoadingSource(),arrivalSource);
   var arrivalManager=SessionManager.getCurrentGameplayMode().getLevelManager();var departedLevel=arrivalManager.getCurrentLevel();long departedGeneration=arrivalManager.getCompletedProductionLoadGeneration();
   var departureCopies=m.originalR11LoadingImage().scroll().copies();var departureCalls=m.programServicesState().completedCalls();var departureSounds=m.programSoundRequests();
   var retiredFull=registry.capture();var retiredCourse=registry.captureCourse();
   run.accept(0);
   var arrivingFrame=m.originalR11EntryFrame();var arrivedLevel=assertInstanceOf(com.openggf.game.soniccd.level.SonicCdLevel.class,arrivalManager.getCurrentLevel());
   assertNotSame(frame,arrivingFrame);assertTrue(frame.closed());assertThrows(IllegalStateException.class,frame::captureActor);assertThrows(IllegalStateException.class,frame::captureShared);
   assertNotSame(departedLevel,arrivedLevel);assertEquals(Math.incrementExact(departedGeneration),arrivalManager.getCompletedProductionLoadGeneration());assertTrue(m.originalR11EngineLoadingAssembled());
   assertSame(arrivalSource,arrivedLevel.loadingSource());assertSame(arrivalSource.placements(),arrivedLevel.nativePlacements());assertSame(arrivalSource.retainedPlayerSource(),arrivingFrame.stageSource());assertEquals(CdStagePayload.R11B.stage(),arrivingFrame.stageSource().stage());
   assertEquals(0x80,arrivingFrame.environment().movement().gates().timeZone(),"Only the later actual Main315 store clears the marker");assertEquals(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,m.originalR11LoadingImage().phase());assertEquals(0,m.originalR11LoadingPrefix().main().levelStartedByte());
   assertFalse(m.originalR11FirstLoad().levelLoaded());assertFalse(m.originalR11FirstLoad().standardLoaded());assertEquals(0,m.originalR11FirstLoad().completedWaits());assertFalse(arrivingFrame.closed());
   assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(levelClock,arrivingFrame.vIntClock());assertEquals(levelClock,frame.vIntClock());CdCapturedValueAssertions.assertCapturedValueEquals(departureCopies,m.originalR11LoadingImage().scroll().copies());assertEquals(departureSounds,m.programSoundRequests());
   var arrivalCalls=m.programServicesState().completedCalls();assertEquals(departureCalls,arrivalCalls.subList(0,departureCalls.size()));assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),arrivalCalls.subList(departureCalls.size(),arrivalCalls.size()));
   assertEquals(SonicCdGameModule.OriginalWarpPhase.NONE,m.originalWarpPhase());assertTrue(m.originalWarpDestination().isEmpty());assertTrue(m.warpSceneState().isEmpty());
   var arrivalImage=image(m);assertThrows(RuntimeException.class,()->registry.restore(retiredFull));CdCapturedValueAssertions.assertCapturedValueEquals(arrivalImage,image(m));
   assertThrows(RuntimeException.class,()->registry.restoreCourse(retiredCourse));CdCapturedValueAssertions.assertCapturedValueEquals(arrivalImage,image(m));
   assertSame(arrivingFrame,m.originalR11EntryFrame());assertSame(arrivedLevel,arrivalManager.getCurrentLevel());assertEquals(Math.incrementExact(departedGeneration),arrivalManager.getCompletedProductionLoadGeneration());assertFalse(arrivingFrame.closed());
  }
 }
 @Test void readyDataAndPublicWarpSceneCannotMintAnOriginalReturnOccurrence() throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   assertEquals(SonicCdGameModule.OriginalWarpPhase.NONE,m.originalWarpPhase());var before=image(m);
   var method=SonicCdGameModule.class.getDeclaredMethod("serviceOriginalWarpProgramme",int.class);method.setAccessible(true);
   var failure=assertThrows(InvocationTargetException.class,()->method.invoke(m,0));assertInstanceOf(IllegalStateException.class,failure.getCause());
   CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));assertTrue(m.warpSceneState().isEmpty());assertFalse(m.originalR11EntryFrame().closed());
  }
 }
 @Test void constructedReturnPortFaultsKeepOnlyCompletedNativePrefixesAndOriginalThrowable() {
  // Constructed control of the SHARED leaf only. It grants no original ingress or scene ownership.
  for(int faultAt=0;faultAt<3;faultAt++)for(boolean landed:new boolean[]{false,true}) {
   final int selected=faultAt;var original=new IllegalStateException("native prefix fault");var writes=new ArrayList<String>();
   var port=new CdRetainedProgrammeReturn.Port(){
    private void store(int ordinal,String value){if(ordinal==selected&&!landed)throw original;writes.add(value);if(ordinal==selected)throw original;}
    public void returnByte(int value){assertEquals(255,value);store(0,"return");}
    public void stopFm(){store(1,"FM_STOP");}
    public void blank(){store(2,"blank");}
   };
   assertSame(original,assertThrows(IllegalStateException.class,()->CdRetainedProgrammeReturn.enter(port)));
   assertEquals(List.of("return","FM_STOP","blank").subList(0,faultAt+(landed?1:0)),writes);
  }
 }
 private static void readyAndActualMarkedRts(SonicCdGameModule m,IntConsumer run) {
  for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)run.accept(0);
  assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
  for(int n=0;n<6351;n++){int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;run.accept(held);assertFalse(m.originalR11EntryFrame().closed());}
  assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,m.originalR11LoadingImage().phase());
  for(int n=0;n<22;n++)run.accept(8);
  assertEquals(SonicCdR31aLoadingVisit.Phase.RETURNED,m.originalR11LoadingImage().phase());
  assertEquals(6351,m.originalR11EntryFrame().environment().globals().levelFrames());
 }
 private static void assertRejectedIdleAndWrongThread(SonicCdGameModule m)throws Exception {
  var before=image(m);var method=SonicCdGameModule.class.getDeclaredMethod("serviceOriginalWarpProgramme",int.class);method.setAccessible(true);
  var idle=assertThrows(InvocationTargetException.class,()->method.invoke(m,0));assertInstanceOf(IllegalStateException.class,idle.getCause());
  var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{m.serviceOriginalR11Loading(0);}catch(Throwable rejected){error.set(rejected);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,error.get());
  var capture=SonicCdGameModule.class.getDeclaredMethod("captureOriginalR11EntryImage");capture.setAccessible(true);Object issued=capture.invoke(m);var type=issued.getClass();var validate=SonicCdGameModule.class.getDeclaredMethod("validateOriginalR11Restore",type);validate.setAccessible(true);
  for(String component:new String[]{"originalWarp","programmeSounds"}) {
   Object wrong=component.equals("originalWarp")?null:List.of(SonicCdGameModule.ProgramSound.STOP_FM);
   var record=copyRecord(issued,component,wrong);var reject=assertThrows(InvocationTargetException.class,()->validate.invoke(m,record));assertInstanceOf(RuntimeException.class,reject.getCause());
  }
  CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));assertFalse(m.originalR11EntryFrame().closed());
 }
 private static Object copyRecord(Object original,String name,Object replacement)throws Exception {
  var components=original.getClass().getRecordComponents();var types=new Class<?>[components.length];var values=new Object[components.length];
  for(int i=0;i<components.length;i++){types[i]=components[i].getType();var read=components[i].getAccessor();read.setAccessible(true);values[i]=components[i].getName().equals(name)?replacement:read.invoke(original);}
  var ctor=original.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);
 }
 private static Object image(SonicCdGameModule m) {
  var f=m.originalR11EntryFrame();return List.of(f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11WorldImage(),m.originalR11TimeWarpSavedImage(),m.originalR11FirstLoad(),m.originalWarpPhase(),m.warpSceneState(),m.programSoundRequests());
 }
 private static AutoCloseable disposeWhileContextCurrent(){return ()->{Throwable failure=null;try{SessionManager.clear();}catch(RuntimeException|Error original){failure=original;}try{com.openggf.game.GameServices.graphics().cleanup();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}if(failure instanceof RuntimeException original)throw original;if(failure instanceof Error original)throw original;};}
}
