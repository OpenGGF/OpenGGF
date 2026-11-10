package com.openggf.game.soniccd;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Positive actual public continuation beyond the old39 frontier; Java UNRUN at source handoff. */
@Isolated class TestCdOriginalTtCollapseFloorWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualRightOrdinaryMustPassCollapseFloorAndFullCourseReplayTheCurrentOwners()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,profile.encode());var attempt=SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&module.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,module.originalR11LoadingImage().phase());
   var mode=SessionManager.getCurrentGameplayMode();var frame=module.originalR11EntryFrame();var playerSource=frame.stageSource();var manager=mode.getLevelManager();var generation=manager.getCompletedProductionLoadGeneration();var level=manager.getCurrentLevel();var loads=module.programServicesState().loads();
   for(int n=0;n<238;n++)step(loop,8);assertEquals(238,frame.environment().globals().levelFrames());
   var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   for(int n=0;n<2;n++)step(loop,8);assertEquals(240,frame.environment().globals().levelFrames());assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6);
   assertTrue(module.originalR11PlacementImage().pool().slots().stream().anyMatch(row->row.id()==0x39&&row.routine()!=0),"A genuine CollapseFloor body must return beyond the old unknown-handler edge");
   var expected=image(module);registry.restore(full);for(int n=0;n<2;n++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));registry.restoreCourse(course);for(int n=0;n<2;n++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
   assertSame(frame,module.originalR11EntryFrame());assertSame(playerSource,frame.stageSource());assertSame(level,manager.getCurrentLevel());assertSame(loads,module.programServicesState().loads());assertEquals(generation,manager.getCompletedProductionLoadGeneration());
  }
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState());}
}
