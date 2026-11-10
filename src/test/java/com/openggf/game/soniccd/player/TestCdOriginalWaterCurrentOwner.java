package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Constructed Frame/owner vectors over genuine media; NOT public TT birth/World/route evidence.
 * Test synthetic rectangles and switch bytes are explicit fixture data, never runtime defaults. */
class TestCdOriginalWaterCurrentOwner {
 private CdStagePlayerSource source()throws Exception {
  String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank());Path cue;
  try(var p=Files.list(Path.of(root).resolve("USA"))){var cues=p.filter(v->v.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
  try(var a=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){return CdStagePlayerSource.fromProgramme(a,CdR4DataLayout.Programme.R41A.payload().stage());}
 }
 private static CdAssetBytes records(int mode,int vx,int vy){byte[] b=new byte[16];word(b,0,0);word(b,2,0);word(b,4,0x1000);word(b,6,0x1000);word(b,8,vx);word(b,10,vy);b[12]=(byte)mode;b[13]=5;b[14]=(byte)0xFF;b[15]=(byte)0xFF;return CdAssetBytes.copyOf(b);}
 private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
 @Test void modeZeroUsesExistingFrameFieldsInNativeStoreOrderAndPreservesEveryOtherPartition()throws Exception {
  var h=new H(source(),2,0,0,0,0);var s=h.frame.state();var e=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();var display=h.frame.display();
  h.run();var m=h.frame.state().motion();assertEquals(List.of("WATER_CURRENT","CHARGE","X_VELOCITY","Y_VELOCITY","ANIMATION","FLAGS"),h.stores);
  assertEquals(1,h.frame.environment().movement().gates().windTunnel());assertEquals(0,m.playerJump());assertEquals(0x400,m.xVelocity());assertEquals(-0x600,m.yVelocity());assertEquals(0xF,m.animation());assertEquals(0x42,m.flags());
  assertEquals(s.motion().centreX16_16(),m.centreX16_16());assertEquals(s.motion().centreY16_16(),m.centreY16_16());assertSame(s.history(),h.frame.state().history());assertSame(s.header(),h.frame.state().header());assertSame(s.timers(),h.frame.state().timers());assertEquals(s.objectLinks(),h.frame.state().objectLinks());assertSame(e.controls(),h.frame.environment().controls());assertSame(e.globals(),h.frame.environment().globals());assertSame(e.movement().physics(),h.frame.environment().movement().physics());assertSame(e.movement().bounds(),h.frame.environment().movement().bounds());assertSame(e.movement().globals(),h.frame.environment().movement().globals());assertEquals(art,h.frame.artState());assertEquals(clock,h.frame.vIntClock());assertEquals(display,h.frame.display());assertEquals(0,h.switchReads);assertEquals(1,h.clockReads);
 }
 @Test void nativeWordInputNudgesPreserveFractionsAndOpposingDirectionsBothRun()throws Exception {
  var h=new H(source(),2,0,0,0,15);var m=h.frame.state().motion();h.run();assertEquals(m.centreX16_16(),h.frame.state().motion().centreX16_16());assertEquals(m.centreY16_16(),h.frame.state().motion().centreY16_16());assertEquals(List.of("Y","Y","X","X"),h.stores.subList(6,10));
  var right=new H(source(),2,0,0,0,8);right.run();assertEquals(0x811234,right.frame.state().motion().centreX16_16());assertEquals(0x100ABCD,right.frame.state().motion().centreY16_16());
 }
 @Test void modeOneUnknownSwitchThrowsSameFaultBeforeAnyCurrentStore()throws Exception {
  var h=new H(source(),2,0,0,0,0);h.data=records(1,0x400,0xFA00);var before=h.frame.state();var env=h.frame.environment();var first=new Fault("unowned original switch");h.switchFault=first;
  assertSame(first,assertThrows(Fault.class,h::run));assertSame(before,h.frame.state());assertSame(env,h.frame.environment());assertTrue(h.stores.isEmpty());assertEquals(1,h.clockReads);assertEquals(1,h.switchReads);assertTrue(h.frame.closed());
 }
 @Test void modeOneClearLatchReturnsWithoutWritingAnyPlayerOrCurrentValue()throws Exception {
  var h=new H(source(),2,0,0,0,0);h.data=records(1,0x400,0xFA00);h.switchValue=0;var before=h.frame.state();var env=h.frame.environment();h.run();assertSame(before,h.frame.state());assertSame(env,h.frame.environment());assertTrue(h.stores.isEmpty());assertEquals(1,h.switchReads);
 }
 @Test void modeTwoUnknownSwitchRetainsAllSixEarlierNativeStoresAndNoInputNudge()throws Exception {
  var h=new H(source(),2,0,0,0,8);h.data=records(2,0x400,0xFA00);var first=new Fault("late original switch");h.switchFault=first;
  assertSame(first,assertThrows(Fault.class,h::run));assertEquals(6,h.stores.size());assertEquals(1,h.frame.environment().movement().gates().windTunnel());assertEquals(0,h.frame.state().motion().playerJump());assertEquals(0x400,h.frame.state().motion().xVelocity());assertEquals(-0x600,h.frame.state().motion().yVelocity());assertEquals(0x801234,h.frame.state().motion().centreX16_16());
 }
 @Test void modeTwoReadsSwitchAfterFlagsAndNegatesRawWordsBeforeHeldInput()throws Exception {
  var h=new H(source(),2,0,0,0,8);h.data=records(2,0x8000,0xFFFF);h.switchValue=0x20;h.run();assertEquals(6,h.storesAtSwitch);assertEquals(-0x8000,h.frame.state().motion().xVelocity());assertEquals(1,h.frame.state().motion().yVelocity());assertEquals(0x811234,h.frame.state().motion().centreX16_16());assertEquals(List.of("X_VELOCITY","Y_VELOCITY","X"),h.stores.subList(6,9));
 }
 @Test void deathRoutineOnlyClearsCurrentByteAfterReachedClockAndSwitchGate()throws Exception {
  var h=new H(source(),4,0,0,1,0);var before=h.frame.state();h.run();assertEquals(List.of("WATER_CURRENT"),h.stores);assertSame(before,h.frame.state());assertEquals(0,h.frame.environment().movement().gates().windTunnel());assertEquals(1,h.clockReads);
 }
 @Test void leavingCurrentClearsAnimationThenCurrentByteAndNeverReadsClockOrSwitch()throws Exception {
  var h=new H(source(),2,0xF,0,1,0);h.data=records(0,0x400,0xFA00);byte[] a=h.data.copy();word(a,0,0x200);h.data=CdAssetBytes.copyOf(a);h.run();assertEquals(List.of("ANIMATION","WATER_CURRENT"),h.stores);assertEquals(0,h.frame.state().motion().animation());assertEquals(0,h.frame.environment().movement().gates().windTunnel());assertEquals(0,h.clockReads);assertEquals(0,h.switchReads);
 }
 @Test void earlyPlayerGatesDoNotReadRectangleBytesClockSwitchOrProduceStores()throws Exception {
  for(int[] gate:List.of(new int[]{0x2B,0},new int[]{0,1})){var h=new H(source(),2,gate[0],gate[1],0,0);h.data=CdAssetBytes.copyOf(new byte[0]);h.run();assertTrue(h.stores.isEmpty());assertEquals(0,h.clockReads);assertEquals(0,h.switchReads);}
 }
 @Test void refusedBeforeStillRunsAfterWithoutNativeStoreAndKeepsOriginalIdentity()throws Exception {
  var h=new H(source(),2,0,0,0,0);var before=h.frame.state();var env=h.frame.environment();h.beforeFault=new Fault("refused current store");
  assertSame(h.beforeFault,assertThrows(Fault.class,h::run));assertEquals(1,h.beforeCalls);assertEquals(1,h.afterCalls);assertFalse(h.saved.landed());assertSame(before,h.frame.state());assertSame(env,h.frame.environment());assertTrue(h.stores.isEmpty());
 }
 @Test void completedStoreThenAfterFaultKeepsPrefixOriginalIdentityAndDistinctSuppressedGuard()throws Exception {
  var h=new H(source(),2,0,0,0,0);h.afterFault=new Fault("after landed global");h.afterGuard=new Fault("independent after-cut guard");
  var first=assertThrows(Fault.class,h::run);assertSame(h.afterFault,first);assertEquals(1,h.frame.environment().movement().gates().windTunnel());assertEquals(1,h.beforeCalls);assertEquals(1,h.afterCalls);assertTrue(Arrays.asList(first.getSuppressed()).contains(h.afterGuard));assertEquals(7,h.frame.state().motion().playerJump());
 }
 @Test void sameFaultFromBeforeAndAfterIsNeverSelfSuppressedOrReplaced()throws Exception {
  var h=new H(source(),2,0,0,0,0);var first=new Fault("same original");h.beforeFault=first;h.afterFault=first;assertSame(first,assertThrows(Fault.class,h::run));assertEquals(0,first.getSuppressed().length);assertEquals(1,h.afterCalls);
 }
 @Test void caughtRecursiveEntryAndCaptureRejectionDoNotCloseHealthyCurrentInvocation()throws Exception {
  var h=new H(source(),2,0,0,0,0);h.beforeAction=()->{assertThrows(IllegalStateException.class,h.frame::captureActor);assertThrows(IllegalStateException.class,h.frame::captureShared);assertThrows(IllegalStateException.class,h::run);};h.run();assertFalse(h.frame.closed());assertEquals(6,h.stores.size());
 }
 @Test void currentReceiptRejectsForeignOffThreadAndRetiredUsesWithoutPublishing()throws Exception {
  var h=new H(source(),2,0,0,0,0);var foreign=new H(h.source,2,0,0,0,0);h.beforeAction=()->{assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(foreign.frame));var fault=new AtomicReference<Throwable>();var t=new Thread(()->{try{h.saved.requireCurrent(h.frame);}catch(Throwable x){fault.set(x);}});t.start();try{t.join();}catch(InterruptedException x){throw new AssertionError(x);}assertInstanceOf(IllegalStateException.class,fault.get());};h.run();assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h.frame));assertThrows(IllegalStateException.class,()->h.saved.cycle().requireCurrent(h.frame));
 }
 @Test void actorAndSharedRestoreRetireOldReceiptAndReproduceValueWithoutPersistingAnotherOwner()throws Exception {
  var h=new H(source(),2,0,0,0,0);var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.run();var old=h.saved;var expected=h.frame.state();var env=h.frame.environment();h.frame.restoreActor(actor);h.frame.restoreShared(shared);h.stores.clear();h.run();assertEquals(expected,h.frame.state());assertEquals(env,h.frame.environment());assertThrows(IllegalStateException.class,()->old.requireCurrent(h.frame));
 }
 @Test void wrongCurrentTailOwnerRejectsBeforeAnyNativeReadWithoutPoisoningFrame()throws Exception {
  var h=new H(source(),2,0,0,0,0);h.tailFault=new Fault("no original World tail");assertSame(h.tailFault,assertThrows(Fault.class,h::run));assertFalse(h.frame.closed());assertEquals(0,h.clockReads);assertEquals(0,h.beforeCalls);h.tailFault=null;h.run();assertFalse(h.frame.closed());
 }
 static final class Fault extends RuntimeException{Fault(String message){super(message);}}
 static final class H implements CdPlayerFrameRuntime.OriginalWaterCurrentOwner {
  final CdStagePlayerSource source;final CdPlayerFrameRuntime frame;final List<String> stores=new ArrayList<>();CdAssetBytes data=records(0,0x400,0xFA00);long generation=1;
  int clockReads,switchReads,storesAtSwitch,switchValue,beforeCalls,afterCalls;boolean failGuard;Fault beforeFault,afterFault,afterGuard,switchFault,tailFault;Runnable beforeAction=()->{};CdPlayerFrameRuntime.OriginalWaterStore saved;
  H(CdStagePlayerSource source,int routine,int animation,int control,int wind,int held){this.source=source;
   var values=new TestCdOriginalPlayerSemanticRuntime.Harness(source);var s=values.state;var m=s.motion();
   var motion=new CdPlayerMotionState(0x801234,0x100ABCD,-0x123,0x456,0x789,routine,0x23,0x40,4,9,19,animation,0x22,7,9,0x21,0x31,control,0x12,0x34);
   var state=new CdPlayerBody.State(motion,s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());var e=values.environment;var me=e.movement();var g=me.gates();
   var gates=new CdPlayerMovement.Gates(source.nativeZoneActWord(),source.ordinaryPeriod(),g.miniSonic(),g.waterSlide(),wind,g.speedShoes(),g.timeWarpDirection());var controls=new CdPlayerBody.Controls(held,0,held,0,0);var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(me.physics(),me.bounds(),gates,me.globals()),controls,e.globals(),e.dynamicSlots());
   frame=CdPlayerFrameRuntime.fromProgrammeEntry(source,state,env,new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),new CdPlayerFrameRuntime.VIntClock(java.util.OptionalLong.of(40),0));
  }
  void run(){frame.runOriginalProgrammeWaterCurrents(this);}
  public long generation(){return generation;}
  public void requireCurrent(CdPlayerFrameRuntime.OriginalProgrammeCall c){c.requireCurrent(frame);assertSame(source,c.source());}
  public void requireCurrentTail(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrent(c);if(tailFault!=null)throw tailFault;if(failGuard)throw afterGuard;}
  public CdAssetBytes currentRecords(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrentTail(c);return data;}
  public CdR4WaterAirPorts.Calls currentWater(CdPlayerFrameRuntime.OriginalProgrammeCall c){requireCurrentTail(c);return new CdR4WaterAirPorts.Calls(){public int read(CdR4WaterAirPorts.Value f){assertEquals(CdR4WaterAirPorts.Value.VBLANK_LOW,f);clockReads++;return 40;}public void write(CdR4WaterAirPorts.Value f,int v){throw nonread();}public CdR4WaterAirPorts.Slot player(){throw nonread();}public CdR4WaterAirPorts.Slot reservedBubbles(){throw nonread();}public Optional<CdR4WaterAirPorts.Slot> spawn(){throw nonread();}public int random(){throw nonread();}public void sound(int id){throw nonread();}public void music(int id){throw nonread();}public void setPlayerGround(){throw nonread();}public void move(CdR4WaterAirPorts.Slot v){throw nonread();}public void animate(CdR4WaterAirPorts.Slot v,CdR4WaterAirPorts.Animation a){throw nonread();}public void draw(CdR4WaterAirPorts.Slot v){throw nonread();}public void delete(CdR4WaterAirPorts.Slot v){throw nonread();}public CdPlayerMath.SineCosine sineCosine(int a){throw nonread();}};}
  public int currentSwitchByte(CdPlayerFrameRuntime.OriginalProgrammeCall c,int index){requireCurrentTail(c);assertEquals(5,index);switchReads++;storesAtSwitch=stores.size();if(switchFault!=null)throw switchFault;return switchValue;}
  public void beforeCurrentStore(CdPlayerFrameRuntime.OriginalWaterStore p){p.requireCurrent(frame);saved=p;beforeCalls++;assertSame(frame.state(),p.beforeState());assertSame(frame.environment(),p.beforeEnvironment());beforeAction.run();if(beforeFault!=null)throw beforeFault;}
  public void afterCurrentStore(CdPlayerFrameRuntime.OriginalWaterStore p){p.requireCurrent(frame);afterCalls++;if(p.landed()){assertSame(frame.state(),p.completedState());assertSame(frame.environment(),p.completedEnvironment());stores.add(p.field().name());}if(afterFault!=null){failGuard=afterGuard!=null;throw afterFault;}}
  private AssertionError nonread(){return new AssertionError("Current routine reached unrelated Body/stage callback");}
  public CdR4WaterAirPorts.Calls water(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}
  public void palette(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void levelArt(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void afterFinish(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void mainMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public int levelFrames(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}
  public Optional<CdPlayerMovement.ObjectContext> objects(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void flipperAnimation(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerMovement.SlotPatch p){throw nonread();}public void draw(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void sound(CdPlayerFrameRuntime.OriginalProgrammeCall c,int id){throw nonread();}public void chargeSound(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Sound sound){throw nonread();}public void beforeCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){throw nonread();}public void afterCharge(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerCharge.Primitive p){throw nonread();}
  public void restoreStageMusic(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void collideObjects(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void timeWarp(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public CdPlayerBody.LevelContext level(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void storeLevel(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.LevelField f,int v){throw nonread();}public void spawnGameOver(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void restartSoundFlag(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void resetSavedObjectFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void completed(CdPlayerFrameRuntime.OriginalProgrammeCall c,CdPlayerBody.Boundary b){throw nonread();}
  public void copyCamerasAndFlags(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public int splitByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public int splitArmWord(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public int pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void clearSplitArm(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void pendingByte(CdPlayerFrameRuntime.OriginalProgrammeCall c,int value){throw nonread();}public int currentWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at){throw nonread();}public void selectWaterPaletteLong(CdPlayerFrameRuntime.OriginalProgrammeCall c,int at,int bits){throw nonread();}public void drawStage(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void slowPlc(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}public void updateHud(CdPlayerFrameRuntime.OriginalProgrammeCall c){throw nonread();}
 }
}
