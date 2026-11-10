package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.r4.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict actual USA original TT public LOAD_GAME; native held Right only, no inserted physical row.
 * Written before production. Java/297+ returns/FULL/course remain UNRUN until Root qualifies. */
@Isolated class TestCdOriginalTtFirstWaterEntryWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualFirstWaterEntry297Through300ReturnsAliveAndReplaysFullAndCourse()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int n=1;n<=296;n++){step(loop,8);living(m,n);}
   var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();var level=manager.getCurrentLevel();var frame=m.originalR11EntryFrame();var loads=m.programServicesState().loads();long generation=manager.getCompletedProductionLoadGeneration();
   var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var expected=new ArrayList<Object>();
   for(int n=297;n<=300;n++){step(loop,8);living(m,n);wet(m);assertSame(frame,m.originalR11EntryFrame());assertSame(level,manager.getCurrentLevel());assertSame(loads,m.programServicesState().loads());assertEquals(generation,manager.getCompletedProductionLoadGeneration());expected.add(image(m));}
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);for(int n=297;n<=300;n++){step(loop,8);living(m,n);wet(m);CdCapturedValueAssertions.assertCapturedValueEquals(expected.get(n-297),image(m));}}
  }
 }
 @Test void idleAndOffThreadWaterPortsCannotWriteModuleDrownOrReservedSeven()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int n=1;n<=300;n++){step(loop,8);living(m,n);}wet(m);
   var binding=field(field(m,"r11World"),"binding");var calls=(CdR4WaterAirPorts.Calls)field(binding,"waterCalls");var before=image(m);
   assertThrows(IllegalStateException.class,()->calls.write(CdR4WaterAirPorts.Value.DROWN_TIMER,3));assertThrows(IllegalStateException.class,calls::reservedBubbles);
   var result=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{calls.write(CdR4WaterAirPorts.Value.DROWN_TIMER,3);}catch(Throwable x){result.set(x);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,result.get());
   assertThrows(IllegalStateException.class,()->m.originalR11EntryFrame().requireOriginalWaterPort(CdPlayerFrameRuntime.OriginalWaterPortKind.VALUE_WRITE,null,CdR4WaterAirPorts.Value.DROWN_TIMER,3));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private static void wet(SonicCdGameModule m){
  var f=m.originalR11EntryFrame();assertEquals(0x40,f.state().motion().flags()&0x40);var p=f.environment().movement().physics();assertEquals(0x300,p.topSpeed());assertEquals(6,p.acceleration());assertEquals(0x40,p.deceleration());
  int air=m.originalR11LoadingImage().globals().drownTimerWord().orElseThrow();assertTrue(air>=0&&air<=30);var row=m.originalR11LoadingImage().reserved().slice((7-1)*64,64);assertEquals(0x21,row.unsignedByte(0));assertEquals(0x81,row.unsignedByte(0x28));assertEquals(10,row.unsignedByte(0x24));assertEquals(0x21E9B4L,row.unsignedLong(4));
 }
 private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source)throws Exception {
  var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  saves.writeSlot("scd",1,profile.encode());return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
 }
 private static void ready(GameLoop loop,SonicCdGameModule m) {
  for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
  assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
  assertEquals(CdStagePayload.R41A.stage(),m.originalR11EntryFrame().stageSource().stage());assertEquals(0,m.originalR11EntryFrame().environment().globals().levelFrames());
 }
 private static void living(SonicCdGameModule m,int completed) {
  var f=m.originalR11EntryFrame();assertFalse(f.closed());assertTrue(f.state().motion().routine()==2||f.state().motion().routine()==4,"genuine service "+completed+" must remain alive");
  assertEquals(completed,f.environment().globals().levelFrames());assertEquals(8,f.environment().controls().rawHeld());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
 }
 private static Object image(SonicCdGameModule m) {
  var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState());
 }
 private static AutoCloseable retireWhileContextCurrent(){return ()->{SessionManager.clear();com.openggf.game.GameServices.graphics().cleanup();};}
 private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
