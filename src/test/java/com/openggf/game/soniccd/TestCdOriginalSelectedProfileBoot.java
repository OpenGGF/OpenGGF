package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.session.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST. Actual fresh Module/Session/final slot; constructed historical save only. No fabricated load publication. Author Java was UNRUN; Root qualification is recorded in the implementation plan. */
@Isolated class TestCdOriginalSelectedProfileBoot {
    @TempDir Path directory;
    @BeforeEach void setup(){TestEnvironment.resetAll();SessionManager.clear();}
    @AfterEach void cleanup(){SessionManager.clear();TestEnvironment.resetAll();}
    @Test void newFactoryIsPackagePrivateAndOldPublicFactoryRemains()throws Exception{
        var c=SonicCdGameModule.class;var fresh=c.getDeclaredMethod("prepareSelectedProfileFromOriginalBoot",CdGameDataSource.class,SaveManager.class,int.class,SonicCdGameModule.ProfileEntry.class,CdProgramInitialization.ExecutionEnvironment.class);assertFalse(Modifier.isPublic(fresh.getModifiers()));assertTrue(Modifier.isStatic(fresh.getModifiers()));assertTrue(Modifier.isPublic(c.getDeclaredMethod("prepareSelectedProfile",CdGameDataSource.class,SaveManager.class,int.class,SonicCdGameModule.ProfileEntry.class).getModifiers()));
    }
    @Test void freshModuleHasActualPreStorageTitleAndInitialization()throws Exception{var m=fresh(0);assertEquals(OptionalInt.of(0x90),m.ipxProgressState().titleFlagsByte());assertEquals(CdProgramInitialization.KnownBit.SET,m.programServicesState().initialization().controllerSetupBit());assertTrue(m.programServicesState().initialization().versionCache().knownBits()==255);assertNull(SessionManager.getCurrentWorldSession());}
    @Test void oldFactoryStillHasUnknownBootHistory()throws Exception{var s=source();var m=SonicCdGameModule.prepareSelectedProfile(s,new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME).module().orElseThrow();assertTrue(m.ipxProgressState().titleFlagsByte().isEmpty());assertEquals(SonicCdProgramLoadServices.unknown(),m.programServicesState().loads());}
    @Test void freshNewGameReachesOriginalR11LevelStartWithoutDeclaringReady()throws Exception{var m=fresh(0);open(m,1);var stop=assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(source()));assertEquals(CdSelectedProfileVisit.Programme.R11A,stop.programme());assertEquals(CdSelectedProfileVisit.Boundary.R11_LEVEL_START_LOADING,stop.boundary());assertFalse(m.originalR11EntryFrame().closed());assertThrows(UnsupportedOperationException.class,m::requirePlayerFrame);}
    @Test void laterZoneDoesNotBecomeR31()throws Exception{var m=fresh(2);open(m,1);assertEquals(CdSelectedProfileVisit.Programme.LATER_ZONE,assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(source())).programme());assertFalse(m.selectedProfileState().calls().contains(CdSelectedProfileVisit.Call.RUN_R31_ENTRY));}
    @Test void realR31PublicationLoadsAndConsumesOnlyItsOriginalPendingPermit()throws Exception{
        TestSonicCdR31aSelectedProfileLoading.assertInheritedR31Promotion(fresh(1),source());
    }
    @Test void selectedTemporaryTailRemainsUnknownAfterFreshBoot()throws Exception{var m=fresh(0);open(m,1);assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(source()));assertEquals(List.of(OptionalInt.empty(),OptionalInt.empty()),m.selectedProfileState().temporary().orElseThrow().tail());}
    @Test void wrongFinalSlotStopsBeforeNativeProgressPublication()throws Exception{var m=fresh(1);open(m,2);var before=m.selectedProfileState();assertThrows(IllegalStateException.class,()->m.createGame(source()));assertSame(before,m.selectedProfileState());}
    @Test void absentWorldCannotClaimABootOrProgrammeContinuation()throws Exception{var m=fresh(1);assertThrows(IllegalStateException.class,()->m.createGame(source()));assertEquals(CdSelectedProfileVisit.Phase.PREPARED,m.selectedProfileState().phase());}
    @Test void replayedR11BoundaryDoesNotRepeatStorage()throws Exception{var m=fresh(0);open(m,1);assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(source()));var before=m.selectedProfileState();assertThrows(CdSelectedProfileVisit.Unowned.class,()->m.createGame(source()));assertSame(before,m.selectedProfileState());}
    @Test void interruptionCannotPublishAModuleOrSearch()throws Exception{var s=source();Thread.currentThread().interrupt();try{assertThrows(java.io.IOException.class,()->SonicCdGameModule.prepareSelectedProfileFromOriginalBoot(s,new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME,TestCdOriginalBootPrefix.environment()));}finally{Thread.interrupted();}new SaveManager(directory).flushPendingWrites();assertFalse(Files.exists(directory.resolve("scd/slot1.json")));}
    @Test void exactOriginalErrorPreservesTheAppliedPrefixAndRetiresTheFreshInvocation()throws Exception{
        var s=source();var base=new SaveManager(directory);base.writeSlot("scd",1,CdSaveProfile.fromTemplate(s.initialSaveAssets().orElseThrow()).encode());
        var original=new AssertionError("actual storage callback failure");var manager=CdSelectedProfileTestStorage.errorOnRead(directory,2,original);
        var m=SonicCdGameModule.prepareSelectedProfileFromOriginalBoot(s,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,TestCdOriginalBootPrefix.environment()).module().orElseThrow();open(m,1);
        assertSame(original,assertThrows(AssertionError.class,()->m.createGame(s)));assertEquals(OptionalInt.of(0x90),m.ipxProgressState().titleFlagsByte());assertEquals(CdSelectedProfileVisit.Call.LOCAL_SUB_POLICY_COPY,m.selectedProfileState().calls().getLast());assertThrows(IllegalStateException.class,()->m.createGame(s));assertThrows(UnsupportedOperationException.class,m::requirePlayerFrame);
    }
    private SonicCdGameModule fresh(int zone)throws Exception{var s=source();var manager=new SaveManager(directory);if(zone!=0){var p=CdSaveProfile.fromTemplate(s.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(zone<<8),OptionalInt.of(0),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0),OptionalInt.of(0),OptionalInt.of(0)));manager.writeSlot("scd",1,p.encode());}return SonicCdGameModule.prepareSelectedProfileFromOriginalBoot(s,manager,1,zone==0?SonicCdGameModule.ProfileEntry.NEW_GAME:SonicCdGameModule.ProfileEntry.LOAD_GAME,TestCdOriginalBootPrefix.environment()).module().orElseThrow();}
    private static CdGameDataSource source()throws Exception{return TestCdOriginalBootPrefix.source();}
    private static void open(SonicCdGameModule m,int slot)throws Exception{SessionManager.openGameplaySession(m,m,source(),SaveSessionContext.forSlot("scd",slot,new SelectedTeam("sonic",List.of()),0,0));}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
