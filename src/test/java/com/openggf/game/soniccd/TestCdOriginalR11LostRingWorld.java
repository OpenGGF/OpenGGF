package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Strict public caller obligation. The measured4251 prefix must return; no early unsupported/death
 * is accepted. No fixture pose, clock, pool, Snapshot or Frame is injected. All Java UNRUN. */
@Isolated class TestCdOriginalR11LostRingWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actual4000RightThenTwelveCallerJumpsReturnsRingLossAndReplaysFullAndCourse()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);
   assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());var frame=module.originalR11EntryFrame();boolean jumped=false,pickup=false,mosqui=false;int oldRings=module.originalR11LoadingPrefix().ring().rings();
   for(int done=0;done<4251;done++){
    step(loop,done>=4000&&done<4012?0x18:8);assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6,"Returned death ends the obligation before another call");
    if(done>=4000&&(frame.state().motion().flags()&2)!=0&&frame.state().motion().yVelocity()<0)jumped=true;
    int now=module.originalR11LoadingPrefix().ring().rings();pickup|=now>oldRings;oldRings=now;
    mosqui|=module.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==CdMosqui.OBJECT_ID&&s.routine()!=0);
   }
   assertTrue(jumped);assertTrue(pickup);assertTrue(mosqui);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   step(loop,8);assertReturned(module);var expected=image(module);
   registry.restore(full);step(loop,8);assertReturned(module);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
   registry.restoreCourse(course);step(loop,8);assertReturned(module);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));assertSame(frame,module.originalR11EntryFrame());
  }
 }
 private static void assertReturned(SonicCdGameModule m){assertFalse(m.originalR11EntryFrame().closed());assertEquals(4,m.originalR11EntryFrame().state().motion().routine());assertEquals(0,m.originalR11LoadingPrefix().ring().rings());var row=m.originalR11PlacementImage().pool().slots().get(35-32);assertEquals(CdLostRing.OBJECT_ID,row.id());assertEquals(2,row.routine());assertEquals(0x20A0FA,row.longBits(CdObjectState.LongField.MAPPINGS));assertTrue(m.originalR11WorldImage().orElseThrow().sounds().stream().anyMatch(s->s instanceof SonicCdGameModule.PastLostRingFm));assertTrue(m.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),m.selectedProfileState(),m.programServicesState());}
}
