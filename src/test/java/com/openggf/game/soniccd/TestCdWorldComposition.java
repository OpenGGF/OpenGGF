package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdR31aAssets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** UNRUN candidates. Historical selected-save/platform ingress is constructed; the Boot,
 * source/selected entry, FULL generation, loading, Frame and physical producers are real.
 * No pool/camera/terrain/controller fitting, source repair or synthetic handler is used.
 * A native family/PlaceBlock frontier is a failure to qualify these positive vectors,
 * never a skip or accepted alternative success. Root records the exact original fault.
 */
@Isolated class TestCdWorldComposition {
 @TempDir Path directory;
 private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
 @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}

 static void assertFirstOrdinary(SonicCdGameModule module,Runnable actualService){
  var core=module.r31LoadingVisit();var frame=module.requirePlayerFrame();var host=module.r31WorldHost();
  var before=core.state();var old=host.video().completedCpu();long clock=frame.vIntClock().absoluteValue().orElseThrow();
  assertEquals(SonicCdR31aLoadingVisit.Phase.READY,before.phase());assertEquals(0,frame.state().motion().routine());
  actualService.run();
  assertSame(core,module.r31LoadingVisit());assertSame(frame,module.requirePlayerFrame());assertSame(host,module.r31WorldHost());
  assertEquals(2,frame.state().motion().routine(),"Original Init returns before Main");assertEquals(1,frame.environment().globals().levelFrames());
  assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow());assertTrue(frame.display().isEmpty(),"Init has no primary Draw");
  assertTrue(host.video().completedCpu().isPresent(),"Only original completed physical drain supplies NEW CPU");
  assertSame(old,assertInstanceOf(SonicCdR31aWorldHost.Published.class,host.selected()).selection().inputs().selectedCpu());
  assertFalse(core.failed());assertFalse(frame.closed());
 }
 @Test void selectedBootBindsTheCommonWorldWithoutServicingAnInput()throws Exception{
  try(var b=fixture.start(1,0x80)){
   assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());var world=world(b.module);
   assertFalse(world.isOrdinaryRunning());assertEquals(SonicCdR31aLoadingVisit.Phase.FIRST_PLC,b.module.r31LoadingVisit().state().phase());
   assertEquals(0,b.module.requirePlayerFrame().vIntClock().deltaSinceAdmittedCut());assertTrue(b.module.requirePlayerFrame().vIntClock().absoluteValue().isEmpty());
   assertThrows(IllegalStateException.class,world::actualScope);
  }
 }
 @Test void clearFlowersProvidesNamedScratchAndShieldCpuKnowledgeAtItsRealCut()throws Exception{
  try(var b=fixture.start(1,0x80)){
   var host=b.module.r31WorldHost();assertFalse(host.clears().contains(SonicCdR31aLoadingVisit.Action.CLEAR_FLOWERS));assertEquals(OptionalInt.of(0),host.noBumperByte());assertArrayEquals(new byte[0x480],host.shieldCpu().orElseThrow().copy());
   TestSonicCdR31aSelectedProfileLoading.step(b,8);
   assertTrue(host.clears().contains(SonicCdR31aLoadingVisit.Action.CLEAR_FLOWERS));assertEquals(OptionalInt.of(0),host.noBumperByte());
   assertArrayEquals(new byte[0x480],host.shieldCpu().orElseThrow().copy());assertEquals(OptionalInt.of(0),host.bossStartedByte());
   TestSonicCdR31aSelectedProfileLoading.ready(b);assertFirstOrdinary(b.module,()->TestSonicCdR31aSelectedProfileLoading.step(b,8));
  }
 }
 @Test void firstRealOrdinaryUsesInitThenSecondUsesThePendingMainProducer()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);assertFirstOrdinary(b.module,()->TestSonicCdR31aSelectedProfileLoading.step(b,8));
   TestSonicCdR31aSelectedProfileLoading.exportPicture(b,TestSonicCdR31aSelectedProfileLoading.draw(b),"ordinary-init-old-cpu");
   var host=b.module.r31WorldHost();var old=host.video().completedCpu();var placement=host.placement().capture();
   TestSonicCdR31aSelectedProfileLoading.step(b,8);
   assertEquals(2,b.module.requirePlayerFrame().environment().globals().levelFrames());assertTrue(b.module.requirePlayerFrame().display().isPresent());
   assertSame(old,assertInstanceOf(SonicCdR31aWorldHost.Published.class,host.selected()).selection().inputs().selectedCpu());
   assertNotSame(old,host.video().completedCpu());assertArrayEquals(placement.savedFlags().copy(),host.placement().capture().savedFlags().copy());
   assertSame(((CdGameDataSource)b.mode.getWorldSession().getDataSource()).r31aAssets().orElseThrow().terrain(),host.currentTerrain());
   TestSonicCdR31aSelectedProfileLoading.exportPicture(b,TestSonicCdR31aSelectedProfileLoading.draw(b),"ordinary-main-old-cpu");
  }
 }
 @Test void fullAndCourseRestoreReplayTheSameInitAndMainOwnersWithoutLoadingAgain()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var world=world(b.module);var before=TestSonicCdR31aSelectedProfileLoading.values(b);long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();
   TestSonicCdR31aSelectedProfileLoading.step(b,8);var first=TestSonicCdR31aSelectedProfileLoading.values(b);
   TestSonicCdR31aSelectedProfileLoading.step(b,8);var second=TestSonicCdR31aSelectedProfileLoading.values(b);
   for(boolean useCourse:new boolean[]{false,true}){
    if(useCourse)registry.restoreCourse(course);else registry.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));
    TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(first,TestSonicCdR31aSelectedProfileLoading.values(b));
    TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(second,TestSonicCdR31aSelectedProfileLoading.values(b));
    assertSame(world,world(b.module));assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
   }
  }
 }
 @Test void invalidHeldLateAndForeignSourceEntryCannotRetireTheHealthyProducer()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);var world=world(b.module);var before=TestSonicCdR31aSelectedProfileLoading.values(b);
   assertThrows(IllegalArgumentException.class,()->b.module.serviceR31Loading(256));
   assertThrows(IllegalStateException.class,()->world.requireCut(CdOrdinaryLoop.Cut.RUN_OBJECTS));
   assertThrows(NullPointerException.class,()->world.run(8,(SonicCdR31aLoadingVisit.OrdinaryAccess)null));
   assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertFirstOrdinary(b.module,()->TestSonicCdR31aSelectedProfileLoading.step(b,8));
  }
 }
 @Test void ordinarySelectedPictureRetainsOldCpuAcrossCompleteRestore()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
   var registry=b.mode.getRewindRegistry();var full=registry.capture();var values=TestSonicCdR31aSelectedProfileLoading.values(b);
   var selected=assertInstanceOf(SonicCdR31aWorldHost.Published.class,b.module.r31WorldHost().selected()).selection();
   assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection());var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);
   assertEquals(values,TestSonicCdR31aSelectedProfileLoading.values(b));TestSonicCdR31aSelectedProfileLoading.step(b,8);registry.restore(full);
   assertEquals(values,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));
  }
 }
 @Test void caughtRenderCaptureServiceRestoreAndCloseReentryLeaveOrdinaryOwnerHealthy()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
   var registry=b.mode.getRewindRegistry();var full=registry.capture();var before=TestSonicCdR31aSelectedProfileLoading.values(b);var world=world(b.module);
   GameServices.graphics().registerCommand((x,y,w,h)->{
    assertThrows(IllegalStateException.class,()->b.module.serviceR31Loading(8));assertThrows(IllegalStateException.class,registry::capture);
    assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,world::close);
    assertThrows(IllegalStateException.class,()->b.module.gameplayFrameController().close());
   });
   TestSonicCdR31aSelectedProfileLoading.draw(b);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));
   TestSonicCdR31aSelectedProfileLoading.step(b,8);assertFalse(b.module.requirePlayerFrame().closed());
  }
 }
 /** Genuine historical selected ingress. This is a measured unsupported-family witness,
  * not positive route completion; ID10 is ObjRing (native Object Index Act1Present:27). */
 @Test void genuineControllerReachesTheSourceBoundRingDispatchFrontier()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var world=world(b.module);var placement=host.placement();
   var terrain=host.currentTerrain();long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();
   for(int n=1;n<=223;n++){
    TestSonicCdR31aSelectedProfileLoading.step(b,8);
    assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertSame(world,world(b.module));assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
    assertSame(terrain,host.currentTerrain(),"No genuine PlaceBlock store was reached on this bounded route");assertEquals(n,frame.environment().globals().levelFrames());
    if(n==200){
     var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=TestSonicCdR31aSelectedProfileLoading.values(b);
     var beforePixels=TestSonicCdR31aSelectedProfileLoading.draw(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);var after=TestSonicCdR31aSelectedProfileLoading.values(b);var afterPixels=TestSonicCdR31aSelectedProfileLoading.draw(b);
     for(boolean courseScope:new boolean[]{false,true}){
      if(courseScope)registry.restoreCourse(course);else registry.restore(full);
      assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(beforePixels,TestSonicCdR31aSelectedProfileLoading.draw(b));
      TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(afterPixels,TestSonicCdR31aSelectedProfileLoading.draw(b));
     }
     registry.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));
    }
   }
   assertEquals(0,frame.environment().controls().controlLocked());assertEquals(1,b.module.r31LoadingVisit().state().calls().stream().filter(c->c==SonicCdR31aLoadingVisit.Call.TITLE_PLC_APPEND).count());
   TestSonicCdR31aSelectedProfileLoading.exportPicture(b,TestSonicCdR31aSelectedProfileLoading.draw(b),"controller-before-ring-frontier");
   var frontier=assertThrows(SonicCdR31aObjectPass.Unowned.class,()->TestSonicCdR31aSelectedProfileLoading.step(b,8));
   assertEquals(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,frontier.boundary());assertEquals(33,frontier.wholeIndex());assertEquals(0x10,frontier.value());
   assertEquals(0x10,placement.pool().state(33).id());assertEquals(0,placement.pool().state(33).routine(),"Unknown Ring Init was never called");
   assertEquals(224,frame.environment().globals().levelFrames());assertTrue(frame.closed());assertThrows(IllegalStateException.class,()->b.module.serviceR31Loading(8));
   System.out.println("R31_WORLD_FRONTIER completed223 ordinary services; next service224: original ID10 Ring at whole33; no PlaceBlock store reached; actor="+frame.state()+"; full/course values and actual pixels replayed at service200/201");
  }
 }
 @Test void componentApiHasNoPublicWorldOrCompletedValueEntry(){
  assertFalse(Modifier.isPublic(CdWorld.class.getModifiers()));
  assertTrue(Arrays.stream(CdWorld.class.getDeclaredConstructors()).noneMatch(c->Modifier.isPublic(c.getModifiers())));
  var runs=Arrays.stream(CdWorld.class.getDeclaredMethods()).filter(m->m.getName().equals("run")).toList();assertEquals(2,runs.size());
  assertTrue(runs.stream().allMatch(m->m.getParameterTypes()[1]==SonicCdR31aLoadingVisit.OrdinaryAccess.class||m.getParameterTypes()[1]==SonicCdAct3FutureLoadingVisit.OrdinaryAccess.class));
 }
 private static CdWorld world(SonicCdGameModule module)throws Exception{var f=SonicCdGameModule.class.getDeclaredField("r31World");f.setAccessible(true);return assertInstanceOf(CdWorld.class,f.get(module));}
}
