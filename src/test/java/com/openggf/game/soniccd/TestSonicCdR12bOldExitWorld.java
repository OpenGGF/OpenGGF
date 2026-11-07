package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.*;
import static com.openggf.game.soniccd.TestSonicCdR12bResultsPass.pool;
import static com.openggf.game.soniccd.TestSonicCdR12bResultsPass.poolRuntime;
import static com.openggf.game.soniccd.TestSonicCdR12bResultsPass.result;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests authored before production. Java UNRUN. Actual original USA loading/Init/Main,
 * then explicitly constructed Results PROGRAMME ingress: known flag BYTE0 and public pool Results8.
 * This is NOT a natural Goal/Signpost route or an offscreen WaitPLC proof. No primary, Gates,
 * physics, clock, queue, art/current, placement cursor or private invocation is hydrated.
 * Main19-115/405-408, Palette Fade98-115, IPX1372-1387 at native N1ecc5f3a.
 */
@Isolated
class TestSonicCdR12bOldExitWorld {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media");assertTrue(Files.isDirectory(Path.of(root)),"configured bad root must fail");fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void actualUnknownGoodFutureFlagsStopsAtItsFirstReachedReadBeforeAnyOldExit()throws Exception{
        var a=loaded();assertTrue(a.h().module().act2PastGoodFutureFlags(a.core()).isEmpty(),"Actual carried knowledge remains unknown; no programme scalar ingress");
        // Explicit public-pool Robot hit-status COMPONENT ingress invokes its real cached goodFuture producer.
        // This does not claim a natural primary collision, and never writes Core/programme/primary values.
        seed(a,0x2D,0);pool(a).write(pool(a).state(32).moveByte(COLLISION_STATUS,1));forward(a);assertEquals(1,a.core().state().program().goodFutureByte());assertTrue(a.h().module().act2PastGoodFutureFlags(a.core()).isEmpty());
        result(a,8,77);var e=assertThrows(SonicCdAct2PastObjectPass.Unowned.class,()->forward(a));assertEquals(SonicCdAct2PastObjectPass.Boundary.RESULTS_FLAGS,e.boundary());assertEquals(2,a.core().state().level().levelRestart());assertEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,a.core().state().phase());assertFalse(a.core().state().calls().contains(SonicCdAct2PastLoadingVisit.Call.OLD_EXIT_PRELUDE));assertTrue(a.frame().closed());
    }
    @Test void completedWalkRunsLaterPhysicalResultsBeforeOldPreludeWithoutDrainingTheCollector()throws Exception{
        var a=prepared();var p=pool(a);p.write(p.state(127).moveByte(ID,0x3A).moveByte(ROUTINE,4).moveByte(MAP_FRAME,1).moveWord(X,0x7FFC).moveWord(RESULTS_DEST_X,0x8004).moveWord(RESULTS_TIMER_WORD,473));var later=p.state(127);var v=a.host().video();forward(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_BLACK_FADE);assertArrayEquals(later.addWord(X,-8).addWord(RESULTS_TIMER_WORD,-1).copyBytes(),p.state(127).copyBytes());assertSame(v.completedCpu(),a.host().video().completedCpu());assertSame(v.cpuSat(),a.host().video().cpuSat());assertNull(actualField(pass(a),"queue"));assertFalse(a.core().state().calls().contains(SonicCdAct2PastLoadingVisit.Call.OLD_EXIT_FADE_SERVICE));
    }
    @Test void oldPreludeKeepsActualResultsRawTwoTwoGatesAndTheRetainedSource()throws Exception{
        var a=prepared();var source=a.frame().stageSource();long clock=clock(a);int frames=frames(a);forward(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_BLACK_FADE);var p=a.h().module().levelProgramState();assertEquals(2,a.frame().environment().movement().gates().zoneAct());assertEquals(2,a.frame().environment().movement().gates().timeZone());assertSame(source,a.frame().stageSource());assertEquals(0,p.demoMode());assertEquals(0,p.usePlayer2());assertEquals(0,p.levelStarted());assertEquals(0x80,p.gameMode()&0x80);assertEquals(1,p.palFadeFlags()&1);assertEquals(0,p.whiteFadeSteps());assertEquals(2,a.core().state().level().levelRestart());assertEquals(clock+1,clock(a));assertEquals((frames+1)&65535,frames(a));assertFalse(a.frame().paused());
    }
    @Test void nativeClearPlcsPreservesCurrentPatternsAndDecoderTableButAbandonsQueuedWork()throws Exception{
        var a=prepared();var registry=a.h().mode().getRewindRegistry();var full=registry.capture();
        // Measure the same genuine pre-body V-int and ordinary prefix without Results. Current art
        // may be written there; ClearPLCs preserves the reached image, not the earlier closed cut.
        pool(a).clearDynamicSlots();forward(a);var expected=a.core().state().section();
        registry.restore(full);forward(a);var after=a.core().state().section();
        assertEquals(expected.residency(),after.residency());assertArrayEquals(expected.codeTable().copy(),after.codeTable().copy());assertEquals(0,after.queue().headSource());assertEquals(0,after.remainingTilesWord());assertTrue(after.decoder().isEmpty());assertEquals(SonicCdAct2PastLoadingVisit.Call.OLD_EXIT_CLEAR_PLCS,a.core().state().calls().getLast());
    }
    @Test void twentyTwoBlackWaitsPublishBeforeColourArithmeticAndDoNotRunSonicOrMain()throws Exception{
        var a=entered();var actor=a.frame().captureActor();var placement=poolRuntime(a).capture();int frames=frames(a);long clock=clock(a);var target=a.core().state().fadePalette();for(int n=1;n<=22;n++){var before=a.core().state();var expected=CdLevelPaletteFade.stepToBlack(new CdLevelPaletteFade.Palettes(before.normalPalette(),before.waterPalette()));forwardExit(a);healthy(a,n==22?SonicCdAct2PastLoadingVisit.Phase.OLD_CLEAR_WAIT:SonicCdAct2PastLoadingVisit.Phase.OLD_BLACK_FADE);assertArrayEquals(before.normalPalette().copy(),a.core().state().displayedPalette().copy());assertArrayEquals(expected.palette().copy(),a.core().state().normalPalette().copy());assertArrayEquals(expected.waterPalette().copy(),a.core().state().waterPalette().copy());assertSame(target,a.core().state().fadePalette());assertEquals(n,a.h().module().levelProgramState().whiteFadeSteps());assertEquals(clock+n,clock(a));assertEquals(frames,frames(a));assertEquals(actor,a.frame().captureActor());assertPlacement(placement,poolRuntime(a).capture());}assertEquals(0,a.core().state().level().levelRestart());assertEquals(0,a.h().module().levelProgramState().palFadeFlags());
    }
    @Test void finalSelectorCCompletesOneRealLoadWaitAndSemanticReturnWithoutBodyOrArtPulse()throws Exception{
        var a=cleared();var actor=a.frame().captureActor();var placement=poolRuntime(a).capture();var art=a.frame().artState();int frames=frames(a);long clock=clock(a);forwardExit(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_RETURNED);assertEquals(clock+1,clock(a));assertEquals(frames,frames(a));assertEquals(actor,a.frame().captureActor());assertPlacement(placement,poolRuntime(a).capture());assertSame(art,a.frame().artState());assertArrayEquals(new byte[128],a.core().state().displayedPalette().copy());assertEquals(0xFF,a.h().module().act2PastOldProgrammeReturnCode());assertEquals(SonicCdAct2PastLoadingVisit.Call.OLD_EXIT_RETURN,a.core().state().calls().getLast());
    }
    @Test void ipxBlankStopsFmAndDisablesTheSelectedPictureWithoutAnyOldFrameReadFinishOrClock()throws Exception{
        var a=returned();var actor=a.frame().captureActor();var shared=a.frame().captureShared();var source=a.frame().stageSource();var section=a.core().state().section();var palette=a.core().state().normalPalette();var picture=((SonicCdAct2PastWorldHost.Published)a.host().selected()).selection().inputs();var oldSounds=a.host().sounds();forwardExit(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED);assertEquals(actor,a.frame().captureActor());assertEquals(shared,a.frame().captureShared());assertSame(source,a.frame().stageSource());assertSame(section,a.core().state().section());assertSame(palette,a.core().state().normalPalette());var selected=((SonicCdAct2PastWorldHost.Published)a.host().selected()).selection().inputs();assertFalse(selected.displayEnabled());assertSame(picture.selectedSat(),selected.selectedSat());assertSame(picture.palette(),selected.palette());assertEquals(oldSounds.size()+1,a.host().sounds().size());assertEquals(new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM),a.host().sounds().getLast());assertEquals(0xFF,a.h().module().act2PastOldProgrammeReturnCode());
    }
    @Test void actualNextActAdmissionReplacesTheHealthyBlankWithOneSameWorldR13FullEpoch()throws Exception{
        var a=returned();forwardExit(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED);
        var module=a.h().module();var mode=a.h().mode();var world=mode.getWorldSession();var manager=mode.getLevelManager();
        long generation=manager.getCompletedProductionLoadGeneration();var oldSource=a.frame().stageSource();
        var oldState=a.core().state();var oldProgramme=module.programServicesState().completedCalls();
        var actualData=assertInstanceOf(CdGameDataSource.class,world.getDataSource());
        var selected=actualData.act3FutureAssets().orElseThrow().select(oldState.program().goodFutureByte());
        forwardExit(a); // Genuine Module RunR13/FULL caller; the old unsupported assertion is obsolete.
        assertSame(world,mode.getWorldSession());assertEquals(generation+1,manager.getCompletedProductionLoadGeneration());
        assertTrue(a.frame().closed());assertSame(oldState,a.core().state());
        assertTrue(module.act3FutureLoadingSelected());var nextFrame=module.requirePlayerFrame();var nextCore=module.act3FutureLoadingVisit();
        assertNotSame(a.frame(),nextFrame);assertNotSame(oldSource,nextFrame.stageSource());assertFalse(nextFrame.closed());assertFalse(nextCore.failed());
        assertSame(selected,nextCore.assets());assertTrue(nextFrame.stageSource().ownsAct3Future(selected));
        assertSame(selected,assertInstanceOf(com.openggf.game.soniccd.level.SonicCdAct3FutureLevel.class,manager.getCurrentLevel()).assets());
        assertEquals(2,nextFrame.environment().movement().gates().zoneAct());assertEquals(2,nextFrame.environment().movement().gates().timeZone());
        assertEquals(SonicCdGameModule.LevelProgramPhase.ACT3_FUTURE_LOADING,module.levelProgramState().phase());
        var completed=module.programServicesState().completedCalls();assertEquals(oldProgramme,completed.subList(0,oldProgramme.size()));
        assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,
                SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS),completed.subList(oldProgramme.size(),completed.size()));
        forwardExit(a); // Repeated public forward services the new retained visit, with no second FULL.
        assertSame(nextFrame,module.requirePlayerFrame());assertSame(nextCore,module.act3FutureLoadingVisit());
        assertEquals(generation+1,manager.getCompletedProductionLoadGeneration());assertSame(world,mode.getWorldSession());
        assertFalse(nextFrame.closed());assertFalse(nextCore.failed());
    }
    @Test void fullAndCourseReplayRetainTheSameOwnersAcrossPreludeFadeClearReturnAndBlank()throws Exception{
        var a=prepared();replay(a);for(int n=0;n<10;n++)forwardExit(a);replay(a);for(int n=11;n<21;n++)forwardExit(a);replay(a);replay(a);replay(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED);
    }
    @Test void equalValuesAndIdleCallbacksCannotMintTheActualCompletedWalkOrExitStoreAuthority()throws Exception{
        var a=prepared();var before=a.h().mode().getRewindRegistry().capture();assertThrows(IllegalStateException.class,a.core()::requireOldExitStore);assertThrows(IllegalStateException.class,()->pass(a).abandonCompletedWalk());assertThrows(IllegalStateException.class,()->a.h().module().prepareAct2PastOldExitReset(a.core()));assertThrows(IllegalStateException.class,a.host()::prepareOldExitBlank);assertThrows(IllegalStateException.class,()->a.h().module().act2PastOldProgrammeReturnCode());values(before,a.h().mode().getRewindRegistry().capture(),"idle exit rejection");assertFalse(a.frame().closed());
    }
    @Test void oldSuspendedImageRejectsIdleScalarAndBlankReentryWithoutClosingTheFrame()throws Exception{
        var a=entered();var before=a.h().mode().getRewindRegistry().capture();assertThrows(IllegalStateException.class,a.core()::requireOldExitStore);assertThrows(IllegalStateException.class,a.host()::prepareOldExitBlank);assertThrows(IllegalStateException.class,()->a.h().module().prepareAct2PastOldExitReset(a.core()));values(before,a.h().mode().getRewindRegistry().capture(),"suspended exit rejection");assertFalse(a.frame().closed());
    }
    @Test void laterPhysicalFaultKeepsTheResultsProgrammePrefixAndCannotEnterOldLevelStartEarly()throws Exception{
        var a=prepared();var p=pool(a);p.write(p.state(127).moveByte(ID,0x3A).moveByte(ROUTINE,3));var bad=p.state(127);var e=assertThrows(CdResults.UnsupportedResults.class,()->forward(a));assertEquals(CdResults.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertArrayEquals(bad.copyBytes(),p.state(127).copyBytes());assertEquals(2,a.core().state().level().levelRestart());assertEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,a.core().state().phase());assertFalse(a.core().state().calls().contains(SonicCdAct2PastLoadingVisit.Call.OLD_EXIT_PRELUDE));assertTrue(a.frame().closed());
    }
    @Test void ordinaryR12bPairCannotEnterOldExitServicesBeforeActualResultsGatesTwoTwo()throws Exception{
        var a=loaded();var before=a.h().mode().getRewindRegistry().capture();var e=assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->a.frame().serviceOldProgrammeExitPaletteFade(8,a.frame().state(),a.frame().environment(),()->fail("inadmissible publication"),()->fail("inadmissible PLC")));assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,e.boundary());values(before,a.h().mode().getRewindRegistry().capture(),"strict R12B old-source gate");assertFalse(a.frame().closed());
    }
    @Test void actualR12bFrameComponentErrorRetainsItsControllerPrefixAndOriginalThrowable()throws Exception{
        var a=entered();var state=a.core().state();var actor=a.frame().captureActor();long clock=clock(a);var original=new AssertionError("explicit actual-source component fast-PLC fault");var order=new ArrayList<String>();var e=assertThrows(AssertionError.class,()->a.frame().serviceOldProgrammeExitPaletteFade(24,a.frame().state(),a.frame().environment(),()->order.add("publication"),()->{order.add("fast");throw original;}));assertSame(original,e);assertEquals(List.of("publication","fast"),order);assertEquals(24,a.frame().environment().controls().rawHeld());assertEquals(clock,clock(a));assertEquals(actor.state(),a.frame().state());assertSame(state,a.core().state());assertTrue(a.frame().closed());
    }
    @Test void actualR12bFrameComponentLoadFaultStopsBeforeFastPlcAndDoesNotIncrementItsClock()throws Exception{
        var a=cleared();var original=new IllegalArgumentException("explicit actual-source component DrawLevel fault");long clock=clock(a);var order=new ArrayList<String>();var e=assertThrows(IllegalArgumentException.class,()->a.frame().serviceOldProgrammeExitLoad(8,a.frame().state(),a.frame().environment(),()->order.add("publication"),()->{order.add("copy/draw");throw original;},()->order.add("fast")));assertSame(original,e);assertEquals(List.of("publication","copy/draw"),order);assertEquals(clock,clock(a));assertTrue(a.frame().closed());assertEquals(SonicCdAct2PastLoadingVisit.Phase.OLD_CLEAR_WAIT,a.core().state().phase());
    }
    @Test void explicitRetirementRejectsExitCaptureAndRestoreWithoutDestroyingTheRetainedPrefix()throws Exception{
        var a=entered();var snapshot=a.core().capture();var state=a.core().state();a.frame().close();assertThrows(IllegalStateException.class,a.core()::capture);assertThrows(IllegalStateException.class,()->a.core().restore(snapshot));assertSame(state,a.core().state());
    }

    static A prepared()throws Exception{var a=loaded();knownProgrammeFlags(a);result(a,8,77);return a;}
    /** Explicit constructed Results programme input only: do not infer the otherwise unknown flag BYTE.
     * All other LevelProgramState fields and every cached owner stay actual. No operation is invoked. */
    static void knownProgrammeFlags(A a)throws Exception{var f=SonicCdGameModule.class.getDeclaredField("levelProgram");f.setAccessible(true);var p=(SonicCdGameModule.LevelProgramState)f.get(a.h().module());f.set(a.h().module(),new SonicCdGameModule.LevelProgramState(p.demoMode(),p.timeStones(),p.specialStage(),p.usePlayer2(),p.palFadeFlags(),p.gameMode(),p.levelStarted(),p.whiteFadeSteps(),p.phase(),OptionalInt.of(0)));}
    static A entered()throws Exception{var a=prepared();forward(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_BLACK_FADE);return a;}
    static A cleared()throws Exception{var a=entered();for(int n=0;n<22;n++)forwardExit(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_CLEAR_WAIT);return a;}
    static A returned()throws Exception{var a=cleared();forwardExit(a);healthy(a,SonicCdAct2PastLoadingVisit.Phase.OLD_RETURNED);return a;}
    static void forwardExit(A a){step(a.h(),right());}
    static long clock(A a){return a.frame().vIntClock().absoluteValue().orElseThrow();}
    static int frames(A a){return a.frame().environment().globals().levelFrames();}
    static SonicCdAct2PastObjectPass pass(A a)throws Exception{return (SonicCdAct2PastObjectPass)actualField(actualField(a.core(),"ordinary"),"objects");}
    static void healthy(A a,SonicCdAct2PastLoadingVisit.Phase p)throws Exception{assertEquals(p,a.core().state().phase());assertFalse(a.frame().closed());assertFalse(a.core().failed());assertSame(a.frame(),a.h().module().requirePlayerFrame());assertNull(actualField(a.core(),"activeOrdinary"));assertFalse((boolean)actualField(a.core(),"running"));}
    static void assertPlacement(CdObjectPlacementRuntime.Snapshot expected,CdObjectPlacementRuntime.Snapshot actual){assertEquals(expected.status(),actual.status());assertArrayEquals(expected.savedFlags().copy(),actual.savedFlags().copy());assertEquals(expected.pool().slots(),actual.pool().slots());}
    static void replay(A a)throws Exception{var r=a.h().mode().getRewindRegistry();var full=r.capture();var course=r.captureCourse();forwardExit(a);var expected=r.capture();var nativeAfter=captureNative(a.h());r.restore(full);forwardExit(a);assertNativeCapture(nativeAfter,a.h());values(expected,r.capture(),"old exit FULL");r.restoreCourse(course);forwardExit(a);assertNativeCapture(nativeAfter,a.h());values(expected,r.capture(),"old exit course");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
