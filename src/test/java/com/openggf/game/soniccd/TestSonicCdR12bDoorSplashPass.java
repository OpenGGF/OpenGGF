package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdR12bDiscDoorSpinPass.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine source-bound World/Host/Frame loading, then declared PUBLIC-POOL COMPONENT ingress.
 * This is not an observed Door allocation, natural route, elapsed animation or pixel qualification.
 * Original pending/completion receipts own publication; descriptive results never grant a dispatch.
 * N1ecc5f3a Spin Tunnel/Main (Door).asm:135-177; complete map100/script24 at actual21D652/21D63A.
 * All Java UNRUN. Source tile0 is preserved; current-pattern appearance is not inferred.
 */
@Isolated
class TestSonicCdR12bDoorSplashPass {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media root");assertTrue(Files.isDirectory(Path.of(root)),"configured bad root must fail");fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void originalSubtypeZeroInitUsesNativeTileZeroFrameZeroAndExactlyOneA2()throws Exception{assertInit(0,0,CdTunnelDoorSplash.FmSound.FM_A2);}
    @Test void originalSubtypeOneInitUsesNativeTileZeroFrameZeroAndExactlyOneA2()throws Exception{assertInit(1,0,CdTunnelDoorSplash.FmSound.FM_A2);}
    @Test void originalSubtypeTwoInitUsesNativeTileZeroFrameSixAndExactlyOneA1()throws Exception{assertInit(2,6,CdTunnelDoorSplash.FmSound.FM_A1);}
    @Test void laterMainRetainsUnwrittenHeaderAndDefersAnUnselectedAnimationDirectoryRead()throws Exception{
        var a=loaded();header(a,2,0);var p=pool(a);p.write(p.state(32).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0xE2).moveByte(ANIMATION_TIME,5).moveByte(MAP_FRAME,0));
        var before=a.host().placement().capture();var source=p.state(32);forward(a);drawBitOnly(source.moveByte(ANIMATION_TIME,4),p.state(32));
        assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);assertFalse(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void nativeFcStillDrawsRoutineFourAndTheFollowingDispatchAloneDeletesAll64()throws Exception{
        var a=loaded();header(a,2,0);var p=pool(a);p.write(p.state(32).moveByte(ANIMATION_FRAME,5).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,2));var before=a.host().placement().capture();var source=p.state(32);
        forward(a);drawBitOnly(source.addByte(ROUTINE,2).moveByte(ANIMATION_TIME,3),p.state(32));assertEquals(4,p.state(32).routine());assertTrue(a.host().video().completedCpu().orElseThrow().provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32));assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);
        forward(a);assertArrayEquals(new byte[64],p.state(32).copyBytes());assertTrue(a.host().video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void directNativeDeleteDoesNotAllocateDrawPlayFmOrClearItsSavedLoadedBit()throws Exception{
        var a=loaded();header(a,4,0);var p=pool(a);p.write(p.state(32).moveByte(SAVED_FLAGS_ID,7));a.host().placement().bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);var before=a.host().placement().capture();
        forward(a);assertArrayEquals(new byte[64],p.state(32).copyBytes());assertTrue(splashSounds(a).isEmpty());assertTrue(a.host().video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));nonwrites(a,before,32);assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void reachedTileSubtypeFailureKeepsOnlyTheActualInitHeaderBeforeAnyFmOrDraw()throws Exception{
        var a=loaded();header(a,0,0);var p=pool(a);p.write(p.state(32).moveByte(SUBTYPE2,1));var before=a.host().placement().capture();var source=p.state(32);
        var e=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->forward(a));assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.TILE_ID_SUBTYPE2,e.boundary());assertEquals(32,e.wholeIndex());assertEquals(1,e.value());assertEquals(0,e.getSuppressed().length);
        assertArrayEquals(source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x21D652).moveByte(ANIMATION,0).copyBytes(),p.state(32).copyBytes());assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);assertTrue(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void reachedAnimationDirectoryFailureKeepsActualA1AndResetTimerPrefixWithoutReplay()throws Exception{
        var a=loaded();header(a,0,3);var p=pool(a);var before=a.host().placement().capture();var source=p.state(32);
        var e=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->forward(a));assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,e.boundary());assertEquals(6,e.value());assertEquals(0,e.getSuppressed().length);
        var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x21D652).moveByte(ANIMATION,3).moveWord(TILE,0).moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255);
        assertArrayEquals(expected.copyBytes(),p.state(32).copyBytes());assertEquals(List.of(new SonicCdGameModule.PastTunnelDoorSplashFm(CdTunnelDoorSplash.FmSound.FM_A1)),splashSounds(a));nonwrites(a,before,32);assertTrue(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void expiredDirtyScriptIndexKeepsOnlyTheActualTimerAndSpeedStoresAtItsFiniteReadStop()throws Exception{
        var a=loaded();header(a,2,0);var p=pool(a);p.write(p.state(32).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_FRAME,250).moveByte(ANIMATION_TIME,0));var before=a.host().placement().capture();var source=p.state(32);
        var e=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->forward(a));assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_FRAME_READ,e.boundary());assertEquals(271,e.value());assertEquals(0,e.getSuppressed().length);assertArrayEquals(source.moveByte(ANIMATION_TIME,3).copyBytes(),p.state(32).copyBytes());assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);assertTrue(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void invalidNativeRoutineFailsBeforeAnySplashOperationAndRetiresItsInvocation()throws Exception{
        var a=loaded();header(a,3,0);var p=pool(a);var before=a.host().placement().capture();var source=p.state(32);
        var e=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->forward(a));assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertEquals(3,e.value());assertEquals(0,e.getSuppressed().length);assertArrayEquals(source.copyBytes(),p.state(32).copyBytes());assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,-1);assertTrue(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void realDrawReceiptCompletesBeforeTheDeferredInvalidMappingFrameStopsCpuDrain()throws Exception{
        var a=loaded();header(a,2,0);var p=pool(a);p.write(p.state(32).moveByte(MAP_FRAME,255));var before=a.host().placement().capture();var source=p.state(32);
        var e=assertThrows(UnsupportedOperationException.class,()->forward(a));assertTrue(e.getMessage().contains("mapping")||e.getMessage().contains("frame"));
        assertArrayEquals(source.moveByte(ANIMATION_TIME,0x7E).moveByte(SPRITE_FLAGS,source.spriteFlags()&127).copyBytes(),p.state(32).copyBytes());assertTrue(splashSounds(a).isEmpty());nonwrites(a,before,32);assertTrue(a.frame().closed());assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void fullAndCourseReplayRepeatOriginalInitFmAndLaterMainUsingOnlyActualCaptures()throws Exception{
        var a=loaded();header(a,0,2);var registry=a.h().mode().getRewindRegistry();var before=captureNative(a.h());var full=registry.capture();var course=registry.captureCourse();
        forward(a);var after=captureNative(a.h());var afterFull=registry.capture();var afterCourse=registry.captureCourse();assertEquals(List.of(new SonicCdGameModule.PastTunnelDoorSplashFm(CdTunnelDoorSplash.FmSound.FM_A1)),splashSounds(a));
        registry.restore(full);assertNativeCapture(before,a.h());forward(a);assertNativeCapture(after,a.h());values(afterFull,registry.capture(),"Splash Init FULL");values(afterCourse,registry.captureCourse(),"Splash Init FULL->course");
        registry.restoreCourse(course);assertNativeCapture(before,a.h());forward(a);assertNativeCapture(after,a.h());values(afterFull,registry.capture(),"Splash Init course->FULL");values(afterCourse,registry.captureCourse(),"Splash Init course");
        var mainFull=registry.capture();var mainCourse=registry.captureCourse();forward(a);var mainAfter=captureNative(a.h());var mainExpected=registry.capture();assertTrue(splashSounds(a).isEmpty());
        registry.restore(mainFull);forward(a);assertNativeCapture(mainAfter,a.h());values(mainExpected,registry.capture(),"Splash Main FULL");registry.restoreCourse(mainCourse);forward(a);assertNativeCapture(mainAfter,a.h());values(mainExpected,registry.capture(),"Splash Main course");assertNull(actualField(pass(a),"splashInvocation"));
    }
    @Test void equalPublicClonesIdleCachedPortsAndDirectBodyCannotMintPrivateWorldAuthority()throws Exception{
        var a=loaded();header(a,0,0);var pass=pass(a);var body=(CdTunnelDoorSplash)actualField(pass,"doorSplash");var placement=a.host().placement();var source=pool(a).state(32);var cut=placement.capture();
        var publication=new CdTunnelDoorSplash.Publication(1,CdTunnelDoorSplash.StoreKind.INIT_HEADER,cut,source,source,source);var draw=new CdTunnelDoorSplash.DrawCall(cut,source);var fm=new CdTunnelDoorSplash.FmCall(cut,source,CdTunnelDoorSplash.FmSound.FM_A2);
        var observer=(CdTunnelDoorSplash.DispatchObserver)actualField(body,"observer");var receipt=(CdTunnelDoorSplash.ReceiptGuard)actualField(body,"receiptGuard");var drawPort=(CdTunnelDoorSplash.SourceDraw)actualField(body,"sourceDraw");var soundPort=(CdTunnelDoorSplash.SourceSound)actualField(body,"sourceSound");
        var nativeBefore=captureNative(a.h());var registry=a.h().mode().getRewindRegistry();var full=registry.capture();
        for(int repeat=0;repeat<2;repeat++){
            assertFalse(body.isCurrentCall(publication));assertFalse(body.isCurrentCallCompleted(publication));
            assertThrows(IllegalStateException.class,()->observer.beforePublication(publication));assertThrows(IllegalStateException.class,()->observer.afterPublication(publication));
            assertThrows(IllegalStateException.class,()->receipt.prepared(publication));assertThrows(IllegalStateException.class,()->receipt.completed(publication));
            assertThrows(IllegalStateException.class,()->observer.beforeCall(draw));assertThrows(IllegalStateException.class,()->observer.afterCall(draw));assertThrows(IllegalStateException.class,()->drawPort.draw(draw));assertThrows(IllegalStateException.class,()->soundPort.play(fm));
            assertThrows(IllegalStateException.class,()->body.run(32));assertThrows(IllegalStateException.class,()->pass.runWalk(a.frame().environment().globals().levelFrames()));
        }
        assertNativeCapture(nativeBefore,a.h());values(full,registry.capture(),"Splash idle/clone nonwrites");assertNull(actualField(pass,"splashInvocation"));assertFalse(a.frame().closed());
    }
    private static void assertInit(int subtype,int frame,CdTunnelDoorSplash.FmSound sound)throws Exception{
        var a=loaded();header(a,0,subtype);var p=pool(a);var before=a.host().placement().capture();var source=p.state(32);forward(a);
        var expected=source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x21D652).moveByte(ANIMATION,subtype).moveWord(TILE,0)
                .moveByte(PREVIOUS_ANIMATION,subtype).moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,3).moveByte(MAP_FRAME,frame);
        drawBitOnly(expected,p.state(32));assertEquals(List.of(new SonicCdGameModule.PastTunnelDoorSplashFm(sound)),splashSounds(a));nonwrites(a,before,32);assertFalse(a.frame().closed());
        assertTrue(a.host().video().completedCpu().orElseThrow().provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32));var body=(CdTunnelDoorSplash)actualField(pass(a),"doorSplash");assertEquals(72,body.assets().objectTileIds().length());assertEquals(100,body.assets().visual().mappings().length());assertEquals(24,body.assets().visual().animationTable().length());assertNull(actualField(pass(a),"splashInvocation"));
    }
    private static void header(A a,int routine,int subtype){
        seed(a,0x0B,routine);var p=a.host().placement().pool();p.write(p.state(32).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SPRITE_FLAGS,routine==0?0xFF:0x84).moveByte(PRIORITY,7)
                .moveByte(FLAGS,0xA0).moveByte(WIDTH,0x23).moveByte(Y_RADIUS,13).moveByte(X_RADIUS,7).moveLong(MAPPINGS,routine==0?0x12345678:0x21D652).moveWord(TILE,routine==0?0xBEEF:0)
                .moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveWord(X_VELOCITY,0x2345).moveWord(Y_VELOCITY,0x6789).moveWord(SWING_STEP,0xBEEF)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,routine==0?0xFE:0).moveByte(ANIMATION_FRAME,routine==0?0xA9:2).moveByte(ANIMATION_TIME,routine==0?0x38:0x7F).moveByte(MAP_FRAME,0));
        p.write(p.state(33).moveByte(PRIORITY,5).moveWord(X_FRACTION,0x9ABC));p.write(p.state(127).moveByte(FLAGS,0xA7).moveWord(Y_FRACTION,0xDEF0));
    }
    private static CdObjectPool pool(A a)throws Exception{return ((CdObjectPlacementRuntime)actualField(a.host(),"placement")).pool();}
    private static SonicCdAct2PastObjectPass pass(A a)throws Exception{return (SonicCdAct2PastObjectPass)actualField(actualField(a.core(),"ordinary"),"objects");}
    private static List<SonicCdGameModule.PastTunnelDoorSplashFm> splashSounds(A a)throws Exception{return sounds(a).stream().filter(SonicCdGameModule.PastTunnelDoorSplashFm.class::isInstance).map(SonicCdGameModule.PastTunnelDoorSplashFm.class::cast).toList();}
    private static void nonwrites(A a,CdObjectPlacementRuntime.Snapshot before,int changed)throws Exception{
        var placement=(CdObjectPlacementRuntime)actualField(a.host(),"placement");var actual=placement.capture();assertEquals(before.status(),actual.status());assertArrayEquals(before.savedFlags().copy(),actual.savedFlags().copy());
        for(var old:before.pool().slots())if(old.identity().wholeIndex()!=changed)assertArrayEquals(old.copyBytes(),actual.pool().slots().get(old.identity().dynamicIndex()).copyBytes());
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
