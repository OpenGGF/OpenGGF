package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual public original selected entry. No pool injection, private snapshot or new authority. */
@Isolated class TestCdOriginalTtTouchWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineFirstOrdinaryTouchReturnsAndFullCourseReplaysTheActualCurrentOwners()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var saves=new SaveManager(root);
  var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(
   OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  saves.writeSlot("scd",1,profile.encode());var attempt=SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var mode=SessionManager.getCurrentGameplayMode();
   for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   assertEquals(1,m.originalR11EntryFrame().environment().movement().gates().timeZone(),"Native TIME_PRESENT; not zone0200 or rawperiod0");
   step(loop,0); // Genuine routine0 Init returns before native Touch.
   var f=m.originalR11EntryFrame();assertEquals(2,f.state().motion().routine());
   assertTrue((byte)f.state().motion().playerControl()>=0);assertNotEquals(0x2B,f.state().motion().animation());
   var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   step(loop,0); // Genuine routine2 Main MUST return through the actual Touch callback.
   assertEquals(2,f.environment().globals().levelFrames());assertFalse(f.closed());
   assertEquals(1L,m.originalR11WorldImage().orElseThrow().sounds().stream().filter(value -> value instanceof SonicCdGameModule.PastFrameSound fm && fm.sound() instanceof com.openggf.game.soniccd.player.CdPlayerFrameRuntime.MovementSound movement && movement.sound()==com.openggf.game.soniccd.player.CdPlayerMovement.Sound.FM_CHARGESTOP).count(),"Actual Main publishes the native balance-path charge-stop before Touch");
   var expected=image(m);registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   var before=image(m);assertThrows(IllegalArgumentException.class,()->m.serviceOriginalR11Loading(256));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private static Object image(SonicCdGameModule m) {
  var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();
  return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11WorldImage(),m.programServicesState());
 }
}
