package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.soniccd.scene.CdWarpScene;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntConsumer;
import static org.junit.jupiter.api.Assertions.*;

/** Actual NEW_GAME/marked-RTS ingress only. No scene, object, clock or input hydration.
 * Native1ecc Time Warp Cutscene/Main.asm Start164-181, Main187-199, RunObjects306-375:
 *90 delay;240 active timer includes prepare VSync +24 fade-in +215 MAIN;24 exit waits.
 *32 MAIN visits cover two full4-slot trail rotations at the native4-pulse spawn cadence. */
@Isolated class TestCdOriginalSelectedWarpTimerPixels extends TestCdOriginalR11LoadingWorld {
 private static final int DELAY=90,FADE=24,ACTIVE_TIMER=240,MAIN=ACTIVE_TIMER-1-FADE;
 private static final int MULTI_MAIN=8*4,FOLLOW_MAIN=MULTI_MAIN+4*4;
 private static final Path CAPTURE_DIR=Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine", "original-selected-warp-timer-20261109");
 private static final String CAPTURE_FILE="ENGINE-original-selected-WARP-timer-multi-main.png";
 private static final String MODULE="com.openggf.game.soniccd.SonicCdGameModule$";
 /** Closed, source-pinned read-only capture shape. No arbitrary object graph reflection.
  * Every declared written field is compared; added/removed fields fail, not silently disappear.
  * Compiler synthetic outer references are redundant to each explicit owner field. */
 private static final Map<String,List<String>> SEALED_FIELDS=Map.of(
  MODULE+"OriginalWarpDestination",List.of("ticket","initial","view"),
  MODULE+"OriginalWarpImage",List.of("owner","frame","levelLifetime","bankLifetime","thread","actor","shared","world","prefix","loading","ipx","sounds","phase","ticket","assets","scene","state","destination"),
  MODULE+"OriginalTimeWarpSavedImage",List.of("owner","boot","retainedSource","world","thread","lifetime","levelLifetime","generation","actor","shared","worldImage","prefix","loading","firstLoad","returnKind","saved","switches"),
  "com.openggf.game.soniccd.CdSelectedProfileVisit$Snapshot",List.of("owner","world","lifetime","ioOrdinal","state"),
  "com.openggf.game.soniccd.CdWorld$R11Snapshot",List.of("owner","cpu","sounds","tunnelServices","mechanismServices","closureServices"),
  "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",List.of("owner","placements","identity","pastIdentity","act2Identity","act2PastIdentity","status","pool","savedFlags","r31aSource"),
  "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot",List.of("owner","slots"));

 @Test void genuineNoStartTimerProducesMultipleObjectsPixelsAndCompleteFullCourseReplay()throws Exception {
  Path export=Boolean.getBoolean("soniccd.warpTimer.capture")?validatedCaptureDirectory():null;
  var source=TestCdOriginalBootPrefix.source();
  var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
  var m=attempt.module().orElseThrow();
  // The inherited genuine boot fixture requires configured surfaceless EGL and no desktop.
  // It owns the current engine context. Opening a second component context here would be wrong.
  try(var boot=boot();var cleanup=disposeWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);IntConsumer run=held->step(loop,held);
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)run.accept(0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   for(int n=0;n<6351;n++) {
    int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;
    run.accept(held);assertFalse(m.originalR11EntryFrame().closed());
   }
   assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,m.originalR11LoadingImage().phase());
   for(int n=0;n<22;n++)run.accept(0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.RETURNED,m.originalR11LoadingImage().phase());
   assertEquals(SonicCdGameModule.OriginalWarpPhase.LEVEL_RETURN_TAIL,m.originalWarpPhase());
   var frame=m.originalR11EntryFrame();assertEquals(6351,frame.environment().globals().levelFrames());
   assertEquals(0x80,frame.environment().movement().gates().timeZone());assertEquals(0,m.originalR11LoadingPrefix().main().level().levelRestart());
   assertEquals(2,m.originalR11LoadingPrefix().main().level().spawnMode());
   var actor=frame.captureActor();var shared=frame.captureShared();var oldState=frame.state();var oldEnvironment=frame.environment();var oldArt=frame.artState();
   var world=m.originalR11WorldImage();var bank=m.originalR11TimeWarpSavedImage();var oldClock=frame.vIntClock();
   run.accept(0);assertTrue(m.warpSceneState().isEmpty()); // actual level return blank only
   run.accept(0);assertScene(m,CdWarpScene.Phase.DELAY,DELAY,0);
   var oldLoading=m.originalR11LoadingImage();var oldPrefix=m.originalR11LoadingPrefix();
   for(int n=1;n<=DELAY;n++){run.accept(0);assertEquals(n,scene(m).vIntCounterWord());assertTrue(scene(m).heldByte().isEmpty());}
   assertScene(m,CdWarpScene.Phase.PREPARE,0,DELAY);
   run.accept(0);assertScene(m,CdWarpScene.Phase.FADE_IN,ACTIVE_TIMER-1,DELAY+1);
   for(int n=0;n<FADE;n++)run.accept(0);
   assertScene(m,CdWarpScene.Phase.MAIN,MAIN,DELAY+1+FADE);
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
   CompositeSnapshot multiFull=null,multiCourse=null,followFull=null,followCourse=null;
   Map<String,Object> multiFullValues=null,multiCourseValues=null,followFullValues=null,followCourseValues=null;
   RgbaImage multiPixels=null,followPixels=null,earlyPixels=null;int firstSatCount=0;byte[] firstSat=null;
   for(int n=1;n<=MAIN;n++) {
    assertEquals(CdWarpScene.Phase.MAIN,scene(m).phase(),"No early Start/unsupported exit is accepted");
    run.accept(0);var selected=scene(m);
    assertEquals(DELAY+1+FADE+n,selected.vIntCounterWord());assertEquals(MAIN-n,selected.timerWord());
    assertSame(oldState,frame.state());assertSame(oldEnvironment,frame.environment());assertSame(oldArt,frame.artState());
    assertSame(oldLoading,m.originalR11LoadingImage());assertSame(oldPrefix,m.originalR11LoadingPrefix());assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(oldClock,frame.vIntClock());
    assertEquals(0,selected.heldByte().orElseThrow());assertEquals(0,selected.tapByte().orElseThrow());
    assertEquals(n==MAIN?CdWarpScene.Phase.FADE_OUT:CdWarpScene.Phase.MAIN,selected.phase());
    if(n==2) {
     assertEquals(1,countObjectId(selected,1,0,4));assertEquals(1,countObjectId(selected,2,4,32));
     firstSatCount=selected.displayedSpriteCount();assertTrue(firstSatCount>0);firstSat=selected.displayedSprites().copy();
     earlyPixels=drawClosed(m);
    }
    if(n==MULTI_MAIN) {
     assertEquals(4,countObjectId(selected,1,0,4));assertTrue(countObjectId(selected,2,4,32)>=2);
     assertTrue(selected.displayedSpriteCount()>firstSatCount,"Multiple genuine Sonic/Sparkle DrawObject publications");
     assertFalse(Arrays.equals(firstSat,selected.displayedSprites().copy()));
     multiFull=registry.capture();multiCourse=registry.captureCourse();
     multiFullValues=allValues(multiFull);multiCourseValues=allValues(multiCourse);multiPixels=drawClosed(m);
     assertFalse(Arrays.equals(Objects.requireNonNull(earlyPixels).pixels(),multiPixels.pixels()),"Fresh actual MAIN pictures must show progression");
    }
    if(n==FOLLOW_MAIN) {
     followFull=registry.capture();followCourse=registry.captureCourse();
     followFullValues=allValues(followFull);followCourseValues=allValues(followCourse);followPixels=drawClosed(m);
    }
   }
   assertScene(m,CdWarpScene.Phase.FADE_OUT,0,DELAY+ACTIVE_TIMER);assertEquals(0,scene(m).fadeStep());
   for(int n=1;n<=FADE;n++){run.accept(0);assertEquals(DELAY+ACTIVE_TIMER+n,scene(m).vIntCounterWord());}
   assertReturned(m);var returnFullValues=allValues(registry.capture());var returnCourseValues=allValues(registry.captureCourse());
   var returnedPixels=drawClosed(m);assertEquals(1,Arrays.stream(returnedPixels.pixels()).distinct().count(),"Actual final selected white picture");
   assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(oldClock,frame.vIntClock());assertFalse(frame.closed());
   CdCapturedValueAssertions.assertCapturedValueEquals(actor,frame.captureActor());
   CdCapturedValueAssertions.assertCapturedValueEquals(shared,frame.captureShared());
   CdCapturedValueAssertions.assertCapturedValueEquals(world,m.originalR11WorldImage());
   assertEquals(List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8),m.programSoundRequests());
   for(boolean course:new boolean[]{false,true}) {
    if(course)registry.restoreCourse(Objects.requireNonNull(multiCourse));else registry.restore(Objects.requireNonNull(multiFull));
    assertAllValues(course?multiCourseValues:multiFullValues,course?registry.captureCourse():registry.capture());
    assertPixels(Objects.requireNonNull(multiPixels),drawClosed(m));
    for(int n=MULTI_MAIN;n<FOLLOW_MAIN;n++)run.accept(0);
    assertAllValues(course?followCourseValues:followFullValues,course?registry.captureCourse():registry.capture());
    assertPixels(Objects.requireNonNull(followPixels),drawClosed(m));
    for(int n=FOLLOW_MAIN;n<MAIN;n++)run.accept(0);
    assertScene(m,CdWarpScene.Phase.FADE_OUT,0,DELAY+ACTIVE_TIMER);
    for(int n=0;n<FADE;n++)run.accept(0);assertReturned(m);
    assertAllValues(course?returnCourseValues:returnFullValues,course?registry.captureCourse():registry.capture());
    assertPixels(returnedPixels,drawClosed(m));
    assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(oldClock,frame.vIntClock());assertFalse(frame.closed());
   }
   // Export only one already proven healthy multi-MAIN ENGINE image, after successful replay.
   if(export!=null)exportOnce(export,Objects.requireNonNull(multiPixels));
   run.accept(0);assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_IPX_TAIL,m.originalWarpPhase());assertTrue(m.originalWarpReturnKnowledge().value().isEmpty());assertFalse(m.originalWarpDisplayEnabled());
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
   assertSame(bank,m.originalR11TimeWarpSavedImage());assertEquals(oldClock,arrivingFrame.vIntClock());assertEquals(oldClock,frame.vIntClock());CdCapturedValueAssertions.assertCapturedValueEquals(departureCopies,m.originalR11LoadingImage().scroll().copies());assertEquals(departureSounds,m.programSoundRequests());
   var arrivalCalls=m.programServicesState().completedCalls();assertEquals(departureCalls,arrivalCalls.subList(0,departureCalls.size()));assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),arrivalCalls.subList(departureCalls.size(),arrivalCalls.size()));
   assertEquals(SonicCdGameModule.OriginalWarpPhase.NONE,m.originalWarpPhase());assertTrue(m.originalWarpDestination().isEmpty());assertTrue(m.warpSceneState().isEmpty());
   var arrivalValues=allValues(registry.capture());assertThrows(RuntimeException.class,()->registry.restore(retiredFull));assertAllValues(arrivalValues,registry.capture());
   assertThrows(RuntimeException.class,()->registry.restoreCourse(retiredCourse));assertAllValues(arrivalValues,registry.capture());
   assertSame(arrivingFrame,m.originalR11EntryFrame());assertSame(arrivedLevel,arrivalManager.getCurrentLevel());assertEquals(Math.incrementExact(departedGeneration),arrivalManager.getCompletedProductionLoadGeneration());assertFalse(arrivingFrame.closed());
  }
 }
 private static CdWarpScene.State scene(SonicCdGameModule m){return m.warpSceneState().orElseThrow();}
 private static void assertScene(SonicCdGameModule m,CdWarpScene.Phase phase,int timer,int counter){var s=scene(m);assertEquals(phase,s.phase());assertEquals(timer,s.timerWord());assertEquals(counter,s.vIntCounterWord());}
 private static void assertReturned(SonicCdGameModule m){assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_RETURNED,m.originalWarpPhase());assertScene(m,CdWarpScene.Phase.RETURNED,0,DELAY+ACTIVE_TIMER+FADE);assertEquals(FADE,scene(m).fadeStep());}
 /** Source format observation only:32 finite $40-byte slots, WORD object ID; no state is supplied. */
 private static int countObjectId(CdWarpScene.State s,int id,int first,int end){int count=0;for(int slot=first;slot<end;slot++)if(s.objects().unsignedWord(slot*0x40)==id)count++;return count;}
 private static Map<String,Object> allValues(CompositeSnapshot s)throws ReflectiveOperationException {
  var result=new LinkedHashMap<String,Object>();for(var entry:s.entries().entrySet())result.put(entry.getKey(),project(entry.getValue()));return result;
 }
 /** LevelSnapshot.capture() deliberately advances its CoW boundary on EVERY capture.
  * Keep both raw maps complete; compare all semantic fields and enforce monotonic capture metadata. */
 private static void assertAllValues(Map<String,Object> expected,CompositeSnapshot actual)throws ReflectiveOperationException {
  var observed=allValues(actual);var comparable=new LinkedHashMap<String,Object>(Objects.requireNonNull(expected));
  if(expected.get("level") instanceof Map<?,?> previous&&"com.openggf.game.rewind.snapshot.LevelSnapshot".equals(previous.get("$record"))){
   var current=assertInstanceOf(Map.class,observed.get("level"));assertEquals(previous.get("$record"),current.get("$record"));
   long before=assertInstanceOf(Long.class,previous.get("epochAtCapture"));long after=assertInstanceOf(Long.class,current.get("epochAtCapture"));
   assertTrue(after>=before,"Level capture/restore CoW boundaries advance; they are not replayed gameplay state");
   var semantic=new LinkedHashMap<Object,Object>(previous);semantic.put("epochAtCapture",after);comparable.put("level",semantic);
  }
  CdCapturedValueAssertions.assertCapturedValueEquals(comparable,observed);
 }
 /** Read-only bounded value projection of records/containers and SIX exact sealed source types.
  * Opaque source/Frame/ticket/lifetime objects retain identity. No constructor/restore/write is called. */
 private static Object project(Object value)throws ReflectiveOperationException {
  if(value==null)return null;Class<?> type=value.getClass();List<String> fields=SEALED_FIELDS.get(type.getName());
  if(fields!=null) {
   var actual=new TreeSet<String>();for(var field:type.getDeclaredFields())if(!field.isSynthetic()&&!Modifier.isStatic(field.getModifiers()))actual.add(field.getName());
   assertEquals(new TreeSet<>(fields),actual,"Pinned private capture shape must remain complete: "+type.getName());
   var out=new LinkedHashMap<String,Object>();out.put("$type",type.getName());
   for(String name:fields){var field=type.getDeclaredField(name);field.setAccessible(true);out.put(name,project(field.get(value)));}return out;
  }
  if(type.isRecord()) {
   var out=new LinkedHashMap<String,Object>();out.put("$record",type.getName());for(var component:type.getRecordComponents()){var read=component.getAccessor();read.setAccessible(true);out.put(component.getName(),project(read.invoke(value)));}return out;
  }
  if(value instanceof Optional<?> option)return option.isEmpty()?Optional.empty():Optional.ofNullable(project(option.orElseThrow()));
  if(value instanceof Map<?,?> map){var out=new LinkedHashMap<Object,Object>();for(var entry:map.entrySet())out.put(entry.getKey(),project(entry.getValue()));return out;}
  if(value instanceof List<?> list){var out=new ArrayList<Object>();for(Object item:list)out.add(project(item));return out;}
  if(value instanceof Object[] array){var out=new ArrayList<Object>();for(Object item:array)out.add(project(item));return out;}
  return value; // defensive bytes, primitive arrays and all opaque owners keep their exact contracts
 }
 private static RgbaImage drawClosed(SonicCdGameModule m)throws ReflectiveOperationException {
  var mode=SessionManager.getCurrentGameplayMode();var registry=mode.getRewindRegistry();var level=assertInstanceOf(com.openggf.level.AbstractLevel.class,mode.getLevelManager().getCurrentLevel());
  var before=allValues(registry.capture());long epoch=level.currentEpoch();
  var a=drawFresh(m,0xFF13C75D);assertEquals(epoch,level.currentEpoch(),"Drawing must not create a level CoW boundary");assertAllValues(before,registry.capture());
  epoch=level.currentEpoch();var b=drawFresh(m,0xFFDC3B91);assertEquals(epoch,level.currentEpoch(),"Repeated drawing must not create a level CoW boundary");assertAllValues(before,registry.capture());assertPixels(a,b);return a;
 }
 private static RgbaImage drawFresh(SonicCdGameModule m,int sentinel) {
  org.lwjgl.opengl.GL11.glViewport(0,0,320,224);org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glColorMask(true,true,true,true);
  org.lwjgl.opengl.GL11.glClearColor(((sentinel>>>16)&255)/255f,((sentinel>>>8)&255)/255f,(sentinel&255)/255f,1);
  org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);org.lwjgl.opengl.GL11.glFinish();
  var cleared=ScreenshotCapture.captureFramebuffer(320,224);assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());assertTrue(Arrays.stream(cleared.pixels()).allMatch(v->v==sentinel));
  m.gameplayFrameController().drawOverlay();org.lwjgl.opengl.GL11.glFinish();var pixels=ScreenshotCapture.captureFramebuffer(320,224);
  assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());assertEquals(320,pixels.width());assertEquals(224,pixels.height());assertEquals(320*224,pixels.pixels().length);
  assertFalse(Arrays.equals(cleared.pixels(),pixels.pixels()),"Untouched or skipped retained renderer cannot satisfy ENGINE pixels");return pixels;
 }
 private static void assertPixels(RgbaImage expected,RgbaImage actual){assertEquals(expected.width(),actual.width());assertEquals(expected.height(),actual.height());assertArrayEquals(expected.pixels(),actual.pixels());}
 /** The capture directory stays fixed under the current user's Downloads. */
 private static Path validatedCaptureDirectory()throws java.io.IOException {
  Path allowed=Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine");
  if(!allowed.isAbsolute()||!allowed.equals(allowed.normalize())||!allowed.equals(allowed.toRealPath())||!Files.isDirectory(allowed,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Engine capture root is not canonical");
  if(!CAPTURE_DIR.isAbsolute()||!CAPTURE_DIR.equals(CAPTURE_DIR.normalize())||!CAPTURE_DIR.getParent().equals(allowed))throw new IllegalStateException("Fixed WARP capture directory is invalid");
  if(!Files.exists(CAPTURE_DIR,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(CAPTURE_DIR);
  if(!Files.isDirectory(CAPTURE_DIR,LinkOption.NOFOLLOW_LINKS)||!CAPTURE_DIR.equals(CAPTURE_DIR.toRealPath()))throw new IllegalStateException("WARP capture directory is aliased");return CAPTURE_DIR;
 }
 private static void exportOnce(Path setup,RgbaImage pixels)throws java.io.IOException {
  Path actual=validatedCaptureDirectory();if(!actual.equals(setup))throw new IllegalStateException("Changed canonical capture directory");
  try(var stream=Files.newDirectoryStream(actual)) {
   if(!(stream instanceof SecureDirectoryStream<Path> safe))throw new IllegalStateException("Capture requires an anchored secure directory stream");
   var options=Set.<OpenOption>of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
   try(var channel=safe.newByteChannel(Path.of(CAPTURE_FILE),options);var out=Channels.newOutputStream(channel)) {
    var image=new java.awt.image.BufferedImage(pixels.width(),pixels.height(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0,0,pixels.width(),pixels.height(),pixels.pixels(),0,pixels.width());
    if(!javax.imageio.ImageIO.write(image,"png",out))throw new java.io.IOException("PNG writer unavailable");
   }
  }
 }
 private static AutoCloseable disposeWhileContextCurrent(){return ()->{Throwable failure=null;try{SessionManager.clear();}catch(RuntimeException|Error original){failure=original;}try{com.openggf.game.GameServices.graphics().cleanup();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}if(failure instanceof RuntimeException original)throw original;if(failure instanceof Error original)throw original;};}
}
