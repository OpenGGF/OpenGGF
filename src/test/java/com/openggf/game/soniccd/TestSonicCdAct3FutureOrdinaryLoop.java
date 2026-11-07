package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Genuine source-bound future Frame; Ports are explicit COMPONENT scheduling controls. Java UNRUN. */
class TestSonicCdAct3FutureOrdinaryLoop {
 private static final List<String> ORDER=List.of("BEFORE","PUBLISH","ART","ANIM","PLANES","SLOW","HUD","MUSIC","SETTLE","SPAWN","BODY","CAMERA","DRAW","TIME","PALETTE","SECTION","PREPARE","GLOBAL");
 @Test void cAndDUseOriginalFrameFinishIncrementAndBodyInitInNativeOrder()throws Exception{
  for(var a:TestCdR13PlaneSources.actual())try(var h=new Harness(a)){h.loop.run(8);assertEquals(ORDER,h.order);assertEquals(Arrays.asList(CdOrdinaryLoop.Cut.values()),h.cuts);assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(1,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(CdPlayerBody.Boundary.INIT_RETURN,h.body.result().boundary());assertEquals(2,h.frame.state().motion().routine());assertEquals(a.range(CdR13FutureAssets.Asset.MAP_SPR_SONIC).nativeAddress(),h.frame.state().header().mappingAddress());}
 }
 @Test void pauseHoldReleaseUnpauseUsesTheOriginalClockAndNeverExecutesAPausedBody()throws Exception{
  try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){for(int held:new int[]{128,128,0}){h.loop.run(held);assertTrue(h.frame.paused());assertNull(h.body);assertEquals(0,h.frame.environment().globals().levelFrames());}h.loop.run(128);assertFalse(h.frame.paused());assertNotNull(h.body);assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(4,h.frame.vIntClock().deltaSinceAdmittedCut());assertEquals(1,Collections.frequency(h.order,"BODY"));}
 }
 @Test void allOriginalPortFaultsRetainTheirFinitePrefixAndExpireTheLoop()throws Exception{
  for(String stop:ORDER)try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){h.stop=stop;assertSame(h.original,assertThrows(AssertionError.class,()->h.loop.run(8)));assertEquals(ORDER.subList(0,ORDER.indexOf(stop)+1),h.order);var before=List.copyOf(h.order);assertThrows(IllegalStateException.class,()->h.loop.run(0));assertEquals(before,h.order);if(ORDER.indexOf(stop)>ORDER.indexOf("BODY"))assertEquals(2,h.frame.state().motion().routine());}
 }
 @Test void caughtNestedEntryAtEveryCutCannotPoisonTheActualOuterCall()throws Exception{
  try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){h.nested=true;h.loop.run(8);assertEquals(ORDER,h.order);assertEquals(CdOrdinaryLoop.Cut.values().length,h.nestedRejected);}
 }
 @Test void nullForeignSourceAndClosedFrameRejectBeforeAnyCallerPort()throws Exception{
  try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){assertThrows(NullPointerException.class,()->new CdOrdinaryLoop(null,h.frame.stageSource(),h));assertThrows(NullPointerException.class,()->new CdOrdinaryLoop(h.frame,null,h));assertThrows(NullPointerException.class,()->new CdOrdinaryLoop(h.frame,h.frame.stageSource(),null));assertThrows(IllegalArgumentException.class,()->new CdOrdinaryLoop(h.frame,CdStagePlayerSource.fromAct3Future(TestCdR13PlaneSources.actual().get(1)),h));h.frame.close();assertThrows(IllegalStateException.class,()->h.loop.run(0));assertTrue(h.order.isEmpty());}
 }
 @Test void invalidIdleInputDoesNotBorrowCpuOrRetireEitherOriginalSource()throws Exception{
  for(var source:TestCdR13PlaneSources.actual())try(var h=new Harness(source)){
   var actor=h.frame.captureActor();var shared=h.frame.captureShared();
   for(int held:new int[]{-1,256,65535,Integer.MIN_VALUE})assertThrows(IllegalArgumentException.class,()->h.loop.run(held));
   assertTrue(h.order.isEmpty());assertTrue(h.cuts.isEmpty());assertEquals(actor,h.frame.captureActor());assertEquals(shared.environment(),h.frame.environment());assertEquals(shared.vIntClock(),h.frame.vIntClock());h.loop.run(8);assertEquals(ORDER,h.order);
  }
 }
 @Test void healthyTransferReturnsAfterTheActualCameraPrefixWithoutRetiringTheLoop()throws Exception{
  for(var source:TestCdR13PlaneSources.actual())try(var h=new Harness(source)){
   h.transfer=true;h.loop.run(8);assertEquals(ORDER.subList(0,ORDER.indexOf("CAMERA")+1),h.order);assertEquals(CdOrdinaryLoop.Cut.POST_OBJECT_CAMERA,h.cuts.getLast());assertFalse(h.frame.closed());assertEquals(1,h.frame.environment().globals().levelFrames());
   // Explicit component owner permits another run; an actual World instead follows its original transfer.
   h.transfer=false;h.order.clear();h.cuts.clear();h.loop.run(128);assertTrue(h.frame.paused());assertFalse(h.order.contains("BODY"));assertEquals(CdOrdinaryLoop.Cut.RETURN,h.cuts.getLast());assertEquals(1,h.frame.environment().globals().levelFrames());assertEquals(2,h.frame.vIntClock().deltaSinceAdmittedCut());
  }
 }
 @Test void nullContinuationFailsAtTheReachedCameraCutAndNeverRunsTheTail()throws Exception{
  try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){
   h.nullContinuation=true;assertThrows(NullPointerException.class,()->h.loop.run(8));assertEquals(ORDER.subList(0,ORDER.indexOf("CAMERA")+1),h.order);assertFalse(h.order.contains("DRAW"));assertThrows(IllegalStateException.class,()->h.loop.run(0));
  }
 }
 @Test void completeTimeStopWordSkipsOnlyPaletteAndIsReadOnceAtItsNativeCut()throws Exception{
  for(var source:TestCdR13PlaneSources.actual())for(int word:new int[]{0,1,0x100,0x8000,0xFFFF})try(var h=new Harness(source)){
   h.timeStop=word;h.loop.run(8);assertEquals(1,Collections.frequency(h.order,"TIME"));assertEquals(word==0,h.order.contains("PALETTE"));assertTrue(h.order.indexOf("TIME")>h.order.indexOf("DRAW"));assertTrue(h.order.indexOf("TIME")<h.order.indexOf("SECTION"));assertEquals("GLOBAL",h.order.getLast());
  }
 }
 @Test void malformedTimeStopWordRetainsDrawPrefixAndRetiresBeforePaletteOrSections()throws Exception{
  for(int word:new int[]{-1,65536})try(var h=new Harness(TestCdR13PlaneSources.actual().getFirst())){
   h.timeStop=word;assertThrows(IllegalArgumentException.class,()->h.loop.run(8));assertEquals(ORDER.subList(0,ORDER.indexOf("TIME")+1),h.order);assertThrows(IllegalStateException.class,()->h.loop.run(0));
  }
 }
 private static final class Harness implements CdOrdinaryLoop.Ports,AutoCloseable {
  final CdR13FutureAssets assets;final CdPlayerFrameRuntime frame;final CdOrdinaryLoop loop;final List<String> order=new ArrayList<>();final List<CdOrdinaryLoop.Cut> cuts=new ArrayList<>();final AssertionError original=new AssertionError("constructed original callback fault");String stop;boolean nested,transfer,nullContinuation;int nestedRejected,timeStop;CdPlayerFrameRuntime.BodyPublication body;
  Harness(CdR13FutureAssets a){assets=a;var actor=new CdPlayerBody.State(new CdPlayerMotionState(0x00400000,0x02A20000,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0,new CdPlayerBody.ObjectLinks(0,0),0);var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0,0,0),new CdPlayerMovement.Bounds(0,0xD97,0x310,0),new CdPlayerMovement.Gates(2,2,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0,0,0,0,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0,160,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);frame=CdPlayerFrameRuntime.fromAct3FutureEntry(a,actor,env,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0));frame.bindArtPublication((request,value)->record("ART"));loop=new CdOrdinaryLoop(frame,frame.stageSource(),this);}
  void record(String s){order.add(s);if(s.equals(stop))throw original;}
  public void requireCurrent(CdOrdinaryLoop.Cut c,CdPlayerFrameRuntime f,CdStagePlayerSource a){assertSame(frame,f);assertSame(frame.stageSource(),a);cuts.add(c);if(nested){assertThrows(IllegalStateException.class,()->loop.run(0));nestedRejected++;}}
  public Optional<CdObjectDrawQueue.CpuSat> beforeSelector8(){record("BEFORE");return Optional.empty();}public void publishLevel(Optional<CdObjectDrawQueue.CpuSat> c){record("PUBLISH");assertTrue(c.isEmpty());}public void updateAnimTiles(){record("ANIM");}public void copyCameraAndDrawLevel(){record("PLANES");}public void serviceSlowPlc(){record("SLOW");}public void updateHud(){record("HUD");}public void mainMusic(){record("MUSIC");}public void settleSelectedCpu(Optional<CdObjectDrawQueue.CpuSat> c){record("SETTLE");}public void spawnObjects(){record("SPAWN");}public void runObjects(int frameWord){record("BODY");body=frame.runBody(frameWord);}public CdOrdinaryLoop.MainContinuation postObjectCamera(){record("CAMERA");return nullContinuation?null:transfer?CdOrdinaryLoop.MainContinuation.TRANSFERRED:CdOrdinaryLoop.MainContinuation.ORDINARY_TAIL;}public void drawObjects(){record("DRAW");}public int timeStopWord(){record("TIME");return timeStop;}public void paletteCycle(){record("PALETTE");}public void updateSectionArt(){record("SECTION");}public void preparePlc(){record("PREPARE");}public void updateGlobalAnimations(){record("GLOBAL");}public void close(){frame.close();}
 }
}
