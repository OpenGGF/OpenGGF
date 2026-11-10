package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine existing selected loading/registry ingress; no supplied bank is adopted. Java UNRUN. */
@Isolated class TestCdOriginalBlockStagingWorld {
 @TempDir Path root;
 private TestCdOriginalR11LoadingWorld fixture;
 @BeforeEach void setup(){fixture=new TestCdOriginalR11LoadingWorld();fixture.root=root;fixture.setup();}
 @AfterEach void cleanup(){fixture.cleanup();}
 @Test void originalClearThenActualDecoderRetainsTheRealSuffixAndCanonicalPrefix()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var clear=module.originalR11LoadingPrefix().blockDefinitions();assertEquals(0,clear.blockWord(1023,3));
   TestCdOriginalR11LoadingWorld.step(loop,0);var bank=module.originalR11LoadingPrefix().blockDefinitions();var product=module.originalR11LoadingImage().blocks().orElseThrow();
   for(int p=0;p<product.length();p+=2)assertEquals(product.unsignedWord(p),bank.unsignedWord(p));assertEquals(0,bank.blockWord(1023,3));assertEquals(0,clear.blockWord(1023,3));assertFalse(module.originalR11EntryFrame().closed());
  }
 }
 @Test void fullAndCourseRestoreCarryTheSameOriginalClearAndDecodedBankWithoutReplay()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=TestCdOriginalR11LoadingWorld.boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var clear=module.originalR11LoadingPrefix().blockDefinitions();var services=module.programServicesState();
   TestCdOriginalR11LoadingWorld.step(loop,0);var decoded=module.originalR11LoadingPrefix().blockDefinitions();var course=registry.captureCourse();
   registry.restore(full);assertSame(clear,module.originalR11LoadingPrefix().blockDefinitions());assertSame(services,module.programServicesState());
   TestCdOriginalR11LoadingWorld.step(loop,0);assertEquals(decoded,module.originalR11LoadingPrefix().blockDefinitions());registry.restoreCourse(course);assertEquals(decoded,module.originalR11LoadingPrefix().blockDefinitions());
   SessionManager.clear();assertThrows(IllegalStateException.class,()->registry.restore(full));
  }
 }
}
