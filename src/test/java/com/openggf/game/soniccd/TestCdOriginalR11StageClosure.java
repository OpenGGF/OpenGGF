package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Proposed strict original-public-controller obligations. Java/discovery/runtime UNRUN.
 * Any earlier family, death, unsupported boundary or missing handoff fails these obligations.
 * No actor/placement/capture image is constructed to stand in for a natural returned course. */
@Isolated
class TestCdOriginalR11StageClosure extends TestCdOriginalR11LoadingWorld {
 @Test void actualBigRingServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x14);}
 @Test void actualGoalMustReturnAndReplayFullAndCourse()throws Exception{family(0x16);}
 @Test void actualSignpostMustReturnAndReplayFullAndCourse()throws Exception{family(0x17);}
 @Test void actualResultsMustReturnAndReplayFullAndCourse()throws Exception{family(0x3A);}
 private void family(int id)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,a);for(int i=0;i<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());var frame=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
   var full=registry.capture();var course=registry.captureCourse();int rows=0;
   while(rows<8192&&!returned(m,id)){step(loop,8);rows++;assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6,"Death is failure, never stage closure");}
   assertTrue(returned(m,id),"Actual returned stage-closure family $"+Integer.toHexString(id));assertTrue(rows>0);var picture=m.originalR11FirstLoad().selection().inputs();var current=m.originalR11WorldImage().orElseThrow().cpu().orElseThrow();assertTrue(picture.selectedCpu().isPresent());assertNotSame(current,picture.selectedCpu().orElseThrow(),"Selector8 retains the previous SAT producer until the next service");var expected=image(m);int count=rows;
   registry.restore(full);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(frame,m.originalR11EntryFrame());
  }
 }
 private static boolean returned(SonicCdGameModule m,int id){return m.originalR11WorldImage().orElseThrow().closureServices().stream().anyMatch(c->c.id()==id&&c.operations()>0);}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),w.closureServices(),m.originalR11FirstLoad().selection().inputs(),m.selectedProfileState(),m.programServicesState());}
 @Test void actualResultsMustTallyAndReachNextProgrammeThroughOriginalController()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,a);boolean results=false,bonus=false,transfer=false;
   for(int i=0;i<12288&&!transfer;i++){
    step(loop,8);transfer=SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel() instanceof com.openggf.game.soniccd.level.SonicCdAct2Level next&&next.assets().identity().file()==CdNativeFile.R12A;
    if(transfer)break;var state=m.originalR11LoadingImage();assertTrue(m.originalR11EntryFrame().state().motion().routine()<6,"Earlier death is failure");
    results|=returned(m,0x3A);bonus|=state.globals().bonus().map(v->v.timeBonusWord()!=0||v.ringBonusWord()!=0).orElse(false);
   }
   assertTrue(results);assertTrue(bonus,"Actual ring/time tally must have existed");assertTrue(transfer,"Actual old programme RTS/IPX destination handoff must return");
  }
 }
 @Test void authenticOriginalViewsHaveDistinctAddressesAndCompleteResultsTail()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var o=a.openingObjects();
  assertEquals(0x23F16A,o.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.CLOSURE_BIG_RING_MAPPINGS).range().nativeAddress());
  assertEquals(0x20F4D4,o.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.CLOSURE_RESULTS_MAPPINGS).range().nativeAddress());
  assertEquals(622,o.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.CLOSURE_RESULTS_MAPPINGS).bytes().length());
 }
 @Test void constructedPlacementCannotAcquireOriginalFamilyInvocation()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var fake=new CdObjectPlacementRuntime(a.placements());
  assertThrows(IllegalArgumentException.class,()->CdBigRing.fromOriginalPresentAssets(fake,a,null,null,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdGoalPost.fromOriginalPresentAssets(fake,a,null,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdSignpost.fromOriginalPresentAssets(fake,a,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdResults.fromOriginalPresentAssets(fake,a,null,null,null,null,null,null,null,null,null));
 }
}
