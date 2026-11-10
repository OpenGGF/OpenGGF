package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.CdR4PlayerBubbles;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Strict source-written public selected-R41A obligations; all Java/1437/replay UNRUN.
 * Controller input only. The supplied reached endpoint is an assertion, never runtime authority. */
@Isolated class TestCdOriginalTtWaterAirFmWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineWarningService1437ReturnsAliveAndReplaysFullAndCourseExactly()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);
   for(int n=1;n<=1436;n++){step(loop,8);living(m,n);}
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=image(m);
   step(loop,8);living(m,1437);var request=warning(m);var fm=source.fmSoundAssets().orElseThrow();assertEquals(fm.sfxHeaderAddress(0xDF),request.headerAddress());assertEquals(fm.nativePriorityByte(0xDF),request.priorityByte());var expected=image(m);
   // Capturing/reading completed metadata must not append or dispatch the command again.
   registry.capture();registry.captureCourse();warning(m);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));step(loop,8);living(m,1437);warning(m);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));}
  }
 }
 @Test void rawIdsIdleOffThreadAndForeignOwnerCannotPublishActualWorldSound()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int n=1;n<=432;n++){step(loop,8);living(m,n);}
   var binding=field(field(m,"r11World"),"binding");var calls=(CdR4WaterAirPorts.Calls)field(binding,"waterCalls");var owner=(CdR4PlayerBubbles.FmOwner)field(binding,"ttAirFmOwner");var before=image(m);
   for(int id:new int[]{0xDF,0xB6,0xA1,0xA2,0,255,256,-1})assertThrows(IllegalStateException.class,()->calls.sound(id));
   assertThrows(IllegalStateException.class,()->owner.sound(null));var rejected=new AtomicReference<Throwable>();var thread=new Thread(()->{try{calls.sound(0xDF);}catch(Throwable x){rejected.set(x);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,rejected.get());
   CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private static SonicCdGameModule.PastTtWaterAirFm warning(SonicCdGameModule m){var sounds=m.originalR11WorldImage().orElseThrow().sounds();var requests=sounds.stream().filter(SonicCdGameModule.PastTtWaterAirFm.class::isInstance).map(SonicCdGameModule.PastTtWaterAirFm.class::cast).toList();assertEquals(1,requests.size());var request=requests.getFirst();assertEquals(CdR4PlayerBubbles.FmSound.AIR_WARNING,request.sound());assertEquals(0xDF,request.nativeId());return request;}
 private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source)throws Exception{var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,profile.encode());return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);}
 private static void ready(GameLoop loop,SonicCdGameModule m){for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(CdStagePayload.R41A.stage(),m.originalR11EntryFrame().stageSource().stage());assertEquals(0,m.originalR11EntryFrame().environment().globals().levelFrames());}
 private static void living(SonicCdGameModule m,int n){var f=m.originalR11EntryFrame();assertFalse(f.closed());assertTrue(f.state().motion().routine()==2||f.state().motion().routine()==4,"Actual returned caller "+n);assertEquals(n,f.environment().globals().levelFrames());assertEquals(8,f.environment().controls().rawHeld());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var b=m.originalR11SwitchesImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState(),b.map(x->List.of(x.knownMask(),x.bytes())));}
 private static AutoCloseable retireContext(){return ()->{SessionManager.clear();com.openggf.game.GameServices.graphics().cleanup();};}
 private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
}
