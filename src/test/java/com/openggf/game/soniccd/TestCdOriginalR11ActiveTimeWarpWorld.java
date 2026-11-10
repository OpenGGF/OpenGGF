package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Public source-written active-warp requirement, not a pose/clock/capture-hydrated fixture. */
@Isolated class TestCdOriginalR11ActiveTimeWarpWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineFourJump6331And6332SaveCurrentWarpAndReplayBothFullAndCourse()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   for(int n=0;n<6330;n++){int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;step(loop,held);assertFalse(m.originalR11EntryFrame().closed());assertTrue(m.originalR11EntryFrame().state().motion().routine()<6);}
   var frame=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();step(loop,8);assertFalse(frame.closed());assertEquals(6331,frame.environment().globals().levelFrames());assertEquals(0x80,frame.environment().movement().gates().timeZone());assertEquals(2,m.originalR11LoadingPrefix().main().level().spawnMode());assertEquals(1,m.originalR11LoadingImage().scroll().foreground().scrollLock()>>>8);
   var bank=m.originalR11TimeWarpSavedImage();assertEquals(1,bank.read(CdTimeWarpSaveData.SavedField.SPAWN_MODE));for(byte mask:bank.knownMask().copy())assertEquals(1,mask,"Every one of42 actual save bytes must be owned after completion");assertEquals(0,bank.read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS)&0x48);var after6331=image(m);var markedFull=registry.capture();var markedCourse=registry.captureCourse();step(loop,8);assertSame(bank,m.originalR11TimeWarpSavedImage(),"spawn2 skips repeat save");var after6332=image(m);for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(after6331,image(m));step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(after6332,image(m));}
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(markedCourse);else registry.restore(markedFull);assertSame(bank,m.originalR11TimeWarpSavedImage());CdCapturedValueAssertions.assertCapturedValueEquals(after6331,image(m));step(loop,8);assertSame(bank,m.originalR11TimeWarpSavedImage(),"restored spawn2 must not save again");CdCapturedValueAssertions.assertCapturedValueEquals(after6332,image(m));}
  }
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.pool().slots(),p.savedFlags(),p.status(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11WorldImage(),m.originalR11FirstLoad(),m.originalR11TimeWarpSavedImage(),m.programServicesState(),m.selectedProfileState());}

 /** Diagnostic only: the strict6331/6332 prefix below must finish before a later
  * unsupported/runtime-fault/death/closed/bound stop can be reported as evidence. */
 @Test void diagnosticAfterGenuine6332ObservesAtMost300PublicCallsUntilFirstBoundary()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   var frame=m.originalR11EntryFrame();
   for(int n=0;n<6330;n++){
    int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;
    step(loop,held);assertFalse(frame.closed(),"Required strict returned prefix before diagnostic admission");assertTrue(frame.state().motion().routine()<6,"A returned death is terminal, never replayed to reach the diagnostic");
   }
   // Positive requirements are deliberately outside every diagnostic catch.
   step(loop,8);assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6);
   assertEquals(6331,frame.environment().globals().levelFrames());assertEquals(0x80,frame.environment().movement().gates().timeZone());
   assertEquals(2,m.originalR11LoadingPrefix().main().level().spawnMode());assertEquals(1,m.originalR11LoadingImage().scroll().foreground().scrollLock()>>>8);
   var bank=m.originalR11TimeWarpSavedImage();assertEquals(1,bank.read(CdTimeWarpSaveData.SavedField.SPAWN_MODE));
   for(byte mask:bank.knownMask().copy())assertEquals(1,mask,"All42 real save bytes must be known before diagnostic admission");
   assertEquals(0,bank.read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS)&0x48);
   String at6331=activeWarpObserverHealthyFacts(m,frame,6331);
   step(loop,8);assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6);assertEquals(6332,frame.environment().globals().levelFrames());assertSame(bank,m.originalR11TimeWarpSavedImage(),"The genuine spawn2 path skips repeat save");
   String lastHealthy=activeWarpObserverHealthyFacts(m,frame,6332);int cachedHealthy=6332,completed=6332,attempted=6332;
   var checkpoints=new StringBuilder(at6331).append(lastHealthy);String outcome="BOUND_300_ADDITIONAL_RETURNED_CALLS";RuntimeException fault=null;int firstReturnedFrameTimer230=0;
   for(int additional=0;additional<300;additional++){
    // The previous completed row was proven living/open. No call follows a
    // returned death/closed Frame, and no Module getter follows a caught fault.
    attempted=completed+1;
    try{step(loop,8);}catch(RuntimeException stopped){fault=stopped;outcome=stopped instanceof UnsupportedOperationException?"FIRST_UNSUPPORTED_ATTEMPT":"FIRST_RUNTIME_FAULT_ATTEMPT";break;}
    completed=attempted;
    if(frame.environment().globals().timeWarpTimer()==230&&firstReturnedFrameTimer230==0)firstReturnedFrameTimer230=completed;
    if(frame.closed()){outcome="FIRST_RETURNED_CLOSED_FRAME";break;}
    if(frame.state().motion().routine()>=6){outcome="FIRST_RETURNED_DEATH";break;}
    lastHealthy=activeWarpObserverHealthyFacts(m,frame,completed);cachedHealthy=completed;
    int timer=frame.environment().globals().timeWarpTimer();
    if(additional==0||timer==229||timer==230||additional==299)checkpoints.append(lastHealthy);
   }
   String boundary="typedBoundary=not-reported; exceptionClass=none; literalMessage=none\n";
   if(fault!=null){
    String typed=fault instanceof com.openggf.game.soniccd.player.CdPlayerTimeWarp.UnsupportedTimeWarp warp?warp.boundary().name():"not-reported";
    boundary="typedBoundary="+typed+"; exceptionClass="+fault.getClass().getName()+"; literalMessage="+String.valueOf(fault.getMessage())+"\n";
    var stack=fault.getStackTrace();for(int n=0;n<Math.min(6,stack.length);n++)boundary+=" at "+stack[n]+"\n";
   }
   // These retained Frame accessors are plain read-only values even after close.
   // They are labelled terminal Frame observations, never a returned Module cut.
   String terminalFrame="retainedFrameAfterStop: closed="+frame.closed()+"; routine="+frame.state().motion().routine()+"; nativeTimeWarpTimer="+frame.environment().globals().timeWarpTimer()+"; rawPeriod="+frame.environment().movement().gates().timeZone()+"; VInt="+frame.vIntClock()+"\n";
   String report="DIAGNOSTIC ONLY: original USA R11 public NEW_GAME/Ready/fixed four jumps; strict6331/6332 save requirements completed before this bounded observer.\n"
    +"Right8 after6332; maximumAdditionalCalls=300; outcome="+outcome+"; lastCompletedCaller="+completed+"; lastAttemptedCaller="+attempted+"; cachedHealthyModuleCut="+cachedHealthy+"; firstReturnedFrameTimer230="+(firstReturnedFrameTimer230==0?"not-observed":firstReturnedFrameTimer230)+"\n"
    +boundary+"SELECTED_COMPLETE_HEALTHY_CUTS\n"+checkpoints+"LAST_COMPLETE_HEALTHY_MODULE_CUT (cached before terminal; restart/spawn/bank are not failed-row values)\n"+lastHealthy+terminalFrame
    +"No fresh Module getter follows unsupported/fault/death/closed. An unsupported or runtime-fault attempted row did not return. Timer230 alone does not prove its restart/music/fade/transfer completed. A bounded/terminal stop is not warp, destination, route, stage, pixel, native or regional completion.\n";
   System.out.println(report);
   assertTrue(completed>=6332&&completed<=6632);assertEquals(fault==null?completed:completed+1,attempted);
  }
 }
 /** Genuine idle owner values cached only after a complete living/open row. */
 private static String activeWarpObserverHealthyFacts(SonicCdGameModule m,com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,int ordinal){
  if(frame.closed()||frame.state().motion().routine()>=6)throw new IllegalStateException("No healthy active-Warp observation cut");
  var prefix=m.originalR11LoadingPrefix();var loading=m.originalR11LoadingImage();var bank=m.originalR11TimeWarpSavedImage();var level=prefix.main().level();
  int known=0;for(byte mask:bank.knownMask().copy())if(mask==1)known++;
  var motion=frame.state().motion();return "healthyCaller="+ordinal+"; loadingPhase="+loading.phase()+"; nativeGameMode="+prefix.gameMode()+"; levelStartedByte="+prefix.main().levelStartedByte()+"; routine="+motion.routine()+"; nativeTimeWarpTimer="+frame.environment().globals().timeWarpTimer()+"; rawPeriod="+frame.environment().movement().gates().timeZone()+"; restartWord="+level.levelRestart()+"; spawnMode="+level.spawnMode()+"; scrollLockWord="+loading.scroll().foreground().scrollLock()+"; bankKnownBytes="+known+"/42; bankKnownMaskHex="+java.util.HexFormat.of().formatHex(bank.knownMask().copy())+"; bankSavedSpawn="+bank.read(CdTimeWarpSaveData.SavedField.SPAWN_MODE)+"; bankSavedPrimaryFlags="+bank.read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS)+"; nativeX="+(motion.centreX16_16()>>>16)+"; nativeY="+(motion.centreY16_16()>>>16)+"; VInt="+frame.vIntClock()+"\n";
 }
}
