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
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.A;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.loaded;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.seed;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.forward;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.sameChunk;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.sounds;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.values;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/** Actual source World services; public-pool component ingress only AFTER real loading/Init/Main.
 * No queue/current/clock/physics/programme preimage ingress. All Java UNRUN.
 * N1ecc5f3a Results/Main.asm:42-366; LoadPLC Functions (General).asm:588-614.
 */
@Isolated
class TestSonicCdR12bResultsPass {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media");assertTrue(Files.isDirectory(Path.of(root)),"configured bad root must fail");fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void signpostChildRunsLaterInTheSamePhysicalWalkAndDecrementsTheActualHighDelay()throws Exception{
        var a=loaded();signpostBonus(a);assertEquals(0x3A,pool(a).state(33).id());assertEquals(15,pool(a).state(33).unsignedByte(RESULTS_TIMER));assertEquals(0,pool(a).state(33).routine());assertEquals(0,a.core().state().section().queue().headSource());assertFalse(a.frame().closed());
    }
    @Test void initDelayChangesOnlyHighByteAndReturnsWithoutDrawingOrSubmittingCue()throws Exception{
        var a=loaded();result(a,0,0x02A5);var before=pool(a).state(32);forward(a);assertArrayEquals(before.addByte(RESULTS_TIMER,-1).copyBytes(),pool(a).state(32).copyBytes());assertEquals(0,a.core().state().section().queue().headSource());assertTrue(endSounds(a).isEmpty());
    }
    @Test void expiredInitSubmitsActualCue10BeforeRoutineTwoAndUsesRealLaterPreparation()throws Exception{
        var a=loaded();result(a,0,0x017B);forward(a);assertEquals(2,pool(a).state(32).routine());assertEquals(0x7B,pool(a).state(32).word(RESULTS_TIMER_WORD));var s=a.core().state().section();assertEquals(0x7880,s.queue().headDestination());assertEquals(s.decoder().orElseThrow().sourcePointer(),s.queue().headSource());for(int at=6;at<96;at++)assertEquals(0,s.queue().bytes().unsignedByte(at));
    }
    @Test void sixteenActualProjectorCuesKeepTheOriginalResultsSourceScan96AndNoStoreCore()throws Exception{
        var a=loaded();sameChunk(a);var p=pool(a);p.clearDynamicSlots();int camera=a.host().scroll().foreground().cameraX();for(int n=0;n<16;n++)p.write(p.state(32+n).moveByte(ID,0x2E).moveWord(X,(camera+120)&65535).moveWord(Y,0x7000));p.write(p.state(127).moveByte(ID,0x3A).moveWord(RESULTS_TIMER_WORD,0x01A5));var e=assertThrows(CdR12bSectionArt.SectionArtException.class,()->forward(a));var b=assertInstanceOf(CdR11aPlcBuffer.BufferBoundary.class,e.getCause());assertEquals(CdR11aPlcBuffer.Access.SOURCE_SCAN,b.access());assertEquals(96,b.byteOffset());assertEquals(0,p.state(127).routine());assertEquals(0xA5,p.state(127).word(RESULTS_TIMER_WORD));assertFalse(a.core().state().section().stopped());assertArrayEquals(e.state().queue().bytes().copy(),a.core().state().section().queue().bytes().copy());retired(a);
    }
    @Test void waitUsesActualRawHeadAndDoesNotInventReadinessOrAnUnconditionalDrawTail()throws Exception{
        var a=loaded();result(a,0,0x0100);forward(a);var before=pool(a).state(32);assertNotEquals(0,a.core().state().section().queue().headSource());forward(a);assertArrayEquals(before.copyBytes(),pool(a).state(32).copyBytes());assertTrue(endSounds(a).isEmpty());
    }
    @Test void zeroHeadUsesActualLiveUnsignedCameraEdgeAndRetainsInsideWaitSource()throws Exception{
        var a=loaded();result(a,2,0xBEEF);var expected=pool(a).state(32);forward(a);int edge=(a.host().scroll().foreground().cameraX()+336)&65535;assertTrue((a.frame().state().motion().centreX16_16()>>>16)<=edge,"This genuine early Main is the inside-edge branch, no camera/player seed");assertArrayEquals(expected.copyBytes(),pool(a).state(32).copyBytes());
    }
    @Test void moveUsesSignedDestinationStepAndTheWord472DrawThreshold()throws Exception{
        var a=loaded();result(a,4,480);pool(a).write(pool(a).state(32).moveWord(X,0xFFF8).moveWord(RESULTS_DEST_X,8).moveWord(RESULTS_SCREEN_Y_WORD,204));var old=pool(a).state(32);forward(a);assertArrayEquals(old.addWord(RESULTS_TIMER_WORD,-1).addWord(X,8).copyBytes(),pool(a).state(32).copyBytes());assertTrue(endSounds(a).isEmpty());
    }
    @Test void arrivedMainRowAdvancesWithoutRunningBonusInThatSameDispatch()throws Exception{
        var a=loaded();result(a,4,480);pool(a).write(pool(a).state(32).moveWord(RESULTS_DEST_X,pool(a).state(32).centreX()).moveByte(MAP_FRAME,0));forward(a);assertEquals(6,pool(a).state(32).routine());assertEquals(479,pool(a).state(32).word(RESULTS_TIMER_WORD));assertEquals(0,a.host().globals().bonus().orElseThrow().updateHudBonusByte());
    }
    @Test void noBonusDecrementsToThirtyReadsActualSpecialStageAndDrawsWithScreenY0a()throws Exception{
        var a=loaded();result(a,6,31);forward(a);assertEquals(30,pool(a).state(32).word(RESULTS_TIMER_WORD));assertTrue(a.host().video().completedCpu().orElseThrow().provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32),"Actual screen-mode row uses WORD0A despite offscreen worldY7000");assertEquals(1,a.host().globals().bonus().orElseThrow().updateHudBonusByte());assertTrue(endSounds(a).isEmpty());assertFalse(a.frame().closed());
    }
    @Test void noBonusNegativeWordAdvancesToEightAndDoesNotReplayProgrammeStores()throws Exception{
        var a=loaded();result(a,6,0);forward(a);assertEquals(8,pool(a).state(32).routine());assertEquals(65535,pool(a).state(32).word(RESULTS_TIMER_WORD));assertEquals(0,a.core().state().level().levelRestart());
    }
    @Test void actualSignpostBonusSubtractsOnceAndUsesTheSharedScoreAndTallyKernel()throws Exception{
        var a=loaded();signpostBonus(a);int time=a.host().globals().bonus().orElseThrow().timeBonusWord(),rings=a.host().globals().bonus().orElseThrow().ringBonusWord();assertTrue(time!=0||rings!=0);var g=a.host().globals();var expected=CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(g.ring().rings(),g.ring().lives(),g.ring().updateHudLives(),g.score().score(),g.score().nextLifeScore(),g.score().updateHudScore()),(time==0?0:10)+(rings==0?0:10));result(a,6,0x0101);forward(a);var after=a.host().globals();assertEquals(time==0?0:(time-100)&65535,after.bonus().orElseThrow().timeBonusWord());assertEquals(rings==0?0:(rings-100)&65535,after.bonus().orElseThrow().ringBonusWord());assertEquals(expected.world().score(),after.score().score());assertEquals(expected.world().lives(),after.ring().lives());assertEquals(expected.world().nextLifeScore(),after.score().nextLifeScore());assertTrue(endSounds(a).isEmpty(),"HIGH timer BYTE1 suppresses tally, independent of WORD parity");
    }
    @Test void bonusWithHighTimerByteZeroCompletesOneNativeTallySound()throws Exception{
        var a=loaded();signpostBonus(a);result(a,6,2);forward(a);assertEquals(List.of(CdResults.Sound.FM_TALLY),endSounds(a));assertEquals(1,pool(a).state(32).word(RESULTS_TIMER_WORD));
    }
    @Test void nextLevelPreservesReachedProgrammePrefixAndStopsAtUnknownFlagsOrPostWalkFlow()throws Exception{
        var a=loaded();result(a,8,77);Throwable e=assertThrows(UnsupportedOperationException.class,()->forward(a));if(e instanceof SonicCdAct2PastOrdinaryWorld.Stopped stopped)assertEquals(SonicCdAct2PastOrdinaryWorld.Boundary.LEVEL_FLOW,stopped.boundary());else assertEquals(SonicCdAct2PastObjectPass.Boundary.RESULTS_FLAGS,assertInstanceOf(SonicCdAct2PastObjectPass.Unowned.class,e).boundary());assertEquals(2,a.core().state().level().levelRestart());assertEquals(0,a.core().state().section().sectionWord());assertEquals(0,a.core().state().level().spawnMode());assertEquals(0,a.core().state().level().flowerCount());assertEquals(0,a.core().state().level().fadeEnableDisplay());assertEquals(0,a.core().state().level().checkpoint());assertEquals(0,a.core().state().level().plcLoadFlags()&2);assertEquals(2,a.frame().environment().movement().gates().zoneAct());assertEquals(2,a.frame().environment().movement().gates().timeZone());var g=globalsRetired(a);assertEquals(0,g.projectorByte());assertEquals(0,g.tail().orElseThrow().unknownLevelByte());assertEquals(1,poolRuntime(a).capture().savedFlags().unsignedByte(0));assertEquals(1,poolRuntime(a).capture().savedFlags().unsignedByte(1));retired(a);
    }
    @Test void idleClonesAndRepeatedWalkCannotMintResultsOrModuleFlagAuthority()throws Exception{
        var a=loaded();result(a,2,0);var pass=pass(a);var body=(CdResults)actualField(pass,"results");var read=(CdResults.NativeRead)actualField(body,"nativeRead");var before=a.h().mode().getRewindRegistry().capture();var programme=(SonicCdGameModule.LevelProgramState)actualField(a.h().module(),"levelProgram");assertThrows(IllegalStateException.class,()->a.h().module().prepareAct2PastResultsGoodFutureFlags(a.core(),a.frame(),pass,null,programme,0));for(int n=0;n<2;n++)assertThrows(IllegalStateException.class,()->read.read(new CdResults.ReadCall(CdResults.ReadField.PLC_HEAD_LONG,poolRuntime(a).capture(),pool(a).state(32))));for(int n=0;n<2;n++)assertThrows(IllegalStateException.class,()->pass.runWalk(a.frame().environment().globals().levelFrames()));values(before,a.h().mode().getRewindRegistry().capture(),"idle Results rejection");
    }
    @Test void fullAndCourseReplayPreserveActualResultsValuesWithoutNewCaptureOwners()throws Exception{
        var a=loaded();signpostBonus(a);result(a,6,2);var r=a.h().mode().getRewindRegistry();var full=r.capture();var course=r.captureCourse();forward(a);var expected=r.capture();var nativeAfter=captureNative(a.h());r.restore(full);forward(a);assertNativeCapture(nativeAfter,a.h());values(expected,r.capture(),"Results FULL");r.restoreCourse(course);forward(a);assertNativeCapture(nativeAfter,a.h());values(expected,r.capture(),"Results course");
    }
    @Test void invalidRoutineRetainsOriginalFull64AndRetiresEveryTransientReceipt()throws Exception{
        var a=loaded();result(a,3,0xA5B6);var before=pool(a).state(32);var e=assertThrows(CdResults.UnsupportedResults.class,()->forward(a));assertEquals(CdResults.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertArrayEquals(before.copyBytes(),pool(a).state(32).copyBytes());retired(a);
    }

    static CdObjectPlacementRuntime poolRuntime(A a)throws Exception{return (CdObjectPlacementRuntime)actualField(a.host(),"placement");}
    static CdObjectPool pool(A a)throws Exception{return poolRuntime(a).pool();}
    static SonicCdAct2PastObjectPass pass(A a)throws Exception{return (SonicCdAct2PastObjectPass)actualField(actualField(a.core(),"ordinary"),"objects");}
    static SonicCdAct2PastWorldHost.Globals globalsRetired(A a)throws Exception{var image=actualField(a.host(),"image");var m=image.getClass().getDeclaredMethod("globals");m.setAccessible(true);return (SonicCdAct2PastWorldHost.Globals)m.invoke(image);}
    static List<CdResults.Sound> endSounds(A a)throws Exception{return sounds(a).stream().filter(SonicCdGameModule.PastResultsSound.class::isInstance).map(SonicCdGameModule.PastResultsSound.class::cast).map(SonicCdGameModule.PastResultsSound::sound).toList();}
    static void retired(A a)throws Exception{assertNull(actualField(pass(a),"resultsInvocation"));assertNull(actualField(pass(a),"pendingResultsPlc"));assertTrue(a.frame().closed());}
    static void result(A a,int routine,int timer)throws Exception{seed(a,0x3A,routine);pool(a).write(pool(a).state(32).moveWord(RESULTS_TIMER_WORD,timer).moveWord(X,288).moveWord(Y,0x7000).moveLong(MAPPINGS,0x20DEEC).moveWord(TILE,0x83C4).moveWord(RESULTS_SCREEN_Y_WORD,204));}
    static void signpostBonus(A a)throws Exception{seed(a,0x17,6);pool(a).write(pool(a).state(32).moveByte(LEVEL_END_TIMER,1).moveWord(Y,0x7000).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveByte(WIDTH,24).moveByte(Y_RADIUS,32).moveLong(MAPPINGS,0x21D9B4).moveWord(TILE,0x843C));forward(a);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
