package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Strict SOURCE obligations, never observed route evidence. All Java/discovery/runtime UNRUN. */
@Isolated
class TestCdOriginalR11MechanismFamilyWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualDiscReturnFullCourse()throws Exception{family(0x09);}
 @Test void actualMovingSpringReturnFullCourse()throws Exception{family(0x0F);}
 @Test void actualSpringboardReturnFullCourse()throws Exception{family(0x28);}
 @Test void actualHoleReturnFullCourse()throws Exception{family(0x31);}
 @Test void actualWallReturnFullCourse()throws Exception{family(0x36);}
 private void family(int id)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int i=0;i<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());var f=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var service=m.programServicesState();
   int rows=0;boolean serviced=false;
   // A bounded proposed ordinary Right course, not a claimed/optimized route. Unsupported/death is failure.
   while(rows<4096&&!serviced){step(loop,8);rows++;assertFalse(f.closed());assertTrue(f.state().motion().routine()<6,"Death cannot certify a new family");serviced=returned(m,id);}
   assertTrue(serviced,"Required actual returned service receipt for family $"+Integer.toHexString(id));assertTrue(rows>0);
   assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isPresent());var expected=image(m);int count=rows;
   registry.restore(full);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(f,m.originalR11EntryFrame());assertSame(service.loads(),m.programServicesState().loads());assertEquals(service.completedCalls(),m.programServicesState().completedCalls());
  }
 }
 private static boolean returned(SonicCdGameModule m,int id){return m.originalR11WorldImage().orElseThrow().mechanismServices().stream().anyMatch(c->c.id()==id&&c.operations()>0);}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),w.mechanismServices(),m.selectedProfileState(),m.programServicesState());}
}
