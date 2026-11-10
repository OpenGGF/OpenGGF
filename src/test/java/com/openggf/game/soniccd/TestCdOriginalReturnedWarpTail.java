package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.scene.CdWarpScene;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Tests written before the production tail. Only the original public controller enters WARP.
 * No private state, scene, period, command, counter or bank is hydrated. All execution UNRUN. */
@Isolated class TestCdOriginalReturnedWarpTail extends TestCdOriginalR11LoadingWorld {
 @Test void actualMarkedWarpReturnPublishesUnknownBlankAndAuthenticatedRequestWithFullCourseReplay()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=dispose()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   for(int n=0;n<6351;n++){int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;step(loop,held);}
   assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,m.originalR11LoadingImage().phase());
   for(int n=0;n<22;n++)step(loop,8);
   assertEquals(SonicCdGameModule.OriginalWarpPhase.LEVEL_RETURN_TAIL,m.originalWarpPhase());
   step(loop,0);step(loop,0);for(int n=0;n<90;n++)step(loop,0);step(loop,0);for(int n=0;n<24;n++)step(loop,0);
   assertEquals(CdWarpScene.Phase.MAIN,m.warpSceneState().orElseThrow().phase());step(loop,0x80);for(int n=0;n<24;n++)step(loop,0);
   assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_RETURNED,m.originalWarpPhase());
   var frame=m.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();var world=m.originalR11WorldImage();var bank=m.originalR11TimeWarpSavedImage();var clock=frame.vIntClock();var scene=m.warpSceneState().orElseThrow();
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var preFull=registry.capture();var preCourse=registry.captureCourse();var preFullValues=values(preFull);var preCourseValues=values(preCourse);
   assertEquals(OptionalInt.of(255),m.originalWarpReturnKnowledge().value());assertTrue(m.originalWarpDisplayEnabled());
   step(loop,0); // RunMMD after WARP RTS: UNKNOWN byte store -> STOP FM -> one IPX blank
   assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_IPX_TAIL,m.originalWarpPhase());assertTrue(m.originalWarpReturnKnowledge().value().isEmpty());
   assertFalse(m.originalWarpDisplayEnabled());assertTrue(m.originalWarpDestination().isEmpty());
   assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8,SonicCdGameModule.ProgramSound.STOP_FM),m.programSoundRequests());
   var blankFull=registry.capture();var blankCourse=registry.captureCourse();var blankFullValues=values(blankFull);var blankCourseValues=values(blankCourse);var blankPixels=draw(m);assertTrue(Arrays.stream(blankPixels).allMatch(v->v==0xFF000000),"IPX blank is fresh actual ENGINE output");
   step(loop,0); // ordered RunLevel reads issue a DATA request, not a destination FULL/World
   assertEquals(SonicCdGameModule.OriginalWarpPhase.DESTINATION_SELECTED,m.originalWarpPhase());var destination=m.originalWarpDestination().orElseThrow();
   assertEquals(0x80,destination.selection().rawPeriodByte());assertEquals(CdIpxProgress.WarpCommandRow.PAST,destination.selection().row());assertEquals(CdCommand.R11B,destination.command());assertEquals(CdStagePayload.R11B.stage(),destination.stage());
   assertSame(source.initialProgramSource().orElseThrow().pastLoadingSource(),destination.loadingSource().orElseThrow());
   var selectedFull=registry.capture();var selectedCourse=registry.captureCourse();var selectedFullValues=values(selectedFull);var selectedCourseValues=values(selectedCourse);assertArrayEquals(blankPixels,draw(m));
   for(boolean course:new boolean[]{false,true}) {
    if(course)registry.restoreCourse(preCourse);else registry.restore(preFull);assertComplete(course?preCourseValues:preFullValues,course?registry.captureCourse():registry.capture());assertEquals(OptionalInt.of(255),m.originalWarpReturnKnowledge().value());
    step(loop,0);assertTrue(m.originalWarpReturnKnowledge().value().isEmpty());assertArrayEquals(blankPixels,draw(m));
    if(course)registry.restoreCourse(blankCourse);else registry.restore(blankFull);assertComplete(course?blankCourseValues:blankFullValues,course?registry.captureCourse():registry.capture());step(loop,0);assertEquals(destination,m.originalWarpDestination().orElseThrow());
    if(course)registry.restoreCourse(selectedCourse);else registry.restore(selectedFull);assertComplete(course?selectedCourseValues:selectedFullValues,course?registry.captureCourse():registry.capture());assertEquals(destination,m.originalWarpDestination().orElseThrow());assertArrayEquals(blankPixels,draw(m));
    assertSame(scene,m.warpSceneState().orElseThrow());assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(clock,frame.vIntClock());assertFalse(frame.closed());
    CdCapturedValueAssertions.assertCapturedValueEquals(actor,frame.captureActor());CdCapturedValueAssertions.assertCapturedValueEquals(shared,frame.captureShared());CdCapturedValueAssertions.assertCapturedValueEquals(world,m.originalR11WorldImage());
   }
   var before=List.of(m.originalWarpDestination(),m.originalWarpReturnKnowledge(),m.programSoundRequests(),m.warpSceneState());
   var service=SonicCdGameModule.class.getDeclaredMethod("serviceOriginalWarpProgramme",int.class);service.setAccessible(true);assertInstanceOf(IllegalStateException.class,assertThrows(InvocationTargetException.class,()->service.invoke(m,0)).getCause());
   var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{m.serviceOriginalR11Loading(0);}catch(Throwable t){error.set(t);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,error.get());
   var capture=SonicCdGameModule.class.getDeclaredMethod("captureOriginalR11EntryImage");capture.setAccessible(true);Object image=capture.invoke(m);var validate=SonicCdGameModule.class.getDeclaredMethod("validateOriginalR11Restore",image.getClass());validate.setAccessible(true);
   assertInstanceOf(RuntimeException.class,assertThrows(InvocationTargetException.class,()->validate.invoke(m,copyRecord(image,"originalWarp",null))).getCause());
   assertEquals(before,List.of(m.originalWarpDestination(),m.originalWarpReturnKnowledge(),m.programSoundRequests(),m.warpSceneState()));
   // The next actual public service consumes this private R11B request and returns
   // the new marked FIRST_PLC cut; it does not execute a loading wait, Ready or Main.
   var arrivalRequest=m.originalWarpDestination().orElseThrow();var arrivalSource=arrivalRequest.loadingSource().orElseThrow();
   assertSame(source.initialProgramSource().orElseThrow().pastLoadingSource(),arrivalSource);
   var arrivalManager=SessionManager.getCurrentGameplayMode().getLevelManager();var departedLevel=arrivalManager.getCurrentLevel();long departedGeneration=arrivalManager.getCompletedProductionLoadGeneration();
   var departureCopies=m.originalR11LoadingImage().scroll().copies();var departureCalls=m.programServicesState().completedCalls();var departureSounds=m.programSoundRequests();
   var retiredFull=registry.capture();var retiredCourse=registry.captureCourse();
   step(loop,0);
   var arrivingFrame=m.originalR11EntryFrame();var arrivedLevel=assertInstanceOf(com.openggf.game.soniccd.level.SonicCdLevel.class,arrivalManager.getCurrentLevel());
   assertNotSame(frame,arrivingFrame);assertTrue(frame.closed());assertThrows(IllegalStateException.class,frame::captureActor);assertThrows(IllegalStateException.class,frame::captureShared);
   assertNotSame(departedLevel,arrivedLevel);assertEquals(Math.incrementExact(departedGeneration),arrivalManager.getCompletedProductionLoadGeneration());assertTrue(m.originalR11EngineLoadingAssembled());
   assertSame(arrivalSource,arrivedLevel.loadingSource());assertSame(arrivalSource.placements(),arrivedLevel.nativePlacements());assertSame(arrivalSource.retainedPlayerSource(),arrivingFrame.stageSource());assertEquals(CdStagePayload.R11B.stage(),arrivingFrame.stageSource().stage());
   assertEquals(0x80,arrivingFrame.environment().movement().gates().timeZone(),"Only the later actual Main315 store clears the marker");assertEquals(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,m.originalR11LoadingImage().phase());assertEquals(0,m.originalR11LoadingPrefix().main().levelStartedByte());
   assertFalse(m.originalR11FirstLoad().levelLoaded());assertFalse(m.originalR11FirstLoad().standardLoaded());assertEquals(0,m.originalR11FirstLoad().completedWaits());assertFalse(arrivingFrame.closed());
   assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(clock,arrivingFrame.vIntClock());assertEquals(clock,frame.vIntClock());CdCapturedValueAssertions.assertCapturedValueEquals(departureCopies,m.originalR11LoadingImage().scroll().copies());assertEquals(departureSounds,m.programSoundRequests());
   var arrivalCalls=m.programServicesState().completedCalls();assertEquals(departureCalls,arrivalCalls.subList(0,departureCalls.size()));assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),arrivalCalls.subList(departureCalls.size(),arrivalCalls.size()));
   assertEquals(SonicCdGameModule.OriginalWarpPhase.NONE,m.originalWarpPhase());assertTrue(m.originalWarpDestination().isEmpty());assertTrue(m.warpSceneState().isEmpty());
   var arrivalValues=values(registry.capture());assertThrows(RuntimeException.class,()->registry.restore(retiredFull));assertComplete(arrivalValues,registry.capture());
   assertThrows(RuntimeException.class,()->registry.restoreCourse(retiredCourse));assertComplete(arrivalValues,registry.capture());
   assertSame(arrivingFrame,m.originalR11EntryFrame());assertSame(arrivedLevel,arrivalManager.getCurrentLevel());assertEquals(Math.incrementExact(departedGeneration),arrivalManager.getCompletedProductionLoadGeneration());assertFalse(arrivingFrame.closed());
  }
 }
 @Test void actualInitialProgramRetainsFinitePcRelativeRowAndOnePastReader()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var ipx=source.initialProgramSource().orElseThrow();
  assertEquals("6e19dbef1bd9ad2d36771dbbce575ba7b37dce6d84cbb37bbeb6a98062674447",ipx.source().fileSha256());assertEquals(0xFF0608,ipx.source().runR11RowAddress());assertEquals(4096,ipx.source().fileBytes());assertEquals(4096,ipx.source().header().declaredCopyBytes());assertEquals(256,ipx.source().header().declaredCopyBytes()-(ipx.source().fileBytes()-com.openggf.game.soniccd.media.CdMmdHeader.HEADER_BYTES),"The original declared copy retains an unbacked tail, never padded source");
  for(int i=0;i<4;i++)assertEquals(i+1,ipx.runR11CommandByte(i));
  assertThrows(IllegalArgumentException.class,()->ipx.runR11CommandByte(-1));assertThrows(IllegalArgumentException.class,()->ipx.runR11CommandByte(4));
  assertSame(ipx.pastLoadingSource(),ipx.pastLoadingSource());assertEquals(CdStagePayload.R11B.stage(),ipx.pastLoadingSource().stage());
  assertThrows(IllegalArgumentException.class,()->ipx.requireDisc("0".repeat(64)));
  assertTrue(Arrays.stream(ipx.getClass().getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
 }
 @Test void compatibilityComponentConstructorKeepsInitialProgramAbsence()throws Exception {
  var actual=TestCdOriginalBootPrefix.source();var component=new CdGameDataSource(actual.release(),actual.assets(),actual.fmSoundAssets(),actual.timeWarpPrograms());
  assertTrue(component.initialProgramSource().isEmpty());assertSame(actual.assets(),component.assets());assertSame(actual.timeWarpPrograms().orElseThrow(),component.timeWarpPrograms().orElseThrow());
 }
 @Test void constructedUnknownReturnFaultControlsRetainOnlyReachedWrites() {
  // Constructed shared-kernel fault controls, never original-programme ingress.
  for(int at=0;at<3;at++)for(boolean landed:new boolean[]{false,true}){final int ordinal=at;var cause=new IllegalStateException("fault");var writes=new ArrayList<String>();
   var unknown=CdRetainedProgrammeReturn.ReturnKnowledge.unknown();var port=new CdRetainedProgrammeReturn.KnowledgePort(){
    void write(int n,String s){if(n==ordinal&&!landed)throw cause;writes.add(s);if(n==ordinal)throw cause;}
    public void returnByte(CdRetainedProgrammeReturn.ReturnKnowledge k){assertSame(unknown,k);assertTrue(k.value().isEmpty());write(0,"unknown");}public void stopFm(){write(1,"stop");}public void blank(){write(2,"blank");}};
   assertSame(cause,assertThrows(IllegalStateException.class,()->CdRetainedProgrammeReturn.enter(port,unknown)));assertEquals(List.of("unknown","stop","blank").subList(0,at+(landed?1:0)),writes);
  }
 }
 /** Reuse the pinned existing test-only closed capture comparator, including Root's
  * level CoW epoch contract. Reflection is read-only and supplies no production authority. */
 @SuppressWarnings("unchecked") private static Map<String,Object> values(com.openggf.game.rewind.CompositeSnapshot image)throws Exception {
  var read=TestCdOriginalSelectedWarpTimerPixels.class.getDeclaredMethod("allValues",com.openggf.game.rewind.CompositeSnapshot.class);read.setAccessible(true);return (Map<String,Object>)read.invoke(null,image);
 }
 private static void assertComplete(Map<String,Object> expected,com.openggf.game.rewind.CompositeSnapshot actual)throws Exception {
  var compare=TestCdOriginalSelectedWarpTimerPixels.class.getDeclaredMethod("assertAllValues",Map.class,com.openggf.game.rewind.CompositeSnapshot.class);compare.setAccessible(true);try{compare.invoke(null,expected,actual);}catch(InvocationTargetException error){if(error.getCause() instanceof AssertionError assertion)throw assertion;throw error;}
 }
 private static int[] draw(SonicCdGameModule m){org.lwjgl.opengl.GL11.glViewport(0,0,320,224);org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glColorMask(true,true,true,true);org.lwjgl.opengl.GL11.glClearColor(0.1f,0.7f,0.3f,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);m.gameplayFrameController().drawOverlay();org.lwjgl.opengl.GL11.glFinish();assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());return ScreenshotCapture.captureFramebuffer(320,224).pixels();}
 private static Object copyRecord(Object original,String name,Object replacement)throws Exception{var cs=original.getClass().getRecordComponents();var ts=new Class<?>[cs.length];var vs=new Object[cs.length];for(int n=0;n<cs.length;n++){ts[n]=cs[n].getType();var r=cs[n].getAccessor();r.setAccessible(true);vs[n]=cs[n].getName().equals(name)?replacement:r.invoke(original);}var c=original.getClass().getDeclaredConstructor(ts);c.setAccessible(true);return c.newInstance(vs);}
 private static AutoCloseable dispose(){return ()->{try{SessionManager.clear();}finally{com.openggf.game.GameServices.graphics().cleanup();}};}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
