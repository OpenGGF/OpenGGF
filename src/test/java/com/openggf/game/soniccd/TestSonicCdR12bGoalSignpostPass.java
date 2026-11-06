package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
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
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.drawBitOnly;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.forward;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.sameChunk;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.sounds;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.values;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine cold/target loading and Sonic Init/Main precede explicit PUBLIC-POOL COMPONENT
 * INGRESS at an unchanged spawner chunk. No primary/camera/cursor/global/queue writes.
 * Timer boundary ingress is not elapsed180-frame qualification. Java UNRUN.
 * N1ecc5f3a Level/_Objects/Level End/Main.asm:570-702,708-907;
 * Functions (General).asm:588-614 owns the finite descriptor append.
 */
@Isolated
class TestSonicCdR12bGoalSignpostPass {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media root");assertTrue(Files.isDirectory(Path.of(root)),"configured bad root must fail");fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void goalInitKeepsActual8381FrameFiveAndFarVerticalMainCannotSubmitArt()throws Exception{
        var a=loaded();seed(a,0x16,0);var p=a.host().placement().pool();p.write(p.state(32).moveWord(Y,0x7000).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678));var old=p.state(32);forward(a);
        drawBitOnly(old.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x21D9B4).moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,32).moveByte(MAP_FRAME,5).moveWord(TILE,0x8381),p.state(32));assertEquals(0,a.core().state().section().queue().headSource());assertTrue(sounds(a).isEmpty());
    }
    @Test void reachedGoalPublishesFrameDestinationAndContactBeforeTheActualThreeRowAppend()throws Exception{
        var a=loaded();seed(a,0x16,0);int camera=a.host().scroll().foreground().cameraX();var old=a.core().state().section();forward(a);
        assertEquals(4,a.host().placement().pool().state(32).routine());assertEquals(camera,a.frame().environment().movement().bounds().left());assertEquals(camera,a.host().scroll().foreground().destLeftBound());assertEquals(0,a.frame().environment().globals().timeWarpTimer());assertEquals(0,a.frame().environment().movement().gates().timeWarpDirection());assertEquals(0,a.host().globals().contact().timeWarp());
        var after=a.core().state().section();var cue=terrain(a).loadPlan().plcs().get(0x12).entries();assertEquals(cue.getFirst().vramAddress(),after.queue().headDestination());assertCue(after.queue().bytes(),6,cue.subList(1,3),2);assertEquals(after.decoder().orElseThrow().sourcePointer(),after.queue().headSource());assertTrue(sounds(a).isEmpty());assertFalse(a.frame().closed());
    }
    @Test void goalAcceptsEqualUnsignedXAtTheActualNextSonicMainCut()throws Exception{
        var a=loaded();var control=nextMain(a);seed(a,0x16,0);a.host().placement().pool().write(a.host().placement().pool().state(32).moveWord(X,control.motion().centreX16_16()>>>16).moveWord(Y,control.motion().centreY16_16()>>>16));forward(a);assertEquals(4,a.host().placement().pool().state(32).routine());
    }
    @Test void goalRejectsPositive128VerticalDeltaBeforeAnyPlayerXOrGoalStore()throws Exception{
        var a=loaded();var control=nextMain(a);seed(a,0x16,0);a.host().placement().pool().write(a.host().placement().pool().state(32).moveWord(Y,((control.motion().centreY16_16()>>>16)-128)&65535));var bound=a.frame().environment().movement().bounds().left();forward(a);assertEquals(2,a.host().placement().pool().state(32).routine());assertEquals(bound,a.frame().environment().movement().bounds().left());assertEquals(0,a.core().state().section().queue().headSource());
    }
    @Test void completedGoalDoesNotRepeatItsProgrammeStoresOrPlcSubmission()throws Exception{
        var a=loaded();goalHeader(a,4);var before=a.core().state().section();forward(a);assertEquals(4,a.host().placement().pool().state(32).routine());for(int at=0;at<96;at+=6)assertNotEquals(terrain(a).sectionSources().source(CdR12bSectionSources.SourceId.ART_SIGNPOST).nativeAddress(),a.core().state().section().queue().bytes().unsignedLong(at));assertTrue(sounds(a).isEmpty());
    }
    @Test void invalidGoalRoutinePreservesOriginalSourceAndRetiresItsTransientInvocation()throws Exception{
        var a=loaded();seed(a,0x16,3);var old=a.host().placement().pool().state(32);var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,()->forward(a));assertEquals(CdGoalPost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertArrayEquals(old.copyBytes(),pool(a).state(32).copyBytes());assertNull(actualField(pass(a),"endInvocation"));assertTrue(a.frame().closed());
    }
    @Test void fourteenRealProjectorSubmissionsRetainTwoCompletedGoalDescriptorsAtSourceWrite96()throws Exception{capacity(14,2);}
    @Test void fifteenRealProjectorSubmissionsRetainOneCompletedGoalDescriptorAtSourceWrite96()throws Exception{capacity(15,1);}
    @Test void sixteenRealProjectorSubmissionsKeepTheNoStoreCoreQueueAtSourceScan96()throws Exception{capacity(16,0);}
    @Test void strictTerminalValidatorRejectsAnEarlierHealthyPreimageAndStoppedStateCannotResume()throws Exception{
        var a=loaded();var before=a.core().state().section();var art=art(a);fill(a,14);var e=assertThrows(CdR12bSectionArt.SectionArtException.class,()->forward(a));assertSame(e.state(),a.core().state().section());assertThrows(IllegalArgumentException.class,()->art.validateGoalAppendFailure(before,e));assertThrows(IllegalStateException.class,()->art.withSectionWord(e.state(),e.state().sectionWord()));assertTrue(a.frame().closed());
    }
    @Test void signpostInitUsesActualSharedRootAnd843cWithoutStoppingTheTimerWhenFarVertically()throws Exception{
        var a=loaded();seed(a,0x17,0);pool(a).write(pool(a).state(32).moveWord(Y,0x7000));int update=a.core().state().level().updateHudTime();forward(a);assertEquals(2,pool(a).state(32).routine());assertEquals(0x21D9B4,pool(a).state(32).longBits(MAPPINGS));assertEquals(0x843C,pool(a).state(32).word(TILE));assertEquals(update,a.core().state().level().updateHudTime());assertTrue(sounds(a).isEmpty());
    }
    @Test void signpostRequiresStrictlyGreaterXAtTheSameActualMainCut()throws Exception{
        var a=loaded();var control=nextMain(a);seed(a,0x17,0);pool(a).write(pool(a).state(32).moveWord(X,control.motion().centreX16_16()>>>16).moveWord(Y,control.motion().centreY16_16()>>>16));forward(a);assertEquals(2,pool(a).state(32).routine());assertTrue(sounds(a).isEmpty());
    }
    @Test void signpostHitStoresNative120TimerPowerupClearsAndCompleted9dSound()throws Exception{
        var a=loaded();seed(a,0x17,0);int camera=a.host().scroll().foreground().cameraX();forward(a);assertEquals(4,pool(a).state(32).routine());assertEquals(120,pool(a).state(32).unsignedByte(LEVEL_END_TIMER));assertEquals(0,pool(a).state(32).unsignedByte(MAP_FRAME));assertEquals(0,a.core().state().level().updateHudTime());assertEquals(camera,a.frame().environment().movement().bounds().left());assertEquals(camera,a.host().scroll().foreground().destLeftBound());assertEquals(0,a.frame().environment().movement().gates().speedShoes());assertEquals(0,a.frame().environment().globals().invincible());assertEquals(List.of(CdSignpost.Sound.FM_SIGNPOST),endSounds(a));
    }
    @Test void spinExpiryUsesTheExistingAnimationAndSetsFrameThreeAndSixtyWithoutStartingResults()throws Exception{
        var a=loaded();signHeader(a,4,1);forward(a);assertEquals(6,pool(a).state(32).routine());assertEquals(3,pool(a).state(32).unsignedByte(MAP_FRAME));assertEquals(60,pool(a).state(32).unsignedByte(LEVEL_END_TIMER));assertTrue(endSounds(a).isEmpty());assertEquals(0,pool(a).state(33).id());
    }
    @Test void delayExpiryRunsTheRealSameWalkResultsInitAfterBonusesAndMusic()throws Exception{
        var a=loaded();signHeader(a,6,1);forward(a);assertFalse(a.frame().closed());assertEquals(8,pool(a).state(32).routine());assertEquals(180,pool(a).state(32).unsignedByte(LEVEL_END_TIMER));assertEquals(0x3A,pool(a).state(33).id());assertEquals(15,pool(a).state(33).unsignedByte(RESULTS_TIMER));assertEquals(0,pool(a).state(33).routine());
        assertEquals(List.of(CdSignpost.Sound.SCMD_FADEPCM,CdSignpost.Sound.SCMD_RESULTMUS),endSounds(a));assertEquals(1,a.frame().environment().controls().controlLocked()&1);assertEquals(8,a.frame().environment().controls().logicalHeld());assertEquals(8,a.frame().environment().controls().logicalTap());
        var g=retiredGlobals(a);var bonus=g.bonus().orElseThrow();int minutes=g.live().knownByte(CdCheckpointLiveAux.TimeByte.MINUTES.nativeAddress()).orElseThrow(),seconds=g.live().knownByte(CdCheckpointLiveAux.TimeByte.SECONDS.nativeAddress()).orElseThrow();int[] nativeBonus={50000,50000,10000,5000,4000,4000,3000,3000,2000,2000,2000,2000,1000,1000,1000,1000,500,500,500,500,0};assertEquals(nativeBonus[Math.min(((minutes*60+seconds)&65535)/15,20)],bonus.timeBonusWord());assertEquals((g.ring().rings()*100)&65535,bonus.ringBonusWord());assertEquals(1,bonus.updateHudBonusByte());assertNull(actualField(pass(a),"endInvocation"));assertFalse(a.frame().closed());assertNull(actualField(pass(a),"resultsInvocation"));
    }
    @Test void reachedGoalFullAndCourseReplayRestoreTheExistingOwnersWithoutNewCaptureKeys()throws Exception{
        var a=loaded();seed(a,0x16,0);var registry=a.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();forward(a);var expected=registry.capture();var expectedCourse=registry.captureCourse();var nativeAfter=captureNative(a.h());registry.restore(full);forward(a);assertNativeCapture(nativeAfter,a.h());values(expected,registry.capture(),"Goal FULL");registry.restoreCourse(course);forward(a);assertNativeCapture(nativeAfter,a.h());values(expected,registry.capture(),"Goal course->FULL");values(expectedCourse,registry.captureCourse(),"Goal course");
    }
    @Test void matchingPublicClonesAndRepeatedIdleWalksCannotMintAnEndInvocation()throws Exception{
        var a=loaded();goalHeader(a,2);var pass=pass(a);var p=a.host().placement();var source=p.pool().state(32);var signSource=p.pool().state(33).moveByte(ID,0x17);p.pool().write(signSource);var before=a.h().mode().getRewindRegistry().capture();var goal=(CdGoalPost)actualField(pass,"goalPost");var read=(CdGoalPost.PlayerWordRead)actualField(goal,"playerRead");
        for(int n=0;n<2;n++)assertThrows(IllegalStateException.class,()->read.read(new CdGoalPost.ReadCall(CdGoalPost.PlayerField.Y_WORD,p.capture(),source)));
        var sign=(CdSignpost)actualField(pass,"signpost");var store=(CdSignpost.EndOfActStore)actualField(sign,"endOfActStore");assertThrows(IllegalStateException.class,()->store.store(new CdSignpost.StoreCall(CdSignpost.StoreField.UPDATE_HUD_TIME_BYTE,0,p.capture(),signSource)));
        for(int n=0;n<2;n++)assertThrows(IllegalStateException.class,()->pass.runWalk(a.frame().environment().globals().levelFrames()));assertNull(actualField(pass,"endInvocation"));values(before,a.h().mode().getRewindRegistry().capture(),"idle End rejection");
    }
    @Test void invalidSignpostRoutineCannotPublishSoundsOrLeaveAnActiveReceipt()throws Exception{
        var a=loaded();seed(a,0x17,3);var old=pool(a).state(32);var e=assertThrows(CdSignpost.UnsupportedSignpost.class,()->forward(a));assertEquals(CdSignpost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertArrayEquals(old.copyBytes(),pool(a).state(32).copyBytes());assertTrue(endSounds(a).isEmpty());assertNull(actualField(pass(a),"endInvocation"));assertTrue(a.frame().closed());
    }

    static CdPlayerBody.State nextMain(A a)throws Exception{var r=a.h().mode().getRewindRegistry();var before=r.capture();forward(a);var value=a.frame().state();r.restore(before);return value;}
    static CdObjectPool pool(A a)throws Exception{return ((CdObjectPlacementRuntime)actualField(a.host(),"placement")).pool();}
    static CdR12bTerrainAssets terrain(A a)throws Exception{return (CdR12bTerrainAssets)actualField(a.core(),"terrain");}
    static CdR12bSectionArt art(A a)throws Exception{return (CdR12bSectionArt)actualField(a.core(),"art");}
    static SonicCdAct2PastObjectPass pass(A a)throws Exception{return (SonicCdAct2PastObjectPass)actualField(actualField(a.core(),"ordinary"),"objects");}
    static SonicCdAct2PastWorldHost.Globals retiredGlobals(A a)throws Exception{var image=actualField(a.host(),"image");var m=image.getClass().getDeclaredMethod("globals");m.setAccessible(true);return (SonicCdAct2PastWorldHost.Globals)m.invoke(image);}
    static List<CdSignpost.Sound> endSounds(A a)throws Exception{return sounds(a).stream().filter(SonicCdGameModule.PastSignpostSound.class::isInstance).map(SonicCdGameModule.PastSignpostSound.class::cast).map(SonicCdGameModule.PastSignpostSound::sound).toList();}
    static void goalHeader(A a,int routine)throws Exception{seed(a,0x16,routine);pool(a).write(pool(a).state(32).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveByte(WIDTH,16).moveByte(Y_RADIUS,32).moveByte(MAP_FRAME,5).moveWord(TILE,0x8381).moveLong(MAPPINGS,0x21D9B4));}
    static void signHeader(A a,int routine,int timer)throws Exception{seed(a,0x17,routine);pool(a).write(pool(a).state(32).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveByte(WIDTH,24).moveByte(Y_RADIUS,32).moveByte(LEVEL_END_TIMER,timer).moveWord(TILE,0x843C).moveLong(MAPPINGS,0x21D9B4));}
    static void fill(A a,int count)throws Exception{sameChunk(a);var p=a.host().placement().pool();p.clearDynamicSlots();int camera=a.host().scroll().foreground().cameraX(),x=a.frame().state().motion().centreX16_16()>>>16,y=a.frame().state().motion().centreY16_16()>>>16;for(int n=0;n<count;n++)p.write(p.state(32+n).moveByte(ID,0x2E).moveWord(X,(camera+120)&65535).moveWord(Y,0x7000));p.write(p.state(127).moveByte(ID,0x16).moveWord(X,(x-100)&65535).moveWord(Y,y));}
    static void capacity(int count,int completed)throws Exception{var a=loaded();var before=a.core().state().section();var meta=terrain(a).loadPlan().plcs().get(0x12).entries();fill(a,count);var e=assertThrows(CdR12bSectionArt.SectionArtException.class,()->forward(a));assertEquals(CdR12bSectionArt.Reason.DESCRIPTOR_BUFFER,e.reason());var cause=assertInstanceOf(CdR11aPlcBuffer.BufferBoundary.class,e.getCause());assertEquals(96,cause.byteOffset());assertEquals(completed==0?CdR11aPlcBuffer.Access.SOURCE_SCAN:CdR11aPlcBuffer.Access.SOURCE_WRITE,cause.access());assertCue(e.state().queue().bytes(),count*6,meta,completed);assertEquals(4,pool(a).state(127).routine());assertSame(before.codeTable(),e.state().codeTable());assertEquals(before.decoder(),e.state().decoder());assertEquals(before.savedDecoder(),e.state().savedDecoder());if(completed>0){assertSame(e.state(),a.core().state().section());assertTrue(a.core().state().section().stopped());}else{assertFalse(a.core().state().section().stopped());assertArrayEquals(e.state().queue().bytes().copy(),a.core().state().section().queue().bytes().copy());}assertEquals(0,e.getSuppressed().length);assertNull(actualField(pass(a),"pendingGoalPlc"));assertNull(actualField(pass(a),"endInvocation"));assertTrue(a.frame().closed());}
    static void assertCue(CdAssetBytes raw,int start,List<CdR12bLoadPlan.PlcEntry> entries,int count){for(int n=0;n<count;n++){assertEquals(entries.get(n).sourceNativeAddress(),raw.unsignedLong(start+6*n));assertEquals(entries.get(n).vramAddress(),raw.unsignedWord(start+6*n+4));}}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
