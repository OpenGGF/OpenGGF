package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.r4.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict actual USA original TT public LOAD_GAME; native held Right only, no inserted physical row.
 * Written before production. Java/Ready/256+ returns/FULL/course remain UNRUN until Root qualifies. */
@Isolated class TestCdOriginalTtFallSpikesWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineService256BindsFallSpikesChildAndReplaysThreeCompleteCurrentWorldImages()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);
   var frame=m.originalR11EntryFrame();var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();
   var level=assertInstanceOf(SonicCdLevel.class,manager.getCurrentLevel());var loading=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
   assertSame(loading,level.loadingSource());assertSame(loading.retainedPlayerSource(),frame.stageSource());
   var loads=m.programServicesState().loads();long generation=manager.getCompletedProductionLoadGeneration();
   for(int completed=1;completed<=255;completed++){step(loop,8);living(m,completed);}
   var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var fullKeys=Set.copyOf(full.entries().keySet());var courseKeys=Set.copyOf(course.entries().keySet());var expected=new ArrayList<Object>();
   for(int completed=256;completed<=258;completed++) {
    step(loop,8);living(m,completed);assertSame(frame,m.originalR11EntryFrame());assertSame(level,manager.getCurrentLevel());assertSame(loads,m.programServicesState().loads());assertEquals(generation,manager.getCompletedProductionLoadGeneration());
    if(completed==256)assertActualParentChild(m);expected.add(image(m));
   }
   for(boolean c:new boolean[]{false,true}) {
    if(c)registry.restoreCourse(course);else registry.restore(full);
    for(int completed=256;completed<=258;completed++) {
     step(loop,8);living(m,completed);CdCapturedValueAssertions.assertCapturedValueEquals(expected.get(completed-256),image(m));
     assertEquals(fullKeys,registry.capture().entries().keySet());assertEquals(courseKeys,registry.captureCourse().entries().keySet());
    }
   }
  }
 }
 @Test void idleCallbacksAndCopiedOperationCannotWriteOutsideActualFallSpikesInvocation()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireWhileContextCurrent()) {
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int completed=1;completed<=256;completed++){step(loop,8);living(m,completed);}assertActualParentChild(m);
   var binding=field(field(m,"r11World"),"binding");var placement=(CdObjectPlacementRuntime)field(binding,"placement");var scope=(CdR4ObjectScope)field(binding,"ttEcology");
   var ports=(CdR4ObjectPorts)field(binding,"ttEcologyPorts");var before=image(m);
   assertThrows(IllegalStateException.class,()->ports.floor(55));assertThrows(IllegalStateException.class,()->ports.solid(55));
   var output=placement.pool().state(55).moveByte(CdObjectState.ByteField.ROUTINE,12);
   var copied=new CdR4ObjectPorts.Operation(CdR4ObjectPorts.Kind.STORE,55,55,placement.capture(),output,null);assertFalse(scope.isCurrent(copied));
   var factory=binding.getClass().getDeclaredMethod("ttEcologyGuard");factory.setAccessible(true);var guard=(CdR4ObjectPorts.Guard)factory.invoke(binding);
   assertThrows(IllegalStateException.class,()->guard.before(scope,copied));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
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
 private static void assertActualParentChild(SonicCdGameModule m) {
  var rows=m.originalR11PlacementImage().pool().slots();var parent=rows.get(55-32);assertEquals(0x32,parent.id());assertEquals(2,parent.routine());assertEquals(0x20EB08,parent.longBits(CdObjectState.LongField.MAPPINGS));
  int low=parent.word(CdObjectState.WordField.BOSS_SAVED_X),child=((short)low-(short)0xD000)/64;assertTrue(child>55&&child<128,"genuine SpawnObjectAfter child");
  var row=rows.get(child-32);assertEquals(0x32,row.id());assertEquals(255,row.subtype());assertEquals(1,row.unsignedByte(CdObjectState.ByteField.MAP_FRAME));assertEquals(parent.identity().nativeAddress24()&65535,row.word(CdObjectState.WordField.EXPLOSION_POINTS_FRAME));
 }
 private static Object image(SonicCdGameModule m) {
  var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState());
 }
 private static AutoCloseable retireWhileContextCurrent(){return ()->{SessionManager.clear();com.openggf.game.GameServices.graphics().cleanup();};}
 private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
