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
 /** Genuine historical selected ingress: bounded positive Ring continuation and first-owner witness. */
 @Test void genuineControllerContinuesThroughDeathToTheNextMeasuredOwner()throws Exception{
  try(var b=fixture.start(1,0x80)){
   TestSonicCdR31aSelectedProfileLoading.ready(b);var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var world=world(b.module);var placement=host.placement();
   var core=b.module.r31LoadingVisit();var terrain=host.currentTerrain();long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();
   int completed=0;UnsupportedOperationException frontier=null;var firstClosedFamilies=new TreeMap<Integer,String>();
   var lastClosedCore=core.state();var lastClosedActor=frame.state();var lastClosedEnvironment=frame.environment();var lastClosedBank=placement.capture();var lastClosedCpu=host.video().completedCpu();var lastClosedScroll=host.scroll();
   var p2registry=b.mode.getRewindRegistry();var p2full=p2registry.capture();var p2course=p2registry.captureCourse();
   TestSonicCdR31aSelectedProfileLoading.Values p2before=null;byte[] p2pixels=null;int p2replayNext=-1;boolean p2replayed=false;
   int deathCut=-1;boolean deathReplayed=false;
   java.util.Optional<com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat> ringCpu=java.util.Optional.empty();
   for(int n=1;n<=1200;n++){
    try{
    TestSonicCdR31aSelectedProfileLoading.step(b,8);
    }catch(SonicCdR31aObjectPass.Unowned reached){frontier=reached;break;}catch(CdWorld.Unowned reached){frontier=reached;break;}
    completed=n;
    if(frame.state().motion().routine()>=6&&frame.environment().globals().debugMode()==0){
     assertEquals(host.scroll().foreground().cameraYWord(),frame.environment().movement().bounds().bottom());assertEquals(host.scroll().foreground().cameraYWord(),host.scroll().foreground().destBottomBound());
     assertEquals(lastClosedScroll.foreground().cameraY16_16(),host.scroll().foreground().cameraY16_16(),"Native death branch skips LevelScroll");
     if(deathCut<0){deathCut=n;exportClosedPicture(b,TestSonicCdR31aSelectedProfileLoading.draw(b),"death-entry-service"+n);}
     if(n==deathCut+1){var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=TestSonicCdR31aSelectedProfileLoading.values(b);var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);var after=TestSonicCdR31aSelectedProfileLoading.values(b);var nextPixels=TestSonicCdR31aSelectedProfileLoading.draw(b);
      for(boolean useCourse:new boolean[]{false,true}){if(useCourse)registry.restoreCourse(course);else registry.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(nextPixels,TestSonicCdR31aSelectedProfileLoading.draw(b));}
      registry.restore(full);deathReplayed=true;exportClosedPicture(b,pixels,"death-first-body-service"+n);
     }
    }
    boolean firstP2=false;
    for(var row:placement.capture().pool().slots())if(row.id()!=0&&row.routine()!=0){boolean added=firstClosedFamilies.putIfAbsent(row.id(),"service="+n+",whole="+row.identity().wholeIndex()+",routine="+row.routine())==null;if(added&&Set.of(0x20,0x21,0x22,0x23,0x26,0x27,0x28).contains(row.id()))firstP2=true;}
    if(p2replayNext==n&&!p2replayed){var after=TestSonicCdR31aSelectedProfileLoading.values(b);var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);var closed=p2registry.capture();
     for(boolean courseScope:new boolean[]{false,true}){if(courseScope)p2registry.restoreCourse(p2course);else p2registry.restore(p2full);assertEquals(p2before,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(p2pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));}
     p2registry.restore(closed);p2replayed=true;
    }
    if(firstP2&&p2replayNext<0){p2full=p2registry.capture();p2course=p2registry.captureCourse();p2before=TestSonicCdR31aSelectedProfileLoading.values(b);p2pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);p2replayNext=n+1;exportClosedPicture(b,p2pixels,"first-closed-p2-service"+n);}

    lastClosedCore=core.state();lastClosedActor=frame.state();lastClosedEnvironment=frame.environment();lastClosedBank=placement.capture();lastClosedCpu=host.video().completedCpu();lastClosedScroll=host.scroll();
    assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertSame(world,world(b.module));assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
    assertSame(terrain,host.currentTerrain(),"No genuine PlaceBlock store was reached on this bounded route");assertEquals(n,frame.environment().globals().levelFrames());
    if(n==224){var ring=placement.pool().state(33);assertEquals(0x10,ring.id());assertEquals(2,ring.routine());assertEquals(0x209BE4,ring.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));assertEquals(0xA7AE,ring.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));ringCpu=host.video().completedCpu();assertTrue(ringCpu.isPresent());}
    if(n==225){var selected=assertInstanceOf(SonicCdR31aWorldHost.Published.class,host.selected()).selection().inputs().selectedCpu().orElseThrow();var old=ringCpu.orElseThrow();assertArrayEquals(old.bytes(),selected.bytes(),"Actual selector8 chooses the preceding completed RunObjects CPU");assertEquals(old.spriteCount(),selected.spriteCount());assertEquals(old.provenance(),selected.provenance());}
    if(n==223 || n==224 || n==225 || n%100==0)exportClosedPicture(b,TestSonicCdR31aSelectedProfileLoading.draw(b),"controller-death-service"+n);
    if(n==200 || n==222 || n==224 || n==250 || n==300){
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
   // The original failure retires the live Module/Host pair. Read retained Frame/pool
   // comparison metadata only; never re-enter its closed authority merely to report it.
   System.out.println("R31_DEATH_CONTINUATION completed="+completed+" frontier="+frontier+" PARTIAL_actor="+frame.state()+" PARTIAL_environment="+frame.environment());
   System.out.println("R31_DEATH_LAST_CLOSED coreLevel="+lastClosedCore.level()+" actor="+lastClosedActor+" environment="+lastClosedEnvironment+" camera="+lastClosedScroll+" CPU="+lastClosedCpu+" bank="+lastClosedBank);
   System.out.println("R31_DEATH_OBSERVED_RETURNED_FAMILIES "+firstClosedFamilies);
   String captureDir=System.getProperty("soniccd.r31.capture.dir");
   if(captureDir!=null){var text=new StringBuilder("ENGINE observed continuation; completed="+completed+"; attempted="+(completed+1)+"; frontier="+frontier+"\nLAST_CLOSED_actor="+lastClosedActor+"\nLAST_CLOSED_environment="+lastClosedEnvironment+"\nLAST_CLOSED_camera="+lastClosedScroll+"\nLAST_CLOSED_CPU="+lastClosedCpu.map(c->HexFormat.of().formatHex(c.bytes()))+"\nObserved returned dynamic family states="+firstClosedFamilies+"\nPARTIAL_actor="+frame.state()+"\nPARTIAL_environment="+frame.environment()+"\n");
    for(var bank:List.of(lastClosedBank,placement.capture())){text.append(bank==lastClosedBank?"LAST_CLOSED_BANK\n":"PARTIAL_BANK\n");text.append(bank.status()).append("\nSaved=").append(HexFormat.of().formatHex(bank.savedFlags().copy())).append("\n");for(var row:bank.pool().slots())text.append("whole").append(row.identity().wholeIndex()).append('=').append(HexFormat.of().formatHex(row.copyBytes())).append('\n');}
    java.nio.file.Files.writeString(Path.of(captureDir).resolve("continuation-evidence.txt"),text,java.nio.file.StandardOpenOption.CREATE_NEW);
   }
   var ring=placement.pool().state(33);
   System.out.println("R31_DEATH_PARTIAL whole33="+java.util.HexFormat.of().formatHex(ring.copyBytes())+" status="+placement.status()+" saved="+java.util.HexFormat.of().formatHex(placement.capture().savedFlags().copy()));
   assertEquals(0,frame.environment().controls().controlLocked());assertEquals(1,core.state().calls().stream().filter(c->c==SonicCdR31aLoadingVisit.Call.TITLE_PLC_APPEND).count());
   assertTrue(p2replayed,"Post-P2 FULL/course next-return and pixels were genuinely replayed");
   assertTrue(completed>350,"The old death-camera frontier must now return");assertTrue(deathReplayed,"Actual death FULL/course and next-state/pixels replayed");
   if(frontier!=null){
    if(frontier instanceof SonicCdR31aObjectPass.Unowned objectFrontier){
    assertTrue(EnumSet.of(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,SonicCdR31aObjectPass.Boundary.SPRING_CHILD_OWNER,SonicCdR31aObjectPass.Boundary.P2_ANIMATION_OWNER,SonicCdR31aObjectPass.Boundary.P2_ART_CLOCK_OWNER,SonicCdR31aObjectPass.Boundary.P2_PARENT_OWNER,SonicCdR31aObjectPass.Boundary.CURRENT_TERRAIN_OWNER).contains(objectFrontier.boundary()),"Only explicit first missing native owner may stop the controller");
    assertNotEquals(0x10,objectFrontier.value(),"Ring is now owned");if(objectFrontier.boundary()==SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY){assertFalse(objectFrontier.value()>=0x30&&objectFrontier.value()<=0x35,"The complete P5 graph is owned");assertFalse(Set.of(0x20,0x21,0x22,0x23,0x26,0x27,0x28).contains(objectFrontier.value()),"The complete P2 dispatch graph is owned");}if(objectFrontier.boundary()==SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY||objectFrontier.boundary()==SonicCdR31aObjectPass.Boundary.SPRING_CHILD_OWNER)assertEquals(objectFrontier.value(),placement.pool().state(objectFrontier.wholeIndex()).id());
    }else assertEquals(CdWorld.Boundary.LEVEL_FLOW,assertInstanceOf(CdWorld.Unowned.class,frontier).boundary());
    assertEquals(completed+1,frame.environment().globals().levelFrames());assertTrue(frame.closed());
    var beforeState=frame.state();var beforeEnvironment=frame.environment();var beforeClock=frame.vIntClock();var beforePool=placement.capture();
    assertThrows(IllegalStateException.class,()->b.module.serviceR31Loading(8));
    assertSame(beforeState,frame.state());assertSame(beforeEnvironment,frame.environment());assertSame(beforeClock,frame.vIntClock());
    var afterPool=placement.capture();assertEquals(beforePool.status(),afterPool.status());assertEquals(beforePool.pool().slots(),afterPool.pool().slots());assertArrayEquals(beforePool.savedFlags().copy(),afterPool.savedFlags().copy());
   }else assertEquals(1200,completed);

  }
 }
 @Test void componentApiHasNoPublicWorldOrCompletedValueEntry(){
  assertFalse(Modifier.isPublic(CdWorld.class.getModifiers()));
  assertTrue(Arrays.stream(CdWorld.class.getDeclaredConstructors()).noneMatch(c->Modifier.isPublic(c.getModifiers())));
  var runs=Arrays.stream(CdWorld.class.getDeclaredMethods()).filter(m->m.getName().equals("run")).toList();assertEquals(2,runs.size());
  assertTrue(runs.stream().allMatch(m->m.getParameterTypes()[1]==SonicCdR31aLoadingVisit.OrdinaryAccess.class||m.getParameterTypes()[1]==SonicCdAct3FutureLoadingVisit.OrdinaryAccess.class));
 }
 /** Observation only from a returned ordinary cut. Selected CPU is the OLD producer; the
  * retained partial bank printed after a fault is never labelled as this closed picture. */
 private static void exportClosedPicture(TestSonicCdR31aSelectedProfileLoading.Booted b,byte[] rgba,String name)throws Exception{
  TestSonicCdR31aSelectedProfileLoading.exportPicture(b,rgba,name);String raw=System.getProperty("soniccd.r31.capture.dir");if(raw==null)return;
  var host=b.module.r31WorldHost();var frame=b.module.requirePlayerFrame();var bank=host.placement().capture();
  var selected=assertInstanceOf(SonicCdR31aWorldHost.Published.class,host.selected()).selection().inputs().selectedCpu();
  var text=new StringBuilder("ENGINE observed fully returned ordinary cut; no native parity claim\n");
  text.append("State8=").append(frame.state()).append("\nEnvironment=").append(frame.environment()).append("\nClock=").append(frame.vIntClock()).append("\nCamera=").append(host.scroll()).append("\nPlacementStatus=").append(bank.status()).append("\nSaved=").append(HexFormat.of().formatHex(bank.savedFlags().copy())).append("\n");
  for(var row:bank.pool().slots())text.append("whole").append(row.identity().wholeIndex()).append('=').append(HexFormat.of().formatHex(row.copyBytes())).append('\n');
  text.append("OLD_selected_CPU=").append(selected.map(c->HexFormat.of().formatHex(c.bytes()))).append("\nCompleted_CPU=").append(host.video().completedCpu().map(c->HexFormat.of().formatHex(c.bytes()))).append("\nAmyCaptured=").append(host.amyCapturedByte()).append("\nSoundCalls=").append(host.sounds()).append('\n');
  java.nio.file.Files.writeString(Path.of(raw).resolve(name+".state.txt"),text,java.nio.file.StandardOpenOption.CREATE_NEW);
 }
 private static CdWorld world(SonicCdGameModule module)throws Exception{var f=SonicCdGameModule.class.getDeclaredField("r31World");f.setAccessible(true);return assertInstanceOf(CdWorld.class,f.get(module));}
}
