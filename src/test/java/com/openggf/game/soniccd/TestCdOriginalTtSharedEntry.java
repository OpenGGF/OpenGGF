package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source requirements FIRST / all Java, media, loading and replay UNRUN.
 * Saves are constructed historical user profiles; Frames/captures come only from public ingress.
 * Positive loading/ordinary/replay requirements remain strict at the named loading/Frame fence.
 */
@Isolated class TestCdOriginalTtSharedEntry {
 @TempDir Path root;
 private TestCdOriginalR11LoadingWorld fixture;
 @BeforeEach void setup(){fixture=new TestCdOriginalR11LoadingWorld();fixture.root=root;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}
 private SonicCdGameModule.SelectedProfileAttempt attempt(CdGameDataSource source,int unlock)throws Exception {
  var manager=new SaveManager(root);
  var p=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(
    OptionalInt.of(0x200|unlock),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(unlock),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  manager.writeSlot("scd",1,p.encode());
  return SonicCdGameModule.prepareSelectedProfile(source,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
 }
 @ParameterizedTest @ValueSource(ints={0,7,255}) void savedZoneTwoRunsRealR4SaveThenSelectsPresentWithRaw0200(int unlock)throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=attempt(source,unlock);var m=a.module().orElseThrow();a.openSession();
  m.createGame(source);assertEquals(0,m.originalR11LoadingPrefix().main().demoModeWord());
  var services=m.programServicesState();
  assertEquals(CdNativeFile.R4PCM,services.loads().installed().orElseThrow().driver().file());
  assertSame(source.r4PcmSource().orElseThrow().bytes(),services.loads().installed().orElseThrow().driver().originalSource());
  assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.START_SCRATCH_CLEAR,
    SonicCdGameModule.ProgramStartCall.WRITE_VERSION_CACHE,SonicCdGameModule.ProgramStartCall.WRITE_INIT_MARKER,SonicCdGameModule.ProgramStartCall.INIT_VDP,
    SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),services.completedCalls());
  assertEquals(SonicCdProgramLoadServices.LoadPrefix.SOURCE_INSTALLED,services.loads().lastLoad().prefix());
  assertEquals(0x380,services.loads().musicSetting().orElseThrow());assertEquals(0x8380,services.loads().masterSetting().orElseThrow());
  var selected=m.selectedProfileState();assertEquals(CdSelectedProfileVisit.Programme.R41A,selected.programme().orElseThrow());
  assertEquals(CdSelectedProfileVisit.Call.RUN_R41_ENTRY,selected.calls().getLast());
  assertEquals(OptionalInt.of(0x200),selected.globals().orElseThrow().zoneAct());assertEquals(1,selected.globals().orElseThrow().period());
  assertTrue(selected.globals().orElseThrow().amyCaptured().isEmpty(),"RunR4 never performs R3's Amy clear");
  assertEquals(OptionalInt.of(0x200|unlock),selected.progress().savedLevel().word(),"saved low byte is unlock, not act");
  assertTrue(selected.calls().contains(CdSelectedProfileVisit.Call.IPX_WRITE_ACK));
  assertFalse(selected.calls().contains(CdSelectedProfileVisit.Call.RUN_R31_ENTRY));
  var data=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow();data.requireRetained();
  assertEquals(CdR4DataLayout.Programme.R41A,data.programme());assertEquals(0x200,data.ordinaryZoneActWord());
  assertEquals("6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca",data.mmdSha256());
  assertThrows(UnsupportedOperationException.class,m::requirePlayerFrame);
  var disk=Files.readAllBytes(root.resolve("scd/slot1.json"));
  assertThrows(IllegalStateException.class,()->m.createGame(source));assertArrayEquals(disk,Files.readAllBytes(root.resolve("scd/slot1.json")));
 }
 @Test void missingWorldIsANonwriteBeforeAnyRunR4SaveOrSourceBorrow()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=attempt(source,7);var m=a.module().orElseThrow();var before=m.selectedProfileState();
  var disk=Files.readAllBytes(root.resolve("scd/slot1.json"));assertThrows(IllegalStateException.class,()->m.createGame(source));
  assertSame(before,m.selectedProfileState());assertArrayEquals(disk,Files.readAllBytes(root.resolve("scd/slot1.json")));
 }
 @Test void authenticOldCaptureCannotRestoreOrReplayANewPrivateBootGeneration()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var prepared=attempt(source,0);
  // This legacy factory supplies a genuine healthy pre-level observation, no original Boot permit.
  var legacy=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.LOAD_GAME);
  var old=legacy.module().orElseThrow();var first=legacy.openSession().getWorldSession();
  assertThrows(CdSelectedProfileVisit.Unowned.class,()->old.createGame(source));
  var oldRegistry=new com.openggf.game.rewind.RewindRegistry();
  com.openggf.game.session.WorldSessionPolicyAccess.registerRewind(first,oldRegistry);
  var full=oldRegistry.capture();var course=oldRegistry.captureCourse();
  SessionManager.clear();var current=prepared.module().orElseThrow();var world=prepared.openSession().getWorldSession();
  var registry=new com.openggf.game.rewind.RewindRegistry();
  com.openggf.game.session.WorldSessionPolicyAccess.registerRewind(world,registry);
  var before=current.selectedProfileState();var services=current.programServicesState();
  assertThrows(IllegalArgumentException.class,()->registry.restore(full));
  assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(course));
  assertSame(before,current.selectedProfileState());assertSame(services,current.programServicesState());
  current.createGame(source);assertEquals(0x200,current.originalR11EntryFrame().stageSource().nativeZoneActWord());
  var stopped=current.programServicesState();
  assertNotNull(registry.capture());
  assertNotNull(registry.captureCourse());
  assertThrows(IllegalStateException.class,()->current.createGame(source));
  assertThrows(IllegalStateException.class,()->old.createGame(source));
  assertSame(stopped,current.programServicesState());
 }
 @Test void equalSourceValuesDoNotGrantOriginalModuleOrWorldInvocation()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=attempt(source,0);var module=a.module().orElseThrow();
  var equal=new CdGameDataSource(source.release(),source.assets(),source.fmSoundAssets(),source.timeWarpPrograms(),
    source.act2Assets(),source.act2PastAssets(),source.act3FutureAssets(),source.r31aAssets(),source.initialSaveAssets(),
    source.r3PcmSource(),source.systemProgramSource(),Map.of(CdStagePayload.R41A.stage(),source.programmeData(CdStagePayload.R41A.stage()).orElseThrow()),source.r4PcmSource());
  assertEquals(source.identity(),equal.identity());assertNotSame(source,equal);
  var before=module.selectedProfileState();var services=module.programServicesState();
  com.openggf.game.session.SessionManager.openGameplaySession(module,module,equal,
    SaveSessionContext.forSlot("scd",1,new SelectedTeam("sonic",List.of()),0,0));
  assertThrows(IllegalStateException.class,()->module.createGame(equal));
  assertSame(before,module.selectedProfileState());assertSame(services,module.programServicesState());
 }
 @Test void selectedEntryMustReturnRealLoadingThenFirstOrdinaryWithItsOwnSource()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=attempt(source,0);var m=a.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,a);var frame=m.requirePlayerFrame();
   assertEquals(CdStagePayload.R41A.stage(),frame.stageSource().stage());assertEquals(0x200,frame.environment().movement().gates().zoneAct());
   assertEquals(0,frame.environment().globals().levelFrames());
   for(int row=0;row<256&&frame.environment().globals().levelFrames()==0;row++)TestCdOriginalR11LoadingWorld.step(loop,0);
   assertEquals(1,frame.environment().globals().levelFrames(),"a complete first Main, never a fabricated Ready/ordinary return");
   assertEquals(2,frame.state().motion().routine());assertFalse(frame.closed());
  }
 }
 @Test void fullAndCourseMustReplayActualFirstReturnedOrdinaryAndCurrentSource()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=attempt(source,0);var m=a.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,a);var frame=m.requirePlayerFrame();
   for(int row=0;row<256&&frame.environment().globals().levelFrames()==0;row++)TestCdOriginalR11LoadingWorld.step(loop,0);
   assertEquals(1,frame.environment().globals().levelFrames());var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
   var full=registry.capture();var course=registry.captureCourse();TestCdOriginalR11LoadingWorld.step(loop,8);
   var actor=frame.captureActor();var shared=frame.captureShared();var retained=frame.stageSource();
   registry.restore(full);TestCdOriginalR11LoadingWorld.step(loop,8);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());
   registry.restoreCourse(course);TestCdOriginalR11LoadingWorld.step(loop,8);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertSame(retained,frame.stageSource());
  }
 }
}
