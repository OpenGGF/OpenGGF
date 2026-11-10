package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original R11 shared lifetime controls; no Frame/actor/timer/snapshot hydration. UNRUN. */
@Isolated class TestCdOriginalPairedFinish {
 @TempDir Path root;
 private TestCdOriginalR11LoadingWorld fixture;
 @BeforeEach void setup(){fixture=new TestCdOriginalR11LoadingWorld();fixture.root=root;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}
 @Test void originalLoadingAndOrdinaryBorrowTheOneCapturedTailAndReplayBothPartitions()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var a=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=a.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,a);var f=m.originalR11EntryFrame();
   assertThrows(IllegalStateException.class,()->f.bindFinishTimersOwner(null));
   TestCdOriginalR11LoadingWorld.step(loop,0);
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var tail=m.originalR11LoadingImage().globals().tail().orElseThrow();
   TestCdOriginalR11LoadingWorld.step(loop,0);var after=m.originalR11LoadingImage().globals().tail().orElseThrow();var shared=f.captureShared();
   assertEquals(0,tail.player2BoredWord());assertEquals(0,after.player2BoredWord(),"native inactive secondary remains inactive");
   registry.restore(full);TestCdOriginalR11LoadingWorld.step(loop,0);assertEquals(after,m.originalR11LoadingImage().globals().tail().orElseThrow());assertEquals(shared,f.captureShared());
   registry.restoreCourse(course);TestCdOriginalR11LoadingWorld.step(loop,0);assertEquals(after,m.originalR11LoadingImage().globals().tail().orElseThrow());assertEquals(shared,f.captureShared());
   for(int row=0;row<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)TestCdOriginalR11LoadingWorld.step(loop,0);
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());TestCdOriginalR11LoadingWorld.step(loop,0);
   assertEquals(0,m.originalR11LoadingImage().globals().tail().orElseThrow().player2BoredWord());assertFalse(f.closed());
  }
 }
 @Test void finishedCallCannotBeForgedOrBorrowedFromCompletedValues(){
  assertTrue(java.util.Arrays.stream(CdPlayerFrameRuntime.FinishTimerCall.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
 }
}
