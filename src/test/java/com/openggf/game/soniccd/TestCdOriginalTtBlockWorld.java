package com.openggf.game.soniccd;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual public entry prefix. A thrown step retires its Frame; no capture can reopen it. */
@Isolated class TestCdOriginalTtBlockWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineInitialPassPreservesBlockAndEitherReturnsOrRetiresAtNamedLaterOwner()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,profile.encode());
  var attempt=SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);
   var f=m.originalR11EntryFrame();assertEquals(1,f.environment().movement().gates().timeZone());var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   try{step(loop,0);}catch(UnsupportedOperationException stopped){
    assertTrue(stopped.getMessage().contains("id=$22"),"The Block-only packet may stop only at its authenticated later Spikes22");assertFalse(stopped.getMessage().contains("id=$34"));
    assertTrue(f.closed());assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,()->registry.restoreCourse(course));assertThrows(IllegalStateException.class,()->m.originalR11PlacementImage());
    return; // Explicit partial prefix only; unconditional fullordinary obligations remain enabled separately.
   }
   // If Root has also bound the disjoint later families, require a healthy completed service;
   // source-only prefix success does not substitute for these positive assertions.
   assertFalse(f.closed());assertEquals(1,f.environment().globals().levelFrames());
   assertTrue(m.originalR11PlacementImage().pool().slots().stream().anyMatch(row->row.id()==0x34&&row.routine()==2));
   var expected=image(m);registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
  }
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingImage(),m.originalR11LoadingPrefix(),m.originalR11WorldImage(),m.programServicesState());}
}
