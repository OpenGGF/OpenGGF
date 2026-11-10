package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Constructed component owners over original media, NOT Module/World/deferred or route evidence. */
class TestCdOriginalProgrammeFrameSemanticCharge {
 private CdStagePlayerSource source()throws Exception {
  String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank());Path cue;
  try(var p=Files.list(Path.of(root).resolve("USA"))){var cues=p.filter(v->v.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
  try(var a=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){return CdStagePlayerSource.fromProgramme(a,CdR4DataLayout.Programme.R41A.payload().stage());}
 }
 private H ready(boolean controlled)throws Exception{return new H(source(),controlled);}
 @Test void loadingFactoryStillCannotServiceOrdinaryOrRunBodyWithoutGenuineInputOccurrence()throws Exception {
  var h=ready(true);var a=h.frame.captureActor();var s=h.frame.captureShared();
  assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));
  h.frame.prepareInput(8);assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,()->h.frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
  h.frame.clearPendingInput();assertEquals(a,h.frame.captureActor());assertEquals(s,h.frame.captureShared());assertFalse(h.frame.closed());
 }
 @Test void servicedRawInputIsConsumedOnceAndActualLivePrefixSurvives()throws Exception {
  var h=ready(true);h.input(8);assertEquals(8,h.frame.environment().controls().rawHeld());
  assertThrows(IllegalStateException.class,()->h.frame.captureShared());
  h.frame.runOriginalProgrammeBody(h);assertEquals(8,h.frame.environment().controls().logicalHeld());
  assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));
  assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h.frame));h.frame.captureShared();
 }
 @Test void actualFinishOwnerAndAfterFinishPrecedeMainAndNoShadowSecondaryExists()throws Exception {
  var h=ready(true);h.input(0);assertTrue(h.events.indexOf("secondary-write")<h.events.indexOf("after-finish"));
  assertTrue(h.events.indexOf("after-finish")<h.events.indexOf("main-music"));assertEquals(8,h.secondary);h.frame.clearPendingInput();
 }
 @Test void foreignSourceOwnerRefusalIsBeforeInputAndPreservesOriginalThrowable()throws Exception {
  var h=ready(true);var before=h.frame.environment();var fault=new IllegalArgumentException("foreign source cut");h.guard=fault;
  h.frame.prepareInput(8);assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,h)));
  assertSame(before,h.frame.environment());assertFalse(h.frame.closed());h.frame.clearPendingInput();
 }
 @Test void paletteFaultRetainsReadControllerAndStopsBeforeArtFinishAndBody()throws Exception {
  var h=ready(true);var clock=h.frame.vIntClock();var fault=new IllegalArgumentException("actual palette fault");h.paletteFault=fault;
  h.frame.prepareInput(8);assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,h)));
  assertEquals(8,h.frame.environment().controls().rawHeld());assertEquals(clock,h.frame.vIntClock());assertTrue(h.frame.closed());assertFalse(h.events.contains("after-finish"));
 }
 @Test void caughtReentryAndCaptureRejectionDoNotRetireHealthyOuterScope()throws Exception {
  var h=ready(true);h.onPalette=()->{assertThrows(IllegalStateException.class,()->h.frame.captureActor());assertThrows(IllegalStateException.class,()->h.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,h));};
  h.input(0);h.frame.runOriginalProgrammeBody(h);assertFalse(h.frame.closed());
 }
 @Test void changedOwnerGenerationAndOffThreadOccurrenceCannotPublish()throws Exception {
  var h=ready(true);h.input(0);var saved=h.saved;var result=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  var t=new Thread(()->{try{saved.requireCurrent(h.frame);}catch(Throwable e){result.set(e);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,result.get());
  h.generation++;assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));assertTrue(h.frame.closed());
 }
 @Test void fullAndCourseValueRestoreDoNotReopenOldOccurrences()throws Exception {
  var h=ready(true);var a=h.frame.captureActor();var s=h.frame.captureShared();h.input(0);h.frame.runOriginalProgrammeBody(h);var old=h.saved;
  h.frame.restoreActor(a);h.frame.restoreShared(s);assertThrows(IllegalStateException.class,()->old.requireCurrent(h.frame));h.input(0);h.frame.runOriginalProgrammeBody(h);assertFalse(h.frame.closed());
 }
 @Test void sourceOrderedChargeSoundFaultPreservesPreSoundStoresAndIdentity()throws Exception {
  var h=ready(false);var fault=new IllegalArgumentException("actual ChargeStop fault");h.chargeFault=fault;h.input(0);
  assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.runOriginalProgrammeBody(h)));
  assertTrue(h.chargeBefore>0);assertTrue(h.chargeAfter>0);assertTrue(h.events.contains("charge:FM_CHARGESTOP"));assertTrue(h.frame.closed());
 }
 @Test void refusedChargeBeforeCannotReachSoundButAfterStillRuns()throws Exception {
  var h=ready(false);h.refuseChargeBefore=true;h.input(0);assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));
  assertTrue(h.chargeBefore>0);assertTrue(h.chargeAfter>0);assertFalse(h.events.stream().anyMatch(v->v.startsWith("charge:")));
 }
 @Test void newOwnerSurfaceKeepsR4PureFactoryFencesAndLoadingSeparate()throws Exception {
  var s=source();assertThrows(CdPlayerBody.UnsupportedBody.class,()->CdPlayerBody.fromStageSource(s));assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->CdPlayerMovement.fromStageSource(s));
  var h=ready(true);assertThrows(CdPlayerBody.UnsupportedBody.class,()->CdPlayerBody.forOriginalLoading(s).step(h.frame.state(),h.frame.environment()));
  assertEquals(CdR11aPlayerAssets.SourceGraph.R4,s.player().sourceGraph());
 }
 @Test void sameValueChargeStopIsCalledOnceAndNeverReplayedFromResult()throws Exception {
  var h=ready(false);h.input(0);assertThrows(CdPlayerFrameRuntime.OriginalProgrammeUnavailable.class,()->h.frame.runOriginalProgrammeBody(h));
  assertEquals(1,h.events.stream().filter(v->v.equals("charge:FM_CHARGESTOP")).count());assertEquals(0,h.frame.state().motion().charge());assertTrue(h.chargeBefore>0);assertEquals(h.chargeBefore,h.chargeAfter);
 }
 @Test void exactBodyDisplayObservationDoesNotInvokeDrawTwice()throws Exception {
  var h=ready(true);h.input(0);h.frame.runOriginalProgrammeBody(h);var value=h.frame.display().orElseThrow();
  assertEquals(h.frame.state().header().mappingAddress(),value.mappingAddress());assertEquals(h.frame.state().motion().centreY16_16(),value.centreY16_16());
  assertEquals(1,h.events.stream().filter(v->v.equals("draw")).count());
 }
 @Test void waterFaultKeepsActualFlagsAndHistoryBeforeUnownedExtraRead()throws Exception {
  var h=ready(true);h.values.water.put(CdR4WaterAirPorts.Value.WATER_Y,0x80);var fault=new IllegalArgumentException("actual drown timer source fault");h.values.failField=CdR4WaterAirPorts.Value.DROWN_TIMER;h.values.failure=fault;
  var history=h.frame.state().history();h.input(0);assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.runOriginalProgrammeBody(h)));
  assertEquals(0x40,h.frame.state().motion().flags()&0x40);assertNotEquals(history,h.frame.state().history());assertEquals(0x600,h.frame.environment().movement().physics().topSpeed());assertTrue(h.frame.closed());
 }
 @Test void equalRetainedSourceValuesFromAnotherFrameDoNotAuthorizeFirstInput()throws Exception {
  var a=ready(true);var b=ready(true);var env=a.frame.environment();var clock=a.frame.vIntClock();
  a.frame.prepareInput(8);assertThrows(IllegalStateException.class,()->a.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,b));
  assertSame(env,a.frame.environment());assertEquals(clock,a.frame.vIntClock());assertFalse(a.frame.closed());a.frame.clearPendingInput();
 }
 @Test void completionRefusalKeepsDerivedValueButCannotReturnHealthyBodyOrReopen()throws Exception {
  var h=ready(true);var fault=new IllegalArgumentException("actual completion refusal");h.completionFault=fault;h.input(0);
  assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.runOriginalProgrammeBody(h)));assertTrue(h.frame.closed());
  assertTrue(h.frame.display().isPresent());assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h.frame));
  assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));
 }
 static class H implements CdPlayerFrameRuntime.OriginalProgrammeOwner {
  final CdStagePlayerSource source;final CdPlayerFrameRuntime frame;final TestCdOriginalPlayerSemanticRuntime.Harness values;
  final List<String> events=new ArrayList<>();long generation=7;int secondary=7,chargeBefore,chargeAfter;boolean refuseChargeBefore;
  CdPlayerFrameRuntime.OriginalProgrammeCall saved;RuntimeException guard,paletteFault,chargeFault,completionFault;Runnable onPalette=()->{};
  H(CdStagePlayerSource source,boolean controlled){this.source=source;values=new TestCdOriginalPlayerSemanticRuntime.Harness(source);
   var init=CdPlayerBody.forOriginalLoading(source).step(values.state,values.environment);values.state=init.state();values.environment=init.environment();values.control(controlled?0x81:0);values.flags(0);values.water.put(CdR4WaterAirPorts.Value.WATER_Y,0x7FFF);
   frame=CdPlayerFrameRuntime.fromProgrammeEntry(source,values.state,values.environment,new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),new CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.of(40),0));
   frame.bindArtPublication((request,state)->events.add("art"));frame.bindFinishTimersOwner(new CdPlayerFrameRuntime.FinishTimersOwner(){public int readSecondary(CdPlayerFrameRuntime.FinishTimerCall c){c.requireCurrent(frame);return secondary;}public void writeSecondary(CdPlayerFrameRuntime.FinishTimerCall c,int word){c.requireCurrent(frame);secondary=word;events.add("secondary-write");}});
  }
  void input(int raw){frame.prepareInput(raw);frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,this);}
  public long generation(){return generation;}
  public void requireCurrent(CdPlayerFrameRuntime.OriginalProgrammeCall c){c.requireCurrent(frame);assertSame(source,c.source());saved=c;if(guard!=null)throw guard;}
  public void palette(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("palette");onPalette.run();if(paletteFault!=null)throw paletteFault;}
  public void levelArt(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("level-art");}
  public void afterFinish(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("after-finish");}
  public void mainMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("main-music");}
  public int levelFrames(CdPlayerFrameRuntime.OriginalProgrammeCall c){return 23;}
  public Optional<CdPlayerMovement.ObjectContext> objects(CdPlayerFrameRuntime.OriginalProgrammeCall c){return Optional.empty();} // Explicit constructed no-support cut, never a production default.
  public void flipperAnimation(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerMovement.SlotPatch p){throw unavailable("constructed flipper");}
  public CdR4WaterAirPorts.Calls water(CdPlayerFrameRuntime.OriginalProgrammeCall c){return values;}
  public void draw(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("draw");}
  public void sound(CdPlayerFrameRuntime.OriginalProgrammeCall c,int id){events.add("sound:"+id);}
  public void chargeSound(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Sound sound){events.add("charge:"+sound);if(chargeFault!=null)throw chargeFault;}
  public void beforeCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){chargeBefore++;if(refuseChargeBefore)throw new IllegalStateException("refused actual occurrence");}
  public void afterCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){chargeAfter++;}
  public void restoreStageMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){events.add("stage-music");}
  public void collideObjects(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw unavailable("actual touch owner");}
  public void timeWarp(CdPlayerFrameRuntime.OriginalProgrammeCall c){assertEquals(0,c.environment().movement().gates().timeWarpDirection());events.add("constructed no-warp branch");}
  public CdPlayerBody.LevelContext level(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw unavailable("actual level cut");}
  public void storeLevel(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.LevelField f,int v){throw unavailable("actual level store");}
  public void spawnGameOver(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw unavailable("actual full96 Game Over");}
  public void restartSoundFlag(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw unavailable("actual driver flag");}
  public void resetSavedObjectFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw unavailable("actual saved bank");}
  public void completed(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.Boundary b){events.add("completed:"+b);if(completionFault!=null)throw completionFault;}
  private CdPlayerFrameRuntime.OriginalProgrammeUnavailable unavailable(String name){return new CdPlayerFrameRuntime.OriginalProgrammeUnavailable(name);}
 }

 @Test void actualFrameLowSplitCopiesBeforeFinishThenSelectsCurrentLongsBeforeDraw()throws Exception {
  var h=new W(source(),0x20);h.input(0);
  assertEquals(0,h.pending);assertEquals(0,h.arm);assertEquals(32,h.paletteReads);
  assertTrue(h.events.indexOf("copies")<h.events.indexOf("secondary-write"));
  assertTrue(h.events.indexOf("secondary-write")<h.events.indexOf("water-long:0"));
  assertTrue(h.events.indexOf("select-long:124")<h.events.indexOf("stage-draw"));
  assertTrue(h.events.indexOf("hud")<h.events.indexOf("main-music"));
  assertEquals(1,h.events.stream().filter("stage-draw"::equals).count());
  assertEquals(41,h.frame.vIntClock().absoluteValue().orElseThrow());
  h.frame.runOriginalProgrammeBody(h);assertFalse(h.frame.closed());
 }
 @Test void actualFrameHighSplitDrawsBeforeFinishAndDfNeverReadsPendingOrWaterLongs()throws Exception {
  var high=new W(source(),0x80);high.pending=0;high.input(0);
  assertTrue(high.events.indexOf("hud")<high.events.indexOf("secondary-write"));assertEquals(32,high.paletteReads);
  high.frame.runOriginalProgrammeBody(high);
  var df=new W(source(),0xDF);df.pending=0xA5;df.input(0);
  assertEquals(0,df.paletteReads);assertEquals(0xA5,df.pending);assertEquals(0,df.pendingReads);assertEquals(0,df.arm);
  df.frame.runOriginalProgrammeBody(df);
 }
 @Test void actualFrameWaterReadFaultRetainsCompletedFinishAndLongPrefixAndCannotResume()throws Exception {
  var h=new W(source(),0x20);var fault=new IllegalArgumentException("current water LONG unavailable");h.waterFault=fault;
  h.frame.prepareInput(8);assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.ORDINARY_LEVEL,h)));
  assertEquals(3,h.selectedLongs);assertEquals(4,h.paletteReads);assertEquals(1,h.pending);
  assertEquals(8,h.secondary);assertEquals(41,h.frame.vIntClock().absoluteValue().orElseThrow());
  assertEquals(8,h.frame.environment().controls().rawHeld());assertFalse(h.events.contains("stage-draw"));
  assertFalse(h.events.contains("main-music"));assertTrue(h.frame.closed());
  assertThrows(IllegalStateException.class,h.frame::captureShared);
  assertThrows(IllegalStateException.class,()->h.frame.runOriginalProgrammeBody(h));
 }
 @Test void actualOriginalMainClockAdvanceKeepsTheSameInputEntitlement()throws Exception {
  var h=new W(source(),0xDF);h.input(0);int next=h.frame.advanceLevelMainFrame();
  h.actualFrames=next;assertEquals(next,h.frame.environment().globals().levelFrames());
  h.frame.runOriginalProgrammeBody(h);assertFalse(h.frame.closed());
 }
 @Test void lagHasNoStageWaterReadAndOldPauseGatesBothBoredWords()throws Exception {
  var lag=new W(source(),0x20);lag.frame.prepareInput(8);
  lag.frame.serviceOriginalProgrammeVBlank(PlcLifecyclePhase.LAG,lag);
  assertEquals(0,lag.paletteReads);assertFalse(lag.events.contains("copies"));
  assertFalse(lag.events.contains("stage-draw"));assertEquals(8,lag.secondary);
  assertThrows(IllegalStateException.class,()->lag.frame.runOriginalProgrammeBody(lag));
  var paused=new W(source(),0xDF);var selection=paused.frame.prepareInput(0x80);
  paused.frame.serviceOriginalProgrammeVBlank(selection.phase(),paused);assertTrue(paused.frame.paused());
  int secondary=paused.secondary,primary=paused.frame.environment().movement().globals().boredTimer();
  selection=paused.frame.prepareInput(0);paused.frame.serviceOriginalProgrammeVBlank(selection.phase(),paused);
  assertEquals(secondary,paused.secondary);assertEquals(primary,paused.frame.environment().movement().globals().boredTimer());
  assertEquals(42,paused.frame.vIntClock().absoluteValue().orElseThrow());
 }
 static final class W extends H implements CdPlayerFrameRuntime.OriginalWaterStageOwner {
  int split,arm,pending,pendingReads,paletteReads,selectedLongs,actualFrames=23;RuntimeException waterFault;
  W(CdStagePlayerSource s,int split){super(s,true);this.split=split;}
  @Override public int levelFrames(CdPlayerFrameRuntime.OriginalProgrammeCall c){return actualFrames;}
  @Override public void palette(CdPlayerFrameRuntime.OriginalProgrammeCall c){super.palette(c);arm=1;}
  public void copyCamerasAndFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);events.add("copies");assertThrows(IllegalStateException.class,frame::captureShared);}
  public int splitByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return split;}
  public int splitArmWord(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);return arm;}
  public int pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);pendingReads++;return pending;}
  public void clearSplitArm(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);arm=0;}
  public void pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c,int value){requireCurrent(c);pending=value;}
  public int currentWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at){requireCurrent(c);paletteReads++;events.add("water-long:"+at);if(waterFault!=null&&at==12)throw waterFault;return at;}
  public void selectWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at,int bits){requireCurrent(c);assertEquals(at,bits);selectedLongs++;events.add("select-long:"+at);}
  public void drawStage(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);events.add("stage-draw");}
  public void slowPlc(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);events.add("slow-plc");}
  public void updateHud(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);events.add("hud");}
 }
}
