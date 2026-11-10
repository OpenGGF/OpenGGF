package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Requirements FIRST. Actual public selected FULL pipeline; every Java check UNRUN. */
@Isolated class TestCdOriginalTtOrdinaryWorld extends TestCdOriginalR11LoadingWorld {
 private SonicCdGameModule.SelectedProfileAttempt selected(CdGameDataSource source)throws Exception {
  var saves=new SaveManager(root);
  var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(
   OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  saves.writeSlot("scd",1,profile.encode());
  return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
 }
 @Test void publicFullReadyReturnsFirstOrdinaryAndReplaysTheCompleteCurrentWorld()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=selected(source);var m=attempt.module().orElseThrow();
  try(var boot=boot()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();
   var level=assertInstanceOf(SonicCdLevel.class,mode.getLevelManager().getCurrentLevel());
   var loading=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
   for(int row=0;row<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());
   var frame=m.originalR11EntryFrame();var registry=mode.getRewindRegistry();
   // N1ecc5f3a InitStageResetInputTimers (r4/main.asm:248-254) clears both before READY.
   assertEquals(0,frame.state().motion().routine(),"loading returns before PlayerInit");
   assertEquals(0,frame.environment().movement().globals().boredTimer());
   assertEquals(0,m.originalR11LoadingImage().globals().tail().orElseThrow().player2BoredWord());
   var full=registry.capture();var course=registry.captureCourse();var before=frame.vIntClock();
   var loads=m.programServicesState().loads();var generation=mode.getLevelManager().getCompletedProductionLoadGeneration();
   step(loop,0); // Must RETURN: no assertThrows, skipped requirement or renamed ordinary fence.
   assertEquals(1,frame.environment().globals().levelFrames());
   assertEquals(new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.VIntClock(before.absoluteSeed(),(before.deltaSinceAdmittedCut()+1)&0xFFFF_FFFFL),frame.vIntClock());
   assertSame(loading.retainedPlayerSource(),frame.stageSource());assertSame(loading,level.loadingSource());
   assertSame(m.originalR11FirstLoad().foreground(),m.originalR11FirstLoad().selection().inputs().foreground());
   assertSame(m.originalR11FirstLoad().background(),m.originalR11FirstLoad().selection().inputs().background());
   assertEquals(96,m.originalR11PlacementImage().pool().slots().size());
   assertEquals(1984,m.originalR11LoadingImage().reserved().length());
   assertEquals(640,m.originalR11LoadingImage().cpuSat().length());
   assertEquals(128,m.originalLoadingWaterPalette().length());
   // N1ecc5f3a UpdateBoredTimer (r4/main.asm:1390-1401) preserves zero before PlayerInit (objects/player.asm:139-157).
   assertEquals(2,frame.state().motion().routine(),"first ordinary object pass runs PlayerInit after VBlank");
   assertEquals(0,frame.environment().movement().globals().boredTimer());
   assertEquals(0,m.originalR11LoadingImage().globals().tail().orElseThrow().player2BoredWord());
   assertArrayEquals(m.originalR11WorldImage().orElseThrow().cpu().orElseThrow().bytes(),m.originalR11LoadingImage().cpuSat().copy());
   assertTrue(m.originalR11FirstLoad().selection().inputs().selectedCpu().isEmpty(),"first selector borrows OLD loading SAT; fresh queue is for next service");
   var expected=image(m);registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(loads,m.programServicesState().loads());assertEquals(generation,mode.getLevelManager().getCompletedProductionLoadGeneration());
   var current=image(m);assertThrows(IllegalArgumentException.class,()->m.serviceOriginalR11Loading(256));
   CdCapturedValueAssertions.assertCapturedValueEquals(current,image(m));
   var foreign=new java.util.concurrent.atomic.AtomicReference<Throwable>();
   var t=new Thread(()->{try{m.serviceOriginalR11Loading(0);}catch(Throwable e){foreign.set(e);}});t.start();t.join();
   assertInstanceOf(IllegalStateException.class,foreign.get());CdCapturedValueAssertions.assertCapturedValueEquals(current,image(m));
   SessionManager.clear();assertTrue(frame.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));
   assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));
  }
 }
 @Test void publicOriginalR11StillReturnsItsFirstOrdinary()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
  var m=attempt.module().orElseThrow();try(var boot=boot()) {var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   step(loop,0);assertEquals(1,m.originalR11EntryFrame().environment().globals().levelFrames());
   assertTrue(m.originalR11EntryFrame().stageSource().ownsPresent(source.assets()));
  }
 }
 private static Object image(SonicCdGameModule m) {
  var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();
  return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),
   m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.programServicesState());
 }

 @Test void actualPresentFirstPassPublishesSpikesTonboAndSuppressedAnimalsAndReplaysAllOwners()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=selected(source);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(1,m.originalR11EntryFrame().environment().movement().gates().timeZone());
   var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();step(loop,0);
   assertEquals(1,m.originalR11EntryFrame().environment().globals().levelFrames());
   var pool=m.originalR11PlacementImage().pool().slots();
   for(int saved:new int[]{3,8}){
    var actor=pool.stream().filter(v->v.id()==0x2B&&v.savedFlagsId()==saved).findFirst().orElseThrow();assertEquals(2,actor.routine());assertEquals(saved==3?0x1CF:0x22F,actor.centreX());assertEquals(saved==3?0xF0:0x150,actor.centreY());
   }
   for(int saved:new int[]{5,7}){
    var actor=pool.stream().filter(v->v.id()==0x2F&&v.savedFlagsId()==saved).findFirst().orElseThrow();assertEquals(0,actor.routine(),"Actual Present/projector-zero helper suppresses normal Animal body");
   }
   assertEquals(0,m.originalR11LoadingImage().globals().projectorByte().orElseThrow());assertEquals(2,pool.stream().filter(v->v.id()==0x22&&v.routine()==2&&v.subtype()==1).count());
   var expected=List.of(image(m),m.originalR11WorldImage());registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,List.of(image(m),m.originalR11WorldImage()));
   registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,List.of(image(m),m.originalR11WorldImage()));
  }
 }

 /** Diagnostic only: real selected TT LOAD_GAME/Ready, then fixed public Right8 input.
  * The qualified240-row healthy prefix is compulsory. A later first unsupported/death/
  * closed stop or1000-call observation bound does not certify a route, warp or parity. */
 @Test void diagnosticOriginalTtAfterHealthyMonitor240ToFirstStopOr1000()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=selected(source);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();
   var level=assertInstanceOf(SonicCdLevel.class,mode.getLevelManager().getCurrentLevel());
   var programme=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow();var loadingSource=programme.levelLoadingSource();
   int loadingCalls=0;for(;loadingCalls<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;loadingCalls++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());
   var frame=m.originalR11EntryFrame();assertFalse(frame.closed());assertSame(loadingSource,level.loadingSource());assertSame(loadingSource.retainedPlayerSource(),frame.stageSource());
   String sourceIdentity="programme="+programme.programme()+"; stage="+loadingSource.stage()+"; file="+loadingSource.file()+"; MMD_SHA256="+programme.mmdSha256()+"; observedWholeDiscSHA256="+programme.wholeDiscSha256();
   String readyClock=frame.vIntClock().toString();
   // Direct Frame readers remain legal after a failed/closed/death row. This reader never
   // asks a Module getter or requests a capture/restore capability.
   java.util.function.Supplier<String> frameFacts=()->{
    var actor=frame.state();var environment=frame.environment();
    return "actualReadOnlyFrame: closed="+frame.closed()+"; VInt="+frame.vIntClock()+"; motion="+actor.motion()+"; header="+actor.header()+"; animation="+actor.animation()+"; timers="+actor.timers()+"; objectLinks="+actor.objectLinks()+"; rotationDistance="+actor.rotationDistance()+"; rotationAngle="+actor.rotationAngle()+"\n"
     +"actualReadOnlyEnvironment: controls="+environment.controls()+"; globals="+environment.globals()+"; movementGates="+environment.movement().gates()+"; movementGlobals="+environment.movement().globals()+"; physics="+environment.movement().physics()+"\n";
   };
   // Invoke ONLY at Ready or after a returned healthy row. The immutable text is retained
   // before the next fallible step; no cached value supplies an input, owner or missing ID.
   java.util.function.IntFunction<String> healthyFacts=ordinal->{
    var prefix=m.originalR11LoadingPrefix();var loading=m.originalR11LoadingImage();var placed=m.originalR11PlacementImage();var selected=m.originalR11FirstLoad().selection().inputs();var world=m.originalR11WorldImage();
    return "cachedHealthyModuleCutOrdinal="+ordinal+"; phase="+loading.phase()+"; fadeSteps="+loading.fadeSteps()+"; loadingDisplay="+loading.displayEnabled()+"; ring="+prefix.ring()+"; score="+prefix.score()+"; level="+prefix.main().level()+"; contact="+loading.globals().contact()+"; placementStatus="+placed.status()+"\n"
     +"cachedSelected: display="+selected.displayEnabled()+"; selectedCpuSprites="+selected.selectedCpu().map(cpu->Integer.toString(cpu.spriteCount())).orElse("absent")+"; selectedSatBytes="+selected.selectedSat().length()+"; hscrollBytes="+selected.hscroll().length()+"; vscrollBits="+Integer.toUnsignedString(selected.vscrollLongBits(),16)+"; paletteHex="+java.util.HexFormat.of().formatHex(selected.palette().copy())+"\n"
     +"cachedModule: currentWorldCpuSprites="+world.flatMap(image->image.cpu()).map(cpu->Integer.toString(cpu.spriteCount())).orElse("absent")+"; reservedBytes="+loading.reserved().length()+"; cpuSatBytes="+loading.cpuSat().length()+"; water="+m.originalLoadingWaterImage()+"; waterPaletteHex="+java.util.HexFormat.of().formatHex(m.originalLoadingWaterPalette().copy())+"; programServices="+m.programServicesState()+"; selectedProfile="+m.selectedProfileState()+"\n"
     +frameFacts.get();
   };
   int completed=0,attempted=0,cachedHealthyCut=0;boolean healthy240=false;String outcome="BOUND_REACHED";
   UnsupportedOperationException unsupported=null;String lastHealthy=healthyFacts.apply(0),cut240="NOT_REACHED_HEALTHY";var checkpoints=new StringBuilder();
   while(completed<1000){
    attempted=completed+1;
    try{step(loop,8);}catch(UnsupportedOperationException first){unsupported=first;outcome="FIRST_UNSUPPORTED";break;}
    completed=attempted;var motion=frame.state().motion();
    if(motion.routine()>=6){outcome="FIRST_RETURNED_DEATH";break;}
    if(frame.closed()){outcome="FIRST_RETURNED_CLOSED_FRAME";break;}
    // Both terminal guards precede every fresh Module read. Do not move these reads into cleanup.
    lastHealthy=healthyFacts.apply(completed);cachedHealthyCut=completed;
    if(completed==240){
     assertEquals(240,frame.environment().globals().levelFrames());var monitor=m.originalR11PlacementImage().pool().slots().get(49-32);
     assertEquals(0x19,monitor.id());assertEquals(2,monitor.subtype());assertTrue(monitor.routine()!=0,"Previously qualified actual Monitor49 must have returned");
     healthy240=true;cut240=lastHealthy;
    }
    if(completed==1||completed==32||completed==64||completed==128||completed==192||completed==239||completed==240||completed==241||completed==256||completed==512||completed==768||completed==1000){
     var prefix=m.originalR11LoadingPrefix();checkpoints.append("ACTUAL_HEALTHY_CALLER=").append(completed).append("; nativeX=").append(motion.centreX16_16()>>>16).append("; nativeY=").append(motion.centreY16_16()>>>16).append("; routine=").append(motion.routine()).append("; flags=").append(motion.flags()).append("; xVelocity=").append(motion.xVelocity()).append("; yVelocity=").append(motion.yVelocity()).append("; groundVelocity=").append(motion.groundVelocity()).append("; ring=").append(prefix.ring()).append("; score=").append(prefix.score()).append("; level=").append(prefix.main().level()).append("; VInt=").append(frame.vIntClock()).append('\n');
    }
   }
   var boundary=new StringBuilder();String message=unsupported==null?"":String.valueOf(unsupported.getMessage());
   if(unsupported!=null){
    java.util.function.Function<String,String> reportedOperand=key->{var matcher=java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(key)+"=([^\\s;,]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);return matcher.find()?matcher.group(1):"not-reported";};
    boundary.append("firstBoundarySlot=").append(reportedOperand.apply("slot")).append("; firstBoundaryWhole=").append(reportedOperand.apply("whole")).append("; firstBoundaryId=").append(reportedOperand.apply("id")).append(" (literal message tokens only; cached slots are not boundary evidence)\n")
     .append("errorClass=").append(unsupported.getClass().getName()).append("; errorMessage=").append(message,0,Math.min(2048,message.length())).append(message.length()>2048?"[message truncated]":"").append('\n');
    var stack=unsupported.getStackTrace();for(int n=0;n<Math.min(12,stack.length);n++)boundary.append(" at ").append(stack[n]).append('\n');
   }else boundary.append("firstBoundarySlot=not-reported; firstBoundaryWhole=not-reported; firstBoundaryId=not-reported; errorClass=none; errorMessage=none\n");
   String report="DIAGNOSTIC OBSERVER ONLY: later unsupported/death/closed or bound does not certify completed TT route, stage closure, reload, warp, native, pixel or regional parity.\n"
    +"ENGINE OpenGGF original USA TT R41A public selected LOAD_GAME and genuine loading/Ready. INPUT_POLICY=Right8 for every postReady caller; maximumCompletedPostReadyCallers=1000. No Frame, pose, clock, pool, input tap, capture or receipt hydration.\n"
    +"source="+sourceIdentity+"; loadingCalls="+loadingCalls+"; ReadyVInt="+readyClock+"\n"
    +"outcome="+outcome+"; lastCompletedCaller="+completed+"; lastAttemptedCaller="+attempted+"; cachedHealthyModuleCut="+cachedHealthyCut+"; qualifiedHealthy240Returned="+healthy240+"\n"
    +boundary+"QUALIFIED_FIRST240_HEALTHY_MODULE_PREFIX\n"+cut240+"\nSELECTED_HEALTHY_CHECKPOINTS\n"+checkpoints
    +"LAST_COMPLETE_HEALTHY_MODULE_PREFIX\n"+lastHealthy+"ACTUAL_TERMINAL_READONLY_FRAME (cached Module facts above are not a failed/death/closed-row Module snapshot)\n"+frameFacts.get()
    +"Only literal reported slot/whole/id operands are boundary evidence; absence remains UNKNOWN. Failed attempted rows did not return. No fresh Module getter follows a terminal stop. All existing strict FULL/course/loading/mechanism/Monitor requirements remain unchanged. No optional PNG is produced by this observer.\n";
   System.out.println(report);
   if(!healthy240)fail("The previously qualified first240 healthy public Right8 rows and actual Monitor49 must return before accepting this diagnostic stop.\n"+report,unsupported);
   if(unsupported!=null)assertEquals(completed+1,attempted,"Only the first unsupported attempted row is unreturned");
   else{assertEquals(completed,attempted);if(outcome.equals("FIRST_RETURNED_DEATH"))assertTrue(frame.state().motion().routine()>=6);else if(outcome.equals("FIRST_RETURNED_CLOSED_FRAME"))assertTrue(frame.closed());else assertEquals(1000,completed);}
  }
 }
}
