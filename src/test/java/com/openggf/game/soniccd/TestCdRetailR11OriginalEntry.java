package com.openggf.game.soniccd;
import com.openggf.game.save.*;
import com.openggf.game.session.*;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE-ONLY / UNRUN. User chooses slot through actual factory/storage/session path.
 * No constructed SaveSessionContext, selected receipt, player/progress/capture injection or DEV boot. */
@Isolated class TestCdRetailR11OriginalEntry {
 @TempDir Path root;
 @BeforeEach void setup(){TestEnvironment.resetAll();SessionManager.clear();}
 @AfterEach void cleanup(){SessionManager.clear();TestEnvironment.resetAll();}
 @Test void selectedNewGameRunsOriginalStartAndBirthBeforeTheExactUnownedLevelStart()throws Exception{
  var s=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(s,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0x80);var m=a.module().orElseThrow();var mode=a.openSession();assertNull(mode.getLevelManager(),"Actual pre-LevelStart session has no attached manager");
  var stop=assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(s));assertEquals(CdSelectedProfileVisit.Boundary.R11_LEVEL_START_LOADING,stop.boundary());assertEquals(CdSelectedProfileVisit.Programme.R11A,stop.programme());
  var f=m.originalR11EntryFrame();assertNull(mode.getLevelManager(),"Entry must preserve observed manager absence");assertTrue(f.stageSource().ownsPresent(s.assets()));assertEquals(0,f.state().header().slotId());assertEquals(0,f.state().motion().routine());assertTrue(f.vIntClock().absoluteSeed().isEmpty());assertEquals(0,f.vIntClock().deltaSinceAdmittedCut());
  assertEquals(0,m.ipxProgressState().timeStonesByte().orElseThrow());assertEquals(1,mode.getWorldSession().getSaveSessionContext().activeSlot().orElseThrow());assertFalse(f.closed());assertThrows(UnsupportedOperationException.class,m::requirePlayerFrame);
  assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.START_SCRATCH_CLEAR,SonicCdGameModule.ProgramStartCall.WRITE_VERSION_CACHE,SonicCdGameModule.ProgramStartCall.WRITE_INIT_MARKER,SonicCdGameModule.ProgramStartCall.INIT_VDP,SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,SonicCdGameModule.ProgramStartCall.GAME_MODE_LEVEL),m.programServicesState().completedCalls());
  var before=m.programServicesState();assertEquals(stop.boundary(),assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(s)).boundary());assertSame(before,m.programServicesState());assertSame(f,m.originalR11EntryFrame());assertThrows(IllegalStateException.class,a::openSession);
 }
 @Test void sameSourceFullAndCourseCutsRestoreWithoutStorageOrStartReplay()throws Exception{
  var s=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(s,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();var world=a.openSession().getWorldSession();assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(s));var r=new RewindRegistry();WorldSessionPolicyAccess.registerRewind(world,r);var full=r.capture();var course=r.captureCourse();var frame=m.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();var services=m.programServicesState();var disk=Files.readAllBytes(root.resolve("scd/slot1.json"));
  r.restore(full);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());r.restoreCourse(course);assertSame(services,m.programServicesState());assertArrayEquals(disk,Files.readAllBytes(root.resolve("scd/slot1.json")));assertSame(frame,m.originalR11EntryFrame());
 }
 @Test void unknownVersionStopsAfterRealScratchBeforeVideoOrFrameAndCannotRetry()throws Exception{
  var s=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfileFromOriginalBoot(s,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,new CdProgramInitialization.ExecutionEnvironment(Optional.empty()));var m=a.module().orElseThrow();a.openSession();var failure=assertThrows(CdProgramInitialization.UnknownRead.class,()->m.createGame(s));assertEquals(CdProgramInitialization.Read.EXECUTION_REGION,failure.read());assertEquals(SonicCdGameModule.ProgramStartCall.START_SCRATCH_CLEAR,m.programServicesState().completedCalls().getLast());assertThrows(IllegalStateException.class,m::originalR11EntryFrame);assertThrows(IllegalStateException.class,()->m.createGame(s));
 }
 @Test void originalLoadOfTheActuallyWrittenZoneZeroSlotReentersR11WithoutAContextFixture()throws Exception{
  var s=TestCdOriginalBootPrefix.source();var manager=new SaveManager(root);var first=SonicCdGameModule.prepareSelectedProfile(s,manager,1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);first.openSession();assertThrows(CdSelectedProfileVisit.Unowned.class,()->first.module().orElseThrow().createGame(s));var old=first.module().orElseThrow().originalR11EntryFrame();SessionManager.clear();assertTrue(old.closed());
  var next=SonicCdGameModule.prepareSelectedProfile(s,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);next.openSession();var m=next.module().orElseThrow();var stop=assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(s));assertEquals(CdSelectedProfileVisit.Boundary.R11_LEVEL_START_LOADING,stop.boundary());assertTrue(m.selectedProfileState().calls().contains(CdSelectedProfileVisit.Call.IPX_LOAD_READ));assertNotSame(old,m.originalR11EntryFrame());assertEquals(3,m.selectedProfileState().globals().orElseThrow().lives().orElseThrow());
 }
 @Test void retiredAndForeignGenerationCutsCannotCreateAnotherOriginalEntry()throws Exception{
  var s=TestCdOriginalBootPrefix.source();var manager=new SaveManager(root);var first=SonicCdGameModule.prepareSelectedProfile(s,manager,1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var w=first.openSession().getWorldSession();var m=first.module().orElseThrow();assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(s));var r=new RewindRegistry();WorldSessionPolicyAccess.registerRewind(w,r);var snapshot=r.capture();SessionManager.clear();assertThrows(IllegalStateException.class,r::capture);assertThrows(IllegalStateException.class,m::originalR11EntryFrame);
  var second=SonicCdGameModule.prepareSelectedProfile(s,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);var other=second.openSession().getWorldSession();assertThrows(CdSelectedProfileVisit.Unowned.class,()->second.module().orElseThrow().createGame(s));var r2=new RewindRegistry();WorldSessionPolicyAccess.registerRewind(other,r2);assertThrows(IllegalArgumentException.class,()->r2.restore(snapshot));
 }
 @Test void invalidExecutionByteAndZeroSlotDoNotInitializeStorage()throws Exception{
  var s=TestCdOriginalBootPrefix.source();assertThrows(IllegalArgumentException.class,()->SonicCdGameModule.prepareSelectedProfile(s,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,1));assertThrows(IllegalArgumentException.class,()->SonicCdGameModule.prepareSelectedProfile(s,new SaveManager(root),0,SonicCdGameModule.ProfileEntry.NEW_GAME,0));assertFalse(Files.exists(root.resolve("scd")));
 }
}
