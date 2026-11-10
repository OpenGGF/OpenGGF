package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.session.*;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict selected original loading requirement. SOURCE ONLY / execution UNRUN.
 * Calls actual Module loading owner with caller rows; no synthetic Frame or saved-state ingress.
 * Ordinary World obligations in TestCdOriginalTtSharedEntry remain independently enabled. */
@Isolated class TestCdOriginalTtLoadingReady {
 @TempDir Path root;
 private TestCdOriginalR11LoadingWorld fixture;
 @BeforeEach void setup(){fixture=new TestCdOriginalR11LoadingWorld();fixture.root=root;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}
 private SonicCdGameModule start()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var saves=new SaveManager(root);
  var save=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(
   OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  saves.writeSlot("scd",1,save.encode());
  var attempt=SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
  var module=attempt.module().orElseThrow();attempt.openSession();module.createGame(source);return module;
 }
 private static void ready(SonicCdGameModule m){
  for(int row=0;row<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)m.serviceOriginalR11Loading(0);
  assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte(),"actual returned second queue/fade, never an expected boundary");
  assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
  assertEquals(22,m.originalR11LoadingImage().fadeSteps());
 }
 @Test void realR41LoadingKeepsRawSourceAndPublishesWaterBeforeReady()throws Exception {
  var m=start();var f=m.originalR11EntryFrame();var source=f.stageSource();
  assertEquals(0x200,source.nativeZoneActWord());assertEquals(CdStagePayload.R41A.stage(),source.stage());
  assertEquals(0,m.originalR11LoadingPrefix().main().demoModeWord());
  assertTrue(m.originalLoadingWaterImage().isEmpty(),"water is unknown before reached native stores");
  for(int row=0;row<256&&m.originalR11LoadingImage().fadeSteps()!=21;row++)m.serviceOriginalR11Loading(0);
  assertEquals(21,m.originalR11LoadingImage().fadeSteps());assertEquals(0,m.originalR11LoadingPrefix().main().levelStartedByte());
  assertFalse(m.originalLoadingWaterImage().orElseThrow().returnedPaletteLoaded());
  m.serviceOriginalR11Loading(0);ready(m);var w=m.originalLoadingWaterImage().orElseThrow();
  assertEquals(0x280,w.waterYWord().orElseThrow());assertEquals(w.waterYWord(),w.staticYWord());assertEquals(w.waterYWord(),w.targetYWord());
  assertEquals(0xDF,w.splitByte().orElseThrow());assertEquals(0,m.originalR11LoadingPrefix().section().hintWord());assertEquals(0,w.pendingByte().orElseThrow(),"original BRAM broad-clear product, not absent-field default");assertEquals(0,w.swayAngleByte().orElseThrow(),"original MAINVARS clear product");
  assertEquals(0,w.routineByte().orElseThrow());assertEquals(0,w.fullByte().orElseThrow());assertEquals(1,w.speedByte().orElseThrow());
  assertTrue(w.returnedPaletteLoaded());assertEquals(128,m.originalLoadingWaterPalette().length());
  var binding=SessionManager.getCurrentWorldSession().getDataSource();
  assertInstanceOf(CdGameDataSource.class,binding);
  var sourcePalette=((CdGameDataSource)binding).programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource().waterPalette(0);
  assertArrayEquals(sourcePalette.bytes().copy(),m.originalLoadingWaterPalette().copy());
  assertEquals(80,f.state().motion().centreX16_16()>>>16);assertEquals(64,f.state().motion().centreY16_16()>>>16);
  assertEquals(0x1C97,f.environment().movement().bounds().right());assertEquals(0x510,f.environment().movement().bounds().bottom());
  assertEquals(0,f.environment().movement().globals().boredTimer());
  assertEquals(0,m.originalR11LoadingImage().globals().tail().orElseThrow().player2BoredWord());
  assertEquals(0,f.environment().globals().levelFrames());assertEquals(0,f.state().motion().routine());assertSame(source,f.stageSource());
  assertEquals(0,m.originalR11LoadingPrefix().gameMode()&128);
 }
 @Test void genuineLoadingFullAndCourseReplayRetainSameOwnersAndNoStartOrSaveReentry()throws Exception {
  var m=start();m.serviceOriginalR11Loading(8);var registry=new RewindRegistry();
  WorldSessionPolicyAccess.registerRewind(SessionManager.getCurrentWorldSession(),registry);
  var full=registry.capture();var course=registry.captureCourse();var services=m.programServicesState();
  var saved=Files.readAllBytes(root.resolve("scd/slot1.json"));var f=m.originalR11EntryFrame();var source=f.stageSource();
  ready(m);var actor=f.captureActor();var shared=f.captureShared();var water=m.originalLoadingWaterImage();
  registry.restore(full);ready(m);assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertEquals(water,m.originalLoadingWaterImage());
  registry.restoreCourse(course);ready(m);assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());assertEquals(water,m.originalLoadingWaterImage());
  assertSame(source,f.stageSource());assertSame(services.loads(),m.programServicesState().loads());assertArrayEquals(saved,Files.readAllBytes(root.resolve("scd/slot1.json")));
 }
 @Test void invalidRowAndForeignThreadAreNonwritesAndRetirementDoesNotReopen()throws Exception {
  var m=start();var f=m.originalR11EntryFrame();var actor=f.captureActor();var shared=f.captureShared();var prefix=m.originalR11LoadingPrefix();
  assertThrows(IllegalArgumentException.class,()->m.serviceOriginalR11Loading(256));
  var fault=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{m.serviceOriginalR11Loading(0);}catch(Throwable e){fault.set(e);}});t.start();t.join();
  assertInstanceOf(IllegalStateException.class,fault.get());assertSame(prefix,m.originalR11LoadingPrefix());assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());
  var registry=new RewindRegistry();WorldSessionPolicyAccess.registerRewind(SessionManager.getCurrentWorldSession(),registry);var full=registry.capture();
  SessionManager.clear();assertTrue(f.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));
 }
 @Test void explicitLoadingFactoryCannotGrantModuleOrOrdinaryInvocation()throws Exception {
  var m=start();var f=m.originalR11EntryFrame();
  var detached=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.fromProgrammeEntry(f.stageSource(),f.state(),f.environment(),f.artState(),f.vIntClock());
  var original=f.captureShared();var detachedState=detached.state();var detachedEnv=detached.environment();
  assertThrows(com.openggf.game.soniccd.player.CdPlayerFrameRuntime.UnsupportedFrame.class,()->detached.serviceVBlank(com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL,()->fail("publication must not run"),()->fail("art must not run")));
  assertSame(detachedState,detached.state());assertSame(detachedEnv,detached.environment());assertFalse(detached.closed());assertEquals(original,f.captureShared());detached.close();
 }
 @Test void componentFaultKeepsFirstFailureAndCompletedControllerPrefixWithoutFinishOrRetry()throws Exception {
  var m=start();var f=m.originalR11EntryFrame();var clock=f.vIntClock();var history=f.state().history();var fault=new IllegalArgumentException("loading publication fault");
  var observed=assertThrows(IllegalArgumentException.class,()->f.serviceLevelLoad(8,()->{throw fault;},()->fail("draw after fault"),()->fail("PLC after fault")));
  assertSame(fault,observed);assertEquals(0,observed.getSuppressed().length);assertEquals(8,f.environment().controls().rawHeld());
  assertEquals(clock,f.vIntClock());assertEquals(history,f.state().history());assertTrue(f.closed());
  assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));
 }

}
