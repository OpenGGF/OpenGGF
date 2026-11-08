package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST; SOURCE proposals / UNRUN. Real factory5/slot/session ingress.
 * This finite prefix is deliberately not Ready or an ordinary service. */
@Isolated class TestCdOriginalR11SharedLevelStart {
 @TempDir Path root;
 @BeforeEach void setup(){TestEnvironment.resetAll();SessionManager.clear();}
 @AfterEach void cleanup(){SessionManager.clear();TestEnvironment.resetAll();}
 @Test void actualSelectedContinuationExecutesLevelStartAndStopsAtFirstLoadingSelection()throws Exception{
  var source=TestCdOriginalBootPrefix.source();
  var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0x80);
  var module=attempt.module().orElseThrow();var mode=attempt.openSession();
  var stop=assertThrows(CdSelectedProfileVisit.Unowned.class,()->module.createGame(source));
  assertEquals(CdSelectedProfileVisit.Boundary.R11_LEVEL_START_LOADING,stop.boundary());
  assertTrue(stop.getMessage().contains("FIRST_SELECTOR_C_SELECTED_DISPLAY_AND_CURRENT_ART_OWNER"));
  var prefix=module.originalR11LoadingPrefix();var main=prefix.main();var frame=module.originalR11EntryFrame();
  assertEquals(0,main.demoModeWord());assertEquals(0,main.levelStartedByte());assertEquals(0,main.usePlayer2Byte());
  assertEquals(1,main.palFadeFlagsByte());assertEquals(1,main.level().plcLoadFlags());assertEquals(0,main.level().checkpoint());
  assertEquals(0x80,prefix.gameMode());assertEquals(3,prefix.ring().lives());assertEquals(5000,prefix.score().nextLifeScore());
  assertEquals(0,prefix.score().score());assertEquals(0,prefix.goodFutureFlags());assertEquals(0,prefix.goodFuture());
  assertEquals(List.of(CdLevelStart.Cut.CLEAR_DEMO,CdLevelStart.Cut.ENTRY_STORES,CdLevelStart.Cut.PLC_LOADED,
   CdLevelStart.Cut.FRESH_PREFIX,CdLevelStart.Cut.RESET_SAVED_FLAGS,CdLevelStart.Cut.FRESH_SUFFIX,
   CdLevelStart.Cut.MARK_INITIALIZING,CdLevelStart.Cut.CLEAR_PLCS,CdLevelStart.Cut.NORMAL_PALETTE),prefix.calls());
  byte[] flags=module.originalR11PlacementImage().savedFlags().copy();assertEquals(766,flags.length);
  assertEquals(1,flags[0]);assertEquals(1,flags[1]);for(int i=2;i<flags.length;i++)assertEquals(0,flags[i]);
  assertEquals(0,prefix.section().queue().headSource());assertEquals(0,prefix.section().remainingTilesWord());
  assertEquals(128,prefix.normalPalette().length());for(byte b:prefix.normalPalette().copy())assertEquals(0,b);
  assertSame(source,mode.getWorldSession().getDataSource());assertTrue(frame.vIntClock().absoluteSeed().isEmpty());
  assertEquals(0,frame.vIntClock().deltaSinceAdmittedCut());assertEquals(0,frame.state().header().slotId());
  assertThrows(UnsupportedOperationException.class,module::requirePlayerFrame);assertFalse(frame.closed());
 }
 @Test void completeReachedPrefixFullAndCourseRestoreDoesNotReplaySavePcmStartOrLevelStart()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
  var module=attempt.module().orElseThrow();var world=attempt.openSession().getWorldSession();assertThrows(CdSelectedProfileVisit.Unowned.class,()->module.createGame(source));
  var registry=new RewindRegistry();WorldSessionPolicyAccess.registerRewind(world,registry);
  var full=registry.capture();var course=registry.captureCourse();var prefix=module.originalR11LoadingPrefix();
  var placed=module.originalR11PlacementImage();var frame=module.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();
  var services=module.programServicesState();var selected=module.selectedProfileState();var disk=Files.readAllBytes(root.resolve("scd/slot1.json"));
  registry.restore(full);registry.restoreCourse(course);
  assertSame(prefix,module.originalR11LoadingPrefix());assertSame(services,module.programServicesState());assertSame(selected,module.selectedProfileState());
  assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());
  assertArrayEquals(placed.savedFlags().copy(),module.originalR11PlacementImage().savedFlags().copy());
  assertEquals(placed.pool().slots(),module.originalR11PlacementImage().pool().slots());
  assertArrayEquals(disk,Files.readAllBytes(root.resolve("scd/slot1.json")));
  assertThrows(CdSelectedProfileVisit.Unowned.class,()->module.createGame(source));assertSame(prefix,module.originalR11LoadingPrefix());
 }
 @Test void actuallyWrittenZoneZeroSlotUsesSameSharedLevelStartOnLoad()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var manager=new SaveManager(root);
  var first=SonicCdGameModule.prepareSelectedProfile(source,manager,1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);first.openSession();assertThrows(CdSelectedProfileVisit.Unowned.class,()->first.module().orElseThrow().createGame(source));
  SessionManager.clear();var next=SonicCdGameModule.prepareSelectedProfile(source,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);next.openSession();var module=next.module().orElseThrow();
  assertThrows(CdSelectedProfileVisit.Unowned.class,()->module.createGame(source));assertEquals(3,module.originalR11LoadingPrefix().ring().lives());
  assertTrue(module.selectedProfileState().calls().contains(CdSelectedProfileVisit.Call.IPX_LOAD_READ));
  assertEquals(1,module.originalR11LoadingPrefix().main().level().plcLoadFlags());
 }
 @Test void failedStartNeverProducesLoadingPrefixOrReady()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfileFromOriginalBoot(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,new CdProgramInitialization.ExecutionEnvironment(Optional.empty()));
  var module=attempt.module().orElseThrow();attempt.openSession();assertThrows(CdProgramInitialization.UnknownRead.class,()->module.createGame(source));
  assertThrows(IllegalStateException.class,module::originalR11LoadingPrefix);assertThrows(IllegalStateException.class,module::originalR11PlacementImage);
  assertThrows(RuntimeException.class,()->module.createGame(source));
 }
 @Test void retiredOwnerDoesNotReopenFromTheReachedLoadingPrefix()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();attempt.openSession();
  assertThrows(CdSelectedProfileVisit.Unowned.class,()->module.createGame(source));var frame=module.originalR11EntryFrame();SessionManager.clear();
  assertTrue(frame.closed());assertThrows(IllegalStateException.class,module::originalR11LoadingPrefix);assertThrows(IllegalStateException.class,attempt::openSession);
 }
}
