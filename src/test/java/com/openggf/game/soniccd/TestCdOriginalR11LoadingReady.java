package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Positive public-ingress requirements, authored before R3 production. SOURCE / UNRUN.
 * Caller rows alone return native waits. No private snapshot/Frame or DEV ingress is supplied. */
@Isolated
class TestCdOriginalR11LoadingReady {
 @TempDir Path root;
 private TestCdOriginalR11LoadingWorld fixture;
 @BeforeEach void setup(){fixture=new TestCdOriginalR11LoadingWorld();fixture.root=root;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}
 private SonicCdGameModule.SelectedProfileAttempt attempt(com.openggf.game.soniccd.CdGameDataSource source)throws Exception {
  return SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
 }
 private static void ready(com.openggf.GameLoop loop,SonicCdGameModule module){
  for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)TestCdOriginalR11LoadingWorld.step(loop,0);
  assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte(),"real Main.BeginLevel must return");
  var prefix=module.originalR11LoadingPrefix();var loaded=module.originalR11LoadingImage();
  assertEquals(prefix.ring(),loaded.globals().ring(),"Ready retains the shared setup ring/lives/HUD stores");
  assertEquals(prefix.score(),loaded.globals().score(),"Ready retains the shared setup score flag stores");
  assertEquals(0x3C,loaded.reserved().unsignedByte(3*64),"The real Title parent survives setup and owns the existing control lock");
  for(int whole:new int[]{2,3,5})assertEquals(0x1C,loaded.reserved().unsignedByte((whole-1)*64),"Actual reserved HUD bytes survive setup");
 }
 @Test void loadingReturnsReadyAndOnlyFollowingCallerRowRunsTheOrdinaryPlayer()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=attempt(source);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var frame=module.originalR11EntryFrame();
   assertSame(source,SessionManager.getCurrentWorldSession().getDataSource());
   assertTrue(frame.vIntClock().absoluteValue().isEmpty());
   TestCdOriginalR11LoadingWorld.step(loop,8);
   assertFalse(frame.closed());assertEquals(0,frame.state().motion().routine());
   assertEquals(0,frame.vIntClock().absoluteValue().orElseThrow(),"actual Main.ClearFlowers owns clock zero");
   ready(loop,module);assertEquals(0,frame.state().motion().routine());
   assertTrue(module.originalR11FirstLoad().selection().inputs().displayEnabled());
   var clock=frame.vIntClock();TestCdOriginalR11LoadingWorld.step(loop,8);
   assertEquals(2,frame.state().motion().routine());assertEquals(8,frame.environment().controls().rawHeld());
   assertNotEquals(clock,frame.vIntClock());assertFalse(frame.closed());
  }
 }
 @Test void fullAndCourseLoadingRestoresReplayWithoutSavePcmOrStartReentry()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=attempt(source);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);TestCdOriginalR11LoadingWorld.step(loop,8);
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var services=module.programServicesState();var disk=Files.readAllBytes(root.resolve("scd/slot1.json"));
   ready(loop,module);var actor=module.originalR11EntryFrame().captureActor();var shared=module.originalR11EntryFrame().captureShared();
   registry.restore(full);ready(loop,module);assertEquals(actor,module.originalR11EntryFrame().captureActor());assertEquals(shared,module.originalR11EntryFrame().captureShared());
   registry.restoreCourse(course);ready(loop,module);assertEquals(actor,module.originalR11EntryFrame().captureActor());assertEquals(shared,module.originalR11EntryFrame().captureShared());
   assertSame(services,module.programServicesState());assertArrayEquals(disk,Files.readAllBytes(root.resolve("scd/slot1.json")));
  }
 }
 @Test void readyFullAndCourseRestoreReplayTheNextReturnedOrdinaryService()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=attempt(source);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   TestCdOriginalR11LoadingWorld.step(loop,8);var actor=module.originalR11EntryFrame().captureActor();var shared=module.originalR11EntryFrame().captureShared();
   registry.restore(full);TestCdOriginalR11LoadingWorld.step(loop,8);assertEquals(actor,module.originalR11EntryFrame().captureActor());assertEquals(shared,module.originalR11EntryFrame().captureShared());
   registry.restoreCourse(course);TestCdOriginalR11LoadingWorld.step(loop,8);assertEquals(actor,module.originalR11EntryFrame().captureActor());assertEquals(shared,module.originalR11EntryFrame().captureShared());
  }
 }
 @Test void invalidRowIsANonwriteAndRetirementCannotRestoreAnEarlierLoadingCut()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=attempt(source);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   boot.bootSonicCdSelectedProfile(source,attempt);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();
   var prefix=module.originalR11LoadingPrefix();var frame=module.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();
   assertThrows(IllegalArgumentException.class,()->module.serviceOriginalR11Loading(256));assertSame(prefix,module.originalR11LoadingPrefix());assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());
   SessionManager.clear();assertTrue(frame.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,()->module.serviceOriginalR11Loading(0));
  }
 }
}
